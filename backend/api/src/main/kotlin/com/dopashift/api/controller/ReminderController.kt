package com.dopashift.api.controller

import com.dopashift.api.dto.ReminderConditionDto
import com.dopashift.api.dto.ReminderCreateDto
import com.dopashift.api.dto.ReminderResponseDto
import com.dopashift.api.dto.ReminderUpdateDto
import com.dopashift.api.dto.RecurrenceRuleDto
import com.dopashift.domain.entity.ConditionType
import com.dopashift.domain.entity.RecurrenceRule
import com.dopashift.domain.entity.RecurrenceType
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderCondition
import com.dopashift.domain.entity.ReminderEntityType
import com.dopashift.domain.repository.ReminderRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * REST controller for reminder CRUD operations.
 * All endpoints require OAuth2 JWT authentication and scope results to the authenticated user.
 *
 * Supports all recurrence types (daily, specific weekdays, weekly, monthly, custom interval)
 * and condition types (entity incomplete).
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4
 */
@RestController
@RequestMapping("/v1/reminders")
class ReminderController(
    private val reminderRepository: ReminderRepository
) {

    /**
     * POST /v1/reminders
     *
     * Creates a new reminder for the authenticated user.
     * Supports one-off, repeating, and conditional reminders.
     *
     * Requirements: 5.1, 5.2, 5.3, 5.4
     */
    @PostMapping
    suspend fun create(
        @Valid @RequestBody request: ReminderCreateDto,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<ReminderResponseDto> {
        val userId = UUID.fromString(jwt.subject)

        val reminder = Reminder(
            id = UUID.randomUUID(),
            userId = userId,
            entityType = parseEntityType(request.entityType),
            entityId = request.entityId,
            scheduledTime = LocalTime.parse(request.scheduledTime),
            scheduledDate = request.scheduledDate?.let { LocalDate.parse(it) },
            recurrence = request.recurrence?.toDomain(),
            condition = request.condition?.toDomain(),
            escalationInterval = Duration.ofMinutes(request.escalationIntervalMinutes),
            maxEscalations = request.maxEscalations
        )

        val saved = reminderRepository.save(reminder)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponseDto())
    }

    /**
     * GET /v1/reminders
     *
     * Returns all active reminders for the authenticated user.
     *
     * Requirements: 5.1
     */
    @GetMapping
    suspend fun list(
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<ReminderResponseDto>> {
        val userId = UUID.fromString(jwt.subject)
        val reminders = reminderRepository.findActiveByUserId(userId)
        return ResponseEntity.ok(reminders.map { it.toResponseDto() })
    }

    /**
     * PUT /v1/reminders/{id}
     *
     * Updates an existing reminder. Only provided fields are updated.
     * The reminder must belong to the authenticated user.
     *
     * Requirements: 5.1, 5.2, 5.3, 5.4
     */
    @PutMapping("/{id}")
    suspend fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReminderUpdateDto,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<ReminderResponseDto> {
        val userId = UUID.fromString(jwt.subject)
        val existing = reminderRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        if (existing.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = existing.copy(
            scheduledTime = request.scheduledTime?.let { LocalTime.parse(it) } ?: existing.scheduledTime,
            scheduledDate = if (request.scheduledDate != null) LocalDate.parse(request.scheduledDate) else existing.scheduledDate,
            recurrence = if (request.recurrence != null) request.recurrence.toDomain() else existing.recurrence,
            condition = if (request.condition != null) request.condition.toDomain() else existing.condition,
            escalationInterval = request.escalationIntervalMinutes?.let { Duration.ofMinutes(it) } ?: existing.escalationInterval,
            maxEscalations = request.maxEscalations ?: existing.maxEscalations,
            isActive = request.isActive ?: existing.isActive
        )

        val saved = reminderRepository.save(updated)
        return ResponseEntity.ok(saved.toResponseDto())
    }

    /**
     * DELETE /v1/reminders/{id}
     *
     * Deletes a reminder. The reminder must belong to the authenticated user.
     *
     * Requirements: 5.1
     */
    @DeleteMapping("/{id}")
    suspend fun delete(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        val userId = UUID.fromString(jwt.subject)
        val existing = reminderRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        if (existing.userId != userId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        reminderRepository.delete(id)
        return ResponseEntity.noContent().build()
    }

    // --- Mapping helpers ---

    private fun parseEntityType(type: String): ReminderEntityType =
        ReminderEntityType.valueOf(type.uppercase())

    private fun RecurrenceRuleDto.toDomain(): RecurrenceRule = RecurrenceRule(
        type = RecurrenceType.valueOf(type.uppercase()),
        weekdays = weekdays,
        intervalDays = intervalDays
    )

    private fun ReminderConditionDto.toDomain(): ReminderCondition = ReminderCondition(
        type = ConditionType.valueOf(type.uppercase())
    )

    private fun Reminder.toResponseDto(): ReminderResponseDto = ReminderResponseDto(
        id = id,
        userId = userId,
        entityType = entityType.name,
        entityId = entityId,
        scheduledTime = scheduledTime.toString(),
        scheduledDate = scheduledDate?.toString(),
        recurrence = recurrence?.let {
            RecurrenceRuleDto(
                type = it.type.name,
                weekdays = it.weekdays,
                intervalDays = it.intervalDays
            )
        },
        condition = condition?.let {
            ReminderConditionDto(type = it.type.name)
        },
        escalationIntervalMinutes = escalationInterval.toMinutes(),
        maxEscalations = maxEscalations,
        escalationCount = escalationCount,
        isActive = isActive
    )
}
