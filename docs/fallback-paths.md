# Fallback Paths

For each subsystem that's currently deferred, partial, or planned, this doc
gives a 3-tier ordered fallback. The order is **always**:

1. **🟢 Cosmo recovered** — first preference. We have the bytes, the format
   is known, the integration shape is documented. Lowest risk.
2. **🟡 Commercial or open-source third-party** — second preference. Real
   models / libraries we can pull off the shelf. Adds a dependency; pinning
   + license + size are real concerns.
3. **🔴 Build it ourselves** — last preference. Either no equivalent exists
   off the shelf, or licensing/quality forces us to author. Real engineering
   investment.

Each subsystem also flags the **trigger condition** that would force us to
move down a tier (e.g., "Pixel never returns" → can't use cosmo's EdgeTPU
Conformer, move to tier 2).

---

## Voice output / TTS (active replacement path — see architecture.md §16)

What we need: synthesized speech for the assistant's spoken replies. Inputs:
text strings (skill responses, confirmation prompts). Output: audio buffer
ready to play through `AudioTrack`.

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **KittenTTS mini through Sherpa-ONNX** | Selected ship path. It uses the same public Android/Kotlin Sherpa `OfflineTts.generateWithConfigAndCallback` API as Kokoro, keeps Cosmo's streaming `AudioTrack` shape, and passed Pixel instrumentation with first audio at ~1.8s. |
| 🟢 1b | **Kokoro 82M ONNX through Sherpa-ONNX** | Quality comparison path. Kokoro is Apache-2.0, 82M params, 24kHz output, and works through the same app interface, but full and int8 bundles were not interactive enough in real-device CPU tests. Keep as a swappable model bundle, not the launch default. |
| 🟢 1c | **Kokoro 82M single E2E ONNX** (`aufklarer/Kokoro-82M-ONNX` / Soniqo) | Viable validation path if we want fixed-shape ONNX Runtime + NNAPI directly. Inputs are phoneme ids, mask, 256-dim voice embedding, speed, random phases; output is `[1,1,120000]` 24kHz PCM plus valid length. It is newer and less battle-tested than Sherpa-ONNX, so use it as a comparison/prototype rather than the first app integration. |
| 🟡 2 | **Android `TextToSpeech`** (platform builtin) | Keep only as an emergency fallback. Real-device smoke proved it works, but quality is too robotic for the product voice. |
| 🟡 3 | **Supertonic 3 ONNX** | Worth benchmarking behind the same interface: 2026 open-weight, 31 languages, ONNX Runtime, 44.1kHz, expression tags, small enough for on-device. Do not switch before a device listen/latency pass because it is newer than Kokoro and has a different voice/style contract. |
| 🟠 4 | **Cosmo VoiceLM + Vogo** (`libvogo_jni.so` + `voicelm/*.tflite`) | Research-only unless recovered intact. The recovered stack shows the desired architecture, but `voicelm/temporal_decoder_int4-graph-custom_op.tflite` is truncated by 64 MB, Vogo is a private native runtime, and prior recovered-app launch logs fail capability initialization with `NOT_FOUND: No suitable capability found`. |
| 🔴 5 | **Train a small distilled voice model** | Not a launch path. Even a clean-room VoiceLM/SoundStream replacement is a research project with data, vocoder, tokenizer/phonemizer, latency, and mobile export risk. |

**Decision:** replace the platform TTS implementation with a Sherpa-backed
`VoiceOutput` provider, keeping Android `TextToSpeech` as fallback. Use Cosmo
for API shape only: streaming callback, reusable task/engine instance,
single utterance update, and `AudioTrack` playback.

**Implementation status:** wired in `SherpaVoiceOutput` with the official
Sherpa-ONNX Android runtime and `kitten-mini-en-v0_1-fp16` model. Hydrate
the gitignored model assets with `scripts/download-voice-models.sh`.

**Validation gate before locking the voice model:** on Pixel 10 and Xiaomi, run
the same 20 assistant utterances through (1) Android TTS, (2) Kitten CPU,
(3) Kokoro int8 CPU, and, where supported, (4) NNAPI/NPU. Record cold start, first-audio
latency, total synthesis time, failure reason, model RSS, and a human
listen-score. The launch model must stay comfortably interactive for short skill
replies.

Source checks: `hexgrad/Kokoro-82M` model card (Apache-2.0, 82M params,
24kHz, StyleTTS2/iSTFTNet architecture), Sherpa-ONNX Kokoro docs and Android
sample (`OfflineTts.generateWithConfigAndCallback`), and
`aufklarer/Kokoro-82M-ONNX` model card (fixed-shape ONNX Runtime E2E variant).

