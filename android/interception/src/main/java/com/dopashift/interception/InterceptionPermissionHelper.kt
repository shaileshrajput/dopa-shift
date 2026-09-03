package com.dopashift.interception

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks and assists with acquiring the permissions required for
 * the interception service.
 *
 * Validates: Requirement 2.1
 * - PACKAGE_USAGE_STATS requires manual grant via Settings.
 * - SYSTEM_ALERT_WINDOW requires manual grant via Settings.
 * Neither can be granted silently on Android 6.0+.
 */
@Singleton
class InterceptionPermissionHelper @Inject constructor() {

    private companion object {
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
    }

    /**
     * Returns true if the app has been granted PACKAGE_USAGE_STATS permission.
     *
     * The AppOps mode is the primary signal, but several OEM ROMs (notably MIUI/Xiaomi,
     * ColorOS/Oppo, and some Vivo builds) return [AppOpsManager.MODE_DEFAULT] here even when the
     * user has toggled Usage Access ON in Settings, which would otherwise cause a false
     * "missing permission" state. In that ambiguous case we fall back to actually querying
     * [UsageStatsManager]: if it returns data, usage access is effectively granted.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }

        val granted = when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            // Ambiguous OEM result — the op mode is unset, so probe the actual API.
            AppOpsManager.MODE_DEFAULT -> canQueryUsageStats(context)
            else -> false
        }
        Timber.d("UsageStats permission granted: $granted (appOpsMode=$mode)")
        return granted
    }

    /**
     * Probes [UsageStatsManager] directly to determine whether usage access is effectively
     * usable. Used only to disambiguate an [AppOpsManager.MODE_DEFAULT] result. Queries the last
     * hour of usage; a non-empty result means the OS is willing to hand us usage data, which is
     * only true when Usage Access has been granted.
     */
    private fun canQueryUsageStats(context: Context): Boolean {
        return try {
            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - ONE_HOUR_MS,
                now
            )
            val hasData = !stats.isNullOrEmpty()
            Timber.d("UsageStats MODE_DEFAULT fallback probe hasData=$hasData")
            hasData
        } catch (e: SecurityException) {
            // Throwing here unambiguously means access is not granted.
            Timber.d("UsageStats fallback probe threw SecurityException — not granted")
            false
        } catch (e: Exception) {
            Timber.w(e, "UsageStats fallback probe failed — treating as not granted")
            false
        }
    }

    /**
     * Returns true if the app has been granted SYSTEM_ALERT_WINDOW permission.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        val granted = Settings.canDrawOverlays(context)
        Timber.d("Overlay permission granted: $granted")
        return granted
    }

    /**
     * Returns true if both required permissions are granted.
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasUsageStatsPermission(context) && hasOverlayPermission(context)
    }

    /**
     * Creates an intent to navigate the user to the usage access settings screen.
     * The user must manually grant PACKAGE_USAGE_STATS from this screen.
     */
    fun createUsageStatsSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates an intent to navigate the user to the overlay permission settings screen.
     * The user must manually grant SYSTEM_ALERT_WINDOW from this screen.
     */
    fun createOverlaySettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Result describing which permissions are missing.
     */
    data class PermissionStatus(
        val hasUsageStats: Boolean,
        val hasOverlay: Boolean
    ) {
        val allGranted: Boolean get() = hasUsageStats && hasOverlay
    }

    /**
     * Returns the current status of all required permissions.
     */
    fun checkPermissionStatus(context: Context): PermissionStatus {
        return PermissionStatus(
            hasUsageStats = hasUsageStatsPermission(context),
            hasOverlay = hasOverlayPermission(context)
        )
    }

    /**
     * Returns permission status from an [InterceptionContext] abstraction (testable).
     */
    fun checkPermissionStatus(context: InterceptionContext): PermissionStatus {
        return PermissionStatus(
            hasUsageStats = context.hasUsageStats,
            hasOverlay = context.hasOverlay
        )
    }
}
