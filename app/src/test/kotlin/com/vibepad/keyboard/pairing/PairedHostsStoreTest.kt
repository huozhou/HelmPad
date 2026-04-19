package com.vibepad.keyboard.pairing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for [PairedHostsStore]. Uses a real Preferences DataStore backed
 * by a temporary file — the API is small enough that mocking DataStore would
 * just re-implement it.
 */
class PairedHostsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope
    private lateinit var ds: DataStore<Preferences>
    private lateinit var store: PairedHostsStore

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file: File = tempFolder.newFile("paired_hosts_test.preferences_pb")
        // The factory insists the file not exist — delete the freshly-created one
        // the JUnit rule handed us so the library can re-create it.
        file.delete()
        ds = PreferenceDataStoreFactory.create(scope = scope) { file }
        store = PairedHostsStore(ds)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `initial flow emits empty list`() = runBlocking {
        val first = store.flow().first()
        assertTrue(first.isEmpty())
    }

    @Test
    fun `recordConnection appends a new row`() = runBlocking {
        store.recordConnection(mac = "AA:BB:CC:11:22:33", systemName = "Owner-mac", nowMs = 1_000L)

        val rows = store.flow().first()
        assertEquals(1, rows.size)
        assertEquals("AA:BB:CC:11:22:33", rows.single().mac)
        assertEquals("Owner-mac", rows.single().systemName)
        assertEquals(1_000L, rows.single().lastSeenAt)
        assertNull(rows.single().alias)
    }

    @Test
    fun `recordConnection on existing MAC updates name and seenAt without duplicating`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "Owner-mac", 1_000L)
        store.recordConnection("AA:BB:CC:11:22:33", "Owner-mac-renamed", 5_000L)

        val rows = store.flow().first()
        assertEquals(1, rows.size)
        assertEquals("Owner-mac-renamed", rows.single().systemName)
        assertEquals(5_000L, rows.single().lastSeenAt)
    }

    @Test
    fun `MAC lookup is case-insensitive`() = runBlocking {
        store.recordConnection("aa:bb:cc:11:22:33", "Owner-mac", 1_000L)
        // Different case for the exact same MAC must coalesce.
        store.recordConnection("AA:BB:CC:11:22:33", "Owner-mac", 2_000L)

        val rows = store.flow().first()
        assertEquals(1, rows.size)
        assertEquals(2_000L, rows.single().lastSeenAt)
    }

    @Test
    fun `setAlias writes and displayName uses alias plus systemName`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "Owner-mac", 1_000L)
        store.setAlias("aa:bb:cc:11:22:33", "  Work Laptop  ")

        val row = store.flow().first().single()
        assertEquals("Work Laptop", row.alias)
        assertEquals("Work Laptop · Owner-mac", row.displayName)
    }

    @Test
    fun `setAlias with blank clears the alias`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "Owner-mac", 1_000L)
        store.setAlias("AA:BB:CC:11:22:33", "Work Laptop")
        store.setAlias("AA:BB:CC:11:22:33", "   ")

        val row = store.flow().first().single()
        assertNull(row.alias)
        assertEquals("Owner-mac", row.displayName)
    }

    @Test
    fun `remove drops both the record and the alias`() = runBlocking {
        store.recordConnection("AA:BB:CC:11:22:33", "Owner-mac", 1_000L)
        store.setAlias("AA:BB:CC:11:22:33", "Work")
        store.recordConnection("DD:EE:FF:44:55:66", "Home-pc", 2_000L)

        store.remove("aa:bb:cc:11:22:33")

        val rows = store.flow().first()
        assertEquals(1, rows.size)
        assertEquals("DD:EE:FF:44:55:66", rows.single().mac)

        // Re-inserting the removed MAC must NOT resurrect the old alias.
        store.recordConnection("AA:BB:CC:11:22:33", "Owner-mac", 3_000L)
        val after = store.flow().first().first { it.mac == "AA:BB:CC:11:22:33" }
        assertNull(after.alias)
    }

    @Test
    fun `flow emits most-recent-first ordering`() = runBlocking {
        store.recordConnection("AA:AA:AA:AA:AA:AA", "A", 100L)
        store.recordConnection("BB:BB:BB:BB:BB:BB", "B", 300L)
        store.recordConnection("CC:CC:CC:CC:CC:CC", "C", 200L)

        val macs = store.flow().first().map { it.mac }
        assertEquals(listOf("BB:BB:BB:BB:BB:BB", "CC:CC:CC:CC:CC:CC", "AA:AA:AA:AA:AA:AA"), macs)
    }
}
