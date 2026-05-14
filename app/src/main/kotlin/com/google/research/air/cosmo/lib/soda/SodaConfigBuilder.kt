package com.google.research.air.cosmo.lib.soda

import android.content.Context
import android.os.Build
import com.google.speech.soda.AudioProto
import com.google.speech.soda.ApplicationDomainProto
import com.google.speech.soda.ClientInfoProto
import com.google.speech.soda.HybridAsrConfigProto
import com.google.speech.soda.IntendedSpeechConfigProto
import com.google.speech.soda.LoggingProto
import com.google.speech.soda.SodaCoreConfigProto
import com.google.speech.soda.client.SodaClientConfigProto
import java.io.File

/**
 * Kotlin port of the recovered cosmo `com.google.research.air.cosmo.lib.soda.SodaConfigUtils`
 * (smali at `cosmo_baksmali/.../lib/soda/SodaConfigUtils.smali`). Builds the protobuf configs
 * that the recovered SODA `Soda.initSoda(SodaCoreConfig)` and `Soda.startCapture(SodaClientConfig)`
 * methods expect.
 *
 * The original cosmo class is in the same Java package as the genesis Kotlin code; we stripped
 * it from `app/libs/recovered-soda.jar` to avoid the duplicate-class build error and replace it
 * with this typed builder.
 */
internal object SodaConfigBuilder {
    private const val SAMPLE_RATE_HZ = 16_000
    private const val CHANNEL_COUNT_MONO = 1
    private const val MAX_CACHED_AUDIO_MS = 60_000
    private const val PLATFORM_ID_ANDROID = "android"

    /**
     * Mirror of `SodaConfigUtils.createDefaultCoreConfig(context, voiceMatchEnabled, forEnrollment, useFallback)`
     * with voiceMatch / enrollment paths omitted (we don't enroll speakers in the smoke test).
     * Fields populated:
     *   - `cacheDirectory` → `<filesDir>/soda`
     *   - `onDeviceAsrConfig.languagePackDirectory` → caller-supplied (the extracted `lp_cpu` dir)
     *   - `onDeviceAsrConfig.applicationDomain` → `AMBIENT_ONESHOT` (per-utterance pipeline that
     *     activates the IntendedSpeechDetector / Magic Mic; verified on-device 2026-05-10)
     *   - `onDeviceAsrConfig.attachHotwordEvent` → true (so HotwordEvents reach the callback)
     *   - `intendedSpeechConfig` → enabled, mode `INTENT_DETECTION_MODE_OPEN_MIC`,
     *     attachOpenMicResult — surfaces `intendedQueryScore`/`intendedQueryThreshold` on
     *     FinalRecognitionResult
     *   - `audioInputConfig.micsAudioFormat` → 16 kHz mono PCM (INT16)
     *   - `audioProcessingConfig.cachedAudioConfig.maxAudioMs` → 60 s
     *   - `audioLoggingPolicy.policyType` → `AUDIO_LOGGING_POLICY_TYPE_NONE`
     *   - `serverAsrConfig.enabled` → false
     *   - `clientInfo.hostAppId/applicationVersion/platformId/deviceModel`
     */
    fun coreConfig(
        context: Context,
        languagePackDirectory: File
    ): SodaCoreConfigProto.SodaCoreConfig {
        val cacheDir = File(context.filesDir, "soda").apply { mkdirs() }
        val micsFormat =
            AudioProto.RawAudioFormat
                .newBuilder()
                .setSampleFormat(AudioProto.RawAudioFormat.SampleFormat.INT16)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelCount(CHANNEL_COUNT_MONO)
                .build()
        val asrConfig =
            SodaCoreConfigProto.OnDeviceAsrConfig
                .newBuilder()
                .setLanguagePackDirectory(languagePackDirectory.absolutePath)
                // AMBIENT_ONESHOT routes ambient audio through SODA's per-utterance pipeline,
                // running the IntendedSpeechDetector (Magic Mic) on each segment. AMBIENT_CONTINUOUS
                // is pure-transcription with no gating; DEFAULT_ONESHOT is single-session.
                // AMBIENT_ONESHOT is the combination genesis needs: continuous listening with
                // per-utterance directedness scores attached to each FinalRecognitionResult.
                .setApplicationDomain(ApplicationDomainProto.ApplicationDomain.AMBIENT_ONESHOT)
                // Cosmo never sets this because cosmo only consumed transcripts. Genesis needs
                // HotwordEvents surfaced (Magic Mic discriminator); without
                // attachHotwordEvent=true, SODA computes the trigger internally but the event
                // never reaches the SodaCallback. Field 0x1c on OnDeviceAsrConfig per
                // SodaCoreConfigProto$OnDeviceAsrConfig.smali:47.
                .setAttachHotwordEvent(true)
                .build()
        val audioInputConfig =
            SodaCoreConfigProto.AudioInputConfig
                .newBuilder()
                .setMicsAudioFormat(micsFormat)
                .build()
        val audioProcessingConfig =
            SodaCoreConfigProto.AudioProcessingConfig
                .newBuilder()
                .setCachedAudioConfig(
                    SodaCoreConfigProto.CachedAudioConfig
                        .newBuilder()
                        .setMaxAudioMs(MAX_CACHED_AUDIO_MS)
                ).build()
        val audioLoggingPolicy =
            LoggingProto.AudioLoggingPolicy
                .newBuilder()
                .setPolicyType(LoggingProto.AudioLoggingPolicyType.AUDIO_LOGGING_POLICY_TYPE_NONE)
                .build()
        val serverAsrConfig =
            HybridAsrConfigProto.ServerAsrConfig
                .newBuilder()
                .setEnabled(false)
                .build()
        val clientInfo =
            ClientInfoProto.ClientInfo
                .newBuilder()
                .setApplicationVersion(versionName(context))
                .setPlatformId(PLATFORM_ID_ANDROID)
                .setDeviceModel(Build.MODEL.orEmpty().take(MAX_DEVICE_MODEL_CHARS))
                .build()
        // IntendedSpeechConfig is the Magic Mic / directedness gate. "Open Mic" is the
        // proto-internal name; "Magic Mic" is marketing. Per
        // IntendedSpeechConfigProto$IntendedSpeechConfig$IntentDetectionMode.smali:46-52:
        //   INTENT_DETECTION_MODE_OPEN_MIC (2) = the Magic Mic mode. SODA's directedness
        //   classifier runs and tags the resulting RecognitionEvent with TRIGGER_TYPE_MAGIC_MIC
        //   (or suppresses the transcript if undirected, depending on attach flags).
        // Cosmo never sets this — cosmo used the EdgeTPU Conformer's isInteresting head as its
        // directedness gate. Genesis activates Magic Mic here because we don't have a working
        // EdgeTPU Conformer on generic CPU hardware.
        val intendedSpeechConfig =
            IntendedSpeechConfigProto.IntendedSpeechConfig
                .newBuilder()
                .setEnabled(true)
                // OPEN_MIC mode runs SODA's IntendedSpeechDetector (Magic Mic) acoustic
                // classifier on each utterance. Verified on-device 2026-05-10: per-utterance
                // intendedQueryScores ranged 0.22-0.998 across 11 fixtures with reasonable
                // discrimination (real commands score high, partial/empty frames score low).
                // NONEMPTY is the degenerate wiring-check mode and must not ship.
                .setIntentDetectionMode(
                    IntendedSpeechConfigProto.IntendedSpeechConfig.IntentDetectionMode.INTENT_DETECTION_MODE_OPEN_MIC
                )
                .setAttachOpenMicResult(true)
                .build()
        return SodaCoreConfigProto.SodaCoreConfig
            .newBuilder()
            .setCacheDirectory(cacheDir.absolutePath)
            .setOnDeviceAsrConfig(asrConfig)
            .setIntendedSpeechConfig(intendedSpeechConfig)
            .setAudioInputConfig(audioInputConfig)
            .setAudioProcessingConfig(audioProcessingConfig)
            .setAudioLoggingPolicy(audioLoggingPolicy)
            .setServerAsrConfig(serverAsrConfig)
            .setClientInfo(clientInfo)
            .build()
    }

