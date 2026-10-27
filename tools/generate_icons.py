"""Renders the Sudoku icon: the 512x512 Play Store icon and the adaptive launcher foreground for each density.

Usage: python3 tools/generate_icons.py [--play]   (requires Pillow and DejaVu Sans Bold)
--play also rewrites app/src/main/ic_launcher-playstore.png (already uploaded to Play Console).
"""
from pathlib import Path

import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SUPERSAMPLE = 4

CYAN, PINK = (0x00, 0xE5, 0xFF, 255), (0xFF, 0x2B, 0xD6, 255)
INDIGO, BLACK = (0x2A, 0x2F, 0x8F, 255), (0x0B, 0x0D, 0x1A, 255)
DIGITS = [(0, 0, "5"), (0, 1, "3"), (1, 0, "7"), (1, 2, "1"), (2, 1, "4"), (2, 2, "9")]

# Adaptive icons are 108dp with a 66dp circular safe zone; a grid spanning 41% of the
# canvas keeps its corners inside that circle and matches the Play icon's proportions.
PLAY_GRID_MARGIN = 0.19
LAUNCHER_GRID_MARGIN = 0.295
DENSITIES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def gradient(size, c1, c2):
    vertical = Image.linear_gradient("L").resize((size, size))
    ramp = ImageChops.add(vertical, vertical.rotate(90), scale=2.0)
    return Image.composite(Image.new("RGB", (size, size), c2), Image.new("RGB", (size, size), c1), ramp)


def render(size, margin):
    s = size * SUPERSAMPLE
    g0, g1 = int(s * margin), s - int(s * margin)
    cell = (g1 - g0) / 3
    thick = int((g1 - g0) * 0.0355)
    font = ImageFont.truetype(FONT, int(cell * 0.6))

    def box(r, c):
        return g0 + c * cell, g0 + r * cell, g0 + (c + 1) * cell, g0 + (r + 1) * cell

    def layer(bg, line, center_fill, center_line, digit_col, center_digit_col, glow):
        img = bg.convert("RGBA")
        lines = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        d = ImageDraw.Draw(lines)
        x0, y0, x1, y1 = box(1, 1)
        pad = cell * 0.08
        d.rounded_rectangle((x0 + pad, y0 + pad, x1 - pad, y1 - pad), radius=int(cell * 0.16),
                            fill=center_fill, outline=center_line, width=max(1, int(thick * 0.41)))
        d.rounded_rectangle((g0, g0, g1, g1), radius=int((g1 - g0) * 0.097), outline=line, width=thick)
        for i in (1, 2):
            p = g0 + i * cell
            d.line((p, g0 + thick / 2, p, g1 - thick / 2), fill=line, width=int(thick * 0.55))
            d.line((g0 + thick / 2, p, g1 - thick / 2, p), fill=line, width=int(thick * 0.55))
        for r, c, t in DIGITS + [(1, 1, "8")]:
            bx0, by0, bx1, by1 = box(r, c)
            d.text(((bx0 + bx1) / 2, (by0 + by1) / 2 + cell * 0.02), t, font=font,
                   fill=center_digit_col if t == "8" else digit_col, anchor="mm")
        if glow:
            halo = lines.filter(ImageFilter.GaussianBlur((g1 - g0) * 0.029))
            halo.putalpha(halo.split()[3].point(lambda v: int(v * glow)))
            img = Image.alpha_composite(Image.alpha_composite(img, halo), halo)
        return Image.alpha_composite(img, lines)

    dark = layer(gradient(s, (0x1C, 0x1D, 0x2A), (0x0D, 0x0E, 0x16)), CYAN, (0xFF, 0x2B, 0xD6, 40), PINK,
                 CYAN, (255, 255, 255, 255), glow=0.9)
    light = layer(gradient(s, (0xFF, 0xFF, 0xFD), (0xEC, 0xEC, 0xE8)), BLACK, (0xDC, 0xE3, 0xFF, 255), INDIGO,
                  INDIGO, INDIGO, glow=None)
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).polygon([(s, 0), (s, s), (0, s)], fill=255)
    icon = Image.composite(light, dark, mask)
    seam = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(seam).line((s, 0, 0, s), fill=(0xFF, 0x2B, 0xD6, 200), width=int(s * 0.006))
    icon = Image.alpha_composite(Image.alpha_composite(icon, seam.filter(ImageFilter.GaussianBlur(s * 0.01))), seam)
    return icon.convert("RGB").resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    if "--play" in sys.argv:
        render(512, PLAY_GRID_MARGIN).save(ROOT / "app/src/main/ic_launcher-playstore.png", optimize=True)
    for density, px in DENSITIES.items():
        out = RES / f"mipmap-{density}" / "ic_launcher_foreground.webp"
        out.parent.mkdir(parents=True, exist_ok=True)
        render(px, LAUNCHER_GRID_MARGIN).save(out, lossless=True, method=6)
        print(out.relative_to(ROOT), px)
