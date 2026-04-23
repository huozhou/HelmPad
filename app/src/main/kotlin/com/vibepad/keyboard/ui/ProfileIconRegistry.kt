package com.vibepad.keyboard.ui

import androidx.annotation.DrawableRes
import com.vibepad.keyboard.R

/**
 * Maps a [Profile.id] to the drawable used for its profile glyph — shown
 * alongside the dropdown and onboarding rows in Settings.
 *
 * Kept as a closed map so unknown profile ids fall back to the Claude Code
 * glyph (always bundled, always valid) rather than crashing the UI.
 */
internal object ProfileIconRegistry {

    @DrawableRes
    private val FALLBACK: Int = R.drawable.ic_profile_claude_code

    private val TABLE: Map<String, Int> = mapOf(
        "profile.claude-code" to R.drawable.ic_profile_claude_code,
        "profile.codex" to R.drawable.ic_profile_codex,
        "profile.cursor" to R.drawable.ic_profile_cursor,
    )

    @DrawableRes
    fun resolve(profileId: String): Int = TABLE[profileId] ?: FALLBACK
}
