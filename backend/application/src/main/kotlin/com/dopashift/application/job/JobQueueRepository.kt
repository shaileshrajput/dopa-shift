package com.dopashift.application.job

import java.time.Instant
import java.util.UUID

/**
 * Port interface for job queue persistence operations.
 * The infrastructure layer provides the concrete implementation using
 * PostgreSQL's SELECT ... FOR UPDATE SKIP LOCKED pattern.
 *
 * Requirements: 11.2, 11.3
 */
interface JobQueueRepository {

    /**
     * Atomically claims the next pending job (PENDING status, scheduled_at <= now)
     * using SKIP LOCKED to allow concurrent workers.
     * Sets status to PROCESSING and started_at to the current time.
     *
     * @param limit maximum number of jobs to claim in one poll
     * @return list of claimed jobs, empty if none available
     */
    suspend fun claimPendingJobs(limit: Int = 1): List<JobRecord>

    /**
     * Marks a job as COMPLETED with the given completion timestamp.
     */
    suspend fun markCompleted(jobId: UUID, completedAt: Instant)

    /**
     * Marks a job as FAILED, incrementing attempts and recording the error message.
     */
    suspend fun markFailed(jobId: UUID, attempts: Int, errorMessage: String)

    /**
     * Resets a job to PENDING for retry, incrementing attempts and clearing started_at.
     */
    suspend fun markForRetry(jobId: UUID, attempts: Int, errorMessage: String)
}

/**
 * Represents a row in the job_queue table that has been claimed for processing.
 */
data class JobRecord(
    val id: UUID,
    val jobType: String,
    val payload: String,
    val status: String,
    val priority: Int,
    val attempts: Int,
    val maxAttempts: Int,
    val scheduledAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val errorMessage: String?,
    val createdAt: Instant
)
