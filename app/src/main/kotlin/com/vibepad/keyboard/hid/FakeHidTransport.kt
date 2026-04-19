package com.vibepad.keyboard.hid

import com.vibepad.keyboard.input.HidFrame
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Test double for [HidTransport]. Does not touch Bluetooth at all.
 *
 *  - Starts in [HidLinkState.Idle]. Use [forceState] to advance through the state
 *    machine exactly the way the unit under test expects.
 *  - Sent frames land in [sentFrames]. Tests can collect and assert on them without
 *    any time-based flakiness.
 *  - Lives in `main` (not `test`) so the service and UI layers can also depend on it
 *    from the same source set for Paparazzi/Preview rendering.
 */
class FakeHidTransport : HidTransport {

    private val _state = MutableStateFlow<HidLinkState>(HidLinkState.Idle)
    override val state: StateFlow<HidLinkState> = _state.asStateFlow()

    private val _sentFrames = MutableSharedFlow<HidFrame>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val sentFrames: SharedFlow<HidFrame> = _sentFrames

    private val startCalls = mutableListOf<Unit>()
    private val stopCalls = mutableListOf<Unit>()

    override fun start() {
        startCalls += Unit
        if (_state.value is HidLinkState.Idle) _state.value = HidLinkState.Proxying
    }

    override fun stop() {
        stopCalls += Unit
        _state.value = HidLinkState.Idle
    }

    override fun sendKeyboard(frame: HidFrame.Keyboard) {
        _sentFrames.tryEmit(frame)
    }

    override fun sendMouse(frame: HidFrame.Mouse) {
        _sentFrames.tryEmit(frame)
    }

    override fun retry() {
        // Equivalent to a fresh start: reset to Idle so start() can transition
        // back into Proxying, matching how the real transport re-registers.
        if (_state.value !is HidLinkState.Connected) {
            _state.value = HidLinkState.Idle
            start()
        }
    }

    /** Test hook: drive state transitions deterministically. */
    fun forceState(next: HidLinkState) { _state.value = next }

    /** Count of [start] calls seen so far. */
    val startCount: Int get() = startCalls.size

    /** Count of [stop] calls seen so far. */
    val stopCount: Int get() = stopCalls.size
}
