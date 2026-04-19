package com.vibepad.keyboard.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R
import com.vibepad.keyboard.hid.HostDevice
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.ui.BrandChrome

private const val WIZARD_STEP_COUNT = 5

/**
 * Top-level wizard composable. Stateless w.r.t. hosting activity — the callers wire
 * up permission launchers, settings intents, and the ViewModel.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenBondingSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onComplete: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    // Poll bonded devices only while on the pairing step to avoid unnecessary work.
    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.PAIRING) viewModel.startBondedPolling()
        else viewModel.stopBondedPolling()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopBondedPolling() }
    }

    // Kick the caller once the ViewModel says we're done.
    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.DONE) onComplete()
    }

    OnboardingScaffold(
        state = state,
        onAdvance = {
            if (state.step == OnboardingStep.HOST_TARGET) viewModel.finish()
            else viewModel.advance()
        },
        onRetreat = viewModel::retreat,
        onRequestPermissions = onRequestPermissions,
        onOpenBluetoothSettings = onOpenBluetoothSettings,
        onOpenBondingSettings = onOpenBondingSettings,
        onOpenBatterySettings = onOpenBatterySettings,
        onSelectHost = viewModel::selectHost,
        onSelectHostTarget = viewModel::selectHostTarget,
    )
}

@Composable
private fun OnboardingScaffold(
    state: OnboardingState,
    onAdvance: () -> Unit,
    onRetreat: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenBondingSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSelectHost: (HostDevice) -> Unit,
    onSelectHostTarget: (HostTarget) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            OnboardingTopBar()
            Spacer(Modifier.height(16.dp))
            // Node + connecting bar progress covering the 5 interactive steps
            // (INTRO..HOST_TARGET). DONE is a virtual terminal state and does
            // not get a node.
            OnboardingProgress(
                currentStepIndex = state.step.ordinal.coerceAtMost(WIZARD_STEP_COUNT - 1),
                totalSteps = WIZARD_STEP_COUNT,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.step) {
                    OnboardingStep.INTRO -> StepIntro()
                    OnboardingStep.PERMISSIONS -> StepPermissions(
                        granted = state.permissionsGranted,
                        onRequest = onRequestPermissions,
                    )
                    OnboardingStep.BLUETOOTH -> StepBluetooth(
                        enabled = state.bluetoothEnabled,
                        onOpenSettings = onOpenBluetoothSettings,
                    )
                    OnboardingStep.PAIRING -> StepPairing(
                        bondedHosts = state.bondedHosts,
                        selected = state.selectedHost,
                        onSelect = onSelectHost,
                        onOpenSettings = onOpenBondingSettings,
                        batteryExempt = state.batteryOptimizationExempt,
                        onRequestBattery = onOpenBatterySettings,
                    )
                    OnboardingStep.HOST_TARGET -> StepHostTarget(
                        host = state.selectedHost,
                        selected = state.selectedHostTarget,
                        onSelect = onSelectHostTarget,
                    )
                    OnboardingStep.DONE -> { /* completion kicks caller */ }
                }
            }

            NavigationRow(
                canAdvance = state.canAdvance,
                canRetreat = state.step.ordinal > 0,
                primaryLabel = if (state.step == OnboardingStep.HOST_TARGET) {
                    stringResource(R.string.onboarding_finish)
                } else {
                    stringResource(R.string.onboarding_next)
                },
                onAdvance = onAdvance,
                onRetreat = onRetreat,
            )
        }
    }
}

// ---- Brand chrome -----------------------------------------------------------

/**
 * Persistent brand strip shown at the top of every onboarding step. Reuses
 * the same [BrandChrome] composable the main operator AppBar uses so brand
 * presentation stays consistent between the two screens where brand chrome
 * is allowed to appear.
 */
@Composable
private fun OnboardingTopBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandChrome()
    }
}

// ---- Step bodies ------------------------------------------------------------

