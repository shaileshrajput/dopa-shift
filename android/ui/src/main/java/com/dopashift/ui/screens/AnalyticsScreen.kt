package com.dopashift.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dopashift.ui.R
import com.dopashift.ui.adaptive.TopDestination
import com.dopashift.ui.components.DopaShiftBottomNavBar
import com.dopashift.ui.components.KineticCard
import com.dopashift.ui.components.SegmentedPill
import com.dopashift.ui.components.UsageBar
import com.dopashift.ui.render.DailyBarCategory
import com.dopashift.ui.render.barColor
import com.dopashift.ui.state.AnalyticsUiState
import com.dopashift.ui.state.DailyBarUi
import com.dopashift.ui.state.DateRange
import com.dopashift.ui.state.SummaryMetrics
import com.dopashift.ui.state.UsageBarUi
import com.dopashift.ui.theme.DopaShiftTheme
import com.dopashift.ui.theme.DopaShiftTokens
import com.dopashift.ui.theme.ThemeMode

/**
 * Callbacks dispatched by [AnalyticsScreen] to its host view-model (spec `android-ui-upgrade`,
 * AUI-6). The screen is presentation-only: it renders the supplied [AnalyticsUiState] and forwards
 * user intent through these callbacks, holding no business logic of its own (design →
 * "presentation-only, no parallel data path").
 *
 * @property onRangeChange invoked with the newly selected date range when the segmented filter
 *   changes (AUI-6.1).
 * @property onNavigate invoked with the chosen bottom-nav destination (AUI-6.6, AUI-7.6).
 */
data class AnalyticsActions(
    val onRangeChange: (DateRange) -> Unit,
    val onNavigate: (TopDestination) -> Unit,
)

/**
 * The two-and-three-segment ordering for the date-range [SegmentedPill] (AUI-6.1). Index 0 is
 * [DateRange.LAST_7], index 1 is [DateRange.LAST_30], index 2 is [DateRange.LAST_90]. Kept as a
 * single source of truth so index↔enum mapping stays symmetric.
 */
private val DATE_RANGE_ORDER = listOf(DateRange.LAST_7, DateRange.LAST_30, DateRange.LAST_90)

/**
 * The **Analytics & Efficiency** screen (AUI-6).
 *
 * Renders — top to bottom — a date-range [SegmentedPill] (`Last 7/30/90 days`, AUI-6.1); an
 * offline / partial-data warning banner in a [DopaShiftTokens.Colors.dangerCoral] container shown
 * **only** when [AnalyticsUiState.offlineOrPartial] is true (AUI-6.2); a 4-column summary metric
 * grid of Average / Highest / Lowest / Days tracked with monospace metric readouts (AUI-6.3); a
 * daily-scores bar chart whose bars map their category to a token color via [barColor] plus a
 * bottom legend (AUI-6.4, Property 4); and horizontal category [UsageBar]s (AUI-6.5). The
 * [DopaShiftBottomNavBar] hosts the screen with [TopDestination.ANALYTICS] active (AUI-6.6).
 *
 * Every user-facing string comes from a localized resource and every color / size from
 * [DopaShiftTokens] (AUI-8.1, AUI-8.6). All intent is forwarded through [on].
 *
 * @param state the rendered analytics state.
 * @param on the intent callbacks.
 * @param modifier optional layout modifier for the screen root.
 */
