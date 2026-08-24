package com.dopashift.infrastructure.persistence

import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.HabitCheckpoint
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.repository.HabitTrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.util.UUID

@Repository
class JdbcHabitTrackRepository(
    private val jdbcTemplate: JdbcTemplate
) : HabitTrackRepository {

    override suspend fun findById(id: UUID): HabitTrack? = withContext(Dispatchers.IO) {
        val tracks = jdbcTemplate.query(
            "SELECT * FROM habit_tracks WHERE id = ?",
            { rs, _ -> mapTrack(rs) },
            id
        )
        tracks.firstOrNull()?.let { attachCheckpoints(it) }
    }

    override suspend fun findActiveByGoalId(goalId: UUID): List<HabitTrack> = withContext(Dispatchers.IO) {
        val tracks = jdbcTemplate.query(
            "SELECT * FROM habit_tracks WHERE goal_id = ? AND is_finished = false",
            { rs, _ -> mapTrack(rs) },
            goalId
        )
        tracks.map { attachCheckpoints(it) }
    }

    override suspend fun findActiveByUserId(userId: UUID): List<HabitTrack> = withContext(Dispatchers.IO) {
        val tracks = jdbcTemplate.query(
            "SELECT * FROM habit_tracks WHERE user_id = ? AND is_finished = false",
            { rs, _ -> mapTrack(rs) },
            userId
        )
        tracks.map { attachCheckpoints(it) }
    }

    override suspend fun save(track: HabitTrack): HabitTrack = withContext(Dispatchers.IO) {
        val exists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM habit_tracks WHERE id = ?",
            Int::class.java,
            track.id
        ) ?: 0

        if (exists > 0) {
            jdbcTemplate.update(
                """UPDATE habit_tracks SET current_day = ?, is_finished = ?, updated_at = NOW()
                   WHERE id = ?""",
                track.currentDay, track.isFinished, track.id
            )
        } else {
            jdbcTemplate.update(
                """INSERT INTO habit_tracks (id, goal_id, user_id, start_date, current_day, is_finished, created_at, updated_at)
                   VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())""",
                track.id, track.goalId, track.userId,
                Date.valueOf(track.startDate), track.currentDay, track.isFinished
            )
        }

        // Sync checkpoints
        track.checkpoints.forEach { cp ->
            val cpExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM habit_checkpoints WHERE id = ?",
                Int::class.java, cp.id
            ) ?: 0

            if (cpExists > 0) {
                jdbcTemplate.update(
                    """UPDATE habit_checkpoints SET status = ?, completed_at = ? WHERE id = ?""",
                    cp.status.name, cp.completedAt?.let { Timestamp.from(it) }, cp.id
                )
            } else {
                jdbcTemplate.update(
                    """INSERT INTO habit_checkpoints (id, habit_track_id, day_number, description, status, completed_at)
                       VALUES (?, ?, ?, ?, ?, ?)""",
                    cp.id, cp.habitTrackId, cp.dayNumber, cp.description,
                    cp.status.name, cp.completedAt?.let { Timestamp.from(it) }
                )
            }
        }

        track
    }

    override suspend fun deleteByGoalId(goalId: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update("DELETE FROM habit_tracks WHERE goal_id = ?", goalId)
    }

    override suspend fun reassignToGoal(fromGoalId: UUID, toGoalId: UUID): Unit = withContext(Dispatchers.IO) {
        jdbcTemplate.update(
            "UPDATE habit_tracks SET goal_id = ? WHERE goal_id = ?",
            toGoalId, fromGoalId
        )
    }

    private fun attachCheckpoints(track: HabitTrack): HabitTrack {
        val checkpoints = jdbcTemplate.query(
            "SELECT * FROM habit_checkpoints WHERE habit_track_id = ? ORDER BY day_number",
            { rs, _ -> mapCheckpoint(rs) },
            track.id
        )
        return track.copy(checkpoints = checkpoints)
    }

    private fun mapTrack(rs: ResultSet): HabitTrack = HabitTrack(
        id = rs.getObject("id", UUID::class.java),
        goalId = rs.getObject("goal_id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        startDate = rs.getDate("start_date").toLocalDate(),
        currentDay = rs.getInt("current_day"),
        isFinished = rs.getBoolean("is_finished"),
        checkpoints = emptyList()
    )

    private fun mapCheckpoint(rs: ResultSet): HabitCheckpoint = HabitCheckpoint(
        id = rs.getObject("id", UUID::class.java),
        habitTrackId = rs.getObject("habit_track_id", UUID::class.java),
        dayNumber = rs.getInt("day_number"),
        description = rs.getString("description"),
        status = CheckpointStatus.valueOf(rs.getString("status")),
        completedAt = rs.getTimestamp("completed_at")?.toInstant()
    )
}
