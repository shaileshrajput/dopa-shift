package com.dopashift.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dopashift.domain.presentation.MotionSpec

/**
 * DopaShift Kinetic Design System — Motion tokens (DUX-2 AC1).
 *
 * Exposes exactly four [MotionSpec] values forming the app-wide motion scale. Every animation
 * in the UI must reference one of these named specs rather than a raw `tween`/duration literal,
 * so the motion vocabulary stays consistent and enforceable (see the DUX-2.1 static-analysis gate).
 *
 * Each spec's `easingName` is the token name resolved to a concrete Material 3 [Easing] curve by
 * [easingFor]. The [MotionSpec] type itself lives in `domain` and carries no Compose dependency;
 * this object is the UI-layer bridge that pairs each spec with its Compose easing.
 */
object MotionTokens {

    /**
     * `motion-instant` — 100 ms, linear. Immediate, near-instant feedback; also the upper bound
     * that decorative motion collapses to under the OS reduced-motion setting.
     */
    val Instant: MotionSpec = MotionSpec(durationMillis = 100, easingName = EASING_LINEAR)

    /**
     * `motion-quick` — 200 ms, fast-out-slow-in. Small, frequent transitions such as completion
     * feedback and toggles.
     */
    val Quick: MotionSpec = MotionSpec(durationMillis = 200, easingName = EASING_FAST_OUT_SLOW_IN)

    /**
     * `motion-standard` — 300 ms, fast-out-slow-in. The default duration for most content
     * transitions.
     */
    val Standard: MotionSpec = MotionSpec(durationMillis = 300, easingName = EASING_FAST_OUT_SLOW_IN)

    /**
     * `motion-emphasized` — 450 ms, emphasized. Expressive, attention-drawing transitions such as
     * progress-ring value animations.
     */
    val Emphasized: MotionSpec = MotionSpec(durationMillis = 450, easingName = EASING_EMPHASIZED)

    /**
     * All four motion specs, in ascending-duration order. Useful for exhaustive iteration in
     * tests and tooling; the app should reference the named specs directly.
     */
    val all: List<MotionSpec> = listOf(Instant, Quick, Standard, Emphasized)

    /**
     * Resolves a [MotionSpec.easingName] token to its concrete Material 3 Compose [Easing] curve.
     *
     * @param easingName one of the token names carried by the four [MotionTokens] specs
     * @return the matching Compose [Easing]
     * @throws IllegalArgumentException if [easingName] is not a recognized motion easing token
     */
    fun easingFor(easingName: String): Easing = when (easingName) {
        EASING_LINEAR -> LinearEasing
        EASING_FAST_OUT_SLOW_IN -> FastOutSlowInEasing
        EASING_EMPHASIZED -> EmphasizedEasing
        else -> throw IllegalArgumentException("Unknown motion easing token: $easingName")
    }

    // === Easing token names (paired with the specs above) ===

    private const val EASING_LINEAR = "linear"
    private const val EASING_FAST_OUT_SLOW_IN = "fastOutSlowIn"
    private const val EASING_EMPHASIZED = "emphasized"

    /**
     * Material 3 "emphasized" easing curve. Compose BOM 2024.02.00 does not expose a public
     * top-level constant for this curve, so it is defined here from Material 3's published
     * emphasized-decelerate control points (0.2, 0.0, 0.0, 1.0).
     */
    private val EmphasizedEasing: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
}

/**
 * Reads the OS reduced-motion accessibility setting so callers can feed it to
 * [com.dopashift.domain.presentation.MotionPolicy.effective] (DUX-2 AC9, DUX-7 AC6).
 *
 * Android exposes "Remove animations" as [Settings.Global.ANIMATOR_DURATION_SCALE]; a value of
 * `0` means the user has disabled animations. This returns `true` in that case so decorative
 * motion is collapsed to `motion-instant` while state changes still occur.
 *
 * @return `true` when the OS animation duration scale is `0` (reduced motion requested)
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    val animationScale = Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    )
    return animationScale == 0f
}
