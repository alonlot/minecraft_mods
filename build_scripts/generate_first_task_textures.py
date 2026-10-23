"""Textures and the item model for the First Task memory's props (Task 11).

Run from the repo root:  python build_scripts/generate_first_task_textures.py

Writes, under src/main/resources/assets/hogwartsmagic/:
  textures/entity/memory_golden_egg.png    128 x 64 equirectangular map of the golden egg actor (u = longitude, v = the
                                           polar angle from the top pole, as client/MemoryEggRenderer maps it): gold, with
                                           etched bands, a lattice band at the waist and a seam
  textures/entity/golden_egg_glint.png     64 x 64 additive star sprite for the glint at the egg's top
  textures/item/golden_egg_prop.png        16 x 16 item texture (the egg's gold and bands, side-on, top at v 0)
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
GOLD_LIGHT = (247, 212, 100)
GOLD_DARK = (152, 104, 26)
ETCH = (104, 70, 18)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def band_weight(y, centre, half):
    """1 inside a band centred at height fraction `centre` (-1..1) with half-width `half`, with a 1-texel soft edge."""
    d = abs(y - centre)
    if d <= half:
        return 1.0
    return max(0.0, 1.0 - (d - half) / 0.025)


# Etched bands as (height fraction of the egg, half-width): a double line near each end and a wide waist band.
BANDS = [(0.80, 0.018), (0.70, 0.018), (-0.12, 0.17), (-0.64, 0.018), (-0.74, 0.018)]


def egg_pixel(u, y, rnd):
    """The egg's colour at longitude fraction u (0..1) and height fraction y (-1 bottom, +1 top)."""
    n = rnd.randint(-5, 5)
    # Gold shaded a little toward the lower half so the light seems to come from above.
    base = mix(GOLD, GOLD_LIGHT, 0.25 + 0.3 * y)
    r, g, b = base[0] + n, base[1] + n, base[2] + n // 2
    # Soft vertical flutes.
    flute = math.sin(u * 2 * math.pi * 12)
    r, g, b = r + flute * 5, g + flute * 4, b + flute * 2
    wide = band_weight(y, BANDS[2][0], BANDS[2][1])
    # The waist band: a raised lattice, darker lines at 45 degrees both ways.
    if wide > 0.5:
        lat = (u * 24 + y * 7.0) % 1.0
        lat2 = (u * 24 - y * 7.0) % 1.0
        shade = 1.0 if lat > 0.12 and lat2 > 0.12 else 0.0
        base = mix(GOLD_DARK, GOLD_LIGHT, 0.55 if shade else 0.0)
        r, g, b = base[0] + n, base[1] + n, base[2] + n // 2
    for i, (c, h) in enumerate(BANDS):
        if i == 2:
            continue
        w = band_weight(y, c, h)
        if w > 0:
            e = mix((r, g, b), ETCH, 0.85 * w)
            r, g, b = e
    # The seam: a thin dark meridian at u = 0 (and 0.5), with a bright lip beside it.
    for m in (0.0, 0.5):
        du = min(abs(u - m), 1 - abs(u - m))
        if du < 0.006:
            r, g, b = ETCH
        elif du < 0.014:
            r, g, b = mix((r, g, b), GOLD_LIGHT, 0.5)
    # The ends are a little darker where the egg turns away.
    if abs(y) > 0.93:
        k = (abs(y) - 0.93) / 0.07
        r, g, b = mix((r, g, b), GOLD_DARK, 0.5 * k)
    return clamp(r), clamp(g), clamp(b), 255


def egg_texture():
    W, H = 128, 64
    rnd = random.Random(23)
    img = Image.new("RGBA", (W, H))
    px = img.load()
    for j in range(H):
        phi = math.pi * (j + 0.5) / H
        y = math.cos(phi)
        for i in range(W):
            px[i, j] = egg_pixel((i + 0.5) / W, y, rnd)
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
            v *= max(0.0, 1.0 - r) ** 0.6
            col = mix((255, 226, 140), (255, 255, 250), min(1.0, core * 1.5))
            a = clamp(255 * v)
            px[i, j] = (clamp(col[0] * v), clamp(col[1] * v), clamp(col[2] * v), a)
    return img


def item_egg_texture():
    """The held egg, side-on: 16 x 16, the top of the egg at v 0 (the model maps its slices onto these rows)."""
    rnd = random.Random(7)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for j in range(16):
        y = 1 - 2 * (j + 0.5) / 16
        for i in range(16):
            r, g, b, a = egg_pixel(0.12 + (i + 0.5) / 16 * 0.3, y, rnd)
            px[i, j] = (r, g, b, 255)
    return img


# ---- The held egg's model ------------------------------------------------------------------------------------------

SLICES = 8
MODEL_L = 12.0                          # px tall; display scale = LENGTH * 16 / MODEL_L
DISPLAY = LENGTH * 16 / MODEL_L
MODEL_W = WIDTH * 16 / DISPLAY          # the widest extent in model px
TAPER = 0.14


def profile(v):
    return math.sqrt(max(0.0, 1 - v * v)) * (1 - TAPER * v)


PEAK = max(profile(-1 + i / 1000.0) for i in range(2001))


def egg_model():
    """The item model: SLICES stacked slices, each an axis-aligned box and a box turned 45 degrees about y."""
    top = 8 + MODEL_L / 2
    h = MODEL_L / SLICES
    # The widest slice's rotated corners reach MODEL_W / 2: half-side a = MODEL_W / 2 / sqrt(2) at the peak.
    a_peak = MODEL_W / 2 / math.sqrt(2)
    elements = []
    for s in range(SLICES):
        y0 = 8 - MODEL_L / 2 + s * h
        y1 = y0 + h
        v = ((y0 + y1) / 2 - 8) / (MODEL_L / 2)
        # The widest slice is the peak; use the slice's own profile height so the maximum is exactly a_peak.
        a = a_peak * profile(v) / max(profile(((k + 0.5) * h - MODEL_L / 2) / (MODEL_L / 2)) for k in range(SLICES))
        v0 = (top - y1) / MODEL_L * 16
        v1 = (top - y0) / MODEL_L * 16
        faces = {side: {"uv": [0, round(v0, 4), 16, round(v1, 4)], "texture": "#egg"}
                 for side in ("north", "south", "east", "west")}
        faces["up"] = {"uv": [4, 0, 12, 4], "texture": "#egg"}
        faces["down"] = {"uv": [4, 12, 12, 16], "texture": "#egg"}
        box = {"from": [round(8 - a, 4), round(y0, 4), round(8 - a, 4)], "to": [round(8 + a, 4), round(y1, 4), round(8 + a, 4)],
               "faces": faces}
        elements.append(box)
        turned = json.loads(json.dumps(box))
        turned["rotation"] = {"origin": [8, 8, 8], "axis": "y", "angle": 45}
        elements.append(turned)
    hand = {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [round(DISPLAY, 4)] * 3}
    first_r = {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [round(DISPLAY, 4)] * 3}
    first_l = {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [round(DISPLAY, 4)] * 3}
    return {
        "textures": {"egg": "hogwartsmagic:item/golden_egg_prop", "particle": "hogwartsmagic:item/golden_egg_prop"},
        "elements": elements,
        "display": {
            "thirdperson_righthand": hand, "thirdperson_lefthand": hand,
            "firstperson_righthand": first_r, "firstperson_lefthand": first_l,
            "gui": {"rotation": [20, 30, 0], "translation": [0, 0, 0], "scale": [1.25, 1.25, 1.25]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [round(DISPLAY, 4)] * 3},
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
