package com.aquib.androidperflab.sdk

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.util.Log

object FakePerformanceMonitorSdk {

    // Simulates: snapshotting baseline memory stats, reading /proc/self/status for CPU
    // accounting, and installing a frame-timing callback — all synchronously on the main
    // thread before the first frame is drawn.
    fun init(context: Context) {
        // Simulate: collecting baseline memory snapshot
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val nativeHeap = Debug.getNativeHeapSize()

        // Simulate: reading ActivityManager memory info
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        // Simulate: reading per-process CPU stats from procfs (string parsing)
        val pid = Process.myPid()
        val procStatLines = buildString {
            repeat(80) { i -> append("cpu_stat_field_${i}=${pid + i}\n") }
        }.lines()

        // Simulate: building baseline metric registry (100 named counters)
        val metrics = HashMap<String, Long>(128)
        repeat(100) { i ->
            metrics["metric_$i"] = System.nanoTime() + i
        }

        // Simulate: GC pause measurement — forces a GC and measures its cost
        val gcBefore = Debug.getRuntimeStat("art.gc.gc-count")
        System.gc()
        val gcAfter = Debug.getRuntimeStat("art.gc.gc-count")

        // Simulate: installing frame-timing infrastructure (method tracing warmup)
        Thread.sleep(100L)

        Log.d(
            "FakePerfMonitorSdk",
            "init complete — heap=${totalMemory / 1024}KB free=${freeMemory / 1024}KB " +
                "nativeHeap=${nativeHeap / 1024}KB availMem=${memInfo.availMem / 1024}KB " +
                "procStatLines=${procStatLines.size} metrics=${metrics.size} gc=$gcBefore→$gcAfter",
        )
    }
}
