package com.vibepad.keyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.pairing.HostOsRecord

/**
 * Sheet shown when the user taps the "Host OS" sub-row in Paired Hosts.
 *
 * Three options, all radio-style — only one can be selected at a time:
 *  - **macOS** / **Windows**: write a manual override that wins over the
 *    detector forever (until the user picks "Use auto-detected" again).
 *  - **Use auto-detected**: clear the override. Subtitle explains *what*
 *    auto-detection produced, so the user knows what they'd be agreeing to;
 *    if the detector has no verdict, this option is disabled with a
 *    matching "(Unknown — no detection yet)" subtitle.
 *
 * The current selection is computed from [HostOsRecord]: if `userOverride`
 * is set we light up the corresponding manual row; otherwise we light up
 * "Use auto-detected".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostOsPickerSheet(
    deviceAlias: String,
    record: HostOsRecord,
    onSelect: (HostTarget?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentOverride = record.userOverrideHostTarget
    val detected = record.detectedHostTarget

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = stringResource(R.string.host_os_picker_title, deviceAlias),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            RadioRow(
                label = stringResource(R.string.host_os_picker_macos),
                selected = currentOverride == HostTarget.MACOS,
                enabled = true,
                onClick = { onSelect(HostTarget.MACOS) },
            )
            RadioRow(
                label = stringResource(R.string.host_os_picker_windows),
                selected = currentOverride == HostTarget.WINDOWS,
                enabled = true,
                onClick = { onSelect(HostTarget.WINDOWS) },
            )

            // "Use auto-detected" — the subtitle reveals what the detector
            // currently thinks, since picking this branch otherwise feels
            // opaque ("auto-detected as what?"). Disabled when there's no
            // verdict so we don't trick the user into a no-op.
            val autoSuffix = when (detected) {
                HostTarget.MACOS -> stringResource(R.string.host_os_picker_auto_suffix_macos)
                HostTarget.WINDOWS -> stringResource(R.string.host_os_picker_auto_suffix_windows)
                null -> stringResource(R.string.host_os_picker_auto_suffix_unknown)
            }
            RadioRow(
                label = stringResource(R.string.host_os_picker_auto),
                subtitle = autoSuffix,
                selected = currentOverride == null,
                enabled = detected != null,
                onClick = { onSelect(null) },
            )
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
