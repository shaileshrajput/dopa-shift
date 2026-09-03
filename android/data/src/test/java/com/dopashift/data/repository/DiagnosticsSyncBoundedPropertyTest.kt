package com.dopashift.data.repository

import androidx.test.core.app.ApplicationProvider
import com.dopashift.data.local.dao.DiagnosticEventDao
import com.dopashift.data.local.entity.LocalDiagnosticEvent
import com.dopashift.domain.creation.DiagnosticEventType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.random.Random

// Feature: dashboard-quick-create, Property 18: Diagnostics Sync Is Aggregated and Bounded
/**
 * Property 18: Diagnostics Sync Is Aggregated and Bounded.
 *
 * Validates: Requirements 6.5 (DQC-6.5).
 *
 * The [DiagnosticsAggregator] must, when it syncs Dashboard quick-create diagnostics:
 *
 *  (a) **Counts only.** What leaves the device is a [DiagnosticsCountsPayload] — a
 *      `Map<String, Long>` of per-event-type counts. It must never carry raw records,
 *      correlation ids, timestamps, or entity values. Each iteration asserts the uploaded
 *      payload is exactly that map, that its keys are only [DiagnosticEventType] names
 *      (never a raw-record field name such as `correlationId`, `occurredAt`, `id`, or
 *      `entityType`), and structurally checks the payload type declares no field of the raw
 *      [LocalDiagnosticEvent] type.
 *
 *  (b) **≤ 10 KB.** For realistic event volumes the produced payload's
 *      [DiagnosticsCountsPayload.estimatedSizeBytes] is `<= MAX_PAYLOAD_BYTES`; and the size
 *      guard predicate the aggregator uses (`estimatedSizeBytes() > MAX_PAYLOAD_BYTES`) fires
 *      for any over-limit payload.
 *
 *  (c) **≤ 1× / 24 h.** If `lastDiagnosticsSyncAt` is within 24 h of `now`,
 *      [DiagnosticsAggregator.aggregateAndSync] returns [DiagnosticsSyncResult.SkippedWithin24h]
 *      and never invokes the uploader. If it is older than 24 h or null, a sync may occur and
 *      the window advances only on success.
 *
 * ## Test-seam decisions (recorded per the task)
 *
 * - [DiagnosticEventDao] and [DiagnosticsCountsUploader] are interfaces, so they use
 *   hand-written in-memory fakes ([FakeDiagnosticEventDao], [CapturingCountsUploader]).
 * - [QuickCreatePreferencesStore] is a `final` `@Singleton` class whose `lastDiagnosticsSyncAt`
 *   is backed by a Context-scoped Jetpack DataStore. It is neither an interface nor `open`, so
 *   it cannot be faked or subclassed in pure JVM, and a test-only seam/refactor was declared
 *   out of scope. The task's guidance ("check whether the data module has JVM unit tests with
 *   Robolectric ... test the aggregator by faking at the boundary you can") is followed here:
 *   this test drives the **real** store under Robolectric (which supplies an Android Context on
 *   the JVM) and controls `lastDiagnosticsSyncAt` through the store's public
 *   `setLastDiagnosticsSyncAt(...)` API — the exact boundary the 24 h gate reads.
 *
 * ## Framework note
 *
 * The task requested Kotest. The data module's unit-test classpath does not include Kotest
 * (see data/build.gradle.kts), matching the existing convention documented in the sibling
 * property tests ([AggregatedCounterSyncNoRawTelemetryPropertyTest],
 * [AggregatedCounterSyncConsentPropertyTest]), which are JUnit4 + Robolectric property tests.
 * Bringing Kotest under Robolectric requires `io.kotest.extensions:kotest-extensions-robolectric`,
 * whose transitive toolchain requirements cannot be provisioned in this build environment (it
 * demands a JetBrains JDK 21 toolchain download that fails offline). To stay consistent with the
 * module and avoid a fragile dependency, this Property-18 test follows the module's established
 * JUnit4 + Robolectric property-test pattern with >=200 randomized iterations per property
 * (design Testing Strategy: min 100 iterations), driven by [kotlin.random.Random].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DiagnosticsSyncBoundedPropertyTest {

    private companion object {
        const val ITERATIONS = 200
        const val FIXED_NOW = 1_700_000_000_000L
        val EVENT_TYPES = DiagnosticEventType.entries
        val EVENT_TYPE_NAMES = EVENT_TYPES.map { it.name }.toSet()

        // Raw-record field names that must NEVER appear as payload keys (counts-only contract).
        val RAW_RECORD_FIELD_NAMES = setOf("id", "correlationId", "entityType", "occurredAt", "eventType")
    }

    private lateinit var preferences: QuickCreatePreferencesStore

    @Before
    fun setUp() {
        preferences = QuickCreatePreferencesStore(ApplicationProvider.getApplicationContext())
    }

    private suspend fun setLastSync(value: Long?) = preferences.setLastDiagnosticsSyncAt(value)
    private suspend fun currentLastSync(): Long? = preferences.observe().first().lastDiagnosticsSyncAt

    private fun randomCounts(rnd: Random, allowZero: Boolean): Map<DiagnosticEventType, Int> {
        // Randomly include a subset of the fixed event types with realistic counts.
        return EVENT_TYPES.mapNotNull { type ->
            val include = rnd.nextBoolean()
            if (!include) return@mapNotNull null
            val count = if (allowZero) rnd.nextInt(0, 5001) else rnd.nextInt(1, 5001)
            type to count
        }.toMap()
    }

    // -----------------------------------------------------------------------------------------
    // (a) Counts-only: the synced payload is a Map<String,Long> of event-type counts — never
    //     raw records / correlation ids / timestamps.
    // -----------------------------------------------------------------------------------------
    @Test
    fun `synced payload is counts-only, keyed by event-type names`() = runBlocking {
        val rnd = Random(0xC0FFEE)
        repeat(ITERATIONS) {
            setLastSync(null) // gate open
            val seeded = randomCounts(rnd, allowZero = true)
            val dao = FakeDiagnosticEventDao().apply { seedCounts(seeded) }
            val uploader = CapturingCountsUploader()
            val aggregator = DiagnosticsAggregator(dao, preferences, uploader)

            // Pure roll-up shape is counts-only, one Long per present type.
            val payload = aggregator.aggregateCounts()
            val expected = seeded.filterValues { it > 0 }
                .mapKeys { it.key.name }
                .mapValues { it.value.toLong() }
            assertEquals("counts-only roll-up", expected, payload.eventTypeCounts)

            payload.eventTypeCounts.keys.forEach { key ->
                assertTrue("key '$key' is a DiagnosticEventType name", key in EVENT_TYPE_NAMES)
                assertFalse("key '$key' must not be a raw-record field", key in RAW_RECORD_FIELD_NAMES)
            }

            // The payload TYPE is structurally counts-only: no field is the raw event type.
            DiagnosticsCountsPayload::class.java.declaredFields.forEach { f ->
                assertFalse(
                    "DiagnosticsCountsPayload.${f.name} must not be a raw LocalDiagnosticEvent",
                    LocalDiagnosticEvent::class.java.isAssignableFrom(f.type)
                )
            }

            // End-to-end: when events exist the uploader receives exactly this counts map.
            if (expected.isNotEmpty()) {
                val result = aggregator.aggregateAndSync(now = FIXED_NOW)
                assertTrue("expected Synced but was $result", result is DiagnosticsSyncResult.Synced)
                assertEquals("exactly one upload", 1, uploader.uploaded.size)
                assertEquals("uploaded payload is the counts map", expected, uploader.uploaded.single().eventTypeCounts)
                uploader.uploaded.single().eventTypeCounts.keys.forEach { key ->
                    assertTrue("uploaded key '$key' is an event-type name", key in EVENT_TYPE_NAMES)
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // (b) Bounded — realistic volumes stay within 10 KB and sync.
    // -----------------------------------------------------------------------------------------
    @Test
    fun `realistic counts produce a payload within the 10 KB bound and sync`() = runBlocking {
        val rnd = Random(0xB0111D)
        repeat(ITERATIONS) {
            // Widest per-key encoding: max Long values across all four types.
            val counts = EVENT_TYPES
                .filter { rnd.nextBoolean() }
                .associateWith { rnd.nextLong(1, Long.MAX_VALUE) }

            val payload = DiagnosticsCountsPayload(eventTypeCounts = counts.mapKeys { it.key.name })
            assertTrue(
                "payload ${payload.estimatedSizeBytes()}B must be within ${DiagnosticsAggregator.MAX_PAYLOAD_BYTES}B",
                payload.estimatedSizeBytes() <= DiagnosticsAggregator.MAX_PAYLOAD_BYTES
            )

            if (counts.isNotEmpty()) {
                setLastSync(null) // gate open
                val dao = FakeDiagnosticEventDao().apply { seedCounts(counts.mapValues { 1 }) }
                val uploader = CapturingCountsUploader()
                val aggregator = DiagnosticsAggregator(dao, preferences, uploader)

                val result = aggregator.aggregateAndSync(now = FIXED_NOW)

                assertTrue("expected Synced but was $result", result is DiagnosticsSyncResult.Synced)
                assertEquals("exactly one upload", 1, uploader.uploaded.size)
                assertEquals(
                    "uploaded keys are the seeded types",
                    counts.keys.map { it.name }.toSet(),
                    uploader.uploaded.single().eventTypeCounts.keys
                )
                // The uploaded payload is itself within the bound.
                assertTrue(
                    "uploaded payload within bound",
                    uploader.uploaded.single().estimatedSizeBytes() <= DiagnosticsAggregator.MAX_PAYLOAD_BYTES
                )
            }
        }
    }

    /**
     * (b') The size-guard predicate the aggregator uses — `estimatedSizeBytes() > MAX_PAYLOAD_BYTES`
     * — fires for any over-limit payload.
     *
     * Reachability note: the production roll-up only emits per-type counts over the four fixed
     * [DiagnosticEventType] values, so a real counts-only payload is always far under 10 KB
     * (proven in `realistic counts ...`). The [DiagnosticsSyncResult.SkippedPayloadTooLarge]
     * branch is therefore a defensive invariant not reachable via `aggregateAndSync` without
     * changing production code (out of scope); we verify the predicate that guards it.
     */
    @Test
    fun `size guard predicate detects any over-limit payload`() {
        val rnd = Random(0x5124E)
        repeat(ITERATIONS) {
            val keyCount = rnd.nextInt(400, 901)
            val oversized = (0 until keyCount).associate { i ->
                "SYNTHETIC_EVENT_TYPE_$i" to (1_000_000_000_000L + i)
            }
            val payload = DiagnosticsCountsPayload(eventTypeCounts = oversized)
            assertTrue(
                "an oversized payload (${payload.estimatedSizeBytes()}B) must exceed the bound",
                payload.estimatedSizeBytes() > DiagnosticsAggregator.MAX_PAYLOAD_BYTES
            )
        }
    }

    // -----------------------------------------------------------------------------------------
    // (c) <= 1x / 24h gate.
    // -----------------------------------------------------------------------------------------
    @Test
    fun `a sync within the last 24h is skipped and does not upload`() = runBlocking {
        val rnd = Random(0x24C)
        repeat(ITERATIONS) {
            // delta in [0, 24h): lastSync is within the window => must skip.
            val delta = rnd.nextLong(0, DiagnosticsAggregator.TWENTY_FOUR_HOURS_MS)
            val lastSync = FIXED_NOW - delta
            setLastSync(lastSync)

            // Seed events so NothingToSync cannot mask a gate failure.
            val dao = FakeDiagnosticEventDao().apply {
                seedCounts(mapOf(DiagnosticEventType.ENTITY_CREATED to rnd.nextInt(1, 10)))
            }
            val uploader = CapturingCountsUploader()
            val aggregator = DiagnosticsAggregator(dao, preferences, uploader)

            val result = aggregator.aggregateAndSync(now = FIXED_NOW)

            assertEquals("within-24h must skip", DiagnosticsSyncResult.SkippedWithin24h, result)
            assertEquals("nothing uploaded", 0, uploader.uploaded.size)
            assertFalse("rows preserved on skip", dao.cleared)
            assertEquals("window not advanced", lastSync, currentLastSync())
        }
    }

    @Test
    fun `a sync older than 24h may proceed and advances the window on success`() = runBlocking {
        val rnd = Random(0x018)
        val maxDelta = 400L * 24 * 60 * 60 * 1000
        repeat(ITERATIONS) {
            // delta > 24h => lastSync is outside the window => sync allowed.
            val delta = rnd.nextLong(DiagnosticsAggregator.TWENTY_FOUR_HOURS_MS + 1, maxDelta)
            val lastSync = FIXED_NOW - delta
            setLastSync(lastSync)

            val dao = FakeDiagnosticEventDao().apply {
                seedCounts(mapOf(DiagnosticEventType.QUICK_CREATE_OPENED to rnd.nextInt(1, 10)))
            }
            val uploader = CapturingCountsUploader()
            val aggregator = DiagnosticsAggregator(dao, preferences, uploader)

            val result = aggregator.aggregateAndSync(now = FIXED_NOW)

            assertTrue("older-than-24h may sync but was $result", result is DiagnosticsSyncResult.Synced)
            assertEquals("exactly one upload", 1, uploader.uploaded.size)
            assertEquals("window advanced to now on success", FIXED_NOW, currentLastSync())
        }
    }

    @Test
    fun `a null last-sync (never synced) is not gated and may proceed`() = runBlocking {
        val rnd = Random(0x0FF)
        repeat(ITERATIONS) {
            setLastSync(null)
            val dao = FakeDiagnosticEventDao().apply {
                seedCounts(mapOf(DiagnosticEventType.UNDO_INVOKED to rnd.nextInt(1, 51)))
            }
            val uploader = CapturingCountsUploader()
            val aggregator = DiagnosticsAggregator(dao, preferences, uploader)

            val result = aggregator.aggregateAndSync(now = FIXED_NOW)

            assertTrue("null last-sync may sync but was $result", result is DiagnosticsSyncResult.Synced)
            assertEquals("exactly one upload", 1, uploader.uploaded.size)
            assertEquals("window advanced to now", FIXED_NOW, currentLastSync())
        }
    }

    // -----------------------------------------------------------------------------------------
    // In-memory fakes for the DAO and uploader ports.
    // -----------------------------------------------------------------------------------------

    /** In-memory [DiagnosticEventDao] mirroring the real DAO's count/clear semantics. */
    private class FakeDiagnosticEventDao : DiagnosticEventDao {
        private val rows = mutableListOf<LocalDiagnosticEvent>()

        /** True once [clear] has been called; lets tests assert rows are/aren't cleared. */
        var cleared: Boolean = false
            private set

        fun seedCounts(countsByType: Map<DiagnosticEventType, Int>, occurredAt: Long = 0L) {
            countsByType.forEach { (type, count) ->
                repeat(count) {
                    rows += LocalDiagnosticEvent(
                        id = UUID.randomUUID().toString(),
                        eventType = type.name,
                        correlationId = UUID.randomUUID().toString(),
                        entityType = null,
                        occurredAt = occurredAt
                    )
                }
            }
        }

        override suspend fun upsert(event: LocalDiagnosticEvent) {
            rows.removeAll { it.id == event.id }
            rows += event
        }

        override suspend fun getAll(): List<LocalDiagnosticEvent> = rows.sortedBy { it.occurredAt }

        override suspend fun countByType(eventType: String): Int = rows.count { it.eventType == eventType }

        override suspend fun deleteById(id: String) {
            rows.removeAll { it.id == id }
        }

        override suspend fun clear() {
            cleared = true
            rows.clear()
        }
    }

    /** Capturing [DiagnosticsCountsUploader]: records every payload it is asked to transmit. */
    private class CapturingCountsUploader : DiagnosticsCountsUploader {
        val uploaded = mutableListOf<DiagnosticsCountsPayload>()
        override suspend fun upload(payload: DiagnosticsCountsPayload) {
            uploaded += payload
        }
    }
}
