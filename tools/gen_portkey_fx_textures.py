"""Textures for the Portkey's world effects.

    python tools/gen_portkey_fx_textures.py

portkey_runes.png  128x128, a 4x4 sheet of angular runes: a stave and its branches, white strokes with a 2 px soft
                   halo. Premultiplied (rgb = alpha), because it is drawn with the additive glow types.
portkey_ring.png   64x64, a glowing ring carrying 12 rune slots, premultiplied, for the bind flash and the landing
                   decal's glow.
portkey_scuff.png  64x64, the flattened, scuffed ground where a group landed: dark umber, densest in the middle,
                   ragged at the edge, with skid streaks thrown outwards. Straight alpha (drawn translucent).
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "fx")
SS = 4  # supersampling for clean strokes

# Rune anchors on a 3 x 5 lattice inside a cell (0..1): a stave down the middle, branches off it.
ANCHORS = [(x / 2.0, y / 4.0) for y in range(5) for x in range(3)]


def rune(rng):
    """A rune as a list of segments: one stave plus two to four branches, all on the lattice (angular, no curves)."""
    segs = []
    stave_x = rng.choice([0.5, 0.5, 0.0, 1.0])
    segs.append(((stave_x, 0.0), (stave_x, 1.0)))
    for _ in range(rng.randint(2, 4)):
        y0 = rng.choice([0.0, 0.25, 0.5, 0.75])
        side = rng.choice([-1, 1]) if stave_x == 0.5 else (1 if stave_x == 0.0 else -1)
        reach = rng.choice([0.5, 1.0]) if stave_x != 0.5 else 0.5
        x1 = stave_x + side * reach
        y1 = y0 + rng.choice([0.25, 0.5, -0.25])
        y1 = min(1.0, max(0.0, y1))
        segs.append(((stave_x, y0), (x1, y1)))
    return segs


def draw_glyph(draw, segs, ox, oy, size, width):
    pad = size * 0.22
    for (x0, y0), (x1, y1) in segs:
        draw.line([(ox + pad + x0 * (size - 2 * pad), oy + pad + y0 * (size - 2 * pad)),
                   (ox + pad + x1 * (size - 2 * pad), oy + pad + y1 * (size - 2 * pad))],
                  fill=255, width=width)


def premultiplied(alpha):
    a = alpha
    return Image.merge("RGBA", [a, a, a, a])


def runes():
    rng = random.Random(1997)
    size = 128 * SS
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    cell = size // 4
    for i in range(16):
        draw_glyph(draw, rune(rng), (i % 4) * cell, (i // 4) * cell, cell, 3 * SS)
    glow = mask.filter(ImageFilter.GaussianBlur(2 * SS))
    combined = Image.eval(Image.blend(glow, mask, 0.5), lambda v: min(255, int(v * 1.6)))
    out = combined.resize((128, 128), Image.LANCZOS)
    premultiplied(out).save(os.path.join(OUT, "portkey_runes.png"))


def ring():
    rng = random.Random(2024)
    size = 64 * SS
    c = size / 2.0
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    r_out, r_in = size * 0.46, size * 0.30
    draw.ellipse([c - r_out, c - r_out, c + r_out, c + r_out], outline=255, width=int(1.5 * SS))
    draw.ellipse([c - r_in, c - r_in, c + r_in, c + r_in], outline=200, width=int(1.0 * SS))
    slot = (r_out - r_in) * 0.8
    for k in range(12):
        ang = k * math.tau / 12.0
        mx, my = c + math.cos(ang) * (r_out + r_in) / 2, c + math.sin(ang) * (r_out + r_in) / 2
        segs = rune(rng)
        cs, sn = math.cos(ang + math.pi / 2), math.sin(ang + math.pi / 2)
        for (x0, y0), (x1, y1) in segs:
            pts = []
            for x, y in ((x0, y0), (x1, y1)):
                lx, ly = (x - 0.5) * slot * 0.6, (y - 0.5) * slot
                pts.append((mx + lx * cs - ly * sn, my + lx * sn + ly * cs))
            draw.line(pts, fill=255, width=int(1.0 * SS))
    glow = mask.filter(ImageFilter.GaussianBlur(1.5 * SS))
    combined = Image.eval(Image.blend(glow, mask, 0.55), lambda v: min(255, int(v * 1.7)))
    premultiplied(combined.resize((64, 64), Image.LANCZOS)).save(os.path.join(OUT, "portkey_ring.png"))


def scuff():
    rng = random.Random(77)
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    c = (size - 1) / 2.0
    # Ragged edge: a radius that wanders round the circle.
    bumps = [rng.uniform(0.82, 1.0) for _ in range(24)]
    streaks = [(rng.uniform(0, math.tau), rng.uniform(0.04, 0.09)) for _ in range(9)]
    for y in range(size):
        for x in range(size):
            dx, dy = (x - c) / c, (y - c) / c
            r = math.hypot(dx, dy)
            ang = math.atan2(dy, dx) % math.tau
            f = ang / math.tau * 24
            i0 = int(f) % 24
            edge = bumps[i0] + (bumps[(i0 + 1) % 24] - bumps[i0]) * (f - int(f))
            a = max(0.0, 1.0 - (r / (edge * 0.78)) ** 2.2) * 0.85
            for sa, sw in streaks:  # skid marks thrown outwards
                d = abs((ang - sa + math.pi) % math.tau - math.pi)
                if d < sw and r < edge:
                    a = max(a, 0.55 * (1.0 - d / sw) * (1.0 - r / edge) ** 0.5)
            a *= rng.uniform(0.75, 1.0)  # grit
            shade = 0.75 + 0.25 * rng.random()
            px[x, y] = (int(46 * shade), int(32 * shade), int(20 * shade), int(255 * min(1.0, a)))
    img.save(os.path.join(OUT, "portkey_scuff.png"))


def main():
    os.makedirs(OUT, exist_ok=True)
    runes()
    ring()
    scuff()
    for n in ("portkey_runes", "portkey_ring", "portkey_scuff"):
        print(os.path.join(OUT, n + ".png"), Image.open(os.path.join(OUT, n + ".png")).size)


if __name__ == "__main__":
    main()
