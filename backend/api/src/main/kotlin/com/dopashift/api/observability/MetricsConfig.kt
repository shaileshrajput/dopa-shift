package com.dopashift.api.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.atomic.AtomicLong

/**
 * Custom Prometheus metrics for DopaShift API.
 *
 * Exposes:
 * - Request rate, error rate, and latency (p50/p95/p99) via default Spring Boot Actuator
 * - Custom metrics: sync-queue depth, overlay trigger rate, job success/failure rates
 * - JVM utilization via default Micrometer JVM metrics
 *
 * Requirements: 13.4, 18.7
 */
@Configuration
class MetricsConfig {

    @Bean
    fun dopaShiftMetrics(registry: MeterRegistry): DopaShiftMetrics {
        return DopaShiftMetrics(registry)
    }
}

/**
 * Centralized access to all custom DopaShift metrics.
 * Inject this class where custom metrics need to be recorded.
 */
class DopaShiftMetrics(private val registry: MeterRegistry) {

    // === Sync Engine Metrics ===

    private val syncQueueDepth = AtomicLong(0L)

    init {
        Gauge.builder("dopashift.sync.queue.depth", syncQueueDepth) { it.get().toDouble() }
            .description("Current depth of the sync push queue (unsynced Change_Log entries)")
            .register(registry)
    }

    val syncPushSuccessCounter: Counter = Counter.builder("dopashift.sync.push.total")
        .tag("result", "success")
        .description("Total successful sync push operations")
        .register(registry)

    val syncPushFailureCounter: Counter = Counter.builder("dopashift.sync.push.total")
        .tag("result", "failure")
        .description("Total failed sync push operations")
        .register(registry)

    val syncPushTimer: Timer = Timer.builder("dopashift.sync.push.duration")
        .description("Duration of sync push operations")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)

    val syncPullTimer: Timer = Timer.builder("dopashift.sync.pull.duration")
        .description("Duration of sync pull operations")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)

    val syncConflictCounter: Counter = Counter.builder("dopashift.sync.conflicts.total")
        .description("Total sync conflicts detected")
        .register(registry)

    // === Overlay Metrics ===

    val overlayTriggerCounter: Counter = Counter.builder("dopashift.overlay.triggers.total")
        .description("Total intercept overlay triggers (from Android telemetry sync)")
        .register(registry)

    // === Background Job Metrics ===

    fun jobSuccessCounter(jobType: String): Counter =
        Counter.builder("dopashift.jobs.total")
            .tag("type", jobType)
            .tag("result", "success")
            .description("Total successful background job executions")
            .register(registry)

    fun jobFailureCounter(jobType: String): Counter =
        Counter.builder("dopashift.jobs.total")
            .tag("type", jobType)
            .tag("result", "failure")
            .description("Total failed background job executions")
            .register(registry)

    fun jobDurationTimer(jobType: String): Timer =
        Timer.builder("dopashift.jobs.duration")
            .tag("type", jobType)
            .description("Duration of background job executions")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry)

    // === Authentication Metrics ===

    val authSuccessCounter: Counter = Counter.builder("dopashift.auth.total")
        .tag("result", "success")
        .description("Successful authentication attempts")
        .register(registry)

    val authFailureCounter: Counter = Counter.builder("dopashift.auth.total")
        .tag("result", "failure")
        .description("Failed authentication attempts")
        .register(registry)

    // === Active Users (approximation via recent API calls) ===

    private val activeUsers = AtomicLong(0L)

    init {
        Gauge.builder("dopashift.users.active", activeUsers) { it.get().toDouble() }
            .description("Approximate count of users active in the last 15 minutes")
            .register(registry)
    }

    // === Mutators ===

    fun updateSyncQueueDepth(depth: Long) {
        syncQueueDepth.set(depth)
    }

    fun updateActiveUsers(count: Long) {
        activeUsers.set(count)
    }
}
