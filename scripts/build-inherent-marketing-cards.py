#!/usr/bin/env python3
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path("/Users/mac/neural")
INHERENT = Path("/Users/mac/inherent")
RUN_DIR = ROOT / "artifacts/google-tensor-aot/g5-benchmark-20260521-182225"

W, H = 1600, 900

INK = "#080914"
PAPER = "#fff8df"
CREAM = "#fff0bc"
WHITE = "#fffaf4"
RED = "#fa0900"
PINK = "#ff00a8"
PURPLE = "#87328f"
YELLOW = "#ffe90f"
LIME = "#c6ff57"
HIT = "#60ff7f"
BLUE = "#2957ff"
SKY = "#63b5ef"

FONT_ARIAL_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_DIN = "/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf"
FONT_MONO = "/System/Library/Fonts/Menlo.ttc"

BG_DOJO = INHERENT / "docs/browser-demo/assets/dojo-clean-background.webp"
HEADER = INHERENT / "docs/inherent-header.webp"
PARAPPA = INHERENT / "docs/browser-demo/assets/dojo-parappa-spritesheet.webp"
ONION = INHERENT / "docs/browser-demo/assets/dojo-onion-spritesheet.webp"

TEXT = {
    "name": "TEXT / BERT-19",
    "subtitle": "16 transformer layers / 19 intent scores",
    "input": '"define polymorphism"',
    "middle": "BERT-19",
    "output": "define_term + search",
    "heads": "13 action heads + 6 screen signals",
    "cpu": 414.110,
    "edge": 7.018,
    "speed": 59.01,
}

AUDIO = {
    "name": "AUDIO / CONFORMER-13",
    "subtitle": "Inherent audio gate / 6 layers",
    "input": '"set a timer"',
    "middle": "Conformer",
    "output": "add_timer slot",
    "heads": "interest gate + 12 action heads",
    "cpu": 512.455,
    "edge": 20.038,
    "speed": 25.57,
}


def font(path: str, size: int, index: int = 0) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size=size, index=index)


def size(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.FreeTypeFont) -> tuple[int, int]:
    box = draw.textbbox((0, 0), text, font=fnt, stroke_width=0)
    return box[2] - box[0], box[3] - box[1]


def fit(draw: ImageDraw.ImageDraw, path: str, text: str, max_w: int, start: int, minimum: int = 18) -> ImageFont.FreeTypeFont:
    for point in range(start, minimum - 1, -2):
        fnt = font(path, point)
        if size(draw, text, fnt)[0] <= max_w:
            return fnt
    return font(path, minimum)


def txt(
    draw: ImageDraw.ImageDraw,
    xy: tuple[int, int],
    text: str,
    fnt: ImageFont.FreeTypeFont,
    fill: str = INK,
    stroke: str | None = None,
    sw: int = 0,
    anchor: str | None = None,
) -> None:
    draw.text(xy, text, font=fnt, fill=fill, stroke_width=sw, stroke_fill=stroke or fill, anchor=anchor)


def poly(draw: ImageDraw.ImageDraw, pts: list[tuple[int, int]], fill: str, outline: str = INK, width: int = 8) -> None:
    draw.polygon(pts, fill=fill)
    draw.line(pts + [pts[0]], fill=outline, width=width, joint="curve")


def slash(x: int, y: int, w: int, h: int, skew: int = 24) -> list[tuple[int, int]]:
    return [(x + skew, y), (x + w, y), (x + w - skew, y + h), (x, y + h)]


def rounded(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], fill: str, outline: str = INK, width: int = 8, radius: int = 22) -> None:
    x1, y1, x2, y2 = box
    draw.rounded_rectangle((x1 + 12, y1 + 12, x2 + 12, y2 + 12), radius=radius, fill=INK)
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def cover(path: Path, target: tuple[int, int], crop_top: float = 0.5) -> Image.Image:
    im = Image.open(path).convert("RGBA")
    tw, th = target
    scale = max(tw / im.width, th / im.height)
    nw, nh = int(im.width * scale), int(im.height * scale)
    im = im.resize((nw, nh), Image.Resampling.LANCZOS)
    left = (nw - tw) // 2
    top = int((nh - th) * crop_top)
    return im.crop((left, top, left + tw, top + th))


