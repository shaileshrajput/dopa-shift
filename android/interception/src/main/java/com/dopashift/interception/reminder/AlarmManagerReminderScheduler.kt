package com.dopashift.interception.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.entity.ReminderEntityType
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReminderNotificationScheduler] implementation using [AlarmManager.setExactAndAllowWhileIdle]
 * for precise, battery-efficient reminder delivery.
 *
 * Requirements: 5.14, 5.16
 * - Uses setExactAndAllowWhileIdle for precise timing.
 * - Schedules escalation alarms on non-acknowledgment.
 * - Handles device offline/power-off scenarios (alarms survive reboot via BOOT_COMPLETED receiver).
 *
 * Architecture:
 * - Each reminder gets a unique [PendingIntent] keyed by its UUID.
 * - When the alarm fires, [ReminderAlarmReceiver] shows the notification.
 * - Escalation scheduling is handled by setting a follow-up alarm after the initial fire.
 */
@Singleton
class AlarmManagerReminderScheduler @Inject constructor(
    private val context: Context
) : ReminderNotificationScheduler {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "dopashift_reminders"
        const val NOTIFICATION_CHANNEL_NAME = "Reminders"

        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_REMINDER_TITLE = "extra_reminder_title"
        const val EXTRA_REMINDER_BODY = "extra_reminder_body"
        const val EXTRA_ESCALATION_COUNT = "extra_escalation_count"
        const val EXTRA_ENTITY_TYPE = "extra_entity_type"

        /** Base notification ID derived from reminder UUID hashCode. */
        fun notificationId(reminderId: UUID): Int = reminderId.hashCode()

        /** Request code for alarm PendingIntent derived from reminder UUID. */
        fun requestCode(reminderId: UUID): Int = reminderId.hashCode()
    }

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    override suspend fun scheduleImmediate(reminder: Reminder) {
        Timber.d("Scheduling immediate notification for reminder ${reminder.id}")
        showNotification(reminder, escalationCount = 0)
    }

    override suspend fun scheduleAtTime(reminder: Reminder, deliveryTime: LocalTime) {
        val triggerAtMillis = computeTriggerMillis(deliveryTime)
        val intent = createAlarmIntent(reminder, escalationCount = 0)
        val pendingIntent = createPendingIntent(reminder.id, intent)

        scheduleExactAlarm(triggerAtMillis, pendingIntent)
        Timber.d("Scheduled reminder ${reminder.id} at $deliveryTime (triggerAt=$triggerAtMillis)")
    }

    override suspend fun scheduleEscalation(reminder: Reminder, escalationCount: Int) {
        Timber.d("Scheduling escalation #$escalationCount for reminder ${reminder.id}")

        // Schedule the escalation alarm after the escalation interval
        val intervalMillis = reminder.escalationInterval.toMillis()
        val triggerAtMillis = System.currentTimeMillis() + intervalMillis
        val intent = createAlarmIntent(reminder, escalationCount)
        val pendingIntent = createPendingIntent(
            reminder.id,
            intent,
            requestCodeSuffix = escalationCount
        )

        scheduleExactAlarm(triggerAtMillis, pendingIntent)
        Timber.d("Escalation alarm scheduled: ${reminder.id} count=$escalationCount in ${intervalMillis}ms")
    }

    override suspend fun cancelPending(reminderId: UUID) {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(reminderId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Timber.d("Cancelled pending alarm for reminder $reminderId")
        }

        // Also cancel any active notification
        notificationManager.cancel(notificationId(reminderId))
    }

    /**
     * Schedules an exact alarm that fires even in doze mode.
     * Uses setExactAndAllowWhileIdle for API 23+.
     */
    private fun scheduleExactAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires canScheduleExactAlarms permission check
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                // Fallback to inexact alarm if exact alarm permission not granted
                Timber.w("Cannot schedule exact alarms; using setAndAllowWhileIdle fallback")
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    /**
     * Computes the trigger time in epoch millis for a given [LocalTime] today (or tomorrow if past).
     */
    private fun computeTriggerMillis(deliveryTime: LocalTime): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var target = ZonedDateTime.of(LocalDate.now(), deliveryTime, zone)

        // If the target time is in the past, schedule for tomorrow
        if (target.isBefore(now)) {
            target = target.plusDays(1)
        }

        return target.toInstant().toEpochMilli()
    }

    /**
     * Creates the alarm intent with reminder metadata extras.
     */
    private fun createAlarmIntent(reminder: Reminder, escalationCount: Int): Intent {
        return Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminder.id.toString())
            putExtra(EXTRA_REMINDER_TITLE, buildNotificationTitle(reminder))
            putExtra(EXTRA_REMINDER_BODY, buildNotificationBody(reminder, escalationCount))
            putExtra(EXTRA_ESCALATION_COUNT, escalationCount)
            putExtra(EXTRA_ENTITY_TYPE, reminder.entityType.name)
        }
    }

    /**
     * Creates a PendingIntent for the alarm, keyed by reminder ID.
     */
    private fun createPendingIntent(
        reminderId: UUID,
        intent: Intent,
        requestCodeSuffix: Int = 0
    ): PendingIntent {
        val code = requestCode(reminderId) + requestCodeSuffix
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Shows a notification immediately (for non-scheduled delivery).
     */
    private fun showNotification(reminder: Reminder, escalationCount: Int) {
        val title = buildNotificationTitle(reminder)
        val body = buildNotificationBody(reminder, escalationCount)

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        notificationManager.notify(notificationId(reminder.id), notification)
    }

    private fun buildNotificationTitle(reminder: Reminder): String {
        return when (reminder.entityType) {
            ReminderEntityType.DAILY_TODO -> "Task Reminder"
            ReminderEntityType.GOAL_CHECKLIST -> "Checklist Reminder"
            ReminderEntityType.HABIT_CHECKPOINT -> "Habit Reminder"
        }
    }

    private fun buildNotificationBody(reminder: Reminder, escalationCount: Int): String {
        val prefix = if (escalationCount > 0) "Follow-up #$escalationCount: " else ""
        return when (reminder.entityType) {
            ReminderEntityType.DAILY_TODO -> "${prefix}You have a pending task to complete"
            ReminderEntityType.GOAL_CHECKLIST -> "${prefix}A checklist item needs attention"
            ReminderEntityType.HABIT_CHECKPOINT -> "${prefix}Don't forget today's micro-habit"
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Scheduled reminders for tasks, habits, and goals"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)
    }
}

/**
 * BroadcastReceiver that fires when an alarm triggers.
 * Shows the reminder notification with the extras provided in the alarm intent.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(AlarmManagerReminderScheduler.EXTRA_REMINDER_ID) ?: return
        val title = intent.getStringExtra(AlarmManagerReminderScheduler.EXTRA_REMINDER_TITLE) ?: "Reminder"
        val body = intent.getStringExtra(AlarmManagerReminderScheduler.EXTRA_REMINDER_BODY) ?: ""
        val escalationCount = intent.getIntExtra(AlarmManagerReminderScheduler.EXTRA_ESCALATION_COUNT, 0)

        Timber.d("ReminderAlarmReceiver fired for reminder $reminderId (escalation=$escalationCount)")

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure channel exists (receiver may fire after process restart)
        val channel = NotificationChannel(
            AlarmManagerReminderScheduler.NOTIFICATION_CHANNEL_ID,
            AlarmManagerReminderScheduler.NOTIFICATION_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(context, AlarmManagerReminderScheduler.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notificationId = UUID.fromString(reminderId).hashCode()
        notificationManager.notify(notificationId, notification)
    }
}

/**
 * BroadcastReceiver for BOOT_COMPLETED that reschedules pending alarms.
 * Exact alarms are cleared on device reboot, so they need to be re-registered.
 *
 * Requirement 5.16: Handle device offline/power-off scenarios (deliver on wake).
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            Timber.i("Boot completed — triggering reminder re-evaluation")
            // Delegate to WorkManager to re-evaluate and reschedule all active reminders
            // The ReminderEvaluationWorker will pick up and re-schedule as needed.
            androidx.work.OneTimeWorkRequest.Builder(
                ReminderEvaluationWorker::class.java
            ).build().let { request ->
                androidx.work.WorkManager.getInstance(context).enqueue(request)
            }
        }
    }
}
