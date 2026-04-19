package com.vibepad.keyboard.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vibepad.keyboard.macro.ProfileLoader
import com.vibepad.keyboard.ui.theme.VibePadTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Snapshot regression for the bundled Claude Code macro grid.
 *
 * Loads the JSON asset straight off disk (same trick as `BundledProfileTest`) to
 * avoid Paparazzi's pre-Robolectric asset handling quirks.
 *
 * @Ignored until golden images are recorded — see tasks.md 7.8.
 */
@Ignore("Enable once golden images are captured — see tasks.md 7.8")
class MacroGridSnapshotTest {

    private val seedConfig = DeviceConfig.PIXEL_5

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = seedConfig)

    private val profile by lazy {
        val json = File("src/main/assets/profiles/claude-code.json").readText()
        val result = ProfileLoader().loadFromString(json) as ProfileLoader.Result.Ok
        result.profile
    }

    @Test fun grid_en() = snapshot(locale = "en")

    @Test fun grid_zh() = snapshot(locale = "zh-rCN")

    private fun snapshot(locale: String) {
        paparazzi.unsafeUpdateConfig(seedConfig.copy(locale = locale))
        paparazzi.snapshot {
            VibePadTheme(useDarkTheme = false, useDynamicColor = false) {
                MacroGrid(
                    profile = profile,
                    onFire = {},
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                )
            }
        }
    }
}
