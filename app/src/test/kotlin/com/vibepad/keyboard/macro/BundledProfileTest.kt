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
 * End-to-end sanity check over every bundled profile JSON under
 * `assets/profiles/`.
 *
 * Running this as a plain JVM test means we don't need the Android runtime — we
 * just read the assets straight off the filesystem. That keeps the check cheap
 * and immediately catches typos at the first edit.
 *
 * Structured as two layers per `add-codex-cursor-profiles` design decision 7:
 *
 *  - **Strong invariants** — things every bundled profile MUST satisfy, so the
 *    UI can rely on them without per-profile special-casing (anchor slots,
 *    `destructive=true` only on `new_session`, etc.).
 *  - **Weak set assertions** — per-profile slot-id sets are listed explicitly
 *    so a JSON edit that drops or renames a slot either updates the set here
 *    or fails the test loudly.
 */
class BundledProfileTest {

    /** Anchor slot ids every bundled profile must include (decision 7). */
    private val anchorSlotIds = setOf("approve", "escape", "arrow_up", "arrow_down")

    /**
     * Exact slot-id sets per bundled profile. Adjusting a bundled JSON must
     * also update this map — that is intentional, it's the invariant
     * enforcement.
     */
    private val expectedSlotIds: Map<String, Set<String>> = mapOf(
        "profile.claude-code" to setOf(
            "approve", "escape", "cycle_mode", "arrow_up",
            "switch_model", "new_session", "compact", "arrow_down",
        ),
        "profile.codex" to setOf(
            "approve", "escape", "cycle_mode", "arrow_up",
            "switch_model", "new_session", "compact", "arrow_down",
        ),
        "profile.cursor" to setOf(
            "approve", "escape", "command_palette", "arrow_up",
            "composer", "new_session", "inline_edit", "arrow_down",
        ),
    )

    @Test
    fun every_bundled_profile_file_loads_and_passes_strong_invariants() {
        val dir = File("src/main/assets/profiles")
        val files = dir.listFiles { _, name -> name.endsWith(".json") }?.sortedBy { it.name }
            ?: error("No bundled profile directory at ${dir.absolutePath}")
        assertTrue(
            "Expected at least one bundled profile JSON, found 0 in ${dir.absolutePath}",
            files.isNotEmpty(),
        )

        val loader = ProfileLoader()
        val loadedIds = mutableSetOf<String>()
        for (file in files) {
            val res = loader.loadFromString(file.readText())
            assertTrue("expected Ok for ${file.name}, was $res", res is ProfileLoader.Result.Ok)
            val profile = (res as ProfileLoader.Result.Ok).profile

            assertEquals(
                "profile ${profile.id}: schemaVersion must be 1.0.0",
                "1.0.0",
                profile.schemaVersion,
            )
            assertEquals(
                "profile ${profile.id}: must have 8 slots (${file.name})",
                Profile.SLOT_COUNT,
                profile.slots.size,
            )
            assertTrue(
                "profile ${profile.id}: name must be non-blank",
                profile.name.isNotBlank(),
            )

            val slotIds = profile.slots.map { it.id }.toSet()
            assertTrue(
                "profile ${profile.id}: must include anchor slots $anchorSlotIds, got $slotIds",
                slotIds.containsAll(anchorSlotIds),
            )
            assertAnchorActions(profile)
            assertDestructiveOnlyOnNewSession(profile)

            profile.slots.forEach { slot ->
                assertTrue(
                    "profile ${profile.id}: slot ${slot.id} label must be non-blank",
                    slot.label.isNotBlank(),
                )
                assertTrue(
                    "profile ${profile.id}: slot ${slot.id} iconRef must be non-blank",
                    slot.iconRef.isNotBlank(),
                )
            }

            val expected = expectedSlotIds[profile.id]
            if (expected != null) {
                assertEquals(
                    "profile ${profile.id}: slot id set drifted (update expectedSlotIds " +
                        "in BundledProfileTest if this is intentional)",
                    expected,
                    slotIds,
                )
            }

            loadedIds += profile.id
        }

        assertTrue(
            "Expected the Claude Code profile to ship; loaded ids were $loadedIds",
            "profile.claude-code" in loadedIds,
        )
        assertEquals(
            "Every id in expectedSlotIds must correspond to a shipped profile",
            expectedSlotIds.keys,
            loadedIds.intersect(expectedSlotIds.keys),
        )
    }

