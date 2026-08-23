package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateTodoRequest(
    val text: String,
    val dueDateTime: String? = null,
    val dayDate: String
)

@JsonClass(generateAdapter = true)
data class UpdateTodoRequest(
    val text: String? = null,
    val dueDateTime: String? = null,
    val isCompleted: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class TodoResponse(
    val id: String,
    val text: String,
    val dueDateTime: String?,
    val isCompleted: Boolean,
    val dayDate: String,
    val createdAt: String,
    val updatedAt: String
)
