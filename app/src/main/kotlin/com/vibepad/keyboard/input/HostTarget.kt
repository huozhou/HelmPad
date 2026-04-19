package com.vibepad.keyboard.input

import kotlinx.serialization.Serializable

/**
 * The operating system the phone is currently paired to.
 *
 * Only influences the modifier-byte binding for abstract [Mod]s. The HID usage codes
 * themselves are platform-independent — both macOS and Windows read the same
 * US-QWERTY HID boot-protocol layout.
 */
@Serializable
enum class HostTarget {
    MACOS,
    WINDOWS,
}
