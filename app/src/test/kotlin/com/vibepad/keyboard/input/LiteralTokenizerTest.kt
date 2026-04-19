package com.vibepad.keyboard.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LiteralTokenizerTest {

    @Test
    fun empty_string_produces_empty_list() {
        assertEquals(emptyList<LiteralTokenizer.Keystroke>(), LiteralTokenizer.tokenize(""))
    }

    @Test
    fun single_lowercase_letter() {
        val out = LiteralTokenizer.tokenize("a")
        assertEquals(listOf(LiteralTokenizer.Keystroke(modifier = 0, usage = 0x04)), out)
    }

    @Test
    fun single_uppercase_letter_requires_shift() {
        val out = LiteralTokenizer.tokenize("A")
        assertEquals(
            listOf(LiteralTokenizer.Keystroke(modifier = ModifierBits.LEFT_SHIFT, usage = 0x04)),
            out,
        )
    }

    @Test
    fun newline_and_tab_are_control_keys() {
        val out = LiteralTokenizer.tokenize("\n\t")
        assertEquals(
            listOf(
                LiteralTokenizer.Keystroke(0, Key.ENTER.usage),
                LiteralTokenizer.Keystroke(0, Key.TAB.usage),
            ),
            out,
        )
    }

    @Test
    fun digits_round_trip() {
        val out = LiteralTokenizer.tokenize("0123456789")
        val expected = listOf(0x27, 0x1E, 0x1F, 0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26)
            .map { LiteralTokenizer.Keystroke(modifier = 0, usage = it) }
        assertEquals(expected, out)
    }

    @Test
    fun shifted_punctuation() {
        val out = LiteralTokenizer.tokenize("!@#$%^&*()")
        val expected = listOf(
            Key.DIGIT_1, Key.DIGIT_2, Key.DIGIT_3, Key.DIGIT_4, Key.DIGIT_5,
            Key.DIGIT_6, Key.DIGIT_7, Key.DIGIT_8, Key.DIGIT_9, Key.DIGIT_0,
        ).map { LiteralTokenizer.Keystroke(modifier = ModifierBits.LEFT_SHIFT, usage = it.usage) }
        assertEquals(expected, out)
    }

    @Test
    fun slash_command_for_claude_code() {
        val out = LiteralTokenizer.tokenize("/model")
        val expected = listOf(
            LiteralTokenizer.Keystroke(modifier = 0, usage = Key.SLASH.usage),
            LiteralTokenizer.Keystroke(modifier = 0, usage = Key.M.usage),
            LiteralTokenizer.Keystroke(modifier = 0, usage = Key.O.usage),
            LiteralTokenizer.Keystroke(modifier = 0, usage = Key.D.usage),
            LiteralTokenizer.Keystroke(modifier = 0, usage = Key.E.usage),
            LiteralTokenizer.Keystroke(modifier = 0, usage = Key.L.usage),
        )
        assertEquals(expected, out)
    }

    @Test
    fun full_ascii_printable_range_survives_without_throwing() {
        val sb = StringBuilder()
        for (code in 0x20..0x7E) sb.append(code.toChar())
        // Should not throw. Length matches: 95 printable characters.
        val out = LiteralTokenizer.tokenize(sb.toString())
        assertEquals(0x7E - 0x20 + 1, out.size)
    }

    @Test
    fun non_ascii_throws_with_index() {
        val ex = assertThrows(LiteralTokenizer.TokenizationException::class.java) {
            LiteralTokenizer.tokenize("hello 你好")
        }
        // "hello " is 6 chars; first unsupported char is at index 6.
        assertEquals(6, ex.index)
    }

    @Test
    fun accented_character_throws() {
        assertThrows(LiteralTokenizer.TokenizationException::class.java) {
            LiteralTokenizer.tokenize("café")
        }
    }

    @Test
    fun null_byte_throws() {
        assertThrows(LiteralTokenizer.TokenizationException::class.java) {
            LiteralTokenizer.tokenize("a\u0000b")
        }
    }
}
