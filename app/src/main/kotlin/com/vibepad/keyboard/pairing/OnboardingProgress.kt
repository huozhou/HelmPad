package com.vibepad.keyboard.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vibepad.keyboard.R

/**
 * "Node + connecting bar" style progress indicator for the 5-step onboarding
 * wizard. Replaces the older [androidx.compose.material3.LinearProgressIndicator]
 * so the wizard reads as a staged flow instead of a single gliding bar.
 *
 * Node states:
 *  - completed → solid filled circle, radius [NODE_RADIUS]
 *  - current   → solid filled circle + halo (alpha=0.22) at [NODE_RADIUS] * 1.6
 *  - upcoming  → outline only (stroke 2dp), radius [NODE_RADIUS]
 *
 * Connecting bars between nodes use the same fill color; the segment leaving
 * a completed node is solid, segments after the current node are alpha=0.3.
 *
 * @param currentStepIndex 0-based index into the `totalSteps` range. Passing
 *                         `totalSteps` (or greater) renders the "all done"
 *                         state with every node filled.
 * @param totalSteps       The wizard's step count (5 in v1).
 */
@Composable
fun OnboardingProgress(
    currentStepIndex: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(NODE_RADIUS.dp * 3.2f),
        ) {
            if (totalSteps < 1) return@Canvas
            val width = size.width
            val centerY = size.height / 2f
            val nodeRadiusPx = NODE_RADIUS.dp.toPx()
            val haloRadiusPx = nodeRadiusPx * 1.6f
            val strokePx = 2.dp.toPx()
            val barThicknessPx = 4.dp.toPx()

            // Evenly distribute nodes across the canvas width; first node pinned
            // to the left edge + radius so the halo doesn't clip.
            val usableWidth = width - nodeRadiusPx * 2
            val stepX = if (totalSteps > 1) usableWidth / (totalSteps - 1) else 0f
            fun nodeCenter(i: Int) = Offset(nodeRadiusPx + stepX * i, centerY)

            for (i in 0 until totalSteps - 1) {
                val from = nodeCenter(i)
                val to = nodeCenter(i + 1)
                val segmentComplete = i < currentStepIndex
                drawLine(
                    color = if (segmentComplete) accent else accent.copy(alpha = 0.3f),
                    start = Offset(from.x + nodeRadiusPx, from.y),
                    end = Offset(to.x - nodeRadiusPx, to.y),
                    strokeWidth = barThicknessPx,
                )
            }

            for (i in 0 until totalSteps) {
                val center = nodeCenter(i)
                when {
                    i < currentStepIndex -> drawCircle(
                        color = accent,
                        radius = nodeRadiusPx,
                        center = center,
                    )
                    i == currentStepIndex -> {
                        drawCircle(
                            color = accent.copy(alpha = 0.22f),
                            radius = haloRadiusPx,
                            center = center,
                        )
                        drawCircle(
                            color = accent,
                            radius = nodeRadiusPx,
                            center = center,
                        )
                    }
                    else -> drawCircle(
                        color = accent,
                        radius = nodeRadiusPx,
                        center = center,
                        style = Stroke(width = strokePx),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val displayStep = (currentStepIndex + 1).coerceIn(1, totalSteps)
        Text(
            text = stringResource(R.string.onboarding_step_counter, displayStep, totalSteps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val NODE_RADIUS = 10
