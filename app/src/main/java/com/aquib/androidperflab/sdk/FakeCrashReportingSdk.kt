package com.aquib.androidperflab.sdk

import android.content.Context
import android.util.Log
import java.io.File

object FakeCrashReportingSdk {

    // Simulates: scanning the cache directory for pending crash dumps left by a previous
    // session, installing an UncaughtExceptionHandler, and uploading any found dumps
    // synchronously before the app is considered "ready".
    fun init(context: Context) {
        // Simulate: scanning cache dir for pending crash dump files
        val cacheDir = context.cacheDir
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

        // Simulate: writing a session sentinel file so the next launch can detect
        // whether this session ended cleanly
        val sentinel = File(cacheDir, "crash_sentinel_${System.currentTimeMillis()}.tmp")
        sentinel.createNewFile()

        // Simulate: registering the uncaught exception handler (involves thread locking)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("FakeCrashReportingSdk", "Uncaught exception on ${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Simulate: blocking upload of any pending crash reports to the backend
        Thread.sleep(120L)

        Log.d("FakeCrashReportingSdk", "init complete — found ${crashDumps.size} pending dumps, parsed ${parsedReports.size} reports")
    }
}
