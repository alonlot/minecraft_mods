"""Paint the Piertotum Locomotor spellbook icon.

    python build_scripts/generate_piertotum_locomotor_book.py

WHY THIS ONE IS A SCRIPT AND NOT A PAINTING
-------------------------------------------
Every other magic book in this mod is a large generated illustration (640px to 2048px) scaled
down by the item renderer. That works, but it costs a megabyte a spell and the results drift --
no two of those covers agree on lighting or silhouette. This one is authored at the size it is
actually drawn at, 16x16, so every pixel is placed on purpose and the file is under a kilobyte.
The tradeoff is that it will read as flatter than its neighbours in the creative tab.

WHY THE COVER SHOWS THE MULTIBLOCK
----------------------------------
The engraved figure is not decoration -- it is the recipe. Head, arms straight out, torso, legs
apart is exactly the pattern StatueMultiblock looks for, and the head pixel is amber because the
head is the one block that cannot be substituted: it must be a Statue Heart. A player who has
looked at the icon has been shown what to build. The alternative -- a generic runic cover -- puts
that knowledge only in the tooltip, where it is one line of prose competing with 37 others.

The art is an explicit character grid rather than drawing calls. At this size a loop that computes
positions is harder to read and much harder to correct than the picture itself, and a one-pixel
mistake in a 16x16 icon is the whole difference between a statue and a smudge.
"""

import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "hogwartsmagic",
                   "textures", "item", "magic_books",
                   "magic_book_piertotum_locomotor.png")

# Cool worked-stone greys. The cover highlight is deliberately near 0x9BA0A4, the colour
# SpellType.PIERTOTUM_LOCOMOTOR uses for its beam and dust, so book and spell read as one thing.
PALETTE = {
    ".": (0, 0, 0, 0),           # transparent
    "o": (24, 24, 27, 255),      # outline
    "s": (58, 58, 63, 255),      # spine, shadowed side
    "S": (84, 86, 91, 255),      # spine, lit side
    "c": (92, 96, 100, 255),     # cover, in shadow at the gutter and fore-edge
    "C": (122, 128, 133, 255),   # cover
    "H": (150, 156, 161, 255),   # cover, catching light along the top
    "F": (196, 201, 205, 255),   # the engraved figure
    "g": (214, 160, 74, 255),    # the Statue Heart set where its head goes
    "p": (150, 142, 122, 255),   # page block, shadowed
    "P": (206, 199, 178, 255),   # page block
}

# Read this as the icon. The figure between the covers is the multiblock:
#   row 4  head (the heart)      row 6  arms out          rows 10-11  legs apart
ART = [
    "................",
    ".oooooooooooooo.",
    ".osScHHHHHHHcPo.",
    ".osScCCCCCCCcPo.",
    ".osScCCCgCCCcPo.",
    ".osScCCCFCCCcPo.",
    ".osScCFFFFFCcPo.",
    ".osScCCCFCCCcPo.",
    ".osScCCCFCCCcPo.",
    ".osScCCFFFCCcPo.",
    ".osScCCFCFCCcPo.",
    ".osScCCFCFCCcPo.",
    ".osScCCCCCCCcPo.",
    ".osSccccccccpPo.",
    ".oooooooooooooo.",
    "................",
]


def main():
    if len(ART) != 16 or any(len(row) != 16 for row in ART):
        raise SystemExit("ART must be exactly 16x16; an off-by-one here is invisible in the file "
                         "and obvious in game")

    image = Image.new("RGBA", (16, 16), PALETTE["."])
    pixels = image.load()
    for y, row in enumerate(ART):
        for x, key in enumerate(row):
            if key not in PALETTE:
                raise SystemExit("unknown palette key %r at (%d, %d)" % (key, x, y))
            pixels[x, y] = PALETTE[key]

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
