package com.vibepad.keyboard.ui

import android.os.SystemClock
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Touchpad surface. Gesture decoder:
 *
 *  - Single-finger drag → relative mouse movement.
 *  - Single tap (<[TAP_MAX_MS], <[TAP_SLOP_DP] travel) → left click.
 *  - Two-finger tap → right click.
 *  - Two-finger vertical drag → wheel scroll.
 *  - Long-press (>[LONG_PRESS_MS] stationary) then drag → drag with left button held.
 *
 * Single `awaitPointerEventScope` + manual state machine. Works identically under
 * Paparazzi snapshot tests and on-device because no framework gesture detector is
 * in play.
 */
@Composable
fun TouchpadSurface(
    controller: TouchpadController,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val tapSlopPx = with(density) { TAP_SLOP_DP.dp.toPx() }
    val scrollUnitPx = with(density) { SCROLL_UNIT_DP.dp.toPx() }
    val context = LocalContext.current

    val surfaceShape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .clip(surfaceShape)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = surfaceShape,
            )
            .pointerInput(Unit) {
                while (true) {
                    awaitPointerEventScope {
                        // Wait for the first down.
                        var event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.changes.none { it.pressed }) return@awaitPointerEventScope
                        val gestureStartUptime = SystemClock.uptimeMillis()
                        val firstPosition = event.changes.first { it.pressed }.position
                        var maxPointers = event.changes.count { it.pressed }
                        var totalTravelPx = 0f
                        var dragHeld = false
                        var longPressArmed = true
                        var accumulatedScrollPx = 0f

                        // Drain events until every pointer lifts.
                        while (true) {
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            maxPointers = maxOf(maxPointers, pressed.size)

                            val now = SystemClock.uptimeMillis()

                            // Long-press arm → enter drag-held mode when stationary.
                            if (longPressArmed
                                && !dragHeld
                                && pressed.size == 1
                                && now - gestureStartUptime >= LONG_PRESS_MS
                            ) {
                                val distanceFromStart = (pressed.first().position - firstPosition).getDistance()
                                if (distanceFromStart < tapSlopPx) {
                                    dragHeld = true
                                    controller.onLeftButtonDown()
                                    Haptics.micro(context)
                                }
                            }

                            if (pressed.size == 1) {
                                val pc = pressed.first()
                                val delta = pc.position - pc.previousPosition
                                if (delta != Offset.Zero) {
                                    val dx = (delta.x * POINTER_GAIN).roundToInt()
                                    val dy = (delta.y * POINTER_GAIN).roundToInt()
                                    if (dx != 0 || dy != 0) controller.onMove(dx, dy)
                                    totalTravelPx += delta.getDistance()
                                    if (totalTravelPx > tapSlopPx) longPressArmed = false
                                    pc.consume()
                                }
                            } else {
                                // 2+ pointers → treat as wheel. Average Y movement.
                                val avgDy = pressed.sumOf { (it.position.y - it.previousPosition.y).toDouble() }
                                    .toFloat() / pressed.size
                                accumulatedScrollPx += avgDy
                                while (abs(accumulatedScrollPx) >= scrollUnitPx) {
                                    val ticks = if (accumulatedScrollPx > 0) -1 else 1
                                    controller.onScroll(ticks)
                                    accumulatedScrollPx += if (ticks == 1) scrollUnitPx else -scrollUnitPx
                                }
                                pressed.forEach { it.consume() }
                                longPressArmed = false
                            }

                            event = awaitPointerEvent(PointerEventPass.Main)
                        }

                        // Gesture over — classify if it was a tap.
                        val gestureDurationMs = SystemClock.uptimeMillis() - gestureStartUptime
                        if (dragHeld) {
                            controller.onLeftButtonUp()
                        } else if (totalTravelPx < tapSlopPx && gestureDurationMs < TAP_MAX_MS) {
                            if (maxPointers >= 2) {
                                controller.onRightTap()
                                Haptics.regular(context)
                            } else {
                                controller.onLeftTap()
                                Haptics.regular(context)
                            }
                        }
                    }
                }
            },
    )
}

/** Callback interface the touchpad emits into. */
interface TouchpadController {
    fun onMove(dX: Int, dY: Int)
    fun onScroll(wheelTicks: Int)
    fun onLeftTap()
    fun onRightTap()
    fun onLeftButtonDown()
    fun onLeftButtonUp()
}

// ---- tunables ---------------------------------------------------------------

private const val TAP_SLOP_DP = 12
private const val SCROLL_UNIT_DP = 24
private const val TAP_MAX_MS = 300L
private const val LONG_PRESS_MS = 500L
private const val POINTER_GAIN = 2.2f
