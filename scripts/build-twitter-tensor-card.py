#!/usr/bin/env python3
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "artifacts/google-tensor-aot"
OUT = OUT_DIR / "tensor_g5_twitter_card_1600x900.png"
OUT_V2 = OUT_DIR / "tensor_g5_twitter_card_metrics_1600x900.png"

W, H = 1600, 900

PINK = "#ff2cab"
DEEP_PINK = "#bc006f"
WHITE = "#fffaf2"
BLACK = "#08070d"
CYAN = "#22e3ff"
BLUE = "#004b8d"
YELLOW = "#fff000"
LIME = "#b8ff18"
CANDY_GREEN = "#39ff14"
PURPLE = "#6a2bff"

FONT_ARIAL_BOLD = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
FONT_DIN = "/System/Library/Fonts/Supplemental/DIN Condensed Bold.ttf"
FONT_MONO = "/System/Library/Fonts/Menlo.ttc"


def font(path: str, size: int, index: int = 0) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size=size, index=index)


def text_size(draw: ImageDraw.ImageDraw, text: str, fnt: ImageFont.FreeTypeFont) -> tuple[int, int]:
    box = draw.textbbox((0, 0), text, font=fnt)
    return box[2] - box[0], box[3] - box[1]


def fit_font(draw: ImageDraw.ImageDraw, path: str, text: str, max_w: int, start: int, min_size: int = 32) -> ImageFont.FreeTypeFont:
    for size in range(start, min_size - 1, -4):
        fnt = font(path, size)
        if text_size(draw, text, fnt)[0] <= max_w:
            return fnt
    return font(path, min_size)


def poly(draw: ImageDraw.ImageDraw, pts: list[tuple[int, int]], fill: str, outline: str = BLACK, width: int = 8) -> None:
    draw.polygon(pts, fill=fill)
    draw.line(pts + [pts[0]], fill=outline, width=width, joint="curve")


def slash_panel(x: int, y: int, w: int, h: int, skew: int = 24) -> list[tuple[int, int]]:
    return [(x + skew, y), (x + w, y), (x + w - skew, y + h), (x, y + h)]


def draw_background(draw: ImageDraw.ImageDraw) -> None:
    draw.rectangle((0, 0, W, H), fill=PINK)

    # Cel-shaded cyber-street backdrop: hard shapes, inked rails, halftone.
    poly(draw, [(-80, 146), (542, 32), (492, 160), (-130, 274)], PURPLE, BLACK, 9)
    poly(draw, [(1158, -60), (1690, -10), (1590, 238), (1100, 174)], YELLOW, BLACK, 9)
    poly(draw, [(1048, 650), (1712, 570), (1688, 930), (970, 936)], CYAN, BLACK, 9)

    for i in range(-160, W + 240, 76):
        draw.line((i, H + 30, i + 345, -30), fill=(255, 255, 255, 42), width=4)
        draw.line((i + 18, H + 30, i + 363, -30), fill=(8, 7, 13, 72), width=2)

    for y in range(0, H, 30):
        for x in range(0, W, 30):
            if (x * 5 + y * 3) % 13 < 5:
                r = 3 + ((x + y) % 9)
                draw.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255, 42))

    for x in range(42, 860, 86):
        y = 812 + int(math.sin(x / 47) * 18)
        draw.line((x, y, x + 66, y - 18), fill=BLACK, width=10)
        draw.line((x + 3, y - 3, x + 63, y - 21), fill=CANDY_GREEN, width=5)

    draw.rectangle((12, 12, W - 12, H - 12), outline=BLACK, width=8)
    draw.rectangle((22, 22, W - 22, H - 22), outline=WHITE, width=3)


