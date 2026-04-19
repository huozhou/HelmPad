package com.vibepad.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepad.keyboard.R
import com.vibepad.keyboard.input.HostTarget

/**
 * Top-of-screen branding + single-glance host indicator + settings entry.
 *
 * Layout (left → right):
 *  1. `BrandChrome` — Rotor logo + "Helm Pad" wordmark (wordmark collapses on
 *     narrow screens < 360dp).
 *  2. Flexible spacer.
 *  3. `HostBadge` — 16dp display-only vector icon (🍎 / 🪟) of the current host
 *     target. Tapping opens [HostStatusSheet] for device details and "change in
 *     Settings".
 *  4. Settings icon button.
 *
 * Connection status is intentionally NOT rendered here. Connected = visual
 * silence (healthy = no chrome); any non-connected state is surfaced by
 * [SetupRecoveryBanner] immediately below this AppBar. Profile is no longer
 * shown here either — it lives as a section header above the macro grid.
 */
@Composable
fun VibeAppBar(
    hostTarget: HostTarget,
    onOpenHostStatusSheet: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandChrome()
        Spacer(Modifier.weight(1f))
        HostBadge(hostTarget = hostTarget, onClick = onOpenHostStatusSheet)
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.appbar_settings_a11y))
        }
    }
}

/**
 * Helm Pad brand mark. On screens narrower than 360dp the wordmark is dropped
 * so the rest of the AppBar (HostBadge + Settings) keeps breathing room.
 */
@Composable
fun BrandChrome(modifier: Modifier = Modifier) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandLogo(size = 24.dp)
        if (widthDp >= 360) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Helm Pad",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                ),
            )
        }
    }
}

/**
 * Miniature Rotor mark rendered as a rounded-corner tile with the launcher
 * gradient behind the foreground vector. Reusing the launcher drawable keeps
 * brand logo and launcher icon visually identical.
 */
@Composable
fun BrandLogo(size: Dp = 24.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.22f))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF5B5FEF), Color(0xFF4347D8)),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * 16dp vector badge showing which host OS the current connection (or the
 * session default when idle) is targeted at.
 *
 * Not a picker: this used to be a dropdown that switched `HostTarget`, but
 * `host-os-autodetect` (Change 4) makes that switch happen automatically
 * per-paired-device and `appbar-consolidation` (this change) relocates the
 * manual override to Paired Hosts settings. So the badge is now purely a
 * "what are you targeting right now" indicator, and tapping it only pops the
 * detail [HostStatusSheet] — no HID events or target changes are triggered
 * from here.
 */
@Composable
private fun HostBadge(
    hostTarget: HostTarget,
    onClick: () -> Unit,
) {
    val iconRes = when (hostTarget) {
        HostTarget.MACOS -> R.drawable.ic_host_macos
        HostTarget.WINDOWS -> R.drawable.ic_host_windows
    }
    val a11yLabel = when (hostTarget) {
        HostTarget.MACOS -> stringResource(R.string.host_badge_a11y_macos)
        HostTarget.WINDOWS -> stringResource(R.string.host_badge_a11y_windows)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = a11yLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}
