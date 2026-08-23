package com.dopashift.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dopashift.ui.theme.DopaShiftTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Analytics & Audit screen implementing Requirements 7.1, 7.2, 7.3, 7.4, 16.4.
 *
 * Displays:
 * - Historical bar charts and line graphs of daily Efficiency_Scores (Req 7.3)
 * - Date range selection: last 7, last 30 (default), last 90, or custom (Req 7.3)
 * - "No data" indicator for days with <1 minute tracked (Req 7.2)
 * - Lazy-loaded paginated data, max 50 records per page (Req 16.4)
 * - Offline-capable with locally cached scores; indicator when data unavailable (Req 7.4)
 *
 * Reactive data: Room -> Repository (Flow) -> ViewModel (combine + flatMapLatest) ->
 * UI (collectAsStateWithLifecycle).
 */
@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnalyticsContent(
        uiState = uiState,
        onDateRangeSelected = viewModel::selectDateRange,
        onToggleChartMode = viewModel::toggleChartMode,
        onLoadMore = viewModel::loadNextPage
    )
}

@Composable
private fun AnalyticsContent(
    uiState: AnalyticsUiState,
    onDateRangeSelected: (DateRangePreset) -> Unit,
    onToggleChartMode: () -> Unit,
    onLoadMore: () -> Unit
) {
    if (uiState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = DopaShiftTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.gutter)
    ) {
        item { Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base)) }

        // Header with chart mode toggle
        item {
            AnalyticsHeader(
                chartMode = uiState.chartMode,
                onToggleChartMode = onToggleChartMode
            )
        }

        // Date range selector chips (Req 7.3)
        item {
            DateRangeSelector(
                selectedPreset = uiState.selectedPreset,
                onPresetSelected = onDateRangeSelected
            )
        }

        // Offline indicator (Req 7.4)
        if (uiState.isOfflineIndicatorVisible) {
            item {
                OfflineIndicator()
            }
        }

        // Summary statistics card
        item {
            SummaryStatsCard(uiState = uiState)
        }

        // Chart visualization (Req 7.3: bar charts and line graphs)
        item {
            ChartCard(
                dataPoints = uiState.dataPoints,
                chartMode = uiState.chartMode
            )
        }

        // Load more button for pagination (Req 16.4)
        if (uiState.pagination.hasMore) {
            item {
                TextButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Load more data")
                }
            }
        }

        item { Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.section)) }
    }
}

// =====================================================================================
// Header
// =====================================================================================

@Composable
private fun AnalyticsHeader(
    chartMode: ChartMode,
    onToggleChartMode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Analytics",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onToggleChartMode) {
            Icon(
                imageVector = when (chartMode) {
                    ChartMode.BAR -> Icons.AutoMirrored.Filled.ShowChart
                    ChartMode.LINE -> Icons.Default.BarChart
                },
                contentDescription = when (chartMode) {
                    ChartMode.BAR -> "Switch to line chart"
                    ChartMode.LINE -> "Switch to bar chart"
                }
            )
        }
    }
}

// =====================================================================================
// Date Range Selector (Req 7.3: last 7, last 30 default, last 90, custom)
// =====================================================================================

@Composable
private fun DateRangeSelector(
    selectedPreset: DateRangePreset,
    onPresetSelected: (DateRangePreset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base)
    ) {
        DateRangePreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selectedPreset,
                onClick = { onPresetSelected(preset) },
                label = { Text(preset.label) },
                modifier = Modifier.semantics {
                    contentDescription = "${preset.label} date range filter"
                }
            )
        }
    }
}

// =====================================================================================
// Offline Indicator (Req 7.4)
// =====================================================================================

@Composable
private fun OfflineIndicator() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(DopaShiftTheme.spacing.stackSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.base)
        ) {
            Icon(
                imageVector = Icons.Default.SignalWifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Some data in this range is unavailable offline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

// =====================================================================================
// Summary Statistics Card
// =====================================================================================

@Composable
private fun SummaryStatsCard(uiState: AnalyticsUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Efficiency score summary statistics" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.gutter)) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Average",
                    value = uiState.averageScore?.let { "$it%" } ?: "—"
                )
                StatItem(
                    label = "Highest",
                    value = uiState.highestScore?.let { "$it%" } ?: "—"
                )
                StatItem(
                    label = "Lowest",
                    value = uiState.lowestScore?.let { "$it%" } ?: "—"
                )
                StatItem(
                    label = "Days tracked",
                    value = "${uiState.daysWithData}/${uiState.totalDays}"
                )
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================================
// Chart Card (Req 7.3: bar charts and line graphs)
// =====================================================================================

@Composable
private fun ChartCard(
    dataPoints: List<ScoreDataPoint>,
    chartMode: ChartMode
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Efficiency score chart" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(DopaShiftTheme.spacing.gutter)) {
            Text(
                text = when (chartMode) {
                    ChartMode.BAR -> "Daily Scores (Bar)"
                    ChartMode.LINE -> "Daily Scores (Line)"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.stackSm))

            if (dataPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Scrollable chart area for large datasets
                val chartWidth = (dataPoints.size * 24).coerceAtLeast(300)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
                    val errorColor = MaterialTheme.colorScheme.error
                    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

                    Canvas(
                        modifier = Modifier
                            .width(chartWidth.dp)
                            .height(200.dp)
                            .semantics {
                                contentDescription = "Efficiency score chart with ${dataPoints.size} data points"
                            }
                    ) {
                        when (chartMode) {
                            ChartMode.BAR -> drawBarChart(
                                dataPoints = dataPoints,
                                barColor = primaryColor,
                                unavailableColor = surfaceVariantColor,
                                gridColor = onSurfaceVariantColor.copy(alpha = 0.2f)
                            )
                            ChartMode.LINE -> drawLineChart(
                                dataPoints = dataPoints,
                                lineColor = primaryColor,
                                pointColor = primaryColor,
                                unavailableColor = errorColor,
                                gridColor = onSurfaceVariantColor.copy(alpha = 0.2f)
                            )
                        }
                    }
                }

                // Date labels (first, middle, last)
                if (dataPoints.size >= 2) {
                    Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.compact))
                    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = dataPoints.first().date.format(formatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dataPoints.last().date.format(formatter),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Legend
                Spacer(modifier = Modifier.height(DopaShiftTheme.spacing.base))
                ChartLegend()
            }
        }
    }
}

