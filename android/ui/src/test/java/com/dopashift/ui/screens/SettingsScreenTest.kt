package com.dopashift.ui.screens

// Feature: android-ui-upgrade, Task 10.3

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.dopashift.ui.R
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.state.AccentSwatch
import com.dopashift.ui.state.AvatarUi
import com.dopashift.ui.state.SettingsUiState
import com.dopashift.ui.theme.AccentSeed
import com.dopashift.ui.theme.DopaShiftKineticDarkColorScheme
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import com.dopashift.ui.theme.withAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI + instrumented tests for the **Settings & Profile** screen ([SettingsScreen]) and the
 * **Global Bottom Navigation** ([com.dopashift.ui.components.DopaShiftBottomNavBar]) — spec
 * `android-ui-upgrade`, task 10.3, AUI-7.1–7.6 (satisfies AUI-9.8).
 *
 * These are Compose UI tests written as **Robolectric JVM unit tests** so they run without an
 * emulator/device, mirroring the sibling [AppLimitsScreenTest] / [DashboardScreenTest]:
 * `createComposeRule` works under [RobolectricTestRunner], and the `:ui` module sets
 * `testOptions.unitTests.isIncludeAndroidResources = true` so `stringResource` lookups resolve.
 *
 * The screen is presentation-only: it renders the [SettingsUiState] it is given and forwards intent
 * through [SettingsActions]. Each test drives [SettingsScreen] with a controlled state plus lambda
 * spies and asserts the render + the dispatched callback. Content is wrapped in
 * `DopaShiftTheme(themeMode = ThemeMode.DARK)`.
 *
 * The final "instrumented" test (AUI-7.4 / AUI-9.8 — accent selection propagates across screens) is
 * an end-to-end Compose interaction: selecting a swatch drives the app-wide [AccentSeed], and the
 * test observes that two independently composed screens both read the re-tinted reward accent from
 * `MaterialTheme.colorScheme`.
 */
