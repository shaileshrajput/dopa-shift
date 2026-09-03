package com.dopashift.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import com.dopashift.domain.presentation.MotionPolicy
import com.dopashift.ui.theme.MotionTokens
import com.dopashift.ui.theme.rememberReducedMotion

/**
 * A completion-feedback checkbox that plays a single, bounded acknowledgment when an item is
 * marked complete (DUX-2 AC3): a brief `motion-quick` scale/checkmark animation plus one light
 * haptic confirmation. Intended for the three completion classes — a `DailyTodoItem`, a
 * `Goal_Checklist_Item`, or a `Habit_Track` checkpoint.
 *
 * This is a **single, non-recurring** acknowledgment, not an escalating reward: the animation
 * fires once per transition into the completed state and does nothing on the reverse transition.
 *
 * Motion: the check's scale-in animation uses [MotionTokens.Quick] resolved through
 * [MotionPolicy.effective] + [rememberReducedMotion]. Under the OS reduced-motion setting the
 * animation collapses to at most `motion-instant` (and the haptic is suppressed with the
 * decorative motion), while the completed/incomplete state still changes and renders correctly
 * (DUX-2 AC9).
 *
 * Non-color state (DUX-5 AC7): completion is conveyed by the **icon shape itself** — a filled
 * check-circle when complete versus an empty circle when not — never by color alone. The
 * aggregate content description also states the state in words.
 *
 * Accessibility: the control exposes a single content description ("<label>, completed" /
 * "<label>, not completed") and hides its decorative child nodes from the a11y tree. The caller
 * is responsible for the click affordance and touch-target sizing (DUX-5 AC1) on the enclosing
 * row; this composable renders the visual + motion + haptic feedback only.
 *
 * @param isComplete whether the item is currently complete; a transition `false -> true` triggers
 *   the completion animation and haptic.
 * @param contentDescription accessibility description of what this control completes (e.g. the
 *   task text); combined with the state into the exposed description.
 * @param modifier layout modifier applied to the control container.
 */
@Composable
fun CompletionCheckbox(
    isComplete: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val motionSpec = MotionPolicy.effective(MotionTokens.Quick, reducedMotion)
    val view = LocalView.current

    // Scale pulse driven once on each transition into the completed state.
    val scale = remember { Animatable(1f) }
    // Track the previous state so we only celebrate the false -> true transition (not first
    // composition of an already-complete item, and not the reverse transition).
    val wasComplete = remember { mutableStateOf(isComplete) }

    LaunchedEffect(isComplete) {
        val justCompleted = isComplete && !wasComplete.value
        wasComplete.value = isComplete
        if (justCompleted && !reducedMotion) {
            // One light haptic confirmation — a permitted haptic class (DUX-2 AC11).
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            // A single brief scale pulse: pop up then settle.
            val half = (motionSpec.durationMillis / 2).coerceAtLeast(1)
            val easing = MotionTokens.easingFor(motionSpec.easingName)
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = COMPLETION_PULSE_SCALE,
                animationSpec = tween(durationMillis = half, easing = easing),
            )
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = half, easing = easing),
            )
        } else {
            scale.snapTo(1f)
        }
    }

    val description = if (isComplete) {
        "$contentDescription, completed"
    } else {
        "$contentDescription, not completed"
    }

    Box(
        modifier = modifier
            .size(CompletionDimens.Box)
            .clearAndSetSemantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Non-color indicator: the icon shape encodes state (filled check vs. empty circle).
        val icon = if (isComplete) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked
        val tint = if (isComplete) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(CompletionDimens.Icon)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                },
        )
    }
}

/**
 * A brief overlay checkmark burst that can be layered over an item as it completes, for cases
 * where the [CompletionCheckbox] icon-swap alone is too subtle. Shows a scaling check-circle for
 * one `motion-quick` beat, then fades out. Purely decorative — it carries no semantics and is
 * skipped by screen readers; the authoritative state is conveyed by the underlying control.
 *
 * @param visible when `true`, plays the burst once; set back to `false` to reset.
 * @param modifier layout modifier applied to the burst container.
 */
@Composable
fun CompletionBurst(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val motionSpec = MotionPolicy.effective(MotionTokens.Quick, reducedMotion)
    val duration = motionSpec.durationMillis
    val easing = MotionTokens.easingFor(motionSpec.easingName)

    Box(modifier = modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(durationMillis = duration, easing = easing)) +
                scaleIn(tween(durationMillis = duration, easing = easing)),
            exit = fadeOut(tween(durationMillis = duration, easing = easing)),
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(CompletionDimens.Burst),
            )
        }
    }
}

/** Component dimensions for completion feedback (not design-system spacing tokens). */
private object CompletionDimens {
    /** Container box; at least the 48dp minimum touch target for the enclosing control. */
    val Box: Dp = 48.dp
    val Icon: Dp = 24.dp
    val Burst: Dp = 64.dp
}

/** Peak scale of the single completion pulse. */
private const val COMPLETION_PULSE_SCALE = 1.2f
