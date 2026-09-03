package com.dopashift.interception.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.dopashift.interception.overlay.OverlayService
import com.dopashift.ui.R
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the limit-reached notification when a Monitored_App breaches its daily limit.
 *
 * Requirement 4.4: within one sampling interval of the breach, post a local notification
 * informing the user that the limit was reached, offering a single action to open the
 * Intercept_Screen for that app.
 * Requirement 4.5: tapping the notification (its single action) presents the Intercept_Screen
 * for the associated app.
 *
 * The single action is a [PendingIntent] targeting [OverlayService], which renders the
 * Intercept_Screen overlay for the associated app package.
 *
 * All user-facing strings (title, text, action label) are resolved from `strings.xml`
 * (en/hi/mr) — none are hardcoded here.
 */
@Singleton
class LimitReachedNotifier @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {

    companion object {
        private const val CHANNEL_ID = "interception_limit_reached"
        private const val CHANNEL_NAME = "Screen-Time Limit Reached"
        private const val NOTIFICATION_ID = 3002
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    /**
     * Posts the limit-reached notification for the given app package.
     *
     * @param triggeringPackageName The package name of the Monitored_App that breached its limit.
     */
    fun notifyLimitReached(triggeringPackageName: String) {
        val notification = buildNotification(triggeringPackageName)
        notificationManager.notify(NOTIFICATION_ID, notification)
        // Do not log the package name at info level to avoid leaking usage data.
        Timber.i("Limit-reached notification posted")
    }

    private fun buildNotification(triggeringPackageName: String): android.app.Notification {
        val openInterceptPendingIntent = buildOpenInterceptPendingIntent(triggeringPackageName)

        val actionLabel = context.getString(R.string.limit_reached_notification_action)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.limit_reached_notification_title))
            .setContentText(context.getString(R.string.limit_reached_notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            // Tapping the notification body opens the Intercept_Screen (Requirement 4.5).
            .setContentIntent(openInterceptPendingIntent)
            // The single explicit action also opens the Intercept_Screen (Requirement 4.4).
            .addAction(
                android.R.drawable.ic_menu_view,
                actionLabel,
                openInterceptPendingIntent
            )
            .build()
    }

    /**
     * Builds a [PendingIntent] that starts [OverlayService] to present the Intercept_Screen
     * for the associated app. Uses a per-package request code so distinct apps do not
     * collide on the cached PendingIntent.
     */
    private fun buildOpenInterceptPendingIntent(triggeringPackageName: String): PendingIntent {
        val intent = OverlayService.createShowIntent(context, triggeringPackageName)
        val requestCode = triggeringPackageName.hashCode()
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts shown when a monitored app reaches its daily screen-time limit"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }
}
