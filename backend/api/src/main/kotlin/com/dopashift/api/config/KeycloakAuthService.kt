package com.dopashift.api.config

import com.dopashift.api.dto.TokenResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

/**
 * Service that proxies authentication requests to Keycloak's OpenID Connect token endpoint.
 *
 * Supports:
 * - Authorization code exchange (login flow)
 * - Refresh token grant (silent re-authentication)
 * - Token revocation (logout)
 *
 * Requirements: 10.3, 22.1
 */
@Service
class KeycloakAuthService(
    private val webClient: WebClient,

    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val issuerUri: String,

    @Value("\${dopashift.security.keycloak-client-id:dopashift-api}")
    private val clientId: String,

    @Value("\${dopashift.security.keycloak-client-secret:}")
    private val clientSecret: String
) {

    private val tokenUrl: String
        get() = "$issuerUri/protocol/openid-connect/token"

    private val logoutUrl: String
        get() = "$issuerUri/protocol/openid-connect/logout"

    /**
     * Exchanges an authorization code for access and refresh tokens.
     *
     * @param authCode The authorization code from Keycloak's login redirect
     * @param redirectUri The redirect URI that was used in the authorization request
     * @return TokenResponse containing access_token, refresh_token, and expiry info
     * @throws KeycloakAuthException if Keycloak rejects the request
     */
    fun exchangeAuthorizationCode(authCode: String, redirectUri: String): TokenResponse {
        val formData = BodyInserters.fromFormData("grant_type", "authorization_code")
            .with("code", authCode)
            .with("client_id", clientId)
            .with("redirect_uri", redirectUri)
            .let { if (clientSecret.isNotBlank()) it.with("client_secret", clientSecret) else it }

        return executeTokenRequest(formData)
    }

    /**
     * Refreshes an access token using a valid refresh token.
     *
     * @param refreshToken The refresh token to exchange for new tokens
     * @return TokenResponse containing new access_token, refresh_token, and expiry info
     * @throws KeycloakAuthException if the refresh token is invalid or expired
     */
    fun refreshAccessToken(refreshToken: String): TokenResponse {
        val formData = BodyInserters.fromFormData("grant_type", "refresh_token")
            .with("refresh_token", refreshToken)
            .with("client_id", clientId)
            .let { if (clientSecret.isNotBlank()) it.with("client_secret", clientSecret) else it }

        return executeTokenRequest(formData)
    }

    /**
     * Revokes a refresh token at Keycloak (logout).
     *
     * @param refreshToken The refresh token to revoke
     * @throws KeycloakAuthException if Keycloak rejects the revocation request
     */
    fun logout(refreshToken: String) {
        val formData = BodyInserters.fromFormData("client_id", clientId)
            .with("refresh_token", refreshToken)
            .let { if (clientSecret.isNotBlank()) it.with("client_secret", clientSecret) else it }

        try {
            webClient.post()
                .uri(logoutUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .toBodilessEntity()
                .block()
        } catch (ex: WebClientResponseException) {
            throw KeycloakAuthException(
                message = "Keycloak logout failed: ${ex.statusCode}",
                statusCode = ex.statusCode.value(),
                keycloakError = ex.responseBodyAsString
            )
        }
    }

    private fun executeTokenRequest(formData: BodyInserters.FormInserter<String>): TokenResponse {
        try {
            val response = webClient.post()
                .uri(tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .bodyToMono(KeycloakTokenResponse::class.java)
                .block()
                ?: throw KeycloakAuthException(
                    message = "Empty response from Keycloak token endpoint",
                    statusCode = 502,
                    keycloakError = null
                )

            return TokenResponse(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresIn = response.expiresIn,
                refreshExpiresIn = response.refreshExpiresIn,
                tokenType = response.tokenType
            )
        } catch (ex: WebClientResponseException) {
            throw KeycloakAuthException(
                message = "Keycloak token request failed: ${ex.statusCode}",
                statusCode = ex.statusCode.value(),
                keycloakError = ex.responseBodyAsString
            )
        }
    }
}

/**
 * Internal DTO for deserializing Keycloak's token endpoint response.
 */
private data class KeycloakTokenResponse(
    @com.fasterxml.jackson.annotation.JsonProperty("access_token")
    val accessToken: String = "",

    @com.fasterxml.jackson.annotation.JsonProperty("refresh_token")
    val refreshToken: String = "",

    @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
    val expiresIn: Long = 0,

    @com.fasterxml.jackson.annotation.JsonProperty("refresh_expires_in")
    val refreshExpiresIn: Long = 0,

    @com.fasterxml.jackson.annotation.JsonProperty("token_type")
    val tokenType: String = "Bearer"
)

/**
 * Exception thrown when a Keycloak authentication operation fails.
 */
class KeycloakAuthException(
    message: String,
    val statusCode: Int,
    val keycloakError: String?
) : RuntimeException(message)
