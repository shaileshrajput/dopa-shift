package com.dopashift.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.dopashift.interception.InterceptionPermissionHelper
import com.dopashift.interception.InterceptionService
import com.dopashift.interception.PermissionRevocationDetector
import com.dopashift.app.navigation.DopaShiftKineticApp
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionRevocationDetector: PermissionRevocationDetector

    @Inject
    lateinit var permissionHelper: InterceptionPermissionHelper

    // TODO: Replace with actual user ID from auth/session layer
    private val userId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

    /**
     * Tracks whether we've already asked for POST_NOTIFICATIONS this launch so the
     * system dialog is only presented once per process (repeated resumes must not
     * re-prompt after a user decision).
     */
    private var notificationPermissionRequested = false

    /**
     * Runtime request for POST_NOTIFICATIONS (Android 13+/API 33+). Without a granted
     * runtime permission the limit-reached and todo-reminder notifications posted by the
     * interception engine are silently dropped by the platform.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Timber.d("POST_NOTIFICATIONS runtime grant result: $granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Attach permission revocation detector as lifecycle observer (Requirement 2.6)
        lifecycle.addObserver(permissionRevocationDetector)

        // Request the runtime notification permission up front (API 33+). This is a normal
        // runtime dialog, unlike the special-access grants (usage stats / overlay) which can
        // only be toggled from system Settings screens.
        requestNotificationPermissionIfNeeded()

        setContent {
            // Task 11.1: host the DopaShift Kinetic NavHost (four bottom-nav destinations + the
            // pushed Goal Detail / App Limits screens), wired in the app module. The permission
            // helper is passed through so RequiredPermissionsGate can re-check on every resume and
            // present the permission dialog when usage-stats / overlay access is missing.
            DopaShiftKineticApp(permissionHelper = permissionHelper)
        }
    }

    override fun onResume() {
        super.onResume()
        // When the user returns from Settings, check if permissions are now granted
        // and start the interception service if so (Requirement 2, AC1).
        startInterceptionServiceIfPermitted()
    }

    /**
     * Requests POST_NOTIFICATIONS at runtime on Android 13+ if it is not already granted.
     * Older platforms grant notification posting at install time, so nothing is requested there.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (notificationPermissionRequested) return

        val alreadyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!alreadyGranted) {
            notificationPermissionRequested = true
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Starts [InterceptionService] as a foreground service if both required special-access
     * permissions (PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW) are granted. Called on every
     * resume to handle the case where the user just returned from granting them in Settings.
     *
     * These two permissions cannot be requested via a runtime dialog — they are only togglable
     * from dedicated system Settings screens. The in-app grant flow (onboarding permission step
     * and the "Manage app limits" permission banner) walks the user to those screens; this method
     * picks up the result once the user returns and starts monitoring.
     */
    private fun startInterceptionServiceIfPermitted() {
        if (permissionHelper.hasAllRequiredPermissions(this)) {
            val serviceIntent = InterceptionService.createStartIntent(this, userId)
            ContextCompat.startForegroundService(this, serviceIntent)
            Timber.d("InterceptionService start requested — permissions granted")
        } else {
            Timber.d("InterceptionService not started — special-access permissions missing")
        }
    }
}