---

## Sentence embeddings / semantic search (Gecko replacement — currently 🟡 DEFERRED — see architecture.md §7.2)

What we need: dense 256-dim vectors per text chunk for the persistent
`ContextStore.queryByEmbedding`. Inputs: arbitrary strings. Output: float
vector; ANN search over stored vectors returns relevant context for prompt
grounding.

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Cosmo Gecko** (`gecko_out256_tpu_v0.tflite` + `gecko/i18n_sentencepiece.model`) | Recovered. 256-dim output. Multilingual sentencepiece tokenizer. **Caveat:** cosmo itself doesn't appear to consume it — the on-device half of the pipeline is wired but `MemoryStorage.search` is `NoOpMemoryImpl` in the cosmo recovery, so we'd be the first real consumer |
| 🟡 2a | **sentence-transformers `all-MiniLM-L6-v2`** (open-source, Apache-2.0) | 384-dim, 22 MB, runs fast in TFLite or ONNX Mobile. Best off-the-shelf quality for the size |
| 🟡 2b | **`nomic-embed-text-v1.5`** (open-source, Apache-2.0) | 768-dim, 137 MB. Higher quality, larger footprint. Has Matryoshka support — can truncate to 256 for parity with Gecko output dim |
| 🟡 2c | **Google MediaPipe Text Embedder** | Platform-bundled when available (varies). Free, fast, lower quality than sentence-transformers |
| 🟡 2d | **Vertex AI / OpenAI / Cohere embed APIs** | Excellent quality, network-dependent, breaks on-device-first |
| 🔴 3  | **Distill our own embedder from MiniLM** | Few weeks of work; only worthwhile if we want a specific output dim + tokenizer pair |

**Trigger to descend:** Gecko (tier 1) ANN search returns poor recall on our
domain (Android-app-text content) → tier 2a/2b.

**Recommendation today:** start with tier 1 (Gecko) since assets are already
in the recovery; if quality disappoints, tier 2a (MiniLM-L6-v2) has best
on-device quality:size tradeoff.

---

## Voice Match / speaker identification (currently ⚠️ PARTIAL — see architecture.md §3.3)

What we need: per-speaker identification so `TranscriptionData.speakerLabel`
becomes meaningful. Inputs: PCM audio. Output: speaker ID matched against
enrolled voice profile (or `unknown`).

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Cosmo MARBLE Voice Match** (`voice_match/MARBLE_speakerid.tflite` + `MARBLE_voice_filter.tflite`) | Bundled inside every SODA pack. JNI surface (`SodaEnrollmentManagerImpl.nativeEnroll*`) ported. **Only the enrollment UI is missing** — about a day of UI work to flip on |
| 🟡 2a | **SpeechBrain ECAPA-TDNN** (open-source, Apache-2.0) | Pretrained voxceleb model, ~24 MB. State of the art for short-utterance speaker ID. Quantized variants run on-device |
| 🟡 2b | **NIST SRE pretrained Resemblyzer** (open-source, MIT) | Smaller (~14 MB), older architecture but well-validated. Embedding-based; cosine distance for matching |
| 🟡 2c | **Pyannote `embedding/wespeaker-voxceleb-resnet34-LM`** (open-source, MIT) | Good quality, ~70 MB. Pairs with Pyannote diarization stack if both are needed |
| 🔴 3  | **Train our own ECAPA-TDNN variant** | Voxceleb-style training. Several weeks. Only justified if licensing or quality issues block tier 1+2 |

**Trigger to descend:** never — tier 1 should just work. The only reason to
move to tier 2 is if the SODA JNI surface can't be re-bound on a target
device for some platform reason.

**Recommendation today:** finish the tier 1 enrollment UI. That's the
single highest-leverage 1-day investment available right now (see
architecture.md §3.3).

---

## Diarization / speaker separation (currently 🟡 DORMANT — see architecture.md §3.2)

