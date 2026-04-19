package com.vibepad.keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R
import com.vibepad.keyboard.hid.FailureCause
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.UnavailableReason

/**
 * The single "something is not ideal" surface shown above the touchpad.
 *
 * Covers every non-[HidLinkState.Connected] state after `appbar-consolidation`
 * relocated the connection status out of the AppBar:
 *
 *  - Connected → null (silent = healthy; banner not shown)
 *  - Advertising / Reconnecting → **transient** banner with an X dismiss button
 *    (user can silence it while the link is spinning back up)
 *  - Proxying → transient banner without dismiss (state flips quickly; hiding
 *    it just hides an indicator that's about to resolve)
 *  - Unavailable / Failed → **actionable** banner with an action button that
 *    deep-links into the system entry point that can resolve the cause;
 *    deliberately not dismissable, so the user can't hide a blocker.
 *
 * `dismissedStates` tracks which state *kinds* (see [kindTag]) the user has
 * closed in the current render pass. It's intentionally **not** persisted —
 * the caller maintains it with `remember`, so killing the app or moving to a
 * different kind of state reopens the banner.
 */
@Composable
fun SetupRecoveryBanner(
    state: HidLinkState,
    dismissedStates: Set<String>,
    onDismiss: (String) -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRerunSetup: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = bannerCopyFor(state) ?: return
    val tag = kindTag(state) ?: return
    if (copy.dismissible && tag in dismissedStates) return

    val containerColor = when (copy.tone) {
        BannerTone.TRANSIENT -> MaterialTheme.colorScheme.surfaceVariant
        BannerTone.ACTIONABLE -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (copy.tone) {
        BannerTone.TRANSIENT -> MaterialTheme.colorScheme.onSurfaceVariant
        BannerTone.ACTIONABLE -> MaterialTheme.colorScheme.onErrorContainer
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = copy.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(copy.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(copy.bodyRes),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (copy.actionRes != null) {
                TextButton(
                    onClick = {
                        when (copy.action) {
                            BannerAction.OPEN_BLUETOOTH -> onOpenBluetoothSettings()
                            BannerAction.RERUN_SETUP -> onRerunSetup()
                            BannerAction.OPEN_DIAGNOSTICS -> onOpenDiagnostics()
                            null -> {}
                        }
                    },
                ) { Text(stringResource(copy.actionRes)) }
            }
            if (copy.dismissible) {
                IconButton(onClick = { onDismiss(tag) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.banner_dismiss_close),
                    )
                }
            }
        }
    }
}

// ---- internals --------------------------------------------------------------

/**
 * Stable identifier for a state kind (not a state instance) so `dismissedStates`
 * survives equality changes in `data class` states (e.g. Reconnecting attempt
 * increments) without letting the user re-dismiss the same visual banner.
 *
 * Returns null when the state has no banner.
 */
private fun kindTag(state: HidLinkState): String? = when (state) {
    is HidLinkState.Connected -> null
    HidLinkState.Idle -> null
    HidLinkState.Advertising -> "ADVERTISING"
    is HidLinkState.Reconnecting -> "RECONNECTING"
    HidLinkState.Proxying -> "PROXYING"
    is HidLinkState.Unavailable -> "UNAVAILABLE_${state.reason.name}"
    is HidLinkState.Failed -> "FAILED_${state.cause.name}"
}

private enum class BannerTone { TRANSIENT, ACTIONABLE }

private data class BannerCopy(
    val titleRes: Int,
    val bodyRes: Int,
    val actionRes: Int?,
    val action: BannerAction?,
    val tone: BannerTone,
    val dismissible: Boolean,
    val icon: ImageVector,
)

private enum class BannerAction { OPEN_BLUETOOTH, RERUN_SETUP, OPEN_DIAGNOSTICS }

private fun bannerCopyFor(state: HidLinkState): BannerCopy? = when (state) {
    is HidLinkState.Connected -> null
    HidLinkState.Idle -> null
    HidLinkState.Advertising -> BannerCopy(
        titleRes = R.string.state_advertising_banner_title,
        bodyRes = R.string.state_advertising_banner_body,
        actionRes = null,
        action = null,
        tone = BannerTone.TRANSIENT,
        dismissible = true,
        icon = Icons.Filled.Info,
    )
    is HidLinkState.Reconnecting -> BannerCopy(
        titleRes = R.string.state_reconnecting_banner_title,
        bodyRes = R.string.state_reconnecting_banner_body,
        actionRes = null,
        action = null,
        tone = BannerTone.TRANSIENT,
        dismissible = true,
        icon = Icons.Filled.Info,
    )
    HidLinkState.Proxying -> BannerCopy(
        titleRes = R.string.state_proxying_banner_title,
        bodyRes = R.string.state_proxying_banner_body,
        actionRes = null,
        action = null,
        tone = BannerTone.TRANSIENT,
        dismissible = false,
        icon = Icons.Filled.Info,
    )
    is HidLinkState.Unavailable -> when (state.reason) {
        UnavailableReason.BLUETOOTH_OFF -> BannerCopy(
            titleRes = R.string.banner_bluetooth_off_title,
            bodyRes = R.string.banner_bluetooth_off_body,
            actionRes = R.string.banner_bluetooth_off_action,
            action = BannerAction.OPEN_BLUETOOTH,
            tone = BannerTone.ACTIONABLE,
            dismissible = false,
            icon = Icons.Filled.Error,
        )
        UnavailableReason.PERMISSIONS_MISSING -> BannerCopy(
            titleRes = R.string.banner_permissions_missing_title,
            bodyRes = R.string.banner_permissions_missing_body,
            actionRes = R.string.banner_permissions_missing_action,
            action = BannerAction.RERUN_SETUP,
            tone = BannerTone.ACTIONABLE,
            dismissible = false,
            icon = Icons.Filled.Error,
        )
        UnavailableReason.BUNDLED_PROFILE_INVALID -> BannerCopy(
            titleRes = R.string.banner_profile_invalid_title,
            bodyRes = R.string.banner_profile_invalid_body,
            actionRes = R.string.banner_profile_invalid_action,
            action = BannerAction.OPEN_DIAGNOSTICS,
            tone = BannerTone.ACTIONABLE,
            dismissible = false,
            icon = Icons.Filled.Error,
        )
        UnavailableReason.DEVICE_NO_HID_PERIPHERAL -> BannerCopy(
            titleRes = R.string.banner_no_hid_title,
            bodyRes = R.string.banner_no_hid_body,
            actionRes = null,
            action = null,
            tone = BannerTone.ACTIONABLE,
            dismissible = false,
            icon = Icons.Filled.Error,
        )
    }
    is HidLinkState.Failed -> when (state.cause) {
        FailureCause.REGISTER_APP_REJECTED,
        FailureCause.PROXY_TIMEOUT,
        FailureCause.UNEXPECTED -> BannerCopy(
            titleRes = R.string.banner_failed_title,
            bodyRes = R.string.banner_failed_body,
            actionRes = R.string.banner_failed_action,
            action = BannerAction.RERUN_SETUP,
            tone = BannerTone.ACTIONABLE,
            dismissible = false,
            icon = Icons.Filled.Error,
        )
    }
}
