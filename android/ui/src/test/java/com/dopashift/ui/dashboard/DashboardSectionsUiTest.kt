// Feature: Home/App-Limits UI fixes — Tasks 2 & 3 verification.
// Robolectric JVM Compose tests driving the live DashboardContent directly with a controlled
// DashboardUiState.Content + DashboardSectionData and recording callbacks. Validates:
//  - Today's Focus caps the collapsed list to 3 rows with a "Show all (N)" / "Show less" toggle
//    that expands in place (Task 3); with <=3 todos no toggle is shown.
//  - The active-habits checkbox is interactive only when today's checkpoint is PENDING, and a
//    MISSED current-day checkpoint renders unchecked and does not fire the completion callback
//    (Task 2).
package com.dopashift.ui.dashboard

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardSectionsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private fun todo(text: String, completed: Boolean = false) = DailyTodoItem(
        id = UUID.randomUUID(),
        userId = userId,
        text = text,
        dueDateTime = null,
        isCompleted = completed,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        dayDate = LocalDate.now(),
    )

    private fun setContent(
        sectionData: DashboardSectionData,
        onHabitComplete: (UUID) -> Unit = {},
        onTodoCheckedChange: (UUID, Boolean) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            DopaShiftTheme(themeMode = ThemeMode.DARK) {
                DashboardContent(
                    uiState = DashboardUiState.Content(
                        sections = emptyList(),
                        efficiencyScore = null,
                    ),
                    sectionData = sectionData,
                    onTodoCheckedChange = onTodoCheckedChange,
                    onHabitComplete = onHabitComplete,
                    onLoadMoreActivity = {},
                    onAddTodo = {},
                    onNavigateToInterceptionRules = {},
                )
            }
        }
    }

    // ---- Task 3: Today's Focus cap + expand/collapse ---------------------------------

    @Test
    fun todaysFocus_capsToThree_andExpandsInPlace() {
        val todos = (1..5).map { todo("Task $it") }
        setContent(DashboardSectionData(todayTodos = todos))

        // The Today's Focus card sits below the header/efficiency cards in the LazyColumn;
        // scroll it into view before asserting.
        scrollTo("Task 1")

        // Collapsed: first 3 exist, 4th and 5th are not composed.
        composeRule.onNodeWithText("Task 1").assertExists()
        composeRule.onNodeWithText("Task 3").assertExists()
        composeRule.onNodeWithText("Task 4").assertDoesNotExist()
        composeRule.onNodeWithText("Task 5").assertDoesNotExist()

        // A "Show all (5)" control is present; tap it.
        scrollTo("Show all (5)")
        composeRule.onNodeWithText("Show all (5)").performClick()

        // Expanded: all 5 exist and a "Show less" control appears.
        scrollTo("Task 5")
        composeRule.onNodeWithText("Task 4").assertExists()
        composeRule.onNodeWithText("Task 5").assertExists()
        composeRule.onNodeWithText("Show less").assertExists()
    }

    @Test
    fun todaysFocus_noToggle_whenThreeOrFewer() {
        val todos = (1..3).map { todo("Task $it") }
        setContent(DashboardSectionData(todayTodos = todos))

        scrollTo("Task 1")
        composeRule.onNodeWithText("Task 1").assertExists()
        composeRule.onNodeWithText("Task 3").assertExists()
        // No expand/collapse control for a list within the cap.
        composeRule.onNodeWithText("Show all (3)").assertDoesNotExist()
        composeRule.onNodeWithText("Show less").assertDoesNotExist()
    }

    // ---- Task 2: habit checkbox interactivity ----------------------------------------

    private fun trackWithCurrentStatus(status: CheckpointStatus): HabitTrack {
        val trackId = UUID.randomUUID()
        return HabitTrack(
            id = trackId,
            goalId = UUID.randomUUID(),
            userId = userId,
            startDate = LocalDate.of(2026, 1, 1),
            currentDay = 2,
            isFinished = false,
            checkpoints = listOf(
                HabitCheckpoint(UUID.randomUUID(), trackId, 1, "Day 1 habit", CheckpointStatus.COMPLETED),
                HabitCheckpoint(UUID.randomUUID(), trackId, 2, "Day 2 habit", status),
            ),
        )
    }

    @Test
    fun activeHabit_pendingCheckpoint_isInteractive_andFiresCompletion() {
        val track = trackWithCurrentStatus(CheckpointStatus.PENDING)
        val completed = mutableListOf<UUID>()
        setContent(
            DashboardSectionData(activeHabitTracks = listOf(track)),
            onHabitComplete = { completed += it },
        )

        // The row's label is the current-day checkpoint description; scroll it into view.
        scrollTo("Day 2 habit")
        composeRule.onNodeWithText("Day 2 habit").assertExists()
        // The single track's checkbox is the only toggleable node; it is enabled and unchecked
        // (PENDING), and clicking it fires completion for this track.
        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.onNode(isToggleable()).performClick()
        assertEquals(listOf(track.id), completed)
    }

    @Test
    fun activeHabit_missedCheckpoint_isNotChecked_andDoesNotFireCompletion() {
        val track = trackWithCurrentStatus(CheckpointStatus.MISSED)
        val completed = mutableListOf<UUID>()
        setContent(
            DashboardSectionData(activeHabitTracks = listOf(track)),
            onHabitComplete = { completed += it },
        )

        // The label still renders, but the checkbox is unchecked (MISSED is not COMPLETED) and
        // disabled, so tapping it does not fire completion.
        scrollTo("Day 2 habit")
        composeRule.onNodeWithText("Day 2 habit").assertExists()
        composeRule.onNode(isToggleable()).assertIsOff()
        composeRule.onNode(isToggleable()).assertIsNotEnabled()
        composeRule.onNode(isToggleable()).performClick()
        assertTrue("a missed-day checkbox must not fire completion", completed.isEmpty())
    }

    /** Scrolls the Dashboard's LazyColumn until a node with [text] is composed/visible. */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }
}
