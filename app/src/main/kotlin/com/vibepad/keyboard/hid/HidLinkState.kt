package com.vibepad.keyboard.hid

/**
 * Single source of truth for the HID link's lifecycle.
 *
 * Mirrors the state machine in design.md D3.
 *
 *  Idle → Proxying → Advertising → Connected → Reconnecting ↩
 *                                           ↓
 *                                        (retries exhausted? no — we keep retrying
 *                                         until the user cancels; see `Failed` for
 *                                         terminal states only.)
 *
 * `Unavailable` is reached from any state when the environment (permissions,
 * bluetooth radio, device HID peripheral capability) is missing. Once the
 * precondition recovers the state machine re-enters `Proxying`.
 */
sealed interface HidLinkState {

    object Idle : HidLinkState

    data class Unavailable(val reason: UnavailableReason) : HidLinkState

    /** Obtaining the `BluetoothHidDevice` profile proxy from the adapter. */
    object Proxying : HidLinkState

    /** Registered; waiting for a paired host to initiate the HID connection. */
    object Advertising : HidLinkState

    data class Connected(val host: HostDevice) : HidLinkState

    /** Just lost the link. [attempt] is 1-indexed and monotonic until the link recovers. */
    data class Reconnecting(val attempt: Int, val previousHost: HostDevice?) : HidLinkState

    /** Terminal failure — requires an explicit user action to retry. */
    data class Failed(val cause: FailureCause) : HidLinkState
}

/** Why the system cannot currently act as a HID peripheral. */
enum class UnavailableReason {
    BLUETOOTH_OFF,
    PERMISSIONS_MISSING,
    DEVICE_NO_HID_PERIPHERAL,
    BUNDLED_PROFILE_INVALID,
}

/** Why a registration attempt terminated non-recoverably. */
enum class FailureCause {
    REGISTER_APP_REJECTED,
    PROXY_TIMEOUT,
    UNEXPECTED,
}
