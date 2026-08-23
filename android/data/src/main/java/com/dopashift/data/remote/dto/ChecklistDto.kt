package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateChecklistItemRequest(
    val text: String
)

@JsonClass(generateAdapter = true)
data class UpdateChecklistItemRequest(
    val text: String? = null,
    val isCompleted: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class ChecklistItemResponse(
    val id: String,
    val goalId: String,
    val text: String,
    val isCompleted: Boolean,
    val createdAt: String,
    val updatedAt: String
)
