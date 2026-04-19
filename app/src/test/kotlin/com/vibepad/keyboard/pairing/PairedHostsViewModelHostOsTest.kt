package com.vibepad.keyboard.pairing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vibepad.keyboard.hid.FakeHidTransport
import com.vibepad.keyboard.input.HostTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Drives the host-OS picker / re-detect surface added in `host-os-autodetect`.
 * Uses a real [PairedHostsStore] backed by a temp DataStore — the API surface
 * is small enough that mocking would just re-implement persistence semantics.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PairedHostsViewModelHostOsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var ds: DataStore<Preferences>
    private lateinit var store: PairedHostsStore
    private lateinit var transport: FakeHidTransport

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempFolder.newFile("vm_hostos_test.preferences_pb").also { it.delete() }
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
    fun `onOpenHostOsPicker emits the row to hostOsPickerFor`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        val vm = buildVm(bondedMacs = setOf(mac))

        val row = awaitFirstRow(vm)
        vm.onOpenHostOsPicker(row)

        assertEquals(row.mac, vm.hostOsPickerFor.value?.mac)

        vm.onDismissHostOsPicker()
        assertNull(vm.hostOsPickerFor.value)
    }

    @Test
    fun `selecting macOS persists override and dismisses sheet`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        val vm = buildVm(bondedMacs = setOf(mac))
        val row = awaitFirstRow(vm)

        vm.onOpenHostOsPicker(row)
        vm.onSelectHostOs(mac, HostTarget.MACOS)

        withTimeout(1_000) {
            while (store.hostOs(mac).userOverrideHostTarget == null) delay(10)
        }
        assertEquals(HostTarget.MACOS, store.hostOs(mac).userOverrideHostTarget)
        assertNull(vm.hostOsPickerFor.value)
    }

    @Test
    fun `selecting auto-detected clears the override`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        store.recordOverride(mac, HostTarget.WINDOWS)
        val vm = buildVm(bondedMacs = setOf(mac))
        val row = awaitFirstRow(vm)

        vm.onOpenHostOsPicker(row)
        vm.onSelectHostOs(mac, target = null)

        withTimeout(1_000) {
            while (store.hostOs(mac).userOverrideHostTarget != null) delay(10)
        }
        assertNull(store.hostOs(mac).userOverrideHostTarget)
    }

    @Test
    fun `onReDetect clears detection but keeps override`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        store.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))
        store.recordOverride(mac, HostTarget.WINDOWS)
        val vm = buildVm(bondedMacs = setOf(mac))

        vm.onReDetect(mac)

        withTimeout(1_000) {
            while (store.hostOs(mac).detectedHostTarget != null) delay(10)
        }
        val rec = store.hostOs(mac)
        assertNull(rec.detectedHostTarget)
        assertEquals(HostTarget.WINDOWS, rec.userOverrideHostTarget)
    }

    @Test
    fun `flow surfaces hostOs metadata onto the row`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        store.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))
        val vm = buildVm(bondedMacs = setOf(mac))

        val row = awaitFirstRow(vm)
        assertEquals(HostTarget.MACOS, row.effectiveHostTarget)
        assertEquals(Source.NAME, row.hostOs.detectedSource)
        assertEquals(false, row.needsReview)
    }

    @Test
    fun `row with no detection and no override is flagged needsReview`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        val vm = buildVm(bondedMacs = setOf(mac))

        val row = awaitFirstRow(vm)
        assertEquals(true, row.needsReview)
        assertNull(row.effectiveHostTarget)
    }

    private fun buildVm(bondedMacs: Set<String>): PairedHostsViewModel = PairedHostsViewModel(
        store = store,
        transport = transport,
        bondedSource = object : BondedHostsSource { override fun currentMacs() = bondedMacs },
        deviceLookup = { null },
        forget = { _ -> RemoveBondHelper.Result.Success },
        pollIntervalMs = 20L,
    )

    private suspend fun awaitFirstRow(vm: PairedHostsViewModel): PairedHostRow =
        withTimeout(2_000) {
            (vm.state.first { it is PairedHostsUiState.List } as PairedHostsUiState.List).rows.first()
        }
}
