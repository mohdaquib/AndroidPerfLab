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
 * Measures app startup time across all three Android startup modes.
 *
 *  Mode  │ Process │ Activity │ What executes
 * ───────┼─────────┼──────────┼──────────────────────────────────────────────────────
 *  COLD  │ killed  │ gone     │ Application.onCreate() + Activity.onCreate() (full SDK init)
 *  WARM  │ alive   │ gone     │ Activity.onCreate() only (SDK init skipped)
 *  HOT   │ alive   │ alive    │ onStart() + onResume() only (cheapest path)
 *
 * Metrics per iteration (written to benchmarkData.json):
 *   timeToInitialDisplayMs  — TTID: system-measured first frame
 *   timeToFullDisplayMs     — TTFD: app-reported via reportFullyDrawn()
 *                             (absent for HOT start — onCreate is not called)
 *
 * Each mode runs 10 iterations under CompilationMode.None() (JIT only, no AOT)
 * to produce a worst-case, maximally repeatable baseline.
 *
 * Run:
 *   ./gradlew :benchmarks:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *     com.aquib.androidperflab.benchmarks.StartupBenchmark
 *
 * Output:
 *   benchmarks/build/outputs/connected_android_test_additional_output/
 *     benchmark/connected/<device>/StartupBenchmark-benchmarkData.json
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Cold start — most expensive.
     * Process is killed before every iteration.
     * Application.onCreate() runs in full, blocking the main thread for ~750 ms
     * across the five fake SDK initialisations.
     */
    @Test
    fun startupCold() = measureStartup(StartupMode.COLD, expectTtfd = true)

    /**
     * Warm start — medium cost.
     * Process stays alive; Activity is destroyed and recreated between iterations.
     * Application.onCreate() is skipped, so the ~750 ms SDK penalty is absent.
     * Remaining cost: Activity + Compose + LazyColumn first composition.
     */
    @Test
    fun startupWarm() = measureStartup(StartupMode.WARM, expectTtfd = true)

    /**
     * Hot start — cheapest path.
     * Process and Activity are both kept alive.
     * The system calls onStart() + onResume(); Compose recomposes only what changed.
     * reportFullyDrawn() is NOT called (onCreate is skipped), so TTFD is absent from
     * the JSON for this mode.
     */
    @Test
    fun startupHot() = measureStartup(StartupMode.HOT, expectTtfd = false)

    // ── Core measurement ─────────────────────────────────────────────────────

    private fun measureStartup(startupMode: StartupMode, expectTtfd: Boolean) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = startupMode,
            iterations = 10,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()
                assertTtidCaptured()
                if (expectTtfd) assertTtfdCaptured()
            },
        )
    }

    // ── Assertion helpers ────────────────────────────────────────────────────

    /**
     * Asserts TTID was captured: verifies the target package is in the foreground
     * and that the first feed item ("Post #0 — Technology") is visible, confirming
     * the first frame rendered real content rather than a blank window surface.
     */
    private fun MacrobenchmarkScope.assertTtidCaptured() {
        assertEquals(
            "Wrong package in foreground — did the app crash during startup?",
            TARGET_PACKAGE,
            device.currentPackageName,
        )
        val firstItem = device.wait(
            Until.hasObject(By.text("Post #0 — Technology")),
            RENDER_TIMEOUT_MS,
        )
        assertNotNull(
            "\"Post #0 — Technology\" not visible within ${RENDER_TIMEOUT_MS} ms — " +
                "first frame may not have rendered real content",
            firstItem,
        )
    }

    /**
     * Asserts TTFD was captured: waits for the feed's LazyColumn to be scrollable,
     * which requires the full Compose layout pass to complete — the same condition
     * that triggers reportFullyDrawn() in MainActivity.onCreate(). Not called for
     * HOT mode since onCreate() is skipped and TTFD is intentionally absent.
     */
    private fun MacrobenchmarkScope.assertTtfdCaptured() {
        val scrollable = device.wait(
            Until.hasObject(By.scrollable(true)),
            RENDER_TIMEOUT_MS,
        )
        assertNotNull(
            "No scrollable view found within ${RENDER_TIMEOUT_MS} ms — " +
                "reportFullyDrawn() may not have fired or the feed did not compose",
            scrollable,
        )
    }
}
