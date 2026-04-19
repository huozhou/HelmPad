package com.vibepad.keyboard.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vibepad.keyboard.hid.FailureCause
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.UnavailableReason
import com.vibepad.keyboard.ui.theme.VibePadTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Snapshot-level coverage of [SetupRecoveryBanner] behavior added in
 * `appbar-consolidation`: transient states render with an X close button;
 * actionable states render the action button without an X.
 *
 * `@Ignore`d like the rest of the Paparazzi suite until baseline goldens are
 * recorded. Exists primarily to *compile-check* the new parameter list and
 * prevent call-site drift.
 */
@Ignore("Baseline pending — see appbar-consolidation tasks.md 9.1")
class SetupRecoveryBannerDismissTest {

    private val seedConfig = DeviceConfig.PIXEL_5

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = seedConfig)

    @Test fun advertising_banner_shows_x_button() = snapshot {
        SetupRecoveryBanner(
            state = HidLinkState.Advertising,
            dismissedStates = emptySet(),
            onDismiss = {},
            onOpenBluetoothSettings = {},
            onRerunSetup = {},
            onOpenDiagnostics = {},
        )
    }

    @Test fun advertising_banner_dismissed_hidden() = snapshot {
        SetupRecoveryBanner(
            state = HidLinkState.Advertising,
            dismissedStates = setOf("ADVERTISING"),
            onDismiss = {},
            onOpenBluetoothSettings = {},
            onRerunSetup = {},
            onOpenDiagnostics = {},
        )
    }

    @Test fun failed_banner_has_no_x_button() = snapshot {
        SetupRecoveryBanner(
            state = HidLinkState.Failed(FailureCause.REGISTER_APP_REJECTED),
            dismissedStates = emptySet(),
            onDismiss = {},
            onOpenBluetoothSettings = {},
            onRerunSetup = {},
            onOpenDiagnostics = {},
        )
    }

    @Test fun bluetooth_off_banner_actionable() = snapshot {
        SetupRecoveryBanner(
            state = HidLinkState.Unavailable(UnavailableReason.BLUETOOTH_OFF),
            dismissedStates = emptySet(),
            onDismiss = {},
            onOpenBluetoothSettings = {},
            onRerunSetup = {},
            onOpenDiagnostics = {},
        )
    }

    private fun snapshot(content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.snapshot { VibePadTheme(useDarkTheme = false, useDynamicColor = false) { content() } }
    }
}
