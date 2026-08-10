"""Generate the Basilisk's entity textures.

The UV layout below is the box-UV packing produced in Blockbench for
blockbench_models/basilisk.bbmodel. Face rectangles are derived from each
cube's (u, v) offset and its w/h/d footprint, using the standard box-UV
arrangement:

    up     (u + d,         v,     w, d)
    down   (u + d + w,     v,     w, d)
    east   (u,             v + d, d, h)
    north  (u + d,         v + d, w, h)
    west   (u + d + w,     v + d, d, h)
    south  (u + 2d + w,    v + d, w, h)

Two files come out of this:

    basilisk.png       the base skin. The eye cubes carry a ruined, gouged
                       socket, because that is what shows once the basilisk
                       has been blinded and the eye layer is dropped.
    basilisk_eyes.png  an emissive overlay holding only the living eyes. The
                       renderer adds it while the basilisk can still see.

The palette is deliberately near-black: a bright green snake reads as a toy.
Dorsal scales carry low-frequency saddle bands running down the length of the
body, which is what makes a real viper look like a viper. `BAND_PERIOD` is in
texture pixels and each cube passes a `band_off` so the bands line up across
segment seams.

Re-run after any change to the model's cube sizes, and re-pack the UVs first.
"""

import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "textures", "entity")

TEX_W, TEX_H = 512, 128
BAND_PERIOD = 13.0

LAYOUT = {
    "seg04_body": (0, 0, 22, 20, 16),
    "seg05_body": (77, 0, 22, 20, 16),
    "seg06_body": (154, 0, 21, 19, 16),
    "jaw_main": (229, 0, 18, 6, 28),
    "skull_rear": (322, 0, 22, 18, 16),
    "seg03_body": (399, 0, 19, 18, 16),
    "seg07_body": (0, 37, 19, 17, 16),
    "seg08_body": (71, 37, 17, 15, 16),
    "seg02_body": (138, 37, 16, 15, 16),
    "seg09_body": (203, 37, 14, 12, 16),
    "seg01_body": (264, 37, 13, 12, 16),
    "skull_mid": (323, 37, 17, 15, 12),
    "seg10_body": (382, 37, 11, 9, 16),
    "hood_l": (437, 37, 4, 13, 12),
    "hood_r": (470, 37, 4, 13, 12),
    "seg11_body": (0, 71, 8, 7, 16),
    "seg12_body": (49, 71, 6, 5, 16),
    "snout": (94, 71, 11, 11, 10),
    "brow_l": (137, 71, 7, 4, 15),
    "brow_r": (182, 71, 7, 4, 15),
    "tongue_base": (227, 71, 2, 1, 18),
    "seg13_body": (268, 71, 3, 3, 16),
    "crest_a": (307, 71, 3, 4, 14),
    "seg03_spine": (342, 71, 3, 3, 12),
    "seg04_spine": (373, 71, 3, 3, 12),
    "seg05_spine": (404, 71, 3, 3, 12),
    "seg06_spine": (435, 71, 3, 3, 12),
    "seg07_spine": (466, 71, 3, 3, 12),
    "seg01_spine": (0, 95, 2, 3, 12),
    "seg02_spine": (29, 95, 2, 3, 12),
    "jaw_mid": (58, 95, 12, 6, 8),
    "eye_l": (99, 95, 2, 6, 8),
    "eye_r": (120, 95, 2, 6, 8),
    "nose": (141, 95, 7, 7, 5),
    "horn_l": (166, 95, 3, 6, 5),
    "horn_r": (183, 95, 3, 6, 5),
    "fang_l": (200, 95, 2, 8, 3),
    "fang_r": (211, 95, 2, 8, 3),
    "tongue_fl": (222, 95, 1, 1, 8),
    "tongue_fr": (241, 95, 1, 1, 8),
    "lfang_l": (260, 95, 2, 5, 3),
    "lfang_r": (271, 95, 2, 5, 3),
}

