package com.dopashift.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dopashift.domain.entity.EfficiencyScore
import com.dopashift.domain.repository.EfficiencyScoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Date range preset options for the analytics view.
 * Requirement 7.3: default last 30 days.
 */
enum class DateRangePreset(val label: String, val days: Int) {
    LAST_7("Last 7 days", 7),
    LAST_30("Last 30 days", 30),
    LAST_90("Last 90 days", 90),
    CUSTOM("Custom", 0)
}

/**
 * Chart visualization mode toggle.
 */
enum class ChartMode {
    BAR, LINE
}

/**
 * Represents a single day's data point for chart rendering.
 */
data class ScoreDataPoint(
    val date: LocalDate,
    val scorePercent: Int?,
    val productiveSeconds: Long,
    val totalTrackedSeconds: Long,
    val isUnavailable: Boolean
)

/**
 * Pagination state for lazy-loaded score data.
 * Requirement 16.4: max 50 records per page.
 */
data class PaginationState(
    val currentPage: Int = 0,
    val pageSize: Int = 50,
    val hasMore: Boolean = false
)

/**
 * Complete UI state for the Analytics & Audit screen.
 */
data class AnalyticsUiState(
    val dataPoints: List<ScoreDataPoint> = emptyList(),
    val selectedPreset: DateRangePreset = DateRangePreset.LAST_30,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val chartMode: ChartMode = ChartMode.BAR,
    val averageScore: Int? = null,
    val highestScore: Int? = null,
    val lowestScore: Int? = null,
    val daysWithData: Int = 0,
    val totalDays: Int = 0,
    val pagination: PaginationState = PaginationState(),
    val isLoading: Boolean = true,
    val isOfflineIndicatorVisible: Boolean = false
)

