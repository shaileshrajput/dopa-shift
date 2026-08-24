package com.dopashift.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.dopashift.domain.repository.LlmConfigRepository
import com.dopashift.domain.service.LlmProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LlmConfigRepository] using EncryptedSharedPreferences.
 *
 * The API key is encrypted at rest using Android Keystore-backed encryption.
 * Provider type and masked key are exposed as observable flows so the Settings
 * UI reflects current state immediately on load.
 */
@Singleton
class LlmConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmConfigRepository {

    private companion object {
        const val PREFS_FILE = "llm_config_encrypted"
        const val KEY_PROVIDER_TYPE = "llm_provider_type"
        const val KEY_API_KEY = "llm_api_key"
        const val KEY_MASKED_KEY = "llm_masked_key"
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Internal state flows that emit current values and update on writes.
    private val _providerType = MutableStateFlow(readProviderType())
    private val _maskedKey = MutableStateFlow(readMaskedKey())
    private val _isConfigured = MutableStateFlow(readIsConfigured())

    override fun observeProviderType(): Flow<LlmProviderType?> = _providerType

    override fun observeMaskedApiKey(): Flow<String> = _maskedKey

    override fun observeIsConfigured(): Flow<Boolean> = _isConfigured

    override suspend fun saveConfig(provider: LlmProviderType, apiKey: String) {
        val masked = "****${apiKey.takeLast(4)}"
        encryptedPrefs.edit()
            .putString(KEY_PROVIDER_TYPE, provider.name)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MASKED_KEY, masked)
            .apply()

        _providerType.value = provider
        _maskedKey.value = masked
        _isConfigured.value = true
    }

    override suspend fun removeConfig() {
        encryptedPrefs.edit()
            .remove(KEY_PROVIDER_TYPE)
            .remove(KEY_API_KEY)
            .remove(KEY_MASKED_KEY)
            .apply()

        _providerType.value = null
        _maskedKey.value = ""
        _isConfigured.value = false
    }

    private fun readProviderType(): LlmProviderType? {
        val name = encryptedPrefs.getString(KEY_PROVIDER_TYPE, null) ?: return null
        return try {
            LlmProviderType.valueOf(name)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun readMaskedKey(): String {
        return encryptedPrefs.getString(KEY_MASKED_KEY, "") ?: ""
    }

    private fun readIsConfigured(): Boolean {
        return encryptedPrefs.getString(KEY_API_KEY, null) != null
    }
}
