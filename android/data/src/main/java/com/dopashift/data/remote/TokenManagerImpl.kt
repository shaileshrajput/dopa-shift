package com.dopashift.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dopashift.data.remote.dto.RefreshTokenRequest
import com.dopashift.data.remote.dto.TokenResponse
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed implementation of [TokenManager].
 * Uses a Mutex to prevent concurrent refresh attempts from racing.
 */
@Singleton
class TokenManagerImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val authApiProvider: AuthApiProvider
) : TokenManager {

    private val refreshMutex = Mutex()

    override suspend fun getAccessToken(): String? {
        return dataStore.data.map { prefs ->
            prefs[ACCESS_TOKEN_KEY]
        }.firstOrNull()
    }

    override suspend fun getRefreshToken(): String? {
        return dataStore.data.map { prefs ->
            prefs[REFRESH_TOKEN_KEY]
        }.firstOrNull()
    }

    override suspend fun refreshAccessToken(): String? {
        return refreshMutex.withLock {
            val refreshToken = getRefreshToken() ?: return@withLock null

            try {
                val response: Response<TokenResponse> = authApiProvider.getAuthApi()
                    .refreshToken(RefreshTokenRequest(refreshToken = refreshToken))

                if (response.isSuccessful) {
                    val tokenResponse = response.body() ?: return@withLock null
                    saveTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
                    tokenResponse.accessToken
                } else {
                    // Refresh token expired or invalid — clear all tokens
                    if (response.code() == 401 || response.code() == 400) {
                        clearTokens()
                    }
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
    }

    override suspend fun clearTokens() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
        }
    }

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    }
}
