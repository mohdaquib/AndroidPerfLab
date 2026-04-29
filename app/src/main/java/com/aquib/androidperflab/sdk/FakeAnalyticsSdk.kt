package com.aquib.androidperflab.sdk

import android.content.Context
import android.os.Build
import android.util.Log

object FakeAnalyticsSdk {

    // Simulates: opening a local SQLite event queue, reading persisted user identity from
    // SharedPreferences, building a device fingerprint, and doing a blocking fetch of
    // the remote analytics endpoint — all on the main thread.
    fun init(context: Context) {
        // Simulate: hydrating the in-memory event schema (heavy map allocation)
        val eventSchema = HashMap<String, String>(512)
        repeat(500) { i -> eventSchema["event_type_$i"] = "schema_v2_$i" }

        // Simulate: reading / writing user identity from SharedPreferences
        val prefs = context.getSharedPreferences("fake_analytics", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", null) ?: run {
            val generated = System.nanoTime().toString(16)
            // commit() instead of apply() — blocks until disk write completes
            prefs.edit().putString("user_id", generated).commit()
            generated
        }
        val sessionIndex = prefs.getInt("session_index", 0) + 1
        prefs.edit().putInt("session_index", sessionIndex).commit()

        // Simulate: device fingerprinting (string concat over multiple Build fields)
        @Suppress("DEPRECATION")
        val fingerprint = buildString {
            append(Build.MANUFACTURER); append('_')
            append(Build.MODEL);        append('_')
            append(Build.DEVICE);       append('_')
            append(Build.FINGERPRINT.takeLast(12))
        }

        // Simulate: blocking endpoint resolution + SDK handshake over network
        Thread.sleep(180L)

        Log.d("FakeAnalyticsSdk", "init complete — user=$userId session=$sessionIndex fp=$fingerprint schema=${eventSchema.size} entries")
    }
}
