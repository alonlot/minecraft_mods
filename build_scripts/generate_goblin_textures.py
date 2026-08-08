"""Generate the Gringotts goblin entity textures (teller + vault guard).

The UV layout below is the box-UV packing produced when the model was built in
Blockbench (see blockbench_models/goblin.bbmodel). Each entry is
    name: (width, height, depth, u, v)
in model pixels, and the six faces of a cube occupy the standard Minecraft box
layout relative to (u, v):

        (u+d, v)      up      w x d
        (u+d+w, v)    down    w x d
        (u, v+d)      east    d x h      (+X side)
        (u+d, v+d)    north   w x h      (front, faces -Z)
        (u+d+w, v+d)  west    d x h      (-X side)
        (u+2d+w, v+d) south   w x h      (back)

Run from the repo root:  python build_scripts/generate_goblin_textures.py
"""

import os
import random

from PIL import Image, ImageDraw

TEX_W = TEX_H = 128

# name -> (w, h, d, u, v)
CUBES = {
    "skull": (8, 6, 7, 0, 0),
    "hair_back": (8, 10, 2, 31, 0),
    "hair_left": (2, 8, 4, 52, 0),
    "hair_right": (2, 8, 4, 65, 0),
    "torso": (9, 7, 5, 78, 0),
    "jaw": (6, 4, 6, 0, 14),
    "coat_skirt": (10, 4, 6, 25, 14),
    "left_sleeve_upper": (3, 5, 4, 58, 14),
    "right_sleeve_upper": (3, 5, 4, 73, 14),
    "collar": (5, 3, 5, 88, 14),
    "left_sleeve_lower": (3, 3, 4, 109, 14),
    "right_sleeve_lower": (3, 3, 4, 0, 25),
    "left_breeches": (3, 4, 3, 15, 25),
    "left_shoe": (4, 2, 5, 28, 25),
    "right_breeches": (3, 4, 3, 47, 25),
    "right_shoe": (4, 2, 5, 60, 25),
    "ear_left_base": (4, 4, 2, 79, 25),
    "ear_right_base": (4, 4, 2, 92, 25),
    "coat_tail": (9, 4, 2, 105, 25),
    "nose_mid": (2, 2, 3, 0, 33),
    "left_cuff": (3, 1, 4, 11, 33),
    "left_hand": (2, 2, 3, 26, 33),
    "right_cuff": (3, 1, 4, 37, 33),
    "right_hand": (2, 2, 3, 52, 33),
    "left_sock": (3, 2, 3, 63, 33),
    "right_sock": (3, 2, 3, 76, 33),
    "nose_tip": (2, 2, 2, 89, 33),
    "ear_left_mid": (3, 3, 1, 98, 33),
    "ear_right_mid": (3, 3, 1, 107, 33),
    "bowtie": (3, 2, 2, 116, 33),
    "nose_bridge": (2, 2, 1, 0, 39),
    "brow": (8, 1, 1, 7, 39),
    "spectacles": (9, 1, 1, 26, 39),
    "ear_left_tip": (3, 1, 1, 47, 39),
    "ear_right_tip": (3, 1, 1, 56, 39),
}

FACE_ORDER = ("up", "down", "east", "north", "west", "south")

# --- palette -----------------------------------------------------------------
# Ruddy, weathered skin rather than the usual green goblin - matches the films,
# where Gringotts goblins are pink-grey and heavily lined.
SKIN = (196, 144, 122)
SKIN_LIT = (214, 164, 140)
SKIN_DARK = (158, 109, 88)
SKIN_DEEP = (120, 78, 62)
EAR_INNER = (207, 152, 137)

HAIR = (226, 224, 216)
HAIR_DARK = (188, 186, 178)
HAIR_DEEP = (152, 150, 144)

COAT = (23, 23, 27)
COAT_LIT = (38, 38, 45)
COAT_DEEP = (13, 13, 16)
VEST = (30, 29, 35)

SHIRT = (233, 233, 226)
SHIRT_DARK = (200, 200, 192)