@Composable
private fun StepIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_intro_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.onboarding_intro_body),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StepPermissions(granted: Boolean, onRequest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_perm_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.onboarding_perm_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        StatusCard(
            ok = granted,
            okText = stringResource(R.string.onboarding_perm_ok),
            pendingText = stringResource(R.string.onboarding_perm_pending),
        )
        if (!granted) {
            Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_perm_request))
            }
        }
    }
}

@Composable
private fun StepBluetooth(enabled: Boolean, onOpenSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_bt_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.onboarding_bt_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        StatusCard(
            ok = enabled,
            okText = stringResource(R.string.onboarding_bt_ok),
            pendingText = stringResource(R.string.onboarding_bt_pending),
        )
        if (!enabled) {
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.onboarding_bt_open))
            }
        }
    }
}

@Composable
private fun StepPairing(
    bondedHosts: List<HostDevice>,
    selected: HostDevice?,
    onSelect: (HostDevice) -> Unit,
    onOpenSettings: () -> Unit,
    batteryExempt: Boolean,
    onRequestBattery: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_pair_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        // Direction callout: explicitly tell the user the computer is the one
        // that has to kick off pairing. This is the #1 support issue the Paired
        // Hosts page surfaces; teaching it up front prevents the confusion
        // rather than explaining it after they get stuck.
        com.vibepad.keyboard.ui.DirectionCallout(
            variant = com.vibepad.keyboard.ui.DirectionCalloutVariant.Full,
        )
        Text(
            text = stringResource(R.string.onboarding_pair_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_pair_open_settings))
        }
        if (bondedHosts.isEmpty()) {
            Text(
                text = stringResource(R.string.onboarding_pair_waiting),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                text = stringResource(R.string.onboarding_pair_pick),
                style = MaterialTheme.typography.titleSmall,
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(bondedHosts) { host ->
                    HostRow(
                        host = host,
                        selected = host == selected,
                        onSelect = { onSelect(host) },
                    )
                }
            }
        }
        if (!batteryExempt) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.onboarding_battery_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_battery_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onRequestBattery) {
                        Text(stringResource(R.string.onboarding_battery_open))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepHostTarget(
    host: HostDevice?,
    selected: HostTarget?,
    onSelect: (HostTarget) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.onboarding_target_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = host?.let {
                stringResource(R.string.onboarding_target_body_with_host, it.name)
            } ?: stringResource(R.string.onboarding_target_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        TargetOption(
            label = stringResource(R.string.host_target_macos),
            helper = stringResource(R.string.host_target_macos_helper),
            icon = Icons.Filled.Computer,
            selected = selected == HostTarget.MACOS,
            onSelect = { onSelect(HostTarget.MACOS) },
        )
        TargetOption(
            label = stringResource(R.string.host_target_windows),
            helper = stringResource(R.string.host_target_windows_helper),
            icon = Icons.Filled.PhoneAndroid,
            selected = selected == HostTarget.WINDOWS,
            onSelect = { onSelect(HostTarget.WINDOWS) },
        )
    }
}

// ---- Small widgets ----------------------------------------------------------

@Composable
private fun StatusCard(ok: Boolean, okText: String, pendingText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ok) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(okText, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(pendingText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun HostRow(host: HostDevice, selected: Boolean, onSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.size(12.dp))
            Column {
                Text(host.name, style = MaterialTheme.typography.titleMedium)
                Text(host.address, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TargetOption(
    label: String,
    helper: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Spacer(Modifier.size(12.dp))
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(helper, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NavigationRow(
    canAdvance: Boolean,
    canRetreat: Boolean,
    primaryLabel: String,
    onAdvance: () -> Unit,
    onRetreat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(
            onClick = onRetreat,
            enabled = canRetreat,
        ) { Text(stringResource(R.string.onboarding_back)) }
        Button(
            onClick = onAdvance,
            enabled = canAdvance,
        ) { Text(primaryLabel) }
    }
}
