package com.dopashift.application.job.handlers

import com.dopashift.application.job.JobType
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.entity.UserProfile
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.UserProfileRepository
import com.dopashift.domain.usecase.habit.RecordMissedDayUseCase
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MissedHabitDetectionJobHandlerTest {

    private val userId = UUID.randomUUID()
    private val goalId = UUID.randomUUID()

    // Fixed clock: 2024-06-16 at 00:05 in Asia/Kolkata (so yesterday = 2024-06-15)
    private val kolkataZone = ZoneId.of("Asia/Kolkata")
    // 2024-06-16 00:05 IST = 2024-06-15 18:35 UTC
    private val fixedInstant = Instant.parse("2024-06-15T18:35:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private fun buildTrack(
        trackId: UUID = UUID.randomUUID(),
        startDate: LocalDate = LocalDate.of(2024, 6, 1),
        currentDay: Int = 1,
        isFinished: Boolean = false,
        checkpoints: List<HabitCheckpoint> = emptyList()
    ): HabitTrack = HabitTrack(
        id = trackId,
        goalId = goalId,
        userId = userId,
        startDate = startDate,
        currentDay = currentDay,
        isFinished = isFinished,
        checkpoints = checkpoints
    )

    private fun buildCheckpoint(
        trackId: UUID,
        dayNumber: Int,
        status: CheckpointStatus = CheckpointStatus.PENDING
    ): HabitCheckpoint = HabitCheckpoint(
        id = UUID.randomUUID(),
        habitTrackId = trackId,
        dayNumber = dayNumber,
        description = "Day $dayNumber habit",
        status = status,
        completedAt = if (status == CheckpointStatus.COMPLETED) Instant.now() else null
    )

    private fun createHandler(
        fakeRepo: FakeHabitTrackRepository = FakeHabitTrackRepository(),
        fakeUserRepo: FakeUserProfileRepository = FakeUserProfileRepository()
    ): Triple<MissedHabitDetectionJobHandler, FakeHabitTrackRepository, FakeUserProfileRepository> {
        val recordMissedDayUseCase = RecordMissedDayUseCase(fakeRepo)
        val handler = MissedHabitDetectionJobHandler(
            habitTrackRepository = fakeRepo,
            userProfileRepository = fakeUserRepo,
            recordMissedDayUseCase = recordMissedDayUseCase,
            clock = fixedClock
        )
        return Triple(handler, fakeRepo, fakeUserRepo)
    }

    @Test
    fun `jobType is MISSED_HABIT_DETECTION`() {
        val (handler, _, _) = createHandler()
        assertEquals(JobType.MISSED_HABIT_DETECTION, handler.jobType)
    }

    @Test
    fun `marks PENDING checkpoint as MISSED for yesterday`() = runTest {
        val trackId = UUID.randomUUID()
        // Start date June 1, so June 15 = day 15
        val startDate = LocalDate.of(2024, 6, 1)
        val checkpoints = (1..30).map { day ->
            buildCheckpoint(trackId, day, CheckpointStatus.PENDING)
        }
        val track = buildTrack(
            trackId = trackId,
            startDate = startDate,
            currentDay = 15,
            checkpoints = checkpoints
        )

        val fakeRepo = FakeHabitTrackRepository()
        fakeRepo.tracks[trackId] = track

        val (handler, repo, _) = createHandler(fakeRepo)

        // Payload: user in Asia/Kolkata. Clock is 2024-06-15T18:35Z = 2024-06-16T00:05 IST
        // Yesterday in IST = 2024-06-15 = day 15 from startDate
        val payload = """{"userId": "$userId", "timezone": "Asia/Kolkata"}"""
        handler.handle(payload)

        // Day 15 should now be MISSED
        val updatedTrack = repo.tracks[trackId]!!
        val day15 = updatedTrack.checkpoints.find { it.dayNumber == 15 }!!
        assertEquals(CheckpointStatus.MISSED, day15.status)
    }

    @Test
    fun `does not mark COMPLETED checkpoint as MISSED`() = runTest {
        val trackId = UUID.randomUUID()
        val startDate = LocalDate.of(2024, 6, 1)
        val checkpoints = (1..30).map { day ->
            val status = if (day == 15) CheckpointStatus.COMPLETED else CheckpointStatus.PENDING
            buildCheckpoint(trackId, day, status)
        }
        val track = buildTrack(
            trackId = trackId,
            startDate = startDate,
            currentDay = 16,
            checkpoints = checkpoints
        )

        val fakeRepo = FakeHabitTrackRepository()
        fakeRepo.tracks[trackId] = track

        val (handler, repo, _) = createHandler(fakeRepo)

        val payload = """{"userId": "$userId", "timezone": "Asia/Kolkata"}"""
        handler.handle(payload)

        // Day 15 should remain COMPLETED
        val updatedTrack = repo.tracks[trackId]!!
        val day15 = updatedTrack.checkpoints.find { it.dayNumber == 15 }!!
        assertEquals(CheckpointStatus.COMPLETED, day15.status)
    }

    @Test
    fun `does not process finished tracks`() = runTest {
        val trackId = UUID.randomUUID()
        val startDate = LocalDate.of(2024, 6, 1)
        val checkpoints = (1..30).map { day ->
            buildCheckpoint(trackId, day, CheckpointStatus.PENDING)
        }
        val track = buildTrack(
            trackId = trackId,
            startDate = startDate,
            currentDay = 15,
            isFinished = true,
            checkpoints = checkpoints
        )

        val fakeRepo = FakeHabitTrackRepository()
        fakeRepo.tracks[trackId] = track

        val (handler, repo, _) = createHandler(fakeRepo)

        val payload = """{"userId": "$userId", "timezone": "Asia/Kolkata"}"""
        handler.handle(payload)

        // Day 15 should remain PENDING (track is finished, not processed)
        val updatedTrack = repo.tracks[trackId]!!
        val day15 = updatedTrack.checkpoints.find { it.dayNumber == 15 }!!
        assertEquals(CheckpointStatus.PENDING, day15.status)
    }

    @Test
    fun `skips days outside 1-30 range`() = runTest {
        val trackId = UUID.randomUUID()
        // Start date is June 16 in IST, so yesterday (June 15) would be day 0 (before start)
        val startDate = LocalDate.of(2024, 6, 16)
        val checkpoints = (1..30).map { day ->
            buildCheckpoint(trackId, day, CheckpointStatus.PENDING)
        }
        val track = buildTrack(
            trackId = trackId,
            startDate = startDate,
            currentDay = 1,
            checkpoints = checkpoints
        )

        val fakeRepo = FakeHabitTrackRepository()
        fakeRepo.tracks[trackId] = track

        val (handler, repo, _) = createHandler(fakeRepo)

        val payload = """{"userId": "$userId", "timezone": "Asia/Kolkata"}"""
        handler.handle(payload)

        // No checkpoints should be marked MISSED because yesterday is before start date
        val updatedTrack = repo.tracks[trackId]!!
        assertTrue(updatedTrack.checkpoints.all { it.status == CheckpointStatus.PENDING })
    }

    @Test
    fun `processes multiple active tracks for same user`() = runTest {
        val trackId1 = UUID.randomUUID()
        val trackId2 = UUID.randomUUID()
        val startDate = LocalDate.of(2024, 6, 1)

        val checkpoints1 = (1..30).map { buildCheckpoint(trackId1, it) }
        val checkpoints2 = (1..30).map { buildCheckpoint(trackId2, it) }

        val track1 = buildTrack(trackId = trackId1, startDate = startDate, currentDay = 15, checkpoints = checkpoints1)
        val track2 = buildTrack(trackId = trackId2, startDate = startDate, currentDay = 15, checkpoints = checkpoints2)

        val fakeRepo = FakeHabitTrackRepository()
        fakeRepo.tracks[trackId1] = track1
        fakeRepo.tracks[trackId2] = track2

        val (handler, repo, _) = createHandler(fakeRepo)

        val payload = """{"userId": "$userId", "timezone": "Asia/Kolkata"}"""
        handler.handle(payload)

        // Day 15 on both tracks should be MISSED
        assertEquals(CheckpointStatus.MISSED, repo.tracks[trackId1]!!.checkpoints.find { it.dayNumber == 15 }!!.status)
        assertEquals(CheckpointStatus.MISSED, repo.tracks[trackId2]!!.checkpoints.find { it.dayNumber == 15 }!!.status)
    }

    @Test
    fun `parses single user payload correctly`() {
        val (handler, _, _) = createHandler()
        val uid = UUID.randomUUID()
        val payload = """{"userId": "$uid", "timezone": "America/New_York"}"""

        val parsed = handler.parsePayload(payload)
        val singleUser = parsed as MissedHabitDetectionJobHandler.Payload.SingleUser
        assertEquals(uid, singleUser.userId)
        assertEquals("America/New_York", singleUser.timezone)
    }

    @Test
    fun `parses batch mode payload correctly`() {
        val (handler, _, _) = createHandler()
        val payload = """{"batchMode": true}"""

        val parsed = handler.parsePayload(payload)
        assertTrue(parsed is MissedHabitDetectionJobHandler.Payload.BatchMode)
    }

    @Test
    fun `rejects invalid payload`() {
        val (handler, _, _) = createHandler()
        val payload = """{"invalid": "data"}"""

        assertFailsWith<IllegalArgumentException> {
            handler.parsePayload(payload)
        }
    }

    @Test
    fun `computeDayNumber returns correct day numbers`() {
        val (handler, _, _) = createHandler()
        val startDate = LocalDate.of(2024, 6, 1)

        assertEquals(1, handler.computeDayNumber(startDate, LocalDate.of(2024, 6, 1)))
        assertEquals(15, handler.computeDayNumber(startDate, LocalDate.of(2024, 6, 15)))
        assertEquals(30, handler.computeDayNumber(startDate, LocalDate.of(2024, 6, 30)))
    }

    @Test
    fun `computeDayNumber returns out-of-range for dates before start`() {
        val (handler, _, _) = createHandler()
        val startDate = LocalDate.of(2024, 6, 10)

        // Day before start = day 0
        assertEquals(0, handler.computeDayNumber(startDate, LocalDate.of(2024, 6, 9)))
    }

    @Test
    fun `batch mode does not throw`() = runTest {
        val (handler, _, _) = createHandler()
        val payload = """{"batchMode": true}"""

        // Should not throw - batch mode is a no-op at application layer
        handler.handle(payload)
    }
}

