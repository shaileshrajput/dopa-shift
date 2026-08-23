package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val displayName: String? = null,
    val preferredLocale: String? = null,
    val accentColor: String? = null,
    val timezone: String? = null,
    val quietHoursStart: String? = null,
    val quietHoursEnd: String? = null
)

@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val id: String,
    val displayName: String,
    val email: String,
    val preferredLocale: String,
    val accentColor: String?,
    val profilePhotoUrl: String?,
    val timezone: String,
    val quietHoursStart: String?,
    val quietHoursEnd: String?,
    val createdAt: String,
    val updatedAt: String
)

@JsonClass(generateAdapter = true)
data class ProfilePhotoResponse(
    val photoUrl: String
)

@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)
