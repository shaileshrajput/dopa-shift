package com.dopashift.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that adds OAuth2 Bearer token to all API requests
 * and handles 401 responses by refreshing the token and retrying once.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth header for auth endpoints themselves
        if (originalRequest.isAuthEndpoint()) {
            return chain.proceed(originalRequest)
        }

        val accessToken = runBlocking { tokenManager.getAccessToken() }
            ?: return chain.proceed(originalRequest)

        val authenticatedRequest = originalRequest.withBearerToken(accessToken)
        val response = chain.proceed(authenticatedRequest)

        // If 401, try refreshing the token and retry once
        if (response.code == 401) {
            response.close()

            val newToken = runBlocking { tokenManager.refreshAccessToken() }

            if (newToken == null) {
                // Refresh failed — re-execute the original request to return the 401
                return chain.proceed(authenticatedRequest)
            }

            val retriedRequest = originalRequest.withBearerToken(newToken)
            return chain.proceed(retriedRequest)
        }

        return response
    }

    private fun Request.isAuthEndpoint(): Boolean {
        val path = url.encodedPath
        return path.contains("v1/auth/token") ||
                path.contains("v1/auth/refresh")
    }

    private fun Request.withBearerToken(token: String): Request {
        return newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }
}
