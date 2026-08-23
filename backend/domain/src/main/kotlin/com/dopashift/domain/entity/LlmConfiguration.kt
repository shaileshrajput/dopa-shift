package com.dopashift.domain.entity

import com.dopashift.domain.model.LlmProviderType
import java.time.Instant
import java.util.UUID

/**
 * Domain entity representing a user's LLM provider configuration.
 * Each user can have at most one active configuration.
 *
 * The API key is stored encrypted at rest (AES-256) and must never be logged
 * in plaintext. The [apiKeyLast4] field holds the last 4 characters for
 * display/masking purposes in GET responses.
 *
 * Requirements: 15.1, 15.2, 22.3
 */
data class LlmConfiguration(
    val id: UUID,
    val userId: UUID,
    val providerType: LlmProviderType,
    val apiKeyEncrypted: ByteArray,
    val apiKeyIv: ByteArray,
    val apiKeyLast4: String,
    val isValidated: Boolean = false,
    val validatedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LlmConfiguration) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
