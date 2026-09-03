// Feature: dynamic-ui-experience, Task 19.1
package com.dopashift.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dashboard scroll-jank Macrobenchmark — **DUX-2.2**.
 *
 * DUX-2.2: the app sustains 60 fps / the device's native refresh rate during
 * scrolling, with dropped frames not exceeding 1% of total frames over a
 * standardized Dashboard-scroll benchmark. This benchmark captures
 * [FrameTimingMetric] (frameDurationCpuMs / frameOverrunMs percentiles) while
 * flinging the Dashboard list, so the jank budget can be gated in CI against a
 * regression threshold (DUX-7.5).
 *
 * Requires a device/emulator to RUN — Macrobenchmark cannot execute on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollDashboardPartialCompilation() =
        scroll(CompilationMode.Partial())

    @Test
    fun scrollDashboardNoCompilation() = scroll(CompilationMode.None())

    private fun scroll(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = 5,
            // Warm start keeps the launch out of the measured frame window so the
            // metric reflects scrolling frames, not startup.
            startupMode = StartupMode.WARM,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
        ) {
            // Locate the scrollable Dashboard list and fling it a few times to
            // capture representative frame timings under sustained scroll.
            device.wait(Until.hasObject(dashboardListSelector()), CONTENT_TIMEOUT_MS)
            val list = device.findObject(dashboardListSelector()) ?: return@measureRepeated
            // Constrain the gesture to the list bounds and slow the fling
            // margin so scroll is smooth and repeatable across runs.
            list.setGestureMargin(device.displayWidth / 5)
            repeat(3) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
            repeat(3) {
                list.fling(Direction.UP)
                device.waitForIdle()
            }
        }
}
