package com.vibepad.keyboard.macro

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibepad.keyboard.input.HostTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.selectionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "selections")

/**
 * User selections persisted across process death via [DataStore] Preferences.
 *
 * Two pieces of state:
 *  - The currently active [Profile.id]. Users pick from the bundled profiles
 *    (Claude Code, Codex, Cursor) via onboarding or Settings; a missing or
 *    unknown value is treated as the Claude Code default by
 *    `resolveActiveProfile` at read time rather than forced on write.
 *  - The [HostTarget] chosen per paired host (keyed by MAC address). When the same host
 *    reconnects we silently restore the user's prior choice; see spec
 *    `pairing-and-permissions` §"按主机记忆目标".
 */
class SelectionsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.selectionsDataStore)

    fun profileIdFlow(): Flow<String?> =
        dataStore.data.map { it[KEY_PROFILE_ID] }

    suspend fun setProfileId(id: String) {
        dataStore.edit { prefs -> prefs[KEY_PROFILE_ID] = id }
    }

    fun hostTargetFlow(mac: String): Flow<HostTarget?> =
        dataStore.data.map { prefs ->
            prefs[hostKey(mac)]?.let { runCatching { HostTarget.valueOf(it) }.getOrNull() }
        }

    suspend fun setHostTarget(mac: String, target: HostTarget) {
        dataStore.edit { prefs -> prefs[hostKey(mac)] = target.name }
    }

    companion object {
        private val KEY_PROFILE_ID = stringPreferencesKey("profile_id")
        private fun hostKey(mac: String) = stringPreferencesKey("host_target:${mac.lowercase()}")
    }
}
