# Repository Layout

The canonical home for FM is `~/neural`. This file is the answer to
"where does X live?" for everything related to the project.

---

## What lives inside `~/neural` (canonical)

```
~/neural/
├── README.md                       # Project overview + capability matrix
├── LAYOUT.md                       # This file
├── settings.gradle.kts             # rootProject.name = "FM"
├── build.gradle.kts                # top-level Gradle
├── gradle/libs.versions.toml       # dependency catalog
│
├── app/                            # Android app module
│   ├── src/main/kotlin/            # Kotlin sources (cosmo-mirror namespace)
│   │   └── com/google/research/air/cosmo/
│   │       ├── audiocapture/       # Per-source audio captors
│   │       ├── controller/         # CosmoController + CosmoStateSource
│   │       ├── gatekeeping/        # Unified Gatekeeper + backends
│   │       ├── intent/             # IntentExtractor + LlmIntentClassifier
│   │       ├── fulfillment/        # Skill catalog + action handlers
│   │       ├── lib/                # llm/, soda/, record/, buds/, util/, …
│   │       ├── presence/           # Ambient agent surfaces (orb / tab / cards)
│   │       ├── appfunctions/       # AmbientAppFunctions (Android 17 surface)
│   │       └── ui/                 # Nav3 main shell + debug panels
│   ├── src/main/cpp/               # JNI sources (bert_gatekeeper_jni.cc, soda_jni.cc)
│   ├── src/main/assets/            # Bundled assets (manifest, prompts, schemas)
│   │   ├── models/                 # *.tflite — gitignored, provisioned externally
│   │   ├── soda/                   # *.zip language packs — gitignored
│   │   └── *.jinja2 / *.textproto  # prompts + schemas — tracked
│   ├── src/main/jniLibs/arm64-v8a/ # *.so files — gitignored, provisioned externally
│   ├── src/main/AndroidManifest.xml
│   ├── src/test/kotlin/            # JVM unit tests
│   └── src/androidTest/kotlin/     # Instrumented tests (need attached device)
│
├── baselineprofile/                # Baseline-profile generator module
│
├── docs/                           # All FM-specific design + spec docs
│   ├── architecture.md             # SYSTEM VIEW — read this first
│   ├── architecture-delta.md       # C1–C9 contracts + PR0–PR6 marching orders
│   ├── camel-port-notes.md         # CaMeL per-AST-node port reference
│   ├── fallback-paths.md           # 3-tier fallback per deferred subsystem
│   ├── blocker-register.md         # Active blockers
│   ├── aicore-api-gap.md           # AICore public-API surface limits
│   ├── audio-corpus-plan.md        # Inherent training corpus design
│   ├── release-signing-policy.md
│   ├── remote-inference-contract.md
│   └── architecture-update-hook.md
│
├── config/                         # Build-time + asset configs
│   ├── audio-corpus/               # Training-data label schemas
│   ├── signing/                    # Debug keystore + release signing policy
│   └── assets/model-manifest.schema.json
│
├── scripts/                        # Dev + provisioning + verification
│   ├── dev-loop.sh                 # 🔥 One-command edit→build→install→log→launch
│   ├── provision-from-recovery.sh  # Copy gitignored binaries from cosmo recovery
│   ├── download-models.sh          # Manifest-driven model fetcher (--verify-only mode)
│   ├── verify-*.sh                 # CI gates
│   ├── train-gatekeeper-baseline.py
│   └── emu-ci-run.sh               # Remote emulator CI integration (separate from local dev-loop)
│
└── app/src/main/assets/model-manifest.json  # Asset SHA-256 registry
```

The Kotlin package namespace is `com.google.research.air.cosmo.*` — that's
intentional cosmo-mirror so the architecture-delta's citations against the
recovered smali stay readable. The product name is "FM"; the package
namespace stays cosmo for parity.

---

## What lives OUTSIDE `~/neural` (and why)

