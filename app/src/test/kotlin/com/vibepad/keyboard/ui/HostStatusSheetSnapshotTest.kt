package com.vibepad.keyboard.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.HostDevice
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.ui.theme.VibePadTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * Snapshot stub for [HostStatusSheet]. Captures the connected branch (device
 * details + "Change in Settings" link + Forget button) and the empty branch
 * (not-connected prompt with an "Open Bluetooth Settings" action).
 */
@Ignore("Baseline pending — see appbar-consolidation tasks.md 9.3")
class HostStatusSheetSnapshotTest {

    private val seedConfig = DeviceConfig.PIXEL_5

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = seedConfig)

    private val host = HostDevice(name = "MBP", address = "AA:BB:CC:DD:EE:FF")

    @Test fun connected_state_en() = snapshot(locale = "en") {
        HostStatusSheet(
            state = HidLinkState.Connected(host),
            hostTarget = HostTarget.MACOS,
            alias = null,
            lastConnectedAgo = "just now",
            onOpenSettings = {},
            onForget = {},
            onOpenBluetoothSettings = {},
            onDismiss = {},
        )
    }

    @Test fun connected_state_zh() = snapshot(locale = "zh-rCN") {
        HostStatusSheet(
            state = HidLinkState.Connected(host),
            hostTarget = HostTarget.MACOS,
            alias = "我的 MBP",
            lastConnectedAgo = "刚刚",
            onOpenSettings = {},
            onForget = {},
            onOpenBluetoothSettings = {},
            onDismiss = {},
        )
    }

    @Test fun disconnected_state_en() = snapshot(locale = "en") {
        HostStatusSheet(
            state = HidLinkState.Idle,
            hostTarget = HostTarget.MACOS,
            alias = null,
            lastConnectedAgo = null,
            onOpenSettings = {},
            onForget = {},
            onOpenBluetoothSettings = {},
            onDismiss = {},
        )
    }

    private fun snapshot(locale: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        paparazzi.unsafeUpdateConfig(seedConfig.copy(locale = locale))
        paparazzi.snapshot { VibePadTheme(useDarkTheme = false, useDynamicColor = false) { content() } }
    }
}
