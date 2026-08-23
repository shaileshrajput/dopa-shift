package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.dto.ConfigureLlmRequest
import com.dopashift.api.dto.LlmConfigResponse
import com.dopashift.api.dto.LlmValidationResponse
import com.dopashift.domain.entity.LlmConfiguration
import com.dopashift.domain.exception.LlmConfigNotFoundException
import com.dopashift.domain.model.LlmProviderType
import com.dopashift.domain.port.EncryptionService
import com.dopashift.domain.port.LlmAdapter
import com.dopashift.domain.port.LlmAdapterFactory
import com.dopashift.domain.repository.LlmConfigRepository
import jakarta.validation.Valid
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * REST controller for LLM provider configuration management.
 *
 * Supports configuring, viewing (with masked key), rotating, removing,
 * and validating a user's BYO-LLM API key.
 *
 * Requirements: 15.1, 15.2, 15.8, 22.3, 22.12
 */
@RestController
@RequestMapping("/v1/settings/llm")
class LlmConfigController(
    private val llmConfigRepository: LlmConfigRepository,
    private val encryptionService: EncryptionService,
    private val llmAdapterFactory: LlmAdapterFactory,
    private val authenticatedUser: AuthenticatedUser
) {

    private val logger = LoggerFactory.getLogger(LlmConfigController::class.java)

    companion object {
        private const val VALIDATION_TIMEOUT_MS = 10_000L
    }

    /**
     * POST /v1/settings/llm — Configure LLM provider with encrypted key storage.
     *
     * Creates or replaces the user's LLM configuration.
     * The API key is encrypted at rest using AES-256.
     *
     * Returns 201 Created with the config (key masked).
     * Returns 400 Bad Request for invalid provider type or input.
     *
     * Requirements: 15.1, 15.2, 22.3
     */
    @PostMapping
    suspend fun configureLlm(
        @Valid @RequestBody request: ConfigureLlmRequest
    ): ResponseEntity<LlmConfigResponse> {
        val userId = authenticatedUser.getUserId()
        val providerType = parseProviderType(request.providerType)
        val apiKey = request.apiKey

        val encryptionResult = encryptionService.encrypt(apiKey)
        val apiKeyLast4 = maskApiKey(apiKey)

        val now = Instant.now()
        val config = LlmConfiguration(
            id = UUID.randomUUID(),
            userId = userId,
            providerType = providerType,
            apiKeyEncrypted = encryptionResult.ciphertext,
            apiKeyIv = encryptionResult.iv,
            apiKeyLast4 = apiKeyLast4,
            isValidated = false,
            validatedAt = null,
            createdAt = now,
            updatedAt = now
        )

        val saved = llmConfigRepository.save(config)
        logger.info("LLM configuration created for user={}, provider={}", userId, providerType)

        return ResponseEntity.status(HttpStatus.CREATED).body(saved.toResponse())
    }

    /**
     * GET /v1/settings/llm — Return configuration with masked key.
     *
     * Returns 200 OK with the config (only last 4 chars of key visible).
     * Returns 404 Not Found if no config exists for the user.
     *
     * Requirements: 15.1
     */
    @GetMapping
    suspend fun getLlmConfig(): ResponseEntity<LlmConfigResponse> {
        val userId = authenticatedUser.getUserId()
        val config = llmConfigRepository.findByUserId(userId)
            ?: throw LlmConfigNotFoundException(userId.toString())

        return ResponseEntity.ok(config.toResponse())
    }

    /**
     * PUT /v1/settings/llm — Rotate API key.
     *
     * Updates the existing config with a new key, immediately invalidating the old one.
     * Resets validation status.
     *
     * Returns 200 OK with the updated config (key masked).
     * Returns 404 Not Found if no config exists for the user.
     * Returns 400 Bad Request for invalid input.
     *
     * Requirements: 22.12
     */
    @PutMapping
    suspend fun rotateLlmKey(
        @Valid @RequestBody request: ConfigureLlmRequest
    ): ResponseEntity<LlmConfigResponse> {
        val userId = authenticatedUser.getUserId()
        val existing = llmConfigRepository.findByUserId(userId)
            ?: throw LlmConfigNotFoundException(userId.toString())

        val providerType = parseProviderType(request.providerType)
        val apiKey = request.apiKey

        val encryptionResult = encryptionService.encrypt(apiKey)
        val apiKeyLast4 = maskApiKey(apiKey)

        val updated = existing.copy(
            providerType = providerType,
            apiKeyEncrypted = encryptionResult.ciphertext,
            apiKeyIv = encryptionResult.iv,
            apiKeyLast4 = apiKeyLast4,
            isValidated = false,
            validatedAt = null,
            updatedAt = Instant.now()
        )

        val saved = llmConfigRepository.save(updated)
        logger.info("LLM API key rotated for user={}, provider={}", userId, providerType)

        return ResponseEntity.ok(saved.toResponse())
    }

    /**
     * DELETE /v1/settings/llm — Remove LLM configuration.
     *
     * Removes the user's LLM config, immediately invalidating the stored key.
     *
     * Returns 204 No Content on success.
     * Returns 404 Not Found if no config exists for the user.
     *
     * Requirements: 22.12
     */
    @DeleteMapping
    suspend fun deleteLlmConfig(): ResponseEntity<Void> {
        val userId = authenticatedUser.getUserId()
        val existing = llmConfigRepository.findByUserId(userId)
            ?: throw LlmConfigNotFoundException(userId.toString())

        llmConfigRepository.deleteByUserId(userId)
        logger.info("LLM configuration deleted for user={}", userId)

        return ResponseEntity.noContent().build()
    }

    /**
     * POST /v1/settings/llm/validate — Test the configured API key.
     *
     * Makes a lightweight health-check call to the configured LLM provider
     * with a 10-second timeout. Updates validation status on success.
     *
     * Returns 200 OK with validation result.
     * Returns 404 Not Found if no config exists for the user.
     *
     * Requirements: 15.8
     */
    @PostMapping("/validate")
    suspend fun validateLlmConfig(): ResponseEntity<LlmValidationResponse> {
        val userId = authenticatedUser.getUserId()
        val config = llmConfigRepository.findByUserId(userId)
            ?: throw LlmConfigNotFoundException(userId.toString())

        val apiKey = encryptionService.decrypt(config.apiKeyEncrypted, config.apiKeyIv)

        val adapter: LlmAdapter = llmAdapterFactory.create(config.providerType, apiKey)

        return try {
            val isValid = withTimeout(VALIDATION_TIMEOUT_MS) {
                adapter.healthCheck()
            }

            if (isValid) {
                val validated = config.copy(
                    isValidated = true,
                    validatedAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                llmConfigRepository.save(validated)
                logger.info("LLM config validated successfully for user={}", userId)
                ResponseEntity.ok(LlmValidationResponse(isValid = true, message = "API key is valid"))
            } else {
                ResponseEntity.ok(
                    LlmValidationResponse(isValid = false, message = "API key validation failed: invalid key")
                )
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.warn("LLM validation timed out for user={}", userId)
            ResponseEntity.ok(
                LlmValidationResponse(isValid = false, message = "Validation timed out after 10 seconds")
            )
        } catch (e: Exception) {
            logger.warn("LLM validation failed for user={}: {}", userId, e.message)
            val message = when {
                e.message?.contains("401", ignoreCase = true) == true ||
                    e.message?.contains("unauthorized", ignoreCase = true) == true ->
                    "API key validation failed: invalid key"
                e.message?.contains("429", ignoreCase = true) == true ||
                    e.message?.contains("rate limit", ignoreCase = true) == true ->
                    "API key validation failed: rate limit exceeded"
                else -> "API key validation failed: ${e.message ?: "unknown error"}"
            }
            ResponseEntity.ok(LlmValidationResponse(isValid = false, message = message))
        }
    }

    /**
     * Parse provider type string to enum, throwing a clear error if invalid.
     */
    private fun parseProviderType(providerType: String): LlmProviderType {
        return try {
            LlmProviderType.valueOf(providerType.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Invalid provider type: '$providerType'. Must be one of: OPENAI, GEMINI, CLAUDE"
            )
        }
    }

    /**
     * Mask API key, showing only the last 4 characters.
     * For keys shorter than 4 characters, mask the entire key.
     */
    private fun maskApiKey(apiKey: String): String {
        return if (apiKey.length <= 4) {
            "*".repeat(apiKey.length)
        } else {
            apiKey.takeLast(4)
        }
    }

    private fun LlmConfiguration.toResponse(): LlmConfigResponse = LlmConfigResponse(
        id = id,
        providerType = providerType.name,
        apiKeyMasked = "****$apiKeyLast4",
        isValidated = isValidated,
        validatedAt = validatedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
