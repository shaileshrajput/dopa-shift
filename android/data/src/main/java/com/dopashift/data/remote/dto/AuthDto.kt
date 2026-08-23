package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TokenRequest(
    val grantType: String,
    val username: String? = null,
    val password: String? = null,
    val code: String? = null,
    val redirectUri: String? = null
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String
)

@JsonClass(generateAdapter = true)
data class LogoutRequest(
    val refreshToken: String
)
