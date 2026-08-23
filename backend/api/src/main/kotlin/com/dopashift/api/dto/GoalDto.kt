package com.dopashift.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * Request body for POST /v1/goals — create a new goal.
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
    val keywords: List<@Size(min = 1, max = 50, message = "Each keyword must be between 1 and 50 characters") String>
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
 */
data class GoalResponse(
    val id: UUID,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val isActive: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Response body for GET /v1/goals — list of goals.
 */
data class GoalListResponse(
    val goals: List<GoalResponse>
)
