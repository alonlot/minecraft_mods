"""Generate the Pensieve memory skins (64x64, standard modern player layout).

Memory scenes put client-only puppet players in a memory; each one wears one of these skins, named in a scene as
{"skin": {"texture": "<name>"}}. Output goes to assets/hogwartsmagic/textures/memory_skin/<name>.png and the names
are listed in data/hogwartsmagic/memories/_skins.json (read by MemoryRegistry and SceneDataTest), which this script
also writes so the two cannot drift.

Layout reminder (modern 64x64): head (0,0) hat (32,0); body (16,16) jacket (16,32); right leg (0,16) overlay (0,32);
left leg (16,48) overlay (0,48); right arm (40,16) overlay (40,32); left arm (32,48) overlay (48,48). Each box is
unfolded as top/bottom over right/front/left/back.

Voldemort matches the Wizard entity texture (entity/voldemort.png): near-black robes (18,18,22) shaded with
(8,8,12), pale grey skin (218,213,207) shaded (188,183,178).
"""

import json
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "memory_skin")
LIST = os.path.join(ROOT, "src", "main", "resources", "data", "hogwartsmagic", "memories", "_skins.json")

SIZE = 64


def box(u, v, w, h, d):
    """Face rectangles (x, y, w, h) of a Minecraft box UV unfolding."""
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


def shade(c, k):
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))), max(0, min(255, int(c[2] * k))), 255)


class Skin:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def paint(self, rect, fn):
        """fn(lx, ly, w, h) -> colour tuple or None (leave what is there)."""
        x0, y0, w, h = rect
        for ly in range(h):
            for lx in range(w):
                c = fn(lx, ly, w, h)
                if c is not None:
                    if len(c) == 3:
                        c = (c[0], c[1], c[2], 255)
                    self.img.putpixel((x0 + lx, y0 + ly), c)

    def fill_box(self, b, fn, faces=("top", "bottom", "right", "front", "left", "back")):
        for f in faces:
            self.paint(b[f], fn)

    def noisy(self, base, amount=0.05):
        k = 1 + self.rng.uniform(-amount, amount)
        return shade(base, k)


