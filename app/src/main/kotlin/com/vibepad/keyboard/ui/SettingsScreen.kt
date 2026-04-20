package com.vibepad.keyboard.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.BuildConfig
import com.vibepad.keyboard.R
import com.vibepad.keyboard.VibePadApplication
import com.vibepad.keyboard.hid.FailureCause
import com.vibepad.keyboard.hid.HidLinkState
import com.vibepad.keyboard.hid.UnavailableReason
import com.vibepad.keyboard.macro.Profile
import kotlinx.coroutines.launch

/**
 * Settings & diagnostics. Narrow scope by design — there is no macro editor and
 * no profile picker in v1. The screen has three jobs:
 *
 *  1. Surface the active profile so the user knows what the eight macros mean.
 *  2. Show whether the HID link is healthy and offer a battery-optimization
 *     escape hatch when Android is killing the foreground service.
 *  3. Let the user replay the welcome wizard without nuking anything they\'ve set
 *     up. The button is treated as semi-destructive: confirmation dialog +
 *     error-tinted styling so it doesn\'t look like a one-tap reset.
 *
 * The "Device" (model / Android version / HID-availability) and "Profile
 * diagnostics" sections that lived here previously were removed in
 * `settings-trim`: the former duplicated Android\'s own About-phone screen, the
 * latter only ever showed `OK` because the only profile is bundled and validated
 * by a unit test before each build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    app: VibePadApplication,
    profiles: List<Profile>,
    activeProfile: Profile,
    onClose: () -> Unit,
    onResetOnboarding: () -> Unit,
    onNavigatePairedHosts: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linkState by app.hidTransport.state.collectAsState()
    var showRerunConfirm by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigatePairedHosts),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    leadingContent = { Icon(Icons.Filled.Devices, contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.paired_hosts_entry_title)) },
                    supportingContent = { Text(stringResource(R.string.paired_hosts_entry_subtitle)) },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    },
                )
            }

            SectionHeader(stringResource(R.string.settings_profile_section))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                ProfileDropdown(
                    profiles = profiles,
                    activeProfileId = activeProfile.id,
                    onSelect = { id ->
                        if (id != activeProfile.id) {
                            scope.launch { app.selectionsStore.setProfileId(id) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            InfoCard {
                KeyValue(
                    stringResource(R.string.settings_profile_about_label),
                    profileAboutText(activeProfile),
                )
                HorizontalDivider()
                KeyValue(
                    stringResource(R.string.settings_profile_future_label),
                    stringResource(R.string.settings_profile_future_body),
                )
            }

            SectionHeader("Connection health")
            InfoCard {
                KeyValue("Link state", linkStateLabel(linkState))
                HorizontalDivider()
                KeyValue("Last failure", lastFailureLabel(linkState))
            }
            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${context.packageName}"))
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Request battery-optimization exemption") }

            // Re-run is semi-destructive in user perception (even though it only
            // resets the onboarding flag). Force a confirmation tap and tint the
            // entry point in the error palette so it stands out from the benign
            // battery-exemption button above it.
            OutlinedButton(
                onClick = { showRerunConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Re-run setup wizard") }

            SectionHeader(stringResource(R.string.settings_about_section))
            AboutVersionCard()
        }
    }

    if (showRerunConfirm) {
        AlertDialog(
            onDismissRequest = { showRerunConfirm = false },
            title = { Text(stringResource(R.string.settings_rerun_confirm_title)) },
            text = { Text(stringResource(R.string.settings_rerun_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRerunConfirm = false
                        scope.launch {
                            app.completionStore.reset()
                            onResetOnboarding()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.settings_rerun_confirm_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showRerunConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Column {
        Text(
            text = key,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

/**
 * Per-profile "About" copy. We hand-write a sentence per known bundled id so the
 * Settings page can speak in human terms ("eight macros tuned for Claude Code")
 * instead of dumping the slot list. New bundled profiles are expected to add a
 * branch here. Anything unknown falls back to a generic n-macros sentence.
 */
@Composable
private fun profileAboutText(profile: Profile): String = when (profile.id) {
    "profile.claude-code" -> stringResource(R.string.settings_profile_about_claude_code)
    "profile.codex" -> stringResource(R.string.settings_profile_about_codex)
    "profile.cursor" -> stringResource(R.string.settings_profile_about_cursor)
    else -> "${profile.slots.size} macros."
}

/**
 * About → Version row.
 *
 * Source of truth is `BuildConfig`, which AGP populates from the Gradle script.
 * Debug builds get a ` · debug` suffix so a screenshot of a dev build can never
 * masquerade as a release. Long-press copies the value to clipboard — when a
 * user reports an issue we need both versionName and versionCode and asking
 * them to type a 5-digit number rarely ends well.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutVersionCard() {
    val context = LocalContext.current
    val versionLabel = stringResource(R.string.settings_about_version_label)
    val versionValue = if (BuildConfig.DEBUG) {
        stringResource(
            R.string.settings_about_version_value_debug,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
    } else {
        stringResource(
            R.string.settings_about_version_value,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
    }
    val copiedToast = stringResource(R.string.settings_about_version_copied)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Helm Pad version", versionValue))
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    },
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KeyValue(versionLabel, versionValue)
        }
    }
}

private fun linkStateLabel(state: HidLinkState): String = when (state) {
    HidLinkState.Idle -> "Idle"
    HidLinkState.Proxying -> "Connecting to Bluetooth profile proxy…"
    HidLinkState.Advertising -> "Advertising — waiting for host"
    is HidLinkState.Connected -> "Connected to ${state.host.name} (${state.host.address})"
    is HidLinkState.Reconnecting -> {
        val target = state.previousHost?.name ?: "host"
        "Reconnecting to $target, attempt ${state.attempt}"
    }
    is HidLinkState.Unavailable -> "Unavailable: ${state.reason.name}"
    is HidLinkState.Failed -> "Failed: ${causeLabel(state.cause)}"
}

private fun lastFailureLabel(state: HidLinkState): String = when (state) {
    is HidLinkState.Failed -> causeLabel(state.cause)
    is HidLinkState.Unavailable -> when (state.reason) {
        UnavailableReason.BLUETOOTH_OFF -> "Bluetooth is off"
        UnavailableReason.PERMISSIONS_MISSING -> "Runtime permissions missing"
        UnavailableReason.DEVICE_NO_HID_PERIPHERAL -> "Device firmware cannot act as a HID peripheral"
        UnavailableReason.BUNDLED_PROFILE_INVALID -> "Bundled profile failed validation"
    }
    else -> "—"
}

private fun causeLabel(cause: FailureCause): String = when (cause) {
    FailureCause.REGISTER_APP_REJECTED -> "HID app registration rejected by the adapter"
    FailureCause.PROXY_TIMEOUT -> "Timed out acquiring the BluetoothHidDevice proxy"
    FailureCause.UNEXPECTED -> "Unexpected error"
}
