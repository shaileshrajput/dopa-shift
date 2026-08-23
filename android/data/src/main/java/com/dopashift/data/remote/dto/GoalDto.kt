package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateGoalRequest(
    val name: String,
    val category: String,
    val keywords: List<String>
)

@JsonClass(generateAdapter = true)
data class UpdateGoalRequest(
    val name: String? = null,
    val category: String? = null,
    val keywords: List<String>? = null,
    val isActive: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class GoalResponse(
    val id: String,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String
)
