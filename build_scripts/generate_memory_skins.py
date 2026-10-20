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

    with open(LIST, "w", encoding="utf-8", newline="\n") as f:
        json.dump(names, f, indent=2)
        f.write("\n")
    print("wrote", len(names), "skins to", OUT_DIR)


if __name__ == "__main__":
    main()
