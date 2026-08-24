package com.dopashift.interception

import android.app.AppOpsManager
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

    /**
     * Returns true if the app has been granted PACKAGE_USAGE_STATS permission.
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
        val granted = mode == AppOpsManager.MODE_ALLOWED
        Timber.d("UsageStats permission granted: $granted")
        return granted
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
