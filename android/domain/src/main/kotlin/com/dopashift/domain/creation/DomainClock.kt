package com.dopashift.domain.creation

import java.time.Instant
import java.time.LocalDate

/**
 * Pure-Kotlin port supplying the current instant and the current date in the user's
 * configured time zone, so the creation interactors stay deterministic and framework-free
 * (no `java.time` clock/zone lookup performed inside `domain`).
 *
 * `now()` stamps `createdAt`/`updatedAt` and Change_Log timestamps; `today()` supplies a
 * to-do's `dayDate` and a habit's `startDate` as the current date in the user's tz
 * (DQC-3.10, DQC-4.3). It is implemented in the `data`/`app` layer and swapped for a fixed
 * clock in tests.
 */
interface DomainClock {
    /** @return the current instant, used for entity and Change_Log timestamps. */
    fun now(): Instant

    /** @return the current date in the user's configured time zone. */
    fun today(): LocalDate
}
