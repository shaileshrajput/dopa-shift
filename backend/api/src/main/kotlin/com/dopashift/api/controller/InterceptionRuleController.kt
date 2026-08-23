package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.dto.CreateInterceptionRuleRequest
import com.dopashift.api.dto.InterceptionRuleResponse
import com.dopashift.api.dto.UpdateInterceptionRuleRequest
import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.repository.InterceptionRuleRepository
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
 * REST controller for interception rule CRUD operations.
 * All endpoints require OAuth2 JWT authentication and scope results to the authenticated user.
 *
 * Interception rules define which apps/sites are tracked and their daily allowance (1-480 minutes).
 *
 * Requirements: 2.3
 */
@RestController
@RequestMapping("/v1/interception/rules")
class InterceptionRuleController(
    private val authenticatedUser: AuthenticatedUser,
    private val interceptionRuleRepository: InterceptionRuleRepository,
    private val clock: Clock
) {

    /**
     * POST /v1/interception/rules
     *
     * Creates a new interception rule for the authenticated user.
     * Validates daily allowance is between 1 and 480 minutes.
     *
     * Requirements: 2.3
     */
    @PostMapping
    suspend fun create(
        @Valid @RequestBody request: CreateInterceptionRuleRequest
    ): ResponseEntity<InterceptionRuleResponse> {
        val userId = authenticatedUser.getUserId()

        val rule = InterceptionRule(
            id = UUID.randomUUID(),
            userId = userId,
            goalId = request.goalId,
            appPackageName = request.appPackageName,
            siteDomain = request.siteDomain,
            dailyAllowanceMinutes = request.dailyAllowanceMinutes,
            isActive = true,
            createdAt = Instant.now(clock)
        )

        val saved = interceptionRuleRepository.save(rule)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    /**
     * GET /v1/interception/rules
     *
     * Returns all interception rules for the authenticated user.
     *
     * Requirements: 2.3
     */
    @GetMapping
    suspend fun list(): ResponseEntity<List<InterceptionRuleResponse>> {
        val userId = authenticatedUser.getUserId()
        val rules = interceptionRuleRepository.findByUserId(userId)
        return ResponseEntity.ok(rules.map { it.toResponse() })
    }

    /**
     * PUT /v1/interception/rules/{id}
     *
     * Updates an existing interception rule. Only provided fields are updated.
     * The rule must belong to the authenticated user.
     * Validates daily allowance is between 1 and 480 minutes when provided.
     *
     * Requirements: 2.3
     */
    @PutMapping("/{id}")
    suspend fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateInterceptionRuleRequest
    ): ResponseEntity<InterceptionRuleResponse> {
        val userId = authenticatedUser.getUserId()

        val existing = interceptionRuleRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        if (existing.userId != userId) {
            return ResponseEntity.notFound().build()
        }

        val updated = existing.copy(
            goalId = request.goalId ?: existing.goalId,
            appPackageName = request.appPackageName ?: existing.appPackageName,
            siteDomain = request.siteDomain ?: existing.siteDomain,
            dailyAllowanceMinutes = request.dailyAllowanceMinutes ?: existing.dailyAllowanceMinutes,
            isActive = request.isActive ?: existing.isActive
        )

        val saved = interceptionRuleRepository.save(updated)
        return ResponseEntity.ok(saved.toResponse())
    }

    /**
     * DELETE /v1/interception/rules/{id}
     *
     * Deletes an interception rule. The rule must belong to the authenticated user.
     *
     * Requirements: 2.3
     */
    @DeleteMapping("/{id}")
    suspend fun delete(
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        val userId = authenticatedUser.getUserId()

        val existing = interceptionRuleRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        if (existing.userId != userId) {
            return ResponseEntity.notFound().build()
        }

        interceptionRuleRepository.delete(id)
        return ResponseEntity.noContent().build()
    }

    // --- Mapping helpers ---

    private fun InterceptionRule.toResponse(): InterceptionRuleResponse = InterceptionRuleResponse(
        id = id,
        userId = userId,
        goalId = goalId,
        appPackageName = appPackageName,
        siteDomain = siteDomain,
        dailyAllowanceMinutes = dailyAllowanceMinutes,
        isActive = isActive,
        createdAt = createdAt.toString()
    )
}