def paste_fit(base: Image.Image, path: Path, box: tuple[int, int, int, int], crop_top: float = 0.5, opacity: float = 1.0) -> None:
    x1, y1, x2, y2 = box
    im = cover(path, (x2 - x1, y2 - y1), crop_top)
    if opacity < 1:
        alpha = im.getchannel("A").point(lambda v: int(v * opacity))
        im.putalpha(alpha)
    base.alpha_composite(im, (x1, y1))


def record_pattern(size_xy: tuple[int, int], dense: bool = False) -> Image.Image:
    bg = Image.new("RGBA", size_xy, INK)
    d = ImageDraw.Draw(bg, "RGBA")
    swatches = [YELLOW, PINK, LIME, SKY, RED, PURPLE]
    step = 112 if dense else 150
    for y in range(-40, size_xy[1] + step, step):
        for x in range(-40, size_xy[0] + step, step):
            color = swatches[((x // step) + (y // step)) % len(swatches)]
            d.rectangle((x, y, x + 56, y + 56), fill=color)
            cx, cy = x + 28, y + 28
            for r, outline in [(34, WHITE), (25, "#bdbdbd"), (16, color)]:
                d.ellipse((cx - r, cy - r, cx + r, cy + r), outline=outline, width=3)
            d.ellipse((cx - 7, cy - 7, cx + 7, cy + 7), fill=INK)
    return bg.filter(ImageFilter.GaussianBlur(0.65))


def draw_logo(base: Image.Image, box: tuple[int, int, int, int], rotate: float = -4) -> None:
    x1, y1, x2, y2 = box
    logo = cover(HEADER, (x2 - x1, y2 - y1), 0.08)
    logo = logo.rotate(rotate, expand=True, resample=Image.Resampling.BICUBIC)
    base.alpha_composite(logo, (x1, y1))


def draw_sprite(base: Image.Image, which: str, box: tuple[int, int, int, int], opacity: float = 0.72) -> None:
    sheet = Image.open(PARAPPA if which == "parappa" else ONION).convert("RGBA")
    if which == "parappa":
        crop = sheet.crop((0, 0, 420, 520))
    else:
        crop = sheet.crop((0, 0, 390, 520))
    crop = crop.resize((box[2] - box[0], box[3] - box[1]), Image.Resampling.LANCZOS)
    alpha = crop.getchannel("A").point(lambda v: int(v * opacity))
    crop.putalpha(alpha)
    base.alpha_composite(crop, (box[0], box[1]))


def run_tag(draw: ImageDraw.ImageDraw, xy: tuple[int, int], w: int = 382, h: int = 158, label: str = "RUN TAG") -> None:
    x, y = xy
    rounded(draw, (x, y, x + w, y + h), PAPER, INK, 7, 24)
    txt(draw, (x + 24, y + 22), label, font(FONT_ARIAL_BOLD, 24), INK)
    mono = font(FONT_MONO, 16)
    lines = [
        "2026-05-21  18:22 CEST",
        "PIXEL 10 PRO FOLD / TENSOR G5",
        "RANGO / 16GB RAM / TSMC 3NM",
        "20 ITERATIONS / MEDIAN",
        "DISPATCH + TACHYON + DARWINN",
    ]
    for i, line in enumerate(lines):
        txt(draw, (x + 24, y + 60 + i * 20), line, mono, INK)


def action_dots(draw: ImageDraw.ImageDraw, x: int, y: int, count: int, first_color: str, caption: str) -> None:
    for i in range(count):
        color = first_color if i < (13 if count == 19 else 1) else SKY
        cx = x + i * 21
        draw.ellipse((cx, y, cx + 14, y + 14), fill=color, outline=INK, width=2)
    txt(draw, (x, y + 28), caption, font(FONT_MONO, 17), "#6b543d")


def tiny_dots(draw: ImageDraw.ImageDraw, x: int, y: int, count: int, first_color: str) -> None:
    for i in range(count):
        color = first_color if i < (13 if count == 19 else 1) else SKY
        cx = x + i * 13
        draw.ellipse((cx, y, cx + 8, y + 8), fill=color, outline=INK, width=1)


def arrow(draw: ImageDraw.ImageDraw, x: int, y: int, w: int = 38, color: str = LIME) -> None:
    draw.rectangle((x, y + 14, x + w - 18, y + 36), fill=color, outline=INK, width=2)
    draw.polygon([(x + w - 18, y + 4), (x + w + 8, y + 25), (x + w - 18, y + 47)], fill=color, outline=INK)


def flow_box(draw: ImageDraw.ImageDraw, x: int, y: int, w: int, text: str, bg: str, fnt: ImageFont.FreeTypeFont | None = None) -> None:
    fnt = fnt or fit(draw, FONT_ARIAL_BOLD, text, w - 24, 22, 14)
    poly(draw, slash(x, y, w, 50, 14), bg, INK, 4)
    txt(draw, (x + w // 2, y + 15), text, fnt, INK if bg != INK else WHITE, anchor="ma")


def flow_row(draw: ImageDraw.ImageDraw, x: int, y: int, model: dict[str, object], widths: tuple[int, int, int]) -> None:
    w1, w2, w3 = widths
    flow_box(draw, x, y, w1, str(model["input"]), CREAM)
    arrow(draw, x + w1 + 12, y)
    flow_box(draw, x + w1 + 60, y, w2, str(model["middle"]), LIME)
    arrow(draw, x + w1 + w2 + 74, y)
    flow_box(draw, x + w1 + w2 + 122, y, w3, str(model["output"]), INK)


def mini_flow(draw: ImageDraw.ImageDraw, x: int, y: int, input_text: str, middle: str, output: str, widths: tuple[int, int, int]) -> None:
    w1, w2, w3 = widths
    flow_box(draw, x, y, w1, input_text, CREAM)
    arrow(draw, x + w1 + 12, y)
    flow_box(draw, x + w1 + 60, y, w2, middle, LIME)
    arrow(draw, x + w1 + w2 + 74, y)
    flow_box(draw, x + w1 + w2 + 122, y, w3, output, INK)


def latency_bars(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    w: int,
    model: dict[str, object],
    wave: bool = False,
    label_size: int = 17,
) -> None:
    cpu = float(model["cpu"])
    edge = float(model["edge"])
    max_ms = 530.0
    cpu_w = int((w - 12) * cpu / max_ms)
    edge_w = max(20, int((w - 12) * edge / max_ms))
    mono = font(FONT_MONO, label_size)
    num = font(FONT_ARIAL_BOLD, 28)

    txt(draw, (x, y), "CPU FALLBACK", mono, INK)
    draw.rounded_rectangle((x, y + 26, x + w, y + 58), radius=5, fill=WHITE, outline=INK, width=4)
    draw.rectangle((x + 6, y + 32, x + 6 + cpu_w, y + 52), fill=SKY)
    if wave:
        pts = []
        for i in range(0, cpu_w, 18):
            pts.append((x + 10 + i, y + 42 + int(math.sin(i / 18) * 8)))
        if len(pts) > 1:
            draw.line(pts, fill=WHITE, width=3)
    txt(draw, (x + w, y + 0), f"{cpu:.2f}ms", num, INK, anchor="ra")

    txt(draw, (x, y + 76), "EDGE TPU", mono, INK)
    draw.rounded_rectangle((x, y + 102, x + w, y + 134), radius=5, fill=WHITE, outline=INK, width=4)
    draw.rectangle((x + 6, y + 108, x + 6 + edge_w, y + 128), fill=HIT)
    if wave:
        pts = []
        for i in range(0, max(edge_w, 45), 8):
            pts.append((x + 10 + i, y + 118 + int(math.sin(i / 7) * 5)))
        if len(pts) > 1:
            draw.line(pts, fill=INK, width=2)
    txt(draw, (x + w, y + 76), f"{edge:.2f}ms", num, INK, anchor="ra")


def model_panel(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], model: dict[str, object], accent: str, wave: bool = False) -> None:
    x1, y1, x2, y2 = box
    rounded(draw, box, PAPER, INK, 7, 18)
    title_f = fit(draw, FONT_ARIAL_BOLD, str(model["name"]), (x2 - x1) - 210, 40, 28)
    txt(draw, (x1 + 28, y1 + 32), str(model["name"]), title_f, INK)
    txt(draw, (x1 + 30, y1 + 76), str(model["subtitle"]), font(FONT_MONO, 18), PURPLE)
    poly(draw, slash(x2 - 186, y1 + 18, 150, 72, 12), LIME, INK, 5)
    txt(draw, (x2 - 110, y1 + 30), f"{float(model['speed']):.1f}x", font(FONT_ARIAL_BOLD, 48), INK, anchor="ma")
    flow_row(draw, x1 + 30, y1 + 112, model, (190, 130, 190))
    action_dots(draw, x1 + 30, y1 + 208, 19 if model is TEXT else 13, accent, str(model["heads"]))
    txt(draw, (x1 + 30, y1 + 286), "MEDIAN LATENCY", font(FONT_ARIAL_BOLD, 24), INK)
    latency_bars(draw, x1 + 30, y1 + 324, (x2 - x1) - 60, model, wave)


def bottom_receipt(draw: ImageDraw.ImageDraw, y: int, text: str, w: int = 900) -> None:
    rounded(draw, ((W - w) // 2, y, (W + w) // 2, y + 64), CREAM, INK, 6, 18)
    txt(draw, (W // 2, y + 20), text, font(FONT_ARIAL_BOLD, 20), INK, anchor="ma")


def make_wild() -> Image.Image:
    base = cover(BG_DOJO, (W, H), 0.45).filter(ImageFilter.GaussianBlur(0.95))
    noise = record_pattern((W, H), True)
    base = Image.blend(base, noise, 0.34).convert("RGBA")
    draw = ImageDraw.Draw(base, "RGBA")
    draw_sprite(base, "parappa", (-34, 520, 204, 910), 0.44)
    draw_sprite(base, "onion", (1398, 505, 1584, 864), 0.50)
    draw.rectangle((0, 0, W, 22), fill=RED)
    draw.rectangle((0, H - 22, W, H), fill=YELLOW)

    for y, color, skew in [(158, LIME, -48), (214, SKY, 36), (270, PINK, -24)]:
        poly(draw, [(0, y), (W, y + skew), (W, y + skew + 34), (0, y + 34)], color, INK, 4)
    for x in range(-80, W + 120, 190):
        draw.line((x, 828, x + 240, 110), fill=(255, 250, 244, 64), width=4)
        draw.line((x + 24, 830, x + 264, 112), fill=(8, 9, 20, 88), width=3)

    draw_logo(base, (54, 18, 238, 214), -7)
    poly(draw, slash(280, 42, 850, 104, 26), PAPER, INK, 8)
    txt(draw, (314, 54), "EDGE TPU BENCHMARK", fit(draw, FONT_DIN, "EDGE TPU BENCHMARK", 760, 82, 64), INK)
    txt(draw, (318, 150), "Pixel 10 Pro Fold / Tensor G5", font(FONT_ARIAL_BOLD, 38), LIME, INK, 4)
    run_tag(draw, (1160, 38), 392, 162)

    scoreboard(base, (84, 260, 748, 536), TEXT, LIME, -2)
    scoreboard(base, (844, 268, 1512, 544), AUDIO, SKY, 2)

    rounded(draw, (98, 570, 1502, 802), PAPER, INK, 7, 20)
    poly(draw, slash(128, 592, 246, 48, 18), LIME, INK, 5)
    txt(draw, (158, 606), "WHAT RAN", font(FONT_MONO, 18), INK)
    poly(draw, slash(472, 592, 300, 48, 18), SKY, INK, 5)
    txt(draw, (504, 606), "WHAT IT DECIDED", font(FONT_MONO, 18), INK)
    poly(draw, slash(888, 592, 300, 48, 18), LIME, INK, 5)
    txt(draw, (920, 606), "WHY AUDIO IS SLOWER", font(FONT_MONO, 16), INK)
    mini_flow(draw, 130, 654, '"define polymorphism"', "BERT-19", "search", (190, 112, 92))
    mini_flow(draw, 130, 720, '"set a timer"', "Conformer", "timer", (128, 126, 82))
    txt(draw, (1060, 662), "audio uses a 3000x128 mel window", font(FONT_ARIAL_BOLD, 22), INK)
    txt(draw, (1060, 696), "~1.5MB input vs ~6KB text tokens", font(FONT_ARIAL_BOLD, 22), PURPLE)
    txt(draw, (1060, 734), "19 text scores", font(FONT_MONO, 16), "#6b543d")
    tiny_dots(draw, 1220, 740, 19, HIT)
    txt(draw, (1060, 764), "13 audio heads", font(FONT_MONO, 16), "#6b543d")
    tiny_dots(draw, 1220, 770, 13, PINK)
    bottom_receipt(draw, 824, "Passing Edge TPU benchmark: Google Dispatch + sb_pixel + Tachyon + Darwinn.", 860)
    return base.convert("RGB")


def vocab_strip(draw: ImageDraw.ImageDraw, x: int, y: int) -> None:
    labels = [("LIST", LIME), ("DOC", SKY), ("TMR", RED), ("CALL", PURPLE), ("WEB", PINK), ("CAM", YELLOW)]
    for i, (label, color) in enumerate(labels):
        bx = x + i * 72
        draw.rectangle((bx, y, bx + 56, y + 56), fill=color, outline=INK, width=5)
        txt(draw, (bx + 28, y + 18), label, font(FONT_ARIAL_BOLD, 18), WHITE if color in (RED, PURPLE, PINK) else INK, INK, 2, anchor="ma")
    txt(draw, (x + 208, y + 78), "SHARED ACTION VOCABULARY", font(FONT_MONO, 18), INK, anchor="ma")


def compact_panel(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int], model: dict[str, object], wave: bool = False) -> None:
    x1, y1, x2, y2 = box
    rounded(draw, box, CREAM, INK, 7, 18)
    title = "TEXT CLASSIFIER" if model is TEXT else "AUDIO CLASSIFIER"
    txt(draw, (x1 + 26, y1 + 34), title, fit(draw, FONT_ARIAL_BOLD, title, (x2 - x1) - 62, 34, 28), INK)
    txt(draw, (x1 + 26, y1 + 72), str(model["input"]), font(FONT_MONO, 18), INK)
    txt(draw, (x1 + 26, y1 + 104), str(model["subtitle"]), font(FONT_MONO, 18), PURPLE)
    poly(draw, slash(x1 + 26, y1 + 142, 166, 68, 10), LIME, INK, 5)
    txt(draw, (x1 + 110, y1 + 153), f"{float(model['speed']):.1f}x", font(FONT_ARIAL_BOLD, 48), INK, anchor="ma")
    txt(draw, (x1 + 26, y1 + 230), "MEDIAN LATENCY", font(FONT_ARIAL_BOLD, 24), INK)
    latency_bars(draw, x1 + 26, y1 + 272, (x2 - x1) - 52, model, wave)


def make_crazy() -> Image.Image:
    base = Image.new("RGBA", (W, H), INK)
    base.alpha_composite(record_pattern((W, H), True))
    draw = ImageDraw.Draw(base, "RGBA")
    draw.rectangle((0, 0, W, H), outline=INK, width=12)
    draw_sprite(base, "parappa", (-20, 536, 180, 902), 0.56)
    draw_sprite(base, "onion", (1422, 504, 1580, 820), 0.62)
    draw_logo(base, (702, 24, 842, 166), -2)
    txt(draw, (52, 58), "BENCHMARK RESULT", fit(draw, FONT_DIN, "BENCHMARK RESULT", 640, 76, 56), WHITE, INK, 5)
    txt(draw, (62, 136), "TENSOR G5 EDGE TPU / PIXEL 10 PRO FOLD", fit(draw, FONT_ARIAL_BOLD, "TENSOR G5 EDGE TPU / PIXEL 10 PRO FOLD", 650, 34, 26), LIME, INK, 4)
    run_tag(draw, (1164, 42), 390, 170)
    rounded(draw, (74, 232, 1540, 810), PAPER, INK, 7, 24)
    poly(draw, slash(104, 250, 520, 52, 18), INK, INK, 4)
    txt(draw, (132, 266), "TEXT -> BERT-19 -> 13 ACTIONS + 6 SIGNALS", font(FONT_MONO, 18), WHITE)
    poly(draw, slash(658, 250, 610, 52, 18), LIME, INK, 5)
    txt(draw, (690, 266), "AUDIO -> CONFORMER-13 -> INTEREST + 12 ACTIONS", font(FONT_MONO, 18), INK)
    vocab_strip(draw, 604, 342)
    compact_panel(draw, (112, 338, 560, 740), TEXT, False)
    compact_panel(draw, (1050, 338, 1500, 740), AUDIO, True)
    mini_flow(draw, 594, 520, '"define polymorphism"', "BERT-19", "search", (136, 92, 74))
    mini_flow(draw, 594, 604, '"set a timer"', "Conformer", "timer", (118, 104, 68))
    action_dots(draw, 608, 706, 17, HIT, "routing heads")
    bottom_receipt(draw, 820, "One-pass routing: text and audio hit the same on-device action vocabulary.", 830)
    return base.convert("RGB")


def scoreboard(base: Image.Image, box: tuple[int, int, int, int], model: dict[str, object], fill: str, rotate: float = -2) -> None:
    x1, y1, x2, y2 = box
    layer = Image.new("RGBA", (x2 - x1 + 80, y2 - y1 + 80), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer, "RGBA")
    poly(d, slash(38, 24, x2 - x1, y2 - y1, 18), fill, INK, 8)
    title = "TEXT / BERT-19" if model is TEXT else "AUDIO / CONFORMER-13"
    txt(d, (72, 58), title, fit(d, FONT_ARIAL_BOLD, title, (x2 - x1) - 80, 40, 30), INK)
    txt(d, (72, 148), f"{float(model['speed']):.1f}x", font(FONT_ARIAL_BOLD, 92), WHITE, INK, 5)

    bar_x = 310
    bar_w = (x2 - x1) - 365
    max_ms = 530.0
    cpu_w = int(bar_w * float(model["cpu"]) / max_ms)
    edge_w = max(16, int(bar_w * float(model["edge"]) / max_ms))
    label = font(FONT_MONO, 15)
    value = font(FONT_ARIAL_BOLD, 22)
    for yy, name, ms, width, color in [
        (142, "CPU", float(model["cpu"]), cpu_w, SKY),
        (204, "EDGE TPU", float(model["edge"]), edge_w, HIT),
    ]:
        txt(d, (bar_x, yy - 22), name, label, INK)
        d.rounded_rectangle((bar_x, yy, bar_x + bar_w, yy + 26), radius=4, fill=WHITE, outline=INK, width=4)
        d.rectangle((bar_x + 5, yy + 6, bar_x + 5 + width, yy + 20), fill=color)
        if model is AUDIO and name == "CPU":
            pts = []
            for i in range(0, width, 12):
                pts.append((bar_x + 8 + i, yy + 13 + int(math.sin(i / 16) * 5)))
            if len(pts) > 1:
                d.line(pts, fill=WHITE, width=2)
        txt(d, (bar_x + bar_w, yy - 24), f"{ms:.2f}ms", value, INK, anchor="ra")

    layer = layer.rotate(rotate, expand=True, resample=Image.Resampling.BICUBIC)
    base.alpha_composite(layer, (x1, y1))


def make_wtf() -> Image.Image:
    base = Image.new("RGBA", (W, H), INK)
    base.alpha_composite(record_pattern((W, H), True))
    draw = ImageDraw.Draw(base, "RGBA")
    poly(draw, slash(80, 36, 1040, 144, 18), PAPER, INK, 8)
    txt(draw, (108, 72), "EDGE TPU RESULT SCREEN", fit(draw, FONT_DIN, "EDGE TPU RESULT SCREEN", 780, 72, 58), INK)
    txt(draw, (112, 148), "PIXEL 10 PRO FOLD / TENSOR G5", font(FONT_ARIAL_BOLD, 32), PURPLE)
    run_tag(draw, (1160, 42), 390, 160)
    scoreboard(base, (82, 242, 720, 520), TEXT, LIME, -2)
    scoreboard(base, (850, 248, 1518, 532), AUDIO, SKY, 3)
    rounded(draw, (286, 570, 1320, 798), PAPER, INK, 7, 12)
    txt(draw, (310, 602), "WHAT IT DID IN ONE PASS", font(FONT_ARIAL_BOLD, 36), INK)
    mini_flow(draw, 326, 648, '"define polymorphism"', "BERT-19", "search", (160, 110, 82))
    mini_flow(draw, 828, 648, '"set a timer"', "Conformer", "timer", (128, 120, 82))
    action_dots(draw, 328, 742, 19, HIT, "19 text scores")
    action_dots(draw, 856, 742, 13, PINK, "13 audio heads")
    bottom_receipt(draw, 824, "Proof-backed passing run: Google Dispatch + sb_pixel + Tachyon + Darwinn observed.", 840)
    draw_sprite(base, "parappa", (-8, 532, 172, 900), 0.48)
    draw_sprite(base, "onion", (1402, 516, 1570, 852), 0.60)
    return base.convert("RGB")


def save_with_preview(name: str, image: Image.Image) -> None:
    out = RUN_DIR / f"tensor-g5-inherent-marketing-{name}-1600x900.png"
    preview = RUN_DIR / f"tensor-g5-inherent-marketing-{name}-1600x900-preview-1200x675.png"
    image.save(out, quality=96, optimize=True)
    image.resize((1200, 675), Image.Resampling.LANCZOS).save(preview, quality=94, optimize=True)
    print(out)
    print(preview)


def main() -> int:
    RUN_DIR.mkdir(parents=True, exist_ok=True)
    save_with_preview("wild", make_wild())
    save_with_preview("crazy", make_crazy())
    save_with_preview("wtf", make_wtf())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
