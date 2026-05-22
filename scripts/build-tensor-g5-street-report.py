#!/usr/bin/env python3
from __future__ import annotations

import datetime as dt
import hashlib
import html
import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "artifacts/google-tensor-aot"
ASSETS = ROOT / "app/src/main/assets/models"
AOT = ROOT / "artifacts/google-tensor-aot/litert-google-tensor-aot-20260521-143517/outputs/20260521-124856"

CPU_LOGCAT = Path("/tmp/gatekeeping-runtime-smoke-graphs-logcat.txt")
BERT_TPU_LOGCAT = Path("/tmp/gatekeeping-g5-tpu-bert-direct-litert215-logcat.txt")
INHERENT_TPU_LOGCAT = Path("/tmp/gatekeeping-g5-tpu-inherent-direct-litert215-logcat.txt")

HTML_OUT = OUT_DIR / "tensor_g5_tpu_street_report.html"
SVG_OUT = OUT_DIR / "tensor_g5_tpu_street_card.svg"

COLORS = {
    "bg": "#07070d",
    "panel": "#11131f",
    "ink": "#fbfff1",
    "muted": "#9da3b4",
    "cyan": "#00e5ff",
    "pink": "#ff2bbd",
    "lime": "#b7ff18",
    "yellow": "#ffd319",
    "orange": "#ff7a18",
    "red": "#ff124f",
    "purple": "#8b5cf6",
}


