package com.dopashift.application.job

import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Core job queue processing logic. Dequeues pending jobs from the repository,
 * dispatches them to the appropriate [JobHandler], and manages retry/failure transitions.
 *
 * Job lifecycle:
 * - PENDING → PROCESSING (claimed via SKIP LOCKED)
 * - PROCESSING → COMPLETED (on success)
 * - PROCESSING → PENDING (on failure, if attempts < max_attempts — retry)
 * - PROCESSING → FAILED (on failure, if attempts >= max_attempts)
 *
 * This class is framework-agnostic. The scheduling mechanism (e.g., Spring @Scheduled
 * or a coroutine-based loop) is implemented in the infrastructure layer.
 *
 * Requirements: 11.2, 11.3
 */
class JobQueueProcessor(
    private val jobQueueRepository: JobQueueRepository,
    handlers: List<JobHandler>,
    private val clock: Clock = Clock.systemUTC()
) {
    private val handlerRegistry: Map<JobType, JobHandler> = handlers.associateBy { it.jobType }

    /**
     * Polls for and processes available jobs. Called by the scheduling infrastructure.
     *
     * @param batchSize maximum number of jobs to claim in one poll cycle
     * @return list of results for each processed job
     */
    suspend fun pollAndProcess(batchSize: Int = 5): List<JobProcessingResult> {
        val jobs = jobQueueRepository.claimPendingJobs(limit = batchSize)
        return jobs.map { job -> processJob(job) }
    }

    private suspend fun processJob(job: JobRecord): JobProcessingResult {
        val jobType = try {
            JobType.fromValue(job.jobType)
        } catch (e: IllegalArgumentException) {
            val errorMsg = "Unknown job type: ${job.jobType}"
            markJobFailed(job, errorMsg)
            return JobProcessingResult(
                jobId = job.id,
                jobType = job.jobType,
                success = false,
                errorMessage = errorMsg
            )
        }

        val handler = handlerRegistry[jobType]
        if (handler == null) {
            val errorMsg = "No handler registered for job type: ${job.jobType}"
            markJobFailed(job, errorMsg)
            return JobProcessingResult(
                jobId = job.id,
                jobType = job.jobType,
                success = false,
                errorMessage = errorMsg
            )
        }

        return try {
            handler.handle(job.payload)
            jobQueueRepository.markCompleted(job.id, Instant.now(clock))
            JobProcessingResult(
                jobId = job.id,
                jobType = job.jobType,
                success = true,
                errorMessage = null
            )
        } catch (e: Exception) {
            val newAttempts = job.attempts + 1
            val errorMsg = e.message ?: e::class.simpleName ?: "Unknown error"

            if (newAttempts >= job.maxAttempts) {
                jobQueueRepository.markFailed(job.id, newAttempts, errorMsg)
            } else {
                jobQueueRepository.markForRetry(job.id, newAttempts, errorMsg)
            }

            JobProcessingResult(
                jobId = job.id,
                jobType = job.jobType,
                success = false,
                errorMessage = errorMsg
            )
        }
    }

    private suspend fun markJobFailed(job: JobRecord, errorMsg: String) {
        val newAttempts = job.attempts + 1
        if (newAttempts >= job.maxAttempts) {
            jobQueueRepository.markFailed(job.id, newAttempts, errorMsg)
        } else {
            jobQueueRepository.markForRetry(job.id, newAttempts, errorMsg)
        }
    }
}

/**
 * Result of processing a single job.
 */
data class JobProcessingResult(
    val jobId: UUID,
    val jobType: String,
    val success: Boolean,
    val errorMessage: String?
)
