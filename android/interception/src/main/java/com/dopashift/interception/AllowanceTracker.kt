package com.dopashift.interception

import com.dopashift.domain.entity.InterceptionRule
import com.dopashift.domain.repository.InterceptionRuleRepository
import com.dopashift.data.repository.LocalTelemetryRepository
import timber.log.Timber
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks daily foreground seconds per monitored app and detects allowance depletion.
 *
 * Responsibilities (Requirements 1.4, 1.5, 1.6, 3.5, 3.7):
 * - Accumulates foreground seconds per tracked app per day. Increments are buffered
 *   in memory and flushed to [LocalTelemetryRepository] at least once every 60 seconds
 *   (Requirement 1.5) rather than on every poll. On a flush failure the buffered value
 *   is retained and retried on the next cycle — no data loss, no double-count
 *   (Requirement 1.6).
 * - Looks up the Rule through the domain [InterceptionRuleRepository] port
 *   (Requirement 3.7 — a deleted Rule reports [AllowanceStatus.NOT_TRACKED]).
 * - Skips accumulation and depletion detection for a Rule that is paused-for-today
 *   ([InterceptionRule.isPausedOn], Requirement 3.5).
 * - Compares accumulated time (persisted + buffered) against the rule's
 *   dailyLimitMinutes * 60 and triggers [OnAllowanceDepleted] on depletion.
 * - Resets at midnight in the user's configured timezone (new day = fresh accumulation).
 *
 * The tracker is polled by [InterceptionService] on each poll cycle.
 * An injectable [Clock] enables deterministic unit testing of both the midnight
 * reset and the 60-second persistence cadence.
 */
