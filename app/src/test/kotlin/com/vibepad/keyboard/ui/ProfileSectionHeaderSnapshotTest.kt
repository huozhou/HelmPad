package com.vibepad.keyboard.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vibepad.keyboard.ui.theme.VibePadTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Snapshot stub for the section header that now sits above the macro grid.
 *
 * Records two locales so the baseline catches both the icon size/tint and the
 * "Claude Code" label rendering with the system font. `@Ignore`d until the
 * goldens are recorded alongside the rest of Change 3's Paparazzi baseline.
 */
@Ignore("Baseline pending — see appbar-consolidation tasks.md 9.4")
class ProfileSectionHeaderSnapshotTest {

    private val seedConfig = DeviceConfig.PIXEL_5

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = seedConfig)

    @Test fun en() = snapshot(locale = "en") {
        ProfileSectionHeader(profileId = "profile.claude-code", profileName = "Claude Code")
    }

    @Test fun zh() = snapshot(locale = "zh-rCN") {
        ProfileSectionHeader(profileId = "profile.claude-code", profileName = "Claude Code")
    }

    private fun snapshot(locale: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(seedConfig.copy(locale = locale))
        paparazzi.snapshot { VibePadTheme(useDarkTheme = false, useDynamicColor = false) { content() } }
    }
}
