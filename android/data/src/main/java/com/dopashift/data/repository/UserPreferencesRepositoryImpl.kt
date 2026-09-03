package com.dopashift.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dopashift.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

/**
 * DataStore-backed implementation of [UserPreferencesRepository].
 *
 * Persists onboarding state, accent color, locale, and display name.
 */
@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val ACCENT_SEED = longPreferencesKey("accent_seed")
        val LOCALE = stringPreferencesKey("locale")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val EMAIL = stringPreferencesKey("email")

        // Dark is the default theme mode per DUX-4.5.
        const val DEFAULT_THEME_MODE = "DARK"
    }

    // === Onboarding ===

    override fun observeOnboardingCompleted(): Flow<Boolean> {
        return context.userPrefsDataStore.data.map { prefs ->
            prefs[ONBOARDING_COMPLETED] ?: false
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED] = completed
        }
    }

    // === Theme Mode (DUX-4.5) ===

    override fun observeThemeMode(): Flow<String> {
        return context.userPrefsDataStore.data.map { prefs ->
            prefs[THEME_MODE] ?: DEFAULT_THEME_MODE
        }
    }

    override suspend fun setThemeMode(mode: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[THEME_MODE] = mode
        }
    }

    // === Accent Color ===

    override fun observeAccentColor(): Flow<String?> {
        return context.userPrefsDataStore.data.map { prefs ->
            prefs[ACCENT_COLOR]
        }
    }

    override suspend fun setAccentColor(hexColor: String?) {
        context.userPrefsDataStore.edit { prefs ->
            if (hexColor != null) {
                prefs[ACCENT_COLOR] = hexColor
            } else {
                prefs.remove(ACCENT_COLOR)
            }
        }
    }

    // === Accent Seed (DUX-4.6) ===

    override fun observeAccentSeed(): Flow<Long?> {
        return context.userPrefsDataStore.data.map { prefs ->
            prefs[ACCENT_SEED]
        }
    }

    override suspend fun setAccentSeed(seed: Long?) {
        context.userPrefsDataStore.edit { prefs ->
            if (seed != null) {
                prefs[ACCENT_SEED] = seed
            } else {
                prefs.remove(ACCENT_SEED)
            }
        }
    }

    // === Locale ===

    override fun observeLocale(): Flow<String> {
        return context.userPrefsDataStore.data.map { prefs ->
            prefs[LOCALE] ?: "en"
        }
    }

    override suspend fun setLocale(localeCode: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[LOCALE] = localeCode
        }
    }

    // === Display Name ===

    override fun observeDisplayName(): Flow<String> {
        return context.userPrefsDataStore.data.map { prefs ->
            prefs[DISPLAY_NAME] ?: ""
        }
    }

    override suspend fun setDisplayName(name: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[DISPLAY_NAME] = name
        }
    }

    // === Email ===

    override fun observeEmail(): Flow<String> {
        return context.userPrefsDataStore.data.map { prefs ->
            prefs[EMAIL] ?: ""
        }
    }

    override suspend fun setEmail(email: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[EMAIL] = email
        }
    }
}
