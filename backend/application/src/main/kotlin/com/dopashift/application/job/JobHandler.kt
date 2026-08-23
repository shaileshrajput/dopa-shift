package com.dopashift.application.job

/**
 * Interface for background job handlers. Each supported [JobType] must have
 * a corresponding implementation registered with the [JobQueueProcessor].
 *
 * Implementations handle specific job logic (e.g., archival, cache eviction)
 * and are dispatched by job type from the queue processor.
 *
 * Requirements: 11.2, 11.3
 */
interface JobHandler {

    /**
     * The job type this handler processes.
     */
    val jobType: JobType

    /**
     * Executes the job with the given JSON payload.
     *
     * @param payload the JSONB payload stored in the job_queue row, containing job-specific parameters
     * @throws Exception if the job execution fails — the processor will handle retry logic
     */
    suspend fun handle(payload: String)
}
