package com.vibepad.keyboard.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.ui.theme.VibePadTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Snapshot regression for the top app bar across host targets and locales.
 *
 * **Baseline invalidated by `appbar-consolidation`.** The AppBar was rewritten
 * from a 6-element row (StatusDot + stateLabel + HostTargetSelector + ProfileChip
 * + Settings) down to 3 elements (BrandChrome + HostBadge + Settings). Previous
 * goldens are discarded; this test remains [`@Ignore`]d until a fresh baseline
 * is captured after Change 3 ships.
 *
 * Run:
 * - `./gradlew :app:recordPaparazziDebug --tests VibeAppBarSnapshotTest` → refresh goldens.
 * - `./gradlew :app:verifyPaparazziDebug --tests VibeAppBarSnapshotTest` → verify.
 */
@Ignore("Baseline invalidated by appbar-consolidation — re-record from zero before enabling.")
class VibeAppBarSnapshotTest {

    private val seedConfig = DeviceConfig.PIXEL_5

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = seedConfig)

    @Test fun macos_en() = snapshot(locale = "en") {
        VibeAppBar(
            hostTarget = HostTarget.MACOS,
            onOpenHostStatusSheet = {},
            onOpenSettings = {},
        )
    }

    @Test fun macos_zh() = snapshot(locale = "zh-rCN") {
        VibeAppBar(
            hostTarget = HostTarget.MACOS,
            onOpenHostStatusSheet = {},
            onOpenSettings = {},
        )
    }

    @Test fun windows_en() = snapshot(locale = "en") {
        VibeAppBar(
            hostTarget = HostTarget.WINDOWS,
            onOpenHostStatusSheet = {},
            onOpenSettings = {},
        )
    }

    @Test fun windows_zh() = snapshot(locale = "zh-rCN") {
        VibeAppBar(
            hostTarget = HostTarget.WINDOWS,
            onOpenHostStatusSheet = {},
            onOpenSettings = {},
        )
    }

    private fun snapshot(locale: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(seedConfig.copy(locale = locale))
        paparazzi.snapshot { VibePadTheme(useDarkTheme = false, useDynamicColor = false) { content() } }
    }
}
