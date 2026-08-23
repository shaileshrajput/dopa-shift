package com.dopashift.interception.reminder

import com.dopashift.domain.entity.Reminder
import java.time.LocalTime

/**
 * Interface for scheduling local notifications for reminders.
 *
 * Implementations use AlarmManager.setExactAndAllowWhileIdle (Requirement 5.14)
 * for time-exact delivery, or NotificationManager for immediate delivery.
 */
interface ReminderNotificationScheduler {

    /**
     * Schedules an immediate local notification for the given reminder.
     * Used when the reminder evaluation result is [ReminderEvaluationResult.Fire].
     */
    suspend fun scheduleImmediate(reminder: Reminder)

    /**
     * Schedules a notification at a specific time (e.g., quiet-hours end).
     * Uses exact alarms for precise delivery (Requirement 5.14).
     *
     * @param reminder The reminder to deliver.
     * @param deliveryTime The local time at which to deliver the notification.
     */
    suspend fun scheduleAtTime(reminder: Reminder, deliveryTime: LocalTime)

    /**
     * Schedules an escalation notification for the given reminder.
     * The notification text should indicate this is a follow-up escalation.
     *
     * @param reminder The reminder being escalated.
     * @param escalationCount The current escalation count (for notification content).
     */
    suspend fun scheduleEscalation(reminder: Reminder, escalationCount: Int)

    /**
     * Cancels any pending notifications for the given reminder.
     * Called when the reminder is acknowledged, completed, or deleted.
     */
    suspend fun cancelPending(reminderId: java.util.UUID)
}
