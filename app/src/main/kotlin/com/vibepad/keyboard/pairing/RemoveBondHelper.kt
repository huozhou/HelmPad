package com.vibepad.keyboard.pairing

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Wrapper around `BluetoothDevice.removeBond()`.
 *
 * The method has been in AOSP since API 19, but the SDK has always marked it
 * `@hide` and therefore omitted it from `android.jar`. Our options are:
 *
 *  - **Reflection** — call through `Class#getMethod("removeBond")`. Works on
 *    stock AOSP, Pixel, and the majority of OEM skins. Fails hard on:
 *      * some ColorOS / HyperOS builds that strip the method (NoSuchMethodException),
 *      * Android 12+ OEMs that added runtime gating (SecurityException),
 *      * the occasional `returned false` because the device is currently
 *        connected on another profile.
 *  - **Fallback** — launch `Settings.ACTION_BLUETOOTH_SETTINGS` so the user can
 *    finish the removal by hand, paired with a snackbar instruction in the UI.
 *
 * The public API returns a [Result] sealed type so callers can distinguish
 * "we fully removed it" (UI updates optimistically, the OS sends the bond
 * state change shortly after) from "user has to finish this in Settings".
 *
 * We intentionally do **not** expose a generic throw-and-hope API — a silent
 * no-op would make users re-click Forget forever.
 */
object RemoveBondHelper {

    private const val TAG = "VibePad/removeBond"

    sealed interface Result {
        /** The reflection path returned `true`. The bond is gone (or will be momentarily). */
        object Success : Result
        /** Reflection unavailable or refused — we opened system Bluetooth settings instead. */
        object FallbackOpenedSettings : Result
        /** We couldn't even open the fallback settings page. Treat as hard error. */
        data class Error(val throwable: Throwable) : Result
    }

    /**
     * Try to remove the bond via reflection. On any failure, open system
     * Bluetooth settings so the user can complete the removal manually.
     */
    fun forget(context: Context, device: BluetoothDevice): Result {
        val outcome = classifyInvocation {
            val method = device.javaClass.getMethod("removeBond")
            method.invoke(device) as? Boolean
        }
        when (outcome) {
            ReflectionOutcome.Success -> return Result.Success
            ReflectionOutcome.Missing -> Log.w(TAG, "removeBond() not present on this ROM")
            ReflectionOutcome.SecurityBlocked -> Log.w(TAG, "removeBond() denied by runtime policy")
            ReflectionOutcome.Refused -> Log.w(TAG, "removeBond() refused; falling back to settings")
        }
        return openBluetoothSettings(context)
    }

    /**
     * Pure decision tree, split from the reflective call so unit tests can
     * drive each branch by passing a hand-rolled invocation lambda. No
     * Android framework dependencies on purpose — that keeps the classifier
     * testable on a plain JVM without tripping `android.util.Log`'s native
     * bridge.
     */
    internal fun classifyInvocation(invoke: () -> Boolean?): ReflectionOutcome {
        return try {
            if (invoke() == true) ReflectionOutcome.Success else ReflectionOutcome.Refused
        } catch (_: NoSuchMethodException) {
            ReflectionOutcome.Missing
        } catch (_: SecurityException) {
            ReflectionOutcome.SecurityBlocked
        } catch (_: Throwable) {
            ReflectionOutcome.Refused
        }
    }

    private fun openBluetoothSettings(context: Context): Result {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Result.FallbackOpenedSettings
        } catch (t: Throwable) {
            Log.e(TAG, "Could not open ACTION_BLUETOOTH_SETTINGS", t)
            Result.Error(t)
        }
    }

    /** Internal so unit tests can drive the decision tree without a real device. */
    internal enum class ReflectionOutcome { Success, Refused, Missing, SecurityBlocked }
}
