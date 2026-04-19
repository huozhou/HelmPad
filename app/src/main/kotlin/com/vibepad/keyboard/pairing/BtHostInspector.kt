package com.vibepad.keyboard.pairing

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.util.Log
import com.vibepad.keyboard.input.HostTarget

/**
 * Looks at a freshly-bonded Bluetooth host and tries to decide whether it's a
 * Mac or a Windows machine, so the operator screen can pick the right modifier
 * mapping (Cmd vs. Ctrl) without the user touching a switch.
 *
 * Three signals, evaluated in priority order — the first match wins:
 *
 *  1. **Device name** (HIGH) — strong matches like `"MacBook Pro"`,
 *     `"DESKTOP-7ABC123"`, `"Surface Laptop 5"`, etc. People rarely rename
 *     their machines, and when they do they usually keep something
 *     vendor-flavored. False positives here are very rare.
 *  2. **BluetoothClass.Major == COMPUTER** (gate) — if the class doesn't say
 *     it's a computer at all, abort. Lots of phones, tablets, and AV devices
 *     also have Bluetooth names containing "mac" or "windows", and we don't
 *     want to mislabel a `mac mini`-shaped speaker.
 *  3. **OUI prefix** (LOW) — the first 3 bytes of the MAC, looked up in our
 *     bundled vendor table. Apple OUIs map to MACOS; Microsoft / Lenovo /
 *     Dell / HP map to WINDOWS. Confidence is LOW because Apple OUIs also
 *     show up in iPads and other non-Mac Apple devices, so the picker
 *     sheet lets the user override.
 *
 * The inspector is **deterministic, side-effect-free, and Android-API
 * independent**: it operates on a small [Probe] DTO so unit tests can feed
 * synthetic devices without touching the Bluetooth stack. The
 * [inspect(device)] overload is a thin Android wrapper that builds a Probe
 * from a real `BluetoothDevice`.
 */
class BtHostInspector(private val ouiHints: OuiVendorHints) {

    /**
     * The minimal slice of a `BluetoothDevice` needed to make a verdict. Stays
     * Android-free on purpose — pure data, easy to construct in tests.
     */
    data class Probe(
        val name: String?,
        val majorClass: Int?,
        val address: String,
    )

    /**
     * Pure decision function — Android-framework-free on purpose so unit tests
     * can drive every branch on a plain JVM without tripping
     * `android.util.Log`'s native bridge. The Bluetooth-aware overload below
     * handles logging and `BluetoothDevice` extraction.
     */
    fun inspect(probe: Probe): HostGuess {
        val name = probe.name.orEmpty()
        if (MAC_NAME_REGEX.containsMatchIn(name) || MAC_OWNED_REGEX.containsMatchIn(name)) {
            return HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME)
        }
        if (WINDOWS_NAME_REGEX.containsMatchIn(name)) {
            return HostGuess(HostTarget.WINDOWS, Confidence.HIGH, Source.NAME)
        }
        if (probe.majorClass != BluetoothClass.Device.Major.COMPUTER) {
            return HostGuess.NONE
        }
        val ouiTarget = ouiHints.lookup(probe.address.take(8))
        if (ouiTarget != null) {
            return HostGuess(ouiTarget, Confidence.LOW, Source.OUI)
        }
        return HostGuess.NONE
    }

    /**
     * Convenience wrapper over a real `BluetoothDevice`. `device.name` is
     * guarded by `BLUETOOTH_CONNECT` on API 31+; we suppress the lint because
     * the only caller is [HidForegroundService], which already holds the
     * permission (it runs only after onboarding).
     */
    @SuppressLint("MissingPermission")
    fun inspect(device: BluetoothDevice): HostGuess {
        val probe = Probe(
            name = runCatching { device.name }.getOrNull(),
            majorClass = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull(),
            address = device.address.uppercase(),
        )
        val guess = inspect(probe)
        Log.i(TAG, "inspect ${probe.address.take(8)} name=\"${probe.name}\" major=${probe.majorClass} → $guess")
        return guess
    }

    companion object {
        private const val TAG = "BtHostInspector"

        // Apple desktop / laptop product names. Word boundaries keep us from
        // matching "Mac" inside random brand names.
        internal val MAC_NAME_REGEX = Regex(
            "(?i)\\b(macbook|imac|mac\\s?mini|mac\\s?studio|mac\\s?pro|mbp|mba)\\b",
        )

        // The "<Owner>'s Mac" / "<Owner>'s MacBook" pattern macOS auto-generates
        // for fresh installs. Allows curly apostrophe (\u2019) and the
        // straight one. Owner segment is constrained to letters/digits/_ to
        // avoid runaway matches on long sentences.
        internal val MAC_OWNED_REGEX = Regex(
            "(?i)\\b[a-z0-9_]+[\u2019']?s?\\s+(mac|macbook)\\b",
        )

        // Windows: the auto-generated `DESKTOP-XXXXXXX` / `LAPTOP-XXXXXXX`
        // host names, plus a small handful of OEM brand words people leave
        // unchanged, plus the literal word "windows".
        internal val WINDOWS_NAME_REGEX = Regex(
            "(?i)^(desktop-|laptop-)[a-z0-9]{7,}$|(?i)\\b(surface|thinkpad|thinkbook|alienware|yoga)\\b|(?i)\\bwindows\\b",
        )
    }
}
