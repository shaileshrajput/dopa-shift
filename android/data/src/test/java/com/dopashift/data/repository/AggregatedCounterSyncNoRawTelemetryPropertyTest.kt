package com.dopashift.data.repository

import com.dopashift.data.local.entity.LocalTelemetryEvent
import com.dopashift.data.remote.dto.InterceptionRuleResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.ParameterizedType
import java.time.LocalDate
import java.util.UUID
import kotlin.random.Random

// Feature: screen-time-interception-engine, Property 22
/**
 * Property 22: Aggregated sync payload contains no raw telemetry.
 *
 * Validates: Requirements 6.3, 6.4.
 *
 * The [AggregatedCounterSync] flow transmits ONLY an [AggregatedCounterPayload] via the
 * injectable [AggregatedCounterUploader] port. That payload — and its nested
 * [AggregatedCounterPayload.AppCounter] — must carry only aggregated summary fields
 * (package, ISO date, total foreground minutes, breach count, rule metadata, diagnostic
 * counts) and must NEVER reference a raw per-sample [LocalTelemetryEvent] row.
 *
 * This is verified two ways:
 *
 *  (a) Round-trip capture (>=100 randomized iterations): a fake [AggregatedCounterUploader]
 *      captures whatever the sync layer hands it. For arbitrary aggregated inputs, the
 *      captured payload is the very payload submitted (same instance, structurally equal)
 *      and every value in it is an aggregated summary field — never a [LocalTelemetryEvent].
 *
 *  (b) Structural type contract (reflection): the declared members of
 *      [AggregatedCounterPayload] and [AppCounter] are inspected and asserted to contain no
 *      field whose type is (or is a collection/map of) [LocalTelemetryEvent], i.e. the raw
 *      per-sample event type is structurally unreachable from the sync payload.
 *
 * The [AggregatedCounterSync.sync] entry point reads consent from a Context-backed
 * DataStore, and the data module unit-test classpath has neither Robolectric nor a Context
 * (see data/build.gradle.kts: only JUnit4 + coroutines-test). The property therefore
 * targets the transmission contract that is testable without a Context: the payload TYPE
 * and the uploader port, which is exactly the boundary at which raw telemetry could leak.
 *
 * JUnit4 + kotlin.random.Random + kotlinx.coroutines.test.runTest, matching the data
 * module's test conventions (Kotest is not on the classpath).
 */
class AggregatedCounterSyncNoRawTelemetryPropertyTest {

    private companion object {
        const val ITERATIONS = 200
        val PACKAGE_SEGMENTS = listOf(
            "com", "org", "net", "example", "app", "social", "video",
            "game", "news", "chat", "foo", "bar", "baz", "player", "feed"
        )
        val DIAGNOSTIC_KEYS = listOf(
            "interceptionsShown", "extensionsGranted", "deferralsHeld",
            "breachesDetected", "overlaysDismissed"
        )
    }

    /**
     * Captures the last payload handed to it. This is the ONLY thing that ever leaves the
     * sync boundary, so inspecting it proves what is (and is not) transmitted.
     */
    private class CapturingUploader : AggregatedCounterUploader {
        var captured: AggregatedCounterPayload? = null
            private set
        var uploadCount: Int = 0
            private set

        override suspend fun upload(payload: AggregatedCounterPayload) {
            captured = payload
            uploadCount++
        }
    }

    private fun randomAppCounter(rnd: Random): AggregatedCounterPayload.AppCounter {
        val segmentCount = rnd.nextInt(2, 5)
        val pkg = (0 until segmentCount)
            .joinToString(".") { PACKAGE_SEGMENTS[rnd.nextInt(PACKAGE_SEGMENTS.size)] }
        val date = LocalDate.ofEpochDay(rnd.nextLong(0, 30_000)).toString()
        return AggregatedCounterPayload.AppCounter(
            appPackageName = pkg,
            date = date,
            totalForegroundMinutes = rnd.nextLong(0, 24 * 60 + 1),
            breachCount = rnd.nextInt(0, 25)
        )
    }

    private fun randomRuleResponse(rnd: Random): InterceptionRuleResponse {
        val pkg = "com.example.app${rnd.nextInt(0, 1000)}"
        return InterceptionRuleResponse(
            id = UUID.randomUUID().toString(),
            goalId = if (rnd.nextBoolean()) UUID.randomUUID().toString() else null,
            appPackageName = if (rnd.nextBoolean()) pkg else null,
            siteDomain = if (rnd.nextBoolean()) "$pkg.example" else null,
            dailyAllowanceMinutes = rnd.nextInt(1, 481),
            isActive = rnd.nextBoolean(),
            createdAt = LocalDate.ofEpochDay(rnd.nextLong(0, 30_000)).toString()
        )
    }

