"""Quidditch kits for the Flying memory: a house-coloured Quidditch tunic per house (a chest armor item, like the house
robes and the champion's robes) and one pair of leather shin guards (a leggings armor item) shared by all four.

Written assets (under assets/hogwartsmagic/):
  textures/models/armor/<house>_quidditch_robes_layer_1.png   64 x 32: body + sleeves, with leather arm guards and gloves
  textures/models/armor/quidditch_shin_guards_layer_2.png     64 x 32: dark breeches, knee pads, leather shin guards, boots
  textures/item/<house>_quidditch_robes.png, quidditch_shin_guards.png   16 x 16 icons
  models/item/<house>_quidditch_robes.json, quidditch_shin_guards.json

Armor UV: body front (20,20) 8x12, back (32,20), sides (16,20) and (28,20) 4x12, top (20,16); the right arm's faces
sit at right (40,20), front (44,20), left (48,20), back (52,20) and the left arm reuses them mirrored; the right leg
(layer_2) at right (0,20), front (4,20), left (8,20), back (12,20). Transparent pixels show the wearer's own skin, so
the sleeves' last two rows are the leather gloves and the body is cut at the hip, where the guards' breeches begin.
Colours are the films' Quidditch teams: scarlet and gold, canary and black, blue and bronze, green and silver.
"""

import json
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
PREVIEWS = os.path.join(ROOT, "build_scripts", "pensieve_scenes", "previews")

HOUSES = {
    #              tunic colour       trim colour       crest field    crest symbol
    "gryffindor": ((150, 24, 32), (226, 178, 48), (226, 178, 48)),
    "hufflepuff": ((236, 190, 34), (26, 24, 24), (26, 24, 24)),
    "ravenclaw": ((30, 60, 146), (200, 146, 74), (200, 146, 74)),
    "slytherin": ((26, 100, 58), (208, 212, 220), (208, 212, 220)),
}

# 4 x 5 house letters for the crest: G, H, R, S (a lion, a badger, an eagle or a serpent is unreadable at this size).
GLYPHS = {
    "gryffindor": ["XXXX", "X...", "X.XX", "X..X", "XXXX"],
    "hufflepuff": ["X..X", "X..X", "XXXX", "X..X", "X..X"],
    "ravenclaw": ["XXX.", "X..X", "XXX.", "X.X.", "X..X"],
    "slytherin": [".XXX", "X...", ".XX.", "...X", "XXX."],
}

LEATHER = (156, 104, 58)
LEATHER_D = (96, 62, 34)
LEATHER_L = (190, 138, 82)
GLOVE = (58, 38, 26)
BRASS = (214, 184, 112)


def shade(c, k):
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))), max(0, min(255, int(c[2] * k))), 255)


class Layer:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        self.rng = random.Random(seed)

    def px(self, x, y, c, noise=0.03):
        k = 1 + self.rng.uniform(-noise, noise)
        self.img.putpixel((x, y), shade(c, k) if noise else (c[0], c[1], c[2], 255))

    def face(self, rect, fn, noise=0.03):
        x0, y0, w, h = rect
        for ly in range(h):
            for lx in range(w):
                c = fn(lx, ly, w, h)
                if c is not None:
                    self.px(x0 + lx, y0 + ly, c, noise)


