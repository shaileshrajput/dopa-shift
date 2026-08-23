package com.dopashift.api.observability

import io.micrometer.observation.ObservationRegistry
import io.micrometer.observation.aop.ObservedAspect
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration for OpenTelemetry distributed tracing via Micrometer Tracing bridge.
 *
 * Requirements: 13.5, 18.11
 * - Configures OpenTelemetry SDK for Spring Boot.
 * - Propagates trace IDs across API requests and background jobs.
 * - Embeds correlation ID as trace attribute.
 *
 * Spring Boot 3.3+ auto-configures Micrometer Tracing with OTel bridge
 * when the dependencies (micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp)
 * are on the classpath. This config class provides additional customization.
 */
@Configuration
class TracingConfig {

    /**
     * Enables the @Observed annotation for method-level tracing spans.
     * Methods annotated with @Observed will automatically create spans.
     */
    @Bean
    fun observedAspect(observationRegistry: ObservationRegistry): ObservedAspect {
        return ObservedAspect(observationRegistry)
    }
}
