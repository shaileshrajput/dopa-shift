package com.dopashift.domain.repository

import com.dopashift.domain.service.LlmProviderType
import kotlinx.coroutines.flow.Flow

/**
 * Repository port for persisting the user's BYO-LLM configuration.
 *
 * The API key is stored encrypted at rest. The provider type and masked key
 * are observable so the Settings UI can reflect current state on load.
 */
interface LlmConfigRepository {

    /**
     * Observes the currently configured LLM provider type, or null if none configured.
     */
    fun observeProviderType(): Flow<LlmProviderType?>

    /**
     * Observes a masked representation of the stored API key (e.g. "****abcd"), or empty if none.
     */
    fun observeMaskedApiKey(): Flow<String>

    /**
     * Observes whether a valid LLM config is currently stored.
     */
    fun observeIsConfigured(): Flow<Boolean>

    /**
     * Saves the LLM provider configuration after successful validation.
     * The [apiKey] is encrypted before persistence.
     */
    suspend fun saveConfig(provider: LlmProviderType, apiKey: String)

    /**
     * Removes the stored LLM configuration entirely.
     * Immediately invalidates the old key from further use.
     */
    suspend fun removeConfig()
}
