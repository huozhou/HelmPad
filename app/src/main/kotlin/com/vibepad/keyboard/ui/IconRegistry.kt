package com.vibepad.keyboard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.KeyboardTab
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Maps the string [iconRef] carried in profile JSON to a concrete Material
 * [ImageVector]. Kept as a closed map so unknown refs fail loudly at profile-load
 * diagnostics rather than silently rendering a fallback glyph on a live button.
 *
 * The bundled `claude-code.json` uses the keys at the top of the table; the fallback
 * is a radar glyph so an obviously-wrong tile stands out visually during debugging.
 */
internal object IconRegistry {

    private val TABLE: Map<String, ImageVector> = mapOf(
        "check_circle" to Icons.Filled.CheckCircle,
        "autorenew" to Icons.Filled.Autorenew,
        "swap_horiz" to Icons.Filled.SwapHoriz,
        "restart_alt" to Icons.Filled.RestartAlt,
        "compress" to Icons.Filled.Compress,
        "difference" to Icons.Filled.Difference,
        "close" to Icons.Filled.Close,
        "arrow_upward" to Icons.Filled.ArrowUpward,
        "arrow_downward" to Icons.Filled.ArrowDownward,
        "search" to Icons.Filled.Search,
        "edit_note" to Icons.Filled.EditNote,
        "edit" to Icons.Filled.Edit,
    )

    fun resolve(iconRef: String): ImageVector = TABLE[iconRef] ?: Icons.Filled.Radar
}
