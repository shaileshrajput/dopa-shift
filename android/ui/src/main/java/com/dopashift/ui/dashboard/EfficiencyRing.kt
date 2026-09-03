package com.dopashift.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.domain.presentation.MotionPolicy
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.MotionTokens
import com.dopashift.ui.theme.rememberReducedMotion

/**
 * Circular efficiency ring for the Dashboard header (DUX-3 AC5, DUX-3 AC6, DUX-2 AC5).
 *
 * Renders today's efficiency percentage as a circular progress ring filled to `score`% with a
 * centered numeric label. When [score] is `null` the ring shows an explicit "No data" state
 * rather than a misleading 0% fill (DUX-3 AC6).
 *
 * Motion: when [animateOnce] is `true` the sweep animates from 0 to the target value one time
 * (per session, gated by the caller). The animation duration comes from
 * [MotionTokens.Emphasized] resolved through [MotionPolicy.effective] + [rememberReducedMotion],
 * so under the OS reduced-motion setting the sweep collapses to at most `motion-instant` while
 * the final value is still shown (DUX-2 AC9). Progress rings use rounded stroke caps.
 *
 * Colors reference theme tokens only (no raw literals): the filled arc uses the momentum-teal
 * progress accent ([MaterialTheme.colorScheme.secondary]), the track uses the surface-variant
 * token, and the numeric/label text uses `onSurface` so it meets contrast against the surface.
 *
 * Accessibility: the whole ring exposes a single content description — "Efficiency N percent"
 * for a score, or "Efficiency: no data" when [score] is `null` — and hides the decorative
 * child text nodes from the accessibility tree. It is also marked a polite live region so a
 * user-initiated value update is announced by screen readers (DUX-5.8).
 *
 * @param score today's efficiency percentage in `0..100`, or `null` for the "No data" state.
 * @param animateOnce when `true`, animate the sweep 0→value once; when `false`, render statically.
 * @param modifier layout modifier applied to the ring container.
 */
@Composable
fun EfficiencyRing(
    score: Int?,
    animateOnce: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasData = score != null
    val targetFraction = if (hasData) score!!.coerceIn(0, 100) / 100f else 0f

    val reducedMotion = rememberReducedMotion()
    val motionSpec = MotionPolicy.effective(MotionTokens.Emphasized, reducedMotion)

    // Drive the sweep with a single-shot animation. When there is no data or we are not
    // animating, snap directly to the target fraction (0f for the "No data" state).
    val sweep = remember { Animatable(if (animateOnce && hasData) 0f else targetFraction) }
    LaunchedEffect(targetFraction, animateOnce, hasData) {
        if (animateOnce && hasData) {
            sweep.snapTo(0f)
            sweep.animateTo(
                targetValue = targetFraction,
                animationSpec = tween(
                    durationMillis = motionSpec.durationMillis,
                    easing = MotionTokens.easingFor(motionSpec.easingName),
                ),
            )
        } else {
            sweep.snapTo(targetFraction)
        }
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurface

    val description = if (hasData) {
        stringResource(R.string.dux_efficiency_content_description, score!!.coerceIn(0, 100))
    } else {
        stringResource(R.string.dux_efficiency_no_data_content_description)
    }

    Box(
        modifier = modifier
            .size(RingDimens.Diameter)
            // DUX-5.8: announce ring-value updates to screen readers via a polite live region.
            .clearAndSetSemantics {
                contentDescription = description
                liveRegion = LiveRegionMode.Polite
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(RingDimens.Diameter)) {
            val strokePx = RingDimens.Stroke.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Track (full circle).
            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Progress arc — only when we have data (never a misleading fill for "No data").
            if (hasData) {
                drawArc(
                    color = progressColor,
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_SWEEP * sweep.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }

        if (hasData) {
            Text(
                text = stringResource(R.string.dux_efficiency_percent_label, score!!.coerceIn(0, 100)),
                style = MaterialTheme.typography.headlineSmall,
                color = labelColor,
            )
        } else {
            Text(
                text = stringResource(R.string.dux_efficiency_no_data),
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
            )
        }
    }
}

/** Ring geometry. These are component dimensions, not design-system spacing tokens. */
private object RingDimens {
    val Diameter: Dp = 96.dp
    val Stroke: Dp = 8.dp
}

// Sweep starts at 12 o'clock and runs clockwise a full turn.
private const val START_ANGLE = -90f
private const val FULL_SWEEP = 360f
