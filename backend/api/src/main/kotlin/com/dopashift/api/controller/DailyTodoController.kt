package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.dto.CreateTodoRequest
import com.dopashift.api.dto.DailyTodoResponse
import com.dopashift.api.dto.UpdateTodoRequest
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.usecase.todo.CreateTodoUseCase
import com.dopashift.domain.usecase.todo.DeleteTodoUseCase
import com.dopashift.domain.usecase.todo.MarkTodoCompleteUseCase
import com.dopashift.domain.usecase.todo.UpdateTodoUseCase
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
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
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * REST controller for daily todo item management.
 * All endpoints require OAuth2 JWT authentication and scope results to the authenticated user.
 *
 * Requirements: 4.1, 4.2, 4.3, 4.12
 */
@RestController
@RequestMapping("/v1/todos")
class DailyTodoController(
    private val authenticatedUser: AuthenticatedUser,
    private val createTodoUseCase: CreateTodoUseCase,
    private val updateTodoUseCase: UpdateTodoUseCase,
    private val deleteTodoUseCase: DeleteTodoUseCase,
    private val markTodoCompleteUseCase: MarkTodoCompleteUseCase,
    private val dailyTodoRepository: DailyTodoRepository,
    private val clock: Clock
) {

    /**
     * POST /v1/todos
     * Creates a new daily todo item. Enforces the 100-item-per-user-per-day limit.
     * If dayDate is not provided, defaults to today.
     *
     * Returns 201 Created on success.
     * Returns 429 Too Many Requests if the 100-item limit is exceeded (via LimitExceededException).
     *
     * Requirements: 4.1, 4.2, 4.12
     */
    @PostMapping
    suspend fun createTodo(
        @Valid @RequestBody request: CreateTodoRequest
    ): ResponseEntity<DailyTodoResponse> {
        val userId = authenticatedUser.getUserId()
        val dayDate = request.dayDate ?: LocalDate.now(clock)

        val item = createTodoUseCase.execute(
            userId = userId,
            text = request.text,
            dayDate = dayDate,
            dueDateTime = request.dueDateTime
        )

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(DailyTodoResponse.from(item))
    }

    /**
     * GET /v1/todos?date={YYYY-MM-DD}
     * Lists all daily todo items for the authenticated user on the specified date.
     * If date is not provided, defaults to today.
     *
     * Requirements: 4.1, 4.2
     */
    @GetMapping
    suspend fun listTodos(
        @RequestParam("date", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?
    ): ResponseEntity<List<DailyTodoResponse>> {
        val userId = authenticatedUser.getUserId()
        val targetDate = date ?: LocalDate.now(clock)

        val items = dailyTodoRepository.findByUserIdAndDate(userId, targetDate)
        return ResponseEntity.ok(items.map { DailyTodoResponse.from(it) })
    }

    /**
     * PUT /v1/todos/{id}
     * Updates an existing daily todo item (text, dueDateTime, and/or completion status).
     *
     * Requirements: 4.2, 4.3, 4.12
     */
    @PutMapping("/{id}")
    suspend fun updateTodo(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTodoRequest
    ): ResponseEntity<DailyTodoResponse> {
        val userId = authenticatedUser.getUserId()

        // Verify the item belongs to the authenticated user
        val existing = dailyTodoRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        if (existing.userId != userId) {
            return ResponseEntity.notFound().build()
        }

        // Handle completion status change if requested
        val afterCompletion = if (request.isCompleted != null && request.isCompleted != existing.isCompleted) {
            markTodoCompleteUseCase.execute(id, request.isCompleted)
        } else {
            existing
        }

        // Handle text and/or dueDateTime update if requested
        val updated = if (request.text != null || request.dueDateTime != null || request.clearDueDateTime) {
            updateTodoUseCase.execute(
                id = afterCompletion.id,
                text = request.text,
                dueDateTime = request.dueDateTime,
                clearDueDateTime = request.clearDueDateTime
            )
        } else {
            afterCompletion
        }

        return ResponseEntity.ok(DailyTodoResponse.from(updated))
    }

    /**
     * DELETE /v1/todos/{id}
     * Deletes an existing daily todo item.
     *
     * Requirements: 4.2
     */
    @DeleteMapping("/{id}")
    suspend fun deleteTodo(
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        val userId = authenticatedUser.getUserId()

        // Verify the item belongs to the authenticated user
        val existing = dailyTodoRepository.findById(id)
            ?: return ResponseEntity.notFound().build()

        if (existing.userId != userId) {
            return ResponseEntity.notFound().build()
        }

        deleteTodoUseCase.execute(id)
        return ResponseEntity.noContent().build()
    }

    /**
     * GET /v1/todos/pending
     * Returns all incomplete daily todo items for the authenticated user for today.
     * Used by the intercept overlay to display pending tasks.
     *
     * Requirements: 4.1, 4.3
     */
    @GetMapping("/pending")
    suspend fun getPendingTodos(): ResponseEntity<List<DailyTodoResponse>> {
        val userId = authenticatedUser.getUserId()
        val today = LocalDate.now(clock)

        val items = dailyTodoRepository.findByUserIdAndDate(userId, today)
        val pending = items.filter { !it.isCompleted }

        return ResponseEntity.ok(pending.map { DailyTodoResponse.from(it) })
    }
}
