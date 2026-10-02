"""Paint the four 16x16 Tome of Mastery item textures.

    python build_scripts/generate_tome_textures.py

Writes assets/hogwartsmagic/textures/item/<tier>_tome_of_mastery.png for common, uncommon, rare
and epic.

WHAT IT DEPICTS
---------------
A closed book seen face-on: spine down the left with two bands, page edges along the bottom, gold
caps on the cover corners and a gold diamond in the middle of the cover. One shared sprite is
painted in each tier's cover colour, so the four read as one family and the tier is legible from
the colour alone. The colours follow the item names (white, yellow, aqua, light purple), which
are vanilla's rarity colours.

STYLE
-----
Flat three-step shading on the cover (highlight on the top and left edge, shade on the right and
bottom) with a darker outline, the way vanilla item sprites are lit from the top left. Every
colour on the cover is derived from the one base colour per tier, so retuning a tier is a
one-line change.
"""

import os

from PIL import Image

SIZE = 16
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                       "assets", "hogwartsmagic", "textures", "item")

# Legend: . clear, o outline, S spine, b spine band, L cover highlight, M cover, D cover shade,
# g emblem and corner caps, G emblem highlight, p page, P page shadow.
SPRITE = [
    "................",
    ".oooooooooooooo.",
    ".oSgLLLLLLLLLgo.",
    ".oSLMMMMMMMMMDo.",
    ".obLMMMMMMMMMDo.",
    ".oSLMMMMgMMMMDo.",
    ".oSLMMMgGgMMMDo.",
    ".oSLMMgGGGgMMDo.",
    ".oSLMMMgGgMMMDo.",
    ".oSLMMMMgMMMMDo.",
    ".obLMMMMMMMMMDo.",
    ".oSLMMMMMMMMMDo.",
    ".oSgDDDDDDDDDgo.",
    ".oSppppppppppPo.",
    ".oooooooooooooo.",
    "................",
]

GOLD = ((201, 152, 48), (255, 222, 120))
PAGE = (238, 228, 204)
PAGE_SHADOW = (190, 176, 146)

# Cover base colour and (emblem, emblem highlight) per tier. Uncommon swaps the gold emblem for
# cream, because gold on a yellow cover disappears.
TIERS = {
    "common": ((196, 190, 178), GOLD),
    "uncommon": ((214, 170, 40), ((255, 246, 214), (255, 255, 255))),
    "rare": ((46, 170, 184), GOLD),
    "epic": ((160, 80, 206), GOLD),
}


def shade(colour, factor):
    return tuple(max(0, min(255, round(v * factor))) for v in colour)


def lighten(colour, amount=0.35):
    return tuple(min(255, round(v + (255 - v) * amount)) for v in colour)


def palette(cover, emblem):
    dark, light = emblem
    return {
        "o": shade(cover, 0.32),
        "S": shade(cover, 0.58),
        "b": dark,
        "L": lighten(cover),
        "M": cover,
        "D": shade(cover, 0.78),
        "g": dark,
        "G": light,
        "p": PAGE,
        "P": PAGE_SHADOW,
    }


def paint(cover, emblem):
    assert len(SPRITE) == SIZE, "sprite must have 16 rows"
    colours = palette(cover, emblem)
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y, row in enumerate(SPRITE):
        assert len(row) == SIZE, "row %d is %d wide" % (y, len(row))
        for x, key in enumerate(row):
            if key == ".":
                continue
            image.putpixel((x, y), colours[key] + (255,))
    return image


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for tier, (cover, emblem) in TIERS.items():
        path = os.path.normpath(os.path.join(OUT_DIR, tier + "_tome_of_mastery.png"))
        paint(cover, emblem).save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
