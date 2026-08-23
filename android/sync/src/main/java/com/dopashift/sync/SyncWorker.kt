package com.dopashift.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dopashift.sync.model.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that coordinates background sync operations.
 * Triggers push of pending local changes and pull of remote events
 * when network connectivity is available.
 *
 * Uses WorkManager's built-in constraints for network detection and
 * delegates retry logic to SyncManager's exponential backoff.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_PERIODIC = "dopashift_periodic_sync"
        const val WORK_NAME_ONE_TIME = "dopashift_one_time_sync"
        private const val PERIODIC_INTERVAL_MINUTES = 15L

        /**
         * Enqueues a one-time sync when the device comes online.
         * Used to trigger immediate sync on reconnect (Requirement 4.8).
         */
        fun enqueueOneTimeSync(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueue(request)
            Timber.d("Enqueued one-time sync work")
        }

        /**
         * Schedules periodic sync to push/pull changes at regular intervals.
         * Uses KEEP policy to avoid replacing existing scheduled work.
         */
        fun schedulePeriodicSync(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Timber.d("Scheduled periodic sync every $PERIODIC_INTERVAL_MINUTES minutes")
        }

        /**
         * Cancels all scheduled sync work.
         */
        fun cancelAll(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
            Timber.d("Cancelled all sync work")
        }
    }

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker starting sync cycle")

        // TODO: Retrieve actual userId from auth session once auth module is integrated.
        // For now, this worker expects userId to be provided via input data or session.
        val userId = inputData.getString("userId") ?: run {
            Timber.w("No userId provided to SyncWorker, skipping sync")
            return Result.success()
        }

        val (pullResult, pushResult) = syncManager.sync(userId)

        return when {
            pullResult is SyncResult.Failure && pullResult.attemptsExhausted -> {
                Timber.e("Sync failed: pull exhausted retries")
                Result.failure()
            }
            pushResult is SyncResult.Failure && pushResult.attemptsExhausted -> {
                Timber.e("Sync failed: push exhausted retries")
                Result.failure()
            }
            else -> {
                Timber.d("Sync cycle completed successfully")
                Result.success()
            }
        }
    }
}
