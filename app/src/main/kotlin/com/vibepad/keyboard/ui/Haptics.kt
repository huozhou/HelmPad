package com.vibepad.keyboard.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService

/**
 * Tiny helper so macro buttons and touchpad taps have physical presence without every
 * caller having to mind API-level differences.
 *
 *  - Regular tap: short (~10ms) tick.
 *  - Destructive tap: double tick (~40ms total), louder.
 *  - Scroll edge: micro tick.
 */
internal object Haptics {

    fun regular(context: Context) = vibrate(context, longArrayOf(0, 12), amplitude = 120)

    fun destructive(context: Context) = vibrate(context, longArrayOf(0, 16, 24, 16), amplitude = 200)

    fun micro(context: Context) = vibrate(context, longArrayOf(0, 6), amplitude = 80)

    private fun vibrate(context: Context, timings: LongArray, amplitude: Int) {
        val vibrator = vibrator(context) ?: return
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val amps = IntArray(timings.size) { i -> if (i == 0) 0 else amplitude.coerceIn(1, 255) }
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
        } else {
            vibrator.vibrate(timings, -1)
        }
    }

    private fun vibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService<Vibrator>()
        }
    }
}
