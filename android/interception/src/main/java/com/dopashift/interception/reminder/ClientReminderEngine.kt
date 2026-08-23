package com.dopashift.interception.reminder

import com.dopashift.domain.entity.Reminder
import com.dopashift.domain.model.ReminderEvaluationResult
import com.dopashift.domain.port.QuietHoursProvider
import com.dopashift.domain.repository.ReminderRepository
import com.dopashift.domain.service.ReminderEvaluationService
import timber.log.Timber
import java.time.Clock
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side reminder evaluation engine that periodically evaluates all active
 * reminders for the user against local Room data.
 *
 * Operates fully offline — all entity state, quiet-hours configuration, and
 * reminder rules are evaluated against locally-stored data.
 *
 * Responsibilities:
 * - Periodically evaluate all active reminders via [ReminderEvaluationService]
 * - When Fire: delegate to [ReminderNotificationScheduler] for local notification
 * - When DelayedToQuietHoursEnd: schedule delivery at the quiet-hours end time
 * - When Escalate: increment escalation count and schedule follow-up notification
 * - Track last-fired timestamps per reminder for escalation logic
 *
 * Integrates with WorkManager for periodic evaluation (via [ReminderEvaluationWorker]).
 *
 * Requirements: 5.5, 5.11, 5.13, 5.14, 5.15, 5.16
 */
