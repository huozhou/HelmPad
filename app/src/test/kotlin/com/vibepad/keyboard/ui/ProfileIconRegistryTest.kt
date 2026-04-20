package com.vibepad.keyboard.ui

import com.vibepad.keyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards [ProfileIconRegistry] so every bundled profile id resolves to its own
 * drawable and an unknown id falls back to a valid (non-zero) Claude Code
 * drawable — matching `add-codex-cursor-profiles` design decision 6. Kept as a
 * plain JVM test (no Android runtime) because the registry just returns `Int`
 * resource ids; R generation happens at compile time so `R.drawable.*` is
 * available without inflating anything.
 */
class ProfileIconRegistryTest {

    @Test
    fun claude_code_id_resolves_to_claude_drawable() {
        assertEquals(
            R.drawable.ic_profile_claude_code,
            ProfileIconRegistry.resolve("profile.claude-code"),
        )
    }

    @Test
    fun codex_id_resolves_to_codex_drawable() {
        assertEquals(
            R.drawable.ic_profile_codex,
            ProfileIconRegistry.resolve("profile.codex"),
        )
    }

    @Test
    fun cursor_id_resolves_to_cursor_drawable() {
        assertEquals(
            R.drawable.ic_profile_cursor,
            ProfileIconRegistry.resolve("profile.cursor"),
        )
    }

    /**
     * Bundled drawable ids must be unique — if two profiles silently share a
     * drawable because of a copy-paste error in the registry, the UI would
     * render the same glyph for both without the compiler noticing.
     */
    @Test
    fun every_known_profile_gets_a_distinct_drawable() {
        val claude = ProfileIconRegistry.resolve("profile.claude-code")
        val codex = ProfileIconRegistry.resolve("profile.codex")
        val cursor = ProfileIconRegistry.resolve("profile.cursor")
        assertNotEquals(claude, codex)
        assertNotEquals(claude, cursor)
        assertNotEquals(codex, cursor)
    }

    @Test
    fun unknown_profile_id_falls_back_to_claude_drawable() {
        val fallback = ProfileIconRegistry.resolve("profile.does-not-exist")
        assertTrue("fallback drawable must be a real, non-zero R id", fallback != 0)
        assertEquals(
            "fallback should be the Claude Code drawable per design decision 6",
            R.drawable.ic_profile_claude_code,
            fallback,
        )
    }

    @Test
    fun blank_and_empty_profile_ids_fall_back_safely() {
        assertEquals(R.drawable.ic_profile_claude_code, ProfileIconRegistry.resolve(""))
        assertEquals(R.drawable.ic_profile_claude_code, ProfileIconRegistry.resolve("   "))
    }
}
