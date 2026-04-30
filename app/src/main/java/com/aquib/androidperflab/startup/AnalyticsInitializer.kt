package com.aquib.androidperflab.startup

import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.aquib.androidperflab.AndroidPerfLabApplication
import com.aquib.androidperflab.sdk.FakeAnalyticsSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * NOT registered in AndroidManifest.xml — triggered manually from Application.onCreate()
 * via AppInitializer.initializeComponent() on Dispatchers.IO.
 *
 * create() returns immediately after launching the background coroutine; the ~180 ms
 * SDK init never touches the main thread. CrashReportingInitializer is declared as a
 * dependency so the exception handler is guaranteed to be registered before any SDK
 * code runs (AppInitializer resolves the dependency graph automatically).
 */
class AnalyticsInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        val app = context.applicationContext as AndroidPerfLabApplication
        app.applicationScope.launch(Dispatchers.IO) {
            FakeAnalyticsSdk.init(context.applicationContext)
            Log.d("AppStartup", "Analytics ready")
        }
        Log.d("AppStartup", "AnalyticsInitializer.create() returned")
    }

    override fun dependencies(): List<Class<out Initializer<*>>> =
        listOf(CrashReportingInitializer::class.java)
}
