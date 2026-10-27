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

    # Winter wear drawn from its own generator, so every other pixel of the skin keeps its seed.
    nr = random.Random("neck:" + name)
    if nr.random() < 0.5:
        add_scarf(s, nr, jacket)
    elif witch and nr.random() < 0.45:
        add_shawl(s, nr, jacket, rarm_o, larm_o)

    s.img.save(os.path.join(OUT_DIR, name + ".png"))


SCARF_COLOURS = [(150, 28, 36), (212, 168, 48), (30, 90, 52), (190, 192, 198), (26, 38, 92), (236, 230, 214),
                 (120, 70, 36), (170, 60, 110), (40, 40, 48), (196, 100, 40)]
SHAWL_COLOURS = [(120, 34, 52), (46, 84, 96), (96, 70, 120), (150, 110, 60), (60, 80, 52), (190, 170, 140)]


def add_scarf(s, nr, jacket):
    """A knitted, striped scarf wrapped twice round the neck on the outer layer, one end hanging down the chest."""
    a, b = nr.sample(SCARF_COLOURS, 2)
    band = nr.choice([1, 2])
    right_end = nr.random() < 0.5
    knit = nr

    def stripe(x, y):
        c = a if ((x + y) // band) % 2 == 0 else b
        return shade(c, 1 + knit.uniform(-0.06, 0.06))

    for f in ("front", "right", "back", "left"):
        s.paint(jacket[f], lambda x, y, w, h, f=f: stripe(x + (3 if f == "back" else 0), y) if y < 2 else None)
    # the lower wrap sits a touch darker, so the two turns read as separate
    for f in ("front", "right", "back", "left"):
        s.paint(jacket[f], lambda x, y, w, h: shade(a, 0.78) if y == 1 and ((x // band) % 2 == 0) else None)
    ex = (5, 6) if right_end else (1, 2)
    s.paint(jacket["front"], lambda x, y, w, h: stripe(x, y) if x in ex and 2 <= y <= 7 else None)
    s.paint(jacket["front"], lambda x, y, w, h: shade(b if x == ex[0] else a, 1.25) if x in ex and y == 8 else None)
    s.paint(jacket["front"], lambda x, y, w, h: shade(a, 0.7) if x in ex and y == 9 and (x + y) % 2 == 0 else None)


def add_shawl(s, nr, jacket, rarm_o, larm_o):
    """A shawl across the shoulders: a V down the chest and back, over both arms, with a fringe."""
    c = nr.choice(SHAWL_COLOURS)
    edge = shade(c, 1.35)

    def weave(x, y):
        return shade(c, (0.9 if (x + y) % 2 else 1.0) + nr.uniform(-0.04, 0.04))

    def vee(x, y, w, h):
        depth = 5 - int(abs(x - 3.5) * 1.2)
        if y < depth - 1:
            return weave(x, y)
        return edge if y == depth - 1 else None
    s.paint(jacket["front"], vee)
    s.paint(jacket["back"], vee)
    s.paint(jacket["top"], lambda x, y, w, h: weave(x, y))
    for f in ("right", "left"):
        s.paint(jacket[f], lambda x, y, w, h: weave(x, y) if y < 3 else (edge if y == 3 else None))
    for arm in (rarm_o, larm_o):
        s.paint(arm["top"], lambda x, y, w, h: weave(x, y))
        for f in ("front", "back", "right", "left"):
            s.paint(arm[f], lambda x, y, w, h: weave(x, y) if y < 3 else (edge if y == 3 else None))


def contact_sheet(names, path, per_row=10):
    """Front and back of each skin side by side, for a quick look at the whole crowd."""
    sc, cw, ch = 5, 192, 168
    rows = (len(names) + per_row - 1) // per_row
    sheet = Image.new("RGBA", (per_row * cw, rows * ch), (70, 72, 80, 255))
    for i, nm in enumerate(names):
        sk = Image.open(os.path.join(OUT_DIR, nm + ".png")).convert("RGBA")
        fig = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
        back = Image.new("RGBA", (16, 32), (0, 0, 0, 0))
        for im, hf, bf, af in ((fig, "front", "front", "front"), (back, "back", "back", "back")):
            hb, hob = box(0, 0, 8, 8, 8)[hf], box(32, 0, 8, 8, 8)[hf]
            parts = [
                (hb, 4, 0), (box(16, 16, 8, 12, 4)[bf], 4, 8), (box(40, 16, 4, 12, 4)[af], 0 if hf == "front" else 12, 8),
                (box(32, 48, 4, 12, 4)[af], 12 if hf == "front" else 0, 8),
                (box(0, 16, 4, 12, 4)[af], 4 if hf == "front" else 8, 20), (box(16, 48, 4, 12, 4)[af], 8 if hf == "front" else 4, 20),
                (hob, 4, 0), (box(16, 32, 8, 12, 4)[bf], 4, 8), (box(40, 32, 4, 12, 4)[af], 0 if hf == "front" else 12, 8),
                (box(48, 48, 4, 12, 4)[af], 12 if hf == "front" else 0, 8),
                (box(0, 32, 4, 12, 4)[af], 4 if hf == "front" else 8, 20), (box(0, 48, 4, 12, 4)[af], 8 if hf == "front" else 4, 20),
            ]
            for (x, y, w, h), dx, dy in parts:
                im.alpha_composite(sk.crop((x, y, x + w, y + h)), (dx, dy))
        pair = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        pair.alpha_composite(fig, (0, 0))
        pair.alpha_composite(back, (16, 0))
        pair = pair.resize((32 * sc, 32 * sc), Image.NEAREST)
        sheet.alpha_composite(pair, ((i % per_row) * cw + 16, (i // per_row) * ch + 4))
    sheet.convert("RGB").save(path)


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


SOURCE_DIR = os.path.join(ROOT, "build_scripts", "skin_sources")


def _blonde(lum, rng):
    """A blonde hair pixel at brightness lum (0..1 of the mid tone), with a little grain."""
    k = lum * (1.0 + rng.uniform(-0.04, 0.04))
    return (min(255, int(232 * k)), min(255, int(196 * k)), min(255, int(112 * k)), 255)


# Base-layer boxes of the modern 64x64 layout: (u, v, w, h, d). Memory skins load as plain textures, so the vanilla
# base-layer "force opaque" pass never runs on them and any alpha below 255 in these faces is a see-through hole.
BASE_BOXES = {"head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "rleg": (0, 16, 4, 12, 4),
              "rarm": (40, 16, 4, 12, 4), "lleg": (16, 48, 4, 12, 4), "larm": (32, 48, 4, 12, 4)}


def _base_pixels(u, v, w, h, d):
    out = []
    for fx, fy, fw, fh in box(u, v, w, h, d).values():
        out.extend((x, y) for y in range(fy, fy + fh) for x in range(fx, fx + fw))
    return out


def fill_base_holes(img):
    """Give every transparent base-layer pixel the colour of the nearest opaque pixel of the same body part."""
    px = img.load()
    for part in BASE_BOXES.values():
        pts = _base_pixels(*part)
        solid = [p for p in pts if px[p][3] == 255]
        for p in pts:
            if px[p][3] < 255:
                q = min(solid, key=lambda o: ((o[0] - p[0]) ** 2 + (o[1] - p[1]) ** 2, o[1], o[0]))
                px[p] = px[q][:3] + (255,)


def check_base_opaque(img, name):
    px = img.load()
    for pname, part in BASE_BOXES.items():
        for p in _base_pixels(*part):
            assert px[p][3] == 255, "%s: base layer %s has alpha %d at %s" % (name, pname, px[p][3], p)


def touch_up_dursleys():
    """Vernon is kept as downloaded (base-layer holes filled); Petunia's hair is recoloured blonde; Dudley is made blond with a striped jumper.

    The raw downloads live in build_scripts/skin_sources/ and the touched-up skins are written to OUT_DIR.
    """
    HEAD = (0, 0, 64, 16)
    img = Image.open(os.path.join(SOURCE_DIR, "vernon.png")).convert("RGBA")
    fill_base_holes(img)
    check_base_opaque(img, "vernon")
    img.save(os.path.join(OUT_DIR, "vernon.png"))

    # Petunia: brown hair pixels (head + hat layer) become blonde at the same relative brightness.
    img = Image.open(os.path.join(SOURCE_DIR, "petunia.png")).convert("RGBA")
    rng = random.Random(71)
    px = img.load()
    for y in range(HEAD[1], HEAD[3]):
        for x in range(HEAD[0], HEAD[2]):
            r, g, b, a = px[x, y]
            if a and r < 140 and r > g > b and r - b < 100:
                px[x, y] = _blonde((r / 108.0) * 0.95 + 0.1, rng)
    fill_base_holes(img)
    check_base_opaque(img, "petunia")
    img.save(os.path.join(OUT_DIR, "petunia.png"))

    # Dudley: the near-black brown hair (43,35,26) becomes blond; the red plaid jumper becomes blue and cream
    # stripes (2 px bands; the base and overlay layers are 16/32 px apart so the bands line up).
    img = Image.open(os.path.join(SOURCE_DIR, "dudley.png")).convert("RGBA")
    rng = random.Random(72)
    px = img.load()
    for y in range(HEAD[1], HEAD[3]):
        for x in range(HEAD[0], HEAD[2]):
            if px[x, y] == (43, 35, 26, 255):
                px[x, y] = _blonde(1.0, rng)
    # The download has no eyes drawn; give him two small blue ones under the fringe.
    for ex in (10, 13):
        px[ex, 13] = (46, 70, 140, 255)
    skin_c = (254, 219, 168, 255)
    stripe = [(52, 84, 170), (234, 228, 208)]
    jumper = [(16, 16, 40, 32), (16, 32, 40, 48), (40, 16, 56, 32), (40, 32, 56, 48), (32, 48, 48, 64),
              (48, 48, 64, 64)]
    for x0, y0, x1, y1 in jumper:
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b, a = px[x, y]
                if a and (r, g, b, a) != skin_c:
                    c = stripe[(y // 2) % 2]
                    k = 1.0 + rng.uniform(-0.03, 0.03)
                    px[x, y] = (int(c[0] * k), int(c[1] * k), int(c[2] * k), 255)
    # The tops of his legs sit under the torso; the download leaves red plaid there. Use the trouser colour.
    for x0, y0 in ((4, 16), (20, 48)):
        for y in range(y0, y0 + 4):
            for x in range(x0, x0 + 4):
                px[x, y] = (191, 180, 107, 255)
    fill_base_holes(img)
    check_base_opaque(img, "dudley")
    img.save(os.path.join(OUT_DIR, "dudley.png"))


# ---------------------------------------------------------------------------------------------------------------
# Flying memory: Madam Hooch (film referee) and young Draco Malfoy, painted by hand rather than downloaded.
# ---------------------------------------------------------------------------------------------------------------

def _parts():
    return dict(head=box(0, 0, 8, 8, 8), hat=box(32, 0, 8, 8, 8), body=box(16, 16, 8, 12, 4),
                jacket=box(16, 32, 8, 12, 4), rleg=box(0, 16, 4, 12, 4), rleg_o=box(0, 32, 4, 12, 4),
                lleg=box(16, 48, 4, 12, 4), lleg_o=box(0, 48, 4, 12, 4), rarm=box(40, 16, 4, 12, 4),
                rarm_o=box(40, 32, 4, 12, 4), larm=box(32, 48, 4, 12, 4), larm_o=box(48, 48, 4, 12, 4))


def _put(s, rect, x, y, c):
    s.img.putpixel((rect[0] + x, rect[1] + y), c if len(c) == 4 else (c[0], c[1], c[2], 255))


def build_hooch():
    """Madam Hooch as the films dress her: short spiky grey-white hair, yellow hawk eyes, a weathered tan face, a
    stand collar and black-and-white striped referee robes, and a whistle on a gold cord."""
    s = Skin("hooch")
    rng = s.rng
    p = _parts()
    skin = (226, 188, 152)
    skin_d = shade(skin, 0.84)
    ink, paper = (22, 22, 27), (232, 232, 236)

    def hair(a=0.1):
        k = rng.uniform(-a, a)
        base = rng.choice([(206, 206, 210), (224, 224, 228), (186, 186, 192), (238, 238, 240)])
        return shade(base, 1 + k)

    # head, then hair: cropped close at the sides, standing up on top (the hat layer carries the spikes)
    s.fill_box(p["head"], lambda x, y, w, h: s.noisy(skin, 0.025))
    s.paint(p["head"]["bottom"], lambda x, y, w, h: skin_d)
    s.paint(p["head"]["top"], lambda x, y, w, h: hair())
    s.paint(p["head"]["back"], lambda x, y, w, h: hair() if y < 5 else None)
    s.paint(p["head"]["right"], lambda x, y, w, h: hair() if (y < 2 or (x < 3 and y < 5)) else None)
    s.paint(p["head"]["left"], lambda x, y, w, h: hair() if (y < 2 or (x > 4 and y < 5)) else None)
    # a ragged fringe: row 0 solid, row 1 mostly, row 2 only the odd tooth
    s.paint(p["head"]["front"], lambda x, y, w, h: hair() if (y == 0 or (y == 1 and x not in (3,)) or
                                                              (y == 2 and x in (0, 2, 5, 7))) else None)
    # hat layer: spikes
    s.paint(p["hat"]["top"], lambda x, y, w, h: hair(0.14) if rng.random() < 0.8 else None)
    s.paint(p["hat"]["front"], lambda x, y, w, h: hair() if (y == 0 and x != 3) or (y == 1 and x in (1, 4, 6)) else None)
    s.paint(p["hat"]["back"], lambda x, y, w, h: hair() if y < 2 or (y < 4 and x in (1, 3, 5)) else None)
    s.paint(p["hat"]["right"], lambda x, y, w, h: hair() if y == 0 or (y == 1 and x % 2 == 0) or (y == 2 and x in (1, 4)) else None)
    s.paint(p["hat"]["left"], lambda x, y, w, h: hair() if y == 0 or (y == 1 and x % 2 == 1) or (y == 2 and x in (2, 5)) else None)

    # the face (head front 8x8): brows row 3, hawk eyes row 4, nose row 5, a firm mouth row 6, creases
    f = p["head"]["front"]
    brow = (92, 92, 100)
    for x in (1, 2, 5, 6):
        _put(s, f, x, 3, brow)
    _put(s, f, 2, 2 if False else 3, shade(brow, 0.7))
    _put(s, f, 5, 3, shade(brow, 0.7))
    for ex in (1, 5):                       # outer corner pale yellow, inner the iris, with the pupil beside it
        _put(s, f, ex, 4, (250, 224, 84))
        _put(s, f, ex + 1, 4, (236, 190, 24))
    _put(s, f, 2, 4, (30, 24, 14)); _put(s, f, 5, 4, (30, 24, 14))
    _put(s, f, 1, 4, (252, 232, 110)); _put(s, f, 6, 4, (252, 232, 110))
    for x in (1, 2, 5, 6):                  # the heavy lid above the hawk eye
        _put(s, f, x, 3, shade(skin, 0.7) if x in (1, 6) else brow)
    _put(s, f, 3, 5, skin_d); _put(s, f, 4, 5, shade(skin, 0.78))
    for x in (3, 4):
        _put(s, f, x, 6, (172, 116, 102))
    _put(s, f, 2, 6, shade(skin, 0.8)); _put(s, f, 5, 6, shade(skin, 0.8))
    _put(s, f, 1, 5, shade(skin, 0.93)); _put(s, f, 6, 5, shade(skin, 0.93))   # cheek lines
    _put(s, f, 3, 7, skin_d); _put(s, f, 4, 7, skin_d)

    # ---- robes: vertical black-and-white stripes, 2 px wide, black stand collar and cuffs, boots ----
    def stripe(x, y, w, h, off=0):
        base = ink if ((x + off) // 2) % 2 == 0 else paper
        return s.noisy(base, 0.03)

    for name in ("body", "jacket"):
        s.paint(p[name]["front"], lambda x, y, w, h: stripe(x, y, w, h))
        s.paint(p[name]["back"], lambda x, y, w, h: stripe(x, y, w, h))
        s.paint(p[name]["right"], lambda x, y, w, h: stripe(x, y, w, h, 1))
        s.paint(p[name]["left"], lambda x, y, w, h: stripe(x, y, w, h, 1))
        s.paint(p[name]["top"], lambda x, y, w, h: ink)
        s.paint(p[name]["bottom"], lambda x, y, w, h: ink)
    # stand collar (rows 0-1 black), robe front seam, a black hem
    for part in ("body", "jacket"):
        for face in ("front", "back", "right", "left"):
            s.paint(p[part][face], lambda x, y, w, h: ink if y < 2 else (shade(ink, 1.0) if y == h - 1 else None))
    s.paint(p["jacket"]["front"], lambda x, y, w, h: (60, 60, 68) if x == 4 and 2 <= y < h - 1 else None)
    s.paint(p["jacket"]["front"], lambda x, y, w, h: (200, 200, 208) if y == 1 and x in (3, 4) else None)

    # whistle on a gold cord: a V from the collar to a silver whistle at the breastbone
    jf = p["jacket"]["front"]
    cord = (214, 172, 52)
    for (x, y) in ((1, 2), (2, 3), (3, 4), (6, 2), (5, 3), (4, 4)):
        _put(s, jf, x, y, cord if (x + y) % 2 else shade(cord, 0.8))
    for (x, y, c) in ((3, 5, (206, 208, 216)), (4, 5, (232, 234, 240)), (3, 6, (150, 152, 164)),
                      (4, 6, (190, 192, 202))):
        _put(s, jf, x, y, c)
    _put(s, jf, 4, 5, (232, 234, 240)); _put(s, jf, 3, 7, (60, 60, 70))   # the mouthpiece's slot

    # legs: striped robe to the hem, black boots
    for leg, over in ((p["rleg"], p["rleg_o"]), (p["lleg"], p["lleg_o"])):
        for part in (leg, over):
            for face in ("front", "back", "right", "left"):
                s.paint(part[face], lambda x, y, w, h: ink if y >= h - 2 else (stripe(x, y, w, h, 1)
                                                                              if part is over or True else None))
        s.paint(leg["top"], lambda x, y, w, h: ink); s.paint(leg["bottom"], lambda x, y, w, h: shade(ink, 0.8))
        # the overlay's lower rows stay clear so the boots and the robe's hem read as separate things
        for face in ("front", "back", "right", "left"):
            s.paint(over[face], lambda x, y, w, h: None if y >= h - 2 else None)
        s.paint(over["front"], lambda x, y, w, h: (70, 70, 78) if y == h - 3 else None)
    # arms: striped sleeves, black cuffs (rows 8-9), bare hands (rows 10-11)
    for arm, over in ((p["rarm"], p["rarm_o"]), (p["larm"], p["larm_o"])):
        for face in ("front", "back", "right", "left"):
            s.paint(arm[face], lambda x, y, w, h: s.noisy(skin, 0.03) if y >= h - 2 else (
                ink if y in (8, 9) else stripe(x, y, w, h, 0)))
            s.paint(over[face], lambda x, y, w, h: (ink if y in (8, 9) else stripe(x, y, w, h, 0))
                    if y < h - 2 else None)
        s.paint(arm["top"], lambda x, y, w, h: ink); s.paint(arm["bottom"], lambda x, y, w, h: skin_d)
        s.paint(over["top"], lambda x, y, w, h: ink)
    path = os.path.join(OUT_DIR, "hooch.png")
    s.img.save(path)
    return path


def build_malfoy_young():
    """Young Draco Malfoy: slicked-back platinum hair, a pale pointed face and a sneer; black school robes over a
    grey vest, white collar and a Slytherin tie."""
    s = Skin("malfoy_young")
    rng = s.rng
    p = _parts()
    skin = (246, 229, 218)
    skin_d = shade(skin, 0.88)
    robe, vest = (22, 22, 27), (118, 120, 126)

    def hair(row=0.0, a=0.05):
        # slicked-back streaks: lighter toward the front, a faint dark comb line now and then
        base = (240, 237, 218)
        if rng.random() < 0.18:
            base = (214, 210, 190)
        elif rng.random() < 0.2:
            base = (252, 251, 242)
        return shade(base, 1 + rng.uniform(-a, a) + row)

    s.fill_box(p["head"], lambda x, y, w, h: s.noisy(skin, 0.02))
    s.paint(p["head"]["bottom"], lambda x, y, w, h: skin_d)
    # combed straight back: top rows get a long front-to-back streak pattern (columns share a tone)
    col_tone = [rng.uniform(-0.05, 0.07) for _ in range(8)]
    s.paint(p["head"]["top"], lambda x, y, w, h: hair(col_tone[x] + y * 0.005, 0.02))
    s.paint(p["head"]["back"], lambda x, y, w, h: hair(-0.03 - 0.01 * y) if y < 6 else None)
    s.paint(p["head"]["right"], lambda x, y, w, h: hair(-0.02) if (y < 2 or (x < 4 and y < 5)) else None)
    s.paint(p["head"]["left"], lambda x, y, w, h: hair(-0.02) if (y < 2 or (x > 3 and y < 5)) else None)
    # a high, slicked hairline: hair on row 0, a widow's peak dip at the centre on row 1, temples only on row 2
    s.paint(p["head"]["front"], lambda x, y, w, h: hair(0.04) if (y == 0 or (y == 1 and x in (0, 1, 3, 4, 6, 7)) or
                                                                  (y == 2 and x in (0, 7))) else None)
    # a thin hat layer: the nape and a little lift at the crown, kept flat to stay sleek
    s.paint(p["hat"]["back"], lambda x, y, w, h: hair(-0.04) if 1 <= y <= 4 and 1 <= x <= 6 else None)
    s.paint(p["hat"]["top"], lambda x, y, w, h: hair(0.05) if y >= 3 and rng.random() < 0.5 else None)
    s.paint(p["hat"]["right"], lambda x, y, w, h: hair(-0.05) if y <= 1 and x < 5 else None)
    s.paint(p["hat"]["left"], lambda x, y, w, h: hair(-0.05) if y <= 1 and x > 2 else None)

    # face: faint pale brows, cold grey-blue eyes, tight cheeks, a sneer (mouth curled up on the viewer's right)
    f = p["head"]["front"]
    brow = (206, 196, 164)
    for x in (1, 2):
        _put(s, f, x, 3, shade(brow, 0.9) if x == 2 else brow)
    for x in (5, 6):
        _put(s, f, x, 3, brow)
    _put(s, f, 2, 3, (176, 164, 134))           # brows drawn down toward the nose: a scowl
    _put(s, f, 5, 3, (176, 164, 134))
    for x in (1, 6):                                # the cold iris on the outside, the pupil beside the nose
        _put(s, f, x, 4, (122, 150, 186))
    for x in (2, 5):
        _put(s, f, x, 4, (38, 52, 78))
    for x in (1, 2, 5, 6):                          # a pale lid line, so the stare is hooded
        _put(s, f, x, 3, (214, 190, 178) if x in (1, 6) else (176, 164, 134))
    _put(s, f, 1, 5, shade(skin, 0.95)); _put(s, f, 6, 5, shade(skin, 0.95))      # faint under-eye shadow
    _put(s, f, 3, 5, skin_d); _put(s, f, 4, 5, shade(skin, 0.8))                   # the nose, shaded on one side
    lip = (200, 146, 142)
    _put(s, f, 2, 6, shade(skin, 0.8)); _put(s, f, 3, 6, shade(lip, 0.9)); _put(s, f, 4, 6, lip)
    _put(s, f, 5, 6, shade(skin, 0.78)); _put(s, f, 6, 5, shade(skin, 0.7))        # the curl of the sneer
    _put(s, f, 3, 7, skin_d)

    # body: black robes, grey vest, white collar, Slytherin tie
    def robe_fn(x, y, w, h):
        return s.noisy(robe if (x * 3 + y // 5) % 4 else shade(robe, 1.35), 0.03)
    s.fill_box(p["jacket"], robe_fn)
    s.fill_box(p["body"], lambda x, y, w, h: s.noisy(vest, 0.03))
    bf = p["body"]["front"]
    for x in (2, 3, 4, 5):
        for y in (0, 1):
            _put(s, bf, x, y, (236, 236, 240) if y == 0 or x in (3, 4) else (214, 214, 220))
    for y in range(2, 9):
        _put(s, bf, 3, y, (30, 100, 56) if y % 2 else (196, 198, 204))
        _put(s, bf, 4, y, (24, 82, 46) if y % 2 else (170, 172, 180))
    _put(s, bf, 3, 2, (22, 80, 44)); _put(s, bf, 4, 2, (22, 80, 44))
    # the robe hangs open at the front: the jacket overlay's centre columns are clear from the collar down
    s.paint(p["jacket"]["front"], lambda x, y, w, h: (0, 0, 0, 0) if x in (2, 3, 4, 5) and 0 < y < h - 3 and
            not (x in (2, 5) and y > 8) else None)
    for x in (2, 5):                              # the robe's lapel edge, a shade lighter
        for y in range(0, 9):
            _put(s, p["jacket"]["front"], x, y, shade(robe, 1.6))
    # a small Slytherin crest over the heart on the left lapel
    for (x, y, c) in ((6, 2, (196, 200, 206)), (6, 3, (30, 100, 56)), (7, 2, (30, 100, 56))):
        _put(s, p["jacket"]["front"], x, y, c)
    s.paint(p["jacket"]["top"], lambda x, y, w, h: robe)
    # legs: grey trousers under black robe ends, black shoes
    for leg, over in ((p["rleg"], p["rleg_o"]), (p["lleg"], p["lleg_o"])):
        s.fill_box(leg, lambda x, y, w, h: s.noisy((70, 72, 78), 0.03) if y < h - 2 else s.noisy((14, 14, 16), 0.1))
        s.paint(leg["bottom"], lambda x, y, w, h: (10, 10, 12))
        for face in ("front", "back", "right", "left"):
            s.paint(over[face], lambda x, y, w, h: robe_fn(x, y, w, h) if y < 7 else None)
    for arm, over in ((p["rarm"], p["rarm_o"]), (p["larm"], p["larm_o"])):
        s.fill_box(arm, lambda x, y, w, h: s.noisy(skin, 0.02) if y >= h - 2 else (
            (236, 236, 240) if y == h - 3 else s.noisy(vest, 0.03)))
        s.paint(arm["bottom"], lambda x, y, w, h: skin_d)
        for face in ("front", "back", "right", "left"):
            s.paint(over[face], lambda x, y, w, h: robe_fn(x, y, w, h) if y < h - 3 else None)
        s.paint(over["top"], lambda x, y, w, h: robe)
    path = os.path.join(OUT_DIR, "malfoy_young.png")
    s.img.save(path)
    return path


def generate_flying_skins():
    from skin_preview import preview, head_closeup
    outs = [build_hooch(), build_malfoy_young()]
    pdir = os.path.join(ROOT, "build_scripts", "pensieve_scenes", "previews")
    for path in outs:
        n = os.path.basename(path)[:-4]
        preview(path, os.path.join(pdir, "skin_" + n + ".png"))
        head_closeup(path, os.path.join(pdir, "skin_" + n + "_head.png"))
    return ["hooch", "malfoy_young"]


# ---------------------------------------------------------------------------------------------------------------
# Battle of Hogwarts cast (Deathly Hallows Part 2 look), generic battle students, Death Eaters, Snatchers, teachers.
# Seen at a distance: strong silhouettes and readable colours first, then a face that still reads up close.
#
# SLIM skins (3-px arms; use {"texture": "<name>", "slim": true} in a scene) are listed in BATTLE_SLIM below:
#   mcgonagall_dh, ginny, luna, molly, bellatrix, battle_*_girl (all four houses), teacher_b.
# All other battle skins are classic 4-px arms.
# mcgonagall_dh and hagrid_dh carry a _dh suffix because mcgonagall.png and hagrid.png are the downloaded skins
# existing memories already use (this script never regenerates those).
# ---------------------------------------------------------------------------------------------------------------

BATTLE_SLIM = {"mcgonagall_dh", "ginny", "luna", "molly", "bellatrix",
               "battle_gryffindor_girl", "battle_hufflepuff_girl", "battle_ravenclaw_girl", "battle_slytherin_girl",
               "teacher_b"}

_HAIR_STYLE = {  # side_top, back_cols, back_depth, back_rows, sideburn_rows
    "short": (2, 2, 5, 6, 3), "scruffy": (3, 3, 6, 7, 3), "bun": (2, 2, 5, 6, 3), "long": (2, 4, 8, 8, 7),
    "wild": (3, 4, 8, 8, 6), "pinned": (2, 2, 5, 6, 3), "wisp": (2, 2, 6, 6, 3), "slick": (2, 2, 4, 5, 2),
}


def _bset(s, rect, x, y, c):
    x0, y0, w, h = rect
    if 0 <= x < w and 0 <= y < h:
        s.img.putpixel((x0 + x, y0 + y), c if len(c) == 4 else (c[0], c[1], c[2], 255))


def _bget(s, rect, x, y):
    return s.img.getpixel((rect[0] + x, rect[1] + y))


def _cl(s, base, x, y, w, h, off=0):
    """Cloth with vertical folds, a darker hem and a lit shoulder; dark cloth gets a stronger fold contrast."""
    lum = (base[0] + base[1] + base[2]) / 3
    amp = 1.0 if lum > 110 else 1.6 if lum > 50 else 2.4
    m = (x + off) % 4
    k = 1 - 0.17 * amp if m == 0 else 1 + 0.12 * amp if m == 2 else 1.0
    if y > h - 3:
        k *= 0.9
    if y < 2:
        k *= 1.05
    c = shade(base, k + s.rng.uniform(-0.03, 0.03))
    if lum <= 50 and m == 2:
        c = (c[0] + 5, c[1] + 5, c[2] + 7, 255)
    return c


def _bparts(slim):
    aw = 3 if slim else 4
    return dict(head=box(0, 0, 8, 8, 8), hat=box(32, 0, 8, 8, 8), body=box(16, 16, 8, 12, 4),
                jacket=box(16, 32, 8, 12, 4), rleg=box(0, 16, 4, 12, 4), lleg=box(16, 48, 4, 12, 4),
                rleg_o=box(0, 32, 4, 12, 4), lleg_o=box(0, 48, 4, 12, 4), rarm=box(40, 16, aw, 12, 4),
                larm=box(32, 48, aw, 12, 4), rarm_o=box(40, 32, aw, 12, 4), larm_o=box(48, 48, aw, 12, 4))


def _bhair(s, B, style, c, streak=None):
    head, hat, rng = B["head"], B["hat"], s.rng
    hp = lambda a=0.12: s.noisy(c, a)
    st, bc, bd, br, burn = _HAIR_STYLE[style]
    s.paint(head["top"], lambda x, y, w, h: hp())
    s.paint(head["back"], lambda x, y, w, h: hp() if y < br else None)

    def side(right):
        def fn(x, y, w, h):
            fb = x if right else 7 - x
            if y < st or (fb < bc and y < bd) or (fb == 7 and y < burn):
                return hp()
            return None
        return fn
    s.paint(head["right"], side(True))
    s.paint(head["left"], side(False))

    def front(x, y):
        if y == 0:
            return True
        if style == "short" or style == "slick":
            return y == 1 and (x in (0, 1, 2, 4, 5, 7) or rng.random() < 0.3) if style == "short" else (
                y == 1 and x in (0, 7))
        if style == "scruffy":
            if y == 1:
                return rng.random() < 0.85 or x in (0, 7)
            if y == 2:
                return x in (0, 7) or (x in (1, 3, 6) and rng.random() < 0.5)
            return y == 3 and x in (0, 7)
        if style in ("bun", "pinned"):
            return y == 1 and x in (0, 7) or (y == 1 and style == "pinned" and x in (1, 6))
        if style in ("long", "wild"):
            if y == 1:
                return True
            if y == 2:
                return x in (0, 1, 6, 7) or (style == "wild" and rng.random() < 0.5)
            return x in (0, 7) and y <= 5
        if style == "wisp":
            return y == 1 and x in (0, 1, 3, 6, 7)
        return False
    s.paint(head["front"], lambda x, y, w, h: hp() if front(x, y) else None)
    s.paint(head["top"], lambda x, y, w, h: shade(c, 1.2) if y == 3 and x in (1, 2, 5) else None)

    # hat layer volume
    def vol(rows, p):
        return lambda x, y, w, h: shade(c, 0.75 + 0.5 * rng.random()) if (y < rows and rng.random() < p) else None
    if style in ("scruffy", "wisp"):
        for f, r in (("top", 8), ("back", 4), ("right", 3), ("left", 3)):
            s.paint(hat[f], vol(r, 0.45))
        s.paint(hat["front"], lambda x, y, w, h: shade(c, 0.8 + 0.4 * rng.random()) if y == 0 and rng.random() < 0.5 else None)
    elif style == "wild":
        s.paint(hat["top"], vol(8, 0.9))
        s.paint(hat["back"], vol(8, 0.85))
        s.paint(hat["right"], lambda x, y, w, h: vol(8, 0.8)(x, y, w, h) if (y < 4 or x < 5) else None)
        s.paint(hat["left"], lambda x, y, w, h: vol(8, 0.8)(x, y, w, h) if (y < 4 or x > 2) else None)
        s.paint(hat["front"], lambda x, y, w, h: vol(3, 0.7)(x, y, w, h) if (y < 2 or x in (0, 7)) else None)
        if streak:  # one pale streak sweeping from the brow over the crown and down the back
            for (f, xs, ys) in (("front", (2,), (0, 1)), ("top", (2, 3), range(8)), ("back", (4, 5), range(8))):
                for xx in xs:
                    for yy in ys:
                        _bset(s, hat[f], xx, yy, shade(streak, 0.85 + 0.3 * rng.random()))
    elif style == "long":
        s.paint(hat["back"], lambda x, y, w, h: hp() if y >= 1 else None)
        s.paint(hat["right"], lambda x, y, w, h: hp() if y >= 3 and x < 4 else None)
        s.paint(hat["left"], lambda x, y, w, h: hp() if y >= 3 and x > 3 else None)
        s.paint(hat["top"], vol(8, 0.25))
    elif style == "bun":  # a knot at the crown-back
        for yy in range(5, 8):
            for xx in range(2, 6):
                _bset(s, hat["top"], xx, yy, shade(c, 1.15 - 0.1 * (yy - 5) + rng.uniform(-0.05, 0.05)))
        for yy in range(0, 3):
            for xx in range(2, 6):
                _bset(s, hat["back"], xx, yy, shade(c, 1.0 - 0.12 * yy + rng.uniform(-0.05, 0.05)))
    elif style == "pinned":
        s.paint(hat["top"], vol(8, 0.9))
        s.paint(hat["back"], vol(5, 0.9))
        s.paint(hat["right"], vol(3, 0.7))
        s.paint(hat["left"], vol(3, 0.7))
        s.paint(hat["front"], lambda x, y, w, h: shade(c, 0.9 + 0.3 * rng.random()) if y == 0 else None)


def _bface(s, B, skin, eyes, brow, lip, bald=False, tags=(), hair=None):
    head, hat, rng = B["head"], B["hat"], s.rng
    F = head["front"]
    sk_d = shade(skin, 0.84)
    white = (236, 236, 238)
    heavy, stern, lash = "heavy" in tags, "stern" in tags, "lashes" in tags
    for y in range(8):
        for x in range(8):
            c = None
            if y == 4 and x in (1, 2, 5, 6):
                c = eyes if x in (2, 5) else white
                if "wide" in tags:
                    c = eyes if x in (2, 5) else shade(eyes, 1.25)
            elif y == 3 and x in (1, 2, 5, 6):
                c = brow
                if stern and x in (2, 5):
                    c = shade(brow, 0.8)
                if heavy:
                    c = (74, 56, 76)
                if "wide" in tags:
                    c = shade(skin, 0.9) if bald else shade(brow, 1.1)
            elif y == 5 and x in (3, 4):
                c = sk_d if x == 3 else shade(skin, 0.9)
            elif y == 6 and x in (2, 3, 4, 5):
                c = lip
                if stern and x in (3, 4):
                    c = shade(lip, 0.7)
            elif lash and y == 5 and x in (1, 6) and False:
                c = None
            if c is not None:
                _bset(s, F, x, y, c)
    if "gaunt" in tags:
        for y in (5, 6):
            for x in (1, 6):
                _bset(s, F, x, y, shade(skin, 0.68))
        for x in (1, 2, 5, 6):
            _bset(s, F, x, 5, shade(skin, 0.74))   # sockets / hollow
        _bset(s, F, 0, 5, shade(skin, 0.78)); _bset(s, F, 7, 5, shade(skin, 0.78))
    if "wrinkles" in tags:
        for x in (0, 7):
            _bset(s, F, x, 4, shade(skin, 0.8)); _bset(s, F, x, 6, shade(skin, 0.82))
        for x in (1, 6):
            _bset(s, F, x, 5, shade(skin, 0.88))
    if "rosy" in tags:
        cheek = (min(255, skin[0] + 8), int(skin[1] * 0.78), int(skin[2] * 0.78))
        for x in (1, 6):
            _bset(s, F, x, 5, cheek)
        for x in (0, 7):
            _bset(s, F, x, 5, shade(cheek, 1.0))
    if "freckles" in tags:
        for fx, fy in [(1, 5), (2, 5), (5, 5), (6, 5), (3, 4), (4, 4), (0, 5), (7, 5), (2, 6), (5, 6)]:
            if rng.random() < 0.85:
                _bset(s, F, fx, fy, shade(skin, 0.62) if sum(skin) > 520 else shade(skin, 0.72))
    if "stubble" in tags:
        for y in (5, 6, 7):
            for x in range(8):
                if _bget(s, F, x, y)[3] and rng.random() < 0.6 and not (y == 6 and x in (3, 4)):
                    _bset(s, F, x, y, shade(skin, 0.66))
        for y in (4, 5, 6, 7):
            for x in (0, 7):
                _bset(s, F, x, y, shade(skin, 0.7))
    if "walrus" in tags:   # big drooping moustache hiding the mouth
        mo = hair or (210, 196, 160)
        for x in range(1, 7):
            _bset(s, F, x, 5, shade(mo, 0.95 + 0.1 * rng.random()))
        for x in (1, 2, 5, 6):
            _bset(s, F, x, 6, shade(mo, 0.85 + 0.15 * rng.random()))
        _bset(s, F, 3, 6, (120, 70, 66)); _bset(s, F, 4, 6, (120, 70, 66))
    if "moustache" in tags:   # a bushy white moustache, mouth still showing below
        mo = hair or (236, 236, 240)
        for x in range(1, 7):
            _bset(s, F, x, 5, shade(mo, 0.92 + 0.12 * rng.random()))
        for x in (1, 6):
            _bset(s, F, x, 6, shade(mo, 0.85))
    if "beard" in tags:   # Hagrid: the beard swallows everything below the eyes and half the cheeks
        bc = hair or (14, 12, 14)
        for y in range(3, 8):
            for x in range(8):
                if y <= 4 and x not in (0, 7):
                    continue
                if y == 5 and x in (3, 4):
                    _bset(s, F, x, y, shade(skin, 0.95))   # the nose, ruddy
                    continue
                _bset(s, F, x, y, shade(bc, 0.8 + 0.5 * rng.random()))
        _bset(s, F, 3, 6, (60, 30, 30)); _bset(s, F, 4, 6, (60, 30, 30))   # mouth shows through the hair
        # beard juts out on the hat layer: sides and chin
        for y in range(4, 8):
            for x in range(8):
                if y == 4 and x not in (0, 7):
                    continue
                if (y == 5 and x in (2, 3, 4, 5)) or (y == 6 and x in (3, 4)):
                    continue
                _bset(s, hat["front"], x, y, shade(bc, 0.8 + 0.5 * rng.random()))
        for side in ("right", "left"):
            s.paint(hat[side], lambda x, y, w, h, side=side: shade(bc, 0.8 + 0.5 * rng.random()) if (
                y >= 4 and ((side == "right" and x >= 3) or (side == "left" and x <= 4))) else None)
        s.paint(hat["bottom"], lambda x, y, w, h: shade(bc, 0.8 + 0.5 * rng.random()))
    if "shortbeard" in tags:   # a trimmed grey beard along the jaw
        bc = hair or (170, 170, 176)
        for y in (5, 6, 7):
            for x in range(8):
                if y == 5 and x not in (0, 1, 6, 7):
                    continue
                if y == 6 and x in (3, 4):
                    continue
                _bset(s, F, x, y, shade(bc, 0.85 + 0.25 * rng.random()))
    if "glasses" in tags:
        gc = (150, 150, 158)
        for x in (1, 2, 5, 6):
            _bset(s, F, x, 3, gc) if False else None
        for x in range(8):
            if x in (0, 3, 4, 7):
                _bset(s, F, x, 4, gc)
            if x in (1, 2, 5, 6):
                _bset(s, F, x, 5, gc)


def _bdirt(s, rect, n, col, rng_x=(0, 8), rng_y=(0, 8), a=0.0):
    x0, x1 = rng_x
    y0, y1 = rng_y
    for _ in range(n):
        x, y = s.rng.randrange(x0, x1), s.rng.randrange(y0, y1)
        p = _bget(s, rect, x, y)
        if p[3]:
            mix = s.rng.uniform(0.35, 0.65)
            _bset(s, rect, x, y, tuple(int(p[i] * (1 - mix) + col[i] * mix) for i in range(3)))


def _bscratch(s, B, skin, x, y, length=3):
    for i in range(length):
        _bset(s, B["head"]["front"], x + i, y + (i // 2), (150, 40, 40) if i != 1 else (120, 30, 32))


def _bcap(s, B, col, band=None, rows=2):
    """A cap on the hat layer: crown plus a band of `rows` around the head, brow left open."""
    hat, rng = B["hat"], s.rng
    for f in ("top", "back", "right", "left"):
        s.paint(hat[f], lambda x, y, w, h, f=f: (_cl(s, col, x, y, w, h) if (f == "top" or y < rows) else None))
    s.paint(hat["front"], lambda x, y, w, h: _cl(s, col, x, y, w, h) if y < rows else None)
    if band:
        for f in ("back", "right", "left", "front"):
            s.paint(hat[f], lambda x, y, w, h: band if y == rows - 1 else None)


def _bwitchhat(s, B, col, band):
    """Tall pointed witch's hat seen as a texture: brim ring around the head, crown shaded to a bright seam."""
    hat = B["hat"]
    s.paint(hat["top"], lambda x, y, w, h: shade(col, 0.7 + 0.18 * (1 - abs(x - 3.5) / 3.5) + 0.0 * y)
            if True else None)
    for yy in range(8):
        _bset(s, hat["top"], 3, yy, shade(col, 1.9)); _bset(s, hat["top"], 4, yy, shade(col, 1.5))
    for f in ("back", "right", "left"):
        s.paint(hat[f], lambda x, y, w, h: _cl(s, col, x, y, w, h) if y < 2 else None)
    s.paint(hat["front"], lambda x, y, w, h: _cl(s, col, x, y, w, h) if y < 2 else None)
    for f in ("front", "back", "right", "left"):
        s.paint(hat[f], lambda x, y, w, h: band if y == 1 else None)
    # the brim's underside hangs lower at the front and sides, in shadow
    s.paint(hat["front"], lambda x, y, w, h: shade(col, 0.45) if (y == 2 and x in (0, 1, 2, 5, 6, 7)) else None)
    s.paint(hat["right"], lambda x, y, w, h: shade(col, 0.45) if y == 2 else None)
    s.paint(hat["left"], lambda x, y, w, h: shade(col, 0.45) if y == 2 else None)


def _bbody(s, B, *, base, sleeve, skin, legs, shoes, arm_o=None, jacket=None, jopen=False, lining=None, hem=None,
           cuff=None, leg_o=None, leg_o_rows=10, ragged=False, off=0, hand_rows=2, body_trim=None):
    rng = s.rng
    s.fill_box(B["body"], lambda x, y, w, h: _cl(s, base, x, y, w, h, off))
    for a in ("rarm", "larm"):
        s.fill_box(B[a], lambda x, y, w, h: s.noisy(skin, 0.03) if y >= h - hand_rows else _cl(s, sleeve, x, y, w, h, off))
        if cuff:
            s.paint(B[a]["front"], lambda x, y, w, h: cuff if y == h - hand_rows - 1 else None)
            s.paint(B[a]["back"], lambda x, y, w, h: cuff if y == h - hand_rows - 1 else None)
    for l in ("rleg", "lleg"):
        s.fill_box(B[l], lambda x, y, w, h: s.noisy(shoes, 0.2) if y >= h - 2 else _cl(s, legs, x, y, w, h, off + 1))
    if jacket is not None:
        s.fill_box(B["jacket"], lambda x, y, w, h: _cl(s, jacket, x, y, w, h, off))
        if jopen:
            s.paint(B["jacket"]["front"], lambda x, y, w, h: (0, 0, 0, 0) if (2 <= x <= 5 and y <= h - 3) else (
                shade(lining or jacket, 0.6) if x in (1, 6) and y <= h - 3 else None))
        if hem:
            for f in ("front", "back", "right", "left"):
                s.paint(B["jacket"][f], lambda x, y, w, h: hem if y == h - 1 else None)
        if ragged:
            for f in ("front", "back", "right", "left"):
                s.paint(B["jacket"][f], lambda x, y, w, h: (0, 0, 0, 0) if (y == h - 1 and rng.random() < 0.5) or (
                    y == h - 2 and rng.random() < 0.15) else None)
    if arm_o is not None:
        for a in ("rarm_o", "larm_o"):
            s.fill_box(B[a], lambda x, y, w, h: _cl(s, arm_o, x, y, w, h, off) if y < h - hand_rows else None)
            if cuff:
                s.paint(B[a]["front"], lambda x, y, w, h: cuff if y == h - hand_rows - 1 else None)
                s.paint(B[a]["back"], lambda x, y, w, h: cuff if y == h - hand_rows - 1 else None)
    if leg_o is not None:
        for l in ("rleg_o", "lleg_o"):
            s.fill_box(B[l], lambda x, y, w, h: _cl(s, leg_o, x, y, w, h, off + 2) if y < leg_o_rows else None)
            if hem:
                s.paint(B[l]["front"], lambda x, y, w, h: hem if y == leg_o_rows - 1 else None)
            if ragged:
                for f in ("front", "back", "right", "left"):
                    s.paint(B[l][f], lambda x, y, w, h: (0, 0, 0, 0) if (
                        y >= leg_o_rows - 2 and y < leg_o_rows and rng.random() < 0.4) else None)


def _bsoot(s, B, n, col=(26, 22, 22), parts=("body", "rarm", "larm", "rleg", "lleg", "jacket", "rarm_o", "larm_o")):
    for p in parts:
        for f in ("front", "back", "right", "left"):
            rect = B[p][f]
            for _ in range(max(1, n // len(parts))):
                x, y = s.rng.randrange(rect[2]), s.rng.randrange(rect[3])
                px = _bget(s, rect, x, y)
                if px[3]:
                    k = s.rng.uniform(0.3, 0.55)
                    _bset(s, rect, x, y, tuple(int(px[i] * (1 - k) + col[i] * k) for i in range(3)))


def _bfinish(s, name):
    s.img.save(os.path.join(OUT_DIR, name + ".png"))


def _bnew(name, seed):
    s = Skin(seed)
    return s, _bparts(name in BATTLE_SLIM)


def _bhead_base(s, B, skin):
    s.fill_box(B["head"], lambda x, y, w, h: s.noisy(skin, 0.03))
    s.paint(B["head"]["bottom"], lambda x, y, w, h: shade(skin, 0.82))


# --- named cast ---------------------------------------------------------------------------------------------

def build_mcgonagall_dh():
    s, B = _bnew("mcgonagall_dh", 701)
    skin = (232, 206, 188)
    _bhead_base(s, B, skin)
    _bhair(s, B, "bun", (156, 156, 162))
    _bface(s, B, skin, (60, 90, 80), (110, 110, 116), (150, 96, 96), tags=("stern", "wrinkles"))
    emerald, deep = (22, 88, 54), (12, 46, 30)
    _bwitchhat(s, B, (18, 18, 22), (22, 88, 54))
    _bbody(s, B, base=emerald, sleeve=emerald, skin=skin, legs=deep, shoes=(20, 18, 20), arm_o=emerald,
           jacket=emerald, jopen=True, lining=deep, hem=deep, cuff=deep, leg_o=emerald, leg_o_rows=10)
    # high collar and a thin silver clasp
    s.paint(B["body"]["front"], lambda x, y, w, h: shade(deep, 1.3) if y < 2 and 1 <= x <= 6 else None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (190, 192, 198) if (y == 0 and x in (3, 4)) else None)
    s.paint(B["body"]["front"], lambda x, y, w, h: (22, 20, 24) if (x in (3, 4) and y > 1) else None)
    _bsoot(s, B, 8, (20, 40, 30))
    _bfinish(s, "mcgonagall_dh")


def build_neville_dh():
    s, B = _bnew("neville_dh", 702)
    skin = (236, 200, 172)
    _bhead_base(s, B, skin)
    _bhair(s, B, "scruffy", (98, 68, 42))
    _bface(s, B, skin, (96, 112, 150), (84, 58, 38), (186, 118, 108), tags=("rosy",))
    _bdirt(s, B["head"]["front"], 14, (70, 54, 44))
    _bdirt(s, B["head"]["front"], 4, (60, 40, 36), (0, 8), (4, 8))
    _bscratch(s, B, skin, 5, 5, 3)
    _bscratch(s, B, skin, 1, 6, 2)
    grey, shirt = (118, 118, 126), (230, 228, 222)
    _bbody(s, B, base=grey, sleeve=grey, skin=skin, legs=(48, 46, 56), shoes=(30, 24, 22), cuff=shade(grey, 0.7))
    # white shirt collar and a torn gap down the front
    s.paint(B["body"]["front"], lambda x, y, w, h: shirt if (y < 3 and 2 <= x <= 5 and not (y == 2 and x in (2, 5))) else None)
    s.paint(B["body"]["front"], lambda x, y, w, h: shade(grey, 0.62) if (y == 2 and x in (3, 4)) else None)
    # tears: shirt shows through the jumper, one sleeve ripped to the elbow
    for (x, y) in [(1, 6), (2, 6), (1, 7), (5, 8), (6, 8), (6, 9)]:
        _bset(s, B["body"]["front"], x, y, shirt if (x + y) % 2 else shade(shirt, 0.8))
    for (x, y) in [(0, 3), (1, 3), (1, 4), (2, 4), (1, 5)]:
        _bset(s, B["rarm"]["front"], x, y, skin if y > 3 else shirt)
    for (x, y) in [(0, 2), (0, 3)]:
        _bset(s, B["larm"]["front"], x, y, shirt)
    s.paint(B["rarm"]["right"], lambda x, y, w, h: skin if (y in (4, 5) and x in (1, 2)) else None)
    _bsoot(s, B, 24, (34, 28, 26), parts=("body", "rarm", "larm", "rleg", "lleg"))
    _bfinish(s, "neville_dh")


def build_ron_dh():
    s, B = _bnew("ron_dh", 703)
    skin = (240, 208, 184)
    _bhead_base(s, B, skin)
    _bhair(s, B, "scruffy", (182, 78, 34))
    _bface(s, B, skin, (66, 106, 156), (160, 70, 34), (190, 120, 110), tags=("freckles",))
    _bdirt(s, B["head"]["front"], 9, (80, 62, 50))
    maroon, dark = (96, 46, 42), (36, 36, 46)
    _bbody(s, B, base=dark, sleeve=maroon, skin=skin, legs=(52, 56, 74), shoes=(34, 26, 22), arm_o=maroon,
           jacket=maroon, jopen=True, lining=(60, 30, 30), hem=shade(maroon, 0.7), cuff=shade(maroon, 0.7))
    # grey shirt collar peeking, a buttoned inner shirt strip
    s.paint(B["body"]["front"], lambda x, y, w, h: (150, 150, 156) if (y < 2 and 2 <= x <= 5) else (
        shade(dark, 1.5) if x in (3, 4) and y % 3 == 1 else None))
    _bsoot(s, B, 30, (40, 32, 28))
    _bfinish(s, "ron_dh")


def build_ginny():
    s, B = _bnew("ginny", 704)
    skin = (242, 212, 190)
    _bhead_base(s, B, skin)
    _bhair(s, B, "long", (190, 84, 38))
    _bface(s, B, skin, (110, 80, 48), (160, 72, 34), (196, 112, 112), tags=("freckles", "lashes"))
    _bdirt(s, B["head"]["front"], 5, (86, 66, 54))
    jk = (38, 36, 46)
    _bbody(s, B, base=(60, 40, 50), sleeve=jk, skin=skin, legs=(54, 58, 76), shoes=(30, 24, 24), arm_o=jk, jacket=jk,
           jopen=True, lining=(70, 30, 36), hem=shade(jk, 1.7), cuff=shade(jk, 1.7))
    s.paint(B["body"]["front"], lambda x, y, w, h: (190, 150, 130) if (y < 2 and 2 <= x <= 5) else None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: shade(jk, 1.9) if (y < 2 and x in (1, 6)) else None)
    _bsoot(s, B, 14, (34, 28, 26))
    _bfinish(s, "ginny")


def build_luna():
    s, B = _bnew("luna", 705)
    skin = (248, 224, 206)
    _bhead_base(s, B, skin)
    _bhair(s, B, "long", (232, 218, 168))
    _bface(s, B, skin, (150, 172, 196), (190, 172, 124), (210, 138, 140), tags=("wide", "lashes"))
    # radish earrings on the sides of the head
    for side in ("right", "left"):
        _bset(s, B["head"][side], 4, 5, (214, 70, 60))
        _bset(s, B["head"][side], 4, 6, (230, 120, 100))
    blue, lilac = (92, 108, 178), (156, 130, 200)
    _bbody(s, B, base=blue, sleeve=blue, skin=skin, legs=(96, 90, 140), shoes=(70, 46, 40), arm_o=lilac,
           jacket=lilac, jopen=True, lining=shade(lilac, 0.7), hem=shade(lilac, 0.7), cuff=shade(blue, 0.8),
           leg_o=(110, 100, 170), leg_o_rows=6)
    s.paint(B["body"]["front"], lambda x, y, w, h: (232, 222, 214) if (y < 2 and 2 <= x <= 5) else None)
    _bsoot(s, B, 12, (80, 70, 70))
    _bfinish(s, "luna")


def build_seamus():
    s, B = _bnew("seamus", 706)
    skin = (240, 206, 178)
    _bhead_base(s, B, skin)
    _bhair(s, B, "scruffy", (186, 146, 90))
    _bface(s, B, skin, (96, 120, 90), (140, 104, 62), (190, 118, 104), tags=("rosy",))
    _bdirt(s, B["head"]["front"], 18, (28, 24, 24))
    _bdirt(s, B["head"]["front"], 6, (28, 24, 24), (0, 8), (0, 3))
    red, gold = (148, 32, 40), (212, 168, 48)
    _bbody(s, B, base=red, sleeve=red, skin=skin, legs=(46, 46, 56), shoes=(28, 24, 22), cuff=gold)
    s.paint(B["body"]["front"], lambda x, y, w, h: (232, 230, 226) if (y < 2 and 2 <= x <= 5) else (
        gold if y == h - 1 else None))
    s.paint(B["body"]["front"], lambda x, y, w, h: (24, 24, 28) if (y == 2 and x in (3, 4)) else None)
    s.paint(B["body"]["back"], lambda x, y, w, h: gold if y == h - 1 else None)
    _bsoot(s, B, 36, (22, 20, 20))
    _bfinish(s, "seamus")


def build_molly():
    s, B = _bnew("molly", 707)
    skin = (242, 208, 184)
    _bhead_base(s, B, skin)
    _bhair(s, B, "pinned", (188, 86, 40))
    _bface(s, B, skin, (96, 70, 44), (160, 80, 44), (196, 92, 100), tags=("rosy", "lashes"))
    card, skirt, blouse = (112, 70, 90), (78, 50, 54), (226, 210, 184)
    _bbody(s, B, base=blouse, sleeve=card, skin=skin, legs=skirt, shoes=(50, 34, 30), arm_o=card, jacket=card,
           jopen=True, lining=shade(card, 0.7), hem=shade(card, 0.65), cuff=(214, 190, 160),
           leg_o=skirt, leg_o_rows=10)
    # buttons on the cardigan edges, a small brooch
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (206, 176, 120) if (x == 1 and y in (2, 5, 8)) else None)
    s.paint(B["body"]["front"], lambda x, y, w, h: (150, 80, 100) if (x in (3, 4) and y == 1) else None)
    # a warm stripe of knit across the cardigan sleeves
    for a in ("rarm_o", "larm_o"):
        for f in ("front", "back", "right", "left"):
            s.paint(B[a][f], lambda x, y, w, h: (150, 96, 70) if y in (3, 4) else None)
    _bsoot(s, B, 6, (90, 74, 70))
    _bfinish(s, "molly")


def build_bellatrix():
    s, B = _bnew("bellatrix", 708)
    skin = (234, 226, 226)
    _bhead_base(s, B, skin)
    _bhair(s, B, "wild", (14, 12, 16), streak=(176, 176, 182))
    _bface(s, B, skin, (36, 28, 30), (22, 18, 20), (96, 22, 42), tags=("heavy", "lashes", "stern"))
    # dark smudged eye-shadow beneath the eyes and cheek hollows
    for x in (1, 2, 5, 6):
        _bset(s, B["head"]["front"], x, 5, (176, 160, 168))
    black, lace = (20, 18, 24), (62, 58, 68)
    _bbody(s, B, base=black, sleeve=(150, 138, 146), skin=skin, legs=(26, 24, 30), shoes=(18, 16, 18), arm_o=black,
           jacket=black, jopen=False, hem=lace, cuff=lace, leg_o=black, leg_o_rows=10, ragged=True)
    # corset: boning, lacing criss-cross at the centre of the back and a sweetheart neckline at the front
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (0, 0, 0, 0) if y > 6 else None)
    s.paint(B["body"]["front"], lambda x, y, w, h: skin if (y < 2 and 1 <= x <= 6 and not (y == 1 and x in (1, 6))) else (
        shade(black, 2.0) if (x in (2, 5) and y >= 2) else None))
    s.paint(B["jacket"]["front"], lambda x, y, w, h: shade(black, 1.9) if (x in (2, 5) and 0 <= y <= 6) else (
        (118, 114, 128) if (x in (3, 4) and y <= 6 and (x + y) % 2 == 0) else (
            shade(black, 1.4) if y <= 6 and x in (3, 4) else None)))
    s.paint(B["jacket"]["back"], lambda x, y, w, h: (118, 114, 128) if (x in (3, 4) and y <= 6 and (x + y) % 2 == 0)
            else (shade(black, 1.4) if (y <= 6 and x in (3, 4)) else None))
    # lace sleeves: a net of black over pale skin
    for a in ("rarm_o", "larm_o"):
        for f in ("front", "back", "right", "left"):
            s.paint(B[a][f], lambda x, y, w, h: (lace if (x + y) % 2 == 0 else (0, 0, 0, 0)) if (y < h - 2 and y > 1)
                    else (black if y <= 1 else None))
        for f in ("front", "back", "right", "left"):
            s.paint(B[a][f], lambda x, y, w, h: lace if y == h - 3 else None)
    # the pale inner arm shows through the lace
    for a in ("rarm", "larm"):
        s.fill_box(B[a], lambda x, y, w, h: s.noisy((206, 196, 200), 0.03) if y < h - 2 else s.noisy(skin, 0.03))
    _bfinish(s, "bellatrix")


def build_hagrid_dh():
    s, B = _bnew("hagrid_dh", 709)
    skin = (214, 160, 130)
    _bhead_base(s, B, skin)
    _bhair(s, B, "wild", (14, 12, 14))
    _bface(s, B, skin, (30, 24, 20), (14, 12, 14), (120, 60, 56), tags=("beard", "wrinkles"), hair=(16, 14, 16))
    coat, dk = (98, 68, 42), (62, 42, 28)
    _bbody(s, B, base=(120, 90, 64), sleeve=coat, skin=skin, legs=(60, 44, 32), shoes=(30, 22, 18), arm_o=coat,
           jacket=coat, jopen=True, lining=dk, hem=shade(coat, 0.72), cuff=shade(coat, 0.7), leg_o=None)
    # shirt, belt with a buckle and brass buttons, big patched pockets, worn patches
    s.paint(B["body"]["front"], lambda x, y, w, h: (150, 130, 100) if (y < 3 and 2 <= x <= 5) else None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (40, 28, 20) if y == 6 else (
        (196, 164, 76) if (x in (1, 6) and y in (1, 3, 5, 8, 10)) else (
            shade(dk, 0.9) if (y in (7, 8, 9) and x in (0, 7)) else None)))
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (206, 176, 80) if (y == 6 and x in (2, 5)) else None)
    s.paint(B["jacket"]["back"], lambda x, y, w, h: shade(dk, 0.85) if (3 <= x <= 5 and 2 <= y <= 6) else None)
    _bdirt(s, B["jacket"]["front"], 8, (50, 36, 28))
    _bsoot(s, B, 20, (44, 32, 24), parts=("jacket", "rarm_o", "larm_o", "rleg", "lleg"))
    _bfinish(s, "hagrid_dh")


def build_kingsley():
    s, B = _bnew("kingsley", 710)
    skin = (98, 64, 46)
    _bhead_base(s, B, skin)
    _bface(s, B, skin, (36, 26, 22), shade(skin, 0.62), (140, 70, 66), bald=True, tags=("stern",))
    for x in (1, 2, 5, 6):   # heavy brow ridge and a shaved scalp sheen
        _bset(s, B["head"]["front"], x, 3, shade(skin, 0.6))
    for x in range(8):
        _bset(s, B["head"]["top"], x, 3, shade(skin, 1.18))
    _bcap(s, B, (44, 38, 112), band=(190, 162, 72), rows=2)
    purple, blue = (70, 52, 138), (36, 40, 110)
    _bbody(s, B, base=blue, sleeve=purple, skin=skin, legs=(34, 32, 70), shoes=(20, 18, 24), arm_o=purple,
           jacket=purple, jopen=True, lining=shade(blue, 0.8), hem=(190, 162, 72), cuff=(190, 162, 72),
           leg_o=purple, leg_o_rows=10)
    s.paint(B["body"]["front"], lambda x, y, w, h: (190, 162, 72) if (y < 2 and 2 <= x <= 5) else None)
    for side in ("right", "left"):   # gold earring
        _bset(s, B["head"][side], 4, 5, (222, 190, 70)); _bset(s, B["head"][side], 4, 6, (222, 190, 70))
    _bsoot(s, B, 6, (30, 26, 40))
    _bfinish(s, "kingsley")


def build_flitwick():
    s, B = _bnew("flitwick", 711)
    skin = (232, 202, 178)
    _bhead_base(s, B, skin)
    _bhair(s, B, "wisp", (238, 238, 242))
    _bface(s, B, skin, (60, 90, 140), (220, 220, 224), (190, 120, 110), tags=("moustache", "wrinkles", "rosy"),
           hair=(240, 240, 244))
    robe, trim = (38, 36, 48), (120, 120, 138)
    _bbody(s, B, base=robe, sleeve=robe, skin=skin, legs=(30, 28, 38), shoes=(20, 18, 20), arm_o=robe, jacket=robe,
           jopen=True, lining=(26, 24, 34), hem=trim, cuff=trim, leg_o=robe, leg_o_rows=10)
    # white cravat and a row of silver buttons
    s.paint(B["body"]["front"], lambda x, y, w, h: (236, 236, 240) if (y < 3 and 2 <= x <= 5) else None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (190, 192, 200) if (x == 1 and y % 3 == 1) else None)
    _bsoot(s, B, 4, (60, 56, 60))
    _bfinish(s, "flitwick")


def build_slughorn():
    s, B = _bnew("slughorn", 712)
    skin = (232, 184, 156)
    _bhead_base(s, B, skin)
    for x in range(8):   # bare head with a pale fringe of hair at the back and sides
        for y in range(3, 6):
            _bset(s, B["head"]["back"], x, y, s.noisy((196, 186, 160), 0.1))
    for side, fb in (("right", 0), ("left", 7)):
        for y in range(3, 5):
            for x in (fb, fb + (1 if fb == 0 else -1)):
                _bset(s, B["head"][side], x, y, s.noisy((196, 186, 160), 0.1))
    _bface(s, B, skin, (96, 104, 130), (214, 200, 170), (180, 100, 96), bald=True, tags=("walrus", "rosy"),
           hair=(220, 208, 174))
    for x in (1, 2, 5, 6):
        _bset(s, B["head"]["front"], x, 3, (216, 204, 176))   # bushy pale brows
    for x in range(8):
        _bset(s, B["head"]["top"], x, 3, shade(skin, 1.12))
    must, brown, shirt = (178, 134, 46), (96, 60, 36), (232, 222, 196)
    _bbody(s, B, base=must, sleeve=brown, skin=skin, legs=(56, 42, 34), shoes=(36, 26, 22), arm_o=brown,
           jacket=brown, jopen=True, lining=must, hem=shade(brown, 0.7), cuff=shirt, leg_o=None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: shade(must, 1.05) if (x in (2, 3, 4, 5) and y <= h - 3 and False) else None)
    s.paint(B["body"]["front"], lambda x, y, w, h: shirt if (y < 2 and 2 <= x <= 5) else (
        (230, 200, 90) if (x in (3, 4) and y in (3, 5, 7, 9)) else (
            (222, 190, 70) if ((x, y) in ((1, 5), (2, 6), (5, 6), (6, 5))) else None)))
    _bsoot(s, B, 4, (70, 56, 40))
    _bfinish(s, "slughorn")


# --- generic battle students -------------------------------------------------------------------------------

BATTLE_HOUSES = {   # jumper colour, tie/trim colour, second trim
    "gryffindor": ((150, 34, 42), (212, 168, 48)),
    "hufflepuff": ((214, 168, 38), (26, 26, 28)),
    "ravenclaw": ((40, 72, 152), (156, 112, 64)),
    "slytherin": ((36, 112, 68), (192, 194, 200)),
}
# name -> skin, hair, hair style, eyes, robe over the jumper?, torn sleeve side, glasses
BATTLE_STUDENTS = {
    "battle_gryffindor_boy": dict(house="gryffindor", girl=False, skin=(232, 190, 160), hair=(92, 60, 36), style="scruffy",
                                  eyes=(70, 110, 70), robe=False, torn="r"),
    "battle_gryffindor_girl": dict(house="gryffindor", girl=True, skin=(124, 82, 56), hair=(22, 18, 20), style="long",
                                   eyes=(50, 34, 26), robe=True, torn=None),
    "battle_hufflepuff_boy": dict(house="hufflepuff", girl=False, skin=(246, 215, 190), hair=(214, 184, 110),
                                  style="scruffy", eyes=(96, 120, 150), robe=True, torn=None),
    "battle_hufflepuff_girl": dict(house="hufflepuff", girl=True, skin=(212, 160, 118), hair=(124, 54, 34),
                                   style="pinned", eyes=(90, 64, 40), robe=False, torn="l"),
    "battle_ravenclaw_boy": dict(house="ravenclaw", girl=False, skin=(164, 112, 76), hair=(26, 22, 24), style="short",
                                 eyes=(48, 36, 30), robe=False, torn=None),
    "battle_ravenclaw_girl": dict(house="ravenclaw", girl=True, skin=(238, 200, 168), hair=(50, 34, 26), style="long",
                                  eyes=(100, 124, 150), robe=True, torn=None, glasses=True),
    "battle_slytherin_boy": dict(house="slytherin", girl=False, skin=(255, 228, 212), hair=(232, 222, 182),
                                 style="short", eyes=(120, 134, 148), robe=True, torn="l"),
    "battle_slytherin_girl": dict(house="slytherin", girl=True, skin=(190, 136, 96), hair=(18, 16, 18), style="bun",
                                  eyes=(60, 44, 32), robe=False, torn=None),
}


def build_battle_student(name, i):
    p = BATTLE_STUDENTS[name]
    s, B = _bnew(name, 800 + i)
    skin, hair = p["skin"], p["hair"]
    jumper, tie = BATTLE_HOUSES[p["house"]]
    jumper = shade(jumper, 0.9)
    _bhead_base(s, B, skin)
    _bhair(s, B, p["style"], hair)
    tags = ["rosy"] if i % 2 else []
    if p["girl"]:
        tags.append("lashes")
    if p.get("glasses"):
        tags.append("glasses")
    lip = (min(255, int(skin[0] * 0.86 + 22)), int(skin[1] * 0.62), int(skin[2] * 0.62)) if p["girl"] else shade(skin, 0.74)
    _bface(s, B, skin, p["eyes"], shade(hair, 0.8) if sum(hair) > 300 else shade(hair, 1.0), lip, tags=tuple(tags))
    _bdirt(s, B["head"]["front"], 8, (40, 36, 34))
    if i % 3 == 0:
        _bscratch(s, B, skin, 5, 5, 2)
    black = (28, 28, 34)
    legs = (60, 58, 68) if p["girl"] else (50, 50, 60)
    skirt = (60, 58, 72) if p["girl"] else None
    _bbody(s, B, base=jumper, sleeve=jumper, skin=skin, legs=(24, 22, 28) if p["girl"] else legs,
           shoes=(26, 22, 22), arm_o=black if p["robe"] else None, jacket=black if p["robe"] else None, jopen=True,
           lining=shade(jumper, 0.7), hem=tie if p["robe"] else None, cuff=tie, leg_o=skirt,
           leg_o_rows=5 if skirt else 10, off=i)
    # white collar, house tie, V-neck
    s.paint(B["body"]["front"], lambda x, y, w, h: (232, 232, 236) if (y < 2 and 2 <= x <= 5) else None)
    s.paint(B["body"]["front"], lambda x, y, w, h: (tie if (y % 2 == 0) else shade(jumper, 0.7)) if (
        x in (3, 4) and 2 <= y <= 7) else None)
    if p["house"] == "hufflepuff":   # black stripes on the yellow jumper hem
        s.paint(B["body"]["front"], lambda x, y, w, h: (26, 26, 28) if y in (h - 2,) else None)
    else:
        s.paint(B["body"]["front"], lambda x, y, w, h: shade(jumper, 0.75) if y == h - 2 else None)
    if not p["robe"]:
        for a in ("rarm", "larm"):
            for f in ("front", "back"):
                s.paint(B[a][f], lambda x, y, w, h: tie if y == h - 3 else None)
    # torn sleeve: the outer sleeve (or the jumper sleeve) frayed to the elbow with skin showing
    if p["torn"]:
        a = ("rarm" if p["torn"] == "r" else "larm")
        tgt = (a + "_o") if p["robe"] else a
        for f in ("front", "back", "right", "left"):
            rect = B[tgt][f]
            for y in range(3, 9):
                for x in range(rect[2]):
                    if y < 6 or s.rng.random() < 0.5:
                        if tgt == a and y >= 4:
                            _bset(s, rect, x, y, skin if s.rng.random() < 0.85 else shade(skin, 0.8))
                        elif tgt != a:
                            _bset(s, rect, x, y, (0, 0, 0, 0))
        if tgt != a:   # robe sleeve gone: the jumper sleeve shows, itself split above the elbow
            for f in ("front", "right"):
                for y in (5, 6):
                    for x in range(1):
                        _bset(s, B[a][f], x, y, skin)
    _bsoot(s, B, 40 if i % 2 == 0 else 26, (36, 32, 30))
    _bfinish(s, name)


# --- Death Eaters and Snatchers ---------------------------------------------------------------------------

_MASKS = {
    "death_eater_mask_a": ["hhhhhhhh", "hhhhhhhh", "hHSSSSHh", "hSSSSSSh", "hSKSSKSh", "hSSDDSSh", "hSDKKDSh", "hhSSSShh"],
    "death_eater_mask_b": ["hhhhhhhh", "hhhhhhhh", "hSGSSGSh", "SGSSSSGS", "SKKSSKKS", "hSDGGDSh", "hSGSSGSh", "hhSGGShh"],
    "death_eater_mask_c": ["hhhhhhhh", "hhhhhhhh", "hSSSSSSh", "hSDSSDSh", "hSKSSKSh", "hSSLLSSh", "hhSLLShh", "hhhDDhhh"],
}
_MASK_PAL = {"S": (176, 178, 186), "H": (226, 228, 236), "D": (104, 106, 116), "K": (6, 6, 8), "G": (198, 162, 66),
             "L": (210, 212, 220)}


def build_death_eater_masked(name, seed):
    s, B = _bnew(name, seed)
    hood = (18, 18, 24)
    robe = (24, 24, 30)
    grid = _MASKS[name]
    head, hat = B["head"], B["hat"]
    s.fill_box(head, lambda x, y, w, h: shade(hood, 1.0 + s.rng.uniform(-0.15, 0.2)))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            c = shade(hood, 1.3 + 0.2 * s.rng.random()) if ch == "h" else _MASK_PAL[ch]
            if ch not in "hK":
                c = shade(c, 1 + s.rng.uniform(-0.04, 0.04))
            _bset(s, head["front"], x, y, c)
    # hood on the hat layer: crown, back, sides, brow and a deep fold beside the face
    for f in ("top", "back", "right", "left"):
        s.paint(hat[f], lambda x, y, w, h: _cl(s, hood, x, y, w, h, 1))
    s.paint(hat["front"], lambda x, y, w, h: _cl(s, hood, x, y, w, h) if (y < 2 or x == 0 or x == 7) else None)
    s.paint(hat["front"], lambda x, y, w, h: shade(hood, 0.5) if (y == 2 and 1 <= x <= 6) else None)
    if name.endswith("_c"):   # the beak stands proud of the face
        for (x, y, k) in [(3, 5, 1.0), (4, 5, 1.0), (3, 6, 0.9), (4, 6, 0.9), (3, 7, 0.5), (4, 7, 0.5)]:
            _bset(s, hat["front"], x, y, shade(_MASK_PAL["L"], k))
    if name.endswith("_b"):   # gold crest over the brow
        for x in (2, 3, 4, 5):
            _bset(s, hat["front"], x, 1, _MASK_PAL["G"])
        _bset(s, hat["front"], 3, 0, _MASK_PAL["G"]); _bset(s, hat["front"], 4, 0, _MASK_PAL["G"])
    skin = (212, 206, 202)
    _bbody(s, B, base=robe, sleeve=robe, skin=skin, legs=(22, 22, 28), shoes=(14, 14, 16), arm_o=robe, jacket=robe,
           jopen=True, lining=(10, 10, 14), hem=(44, 44, 54), cuff=(54, 54, 66), leg_o=robe, leg_o_rows=10,
           off=seed)
    s.paint(B["body"]["front"], lambda x, y, w, h: _MASK_PAL["S"] if (y == 0 and x in (3, 4)) else shade(robe, 1.6)
            if (y < 2 and 1 <= x <= 6) else None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (22, 22, 28) if (name.endswith("_b") and y == 5 and x in (1, 6))
            else None)
    # a pale silver clasp at the throat, differs per mask
    _bset(s, B["body"]["front"], 3, 0, _MASK_PAL["G" if name.endswith("_b") else "S"])
    _bset(s, B["body"]["front"], 4, 0, _MASK_PAL["G" if name.endswith("_b") else "S"])
    _bsoot(s, B, 10, (8, 8, 10))
    _bfinish(s, name)


def build_death_eater_bare(name, seed, hair, hstyle, eyes, skin):
    s, B = _bnew(name, seed)
    _bhead_base(s, B, skin)
    _bhair(s, B, hstyle, hair)
    _bface(s, B, skin, eyes, shade(hair, 0.75) if sum(hair) > 360 else shade(hair, 1.2), (150, 100, 100),
           tags=("gaunt", "stern"))
    robe, trim = (24, 24, 30), (50, 50, 62)
    _bbody(s, B, base=robe, sleeve=robe, skin=skin, legs=(22, 22, 28), shoes=(14, 14, 16), arm_o=robe, jacket=robe,
           jopen=True, lining=(10, 10, 14), hem=trim, cuff=trim, leg_o=robe, leg_o_rows=10, off=seed)
    # a high collar, a silver clasp
    s.paint(B["body"]["front"], lambda x, y, w, h: shade(robe, 1.8) if (y < 2 and 1 <= x <= 6) else None)
    _bset(s, B["body"]["front"], 3, 1, _MASK_PAL["S"]); _bset(s, B["body"]["front"], 4, 1, _MASK_PAL["S"])
    if hstyle == "long":   # pale, long, Malfoy-fashion: a green thread in the lining
        s.paint(B["jacket"]["front"], lambda x, y, w, h: (36, 100, 60) if (x in (1, 6) and 1 <= y <= 8) else None)
    _bdirt(s, B["head"]["front"], 3, (60, 56, 56))
    _bsoot(s, B, 10, (8, 8, 10))
    _bfinish(s, name)


def build_snatcher(name, seed, variant):
    s, B = _bnew(name, seed)
    if variant == "a":
        skin, hair, eyes = (214, 168, 138), (70, 52, 38), (76, 60, 40)
        coat, vest, pants, scarf = (84, 58, 38), (46, 40, 36), (50, 46, 40), (110, 40, 40)
        sleeve_l, sleeve_r = (84, 58, 38), (54, 54, 46)
    else:
        skin, hair, eyes = (224, 182, 150), (120, 108, 94), (90, 100, 90)
        coat, vest, pants, scarf = (62, 66, 54), (96, 66, 40), (70, 56, 44), (150, 150, 140)
        sleeve_l, sleeve_r = (62, 66, 54), (110, 82, 54)
    _bhead_base(s, B, skin)
    _bhair(s, B, "scruffy", hair)
    _bface(s, B, skin, eyes, shade(hair, 0.8), shade(skin, 0.66), tags=("stubble", "wrinkles", "stern"))
    _bdirt(s, B["head"]["front"], 10, (62, 46, 34))
    if variant == "b":
        _bscratch(s, B, skin, 5, 4, 3)
    _bbody(s, B, base=vest, sleeve=sleeve_l, skin=skin, legs=pants, shoes=(28, 22, 18), arm_o=sleeve_l, jacket=coat,
           jopen=True, lining=shade(coat, 0.55), hem=shade(coat, 0.6), cuff=shade(coat, 0.6), leg_o=None, ragged=True,
           off=seed)
    # mismatched right sleeve (and right half patch), belt, scarf, patches
    s.fill_box(B["rarm_o"], lambda x, y, w, h: _cl(s, sleeve_r, x, y, w, h, 1) if y < h - 2 else None)
    s.fill_box(B["rarm"], lambda x, y, w, h: s.noisy(skin, 0.03) if y >= h - 2 else _cl(s, shade(sleeve_r, 0.8), x, y, w, h))
    s.paint(B["jacket"]["front"], lambda x, y, w, h: _cl(s, sleeve_r, x, y, w, h) if (x >= 6 and y > 2 and y < 9) else None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (30, 22, 18) if y == 7 else None)
    s.paint(B["jacket"]["back"], lambda x, y, w, h: shade(sleeve_r, 1.1) if (2 <= x <= 4 and 3 <= y <= 6) else None)
    for xx in range(8):   # scarf wrapped at the neck
        _bset(s, B["body"]["front"], xx, 0, shade(scarf, 0.9 + 0.2 * (xx % 2)))
        _bset(s, B["body"]["front"], xx, 1, shade(scarf, 0.7 + 0.2 * (xx % 2))) if xx in (2, 3, 4, 5) else None
    for xx in (3, 4):   # a hanging scarf end
        for yy in range(2, 5):
            _bset(s, B["body"]["front"], xx, yy, shade(scarf, 0.75 + 0.15 * (yy % 2)))
    # a patch on one knee
    s.paint(B["lleg"]["front"], lambda x, y, w, h: shade(coat, 1.0) if (1 <= x <= 2 and 4 <= y <= 6) else None)
    _bdirt(s, B["jacket"]["front"], 6, (30, 26, 22))
    _bsoot(s, B, 24, (30, 24, 20))
    _bfinish(s, name)


# --- teachers -----------------------------------------------------------------------------------------------

def build_teacher_a():
    s, B = _bnew("teacher_a", 901)
    skin = (226, 190, 162)
    _bhead_base(s, B, skin)
    _bhair(s, B, "short", (176, 176, 182))
    _bface(s, B, skin, (70, 90, 110), (150, 150, 156), (170, 110, 104), tags=("wrinkles", "shortbeard", "stern"),
           hair=(176, 176, 182))
    robe, trim = (34, 34, 50), (150, 152, 166)
    _bbody(s, B, base=(48, 48, 66), sleeve=robe, skin=skin, legs=(28, 28, 40), shoes=(18, 16, 18), arm_o=robe,
           jacket=robe, jopen=True, lining=(20, 20, 32), hem=trim, cuff=trim, leg_o=robe, leg_o_rows=10)
    s.paint(B["body"]["front"], lambda x, y, w, h: (220, 220, 226) if (y < 2 and 2 <= x <= 5) else None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (190, 192, 204) if (x == 1 and y in (2, 5, 8)) else None)
    _bsoot(s, B, 14, (30, 28, 36))
    _bfinish(s, "teacher_a")


def build_teacher_b():
    s, B = _bnew("teacher_b", 902)
    skin = (238, 208, 186)
    _bhead_base(s, B, skin)
    _bhair(s, B, "bun", (190, 190, 196))
    _bface(s, B, skin, (96, 110, 90), (150, 150, 156), (176, 108, 108), tags=("wrinkles", "glasses"))
    robe, trim = (44, 30, 48), (150, 120, 150)
    _bbody(s, B, base=(70, 50, 74), sleeve=robe, skin=skin, legs=(30, 24, 34), shoes=(20, 16, 20), arm_o=robe,
           jacket=robe, jopen=True, lining=(24, 16, 28), hem=trim, cuff=trim, leg_o=robe, leg_o_rows=10)
    s.paint(B["body"]["front"], lambda x, y, w, h: (226, 216, 226) if (y < 2 and 2 <= x <= 5) else None)
    s.paint(B["jacket"]["front"], lambda x, y, w, h: (200, 170, 200) if (y == 0 and x in (1, 6)) else None)
    _bsoot(s, B, 10, (34, 26, 38))
    _bfinish(s, "teacher_b")


def generate_battle_skins():
    build_mcgonagall_dh()
    build_neville_dh()
    build_ron_dh()
    build_ginny()
    build_luna()
    build_seamus()
    build_molly()
    build_bellatrix()
    build_hagrid_dh()
    build_kingsley()
    build_flitwick()
    build_slughorn()
    for i, n in enumerate(BATTLE_STUDENTS):
        build_battle_student(n, i)
    build_death_eater_masked("death_eater_mask_a", 1001)
    build_death_eater_masked("death_eater_mask_b", 1002)
    build_death_eater_masked("death_eater_mask_c", 1003)
    build_death_eater_bare("death_eater_bare_a", 1004, (30, 26, 28), "slick", (74, 70, 70), (214, 204, 196))
    build_death_eater_bare("death_eater_bare_b", 1005, (226, 218, 190), "long", (120, 140, 160), (236, 228, 222))
    build_snatcher("snatcher_a", 1006, "a")
    build_snatcher("snatcher_b", 1007, "b")
    build_teacher_a()
    build_teacher_b()
    return ["mcgonagall_dh", "neville_dh", "ron_dh", "ginny", "luna", "seamus", "molly", "bellatrix", "hagrid_dh",
            "kingsley", "flitwick", "slughorn"] + list(BATTLE_STUDENTS) + [
        "death_eater_mask_a", "death_eater_mask_b", "death_eater_mask_c", "death_eater_bare_a", "death_eater_bare_b",
        "snatcher_a", "snatcher_b", "teacher_a", "teacher_b"]


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
    # Dursleys (NovaSkin ids 1aisrt9qman, 2740043036, 4202223350; raw files in build_scripts/skin_sources/, touched up
    # by touch_up_dursleys() above):
    #   vernon              https://minecraft.novaskin.me/post/1aisrt9qman
    #   petunia             https://minecraft.novaskin.me/post/2740043036  (hair recoloured blonde)
    #   dudley              https://minecraft.novaskin.me/post/4202223350  (blond, striped jumper)
    for name in ("sirius", "hermione", "harry", "mcgonagall", "dumbledore", "hagrid", "snape", "quirrell", "ron",
                 "student_hufflepuff", "student_ravenclaw"):
        if not os.path.exists(os.path.join(OUT_DIR, name + ".png")):
            raise SystemExit("missing downloaded skin " + name + ".png")
        names.append(name)

    touch_up_dursleys()
    names.extend(["vernon", "petunia", "dudley"])
    names.extend(generate_flying_skins())

    names.extend(generate_battle_skins())
    names.extend(generate_students())
    names.extend(generate_crowd())

    with open(LIST, "w", encoding="utf-8", newline="\n") as f:
        json.dump(names, f, indent=2)
        f.write("\n")
    print("wrote", len(names), "skins to", OUT_DIR)
    contact_sheet([n for n in names if n.split("_")[0] in ("witch", "wizard", "shopkeeper") or n == "ollivander"],
                  os.path.join(ROOT, "build_scripts", "pensieve_scenes", "previews", "wand_skins_contact.png"))


if __name__ == "__main__":
    main()
