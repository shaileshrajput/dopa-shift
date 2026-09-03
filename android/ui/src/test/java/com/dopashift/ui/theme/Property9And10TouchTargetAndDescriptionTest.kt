package com.dopashift.ui.theme

// Feature: dynamic-ui-experience, Property 9: All interactive targets meet the minimum touch size
// Feature: dynamic-ui-experience, Property 10: Every non-decorative icon-only control has a content description
// Validates: Requirements DUX-5.1 (Property 9), DUX-5.2 (Property 10)

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.ui.dashboard.CompletionCheckbox
import com.dopashift.ui.dashboard.GoalCard
import com.dopashift.ui.dashboard.GoalProgressSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Compose semantics-scan property tests for the `ui` module's accessibility ergonomics
 * ([com.dopashift.ui.theme.Accessibility]) and their application to interactive dashboard
 * controls. These cover the two Compose-side accessibility correctness properties:
 *
 *  - **Property 9: All interactive targets meet the minimum touch size** (DUX-5.1) — for any
 *    generated intrinsic content size, a custom clickable surface that applies
 *    [Modifier.minTouchTarget] measures at least [A11yDimens.MinTouchTarget] (48dp) in *both*
 *    axes, and a task-style row that applies [Modifier.minTaskRowHeight] measures at least
 *    [A11yDimens.MinTaskRowHeight] (56dp) tall. Small (0..40dp) content boxes are generated and
 *    the laid-out node bounds are asserted with `assertWidthIsAtLeast` / `assertHeightIsAtLeast`.
 *
 *  - **Property 10: Every non-decorative icon-only control has a content description** (DUX-5.2)
 *    — for a batch of rendered interactive dashboard controls (clickable [GoalCard]s and
 *    [CompletionCheckbox]es) every one exposes a non-blank content description, discoverable via
 *    a semantics scan; there are no described-but-blank interactive nodes.
 *
 * Approach (Robolectric constraint: at most one `setContent` per `@Test`):
 *  - Property 9 generates >= [MIN_ITERATIONS] small content sizes, lays each out inside a
 *    `minTouchTarget()` box (and a `minTaskRowHeight()` row) tagged for lookup, all in a single
 *    composition [Column], then scans every tagged node and asserts its bounds meet the minimum.
 *  - Property 10 renders a batch of interactive controls with generated labels in one
 *    composition and asserts each exposes a non-blank content description.
 *
 * Composables are wrapped in `DopaShiftTheme(themeMode = ThemeMode.DARK)` per the task and the
 * established `Property8And11GoalCardSemanticsTest` pattern. No production composable is edited.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Property9And10TouchTargetAndDescriptionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        /** Property tests run a minimum of 100 generated cases (design Testing Strategy). */
        const val MIN_ITERATIONS = 120

        /**
         * Upper bound for generated intrinsic content sizes, deliberately below both minimums
         * (48dp / 56dp) so the helpers must do the expanding.
         */
        const val MAX_CONTENT_DP = 40

        /** Upper bound for generated streak lengths. */
        const val MAX_STREAK_DAYS = 400

        /** Upper bound for generated checklist item totals. */
        const val MAX_TOTAL_ITEMS = 60

        /** Fixed seed so a failing run is reproducible. */
        const val GENERATOR_SEED = 20240613L

        const val TOUCH_TARGET_TAG_PREFIX = "touch-target-"
        const val TASK_ROW_TAG_PREFIX = "task-row-"
        const val INTERACTIVE_TAG_PREFIX = "interactive-"
    }

    private val rng = Random(GENERATOR_SEED)

    private val categories = listOf(
        "Career", "Fitness", "Business", "Exams", "Health", "Finance", "Learning",
    )

    // =================================================================================
    // Property 9: All interactive targets meet the minimum touch size (DUX-5.1)
    // =================================================================================

    @Test
    fun property9_minTouchTarget_expandsAnySmallContentToAtLeast48dpInBothAxes() {
        // Generate many small (0..MAX_CONTENT_DP) content sizes. Each is placed inside a
        // clickable box carrying Modifier.minTouchTarget(); regardless of the tiny content the
        // laid-out node must measure >= 48dp x 48dp.
        val contentSizes = (0 until MIN_ITERATIONS).map {
            rng.nextInt(0, MAX_CONTENT_DP + 1)
        }

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                // A scrolling Column gives each child unbounded vertical space, so a batch of
                // 120 targets is not squeezed by the finite test window (which would otherwise
                // shrink later items below the minimum and defeat the property).
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    contentSizes.forEachIndexed { index, contentDp ->
                        // A custom clickable surface (does NOT inherit Material's minimum size),
                        // so the minTouchTarget() helper is the only thing enforcing 48dp. The
                        // tagged node applies minTouchTarget() first so its own layout carries
                        // the >= 48dp minimum; a small child (0..40dp) is placed inside it.
                        Box(
                            modifier = Modifier
                                .minTouchTarget()
                                .testTag("$TOUCH_TARGET_TAG_PREFIX$index")
                                .clickable(onClick = {}),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(contentDp.dp)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
        }

        // Assert every generated touch target measures at least 48dp in both dimensions,
        // scanning the unmerged tree so the tagged surface node's own bounds are checked.
        contentSizes.indices.forEach { index ->
            composeRule.onAllNodesWithTag("$TOUCH_TARGET_TAG_PREFIX$index", useUnmergedTree = true)[0]
                .assertWidthIsAtLeast(A11yDimens.MinTouchTarget)
                .assertHeightIsAtLeast(A11yDimens.MinTouchTarget)
        }

        assertTrue(
            "Sanity: at least MIN_ITERATIONS touch targets were generated (Property 9, DUX-5.1)",
            contentSizes.size >= MIN_ITERATIONS,
        )
    }

    @Test
    fun property9_minTaskRowHeight_expandsAnyShortRowToAtLeast56dpTall() {
        // Generate many short row heights; the minTaskRowHeight() helper must lift each to 56dp.
        val rowHeights = (0 until MIN_ITERATIONS).map {
            rng.nextInt(0, MAX_CONTENT_DP + 1)
        }

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    rowHeights.forEachIndexed { index, contentDp ->
                        Box(
                            modifier = Modifier
                                .minTaskRowHeight()
                                .testTag("$TASK_ROW_TAG_PREFIX$index")
                                .clickable(onClick = {}),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(contentDp.dp)
                                    .background(MaterialTheme.colorScheme.secondary),
                            )
                        }
                    }
                }
            }
        }

        rowHeights.indices.forEach { index ->
            composeRule.onAllNodesWithTag("$TASK_ROW_TAG_PREFIX$index", useUnmergedTree = true)[0]
                .assertHeightIsAtLeast(A11yDimens.MinTaskRowHeight)
        }

        assertTrue(
            "Sanity: at least MIN_ITERATIONS task rows were generated (Property 9, DUX-5.1)",
            rowHeights.size >= MIN_ITERATIONS,
        )
    }

    @Test
    fun property9_a11yDimens_declareTheDux51Minimums() {
        // The named dimensions the helpers reference must equal the DUX-5.1 minimums, so the
        // property above is anchored to the spec's numbers, not incidental values.
        assertTrue(
            "MinTouchTarget must be 48dp (DUX-5.1). Was: ${A11yDimens.MinTouchTarget}",
            A11yDimens.MinTouchTarget == 48.dp,
        )
        assertTrue(
            "MinTaskRowHeight must be 56dp (DUX-5.1). Was: ${A11yDimens.MinTaskRowHeight}",
            A11yDimens.MinTaskRowHeight == 56.dp,
        )
    }

    // =================================================================================
    // Property 10: Every non-decorative icon-only control has a content description (DUX-5.2)
    // =================================================================================

    @Test
    fun property10_everyInteractiveDashboardControl_exposesNonBlankContentDescription() {
        // Render a batch of interactive dashboard controls (clickable goal cards and completion
        // checkboxes) with generated labels; scan each tagged control's semantics subtree and
        // assert every one carries a non-blank content description (Property 10, DUX-5.2).
        val summaries = (0 until MIN_ITERATIONS).map { generateSummary(it) }
        val checkboxLabels = (0 until MIN_ITERATIONS).map { "Task ${it + 1} ${randomWord()}" }

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column {
                    summaries.forEachIndexed { index, summary ->
                        GoalCard(
                            summary = summary,
                            modifier = Modifier.testTag("$INTERACTIVE_TAG_PREFIX-card-$index"),
                            onClick = {},
                        )
                    }
                    checkboxLabels.forEachIndexed { index, label ->
                        CompletionCheckbox(
                            isComplete = index % 2 == 0,
                            contentDescription = label,
                            modifier = Modifier.testTag("$INTERACTIVE_TAG_PREFIX-check-$index"),
                        )
                    }
                }
            }
        }

        // Scan every node that carries a content description; each interactive control uses
        // clearAndSetSemantics { contentDescription = ... } so it is exactly one described node.
        val descriptions = composeRule
            .onAllNodes(hasContentDescription())
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            }

        val expectedControls = summaries.size + checkboxLabels.size
        assertTrue(
            "Expected at least $expectedControls described interactive controls (one per goal " +
                "card + one per checkbox), found ${descriptions.size} (Property 10, DUX-5.2)",
            descriptions.size >= expectedControls,
        )

        // No interactive control may expose a blank content description.
        descriptions.forEach { description ->
            assertFalse(
                "An interactive control exposed a blank content description; every " +
                    "non-decorative icon-only control must carry a non-empty description " +
                    "(Property 10, DUX-5.2)",
                description.isBlank(),
            )
        }

        // Every generated checkbox label must appear in some control's description, proving the
        // label is carried into the accessible semantics for any generated input.
        checkboxLabels.forEach { label ->
            assertTrue(
                "No control description carried the checkbox label '$label' (Property 10, " +
                    "DUX-5.2)",
                descriptions.any { it.contains(label) },
            )
        }
    }

    @Test
    fun property10_decorativeElement_isSkippedByScreenReaders() {
        // A decorative-only surface (contentDescription = null on its icon, cleared semantics)
        // must NOT surface a content description, while a sibling interactive control does.
        // Confirms decorative elements are marked so screen readers skip them (Property 10 /
        // DUX-5.2, second clause).
        val label = "Complete morning workout"
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column {
                    // Interactive control: exposes a description.
                    CompletionCheckbox(
                        isComplete = true,
                        contentDescription = label,
                        modifier = Modifier.testTag("interactive-only"),
                    )
                    // Decorative-only box: no semantics/description of its own.
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("decorative-only"),
                    )
                }
            }
        }

        val descriptions = composeRule
            .onAllNodes(hasContentDescription())
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") }

        assertTrue(
            "The interactive control must expose a non-blank content description " +
                "(Property 10, DUX-5.2). Descriptions: $descriptions",
            descriptions.any { it.contains(label) && it.isNotBlank() },
        )
        // Decorative box carries no text, so no description should equal its (empty) content.
        assertFalse(
            "A purely decorative element must not surface a content description " +
                "(Property 10, DUX-5.2). Descriptions: $descriptions",
            descriptions.any { it.isBlank() },
        )
    }

    // ---------------------------------------------------------------------------------
    // Generators
    // ---------------------------------------------------------------------------------

    /** Generates one valid [GoalProgressSummary] respecting [GoalProfile]'s invariants. */
    private fun generateSummary(index: Int): GoalProgressSummary {
        val name = "Goal ${index + 1} ${randomWord()}".take(100)
        val category = categories[rng.nextInt(categories.size)]
        val total = rng.nextInt(0, MAX_TOTAL_ITEMS + 1)
        val completed = if (total == 0) 0 else rng.nextInt(0, total + 1)
        val streak = rng.nextInt(0, MAX_STREAK_DAYS + 1)
        val now = Instant.now()
        val goal = GoalProfile(
            id = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            name = name,
            category = category,
            keywords = emptyList(),
            createdAt = now,
            updatedAt = now,
            isActive = true,
        )
        return GoalProgressSummary(
            goal = goal,
            completedItems = completed,
            totalItems = total,
            streakDays = streak,
        )
    }

    private fun randomWord(): String {
        val len = rng.nextInt(3, 8)
        return (1..len).map { ('a' + rng.nextInt(26)) }.joinToString("")
    }

    /** Matches any semantics node that carries a (non-null) content description. */
    private fun hasContentDescription(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
}
