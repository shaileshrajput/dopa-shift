package com.dopashift.domain.creation

import kotlinx.coroutines.flow.Flow

/**
 * Single source for the Dashboard's Zero_State / Partial_State / populated resolution
 * (DQC-5.3).
 *
 * Backed locally so the Dashboard render path issues no network call to decide state,
 * and refreshable from the additive `GET /v1/dashboard/summary` endpoint. Every read
 * and refresh is scoped by the authenticated [UserId]; no endpoint accepts another
 * user's data based on a client-supplied id alone.
 *
 * The observed [DashboardSummary] is defined in this package by task 3.2.
 */
interface DashboardSummaryRepository {

    /**
     * Observes the [DashboardSummary] for [userId] as a reactive [Flow], so each
     * successful creation refreshes the state and the Dashboard reflects the new
     * entity within one second (DQC-1.5, DQC-2.9).
     *
     * @param userId the authenticated owner whose summary is observed.
     */
    fun observe(userId: UserId): Flow<DashboardSummary>

    /**
     * Reconciles the locally observed summary with the Backend summary endpoint.
     *
     * @param userId the authenticated owner whose summary is refreshed.
     */
    suspend fun refresh(userId: UserId)
}
