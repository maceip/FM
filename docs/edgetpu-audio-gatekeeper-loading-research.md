# Recovered EdgeTPU Audio Gatekeeper Loading Research

Date: 2026-05-18

## Finding

`audio_gatekeeper_edgetpu.tflite` is an already-compiled Pixel/DarwiNN EdgeTPU
artifact, not a normal LiteRT/TFLite graph that public NNAPI or LiteRT NPU APIs
can recompile from source. On retail Pixel 10 Pro Fold, the recovered Cosmo
loading path reaches the Pixel EdgeTPU stack but is rejected by platform
authorization before the delegate becomes usable.

The practical result: the artifact is real and structurally complete, but the
retail-device blocker is authorization/tooling access, not missing bytes.

## Local Model Facts

Verified with TensorFlow Lite Python metadata inspection:

```text
audio_gatekeeper_edgetpu.tflite
size:   48,156,352 bytes
sha256: a1388e523b7786800e0fdd8da4c2a7e4400aeb6781824ea234064e03844206f2

inputs:
  serving_default_mel_spectrogram:0  FLOAT32 [1, 3000, 128]
  serving_default_padding_mask:0     BOOL    [1, 3000]

output:
  StatefulPartitionedCall:0          FLOAT32 [1, 13]

ops:
  edgetpu-custom-op-2 x1
```

For comparison, the current replacement `inherent.tflite` is a standard-op
TFLite model with one `FLOAT32 [1, 3000, 128]` input, one `FLOAT32 [1, 13]`
output, and 724 standard ops.

## Recovered Cosmo Runtime Path

Recovered smali shows the original app used:

```text
classes38/com/google/research/air/cosmo/gatekeeping/models/ConformerEncoderImpl.smali
classes45/com/google/android/apps/hardware/protostar/components/delegate/DarwinnDelegate.smali
```

Important constants and calls:

```text
FRONTEND_MODEL_ASSET_PATH = gatekeeper/audio_frontend.tflite
BACKEND_MODEL_ASSET_PATH  = gatekeeper/audio_gatekeeper_edgetpu.tflite

DarwinnDelegate.Options.builder()
  .setUseTachyonBackend(flag)
  .build()

DarwinnDelegate.createDelegate(inferencePowerState, inferenceMemoryPowerState, useTachyonBackend)
TensorFlow Lite Interpreter(backendModel, Interpreter.Options().addDelegate(darwinnDelegate))
```

Our shim intentionally preserves that JNI package/class signature:

```text
app/src/main/kotlin/com/google/android/apps/hardware/protostar/components/delegate/DarwinnDelegate.kt
app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/AudioGatekeepingModel.kt
```

## Pixel 10 Evidence

Device:

```text
model: Pixel 10 Pro Fold
device: rango
Android: 16 / SDK 36
build: google/rango/rango:16/BD3A.251105.010.E1/14337626:user/release-keys
SoC: Google Tensor G5
```

Publicly visible Pixel services:

```text
android.hardware.neuralnetworks.IDevice/google-edgetpu
com.google.edgetpu.IEdgeTpuAppService/default
```

VINTF/init confirms the two relevant service surfaces:

```text
/system_ext/etc/vintf/manifest/vendor.google.edgetpu_app_service@1.0-service.xml
  com.google.edgetpu IEdgeTpuAppService/default

/vendor/etc/vintf/manifest/android.hardware.neuralnetworks@service-darwinn-aidl.xml
  android.hardware.neuralnetworks IDevice/google-edgetpu
```

The EdgeTPU client library exports app-service calls for `getEdgeTpuFd`,
`compileTfLiteSubgraph`, `getAuthorizedOperators`, `tryReadCompileCache`, and
`userIsAuthorized`:

```text
libedgetpu_client.google.so
  EdgeTpuServiceAppConnector::GetEdgeTpuFd
  EdgeTpuServiceAppConnector::CompileTfLiteSubgraph
  EdgeTpuServiceAppConnector::getAuthorizedOperators
  EdgeTpuServiceAppConnector::TryReadCompileCache
  EdgeTpuServiceAppConnector::UserIsAuthorized
```

Authorization probe:

```text
com.google.research.air.cosmo uid=10322 -> userIsAuthorized = 0
com.google.android.as          uid=10181 -> userIsAuthorized = 1
com.google.android.aicore      uid=10168 -> userIsAuthorized = 1
com.google.android.apps.tachyon uid=10237 -> userIsAuthorized = 1
```

Direct recovered-path run on Pixel:

```text
artifacts/audio-run/20260518-040219/logcat.txt
  Darwinn delegate created handle=0 tachyon=false
  Internal error: Found invalid handle
```

The result is consistent with an authorization failure before TFLite can prepare
the delegated backend interpreter.

## Public API Loading Attempts

### 1. TFLite + Public NNAPI

Added androidTest:

```text
GatekeepingRuntimeSmokeTest#recoveredEdgeTpuAudioModelDoesNotLoadThroughPublicNnApiPath
```

Pixel result:

```text
artifacts/edgetpu-research/nnapi-public-smoke/instrumentation.txt
  OK (1 test)

artifacts/edgetpu-research/nnapi-public-smoke/logcat.txt
  Created TensorFlow Lite delegate for NNAPI.
  Cannot create interpreter: Encountered unresolved custom op: edgetpu-custom-op-2.
  Node number 0 (edgetpu-custom-op-2) failed to prepare.
  Failed to apply delegates.
```

