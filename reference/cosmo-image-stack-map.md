# Cosmo Image Stack — Static Analysis Map

Static-analysis map of every image-input surface in the recovered Cosmo APK
(`com.google.research.air.cosmo`, antisplit v1.1).

Same artifact-only discipline as `audio_stack_map.md` and
`text_stack_map.md`. Every claim is from a smali grep, a const-string, an
ELF symbol, an `aapt2` line, or extracted asset bytes. No behavioral
inference.

Permission tags shared with the audio and text docs:

| Tag | Permission | Notes |
|---|---|---|
| `[P:CAMERA]` | `android.permission.CAMERA` | dangerous, runtime |
| `[P:FGS_CAMERA]` | `android.permission.FOREGROUND_SERVICE_CAMERA` | normal; required for foreground services that use the camera |
| `[P:GLASS-BIND]` | `bindService` to `com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service.ProjectedGlassCaptureService` (vendor service, no public Android permission) | reachability depends on the service being installed and the device being a "Projected" Glass host |

Both `CAMERA` and `FOREGROUND_SERVICE_CAMERA` are declared by Cosmo (per
`aapt2 dump permissions` output reproduced in `audio_stack_map.md` §
permissions).

---

## Section A — Foreground camera path (CameraX)

### A.1 `com.google.research.air.cosmo.voiceassistant.camera.CameraViewComposable`

Constructor:

```
public <init>(java.util.concurrent.Executor,
              com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer)
```

Public methods (`^\.method` grep, non-synthetic):

```
public final CameraView-3IgeMak(androidx.compose.ui.Modifier,
                                 long /* color or duration */,
                                 kotlin.jvm.functions.Function1,
                                 androidx.compose.runtime.Composer,
                                 int, int): void
public final CameraPreview(androidx.compose.ui.Modifier,
                            int,
                            androidx.compose.runtime.Composer,
                            int, int): void
public final getCameraProvider(android.content.Context, Continuation): java.lang.Object
private final CameraViewInternal-iJQMabo(androidx.compose.ui.Modifier,
                                          long,
                                          androidx.compose.runtime.Composer,
                                          int, int): void
```

(The `-3IgeMak` / `-iJQMabo` suffixes are Kotlin's mangling for inline
parameters with value classes; not a typo.)

const-strings observed:

```
"android.permission.CAMERA"
"Camera permission is required, please grant camera permission."
"CameraPreview"
"CameraView"
"CameraView-3IgeMak"
"Cameraswitch"
"CameraViewComposable.kt"
```

### A.2 CameraX system APIs invoked by `CameraViewComposable*.smali`

From `invoke-virtual` / `invoke-static` grep, deduped, camera-relevant:

```
androidx.camera.core.CameraSelector$Builder.build()
androidx.camera.core.CameraSelector$Builder.requireLensFacing(int)
androidx.camera.core.ImageAnalysis.setAnalyzer(Executor, ImageAnalysis$Analyzer)
androidx.camera.core.ImageAnalysis$Builder.build()
androidx.camera.core.ImageAnalysis$Builder.setBackpressureStrategy(int)
androidx.camera.core.Preview.setSurfaceProvider(Preview$SurfaceProvider)
androidx.camera.core.Preview$Builder.build()
androidx.camera.lifecycle.ProcessCameraProvider.bindToLifecycle(LifecycleOwner, CameraSelector, UseCase[])
androidx.camera.lifecycle.ProcessCameraProvider.unbindAll()
androidx.camera.lifecycle.ProcessCameraProvider$Companion.getInstance(Context)
androidx.camera.view.PreviewView.getSurfaceProvider()
android.graphics.Bitmap.getHeight()
android.graphics.Bitmap.getWidth()
```

[P:CAMERA]. [R:OK-CONSENT] (the user must grant `CAMERA` via the runtime
permission dialog; the const-string `Camera permission is required, please
grant camera permission.` is the message shown when not granted).

### A.3 `com.google.research.air.cosmo.voiceassistant.camera.CameraBitmapAnalyzer`

Constructor:

```
public <init>(kotlin.jvm.functions.Function1)
```

Methods:

