package com.dopashift.interception

import com.dopashift.data.local.dao.InterceptionRuleDao
import com.dopashift.data.local.entity.LocalInterceptionRule
import com.dopashift.data.repository.LocalTelemetryRepository
import timber.log.Timber
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks daily foreground seconds per monitored app and detects allowance depletion.
 *
 * Responsibilities (Requirements 2.3, 2.4, 2.12):
 * - Accumulates foreground seconds per tracked app per day via [LocalTelemetryRepository].
 * - Compares accumulated time against the rule's dailyAllowanceMinutes * 60.
 * - Triggers [OnAllowanceDepleted] callback within 2 seconds of depletion detection.
 * - Resets at midnight in the user's configured timezone (new day = fresh accumulation).
 *
 * The tracker is polled by [InterceptionService] on each poll cycle.
 * An injectable [Clock] enables deterministic unit testing.
 */
@Singleton
class AllowanceTracker @Inject constructor(
    private val interceptionRuleDao: InterceptionRuleDao,
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
         * @param rule The interception rule that was violated.
         */
        fun onDepleted(packageName: String, rule: LocalInterceptionRule)
    }

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
     * in the foreground. Accumulates time and checks for depletion.
     *
     * @param packageName The foreground app's package name.
     * @param elapsedSeconds Seconds elapsed since last poll (i.e., the poll interval in seconds).
     * @return [AllowanceStatus] indicating whether the app's allowance is still valid or depleted.
     */
    suspend fun onForegroundDetected(
        packageName: String,
        elapsedSeconds: Long
    ): AllowanceStatus {
        val today = resolveToday()
        resetIfNewDay(today)

        // Look up the interception rule for this package
        val rule = interceptionRuleDao.findActiveByPackageName(packageName)
            ?: return AllowanceStatus.NOT_TRACKED

        // Persist the accumulated time
        telemetryRepository.recordEvent(packageName, elapsedSeconds, today)

        // Get total accumulated seconds for today
        val totalSeconds = telemetryRepository.getAccumulatedSeconds(packageName, today)
        val allowanceSeconds = rule.dailyAllowanceMinutes.toLong() * 60L

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
     */
    suspend fun isAllowanceDepleted(packageName: String): Boolean {
        val today = resolveToday()
        resetIfNewDay(today)

        val rule = interceptionRuleDao.findActiveByPackageName(packageName)
            ?: return false

        val totalSeconds = telemetryRepository.getAccumulatedSeconds(packageName, today)
        val allowanceSeconds = rule.dailyAllowanceMinutes.toLong() * 60L
        return totalSeconds >= allowanceSeconds
    }

    /**
     * Returns the remaining allowance seconds for a given app today.
     * Returns null if the app is not tracked.
     */
    suspend fun getRemainingSeconds(packageName: String): Long? {
        val today = resolveToday()
        val rule = interceptionRuleDao.findActiveByPackageName(packageName)
            ?: return null

        val totalSeconds = telemetryRepository.getAccumulatedSeconds(packageName, today)
        val allowanceSeconds = rule.dailyAllowanceMinutes.toLong() * 60L
        return (allowanceSeconds - totalSeconds).coerceAtLeast(0L)
    }

    /**
     * Resolves "today" based on the user's configured timezone and the injectable clock.
     */
    private fun resolveToday(): LocalDate {
        return LocalDate.now(clock.withZone(userTimeZone))
    }

    /**
     * Detects a midnight crossing and resets internal state for the new day.
     * Accumulated telemetry is stored per-date in the repository, so only
     * the in-memory depletion set needs resetting.
     */
    private fun resetIfNewDay(today: LocalDate) {
        if (currentTrackingDate != today) {
            Timber.d("AllowanceTracker: midnight reset — new day %s (was %s)", today, currentTrackingDate)
            depletedToday.clear()
            currentTrackingDate = today
        }
    }

    /**
     * Fires the depletion callback if this app hasn't already triggered today.
     */
    private fun triggerDepletion(packageName: String, rule: LocalInterceptionRule) {
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
