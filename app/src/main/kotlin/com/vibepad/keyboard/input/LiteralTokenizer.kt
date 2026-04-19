package com.vibepad.keyboard.input

/**
 * Tokenizes literal ASCII strings into (modifier, usage) key-press pairs under the
 * US-QWERTY keyboard layout.
 *
 * Scope — what works and what doesn't:
 *  - ✅ ASCII printable (0x20 – 0x7E), plus CR (`\n`), TAB (`\t`).
 *  - ❌ Any other codepoint (CJK, accented characters, control codes): we explicitly
 *       throw [TokenizationException] rather than silently dropping. Per
 *       `specs/input-actions/spec.md`, this rejection is surfaced at profile-load
 *       time so a broken macro never reaches runtime.
 *
 * The tokenizer is pure: same input → same output, no state, no I/O.
 */
object LiteralTokenizer {

    /** A single keystroke produced from one input codepoint. */
    data class Keystroke(val modifier: Int, val usage: Int)

    /** One input character could not be represented under US-QWERTY. */
    class TokenizationException(
        val index: Int,
        val codepoint: Int,
        message: String,
    ) : IllegalArgumentException(message)

    /**
     * Expands [text] into an ordered list of [Keystroke]s. Throws if any character is
     * outside the supported set.
     */
    fun tokenize(text: String): List<Keystroke> {
        val out = ArrayList<Keystroke>(text.length)
        text.forEachIndexed { idx, ch ->
            out += lookup(idx, ch)
        }
        return out
    }

    private fun lookup(index: Int, ch: Char): Keystroke {
        // Control characters we support.
        when (ch) {
            '\n', '\r' -> return Keystroke(modifier = 0, usage = Key.ENTER.usage)
            '\t' -> return Keystroke(modifier = 0, usage = Key.TAB.usage)
        }
        // Unshifted lowercase letters.
        if (ch in 'a'..'z') {
            return Keystroke(modifier = 0, usage = Key.A.usage + (ch - 'a'))
        }
        // Shifted uppercase letters.
        if (ch in 'A'..'Z') {
            return Keystroke(modifier = ModifierBits.LEFT_SHIFT, usage = Key.A.usage + (ch - 'A'))
        }
        // Digits.
        if (ch in '1'..'9') {
            return Keystroke(modifier = 0, usage = Key.DIGIT_1.usage + (ch - '1'))
        }
        if (ch == '0') {
            return Keystroke(modifier = 0, usage = Key.DIGIT_0.usage)
        }
        // Punctuation table.
        val mapped = PUNCTUATION[ch]
        if (mapped != null) return mapped
        throw TokenizationException(
            index = index,
            codepoint = ch.code,
            message = "Unsupported character at index $index: '$ch' (U+${
                ch.code.toString(16).uppercase().padStart(4, '0')
            }). Only US-QWERTY ASCII is supported.",
        )
    }

    /**
     * Every ASCII punctuation character expressible via US-QWERTY, mapped to its
     * (modifier, usage) pair. Order follows the HID Usage Table numerically so it's
     * easy to cross-check.
     */
    private val PUNCTUATION: Map<Char, Keystroke> = mapOf(
        ' ' to Keystroke(0, Key.SPACE.usage),
        '-' to Keystroke(0, Key.MINUS.usage),
        '_' to Keystroke(ModifierBits.LEFT_SHIFT, Key.MINUS.usage),
        '=' to Keystroke(0, Key.EQUAL.usage),
        '+' to Keystroke(ModifierBits.LEFT_SHIFT, Key.EQUAL.usage),
        '[' to Keystroke(0, Key.LEFT_BRACKET.usage),
        '{' to Keystroke(ModifierBits.LEFT_SHIFT, Key.LEFT_BRACKET.usage),
        ']' to Keystroke(0, Key.RIGHT_BRACKET.usage),
        '}' to Keystroke(ModifierBits.LEFT_SHIFT, Key.RIGHT_BRACKET.usage),
        '\\' to Keystroke(0, Key.BACKSLASH.usage),
        '|' to Keystroke(ModifierBits.LEFT_SHIFT, Key.BACKSLASH.usage),
        ';' to Keystroke(0, Key.SEMICOLON.usage),
        ':' to Keystroke(ModifierBits.LEFT_SHIFT, Key.SEMICOLON.usage),
        '\'' to Keystroke(0, Key.APOSTROPHE.usage),
        '"' to Keystroke(ModifierBits.LEFT_SHIFT, Key.APOSTROPHE.usage),
        '`' to Keystroke(0, Key.GRAVE.usage),
        '~' to Keystroke(ModifierBits.LEFT_SHIFT, Key.GRAVE.usage),
        ',' to Keystroke(0, Key.COMMA.usage),
        '<' to Keystroke(ModifierBits.LEFT_SHIFT, Key.COMMA.usage),
        '.' to Keystroke(0, Key.PERIOD.usage),
        '>' to Keystroke(ModifierBits.LEFT_SHIFT, Key.PERIOD.usage),
        '/' to Keystroke(0, Key.SLASH.usage),
        '?' to Keystroke(ModifierBits.LEFT_SHIFT, Key.SLASH.usage),
        // Shifted-digit symbols.
        '!' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_1.usage),
        '@' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_2.usage),
        '#' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_3.usage),
        '$' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_4.usage),
        '%' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_5.usage),
        '^' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_6.usage),
        '&' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_7.usage),
        '*' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_8.usage),
        '(' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_9.usage),
        ')' to Keystroke(ModifierBits.LEFT_SHIFT, Key.DIGIT_0.usage),
    )
}
