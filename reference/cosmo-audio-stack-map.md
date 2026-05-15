# Cosmo Audio Stack — Static Analysis Map

Static-analysis map of every audio surface in the recovered Cosmo APK
(`com.google.research.air.cosmo`, antisplit v1.1).

Every claim in this document is artifact-backed. Sources:

- `cosmo_baksmali/` — smali; method signatures and embedded const-strings only.
- `cosmo_recovered_native_libs/lib/arm64-v8a/` — `nm -D` + `readelf -d`.
- `cosmo_recovered_large_assets/` — file sizes via `ls -la`.
- `cosmo_artifact_integrity/file_inventory.csv` — declared vs on-disk sizes.
- `aapt2 dump permissions` of `com.google.research.air.cosmo v1.1_antisplit_repaired_stubmodel_unsigned.apk`.

Where a body is stripped or obfuscated past recovery, the row says
`(body not recovered)`. Where a method's behavior cannot be read directly
from artifact, no behavioral claim is made — only signatures and the
external classes/methods it invokes (from `invoke-virtual` / `invoke-static`
lines).

Tags `[P:…]` mark the Android permissions a class declares or whose APIs
it invokes. Tags `[R:…]` mark reachability from a debug-keystore APK on a
retail Pixel:

- `[R:OK]` — runtime-grantable, no signing gate.
- `[R:OK-CONSENT]` — runtime user consent required (deep-link Settings or
  in-process system dialog).
- `[R:OK-COMPANION]` — requires a paired or bound external device.
- `[R:BLOCKED-SIG]` — permission is signature-protected; debug-keystore
  APKs cannot hold it.
- `[R:BLOCKED-FW]` — blocked at hardware/firmware level (e.g. AOC nanoapp
  loader signature verification).
- `[R:BLOCKED-OPT-IN]` — works in principle but typical target apps opt
  out via `allowAudioPlaybackCapture="false"`.

---

## Permissions Cosmo declared (verbatim from `aapt2 dump permissions`)

```
android.permission.ACCESS_CONTEXT_HUB                ← signature
android.permission.FOREGROUND_SERVICE
android.permission.FOREGROUND_SERVICE_MICROPHONE
android.permission.FOREGROUND_SERVICE_DATA_SYNC
android.permission.FOREGROUND_SERVICE_CAMERA
android.permission.POST_NOTIFICATIONS
android.permission.RECORD_AUDIO
android.permission.MODIFY_AUDIO_ROUTING              ← signature|privileged
android.permission.CAPTURE_AUDIO_OUTPUT              ← signature|privileged|role
android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT ← signature|privileged
android.permission.SYSTEM_ALERT_WINDOW
android.permission.WAKE_LOCK
android.permission.CAMERA
android.permission.CALL_PHONE
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
com.google.android.apps.aicore.service.BIND_SERVICE
android.permission.BIND_NOTIFICATION_LISTENER_SERVICE
android.permission.ACCESS_COARSE_LOCATION
android.permission.ACCESS_FINE_LOCATION
android.permission.ACCESS_BACKGROUND_LOCATION
android.permission.BLUETOOTH_CONNECT
android.permission.SCHEDULE_EXACT_ALARM
com.google.research.air.experiences.glimmer.altair.permission.WRITE_COSMO_CONTEXT
android.permission.READ_CALENDAR
android.permission.WRITE_CALENDAR
com.android.alarm.permission.SET_ALARM
permission: com.google.research.air.cosmo.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
```

The protection-level annotations come from public Android source
(`frameworks/base/core/res/AndroidManifest.xml`); they are not asserted
about Cosmo's build.

---

## Section A — Audio capture (input)

### A.1 `com.google.research.air.cosmo.lib.soda.SodaController` (abstract)

Method list (from `^\.method` grep, non-synthetic):

```
public final addAudioBytes(byte[]): void
protected abstract addSodaAudioBytes(byte[]): void
public abstract createSodaClientConfig$java_com_google_research_air_cosmo_lib_soda_soda(): SodaClientConfigProto.SodaClientConfig
public final enrollVoiceMatch(List): boolean
public final flush(): void
public final getSoda$java_com_google_research_air_cosmo_lib_soda_soda(): com.google.android.libraries.assistant.soda.Soda
public final setSoda$java_com_google_research_air_cosmo_lib_soda_soda(com.google.android.libraries.assistant.soda.Soda): void
public final declared-synchronized startCapture(boolean, kotlin.jvm.functions.Function1): void
public final declared-synchronized stopCapture(): void
private final getVoiceMatchEnrollmentConfig(List): SpeakerIdProto.VoiceMatchEnrollmentConfig
private final initSodaIfNecessary(boolean, Function1): void
private final startSodaCapture(): boolean
private final tryInitSoda(boolean, Function1, boolean): com.google.android.libraries.assistant.soda.Soda
public constructor <init>(Context, SodaTransportFactory, CosmoSettingStore, AudioAccumulator)
```

`.super` line: `Ljava/lang/Object;` (verified by grep).

Concrete subclasses (`.super Lcom/google/research/air/cosmo/lib/soda/SodaController;`,
verified by grep — only two):

```
SodaController$MicSodaController          (extends SodaController)
SodaController$StreamingSodaController    (extends SodaController)
```

The six other `*SodaController.smali` files in
`com/google/research/air/cosmo/lib/soda/` are not concrete subclasses;
they are `@Qualifier` annotation interfaces (each carries
`.class public interface abstract annotation` and
`.implements Ljava/lang/annotation/Annotation;`). Their role is to
disambiguate Hilt/javax-inject bindings:

```
BudsAudioSodaController          (annotation, @Qualifier)
CallAudioSodaController          (annotation, @Qualifier)
DefaultMicSodaController         (annotation, @Qualifier)
DeviceAudioSodaController        (annotation, @Qualifier)
StreamingMicSodaController       (annotation, @Qualifier)
WatchAudioSodaController         (annotation, @Qualifier)
```

`SodaControllerModule_Provide{Buds,Call,DefaultMic,Device,StreamingMic,
WatchAudio}SodaControllerFactory.smali` provide instances against those
qualifiers; each instance is one of the two concrete inner subclasses.

[P:RECORD_AUDIO] (transitively, via concrete subclass paths to `RecordService`).

### A.2 `com.google.research.air.cosmo.lib.soda.AudioAccumulator`

Methods:

