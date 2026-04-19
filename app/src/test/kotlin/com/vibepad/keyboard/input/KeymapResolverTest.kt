package com.vibepad.keyboard.input

import org.junit.Assert.assertEquals
import org.junit.Test

class KeymapResolverTest {

    // ---- Modifier mapping -------------------------------------------------------

    @Test
    fun primary_on_macos_is_left_gui() {
        assertEquals(ModifierBits.LEFT_GUI, KeymapResolver.modifierBit(Mod.PRIMARY, HostTarget.MACOS))
    }

    @Test
    fun primary_on_windows_is_left_ctrl() {
        assertEquals(ModifierBits.LEFT_CTRL, KeymapResolver.modifierBit(Mod.PRIMARY, HostTarget.WINDOWS))
    }

    @Test
    fun hyper_on_macos_is_left_ctrl() {
        assertEquals(ModifierBits.LEFT_CTRL, KeymapResolver.modifierBit(Mod.HYPER, HostTarget.MACOS))
    }

    @Test
    fun hyper_on_windows_is_left_gui() {
        assertEquals(ModifierBits.LEFT_GUI, KeymapResolver.modifierBit(Mod.HYPER, HostTarget.WINDOWS))
    }

    @Test
    fun shift_and_alt_are_host_independent() {
        for (host in HostTarget.values()) {
            assertEquals(ModifierBits.LEFT_SHIFT, KeymapResolver.modifierBit(Mod.SHIFT, host))
            assertEquals(ModifierBits.LEFT_ALT, KeymapResolver.modifierBit(Mod.ALT, host))
        }
    }

    // ---- Chord resolution -------------------------------------------------------

    @Test
    fun single_key_chord_produces_press_and_release() {
        val frames = KeymapResolver.resolveFrames(
            InputAction.Chord(modifiers = emptySet(), key = Key.A),
            HostTarget.MACOS,
        )
        assertEquals(
            listOf(
                HidFrame.Keyboard(modifier = 0, keys = listOf(Key.A.usage)),
                HidFrame.Keyboard.RELEASE,
            ),
            frames,
        )
    }

    @Test
    fun shift_tab_is_host_independent() {
        val chord = InputAction.Chord(modifiers = setOf(Mod.SHIFT), key = Key.TAB)
        val mac = KeymapResolver.resolveFrames(chord, HostTarget.MACOS)
        val win = KeymapResolver.resolveFrames(chord, HostTarget.WINDOWS)
        assertEquals(mac, win)
        assertEquals(
            listOf(
                HidFrame.Keyboard(modifier = ModifierBits.LEFT_SHIFT, keys = listOf(Key.TAB.usage)),
                HidFrame.Keyboard.RELEASE,
            ),
            mac,
        )
    }

    @Test
    fun primary_a_differs_only_in_modifier_between_hosts() {
        val chord = InputAction.Chord(modifiers = setOf(Mod.PRIMARY), key = Key.A)
        val mac = KeymapResolver.resolveFrames(chord, HostTarget.MACOS) as List<HidFrame.Keyboard>
        val win = KeymapResolver.resolveFrames(chord, HostTarget.WINDOWS) as List<HidFrame.Keyboard>
        assertEquals(ModifierBits.LEFT_GUI, mac[0].modifier)
        assertEquals(ModifierBits.LEFT_CTRL, win[0].modifier)
        // Everything else matches: keys and the release frame.
        assertEquals(mac[0].keys, win[0].keys)
        assertEquals(mac[1], win[1])
    }

    @Test
    fun combined_modifiers_bitwise_OR_into_one_byte() {
        val chord = InputAction.Chord(modifiers = setOf(Mod.PRIMARY, Mod.SHIFT), key = Key.Z)
        val frames = KeymapResolver.resolveFrames(chord, HostTarget.MACOS) as List<HidFrame.Keyboard>
        val expected = ModifierBits.LEFT_GUI or ModifierBits.LEFT_SHIFT
        assertEquals(expected, frames[0].modifier)
        assertEquals(listOf(Key.Z.usage), frames[0].keys)
    }

