package com.vibepad.keyboard.pairing

import com.vibepad.keyboard.hid.HostDevice
import com.vibepad.keyboard.input.HostTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {

    private val host = HostDevice(name = "MacBook Pro", address = "AA:BB:CC:DD:EE:FF")

    @Test
    fun fresh_state_is_incomplete_and_first_incomplete_is_permissions() {
        val s = OnboardingState()
        assertFalse(s.isComplete)
        assertEquals(OnboardingStep.PERMISSIONS, s.firstIncompleteStep())
    }

    @Test
    fun intro_always_allows_advance() {
        val s = OnboardingState(step = OnboardingStep.INTRO)
        assertTrue(s.canAdvance)
    }

    @Test
    fun permissions_step_requires_permissions_granted() {
        val off = OnboardingState(step = OnboardingStep.PERMISSIONS, permissionsGranted = false)
        val on = OnboardingState(step = OnboardingStep.PERMISSIONS, permissionsGranted = true)
        assertFalse(off.canAdvance)
        assertTrue(on.canAdvance)
    }

    @Test
    fun pairing_step_requires_a_selected_host() {
        val nothing = OnboardingState(
            step = OnboardingStep.PAIRING,
            permissionsGranted = true,
            bluetoothEnabled = true,
        )
        val selected = nothing.copy(selectedHost = host)
        assertFalse(nothing.canAdvance)
        assertTrue(selected.canAdvance)
    }

    @Test
    fun host_target_step_requires_selection_and_host() {
        val hostOnly = OnboardingState(
            step = OnboardingStep.HOST_TARGET,
            permissionsGranted = true,
            bluetoothEnabled = true,
            selectedHost = host,
        )
        val both = hostOnly.copy(selectedHostTarget = HostTarget.MACOS)
        assertFalse(hostOnly.canAdvance)
        assertTrue(both.canAdvance)
    }

    @Test
    fun all_complete_means_is_complete_is_true() {
        val s = OnboardingState(
            step = OnboardingStep.DONE,
            permissionsGranted = true,
            bluetoothEnabled = true,
            selectedHost = host,
            selectedHostTarget = HostTarget.WINDOWS,
        )
        assertTrue(s.isComplete)
        assertEquals(OnboardingStep.DONE, s.firstIncompleteStep())
    }

    @Test
    fun first_incomplete_skips_already_satisfied_steps() {
        val s = OnboardingState(
            permissionsGranted = true,
            bluetoothEnabled = false,
        )
        assertEquals(OnboardingStep.BLUETOOTH, s.firstIncompleteStep())
    }

    // --- Smart-skip resume behavior -----------------------------------------

    @Test
    fun resumeStep_freshEnv_landsOnIntro() {
        val fresh = OnboardingState()
        assertEquals(OnboardingStep.INTRO, fresh.resumeStep())
    }

    @Test
    fun resumeStep_withPermissionsButBluetoothOff_landsOnBluetooth() {
        val s = OnboardingState(permissionsGranted = true)
        assertEquals(OnboardingStep.BLUETOOTH, s.resumeStep())
    }

    @Test
    fun resumeStep_allPairedNoTarget_landsOnHostTarget() {
        val s = OnboardingState(
            permissionsGranted = true,
            bluetoothEnabled = true,
            selectedHost = host,
        )
        assertEquals(OnboardingStep.HOST_TARGET, s.resumeStep())
    }

    @Test
    fun resumeStep_neverPersistsAStep_recomputesFromEnvironment() {
        // Same fully-complete env should always produce DONE, regardless of the
        // OnboardingState.step value the caller happened to stash. This proves
        // we're reading env, not a stored resume index.
        val complete = OnboardingState(
            step = OnboardingStep.INTRO,
            permissionsGranted = true,
            bluetoothEnabled = true,
            selectedHost = host,
            selectedHostTarget = HostTarget.MACOS,
        )
        assertEquals(OnboardingStep.DONE, complete.resumeStep())
    }
}