@Composable
fun AnalyticsScreen(
    state: AnalyticsUiState,
    on: AnalyticsActions,
    modifier: Modifier = Modifier,
) {
    val selectedRangeIndex = DATE_RANGE_ORDER.indexOf(state.range).coerceAtLeast(0)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DopaShiftTokens.Colors.background,
        bottomBar = {
            DopaShiftBottomNavBar(
                current = TopDestination.ANALYTICS,
                onNavigate = on.onNavigate,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DopaShiftTokens.Colors.background),
            contentPadding = PaddingValues(DopaShiftTokens.Spacing.marginMobile),
            verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
        ) {
            // --- Screen title ---
            item(key = "title") {
                Text(
                    text = stringResource(R.string.aui_analytics_screen_title),
                    style = DopaShiftTokens.TextRoles.headlineMedium,
                    color = DopaShiftTokens.Colors.textPrimary,
                )
            }

            // --- Date-range segmented filter (AUI-6.1) ---
            item(key = "range") {
                val last7 = stringResource(R.string.aui_analytics_range_last_7)
                val last30 = stringResource(R.string.aui_analytics_range_last_30)
                val last90 = stringResource(R.string.aui_analytics_range_last_90)
                SegmentedPill(
                    options = listOf(last7, last30, last90),
                    selectedIndex = selectedRangeIndex,
                    onSelect = { index -> on.onRangeChange(DATE_RANGE_ORDER[index]) },
                )
            }

            // --- Offline / partial-data warning banner — ONLY when offlineOrPartial (AUI-6.2) ---
            if (state.offlineOrPartial) {
                item(key = "offline_banner") {
                    OfflineBanner()
                }
            }

            // --- Summary metric grid (AUI-6.3) ---
            item(key = "summary") {
                SummaryMetricGrid(summary = state.summary)
            }

            // --- Daily-scores bar chart + legend (AUI-6.4) ---
            item(key = "daily_scores") {
                DailyScoresCard(bars = state.dailyBars)
            }

            // --- Category usage bars (AUI-6.5) ---
            item(key = "usage") {
                CategoryUsageCard(usage = state.categoryUsage)
            }
        }
    }
}

/**
 * The offline / partial-data warning banner (AUI-6.2), rendered in a [DopaShiftTokens.Colors.dangerCoral]
 * container with a warning glyph, a title, and an explanatory body. Collapsed to a single TalkBack
 * node describing the offline state.
 */
@Composable
private fun OfflineBanner() {
    val description = stringResource(R.string.aui_analytics_offline_banner_content_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DopaShiftTokens.Radii.card))
            .background(DopaShiftTokens.Colors.dangerCoral)
            .padding(DopaShiftTokens.Spacing.space16)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.WarningAmber,
            contentDescription = null, // whole banner described via clearAndSetSemantics
            tint = DopaShiftTokens.Colors.textPrimary,
        )
        Spacer(modifier = Modifier.width(DopaShiftTokens.Spacing.space12))
        Column {
            Text(
                text = stringResource(R.string.aui_analytics_offline_banner_title),
                style = DopaShiftTokens.TextRoles.titleMedium,
                color = DopaShiftTokens.Colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.aui_analytics_offline_banner_body),
                style = DopaShiftTokens.TextRoles.bodySmall,
                color = DopaShiftTokens.Colors.textPrimary,
            )
        }
    }
}

/**
 * The 4-column summary metric grid (AUI-6.3): Average, Highest, Lowest, and Days tracked. Each
 * metric renders its numeric value in the monospace metric role to eliminate layout jitter
 * (AUI-8.4).
 */
@Composable
private fun SummaryMetricGrid(summary: SummaryMetrics) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(R.string.aui_analytics_summary_header))
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space12))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space8),
        ) {
            SummaryMetricCell(
                label = stringResource(R.string.aui_analytics_summary_average),
                value = stringResource(R.string.aui_analytics_metric_percent, summary.averagePercent),
                modifier = Modifier.weight(1f),
            )
            SummaryMetricCell(
                label = stringResource(R.string.aui_analytics_summary_highest),
                value = stringResource(R.string.aui_analytics_metric_percent, summary.highestPercent),
                modifier = Modifier.weight(1f),
            )
            SummaryMetricCell(
                label = stringResource(R.string.aui_analytics_summary_lowest),
                value = stringResource(R.string.aui_analytics_metric_percent, summary.lowestPercent),
                modifier = Modifier.weight(1f),
            )
            SummaryMetricCell(
                label = stringResource(R.string.aui_analytics_summary_days_tracked),
                value = summary.daysTracked.toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A single summary metric cell: the monospace numeric value above its secondary-tone label. The
 * value uses the [DopaShiftTokens.TextRoles.metricCounter] mono role so counters do not jitter
 * (AUI-8.4).
 */
@Composable
private fun SummaryMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space4),
    ) {
        Text(
            text = value,
            style = DopaShiftTokens.TextRoles.metricCounter,
            color = DopaShiftTokens.Colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = label,
            style = DopaShiftTokens.TextRoles.bodySmall,
            color = DopaShiftTokens.Colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The daily-scores bar chart card (AUI-6.4): a header, a row of vertical bars whose height is
 * proportional to each day's score and whose color is [barColor] of its category, and a bottom
 * legend mapping Productive / Distracting / No data to their token colors.
 */
@Composable
private fun DailyScoresCard(bars: List<DailyBarUi>) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(R.string.aui_analytics_daily_scores_header))
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space16))

        if (bars.isEmpty()) {
            EmptyHint(text = stringResource(R.string.aui_analytics_daily_scores_empty))
        } else {
            val chartDescription = stringResource(R.string.aui_analytics_chart_content_description)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    .semantics { contentDescription = chartDescription },
                horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space4),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    DailyBar(bar = bar, modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space16))
        ChartLegend()
    }
}

