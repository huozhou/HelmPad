package com.vibepad.keyboard.pairing

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.vibepad.keyboard.hid.HostDevice

/**
 * Abstraction over Bluetooth / power manager reads the onboarding wizard needs.
 *
 * The [AndroidBluetoothEnvironment] implementation is the real thing. A fake is
 * trivial to inject in tests (see `OnboardingViewModelTest`).
 */
interface BluetoothEnvironment {
    fun isBluetoothEnabled(): Boolean
    fun bondedHosts(): List<HostDevice>
    fun isIgnoringBatteryOptimizations(): Boolean
}

/**
 * Read-only snapshot of Bluetooth-related environment: whether the adapter is
 * enabled, and which already-bonded devices exist. The onboarding screen polls this
 * every ~1s while on the pairing step.
 *
 * Android 31+ requires BLUETOOTH_CONNECT to enumerate bonded devices or read the
 * adapter's name. The helper fails closed — no permission → empty list — so the
 * wizard's pairing step keeps spinning until the user grants the permission.
 */
class AndroidBluetoothEnvironment(private val context: Context) : BluetoothEnvironment {

    override fun isBluetoothEnabled(): Boolean = adapter()?.isEnabled == true

    @SuppressLint("MissingPermission")
    override fun bondedHosts(): List<HostDevice> {
        if (!hasBluetoothConnect()) return emptyList()
        val adapter = adapter() ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().mapNotNull { device ->
                val address = device.address ?: return@mapNotNull null
                HostDevice(name = device.name ?: address, address = address)
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    override fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun hasBluetoothConnect(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun adapter(): BluetoothAdapter? =
        context.getSystemService<BluetoothManager>()?.adapter
}
