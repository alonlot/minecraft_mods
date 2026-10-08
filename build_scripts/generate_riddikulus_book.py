"""Paint the Riddikulus spellbook icon.

    python build_scripts/generate_riddikulus_book.py

Authored at 16x16 like generate_piertotum_locomotor_book.py, for the same reasons: every pixel is
placed on purpose, the file is under a kilobyte, and the art is an explicit character grid rather
than drawing calls, because a one-pixel slip at this size is the difference between a toy and a smear.

WHY A JACK-IN-THE-BOX
---------------------
Riddikulus does not fight the Boggart; it makes it silly. The cover shows the joke the spell is
built around: a yellow box, a spring, and a grinning face popped out of it. The cover is pink and
violet because the beam and dust use 0xFF8AD1 (SpellType.RIDDIKULUS), so the book and the spell
read as one thing in the creative tab and on the loot screen.
"""

import os

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "src", "main", "resources", "assets", "hogwartsmagic",
                   "textures", "item", "magic_books",
                   "magic_book_riddikulus.png")

# Pinks and violets around 0xFF8AD1, the colour of the spell's beam, with a warm yellow toy on top.
PALETTE = {
    ".": (0, 0, 0, 0),           # transparent
    "o": (31, 20, 36, 255),      # outline
    "s": (92, 48, 120, 255),     # spine, shadowed side
    "S": (134, 76, 170, 255),    # spine, lit side
    "c": (196, 104, 164, 255),   # cover, in shadow at the gutter and fore-edge
    "C": (255, 138, 209, 255),   # cover (the beam colour)
    "H": (255, 182, 228, 255),   # cover, catching light along the top
    "y": (214, 160, 40, 255),    # box, shadowed side
    "Y": (250, 210, 70, 255),    # box, lit side
    "w": (120, 120, 128, 255),   # spring, grey coils
    "f": (255, 234, 200, 255),   # face
    "e": (31, 20, 36, 255),      # eyes and grin
    "p": (150, 142, 122, 255),   # page block, shadowed
    "P": (206, 199, 178, 255),   # page block
}

# Read this as the icon. Between the covers, top to bottom:
#   rows 3-5  the face (eyes, then a grin)   rows 6-8  the spring   rows 9-12  the box
ART = [
    "................",
    ".oooooooooooooo.",
    ".osScHHHHHHHcPo.",
    ".osScCCfffCCcPo.",
    ".osScCfefefCcPo.",
    ".osScCfeeefCcPo.",
    ".osScCCfffCCcPo.",
    ".osScCCCwCCCcPo.",
    ".osScCCwCCCCcPo.",
    ".osScCCCwCCCcPo.",
    ".osScCYYYYyCcPo.",
    ".osScCYYYYyCcPo.",
    ".osScCYYYYyCcPo.",
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
