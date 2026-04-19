package com.vibepad.keyboard.pairing

import android.bluetooth.BluetoothDevice
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vibepad.keyboard.hid.FakeHidTransport
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.HostDevice
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Drives [PairedHostsViewModel] with a real [PairedHostsStore] (temp-file
 * DataStore) and a [FakeHidTransport]. Confirms:
 *  1. Only macs in BOTH the store AND the bondedDevices snapshot appear.
 *  2. The actively connected mac is flagged with `isActive = true`.
 *  3. Forget drops the row locally and emits `Forgot` / `ForgotLocally`.
 *  4. Fallback leaves the row in place but emits `FallbackOpenedSettings`.
 *
 * These tests use real coroutines and a short bonded-poll interval rather
 * than a `TestDispatcher` because [PairedHostsViewModel]'s hot flow uses
 * [SharingStarted.WhileSubscribed], and runBlocking subscribes for us.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairedHostsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var ds: DataStore<Preferences>
    private lateinit var store: PairedHostsStore
    private lateinit var transport: FakeHidTransport

    @Before
    fun setUp() {
        // `viewModelScope` resolves Dispatchers.Main.immediate at construction
        // time. In plain JUnit unit tests Main isn't installed — set an
        // Unconfined test dispatcher so launched work runs inline on the
        // calling thread.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file: File = tempFolder.newFile("vm_test.preferences_pb").also { it.delete() }
        ds = PreferenceDataStoreFactory.create(scope = scope) { file }
        store = PairedHostsStore(ds)
        transport = FakeHidTransport()
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `intersection hides macs absent from bondedDevices`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "mac-a", 1_000L)
        store.recordConnection("DD:EE:FF:44:55:66", "mac-b", 2_000L)

        val vm = buildVm(bondedMacs = setOf("AA:BB:CC:11:22:33"))
        val list = awaitList(vm)
        assertEquals(1, list.rows.size)
        assertEquals("AA:BB:CC:11:22:33", list.rows.single().mac)
    }

    @Test
    fun `bonded mac absent from store is hidden`() = runBlocking {
        // Headphones are bonded but never recorded by Helm Pad — don't show them.
        val vm = buildVm(bondedMacs = setOf("HE:AD:PH:ON:ES:00"))
        val state = awaitNonLoading(vm)
        assertTrue(state is PairedHostsUiState.Empty)
    }

    @Test
    fun `currently-connected mac is flagged isActive`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "mac-a", 1_000L)
        store.recordConnection("DD:EE:FF:44:55:66", "mac-b", 2_000L)
        transport.forceState(
            HidLinkState.Connected(HostDevice(name = "mac-b", address = "DD:EE:FF:44:55:66")),
        )
        val vm = buildVm(bondedMacs = setOf("AA:BB:CC:11:22:33", "DD:EE:FF:44:55:66"))

        val list = awaitList(vm)
        val active = list.rows.single { it.isActive }
        assertEquals("DD:EE:FF:44:55:66", active.mac)
        assertFalse(list.rows.single { it.mac == "AA:BB:CC:11:22:33" }.isActive)
    }

    @Test
    fun `onForget with no bonded device prunes store and emits ForgotLocally`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "mac-a", 1_000L)
        val vm = PairedHostsViewModel(
            store = store,
            transport = transport,
            bondedSource = object : BondedHostsSource { override fun currentMacs() = emptySet<String>() },
            deviceLookup = { null },
            forget = { _ -> error("should not be called without a device") },
            pollIntervalMs = 20L,
        )

        val event = collectFirstEventAfter(vm) { vm.onForget("AA:BB:CC:11:22:33") }
        assertEquals(PairedHostsViewModel.Event.ForgotLocally, event)
        assertTrue(store.flow().first().isEmpty())
    }

    @Test
    fun `onForget fallback keeps record but emits FallbackOpenedSettings`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "mac-a", 1_000L)
        val fakeDevice = mockk<BluetoothDevice>(relaxed = true)
        val vm = PairedHostsViewModel(
            store = store,
            transport = transport,
            bondedSource = object : BondedHostsSource { override fun currentMacs() = setOf("AA:BB:CC:11:22:33") },
            deviceLookup = { fakeDevice },
            forget = { _ -> RemoveBondHelper.Result.FallbackOpenedSettings },
            pollIntervalMs = 20L,
        )

        val event = collectFirstEventAfter(vm) { vm.onForget("AA:BB:CC:11:22:33") }
        assertEquals(PairedHostsViewModel.Event.FallbackOpenedSettings, event)
        assertEquals(1, store.flow().first().size) // still present — user must finish in Settings
    }

    @Test
    fun `onRename writes alias to store`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "mac-a", 1_000L)
        val vm = PairedHostsViewModel(
            store = store,
            transport = transport,
            bondedSource = object : BondedHostsSource { override fun currentMacs() = emptySet<String>() },
            deviceLookup = { null },
            forget = { _ -> RemoveBondHelper.Result.Success },
            pollIntervalMs = 20L,
        )
        vm.onRename("AA:BB:CC:11:22:33", "Work Laptop")
        withTimeout(1_000) {
            while (store.flow().first().single().alias == null) delay(10)
        }
        val row = store.flow().first().single()
        assertEquals("Work Laptop", row.alias)
    }

    // ---- helpers ------------------------------------------------------------

    private fun buildVm(bondedMacs: Set<String>): PairedHostsViewModel = PairedHostsViewModel(
        store = store,
        transport = transport,
        bondedSource = object : BondedHostsSource { override fun currentMacs() = bondedMacs },
        deviceLookup = { null },
        forget = { _ -> RemoveBondHelper.Result.Success },
        pollIntervalMs = 20L,
    )

    private suspend fun awaitList(vm: PairedHostsViewModel): PairedHostsUiState.List =
        withTimeout(2_000) {
            vm.state.first { it is PairedHostsUiState.List } as PairedHostsUiState.List
        }

    private suspend fun awaitNonLoading(vm: PairedHostsViewModel): PairedHostsUiState =
        withTimeout(2_000) {
            vm.state.first { it !is PairedHostsUiState.Loading }
        }

    /**
     * Subscribes to `vm.events` and **only then** runs [trigger]. Without
     * waiting for `onSubscription` to fire the SharedFlow (replay = 0) drops
     * the event: the collect coroutine hasn't latched yet when onForget
     * finishes.
     */
    private suspend fun collectFirstEventAfter(
        vm: PairedHostsViewModel,
        trigger: suspend () -> Unit,
    ): PairedHostsViewModel.Event {
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        val events = mutableListOf<PairedHostsViewModel.Event>()
        val job = scope.launch {
            vm.events
                .onSubscription { ready.complete(Unit) }
                .collect { events += it }
        }
        ready.await()
        trigger()
        withTimeout(2_000) { while (events.isEmpty()) delay(10) }
        job.cancel()
        return events.first()
    }
}
