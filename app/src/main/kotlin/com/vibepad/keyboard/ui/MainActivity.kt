package com.vibepad.keyboard.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vibepad.keyboard.VibePadApplication
import com.vibepad.keyboard.hid.HidForegroundService
import com.vibepad.keyboard.macro.Profile
import com.vibepad.keyboard.pairing.OnboardingScreen
import com.vibepad.keyboard.pairing.OnboardingViewModel
import com.vibepad.keyboard.pairing.PermissionHelper
import com.vibepad.keyboard.ui.theme.VibePadTheme

/**
 * Single activity hosting the entire operator UI. Routes between two stable states:
 *
 *  1. Onboarding — shown until [com.vibepad.keyboard.pairing.OnboardingCompletionStore]
 *     says the user finished the 6-step wizard. No HID traffic is started yet.
 *  2. Operator — AppBar + Touchpad + MacroGrid, backed by a foreground-service-hosted
 *     [com.vibepad.keyboard.hid.HidTransport].
 *
 * The transport itself lives in [VibePadApplication] so the service and the activity
 * share one instance. The service owns the notification and the registration lifecycle;
 * this activity just drives the UI.
 */
class MainActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* onResume refreshes the onboarding ViewModel. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibePadTheme {
                val app = applicationContext as VibePadApplication
                val completed by app.completionStore
                    .isCompleteFlow()
                    .collectAsState(initial = null)

                when (completed) {
                    null -> LoadingScaffold()
                    false -> OnboardingRoute(
                        onRequestPermissions = {
                            permLauncher.launch(PermissionHelper.requiredPermissions())
                        },
                        onOpenBluetoothSettings = {
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        onOpenBondingSettings = {
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        },
                        onOpenBatterySettings = { openBatteryOptimizationSettings() },
                    )
                    true -> OperatorRoute(app = app)
                }
            }
        }
    }

    private fun openBatteryOptimizationSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

// ---- Onboarding route -------------------------------------------------------

@Composable
private fun OnboardingRoute(
    onRequestPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onOpenBondingSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val context = LocalContext.current
    val vm: OnboardingViewModel = viewModel(factory = OnboardingViewModel.factory(context))
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh env state every time the user returns from a system dialog.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshEnvironment()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    OnboardingScreen(
        viewModel = vm,
        onRequestPermissions = onRequestPermissions,
        onOpenBluetoothSettings = onOpenBluetoothSettings,
        onOpenBondingSettings = onOpenBondingSettings,
        onOpenBatterySettings = onOpenBatterySettings,
        onComplete = { /* DataStore flow flips to `true` and the outer router re-renders. */ },
    )
}

// ---- Operator route ---------------------------------------------------------

@Composable
private fun OperatorRoute(app: VibePadApplication) {
    val context = LocalContext.current

    // Start the foreground service exactly once the user has earned it (onboarding
    // is complete). Leaving it running after onStop is fine — that's the whole
    // point of the FGS, keeping the HID registration alive across UI lifecycles.
    LaunchedEffect(Unit) {
        ContextCompat.startForegroundService(context, HidForegroundService.startIntent(context))
    }

    // Load bundled profiles off the main thread once. The list is the source the
    // operator + Settings + onboarding profile pickers all read from; any JSON
    // whose ProfileLoader validation fails is dropped here and its diagnostic
    // becomes the error message if nothing valid remains. See
    // `add-codex-cursor-profiles` design decisions 2 + 5 for the fallback and
    // VM-rebuild keying semantics respectively.
    var profileLoadResult by remember { mutableStateOf<ProfileLoadResult>(ProfileLoadResult.Loading) }
    LaunchedEffect(Unit) {
        profileLoadResult = try {
            val outcome = app.profileSource.loadAll()
            if (outcome.profiles.isEmpty()) {
                ProfileLoadResult.Failed(
                    "No valid profile found. Diagnostics: " +
                        outcome.diagnostics.joinToString("; ") { "${it.assetPath}: ${it.message}" },
                )
            } else {
                ProfileLoadResult.Ok(outcome.profiles)
            }
        } catch (t: Throwable) {
            ProfileLoadResult.Failed(t.message ?: t::class.java.simpleName)
        }
    }

    when (val r = profileLoadResult) {
        ProfileLoadResult.Loading -> LoadingScaffold()
        is ProfileLoadResult.Failed -> ErrorScaffold(message = r.message)
        is ProfileLoadResult.Ok -> OperatorContent(
            app = app,
            profiles = r.profiles,
        )
    }
}

