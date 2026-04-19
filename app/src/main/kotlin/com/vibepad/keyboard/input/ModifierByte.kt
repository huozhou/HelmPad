package com.vibepad.keyboard.input

/**
 * HID boot-protocol keyboard modifier-byte bit layout (byte 0 of the keyboard report).
 *
 * See the USB HID Usage Tables v1.12, §10 "Keyboard/Keypad" for full details. We only
 * use left-hand modifiers in v1; the host does not distinguish semantically between
 * left and right for our purposes.
 */
internal object ModifierBits {
    const val LEFT_CTRL = 0x01
    const val LEFT_SHIFT = 0x02
    const val LEFT_ALT = 0x04
    const val LEFT_GUI = 0x08
    const val RIGHT_CTRL = 0x10
    const val RIGHT_SHIFT = 0x20
    const val RIGHT_ALT = 0x40
    const val RIGHT_GUI = 0x80
}
