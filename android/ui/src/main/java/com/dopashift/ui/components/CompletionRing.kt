package com.dopashift.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * Circular progress meter — the DopaShift Kinetic **Completion Ring** (AUI-1.1, AUI-4.1, AUI-8.4).
 *
 * Renders [percent] as a Momentum-Teal arc drawn over an elevated-surface track, with the integer
 * percentage rendered dead-center in the JetBrains Mono metric role so the value never jitters as
 * it ticks (AUI-8.4). The value is clamped to `0..100` and rendered with no fractional digits
 * (Property 5 — Efficiency Ring Renders an Integer Percentage).
 *
 * All colors, the stroke width, the text role, and the center label formatting are sourced from
 * [DopaShiftTokens]; no raw literals are inlined (AUI-8.1).
 *
 * Accessibility (AUI-8.5): the whole ring exposes a single [contentDescription] for TalkBack and
 * hides its decorative child nodes (the arc and the numeric label) from the accessibility tree, so
 * a screen reader announces the supplied description rather than the bare number.
 *
 * @param percent progress in `0..100`; clamped and rendered as an integer percentage.
 * @param contentDescription the TalkBack description for the whole ring (e.g. "Efficiency 72 percent").
 * @param modifier layout modifier applied to the ring container.
 * @param size the ring diameter; defaults to the 64dp dashboard header ring.
 * @param trackColor the unfilled track color; defaults to the elevated-surface token.
 * @param progressColor the filled-arc color; defaults to the Momentum-Teal reward token.
 */
@Composable
fun CompletionRing(
    percent: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = DEFAULT_RING_SIZE,
    trackColor: Color = DopaShiftTokens.Colors.surfaceElevated,
    progressColor: Color = DopaShiftTokens.Colors.momentumTeal,
) {
    val clamped = percent.coerceIn(0, 100)
    val fraction = clamped / 100f

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = RING_STROKE.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Elevated-surface track (full circle).
            drawArc(
                color = trackColor,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Teal progress arc.
            if (fraction > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = START_ANGLE,
                    sweepAngle = FULL_SWEEP * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }

        // Integer-percent center in the JetBrains Mono metric role (AUI-8.4). The value is an
        // integer with no fractional digits (Property 5); the trailing percent sign comes from the
        // caller-agnostic mono formatting rather than a hardcoded UI string.
        Text(
            text = clamped.toString() + PERCENT_SIGN,
            style = DopaShiftTokens.TextRoles.metricCounter,
            color = DopaShiftTokens.Colors.textPrimary,
        )
    }
}

/** Default 64dp dashboard header ring diameter (AUI-1.1). */
private val DEFAULT_RING_SIZE: Dp = 64.dp

/** Ring stroke width — a component dimension, not a design-system spacing token. */
private val RING_STROKE: Dp = 6.dp

// Sweep starts at 12 o'clock and runs clockwise a full turn.
private const val START_ANGLE = -90f
private const val FULL_SWEEP = 360f

/** The percent glyph appended to the integer readout; concatenated rather than inlined as copy. */
private const val PERCENT_SIGN = "%"