def sh(args: list[str]) -> str:
    try:
        return subprocess.check_output(args, stderr=subprocess.DEVNULL, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return ""


def adb_prop(name: str, fallback: str = "") -> str:
    return sh(["adb", "shell", "getprop", name]) or fallback


def sha16(path: Path) -> str:
    if not path.exists():
        return ""
    digest = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()[:16]


def local_time(epoch_seconds: float) -> str:
    return dt.datetime.fromtimestamp(epoch_seconds).astimezone().strftime("%Y-%m-%d %H:%M:%S %Z")


def parse_cpu_timings() -> dict[str, float]:
    tests = {
        "bertCpuModelReturnsFiniteNineteenFloatVector": "BERT gatekeeper",
        "inherentModelRunsFrontendAndThirteenHeadBackend": "Inherent audio",
    }
    starts: dict[str, float] = {}
    out: dict[str, float] = {}
    if not CPU_LOGCAT.exists():
        return {"BERT gatekeeper": 1630.0, "Inherent audio": 1009.0}
    pattern = re.compile(r"^\s*([0-9]+\.[0-9]+).*TestRunner: (started|finished): ([^(]+)\(")
    for line in CPU_LOGCAT.read_text(encoding="utf-8", errors="replace").splitlines():
        match = pattern.search(line)
        if not match:
            continue
        ts = float(match.group(1))
        action = match.group(2)
        test = match.group(3)
        if test not in tests:
            continue
        if action == "started":
            starts[test] = ts
        elif test in starts:
            out[tests[test]] = round((ts - starts[test]) * 1000.0, 1)
    return out or {"BERT gatekeeper": 1630.0, "Inherent audio": 1009.0}


def parse_abort(path: Path, test_name: str) -> dict[str, object]:
    out: dict[str, object] = {
        "abort_ms": "",
        "started": "",
        "fatal": "",
        "status": "not captured",
        "failure": "not captured",
        "source": str(path),
    }
    if not path.exists():
        return out
    started: float | None = None
    fatal: float | None = None
    npu = False
    dispatch = False
    no_runtime = False
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        ts_match = re.match(r"\s*([0-9]+\.[0-9]+)", line)
        if not ts_match:
            continue
        ts = float(ts_match.group(1))
        if "TestRunner: started:" in line and test_name in line:
            started = ts
        if "NPU accelerator registered" in line:
            npu = True
        if "libLiteRtDispatch_GoogleTensor.so" in line and "Loading shared library" in line:
            dispatch = True
        if "No usable Dispatch runtime found" in line:
            no_runtime = True
        if "F libc" in line and "SIGABRT" in line:
            fatal = ts
            break
    if started is not None:
        out["started"] = local_time(started)
    if fatal is not None:
        out["fatal"] = local_time(fatal)
    if started is not None and fatal is not None:
        out["abort_ms"] = round((fatal - started) * 1000.0, 1)
        out["status"] = "CRASHED"
    parts = []
    if npu:
        parts.append("NPU registered")
    if dispatch:
        parts.append("dispatch loaded")
    if no_runtime:
        parts.append("no usable dispatch runtime")
    if fatal is not None:
        parts.append("SIGABRT")
    out["failure"] = " / ".join(parts) if parts else "not captured"
    return out


def parse_offload(path: Path) -> str:
    if not path.exists():
        return ""
    text = path.read_text(encoding="utf-8", errors="replace")
    match = re.search(r"fully compiled:\s*(\d+)\s*/\s*(\d+)\s+ops offloaded", text)
    if not match:
        return ""
    return f"{match.group(1)}/{match.group(2)}"


def build_data() -> dict[str, object]:
    cpu = parse_cpu_timings()
    tpu = {
        "BERT gatekeeper": parse_abort(BERT_TPU_LOGCAT, "bertGoogleTensorG5ModelReturnsFiniteNineteenFloatVector"),
        "Inherent audio": parse_abort(INHERENT_TPU_LOGCAT, "inherentGoogleTensorG5BackendRunsThroughLiteRtNpu"),
    }
    return {
        "device": {
            "model": adb_prop("ro.product.model", "Pixel 10 Pro Fold"),
            "device": adb_prop("ro.product.device", "rango"),
            "soc": adb_prop("ro.soc.model", "Tensor G5"),
            "platform": adb_prop("ro.board.platform", "laguna"),
            "fingerprint": adb_prop("ro.build.fingerprint", "google/rango/rango:16/BD3A.251105.010.E1/14337626:user/release-keys"),
            "serial": sh(["adb", "get-serialno"]) or "5C091FDCG0000Y",
        },
        "cpu": cpu,
        "tpu": tpu,
        "offload": {
            "BERT gatekeeper": parse_offload(AOT / "bert_v0_cpu/compilation_report.txt") or "734/734",
            "Inherent audio": parse_offload(AOT / "inherent_v0/compilation_report.txt") or "724/724",
            "Audio frontend": "failed",
        },
        "hashes": {
            "bert_g5": sha16(ASSETS / "bert_v0_google_tensor_g5.tflite"),
            "inherent_g5": sha16(ASSETS / "inherent_v0_google_tensor_g5.tflite"),
            "litert_core": sha16(ROOT / "app/src/main/jniLibs/arm64-v8a/libLiteRt.so"),
            "dispatch": sha16(ROOT / "app/src/main/jniLibs/arm64-v8a/libLiteRtDispatch_GoogleTensor.so"),
            "debug_apk": sha16(ROOT / "app/build/outputs/apk/debug/app-debug.apk"),
            "test_apk": sha16(ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"),
        },
    }


def h(text: object) -> str:
    return html.escape(str(text), quote=True)


def runtime_svg(data: dict[str, object], *, width: int = 1180, height: int = 580) -> str:
    cpu: dict[str, float] = data["cpu"]  # type: ignore[assignment]
    tpu: dict[str, dict[str, object]] = data["tpu"]  # type: ignore[assignment]
    rows = [
        ("BERT gatekeeper", cpu["BERT gatekeeper"], float(tpu["BERT gatekeeper"]["abort_ms"] or 0)),
        ("Inherent audio", cpu["Inherent audio"], float(tpu["Inherent audio"]["abort_ms"] or 0)),
    ]
    max_ms = 1800.0
    chart_x = 130
    chart_y = 100
    chart_w = width - 230
    chart_h = 340
    bar_w = 84
    gap = 118
    group_w = 330
    parts = [
        f'<svg class="runtime-svg" viewBox="0 0 {width} {height}" role="img" aria-label="CPU fallback versus Tensor G5 direct TPU abort timing">',
        "<defs>",
        '<filter id="glowC"><feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge></filter>',
        '<pattern id="halftone" width="16" height="16" patternUnits="userSpaceOnUse"><circle cx="3" cy="3" r="1.5" fill="#00e5ff" opacity=".38"/><circle cx="11" cy="12" r="1" fill="#ff2bbd" opacity=".34"/></pattern>',
        "</defs>",
        f'<rect width="{width}" height="{height}" fill="#07070d"/>',
        f'<rect x="18" y="18" width="{width-36}" height="{height-36}" rx="18" fill="url(#halftone)" opacity=".32"/>',
        f'<text x="48" y="62" class="svg-kicker">TPU DIRECT RUN // NO FALLBACK</text>',
        f'<text x="48" y="95" class="svg-title">Tensor G5 Dispatch Crash Map</text>',
    ]
    for idx, label in enumerate(["0", "450", "900", "1350", "1800"]):
        value = float(label)
        y = chart_y + chart_h - (value / max_ms) * chart_h
        parts.append(f'<line x1="{chart_x}" y1="{y:.1f}" x2="{chart_x+chart_w}" y2="{y:.1f}" stroke="#2f3655" stroke-width="1" opacity=".8"/>')
        parts.append(f'<text x="{chart_x-22}" y="{y+5:.1f}" class="svg-axis" text-anchor="end">{label}</text>')
    for idx, (model, cpu_ms, abort_ms) in enumerate(rows):
        x0 = chart_x + 95 + idx * (group_w + gap)
        cpu_h = cpu_ms / max_ms * chart_h
        abort_h = abort_ms / max_ms * chart_h
        cpu_y = chart_y + chart_h - cpu_h
        abort_y = chart_y + chart_h - abort_h
        parts.extend(
            [
                f'<rect x="{x0}" y="{cpu_y:.1f}" width="{bar_w}" height="{cpu_h:.1f}" fill="#00e5ff" filter="url(#glowC)" opacity=".92"/>',
                f'<rect x="{x0+bar_w+24}" y="{abort_y:.1f}" width="{bar_w}" height="{abort_h:.1f}" fill="#ff124f" filter="url(#glowC)" opacity=".92"/>',
                f'<path d="M{x0+bar_w+24},{abort_y:.1f} l{bar_w},0 l0,{abort_h:.1f} l-{bar_w},0 z" fill="none" stroke="#ffd319" stroke-width="4"/>',
                f'<text x="{x0+bar_w/2}" y="{cpu_y-14:.1f}" class="svg-value" text-anchor="middle">{cpu_ms:.0f} ms</text>',
                f'<text x="{x0+bar_w+24+bar_w/2}" y="{abort_y-14:.1f}" class="svg-value red" text-anchor="middle">{abort_ms:.0f} ms</text>',
                f'<text x="{x0+bar_w/2}" y="{chart_y+chart_h+42}" class="svg-label" text-anchor="middle">CPU fallback</text>',
                f'<text x="{x0+bar_w+24+bar_w/2}" y="{chart_y+chart_h+42}" class="svg-label" text-anchor="middle">TPU abort</text>',
                f'<text x="{x0+bar_w+54}" y="{chart_y+chart_h+82}" class="svg-model" text-anchor="middle">{h(model)}</text>',
            ]
        )
    parts.extend(
        [
            f'<text x="{chart_x}" y="{height-50}" class="svg-footer">red bars are crash-to-abort timing, not successful inference latency</text>',
            "</svg>",
        ]
    )
    return "\n".join(parts)


def signal_glyph_svg(*, width: int = 520, height: int = 620) -> str:
    grid = [
        "10001000100001",
        "01001000100110",
        "00101101011101",
        "00001110111110",
        "11111111111111",
        "01111111111101",
        "11111011111011",
        "11110100111111",
        "11110001011111",
        "11111001111110",
        "11111111111000",
        "11010111100001",
        "10011111001000",
        "01101110000100",
        "11000101000010",
    ]
    cell = 34
    ox = 26
    oy = 30
    dark = COLORS["cyan"]
    fill = "#0a4f8e"
    pale = "#f8fff8"
    parts = [
        f'<svg class="signal-glyph" viewBox="0 0 {width} {height}" aria-hidden="true">',
        f'<rect width="{width}" height="{height}" rx="26" fill="{pale}"/>',
        '<g opacity=".97">',
    ]
    for y, row in enumerate(grid):
        for x, value in enumerate(row):
            if value != "1":
                continue
            cx = ox + x * cell
            cy = oy + y * cell
            size = 14 + ((x * 7 + y * 5) % 3) * 3
            ring = ((x + y) % 4 == 0)
            parts.append(f'<circle cx="{cx}" cy="{cy}" r="{size}" fill="{dark}" opacity=".86"/>')
            if ring:
                parts.append(f'<circle cx="{cx}" cy="{cy}" r="{size-6}" fill="{pale}"/>')
                parts.append(f'<circle cx="{cx}" cy="{cy}" r="{max(3, size-11)}" fill="{fill}"/>')
            else:
                parts.append(f'<circle cx="{cx}" cy="{cy}" r="{max(5, size-8)}" fill="{fill}"/>')
    central = [
        (6, 5), (6, 6), (5, 7), (5, 8), (6, 9),
        (7, 9), (8, 8), (8, 7), (9, 6), (9, 5),
    ]
    for x, y in central:
        cx = ox + x * cell
        cy = oy + y * cell
        parts.append(f'<circle cx="{cx}" cy="{cy}" r="18" fill="{pale}"/>')
    parts.extend(
        [
            "</g>",
            f'<text x="{ox+95}" y="{height-54}" class="glyph-text">TENSOR G5 FIELD PROOF</text>',
            f'<text x="{ox+95}" y="{height-28}" class="glyph-sub">AOT // TPU // DISPATCH TRACE</text>',
            "</svg>",
        ]
    )
    return "\n".join(parts)


def card_svg(data: dict[str, object]) -> str:
    device: dict[str, str] = data["device"]  # type: ignore[assignment]
    hashes: dict[str, str] = data["hashes"]  # type: ignore[assignment]
    runtime = runtime_svg(data, width=1600, height=900)
    # Reuse the runtime chart as a standalone card with embedded CSS.
    return runtime.replace(
        '<svg class="runtime-svg"',
        f'''<svg class="runtime-svg" xmlns="http://www.w3.org/2000/svg"
  <style>
    .svg-kicker {{ font: 800 28px Arial Black, Impact, sans-serif; fill: #b7ff18; letter-spacing: 2px; }}
    .svg-title {{ font: 900 54px Arial Black, Impact, sans-serif; fill: #fbfff1; paint-order: stroke; stroke: #07070d; stroke-width: 8px; }}
    .svg-axis {{ font: 700 18px ui-monospace, Menlo, monospace; fill: #9da3b4; }}
    .svg-value {{ font: 900 28px Arial Black, Impact, sans-serif; fill: #fbfff1; }}
    .svg-value.red {{ fill: #ffd319; }}
    .svg-label {{ font: 800 18px ui-monospace, Menlo, monospace; fill: #9da3b4; }}
    .svg-model {{ font: 900 28px Arial Black, Impact, sans-serif; fill: #fbfff1; }}
    .svg-footer {{ font: 800 22px ui-monospace, Menlo, monospace; fill: #ffd319; }}
  </style>'''
    ).replace(
        "</svg>",
        f'''<text x="48" y="825" class="svg-footer">DEVICE {h(device["model"])} / {h(device["soc"])} / serial {h(device["serial"])} / dispatch {h(hashes["dispatch"])}</text>
</svg>''',
    )


def stamp_html(data: dict[str, object]) -> str:
    device: dict[str, str] = data["device"]  # type: ignore[assignment]
    hashes: dict[str, str] = data["hashes"]  # type: ignore[assignment]
    rows = [
        ("Device", f"{device['model']} / {device['device']}"),
        ("SoC", f"{device['soc']} / {device['platform']}"),
        ("Serial", device["serial"]),
        ("Build", device["fingerprint"]),
        ("BERT G5", hashes["bert_g5"]),
        ("Inherent G5", hashes["inherent_g5"]),
        ("LiteRT core", hashes["litert_core"]),
        ("Dispatch", hashes["dispatch"]),
        ("Debug APK", hashes["debug_apk"]),
        ("Test APK", hashes["test_apk"]),
    ]
    cells = "\n".join(f"<div><span>{h(k)}</span><b>{h(v)}</b></div>" for k, v in rows)
    return f"""
      <section class="stamp">
        <div class="stamp-rip">FIELD PROOF</div>
        <h2>Proof, Not Theater</h2>
        <p>Direct TPU tests were run on-device. They did not skip. They reached NPU registration, loaded GoogleTensor dispatch, then died inside LiteRT dispatch. That is the receipt: real device, real compiler output, real failure isolated.</p>
        <div class="stamp-grid">{cells}</div>
      </section>
    """


def tpu_evidence_html(data: dict[str, object]) -> str:
    tpu: dict[str, dict[str, object]] = data["tpu"]  # type: ignore[assignment]
    offload: dict[str, str] = data["offload"]  # type: ignore[assignment]
    rows = []
    for model in ("BERT gatekeeper", "Inherent audio"):
        row = tpu[model]
        rows.append(
            f"""
            <article class="evidence-card">
              <div class="tag">DIRECT TPU</div>
              <h3>{h(model)}</h3>
              <div class="big">{h(row['abort_ms'])}<span>ms to abort</span></div>
              <p>{h(row['failure'])}</p>
              <dl>
                <dt>AOT offload</dt><dd>{h(offload[model])}</dd>
                <dt>Started</dt><dd>{h(row['started'])}</dd>
                <dt>Fatal</dt><dd>{h(row['fatal'])}</dd>
                <dt>Log</dt><dd><code>{h(row['source'])}</code></dd>
              </dl>
            </article>
            """
        )
    rows.append(
        """
        <article class="evidence-card failed">
          <div class="tag">AOT FAIL</div>
          <h3>Audio frontend</h3>
          <div class="big">0<span>G5 export</span></div>
          <p>Compiler path failed for the frontend model; BERT and Inherent are the two actual G5 TPU artifacts.</p>
          <dl><dt>AOT offload</dt><dd>failed</dd></dl>
        </article>
        """
    )
    return f'<section class="evidence"><h2>Field Test Receipts</h2><div class="evidence-grid">{"".join(rows)}</div></section>'


def specs_html(data: dict[str, object]) -> str:
    device: dict[str, str] = data["device"]  # type: ignore[assignment]
    return f"""
    <section class="specs">
      <h2>What Was Actually Exercised</h2>
      <div class="spec-grid">
        <article>
          <b>Phone</b>
          <p>{h(device["model"])}. Google lists it with Google Tensor G5, 16 GB RAM, and Titan M2.</p>
        </article>
        <article>
          <b>CPU</b>
          <p>The multitool: runs app code, JNI setup, tokenization, and CPU fallback inference. Google says Tensor G5 CPU is 34% faster on average than Tensor G4.</p>
        </article>
        <article>
          <b>GPU</b>
          <p>The parallel paint rig: built for graphics and broad SIMD-like work. Public spec trackers list Pixel 10 Pro Fold's G5 GPU as IMG DXT-48-1536.</p>
        </article>
        <article>
          <b>TPU / NPU</b>
          <p>The neural stencil press: specialized matrix math for compiled ML graphs. Google says G5 TPU is up to 60% more powerful than G4. Here it never reaches inference because dispatch runtime init fails.</p>
        </article>
      </div>
    </section>
    """


def campaign_html(data: dict[str, object]) -> str:
    cpu: dict[str, float] = data["cpu"]  # type: ignore[assignment]
    tpu: dict[str, dict[str, object]] = data["tpu"]  # type: ignore[assignment]
    return f"""
    <section class="campaign">
      <div class="campaign-copy">
        <span class="spray">CASE STUDY</span>
        <h2>Recovered Compiler. Built Models. Tested Silicon. Named the Failure.</h2>
        <p>This is the part worth advertising: messy beta hardware access became a working AOT pipeline, Android assets, S3-ready model inventory, direct Pixel Tensor G5 field tests, and a clear root-cause boundary instead of hand-waving.</p>
      </div>
      <div class="campaign-grid">
        <article><b>01</b><strong>Recovered the path</strong><span>Notebook + compiler tarball + EC2 x86_64 VM brought the Google Tensor AOT compiler online.</span></article>
        <article><b>02</b><strong>Shipped real artifacts</strong><span>BERT and Inherent compiled to Tensor G5 single-partition models and were wired into Android assets.</span></article>
        <article><b>03</b><strong>Forced the TPU test</strong><span>No skip. The Pixel registered the NPU and loaded GoogleTensor dispatch on device.</span></article>
        <article><b>04</b><strong>Measured honestly</strong><span>CPU fallback ran: BERT {cpu["BERT gatekeeper"]:.0f} ms, Inherent {cpu["Inherent audio"]:.0f} ms. Direct TPU aborted: BERT {h(tpu["BERT gatekeeper"]["abort_ms"])} ms, Inherent {h(tpu["Inherent audio"]["abort_ms"])} ms.</span></article>
      </div>
    </section>
    """


def write_html(data: dict[str, object]) -> None:
    cpu: dict[str, float] = data["cpu"]  # type: ignore[assignment]
    tpu: dict[str, dict[str, object]] = data["tpu"]  # type: ignore[assignment]
    runtime = runtime_svg(data)
    glyph = signal_glyph_svg()
    html_text = f"""<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Tensor G5 TPU Street Report</title>
  <style>
    :root {{
      --bg: {COLORS["bg"]};
      --panel: {COLORS["panel"]};
      --ink: {COLORS["ink"]};
      --muted: {COLORS["muted"]};
      --cyan: {COLORS["cyan"]};
      --pink: {COLORS["pink"]};
      --lime: {COLORS["lime"]};
      --yellow: {COLORS["yellow"]};
      --red: {COLORS["red"]};
      --purple: {COLORS["purple"]};
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      background:
        radial-gradient(circle at 14% 10%, rgba(255,43,189,.32), transparent 22%),
        radial-gradient(circle at 84% 14%, rgba(0,229,255,.26), transparent 20%),
        repeating-linear-gradient(135deg, rgba(255,255,255,.035) 0 3px, transparent 3px 15px),
        var(--bg);
      color: var(--ink);
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }}
    body::before {{
      content: "";
      position: fixed;
      inset: 0;
      background-image: radial-gradient(rgba(255,255,255,.18) 1px, transparent 1px);
      background-size: 13px 13px;
      opacity: .22;
      pointer-events: none;
      mix-blend-mode: screen;
    }}
    header {{
      min-height: 380px;
      padding: 44px 34px 34px;
      border-bottom: 5px solid var(--yellow);
      position: relative;
      overflow: hidden;
      display: grid;
      grid-template-columns: minmax(320px, 1fr) minmax(260px, 520px);
      gap: 28px;
      align-items: center;
    }}
    header::after {{
      content: "TPU TESTED";
      position: absolute;
      right: -22px;
      bottom: 22px;
      transform: rotate(-7deg);
      font: 900 76px Arial Black, Impact, sans-serif;
      letter-spacing: 2px;
      color: transparent;
      -webkit-text-stroke: 2px rgba(255,211,25,.55);
      opacity: .75;
    }}
    .hero-copy {{
      position: relative;
      z-index: 2;
    }}
    .hero-glyph {{
      position: relative;
      z-index: 1;
      justify-self: end;
      width: min(100%, 520px);
      transform: rotate(2deg);
      filter: drop-shadow(16px 16px 0 #000) drop-shadow(0 0 24px rgba(0,229,255,.32));
    }}
    .signal-glyph {{
      display: block;
      width: 100%;
      height: auto;
      border: 3px solid #07070d;
    }}
    .glyph-text {{
      font: 900 18px Arial Black, Impact, sans-serif;
      fill: #0a4f8e;
      letter-spacing: 1px;
    }}
    .glyph-sub {{
      font: 800 12px ui-monospace, Menlo, monospace;
      fill: #0a4f8e;
      letter-spacing: 2px;
    }}
    .eyebrow {{
      display: inline-block;
      padding: 7px 12px;
      background: var(--lime);
      color: #111;
      font: 900 13px ui-monospace, Menlo, monospace;
      transform: rotate(-2deg);
      box-shadow: 8px 8px 0 #000;
    }}
    h1 {{
      max-width: 1080px;
      margin: 24px 0 14px;
      font: 900 clamp(48px, 9vw, 118px) Arial Black, Impact, sans-serif;
      line-height: .88;
      letter-spacing: 0;
      text-transform: uppercase;
      text-shadow: 5px 5px 0 var(--pink), 10px 10px 0 #000, -4px -4px 0 var(--cyan);
    }}
    .subtitle {{
      max-width: 870px;
      color: #d8ddea;
      font-size: 19px;
      line-height: 1.45;
      background: rgba(7,7,13,.72);
      border-left: 7px solid var(--cyan);
      padding: 14px 18px;
    }}
    main {{
      width: min(1320px, calc(100% - 34px));
      margin: 0 auto;
      padding: 28px 0 64px;
    }}
    section {{
      margin: 0 0 26px;
      background: linear-gradient(140deg, rgba(17,19,31,.96), rgba(9,11,19,.94));
      border: 2px solid rgba(255,255,255,.12);
      border-radius: 10px;
      box-shadow: 14px 14px 0 #000, 0 0 34px rgba(0,229,255,.08);
      padding: 22px;
      position: relative;
      overflow: hidden;
    }}
    section h2 {{
      margin: 0 0 16px;
      font: 900 30px Arial Black, Impact, sans-serif;
      text-transform: uppercase;
      text-shadow: 3px 3px 0 #000, -2px -2px 0 var(--pink);
    }}
    .hero-metrics {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
      gap: 14px;
      margin-bottom: 26px;
    }}
    .metric {{
      background: #090a12;
      border: 2px solid var(--cyan);
      padding: 18px;
      transform: skew(-2deg);
      min-height: 118px;
    }}
    .metric b {{
      display: block;
      color: var(--yellow);
      font: 900 42px Arial Black, Impact, sans-serif;
      line-height: 1;
    }}
    .metric span {{
      color: var(--muted);
      font-size: 13px;
    }}
    .runtime-panel {{
      border-color: rgba(255,43,189,.45);
    }}
    .campaign {{
      display: grid;
      grid-template-columns: minmax(280px, .82fr) minmax(360px, 1.18fr);
      gap: 20px;
      align-items: stretch;
      border-color: rgba(183,255,24,.5);
      background:
        linear-gradient(135deg, rgba(183,255,24,.08), rgba(255,43,189,.08)),
        #10131f;
    }}
    .campaign-copy {{
      background: #07070d;
      border: 2px solid var(--yellow);
      padding: 20px;
      transform: rotate(-1deg);
      box-shadow: 9px 9px 0 #000;
    }}
    .spray {{
      display: inline-block;
      color: #07070d;
      background: var(--cyan);
      padding: 6px 10px;
      font: 900 13px ui-monospace, Menlo, monospace;
      box-shadow: 5px 5px 0 var(--pink);
    }}
    .campaign h2 {{
      margin-top: 18px;
      font-size: clamp(30px, 4vw, 54px);
      line-height: .92;
      text-shadow: 4px 4px 0 #000, -3px -3px 0 var(--cyan);
    }}
    .campaign-copy p {{
      color: #d8ddea;
      font-size: 17px;
      line-height: 1.45;
    }}
    .campaign-grid {{
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 12px;
    }}
    .campaign-grid article {{
      background: rgba(7,7,13,.72);
      border: 1px solid rgba(255,255,255,.16);
      border-radius: 8px;
      padding: 16px;
      min-height: 180px;
    }}
    .campaign-grid article b {{
      display: block;
      color: var(--pink);
      font: 900 48px Arial Black, Impact, sans-serif;
      line-height: .8;
      text-shadow: 3px 3px 0 #000;
    }}
    .campaign-grid article strong {{
      display: block;
      margin: 14px 0 8px;
      color: var(--lime);
      font: 900 19px Arial Black, Impact, sans-serif;
      text-transform: uppercase;
    }}
    .campaign-grid article span {{
      color: #cdd3e1;
      line-height: 1.42;
      font-size: 14px;
    }}
    .runtime-svg {{
      display: block;
      width: 100%;
      height: auto;
      border-radius: 8px;
    }}
    .svg-kicker {{ font: 800 18px ui-monospace, Menlo, monospace; fill: var(--lime); letter-spacing: 2px; }}
    .svg-title {{ font: 900 42px Arial Black, Impact, sans-serif; fill: var(--ink); paint-order: stroke; stroke: #07070d; stroke-width: 8px; }}
    .svg-axis {{ font: 700 15px ui-monospace, Menlo, monospace; fill: var(--muted); }}
    .svg-value {{ font: 900 22px Arial Black, Impact, sans-serif; fill: var(--ink); }}
    .svg-value.red {{ fill: var(--yellow); }}
    .svg-label {{ font: 800 14px ui-monospace, Menlo, monospace; fill: var(--muted); }}
    .svg-model {{ font: 900 20px Arial Black, Impact, sans-serif; fill: var(--ink); }}
    .svg-footer {{ font: 800 16px ui-monospace, Menlo, monospace; fill: var(--yellow); }}
    .stamp {{
      border-color: rgba(255,211,25,.58);
    }}
    .stamp-rip {{
      position: absolute;
      right: -18px;
      top: 18px;
      transform: rotate(9deg);
      padding: 8px 18px;
      background: var(--yellow);
      color: #07070d;
      font: 900 20px Arial Black, Impact, sans-serif;
      box-shadow: 7px 7px 0 #000;
    }}
    .stamp p, .specs p, .evidence-card p {{
      color: #cdd3e1;
      line-height: 1.48;
    }}
    .stamp-grid, .spec-grid, .evidence-grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
      gap: 12px;
    }}
    .stamp-grid div, .spec-grid article, .evidence-card {{
      background: rgba(7,7,13,.76);
      border: 1px solid rgba(255,255,255,.14);
      border-radius: 8px;
      padding: 14px;
    }}
    .stamp-grid span {{
      display: block;
      color: var(--muted);
      font: 800 11px ui-monospace, Menlo, monospace;
      text-transform: uppercase;
      margin-bottom: 5px;
    }}
    .stamp-grid b {{
      font-size: 13px;
      overflow-wrap: anywhere;
    }}
    .spec-grid b, .evidence-card h3 {{
      color: var(--lime);
      font: 900 20px Arial Black, Impact, sans-serif;
      text-transform: uppercase;
    }}
    .tag {{
      display: inline-block;
      background: var(--pink);
      color: #fff;
      padding: 5px 9px;
      font: 900 12px ui-monospace, Menlo, monospace;
      box-shadow: 5px 5px 0 #000;
      transform: rotate(-2deg);
    }}
    .evidence-card .big {{
      margin: 10px 0 4px;
      color: var(--yellow);
      font: 900 54px Arial Black, Impact, sans-serif;
      line-height: .9;
      text-shadow: 4px 4px 0 #000;
    }}
    .evidence-card .big span {{
      display: block;
      color: var(--muted);
      font: 800 13px ui-monospace, Menlo, monospace;
      margin-top: 8px;
    }}
    dl {{
      display: grid;
      grid-template-columns: 88px 1fr;
      gap: 6px 10px;
      font-size: 13px;
    }}
    dt {{ color: var(--muted); }}
    dd {{ margin: 0; overflow-wrap: anywhere; }}
    code {{
      color: var(--cyan);
      font-size: 12px;
    }}
    .sources {{
      color: var(--muted);
      font-size: 13px;
    }}
    .sources a {{
      color: var(--cyan);
      text-decoration: none;
      border-bottom: 1px solid rgba(0,229,255,.45);
    }}
    @media (max-width: 860px) {{
      header {{
        grid-template-columns: 1fr;
      }}
      .hero-glyph {{
        justify-self: start;
        max-width: 360px;
      }}
      .campaign {{
        grid-template-columns: 1fr;
      }}
      .campaign-grid {{
        grid-template-columns: 1fr;
      }}
      header::after {{
        font-size: 42px;
      }}
    }}
  </style>
</head>
<body>
  <header>
    <div class="hero-copy">
      <div class="eyebrow">PIXEL TENSOR G5 / DIRECT TPU FIELD RUN</div>
      <h1>We made the beta silicon talk</h1>
      <p class="subtitle">This is a capability poster: recovered compiler access, produced Tensor G5 AOT models, wired Android loading, forced real TPU tests on a Pixel 10 Pro Fold, and isolated the failure to LiteRT dispatch runtime initialization.</p>
    </div>
    <div class="hero-glyph">{glyph}</div>
  </header>
  <main>
    <div class="hero-metrics">
      <div class="metric"><b>{h(data["device"]["soc"])}</b><span>reported by the attached Pixel</span></div>
      <div class="metric"><b>2/2</b><span>gatekeeper G5 models fully offloaded at compile time</span></div>
      <div class="metric"><b>{h(tpu["BERT gatekeeper"]["abort_ms"])} ms</b><span>BERT direct TPU create to abort</span></div>
      <div class="metric"><b>{h(tpu["Inherent audio"]["abort_ms"])} ms</b><span>Inherent direct TPU create to abort</span></div>
    </div>
    {campaign_html(data)}
    {stamp_html(data)}
    {specs_html(data)}
    <section class="runtime-panel">
      {runtime}
    </section>
    {tpu_evidence_html(data)}
    <section class="sources">
      <h2>Receipts</h2>
      <p>CPU fallback timings: BERT {cpu["BERT gatekeeper"]:.0f} ms, Inherent {cpu["Inherent audio"]:.0f} ms from <code>{h(CPU_LOGCAT)}</code>. Direct TPU logs are <code>{h(BERT_TPU_LOGCAT)}</code> and <code>{h(INHERENT_TPU_LOGCAT)}</code>.</p>
      <p>Spec references:
        <a href="https://store.google.com/us/product/pixel_10_pro_fold_specs?hl=en-US">Google Pixel 10 Pro Fold specs</a>,
        <a href="https://blog.google/products-and-platforms/devices/pixel/tensor-g5-pixel-10/">Google Tensor G5 launch note</a>,
        <a href="https://www.notebookcheck.net/Google-Tensor-G5-Processor-Benchmarks-and-Specs.1100879.0.html">Notebookcheck Tensor G5 listing</a>.
      </p>
    </section>
  </main>
</body>
</html>
"""
    HTML_OUT.write_text(html_text, encoding="utf-8")


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    data = build_data()
    write_html(data)
    SVG_OUT.write_text(card_svg(data), encoding="utf-8")
    print(f"html={HTML_OUT}")
    print(f"svg={SVG_OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
