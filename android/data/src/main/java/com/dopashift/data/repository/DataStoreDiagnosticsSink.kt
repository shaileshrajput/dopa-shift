package com.dopashift.data.repository

import com.dopashift.data.local.dao.DiagnosticEventDao
import com.dopashift.data.local.entity.LocalDiagnosticEvent
import com.dopashift.domain.creation.CorrelationId
import com.dopashift.domain.creation.DiagnosticEventType
import com.dopashift.domain.creation.DiagnosticsEventSink
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `data`-module implementation of the [DiagnosticsEventSink] domain port (DQC-6.4).
 *
 * Records structured local diagnostic events — each carrying a machine-parseable
 * [DiagnosticEventType] and the originating [CorrelationId] — into the local-only
 * `quick_create_diagnostics` Room table via [DiagnosticEventDao].
 *
 * Raw records are LOCAL ONLY and never synced: this sink only ever writes rows. The
 * counts-only aggregation and (bounded) transmission are the sole responsibility of
 * [DiagnosticsAggregator] (DQC-6.5). No secrets, tokens, or PII are persisted — only the
 * event type, correlation id, and an optional non-sensitive `entityType` from [meta].
 */
@Singleton
class DataStoreDiagnosticsSink @Inject constructor(
    private val diagnosticEventDao: DiagnosticEventDao
) : DiagnosticsEventSink {

    override suspend fun record(
        event: DiagnosticEventType,
        correlationId: CorrelationId,
        meta: Map<String, String>
    ) {
        diagnosticEventDao.upsert(
            LocalDiagnosticEvent(
                id = UUID.randomUUID().toString(),
                eventType = event.name,
                correlationId = correlationId.value,
                // Only a coarse, non-sensitive entity type is retained (GOAL | HABIT | TODO).
                // Never any user-supplied content, secrets, tokens, or PII.
                entityType = meta[META_ENTITY_TYPE],
                occurredAt = System.currentTimeMillis()
            )
        )
    }

    companion object {
        /** Optional [record] meta key naming the entity type involved (GOAL | HABIT | TODO). */
        const val META_ENTITY_TYPE = "entityType"
    }
}
