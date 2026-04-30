package com.aquib.androidperflab.benchmarks

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE    = "com.aquib.androidperflab"
private const val RENDER_TIMEOUT_MS = 5_000L

/**
 * Before / after comparison for moving SDK initializations off the main thread.
 *
 * ┌─────────────────────┬─────────────────┬──────────────────────────────────────────┐
 * │ State               │ TTID (approx.)  │ Main-thread SDK blocking                 │
 * ├─────────────────────┼─────────────────┼──────────────────────────────────────────┤
 * │ BEFORE (baseline)   │ ~1100–1300 ms   │ ~750 ms (5 SDKs × synchronous sleep)     │
 * │ AFTER  (this build) │ ~150–350 ms     │ < 5 ms (handler registration only)       │
 * └─────────────────────┴─────────────────┴──────────────────────────────────────────┘
 *
 * What changed:
 *  • CrashReportingInitializer (manifest, before Application.onCreate):
 *      – registerHandler()      — < 1 ms, main thread
 *      – uploadPendingReports() — ~120 ms, Dispatchers.IO
 *  • AnalyticsInitializer + PerfMonitorInitializer (from Application.onCreate):
 *      – launched immediately on Dispatchers.IO (~180 ms + ~100 ms, never main thread)
 *  • FeatureFlagsInitializer + RemoteConfigInitializer (lazy, from Application.onCreate):
 *      – deferred 500 ms, then run on Dispatchers.IO (~150 ms + ~200 ms)
 *      – SDK public methods return defaults until coroutines complete
 *
 * Run against the BEFORE baseline:
 *   git stash        # restore original synchronous Application.onCreate
 *   ./gradlew :benchmarks:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.aquib.androidperflab.benchmarks.AppStartupBenchmark
 *   git stash pop    # restore async implementation
 *   ./gradlew :benchmarks:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.aquib.androidperflab.benchmarks.AppStartupBenchmark
 *
 * Compare timeToInitialDisplayMs across both runs.
 * See also: StartupBenchmark.startupCold() for the original three-mode baseline.
 */
@RunWith(AndroidJUnit4::class)
class AppStartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Cold start — full Application.onCreate() path.
     *
     * BEFORE: TTID dominated by ~750 ms of synchronous SDK init on the main thread.
     * AFTER:  TTID reflects only Compose first-frame work; SDKs run in background.
     *
     * Expected improvement: ~600–750 ms reduction in timeToInitialDisplayMs.
     */
    @Test
    fun startupCold_sdkAsyncInit() = measure(StartupMode.COLD)

    /**
     * Warm start — Activity recreated, Application.onCreate() skipped.
     *
     * TTID here should be identical before and after the fix — confirming that
     * async SDK init does not regress non-cold startup paths.
     */
    @Test
    fun startupWarm_sdkAsyncInit() = measure(StartupMode.WARM)

    // ── Core measurement ──────────────────────────────────────────────────────

    private fun measure(startupMode: StartupMode) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = startupMode,
            iterations = 10,
            setupBlock = { pressHome() },
            measureBlock = {
                startActivityAndWait()
                assertTtidCaptured()
                assertTtfdCaptured()
            },
        )
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
