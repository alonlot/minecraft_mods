"""Paint the 18x18 icons for the mod's remaining visible effects.

    python build_scripts/generate_mod_effect_icons.py [output_dir]

Sibling of generate_combat_effect_icons.py (same house style: 18x18, no anti-aliasing, a one-pixel
dilated dark outline, one highlight). Writes assets/hogwartsmagic/textures/mob_effect/<effect>.png for

  lumos                    a pale light orb with rays
  protego                  a blue kite shield with a bright boss
  tenebris_flight          a black smoke wisp curling upward
  polyjuice                a round flask of bubbling green-brown brew
  gillyweed                green weed fronds with a gill-slit leaf
  wizard_elixir            a corked vial of blue elixir with a sparkle
  basilisk_gaze            a green serpent eye with a gold slit pupil
  basilisk_petrification   a grey cracked stone eye
  incarcerous_chains       two interlocked chain links   (effect is applied hidden today)
  levicorpus               an upside-down figure hoisted by the ankle (applied hidden today)

Effects applied with showIcon=false (petrified, glacius_*, imperius, confunded, rooted, possessed,
possessing) deliberately have no icon.
"""

import importlib.util
import math
import os
import sys

from PIL import ImageDraw

_here = os.path.dirname(os.path.abspath(__file__))
_spec = importlib.util.spec_from_file_location("combat_icons", os.path.join(_here, "generate_combat_effect_icons.py"))
base = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(base)
SIZE, blank, outline, DEFAULT_OUT = base.SIZE, base.blank, base.outline, base.DEFAULT_OUT


def c(r, g, b, a=255):
    return (r, g, b, a)


def put(image, pts, colour):
    for x, y in pts:
        if 0 <= x < SIZE and 0 <= y < SIZE:
            image.putpixel((x, y), colour)


def disc(image, cx, cy, r, colour):
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + .5 - cx) ** 2 + (y + .5 - cy) ** 2 <= r * r:
                image.putpixel((x, y), colour)


def lumos():
    im = blank()
    # rays first (they sit under the orb)
    for ang in range(0, 360, 45):
        dx, dy = math.cos(math.radians(ang)), math.sin(math.radians(ang))
        for dist in (6.2, 7.4) if ang % 90 == 0 else (6.0,):
            x, y = int(round(8.5 + dx * dist - .5)), int(round(8.5 + dy * dist - .5))
            put(im, [(x, y)], c(255, 226, 120))
    disc(im, 9, 9, 4.6, c(255, 214, 90))
    disc(im, 9, 9, 3.4, c(255, 238, 150))
    disc(im, 9, 9, 2.0, c(255, 252, 226))
    # shade the lower right of the orb
    put(im, [(12, 11), (12, 10), (11, 12), (10, 13)], c(240, 176, 60))
    im = outline(im, (112, 76, 8))
    put(im, [(8, 8)], c(255, 255, 255))
    return im


def protego():
    im = blank()
    d = ImageDraw.Draw(im)
    shield = [(2, 2), (15, 2), (15, 9), (12, 13), (8.5, 16), (5, 13), (2, 9)]
    d.polygon(shield, fill=c(70, 130, 220))
    d.polygon([(8.5, 2), (15, 2), (15, 9), (12, 13), (8.5, 16)], fill=c(44, 92, 178))
    d.rectangle((3, 3, 4, 8), fill=c(120, 176, 250))
    im = outline(im, (14, 30, 76))
    d = ImageDraw.Draw(im)
    d.line([(8, 3), (8, 14)], fill=c(214, 236, 255))
    d.line([(4, 7), (13, 7)], fill=c(214, 236, 255))
    put(im, [(8, 7)], c(255, 255, 255))
    put(im, [(4, 3), (5, 3)], c(190, 220, 255))
    return im


