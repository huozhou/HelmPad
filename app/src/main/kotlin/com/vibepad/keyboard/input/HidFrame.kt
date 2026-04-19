package com.vibepad.keyboard.input

/**
 * Immutable HID report payload ready to be shipped through a [HidTransport][
 * com.vibepad.keyboard.hid.HidTransport].
 *
 * Two shapes are supported — see subtypes. Both expose [toByteArray] which returns
 * the exact bytes expected by macOS and Windows HID boot-protocol parsers.
 *
 * This type is intentionally Android-free so it can be exhaustively unit-tested.
 */
sealed interface HidFrame {

    /** Report ID used when the transport speaks the combined (keyboard + mouse) descriptor. */
    val reportId: Int

    fun toByteArray(): ByteArray

    /**
     * 8-byte boot-protocol keyboard report.
     *
     * | byte | meaning                              |
     * |------|--------------------------------------|
     * |  0   | modifier bitmap                      |
     * |  1   | reserved (always 0x00)               |
     * | 2..7 | up to 6 simultaneously-pressed keys  |
     *
     * A zero-filled frame (modifier byte 0, all key slots 0) represents "all keys
     * released" and MUST be emitted after every press frame — otherwise the host
     * treats the key as auto-repeating.
     */
    data class Keyboard(
        val modifier: Int,
        val keys: List<Int>,
    ) : HidFrame {

        init {
            require(modifier in 0..0xFF) { "modifier out of range: $modifier" }
            require(keys.size <= 6) { "boot-protocol keyboard report holds at most 6 keys" }
            keys.forEach { require(it in 0..0xFF) { "key usage out of range: $it" } }
        }

        override val reportId: Int get() = REPORT_ID

        override fun toByteArray(): ByteArray {
            val out = ByteArray(LENGTH)
            out[0] = modifier.toByte()
            out[1] = 0x00
            for (i in 0 until 6) {
                out[2 + i] = keys.getOrElse(i) { 0 }.toByte()
            }
            return out
        }

        companion object {
            const val REPORT_ID = 1
            const val LENGTH = 8

            /** "All keys released" frame — the canonical release. */
            val RELEASE = Keyboard(modifier = 0, keys = emptyList())
        }
    }

    /**
     * 5-byte mouse report (buttons + 16-bit signed dX + 16-bit signed dY), optionally
     * extended with a 1-byte signed wheel delta.
     *
     * Per-frame movement is clamped to int16 range, but the spec (and practice)
     * prefers splitting into multiple smaller frames for smoothness. [MAX_AXIS_DELTA]
     * captures the conventional per-frame cap used when splitting.
     */
    data class Mouse(
        val buttons: Int,
        val dX: Int,
        val dY: Int,
        val wheel: Int = 0,
    ) : HidFrame {

        init {
            require(buttons in 0..0x07) { "buttons bitmap must fit in 3 bits: $buttons" }
            require(dX in Short.MIN_VALUE..Short.MAX_VALUE) { "dX out of int16 range: $dX" }
            require(dY in Short.MIN_VALUE..Short.MAX_VALUE) { "dY out of int16 range: $dY" }
            require(wheel in Byte.MIN_VALUE..Byte.MAX_VALUE) { "wheel out of int8 range: $wheel" }
        }

        override val reportId: Int get() = REPORT_ID

        override fun toByteArray(): ByteArray {
            val out = ByteArray(LENGTH)
            out[0] = buttons.toByte()
            out[1] = (dX and 0xFF).toByte()
            out[2] = ((dX shr 8) and 0xFF).toByte()
            out[3] = (dY and 0xFF).toByte()
            out[4] = ((dY shr 8) and 0xFF).toByte()
            out[5] = wheel.toByte()
            return out
        }

        companion object {
            const val REPORT_ID = 2
            const val LENGTH = 6
            const val MAX_AXIS_DELTA = 127
            const val MAX_WHEEL_DELTA = 15

            const val BUTTON_LEFT = 0x01
            const val BUTTON_RIGHT = 0x02
            const val BUTTON_MIDDLE = 0x04

            /** "All buttons released, no movement" frame. */
            val RELEASE = Mouse(buttons = 0, dX = 0, dY = 0, wheel = 0)
        }
    }
}
