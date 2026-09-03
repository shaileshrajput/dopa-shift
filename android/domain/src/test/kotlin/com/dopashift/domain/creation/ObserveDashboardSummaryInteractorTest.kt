package com.dopashift.domain.creation

// Feature: dashboard-quick-create
// Tags: DQC-5.3, DQC-5.7, DQC-1.5, DQC-2.9

import com.dopashift.domain.creation.usecase.ObserveDashboardSummaryInteractor
import io.kotest.core.spec.style.StringSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.util.UUID

/**
 * Unit tests for [ObserveDashboardSummaryInteractor] (tasks.md 11.2, DQC-5.3, DQC-5.7, DQC-1.5,
 * DQC-2.9).
 *
 * Assert that the use case:
 * - sources its [DashboardSummary] flow from the [DashboardSummaryRepository];
 * - re-emits a fresh summary whenever the repository's observed state changes, so a creation is
 *   reflected without a second query (DQC-1.5, DQC-2.9);
 * - triggers a best-effort [DashboardSummaryRepository.refresh] on subscription and keeps
 *   observing even when that refresh fails (offline);
 * - scopes every read to the supplied authenticated user id.
 *
 * All fakes are in-memory and framework-free so the domain module stays pure Kotlin.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDashboardSummaryInteractorTest : StringSpec({

    val userId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa")

    fun summary(goals: Int, todos: Int, habits: Int, status: OnboardingStatus) =
        DashboardSummary(
            userId = userId,
            activeGoalCount = goals,
            todayTodoCount = todos,
            activeHabitCount = habits,
            onboardingStatus = status
        )

    "invoke emits the repository's observed summary for the requested user" {
        runTest {
            val expected = summary(1, 2, 3, OnboardingStatus.COMPLETED)
            val repo = FakeDashboardSummaryRepository(
                observed = { flow { emit(expected) } }
            )

            val interactor = ObserveDashboardSummaryInteractor(repo)

            val emitted = interactor(userId).take(1).toList()

            emitted.single() shouldBe expected
            repo.observedUserIds shouldBe listOf(userId)
        }
    }

    "invoke re-emits when the repository state changes so a creation is reflected without re-query" {
        runTest {
            val state = MutableStateFlow(summary(0, 0, 0, OnboardingStatus.SKIPPED))
            val repo = FakeDashboardSummaryRepository(observed = { state })

            val interactor = ObserveDashboardSummaryInteractor(repo)

            val collected = ArrayList<DashboardSummary>()
            val job = launch { interactor(userId).take(2).toList(collected) }

            // Let the collector subscribe and receive the initial state before it changes, so the
            // conflating StateFlow does not drop the first value.
            runCurrent()

            // Simulate a successful creation updating the local store the repository observes.
            state.value = summary(1, 0, 0, OnboardingStatus.SKIPPED)
            job.join()

            collected shouldBe listOf(
                summary(0, 0, 0, OnboardingStatus.SKIPPED),
                summary(1, 0, 0, OnboardingStatus.SKIPPED)
            )
        }
    }

    "invoke refreshes the summary on subscription for the requested user" {
        runTest {
            val repo = FakeDashboardSummaryRepository(
                observed = { flow { emit(summary(0, 0, 0, OnboardingStatus.NOT_STARTED)) } }
            )

            val interactor = ObserveDashboardSummaryInteractor(repo)
            interactor(userId).take(1).toList()

            repo.refreshedUserIds shouldBe listOf(userId)
        }
    }

    "invoke keeps observing when the on-subscription refresh fails (offline)" {
        runTest {
            val expected = summary(2, 0, 0, OnboardingStatus.COMPLETED)
            val repo = FakeDashboardSummaryRepository(
                observed = { flow { emit(expected) } },
                refreshError = { throw IllegalStateException("offline") }
            )

            val interactor = ObserveDashboardSummaryInteractor(repo)

            val emitted = interactor(userId).take(1).toList()

            emitted.single() shouldBe expected
        }
    }
})

/**
 * In-memory [DashboardSummaryRepository] recording the ids it was asked to observe/refresh, with
 * injectable observed-flow and refresh behavior for the tests above.
 */
private class FakeDashboardSummaryRepository(
    private val observed: (UserId) -> Flow<DashboardSummary>,
    private val refreshError: (() -> Unit)? = null
) : DashboardSummaryRepository {

    val observedUserIds = ArrayList<UserId>()
    val refreshedUserIds = ArrayList<UserId>()

    override fun observe(userId: UserId): Flow<DashboardSummary> {
        observedUserIds.add(userId)
        return observed(userId)
    }

    override suspend fun refresh(userId: UserId) {
        refreshedUserIds.add(userId)
        refreshError?.invoke()
    }
}
