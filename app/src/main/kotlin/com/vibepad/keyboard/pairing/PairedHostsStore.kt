package com.vibepad.keyboard.pairing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vibepad.keyboard.input.HostTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.pairedHostsDataStore: DataStore<Preferences> by preferencesDataStore(name = "paired_hosts")

/**
 * App-local ledger of the Bluetooth hosts Helm Pad has seen a successful HID
 * connection to, indexed by MAC address.
 *
 * Why a private ledger and not `BluetoothAdapter.bondedDevices`:
 *   - `bondedDevices` returns every BT peer the phone has ever paired with —
 *     headphones, car kits, watches. None of those are Helm Pad hosts and
 *     offering "Forget" on them would confuse users.
 *   - We only add a MAC here on a real HID `Connected` transition, so the list
 *     is by construction "hosts Helm Pad has talked to as a keyboard".
 *
 * Persistence shape — all kept in a single DataStore Preferences instance so a
 * single atomic `edit { }` updates all three tables:
 *
 *  | key                | type          | purpose                               |
 *  |--------------------|---------------|---------------------------------------|
 *  | `hosts_json`       | String (JSON) | list of [Record] (mac, name, seenMs)  |
 *  | `aliases_json`     | String (JSON) | map<mac, user-chosen alias>           |
 *
 * The JSON blobs are small (a handful of rows) so the read cost is negligible
 * compared to reaching for a relational store. All writes happen on IO.
 *
 * A MAC is the primary key. `systemName` is the best-known name at the time of
 * the most recent connection; if the host is later renamed on the OS side we'll
 * refresh it on the next `recordConnection` call.
 */
class PairedHostsStore(private val dataStore: DataStore<Preferences>) {

    constructor(context: Context) : this(context.applicationContext.pairedHostsDataStore)

    /**
     * Emits the full list of recorded hosts on every change, newest first.
     * Aliases and the per-host OS verdict are merged in so downstream UIs
     * don't need to cross-reference three flows.
     */
    fun flow(): Flow<List<RecordedHost>> = dataStore.data.map { prefs ->
        val records = prefs.readRecords()
        val aliases = prefs.readAliases()
        val osMap = prefs.readHostOs()
        records
            .sortedByDescending { it.lastSeenAt }
            .map { rec ->
                RecordedHost(
                    mac = rec.mac,
                    systemName = rec.systemName,
                    alias = aliases[rec.mac],
                    lastSeenAt = rec.lastSeenAt,
                    hostOs = osMap[rec.mac] ?: HostOsRecord.EMPTY,
                )
            }
    }

    /**
     * Returns the currently-known [HostOsRecord] for a MAC, or
     * [HostOsRecord.EMPTY] if we've never inspected nor heard a manual
     * override for it. Used by [HidForegroundService] to decide whether to
     * (re-)run the inspector on the next connect.
     */
    suspend fun hostOs(mac: String): HostOsRecord {
        val normalized = mac.uppercase()
        return dataStore.data.first().readHostOs()[normalized] ?: HostOsRecord.EMPTY
    }

    /**
     * Resolves the OS Helm Pad should treat the given MAC as. Order:
     *   1. user override (highest, never overwritten by detector)
     *   2. detector verdict (any confidence — UI surfaces low-confidence ones
     *      separately so the user can confirm)
     *   3. null — caller falls back to its own default
     *
     * Suspending because we read the latest DataStore snapshot. Callers that
     * need a continuous stream should `combine` with [flow] instead.
     */
    suspend fun effectiveHostTarget(mac: String): HostTarget? {
        val rec = hostOs(mac)
        return rec.userOverrideHostTarget ?: rec.detectedHostTarget
    }


    /**
     * Upsert: if `mac` already exists, updates `systemName` and `lastSeenAt`;
     * otherwise appends a new row. Idempotent on a single connection event.
     */
    suspend fun recordConnection(mac: String, systemName: String, nowMs: Long) {
        val normalized = mac.uppercase()
        dataStore.edit { prefs ->
            val existing = prefs.readRecords().toMutableList()
            val idx = existing.indexOfFirst { it.mac == normalized }
            if (idx >= 0) {
                existing[idx] = existing[idx].copy(systemName = systemName, lastSeenAt = nowMs)
            } else {
                existing += Record(mac = normalized, systemName = systemName, lastSeenAt = nowMs)
            }
            prefs[KEY_HOSTS] = Json.encodeToString(recordsSerializer, existing)
        }
    }

    /**
     * Removes the MAC from every table (records, aliases, hostOs). Call when
     * the user explicitly Forgets a host so the row disappears completely on
     * the next reconnect.
     */
    suspend fun remove(mac: String) {
        val normalized = mac.uppercase()
        dataStore.edit { prefs ->
            val records = prefs.readRecords().filterNot { it.mac == normalized }
            prefs[KEY_HOSTS] = Json.encodeToString(recordsSerializer, records)
            val aliases = prefs.readAliases().toMutableMap()
            aliases.remove(normalized)
            prefs[KEY_ALIASES] = Json.encodeToString(aliasesSerializer, aliases)
            val osMap = prefs.readHostOs().toMutableMap()
            osMap.remove(normalized)
            prefs[KEY_HOST_OS] = Json.encodeToString(hostOsSerializer, osMap)
        }
    }

    /**
     * Persists what the inspector concluded about a host. Always overwrites
     * the previous detector verdict — if the user wants their old verdict
     * preserved, that's what the *override* field is for.
     */
    suspend fun recordDetection(mac: String, guess: HostGuess) {
        updateHostOs(mac) { existing ->
            existing.copy(
                detectedHostTarget = guess.target,
                detectedConfidence = guess.confidence,
                detectedSource = guess.source,
            )
        }
    }

