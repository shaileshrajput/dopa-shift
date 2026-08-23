package com.dopashift.domain.repository

import com.dopashift.domain.entity.UserProfile
import java.util.UUID

/**
 * Port interface for user profile persistence.
 * Implementations live in the infrastructure layer.
 */
interface UserProfileRepository {
    suspend fun findById(id: UUID): UserProfile?
    suspend fun findByKeycloakId(keycloakId: String): UserProfile?
    suspend fun save(profile: UserProfile): UserProfile
}
