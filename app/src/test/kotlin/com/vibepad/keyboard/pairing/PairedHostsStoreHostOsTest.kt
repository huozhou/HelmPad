package com.vibepad.keyboard.pairing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vibepad.keyboard.input.HostTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Targeted tests for the host-OS slice of [PairedHostsStore] added by
 * `host-os-autodetect`. Existing connection / alias coverage lives in
 * [PairedHostsStoreTest].
 */
class PairedHostsStoreHostOsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var ds: DataStore<Preferences>
    private lateinit var store: PairedHostsStore

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = tempFolder.newFile("paired_hosts_hostos_test.preferences_pb")
        file.delete()
        ds = PreferenceDataStoreFactory.create(scope = scope) { file }
        store = PairedHostsStore(ds)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `recordDetection writes all three detector fields atomically`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        store.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))

        val rec = store.hostOs(mac)
        assertEquals(HostTarget.MACOS, rec.detectedHostTarget)
        assertEquals(Confidence.HIGH, rec.detectedConfidence)
        assertEquals(Source.NAME, rec.detectedSource)
        assertNull(rec.userOverrideHostTarget)
    }

    @Test
    fun `effectiveHostTarget prefers user override over detector`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        store.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))
        store.recordOverride(mac, HostTarget.WINDOWS)

        assertEquals(HostTarget.WINDOWS, store.effectiveHostTarget(mac))
    }

    @Test
    fun `effectiveHostTarget falls back through override → detector → null`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)

        // No detection yet, no override.
        assertNull(store.effectiveHostTarget(mac))

        // Detection only.
        store.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))
        assertEquals(HostTarget.MACOS, store.effectiveHostTarget(mac))

        // Override wins.
        store.recordOverride(mac, HostTarget.WINDOWS)
        assertEquals(HostTarget.WINDOWS, store.effectiveHostTarget(mac))

        // Clear override → detection wins again.
        store.recordOverride(mac, null)
        assertEquals(HostTarget.MACOS, store.effectiveHostTarget(mac))
    }

    @Test
    fun `clearDetection wipes detector fields but keeps user override`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        store.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))
        store.recordOverride(mac, HostTarget.WINDOWS)

        store.clearDetection(mac)

        val rec = store.hostOs(mac)
        assertNull(rec.detectedHostTarget)
        assertEquals(Confidence.NONE, rec.detectedConfidence)
        assertEquals(Source.NONE, rec.detectedSource)
        assertEquals(HostTarget.WINDOWS, rec.userOverrideHostTarget)
        // Override still wins post-clear.
        assertEquals(HostTarget.WINDOWS, store.effectiveHostTarget(mac))
    }

    @Test
    fun `mac normalisation is case-insensitive across the host-OS APIs`() = runBlocking {
        store.recordConnection("aa:bb:cc:11:22:33", "Owner-mac", 1L)
        store.recordDetection("Aa:Bb:Cc:11:22:33", HostGuess(HostTarget.MACOS, Confidence.LOW, Source.OUI))
        store.recordOverride("AA:BB:CC:11:22:33", HostTarget.WINDOWS)

        val rec = store.hostOs("aa:bb:cc:11:22:33")
        assertEquals(HostTarget.MACOS, rec.detectedHostTarget)
        assertEquals(HostTarget.WINDOWS, rec.userOverrideHostTarget)
    }

    @Test
    fun `remove drops the host-OS slice as well`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        store.recordOverride(mac, HostTarget.WINDOWS)

        store.remove(mac)

        // After remove, the row is gone and a fresh recordConnection starts
        // with a clean slate (no resurrected override).
        store.recordConnection(mac, "Owner-mac", 2L)
        assertNull(store.effectiveHostTarget(mac))
        assertEquals(HostOsRecord.EMPTY, store.hostOs(mac))
    }

    @Test
    fun `hostOs flows out via flow() merged into RecordedHost`() = runBlocking {
        val mac = "AA:BB:CC:11:22:33"
        store.recordConnection(mac, "Owner-mac", 1L)
        store.recordDetection(mac, HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME))

        val recorded = store.flow().first().single()
        assertEquals(HostTarget.MACOS, recorded.effectiveHostTarget)
        assertEquals(Source.NAME, recorded.hostOs.detectedSource)
    }

    @Test
    fun `legacy v1 records read back with empty hostOs slice`() = runBlocking {
        // Simulate a v1-on-disk state by writing only the records and aliases
        // tables — i.e. exactly what the pre-Change-4 store would have left.
        store.recordConnection("AA:BB:CC:11:22:33", "Owner-mac", 1L)
        store.setAlias("AA:BB:CC:11:22:33", "Office")

        val row = store.flow().first().single()
        assertEquals(HostOsRecord.EMPTY, row.hostOs)
        assertNull(row.effectiveHostTarget)
    }
}
