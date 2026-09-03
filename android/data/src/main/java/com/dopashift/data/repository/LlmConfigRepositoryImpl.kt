package com.dopashift.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.dopashift.domain.repository.LlmConfigRepository
import com.dopashift.domain.service.LlmProviderType
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [LlmConfigRepository] using EncryptedSharedPreferences.
 *
 * The API key is encrypted at rest using Android Keystore-backed encryption.
 * Provider type and masked key are exposed as observable flows so the Settings
 * UI reflects current state immediately on load.
 *
 * Resilience: [EncryptedSharedPreferences.create] (and any subsequent read) can throw when the
 * on-disk encrypted file can no longer be decrypted by the current Keystore master key — e.g.
 * after an app reinstall, a device Keystore reset, or a restore-to-new-device. Tink surfaces this
 * as an `AEADBadTagException` / `KeyStoreException` ("Signature/MAC verification failed"). Because
 * this class is an eagerly-read `@Singleton`, letting that exception escape crashes every screen
 * that injects it (observed as a crash when opening Settings). We therefore (a) never read
 * encrypted prefs from the constructor, and (b) on an undecryptable file, wipe the corrupt prefs +
 * keyset and recreate them, degrading to an empty (unconfigured) state instead of crashing.
 */
@Singleton
class LlmConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : LlmConfigRepository {

    private companion object {
        const val TAG = "LlmConfigRepository"
        const val PREFS_FILE = "llm_config_encrypted"
        const val KEY_PROVIDER_TYPE = "llm_provider_type"
        const val KEY_API_KEY = "llm_api_key"
        const val KEY_MASKED_KEY = "llm_masked_key"
    }

    private val encryptedPrefs: SharedPreferences by lazy { openEncryptedPrefs() }

    /**
     * Opens the encrypted prefs, recovering once from an undecryptable file by wiping the corrupt
     * store and recreating it. If recovery also fails, rethrows so the failure is not silently
     * swallowed at the storage layer (reads/writes above still guard against it).
     */
    private fun openEncryptedPrefs(): SharedPreferences {
        return try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unreadable — wiping corrupt LLM config store and recreating", e)
            clearCorruptStore()
            createEncryptedPrefs()
        }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Deletes the encrypted prefs file so a stale file whose Keystore key no longer matches is
     * not carried forward. The stored API key is unrecoverable in this state anyway, so the user
     * simply re-enters it. Uses the platform delete API (not raw SQL / string ops).
     */
    private fun clearCorruptStore() {
        try {
            context.deleteSharedPreferences(PREFS_FILE)
        } catch (e: Exception) {
            // Fallback for older behavior: remove the backing xml directly.
            Log.w(TAG, "deleteSharedPreferences failed — attempting to delete backing file", e)
            runCatching {
                File(context.filesDir.parentFile, "shared_prefs/$PREFS_FILE.xml").delete()
            }
        }
    }

    // Internal state flows. Initialized lazily-safe (not from the constructor) so a decryption
    // failure recovers to an empty state rather than crashing injectors of this singleton.
    private val _providerType = MutableStateFlow(safeReadProviderType())
    private val _maskedKey = MutableStateFlow(safeReadMaskedKey())
    private val _isConfigured = MutableStateFlow(safeReadIsConfigured())

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

    private fun safeReadProviderType(): LlmProviderType? = runCatching { readProviderType() }.getOrNull()

    private fun safeReadMaskedKey(): String = runCatching { readMaskedKey() }.getOrDefault("")

    private fun safeReadIsConfigured(): Boolean = runCatching { readIsConfigured() }.getOrDefault(false)

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
