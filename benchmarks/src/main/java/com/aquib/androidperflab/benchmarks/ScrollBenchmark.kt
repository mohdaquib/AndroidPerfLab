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
private const val FRAME_P99_MAX_MS  = 16.0   // enforced by BenchmarkResultsParser.py

/**
 * Measures scroll rendering performance — before and after Compose optimisations.
 * Both tests run in a single benchmark session and their results appear side by side
 * in benchmarkData.json, keyed by test name.
 *
 * ┌──────────────────────────────────┬─────────────────────────────────────────────────────┐
 * │ Test                             │ Compose problems                                    │
 * ├──────────────────────────────────┼─────────────────────────────────────────────────────┤
 * │ scrollAnimatedList_unoptimized   │ no key{}, alpha in composition scope, animateContent │
 * │                                  │ Size(), inline Color per recompose → janky 60-fps    │
 * ├──────────────────────────────────┼─────────────────────────────────────────────────────┤
 * │ scrollAnimatedList_optimized     │ stable key, graphicsLayer alpha, layout-phase anim,  │
 * │                                  │ remembered Color → p99 must be < 16 ms              │
 * └──────────────────────────────────┴─────────────────────────────────────────────────────┘
 *
 * Metrics reported per iteration (written to benchmarkData.json):
 *   frameDurationCpuMs  — p50 / p90 / p95 / p99 frame durations
 *   frameOverrunMs      — per-frame budget overrun (devices with HW timestamps only)
 *   jankyFrameCount     — number of frames exceeding the 16 ms / 60 fps deadline
 *   jankyFramePercent   — janky frames as a fraction of total frames rendered
 *
 * The p99 frame duration threshold ([FRAME_P99_MAX_MS] ms) is enforced for the optimised
 * test by BenchmarkResultsParser.py, which exits non-zero and fails CI if exceeded.
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
     * Measures UnoptimizedAnimatedListScreen — four intentional Compose anti-patterns:
     *  1. No key{} lambda → position-based node reuse on scroll
     *  2. Alpha read via `by` in composition scope → full recompose every 16 ms
     *  3. animateContentSize() combined with per-frame recomposition → extra layout passes
     *  4. Inline Color() per recompose → sustained allocation pressure
     *
     * High jankyFrameCount and p99 here confirm the baseline is genuinely slow.
     * Navigates via the ⚠ FAB (contentDescription = "unoptimized_list_fab").
     */
    @Test
    fun scrollAnimatedList_unoptimized() =
        measureScroll(fabContentDesc = "unoptimized_list_fab", listContentDesc = "unoptimized_animated_list")

    /**
     * Measures AnimatedListScreen — all four anti-patterns fixed:
     *  1. key = { it.id } → identity-based node reuse
     *  2. graphicsLayer { alpha = alphaState.value } → draw-phase alpha, zero recomposition
     *  3. DeferredTargetAnimation + Modifier.layout → height animation in layout phase only
     *  4. remember(item.id) { Color(...) } → Color allocated once per item
     *
     * Expected p99 < [FRAME_P99_MAX_MS] ms. Navigates via the ▶ FAB
     * (contentDescription = "animated_list_fab").
     */
    @Test
    fun scrollAnimatedList_optimized() =
        measureScroll(fabContentDesc = "animated_list_fab", listContentDesc = "animated_list")

    // ── Core measurement ──────────────────────────────────────────────────────

    private fun measureScroll(fabContentDesc: String, listContentDesc: String) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()

                val fab = device.wait(
                    Until.findObject(By.desc(fabContentDesc)),
                    RENDER_TIMEOUT_MS,
                ) ?: throw RuntimeException("FAB '$fabContentDesc' not found")
                fab.click()

                val listAppeared = device.wait(
                    Until.hasObject(By.desc(listContentDesc)),
                    RENDER_TIMEOUT_MS,
                )
                check(listAppeared) { "'$listContentDesc' did not appear within ${RENDER_TIMEOUT_MS}ms" }
            },
            measureBlock = {
                device.wait(Until.hasObject(By.desc(listContentDesc)), RENDER_TIMEOUT_MS)

                val list = device.findObject(By.desc(listContentDesc))
                    ?: throw RuntimeException("List '$listContentDesc' not found")

                repeat(5) { list.scroll(Direction.DOWN, 1.0f) }
                repeat(5) { list.scroll(Direction.UP, 1.0f) }
            },
        )
    }
}
