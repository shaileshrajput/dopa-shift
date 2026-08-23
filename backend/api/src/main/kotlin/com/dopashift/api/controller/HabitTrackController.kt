package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.dto.ActivateHabitTrackRequest
import com.dopashift.api.dto.HabitTrackDetailResponse
import com.dopashift.api.dto.HabitTrackSummaryResponse
import com.dopashift.api.dto.UpdateCheckpointRequest
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.usecase.habit.ActivateHabitTrackUseCase
import com.dopashift.domain.usecase.habit.AdvanceDayUseCase
import com.dopashift.domain.usecase.habit.MarkCheckpointUseCase
import com.dopashift.domain.usecase.habit.RecordMissedDayUseCase
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * REST controller for habit track management.
 * All endpoints require OAuth2 JWT authentication and scope results to the authenticated user.
 *
 * Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6
 */
@RestController
@RequestMapping("/v1/habits")
class HabitTrackController(
    private val authenticatedUser: AuthenticatedUser,
    private val activateHabitTrackUseCase: ActivateHabitTrackUseCase,
    private val markCheckpointUseCase: MarkCheckpointUseCase,
    private val advanceDayUseCase: AdvanceDayUseCase,
    private val recordMissedDayUseCase: RecordMissedDayUseCase,
    private val habitTrackRepository: HabitTrackRepository,
    private val clock: Clock
) {

    /**
     * POST /v1/habits
     * Activates a new 30-day habit track for a goal.
     *
     * Requirements: 6.1
     */
    @PostMapping
    suspend fun activateHabitTrack(
        @Valid @RequestBody request: ActivateHabitTrackRequest
    ): ResponseEntity<HabitTrackDetailResponse> {
        val userId = authenticatedUser.getUserId()
        val startDate = request.startDate ?: LocalDate.now(clock)

        val track = activateHabitTrackUseCase.execute(
            userId = userId,
            goalId = request.goalId,
            startDate = startDate,
            descriptions = request.descriptions
        )

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(HabitTrackDetailResponse.from(track))
    }

    /**
     * GET /v1/habits?goalId={id}
     * Lists active habit tracks for a goal.
     *
     * Requirements: 6.2, 6.5
     */
    @GetMapping
    suspend fun listHabitTracks(
        @RequestParam("goalId") goalId: UUID
    ): ResponseEntity<List<HabitTrackSummaryResponse>> {
        val tracks = habitTrackRepository.findActiveByGoalId(goalId)

        // Filter to only tracks belonging to the authenticated user
        val userId = authenticatedUser.getUserId()
        val userTracks = tracks.filter { it.userId == userId }

        return ResponseEntity.ok(userTracks.map { HabitTrackSummaryResponse.from(it) })
    }

    /**
     * GET /v1/habits/{id}
     * Returns a habit track with all checkpoints.
     *
     * Requirements: 6.2
     */
    @GetMapping("/{id}")
    suspend fun getHabitTrack(
        @PathVariable id: UUID
    ): ResponseEntity<HabitTrackDetailResponse> {
        val userId = authenticatedUser.getUserId()
        val track = habitTrackRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        if (track.userId != userId) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok(HabitTrackDetailResponse.from(track))
    }

    /**
     * PUT /v1/habits/{id}/checkpoints/{day}
     * Marks a checkpoint as COMPLETED or MISSED.
     *
     * Requirements: 6.3, 6.4
     */
    @PutMapping("/{id}/checkpoints/{day}")
    suspend fun updateCheckpoint(
        @PathVariable id: UUID,
        @PathVariable day: Int,
        @Valid @RequestBody request: UpdateCheckpointRequest
    ): ResponseEntity<HabitTrackDetailResponse> {
        val userId = authenticatedUser.getUserId()

        // Verify track belongs to user
        val track = habitTrackRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        if (track.userId != userId) {
            return ResponseEntity.notFound().build()
        }

        val updatedTrack = when (request.status) {
            CheckpointStatus.COMPLETED -> {
                val marked = markCheckpointUseCase.execute(
                    trackId = id,
                    dayNumber = day,
                    completedAt = clock.instant()
                )
                // Advance day pointer after marking as complete
                advanceDayUseCase.execute(marked.id)
            }
            CheckpointStatus.MISSED -> {
                recordMissedDayUseCase.execute(trackId = id, dayNumber = day)
            }
            CheckpointStatus.PENDING -> {
                return ResponseEntity.badRequest().build()
            }
        }

        return ResponseEntity.ok(HabitTrackDetailResponse.from(updatedTrack))
    }

    /**
     * GET /v1/habits/current
     * Returns the track with the earliest start-date that's not finished, for overlay display.
     *
     * Requirements: 6.2, 6.5
     */
    @GetMapping("/current")
    suspend fun getCurrentHabitTrack(): ResponseEntity<HabitTrackDetailResponse> {
        val userId = authenticatedUser.getUserId()
        val activeTracks = habitTrackRepository.findActiveByUserId(userId)

        if (activeTracks.isEmpty()) {
            return ResponseEntity.notFound().build()
        }

        // Return the track with the earliest start date (for overlay)
        val currentTrack = activeTracks.minByOrNull { it.startDate }
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(HabitTrackDetailResponse.from(currentTrack))
    }
}
