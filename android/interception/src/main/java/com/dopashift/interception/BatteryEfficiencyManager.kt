package com.dopashift.interception

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages battery and memory efficiency constraints for the interception background service.
 *
 * Requirements: 16.2, 16.3, 16.6, 16.8
 * - Request battery optimization exemption with user-facing explanation.
 * - Release wake locks within 500ms of check completion.
 * - Keep heap ≤ 64MB for up to 20 tracked apps.
 * - Target ≤ 3% battery per hour.
 */
@Singleton
class BatteryEfficiencyManager @Inject constructor() {

    companion object {
        /** Maximum heap usage for the interception service (64MB). Requirement 16.6. */
        const val MAX_HEAP_BYTES = 64L * 1024 * 1024

        /** Maximum number of tracked apps before memory constraints apply. */
        const val MAX_TRACKED_APPS = 20

        /** Maximum duration (ms) a wake lock should be held per poll cycle. Requirement 16.3. */
        const val MAX_WAKE_LOCK_DURATION_MS = 500L

        /** Target battery drain: ≤3% per hour. Used for telemetry/monitoring. */
        const val TARGET_BATTERY_PERCENT_PER_HOUR = 3.0f
    }

    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Checks whether the app is exempt from battery optimization (doze mode).
     * Returns true if already exempt.
     */
    fun isBatteryOptimizationExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Creates an intent to request battery optimization exemption from the user.
     * Shows system dialog with DopaShift explanation.
     *
     * Requirement 16.2: Request battery optimization exemption with user-facing explanation.
     */
    fun createBatteryOptimizationExemptionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Acquires a partial wake lock for the duration of a poll check.
     * The lock auto-releases after [MAX_WAKE_LOCK_DURATION_MS] to comply with Requirement 16.3.
     *
     * @param context Application context.
     * @return The acquired wake lock (already released after timeout), or null on failure.
     */
    fun acquireCheckWakeLock(context: Context): PowerManager.WakeLock? {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DopaShift:InterceptionCheck"
            ).apply {
                // Auto-release within 500ms — Requirement 16.3
                acquire(MAX_WAKE_LOCK_DURATION_MS)
            }
            wakeLock = lock
            Timber.v("Wake lock acquired (auto-release in ${MAX_WAKE_LOCK_DURATION_MS}ms)")
            return lock
        } catch (e: Exception) {
            Timber.e(e, "Failed to acquire wake lock")
            return null
        }
    }

    /**
     * Explicitly releases the wake lock if still held.
     * Should be called immediately after the poll check completes.
     */
    fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Timber.v("Wake lock released explicitly")
            }
        }
        wakeLock = null
    }

    /**
     * Returns the current estimated heap usage of the process in bytes.
     */
    fun getCurrentHeapUsageBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /**
     * Checks if current heap usage is within the allowed limit.
     * Returns true if heap is below [MAX_HEAP_BYTES].
     *
     * Requirement 16.6: Keep heap ≤ 64MB for up to 20 tracked apps.
     */
    fun isHeapWithinLimit(): Boolean {
        val used = getCurrentHeapUsageBytes()
        val withinLimit = used < MAX_HEAP_BYTES
        if (!withinLimit) {
            Timber.w("Heap usage ${used / (1024 * 1024)}MB exceeds ${MAX_HEAP_BYTES / (1024 * 1024)}MB limit")
        }
        return withinLimit
    }

    /**
     * Returns the maximum heap available to this process.
     */
    fun getMaxHeapBytes(): Long {
        return Runtime.getRuntime().maxMemory()
    }

    /**
     * Validates that the polling configuration is memory-efficient for the given
     * number of tracked apps.
     *
     * @param trackedAppCount Number of apps currently being tracked.
     * @return [EfficiencyStatus] indicating if the configuration is sustainable.
     */
    fun validateEfficiency(trackedAppCount: Int): EfficiencyStatus {
        val heapUsed = getCurrentHeapUsageBytes()
        val heapOk = heapUsed < MAX_HEAP_BYTES
        val appCountOk = trackedAppCount <= MAX_TRACKED_APPS

        return when {
            !heapOk -> EfficiencyStatus.HEAP_EXCEEDED
            !appCountOk -> EfficiencyStatus.TOO_MANY_APPS
            else -> EfficiencyStatus.WITHIN_LIMITS
        }
    }

    /**
     * Suggests an appropriate poll interval based on current resource usage.
     * If heap is high or battery is low, recommends a longer interval to conserve resources.
     *
     * @param currentIntervalMs The current poll interval.
     * @param batteryPercent Current battery level (0-100).
     * @return Suggested interval in milliseconds.
     */
    fun suggestPollInterval(currentIntervalMs: Long, batteryPercent: Int): Long {
        return when {
            batteryPercent < 15 -> {
                // Critical battery: extend to maximum interval (30s)
                InterceptionConfig.MAX_POLL_INTERVAL_MS
            }
            batteryPercent < 30 -> {
                // Low battery: extend interval by 2x, clamped
                (currentIntervalMs * 2).coerceAtMost(InterceptionConfig.MAX_POLL_INTERVAL_MS)
            }
            !isHeapWithinLimit() -> {
                // High memory: increase interval to reduce pressure
                (currentIntervalMs * 2).coerceAtMost(InterceptionConfig.MAX_POLL_INTERVAL_MS)
            }
            else -> currentIntervalMs
        }
    }
}

/**
 * Status of the interception service's resource efficiency.
 */
enum class EfficiencyStatus {
    /** All resource constraints are satisfied. */
    WITHIN_LIMITS,

    /** Heap usage exceeds the 64MB target. */
    HEAP_EXCEEDED,

    /** More than 20 apps are being tracked simultaneously. */
    TOO_MANY_APPS
}