What we need: who-spoke-when boundaries in ambient audio. Inputs: PCM.
Output: time-stamped speaker turns.

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **SODA diarization** (`diarization/*` files inside each SODA pack) | Already on-device. JNI surface exists (`nativeDeleteDiarizationProcessor`, `clearDiarizationCache`). **Just need to enable the config bit** in `SodaConfigBuilder` and route the output into `TranscriptionData.speakerLabel`. ~half a day |
| 🟡 2a | **Pyannote diarization-3.1** (open-source, MIT) | Best off-the-shelf. ~150 MB quantized. Not designed for fully on-device but runs reasonably on flagship phones |
| 🟡 2b | **SpeechBrain VBx diarization** (open-source, Apache-2.0) | Variational Bayes-based clustering on speaker embeddings. Smaller than Pyannote, requires the ECAPA-TDNN embedder from tier 1 of Voice Match |
| 🔴 3  | **Build clustering on top of MARBLE speaker embeddings** | If MARBLE voice-match embeddings are exposed via the JNI, clustering them in Kotlin is ~200 LOC. Quality depends entirely on the embedder |

**Trigger to descend:** never expected — tier 1 is recovered and free.

**Recommendation today:** turn on tier 1 when the use case appears (e.g.,
multi-speaker meeting capture). Today no consumer reads `speakerLabel`, so
flipping it on without a consumer is wasted work.

---

## Inherent (joint audio→intent model — currently ⚠️ PARTIAL — see architecture.md §4.1)

What we need: a single CPU model that takes 16 kHz PCM and emits a vector
of per-skill scores (the on-device equivalent of cosmo's EdgeTPU Conformer
when no EdgeTPU is available).

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **`~/inherent` project produces `inherent.tflite`** | The dedicated training pipeline at `~/inherent`. Plan: synthetic + recorded corpus → trained model → int8 quantized TFLite + metadata sidecar (`inherent.metadata.json`). 5–6 weeks per the project plan. **This is the canonical path** |
| 🟡 2a | **Fine-tune Distil-Whisper-tiny + classifier head** | Use Distil-Whisper as a frozen encoder, add a 12-output sigmoid classifier head. ~40 MB. ~1 week to train + quantize + validate |
| 🟡 2b | **NeMo Conformer-CTC small + classifier head** | Same idea, NeMo backbone. Larger (~120 MB). Better acoustic invariance |
| 🟡 2c | **mediapipe.tasks.audio.AudioClassifier** | Platform-bundled, simpler API, lower quality |
| 🔴 3  | **Train Conformer from scratch** | What `~/inherent` is already doing. Tier 1 IS this path |

**Trigger to descend:** `~/inherent` slips beyond the 6-week budget → use
tier 2a as an interim model so the `InherentBackend` slot has something to
return while the real model trains.

**Recommendation today:** stay on tier 1; the slot is wired and waiting for
the .tflite. If we need a placeholder *that actually classifies*, tier 2a
is a 1-week stopgap.

---

## mt6989 BERT variant (currently 🔵 PLANNED — see architecture.md §4.2)

What we need: a BERT classifier compiled for the MediaTek Dimensity 9300
(NPU codename mt6989) so MediaTek flagships get the same accelerated path
that Tensor G4/G5 Pixels have today.

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Cosmo's drafted AOT compile pipeline** | Documented in `$COSMO_DEV_ROOT/repo/docs/bert_gatekeeper_litert_jni_replacement.md`. Uses `mtk_target.SocModel.MT6989` from the MediaTek Neuron compiler. Working scripts exist under `$COSMO_DEV_ROOT/litert_aot_bert_work/`. **Just needs to be run on a machine with the Neuron toolchain** |
| 🟡 2a | **TFLite default (no NPU acceleration)** | Fall back to `bert_v0_cpu.tflite` for MediaTek devices. Slower but works. The selector already does this when no SoC-specific variant is present |
| 🟡 2b | **Run on the MediaTek device's GPU via TFLite GPU delegate** | Middle ground. Worse latency than NPU but better than CPU. Requires a separate `bert_v0_mtk_gpu.tflite` |
| 🔴 3  | **Hand-author quantized BERT for mt6989** | Weeks of work; only justified if the AOT toolchain breaks fundamentally |

**Trigger to descend:** the MediaTek Neuron toolchain blocks the AOT compile
on some op → tier 2a (CPU fallback works today).

**Recommendation today:** stay on tier 1; the AOT pipeline is drafted. The
manifest already reserves the slot with a placeholder hash, so when a real
.tflite arrives it just drops in.

---

## Conformer EdgeTPU audio gatekeeper activation (currently ⚠️ PARTIAL — see architecture.md §4.1)

