package com.vibepad.keyboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibepad.keyboard.R
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.pairing.PairedHostRow
import com.vibepad.keyboard.pairing.PairedHostsUiState
import com.vibepad.keyboard.pairing.PairedHostsViewModel
import com.vibepad.keyboard.pairing.Source
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.TimeUnit

/**
 * "Paired Hosts" — reachable from Settings. Shows every host Helm Pad has
 * successfully connected to (ledger ∩ bondedDevices), lets the user rename
 * them locally, and lets them Forget with a reflective-removeBond + fallback.
 *
 * Design notes:
 *  - The direction callout at the top is permanent — this page is low-traffic,
 *    and the education it provides is the #1 reason a user lands here confused.
 *  - The active (currently connected) row is flagged with a filled Bluetooth
 *    icon + a "connected" chip; everything else uses a neutral Computer icon.
 *  - Forget is a destructive action and goes behind an AlertDialog. The
 *    Snackbar host at the bottom surfaces the fallback case.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairedHostsScreen(
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val vm: PairedHostsViewModel = viewModel(factory = PairedHostsViewModel.factory(context))
    val state by vm.state.collectAsState()
    val pickerFor by vm.hostOsPickerFor.collectAsState()

    val snackbarState = remember { SnackbarHostState() }
    val fallbackMsg = stringResource(R.string.paired_hosts_fallback_snackbar)
    val forgotMsg = stringResource(R.string.paired_hosts_forgot_snackbar)
    val errorMsg = stringResource(R.string.paired_hosts_error_snackbar)

    LaunchedEffect(vm) {
        vm.events.collectLatest { event ->
            when (event) {
                PairedHostsViewModel.Event.FallbackOpenedSettings -> snackbarState.showSnackbar(fallbackMsg)
                PairedHostsViewModel.Event.Forgot,
                PairedHostsViewModel.Event.ForgotLocally -> snackbarState.showSnackbar(forgotMsg)
                is PairedHostsViewModel.Event.Error -> snackbarState.showSnackbar(errorMsg)
            }
        }
    }

    var forgetCandidate by remember { mutableStateOf<PairedHostRow?>(null) }
    var renameCandidate by remember { mutableStateOf<PairedHostRow?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.paired_hosts_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DirectionCallout(variant = DirectionCalloutVariant.Full)

            when (val s = state) {
                PairedHostsUiState.Loading -> LoadingBlock()
                PairedHostsUiState.Empty -> EmptyBlock()
                is PairedHostsUiState.List -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(s.rows, key = { it.mac }) { row ->
                            HostListItem(
                                row = row,
                                onForget = { forgetCandidate = row },
                                onRename = { renameCandidate = row },
                                onOpenHostOsPicker = { vm.onOpenHostOsPicker(row) },
                                onReDetect = { vm.onReDetect(row.mac) },
                            )
                        }
                    }
                }
            }
        }
    }

    forgetCandidate?.let { target ->
        ForgetDialog(
            row = target,
            onConfirm = {
                vm.onForget(target.mac)
                forgetCandidate = null
            },
            onDismiss = { forgetCandidate = null },
        )
    }
    renameCandidate?.let { target ->
        RenameDialog(
            row = target,
            onSave = { newAlias ->
                vm.onRename(target.mac, newAlias)
                renameCandidate = null
            },
            onDismiss = { renameCandidate = null },
        )
    }
    pickerFor?.let { row ->
        HostOsPickerSheet(
            deviceAlias = row.displayName,
            record = row.hostOs,
            onSelect = { target -> vm.onSelectHostOs(row.mac, target) },
            onDismiss = { vm.onDismissHostOsPicker() },
        )
    }
}

@Composable
private fun LoadingBlock() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBlock() {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Icon(
            imageVector = Icons.Filled.Computer,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.paired_hosts_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.paired_hosts_empty_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HostListItem(
    row: PairedHostRow,
    onForget: () -> Unit,
    onRename: () -> Unit,
    onOpenHostOsPicker: () -> Unit,
    onReDetect: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        ListItem(
            leadingContent = {
                if (row.isActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.BluetoothConnected,
                                contentDescription = stringResource(R.string.paired_hosts_active_badge),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            },
            headlineContent = { Text(row.displayName, fontWeight = FontWeight.Medium) },
            supportingContent = {
                Text(
                    text = relativeSeenAt(row.lastSeenAt),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.paired_hosts_row_overflow),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.paired_hosts_action_rename)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.host_os_redetect)) },
                            leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                            onClick = { menuOpen = false; onReDetect() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.paired_hosts_action_forget)) },
                            leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; onForget() },
                        )
                    }
                }
            },
        )
        HostOsSubRow(row = row, onClick = onOpenHostOsPicker)
    }
}

/**
 * Indented sub-row under each host showing the resolved OS + provenance, with
 * a "Needs review" badge for hosts the inspector hasn't decided on yet. Tap
 * opens the picker sheet.
 */
@Composable
private fun HostOsSubRow(row: PairedHostRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 72.dp, end = 16.dp, top = 0.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.host_os_row_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = effectiveOsLabel(row),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (row.needsReview) {
                    Spacer(Modifier.width(8.dp))
                    NeedsReviewBadge()
                }
            }
            val source = sourceLabelOrNull(row)
            if (source != null) {
                Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NeedsReviewBadge() {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.host_os_needs_review_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}

@Composable
private fun effectiveOsLabel(row: PairedHostRow): String = when (row.effectiveHostTarget) {
    HostTarget.MACOS -> stringResource(R.string.host_os_picker_macos)
    HostTarget.WINDOWS -> stringResource(R.string.host_os_picker_windows)
    null -> stringResource(R.string.host_os_redetect_pending)
}

@Composable
private fun sourceLabelOrNull(row: PairedHostRow): String? {
    val rec = row.hostOs
    if (rec.userOverrideHostTarget != null) return stringResource(R.string.host_os_source_manual)
    return when (rec.detectedSource) {
        Source.NAME -> stringResource(R.string.host_os_source_name)
        Source.COD -> stringResource(R.string.host_os_source_cod)
        Source.OUI -> stringResource(R.string.host_os_source_oui)
        Source.NONE -> if (rec.detectedHostTarget == null) null else stringResource(R.string.host_os_source_none)
    }
}

@Composable
private fun ForgetDialog(
    row: PairedHostRow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paired_hosts_forget_title)) },
        text = { Text(stringResource(R.string.paired_hosts_forget_body, row.displayName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.paired_hosts_action_forget)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RenameDialog(
    row: PairedHostRow,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(row.alias.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.paired_hosts_rename_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.paired_hosts_rename_body, row.systemName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.paired_hosts_rename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Tiny relative formatter: "just now" / "3 min ago" / "4 h ago" / "6 d ago".
 * Intentionally coarse — users don't need second-level precision here, and
 * avoiding `DateTimeFormatter` keeps the preview / snapshot renders hermetic.
 */
private fun relativeSeenAt(lastSeenAt: Long): String {
    val diffMs = (System.currentTimeMillis() - lastSeenAt).coerceAtLeast(0)
    val min = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hr = TimeUnit.MILLISECONDS.toHours(diffMs)
    val day = TimeUnit.MILLISECONDS.toDays(diffMs)
    return when {
        min < 1 -> "just now"
        hr < 1 -> "${min}m ago"
        day < 1 -> "${hr}h ago"
        else -> "${day}d ago"
    }
}
