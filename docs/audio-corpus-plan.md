# Audio Corpus Plan

Cosmo should use public corpora aggressively, but product builds must default to license-safe subsets. Hard-to-get, high-value examples are first-party hand recordings with stricter metadata and review gates.

## Source Strategy

- Intent-ish positives: SLURP, Speech-MASSIVE, Google Speech Commands, Fluent Speech Commands, synthetic positives, and first-party clips.
- Conversational hard negatives: AMI, ICSI, Common Voice, and LibriSpeech.
- Ambient/non-speech negatives: AudioSet, FSD50K, DCASE/TUT, SONYC-UST, ESC-10/50, UrbanSound8K, Nonspeech7k, and selected Freesound clips.
- Wearable/domain coverage: Wearable SELD, VibraVox, and first-party Buds/watch/glasses/local Android peer recordings.
- Hard near misses: mine transcripts for words like `call`, `timer`, `calendar`, `list`, `search`, `meeting`, `remind`, and `message`, then manually reject actual assistant commands.

## License Policy

`product_eligible` includes only permissive, attribution, and first-party sources. Mixed-license sources must be filtered to CC0/CC-BY or equivalent before product training. Noncommercial, no-derivatives, and ambiguous-license datasets are allowed only in `research_eval`.

`first_party_hand_recorded` is the override path for labels public corpora do not cover well. Raw recordings should stay outside git; only manifests and aggregate stats belong here.

## Normalized Heads

The registry maps datasets into these heads:

- Intent positives: `timer`, `list`, `search`, `calendar`, `person`, `event`, `device_control`.
- Negatives: `speech_non_command`, `near_miss`, `ambient_non_speech`, `human_non_speech`.
- Domains: `buds_domain`, `glasses_domain`, `watch_domain`, `local_android_peer`.

## Files

- `config/audio-corpus/datasets.json`: dataset registry, license category, product eligibility, roles, and weak label mapping.
- `config/audio-corpus/label-map.json`: normalized heads and near-miss policy.
- `config/audio-corpus/hand-label-schema.json`: required metadata for hand-recorded clips.
- `config/audio-corpus/gatekeeper-targets.json`: mapping from corpus heads into the app's 13 raw-audio heads and 19-output BERT contract.
- `scripts/audio-corpus-plan.py`: validates the registry and prints selected datasets for a profile.
- `scripts/train-gatekeeper-baseline.py`: dependency-free bootstrap trainer for transcript-level gatekeeper experiments.

## Training Bootstrap

Start with the local smoke trainer:

```sh
scripts/train-gatekeeper-baseline.py --target-set audio13 --profile product_eligible
scripts/train-gatekeeper-baseline.py --target-set text19 --profile product_eligible
```

This writes `train.jsonl`, `val.jsonl`, `metrics.json`, and `model.json` under `artifacts/audio-corpus/runs/<timestamp>-<target>`. These JSON models are not Android runtime artifacts; they are checkpointable training smoke outputs while public corpus adapters, audio feature extraction, and TFLite export are added.

First-party manifests can be added with repeated `--manifest path/to/labels.jsonl` arguments. Rows should follow `config/audio-corpus/hand-label-schema.json`; `primary_label` and `secondary_labels` are mapped through `config/audio-corpus/gatekeeper-targets.json`.

Install the current `~/inherent` LiteRT export into the Android runtime assets:

```sh
scripts/install-inherent-litert-artifact.py
scripts/download-models.sh --verify-only
```

`~/inherent` owns training and export. The installer defaults to `~/inherent/artifacts/asap-static3000-int8/`, copies `inherent.tflite` and `inherent.metadata.json` into `app/src/main/assets/models/`, then upserts both into `app/src/main/assets/model-manifest.json`. The older `model-groups/001/export/litert` path is a 50-frame export and must not be installed. The Android side runs `audio_frontend.tflite` first and feeds the mel tensor into `inherent.tflite`, matching `~/inherent/contracts/runtime_contract.md`.

## Next Steps

1. Run `scripts/audio-corpus-plan.py --profile product_eligible` for product-safe candidates.
2. Run `scripts/audio-corpus-plan.py --profile research_eval` for broad offline research/eval coverage.
3. Run the bootstrap trainer to keep the label contract and metrics loop live.
4. Add downloader/adapter scripts per dataset only after license review.
5. Start first-party manifests with `review_state=raw`, then promote clips through labeled/reviewed states.
6. Improve the `~/inherent` model group and reinstall its LiteRT export into `~/neural` with `scripts/install-inherent-litert-artifact.py`.