What we need: activation of the recovered `audio_gatekeeper_edgetpu.tflite`
on devices that have the Tensor Darwinn delegate. The model and JNI are
bundled; activation is gated on a hardware-class probe.

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Pixel + Tensor Darwinn delegate** | Bundled `audio_gatekeeper_edgetpu.tflite` + `libdarwinndelegatejni.so`. **Blocker:** physical Pixel device for the activation probe. Arriving 2026-05-11 per session notes |
| 🟡 2a | **Inherent backend on CPU** | Tier 1 of the Inherent fallback path above. Same functional contract, runs on any device |
| 🟡 2b | **CPU recovery backend (BERT-on-transcript)** | Already the default for non-Pixel devices. Magic Mic gate + BERT classification. Lower fidelity than the audio path but functional |
| 🔴 3  | **N/A** | The audio path is fundamentally about latency; if neither EdgeTPU nor a small Inherent model is available, the CPU recovery backend is the design target |

**Trigger to descend:** Pixel device unavailable for >2 weeks → defer
EdgeTPU activation indefinitely; ship on Inherent (when ready) or
CpuRecovery (today's default).

**Recommendation today:** tier 2b is the live shipping path. Tier 1 is
"when Pixel arrives." No interim work needed.

---

## Browsing-agent backend (currently ⚠️ PARTIAL — see architecture.md §15)

What we need: an agent that drives Mariner / TaskMonitor (the browsing-agent
surface). Takes a high-level user query, produces a sequence of browser
actions (navigate, click, extract), surfaces progress to the user.

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Cosmo's Mariner backend** | Server-side in cosmo; **NOT recovered** from APK (it's server code). No tier-1 option exists from recovery |
| 🟡 2a | **Browser-Use** (open-source, MIT) | Python agent framework for browser automation. Server-hosted. ~1 week of integration to wrap behind our remote-inference contract |
| 🟡 2b | **OpenAI's `gpt-4o` with browsing tool** (commercial) | Network-required, ~$5/1M tokens, high quality. Behind opt-in user consent |
| 🟡 2c | **Anthropic Claude with computer use** (commercial) | Similar to 2b. Different agent characteristics |
| 🔴 3  | **On-device tool-using agent** with LiteRT-LM + custom tool dispatch | Currently infeasible at on-device context-window sizes for multi-step browser tasks. Wait for context windows + tool-calling fidelity to improve |

**Trigger to descend:** N/A — tier 1 doesn't exist; we start at tier 2.

**Recommendation today:** **tier 2a (Browser-Use behind our remote inference
contract)** is the path with lowest commitment. The TaskMonitor UI already
exists and renders agent prompt/response bubbles — wiring it to a Browser-Use
backend is a finite integration project.

---

## Personal Intelligence (PI) replacement (currently 🟡 EXPLICITLY SKIPPED — see architecture.md §15.3)

What cosmo had: Google's PI backend for personal-data aggregation. Genesis
explicitly skips this. The functional need: cross-source personal context
("what was that thing I mentioned yesterday about the dentist appointment").

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Cosmo's PI service** | Server-side; **NOT recovered** |
| 🟡 2  | **Our on-device `ContextStore`** (Room + SQLCipher) | What we have today. Encrypted local store; per-source ephemeral buffers age out per settings. Combined with Gecko embeddings (when we add them) gives semantic recall over local data only |
| 🔴 3  | **Build PI-equivalent backend** | Out of scope for FM. The whole point of the on-device design is to *not* aggregate personal data server-side |

**Trigger to descend:** N/A — tier 2 is the design.

**Recommendation today:** tier 2 is the answer. The work is: finish the
Gecko semantic-search path so `ContextStore.queryByEmbedding` is functional
(currently returns empty list).

---

## Workspace grounding replacement (currently 🟡 EXPLICITLY SKIPPED — see architecture.md §15.3)

What cosmo had: server-side fetch of Gmail / Calendar / Drive content for
prompt context. Genesis explicitly skips this.

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Cosmo's BARD Workspace grounding service** | Server-side; **NOT recovered** |
| 🟡 2  | **Android content providers + user permissions** | What we have today. Per-source captors read calendar / notifications / etc. through OS-mediated permissions. User owns the read path. No server fetch |
| 🔴 3  | **Build a Workspace grounding service** | Out of scope; same privacy reasoning as PI |

**Trigger to descend:** N/A.

**Recommendation today:** tier 2 is the design. No work needed.

---

## DBW (Distill-By-Watching) server pipeline (currently 🔵 PLANNED — see architecture.md §15.2)

