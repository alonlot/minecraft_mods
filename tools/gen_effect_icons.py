"""
Generate 18x18 RGBA mob-effect icons for hogwartsmagic effects that lack one.

Each icon: dark rounded background + a simple, distinct glyph/colour.
Output: src/main/resources/assets/hogwartsmagic/textures/mob_effect/<name>.png

Run from the repo root:  python3 tools/gen_effect_icons.py
"""
import math
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/hogwartsmagic/textures/mob_effect")
S = 18
BG = (28, 24, 40, 255)
BG_EDGE = (60, 54, 80, 255)


def base():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, S - 1, S - 1), radius=4, fill=BG, outline=BG_EDGE)
    return im, d


def star(d, cx, cy, n, r_out, r_in, fill):
    pts = []
    for i in range(2 * n):
        r = r_out if i % 2 == 0 else r_in
        a = math.pi * i / n - math.pi / 2
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, fill=fill)


def lumos(d):
    star(d, 8.5, 8.5, 8, 7.5, 3, (255, 230, 90, 255))
    d.ellipse((6, 6, 11, 11), fill=(255, 255, 220, 255))


def protego(d):
    d.polygon([(4, 3), (13, 3), (14, 9), (8.5, 15), (3, 9)], fill=(70, 130, 240, 255))
    d.polygon([(6, 5), (11, 5), (11.5, 9), (8.5, 12.5), (5.5, 9)], fill=(140, 190, 255, 255))
    d.line((8, 6, 8, 11), fill=(70, 130, 240, 255))


def tenebris_flight(d):
    c = (20, 20, 25, 255)
    g = (70, 70, 85, 255)
    d.ellipse((3, 8, 9, 14), fill=c)
    d.ellipse((7, 5, 14, 12), fill=c)
    d.ellipse((5, 3, 10, 8), fill=c)
    d.ellipse((10, 10, 15, 15), fill=c)
    d.point((6, 10), fill=g); d.point((9, 7), fill=g); d.point((12, 12), fill=g)
    d.line((2, 4, 4, 6), fill=g); d.line((14, 3, 15, 5), fill=g)


def incarcerous_chains(d):
    col = (160, 160, 170, 255)
    dk = (100, 100, 110, 255)
    for (x, y) in [(2, 2), (7, 6), (11, 10)]:
        d.rounded_rectangle((x, y, x + 5, y + 7), radius=2, outline=col, width=1)
        d.rounded_rectangle((x + 1, y + 1, x + 4, y + 6), radius=1, outline=dk)
    d.line((5, 7, 8, 8), fill=col); d.line((10, 11, 12, 12), fill=col)


def levicorpus(d):
    col = (190, 90, 240, 255)
    d.polygon([(8.5, 2), (14, 8), (11, 8), (11, 15), (6, 15), (6, 8), (3, 8)], fill=col)
    d.line((8, 5, 8, 13), fill=(230, 180, 255, 255))


def polyjuice(d):
    glass = (200, 220, 230, 255)
    liq = (90, 200, 70, 255)
    d.polygon([(7, 2), (10, 2), (10, 6), (14, 13), (14, 15), (3, 15), (3, 13), (7, 6)], outline=glass)
    d.polygon([(6, 9), (11, 9), (13, 14), (4, 14)], fill=liq)
    d.point((7, 11), fill=(170, 255, 150, 255)); d.point((10, 12), fill=(170, 255, 150, 255))
    d.ellipse((8, 4, 9, 5), fill=liq); d.point((5, 7), fill=liq)


