package com.dopashift.interception

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.dopashift.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects permission revocation for overlay and usage-stats permissions.
 *
 * Requirements 2.6, 2.7:
 * - Detect revocation within 5 seconds.
 * - Notify the user if interception is disabled due to missing permissions.
 * - Handle Android 15+ visible-overlay-before-foreground-service requirement.
 *
 * This component attaches as a lifecycle observer to the Activity. When the app
 * comes to the foreground, it checks permissions immediately and starts periodic
 * monitoring. If a permission is revoked, it fires a notification and optionally
 * invokes a callback for the UI to show an in-app warning.
 */
@Singleton
class PermissionRevocationDetector @Inject constructor(
    private val permissionHelper: InterceptionPermissionHelper
) : DefaultLifecycleObserver {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "permission_revocation_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Permission Alerts"
        private const val NOTIFICATION_ID = 2001

        /** Check permissions every 5 seconds per Requirement 2.6. */
        private const val CHECK_INTERVAL_MS = 5_000L
    }

    /**
     * Callback interface for permission revocation events.
     */
    fun interface OnPermissionRevokedListener {
        /**
         * Called when one or more required permissions have been revoked.
         *
         * @param status Current permission status indicating which permissions are missing.
         */
        fun onRevoked(status: InterceptionPermissionHelper.PermissionStatus)
    }

    private var listener: OnPermissionRevokedListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitoringJob: Job? = null

    /**
     * Last known permission status — used to detect transitions from granted → revoked.
     */
    @Volatile
    private var lastKnownStatus: InterceptionPermissionHelper.PermissionStatus? = null

    /**
     * Registers a listener for permission revocation events.
     */
    fun setOnPermissionRevokedListener(listener: OnPermissionRevokedListener?) {
        this.listener = listener
    }

    /**
     * Lifecycle callback: on app resume, immediately check permissions and start monitoring.
     */
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        startMonitoring(owner as? android.app.Activity ?: return)
    }

    /**
     * Lifecycle callback: on app pause, stop periodic monitoring (no need to poll in background).
     */
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        stopMonitoring()
    }

    /**
     * Starts periodic permission checking. Runs a coroutine that checks every 5 seconds.
     */
    private fun startMonitoring(context: Context) {
        // Cancel any existing monitoring
        monitoringJob?.cancel()

        monitoringJob = scope.launch {
            while (isActive) {
                checkPermissions(context)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Stops the periodic monitoring coroutine.
     */
    private fun stopMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }

    /**
     * Performs a single permission check. If permissions were previously granted
     * but are now revoked, fires the notification and callback.
     */
    private fun checkPermissions(context: Context) {
        val currentStatus = permissionHelper.checkPermissionStatus(context)

        val previousStatus = lastKnownStatus
        lastKnownStatus = currentStatus

        // Only trigger on transition: was granted → now revoked
        if (previousStatus != null && previousStatus.allGranted && !currentStatus.allGranted) {
            Timber.w("Permission revocation detected! Usage: ${currentStatus.hasUsageStats}, Overlay: ${currentStatus.hasOverlay}")
            handleRevocation(context, currentStatus)
        } else if (!currentStatus.allGranted && previousStatus == null) {
            // First check and already revoked — inform the user
            Timber.w("Permissions missing on first check: Usage: ${currentStatus.hasUsageStats}, Overlay: ${currentStatus.hasOverlay}")
            handleRevocation(context, currentStatus)
        }
    }

    /**
     * Handles permission revocation: notifies user and fires callback.
     */
    private fun handleRevocation(
        context: Context,
        status: InterceptionPermissionHelper.PermissionStatus
    ) {
        // Fire listener callback for in-app UI warning
        listener?.onRevoked(status)

        // Show a system notification
        showRevocationNotification(context, status)
    }

    /**
     * Displays a notification informing the user that interception is disabled
     * due to missing permissions.
     */
    private fun showRevocationNotification(
        context: Context,
        status: InterceptionPermissionHelper.PermissionStatus
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel (idempotent)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when DopaShift permissions are revoked"
        }
        notificationManager.createNotificationChannel(channel)

        val missingPermissions = buildString {
            if (!status.hasUsageStats) append(context.getString(R.string.permission_revocation_usage_access))
            if (!status.hasUsageStats && !status.hasOverlay) append(", ")
            if (!status.hasOverlay) append(context.getString(R.string.permission_revocation_overlay))
        }

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.permission_revocation_notification_title))
            .setContentText(
                context.getString(R.string.permission_revocation_notification_text, missingPermissions)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SYSTEM)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Timber.i("Permission revocation notification shown: $missingPermissions")
    }

    /**
     * Checks for the Android 15+ requirement that overlay must be visible before
     * starting a foreground service. Returns true if the platform requires this.
     *
     * On Android 15+, the app must display an overlay window BEFORE calling
     * Service.startForeground(). This method helps callers sequence the operations correctly.
     */
    fun requiresVisibleOverlayBeforeForegroundService(): Boolean {
        return Build.VERSION.SDK_INT >= 35 // Android 15 = API 35
    }

    /**
     * Performs an immediate one-shot permission check.
     * Returns the current status without triggering notifications (useful for startup checks).
     */
    fun checkImmediate(context: Context): InterceptionPermissionHelper.PermissionStatus {
        val status = permissionHelper.checkPermissionStatus(context)
        lastKnownStatus = status
        return status
    }
}
