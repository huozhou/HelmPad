package com.vibepad.keyboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibepad.keyboard.macro.MacroDefinition
import com.vibepad.keyboard.macro.Profile
/**
 * The 4×2 macro grid. One tap = fire. Long-press is intentionally a no-op in v1 —
 * see roadmap-next-phase.md for the in-app editor plan.
 *
 * `operator-layout-responsive` swapped this from `LazyVerticalGrid` to a plain
 * `Column` of `Row`s. With 8 fixed slots there's no virtualization win to be
 * had, and the lazy grid intercepts vertical scroll gestures, which fights the
 * outer page scroller we use as the short-screen fallback. The column is
 * `wrapContentHeight()` so [MainScreen]'s budget calculator can hand the grid
 * exactly its natural height; [LayoutBudget.macroGridHeight] mirrors the math.
 *
 * Only `new_session` (`/clear`) carries `destructive = true` — it wipes the current
 * Claude Code context and can't be undone. Its ring is tinted `error` (vs. the
 * neutral `outlineVariant` for the others) and the stronger haptic on tap makes
 * an accidental press obviously different from routine approvals.
 *
 * Visual language (`minimalist-visual-pass`): tiles are unfilled — just a
 * hairline outline (0.5dp `outlineVariant`, or 1dp `error` for destructive) over
 * the ambient surface. The previous lilac `primaryContainer` fill made every
 * tile a heavyweight coloured block; stripping it to an outline lets the icon
 * + label carry meaning and matches the Apple HIG / Linear-ish direction the
 * project is heading. Press feedback is a three-layer stack: M3 ripple, a 0.96
 * scale drop over 80ms, and the border briefly tints toward `primary`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MacroGrid(
    profile: Profile,
    onFire: (MacroDefinition) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(LayoutBudget.MacroGridOuterPadding),
        verticalArrangement = Arrangement.spacedBy(LayoutBudget.MacroGridTileGap),
    ) {
        profile.slots.chunked(LayoutBudget.MacroGridColumns).forEach { rowSlots ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LayoutBudget.MacroGridTileGap),
            ) {
                rowSlots.forEach { slot ->
                    Box(modifier = Modifier.weight(1f)) {
                        MacroTile(slot = slot, onFire = onFire)
                    }
                }
                // Pad short trailing rows so every tile keeps the same width as
                // the rows above it — without this a 5-slot profile would render
                // its lone bottom tile spanning the full width.
                repeat(LayoutBudget.MacroGridColumns - rowSlots.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MacroTile(
    slot: MacroDefinition,
    onFire: (MacroDefinition) -> Unit,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    // Press feedback (three layers): M3 ripple, a 0.96 scale drop over 80ms, and
    // the border switches to the primary tint while held. No container fill
    // change — the outlined tile keeps the minimalist surface across states.
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "tile-scale",
    )

    val accent = if (slot.destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.primary
    val restingBorderColor = if (slot.destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.outlineVariant
    val restingBorderWidth = if (slot.destructive) DESTRUCTIVE_BORDER_WIDTH else BORDER_WIDTH
    val borderColor = if (pressed) accent else restingBorderColor
    val borderWidth = if (pressed) DESTRUCTIVE_BORDER_WIDTH else restingBorderWidth

    val contentColor = if (slot.destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface

    val label = slot.label

    val shape = RoundedCornerShape(CORNER_RADIUS)
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (slot.destructive) Haptics.destructive(context) else Haptics.regular(context)
                    onFire(slot)
                },
                // Long-press is intentionally a no-op in v1 — the editing entry point
                // is deferred to v2 (see docs/roadmap-next-phase.md).
                onLongClick = null,
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        border = BorderStroke(width = borderWidth, color = borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(ICON_LABEL_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = IconRegistry.resolve(slot.iconRef),
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(ICON_SIZE),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = LocalContentColor.current,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Layout parameters kept at file scope so Paparazzi snapshots and future theming
// see the same numbers. Changing any of these invalidates golden images.
private val CORNER_RADIUS = 14.dp
private val ICON_SIZE = 28.dp
private val ICON_LABEL_GAP = 6.dp
private val BORDER_WIDTH = 0.5.dp
private val DESTRUCTIVE_BORDER_WIDTH = 1.dp
