package com.dopashift.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.domain.presentation.DashboardSectionType
import com.dopashift.ui.R
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.MotionTokens
import com.dopashift.ui.theme.rememberReducedMotion

/**
 * Skeleton (loading placeholder) rendering for Dashboard sections and the reusable
 * skeleton-first render wrapper (DUX-3.3).
 *
 * While a section's data is still loading the Dashboard shows a non-interactive
 * [SectionSkeleton] sized to the eventual content, rendered immediately (within the
 * 100 ms budget of DUX-3.3) — never a blank screen or a full-screen blocking spinner.
 * Once the data is available [SkeletonFirst] swaps the placeholder for the real content.
 *
 * Motion: the shimmer uses the [MotionTokens] `Standard` duration and honors the OS
 * reduced-motion setting via [rememberReducedMotion]; under reduced motion the shimmer
 * collapses to a static placeholder tint so no decorative animation runs while the
 * placeholder still renders (DUX-2.9).
 *
 * This file is intentionally self-contained: it depends only on the pure domain
 * [DashboardSectionType] and the theme tokens, so it compiles independently of the
 * (parallel) [DashboardViewModel] / dashboard UI-state types.
 */

// =====================================================================================
// Skeleton-first render wrapper
// =====================================================================================

/**
 * Renders [skeleton] while [loading] is `true`, then swaps to [content] once loading
 * completes. This is the reusable "skeleton-first" primitive that screen wiring
 * (Dashboard and later task 11/12 screens) calls so a section shows a sized placeholder
 * from the first frame rather than a blank space or a spinner (DUX-3.3).
 *
 * @param loading whether the underlying data is still loading; `true` shows [skeleton].
 * @param skeleton the placeholder content shown while loading.
 * @param content the real content shown once [loading] is `false`.
 */
@Composable
fun <T> SkeletonFirst(
    loading: Boolean,
    skeleton: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (loading) {
        skeleton()
    } else {
        content()
    }
}

// =====================================================================================
// Section skeleton
// =====================================================================================

/**
 * A non-interactive placeholder for a single Dashboard section, shaped to approximate the
 * eventual content of [type] so the layout does not jump when real data arrives (DUX-3.3).
 *
 * @param type the section this skeleton stands in for; drives the placeholder shape.
 * @param modifier layout modifier applied to the skeleton container.
 */
@Composable
fun SectionSkeleton(
    type: DashboardSectionType,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val shimmerAlpha = rememberShimmerAlpha(reducedMotion)
    val loadingDescription = stringResource(R.string.dux_section_loading)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = loadingDescription },
        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.stackSm),
    ) {
        when (type) {
            DashboardSectionType.HABIT_CHECKPOINT_DUE,
            DashboardSectionType.OVERDUE_TODOS,
            DashboardSectionType.TODAY_TODOS -> ListSectionSkeleton(shimmerAlpha)

            DashboardSectionType.ACTIVE_GOALS -> CardSectionSkeleton(shimmerAlpha)

            DashboardSectionType.EFFICIENCY_TREND -> EfficiencySkeleton(shimmerAlpha)

            DashboardSectionType.ACTIVITY_FEED -> ActivityFeedSkeleton(shimmerAlpha)
        }
    }
}

// =====================================================================================
// Per-section skeleton shapes
// =====================================================================================

