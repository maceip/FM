# BERT Gatekeeper LiteRT JNI Replacement

## Goal

Build a clean, editable replacement for `libbert_gatekeeper_jni.so` that is binary-compatible with the recovered Kotlin/JNI surface, while using our own C++ implementation and LiteRT runtime.

This document is for the standalone BERT gatekeeper implementation track. The recovered native library is reference material only and should not be shipped.

## Recovered JNI Surface

Java/Kotlin class:

```text
com.google.research.air.cosmo.gatekeeping.models.BertEncoderImpl
```

Native exports to preserve:

```cpp
extern "C" JNIEXPORT jlong JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeCreate(
    JNIEnv* env, jclass clazz, jbyteArray vocab_buffer, jbyteArray model_buffer,
    jboolean is_tpu_enabled);

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeRunStandardEncoder(
    JNIEnv* env, jclass clazz, jlong handle, jstring query,
    jint segment_id_override);

extern "C" JNIEXPORT jobject JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeRunMultiEncoder(
    JNIEnv* env, jclass clazz, jlong handle, jstring query,
    jint segment_id_override);

extern "C" JNIEXPORT void JNICALL
Java_com_google_research_air_cosmo_gatekeeping_models_BertEncoderImpl_nativeDestroy(
    JNIEnv* env, jclass clazz, jlong handle);
```

Recovered Kotlin signatures:

```kotlin
@JvmStatic external fun nativeCreate(
    vocabBuffer: ByteArray,
    modelBuffer: ByteArray,
    isTpuEnabled: Boolean,
): Long

@JvmStatic external fun nativeRunStandardEncoder(
    handle: Long,
    query: String,
    segmentIdOverride: Int,
): FloatArray?

@JvmStatic external fun nativeRunMultiEncoder(
    handle: Long,
    query: String,
    segmentIdOverride: Int,
): BertEncoderOutput?

@JvmStatic external fun nativeDestroy(handle: Long)
```

## Recovered Assets

Local analysis paths:

```text
/Users/mac/Downloads/cosmo_recovered_large_assets/assets__gatekeeper__bert_vocab.txt
/Users/mac/Downloads/cosmo_recovered_large_assets/assets__gatekeeper__bert_v0_cpu.tflite
/Users/mac/Downloads/cosmo_recovered_large_assets/assets__gatekeeper__bert_v0_p24.tflite
/Users/mac/Downloads/cosmo_recovered_large_assets/assets__gatekeeper__bert_v0_p25.tflite
```

Vocab facts:

```text
tokens: 30522
bytes: 231508
sha256: 07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3
[PAD]  = 0
[UNK]  = 100
[CLS]  = 101
[SEP]  = 102
[MASK] = 103
```

Model facts:

```text
bert_v0_cpu.tflite
size:   71935326
sha256: 1c6da7858a1cccd9ac6cd5c323d26ef99d962761e34d3f65871762123f95af34

bert_v0_p24.tflite
size:   44995072
sha256: 95142cbaaf2102c62e095c16a352cb19c8c05369859922fcbddc5699a8c903c8
op:     edgetpu-custom-op-2

bert_v0_p25.tflite
size:   46246528
sha256: f9099cd4b718b64e78ee021fb59a2c1a5581e918bcaff965665f0591de2b6e80
op:     edgetpu-custom-op-2
```

All three BERT models have the same public tensor contract:

```text
inputs:
  0: serving_default_input_word_ids:0  INT32 [1, 512]
  1: serving_default_input_type_ids:0  INT32 [1, 512]
  2: serving_default_input_mask:0      INT32 [1, 512]

output:
  0: StatefulPartitionedCall:0         FLOAT32 [1, 19]
```

## Runtime Direction

Use LiteRT C++ `CompiledModel` as the primary runtime path because NPU support is a target.

Important LiteRT C++ facts:

```cpp
// File model path.
CompiledModel::Create(env, model_filename, hardware_accelerators);

// In-memory model buffer. Required for JNI compatibility because nativeCreate
// receives model bytes, not a file path.
CompiledModel::Create(env, BufferRef(model_bytes), hardware_accelerators);
```

The model bytes must outlive the `CompiledModel`, so the native handle must own a copy of `modelBuffer`.

Current implementation status:

```text
app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/models/BertEncoderImpl.kt
app/src/main/kotlin/com/google/research/air/cosmo/gatekeeping/models/BertEncoderOutput.kt
app/src/main/cpp/bert_gatekeeper_jni.cc
app/src/main/cpp/litert_minimal_c_api.h
app/src/main/cpp/CMakeLists.txt
```

