package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncPushRequest(
    val events: List<SyncEvent>
)

@JsonClass(generateAdapter = true)
data class SyncPushResponse(
    val serverTimestamps: List<ServerTimestamp>,
    val conflicts: List<ResolvedConflict> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncPullResponse(
    val events: List<SyncEvent>,
    val serverTimestamp: Long
)

@JsonClass(generateAdapter = true)
data class SyncEvent(
    val id: String,
    val entityId: String,
    val entityType: String,
    val field: String,
    val value: String?,
    val timestamp: Long,
    val deviceId: String,
    val userId: String
)

@JsonClass(generateAdapter = true)
data class ServerTimestamp(
    val eventId: String,
    val serverTimestamp: Long
)

@JsonClass(generateAdapter = true)
data class ResolvedConflict(
    val eventId: String,
    val resolution: String
)

@JsonClass(generateAdapter = true)
data class ConflictResponse(
    val id: String,
    val changeLogId: String,
    val supersededValue: String?,
    val supersededDeviceId: String?,
    val supersededTimestamp: Long?,
    val resolutionReason: String,
    val createdAt: String
)
