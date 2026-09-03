package com.dopashift.domain.presentation

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure WCAG 2.1 contrast math used to validate accent colors against a theme surface
 * (DUX-5 AC3, AC4). Framework-free (no Android [android.graphics.Color]) so it runs under
 * fast JVM tests.
 *
 * Colors are passed as 32-bit `ARGB` packed into a [Long] (`0xAARRGGBB`). Only the RGB
 * channels affect contrast; the alpha byte is ignored (contrast is undefined for a
 * partially transparent color, so callers should composite before measuring).
 */
object ContrastCalculator {

    /** WCAG AA contrast threshold for normal body text. */
    const val AA_BODY: Double = 4.5

    /** WCAG AA contrast threshold for large text and meaningful non-text elements. */
    const val AA_LARGE: Double = 3.0

    private const val OPAQUE_ALPHA: Long = 0xFF000000L

    /**
     * WCAG 2.1 relative-contrast ratio between [foreground] and [background].
     *
     * Computed as `(Llighter + 0.05) / (Ldarker + 0.05)` from the two relative luminances,
     * which is symmetric in its arguments and always lands in `[1.0, 21.0]`: `1.0` for two
     * colors with equal luminance and `21.0` for pure black against pure white.
     *
     * @return the contrast ratio, clamped to `[1.0, 21.0]`
     */
    fun ratio(foreground: Long, background: Long): Double {
        val lf = relativeLuminance(foreground)
        val lb = relativeLuminance(background)
        val lighter = max(lf, lb)
        val darker = min(lf, lb)
        val raw = (lighter + 0.05) / (darker + 0.05)
        // Guard against floating-point drift outside the mathematical [1.0, 21.0] range.
        return raw.coerceIn(1.0, 21.0)
    }

    /**
     * Whether the [foreground]/[background] pair meets the WCAG AA contrast threshold:
     * [AA_BODY] (4.5:1) for normal body text, or [AA_LARGE] (3:1) for large text and
     * meaningful non-text elements when [largeText] is `true` (DUX-5 AC3).
     */
    fun meetsAA(foreground: Long, background: Long, largeText: Boolean): Boolean {
        val threshold = if (largeText) AA_LARGE else AA_BODY
        // Tolerate the tiny floating-point error introduced by luminance math so a pair
        // sitting exactly on the threshold is treated as compliant.
        return ratio(foreground, background) >= threshold - THRESHOLD_EPSILON
    }

    /**
     * Nearest shade of [candidate] that meets AA against [surface] (DUX-5 AC4).
     *
     * If [candidate] already meets AA against [surface] it is returned unchanged. Otherwise
     * the candidate is blended, in small steps, toward whichever monochrome endpoint (white
     * or black) has the higher contrast against [surface]; the first blended shade that meets
     * AA is returned. Because pure white against any non-white surface and pure black against
     * any non-black surface both exceed the AA threshold, a compliant shade always exists on
     * the chosen path, so the returned color is *guaranteed* to meet AA (Property 13).
     *
     * The alpha of the returned color matches [candidate]'s alpha.
     *
     * @return an `ARGB` [Long] whose RGB meets AA against [surface] for the given [largeText]
     */
    fun nearestCompliant(candidate: Long, surface: Long, largeText: Boolean): Long {
        if (meetsAA(candidate, surface, largeText)) return candidate

        // Choose the endpoint that pulls contrast up fastest: whichever of white/black
        // contrasts more strongly with the surface.
        val target = if (ratio(WHITE, surface) >= ratio(BLACK, surface)) WHITE else BLACK

        val alpha = candidate and OPAQUE_ALPHA
        val cr = red(candidate)
        val cg = green(candidate)
        val cb = blue(candidate)
        val tr = red(target)
        val tg = green(target)
        val tb = blue(target)

        // Walk from the candidate toward the endpoint in fine steps and return the nearest
        // (smallest blend fraction) shade that satisfies AA.
        for (step in 1..BLEND_STEPS) {
            val t = step.toDouble() / BLEND_STEPS
            val r = lerpChannel(cr, tr, t)
            val g = lerpChannel(cg, tg, t)
            val b = lerpChannel(cb, tb, t)
            val shade = alpha or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()
            if (meetsAA(shade, surface, largeText)) return shade
        }

        // The endpoint itself is guaranteed compliant; return it with the original alpha.
        return alpha or (target and 0x00FFFFFFL)
    }

    /** WCAG 2.1 relative luminance of an `ARGB` color in `[0.0, 1.0]`. */
    private fun relativeLuminance(color: Long): Double {
        val r = linearize(red(color))
        val g = linearize(green(color))
        val b = linearize(blue(color))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** sRGB gamma expansion for a single 0..255 channel, per WCAG 2.1. */
    private fun linearize(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun red(color: Long): Int = ((color shr 16) and 0xFF).toInt()

    private fun green(color: Long): Int = ((color shr 8) and 0xFF).toInt()

    private fun blue(color: Long): Int = (color and 0xFF).toInt()

    private fun lerpChannel(from: Int, to: Int, t: Double): Int =
        (from + (to - from) * t).roundToInt().coerceIn(0, 255)

    private const val WHITE: Long = 0xFFFFFFFFL
    private const val BLACK: Long = 0xFF000000L

    /** Number of blend steps between the candidate and the endpoint in [nearestCompliant]. */
    private const val BLEND_STEPS: Int = 256

    /** Tolerance for treating a ratio sitting on the AA threshold as compliant. */
    private const val THRESHOLD_EPSILON: Double = 1e-9
}
