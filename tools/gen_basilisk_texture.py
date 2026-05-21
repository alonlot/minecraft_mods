"""Generate src/main/resources/assets/hogwartsmagic/textures/entity/basilisk.png
matching the BasiliskModel UV layout. Re-run after model changes."""

import os
import random
from PIL import Image

random.seed(7)

W, H = 128, 64
OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "src", "main", "resources", "assets", "hogwartsmagic",
    "textures", "entity", "basilisk.png",
)

SCALE_DARK = (18, 38, 16, 255)
SCALE_MID = (36, 80, 34, 255)
SCALE_LIGHT = (78, 140, 64, 255)
SCALE_HIGHLIGHT = (140, 190, 100, 255)
BELLY = (200, 200, 130, 255)
BELLY_SHADOW = (140, 140, 88, 255)
EYE_YELLOW = (240, 220, 70, 255)
EYE_PUPIL = (10, 8, 4, 255)
FANG_IVORY = (230, 215, 175, 255)
FANG_DARK = (90, 78, 56, 255)
TONGUE_RED = (180, 30, 30, 255)


def fill(img, x, y, w, h, color):
    for px in range(x, min(x + w, W)):
        for py in range(y, min(y + h, H)):
            img.putpixel((px, py), color)


def outline(img, x, y, w, h, color):
    if w <= 0 or h <= 0:
        return
    for px in range(x, min(x + w, W)):
        if 0 <= y < H: img.putpixel((px, y), color)
        if 0 <= y + h - 1 < H: img.putpixel((px, y + h - 1), color)
    for py in range(y, min(y + h, H)):
        if 0 <= x < W: img.putpixel((x, py), color)
        if 0 <= x + w - 1 < W: img.putpixel((x + w - 1, py), color)


def scale_pattern(img, x, y, w, h, dark, mid, light, hi, density=0.08):
    """Paint a diamond-scale pattern over a rectangle."""
    for px in range(x, min(x + w, W)):
        for py in range(y, min(y + h, H)):
            rx = px - x
            ry = py - y
            # Diamond pattern: pixel "in scale" boundary on alternating diagonals.
            on_edge = (rx + ry) % 3 == 0 or (rx - ry) % 3 == 0
            r = random.random()
            if on_edge:
                col = dark
            elif r < density:
                col = hi
            elif r < density + 0.18:
                col = light
            else:
                col = mid
            img.putpixel((px, py), col)


def belly_pattern(img, x, y, w, h):
    for px in range(x, min(x + w, W)):
        for py in range(y, min(y + h, H)):
            on_edge = (px + py) % 4 == 0
            col = BELLY_SHADOW if on_edge else BELLY
            img.putpixel((px, py), col)


def box_faces(u, v, lx, ly, lz):
    return {
        "up":    (u + lz, v, lx, lz),
        "down":  (u + lz + lx, v, lx, lz),
        "west":  (u, v + lz, lz, ly),
        "north": (u + lz, v + lz, lx, ly),
        "east":  (u + lz + lx, v + lz, lz, ly),
        "south": (u + lz * 2 + lx, v + lz, lx, ly),
    }


def paint_scales(img, faces, dark=SCALE_DARK, mid=SCALE_MID, light=SCALE_LIGHT, hi=SCALE_HIGHLIGHT):
    # All side faces get scale pattern. Bottom face gets belly pattern.
    for name in ("up", "west", "north", "east", "south"):
        x, y, w, h = faces[name]
        scale_pattern(img, x, y, w, h, dark, mid, light, hi)
        outline(img, x, y, w, h, dark)
    bx, by, bw, bh = faces["down"]
    belly_pattern(img, bx, by, bw, bh)
    outline(img, bx, by, bw, bh, BELLY_SHADOW)