```
public final declared-synchronized addAudioBytes(byte[]): void
public final flush(): void
public final declared-synchronized startCapture(boolean, Function1): void
public final declared-synchronized stopCapture(): void
private final flush(Function1): void
public constructor <init>(CoroutineScope, com.google.common.time.TimeSource)
companion-static synthetic accessors:
   $access$getAUDIO_GATEKEEPER_BUFFER_DURATION$cp(): long
   $access$getDEFAULT_BUFFER_DURATION$cp(): long
```

The two buffer-duration constants are package-private static fields exposed
via the `access$…$cp` synthetic accessors — present in smali, values not
read in this analysis.

### A.3 `com.google.research.air.cosmo.lib.soda.TranscriptionData`

Constructor signature:

```
public constructor <init>(java.lang.String, com.google.protobuf.ByteString,
                          java.lang.String, java.util.Map, long)
```

Public accessors (from `^\.method` grep):

```
public final getAudioData(): com.google.protobuf.ByteString
public final getSpeakerConfidenceScoreMap(): java.util.Map
public final getSpeakerLabel(): java.lang.String
public final getText(): java.lang.String
public final getTimestampMillis(): long
public final getTranscription(): java.lang.String
public final hasContent(): boolean
public final component1..5(): each accessor's underlying type
public final copy(...)
public equals / hashCode / toString
```

### A.4 `com.google.android.libraries.assistant.soda.Soda` (third-party library; bundled into the APK, not in the `com.google.research.air.cosmo` package tree)

Public constructors (eight overloads, from grep):

```
public <init>(Context)
public <init>(Context, SodaCallback)
public <init>(Context, SodaCallback, HotwordResourceManager)
public <init>(Context, SodaCallback, com.google.common.base.Optional)
public <init>(Context, SodaCallback, boolean)
public <init>(Context, ListeningExecutorService, ListeningScheduledExecutorService, long)
public <init>(Context, ListeningExecutorService, ListeningScheduledExecutorService, com.google.common.base.Optional)
```

Public non-native methods (selection):

```
public addAudio(java.nio.ByteBuffer, int)
public addTimestampedAudio(ByteBuffer, int, ByteBuffer, int)
public addTimestampedLoopbackAudio(ByteBuffer, int, ByteBuffer, int)
public cancelAsrSession()
public declared-synchronized cancelLiveTimeout()
public declared-synchronized clearDiarizationCache(StopTypeProto.StopType)
public collectDebugInfo(boolean)
public declared-synchronized delete()
public doGrecoTurn(boolean)
public enrollForVoiceMatch(SpeakerIdProto.VoiceMatchEnrollmentConfig): SpeakerIdProto.VoiceMatchEnrollmentEvent
public executeTasks(TaskProto.TaskInput)
public getSharedResources(): long
public declared-synchronized getSharedResourcesTimeoutDelay(): long
public declared-synchronized getSharedResourcesTimeoutElapsedMillis(): long
public getSodaCallback(): SodaCallback
public getSpeakerIdEnrollmentInfo(): SpeakerIdProto.EnrollmentInfo
public declared-synchronized hasActiveLiveTimeout(): boolean
public hasPendingGrecoTurn(): boolean
public declared-synchronized initSoda(SodaCoreConfigProto.SodaCoreConfig): StatusProto.ConfigResult
public declared-synchronized initSodaWithParams(SodaInitParams): StatusProto.ConfigResult
public declared-synchronized isInitialized(): boolean
public isRunning(): boolean
public logEvents(LoggingProto.LoggingInput)
public pushAudio(SodaInterface.PushAudioSource, SodaInterface.PushAudioInputError, AudioProto.Audio, j$.time.Duration): com.google.rpc.Status
public requiresReinitialization(SodaCoreConfigProto.SodaCoreConfig): boolean
public scheduleLiveTimeout(com.google.protobuf.Duration, java.lang.Runnable)
public setSodaCallback(SodaCallback)
public startAsrSession(TriggerTypeProto.TriggerType)
public startCapture(SodaClientConfigProto.SodaClientConfig): com.google.rpc.Status
public startCapture(SodaClientConfigProto.SodaClientConfig, java.io.InputStream, com.google.common.base.Optional): ListenableFuture
public startCapture(SodaClientConfigProto.SodaClientConfig, java.io.InputStream, Optional, Optional): ListenableFuture
public startCapture(): com.google.rpc.Status
public stopCapture()
public updateContext(SodaContextProto.ContextUpdate)
public updateRecognitionContext(SodaContextProto.RecognitionContext)
public updateRuntime(SodaRuntimeConfigProto.SodaRuntimeConfig)
public updateQuickPhrases(HotqueryConfigProto.ContextualHotqueryConfig)
native nativeConstruct(long): long                                (instance, package-private)
```

Static native methods:

```
public static native nativeCreateSharedResources(java.lang.Object): long
public static native nativeDeleteSharedResources(java.lang.Object, long): boolean
```

Instance-private native methods:

```
private native nativeAddAudio(long, ByteBuffer, long): boolean
private native nativeAddTimestampedAudio(long, ByteBuffer, long, ByteBuffer, long): boolean
private native nativeAddTimestampedLoopbackAudio(long, ByteBuffer, long, ByteBuffer, long): void
private native nativeCancelAsr(long): void
private native nativeCollectDebugInfo(long, boolean): void
private native nativeDelete(long): void
private native nativeDeleteDiarizationProcessor(long): boolean
private native nativeDeleteSharedResourcesIfDiarizationProcessorAbsent(long): boolean
private native nativeDoGrecoTurn(long, boolean): void
private native nativeEnrollForVoiceMatch(long, byte[]): byte[]
private native nativeExecuteTasks(long, byte[]): void
private native nativeGetSpeakerIdEnrollmentInfo(long): byte[]
private native nativeHasPendingGrecoTurn(long): boolean
private native nativeInit(long, byte[]): byte[]
private native nativeLogEvents(long, byte[]): void
private native nativePushAudio(long, int, int, ByteBuffer, long, int): byte[]
private native nativeRequiresReinitialization(long, byte[]): boolean
private native nativeSetApaLogger(long, SodaApaAppFlow): void
private native nativeSetDataProvider(long, data.SodaDataProviderJni): void
private native nativeSetTransportFactory(long, s3client.SodaTransportFactory): void
private native nativeStartAsr(long, int): void
private native nativeStartCapture(long, byte[]): byte[]
private native nativeStopCapture(long): void
private native nativeUpdateContext(long, byte[]): void
private native nativeUpdateRecognitionContext(long, byte[]): void
private native nativeUpdateRuntime(long, byte[]): void
```

Protected callback targets the .so calls back into:

```
protected handleStart(): void
protected handleStop(int): void
protected handleShutdown(): void
protected handleSodaEvent(byte[]): void
```

Other private helpers (from `^\.method` grep):

