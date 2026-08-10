"""Generate the House Elf entity textures (bound + freed).

The UV layout below is the box-UV packing produced when the model was built in
Blockbench (see blockbench_models/house_elf.bbmodel). Each entry is
    name: (width, height, depth, u, v)
in model pixels, and the six faces of a cube occupy the standard Minecraft box
layout relative to (u, v):

        (u+d, v)      up      w x d
        (u+d+w, v)    down    w x d
        (u, v+d)      east    d x h      (+X side)
        (u+d, v+d)    north   w x h      (front, faces -Z)
        (u+d+w, v+d)  west    d x h      (-X side)
        (u+2d+w, v+d) south   w x h      (back)

The UV space stays 128x128 (that is what LayerDefinition.create is given), but
the PNG is written at SCALE times that, so the enormous eyes have enough texels
to read as eyes rather than as two dark smudges.

Run from the repo root:  python build_scripts/generate_house_elf_textures.py
"""

import os
import random

from PIL import Image

UV_W = UV_H = 128
SCALE = 4
TEX_W, TEX_H = UV_W * SCALE, UV_H * SCALE

# name -> (w, h, d, u, v)
CUBES = {
    "skull": (8, 6, 7, 0, 0),
    "pillowcase": (6, 6, 5, 31, 0),
    "torso": (6, 6, 4, 54, 0),
    "jaw": (6, 3, 6, 75, 0),
    "left_thigh": (2, 5, 3, 100, 0),
    "left_shin": (2, 6, 2, 111, 0),
    "right_thigh": (2, 5, 3, 0, 14),
    "right_shin": (2, 6, 2, 11, 14),
    "cranium": (7, 1, 6, 20, 14),
    "left_upper_arm": (2, 5, 2, 47, 14),
    "right_upper_arm": (2, 5, 2, 56, 14),
    "left_foot": (3, 2, 5, 65, 14),
    "right_foot": (3, 2, 5, 82, 14),
    "ear_left_tip": (3, 4, 2, 99, 14),
    "ear_right_tip": (3, 4, 2, 110, 14),
    "pc_collar": (7, 1, 5, 0, 23),
    "left_forearm": (2, 4, 2, 25, 23),
    "right_forearm": (2, 4, 2, 34, 23),
    "nose": (2, 3, 2, 43, 23),
    "chin": (3, 2, 3, 52, 23),
    "ear_left_base": (3, 3, 2, 65, 23),
    "ear_left_mid": (3, 3, 2, 76, 23),
    "ear_right_base": (3, 3, 2, 87, 23),
    "ear_right_mid": (3, 3, 2, 98, 23),
    "neck": (2, 3, 2, 109, 23),
    "left_hand": (2, 3, 2, 118, 23),
    "right_hand": (2, 3, 2, 0, 30),
    "left_knee": (3, 2, 3, 9, 30),
    "right_knee": (3, 2, 3, 22, 30),
    "nose_tip": (1, 2, 2, 35, 30),
    "collar": (5, 1, 3, 42, 30),
    "pc_strap_left": (2, 2, 2, 59, 30),
    "pc_strap_right": (2, 2, 2, 68, 30),
    "pc_rag": (2, 3, 1, 77, 30),
    "eye_left": (3, 2, 1, 84, 30),
    "eye_right": (3, 2, 1, 93, 30),
    "nose_bridge": (2, 2, 1, 102, 30),
    "brow": (8, 1, 1, 109, 30),
}

# --- palette -----------------------------------------------------------------
# Sallow grey-tan rather than green: in the films a house elf's skin is dusty
# and bloodless, closer to weathered parchment than to a goblin's ruddiness.
SKIN = (158, 142, 118)
SKIN_LIT = (182, 166, 140)
SKIN_DARK = (126, 111, 92)
SKIN_DEEP = (96, 84, 69)
SKIN_SHADOW = (74, 64, 54)
EAR_INNER = (178, 144, 132)
EAR_INNER_DARK = (140, 108, 100)

SCLERA = (234, 232, 224)
IRIS = (118, 156, 150)
IRIS_DARK = (78, 112, 110)
PUPIL = (22, 22, 28)

# A pillowcase that has been worn for years and washed never.
CLOTH = (206, 201, 186)
CLOTH_LIT = (228, 224, 212)
CLOTH_DARK = (170, 165, 150)
CLOTH_DEEP = (132, 127, 114)
STAIN = (146, 134, 108)

