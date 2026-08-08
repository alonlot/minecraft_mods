"""Generate the Dementor entity texture.

The model (blockbench_models/dementor.bbmodel) uses box UV, so every cube
occupies a deterministic footprint of 2*(depth+width) by (depth+height) texels
at its own uv_offset. LAYOUT below is that packing, dumped straight out of
Blockbench; regenerate it there and re-run this script after any size change.

Two materials:
  * cloth - the black robe, "as if soaked and rotting in water"
  * flesh - the grey, scabbed skin of the face and hands

Rag pieces get their bottom edge chewed away with real alpha so the hem is torn
rather than a clean rectangle. Entity rendering uses entityCutoutNoCull, which
discards transparent texels, so the holes come through in game.
"""

import os
import random

from PIL import Image

random.seed(1993)  # Prisoner of Azkaban

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "hogwartsmagic",
    "textures", "entity", "dementor.png",
)

SIZE = 256

# name, u, v, width, height, depth
LAYOUT = [
    ("tendril_f3", 0, 0, 3, 43, 1),
    ("tendril_b2", 8, 0, 3, 42, 1),
    ("tendril_r2", 16, 0, 1, 40, 3),
    ("tendril_l2", 24, 0, 1, 39, 3),
    ("tendril_f1", 32, 0, 3, 36, 1),
    ("tendril_b4", 40, 0, 3, 35, 1),
    ("tendril_r1", 48, 0, 1, 27, 3),
    ("tendril_b1", 56, 0, 3, 28, 1),
    ("tendril_l1", 64, 0, 1, 25, 3),
    ("mantle_rag_l", 72, 0, 1, 22, 5),
    ("mantle_rag_r", 84, 0, 1, 22, 5),
    ("tendril_f4", 96, 0, 3, 26, 1),
    ("cloak_upper", 104, 0, 14, 17, 9),
    ("tendril_i3", 150, 0, 3, 24, 1),
    ("cloak_mid", 158, 0, 19, 12, 12),
    ("tendril_f2", 220, 0, 3, 23, 1),
    ("tendril_l3", 228, 0, 1, 21, 3),
    ("tendril_i5", 236, 0, 3, 23, 1),
    ("tendril_b3", 244, 0, 3, 22, 1),
    ("tendril_i1", 0, 44, 3, 21, 1),
    ("tendril_f5", 8, 44, 2, 20, 1),
    ("tendril_r3", 14, 44, 1, 19, 2),
    ("tendril_i4", 20, 44, 3, 19, 1),
    ("cloak_tail", 28, 44, 12, 11, 8),
    ("cloak_tail_tip", 68, 44, 8, 14, 5),
    ("fold_l", 94, 44, 3, 12, 7),
    ("fold_r", 114, 44, 3, 12, 7),
    ("tendril_b5", 134, 44, 2, 18, 1),
    ("chest", 140, 44, 7, 13, 5),
    ("hood_side_l", 164, 44, 3, 10, 8),
    ("hood_side_r", 186, 44, 3, 10, 8),
    ("tendril_i6", 208, 44, 2, 17, 1),
    ("shroud", 0, 66, 16, 8, 9),
    ("tendril_i2", 50, 66, 2, 16, 1),
    ("sleeve_upper_l", 56, 66, 4, 11, 4),
    ("sleeve_upper_r", 72, 66, 4, 11, 4),
    ("sleeve_rag_l", 88, 66, 3, 14, 1),
    ("sleeve_rag_r", 96, 66, 3, 14, 1),
    ("hood_top", 104, 66, 13, 5, 9),
    ("hood_veil", 148, 66, 9, 12, 2),
    ("sleeve_lower_l", 170, 66, 3, 10, 4),
    ("sleeve_lower_r", 184, 66, 3, 10, 4),
    ("hood_crown", 198, 66, 6, 8, 5),
    ("hood_drape_l", 220, 66, 3, 10, 3),
    ("hood_drape_r", 232, 66, 3, 10, 3),
    ("hood_back", 0, 83, 8, 10, 2),
    ("finger_l2", 20, 83, 1, 11, 1),
    ("finger_r2", 24, 83, 1, 11, 1),
    ("hood_cheek_l", 28, 83, 3, 9, 2),
    ("hood_cheek_r", 38, 83, 3, 9, 2),
    ("finger_l1", 48, 83, 1, 10, 1),
    ("finger_l3", 52, 83, 1, 10, 1),
    ("finger_r1", 56, 83, 1, 10, 1),
    ("finger_r3", 60, 83, 1, 10, 1),
    ("face", 64, 83, 8, 7, 3),
    ("hood_peak", 86, 83, 4, 5, 4),
    ("jaw", 102, 83, 8, 5, 3),
    ("palm_l", 124, 83, 3, 4, 2),
    ("palm_r", 134, 83, 3, 4, 2),
    ("thumb_l", 144, 83, 1, 5, 1),
    ("thumb_r", 148, 83, 1, 5, 1),
    ("hood_brim", 152, 83, 13, 3, 2),
]

