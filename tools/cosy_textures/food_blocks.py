"""Block textures for the cosy food and kitchen blocks (16x16 each).

    python tools/cosy_textures/food_blocks.py

Files are prefixed food_ (keg) and kitchen_ (stove, pot) so they cannot collide with the home pack's. Every
texture is drawn deterministically: a small integer hash gives the grain and speckle, so a re-run changes nothing.
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic", "textures", "block")


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def noise(x, y, seed=0):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFF) / 255.0


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def save(img, name):
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, name + ".png"))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


# ---------------------------------------------------------------------------------------------- keg
WOOD = [rgb("#6e4423"), rgb("#835329"), rgb("#9a6532"), rgb("#b07a3f")]
WOOD_SEAM = rgb("#432712")
IRON = [rgb("#3b3f4a"), rgb("#575c6a"), rgb("#7b8090"), rgb("#a0a5b3")]
BRASS = [rgb("#7a531b"), rgb("#a67a2b"), rgb("#d3a43f"), rgb("#f0cf6a")]


def keg_wood():
    """Side staves running left to right: 4 px per stave, a dark seam under each, grain streaks along them."""
    im = new()
    px = im.load()
    for y in range(16):
        stave = y // 4
        for x in range(16):
            if y % 4 == 3:
                px[x, y] = WOOD_SEAM
                continue
            base = WOOD[(stave + (1 if y % 4 == 0 else 0)) % 3 + (1 if y % 4 == 0 else 0)]
            n = noise(x // 3, y, stave)
            px[x, y] = shade(base, 0.9 + 0.2 * n)
    return im


def keg_end():
    """The round head: radial staves, an iron ring and a bung in the middle."""
    im = new()
    px = im.load()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d > 8.2:
                px[x, y] = WOOD_SEAM
            elif d > 6.6:
                px[x, y] = IRON[1 + (x + y) % 2] if d < 7.6 else IRON[0]
            else:
                stave = int((x - cx + 8) // 3)
                seam = (x - 1) % 3 == 2
                base = WOOD[1 + stave % 3]
                px[x, y] = WOOD_SEAM if seam else shade(base, 0.92 + 0.16 * noise(x, y, 5))
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = WOOD[0]
    px[7, 7] = WOOD_SEAM
    return im


def keg_iron():
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = IRON[1]
            if y in (0, 15):
                c = IRON[0]
            elif y in (1, 14):
                c = IRON[3] if y == 1 else IRON[0]
            elif noise(x, y, 9) > 0.86:
                c = IRON[2]
            px[x, y] = c
    for x in (3, 12):
        for y in (4, 11):
            px[x, y] = IRON[3]
    return im


def keg_tap():
    im = new()
    px = im.load()
    for y in range(16):
        for x in range(16):
            c = BRASS[1] if y % 8 < 4 else BRASS[2]
            if x in (0, 15) or y in (0, 15):
                c = BRASS[0]
            elif x == 1 or y == 1:
                c = BRASS[3]
            px[x, y] = c
    return im


def keg_gauge(level):
    """A sight glass on the front: a dark tube, with beer in it from the bottom up to the level (0..3)."""
    im = new()
    px = im.load()
    glass = rgb("#26303a")
    edge = rgb("#8fa3b5")
    for y in range(16):
        for x in range(16):
            px[x, y] = glass
            if x in (0, 15) or y in (0, 15):
                px[x, y] = edge
    fill_to = {0: 16, 1: 11, 2: 6, 3: 1}[level]
    colour = {0: None, 1: rgb("#8a5c1c"), 2: rgb("#d99a2a"), 3: rgb("#f6cd4a")}[level]
    if colour:
        for y in range(fill_to, 15):
            for x in range(1, 15):
                px[x, y] = colour if (y + x) % 7 else shade(colour, 1.18)
        if level == 2:
            for (x, y) in ((4, 9), (10, 12), (7, 7)):
                px[x, y] = rgb("#fff0b0")
        if level == 3:
            for x in range(1, 15):
                px[x, fill_to] = rgb("#fff6d0")
    return im


def main():
    save(keg_wood(), "food_keg_wood")
    save(keg_end(), "food_keg_end")
    save(keg_iron(), "food_keg_iron")
    save(keg_tap(), "food_keg_tap")
    for level in range(4):
        save(keg_gauge(level), f"food_keg_gauge_{level}")
    print("block textures written")


if __name__ == "__main__":
    main()
