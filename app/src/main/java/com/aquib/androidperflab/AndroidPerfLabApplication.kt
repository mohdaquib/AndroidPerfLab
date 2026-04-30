package com.aquib.androidperflab

import android.app.Application
import android.util.Log
import androidx.startup.AppInitializer
import com.aquib.androidperflab.startup.AnalyticsInitializer
import com.aquib.androidperflab.startup.FeatureFlagsInitializer
import com.aquib.androidperflab.startup.PerfMonitorInitializer
import com.aquib.androidperflab.startup.RemoteConfigInitializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AndroidPerfLabApplication : Application() {

    // Initialized at property-declaration time (before Application.onCreate and before
    // ContentProviders start). CrashReportingInitializer accesses this scope to fire
    // its background upload job — which is why it must be a property, not set in onCreate.
    // SupervisorJob: a failing child coroutine does not cancel sibling coroutines.
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val t0 = System.currentTimeMillis()

        // CrashReportingInitializer ran synchronously via InitializationProvider BEFORE
        // this method. The exception handler is already registered; its upload coroutine
        // is already running on Dispatchers.IO. Nothing to do here for crash reporting.
        val appInit = AppInitializer.getInstance(this)

        // Non-critical SDKs (Analytics, PerfMonitor): launch immediately on IO.
        // AppInitializer.initializeComponent() is idempotent — safe to call even if
        // a dependency was already initialized via the manifest.
        // Both SDKs initialize in ~180 ms + ~100 ms but never touch the main thread.
        applicationScope.launch(Dispatchers.IO) {
            appInit.initializeComponent(AnalyticsInitializer::class.java)
            // PerfMonitor declares Analytics as a dependency in the initializer graph,
            // so AppInitializer will invoke Analytics.create() first automatically —
            // the explicit sequence here is just for clarity.
            appInit.initializeComponent(PerfMonitorInitializer::class.java)
        }

        // Lazy SDKs (FeatureFlags, RemoteConfig): deferred by 500 ms so they never
        // compete with Compose's first layout-and-draw pass. Both SDKs return safe
        // defaults until their background coroutines complete, so the UI is never gated
        // on their initialization.
        applicationScope.launch(Dispatchers.IO) {
            delay(500L)
            appInit.initializeComponent(FeatureFlagsInitializer::class.java)
            appInit.initializeComponent(RemoteConfigInitializer::class.java)
        }

        Log.d(TAG, "Application.onCreate() returned in ${System.currentTimeMillis() - t0} ms " +
                "— all SDKs initializing in background")
    }

    companion object {
        private const val TAG = "AppStartup"
    }
}
