package com.dopashift.data.repository

import com.dopashift.data.local.dao.DailyTodoDao
import com.dopashift.data.local.dao.GoalDao
import com.dopashift.data.local.dao.HabitTrackDao
import com.dopashift.data.remote.DopaShiftApi
import com.dopashift.domain.creation.DashboardSummary
import com.dopashift.domain.creation.DashboardSummaryRepository
import com.dopashift.domain.creation.OnboardingStatus
import com.dopashift.domain.creation.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [DashboardSummaryRepository].
 *
 * [observe] derives the [DashboardSummary] locally by combining three reactive count
 * flows — active goals, today's to-dos, and active habit tracks — with the persisted
 * onboarding status from [QuickCreatePreferencesStore]. Because every count is read
 * from the local Room store (kept fresh by the Sync_Engine's change-log replay), the
 * Dashboard render path issues no network call to decide Zero_State vs Partial_State
 * (Backend Delta 2, DQC-5.3), and each successful local creation re-emits the summary
 * so the Dashboard reflects the new entity within one second (DQC-1.5, DQC-2.9).
 *
 * Security: every DAO query is scoped by the authenticated [userId] (as its UUID
 * string) — no count is derived from another user's rows on a client-supplied id
 * alone.
 */
@Singleton
class RoomDashboardSummaryRepository @Inject constructor(
    private val goalDao: GoalDao,
    private val dailyTodoDao: DailyTodoDao,
    private val habitTrackDao: HabitTrackDao,
    private val preferencesStore: QuickCreatePreferencesStore,
    private val api: DopaShiftApi
) : DashboardSummaryRepository {

    override fun observe(userId: UserId): Flow<DashboardSummary> {
        val userIdString = userId.toString()
        // "Today" is resolved in the device's default time zone. The user's configured
        // time zone is not persisted locally in a usable form yet; when it is, replace
        // ZoneId.systemDefault() with the user's configured zone (DQC-4.3, DQC-3.10).
        val today: LocalDate = LocalDate.now(ZoneId.systemDefault())

        val activeGoalCounts = goalDao.observeActiveCountByUserId(userIdString)
        val todayTodoCounts = dailyTodoDao.observeCountByUserIdAndDate(userIdString, today.toString())
        val activeHabitCounts = habitTrackDao.observeActiveCountByUserId(userIdString)
        val prefs = preferencesStore.observe()

        return combine(
            activeGoalCounts,
            todayTodoCounts,
            activeHabitCounts,
            prefs
        ) { activeGoalCount, todayTodoCount, activeHabitCount, quickCreatePrefs ->
            DashboardSummary(
                userId = userId,
                activeGoalCount = activeGoalCount,
                todayTodoCount = todayTodoCount,
                activeHabitCount = activeHabitCount,
                onboardingStatus = quickCreatePrefs.onboardingStatus
            )
        }
    }

    override suspend fun refresh(userId: UserId) {
        // Best-effort reconciliation against GET /v1/dashboard/summary. The endpoint is
        // scoped server-side by the authenticated token and accepts no client-supplied
        // user id, so it never returns another user's data. Its response is aggregate
        // counts only; the authoritative per-entity data that `observe` counts is
        // reconciled through the Sync_Engine's change-log pull, so a failure here must
        // never break the offline-first local read path.
        //
        // TODO(task 17.2): once GET /v1/dashboard/summary is finalized, use the returned
        //  counts to trigger/verify a Sync_Engine pull for this user so local entities
        //  (and therefore the observed counts) reconcile with the backend.
        try {
            api.getDashboardSummary()
        } catch (_: Exception) {
            // Offline or transient failure: local counts remain the source of truth.
        }
    }
}
