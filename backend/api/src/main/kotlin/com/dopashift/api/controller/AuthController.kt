package com.dopashift.api.controller

import com.dopashift.api.config.KeycloakAuthException
import com.dopashift.api.config.KeycloakAuthService
import com.dopashift.api.dto.LogoutRequest
import com.dopashift.api.dto.RefreshTokenRequest
import com.dopashift.api.dto.TokenRequest
import com.dopashift.api.dto.TokenResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for authentication operations.
 * These endpoints proxy requests to Keycloak and are EXEMPT from JWT authentication
 * (they are the mechanism by which tokens are obtained).
 *
 * Endpoints:
 * - POST /v1/auth/token  — Exchange authorization code for access + refresh tokens
 * - POST /v1/auth/refresh — Refresh an expired access token using a refresh token
 * - POST /v1/auth/logout  — Revoke a refresh token (server-side logout)
 *
 * Requirements: 10.3, 22.1
 */
@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val keycloakAuthService: KeycloakAuthService
) {

    /**
     * POST /v1/auth/token
     *
     * Exchanges an authorization code (from Keycloak's login redirect) for
     * access and refresh tokens. This is step 2 of the OAuth2 authorization code flow.
     *
     * @param request Contains the auth_code and redirect_uri
     * @return TokenResponse with access_token, refresh_token, and expiry details
     */
    @PostMapping("/token")
    fun exchangeToken(@Valid @RequestBody request: TokenRequest): ResponseEntity<TokenResponse> {
        return try {
            val tokenResponse = keycloakAuthService.exchangeAuthorizationCode(
                authCode = request.authCode,
                redirectUri = request.redirectUri
            )
            ResponseEntity.ok(tokenResponse)
        } catch (ex: KeycloakAuthException) {
            ResponseEntity.status(mapKeycloakStatus(ex.statusCode)).build()
        }
    }

    /**
     * POST /v1/auth/refresh
     *
     * Refreshes an access token using a valid refresh token.
     * Used by clients to silently re-authenticate without user interaction.
     *
     * @param request Contains the refresh_token
     * @return TokenResponse with new access_token, refresh_token, and expiry details
     */
    @PostMapping("/refresh")
    fun refreshToken(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<TokenResponse> {
        return try {
            val tokenResponse = keycloakAuthService.refreshAccessToken(
                refreshToken = request.refreshToken
            )
            ResponseEntity.ok(tokenResponse)
        } catch (ex: KeycloakAuthException) {
            ResponseEntity.status(mapKeycloakStatus(ex.statusCode)).build()
        }
    }

    /**
     * POST /v1/auth/logout
     *
     * Revokes a refresh token at Keycloak, effectively logging the user out server-side.
     * The client should also discard its locally stored tokens.
     *
     * @param request Contains the refresh_token to revoke
     * @return 204 No Content on success
     */
    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<Void> {
        return try {
            keycloakAuthService.logout(refreshToken = request.refreshToken)
            ResponseEntity.noContent().build()
        } catch (ex: KeycloakAuthException) {
            ResponseEntity.status(mapKeycloakStatus(ex.statusCode)).build()
        }
    }

    /**
     * Maps Keycloak HTTP error codes to appropriate client-facing status codes.
     * - 400/401 from Keycloak → 401 Unauthorized (invalid credentials/tokens)
     * - 5xx from Keycloak → 502 Bad Gateway (upstream service issue)
     * - Other → pass through
     */
    private fun mapKeycloakStatus(keycloakStatus: Int): HttpStatus {
        return when {
            keycloakStatus == 400 -> HttpStatus.UNAUTHORIZED
            keycloakStatus == 401 -> HttpStatus.UNAUTHORIZED
            keycloakStatus in 500..599 -> HttpStatus.BAD_GATEWAY
            else -> HttpStatus.INTERNAL_SERVER_ERROR
        }
    }
}
