package com.dopashift.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request DTO for token exchange (authorization code flow).
 * The client submits an authorization code received from Keycloak's login page.
 */
data class TokenRequest(
    @JsonProperty("auth_code")
    val authCode: String,

    @JsonProperty("redirect_uri")
    val redirectUri: String
)

/**
 * Request DTO for token refresh.
 * The client submits a refresh token to obtain new access + refresh tokens.
 */
data class RefreshTokenRequest(
    @JsonProperty("refresh_token")
    val refreshToken: String
)

/**
 * Request DTO for logout.
 * The client submits a refresh token to be revoked at Keycloak.
 */
data class LogoutRequest(
    @JsonProperty("refresh_token")
    val refreshToken: String
)

/**
 * Response DTO for token exchange and refresh operations.
 * Contains the tokens issued by Keycloak.
 */
data class TokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,

    @JsonProperty("refresh_token")
    val refreshToken: String,

    @JsonProperty("expires_in")
    val expiresIn: Long,

    @JsonProperty("refresh_expires_in")
    val refreshExpiresIn: Long,

    @JsonProperty("token_type")
    val tokenType: String
)