DORSAL_DARK = (10, 20, 14)
DORSAL_MID = (22, 40, 26)
DORSAL_LIT = (38, 60, 38)
BAND_DARK = (5, 11, 8)
FLANK_DARK = (14, 26, 16)
FLANK_MID = (30, 46, 26)
FLANK_LIT = (52, 68, 36)
RIDGE_DARK = (6, 14, 10)
RIDGE_MID = (16, 30, 20)
RIDGE_LIT = (30, 48, 30)
VENTRAL_MID = (150, 146, 116)
VENTRAL_LIT = (172, 168, 136)
VENTRAL_DARK = (104, 100, 76)
MOUTH_DARK = (40, 12, 14)
GUM = (96, 30, 30)
TONGUE = (110, 28, 38)
FANG_MID = (222, 216, 196)
FANG_DARK = (150, 144, 122)
SOCKET_DARK = (16, 7, 6)
SOCKET_MID = (40, 16, 14)
EYE_IRIS = (236, 178, 20)
EYE_IRIS_LIT = (255, 228, 96)
CROSS_SECTION = (18, 26, 18)

# Segment cubes are 16 px deep and laid end to end, so a cube's index times 16
# keeps the saddle banding continuous from the neck to the tail tip.
BAND_OFFSETS = {}
for _i in range(1, 14):
    BAND_OFFSETS["seg%02d_body" % _i] = (_i - 1) * 16
    BAND_OFFSETS["seg%02d_spine" % _i] = (_i - 1) * 16 + 2
BAND_OFFSETS.update({"skull_rear": -16, "skull_mid": -30, "snout": -42, "nose": -50})


def faces(name):
    u, v, w, h, d = LAYOUT[name]
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def cell_noise(gx, gy):
    """Stable per-scale jitter, so re-running never reshuffles the skin."""
    n = (gx * 73856093) ^ (gy * 19349663)
    return ((n ^ (n >> 13)) & 1023) / 1023.0


