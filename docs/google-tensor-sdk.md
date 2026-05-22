# Google Tensor SDK (Pixel LiteRT AOT)

Pixel EdgeTPU compiles and Play On-Device AI (PODAI) packaging for gatekeeper
and Inherent TFLite artifacts. **Tooling is not in this repo** — use the copies
in `~/Downloads`:

| File | Role |
| --- | --- |
| `LiteRT NPU AOT compilation for Google Tensor.ipynb` | Official walkthrough: install SDK, `aot_compile`, G3/G4/G5 outputs, AI Pack |
| `litert_plugin_compiler.tar.gz` | Required plugin (`liblitert_plugin_compiler.so`); set `GOOGLE_TENSOR_SDK_BETA` |

```text
GOOGLE_TENSOR_SDK_BETA=~/Downloads/litert_plugin_compiler.tar.gz
pip install ai-edge-litert-sdk-google-tensor==2.1.5
pip install ai-edge-litert-nightly==2.2.0.dev20260518
```

**Inputs from this repo / sibling projects:**

- Gatekeeper BERT / audio TFLite under `app/src/main/assets/models/` (from
  `scripts/download-models.sh`).
- `inherent.tflite` from `~/inherent` export — full AOT + contract notes in
  [`~/inherent/docs/google-tensor-sdk.md`](../../inherent/docs/google-tensor-sdk.md).

**Workflow:** CPU-validated `.tflite` → `ai_edge_litert.aot.aot_compile` →
per-SoC `*_Google_Tensor_G*.tflite` + `*_fallback.tflite` → optional
`ai_pack_export.export` for PODAI.

**Host:** AOT on Linux x86_64 (AVX), not Apple Silicon. **Device:** validate
EdgeTPU compiles on physical Pixel; smoke CPU fallback with `CompiledModel` anywhere.

References: [LiteRT NPU](https://ai.google.dev/edge/litert/next/npu) ·
[Tensor ML SDK](https://ai.google.dev/edge/litert/next/tensor_ml_sdk) ·
[Play on-device AI](https://developer.android.com/google/play/on-device-ai)
