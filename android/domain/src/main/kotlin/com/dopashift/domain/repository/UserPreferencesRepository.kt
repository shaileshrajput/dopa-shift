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

    // === Theme Mode (DUX-4.5) ===

    /**
     * Observes the user's selected theme mode as a token name: "DARK", "LIGHT", or "SYSTEM".
     * Defaults to "DARK" (dark is the default per DUX-4.5), so recolor happens without a restart
     * when the value changes.
     */
    fun observeThemeMode(): Flow<String>

    /**
     * Persists the user's theme mode choice. [mode] is one of "DARK", "LIGHT", "SYSTEM".
     */
    suspend fun setThemeMode(mode: String)

    // === Accent Color ===

    /**
     * Observes the user's selected accent color hex string (e.g. "#0066FF"), or null for default.
     */
    fun observeAccentColor(): Flow<String?>

    /**
     * Persists the user's accent color choice.
     */
    suspend fun setAccentColor(hexColor: String?)

    // === Accent Seed (DUX-4.6) ===

    /**
     * Observes the user's accent seed color as an ARGB color long, or null for the design-system
     * default. This is the Material 3 [androidx.compose.material3.ColorScheme] seed the theme uses
     * (DUX-4.6); changing it recolors the visible screen without a restart.
     */
    fun observeAccentSeed(): Flow<Long?>

    /**
     * Persists the user's accent seed (ARGB color long), or null to fall back to the default seed.
     */
    suspend fun setAccentSeed(seed: Long?)

    // === Locale ===

    /**
     * Observes the user's selected locale code (e.g. "en", "hi", "mr").
     */
    fun observeLocale(): Flow<String>

    /**
     * Persists the user's locale selection.
     */
    suspend fun setLocale(localeCode: String)

    // === Full Name ===

    /**
     * Observes the user's full name, captured during onboarding (Requirement 17.4).
     * Empty string when not yet provided.
     */
    fun observeFullName(): Flow<String>

    /**
     * Persists the user's full name (typically captured in the onboarding flow and shown on the
     * Settings profile).
     */
    suspend fun setFullName(name: String)

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
