"""Generates the Hippogriff entity texture from the Blockbench model's own UV layout.

Buckbeak in Prisoner of Azkaban is a steel-grey creature: a near-white feathered face
and throat, silver-grey feather coverts over the shoulders, near-black banded primaries
on the wings, a dappled grey horse hindquarter, black talons, dark hooves and a pale
silver tail. This paints that palette per cube, using the exact per-face UV rectangles
recorded in blockbench_models/hippogriff_v2.bbmodel so the texture can never drift out
of register with the geometry.

Run:  python build_scripts/generate_hippogriff_texture.py
"""

import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(ROOT, "blockbench_models", "hippogriff_v2.bbmodel")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                   "textures", "entity", "hippogriff.png")

SEED = 20260808


# --- Palette ---------------------------------------------------------------------------------
COAT_MID = (146, 96, 56)          # chestnut barrel / haunch
COAT_DARK = (112, 72, 40)
COAT_LIGHT = (178, 126, 80)

PLUME_MID = (232, 224, 204)       # cream breast, neck, shoulder feathering
PLUME_DARK = (198, 186, 162)
PLUME_LIGHT = (246, 240, 226)

FACE_PALE = (246, 241, 228)       # skull, cheeks, throat
FACE_SHADE = (226, 216, 196)      # brow and nape, only a shade off the face

COVERT_MID = (154, 102, 58)       # wing coverts
COVERT_DARK = (118, 76, 42)

SECONDARY = (132, 86, 48)         # secondary feather sheet
PRIMARY_MID = (120, 78, 44)       # the long flight feathers
PRIMARY_TIP = (84, 54, 30)
PRIMARY_SHAFT = (198, 158, 112)

BEAK_MID = (233, 182, 76)         # warm gold, the way it reads on screen
BEAK_TIP = (198, 140, 48)
BEAK_RIDGE = (250, 218, 138)

SCALE_MID = (216, 190, 134)       # pale yellow bird shanks
SCALE_DARK = (176, 148, 96)
TALON = (74, 62, 50)
HOOF = (84, 62, 44)

LOWER_LEG = (206, 186, 150)       # the pale socks on the hind legs

HAIR_MID = (176, 138, 96)         # tail hair
HAIR_DARK = (138, 102, 66)

EYE_IRIS = (168, 110, 56)
EYE_PUPIL = (38, 26, 18)
EYE_GLINT = (255, 252, 244)
EYE_RIM = (250, 244, 232)


# --- Which material each cube is painted with -------------------------------------------------
def material_for(name):
    if name in ("barrel", "croup", "tail_dock"):
        return "fur", COAT_MID
    if name.endswith("_haunch"):
        return "fur", COAT_MID
    if name.endswith("_gaskin"):
        return "fur", COAT_LIGHT              # the coat fades out down the leg
    if name.endswith("_cannon"):
        return "fur", LOWER_LEG
    if name.endswith("_fetlock"):
        return "hair", PLUME_DARK
    if name.endswith("_hoof"):
        return "hoof", HOOF
    if name in ("breast", "keel", "neck_lower", "neck_up"):
        return "feather", PLUME_MID
    if name.startswith("ruff"):
        return "feather", PLUME_LIGHT
    if name in ("skull", "cheek_l", "cheek_r", "throat"):
        return "feather", FACE_PALE
    if name in ("brow", "nape"):
        return "feather", FACE_SHADE       # the darker hood over brow and nape
    if name.startswith("crest"):
        return "feather", mix(FACE_PALE, COAT_LIGHT, 0.34)   # just enough to read as separate
    if name in ("beak_base", "jaw"):
        return "beak", BEAK_MID
    if name in ("beak_tip", "beak_hook"):
        return "beak", BEAK_TIP
    if "_prim" in name:
        return "primary", PRIMARY_MID
    if name.endswith("_secondaries"):
        return "primary", SECONDARY
    if name.endswith("_wing_arm") or name.endswith("_arm_coverts") \
            or name.endswith("_wing_fore") or name.endswith("_wing_hand"):
        return "covert", COVERT_MID
    if name.endswith("_thigh"):
        return "feather", mix(PLUME_MID, COAT_LIGHT, 0.30)
    if name.endswith("_shank") or name.endswith("_foot"):
        return "scale", SCALE_MID
    if "_toe_" in name:
        return "talon", TALON
    return "fur", COAT_MID


