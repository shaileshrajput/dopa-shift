package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.dto.CreateChecklistItemRequest
import com.dopashift.api.dto.GoalChecklistItemResponse
import com.dopashift.api.dto.UpdateChecklistItemRequest
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.repository.GoalChecklistItemRepository
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
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * REST controller for goal-scoped checklist item management (CRUD).
 *
 * All endpoints require OAuth2 JWT authentication and scope results
 * to the authenticated user via AuthenticatedUser component.
 *
 * Requirements: 1.3
 */
@RestController
@RequestMapping("/v1/goals")
class GoalChecklistController(
    private val authenticatedUser: AuthenticatedUser,
    private val goalRepository: GoalRepository,
    private val goalChecklistItemRepository: GoalChecklistItemRepository,
    private val clock: Clock
) {

    /**
     * POST /v1/goals/{id}/checklist
     * Creates a new checklist item scoped to the specified goal.
     *
     * Returns 201 Created on success.
     * Returns 404 Not Found if goal does not exist or belongs to another user.
     *
     * Requirements: 1.3
     */
    @PostMapping("/{id}/checklist")
    suspend fun createChecklistItem(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateChecklistItemRequest
    ): ResponseEntity<GoalChecklistItemResponse> {
        val userId = authenticatedUser.getUserId()
        val goal = goalRepository.findById(id)
            ?: throw GoalNotFoundException(id.toString())

        if (goal.userId != userId) {
            throw GoalNotFoundException(id.toString())
        }

        val now = Instant.now(clock)
        val item = GoalChecklistItem(
            id = UUID.randomUUID(),
            goalId = id,
            userId = userId,
            text = request.text,
            isCompleted = false,
            createdAt = now,
            updatedAt = now
        )

        val saved = goalChecklistItemRepository.save(item)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(GoalChecklistItemResponse.from(saved))
    }

    /**
     * GET /v1/goals/{id}/checklist
     * Lists all checklist items for the specified goal.
     *
     * Returns 200 OK with the list of checklist items.
     * Returns 404 Not Found if goal does not exist or belongs to another user.
     *
     * Requirements: 1.3
     */
    @GetMapping("/{id}/checklist")
    suspend fun listChecklistItems(
        @PathVariable id: UUID
    ): ResponseEntity<List<GoalChecklistItemResponse>> {
        val userId = authenticatedUser.getUserId()
        val goal = goalRepository.findById(id)
            ?: throw GoalNotFoundException(id.toString())

        if (goal.userId != userId) {
            throw GoalNotFoundException(id.toString())
        }

        val items = goalChecklistItemRepository.findByGoalId(id)
        return ResponseEntity.ok(items.map { GoalChecklistItemResponse.from(it) })
    }

    /**
     * PUT /v1/goals/{goalId}/checklist/{itemId}
     * Updates an existing checklist item (text and/or completion status).
     *
     * Returns 200 OK with the updated item.
     * Returns 404 Not Found if goal or item does not exist or belongs to another user.
     *
     * Requirements: 1.3
     */
    @PutMapping("/{goalId}/checklist/{itemId}")
    suspend fun updateChecklistItem(
        @PathVariable goalId: UUID,
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: UpdateChecklistItemRequest
    ): ResponseEntity<GoalChecklistItemResponse> {
        val userId = authenticatedUser.getUserId()
        val goal = goalRepository.findById(goalId)
            ?: throw GoalNotFoundException(goalId.toString())

        if (goal.userId != userId) {
            throw GoalNotFoundException(goalId.toString())
        }

        val existing = goalChecklistItemRepository.findById(itemId)
            ?: return ResponseEntity.notFound().build()

        if (existing.goalId != goalId || existing.userId != userId) {
            return ResponseEntity.notFound().build()
        }

        val now = Instant.now(clock)
        val updated = existing.copy(
            text = request.text ?: existing.text,
            isCompleted = request.isCompleted ?: existing.isCompleted,
            updatedAt = now
        )

        val saved = goalChecklistItemRepository.save(updated)
        return ResponseEntity.ok(GoalChecklistItemResponse.from(saved))
    }

    /**
     * DELETE /v1/goals/{goalId}/checklist/{itemId}
     * Deletes an existing checklist item.
     *
     * Returns 204 No Content on success.
     * Returns 404 Not Found if goal or item does not exist or belongs to another user.
     *
     * Requirements: 1.3
     */
    @DeleteMapping("/{goalId}/checklist/{itemId}")
    suspend fun deleteChecklistItem(
        @PathVariable goalId: UUID,
        @PathVariable itemId: UUID
    ): ResponseEntity<Void> {
        val userId = authenticatedUser.getUserId()
        val goal = goalRepository.findById(goalId)
            ?: throw GoalNotFoundException(goalId.toString())

        if (goal.userId != userId) {
            throw GoalNotFoundException(goalId.toString())
        }

        val existing = goalChecklistItemRepository.findById(itemId)
            ?: return ResponseEntity.notFound().build()

        if (existing.goalId != goalId || existing.userId != userId) {
            return ResponseEntity.notFound().build()
        }

        goalChecklistItemRepository.delete(itemId)
        return ResponseEntity.noContent().build()
    }
}
