package com.vibepad.keyboard.hid

import com.vibepad.keyboard.input.HidFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HidSendThrottlerTest {

    // ---- Fragmentation logic (pure, no coroutines) ------------------------------

    @Test
    fun small_mouse_delta_is_not_fragmented() {
        val t = HidSendThrottler(TestScope(), sink = { })
        val frame = HidFrame.Mouse(buttons = 0, dX = 10, dY = -20)
        val out = t.fragment(frame)
        assertEquals(listOf(frame), out)
    }

    @Test
    fun oversized_dX_fragments_into_sum_matching_frames() {
        val t = HidSendThrottler(TestScope(), sink = { })
        val frame = HidFrame.Mouse(buttons = 0, dX = 400, dY = -10, wheel = 0)
        val out = t.fragment(frame)
        // Fragments must sum to the original delta.
        assertEquals(400, out.sumOf { it.dX })
        assertEquals(-10, out.sumOf { it.dY })
        // And none exceed the cap.
        out.forEach {
            assertTrue(kotlin.math.abs(it.dX) <= HidFrame.Mouse.MAX_AXIS_DELTA)
            assertTrue(kotlin.math.abs(it.dY) <= HidFrame.Mouse.MAX_AXIS_DELTA)
        }
    }

    @Test
    fun fragmentation_only_replays_button_on_first_fragment() {
        val t = HidSendThrottler(TestScope(), sink = { })
        val frame = HidFrame.Mouse(
            buttons = HidFrame.Mouse.BUTTON_LEFT,
            dX = 500,
            dY = 0,
            wheel = 3,
        )
        val out = t.fragment(frame)
        assertEquals(HidFrame.Mouse.BUTTON_LEFT, out.first().buttons)
        assertEquals(3, out.first().wheel)
        out.drop(1).forEach {
            assertEquals(0, it.buttons)
            assertEquals(0, it.wheel)
        }
    }

    // ---- Throttling via virtual time --------------------------------------------

    @Test
    fun keyboard_frames_are_drained_with_min_gap() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val delivered = mutableListOf<HidFrame>()
        val throttler = HidSendThrottler(scope, sink = { delivered += it })
        throttler.start()

        val frame = HidFrame.Keyboard(modifier = 0, keys = listOf(0x04))
        repeat(5) { throttler.offer(frame) }

        // Let everything scheduled run.
        advanceUntilIdle()
        assertEquals(5, delivered.size)

        // 5 frames separated by at least MIN_GAP_MS. After the fifth, virtual time
        // must be >= 4 × MIN_GAP_MS (first frame is immediate, then 4 gaps).
        val elapsed = testScheduler.currentTime
        assertTrue(
            "elapsed=$elapsed but expected >= ${4 * HidSendThrottler.MIN_GAP_MS}",
            elapsed >= 4 * HidSendThrottler.MIN_GAP_MS,
        )

        throttler.stop()
    }

    @Test
    fun mouse_and_keyboard_drain_independently() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val delivered = mutableListOf<HidFrame>()
        val throttler = HidSendThrottler(scope, sink = { delivered += it })
        throttler.start()

        // Interleave: one keyboard, one mouse.
        throttler.offer(HidFrame.Keyboard(modifier = 0, keys = listOf(0x04)))
        throttler.offer(HidFrame.Mouse(buttons = 0, dX = 1, dY = 0))

        // Advance by the minimum gap; both should have been delivered because they
        // sit in separate channels / coroutines and don't serialize against each
        // other.
        advanceTimeBy(1L)
        advanceUntilIdle()

        assertEquals(2, delivered.size)
        assertTrue(delivered.any { it is HidFrame.Keyboard })
        assertTrue(delivered.any { it is HidFrame.Mouse })

        throttler.stop()
    }
}