```
public analyze(androidx.camera.core.ImageProxy): void
```

`analyze` body (read directly from smali):

```
public analyze(ImageProxy image)
    System.currentTimeMillis()                            ← v0
    iget-wide v2, this, lastFrameSentTimestamp:J
    sub-long v2, v0, v2                                   ← elapsed = now - lastFrameSentTimestamp
    const-wide/16 v4, 0x2710                              ← 10000 ms (= 10 s)
    cmp-long v2, v2, v4
    if-ltz v2, :cond_45                                   ← if elapsed < 10000 jump (skip frame)
    [continued: convert ImageProxy→Bitmap, invoke listener, set lastFrameSentTimestamp]
```

The class declares an instance field:

```
private lastFrameSentTimestamp: long
```

…and its const-strings include `"image"`, `"bitmap"`, `"listener"`,
`"Landroidx/camera/core/ImageAnalysis$Analyzer;"`,
`"Landroidx/camera/core/ImageProxy;"`,
`"Landroid/graphics/Bitmap;"`, `"Lkotlin/Function1;"`.

The `0x2710` constant gives the frame throttle: bitmaps are forwarded to
the `Function1<Bitmap, Unit>` listener at most once per 10,000 ms. (This
is the only behavioral claim in this section read directly from a method
body rather than from invocations or method signatures.)

`androidx.camera.core.ImageProxy.toBitmap()` is invoked. `ImageProxy.close()`
is also invoked, which is the required CameraX cleanup per the public
CameraX docs.

[P:CAMERA] (transitively, via the analyzer chain).

### A.4 CameraX support libraries (recovered .so files)

`libimage_processing_util_jni.so` — JNI exports (from `nm -D`):

```
Java_androidx_camera_core_ImageProcessingUtil_nativeConvertAndroid420ToABGR
Java_androidx_camera_core_ImageProcessingUtil_nativeConvertAndroid420ToBitmap
Java_androidx_camera_core_ImageProcessingUtil_nativeCopyBetweenByteBufferAndBitmap
Java_androidx_camera_core_ImageProcessingUtil_nativeGetYUVImageVUOff
Java_androidx_camera_core_ImageProcessingUtil_nativeNewDirectByteBuffer
Java_androidx_camera_core_ImageProcessingUtil_nativeRotateYUV
Java_androidx_camera_core_ImageProcessingUtil_nativeShiftPixel
Java_androidx_camera_core_ImageProcessingUtil_nativeWriteJpegToSurface
```

Size: 32,528 bytes. ELF NEEDED:
`liblog.so libandroid.so libjnigraphics.so libm.so libdl.so libc.so`.

`libsurface_util_jni.so` — JNI exports:

```
Java_androidx_camera_core_impl_utils_SurfaceUtil_nativeGetSurfaceInfo
```

Size: 4,896 bytes. ELF NEEDED:
`libandroid.so libm.so libdl.so libc.so`.

`libfilterframework_jni.so` — JNI exports:

```
Java_androidx_media_filterfw_ColorSpace_nativeCropRgbaImage
Java_androidx_media_filterfw_ColorSpace_nativeRgba8888ToGray8888
Java_androidx_media_filterfw_ColorSpace_nativeRgba8888ToHsva8888
Java_androidx_media_filterfw_ColorSpace_nativeRgba8888ToYcbcra8888
Java_androidx_media_filterfw_ColorSpace_nativeYuv420pToRgba8888
Java_androidx_media_filterfw_PixelUtils_nativeCopyPixels
Java_androidx_media_filterfw_imageutils_FastBoxBlur_blur
Java_androidx_media_filterfw_imageutils_GrayValuesExtractor_toGrayValues
Java_androidx_media_filterfw_imageutils_PerceptualSharpnessCalculator_computePerceptualSharpness
Java_androidx_media_filterfw_imageutils_RegionStatsCalculator_regionscore
```

Size: 69,728 bytes. ELF NEEDED: `libdl.so liblog.so libc.so libm.so`.

`libandroidx.graphics.path.so` — JNI exports: (none reported by
`nm -D ... | grep ' T Java_'` in this analysis).

