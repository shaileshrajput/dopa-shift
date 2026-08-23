package com.dopashift.data.remote.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateInterceptionRuleRequest(
    val goalId: String? = null,
    val appPackageName: String? = null,
    val siteDomain: String? = null,
    val dailyAllowanceMinutes: Int
)

@JsonClass(generateAdapter = true)
data class UpdateInterceptionRuleRequest(
    val goalId: String? = null,
    val appPackageName: String? = null,
    val siteDomain: String? = null,
    val dailyAllowanceMinutes: Int? = null,
    val isActive: Boolean? = null
)

@JsonClass(generateAdapter = true)
data class InterceptionRuleResponse(
    val id: String,
    val goalId: String?,
    val appPackageName: String?,
    val siteDomain: String?,
    val dailyAllowanceMinutes: Int,
    val isActive: Boolean,
    val createdAt: String
)
