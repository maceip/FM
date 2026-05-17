package com.google.research.air.cosmo.controller

import com.google.research.air.cosmo.audiocapture.AmbientAudioCaptor
import com.google.research.air.cosmo.audiocapture.BudsAudioCaptor
import com.google.research.air.cosmo.audiocapture.DeviceAudioCaptor
import com.google.research.air.cosmo.audiocapture.WatchAudioCaptor
import com.google.research.air.cosmo.contentcapture.PeriodicScreenCaptor
import com.google.research.air.cosmo.contentcapture.ScreenObservation
import com.google.research.air.cosmo.devicediscovery.AmbientLanPeerBridge
import com.google.research.air.cosmo.gatekeeping.Gatekeeper
import com.google.research.air.cosmo.glasses.CosmoGlassManager
import com.google.research.air.cosmo.interactioncapture.ScreenInteraction
import com.google.research.air.cosmo.interactioncapture.ScreenInteractionCaptor
import com.google.research.air.cosmo.lib.soda.TranscriptionData
import com.google.research.air.cosmo.locationcapture.LocationCaptor
import com.google.research.air.cosmo.locationcapture.LocationObservation
import com.google.research.air.cosmo.notificationcapture.NotificationCaptureController
import com.google.research.air.cosmo.setting.CosmoSettingSerializer
import com.google.research.air.cosmo.setting.CosmoSettingStore
import com.google.research.air.cosmo.setting.CosmoState
import com.google.research.air.cosmo.setting.CosmoStateManager
import com.google.research.air.cosmo.setting.proto.CosmoSetting
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CosmoControllerTest {
    @Test
    fun pauseMicAudioCaptureStopsAllAudioSourceJobs() = runTest {
        val harness =
            harness(
                setting =
                    audioSetting(
                        ambient = true,
                        buds = true,
                        media = true,
                        watch = true
                    )
            )

        try {
            harness.controller.init()
            advanceUntilIdle()

            assertTrue(harness.controller.isAnyMicAudioCaptureOn())
            assertEquals(1, harness.ambient.starts)
            assertEquals(1, harness.buds.starts)
            assertEquals(1, harness.device.starts)
            assertEquals(1, harness.watch.starts)

            harness.controller.pauseMicAudioCapture()
            advanceUntilIdle()

            assertFalse(harness.controller.isAnyMicAudioCaptureOn())
            assertEquals(1, harness.ambient.stops)
            assertEquals(1, harness.buds.stops)
            assertEquals(1, harness.device.stops)
            assertEquals(1, harness.watch.stops)
        } finally {
            harness.controller.close()
            advanceUntilIdle()
        }
    }

    @Test
    fun resumeMicAudioCaptureRestartsOnlyEnabledAudioSources() = runTest {
        val harness =
            harness(
                setting =
                    audioSetting(
                        ambient = true,
                        buds = false,
                        media = true,
                        watch = false
                    )
            )

        try {
            harness.controller.init()
            advanceUntilIdle()
            harness.controller.pauseMicAudioCapture()
            advanceUntilIdle()

            harness.controller.resumeMicAudioCapture()
            advanceUntilIdle()

            assertTrue(harness.controller.isAnyMicAudioCaptureOn())
            assertEquals(2, harness.ambient.starts)
            assertEquals(0, harness.buds.starts)
            assertEquals(2, harness.device.starts)
            assertEquals(0, harness.watch.starts)
        } finally {
            harness.controller.close()
            advanceUntilIdle()
        }
    }

    @Test
    fun resumeMicAudioCaptureBeforeInitInitializesAndStartsEnabledAudio() = runTest {
        val harness = harness(setting = audioSetting(ambient = true))

        try {
            harness.controller.resumeMicAudioCapture()
            advanceUntilIdle()

            verify(exactly = 1) { harness.notificationController.initialize() }
            verify(exactly = 1) { harness.gatekeeper.start() }
            verify(exactly = 1) { harness.glassManager.startGlassSettingsListener() }
            verify(exactly = 1) { harness.stateManager.updateAndScheduleCosmoState() }
            assertEquals(1, harness.ambient.starts)
        } finally {
            harness.controller.close()
            advanceUntilIdle()
        }
    }

    private fun TestScope.harness(
        setting: CosmoSetting = audioSetting()
    ): Harness {
        val settingFlow = MutableStateFlow(setting)
        val stateFlow = MutableStateFlow(CosmoState.ENABLED)
        val settings = mockk<CosmoSettingStore>()
        every { settings.getCosmoSettingFlow() } returns settingFlow
        every { settings.isGlobalCosmoEnabled() } answers { settingFlow.value.globalCosmoEnabled }
        every { settings.isAmbientAudioEnabled() } answers { settingFlow.value.ambientAudioEnabled }
        every { settings.isBudsAudioEnabled() } answers { settingFlow.value.budsAudioEnabled }
        every { settings.isMediaAudioEnabled() } answers { settingFlow.value.mediaAudioEnabled }
        every { settings.isWatchAudioEnabled() } answers { settingFlow.value.watchAudioEnabled }
        every { settings.isConnectToGlassEnabled() } answers { settingFlow.value.connectToGlassEnabled }
        every { settings.isScreenContextEnabled() } answers { settingFlow.value.screenContextEnabled }
        every { settings.isScreenInteractionCaptureEnabled() } answers { settingFlow.value.screenInteractionCaptureEnabled }
        every { settings.isLocationCaptureEnabled() } answers { settingFlow.value.locationCaptureEnabled }
        every { settings.isDeviceControlEnabled() } answers { settingFlow.value.deviceControlEnabled }

        val stateManager = mockk<CosmoStateManager>()
        every { stateManager.getCosmoState() } returns stateFlow
        justRun { stateManager.updateAndScheduleCosmoState() }

        val notificationController = mockk<NotificationCaptureController>(relaxed = true)
        val gatekeeper = mockk<Gatekeeper>(relaxed = true)
        val glassManager = mockk<CosmoGlassManager>(relaxed = true)
        val peerBridge = mockk<AmbientLanPeerBridge>(relaxed = true)
        val ambient = RecordingAudioCaptor()
        val buds = RecordingAudioCaptor()
        val device = RecordingAudioCaptor()
        val watch = RecordingAudioCaptor()

        val controller =
            CosmoController(
                periodicScreenCaptor = NoopScreenCaptor,
                screenInteractionCaptor = NoopInteractionCaptor,
                ambientAudioCaptor = ambient,
                budsAudioCaptor = buds,
                deviceAudioCaptor = device,
                watchAudioCaptor = watch,
                glassManager = glassManager,
                locationCaptor = NoopLocationCaptor,
                ambientLanPeerBridge = peerBridge,
                notificationCaptureManager = notificationController,
                gatekeeper = gatekeeper,
                settingStore = settings,
                cosmoStateManager = stateManager,
                scope = this
            )
        return Harness(
            controller = controller,
            ambient = ambient,
            buds = buds,
            device = device,
            watch = watch,
            notificationController = notificationController,
            gatekeeper = gatekeeper,
            glassManager = glassManager,
            stateManager = stateManager
        )
    }

    private fun audioSetting(
        ambient: Boolean = false,
        buds: Boolean = false,
        media: Boolean = false,
        watch: Boolean = false
    ): CosmoSetting =
        CosmoSettingSerializer.defaultValue
            .toBuilder()
            .setGlobalCosmoEnabled(true)
            .setAmbientAudioEnabled(ambient)
            .setBudsAudioEnabled(buds)
            .setMediaAudioEnabled(media)
            .setWatchAudioEnabled(watch)
            .setConnectToGlassEnabled(false)
            .setScreenContextEnabled(false)
            .setScreenInteractionCaptureEnabled(false)
            .setLocationCaptureEnabled(false)
            .setDeviceControlEnabled(false)
            .build()

    private data class Harness(
        val controller: CosmoController,
        val ambient: RecordingAudioCaptor,
        val buds: RecordingAudioCaptor,
        val device: RecordingAudioCaptor,
        val watch: RecordingAudioCaptor,
        val notificationController: NotificationCaptureController,
        val gatekeeper: Gatekeeper,
        val glassManager: CosmoGlassManager,
        val stateManager: CosmoStateManager
    )

    private class RecordingAudioCaptor : AmbientAudioCaptor, BudsAudioCaptor, DeviceAudioCaptor, WatchAudioCaptor {
        var starts = 0
            private set
        var stops = 0
            private set

        override fun startCapture(): Flow<TranscriptionData> =
            flow {
                starts += 1
                try {
                    awaitCancellation()
                } finally {
                    stops += 1
                }
            }

        override fun flush() = Unit
    }

    private object NoopScreenCaptor : PeriodicScreenCaptor {
        override fun startCapture(): Flow<ScreenObservation> = emptyFlow()
    }

    private object NoopInteractionCaptor : ScreenInteractionCaptor {
        override fun startCapture(): Flow<ScreenInteraction> = emptyFlow()
    }

    private object NoopLocationCaptor : LocationCaptor {
        override fun startCapture(): Flow<LocationObservation> = emptyFlow()

        override fun lastKnown(): LocationObservation? = null
    }
}
