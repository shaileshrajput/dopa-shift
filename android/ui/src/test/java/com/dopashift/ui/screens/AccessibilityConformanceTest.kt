package com.dopashift.ui.screens

// Feature: android-ui-upgrade, Task 12.2
// Tags: AUI-8.3, AUI-8.5, AUI-8.6 (satisfies AUI-9.9)
//
// Accessibility-conformance scan for the seven DopaShift Kinetic screens (AUI-1..AUI-7) and their
// shared Kinetic components. This is the automated equivalent of running the Accessibility Scanner
// on each screen: a physical-device scanner is unavailable in this environment, so the two
// conformance guarantees are asserted directly against the Compose semantics tree under Robolectric,
// matching the established `:ui` Compose-UI-test convention (createComposeRule under
// RobolectricTestRunner; testOptions.unitTests.isIncludeAndroidResources resolves stringResource).
//
// For every screen it renders the real composable with a representative state and then, over EVERY
// interactive node (any node exposing an OnClick, a ToggleableState, or a Selected/selectable
// action), asserts:
//   * AUI-8.3 — the node's hit area measures at least 48dp x 48dp (100% touch-target conformance).
//   * AUI-8.5 — the node exposes a non-blank accessible name (a content description or a text label)
//               sufficient for TalkBack navigation.
// The screens externalize every user-facing string through localized resources (AUI-8.6, task 12.1),
// so the accessible names asserted here are the resolved localized strings, not hardcoded copy.

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.render.DailyBarCategory
import com.dopashift.ui.state.AccentSwatch
import com.dopashift.ui.state.AnchorHabitUi
import com.dopashift.ui.state.AnalyticsUiState
import com.dopashift.ui.state.AppLimitsUiState
import com.dopashift.ui.state.AvatarUi
import com.dopashift.ui.state.DailyBarUi
import com.dopashift.ui.state.DashboardUiState
import com.dopashift.ui.state.DateRange
import com.dopashift.ui.state.GoalCardUi
import com.dopashift.ui.state.GoalDetailUiState
import com.dopashift.ui.state.GoalsUiState
import com.dopashift.ui.state.InstalledAppUi
import com.dopashift.ui.state.IntervalPreset
import com.dopashift.ui.state.LimitType
import com.dopashift.ui.state.OverlayUiState
import com.dopashift.ui.state.RuleRowUi
import com.dopashift.ui.state.SettingsUiState
import com.dopashift.ui.state.SummaryMetrics
import com.dopashift.ui.state.TodoRowUi
import com.dopashift.ui.state.UsageBarUi
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders each of the seven screens and asserts 100% touch-target and content-description
 * conformance across every interactive control (AUI-8.3, AUI-8.5), with all copy sourced from the
 * localized resources added in task 12.1 (AUI-8.6). Satisfies AUI-9.9.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccessibilityConformanceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        /** The 48dp x 48dp Touch_Target minimum required of every interactive control (AUI-8.3). */
        val MIN_TOUCH_TARGET = 48.dp
        val TEAL = Color(0xFF14B8A6)
    }

    // ---------------------------------------------------------------------------------------------
    // Shared conformance scan
    // ---------------------------------------------------------------------------------------------

    /**
     * Scans every interactive node in the currently-composed [screenName] and asserts each meets
     * the 48dp x 48dp touch target (AUI-8.3) and exposes a non-blank accessible name (AUI-8.5).
     *
     * An "interactive node" is any node that exposes a click action ([SemanticsActions.OnClick]) or
     * a toggleable state ([SemanticsProperties.ToggleableState]) or a selectable state
     * ([SemanticsProperties.Selected]) — i.e. the actionable controls a user (and TalkBack) can
     * operate. Purely decorative or text nodes are intentionally excluded.
     *
     * The scan is done over the **merged** semantics tree so an aggregated control (e.g. a
     * [com.dopashift.ui.components.MomentumCheckbox] that clears its children under one node) is
     * measured and named as the single control the user actually touches.
     */
    private fun assertScreenConformsToAccessibility(screenName: String) {
        val interactions = composeRule.onAllNodes(isInteractive(), useUnmergedTree = false)
        val interactiveNodes = interactions.fetchSemanticsNodes()

        assertTrue(
            "Screen '$screenName' rendered no interactive controls; the conformance scan would be " +
                "vacuous. Every screen under test hosts at least one actionable control.",
            interactiveNodes.isNotEmpty(),
        )

        // `onAllNodes(...)[index]` and `fetchSemanticsNodes()[index]` share ordering, so the node
        // scanned for its accessible name is the same node whose bounds are asserted below.
        interactiveNodes.forEachIndexed { index, node ->
            // AUI-8.5: every interactive control must carry a non-blank accessible name so TalkBack
            // can announce it — either a content description or a visible text label.
            val contentDescription = node.config
                .getOrNull(SemanticsProperties.ContentDescription)
                ?.joinToString(" ")
                ?.trim()
                .orEmpty()
            val text = node.config
                .getOrNull(SemanticsProperties.Text)
                ?.joinToString(" ") { it.text }
                ?.trim()
                .orEmpty()
            val accessibleName = if (contentDescription.isNotEmpty()) contentDescription else text

            assertTrue(
                "Screen '$screenName': interactive control #$index (id=${node.id}) exposes no " +
                    "non-blank accessible name. Every non-text interactive control and " +
                    "informational icon must carry a content description for TalkBack (AUI-8.5).",
                accessibleName.isNotEmpty(),
            )

            // AUI-8.3: the same control's hit area must measure at least 48dp x 48dp.
            interactions[index]
                .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
                .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        }
    }

    /** Matches any node that a user can operate: click, toggle, or select. */
    private fun isInteractive(): SemanticsMatcher = SemanticsMatcher(
        "is an interactive control (click / toggle / select)",
    ) { node ->
        node.config.getOrNull(SemanticsActions.OnClick) != null ||
            node.config.getOrNull(SemanticsProperties.ToggleableState) != null ||
            node.config.getOrNull(SemanticsProperties.Selected) != null
    }

    private fun setScreen(content: @Composable () -> Unit) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                content()
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // AUI-1 Home Dashboard
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aui_8_3_8_5_dashboardScreen_allInteractiveControlsConform() {
        setScreen {
            DashboardScreen(
                state = DashboardUiState(
                    greeting = "Good morning, Sam",
                    subCaption = "One small step at a time",
                    streakDays = 6,
                    efficiencyPercent = 72,
                    anchorHabit = AnchorHabitUi(
                        id = "anchor-1",
                        title = "Read 10 pages",
                        subtitle = "Learning • morning",
                        estimatedTimeLabel = "10m",
                        dayLabel = "Day 6/30",
                        completed = false,
                    ),
                    focusTodos = listOf(
                        TodoRowUi(id = "t1", text = "Draft outline", done = false, typeTag = "Focus"),
                        TodoRowUi(id = "t2", text = "Reply to email", done = true),
                    ),
                    activeGoals = listOf(
                        goalCard("g1", "Get Fit", "Fitness"),
                        goalCard("g2", "Ship App", "Career"),
                    ),
                ),
                on = object : DashboardActions {
                    override fun onAnchorHabitToggled(checked: Boolean) {}
                    override fun onQuickAddTodo(text: String) {}
                    override fun onTodoToggled(id: String, done: Boolean) {}
                    override fun onGoalClicked(id: String) {}
                    override fun onQuickCreate() {}
                    override fun onNavigate(destination: TopDestination) {}
                },
            )
        }

        assertScreenConformsToAccessibility("Home Dashboard (AUI-1)")
    }

    // ---------------------------------------------------------------------------------------------
    // AUI-2 Intercept Overlay
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aui_8_3_8_5_interceptOverlayScreen_allInteractiveControlsConform() {
        setScreen {
            InterceptOverlayScreen(
                state = OverlayUiState(
                    targetAppName = "Instagram",
                    cooldownSeconds = 0, // zero so the enabled Continue button is also scanned
                    continueEnabled = true,
                    rescueHabit = AnchorHabitUi(
                        id = "habit-1",
                        title = "Read one page",
                        subtitle = "Learning",
                        estimatedTimeLabel = "2m",
                        dayLabel = "Day 6/30",
                        completed = false,
                        rewardPrompt = "Momentum builds.",
                    ),
                    pendingTasks = listOf(
                        TodoRowUi(id = "t1", text = "Draft outline", done = false, estimatedTimeLabel = "15m"),
                        TodoRowUi(id = "t2", text = "Stretch break", done = false, estimatedTimeLabel = "5m"),
                    ),
                ),
                on = OverlayActions(),
            )
        }

        assertScreenConformsToAccessibility("Intercept Overlay (AUI-2)")
    }

    // ---------------------------------------------------------------------------------------------
    // AUI-3 App Limits & Rules
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aui_8_3_8_5_appLimitsScreen_allInteractiveControlsConform() {
        setScreen {
            AppLimitsScreen(
                state = AppLimitsUiState(
                    query = "soc",
                    installedApps = listOf(
                        InstalledAppUi("com.example.social", "Social", selected = true),
                        InstalledAppUi("com.example.video", "Video", selected = false),
                    ),
                    limitType = LimitType.REPETITIVE, // exercises the interval preset chips too
                    intervalPreset = IntervalPreset("30 min", 30, selected = true),
                    intervalPresets = listOf(
                        IntervalPreset("15 min", 15),
                        IntervalPreset("30 min", 30, selected = true),
                        IntervalPreset("45 min", 45),
                        IntervalPreset("60 min", 60),
                    ),
                    activeRules = listOf(
                        RuleRowUi(
                            id = "rule-1",
                            appLabel = "Social",
                            packageName = "com.example.social",
                            ruleTypeLabel = "Repetitive: Every 30m • 3 loops",
                            paused = false,
                        ),
                        RuleRowUi(
                            id = "rule-2",
                            appLabel = "Video",
                            packageName = "com.example.video",
                            ruleTypeLabel = "Daily limit: 30m",
                            paused = true,
                        ),
                    ),
                ),
                on = AppLimitsActions(
                    onBack = {},
                    onQueryChange = {},
                    onClearQuery = {},
                    onAppSelected = { _, _ -> },
                    onLimitTypeChange = {},
                    onIntervalPresetSelected = {},
                    onDailyLimitChange = { _, _ -> },
                    onCustomIntervalChange = {},
                    onSave = {},
                    onRuleEdit = {},
                    onRulePauseToggle = {},
                    onRuleDelete = {},
                ),
            )
        }

        assertScreenConformsToAccessibility("App Limits & Rules (AUI-3)")
    }

    // ---------------------------------------------------------------------------------------------
    // AUI-4 Goals & Habits
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aui_8_3_8_5_goalsHabitsScreen_allInteractiveControlsConform() {
        setScreen {
            GoalsHabitsScreen(
                state = GoalsUiState(
                    goals = listOf(
                        goalCard("g1", "Get Fit", "Fitness"),
                        goalCard("g2", "Ship App", "Career"),
                    ),
                ),
                on = object : GoalsActions {
                    override fun onGoalClicked(id: String) {}
                    override fun onQuickCreate() {}
                    override fun onNavigate(destination: TopDestination) {}
                },
            )
        }

        assertScreenConformsToAccessibility("Goals & Habits (AUI-4)")
    }

    // ---------------------------------------------------------------------------------------------
    // AUI-5 Goal Detail & Roadmap
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aui_8_3_8_5_goalDetailScreen_allInteractiveControlsConform() {
        setScreen {
            GoalDetailScreen(
                state = GoalDetailUiState(
                    goalId = "g1",
                    title = "Ship the side project",
                    category = "Business",
                    keywords = listOf("startup", "mvp", "launch"),
                    checklist = listOf(
                        TodoRowUi(id = "1", text = "Draft landing page", done = true, deletable = true),
                        TodoRowUi(id = "2", text = "Wire up payments", done = false, deletable = true),
                    ),
                    roadmapDayLabel = "Day 6/30",
                    roadmapActive = true,
                    roadmapCompletedDays = 6,
                    roadmapSubCaption = "6 of 30 days completed",
                ),
                on = GoalDetailActions(
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                    onChecklistItemToggled = { _, _ -> },
                    onChecklistItemDeleted = {},
                    onAddChecklistItem = {},
                ),
            )
        }

        assertScreenConformsToAccessibility("Goal Detail & Roadmap (AUI-5)")
    }

    // ---------------------------------------------------------------------------------------------
    // AUI-6 Analytics & Efficiency
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aui_8_3_8_5_analyticsScreen_allInteractiveControlsConform() {
        setScreen {
            AnalyticsScreen(
                state = AnalyticsUiState(
                    range = DateRange.LAST_7,
                    offlineOrPartial = true,
                    summary = SummaryMetrics(
                        averagePercent = 72,
                        highestPercent = 94,
                        lowestPercent = 41,
                        daysTracked = 7,
                    ),
                    dailyBars = listOf(
                        DailyBarUi("Mon", 80, DailyBarCategory.PRODUCTIVE),
                        DailyBarUi("Tue", 55, DailyBarCategory.DISTRACTING),
                        DailyBarUi("Wed", 0, DailyBarCategory.NO_DATA),
                    ),
                    categoryUsage = listOf(
                        UsageBarUi("Social", "1h 20m", 0.35f, "35%", Color(0xFFEF4444)),
                        UsageBarUi("Learning", "2h 05m", 0.55f, "55%", TEAL),
                    ),
                ),
                on = AnalyticsActions(
                    onRangeChange = {},
                    onNavigate = {},
                ),
            )
        }

        assertScreenConformsToAccessibility("Analytics & Efficiency (AUI-6)")
    }

    // ---------------------------------------------------------------------------------------------
    // AUI-7 Settings & Profile
    // ---------------------------------------------------------------------------------------------

    @Test
    fun aui_8_3_8_5_settingsScreen_allInteractiveControlsConform() {
        val swatches = List(12) { index ->
            AccentSwatch(
                id = "swatch_$index",
                label = "Accent $index",
                color = if (index % 2 == 0) TEAL else Color(0xFF2563EB),
                accentSeed = index,
            )
        }
        setScreen {
            SettingsScreen(
                state = SettingsUiState(
                    avatar = AvatarUi(imageUri = null, monogram = "DS"),
                    fullName = "Dopa User",
                    displayName = "Dopa User",
                    accentSwatches = swatches,
                    selectedAccent = swatches.first(),
                ),
                on = SettingsActions(
                    onUploadPhoto = {},
                    onDisplayNameChange = {},
                    onSaveDisplayName = {},
                    onManageAppLimits = {},
                    onAccentSelected = {},
                    onChangePassword = { _, _, _ -> },
                    onNavigate = {},
                ),
            )
        }

        assertScreenConformsToAccessibility("Settings & Profile (AUI-7)")
    }

    // ---------------------------------------------------------------------------------------------
    // Test data helpers
    // ---------------------------------------------------------------------------------------------

    private fun goalCard(id: String, title: String, category: String) = GoalCardUi(
        id = id,
        title = title,
        category = category,
        completionPercent = 40,
        pendingTaskLabel = "1/2 tasks pending",
        keywords = listOf("focus", "daily"),
        accentColor = TEAL,
        streakCompletedDays = 6,
        streakTotalDays = 30,
        roadmapStatusLabel = "Day 6/30",
    )
}
