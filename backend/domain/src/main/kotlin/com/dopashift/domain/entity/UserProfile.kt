package com.dopashift.domain.entity

import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Domain entity representing a user's profile settings.
 *
 * Contains display preferences (locale, accent color, timezone),
 * quiet hours configuration, and profile photo reference.
 */
data class UserProfile(
    val id: UUID,
    val keycloakId: String,
    val displayName: String,
    val email: String,
    val preferredLocale: String = "en",
    val accentColor: String? = null,
    val profilePhotoUrl: String? = null,
    val timezone: String = "UTC",
    val quietHoursStart: LocalTime? = null,
    val quietHoursEnd: LocalTime? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)
