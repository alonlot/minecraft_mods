"""Paint the Whomping Willow's particle sprites and write their particle definitions.

    python build_scripts/generate_willow_particle_textures.py

Hand-placed 8x8 grids, one character per pixel, in the same palette as the willow's own texture
(generate_whomping_willow_texture.py), so a leaf shaken off the tree is the green of the tree.
Vanilla particles are 8x8 too, so these sit at the same texel density as everything around them.

Writes textures/particle/<name>_<i>.png and particles/<name>.json (the sprite list the client's
particle engine stitches into the particle atlas).
"""

import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic")
TEX = os.path.join(ASSETS, "textures", "particle")
DEFS = os.path.join(ASSETS, "particles")

# --- palette (matches the willow texture) ---------------------------------------------------------
P = {
    ".": (0, 0, 0, 0),
    # leaves
    "d": (44, 56, 33, 255),      # LEAF_DARK
    "m": (74, 92, 48, 255),      # LEAF_MID
    "l": (108, 124, 68, 255),    # LEAF_LIGHT
    "y": (128, 118, 54, 255),    # LEAF_SICK
    "Y": (156, 140, 66, 255),    # sick, lit
    "v": (58, 70, 40, 255),      # midrib
    # bark
    "k": (38, 31, 24, 255),      # BARK_DARK
    "b": (66, 55, 42, 255),      # BARK_MID
    "B": (98, 84, 65, 255),      # BARK_LIGHT
    "g": (112, 106, 94, 255),    # BARK_GREY (cut heartwood)
    # dust: earthy, alpha-graded so the puff has a soft edge
    "1": (92, 74, 54, 70),
    "2": (92, 74, 54, 130),
    "3": (86, 68, 50, 190),
    "4": (78, 62, 46, 235),
    # glint: sickly green-gold
    "o": (190, 210, 110, 120),
    "O": (210, 228, 130, 220),
    "W": (240, 248, 200, 255),
}

SPRITES = {
    # Narrow lanceolate willow leaves: the long thin shape is what says "willow" rather than "oak".
    "willow_leaf": [
        ["........",
         "......lm",
         ".....lmd",
         "....lvd.",
         "...mvd..",
         "..mvd...",
         ".mdd....",
         "md......"],
        ["........",
         ".......m",
         "......ml",
         ".....mvd",
         "...mmvd.",
         ".mmvdd..",
         "mddd....",
         "........"],
        ["........",
         "........",
         "......lm",
         "...llmmd",
         "mmmvvvd.",
         ".ddddd..",
         "........",
         "........"],
        ["........",
         "......Yy",
         ".....Yyy",
         "....Yvy.",
         "...yvy..",
         "..yvd...",
         ".ydd....",
         "yd......"],
    ],
    # Splinters: a pale cut face against dark outer bark, irregular, never a tidy square.
    "bark_chip": [
        ["........",
         "........",
         "...kb...",
         "..kbBg..",
         "..bBgk..",
         "...gk...",
         "........",
         "........"],
        ["........",
         "........",
         "..kkb...",
         "..bBBbk.",
         "...kgBb.",
         ".....k..",
         "........",
         "........"],
        ["........",
         "....k...",
         "...kb...",
         "...bB...",
         "...Bg...",
         "...gk...",
         "....k...",
         "........"],
        ["........",
         "........",
         "........",
         "..kbbk..",
         ".kBggBk.",
         "..kbbk..",
         "........",
         "........"],
    ],
    # Dust runs dense clod -> loose haze across the particle's life (setSpriteFromAge).
    "root_dust": [
        ["........",
         "........",
         "...23...",
         "..3443..",
         "..3443..",
         "...32...",
         "........",
         "........"],
        ["........",
         "...12...",
         "..2332..",
         ".23443..",
         ".234432.",
         "..2332..",
         "...21...",
         "........"],
        ["..1.....",
         ".1221...",
         ".233221.",
         "12333221",
         ".1233321",
         ".122321.",
         "..1121..",
         "....1..."],
        [".1...1..",
         "1.1.1.1.",
         ".12121.1",
         "1.2221..",
         ".12221.1",
         "1..121..",
         ".1..1.1.",
         "..1....."],
    ],
    # A four-pointed glint that tightens and goes out.
    "willow_glint": [
        ["...o....",
         "...O....",
         "..oWo...",
         "oOWWWOo.",
         "..oWo...",
         "...O....",
         "...o....",
         "........"],
        ["........",
         "...o....",
         "...O....",
         ".oOWOo..",
         "...O....",
         "...o....",
         "........",
         "........"],
        ["........",
         "........",
         "...o....",
         "..oWo...",
         "...o....",
         "........",
         "........",
         "........"],
    ],
}


def paint(grid):
    img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(grid):
        assert len(row) == 8, row
        for x, ch in enumerate(row):
            px[x, y] = P[ch]
    return img


def main():
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(DEFS, exist_ok=True)
    for name, frames in SPRITES.items():
        assert len(frames) and all(len(f) == 8 for f in frames), name
        refs = []
        for i, grid in enumerate(frames):
            paint(grid).save(os.path.join(TEX, "%s_%d.png" % (name, i)))
            refs.append("hogwartsmagic:%s_%d" % (name, i))
        with open(os.path.join(DEFS, name + ".json"), "w", encoding="utf-8") as fh:
            json.dump({"textures": refs}, fh, indent=2)
            fh.write("\n")
        print("wrote %s: %d sprites" % (name, len(frames)))


if __name__ == "__main__":
    main()
