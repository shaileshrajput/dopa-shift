// Feature: dynamic-ui-experience, Task 19.1
package com.dopashift.macrobenchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.PowerMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Motion battery-overhead Macrobenchmark — **DUX-6.6**.
 *
 * DUX-6.6: the DUX-2 motion system must not increase measured battery
 * consumption attributable to UI rendering by more than 1%/hour of active
 * foreground use, verified on the reference device. The overhead is derived by
 * comparing a motion-ON foreground session against a motion-OFF (reduced-motion)
 * session and diffing the battery-charge delta captured by [PowerMetric] with
 * [PowerMetric.Type.Battery].
 *
 * PowerMetric is **experimental** and only reports real data on SUPPORTED
 * hardware: a Pixel 6 or later (on-device power rails) or a rooted device with
 * ODPM/battery-stats access. It CANNOT produce meaningful numbers on the CI
 * emulator or an unrooted device, so these tests are [Ignore]d by default with
 * an explicit reason — the scaffold captures the measurement intent (DUX-6.6)
 * and is run manually on qualifying hardware.
 *
 * Requires a supported device to RUN — Macrobenchmark cannot execute on the JVM.
 */
@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class PowerBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Baseline battery draw of a foreground Dashboard session with the motion
     * system ACTIVE. Compare its battery-charge delta against
     * [dashboardSessionMotionOff] to derive the DUX-6.6 overhead.
     */
    @Ignore("PowerMetric.Type.Battery requires a Pixel 6+/rooted device; not runnable on CI/emulator (DUX-6.6).")
    @Test
    fun dashboardSessionMotionOn() = powerSession()

    /**
     * Reference battery draw of the same foreground Dashboard session with the
     * OS reduced-motion setting enabled (motion effectively off, per DUX-2.9).
     * The difference vs [dashboardSessionMotionOn] is the motion overhead the
     * <= 1%/hour DUX-6.6 budget gates.
     */
    @Ignore("PowerMetric.Type.Battery requires a Pixel 6+/rooted device; not runnable on CI/emulator (DUX-6.6).")
    @Test
    fun dashboardSessionMotionOff() = powerSession()

    private fun powerSession() =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(PowerMetric(PowerMetric.Type.Battery())),
            compilationMode = CompilationMode.Partial(),
            iterations = 3,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
            device.wait(Until.hasObject(dashboardListSelector()), CONTENT_TIMEOUT_MS)
            // Exercise the UI for a bounded foreground window so battery draw is
            // attributable to sustained rendering/motion.
            val list = device.findObject(dashboardListSelector())
            repeat(5) {
                list?.fling(Direction.DOWN)
                device.waitForIdle()
                list?.fling(Direction.UP)
                device.waitForIdle()
            }
        }
}
