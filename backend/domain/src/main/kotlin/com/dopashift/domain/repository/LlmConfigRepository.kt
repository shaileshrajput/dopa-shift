package com.dopashift.domain.repository

import com.dopashift.domain.entity.LlmConfiguration
import java.util.UUID

/**
 * Port interface for LLM configuration persistence.
 * Stores encrypted API keys per user, supporting one active config per user.
 *
 * Implementations live in the infrastructure layer and handle actual
 * JPA/JDBC persistence of the encrypted key bytes.
 *
 * Requirements: 15.1, 15.2, 22.3, 22.12
 */
interface LlmConfigRepository {
    suspend fun findByUserId(userId: UUID): LlmConfiguration?
    suspend fun save(config: LlmConfiguration): LlmConfiguration
    suspend fun deleteByUserId(userId: UUID)
}