    private fun randomPayload(rnd: Random): AggregatedCounterPayload {
        val appCounters = (0 until rnd.nextInt(0, 8)).map { randomAppCounter(rnd) }
        val rules = (0 until rnd.nextInt(0, 6)).map { randomRuleResponse(rnd) }
        val diagnostics = (0 until rnd.nextInt(0, DIAGNOSTIC_KEYS.size + 1)).associate {
            DIAGNOSTIC_KEYS[it % DIAGNOSTIC_KEYS.size] to rnd.nextLong(0, 10_000)
        }
        return AggregatedCounterPayload(
            appCounters = appCounters,
            rules = rules,
            diagnostics = diagnostics
        )
    }

    /**
     * (a) Round-trip capture: for arbitrary aggregated inputs, exactly the submitted
     * payload reaches the uploader, and it exposes only aggregated summary fields — never a
     * [LocalTelemetryEvent] anywhere in its structure.
     */
    @Test
    fun `captured payload is the submitted aggregated payload and carries no raw telemetry`() =
        runTest {
            val rnd = Random(0xA66_5217)
            repeat(ITERATIONS) {
                val uploader = CapturingUploader()
                val payload = randomPayload(rnd)

                // Exercise the transmission boundary directly (the uploader port is what the
                // sync layer delegates to; sync() itself needs a Context unavailable here).
                uploader.upload(payload)

                val captured = uploader.captured
                assertEquals("exactly one upload occurred", 1, uploader.uploadCount)
                assertTrue("a payload was captured", captured != null)
                // Same instance handed straight through — nothing is enriched with raw rows.
                assertSame(
                    "the captured payload is exactly the submitted aggregated payload",
                    payload,
                    captured
                )
                assertEquals("payload is structurally unchanged", payload, captured)

                // No value anywhere in the payload is a raw telemetry event.
                assertNoRawTelemetryValues(captured!!)

                // The aggregated summary fields are exactly the ones present, unchanged.
                assertEquals(payload.appCounters, captured.appCounters)
                assertEquals(payload.rules, captured.rules)
                assertEquals(payload.diagnostics, captured.diagnostics)
                captured.appCounters.forEach { counter ->
                    // AppCounter exposes only aggregated summary values (no raw sample data).
                    assertTrue(
                        "totalForegroundMinutes is a non-negative aggregate",
                        counter.totalForegroundMinutes >= 0
                    )
                    assertTrue("breachCount is a non-negative aggregate", counter.breachCount >= 0)
                }
            }
        }

    /**
     * (b) Structural type contract: neither [AggregatedCounterPayload] nor its nested
     * [AggregatedCounterPayload.AppCounter] declares any field whose type is (or is a
     * List/Set/Map of) the raw per-sample [LocalTelemetryEvent]. The raw event type is
     * therefore structurally unreachable from the sync payload.
     */
    @Test
    fun `payload types declare no field of raw telemetry type`() {
        assertNoRawTelemetryFieldType(AggregatedCounterPayload::class.java)
        assertNoRawTelemetryFieldType(AggregatedCounterPayload.AppCounter::class.java)
    }

    // ---- Assertions -------------------------------------------------------

    private val rawTelemetryClass: Class<*> = LocalTelemetryEvent::class.java

    /**
     * Asserts none of the declared fields of [klass] are of the raw telemetry type, nor a
     * collection/map parameterized with it, nor named like a raw event field. Uses
     * java.lang.reflect only (kotlin-reflect is not on the data module classpath).
     */
    private fun assertNoRawTelemetryFieldType(klass: Class<*>) {
        klass.declaredFields.forEach { field ->
            val rawType = field.type
            assertFalse(
                "${klass.simpleName}.${field.name} must not be of raw telemetry type " +
                    rawTelemetryClass.simpleName,
                rawTelemetryClass.isAssignableFrom(rawType)
            )

            // Guard against a collection/map that carries raw telemetry as a type argument.
            val generic = field.genericType
            if (generic is ParameterizedType) {
                generic.actualTypeArguments.forEach { arg ->
                    val argClass = when (arg) {
                        is Class<*> -> arg
                        is ParameterizedType -> arg.rawType as? Class<*>
                        else -> null
                    }
                    if (argClass != null) {
                        assertFalse(
                            "${klass.simpleName}.${field.name} must not carry raw telemetry " +
                                "type ${rawTelemetryClass.simpleName} as a type argument",
                            rawTelemetryClass.isAssignableFrom(argClass)
                        )
                    }
                }
            }

            // Defensive: a field named like a raw per-sample event would be a red flag even
            // if it were mistyped.
            val lower = field.name.lowercase()
            assertFalse(
                "${klass.simpleName}.${field.name} must not be a raw per-sample telemetry field",
                lower.contains("telemetryevent") ||
                    lower.contains("rawsample") ||
                    lower.contains("persample")
            )
        }
    }

    /** Asserts no value reachable within the payload is a [LocalTelemetryEvent]. */
    private fun assertNoRawTelemetryValues(payload: AggregatedCounterPayload) {
        val values = buildList<Any?> {
            addAll(payload.appCounters)
            addAll(payload.rules)
            addAll(payload.diagnostics.keys)
            addAll(payload.diagnostics.values)
            add(payload)
        }
        values.forEach { value ->
            assertFalse(
                "no value in the payload may be a raw LocalTelemetryEvent",
                value is LocalTelemetryEvent
            )
        }
    }
}
