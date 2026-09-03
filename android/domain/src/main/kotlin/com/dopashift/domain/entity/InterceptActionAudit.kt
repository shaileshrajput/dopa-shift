package com.dopashift.domain.entity

import java.time.Instant
import java.util.UUID

/**
 * The action a user took on the Intercept_Screen overlay.
 *
 * - [CONTINUE]: the user chose "Continue to app" and returned to the Monitored_App.
 * - [SWITCH_TO_DOPASHIFT]: the user chose "Switch to DopaShift" and was taken to
 *   the DopaShift main activity.
 */
enum class InterceptActionType { CONTINUE, SWITCH_TO_DOPASHIFT }

/**
 * A local-only, aggregated audit entry recording that an intercept action was
 * executed for a given app package at a given time.
 *
 * This record is on-device only: it has no DTO and is never referenced by any
 * sync path. Individual intercept-interaction records never leave the device
 * (Requirements 3.4, 3.5). Pure Kotlin — no Android/framework dependencies.
 */
data class InterceptActionAudit(
    val id: UUID,
    val userId: UUID,
    val appPackageName: String,
    val actionType: InterceptActionType,
    val recordedAt: Instant
)