/**
 * ViewModel for the Analytics & Audit screen.
 *
 * Observes efficiency scores from the local Room database via reactive Flow,
 * supporting date range selection and lazy-loaded paginated data.
 *
 * Offline-capable: displays locally cached scores. When backend-synced history
 * is unreachable for a requested range not available locally, shows an indicator
 * that remaining data is unavailable (Requirement 7.4).
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4, 16.4
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val efficiencyScoreRepository: EfficiencyScoreRepository
) : ViewModel() {

    // TODO: Replace with actual user ID from auth/session layer
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    private val _selectedPreset = MutableStateFlow(DateRangePreset.LAST_30)
    private val _customStartDate = MutableStateFlow<LocalDate?>(null)
    private val _customEndDate = MutableStateFlow<LocalDate?>(null)
    private val _chartMode = MutableStateFlow(ChartMode.BAR)
    private val _paginationState = MutableStateFlow(PaginationState())

    /**
     * Combined reactive UI state. Whenever the date range changes,
     * [flatMapLatest] switches to the new observation window.
     * Room's invalidation tracker triggers re-emission on data changes.
     */
    val uiState: StateFlow<AnalyticsUiState> = combine(
        _selectedPreset,
        _customStartDate,
        _customEndDate,
        _chartMode,
        _paginationState
    ) { preset, customStart, customEnd, chartMode, pagination ->
        DateRangeParams(preset, customStart, customEnd, chartMode, pagination)
    }.flatMapLatest { params ->
        val (startDate, endDate) = computeDateRange(params.preset, params.customStart, params.customEnd)
        efficiencyScoreRepository.observeByUserIdAndDateRange(userId, startDate, endDate)
            .combine(MutableStateFlow(params)) { scores, p ->
                buildUiState(scores, p, startDate, endDate)
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = AnalyticsUiState()
    )

    /**
     * Select a date range preset. Resets custom dates if not CUSTOM.
     */
    fun selectDateRange(preset: DateRangePreset) {
        _selectedPreset.value = preset
        if (preset != DateRangePreset.CUSTOM) {
            _customStartDate.value = null
            _customEndDate.value = null
        }
        // Reset pagination on range change
        _paginationState.value = PaginationState()
    }

    /**
     * Set a custom date range (only applies when preset is CUSTOM).
     */
    fun setCustomDateRange(start: LocalDate, end: LocalDate) {
        _selectedPreset.value = DateRangePreset.CUSTOM
        _customStartDate.value = start
        _customEndDate.value = end
        _paginationState.value = PaginationState()
    }

    /**
     * Toggle between bar chart and line graph visualization.
     */
    fun toggleChartMode() {
        _chartMode.update { current ->
            when (current) {
                ChartMode.BAR -> ChartMode.LINE
                ChartMode.LINE -> ChartMode.BAR
            }
        }
    }

    /**
     * Load next page of data (max 50 records per page per Requirement 16.4).
     */
    fun loadNextPage() {
        _paginationState.update { current ->
            if (current.hasMore) {
                current.copy(currentPage = current.currentPage + 1)
            } else {
                current
            }
        }
    }

    private fun computeDateRange(
        preset: DateRangePreset,
        customStart: LocalDate?,
        customEnd: LocalDate?
    ): Pair<LocalDate, LocalDate> {
        val today = LocalDate.now()
        return when (preset) {
            DateRangePreset.LAST_7 -> today.minusDays(6) to today
            DateRangePreset.LAST_30 -> today.minusDays(29) to today
            DateRangePreset.LAST_90 -> today.minusDays(89) to today
            DateRangePreset.CUSTOM -> {
                val start = customStart ?: today.minusDays(29)
                val end = customEnd ?: today
                start to end
            }
        }
    }

    private fun buildUiState(
        scores: List<EfficiencyScore>,
        params: DateRangeParams,
        startDate: LocalDate,
        endDate: LocalDate
    ): AnalyticsUiState {
        val totalDays = startDate.until(endDate).days + 1

        // Build data points for every day in the range
        val allDataPoints = (0L until totalDays).map { dayOffset ->
            val date = startDate.plusDays(dayOffset)
            val score = scores.find { it.date == date }
            ScoreDataPoint(
                date = date,
                scorePercent = score?.scorePercent,
                productiveSeconds = score?.productiveSeconds ?: 0L,
                totalTrackedSeconds = score?.totalTrackedSeconds ?: 0L,
                // Req 7.2: <1 minute (60s) tracked = unavailable
                isUnavailable = score == null || (score.totalTrackedSeconds < 60)
            )
        }

        // Apply pagination (max 50 per page, Req 16.4)
        val pageSize = params.pagination.pageSize
        val currentPage = params.pagination.currentPage
        val endIndex = ((currentPage + 1) * pageSize).coerceAtMost(allDataPoints.size)
        val paginatedDataPoints = allDataPoints.take(endIndex)
        val hasMore = endIndex < allDataPoints.size

        // Compute summary statistics (only from available scores)
        val availableScores = paginatedDataPoints.filter { !it.isUnavailable && it.scorePercent != null }
        val averageScore = if (availableScores.isNotEmpty()) {
            availableScores.mapNotNull { it.scorePercent }.average().toInt()
        } else null
        val highestScore = availableScores.mapNotNull { it.scorePercent }.maxOrNull()
        val lowestScore = availableScores.mapNotNull { it.scorePercent }.minOrNull()

        return AnalyticsUiState(
            dataPoints = paginatedDataPoints,
            selectedPreset = params.preset,
            customStartDate = params.customStart,
            customEndDate = params.customEnd,
            chartMode = params.chartMode,
            averageScore = averageScore,
            highestScore = highestScore,
            lowestScore = lowestScore,
            daysWithData = availableScores.size,
            totalDays = totalDays.toInt(),
            pagination = PaginationState(
                currentPage = currentPage,
                pageSize = pageSize,
                hasMore = hasMore
            ),
            isLoading = false,
            // Req 7.4: indicator when locally available data doesn't cover requested range
            isOfflineIndicatorVisible = scores.size < totalDays
        )
    }

    private data class DateRangeParams(
        val preset: DateRangePreset,
        val customStart: LocalDate?,
        val customEnd: LocalDate?,
        val chartMode: ChartMode,
        val pagination: PaginationState
    )
}
