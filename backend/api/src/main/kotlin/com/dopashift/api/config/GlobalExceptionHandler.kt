package com.dopashift.api.config

import com.dopashift.api.dto.ErrorDetail
import com.dopashift.api.dto.ErrorResponse
import com.dopashift.api.controller.PasswordPolicyViolationException
import com.dopashift.api.controller.PasswordVerificationException
import com.dopashift.domain.exception.DomainException
import com.dopashift.domain.exception.EntityNotFoundException
import com.dopashift.domain.exception.GoalNameAlreadyExistsException
import com.dopashift.domain.exception.GoalNotFoundException
import com.dopashift.domain.exception.InvalidReassignmentTargetException
import com.dopashift.domain.exception.KeywordValidationException
import com.dopashift.domain.exception.LimitExceededException
import com.dopashift.domain.exception.LlmConfigNotFoundException
import com.dopashift.domain.exception.NoActiveGoalsException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.Instant

/**
 * Global exception handler that maps domain and framework exceptions
 * to standardized error responses with correlation IDs.
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(GoalNameAlreadyExistsException::class)
    fun handleGoalNameAlreadyExists(
        ex: GoalNameAlreadyExistsException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(
            status = HttpStatus.CONFLICT,
            code = "GOAL_NAME_DUPLICATE",
            message = ex.message ?: "A goal with this name already exists.",
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(EntityNotFoundException::class, GoalNotFoundException::class, LlmConfigNotFoundException::class)
    fun handleEntityNotFound(
        ex: DomainException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(
            status = HttpStatus.NOT_FOUND,
            code = "ENTITY_NOT_FOUND",
            message = ex.message ?: "The requested entity was not found.",
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(LimitExceededException::class)
    fun handleLimitExceeded(
        ex: LimitExceededException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(
            status = HttpStatus.TOO_MANY_REQUESTS,
            code = "LIMIT_EXCEEDED",
            message = ex.message ?: "A resource limit has been exceeded.",
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(KeywordValidationException::class, InvalidReassignmentTargetException::class)
    fun handleValidationDomainException(
        ex: DomainException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = ex.message ?: "The request contains invalid data.",
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(NoActiveGoalsException::class)
    fun handleNoActiveGoals(
        ex: NoActiveGoalsException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "NO_ACTIVE_GOALS",
            message = ex.message ?: "At least one active goal is required.",
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            message = "Request validation failed.",
            details = fieldErrors,
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(PasswordVerificationException::class)
    fun handlePasswordVerification(
        ex: PasswordVerificationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(
            status = HttpStatus.UNAUTHORIZED,
            code = "PASSWORD_VERIFICATION_FAILED",
            message = ex.message ?: "Password verification failed.",
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(PasswordPolicyViolationException::class)
    fun handlePasswordPolicyViolation(
        ex: PasswordPolicyViolationException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "PASSWORD_POLICY_VIOLATION",
            message = ex.message ?: "Password does not meet security requirements.",
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_ARGUMENT",
            message = ex.message ?: "Invalid argument provided.",
            request = request,
            exception = ex
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error [correlationId={}]", getCorrelationId(request), ex)
        return buildErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_ERROR",
            message = "An unexpected error occurred.",
            request = request,
            exception = ex
        )
    }

    private fun buildErrorResponse(
        status: HttpStatus,
        code: String,
        message: String,
        details: Map<String, Any?>? = null,
        request: HttpServletRequest,
        exception: Exception
    ): ResponseEntity<ErrorResponse> {
        val correlationId = getCorrelationId(request)

        logger.warn(
            "Handling exception [correlationId={}, code={}, status={}]: {}",
            correlationId, code, status.value(), exception.message
        )

        val errorResponse = ErrorResponse(
            error = ErrorDetail(
                code = code,
                message = message,
                details = details,
                correlationId = correlationId,
                timestamp = Instant.now()
            )
        )

        return ResponseEntity.status(status).body(errorResponse)
    }

    private fun getCorrelationId(request: HttpServletRequest): String {
        return request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE) as? String
            ?: "unknown"
    }
}