def tenebris_flight():
    im = blank()
    body = c(30, 26, 40)
    mid = c(60, 52, 80)
    # a ribbon of smoke: discs along a rising S-curve, fat at the bottom, thin at the wisp tip
    path = []
    for i in range(15):
        t = i / 14.0
        path.append((6.0 + 5.0 * t + 3.4 * math.sin(t * 2 * math.pi), 14.5 - 13 * t, 3.4 * (1 - t) + 0.9))
    for x, y, r in path:
        disc(im, x, y, r, body)
    # lighter rim on the upper-left of the ribbon
    for x, y, r in path[1:12]:
        put(im, [(int(x - r * .7), int(y - r * .5))], mid)
    im = outline(im, (96, 86, 130))
    put(im, [(6, 13), (9, 9), (13, 3)], c(150, 134, 200))
    return im


def incarcerous_chains():
    im = blank()
    d = ImageDraw.Draw(im)
    steel = c(176, 182, 196)
    dark = c(98, 104, 120)
    # two diagonal links that interlock: one tall oval, one flat oval
    d.ellipse((2, 1, 9, 10), outline=steel, width=2)
    d.ellipse((8, 7, 16, 16), outline=steel, width=2)
    d.ellipse((6, 5, 12, 12), outline=dark, width=1)
    im = outline(im, (24, 26, 36))
    put(im, [(3, 4), (4, 2), (4, 3), (11, 9), (12, 8)], c(244, 246, 255))
    put(im, [(14, 14), (15, 12), (8, 9), (7, 8)], dark)
    return im


def levicorpus():
    im = blank()
    d = ImageDraw.Draw(im)
    # lift lines above the ankle
    for x in (4, 13):
        d.line([(x, 6), (x, 1)], fill=c(170, 120, 255))
        put(im, [(x - 1, 2), (x + 1, 2)], c(170, 120, 255))
    skin = c(240, 200, 160)
    cloth = c(110, 74, 190)
    # legs at the top (together, hoisted), body, head hanging at the bottom
    d.line([(8, 1), (8, 6)], fill=skin, width=2)
    d.rectangle((6, 6, 11, 11), fill=cloth)
    d.line([(4, 11), (3, 15)], fill=skin)
    d.line([(13, 11), (14, 15)], fill=skin)
    d.ellipse((6, 12, 11, 17), fill=skin)
    im = outline(im, (36, 18, 70))
    put(im, [(7, 14), (10, 14)], c(40, 24, 20))
    put(im, [(7, 7), (7, 8)], c(160, 128, 232))
    put(im, [(8, 2)], c(255, 236, 206))
    return im


def polyjuice():
    im = blank()
    d = ImageDraw.Draw(im)
    glass = c(190, 214, 224)
    d.ellipse((2, 7, 15, 17), fill=glass)
    d.rectangle((6, 3, 10, 8), fill=glass)
    d.ellipse((3, 9, 14, 16), fill=c(112, 150, 40))
    d.rectangle((3, 10, 14, 12), fill=c(112, 150, 40))
    d.ellipse((4, 12, 13, 16), fill=c(78, 112, 30))
    d.rectangle((5, 10, 12, 10), fill=c(170, 206, 70))
    d.rectangle((6, 2, 10, 3), fill=c(130, 88, 48))
    im = outline(im, (26, 40, 44))
    put(im, [(4, 9), (3, 11), (3, 12)], c(250, 255, 255))
    put(im, [(11, 12), (9, 14), (6, 13)], c(190, 226, 100))
    # bubbles above the neck
    put(im, [(11, 1), (13, 0)], c(214, 238, 120))
    put(im, [(12, 1)], c(120, 160, 40))
    return im


def gillyweed():
    im = blank()
    d = ImageDraw.Draw(im)
    green = c(60, 168, 90)
    light = c(140, 224, 130)
    # a tuft of rat-tail tendrils from a common root, each ending in a bulb
    tendrils = ([(9, 16), (8, 12), (10, 8), (8, 4), (9, 2)],
                [(9, 16), (6, 12), (4, 8), (5, 4)],
                [(9, 16), (12, 12), (14, 8), (13, 5)])
    for pts in tendrils:
        d.line(pts, fill=green, width=2)
    for x, y in ((9, 2), (5, 4), (13, 5)):
        d.ellipse((x - 1, y - 1, x + 1, y + 1), fill=light)
    d.rectangle((7, 14, 11, 16), fill=c(40, 124, 78))
    im = outline(im, (10, 52, 44))
    # light edge and the gill slits (little dark ticks) down the main tendril
    put(im, [(8, 10), (9, 7), (8, 5), (9, 1)], light)
    put(im, [(9, 11), (10, 9), (9, 6)], c(20, 80, 56))
    put(im, [(4, 7), (12, 11)], light)
    put(im, [(16, 3), (15, 8), (2, 11)], c(180, 230, 255))
    return im


