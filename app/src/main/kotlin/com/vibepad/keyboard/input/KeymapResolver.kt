package com.vibepad.keyboard.input

/**
 * Pure function: given an [InputAction] plus the current [HostTarget], produces an
 * ordered list of [HidFrame]s ready for the transport.
 *
 * For keyboard actions we emit both press and release frames. For sequences we flatten
 * recursively. Inter-step delays are preserved as [ResolvedStep.DelayMs] markers so
 * the transport layer can honor them without round-tripping to this module.
 */
object KeymapResolver {

    /**
     * Resolver output. A mix of frames-to-send and delays-to-wait. Keeping delays in
     * the output stream (rather than mutating the resolver into a `suspend`-returning
     * function) lets callers schedule delays however they want.
     */
    sealed interface ResolvedStep {
        data class Frame(val frame: HidFrame) : ResolvedStep
        data class DelayMs(val millis: Long) : ResolvedStep
    }

    fun resolve(action: InputAction, host: HostTarget): List<ResolvedStep> {
        val out = ArrayList<ResolvedStep>()
        expand(action, host, out)
        return out
    }

    /**
     * Same as [resolve] but filters out any [ResolvedStep.DelayMs] and returns the
     * HID frames only. Handy for unit tests that verify byte sequences without caring
     * about timing.
     */
    fun resolveFrames(action: InputAction, host: HostTarget): List<HidFrame> =
        resolve(action, host).filterIsInstance<ResolvedStep.Frame>().map { it.frame }

    private fun expand(action: InputAction, host: HostTarget, out: MutableList<ResolvedStep>) {
        when (action) {
            is InputAction.Chord -> expandChord(action, host, out)
            is InputAction.Literal -> expandLiteral(action, out)
            is InputAction.Sequence -> expandSequence(action, host, out)
        }
    }

    private fun expandChord(
        chord: InputAction.Chord,
        host: HostTarget,
        out: MutableList<ResolvedStep>,
    ) {
        val modifier = chord.modifiers.fold(0) { acc, mod -> acc or modifierBit(mod, host) }
        out += ResolvedStep.Frame(HidFrame.Keyboard(modifier = modifier, keys = listOf(chord.key.usage)))
        out += ResolvedStep.Frame(HidFrame.Keyboard.RELEASE)
    }

    private fun expandLiteral(literal: InputAction.Literal, out: MutableList<ResolvedStep>) {
        val strokes = LiteralTokenizer.tokenize(literal.text)
        strokes.forEach { stroke ->
            out += ResolvedStep.Frame(HidFrame.Keyboard(modifier = stroke.modifier, keys = listOf(stroke.usage)))
            out += ResolvedStep.Frame(HidFrame.Keyboard.RELEASE)
        }
    }

    private fun expandSequence(
        sequence: InputAction.Sequence,
        host: HostTarget,
        out: MutableList<ResolvedStep>,
    ) {
        sequence.steps.forEachIndexed { idx, step ->
            expand(step, host, out)
            val isLast = idx == sequence.steps.lastIndex
            if (!isLast && sequence.interStepDelayMs > 0L) {
                out += ResolvedStep.DelayMs(sequence.interStepDelayMs)
            }
        }
    }

    /**
     * Concrete HID modifier bit for an abstract [Mod] on a given [host].
     *
     * | Abstract | macOS          | Windows        |
     * |----------|----------------|----------------|
     * | PRIMARY  | Left GUI (Cmd) | Left Control   |
     * | SHIFT    | Left Shift     | Left Shift     |
     * | ALT      | Left Option    | Left Alt       |
     * | HYPER    | Left Control   | Left GUI (Win) |
     */
    internal fun modifierBit(mod: Mod, host: HostTarget): Int = when (mod) {
        Mod.SHIFT -> ModifierBits.LEFT_SHIFT
        Mod.ALT -> ModifierBits.LEFT_ALT
        Mod.PRIMARY -> when (host) {
            HostTarget.MACOS -> ModifierBits.LEFT_GUI
            HostTarget.WINDOWS -> ModifierBits.LEFT_CTRL
        }
        Mod.HYPER -> when (host) {
            HostTarget.MACOS -> ModifierBits.LEFT_CTRL
            HostTarget.WINDOWS -> ModifierBits.LEFT_GUI
        }
    }
}
