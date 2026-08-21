"""Draw the Gringotts cart's texture.

Matches the box UVs in client/model/GringottsCartModel.java (64x48 sheet):
  (0,0)   18x2x14  tub floor
  (0,16)  1x5x14   side walls
  (30,16) 16x5x1   end walls
  (0,36)  1x4x4    wheels

Dark pitted iron with a warmer rivet line, so it reads as goblin ironwork in a deepslate
tunnel rather than as another grey box.
"""
import os
from PIL import Image, ImageDraw

W, H = 64, 48
IRON_DARK = (46, 44, 48)
IRON = (66, 63, 68)
IRON_LIGHT = (88, 84, 90)
RIVET = (128, 106, 58)
RUST = (92, 62, 44)


def noise(x, y, salt=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def plate(img, x0, y0, w, h, salt):
    """A pitted iron plate with a rivet along its top edge."""
    for x in range(x0, x0 + w):
        for y in range(y0, y0 + h):
            n = noise(x, y, salt)
            if n < 0.10:
                c = IRON_DARK
            elif n < 0.72:
                c = IRON
            elif n < 0.92:
                c = IRON_LIGHT
            else:
                c = RUST
            img.putpixel((x % W, y % H), c)
    for x in range(x0 + 1, x0 + w, 5):
        if 0 <= y0 < H:
            img.putpixel((x % W, y0 % H), RIVET)


def main():
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    # Every box unwraps to a run of faces starting at its texOffs; painting a generous
    # rectangle over each region covers all of them without unwrapping each face by hand.
    plate(img, 0, 0, 64, 16, 1)     # floor box
    plate(img, 0, 16, 30, 20, 2)    # side walls
    plate(img, 30, 16, 34, 20, 3)   # end walls
    plate(img, 0, 36, 24, 12, 4)    # wheels

    # Wheel rims, so they read as wheels and not as blocks.
    d = ImageDraw.Draw(img)
    for i in range(3):
        d.rectangle([i * 8, 36, i * 8 + 5, 41], outline=(30, 28, 32))

    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                       'src', 'main', 'resources', 'assets', 'hogwartsmagic',
                       'textures', 'entity', 'gringotts_cart.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print('wrote %s  %dx%d' % (out, W, H))


if __name__ == '__main__':
    main()
