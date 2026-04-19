package com.vibepad.keyboard.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
 * Claude Code context and can't be undone. It renders with the error-colour
 * container and triggers the stronger haptic so an accidental tap feels obviously
 * different from routine approvals.
 *
 * Press feedback uses a four-layer stack — M3 ripple (clipped by the tile's
 * rounded-corner shape because `.clip(shape)` sits above `.combinedClickable` in
 * the modifier chain), a 0.96 scale drop animated over 80ms, an 0.88 alpha
 * darken on the container tint, and a `tonalElevation` drop from 2dp to 0dp.
 * Each layer on its own is subtle; together they give the tile a confident
 * "button-that-gets-pushed-in" feel without ever flashing content alpha.
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

    // Press feedback stack (four layers) — each one is subtle on its own but
    // together they give the tile a confident "button-that-gets-pushed-in" feel:
    //   1. Material3 ripple via [LocalIndication] (theme-provided).
    //   2. `graphicsLayer` scale drop to 0.96 over 80ms.
    //   3. Container tint darkens to alpha 0.88 of `baseColor`.
    //   4. `tonalElevation` drops from 2dp to 0dp so the tile flattens into the
    //      surface.
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 80),
        label = "tile-scale",
    )

    val baseColor = if (slot.destructive) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (slot.destructive) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer

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
        color = if (pressed) baseColor.copy(alpha = 0.88f) else baseColor,
        contentColor = contentColor,
        tonalElevation = if (pressed) 0.dp else 2.dp,
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
private val CORNER_RADIUS = 18.dp
private val ICON_SIZE = 28.dp
private val ICON_LABEL_GAP = 6.dp