BRASS = (170, 137, 58)
BRASS_LIT = (208, 175, 88)
BRASS_DEEP = (110, 88, 34)

SHOE = (19, 19, 23)
SHOE_LIT = (34, 34, 40)
BUCKLE = (184, 188, 196)

EYE_SOCKET = (104, 68, 54)
EYE_WHITE = (214, 205, 190)
EYE_PUPIL = (36, 24, 18)
MOUTH = (92, 56, 46)

LEATHER = (86, 58, 36)
LEATHER_LIT = (116, 82, 52)
LEATHER_DEEP = (58, 38, 23)
IRON = (128, 132, 140)
IRON_LIT = (166, 170, 178)
IRON_DEEP = (86, 90, 98)


def faces(name):
    """Return {face: (x, y, w, h)} pixel rects for a cube's box UV."""
    w, h, d, u, v = CUBES[name]
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


class Tex:
    def __init__(self):
        self.img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def box(self, x, y, w, h, color):
        if w <= 0 or h <= 0:
            return
        self.d.rectangle([x, y, x + w - 1, y + h - 1], fill=color)

    def px(self, x, y, color):
        if 0 <= x < TEX_W and 0 <= y < TEX_H:
            self.d.point((x, y), fill=color)

    def fill_cube(self, name, base, top=None, bottom=None, back=None):
        """Flat-fill every face of a cube, with optional per-face overrides."""
        f = faces(name)
        for face in FACE_ORDER:
            x, y, w, h = f[face]
            color = base
            if face == "up" and top is not None:
                color = top
            elif face == "down" and bottom is not None:
                color = bottom
            elif face == "south" and back is not None:
                color = back
            self.box(x, y, w, h, color)

    def shade_cube(self, name, lit, dark, amount=1):
        """Darken the bottom rows and lighten the top row of the side faces so
        the form reads at Minecraft's tiny scale."""
        f = faces(name)
        for face in ("east", "north", "west", "south"):
            x, y, w, h = f[face]
            if h <= 1:
                continue
            self.box(x, y, w, amount, lit)
            self.box(x, y + h - amount, w, amount, dark)

    def speckle(self, name, colors, density, seed):
        """Sprinkle deterministic noise over a cube's faces (skin pores, cloth
        weave, hair strands)."""
        rng = random.Random(seed)
        f = faces(name)
        for face in FACE_ORDER:
            x, y, w, h = f[face]
            for iy in range(h):
                for ix in range(w):
                    if rng.random() < density:
                        self.px(x + ix, y + iy, rng.choice(colors))


