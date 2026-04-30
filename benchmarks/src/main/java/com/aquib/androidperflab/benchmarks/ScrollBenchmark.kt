package com.aquib.androidperflab.benchmarks

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE    = "com.aquib.androidperflab"
private const val RENDER_TIMEOUT_MS = 5_000L

/**
 * Measures scroll rendering performance on the unoptimized AnimatedListScreen.
 *
 * Metrics reported per iteration (written to benchmarkData.json):
 *   frameDurationCpuMs  — p50 / p90 / p95 / p99 frame durations
 *   frameOverrunMs      — per-frame budget overrun (devices with HW timestamps only)
 *   jankyFrameCount     — number of frames exceeding the 16 ms / 60 fps deadline
 *   jankyFramePercent   — janky frames as a fraction of total frames rendered
 *
 * The AnimatedListScreen is intentionally unoptimized:
 *   - No key{} lambda in LazyColumn.items() → position-based diffing on recomposition
 *   - Alpha state read in composition scope, not inside graphicsLayer → full recompose per frame
 *   - animateContentSize() on every card → expensive layout on every frame
 *   - Inline Color construction per recomposition → allocation pressure
 *
 * Run:
 *   ./gradlew :benchmarks:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.aquib.androidperflab.benchmarks.ScrollBenchmark
 *
 * Output:
 *   benchmarks/build/outputs/connected_android_test_additional_output/
 *     benchmark/connected/<device>/ScrollBenchmark-benchmarkData.json
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Cold-starts the app for each of 5 iterations, navigates to AnimatedListScreen
     * in the un-measured setupBlock, then scrolls the 80-item LazyColumn 5 pages down
     * and 5 pages up while FrameTimingMetric records every frame.
     *
     * CompilationMode.None() keeps the JVM in JIT-only mode so results reflect the
     * worst-case unoptimized baseline (no AOT profile warm-up across iterations).
     */
    @Test
    fun scrollAnimatedListUnoptimized() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                
                // Find FAB by content description
                val fab = device.wait(Until.findObject(By.desc("animated_list_fab")), RENDER_TIMEOUT_MS)
                    ?: throw RuntimeException("Could not find the FAB to navigate to Animated List")
                fab.click()
                
                // Wait for the list to be present on screen before starting measurement
                val listAppeared = device.wait(Until.hasObject(By.desc("animated_list")), RENDER_TIMEOUT_MS)
                check(listAppeared) { "AnimatedListScreen did not appear within ${RENDER_TIMEOUT_MS}ms" }
            },
            measureBlock = {
                // Guard against any residual render delay from the heavy 80-item
                // infinite-animation composition before attempting to find the node.
                device.wait(Until.hasObject(By.desc("animated_list")), RENDER_TIMEOUT_MS)

                // Find the list by content description
                val list = device.findObject(By.desc("animated_list"))
                    ?: throw RuntimeException("Could not find the animated list scrollable object")

                repeat(5) { list.scroll(Direction.DOWN, 1.0f) }
                repeat(5) { list.scroll(Direction.UP, 1.0f) }
            },
        )
    }
}
