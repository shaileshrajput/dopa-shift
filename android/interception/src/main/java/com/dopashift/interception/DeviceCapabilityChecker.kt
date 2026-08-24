package com.dopashift.interception

import android.app.ActivityManager
import android.content.Context
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects device capability limitations relevant to interception functionality.
 *
 * Validates: Requirement 2.2
 * - Detects Android Go edition (low-RAM devices) and informs the user
 *   that overlay interception is unavailable on this device class.
 */
@Singleton
class DeviceCapabilityChecker @Inject constructor() {

    /**
     * Result describing whether the device supports overlay interception.
     */
    sealed class DeviceSupport {
        /** Device supports all interception features. */
        data object Supported : DeviceSupport()

        /** Device is Android Go / low-RAM — interception is unavailable. */
        data class Unsupported(val reason: String) : DeviceSupport()
    }

    /**
     * Checks whether the current device supports overlay interception.
     *
     * Android Go edition devices (identified via [ActivityManager.isLowRamDevice])
     * have restrictions on overlay windows and background services that make
     * real-time interception unreliable.
     *
     * @return [DeviceSupport.Supported] if the device can run interception,
     *         [DeviceSupport.Unsupported] with an explanation message otherwise.
     */
    fun checkInterceptionSupport(context: Context): DeviceSupport {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        if (activityManager.isLowRamDevice) {
            val reason = "Overlay interception is unavailable on this device. " +
                "Android Go edition (low-RAM) devices have restrictions that prevent " +
                "reliable background monitoring and overlay display."
            Timber.w("Device detected as Android Go / low-RAM — interception unavailable")
            return DeviceSupport.Unsupported(reason)
        }

        Timber.d("Device supports interception features")
        return DeviceSupport.Supported
    }

    /**
     * Checks interception support from an [InterceptionContext] abstraction (testable).
     */
    fun checkInterceptionSupport(context: InterceptionContext): DeviceSupport {
        if (context.isLowRamDevice) {
            val reason = "Overlay interception is unavailable on this device. " +
                "Android Go edition (low-RAM) devices have restrictions that prevent " +
                "reliable background monitoring and overlay display."
            return DeviceSupport.Unsupported(reason)
        }
        return DeviceSupport.Supported
    }

    /**
     * Convenience check: returns true only if the device is fully capable of interception.
     */
    fun isInterceptionAvailable(context: Context): Boolean {
        return checkInterceptionSupport(context) is DeviceSupport.Supported
    }
}
