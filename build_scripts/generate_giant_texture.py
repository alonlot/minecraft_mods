"""Paint the Giant's entity texture from the UV layout baked into giant.bbmodel.

The model is packed with deterministic box UVs (see the Blockbench build step), so
every cube's six face rectangles are derivable from its uv_offset and its size:

    up    (u + d,         v,     w, d)
    down  (u + d + w,     v,     w, d)
    east  (u,             v + d, d, h)
    north (u + d,         v + d, w, h)   <- the face the player looks at
    west  (u + d + w,     v + d, d, h)
    south (u + 2d + w,    v + d, w, h)

Run after any geometry change; the layout is re-read from the .bbmodel each time.
"""

import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BBMODEL = os.path.join(ROOT, "blockbench_models", "giant.bbmodel")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                   "textures", "entity", "giant.png")

rng = random.Random(0x67114)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def jitter(c, amount):
    return tuple(max(0, min(255, c[i] + rng.randint(-amount, amount))) for i in range(3))


# --- palettes -------------------------------------------------------------

SKIN = (198, 161, 142)
SKIN_DARK = (150, 116, 99)
SKIN_LIGHT = (223, 191, 172)
SKIN_RED = (186, 122, 106)
GRIME = (86, 68, 54)

HAIR = (58, 43, 34)
HAIR_LIGHT = (88, 66, 49)
HAIR_DARK = (34, 25, 20)

PELT = (74, 62, 51)
PELT_LIGHT = (110, 94, 76)
PELT_DARK = (46, 38, 31)

LOIN = (114, 92, 68)
LOIN_LIGHT = (146, 121, 92)
LOIN_DARK = (76, 60, 45)

NAIL = (168, 148, 120)
EYE_WHITE = (206, 198, 180)
EYE_IRIS = (74, 53, 32)
LIP = (158, 106, 94)
LIP_DARK = (118, 74, 66)
MOUTH_DARK = (72, 44, 40)

HAIR_PARTS = {"hair_top", "hair_fringe", "hair_back", "hair_tuft_right",
              "hair_tuft_left", "sideburn_right", "sideburn_left"}
PELT_PARTS = {"pelt_shoulder", "pelt_a", "pelt_b", "pelt_c", "pelt_back"}
LOIN_PARTS = {"loin_front", "loin_back", "loin_right", "loin_left",
              "loin_strip_a", "loin_strip_b", "loin_strip_c"}
# Hands, feet and the lower legs are caked in forest muck.
FILTHY_PARTS = {"hand_right_c", "hand_left_c", "thumb_right", "thumb_left",
                "foot_right_c", "foot_left_c", "toes_right", "toes_left",
                "shin_right_c", "shin_left_c"}


def kind_of(name):
    if name in HAIR_PARTS:
        return "hair"
    if name in PELT_PARTS:
        return "pelt"
    if name in LOIN_PARTS:
        return "loin"
    if name in ("eye_left", "eye_right"):
        return "eye"
    if name == "lip":
        return "lip"
    return "skin"


# --- painting -------------------------------------------------------------

def fill(img, x, y, w, h, color):
    px = img.load()
    for j in range(int(y), int(y + h)):
        for i in range(int(x), int(x + w)):
            if 0 <= i < img.width and 0 <= j < img.height:
                px[i, j] = color + (255,)


def paint_skin(img, x, y, w, h, name, face):
    """Blotchy pale hide: vertical shading, mottling, grime and bruise patches."""
    px = img.load()
    w, h = int(math.ceil(w)), int(math.ceil(h))
    filthy = name in FILTHY_PARTS
    for j in range(h):
        # Top-lit: the upper part of every limb and slab catches light.
        t = j / max(1, h - 1)
        base = lerp(SKIN_LIGHT, SKIN_DARK, 0.25 + 0.55 * t)
        for i in range(w):
            c = jitter(base, 7)
            # Broad mottling in low-frequency blobs.
            if rng.random() < 0.10:
                c = lerp(c, SKIN_RED, rng.uniform(0.2, 0.5))
            if rng.random() < 0.05:
                c = lerp(c, SKIN, 0.5)
            # Muck creeps up from the bottom of the filthy parts.
            if filthy:
                muck = max(0.0, 1.0 - (j / max(1.0, h * 0.75)))
                if rng.random() < 0.35 + 0.4 * muck:
                    c = lerp(c, GRIME, rng.uniform(0.25, 0.7) * (0.35 + muck))
            elif rng.random() < 0.06:
                c = lerp(c, GRIME, rng.uniform(0.15, 0.4))
            ix, iy = int(x) + i, int(y) + j
            if 0 <= ix < img.width and 0 <= iy < img.height:
                px[ix, iy] = c + (255,)


