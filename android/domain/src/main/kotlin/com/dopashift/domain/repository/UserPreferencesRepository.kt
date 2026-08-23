package com.dopashift.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository port interface for user preferences persistence (onboarding state, theme, etc.).
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
}