def draw_robot(draw: ImageDraw.ImageDraw, x: int, y: int, s: float = 1.0) -> None:
    def rect(points, fill=BLACK):
        draw.rectangle(tuple(int(v) for v in points), fill=fill)

    def round_rect(points, radius, fill=BLACK):
        draw.rounded_rectangle(tuple(int(v) for v in points), radius=int(radius), fill=fill)

    # Original block mascot echoing the supplied geometric references.
    round_rect((x + 46 * s, y, x + 298 * s, y + 178 * s), 48 * s, BLACK)
    rect((x + 106 * s, y + 58 * s, x + 242 * s, y + 122 * s), WHITE)
    draw.polygon(
        [
            (x + 118 * s, y + 58 * s),
            (x + 164 * s, y + 58 * s),
            (x + 188 * s, y + 116 * s),
            (x + 211 * s, y + 58 * s),
            (x + 242 * s, y + 58 * s),
            (x + 208 * s, y + 146 * s),
            (x + 144 * s, y + 146 * s),
        ],
        fill=BLACK,
    )
    rect((x + 150 * s, y + 112 * s, x + 206 * s, y + 142 * s), WHITE)
    rect((x - 2 * s, y + 204 * s, x + 348 * s, y + 260 * s), BLACK)
    draw.polygon([(x + 48 * s, y + 260 * s), (x + 132 * s, y + 260 * s), (x + 98 * s, y + 330 * s), (x + 18 * s, y + 330 * s)], fill=BLACK)
    draw.polygon([(x + 216 * s, y + 260 * s), (x + 300 * s, y + 260 * s), (x + 334 * s, y + 330 * s), (x + 252 * s, y + 330 * s)], fill=BLACK)
    rect((x + 136 * s, y + 154 * s, x + 216 * s, y + 194 * s), BLACK)
    rect((x + 154 * s, y + 164 * s, x + 198 * s, y + 180 * s), PINK)


def draw_signal_matrix(draw: ImageDraw.ImageDraw, x0: int, y0: int, scale: float = 1.0) -> None:
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
    ]
    cell = int(24 * scale)
    for y, row in enumerate(grid):
        for x, value in enumerate(row):
            if value != "1":
                continue
            cx = x0 + x * cell
            cy = y0 + y * cell
            r = int((10 + ((x * 7 + y * 3) % 5)) * scale)
            draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=CYAN, outline=BLACK, width=max(2, int(3 * scale)))
            rr = max(3, r - 8)
            draw.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=BLUE)


def draw_label(draw: ImageDraw.ImageDraw, xy: tuple[int, int], text: str, fill: str, bg: str, size: int) -> tuple[int, int]:
    fnt = font(FONT_MONO, size)
    x, y = xy
    tw, th = text_size(draw, text, fnt)
    poly(draw, slash_panel(x, y, tw + 42, th + 24, 14), bg, BLACK, 5)
    draw.text((x + 22, y + 11), text, font=fnt, fill=fill)
    return x + tw + 54, y


def draw_dog_tag(base: Image.Image, xy: tuple[int, int]) -> None:
    tag = Image.new("RGBA", (500, 214), (0, 0, 0, 0))
    d = ImageDraw.Draw(tag, "RGBA")
    d.rounded_rectangle((12, 16, 488, 198), radius=34, fill="#d8dee2", outline=BLACK, width=8)
    d.rounded_rectangle((24, 28, 476, 186), radius=27, outline=WHITE, width=3)
    for x in range(-30, 520, 58):
        d.line((x, 205, x + 120, 8), fill=(255, 255, 255, 58), width=8)
        d.line((x + 18, 205, x + 138, 8), fill=(42, 46, 52, 36), width=5)
    d.ellipse((36, 82, 82, 128), fill=BLACK)
    d.ellipse((47, 93, 71, 117), fill="#9aa3a9")
    d.line((58, 0, 58, 82), fill=BLACK, width=5)
    for cy in range(2, 82, 16):
        d.ellipse((49, cy, 67, cy + 18), outline=WHITE, width=3)

    mono = font(FONT_MONO, 20)
    bold = font(FONT_ARIAL_BOLD, 24)
    d.text((102, 34), "RUN TAG // DEVICE SPEC", font=bold, fill=BLACK)
    lines = [
        "2026-05-21",
        "PIXEL 10 PRO FOLD",
        "TENSOR G5 / RANGO",
        "16GB RAM / TSMC 3NM",
        "+60% TPU CLAIM, +34% CPU",
    ]
    for idx, line in enumerate(lines):
        d.text((104, 70 + idx * 23), line, font=mono, fill=BLACK)

    tag = tag.rotate(-6, expand=True, resample=Image.Resampling.BICUBIC)
    base.alpha_composite(tag, xy)


