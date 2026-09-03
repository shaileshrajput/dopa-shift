package com.dopashift.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * The DopaShift Kinetic **card** surface (AUI-8.2).
 *
 * Renders content on a Level-1 surface with a **16dp** corner radius, a **1dp** hairline
 * `surfaceBorder` stroke, and **zero elevation** — depth comes from tonal layering, never shadow
 * (AUI-8.2). When [accentStroke] is provided (e.g. Momentum Teal for the Primary Anchor habit) the
 * card uses that color at a slightly heavier 1.5dp weight instead of the default hairline, so an
 * anchored/active card reads as accented without adding a shadow.
 *
 * All colors, the radius, the stroke widths, and the internal padding are sourced from
 * [DopaShiftTokens]; no raw literals are inlined (AUI-8.1).
 *
 * Accessibility (AUI-8.5): when [contentDescription] is supplied it is attached to the card
 * container so an informational card is announced by TalkBack; interactive children inside the card
 * carry their own semantics.
 *
 * @param modifier layout modifier applied to the card.
 * @param accentStroke optional accent color for the border; when set, drawn at 1.5dp instead of the
 *   default 1dp `surfaceBorder`.
 * @param contentDescription optional TalkBack description for the card container.
 * @param content the card body, laid out in a [ColumnScope].
 */
@Composable
fun KineticCard(
    modifier: Modifier = Modifier,
    accentStroke: Color? = null,
    contentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = if (accentStroke != null) {
        BorderStroke(ACCENT_STROKE_WIDTH, accentStroke)
    } else {
        BorderStroke(DEFAULT_STROKE_WIDTH, DopaShiftTokens.Colors.surfaceBorder)
    }

    val cardModifier = if (contentDescription != null) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    OutlinedCard(
        modifier = cardModifier,
        shape = RoundedCornerShape(DopaShiftTokens.Radii.card),
        colors = CardDefaults.outlinedCardColors(
            containerColor = DopaShiftTokens.Colors.surface,
            contentColor = DopaShiftTokens.Colors.textPrimary,
        ),
        // Zero elevation shadow — depth via tonal layering + stroke only (AUI-8.2).
        elevation = CardDefaults.outlinedCardElevation(defaultElevation = ZERO_ELEVATION),
        border = border,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(DopaShiftTokens.Spacing.cardPadding),
            content = content,
        )
    }
}

/** Default 1dp hairline border weight (AUI-8.2); a component dimension, not a spacing token. */
private val DEFAULT_STROKE_WIDTH: Dp = 1.dp

/** Accent border weight (1.5dp) for anchored/active cards; a component dimension. */
private val ACCENT_STROKE_WIDTH: Dp = 1.5.dp

/** Zero elevation — no shadow on any card surface (AUI-8.2). */
private val ZERO_ELEVATION: Dp = 0.dp