/** A short heading bar plus a few task-row placeholders (todos / habit checkpoints). */
@Composable
private fun ListSectionSkeleton(shimmerAlpha: Float) {
    SkeletonBlock(
        width = SkeletonSizes.HeadingWidth,
        height = SkeletonSizes.HeadingHeight,
        shimmerAlpha = shimmerAlpha,
    )
    repeat(SkeletonSizes.LIST_ROW_COUNT) {
        SkeletonBlock(
            height = SkeletonSizes.RowHeight,
            shape = RoundedCornerShape(DopaShiftTheme.shapes.medium),
            shimmerAlpha = shimmerAlpha,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** A heading bar plus a full-width card-shaped placeholder for the active goals grid. */
@Composable
private fun CardSectionSkeleton(shimmerAlpha: Float) {
    SkeletonBlock(
        width = SkeletonSizes.HeadingWidth,
        height = SkeletonSizes.HeadingHeight,
        shimmerAlpha = shimmerAlpha,
    )
    SkeletonBlock(
        height = SkeletonSizes.CardHeight,
        shape = RoundedCornerShape(DopaShiftTheme.shapes.large),
        shimmerAlpha = shimmerAlpha,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A ring placeholder beside two text lines, mirroring the efficiency-score card. */
@Composable
private fun EfficiencySkeleton(shimmerAlpha: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DopaShiftTheme.shapes.large))
            .placeholderTint(shimmerAlpha)
            .padding(DopaShiftTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.gutter),
    ) {
        Box(
            modifier = Modifier
                .size(SkeletonSizes.RingSize)
                .clip(CircleShape)
                .placeholderTint(shimmerAlpha, emphasize = true),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base),
        ) {
            SkeletonBlock(
                width = SkeletonSizes.HeadingWidth,
                height = SkeletonSizes.HeadingHeight,
                shimmerAlpha = shimmerAlpha,
                emphasize = true,
            )
            SkeletonBlock(
                width = SkeletonSizes.SubtitleWidth,
                height = SkeletonSizes.LineHeight,
                shimmerAlpha = shimmerAlpha,
                emphasize = true,
            )
        }
    }
}

/** A heading bar plus several thin line placeholders for recent activity entries. */
@Composable
private fun ActivityFeedSkeleton(shimmerAlpha: Float) {
    SkeletonBlock(
        width = SkeletonSizes.HeadingWidth,
        height = SkeletonSizes.HeadingHeight,
        shimmerAlpha = shimmerAlpha,
    )
    repeat(SkeletonSizes.ACTIVITY_ROW_COUNT) {
        SkeletonBlock(
            width = SkeletonSizes.LineWidth,
            height = SkeletonSizes.LineHeight,
            shimmerAlpha = shimmerAlpha,
        )
    }
}

// =====================================================================================
// Primitives
// =====================================================================================

/**
 * A single rounded placeholder block with the shimmer tint applied.
 *
 * @param height the block height.
 * @param width optional fixed width; when `null` the caller controls width via [modifier].
 * @param shape corner shape of the block.
 * @param shimmerAlpha current shimmer opacity from [rememberShimmerAlpha].
 * @param emphasize when `true`, uses the higher-contrast placeholder tint (for foreground shapes).
 */
@Composable
private fun SkeletonBlock(
    height: Dp,
    shimmerAlpha: Float,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    shape: RoundedCornerShape = RoundedCornerShape(DopaShiftTheme.shapes.default),
    emphasize: Boolean = false,
) {
    val sized = if (width != null) modifier.width(width) else modifier
    Box(
        modifier = sized
            .height(height)
            .clip(shape)
            .placeholderTint(shimmerAlpha, emphasize = emphasize),
    )
}

/**
 * Applies the placeholder background tint at the given [shimmerAlpha]. Uses theme surface
 * tokens only (no raw literals): a base surface-container-highest fill blended toward the
 * outline tint to produce the shimmer highlight.
 */
@Composable
private fun Modifier.placeholderTint(shimmerAlpha: Float, emphasize: Boolean = false): Modifier {
    val scheme = MaterialTheme.colorScheme
    val base = if (emphasize) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh
    val highlight = scheme.surfaceVariant
    val brush = Brush.horizontalGradient(
        colors = listOf(
            lerpColor(base, highlight, shimmerAlpha * 0.5f),
            lerpColor(base, highlight, shimmerAlpha),
            lerpColor(base, highlight, shimmerAlpha * 0.5f),
        )
    )
    return this.background(brush)
}

/**
 * Drives the shimmer opacity. When [reducedMotion] is `true` the value is fixed (a static
 * placeholder tint) so no decorative animation runs (DUX-2.9); otherwise it oscillates on an
 * infinite transition timed with the [MotionTokens] `Standard` duration.
 */
@Composable
private fun rememberShimmerAlpha(reducedMotion: Boolean): Float {
    if (reducedMotion) {
        return SkeletonSizes.STATIC_SHIMMER_ALPHA
    }
    val transition = rememberInfiniteTransition(label = "sectionSkeletonShimmer")
    val alpha by transition.animateFloat(
        initialValue = SkeletonSizes.SHIMMER_MIN_ALPHA,
        targetValue = SkeletonSizes.SHIMMER_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = MotionTokens.Standard.durationMillis,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sectionSkeletonShimmerAlpha",
    )
    return alpha
}

/** Simple linear color blend between [start] and [end] by [fraction]. */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}

/**
 * Fixed sizes for the skeleton placeholders. These are placeholder geometry (approximating the
 * eventual content), not design-system color/spacing tokens — spacing/shape/color are still
 * referenced from [DopaShiftTheme].
 */
private object SkeletonSizes {
    val HeadingWidth: Dp = 160.dp
    val HeadingHeight: Dp = 24.dp
    val SubtitleWidth: Dp = 120.dp
    val LineWidth: Dp = 220.dp
    val LineHeight: Dp = 14.dp
    val RowHeight: Dp = 56.dp
    val CardHeight: Dp = 140.dp
    val RingSize: Dp = 56.dp

    const val LIST_ROW_COUNT = 3
    const val ACTIVITY_ROW_COUNT = 4

    const val SHIMMER_MIN_ALPHA = 0.2f
    const val SHIMMER_MAX_ALPHA = 1.0f
    const val STATIC_SHIMMER_ALPHA = 0.5f
}