FLESH_PARTS = {"face", "jaw", "palm_l", "palm_r", "thumb_l", "thumb_r"}
FLESH_PREFIXES = ("finger_",)

# Cloth panels whose lower edge is a torn hem.
RAGGED = {
    "sleeve_rag_l", "sleeve_rag_r", "mantle_rag_l", "mantle_rag_r",
    "cloak_tail_tip",
}
RAGGED_PREFIXES = ("tendril_",)

TRANSPARENT = (0, 0, 0, 0)


def is_flesh(name):
    return name in FLESH_PARTS or name.startswith(FLESH_PREFIXES)


def is_ragged(name):
    return name in RAGGED or name.startswith(RAGGED_PREFIXES)


def clamp(v):
    return max(0, min(255, int(v)))


def faces(u, v, w, h, d):
    """Box-UV face rectangles as (x, y, width, height).

    The four side faces sit contiguously on the row at v+d, in the order
    east, north, west, south - which is what lets a torn hem run continuously
    around the whole cube.
    """
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def cloth_pixel(x, y, h):
    """Wet, rotting black cloth.

    A cold blue-grey base, vertical fold streaks, and grime that pools toward
    the bottom of every panel.
    """
    base = 19
    # Vertical fold banding - fabric hangs in creases.
    fold = 7.0 * (0.5 + 0.5 * __import__("math").cos(x * 1.7 + (x // 3)))
    # Grime gradient: darker at the hem.
    grime = -9.0 * (y / max(1.0, h - 1.0))
    noise = random.gauss(0.0, 4.0)
    v = base + fold + grime + noise
    # Occasional near-black rot blotch.
    if random.random() < 0.10:
        v -= random.uniform(6.0, 13.0)
    # Rare damp highlight catching the light.
    if random.random() < 0.035:
        v += random.uniform(10.0, 20.0)
    return (clamp(v * 0.88), clamp(v * 0.92), clamp(v * 1.06), 255)


def flesh_pixel():
    """Grey, scabbed, drowned-looking skin.

    The face is only ever exposed mid-Kiss, so this is pitched bright enough to
    jump out of a black hood - a cold grey-green, not a healthy tone.
    """
    v = random.gauss(104.0, 10.0)
    if random.random() < 0.20:  # scab
        v -= random.uniform(20.0, 40.0)
    elif random.random() < 0.06:  # taut shine over bone
        v += random.uniform(12.0, 22.0)
    return (clamp(v * 0.99), clamp(v * 1.00), clamp(v * 0.88), 255)


def fill(img, rect, fn):
    x0, y0, w, h = rect
    px = img.load()
    for j in range(h):
        for i in range(w):
            px[x0 + i, y0 + j] = fn(i, j)


def draw_cloth(img, name, u, v, w, h, d):
    f = faces(u, v, w, h, d)
    for key, rect in f.items():
        fill(img, rect, lambda i, j, hh=rect[3]: cloth_pixel(i, j, hh))

    if not is_ragged(name):
        return

    # One continuous torn profile around the four side faces. They are laid out
    # contiguously starting at u on the row v+d, spanning 2*(d+w) texels, so a
    # single walk across the strip cuts a hem that wraps the whole cube.
    px = img.load()
    strip_x = u
    strip_y = v + d
    strip_w = 2 * (d + w)
    # A tendril is eaten away over most of its length; a hem only at the edge.
    limit = max(1, int(h * (0.62 if name.startswith("tendril_") else 0.5)))
    depth = limit // 3
    for i in range(strip_w):
        depth += random.choice((-3, -2, -1, -1, 0, 0, 1, 1, 2, 3))
        depth = max(0, min(limit, depth))
        for j in range(depth):
            px[strip_x + i, strip_y + h - 1 - j] = TRANSPARENT
        # Fray the surviving edge texel so the cut does not read as a clean line.
        if depth < h and random.random() < 0.5:
            ex, ey = strip_x + i, strip_y + h - 1 - depth
            r, g, b, a = px[ex, ey]
            px[ex, ey] = (clamp(r * 1.5), clamp(g * 1.5), clamp(b * 1.45), a)

    # The underside of a torn hem is never seen and no longer exists anyway.
    fill(img, f["down"], lambda i, j: TRANSPARENT)


def draw_flesh(img, name, u, v, w, h, d):
    for rect in faces(u, v, w, h, d).values():
        fill(img, rect, lambda i, j: flesh_pixel())


# Face pixel maps, drawn on the north (front) face. Six texels across is all a
# box-UV head gets, so every feature has to survive at one texel per model unit:
# keep the value noise tight and let contrast do the work.
#   S taut skin   s shadowed skin   O empty socket
#   M gaping maw  r wet maw rim
FACE_FRONT = [
    "SSSSSSSS",   # brow ridge
    "SOOOOOOS",   # one unbroken band of shadow - gaunt, not a pair of eyes
    "SOOsSOOS",   # sockets deepen either side of a thin ridge of bone
    "sSMMMMSs",   # the maw is already opening this high up the face
    "SMMMMMMS",
    "SMMMMMMS",
    "sMMMMMMs",
]

JAW_FRONT = [
    "sMMMMMMs",   # the hole carries straight on down across the jaw...
    "SMMMMMMS",
    "SrMMMMrS",
    "SSrMMrSS",
    "SSSSSSSS",   # ...and only closes at the very bottom
]


def tint(value, mult, sigma):
    v = random.gauss(value, sigma)
    return (clamp(v * mult[0]), clamp(v * mult[1]), clamp(v * mult[2]), 255)


FACE_COLORS = {
    "S": lambda: tint(104, (1.02, 0.97, 0.90), 5.0),
    "s": lambda: tint(68, (1.02, 0.97, 0.90), 4.0),
    "O": lambda: tint(20, (1.00, 0.95, 0.92), 3.0),
    "M": lambda: tint(7, (1.00, 0.92, 0.95), 2.0),
    "r": lambda: tint(128, (1.03, 0.94, 0.88), 6.0),
}


def stamp(img, rect, rows):
    x0, y0, w, h = rect
    px = img.load()
    for j in range(min(h, len(rows))):
        row = rows[j]
        for i in range(min(w, len(row))):
            px[x0 + i, y0 + j] = FACE_COLORS[row[i]]()


def main():
    img = Image.new("RGBA", (SIZE, SIZE), TRANSPARENT)

    for name, u, v, w, h, d in LAYOUT:
        if is_flesh(name):
            draw_flesh(img, name, u, v, w, h, d)
        else:
            draw_cloth(img, name, u, v, w, h, d)

    by_name = {row[0]: row for row in LAYOUT}

    name, u, v, w, h, d = by_name["face"]
    stamp(img, faces(u, v, w, h, d)["north"], FACE_FRONT)

    name, u, v, w, h, d = by_name["jaw"]
    stamp(img, faces(u, v, w, h, d)["north"], JAW_FRONT)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
