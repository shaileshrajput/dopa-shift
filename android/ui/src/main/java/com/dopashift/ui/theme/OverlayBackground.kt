package com.dopashift.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Intercept_Overlay background treatment (DUX-4.8).
 *
 * The full-screen interception overlay renders its productive alternatives (pending to-dos,
 * habit checkpoint, video suggestion) over a depth-giving background. Per DUX-4.8 the background
 * is API-gated:
 *
 * - **API 31+ (Android 12+):** a [OVERLAY_BLUR_RADIUS] (20dp) blur applied to a background layer
 *   painted with the 95%-opacity `intercept-overlay` tint, using Compose's [Modifier.blur]
 *   (backed by the platform `RenderEffect` on these API levels).
 * - **API < 31:** a solid [SCRIM_ALPHA] (97%) scrim in the same navy tone, with no blur — the
 *   fallback keeps the overlay at full coverage and does not reduce legibility.
 *
 * The blur is confined to a background layer drawn *behind* the content (see [OverlayBackground]),
 * so foreground text and controls are never blurred and contrast is preserved on both branches.
 *
 * Colors are taken from theme tokens only (the `intercept-overlay` tint and the M3 `scrim`/`surface`
 * roles); no raw color literals are introduced here (DUX-4.1).
 */

/** Backdrop blur radius on API 31+ (DUX-4.8). */
val OVERLAY_BLUR_RADIUS: Dp = 20.dp

/** Solid scrim opacity used as the pre-API-31 fallback (DUX-4.8): 97%. */
const val SCRIM_ALPHA: Float = 0.97f

/**
 * Whether the running device supports the blurred-backdrop branch of DUX-4.8.
 *
 * Gated on [Build.VERSION.SDK_INT] >= [Build.VERSION_CODES.S] (API 31), matching the
 * `RenderEffect`-backed [Modifier.blur] availability. Exposed as an overridable parameter on
 * [OverlayBackground] / [Modifier.overlayBackground] so tests can exercise both branches.
 */
val supportsOverlayBlur: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Fills the receiver with the Intercept_Overlay backdrop, choosing the blur or scrim branch by
 * API level (DUX-4.8).
 *
 * This is the reusable, self-contained treatment: apply it to a full-size [Box] that sits *behind*
 * the overlay content. Because the modifier (and its blur) is scoped to the background node, the
 * content drawn on top is never blurred.
 *
 * @param supportsBlur whether to use the API 31+ blur branch; defaults to [supportsOverlayBlur].
 *   Injectable for tests so both the blur and scrim branches can be verified without an emulator.
 * @param tint the 95%-opacity `intercept-overlay` tint token (defaults to the theme token).
 * @param scrim the base color for the solid fallback scrim; defaults to the M3 `scrim` role
 *   composited over the intercept tint so the fallback stays in the same navy family at full
 *   coverage.
 */
fun Modifier.overlayBackground(
    supportsBlur: Boolean,
    tint: Color,
    scrim: Color,
): Modifier =
    if (supportsBlur) {
        // API 31+: blur the background layer, then paint the 95%-opacity intercept tint gradient.
        this
            .blur(OVERLAY_BLUR_RADIUS)
            .background(
                Brush.verticalGradient(
                    colors = listOf(tint, tint.copy(alpha = 0.98f)),
                ),
            )
    } else {
        // Pre-API-31: solid 97%-opacity scrim, no blur — full coverage, legibility preserved.
        this.background(scrim.copy(alpha = SCRIM_ALPHA))
    }

/**
 * A full-screen background layer implementing the DUX-4.8 blur/scrim treatment, intended to be
 * placed as the first child of the overlay's root [Box] so it renders behind the content.
 *
 * On API 31+ it draws a 20dp-blurred navy tint; below API 31 it draws a solid 97% scrim. The
 * default [scrim] blends the theme `scrim` role over the `intercept-overlay` tint so the fallback
 * matches the blurred branch's tone while remaining fully opaque enough for legible foreground text.
 *
 * @param modifier extra modifiers applied after [fillMaxSize] (e.g. testing/semantics tags).
 * @param supportsBlur API-branch selector; defaults to [supportsOverlayBlur]. Injectable for tests.
 * @param tint the `intercept-overlay` tint token; defaults to [DopaShiftTheme.colors].
 * @param scrim the fallback scrim base; defaults to the M3 `scrim` role over the intercept tint.
 */
@Composable
fun OverlayBackground(
    modifier: Modifier = Modifier,
    supportsBlur: Boolean = supportsOverlayBlur,
    tint: Color = DopaShiftTheme.colors.interceptOverlay,
    scrim: Color = androidx.compose.material3.MaterialTheme.colorScheme.scrim
        .compositeOver(DopaShiftTheme.colors.interceptOverlay),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .overlayBackground(supportsBlur = supportsBlur, tint = tint, scrim = scrim)
            .then(modifier),
    )
}
