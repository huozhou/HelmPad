package com.vibepad.keyboard.macro

import com.vibepad.keyboard.input.InputAction
import com.vibepad.keyboard.input.Key
import com.vibepad.keyboard.input.Mod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * End-to-end sanity check over the bundled `assets/profiles/claude-code.json` file.
 *
 * Running this as a plain JVM test means we don't need the Android runtime — we just
 * read the asset straight off the filesystem. That keeps the check cheap and
 * immediately catches typos at the first edit.
 */
class BundledProfileTest {

    @Test
    fun bundled_claude_code_profile_is_valid() {
        val file = File("src/main/assets/profiles/claude-code.json")
        assertTrue("Bundled profile should exist at ${file.absolutePath}", file.exists())

        val loader = ProfileLoader()
        val res = loader.loadFromString(file.readText())
        assertTrue("expected Ok, was $res", res is ProfileLoader.Result.Ok)
        val profile = (res as ProfileLoader.Result.Ok).profile

        assertEquals("profile.claude-code", profile.id)
        assertEquals(8, profile.slots.size)

        val requiredIds = setOf(
            "approve", "escape", "cycle_mode", "arrow_up",
            "switch_model", "new_session", "compact", "arrow_down",
        )
        assertEquals(requiredIds, profile.slots.map { it.id }.toSet())

        profile.slots.forEach { slot ->
            assertTrue(slot.label.isNotBlank())
            assertTrue(slot.iconRef.isNotBlank())
        }
    }

    /**
     * Slot ids removed across the `macro-grid-polish` and `claude-code-profile-trim`
     * changes must stay removed. `reject` carried a wrong semantic ("2\n" mapped
     * to "Yes, don't ask again" in newer Claude Code versions) and is gone;
     * `yes_dont_ask` was dropped after the upstream permission prompt proved too
     * buggy to automate reliably (see `claude-code-profile-trim/design.md`
     * decision 4); the remaining ids either had no day-to-day value or were
     * covered cheaper by the generic arrow keys.
     */
    @Test
    fun removed_slot_ids_do_not_reappear() {
        val profile = loadBundled()
        val bannedIds = setOf(
            "reject", "interrupt",
            "yes_dont_ask", "add_file", "tab", "enter",
            "history_up", "history_down", "toggle_plan",
        )
        val overlap = profile.slots.map { it.id }.toSet() intersect bannedIds
        assertTrue(
            "Removed slot ids reappeared: $overlap (see claude-code-profile-trim design.md)",
            overlap.isEmpty(),
        )
    }

    /**
     * Only `new_session` is destructive — it wipes the current Claude Code context.
     * Other irreversible-adjacent actions (Esc, Cycle mode) are recoverable at the
     * conversation level, so they use the regular container color and haptic.
     */
    @Test
    fun destructive_flag_is_only_on_new_session() {
        val profile = loadBundled()
        profile.slots.forEach { slot ->
            if (slot.id == "new_session") {
                assertTrue("new_session must be destructive", slot.destructive)
            } else {
                assertFalse(
                    "Only new_session should be destructive, but ${slot.id} is",
                    slot.destructive,
                )
            }
        }
    }

    /**
     * `arrow_up` must resolve to the bare UP_ARROW keyboard usage with no modifiers.
     * This keeps history navigation byte-identical on macOS and Windows and
     * survives as a manual workaround for the deleted `yes_dont_ask` slot.
     */
    @Test
    fun arrow_up_is_chord_of_arrow_up_key_with_no_modifiers() {
        val slot = loadBundled().slots.single { it.id == "arrow_up" }
        val action = slot.action
        assertTrue("arrow_up must use Chord, got ${action::class.simpleName}", action is InputAction.Chord)
        val chord = action as InputAction.Chord
        assertEquals(Key.UP_ARROW, chord.key)
        assertEquals(emptySet<Mod>(), chord.modifiers)
    }

    /** Symmetric guard for `arrow_down` — see [arrow_up_is_chord_of_arrow_up_key_with_no_modifiers]. */
    @Test
    fun arrow_down_is_chord_of_arrow_down_key_with_no_modifiers() {
        val slot = loadBundled().slots.single { it.id == "arrow_down" }
        val action = slot.action
        assertTrue("arrow_down must use Chord, got ${action::class.simpleName}", action is InputAction.Chord)
        val chord = action as InputAction.Chord
        assertEquals(Key.DOWN_ARROW, chord.key)
        assertEquals(emptySet<Mod>(), chord.modifiers)
    }

    /**
     * The stored action on `switch_model` stays as a literal `/model\n` — that's the
     * fallback payload the JSON serializer accepts, and the UI intercepts the tap
     * before it reaches [KeymapResolver]. If the in-app [ModelPickerSheet] is ever
     * torn out, firing this slot directly still opens the TUI list menu.
     */
    @Test
    fun switch_model_action_is_literal_slash_model_newline() {
        val slot = loadBundled().slots.single { it.id == "switch_model" }
        val action = slot.action
        assertTrue("switch_model must use Literal, got ${action::class.simpleName}", action is InputAction.Literal)
        assertEquals("/model\n", (action as InputAction.Literal).text)
    }

    /**
     * The `cycle_mode` label must stay generic. Early drafts spelled out individual
     * mode names ("Plan mode"), which drifts out of sync with upstream whenever
     * Anthropic reorders or renames modes. Keeping the label generic lets
     * Shift-Tab's next-mode semantics carry the meaning at use time.
     */
    @Test
    fun cycle_mode_label_does_not_contain_mode_names() {
        val slot = loadBundled().slots.single { it.id == "cycle_mode" }
        val bannedSubstrings = listOf("Plan", "Thinking", "Auto")
        bannedSubstrings.forEach { needle ->
            assertFalse(
                "cycle_mode label must stay generic; found '$needle' in '${slot.label}'",
                slot.label.contains(needle),
            )
        }
    }

    private fun loadBundled(): Profile {
        val file = File("src/main/assets/profiles/claude-code.json")
        val res = ProfileLoader().loadFromString(file.readText())
        return (res as ProfileLoader.Result.Ok).profile
    }
}
