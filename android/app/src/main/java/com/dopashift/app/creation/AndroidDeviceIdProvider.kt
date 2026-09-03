package com.dopashift.app.creation

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.dopashift.domain.creation.DeviceIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [DeviceIdProvider] returning the stable per-install identifier stamped onto every
 * [com.dopashift.domain.entity.ChangeLogEntry] this device appends (DQC-6.1, DQC-6.2).
 *
 * Uses [Settings.Secure.ANDROID_ID], a stable identifier scoped to the app-signing key and user
 * for the lifetime of the install. It is not a hardware serial and carries no PII, so it is safe
 * to place in a Change_Log `deviceId` field without redaction concerns.
 */
@Singleton
class AndroidDeviceIdProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceIdProvider {

    @SuppressLint("HardwareIds")
    override fun deviceId(): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: FALLBACK_DEVICE_ID

    private companion object {
        /** Used only if the platform returns a null ANDROID_ID (rare/unrooted edge cases). */
        const val FALLBACK_DEVICE_ID = "unknown-device"
    }
}
