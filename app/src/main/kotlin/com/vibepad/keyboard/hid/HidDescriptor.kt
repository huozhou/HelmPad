package com.vibepad.keyboard.hid

/**
 * Combined HID report descriptor: Report ID 1 = boot-protocol keyboard,
 * Report ID 2 = mouse with 16-bit relative X/Y and signed wheel.
 *
 * This byte sequence follows the USB HID Class Definition, §6.2.2 "Report Descriptor".
 * Annotated inline so future contributors can match each byte pair to its meaning —
 * the raw stream is opaque, but every two bytes is a `(tag, data)` item.
 *
 * macOS and Windows both accept boot-protocol sub-classes, so this descriptor alone is
 * enough; no platform-specific quirks are needed.
 */
internal object HidDescriptor {

    val BYTES: ByteArray = byteArrayOf(
        // ===== Keyboard (Report ID 1) =====
        0x05, 0x01.toByte(),            // Usage Page (Generic Desktop)
        0x09, 0x06.toByte(),            // Usage (Keyboard)
        0xA1.toByte(), 0x01,            // Collection (Application)
        0x85.toByte(), 0x01,            //   Report ID (1)
        // Modifier bits (byte 0): 8 × 1-bit inputs for usage 0xE0..0xE7
        0x05, 0x07,                     //   Usage Page (Key Codes)
        0x19, 0xE0.toByte(),            //   Usage Minimum (224 = LeftCtrl)
        0x29, 0xE7.toByte(),            //   Usage Maximum (231 = RightGUI)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x25, 0x01,                     //   Logical Maximum (1)
        0x75, 0x01,                     //   Report Size (1)
        0x95.toByte(), 0x08,            //   Report Count (8)
        0x81.toByte(), 0x02,            //   Input (Data, Var, Abs)
        // Reserved byte 1
        0x95.toByte(), 0x01,            //   Report Count (1)
        0x75, 0x08,                     //   Report Size (8)
        0x81.toByte(), 0x01,            //   Input (Const)
        // Key array bytes 2..7
        0x95.toByte(), 0x06,            //   Report Count (6)
        0x75, 0x08,                     //   Report Size (8)
        0x15, 0x00,                     //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x00, // Logical Maximum (0xFF, 16-bit form)
        0x05, 0x07,                     //   Usage Page (Key Codes)
        0x19, 0x00,                     //   Usage Minimum (0)
        0x29, 0xFF.toByte(),            //   Usage Maximum (255)
        0x81.toByte(), 0x00,            //   Input (Data, Array)
        0xC0.toByte(),                  // End Collection

        // ===== Mouse (Report ID 2) =====
        0x05, 0x01,                     // Usage Page (Generic Desktop)
        0x09, 0x02,                     // Usage (Mouse)
        0xA1.toByte(), 0x01,            // Collection (Application)
        0x85.toByte(), 0x02,            //   Report ID (2)
        0x09, 0x01,                     //   Usage (Pointer)
        0xA1.toByte(), 0x00,            //   Collection (Physical)
        // Buttons 1..3 (3 bits) + 5 bits padding
        0x05, 0x09,                     //     Usage Page (Button)
        0x19, 0x01,                     //     Usage Minimum (Button 1)
        0x29, 0x03,                     //     Usage Maximum (Button 3)
        0x15, 0x00,                     //     Logical Minimum (0)
        0x25, 0x01,                     //     Logical Maximum (1)
        0x95.toByte(), 0x03,            //     Report Count (3)
        0x75, 0x01,                     //     Report Size (1)
        0x81.toByte(), 0x02,            //     Input (Data, Var, Abs)
        0x95.toByte(), 0x01,            //     Report Count (1)
        0x75, 0x05,                     //     Report Size (5)
        0x81.toByte(), 0x03,            //     Input (Const, Var, Abs) — padding
        // X, Y relative 16-bit
        0x05, 0x01,                     //     Usage Page (Generic Desktop)
        0x09, 0x30,                     //     Usage (X)
        0x09, 0x31,                     //     Usage (Y)
        0x16, 0x00, 0x80.toByte(),      //     Logical Minimum (-32768)
        0x26.toByte(), 0xFF.toByte(), 0x7F, // Logical Maximum (32767)
        0x75, 0x10,                     //     Report Size (16)
        0x95.toByte(), 0x02,            //     Report Count (2)
        0x81.toByte(), 0x06,            //     Input (Data, Var, Rel)
        // Wheel signed 8-bit
        0x09, 0x38,                     //     Usage (Wheel)
        0x15, 0x81.toByte(),            //     Logical Minimum (-127)
        0x25, 0x7F,                     //     Logical Maximum (127)
        0x75, 0x08,                     //     Report Size (8)
        0x95.toByte(), 0x01,            //     Report Count (1)
        0x81.toByte(), 0x06,            //     Input (Data, Var, Rel)
        0xC0.toByte(),                  //   End Collection (Physical)
        0xC0.toByte(),                  // End Collection (Application)
    )

    /** Subclass code advertised in SDP. 0xC0 = HID boot combo (keyboard + mouse). */
    const val SDP_SUBCLASS_COMBO: Byte = 0xC0.toByte()
}
