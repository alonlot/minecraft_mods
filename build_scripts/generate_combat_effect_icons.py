"""Paint the five 18x18 shared combat effect icons.

    python build_scripts/generate_combat_effect_icons.py [output_dir]

Writes assets/hogwartsmagic/textures/mob_effect/<effect>.png for stunned, bleeding, cursed_wound,
brittle and silenced (or into output_dir when one is given, for previewing).

WHAT THEY DEPICT
----------------
  * stunned       three gold stars with little speed marks, the cartoon "seeing stars"
  * bleeding      a dark red drop with a highlight
  * cursed_wound  a violet heart split by a black, green-edged crack
  * brittle       a pale ice shard with white fracture lines
  * silenced      a wand under a red no-entry ring

STYLE
-----
Vanilla effect icons are 18x18, flat-shaded, with a dark outline and one highlight, so these are too.
Shapes are drawn with PIL's polygon and ellipse primitives (no anti-aliasing) and then outlined by
a one-pixel dilation pass, which keeps the outline exactly one pixel wide everywhere.
"""

import math
import os
import sys

from PIL import Image, ImageDraw

SIZE = 18
DEFAULT_OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                           "assets", "hogwartsmagic", "textures", "mob_effect")


def blank():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def outline(image, colour):
    """Add a one-pixel outline around every opaque pixel (4-neighbour dilation)."""
    src = image.copy()
    px = src.load()
    out = image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < SIZE and 0 <= ny < SIZE and px[nx, ny][3] != 0:
                    out[x, y] = colour + (255,)
                    break
    return image


def star_points(cx, cy, outer, inner):
    points = []
    for i in range(10):
        radius = outer if i % 2 == 0 else inner
        angle = -math.pi / 2 + i * math.pi / 5
        points.append((cx + radius * math.cos(angle), cy + radius * math.sin(angle)))
    return points


def stunned():
    image = blank()
    draw = ImageDraw.Draw(image)
    for cx, cy, outer in ((5.0, 5.5, 4.0), (13.0, 5.0, 3.5), (9.0, 13.0, 3.5)):
        draw.polygon(star_points(cx, cy, outer, outer * 0.45), fill=(255, 214, 64, 255))
    image = outline(image, (92, 64, 8))
    marks = ImageDraw.Draw(image)
    for x in (1, 2, 15, 16):
        marks.point((x, 11), fill=(214, 196, 140, 255))
    marks.point((3, 12), fill=(214, 196, 140, 255))
    marks.point((14, 12), fill=(214, 196, 140, 255))
    image.putpixel((5, 5), (255, 250, 214, 255))
    image.putpixel((13, 4), (255, 250, 214, 255))
    image.putpixel((9, 12), (255, 250, 214, 255))
    return image


def bleeding():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.polygon([(9, 2), (13, 9), (5, 9)], fill=(150, 10, 12, 255))
    draw.ellipse((4, 7, 14, 16), fill=(150, 10, 12, 255))
    draw.ellipse((10, 11, 13, 15), fill=(110, 4, 6, 255))
    image = outline(image, (48, 0, 2))
    image.putpixel((7, 9), (255, 170, 160, 255))
    image.putpixel((7, 10), (230, 90, 84, 255))
    return image


def cursed_wound():
    image = blank()
    draw = ImageDraw.Draw(image)
    violet = (112, 40, 140, 255)
    draw.ellipse((2, 3, 9, 10), fill=violet)
    draw.ellipse((8, 3, 15, 10), fill=violet)
    draw.polygon([(2, 7), (15, 7), (8.5, 15)], fill=violet)
    image = outline(image, (34, 6, 44))
    crack = [(9, 4), (8, 6), (10, 8), (8, 10), (9, 12), (8, 14)]
    draw = ImageDraw.Draw(image)
    draw.line(crack, fill=(16, 0, 20, 255))
    for x, y in ((10, 6), (9, 9), (10, 11)):
        image.putpixel((x, y), (120, 230, 120, 255))
    image.putpixel((5, 5), (200, 150, 222, 255))
    return image


def brittle():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.polygon([(9, 1), (14, 7), (11, 16), (6, 16), (4, 7)], fill=(170, 222, 240, 255))
    draw.polygon([(9, 1), (14, 7), (11, 16), (9, 9)], fill=(120, 186, 214, 255))
    image = outline(image, (30, 70, 100))
    draw = ImageDraw.Draw(image)
    draw.line([(7, 4), (9, 8), (7, 11), (9, 14)], fill=(255, 255, 255, 255))
    draw.line([(9, 8), (12, 9)], fill=(240, 252, 255, 255))
    return image


def silenced():
    image = blank()
    draw = ImageDraw.Draw(image)
    draw.line([(4, 14), (13, 5)], fill=(120, 78, 40, 255), width=2)
    image.putpixel((13, 4), (255, 255, 255, 255))
    image.putpixel((14, 4), (255, 255, 255, 255))
    image = outline(image, (40, 24, 10))
    draw = ImageDraw.Draw(image)
    draw.ellipse((1, 1, 16, 16), outline=(210, 30, 30, 255), width=2)
    draw.line([(4, 4), (13, 13)], fill=(210, 30, 30, 255), width=2)
    return image


ICONS = {
    "stunned": stunned,
    "bleeding": bleeding,
    "cursed_wound": cursed_wound,
    "brittle": brittle,
    "silenced": silenced,
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
