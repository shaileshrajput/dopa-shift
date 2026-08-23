package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SubmitEfficiencyScoreRequest(
    val date: String,
    val productiveSeconds: Long,
    val totalTrackedSeconds: Long,
    val scorePercent: Int?
)

@JsonClass(generateAdapter = true)
data class EfficiencyScoreResponse(
    val id: String,
    val date: String,
    val productiveSeconds: Long,
    val totalTrackedSeconds: Long,
    val scorePercent: Int?,
    val computedAt: String
)

@JsonClass(generateAdapter = true)
data class DashboardSummaryResponse(
    val pendingTodosCount: Int,
    val activeHabitsCount: Int,
    val goalsCount: Int,
    val todayEfficiencyScore: Int?,
    val efficiencyTrend: String? // IMPROVING, DECLINING, STABLE
)

@JsonClass(generateAdapter = true)
data class ActivityFeedResponse(
    val items: List<ActivityFeedItem>,
    val page: Int,
    val totalPages: Int,
    val totalItems: Int
)

@JsonClass(generateAdapter = true)
data class ActivityFeedItem(
    val id: String,
    val type: String,
    val description: String,
    val timestamp: String,
    val entityId: String?,
    val entityType: String?
)
