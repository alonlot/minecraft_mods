"""Recolour the Hungarian Horntail's hide into a Ukrainian Ironbelly.

The Ironbelly is the larger breed and it has been kept in the dark under a bank for a very
long time, so this is the Horntail desaturated to a cold metallic grey, lightened, and then
scarred: pale streaks where the chains and the whip have been, and one blind eye.

Run from the repo root:  python build_scripts/generate_ironbelly_texture.py
"""
import os
from PIL import Image

SRC = 'src/main/resources/assets/hogwartsmagic/textures/entity/hungarian_horntail.png'
OUT = 'src/main/resources/assets/hogwartsmagic/textures/entity/ukrainian_ironbelly.png'


def noise(x, y, salt=0):
    h = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def main():
    if not os.path.exists(SRC):
        raise SystemExit('no horntail texture at ' + SRC)
    img = Image.open(SRC).convert('RGBA')
    w, h = img.size
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))

    for x in range(w):
        for y in range(h):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            # Desaturate hard, lift, and push very slightly cold. A dragon the colour of old iron.
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            lum = min(255.0, lum * 1.18 + 46.0)
            nr = int(min(255, lum * 0.98))
            ng = int(min(255, lum * 1.00))
            nb = int(min(255, lum * 1.06))

            # Scarring: pale streaks, sparse and roughly horizontal, where it has been handled.
            if noise(x // 3, y, 11) > 0.955:
                nr = min(255, nr + 52)
                ng = min(255, ng + 52)
                nb = min(255, nb + 48)
            # Grime in the pits, so it does not come out uniformly clean.
            elif noise(x, y, 12) < 0.10:
                nr = int(nr * 0.80)
                ng = int(ng * 0.80)
                nb = int(nb * 0.82)

            out.putpixel((x, y), (nr, ng, nb, a))

    out.save(OUT)
    print('wrote %s  %dx%d  (from the horntail hide)' % (OUT, w, h))


if __name__ == '__main__':
    main()
