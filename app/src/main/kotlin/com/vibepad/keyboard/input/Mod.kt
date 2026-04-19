package com.vibepad.keyboard.input

import kotlinx.serialization.Serializable

/**
 * Abstract modifiers. Intentionally decoupled from HID modifier bits so one macro
 * definition can target both macOS and Windows without duplication.
 *
 * [PRIMARY] is the "main menu / copy-paste" modifier — `Cmd` on macOS, `Ctrl` on Windows.
 * [HYPER] is the less-common one — `Ctrl` on macOS (for readline shortcuts, terminal
 * bindings) and `Win` on Windows.
 *
 * Concrete mapping lives in [KeymapResolver].
 */
@Serializable
enum class Mod {
    PRIMARY,
    SHIFT,
    ALT,
    HYPER,
}