| Location | What | Why outside | How `~/neural` reaches it |
|---|---|---|---|
| `~/Downloads/cosmo_recovered_large_assets/` | Recovered TFLite models + SODA language packs (~500 MB) | Files exceed GitHub's 100 MB per-file limit; binary; recoverable, not source | Symlinked-conceptually via `scripts/provision-from-recovery.sh` (copies into `app/src/main/assets/` on demand; gitignored at destination) |
| `~/Downloads/cosmo_recovered_native_libs/lib/arm64-v8a/` | 13 recovered JNI .so files | Same — large binaries | Same script provisions 3 of the 13 (the ones genesis bundles) |
| `~/Downloads/cosmo_baksmali/` | Disassembled smali (~2.5 GB) | Read-only reference; gigantic; not a source of truth for genesis code | Cited by line in `architecture-delta.md` and `docs/camel-port-notes.md` |
| `~/Downloads/cosmo_recovered_services/` | META-INF/services descriptors | Cosmo APK extraction byproduct | Reference only |
| `~/Downloads/cosmo_artifact_integrity/` | SHA-256 hashes + recovery ledger | Recovery audit trail | Verified at runtime by `app/src/main/assets/model-manifest.json` SHA-256 checks |
| `~/cosmo-dev/repo/docs/*_stack_map.md` | Substrate maps (audio, text, image, llm, dagger graph, settings, telemetry, UI surface) | Cosmo recovery documentation; authored by recovery work; updated occasionally | See "Consolidation roadmap" below — proposal to copy critical stack maps into `~/neural/reference/` |
| `~/cosmo-dev/repo/docs/architecture.md` | Upstream cosmo architecture (179 lines) | Reference for the delta; rarely changes | Cited from `architecture-delta.md` as the source it's a delta against |
| `~/cosmo-dev/WS4/docs/ws4-known-gaps.md` | Workstream-level intentional non-goals | Workstream organization; predates FM rename | Cited from `architecture.md §15` |
| `~/inherent/` | Joint audio→intent training pipeline (separate Python project) | Distinct workstream with its own git repo, training infra, dependencies | Output (`inherent.tflite` + `inherent.metadata.json`) drops into `~/neural/app/src/main/assets/models/` when ready. Contract: `~/inherent/contracts/runtime_contract.md` |
| `~/cosmo-dev/litert_aot_bert_work/` | MediaTek AOT compile pipeline drafts | Vendor-toolchain experiments; per-machine setup | Output (`bert_v0_mt6989.tflite`) drops into `~/neural/app/src/main/assets/models/` when produced |
| `~/cosmo-dev/mtk_npu_work/` | MediaTek NPU exploration | Same — vendor experiments | Same drop pattern |
| `~/cosmo-dev/soda-jni-test/` | Standalone SODA JNI smoke test (separate Gradle project) | Isolation for SODA native-lib debugging | Reference only; learnings inform `~/neural/app/src/main/cpp/soda/soda_jni.cc` |
| `~/cosmo-dev/cosmo-soda-interop/` | SODA interop tooling | Cross-project interop experiments | Reference only |
| `~/cosmo-dev/emu-provision/` | Emulator provisioning for `emu.devkeys.net` | Remote emulator service (separate from local dev loop) | Used by `~/neural/scripts/emu-ci-run.sh` for CI runs against the remote service |

---

## Consolidation roadmap (concern surfaced 2026-05-15)