def paint_fur(img, x, y, w, h, base, light, dark, streak_chance):
    """Matted hair/pelt: vertical streaks of varying value."""
    px = img.load()
    w, h = int(math.ceil(w)), int(math.ceil(h))
    for i in range(w):
        # Each column is a strand with its own tone.
        strand = rng.random()
        col = lerp(dark, light, strand)
        for j in range(h):
            t = j / max(1, h - 1)
            c = lerp(col, dark, 0.15 + 0.35 * t)
            c = jitter(c, 6)
            if rng.random() < streak_chance:
                c = lerp(c, light, rng.uniform(0.2, 0.6))
            if rng.random() < 0.05:
                c = lerp(c, dark, 0.5)
            ix, iy = int(x) + i, int(y) + j
            if 0 <= ix < img.width and 0 <= iy < img.height:
                px[ix, iy] = c + (255,)


def paint_hide(img, x, y, w, h):
    """Tanned hide wrap: pelt strips of varying tone, banded and ragged.

    A flat fill reads as a black slab at this size, so the wrap is built from
    per-column strips with their own value plus horizontal banding, which gives
    the eye something to catch at Minecraft's texel density.
    """
    px = img.load()
    w, h = int(math.ceil(w)), int(math.ceil(h))
    # Each vertical strip of the wrap is a separate piece of hide.
    strip_w = max(2, w // 5)
    strips = [rng.uniform(0.0, 1.0) for _ in range(w // strip_w + 2)]
    # Two or three horizontal binding bands hold the wrap together.
    bands = sorted(rng.sample(range(1, max(2, h - 1)), min(2, max(1, h - 2))))
    for j in range(h):
        t = j / max(1, h - 1)
        for i in range(w):
            tone = strips[i // strip_w]
            base = lerp(lerp(LOIN_DARK, LOIN_LIGHT, tone), LOIN_DARK, 0.1 + 0.35 * t)
            c = jitter(base, 9)
            if rng.random() < 0.14:
                c = lerp(c, LOIN_LIGHT, rng.uniform(0.2, 0.5))
            if rng.random() < 0.10:
                c = lerp(c, LOIN_DARK, rng.uniform(0.3, 0.6))
            # Seams between strips.
            if i % strip_w == 0:
                c = lerp(c, LOIN_DARK, 0.55)
            # Binding bands run darker and flatter across the strips.
            if j in bands:
                c = jitter(lerp(PELT_DARK, LOIN, 0.35), 5)
            # Ragged hem: the bottom row bites into the strips unevenly.
            if j == h - 1 and rng.random() < 0.45:
                c = lerp(c, LOIN_DARK, 0.7)
            ix, iy = int(x) + i, int(y) + j
            if 0 <= ix < img.width and 0 <= iy < img.height:
                px[ix, iy] = c + (255,)


def face_rects(element):
    """Read the six baked box-UV rectangles straight off the element.

    Blockbench stores each face as [x1, y1, x2, y2] and may emit them flipped
    for mirrored faces, so normalise into (x, y, w, h).
    """
    out = {}
    for side, face in element.get("faces", {}).items():
        uv = face.get("uv")
        if not uv:
            continue
        x1, y1, x2, y2 = uv
        x, y = min(x1, x2), min(y1, y2)
        out[side] = (int(math.floor(x)), int(math.floor(y)),
                     int(math.ceil(abs(x2 - x1))), int(math.ceil(abs(y2 - y1))))
    return out


def main():
    with open(BBMODEL, "r", encoding="utf-8") as f:
        model = json.load(f)

    res = model["resolution"]
    img = Image.new("RGBA", (res["width"], res["height"]), (0, 0, 0, 0))

    # Paint in a fixed order so the RNG stream is stable across runs.
    elements = sorted(model["elements"], key=lambda e: e["name"])
    detail = {}

    for e in elements:
        name = e["name"]
        u, v = e.get("uv_offset", [0, 0])
        w = abs(e["to"][0] - e["from"][0])
        h = abs(e["to"][1] - e["from"][1])
        d = abs(e["to"][2] - e["from"][2])
        kind = kind_of(name)
        rects = face_rects(e)
        detail[name] = rects

        # Base fill over the whole footprint so no seam can show through.
        base = {"skin": SKIN_DARK, "hair": HAIR_DARK, "pelt": PELT_DARK,
                "loin": LOIN_DARK, "eye": EYE_WHITE, "lip": LIP}[kind]
        fill(img, u, v, 2 * math.ceil(d) + 2 * math.ceil(w), math.ceil(d) + math.ceil(h), base)

        for rect in rects.values():
            rx, ry, rw, rh = rect
            if rw <= 0 or rh <= 0:
                continue
            if kind == "skin":
                paint_skin(img, rx, ry, rw, rh, name, rect)
            elif kind == "hair":
                paint_fur(img, rx, ry, rw, rh, HAIR, HAIR_LIGHT, HAIR_DARK, 0.16)
            elif kind == "pelt":
                paint_fur(img, rx, ry, rw, rh, PELT, PELT_LIGHT, PELT_DARK, 0.22)
            elif kind == "loin":
                paint_hide(img, rx, ry, rw, rh)
            elif kind == "eye":
                fill(img, rx, ry, rw, rh, EYE_WHITE)
            elif kind == "lip":
                fill(img, rx, ry, rw, rh, LIP)

    px = img.load()

    def put(x, y, c):
        if 0 <= int(x) < img.width and 0 <= int(y) < img.height:
            px[int(x), int(y)] = c + (255,)

    # --- facial detail, painted onto the north (front) faces ---------------

    # Eyes: small, close-set, deep-set under the brow. Iris fills most of the
    # 4x2 front face with a dark pupil and one specular pixel.
    for eye in ("eye_left", "eye_right"):
        x, y, w, h = detail[eye]["north"]
        for j in range(int(h)):
            for i in range(int(w)):
                put(x + i, y + j, EYE_WHITE)
        put(x + 1, y, EYE_IRIS)
        put(x + 2, y, EYE_IRIS)
        put(x + 1, y + 1, (26, 18, 12))
        put(x + 2, y + 1, EYE_IRIS)
        put(x + 1, y, (150, 140, 122))

    # Nostrils: two dark slots in the underside of the upturned snout.
    x, y, w, h = detail["nose_tip"]["north"]
    for i in range(int(w)):
        for j in range(int(h)):
            put(x + i, y + j, jitter(SKIN_RED, 6))
    put(x + 1, y + int(h) - 1, (48, 30, 26))
    put(x + int(w) - 2, y + int(h) - 1, (48, 30, 26))
    x, y, w, h = detail["nose_tip"]["down"]
    for i in range(int(w)):
        for j in range(int(h)):
            put(x + i, y + j, (56, 36, 30) if 1 <= i <= int(w) - 2 else jitter(SKIN_DARK, 5))

    # Mouth line under the lip.
    x, y, w, h = detail["lip"]["north"]
    for i in range(int(w)):
        put(x + i, y, lerp(LIP, LIP_DARK, 0.5))
        if int(h) > 1:
            put(x + i, y + int(h) - 1, MOUTH_DARK)

    # Heavy brow: a dark shelf shadow along its lower edge.
    x, y, w, h = detail["brow"]["north"]
    for i in range(int(w)):
        put(x + i, y + int(h) - 1, lerp(SKIN_DARK, GRIME, 0.55))

    # Fingernails and toenails: pale caps on the front edge of the digits.
    for part in ("toes_right", "toes_left"):
        x, y, w, h = detail[part]["north"]
        for i in range(int(w)):
            if i % 2 == 0:
                put(x + i, y, NAIL)
                put(x + i, y + 1, lerp(NAIL, SKIN_DARK, 0.4))
    for part in ("thumb_right", "thumb_left"):
        x, y, w, h = detail[part]["up"]
        for i in range(int(w)):
            put(x + i, y, NAIL)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote {} ({}x{}), {} cubes".format(OUT, img.width, img.height, len(elements)))


if __name__ == "__main__":
    main()
