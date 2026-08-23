package com.dopashift.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Response body for GET /v1/profile.
 */
data class UserProfileResponse(
    val id: UUID,
    val displayName: String,
    val email: String,
    val preferredLocale: String,
    val accentColor: String?,
    val profilePhotoUrl: String?,
    val timezone: String,
    val quietHoursStart: LocalTime?,
    val quietHoursEnd: LocalTime?,
    val createdAt: Instant,
    val updatedAt: Instant
)

/**
 * Request body for PUT /v1/profile — update profile settings.
 *
 * Requirements: 19.12, 19.13, 19.14
 */
data class UpdateProfileRequest(
    @field:NotBlank(message = "Display name is required")
    @field:Size(min = 1, max = 100, message = "Display name must be between 1 and 100 characters")
    val displayName: String,

    @field:NotBlank(message = "Preferred locale is required")
    @field:Pattern(regexp = "^(en|hi|mr)$", message = "Preferred locale must be one of: en, hi, mr")
    val preferredLocale: String,

    @field:Pattern(
        regexp = "^#[0-9A-Fa-f]{6}$",
        message = "Accent color must be a valid hex color code (#RRGGBB)"
    )
    val accentColor: String? = null,

    @field:NotBlank(message = "Timezone is required")
    val timezone: String,

    val quietHoursStart: LocalTime? = null,
    val quietHoursEnd: LocalTime? = null
)

/**
 * Request body for POST /v1/profile/password — change password via Keycloak.
 *
 * Requirements: 19.7, 19.8, 19.9
 */
data class ChangePasswordRequest(
    @field:NotBlank(message = "Current password is required")
    val currentPassword: String,

    @field:NotBlank(message = "New password is required")
    @field:Size(min = 8, message = "New password must be at least 8 characters")
    val newPassword: String
)

/**
 * Response for successful password change.
 */
data class PasswordChangeResponse(
    val message: String = "Password changed successfully"
)

/**
 * Response for successful photo upload.
 */
data class PhotoUploadResponse(
    val profilePhotoUrl: String
)
