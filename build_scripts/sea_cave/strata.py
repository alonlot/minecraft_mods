"""Pass 2: the stone of each solid cell, from tilted, gently warped beds.

The stack, the needle, the stump and the cavern walls are one rock, so one set of beds runs through
all of them: a cell's bed comes from the same bed height `frame.bed_eff` that steps the outline in
shape.py (hard beds jut, soft beds recess), plus a gentle warp. Each bed is one stone; neighbouring
beds are always different stones, so every band is exactly one bed, 2 to 6 thick (the warp depends on
x and z only, so a bed keeps its full thickness in every column).

    block_at(x, y, z) -> block id for a solid cell
    LUMA                 mean luma of each stone's textures, from the vanilla client jar
    MAX_STEP             the most two touching bands may differ, on side and on top faces
"""

import math

from . import frame as F
from .shape import sn

# --- luma ------------------------------------------------------------------------------------
# Mean Rec.601 luma (0.299 R + 0.587 G + 0.114 B) over the 16 x 16 texture, read from
# ~/.gradle/caches/forge_gradle/minecraft_repo/versions/1.21/client-extra.jar
# (assets/minecraft/textures/block/<name>.png). `python -m sea_cave.strata <jar>` recomputes it.
# (side, top): the face seen on a cliff, and the face seen on a ledge or from above. The hue column
# is the mean colour's HSV hue and saturation: all but blackstone and tuff are blue-grey with
# saturation under 0.1; blackstone leans plum (0.16), tuff olive (0.06). Both stay near-grey.
#
#   stone                 side   top    hue/sat      texture files
LUMA = {
    'minecraft:deepslate':          (80.4, 87.5),   # 240/0.03   deepslate, deepslate_top
    'minecraft:cobbled_deepslate':  (77.7, 77.7),   # 240/0.04   cobbled_deepslate
    'minecraft:basalt':             (73.3, 81.8),   # 247/0.07   basalt_side, basalt_top
    'minecraft:smooth_basalt':      (73.1, 73.1),   # 243/0.08   smooth_basalt
    'minecraft:blackstone':         (38.1, 38.9),   # 313/0.16   blackstone, blackstone_top
    'minecraft:tuff':               (108.1, 108.1),  # 69/0.06   tuff
}
MAX_STEP = 40.0
# What touching pairs this rules out: blackstone with deepslate (42 / 49) and with basalt's top
# (43); tuff with blackstone (70). So blackstone only ever lies between smooth basalt and cobbled
# deepslate, and tuff never meets it.

D = 'minecraft:deepslate'
C = 'minecraft:cobbled_deepslate'
B = 'minecraft:basalt'
S = 'minecraft:smooth_basalt'
K = 'minecraft:blackstone'
T = 'minecraft:tuff'

# One stone per bed of frame.BEDS, bottom up. Bed bottoms (bed height) in the comments; local y is
# about the same at the stack's centre, a block or two lower to the east (the dip).
# Mostly mid-grey beds told apart by texture (deepslate's fine grain, cobbled deepslate's rubble,
# basalt's columns, smooth basalt's plain face), with thin dark blackstone partings at irregular
# spacing, most on the very soft beds shape.py recesses (so each dark line sits in a groove, the way
# a shale parting weathers back), and one thin pale tuff bed high on the stack. In the cavern the
# floors, the island and the ceiling panels are flat and cut across the beds, so a dark bed there
# shows as blotches on every flat surface; it keeps one dark parting at the height where its walls
# are and its flats are not, and is otherwise the quiet mid-grey beds. (Tuff just above the
# sea, 3 thick, was tried: it read as a painted tide line round the whole foot.)
BED_STONE = [
    D,   #  0  -6  hard
    C,   #  1  -1
    S,   #  2   2  hard
    D,   #  3   6  very soft   the lake bed
    C,   #  4   8  hard     lake surface (10), the island
    D,   #  5  14
    S,   #  6  17  hard     north ledge (18)
    K,   #  7  21           the cavern's one dark parting, 2 thick, round its walls between the
                         #  shore and the ceiling lows (the bed with the most wall and least floor)
    C,   #  8  23  hard
    B,   #  9  28  very soft   columns in the vault
    C,   # 10  31  hard     cavern roof cap, the sea floor
    D,   # 11  37
    B,   # 12  39  hard     waterline: the notch is cut in columnar basalt
    S,   # 13  43           cleft head, antechamber
    C,   # 14  46  hard     the bed that overhangs the west notch (features.py)
    K,   # 15  51  very soft   dark parting the cleft's hairline climbs through, in a groove
    C,   # 16  53  hard
    D,   # 17  57
    S,   # 18  60  hard     the spire's foot, the needle's top
    T,   # 19  66           the one pale bed, 2 thick, round the spire and the shoulder's foot
    B,   # 20  68  hard     shoulder top: columns
    C,   # 21  71           ledge 72
    S,   # 22  76  hard     ledge 76
    K,   # 23  80  very soft   high parting on the spire
    C,   # 24  82  hard
    D,   # 25  87           summit
    C,   # 26  90
    D,   # 27  94
    C,   # 28 100
    D,   # 29 103
]
assert len(BED_STONE) == len(F.BEDS)

