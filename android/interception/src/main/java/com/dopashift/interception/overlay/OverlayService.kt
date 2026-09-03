package com.dopashift.interception.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dopashift.domain.repository.DailyTodoRepository
import com.dopashift.domain.repository.GoalRepository
import com.dopashift.domain.repository.HabitTrackRepository
import com.dopashift.interception.InterceptionService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Service that manages the TYPE_APPLICATION_OVERLAY window for the intercept overlay.
 *
 * Displays a full-screen overlay using Jetpack Compose rendered in a system overlay window.
 * The overlay is shown when a tracked app's daily allowance is depleted and dismissed
 * after the user satisfies the minimum engagement requirement.
 *
 * Requirements: 2.4, 2.5, 2.9, 17.7
 *
 * Architecture:
 * - Uses [ComposeView] attached to a [WindowManager] overlay.
 * - Implements [LifecycleOwner], [ViewModelStoreOwner], and [SavedStateRegistryOwner]
 *   so that Compose can properly manage state and lifecycle within a Service context.
 * - The [OverlayViewModel] handles business logic; this service only manages the window.
 */
@AndroidEntryPoint
class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_triggering_package"

        private const val NOTIFICATION_ID = 1003
        private const val CHANNEL_ID = "interception_overlay_channel"
        private const val CHANNEL_NAME = "Focus Screen"

        /**
         * Creates an intent to show the overlay triggered by a specific app.
         */
        fun createShowIntent(context: Context, triggeringPackageName: String): Intent {
            return Intent(context, OverlayService::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, triggeringPackageName)
            }
        }
    }

    @Inject
    lateinit var dailyTodoRepository: DailyTodoRepository

    @Inject
    lateinit var habitTrackRepository: HabitTrackRepository

    @Inject
    lateinit var goalRepository: GoalRepository

    /**
     * Handles overlay to-do completions so they go through the same Change_Log pipeline as normal
     * completions (Requirement 4.8). Injected as a [Singleton] since it has an @Inject constructor.
     */
    @Inject
    lateinit var todoCompletionHandler: OverlayTodoCompletionHandler

    /**
     * Handles inline goal capture from the overlay when the user has zero goals.
     */
    @Inject
    lateinit var inlineGoalCaptureHandler: OverlayInlineGoalCaptureHandler

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var viewModel: OverlayViewModel? = null

    // Lifecycle management for Compose within a Service
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val _viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Timber.d("OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val triggeringPackage = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        Timber.d("OverlayService showing overlay for: $triggeringPackage")

        // This service is launched via startForegroundService() by InterceptionService, so it
        // MUST promote itself to the foreground within the platform's ~5s window or Android throws
        // ForegroundServiceDidNotStartInTimeException and kills it before the overlay renders.
        // Promote first, then add the overlay window.
        promoteToForeground()

        showOverlay(triggeringPackage)
        return START_NOT_STICKY
    }

    /**
     * Promotes this service to the foreground with a lightweight persistent notification. Required
     * because the service is started with [Context.startForegroundService]; failing to call
     * [startForeground] in time causes the platform to kill the service (Android 8+/12+ strict
     * enforcement), which is why the Intercept_Screen never appeared.
     */
    private fun promoteToForeground() {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Time to refocus")
            .setContentText("Your screen-time allowance is up.")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Timber.d("OverlayService promoted to foreground")
    }

    /**
     * Creates the notification channel backing the overlay foreground notification (required on
     * Android O+).
     */
    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while the full-screen focus overlay is active"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        dismissOverlay()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        _viewModelStore.clear()
        Timber.d("OverlayService destroyed")
    }

    /**
     * Shows the full-screen overlay window using TYPE_APPLICATION_OVERLAY.
     */
    private fun showOverlay(triggeringPackageName: String) {
        if (overlayView != null) {
            Timber.w("Overlay already showing, ignoring duplicate request")
            return
        }

        // OverlayViewModel is not created via Hilt in the overlay window (a Service-hosted
        // ComposeView), so its collaborators are supplied here from the service's injected
        // dependencies. Routing completions through the handler preserves the Change_Log pipeline
        // (Requirement 4.8).
        val vm = OverlayViewModel(
            dailyTodoRepository,
            habitTrackRepository,
            goalRepository,
            todoCompletionHandler,
            inlineGoalCaptureHandler
        )
        vm.initialize(triggeringPackageName)
        viewModel = vm

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                // Host the DopaShift Kinetic overlay composable (spec android-ui-upgrade, task
                // 11.2). It renders the state this service exposes via OverlayViewModel and runs
                // no timer of its own — the cooldown is derived from the view-model's timer.
                InterceptOverlayHost(
                    viewModel = vm,
                    onDismiss = { dismissOverlay() }
                )
            }
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            // On Android 15+, ensure the overlay is visible before foreground service starts
            if (Build.VERSION.SDK_INT >= 35) {
                // Mark as an accessibility overlay to satisfy the visible-before-start restriction
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
            overlayView = composeView
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            Timber.i("Overlay displayed for package: $triggeringPackageName")
        } catch (e: Exception) {
            Timber.e(e, "Failed to add overlay view")
            stopSelf()
        }
    }

    /**
     * Removes the overlay window and stops the service.
     *
     * Before tearing down, reports the overlay engagement result (whether the user checked a habit
     * or completed a to-do) back to [InterceptionService] via an Intent signal so the Engine can
     * decide whether to grant a Focus_Extension (Requirements 4.9, 4.13). Using an Intent keeps the
     * two services decoupled — [InterceptionService] is START_STICKY and already running, so it
     * routes the extra to `onOverlayEngagementResult` from `onStartCommand`.
     */
    private fun dismissOverlay() {
        reportEngagementResult()

        overlayView?.let { view ->
            try {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                windowManager.removeView(view)
                Timber.i("Overlay dismissed")
            } catch (e: Exception) {
                Timber.e(e, "Error removing overlay view")
            }
        }
        overlayView = null
        viewModel = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    /**
     * Signals [InterceptionService] with the overlay engagement result on dismissal. Reads the
     * recorded-engagement flag, triggering package, and the selected primary action from the
     * [OverlayViewModel] and starts [InterceptionService] with the engagement action so it can
     * grant/deny a Focus_Extension, route the Continue/Switch behavior, append the local-only
     * action audit, and reset its overlay-active state (Requirements 3.2–3.4, 4.9, 4.13).
     */
    private fun reportEngagementResult() {
        val vm = viewModel ?: return
        val engaged = vm.hasRecordedEngagement()
        val pkg = vm.triggeringPackageName()
        // The primary action the user tapped ("Continue to app" / "Switch to DopaShift"), or null
        // when the overlay was dismissed without one. Forwarded so the Engine can route the correct
        // Continue/Switch behavior and append the local-only action audit (Requirements 3.2–3.4).
        val selectedAction = vm.selectedAction()
        try {
            val intent = InterceptionService.createEngagementResultIntent(this, pkg, engaged, selectedAction)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Timber.d("Reported overlay engagement result to InterceptionService (engaged=%b)", engaged)
        } catch (e: Exception) {
            Timber.e(e, "Failed to report overlay engagement result")
        }
    }
}