The implementation builds `libbert_gatekeeper_jni.so`, exports the recovered JNI symbols, loads and validates the vocab, owns the model bytes, and constructs BERT input tensors with WordPiece tokenization.

The backend now uses the app-packaged `libLiteRt.so` C ABI directly from C++:

```text
LiteRtCreateModelFromBuffer
LiteRtCreateCompiledModel
LiteRtCreateTensorBufferFromHostMemory
LiteRtRunCompiledModel
```

This keeps `nativeCreate` truly byte-buffer based. The previous app-cache temp `.tflite` bridge through LiteRT Java `CompiledModel.create(path, ...)` has been removed from the runtime path.

The LiteRT C ABI declarations are isolated in `litert_minimal_c_api.h`, pinned to LiteRT ref `47615eb6eaec25e8dfcd1aba922c560a57cba0a2`. This avoids ad hoc ABI declarations inside the JNI implementation while keeping the included surface small and auditable.

Debug smoke harness:

```text
app/src/debug/kotlin/com/cosmo/prototype/gatekeeper/BertGatekeeperSmokeHarness.kt
```

The debug build includes `/Users/mac/Downloads/cosmo_recovered_large_assets` as an asset source when present, so the harness can load the recovered vocab and CPU/NPU BERT models without checking large assets into git.

The harness also calls `nativeDebugBuildInputs` to validate tokenizer-side input construction: `[CLS]`, terminal `[SEP]`, padding mask, default type IDs, and `segmentIdOverride` propagation.

MediaTek NeuroPilot / NPU trial status:

```text
Legacy MediaTek converter scratch path:
$COSMO_DEV_ROOT/mtk_npu_work

Legacy inputs:
/Users/mac/Downloads/mtk_converter-8.13.0_packages.zip
/Users/mac/Downloads/np8_tutorial_20240826.zip
/Users/mac/Downloads/Android_V_NeuroPilotTFLiteShim_240408.rar
```

The old MediaTek package contains Linux x86_64 Python wheels for `mtk_converter` 8.13.0. It does not contain the `ncc-tflite` host compiler used by the tutorial `.tflite -> .dla` scripts. The tutorials reference an external NeuroPilot SDK layout:

```sh
ncc-tflite model.tflite -o model.dla --arch mdla5.5 --l1-size-kb 6144 --num-mdla 4
```

Local searches under `/Users/mac/Downloads` and `$COSMO_DEV_ROOT` did not find `ncc-tflite` or `neuron_sdk`.

The current public LiteRT path supersedes that older flow. The fresh scratch path is:

```text
$COSMO_DEV_ROOT/litert_aot_bert_work
```

Installed current packages in Linux containers:

```text
ai-edge-litert-nightly==2.2.0.dev20260506
ai-edge-litert-sdk-mediatek-nightly==0.2.0.dev20260506
```

The current AOT API can target MediaTek directly from the recovered CPU `.tflite`:

```python
from ai_edge_litert.aot import aot_compile as aot_lib
from ai_edge_litert.aot.vendors.mediatek import target as mtk_target

target = mtk_target.Target(mtk_target.SocModel.MT6989)
compiled_models = aot_lib.aot_compile(
    "models/bert_v0_cpu.tflite",
    target=[target],
    keep_going=False,
)
compiled_models.export("out/aot_bert_mt6989", model_name="bert_v0_cpu")
```

Local retry results:

```text
linux/amd64 container:
  MediaTek SDK and LiteRT AOT install successfully.
  AOT reaches MediaTek_MT6989 apply_plugin.
  apply_plugin aborts because the bundled x86_64 binary requires AVX, and
  Apple Silicon amd64 emulation exposes SSE/FMA but not AVX.

  Preserved error:
  FATAL ERROR: This binary was compiled with avx enabled, but this feature is
  not available on this processor (go/sigill-fail-fast).

linux/arm64 container:
  LiteRT AOT installs, but MediaTek AOT is effectively unavailable.
  The arm64 package does not include ai_edge_litert/tools/apply_plugin_main.

  Preserved error:
  FileNotFoundError: Failed to find apply plugin binary. AOT might not be
  available on your platform.
```

So the current blocker is local execution environment, not yet BERT model incompatibility. The next valid retry must run the same current AOT script on a real Linux x86_64 host with AVX support, or in a remote environment/CI runner with AVX exposed.