/**
 * Draw a bar chart visualization on Canvas.
 * Each bar represents one day's efficiency score.
 * Days with unavailable data (Req 7.2: <1 min tracked) show a short
 * placeholder bar in a distinct color.
 */
private fun DrawScope.drawBarChart(
    dataPoints: List<ScoreDataPoint>,
    barColor: Color,
    unavailableColor: Color,
    gridColor: Color
) {
    val chartHeight = size.height
    val chartWidth = size.width
    val barCount = dataPoints.size
    if (barCount == 0) return

    val barWidth = (chartWidth / barCount) * 0.7f
    val barSpacing = (chartWidth / barCount) * 0.3f

    // Draw horizontal grid lines at 25%, 50%, 75%, 100%
    for (percent in listOf(25, 50, 75, 100)) {
        val y = chartHeight - (chartHeight * percent / 100f)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1f
        )
    }

    // Draw bars
    dataPoints.forEachIndexed { index, point ->
        val x = index * (barWidth + barSpacing) + barSpacing / 2
        if (point.isUnavailable) {
            // Req 7.2: "No data" indicator — short placeholder bar
            val placeholderHeight = chartHeight * 0.05f
            drawRect(
                color = unavailableColor,
                topLeft = Offset(x, chartHeight - placeholderHeight),
                size = Size(barWidth, placeholderHeight)
            )
        } else {
            val scoreHeight = chartHeight * (point.scorePercent ?: 0) / 100f
            drawRect(
                color = barColor,
                topLeft = Offset(x, chartHeight - scoreHeight),
                size = Size(barWidth, scoreHeight)
            )
        }
    }
}

/**
 * Draw a line chart visualization on Canvas.
 * Points for unavailable days are rendered as small dots in a different color.
 */
private fun DrawScope.drawLineChart(
    dataPoints: List<ScoreDataPoint>,
    lineColor: Color,
    pointColor: Color,
    unavailableColor: Color,
    gridColor: Color
) {
    val chartHeight = size.height
    val chartWidth = size.width
    val pointCount = dataPoints.size
    if (pointCount == 0) return

    val pointSpacing = if (pointCount > 1) chartWidth / (pointCount - 1) else chartWidth / 2

    // Draw horizontal grid lines at 25%, 50%, 75%, 100%
    for (percent in listOf(25, 50, 75, 100)) {
        val y = chartHeight - (chartHeight * percent / 100f)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(chartWidth, y),
            strokeWidth = 1f
        )
    }

    // Build path for available data points only
    val path = Path()
    var pathStarted = false
    val availablePoints = mutableListOf<Offset>()

    dataPoints.forEachIndexed { index, point ->
        val x = if (pointCount > 1) index * pointSpacing else chartWidth / 2
        if (!point.isUnavailable && point.scorePercent != null) {
            val y = chartHeight - (chartHeight * point.scorePercent / 100f)
            val offset = Offset(x, y)
            availablePoints.add(offset)
            if (!pathStarted) {
                path.moveTo(x, y)
                pathStarted = true
            } else {
                path.lineTo(x, y)
            }
        }
    }

    // Draw the line
    if (availablePoints.size > 1) {
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }

    // Draw points
    dataPoints.forEachIndexed { index, point ->
        val x = if (pointCount > 1) index * pointSpacing else chartWidth / 2
        if (point.isUnavailable) {
            // Req 7.2: unavailable indicator dot
            val y = chartHeight - 4f
            drawCircle(
                color = unavailableColor,
                radius = 3f,
                center = Offset(x, y)
            )
        } else if (point.scorePercent != null) {
            val y = chartHeight - (chartHeight * point.scorePercent / 100f)
            drawCircle(
                color = pointColor,
                radius = 4f,
                center = Offset(x, y)
            )
        }
    }
}

// =====================================================================================
// Chart Legend
// =====================================================================================

@Composable
private fun ChartLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.gutter)
    ) {
        LegendItem(
            color = MaterialTheme.colorScheme.primary,
            label = "Score"
        )
        LegendItem(
            color = MaterialTheme.colorScheme.surfaceVariant,
            label = "No data (<1 min)"
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DopaShiftTheme.spacing.compact)
    ) {
        Canvas(modifier = Modifier.height(12.dp).width(12.dp)) {
            drawRect(color = color, size = size)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
