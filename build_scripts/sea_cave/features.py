"""Pass 2: the big features, each placed by hand on the pass 1 shape and named here.

shape.py already carries the masses (main spire, shoulder, ledges, satellite needle, stump), the north
overhang flake with its crack, the fallen slab at the cavern's north-west wall and the north ledge.
This adds the rest, and each one follows the beds, so the colour and the form agree:

- **West overhang.** The hard bed 14 (cobbled deepslate) runs on out over the wave notch on the
  exposed west face, 1 to 3 blocks, for about 16 blocks along it. Its underside is the bed below.
- **West fallen slab.** A piece of that same bed lies tilted on the apron below it, its east end
  out of the water, cobbled deepslate like the bed it broke from.
- **Vault slab.** A slab of the columnar bed under the cavern vault lies in the lake, high end out
  of the water to the north, low end on the lake bed; basalt like the bed it fell from.
- **Shoulder gendarme.** A joint cut down through the west end of the flat-topped shoulder splits off
  its south-west corner as a pinnacle that stands 2 to 4 above the rest, so the shoulder is a broken
  block with a tooth, not a drum.
- **Mouth balcony.** Pass 1 leaned the cavern's east wall in so far that a 3-thick lip of it stood
  in front of the passage mouth from y 23 to the roof: the mouth ledge looked at a wall, not over
  the cavern. The lip is cut back in front of the ledge, so the ledge's edge drops to the talus and
  the lake and island are in view. Kept more than 4 blocks from the descent.
- **South ledge.** Bed 5 (deepslate) juts 2 to 3 from the cavern's south wall west of the beach, a
  shelf over the water you see from the island.

    apply(blocks)    edits the build in place; new rock is frame.STONE (strata paints it) unless the
                     feature fell or slid, in which case it carries its own stone
"""

import math

from . import frame as F
from . import strata
from .shape import smooth, vn

NOT_ROCK = (F.CAVE_AIR, F.WATER, 'minecraft:air', 'minecraft:structure_void')


def _rock(blocks, p):
    v = blocks.get(p)
    return v is not None and v[0] not in NOT_ROCK


# --- west overhang ---------------------------------------------------------------------------
WEST_OVERHANG = dict(bed=14, z=(38, 55), y=(42, 56), x_max=36, jut=2.6, seed=701)


def west_overhang(blocks):
    o = WEST_OVERHANG
    z0, z1 = o['z']
    added = 0
    for z in range(z0, z1 + 1):
        # out to the full jut in the middle, tapering at both ends, wandering along the face
        j = o['jut'] * smooth((z - z0) / 4.0) * smooth((z1 - z) / 4.0) * (0.7 + 0.6 * vn(z, 0, 3.5, o['seed']))
        for y in range(o['y'][0], o['y'][1] + 1):
            x0 = next((x for x in range(4, o['x_max']) if _rock(blocks, (x, y, z))), None)
            if x0 is None:
                continue
            jut = int(round(j + 0.4 * vn(z, y, 2.0, o['seed'] + 1)))
            for k in range(1, jut + 1):
                p = (x0 - k, y, z)
                if p in blocks or strata.bed_index(*p) != o['bed']:
                    break
                blocks[p] = (F.STONE, None, None)
                added += 1
    return added


# --- fallen slabs ------------------------------------------------------------------------------
# (centre x, z, half length, half width, angle of the long side from +x in degrees, top at the
# centre, top gradient x, z, stone). A slab is 2 thick in every column under a planar top, so it
# never stands on end; its corners are chipped.
SLABS = {
    'west_fallen': (19.5, 49.5, 3.8, 2.3, 75, 41.6, 0.45, 0.12, strata.C, 'outside'),
    'vault': (39.0, 36.5, 4.2, 3.2, 8, 8.6, -0.12, -1.0, strata.BED_STONE[9], 'lake'),
}


N6 = ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))

# Cells of every fallen slab placed by the last apply(): a fallen block is not a bed, so the
# palette_contrast audit judges the bedded rock without them (it still checks their luma contacts).
FALLEN = set()


