package com.aquib.androidperflab.benchmarks

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE    = "com.aquib.androidperflab"
private const val RENDER_TIMEOUT_MS = 5_000L

/**
 * Before / after comparison for moving SDK initialisations off the main thread.
 * Both states are measured in a single benchmark run and appear side by side in
 * benchmarkData.json, keyed by test name.
 *
 * ┌──────────────────────────────────  ┬─────────────────┬──────────────────────────────────────────┐
 * │ Test                               │ TTID (approx.)  │ Main-thread SDK blocking                 │
 * ├──────────────────────────────────  ┼─────────────────┼──────────────────────────────────────────┤
 * │ startupCold_sdkAsyncInit_baseline  │ ~1100–1300 ms   │ ~750 ms (5 SDKs × synchronous sleep)     │
 * │ startupCold_sdkAsyncInit_optimized │ ~150–350 ms     │ < 5 ms (handler registration only)       │
 * └──────────────────────────────────  ┴─────────────────┴──────────────────────────────────────────┘
 *
 * How the baseline state is activated:
 *   The baseline test writes /data/local/tmp/perflab_slow_startup via adb shell before each
 *   iteration. AndroidPerfLabApplication.onCreate() detects this file and runs all five SDKs
 *   synchronously on the main thread instead of dispatching to Dispatchers.IO. The flag file
 *   is removed by the optimized test's setupBlock and by the @After tearDown().
 *
 * What changed between states:
 *  • CrashReportingInitializer (manifest, before Application.onCreate):
 *      – registerHandler()      — < 1 ms, main thread
 *      – uploadPendingReports() — ~120 ms, Dispatchers.IO
 *  • AnalyticsInitializer + PerfMonitorInitializer (from Application.onCreate):
 *      – launched immediately on Dispatchers.IO (~180 ms + ~100 ms, never main thread)
 *  • FeatureFlagsInitializer + RemoteConfigInitializer (lazy, from Application.onCreate):
 *      – deferred 500 ms, then run on Dispatchers.IO (~150 ms + ~200 ms)
 *      – SDK public methods return defaults until coroutines complete
 *
 * Run:
 *   ./gradlew :benchmarks:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.aquib.androidperflab.benchmarks.AppStartupBenchmark
 */
@RunWith(AndroidJUnit4::class)
class AppStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Cold start — BASELINE ("before" state).
     * The flag file activates synchronous SDK init in Application.onCreate():
     * all five SDKs block the main thread for a combined ~750 ms.
     *
     * Expected TTID: ~1100–1300 ms.
     */
    @Test
    fun startupCold_sdkAsyncInit_baseline() = measure(StartupMode.COLD, slowStartupMode = true)

    /**
     * Cold start — OPTIMISED ("after" state).
     * All SDKs run on Dispatchers.IO; the main thread is free after <5 ms.
     *
     * Expected TTID: ~150–350 ms. Must be < 800 ms (enforced by BenchmarkResultsParser.py).
     */
    @Test
    fun startupCold_sdkAsyncInit_optimized() = measure(StartupMode.COLD, slowStartupMode = false)

    /**
     * Warm start — OPTIMISED.
     * Activity is recreated but Application.onCreate() is skipped, so TTID should be
     * identical across baseline and optimised — confirming the async init does not
     * regress non-cold startup paths.
     */
    @Test
    fun startupWarm_sdkAsyncInit_optimized() = measure(StartupMode.WARM, slowStartupMode = false)

    // ── Core measurement ──────────────────────────────────────────────────────

    private fun measure(startupMode: StartupMode, slowStartupMode: Boolean) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = startupMode,
            iterations = 10,
            setupBlock = {
                if (slowStartupMode) {
                    device.executeShellCommand("touch /data/local/tmp/perflab_slow_startup")
                } else {
                    device.executeShellCommand("rm -f /data/local/tmp/perflab_slow_startup")
                }
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()
                assertTtidCaptured()
                assertTtfdCaptured()
            },
        )
    }

    @After
    fun tearDown() {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            .executeShellCommand("rm -f /data/local/tmp/perflab_slow_startup")
    }

    // ── Assertion helpers ─────────────────────────────────────────────────────

    /**
     * Verifies TTID: the target package is in the foreground and the first feed item
     * is visible. An app crash during async SDK init would cause this to fail with a
     * "Wrong package" or timeout error rather than a silent incorrect measurement.
     */
    private fun MacrobenchmarkScope.assertTtidCaptured() {
        assertEquals(
            "Wrong package in foreground — async SDK init may have caused a crash",
            TARGET_PACKAGE,
            device.currentPackageName,
        )
        val firstItem = device.wait(
            Until.hasObject(By.text("Post #0 — Technology")),
            RENDER_TIMEOUT_MS,
        )
        assertNotNull(
            "\"Post #0 — Technology\" not visible within ${RENDER_TIMEOUT_MS} ms — " +
                "async SDK init may be blocking the main thread",
            firstItem,
        )
    }

    /**
     * Verifies TTFD: waits for the feed LazyColumn to become scrollable, which
     * requires Compose's full first layout pass to have completed.
     */
    private fun MacrobenchmarkScope.assertTtfdCaptured() {
        val scrollable = device.wait(
            Until.hasObject(By.scrollable(true)),
            RENDER_TIMEOUT_MS,
        )
        assertNotNull(
            "No scrollable view within ${RENDER_TIMEOUT_MS} ms — " +
                "Compose layout may not have completed",
            scrollable,
        )
    }
}
