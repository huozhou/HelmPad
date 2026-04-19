package com.vibepad.keyboard.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R

/**
 * Bottom-sheet picker for Claude Code models.
 *
 * Opens when the `switch_model` macro tile is tapped. Each row sends a single
 * literal `/model <id>\n` to the host — a one-shot switch rather than a two-step
 * TUI navigation. The sheet owns no state of its own; parent tracks visibility and
 * `onSelect` / `onDismiss` are terminal.
 *
 * See `claude-code-profile-trim` design decisions 2 (one-shot switching), 3
 * (stateless button — no "currently selected" indicator), and 6 (hardcoded model
 * list for v1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    onSelect: (modelId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ModelPickerContent(
            onSelect = onSelect,
            onCancel = onDismiss,
        )
    }
}

@Composable
private fun ModelPickerContent(
    onSelect: (modelId: String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Text(
            text = stringResource(R.string.model_picker_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        )
        ModelRow(
            titleRes = R.string.model_picker_opus_title,
            bodyRes = R.string.model_picker_opus_body,
            onClick = {
                Haptics.regular(context)
                onSelect(MODEL_OPUS)
            },
        )
        ModelRow(
            titleRes = R.string.model_picker_sonnet_title,
            bodyRes = R.string.model_picker_sonnet_body,
            onClick = {
                Haptics.regular(context)
                onSelect(MODEL_SONNET)
            },
        )
        ModelRow(
            titleRes = R.string.model_picker_haiku_title,
            bodyRes = R.string.model_picker_haiku_body,
            onClick = {
                Haptics.regular(context)
                onSelect(MODEL_HAIKU)
            },
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 16.dp),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun ModelRow(
    titleRes: Int,
    bodyRes: Int,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(stringResource(bodyRes)) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

/** Model ids forwarded verbatim to `/model <id>\n`. */
const val MODEL_OPUS = "opus"
const val MODEL_SONNET = "sonnet"
const val MODEL_HAIKU = "haiku"