    // ---- Literal resolution -----------------------------------------------------

    @Test
    fun literal_model_newline_snapshot() {
        val frames = KeymapResolver.resolveFrames(
            InputAction.Literal("/model\n"),
            HostTarget.MACOS,
        )
        // 7 strokes × 2 frames (press + release) = 14 frames.
        assertEquals(14, frames.size)
        // Byte-level snapshot of the first four frames.
        assertEquals(byteArrayOf(0, 0, 0x38.toByte(), 0, 0, 0, 0, 0).toList(),
            (frames[0] as HidFrame.Keyboard).toByteArray().toList())
        assertEquals(byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0).toList(),
            (frames[1] as HidFrame.Keyboard).toByteArray().toList())
        assertEquals(byteArrayOf(0, 0, Key.M.usage.toByte(), 0, 0, 0, 0, 0).toList(),
            (frames[2] as HidFrame.Keyboard).toByteArray().toList())
        // Final frame: Enter press then release.
        val enterPress = frames[frames.size - 2] as HidFrame.Keyboard
        val enterRelease = frames[frames.size - 1] as HidFrame.Keyboard
        assertEquals(Key.ENTER.usage, enterPress.keys.single())
        assertEquals(HidFrame.Keyboard.RELEASE, enterRelease)
    }

    @Test
    fun literal_slash_clear_newline_for_new_session() {
        val frames = KeymapResolver.resolveFrames(
            InputAction.Literal("/clear\n"),
            HostTarget.MACOS,
        )
        // Usage codes in the order they appear in /clear\n
        val expectedUsages = listOf(
            Key.SLASH.usage,
            Key.C.usage,
            Key.L.usage,
            Key.E.usage,
            Key.A.usage,
            Key.R.usage,
            Key.ENTER.usage,
        )
        val pressUsages = frames
            .filterIsInstance<HidFrame.Keyboard>()
            .filter { it.keys.isNotEmpty() }
            .map { it.keys.single() }
        assertEquals(expectedUsages, pressUsages)
    }

    // ---- Sequence resolution ----------------------------------------------------

    @Test
    fun sequence_flattens_subactions_in_order() {
        val seq = InputAction.Sequence(
            steps = listOf(
                InputAction.Chord(key = Key.A),
                InputAction.Chord(key = Key.B),
            ),
            interStepDelayMs = 0,
        )
        val steps = KeymapResolver.resolve(seq, HostTarget.MACOS)
        val frames = steps.filterIsInstance<KeymapResolver.ResolvedStep.Frame>().map { it.frame }
        assertEquals(4, frames.size) // press A, release, press B, release
        assertEquals(Key.A.usage, (frames[0] as HidFrame.Keyboard).keys.single())
        assertEquals(Key.B.usage, (frames[2] as HidFrame.Keyboard).keys.single())
    }

    @Test
    fun sequence_preserves_inter_step_delays_between_but_not_after() {
        val seq = InputAction.Sequence(
            steps = listOf(
                InputAction.Chord(key = Key.A),
                InputAction.Chord(key = Key.B),
                InputAction.Chord(key = Key.C),
            ),
            interStepDelayMs = 50,
        )
        val steps = KeymapResolver.resolve(seq, HostTarget.MACOS)
        val delays = steps.filterIsInstance<KeymapResolver.ResolvedStep.DelayMs>()
        // Two gaps between three steps — not three.
        assertEquals(2, delays.size)
        delays.forEach { assertEquals(50L, it.millis) }
    }

    @Test
    fun sequence_with_zero_delay_omits_delay_markers() {
        val seq = InputAction.Sequence(
            steps = listOf(
                InputAction.Chord(key = Key.A),
                InputAction.Chord(key = Key.B),
            ),
            interStepDelayMs = 0,
        )
        val delays = KeymapResolver.resolve(seq, HostTarget.MACOS)
            .filterIsInstance<KeymapResolver.ResolvedStep.DelayMs>()
        assertEquals(emptyList<KeymapResolver.ResolvedStep.DelayMs>(), delays)
    }

}
