"""The vault door's two textures, which are the same plate twice.

    python build_scripts/generate_vault_door_textures.py

  * `textures/block/gringotts_vault_door.png` - 16x16, what the shut door is built out of.
  * `textures/entity/gringotts_vault_door.png` - 64x32, the same plate laid into a cube UV so the
    block-entity renderer can draw a leaf cell that has left its block.

The second one exists because a moving block is not a block any more. Minecraft can only draw a
block where its block is, so the parting has to be drawn by a renderer, and a renderer needs an
entity-style texture rather than an atlas sprite. Generating both from one routine is the only way
the moving half and the still half stay the same door.

The plate itself is a recessed panel with four rivets. One panel per block means the seams between
cells read as panel lines for nothing, which is what the film's door is close up: a grid of riveted
plates, not a sheet.
"""

import io
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAR = r'C:/Users/alonl/.gradle/caches/forge_gradle/minecraft_repo/versions/1.21/client-extra.jar'
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/hogwartsmagic')

BLOCK_TEX = os.path.join(ASSETS, 'textures/block/gringotts_vault_door.png')
ENTITY_TEX = os.path.join(ASSETS, 'textures/entity/gringotts_vault_door.png')


def plate():
    z = zipfile.ZipFile(JAR)
    img = Image.open(io.BytesIO(
        z.read('assets/minecraft/textures/block/iron_block.png'))).convert('RGBA')
    px = img.load()

    def shade(x, y, f):
        r, g, b, a = px[x, y]
        px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), a)

    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            shade(x, y, 0.62)
        for x, y in ((i, 1), (i, 14), (1, i), (14, i)):
            if 1 <= x <= 14 and 1 <= y <= 14:
                shade(x, y, 1.18)
    for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        shade(cx, cy, 1.35)
        shade(cx + 1, cy, 1.22)
        shade(cx, cy + 1, 1.22)
        shade(cx + 1, cy + 1, 0.70)
    return img


def main():
    face = plate()
    os.makedirs(os.path.dirname(BLOCK_TEX), exist_ok=True)
    face.save(BLOCK_TEX)
    print('->', os.path.relpath(BLOCK_TEX, ROOT))

    # A 16-cube box UV is exactly 64x32: up/down across the top, then the four sides.
    cube = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    for x, y in ((16, 0), (32, 0), (0, 16), (16, 16), (32, 16), (48, 16)):
        cube.paste(face, (x, y))
    os.makedirs(os.path.dirname(ENTITY_TEX), exist_ok=True)
    cube.save(ENTITY_TEX)
    print('->', os.path.relpath(ENTITY_TEX, ROOT))


if __name__ == '__main__':
    main()
