"""Paint the 64x32 snake texture.

    python build_scripts/generate_snake_texture.py

Writes assets/hogwartsmagic/textures/entity/snake.png.

Layout (texOffs in client/model/SnakeModel.java): head 3x2x4 at (0,0), tongue 1x0x3 at (16,0),
segments 0-2 (3x2x4) at (0,8), (16,8), (32,8), segment 3 (2x2x4) at (48,8), segment 4 (2x1x4) at
(0,16), segment 5 (1x1x4) at (16,16). The body is a grass-snake green with darker diamond scales
stamped over the whole sheet, so every face of every segment carries the pattern; the head gets two
yellow eyes on its front face (north face of the head box: (4,4) to (6,5)) and the tongue is red.
"""

import os
import random

from PIL import Image

W, H = 64, 32
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "entity", "snake.png")

BASE = (66, 112, 48)
DARK = (34, 64, 28)
LIGHT = (120, 156, 76)
EYE = (232, 204, 58)
PUPIL = (20, 20, 16)
TONGUE = (176, 40, 44)


def main():
    rng = random.Random(11)
    image = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = image.load()
    for y in range(24):
        for x in range(W):
            diamond = (x + y) % 4 == 0 or (x - y) % 4 == 0
            colour = DARK if diamond else BASE
            jitter = rng.choice((-8, 0, 8))
            if rng.random() < 0.08:
                colour = LIGHT
            px[x, y] = (max(0, min(255, colour[0] + jitter)),
                        max(0, min(255, colour[1] + jitter)),
                        max(0, min(255, colour[2] + jitter)), 255)
    # Tongue sheet (16,0)-(24,3).
    for y in range(0, 3):
        for x in range(16, 24):
            px[x, y] = TONGUE + (255,)
    # Eyes on the head's front face, mouth line below them.
    px[4, 4] = EYE + (255,)
    px[6, 4] = EYE + (255,)
    px[5, 4] = DARK + (255,)
    for x in range(4, 7):
        px[x, 5] = PUPIL + (255,)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