# --- the warp --------------------------------------------------------------------------------
# A gentle bend in the beds on top of the planar dip: a whole block up or down over broad patches
# 20 to 40 blocks across. It is a whole-block shift per column, rounded from smooth noise, so it
# moves whole beds and never changes a bed's thickness; and where it is zero (most of the rock)
# the colour bands sit exactly on the beds shape.py steps the outline with, so a snapped top is
# the top of its own band. A fractional warp would misalign them everywhere and speckle every flat
# top with the next band (seen in the first render: dark blotches on the shoulder from above).
WARP = ((1.25, 40.0, 501), (0.35, 15.0, 502), (1.6, 10.0, 504))


def warp(x, z):
    return sum(a * sn(x, z, s, seed) for a, s, seed in WARP)


# A cell's bed height is y + dip(x, z), and the bed boundaries are whole numbers, so a column's beds
# all sit at y + floor(dip): one whole-block shift per column, to which the warp adds its own. That
# shift field
# is smoothed of one-column peaks and pits (a column one block above or below both its neighbours
# along x or along z), which would show as a one-block fin of the next band on a face, and held to
# one block between neighbours.
def _shift_grid():
    W, _, D_ = F.SIZE
    g = {(x, z): math.floor(F.bed_eff(x, 0, z)) + int(round(warp(x, z)))
         for x in range(-1, W + 1) for z in range(-1, D_ + 1)}
    # where a dip step and a warp step fall on the same column the shift jumps 2, which stands a
    # band boundary on end for a block: hold neighbours to 1 apart
    for _ in range(10):
        changed = False
        for (x, z), v in sorted(g.items()):
            for n in ((x - 1, z), (x + 1, z), (x, z - 1), (x, z + 1)):
                if n in g and g[n] < v - 1:
                    g[n] = v - 1
                    changed = True
        if not changed:
            break
    for _ in range(80):
        changed = False
        for (x, z), v in list(g.items()):
            for a, b in (((x - 1, z), (x + 1, z)), ((x, z - 1), (x, z + 1))):
                if a in g and b in g and g[a] == g[b] != v:
                    g[(x, z)] = v = g[a]
                    changed = True
        if not changed:
            break
    return g


_SHIFT = None
_LO = [lo for lo, hi, r, i in F.BED_TABLE]


def bed_index(x, y, z):
    global _SHIFT
    if _SHIFT is None:
        _SHIFT = _shift_grid()
    s = _SHIFT.get((x, z))
    if s is None:
        s = math.floor(F.bed_eff(x, 0, z)) + int(round(warp(x, z)))
    e = y + s
    k = 0
    for i, lo in enumerate(_LO):
        if e >= lo:
            k = i
    return k


# Pass 2b: few, thick, visible strata with ragged contacts, dark at the wet foot and lighter where
# the rock dries out high up. Each entry is (bottom bed height, stone); a stone runs up to the next
# bottom. Neighbouring stones stay within MAX_STEP luma: blackstone only ever touches basalt.
ZONES = [
    (-99, D),
    (18, C),     # cavern walls: rubbly band round the lake shore and up the walls
    (27, D),
    (33, S),     # under the sea floor, the darkening begins
    (36, K),     # the wet foot: black rock from below the waterline (42) to the first ledges
    (47, S),     # smooth basalt, then columnar basalt, over the black foot
    (51, B),
    (55, C),
    (60, D),     # the dry upper stack
    (66, C),     # rubbly bed round the spire's waist (a pale tuff belt here read as a painted ring)
    (69, D),
    (77, B),
    (81, C),     # the crown
]


def _zone_index(e):
    k = 0
    for i, (lo, _) in enumerate(ZONES):
        if e >= lo:
            k = i
    return k


def block_at(x, y, z):
    global _SHIFT
    if _SHIFT is None:
        _SHIFT = _shift_grid()
    s = _SHIFT.get((x, z))
    if s is None:
        s = math.floor(F.bed_eff(x, 0, z)) + int(round(warp(x, z)))
    return ZONES[_zone_index(y + s)][1]


# Blocks that carry an `axis` (pillars): written standing.
AXIS = {D, B}


def props(block):
    return {'axis': 'y'} if block in AXIS else None


def _luma_from_jar(jar):
    import io
    import zipfile
    from PIL import Image
    files = {D: ('deepslate', 'deepslate_top'), C: ('cobbled_deepslate',) * 2,
             B: ('basalt_side', 'basalt_top'), S: ('smooth_basalt',) * 2,
             K: ('blackstone', 'blackstone_top'), T: ('tuff',) * 2}
    z = zipfile.ZipFile(jar)
    out = {}
    for block, names in files.items():
        vals = []
        for n in names:
            im = Image.open(io.BytesIO(z.read('assets/minecraft/textures/block/%s.png' % n))).convert('RGB')
            px = list(im.getdata())[:256]
            vals.append(round(sum(0.299 * r + 0.587 * g + 0.114 * b for r, g, b in px) / len(px), 1))
        out[block] = tuple(vals)
    return out


if __name__ == '__main__':
    import sys
    for k, v in _luma_from_jar(sys.argv[1]).items():
        print('%-30s side %5.1f  top %5.1f   table %s' % (k, v[0], v[1], LUMA[k]))
