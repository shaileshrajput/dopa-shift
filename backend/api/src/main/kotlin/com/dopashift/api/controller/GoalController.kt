package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.dto.CreateGoalRequest
import com.dopashift.api.dto.GoalListResponse
import com.dopashift.api.dto.GoalResponse
import com.dopashift.api.dto.UpdateGoalRequest
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.usecase.goal.CreateGoalCommand
import com.dopashift.domain.usecase.goal.CreateGoalUseCase
import com.dopashift.domain.usecase.goal.DeleteGoalAction
import com.dopashift.domain.usecase.goal.DeleteGoalCommand
import com.dopashift.domain.usecase.goal.DeleteGoalUseCase
import com.dopashift.domain.usecase.goal.UpdateGoalCommand
import com.dopashift.domain.usecase.goal.UpdateGoalUseCase
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.exception.GoalNotFoundException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST controller for goal profile management (CRUD).
 *
 * All endpoints require OAuth2 JWT authentication and scope results
 * to the authenticated user via AuthenticatedUser component.
 *
 * Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 1.7
 */
@RestController
@RequestMapping("/v1/goals")
class GoalController(
    private val createGoalUseCase: CreateGoalUseCase,
    private val updateGoalUseCase: UpdateGoalUseCase,
    private val deleteGoalUseCase: DeleteGoalUseCase,
    private val goalRepository: GoalRepository,
    private val authenticatedUser: AuthenticatedUser
) {

    /**
     * POST /v1/goals — Create a new goal with name uniqueness validation.
     *
     * Returns 201 Created with the created goal.
     * Returns 409 Conflict if name already exists for the user.
     * Returns 400 Bad Request for invalid input.
     *
     * Requirements: 1.1, 1.2, 1.7
     */
    @PostMapping
    suspend fun createGoal(
        @Valid @RequestBody request: CreateGoalRequest
    ): ResponseEntity<GoalResponse> {
        val userId = authenticatedUser.getUserId()

        val command = CreateGoalCommand(
            userId = userId,
            name = request.name,
            category = request.category,
            keywords = request.keywords
        )

        val goal = createGoalUseCase.execute(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(goal.toResponse())
    }

    /**
     * GET /v1/goals — List all goals for the authenticated user.
     *
     * Returns 200 OK with the list of goals.
     */
    @GetMapping
    suspend fun listGoals(): ResponseEntity<GoalListResponse> {
        val userId = authenticatedUser.getUserId()
        val goals = goalRepository.findByUserId(userId)
        return ResponseEntity.ok(GoalListResponse(goals = goals.map { it.toResponse() }))
    }

    /**
     * GET /v1/goals/{id} — Get goal detail with keywords and stats.
     *
     * Returns 200 OK with the goal detail.
     * Returns 404 Not Found if goal does not exist or belongs to another user.
     */
    @GetMapping("/{id}")
    suspend fun getGoal(@PathVariable id: UUID): ResponseEntity<GoalResponse> {
        val userId = authenticatedUser.getUserId()
        val goal = goalRepository.findById(id)
            ?: throw GoalNotFoundException(id.toString())

        // Ensure the goal belongs to the authenticated user
        if (goal.userId != userId) {
            throw GoalNotFoundException(id.toString())
        }

        return ResponseEntity.ok(goal.toResponse())
    }

    /**
     * PUT /v1/goals/{id} — Update an existing goal.
     *
     * Returns 200 OK with the updated goal.
     * Returns 404 Not Found if goal does not exist or belongs to another user.
     * Returns 409 Conflict if new name already exists for the user.
     * Returns 400 Bad Request for invalid input.
     *
     * Requirements: 1.1, 1.2, 1.7
     */
    @PutMapping("/{id}")
    suspend fun updateGoal(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateGoalRequest
    ): ResponseEntity<GoalResponse> {
        val userId = authenticatedUser.getUserId()

        // Verify the goal belongs to the authenticated user
        val existing = goalRepository.findById(id)
            ?: throw GoalNotFoundException(id.toString())
        if (existing.userId != userId) {
            throw GoalNotFoundException(id.toString())
        }

        val command = UpdateGoalCommand(
            goalId = id,
            name = request.name,
            category = request.category,
            keywords = request.keywords,
            isActive = request.isActive
        )

        val updated = updateGoalUseCase.execute(command)
        return ResponseEntity.ok(updated.toResponse())
    }

    /**
     * DELETE /v1/goals/{id} — Delete a goal with dependency resolution.
     *
     * Query parameters:
     * - action=DELETE_ALL — delete all dependent items (checklist items, habit tracks, interception rules)
     * - action=REASSIGN&targetGoalId={uuid} — reassign all dependents to the specified target goal
     *
     * Returns 204 No Content on successful deletion.
     * Returns 404 Not Found if goal does not exist or belongs to another user.
     * Returns 400 Bad Request if action is missing or invalid.
     *
     * Requirements: 1.4, 1.5, 1.6
     */
    @DeleteMapping("/{id}")
    suspend fun deleteGoal(
        @PathVariable id: UUID,
        @RequestParam action: String,
        @RequestParam(required = false) targetGoalId: UUID?
    ): ResponseEntity<Void> {
        val userId = authenticatedUser.getUserId()

        // Verify the goal belongs to the authenticated user
        val existing = goalRepository.findById(id)
            ?: throw GoalNotFoundException(id.toString())
        if (existing.userId != userId) {
            throw GoalNotFoundException(id.toString())
        }

        val deleteAction = when (action.uppercase()) {
            "DELETE_ALL" -> DeleteGoalAction.DeleteAll
            "REASSIGN" -> {
                val target = targetGoalId
                    ?: throw IllegalArgumentException("targetGoalId is required when action is REASSIGN")
                DeleteGoalAction.Reassign(target)
            }
            else -> throw IllegalArgumentException("Invalid action: $action. Must be DELETE_ALL or REASSIGN")
        }

        val command = DeleteGoalCommand(
            goalId = id,
            action = deleteAction
        )

        deleteGoalUseCase.execute(command)
        return ResponseEntity.noContent().build()
    }

    private fun GoalProfile.toResponse(): GoalResponse = GoalResponse(
        id = id,
        name = name,
        category = category,
        keywords = keywords,
        isActive = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
