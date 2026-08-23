package com.dopashift.infrastructure.job

import com.dopashift.application.job.JobHandler
import com.dopashift.application.job.JobQueueProcessor
import com.dopashift.application.job.JobQueueRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * Spring-scheduled poller that drives the [JobQueueProcessor].
 * Polls for pending jobs at a configurable interval (default 5 seconds)
 * and delegates processing to the application-layer processor.
 *
 * The polling interval is configurable via the property:
 *   dopashift.job-queue.poll-interval-ms (default: 5000)
 *
 * The batch size per poll is configurable via:
 *   dopashift.job-queue.batch-size (default: 5)
 *
 * Requirements: 11.2, 11.3
 */
@Component
class ScheduledJobQueuePoller(
    jobQueueRepository: JobQueueRepository,
    handlers: List<JobHandler>,
    @Value("\${dopashift.job-queue.batch-size:5}")
    private val batchSize: Int,
    clock: Clock
) {
    private val logger = LoggerFactory.getLogger(ScheduledJobQueuePoller::class.java)

    private val processor = JobQueueProcessor(
        jobQueueRepository = jobQueueRepository,
        handlers = handlers,
        clock = clock
    )

    @Scheduled(fixedDelayString = "\${dopashift.job-queue.poll-interval-ms:5000}")
    fun poll() {
        try {
            val results = runBlocking { processor.pollAndProcess(batchSize) }
            if (results.isNotEmpty()) {
                val succeeded = results.count { it.success }
                val failed = results.size - succeeded
                logger.info("Job queue poll completed: {} succeeded, {} failed", succeeded, failed)
            }
        } catch (e: Exception) {
            logger.error("Error during job queue poll cycle", e)
        }
    }
}
