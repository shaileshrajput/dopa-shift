package com.dopashift.interception.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * WorkManager-based periodic worker that evaluates all active reminders.
 *
 * This worker runs periodically (minimum 15 minutes per WorkManager constraint)
 * and delegates to [ClientReminderEngine] for the actual evaluation logic.
 *
 * For time-exact delivery, AlarmManager is used (see ReminderNotificationScheduler).
 * WorkManager provides the periodic "catch-up" evaluation for reminders that may
 * have been missed while the device was in Doze or the app was killed.
 *
 * Requirements: 5.14, 5.15, 5.16
 */
@HiltWorker
class ReminderEvaluationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val clientReminderEngine: ClientReminderEngine
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "reminder_evaluation"
        const val KEY_USER_ID = "user_id"
        private const val REPEAT_INTERVAL_MINUTES = 15L

        /**
         * Enqueues the periodic reminder evaluation work.
         * Uses KEEP policy to avoid restarting if already scheduled.
         */
        fun enqueue(context: Context, userId: UUID) {
            val workRequest = PeriodicWorkRequestBuilder<ReminderEvaluationWorker>(
                REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .addTag("reminder_evaluation_$userId")
                .setInputData(
                    androidx.work.Data.Builder()
                        .putString(KEY_USER_ID, userId.toString())
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
                )

            Timber.d("Reminder evaluation work enqueued for user $userId")
        }

        /**
         * Cancels the periodic reminder evaluation work.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Timber.d("Reminder evaluation work cancelled")
        }
    }

    override suspend fun doWork(): Result {
        val userIdStr = inputData.getString(KEY_USER_ID)
        if (userIdStr == null) {
            Timber.e("ReminderEvaluationWorker: missing user_id input")
            return Result.failure()
        }

        val userId = try {
            UUID.fromString(userIdStr)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "ReminderEvaluationWorker: invalid user_id")
            return Result.failure()
        }

        return try {
            Timber.d("ReminderEvaluationWorker: evaluating reminders for user $userId")
            val actions = clientReminderEngine.evaluateAll(userId)
            Timber.d("ReminderEvaluationWorker: completed with ${actions.size} actions")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "ReminderEvaluationWorker: evaluation failed")
            Result.retry()
        }
    }
}
