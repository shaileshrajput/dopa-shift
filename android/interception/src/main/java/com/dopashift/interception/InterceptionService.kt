package com.dopashift.interception

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import com.dopashift.domain.entity.InterceptActionAudit
import com.dopashift.domain.repository.InterceptActionAuditRepository
import com.dopashift.interception.overlay.InterceptAction
import com.dopashift.interception.overlay.OverlayService
import com.dopashift.interception.reminder.InterceptionTodoReminderNotifier
import com.dopashift.interception.reminder.LimitReachedNotifier
import com.dopashift.ui.R
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
import java.time.Clock
import java.util.Collections
import java.util.UUID
import javax.inject.Inject

/**
 * Persistent foreground service for screen-time interception.
 *
 * Polls [UsageStatsManager] at a configurable interval (3-30s, default 5s)
 * to detect the current foreground app, checks against interception rules via
 * [AllowanceTracker], and triggers overlays when daily allowances are depleted.
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 2.4, 2.6, 2.7, 2.10, 2.12, 16.1
 * - Batched/interval-based polling, NOT a tight loop or Thread.sleep.
 * - Uses coroutines with delay() for scheduling.
 * - Runs as a persistent foreground service with a notification.
 * - Detects Android Go edition and refuses to start on low-RAM devices.
 * - Requires PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW permissions.
 * - Triggers overlay within 2 seconds of depletion detection.
 * - Fires todo reminder notification on depletion (Requirement 2.10).
 *
 * On depletion (a limit breach) the service routes presentation through
 * [DeferredInterceptCoordinator] (Suppression_Context / active Focus_Extension checks —
 * Requirements 4.11, 5.2–5.6) and posts the limit-reached notification via [LimitReachedNotifier]
 * (Requirements 4.4, 4.5). Deferred breaches are re-evaluated each poll cycle until presented or
 * discarded. The [FocusExtensionManager] reference is held so an active extension suppresses
 * re-triggering (via the coordinator) and so task 12.1 can wire the overlay engagement result back
 * through [onOverlayEngagementResult].
 */
