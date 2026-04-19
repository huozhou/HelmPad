package com.vibepad.keyboard.input

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HidFrameTest {

    // ---- Keyboard ---------------------------------------------------------------

    @Test
    fun release_frame_is_all_zero_bytes() {
        assertArrayEquals(
            ByteArray(HidFrame.Keyboard.LENGTH),
            HidFrame.Keyboard.RELEASE.toByteArray(),
        )
    }

    @Test
    fun single_key_press_byte_layout() {
        val frame = HidFrame.Keyboard(modifier = 0, keys = listOf(Key.A.usage))
        assertArrayEquals(
            byteArrayOf(0, 0, 0x04, 0, 0, 0, 0, 0),
            frame.toByteArray(),
        )
    }

    @Test
    fun modifier_byte_is_packed_in_byte_zero() {
        val frame = HidFrame.Keyboard(
            modifier = ModifierBits.LEFT_GUI or ModifierBits.LEFT_SHIFT,
            keys = listOf(Key.A.usage),
        )
        val bytes = frame.toByteArray()
        assertEquals((0x08 or 0x02).toByte(), bytes[0])
        assertEquals(0x00.toByte(), bytes[1])
        assertEquals(Key.A.usage.toByte(), bytes[2])
    }

    @Test
    fun up_to_six_simultaneous_keys_are_packed() {
        val keys = listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09)
        val frame = HidFrame.Keyboard(modifier = 0, keys = keys)
        val bytes = frame.toByteArray()
        for (i in 0..5) assertEquals(keys[i].toByte(), bytes[2 + i])
    }

    @Test
    fun more_than_six_keys_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            HidFrame.Keyboard(modifier = 0, keys = List(7) { it + 0x04 })
        }
    }

    @Test
    fun modifier_out_of_range_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            HidFrame.Keyboard(modifier = 0x100, keys = emptyList())
        }
    }

    // ---- Mouse ------------------------------------------------------------------

    @Test
    fun mouse_release_is_all_zero() {
        assertArrayEquals(ByteArray(HidFrame.Mouse.LENGTH), HidFrame.Mouse.RELEASE.toByteArray())
    }

    @Test
    fun mouse_dX_encodes_int16_little_endian() {
        // 0x1234 = 4660
        val frame = HidFrame.Mouse(buttons = 0, dX = 0x1234, dY = 0)
        val bytes = frame.toByteArray()
        assertEquals(0x34.toByte(), bytes[1])
        assertEquals(0x12.toByte(), bytes[2])
    }

    @Test
    fun mouse_negative_dY_encodes_twos_complement() {
        // -1 as int16 = 0xFFFF
        val frame = HidFrame.Mouse(buttons = 0, dX = 0, dY = -1)
        val bytes = frame.toByteArray()
        assertEquals(0xFF.toByte(), bytes[3])
        assertEquals(0xFF.toByte(), bytes[4])
    }

    @Test
    fun mouse_right_button_bit_is_0x02() {
        val frame = HidFrame.Mouse(buttons = HidFrame.Mouse.BUTTON_RIGHT, dX = 0, dY = 0)
        assertEquals(0x02.toByte(), frame.toByteArray()[0])
    }

    @Test
    fun mouse_wheel_encodes_signed_byte() {
        val up = HidFrame.Mouse(buttons = 0, dX = 0, dY = 0, wheel = 3).toByteArray()
        assertEquals(3.toByte(), up[5])
        val down = HidFrame.Mouse(buttons = 0, dX = 0, dY = 0, wheel = -3).toByteArray()
        assertEquals((-3).toByte(), down[5])
    }

    @Test
    fun mouse_buttons_must_fit_in_three_bits() {
        assertThrows(IllegalArgumentException::class.java) {
            HidFrame.Mouse(buttons = 0x08, dX = 0, dY = 0)
        }
    }
}
