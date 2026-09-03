package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.DashboardSummary
import com.dopashift.domain.creation.UserId
import kotlinx.coroutines.flow.Flow

/**
 * Single source of the Dashboard's Zero_State / Partial_State / populated rendering state
 * (DQC-5.3).
 *
 * Exposes the [DashboardSummary] for the authenticated user as a reactive [Flow] sourced from
 * [com.dopashift.domain.creation.DashboardSummaryRepository]. Because the summary is observed
 * (not fetched once), each successful creation — which refreshes the repository's local counts —
 * causes a new [DashboardSummary] to be emitted, so the Dashboard reflects the new entity within
 * one second and the user is never navigated away (DQC-1.5, DQC-2.9).
 *
 * The emitted summary also carries the persisted onboarding outcome, so the UI can suppress
 * Zero_State creation prompts for entity types already created during onboarding — a user who
 * completed onboarding is not re-prompted to create what onboarding already produced (DQC-5.7).
 * That suppression is expressed on the [DashboardSummary] itself
 * (see [DashboardSummary.suppressesZeroStatePrompt]) so there is exactly one state feed and no
 * second query.
 *
 * Every read is scoped by the authenticated [UserId]; no summary is derived from another user's
 * data on the basis of a client-supplied id alone.
 */
interface ObserveDashboardSummaryUseCase {
    /**
     * Observe the Dashboard state for [userId].
     *
     * @param userId the authenticated owner whose Dashboard state is observed.
     * @return a [Flow] emitting a fresh [DashboardSummary] whenever the user's underlying goal,
     *   to-do, habit, or onboarding state changes.
     */
    operator fun invoke(userId: UserId): Flow<DashboardSummary>
}