The Python converter does install and parse the BERT models in an `linux/amd64` container. Parser results confirm the same public tensor contract for all three assets. The CPU model parser reports this operator mix:

```text
CAST x2
RESHAPE x70
SUM x2
MAXIMUM x1
BROADCAST_TO x1
SUB x1
MUL x83
GATHER x2
STRIDED_SLICE x2
PAD x2
CONCATENATION x1
FULLY_CONNECTED x242
ADD x195
TRANSPOSE x80
BATCH_MATMUL x32
SOFTMAX x16
DIV x1
LOGISTIC x1
```

The existing `bert_v0_p24.tflite` and `bert_v0_p25.tflite` assets are not MediaTek-ready artifacts; MediaTek's parser sees them as a single custom `edgetpu-custom-op-2`.

Attempting the converter's TFLite dissection/execution path on `bert_v0_cpu.tflite` currently fails inside the MediaTek TFLite importer:

```text
mtk_dissect_tflite_model --input_model_file models/bert_v0_cpu.tflite \
  --input_data_files inputs/word_ids.npy,inputs/type_ids.npy,inputs/mask.npy \
  --export_dir out/dissect_bert_cpu --quiet True

AttributeError: 'NoneType' object has no attribute '__module__'
  at mtk_converter.python.converters.tflite.importer._convert_cast_operator
```

This only applies to the legacy `mtk_converter` path. The current LiteRT AOT path should be retried first on a real AVX-capable Linux x86_64 host before drawing any conclusion about BERT model compatibility.

## Native Handle

Suggested C++ handle:

```cpp
struct BertGatekeeperHandle {
  std::vector<uint8_t> vocab_bytes;
  std::vector<uint8_t> model_bytes;

  BertTokenizer tokenizer;

  LiteRtEnvironment environment;
  LiteRtModel model;
  LiteRtOptions options;
  LiteRtCompiledModel compiled_model;

  std::array<LiteRtTensorBuffer, 3> input_buffers;
  std::array<LiteRtTensorBuffer, 1> output_buffers;

  bool accelerator_requested = false;
  bool accelerator_enabled = false;
};
```

`jlong` returns `reinterpret_cast<jlong>(handle)`.

`nativeDestroy` deletes the handle and releases LiteRT resources.

## Tokenization Contract

The recovered native library embeds strings from:

```text
java/com/google/android/apps/hardware/protostar/components/bert/jni/bert_preprocessor.cc
java/com/google/android/apps/hardware/protostar/components/bert/jni/bert_interpreter.cc
```

Public LiteRT/TFLite BERT preprocessing behavior is the closest match:

1. Lowercase input text.
2. Strip combining marks and common precomposed Latin accents.
3. Basic-tokenize on whitespace, punctuation, and CJK boundaries.
4. Greedy WordPiece tokenize with `##` suffix pieces.
5. Add `[CLS]` at the beginning and `[SEP]` at the end.
6. Truncate to fit 512 tokens including specials.
7. Pad to exactly 512.

Input tensor construction:

```text
word_ids: token IDs, padded with 0
mask:     1 for real `[CLS] ... [SEP]` tokens, 0 for padding
type_ids: if segmentIdOverride >= 0, fill real tokens with that value;
          otherwise fill with 0
```

The recovered Kotlin wrapper passes `-1` when `segmentIdOverride == null`.

Open question to verify against native parity: whether `segmentIdOverride` applies to all real tokens or only non-special tokens. Default path uses `-1`, so this only matters for explicit override callers.

## `nativeCreate`

Behavior:

1. Validate non-null `vocabBuffer` and `modelBuffer`.
2. Copy Java byte arrays into owned `std::vector<uint8_t>`.
3. Build tokenizer from vocab text.
4. Create LiteRT `Environment`.
5. Create LiteRT model from `model_bytes`.
6. Validate tensor contract before compilation:
   - main subgraph has exactly 3 inputs
   - main subgraph has exactly 1 output
   - input names are `serving_default_input_word_ids:0`, `serving_default_input_type_ids:0`, and `serving_default_input_mask:0`
   - output name is `StatefulPartitionedCall:0`
   - input types are int32
   - output type is float32
   - input shape is `[1, 512]`
   - output shape is `[1, 19]`
7. Compile model from `model_bytes`.
8. Create input/output buffers.
9. Return native handle pointer.

Accelerator behavior:

