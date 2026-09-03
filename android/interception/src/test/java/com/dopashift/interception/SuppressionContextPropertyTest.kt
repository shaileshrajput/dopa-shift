package com.dopashift.interception

// Feature: screen-time-interception-engine, Property 19

import com.dopashift.interception.SuppressionContextDetector.SuppressionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

/**
 * Property 19: Never present the Intercept_Screen during a Suppression_Context.
 *
 * For any combination of active safety-critical signals (active call, emergency dialer,
 * ringing alarm/timer, active navigation), whenever at least one signal is active the
 * [SuppressionContextDetector.isSuppressed] result MUST be a non-[SuppressionResult.None]
 * value with `isSuppressing == true` — so the engine never transitions to Presenting while
 * suppression is active. When no signal is active it MUST return [SuppressionResult.None].
 *
 * **Validates: Requirements 5.2**
 *
 * Property-based test following existing module conventions (JUnit4 + randomized iterations,
 * as in [AllowanceDepletionPropertyTest] / [OverlayEngagementPropertyTest]); Kotest is not on
 * this module's test classpath, so the established `repeat(N)` randomized-input style is used.
 * A configurable [FakeSuppressionSignalSource] stands in for the real Android system services.
 */
class SuppressionContextPropertyTest {

    /**
     * Fake [SuppressionSignalSource] whose four signals are independently configurable so the
     * detection logic can be exercised deterministically without any Android runtime deps.
     */
    private class FakeSuppressionSignalSource(
        var callActive: Boolean = false,
        var emergencyDialerActive: Boolean = false,
        var alarmRinging: Boolean = false,
        var navigationActive: Boolean = false
    ) : SuppressionSignalSource {
        override fun isCallActive(): Boolean = callActive
        override fun isEmergencyDialerActive(): Boolean = emergencyDialerActive
        override fun isAlarmRinging(): Boolean = alarmRinging
        override fun isNavigationActive(): Boolean = navigationActive
    }

    /**
     * Property: whenever at least one signal is active, isSuppressed returns a non-None,
     * suppressing result; when none are active it returns None.
     *
     * Exhaustively exercises all 16 signal combinations, then repeats with randomized instants
     * across ≥100 iterations to confirm the outcome is signal-driven and time-independent.
     */
    @Test
    fun `property - any active signal suppresses and no active signal does not`() {
        val random = Random(seed = 519)

        repeat(160) { iteration ->
            // Cover all 16 combinations deterministically (bits 0..3), cycling as iterations grow.
            val bits = iteration % 16
            val call = bits and 0b0001 != 0
            val emergency = bits and 0b0010 != 0
            val alarm = bits and 0b0100 != 0
            val navigation = bits and 0b1000 != 0

            val source = FakeSuppressionSignalSource(
                callActive = call,
                emergencyDialerActive = emergency,
                alarmRinging = alarm,
                navigationActive = navigation
            )
            val detector = SuppressionContextDetector(source)

            // `now` should not influence the outcome — use a randomized instant each iteration.
            val now = Instant.ofEpochSecond(random.nextLong(0, 4_000_000_000L))
            val result = detector.isSuppressed(now)

            val anyActive = call || emergency || alarm || navigation

            if (anyActive) {
                assertTrue(
                    "Iteration $iteration (call=$call, emergency=$emergency, alarm=$alarm, " +
                        "nav=$navigation): expected a suppressing result but got $result",
                    result.isSuppressing
                )
                assertFalse(
                    "Iteration $iteration: a suppressing context must never be reported as None",
                    result == SuppressionResult.None
                )
            } else {
                assertEquals(
                    "Iteration $iteration: no active signal must yield None",
                    SuppressionResult.None,
                    result
                )
                assertFalse(
                    "Iteration $iteration: None must never report isSuppressing == true",
                    result.isSuppressing
                )
            }
        }
    }

    /**
     * Property: for any single active signal, the detector reports the corresponding named
     * Suppression_Context, and it is always suppressing (never None). This pins the invariant
     * that each individual safety-critical signal on its own blocks presentation.
     */
    @Test
    fun `property - each individual signal on its own suppresses with a named context`() {
        val random = Random(seed = 777)

        repeat(100) { iteration ->
            when (iteration % 4) {
                0 -> {
                    val detector = SuppressionContextDetector(
                        FakeSuppressionSignalSource(callActive = true)
                    )
                    val result = detector.isSuppressed(randomInstant(random))
                    assertEquals(SuppressionResult.ActiveCall, result)
                    assertTrue(result.isSuppressing)
                }
                1 -> {
                    val detector = SuppressionContextDetector(
                        FakeSuppressionSignalSource(emergencyDialerActive = true)
                    )
                    val result = detector.isSuppressed(randomInstant(random))
                    assertEquals(SuppressionResult.EmergencyDialer, result)
                    assertTrue(result.isSuppressing)
                }
                2 -> {
                    val detector = SuppressionContextDetector(
                        FakeSuppressionSignalSource(alarmRinging = true)
                    )
                    val result = detector.isSuppressed(randomInstant(random))
                    assertEquals(SuppressionResult.RingingAlarm, result)
                    assertTrue(result.isSuppressing)
                }
                else -> {
                    val detector = SuppressionContextDetector(
                        FakeSuppressionSignalSource(navigationActive = true)
                    )
                    val result = detector.isSuppressed(randomInstant(random))
                    assertEquals(SuppressionResult.ActiveNavigation, result)
                    assertTrue(result.isSuppressing)
                }
            }
        }
    }

    /**
     * Property: with several signals active at once, the detector still reports a suppressing
     * result (never None) regardless of which combination or how many are set.
     */
    @Test
    fun `property - overlapping active signals never yield None`() {
        val random = Random(seed = 4242)

        repeat(100) { iteration ->
            // Force at least one signal to be active by OR-ing in a guaranteed-set bit.
            val call = random.nextBoolean()
            val emergency = random.nextBoolean()
            val alarm = random.nextBoolean()
            var navigation = random.nextBoolean()
            if (!(call || emergency || alarm || navigation)) {
                navigation = true // guarantee at least one active signal for this property
            }

            val detector = SuppressionContextDetector(
                FakeSuppressionSignalSource(
                    callActive = call,
                    emergencyDialerActive = emergency,
                    alarmRinging = alarm,
                    navigationActive = navigation
                )
            )

            val result = detector.isSuppressed(randomInstant(random))

            assertTrue(
                "Iteration $iteration (call=$call, emergency=$emergency, alarm=$alarm, " +
                    "nav=$navigation): at least one active signal must suppress",
                result.isSuppressing
            )
            assertFalse(
                "Iteration $iteration: overlapping active signals must never report None",
                result == SuppressionResult.None
            )
        }
    }

    private fun randomInstant(random: Random): Instant =
        Instant.ofEpochSecond(random.nextLong(0, 4_000_000_000L))
}
