package com.google.research.air.cosmo.batch

import android.content.Context
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.research.air.cosmo.fulfillment.FulfillmentOrchestrator
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class BatteryAwareBatchWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val orchestrator: FulfillmentOrchestrator,
    private val settings: CosmoSettingStore,
    private val batteryManager: BatteryManager
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (!settings.isGlobalCosmoEnabled() || !settings.isBatchModeEnabled() || settings.isSnoozed()) {
            return Result.success()
        }
        val batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (batteryPercent in 0..MINIMUM_BATTERY_PERCENT) {
            return Result.retry()
        }
        return runCatching {
            orchestrator.handleBatchRun()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val MINIMUM_BATTERY_PERCENT = 20
    }
}

@Singleton
class BatchWorkScheduler
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val settings: CosmoSettingStore
) {
    fun schedule() {
        val intervalMinutes = settings.getBatchProcessingIntervalMin().coerceAtLeast(MIN_PERIODIC_INTERVAL_MINUTES)
        val request =
            PeriodicWorkRequestBuilder<BatteryAwareBatchWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
                .setConstraints(batchConstraints())
                .addTag(WORK_TAG)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private fun batchConstraints(): Constraints = Constraints
        .Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresBatteryNotLow(true)
        .setRequiresDeviceIdle(true)
        .build()

    companion object {
        const val MIN_PERIODIC_INTERVAL_MINUTES = 15
        const val UNIQUE_WORK_NAME = "cosmo_batch_fulfillment"
        const val WORK_TAG = "cosmo_batch"
    }
}
