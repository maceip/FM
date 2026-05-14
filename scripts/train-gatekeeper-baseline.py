#!/usr/bin/env python3
"""Train a dependency-free baseline gatekeeper from available transcript data.

This is a bootstrap trainer, not the final TFLite/audio training stack. It gives us a repeatable
training loop now, consumes first-party JSONL manifests when they exist, and writes versioned
artifacts that can be compared while dataset adapters and converter tooling come online.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import math
import random
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


REPO_ROOT = Path(__file__).resolve().parents[1]
CONFIG_DIR = REPO_ROOT / "config" / "audio-corpus"
TARGETS_PATH = CONFIG_DIR / "gatekeeper-targets.json"
DATASETS_PATH = CONFIG_DIR / "datasets.json"
FIXTURES_PATH = REPO_ROOT / "scripts" / "test-fixtures" / "gatekeeper-utterances.json"
DEFAULT_OUTPUT_ROOT = REPO_ROOT / "artifacts" / "audio-corpus" / "runs"


SKILL_SEEDS: dict[str, list[str]] = {
    "add_to_memory": [
        "Key insight from the meeting was customer churn dropped.",
        "Remember that Avery is allergic to peanuts.",
        "The important takeaway is that we should launch in June.",
        "My goal is to save this decision for later.",
    ],
    "add_to_list": [
        "Add oat milk to my list.",
        "I should get coffee filters.",
        "Put batteries on the shopping list.",
        "Need to pick up toothpaste.",
    ],
    "define_term": [
        "Define polymorphism.",
        "What is the meaning of foreshadowing?",
        "What does vector search mean?",
        "Explain what a token is.",
    ],
    "query_photos": [
        "Find the photos of Berlin.",
        "Show me pictures from the hike.",
        "Look for screenshots of the receipt.",
        "Search my albums for the dog photo.",
    ],
    "create_calendar_event": [
        "Schedule a meeting with Avery on Tuesday at three.",
        "Add dinner tomorrow at seven to my calendar.",
        "Book a call with the design team next Friday.",
        "Create a calendar invite for the launch review.",
    ],
    "create_doc": [
        "Create a doc from these notes.",
        "Draft an email to the client about the renewal.",
        "Write a one pager about the roadmap.",
        "Summarize this into a document.",
    ],
    "provide_person_context": [
        "She said the deadline moved.",
        "What does my doctor usually recommend?",
        "Remind me what Avery likes.",
        "Tell me what the client mentioned last time.",
    ],
    "provide_event_context": [
        "What was the meeting about q3 roadmap?",
        "Give me context on the upcoming launch sync.",
        "What happened at the dentist appointment?",
        "What do we know about that event next week?",
    ],
    "deep_research": [
        "Research the market trends.",
        "Look into the long term impact.",
        "Analyze the data on battery usage.",
        "Study the best practices for this architecture.",
    ],
    "provide_insight": [
        "What are the pros and cons of this approach?",
        "How should we decide between the options?",
        "Is there a pattern in this feedback?",
        "Give me the trade offs.",
    ],
    "browsing_agent": [
        "Pay this bill online.",
        "Process a return for these jeans.",
        "Fill out that spreadsheet.",
        "Book the reservation on the website.",
    ],
    "call_agent": [
        "Call the dentist office.",
        "Contact the insurance company.",
        "Book an appointment with the clinic.",
        "Get a quote from the repair shop.",
    ],
    "add_timer": [
        "Set a timer for ten minutes.",
        "Start a countdown for five minutes.",
        "Remind me in twenty minutes.",
        "Let me know in one hour.",
    ],
    "is_shopping_cart": [
        "Screen shows cart subtotal checkout and shipping.",
        "The page has items in cart and a checkout button.",
    ],
    "is_checkout_page": [
        "Screen shows payment method shipping address and place order.",
        "Checkout page with billing details and promo code.",
    ],
    "is_product_details": [
        "Screen shows product details reviews price and size.",
        "Product page with color options and add to cart.",
    ],
    "is_search_results": [
        "Screen shows search results filters and sort options.",
        "Search results page with many matching items.",
    ],
    "is_travel_category": [
        "Screen shows flights hotels itinerary and destination.",
        "Travel page with airport departure and gate information.",
    ],
    "is_shopping_category": [
        "Screen shows products prices carts and checkout.",
        "Shopping page with item recommendations and sale prices.",
    ],
}

NEGATIVE_SEEDS = [
    "It is a beautiful day outside.",
    "The timer component in that app is hard to test.",
    "We should call this function after rendering.",
    "The calendar word appears in the article title.",
    "Search is mentioned in the meeting but nobody asked for anything.",
    "I made a list of issues during the conversation.",
    "The meeting about timers was mostly technical.",
    "There was music playing in the cafe.",
    "A truck drove by while someone was speaking.",
    "Please ignore this random background speech.",
    "Sine wave audio smoke test with no command.",
]

SKILL_ALIASES = {
    "web_search": "define_term",
    "draft_email": "create_doc",
}


@dataclass(frozen=True)
class Example:
    text: str
    labels: tuple[str, ...]
    source: str
    split: str | None = None


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def fail(message: str) -> None:
    print(f"error: {message}", file=sys.stderr)
    raise SystemExit(1)


def normalize_label(label: str) -> str:
    return SKILL_ALIASES.get(label, label)


def stable_bucket(text: str) -> int:
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    return int(digest[:8], 16) % 100


def tokenize(text: str) -> list[str]:
    return re.findall(r"[a-z0-9']+", text.lower())


def feature_indices(text: str, feature_count: int) -> dict[int, float]:
    text_lc = text.lower()
    features: dict[int, float] = {}

    def add(name: str, value: float = 1.0) -> None:
        digest = hashlib.blake2b(name.encode("utf-8"), digest_size=8).hexdigest()
        index = int(digest, 16) % feature_count
        features[index] = features.get(index, 0.0) + value

    tokens = tokenize(text_lc)
    for token in tokens:
        add(f"tok={token}")
    for first, second in zip(tokens, tokens[1:]):
        add(f"bi={first}_{second}")
    padded = f"  {text_lc}  "
    for size in (3, 4, 5):
        for index in range(max(0, len(padded) - size + 1)):
            add(f"char{size}={padded[index:index + size]}", 0.35)

    length = max(1.0, math.sqrt(sum(value * value for value in features.values())))
    return {index: value / length for index, value in features.items()}


def sigmoid(value: float) -> float:
    if value >= 0:
        z = math.exp(-value)
        return 1.0 / (1.0 + z)
    z = math.exp(value)
    return z / (1.0 + z)


def dot(weights: list[float], features: dict[int, float]) -> float:
    return sum(weights[index] * value for index, value in features.items())


def fixture_examples(labels: list[str], target_set: str) -> list[Example]:
    if not FIXTURES_PATH.exists():
        return []
    fixture = load_json(FIXTURES_PATH)
    examples: list[Example] = []
    audio_labels = labels[:13]
    for row in fixture.get("audio_fixtures", []):
        phrase = row.get("phrase")
        if not phrase or phrase.startswith("("):
            continue
        row_labels: list[str] = []
        if target_set == "audio13" and row.get("expected_audio_gatekeeper_top_index") is not None:
            index = int(row["expected_audio_gatekeeper_top_index"])
            if index < len(audio_labels):
                row_labels.append(audio_labels[index])
        else:
            expected = row.get("expected_skill_via_soda_then_regex")
            if expected:
                row_labels.append(normalize_label(expected))
        filtered = tuple(sorted({label for label in row_labels if label in labels}))
        if filtered:
            examples.append(Example(text=phrase, labels=filtered, source="gatekeeper_fixture"))

    for row in fixture.get("negative_controls_for_regression", []):
        text = row.get("input")
        if text:
            examples.append(Example(text=text, labels=(), source="gatekeeper_negative_fixture"))
    return examples


def seed_examples(labels: list[str]) -> list[Example]:
    examples: list[Example] = []
    for label in labels:
        for text in SKILL_SEEDS.get(label, []):
            examples.append(Example(text=text, labels=(label,), source="seed_positive"))
    for text in NEGATIVE_SEEDS:
        examples.append(Example(text=text, labels=(), source="seed_negative"))
    return examples


def manifest_examples(paths: Iterable[Path], labels: list[str], corpus_head_to_skill: dict[str, list[str]]) -> list[Example]:
    examples: list[Example] = []
    for path in paths:
        if not path.exists():
            fail(f"manifest not found: {path}")
        with path.open("r", encoding="utf-8") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                try:
                    row = json.loads(line)
                except json.JSONDecodeError as error:
                    fail(f"{path}:{line_number}: invalid JSONL row: {error}")

                text = row.get("transcript") or row.get("text") or row.get("phrase")
                if not isinstance(text, str) or not text.strip():
                    continue

                split = row.get("split")
                primary = row.get("primary_label")
                row_labels: list[str] = []
                if isinstance(primary, str):
                    row_labels.extend(corpus_head_to_skill.get(primary, [primary]))
                for label in row.get("secondary_labels", []):
                    if isinstance(label, str):
                        row_labels.extend(corpus_head_to_skill.get(label, [label]))

                normalized = tuple(sorted({normalize_label(label) for label in row_labels if normalize_label(label) in labels}))
                examples.append(Example(text=text, labels=normalized, source=f"manifest:{path.name}", split=split))
    return examples


def split_examples(examples: list[Example]) -> tuple[list[Example], list[Example]]:
    train: list[Example] = []
    val: list[Example] = []
    for example in examples:
        explicit = example.split
        if explicit in {"val", "test", "gold_eval"}:
            val.append(example)
        elif explicit == "train":
            train.append(example)
        elif stable_bucket(example.text) < 20:
            val.append(example)
        else:
            train.append(example)

    if not train or not val:
        midpoint = max(1, int(len(examples) * 0.8))
        train = examples[:midpoint]
        val = examples[midpoint:] or examples[:]
    return train, val


def train(
    train_set: list[Example],
    labels: list[str],
    feature_count: int,
    epochs: int,
    learning_rate: float,
    l2: float,
    seed: int,
) -> tuple[dict[str, list[float]], dict[str, float]]:
    rng = random.Random(seed)
    weights = {label: [0.0] * feature_count for label in labels}
    bias = {label: 0.0 for label in labels}
    rows = [(feature_indices(example.text, feature_count), set(example.labels)) for example in train_set]
    positive_weight = {}
    for label in labels:
        positives = sum(1 for _, targets in rows if label in targets)
        negatives = max(1, len(rows) - positives)
        positive_weight[label] = max(1.0, negatives / max(1, positives))

    for epoch in range(epochs):
        rng.shuffle(rows)
        step_lr = learning_rate / math.sqrt(epoch + 1)
        for features, targets in rows:
            for label in labels:
                y = 1.0 if label in targets else 0.0
                score = dot(weights[label], features) + bias[label]
                prediction = sigmoid(score)
                class_weight = positive_weight[label] if y == 1.0 else 1.0
                error = (prediction - y) * class_weight
                for index, value in features.items():
                    weights[label][index] -= step_lr * (error * value + l2 * weights[label][index])
                bias[label] -= step_lr * error

    return weights, bias


def predict_scores(weights: dict[str, list[float]], bias: dict[str, float], text: str, feature_count: int) -> dict[str, float]:
    features = feature_indices(text, feature_count)
    return {label: sigmoid(dot(label_weights, features) + bias[label]) for label, label_weights in weights.items()}


def evaluate(
    examples: list[Example],
    labels: list[str],
    weights: dict[str, list[float]],
    bias: dict[str, float],
    feature_count: int,
    threshold: float,
) -> dict[str, Any]:
    per_label: dict[str, dict[str, int]] = {label: {"tp": 0, "fp": 0, "fn": 0} for label in labels}
    exact = 0
    for example in examples:
        gold = set(example.labels)
        scores = predict_scores(weights, bias, example.text, feature_count)
        predicted = {label for label, score in scores.items() if score >= threshold}
        if predicted == gold:
            exact += 1
        for label in labels:
            if label in predicted and label in gold:
                per_label[label]["tp"] += 1
            elif label in predicted and label not in gold:
                per_label[label]["fp"] += 1
            elif label not in predicted and label in gold:
                per_label[label]["fn"] += 1

    label_metrics = {}
    total_tp = total_fp = total_fn = 0
    for label, counts in per_label.items():
        tp = counts["tp"]
        fp = counts["fp"]
        fn = counts["fn"]
        total_tp += tp
        total_fp += fp
        total_fn += fn
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = (2 * precision * recall / (precision + recall)) if precision + recall else 0.0
        label_metrics[label] = {
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            **counts,
        }

    micro_precision = total_tp / (total_tp + total_fp) if total_tp + total_fp else 0.0
    micro_recall = total_tp / (total_tp + total_fn) if total_tp + total_fn else 0.0
    micro_f1 = (
        2 * micro_precision * micro_recall / (micro_precision + micro_recall)
        if micro_precision + micro_recall
        else 0.0
    )
    return {
        "examples": len(examples),
        "exact_match": round(exact / len(examples), 4) if examples else 0.0,
        "micro_precision": round(micro_precision, 4),
        "micro_recall": round(micro_recall, 4),
        "micro_f1": round(micro_f1, 4),
        "labels": label_metrics,
    }


def write_jsonl(path: Path, examples: list[Example]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for example in examples:
            handle.write(json.dumps(example.__dict__, sort_keys=True) + "\n")


def write_outputs(
    output_dir: Path,
    args: argparse.Namespace,
    labels: list[str],
    train_set: list[Example],
    val_set: list[Example],
    weights: dict[str, list[float]],
    bias: dict[str, float],
    metrics: dict[str, Any],
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    write_jsonl(output_dir / "train.jsonl", train_set)
    write_jsonl(output_dir / "val.jsonl", val_set)
    with (output_dir / "metrics.json").open("w", encoding="utf-8") as handle:
        json.dump(metrics, handle, indent=2, sort_keys=True)
        handle.write("\n")
    model = {
        "schema_version": 1,
        "model_type": "hashed_logistic_regression_multilabel",
        "target_set": args.target_set,
        "feature_count": args.features,
        "threshold": args.threshold,
        "labels": labels,
        "bias": bias,
        "weights": weights,
        "notes": "Bootstrap artifact for local comparison only; not consumed by the Android runtime.",
    }
    with (output_dir / "model.json").open("w", encoding="utf-8") as handle:
        json.dump(model, handle, sort_keys=True)
        handle.write("\n")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target-set", choices=["audio13", "text19"], default="audio13")
    parser.add_argument("--profile", default="product_eligible")
    parser.add_argument("--manifest", action="append", default=[], help="Optional first-party/corpus JSONL manifest.")
    parser.add_argument("--output-root", type=Path, default=DEFAULT_OUTPUT_ROOT)
    parser.add_argument("--features", type=int, default=2048)
    parser.add_argument("--epochs", type=int, default=80)
    parser.add_argument("--learning-rate", type=float, default=0.45)
    parser.add_argument("--l2", type=float, default=0.0005)
    parser.add_argument("--threshold", type=float, default=0.08)
    parser.add_argument("--seed", type=int, default=1337)
    args = parser.parse_args()

    targets = load_json(TARGETS_PATH)
    datasets = load_json(DATASETS_PATH)
    if args.profile not in datasets.get("profiles", {}):
        fail(f"unknown corpus profile: {args.profile}")

    target = targets["target_sets"][args.target_set]
    labels = list(target["labels"])
    corpus_head_to_skill = targets.get("corpus_head_to_skill", {})

    examples = []
    examples.extend(seed_examples(labels))
    examples.extend(fixture_examples(labels, args.target_set))
    examples.extend(manifest_examples([Path(path) for path in args.manifest], labels, corpus_head_to_skill))
    if len(examples) < 10:
        fail("not enough examples to train")

    train_set, val_set = split_examples(examples)
    weights, bias = train(
        train_set=train_set,
        labels=labels,
        feature_count=args.features,
        epochs=args.epochs,
        learning_rate=args.learning_rate,
        l2=args.l2,
        seed=args.seed,
    )
    metrics = {
        "target_set": args.target_set,
        "profile": args.profile,
        "created_at": dt.datetime.now(dt.UTC).isoformat(),
        "train_examples": len(train_set),
        "val_examples": len(val_set),
        "epochs": args.epochs,
        "features": args.features,
        "threshold": args.threshold,
        "validation": evaluate(val_set, labels, weights, bias, args.features, args.threshold),
    }

    run_id = dt.datetime.now().strftime("%Y%m%d-%H%M%S") + f"-{args.target_set}"
    output_dir = args.output_root / run_id
    write_outputs(output_dir, args, labels, train_set, val_set, weights, bias, metrics)

    validation = metrics["validation"]
    print(f"TRAINING_RUN_COMPLETE target_set={args.target_set} output={output_dir}")
    print(
        "validation "
        f"examples={validation['examples']} "
        f"micro_f1={validation['micro_f1']} "
        f"precision={validation['micro_precision']} "
        f"recall={validation['micro_recall']} "
        f"exact_match={validation['exact_match']}"
    )


if __name__ == "__main__":
    main()
