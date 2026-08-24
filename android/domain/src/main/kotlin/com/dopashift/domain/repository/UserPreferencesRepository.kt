package com.dopashift.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository port interface for user preferences persistence (onboarding state, theme, locale, accent color, display name).
 */
interface UserPreferencesRepository {
    /**
     * Observes whether the user has completed (or skipped) onboarding.
     */
    fun observeOnboardingCompleted(): Flow<Boolean>

    /**
     * Marks onboarding as completed (or skipped).
     */
    suspend fun setOnboardingCompleted(completed: Boolean)

    // === Accent Color ===

    /**
     * Observes the user's selected accent color hex string (e.g. "#0066FF"), or null for default.
     */
    fun observeAccentColor(): Flow<String?>

    /**
     * Persists the user's accent color choice.
     */
    suspend fun setAccentColor(hexColor: String?)

    // === Locale ===

    /**
     * Observes the user's selected locale code (e.g. "en", "hi", "mr").
     */
    fun observeLocale(): Flow<String>

    /**
     * Persists the user's locale selection.
     */
    suspend fun setLocale(localeCode: String)

    // === Display Name ===

    /**
     * Observes the user's display name.
     */
    fun observeDisplayName(): Flow<String>

    /**
     * Persists the user's display name.
     */
    suspend fun setDisplayName(name: String)

    // === Email ===

    /**
     * Observes the user's email address (cached from auth/profile).
     */
    fun observeEmail(): Flow<String>

    /**
     * Persists the user's email (typically set from Keycloak token claims on login).
     */
    suspend fun setEmail(email: String)
}
