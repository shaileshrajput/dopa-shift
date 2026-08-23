package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ActivateHabitTrackRequest(
    val goalId: String,
    val checkpoints: List<HabitCheckpointInput>
)

@JsonClass(generateAdapter = true)
data class HabitCheckpointInput(
    val dayNumber: Int,
    val description: String
)

@JsonClass(generateAdapter = true)
data class UpdateCheckpointRequest(
    val status: String // COMPLETED or MISSED
)

@JsonClass(generateAdapter = true)
data class HabitTrackResponse(
    val id: String,
    val goalId: String,
    val startDate: String,
    val currentDay: Int,
    val isFinished: Boolean,
    val checkpoints: List<HabitCheckpointResponse>,
    val createdAt: String,
    val updatedAt: String
)

@JsonClass(generateAdapter = true)
data class HabitCheckpointResponse(
    val id: String,
    val dayNumber: Int,
    val description: String,
    val status: String,
    val completedAt: String?
)