Size: 9,952 bytes. ELF NEEDED: `libm.so libdl.so libc.so`.

Cosmo references the Java side of `ImageProcessingUtil` via
`ImageProxy.toBitmap()`, which internally uses
`libimage_processing_util_jni.so` per the public AndroidX source.

---

## Section B — Glass camera path

### B.1 `com.google.research.air.cosmo.glasses.CosmoGlassManager` (camera side)

(Audio-side methods covered in `audio_stack_map.md` §G; this section is
the camera-side surface only.)

Camera-relevant methods (from `^\.method` grep on
`CosmoGlassManager.smali`):

```
private final setupGlassCameraCapture(): void
private final startGlassCameraCapture(): void
private final stopGlassCameraCapture(): void
public final clearGlassCameraBuffer(): void
private static final setupGlassCameraCapture$lambda$0(CosmoGlassManager,
                                                       androidx.camera.core.ImageProxy):
                                                       kotlin.Unit
```

External invokes referenced in the camera path (from `invoke-…` grep,
deduped, camera-relevant subset):

```
com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service
        .ProjectedGlassCaptureService.setupImageCapture(...)
com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service
        .ProjectedGlassCaptureService.startImageCapture(...)
com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service
        .ProjectedGlassCaptureService.stopImageCapture(...)
com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service
        .ProjectedGlassCaptureService.startAudioCapture(...)        ← audio side, listed for completeness
com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service
        .ProjectedGlassCaptureService.stopAudioCapture(...)
androidx.xr.projected.ProjectedContext.createProjectedActivityOptions(Context, …)
androidx.xr.projected.ProjectedContext.isProjectedDeviceConnected(Context)
android.app.ActivityOptions.toBundle()
android.content.Context.startActivity(Intent, Bundle)
android.content.Intent.addFlags(int)
android.content.Intent.putExtra(String, …)
androidx.camera.core.ImageProxy.close()
androidx.camera.core.ImageProxy.toBitmap()
com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer.add(Object, long)
com.google.research.air.cosmo.ephemeralbuffer.InMemoryEphemeralBuffer.clear()
com.google.research.air.cosmo.setting.CosmoSetting.getGlassesCameraEnabled()
com.google.research.air.cosmo.setting.CosmoSettingStore.isGlassesCameraEnabled()
```

const-strings observed in `CosmoGlassManager.smali` (camera-relevant):

```
"Glass camera frame captured"
"Glass service binding started."
"Glass service bound."
"Already bound or binding in progress."
"CosmoGlassActivity finish intent sent."
"CosmoGlassActivity started."
"Failed to launch CosmoGlassActivity."
"Failed to send finish intent to CosmoGlassActivity."
"finish_activity"
"audioConfig"
"buffer"
"bindingJob"
"_isGlassServiceBound"
"glassCaptureService"
"clearGlassCameraBuffer"
"getApplication"
"setupGlassCameraCapture"
"bindGlassCaptureService"
```

The bound service's full class name (from invokes):

```
com.google.android.glasses.shared.ai.experimental.apis.projectedcapture.service.ProjectedGlassCaptureService
```

This is the external service Cosmo depends on. It is not part of the
recovered APK; it is a separate vendor-installed system service on
"Projected" Glass host devices (see invokes of
`androidx.xr.projected.ProjectedContext.isProjectedDeviceConnected`).

### B.2 `com.google.research.air.cosmo.glasses.CosmoGlassActivity`

Methods (`^\.method` grep, non-synthetic):

```
public <init>()
public onCreate(Bundle): void                              (protected)
public onDestroy(): void                                   (protected)
public onNewIntent(Intent): void                           (protected)
private final arePermissionsGranted(): boolean
private final getViewModel(): CosmoGlassViewModel
private final handleFinishIntent(Intent): void
private final onPermissionsGranted(): void
private final requestPermissions(): void
```

External invokes in the activity:

```
android.content.Intent.getBooleanExtra(String, boolean)
androidx.activity.compose.ComponentActivityKt.setContent(ComponentActivity, kotlin.jvm.functions.Function2)
androidx.activity.result.ActivityResultLauncher.launch(Object)
androidx.core.content.ContextCompat.checkSelfPermission(Context, String)
CosmoGlassActivity.bindService(Intent, ServiceConnection, int)        // inherited from android.content.Context via Activity
CosmoGlassActivity.startForegroundService(Intent)
CosmoGlassActivity.stopService(Intent)
CosmoGlassActivity.unbindService(ServiceConnection)
CosmoGlassActivity.registerForActivityResult(ActivityResultContract, ActivityResultCallback)
CosmoGlassActivity.requestPermissions(...)
CosmoGlassActivity.finish()
CosmoGlassActivity.isFinishing()
CosmoGlassActivity.getIntent()
```

const-strings observed in `CosmoGlassActivity.smali`:

```
"android.permission.CAMERA"
"android.permission.POST_NOTIFICATIONS"
"android.permission.RECORD_AUDIO"
"Lcom/google/android/glasses/sdk/permissions/standardactivity/GlassesPermissionsRequestData;"
"Need audio & camera recording permission for glass."
"Requesting permissions for glass."
"Required permissions not granted."
```

Class hierarchy (verified by grep):

```
CosmoGlassActivity.smali
    .super Lcom/google/research/air/cosmo/glasses/Hilt_CosmoGlassActivity;

Hilt_CosmoGlassActivity.smali
    .super Landroidx/activity/ComponentActivity;
```

The activity holds a static field
`requiredPermissions: java.util.List` populated with the three
`android.permission.…` const-strings above (verified via `sget-object`
references in `arePermissionsGranted` and `requestPermissions`).
`arePermissionsGranted()` (body read directly from smali) iterates that
list and calls
`androidx.core.content.ContextCompat.checkSelfPermission(Context, String)`
on each, returning `true` only if every entry's result is
`PERMISSION_GRANTED` (`0`). The three permissions checked are therefore
`CAMERA`, `RECORD_AUDIO`, `POST_NOTIFICATIONS`.

[P:CAMERA] [P:RECORD_AUDIO] [P:POST_NOTIFICATIONS] [P:GLASS-BIND].
[R:OK-COMPANION] (`androidx.xr.projected.ProjectedContext.isProjectedDeviceConnected`
must return `true`, which on a non-Projected device returns `false` and
the activity short-circuits).

### B.3 Glass camera frame buffer

`CosmoGlassManager` invokes
`InMemoryEphemeralBuffer.add(java.lang.Object, long)` from inside the glass
camera capture path (`setupGlassCameraCapture$lambda$0`). The buffer
itself is the same `InMemoryEphemeralBuffer` defined in
`text_stack_map.md` §D.2; the `add` signature takes
`Ljava/lang/Object;` after generic-type erasure. In the same lambda body
the smali grep shows `androidx.camera.core.ImageProxy.toBitmap()` is
invoked, and no other producer is invoked between that call and
`InMemoryEphemeralBuffer.add` — making `android.graphics.Bitmap` the only
candidate value passed in.

The const-string `"Glass camera frame captured"` is logged in this lambda.

---

## Section C — Ephemeral buffer qualifiers (image side)

`com.google.research.air.cosmo.ephemeralbuffer.DeviceCameraEphemeralBuffer`
and `…GlassesCameraEphemeralBuffer` are **not classes** — they are
runtime-retained `@Qualifier` annotations:

```
.class public interface abstract annotation Lcom/google/research/air/cosmo/ephemeralbuffer/DeviceCameraEphemeralBuffer;
.super Ljava/lang/Object;
.implements Ljava/lang/annotation/Annotation;
.annotation runtime Ljavax/inject/Qualifier; …
.annotation runtime Ljava/lang/annotation/Retention;
    value = .enum Ljava/lang/annotation/RetentionPolicy;->RUNTIME …
```

(Same shape for `GlassesCameraEphemeralBuffer`.)

These exist purely as Hilt/javax-inject disambiguators for the
`InMemoryEphemeralBuffer` injections (`@DeviceCameraEphemeralBuffer
@Inject InMemoryEphemeralBuffer`).

