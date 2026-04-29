package com.aquib.androidperflab.sdk

import android.content.Context
import android.util.Log

object FakeRemoteConfigSdk {

    private val config = HashMap<String, String>()

    // Simulates: reading the last-fetched remote config blob from SharedPreferences,
    // validating an HMAC checksum, deserialising key-value pairs, and resolving
    // parameter overrides — synchronously blocking the main thread.
    fun init(context: Context) {
        val prefs = context.getSharedPreferences("fake_remote_config", Context.MODE_PRIVATE)

        // Simulate: reading + validating a previously cached config blob
        val cachedBlob = prefs.getString("config_blob", null)
        val storedChecksum = prefs.getString("config_checksum", "")

        if (cachedBlob != null) {
            // Simulate: checksum validation (string hashing)
            val computedChecksum = cachedBlob.fold(0L) { acc, c -> acc * 31 + c.code }.toString(16)
            val valid = computedChecksum == storedChecksum
            Log.d("FakeRemoteConfigSdk", "cache checksum valid=$valid")
        }

        // Simulate: building a new in-memory config with 150 keys
        val newBlob = buildString {
            repeat(150) { i ->
                val key = "config_key_$i"
                val value = "value_${i}_${System.nanoTime() % 1000}"
                config[key] = value
                append("$key=$value;")
            }
        }
        val checksum = newBlob.fold(0L) { acc, c -> acc * 31 + c.code }.toString(16)
        prefs.edit()
            .putString("config_blob", newBlob)
            .putString("config_checksum", checksum)
            .commit()

        // Simulate: blocking fetch of fresh config values from the remote endpoint
        Thread.sleep(200L)

        Log.d("FakeRemoteConfigSdk", "init complete — ${config.size} keys loaded, checksum=$checksum")
    }

    fun getString(key: String, default: String = ""): String = config[key] ?: default
}
