package com.dopashift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.theme.DopaShiftPillShape
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * The 1dp hairline stroke width shared by the Kinetic controls in this file, matching the
 * `surfaceBorder` hairline treatment defined in the design token file (AUI-8.2). Named here so the
 * width is referenced by name rather than an inline literal at each call site.
 */
private val HairlineStroke: Dp = 1.dp

/**
 * A Material 3 two-option **segmented pill** selector (AUI-3.3).
 *
 * Renders [options] side-by-side inside a single full-pill container. The [selectedIndex] segment
 * is filled with [DopaShiftTokens.Colors.momentumTeal] and its label is drawn in the dark
 * on-teal foreground ([DopaShiftTokens.Colors.onMomentumTeal]); every unselected segment sits on
 * [DopaShiftTokens.Colors.surface] with secondary text. The whole control clips to
 * [DopaShiftPillShape] (full pill) and carries a 1dp [DopaShiftTokens.Colors.surfaceBorder]
 * hairline, matching the Kinetic control treatment.
 *
 * Each segment is a [selectable] within a [selectableGroup], so TalkBack announces the group as a
 * radio set and reads each option's text and selected state. Segments fill the full
 * [DopaShiftTokens.TouchTargets.minTouchTarget] (48dp) height so every option meets the touch
 * target (AUI-8.3, Property 8).
 *
 * Although the design language is a *two-option* pill (`Once` / `Repetitive`), the composable
 * renders whatever [options] it is given so it can also back the Analytics date-range picker
 * (7 / 30 / 90) without a second implementation.
 *
 * @param options the segment labels, left-to-right (already localized by the caller).
 * @param selectedIndex the currently selected segment index.
 * @param onSelect invoked with the tapped segment's index.
 * @param modifier optional layout modifier for the pill container.
 */
@Composable
fun SegmentedPill(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DopaShiftTokens.TouchTargets.minTouchTarget)
            .clip(DopaShiftPillShape)
            .background(DopaShiftTokens.Colors.surface)
            .border(HairlineStroke, DopaShiftTokens.Colors.surfaceBorder, DopaShiftPillShape)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Text(
                text = label,
                style = DopaShiftTokens.TextRoles.labelLarge,
                textAlign = TextAlign.Center,
                color = if (selected) {
                    DopaShiftTokens.Colors.onMomentumTeal
                } else {
                    DopaShiftTokens.Colors.textSecondary
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(DopaShiftPillShape)
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onSelect(index) },
                    )
                    .background(
                        if (selected) DopaShiftTokens.Colors.momentumTeal else Color.Transparent,
                    )
                    .wrapContentSize(Alignment.Center)
                    .padding(horizontal = DopaShiftTokens.Spacing.space12),
            )
        }
    }
}
