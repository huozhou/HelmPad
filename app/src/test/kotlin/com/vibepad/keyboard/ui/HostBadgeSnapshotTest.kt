package com.vibepad.keyboard.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.ui.theme.VibePadTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Snapshot stub for the AppBar's host badge after `appbar-consolidation`
 * reshaped it from a dropdown selector into a 16dp vector icon.
 *
 * Since `HostBadge` is `private` inside `AppBar.kt`, we exercise it indirectly
 * via the full `VibeAppBar` — the only visible difference across the two cases
 * is the drawable inside the badge. Once goldens are captured, any regression
 * to icon sizing, tint, or tap target padding will surface here.
 */
@Ignore("Baseline pending — see appbar-consolidation tasks.md 9.2")
class HostBadgeSnapshotTest {

    private val seedConfig = DeviceConfig.PIXEL_5

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = seedConfig)

    @Test fun macos_badge_idle() = snapshot {
        VibeAppBar(
            hostTarget = HostTarget.MACOS,
            onOpenHostStatusSheet = {},
            onOpenSettings = {},
        )
    }

    @Test fun windows_badge_idle() = snapshot {
        VibeAppBar(
            hostTarget = HostTarget.WINDOWS,
            onOpenHostStatusSheet = {},
            onOpenSettings = {},
        )
    }

    private fun snapshot(content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.snapshot { VibePadTheme(useDarkTheme = false, useDynamicColor = false) { content() } }
    }
}