Conclusion: generic public NNAPI is not enough for this artifact. The custom op
requires the DarwiNN delegate path or an equivalent registered custom-op loader.

### 2. Recovered DarwiNN Delegate

This is the path Cosmo used. It loads `libdarwinndelegatejni.so`, calls
`DarwinnDelegate.createDelegate(...)`, then passes that delegate to the TFLite
backend interpreter.

Conclusion: the implementation path is understood and wired, but retail Pixel
returns a zero delegate handle for our package/UID.

### 3. LiteRT NPU / Google Tensor ML SDK

Official LiteRT NPU documentation describes LiteRT NPU acceleration and AOT
compilation as the public direction for NPU use. Google Tensor support is
currently tied to the Google Tensor ML SDK experimental access path.

Conclusion: this route is promising for a **standard-op source model** or an
AOT-produced artifact, but not for feeding an already DarwiNN-compiled
`edgetpu-custom-op-2` payload back into public LiteRT as source.

Relevant official docs:

```text
https://ai.google.dev/edge/litert/next/npu
https://ai.google.dev/edge/litert/next/tensor_ml_sdk
https://ai.google.dev/edge/litert/next/custom_op_dispatcher
https://github.com/google-ai-edge/litert-samples/blob/main/compiled_model_api/colab/LiteRT_AOT_Compilation_Tutorial.ipynb
```

### 4. MediaTek / Other Vendor AOT

The existing AOT work for BERT already found the correct pattern:

```python
from ai_edge_litert.aot import aot_compile as aot_lib
from ai_edge_litert.aot.vendors.mediatek import target as mtk_target

target = mtk_target.Target(mtk_target.SocModel.MT6989)
compiled_models = aot_lib.aot_compile("model.tflite", target=[target])
compiled_models.export("out/aot_model", model_name="model")
```

Local macOS/arm64 cannot complete the MediaTek host compiler path because the
needed plugin binary is unavailable in the macOS wheel. Older Linux/amd64
container retries reached the MediaTek apply-plugin step, but Apple Silicon
emulation lacks AVX for that binary.

Conclusion: use a real Linux x86_64 AVX host or CI runner for standard-op
models. This is the right route for a high-quality replacement conformer, not
for `audio_gatekeeper_edgetpu.tflite`.

### 5. Authorized Google Apps / AICore

DeviceConfig `AicQuota__quota` contains package names including:

```text
com.google.android.apps.pixel.agent
com.google.android.apps.pixel.psi
com.google.android.apps.pixel.aurelius
com.google.android.inputmethod.latin
com.google.android.apps.hardware.protostar.app
```

But this is AICore quota/config, not evidence of a public arbitrary-TFLite
loader. `com.google.android.apps.hardware.protostar.app` is not installed on the
Pixel 10 device tested. Installed authorized packages expose platform services
such as TextClassifier, TranslationService, VoiceInteractionService, and AICore
GenerativeModel-style APIs, but no documented public local interface was found
that accepts an arbitrary `.tflite`/DarwiNN model asset and returns inference.

Conclusion: the legitimate path is vendor/developer access or a documented SDK,
not IPC through an installed consumer app.

## Android Vendor Extension Context

Android's NNAPI vendor-extension documentation explicitly treats vendor
extensions as controlled device/runtime features. That is consistent with the
Pixel exposing `google-edgetpu` through NNAPI while still keeping custom
EdgeTPU/DarwiNN paths behind service-side authorization.

Relevant docs:

```text
https://source.android.com/docs/core/interaction/neural-networks/vendor-extensions
```

## Current Feasible Paths

1. **Publishable recovered-artifact analysis now.**
   State that the model is a complete DarwiNN EdgeTPU custom-op artifact, its
   tensor contract is recovered, and retail Pixel rejects non-authorized callers
   before delegate preparation.

2. **Standard-op conformer replacement.**
   Keep pushing the Inherent team toward a high-quality standard-op
   `[1,3000,128] -> [1,13]` TFLite. That can run on CPU today and is the only
   realistic source for public LiteRT NPU/AOT acceleration.

3. **Official Pixel NPU access.**
   Request Google Tensor ML SDK / LiteRT NPU access and ask specifically for a
   way to AOT compile or load a local standard-op conformer on Tensor G5.

4. **Linux x86_64 AVX AOT builder.**
   Run LiteRT AOT for MediaTek/other vendor targets on a host that can execute
   the vendor compiler plugin.

5. **Userdebug/vendor research build.**
   If a legitimate research build exposes EdgeTPU service authorization controls
   or test package enrollment, rerun the recovered DarwiNN path there.

## Short Public Claim

> Recovered Pixel/Cosmo audio gatekeeper is a 48 MB DarwiNN EdgeTPU custom-op
> model with inputs `[1,3000,128]` mel + `[1,3000]` mask and output `[1,13]`.
> On retail Pixel 10, public NNAPI creates but cannot resolve
> `edgetpu-custom-op-2`; the recovered DarwiNN path reaches Pixel EdgeTPU but
> returns delegate handle 0 for non-authorized UIDs. The blocker is platform
> authorization/tooling access, not missing model bytes.
