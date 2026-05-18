package com.google.research.air.cosmo.setting

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.security.AndroidKeyStoreAead
import com.google.research.air.cosmo.security.EncryptedSerializer
import com.google.research.air.cosmo.setting.proto.CosmoSetting
import com.google.research.air.cosmo.setting.proto.FulfillmentModel
import com.google.research.air.cosmo.setting.proto.ProcessingMode
import com.google.research.air.cosmo.setting.proto.ProcessingMode.PROCESSING_MODE_BATCH
import dagger.hilt.EntryPoint
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object CosmoSettingSerializer : Serializer<CosmoSetting> {
    override val defaultValue: CosmoSetting =
        CosmoSetting
            .newBuilder()
            .setEphemeralBufferDurationMin(5)
            .setMaxRawAudioInFulfillmentSec(30)
            .setCosmoStartDayTime(8 * 60)
            .setCosmoEndDayTime(22 * 60)
            .setBatchProcessingIntervalMins(30)
            .setCosmoTransparencyLevel(0.92f)
            .setGlobalCosmoEnabled(true)
            .setAmbientAudioEnabled(true)
            .setScreenContextEnabled(true)
            .setScreenInteractionCaptureEnabled(true)
            .setNotificationCaptureEnabled(true)
            .setGatekeeperEnabled(true)
            .setIntentExtractionEnabled(true)
            .setAnalyticsEnabled(true)
            .setAutoSnoozeEnabled(true)
            .setBatchModeEnabled(true)
            .setCameraEnabled(false)
            .setProcessingMode(ProcessingMode.PROCESSING_MODE_HYBRID)
            .setFulfillmentModel(FulfillmentModel.FULFILLMENT_MODEL_NETWORK_AWARE)
            .build()

    override suspend fun readFrom(input: InputStream): CosmoSetting = CosmoSetting.parseFrom(input)

    override suspend fun writeTo(
        t: CosmoSetting,
        output: OutputStream
    ) = t.writeTo(output)
}

@Module
@InstallIn(SingletonComponent::class)
object CosmoSettingModule {
    @Provides
    @Singleton
    fun provideCosmoSettingDataStore(
        @ApplicationContext context: Context
    ): DataStore<CosmoSetting> = DataStoreFactory.create(
        serializer = EncryptedSerializer(CosmoSettingSerializer, AndroidKeyStoreAead(COSMO_SETTING_KEY_ALIAS)),
        produceFile = { context.dataStoreFile("cosmo_setting.pb") }
    )

    private const val COSMO_SETTING_KEY_ALIAS = "cosmo_setting_datastore_v1"
}

