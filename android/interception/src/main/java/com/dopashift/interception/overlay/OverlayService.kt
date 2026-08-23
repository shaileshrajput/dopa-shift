package com.dopashift.interception.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.dopashift.domain.repository.HabitTrackRepository
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
        Timber.d("OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val triggeringPackage = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
        Timber.d("OverlayService showing overlay for: $triggeringPackage")

        showOverlay(triggeringPackage)
        return START_NOT_STICKY
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

        val vm = OverlayViewModel(dailyTodoRepository, habitTrackRepository)
        vm.initialize(triggeringPackageName)
        viewModel = vm

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            setContent {
                InterceptOverlayScreen(
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
     */
    private fun dismissOverlay() {
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
        stopSelf()
    }
}