def gillyweed(d):
    col = (30, 170, 150, 255)
    lt = (90, 220, 190, 255)
    for x0 in (4, 8, 12):
        pts = [(x0 + (1 if (y // 3) % 2 else 0), y) for y in range(15, 2, -1)]
        d.line(pts, fill=col, width=2 if x0 == 8 else 1)
    d.point((9, 4), fill=lt); d.point((5, 6), fill=lt); d.point((13, 8), fill=lt)
    d.line((2, 15, 15, 15), fill=(90, 70, 40, 255))


def wizard_elixir(d):
    gold = (240, 190, 50, 255)
    d.polygon([(8.5, 2), (13, 10), (4, 10)], fill=gold)
    d.ellipse((3, 7, 14, 15), fill=gold)
    d.ellipse((6, 10, 8, 12), fill=(255, 240, 170, 255))


def cracked_stone(d, light):
    stone = (140, 140, 140, 255) if light else (110, 110, 115, 255)
    crack = (50, 50, 55, 255)
    d.rounded_rectangle((2, 2, 15, 15), radius=2, fill=stone)
    d.line((8, 2, 7, 6, 9, 9, 7, 12, 8, 15), fill=crack)
    d.line((7, 6, 3, 8), fill=crack); d.line((9, 9, 14, 7), fill=crack); d.line((7, 12, 4, 14), fill=crack)


def basilisk_petrification(d):
    cracked_stone(d, True)
    d.point((12, 12), fill=(90, 200, 90, 255))


def petrified(d):
    cracked_stone(d, False)


def basilisk_gaze(d):
    d.ellipse((1, 5, 16, 12), fill=(220, 200, 40, 255))
    d.ellipse((2, 6, 15, 11), fill=(250, 235, 90, 255))
    d.polygon([(8.5, 4), (10, 8.5), (8.5, 13), (7, 8.5)], fill=(10, 10, 10, 255))


def snowflake(d, col, dense):
    cx, cy = 8.5, 8.5
    for i in range(6):
        a = math.pi * i / 3
        x, y = cx + 7 * math.cos(a), cy + 7 * math.sin(a)
        d.line((cx, cy, x, y), fill=col)
        if dense:
            for t in (0.5, 0.75):
                px, py = cx + 7 * t * math.cos(a), cy + 7 * t * math.sin(a)
                for s in (-1, 1):
                    b = a + s * math.pi / 3
                    d.line((px, py, px + 2 * math.cos(b), py + 2 * math.sin(b)), fill=col)
    d.ellipse((7, 7, 10, 10), fill=col)


def glacius_chill(d):
    snowflake(d, (170, 225, 255, 255), False)


def glacius_frozen(d):
    d.rounded_rectangle((1, 1, 16, 16), radius=3, fill=(40, 80, 130, 255))
    snowflake(d, (120, 190, 240, 255), True)


def imperius(d):
    d.ellipse((1, 5, 16, 12), fill=(40, 160, 60, 255))
    d.ellipse((2, 6, 15, 11), fill=(90, 230, 110, 255))
    d.ellipse((6, 6, 11, 11), fill=(10, 30, 10, 255))
    d.point((8, 7), fill=(200, 255, 200, 255))


def confunded(d):
    col = (255, 110, 200, 255)
    pts = []
    for i in range(0, 60):
        a = i * 0.25
        r = 0.6 + a * 0.75
        if r > 6.5:
            break
        pts.append((8.5 + r * math.cos(a), 8.5 + r * math.sin(a)))
    d.line(pts, fill=col, width=2)
    d.rectangle((8, 8, 9, 9), fill=(255, 200, 240, 255))
    d.ellipse((12, 12, 15, 15), fill=col)


def rooted(d):
    col = (130, 85, 40, 255)
    lt = (180, 130, 70, 255)
    d.line((8, 2, 8, 9), fill=lt, width=2)
    d.line((8, 8, 4, 12, 2, 15), fill=col)
    d.line((9, 8, 13, 12, 15, 15), fill=col)
    d.line((8, 10, 6, 15), fill=col)
    d.line((9, 10, 11, 15), fill=col)
    d.line((5, 12, 4, 14), fill=col); d.line((12, 12, 13, 14), fill=col)
    d.point((7, 4), fill=(90, 200, 70, 255)); d.point((10, 3), fill=(90, 200, 70, 255))


def ghost(d, col):
    d.ellipse((3, 2, 14, 13), fill=col)
    d.rectangle((3, 8, 14, 14), fill=col)
    d.polygon([(3, 14), (5, 16), (7, 14), (9, 16), (11, 14), (13, 16), (14, 14)], fill=col)


def possessed(d):
    ghost(d, (170, 110, 230, 255))
    d.rectangle((6, 6, 7, 7), fill=(40, 10, 60, 255)); d.rectangle((10, 6, 11, 7), fill=(40, 10, 60, 255))


def possessing(d):
    ghost(d, (120, 70, 200, 255))
    d.ellipse((5, 5, 12, 9), fill=(255, 255, 255, 255))
    d.rectangle((8, 6, 9, 8), fill=(20, 0, 30, 255))


ICONS = {
    "lumos": lumos, "protego": protego, "tenebris_flight": tenebris_flight,
    "incarcerous_chains": incarcerous_chains, "levicorpus": levicorpus,
    "polyjuice": polyjuice, "gillyweed": gillyweed, "wizard_elixir": wizard_elixir,
    "basilisk_petrification": basilisk_petrification, "basilisk_gaze": basilisk_gaze,
    "petrified": petrified, "glacius_chill": glacius_chill, "glacius_frozen": glacius_frozen,
    "imperius": imperius, "confunded": confunded, "rooted": rooted,
    "possessed": possessed, "possessing": possessing,
}


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in ICONS.items():
        im, d = base()
        fn(d)
        path = os.path.join(OUT, name + ".png")
        im.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