# The freed elf's mismatched knitwear.
JUMPER = (150, 74, 58)
JUMPER_LIT = (176, 96, 76)
JUMPER_DARK = (116, 54, 42)
JUMPER_DEEP = (88, 40, 32)
STRIPE = (196, 172, 96)
SOCK_LEFT = (168, 60, 60)
SOCK_RIGHT = (74, 128, 84)

FACES = ("up", "down", "east", "north", "west", "south")

CLOTH_CUBES = {"pillowcase", "pc_collar", "pc_strap_left", "pc_strap_right", "pc_rag"}
EAR_CUBES = {
    "ear_left_base", "ear_left_mid", "ear_left_tip",
    "ear_right_base", "ear_right_mid", "ear_right_tip",
}


def face_rect(name, face):
    """Return the (x, y, w, h) of one face of a cube, in model pixels."""
    w, h, d, u, v = CUBES[name]
    if face == "up":
        return (u + d, v, w, d)
    if face == "down":
        return (u + d + w, v, w, d)
    if face == "east":
        return (u, v + d, d, h)
    if face == "north":
        return (u + d, v + d, w, h)
    if face == "west":
        return (u + d + w, v + d, d, h)
    if face == "south":
        return (u + 2 * d + w, v + d, w, h)
    raise ValueError(face)