def build(name, seed, *, skin, hair, robe, trim, hood=None, mask=None, bald=False, eyes=(240, 240, 240),
          pupil=(40, 30, 28), eye_glow=None, tie=None, lip=None, nose_slits=False, shadow_face=False,
          hair_long=False, slim=False, post=None):
    s = Skin(seed)
    skin_dark = shade(skin, 0.86)
    robe_dark = shade(robe, 0.55)
    arm_w = 3 if slim else 4

    head = box(0, 0, 8, 8, 8)
    hat = box(32, 0, 8, 8, 8)
    body = box(16, 16, 8, 12, 4)
    jacket = box(16, 32, 8, 12, 4)
    rleg = box(0, 16, 4, 12, 4)
    rleg_o = box(0, 32, 4, 12, 4)
    lleg = box(16, 48, 4, 12, 4)
    lleg_o = box(0, 48, 4, 12, 4)
    rarm = box(40, 16, arm_w, 12, 4)
    rarm_o = box(40, 32, arm_w, 12, 4)
    larm = box(32, 48, arm_w, 12, 4)
    larm_o = box(48, 48, arm_w, 12, 4)

    # ---- head: skin, then hair, then the face ----
    s.fill_box(head, lambda x, y, w, h: s.noisy(skin, 0.03))
    if not bald:
        def hair_fn(x, y, w, h):
            return s.noisy(hair, 0.12)
        s.paint(head["top"], hair_fn)
        s.paint(head["back"], hair_fn)
        rows_side = 6 if hair_long else 3
        s.paint(head["right"], lambda x, y, w, h: hair_fn(x, y, w, h) if (y < rows_side or x > w - 3) else None)
        s.paint(head["left"], lambda x, y, w, h: hair_fn(x, y, w, h) if (y < rows_side or x < 2) else None)
        s.paint(head["front"], lambda x, y, w, h: hair_fn(x, y, w, h) if y < 2 or (y < 3 and x in (0, 3, 7)) else None)
        s.paint(head["bottom"], lambda x, y, w, h: skin_dark)
        if hair_long:
            s.paint(head["back"], lambda x, y, w, h: hair_fn(x, y, w, h))

    def face(x, y, w, h):
        # eyes on row 4 (x 1-2 and 5-6), brows row 3, mouth row 6
        if shadow_face:
            return s.noisy((10, 9, 12), 0.2) if not (y == 4 and x in (2, 5)) else (150, 20, 20)
        if y == 4 and x in (1, 2, 5, 6):
            if eye_glow is not None and x in (2, 5):
                return eye_glow
            return pupil if x in (2, 5) else eyes
        if y == 3 and x in (1, 2, 5, 6) and not bald:
            return shade(hair, 0.8)
        if y == 3 and x in (1, 2, 5, 6) and bald:
            return skin_dark
        if y == 5 and x in (3, 4):
            if nose_slits:
                return (60, 50, 52) if x in (3, 4) and y == 5 else None
            return skin_dark
        if y == 6 and x in (2, 3, 4, 5):
            return lip if lip is not None else shade(skin, 0.7)
        if nose_slits and y == 5:
            return None
        return None

    if mask is not None:
        def mask_fn(x, y, w, h):
            if y < 2:
                return None
            if y == 4 and x in (1, 2, 5, 6):
                return (6, 6, 8)
            if y == 3 and x in (1, 2, 5, 6):
                return shade(mask, 0.8)
            if y == 5 and x in (3, 4):
                return shade(mask, 0.65)
            if y == 6 and x in (2, 3, 4, 5):
                return shade(mask, 0.7)
            return s.noisy(mask, 0.04)
        s.paint(head["front"], mask_fn)
    else:
        s.paint(head["front"], face)
        if nose_slits:
            s.paint(head["front"], lambda x, y, w, h: (70, 60, 64) if (y == 5 and x in (3, 4)) else None)

    # ---- hat layer: the hood ----
    if hood is not None:
        hd = hood
        def hood_fn(x, y, w, h):
            return s.noisy(hd, 0.06)
        s.paint(hat["top"], hood_fn)
        s.paint(hat["back"], hood_fn)
        s.paint(hat["right"], hood_fn)
        s.paint(hat["left"], hood_fn)
        s.paint(hat["bottom"], lambda x, y, w, h: None)
        s.paint(hat["front"], lambda x, y, w, h: hood_fn(x, y, w, h) if (y < 2 or x == 0 or x == 7) else None)
        # a deeper fold over the brow
        s.paint(hat["front"], lambda x, y, w, h: shade(hd, 0.6) if (y == 2 and 1 <= x <= 6) else None)

    # ---- torso and legs: robes ----
    def robe_fn(x, y, w, h):
        base = robe
        if (x * 3 + y // 5) % 4 == 0:
            base = shade(robe, 0.82)  # fold lines
        if y > h - 3:
            base = shade(base, 0.9)
        return s.noisy(base, 0.03)

    s.fill_box(body, robe_fn)
    s.fill_box(jacket, robe_fn)
    # collar and open front: a dark seam down the middle of the chest, trim at the hem
    s.paint(body["front"], lambda x, y, w, h: shade(robe, 0.5) if x in (3, 4) and y > 1 else None)
    s.paint(jacket["front"], lambda x, y, w, h: shade(robe, 0.5) if x in (3, 4) and y > 1 else (
        trim if y == h - 1 else None))
    s.paint(jacket["back"], lambda x, y, w, h: trim if y == h - 1 else None)
    s.paint(jacket["right"], lambda x, y, w, h: trim if y == h - 1 else None)
    s.paint(jacket["left"], lambda x, y, w, h: trim if y == h - 1 else None)

    if tie is not None:
        # white collar and a striped tie on the chest
        s.paint(body["front"], lambda x, y, w, h: (235, 235, 235) if y < 2 and 2 <= x <= 5 else None)
        s.paint(jacket["front"], lambda x, y, w, h: tie[(y // 1) % len(tie)] if x in (3, 4) and 1 <= y <= 7 else None)

    def leg_fn(x, y, w, h):
        if y >= h - 2:
            return s.noisy((24, 20, 20), 0.2)  # shoes
        return robe_fn(x, y, w, h)
    for leg in (rleg, lleg):
        s.fill_box(leg, leg_fn)
    for leg in (rleg_o, lleg_o):
        s.fill_box(leg, lambda x, y, w, h: robe_fn(x, y, w, h) if y < h - 2 else None)
    # the robe's hem flares over the shoes on the outer layer
    for leg in (rleg_o, lleg_o):
        s.paint(leg["front"], lambda x, y, w, h: trim if y == h - 3 else None)

    # ---- arms: sleeves with bare hands ----
    def arm_fn(x, y, w, h):
        if y >= h - 2:
            return s.noisy(skin, 0.03)
        return robe_fn(x, y, w, h)
    for arm in (rarm, larm):
        s.fill_box(arm, arm_fn)
    for arm in (rarm_o, larm_o):
        s.fill_box(arm, lambda x, y, w, h: robe_fn(x, y, w, h) if y < h - 2 else None)
        s.paint(arm["front"], lambda x, y, w, h: trim if y == h - 3 else None)
        s.paint(arm["back"], lambda x, y, w, h: trim if y == h - 3 else None)

    if post is not None:
        post(s, dict(head=head, hat=hat, body=body, jacket=jacket, rleg=rleg, lleg=lleg, rleg_o=rleg_o,
                     lleg_o=lleg_o, rarm=rarm, larm=larm, rarm_o=rarm_o, larm_o=larm_o))

    path = os.path.join(OUT_DIR, name + ".png")
    s.img.save(path)
    return path

# ---------------------------------------------------------------------------------------------------------------
# Procedural Great Hall students: student_<house>_<nn> (30 per house) and first_year_<nn> (10), seeded by name.
# ---------------------------------------------------------------------------------------------------------------

HOUSE_COLOURS = {
    "gryffindor": ((160, 28, 36), (212, 168, 48)),
    "hufflepuff": ((236, 185, 30), (26, 26, 26)),
    "ravenclaw": ((30, 60, 150), (150, 110, 60)),
    "slytherin": ((30, 110, 64), (190, 192, 198)),
}
HOUSE_ORDER = ("gryffindor", "hufflepuff", "ravenclaw", "slytherin")

SKIN_TONES = [(255, 228, 212), (246, 215, 190), (238, 200, 168), (226, 180, 142), (212, 160, 118), (190, 136, 96),
              (162, 110, 74), (134, 88, 58), (106, 68, 46), (84, 54, 38)]
HAIR_COLOURS = [(18, 16, 18), (48, 32, 24), (84, 54, 32), (104, 70, 42), (140, 98, 58), (122, 52, 32),
                (176, 78, 34), (196, 164, 94), (222, 196, 128), (236, 226, 184), (58, 40, 30), (34, 28, 28),
                (26, 22, 24)]
EYE_COLOURS = [(88, 56, 34), (66, 42, 28), (140, 104, 48), (74, 128, 70), (66, 112, 176), (120, 134, 148)]
BOY_STYLES = ["short", "side_parted", "messy", "curly", "short", "side_parted", "messy"]
GIRL_STYLES = ["long_straight", "ponytail", "curly", "bob", "braids", "long_straight", "ponytail", "bob"]
ROBES = [(22, 22, 26), (28, 28, 33), (20, 20, 24), (36, 36, 42), (26, 25, 30)]


def student_traits(name, house, index):
    """Deterministic traits for one student. Cycling lattices keep the 30 of a house from repeating a combination."""
    r = random.Random("student:" + name)
    h = HOUSE_ORDER.index(house) if house else 5
    girl = index % 2 == 1
    styles = GIRL_STYLES if girl else BOY_STYLES
    return dict(
        girl=girl,
        style=styles[(index // 2 + h) % len(styles)],
        skin=SKIN_TONES[(index * 3 + h * 2 + r.randint(0, 1)) % len(SKIN_TONES)],
        hair=HAIR_COLOURS[(index * 5 + h * 3 + r.randint(0, 2)) % len(HAIR_COLOURS)],
        eyes=EYE_COLOURS[(index * 7 + h + r.randint(0, 1)) % len(EYE_COLOURS)],
        robe=ROBES[r.randrange(len(ROBES))],
        freckles=r.random() < 0.22,
        glasses=r.random() < 0.13,
        rosy=r.random() < 0.4,
        vest=house is not None and r.random() < 0.45,
        socks=r.random() < 0.5,
        seed=r.randrange(1 << 30),
    )


def build_student(name, house, index):
    t = student_traits(name, house, index)
    s = Skin(t["seed"])
    rng = s.rng
    skin, hair, robe, style, girl = t["skin"], t["hair"], t["robe"], t["style"], t["girl"]
    skin_dark = shade(skin, 0.86)
    if house:
        c1, c2 = HOUSE_COLOURS[house]
    else:
        c1, c2 = (92, 92, 98), (64, 64, 70)
    trim = c1 if house else shade(robe, 1.5)
    accent = c1 if house else (150, 60, 90)

    head = box(0, 0, 8, 8, 8)
    hat = box(32, 0, 8, 8, 8)
    body = box(16, 16, 8, 12, 4)
    jacket = box(16, 32, 8, 12, 4)
    rleg, rleg_o = box(0, 16, 4, 12, 4), box(0, 32, 4, 12, 4)
    lleg, lleg_o = box(16, 48, 4, 12, 4), box(0, 48, 4, 12, 4)
    rarm, rarm_o = box(40, 16, 4, 12, 4), box(40, 32, 4, 12, 4)
    larm, larm_o = box(32, 48, 4, 12, 4), box(48, 48, 4, 12, 4)

    def hp(a=0.1):
        return s.noisy(hair, a)

    # ---- head: skin first ----
    s.fill_box(head, lambda x, y, w, h: s.noisy(skin, 0.03))
    s.paint(head["bottom"], lambda x, y, w, h: skin_dark)

    # hair coverage per face. right face: x 0 is the back of the head, x 7 the front; the left face is mirrored.
    back_rows = {"short": 6, "side_parted": 6, "messy": 6, "curly": 7, "ponytail": 7, "bob": 7,
                 "long_straight": 8, "braids": 8}[style]
    back_cols = {"short": 2, "side_parted": 2, "messy": 3, "curly": 3, "ponytail": 2, "bob": 4,
                 "long_straight": 4, "braids": 4}[style]
    back_depth = {"short": 5, "side_parted": 5, "messy": 5, "curly": 6, "ponytail": 5, "bob": 7,
                  "long_straight": 8, "braids": 8}[style]
    side_top = 3 if style in ("messy", "curly") else 2
    burn = {"short": 3, "side_parted": 3, "messy": 3, "curly": 4, "ponytail": 3, "bob": 6, "long_straight": 7,
            "braids": 5}[style]

    s.paint(head["top"], lambda x, y, w, h: hp())
    s.paint(head["back"], lambda x, y, w, h: hp() if y < back_rows else None)

    def side_fn(front_x):
        def fn(x, y, w, h):
            from_back = x if front_x == 7 else 7 - x
            if y < side_top:
                return hp()
            if from_back < back_cols and y < back_depth:
                return hp()
            if from_back == 7 and y < burn:
                return hp()
            return None
        return fn
    s.paint(head["right"], side_fn(7))
    s.paint(head["left"], side_fn(0))

    part_left = rng.random() < 0.5
    bangs = rng.random() < 0.5

    def front_hair(x, y):
        if y == 0:
            return True
        if style == "side_parted":
            if y == 1:
                return x <= 4 if part_left else x >= 3
            if y == 2:
                return x <= 1 if part_left else x >= 6
            return False
        if style == "messy":
            if y == 1:
                return rng.random() < 0.8 or x in (0, 7)
            if y == 2:
                return x in (0, 7) or (x in (1, 6, 3) and rng.random() < 0.5)
            return x in (0, 7) and y == 3
        if style in ("long_straight", "braids", "bob", "ponytail"):
            if y == 1:
                return True
            if y == 2:
                if style == "bob" and bangs:
                    return True
                return x in (0, 1, 6, 7) if style != "ponytail" else x in (0, 7)
            if style in ("long_straight", "braids"):
                return x in (0, 7) and y <= 5
            if style == "bob":
                return x in (0, 7) and y <= 4
            return False
        if style == "curly":
            if y == 1:
                return True
            return y == 2 and x in (0, 1, 6, 7)
        # short
        if y == 1:
            return x in (0, 1, 2, 4, 5, 7) or rng.random() < 0.3
        return False
    s.paint(head["front"], lambda x, y, w, h: hp() if front_hair(x, y) else None)
    if style != "curly":
        s.paint(head["top"], lambda x, y, w, h: shade(hair, 1.18) if y == 3 and x in (1, 2, 5) else None)

    # ---- hat layer: volume ----
    def hat_hair(a=0.12):
        return s.noisy(hair, a)
    if style == "messy":
        for face in ("top", "right", "left", "back"):
            s.paint(hat[face], lambda x, y, w, h: hat_hair() if (y < 3 or face == "top") and rng.random() < 0.4
                    else None)
    elif style == "curly":
        def curl(x, y, w, h, rows):
            if y >= rows or rng.random() < 0.1:
                return None
            return shade(hair, 0.75 + 0.5 * rng.random())
        s.paint(hat["top"], lambda x, y, w, h: curl(x, y, w, h, 8))
        s.paint(hat["back"], lambda x, y, w, h: curl(x, y, w, h, 6))
        s.paint(hat["right"], lambda x, y, w, h: curl(x, y, w, h, 5) if (y < 3 or x < 5) else None)
        s.paint(hat["left"], lambda x, y, w, h: curl(x, y, w, h, 5) if (y < 3 or x > 2) else None)
        s.paint(hat["front"], lambda x, y, w, h: curl(x, y, w, h, 3) if (y < 1 or x in (0, 7)) else None)
    elif style == "ponytail":
        s.paint(hat["back"], lambda x, y, w, h: hat_hair() if 2 <= x <= 5 and y <= 6 else None)
        s.paint(hat["back"], lambda x, y, w, h: accent if y == 2 and x in (3, 4) else None)
        s.paint(hat["top"], lambda x, y, w, h: hat_hair() if y >= 5 and 2 <= x <= 5 else None)
    elif style == "bob":
        s.paint(hat["back"], lambda x, y, w, h: hat_hair() if 2 <= y <= 6 else None)
        s.paint(hat["right"], lambda x, y, w, h: hat_hair() if 3 <= y <= 6 and x < 5 else None)
        s.paint(hat["left"], lambda x, y, w, h: hat_hair() if 3 <= y <= 6 and x > 2 else None)
    elif style in ("long_straight", "braids"):
        s.paint(hat["back"], lambda x, y, w, h: hat_hair() if y >= 1 else None)
        s.paint(hat["right"], lambda x, y, w, h: hat_hair() if y >= 3 and x < 4 else None)
        s.paint(hat["left"], lambda x, y, w, h: hat_hair() if y >= 3 and x > 3 else None)
        if style == "braids":
            def braid(x, y, w, h):
                if x in (1, 2, 5, 6) and 3 <= y <= 6:
                    return shade(hair, 1.0 if (x + y) % 2 == 0 else 0.7)
                if x in (1, 2, 5, 6) and y == 7:
                    return accent
                return None
            s.paint(hat["back"], braid)

    # ---- face ----
    brow = shade(hair, 0.75) if sum(hair) > 400 else shade(hair, 1.0)
    if girl:
        lip = (min(255, int(skin[0] * 0.86 + 22)), int(skin[1] * 0.62), int(skin[2] * 0.62), 255)
    else:
        lip = shade(skin, 0.74)
    cheek = (min(255, int(skin[0] * 0.98 + 6)), int(skin[1] * 0.82), int(skin[2] * 0.8), 255)

    def face(x, y, w, h):
        if y == 4 and x in (1, 2, 5, 6):
            return t["eyes"] if x in (2, 5) else (238, 238, 238)
        if y == 3 and x in (1, 2, 5, 6):
            return brow
        if y == 5 and x in (3, 4):
            return skin_dark
        if y == 6 and x in (2, 3, 4, 5):
            return lip
        if t["glasses"] and ((y == 5 and x in (1, 2, 5, 6)) or (y == 4 and x in (0, 3, 4, 7))):
            return (34, 30, 30, 255)
        if t["rosy"] and y == 5 and x in (1, 6):
            return cheek
        return None
    s.paint(head["front"], face)
    if t["freckles"]:
        fx0, fy0 = head["front"][0], head["front"][1]
        for fx, fy in [(1, 5), (2, 5), (5, 5), (6, 5), (3, 4), (4, 4), (1, 6), (6, 6), (0, 5), (7, 5)]:
            if t["glasses"] and (fy == 5 or fy == 4):
                continue
            if rng.random() < 0.7:
                s.img.putpixel((fx0 + fx, fy0 + fy), shade(skin, 0.7))

    # ---- robe, shirt, tie, vest ----
    def robe_fn(x, y, w, h):
        base = robe
        if (x * 3 + y // 5) % 4 == 0:
            base = shade(robe, 0.82)
        if y > h - 3:
            base = shade(base, 0.9)
        return s.noisy(base, 0.03)
    grey = (96, 98, 104)
    s.fill_box(body, robe_fn)
    s.fill_box(jacket, robe_fn)
    s.paint(jacket["front"], lambda x, y, w, h: (0, 0, 0, 0) if 2 <= x <= 5 and y <= 8 else (
        shade(robe, 0.6) if x in (1, 6) and y <= 8 else (trim if y == h - 1 else None)))
    for f in ("back", "right", "left"):
        s.paint(jacket[f], lambda x, y, w, h: trim if y == h - 1 else None)

    shirt = (232, 232, 232)

    def chest(x, y, w, h):
        if not 2 <= x <= 5 or y > 8:
            return None
        if y <= 1:
            return c1 if (x in (3, 4) and y == 1) else shirt
        if t["vest"]:
            if x in (3, 4):
                return (c1, c2)[y % 2] if y <= 6 else c1
            return c2 if y == 2 else shade(c1, 0.9)
        if x in (3, 4):
            return (c1, c2)[y % 2] if y <= 7 else shirt
        return shirt
    s.paint(body["front"], chest)
    s.paint(body["front"], lambda x, y, w, h: grey if y > 8 else None)

    # ---- legs: trousers or skirt, dark socks, shoes ----
    sock = (30, 28, 32)
    shoe = (20, 16, 16)

    def leg_fn(x, y, w, h):
        if y == h - 1:
            return s.noisy(shoe, 0.2)
        if y >= h - 4 and (t["socks"] or girl):
            return s.noisy(sock, 0.12)
        if girl and y >= h - 6:
            return s.noisy(skin, 0.03)
        return s.noisy(grey, 0.04)
    for leg in (rleg, lleg):
        s.fill_box(leg, leg_fn)
    outer_rows = 8 if girl else 9
    for leg in (rleg_o, lleg_o):
        s.fill_box(leg, lambda x, y, w, h: robe_fn(x, y, w, h) if y < outer_rows else None)
        s.paint(leg["front"], lambda x, y, w, h: trim if y == outer_rows - 1 else None)
        if girl:
            for f in ("front", "back", "right", "left"):
                s.paint(leg[f], lambda x, y, w, h: s.noisy(grey, 0.04) if y == outer_rows else None)

    # ---- arms: robe sleeves, bare hands ----
    def arm_fn(x, y, w, h):
        return s.noisy(skin, 0.03) if y >= h - 2 else robe_fn(x, y, w, h)
    for arm in (rarm, larm):
        s.fill_box(arm, arm_fn)
    for arm in (rarm_o, larm_o):
        s.fill_box(arm, lambda x, y, w, h: robe_fn(x, y, w, h) if y < h - 2 else None)
        s.paint(arm["front"], lambda x, y, w, h: trim if y == h - 3 else None)
        s.paint(arm["back"], lambda x, y, w, h: trim if y == h - 3 else None)

    path = os.path.join(OUT_DIR, name + ".png")
    s.img.save(path)
    return path


def generate_students():
    names = []
    for h in HOUSE_ORDER:
        for i in range(1, 31):
            n = "student_%s_%02d" % (h, i)
            build_student(n, h, i - 1)
            names.append(n)
    for i in range(1, 11):
        n = "first_year_%02d" % i
        build_student(n, None, i - 1)
        names.append(n)
    return names


# ---------------------------------------------------------------------------------------------------------------
# Diagon Alley crowd: witch_01..20, wizard_01..20, shopkeeper_01..06, and Ollivander. Adults in robes of every
# colour, with greying hair, beards, hats and cloaks, seeded by name.
# ---------------------------------------------------------------------------------------------------------------

ADULT_ROBES = [(70, 28, 96), (52, 24, 80), (30, 84, 52), (22, 64, 44), (96, 62, 34), (74, 46, 26), (120, 28, 40),
               (92, 22, 36), (26, 38, 92), (20, 28, 66), (58, 58, 64), (34, 70, 92), (110, 86, 40), (40, 36, 44)]
ADULT_TRIMS = [(212, 168, 48), (190, 192, 198), (30, 26, 34), (150, 110, 60), (200, 90, 60)]
GREYS = [(150, 150, 154), (190, 190, 194), (222, 222, 226), (118, 116, 120)]
ADULT_HAIR = [(18, 16, 18), (48, 32, 24), (84, 54, 32), (122, 52, 32), (176, 78, 34), (196, 164, 94), (34, 28, 28),
              (104, 70, 42)]


def build_adult(name, kind, index):
    r = random.Random("adult:" + name)
    witch = kind == "witch" or (kind == "shopkeeper" and index % 2 == 1)
    age = r.choice(["young", "mid", "mid", "old"]) if kind != "shopkeeper" else ["mid", "old", "young"][index % 3]
    old = age == "old"
    skin = SKIN_TONES[(index * 3 + (0 if witch else 2) + r.randint(0, 3)) % len(SKIN_TONES)]
    if old:
        skin = shade(skin, 0.96)
    if old or (age == "mid" and r.random() < 0.3):
        hair = r.choice(GREYS)
    else:
        hair = r.choice(ADULT_HAIR)
    eyes = r.choice(EYE_COLOURS)
    robe = ADULT_ROBES[(index * 5 + (3 if witch else 0) + r.randint(0, 3)) % len(ADULT_ROBES)]
    trim = r.choice(ADULT_TRIMS)
    style = r.choice(["long_straight", "bun", "curly", "bob", "ponytail"] if witch else
                     ["short", "side_parted", "messy", "bald", "long"])
    beard = (not witch) and ((old and r.random() < 0.75) or (age == "mid" and r.random() < 0.4) or
                             (kind == "shopkeeper" and r.random() < 0.3))
    moustache = (not witch) and not beard and r.random() < 0.3
    hat = kind != "shopkeeper" and r.random() < 0.45
    hat_col = shade(robe, 0.7) if r.random() < 0.6 else (28, 26, 32)
    cloak = (not hat or r.random() < 0.5) and r.random() < 0.5 and kind != "shopkeeper"
    cloak_col = shade(robe, 0.8) if r.random() < 0.5 else (36, 32, 40)
    apron = kind == "shopkeeper"
    apron_col = r.choice([(214, 204, 180), (120, 90, 60), (90, 100, 70)])
    glasses = r.random() < (0.3 if old else 0.1)
    belt = r.random() < 0.5
    inner = (226, 222, 214) if r.random() < 0.6 else shade(robe, 1.35)
    s = Skin(r.randrange(1 << 30))
    rng = s.rng
    skin_dark = shade(skin, 0.86)

    head = box(0, 0, 8, 8, 8)
    hatb = box(32, 0, 8, 8, 8)
    body = box(16, 16, 8, 12, 4)
    jacket = box(16, 32, 8, 12, 4)
    rleg, rleg_o = box(0, 16, 4, 12, 4), box(0, 32, 4, 12, 4)
    lleg, lleg_o = box(16, 48, 4, 12, 4), box(0, 48, 4, 12, 4)
    rarm, rarm_o = box(40, 16, 4, 12, 4), box(40, 32, 4, 12, 4)
    larm, larm_o = box(32, 48, 4, 12, 4), box(48, 48, 4, 12, 4)

    def hp(a=0.1):
        return s.noisy(hair, a)

    s.fill_box(head, lambda x, y, w, h: s.noisy(skin, 0.03))
    s.paint(head["bottom"], lambda x, y, w, h: skin_dark)
    long_hair = style in ("long_straight", "long", "curly", "ponytail", "bun")
    bald = style == "bald"
    if not bald:
        rows = 8 if style in ("long_straight", "long") else (7 if style in ("curly", "bob") else 6)
        cols = 4 if style in ("long_straight", "bob", "long", "curly") else 2
        s.paint(head["top"], lambda x, y, w, h: hp())
        s.paint(head["back"], lambda x, y, w, h: hp() if y < rows else None)

        def side(front_x):
            def fn(x, y, w, h):
                fb = x if front_x == 7 else 7 - x
                if y < 2 or (fb < cols and y < rows - 1) or (fb == 7 and y < 3):
                    return hp()
                return None
            return fn
        s.paint(head["right"], side(7))
        s.paint(head["left"], side(0))

        def front(x, y, w, h):
            if y == 0:
                return hp()
            if y == 1:
                if style == "side_parted":
                    return hp() if x <= 4 else None
                return hp() if (style != "short" or x in (0, 1, 2, 4, 5, 7)) else None
            if y == 2:
                return hp() if (x in (0, 7) or (style == "messy" and x in (2, 5)) or
                                (witch and style in ("long_straight", "bob") and x in (0, 1, 6, 7))) else None
            if y in (3, 4, 5) and x in (0, 7) and style in ("long_straight", "long"):
                return hp()
            return None
        s.paint(head["front"], front)
        if long_hair or style == "messy":
            # volume on the hat layer: hair standing out behind and above
            vol = 0.5 if style == "messy" else 0.9
            for face_name, rr in (("back", 7), ("right", 6), ("left", 6)):
                def vfn(x, y, w, h, face_name=face_name, rr=rr):
                    ok = face_name == "back" or (x < 4 if face_name == "right" else x > 3) or y < 2
                    return hp(0.15) if y < rr and ok and rng.random() < vol else None
                s.paint(hatb[face_name], vfn)
            s.paint(hatb["top"], lambda x, y, w, h: hp(0.15) if rng.random() < vol else None)
        if style == "bun":
            s.paint(hatb["top"], lambda x, y, w, h: hp(0.18) if 2 <= x <= 5 and y >= 4 else None)
            s.paint(hatb["back"], lambda x, y, w, h: hp(0.18) if 2 <= x <= 5 and y <= 2 else None)

    brow = shade(hair, 0.8)
    lip = (min(255, int(skin[0] * 0.86 + 24)), int(skin[1] * 0.6), int(skin[2] * 0.6), 255) if witch \
        else shade(skin, 0.72)
    wrinkle = shade(skin, 0.8)

    def face(x, y, w, h):
        if y == 4 and x in (1, 2, 5, 6):
            return eyes if x in (2, 5) else (232, 232, 232)
        if y == 3 and x in (1, 2, 5, 6):
            return brow if not bald else skin_dark
        if glasses and ((y == 5 and x in (1, 2, 5, 6)) or (y == 4 and x in (0, 3, 4, 7))):
            return (40, 34, 30, 255)
        if y == 5 and x in (3, 4):
            return skin_dark
        if y == 6 and x in (2, 3, 4, 5):
            return lip
        if old and y == 5 and x in (0, 7):
            return wrinkle
        return None
    s.paint(head["front"], face)

    if beard:
        bc = hair if (sum(hair) > 380 or old) else shade(hair, 0.9)
        s.paint(head["front"], lambda x, y, w, h: (s.noisy(shade(bc, 0.85), 0.1) if (y == 6 and 2 <= x <= 5) else
                                                  s.noisy(bc, 0.1)) if (y >= 6 or (y == 5 and x not in (3, 4))) else None)
        s.paint(head["right"], lambda x, y, w, h: s.noisy(bc, 0.1) if x >= 5 and y >= 5 else None)
        s.paint(head["left"], lambda x, y, w, h: s.noisy(bc, 0.1) if x <= 2 and y >= 5 else None)
        s.paint(head["bottom"], lambda x, y, w, h: s.noisy(bc, 0.1))
        if old:  # a long beard hangs past the chin on the hat layer
            s.paint(hatb["front"], lambda x, y, w, h: s.noisy(bc, 0.12) if y == 7 and 2 <= x <= 5 else None)
    elif moustache:
        s.paint(head["front"], lambda x, y, w, h: s.noisy(shade(hair, 0.9), 0.1) if y == 5 and x in (2, 3, 4, 5)
                else None)

    # ---- hat: a dark cap with a brim line over the brow and a band ----
    if hat:
        hc = hat_col
        for f in ("top", "right", "left", "back"):
            s.paint(hatb[f], lambda x, y, w, h, f=f: s.noisy(hc, 0.05) if (f == "top" or y < 3) else None)
        s.paint(hatb["front"], lambda x, y, w, h: s.noisy(hc, 0.05) if y < 2 else (
            shade(hc, 0.55) if y == 2 else None))
        s.paint(hatb["top"], lambda x, y, w, h: shade(hc, 0.8) if 2 <= x <= 5 and 2 <= y <= 5 else None)
        for f in ("right", "left", "back"):
            s.paint(hatb[f], lambda x, y, w, h: shade(trim, 0.85) if y == 2 else None)

    # ---- robes ----
    def robe_fn(x, y, w, h):
        base = robe
        if (x * 3 + y // 5) % 4 == 0:
            base = shade(robe, 0.82)
        if y > h - 3:
            base = shade(base, 0.9)
        return s.noisy(base, 0.03)
    s.fill_box(body, robe_fn)
    s.fill_box(jacket, robe_fn)
    s.paint(body["front"], lambda x, y, w, h: inner if 3 <= x <= 4 and y < 5 else None)
    s.paint(jacket["front"], lambda x, y, w, h: (0, 0, 0, 0) if x in (3, 4) and y <= 5 else (
        shade(robe, 0.6) if x in (2, 5) and y <= 5 else (trim if y == h - 1 else None)))
    for f in ("back", "right", "left"):
        s.paint(jacket[f], lambda x, y, w, h: trim if y == h - 1 else None)
    if belt:
        s.paint(jacket["front"], lambda x, y, w, h: shade(trim, 0.8) if y == 6 and x not in (3, 4) else None)
        for f in ("back", "right", "left"):
            s.paint(jacket[f], lambda x, y, w, h: shade(trim, 0.8) if y == 6 else None)
    if apron:
        ad = shade(apron_col, 0.75)
        s.paint(jacket["front"], lambda x, y, w, h: s.noisy(apron_col, 0.04) if 1 <= x <= 6 and y >= 2 else None)
        for f in ("front", "right", "left", "back"):
            s.paint(jacket[f], lambda x, y, w, h: ad if y == 2 and (f != "front" or 1 <= x <= 6) else None)
    if cloak:
        cc = cloak_col

        def cl(x, y, w, h):
            return s.noisy(cc if (x + y // 4) % 3 else shade(cc, 0.85), 0.03)
        s.paint(jacket["back"], cl)
        s.paint(jacket["right"], lambda x, y, w, h: cl(x, y, w, h) if x >= 1 else None)
        s.paint(jacket["left"], lambda x, y, w, h: cl(x, y, w, h) if x <= 2 else None)
        s.paint(jacket["front"], lambda x, y, w, h: shade(cc, 0.9) if (x in (0, 7) or y == 0) else None)
        s.paint(jacket["top"], lambda x, y, w, h: cc)
        s.paint(jacket["back"], lambda x, y, w, h: shade(trim, 0.9) if y == h - 1 else None)

    shoe = (30, 22, 18)

    def leg_fn(x, y, w, h):
        if y >= h - 2:
            return s.noisy(shoe, 0.2)
        return s.noisy(shade(robe, 0.6), 0.04)
    for leg in (rleg, lleg):
        s.fill_box(leg, leg_fn)
    rows_o = 9 if witch or cloak else 7
    for leg in (rleg_o, lleg_o):
        s.fill_box(leg, lambda x, y, w, h: robe_fn(x, y, w, h) if y < rows_o else None)
        s.paint(leg["front"], lambda x, y, w, h: trim if y == rows_o - 1 else None)

    def arm_fn(x, y, w, h):
        return s.noisy(skin, 0.03) if y >= h - 2 else robe_fn(x, y, w, h)
    for arm in (rarm, larm):
        s.fill_box(arm, arm_fn)
    for arm in (rarm_o, larm_o):
        s.fill_box(arm, lambda x, y, w, h: robe_fn(x, y, w, h) if y < h - 2 else None)
        s.paint(arm["front"], lambda x, y, w, h: trim if y == h - 3 else None)
        s.paint(arm["back"], lambda x, y, w, h: trim if y == h - 3 else None)

    s.img.save(os.path.join(OUT_DIR, name + ".png"))


def build_ollivander():
    """Pale, elderly, wild white hair standing out on the hat layer, pale grey eyes, waistcoat and cravat, long coat."""
    s = Skin(4242)
    rng = s.rng
    skin = (236, 224, 214)
    skin_dark = shade(skin, 0.86)
    white = (226, 226, 230)
    coat = (30, 28, 32)
    vest = (68, 60, 58)
    shirt = (236, 236, 238)
    head = box(0, 0, 8, 8, 8)
    hat = box(32, 0, 8, 8, 8)
    body = box(16, 16, 8, 12, 4)
    jacket = box(16, 32, 8, 12, 4)
    rleg, rleg_o = box(0, 16, 4, 12, 4), box(0, 32, 4, 12, 4)
    lleg, lleg_o = box(16, 48, 4, 12, 4), box(0, 48, 4, 12, 4)
    rarm, rarm_o = box(40, 16, 4, 12, 4), box(40, 32, 4, 12, 4)
    larm, larm_o = box(32, 48, 4, 12, 4), box(48, 48, 4, 12, 4)

    def hp(a=0.08):
        return s.noisy(white if rng.random() < 0.8 else (180, 180, 186), a)

    s.fill_box(head, lambda x, y, w, h: s.noisy(skin, 0.025))
    s.paint(head["bottom"], lambda x, y, w, h: skin_dark)
    s.paint(head["top"], lambda x, y, w, h: hp())
    s.paint(head["back"], lambda x, y, w, h: hp() if y < 7 else None)
    s.paint(head["right"], lambda x, y, w, h: hp() if ((y < 3 or x < 4) and y < 7) or (x == 7 and y < 3) else None)
    s.paint(head["left"], lambda x, y, w, h: hp() if ((y < 3 or x > 3) and y < 7) or (x == 0 and y < 3) else None)
    s.paint(head["front"], lambda x, y, w, h: hp() if y < 2 or (y == 2 and x in (0, 1, 6, 7)) or
            (y in (3, 4) and x in (0, 7)) else None)

    def face(x, y, w, h):
        if y == 4 and x in (1, 2, 5, 6):
            return (178, 190, 200) if x in (2, 5) else (240, 240, 242)
        if y == 3 and x in (1, 2, 5, 6):
            return (196, 196, 200)
        if y == 5 and x in (3, 4):
            return skin_dark
        if y == 5 and x in (0, 7):
            return shade(skin, 0.8)
        if y == 6 and x in (2, 3, 4, 5):
            return shade(skin, 0.72)
        return None
    s.paint(head["front"], face)
    # wild hair sticking out on the hat layer, uneven: tufts above, wisps down the sides and back
    for f, rr in (("top", 8), ("back", 8), ("right", 7), ("left", 7)):
        def tuft(x, y, w, h, f=f, rr=rr):
            ok = f in ("top", "back") or y < 3 or (x < 5 if f == "right" else x > 2)
            return hp(0.14) if y < rr and ok and rng.random() < (0.85 if f == "top" else 0.6) else None
        s.paint(hat[f], tuft)
    s.paint(hat["front"], lambda x, y, w, h: hp(0.14) if (y == 0 and rng.random() < 0.9) or (y == 1 and x in (0, 2, 5, 7))
            or (y in (2, 3) and x in (0, 7) and rng.random() < 0.6) else None)

    # ---- long dark coat over a waistcoat, white shirt, dark cravat ----
    def coat_fn(x, y, w, h):
        base = coat if (x * 3 + y // 5) % 4 else shade(coat, 0.8)
        return s.noisy(base, 0.03)
    s.fill_box(body, coat_fn)
    s.fill_box(jacket, coat_fn)
    s.paint(jacket["front"], lambda x, y, w, h: (0, 0, 0, 0) if 2 <= x <= 5 and y <= 9 else (
        shade(coat, 1.5) if x in (1, 6) and y <= 9 else None))

    def chest(x, y, w, h):
        if not 2 <= x <= 5 or y > 10:
            return None
        if y <= 1:
            return shirt
        if y == 2:
            return (30, 28, 34) if x in (3, 4) else shirt  # cravat
        if x in (3, 4) and y == 3:
            return (24, 22, 28)
        if y == 4 and x in (2, 5):
            return shirt
        if x in (3, 4) and y % 3 == 1:
            return (150, 140, 120)  # waistcoat buttons
        return s.noisy(vest, 0.05)
    s.paint(body["front"], chest)
    s.paint(body["front"], lambda x, y, w, h: shade(coat, 1.2) if y > 10 else None)
    s.paint(jacket["front"], lambda x, y, w, h: shade(coat, 0.7) if y == h - 1 else None)

    def leg_fn(x, y, w, h):
        return s.noisy((24, 20, 20), 0.2) if y >= h - 2 else s.noisy((44, 42, 46), 0.04)
    for leg in (rleg, lleg):
        s.fill_box(leg, leg_fn)
    for leg in (rleg_o, lleg_o):  # long coat skirts
        s.fill_box(leg, lambda x, y, w, h: coat_fn(x, y, w, h) if y < 9 else None)

    def arm_fn(x, y, w, h):
        return s.noisy(skin, 0.03) if y >= h - 2 else coat_fn(x, y, w, h)
    for arm in (rarm, larm):
        s.fill_box(arm, arm_fn)
    for arm in (rarm_o, larm_o):
        s.fill_box(arm, lambda x, y, w, h: coat_fn(x, y, w, h) if y < h - 2 else None)
        s.paint(arm["front"], lambda x, y, w, h: shirt if y == h - 3 else None)
        s.paint(arm["back"], lambda x, y, w, h: shirt if y == h - 3 else None)
    s.img.save(os.path.join(OUT_DIR, "ollivander.png"))


def generate_crowd():
    names = []
    for kind, n in (("witch", 20), ("wizard", 20), ("shopkeeper", 6)):
        for i in range(1, n + 1):
            nm = "%s_%02d" % (kind, i)
            build_adult(nm, kind, i - 1)
            names.append(nm)
    build_ollivander()
    names.append("ollivander")
    return names


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    black = (22, 22, 26)
    names = []

    # A cloaked, faceless watcher: the shape every memory has somewhere in it.
    build("hooded_figure", 1, skin=(120, 114, 108), hair=(10, 10, 12), robe=(30, 28, 34), trim=(14, 14, 18),
          hood=(26, 24, 30), shadow_face=True, bald=True)
    names.append("hooded_figure")

    # Regulus Black: pale, dark curls, Slytherin green.
    build("regulus", 2, skin=(226, 208, 196), hair=(28, 22, 22), robe=black, trim=(30, 110, 64),
          tie=[(30, 110, 64), (190, 192, 198)], hair_long=False, pupil=(70, 80, 90), lip=(172, 110, 108))
    names.append("regulus")

    build("student_gryffindor", 3, skin=(232, 190, 160), hair=(96, 58, 30), robe=black, trim=(160, 28, 36),
          tie=[(160, 28, 36), (212, 168, 48)], pupil=(54, 74, 40), lip=(190, 110, 100))
    names.append("student_gryffindor")

    build("student_slytherin", 4, skin=(238, 218, 200), hair=(214, 196, 150), robe=black, trim=(30, 110, 64),
          tie=[(30, 110, 64), (190, 192, 198)], pupil=(90, 100, 110), lip=(180, 120, 114))
    names.append("student_slytherin")

    # Death Eater: hooded black robes and a pale mask.
    build("death_eater", 5, skin=(210, 204, 198), hair=(10, 10, 12), robe=(18, 18, 22), trim=(36, 36, 44),
          hood=(14, 14, 18), mask=(218, 213, 207), bald=True)
    names.append("death_eater")

    # Voldemort: bald, snake-pale, slit nose, red eyes, the Wizard texture's black robe.
    build("voldemort", 6, skin=(218, 213, 207), hair=(188, 183, 178), robe=(18, 18, 22), trim=(50, 50, 60),
          bald=True, eyes=(200, 40, 40), pupil=(110, 0, 0), eye_glow=(230, 50, 40), nose_slits=True,
          lip=(150, 120, 120))
    names.append("voldemort")

    # Second looks for the house tables, so a full hall does not read as clones.
    build("student_gryffindor_b", 13, skin=(210, 160, 128), hair=(30, 22, 18), robe=black, trim=(160, 28, 36),
          tie=[(160, 28, 36), (212, 168, 48)], pupil=(60, 50, 40), lip=(170, 100, 90))
    names.append("student_gryffindor_b")
    build("student_slytherin_b", 14, skin=(236, 206, 184), hair=(40, 30, 26), robe=black, trim=(30, 110, 64),
          tie=[(30, 110, 64), (190, 192, 198)], pupil=(70, 80, 60), lip=(176, 112, 108))
    names.append("student_slytherin_b")
    build("student_hufflepuff_b", 15, skin=(228, 184, 150), hair=(150, 96, 44), robe=black, trim=(236, 185, 30),
          tie=[(236, 185, 30), (24, 24, 24)], pupil=(60, 90, 50), lip=(186, 112, 100))
    names.append("student_hufflepuff_b")
    build("student_ravenclaw_b", 16, skin=(196, 150, 116), hair=(20, 16, 14), robe=black, trim=(30, 60, 150),
          tie=[(30, 60, 150), (150, 110, 60)], pupil=(50, 40, 30), lip=(160, 96, 86))
    names.append("student_ravenclaw_b")
    # A first-year before the Sorting: plain black robe, no house colours yet.
    build("first_year", 17, skin=(234, 196, 168), hair=(120, 76, 40), robe=black, trim=(40, 40, 46),
          tie=[(90, 90, 96), (60, 60, 66)], pupil=(70, 90, 110), lip=(188, 112, 104))
    names.append("first_year")

    # Downloaded, not generated: NovaSkin gallery skins the user picked, kept as-is (credit their posts).
    #   sirius   https://minecraft.novaskin.me/post/7016934077/sirius-black
    #   hermione https://minecraft.novaskin.me/post/1834684774/hermione-harry-potter
    #   harry    https://minecraft.novaskin.me/post/1391313165/skin-harry-potter
    #   mcgonagall          https://minecraft.novaskin.me/post/1044483149
    #   dumbledore          https://minecraft.novaskin.me/post/3528453724
    #   hagrid              https://minecraft.novaskin.me/post/1143443052
    #   snape               https://minecraft.novaskin.me/post/3867473134
    #   quirrell            https://minecraft.novaskin.me/post/5066624078
    #   ron                 https://minecraft.novaskin.me/post/1485033249
    #   student_hufflepuff  https://minecraft.novaskin.me/post/4747441164779520
    #   student_ravenclaw   https://minecraft.novaskin.me/post/4832858287374336
    for name in ("sirius", "hermione", "harry", "mcgonagall", "dumbledore", "hagrid", "snape", "quirrell", "ron",
                 "student_hufflepuff", "student_ravenclaw"):
        if not os.path.exists(os.path.join(OUT_DIR, name + ".png")):
            raise SystemExit("missing downloaded skin " + name + ".png")
        names.append(name)

    names.extend(generate_students())
    names.extend(generate_crowd())

    with open(LIST, "w", encoding="utf-8", newline="\n") as f:
        json.dump(names, f, indent=2)
        f.write("\n")
    print("wrote", len(names), "skins to", OUT_DIR)


if __name__ == "__main__":
    main()
