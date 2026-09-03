package com.dopashift.domain.creation.usecase

import com.dopashift.domain.creation.DashboardSummary
import com.dopashift.domain.creation.DashboardSummaryRepository
import com.dopashift.domain.creation.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject

/**
 * The single implementation of [ObserveDashboardSummaryUseCase] (DQC-5.3, DQC-5.7).
 *
 * Pure Kotlin: it depends only on the [DashboardSummaryRepository] port and performs no network
 * call itself. The observed [DashboardSummary] is the one and only feed driving Zero_State /
 * Partial_State / populated rendering, so there is no parallel state path.
 *
 * On subscription it asks the repository to [DashboardSummaryRepository.refresh] the local
 * summary from the Backend, then emits the local [DashboardSummaryRepository.observe] flow. Local
 * observation comes first in ordering terms — the repository writes local Room before syncing, so
 * a creation is reflected within one second regardless of connectivity (DQC-1.5, DQC-2.9); the
 * refresh is best-effort reconciliation and its failure (e.g. offline) never breaks observation.
 *
 * The refresh and the observation are both scoped to the authenticated [UserId] the caller
 * supplies; the use case never derives state from another user's data on a client-supplied id
 * alone.
 */
class ObserveDashboardSummaryInteractor @Inject constructor(
    private val repository: DashboardSummaryRepository
) : ObserveDashboardSummaryUseCase {

    override fun invoke(userId: UserId): Flow<DashboardSummary> =
        repository.observe(userId)
            .onStart {
                // Best-effort reconciliation with the Backend summary endpoint. Swallow failures
                // (offline / transient) so the locally observed summary continues to drive the
                // Dashboard — the local write already reflects any just-created entity.
                runCatching { repository.refresh(userId) }
            }
            // Guard against the repository's observe flow itself erroring; the Dashboard state
            // feed must not terminate the collector on a transient store hiccup. Rethrow nothing —
            // an empty completion lets the UI keep its last rendered state.
            .catch { }
}
