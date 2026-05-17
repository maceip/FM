package com.google.research.air.cosmo.lib.soda

import android.content.Context
import android.os.Build
import com.google.speech.soda.AudioProto
import com.google.speech.soda.ApplicationDomainProto
import com.google.speech.soda.ClientInfoProto
import com.google.speech.soda.HybridAsrConfigProto
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
     *   - `onDeviceAsrConfig.applicationDomain` → `AMBIENT_CONTINUOUS`
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
                // Match recovered cosmo exactly: SODA is the transcript source. Directedness
                // comes from the audio gatekeeper backend, not from SODA suppressing transcripts.
                .setApplicationDomain(ApplicationDomainProto.ApplicationDomain.AMBIENT_CONTINUOUS)
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
        return SodaCoreConfigProto.SodaCoreConfig
            .newBuilder()
            .setCacheDirectory(cacheDir.absolutePath)
            .setOnDeviceAsrConfig(asrConfig)
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
