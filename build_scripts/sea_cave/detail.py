"""Pass 3: detail by rule, never sprinkled.

    apply(blocks) -> {kind: count}

- Pointed dripstone hangs in clusters from the cavern's ceiling lows: where the rock above is low over
  open air or the lake, and only where a slow noise says a cluster is, so they come in groups with
  bare ceiling between. Each hangs 1 to 3 long (tip, frustum, base) and never lower than 3 blocks
  above whatever is under it, so nothing hangs into the walking route.
- Seagrass and kelp grow on the stack's underwater flanks and seat: on rock tops in the sea, below
  the waterline, in patches. They are written into the template's own water cells above the rock.

Only cave_air and template-unlisted sea cells above rock are ever touched: never rock, never the route.
"""

from . import frame as F
from .shape import vn

DRIP = 'minecraft:pointed_dripstone'
SEAGRASS = 'minecraft:seagrass'
KELP = 'minecraft:kelp'
KELP_PLANT = 'minecraft:kelp_plant'
WATER = F.WATER


def _rock(blocks, p):
    v = blocks.get(p)
    return v is not None and v[0] not in (F.CAVE_AIR, F.WATER, 'minecraft:air', 'minecraft:structure_void')


def _air(blocks, p):
    v = blocks.get(p)
    return v is not None and v[0] == F.CAVE_AIR


def dripstone(blocks):
    n = 0
    for (x, y, z), v in list(blocks.items()):
        if v[0] != F.CAVE_AIR or not (F.LAKE_Y + 4 <= y <= 36):
            continue
        if not _rock(blocks, (x, y + 1, z)):
            continue
        # a cluster: slow noise high here, and a little scatter inside it
        if vn(x, z, 7.0, 901) < 0.72 or vn(x, z, 1.7, 902) < 0.45:
            continue
        # clear drop below: at least 3 + length of open air or lake
        length = 1 + int(3 * vn(x, z, 2.3, 903))
        length = min(length, 3)
        clear = 0
        for d in range(1, 12):
            b = blocks.get((x, y - d, z))
            if b is not None and b[0] in (F.CAVE_AIR, F.WATER):
                clear += 1
            else:
                break
        if clear < length + 3:
            continue
        if any((x, y - d, z) in ROUTE for d in range(0, length + 3)):
            continue
        shapes = {1: ['tip'], 2: ['frustum', 'tip'], 3: ['base', 'frustum', 'tip']}[length]
        for i, th in enumerate(shapes):
            blocks[(x, y - i, z)] = (DRIP, {'vertical_direction': 'down', 'thickness': th,
                                            'waterlogged': 'false'}, None)
            n += 1
    return n


def sea_plants(blocks):
    """Seagrass and kelp on underwater rock tops outside the cave (the open sea side)."""
    n = 0
    tops = {}
    for (x, y, z), v in blocks.items():
        if not (24 <= y < F.SEA_Y - 3) or v[0] in (F.CAVE_AIR, F.WATER, 'minecraft:structure_void'):
            continue
        if (x, y + 1, z) in blocks:
            continue       # covered by the template itself (rock, cave or the pool)
        if tops.get((x, z), -1) < y:
            tops[(x, z)] = y
    for (x, z), y in tops.items():
        if vn(x, z, 6.0, 911) < 0.55:
            continue
        if vn(x, z, 2.0, 912) > 0.86 and y < F.SEA_Y - 12:
            h = 3 + int(8 * vn(x, z, 3.0, 913))
            for i in range(1, h):
                blocks[(x, y + i, z)] = (KELP_PLANT, None, None)
            blocks[(x, y + h, z)] = (KELP, {'age': '20'}, None)
            n += h
        elif vn(x, z, 1.3, 914) > 0.35:
            blocks[(x, y + 1, z)] = (SEAGRASS, None, None)
            n += 1
    return n


ROUTE = set()


def _route_cells():
    """Feet and head cells along the descent, the mouth ledge and the shore path: nothing hangs there."""
    out = set()
    pts = list(F.DESCENT) + list(F.SHORE_PATH) + [F.MOUTH]
    for a, b in zip(pts, pts[1:]):
        steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2]), 1)
        for s in range(steps + 1):
            t = s / steps
            x = round(a[0] + (b[0] - a[0]) * t)
            y = round(a[1] + (b[1] - a[1]) * t)
            z = round(a[2] + (b[2] - a[2]) * t)
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for dy in (0, 1, 2):
                        out.add((x + dx, y + dy, z + dz))
    return out


def apply(blocks):
    ROUTE.clear()
    ROUTE.update(_route_cells())
    return {'dripstone': dripstone(blocks), 'sea_plants': sea_plants(blocks)}