# Per-face directional shading so the model reads with volume under flat lighting.
FACE_SHADING = {
    "up": 1.09,
    "down": 0.76,
    "north": 0.97,
    "south": 0.90,
    "east": 1.02,
    "west": 0.94,
}


def shade(color, factor):
    return tuple(max(0, min(255, int(round(c * factor)))) for c in color)


def jitter(rng, color, amount):
    return tuple(max(0, min(255, c + rng.randint(-amount, amount))) for c in color)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# Feather and coat shadows are tinted toward a warm brown rather than pure black;
# mixing toward black turns the cream plumage grey and makes the creature look cold.
SHADOW = (74, 52, 34)


def shade_down(color, amount):
    return mix(color, SHADOW, amount)


# --- Material painters ------------------------------------------------------------------------
def paint_fur(img, x0, y0, w, h, base, rng):
    px = img.load()
    for y in range(h):
        for x in range(w):
            c = jitter(rng, base, 7)
            if rng.random() < 0.16:
                c = mix(c, COAT_DARK, 0.45)
            px[x0 + x, y0 + y] = c + (255,)
    # short vertical hair streaks
    for _ in range(max(2, (w * h) // 14)):
        sx = rng.randrange(w)
        sy = rng.randrange(h)
        length = rng.randint(2, 4)
        tone = mix(base, COAT_DARK if rng.random() < 0.6 else COAT_LIGHT, 0.5)
        for k in range(length):
            if sy + k < h:
                px[x0 + sx, y0 + sy + k] = jitter(rng, tone, 5) + (255,)
    # A soft sheen where the light catches the coat, rather than grey-horse dappling.
    for _ in range(max(1, (w * h) // 110)):
        cx = rng.randrange(w)
        cy = rng.randrange(h)
        r = rng.randint(2, 3)
        for y in range(max(0, cy - r), min(h, cy + r + 1)):
            for x in range(max(0, cx - r), min(w, cx + r + 1)):
                if math.hypot(x - cx, y - cy) <= r:
                    px[x0 + x, y0 + y] = jitter(rng, mix(base, COAT_LIGHT, 0.4), 5) + (255,)


def paint_feather(img, x0, y0, w, h, base, rng):
    """Ranks of individually toned feathers.

    Row heights and feather widths are both randomised so the plumage never falls into
    the machine-regular horizontal banding that a fixed pitch produces at this scale.
    """
    px = img.load()
    dark = shade_down(base, 0.20)
    light = mix(base, (255, 255, 255), 0.18)
    for y in range(h):
        for x in range(w):
            px[x0 + x, y0 + y] = jitter(rng, base, 6) + (255,)
    y = 0
    while y < h:
        rowh = rng.choice([2, 3, 3, 4])
        x = -rng.randrange(4)
        while x < w:
            fw = rng.choice([2, 3, 3, 4, 5])
            tone = mix(base, light if rng.random() < 0.42 else dark, rng.random() * 0.42)
            for dy in range(rowh):
                for dx in range(fw):
                    xx, yy = x + dx, y + dy
                    if not (0 <= xx < w and 0 <= yy < h):
                        continue
                    if dy == rowh - 1:
                        c = dark                       # shadow under the feather lip
                    elif dx == 0 and rowh > 2:
                        c = mix(tone, dark, 0.45)      # the split between quills
                    else:
                        c = tone
                    px[x0 + xx, y0 + yy] = jitter(rng, c, 4) + (255,)
            x += fw
        y += rowh


def paint_covert(img, x0, y0, w, h, base, rng):
    """Coverts: tighter, darker feather rows with the odd pale margin."""
    px = img.load()
    dark = shade_down(base, 0.32)
    light = mix(base, (255, 255, 255), 0.28)
    for y in range(h):
        for x in range(w):
            c = base
            if y % 2 == 1:
                c = mix(c, dark, 0.5)
            if (x + (y // 2) * 3) % 5 == 0:
                c = mix(c, dark, 0.4)
            px[x0 + x, y0 + y] = jitter(rng, c, 5) + (255,)
    for _ in range(max(1, (w * h) // 40)):
        sx = rng.randrange(w)
        sy = rng.randrange(h)
        px[x0 + sx, y0 + sy] = jitter(rng, light, 6) + (255,)


def paint_primary(img, x0, y0, w, h, base, rng):
    """Flight feathers: barbs streaking along the long axis, a pale shaft, dark tip."""
    px = img.load()
    horizontal = w >= h
    length = w if horizontal else h
    across = h if horizontal else w
    shaft = across // 2
    for i in range(length):
        # darken toward the outer end of the feather
        t = i / max(1, length - 1)
        col = mix(base, PRIMARY_TIP, 0.15 + 0.55 * t)
        for j in range(across):
            c = col
            if j == shaft and across >= 3:
                c = mix(PRIMARY_SHAFT, col, 0.45)
            elif (i + j * 2) % 7 == 0:
                c = mix(col, (255, 255, 255), 0.12)
            elif (i + j * 3) % 5 == 0:
                c = shade_down(col, 0.24)
            x, y = (i, j) if horizontal else (j, i)
            px[x0 + x, y0 + y] = jitter(rng, c, 4) + (255,)


def paint_beak(img, x0, y0, w, h, base, rng):
    """Horn: smooth, with a lighter culmen ridge and a darkened tip."""
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = y / max(1, h - 1)
            c = mix(mix(base, BEAK_RIDGE, 0.35), mix(base, BEAK_TIP, 0.55), t)
            if y <= 1:
                c = mix(c, BEAK_RIDGE, 0.5)
            px[x0 + x, y0 + y] = jitter(rng, c, 4) + (255,)


def paint_scale(img, x0, y0, w, h, base, rng):
    px = img.load()
    for y in range(h):
        for x in range(w):
            c = base
            offset = (y // 2) % 2
            if (x + offset) % 2 == 0:
                c = mix(c, SCALE_DARK, 0.5)
            if y % 2 == 1:
                c = mix(c, SCALE_DARK, 0.3)
            px[x0 + x, y0 + y] = jitter(rng, c, 5) + (255,)


def paint_talon(img, x0, y0, w, h, base, rng):
    px = img.load()
    for y in range(h):
        for x in range(w):
            c = base
            if x == 0 or y == 0:
                c = mix(c, (255, 255, 255), 0.20)
            px[x0 + x, y0 + y] = jitter(rng, c, 4) + (255,)


def paint_hoof(img, x0, y0, w, h, base, rng):
    px = img.load()
    for y in range(h):
        for x in range(w):
            c = base
            if y == 0:
                c = mix(c, (255, 255, 255), 0.28)
            elif y >= h - 1:
                c = mix(c, (0, 0, 0), 0.35)
            px[x0 + x, y0 + y] = jitter(rng, c, 4) + (255,)


def paint_hair(img, x0, y0, w, h, base, rng):
    """Long tail strands running down the face."""
    px = img.load()
    for x in range(w):
        tone = mix(base, HAIR_DARK if rng.random() < 0.5 else PLUME_LIGHT,
                   rng.random() * 0.7)
        for y in range(h):
            c = tone
            if (y + x * 3) % 11 == 0:
                c = mix(c, (255, 255, 255), 0.18)
            elif (y + x * 5) % 7 == 0:
                c = mix(c, (0, 0, 0), 0.16)
            px[x0 + x, y0 + y] = jitter(rng, c, 4) + (255,)


PAINTERS = {
    "fur": paint_fur,
    "feather": paint_feather,
    "covert": paint_covert,
    "primary": paint_primary,
    "beak": paint_beak,
    "scale": paint_scale,
    "talon": paint_talon,
    "hoof": paint_hoof,
    "hair": paint_hair,
}


def norm(uv):
    x0, y0, x1, y1 = uv
    return (int(math.floor(min(x0, x1))), int(math.floor(min(y0, y1))),
            int(math.ceil(max(x0, x1))), int(math.ceil(max(y0, y1))))


def draw_eye(img, rect, flip):
    """Paints one large, soft eye right at the front of a cheek plate, beside the beak.

    The cheek plates sit proud of the skull, so the eye has to live on them or it is
    buried. Kept big and round with a catchlight and a pale lid rather than a dark brow
    stripe, which reads as a scowl. rect is (x0, y0, x1, y1) of that face; `flip` mirrors
    it for the far side.
    """
    px = img.load()
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    ew, eh = 4, 3
    # u runs back-to-front on the east face and front-to-back on the west one, so the
    # forward end of the plate is u=0 on west and u=w on east.
    ex = 1 if flip else max(0, w - ew - 1)
    ey = max(0, int(h * 0.26))
    glint_dx = 2 if flip else 1
    for dy in range(eh):
        for dx in range(ew):
            x, y = x0 + ex + dx, y0 + ey + dy
            if not (x0 <= x < x1 and y0 <= y < y1):
                continue
            if dy >= 1 and 1 <= dx <= 2:
                c = EYE_GLINT if (dy == 1 and dx == glint_dx) else EYE_PUPIL
            else:
                c = EYE_IRIS
            px[x, y] = c + (255,)
    # pale lid above the eye instead of a dark brow
    for dx in range(ew):
        x, y = x0 + ex + dx, y0 + ey - 1
        if x0 <= x < x1 and y0 <= y < y1:
            px[x, y] = EYE_RIM + (255,)


def draw_nostril(img, rect, flip):
    """A single dark nostril slit near the base of the upper beak."""
    px = img.load()
    x0, y0, x1, y1 = rect
    w, h = x1 - x0, y1 - y0
    nx = x0 + (1 if flip else w - 2)
    ny = y0 + max(1, int(h * 0.45))
    if x0 <= nx < x1 and y0 <= ny < y1:
        px[nx, ny] = mix(BEAK_TIP, SHADOW, 0.35) + (255,)


def main():
    with open(MODEL, encoding="utf-8") as fh:
        model = json.load(fh)

    size = (model["resolution"]["width"], model["resolution"]["height"])
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(SEED)

    eye_faces = {}
    nostril_faces = {}

    for el in model["elements"]:
        name = el["name"]
        kind, base = material_for(name)
        painter = PAINTERS[kind]

        # Flood the whole footprint first so nothing samples a transparent gutter.
        rects = {f: norm(d["uv"]) for f, d in el["faces"].items() if "uv" in d}
        if not rects:
            continue
        fx0 = min(r[0] for r in rects.values())
        fy0 = min(r[1] for r in rects.values())
        fx1 = max(r[2] for r in rects.values())
        fy1 = max(r[3] for r in rects.values())
        for y in range(fy0, min(fy1, size[1])):
            for x in range(fx0, min(fx1, size[0])):
                px[x, y] = jitter(rng, base, 4) + (255,)

        for face, rect in rects.items():
            x0, y0, x1, y1 = rect
            w, h = x1 - x0, y1 - y0
            if w <= 0 or h <= 0:
                continue
            tone = shade(base, FACE_SHADING.get(face, 1.0))
            painter(img, x0, y0, w, h, tone, rng)
            if (name, face) in (("cheek_l", "east"), ("cheek_r", "west")):
                eye_faces[face] = rect
            if name == "beak_base" and face in ("east", "west"):
                nostril_faces[face] = rect

    for face, rect in eye_faces.items():
        draw_eye(img, rect, flip=(face == "west"))
    for face, rect in nostril_faces.items():
        draw_nostril(img, rect, flip=(face == "west"))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT, size)


if __name__ == "__main__":
    main()
