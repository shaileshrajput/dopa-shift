// Feature: dynamic-ui-experience, Task 19.1
package com.dopashift.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start Macrobenchmark for the Dashboard — **DUX-6.1**.
 *
 * DUX-6.1: the Dashboard's first meaningful frame renders within 1000 ms of a
 * COLD start on the reference device, using cached local data. This benchmark
 * captures [StartupTimingMetric] (timeToInitialDisplay / timeToFullDisplay)
 * over [StartupMode.COLD] launches so the < 1000 ms budget can be gated in CI
 * against a regression threshold (DUX-7.5).
 *
 * Requires a device/emulator to RUN — Macrobenchmark cannot execute on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun coldStartupPartialCompilation() =
        startup(CompilationMode.Partial())

    private fun startup(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            // >= 3 iterations per task requirement for a stable cold-start median.
            iterations = 5,
            startupMode = StartupMode.COLD,
            setupBlock = {
                pressHome()
            },
        ) {
            startActivityAndWait()
            // Wait for the Dashboard's first meaningful content (its scrollable
            // list) so the measured window reflects cached-data render, not just
            // process fork.
            device.wait(Until.hasObject(dashboardListSelector()), CONTENT_TIMEOUT_MS)
        }
}