```
private createNativeSoda(): void
private static convertToStatus(byte[]): com.google.rpc.Status
private static createExecutor(java.lang.String, int): ListeningScheduledExecutorService
private static getNumChannels(SodaClientConfigProto.SodaClientConfig): int
private declared-synchronized delete(boolean): void
private synthetic lambda$updateSharedResourcesTimeout$0(com.google.protobuf.Duration): void
private static synthetic lambda$scheduleLiveTimeout$0(com.google.protobuf.Duration, java.lang.Runnable): void
private startCaptureInternal(SodaClientConfigProto.SodaClientConfig): com.google.rpc.Status
private possiblyStartSodaAudioAdapter(SodaClientConfigProto.SodaClientConfig): void
private static replaceHotwordData(SodaCoreConfigProto.SodaCoreConfig, com.google.protobuf.ByteString): SodaCoreConfigProto.SodaCoreConfig
private validateConstruct(): void
```

### A.5 `com.google.android.libraries.assistant.soda.SodaCallback` (interface; third-party library)

```
public abstract handleShutdown(): void
public abstract handleSodaEvent(SodaEventProto.SodaEvent): void
public abstract handleStart(): void
public abstract handleStop(SodaStopReason): void
```

### A.6 `com.google.android.libraries.assistant.soda.SodaStopReason` (enum; third-party library)

Enum constants from `enum` field declarations:

```
ERROR
HOTWORD_TIMEOUT
MIC_END_OF_DATA
STOP_CALLED
UNKNOWN
```

### A.7 `libsoda_dev_jni.so` — JNI exports + ELF NEEDED

48 `Java_…` exports total (the names appearing as `private native …` on
`com.google.android.libraries.assistant.soda.Soda` in §A.4 are the
caller-side declarations; the matching exports were verified via
`nm -D libsoda_dev_jni.so | grep ' T Java_'`).

ELF NEEDED (from `cosmo_baksmali_imports/native_libraries.md`):

```
liblog.so libm.so libEGL.so libGLESv2.so libdl.so libGLESv3.so libandroid.so libz.so libc.so
```

`libsoda_dev_jni.so` size: 49,730,232 bytes (from `file_inventory.csv`).

---

## Section B — RecordService (the foundational AudioRecord owner)

### B.1 `com.google.research.air.cosmo.lib.record.RecordService`

`.super Landroid/app/Service;` (verified by grep).

Methods (`^\.method` grep, non-synthetic):

```
public onBind(Intent): IBinder
public final getMicAudioRingBuffer(): com.google.research.air.cosmo.lib.util.ByteArrayRingBuffer
public final isAOCEnabled(): boolean
public final setAOCEnabled(boolean): void
public final isAocRecording(): boolean
public final isMicRecording(): boolean
public final startAocRecording(RecordServiceListener): void
public final stopAocRecording(): void
public final startMicRecording(RecordServiceListener): void
public final stopMicAudioRecording(): void
public final startDeviceAudioRecording(RecordServiceListener): void
public final stopDeviceAudioRecording(): void
public final startCallAudioRecording(RecordServiceListener): void
public final stopCallAudioRecording(): void
public final runMicRecording$java_com_google_research_air_cosmo_lib_record_record(RecordServiceListener): void
public final runDeviceAudioRecording$java_com_google_research_air_cosmo_lib_record_record(RecordServiceListener): void
public final runCallAudioRecording$java_com_google_research_air_cosmo_lib_record_record(RecordServiceListener): void
public final runRecordingLoop(android.media.AudioRecord, AtomicBoolean, RecordServiceListener): void
public final createAudioMixForDeviceAudio$java_com_google_research_air_cosmo_lib_record_record(android.media.AudioFormat): android.media.audiopolicy.AudioMix
```

Inner: `RecordService$LocalBinder` (`extends Binder`).

### B.2 `com.google.research.air.cosmo.lib.record.RecordServiceListener` (interface)

```
public abstract onBytesReceived(byte[]): void
public abstract onStopRecording(boolean): void
```

### B.3 Android system APIs invoked by `RecordService` (verbatim from
`invoke-virtual` / `invoke-static` lines)

```
android.media.AudioFormat$Builder.build()
android.media.AudioFormat$Builder.setChannelMask(int)
android.media.AudioFormat$Builder.setEncoding(int)
android.media.AudioFormat$Builder.setSampleRate(int)
android.media.AudioManager.registerAudioPolicy(AudioPolicy)
android.media.audiopolicy.AudioMix$Builder.build()
android.media.audiopolicy.AudioMix$Builder.setFormat(AudioFormat)
android.media.audiopolicy.AudioMix$Builder.setRouteFlags(int)
android.media.audiopolicy.AudioMixingRule$Builder.addRule(AudioAttributes, int)
android.media.audiopolicy.AudioMixingRule$Builder.allowPrivilegedPlaybackCapture(boolean)
android.media.audiopolicy.AudioMixingRule$Builder.build()
android.media.audiopolicy.AudioMixingRule$Builder.excludeMixRule(AudioAttributes, int)
android.media.audiopolicy.AudioPolicy$Builder.addMix(AudioMix)
android.media.audiopolicy.AudioPolicy$Builder.build()
android.media.audiopolicy.AudioPolicy.createAudioRecordSink(AudioMix): AudioRecord
android.media.AudioRecord.<init>(int, int, int, int, int)         (the (IIIII)V ctor)
android.media.AudioRecord.getMinBufferSize(int, int, int): int
android.media.AudioRecord.getState(): int
android.media.AudioRecord.read(byte[], int, int): int
android.media.AudioRecord.release()
android.media.AudioRecord.startRecording()
android.media.AudioRecord.stop()
```

[P:RECORD_AUDIO] [P:FOREGROUND_SERVICE_MICROPHONE] for the mic flavor
(transitively via `AudioRecord` open).

[P:CAPTURE_AUDIO_OUTPUT] [P:MODIFY_AUDIO_ROUTING] for the device-audio
flavor — these permissions gate `AudioMixingRule.Builder.allowPrivilegedPlaybackCapture(true)`
and `AudioManager.registerAudioPolicy` per the public Android source.
[R:BLOCKED-SIG] for a debug-keystore APK.

[P:CAPTURE_VOICE_COMMUNICATION_OUTPUT] [P:MODIFY_AUDIO_ROUTING] for the
call-audio flavor (same `AudioPolicy` machinery).
[R:BLOCKED-SIG].

---

## Section C — AOC nanoapp path

### C.1 `com.google.research.air.cosmo.lib.record.NanoAppClient`

Constructor:

