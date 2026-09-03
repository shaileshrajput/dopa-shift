package com.dopashift.domain.creation

/**
 * Records structured local diagnostic events for Dashboard creation flows (DQC-6.4).
 *
 * Every event carries a machine-parseable [DiagnosticEventType] and the originating
 * [CorrelationId] so an action can be traced end to end. Raw records are stored
 * locally and never synced; only aggregated per-type counts leave the device
 * (≤10 KB, ≤1×/24 h — DQC-6.5). No secrets, tokens, or PII appear in [meta].
 *
 * Declared in `domain` as a pure-Kotlin port; the local-store implementation lives in
 * the `data` module (`DataStoreDiagnosticsSink`).
 */
interface DiagnosticsEventSink {

    /**
     * Records a single structured local diagnostic event.
     *
     * @param event the machine-parseable event type.
     * @param correlationId the [CorrelationId] generated at the action's origin.
     * @param meta optional non-sensitive metadata (e.g. entity type); never PII or secrets.
     */
    suspend fun record(
        event: DiagnosticEventType,
        correlationId: CorrelationId,
        meta: Map<String, String> = emptyMap()
    )
}
