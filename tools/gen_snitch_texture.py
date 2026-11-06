"""
Repaint the Golden Snitch's wings (golden_snitch.png, GoldenSnitchModel).

The ball is kept as drawn; the wings were plain white slabs. Each wing (6 long, 3 deep) now reads
as a silver feathered wing: a gold socket at the root, a bright leading edge with a dark spar
behind it, a pale membrane, and scalloped feather tips along the trailing edge that darken toward
the wing tip. Painted texel by texel from where it sits (tools/texture_audit/paint.py).

Run (after bash tools/texture_audit/run.sh has dumped the models):
    python tools/gen_snitch_texture.py [--preview out.png]
"""
import io
import os
import subprocess
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, 'texture_audit'))
from paint import texels, preview  # noqa: E402

SOURCE_COMMIT = '3d22654d'      # the hand-drawn ball this keeps
REL = 'src/main/resources/assets/hogwartsmagic/textures/entity/golden_snitch.png'

SILVER = [(118, 122, 132), (150, 154, 164), (182, 186, 194), (210, 213, 220), (232, 234, 240), (248, 249, 252)]
GOLD = [(150, 104, 28), (196, 146, 40), (228, 184, 64)]


def wing(t):
    nx, ny, nz = t.n
    along = abs(t.local[0]) / 6.0              # 0 at the socket .. 1 at the tip
    if along < 0.17:
        return GOLD[2] if t.face == 'top' else GOLD[1]
    if t.face in ('front', 'back', 'left', 'right'):
        return SILVER[1] if t.face != 'front' else SILVER[3]
    if t.face == 'bottom':
        lvl = 2
    elif nz < 0.34:
        lvl = 5                                # leading edge
    elif nz < 0.5:
        lvl = 1                                # spar
    elif nz < 0.67:
        lvl = 4                                # membrane
    else:
        lvl = 3 if int(t.local[0] * 1.0) % 2 == 0 else 2   # scalloped feather tips
    if along > 0.84:
        lvl -= 1
    return SILVER[max(0, min(len(SILVER) - 1, lvl))]


def main():
    blob = subprocess.run(['git', 'show', '%s:%s' % (SOURCE_COMMIT, REL)], cwd=os.path.join(HERE, '..'),
                          capture_output=True, check=True).stdout
    img = Image.open(io.BytesIO(blob)).convert('RGBA')
    px = img.load()
    for t in texels('snitch'):
        if t.part.endswith('_wing'):
            px[t.u, t.v] = wing(t) + (255,)
    img.save(os.path.join(HERE, '..', REL))
    print('wrote golden_snitch.png')
    if '--preview' in sys.argv:
        preview('snitch', img, sys.argv[sys.argv.index('--preview') + 1])


if __name__ == '__main__':
    main()
