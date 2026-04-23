package com.vibepad.keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.HidTransport
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.macro.Profile

/**
 * Operator screen — what the user sees 99% of the time.
 *
 * Layout pivots on orientation:
 *  - Portrait: touchpad on top, profile section header + macro grid on bottom.
 *  - Landscape: touchpad left (60%), profile section header + macro grid right (40%).
 *
 * `appbar-consolidation` stripped the AppBar down to `BrandChrome + HostBadge +
 * Settings`. Non-connected states surface in [SetupRecoveryBanner] immediately
 * under the AppBar; the banner is dismissable for transient states via
 * [dismissedBannerStates]. `HostStatusSheet` is opened by tapping the host
 * badge and shows device details + a "change in Settings" link.
 */
@Composable
fun MainScreen(
    transport: HidTransport,
    profile: Profile,
    hostTarget: HostTarget,
    @Suppress("UNUSED_PARAMETER") onSelectHostTarget: (HostTarget) -> Unit,
    touchpadController: TouchpadController,
    onFireMacro: (slotId: String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenPairedHosts: () -> Unit = onOpenSettings,
    onOpenBluetoothSettings: () -> Unit = {},
    onRerunSetup: () -> Unit = {},
) {
    val linkState by transport.state.collectAsState()
    var dismissedBannerStates by remember { mutableStateOf(emptySet<String>()) }
    var showHostStatusSheet by remember { mutableStateOf(false) }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            VibeAppBar(
                hostTarget = hostTarget,
                onOpenHostStatusSheet = { showHostStatusSheet = true },
                onOpenSettings = onOpenSettings,
            )
            SetupRecoveryBanner(
                state = linkState,
                dismissedStates = dismissedBannerStates,
                onDismiss = { tag ->
                    dismissedBannerStates = dismissedBannerStates + tag
                },
                onOpenBluetoothSettings = onOpenBluetoothSettings,
                onRerunSetup = onRerunSetup,
                onOpenDiagnostics = onOpenSettings,
            )
            HorizontalDivider()
            BodyLayout(
                profile = profile,
                linkState = linkState,
                touchpadController = touchpadController,
                onFireMacro = onFireMacro,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
    }

    if (showHostStatusSheet) {
        HostStatusSheet(
            state = linkState,
            hostTarget = hostTarget,
            alias = null,
            lastConnectedAgo = null,
            onOpenSettings = {
                showHostStatusSheet = false
                onOpenSettings()
            },
            onForget = {
                showHostStatusSheet = false
                onOpenPairedHosts()
            },
            onOpenBluetoothSettings = {
                showHostStatusSheet = false
                onOpenBluetoothSettings()
            },
            onDismiss = { showHostStatusSheet = false },
        )
    }
}

@Composable
private fun BodyLayout(
    profile: Profile,
    linkState: HidLinkState,
    touchpadController: TouchpadController,
    onFireMacro: (slotId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val interactive = linkState is HidLinkState.Connected

    if (isLandscape) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TouchpadSurface(
                controller = if (interactive) touchpadController else NoopTouchpadController,
                modifier = Modifier.weight(0.6f).fillMaxSize(),
            )
            Column(modifier = Modifier.weight(0.4f).fillMaxSize()) {
                if (!interactive) DisconnectedBanner()
                MacroGrid(
                    profile = profile,
                    onFire = { if (interactive) onFireMacro(it.id) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    } else {
        // Portrait body strategy (operator-layout-responsive):
        //   1. macros pin to their natural height — `LayoutBudget.macroGridHeight`
        //      mirrors `MacroGrid`'s Column+Row math so we can predict it.
        //   2. touchpad takes the rest, clamped to a sane min/max so it never
        //      goes claustrophobic on an unfolded device or laptop-tall on a
        //      tablet.
        //   3. if step 2 still doesn't fit in the available height (short
        //      device, or keyboard pushed up), the whole page becomes
        //      verticalScroll() — touchpad capped lower in that branch so users
        //      always get *some* of it on screen above the macros.
        BoxWithConstraints(modifier = modifier) {
            val natural = LayoutBudget.naturalTotalHeight(
                screenWidth = maxWidth,
                slotCount = profile.slots.size,
            )
            val fitsWithoutScroll = maxHeight >= natural
            if (fitsWithoutScroll) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TouchpadSurface(
                        controller = if (interactive) touchpadController else NoopTouchpadController,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = LayoutBudget.TouchpadMinHeight,
                                max = LayoutBudget.TouchpadMaxHeightFit,
                            )
                            .weight(1f, fill = true),
                    )
                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                        if (!interactive) DisconnectedBanner()
                        MacroGrid(
                            profile = profile,
                            onFire = { if (interactive) onFireMacro(it.id) },
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TouchpadSurface(
                        controller = if (interactive) touchpadController else NoopTouchpadController,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = LayoutBudget.TouchpadMinHeight,
                                max = LayoutBudget.TouchpadMaxHeightScroll,
                            ),
                    )
                    Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                        if (!interactive) DisconnectedBanner()
                        MacroGrid(
                            profile = profile,
                            onFire = { if (interactive) onFireMacro(it.id) },
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Layout constants and helpers for the portrait operator screen. Public-by-package
 * so unit tests can exercise the height math without spinning up Compose.
 *
 * The numbers here are deliberately conservative: when adding anything new to
 * the operator screen, also bump the matching constant or the page will silently
 * start scrolling on borderline devices instead of obviously overflowing in a
 * way reviewers would catch.
 */
internal object LayoutBudget {
    val TouchpadMinHeight: Dp = 160.dp
    val TouchpadMaxHeightFit: Dp = 360.dp
    val TouchpadMaxHeightScroll: Dp = 240.dp

    val MacroGridOuterPadding: Dp = 8.dp
    val MacroGridTileGap: Dp = 8.dp
    const val MacroGridColumns: Int = 4

    val SafetyBuffer: Dp = 8.dp
    val AppBarHeight: Dp = 56.dp
    val DividerHeight: Dp = 1.dp
    val MaxBannerHeight: Dp = 80.dp

    /**
     * Predicted height of the macro grid for a given screen width and slot
     * count. Mirrors the layout in [MacroGrid] (4-wide rows of square tiles
     * separated by [MacroGridTileGap], with [MacroGridOuterPadding] outside).
     */
    fun macroGridHeight(screenWidth: Dp, slotCount: Int): Dp {
        if (slotCount <= 0) return MacroGridOuterPadding * 2
        val columnGapTotal = MacroGridTileGap * (MacroGridColumns - 1)
        val tileSize = (screenWidth - MacroGridOuterPadding * 2 - columnGapTotal) / MacroGridColumns
        val rows = (slotCount + MacroGridColumns - 1) / MacroGridColumns
        val rowGapTotal = if (rows > 1) MacroGridTileGap * (rows - 1) else 0.dp
        return tileSize * rows + rowGapTotal + MacroGridOuterPadding * 2 + 4.dp
    }

    /**
     * Total height the operator screen would occupy at its most generous —
     * banner pinned at [maxBannerHeight], touchpad at its minimum, macros at
     * their natural height. Compared against the parent
     * `BoxWithConstraints.maxHeight` to decide whether to fall back to
     * verticalScroll().
     *
     * Note: AppBar / banner / divider are intentionally still summed in even
     * though they live *outside* the BoxWithConstraints (in [MainScreen]'s
     * outer Column) and are therefore already deducted from `maxHeight`.
     * This produces a deliberately conservative breakpoint — counting those
     * three a second time pushes the "fits" threshold up by ~140dp, which is
     * what makes a 360×640 phone scroll while a 360×720 phone doesn't. If
     * you "clean this up" by dropping the three terms, the breakpoint drops
     * to ~520dp and short phones will silently start clipping the macro grid
     * instead of scrolling. Don't.
     */
    fun naturalTotalHeight(
        screenWidth: Dp,
        slotCount: Int,
        maxBannerHeight: Dp = MaxBannerHeight,
    ): Dp = AppBarHeight +
        maxBannerHeight +
        DividerHeight +
        TouchpadMinHeight +
        macroGridHeight(screenWidth, slotCount) +
        SafetyBuffer
}

@Composable
private fun DisconnectedBanner() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.state_advertising),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.size(4.dp))
    }
}

/** Drops every interaction on the floor. Used when the link is not connected. */
private object NoopTouchpadController : TouchpadController {
    override fun onMove(dX: Int, dY: Int) {}
    override fun onScroll(wheelTicks: Int) {}
    override fun onLeftTap() {}
    override fun onRightTap() {}
    override fun onLeftButtonDown() {}
    override fun onLeftButtonUp() {}
}