    /**
     * Slot ids removed across the `macro-grid-polish` and
     * `claude-code-profile-trim` changes must stay removed — applied across
     * every bundled profile now, so copy-pasting slots between profiles can't
     * smuggle a banned id back in.
     */
    @Test
    fun removed_slot_ids_do_not_reappear_in_any_profile() {
        val bannedIds = setOf(
            "reject", "interrupt",
            "yes_dont_ask", "add_file", "tab", "enter",
            "history_up", "history_down", "toggle_plan",
        )
        loadAll().forEach { profile ->
            val overlap = profile.slots.map { it.id }.toSet() intersect bannedIds
            assertTrue(
                "profile ${profile.id}: banned slot ids reappeared: $overlap",
                overlap.isEmpty(),
            )
        }
    }

    /**
     * `switch_model` is a Claude/Codex-only slot — both fire `Literal("/model\n")`.
     * Claude's tap is intercepted by [ModelPickerSheet]; Codex drops into its own
     * TUI `/model` menu.
     *
     * Cursor v1 omits `switch_model` entirely (decision 3 + decision 8 + decision
     * 11 — no reliable default shortcut for model switching in Cursor desktop
     * chat; slot is redirected to `composer`).
     */
    @Test
    fun switch_model_action_matches_profile_target() {
        val literalProfiles = setOf("profile.claude-code", "profile.codex")

        loadAll().forEach { profile ->
            val slot = profile.slots.singleOrNull { it.id == "switch_model" } ?: return@forEach
            assertTrue(
                "profile ${profile.id}: unexpected switch_model slot — only Claude / Codex " +
                    "may carry it. Update literalProfiles in BundledProfileTest if a new " +
                    "bundled profile is shipping with switch_model.",
                profile.id in literalProfiles,
            )
            val action = slot.action
            assertTrue(
                "profile ${profile.id}: switch_model must be Literal, got ${action::class.simpleName}",
                action is InputAction.Literal,
            )
            assertEquals(
                "profile ${profile.id}: switch_model literal should be '/model\\n'",
                "/model\n",
                (action as InputAction.Literal).text,
            )
        }
    }

    /**
     * Cursor profile (decision 11) is built on stable default VS Code / Cursor
     * shortcuts — each of the four flex slots must use the exact `PRIMARY[+SHIFT]`
     * chord users already have in their muscle memory for VS Code.
     */
    @Test
    fun cursor_flex_slots_match_vscode_defaults() {
        val cursor = loadAll().singleOrNull { it.id == "profile.cursor" } ?: return
        val bySlot = cursor.slots.associateBy { it.id }

        assertChord(cursor.id, bySlot.getValue("command_palette"), setOf(Mod.PRIMARY, Mod.SHIFT), Key.P)
        assertChord(cursor.id, bySlot.getValue("composer"), setOf(Mod.PRIMARY), Key.I)
        assertChord(cursor.id, bySlot.getValue("new_session"), setOf(Mod.PRIMARY), Key.L)
        assertChord(cursor.id, bySlot.getValue("inline_edit"), setOf(Mod.PRIMARY), Key.K)
    }

    /**
     * Codex (decision 4 post-1.3a): `cycle_mode` is Shift+Tab for cycling
     * approval modes, and the R2C3 slot is the original `/compact` (the
     * originally-proposed `/review` does not exist in the current `codex` CLI).
     */
    @Test
    fun codex_cycle_mode_and_compact_match_cli_commands() {
        val codex = loadAll().singleOrNull { it.id == "profile.codex" } ?: return
        val bySlot = codex.slots.associateBy { it.id }

        assertChord(codex.id, bySlot.getValue("cycle_mode"), setOf(Mod.SHIFT), Key.TAB)

        val compactAction = bySlot.getValue("compact").action
        assertTrue(
            "profile ${codex.id}: compact must be Literal, got ${compactAction::class.simpleName}",
            compactAction is InputAction.Literal,
        )
        assertEquals(
            "profile ${codex.id}: compact literal should be '/compact\\n'",
            "/compact\n",
            (compactAction as InputAction.Literal).text,
        )

        val newSessionAction = bySlot.getValue("new_session").action
        assertTrue(
            "profile ${codex.id}: new_session must be Literal, got ${newSessionAction::class.simpleName}",
            newSessionAction is InputAction.Literal,
        )
        assertEquals(
            "profile ${codex.id}: new_session literal should be '/new\\n' (Codex uses /new, not /clear)",
            "/new\n",
            (newSessionAction as InputAction.Literal).text,
        )
    }

