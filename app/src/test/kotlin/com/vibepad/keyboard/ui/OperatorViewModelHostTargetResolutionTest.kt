package com.vibepad.keyboard.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vibepad.keyboard.hid.FakeHidTransport
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.HostDevice
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.macro.ProfileLoader
import com.vibepad.keyboard.macro.SelectionsStore
import com.vibepad.keyboard.pairing.Confidence
import com.vibepad.keyboard.pairing.HostGuess
import com.vibepad.keyboard.pairing.PairedHostsStore
import com.vibepad.keyboard.pairing.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Validates the resolution chain inside [OperatorViewModel.attachToHost]:
 *
 *   userOverride > detectedHostTarget > SelectionsStore (per-mac legacy)
 *                                     > MACOS (cold-start fallback)
 *
 * Tests use a real `PairedHostsStore` + `SelectionsStore` so the same
 * preference-store layering production sees is exercised end-to-end.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperatorViewModelHostTargetResolutionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var selectionsDs: DataStore<Preferences>
    private lateinit var pairedDs: DataStore<Preferences>
    private lateinit var selections: SelectionsStore
    private lateinit var pairedHosts: PairedHostsStore
    private lateinit var transport: FakeHidTransport

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        selectionsDs = PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("ovm_sel.preferences_pb").also { it.delete() }
        }
        pairedDs = PreferenceDataStoreFactory.create(scope = scope) {
            tempFolder.newFile("ovm_paired.preferences_pb").also { it.delete() }
        }
        selections = SelectionsStore(selectionsDs)
        pairedHosts = PairedHostsStore(pairedDs)
        transport = FakeHidTransport()
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `connected host with detector verdict drives host target`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        pairedHosts.recordConnection(mac, "Owner-mac", 1L)
        pairedHosts.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))

        val vm = buildAndAttach()
        transport.forceState(HidLinkState.Connected(HostDevice(name = "Owner-mac", address = mac)))

        awaitHostTarget(vm, HostTarget.MACOS)
    }

    @Test
    fun `user override beats detector`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        pairedHosts.recordConnection(mac, "Owner-mac", 1L)
        pairedHosts.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))
        pairedHosts.recordOverride(mac, HostTarget.WINDOWS)

        val vm = buildAndAttach()
        transport.forceState(HidLinkState.Connected(HostDevice(name = "Owner-mac", address = mac)))

        awaitHostTarget(vm, HostTarget.WINDOWS)
    }

    @Test
    fun `legacy SelectionsStore acts as fallback when nothing recorded`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        pairedHosts.recordConnection(mac, "Owner-mac", 1L)
        selections.setHostTarget(mac, HostTarget.WINDOWS)

        val vm = buildAndAttach()
        transport.forceState(HidLinkState.Connected(HostDevice(name = "Owner-mac", address = mac)))

        awaitHostTarget(vm, HostTarget.WINDOWS)
    }

    @Test
    fun `disconnected state leaves host target at the last known value`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        pairedHosts.recordConnection(mac, "Owner-mac", 1L)
        pairedHosts.recordDetection(mac, HostGuess(HostTarget.WINDOWS, Confidence.HIGH, Source.NAME))

        val vm = buildAndAttach()
        transport.forceState(HidLinkState.Connected(HostDevice(name = "Owner-mac", address = mac)))
        awaitHostTarget(vm, HostTarget.WINDOWS)

        transport.forceState(HidLinkState.Idle)
        // Sticky behaviour: the cached value remains, no flicker back to MACOS.
        assertEquals(HostTarget.WINDOWS, vm.hostTarget.value)
    }

    private fun buildAndAttach(): OperatorViewModel {
        val profile = ProfileLoader()
            .loadFromString(File("src/main/assets/profiles/claude-code.json").readText())
            .let { (it as ProfileLoader.Result.Ok).profile }
        val vm = OperatorViewModel(
            transport = transport,
            selectionsStore = selections,
            pairedHostsStore = pairedHosts,
            profile = profile,
        )
        vm.attachToHost()
        return vm
    }

    private suspend fun awaitHostTarget(vm: OperatorViewModel, expected: HostTarget) {
        withTimeout(2_000) {
            while (vm.hostTarget.value != expected) delay(10)
        }
    }
}
