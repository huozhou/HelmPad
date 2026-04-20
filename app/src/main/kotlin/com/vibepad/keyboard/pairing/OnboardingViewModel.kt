package com.vibepad.keyboard.pairing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vibepad.keyboard.VibePadApplication
import com.vibepad.keyboard.hid.HostDevice
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.macro.AssetProfileSource
import com.vibepad.keyboard.macro.SelectionsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the 5-step onboarding wizard.
 *
 * Design choices that matter:
 *  - All environment reads (permissions / BT enabled / bonded hosts / battery
 *    exemption) are re-checked whenever the UI calls [refreshEnvironment]. The UI
 *    calls it on `onResume` so returning from a system settings screen instantly
 *    reflects the new state.
 *  - The pairing step polls bonded devices every [POLL_INTERVAL_MS] so the user
 *    doesn't have to press a "refresh" button — they pair the device in Settings
 *    and swipe back; the wizard picks it up on the next tick.
 */
class OnboardingViewModel(
    private val env: BluetoothEnvironment,
    private val permissionsGranted: () -> Boolean,
    private val selectionsStore: SelectionsStore,
    private val completionStore: OnboardingCompletionStore,
    private val profileSource: AssetProfileSource,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private var bondedPollingJob: Job? = null

    init {
        // Smart-skip: refresh env first, then land on the first incomplete step
        // based on the current environment (or INTRO for a truly fresh user).
        // We do NOT persist any "last step" — see OnboardingState.resumeStep().
        refreshEnvironment()
        _state.update { it.copy(step = it.resumeStep()) }
        viewModelScope.launch { loadProfilesAndSelection() }
    }

    /**
     * Loads bundled profiles from assets and seeds `selectedProfileId` from
     * `SelectionsStore`. If the user already has a persisted profile id we
     * treat the PROFILE step as pre-completed (they onboarded once, and the
     * upgrade path that introduced this step shouldn't ask them again). If
     * the store is empty we keep the cold-start default and leave
     * `profileStepCompleted = false` so `firstIncompleteStep` routes through
     * the new PROFILE step.
     */
    private suspend fun loadProfilesAndSelection() {
        val outcome = runCatching { profileSource.loadAll() }.getOrNull()
        val loaded = outcome?.profiles.orEmpty()
        val persistedId = selectionsStore.profileIdFlow().first()
        _state.update { current ->
            val resolvedId = persistedId
                ?: current.selectedProfileId
                ?: OnboardingState.DEFAULT_PROFILE_ID
            current.copy(
                profiles = loaded,
                selectedProfileId = resolvedId,
                profileStepCompleted = persistedId != null,
            )
        }
    }

    fun refreshEnvironment() {
        _state.update {
            it.copy(
                permissionsGranted = permissionsGranted(),
                bluetoothEnabled = env.isBluetoothEnabled(),
                bondedHosts = env.bondedHosts(),
                batteryOptimizationExempt = env.isIgnoringBatteryOptimizations(),
            )
        }
    }

    fun advance() {
        _state.update { it.copy(step = it.step.next()) }
    }

    fun retreat() {
        _state.update { it.copy(step = it.step.previous()) }
    }

    fun goToStep(step: OnboardingStep) {
        _state.update { it.copy(step = step) }
    }

    fun selectHost(host: HostDevice) {
        _state.update { it.copy(selectedHost = host) }
    }

    fun selectHostTarget(target: HostTarget) {
        _state.update { it.copy(selectedHostTarget = target) }
    }

    /**
     * User picked a profile in the PROFILE step. Only mutates local state —
     * persistence happens in [finish], so backing out of onboarding before
     * pressing Finish doesn't silently rewrite the user's stored selection.
     */
    fun selectProfile(profileId: String) {
        _state.update { it.copy(selectedProfileId = profileId) }
    }

    /** Called when the UI enters the pairing step. */
    fun startBondedPolling() {
        if (bondedPollingJob?.isActive == true) return
        bondedPollingJob = viewModelScope.launch {
            while (true) {
                _state.update {
                    val nowBonded = env.bondedHosts()
                    // Auto-pick: if the user just finished pairing a new host in Settings
                    // and we didn't have any selection yet, select the newest one.
                    val nextSelected = it.selectedHost
                        ?: nowBonded.firstOrNull { h -> h !in it.bondedHosts }
                    it.copy(bondedHosts = nowBonded, selectedHost = nextSelected)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopBondedPolling() {
        bondedPollingJob?.cancel()
        bondedPollingJob = null
    }

    /** Persist the choices made in the wizard and mark onboarding complete. */
    fun finish() {
        val s = _state.value
        val host = s.selectedHost ?: return
        val target = s.selectedHostTarget ?: return
        val profileId = s.selectedProfileId ?: OnboardingState.DEFAULT_PROFILE_ID
        viewModelScope.launch {
            selectionsStore.setHostTarget(host.address, target)
            selectionsStore.setProfileId(profileId)
            completionStore.markComplete()
            _state.update {
                it.copy(step = OnboardingStep.DONE, profileStepCompleted = true)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        bondedPollingJob?.cancel()
    }

    companion object {
        internal const val POLL_INTERVAL_MS: Long = 1_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val app = appContext as VibePadApplication
                OnboardingViewModel(
                    env = AndroidBluetoothEnvironment(appContext),
                    permissionsGranted = { PermissionHelper.allGranted(appContext) },
                    selectionsStore = app.selectionsStore,
                    completionStore = app.completionStore,
                    profileSource = app.profileSource,
                )
            }
        }
    }
}