@Composable
private fun OperatorContent(
    app: VibePadApplication,
    profiles: List<Profile>,
) {
    // Source-of-truth for the active profile. `null` at cold start (user has
    // never picked one) and after this activity's first render — we fall back
    // to Claude Code (DEFAULT_PROFILE_ID) without writing back, so the user's
    // first real choice is the first write. See design decision 2.
    val activeProfileId by app.selectionsStore.profileIdFlow().collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val profile = remember(activeProfileId, profiles) {
        resolveActiveProfile(profiles, activeProfileId)
    }

    // If the stored id pointed at a profile that no longer loads (user removed
    // the JSON, or we deleted/renamed a bundled one between releases), heal
    // the store once so future cold starts don't walk the fallback chain. The
    // write is idempotent — same id, no-op — for the common valid case.
    LaunchedEffect(activeProfileId, profile.id) {
        if (activeProfileId != null && activeProfileId != profile.id) {
            app.selectionsStore.setProfileId(profile.id)
        }
    }

    val vm: OperatorViewModel = viewModel(
        key = profile.id,
        factory = OperatorViewModel.factory(app, profile),
    )
    val hostTarget by vm.hostTarget.collectAsState()
    val showModelPicker by vm.showModelPicker.collectAsState()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showPairedHosts by remember { mutableStateOf(false) }

    if (showPairedHosts) {
        PairedHostsScreen(onClose = { showPairedHosts = false })
    } else if (showSettings) {
        SettingsScreen(
            app = app,
            profiles = profiles,
            activeProfile = profile,
            onClose = { showSettings = false },
            onResetOnboarding = { showSettings = false },
            onNavigatePairedHosts = { showPairedHosts = true },
        )
    } else {
        MainScreen(
            transport = app.hidTransport,
            profile = profile,
            hostTarget = hostTarget,
            onSelectHostTarget = vm::selectHostTarget,
            touchpadController = vm.touchpadController,
            onFireMacro = vm::fireMacro,
            onOpenSettings = { showSettings = true },
            onOpenPairedHosts = { showPairedHosts = true },
            onOpenBluetoothSettings = {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            onRerunSetup = {
                scope.launch { app.completionStore.reset() }
            },
        )
        if (showModelPicker) {
            ModelPickerSheet(
                onSelect = vm::selectModel,
                onDismiss = vm::dismissModelPicker,
            )
        }
    }
}

// ---- Shared scaffolds -------------------------------------------------------

@Composable
private fun LoadingScaffold() {
    Scaffold { inner ->
        Box(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }
}

@Composable
private fun ErrorScaffold(message: String) {
    Scaffold { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Startup failed", style = MaterialTheme.typography.headlineSmall)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// ---- Helpers ----------------------------------------------------------------

/**
 * Cold-start fallback profile id. Used by [OperatorContent] when the user has
 * not yet chosen a profile (store returns `null`) *or* when the stored id no
 * longer resolves to a loaded profile. See `add-codex-cursor-profiles`
 * design decision 2.
 */
internal const val DEFAULT_PROFILE_ID = "profile.claude-code"

/**
 * Pure fallback chain extracted from [OperatorContent] for unit testing.
 *
 * Evaluation order, aligned with `add-codex-cursor-profiles` design decision 2:
 *
 *  1. Stored id → if it names a currently loaded profile, use it.
 *  2. Default id ([DEFAULT_PROFILE_ID]) → used for `null` / unknown stored ids
 *     whenever Claude Code is still bundled.
 *  3. First loaded profile — only reached when Claude Code itself is missing,
 *     which is defensive: every released build ships Claude Code.
 *
 * Precondition: [profiles] must not be empty. [OperatorContent]'s caller
 * already guarantees this via [ProfileLoadResult.Ok], but callers in tests
 * should handle the empty case themselves.
 */
internal fun resolveActiveProfile(
    profiles: List<Profile>,
    storedProfileId: String?,
): Profile {
    require(profiles.isNotEmpty()) { "resolveActiveProfile requires at least one loaded profile" }
    return profiles.firstOrNull { it.id == storedProfileId }
        ?: profiles.firstOrNull { it.id == DEFAULT_PROFILE_ID }
        ?: profiles.first()
}

private sealed interface ProfileLoadResult {
    data object Loading : ProfileLoadResult
    data class Failed(val message: String) : ProfileLoadResult
    data class Ok(val profiles: List<Profile>) : ProfileLoadResult
}