def robes_layer(house):
    base, trim, _ = HOUSES[house]
    dark = shade(base, 0.72)
    L = Layer("quidditch:" + house)
    glyph = GLYPHS[house]

    # -- body: front (20,20) 8x12 --
    def front(x, y, w, h):
        if y == 0 and 2 <= x <= 5:                      # high stand collar, split at the throat
            return trim if x not in (3, 4) else shade(trim, 0.8)
        if y == 1 and x in (2, 5):
            return trim
        if y == h - 1:
            return trim                                 # hem
        if x == 0 or x == 7:
            return dark                                 # shaded flanks
        if (x in (3, 4)) and y == 2:
            return dark
        return base if (x * 5 + y) % 7 else shade(base, 0.92)
    L.face((20, 20, 8, 12), front)
    # the house crest on the chest: a dark 6 x 7 field with the house letter (4 x 5) in the trim colour
    for sy in range(7):
        for sx in range(6):
            L.px(20 + 1 + sx, 20 + 3 + sy, shade(base, 0.5), 0.02)
    for gy, row in enumerate(glyph):
        for gx, ch in enumerate(row):
            if ch == "X":
                L.px(20 + 2 + gx, 20 + 4 + gy, trim, 0)

    # -- body: back (32,20): a trim yoke across the shoulders, a spine stripe, a broad hem --
    def back(x, y, w, h):
        if y == 0:
            return trim
        if y == 1:
            return shade(trim, 0.72)
        if x in (3, 4) and y >= 2:
            return trim if y < h - 1 else trim
        if y == h - 1:
            return trim
        if x == 0 or x == 7:
            return dark
        return base if (x * 5 + y) % 7 else shade(base, 0.92)
    L.face((32, 20, 8, 12), back)

    def side(x, y, w, h):
        if y < 2:
            return trim if y == 0 else shade(trim, 0.85)
        if y == h - 1:
            return trim
        if x in (1, 2) and y == 2:
            return shade(trim, 0.6)
        return shade(base, 0.9) if (x + y) % 5 else shade(base, 0.8)
    L.face((16, 20, 4, 12), side)
    L.face((28, 20, 4, 12), side)
    L.face((20, 16, 8, 4), lambda x, y, w, h: trim if (x in (2, 3, 4, 5) and y in (0, 1)) else base)
    L.face((28, 16, 8, 4), lambda x, y, w, h: dark)

    # -- sleeves (right arm UV; the left arm reuses it mirrored): shoulder band, bracer, buckles, glove --
    def sleeve(face_name):
        def fn(x, y, w, h):
            if y == 0:
                return trim                              # the shoulder seam
            if y == 1:
                return shade(trim, 0.85) if face_name in ("front", "back") else shade(base, 0.9)
            if y >= 10:
                return GLOVE if y == 10 else shade(GLOVE, 0.85)      # leather glove over the hand
            if 6 <= y <= 9:                                          # the arm guard, four rows
                if y in (6, 9):
                    return LEATHER_D
                if y == 7 and face_name == "front" and x == 1:
                    return BRASS                                     # a buckle on the strap
                return LEATHER if y == 8 else shade(LEATHER, 0.9)
            return base if (x + y) % 4 else shade(base, 0.9)
        return fn
    for rect, name in (((40, 20, 4, 12), "right"), ((44, 20, 4, 12), "front"), ((48, 20, 4, 12), "left"),
                       ((52, 20, 4, 12), "back")):
        L.face(rect, sleeve(name))
    L.face((44, 16, 4, 4), lambda x, y, w, h: trim if y == 0 else base)
    L.face((48, 16, 4, 4), lambda x, y, w, h: shade(GLOVE, 0.7))
    return L.img


def guards_layer():
    L = Layer("quidditch:guards")
    breech = (62, 50, 44)
    breech_l = (84, 68, 58)

    def leg(face_name):
        def fn(x, y, w, h):
            if y <= 4:                                       # the breeches, waist down to the knee
                if face_name == "front" and y == 4 and x in (1, 2):
                    return shade(LEATHER, 0.8)               # the top of the knee pad
                return breech if (x + y) % 5 else breech_l
            if y == 5:
                if face_name == "back":
                    return breech
                return shade(LEATHER, 0.85)
            if y in (6, 7, 8, 9):                            # the shin guard
                if face_name == "back":
                    return LEATHER_D if y == 8 else breech            # a calf strap crossing the back
                if y in (6, 9):
                    return LEATHER_D
                if y == 7 and face_name == "front" and x == 1:
                    return BRASS
                return LEATHER
            if y == 10:                                      # the boot's cuff
                return (88, 60, 40)
            return (40, 28, 22)                              # boot
        return fn
    for rect, name in (((0, 20, 4, 12), "right"), ((4, 20, 4, 12), "front"), ((8, 20, 4, 12), "left"),
                       ((12, 20, 4, 12), "back")):
        L.face(rect, leg(name))
    L.face((4, 16, 4, 4), lambda x, y, w, h: breech)
    L.face((8, 16, 4, 4), lambda x, y, w, h: (30, 22, 18))
    return L.img


