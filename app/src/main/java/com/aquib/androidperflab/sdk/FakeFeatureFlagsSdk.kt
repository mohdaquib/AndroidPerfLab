package com.aquib.androidperflab.sdk

import android.content.Context
import android.util.Log

object FakeFeatureFlagsSdk {

    private val flags = HashMap<String, Boolean>()

    // Simulates: parsing a large bundled JSON payload of flag definitions, evaluating
    // per-user targeting rules against locally stored user attributes, and persisting
    // the resolved flag state — all synchronously on the main thread.
    fun init(context: Context) {
        // Simulate: deserialising bundled flag defaults (CPU-bound JSON parsing)
        val rawPayload = buildString {
            repeat(200) { i ->
                append("""{"flag":"feature_$i","enabled":${i % 3 != 0},"rollout":${(i * 7) % 100}},""")
            }
        }

        // Simulate: evaluating targeting rules for each flag
        val prefs = context.getSharedPreferences("fake_feature_flags", Context.MODE_PRIVATE)
        val userSegment = prefs.getInt("user_segment", (System.nanoTime() % 100).toInt())
        prefs.edit().putInt("user_segment", userSegment).commit()

        repeat(200) { i ->
            val rollout = (i * 7) % 100
            flags["feature_$i"] = (i % 3 != 0) && (userSegment < rollout)
        }

        val enabledCount = flags.values.count { it }

        // Simulate: blocking network sync to fetch any server-side flag overrides
        Thread.sleep(150L)

        Log.d("FakeFeatureFlagsSdk", "init complete — ${flags.size} flags resolved, $enabledCount enabled for segment=$userSegment payload=${rawPayload.length} chars")
    }

    fun isEnabled(flag: String): Boolean = flags[flag] ?: false
}