```
public <init>(android.app.Application, java.util.concurrent.ScheduledExecutorService)
```

Methods (`^\.method` grep, non-synthetic):

```
public final getContextHubClientCallback$java_com_google_research_air_cosmo_lib_record_record(): android.hardware.location.ContextHubClientCallback
public final getCurrentSessionToken$java_com_google_research_air_cosmo_lib_record_record(): int
public final setCurrentSessionToken$java_com_google_research_air_cosmo_lib_record_record(int): void
public final getRecordListener(): RecordServiceListener
public final setRecordListener(RecordServiceListener): void
public final getSilenceInjectionFuture$java_com_google_research_air_cosmo_lib_record_record(): java.util.concurrent.ScheduledFuture
public final setSilenceInjectionFuture$java_com_google_research_air_cosmo_lib_record_record(ScheduledFuture): void
public final getTotalSilenceInjectedMs$java_com_google_research_air_cosmo_lib_record_record(): long
public final setTotalSilenceInjectedMs$java_com_google_research_air_cosmo_lib_record_record(long): void
public final initializeNanoApp(): void
public final startRecording(): boolean
public final declared-synchronized stopRecording(): boolean
private final injectSilence(int): void
private static final injectSilence$lambda$0$0(NanoAppClient, int): void
private final declared-synchronized resetSilenceInjection(): void
private final processNanoappMessage(byte[], int): void
private final sendMessageToNanoApp(int): boolean
```

### C.2 Constants observed in `NanoAppClient.smali`

A 64-bit constant (declared as `const-wide`):

```
const-wide v6, 0x476f6f676c001068L
```

Byte decode: `0x47 0x6f 0x6f 0x67 0x6c` = ASCII `Googl`; trailing
3 bytes `0x001068` = 4200. (The 64-bit-with-vendor-in-high-bytes
nanoapp ID convention is documented in public Android source —
external claim, not asserted from this APK.)

Other identifier-like fields in the class (not enumerated for value):

```
EMPTY_MESSAGE_PAYLOAD
SILENCE_BUFFER_SIZE
silenceBytes
totalSilenceInjectedMs
silenceInjectionFuture
currentSessionToken
contextHubClient
contextHubClientCallback
contextHubClientUtil
```

### C.3 Android system APIs invoked by `NanoAppClient`

```
android.hardware.location.ContextHubClient.sendMessageToNanoApp(NanoAppMessage)
android.hardware.location.NanoAppMessage.createMessageToNanoApp(long, int, byte[]): NanoAppMessage
com.google.research.air.cosmo.lib.util.ContextHubClientUtil$Companion.createOrNull(...): ContextHubClient
```

`ContextHubClientUtil` is a Cosmo helper; not analyzed in this doc beyond
the `createOrNull` invocation.

### C.4 Permission posture

[P:ACCESS_CONTEXT_HUB] is signature-protected per public Android source.
Cosmo's manifest declares it. A debug-keystore APK that declares the same
permission will install but the package manager grants `granted=false`.
The first call to `ContextHubManager.getContextHubs()` (or any Context Hub
binder transaction) raises `SecurityException`.

[R:BLOCKED-SIG] [R:BLOCKED-FW] (the firmware block is independently
documented by Google's Android Open Source Project: nanoapps loaded onto
the AOC must be signed by a key the AOC bootloader trusts; the public API
exposes `loadNanoApp` but the AOC verifies signatures before execution).

---

## Section D — `SODAAmbientAudioCaptor`

`com.google.research.air.cosmo.audiocapture.SODAAmbientAudioCaptor`

Constructor:

```
public <init>(CoroutineScope, SodaController, RecordServiceManager, Context,
              CosmoSettingStore, com.google.research.air.cosmo.glasses.CosmoGlassManager)
```

Methods (non-synthetic):

```
public final getCoroutineScope(): CoroutineScope
public flush(): void
public startCapture(): kotlinx.coroutines.flow.Flow
private final onGlassAudioData(byte[], int): void
private final startWork(kotlin.coroutines.Continuation): java.lang.Object
private final stopWork(): void
private inner SODAAmbientAudioCaptor$startWork$listener$1   (listener body)
```

External classes/methods invoked (`invoke-…` grep, deduped, audio-relevant
subset shown):

```
com.google.research.air.cosmo.audiocapture.RecordServiceManager.getRecordService(): RecordService
com.google.research.air.cosmo.lib.record.RecordService.startMicRecording(RecordServiceListener)
com.google.research.air.cosmo.lib.record.RecordService.startAocRecording(RecordServiceListener)
com.google.research.air.cosmo.lib.record.RecordService.stopMicAudioRecording()
com.google.research.air.cosmo.lib.record.RecordService.stopAocRecording()
com.google.research.air.cosmo.lib.record.RecordService.isMicRecording()
com.google.research.air.cosmo.lib.record.RecordService.isAocRecording()
com.google.research.air.cosmo.lib.soda.SodaController.addAudioBytes(byte[])
com.google.research.air.cosmo.lib.soda.SodaController.flush()
com.google.research.air.cosmo.lib.soda.SodaController.stopCapture()
com.google.research.air.cosmo.lib.soda.TranscriptionData.getAudioData()
com.google.research.air.cosmo.lib.soda.TranscriptionData.getTimestampMillis()
com.google.research.air.cosmo.lib.soda.TranscriptionData.getTranscription()
com.google.research.air.cosmo.lib.soda.TranscriptionData.hasContent()
com.google.research.air.cosmo.glasses.CosmoGlassManager.isGlassServiceBound()
com.google.research.air.cosmo.glasses.CosmoGlassManager.startGlassAudioCapture(Function2)
com.google.research.air.cosmo.glasses.CosmoGlassManager.stopGlassAudioCapture()
com.google.research.air.cosmo.setting.CosmoSetting.getUseNanoappAudioEnabled()
com.google.research.air.cosmo.setting.CosmoSettingStore.getCosmoSettingFlow()
com.google.research.air.cosmo.setting.CosmoSettingStore.isRawAudioGatekeeperEnabled()
com.google.research.air.cosmo.setting.CosmoSettingStore.isRealtimeProcessingEnabled()
com.google.research.air.cosmo.setting.CosmoSettingStore.isUseNanoappAudioEnabled()
```

[P:RECORD_AUDIO] (mic branch) [P:ACCESS_CONTEXT_HUB] (AOC branch).
[R:OK] for the mic branch; [R:BLOCKED-SIG][R:BLOCKED-FW] for AOC; the glass
branch's reachability is determined by `CosmoGlassManager` (§G).

---

