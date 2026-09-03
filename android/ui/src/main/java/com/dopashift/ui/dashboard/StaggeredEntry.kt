package com.dopashift.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.domain.presentation.MotionPolicy
import com.dopashift.domain.presentation.StaggerPolicy
import com.dopashift.ui.theme.MotionTokens
import com.dopashift.ui.theme.rememberReducedMotion

/**
 * Applies the staggered entry animation used when a list is rendered for the first time in a
 * session (DUX-2 AC6). Each item fades and slides up into place, with its start delayed per
 * [StaggerPolicy.entryDelayMillis] so the list appears to cascade in.
 *
 * The stagger rule is **not re-derived here** — the per-item delay comes straight from the pure
 * [StaggerPolicy] in `domain` (`index * 40ms` for `index < 8`, else `0ms`, capped so long lists
 * never produce a long entrance). This modifier only turns that delay into a Compose animation.
 *
 * The entry motion itself uses [MotionTokens.Quick] resolved through [MotionPolicy.effective] +
 * [rememberReducedMotion]. Under the OS reduced-motion setting the entry collapses to at most
 * `motion-instant` and the per-item delay is dropped, so all items appear effectively at once
 * while the content still renders (DUX-2 AC9). The animation is decorative only: the item is
 * fully laid out and interactive regardless of animation state.
 *
 * Usage: apply to each item composable in a first-render list, passing its zero-based [index]:
 * ```
 * items.forEachIndexed { i, item ->
 *     Row(modifier = Modifier.staggeredEntry(index = i)) { /* ... */ }
 * }
 * ```
 *
 * @param index zero-based position of this item in the list; must be non-negative.
 * @param enabled when `false`, the entry animation is skipped and the item renders statically
 *   (e.g. for re-renders after the first-in-session render).
 * @return a [Modifier] that animates the item's alpha and vertical offset on first composition.
 */
fun Modifier.staggeredEntry(index: Int, enabled: Boolean = true): Modifier = composed {
    require(index >= 0) { "index must be non-negative, was $index" }

    val reducedMotion = rememberReducedMotion()
    val motionSpec = MotionPolicy.effective(MotionTokens.Quick, reducedMotion)

    // Delay comes from the pure domain policy; drop it under reduced motion so items settle at once.
    val delayMillis = if (reducedMotion) 0 else StaggerPolicy.entryDelayMillis(index)

    // Progress runs 0f -> 1f: 0f = fully offset + transparent, 1f = in place + opaque.
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = motionSpec.durationMillis,
                    delayMillis = delayMillis,
                    easing = MotionTokens.easingFor(motionSpec.easingName),
                ),
            )
        } else {
            progress.snapTo(1f)
        }
    }

    val fraction = progress.value

    this
        .alpha(fraction)
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            // Slide up: start offset below the final position, settling to 0 as progress -> 1.
            val offsetY = (StaggerEntryDimens.Offset.toPx() * (1f - fraction)).toInt()
            layout(placeable.width, placeable.height) {
                placeable.placeRelative(0, offsetY)
            }
        }
}

/** Entry-animation geometry (a component dimension, not a design-system spacing token). */
private object StaggerEntryDimens {
    /** Vertical distance each item slides up over during its entry. */
    val Offset: Dp = 12.dp
}