/**
 * A single vertical daily-score bar (AUI-6.4, Property 4). The filled column height is
 * proportional to [DailyBarUi.scorePercent] (clamped to `0..100`) and its color is [barColor] of
 * [DailyBarUi.category]; a day label sits beneath. The bar exposes a single TalkBack node naming
 * the day, its percent, and its category.
 */
@Composable
private fun DailyBar(
    bar: DailyBarUi,
    modifier: Modifier = Modifier,
) {
    val fraction = bar.scorePercent.coerceIn(0, 100) / 100f
    val categoryLabel = categoryLabel(bar.category)
    val description = stringResource(
        R.string.aui_analytics_bar_content_description,
        bar.dayLabel,
        bar.scorePercent.coerceIn(0, 100),
        categoryLabel,
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        // The bar column fills the remaining height above the day label; the filled portion is
        // sized to `fraction` of that column via a custom layout so height ∝ scorePercent.
        Box(
            modifier = Modifier
                .weight(1f)
                .width(BAR_WIDTH),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .width(BAR_WIDTH)
                    .fillMaxHeight()
                    .layout { measurable, constraints ->
                        val fullHeight = constraints.maxHeight
                        val barHeight = (fullHeight * fraction).toInt().coerceIn(0, fullHeight)
                        val placeable = measurable.measure(
                            constraints.copy(minHeight = barHeight, maxHeight = barHeight),
                        )
                        layout(placeable.width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
                    .clip(RoundedCornerShape(DopaShiftTokens.Radii.small))
                    .background(barColor(bar.category)),
            )
        }
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space4))
        Text(
            text = bar.dayLabel,
            style = DopaShiftTokens.TextRoles.labelMono,
            color = DopaShiftTokens.Colors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The daily-scores chart legend (AUI-6.4): a colored dot + label for each of Productive
 * ([DopaShiftTokens.Colors.momentumTeal]), Distracting ([DopaShiftTokens.Colors.dangerCoral]), and
 * No data ([DopaShiftTokens.Colors.chartNoData]), each color sourced from [barColor] so the legend
 * and the bars share one mapping.
 */
@Composable
private fun ChartLegend() {
    val legendDescription = stringResource(R.string.aui_analytics_legend_content_description)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = legendDescription },
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(
            color = barColor(DailyBarCategory.PRODUCTIVE),
            label = stringResource(R.string.aui_analytics_legend_productive),
        )
        LegendItem(
            color = barColor(DailyBarCategory.DISTRACTING),
            label = stringResource(R.string.aui_analytics_legend_distracting),
        )
        LegendItem(
            color = barColor(DailyBarCategory.NO_DATA),
            label = stringResource(R.string.aui_analytics_legend_no_data),
        )
    }
}

/** A single legend entry: a colored dot followed by its category label. */
@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(LEGEND_DOT_SIZE)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(DopaShiftTokens.Spacing.space8))
        Text(
            text = label,
            style = DopaShiftTokens.TextRoles.bodySmall,
            color = DopaShiftTokens.Colors.textSecondary,
        )
    }
}