## Section E — `SODABudsAudioCaptor` + `BudsAudioProvider` + SBC decode

### E.1 `com.google.research.air.cosmo.audiocapture.SODABudsAudioCaptor`

Constructor:

```
public <init>(Context, CoroutineScope, SodaController,
              com.google.research.air.cosmo.lib.buds.BudsAudioProvider,
              com.google.android.apps.search.assistant.surfaces.bisto.interactor
                .sbcdecoder.monodecoder.JniSbcDecoderImpl,
              CosmoSettingStore)
```

Methods (non-synthetic):

```
public final getCoroutineScope(): CoroutineScope
public flush(): void
public startCapture(): kotlinx.coroutines.flow.Flow
private final getPixelBudsDevice(): android.bluetooth.BluetoothDevice
private final isPixelBudsDevice(android.bluetooth.BluetoothDevice): boolean
private final listenForConnectionChanges(): void
private final onDeviceConnected(android.bluetooth.BluetoothDevice): void
private final onDeviceDisconnected(): void
private final startWork(): void
private final stopWork(): void
```

External invokes (audio-relevant subset):

```
com.google.research.air.cosmo.lib.buds.BudsAudioProvider.start(AudioDevice, Continuation): Object
com.google.research.air.cosmo.lib.buds.BudsAudioProvider.stop(Continuation): Object
com.google.research.air.cosmo.lib.buds.BudsAudioProvider.getState(): StateFlow
com.google.research.air.cosmo.lib.buds.BudsAudioProvider.getAudioData(): Flow
com.google.research.air.cosmo.lib.buds.BudsAudioProvider$Companion.getSPP_UUID(): java.util.UUID
com.google.research.air.cosmo.lib.buds.AudioChunk.getData(): byte[]
com.google.research.air.cosmo.lib.soda.SodaController.addAudioBytes(byte[])
com.google.research.air.cosmo.lib.soda.SodaController.flush()
com.google.research.air.cosmo.lib.soda.SodaController.stopCapture()
com.google.research.air.cosmo.setting.CosmoSetting.getBudsAudioEnabled()
com.google.research.air.cosmo.setting.CosmoSetting.getGlobalCosmoEnabled()
com.google.research.air.cosmo.setting.CosmoSettingStore.getCosmoSettingFlow()
```

### E.2 `com.google.research.air.cosmo.lib.buds.BudsAudioProvider` (interface)

```
public abstract start(AudioDevice, Continuation): Object
public abstract stop(Continuation): Object
public abstract getState(): kotlinx.coroutines.flow.StateFlow
public abstract getAudioData(): kotlinx.coroutines.flow.Flow
companion BudsAudioProvider$Companion.getSPP_UUID(): java.util.UUID
```

### E.3 `com.google.research.air.cosmo.lib.buds.BudsAudioProviderImpl`

Constructor:

```
public <init>(Context, CoroutineScope)
```

Methods (non-synthetic):

```
public start(AudioDevice, Continuation): Object
public stop(Continuation): Object
public getAudioData(): Flow
public getState(): StateFlow
public final shutdown(): void
private final createAudioDevice(BluetoothDevice): AudioDevice
private final streamAudio(CoroutineScope, BluetoothSocket, Continuation): Object
```

External invokes (Bluetooth-relevant subset):

```
android.bluetooth.BluetoothDevice.getAddress()
android.bluetooth.BluetoothDevice.getName()
android.bluetooth.BluetoothSocket.getInputStream()
android.bluetooth.BluetoothSocket.close()
java.io.InputStream.read([B): int
```

The constructor signature `createInsecureRfcommSocketToServiceRecord` is
not visible in the recovered method-invocation grep; only the resulting
`BluetoothSocket` accessors are. (The opening of the socket is in a body
not extracted in this analysis.)

### E.4 `…ProviderState` (sealed class members)

`smali` files in `com/google/research/air/cosmo/lib/buds/`:

```
ProviderState
ProviderState$Connecting
ProviderState$Error
ProviderState$Idle
ProviderState$Streaming
```

### E.5 `com.google.android.apps.search.assistant.surfaces.bisto.interactor
  .sbcdecoder.monodecoder.JniSbcDecoderImpl` (third-party library; bundled, not in `com.google.research.air.cosmo`)

Methods (non-synthetic):

```
constructor <init>(JniSbcDecoderImpl$Decoder)
private static native decodeFrame(byte[]): byte[]
private static native initialDecoder(int, int, int, int, int, int): void
private initializeDecoder(int, int, int, int, int): boolean
public initializeDecoder(com.google.android.bisto.DeviceInput$VoiceStart): boolean
public decodeSbcAudio(byte[]): byte[]
public finishDecodingIfCurrentlyRunning(): void
public isInDecoding(): boolean
private static calculateDecodedFrameSize(int, int): int
private static calculateFrameSize(int, int, int): int
```

`libsbcdecoder_jni.so` JNI exports (from `nm -D`):

```
Java_com_google_android_apps_search_assistant_surfaces_bisto_interactor
    _sbcdecoder_monodecoder_JniSbcDecoderImpl_decodeFrame
Java_com_google_android_apps_search_assistant_surfaces_bisto_interactor
    _sbcdecoder_monodecoder_JniSbcDecoderImpl_initialDecoder