def paint_common(t):
    """Everything that is identical between the teller and the guard: the body,
    the head, and the face."""

    # ---- skull ---------------------------------------------------------------
    t.fill_cube("skull", SKIN, top=SKIN_LIT, bottom=SKIN_DEEP)
    t.shade_cube("skull", SKIN_LIT, SKIN_DARK)
    x, y, w, h = faces("skull")["up"]
    # Bald, shiny crown: a highlight blob offset toward the front of the head.
    t.box(x + 2, y + 2, w - 4, 3, SKIN_LIT)
    t.box(x + 3, y + 3, 2, 1, (226, 182, 158))

    fx, fy, fw, fh = faces("skull")["north"]
    # Forehead creases - the goblin is ancient and permanently unimpressed.
    t.box(fx + 1, fy + 1, fw - 2, 1, SKIN_DARK)
    t.px(fx + 2, fy + 2, SKIN_DARK)
    t.px(fx + 5, fy + 2, SKIN_DARK)
    # Eyes sit in the one clear row between the jutting brow ridge (which
    # covers the row above) and the spectacle bar (which covers the row below).
    t.box(fx, fy + 3, fw, 1, SKIN_DARK)
    for ex in (fx + 1, fx + 5):
        t.box(ex, fy + 3, 2, 1, EYE_SOCKET)
        t.px(ex, fy + 3, EYE_WHITE)
        t.px(ex + 1, fy + 3, EYE_PUPIL)
    # Temple hollows and sunken cheeks below the eyes.
    t.px(fx, fy + 3, SKIN_DEEP)
    t.px(fx + fw - 1, fy + 3, SKIN_DEEP)
    t.box(fx, fy + fh - 1, fw, 1, SKIN_DARK)
    t.speckle("skull", [SKIN_DARK, SKIN_LIT], 0.06, 11)

    # ---- brow ridge ----------------------------------------------------------
    t.fill_cube("brow", SKIN_DARK, top=SKIN, bottom=SKIN_DEEP)
    bx, by, bw, bh = faces("brow")["north"]
    t.box(bx, by, bw, bh, SKIN_DEEP)
    t.box(bx + 1, by, 2, 1, SKIN_DARK)
    t.box(bx + 5, by, 2, 1, SKIN_DARK)

    # ---- jaw / lower face ----------------------------------------------------
    t.fill_cube("jaw", SKIN, bottom=SKIN_DEEP)
    t.shade_cube("jaw", SKIN_LIT, SKIN_DARK)
    jx, jy, jw, jh = faces("jaw")["north"]
    # Sunken cheeks either side of the muzzle.
    t.box(jx, jy, 1, jh, SKIN_DARK)
    t.box(jx + jw - 1, jy, 1, jh, SKIN_DARK)
    # Downturned, disapproving mouth - the defining Gringotts expression. It
    # lives on the bottom row, which is the only part of the jaw the overhanging
    # nose does not cover.
    t.box(jx + 1, jy + jh - 1, jw - 2, 1, MOUTH)
    t.px(jx, jy + jh - 2, MOUTH)
    t.px(jx + jw - 1, jy + jh - 2, MOUTH)
    # Nasolabial folds running down from the nose to the mouth corners.
    t.px(jx + 1, jy + 1, SKIN_DARK)
    t.px(jx + jw - 2, jy + 1, SKIN_DARK)
    t.px(jx, jy + jh - 1, SKIN_DEEP)
    t.px(jx + jw - 1, jy + jh - 1, SKIN_DEEP)
    t.speckle("jaw", [SKIN_DARK], 0.05, 23)

    # ---- nose ----------------------------------------------------------------
    for part in ("nose_bridge", "nose_mid", "nose_tip"):
        t.fill_cube(part, SKIN, top=SKIN_LIT, bottom=SKIN_DARK)
        t.shade_cube(part, SKIN_LIT, SKIN_DARK)
    # Bulbous tip: darker underside with two nostrils.
    nx, ny, nw, nh = faces("nose_tip")["down"]
    t.box(nx, ny, nw, nh, SKIN_DARK)
    t.px(nx + 1, ny + 1, SKIN_DEEP)
    t.px(nx + nw - 2, ny + 1, SKIN_DEEP)
    tx, ty, tw, th = faces("nose_tip")["north"]
    t.box(tx + 1, ty, 1, 1, SKIN_LIT)
    t.speckle("nose_tip", [SKIN_DARK, SKIN_LIT], 0.10, 31)
    t.speckle("nose_mid", [SKIN_DARK], 0.08, 37)

    # ---- ears ----------------------------------------------------------------
    for side in ("left", "right"):
        for seg, seed in ((f"ear_{side}_base", 41), (f"ear_{side}_mid", 43), (f"ear_{side}_tip", 47)):
            t.fill_cube(seg, SKIN, top=SKIN_LIT, bottom=SKIN_DARK)
            t.shade_cube(seg, SKIN_LIT, SKIN_DARK)
            # Thin cartilage rim around the outer edge.
            x, y, w, h = faces(seg)["south"]
            t.box(x, y, w, 1, SKIN_DARK)
            t.box(x, y + h - 1, w, 1, SKIN_DARK)
            t.speckle(seg, [SKIN_DARK], 0.07, seed)
        # Inner ear is pinker and catches light.
        x, y, w, h = faces(f"ear_{side}_base")["north"]
        t.box(x, y + 1, w, h - 2, EAR_INNER)
        t.box(x + 1, y + 1, w - 2, 1, (222, 172, 158))
        x, y, w, h = faces(f"ear_{side}_mid")["north"]
        t.box(x, y + 1, w, h - 2, EAR_INNER)

    # ---- hair: bald on top, long white wisps at the back and sides -----------
    for part, seed in (("hair_back", 53), ("hair_left", 59), ("hair_right", 61)):
        t.fill_cube(part, HAIR, top=HAIR_DARK, bottom=HAIR_DEEP)
        t.shade_cube(part, (240, 238, 232), HAIR_DEEP)
        t.speckle(part, [HAIR_DARK, HAIR_DEEP, (244, 242, 238)], 0.22, seed)
    # Ragged, thinning ends on the long back hair.
    x, y, w, h = faces("hair_back")["south"]
    for ix in range(w):
        if ix % 2 == 0:
            t.px(x + ix, y + h - 1, HAIR_DEEP)
    for part in ("hair_left", "hair_right"):
        x, y, w, h = faces(part)["east"]
        for iy in range(h):
            if iy % 3 == 0:
                t.px(x, y + iy, HAIR_DEEP)

    # ---- spectacles ----------------------------------------------------------
    t.fill_cube("spectacles", BRASS, top=BRASS_LIT, bottom=BRASS_DEEP)
    sx, sy, sw, sh = faces("spectacles")["north"]
    t.box(sx, sy, sw, sh, BRASS)
    # Two lenses with a bridge between them; arms run out to the temples.
    t.box(sx + 1, sy, 2, 1, (150, 160, 170))
    t.box(sx + 6, sy, 2, 1, (150, 160, 170))
    t.px(sx + 4, sy, BRASS_LIT)

    # ---- hands ---------------------------------------------------------------
    for side, seed in (("left", 67), ("right", 71)):
        part = f"{side}_hand"
        t.fill_cube(part, SKIN, top=SKIN_LIT, bottom=SKIN_DARK)
        t.shade_cube(part, SKIN_LIT, SKIN_DARK)
        # Long spindly fingers: vertical dark grooves on the front and back.
        for face in ("north", "south"):
            x, y, w, h = faces(part)[face]
            for ix in range(w):
                if ix % 2 == 1:
                    t.box(x + ix, y + 1, 1, h - 1, SKIN_DARK)
        x, y, w, h = faces(part)["down"]
        t.box(x, y, w, h, SKIN_DARK)
        t.speckle(part, [SKIN_DEEP], 0.08, seed)


