package com.dopashift.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.dopashift.interception.InterceptionPermissionHelper
import com.dopashift.interception.InterceptionService
import com.dopashift.interception.PermissionRevocationDetector
import com.dopashift.ui.navigation.DopaShiftApp
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Attach permission revocation detector as lifecycle observer (Requirement 2.6)
        lifecycle.addObserver(permissionRevocationDetector)

        setContent {
            DopaShiftApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // When the user returns from Settings, check if permissions are now granted
        // and start the interception service if so (Requirement 2, AC1).
        startInterceptionServiceIfPermitted()
    }

    /**
     * Starts [InterceptionService] as a foreground service if both required permissions
     * (PACKAGE_USAGE_STATS and SYSTEM_ALERT_WINDOW) are granted. Called on every resume
     * to handle the case where the user just returned from granting permissions in Settings.
     */
    private fun startInterceptionServiceIfPermitted() {
        if (permissionHelper.hasAllRequiredPermissions(this)) {
            val serviceIntent = InterceptionService.createStartIntent(this, userId)
            ContextCompat.startForegroundService(this, serviceIntent)
            Timber.d("InterceptionService start requested — permissions granted")
        }
    }
}
