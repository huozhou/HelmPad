package com.vibepad.keyboard.hid

import com.vibepad.keyboard.input.HidFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Rate-limits outbound HID frames to at most [MAX_HZ] of each kind (keyboard, mouse).
 *
 * Callers [offer] frames; the throttler sequences them and invokes [sink] on a single
 * coroutine per kind with a minimum inter-frame gap of [MIN_GAP_MS].
 *
 * Two separate coroutines — keyboard and mouse — share no state beyond the sink, so
 * bursts on one kind don't delay the other.
 *
 * Large mouse deltas are fragmented: any frame whose |dX| or |dY| exceeds
 * [HidFrame.Mouse.MAX_AXIS_DELTA] is split into multiple sequential frames. This keeps
 * per-frame values well within the 16-bit range and produces smoother pointer motion
 * on MacBooks whose BLE HID parser debounces unusually large steps.
 */
class HidSendThrottler(
    private val scope: CoroutineScope,
    private val sink: suspend (HidFrame) -> Unit,
) {

    // Unbounded because an individual frame is ~16 bytes; we'd rather hold a whole
    // typed-out paragraph in memory than drop keystrokes. The producer side rate is
    // bounded by user input, not by the throttler.
    private val keyboardChannel = Channel<HidFrame.Keyboard>(capacity = Channel.UNLIMITED)
    private val mouseChannel = Channel<HidFrame.Mouse>(capacity = Channel.UNLIMITED)

    private val _emitted = MutableSharedFlow<HidFrame>(replay = 0, extraBufferCapacity = 64)

    /** Test-only view of what was actually sent to [sink]. */
    val emitted: SharedFlow<HidFrame> = _emitted

    private var keyboardJob: Job? = null
    private var mouseJob: Job? = null

    fun start() {
        if (keyboardJob == null) keyboardJob = scope.launch { drain(keyboardChannel) }
        if (mouseJob == null) mouseJob = scope.launch { drain(mouseChannel) }
    }

    fun stop() {
        keyboardJob?.cancel(); keyboardJob = null
        mouseJob?.cancel(); mouseJob = null
        keyboardChannel.close()
        mouseChannel.close()
    }

    fun offer(frame: HidFrame.Keyboard) {
        keyboardChannel.trySend(frame)
    }

    fun offer(frame: HidFrame.Mouse) {
        for (split in fragment(frame)) mouseChannel.trySend(split)
    }

    private suspend inline fun <reified T : HidFrame> drain(channel: Channel<T>) {
        for (frame in channel) {
            sink(frame)
            _emitted.tryEmit(frame)
            delay(MIN_GAP_MS)
        }
    }

    /**
     * Fragments a mouse frame into pieces whose |dX| and |dY| are each at most
     * [HidFrame.Mouse.MAX_AXIS_DELTA]. The button and wheel values are attached to the
     * first fragment; subsequent fragments carry movement only (buttons 0, wheel 0)
     * so a "press" isn't replayed on every fragment.
     */
    internal fun fragment(frame: HidFrame.Mouse): List<HidFrame.Mouse> {
        val cap = HidFrame.Mouse.MAX_AXIS_DELTA
        val absX = kotlin.math.abs(frame.dX)
        val absY = kotlin.math.abs(frame.dY)
        if (absX <= cap && absY <= cap) return listOf(frame)

        val signX = if (frame.dX >= 0) 1 else -1
        val signY = if (frame.dY >= 0) 1 else -1
        val steps = kotlin.math.max(
            (absX + cap - 1) / cap,
            (absY + cap - 1) / cap,
        )
        val out = ArrayList<HidFrame.Mouse>(steps)
        var remainingX = absX
        var remainingY = absY
        for (i in 0 until steps) {
            val takeX = kotlin.math.min(remainingX, cap)
            val takeY = kotlin.math.min(remainingY, cap)
            out += HidFrame.Mouse(
                buttons = if (i == 0) frame.buttons else 0,
                dX = signX * takeX,
                dY = signY * takeY,
                wheel = if (i == 0) frame.wheel else 0,
            )
            remainingX -= takeX
            remainingY -= takeY
        }
        return out
    }

    companion object {
        const val MAX_HZ = 125
        const val MIN_GAP_MS = 1000L / MAX_HZ // 8 ms
    }
}