The actual buffers are `InMemoryEphemeralBuffer<Bitmap>` instances
constructed by `EphemeralBufferModule.Companion`'s synthetic-lambda
factories (sibling smali files
`EphemeralBufferModule$Companion$$ExternalSyntheticLambda{0..11}.smali`
exist; their bodies are not enumerated in this analysis).

---

## Section D — Bound `ProjectedGlassCaptureService` interface (external)

The recovered APK invokes the following methods on the external service
class `com.google.android.glasses.shared.ai.experimental.apis.projectedcapture
.service.ProjectedGlassCaptureService`:

```
setupImageCapture(...)
startImageCapture(...)
stopImageCapture(...)
startAudioCapture(...)            ← from audio doc
stopAudioCapture(...)
```

The exact signatures are not visible in Cosmo's smali (`invoke-…` records
only the symbolic name + return type after `;->`). The full method
shapes can only be recovered from a separate APK that ships this service
class.

Because the service class is not part of `com.google.research.air.cosmo`,
it is opaque to this analysis. What is artifact-backed is only that
Cosmo binds to it and calls these five method names on it.

---

## Section E — TFLite assets in image paths

The recovered APK contains no asset whose filename matches `image*` or
`vision*` or `camera*` under `assets/` (verified in
`cosmo_artifact_integrity/file_inventory.csv`). All `.tflite` files
live under `assets/gatekeeper/`, `assets/voicelm/`, or `assets/gecko/`.

`assets/gecko/` contains (verified by listing
`cosmo_recovered_large_assets/`):

```
assets/gecko/gecko_out256_tpu_v0.tflite
assets/gecko/i18n_sentencepiece.model
```

These two are not referenced from any smali file under
`com/google/research/air/cosmo/` (per the grep in
`audio_stack_map.md` §H — `gecko/i18n_sentencepiece` and
`gecko_out256_tpu_v0` do not appear as `^const-string` matches). The
audio-gatekeeper / VoiceLM models are documented in
`audio_stack_map.md` §H; the `voicelm/*.tflite` filenames appear only
inside `libvogo_jni.so`'s string table (encoder / depth_decoder /
temporal_decoder / soundstream_*) — see `audio_stack_map.md` §I.3.

No `.tflite` filename containing `image`, `vision`, `camera`, `face`,
`ocr`, or `object` is present in `assets/`.

---

## Section F — Reachability summary

| Path | Reach | Reason from artifact |
|---|---|---|
| Foreground camera (CameraViewComposable + CameraBitmapAnalyzer) | [R:OK-CONSENT] | uses `CAMERA` runtime permission + `ProcessCameraProvider.bindToLifecycle`; declared in manifest |
| Glass camera via `ProjectedGlassCaptureService` | [R:OK-COMPANION] | requires `androidx.xr.projected.ProjectedContext.isProjectedDeviceConnected` to return true and the matching service to be installed on the Glass host |
| Periodic screen capture (image side) | [R:NONE in this APK] | no smali references to `ImageReader`, `MediaProjection`, or `ScreenCaptor`-image side; `PeriodicScreenCaptor` produces `Observation` (text) only — see `text_stack_map.md` §B |

`PeriodicScreenCaptor` is documented in `text_stack_map.md`. Its emitted
type (per the metadata-block enumeration) is
`com.google.protos.intelligence_dbw_modeling_proto.Observation`, not a
bitmap. There is no separate "screen-image" path in the recovered APK.

---

## Section G — Provenance

- `cosmo_baksmali/classes*/com/google/research/air/cosmo/` directories:
  `voiceassistant/camera`, `glasses`, `ephemeralbuffer`.
- `nm -D` outputs for `libimage_processing_util_jni.so`,
  `libsurface_util_jni.so`, `libfilterframework_jni.so`,
  `libandroidx.graphics.path.so` from
  `cosmo_recovered_native_libs/lib/arm64-v8a/`.
- `cosmo_baksmali_imports/native_libraries.md` for ELF NEEDED listings.
- `cosmo_artifact_integrity/file_inventory.csv` for asset path inventory.
- `aapt2 dump permissions com.google.research.air.cosmo v1.1_antisplit_repaired_stubmodel_unsigned.apk`.

This document is static analysis only.
