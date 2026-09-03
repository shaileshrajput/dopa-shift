package com.dopashift.data.repository

// Feature: dashboard-quick-create, Property 17: One Origin Correlation_ID and Correct Event Type per Action

import com.dopashift.data.local.dao.DiagnosticEventDao
import com.dopashift.data.local.entity.LocalDiagnosticEvent
import com.dopashift.domain.creation.CorrelationId
import com.dopashift.domain.creation.DiagnosticEventType
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.arbitrary.uuid
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Property-based test for [DataStoreDiagnosticsSink] correlation-id + event-type propagation.
 *
 * **Feature: dashboard-quick-create, Property 17: One Origin Correlation_ID and Correct Event Type per Action**
 *
 * **Validates: Requirements 6.3, 6.4**
 *
 * For any Dashboard creation action, every local diagnostic write for that action carries the
 * single [CorrelationId] generated at the action's origin; and for each of the four action kinds
 * (opened, created, abandoned, undo) the persisted row carries the matching machine-parseable
 * event-type field.
 *
 * This is a plain JVM unit test (data module `src/test`) using a hand-written in-memory
 * [DiagnosticEventDao] fake, so no Android emulator is required. Properties are exercised with
 * Kotest's [checkAll] property engine at 200 generated cases each (>= 100 per the design Testing
 * Strategy). It is hosted in a JUnit4 test class — matching the data module's discovery
 * convention — because AGP's Android unit-test task discovers JUnit tests, while Kotest spec
 * styles (StringSpec/FunSpec) are not auto-discovered here; `checkAll` still provides the full
 * property-based generation and shrinking.
 */
@OptIn(ExperimentalKotest::class)
class DiagnosticsSinkCorrelationIdPropertyTest {

    // >= 100 generated cases per property (design Testing Strategy: min 100 iterations).
    private val config = PropTestConfig(iterations = 200)

    // A CorrelationId generated at an action's origin. Non-blank per the value class invariant.
    private val correlationIds = Arb.uuid().map { CorrelationId(it.toString()) }
    private val eventTypes = Arb.enum<DiagnosticEventType>()

    // A non-blank entity type sometimes supplied in meta (GOAL | HABIT | TODO, or arbitrary).
    private val entityTypes = Arb.string(minSize = 1, maxSize = 8)

    @Test
    fun `a recorded event persists exactly one row with the supplied event type and correlation id`() =
        runTest {
            checkAll(config, eventTypes, correlationIds) { event, correlationId ->
                val dao = FakeDiagnosticEventDao()
                val sink = DataStoreDiagnosticsSink(dao)

                sink.record(event, correlationId, emptyMap())

                val rows = dao.getAll()
                assertEquals("exactly one row persisted", 1, rows.size)
                assertEquals("event type matches", event.name, rows.single().eventType)
                assertEquals(
                    "correlation id matches the origin id",
                    correlationId.value,
                    rows.single().correlationId
                )
            }
        }

    @Test
    fun `the supplied entityType meta is persisted verbatim`() = runTest {
        checkAll(config, eventTypes, correlationIds, entityTypes) { event, correlationId, entityType ->
            val dao = FakeDiagnosticEventDao()
            val sink = DataStoreDiagnosticsSink(dao)

            sink.record(
                event,
                correlationId,
                mapOf(DataStoreDiagnosticsSink.META_ENTITY_TYPE to entityType)
            )

            assertEquals(entityType, dao.getAll().single().entityType)
        }
    }

    @Test
    fun `when no entityType meta is supplied the persisted entityType is null`() = runTest {
        checkAll(config, eventTypes, correlationIds) { event, correlationId ->
            val dao = FakeDiagnosticEventDao()
            val sink = DataStoreDiagnosticsSink(dao)

            sink.record(event, correlationId, emptyMap())

            assertEquals(null, dao.getAll().single().entityType)
        }
    }

    @Test
    fun `a sequence of events sharing one origin correlation id all persist that id with their own correct event types`() =
        runTest {
            checkAll(config, correlationIds, Arb.list(eventTypes, 1..12)) { originId, events ->
                val dao = FakeDiagnosticEventDao()
                val sink = DataStoreDiagnosticsSink(dao)

                // A single Dashboard action emits multiple diagnostic events (e.g.
                // QUICK_CREATE_OPENED then ENTITY_CREATED), all under the ONE correlation id
                // minted at the action's origin.
                events.forEach { event -> sink.record(event, originId, emptyMap()) }

                val rows = dao.getAll()
                assertEquals("one row per recorded event", events.size, rows.size)

                // Every persisted row carries the single origin correlation id (DQC-6.3).
                assertTrue(
                    "all rows share the one origin correlation id",
                    rows.all { it.correlationId == originId.value }
                )

                // Each recorded event's type is persisted with the matching, machine-parseable
                // event-type field, in the order emitted (DQC-6.4).
                assertEquals(events.map { it.name }, rows.map { it.eventType })
            }
        }
}

/**
 * In-memory fake of [DiagnosticEventDao] for JVM unit tests — no Room / Android emulator needed.
 * Preserves insertion order for `getAll()` (the real DAO orders by `occurredAt ASC`).
 */
private class FakeDiagnosticEventDao : DiagnosticEventDao {
    private val events = mutableListOf<LocalDiagnosticEvent>()

    override suspend fun upsert(event: LocalDiagnosticEvent) {
        events.removeAll { it.id == event.id }
        events.add(event)
    }

    override suspend fun getAll(): List<LocalDiagnosticEvent> = events.toList()

    override suspend fun countByType(eventType: String): Int =
        events.count { it.eventType == eventType }

    override suspend fun deleteById(id: String) {
        events.removeAll { it.id == id }
    }

    override suspend fun clear() {
        events.clear()
    }
}
