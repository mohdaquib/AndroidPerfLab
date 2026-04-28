package com.aquib.androidperflab.benchmarks

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for :app.
 *
 * Run with: ./gradlew :app:generateBaselineProfile
 * (the Baseline Profile Gradle plugin calls this test automatically)
 *
 * The generated profile is written to app/src/main/baseline-prof.txt and
 * installed into the APK via :app's profileinstaller dependency.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        baselineProfileRule.collect(
            packageName = "com.aquib.androidperflab",
        ) {
            // Cover the critical startup path.
            pressHome()
            startActivityAndWait()
        }
    }
}
