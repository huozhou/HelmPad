package com.vibepad.keyboard.pairing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vibepad.keyboard.hid.HostDevice
import com.vibepad.keyboard.input.HostTarget
import com.vibepad.keyboard.macro.AssetProfileSource
import com.vibepad.keyboard.macro.SelectionsStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises the onboarding viewmodel's PROFILE-step contract end-to-end:
 *
 *  - `selectProfile` mutates local state only (persistence happens in
 *    [OnboardingViewModel.finish]), so backing out of onboarding never
 *    silently rewrites `SelectionsStore`.
 *  - `finish` persists the picked profile id to `SelectionsStore` and flips
 *    `profileStepCompleted = true`.
 *  - When the store already has a persisted profile id (upgrade path), the
 *    initial state comes back with that id preselected *and*
 *    `profileStepCompleted = true` so the wizard doesn't rehash the step.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var ds: DataStore<Preferences>
    private lateinit var completionDs: DataStore<Preferences>
    private lateinit var selections: SelectionsStore
    private lateinit var completion: OnboardingCompletionStore
    private lateinit var env: BluetoothEnvironment
    private lateinit var profileSource: AssetProfileSource

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val selFile: File = tempFolder.newFile("onboarding_sel.preferences_pb").also { it.delete() }
        ds = PreferenceDataStoreFactory.create(scope = scope) { selFile }
        selections = SelectionsStore(ds)
        val cFile: File = tempFolder.newFile("onboarding_complete.preferences_pb").also { it.delete() }
        completionDs = PreferenceDataStoreFactory.create(scope = scope) { cFile }
        completion = OnboardingCompletionStore(completionDs)
        env = mockk(relaxed = true) {
            every { isBluetoothEnabled() } returns true
            every { bondedHosts() } returns emptyList()
            every { isIgnoringBatteryOptimizations() } returns true
        }
        // AssetProfileSource.loadAll returns an empty outcome by default — the
        // PROFILE step tests don't depend on the actual profile list because
        // `selectProfile` takes any String id; we assert state, not UI rows.
        profileSource = mockk {
            every { loadAll() } returns AssetProfileSource.ScanOutcome(
                profiles = emptyList(),
                diagnostics = emptyList(),
            )
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        Dispatchers.resetMain()
    }

    @Test
    fun selectProfile_updates_state_without_writing_store() = runBlocking {
        val vm = buildVm()
        // Wait for the init-time loadProfilesAndSelection to settle.
        vm.state.first { it.profiles.isEmpty() && it.selectedProfileId != null }

        vm.selectProfile("profile.codex")
        assertEquals("profile.codex", vm.state.value.selectedProfileId)

        // selectProfile is a pure state mutation — the store must still be
        // null until finish() runs, so a user backing out of onboarding
        // doesn't silently rewrite their persisted selection.
        val persisted = selections.profileIdFlow().first()
        assertTrue(
            "selectProfile must not persist; store still had $persisted",
            persisted == null,
        )
    }

    @Test
    fun finish_persists_selected_profile_id_to_selections_store() = runBlocking {
        val vm = buildVm()
        vm.state.first { it.selectedProfileId != null }

        val host = HostDevice(name = "Laptop", address = "AA:BB:CC:DD:EE:FF")
        vm.selectHost(host)
        vm.selectHostTarget(HostTarget.MACOS)
        vm.selectProfile("profile.cursor")

        vm.finish()

        // finish() launches on viewModelScope but suspends at DataStore
        // writes (which run on Dispatchers.IO). Wait for the state to
        // transition to DONE, which only happens after the writes settle —
        // any failure path leaves profileStepCompleted=false or step!=DONE.
        val doneState = vm.state.first {
            it.step == OnboardingStep.DONE && it.profileStepCompleted
        }

        val persisted = selections.profileIdFlow().first()
        assertEquals("profile.cursor", persisted)
        assertEquals("profile.cursor", doneState.selectedProfileId)
    }

    @Test
    fun upgraded_user_with_persisted_profile_sees_step_already_completed() = runBlocking {
        // Simulate a user who completed onboarding on an earlier build: their
        // profile id is already in the store, so the new PROFILE step should
        // be skipped on the upgrade — no extra tap required.
        selections.setProfileId("profile.codex")

        val vm = buildVm()
        // Wait for init's async loadProfilesAndSelection to flow the
        // persisted id into state. `state.first {…}` times out via the
        // enclosing runBlocking if the VM never gets there, which is the
        // failure we want.
        val state = vm.state.first { it.profileStepCompleted }

        assertEquals("profile.codex", state.selectedProfileId)
    }

    private fun buildVm(): OnboardingViewModel = OnboardingViewModel(
        env = env,
        permissionsGranted = { true },
        selectionsStore = selections,
        completionStore = completion,
        profileSource = profileSource,
    )
}