/**
 * Fake HabitTrackRepository for testing.
 */
class FakeHabitTrackRepository : HabitTrackRepository {
    val tracks = mutableMapOf<UUID, HabitTrack>()

    override suspend fun findById(id: UUID): HabitTrack? = tracks[id]

    override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> =
        tracks.values.filter { it.goalId == goalId && !it.isFinished }

    override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> =
        tracks.values.filter { it.userId == userId && !it.isFinished }

    override suspend fun save(track: HabitTrack): HabitTrack {
        tracks[track.id] = track
        return track
    }

    override suspend fun deleteByGoalId(goalId: UUID) {
        tracks.entries.removeAll { it.value.goalId == goalId }
    }

    override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID) {
        tracks.replaceAll { _, track ->
            if (track.goalId == fromGoalId) track.copy(goalId = toGoalId) else track
        }
    }

    override suspend fun countActiveByUserId(userId: UUID): Int =
        tracks.values.count { it.userId == userId && !it.isFinished }
}

/**
 * Fake UserProfileRepository for testing.
 */
class FakeUserProfileRepository : UserProfileRepository {
    val profiles = mutableMapOf<UUID, UserProfile>()

    override suspend fun findById(id: UUID): UserProfile? = profiles[id]

    override suspend fun findByKeycloakId(keycloakId: String): UserProfile? =
        profiles.values.find { it.keycloakId == keycloakId }

    override suspend fun save(profile: UserProfile): UserProfile {
        profiles[profile.id] = profile
        return profile
    }
}
