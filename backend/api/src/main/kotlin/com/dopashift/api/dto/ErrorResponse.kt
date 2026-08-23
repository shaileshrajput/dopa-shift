package com.dopashift.api.dto

import java.time.Instant

/**
 * Standardized error response format.
 * All API errors are wrapped in this envelope for consistency and traceability.
 */
data class ErrorResponse(
    val error: ErrorDetail
)

data class ErrorDetail(
    val code: String,
    val message: String,
    val details: Map<String, Any?>? = null,
    val correlationId: String,
    val timestamp: Instant = Instant.now()
)