@Singleton
class AllowanceTracker @Inject constructor(
    private val ruleRepository: InterceptionRuleRepository,
    private val telemetryRepository: LocalTelemetryRepository,
    private val clock: Clock
) {

    /**
     * Functional callback interface triggered when daily allowance is depleted.
     */
    fun interface OnAllowanceDepleted {
        /**
         * Called when the accumulated foreground time meets or exceeds the daily allowance.
         *
         * @param packageName The tracked app whose allowance was depleted.
         * @param rule The interception rule that was violated (domain entity).
         */
        fun onDepleted(packageName: String, rule: InterceptionRule)
    }

    /** Minimum time between telemetry flushes (Requirement 1.5). */
    private val flushIntervalSeconds: Long = 60L

    private var depletionListener: OnAllowanceDepleted? = null

    /**
     * In-memory cache of the current date (in the configured timezone) to detect midnight crossings.
     */
    @Volatile
    private var currentTrackingDate: LocalDate? = null

    /**
     * The timezone used for midnight reset detection.
     * Defaults to system default; should be set from user preferences.
     */
    @Volatile
    var userTimeZone: ZoneId = ZoneId.systemDefault()

    /**
     * The authenticated user whose Rules are consulted. Set by [InterceptionService]
     * from the started intent; every Rule lookup is scoped to this id (Requirement 3.8).
     */
    @Volatile
    var userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    /**
     * Per-app foreground seconds accumulated in memory but not yet flushed to the
     * Local_Store. Retained across a failed flush so no time is lost and none is
     * double-counted (Requirements 1.5, 1.6).
     */
    private val pendingSeconds: MutableMap<String, Long> = mutableMapOf()

    /**
     * Instant of the last successful flush per app. A flush is forced once the
     * gap since this instant reaches [flushIntervalSeconds].
     */
    private val lastFlush: MutableMap<String, Instant> = mutableMapOf()

    /**
     * Set of package names that have already triggered depletion today.
     * Prevents repeated triggers for the same app within a single day.
     */
    private val depletedToday: MutableSet<String> = mutableSetOf()

    /**
     * Registers a listener to be notified when any tracked app's allowance is depleted.
     */
    fun setOnAllowanceDepletedListener(listener: OnAllowanceDepleted?) {
        depletionListener = listener
    }

    /**
     * Called on each poll cycle by the interception service when a tracked app is detected
     * in the foreground. Buffers time, flushes on cadence, and checks for depletion.
     *
     * @param packageName The foreground app's package name.
     * @param elapsedSeconds Seconds elapsed since last poll (i.e., the poll interval in seconds).
     * @return [AllowanceStatus] indicating whether the app is tracked, within allowance, or depleted.
     */
    suspend fun onForegroundDetected(
        packageName: String,
        elapsedSeconds: Long
    ): AllowanceStatus {
        val today = resolveToday()
        resetIfNewDay(today)

        // Look up the interception rule for this package via the domain port.
        // A missing/deleted rule reports NOT_TRACKED (Requirement 3.7).
        val rule = ruleRepository.findActiveByPackage(userId, packageName)
            ?: run {
                // Drop any stale buffered time for a package that is no longer tracked.
                pendingSeconds.remove(packageName)
                lastFlush.remove(packageName)
                return AllowanceStatus.NOT_TRACKED
            }

        // A Rule paused for today attributes no accumulation and never intercepts
        // (Requirement 3.5). It auto-resumes once the local date advances past the
        // paused day because isPausedOn(today) is then false.
        if (rule.isPausedOn(today)) {
            Timber.v("AllowanceTracker: %s is paused for %s — skipping", packageName, today)
            return AllowanceStatus.WITHIN_ALLOWANCE
        }

        // Buffer the elapsed time in memory and flush on cadence (Requirements 1.5, 1.6).
        bufferSeconds(packageName, elapsedSeconds)
        flushIfDue(packageName, today)

        // Total for depletion comparison = persisted + still-buffered.
        val totalSeconds = totalAccumulated(packageName, today)
        val allowanceSeconds = rule.dailyLimitMinutes.toLong() * 60L

        Timber.d(
            "AllowanceTracker: %s accumulated %ds / %ds allowance",
            packageName, totalSeconds, allowanceSeconds
        )

        return if (totalSeconds >= allowanceSeconds) {
            triggerDepletion(packageName, rule)
            AllowanceStatus.DEPLETED
        } else {
            AllowanceStatus.WITHIN_ALLOWANCE
        }
    }

    /**
     * Checks if a specific app has already depleted its allowance today
     * without incrementing accumulated time. Useful for re-checking on overlay dismissal.
     * A paused-for-today Rule is never considered depleted (Requirement 3.5).
     */
    suspend fun isAllowanceDepleted(packageName: String): Boolean {
        val today = resolveToday()
        resetIfNewDay(today)

        val rule = ruleRepository.findActiveByPackage(userId, packageName)
            ?: return false

        if (rule.isPausedOn(today)) return false

        val totalSeconds = totalAccumulated(packageName, today)
        val allowanceSeconds = rule.dailyLimitMinutes.toLong() * 60L
        return totalSeconds >= allowanceSeconds
    }

    /**
     * Returns the remaining allowance seconds for a given app today.
     * Returns null if the app is not tracked.
     */
    suspend fun getRemainingSeconds(packageName: String): Long? {
        val today = resolveToday()
        val rule = ruleRepository.findActiveByPackage(userId, packageName)
            ?: return null

        val totalSeconds = totalAccumulated(packageName, today)
        val allowanceSeconds = rule.dailyLimitMinutes.toLong() * 60L
        return (allowanceSeconds - totalSeconds).coerceAtLeast(0L)
    }

    /**
     * Adds elapsed foreground seconds to the in-memory buffer for [packageName].
     */
    private fun bufferSeconds(packageName: String, elapsedSeconds: Long) {
        if (elapsedSeconds <= 0L) {
            // Still ensure a flush baseline exists so cadence tracking starts.
            lastFlush.putIfAbsent(packageName, clock.instant())
            return
        }
        pendingSeconds[packageName] = (pendingSeconds[packageName] ?: 0L) + elapsedSeconds
    }

    /**
     * Flushes buffered seconds for [packageName] to the Local_Store when at least
     * [flushIntervalSeconds] have elapsed since the last successful flush, or on the
     * first observation for the app. On a flush failure the buffered value is retained
     * and retried on the next cycle (Requirement 1.6).
     */
    private suspend fun flushIfDue(packageName: String, today: LocalDate) {
        val now = clock.instant()
        val last = lastFlush[packageName]
        if (last == null) {
            // First observation: establish the flush baseline. Persist immediately so
            // that even sub-60s sessions are durable, matching prior per-poll behavior
            // for the very first sample.
            attemptFlush(packageName, today, now)
            return
        }
        val elapsedSinceFlush = now.epochSecond - last.epochSecond
        if (elapsedSinceFlush >= flushIntervalSeconds) {
            attemptFlush(packageName, today, now)
        }
    }

    /**
     * Persists the buffered seconds for [packageName] and, only on success, clears the
     * buffer and advances the flush baseline. A failure leaves both untouched so the
     * value is retried next cycle without double-counting (Requirement 1.6).
     */
    private suspend fun attemptFlush(packageName: String, today: LocalDate, now: Instant) {
        val pending = pendingSeconds[packageName] ?: 0L
        if (pending <= 0L) {
            lastFlush[packageName] = now
            return
        }
        try {
            telemetryRepository.recordEvent(packageName, pending, today)
            pendingSeconds[packageName] = 0L
            lastFlush[packageName] = now
        } catch (e: Exception) {
            // Retain the pending value and the previous flush baseline; retry next cycle.
            Timber.w(e, "AllowanceTracker: flush failed for %s — retaining %ds", packageName, pending)
        }
    }

    /**
     * Total accumulated seconds for depletion comparison: persisted value in the
     * Local_Store plus any seconds still buffered in memory.
     */
    private suspend fun totalAccumulated(packageName: String, today: LocalDate): Long {
        val persisted = telemetryRepository.getAccumulatedSeconds(packageName, today)
        val pending = pendingSeconds[packageName] ?: 0L
        return persisted + pending
    }

    /**
     * Resolves "today" based on the user's configured timezone and the injectable clock.
     */
    private fun resolveToday(): LocalDate {
        return LocalDate.now(clock.withZone(userTimeZone))
    }

    /**
     * Detects a midnight crossing and resets internal state for the new day.
     * Accumulated telemetry is stored per-date in the repository, so the in-memory
     * depletion set, pending buffers, and flush baselines are reset for the new day.
     */
    private fun resetIfNewDay(today: LocalDate) {
        if (currentTrackingDate != today) {
            Timber.d("AllowanceTracker: midnight reset — new day %s (was %s)", today, currentTrackingDate)
            depletedToday.clear()
            pendingSeconds.clear()
            lastFlush.clear()
            currentTrackingDate = today
        }
    }

    /**
     * Fires the depletion callback if this app hasn't already triggered today.
     */
    private fun triggerDepletion(packageName: String, rule: InterceptionRule) {
        if (depletedToday.add(packageName)) {
            Timber.i("AllowanceTracker: allowance DEPLETED for %s", packageName)
            depletionListener?.onDepleted(packageName, rule)
        }
    }
}

/**
 * Result of an allowance check during a poll cycle.
 */
enum class AllowanceStatus {
    /** The app is not tracked by any active interception rule. */
    NOT_TRACKED,

    /** The app is tracked and still within its daily allowance. */
    WITHIN_ALLOWANCE,

    /** The app's daily allowance has been fully consumed — overlay should trigger. */
    DEPLETED
}
