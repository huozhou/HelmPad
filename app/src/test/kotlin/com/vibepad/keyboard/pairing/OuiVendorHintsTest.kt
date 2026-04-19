package com.vibepad.keyboard.pairing

import com.vibepad.keyboard.input.HostTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [OuiVendorHints]. Uses the [OuiVendorHints.loadFromBytes]
 * test seam so we don't need an AssetManager.
 */
class OuiVendorHintsTest {

    @Test
    fun `load parses every entry into the lookup table`() = runBlocking {
        val hints = OuiVendorHints.fakeEmpty()
        hints.loadFromBytes(label = "in-memory", read = {
            """
            [
              { "prefix": "AC:BC:32", "vendor": "Apple", "target": "MACOS" },
              { "prefix": "7C:1E:52", "vendor": "Microsoft", "target": "WINDOWS" }
            ]
            """.trimIndent().toByteArray()
        })
        assertEquals(HostTarget.MACOS, hints.lookup("AC:BC:32"))
        assertEquals(HostTarget.WINDOWS, hints.lookup("7C:1E:52"))
    }

    @Test
    fun `lookup is case-insensitive`() = runBlocking {
        val hints = OuiVendorHints.fakeEmpty()
        hints.loadFromBytes(label = "in-memory", read = {
            """[ { "prefix": "ac:bc:32", "vendor": "Apple", "target": "MACOS" } ]"""
                .toByteArray()
        })
        assertEquals(HostTarget.MACOS, hints.lookup("ac:bc:32"))
        assertEquals(HostTarget.MACOS, hints.lookup("AC:BC:32"))
        assertEquals(HostTarget.MACOS, hints.lookup("Ac:Bc:32"))
    }

    @Test
    fun `unknown prefix returns null`() = runBlocking {
        val hints = OuiVendorHints.fakeEmpty()
        hints.loadFromBytes(label = "in-memory", read = {
            """[ { "prefix": "AC:BC:32", "vendor": "Apple", "target": "MACOS" } ]"""
                .toByteArray()
        })
        assertNull(hints.lookup("00:11:22"))
    }

    @Test
    fun `IO failure during load leaves the table empty`() = runBlocking {
        val hints = OuiVendorHints.fakeEmpty()
        hints.loadFromBytes(label = "in-memory", read = { throw IOException("boom") })
        assertNull(hints.lookup("AC:BC:32"))
    }

    @Test
    fun `malformed JSON leaves the table empty`() = runBlocking {
        val hints = OuiVendorHints.fakeEmpty()
        hints.loadFromBytes(label = "in-memory", read = { "not json".toByteArray() })
        assertNull(hints.lookup("AC:BC:32"))
    }
}
