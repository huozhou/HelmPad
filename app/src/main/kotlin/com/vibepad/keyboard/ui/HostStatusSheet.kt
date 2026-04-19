package com.vibepad.keyboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.input.HostTarget

/**
 * Detail sheet opened by tapping the AppBar's `HostBadge`.
 *
 * Surfaces the facts a user wants when they glance at that icon and wonder
 * "wait, *which* computer is this?":
 *
 *  - Device alias (user alias if set, else system bonded name)
 *  - Last 4 chars of the MAC (as a disambiguator when two machines share a name)
 *  - Current target OS + a "change in Settings" link (Change 4 will re-route
 *    this to the per-device OS row in Paired Hosts)
 *  - "Last connected" relative time
 *  - "Forget this device" shortcut → jumps into Paired Hosts for the confirm dialog
 *
 * When not connected, the sheet degrades to an empty-state with an "Open
 * Bluetooth settings" button — the most useful next action when there's no
 * host to describe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostStatusSheet(
    state: HidLinkState,
    hostTarget: HostTarget,
    alias: String?,
    lastConnectedAgo: String?,
    onOpenSettings: () -> Unit,
    onForget: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        if (state is HidLinkState.Connected) {
            ConnectedContent(
                deviceName = alias?.takeIf { it.isNotBlank() } ?: state.host.name,
                macAddress = state.host.address,
                hostTarget = hostTarget,
                lastConnectedAgo = lastConnectedAgo,
                onOpenSettings = onOpenSettings,
                onForget = onForget,
            )
        } else {
            EmptyContent(
                onOpenBluetoothSettings = onOpenBluetoothSettings,
            )
        }
    }
}

@Composable
private fun ConnectedContent(
    deviceName: String,
    macAddress: String,
    hostTarget: HostTarget,
    lastConnectedAgo: String?,
    onOpenSettings: () -> Unit,
    onForget: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = stringResource(R.string.host_status_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        LabeledRow(
            label = stringResource(R.string.host_status_device_alias_label),
            value = deviceName,
        )
        LabeledRow(
            label = stringResource(R.string.host_status_mac_suffix_label),
            value = macSuffix(macAddress),
        )
        LabeledRow(
            label = stringResource(R.string.host_status_os_label),
            value = when (hostTarget) {
                HostTarget.MACOS -> stringResource(R.string.host_target_macos)
                HostTarget.WINDOWS -> stringResource(R.string.host_target_windows)
            },
            trailing = {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.host_status_change_os_link))
                }
            },
        )
        if (lastConnectedAgo != null) {
            LabeledRow(
                label = stringResource(R.string.host_status_last_connected_label),
                value = lastConnectedAgo,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onForget) {
                Text(stringResource(R.string.host_status_forget_button))
            }
        }
    }
}

@Composable
private fun EmptyContent(
    onOpenBluetoothSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = null,
            )
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text(
                text = stringResource(R.string.host_status_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onOpenBluetoothSettings) {
                Text(stringResource(R.string.host_status_empty_action))
            }
        }
    }
}

@Composable
private fun LabeledRow(
    label: String,
    value: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

/**
 * Formats the last 5 chars of a MAC — e.g. `"AA:BB:CC:DD:EE:FF"` → `"EE:FF"`.
 * We deliberately don't show the full MAC in the sheet because it's:
 *   1. too long to glance-read on the narrow Android screen,
 *   2. identifying enough with the last octet pair when the user already knows
 *      their own devices,
 *   3. slightly privacy-friendlier if a screenshot escapes.
 */
private fun macSuffix(address: String): String {
    val normalized = address.uppercase()
    return if (normalized.length >= 5) normalized.takeLast(5) else normalized
}
