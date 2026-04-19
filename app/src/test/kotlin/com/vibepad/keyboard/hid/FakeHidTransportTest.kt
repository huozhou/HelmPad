package com.vibepad.keyboard.hid

import app.cash.turbine.test
import com.vibepad.keyboard.input.HidFrame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FakeHidTransportTest {

    @Test
    fun starts_in_idle_and_transitions_to_proxying_on_start() = runTest {
        val fake = FakeHidTransport()
        assertEquals(HidLinkState.Idle, fake.state.value)
        fake.start()
        assertEquals(HidLinkState.Proxying, fake.state.value)
    }

    @Test
    fun stop_brings_state_back_to_idle() {
        val fake = FakeHidTransport()
        fake.forceState(HidLinkState.Connected(HostDevice("MacBook", "AA:BB:CC:DD:EE:FF")))
        fake.stop()
        assertEquals(HidLinkState.Idle, fake.state.value)
    }

    @Test
    fun sent_frames_are_recorded_for_later_assertion() = runTest {
        val fake = FakeHidTransport()
        fake.sentFrames.test {
            fake.sendKeyboard(HidFrame.Keyboard(modifier = 0, keys = listOf(0x04)))
            val first = awaitItem()
            assertTrue(first is HidFrame.Keyboard)
            assertEquals(listOf(0x04), (first as HidFrame.Keyboard).keys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun retry_when_disconnected_reinvokes_start() {
        val fake = FakeHidTransport()
        fake.forceState(HidLinkState.Failed(FailureCause.REGISTER_APP_REJECTED))
        fake.retry()
        assertEquals(HidLinkState.Proxying, fake.state.value)
    }

    @Test
    fun retry_when_connected_is_noop() {
        val fake = FakeHidTransport()
        val host = HostDevice("Pixelbook", "11:22:33:44:55:66")
        fake.forceState(HidLinkState.Connected(host))
        val before = fake.startCount
        fake.retry()
        assertEquals(before, fake.startCount)
        assertEquals(HidLinkState.Connected(host), fake.state.value)
    }
}
