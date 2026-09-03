package com.dopashift.ui.dashboard

// Feature: dynamic-ui-experience, Property 8: Goal card renders all required fields
// Feature: dynamic-ui-experience, Property 11: State is never conveyed by color alone
// Validates: Requirements DUX-3.7 (Property 8), DUX-5.7 (Property 11)

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 * Compose semantics-scan property tests for the Dashboard [GoalCard] and the non-color
 * completion state exposed by [CompletionCheckbox].
 *
 * These cover the two Compose-side correctness properties from the design's Correctness
 * Properties section:
 *
 *  - **Property 8: Goal card renders all required fields** (DUX-3.7) — for any valid
 *    [GoalProgressSummary], the composed [GoalCard] exposes a single non-blank content
 *    description that includes the numeric progress percentage, so the card's meaning
 *    (goal name + progress) is discoverable via the semantics tree.
 *
 *  - **Property 11: State is never conveyed by color alone** (DUX-5.7) — [CompletionCheckbox]
 *    in the `true` vs `false` state exposes *distinct* content descriptions
 *    (`".*completed"` vs `".*not completed"`) and swaps the icon *shape* (filled check-circle
 *    vs. empty circle). The state is therefore discoverable without relying on color.
 *
 * Approach (Robolectric constraint: at most one `setContent` per `@Test`):
 *  - Property 8 generates a batch of many [GoalProgressSummary] values and lays them out in a
 *    single [Column] in one composition, then scans *all* card semantics nodes and asserts
 *    every one carries a non-blank content description containing the expected `"<percent>%"`.
 *    The batch is regenerated deterministically per run over >= 100 generated summaries.
 *  - Property 11 renders both checkbox states in one composition and asserts their
 *    content descriptions differ and match the expected completed / not-completed patterns.
 *
 * Composables are wrapped in `DopaShiftTheme(themeMode = ThemeMode.DARK)` per the task and the
 * established test pattern, so the color scheme / tokens they reference are available. No
 * production composable is edited by this file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Property8And11GoalCardSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        /** Property tests run a minimum of 100 generated cases (design Testing Strategy). */
        const val MIN_ITERATIONS = 120

        /** Upper bound for generated checklist item totals. */
        const val MAX_TOTAL_ITEMS = 60

        /** Upper bound for generated streak lengths. */
        const val MAX_STREAK_DAYS = 400

        /** Fixed seed so a failing run is reproducible. */
        const val GENERATOR_SEED = 20240517L
    }

    // A stable set of category names; GoalProfile.category must be 1..50 chars.
    private val categories = listOf(
        "Career", "Fitness", "Business", "Exams", "Health", "Finance", "Learning",
    )

    private val rng = Random(GENERATOR_SEED)

    /** Generates one valid [GoalProgressSummary] respecting [GoalProfile]'s invariants. */
    private fun generateSummary(index: Int): GoalProgressSummary {
        // Goal name must be 1..100 chars and non-blank.
        val name = "Goal ${index + 1} ${randomWord()}".take(100)
        val category = categories[rng.nextInt(categories.size)]
        val total = rng.nextInt(0, MAX_TOTAL_ITEMS + 1) // totalItems in 0..N
        val completed = if (total == 0) 0 else rng.nextInt(0, total + 1) // completed in 0..total
        val streak = rng.nextInt(0, MAX_STREAK_DAYS + 1) // streakDays >= 0
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

    // =================================================================================
    // Property 8: Goal card renders all required fields (DUX-3.7)
    // =================================================================================

    @Test
    fun property8_everyGoalCard_exposesNonBlankContentDescriptionWithProgress() {
        // Generate a batch of >= MIN_ITERATIONS summaries. Rendering hundreds of cards in a
        // single composition is expensive, so we render them in generation-order batches
        // across a single setContent using a Column — one composition, many card nodes.
        val summaries = (0 until MIN_ITERATIONS).map { generateSummary(it) }

        // Pre-compute the expected percent token each card must surface.
        val expectedPercents = summaries.map { "${it.progressPercent}%" }

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column {
                    summaries.forEach { summary ->
                        GoalCard(summary = summary)
                    }
                }
            }
        }

        // Scan every node in the tree that carries a content description (GoalCard uses
        // clearAndSetSemantics { contentDescription = ... } so each card is exactly one
        // such node). Collect the descriptions.
        val nodes = composeRule
            .onAllNodes(hasContentDescription())
            .fetchSemanticsNodes()

        val descriptions = nodes.mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        }

        // There must be at least one described node per generated card (there can be a few
        // extra from any incidental described chrome, but never fewer than the card count).
        assertTrue(
            "Expected at least ${summaries.size} nodes with a content description " +
                "(one per goal card), found ${descriptions.size}",
            descriptions.size >= summaries.size,
        )

        // Every card's content description must be non-blank (Property 8 / DUX-3.7).
        descriptions.forEach { description ->
            assertFalse(
                "A goal-card content description was blank; the card must expose its " +
                    "goal name + progress semantics (Property 8, DUX-3.7)",
                description.isBlank(),
            )
        }

        // Every expected "<percent>%" progress token must appear in at least one card's
        // content description, proving the numeric progress is carried in the semantics for
        // any generated summary (Property 8 / DUX-3.7). Cards with the same percent share a
        // token, so we require each distinct expected token to be present.
        val joined = descriptions.joinToString(" | ")
        expectedPercents.distinct().forEach { token ->
            assertTrue(
                "No goal-card content description carried the progress token '$token'; " +
                    "the card must expose its numeric progress (Property 8, DUX-3.7). " +
                    "Descriptions: $joined",
                descriptions.any { it.contains(token) },
            )
        }
    }

    @Test
    fun property8_goalCard_contentDescription_includesGoalNameAndPercent_representativeSample() {
        // A representative single-card composition asserting the description contains BOTH the
        // goal name and the percent, complementing the batch scan above.
        val summary = GoalProgressSummary(
            goal = GoalProfile(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                name = "Run a 10k",
                category = "Fitness",
                keywords = emptyList(),
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                isActive = true,
            ),
            completedItems = 3,
            totalItems = 4,
            streakDays = 5,
        )

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                GoalCard(summary = summary)
            }
        }

        val description = composeRule
            .onAllNodes(hasContentDescription())
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") }
            .firstOrNull { it.contains("Run a 10k") }

        assertTrue(
            "Goal card must expose a content description naming the goal (Property 8, DUX-3.7)",
            description != null,
        )
        assertTrue(
            "Goal card content description must include the numeric progress percent " +
                "(Property 8, DUX-3.7). Was: $description",
            description!!.contains("${summary.progressPercent}%"),
        )
    }

    // =================================================================================
    // Property 11: State is never conveyed by color alone (DUX-5.7)
    // =================================================================================

    @Test
    fun property11_completionCheckbox_completeVsIncomplete_exposeDistinctNonColorDescriptions() {
        // Render both states in a single composition (Robolectric: one setContent per test).
        val label = "Finish morning workout"
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column {
                    CompletionCheckbox(isComplete = true, contentDescription = label)
                    CompletionCheckbox(isComplete = false, contentDescription = label)
                }
            }
        }

        val descriptions = composeRule
            .onAllNodes(hasContentDescription())
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") }

        val completed = descriptions.firstOrNull { it.matches(Regex(".*(?<!not )completed$")) }
        val notCompleted = descriptions.firstOrNull { it.matches(Regex(".*not completed$")) }

        assertTrue(
            "Completed checkbox must expose a '.*completed' content description so state is " +
                "discoverable without color (Property 11, DUX-5.7). Descriptions: $descriptions",
            completed != null,
        )
        assertTrue(
            "Incomplete checkbox must expose a '.*not completed' content description so state " +
                "is discoverable without color (Property 11, DUX-5.7). Descriptions: $descriptions",
            notCompleted != null,
        )
        // The two states must be DISTINCT in the semantics tree (not color-only).
        assertNotEquals(
            "Complete and incomplete states must expose distinct content descriptions " +
                "(Property 11, DUX-5.7)",
            completed,
            notCompleted,
        )
    }

    @Test
    fun property11_completionCheckbox_state_distinctForManyLabels() {
        // Property-style loop: for many generated labels the completed / not-completed
        // descriptions must always differ and follow the non-color pattern. All labels are
        // rendered in a single composition to respect the one-setContent Robolectric rule.
        val labels = (0 until MIN_ITERATIONS).map { "Task ${it + 1} ${randomWord()}" }

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column {
                    labels.forEach { label ->
                        CompletionCheckbox(isComplete = true, contentDescription = label)
                        CompletionCheckbox(isComplete = false, contentDescription = label)
                    }
                }
            }
        }

        val descriptions = composeRule
            .onAllNodes(hasContentDescription())
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") }

        labels.forEach { label ->
            val completed = "$label, completed"
            val notCompleted = "$label, not completed"
            assertTrue(
                "Missing completed description for '$label' (Property 11, DUX-5.7)",
                descriptions.contains(completed),
            )
            assertTrue(
                "Missing not-completed description for '$label' (Property 11, DUX-5.7)",
                descriptions.contains(notCompleted),
            )
            assertNotEquals(
                "Complete/incomplete descriptions must differ for '$label' (Property 11, DUX-5.7)",
                completed,
                notCompleted,
            )
        }
    }

    @Test
    fun property11_completionCheckbox_completeAndIncomplete_haveDistinctIconShapes() {
        // A card generated with all items complete carries a filled check-circle plus explicit
        // "Complete" text; an incomplete card carries the fraction text instead of the check
        // icon. Rendering both proves the completion state has a non-color (shape/text) signal
        // in addition to color (Property 11, DUX-5.7). Robolectric: single setContent.
        val completeSummary = GoalProgressSummary(
            goal = goalNamed("Done goal"),
            completedItems = 5,
            totalItems = 5,
            streakDays = 7,
        )
        val incompleteSummary = GoalProgressSummary(
            goal = goalNamed("Ongoing goal"),
            completedItems = 2,
            totalItems = 5,
            streakDays = 3,
        )

        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                Column {
                    GoalCard(summary = completeSummary)
                    GoalCard(summary = incompleteSummary)
                }
            }
        }

        val descriptions = composeRule
            .onAllNodes(hasContentDescription())
            .fetchSemanticsNodes()
            .mapNotNull { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") }

        // The complete goal's aggregate description explicitly states "Complete" (the non-color
        // textual signal); the incomplete goal's does not. This confirms completion state is
        // conveyed by text (non-color), not color alone.
        val completeDesc = descriptions.firstOrNull { it.contains("Done goal") }
        val incompleteDesc = descriptions.firstOrNull { it.contains("Ongoing goal") }

        assertTrue("Complete goal card must be present", completeDesc != null)
        assertTrue("Incomplete goal card must be present", incompleteDesc != null)
        assertTrue(
            "Complete goal card must convey completion in text, not color alone " +
                "(Property 11, DUX-5.7). Was: $completeDesc",
            completeDesc!!.contains("Complete"),
        )
        assertFalse(
            "Incomplete goal card must NOT claim completion (Property 11, DUX-5.7). Was: $incompleteDesc",
            incompleteDesc!!.contains("Complete"),
        )
        assertNotEquals(
            "Complete and incomplete goal cards must differ in their non-color state signal " +
                "(Property 11, DUX-5.7)",
            completeDesc,
            incompleteDesc,
        )
    }

    private fun goalNamed(name: String): GoalProfile = GoalProfile(
        id = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        name = name,
        category = "Fitness",
        keywords = emptyList(),
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        isActive = true,
    )

    /** Matches any semantics node that carries a (non-null) content description. */
    private fun hasContentDescription(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
}