The on-disk dispersal is intentional for the large recovery binaries (they
can't live in git) but **excessive for the docs and design references**.
Plan to consolidate:

### Phase 1 — bring docs into `~/neural/reference/` (low risk)

Create `~/neural/reference/` containing read-only mirrors of cosmo-recovery
docs that FM cites. Files involved:

- `~/cosmo-dev/repo/docs/architecture.md` → `~/neural/reference/cosmo-architecture.md`
- `~/cosmo-dev/repo/docs/audio_stack_map.md` → `~/neural/reference/cosmo-audio-stack-map.md`
- `~/cosmo-dev/repo/docs/text_stack_map.md` → `~/neural/reference/cosmo-text-stack-map.md`
- `~/cosmo-dev/repo/docs/llm_stack_map.md` → `~/neural/reference/cosmo-llm-stack-map.md`
- `~/cosmo-dev/repo/docs/image_stack_map.md` → `~/neural/reference/cosmo-image-stack-map.md`
- `~/cosmo-dev/repo/docs/dagger_graph_map.md` → `~/neural/reference/cosmo-dagger-graph-map.md`
- `~/cosmo-dev/repo/docs/settings_and_config_map.md` → `~/neural/reference/cosmo-settings-and-config-map.md`
- `~/cosmo-dev/repo/docs/telemetry_map.md` → `~/neural/reference/cosmo-telemetry-map.md`
- `~/cosmo-dev/repo/docs/bert_gatekeeper_litert_jni_replacement.md` → `~/neural/reference/cosmo-bert-aot-pipeline.md`
- `~/cosmo-dev/repo/docs/cosmo_apk_consolidated_plan.md` → `~/neural/reference/cosmo-apk-consolidated-plan.md`
- `~/cosmo-dev/WS4/docs/ws4-known-gaps.md` → `~/neural/reference/ws4-known-gaps.md`

Add `scripts/sync-reference-docs.sh` to re-pull these from cosmo-dev when
they change. Total size: ~500 KB; tracked in git as text.

**Status:** not yet executed.

### Phase 2 — bring `~/inherent` runtime contract under `~/neural`

Copy `~/inherent/contracts/runtime_contract.md` →
`~/neural/reference/inherent-runtime-contract.md` and keep it in sync.

The training pipeline source (`~/inherent/src/`) stays separate — it's a
distinct project with its own dependencies. The **contract** is what FM
needs to read.

**Status:** not yet executed.

### Phase 3 — point everything at the consolidated copies

Update internal doc cross-references in `architecture.md`,
`architecture-delta.md`, etc. to point to `reference/*` instead of
`~/cosmo-dev/repo/docs/*`. Once Phase 1+2 land, FM is **self-contained for
all doc purposes**.

**Status:** depends on Phase 1+2.

### Out of scope — large binaries stay out

The 600+ MB of recovered binaries (TFLites, SODA packs, .so files) stay
out of `~/neural/` git history. They live at `~/Downloads/cosmo_recovered_*`
and get copied into `~/neural/app/src/main/assets/...` and
`~/neural/app/src/main/jniLibs/...` by `scripts/provision-from-recovery.sh`.

Destination paths are gitignored (see `.gitignore` patterns
`app/src/main/assets/models/*.tflite`, `app/src/main/assets/soda/*.zip`,
`app/src/main/jniLibs/arm64-v8a/*.so`, `app/libs/*.jar`).

The build works because the files are present-untracked on the dev's
machine. New clones run `scripts/provision-from-recovery.sh` to populate.

---

## The on-disk story for a new contributor

1. Clone `~/neural`: `git clone git@github.com:maceip/FM.git neural` (if access granted)
2. Provision recovered binaries: `cd neural && scripts/provision-from-recovery.sh`
   - Requires `~/Downloads/cosmo_recovered_*` to be present (or `COSMO_RECOVERY_ROOT` set to wherever the recovery lives)
3. Build + install + launch: `scripts/dev-loop.sh`
   - Requires an attached Android device or running emulator
4. Read docs in `docs/`, starting with `architecture.md`

That's the whole flow. Three commands; one prerequisite (the recovery
directory must exist on the local machine).

---

## Anti-goals

- **Do not** move `~/Downloads/cosmo_recovered_*` into `~/neural` — they're
  the canonical recovery artifacts and live read-only outside FM. Multiple
  projects (FM, inherent, soda-jni-test) source from the same recovery.
- **Do not** rename the `com.google.research.air.cosmo` package — it's
  intentional cosmo-mirror for architecture-delta parity citations.
- **Do not** track large recovered binaries in git — GitHub rejects >100 MB
  files; the entire repo previously hit this issue (see `project_fm_repo.md`
  memory for the orphan-branch reset that fixed it).
