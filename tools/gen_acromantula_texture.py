"""Generate src/main/resources/assets/hogwartsmagic/textures/entity/acromantula.png
matching the AcromantulaModel UV layout. Re-run after model changes."""

import os
import random
from PIL import Image, ImageDraw

random.seed(42)

W = H = 128
OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "src", "main", "resources", "assets", "hogwartsmagic",
    "textures", "entity", "acromantula.png",
)

CHITIN_DARK = (28, 22, 16, 255)
CHITIN_MID = (44, 34, 26, 255)
CHITIN_LIGHT = (72, 56, 42, 255)
ABDOMEN_BASE = (40, 24, 22, 255)
ABDOMEN_MARK = (140, 26, 26, 255)
EYE_RED = (200, 30, 30, 255)
FANG_BASE = (60, 48, 36, 255)
FANG_TIP = (228, 210, 170, 255)
LEG_DARK = (32, 24, 18, 255)
LEG_LIGHT = (60, 46, 34, 255)
HAIR = (12, 8, 6, 255)


def fill_rect(img, x, y, w, h, color):
    for px in range(x, x + w):
        for py in range(y, y + h):
            img.putpixel((px, py), color)


def speckle(img, x, y, w, h, color, density=0.18):
    for px in range(x, x + w):
        for py in range(y, y + h):
            if random.random() < density:
                img.putpixel((px, py), color)


def noise_block(img, x, y, w, h, base, hi, lo):
    """Fill region with base + per-pixel jitter towards hi/lo for chitin texture."""
    for px in range(x, x + w):
        for py in range(y, y + h):
            t = random.random()
            if t < 0.18:
                img.putpixel((px, py), lo)
            elif t > 0.82:
                img.putpixel((px, py), hi)
            else:
                img.putpixel((px, py), base)


def box_faces(u, v, lx, ly, lz):
    """Return the (up, down, west, north, east, south) face rects for a Minecraft box."""
    return {
        "up":    (u + lz, v, lx, lz),
        "down":  (u + lz + lx, v, lx, lz),
        "west":  (u, v + lz, lz, ly),
        "north": (u + lz, v + lz, lx, ly),
        "east":  (u + lz + lx, v + lz, lz, ly),
        "south": (u + lz * 2 + lx, v + lz, lx, ly),
    }


def paint_chitin(img, faces, base=CHITIN_MID, hi=CHITIN_LIGHT, lo=CHITIN_DARK,
                 hair_density=0.05):
    for name, (x, y, w, h) in faces.items():
        noise_block(img, x, y, w, h, base, hi, lo)
        # Outline the face so seams read distinctly.
        outline_rect(img, x, y, w, h, lo)
        speckle(img, x + 1, y + 1, max(1, w - 2), max(1, h - 2), HAIR, hair_density)


def outline_rect(img, x, y, w, h, color):
    if w <= 0 or h <= 0:
        return
    for px in range(x, x + w):
        img.putpixel((px, y), color)
        img.putpixel((px, y + h - 1), color)
    for py in range(y, y + h):
        img.putpixel((x, py), color)
        img.putpixel((x + w - 1, py), color)


def paint_abdomen(img, faces):
    paint_chitin(img, faces, base=ABDOMEN_BASE, hi=(80, 38, 32, 255), lo=(20, 12, 10, 255), hair_density=0.10)
    # Hourglass marking on the up face (top of abdomen).
    ux, uy, uw, uh = faces["up"]
    cx = ux + uw // 2
    for i in range(uh):
        t = i / max(1, uh - 1)
        wid = int(round((1 - abs(2 * t - 1)) * (uw // 2 - 1))) + 1
        for dx in range(-wid, wid + 1):
            px = cx + dx
            py = uy + i
            if ux <= px < ux + uw and uy <= py < uy + uh:
                img.putpixel((px, py), ABDOMEN_MARK)


def paint_head(img, faces):
    paint_chitin(img, faces, base=CHITIN_DARK, hi=CHITIN_MID, lo=(10, 6, 4, 255), hair_density=0.06)
    # Eyes on the front face (north).
    nx, ny, nw, nh = faces["north"]
    if nw >= 4 and nh >= 4:
        eye_y = ny + nh // 3
        for ex in (nx + nw // 4, nx + nw - nw // 4 - 1):
            img.putpixel((ex, eye_y), EYE_RED)
            if ex + 1 < nx + nw:
                img.putpixel((ex + 1, eye_y), EYE_RED)
            if eye_y + 1 < ny + nh:
                img.putpixel((ex, eye_y + 1), EYE_RED)
        # Cluster of smaller eyes above.
        for _ in range(4):
            ex = nx + random.randint(1, nw - 2)
            ey = ny + random.randint(1, max(1, nh // 3))
            img.putpixel((ex, ey), EYE_RED)


def paint_fangs(img, faces):
    paint_chitin(img, faces, base=FANG_BASE, hi=(120, 100, 76, 255), lo=(30, 22, 16, 255), hair_density=0.0)
    # Tip the bottom row (down face) and lower portion of side faces with ivory.
    for name in ("west", "north", "east", "south"):
        x, y, w, h = faces[name]
        if h >= 3:
            for px in range(x, x + w):
                img.putpixel((px, y + h - 1), FANG_TIP)
                img.putpixel((px, y + h - 2), (180, 160, 124, 255))
    dx, dy, dw, dh = faces["down"]
    fill_rect(img, dx, dy, dw, dh, FANG_TIP)


def paint_leg(img, faces, base=LEG_DARK, hi=LEG_LIGHT, lo=(16, 12, 8, 255)):
    paint_chitin(img, faces, base=base, hi=hi, lo=lo, hair_density=0.12)


def main():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))

    # Thorax (0, 0) 10×8×12
    paint_chitin(img, box_faces(0, 0, 10, 8, 12), hair_density=0.08)

    # Abdomen (0, 20) 14×12×18
    paint_abdomen(img, box_faces(0, 20, 14, 12, 18))

    # Head (64, 20) 8×8×6
    paint_head(img, box_faces(64, 20, 8, 8, 6))

    # Fang (92, 20) 2×6×3
    paint_fangs(img, box_faces(92, 20, 2, 6, 3))

    # Leg upper (0, 52) 3×3×16
    paint_leg(img, box_faces(0, 52, 3, 3, 16))

    # Leg lower (40, 52) 2×2×20 — slightly darker, with stripe.
    lower_faces = box_faces(40, 52, 2, 2, 20)
    paint_leg(img, lower_faces, base=(24, 18, 12, 255), hi=(48, 36, 26, 255))
    # Hint of bristle stripes along the tibia (up face).
    ux, uy, uw, uh = lower_faces["up"]
    for offset in (uh // 4, uh // 2, 3 * uh // 4):
        for dx in range(uw):
            img.putpixel((ux + dx, uy + offset), HAIR)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