def slab(blocks, name):
    cx, cz, hl, hw, ang, top, gx, gz, stone, where = SLABS[name]
    cells = []
    a = math.radians(ang)
    ca, sa = math.cos(a), math.sin(a)
    added = 0
    for x in range(int(cx - hl - hw) - 1, int(cx + hl + hw) + 2):
        for z in range(int(cz - hl - hw) - 1, int(cz + hl + hw) + 2):
            u = (x - cx) * ca + (z - cz) * sa
            v = -(x - cx) * sa + (z - cz) * ca
            chip = 0.9 * vn(x, z, 1.7, 711 + int(cx))
            if abs(u) > hl - (chip if abs(v) > hw - 1.2 else 0) or abs(v) > hw:
                continue
            t = int(math.floor(top + gx * (x - cx) + gz * (z - cz)))
            for y in (t - 1, t):
                p = (x, y, z)
                cur = blocks.get(p)
                if where == 'outside' and cur is not None:
                    continue           # only into the open sea, never over the rock it lies on
                if where == 'lake' and (cur is None or cur[0] not in (F.WATER, F.CAVE_AIR)):
                    continue
                blocks[p] = (stone, None, None)
                cells.append(p)
                added += 1
    FALLEN.update(cells)
    return added


# --- shoulder gendarme ------------------------------------------------------------------------
# The joint: x = 29.4 + 0.2 (z - 45), 1 to 2 wide, from the shoulder top down to y 61, z 42..51.
# West of it, round (27, 47), the corner stands up as a pinnacle.
GENDARME = dict(joint=(29.4, 0.2, 45), z=(42, 51), y_from=61, peak=(27.2, 47.3), rise=4.2, seed=731)


def shoulder_gendarme(blocks):
    o = GENDARME
    cut = added = 0
    for z in range(o['z'][0], o['z'][1] + 1):
        xc = o['joint'][0] + o['joint'][1] * (z - o['joint'][2])
        half = 0.55 + 0.35 * vn(z, 0, 2.5, o['seed'])
        for x in range(int(xc) - 2, int(xc) + 3):
            if abs(x + 0.5 - xc) > half:
                continue
            for y in range(o['y_from'], F.SIZE[1]):
                if _rock(blocks, (x, y, z)):
                    del blocks[(x, y, z)]       # open air above the sea: the world's own
                    cut += 1
    px, pz = o['peak']
    for x in range(int(px) - 3, int(px) + 4):
        for z in range(int(pz) - 3, int(pz) + 4):
            xc = o['joint'][0] + o['joint'][1] * (z - o['joint'][2])
            if x + 0.5 >= xc - 0.6:
                continue                         # stays west of the joint
            top = next((y for y in range(F.SIZE[1] - 2, o['y_from'], -1) if _rock(blocks, (x, y, z))), None)
            if top is None or top < 64:
                continue
            r = math.hypot((x + 0.5 - px) * 1.2, z + 0.5 - pz)
            h = int(math.floor(o['rise'] * (1.0 - r / 3.3) + 0.6 * vn(x, z, 1.6, o['seed'] + 1)))
            for y in range(top + 1, top + 1 + max(0, h)):
                blocks[(x, y, z)] = (F.STONE, None, None)
                added += 1
    return cut, added


# --- mouth balcony ----------------------------------------------------------------------------
BALCONY = dict(centre=(52.0, 46.0), reach=8.2, x=(42, 49), z=(42, 50), y=(23, 30), seed=741)


