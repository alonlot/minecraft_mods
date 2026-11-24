"""The working pieces in the template and the hidden markers the Java side reads.

    apply(blocks) -> list of (role, index, pos)

Markers are `hogwartsmagic:cave_waypoint` blocks (invisible, no collision) with block-entity NBT
{Role, Index}. They sit in open cells only (cave_air), so they never punch a hole in rock or water,
and they turn with the template, so the cave works at any rotation. Roles:
    basin        two above the basin (head height over it): the anchor
    arch         in the passage just past the blood arch, at the passage roof
    ring         the standing cell on the shore; the iron ring block is set in the rock floor right under it
    dock_shore   above the water where the skiff rises, by the ring
    path 0..n    above the lake, the skiff's crossing in order
    dock_island  above the water where the skiff grounds on the island
    lake         one cell above the lake surface: lake surface y = this y - 1

Also placed: the Basin of Despair, the crystal goblet beside it, and the blood seal filling the arch.
"""

from . import frame as F

WAYPOINT = 'hogwartsmagic:cave_waypoint'
BASIN = 'hogwartsmagic:despair_basin'
GOBLET = 'hogwartsmagic:crystal_goblet'
SEAL = 'hogwartsmagic:blood_seal'
RING = 'hogwartsmagic:cave_ring'


def _put_marker(blocks, out, role, index, pos):
    v = blocks.get(pos)
    if v is None or v[0] != F.CAVE_AIR:
        raise ValueError('marker %s %d at %s is not in open cave air (%s)' % (role, index, pos, v))
    blocks[pos] = (WAYPOINT, None, {'Role': ('string', role), 'Index': ('int', index)})
    out.append((role, index, pos))


def _standing(blocks, near, r=6):
    """The open cave cell nearest `near` that a player could stand in: rock below, air above."""
    best = None
    for dx in range(-r, r + 1):
        for dy in range(-3, 4):
            for dz in range(-r, r + 1):
                p = (near[0] + dx, near[1] + dy, near[2] + dz)
                below = blocks.get((p[0], p[1] - 1, p[2]))
                if (blocks.get(p, (None,))[0] == F.CAVE_AIR
                        and blocks.get((p[0], p[1] + 1, p[2]), (None,))[0] == F.CAVE_AIR
                        and below is not None and below[0] not in (F.CAVE_AIR, F.WATER)):
                    d = dx * dx + dy * dy * 4 + dz * dz
                    if best is None or d < best[0]:
                        best = (d, p)
    if best is None:
        raise ValueError('no standing cell near %s' % (near,))
    return best[1]


def apply(blocks):
    out = []
    bx, by, bz = F.BASIN
    blocks[(bx, by, bz)] = (BASIN, {'stage': 'full', 'locket': 'false'}, None)
    blocks[F.GOBLET] = (GOBLET, None, None)
    _put_marker(blocks, out, 'basin', 0, (bx, by + 2, bz))

    # the blood seal fills the arch opening: x = ARCH_X, z over ARCH_Z, the passage's 3 tall air
    z0, z1 = F.ARCH_Z
    for z in range(z0, z1 + 1):
        for y in range(F.ARCH_FEET, F.ARCH_FEET + 3):
            if blocks.get((F.ARCH_X, y, z), (None,))[0] == F.CAVE_AIR:
                # FACING points to the outside: the antechamber, west of the arch
                blocks[(F.ARCH_X, y, z)] = (SEAL, {'facing': 'west', 'open': 'false'}, None)
    _put_marker(blocks, out, 'arch', 0, (F.ARCH_X + 1, F.ARCH_FEET + 2, (z0 + z1) // 2))

    rx, ry, rz = _standing(blocks, F.RING)
    _put_marker(blocks, out, 'ring', 0, (rx, ry, rz))
    # the ring itself lies set in the shore floor, in the rock cell under the marker
    under = blocks.get((rx, ry - 1, rz))
    if under is None or under[0] in (F.CAVE_AIR, F.WATER):
        raise ValueError('no rock under the ring marker at %s' % ((rx, ry, rz),))
    blocks[(rx, ry - 1, rz)] = (RING, None, None)
    sx, sy, sz = F.SKIFF[0]
    _put_marker(blocks, out, 'dock_shore', 0, (sx, sy + 1, sz))
    pts = F.SKIFF[1:-1]
    # one more point between each pair so the crossing curves through at least three
    path = []
    for a, b in zip(pts, pts[1:]):
        path.append(a)
        path.append(((a[0] + b[0]) // 2, a[1], (a[2] + b[2]) // 2))
    path.append(pts[-1])
    for i, (x, y, z) in enumerate(path):
        _put_marker(blocks, out, 'path', i, (x, y + 1, z))
    ix, iy, iz = F.SKIFF[-1]
    _put_marker(blocks, out, 'dock_island', 0, (ix, iy + 1, iz))
    # the lake marker: above a lake cell between shore and island
    lx, lz = (sx + ix) // 2 + 2, (sz + iz) // 2 + 2
    _put_marker(blocks, out, 'lake', 0, (lx, F.LAKE_Y + 1, lz))
    return out
