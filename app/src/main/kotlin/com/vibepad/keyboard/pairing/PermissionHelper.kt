package com.vibepad.keyboard.pairing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Thin static helper over the runtime permissions the HID peripheral role needs.
 *
 *  - API 31+ (`Build.VERSION.S`): must request `BLUETOOTH_CONNECT` and
 *    `BLUETOOTH_ADVERTISE` at runtime. Without both we can't even read the adapter
 *    name.
 *  - API 28-30: the legacy `BLUETOOTH` + `BLUETOOTH_ADMIN` manifest permissions are
 *    granted at install time; no runtime request is necessary.
 */
object PermissionHelper {

    /** Full set of runtime permissions this build targets. */
    fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            emptyArray()
        }
    }

    fun allGranted(context: Context): Boolean {
        return requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
