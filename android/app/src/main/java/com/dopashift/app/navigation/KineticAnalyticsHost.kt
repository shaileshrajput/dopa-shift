package com.dopashift.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.analytics.AnalyticsViewModel
import com.dopashift.ui.analytics.DateRangePreset
import com.dopashift.ui.analytics.ScoreDataPoint
import com.dopashift.ui.render.DailyBarCategory
import com.dopashift.ui.screens.AnalyticsActions
import com.dopashift.ui.screens.AnalyticsScreen
import com.dopashift.ui.state.AnalyticsUiState
import com.dopashift.ui.state.DailyBarUi
import com.dopashift.ui.state.DateRange
import com.dopashift.ui.state.SummaryMetrics

/** Score at/above which a tracked day is classified productive (mirrors the domain→UI mapper). */
private const val PRODUCTIVE_THRESHOLD = 50

/**
 * Presentation bridge hosting the Kinetic [AnalyticsScreen] (AUI-6) on the
 * [KineticRoutes.ANALYTICS] route (spec `android-ui-upgrade`, task 11.1).
 *
 * It observes the existing [AnalyticsViewModel] and maps its data points + summary stats into the
 * presentation-only [AnalyticsUiState] the Kinetic screen renders — the daily bars carry a
 * [DailyBarCategory] so the screen colors them via the shared `barColor` helper (AUI-6.4). The
 * date-range segmented filter dispatches back to [AnalyticsViewModel.selectDateRange] (AUI-6.1); the
 * bottom nav navigates via [onNavigate] with Analytics active (AUI-6.6). No parallel data path is
 * introduced.
 *
 * @param onNavigate invoked with the chosen bottom-nav [TopDestination] (AUI-6.6).
 * @param viewModel the existing analytics view-model (Hilt-provided).
 */
@Composable
fun KineticAnalyticsHost(
    onNavigate: (TopDestination) -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val vmState by viewModel.uiState.collectAsStateWithLifecycle()

    val state = AnalyticsUiState(
        range = vmState.selectedPreset.toKineticRange(),
        offlineOrPartial = vmState.isOfflineIndicatorVisible,
        summary = SummaryMetrics(
            averagePercent = vmState.averageScore ?: 0,
            highestPercent = vmState.highestScore ?: 0,
            lowestPercent = vmState.lowestScore ?: 0,
            daysTracked = vmState.daysWithData,
        ),
        dailyBars = vmState.dataPoints.map { it.toDailyBarUi() },
        categoryUsage = emptyList(),
    )

    val actions = AnalyticsActions(
        onRangeChange = { range -> viewModel.selectDateRange(range.toPreset()) },
        onNavigate = onNavigate,
    )

    AnalyticsScreen(state = state, on = actions)
}

/** Maps a domain-side [DateRangePreset] to the Kinetic [DateRange]; CUSTOM falls back to 30 days. */
private fun DateRangePreset.toKineticRange(): DateRange = when (this) {
    DateRangePreset.LAST_7 -> DateRange.LAST_7
    DateRangePreset.LAST_30 -> DateRange.LAST_30
    DateRangePreset.LAST_90 -> DateRange.LAST_90
    DateRangePreset.CUSTOM -> DateRange.LAST_30
}

/** Maps the Kinetic [DateRange] back to the view-model's [DateRangePreset]. */
private fun DateRange.toPreset(): DateRangePreset = when (this) {
    DateRange.LAST_7 -> DateRangePreset.LAST_7
    DateRange.LAST_30 -> DateRangePreset.LAST_30
    DateRange.LAST_90 -> DateRangePreset.LAST_90
}

/**
 * Maps one [ScoreDataPoint] to a Kinetic [DailyBarUi] (AUI-6.4). Unavailable / no-data days become
 * [DailyBarCategory.NO_DATA]; otherwise the score is classified productive/distracting against
 * [PRODUCTIVE_THRESHOLD]. The x-axis label uses the day-of-month.
 */
private fun ScoreDataPoint.toDailyBarUi(): DailyBarUi {
    val percent = (scorePercent ?: 0).coerceIn(0, 100)
    val category = when {
        isUnavailable || scorePercent == null -> DailyBarCategory.NO_DATA
        percent >= PRODUCTIVE_THRESHOLD -> DailyBarCategory.PRODUCTIVE
        else -> DailyBarCategory.DISTRACTING
    }
    return DailyBarUi(
        dayLabel = date.dayOfMonth.toString(),
        scorePercent = percent,
        category = category,
    )
}