def wizard_elixir():
    im = blank()
    d = ImageDraw.Draw(im)
    glass = c(196, 214, 236)
    d.ellipse((3, 7, 14, 17), fill=glass)
    d.rectangle((7, 3, 10, 8), fill=glass)
    d.ellipse((4, 9, 13, 16), fill=c(70, 120, 235))
    d.rectangle((4, 10, 13, 12), fill=c(70, 120, 235))
    d.ellipse((5, 12, 13, 16), fill=c(44, 78, 180))
    d.rectangle((5, 9, 12, 9), fill=c(150, 190, 255))
    d.rectangle((7, 1, 10, 2), fill=c(150, 100, 56))
    im = outline(im, (18, 24, 66))
    put(im, [(5, 8), (4, 10), (4, 11)], c(255, 255, 255))
    # sparkle (four-point star) to the upper right, plus two motes
    put(im, [(14, 2), (14, 3), (14, 4), (13, 3), (15, 3)], c(255, 238, 140))
    put(im, [(14, 3)], c(255, 255, 255))
    put(im, [(1, 3), (16, 9)], c(255, 226, 120))
    put(im, [(8, 13), (10, 11)], c(170, 206, 255))
    return im


def _eye(im, fill, shade_col, edge):
    d = ImageDraw.Draw(im)
    almond = [(1, 9), (4, 5), (9, 3.5), (14, 5), (17, 9), (14, 13), (9, 14.5), (4, 13)]
    d.polygon(almond, fill=fill)
    d.polygon([(1, 9), (17, 9), (14, 13), (9, 14.5), (4, 13)], fill=shade_col)
    return outline(im, edge)


def basilisk_gaze():
    im = blank()
    im = _eye(im, c(150, 214, 60), c(92, 168, 44), (14, 44, 16))
    d = ImageDraw.Draw(im)
    d.ellipse((6, 5, 12, 12), fill=c(230, 196, 40))
    d.rectangle((8, 4, 9, 13), fill=c(12, 20, 8))
    put(im, [(7, 6), (7, 7)], c(255, 244, 150))
    put(im, [(4, 8), (13, 8)], c(210, 244, 120))
    put(im, [(11, 6)], c(255, 255, 255))
    return im


def basilisk_petrification():
    im = blank()
    im = _eye(im, c(176, 176, 180), c(118, 118, 126), (34, 34, 40))
    d = ImageDraw.Draw(im)
    d.ellipse((6, 5, 12, 12), fill=c(136, 136, 144))
    d.rectangle((8, 4, 9, 13), fill=c(48, 48, 56))
    # cracks
    d.line([(3, 7), (5, 9), (4, 11)], fill=c(60, 60, 68))
    d.line([(14, 6), (12, 9), (14, 11)], fill=c(60, 60, 68))
    put(im, [(7, 6), (7, 7), (5, 6), (9, 4)], c(226, 226, 230))
    put(im, [(15, 8), (2, 9)], c(92, 92, 100))
    return im


ICONS = {
    "lumos": lumos,
    "protego": protego,
    "tenebris_flight": tenebris_flight,
    "incarcerous_chains": incarcerous_chains,
    "levicorpus": levicorpus,
    "polyjuice": polyjuice,
    "gillyweed": gillyweed,
    "wizard_elixir": wizard_elixir,
    "basilisk_gaze": basilisk_gaze,
    "basilisk_petrification": basilisk_petrification,
}


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    os.makedirs(out_dir, exist_ok=True)
    for name, painter in ICONS.items():
        image = painter()
        assert image.size == (SIZE, SIZE), name + " is not 18x18"
        path = os.path.normpath(os.path.join(out_dir, name + ".png"))
        image.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
