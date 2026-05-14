package com.google.research.air.cosmo

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.research.air.cosmo.gatekeeping.GatekeeperDebugReceiver
import com.google.research.air.cosmo.intent.IntentExtractor
import com.google.research.air.cosmo.lib.battery.BatteryStateReceiver
import com.google.research.air.cosmo.lib.performance.DebugDiagnostics
import com.google.research.air.cosmo.lib.performance.MemoryPressureTrimmer
import com.google.research.air.cosmo.notifications.CosmoNotificationManager
import com.google.research.air.cosmo.runtime.ProcessInitializerRunner
import com.google.research.air.cosmo.runtime.PureProcess
import com.google.research.air.cosmo.setting.CosmoStateManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CosmoApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var processInitializerRunner: ProcessInitializerRunner

    @Inject lateinit var cosmoStateManager: CosmoStateManager

    @Inject lateinit var notificationManager: CosmoNotificationManager

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var memoryPressureTrimmer: MemoryPressureTrimmer

    @Inject lateinit var gatekeeperDebugReceiver: GatekeeperDebugReceiver

    @Inject lateinit var intentExtractor: IntentExtractor

    private var batteryReceiver: BatteryStateReceiver? = null

    override fun onCreate() {
        super.onCreate()
        if (PureProcess.isPure(this)) return

        if (BuildConfig.DEBUG) {
            DebugDiagnostics.install(this)
        }
        processInitializerRunner.init()
        notificationManager.createNotificationChannels()
        batteryReceiver =
            BatteryStateReceiver(cosmoStateManager).also { receiver ->
                val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                sticky?.let { receiver.onReceive(this, it) }
            }
        gatekeeperDebugReceiver.registerIfDebug(this)
        // C7: second consumer of GatekeeperOutputSource.results (action pipeline being the first).
        // Subscribes once at app start; idempotent.
        intentExtractor.start()
    }

    override fun onTerminate() {
        batteryReceiver?.let(::unregisterReceiver)
        batteryReceiver = null
        gatekeeperDebugReceiver.unregister(this)
        intentExtractor.close()
        super.onTerminate()
    }

    override fun onTrimMemory(level: Int) {
        memoryPressureTrimmer.trim(level)
        super.onTrimMemory(level)
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}
