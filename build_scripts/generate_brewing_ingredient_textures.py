"""Paint the five brewing ingredient icons and the dittany plant (Cauldron Brewing Phase 2).

    python build_scripts/generate_brewing_ingredient_textures.py [--preview]

Writes to assets/hogwartsmagic/textures/:

  item/dittany.png          a sprig of round, woolly grey-green leaves under a pink flower spike
  item/asphodel.png         white star flowers with yellow hearts on a green stalk
  item/wormwood.png         a feathery silver sprig
  item/valerian_sprigs.png  three stems of pink-white flower heads tied with string
  item/ashwinder_egg.png    a glowing red egg with ember specks
  block/dittany_plant.png   the plant as it grows (a cross model): a low woolly bush with pink spikes

STYLE
-----
Hand-placed pixel art: each sheet is a 16x16 grid of palette letters ('.' is transparent), so a
reviewer can read the picture in this file. The palettes are short (three to five colours) like the
mod's other plant icons (mandrake_root, gillyweed). No randomness: the output is byte-stable.

--preview also writes /tmp/brewing_ingredients_preview.png: every sheet at 8x over a dark
checkerboard, side by side, to look at with an image viewer.
"""

import os
import sys

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "hogwartsmagic", "textures")

DITTANY_LEAF = {
    "L": (169, 185, 154),  # woolly leaf, light
    "l": (127, 148, 112),  # leaf, shade
    "s": (94, 107, 62),    # stem
    "p": (217, 138, 184),  # flower
    "P": (168, 80, 127),   # flower, deep
}

DITTANY_ITEM = [
    "................",
    ".........pP.....",
    "........pPpP....",
    ".........pP.....",
    ".....LL..s......",
    "....LllL.s..LL..",
    "....LllLss.LllL.",
    ".....LLs.s.LllL.",
    ".......s.sssLL..",
    "..LL...ss.s.....",
    ".LllL...s.......",
    ".LllLsssss......",
    "..LL....s.LL....",
    "........sLllL...",
    ".......s.LllL...",
    "......s...LL....",
]

DITTANY_PLANT = [
    "................",
    "......pP...pP...",
    ".....pPp..pPp...",
    "......P....P....",
    "...LL..s..sLL...",
    "..LllL.s.s.LllL.",
    "..LllLLs.sLLllL.",
    "...LLlllsLlllL..",
    ".LL.LlllLLllL.LL",
    "LllLsLLLsLLLsLlL",
    "LllLLs.LsL.sLLlL",
    ".LLlLLs.s.sLlLL.",
    "...LlllLsLlllL..",
    "....LLlLsLlLL...",
    "......LLsLL.....",
    ".......sss......",
]

ASPHODEL = (
    {
        "w": (242, 238, 245),  # petal
        "v": (201, 184, 222),  # petal shade
        "y": (232, 201, 74),   # heart
        "g": (110, 138, 74),   # stalk
        "G": (85, 112, 56),    # leaf
    },
    [
        "................",
        ".......w........",
        "......wyw..w....",
        ".......v..wyw...",
        "....w..g...v....",
        "...wyw.g..g.....",
        "....v.g.gg......",
        ".....g..g.......",
        ".....g.g..w.....",
        "......gg.wyw....",
        "......g...v.....",
        ".....Gg..g......",
        "....GG.gg.......",
        "...GG..g........",
        "......g.........",
        ".....g..........",
    ],
)

WORMWOOD = (
    {
        "S": (197, 204, 192),  # silver leaf
        "s": (152, 163, 148),  # leaf shade
        "t": (122, 132, 98),   # stem
    },
    [
        "................",
        "..........S.....",
        ".........SsS....",
        ".....S..S.t.S...",
        "....SsS..tsSs...",
        "...S.t.S.t..S...",
        "..SsS.tSt.S.....",
        "...S..tt.SsS....",
        ".......tSt.S....",
        "....SsStt.......",
        "...S.S.t.S......",
        "......t.SsS.....",
        ".....tSt.S......",
        "....t.S.........",
        "...t............",
        "..t.............",
    ],
)

VALERIAN = (
    {
        "p": (240, 214, 228),  # flower, pale
        "P": (217, 154, 186),  # flower, pink
        "g": (94, 138, 62),    # stem
        "k": (176, 138, 90),   # string
    },
    [
        "................",
        "...pPp...pPp....",
        "..pPpPp.pPpPp...",
        "...pPp.p.pPp....",
        "....g.pPp.g.....",
        "....g..pg.g.....",
        ".....g.g.g......",
        ".....g.g.g......",
        "......ggg.......",
        "......kkk.......",
        "......ggg.......",
        ".....g.g.g......",
        ".....g.g.g......",
        "....g..g..g.....",
        "....g..g..g.....",
        "................",
    ],
)

ASHWINDER_EGG = (
    {
        "a": (90, 20, 8),      # outline
        "b": (200, 48, 26),    # shell
        "c": (255, 154, 90),   # highlight
        "d": (255, 194, 58),   # ember speck
        "e": (142, 32, 16),    # shade
    },
    [
        "................",
        "................",
        "......aaaa......",
        ".....abbbba.....",
        "....abccbbba....",
        "....abcbbbba....",
        "...abbbbdbbba...",
        "...abbbbbbbba...",
        "...abdbbbbbba...",
        "...abbbbbbdba...",
        "...aebbbbbbea...",
        "....aebbdbea....",
        "....aeebbeea....",
        ".....aeeeea.....",
        "......aaaa......",
        "................",
    ],
)

SHEETS = [
    ("item/dittany", DITTANY_LEAF, DITTANY_ITEM),
    ("item/asphodel", ASPHODEL[0], ASPHODEL[1]),
    ("item/wormwood", WORMWOOD[0], WORMWOOD[1]),
    ("item/valerian_sprigs", VALERIAN[0], VALERIAN[1]),
    ("item/ashwinder_egg", ASHWINDER_EGG[0], ASHWINDER_EGG[1]),
    ("block/dittany_plant", DITTANY_LEAF, DITTANY_PLANT),
]


def paint(name, palette, grid):
    if len(grid) != 16 or any(len(row) != 16 for row in grid):
        raise ValueError(name + ": every sheet is 16 rows of 16 characters")
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, char in enumerate(row):
            if char == ".":
                continue
            if char not in palette:
                raise ValueError(name + ": unknown palette letter " + repr(char))
            image.putpixel((x, y), palette[char] + (255,))
    return image


def preview(images, path, scale=8):
    width = 16 * scale * len(images) + 8 * (len(images) + 1)
    height = 16 * scale + 16
    sheet = Image.new("RGBA", (width, height), (60, 60, 60, 255))
    for y in range(height):
        for x in range(width):
            if ((x // 8) + (y // 8)) % 2:
                sheet.putpixel((x, y), (84, 84, 84, 255))
    left = 8
    for image in images:
        big = image.resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.alpha_composite(big, (left, 8))
        left += 16 * scale + 8
    sheet.save(path)


def main():
    images = []
    for name, palette, grid in SHEETS:
        image = paint(name, palette, grid)
        out = os.path.join(ROOT, name + ".png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        image.save(out)
        images.append(image)
        print("wrote " + os.path.normpath(out))
    if "--preview" in sys.argv[1:]:
        preview(images, "/tmp/brewing_ingredients_preview.png")
        print("wrote /tmp/brewing_ingredients_preview.png")


if __name__ == "__main__":
    main()
