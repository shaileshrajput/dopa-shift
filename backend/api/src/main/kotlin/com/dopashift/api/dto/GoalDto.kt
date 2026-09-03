package com.dopashift.api.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Request body for POST /v1/goals — create a new goal.
 *
 * The optional nested [habitTrack] enables Inline_Goal_Capture: when present, the goal
 * and the habit track (plus its checkpoints) are committed in a single server-side
 * transaction (DQC-3.3). The field is additive — existing payloads without it remain
 * valid.
 */
data class CreateGoalRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Category is required")
    @field:Size(min = 1, max = 50, message = "Category must be between 1 and 50 characters")
    val category: String,

    @field:NotEmpty(message = "At least one keyword is required")
    @field:Size(max = 20, message = "A goal can have at most 20 keywords")
    val keywords: List<@Size(min = 1, max = 50, message = "Each keyword must be between 1 and 50 characters") String>,

    @field:Valid
    val habitTrack: NestedHabitTrackRequest? = null
)

/**
 * Optional nested habit-track payload for POST /v1/goals.
 * When supplied, the habit track is committed atomically with its goal.
 * The habit references the goal created in the same request — the client never
 * supplies a goalId here, upholding the no-orphan invariant by construction.
 */
data class NestedHabitTrackRequest(
    val startDate: LocalDate? = null,

    @field:NotEmpty(message = "At least one checkpoint description is required")
    @field:Size(min = 1, max = 30, message = "Descriptions must contain between 1 and 30 items")
    val descriptions: List<@Size(min = 1, max = 200, message = "Each description must be between 1 and 200 characters") String>
)

/**
 * Request body for PUT /v1/goals/{id} — update an existing goal.
 */
data class UpdateGoalRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    val name: String,

    @field:NotBlank(message = "Category is required")
    @field:Size(min = 1, max = 50, message = "Category must be between 1 and 50 characters")
    val category: String,

    @field:NotEmpty(message = "At least one keyword is required")
    @field:Size(max = 20, message = "A goal can have at most 20 keywords")
    val keywords: List<@Size(min = 1, max = 50, message = "Each keyword must be between 1 and 50 characters") String>,

    val isActive: Boolean = true
)

/**
 * Response body for goal endpoints.
 * Used for both single-goal responses and list items.
 *
 * [habitTrack] is populated only when a goal was created with a nested habit track
 * via Inline_Goal_Capture; it is null (and omitted from JSON) otherwise, keeping the
 * response additive and non-breaking for existing consumers.
 */
data class GoalResponse(
    val id: UUID,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val habitTrack: HabitTrackDetailResponse? = null
)

/**
 * Response body for GET /v1/goals — list of goals.
 */
data class GoalListResponse(
    val goals: List<GoalResponse>
)
