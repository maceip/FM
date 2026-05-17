package com.google.research.air.cosmo.controller

import com.google.research.air.cosmo.audiocapture.AmbientAudioCaptor
import com.google.research.air.cosmo.audiocapture.AudioCaptor
import com.google.research.air.cosmo.audiocapture.BudsAudioCaptor
import com.google.research.air.cosmo.audiocapture.DeviceAudioCaptor
import com.google.research.air.cosmo.audiocapture.WatchAudioCaptor
import com.google.research.air.cosmo.contentcapture.PeriodicScreenCaptor
import com.google.research.air.cosmo.di.ApplicationScope
import com.google.research.air.cosmo.devicediscovery.AmbientLanPeerBridge
import com.google.research.air.cosmo.gatekeeping.Gatekeeper
import com.google.research.air.cosmo.glasses.CosmoGlassManager
import com.google.research.air.cosmo.interactioncapture.ScreenInteractionCaptor
import com.google.research.air.cosmo.locationcapture.LocationCaptor
import com.google.research.air.cosmo.notificationcapture.NotificationCaptureController
import com.google.research.air.cosmo.setting.CosmoState
import com.google.research.air.cosmo.setting.CosmoStateManager
import com.google.research.air.cosmo.setting.CosmoSettingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class CosmoController
@Inject
constructor(
    private val periodicScreenCaptor: PeriodicScreenCaptor,
    private val screenInteractionCaptor: ScreenInteractionCaptor,
    private val ambientAudioCaptor: AmbientAudioCaptor,
    private val budsAudioCaptor: BudsAudioCaptor,
    private val deviceAudioCaptor: DeviceAudioCaptor,
    private val watchAudioCaptor: WatchAudioCaptor,
    private val glassManager: CosmoGlassManager,
    private val locationCaptor: LocationCaptor,
    private val ambientLanPeerBridge: AmbientLanPeerBridge,
    private val notificationCaptureManager: NotificationCaptureController,
    private val gatekeeper: Gatekeeper,
    private val settingStore: CosmoSettingStore,
    private val cosmoStateManager: CosmoStateManager,
    @ApplicationScope private val scope: CoroutineScope
) : CosmoStateSource {
    private var initialized = false
    private var settingsJob: Job? = null
    private var audioJob: Job? = null
    private var budsAudioJob: Job? = null
    private var deviceAudioJob: Job? = null
    private var watchAudioJob: Job? = null
    private var screenJob: Job? = null
    private var interactionJob: Job? = null
    private var locationJob: Job? = null
    private var glassJob: Job? = null
    private var deviceDiscoveryJob: Job? = null

    fun init() {
        if (initialized) return
        initialized = true
        notificationCaptureManager.initialize()
        gatekeeper.start()
        glassManager.startGlassSettingsListener()
        cosmoStateManager.updateAndScheduleCosmoState()
        settingsJob =
            scope.launch {
                settingStore.getCosmoSettingFlow().combine(cosmoStateManager.getCosmoState()) { _, state -> state }.collectLatest {
                    reconcileCaptureJobs()
                }
            }
    }

    override fun isCosmoEnabled(): Boolean = settingStore.isGlobalCosmoEnabled()

    fun isAmbientAudioCaptureOn(): Boolean = audioJob?.isActive == true

    fun isBudsAudioCaptureOn(): Boolean = budsAudioJob?.isActive == true

    fun isDeviceAudioCaptureOn(): Boolean = deviceAudioJob?.isActive == true

    fun isWatchAudioCaptureOn(): Boolean = watchAudioJob?.isActive == true

    override fun isAnyMicAudioCaptureOn(): Boolean =
        isAmbientAudioCaptureOn() ||
            isBudsAudioCaptureOn() ||
            isDeviceAudioCaptureOn() ||
            isWatchAudioCaptureOn()

    fun close() {
        settingsJob = stopJob(settingsJob)
        stopCaptureJobs()
    }

    fun isRawAudioGatekeeperOn(): Boolean =
        settingStore.isAmbientAudioEnabled() && settingStore.isRawAudioGatekeeperEnabled()

    fun isScreenCaptureOn(): Boolean = screenJob?.isActive == true

    fun isScreenInteractionCaptureOn(): Boolean = interactionJob?.isActive == true

    fun isLocationCaptureOn(): Boolean = locationJob?.isActive == true

    fun isGlassCaptureOn(): Boolean = glassJob?.isActive == true

    fun isAmbientDeviceDiscoveryOn(): Boolean = deviceDiscoveryJob?.isActive == true

    fun pauseMicAudioCapture() {
        audioJob = stopJob(audioJob)
        budsAudioJob = stopJob(budsAudioJob)
        deviceAudioJob = stopJob(deviceAudioJob)
        watchAudioJob = stopJob(watchAudioJob)
    }

    fun resumeMicAudioCapture() {
        if (!initialized) {
            init()
        }
        if (captureAllowed()) reconcileAudioJobs()
    }

    private fun reconcileCaptureJobs() {
        if (!captureAllowed()) {
            stopCaptureJobs()
            return
        }
        reconcileAudioJobs()
        glassJob = reconcileJob(glassJob, settingStore.isConnectToGlassEnabled()) {
            scope.launch {
                try {
                    glassManager.startProjectedCapture()
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    glassManager.stopProjectedCapture()
                }
            }
        }
        screenJob = reconcileJob(screenJob, settingStore.isScreenContextEnabled()) {
            scope.launch { periodicScreenCaptor.startCapture().collectLatest { } }
        }
        interactionJob = reconcileJob(interactionJob, settingStore.isScreenInteractionCaptureEnabled()) {
            scope.launch {
                screenInteractionCaptor.startCapture().collectLatest { }
            }
        }
        locationJob = reconcileJob(locationJob, settingStore.isLocationCaptureEnabled()) {
            scope.launch { locationCaptor.startCapture().collectLatest { } }
        }
        deviceDiscoveryJob = reconcileJob(deviceDiscoveryJob, settingStore.isDeviceControlEnabled()) {
            scope.launch {
                try {
                    ambientLanPeerBridge.start()
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    ambientLanPeerBridge.stop()
                }
            }
        }
    }

    private fun reconcileAudioJobs() {
        audioJob = reconcileJob(audioJob, settingStore.isAmbientAudioEnabled()) {
            startAudioJob(ambientAudioCaptor)
        }
        budsAudioJob = reconcileJob(budsAudioJob, settingStore.isBudsAudioEnabled()) {
            startAudioJob(budsAudioCaptor)
        }
        deviceAudioJob = reconcileJob(deviceAudioJob, settingStore.isMediaAudioEnabled()) {
            startAudioJob(deviceAudioCaptor)
        }
        watchAudioJob = reconcileJob(watchAudioJob, settingStore.isWatchAudioEnabled()) {
            startAudioJob(watchAudioCaptor)
        }
    }

    private fun captureAllowed(): Boolean {
        return settingStore.isGlobalCosmoEnabled() && cosmoStateManager.getCosmoState().value == CosmoState.ENABLED
    }

    private fun startAudioJob(
        captor: AudioCaptor
    ): Job =
        scope.launch {
            captor.startCapture().collectLatest { }
        }

    private fun reconcileJob(
        current: Job?,
        shouldRun: Boolean,
        start: () -> Job
    ): Job? {
        if (shouldRun) {
            return current?.takeIf { it.isActive } ?: start()
        }
        current?.cancel()
        return null
    }

    private fun stopJob(current: Job?): Job? {
        current?.cancel()
        return null
    }

    private fun stopCaptureJobs() {
        audioJob = stopJob(audioJob)
        budsAudioJob = stopJob(budsAudioJob)
        deviceAudioJob = stopJob(deviceAudioJob)
        watchAudioJob = stopJob(watchAudioJob)
        screenJob = stopJob(screenJob)
        interactionJob = stopJob(interactionJob)
        locationJob = stopJob(locationJob)
        glassJob = stopJob(glassJob)
        deviceDiscoveryJob = stopJob(deviceDiscoveryJob)
        glassManager.stopProjectedCapture()
        ambientLanPeerBridge.stop()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CosmoControllerModule {
    @Binds
    abstract fun bindCosmoStateSource(impl: CosmoController): CosmoStateSource
}