@Singleton
class ClientReminderEngine @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val evaluationService: ReminderEvaluationService,
    private val quietHoursProvider: QuietHoursProvider,
    private val notificationScheduler: ReminderNotificationScheduler,
    private val clock: Clock
) {

    /**
     * In-memory map of reminderId -> last fired instant.
     * In production this would be persisted, but for the engine's core logic
     * we track it here. WorkManager will invoke evaluate periodically.
     */
    private val lastFiredMap = mutableMapOf<UUID, Instant>()

    /**
     * Evaluates all active reminders for the given [userId].
     * This is the main entry point invoked by [ReminderEvaluationWorker].
     *
     * @param userId The ID of the user whose reminders should be evaluated.
     * @return A list of [EvaluationAction] indicating what actions were taken.
     */
    suspend fun evaluateAll(userId: UUID): List<EvaluationAction> {
        val reminders = reminderRepository.findActiveByUserId(userId)
        val quietHours = quietHoursProvider.getQuietHours(userId)
        val userZone = quietHoursProvider.getUserTimeZone(userId)

        val actions = mutableListOf<EvaluationAction>()

        for (reminder in reminders) {
            try {
                val action = evaluateReminder(reminder, quietHours, userZone)
                if (action != null) {
                    actions.add(action)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to evaluate reminder ${reminder.id}")
            }
        }

        return actions
    }

    /**
     * Evaluates a single reminder, handling both initial firing and escalation.
     */
    private suspend fun evaluateReminder(
        reminder: Reminder,
        quietHours: com.dopashift.domain.model.QuietHoursWindow?,
        userZone: java.time.ZoneId
    ): EvaluationAction? {
        val lastFired = lastFiredMap[reminder.id]

        // If the reminder has already fired and hasn't been acknowledged,
        // evaluate escalation instead
        if (lastFired != null) {
            return evaluateEscalation(reminder, lastFired, quietHours, userZone)
        }

        // Evaluate whether the reminder should fire now
        val result = evaluationService.evaluate(reminder, quietHours, userZone)
        return handleResult(reminder, result)
    }

    /**
     * Evaluates escalation for a reminder that has already fired but wasn't acknowledged.
     */
    private suspend fun evaluateEscalation(
        reminder: Reminder,
        lastFired: Instant,
        quietHours: com.dopashift.domain.model.QuietHoursWindow?,
        userZone: java.time.ZoneId
    ): EvaluationAction? {
        val result = evaluationService.evaluateEscalation(
            reminder, lastFired, quietHours, userZone
        )
        return handleEscalationResult(reminder, result)
    }

    /**
     * Handles the result of a reminder evaluation.
     */
    private suspend fun handleResult(
        reminder: Reminder,
        result: ReminderEvaluationResult
    ): EvaluationAction? {
        return when (result) {
            is ReminderEvaluationResult.Fire -> {
                Timber.d("Reminder ${reminder.id} should fire")
                val now = Instant.now(clock)
                lastFiredMap[reminder.id] = now
                notificationScheduler.scheduleImmediate(reminder)
                EvaluationAction.Fired(reminder.id)
            }

            is ReminderEvaluationResult.DelayedToQuietHoursEnd -> {
                Timber.d("Reminder ${reminder.id} delayed to quiet-hours end: ${result.deliveryTime}")
                notificationScheduler.scheduleAtTime(reminder, result.deliveryTime)
                EvaluationAction.DelayedToQuietHoursEnd(reminder.id, result.deliveryTime)
            }

            is ReminderEvaluationResult.Suppressed -> {
                Timber.v("Reminder ${reminder.id} suppressed: ${result.reason}")
                null
            }

            is ReminderEvaluationResult.Escalate -> {
                // This shouldn't happen for initial evaluation, but handle it
                Timber.d("Reminder ${reminder.id} escalating (count: ${result.newEscalationCount})")
                val now = Instant.now(clock)
                lastFiredMap[reminder.id] = now
                updateEscalationCount(reminder, result.newEscalationCount)
                notificationScheduler.scheduleEscalation(reminder, result.newEscalationCount)
                EvaluationAction.Escalated(reminder.id, result.newEscalationCount)
            }
        }
    }

    /**
     * Handles the result of an escalation evaluation.
     */
    private suspend fun handleEscalationResult(
        reminder: Reminder,
        result: ReminderEvaluationResult
    ): EvaluationAction? {
        return when (result) {
            is ReminderEvaluationResult.Escalate -> {
                Timber.d("Reminder ${reminder.id} escalating (count: ${result.newEscalationCount})")
                val now = Instant.now(clock)
                lastFiredMap[reminder.id] = now
                updateEscalationCount(reminder, result.newEscalationCount)
                notificationScheduler.scheduleEscalation(reminder, result.newEscalationCount)
                EvaluationAction.Escalated(reminder.id, result.newEscalationCount)
            }

            is ReminderEvaluationResult.DelayedToQuietHoursEnd -> {
                Timber.d("Escalation for ${reminder.id} delayed to quiet-hours end: ${result.deliveryTime}")
                notificationScheduler.scheduleAtTime(reminder, result.deliveryTime)
                EvaluationAction.DelayedToQuietHoursEnd(reminder.id, result.deliveryTime)
            }

            is ReminderEvaluationResult.Suppressed -> {
                // If suppressed due to entity complete or max escalations, clear tracking
                if (result.reason == com.dopashift.domain.model.SuppressionReason.ENTITY_COMPLETE ||
                    result.reason == com.dopashift.domain.model.SuppressionReason.MAX_ESCALATIONS_REACHED
                ) {
                    lastFiredMap.remove(reminder.id)
                }
                null
            }

            is ReminderEvaluationResult.Fire -> {
                // Shouldn't happen for escalation, but treat as fire
                val now = Instant.now(clock)
                lastFiredMap[reminder.id] = now
                notificationScheduler.scheduleImmediate(reminder)
                EvaluationAction.Fired(reminder.id)
            }
        }
    }

    /**
     * Persists the updated escalation count back to the repository.
     */
    private suspend fun updateEscalationCount(reminder: Reminder, newCount: Int) {
        val updated = reminder.copy(escalationCount = newCount)
        reminderRepository.save(updated)
    }

    /**
     * Marks a reminder as acknowledged (user opened/completed/dismissed the notification).
     * This resets the escalation tracking for this reminder.
     */
    fun acknowledgeReminder(reminderId: UUID) {
        lastFiredMap.remove(reminderId)
        Timber.d("Reminder $reminderId acknowledged, escalation tracking cleared")
    }

    /**
     * Clears all tracking state. Called when user logs out or on engine reset.
     */
    fun reset() {
        lastFiredMap.clear()
    }
}

/**
 * Represents an action taken by the engine after evaluating a reminder.
 */
sealed class EvaluationAction {
    data class Fired(val reminderId: UUID) : EvaluationAction()
    data class Escalated(val reminderId: UUID, val newEscalationCount: Int) : EvaluationAction()
    data class DelayedToQuietHoursEnd(
        val reminderId: UUID,
        val deliveryTime: java.time.LocalTime
    ) : EvaluationAction()
}
