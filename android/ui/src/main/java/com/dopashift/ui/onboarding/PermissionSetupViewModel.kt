package com.dopashift.ui.onboarding

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * UI state for the permission setup page within onboarding.
 */
data class PermissionSetupUiState(
    val hasUsageStatsPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
) {
    val allGranted: Boolean get() = hasUsageStatsPermission && hasOverlayPermission
}

/**
 * ViewModel for the permission setup page in onboarding.
 *
 * Checks the current status of PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW
 * and provides intents to navigate the user to the relevant Settings screens.
 *
 * Implements Requirement 2 AC1: manual Settings grant flow for both permissions.
 */
@HiltViewModel
class PermissionSetupViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionSetupUiState())
    val uiState: StateFlow<PermissionSetupUiState> = _uiState.asStateFlow()

    init {
        refreshPermissionStatus()
    }

    /**
     * Re-checks permission status. Call this when the user returns from Settings.
     */
    fun refreshPermissionStatus() {
        _uiState.update {
            it.copy(
                hasUsageStatsPermission = checkUsageStatsPermission(),
                hasOverlayPermission = checkOverlayPermission(),
            )
        }
    }

    /**
     * Creates the intent to open the Usage Access settings screen.
     */
    fun createUsageStatsSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Creates the intent to open the Overlay permission settings screen for this app.
     */
    fun createOverlaySettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${appContext.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Checks PACKAGE_USAGE_STATS grant. The AppOps mode is primary, but several OEM ROMs
     * (notably Vivo Funtouch/OriginOS, Xiaomi MIUI, Oppo ColorOS) return
     * [AppOpsManager.MODE_DEFAULT] even when Usage Access is toggled ON — which would falsely show
     * this permission as ungranted on the onboarding page. In that ambiguous case we probe
     * [UsageStatsManager] directly: if it hands back data, access is effectively granted.
     *
     * Kept in sync with `InterceptionPermissionHelper.hasUsageStatsPermission`; the two live in
     * separate modules (ui cannot depend on interception, which depends on ui) so the logic is
     * intentionally duplicated.
     */
    private fun checkUsageStatsPermission(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT -> canQueryUsageStats()
            else -> false
        }
    }

    /**
     * Disambiguates [AppOpsManager.MODE_DEFAULT] by querying the last hour of usage stats.
     * A non-empty result (and no [SecurityException]) means Usage Access is granted.
     */
    private fun canQueryUsageStats(): Boolean {
        return try {
            val usageStatsManager =
                appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - ONE_HOUR_MS,
                now
            )
            !stats.isNullOrEmpty()
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(appContext)
    }

    private companion object {
        private const val ONE_HOUR_MS = 60L * 60L * 1000L
    }
}
