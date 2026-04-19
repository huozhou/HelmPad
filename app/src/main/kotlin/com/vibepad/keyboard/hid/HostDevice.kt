package com.vibepad.keyboard.hid

/**
 * Minimal host descriptor kept away from `BluetoothDevice` so pure state (and tests)
 * can reason about the link without pulling in Android SDK types.
 *
 * [address] is the MAC address of the paired host (case-insensitive colon-separated).
 * We use it as the stable identity key when persisting per-host target preferences.
 */
data class HostDevice(
    val name: String,
    val address: String,
)
