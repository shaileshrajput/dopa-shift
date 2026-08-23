package com.dopashift.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
 */
@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

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
}
