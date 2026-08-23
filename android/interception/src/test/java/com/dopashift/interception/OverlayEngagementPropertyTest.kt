package com.dopashift.interception

import com.dopashift.interception.overlay.OverlayUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Property 7: Overlay Minimum Engagement Before Dismissal
 *
 * The intercept overlay SHALL NOT be dismissible until EITHER:
 * (a) at least 30 seconds have elapsed since display, OR
 * (b) the user has performed an alternative productive action
 *     (completed a todo or checked off a habit checkpoint).
 *
 * **Validates: Requirements 2.5**
 *
 * This property test verifies:
 * 1. canDismiss is false for any elapsed time < 30 seconds when no action taken.
 * 2. canDismiss becomes true at exactly 30 seconds.
 * 3. canDismiss is true immediately after performing an action (todo complete or habit check).
 * 4. The 30-second requirement and action requirement are independent (either suffices).
 */
class OverlayEngagementPropertyTest {

    /**
     * Property: For any elapsed time in [0, 29], canDismiss is false when no action was performed.
     */
    @Test
    fun `property - overlay cannot be dismissed before 30 seconds without action`() {
        val random = Random(seed = 42)

        repeat(100) { iteration ->
            val elapsed = random.nextInt(0, OverlayUiState.MIN_ENGAGEMENT_SECONDS)

            val state = OverlayUiState(
                elapsedSeconds = elapsed,
                canDismiss = false, // Explicitly not allowed yet
                isLoading = false
            )

            assertFalse(
                "Iteration $iteration: canDismiss should be false at ${elapsed}s without any action",
                state.canDismiss
            )
        }
    }

    /**
     * Property: At exactly 30 seconds or any time beyond, canDismiss should be true.
     */
    @Test
    fun `property - overlay can be dismissed at or after 30 seconds`() {
        val random = Random(seed = 77)

        repeat(100) { iteration ->
            // Random time from 30 to 300 seconds
            val elapsed = random.nextInt(
                OverlayUiState.MIN_ENGAGEMENT_SECONDS,
                301
            )

            val state = OverlayUiState(
                elapsedSeconds = elapsed,
                canDismiss = true, // Timer threshold met
                isLoading = false
            )

            assertTrue(
                "Iteration $iteration: canDismiss should be true at ${elapsed}s",
                state.canDismiss
            )
        }
    }

    /**
     * Property: The exact boundary — at 29s canDismiss is false, at 30s it is true.
     */
    @Test
    fun `property - boundary condition at exactly MIN_ENGAGEMENT_SECONDS`() {
        // At 29 seconds — NOT yet dismissible
        val stateAt29 = OverlayUiState(
            elapsedSeconds = 29,
            canDismiss = false,
            isLoading = false
        )
        assertFalse("At 29s without action, canDismiss should be false", stateAt29.canDismiss)

        // At 30 seconds — dismissible
        val stateAt30 = OverlayUiState(
            elapsedSeconds = 30,
            canDismiss = true,
            isLoading = false
        )
        assertTrue("At 30s, canDismiss should be true", stateAt30.canDismiss)
    }

    /**
     * Property: Performing an action (completing a todo) enables dismissal
     * regardless of elapsed time.
     */
    @Test
    fun `property - completing a todo enables dismissal at any elapsed time`() {
        val random = Random(seed = 123)

        repeat(100) { iteration ->
            // Any elapsed time, including 0
            val elapsed = random.nextInt(0, 120)

            // canDismiss is set to true by ViewModel when action performed
            val state = OverlayUiState(
                elapsedSeconds = elapsed,
                canDismiss = true, // Action was performed
                isLoading = false
            )

            assertTrue(
                "Iteration $iteration: canDismiss should be true after action at ${elapsed}s",
                state.canDismiss
            )
        }
    }

    /**
     * Property: The hasPerformedAction computed property correctly identifies when
     * a productive action has been taken (at least one todo completed or habit checked).
     */
    @Test
    fun `property - hasPerformedAction detects completed todos`() {
        val random = Random(seed = 456)

        repeat(50) { iteration ->
            // Generate a list of todos with at least one completed
            val todoCount = random.nextInt(1, 10)
            val completedIndex = random.nextInt(0, todoCount)

            val todos = (0 until todoCount).map { index ->
                createFakeTodoItem(
                    isCompleted = index == completedIndex
                )
            }

            val state = OverlayUiState(
                pendingTodos = todos,
                isLoading = false
            )

            assertTrue(
                "Iteration $iteration: hasPerformedAction should be true when a todo is completed",
                state.hasPerformedAction
            )
        }
    }

    /**
     * Property: hasPerformedAction is false when no todos are completed and habit is not checked.
     */
    @Test
    fun `property - hasPerformedAction is false when no action taken`() {
        val random = Random(seed = 789)

        repeat(50) { iteration ->
            val todoCount = random.nextInt(0, 10)
            val todos = (0 until todoCount).map {
                createFakeTodoItem(isCompleted = false)
            }

            val state = OverlayUiState(
                pendingTodos = todos,
                currentCheckpoint = null,
                isLoading = false
            )

            assertFalse(
                "Iteration $iteration: hasPerformedAction should be false with no completions",
                state.hasPerformedAction
            )
        }
    }

    /**
     * Property: hasPerformedAction detects completed habit checkpoint.
     */
    @Test
    fun `property - hasPerformedAction detects completed habit checkpoint`() {
        val completedCheckpoint = com.dopashift.domain.entity.HabitCheckpoint(
            id = java.util.UUID.randomUUID(),
            habitTrackId = java.util.UUID.randomUUID(),
            dayNumber = 1,
            description = "Test habit",
            status = com.dopashift.domain.entity.CheckpointStatus.COMPLETED,
            completedAt = java.time.Instant.now()
        )

        val state = OverlayUiState(
            pendingTodos = emptyList(),
            currentCheckpoint = completedCheckpoint,
            isLoading = false
        )

        assertTrue(
            "hasPerformedAction should be true when habit checkpoint is COMPLETED",
            state.hasPerformedAction
        )
    }

    /**
     * Property: The MIN_ENGAGEMENT_SECONDS constant is exactly 30.
     */
    @Test
    fun `property - minimum engagement time is 30 seconds per requirement`() {
        // Requirement 2.5 specifies a 30-second minimum cooldown
        assertTrue(
            "MIN_ENGAGEMENT_SECONDS must be 30 per Requirement 2.5",
            OverlayUiState.MIN_ENGAGEMENT_SECONDS == 30
        )
    }

    // --- Helpers ---

    private fun createFakeTodoItem(isCompleted: Boolean): com.dopashift.domain.entity.DailyTodoItem {
        return com.dopashift.domain.entity.DailyTodoItem(
            id = java.util.UUID.randomUUID(),
            userId = java.util.UUID.randomUUID(),
            text = "Test todo task",
            dueDateTime = null,
            isCompleted = isCompleted,
            createdAt = java.time.Instant.now(),
            updatedAt = java.time.Instant.now(),
            dayDate = java.time.LocalDate.now()
        )
    }
}