What we need (eventually): an opt-in upload pipeline for accessibility-tree
captures so we can fine-tune screen-understanding models against real user
flow. Server-side; specifics deferred.

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Cosmo's DBW server pipeline** | Server-side; not recovered. Client-side library shape (`AndroidAccessibilityForest`) is recovered |
| 🟡 2a | **Self-hosted opt-in upload + Hugging Face training pipeline** | Build a minimal upload endpoint (e.g., FastAPI + S3-compatible storage) + adapt an existing screen-understanding training recipe (e.g., ScreenAI / Pix2Struct fine-tuning) |
| 🟡 2b | **Google Vertex AI custom training jobs** | Commercial, opt-in user upload. Less ownership |
| 🔴 3  | **Self-hosted from scratch** | Full pipeline (ingestion + storage + de-identification + training + model delivery). Months of work |

**Trigger to descend:** N/A — there's no tier 1.

**Recommendation today:** this is a long-tail planned item. Tier 2a is the
realistic path when the workstream activates. **No work needed now** beyond
keeping the client-side `AccessibilityForestCaptor` slot reserved (per
architecture.md §15.2).

---

## CaMeL trust layer LLM backends (currently 🔵 PLANNED — see architecture.md §12)

What we need: separate LLM service instances for the P-LLM (privileged,
sees user query + tool API) and Q-LLM (quarantined, parses untrusted
content into typed primitives).

| Tier | Option | Notes |
|---|---|---|
| 🟢 1 | **Same `LLM` instance for both** (e.g., AICore Gemini Nano for both P-LLM and Q-LLM) | What's reachable today. The reference implementation does this — the Q-LLM is a separate session of the same model. Genesis can wrap the same `LLM` in two services with different system prompts |
| 🟡 2a | **Different LLMs for each role** | E.g., on-device LiteRT-LM Gemma for P-LLM, remote inference for Q-LLM (because Q-LLM tolerates higher latency). Adds complexity, not clear win |
| 🟡 2b | **Specialized Q-LLM** (e.g., a small extraction-only fine-tune) | A Q-LLM that's purpose-trained for "extract this typed field from this untrusted blob" might be smaller, faster, and more reliable than a general LLM. ~2 weeks of fine-tuning work |
| 🔴 3  | **Custom interpreter without LLM Q-extraction** | Replace `query_ai_assistant` with regex/grammar-based extractors. Loses generality |

**Trigger to descend:** tier 1 latency too high for the loop → tier 2a/2b.
Tier 1 quality unreliable for type coercion → tier 2b.

**Recommendation today:** tier 1 when CaMeL workstream starts. Run both
roles on AICore Gemini Nano. Optimize later.

---

## Summary triage

For a project lead picking what to invest in next, sorted by leverage
(estimated time × value):

| Subsystem | Recommended tier | Effort | Why now? |
|---|---|---|---|
| Voice Match enrollment UI | 🟢 1 | ~1 day | Cosmo assets + JNI already ported; only UI missing. Highest leverage today |
| Build/run loop (`scripts/dev-loop.sh`) | n/a | DONE | Unblocks all other iteration |
| Diarization config flip | 🟢 1 | ~½ day | Needs a consumer first; skip until there's one |
| Inherent model (`~/inherent`) | 🟢 1 | 5–6 weeks | Already in flight on a separate project |
| TTS / Voice output | 🟢 KittenTTS mini via Sherpa-ONNX, Android TTS fallback | first app integration complete; device benchmark continues | Platform TTS works but quality is unacceptable. VoiceLM/Vogo remains research-only until an intact private runtime/asset set can initialize |
| Gecko semantic search | 🟢 1 | ~3 days | Assets present; ContextStore hook exists. Unblocks tier-2 PI replacement |
| Conformer EdgeTPU activation | 🟢 1 | 1 day after Pixel arrives | Hardware-dependent |
| mt6989 BERT | 🟢 1 | 1 day on a Neuron-toolchain machine | Hardware-dependent |
| Browsing-agent backend | 🟡 2a (Browser-Use) | ~1 week | Tier 1 doesn't exist; tier 2 is the only path |
| CaMeL trust layer | 🟢 1 (same LLM, two services) | Per camel-port-notes.md — ~2 engineer-weeks for Plan DSL variant | Defense-by-design before more skills land |
| DBW server pipeline | 🟡 2a | Months | Long-tail; not urgent |

The takeaway: **most subsystems we currently call "deferred" have a free
tier-1 cosmo-recovered path**, and the only ones that don't (browsing-agent
backend, DBW server, PI replacement, Workspace grounding) are either
intentionally-skipped or planned-but-not-urgent.

The lowest-friction high-value work today is **Voice Match enrollment UI**
(~1 day, tier 1 path, ships a real user-visible feature).
