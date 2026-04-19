package com.vibepad.keyboard.macro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLoaderTest {

    private val loader = ProfileLoader()

    @Test
    fun valid_minimal_profile_round_trips() {
        val json = buildProfileJson(slots = FULL_SLOTS)
        val res = loader.loadFromString(json)
        assertTrue("expected Ok but was $res", res is ProfileLoader.Result.Ok)
        val profile = (res as ProfileLoader.Result.Ok).profile
        assertEquals("profile.test", profile.id)
        assertEquals(Profile.SLOT_COUNT, profile.slots.size)
    }

    @Test
    fun wrong_slot_count_is_invalid() {
        val json = buildProfileJson(slots = FULL_SLOTS.take(5))
        val res = loader.loadFromString(json)
        assertTrue(res is ProfileLoader.Result.Invalid)
        val issues = (res as ProfileLoader.Result.Invalid).issues
        assertTrue(issues.any { it.path == "slots" })
    }

    @Test
    fun blank_label_is_invalid() {
        val broken = FULL_SLOTS.toMutableList().apply {
            this[0] = this[0].replace("\"label\": \"Slot 0\"", "\"label\": \"\"")
        }
        val json = buildProfileJson(slots = broken)
        val res = loader.loadFromString(json)
        assertTrue(res is ProfileLoader.Result.Invalid)
        assertTrue((res as ProfileLoader.Result.Invalid).issues.any { it.path.endsWith("label") })
    }

    @Test
    fun duplicate_macro_ids_are_invalid() {
        val broken = FULL_SLOTS.toMutableList().apply {
            this[1] = this[1].replace("\"id\": \"slot_1\"", "\"id\": \"slot_0\"")
        }
        val json = buildProfileJson(slots = broken)
        val res = loader.loadFromString(json)
        assertTrue(res is ProfileLoader.Result.Invalid)
        assertTrue((res as ProfileLoader.Result.Invalid).issues.any { it.message.contains("Duplicate") })
    }

    @Test
    fun non_ascii_literal_is_rejected_with_index() {
        val broken = FULL_SLOTS.toMutableList().apply {
            this[0] = this[0].replace("\"text\": \"hello\"", "\"text\": \"h你\"")
        }
        val json = buildProfileJson(slots = broken)
        val res = loader.loadFromString(json)
        assertTrue(res is ProfileLoader.Result.Invalid)
        val issues = (res as ProfileLoader.Result.Invalid).issues
        assertTrue(issues.any { it.message.contains("codepoint") })
    }

    @Test
    fun unknown_major_schema_is_flagged() {
        val json = buildProfileJson(schema = "2.0.0", slots = FULL_SLOTS)
        val res = loader.loadFromString(json)
        assertTrue(res is ProfileLoader.Result.UnknownSchema)
        assertEquals(2, (res as ProfileLoader.Result.UnknownSchema).majorSeen)
    }

    @Test
    fun malformed_json_returns_diagnostic_message() {
        val res = loader.loadFromString("{ not real json")
        assertTrue(res is ProfileLoader.Result.MalformedJson)
    }

    /**
     * The [InputAction.Sequence] variant is no longer used by any bundled slot (the
     * previous `yes_dont_ask` was deleted in `claude-code-profile-trim`), but custom
     * profiles may still depend on it. Ensure the deserializer keeps round-tripping
     * a sequence with an inter-step delay.
     */
    @Test
    fun sequence_of_chords_with_inter_step_delay_parses() {
        val sequenceSlot = """
            {
              "id": "two_step_chord",
              "label": "Two-step",
              "iconRef": "arrow_downward",
              "action": {
                "type": "Sequence",
                "steps": [
                  { "type": "Chord", "modifiers": [], "key": "DOWN_ARROW" },
                  { "type": "Chord", "modifiers": [], "key": "ENTER" }
                ],
                "interStepDelayMs": 30
              },
              "destructive": false
            }
        """.trimIndent()
        val slots = mutableListOf(sequenceSlot)
        slots += (1 until Profile.SLOT_COUNT).map { idx ->
            """
            {
              "id": "filler_$idx",
              "label": "Filler $idx",
              "iconRef": "radar",
              "action": { "type": "Literal", "text": "x" },
              "destructive": false
            }
            """.trimIndent()
        }
        val res = loader.loadFromString(buildProfileJson(slots = slots))
        assertTrue("expected Ok but was $res", res is ProfileLoader.Result.Ok)
        val profile = (res as ProfileLoader.Result.Ok).profile
        val action = profile.slots.first().action
        assertTrue(action is com.vibepad.keyboard.input.InputAction.Sequence)
        val seq = action as com.vibepad.keyboard.input.InputAction.Sequence
        assertEquals(2, seq.steps.size)
        assertEquals(30L, seq.interStepDelayMs)
    }

    @Test
    fun chord_with_no_modifiers_validates() {
        val single = """
            {
              "id": "only_key",
              "label": "Enter",
              "iconRef": "radar",
              "action": { "type": "Chord", "modifiers": [], "key": "ENTER" },
              "destructive": false
            }
        """.trimIndent()
        val slots = (0 until Profile.SLOT_COUNT).map {
            single.replace("\"only_key\"", "\"only_key_$it\"")
        }
        val res = loader.loadFromString(buildProfileJson(slots = slots))
        assertTrue(res is ProfileLoader.Result.Ok)
    }

    // ---- fixtures -----------------------------------------------------------

    private fun buildProfileJson(
        id: String = "profile.test",
        schema: String = "1.0.0",
        slots: List<String>,
    ): String = """
        {
          "id": "$id",
          "name": "Test",
          "schemaVersion": "$schema",
          "slots": [${slots.joinToString(",")}]
        }
    """.trimIndent()

    /** Exactly [Profile.SLOT_COUNT] filler slots for the happy-path tests. */
    private val FULL_SLOTS: List<String> = (0 until Profile.SLOT_COUNT).map { idx ->
        """
        {
          "id": "slot_$idx",
          "label": "Slot $idx",
          "iconRef": "icon_$idx",
          "action": { "type": "Literal", "text": "hello" },
          "destructive": false
        }
        """.trimIndent()
    }
}
