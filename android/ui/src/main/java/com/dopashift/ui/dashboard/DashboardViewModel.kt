package com.dopashift.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.ChangeLogEntry
import com.dopashift.domain.entity.CheckpointStatus
import com.dopashift.domain.entity.DailyTodoItem
import com.dopashift.domain.entity.EfficiencyScore
import com.dopashift.domain.entity.GoalChecklistItem
import com.dopashift.domain.entity.GoalProfile
import com.dopashift.domain.entity.HabitTrack
import com.dopashift.domain.presentation.DashboardSectionInput
import com.dopashift.domain.presentation.DashboardSectionOrderer
import com.dopashift.domain.presentation.DashboardSectionType
import com.dopashift.domain.presentation.OrderedSection
import com.dopashift.domain.repository.ChangeLogRepository
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.EfficiencyScoreRepository
import com.dopashift.domain.repository.GoalChecklistItemRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.domain.repository.SyncStatusProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the Dashboard screen (DUX-3).
 *
 * Renders strictly from the local store: it subscribes only to local repository [Flow]s
 * (Room-backed) and never performs a network request on the synchronous render path
 * (DUX-3 AC10). Section ordering is delegated to the pure, deterministic
 * [DashboardSectionOrderer.order] in `domain` (DUX-3 AC1, AC2). Because Room re-emits on
 * any relevant change, an affected section updates well within the 2-second budget without
 * polling (DUX-3 AC4).
 *
 * State is split into two reactive streams so the ordering contract stays small:
 *  - [uiState] — the sealed [DashboardUiState] (skeleton [DashboardUiState.Loading] first,
 *    then [DashboardUiState.Content] carrying the ordered sections + header/indicators).
 *  - [sectionData] — the per-section item data the section bodies render.
 *
 * One-shot effects (e.g. an optimistic-update revert) are delivered exactly once via
 * [events]. The optimistic-update + revert transition itself is implemented in task 10.3;
 * this ViewModel exposes the [events] channel and the [emitEvent] hook it builds on.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val dailyTodoRepository: DailyTodoRepository,
    private val habitTrackRepository: HabitTrackRepository,
    private val efficiencyScoreRepository: EfficiencyScoreRepository,
    private val changeLogRepository: ChangeLogRepository,
    private val goalChecklistItemRepository: GoalChecklistItemRepository,
    private val syncStatusProvider: SyncStatusProvider
) : ViewModel() {

    // TODO: Replace with the authenticated user id from the auth/session layer.
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val today: LocalDate = LocalDate.now()
    private val activityPageSize = 20

    private val _activityPage = MutableStateFlow(0)
    private val _activityFeedState = MutableStateFlow<List<ActivityFeedEntry>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)

    /** Tracks the current activity-feed collection so load-more can replace it cleanly. */
    private var activityFeedJob: Job? = null

    /** One-shot effects delivered exactly once (consumed as non-blocking snackbars). */
    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    /**
     * Per-section item data, combined reactively from local repository Flows. The section
     * bodies subscribe to this; the ordered-section contract in [uiState] stays independent.
     */
    val sectionData: StateFlow<DashboardSectionData> = combine(
        goalRepository.observeByUserId(userId),
        dailyTodoRepository.observeByUserIdAndDate(userId, today),
        habitTrackRepository.observeActiveByUserId(userId),
        efficiencyScoreRepository.observeByUserIdAndDateRange(userId, today.minusDays(8), today),
        goalChecklistItemRepository.observeByUserId(userId)
    ) { goals, todos, habitTracks, scores, checklistItems ->
        buildSectionData(goals, todos, habitTracks, scores, checklistItems)
    }.combine(_activityFeedState) { data, feed ->
        data.copy(
            activityFeed = feed,
            // More pages remain when the current page came back completely full (a partial
            // page means we've reached the end of the change log).
            activityFeedHasMore = feed.size >= (_activityPage.value + 1) * activityPageSize
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = DashboardSectionData()
    )

    /**
     * The sealed Dashboard render state. Emits [DashboardUiState.Loading] until the first
     * local snapshot arrives, then [DashboardUiState.Content] with the deterministically
     * ordered sections and the header/indicator fields.
     */
    val uiState: StateFlow<DashboardUiState> = combine(
        sectionData,
        syncStatusProvider.observeSyncPending(),
        _isRefreshing
    ) { data, syncPending, refreshing ->
        DashboardUiState.Content(
            sections = orderSections(data),
            efficiencyScore = data.efficiencyScore,
            isSyncPending = syncPending,
            isRefreshing = refreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = DashboardUiState.Loading
    )

    init {
        loadActivityFeed()
    }

    // ---- Section ordering (delegates to the pure domain rule) ------------------------

    /**
     * Maps the current [DashboardSectionData] to the six [DashboardSectionInput] counts and
     * delegates ordering to the pure [DashboardSectionOrderer.order] (DUX-3 AC1, AC2).
     */
    private fun orderSections(data: DashboardSectionData): List<OrderedSection> {
        val habitCheckpointsDue = data.activeHabitTracks.count { track ->
            track.checkpoints.any {
                it.dayNumber == track.currentDay && it.status == CheckpointStatus.PENDING
            }
        }
        val inputs = listOf(
            DashboardSectionInput(DashboardSectionType.HABIT_CHECKPOINT_DUE, habitCheckpointsDue),
            DashboardSectionInput(DashboardSectionType.OVERDUE_TODOS, data.overdueTodos.size),
            DashboardSectionInput(
                DashboardSectionType.TODAY_TODOS,
                data.todayTodos.count { !it.isCompleted }
            ),
            DashboardSectionInput(DashboardSectionType.ACTIVE_GOALS, data.goalProgressSummaries.size),
            DashboardSectionInput(
                DashboardSectionType.EFFICIENCY_TREND,
                if (data.efficiencyScore != null) 1 else 0
            ),
            DashboardSectionInput(DashboardSectionType.ACTIVITY_FEED, data.activityFeed.size)
        )
        return DashboardSectionOrderer.order(inputs)
    }

    // ---- Section data assembly -------------------------------------------------------

    private fun buildSectionData(
        goals: List<GoalProfile>,
        todos: List<DailyTodoItem>,
        habitTracks: List<HabitTrack>,
        scores: List<EfficiencyScore>,
        checklistItems: List<GoalChecklistItem>
    ): DashboardSectionData {
        val activeGoals = goals.filter { it.isActive }
        val now = Instant.now()

        val todayScore = scores.find { it.date == today }?.scorePercent
        val previousDayScore = scores.find { it.date == today.minusDays(1) }?.scorePercent

        val goalProgressSummaries = activeGoals.map { goal ->
            val goalItems = checklistItems.filter { it.goalId == goal.id }
            GoalProgressSummary(
                goal = goal,
                completedItems = goalItems.count { it.isCompleted },
                totalItems = goalItems.size
            )
        }

        // (b) overdue vs (c) today's remaining: a to-do is overdue when it has a past due
        // instant and is not yet completed; everything else incomplete counts as today's.
        val (overdue, todaysRemaining) = todos
            .filter { !it.isCompleted }
            .partition { todo ->
                val due = todo.dueDateTime
                due != null && due.isBefore(now)
            }

        return DashboardSectionData(
            todayTodos = todaysRemaining,
            overdueTodos = overdue,
            activeHabitTracks = habitTracks,
            goalProgressSummaries = goalProgressSummaries,
            efficiencyScore = todayScore,
            previousDayScore = previousDayScore,
            efficiencyTrend = computeTrend(scores)
            // activityFeed / activityFeedHasMore are filled by the outer combine below.
        )
    }

    /**
     * Compares the most recent available score against the average of the preceding days;
     * returns [EfficiencyTrend.NOT_ENOUGH_DATA] when fewer than two days are available.
     */
    private fun computeTrend(scores: List<EfficiencyScore>): EfficiencyTrend {
        val available = scores.filter { it.scorePercent != null }
        if (available.size < 2) return EfficiencyTrend.NOT_ENOUGH_DATA

        val mostRecent = available.maxByOrNull { it.date } ?: return EfficiencyTrend.NOT_ENOUGH_DATA
        val current = mostRecent.scorePercent ?: return EfficiencyTrend.NOT_ENOUGH_DATA

        val preceding = available
            .filter { it.date != mostRecent.date }
            .mapNotNull { it.scorePercent }
        if (preceding.isEmpty()) return EfficiencyTrend.NOT_ENOUGH_DATA

        val diff = current - preceding.average()
        return when {
            diff > 2.0 -> EfficiencyTrend.IMPROVING
            diff < -2.0 -> EfficiencyTrend.DECLINING
            else -> EfficiencyTrend.STABLE
        }
    }

    // ---- Quick actions (offline-first: local write, Room re-emits reactively) --------

    /** Quick-add a new to-do from the inline "Today's Focus" input. */
    fun addTodo(text: String) {
        viewModelScope.launch {
            val newTodo = DailyTodoItem(
                id = UUID.randomUUID(),
                userId = userId,
                text = text,
                dueDateTime = null,
                isCompleted = false,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                dayDate = today
            )
            dailyTodoRepository.save(newTodo)
        }
    }

    /** Toggle a to-do's completion directly from the Dashboard. */
    fun setTodoCompleted(todoId: UUID, completed: Boolean) {
        viewModelScope.launch {
            val todo = dailyTodoRepository.findById(todoId) ?: return@launch
            dailyTodoRepository.save(todo.copy(isCompleted = completed, updatedAt = Instant.now()))
        }
    }

    /** Check off today's micro-habit checkpoint from the Dashboard. */
    fun completeHabitCheckpoint(trackId: UUID) {
        viewModelScope.launch {
            val track = habitTrackRepository.findById(trackId) ?: return@launch
            val updatedCheckpoints = track.checkpoints.map { checkpoint ->
                if (checkpoint.dayNumber == track.currentDay &&
                    checkpoint.status == CheckpointStatus.PENDING
                ) {
                    checkpoint.copy(status = CheckpointStatus.COMPLETED, completedAt = Instant.now())
                } else {
                    checkpoint
                }
            }
            val nextDay = updatedCheckpoints
                .filter { it.dayNumber > track.currentDay && it.status != CheckpointStatus.COMPLETED }
                .minByOrNull { it.dayNumber }?.dayNumber
                ?: (track.currentDay + 1).coerceAtMost(30)
            val isFinished = updatedCheckpoints.all {
                it.status == CheckpointStatus.COMPLETED || it.status == CheckpointStatus.MISSED
            }
            habitTrackRepository.save(
                track.copy(
                    currentDay = if (isFinished) track.currentDay else nextDay,
                    isFinished = isFinished,
                    checkpoints = updatedCheckpoints
                )
            )
        }
    }

    // ---- Pull-to-refresh & activity feed ---------------------------------------------

    /**
     * Marks the refresh indicator active for a pull-to-refresh (DUX-3 AC8). The actual
     * Sync_Engine pull is wired by the sync integration; this ViewModel only surfaces the
     * in-flight indicator and clears it via [endRefresh].
     */
    fun startRefresh() {
        _isRefreshing.value = true
    }

    /** Clears the pull-to-refresh indicator once the sync pull completes. */
    fun endRefresh() {
        _isRefreshing.value = false
    }

    /** Load the next page of activity feed entries (pages of 20), on demand. */
    fun loadMoreActivityFeed() {
        _activityPage.update { it + 1 }
        loadActivityFeed()
    }

    /**
     * Subscribe to the recent activity feed up to and including the current page. Loading is
     * lazy: only [loadMoreActivityFeed] (a user-driven "load more") advances the page and
     * widens the window, so no extra rows are fetched until the user asks for them. Each call
     * cancels the previous subscription so exactly one collector is active at a time.
     */
    private fun loadActivityFeed() {
        activityFeedJob?.cancel()
        activityFeedJob = viewModelScope.launch {
            val limit = (_activityPage.value + 1) * activityPageSize
            changeLogRepository.observeRecent(userId = userId, limit = limit, offset = 0)
                .collect { entries -> _activityFeedState.value = buildActivityEntries(entries) }
        }
    }

    private fun buildActivityEntries(entries: List<ChangeLogEntry>): List<ActivityFeedEntry> =
        entries.map { entry ->
            ActivityFeedEntry(
                id = entry.id,
                actionType = classifyActionType(entry.entityType, entry.field),
                entityName = entry.value ?: entry.entityType,
                timestamp = entry.timestamp,
                genericEntityType = entry.entityType,
                genericField = entry.field,
            )
        }

    /**
     * Classify a change-log entry into a stable [ActivityActionType]. The human-readable,
     * localized label is resolved from string resources in the Composable layer (DUX-4.11) — the
     * ViewModel deliberately emits no user-facing English text here.
     */
    private fun classifyActionType(entityType: String, field: String): ActivityActionType = when {
        entityType == "DailyTodoItem" && field == "isCompleted" -> ActivityActionType.TASK_COMPLETED
        entityType == "DailyTodoItem" && field == "text" -> ActivityActionType.TASK_UPDATED
        entityType == "DailyTodoItem" && field == "DELETE" -> ActivityActionType.TASK_DELETED
        entityType == "GoalChecklistItem" && field == "isCompleted" -> ActivityActionType.GOAL_TASK_COMPLETED
        entityType == "GoalChecklistItem" -> ActivityActionType.GOAL_TASK_UPDATED
        entityType == "HabitCheckpoint" && field == "status" -> ActivityActionType.HABIT_COMPLETED
        entityType == "GoalProfile" && field == "DELETE" -> ActivityActionType.GOAL_DELETED
        entityType == "GoalProfile" -> ActivityActionType.GOAL_CREATED
        else -> ActivityActionType.GENERIC
    }

    // ---- One-shot effect hooks (used by the optimistic-revert transition, task 10.3) --

    /** Emit a one-shot [UiEvent] (delivered exactly once via [events]). */
    internal fun emitEvent(event: UiEvent) {
        viewModelScope.launch { _events.send(event) }
    }
}
