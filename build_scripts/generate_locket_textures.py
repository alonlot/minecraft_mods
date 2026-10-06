"""Paint the Unquenchable Thirst effect icon (18x18) and the Slytherin's Locket item texture (16x16).

    python build_scripts/generate_locket_textures.py

Writes textures/mob_effect/unquenchable_thirst.png and textures/item/slytherins_locket.png.
Same style as generate_combat_effect_icons.py: flat shading, one-pixel dark outline, one highlight.
"""

import os

from PIL import Image, ImageDraw

ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                      "assets", "hogwartsmagic", "textures")


def outline(image, colour):
    w, h = image.size
    src = image.copy().load()
    out = image.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] != 0:
                    out[x, y] = colour + (255,)
                    break
    return image


def thirst():
    """A cracked, dry green droplet-shaped basin cup: a pale-green drop with a dry crack through it."""
    image = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    green = (62, 107, 72, 255)
    d.polygon([(9, 2), (13, 9), (5, 9)], fill=green)
    d.ellipse((4, 7, 14, 16), fill=green)
    d.ellipse((10, 11, 13, 15), fill=(40, 76, 50, 255))
    image = outline(image, (14, 32, 18))
    for x, y in ((9, 6), (8, 8), (9, 10), (8, 12), (9, 14)):
        image.putpixel((x, y), (14, 32, 18, 255))
    image.putpixel((7, 9), (190, 235, 170, 255))
    image.putpixel((7, 10), (130, 190, 120, 255))
    return image


def locket():
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(image)
    # chain loop
    d.arc((5, 0, 10, 5), 180, 360, fill=(190, 160, 50, 255))
    d.line([(5, 3), (5, 4)], fill=(190, 160, 50, 255))
    d.line([(10, 3), (10, 4)], fill=(190, 160, 50, 255))
    gold, dark = (222, 184, 56, 255), (170, 130, 30, 255)
    d.ellipse((2, 4, 13, 15), fill=gold)
    d.ellipse((4, 6, 11, 13), fill=dark)
    d.ellipse((5, 6, 11, 12), fill=(30, 70, 34, 255))
    image = outline(image, (70, 48, 8))
    # serpentine S
    s = (110, 220, 100, 255)
    for x, y in ((7, 7), (8, 7), (9, 7), (7, 8), (7, 9), (8, 9), (9, 9), (9, 10), (7, 11), (8, 11), (9, 11)):
        image.putpixel((x, y), s)
    image.putpixel((4, 7), (255, 240, 170, 255))
    image.putpixel((3, 8), (255, 240, 170, 255))
    return image


def main():
    for sub, name, img in (("mob_effect", "unquenchable_thirst", thirst()), ("item", "slytherins_locket", locket())):
        path = os.path.join(ASSETS, sub, name + ".png")
        img.save(path)
        print("wrote", os.path.normpath(path))


if __name__ == "__main__":
    main()
