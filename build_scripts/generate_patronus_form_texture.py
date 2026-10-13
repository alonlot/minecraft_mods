"""Paint the silver texture every personal Patronus form is drawn with.

    python build_scripts/generate_patronus_form_texture.py

Writes assets/hogwartsmagic/textures/entity/patronus_form.png (64x64).

The forms reuse vanilla animal models whose UV layouts all differ, so the texture is deliberately
uniform: a near-white silver with a faint cold mottle and a soft vertical sheen. Any face of any model
lands on the same kind of light, and the renderer's tint and translucency do the rest.
"""

import os
import random

from PIL import Image

SIZE = 64
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
                   "assets", "hogwartsmagic", "textures", "entity", "patronus_form.png")


def main():
    rng = random.Random(7)
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    for y in range(SIZE):
        sheen = 0.88 + 0.12 * (1.0 - abs((y % 16) - 8) / 8.0)
        for x in range(SIZE):
            mottle = rng.uniform(-0.06, 0.04)
            level = max(0.0, min(1.0, sheen + mottle))
            pixels[x, y] = (int(205 * level), int(232 * level), int(255 * level), 255)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