```

`libsbcdecoder_jni.so` size: 19,088 bytes. ELF NEEDED (from
`native_libraries.md`): `libdl.so libc.so libm.so`.

[P:BLUETOOTH_CONNECT] (declared by Cosmo). [R:OK-COMPANION] for any
Bluetooth-paired device matching the buds detection logic.

---

## Section F — `SODADeviceAudioCaptor` and `SODAWatchAudioCaptor`

### F.1 `com.google.research.air.cosmo.audiocapture.SODADeviceAudioCaptor`

Constructor:

```
public <init>(android.app.Application, CoroutineScope, SodaController, SodaController, RecordServiceManager)
```

Methods (non-synthetic):

```
public final getCoroutineScope(): CoroutineScope
public flush(): void
public startCapture(): kotlinx.coroutines.flow.Flow
private final startWork(Continuation): Object
private final stopWork(): void
private inner SODADeviceAudioCaptor$AudioListener  (RecordServiceListener impl)
```

External invokes:

```
RecordServiceManager.getRecordService()
RecordService.startDeviceAudioRecording(RecordServiceListener)
RecordService.startCallAudioRecording(RecordServiceListener)
RecordService.stopDeviceAudioRecording()
RecordService.stopCallAudioRecording()
SodaController.addAudioBytes(byte[])
SodaController.flush()
SodaController.stopCapture()
TranscriptionData.{getTimestampMillis,getTranscription,hasContent}
```

[P:CAPTURE_AUDIO_OUTPUT] [P:MODIFY_AUDIO_ROUTING] [R:BLOCKED-SIG] for the
device branch. [P:CAPTURE_VOICE_COMMUNICATION_OUTPUT] [R:BLOCKED-SIG] for
the call branch.

### F.2 `com.google.research.air.cosmo.audiocapture.SODAWatchAudioCaptor`

Constructor:

```
public <init>(android.app.Application, CoroutineScope, SodaController)
```

Methods (non-synthetic):

```
public final getCoroutineScope(): CoroutineScope
public flush(): void
public startCapture(): kotlinx.coroutines.flow.Flow
public final onAudioBytesReceived(byte[]): void
private final startWork(): void
private final stopWork(): void
```

External invokes (subset):

```
SodaController.addAudioBytes(byte[])
SodaController.flush()
SodaController.stopCapture()
TranscriptionData.{getTimestampMillis,getTranscription,hasContent}
```

### F.3 `com.google.research.air.cosmo.lib.wearable.WearableAudioStreamService`

`.super Lcom/google/research/air/cosmo/lib/wearable/Hilt_WearableAudioStreamService;`
(verified by grep). The Hilt parent's `.super` is
`Landroid/app/Service;` (verified by grep on the Hilt smali).

Methods (non-synthetic):

```
public onCreate(): void
public onStartCommand(Intent, int, int): int
public onBind(Intent): IBinder
public onDestroy(): void
public final getBackgroundScope(): CoroutineScope
public final setBackgroundScope(CoroutineScope): void
public final getCancelled(): boolean
public final setCancelled(boolean): void
public final getInputStream(): java.io.InputStream
public final setInputStream(java.io.InputStream): void
public final getWatchAudioCaptor(): SODAWatchAudioCaptor
public final setWatchAudioCaptor(SODAWatchAudioCaptor): void
public final receiveAudioStream(com.google.android.gms.wearable.ChannelClient$Channel): void
private final createNotification(): android.app.Notification
private final createNotificationChannel(): void
```

[P:FOREGROUND_SERVICE] [P:FOREGROUND_SERVICE_DATA_SYNC] (declared by
Cosmo). [R:OK-COMPANION] (requires a Wear OS device that opens a
ChannelClient channel).

---

## Section G — `CosmoGlassManager` (audio side)

`com.google.research.air.cosmo.glasses.CosmoGlassManager`

Constructor:

```
public <init>(Context, CoroutineScope, CosmoSettingStore, CosmoStateManager,
              com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer)
