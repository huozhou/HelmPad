package com.vibepad.keyboard.macro

import com.vibepad.keyboard.input.InputAction
import kotlinx.serialization.Serializable

/**
 * One slot in the operator grid. Serialized to JSON as part of the bundled profile.
 *
 * @property id Stable identifier; used for per-host-preference keying and, in the
 *              future (v2), for overlay resolution. Never displayed to users.
 * @property label User-facing name. Free-form string; the user picks whatever language
 *                 or symbol they want when they author their own profile.
 * @property iconRef A logical reference the UI layer maps to a `Material Icons`
 *                   drawable (e.g. `"check_circle"` → `Icons.Filled.CheckCircle`).
 *                   Kept as a string so profile JSON does not depend on Android SDK
 *                   icon enums and can be edited by future authoring tools.
 * @property action What pressing this slot actually does.
 * @property destructive Whether this slot is expected to perform an irreversible /
 *                       interrupting operation (e.g. `Esc` mid-stream, reject). The UI
 *                       layer amplifies haptics and uses an error-coloured ripple.
 */
@Serializable
data class MacroDefinition(
    val id: String,
    val label: String,
    val iconRef: String,
    val action: InputAction,
    val destructive: Boolean = false,
)

/**
 * A full grid of [Profile.SLOT_COUNT] macros that the user can fire from the operator
 * screen.
 *
 * @property id Stable profile identifier ("profile.claude-code" for v1's only entry).
 * @property name Display name shown in the profile selector.
 * @property schemaVersion Major / minor / patch. Readers reject unknown major versions.
 */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val schemaVersion: String,
    val slots: List<MacroDefinition>,
) {
    companion object {
        const val SLOT_COUNT = 8
        const val CURRENT_SCHEMA_MAJOR = 1
    }
}
