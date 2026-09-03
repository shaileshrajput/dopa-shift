package com.dopashift.ui.adaptive

// Feature: dynamic-ui-experience, Task 8.3
// Validates: Requirements DUX-1.3, DUX-1.7, DUX-4.9

import android.graphics.Rect
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI tests for [AdaptiveScaffold] reconfiguration, foldable-hinge, and edge-to-edge
 * inset behaviour.
 *
 * Run under [RobolectricTestRunner] with [createComposeRule] so they execute on the
 * JVM without an emulator, matching the pattern established by `DopaShiftThemeUiTest`.
 * Kept in a DISTINCT file from the task 8.2 size-class parameterized suite in the same
 * `com.dopashift.ui.adaptive` package.
 *
 * Coverage:
 *  - Foldable hinge occlusion (DUX-1.7): [hingeOcclusionWidth] reserves the hinge width
 *    as padding for a vertical, full-occlusion fold and returns `0.dp` for null /
 *    non-separating / horizontal folds. A [FakeFoldingFeature] stub drives the function
 *    directly since `FoldingFeature` is an androidx.window.layout interface.
 *  - Reconfiguration state preservation (DUX-1.3): a `rememberSaveable` value held by the
 *    scaffold content survives a `WindowSizeClass` change (a simulated reconfiguration).
 *  - Edge-to-edge insets (DUX-4.9): the content lambda receives a non-null [PaddingValues]
 *    that includes the configured screen margin.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AdaptiveScaffoldReconfigFoldInsetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * A test [FoldingFeature] that returns configured [bounds], [orientation], and
     * [occlusionType]. `FoldingFeature` is an interface from androidx.window.layout, so a
     * stub is required to exercise [hingeOcclusionWidth] without a real device fold.
     */
    private class FakeFoldingFeature(
        private val boundsRect: Rect,
        private val featureOrientation: FoldingFeature.Orientation,
        private val featureOcclusionType: FoldingFeature.OcclusionType,
        private val featureState: FoldingFeature.State = FoldingFeature.State.HALF_OPENED,
    ) : FoldingFeature {
        override val bounds: Rect get() = boundsRect
        override val isSeparating: Boolean
            get() = featureOcclusionType == FoldingFeature.OcclusionType.FULL
        override val occlusionType: FoldingFeature.OcclusionType get() = featureOcclusionType
        override val orientation: FoldingFeature.Orientation get() = featureOrientation
        override val state: FoldingFeature.State get() = featureState
    }

    // --- Foldable hinge occlusion (DUX-1.7) --------------------------------

    @Test
    fun hingeOcclusion_verticalFullFold_reservesBoundsWidthAsPadding() {
        // A vertical (book-posture) fold that fully occludes a 48px-wide hinge region.
        val hingeWidthPx = 48
        val fold = FakeFoldingFeature(
            boundsRect = Rect(/* left = */ 100, /* top = */ 0, /* right = */ 100 + hingeWidthPx, /* bottom = */ 800),
            featureOrientation = FoldingFeature.Orientation.VERTICAL,
            featureOcclusionType = FoldingFeature.OcclusionType.FULL,
        )

        assertEquals(
            "vertical full fold must reserve the hinge bounds width as padding",
            hingeWidthPx.dp,
            hingeOcclusionWidth(fold),
        )
    }

    @Test
    fun hingeOcclusion_nullFold_returnsZero() {
        assertEquals(
            "no fold must reserve zero hinge padding",
            0.dp,
            hingeOcclusionWidth(null),
        )
    }

    @Test
    fun hingeOcclusion_nonSeparatingFold_returnsZero() {
        // A vertical fold whose occlusion type is NONE (does not fully occlude) reserves nothing.
        val fold = FakeFoldingFeature(
            boundsRect = Rect(100, 0, 148, 800),
            featureOrientation = FoldingFeature.Orientation.VERTICAL,
            featureOcclusionType = FoldingFeature.OcclusionType.NONE,
        )

        assertEquals(
            "non-occluding fold must reserve zero hinge padding",
            0.dp,
            hingeOcclusionWidth(fold),
        )
    }

    @Test
    fun hingeOcclusion_horizontalFullFold_returnsZero() {
        // A horizontal (tabletop) fold occupies vertical space, not a horizontal hinge column,
        // so hingeOcclusionWidth (which reserves horizontal padding) returns zero.
        val fold = FakeFoldingFeature(
            boundsRect = Rect(0, 400, 800, 448),
            featureOrientation = FoldingFeature.Orientation.HORIZONTAL,
            featureOcclusionType = FoldingFeature.OcclusionType.FULL,
        )

        assertEquals(
            "horizontal fold must reserve zero horizontal hinge padding",
            0.dp,
            hingeOcclusionWidth(fold),
        )
    }

    // --- Reconfiguration state preservation (DUX-1.3) ----------------------

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun reconfiguration_preservesSaveableStateAcrossSizeClassChange() {
        val observedCounter = intArrayOf(-1)

        composeRule.setContent {
            // Drive the size class via mutableStateOf so we can simulate a
            // configuration change (Compact -> Expanded) in-place, no restart.
            var size by remember { mutableStateOf(DpSize(400.dp, 800.dp)) }
            val windowSizeClass = WindowSizeClass.calculateFromSize(size)

            AdaptiveScaffold(
                windowSizeClass = windowSizeClass,
                selected = TopDestination.HOME,
                onSelect = {},
                foldingFeature = null,
            ) { _, _ ->
                // rememberSaveable value that must survive the size-class change.
                var counter by rememberSaveable { mutableStateOf(0) }
                observedCounter[0] = counter
                Column {
                    Text("count-$counter")
                    Text(
                        text = "increment",
                        modifier = Modifier.clickable { counter++ },
                    )
                    Text(
                        text = "resize",
                        modifier = Modifier.clickable { size = DpSize(1200.dp, 900.dp) },
                    )
                }
            }
        }

        // Mutate the saveable state on the Compact layout.
        composeRule.onNodeWithText("increment").performClick()
        composeRule.onNodeWithText("increment").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("count-2").assertExists()

        // Simulate a reconfiguration: switch the window size class (Compact -> Expanded).
        composeRule.onNodeWithText("resize").performClick()
        composeRule.waitForIdle()

        // The saveable value is retained across the size-class change (no state loss).
        composeRule.onNodeWithText("count-2").assertExists()
        assertEquals(
            "rememberSaveable state must survive reconfiguration (size-class change)",
            2,
            observedCounter[0],
        )
    }

    // --- Edge-to-edge insets (DUX-4.9) -------------------------------------

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    @Test
    fun edgeToEdge_contentReceivesNonNullPaddingIncludingCompactMargin() {
        val capturedPadding = arrayOfNulls<PaddingValues>(1)
        val startPadding = floatArrayOf(-1f)
        val endPadding = floatArrayOf(-1f)

        composeRule.setContent {
            // Compact window: the scaffold applies the 24dp screen margin (DUX-1.5)
            // merged with WindowInsets.safeDrawing (edge-to-edge, DUX-4.9).
            val windowSizeClass = WindowSizeClass.calculateFromSize(DpSize(400.dp, 800.dp))
            AdaptiveScaffold(
                windowSizeClass = windowSizeClass,
                selected = TopDestination.HOME,
                onSelect = {},
                foldingFeature = null,
            ) { _, contentPadding ->
                capturedPadding[0] = contentPadding
                startPadding[0] = contentPadding.calculateStartPadding(LayoutDirection.Ltr).value
                endPadding[0] = contentPadding.calculateEndPadding(LayoutDirection.Ltr).value
                Text("scaffold-content")
            }
        }

        composeRule.onNodeWithText("scaffold-content").assertExists()

        // The content lambda received a non-null PaddingValues.
        assertNotNull("AdaptiveScaffold must pass a non-null contentPadding", capturedPadding[0])

        // The 24dp Compact margin is present in the content padding (>= configured margin);
        // system-bar insets are >= 0 so the merged padding never drops below the margin.
        assertTrue(
            "start content padding must include the >= 24dp Compact margin, was ${startPadding[0]}",
            startPadding[0] >= 24f,
        )
        assertTrue(
            "end content padding must include the >= 24dp Compact margin, was ${endPadding[0]}",
            endPadding[0] >= 24f,
        )
    }
}
