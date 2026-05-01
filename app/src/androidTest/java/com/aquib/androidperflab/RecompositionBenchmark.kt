package com.aquib.androidperflab

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aquib.androidperflab.ui.DetailScreen
import com.aquib.androidperflab.ui.FeedItem
import com.aquib.androidperflab.ui.theme.AndroidPerfLabTheme
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecompositionBenchmark {
    // Shared scheduler drives virtual time for runTest blocks in this class.
    private val testScheduler = TestCoroutineScheduler()

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val testItem = FeedItem(
        id = 42,
        title = "Performance Testing in Android",
        subtitle = "Benchmarking Compose recompositions",
        description = "Android performance testing involves many dimensions including startup " +
            "time, frame rendering, and recomposition overhead. This article explores " +
            "techniques for measuring each of these in depth.",
        author = "Mohd Aquib",
        imageUrl = "",
        timestampMillis = 1_700_000_000_000L,
    )

    @Before
    fun setUp() {
        composeTestRule.setContent {
            AndroidPerfLabTheme {
                DetailScreen(item = testItem, onBack = {})
            }
        }
        // Pause Compose's virtual clock so the LaunchedEffect tick loop in DetailScreen
        // does not fire between measurement checkpoints, keeping deltas deterministic.
        composeTestRule.mainClock.autoAdvance = false
        // Drain the initial composition pass before any test captures a baseline count.
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()
    }

    // ── Like button ──────────────────────────────────────────────────────────────

    @Test
    fun likeButton_recompositionCount_optimized() {
        val before = totalChangeCount()

        composeTestRule.onNodeWithTag("detail_like_button").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        val delta = totalChangeCount() - before
        assertEquals(
            "Unnecessary recompositions after like-button click must be zero",
            EXPECTED_RECOMPOSITIONS_PER_BUTTON_CLICK,
            delta,
        )
        record("like_button_click", before, delta)
    }

    // ── Bookmark button ──────────────────────────────────────────────────────────

    @Test
    fun bookmarkButton_recompositionCount_optimized() {
        val before = totalChangeCount()

        composeTestRule.onNodeWithTag("detail_bookmark_button").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.waitForIdle()

        val delta = totalChangeCount() - before
        assertEquals(
            "Unnecessary recompositions after bookmark-button click must be zero",
            EXPECTED_RECOMPOSITIONS_PER_BUTTON_CLICK,
            delta,
        )
        record("bookmark_button_click", before, delta)
    }

    // ── Tick-driven recompositions ───────────────────────────────────────────────

    @Test
    fun tickEffect_recompositionCountPerInterval_optimized() = runTest(testScheduler) {
        val tickCount = 5
        val beforeAll = totalChangeCount()
        var totalDelta = 0L

        repeat(tickCount) { index ->
            val before = totalChangeCount()

            // Advance Compose's main clock to unblock the delay(500L) in LaunchedEffect.
            composeTestRule.mainClock.advanceTimeBy(500L)
            // Advance TestCoroutineScheduler by the same interval so virtual time
            // stays in sync for any coroutines running on testScheduler.
            testScheduler.advanceTimeBy(500L)
            composeTestRule.waitForIdle()

            val delta = totalChangeCount() - before
            totalDelta += delta
            Log.d(TAG, "Tick ${index + 1}: $delta recompositions")
        }

        val avgDelta = totalDelta / tickCount
        assertEquals(
            "Unnecessary recompositions per tick must be zero",
            EXPECTED_RECOMPOSITIONS_PER_TICK,
            avgDelta,
        )
        record("tick_effect_per_interval", beforeAll, avgDelta)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private fun totalChangeCount(): Long =
        Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    private fun record(interaction: String, before: Long, delta: Long) {
        val after = before + delta
        Log.d(TAG, "[$interaction] before=$before  after=$after  delta=$delta")
        val bundle = Bundle().apply {
            putLong("${interaction}_before", before)
            putLong("${interaction}_after", after)
            putLong("${interaction}_delta", delta)
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(2, bundle)
    }

    companion object {
        private const val TAG = "RecompositionBenchmark"
        private const val EXPECTED_RECOMPOSITIONS_PER_BUTTON_CLICK = 1L
        private const val EXPECTED_RECOMPOSITIONS_PER_TICK = 1L
    }
}
