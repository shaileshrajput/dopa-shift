package com.dopashift.app.creation

import com.dopashift.domain.creation.DomainClock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [DomainClock] backed by the system clock and the device's default time zone.
 *
 * `now()` supplies wall-clock instants for entity and Change_Log timestamps; `today()` supplies
 * the current date in the user's configured (device default) time zone for a to-do's `dayDate`
 * and a habit's `startDate` (DQC-3.10, DQC-4.3). Keeping the `java.time` zone lookup here — in the
 * `app` layer — lets the `domain` interactors stay pure and deterministic under a fixed test clock.
 */
@Singleton
class SystemDomainClock @Inject constructor() : DomainClock {

    override fun now(): Instant = Instant.now()

    override fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
}
