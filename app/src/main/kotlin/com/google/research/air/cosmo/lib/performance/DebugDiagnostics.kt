package com.google.research.air.cosmo.lib.performance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object DebugDiagnostics {
    fun install(context: Context? = null) {
        StrictMode.setThreadPolicy(
            StrictMode
                .ThreadPolicy
                .Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode
                .VmPolicy
                .Builder()
                .detectActivityLeaks()
                .detectFileUriExposure()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .penaltyLog()
                .build()
        )
        MainThreadAnrWatchdog().start()
        if (context != null) {
            ProfilingDiagnostics.installIfAvailable(context)
        }
    }
}

private object ProfilingDiagnostics {
    fun installIfAvailable(context: Context) {
        val managerClass =
            runCatching { Class.forName("android.os.ProfilingManager") }
                .getOrNull()
                ?: return
        val manager = runCatching { context.getSystemService(managerClass) }.getOrNull() ?: return
        Log.i(TAG, "ProfilingManager available for debug diagnostics: ${manager.javaClass.name}")
        // Android 17 anomaly-trigger registration is intentionally reflective here so the app can
        // ship against stable SDKs while enabling the hook on preview builds that expose it.
        val triggerMethod =
            manager.javaClass.methods.firstOrNull { method ->
                method.name.contains("trigger", ignoreCase = true) &&
                    method.name.contains("profil", ignoreCase = true)
            }
        Log.i(TAG, "Profiling anomaly trigger method present: ${triggerMethod != null}")
    }

    private const val TAG = "COSMO-Profiling"
}

private class MainThreadAnrWatchdog(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    private val pollIntervalMillis: Long = DEFAULT_POLL_INTERVAL_MILLIS
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)

    fun start() {
        if (!running.compareAndSet(false, true)) return
        Thread(::loop, THREAD_NAME).apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    private fun loop() {
        var lastReportAt = 0L
        while (running.get()) {
            val latch = CountDownLatch(1)
            val postedAt = SystemClock.uptimeMillis()
            mainHandler.post { latch.countDown() }
            val responsive = latch.await(timeoutMillis, TimeUnit.MILLISECONDS)
            val now = SystemClock.uptimeMillis()
            if (!responsive && now - lastReportAt >= REPORT_THROTTLE_MILLIS) {
                lastReportAt = now
                Log.e(TAG, "Main thread unresponsive for at least ${now - postedAt} ms", mainThreadSnapshot())
            }
            Thread.sleep(pollIntervalMillis)
        }
    }

    private fun mainThreadSnapshot(): Throwable {
        val mainThread = Looper.getMainLooper().thread
        val throwable = Throwable("Main thread stack snapshot")
        throwable.stackTrace = mainThread.stackTrace
        return throwable
    }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MILLIS = 1_000L
        const val DEFAULT_TIMEOUT_MILLIS = 5_000L
        const val REPORT_THROTTLE_MILLIS = 30_000L
        const val TAG = "COSMO-ANRWatchdog"
        const val THREAD_NAME = "cosmo-anr-watchdog"
    }
}
