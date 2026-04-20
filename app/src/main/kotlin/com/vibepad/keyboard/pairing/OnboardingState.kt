package com.vibepad.keyboard.pairing

import com.vibepad.keyboard.hid.HostDevice
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.macro.Profile

/**
 * View state for the onboarding screen. Pure data — safe to snapshot-test and to
 * feed into `@Preview` composables.
 *
 * `selectedProfileId` is seeded with `"profile.claude-code"` at construction
 * time so the PROFILE step's primary button is immediately enabled — users who
 * only want Claude see a one-tap Finish. `profileStepCompleted` flips to `true`
 * after [OnboardingViewModel.finish] writes the id to `SelectionsStore`; that
 * tell is what `firstIncompleteStep` uses to distinguish "default not yet
 * confirmed" from "user has reviewed profile selection".
 */
data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.INTRO,
    val permissionsGranted: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val bondedHosts: List<HostDevice> = emptyList(),
    val selectedHost: HostDevice? = null,
    val selectedHostTarget: HostTarget? = null,
    val batteryOptimizationExempt: Boolean = false,
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = DEFAULT_PROFILE_ID,
    val profileStepCompleted: Boolean = false,
) {

    /** Can the user advance past [step]? Drives the enabled state of the Next button. */
    val canAdvance: Boolean get() = when (step) {
        OnboardingStep.INTRO -> true
        OnboardingStep.PERMISSIONS -> permissionsGranted
        OnboardingStep.BLUETOOTH -> bluetoothEnabled
        OnboardingStep.PAIRING -> selectedHost != null
        OnboardingStep.HOST_TARGET -> selectedHostTarget != null && selectedHost != null
        OnboardingStep.PROFILE -> selectedProfileId != null
        OnboardingStep.DONE -> false
    }

    /** The first step whose preconditions haven't been satisfied. */
    fun firstIncompleteStep(): OnboardingStep {
        if (!permissionsGranted) return OnboardingStep.PERMISSIONS
        if (!bluetoothEnabled) return OnboardingStep.BLUETOOTH
        if (selectedHost == null) return OnboardingStep.PAIRING
        if (selectedHostTarget == null) return OnboardingStep.HOST_TARGET
        if (!profileStepCompleted) return OnboardingStep.PROFILE
        return OnboardingStep.DONE
    }

    /**
     * Smart-skip entry step: purely a function of the current environment, no
     * persisted "last step" field. Any prior progress (permission granted,
     * Bluetooth already on, a paired host, or a host target already picked)
     * counts as "returning user" and bypasses the INTRO brand screen. Truly
     * fresh installs fall through to INTRO so the brand moment still lands.
     */
    fun resumeStep(): OnboardingStep {
        val hasAnyProgress = permissionsGranted ||
            bluetoothEnabled ||
            selectedHost != null ||
            selectedHostTarget != null ||
            profileStepCompleted
        return if (hasAnyProgress) firstIncompleteStep() else OnboardingStep.INTRO
    }

    val isComplete: Boolean get() = firstIncompleteStep() == OnboardingStep.DONE

    companion object {
        /**
         * Cold-start default for [selectedProfileId]. Mirrors
         * `com.vibepad.keyboard.ui.DEFAULT_PROFILE_ID` — kept duplicated here
         * so the pairing module doesn't reach into `ui` just for a constant.
         */
        const val DEFAULT_PROFILE_ID = "profile.claude-code"
    }
}
