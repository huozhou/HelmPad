package com.vibepad.keyboard.input

import com.vibepad.keyboard.hid.HidTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Routes "user pressed macro X" events into HID frames on the wire.
 *
 * Splits cleanly into two stages:
 *   1. [KeymapResolver] turns an abstract [InputAction] + [HostTarget] into
 *      concrete [HidFrame]s interleaved with any inter-step delays.
 *   2. [HidTransport] accepts those frames. Delays are honored here (via `delay`)
 *      so the transport stays simple.
 *
 * All work happens on [scope]. Callers just fire-and-forget.
 */
class InputActionEngine(
    private val transport: HidTransport,
    private val scope: CoroutineScope,
    private val hostTarget: () -> HostTarget,
) {

    /** Emit the resolved frames for [action]. Returns immediately. */
    fun fire(action: InputAction) {
        scope.launch { emit(action) }
    }

    internal suspend fun emit(action: InputAction) {
        val steps = KeymapResolver.resolve(action, hostTarget())
        for (step in steps) when (step) {
            is KeymapResolver.ResolvedStep.Frame -> when (val f = step.frame) {
                is HidFrame.Keyboard -> transport.sendKeyboard(f)
                is HidFrame.Mouse -> transport.sendMouse(f)
            }
            is KeymapResolver.ResolvedStep.DelayMs -> delay(step.millis)
        }
    }
}