    /**
     * Genesis feeds PCM externally via `Soda.addAudio()`. The streaming-vs-non-streaming
     * distinction in SODA is keyed off the AudioProviderConfig variant: `AndroidAudioProvider`
     * tells SODA to own an internal AudioRecord (rejecting external `addAudio` with "Not a
     * streaming audio provider"), while `EncodedAudioProvider` with `InputType.STREAMING_DATA`
     * is the external-feed path that `addAudio` cooperates with.
     */
    fun clientConfig(): SodaClientConfigProto.SodaClientConfig {
        val rawFormat =
            AudioProto.RawAudioFormat
                .newBuilder()
                .setSampleFormat(AudioProto.RawAudioFormat.SampleFormat.INT16)
                .setSampleRate(SAMPLE_RATE_HZ)
                .setChannelCount(CHANNEL_COUNT_MONO)
                .build()
        val encodedProvider =
            SodaClientConfigProto.EncodedAudioProvider
                .newBuilder()
                .setAudioFormat(rawFormat)
                .setEncodingType(SodaClientConfigProto.EncodedAudioProvider.EncodingType.DEFAULT_RAW)
                .setInputType(SodaClientConfigProto.EncodedAudioProvider.InputType.STREAMING_DATA)
                .build()
        val providerConfig =
            SodaClientConfigProto.AudioProviderConfig
                .newBuilder()
                .setEncodedAudioProvider(encodedProvider)
                .build()
        return SodaClientConfigProto.SodaClientConfig
            .newBuilder()
            .setMicsAudioProviderConfig(providerConfig)
            .setRequireHotword(false)
            .build()
    }

    private fun versionName(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("")

    private const val MAX_DEVICE_MODEL_CHARS = 64
}
