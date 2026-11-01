"""Textures and the item model for the First Task memory's props (Task 11).

Run from the repo root:  python build_scripts/generate_first_task_textures.py

Writes, under src/main/resources/assets/hogwartsmagic/:
  textures/entity/memory_golden_egg.png    256 x 128 equirectangular map of the golden egg actor (u = longitude, v = the
                                           polar angle from the top pole, as client/MemoryEggRenderer maps it): gold, with
                                           etched bands, a lattice band at the waist and a seam
  textures/entity/golden_egg_glint.png     64 x 64 additive star sprite for the glint at the egg's top
  textures/item/golden_egg_prop.png        64 x 64 item texture (the egg's gold and bands, side-on, top at v 0)
  models/item/golden_egg_prop.json         the held egg: eight slices of boxes (each an axis-aligned one and one turned 45
                                           degrees), sized so model extent x display scale is the actor's 0.36 x 0.26
  textures/item/triwizard_robes.png        the champion's robes, 16 x 16: charcoal with bronze trim
  textures/models/armor/triwizard_robes_layer_1.png   the worn robes, recoloured from the Gryffindor robes
  models/item/triwizard_robes.json

The robes are the Gryffindor robes with their reds turned charcoal and their golds bronze, so every fold and trim line
stays: house-neutral. The fire flicker strip of the plan's Task 11 text was written by Task 7's own
generate_dragon_fire_textures.py and is left to it.
"""
import colorsys
import json
import math
import random
from pathlib import Path

from PIL import Image

ASSETS = Path("src/main/resources/assets/hogwartsmagic")

# The egg's size and shape: pensieve/scene/GoldenEgg (LENGTH, WIDTH, TAPER; GoldenEggTest checks the item against them).
LENGTH, WIDTH = 0.36, 0.26

GOLD = (214, 168, 52)
GOLD_LIGHT = (250, 220, 118)
GOLD_DARK = (122, 80, 18)
ETCH = (84, 54, 12)
WHITE_GOLD = (255, 246, 205)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def gauss(x, c, w):
    return math.exp(-((x - c) / w) ** 2)


# The egg's surface in blocks: the circumference at the widest is pi * WIDTH, the half height LENGTH / 2.
CIRC, HALF = math.pi * WIDTH, LENGTH / 2

# Etched bands as (height fraction, half-width): a double line near each end, each a few texels thick even on the 64 px
# item texture; the waist band carries the lattice.
LINES = [(0.80, 0.032), (0.65, 0.032), (-0.68, 0.032), (-0.83, 0.032)]
WAIST_C, WAIST_H = -0.10, 0.20
LATTICE_CELL = CIRC / 16.0          # one diamond's width, in blocks
LATTICE_LINE = 0.0105               # a lattice line's thickness, in blocks (about 3 texels on the actor map)
STREAKS = (0.125, 0.375, 0.625, 0.875)   # longitudes of the baked highlight streaks (the egg looks the same from all sides)


def egg_pixel(u, y):
    """The egg colour at longitude fraction u (0..1) and height fraction y (-1 bottom, +1 top). Polished gold: the
    luminance follows what a mirrored egg would reflect (bright sky above, a dark horizon, a warm bounce below), with
    soft baked highlight streaks, crisp etched lines and a diamond lattice round the waist. Pure, no noise."""
    lum = (0.52 + 0.34 * y - 0.30 * gauss(y, -0.12, 0.17) + 0.24 * gauss(y, -0.62, 0.18)
           - 0.30 * smooth(0.82, 1.0, abs(y)))
    if lum < 0.5:
        col = mix(GOLD_DARK, GOLD, lum / 0.5)
    else:
        col = mix(GOLD, GOLD_LIGHT, (lum - 0.5) / 0.4)
    # Highlight streaks: tall, narrow, brightest on the shoulders.
    for c0 in STREAKS:
        du = min(abs(u - c0), 1 - abs(u - c0))
        s = gauss(du, 0, 0.03) * smooth(-0.55, 0.0, y) * (1 - smooth(0.78, 0.97, y))
        col = mix(col, WHITE_GOLD, 0.92 * s)
        col = mix(col, GOLD_DARK, 0.35 * gauss(du, 0.07, 0.03) * smooth(-0.6, 0.0, y) * (1 - smooth(0.7, 0.95, y)))
    # The thin etched lines: a dark core with a bright lip on the upper side.
    for c, h in LINES:
        d = y - c
        if abs(d) <= h:
            col = mix(col, ETCH, 0.9)
        elif -h - 0.03 < d < -h:
            col = mix(col, GOLD_LIGHT, 0.45)
    # The waist: a dark ground with a diamond lattice of bright raised lines, closed by a dark rule above and below.
    d = y - WAIST_C
    if abs(d) <= WAIST_H:
        s, hgt = u * CIRC, y * HALF
        a = ((s + hgt) / LATTICE_CELL) % 1.0
        b = ((s - hgt) / LATTICE_CELL) % 1.0
        w = LATTICE_LINE / LATTICE_CELL
        da = min(a, 1 - a)
        db = min(b, 1 - b)
        on = da < w / 2 or db < w / 2
        ground = mix(GOLD_DARK, GOLD, 0.25 + 0.25 * (0.5 + 0.5 * y))
        line = mix(GOLD, GOLD_LIGHT, 0.55 + 0.3 * (0.5 + 0.5 * y))
        col = line if on else ground
        if abs(d) > WAIST_H - 0.035:
            col = ETCH
    return col


