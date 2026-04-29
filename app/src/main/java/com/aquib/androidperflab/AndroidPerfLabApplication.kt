package com.aquib.androidperflab

import android.app.Application
import android.util.Log
import com.aquib.androidperflab.sdk.FakeAnalyticsSdk
import com.aquib.androidperflab.sdk.FakeCrashReportingSdk
import com.aquib.androidperflab.sdk.FakeFeatureFlagsSdk
import com.aquib.androidperflab.sdk.FakePerformanceMonitorSdk
import com.aquib.androidperflab.sdk.FakeRemoteConfigSdk

class AndroidPerfLabApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Intentionally bad: all five SDKs are initialised synchronously on the main thread.
        // Each blocks via Thread.sleep() to simulate the I/O, disk, and network work that
        // real SDKs perform. Total cold-start penalty ≈ 750 ms before the first frame.

        val t0 = System.currentTimeMillis()

        FakeCrashReportingSdk.init(this)           // ~120 ms — must be first to catch early crashes
        Log.d("AppStartup", "CrashReporting ready   +${System.currentTimeMillis() - t0}ms")

        FakeAnalyticsSdk.init(this)                // ~180 ms
        Log.d("AppStartup", "Analytics ready        +${System.currentTimeMillis() - t0}ms")

        FakeFeatureFlagsSdk.init(this)             // ~150 ms
        Log.d("AppStartup", "FeatureFlags ready     +${System.currentTimeMillis() - t0}ms")

        FakeRemoteConfigSdk.init(this)             // ~200 ms
        Log.d("AppStartup", "RemoteConfig ready     +${System.currentTimeMillis() - t0}ms")

        FakePerformanceMonitorSdk.init(this)       // ~100 ms — last so it can baseline the others
        Log.d("AppStartup", "PerfMonitor ready      +${System.currentTimeMillis() - t0}ms")

        Log.d("AppStartup", "Application.onCreate() complete — total blocked=${System.currentTimeMillis() - t0}ms")
    }
}
