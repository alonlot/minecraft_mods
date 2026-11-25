#!/usr/bin/env python
"""Sea cave texture candidates (Task 13, step 1).

Writes two candidates (A and B) for every sea-cave texture to
texture_proposals/sea_cave/<name>_<A|B>.png and a comparison sheet to
texture_proposals/sea_cave_texture_sheet.png.

Nothing is copied into the mod unless you ask for it:

    python build_scripts/generate_sea_cave_textures.py                 # candidates + sheet
    python build_scripts/generate_sea_cave_textures.py --list          # show apply keys
    python build_scripts/generate_sea_cave_textures.py --apply blood_arch=A,basin_liquid=B,skiff=A
    python build_scripts/generate_sea_cave_textures.py --apply all=A   # every item, variant A

Every sprite is hand-placed: 16x16 character grids with a limited palette, or small
explicit geometry (circle/line lists) drawn pixel by pixel. Vanilla blackstone and
deepslate are read from the client jar as the base the carvings sit on.
"""
import argparse
import json
import math
import random
import shutil
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/hogwartsmagic/textures"
OUT = ROOT / "texture_proposals/sea_cave"
SHEET = ROOT / "texture_proposals/sea_cave_texture_sheet.png"
JARS = [
    Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.21/client-extra.jar",
    Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.21/client.jar",
]

_jar = None


def vanilla(name):
    global _jar
    if _jar is None:
        for j in JARS:
            if j.exists():
                _jar = zipfile.ZipFile(j)
                break
        if _jar is None:
            sys.exit("client jar not found: " + str(JARS))
    import io
    return Image.open(io.BytesIO(_jar.read("assets/minecraft/textures/block/%s.png" % name))).convert("RGBA")


def rgb(h):
    return (h >> 16 & 255, h >> 8 & 255, h & 255, 255)


def grid(rows, pal):
    assert len(rows) == 16, len(rows)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        assert len(r) == 16, (y, r, len(r))
        for x, c in enumerate(r):
            if c != ".":
                im.putpixel((x, y), pal[c])
    return im


def over(base, top):
    b = base.copy()
    b.alpha_composite(top)
    return b


def tile_n(img, n, m=None):
    m = m or n
    w, h = img.size
    out = Image.new("RGBA", (w * n, h * m), (0, 0, 0, 0))
    for j in range(m):
        for i in range(n):
            out.paste(img, (i * w, j * h))
    return out


