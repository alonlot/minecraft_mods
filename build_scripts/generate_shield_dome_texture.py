"""Generates textures/entity/shield_hex.png: the Protego Maxima dome's tiling hex lattice (ShieldDomeRenderer).

    python -I build_scripts/generate_shield_dome_texture.py

Additive, so black is nothing: soft bright cell edges over a dim interior, brighter toward each cell's rim. The tile
repeats seamlessly: it holds two pointy-top hex columns across and one vertical period (two rows), the rows stretched
a little to fill the square (the dome's mesh stretches it far more anyway).
"""
import math
import os
from PIL import Image

N = 128
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets',
                   'hogwartsmagic', 'textures', 'entity', 'shield_hex.png')


def hex_dist(px, py):
    """Distance (in hex-radius units, 1 at a cell's edge) from (px, py) to the nearest cell centre's edge measure."""
    best = 9.0
    w = 0.5            # a cell's width in tile units (two across)
    vp = 1.0           # vertical period (two rows)
    a = w / math.sqrt(3)
    for cx0, cy0 in ((0.25, 0.0), (0.75, 0.0), (0.0, 0.5), (0.5, 0.5), (1.0, 0.5)):
        for ox in (-1, 0, 1):
            for oy in (-1, 0, 1):
                cx, cy = cx0 + ox, cy0 + oy * vp
                dx, dy = abs(px - cx), abs(py - cy) * (2 * 1.5 * a) / vp * 1.0
                # pointy-top hex metric: max of the three slab distances, 1 at the edge
                d = max(dx / (w / 2), (dx * 0.5 + dy * math.sqrt(3) / 2) / (w / 2))
                best = min(best, d)
    return best


def main():
    img = Image.new('RGBA', (N, N))
    px = img.load()
    for y in range(N):
        for x in range(N):
            d = hex_dist((x + 0.5) / N, (y + 0.5) / N)
            edge = math.exp(-((1.0 - d) / 0.045) ** 2)
            inner = 0.22 + 0.25 * d ** 4
            v = min(1.0, inner + edge)
            c = int(round(255 * v))
            px[x, y] = (c, c, c, 255)
    img.save(OUT)
    print('wrote', OUT)


if __name__ == '__main__':
    main()