def icon_robes(house):
    base, trim, _ = HOUSES[house]
    dark = shade(base, 0.7)
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def p(x, y, c):
        im.putpixel((x, y), (c[0], c[1], c[2], 255))
    # a tunic: shoulders, sleeves hanging out, a body, gloves at the cuffs
    rows = {1: (5, 10), 2: (3, 12), 3: (2, 13), 4: (1, 14), 5: (1, 14), 6: (1, 14), 7: (1, 14), 8: (2, 13),
            9: (4, 11), 10: (4, 11), 11: (4, 11), 12: (4, 11), 13: (4, 11)}
    for y, (x0, x1) in rows.items():
        for x in range(x0, x1 + 1):
            p(x, y, base if (x + y) % 5 else shade(base, 0.9))
    for y in range(3, 9):                                    # the sleeves' leather bracers
        for x in (1, 2, 13, 14):
            if 5 <= y <= 7:
                p(x, y, LEATHER)
    for y in (4, 8):
        for x in (1, 2, 13, 14):
            p(x, y, GLOVE if y == 8 else base)
    for x in (5, 6, 7, 8, 9, 10):                            # the collar and the hem
        p(x, 1, trim)
    for x in range(4, 12):
        p(x, 13, trim)
    for y in range(2, 13):
        p(4, y, dark) if y > 3 else None
        p(11, y, dark) if y > 3 else None
    for (x, y) in ((6, 5), (7, 5), (8, 5), (9, 5), (6, 6), (9, 6), (6, 7), (9, 7), (7, 8), (8, 8)):
        p(x, y, trim)                                        # the crest
    for (x, y) in ((7, 6), (8, 6), (7, 7), (8, 7)):
        p(x, y, shade(base, 0.6))
    for (x, y) in ((3, 2), (12, 2)):
        p(x, y, trim)
    return im


def icon_guards():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))

    def p(x, y, c):
        im.putpixel((x, y), (c[0], c[1], c[2], 255))
    for ox in (2, 9):
        for y in range(2, 14):
            for x in range(ox, ox + 5):
                c = LEATHER if (x + y) % 6 else LEATHER_L
                if y in (2, 13) or x in (ox, ox + 4):
                    c = LEATHER_D
                p(x, y, c)
        for y in (5, 9):
            for x in range(ox, ox + 5):
                p(x, y, LEATHER_D)
            p(ox + 2, y, BRASS)
        for x in range(ox + 1, ox + 4):
            p(x, 11, shade(LEATHER, 0.7))
    return im


def main():
    from skin_preview import preview
    os.makedirs(os.path.join(ASSETS, "textures/models/armor"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "textures/item"), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, "models/item"), exist_ok=True)
    os.makedirs(PREVIEWS, exist_ok=True)
    guards_path = os.path.join(ASSETS, "textures/models/armor/quidditch_shin_guards_layer_2.png")
    guards_layer().save(guards_path)
    icon_guards().save(os.path.join(ASSETS, "textures/item/quidditch_shin_guards.png"))
    names = ["quidditch_shin_guards"]
    for house in HOUSES:
        n = house + "_quidditch_robes"
        names.append(n)
        path = os.path.join(ASSETS, "textures/models/armor", n + "_layer_1.png")
        robes_layer(house).save(path)
        icon_robes(house).save(os.path.join(ASSETS, "textures/item", n + ".png"))
        for tester in ("student_" + house + "_05", "student_" + house + "_08"):
            skin = os.path.join(ASSETS, "textures/memory_skin", tester + ".png")
            preview(skin, os.path.join(PREVIEWS, "kit_%s_%s.png" % (house, tester[-2:])), armor1=path,
                    armor2=guards_path)
    for n in names:
        with open(os.path.join(ASSETS, "models/item", n + ".json"), "w", newline="\n", encoding="utf-8") as f:
            f.write(json.dumps({"parent": "minecraft:item/generated",
                                "textures": {"layer0": "hogwartsmagic:item/" + n}}, indent=2) + "\n")
    print("wrote", ", ".join(names))


if __name__ == "__main__":
    main()
