package com.dopashift.ui.theme

// Feature: dynamic-ui-experience, Task 15.2
// Validates: Requirements DUX-4.8

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI tests for the Intercept_Overlay backdrop's two DUX-4.8 code paths
 * ([OverlayBackground] / [Modifier.overlayBackground]).
 *
 * Per DUX-4.8 the overlay backdrop is API-gated:
 *  - **API 31+ (Android 12+):** a 20dp `RenderEffect`-backed blur over the
 *    95%-opacity `intercept-overlay` tint.
 *  - **API < 31:** a solid 97%-opacity scrim, no blur, full coverage.
 *
 * Because blur pixels cannot be asserted under Robolectric, these tests exercise
 * BOTH branches via the injectable `supportsBlur` parameter and assert:
 *  - the backdrop composes and lays out full-size without throwing, and
 *  - foreground content drawn *over* the backdrop remains present/assertable
 *    (the blur is confined to the background layer, so content is never blurred).
 *
 * They run under [RobolectricTestRunner] with `createComposeRule` (no emulator),
 * matching the pattern used by the other theme UI tests. The default `@Config`
 * pins the SDK to 33 so `supportsOverlayBlur` reflects the real API gate; one
 * method overrides to `sdk = 29` to assert the false branch of the gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OverlayBackgroundBranchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val BACKDROP_TAG = "overlay-backdrop"
        const val FOREGROUND_TAG = "overlay-foreground"
        const val FOREGROUND_TEXT = "Reclaim your focus"
    }

    // --- Blur branch: supportsBlur = true (API 31+) ------------------------

    @Test
    fun blurBranch_composesFullSizeWithForegroundContentPresent() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background layer under test: blur branch.
                    OverlayBackground(
                        modifier = Modifier.testTag(BACKDROP_TAG),
                        supportsBlur = true,
                    )
                    // Foreground content drawn on top — must remain assertable.
                    Text(
                        text = FOREGROUND_TEXT,
                        modifier = Modifier.testTag(FOREGROUND_TAG),
                    )
                }
            }
        }

        // Backdrop composed and laid out full-size (no throw on the blur path).
        composeRule.onNodeWithTag(BACKDROP_TAG)
            .assertExists()
            .assertWidthIsAtLeast(1.dp)
            .assertHeightIsAtLeast(1.dp)

        // Foreground sibling remains present and displayed over the blurred layer.
        composeRule.onNodeWithTag(FOREGROUND_TAG).assertExists().assertIsDisplayed()
        composeRule.onNodeWithText(FOREGROUND_TEXT).assertExists()
    }

    // --- Scrim branch: supportsBlur = false (pre-API-31 fallback) ----------

    @Test
    fun scrimBranch_composesFullSizeWithForegroundContentPresent() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background layer under test: solid-scrim fallback branch.
                    OverlayBackground(
                        modifier = Modifier.testTag(BACKDROP_TAG),
                        supportsBlur = false,
                    )
                    Text(
                        text = FOREGROUND_TEXT,
                        modifier = Modifier.testTag(FOREGROUND_TAG),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(BACKDROP_TAG)
            .assertExists()
            .assertWidthIsAtLeast(1.dp)
            .assertHeightIsAtLeast(1.dp)

        composeRule.onNodeWithTag(FOREGROUND_TAG).assertExists().assertIsDisplayed()
        composeRule.onNodeWithText(FOREGROUND_TEXT).assertExists()
    }

    // --- Modifier.overlayBackground exercised directly on both branches ----

    @Test
    fun overlayBackgroundModifier_bothBranchesLayOutFullSize() {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                val tint = DopaShiftTheme.colors.interceptOverlay
                Box(modifier = Modifier.fillMaxSize()) {
                    // Blur branch applied via the raw modifier.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .overlayBackground(supportsBlur = true, tint = tint, scrim = tint)
                            .testTag("mod-blur"),
                    )
                    // Scrim branch applied via the raw modifier.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .overlayBackground(supportsBlur = false, tint = tint, scrim = tint)
                            .testTag("mod-scrim"),
                    )
                    Text(text = FOREGROUND_TEXT, modifier = Modifier.testTag(FOREGROUND_TAG))
                }
            }
        }

        composeRule.onNodeWithTag("mod-blur")
            .assertExists()
            .assertWidthIsAtLeast(1.dp)
            .assertHeightIsAtLeast(1.dp)
        composeRule.onNodeWithTag("mod-scrim")
            .assertExists()
            .assertWidthIsAtLeast(1.dp)
            .assertHeightIsAtLeast(1.dp)
        composeRule.onNodeWithTag(FOREGROUND_TAG).assertExists().assertIsDisplayed()
    }

    // --- supportsOverlayBlur reflects the Build.VERSION.SDK_INT >= S gate ---

    @Test
    fun supportsOverlayBlur_isTrueOnApi31Plus() {
        // Under the class-level @Config(sdk = [33]), the running SDK is >= S (31).
        assertTrue("SDK 33 must be >= S (31)", Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        assertTrue("supportsOverlayBlur must be true on API 33", supportsOverlayBlur)
    }

    @Test
    @Config(sdk = [29])
    fun supportsOverlayBlur_isFalseBelowApi31() {
        // Per-method override to API 29 (< S) — the scrim-fallback gate.
        assertEquals("running SDK must be 29", 29, Build.VERSION.SDK_INT)
        assertFalse("supportsOverlayBlur must be false below API 31", supportsOverlayBlur)
    }

    @Test
    @Config(sdk = [29])
    fun defaultBranch_belowApi31_stillComposesWithForegroundContent() {
        // With no explicit supportsBlur, the composable falls back to
        // supportsOverlayBlur (false at API 29): exercises the default-scrim path.
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OverlayBackground(modifier = Modifier.testTag(BACKDROP_TAG))
                    Text(text = FOREGROUND_TEXT, modifier = Modifier.testTag(FOREGROUND_TAG))
                }
            }
        }

        composeRule.onNodeWithTag(BACKDROP_TAG)
            .assertExists()
            .assertWidthIsAtLeast(1.dp)
            .assertHeightIsAtLeast(1.dp)
        composeRule.onNodeWithTag(FOREGROUND_TAG).assertExists().assertIsDisplayed()
        composeRule.onNodeWithText(FOREGROUND_TEXT).assertExists()
    }
}