def mouth_balcony(blocks):
    from . import shape
    o = BALCONY
    c = shape._cache()
    guard = [p for p in c.descent if p[0] >= 44 and p[1] >= 18]
    cut = 0
    for x in range(o['x'][0], o['x'][1] + 1):
        for z in range(o['z'][0], o['z'][1] + 1):
            if (x, z) not in c.floor:
                continue
            r = math.hypot(x - o['centre'][0], (z - o['centre'][1]) * 1.3) + 1.2 * vn(x, z, 2.2, o['seed'])
            if r > o['reach']:
                continue
            top = min(c.ceil[(x, z)], o['y'][1] + (1 if vn(x, z, 2.0, o['seed'] + 1) > 0.5 else 0))
            for y in range(max(o['y'][0], c.floor[(x, z)] + 1), top + 1):
                p = (x, y, z)
                if not _rock(blocks, p):
                    continue
                if any((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2 + (p[2] - q[2]) ** 2 <= 4.5 ** 2 for q in guard):
                    continue
                blocks[p] = (F.CAVE_AIR, None, None)
                cut += 1
    # bits of the lip left hanging in the air go too
    box = ((o['x'][0] - 3, o['x'][1] + 3), (o['y'][0] - 3, o['y'][1] + 3), (o['z'][0] - 3, o['z'][1] + 3))
    inside = lambda q: all(box[i][0] <= q[i] <= box[i][1] for i in range(3))
    seen = set()
    for x in range(box[0][0], box[0][1] + 1):
        for y in range(box[1][0], box[1][1] + 1):
            for z in range(box[2][0], box[2][1] + 1):
                s0 = (x, y, z)
                if s0 in seen or not _rock(blocks, s0):
                    continue
                comp, q, escaped = [s0], [s0], False
                seen.add(s0)
                while q:
                    a = q.pop()
                    for d in N6:
                        n = (a[0] + d[0], a[1] + d[1], a[2] + d[2])
                        if n in seen or not _rock(blocks, n):
                            continue
                        if not inside(n):
                            escaped = True
                            continue
                        seen.add(n)
                        comp.append(n)
                        q.append(n)
                if not escaped:
                    for a in comp:
                        blocks[a] = (F.CAVE_AIR, None, None)
                        cut += 1
    return cut


# --- side ledges in the cavern ------------------------------------------------------------------
SOUTH_LEDGE = dict(bed=5, x=(20, 32), z=(50, 63), depth=3.0, seed=721)


def south_ledge(blocks):
    o = SOUTH_LEDGE
    x0, x1 = o['x']
    added = 0
    for x in range(x0, x1 + 1):
        d = o['depth'] * smooth((x - x0) / 3.0) * smooth((x1 - x) / 3.0) * (0.75 + 0.5 * vn(x, 0, 3.0, o['seed']))
        for y in range(F.LAKE_Y + 2, 24):
            zw = next((z for z in range(o['z'][0], o['z'][1] + 1) if _rock(blocks, (x, y, z))), None)
            if zw is None:
                continue
            n = int(round(d + 0.4 * vn(x, y, 2.0, o['seed'] + 1)))
            for k in range(1, n + 1):
                p = (x, y, zw - k)
                v = blocks.get(p)
                if v is None or v[0] != F.CAVE_AIR or strata.bed_index(*p) != o['bed']:
                    break
                blocks[p] = (F.STONE, None, None)
                added += 1
    return added


# The fallen slab shape.py lays at the cavern's north-west wall came down from the scar above it at
# about y 24, which is bed 8 (smooth basalt): it carries that stone, not the beds it now lies across.
def north_west_slab(blocks):
    from . import shape
    cells = [p for p in shape._cache().slab if _rock(blocks, p)]
    for p in cells:
        blocks[p] = (strata.BED_STONE[8], None, None)
    FALLEN.update(cells)
    return len(cells)


def apply(blocks):
    """Place every feature; returns {name: cells added (or repainted)}."""
    FALLEN.clear()
    return {
        'north_west_slab': north_west_slab(blocks),
        'mouth_balcony': mouth_balcony(blocks),
        'west_overhang': west_overhang(blocks),
        'west_fallen_slab': slab(blocks, 'west_fallen'),
        'vault_slab': slab(blocks, 'vault'),
        'south_ledge': south_ledge(blocks),
        'shoulder_gendarme (cut, added)': shoulder_gendarme(blocks),
    }