```text
isTpuEnabled == false:
  compile for CPU.
  failure -> return 0.

isTpuEnabled == true:
  compile for NPU / accelerator path.
  failure -> return 0.
```

Returning `0` on accelerator failure matches the recovered Kotlin fallback behavior: it tries TPU first and then retries CPU when the handle is `0`.

## `nativeRunStandardEncoder`

Behavior:

1. Validate handle.
2. Convert `jstring query` to UTF-8.
3. Tokenize and build three `int32_t[512]` arrays.
4. Write arrays into LiteRT input buffers in model input order:
   - word IDs
   - type IDs
   - mask
5. Run `CompiledModel`.
6. Read float output.
7. Validate exactly 19 floats.
8. Return `jfloatArray` of length 19.

On failure, return `nullptr` and log enough detail to diagnose runtime or tensor issues.

## Output Index Mapping

The returned 19-float vector maps to:

```text
0  isInteresting / add_to_memory
1  hasAddToListIntent / add_to_list
2  hasTermSearchQuery / define_term, web_search
3  hasPhotoQuery / query_photos
4  hasCalendarEvent / create_calendar_event
5  hasCreateDocIntent / create_doc
6  hasPersonContext / provide_person_context
7  hasEventContext / provide_event_context
8  hasDeepResearchIntent / deep_research
9  hasInsightIntent / provide_insight
10 hasBrowsingAgentIntent / browsing_agent
11 hasCallingAgentIntent / call_agent
12 hasStartTimerIntent / add_timer
13 isShoppingCart
14 isCheckoutPage
15 isProductDetails
16 isSearchResults
17 isTravelCategory
18 isShoppingCategory
```

This mapping is not needed inside the native implementation, but it is useful for tests and debug logs.

## `nativeRunMultiEncoder`

Recovered return type:

```kotlin
data class BertEncoderOutput(
    val pooledEncoding: FloatArray,
    val tokenEncodings: FloatArray,
    val mask: FloatArray,
)
```

The observed classifier path uses `nativeRunStandardEncoder`, not `nativeRunMultiEncoder`.

Initial implementation options:

1. Return `nullptr` until a caller requires multi-output inference.
2. Implement only if we identify a model output path that exposes pooled/token encodings.

If implemented, construct:

```text
com/google/research/air/cosmo/gatekeeping/models/BertEncoderOutput
constructor: ([F[F[F)V
```

## Testing Plan

Unit tests:

```text
- Vocab loads to 30522 entries.
- Special token IDs match [PAD]=0, [UNK]=100, [CLS]=101, [SEP]=102, [MASK]=103.
- Tokenization creates exactly 512 IDs/mask/type IDs.
- Empty string produces [CLS], [SEP], padding.
- Long string truncates to 512 with final [SEP].
```

Model tests:

```text
- CPU model compiles from buffer.
- Input/output tensor contract matches expected names, types, and shapes.
- Inference returns exactly 19 finite floats for representative queries.
```

Interop tests:

```text
- Load replacement library under the recovered Kotlin class.
- nativeCreate returns non-zero for CPU model.
- nativeRunStandardEncoder returns jfloatArray length 19.
- nativeDestroy is idempotent for one valid handle lifecycle.
```

Parity tests, if the recovered native library can be executed in a controlled harness:

```text
- Compare tokenization-sensitive prompts against recovered output.
- Compare CPU model outputs within tolerance.
- Verify TPU failure/fallback behavior returns 0 in create path.
```

Representative prompt prefixes observed in Kotlin:

```text
Screen: 
Notification: 
Buds audio: 
Device audio: 
Watch audio: 
Ambient audio: 
```

## SODA / Universal Native Voice

See [`soda_universal_voice_jni_replacement.md`](soda_universal_voice_jni_replacement.md)
for the parallel plan covering `libsoda_dev_jni.so`. SODA is granted a
narrow exception to ship the recovered shared object verbatim if a clean
replacement isn't ready by M3; that exception does not apply to the
BERT gatekeeper.

## Open Questions

```text
- Exact LiteRT Android C++ packaging path to use: Maven prebuilt C++ SDK vs source build.
- Exact NPU accelerator enum/options for Pixel Tensor / Edge TPU path.
- Whether p24/p25 `edgetpu-custom-op-2` models compile through public LiteRT NPU APIs or need vendor-specific plugin packaging.
- Whether `segmentIdOverride` should apply to special tokens.
- Whether any real caller requires `nativeRunMultiEncoder`.
```