def paint_head(img, faces):
    paint_scales(img, faces)
    # Glowing eye markers on the up face (top of head) — bright yellow spots with dark slit pupils.
    ux, uy, uw, uh = faces["up"]
    # Two eyes near the front-half of the up face, symmetric around midline.
    cx_left = ux + max(1, uw // 4)
    cx_right = ux + uw - max(1, uw // 4) - 1
    eye_y = uy + max(1, uh // 3)
    for cx in (cx_left, cx_right):
        # 3x2 eye blob
        for ex in range(max(ux, cx - 1), min(ux + uw, cx + 2)):
            for ey in range(max(uy, eye_y - 1), min(uy + uh, eye_y + 1)):
                img.putpixel((ex, ey), EYE_YELLOW)
        # Vertical slit pupil center
        if ux <= cx < ux + uw and uy <= eye_y < uy + uh:
            img.putpixel((cx, eye_y), EYE_PUPIL)


def paint_snout(img, faces):
    paint_scales(img, faces, dark=(22, 44, 18, 255), mid=(48, 90, 40, 255))


def paint_jaw(img, faces):
    # Jaw inside is fleshy pink-ish; outside is scale.
    for name in ("west", "north", "east", "south"):
        x, y, w, h = faces[name]
        scale_pattern(img, x, y, w, h, SCALE_DARK, SCALE_MID, SCALE_LIGHT, SCALE_HIGHLIGHT)
        outline(img, x, y, w, h, SCALE_DARK)
    # Top of jaw (which faces UP into the mouth interior when the jaw is open) is mouth-pink.
    ux, uy, uw, uh = faces["up"]
    fill(img, ux, uy, uw, uh, (180, 90, 90, 255))
    outline(img, ux, uy, uw, uh, (110, 50, 50, 255))
    # Underside of jaw — belly pattern.
    bx, by, bw, bh = faces["down"]
    belly_pattern(img, bx, by, bw, bh)


def paint_fang(img, faces):
    # Fangs are tiny 1x3x1 boxes. Most faces get ivory; tip darker.
    for name in ("up", "west", "north", "east", "south"):
        x, y, w, h = faces[name]
        fill(img, x, y, w, h, FANG_IVORY)
        if h >= 2:
            # darken the bottom row (fang tip)
            for px in range(x, min(x + w, W)):
                py = y + h - 1
                if 0 <= py < H:
                    img.putpixel((px, py), FANG_DARK)
    bx, by, bw, bh = faces["down"]
    fill(img, bx, by, bw, bh, FANG_DARK)


def paint_crest(img, faces):
    # Bony crest ridges on top of head — slightly redder/darker scales.
    paint_scales(img, faces,
                 dark=(34, 22, 14, 255),
                 mid=(68, 44, 26, 255),
                 light=(108, 76, 44, 255),
                 hi=(160, 116, 70, 255))


def paint_tongue(img, x, y, w, h):
    fill(img, x, y, w, h, TONGUE_RED)
    outline(img, x, y, w, h, (110, 16, 16, 255))


def main():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # Head (0, 0) 9x6x13
    paint_head(img, box_faces(0, 0, 9, 6, 13))
    # Snout (0, 19) 3x2x5 — but watch: box_faces would extend beyond y=64 if lz*2 too large. Snout small enough.
    paint_snout(img, box_faces(0, 19, 3, 2, 5))
    # Head crest (36, 0) 5x1x6
    paint_crest(img, box_faces(36, 0, 5, 1, 6))
    # Jaw (36, 8) 8x2x12
    paint_jaw(img, box_faces(36, 8, 8, 2, 12))
    # Fangs (0, 27) and (4, 27) 1x3x1 — both at same y, two different x offsets
    paint_fang(img, box_faces(0, 27, 1, 3, 1))
    paint_fang(img, box_faces(4, 27, 1, 3, 1))
    # Neck (0, 24) — but wait, neck would overlap with fangs at y=24? Let me check.
    # Neck texOffs(0, 24) 7x7x14 → bbox (0, 24) to (2*7+2*14, 24+7+14) = (42, 45). Overlaps with fangs at y=24-30.
    # The model file already has this overlap; we just paint what's expected.
    paint_scales(img, box_faces(0, 24, 7, 7, 14))
    # Body1 (0, 42) 10x8x18
    paint_scales(img, box_faces(0, 42, 10, 8, 18))
    # Body2 (50, 42) 8x6x17
    paint_scales(img, box_faces(50, 42, 8, 6, 17))
    # Tail1 (36, 13) 6x5x17 — overlaps with jaw/fangs region but matches model
    paint_scales(img, box_faces(36, 13, 6, 5, 17))
    # Tail2 (24, 24) 4x4x18 — overlaps with neck region
    paint_scales(img, box_faces(24, 24, 4, 4, 18))
    # Tongue (anywhere reasonable) — we'll add a procedurally-extracted tongue stripe at (110, 0) 2x4 for the new tongue part
    paint_tongue(img, 110, 0, 4, 6)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