def draw_metric_row(
    draw: ImageDraw.ImageDraw,
    y: int,
    model: str,
    cpu_ms: int,
    tpu_ms: int,
    scale_max: int = 1700,
) -> None:
    label_font = fit_font(draw, FONT_ARIAL_BOLD, model, 255, 52, 36)
    num_font = font(FONT_ARIAL_BOLD, 58)
    small = font(FONT_MONO, 25)
    x = 82
    bar_x = 382
    max_w = 690
    cpu_w = int(max_w * cpu_ms / scale_max)
    tpu_w = int(max_w * tpu_ms / scale_max)

    poly(draw, slash_panel(x, y + 14, 268, 116, 26), WHITE, CANDY_GREEN, 7)
    draw.text((x + 26, y + 49), model, font=label_font, fill=BLACK)

    draw.text((bar_x, y - 2), "CPU FALLBACK", font=small, fill=WHITE)
    draw.rounded_rectangle((bar_x, y + 32, bar_x + max_w, y + 78), radius=4, fill=BLACK, outline=WHITE, width=6)
    draw.rectangle((bar_x + 6, y + 38, bar_x + cpu_w, y + 72), fill=CYAN)
    draw.text((bar_x + max_w + 34, y + 20), f"{cpu_ms}ms", font=num_font, fill=CYAN)

    draw.text((bar_x, y + 88), "TPU INIT FAILED", font=small, fill=WHITE)
    draw.rounded_rectangle((bar_x, y + 118, bar_x + max_w, y + 164), radius=4, fill=BLACK, outline=WHITE, width=6)
    draw.rectangle((bar_x + 6, y + 124, bar_x + tpu_w, y + 158), fill=CANDY_GREEN)
    draw.text((bar_x + max_w + 34, y + 106), f"{tpu_ms}ms", font=num_font, fill=WHITE)



def make_card() -> Image.Image:
    img = Image.new("RGBA", (W, H), PINK)
    draw = ImageDraw.Draw(img, "RGBA")
    draw_background(draw)
    img = img.filter(ImageFilter.GaussianBlur(0.55))
    draw = ImageDraw.Draw(img, "RGBA")

    # Header.
    draw.text((54, 40), "GOOGLE TENSOR EDGE TPU BETA", font=font(FONT_MONO, 25), fill=WHITE)
    title = fit_font(draw, FONT_DIN, "TENSOR G5 TPU", 960, 150)
    title2 = fit_font(draw, FONT_DIN, "PERF RECEIPTS", 960, 136)
    draw.text((54, 82), "TENSOR G5 TPU", font=title, fill=WHITE, stroke_width=5, stroke_fill=BLACK)
    draw.text((54, 198), "PERF RECEIPTS", font=title2, fill=CANDY_GREEN, stroke_width=5, stroke_fill=BLACK)

    sub = font(FONT_ARIAL_BOLD, 31)
    draw.rectangle((62, 306, 918, 356), fill=BLACK)
    draw.text((82, 315), "AOT compiled. NPU path loaded. LiteRT dispatch failed.", font=sub, fill=WHITE)

    # Keep decorative graphics behind the enlarged metric component.
    draw_signal_matrix(draw, 1160, 122, 0.9)
    draw_robot(draw, 1148, 282, 1.0)

    # Metrics panel: this is the center of the card.
    poly(draw, slash_panel(42, 370, 1500, 508, 36), BLACK, CANDY_GREEN, 10)
    metrics_title = fit_font(draw, FONT_DIN, "VISIBLE PERFORMANCE METRICS", 1338, 124, 84)
    draw.text((84, 394), "VISIBLE PERFORMANCE METRICS", font=metrics_title, fill=CYAN)
    draw.text((72, 382), "VISIBLE PERFORMANCE METRICS", font=metrics_title, fill=WHITE)
    draw.rectangle((82, 496, 1268, 507), fill=CANDY_GREEN)
    draw.text((84, 522), "CYAN = CPU FALLBACK / GREEN = FORCED TPU PATH UNTIL INIT FAILURE", font=font(FONT_MONO, 25), fill=WHITE)
    draw_metric_row(draw, 584, "BERT", 1630, 525)
    draw_metric_row(draw, 708, "INHERENT", 1009, 285)

    draw_dog_tag(img, (1036, 36))

    # Inked glow pass for the poster punch.
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.text((54, 82), "TENSOR G5 TPU", font=title, fill=(255, 255, 255, 90), stroke_width=5, stroke_fill=(255, 255, 255, 40))
    gd.text((54, 198), "PERF RECEIPTS", font=title2, fill=(57, 255, 20, 90), stroke_width=5, stroke_fill=(57, 255, 20, 46))
    glow = glow.filter(ImageFilter.GaussianBlur(5))
    img = Image.alpha_composite(glow, img)
    return img


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    card = make_card().convert("RGB")
    card.save(OUT, quality=96, optimize=True)
    card.save(OUT_V2, quality=96, optimize=True)
    print(OUT)
    print(OUT_V2)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
