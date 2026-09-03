package com.dopashift.interception.overlay

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Unit tests for [toKineticOverlayState] — the seam that maps the interception module's
 * [OverlayUiState] into the `ui`-module Kinetic overlay state so the shared `InterceptOverlayScreen`
 * can be hosted in the interception overlay window (spec `android-ui-upgrade`, task 11.2).
 *
 * The mapper introduces **no parallel timer**: it derives the cooldown from the engagement timer the
 * [OverlayViewModel] already runs (`MIN_ENGAGEMENT_SECONDS - elapsedSeconds`, floored at zero), and
 * derives the Continue-button gating from that value (Property 1, AUI-2.7). These tests pin that
 * derivation plus the rescue-habit and pending-task projections.
 *
 * Validates: Requirements 2.1, 2.7
 */
class OverlayUiStateMapperTest {

    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000009")

    private fun todo(text: String, completed: Boolean = false): DailyTodoItem {
        val now = Instant.parse("2024-06-15T09:00:00Z")
        return DailyTodoItem(
            id = UUID.randomUUID(),
            userId = userId,
            text = text,
            dueDateTime = null,
            isCompleted = completed,
            createdAt = now,
            updatedAt = now,
            dayDate = LocalDate.of(2024, 6, 15),
        )
    }

    private fun checkpoint(day: Int, status: CheckpointStatus): HabitCheckpoint =
        HabitCheckpoint(
            id = UUID.randomUUID(),
            habitTrackId = UUID.randomUUID(),
            dayNumber = day,
            description = "Read one page",
            status = status,
        )

    private fun track(currentDay: Int, checkpoint: HabitCheckpoint): HabitTrack =
        HabitTrack(
            id = UUID.randomUUID(),
            goalId = UUID.randomUUID(),
            userId = userId,
            startDate = LocalDate.of(2024, 6, 10),
            currentDay = currentDay,
            checkpoints = listOf(checkpoint),
        )

    @Test
    fun `cooldown counts down from the minimum engagement window`() {
        val state = OverlayUiState(elapsedSeconds = 5)

        val mapped = state.toKineticOverlayState()

        assertEquals(OverlayUiState.MIN_ENGAGEMENT_SECONDS - 5, mapped.cooldownSeconds)
    }

    @Test
    fun `cooldown never goes negative once the window has elapsed`() {
        val state = OverlayUiState(elapsedSeconds = OverlayUiState.MIN_ENGAGEMENT_SECONDS + 10)

        val mapped = state.toKineticOverlayState()

        assertEquals(0, mapped.cooldownSeconds)
    }

    @Test
    fun `continue is disabled while cooldown is positive`() {
        val state = OverlayUiState(elapsedSeconds = 0)

        val mapped = state.toKineticOverlayState()

        assertTrue(mapped.cooldownSeconds > 0)
        assertFalse(mapped.continueEnabled)
    }

    @Test
    fun `continue is enabled only when cooldown reaches zero`() {
        val state = OverlayUiState(elapsedSeconds = OverlayUiState.MIN_ENGAGEMENT_SECONDS)

        val mapped = state.toKineticOverlayState()

        assertEquals(0, mapped.cooldownSeconds)
        assertTrue(mapped.continueEnabled)
    }

    @Test
    fun `target app name is carried through`() {
        val state = OverlayUiState(triggeringPackageName = "com.social.distracting")

        val mapped = state.toKineticOverlayState()

        assertEquals("com.social.distracting", mapped.targetAppName)
    }

    @Test
    fun `rescue habit is projected when a track and checkpoint are present`() {
        val cp = checkpoint(day = 6, status = CheckpointStatus.PENDING)
        val state = OverlayUiState(
            currentHabitTrack = track(currentDay = 6, checkpoint = cp),
            currentCheckpoint = cp,
        )

        val mapped = state.toKineticOverlayState()

        val rescue = requireNotNull(mapped.rescueHabit) { "expected a rescue habit" }
        assertEquals("Read one page", rescue.title)
        assertEquals("Day 6/30", rescue.dayLabel)
        assertFalse(rescue.completed)
    }

    @Test
    fun `rescue habit reflects a completed checkpoint`() {
        val cp = checkpoint(day = 3, status = CheckpointStatus.COMPLETED)
        val state = OverlayUiState(
            currentHabitTrack = track(currentDay = 3, checkpoint = cp),
            currentCheckpoint = cp,
        )

        val mapped = state.toKineticOverlayState()

        assertTrue(requireNotNull(mapped.rescueHabit).completed)
    }

    @Test
    fun `rescue habit is null when no checkpoint is available`() {
        val state = OverlayUiState(currentHabitTrack = null, currentCheckpoint = null)

        val mapped = state.toKineticOverlayState()

        assertNull(mapped.rescueHabit)
    }

    @Test
    fun `pending tasks are projected preserving text and completion`() {
        val state = OverlayUiState(
            pendingTodos = listOf(
                todo("Write report", completed = false),
                todo("Reply to email", completed = true),
            ),
        )

        val mapped = state.toKineticOverlayState()

        assertEquals(2, mapped.pendingTasks.size)
        assertEquals("Write report", mapped.pendingTasks[0].text)
        assertFalse(mapped.pendingTasks[0].done)
        assertEquals("Reply to email", mapped.pendingTasks[1].text)
        assertTrue(mapped.pendingTasks[1].done)
    }
}
