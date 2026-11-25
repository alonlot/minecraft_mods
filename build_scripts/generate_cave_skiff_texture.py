"""Paints the PLACEHOLDER texture for the Locket cave skiff (Task 13 redoes it).

    python build_scripts/generate_cave_skiff_texture.py

Writes src/main/resources/assets/hogwartsmagic/textures/entity/cave_skiff.png (128x64) and an enlarged
preview, texture_proposals/cave_skiff_placeholder.png. Layout, matching CaveSkiffModel:

  x 0..103, y 0..53    the one plank region every wooden box samples (dark, wet, green-tinged boards)
  x 104..123, y 0..10  lantern glass (warm green glow, brighter in the middle)
  x 104..111, y 12..27 lantern post (aged brass)
  x 104..127, y 30..36 lantern cap (aged brass)
"""
import random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/hogwartsmagic/textures/entity/cave_skiff.png"
PREVIEW = ROOT / "texture_proposals/cave_skiff_placeholder.png"

random.seed(24)
img = Image.new("RGBA", (128, 64), (0, 0, 0, 0))
px = img.load()


def clamp(v):
    return max(0, min(255, int(v)))


# Planks: horizontal boards 5 px tall, each its own tone, dark seams and butt joints, damp green sheen.
BOARD = 5
for y in range(0, 54):
    board = y // BOARD
    rnd = random.Random(board * 97)
    tone = rnd.uniform(0.8, 1.15)
    for x in range(0, 104):
        seam = (y % BOARD == 0)
        butt = ((x + board * 29) % 61 == 0)
        grain = 0.9 + 0.2 * random.random()
        if (x // 7 + board) % 3 == 0:
            grain *= 0.92
        r = 42 * tone * grain
        g = 46 * tone * grain
        b = 34 * tone * grain
        if y % BOARD == 1 or random.random() < 0.04:
            g += 6
            b += 3
        if seam or butt:
            r, g, b = r * 0.45, g * 0.5, b * 0.45
        px[x, y] = (clamp(r), clamp(g), clamp(b), 255)

# Lantern glass: dim at the edges, bright in the middle of each face.
for y in range(0, 11):
    for x in range(104, 124):
        edge = min(x - 104, 123 - x, y, 10 - y)
        k = min(1.0, 0.45 + edge * 0.18)
        px[x, y] = (clamp(150 * k + 60), clamp(200 * k + 40), clamp(120 * k + 40), 255)

# Brass: aged, green-bronze.
for (x0, y0, x1, y1) in ((104, 12, 112, 28), (104, 30, 128, 37)):
    for y in range(y0, y1):
        for x in range(x0, x1):
            v = 0.85 + 0.3 * random.random()
            px[x, y] = (clamp(120 * v), clamp(100 * v), clamp(44 * v), 255)

OUT.parent.mkdir(parents=True, exist_ok=True)
img.save(OUT)
PREVIEW.parent.mkdir(parents=True, exist_ok=True)
img.resize((128 * 6, 64 * 6), Image.NEAREST).save(PREVIEW)
print("wrote", OUT, PREVIEW)
