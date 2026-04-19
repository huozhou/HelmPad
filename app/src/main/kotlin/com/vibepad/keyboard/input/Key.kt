package com.vibepad.keyboard.input

import kotlinx.serialization.Serializable

/**
 * HID Usage Table entries for the Keyboard/Keypad page (0x07) that the app emits.
 *
 * Values are the raw usage codes that land in bytes 2–7 of the keyboard report.
 * Only the codes actually reachable from v1 macros or literal text are enumerated.
 */
@Serializable
enum class Key(val usage: Int) {
    // Letters
    A(0x04), B(0x05), C(0x06), D(0x07), E(0x08), F(0x09), G(0x0A), H(0x0B), I(0x0C),
    J(0x0D), K(0x0E), L(0x0F), M(0x10), N(0x11), O(0x12), P(0x13), Q(0x14), R(0x15),
    S(0x16), T(0x17), U(0x18), V(0x19), W(0x1A), X(0x1B), Y(0x1C), Z(0x1D),

    // Row of digits
    DIGIT_1(0x1E), DIGIT_2(0x1F), DIGIT_3(0x20), DIGIT_4(0x21), DIGIT_5(0x22),
    DIGIT_6(0x23), DIGIT_7(0x24), DIGIT_8(0x25), DIGIT_9(0x26), DIGIT_0(0x27),

    // Control keys
    ENTER(0x28),
    ESCAPE(0x29),
    BACKSPACE(0x2A),
    TAB(0x2B),
    SPACE(0x2C),

    // Punctuation (unshifted side)
    MINUS(0x2D),        // - _
    EQUAL(0x2E),        // = +
    LEFT_BRACKET(0x2F), // [ {
    RIGHT_BRACKET(0x30),// ] }
    BACKSLASH(0x31),    // \ |
    SEMICOLON(0x33),    // ; :
    APOSTROPHE(0x34),   // ' "
    GRAVE(0x35),        // ` ~
    COMMA(0x36),        // , <
    PERIOD(0x37),       // . >
    SLASH(0x38),        // / ?

    // Arrows
    RIGHT_ARROW(0x4F),
    LEFT_ARROW(0x50),
    DOWN_ARROW(0x51),
    UP_ARROW(0x52),
}
