package com.vibepad.keyboard.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the static backoff curve used by the reconnect loop. The rest of
 * [AndroidHidTransport] is Android-coupled and is covered by instrumented tests
 * (see `src/androidTest/`).
 */
class AndroidHidTransportBackoffTest {

    @Test
    fun first_attempt_is_one_second() {
        assertEquals(1_000L, AndroidHidTransport.computeBackoffMs(1))
    }

    @Test
    fun curve_doubles_then_caps_at_thirty_seconds() {
        // 1s, 2s, 4s, 8s, 16s, then capped at 30s.
        val expected = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L, 30_000L)
        val actual = (1..expected.size).map { AndroidHidTransport.computeBackoffMs(it) }
        assertEquals(expected, actual)
    }

    @Test
    fun curve_never_exceeds_cap_even_for_huge_attempt() {
        assertTrue(AndroidHidTransport.computeBackoffMs(attempt = 100) <= 30_000L)
    }
}