def scales(px, rect, dark, base, lit, cell, shade_top=0.0, band_off=0,
           band_axis="y", band_strength=0.55):
    """Offset-row scales, each shaded like a small rounded plate, under bands.

    `band_axis` names the rect axis that runs along the body: "y" for up/down
    faces (where the rect is w x d) and "x" for the flanks (where it is d x h).
    """
    x0, y0, w, h = rect
    for y in range(h):
        row = y // cell
        xoff = (row % 2) * (cell // 2)
        vgrad = (y / max(1, h - 1)) if h > 1 else 0.0
        for x in range(w):
            gx = (x + xoff) // cell
            fx = (x + xoff) % cell
            fy = y % cell
            n = cell_noise(gx, row)
            # Bright at the scale's top-left, falling off to its bottom-right.
            t = 1.0 - (fx + fy) / max(1.0, 2.0 * (cell - 1))
            col = mix(dark, base, 0.35 + 0.45 * n)
            col = mix(col, lit, 0.55 * t * (0.45 + 0.55 * n))
            if fx == cell - 1 or fy == cell - 1:
                col = mix(col, dark, 0.5)
            if band_strength:
                pos = (y if band_axis == "y" else x) + band_off
                band = math.sin(pos * math.pi / BAND_PERIOD)
                if band > 0.45:
                    col = mix(col, BAND_DARK, band_strength * (band - 0.45) / 0.55)
            if shade_top:
                col = mix(col, dark, shade_top * (1.0 - vgrad))
            px[x0 + x, y0 + y] = col + (255,)


def scutes(px, rect, rng):
    """Belly plates: dull bone bands running across the body."""
    x0, y0, w, h = rect
    for y in range(h):
        band = y % 3
        for x in range(w):
            n = rng.random()
            if band == 0:
                col = mix(VENTRAL_DARK, VENTRAL_MID, 0.3 * n)
            else:
                col = mix(VENTRAL_MID, VENTRAL_LIT, 0.35 + 0.45 * n)
            if min(x, w - 1 - x) == 0:
                col = mix(col, VENTRAL_DARK, 0.55)
            px[x0 + x, y0 + y] = col + (255,)


def flat(px, rect, color, rng=None, jitter=0.0):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            col = color
            if rng is not None and jitter:
                col = mix(color, (0, 0, 0), jitter * rng.random())
            px[x0 + x, y0 + y] = col + (255,)


def vertical_gradient(px, rect, top, bottom, rng):
    x0, y0, w, h = rect
    for y in range(h):
        t = y / max(1, h - 1)
        for x in range(w):
            col = mix(mix(top, bottom, t), (0, 0, 0), 0.10 * rng.random())
            px[x0 + x, y0 + y] = col + (255,)


def draw_socket(px, rect, rng):
    """A clawed-out eye: a wet dark pit inside a torn rim."""
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            if min(x, w - 1 - x, y, h - 1 - y) == 0:
                col = mix(SOCKET_MID, DORSAL_DARK, 0.4 + 0.4 * rng.random())
            else:
                col = mix(SOCKET_DARK, SOCKET_MID, 0.5 * rng.random())
            px[x0 + x, y0 + y] = col + (255,)


def draw_eye(px, rect, rng):
    """Living eye: yellow iris around a vertical slit, dark lid all round."""
    x0, y0, w, h = rect
    cx = (w - 1) / 2.0
    cy = (h - 1) / 2.0
    for y in range(h):
        for x in range(w):
            # Lids only at the front and back corners. Rimming all four sides
            # splits the iris in two and it stops reading as one eye.
            if min(x, w - 1 - x) == 0:
                col = mix(SOCKET_MID, DORSAL_DARK, 0.55)
            else:
                r = math.hypot((x - cx) / max(1.0, cx), (y - cy) / max(1.0, cy))
                col = mix(EYE_IRIS_LIT, EYE_IRIS, min(1.0, r))
                col = mix(col, (0, 0, 0), 0.10 * rng.random())
            # A slit pupil: two pixels through the middle, pinching to one at
            # the top and bottom of the eye.
            narrow = y == 0 or y == h - 1
            if abs(x - cx) < (0.6 if narrow else 1.1):
                col = (8, 6, 5)
            px[x0 + x, y0 + y] = col + (255,)


def build():
    rng = random.Random(20260810)
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    eyes = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    epx = eyes.load()

    # --- spine segments and their dorsal ridges -------------------------------
    for name in LAYOUT:
        if not name.startswith("seg"):
            continue
        f = faces(name)
        off = BAND_OFFSETS.get(name, 0)
        ridge = name.endswith("_spine")
        if ridge:
            scales(px, f["up"], RIDGE_DARK, RIDGE_MID, RIDGE_LIT, 2, band_off=off)
            for side in ("east", "west"):
                scales(px, f[side], RIDGE_DARK, RIDGE_MID, RIDGE_LIT, 2,
                       band_off=off, band_axis="x")
            flat(px, f["down"], RIDGE_DARK, rng, 0.2)
        else:
            scales(px, f["up"], DORSAL_DARK, DORSAL_MID, DORSAL_LIT, 3, band_off=off)
            for side in ("east", "west"):
                scales(px, f[side], FLANK_DARK, FLANK_MID, FLANK_LIT, 3,
                       shade_top=0.55, band_off=off, band_axis="x",
                       band_strength=0.4)
            scutes(px, f["down"], rng)
        for side in ("north", "south"):
            flat(px, f[side], CROSS_SECTION, rng, 0.25)

    # --- skull, snout, nose: the wedge ---------------------------------------
    for name in ("skull_rear", "skull_mid", "snout", "nose"):
        f = faces(name)
        off = BAND_OFFSETS[name]
        cell = 3 if name.startswith("skull") else 2
        scales(px, f["up"], DORSAL_DARK, DORSAL_MID, DORSAL_LIT, cell, band_off=off)
        for side in ("east", "west"):
            scales(px, f[side], FLANK_DARK, FLANK_MID, FLANK_LIT, cell,
                   shade_top=0.45, band_off=off, band_axis="x", band_strength=0.35)
        # Each block of the wedge is wider than the one in front, so a ring of
        # its front face shows as cheek. That must read as scales, not mouth.
        scales(px, f["north"], FLANK_DARK, FLANK_MID, FLANK_LIT, cell,
               shade_top=0.4, band_strength=0.0)
        flat(px, f["south"], CROSS_SECTION, rng, 0.25)
        flat(px, f["down"], MOUTH_DARK if name != "nose" else GUM, rng, 0.3)

    # two nostril slits on the front of the nose
    nx, ny, nw, nh = faces("nose")["north"]
    for dx in (nw // 2 - 2, nw // 2 + 1):
        for dy in (nh // 3, nh // 3 + 1):
            if 0 <= dx < nw and 0 <= dy < nh:
                px[nx + dx, ny + dy] = (8, 12, 9, 255)

    # --- brow ridges, crest and horns ----------------------------------------
    for name in ("brow_l", "brow_r", "crest_a", "horn_l", "horn_r"):
        f = faces(name)
        for side in ("up", "east", "west", "north", "south"):
            scales(px, f[side], RIDGE_DARK, RIDGE_MID, RIDGE_LIT, 2, band_strength=0.0)
        flat(px, f["down"], RIDGE_DARK, rng, 0.3)

    # --- hood plates ----------------------------------------------------------
    for name in ("hood_l", "hood_r"):
        f = faces(name)
        for side in ("up", "north", "south"):
            scales(px, f[side], DORSAL_DARK, DORSAL_MID, DORSAL_LIT, 2, band_strength=0.0)
        for side in ("east", "west"):
            scales(px, f[side], FLANK_DARK, FLANK_MID, FLANK_LIT, 3, shade_top=0.5,
                   band_off=-16, band_axis="x", band_strength=0.45)
        flat(px, f["down"], VENTRAL_DARK, rng, 0.2)

    # --- jaw ------------------------------------------------------------------
    for name in ("jaw_main", "jaw_mid"):
        f = faces(name)
        flat(px, f["up"], GUM, rng, 0.35)              # floor of the mouth
        scutes(px, f["down"], rng)                     # chin plates
        for side in ("east", "west", "north"):
            vertical_gradient(px, f[side], FLANK_MID, VENTRAL_DARK, rng)
        flat(px, f["south"], MOUTH_DARK, rng, 0.3)

    # --- fangs ----------------------------------------------------------------
    for name in ("fang_l", "fang_r", "lfang_l", "lfang_r"):
        f = faces(name)
        for side in ("east", "west", "north", "south"):
            vertical_gradient(px, f[side], FANG_DARK, FANG_MID, rng)
        flat(px, f["up"], GUM, rng, 0.3)
        flat(px, f["down"], FANG_MID, rng, 0.15)

    # --- tongue ---------------------------------------------------------------
    for name in ("tongue_base", "tongue_fl", "tongue_fr"):
        f = faces(name)
        for side in ("up", "down", "east", "west", "north", "south"):
            flat(px, f[side], TONGUE, rng, 0.35)

    # --- eyes: gouged on the base, living on the emissive layer ---------------
    for name in ("eye_l", "eye_r"):
        f = faces(name)
        for side in ("up", "down", "north", "south"):
            flat(px, f[side], SOCKET_MID, rng, 0.4)
        for side in ("east", "west"):
            draw_socket(px, f[side], rng)
            draw_eye(epx, f[side], rng)

    os.makedirs(OUT_DIR, exist_ok=True)
    base_path = os.path.join(OUT_DIR, "basilisk.png")
    eyes_path = os.path.join(OUT_DIR, "basilisk_eyes.png")
    img.save(base_path)
    eyes.save(eyes_path)
    print("wrote", base_path)
    print("wrote", eyes_path)


if __name__ == "__main__":
    build()