    /**
     * Sets — or, with a null target, clears — the user's manual override for
     * a MAC's OS. Clearing falls back to the detector verdict on the next
     * read.
     */
    suspend fun recordOverride(mac: String, override: HostTarget?) {
        updateHostOs(mac) { it.copy(userOverrideHostTarget = override) }
    }

    /**
     * Wipes the detector verdict for a MAC (keeps the user override if any),
     * so the next connect re-runs the inspector. Powers the "Re-detect"
     * action in Paired Hosts.
     */
    suspend fun clearDetection(mac: String) {
        updateHostOs(mac) {
            it.copy(
                detectedHostTarget = null,
                detectedConfidence = Confidence.NONE,
                detectedSource = Source.NONE,
            )
        }
    }

    private suspend fun updateHostOs(mac: String, transform: (HostOsRecord) -> HostOsRecord) {
        val normalized = mac.uppercase()
        dataStore.edit { prefs ->
            val current = prefs.readHostOs().toMutableMap()
            val before = current[normalized] ?: HostOsRecord.EMPTY
            val after = transform(before)
            if (after.isEmpty()) current.remove(normalized) else current[normalized] = after
            prefs[KEY_HOST_OS] = Json.encodeToString(hostOsSerializer, current)
        }
    }

    /**
     * Sets — or, with a blank value, clears — the user's local alias for a MAC.
     * Blank is treated as "remove alias" so the UI "reset to default" flow is
     * just "clear the text field and hit save".
     */
    suspend fun setAlias(mac: String, alias: String) {
        val normalized = mac.uppercase()
        dataStore.edit { prefs ->
            val aliases = prefs.readAliases().toMutableMap()
            val trimmed = alias.trim()
            if (trimmed.isEmpty()) aliases.remove(normalized) else aliases[normalized] = trimmed
            prefs[KEY_ALIASES] = Json.encodeToString(aliasesSerializer, aliases)
        }
    }

    private fun Preferences.readRecords(): List<Record> =
        this[KEY_HOSTS]?.let { runCatching { Json.decodeFromString(recordsSerializer, it) }.getOrDefault(emptyList()) }
            ?: emptyList()

    private fun Preferences.readAliases(): Map<String, String> =
        this[KEY_ALIASES]?.let { runCatching { Json.decodeFromString(aliasesSerializer, it) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    private fun Preferences.readHostOs(): Map<String, HostOsRecord> =
        this[KEY_HOST_OS]?.let { runCatching { Json.decodeFromString(hostOsSerializer, it) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    @Serializable
    internal data class Record(
        val mac: String,
        val systemName: String,
        val lastSeenAt: Long,
    )

    companion object {
        private val KEY_HOSTS = stringPreferencesKey("hosts_json")
        private val KEY_ALIASES = stringPreferencesKey("aliases_json")
        private val KEY_HOST_OS = stringPreferencesKey("host_os_json")
        private val recordsSerializer: KSerializer<List<Record>> = ListSerializer(Record.serializer())
        private val aliasesSerializer: KSerializer<Map<String, String>> =
            MapSerializer(String.serializer(), String.serializer())
        private val hostOsSerializer: KSerializer<Map<String, HostOsRecord>> =
            MapSerializer(String.serializer(), HostOsRecord.serializer())
    }
}

/**
 * Per-host OS verdict, stored as a separate JSON blob keyed by MAC so adding
 * the field doesn't disturb the existing `hosts_json` schema (older builds
 * just see an extra key they don't read).
 *
 * Two verdicts coexist:
 *  - [detectedHostTarget] / [detectedConfidence] / [detectedSource]: written
 *    by [BtHostInspector] on connect. Refreshed by Paired Hosts → "Re-detect".
 *  - [userOverrideHostTarget]: the user's manual choice from the picker
 *    sheet. Wins over the detector when both are present, and survives
 *    Re-detect.
 */
@Serializable
data class HostOsRecord(
    val detectedHostTarget: HostTarget? = null,
    val detectedConfidence: Confidence = Confidence.NONE,
    val detectedSource: Source = Source.NONE,
    val userOverrideHostTarget: HostTarget? = null,
) {
    /** True when no field carries information — used to garbage-collect map entries. */
    fun isEmpty(): Boolean =
        detectedHostTarget == null &&
            detectedConfidence == Confidence.NONE &&
            detectedSource == Source.NONE &&
            userOverrideHostTarget == null

    companion object {
        val EMPTY = HostOsRecord()
    }
}

/**
 * One row shown in `PairedHostsScreen`. [alias] is the user-chosen local name
 * (never the OS-reported name); [systemName] is what the OS told us last time
 * we connected.
 */
data class RecordedHost(
    val mac: String,
    val systemName: String,
    val alias: String?,
    val lastSeenAt: Long,
    /**
     * Per-host OS verdict, defaulting to [HostOsRecord.EMPTY] when the
     * inspector hasn't run yet. Always non-null so screens can render
     * "Unknown — needs review" without a separate null check.
     */
    val hostOs: HostOsRecord = HostOsRecord.EMPTY,
) {
    /** Format used in the list row: "{alias} · {systemName}" when alias is set. */
    val displayName: String get() = if (!alias.isNullOrBlank()) "$alias · $systemName" else systemName

    /**
     * The OS this row's HID frames will use. User override wins over the
     * detector verdict; if neither is set, returns null so the caller can
     * decide on a fallback (typically `MACOS`).
     */
    val effectiveHostTarget: HostTarget?
        get() = hostOs.userOverrideHostTarget ?: hostOs.detectedHostTarget
}
