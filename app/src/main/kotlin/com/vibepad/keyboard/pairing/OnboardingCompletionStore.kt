package com.vibepad.keyboard.pairing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore: DataStore<Preferences> by preferencesDataStore(name = "onboarding")

/**
 * Tracks two persistent booleans:
 *  - `onboarding_complete`: the user has finished the 5-step wizard at least once.
 *  - `battery_opt_dismissed_at`: the last time the user declined the battery
 *    optimization prompt. We honor a 30-day cool-down before asking again.
 */
class OnboardingCompletionStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.onboardingDataStore)

    fun isCompleteFlow(): Flow<Boolean> =
        dataStore.data.map { it[KEY_COMPLETE] ?: false }

    suspend fun markComplete() {
        dataStore.edit { it[KEY_COMPLETE] = true }
    }

    suspend fun reset() {
        dataStore.edit { it[KEY_COMPLETE] = false }
    }

    fun batteryDismissedAtFlow(): Flow<Long?> =
        dataStore.data.map { it[KEY_BATTERY_DISMISSED_AT] }

    suspend fun markBatteryDismissed(nowMs: Long) {
        dataStore.edit { it[KEY_BATTERY_DISMISSED_AT] = nowMs }
    }

    companion object {
        const val BATTERY_COOLDOWN_MS: Long = 30L * 24 * 60 * 60 * 1000

        private val KEY_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val KEY_BATTERY_DISMISSED_AT = longPreferencesKey("battery_dismissed_at")
    }
}
