package com.aquib.androidperflab.startup

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.aquib.androidperflab.AndroidPerfLabApplication
import com.aquib.androidperflab.sdk.FakePerformanceMonitorSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * NOT registered in AndroidManifest.xml — triggered from Application.onCreate() on
 * Dispatchers.IO, sequenced after AnalyticsInitializer.
 *
 * Declaring AnalyticsInitializer as a dependency preserves the original intent: the
 * performance monitor should start after the other SDKs have launched so it can
 * baseline their initialization overhead. Note that the dependency guarantees ordering
 * of create() calls, not completion of the background coroutines — PerfMonitor may
 * start its actual work while Analytics is still initializing.
 */
class PerfMonitorInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val app = context.applicationContext as AndroidPerfLabApplication
        app.applicationScope.launch(Dispatchers.IO) {
            FakePerformanceMonitorSdk.init(context.applicationContext)
            Log.d("AppStartup", "PerfMonitor ready")
        }
        Log.d("AppStartup", "PerfMonitorInitializer.create() returned")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(AnalyticsInitializer::class.java)
}
