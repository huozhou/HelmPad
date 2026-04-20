package com.vibepad.keyboard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibepad.keyboard.VibePadApplication
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.HidTransport
import com.vibepad.keyboard.input.HidFrame
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.input.InputAction
import com.vibepad.keyboard.input.InputActionEngine
import com.vibepad.keyboard.macro.MacroDefinition
import com.vibepad.keyboard.macro.Profile
import com.vibepad.keyboard.macro.SelectionsStore
import com.vibepad.keyboard.pairing.PairedHostsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Holds the operator screen's run-time state: which host target is selected (so the
 * [InputActionEngine] resolves modifiers correctly) and how to fan out touchpad
 * callbacks into HID frames.
 *
 * Profile is injected rather than loaded here so the caller can wait on a valid
 * load diagnostic before constructing the VM.
 */
class OperatorViewModel(
    private val transport: HidTransport,
    private val selectionsStore: SelectionsStore,
    private val pairedHostsStore: PairedHostsStore,
    private val profile: Profile,
) : ViewModel() {

    private val _hostTarget = MutableStateFlow(HostTarget.MACOS)

    /** Host target feeding the [InputActionEngine]. Observed so the keymap updates live. */
    val hostTarget: StateFlow<HostTarget> = _hostTarget.asStateFlow()

    private val engine = InputActionEngine(
        transport = transport,
        scope = viewModelScope,
        hostTarget = { _hostTarget.value },
    )

    val linkState: StateFlow<HidLinkState> = transport.state

    private val _showModelPicker = MutableStateFlow(false)

    /**
     * True while the Switch-Model bottom sheet is visible. Tapping the `switch_model`
     * macro slot flips this on instead of firing `/model\n`, so the user can pick a
     * concrete model in one shot (`/model opus`, etc.) rather than navigating the
     * two-step TUI menu by hand. See `claude-code-profile-trim` design decision 2.
     */
    val showModelPicker: StateFlow<Boolean> = _showModelPicker.asStateFlow()

    /**
     * Wires `_hostTarget` to the resolution chain
     *
     *   userOverride > detectedHostTarget > SelectionsStore (per-mac legacy)
     *                                     > MACOS (cold-start fallback)
     *
     * `flatMapLatest` is used twice — once on `linkState` so switching between
     * hosts immediately re-subscribes to that host's row in
     * `pairedHostsStore.flow()`, and once inside that row to fall back to
     * `SelectionsStore` whenever neither the override nor the detector has
     * a verdict. The result is hot-collected for the lifetime of the VM, so
     * the engine always reads the latest value out of `_hostTarget`.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun attachToHost() {
        viewModelScope.launch {
            linkState
                .flatMapLatest { state ->
                    if (state !is HidLinkState.Connected) {
                        flowOf<HostTarget?>(null)
                    } else {
                        val mac = state.host.address.uppercase()
                        combine(
                            pairedHostsStore.flow(),
                            selectionsStore.hostTargetFlow(mac),
                        ) { hosts, legacy ->
                            val effective = hosts.firstOrNull { it.mac == mac }?.effectiveHostTarget
                            effective ?: legacy
                        }
                    }
                }
                .collect { resolved ->
                    if (resolved != null) _hostTarget.value = resolved
                }
        }
    }

    /**
     * Manual override from the AppBar (legacy v1 flow). Writes both stores so
     * the choice survives a Re-detect: `pairedHostsStore` is the new
     * source-of-truth, `selectionsStore` keeps the cold-start default
     * functional for users who never opened Paired Hosts.
     */
    fun selectHostTarget(target: HostTarget) {
        _hostTarget.value = target
        val host = (linkState.value as? HidLinkState.Connected)?.host
        if (host != null) {
            viewModelScope.launch {
                pairedHostsStore.recordOverride(host.address, target)
                selectionsStore.setHostTarget(host.address, target)
            }
        }
    }

    fun fireMacro(slotId: String) {
        // ModelPickerSheet is Claude-only (see `add-codex-cursor-profiles`
        // design decision 3). Codex and Cursor handle model switching
        // themselves — for those, tapping switch_model just forwards the
        // slot's JSON-declared action: Codex uses `Literal("/model\n")`
        // (drops into its TUI `/model` menu), Cursor uses
        // `Chord(PRIMARY, SLASH)` (Cmd+/ on macOS, Ctrl+/ on Windows) so
        // the same button works in `cursor-agent` and in the Cursor /
        // VS Code chat panel.
        if (slotId == SLOT_SWITCH_MODEL && profile.id == CLAUDE_PROFILE_ID) {
            _showModelPicker.value = true
            return
        }
        val slot = profile.slots.firstOrNull { it.id == slotId } ?: return
        fire(slot)
    }

    fun fire(slot: MacroDefinition) = fire(slot.action)

    fun fire(action: InputAction) = engine.fire(action)

    /**
     * Picked a concrete model from [ModelPickerSheet]. Fires `/model <id>\n` as a
     * single literal and dismisses the sheet. Haptics have already been played by
     * the picker row itself.
     */
    fun selectModel(modelId: String) {
        engine.fire(InputAction.Literal("/model $modelId\n"))
        _showModelPicker.value = false
    }

    /** Sheet dismissed without a selection. No engine traffic, no haptic. */
    fun dismissModelPicker() {
        _showModelPicker.value = false
    }

    // ---- Touchpad bridge ----------------------------------------------------

    val touchpadController: TouchpadController = object : TouchpadController {
        override fun onMove(dX: Int, dY: Int) {
            transport.sendMouse(HidFrame.Mouse(buttons = 0, dX = dX, dY = dY))
        }
        override fun onScroll(wheelTicks: Int) {
            transport.sendMouse(HidFrame.Mouse(buttons = 0, dX = 0, dY = 0, wheel = wheelTicks))
        }
        override fun onLeftTap() {
            transport.sendMouse(HidFrame.Mouse(buttons = HidFrame.Mouse.BUTTON_LEFT, dX = 0, dY = 0))
            transport.sendMouse(HidFrame.Mouse.RELEASE)
        }
        override fun onRightTap() {
            transport.sendMouse(HidFrame.Mouse(buttons = HidFrame.Mouse.BUTTON_RIGHT, dX = 0, dY = 0))
            transport.sendMouse(HidFrame.Mouse.RELEASE)
        }
        override fun onLeftButtonDown() {
            transport.sendMouse(HidFrame.Mouse(buttons = HidFrame.Mouse.BUTTON_LEFT, dX = 0, dY = 0))
        }
        override fun onLeftButtonUp() {
            transport.sendMouse(HidFrame.Mouse.RELEASE)
        }
    }

    companion object {
        /** Slot id that opens [ModelPickerSheet] instead of firing its JSON action. */
        const val SLOT_SWITCH_MODEL = "switch_model"

        /**
         * Profile id gating [ModelPickerSheet]. Only Claude Code has a
         * hard-coded model list; other profiles that declare a `switch_model`
         * slot fall through to the slot's JSON action. See design decision 3.
         */
        const val CLAUDE_PROFILE_ID = "profile.claude-code"

        fun factory(app: VibePadApplication, profile: Profile): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    OperatorViewModel(
                        transport = app.hidTransport,
                        selectionsStore = app.selectionsStore,
                        pairedHostsStore = app.pairedHostsStore,
                        profile = profile,
                    ).also { it.attachToHost() }
                }
            }
    }
}
