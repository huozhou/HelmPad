package com.vibepad.keyboard.input

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Platform-independent description of what pressing a macro button should do.
 *
 * The concrete bytes-on-the-wire are produced by [KeymapResolver] together with a
 * [HostTarget]. Keep this type pure (no Android deps) so it can be exhaustively
 * unit-tested.
 */
@Serializable
sealed interface InputAction {
    /**
     * A single chord press: zero or more [Mod]ifiers held while [key] is pressed.
     * Emits exactly one keyboard "press" frame followed by one "release" frame.
     */
    @Serializable
    @SerialName("Chord")
    data class Chord(
        val modifiers: Set<Mod> = emptySet(),
        val key: Key,
    ) : InputAction

    /**
     * Literal text to be typed character-by-character under the US-QWERTY layout.
     * Tokenized by [LiteralTokenizer]. Non-ASCII characters cause a
     * [LiteralTokenizer.TokenizationException] — never silently dropped.
     */
    @Serializable
    @SerialName("Literal")
    data class Literal(val text: String) : InputAction

    /**
     * Ordered list of sub-actions with an optional inter-step delay. Useful for
     * "type `/model`, wait a beat, press Enter" style macros when Claude Code needs
     * the command to register before the newline arrives.
     */
    @Serializable
    @SerialName("Sequence")
    data class Sequence(
        val steps: List<InputAction>,
        val interStepDelayMs: Long = 0L,
    ) : InputAction
}
