"""Generates the Hungarian Horntail entity textures.

The Goblet of Fire dragon is near-black with a bronze/olive sheen, bronze horns and tail spikes,
a bone-coloured plated underside, dark leathery wing membranes and burning amber eyes. This paints
that palette into the UV regions used by HungarianHorntailModel (256x256 layout).

Outputs:
  textures/entity/hungarian_horntail.png       - the skin
  textures/entity/hungarian_horntail_eyes.png  - additive glow layer (eyes only)

Run:  python build_scripts/generate_horntail_texture.py
"""

import math
import os
import random

from PIL import Image

SIZE = 256
SEED = 20260808

OUT_DIR = os.path.join("src", "main", "resources", "assets", "hogwartsmagic", "textures", "entity")

# --- Palette -------------------------------------------------------------------------------------
SCALE_DARK = (18, 16, 14)
SCALE_MID = (34, 31, 26)
SCALE_HIGH = (52, 47, 38)
SCALE_SHEEN = (86, 68, 30)      # the bronze glint that catches the light on the film dragon

BELLY_DARK = (78, 66, 46)
BELLY_LIGHT = (140, 122, 88)

MEMBRANE_DARK = (38, 24, 22)
MEMBRANE_MID = (62, 38, 33)
MEMBRANE_RIB = (94, 62, 48)

BRONZE_DARK = (94, 70, 26)
BRONZE_MID = (150, 116, 48)
BRONZE_HIGH = (206, 170, 88)

EYE_AMBER = (232, 168, 32)
EYE_AMBER_HOT = (255, 216, 96)
EYE_PUPIL = (14, 10, 6)


# --- UV regions (must match HungarianHorntailModel) ------------------------------------------------
# Whole-box footprints that need a non-default material.
BRONZE_REGIONS = [(230, 0, 26, 36)]                       # horns + ridge spikes + claws
MEMBRANE_REGIONS = [
    (0, 178, 70, 26),      # inner sail  (texOffs 0,180  -> 68 x 21)
    (72, 178, 102, 32),    # outer sail  (texOffs 72,180 -> 100 x 27)
    (0, 208, 56, 20),      # shoulder sail (texOffs 0,210 -> 54 x 15)
]
# DOWN faces, computed as (u + dz, v, dx, dz) for each box that has a plated underside.
BELLY_REGIONS = [
    (28, 0, 14, 28),       # torso
    (10, 96, 12, 10),      # pelvis
    (102, 0, 8, 14),       # neck 1
    (102, 24, 6, 14),      # neck 2
    (151, 40, 7, 15),      # jaw
    (14, 120, 8, 14),      # tail 1
    (62, 120, 6, 14),      # tail 2
    (106, 120, 5, 14),     # tail 3
    (148, 120, 4, 14),     # tail 4
]
EYE_REGIONS = [(200, 0, 10, 7), (212, 0, 10, 7)]


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def paint_scales(px, rng):
    """Base hide: overlapping rows of small dark scales with an occasional bronze glint."""
    for y in range(SIZE):
        for x in range(SIZE):
            # Offset every other row so the scales interlock rather than forming a grid.
            row = y // 3
            ox = x + (2 if row % 2 else 0)
            col = ox // 3
            # Per-scale tone.
            tone = ((col * 73 + row * 151) % 97) / 96.0
            base = lerp(SCALE_DARK, SCALE_MID, tone)
            # Rounded shading inside each scale: darker at its lower edge.
            inner_y = y % 3
            if inner_y == 2:
                base = lerp(base, SCALE_DARK, 0.55)
            elif inner_y == 0:
                base = lerp(base, SCALE_HIGH, 0.35)
            # Fine grain.
            n = rng.randint(-6, 6)
            base = (max(0, base[0] + n), max(0, base[1] + n), max(0, base[2] + n))
            # Sparse bronze sheen.
            if rng.random() < 0.012:
                base = lerp(base, SCALE_SHEEN, rng.uniform(0.35, 0.9))
            px[x, y] = base + (255,)


def paint_bronze(px, rng, rect):
    """Horn keratin: vertical striations running up the spar, bright at the tip."""
    x0, y0, w, h = rect
    for y in range(y0, min(SIZE, y0 + h)):
        for x in range(x0, min(SIZE, x0 + w)):
            # Along-the-horn gradient: the tips (top of the strip) are polished, the base is dull.
            t = 1.0 - (y - y0) / max(1.0, h - 1.0)
            base = lerp(BRONZE_DARK, BRONZE_MID, t)
            # Ridged striations.
            if (x % 3) == 0:
                base = lerp(base, BRONZE_HIGH, 0.45 * t + 0.15)
            elif (x % 3) == 2:
                base = lerp(base, BRONZE_DARK, 0.4)
            n = rng.randint(-8, 8)
            px[x, y] = (max(0, base[0] + n), max(0, base[1] + n), max(0, base[2] + n), 255)