def paint_teller(t):
    """Black Victorian frock coat, wing collar, bow tie, brass buttons."""
    t.fill_cube("torso", COAT, top=COAT_DEEP, bottom=COAT_DEEP)
    t.shade_cube("torso", COAT_LIT, COAT_DEEP)
    x, y, w, h = faces("torso")["north"]
    # Waistcoat panel showing between the open coat fronts.
    t.box(x + 2, y, w - 4, h, VEST)
    # Lapels: two diagonal highlights peeling away from the collar.
    for i in range(3):
        t.px(x + 2 - min(i, 2) + i, y + i, COAT_LIT)
        t.px(x + w - 3 - i + min(i, 2), y + i, COAT_LIT)
    t.box(x + 1, y, 1, h, COAT_LIT)
    t.box(x + w - 2, y, 1, h, COAT_LIT)
    # Shirt front peeking out at the throat.
    t.box(x + 3, y, w - 6, 2, SHIRT)
    t.px(x + 3, y + 1, SHIRT_DARK)
    # Three brass buttons down the waistcoat.
    for i, by in enumerate((y + 2, y + 4, y + 6)):
        if by < y + h:
            t.px(x + w // 2, by, BRASS)
            t.px(x + w // 2, by, BRASS_LIT if i == 0 else BRASS)
    # Coat vent up the back.
    x, y, w, h = faces("torso")["south"]
    t.box(x + w // 2, y, 1, h, COAT_DEEP)
    t.box(x, y, 1, h, COAT_LIT)
    t.box(x + w - 1, y, 1, h, COAT_LIT)

    for part in ("coat_skirt", "coat_tail"):
        t.fill_cube(part, COAT, top=COAT_DEEP, bottom=COAT_DEEP)
        t.shade_cube(part, COAT_LIT, COAT_DEEP)
    # Split coat tails at the back.
    x, y, w, h = faces("coat_tail")["south"]
    t.box(x + w // 2, y, 1, h, COAT_DEEP)
    x, y, w, h = faces("coat_skirt")["north"]
    t.box(x, y, w, 1, COAT_LIT)

    # Wing collar and black silk bow tie.
    t.fill_cube("collar", SHIRT, top=SHIRT_DARK, bottom=SHIRT_DARK)
    t.shade_cube("collar", (245, 245, 240), SHIRT_DARK)
    x, y, w, h = faces("collar")["north"]
    t.box(x + 1, y, w - 2, 1, SHIRT_DARK)

    t.fill_cube("bowtie", COAT, top=COAT_LIT, bottom=COAT_DEEP)
    x, y, w, h = faces("bowtie")["north"]
    t.box(x, y, w, h, COAT)
    t.box(x + w // 2 - 1, y, 2, h, COAT_LIT)  # knot
    t.px(x, y, COAT_DEEP)
    t.px(x + w - 1, y, COAT_DEEP)

    for side in ("left", "right"):
        t.fill_cube(f"{side}_sleeve_upper", COAT, top=COAT_DEEP, bottom=COAT_DEEP)
        t.shade_cube(f"{side}_sleeve_upper", COAT_LIT, COAT_DEEP)
        t.fill_cube(f"{side}_sleeve_lower", COAT, top=COAT_DEEP, bottom=COAT_DEEP)
        t.shade_cube(f"{side}_sleeve_lower", COAT_LIT, COAT_DEEP)
        # White shirt cuff at the wrist.
        t.fill_cube(f"{side}_cuff", SHIRT, top=SHIRT_DARK, bottom=SHIRT_DARK)
        x, y, w, h = faces(f"{side}_cuff")["north"]
        t.px(x + w - 1, y, BRASS)  # cufflink

    paint_legs(t, breeches=COAT, breeches_lit=COAT_LIT, breeches_deep=COAT_DEEP)


def paint_guard(t):
    """Same goblin, kitted out for the vaults: leather jerkin, iron shoulder."""
    t.fill_cube("torso", LEATHER, top=LEATHER_DEEP, bottom=LEATHER_DEEP)
    t.shade_cube("torso", LEATHER_LIT, LEATHER_DEEP)
    x, y, w, h = faces("torso")["north"]
    # Laced jerkin front.
    t.box(x + w // 2 - 1, y, 2, h, LEATHER_DEEP)
    for iy in range(y + 1, y + h, 2):
        t.px(x + w // 2 - 1, iy, (196, 176, 140))
        t.px(x + w // 2, iy, (196, 176, 140))
    # Iron breastplate band across the chest.
    t.box(x + 1, y + 2, w - 2, 2, IRON)
    t.box(x + 1, y + 2, w - 2, 1, IRON_LIT)
    t.box(x + 1, y + 3, w - 2, 1, IRON_DEEP)
    for bx in (x + 2, x + w - 3):
        t.px(bx, y + 2, BRASS)
    x, y, w, h = faces("torso")["south"]
    t.box(x + 1, y + 2, w - 2, 2, IRON)
    t.box(x + 1, y + 3, w - 2, 1, IRON_DEEP)
    t.speckle("torso", [LEATHER_DEEP, LEATHER_LIT], 0.10, 83)

    for part in ("coat_skirt", "coat_tail"):
        t.fill_cube(part, LEATHER, top=LEATHER_DEEP, bottom=LEATHER_DEEP)
        t.shade_cube(part, LEATHER_LIT, LEATHER_DEEP)
        t.speckle(part, [LEATHER_DEEP], 0.10, 89)
    # Studded belt line along the top of the skirt.
    x, y, w, h = faces("coat_skirt")["north"]
    t.box(x, y, w, 1, LEATHER_DEEP)
    for ix in range(0, w, 3):
        t.px(x + ix, y, BRASS)

    # No wing collar or bow tie - a plain leather gorget instead.
    t.fill_cube("collar", LEATHER_DEEP, top=LEATHER, bottom=LEATHER_DEEP)
    t.shade_cube("collar", LEATHER, LEATHER_DEEP)
    t.fill_cube("bowtie", IRON, top=IRON_LIT, bottom=IRON_DEEP)
    x, y, w, h = faces("bowtie")["north"]
    t.box(x, y, w, h, IRON)
    t.box(x + 1, y, w - 2, 1, IRON_LIT)
    t.px(x + w // 2, y + h - 1, BRASS)

    for side in ("left", "right"):
        t.fill_cube(f"{side}_sleeve_upper", LEATHER, top=LEATHER_DEEP, bottom=LEATHER_DEEP)
        t.shade_cube(f"{side}_sleeve_upper", LEATHER_LIT, LEATHER_DEEP)
        # Iron pauldron on the outer shoulder.
        x, y, w, h = faces(f"{side}_sleeve_upper")["up"]
        t.box(x, y, w, h, IRON)
        t.box(x, y, w, 1, IRON_LIT)
        for face in ("east", "west"):
            x, y, w, h = faces(f"{side}_sleeve_upper")[face]
            t.box(x, y, w, 2, IRON)
            t.box(x, y, w, 1, IRON_LIT)
        t.fill_cube(f"{side}_sleeve_lower", LEATHER, top=LEATHER_DEEP, bottom=LEATHER_DEEP)
        t.shade_cube(f"{side}_sleeve_lower", LEATHER_LIT, LEATHER_DEEP)
        # Iron vambrace instead of a shirt cuff.
        t.fill_cube(f"{side}_cuff", IRON, top=IRON_LIT, bottom=IRON_DEEP)
        x, y, w, h = faces(f"{side}_cuff")["north"]
        t.box(x, y, w, h, IRON)
        t.px(x + w // 2, y, BRASS)

    paint_legs(t, breeches=LEATHER, breeches_lit=LEATHER_LIT, breeches_deep=LEATHER_DEEP)


def paint_legs(t, breeches, breeches_lit, breeches_deep):
    """Knee breeches, white stockings and buckled shoes - shared silhouette."""
    for side in ("left", "right"):
        part = f"{side}_breeches"
        t.fill_cube(part, breeches, top=breeches_deep, bottom=breeches_deep)
        t.shade_cube(part, breeches_lit, breeches_deep)
        # Cuffed hem just below the knee.
        for face in ("east", "north", "west", "south"):
            x, y, w, h = faces(part)[face]
            t.box(x, y + h - 1, w, 1, breeches_deep)
            t.box(x, y + h - 2, w, 1, breeches_lit)

        part = f"{side}_sock"
        t.fill_cube(part, SHIRT, top=SHIRT_DARK, bottom=SHIRT_DARK)
        t.shade_cube(part, (245, 245, 240), SHIRT_DARK)

        part = f"{side}_shoe"
        t.fill_cube(part, SHOE, top=SHOE_LIT, bottom=COAT_DEEP)
        t.shade_cube(part, SHOE_LIT, COAT_DEEP)
        # Square silver buckle on the instep.
        x, y, w, h = faces(part)["up"]
        t.box(x + 1, y + 1, w - 2, 1, BUCKLE)
        t.px(x + w // 2, y + 1, (220, 224, 232))
        # Polished toe cap.
        x, y, w, h = faces(part)["north"]
        t.box(x, y, w, 1, SHOE_LIT)


def build(kind):
    t = Tex()
    paint_common(t)
    if kind == "teller":
        paint_teller(t)
    else:
        paint_guard(t)
    return t.img


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out_dir = os.path.join(
        root, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "entity"
    )
    os.makedirs(out_dir, exist_ok=True)
    for kind, filename in (("teller", "goblin.png"), ("guard", "goblin_guard.png")):
        path = os.path.join(out_dir, filename)
        build(kind).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
