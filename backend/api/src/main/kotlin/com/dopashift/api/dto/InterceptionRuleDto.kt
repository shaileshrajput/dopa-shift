package com.dopashift.api.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.util.UUID

/**
 * DTOs for the Interception Rules REST API.
 * Supports CRUD operations for app/site tracking rules with daily allowance validation.
 *
 * Requirements: 2.3
 */

// --- Request DTOs ---

data class CreateInterceptionRuleRequest(
    val goalId: UUID? = null,
    val appPackageName: String? = null,
    val siteDomain: String? = null,
    @field:Min(1, message = "Daily allowance must be at least 1 minute")
    @field:Max(480, message = "Daily allowance must be at most 480 minutes")
    val dailyAllowanceMinutes: Int
)

data class UpdateInterceptionRuleRequest(
    val goalId: UUID? = null,
    val appPackageName: String? = null,
    val siteDomain: String? = null,
    @field:Min(1, message = "Daily allowance must be at least 1 minute")
    @field:Max(480, message = "Daily allowance must be at most 480 minutes")
    val dailyAllowanceMinutes: Int? = null,
    val isActive: Boolean? = null
)

// --- Response DTO ---

data class InterceptionRuleResponse(
    val id: UUID,
    val userId: UUID,
    val goalId: UUID?,
    val appPackageName: String?,
    val siteDomain: String?,
    val dailyAllowanceMinutes: Int,
    val isActive: Boolean,
    val createdAt: String
)
