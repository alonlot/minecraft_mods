"""Placeholder blood-arch textures (16x16), staged from vanilla blackstone.

    python build_scripts/generate_blood_arch_textures.py

Writes textures/block/blood_seal.png (stage 0: bare blackstone with a 1-pixel dark-red hairline where
the arch outline would run), blood_seal_1.png (adds a red smear), blood_seal_2.png (dissolving: the
rock breaks up into holes). Stage 3 is open and never rendered. Task 13 repaints these with the user.
"""
import os
import random
import zipfile
import io

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "hogwartsmagic", "textures", "block")
JAR = os.path.expanduser("~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.21/client-extra.jar")
HAIR = (92, 8, 8)
SMEAR = (120, 10, 10)


def blackstone():
    with zipfile.ZipFile(JAR) as z:
        return Image.open(io.BytesIO(z.read("assets/minecraft/textures/block/blackstone.png"))).convert("RGBA")


def hairline(img):
    px = img.load()
    for i in range(16):
        if i % 5 != 4:  # broken, so a grid of blocks does not read as a grid
            px[i, 0] = HAIR + (255,)
        if i % 7 != 3:
            px[0, i] = HAIR + (255,)
    return img


def smear(img, rng):
    px = img.load()
    for x in range(3, 13):
        length = rng.randint(3, 9)
        if rng.random() < 0.6:
            for y in range(0, length):
                px[x, y] = SMEAR + (255,)
    return img


def dissolve(img, rng):
    px = img.load()
    for y in range(16):
        for x in range(16):
            r = rng.random()
            if r < 0.30:
                px[x, y] = (0, 0, 0, 0)
            elif r < 0.55:
                px[x, y] = SMEAR + (255,)
    return img


def main():
    rng = random.Random(11)
    base = blackstone()
    s0 = hairline(base.copy())
    s1 = smear(s0.copy(), rng)
    s2 = dissolve(s1.copy(), rng)
    for name, img in (("blood_seal", s0), ("blood_seal_1", s1), ("blood_seal_2", s2)):
        img.save(os.path.join(OUT, name + ".png"))


if __name__ == "__main__":
    main()
