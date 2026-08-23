package com.dopashift.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/**
 * Request body for POST /v1/settings/llm — configure LLM provider.
 * Also used for PUT /v1/settings/llm — rotate key.
 *
 * Requirements: 15.1
 */
data class ConfigureLlmRequest(
    @field:NotBlank(message = "Provider type is required")
    val providerType: String,

    @field:NotBlank(message = "API key is required")
    @field:Size(min = 1, max = 256, message = "API key must be between 1 and 256 characters")
    val apiKey: String
)

/**
 * Response body for LLM configuration endpoints.
 * The API key is masked — only the last 4 characters are visible.
 *
 * Requirements: 15.1, 15.2
 */
data class LlmConfigResponse(
    val id: UUID,
    val providerType: String,
    val apiKeyMasked: String,
    val isValidated: Boolean,
    val validatedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Response body for POST /v1/settings/llm/validate — validation result.
 *
 * Requirements: 15.8
 */
data class LlmValidationResponse(
    val isValid: Boolean,
    val message: String?
)
