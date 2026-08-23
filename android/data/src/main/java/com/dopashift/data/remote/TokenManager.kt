package com.dopashift.data.remote

/**
 * Manages OAuth2 token storage, retrieval, and refresh.
 * Provides thread-safe access to access/refresh tokens for the AuthInterceptor.
 */
interface TokenManager {

    /**
     * Returns the current access token, or null if not authenticated.
     */
    suspend fun getAccessToken(): String?

    /**
     * Returns the current refresh token, or null if not authenticated.
     */
    suspend fun getRefreshToken(): String?

    /**
     * Attempts to refresh the access token using the stored refresh token.
     * Returns the new access token on success, or null on failure (e.g., refresh token expired).
     */
    suspend fun refreshAccessToken(): String?

    /**
     * Stores new token pair received from authentication or refresh.
     */
    suspend fun saveTokens(accessToken: String, refreshToken: String)

    /**
     * Clears all stored tokens (logout).
     */
    suspend fun clearTokens()
}