@RunWith(RobolectricTestRunner::class)
// A tall viewport qualifier so the whole Settings LazyColumn (a tall profile card, the accent grid,
// and the bottom security card) lays out with room to scroll; the default Robolectric device is too
// short for the bottom submit Button to settle fully in the viewport for a reliable click dispatch.
@Config(sdk = [33], qualifiers = "w411dp-h2000dp")
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun str(resId: Int): String = context.getString(resId)
    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    /** Twelve accent swatches, mirroring the palette the picker renders (AUI-7.3). */
    private fun swatches(): List<AccentSwatch> = List(ACCENT_SWATCH_COUNT) { index ->
        AccentSwatch(
            id = "swatch_$index",
            label = "Accent $index",
            color = Color(0xFF000000.toInt() or (0x00113355 * (index + 1) and 0x00FFFFFF)),
            accentSeed = index,
        )
    }

    private fun state(
        avatar: AvatarUi = AvatarUi(imageUri = null, monogram = "DS"),
        fullName: String = "Dopa User",
        displayName: String = "Dopa User",
        accentSwatches: List<AccentSwatch> = swatches(),
        selectedAccent: AccentSwatch = accentSwatches.first(),
    ) = SettingsUiState(
        avatar = avatar,
        fullName = fullName,
        displayName = displayName,
        accentSwatches = accentSwatches,
        selectedAccent = selectedAccent,
    )

    /** Renders [SettingsScreen] with all callbacks defaulting to no-ops. */
    private fun setContent(
        uiState: SettingsUiState,
        onUploadPhoto: () -> Unit = {},
        onDisplayNameChange: (String) -> Unit = {},
        onSaveDisplayName: () -> Unit = {},
        onManageAppLimits: () -> Unit = {},
        onAccentSelected: (AccentSwatch) -> Unit = {},
        onChangePassword: (String, String, String) -> Unit = { _, _, _ -> },
        onNavigate: (TopDestination) -> Unit = {},
    ) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                SettingsScreen(
                    state = uiState,
                    on = SettingsActions(
                        onUploadPhoto = onUploadPhoto,
                        onDisplayNameChange = onDisplayNameChange,
                        onSaveDisplayName = onSaveDisplayName,
                        onManageAppLimits = onManageAppLimits,
                        onAccentSelected = onAccentSelected,
                        onChangePassword = onChangePassword,
                        onNavigate = onNavigate,
                    ),
                )
            }
        }
    }

    // --- AUI-7.1: profile section — avatar, upload photo, display name + save -----------------

    @Test
    fun aui_7_1_profile_rendersMonogramAvatarUploadControlAndDisplayName() {
        setContent(state(avatar = AvatarUi(imageUri = null, monogram = "SM"), displayName = "Sam"))

        // Profile header + the monogram avatar (rendered via its content description).
        composeRule.onNodeWithText(str(R.string.aui_settings_profile_header)).assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(str(R.string.aui_settings_avatar_content_description))
            .assertIsDisplayed()

        // The Upload Photo control names the accepted formats/size (JPEG/PNG <= 5MB) in its hint.
        composeRule.onNodeWithText(str(R.string.aui_settings_upload_photo)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.aui_settings_upload_photo_hint)).assertIsDisplayed()

        // The display-name field renders the current value.
        composeRule.onNodeWithText("Sam").assertIsDisplayed()
    }

    @Test
    fun aui_7_1_profile_uploadPhotoTapInvokesCallback() {
        var uploaded = false
        setContent(state(), onUploadPhoto = { uploaded = true })

        composeRule.onNodeWithText(str(R.string.aui_settings_upload_photo)).performClick()

        assertEquals("Upload Photo must dispatch onUploadPhoto", true, uploaded)
    }

    @Test
    fun aui_7_1_profile_editingDisplayNameInvokesOnDisplayNameChangeAndSaveDispatches() {
        var typed: String? = null
        var saved = false
        setContent(
            state(displayName = "Sam"),
            onDisplayNameChange = { typed = it },
            onSaveDisplayName = { saved = true },
        )

        // Typing appends to the current field value, forwarding through onDisplayNameChange.
        composeRule.onNodeWithText("Sam").performTextInput("!")
        assertNotEquals("editing the display name must invoke onDisplayNameChange", null, typed)

        // Tapping Save dispatches onSaveDisplayName.
        composeRule.onNodeWithText(str(R.string.aui_settings_save_display_name)).performClick()
        assertEquals("Save must dispatch onSaveDisplayName", true, saved)
    }

    // --- AUI-7.2: "Manage App Limits" navigation card -----------------------------------------

    @Test
    fun aui_7_2_manageAppLimitsCard_rendersTitleSubtitleAndTapNavigates() {
        var navigated = false
        setContent(state(), onManageAppLimits = { navigated = true })

        // Scroll the manage-limits card into composition (it sits below the tall profile card).
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_settings_manage_limits_title))

        composeRule.onNodeWithText(str(R.string.aui_settings_manage_limits_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(str(R.string.aui_settings_manage_limits_subtitle))
            .assertIsDisplayed()

        // Tapping the card (exposed with its own content description) navigates to AUI-3.
        composeRule
            .onNodeWithContentDescription(str(R.string.aui_settings_manage_limits_content_description))
            .performClick()
        assertEquals("Manage App Limits card must dispatch onManageAppLimits", true, navigated)
    }

    // --- AUI-7.3: 12-swatch accent picker + dynamic label + double-ring selection -------------

    @Test
    fun aui_7_3_accentPicker_rendersTwelveSwatchesAndDynamicActiveLabel() {
        val palette = swatches()
        val selected = palette[3]
        setContent(state(accentSwatches = palette, selectedAccent = selected))

        // The accent header + a dynamic label naming the active theme are shown.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_settings_accent_header))
        composeRule.onNodeWithText(str(R.string.aui_settings_accent_header)).assertIsDisplayed()
        composeRule
            .onNodeWithText(str(R.string.aui_settings_accent_active_label, selected.label))
            .assertIsDisplayed()

        // Exactly twelve swatches are rendered, each exposing its label via a content description.
        val swatchNodes = countSwatchesWithDescription(palette)
        assertEquals("the accent picker must render exactly 12 swatches", ACCENT_SWATCH_COUNT, swatchNodes)
    }

    @Test
    fun aui_7_3_accentPicker_selectedSwatchIsAnnouncedSelected() {
        val palette = swatches()
        val selected = palette[2]
        setContent(state(accentSwatches = palette, selectedAccent = selected))

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_settings_accent_header))

        // The selected swatch is announced as selected (double-ring + checkmark indicator).
        composeRule
            .onNodeWithContentDescription(
                str(R.string.aui_settings_accent_swatch_content_description, selected.label),
            )
            .assertIsSelected()
    }

    // --- AUI-7.4: selecting a swatch dispatches onAccentSelected ------------------------------

    @Test
    fun aui_7_4_tappingSwatchInvokesOnAccentSelectedWithThatSwatch() {
        val palette = swatches()
        val selected = palette.first()
        val target = palette[5]
        var chosen: AccentSwatch? = null
        setContent(
            state(accentSwatches = palette, selectedAccent = selected),
            onAccentSelected = { chosen = it },
        )

        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_settings_accent_header))
        composeRule
            .onNodeWithContentDescription(
                str(R.string.aui_settings_accent_swatch_content_description, target.label),
            )
            .performClick()

        assertEquals("tapping a swatch must dispatch that AccentSwatch", target, chosen)
    }

    // --- AUI-7.5: Keycloak-delegated password card --------------------------------------------

    @Test
    fun aui_7_5_securityCard_rendersKeycloakNoteThreePasswordFieldsAndSubmitDispatches() {
        val submitted = mutableListOf<Triple<String, String, String>>()
        setContent(state(), onChangePassword = { c, n, cf -> submitted += Triple(c, n, cf) })

        // Scroll the security card into composition (it sits at the bottom of the LazyColumn).
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_settings_security_header))

        composeRule.onNodeWithText(str(R.string.aui_settings_security_header)).assertIsDisplayed()
        // The card states password change is managed through Keycloak.
        composeRule
            .onNodeWithText(str(R.string.aui_settings_security_keycloak_note))
            .assertIsDisplayed()

        // Current / New / Confirm inputs are all present (asserted via their labels).
        composeRule.onNodeWithText(str(R.string.aui_settings_password_current_label)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.aui_settings_password_new_label)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.aui_settings_password_confirm_label)).assertIsDisplayed()

        // Submitting forwards the three inputs to the host (which drives the Keycloak flow). The
        // submit Button sits at the bottom of the LazyColumn, so first scroll it into the viewport
        // via the lazy container, then click the Button node (scoped with hasClickAction so the tap
        // lands on the Button itself, not a bare Text child). performClick dispatches at the node
        // center; the scroll guarantees the button is fully in view so the click reaches onClick.
        composeRule.onNode(hasScrollAction())
            .performScrollToNode(hasTextResource(R.string.aui_settings_change_password) and hasClickAction())
        composeRule.onNode(hasTextResource(R.string.aui_settings_change_password) and hasClickAction())
            .performClick()
        assertEquals("Change password must dispatch onChangePassword once", 1, submitted.size)
    }

    // --- AUI-7.6: Settings hosts the four-destination bottom nav with Settings active ---------

    @Test
    fun aui_7_6_bottomNav_rendersFourDestinationsWithSettingsActive() {
        val navTargets = mutableListOf<TopDestination>()
        setContent(state(), onNavigate = { navTargets += it })

        // The bottom nav renders exactly four destinations (Home, Goals, Analytics, Settings). Each
        // item is a `selectable` carrying Role.Tab; the active one additionally shows its bold label
        // while the inactive items render icon-only. Scope the count to the Tab role so the assertion
        // targets exactly the nav destinations and not the accent-picker swatches (which are also
        // selectable, but carry Role.RadioButton).
        val tabCount = composeRule.onAllNodes(isNavTab()).fetchSemanticsNodes().size
        assertEquals("the bottom nav must render exactly four destinations", 4, tabCount)

        // Settings is the active destination: its tab shows the label and is selected. ("Settings"
        // also appears as the screen title, so scope to the nav tab carrying that text.)
        composeRule.onNode(hasTextResource(R.string.nav_settings) and isNavTab())
            .assertIsDisplayed()
            .assertIsSelected()

        // Selecting a currently-inactive destination dispatches onNavigate. Tap the first
        // not-selected tab; the bar always forwards the chosen destination to the caller.
        composeRule.onAllNodes(isNavTab() and isNotSelected())[0].performClick()
        assertEquals("selecting an inactive destination must dispatch exactly one onNavigate", 1, navTargets.size)
        assertNotEquals(
            "selecting an inactive destination must not re-dispatch the active one",
            TopDestination.SETTINGS,
            navTargets.first(),
        )
    }

    // --- AUI-7.4 / AUI-9.8 (instrumented): accent selection propagates across screens ---------

    @Test
    fun aui_7_4_accentSelection_propagatesAcrossScreens() {
        // A red accent swatch distinct from the Momentum Teal system default.
        val redAccent = Color(0xFFEF4444)
        val redSeed = redAccent.toArgb()

        // Baseline: with the default (Momentum Teal) accent, the reward roles equal the base
        // scheme's roles on every screen.
        val defaultReward = DopaShiftKineticDarkColorScheme.secondary.toArgb()

        // Two independently composed "screens" (A and B) share one app-wide accent selection. When
        // a swatch is selected, BOTH screens re-read the same re-tinted reward accent from
        // MaterialTheme.colorScheme — i.e. the selection propagated app-wide (AUI-7.4, AUI-9.8).
        val screenAReward = intArrayOf(0)
        val screenBReward = intArrayOf(0)

        composeRule.setContent {
            // The single source of truth a Settings swatch tap would drive.
            var accent by remember { mutableStateOf(AccentSeed.Default) }

            // Screen A — reads the reward accent (secondary) from the themed color scheme.
            DopaShiftTheme(accent = accent) {
                screenAReward[0] = MaterialTheme.colorScheme.secondary.toArgb()
                Text(
                    text = "select-red",
                    modifier = Modifier.clickable { accent = AccentSeed(redAccent) },
                )
            }
            // Screen B — an independently composed screen under the same accent selection.
            DopaShiftTheme(accent = accent) {
                screenBReward[0] = MaterialTheme.colorScheme.secondary.toArgb()
                Text("screen-b")
            }
        }

        composeRule.onNodeWithText("screen-b").assertExists()
        // Before selection both screens sit at the default reward accent.
        assertEquals("screen A starts at the default reward accent", defaultReward, screenAReward[0])
        assertEquals("screen B starts at the default reward accent", defaultReward, screenBReward[0])

        // Simulate the Settings swatch selection.
        composeRule.onNodeWithText("select-red").performClick()
        composeRule.waitForIdle()

        // The selected accent propagated to BOTH independently composed screens.
        assertEquals("screen A must re-tint to the selected accent", redSeed, screenAReward[0])
        assertEquals("screen B must re-tint to the selected accent", redSeed, screenBReward[0])
        assertNotEquals("the selection must actually change the reward accent", defaultReward, screenAReward[0])

        // Sanity: the composed propagation matches the pure withAccent derivation (Property 7 path).
        val expected = DopaShiftKineticDarkColorScheme.withAccent(AccentSeed(redAccent)).secondary.toArgb()
        assertEquals("composed accent must match the withAccent derivation", expected, screenAReward[0])
    }

    /** Matcher on a resolved string resource, used with `performScrollToNode`. */
    private fun hasTextResource(resId: Int) =
        androidx.compose.ui.test.hasText(str(resId))

    /**
     * Matcher selecting the bottom-nav destination tabs specifically. The nav items are `selectable`
     * with [Role.Tab]; the accent-picker swatches are also selectable but carry [Role.RadioButton],
     * so matching on the Tab role isolates exactly the four navigation destinations regardless of
     * whether the swatch grid is also composed in the viewport.
     */
    private fun isNavTab(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /** Counts how many of [palette]'s swatch content descriptions are present in the tree. */
    private fun countSwatchesWithDescription(palette: List<AccentSwatch>): Int =
        palette.count { swatch ->
            composeRule
                .onAllNodesWithContentDescription(
                    str(R.string.aui_settings_accent_swatch_content_description, swatch.label),
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
}

/** The expected count of accent swatches in the picker palette (AUI-7.3). */
private const val ACCENT_SWATCH_COUNT = 12