@AndroidEntryPoint
class InterceptionService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "interception_service_channel"
        private const val CHANNEL_NAME = "Screen-Time Monitoring"

        private const val EXTENSION_CAP_NOTIFICATION_ID = 1002
        private const val EXTENSION_CAP_CHANNEL_ID = "interception_extension_cap_channel"
        private const val EXTENSION_CAP_CHANNEL_NAME = "Focus Extension Limit"

        private const val PERMISSION_NOTIFICATION_ID = 1003
        private const val PERMISSION_CHANNEL_ID = "interception_permission_channel"
        private const val PERMISSION_CHANNEL_NAME = "Interception Permissions"

        /**
         * Upper bound on the real elapsed time credited to the foreground app in a single poll.
         * Caps a long gap (device doze, process death, monotonic clock jump) so accumulation cannot
         * jump by minutes at once; steady polling stays well under this. Set to the max poll
         * interval so a normal delayed poll is fully credited but an abnormal gap is not.
         */
        private const val MAX_ELAPSED_CREDIT_MS = InterceptionConfig.MAX_POLL_INTERVAL_MS

        /**
         * How many poll intervals of usage events to look back when resolving the current
         * foreground app, so a foreground transition that occurred between polls is still captured.
         */
        private const val FOREGROUND_EVENT_LOOKBACK_MULTIPLIER = 4L

        private const val EXTRA_POLL_INTERVAL_MS = "extra_poll_interval_ms"
        private const val EXTRA_USER_ID = "extra_user_id"

        /**
         * Intent action carrying the overlay engagement result back from [OverlayService]
         * (task 12.1). Handled in [onStartCommand] and routed to [onOverlayEngagementResult] so the
         * Engine can grant/deny a Focus_Extension without the overlay holding a direct reference to
         * this service.
         */
        const val ACTION_OVERLAY_ENGAGEMENT = "com.dopashift.interception.action.OVERLAY_ENGAGEMENT"
        private const val EXTRA_ENGAGEMENT_PACKAGE = "extra_engagement_package"
        private const val EXTRA_ENGAGEMENT_ENGAGED = "extra_engagement_engaged"

        /**
         * The primary Intercept_Screen action the user selected, carried as the [InterceptAction]
         * enum name (`CONTINUE_TO_APP` / `SWITCH_TO_DOPASHIFT`) on the engagement result intent
         * (task 7.2). Absent/blank when the overlay was dismissed without an explicit primary
         * action (e.g. timed-out dismissal), in which case no Continue/Switch routing or audit
         * append is performed.
         */
        private const val EXTRA_OVERLAY_ACTION = "extra_overlay_action"

        /**
         * Creates an intent to start the interception service with optional configuration.
         */
        fun createStartIntent(
            context: Context,
            userId: UUID,
            pollIntervalMs: Long = InterceptionConfig.DEFAULT_POLL_INTERVAL_MS
        ): Intent {
            return Intent(context, InterceptionService::class.java).apply {
                putExtra(EXTRA_POLL_INTERVAL_MS, pollIntervalMs)
                putExtra(EXTRA_USER_ID, userId.toString())
            }
        }

        /**
         * Creates an intent to stop the interception service.
         */
        fun createStopIntent(context: Context): Intent {
            return Intent(context, InterceptionService::class.java)
        }

        /**
         * Creates an intent that reports an overlay engagement result to the (already running)
         * [InterceptionService]. Sent by [com.dopashift.interception.overlay.OverlayService] when
         * the Intercept_Screen is dismissed.
         *
         * @param packageName the Monitored_App the Intercept_Screen was shown for.
         * @param engaged whether the user recorded at least one action (checked a habit / completed
         *   a to-do) — drives [FocusExtensionManager.grantIfEngaged] (Requirement 4.9).
         * @param selectedAction the primary Intercept_Screen action the user tapped
         *   ("Continue to app" / "Switch to DopaShift"), or null when the overlay was dismissed
         *   without one. Drives the Continue/Switch routing and the local-only action audit
         *   (Requirements 3.2, 3.3, 3.4).
         */
        fun createEngagementResultIntent(
            context: Context,
            packageName: String,
            engaged: Boolean,
            selectedAction: InterceptAction? = null
        ): Intent {
            return Intent(context, InterceptionService::class.java).apply {
                action = ACTION_OVERLAY_ENGAGEMENT
                putExtra(EXTRA_ENGAGEMENT_PACKAGE, packageName)
                putExtra(EXTRA_ENGAGEMENT_ENGAGED, engaged)
                selectedAction?.let { putExtra(EXTRA_OVERLAY_ACTION, it.name) }
            }
        }
    }

    @Inject
    lateinit var permissionHelper: InterceptionPermissionHelper

    @Inject
    lateinit var deviceCapabilityChecker: DeviceCapabilityChecker

    @Inject
    lateinit var allowanceTracker: AllowanceTracker

    /**
     * Owns per-cycle `Session_Usage` accumulation and the `PAUSED_FOR_OVERLAY` state for
     * `Repetitive` Rules (Requirements 2.2, 2.3, 2.5, 2.6, 2.7). The service routes each foreground
     * sample of a `Repetitive`-governed app through [RepetitiveSessionTracker.onForeground] and, on
     * [SessionResult.IntervalBreached], presents the overlay through the same
     * [deferredInterceptCoordinator] used by the `Once` path, then freezes accumulation via
     * [RepetitiveSessionTracker.markPausedForOverlay]. `Once` Rules bypass this and keep flowing
     * through [allowanceTracker] unchanged.
     */
    @Inject
    lateinit var repetitiveSessionTracker: RepetitiveSessionTracker

    @Inject
    lateinit var todoReminderNotifier: InterceptionTodoReminderNotifier

    /**
     * Owns the Focus_Extension grace/cap state machine (Requirements 4.9–4.13). The service holds
     * this reference so that (a) a currently-active extension suppresses re-triggering the overlay
     * (via [deferredInterceptCoordinator], which consults it internally — Requirement 4.11) and
     * (b) task 12.1 can wire the overlay engagement result back into [grantIfEngaged] through the
     * [onOverlayEngagementResult] seam.
     */
    @Inject
    lateinit var focusExtensionManager: FocusExtensionManager

    /**
     * Decides whether a breach's Intercept_Screen is presented now, deferred, or discarded when a
     * Suppression_Context is active or a Focus_Extension is suppressing re-triggering
     * (Requirements 4.11, 5.2–5.6).
     */
    @Inject
    lateinit var deferredInterceptCoordinator: DeferredInterceptCoordinator

    /**
     * Posts the limit-reached notification with a single "open Intercept_Screen" action at breach
     * time (Requirements 4.4, 4.5).
     */
    @Inject
    lateinit var limitReachedNotifier: LimitReachedNotifier

    /**
     * Local-only audit log for executed Intercept_Screen actions (Requirements 3.4, 3.5). On each
     * Continue/Switch action the service appends exactly one [InterceptActionAudit] entry scoped to
     * the authenticated [userId], timestamped from the injectable [clock]. The append is on-device
     * only — never transmitted — and a write failure never blocks the overlay action UX (the append
     * is best-effort and logged, not surfaced).
     */
    @Inject
    lateinit var interceptActionAuditRepository: InterceptActionAuditRepository

    /**
     * Injectable clock shared across the interception subsystem; supplies the instant used when
     * evaluating deferrals so the timing is consistent with [DeferredInterceptCoordinator] and
     * [FocusExtensionManager].
     */
    @Inject
    lateinit var clock: Clock

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null
    private var config = InterceptionConfig()
    private var userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    /**
     * Tracks whether the overlay is currently being shown to avoid duplicate triggers.
     */
    @Volatile
    private var isOverlayActive: Boolean = false

    /**
     * Packages whose breach has been deferred and are awaiting a [DeferredInterceptCoordinator]
     * decision on subsequent poll cycles (Requirement 5.4). The poll loop re-evaluates each entry
     * every cycle and removes it when the coordinator presents or discards it.
     */
    private val pendingDeferrals: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Subset of [pendingDeferrals] whose breach originated from a `Repetitive` Rule
     * ([SessionResult.IntervalBreached]) rather than the `Once` daily-allowance path. Membership
     * lets [evaluatePendingDeferrals] apply the repetitive-specific side effects when the
     * coordinator resolves the deferral: on [Decision.Present] transition the Rule to
     * `PAUSED_FOR_OVERLAY` via [RepetitiveSessionTracker.markPausedForOverlay] (Requirement 2.5);
     * on [Decision.Discard] reset `Session_Usage` (foreground loss / 300s window elapsed, OQ-2).
     * `Once` deferrals are absent here and keep their existing behavior.
     */
    private val repetitiveDeferrals: MutableSet<String> =
        Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * The package detected as foreground on the most recent poll. Used to tell the coordinator
     * whether a deferred Monitored_App is still foreground this cycle (Requirement 5.5).
     */
    @Volatile
    private var currentForegroundPackage: String? = null

    /**
     * Wall-clock instant (epoch millis) of the previous poll, used to credit the *actual* elapsed
     * time to the foreground app rather than a fixed poll-interval constant. A poll that is delayed
     * (device doze, GC, CPU throttling) or that follows a skipped cycle otherwise loses that real
     * time, causing tracked totals to lag far behind real usage — the reason a limit could be
     * exceeded in wall-clock terms without the overlay ever triggering. Null until the first poll.
     */
    @Volatile
    private var lastPollElapsedRealtimeMs: Long = 0L

    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var notificationManager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Timber.d("InterceptionService created")
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // Register the depletion listener to trigger overlay + notification
        allowanceTracker.setOnAllowanceDepletedListener { packageName, _ ->
            onAllowanceDepleted(packageName)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("InterceptionService onStartCommand")

        // Overlay engagement result signal (task 12.1). Handled without restarting polling or the
        // foreground promotion — the service is already running when the overlay dismisses.
        if (intent?.action == ACTION_OVERLAY_ENGAGEMENT) {
            val pkg = intent.getStringExtra(EXTRA_ENGAGEMENT_PACKAGE).orEmpty()
            val engaged = intent.getBooleanExtra(EXTRA_ENGAGEMENT_ENGAGED, false)
            onOverlayEngagementResult(pkg, engaged)

            // Route the explicit primary action the user chose on the Intercept_Screen, if any
            // (task 7.2). Parse the enum name defensively so an unknown/blank value falls back to
            // the generic dismissal path rather than crashing.
            val selectedAction = intent.getStringExtra(EXTRA_OVERLAY_ACTION)
                ?.let { name -> runCatching { InterceptAction.valueOf(name) }.getOrNull() }

            when (selectedAction) {
                InterceptAction.CONTINUE_TO_APP -> onContinueToApp(pkg)
                InterceptAction.SWITCH_TO_DOPASHIFT -> onSwitchToDopaShift(pkg)
                null -> {
                    // No explicit primary action (e.g. dismissed after the engagement cooldown):
                    // fall back to the generic dismissal that resumes + resets a paused
                    // `Repetitive` cycle (Requirement 2.7) without appending an action audit.
                    onOverlayDismissed(pkg)
                }
            }
            return START_STICKY
        }

        // Check device capability (Android Go detection)
        val deviceSupport = deviceCapabilityChecker.checkInterceptionSupport(this)
        if (deviceSupport is DeviceCapabilityChecker.DeviceSupport.Unsupported) {
            Timber.w("Interception unavailable: ${deviceSupport.reason}")
            stopSelf()
            return START_NOT_STICKY
        }

        // Check required permissions. Instead of silently disappearing, surface a
        // user-visible notification explaining which special-access permission is missing
        // so the user can grant it and re-enable monitoring (Requirement 2.6).
        if (!permissionHelper.hasAllRequiredPermissions(this)) {
            val status = permissionHelper.checkPermissionStatus(this)
            Timber.w(
                "Required permissions not granted (usageStats=%s, overlay=%s), stopping service",
                status.hasUsageStats, status.hasOverlay
            )
            notifyPermissionsMissing(status)
            stopSelf()
            return START_NOT_STICKY
        }

        // Parse user ID from intent
        val userIdStr = intent?.getStringExtra(EXTRA_USER_ID)
        if (userIdStr != null) {
            userId = UUID.fromString(userIdStr)
        }

        // Scope every Rule lookup performed by the trackers to the authenticated user
        // (Requirement 3.8). Both trackers consult the domain repository port by userId.
        allowanceTracker.userId = userId
        repetitiveSessionTracker.userId = userId

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
        allowanceTracker.setOnAllowanceDepletedListener(null)
        serviceScope.cancel()
    }

    /**
     * Promotes this service to foreground with a persistent notification.
     * On Android 15+ (API 35), ensures a visible overlay exists before starting
     * the foreground service per platform-enforced ordering (Requirement 2.7).
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

        // Reset the elapsed-time baseline so the first poll of this loop credits a single interval
        // rather than a stale gap carried over from a previous polling session.
        lastPollElapsedRealtimeMs = 0L

        pollingJob = serviceScope.launch {
            Timber.d("Polling started with interval: ${config.pollIntervalMs}ms")

            while (isActive) {
                try {
                    pollForegroundApp()
                } catch (e: SecurityException) {
                    Timber.e(e, "Permission revoked during polling")
                    // Permission was revoked — stop the service (Requirement 2.6)
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
     * Queries UsageStatsManager for the current foreground app and delegates
     * to [handleForegroundApp] for allowance tracking.
     *
     * This is a suspend function because [AllowanceTracker.onForegroundDetected]
     * performs Room DB queries.
     */
    private suspend fun pollForegroundApp() {
        // Verify overlay permission hasn't been revoked (Requirement 2.6)
        if (!Settings.canDrawOverlays(this)) {
            Timber.w("Overlay permission revoked — stopping interception service")
            stopSelf()
            return
        }

        // Real elapsed time since the previous poll (Requirement 2.3 / accurate accumulation).
        // Using the monotonic elapsedRealtime clock (not wall-clock) so a system time change or a
        // delayed/skipped poll credits the true elapsed foreground time to the app, capped so a
        // long doze/sleep gap does not over-credit in a single burst.
        val nowRealtime = android.os.SystemClock.elapsedRealtime()
        val elapsedSeconds = computeElapsedSeconds(nowRealtime)
        lastPollElapsedRealtimeMs = nowRealtime

        val currentApp = detectForegroundPackage()
        currentForegroundPackage = currentApp

        if (currentApp != null) {
            Timber.v("Current foreground app: $currentApp (elapsed=${elapsedSeconds}s)")
            handleForegroundApp(currentApp, elapsedSeconds)
        }

        // Re-check any deferred breaches this cycle (Requirements 5.4, 5.5, 5.6).
        evaluatePendingDeferrals()
    }

    /**
     * Computes the real seconds elapsed since the previous poll from the monotonic
     * [android.os.SystemClock.elapsedRealtime] clock. The first poll (no prior baseline) credits a
     * single poll interval so accumulation starts immediately. Subsequent polls credit the true
     * delta, capped at [MAX_ELAPSED_CREDIT_MS] so a long gap (doze, process death, clock jump) does
     * not over-credit usage in one cycle.
     */
    private fun computeElapsedSeconds(nowRealtime: Long): Long {
        val previous = lastPollElapsedRealtimeMs
        if (previous <= 0L) {
            // First poll this service lifetime — credit exactly one interval.
            return (config.pollIntervalMs / 1000L).coerceAtLeast(1L)
        }
        val deltaMs = (nowRealtime - previous).coerceIn(0L, MAX_ELAPSED_CREDIT_MS)
        return deltaMs / 1000L
    }

    /**
     * Resolves the current foreground app package using [UsageStatsManager.queryEvents], reading
     * the most recent foreground transition (`MOVE_TO_FOREGROUND`, or `ACTIVITY_RESUMED` on API
     * 29+). This is far more reliable than aggregating [UsageStatsManager.queryUsageStats] buckets
     * and picking the max `lastTimeUsed`, which frequently returns a recently-used-but-not-current
     * app and silently drops genuine foreground time. Returns null when no transition is found in
     * the window (foreground app unchanged and outside the window, or usage access unavailable).
     */
    @Suppress("DEPRECATION") // MOVE_TO_FOREGROUND is the correct signal on API < 29 (minSdk 26).
    private fun detectForegroundPackage(): String? {
        val endTime = System.currentTimeMillis()
        // Look back a few intervals so a foreground transition that happened between polls is still
        // captured even if the app has been steady since.
        val beginTime = endTime - (config.pollIntervalMs * FOREGROUND_EVENT_LOOKBACK_MULTIPLIER)

        val events = usageStatsManager.queryEvents(beginTime, endTime)
        if (events == null) {
            Timber.v("No usage events available for current window")
            return currentForegroundPackage
        }

        val event = android.app.usage.UsageEvents.Event()
        var latestForegroundPackage: String? = null
        var latestTimestamp = Long.MIN_VALUE

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForegroundTransition = event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED)
            if (isForegroundTransition && event.timeStamp >= latestTimestamp) {
                latestTimestamp = event.timeStamp
                latestForegroundPackage = event.packageName
            }
        }

        // No transition in this window means the foreground app has not changed — keep the last
        // known package so steady, uninterrupted usage keeps accumulating.
        return latestForegroundPackage ?: currentForegroundPackage
    }

    /**
     * Re-evaluates every pending deferred breach on the current monitoring cycle by consulting
     * [DeferredInterceptCoordinator.evaluate]. On [Decision.Present] the overlay is shown (within
     * this cycle, Requirement 5.4); on [Decision.Discard] the deferral is dropped (app left the
     * foreground per Requirement 5.5, or the 300s window elapsed per Requirement 5.6); on
     * [Decision.Hold] the deferral is retained for the next cycle.
     *
     * The coordinator internally clears its own record on Present/Discard, so this only maintains
     * the service-side [pendingDeferrals] tracking set.
     */
    private fun evaluatePendingDeferrals() {
        if (pendingDeferrals.isEmpty()) return

        val now = clock.instant()
        val foreground = currentForegroundPackage
        // Snapshot to avoid concurrent-modification while mutating the synchronized set.
        val snapshot = synchronized(pendingDeferrals) { pendingDeferrals.toList() }

        for (pkg in snapshot) {
            val isAppForeground = pkg == foreground
            when (deferredInterceptCoordinator.evaluate(pkg, isAppForeground, now)) {
                Decision.Present -> {
                    pendingDeferrals.remove(pkg)
                    Timber.d("Deferred breach cleared for $pkg — presenting overlay")
                    presentOverlay(pkg)
                    // For a `Repetitive` deferral, freeze the per-cycle timer now that the overlay
                    // is presented (Requirements 2.5, 2.6).
                    if (repetitiveDeferrals.remove(pkg)) {
                        repetitiveSessionTracker.markPausedForOverlay(pkg)
                    }
                }
                Decision.Discard -> {
                    pendingDeferrals.remove(pkg)
                    Timber.d("Deferred breach discarded for $pkg")
                    // A discarded `Repetitive` deferral means the app left the foreground or the
                    // 300s window elapsed — reset the cycle so a stale count cannot immediately
                    // re-trigger on return (OQ-2, consistent with Requirement 2.7).
                    if (repetitiveDeferrals.remove(pkg)) {
                        repetitiveSessionTracker.reset(pkg)
                    }
                }
                Decision.Hold -> {
                    // Keep deferring; re-evaluate on the next cycle.
                    Timber.v("Deferred breach still held for $pkg")
                }
            }
        }
    }

    /**
     * Handles detection of a foreground app. Accumulates time via [AllowanceTracker]
     * and triggers overlay when allowance is depleted.
     *
     * @param elapsedSeconds the real seconds elapsed since the previous poll (from the monotonic
     *   clock), so accumulation reflects actual foreground time rather than a fixed poll constant.
     *
     * Requirements: 2.3, 2.4, 2.12
     * - Samples foreground status at the poll interval.
     * - Accumulates time against user-configured daily allowance.
     * - Triggers overlay within 2 seconds of depletion detection (poll interval guarantees this).
     * - Midnight resets handled internally by AllowanceTracker.
     */
    private suspend fun handleForegroundApp(packageName: String, elapsedSeconds: Long) {
        // Skip our own package — never intercept ourselves
        if (packageName == this.packageName) return

        // Route `Repetitive` Rules through the per-cycle session tracker first (Requirements 2.2,
        // 2.3, 2.5). A `NotRepetitive` result means the package has no active Rule or an active
        // `Once` Rule, so it falls through to the unchanged `AllowanceTracker` daily-allowance path.
        when (repetitiveSessionTracker.onForeground(packageName, elapsedSeconds)) {
            SessionResult.IntervalBreached -> {
                // The per-interval usage reached the Repetitive_Interval — present within one
                // sampling interval, routing through the same Suppression_Context deferral used by
                // the `Once` path (Requirements 2.3, 2.4). Do NOT fall through to `AllowanceTracker`.
                Timber.d("Repetitive interval breached for: $packageName")
                onRepetitiveIntervalBreached(packageName)
                return
            }
            SessionResult.Accumulating -> {
                Timber.v("Repetitive Session_Usage accumulating for: $packageName")
                return
            }
            SessionResult.Paused -> {
                // Overlay is showing for this app — accumulation frozen (Requirements 2.5, 2.6).
                Timber.v("Repetitive Session_Usage paused (overlay active) for: $packageName")
                return
            }
            SessionResult.NotRepetitive -> {
                // Not a `Repetitive` Rule — fall through to the existing `Once` allowance path.
            }
        }

        val status = allowanceTracker.onForegroundDetected(packageName, elapsedSeconds)

        when (status) {
            AllowanceStatus.DEPLETED -> {
                // Depletion callback handles overlay trigger via the listener
                Timber.d("Allowance depleted for: $packageName")
            }
            AllowanceStatus.WITHIN_ALLOWANCE -> {
                Timber.v("Within allowance for: $packageName")
            }
            AllowanceStatus.NOT_TRACKED -> {
                Timber.v("App not tracked: $packageName")
            }
        }
    }

    /**
     * Handles a `Repetitive` Rule whose `Session_Usage` reached its Repetitive_Interval
     * ([SessionResult.IntervalBreached]).
     *
     * Routing mirrors the `Once` breach path in [onAllowanceDepleted], reusing the existing
     * [DeferredInterceptCoordinator] so the Suppression_Context check and 300s deferral
     * (Requirement 2.4) are not duplicated:
     * - [BreachOutcome.PresentNow] — nothing is suppressing, so present the overlay now and freeze
     *   accumulation via [RepetitiveSessionTracker.markPausedForOverlay] (Requirements 2.3, 2.5).
     * - [BreachOutcome.Deferred] — a Suppression_Context (or Focus_Extension) is active; the
     *   package is tracked in [pendingDeferrals] / [repetitiveDeferrals] and re-evaluated each poll
     *   cycle by [evaluatePendingDeferrals] until presented (then paused) or discarded (then reset,
     *   OQ-2).
     */
    private fun onRepetitiveIntervalBreached(packageName: String) {
        when (deferredInterceptCoordinator.onBreach(packageName)) {
            BreachOutcome.PresentNow -> {
                Timber.i("Repetitive breach for $packageName not suppressed — presenting overlay now")
                presentOverlay(packageName)
                // Freeze the per-cycle timer while the overlay is visible (Requirements 2.5, 2.6).
                repetitiveSessionTracker.markPausedForOverlay(packageName)
            }
            BreachOutcome.Deferred -> {
                Timber.i("Repetitive breach for $packageName suppressed — deferring Intercept_Screen")
                pendingDeferrals.add(packageName)
                repetitiveDeferrals.add(packageName)
            }
        }
    }

    /**
     * Called by the [AllowanceTracker.OnAllowanceDepleted] listener when an app's
     * daily allowance is fully consumed (a limit breach).
     *
     * Routing (design State Machine, Requirements 4.4/4.5/4.11, 5.2–5.6):
     * 1. Fire the limit-reached notification with a single "open Intercept_Screen" action at breach
     *    time — within one sampling interval (Requirements 4.4, 4.5).
     * 2. Route the breach through [DeferredInterceptCoordinator.onBreach]. The coordinator
     *    internally treats an active [FocusExtensionManager] extension (Requirement 4.11) and any
     *    active Suppression_Context (Requirement 5.2) as suppression:
     *    - [BreachOutcome.PresentNow] — nothing is suppressing, so present the overlay now
     *      (Requirement 2.4, within 2s of detection).
     *    - [BreachOutcome.Deferred] — presentation is deferred; the poll loop re-evaluates this
     *      package each cycle via [evaluatePendingDeferrals] until the coordinator presents
     *      (Requirement 5.4) or discards it (Requirements 5.5, 5.6).
     * 3. Fire the existing todo reminder notification simultaneously (Requirement 2.10).
     */
    private fun onAllowanceDepleted(packageName: String) {
        // Fire the todo reminder notification simultaneously with breach detection (Req 2.10).
        fireTodoReminder(packageName)

        // Post the limit-reached notification at breach time (Requirements 4.4, 4.5).
        try {
            limitReachedNotifier.notifyLimitReached(packageName)
        } catch (e: Exception) {
            Timber.e(e, "Failed to post limit-reached notification")
        }

        // Route the breach through the deferral/extension state machine (Req 4.11, 5.2–5.6).
        when (deferredInterceptCoordinator.onBreach(packageName)) {
            BreachOutcome.PresentNow -> {
                Timber.i("Breach for $packageName not suppressed — presenting overlay now")
                presentOverlay(packageName)
            }
            BreachOutcome.Deferred -> {
                // Suppressed (Suppression_Context or active Focus_Extension). Defer and let the
                // poll loop re-evaluate each cycle (Requirement 5.4).
                Timber.i("Breach for $packageName suppressed — deferring Intercept_Screen")
                pendingDeferrals.add(packageName)
            }
        }
    }

    /**
     * Presents the full-screen Intercept_Screen overlay for [packageName] (Requirement 2.4),
     * honoring the [isOverlayActive] dedup so a single overlay is shown at a time. Shared by the
     * immediate-present path and the deferred-present path.
     */
    private fun presentOverlay(packageName: String) {
        if (isOverlayActive) {
            Timber.d("Overlay already active, skipping duplicate trigger for: $packageName")
            return
        }

        Timber.i("Triggering intercept overlay for depleted app: $packageName")
        isOverlayActive = true

        val overlayIntent = OverlayService.createShowIntent(this, packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }
    }

    /**
     * Fires the existing todo reminder notification for the depleted app (Requirement 2.10).
     */
    private fun fireTodoReminder(packageName: String) {
        serviceScope.launch {
            try {
                todoReminderNotifier.notifyPendingTasks(userId, packageName)
            } catch (e: Exception) {
                Timber.e(e, "Failed to fire todo reminder notification")
            }
        }
    }

    /**
     * Seam for task 12.1 — the overlay reports back whether the user engaged (checked a habit or
     * completed a to-do). Task 12.1 will implement the overlay → service → [FocusExtensionManager]
     * plumbing so that [FocusExtensionManager.grantIfEngaged] can decide whether to grant a
     * Focus_Extension grace window (Requirements 4.9, 4.13). This task deliberately does NOT
     * implement the grant here — it only reserves the entry point so 12.1 does not have to touch
     * the breach-routing above.
     *
     * @param packageName the Monitored_App whose Intercept_Screen was engaged with.
     * @param engaged whether the user recorded at least one action on the Intercept_Screen.
     */
    fun onOverlayEngagementResult(packageName: String, engaged: Boolean) {
        if (packageName.isEmpty()) {
            Timber.w("Overlay engagement result received with empty package — ignoring")
            return
        }

        when (val result = focusExtensionManager.grantIfEngaged(packageName, engaged, config)) {
            is GrantResult.Granted -> {
                // A grace window was granted; the coordinator/manager suppress re-triggering while
                // it is active (Requirement 4.11). Nothing further to surface.
                Timber.i(
                    "Focus_Extension granted until %s (%d remaining) after overlay engagement",
                    result.expiresAt, result.remaining
                )
            }
            GrantResult.NoActionRecorded -> {
                // No action was recorded — no extension (Requirement 4.9). The overlay simply
                // dismissed; the next breach will re-present normally.
                Timber.d("No engagement recorded — no Focus_Extension granted")
            }
            GrantResult.CapReached -> {
                // 3/day cap already exhausted — indicate no more extensions today (Requirement 4.13).
                Timber.i("Focus_Extension cap reached — surfacing no-extensions notice")
                notifyNoExtensionsRemaining()
            }
        }
    }

    /**
     * Posts a local notification telling the user that no additional Focus_Extensions are available
     * for the rest of the calendar day (Requirement 4.13). All text is localized (en/hi/mr).
     */
    private fun notifyNoExtensionsRemaining() {
        try {
            val channel = NotificationChannel(
                EXTENSION_CAP_CHANNEL_ID,
                EXTENSION_CAP_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when no more Focus Extensions are available today"
            }
            notificationManager.createNotificationChannel(channel)

            val notification = Notification.Builder(this, EXTENSION_CAP_CHANNEL_ID)
                .setContentTitle(getString(R.string.focus_extension_cap_notification_title))
                .setContentText(getString(R.string.focus_extension_cap_notification_text))
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build()

            notificationManager.notify(EXTENSION_CAP_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to post no-extensions-remaining notification")
        }
    }

    /**
     * Posts a user-visible notification when the service cannot run because a required
     * special-access permission is missing (Requirement 2.6). Previously the service simply
     * called stopSelf() and vanished with no feedback, so a user who never granted (or later
     * revoked) Usage Access / "Display over other apps" saw no overlay and no explanation.
     *
     * The notification's tap action opens the relevant system Settings screen — usage-access
     * takes priority since it is required before any monitoring can occur, otherwise the overlay
     * grant screen. Tapping there lets the user grant the permission; returning to the app then
     * restarts the service via MainActivity.onResume().
     */
    private fun notifyPermissionsMissing(status: InterceptionPermissionHelper.PermissionStatus) {
        try {
            val channel = NotificationChannel(
                PERMISSION_CHANNEL_ID,
                PERMISSION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when screen-time monitoring cannot run due to missing permissions"
            }
            notificationManager.createNotificationChannel(channel)

            // Route the tap to the first missing permission's Settings screen.
            val settingsIntent = if (!status.hasUsageStats) {
                permissionHelper.createUsageStatsSettingsIntent()
            } else {
                permissionHelper.createOverlaySettingsIntent(this)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val contentText = when {
                !status.hasUsageStats && !status.hasOverlay ->
                    getString(R.string.interception_permission_missing_both)
                !status.hasUsageStats ->
                    getString(R.string.interception_permission_missing_usage)
                else ->
                    getString(R.string.interception_permission_missing_overlay)
            }

            val notification = Notification.Builder(this, PERMISSION_CHANNEL_ID)
                .setContentTitle(getString(R.string.interception_permission_missing_title))
                .setContentText(contentText)
                .setStyle(Notification.BigTextStyle().bigText(contentText))
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ERROR)
                .build()

            notificationManager.notify(PERMISSION_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.e(e, "Failed to post permissions-missing notification")
        }
    }

    /**
     * Called when the overlay is dismissed — resets the overlay-active flag so subsequent
     * depletions can re-trigger. For a `Repetitive` Rule paused for the overlay, resumes and resets
     * its per-cycle `Session_Usage` on return to the foreground (Requirement 2.7); harmless no-op
     * for a `Once` app or an app with no tracked repetitive cycle.
     *
     * @param packageName the Monitored_App the dismissed overlay was shown for, or empty/blank when
     *   the caller has no package context (then only the overlay flag is reset).
     */
    fun onOverlayDismissed(packageName: String = "") {
        isOverlayActive = false
        if (packageName.isNotBlank()) {
            repetitiveSessionTracker.onOverlayDismissedAndReturned(packageName)
        }
        Timber.d("Overlay dismissed, ready for next trigger")
    }

    /**
     * Routes a "Continue to app" primary action (Requirement 3.3).
     *
     * Removes the overlay (clears [isOverlayActive]), returns focus to the Monitored_App — the
     * overlay window has already been torn down by [OverlayService], so the previously foreground
     * Monitored_App regains focus with nothing further to launch — resets the Rule's `Session_Usage`
     * to 0 and transitions its Monitoring_State back to `ACTIVE` for the next interval cycle (via
     * [RepetitiveSessionTracker.onOverlayDismissedAndReturned]), and appends exactly one `CONTINUE`
     * audit entry (Requirement 3.4).
     */
    fun onContinueToApp(packageName: String) {
        isOverlayActive = false
        if (packageName.isNotBlank()) {
            // Reset Session_Usage to 0 and return the Rule to ACTIVE (Requirement 3.3). Harmless
            // no-op for a `Once` app or an app with no tracked repetitive cycle.
            repetitiveSessionTracker.onOverlayDismissedAndReturned(packageName)
        }
        appendActionAudit(packageName, InterceptAction.CONTINUE_TO_APP)
        Timber.d("Overlay action: Continue to app for %s", packageName)
    }

    /**
     * Routes a "Switch to DopaShift" primary action (Requirement 3.2).
     *
     * Removes the overlay (clears [isOverlayActive]), launches the DopaShift main activity in the
     * foreground, resets the Rule's `Session_Usage` to 0 and returns its Monitoring_State to
     * `ACTIVE` (leaving it ACTIVE per Requirement 3.2, via
     * [RepetitiveSessionTracker.onOverlayDismissedAndReturned]), and appends exactly one
     * `SWITCH_TO_DOPASHIFT` audit entry (Requirement 3.4).
     */
    fun onSwitchToDopaShift(packageName: String) {
        isOverlayActive = false
        if (packageName.isNotBlank()) {
            repetitiveSessionTracker.onOverlayDismissedAndReturned(packageName)
        }
        launchDopaShiftMainActivity()
        appendActionAudit(packageName, InterceptAction.SWITCH_TO_DOPASHIFT)
        Timber.d("Overlay action: Switch to DopaShift for %s", packageName)
    }

    /**
     * Brings the DopaShift main/launcher activity to the foreground (Requirement 3.2).
     *
     * The interception module cannot reference the `app` module's `MainActivity` directly (the
     * dependency direction is `app -> interception`), so the launcher activity is resolved via the
     * [android.content.pm.PackageManager] launch intent for this app's own package. `FLAG_ACTIVITY_NEW_TASK`
     * is required to start an Activity from a Service context.
     */
    private fun launchDopaShiftMainActivity() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(this.packageName)
            if (launchIntent == null) {
                Timber.w("No launcher activity found for %s — cannot switch to DopaShift", this.packageName)
                return
            }
            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
            startActivity(launchIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to launch DopaShift main activity")
        }
    }

    /**
     * Appends exactly one local-only [InterceptActionAudit] entry for an executed overlay action
     * (Requirement 3.4). The entry is scoped to the authenticated [userId], timestamped from the
     * injectable [clock], and records the target app package plus the action type. The write is
     * best-effort: it runs off the service scope and any failure is logged but NEVER blocks the
     * overlay action UX (Requirement: audit failure must not gate the action). The audit log is
     * on-device only and is never transmitted (Requirement 3.5).
     */
    private fun appendActionAudit(packageName: String, action: InterceptAction) {
        if (packageName.isBlank()) {
            Timber.w("Skipping intercept-action audit — empty package name")
            return
        }
        val audit = InterceptActionAudit(
            id = UUID.randomUUID(),
            userId = userId,
            appPackageName = packageName,
            actionType = action.toAuditType(),
            recordedAt = clock.instant()
        )
        serviceScope.launch {
            interceptActionAuditRepository.append(userId, audit)
                .onFailure { e -> Timber.e(e, "Failed to append intercept-action audit") }
        }
    }
}
