package com.dopashift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.dopashift.ui.theme.DopaShiftPillShape
import com.dopashift.ui.theme.DopaShiftTokens
import kotlin.math.roundToInt

/** Height of the horizontal usage track. */
private val TrackHeight: Dp = 8.dp

/**
 * A horizontal **usage bar** with a label, a monospace value, and a percent fill (AUI-6.5).
 *
 * Renders a top row pairing [label] (secondary body text, start-aligned) with [value] (a
 * monospace [DopaShiftTokens.TextRoles.labelMono] readout, end-aligned) and a rounded track below
 * it. The track sits on [DopaShiftTokens.Colors.surfaceElevated]; the filled portion is drawn in
 * [color] and spans [fraction] of the width. [fraction] is clamped to `0f..1f` so out-of-range
 * inputs never over- or under-draw the fill.
 *
 * The whole component is collapsed into a single TalkBack node via [clearAndSetSemantics] with a
 * content description combining the label, value, and integer percent, so screen readers announce
 * one coherent phrase rather than three fragments.
 *
 * @param label the category / metric name (already localized).
 * @param value the absolute value readout (e.g. a formatted duration), drawn in monospace.
 * @param fraction the fill fraction; clamped to `0f..1f`.
 * @param color the fill color for this category.
 * @param modifier optional layout modifier.
 */
@Composable
fun UsageBar(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val safeFraction = fraction.coerceIn(0f, 1f)
    val percent = (safeFraction * 100f).roundToInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "$label, $value, $percent%"
            },
        verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space4),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = DopaShiftTokens.TextRoles.bodyMedium,
                color = DopaShiftTokens.Colors.textSecondary,
            )
            Text(
                text = value,
                style = DopaShiftTokens.TextRoles.labelMono,
                color = DopaShiftTokens.Colors.textPrimary,
                textAlign = TextAlign.End,
            )
        }
        // Track + fill.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrackHeight)
                .clip(DopaShiftPillShape)
                .background(DopaShiftTokens.Colors.surfaceElevated),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackHeight)
                    .layout { measurable, constraints ->
                        val width = (constraints.maxWidth * safeFraction).roundToInt()
                        val placeable = measurable.measure(
                            constraints.copy(minWidth = width, maxWidth = width),
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .clip(DopaShiftPillShape)
                    .background(color),
            ) {}
        }
    }
}