/**
 * The category usage card (AUI-6.5): a header followed by a horizontal [UsageBar] for each
 * category, rendering its label, absolute-time value, and fill fraction in its category color.
 */
@Composable
private fun CategoryUsageCard(usage: List<UsageBarUi>) {
    KineticCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(text = stringResource(R.string.aui_analytics_usage_header))
        Spacer(modifier = Modifier.height(DopaShiftTokens.Spacing.space12))

        if (usage.isEmpty()) {
            EmptyHint(text = stringResource(R.string.aui_analytics_usage_empty))
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(DopaShiftTokens.Spacing.space12),
            ) {
                usage.forEach { bar ->
                    UsageBar(
                        label = bar.label,
                        value = bar.value,
                        fraction = bar.fraction,
                        color = bar.color,
                    )
                }
            }
        }
    }
}

/** A small section header used above the summary, chart, and usage sections. */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = DopaShiftTokens.TextRoles.titleMedium,
        color = DopaShiftTokens.Colors.textPrimary,
    )
}

/** An inline empty-state hint for the chart and usage lists. */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = DopaShiftTokens.TextRoles.bodyMedium,
        color = DopaShiftTokens.Colors.textSecondary,
        modifier = Modifier.clearAndSetSemantics { contentDescription = text },
    )
}

/** Resolves the localized legend / accessibility label for a [DailyBarCategory]. */
@Composable
private fun categoryLabel(category: DailyBarCategory): String = when (category) {
    DailyBarCategory.PRODUCTIVE -> stringResource(R.string.aui_analytics_legend_productive)
    DailyBarCategory.DISTRACTING -> stringResource(R.string.aui_analytics_legend_distracting)
    DailyBarCategory.NO_DATA -> stringResource(R.string.aui_analytics_legend_no_data)
}

// -------------------------------------------------------------------------------------------------
// Component dimensions (not design-system spacing tokens)
// -------------------------------------------------------------------------------------------------

/** Fixed height of the daily-scores bar chart plot area + day labels (AUI-6.4). */
private val CHART_HEIGHT: Dp = 160.dp

/** Width of a single daily-score bar column (AUI-6.4). */
private val BAR_WIDTH: Dp = 16.dp

/** Diameter of a legend color dot (AUI-6.4). */
private val LEGEND_DOT_SIZE: Dp = 12.dp

@Preview
@Composable
private fun AnalyticsScreenPreview() {
    DopaShiftTheme(themeMode = ThemeMode.DARK) {
        AnalyticsScreen(
            state = AnalyticsUiState(
                range = DateRange.LAST_7,
                offlineOrPartial = true,
                summary = SummaryMetrics(
                    averagePercent = 72,
                    highestPercent = 94,
                    lowestPercent = 41,
                    daysTracked = 7,
                ),
                dailyBars = listOf(
                    DailyBarUi("Mon", 80, DailyBarCategory.PRODUCTIVE),
                    DailyBarUi("Tue", 55, DailyBarCategory.DISTRACTING),
                    DailyBarUi("Wed", 0, DailyBarCategory.NO_DATA),
                    DailyBarUi("Thu", 94, DailyBarCategory.PRODUCTIVE),
                    DailyBarUi("Fri", 41, DailyBarCategory.DISTRACTING),
                    DailyBarUi("Sat", 68, DailyBarCategory.PRODUCTIVE),
                    DailyBarUi("Sun", 72, DailyBarCategory.PRODUCTIVE),
                ),
                categoryUsage = listOf(
                    UsageBarUi(
                        label = "Social",
                        value = "1h 20m",
                        fraction = 0.35f,
                        percentLabel = "35%",
                        color = DopaShiftTokens.Colors.dangerCoral,
                    ),
                    UsageBarUi(
                        label = "Learning",
                        value = "2h 05m",
                        fraction = 0.55f,
                        percentLabel = "55%",
                        color = DopaShiftTokens.Colors.momentumTeal,
                    ),
                ),
            ),
            on = AnalyticsActions(
                onRangeChange = {},
                onNavigate = {},
            ),
        )
    }
}
