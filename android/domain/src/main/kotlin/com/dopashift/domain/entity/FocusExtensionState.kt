package com.dopashift.domain.entity

import java.time.Instant
import java.time.LocalDate

/**
 * Ephemeral, day-scoped Focus_Extension state for a single Monitored_App.
 *
 * Tracks how many extensions have been granted for [packageName] on [date] and,
 * when an extension is active, the [activeUntil] instant at which it expires.
 * Held in memory only (reset at the local midnight boundary); never synced.
 */
data class FocusExtensionState(
    val packageName: String,
    val date: LocalDate,
    val extensionsGranted: Int = 0,
    val activeUntil: Instant? = null
)
