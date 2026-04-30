package com.aquib.androidperflab.startup

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.aquib.androidperflab.AndroidPerfLabApplication
import com.aquib.androidperflab.sdk.FakeCrashReportingSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs via InitializationProvider BEFORE Application.onCreate().
 *
 * Critical path (main thread, synchronous):
 *   FakeCrashReportingSdk.registerHandler() — installs the UncaughtExceptionHandler.
 *   Cost: < 1 ms.
 *
 * Non-critical path (Dispatchers.IO, fire-and-forget):
 *   FakeCrashReportingSdk.uploadPendingReports() — scans crash dumps and simulates upload.
 *   Cost: ~120 ms off the main thread.
 *
 * applicationScope is initialized at property-declaration time in AndroidPerfLabApplication,
 * so it exists before this Initializer runs (the Application object is created before
 * ContentProviders are started).
 */
class CrashReportingInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val app = context.applicationContext as AndroidPerfLabApplication

        // Synchronous — registers the exception handler before any other SDK work.
        FakeCrashReportingSdk.registerHandler(context.applicationContext)

        // Asynchronous — upload is I/O bound and does not need to complete before
        // the first frame; move it off the critical path entirely.
        app.applicationScope.launch(Dispatchers.IO) {
            FakeCrashReportingSdk.uploadPendingReports(context.applicationContext)
            Log.d("AppStartup", "CrashReporting upload complete")
        }

        Log.d("AppStartup", "CrashReportingInitializer.create() returned")
    }

    // No dependencies — CrashReporting must be the root of the initializer graph.
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
