package com.vibepad.keyboard.ui

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vibepad.keyboard.hid.FakeHidTransport
import com.vibepad.keyboard.input.HidFrame
import com.vibepad.keyboard.input.Key
import com.vibepad.keyboard.macro.Profile
import com.vibepad.keyboard.macro.ProfileLoader
import com.vibepad.keyboard.macro.SelectionsStore
import com.vibepad.keyboard.pairing.PairedHostsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
 * Covers the [OperatorViewModel] shim that intercepts `switch_model` taps and routes
 * them through [ModelPickerSheet] instead of firing the raw `/model\n` literal. See
 * `claude-code-profile-trim/design.md` decision 2.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OperatorViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var ds: DataStore<Preferences>
    private lateinit var pairedDs: DataStore<Preferences>
    private lateinit var selections: SelectionsStore
    private lateinit var pairedHosts: PairedHostsStore
    private lateinit var transport: FakeHidTransport

    @Before
    fun setUp() {
        // viewModelScope resolves Dispatchers.Main.immediate at construction time.
        // Install an Unconfined test dispatcher so launched work runs inline on the
        // calling thread — the same pattern PairedHostsViewModelTest uses.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file: File = tempFolder.newFile("operator_vm_test.preferences_pb").also { it.delete() }
        ds = PreferenceDataStoreFactory.create(scope = scope) { file }
        selections = SelectionsStore(ds)
        val pairedFile: File = tempFolder.newFile("operator_vm_paired.preferences_pb").also { it.delete() }
        pairedDs = PreferenceDataStoreFactory.create(scope = scope) { pairedFile }
        pairedHosts = PairedHostsStore(pairedDs)
        transport = FakeHidTransport()
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun `tap switch_model opens sheet without firing any frames`() = runBlocking {
        val vm = buildVm()
        val sent = collectFrames {
            vm.fireMacro("switch_model")
        }
        assertTrue("switch_model must not produce HID frames; got ${sent.size}", sent.isEmpty())
        assertTrue("sheet must be open after the tap", vm.showModelPicker.value)
    }

    @Test
    fun `select opus fires literal slash model opus newline and closes sheet`() = runBlocking {
        val vm = buildVm()
        vm.fireMacro("switch_model")
        assertTrue(vm.showModelPicker.value)

        val sent = collectFrames { vm.selectModel(MODEL_OPUS) }
        assertFalse("sheet must close after selection", vm.showModelPicker.value)
        assertPressedKeyUsages("/model opus\n", sent)
    }

    @Test
    fun `select sonnet fires literal slash model sonnet newline`() = runBlocking {
        val vm = buildVm()
        vm.fireMacro("switch_model")
        val sent = collectFrames { vm.selectModel(MODEL_SONNET) }
        assertFalse(vm.showModelPicker.value)
        assertPressedKeyUsages("/model sonnet\n", sent)
    }

    @Test
    fun `select haiku fires literal slash model haiku newline`() = runBlocking {
        val vm = buildVm()
        vm.fireMacro("switch_model")
        val sent = collectFrames { vm.selectModel(MODEL_HAIKU) }
        assertFalse(vm.showModelPicker.value)
        assertPressedKeyUsages("/model haiku\n", sent)
    }

    @Test
    fun `cancel closes sheet without firing`() = runBlocking {
        val vm = buildVm()
        vm.fireMacro("switch_model")
        assertTrue(vm.showModelPicker.value)

        val sent = collectFrames { vm.dismissModelPicker() }
        assertFalse("sheet must close after dismiss", vm.showModelPicker.value)
        assertTrue("dismiss must not send any frames; got ${sent.size}", sent.isEmpty())
    }

    @Test
    fun `non-switch_model slot still fires normally`() = runBlocking {
        val vm = buildVm()
        val sent = collectFrames { vm.fireMacro("approve") }
        // Approve is Literal("\n") → single Enter press + release.
        assertEquals(2, sent.size)
        val press = sent[0] as HidFrame.Keyboard
        assertEquals(Key.ENTER.usage, press.keys.single())
        assertFalse("sheet must remain closed", vm.showModelPicker.value)
    }

    /**
     * Codex has its own in-TUI model picker, so tapping `switch_model`
     * must forward the profile's literal `/model\n` and leave the in-app
     * bottom sheet untouched. See `add-codex-cursor-profiles` design
     * decision 3.
     */
    @Test
    fun `tap switch_model in codex profile fires literal and does not open sheet`() = runBlocking {
        val vm = buildVm(loadBundled("codex.json"))
        val sent = collectFrames { vm.fireMacro("switch_model") }
        assertFalse("sheet must remain closed for Codex", vm.showModelPicker.value)
        assertPressedKeyUsages("/model\n", sent)
    }

    /**
     * Cursor uses a Chord(PRIMARY, SLASH) — Cmd+/ on macOS, Ctrl+/ on Windows —
     * to cycle models in both `cursor-agent` and the Cursor / VS Code chat
     * panel. The tap must bypass [ModelPickerSheet] entirely and emit a single
     * press frame carrying the platform's primary modifier + SLASH, followed
     * by a release frame.
     */
    @Test
    fun `tap switch_model in cursor profile fires chord and does not open sheet`() = runBlocking {
        val vm = buildVm(loadBundled("cursor.json"))
        val sent = collectFrames { vm.fireMacro("switch_model") }
        assertFalse("sheet must remain closed for Cursor", vm.showModelPicker.value)

        val keyboardFrames = sent.filterIsInstance<HidFrame.Keyboard>()
        assertEquals(
            "expected one press + one release for Cmd/Ctrl+/, got $keyboardFrames",
            2,
            keyboardFrames.size,
        )
        val press = keyboardFrames[0]
        assertEquals(
            "press frame must carry SLASH usage",
            Key.SLASH.usage,
            press.keys.single(),
        )
        assertTrue(
            "press modifier byte must be non-zero (Cmd or Ctrl), got ${press.modifier}",
            press.modifier != 0,
        )
        val release = keyboardFrames[1]
        assertTrue("release frame must carry no keys", release.keys.isEmpty())
        assertEquals("release modifier byte must be zero", 0, release.modifier)
    }

    /**
     * Regression guard — no slot in a non-Claude profile should ever open
     * [ModelPickerSheet], even ones whose id happens to equal
     * [OperatorViewModel.SLOT_SWITCH_MODEL] or other anchors. This brute-force
     * iterates every slot of both non-Claude bundled profiles.
     */
    @Test
    fun `no slot opens sheet in non-claude profiles`() = runBlocking {
        for (file in listOf("codex.json", "cursor.json")) {
            val profile = loadBundled(file)
            for (slot in profile.slots) {
                val vm = buildVm(profile)
                vm.fireMacro(slot.id)
                assertFalse(
                    "profile ${profile.id} slot ${slot.id} must not open ModelPickerSheet",
                    vm.showModelPicker.value,
                )
            }
        }
    }

    // ---- helpers ------------------------------------------------------------

    /**
     * Runs [trigger] while collecting any frames [FakeHidTransport] emits. Subscribes
     * on `Main.immediate` (wired to an `UnconfinedTestDispatcher`) so the collector
     * latches before [trigger] runs; without that the `replay = 0` SharedFlow would
     * drop every emission.
     */
    private suspend fun collectFrames(trigger: suspend () -> Unit): List<HidFrame> {
        val collected = mutableListOf<HidFrame>()
        val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
        val collector = scope.launch(Dispatchers.Main.immediate) {
            transport.sentFrames
                .onSubscription { ready.complete(Unit) }
                .collect { collected += it }
        }
        ready.await()
        trigger()
        kotlinx.coroutines.yield()
        collector.cancel()
        return collected.toList()
    }

    private fun assertPressedKeyUsages(literal: String, frames: List<HidFrame>) {
        val pressUsages = frames
            .filterIsInstance<HidFrame.Keyboard>()
            .filter { it.keys.isNotEmpty() }
            .map { it.keys.single() }
        val expectedCount = literal.length
        assertEquals(
            "expected one press frame per char in '$literal', got $pressUsages",
            expectedCount,
            pressUsages.size,
        )
    }

    private fun buildVm(profile: Profile = loadBundled("claude-code.json")): OperatorViewModel {
        return OperatorViewModel(
            transport = transport,
            selectionsStore = selections,
            pairedHostsStore = pairedHosts,
            profile = profile,
        )
    }

    private fun loadBundled(fileName: String): Profile {
        val text = File("src/main/assets/profiles/$fileName").readText()
        return (ProfileLoader().loadFromString(text) as ProfileLoader.Result.Ok).profile
    }
}