def sample(u0, u1, y0, y1, n=8):
    """The average of egg_pixel over a texel (u0..u1, y0..y1), n x n subsamples: antialiased, crisp lines."""
    r = g = b = 0.0
    for j in range(n):
        y = y0 + (y1 - y0) * (j + 0.5) / n
        for i in range(n):
            c = egg_pixel((u0 + (u1 - u0) * (i + 0.5) / n) % 1.0, y)
            r += c[0]
            g += c[1]
            b += c[2]
    k = n * n
    return clamp(r / k), clamp(g / k), clamp(b / k), 255


def egg_texture():
    W, H = 256, 128
    img = Image.new("RGBA", (W, H))
    px = img.load()
    for j in range(H):
        # Row j covers the polar angle phi in [pi j / H, pi (j + 1) / H]; y = cos(phi).
        ya, yb = math.cos(math.pi * j / H), math.cos(math.pi * (j + 1) / H)
        for i in range(W):
            px[i, j] = sample(i / W, (i + 1) / W, yb, ya, 6)
    return img


def glint_texture():
    """A four-point star with a soft glow, premultiplied white-gold on black: drawn additive."""
    N = 64
    img = Image.new("RGBA", (N, N))
    px = img.load()
    c = (N - 1) / 2
    for j in range(N):
        for i in range(N):
            x, y = (i - c) / c, (j - c) / c
            r = math.hypot(x, y)
            # Long thin spikes along the axes, short fat ones on the diagonals, a round core.
            spike = math.exp(-abs(x) * 26) * math.exp(-abs(y) * 2.2) + math.exp(-abs(y) * 26) * math.exp(-abs(x) * 2.2)
            dx, dy = (x + y) / math.sqrt(2), (x - y) / math.sqrt(2)
            diag = 0.45 * (math.exp(-abs(dx) * 24) * math.exp(-abs(dy) * 4.5)
                           + math.exp(-abs(dy) * 24) * math.exp(-abs(dx) * 4.5))
            core = math.exp(-r * r * 38)
            glow = 0.35 * math.exp(-r * r * 6)
            v = min(1.0, spike + diag + core + glow)
            v *= max(0.0, 1 - r) ** 0.6
            col = mix((255, 226, 140), (255, 255, 250), min(1.0, core * 1.5))
            a = clamp(255 * v)
            px[i, j] = (clamp(col[0] * v), clamp(col[1] * v), clamp(col[2] * v), a)
    return img


# ---- The held egg model --------------------------------------------------------------------------------------------

ITEM_PX = 64                            # the item texture is ITEM_PX square; row j is the height band of the egg at v = j
ITEM_U = 0.25                           # the item sides show this share of the longitudes, starting at 0
SLICES = 14                             # stacked slices, equal steps in the polar angle (thin ones at the pointed ends)
POLY = 4                                # boxes per slice, turned 0, 22.5, 45, -22.5 degrees: a 16-gon
MODEL_L = 12.0                          # px tall
DISPLAY_FIRST = LENGTH * 16 / MODEL_L   # first person and the ground: model extent x scale is the actor size
DISPLAY = DISPLAY_FIRST / 0.9375        # third person: the player model is drawn at 15/16 (HumanMotion.MODEL_SCALE)
TAPER = 0.14
CORNER = 1.0      # the 0 and -22.5 degree boxes put a corner on the x and z axes: the extent there is the corner radius
MODEL_W = WIDTH * 16 / DISPLAY_FIRST    # the widest extent in model px (measured at the first-person scale)


def profile(v):
    return math.sqrt(max(0.0, 1 - v * v)) * (1 - TAPER * v)


PEAK = max(profile(-1 + i / 1000.0) for i in range(2001))


def item_egg_texture():
    """The held egg, side-on, ITEM_PX square: row j covers a height band of the egg (the top at row 0), the columns the
    longitudes 0..ITEM_U. Made by the same function as the actor map, antialiased, so the bands and the lattice are the
    actor ones."""
    img = Image.new("RGBA", (ITEM_PX, ITEM_PX))
    px = img.load()
    for j in range(ITEM_PX):
        ya, yb = 1 - 2 * j / ITEM_PX, 1 - 2 * (j + 1) / ITEM_PX
        for i in range(ITEM_PX):
            px[i, j] = sample(ITEM_U * i / ITEM_PX, ITEM_U * (i + 1) / ITEM_PX, yb, ya, 6)
    return img


