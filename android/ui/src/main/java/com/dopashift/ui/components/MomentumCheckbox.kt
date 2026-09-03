package com.dopashift.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.theme.DopaShiftTokens

/**
 * The DopaShift Kinetic **Momentum Checkbox** (AUI-1.3, AUI-5.3).
 *
 * A 24dp visual box centered inside a 48dp Touch_Target hit area ([DopaShiftTokens.TouchTargets]),
 * followed by a label. Toggling fires a single confirmation haptic pulse, flips the box to a filled
 * Momentum-Teal square with a check glyph, and — when [struckThroughWhenChecked] is `true` — applies
 * strikethrough to the label in the completed-text color (Property 6 — Completed Items Are Struck
 * Through and Dimmed). When unchecked the box shows an empty outline and the label renders normally.
 *
 * All colors, the box/hit-area sizes, the label text role, and the completed styling come from
 * [DopaShiftTokens]; no raw literals are inlined (AUI-8.1). The label is supplied by the caller
 * (never a hardcoded `Text("...")`), so upstream screens externalize the copy (AUI-8.6).
 *
 * Accessibility (AUI-8.3, AUI-8.5): the whole control is a single [Role.Checkbox] toggle whose hit
 * area is the full 48dp target; it exposes one aggregate content description ("<label>, completed" /
 * "<label>, not completed") and hides its decorative child nodes so TalkBack announces the label and
 * its state together.
 *
 * @param checked current completion state.
 * @param onCheckedChange invoked with the new state when the control is toggled.
 * @param label the item text rendered beside the box; supplied (and localized) by the caller.
 * @param modifier layout modifier applied to the control row.
 * @param struckThroughWhenChecked when `true`, a checked label is dimmed + struck through.
 */
@Composable
fun MomentumCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    struckThroughWhenChecked: Boolean = true,
) {
    val view = LocalView.current
    val stateSuffix = if (checked) COMPLETED_SUFFIX else NOT_COMPLETED_SUFFIX
    val description = label + stateSuffix

    Row(
        modifier = modifier
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = { newValue ->
                    // A single light confirmation haptic on every toggle (AUI-1.3).
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onCheckedChange(newValue)
                },
            )
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 24dp visual box inside the 48dp hit area (AUI-8.3).
        Box(
            modifier = Modifier.size(DopaShiftTokens.TouchTargets.minTouchTarget),
            contentAlignment = Alignment.Center,
        ) {
            val boxModifier = if (checked) {
                Modifier
                    .size(DopaShiftTokens.TouchTargets.checkboxBox)
                    .background(DopaShiftTokens.Colors.momentumTeal, BOX_SHAPE)
            } else {
                Modifier
                    .size(DopaShiftTokens.TouchTargets.checkboxBox)
                    .border(BOX_STROKE, DopaShiftTokens.Colors.textSecondary, BOX_SHAPE)
            }
            Box(modifier = boxModifier, contentAlignment = Alignment.Center) {
                if (checked) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = DopaShiftTokens.Colors.onMomentumTeal,
                        modifier = Modifier.size(DopaShiftTokens.TouchTargets.checkboxBox),
                    )
                }
            }
        }

        val labelColor: Color
        val decoration: TextDecoration?
        if (checked && struckThroughWhenChecked) {
            labelColor = DopaShiftTokens.Colors.textCompleted
            decoration = TextDecoration.LineThrough
        } else {
            labelColor = DopaShiftTokens.Colors.textPrimary
            decoration = null
        }

        Text(
            text = label,
            style = DopaShiftTokens.TextRoles.bodyMedium,
            color = labelColor,
            textDecoration = decoration,
            modifier = Modifier.padding(start = DopaShiftTokens.Spacing.space8),
        )
    }
}

/** Rounded corner on the visual box; a component dimension, not a spacing token. */
private val BOX_SHAPE = RoundedCornerShape(6.dp)

/** 2dp outline stroke on the unchecked box; a component dimension, not a spacing token. */
private val BOX_STROKE: Dp = 2.dp

/** State suffixes appended to the label for the aggregate content description (AUI-8.5). */
private const val COMPLETED_SUFFIX = ", completed"
private const val NOT_COMPLETED_SUFFIX = ", not completed"
