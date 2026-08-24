package com.dopashift.api.controller

import com.dopashift.api.config.AuthenticatedUser
import com.dopashift.api.config.KeycloakAuthException
import com.dopashift.api.dto.ChangePasswordRequest
import com.dopashift.api.dto.PasswordChangeResponse
import com.dopashift.api.dto.PhotoUploadResponse
import com.dopashift.api.dto.UpdateProfileRequest
import com.dopashift.api.dto.UserProfileResponse
import com.dopashift.domain.entity.UserProfile
import com.dopashift.domain.port.ProfileStorageService
import com.dopashift.domain.repository.UserProfileRepository
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant
import java.util.UUID

/**
 * REST controller for user profile management.
 *
 * Endpoints:
 * - GET /v1/profile — retrieve authenticated user's profile
 * - PUT /v1/profile — update profile settings (locale, accent color, timezone, quiet hours)
 * - POST /v1/profile/photo — upload profile photo (JPEG/PNG, max 5MB)
 * - DELETE /v1/profile/photo — remove profile photo
 * - POST /v1/profile/password — change password via Keycloak Account Management API
 *
 * Requirements: 19.1–19.17
 */
@RestController
@RequestMapping("/v1/profile")
class UserProfileController(
    private val userProfileRepository: UserProfileRepository,
    private val profileStorageService: ProfileStorageService,
    private val authenticatedUser: AuthenticatedUser,
    private val webClient: WebClient,

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val keycloakIssuerUri: String,

    @Value("\${dopashift.security.keycloak-client-id:dopashift-api}")
    private val keycloakClientId: String,

    @Value("\${dopashift.security.keycloak-client-secret:}")
    private val keycloakClientSecret: String
) {

    private val logger = LoggerFactory.getLogger(UserProfileController::class.java)

    companion object {
        private const val MAX_PHOTO_SIZE_BYTES = 5 * 1024 * 1024L // 5 MB
        private val ALLOWED_CONTENT_TYPES = setOf(
            MediaType.IMAGE_JPEG_VALUE,
            MediaType.IMAGE_PNG_VALUE
        )
    }

    /**
     * GET /v1/profile — retrieve the authenticated user's profile.
     *
     * Returns 200 OK with the user's profile data.
     * Returns 404 if no profile exists yet for the authenticated user.
     */
    @GetMapping
    suspend fun getProfile(): ResponseEntity<UserProfileResponse> {
        val userId = authenticatedUser.getUserId()
        val profile = userProfileRepository.findById(userId)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(profile.toResponse())
    }

    /**
     * PUT /v1/profile — update profile settings.
     *
     * Updates locale, accent color, timezone, quiet hours, and display name.
     * Creates the profile if it doesn't exist yet (upsert behavior).
     *
     * Requirements: 19.12, 19.13, 19.14, 19.17
     */
    @PutMapping
    suspend fun updateProfile(
        @Valid @RequestBody request: UpdateProfileRequest
    ): ResponseEntity<UserProfileResponse> {
        val userId = authenticatedUser.getUserId()
        val now = Instant.now()

        val existing = userProfileRepository.findById(userId)

        val updatedProfile = if (existing != null) {
            existing.copy(
                displayName = request.displayName,
                preferredLocale = request.preferredLocale,
                accentColor = request.accentColor,
                timezone = request.timezone,
                quietHoursStart = request.quietHoursStart,
                quietHoursEnd = request.quietHoursEnd,
                updatedAt = now
            )
        } else {
            // Create new profile (upsert)
            UserProfile(
                id = userId,
                keycloakId = authenticatedUser.getJwt().subject,
                displayName = request.displayName,
                email = authenticatedUser.getEmail() ?: "",
                preferredLocale = request.preferredLocale,
                accentColor = request.accentColor,
                timezone = request.timezone,
                quietHoursStart = request.quietHoursStart,
                quietHoursEnd = request.quietHoursEnd,
                createdAt = now,
                updatedAt = now
            )
        }

        val saved = userProfileRepository.save(updatedProfile)
        return ResponseEntity.ok(saved.toResponse())
    }

    /**
     * POST /v1/profile/photo — upload a profile photo.
     *
     * Accepts JPEG/PNG files up to 5MB.
     * Replaces existing photo if present (deletes old object from storage).
     *
     * Requirements: 19.1, 19.2, 19.3
     */
    @PostMapping("/photo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun uploadPhoto(
        @RequestParam("file") file: MultipartFile
    ): ResponseEntity<PhotoUploadResponse> {
        val userId = authenticatedUser.getUserId()

        // Validate content type
        val contentType = file.contentType
        if (contentType == null || contentType !in ALLOWED_CONTENT_TYPES) {
            throw IllegalArgumentException(
                "Invalid file type. Only JPEG and PNG are accepted."
            )
        }

        // Validate file size
        if (file.size > MAX_PHOTO_SIZE_BYTES) {
            throw IllegalArgumentException(
                "File size exceeds maximum allowed size of 5 MB."
            )
        }

        // Delete existing photo if present (Requirement 19.3)
        val existingProfile = userProfileRepository.findById(userId)
        val existingPhotoUrl = existingProfile?.profilePhotoUrl
        if (existingPhotoUrl != null) {
            profileStorageService.deletePhoto(userId, existingPhotoUrl)
        }

        // Upload new photo
        val photoUrl = profileStorageService.uploadPhoto(userId, file.bytes, contentType)

        // Update profile with new photo URL
        val now = Instant.now()
        val updatedProfile = if (existingProfile != null) {
            existingProfile.copy(profilePhotoUrl = photoUrl, updatedAt = now)
        } else {
            UserProfile(
                id = userId,
                keycloakId = authenticatedUser.getJwt().subject,
                displayName = authenticatedUser.getPreferredUsername() ?: "User",
                email = authenticatedUser.getEmail() ?: "",
                profilePhotoUrl = photoUrl,
                createdAt = now,
                updatedAt = now
            )
        }
        userProfileRepository.save(updatedProfile)

        return ResponseEntity.status(HttpStatus.CREATED).body(PhotoUploadResponse(profilePhotoUrl = photoUrl))
    }

    /**
     * DELETE /v1/profile/photo — remove profile photo.
     *
     * Deletes the photo from S3 storage and reverts the profile to no photo.
     *
     * Requirements: 19.4
     */
    @DeleteMapping("/photo")
    suspend fun deletePhoto(): ResponseEntity<Void> {
        val userId = authenticatedUser.getUserId()
        val profile = userProfileRepository.findById(userId)
            ?: return ResponseEntity.notFound().build()

        val photoUrl = profile.profilePhotoUrl
            ?: return ResponseEntity.noContent().build() // No photo to delete

        // Delete from storage
        profileStorageService.deletePhoto(userId, photoUrl)

        // Update profile
        val updated = profile.copy(profilePhotoUrl = null, updatedAt = Instant.now())
        userProfileRepository.save(updated)

        return ResponseEntity.noContent().build()
    }

    /**
     * POST /v1/profile/password — change password via Keycloak Account Management API.
     *
     * Requires current password verification.
     * Routes through Keycloak — does NOT implement custom password storage.
     *
     * Requirements: 19.7, 19.8, 19.9, 19.10, 19.11
     */
    @PostMapping("/password")
    suspend fun changePassword(
        @Valid @RequestBody request: ChangePasswordRequest
    ): ResponseEntity<PasswordChangeResponse> {
        val userId = authenticatedUser.getUserId()
        val keycloakUserId = authenticatedUser.getJwt().subject

        // Use Keycloak Admin REST API to update the user's password
        // The Admin API endpoint: PUT /admin/realms/{realm}/users/{userId}
        // We first verify the current password by attempting a token grant,
        // then reset the password via the Admin API.

        // Verify current password via Keycloak token endpoint
        verifyCurrentPassword(request.currentPassword)

        // Set new password via Keycloak Admin API
        setNewPassword(keycloakUserId, request.newPassword)

        logger.info("Password changed successfully for user {}", userId)

        return ResponseEntity.ok(PasswordChangeResponse())
    }

    /**
     * Verifies the current password by attempting a direct grant (resource owner password)
     * against Keycloak's token endpoint.
     *
     * The dopashift-api client is confidential (publicClient=false), so the client_secret
     * must be included in the token request for Keycloak to accept it.
     */
    private fun verifyCurrentPassword(currentPassword: String) {
        val username = authenticatedUser.getPreferredUsername()
            ?: throw IllegalStateException("Cannot determine username for password verification")

        val tokenUrl = "$keycloakIssuerUri/protocol/openid-connect/token"

        try {
            val formData = BodyInserters.fromFormData("grant_type", "password")
                .with("username", username)
                .with("password", currentPassword)
                .with("client_id", keycloakClientId)
                .with("client_secret", keycloakClientSecret)

            webClient.post()
                .uri(tokenUrl)
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .toBodilessEntity()
                .block()
        } catch (ex: WebClientResponseException) {
            if (ex.statusCode.value() == 401 || ex.statusCode.value() == 400) {
                // Requirement 19.11: Do not reveal whether failure is incorrect password
                // vs locked account
                throw PasswordVerificationException(
                    "Password change request failed. Please verify your credentials."
                )
            }
            throw KeycloakAuthException(
                message = "Password verification failed: ${ex.statusCode}",
                statusCode = ex.statusCode.value(),
                keycloakError = ex.responseBodyAsString
            )
        }
    }

    /**
     * Sets a new password via the Keycloak Admin REST API.
     * Uses the realm admin endpoint to reset the user's credentials.
     */
    private fun setNewPassword(keycloakUserId: String, newPassword: String) {
        // Extract realm from issuer URI (e.g., .../realms/dopashift -> dopashift)
        val realm = keycloakIssuerUri.substringAfterLast("/realms/").trimEnd('/')
        val baseUrl = keycloakIssuerUri.substringBefore("/realms/")
        val adminUrl = "$baseUrl/admin/realms/$realm/users/$keycloakUserId/reset-password"

        val credentialPayload = mapOf(
            "type" to "password",
            "value" to newPassword,
            "temporary" to false
        )

        try {
            webClient.put()
                .uri(adminUrl)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .header(
                    "Authorization",
                    "Bearer ${authenticatedUser.getJwt().tokenValue}"
                )
                .bodyValue(credentialPayload)
                .retrieve()
                .toBodilessEntity()
                .block()
        } catch (ex: WebClientResponseException) {
            if (ex.statusCode.value() == 400) {
                // Requirement 19.9: Surface password policy violations in human-readable form
                throw PasswordPolicyViolationException(
                    "New password does not meet the security requirements. " +
                        "Please ensure it meets the minimum length and complexity rules."
                )
            }
            throw KeycloakAuthException(
                message = "Password reset failed: ${ex.statusCode}",
                statusCode = ex.statusCode.value(),
                keycloakError = ex.responseBodyAsString
            )
        }
    }

    private fun UserProfile.toResponse(): UserProfileResponse = UserProfileResponse(
        id = id,
        displayName = displayName,
        email = email,
        preferredLocale = preferredLocale,
        accentColor = accentColor,
        profilePhotoUrl = profilePhotoUrl,
        timezone = timezone,
        quietHoursStart = quietHoursStart,
        quietHoursEnd = quietHoursEnd,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

/**
 * Thrown when password verification fails.
 * Requirement 19.11: generic message, not revealing specific failure reason.
 */
class PasswordVerificationException(message: String) : RuntimeException(message)

/**
 * Thrown when the new password violates Keycloak's password policy.
 * Requirement 19.9: surface policy violations in human-readable form.
 */
class PasswordPolicyViolationException(message: String) : RuntimeException(message)