def egg_model():
    """The item model: SLICES stacked slices, each POLY boxes turned about y (a 16-gon), whose radius follows the egg
    profile, sized so model extent x display scale is the actor 0.36 x 0.26."""
    top = 8 + MODEL_L / 2
    r_peak = MODEL_W / 2 / CORNER           # the corner radius of the widest slice
    elements = []
    for s in range(SLICES):
        pa, pb = math.pi * s / SLICES, math.pi * (s + 1) / SLICES
        y1, y0 = 8 + MODEL_L / 2 * math.cos(pa), 8 + MODEL_L / 2 * math.cos(pb)
        r = r_peak * profile(math.cos((pa + pb) / 2)) / PEAK
        a = r / math.sqrt(2)                # the half side of the square whose corner radius is r
        # The sides map the band of the texture this slice covers; up and down use a plain gold patch.
        v0, v1 = (top - y1) / MODEL_L * 16, (top - y0) / MODEL_L * 16
        faces = {side: {"uv": [0, round(v0, 4), 16, round(v1, 4)], "texture": "#egg"}
                 for side in ("north", "south", "east", "west")}
        faces["up"] = {"uv": [0, 4, 3, 6], "texture": "#egg"}
        faces["down"] = {"uv": [0, 4, 3, 6], "texture": "#egg"}
        for k in range(POLY):
            box = {"from": [round(8 - a, 4), round(y0, 4), round(8 - a, 4)],
                   "to": [round(8 + a, 4), round(y1, 4), round(8 + a, 4)], "faces": faces}
            if k:
                # A model element may only turn -45..45 (67.5 fails the whole model): a square turned 67.5 is the
                # same square turned -22.5
                angle = 90.0 / POLY * k
                box["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": angle - 90.0 if angle > 45 else angle}
            elements.append(json.loads(json.dumps(box)))
    d3, d1 = [round(DISPLAY, 4)] * 3, [round(DISPLAY_FIRST, 4)] * 3
    hand = {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": d3}
    first_r = {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": d1}
    first_l = {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": d1}
    return {
        "textures": {"egg": "hogwartsmagic:item/golden_egg_prop", "particle": "hogwartsmagic:item/golden_egg_prop"},
        "elements": elements,
        "display": {
            "thirdperson_righthand": hand, "thirdperson_lefthand": hand,
            "firstperson_righthand": first_r, "firstperson_lefthand": first_l,
            "gui": {"rotation": [20, 30, 0], "translation": [0, 0, 0], "scale": [1.25, 1.25, 1.25]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": d1},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
        },
    }


# ---- The robes -----------------------------------------------------------------------------------------------------

def to_charcoal_bronze(img):
    """Gryffindor robes recoloured: reds to a charcoal ramp (same lightness), golds to bronze, cream to pale bronze,
    blacks left alone. Alpha is untouched."""
    out = img.copy()
    px = out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.45 and (h < 0.06 or h > 0.94):
                level = 26 + v * 86                       # charcoal, a hint of blue
                px[x, y] = (clamp(level), clamp(level + 2), clamp(level + 7), a)
            elif s > 0.4 and 0.08 < h < 0.2:
                px[x, y] = (clamp(v * 218), clamp(v * 140), clamp(v * 58), a)    # bronze
            elif v > 0.55 and s < 0.5:
                px[x, y] = (clamp(v * 214), clamp(v * 176), clamp(v * 128), a)   # the cream tie, pale bronze
            elif s > 0.25 and (h < 0.1 or h > 0.9):
                level = 20 + v * 70
                px[x, y] = (clamp(level), clamp(level + 2), clamp(level + 6), a)  # the deep red shadow tones
    return out


def main():
    for d in ("textures/entity", "textures/item", "textures/models/armor", "models/item"):
        (ASSETS / d).mkdir(parents=True, exist_ok=True)
    egg_texture().save(ASSETS / "textures/entity/memory_golden_egg.png")
    glint_texture().save(ASSETS / "textures/entity/golden_egg_glint.png")
    item_egg_texture().save(ASSETS / "textures/item/golden_egg_prop.png")
    with open(ASSETS / "models/item/golden_egg_prop.json", "w", newline="\n", encoding="utf-8") as f:
        f.write(json.dumps(egg_model(), indent=2) + "\n")
    for src, dst in (("textures/item/gryffindor_robes.png", "textures/item/triwizard_robes.png"),
                     ("textures/models/armor/gryffindor_robes_layer_1.png",
                      "textures/models/armor/triwizard_robes_layer_1.png")):
        to_charcoal_bronze(Image.open(ASSETS / src).convert("RGBA")).save(ASSETS / dst)
    with open(ASSETS / "models/item/triwizard_robes.json", "w", newline="\n", encoding="utf-8") as f:
        f.write(json.dumps({"parent": "minecraft:item/generated",
                            "textures": {"layer0": "hogwartsmagic:item/triwizard_robes"}}, indent=2) + "\n")
    print("wrote the egg, the glint, the egg item and model, and the robes")


if __name__ == "__main__":
    main()
