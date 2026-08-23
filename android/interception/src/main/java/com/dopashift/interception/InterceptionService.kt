package com.dopashift.interception

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Persistent foreground service for screen-time interception.
 *
 * Polls [UsageStatsManager] at a configurable interval (3-30s, default 5s)
 * to detect the current foreground app, checks if interception rules apply,
 * and triggers overlays when daily allowances are depleted.
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 16.1
 * - Batched/interval-based polling, NOT a tight loop or Thread.sleep.
 * - Uses coroutines with delay() for scheduling.
 * - Runs as a persistent foreground service with a notification.
 * - Detects Android Go edition and refuses to start on low-RAM devices.
 * - Requires PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW permissions.
 */
@AndroidEntryPoint
class InterceptionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "interception_service_channel"
        private const val CHANNEL_NAME = "Screen-Time Monitoring"

        private const val EXTRA_POLL_INTERVAL_MS = "extra_poll_interval_ms"

        /**
         * Creates an intent to start the interception service with optional configuration.
         */
        fun createStartIntent(
            context: Context,
            pollIntervalMs: Long = InterceptionConfig.DEFAULT_POLL_INTERVAL_MS
        ): Intent {
            return Intent(context, InterceptionService::class.java).apply {
                putExtra(EXTRA_POLL_INTERVAL_MS, pollIntervalMs)
            }
        }

        /**
         * Creates an intent to stop the interception service.
         */
        fun createStopIntent(context: Context): Intent {
            return Intent(context, InterceptionService::class.java)
        }
    }

    @Inject
    lateinit var permissionHelper: InterceptionPermissionHelper

    @Inject
    lateinit var deviceCapabilityChecker: DeviceCapabilityChecker

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var config = InterceptionConfig()

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("InterceptionService created")
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("InterceptionService onStartCommand")

        // Check device capability (Android Go detection)
        val deviceSupport = deviceCapabilityChecker.checkInterceptionSupport(this)
        if (deviceSupport is DeviceCapabilityChecker.DeviceSupport.Unsupported) {
            Timber.w("Interception unavailable: ${deviceSupport.reason}")
            stopSelf()
            return START_NOT_STICKY
        }

        // Check required permissions
        if (!permissionHelper.hasAllRequiredPermissions(this)) {
            Timber.w("Required permissions not granted, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        // Parse config from intent
        val pollIntervalMs = intent?.getLongExtra(
            EXTRA_POLL_INTERVAL_MS,
            InterceptionConfig.DEFAULT_POLL_INTERVAL_MS
        ) ?: InterceptionConfig.DEFAULT_POLL_INTERVAL_MS

        config = InterceptionConfig(
            pollIntervalMs = pollIntervalMs.coerceIn(
                InterceptionConfig.MIN_POLL_INTERVAL_MS,
                InterceptionConfig.MAX_POLL_INTERVAL_MS
            )
        )

        // Start as a foreground service with persistent notification
        startForegroundWithNotification()

        // Start the polling loop
        startPolling()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("InterceptionService destroyed")
        stopPolling()
        serviceScope.cancel()
    }

    /**
     * Promotes this service to foreground with a persistent notification.
     */
    private fun startForegroundWithNotification() {
        val notification = buildForegroundNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ requires specifying foreground service type
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Timber.d("Foreground service started with notification")
    }

    /**
     * Builds the persistent notification for the foreground service.
     */
    private fun buildForegroundNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DopaShift Active")
            .setContentText("Monitoring screen time")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    /**
     * Creates the notification channel for the foreground service.
     * Required for Android O+.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification for screen-time monitoring"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Starts the coroutine-based polling loop.
     * Uses delay() for interval-based scheduling — not Thread.sleep or tight loop.
     *
     * Validates: Requirement 16.1 — batched/interval-based polling.
     */
    private fun startPolling() {
        // Cancel any existing polling job before starting a new one
        pollingJob?.cancel()

        pollingJob = serviceScope.launch {
            Timber.d("Polling started with interval: ${config.pollIntervalMs}ms")

            while (isActive) {
                try {
                    pollForegroundApp()
                } catch (e: SecurityException) {
                    Timber.e(e, "Permission revoked during polling")
                    // Permission was revoked — stop the service
                    stopSelf()
                    break
                } catch (e: Exception) {
                    Timber.e(e, "Error during foreground app poll")
                }

                // Batched interval delay — NOT a tight loop
                delay(config.pollIntervalMs)
            }
        }
    }

    /**
     * Stops the polling loop.
     */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        Timber.d("Polling stopped")
    }

    /**
     * Queries UsageStatsManager for the current foreground app.
     * Detects the package name and checks against interception rules.
     */
    private fun pollForegroundApp() {
        val endTime = System.currentTimeMillis()
        // Query a window slightly larger than the poll interval to ensure coverage
        val beginTime = endTime - (config.pollIntervalMs * 2)

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            beginTime,
            endTime
        )

        if (usageStats.isNullOrEmpty()) {
            Timber.v("No usage stats available for current window")
            return
        }

        // Find the most recently used app (highest lastTimeUsed timestamp)
        val currentApp = usageStats
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }

        if (currentApp != null) {
            val packageName = currentApp.packageName
            Timber.v("Current foreground app: $packageName")
            handleForegroundApp(packageName)
        }
    }

    /**
     * Handles detection of a foreground app. Checks if interception rules
     * apply and accumulates usage time.
     *
     * Future tasks will flesh this out with rule checking and overlay triggering.
     */
    private fun handleForegroundApp(packageName: String) {
        // Skip our own package
        if (packageName == this.packageName) return

        // TODO (Task 13.2+): Check interception rules from local DB
        // TODO (Task 13.3+): Accumulate elapsed time against daily allowance
        // TODO (Task 13.4+): Trigger overlay when allowance depleted
        Timber.v("Checking interception rules for: $packageName")
    }
}