def line(draw, x0, y0, x1, y1, c):
    """Bresenham on a pixel canvas (draw = dict-like putter)."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        draw(x0, y0, c)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def blotch(rng, ramp, cell=4, jitter=0.3, weights=None):
    n = 16 // cell
    g = [[rng.random() for _ in range(n)] for _ in range(n)]
    im = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            v = g[y // cell][x // cell] * (1 - jitter) + rng.random() * jitter
            idx = min(len(ramp) - 1, int(v * len(ramp)))
            im.putpixel((x, y), ramp[idx])
    return im


# --------------------------------------------------------------------------------------
# 1. BLOOD ARCH  (3 wide x 3 tall seal, 9 tile pieces per stage)
# --------------------------------------------------------------------------------------
RED_LINE = (104, 14, 18, 255)
RED_LINE_LIT = (132, 18, 22, 255)
SM1 = (178, 20, 24, 255)
SM2 = (128, 12, 18, 255)
SM3 = (224, 66, 58, 255)
CHAR = (22, 8, 10, 255)
EMBER = (168, 30, 14, 255)


def arch_base():
    bs = vanilla("blackstone")
    return tile_n(bs, 3)


class Canvas:
    def __init__(self, im):
        self.im = im

    def put(self, x, y, c):
        if 0 <= x < self.im.width and 0 <= y < self.im.height:
            self.im.putpixel((x, y), c)


def arc_pixels(r, width=0.5):
    pts = []
    for y in range(24):
        for x in range(48):
            d = math.hypot(x + .5 - 24, y + .5 - 24)
            if abs(d - r) <= width:
                pts.append((x, y))
    return pts


def polar(r, deg_from_top, side):
    a = math.radians(deg_from_top)
    return (round(24 + side * r * math.sin(a) - .5), round(24 - r * math.cos(a) - .5))


def draw_arch_outline(cv, var):
    P = cv.put
    for (x, y) in arc_pixels(19.5):
        P(x, y, RED_LINE_LIT if (x + y) < 30 else RED_LINE)
    for y in range(24, 48):
        P(4, y, RED_LINE_LIT)
        P(43, y, RED_LINE)
    if var == "A":
        # keystone wedge + impost ticks: reads as one carved voussoir at the crown
        for side in (-1, 1):
            a = polar(19.5, 7, side)
            b = polar(14.5, 6, side)
            line(P, a[0], a[1], b[0], b[1], RED_LINE)
        l = polar(14.5, 6, -1)
        r = polar(14.5, 6, 1)
        line(P, l[0], l[1], r[0], r[1], RED_LINE)
        for x in (5, 6, 7):
            P(x, 24, RED_LINE)
            P(47 - x, 24, RED_LINE)
    else:
        # twin rings with voussoir joints: stone-cut arch ring, courses on the jambs
        for (x, y) in arc_pixels(15.5):
            P(x, y, RED_LINE)
        for y in range(24, 48):
            P(8, y, RED_LINE)
            P(39, y, RED_LINE_LIT)
        for ang in (4, 22, 40, 58, 76):
            for side in (-1, 1):
                a = polar(19.5, ang, side)
                b = polar(15.5, ang, side)
                line(P, a[0], a[1], b[0], b[1], RED_LINE)
        for y in (24, 31, 38, 45):
            for x in (5, 6, 7):
                P(x, y, RED_LINE)
                P(47 - x, y, RED_LINE)


def draw_arch_smear(cv, var):
    P = cv.put
    if var == "A":
        # fresh blood running down out of the arch line
        for x, ln in ((13, 5), (18, 3), (22, 9), (27, 4), (31, 8), (36, 6), (41, 3)):
            y0 = 24
            for y in range(24):
                if abs(math.hypot(x + .5 - 24, y + .5 - 24) - 19.5) <= 0.5:
                    y0 = y
                    break
            if x <= 4 or x >= 43:
                continue
            for i in range(1, ln + 1):
                P(x, y0 + i, SM1 if i < ln else SM3)
                if i < ln - 1:
                    P(x - 1, y0 + i, SM2) if i % 2 else None
            P(x + 1, y0 + ln, SM2)
        for (x, y) in arc_pixels(19.5):
            if 11 <= x <= 36:
                P(x, y, SM2)
    else:
        # one hand-wide wipe across the seal, dry-brush at the tail
        pts = []
        for i in range(0, 34):
            x = 9 + i
            y = 14 + int(i * 0.8 + 3 * math.sin(i / 5.0))
            pts.append((x, y))
        for i, (x, y) in enumerate(pts):
            th = 1 if i < 5 or i > 29 else 2
            for t in range(-th, th + 1):
                dry = i > 24 and (t + i) % 3 == 0
                if dry:
                    continue
                c = SM1 if abs(t) < th else SM2
                if t == -th + 1 and i % 4 < 2:
                    c = SM3
                P(x, y + t, c)
        for x, y0, ln in ((14, 22, 6), (19, 25, 8), (26, 33, 5)):
            for k in range(ln):
                P(x, y0 + k, SM2 if k < ln - 1 else SM1)


def draw_arch_dissolve(cv, var):
    im = cv.im
    if var == "A":
        holes = [(24, 31, 7), (14, 40, 4), (34, 37, 5), (24, 14, 3), (10, 28, 3), (38, 24, 3), (24, 43, 3)]
        rim = EMBER
    else:
        holes = [(24, 29, 8), (24, 21, 5), (18, 35, 5), (30, 35, 5), (24, 40, 4),
                 (11, 12, 2), (38, 14, 2), (8, 40, 3), (40, 42, 2), (14, 22, 2)]
        rim = CHAR
    mask = [[False] * 48 for _ in range(48)]
    for k, (cx, cy, r) in enumerate(holes):
        for y in range(48):
            for x in range(48):
                dx, dy = x + .5 - cx, y + .5 - cy
                a = math.atan2(dy, dx)
                re = r * (1 + 0.28 * math.sin(3 * a + k) + 0.16 * math.sin(5 * a + 2 * k))
                if dx * dx + dy * dy <= re * re:
                    mask[y][x] = True
    for y in range(48):
        for x in range(48):
            if mask[y][x]:
                im.putpixel((x, y), (0, 0, 0, 0))
    edge = []
    for y in range(48):
        for x in range(48):
            if mask[y][x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 48 and 0 <= ny < 48 and mask[ny][nx]:
                    edge.append((x, y))
                    break
    for (x, y) in edge:
        im.putpixel((x, y), rim)
    if var == "B":
        for (x, y) in edge:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 48 and 0 <= ny < 48 and not mask[ny][nx] and im.getpixel((nx, ny))[3] and (nx, ny) not in edge:
                    r0, g0, b0, _ = im.getpixel((nx, ny))
                    im.putpixel((nx, ny), (min(255, r0 + 60), g0 // 2, b0 // 2, 255))


def build_arch(stage, var):
    cv = Canvas(arch_base())
    draw_arch_outline(cv, var)
    if stage >= 1:
        draw_arch_smear(cv, var)
    if stage >= 2:
        draw_arch_dissolve(cv, var)
    return cv.im


def arch_pieces(im):
    return {(c, r): im.crop((c * 16, r * 16, c * 16 + 16, r * 16 + 16)) for c in range(3) for r in range(3)}


# --------------------------------------------------------------------------------------
# 2. BASIN OF DESPAIR
# --------------------------------------------------------------------------------------
BS_RAMP = [rgb(0x1b1c24), rgb(0x23242d), rgb(0x2c2d38), rgb(0x373845), rgb(0x45465a)]
BS_DEEP = rgb(0x121319)
BS_LIT = rgb(0x5a5c72)
GROOVE = rgb(0x0d0e13)
GREEN_TINT = [rgb(0x232d27), rgb(0x2a3a2d), rgb(0x34483a)]


def basin_stone(var):
    rng = random.Random(41 if var == "A" else 42)
    im = blotch(rng, BS_RAMP, cell=2, jitter=0.45)
    P = lambda x, y, c: im.putpixel((x, y), c)
    if var == "A":
        # ashlar: two courses with offset joints, a carved zigzag frieze across the middle
        for x in range(16):
            P(x, 0, BS_LIT if x % 5 else BS_RAMP[3])
            P(x, 7, BS_DEEP)
            P(x, 8, BS_LIT)
            P(x, 15, BS_DEEP)
        for y in range(1, 7):
            P(5, y, BS_DEEP)
            P(6, y, BS_RAMP[4])
        for y in range(9, 15):
            P(12, y, BS_DEEP)
            P(13, y, BS_RAMP[4])
        for x, y in ((1, 4), (2, 3), (3, 4), (4, 3)) + ((8, 4), (9, 3), (10, 4), (11, 3), (12, 4), (13, 3), (14, 4)):
            P(x, y, GROOVE)
        for x, y in ((0, 11), (1, 12), (2, 11), (3, 12), (4, 11), (5, 12), (6, 11), (7, 12), (8, 11), (9, 12), (10, 11)):
            P(x, y, GROOVE)
    else:
        # rough-hewn block, hairline crack, damp green creeping up from the base
        for y in range(11, 16):
            for x in range(16):
                if (x * 3 + y * 5 + (x // 3)) % 4 < (y - 9):
                    P(x, y, GREEN_TINT[(x + y) % 3])
        for x, y in ((9, 0), (9, 1), (8, 2), (8, 3), (7, 4), (7, 5), (8, 6), (8, 7), (9, 8), (10, 9)):
            P(x, y, GROOVE)
        for x, y in ((3, 2), (4, 2), (3, 3), (12, 5), (13, 5), (12, 6), (2, 9), (3, 9)):
            P(x, y, BS_LIT)
        for x in range(16):
            P(x, 0, BS_RAMP[3])
    return im


def basin_rim(var):
    rng = random.Random(51 if var == "A" else 52)
    im = blotch(rng, BS_RAMP, cell=2, jitter=0.4)
    P = lambda x, y, c: im.putpixel((x, y), c)
    if var == "A":
        # chiselled frame: dark groove one pixel in, lit lip under it, diamond at each corner
        for i in range(2, 14):
            P(i, 2, GROOVE)
            P(2, i, GROOVE)
            P(i, 13, BS_LIT)
            P(13, i, BS_LIT)
            P(i, 3, BS_RAMP[3]) if i < 13 else None
            P(3, i, BS_RAMP[3])
        for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
            for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1)):
                P(cx + dx, cy + dy, GROOVE if (dx, dy) != (0, 0) else BS_LIT)
        for i in range(16):
            P(i, 0, BS_LIT) if i % 3 else None
            P(0, i, BS_LIT) if i % 3 else None
            P(i, 15, BS_DEEP)
            P(15, i, BS_DEEP)
    else:
        # worn lip, one continuous carved channel with inward ticks (a rune band), chipped corners
        for i in range(16):
            P(i, 0, BS_RAMP[3]); P(0, i, BS_RAMP[3]); P(i, 15, BS_DEEP); P(15, i, BS_DEEP)
        for i in range(3, 13):
            P(i, 3, GROOVE); P(i, 12, GROOVE); P(3, i, GROOVE); P(12, i, GROOVE)
            if i > 3:
                P(i, 13, BS_LIT); P(13, i, BS_LIT)
        for i in (5, 8, 10):
            P(i, 4, GROOVE); P(i, 11, GROOVE); P(4, i, GROOVE); P(11, i, GROOVE)
        for x, y in ((0, 0), (1, 0), (0, 1), (15, 15), (14, 15), (15, 14)):
            P(x, y, BS_DEEP)
    return im


def basin_floor(var):
    rng = random.Random(61 if var == "A" else 62)
    base = [rgb(0x15161c), rgb(0x1b1c24), rgb(0x20212b)]
    im = blotch(rng, base, cell=2, jitter=0.35)
    P = lambda x, y, c: im.putpixel((x, y), c) if 0 <= x < 16 and 0 <= y < 16 else None
    if var == "A":
        crust = [rgb(0x4a5a3a), rgb(0x66794a), rgb(0x86996a)]
        for pts in (((1, 3), (2, 3), (3, 4), (4, 5), (4, 6), (5, 7)), ((10, 2), (11, 3), (11, 4), (12, 5), (13, 5)),
                    ((6, 11), (7, 11), (8, 12), (9, 12), (10, 13)), ((13, 9), (14, 10), (14, 11))):
            for i, (x, y) in enumerate(pts):
                P(x, y, rgb(0x08090c))
                P(x + 1, y + 1, crust[i % 2])
        for x, y in ((3, 10), (4, 10), (9, 6), (10, 6), (14, 2), (1, 14), (2, 14)):
            P(x, y, crust[2])
    else:
        # dried residue in big cracked plates: hand-placed crack polylines, two dust tones
        dust = [rgb(0x34373a), rgb(0x42463f), rgb(0x565a50)]
        for y in range(16):
            for x in range(16):
                P(x, y, dust[((x // 5) + (y // 4)) % 2])
        crack = rgb(0x0b0c10)
        for pts in (((0, 4), (3, 4), (4, 5), (4, 8), (6, 9), (9, 9), (9, 12), (11, 13), (15, 13)),
                    ((7, 0), (7, 3), (9, 4), (11, 4), (12, 7), (15, 7)),
                    ((4, 8), (2, 10), (2, 15))):
            for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
                line(P, x0, y0, x1, y1, crack)
        for x, y in ((1, 1), (2, 1), (11, 1), (12, 10), (13, 10), (6, 13), (7, 13)):
            P(x, y, dust[2])
    return im


def basin_liquid(var):
    frames = 8
    strip = Image.new("RGBA", (16, 16 * frames))
    for f in range(frames):
        t = f / frames
        for y in range(16):
            for x in range(16):
                u, v = x / 16.0, y / 16.0
                if var == "A":
                    # dark emerald body with thin bright veins that drift (vein = where a wave crosses zero)
                    body = math.sin(2 * math.pi * (u + v - t)) + math.sin(2 * math.pi * (2 * u - v + t))
                    vein = math.sin(2 * math.pi * (u + 2 * v - t)) + 0.5 * math.sin(2 * math.pi * (2 * u - v + t))
                    pal = [rgb(0x0b3a20), rgb(0x105a2c), rgb(0x178a3c), rgb(0x34c050)]
                    c = pal[0] if body < -0.2 else pal[1] if body < 1.0 else pal[2]
                    if abs(vein) < 0.2:
                        c = pal[3]
                    if abs(vein) < 0.04:
                        c = rgb(0xb4ffa0)
                else:
                    # calm glowing pool: slow swell, sparse sparkles, bubbles rising
                    sw = math.sin(2 * math.pi * (u + t)) + math.sin(2 * math.pi * (v - t)) + 0.7 * math.sin(2 * math.pi * (u + v))
                    pal = [rgb(0x0f4a28), rgb(0x16662f), rgb(0x218a3e), rgb(0x44c060)]
                    c = pal[0] if sw < -1.0 else pal[1] if sw < 0.1 else pal[2] if sw < 1.3 else pal[3]
                strip.putpixel((x, f * 16 + y), c)
        if var == "B":
            for bx, by0 in ((3, 14), (11, 6), (7, 1)):
                by = (by0 - f * 2) % 16
                strip.putpixel((bx, f * 16 + by), rgb(0xe0ffc8))
                strip.putpixel((bx, f * 16 + (by + 1) % 16), rgb(0x8cf09c))
                strip.putpixel(((bx + 1) % 16, f * 16 + by), rgb(0x8cf09c))
    return strip


def basin_locket(var):
    floor = basin_floor("A")
    P = lambda im, x, y, c: im.putpixel((x, y), c)
    pal = {"D": rgb(0x5a380e), "G": rgb(0xffe98a), "g": rgb(0xe0aa2c), "d": rgb(0xa8701a),
           "s": rgb(0x2cb468), "c": rgb(0xd8a830), "C": rgb(0x7c5416)}
    top = Image.new("RGBA", (16, 16))
    if var == "A":
        path = [(8, 2), (7, 1), (6, 1), (5, 1), (4, 2), (3, 3), (3, 4), (3, 5), (3, 6), (4, 7), (4, 8), (4, 9), (5, 10)]
        for i, (x, y) in enumerate(path):
            P(top, x, y, pal["c"] if i % 2 == 0 else pal["C"])
        rows = ["......DDDDDD....", ".....DGGggggD...", ".....DGgggggdD..", ".....DGsssgdD...",
                ".....DGsgggdD...", ".....DGsssgdD...", ".....DGggsgdD...", ".....DGsssgdD...",
                ".....DgggdddD...", "......DDDDDD...."]
        for j, r in enumerate(rows):
            assert len(r) == 16, r
            for x, ch in enumerate(r):
                if ch != ".":
                    P(top, x, 3 + j, pal[ch])
        for x, y in ((7, 2), (8, 2)):
            P(top, x, y, pal["c"])
    else:
        # round medallion lying face-up on a coil of chain
        cx, cy = 8.0, 8.5
        for k in range(40):
            a = math.radians(k * 9)
            x = round(cx + 6.2 * math.cos(a) - .5)
            y = round(cy + 5.2 * math.sin(a) - .5)
            if 0 <= x < 16 and 0 <= y < 16 and not (110 < k * 9 < 200 and False):
                P(top, x, y, pal["c"] if k % 2 == 0 else pal["C"])
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + .5 - 8.3, y + .5 - 8.0)
                if d <= 4.6:
                    lit = ((x + .5 - 8.3) + (y + .5 - 8.0)) / 6.5
                    c = pal["D"] if d > 3.9 and lit > 0 else pal["G"] if lit < -0.45 else pal["g"] if lit < 0.2 else pal["d"]
                    P(top, x, y, c)
        for x, y in ((7, 6), (8, 6), (9, 7), (8, 8), (7, 9), (8, 10), (9, 10)):
            P(top, x, y, pal["s"])
    return over(floor, top)


# --------------------------------------------------------------------------------------
# 3. CRYSTAL GOBLET
# --------------------------------------------------------------------------------------
GL = {"L": rgb(0xdceafa), "w": rgb(0xf6fbff), "o": rgb(0x7898c8), "O": rgb(0x4a6ca4)}
FILLS = {
    "empty": None,
    "potion": {"F": rgb(0xb4ff9c), "f": rgb(0x50d85a), "d": rgb(0x1e9444)},
    "water": {"F": rgb(0x3a5a96), "f": rgb(0x1a2c5c), "d": rgb(0x0a1030)},
}
GOBLET_A = [
    "................",
    "....wLLLLLLo....",
    "....wFFFFFFO....",
    "....wFfffdO.....".replace("dO.....", "dO...."),
    "....wfffffdO....",
    ".....wfffdO.....",
    ".....wffddO.....",
    "......wddO......",
    ".......wO.......",
    ".......wO.......",
    ".......wO.......",
    ".......wO.......",
    "......wLLO......",
    ".....wLLLLO.....",
    ".....OOOOOO.....",
    "................",
]
GOBLET_B = [
    "................",
    "..wLLLLLLLLLLO..",
    "..wFFFFFFFFFFO..",
    "..wFffffffffdO..",
    "..wffffffffddO..",
    "...wffffffddO...",
    "...wfffffdddO...",
    "....wffffddO....",
    ".....wfdddO.....",
    "......wddO......",
    ".......wO.......",
    ".......wO.......",
    "......wLLO......",
    "....wLLLLLLO....",
    "....OOOOOOOO....",
    "................",
]


def goblet_icon(var, fill):
    rows = GOBLET_A if var == "A" else GOBLET_B
    pal = dict(GL)
    fl = FILLS[fill]
    out = []
    for r in rows:
        out.append(r)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, r in enumerate(out):
        if len(r) != 16:
            r = (r + "." * 16)[:16]
        for x, ch in enumerate(r):
            if ch == ".":
                continue
            if ch in "Ffd":
                if fl:
                    im.putpixel((x, y), fl[ch])
                elif ch == "F" and y == 2 and False:
                    pass
                continue
            im.putpixel((x, y), pal[ch])
    return im


def goblet_block(var):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    P = lambda x, y, c: im.putpixel((x, y), c)
    if var == "A":
        ramp = [rgb(0x94b6d8), rgb(0xb6d0ea), rgb(0xdcebf8), rgb(0xf4faff)]
        for y in range(16):
            for x in range(16):
                P(x, y, ramp[((x - y + 32) // 4) % 4])
        for i in range(16):
            P(i, 0, rgb(0xf6fbff))
            P(0, i, rgb(0xf6fbff))
            P(i, 15, rgb(0x5878a8))
            P(15, i, rgb(0x5878a8))
    else:
        for i in range(16):
            for c, x, y in ((rgb(0xe4f0fc), i, 0), (rgb(0xe4f0fc), 0, i), (rgb(0x6c8cbc), i, 15), (rgb(0x6c8cbc), 15, i)):
                P(x, y, c)
        for x, y in ((3, 4), (4, 3), (5, 2), (3, 5), (4, 4), (5, 3), (7, 10), (8, 9), (9, 8), (8, 10), (9, 9), (10, 8)):
            P(x, y, rgb(0xf6fbff))
        for x, y in ((3, 6), (4, 5), (9, 11), (10, 10)):
            P(x, y, rgb(0xbcd4ee))
    return im


# --------------------------------------------------------------------------------------
# 4. IRON RING + CHAIN TINT
# --------------------------------------------------------------------------------------
IRON = [rgb(0x23262e), rgb(0x4a4e5a), rgb(0x7c808c), rgb(0xb0b4be)]
RUST = [rgb(0x6a3a22), rgb(0x8c4c28)]


def ring_top(var):
    im = vanilla("deepslate").copy()
    P = lambda x, y, c: im.putpixel((x, y), c)
    cx, cy = 8.0, 8.0
    if var == "A":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + .5 - cx, y + .5 - cy)
                if 5.2 < d <= 6.9:      # socket shadow
                    r, g, b, _ = im.getpixel((x, y))
                    P(x, y, (r * 6 // 10, g * 6 // 10, b * 6 // 10, 255))
        r_in, r_out = 2.6, 5.0
        plate = [(6, 0), (7, 0), (8, 0), (9, 0), (6, 1), (7, 1), (8, 1), (9, 1)]
    else:
        for y in range(2, 14):
            for x in range(2, 14):
                r, g, b, _ = im.getpixel((x, y))
                f = 5 if (x == 2 or y == 2) else 8 if (x == 13 or y == 13) else 7
                P(x, y, (r * f // 10, g * f // 10, b * f // 10, 255))
        for x, y in ((3, 12), (4, 12), (12, 11), (12, 12)):
            P(x, y, IRON[1])
        r_in, r_out = 2.2, 4.8
        plate = []
    for y in range(16):
        for x in range(16):
            dx, dy = x + .5 - cx, y + .5 - cy
            d = math.hypot(dx, dy)
            if r_in <= d <= r_out:
                u = (d - (r_in + r_out) / 2) / ((r_out - r_in) / 2)
                lit = -(dx + dy) / (d * 1.414)
                s = 0.55 * lit + 0.45 * (1 - abs(u)) - 0.25 * max(0, u)
                c = IRON[3] if s > 0.7 else IRON[2] if s > 0.3 else IRON[1] if s > -0.1 else IRON[0]
                if abs(u) > 0.8 and lit < 0.2:
                    c = IRON[0]
                P(x, y, c)
    for i, (x, y) in enumerate(plate):
        P(x, y, IRON[2] if y == 0 else IRON[1])
    if plate:
        P(6, 0, IRON[3]); P(9, 1, IRON[0])
    if var == "B":
        for x, y in ((6, 11), (7, 11), (7, 12), (9, 12), (9, 13), (10, 9), (5, 4)):
            P(x, y, RUST[0])
        for x, y in ((7, 12), (9, 13)):
            P(x, y, RUST[1])
    return im


def chain_tint(var):
    ch = vanilla("chain")
    tint = (96, 214, 120) if var == "A" else (58, 255, 108)
    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            r, g, b, a = ch.getpixel((x, y))
            if a:
                # vanilla multiplies the sprite by the tint; lift the dark steel a little so it reads
                lum = (r + g + b) / 3.0
                k = 0.55 + lum / 255.0 * 1.6
                out.putpixel((x, y), (min(255, int(tint[0] / 255.0 * lum * k * 1.4)),
                                      min(255, int(tint[1] / 255.0 * lum * k * 1.4)),
                                      min(255, int(tint[2] / 255.0 * lum * k * 1.4)), 255))
    return out


# --------------------------------------------------------------------------------------
# 5. SKIFF  (128x64, existing UV layout: hull atlas 0..104 x 0..54, lantern/post/cap at x>=104)
# --------------------------------------------------------------------------------------
WOOD = [rgb(0x0e130d), rgb(0x161d13), rgb(0x1f2819), rgb(0x293420), rgb(0x374430)]
MOSS = [rgb(0x203a24), rgb(0x2c5030), rgb(0x3e6e44)]


def skiff(var):
    rng = random.Random(7 if var == "A" else 8)
    im = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
    P = lambda x, y, c: im.putpixel((x, y), c) if 0 <= x < 128 and 0 <= y < 64 else None
    W, H = 104, 54
    y = 0
    while y < H:
        x = -rng.randrange(0, 24)
        while x < W:
            L = rng.randrange(22, 46)
            tone = rng.choice([2, 2, 3, 1, 2])
            for yy in range(4):
                for xx in range(L):
                    px, py = x + xx, y + yy
                    if px < 0 or px >= W or py >= H:
                        continue
                    if yy == 3:
                        c = WOOD[0]
                    elif yy == 0:
                        c = WOOD[min(4, tone + 1)]
                    else:
                        c = WOOD[tone]
                    if xx == 0:
                        c = WOOD[0]
                    P(px, py, c)
            # grain: short darker/lighter runs along the plank
            for _ in range(L // 9):
                gx, gy = x + rng.randrange(2, L - 2), y + rng.randrange(1, 3)
                for k in range(rng.randrange(3, 8)):
                    if 0 <= gx + k < W and gx + k < x + L:
                        P(gx + k, gy, WOOD[max(0, tone - 1)] if rng.random() < .6 else WOOD[min(4, tone + 1)])
            # nails
            for nx in (x + 2, x + L - 3):
                if 0 < nx < W - 1 and y + 1 < H:
                    P(nx, y + 1, WOOD[0])
                    P(nx + 1, y + 1, rgb(0x6a6a5a) if var == "A" else RUST[1])
            x += L
        y += 4
    # wet green: moss creeping up from the bottom of every board zone and in blotches
    for _ in range(6 if var == "A" else 16):
        bx, by = rng.randrange(0, W), rng.randrange(0, H)
        for k in range(rng.randrange(4, 9)):
            px = bx + k
            for yy in range(rng.randrange(1, 3)):
                if px < W and by + yy < H:
                    cur = im.getpixel((px, by + yy))
                    if cur[1] < 90 and rng.random() < 0.8:
                        P(px, by + yy, MOSS[rng.randrange(0, 2 if var == "A" else 3)])
    for yy in range(36, 48):   # lower waterline of the side boards (the 36x12 faces)
        for xx in range(0, 76):
            if rng.random() < (yy - 35) / 40.0:
                P(xx, yy, MOSS[0] if rng.random() < .7 else MOSS[1])
    if var == "B":
        for _ in range(7):    # splits in the grain
            sx, sy = rng.randrange(4, 100), rng.randrange(0, 50)
            for k in range(rng.randrange(5, 9)):
                P(sx + k, sy + (k // 3), WOOD[0])
    # ---- lantern box at (104,0): 5x6x5 -> top (109,0) bottom (114,0) sides y5..11
    IRONC = [rgb(0x15171a), rgb(0x2a2d33), rgb(0x45494f)]
    GLOW = [rgb(0x3ea03c), rgb(0x78e068), rgb(0xc8ffa8), rgb(0xf0ffe0)]
    for xx in range(5):
        for yy in range(5):
            P(109 + xx, yy, IRONC[1] if (xx in (0, 4) or yy in (0, 4)) else IRONC[2])
            P(114 + xx, yy, IRONC[0])
    for face in range(4):
        ox = 104 + face * 5
        for xx in range(5):
            for yy in range(6):
                edge = xx in (0, 4) or yy in (0, 5)
                if edge:
                    c = IRONC[1] if yy in (0, 5) else IRONC[0]
                else:
                    c = GLOW[1]
                    if var == "A":
                        c = GLOW[2] if (xx == 2 and yy in (2, 3)) else GLOW[1] if xx != 3 else GLOW[0]
                    else:
                        c = GLOW[3] if (xx == 2 and 2 <= yy <= 3) else GLOW[2] if (xx == 2 or (xx in (1, 3) and yy == 3)) else GLOW[0] if yy == 4 else GLOW[1]
                P(ox + xx, 5 + yy, c)
    # ---- post (104,12): d2 w2 h14 -> faces at (104..112, 14..28)
    for xx in range(8):
        for yy in range(14):
            if var == "A":
                c = IRONC[1] if xx % 2 == 0 else IRONC[0]
                if xx % 2 == 0 and yy % 5 == 0:
                    c = IRONC[2]
            else:
                c = WOOD[2] if xx % 2 == 0 else WOOD[1]
                if yy % 3 == 1:
                    c = rgb(0x6c5a3a) if xx % 2 == 0 else rgb(0x3e3322)   # rope wraps
            P(104 + xx, 14 + yy, c)
    for xx in range(4):
        for yy in range(2):
            P(106 + xx, 12 + yy, IRONC[2])
    # ---- lantern cap (104,30): d6 w6 h1: top (110,30) 6x6, bottom (116,30), sides row y36
    for xx in range(24):
        for yy in range(7):
            if xx < 6 and yy < 6:
                continue
            if yy == 6 or (110 <= 104 + xx < 122 and yy < 6):
                pass
    for xx in range(6):
        for yy in range(6):
            P(110 + xx, 30 + yy, IRONC[2] if (xx in (0, 5) or yy in (0, 5)) else IRONC[1])
            P(116 + xx, 30 + yy, IRONC[0])
    for xx in range(24):
        P(104 + xx, 36, IRONC[1] if xx % 6 else IRONC[0])
    return im


# --------------------------------------------------------------------------------------
# REGISTRY  (key -> entry)
# --------------------------------------------------------------------------------------
def build_all():
    E = []
    # arch: 3 stages x 2 variants; entry holds the 48x48 canvases
    for st in (0, 1, 2):
        imgs = {v: build_arch(st, v) for v in "AB"}
        E.append(dict(key="blood_arch_s%d" % st, apply="blood_arch", kind="arch", stage=st, imgs=imgs,
                      title={0: "1. Blood arch stage 0 (hairline arch)", 1: "1. Blood arch stage 1 (fresh smear)",
                             2: "1. Blood arch stage 2 (dissolving, needs cutout + noOcclusion)"}[st],
                      before=[ASSETS / "block" / ("blood_seal.png", "blood_seal_1.png", "blood_seal_2.png")[st]]))
    E.append(dict(key="basin_rim", apply="basin", kind="tile", title="2a. Basin rim (top, carved)", imgs={v: basin_rim(v) for v in "AB"},
                  before=[ASSETS / "block/despair_basin_rim.png"], asset="block/despair_basin_rim.png"))
    E.append(dict(key="basin_stone", apply="basin", kind="tile", title="2b. Basin stone (sides)", imgs={v: basin_stone(v) for v in "AB"},
                  before=[ASSETS / "block/despair_basin_stone.png"], asset="block/despair_basin_stone.png"))
    E.append(dict(key="basin_liquid", apply="basin_liquid", kind="strip", title="2c. Basin liquid (8-frame animation, mcmeta)", imgs={v: basin_liquid(v) for v in "AB"},
                  before=[ASSETS / "block/despair_basin_liquid.png"], asset="block/despair_basin_liquid.png"))
    E.append(dict(key="basin_floor", apply="basin", kind="tile", title="2d. Basin dry floor", imgs={v: basin_floor(v) for v in "AB"},
                  before=[ASSETS / "block/despair_basin_floor.png"], asset="block/despair_basin_floor.png"))
    E.append(dict(key="basin_locket", apply="basin", kind="tile", title="2e. Locket lying in the basin", imgs={v: basin_locket(v) for v in "AB"},
                  before=[ASSETS / "block/despair_basin_locket.png"], asset="block/despair_basin_locket.png"))
    E.append(dict(key="goblet_block", apply="goblet", kind="tile", title="3a. Crystal goblet block texture", imgs={v: goblet_block(v) for v in "AB"},
                  before=[ASSETS / "block/crystal_goblet.png"], asset="block/crystal_goblet.png"))
    for fill, asset in (("empty", "item/crystal_goblet.png"), ("potion", "item/crystal_goblet_potion.png"), ("water", "item/crystal_goblet_water.png")):
        E.append(dict(key="goblet_item_" + fill, apply="goblet_item", kind="icon", title="3b. Goblet icon, " + fill,
                      imgs={v: goblet_icon(v, fill) for v in "AB"}, before=[None], asset=asset))
    E.append(dict(key="ring_top", apply="ring", kind="tile", title="4a. Iron ring in the shore floor (top)", imgs={v: ring_top(v) for v in "AB"},
                  before=["vanilla:deepslate"], asset="block/cave_ring_top.png"))
    E.append(dict(key="chain_tint", apply=None, kind="swatch", title="4b. Chain tint when revealed (vanilla chain x tint)", imgs={v: chain_tint(v) for v in "AB"},
                  before=["vanilla:chain"], asset=None))
    E.append(dict(key="skiff", apply="skiff", kind="skiff", title="5. Skiff entity texture 128x64 (same UV layout)", imgs={v: skiff(v) for v in "AB"},
                  before=[ASSETS / "entity/cave_skiff.png"], asset="entity/cave_skiff.png"))
    return E


def write_candidates(E):
    OUT.mkdir(parents=True, exist_ok=True)
    for e in E:
        for v, im in e["imgs"].items():
            if e["kind"] == "arch":
                for (c, r), p in arch_pieces(im).items():
                    p.save(OUT / ("blood_arch_s%d_c%d_r%d_%s.png" % (e["stage"], c, r, v)))
            else:
                im.save(OUT / ("%s_%s.png" % (e["key"], v)))
                if e["kind"] == "strip":
                    (OUT / ("%s_%s.png.mcmeta" % (e["key"], v))).write_text(
                        json.dumps({"animation": {"frametime": 4, "interpolate": False}}, indent=2))


def apply_picks(E, spec):
    picks = {}
    for part in spec.split(","):
        k, v = part.split("=")
        picks[k.strip()] = v.strip().upper()
    done = []
    for e in E:
        grp = e["apply"]
        v = picks.get(e["key"]) or picks.get(grp) or picks.get("all")
        if not grp or not v:
            continue
        if e["kind"] == "arch":
            for (c, r), p in arch_pieces(e["imgs"][v]).items():
                dst = ASSETS / "block" / ("blood_arch_s%d_c%d_r%d.png" % (e["stage"], c, r))
                p.save(dst); done.append(dst)
        else:
            dst = ASSETS / e["asset"]
            dst.parent.mkdir(parents=True, exist_ok=True)
            e["imgs"][v].save(dst); done.append(dst)
            if e["kind"] == "strip":
                shutil.copy(OUT / ("%s_%s.png.mcmeta" % (e["key"], v)), str(dst) + ".mcmeta")
    for d in done:
        print("wrote", d)


# --------------------------------------------------------------------------------------
# SHEET
# --------------------------------------------------------------------------------------
BG = (28, 28, 34)
CELL_W = 540


def font(sz):
    for n in ("arial.ttf", "segoeui.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(n, sz)
        except OSError:
            pass
    return ImageFont.load_default()


F_T, F_L = font(22), font(15)


def checker(w, h, s=8):
    im = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(0, h, s):
        for x in range(0, w, s):
            d.rectangle([x, y, x + s - 1, y + s - 1], fill=(70, 70, 76) if (x // s + y // s) % 2 else (86, 86, 92))
    return im


def nn(im, k):
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def on_checker(im):
    c = checker(im.width, im.height)
    c.alpha_composite(im)
    return c


def load_before(spec):
    if spec is None:
        return None
    if isinstance(spec, str):
        im = vanilla(spec.split(":")[1])
        return im
    if not Path(spec).exists():
        return None
    im = Image.open(spec).convert("RGBA")
    return im


def label_img(text, w, color=(230, 230, 235), f=None):
    f = f or F_L
    im = Image.new("RGBA", (w, 22), BG + (255,))
    ImageDraw.Draw(im).text((2, 2), text, fill=color, font=f)
    return im


def stack(parts, gap=6):
    w = max(p.width for p in parts)
    h = sum(p.height for p in parts) + gap * (len(parts) - 1)
    out = Image.new("RGBA", (w, h), BG + (255,))
    y = 0
    for p in parts:
        out.paste(p, (0, y))
        y += p.height + gap
    return out


def hstack(parts, gap=14):
    h = max(p.height for p in parts)
    w = sum(p.width for p in parts) + gap * (len(parts) - 1)
    out = Image.new("RGBA", (w, h), BG + (255,))
    x = 0
    for p in parts:
        out.paste(p, (x, 0))
        x += p.width + gap
    return out


def cell_tile(im, label):
    if im is None:
        ph = Image.new("RGBA", (CELL_W - 20, 192), (44, 44, 52, 255))
        ImageDraw.Draw(ph).text((12, 80), "none", fill=(160, 160, 170), font=F_T)
        return stack([label_img(label, CELL_W - 20), ph])
    note = ""
    if im.size != (16, 16):
        note = " (source %dx%d)" % im.size
        im = im.crop((0, 0, 16, 16)) if im.width == 16 else im.resize((16, 16), Image.NEAREST)
    big = on_checker(nn(im, 8))
    t3 = on_checker(nn(tile_n(im, 3), 4))
    return stack([label_img(label + note, CELL_W - 20), hstack([big, t3], 10)])


def cell_strip(im, label):
    if im is None:
        return cell_tile(None, label)
    if im.width != 16 or im.height % 16:
        return cell_tile(im, label)
    n = im.height // 16
    frames = [on_checker(nn(im.crop((0, i * 16, 16, i * 16 + 16)), 4)) for i in range(n)]
    row = hstack(frames, 2)
    f0 = on_checker(nn(tile_n(im.crop((0, 0, 16, 16)), 3), 4))
    f4 = on_checker(nn(tile_n(im.crop((0, 64, 16, 80)), 3), 4)) if n > 4 else f0
    return stack([label_img(label + "  (%d frames, 4x)" % n, CELL_W - 20), row, hstack([f0, f4], 10)])


def arch_cell(im, label, before=None):
    bs = vanilla("blackstone")
    ctx = Image.new("RGBA", (80, 80))
    for j in range(5):
        for i in range(5):
            ctx.paste(bs, (i * 16, j * 16))
    if before is not None:
        for j in range(3):
            for i in range(3):
                ctx.alpha_composite(before.convert("RGBA"), (16 + i * 16, 16 + j * 16))
        big = on_checker(nn(ctx, 4))
        return stack([label_img(label, CELL_W - 20), big])
    ctx.alpha_composite(im, (16, 16))
    big = nn(ctx, 4)
    # exploded view of the 9 tile pieces, with gaps, so the piece boundaries are visible
    ex = Image.new("RGBA", (48 * 3 + 8, 48 * 3 + 8), BG + (255,))
    for (c, r), p in arch_pieces(im).items():
        ex.alpha_composite(on_checker(nn(p, 3)), (c * 52, r * 52))
    d = ImageDraw.Draw(ex)
    for (c, r) in arch_pieces(im):
        d.text((c * 52 + 3, r * 52 + 2), "c%d r%d" % (c, r), fill=(255, 255, 255), font=font(11))
    return stack([label_img(label, CELL_W - 20), hstack([big, ex], 10)])


def icons_cell(ims, label):
    if ims is None:
        return cell_tile(None, label)
    slots = []
    for im in ims:
        s = Image.new("RGBA", (76, 76), rgb(0x373737))
        ImageDraw.Draw(s).rectangle([2, 2, 73, 73], fill=rgb(0x8b8b8b))
        s.alpha_composite(nn(im, 4), (6, 6))
        slots.append(s)
    big = [on_checker(nn(im, 8)) for im in ims]
    return stack([label_img(label, CELL_W - 20), hstack(slots, 6), hstack([nn(im, 8) if False else on_checker(nn(im, 4)) for im in ims], 6)])


def cell_skiff(im, label):
    if im is None:
        return cell_tile(None, label)
    return stack([label_img(label, CELL_W - 20), on_checker(nn(im, 4))])


def cell_swatch(im, label, tints=None):
    return cell_tile(im, label)


def build_sheet(E):
    rows = []
    hdr = hstack([label_img("BEFORE (current file)", CELL_W - 20, (255, 214, 120), F_T),
                  label_img("A", CELL_W - 20, (140, 220, 255), F_T),
                  label_img("B", CELL_W - 20, (170, 255, 170), F_T)], 20)
    rows.append(hdr)
    goblet_icons_done = False
    for e in E:
        if e["kind"] == "arch":
            b = load_before(e["before"][0])
            cells = [arch_cell(None, "%s - BEFORE (one tile repeated)" % e["title"], b),
                     arch_cell(e["imgs"]["A"], e["title"] + " - A"),
                     arch_cell(e["imgs"]["B"], e["title"] + " - B")]
        elif e["kind"] == "icon":
            if goblet_icons_done:
                continue
            goblet_icons_done = True
            ics = [x for x in E if x["kind"] == "icon"]
            cells = [icons_cell(None, "3b. Goblet icons - BEFORE (none; item reuses the block model)"),
                     icons_cell([x["imgs"]["A"] for x in ics], "3b. Goblet icons empty | green potion | lake water - A (4x on slot, then bare)"),
                     icons_cell([x["imgs"]["B"] for x in ics], "3b. Goblet icons empty | green potion | lake water - B")]
        elif e["kind"] == "skiff":
            b = load_before(e["before"][0])
            cells = [cell_skiff(b, e["title"] + " - BEFORE"), cell_skiff(e["imgs"]["A"], e["title"] + " - A"),
                     cell_skiff(e["imgs"]["B"], e["title"] + " - B")]
        elif e["kind"] == "strip":
            b = load_before(e["before"][0])
            cells = [cell_tile(b, e["title"] + " - BEFORE"), cell_strip(e["imgs"]["A"], e["title"] + " - A"),
                     cell_strip(e["imgs"]["B"], e["title"] + " - B")]
        else:
            b = load_before(e["before"][0])
            t = e["title"]
            if e["kind"] == "swatch":
                tl = {"A": "tint #60D678", "B": "tint #3AFF6C"}
                cells = [cell_tile(b, t + " - BEFORE (untinted vanilla)"), cell_tile(e["imgs"]["A"], t + " - A " + tl["A"]),
                         cell_tile(e["imgs"]["B"], t + " - B " + tl["B"])]
            else:
                cells = [cell_tile(b, t + " - BEFORE"), cell_tile(e["imgs"]["A"], t + " - A"), cell_tile(e["imgs"]["B"], t + " - B")]
        rows.append(hstack(cells, 20))
    W = max(r.width for r in rows) + 40
    sheet = stack(rows, 14)
    out = Image.new("RGBA", (W, sheet.height + 40), BG + (255,))
    out.paste(sheet, (20, 20))
    SHEET.parent.mkdir(parents=True, exist_ok=True)
    out.convert("RGB").save(SHEET)
    print("sheet", SHEET, out.size)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", help="key=A|B[,key=A|B...] or all=A; copies the chosen candidates into the assets folder")
    ap.add_argument("--list", action="store_true")
    a = ap.parse_args()
    E = build_all()
    if a.list:
        print(sorted({e["apply"] for e in E if e["apply"]}))
        return
    if a.apply:
        write_candidates(E)
        apply_picks(E, a.apply)
        return
    write_candidates(E)
    build_sheet(E)


if __name__ == "__main__":
    main()
