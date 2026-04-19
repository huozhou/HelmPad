package com.vibepad.keyboard.hid

import com.vibepad.keyboard.input.HidFrame
import kotlinx.coroutines.flow.StateFlow

/**
 * Abstraction over "emit HID reports on the wire".
 *
 * The real implementation ([AndroidHidTransport]) wraps `BluetoothHidDevice`; the fake
 * ([FakeHidTransport]) records emitted frames into a channel for testing.
 *
 * All `send*` methods are non-blocking. Actual emission is throttled internally so the
 * BLE peripheral link isn't overwhelmed. Callers can fire at any rate.
 *
 * Implementations MUST be safe to call from any thread.
 */
interface HidTransport {

    val state: StateFlow<HidLinkState>

    /** Begin acquiring the HID profile proxy and register the app. Idempotent. */
    fun start()

    /** Unregister the app and release the proxy. Idempotent. */
    fun stop()

    /** Enqueue a keyboard frame. No-op when not `Connected`. */
    fun sendKeyboard(frame: HidFrame.Keyboard)

    /** Enqueue a mouse frame. No-op when not `Connected`. */
    fun sendMouse(frame: HidFrame.Mouse)

    /** User pressed "retry" after a `Failed` or `Unavailable` state. */
    fun retry()
}
