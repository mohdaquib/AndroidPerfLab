package com.aquib.androidperflab.sdk

import android.content.Context
import android.util.Log
import java.io.File

object FakeCrashReportingSdk {

    // ── Fast path — called synchronously from CrashReportingInitializer ──────────
    //
    // Only registers the UncaughtExceptionHandler. Safe to call from any thread.
    // Separated from uploadPendingReports() so the handler is installed before any
    // other SDK work begins — matching the original "must be first" requirement —
    // without blocking the main thread for the full 120 ms upload simulation.

    fun registerHandler(context: Context) {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FakeCrashReportingSdk", "Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }
        Log.d("FakeCrashReportingSdk", "registerHandler complete")
    }

    // ── Slow path — called from Dispatchers.IO via CrashReportingInitializer ─────
    //
    // Simulates: scanning the cache directory for pending crash dumps, parsing them,
    // writing a session sentinel, and uploading reports to the backend. All I/O and
    // the blocking upload sleep are safe on a background thread.

    fun uploadPendingReports(context: Context) {
        val cacheDir = context.cacheDir

        // Simulate: scanning cache dir for pending crash dump files
        val crashDumps = cacheDir.listFiles { f -> f.name.startsWith("crash_") }
            ?.toList()
            ?: emptyList()

        // Simulate: parsing each crash dump (string + I/O work per file)
        val parsedReports = crashDumps.map { file ->
            buildString {
                append("report["); append(file.name); append("]: ")
                append(file.length()); append(" bytes, modified=")
                append(file.lastModified())
            }
        }

        // Simulate: writing a session sentinel so the next launch can detect clean exits
        val sentinel = File(cacheDir, "crash_sentinel_${System.currentTimeMillis()}.tmp")
        sentinel.createNewFile()

        // Simulate: blocking upload of pending reports to the backend
        Thread.sleep(120L)

        Log.d(
            "FakeCrashReportingSdk",
            "uploadPendingReports complete — found ${crashDumps.size} dumps, " +
                "parsed ${parsedReports.size} reports",
        )
    }
}