    /**
     * The `cycle_*` slot labels must stay generic across profiles. Early
     * drafts spelled out individual mode names ("Plan mode"), which drifts out
     * of sync with upstream whenever the CLI renames modes.
     */
    @Test
    fun cycle_slot_labels_stay_generic_across_profiles() {
        val bannedSubstrings = listOf("Plan", "Thinking", "Auto")
        loadAll().forEach { profile ->
            profile.slots
                .filter { it.id.startsWith("cycle_") }
                .forEach { slot ->
                    bannedSubstrings.forEach { needle ->
                        assertFalse(
                            "profile ${profile.id}: slot ${slot.id} label must stay generic; " +
                                "found '$needle' in '${slot.label}'",
                            slot.label.contains(needle),
                        )
                    }
                }
        }
    }

    // --- shared helpers ------------------------------------------------------

    private fun assertAnchorActions(profile: Profile) {
        val byId = profile.slots.associateBy { it.id }
        val approve = byId.getValue("approve").action
        assertTrue(
            "profile ${profile.id}: approve must be Literal(\"\\n\"), got $approve",
            approve is InputAction.Literal && approve.text == "\n",
        )

        val escape = byId.getValue("escape").action
        assertTrue(
            "profile ${profile.id}: escape must be Chord([], ESCAPE), got $escape",
            escape is InputAction.Chord && escape.modifiers.isEmpty() && escape.key == Key.ESCAPE,
        )

        val arrowUp = byId.getValue("arrow_up").action
        assertTrue(
            "profile ${profile.id}: arrow_up must be Chord([], UP_ARROW), got $arrowUp",
            arrowUp is InputAction.Chord && arrowUp.modifiers.isEmpty() && arrowUp.key == Key.UP_ARROW,
        )

        val arrowDown = byId.getValue("arrow_down").action
        assertTrue(
            "profile ${profile.id}: arrow_down must be Chord([], DOWN_ARROW), got $arrowDown",
            arrowDown is InputAction.Chord && arrowDown.modifiers.isEmpty() && arrowDown.key == Key.DOWN_ARROW,
        )
    }

    private fun assertChord(
        profileId: String,
        slot: MacroDefinition,
        expectedModifiers: Set<Mod>,
        expectedKey: Key,
    ) {
        val action = slot.action
        assertTrue(
            "profile $profileId: slot ${slot.id} must be Chord, got ${action::class.simpleName}",
            action is InputAction.Chord,
        )
        val chord = action as InputAction.Chord
        assertEquals(
            "profile $profileId: slot ${slot.id} modifiers must be $expectedModifiers",
            expectedModifiers,
            chord.modifiers,
        )
        assertEquals(
            "profile $profileId: slot ${slot.id} key must be $expectedKey",
            expectedKey,
            chord.key,
        )
    }

    private fun assertDestructiveOnlyOnNewSession(profile: Profile) {
        profile.slots.forEach { slot ->
            if (slot.id == "new_session") {
                assertTrue(
                    "profile ${profile.id}: new_session must be destructive",
                    slot.destructive,
                )
            } else {
                assertFalse(
                    "profile ${profile.id}: only new_session should be destructive, but ${slot.id} is",
                    slot.destructive,
                )
            }
        }
    }

    private fun loadAll(): List<Profile> {
        val dir = File("src/main/assets/profiles")
        val files = dir.listFiles { _, name -> name.endsWith(".json") }?.sortedBy { it.name }.orEmpty()
        val loader = ProfileLoader()
        return files.map { file ->
            val res = loader.loadFromString(file.readText())
            (res as ProfileLoader.Result.Ok).profile
        }
    }

}
