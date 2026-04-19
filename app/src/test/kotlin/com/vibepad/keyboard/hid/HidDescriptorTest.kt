package com.vibepad.keyboard.hid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HidDescriptorTest {

    @Test
    fun descriptor_contains_both_report_ids_in_order() {
        val bytes = HidDescriptor.BYTES
        // Report ID item = 0x85 followed by the id.
        var sawReportId1 = false
        var sawReportId2 = false
        var i = 0
        while (i < bytes.size - 1) {
            if (bytes[i] == 0x85.toByte()) {
                when (bytes[i + 1]) {
                    0x01.toByte() -> {
                        assertTrue("Report ID 1 should precede Report ID 2", !sawReportId2)
                        sawReportId1 = true
                    }
                    0x02.toByte() -> {
                        assertTrue("Report ID 1 must appear before Report ID 2", sawReportId1)
                        sawReportId2 = true
                    }
                }
            }
            i++
        }
        assertTrue("Descriptor must contain Report ID 1 (keyboard)", sawReportId1)
        assertTrue("Descriptor must contain Report ID 2 (mouse)", sawReportId2)
    }

    @Test
    fun descriptor_starts_with_generic_desktop_keyboard_header() {
        // 0x05 0x01 = Usage Page (Generic Desktop), 0x09 0x06 = Usage (Keyboard)
        val bytes = HidDescriptor.BYTES
        assertEquals(0x05.toByte(), bytes[0])
        assertEquals(0x01.toByte(), bytes[1])
        assertEquals(0x09.toByte(), bytes[2])
        assertEquals(0x06.toByte(), bytes[3])
    }

    @Test
    fun descriptor_is_boot_combo_subclass() {
        assertEquals(0xC0.toByte(), HidDescriptor.SDP_SUBCLASS_COMBO)
    }
}
