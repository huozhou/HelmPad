package com.vibepad.keyboard.input

import app.cash.turbine.test
import com.vibepad.keyboard.hid.FakeHidTransport
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.HostDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InputActionEngineTest {

    @Test
    fun chord_emits_press_and_release() = runTest {
        val transport = connectedFake()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val engine = InputActionEngine(transport, scope, hostTarget = { HostTarget.MACOS })
        transport.sentFrames.test {
            engine.fire(InputAction.Chord(modifiers = setOf(Mod.SHIFT), key = Key.TAB))
            scope.advanceUntilIdle()
            val press = awaitItem() as HidFrame.Keyboard
            val release = awaitItem() as HidFrame.Keyboard
            assertEquals(ModifierBits.LEFT_SHIFT, press.modifier)
            assertEquals(listOf(Key.TAB.usage), press.keys)
            assertEquals(0, release.modifier)
            assertTrue(release.keys.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun literal_emits_one_press_release_per_character() = runTest {
        val transport = connectedFake()
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val engine = InputActionEngine(transport, scope, hostTarget = { HostTarget.WINDOWS })
        val collected = mutableListOf<HidFrame>()
        val collectorJob = scope.launch { transport.sentFrames.collect { collected += it } }
        scope.advanceUntilIdle() // let the collector subscribe before frames fly
        engine.emit(InputAction.Literal("hi"))
        scope.advanceUntilIdle()
        collectorJob.cancel()
        // 'h': press + release; 'i': press + release.
        assertEquals(4, collected.size)
    }

    private fun connectedFake(): FakeHidTransport = FakeHidTransport().apply {
        forceState(HidLinkState.Connected(HostDevice("test", "AA:BB:CC:DD:EE:FF")))
    }
}