def paint_membrane(px, rng, rect):
    """Wing skin: thin dark leather with heavier ribs fanning across it and worn, translucent gaps."""
    x0, y0, w, h = rect
    for y in range(y0, min(SIZE, y0 + h)):
        for x in range(x0, min(SIZE, x0 + w)):
            u = (x - x0) / max(1.0, w - 1.0)
            v = (y - y0) / max(1.0, h - 1.0)
            # Thinner (lighter) toward the trailing edge, where light comes through.
            base = lerp(MEMBRANE_DARK, MEMBRANE_MID, 0.25 + 0.6 * v)
            # Ribs fan out from the leading edge.
            rib = math.sin(u * math.pi * 7.0 + v * 1.4)
            if rib > 0.88:
                base = lerp(base, MEMBRANE_RIB, 0.75)
            elif rib > 0.72:
                base = lerp(base, MEMBRANE_RIB, 0.3)
            # Scarring / old tears.
            if rng.random() < 0.006:
                base = lerp(base, MEMBRANE_DARK, 0.8)
            n = rng.randint(-5, 5)
            px[x, y] = (max(0, base[0] + n), max(0, base[1] + n), max(0, base[2] + n), 255)


def paint_belly(px, rng, rect):
    """Underside: broad horn-coloured transverse plates, like a snake's scutes."""
    x0, y0, w, h = rect
    for y in range(y0, min(SIZE, y0 + h)):
        for x in range(x0, min(SIZE, x0 + w)):
            band = (y - y0) % 4
            if band == 3:
                base = lerp(BELLY_DARK, SCALE_DARK, 0.5)     # groove between plates
            else:
                # Plates are lightest along their centre line and darken toward the flanks.
                fx = abs((x - x0) / max(1.0, w - 1.0) - 0.5) * 2.0
                base = lerp(BELLY_LIGHT, BELLY_DARK, fx * 0.85)
            n = rng.randint(-7, 7)
            px[x, y] = (max(0, base[0] + n), max(0, base[1] + n), max(0, base[2] + n), 255)


def paint_eye(px, rect):
    """Amber iris with a vertical reptilian slit."""
    x0, y0, w, h = rect
    for y in range(y0, min(SIZE, y0 + h)):
        for x in range(x0, min(SIZE, x0 + w)):
            u = (x - x0) / max(1.0, w - 1.0)
            v = (y - y0) / max(1.0, h - 1.0)
            r = math.hypot(u - 0.5, v - 0.5)
            base = lerp(EYE_AMBER_HOT, EYE_AMBER, min(1.0, r * 2.0))
            px[x, y] = base + (255,)
    # Slit pupil down the middle of each side face.
    cx = x0 + w // 2
    for y in range(y0 + 1, min(SIZE, y0 + h - 1)):
        for x in (cx - 1, cx):
            if 0 <= x < SIZE:
                px[x, y] = EYE_PUPIL + (255,)


def build_skin():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 255))
    px = img.load()
    rng = random.Random(SEED)

    paint_scales(px, rng)
    for rect in BELLY_REGIONS:
        paint_belly(px, rng, rect)
    for rect in MEMBRANE_REGIONS:
        paint_membrane(px, rng, rect)
    for rect in BRONZE_REGIONS:
        paint_bronze(px, rng, rect)
    for rect in EYE_REGIONS:
        paint_eye(px, rect)
    return img


def build_eye_glow():
    """Additive layer: everything transparent except the irises, so only the eyes glow in the dark."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = img.load()
    for rect in EYE_REGIONS:
        paint_eye(px, rect)
        x0, y0, w, h = rect
        # Knock the pupil back out so the slit stays dark against the glow.
        cx = x0 + w // 2
        for y in range(y0 + 1, min(SIZE, y0 + h - 1)):
            for x in (cx - 1, cx):
                if 0 <= x < SIZE:
                    px[x, y] = (0, 0, 0, 0)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    skin_path = os.path.join(OUT_DIR, "hungarian_horntail.png")
    glow_path = os.path.join(OUT_DIR, "hungarian_horntail_eyes.png")
    build_skin().save(skin_path)
    build_eye_glow().save(glow_path)
    print("wrote", skin_path)
    print("wrote", glow_path)


if __name__ == "__main__":
    main()