class Tex:
    """A texture canvas addressed in model-pixel space, written at SCALE."""

    def __init__(self):
        self.img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
        self.px = self.img.load()

    def dot(self, x, y, colour):
        """Paint one model pixel (a SCALE x SCALE block of texels)."""
        for sy in range(SCALE):
            for sx in range(SCALE):
                tx, ty = int(x) * SCALE + sx, int(y) * SCALE + sy
                if 0 <= tx < TEX_W and 0 <= ty < TEX_H:
                    self.px[tx, ty] = colour + (255,)

    def subdot(self, x, y, colour, sx, sy):
        """Paint a single texel inside model pixel (x, y) at sub-offset."""
        tx, ty = int(x) * SCALE + sx, int(y) * SCALE + sy
        if 0 <= tx < TEX_W and 0 <= ty < TEX_H:
            self.px[tx, ty] = colour + (255,)

    def fill(self, rect, colour):
        x, y, w, h = rect
        for j in range(h):
            for i in range(w):
                self.dot(x + i, y + j, colour)

    def mottle(self, rect, light, dark, rng, density=0.18):
        """Break up a flat fill with a sparse dusting of lighter/darker texels."""
        x, y, w, h = rect
        for j in range(h * SCALE):
            for i in range(w * SCALE):
                if rng.random() < density:
                    c = light if rng.random() < 0.5 else dark
                    tx, ty = x * SCALE + i, y * SCALE + j
                    if 0 <= tx < TEX_W and 0 <= ty < TEX_H:
                        self.px[tx, ty] = c + (255,)

    def hline(self, rect, row, colour, x0=0, x1=None, sub=0):
        """A one-texel horizontal line across a face, at model-row `row`."""
        x, y, w, h = rect
        x1 = w if x1 is None else x1
        for i in range((x1 - x0) * SCALE):
            self.subdot(x + x0 + i // SCALE, y + row, colour, i % SCALE, sub)

    def shade_box(self, name, base, lit, dark, deep, rng):
        """Standard six-face shading: sky above, ground below, mid on the sides."""
        self.fill(face_rect(name, "up"), lit)
        self.fill(face_rect(name, "down"), deep)
        self.fill(face_rect(name, "east"), base)
        self.fill(face_rect(name, "north"), base)
        self.fill(face_rect(name, "west"), base)
        self.fill(face_rect(name, "south"), dark)
        for f in FACES:
            self.mottle(face_rect(name, f), lit, dark, rng, 0.12)


def paint_skin(tex, rng, names):
    for name in names:
        tex.shade_box(name, SKIN, SKIN_LIT, SKIN_DARK, SKIN_DEEP, rng)


def paint_ears(tex, rng):
    """Ears: pink and translucent-looking on the inner face, grey on the back."""
    for name in EAR_CUBES:
        tex.shade_box(name, SKIN, SKIN_LIT, SKIN_DARK, SKIN_DEEP, rng)
        north = face_rect(name, "north")
        tex.fill(north, EAR_INNER)
        tex.mottle(north, EAR_INNER, EAR_INNER_DARK, rng, 0.16)
        # A cartilage ridge running the length of the inner ear.
        w, h, d, u, v = CUBES[name]
        for j in range(h):
            tex.subdot(north[0] + max(0, w // 2 - 1), north[1] + j,
                       EAR_INNER_DARK, 1, 2)
            tex.subdot(north[0] + max(0, w // 2 - 1), north[1] + j,
                       EAR_INNER_DARK, 2, 2)


def paint_eye(tex, name, rng, flip):
    """One enormous eye: sclera, wide pale-green iris, blown pupil, catchlight.

    `flip` mirrors the iris placement so the two eyes look very slightly
    outward, which is what sells the anxious stare.
    """
    w, h, d, u, v = CUBES[name]
    x, y, fw, fh = face_rect(name, "north")
    # Lid and socket around the eye stay skin.
    for f in ("up", "down", "east", "west", "south"):
        tex.fill(face_rect(name, f), SKIN_DARK)
    tex.fill((x, y, fw, fh), SCLERA)

    # Work in texels across the whole face for a round eye.
    tw, th = fw * SCALE, fh * SCALE
    cx = tw * (0.56 if not flip else 0.44)
    cy = th * 0.5
    r_iris = min(tw, th) * 0.42
    r_pupil = r_iris * 0.52
    for j in range(th):
        for i in range(tw):
            dx, dy = (i + 0.5) - cx, (j + 0.5) - cy
            dist = (dx * dx + dy * dy) ** 0.5
            if dist <= r_pupil:
                c = PUPIL
            elif dist <= r_iris:
                c = IRIS_DARK if dist > r_iris * 0.82 else IRIS
            elif j == 0 or j == th - 1:
                c = SKIN_SHADOW
            else:
                continue
            tex.px[x * SCALE + i, y * SCALE + j] = c + (255,)
    # Catchlight, up and to one side.
    hx = int(cx - r_iris * 0.35)
    hy = int(cy - r_iris * 0.40)
    for j in range(2):
        for i in range(2):
            tx, ty = x * SCALE + hx + i, y * SCALE + hy + j
            if 0 <= tx < TEX_W and 0 <= ty < TEX_H:
                tex.px[tx, ty] = (250, 250, 246, 255)


def paint_face_details(tex, rng):
    """Brow shelf, mouth, nose shading, forehead lines."""
    # Heavy brow, darker than the rest of the skin and shadowed underneath.
    for f in FACES:
        tex.fill(face_rect("brow", f), SKIN_DARK)
    tex.fill(face_rect("brow", "north"), SKIN_DARK)
    tex.fill(face_rect("brow", "down"), SKIN_DEEP)

    # A small, thin-lipped, downturned mouth. It must stay narrow: run it the
    # full width of the jaw and the elf reads as a letterbox.
    jx, jy, jw, jh = face_rect("jaw", "north")
    span = jw * SCALE
    for i in range(span):
        frac = i / float(span - 1)
        if not 0.30 <= frac <= 0.70:
            continue
        dip = 0 if 0.40 < frac < 0.60 else 1
        tex.subdot(jx + i // SCALE, jy + 1, SKIN_DEEP, i % SCALE, dip)

    # Nose: keep the underside dark so the hook reads in profile.
    tex.fill(face_rect("nose", "down"), SKIN_SHADOW)
    tex.fill(face_rect("nose_tip", "down"), SKIN_SHADOW)
    tex.fill(face_rect("nose_bridge", "north"), SKIN_LIT)

    # Two worry lines across the forehead.
    sx, sy, sw, sh = face_rect("skull", "north")
    for row, inset in ((1, 1), (2, 2)):
        for i in range((sw - inset * 2) * SCALE):
            tex.subdot(sx + inset + i // SCALE, sy + row, SKIN_DARK, i % SCALE, 2)

    # Hollow cheeks either side of the jaw.
    for side in ("east", "west"):
        r = face_rect("jaw", side)
        tex.mottle(r, SKIN_DARK, SKIN_DEEP, rng, 0.35)


def paint_hands_feet(tex, rng):
    """Knuckle and toe separations, so the long fingers and flat feet read."""
    for name in ("left_hand", "right_hand"):
        r = face_rect(name, "north")
        w = CUBES[name][0]
        for i in range(1, w):
            for j in range(r[3] * SCALE):
                tex.subdot(r[0] + i - 1, r[1] + j // SCALE, SKIN_DEEP,
                           SCALE - 1, j % SCALE)
    for name in ("left_foot", "right_foot"):
        r = face_rect(name, "up")
        w, h, d = CUBES[name][:3]
        for i in range(1, w):
            for j in range(d * SCALE):
                tex.subdot(r[0] + i - 1, r[1] + j // SCALE, SKIN_DEEP,
                           SCALE - 1, j % SCALE)


def paint_cloth(tex, rng, base, lit, dark, deep, stains):
    for name in CLOTH_CUBES:
        tex.shade_box(name, base, lit, dark, deep, rng)
    if not stains:
        return
    # Grime: a few irregular blotches on the front and back of the sack.
    for face in ("north", "south", "east", "west"):
        x, y, w, h = face_rect("pillowcase", face)
        for _ in range(5):
            bx = rng.randrange(0, max(1, w * SCALE - 6))
            by = rng.randrange(0, max(1, h * SCALE - 6))
            for j in range(rng.randint(3, 6)):
                for i in range(rng.randint(3, 7)):
                    if rng.random() < 0.55:
                        tx, ty = x * SCALE + bx + i, y * SCALE + by + j
                        if 0 <= tx < TEX_W and 0 <= ty < TEX_H:
                            tex.px[tx, ty] = STAIN + (255,)
    # Frayed lower edge.
    for face in ("north", "south", "east", "west"):
        x, y, w, h = face_rect("pillowcase", face)
        for i in range(w * SCALE):
            if rng.random() < 0.5:
                tex.subdot(x + i // SCALE, y + h - 1, deep, i % SCALE, SCALE - 1)


def paint_jumper_knit(tex, rng):
    """Horizontal knit rows and one bright stripe on the freed elf's jumper."""
    for face in ("north", "south", "east", "west"):
        x, y, w, h = face_rect("pillowcase", face)
        for j in range(h * SCALE):
            if j % 2:
                continue
            for i in range(w * SCALE):
                tx, ty = x * SCALE + i, y * SCALE + j
                if 0 <= tx < TEX_W and 0 <= ty < TEX_H:
                    tex.px[tx, ty] = JUMPER_DARK + (255,)
        band = int(h * SCALE * 0.42)
        for j in range(band, min(band + 3, h * SCALE)):
            for i in range(w * SCALE):
                tx, ty = x * SCALE + i, y * SCALE + j
                if 0 <= tx < TEX_W and 0 <= ty < TEX_H:
                    tex.px[tx, ty] = STRIPE + (255,)


def paint_socks(tex, rng):
    """Dobby's mismatched socks, one on each foot."""
    for name, colour in (("left_foot", SOCK_LEFT), ("right_foot", SOCK_RIGHT)):
        dark = tuple(max(0, c - 40) for c in colour)
        lit = tuple(min(255, c + 28) for c in colour)
        tex.shade_box(name, colour, lit, dark, dark, rng)


def build(free):
    rng = random.Random(20260810 if not free else 20260811)
    tex = Tex()

    skin_cubes = [n for n in CUBES
                  if n not in CLOTH_CUBES and n not in EAR_CUBES
                  and n not in ("eye_left", "eye_right")]
    if free:
        skin_cubes = [n for n in skin_cubes if n not in ("left_foot", "right_foot")]

    paint_skin(tex, rng, skin_cubes)
    paint_ears(tex, rng)
    paint_eye(tex, "eye_left", rng, flip=False)
    paint_eye(tex, "eye_right", rng, flip=True)
    paint_face_details(tex, rng)
    paint_hands_feet(tex, rng)

    if free:
        paint_cloth(tex, rng, JUMPER, JUMPER_LIT, JUMPER_DARK, JUMPER_DEEP,
                    stains=False)
        paint_jumper_knit(tex, rng)
        paint_socks(tex, rng)
    else:
        paint_cloth(tex, rng, CLOTH, CLOTH_LIT, CLOTH_DARK, CLOTH_DEEP,
                    stains=True)
    return tex.img


def main():
    out_dir = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "src", "main", "resources", "assets", "hogwartsmagic", "textures", "entity",
    )
    os.makedirs(out_dir, exist_ok=True)
    for free, fname in ((False, "house_elf.png"), (True, "house_elf_free.png")):
        img = build(free)
        dest = os.path.join(out_dir, fname)
        img.save(dest)
        print("wrote", dest, img.size)


if __name__ == "__main__":
    main()
