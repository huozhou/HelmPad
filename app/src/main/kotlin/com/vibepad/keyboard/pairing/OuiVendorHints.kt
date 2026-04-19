package com.vibepad.keyboard.pairing

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.vibepad.keyboard.input.HostTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Bundled lookup table from MAC OUI prefix (first 3 bytes, formatted as
 * `AA:BB:CC`) to the [HostTarget] we expect that vendor's machines to ship as.
 *
 * Why bundled and not a network call:
 *  - The phone may be off-network the first time it sees a host, and the
 *    inspector wants to make a verdict synchronously inside the
 *    HID-foreground-service tick.
 *  - The full IEEE OUI registry is huge (>50k entries, several MB) and
 *    hostile to ship in an APK. We only care about the dozen vendors
 *    whose machines users actually pair as Helm Pad hosts.
 *
 * Maintenance contract:
 *  - The asset is reviewed once per release (see
 *    `docs/release-notes/host-os-autodetect.md`).
 *  - Vendors only enter the table if they almost exclusively ship Macs or
 *    Windows machines under the prefix in question. Mixed vendors (e.g.
 *    Intel modules, Realtek chipsets) are deliberately excluded — false
 *    positives are worse than no signal.
 *  - Apple OUIs map to MACOS but produce only `Confidence.LOW`, because
 *    iPads / iPhones share Apple's OUI pool. The picker sheet lets the user
 *    correct this.
 */
class OuiVendorHints internal constructor(
    private val assets: AssetManager?,
    private val assetPath: String = "oui-vendor-hints.json",
) {

    constructor(context: Context) : this(context.applicationContext.assets)

    @Volatile
    private var table: Map<String, HostTarget> = emptyMap()

    /**
     * Populates the in-memory table. Safe to call multiple times — the second
     * call simply re-reads the asset and atomically swaps the table reference,
     * which means callers concurrently issuing [lookup] always see one
     * consistent map.
     *
     * Failures (asset missing, JSON malformed) are logged and leave the table
     * unchanged. The inspector degrades gracefully: it still uses the name
     * regexes and produces NONE for OUI-only matches.
     */
    suspend fun load() {
        val source = assets ?: return
        loadFromBytes(
            read = { source.open(assetPath).readBytes() },
            label = assetPath,
        )
    }

    /**
     * Test seam — load from any byte source the caller can produce. Production
     * uses the assets path; tests can hand in a string-backed lambda to avoid
     * standing up an AssetManager.
     */
    internal suspend fun loadFromBytes(read: () -> ByteArray, label: String) {
        val parsed = withContext(Dispatchers.IO) {
            try {
                val raw = String(read())
                val entries = JSON.decodeFromString<List<OuiEntry>>(raw)
                entries.associate { entry ->
                    entry.prefix.uppercase() to HostTarget.valueOf(entry.target)
                }
            } catch (io: IOException) {
                safeLogW(TAG, "asset $label missing or unreadable: ${io.message}")
                null
            } catch (t: Throwable) {
                safeLogW(TAG, "failed to parse $label: ${t.message}")
                null
            }
        }
        if (parsed != null) {
            table = parsed
            safeLogI(TAG, "loaded ${parsed.size} OUI entries from $label")
        }
    }

    // android.util.Log routes through native code that's stubbed out in JVM
    // unit tests (UnsatisfiedLinkError on println_native). loadFromBytes is
    // exercised directly by OuiVendorHintsTest, so the catch keeps tests
    // independent of Robolectric while still emitting real logs on-device.
    private fun safeLogI(tag: String, msg: String) {
        try { Log.i(tag, msg) } catch (_: Throwable) { /* unit-test JVM */ }
    }

    private fun safeLogW(tag: String, msg: String) {
        try { Log.w(tag, msg) } catch (_: Throwable) { /* unit-test JVM */ }
    }

    /**
     * Returns the OS Helm Pad expects for the given MAC, or null if the
     * prefix isn't in the table.
     *
     * @param prefix first 8 chars of a MAC (`AA:BB:CC`). Case-insensitive.
     */
    fun lookup(prefix: String): HostTarget? {
        val key = prefix.uppercase()
        return table[key]
    }

    @Serializable
    internal data class OuiEntry(
        val prefix: String,
        val vendor: String,
        val target: String,
    )

    companion object {
        private const val TAG = "OuiVendorHints"
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * Returns a preloaded instance bypassing asset I/O. Test-only: lets
         * unit tests seed deterministic OUI data without standing up an
         * AssetManager double.
         */
        internal fun fakeWith(entries: Map<String, HostTarget>): OuiVendorHints {
            val instance = OuiVendorHints(assets = null)
            instance.table = entries.mapKeys { it.key.uppercase() }
            return instance
        }

        /** Empty preloaded instance — equivalent to a load() that found zero rows. */
        internal fun fakeEmpty(): OuiVendorHints = fakeWith(emptyMap())
    }
}
