package com.dopashift.application.dashboard

import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.entity.UserProfile
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.UserProfileRepository
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [DashboardSummaryQueryService].
 *
 * Validates that the summary returns per-entity-type counts scoped to the authenticated
 * user, resolves "today" in the user's configured time zone, and produces counts sufficient
 * to distinguish Zero_State from Partial_State.
 *
 * Tag: DQC-5.3
 * Requirements: 5.3
 */
class DashboardSummaryQueryServiceTest {

    private val userId: UUID = UUID.randomUUID()
    private val otherUserId: UUID = UUID.randomUUID()

    // Fixed instant: 2026-01-15T02:00:00Z. In UTC this is Jan 15; in America/Los_Angeles
    // (UTC-8) this is still Jan 14 — used to prove the user's time zone drives "today".
    private val fixedInstant: Instant = Instant.parse("2026-01-15T02:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @Test
    fun `zero state when user has no goals, todos, or habits`() = runTest {
        val service = service()

        val summary = service.getSummary(userId)

        assertEquals(0, summary.activeGoalCount)
        assertEquals(0, summary.todayTodoCount)
        assertEquals(0, summary.activeHabitCount)
        assertTrue(summary.isZeroState)
    }

    @Test
    fun `partial state is not zero state when only todos exist today`() = runTest {
        val goals = FakeGoalRepository()
        val todos = FakeDailyTodoRepository()
        val habits = FakeHabitTrackRepository()
        val profiles = FakeUserProfileRepository()

        val todayUtc = LocalDate.of(2026, 1, 15)
        todos.add(todoFor(userId, todayUtc, completed = true))

        val service = DashboardSummaryQueryService(goals, todos, habits, profiles, clock)
        val summary = service.getSummary(userId)

        assertEquals(0, summary.activeGoalCount)
        assertEquals(1, summary.todayTodoCount) // total, including completed
        assertEquals(0, summary.activeHabitCount)
        assertFalse(summary.isZeroState)
    }

    @Test
    fun `counts are scoped to the authenticated user`() = runTest {
        val goals = FakeGoalRepository()
        val todos = FakeDailyTodoRepository()
        val habits = FakeHabitTrackRepository()
        val profiles = FakeUserProfileRepository()

        val todayUtc = LocalDate.of(2026, 1, 15)
        // Data for the authenticated user
        goals.add(goalFor(userId))
        todos.add(todoFor(userId, todayUtc))
        habits.add(habitFor(userId, finished = false))
        // Noise for another user — must NOT be counted
        goals.add(goalFor(otherUserId))
        goals.add(goalFor(otherUserId))
        todos.add(todoFor(otherUserId, todayUtc))
        habits.add(habitFor(otherUserId, finished = false))

        val service = DashboardSummaryQueryService(goals, todos, habits, profiles, clock)
        val summary = service.getSummary(userId)

        assertEquals(1, summary.activeGoalCount)
        assertEquals(1, summary.todayTodoCount)
        assertEquals(1, summary.activeHabitCount)
    }

    @Test
    fun `finished habits and inactive goals are excluded`() = runTest {
        val goals = FakeGoalRepository()
        val todos = FakeDailyTodoRepository()
        val habits = FakeHabitTrackRepository()
        val profiles = FakeUserProfileRepository()

        goals.add(goalFor(userId, active = true))
        goals.add(goalFor(userId, active = false))
        habits.add(habitFor(userId, finished = false))
        habits.add(habitFor(userId, finished = true))

        val service = DashboardSummaryQueryService(goals, todos, habits, profiles, clock)
        val summary = service.getSummary(userId)

        assertEquals(1, summary.activeGoalCount)
        assertEquals(1, summary.activeHabitCount)
    }

    @Test
    fun `today is resolved in the user's configured time zone`() = runTest {
        val goals = FakeGoalRepository()
        val todos = FakeDailyTodoRepository()
        val habits = FakeHabitTrackRepository()
        val profiles = FakeUserProfileRepository()

        // The fixed instant is Jan 15 in UTC but Jan 14 in Los Angeles (UTC-8).
        profiles.add(profileFor(userId, timezone = "America/Los_Angeles"))
        // A to-do dated Jan 14 (user-local today) should be counted...
        todos.add(todoFor(userId, LocalDate.of(2026, 1, 14)))
        // ...while a to-do dated Jan 15 (UTC today) should NOT.
        todos.add(todoFor(userId, LocalDate.of(2026, 1, 15)))

        val service = DashboardSummaryQueryService(goals, todos, habits, profiles, clock)
        val summary = service.getSummary(userId)

        assertEquals(1, summary.todayTodoCount)
    }

    @Test
    fun `falls back to clock zone when time zone is invalid`() = runTest {
        val goals = FakeGoalRepository()
        val todos = FakeDailyTodoRepository()
        val habits = FakeHabitTrackRepository()
        val profiles = FakeUserProfileRepository()

        profiles.add(profileFor(userId, timezone = "Not/AZone"))
        todos.add(todoFor(userId, LocalDate.of(2026, 1, 15))) // UTC today

        val service = DashboardSummaryQueryService(goals, todos, habits, profiles, clock)
        val summary = service.getSummary(userId)

        assertEquals(1, summary.todayTodoCount)
    }

    // --- helpers ---

    private fun service(): DashboardSummaryQueryService =
        DashboardSummaryQueryService(
            FakeGoalRepository(),
            FakeDailyTodoRepository(),
            FakeHabitTrackRepository(),
            FakeUserProfileRepository(),
            clock
        )

    private fun goalFor(owner: UUID, active: Boolean = true): GoalProfile =
        GoalProfile(
            id = UUID.randomUUID(),
            userId = owner,
            name = "Goal ${UUID.randomUUID()}".take(50),
            category = "Fitness",
            keywords = listOf("run"),
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
            isActive = active
        )

    private fun todoFor(owner: UUID, day: LocalDate, completed: Boolean = false): DailyTodoItem =
        DailyTodoItem(
            id = UUID.randomUUID(),
            userId = owner,
            text = "Task",
            dueDateTime = null,
            isCompleted = completed,
            createdAt = fixedInstant,
            updatedAt = fixedInstant,
            dayDate = day
        )

    private fun habitFor(owner: UUID, finished: Boolean): HabitTrack =
        HabitTrack(
            id = UUID.randomUUID(),
            goalId = UUID.randomUUID(),
            userId = owner,
            startDate = LocalDate.of(2026, 1, 1),
            currentDay = 1,
            isFinished = finished,
            checkpoints = emptyList()
        )

    private fun profileFor(id: UUID, timezone: String): UserProfile =
        UserProfile(
            id = id,
            keycloakId = id.toString(),
            displayName = "User",
            email = "user@example.com",
            timezone = timezone,
            createdAt = fixedInstant,
            updatedAt = fixedInstant
        )

    // --- in-memory fakes ---

    private class FakeGoalRepository : GoalRepository {
        private val store = mutableListOf<GoalProfile>()
        fun add(goal: GoalProfile) { store.add(goal) }
        override suspend fun findById(id: UUID): GoalProfile? = store.find { it.id == id }
        override suspend fun findByUserId(userId: UUID): List<GoalProfile> = store.filter { it.userId == userId }
        override suspend fun findByUserIdAndName(userId: UUID, name: String): GoalProfile? =
            store.find { it.userId == userId && it.name.equals(name, ignoreCase = true) }
        override suspend fun save(goal: GoalProfile): GoalProfile { store.add(goal); return goal }
        override suspend fun delete(id: UUID) { store.removeIf { it.id == id } }
        override suspend fun countActiveByUserId(userId: UUID): Int =
            store.count { it.userId == userId && it.isActive }
    }

    private class FakeDailyTodoRepository : DailyTodoRepository {
        private val store = mutableListOf<DailyTodoItem>()
        fun add(item: DailyTodoItem) { store.add(item) }
        override suspend fun findById(id: UUID): DailyTodoItem? = store.find { it.id == id }
        override suspend fun findByUserIdAndDate(userId: UUID, date: LocalDate): List<DailyTodoItem> =
            store.filter { it.userId == userId && it.dayDate == date }
        override suspend fun countByUserIdAndDate(userId: UUID, date: LocalDate): Int =
            store.count { it.userId == userId && it.dayDate == date }
        override suspend fun save(item: DailyTodoItem): DailyTodoItem { store.add(item); return item }
        override suspend fun delete(id: UUID) { store.removeIf { it.id == id } }
    }

    private class FakeHabitTrackRepository : HabitTrackRepository {
        private val store = mutableListOf<HabitTrack>()
        fun add(track: HabitTrack) { store.add(track) }
        override suspend fun findById(id: UUID): HabitTrack? = store.find { it.id == id }
        override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> =
            store.filter { it.goalId == goalId && !it.isFinished }
        override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> =
            store.filter { it.userId == userId && !it.isFinished }
        override suspend fun save(track: HabitTrack): HabitTrack { store.add(track); return track }
        override suspend fun deleteByGoalId(goalId: UUID) { store.removeIf { it.goalId == goalId } }
        override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) {}
        override suspend fun countActiveByUserId(userId: UUID): Int =
            store.count { it.userId == userId && !it.isFinished }
    }

    private class FakeUserProfileRepository : UserProfileRepository {
        private val store = mutableMapOf<UUID, UserProfile>()
        fun add(profile: UserProfile) { store[profile.id] = profile }
        override suspend fun findById(id: UUID): UserProfile? = store[id]
        override suspend fun findByKeycloakId(keycloakId: String): UserProfile? =
            store.values.find { it.keycloakId == keycloakId }
        override suspend fun save(profile: UserProfile): UserProfile { store[profile.id] = profile; return profile }
    }
}
