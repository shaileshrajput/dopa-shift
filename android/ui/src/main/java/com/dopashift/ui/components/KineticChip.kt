package com.dopashift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.theme.DopaShiftPillShape
import com.dopashift.ui.theme.DopaShiftTokens

/** 1dp hairline stroke width for the chip outline, matching the `surfaceBorder` treatment. */
private val ChipStroke: Dp = 1.dp

/**
 * A rounded **status / keyword / category chip** on an elevated surface (AUI-4.1, AUI-5.2).
 *
 * Renders [text] on a [DopaShiftTokens.Colors.surfaceElevated] full-pill container tinted by a
 * hairline stroke in [accent] so category, keyword, and status chips read as tappable-looking
 * tokens without competing with primary content. The label uses the mono
 * [DopaShiftTokens.TextRoles.labelMono] badge role and is drawn in the [accent] color; the pill
 * shape is [DopaShiftPillShape] (full pill).
 *
 * The chip is presentational (non-interactive), so its text is exposed to TalkBack directly and it
 * intentionally carries no separate content description or minimum touch target — it is a label,
 * not a control.
 *
 * @param text the chip label (already localized by the caller).
 * @param accent the semantic accent for the stroke and label; defaults to
 *   [DopaShiftTokens.Colors.momentumTeal].
 * @param modifier optional layout modifier.
 */
@Composable
fun KineticChip(
    text: String,
    accent: Color = DopaShiftTokens.Colors.momentumTeal,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = DopaShiftTokens.TextRoles.labelMono,
        color = accent,
        modifier = modifier
            .background(DopaShiftTokens.Colors.surfaceElevated, DopaShiftPillShape)
            .border(ChipStroke, accent.copy(alpha = ChipStrokeAlpha), DopaShiftPillShape)
            .padding(
                horizontal = DopaShiftTokens.Spacing.space12,
                vertical = DopaShiftTokens.Spacing.space4,
            ),
    )
}

/** Alpha applied to the accent when used as the chip's subtle outline. */
private const val ChipStrokeAlpha = 0.4f
