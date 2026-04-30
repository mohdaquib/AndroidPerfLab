package com.aquib.androidperflab.startup

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.aquib.androidperflab.AndroidPerfLabApplication
import com.aquib.androidperflab.sdk.FakeFeatureFlagsSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lazy initializer for the Feature Flags SDK.
 *
 * NOT registered in AndroidManifest.xml. Application.onCreate() defers triggering
 * this initializer by 500 ms so it never competes with first-frame rendering or the
 * immediately-needed Analytics / PerfMonitor SDKs.
 *
 * FakeFeatureFlagsSdk.isEnabled() returns false (the default) for any flag until the
 * background coroutine completes — the UI is never gated on this SDK being ready.
 */
class FeatureFlagsInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val app = context.applicationContext as AndroidPerfLabApplication
        app.applicationScope.launch(Dispatchers.IO) {
            FakeFeatureFlagsSdk.init(context.applicationContext)
            Log.d("AppStartup", "FeatureFlags ready")
        }
        Log.d("AppStartup", "FeatureFlagsInitializer.create() returned")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(CrashReportingInitializer::class.java)
}