```

Methods (non-synthetic, audio-related subset):

```
public final startGlassAudioCapture(kotlin.jvm.functions.Function2): void
public final stopGlassAudioCapture(): void
public final startGlassSettingsListener(): void
public isGlassConnected(): kotlinx.coroutines.flow.StateFlow
public isGlassServiceBound(): kotlinx.coroutines.flow.StateFlow
private final bindGlassCaptureService(): void
private final getApplication(): android.app.Application
private final startGlassActivityAndBindService(Continuation): Object
private final stopGlassActivity(): void
private inner CosmoGlassManager$glassCaptureServiceConnection$1: ServiceConnection
```

(Camera-side methods on the same class are listed in `image_stack_map.md`.)

[R:OK-COMPANION]. The `bindService` target in
`bindGlassCaptureService` is not enumerated in this analysis (the
intent-action constants are not listed via `^const-string` grep within
the methods we have); reachability is gated by whether the matching
external service is present on the device.

---

## Section H — TFLite assets in audio paths

Sizes from `cosmo_artifact_integrity/file_inventory.csv`.

| Asset | On-disk size | Declared size | Status |
|---|---|---|---|
| `assets/gatekeeper/audio_frontend.tflite` | 3,018,776 | 3,018,776 | complete |
| `assets/gatekeeper/audio_gatekeeper_edgetpu.tflite` | 48,156,352 | 48,156,352 | complete |
| `assets/gatekeeper/bert_v0_cpu.tflite` | 71,935,326 | 71,935,326 | complete |
| `assets/gatekeeper/bert_v0_p24.tflite` | 44,995,072 | 44,995,072 | complete |
| `assets/gatekeeper/bert_v0_p25.tflite` | 46,246,528 | 46,246,528 | complete |
| `assets/gatekeeper/bert_vocab.txt` | 231,508 | 231,508 | complete |
| `assets/voicelm/encoder_int4-graph-custom_op.tflite` | 137,043,136 | 137,043,136 | complete |
| `assets/voicelm/merged_depth_decoder_int4-graph-custom_op.tflite` | 48,470,336 | 48,470,336 | complete |
| `assets/voicelm/preprocessing.tflite` | 10,352 | 10,352 | complete |
| `assets/voicelm/soundstream_decoder_tpu.tflite` | 57,299,456 | 57,299,456 | complete |
| `assets/voicelm/soundstream_encoder_tpu.tflite` | 14,788,096 | 14,788,096 | complete |
| `assets/voicelm/soundstream_quantizer.tflite` | 4,721,524 | 4,721,524 | complete |
| `assets/voicelm/speaker_encodings_100ms.pb` | 77,360 | 77,360 | complete |
| `assets/voicelm/temporal_decoder_int4-graph-custom_op.tflite` | 47,703,764 | 112,552,896 | **truncated, 64,849,132 bytes missing** |
| `assets/soda/lp_cpu.zip` | 56,458,465 | 56,458,465 | complete |
| `assets/soda/lp_lga_tpu.zip` | 145,607,304 | 145,607,304 | complete |
| `assets/soda/lp_zuma_tpu.zip` | 146,456,436 | 146,456,436 | complete |

### H.1 Asset filenames referenced in smali (audio gatekeeper path)

`com.google.research.air.cosmo.gatekeeping.models.ConformerEncoderImpl`
contains the strings (from `^const-string` grep):

```
"gatekeeper/audio_frontend.tflite"
"gatekeeper/audio_gatekeeper_edgetpu.tflite"
```

The class also calls (from `invoke-…` grep):

```
org.tensorflow.lite.Interpreter.close()
org.tensorflow.lite.Interpreter.getInputTensorCount(): int
org.tensorflow.lite.Interpreter.runForMultipleInputsOutputs(Object[], Map): void
org.tensorflow.lite.Interpreter$Options.addDelegate(Delegate)
org.tensorflow.lite.support.common.FileUtil.loadMappedFile(Context, String): MappedByteBuffer
com.google.android.apps.hardware.protostar.components.delegate.DarwinnDelegate.close()
com.google.research.air.cosmo.gatekeeping.models.DarwinnDelegateFactory.create(...)
```

Methods on `ConformerEncoderImpl` (non-synthetic):

```
public <init>(Context, CoroutineScope, DarwinnDelegateFactory)
public awaitInitialization(Continuation): Object
public releaseResources(Continuation): Object
public runInference(byte[], Continuation): Object
private final initialize(Continuation): Object
private final loadModelFile(Context, String): MappedByteBuffer
private final preprocess(byte[]): void
private final runFrontendStage(org.tensorflow.lite.Interpreter): void
private final runBackendStage(org.tensorflow.lite.Interpreter): void
```

`libtensorflowlite_jni.so` exports 56 `Java_…` symbols; size 4,105,328
bytes. `libdarwinndelegatejni.so` exports two:

```
Java_com_google_android_apps_hardware_protostar_components_delegate_DarwinnDelegate_createDelegate
Java_com_google_android_apps_hardware_protostar_components_delegate_DarwinnDelegate_deleteDelegate
```

Size 1,747,216 bytes.

### H.2 BERT gatekeeper model paths in smali

`com.google.research.air.cosmo.gatekeeping.impl.BertClassifierGatekeepingModel`
contains (from `^const-string` grep):

```
"gatekeeper/bert_v0_cpu.tflite"
"gatekeeper/bert_v0_p24.tflite"
"gatekeeper/bert_v0_p25.tflite"
"gatekeeper/bert_vocab.txt"
"Pixel 9"
"Pixel 10"
```

Plus the source-tag strings used to build the BERT input:

```
"Ambient audio: "
"Buds audio: "
"Device audio: "
"Watch audio: "
"Notification: "
"Screen: "
```

…and the action-name strings observed in the same file (14 unique
literals; verified by deduped `^\s*const-string ` grep on this single
smali file):

```
"add_to_memory"
"add_to_list"
"add_timer"
"browsing_agent"
"call_agent"
"create_calendar_event"
"create_doc"
"deep_research"
"define_term"
"provide_event_context"
"provide_insight"
"provide_person_context"
"query_photos"
"web_search"
```

These are a subset of the 23 `ACTION_TYPE_*` enum values in
`assets/filtered_skill_schema.textproto` (see `llm_stack_map.md` §C.6) —
i.e. the BERT classifier was trained for 14 labels while the schema
defines a larger superset.

### H.3 SODA language pack selection

`com.google.research.air.cosmo.lib.soda.LanguagePackManager` (from
`^const-string` grep):

```
"soda/lp_cpu.zip"
"soda/lp_lga_tpu.zip"
"soda/lp_zuma_tpu.zip"
"Pixel 9"
"Pixel 10"
"eng"
"userdebug"
"SODA resources already extracted."
"SODA resources extraction completed."
"SODA resources not extracted, extracting now."
```

Method list:

```
public <init>(Context, CosmoSettingStore)
public final prepareSodaResources(): void
private final expectedLanguagePackVersion(): int
companion access$getLogger$cp(): GoogleLogger
```

### H.4 VoiceLM asset file path observed in `VoiceLMTTS.smali`

```
"voicelm/speaker_encodings_100ms.pb"
"voicelm"
```

The other `voicelm/*.tflite` filenames are not present as `^const-string`
matches in any smali file under `com/google/research/air/cosmo/`. They
appear as embedded path strings inside `libvogo_jni.so`'s string table
(see `audio out` section).

---

## Section I — Audio output

### I.1 `com.google.research.air.cosmo.lib.tts.TTS` (interface)

```
public abstract isPlaying(): boolean
public abstract speak(java.lang.String): void
public abstract stop(): void
```

### I.2 `com.google.research.air.cosmo.lib.tts.VoiceLMTTS`

Constructor:

```
public <init>(Context, CosmoSettingStore, CoroutineScope,
              com.google.research.air.cosmo.lib.wearable.WearableClient)
```

Methods (non-synthetic):

```
public isPlaying(): boolean
public speak(java.lang.String): void
public stop(): void
private final playAudio(int): void
private final startVogoVoicelmTask(): void
```

External invokes (audio-relevant subset, from `^const-string` annotations
of method names — these are the names appearing in the smali's
`Companion`/`Lambda` debug strings):

```
"copyAssetSubFolderSafe(...)"
"initializeCapability(...)"
"createBuffer(...)"
"processTask(...)"
"updateTask(...)"
"cancelTask(...)"
"audioRenderer"
"voicelm/speaker_encodings_100ms.pb"
"voicelm"
"j$/nio/file/Path"
```

…and one log line:

```
"VOGO Library cancelTask failed: %s"
"VOGO Library cancelTask succeeded."
"VOGO Library initializeCapability failed: %s"
"VOGO Library initializeCapability succeeded."
"VOGO Library processTask failed: %s"
"VOGO Library processTask succeeded."
"VOGO Library updateTask failed: %s"
"VOGO Library updateTask succeeded."
```

### I.3 `research.xeno.audio.apis.android.VogoLibrary` (third-party library; bundled, not in `com.google.research.air.cosmo`)

Constructor:

```
public <init>()
```

Methods (non-synthetic):

```
public initializeCapability(research.xeno.audio.apis.Protos$VogoCapabilityOptions):
        research.xeno.audio.apis.Protos$Status
public processTask(Protos$VogoTaskInput, VogoLibrary$TaskCallback): Protos$StatusOrHandle
public updateTask(long, Protos$VogoTaskInput): Protos$Status
public cancelTask(long): Protos$Status
public cancelTask(long, boolean): Protos$Status
public createBuffer(long, long): VogoLibrary$VogoBuffer
public releaseBuffer(long): void

private parseStatus(byte[]): Protos$Status
private parseStatusOrHandle(byte[]): Protos$StatusOrHandle

private native nativeInitializeCapability(byte[]): byte[]
private native nativeProcessTask(byte[], VogoLibrary$NativeCallback): byte[]
private native nativeUpdateTask(long, byte[]): byte[]
private native nativeCancelTask(long, boolean): byte[]
private native nativeUseBuffer(java.nio.ByteBuffer, long): byte[]
private native nativeReleaseBuffer(long): byte[]
```

Inner types (from `.smali` filenames):

```
VogoLibrary$NativeCallback
VogoLibrary$TaskCallback
VogoLibrary$VogoBuffer
```

`libvogo_jni.so` size: 95,526,472 bytes. ELF NEEDED (from
`native_libraries.md`): `libdl.so libz.so liblog.so libEGL.so libGLESv2.so
libandroid.so libGLESv3.so libm.so libc.so`. Asset filenames embedded as
strings inside `libvogo_jni.so` (from `strings`):

```
encoder_tpu.tflite
depth_decoder_tpu.tflite
temporal_decoder_tpu.tflite
soundstream_decoder_runner_calculator.cc
soundstream_encoder_runner_calculator.cc
soundstream_inference_calculator.cc
soundstream_decoder_output_calculator.cc
soundstream_encoder_input_calculator.cc
```

The `libvogo_jni.so` string table also contains a long list of
`*_spline.pb` filenames (sample, verified via `strings`):

```
abs_spline.pb
atan_recip_spline.pb
atan_spline.pb
cbrt_spline.pb
classify_negative_zero_positive_spline.pb
elu_spline.pb
erf_spline.pb
exp_spline.pb
gelu_approximated_for_positive_spline.pb
gelu_approximated_spline.pb
gelu_spline.pb
hard_swish_spline.pb
is_negative_spline.pb
is_not_negative_spline.pb
is_not_positive_spline.pb
is_positive_spline.pb
is_zero_spline.pb
log2_exponent_spline.pb
log2_significand_spline.pb
…
```

…and Rust HuggingFace `tokenizers` crate symbols (verified):

```
tokenizers_new_from_str
tokenizers_free
tokenizers_get_vocab_size
tokenizers_id_to_token
tokenizers_token_to_id
```

### I.4 `com.google.research.air.cosmo.lib.tts.AudioRenderer`

```
public <init>(int, int, int)
public final isPlaying(): boolean
public final release(): void
public final render(short[]): void
public final reset(): void
```

`com.google.research.air.cosmo.lib.tts.AudioRenderer.smali` contains the
`const-string` `"audio track is initialized"` and `"shorts"`.
`android.media.AudioTrack` is invoked directly (verified by
`invoke-…` grep on this smali):

```
android.media.AudioFormat$Builder.<init>()
android.media.AudioFormat$Builder.setEncoding(int)
android.media.AudioFormat$Builder.setSampleRate(int)
android.media.AudioFormat$Builder.setChannelMask(int)
android.media.AudioFormat$Builder.build()
android.media.AudioFormat.getSampleRate()
android.media.AudioFormat.getChannelMask()
android.media.AudioFormat.getEncoding()
android.media.AudioTrack.getMinBufferSize(int, int, int): int
android.media.AudioTrack$Builder.<init>()
android.media.AudioAttributes$Builder.<init>()
android.media.AudioAttributes$Builder.setContentType(int)
android.media.AudioAttributes$Builder.setUsage(int)
android.media.AudioAttributes$Builder.build()
android.media.AudioTrack$Builder.setAudioAttributes(AudioAttributes)
android.media.AudioTrack$Builder.setAudioFormat(AudioFormat)
android.media.AudioTrack$Builder.setTransferMode(int)
android.media.AudioTrack$Builder.setBufferSizeInBytes(int)
android.media.AudioTrack$Builder.build()
android.media.AudioTrack.getPlaybackParams()
android.media.AudioTrack.setPlaybackParams(PlaybackParams)
android.media.AudioTrack.getPlaybackHeadPosition()
android.media.AudioTrack.getPlayState()
android.media.AudioTrack.play()
android.media.AudioTrack.pause()
android.media.AudioTrack.write(short[], int, int): int
android.media.AudioTrack.flush()
android.media.AudioTrack.release()
```

### I.5 `com.google.research.air.cosmo.lib.tts.AudioResampler`

```
public <init>()
public final resample(java.io.InputStream): java.io.InputStream
```

### I.6 `com.google.research.air.cosmo.lib.tts.LocalTTS`

Constructor:

```
public <init>(Context, CosmoSettingStore, CoroutineScope, WearableClient, java.util.Locale)
```

Methods (non-synthetic):

```
public isPlaying(): boolean
public onInit(int): void
public speak(java.lang.String): void
public stop(): void
private final cleanupWatchTempFile(java.lang.String): void
private final sendAudioToWatch(java.lang.String, Continuation): Object
private final speakLocal(java.lang.String, java.lang.String): void
private final speakToWatch(java.lang.String, java.lang.String): void
```

`onInit(int)` is the standard `android.speech.tts.TextToSpeech.OnInitListener`
callback shape.

---

## Section J — Reachability summary table for a debug-keystore APK

| Path | Reach | Reason from artifact |
|---|---|---|
| Mic via `RecordService.startMicRecording` | [R:OK] | uses `AudioRecord(IIIII)V`, gated by `RECORD_AUDIO` (declared) |
| Device audio via `RecordService.startDeviceAudioRecording` | [R:BLOCKED-SIG] | calls `AudioMixingRule$Builder.allowPrivilegedPlaybackCapture(true)` and `AudioManager.registerAudioPolicy`; both gated by `CAPTURE_AUDIO_OUTPUT` + `MODIFY_AUDIO_ROUTING` (signature) |
| Call audio via `RecordService.startCallAudioRecording` | [R:BLOCKED-SIG] | same `AudioPolicy` machinery; gated by `CAPTURE_VOICE_COMMUNICATION_OUTPUT` (signature) |
| AOC via `RecordService.startAocRecording` → `NanoAppClient` | [R:BLOCKED-SIG][R:BLOCKED-FW] | `ContextHubClient.sendMessageToNanoApp` gated by `ACCESS_CONTEXT_HUB` (signature). Public Android source documents AOC firmware nanoapp signature verification at load time |
| Buds via `BudsAudioProviderImpl` + `JniSbcDecoderImpl` | [R:OK-COMPANION] | uses `BluetoothSocket` operations; gated by `BLUETOOTH_CONNECT` (declared, runtime grant) |
| Watch via `WearableAudioStreamService` | [R:OK-COMPANION] | uses `ChannelClient.Channel`; companion device required |
| Glass audio via `CosmoGlassManager.startGlassAudioCapture` | reachability depends on a matching `ServiceConnection` target on device | `bindService` to a target whose intent action is not listed in this analysis |

---

## Section K — Provenance

- `cosmo_baksmali/classes*/com/google/research/air/cosmo/` — smali tree.
- `cosmo_recovered_native_libs/lib/arm64-v8a/` — `nm -D` outputs in this doc.
- `cosmo_recovered_large_assets/` — file sizes corroborated by
  `cosmo_artifact_integrity/file_inventory.csv`.
- `aapt2 dump permissions com.google.research.air.cosmo v1.1_antisplit_repaired_stubmodel_unsigned.apk`.
- `cosmo_baksmali_imports/native_libraries.md` — ELF NEEDED listings.

This document is static analysis only.
