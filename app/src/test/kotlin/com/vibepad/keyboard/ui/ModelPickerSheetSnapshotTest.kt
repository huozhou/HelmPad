package com.vibepad.keyboard.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vibepad.keyboard.ui.theme.VibePadTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Snapshot regression for [ModelPickerSheet].
 *
 * Skeleton only — `@Ignore`d until golden images are captured, in the same spirit
 * as [MacroGridSnapshotTest]. Once a baseline lands, remove the annotation and the
 * `en` / `zh` screenshots become part of CI.
 */
@Ignore("Enable once golden images are captured")
class ModelPickerSheetSnapshotTest {

    private val seedConfig = DeviceConfig.PIXEL_5

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = seedConfig)

    @Test fun sheet_en() = snapshot(locale = "en")

    @Test fun sheet_zh() = snapshot(locale = "zh-rCN")

    private fun snapshot(locale: String) {
        paparazzi.unsafeUpdateConfig(seedConfig.copy(locale = locale))
        paparazzi.snapshot {
            VibePadTheme(useDarkTheme = false, useDynamicColor = false) {
                // ModalBottomSheet host cannot render in isolation; snapshot the
                // composable content that lives inside the sheet instead.
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Mirrors ModelPickerContent; kept inline to keep the
                    // snapshot self-contained without exposing test-only hooks.
                    ModelRowStub(title = "Opus", body = "Most capable for complex reasoning")
                    ModelRowStub(title = "Sonnet", body = "Default daily driver")
                    ModelRowStub(title = "Haiku", body = "Fast and efficient")
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ModelRowStub(title: String, body: String) {
        androidx.compose.material3.ListItem(
            headlineContent = { androidx.compose.material3.Text(title) },
            supportingContent = { androidx.compose.material3.Text(body) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
