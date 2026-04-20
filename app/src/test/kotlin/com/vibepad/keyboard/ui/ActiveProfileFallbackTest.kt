package com.vibepad.keyboard.ui

import com.vibepad.keyboard.input.InputAction
import com.vibepad.keyboard.macro.MacroDefinition
import com.vibepad.keyboard.macro.Profile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for the `stored id → default id → first loaded` fallback chain
 * that [OperatorContent] uses to choose the active profile. The Composable
 * itself only delegates to [resolveActiveProfile], so pinning that function's
 * behaviour here covers `add-codex-cursor-profiles` tasks.md §4.4 without
 * spinning up a Compose test runtime.
 */
class ActiveProfileFallbackTest {

    @Test
    fun null_stored_id_falls_back_to_default_profile() {
        val resolved = resolveActiveProfile(ALL_PROFILES, storedProfileId = null)
        assertEquals(DEFAULT_PROFILE_ID, resolved.id)
    }

    @Test
    fun stored_id_matching_loaded_profile_is_used_as_is() {
        val resolved = resolveActiveProfile(ALL_PROFILES, storedProfileId = "profile.codex")
        assertEquals("profile.codex", resolved.id)
    }

    @Test
    fun unknown_stored_id_falls_back_to_default_profile() {
        val resolved = resolveActiveProfile(ALL_PROFILES, storedProfileId = "profile.unknown")
        assertEquals(
            "Unknown stored id must resolve to the default profile",
            DEFAULT_PROFILE_ID,
            resolved.id,
        )
    }

    @Test
    fun default_missing_but_others_present_falls_back_to_first_profile() {
        // Defensive path — every shipped build includes Claude Code, but if a
        // future build ever dropped it we still want the UI to render the
        // first loaded profile instead of crashing or looping.
        val noClaude = listOf(PROFILE_CODEX, PROFILE_CURSOR)
        val resolved = resolveActiveProfile(noClaude, storedProfileId = DEFAULT_PROFILE_ID)
        assertEquals(
            "With Claude Code unavailable, the first loaded profile wins",
            "profile.codex",
            resolved.id,
        )
    }

    @Test
    fun empty_profiles_list_throws_to_surface_bug_fast() {
        try {
            resolveActiveProfile(emptyList(), storedProfileId = null)
            error("Expected IllegalArgumentException for empty profiles")
        } catch (expected: IllegalArgumentException) {
            // ok
        }
    }

    // --- fixtures ------------------------------------------------------------

    private val PROFILE_CLAUDE = profileFixture("profile.claude-code", "Claude Code")
    private val PROFILE_CODEX = profileFixture("profile.codex", "Codex")
    private val PROFILE_CURSOR = profileFixture("profile.cursor", "Cursor")
    private val ALL_PROFILES = listOf(PROFILE_CLAUDE, PROFILE_CODEX, PROFILE_CURSOR)

    private fun profileFixture(id: String, name: String): Profile = Profile(
        id = id,
        name = name,
        schemaVersion = "1.0.0",
        slots = (0 until Profile.SLOT_COUNT).map { idx ->
            MacroDefinition(
                id = "slot_$idx",
                label = "Slot $idx",
                iconRef = "check_circle",
                action = InputAction.Literal("\n"),
                destructive = false,
            )
        },
    )
}
