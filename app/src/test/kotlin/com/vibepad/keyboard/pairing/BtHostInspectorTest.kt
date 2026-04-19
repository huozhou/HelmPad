package com.vibepad.keyboard.pairing

import android.bluetooth.BluetoothClass
import com.vibepad.keyboard.input.HostTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM tests for [BtHostInspector] using the [BtHostInspector.Probe] DTO so
 * we never have to construct an Android `BluetoothDevice` mock. Coverage targets
 * the three signal cascades plus their negatives.
 */
class BtHostInspectorTest {

    private val emptyOui = OuiVendorHints.fakeEmpty()
    private val inspector = BtHostInspector(emptyOui)

    @Test
    fun `MacBook name returns macOS HIGH NAME`() {
        val guess = inspector.inspect(probe(name = "MacBook Pro"))
        assertEquals(HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME), guess)
    }

    @Test
    fun `Owner-style possessive Mac name returns macOS HIGH NAME`() {
        val guess = inspector.inspect(probe(name = "Owner\u2019s Mac"))
        assertEquals(HostGuess(HostTarget.MACOS, Confidence.HIGH, Source.NAME), guess)
    }

    @Test
    fun `DESKTOP template returns Windows HIGH NAME`() {
        val guess = inspector.inspect(probe(name = "DESKTOP-7ABC123"))
        assertEquals(HostGuess(HostTarget.WINDOWS, Confidence.HIGH, Source.NAME), guess)
    }

    @Test
    fun `Surface keyword returns Windows HIGH NAME`() {
        val guess = inspector.inspect(probe(name = "Surface Laptop 5"))
        assertEquals(HostGuess(HostTarget.WINDOWS, Confidence.HIGH, Source.NAME), guess)
    }

    @Test
    fun `bare 'mac' word does not match macOS regex`() {
        // We intentionally don't match "mac" in arbitrary positions because it
        // collides with names like "Mac (the dog)". The owner-pattern regex
        // requires possessive-S or "macbook" follow-up.
        val guess = inspector.inspect(probe(name = "Mac the dog"))
        // "Mac the dog" has "mac" but not adjacent to a host product noun.
        assertNull(guess.target)
    }

    @Test
    fun `non-computer major class with unknown name returns NONE`() {
        val guess = inspector.inspect(
            probe(name = "Random thing", majorClass = BluetoothClass.Device.Major.PHONE),
        )
        assertEquals(HostGuess.NONE, guess)
    }

    @Test
    fun `computer class with unknown OUI returns NONE`() {
        val guess = inspector.inspect(probe(name = "Office", majorClass = BluetoothClass.Device.Major.COMPUTER))
        assertEquals(HostGuess.NONE, guess)
    }

    @Test
    fun `OUI prefix match returns LOW OUI`() {
        val seeded = OuiVendorHints.fakeWith(mapOf("AC:BC:32" to HostTarget.MACOS))
        val seededInspector = BtHostInspector(seeded)
        val guess = seededInspector.inspect(
            probe(
                name = "Office",
                majorClass = BluetoothClass.Device.Major.COMPUTER,
                address = "AC:BC:32:11:22:33",
            ),
        )
        assertEquals(HostGuess(HostTarget.MACOS, Confidence.LOW, Source.OUI), guess)
    }

    @Test
    fun `name signal beats OUI signal`() {
        val seeded = OuiVendorHints.fakeWith(mapOf("AC:BC:32" to HostTarget.MACOS))
        val seededInspector = BtHostInspector(seeded)
        // Name says Windows even though the OUI table maps the prefix to macOS:
        // the high-confidence name wins because it's evaluated first.
        val guess = seededInspector.inspect(
            probe(
                name = "DESKTOP-ABCDEFG",
                majorClass = BluetoothClass.Device.Major.COMPUTER,
                address = "AC:BC:32:11:22:33",
            ),
        )
        assertEquals(HostTarget.WINDOWS, guess.target)
        assertEquals(Source.NAME, guess.source)
    }

    @Test
    fun `null name is tolerated`() {
        val guess = inspector.inspect(probe(name = null))
        assertEquals(HostGuess.NONE, guess)
    }

    private fun probe(
        name: String?,
        majorClass: Int? = BluetoothClass.Device.Major.COMPUTER,
        address: String = "AA:BB:CC:11:22:33",
    ): BtHostInspector.Probe = BtHostInspector.Probe(name, majorClass, address)
}