@Singleton
class CosmoSettingStore
@Inject
constructor(
    private val dataStore: DataStore<CosmoSetting>,
    @ApplicationScope private val scope: CoroutineScope
) {
    val settingState: StateFlow<CosmoSetting> =
        dataStore.data.stateIn(
            scope,
            SharingStarted.Eagerly,
            CosmoSettingSerializer.defaultValue
        )

    val currentSetting: CosmoSetting get() = settingState.value

    fun getCosmoSettingFlow(): StateFlow<CosmoSetting> = settingState

    fun getEphemeralBufferDurationMin(): Int = currentSetting.ephemeralBufferDurationMin.coerceAtLeast(1)

    fun getMaxRawAudioInFulfillmentSec(): Int = currentSetting.maxRawAudioInFulfillmentSec.coerceAtLeast(1)

    fun getCosmoStartTime(): Int = currentSetting.cosmoStartDayTime

    fun getCosmoEndTime(): Int = currentSetting.cosmoEndDayTime

    fun getBatchProcessingIntervalMin(): Int = currentSetting.batchProcessingIntervalMins.coerceAtLeast(1)

    fun getFulfillmentModel(): FulfillmentModel = currentSetting.fulfillmentModel

    fun getProcessingMode(): ProcessingMode = currentSetting.processingMode

    fun isRealtimeProcessingEnabled(): Boolean = getProcessingMode() != PROCESSING_MODE_BATCH

    fun getSnoozeUntil(): Instant? = currentSetting.snoozeUntilTimestampMs.takeIf { it > 0 }?.let(Instant::ofEpochMilli)

    fun isSnoozed(clock: Clock = Clock.systemDefaultZone()): Boolean = getSnoozeUntil()?.isAfter(clock.instant()) == true

    fun isGlobalCosmoEnabled(): Boolean = currentSetting.globalCosmoEnabled

    fun isAmbientAudioEnabled(): Boolean = currentSetting.ambientAudioEnabled

    fun isBudsAudioEnabled(): Boolean = currentSetting.budsAudioEnabled

    fun isCameraEnabled(): Boolean = currentSetting.cameraEnabled

    fun isConnectToGlassEnabled(): Boolean = currentSetting.connectToGlassEnabled

    fun isGlassesCameraEnabled(): Boolean = currentSetting.glassesCameraEnabled

    fun isMediaAudioEnabled(): Boolean = currentSetting.mediaAudioEnabled

    fun isNotificationCaptureEnabled(): Boolean = currentSetting.notificationCaptureEnabled

    fun isScreenContextEnabled(): Boolean = currentSetting.screenContextEnabled

    fun isScreenInteractionCaptureEnabled(): Boolean = currentSetting.screenInteractionCaptureEnabled

    fun isIntentExtractionEnabled(): Boolean = currentSetting.intentExtractionEnabled

    fun isLocationCaptureEnabled(): Boolean = currentSetting.locationCaptureEnabled

    fun isGatekeeperEnabled(): Boolean = currentSetting.gatekeeperEnabled

    fun isRawAudioGatekeeperEnabled(): Boolean = currentSetting.rawAudioGatekeeperEnabled

    fun isWatchAudioEnabled(): Boolean = currentSetting.watchAudioEnabled

    fun isUseNanoappAudioEnabled(): Boolean = currentSetting.useNanoappAudioEnabled

    fun isAnalyticsEnabled(): Boolean = currentSetting.analyticsEnabled

    fun isBatchModeEnabled(): Boolean = currentSetting.batchModeEnabled

    fun isVoiceMatchEnabled(): Boolean = currentSetting.voiceMatchEnabled

    fun isStreamToWatchEnabled(): Boolean = currentSetting.streamToWatchEnabled

    fun isAutoSnoozeEnabled(): Boolean = currentSetting.autoSnoozeEnabled

    fun isSuggestCalendarEnabled(): Boolean = currentSetting.suggestCalendarEnabled

    fun isSuggestTimerEnabled(): Boolean = currentSetting.suggestTimerEnabled

    fun isSuggestGoogleSearchesEnabled(): Boolean = currentSetting.suggestGoogleSearchesEnabled

    fun isSuggestDocsEnabled(): Boolean = currentSetting.suggestDocsEnabled

    fun isSuggestMailEnabled(): Boolean = currentSetting.suggestMailEnabled

    fun isDraftSmsEnabled(): Boolean = currentSetting.draftSmsEnabled

    fun isInvokeAgentQEnabled(): Boolean = currentSetting.invokeAgentQEnabled

    fun isDeviceControlEnabled(): Boolean = currentSetting.deviceControlEnabled

    fun isOnboardingCompleted(): Boolean = currentSetting.onboardingCompleted

    suspend fun markOnboardingCompleted() = update { onboardingCompleted = true }

    suspend fun update(transform: CosmoSetting.Builder.() -> Unit) {
        dataStore.updateData { current -> current.toBuilder().apply(transform).build() }
    }

    fun launchUpdate(transform: CosmoSetting.Builder.() -> Unit) {
        scope.launch { update(transform) }
    }

    suspend fun setGlobalCosmoEnabled(enabled: Boolean) = update { globalCosmoEnabled = enabled }

    suspend fun setAmbientAudioEnabled(enabled: Boolean) = update { ambientAudioEnabled = enabled }

    suspend fun setScreenContextEnabled(enabled: Boolean) = update { screenContextEnabled = enabled }

    suspend fun setScreenInteractionCaptureEnabled(enabled: Boolean) = update { screenInteractionCaptureEnabled = enabled }

    suspend fun setNotificationCaptureEnabled(enabled: Boolean) = update { notificationCaptureEnabled = enabled }

    suspend fun setCameraEnabled(enabled: Boolean) = update { cameraEnabled = enabled }

    suspend fun setGatekeeperEnabled(enabled: Boolean) = update { gatekeeperEnabled = enabled }

    suspend fun setRawAudioGatekeeperEnabled(enabled: Boolean) = update { rawAudioGatekeeperEnabled = enabled }

    suspend fun setProcessingMode(mode: com.google.research.air.cosmo.setting.proto.ProcessingMode) =
        update { processingMode = mode }

    suspend fun setIntentExtractionEnabled(enabled: Boolean) = update { intentExtractionEnabled = enabled }

    suspend fun setLocationCaptureEnabled(enabled: Boolean) = update { locationCaptureEnabled = enabled }

    suspend fun setSnoozeUntil(instant: Instant) = update {
        snoozeUntilTimestampMs = instant.toEpochMilli()
        snoozeUntilTimeEpochMs = instant.toEpochMilli()
    }

    suspend fun clearSnooze() = update {
        snoozeUntilTimestampMs = 0
        snoozeUntilTimeEpochMs = 0
    }

    suspend fun setCosmoStartTime(minutesAfterMidnight: Int) = update { cosmoStartDayTime = minutesAfterMidnight.coerceIn(0, 1439) }

    suspend fun setCosmoEndTime(minutesAfterMidnight: Int) = update { cosmoEndDayTime = minutesAfterMidnight.coerceIn(0, 1439) }

    fun enabledSourcesFlow(): StateFlow<Set<String>> = settingState
        .map { setting ->
            buildSet {
                if (setting.ambientAudioEnabled) add("AUDIO")
                if (setting.screenContextEnabled) add("SCREEN")
                if (setting.notificationCaptureEnabled) add("NOTIFICATION")
                if (setting.cameraEnabled) add("CAMERA")
                if (setting.screenInteractionCaptureEnabled) add("INTERACTION")
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptySet())
}

enum class CosmoState {
    ENABLED,
    DISABLED,
    SNOOZED,
    OUTSIDE_ACTIVE_HOURS,
    LOW_BATTERY
}

@Singleton
class CosmoConfiguration
@Inject
constructor() {
    val isExternal: Boolean = false
    val isTeamfood: Boolean = true

    fun isCosmoSkillsEnabled(): Boolean = true

    fun isAnalyticsSettingsVisible(): Boolean = true

    fun isBufferDurationSettingsVisible(): Boolean = true

    fun isRawAudioGatekeeperToggleVisible(): Boolean = true

    fun isProcessingModeToggleVisible(): Boolean = true
}

@Singleton
class CosmoStateManager
@Inject
constructor(
    private val store: CosmoSettingStore,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val alarmManager: AlarmManager,
    private val clock: Clock
) {
    private val _cosmoState = kotlinx.coroutines.flow.MutableStateFlow(CosmoState.DISABLED)
    val stateFlow: StateFlow<CosmoState> = _cosmoState

    fun getCosmoState(): StateFlow<CosmoState> = stateFlow

    fun enableCosmo() = scope.launch {
        store.setGlobalCosmoEnabled(true)
        updateAndScheduleCosmoState()
    }

    fun disableCosmo() = scope.launch {
        store.setGlobalCosmoEnabled(false)
        updateAndScheduleCosmoState()
    }

    fun clearSnooze() = scope.launch {
        store.clearSnooze()
        updateAndScheduleCosmoState()
    }

    fun setSnoozeUntil(instant: Instant) = scope.launch {
        store.setSnoozeUntil(instant)
        updateAndScheduleCosmoState()
    }

    fun setCosmoStartTime(minutesAfterMidnight: Int) = scope.launch {
        store.setCosmoStartTime(minutesAfterMidnight)
        updateAndScheduleCosmoState()
    }

    fun setCosmoEndTime(minutesAfterMidnight: Int) = scope.launch {
        store.setCosmoEndTime(minutesAfterMidnight)
        updateAndScheduleCosmoState()
    }

    fun handleBatteryLevelChange(batteryPercent: Float) {
        if (batteryPercent < BATTERY_LOW_THRESHOLD && store.isAutoSnoozeEnabled()) {
            setSnoozeUntil(clock.instant().plus(LOW_BATTERY_SNOOZE_DURATION))
            _cosmoState.value = CosmoState.LOW_BATTERY
        }
    }

    fun updateAndScheduleCosmoState() {
        _cosmoState.value = computeState(clock.instant())
        scheduleNextStateUpdate(clock.instant())
    }

    private fun computeState(now: Instant): CosmoState = when {
        !store.isGlobalCosmoEnabled() -> CosmoState.DISABLED
        store.isSnoozed(clock) -> CosmoState.SNOOZED
        isInsideActiveHours(now) -> CosmoState.ENABLED
        else -> CosmoState.OUTSIDE_ACTIVE_HOURS
    }

    private fun isInsideActiveHours(now: Instant): Boolean {
        val local = LocalTime.ofInstant(now, ZoneId.systemDefault())
        val minute = local.hour * 60 + local.minute
        val start = store.getCosmoStartTime()
        val end = store.getCosmoEndTime()
        return if (start <= end) minute in start until end else minute >= start || minute < end
    }

    private fun scheduleNextStateUpdate(now: Instant) {
        val candidates =
            listOf(store.getCosmoStartTime(), store.getCosmoEndTime())
                .map { nextOccurrence(now, it) }
                .plus(store.getSnoozeUntil()?.takeIf { it.isAfter(now) })
                .filterNotNull()
        val next = candidates.minOrNull() ?: now.plus(Duration.ofHours(1))
        val pendingIntent =
            PendingIntent.getBroadcast(
                context,
                COSMO_STATE_UPDATE_REQUEST_CODE,
                Intent(context, CosmoStateUpdateReceiver::class.java).setAction(COSMO_STATE_UPDATE_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toEpochMilli(), pendingIntent)
        }
    }

    private fun nextOccurrence(
        now: Instant,
        minutesAfterMidnight: Int
    ): Instant {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.ofInstant(now, zone)
        val targetTime = LocalTime.of(minutesAfterMidnight / 60, minutesAfterMidnight % 60)
        val candidate = today.atTime(targetTime).atZone(zone).toInstant()
        return if (candidate.isAfter(now)) {
            candidate
        } else {
            today
                .plusDays(1)
                .atTime(targetTime)
                .atZone(zone)
                .toInstant()
        }
    }

    companion object {
        // C6: cosmo's CosmoStateManager.smali uses 15% as the low-battery threshold (the
        // architecture-delta marching orders cite it as the suspend boundary). Genesis uses
        // a longer snooze duration in lieu of explicit hysteresis: once battery drops below
        // 15% and auto-snooze is on, capture is suspended for LOW_BATTERY_SNOOZE_DURATION.
        // That window prevents thrash from quick battery fluctuations on a charger.
        const val BATTERY_LOW_THRESHOLD = 15.0f
        const val COSMO_STATE_UPDATE_ACTION = "com.google.research.air.cosmo.setting.STATE_UPDATE"
        const val COSMO_STATE_UPDATE_REQUEST_CODE = 1001
        private val LOW_BATTERY_SNOOZE_DURATION: Duration = Duration.ofHours(2)
    }
}

class CosmoStateUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        if (intent.action == CosmoStateManager.COSMO_STATE_UPDATE_ACTION) {
            try {
                EntryPointAccessors
                    .fromApplication(context.applicationContext, CosmoStateUpdateReceiverEntryPoint::class.java)
                    .cosmoStateManager()
                    .updateAndScheduleCosmoState()
            } catch (error: IllegalStateException) {
                if (context.applicationContext.javaClass.name == HILT_TEST_APPLICATION_CLASS_NAME) {
                    Log.w(TAG, "Skipping state update before Hilt test component exists.", error)
                } else {
                    throw error
                }
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CosmoStateUpdateReceiverEntryPoint {
        fun cosmoStateManager(): CosmoStateManager
    }

    companion object {
        private const val TAG = "CosmoStateUpdateReceiver"
        private const val HILT_TEST_APPLICATION_CLASS_NAME = "dagger.hilt.android.testing.HiltTestApplication"
    }
}
