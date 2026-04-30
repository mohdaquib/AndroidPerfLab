package com.aquib.androidperflab.startup

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.aquib.androidperflab.AndroidPerfLabApplication
import com.aquib.androidperflab.sdk.FakeRemoteConfigSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Lazy initializer for the Remote Config SDK.
 *
 * NOT registered in AndroidManifest.xml. Triggered from Application.onCreate() after
 * a 500 ms delay (alongside FeatureFlagsInitializer) so first-frame rendering is
 * never competed with by config disk / network I/O.
 *
 * FakeRemoteConfigSdk.getString() returns the previously cached blob from SharedPreferences
 * until the background coroutine completes — the app always has a usable config value.
 */
class RemoteConfigInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        val app = context.applicationContext as AndroidPerfLabApplication
        app.applicationScope.launch(Dispatchers.IO) {
            FakeRemoteConfigSdk.init(context.applicationContext)
            Log.d("AppStartup", "RemoteConfig ready")
        }
        Log.d("AppStartup", "RemoteConfigInitializer.create() returned")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(CrashReportingInitializer::class.java)
}
