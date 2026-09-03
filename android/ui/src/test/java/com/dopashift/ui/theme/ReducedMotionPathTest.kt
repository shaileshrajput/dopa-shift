package com.dopashift.ui.theme

// Feature: dynamic-ui-experience, Task 7.2 — reduced-motion path UI test

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.dopashift.domain.presentation.MotionPolicy
import com.dopashift.domain.presentation.MotionSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI-layer test for the reduced-motion path (task 7.2).
 *
 * Validates: Requirements DUX-2.9, DUX-7.6
 *
 * DUX-7.6 requires the reduced-motion path be tested by asserting **no animation duration
 * exceeds `motion-instant`** when the OS accessibility setting is enabled; DUX-2.9 requires
 * that when that setting is on, decorative animation is disabled but **state changes and
 * functionality are retained** (animation is never the sole carrier of information).
 *
 * Property 3 in the `domain` module already property-tests `MotionPolicy.effective` in
 * isolation. This test covers the piece that only exists in the UI layer and cannot run in
 * `domain`: the bridge from the real OS setting through [rememberReducedMotion] into
 * [MotionTokens] + [MotionPolicy], plus the behavioral guarantee that a composable driven by
 * that policy still reaches its target state under reduced motion.
 *
 * It runs as a **Robolectric JVM unit test** so it executes without an emulator/device.
 * `createComposeRule` works under [RobolectricTestRunner], and the `:ui` module enables
 * `testOptions.unitTests.isIncludeAndroidResources = true`. The OS reduced-motion setting is
 * simulated by writing [Settings.Global.ANIMATOR_DURATION_SCALE] on the Robolectric context,
 * which is exactly the value [rememberReducedMotion] reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReducedMotionPathTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** `motion-instant` upper bound (100 ms) for any decorative animation under reduced motion. */
    private val motionInstantMillis = MotionPolicy.MOTION_INSTANT_MILLIS

    /** Enables/disables the OS "Remove animations" setting that [rememberReducedMotion] reads. */
    private fun setAnimatorDurationScale(scale: Float) {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            scale,
        )
    }

    /** Renders a composable that captures the [rememberReducedMotion] value into [sink]. */
    private fun captureReducedMotion(sink: (Boolean) -> Unit) {
        composeRule.setContent {
            sink(rememberReducedMotion())
        }
        composeRule.waitForIdle()
    }

    // --- DUX-7.6: no effective duration exceeds motion-instant under reduced motion ---------

    @Test
    fun reducedMotionEnabled_collapsesEveryMotionTokenToAtMostInstant() {
        // OS reduced-motion ON: animation duration scale == 0.
        setAnimatorDurationScale(0f)

        var reducedMotion: Boolean? = null
        captureReducedMotion { reducedMotion = it }

        // The UI bridge must read the OS setting as "reduced motion requested".
        assertTrue(
            "rememberReducedMotion() must be true when ANIMATOR_DURATION_SCALE == 0",
            reducedMotion == true,
        )

        // For every one of the four MotionTokens specs, the effective duration the UI would
        // animate with must not exceed motion-instant (100 ms). This is the DUX-7.6 assertion.
        MotionTokens.all.forEach { spec ->
            val effective = MotionPolicy.effective(spec, reducedMotion = reducedMotion == true)
            assertTrue(
                "spec ${spec.easingName}/${spec.durationMillis}ms must collapse to <= " +
                    "$motionInstantMillis ms under reduced motion, was ${effective.durationMillis}",
                effective.durationMillis <= motionInstantMillis,
            )
        }
    }

    @Test
    fun reducedMotionDisabled_keepsBaseMotionTokenDurations() {
        // OS reduced-motion OFF: normal animation scale.
        setAnimatorDurationScale(1f)

        var reducedMotion: Boolean? = null
        captureReducedMotion { reducedMotion = it }

        assertFalse(
            "rememberReducedMotion() must be false when animations are enabled",
            reducedMotion == true,
        )

        // With motion enabled, each token keeps its base (non-collapsed) duration — confirming
        // the collapse is specific to the reduced-motion path, not applied unconditionally.
        MotionTokens.all.forEach { spec ->
            val effective = MotionPolicy.effective(spec, reducedMotion = reducedMotion == true)
            assertEquals(
                "spec ${spec.easingName} must be unchanged with motion enabled",
                spec.durationMillis,
                effective.durationMillis,
            )
        }
    }

    // --- DUX-2.9: state changes are retained even when animation is collapsed ---------------

    @Test
    fun reducedMotionEnabled_stateChangeStillReachesTarget() {
        setAnimatorDurationScale(0f)

        // A composable driven by the reduced-motion policy: it renders "before" until toggled,
        // then "after". The policy chooses the (collapsed) duration; the state change is applied
        // by the caller regardless. We assert the target state is reached — i.e. functionality is
        // retained and information is not conveyed by animation alone (DUX-2.9).
        var toggle: () -> Unit = {}

        composeRule.setContent {
            val reducedMotion = rememberReducedMotion()
            // Effective spec is collapsed but the state change below is independent of it.
            val effective: MotionSpec =
                MotionPolicy.effective(MotionTokens.Standard, reducedMotion)
            assertTrue(
                "under reduced motion the driving spec must be collapsed to <= instant",
                effective.durationMillis <= motionInstantMillis,
            )

            var shown by remember { mutableStateOf(false) }
            toggle = { shown = true }
            Box {
                Text(if (shown) TARGET_STATE else INITIAL_STATE)
            }
        }
        composeRule.waitForIdle()

        // Initial state is visible.
        composeRule.onNodeWithText(INITIAL_STATE).assertIsDisplayed()

        // Perform the state change.
        composeRule.runOnUiThread { toggle() }
        composeRule.waitForIdle()

        // Target state is reached despite reduced motion — the state change is preserved.
        composeRule.onNodeWithText(TARGET_STATE).assertIsDisplayed()
    }

    private companion object {
        const val INITIAL_STATE = "reduced-motion-initial"
        const val TARGET_STATE = "reduced-motion-target"
    }
}
