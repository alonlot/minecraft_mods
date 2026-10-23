"""Builds the First Task memory's grounds: structure/first_task_grounds.nbt, the rocky ravine arena with its timber
stands and the Horntail's nest, the Highland ground between it and the Mini Hogwarts (SCHEMATIC), the gorge
under the castle's west cliff and the stone viaduct across it, and the anchors first_task_scene.py stages
`first_task_arena`, `first_task_chase` and `first_task_egg` with.

    python build_scripts/pensieve_scenes/first_task_grounds.py

Using the set from a scene (first_task_scene.py):

    import first_task_grounds as FG
    'radius': FG.RADIUS,
    'set': {'templates': [{'id': 'hogwartsmagic:first_task_grounds', 'at': list(FG.TEMPLATE_AT)}],
            'schematics': [FG.SCHEMATIC], 'fills': FG.FILLS, 'time_of_day': FG.TIME_OF_DAY}
    sky: {'kind': 'effect', 'id': 'sky', 'style': 'overcast', 'at': FG.ANCHORS['nest_centre']}
    fog_far: FG.FOG_FAR

The set
-------
  - The arena (centre ARENA_C, floor at stand y 0): a ravine of grey rock, a rounded-rectangle floor about 60 x 44
    of coarse dirt, gravel and tuff with dragon-scorched patches, scree and loose boulders, closed by cliffs of
    stone, andesite, tuff and cobble that climb 16 to the rim (stand y 16) with ragged ledges. In it:
      * the nest east of centre: a heap of boulders with a flat rock shelf at its back where she perches and a
        bowl in front holding her clutch (vanilla dragon eggs on rock) with a gap for the golden egg;
      * the hiding rock between the nest and the entrance, a boulder about 3.5 tall with a flat face to the nest;
      * the stake behind the nest for her chain;
      * the champions' entrance: a timber-lined tunnel through the west cliff under the west stand.
  - The stands: four straight timber grandstands on the rim, one on each side, 15 rows each (a seat slab with the
    next row's deck behind it as the back rest), aisles of stairs, a fascia of school colours along each front,
    a back wall with posts and beams, flags on poles along the crest. The commentator's booth is at the top of the
    north stand, and the two cannon platforms are at the top corners of the south stand. Rock crags fill the
    corners between the stands, lower in the north-east so the castle shows from the arena.
  - The ground: a Highland plateau at stand y 16 (grass, moss, coarse dirt, scattered boulders, heather), rising to
    snow-capped mountains round the set's edge so no chase-height camera sees the set end.
  - The castle: the Mini Hogwarts (SCHEMATIC, rotation 180 so its spire face looks west at the arena) on the
    plateau, its crag foot closed with rock, its loose gravel propped.
  - The gorge: a canyon about 36 wide and 38 deep with a stream, running north to south past the castle's west
    side, rising into the mountains in the north and ending in a head south of the viaduct.
  - The viaduct: a stone bridge 3 wide across the gorge at z -20, from a watch tower on the west rim to the castle
    side: a central arch 18 wide over the stream (springing y 2, crown underside y 11, so 33 of air under it),
    a side arch either way, tall piers on the canyon walls, a parapet. The dragon hits its central span.

ANCHORS format
--------------
Every coordinate is a SCENE coordinate (template local + TEMPLATE_AT). The arena floor is the block layer at
y -1, so anyone on it stands at y 0 (ground_y); the plateau outside stands at y 16 (plateau_y). yaw is
Minecraft's: 0 faces +Z (south), 90 -X (west), 180 -Z (north), -90 +X (east).

  ground_y, plateau_y  0.0 and 16.0
  nest_centre      [x, y, z] the middle of the nest at floor level
  nest_perch       {pos: [x, y, z] the point between her feet on the rock shelf (y on its top), yaw: 90 (facing
                   west, at the entrance and the hiding rock), shelf: [[x1, y1, z1], [x2, y2, z2]] its top's
                   bounds}
  egg_spot         [x, y, z] the golden egg's centre in the gap of the clutch (it rests on rock)
  eggs             [[x, y, z], ...] the dragon eggs' cell centres
  entrance         {pos: [x, y, z] the tunnel mouth on the arena floor, yaw: -90 (facing in, +X), inside: a spot
                   back in the dark tunnel}
  hide_rock        {spot: [x, y, z] the viewer's crouch behind it, face_centre, face_normal, face_box:
                   [[x1, y1, z1], [x2, y2, z2]] the flat face toward the nest, top_y}
  stake            [x, y, z] the top of the chain's post (where the last link hooks)
  cannon_spots     2 x {muzzle: [x, y, z], dir: [dx, dy, dz], smoke: [x, y, z]} on the south stand's corners
  stands           [{name, school, facing_yaw, rows: [[x1, y1, z1, x2, y2, z2], ...]}] seat-top segments
                   (bottom slabs, 2 air above), split at the aisles; figures face facing_yaw
  commentator_box  {stand: [x, y, z], yaw, look: [x, y, z]} the booth's front at the top of the north stand
  broom_lane       [[x, y, z], ...] spline waypoints for the Firebolt from the north-east, over the north stand,
                   down to the hiding rock (the last point is beside the rock, 1.3 above the floor)
  arena_air_box    [[x1, y1, z1], [x2, y2, z2]] an all-air box over the arena floor (above the nest)
  launch_lane      [[x, y, z], ...] her climb out of the arena over the north-east crags toward the castle
  castle_route     [[x, y, z], ...] air waypoints over and between the towers, each 6 or more from every block
  gorge_lane       [[x, y, z], ...] the dive into the gorge and the flight south under the viaduct's central arch
                   and out of the gorge head toward the arena
  slate_roofs      [{cells: [[x, y, z], ...], normal: [nx, ny, nz], centre}] castle roof cells the tail smashes
  bridge           {span_box, break_box, cells, underside_y, fall_column, arch: {x1, x2, crown_y, spring_y},
                   deck_y}: boxes are [[x1, y1, z1], [x2, y2, z2]] cell bounds (max exclusive); `cells` (filled by build()) is every
                   block in break_box (they go to air at the crash); fall_column is all air below the break box
                   down to the gorge floor
  castle_view, arena_view, gorge_view  {pos, look} camera spots
  castle_box       [[x1, y1, z1], [x2, y2, z2]] the Mini Hogwarts' footprint and height
  RADIUS           int
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import (physics_problems, FREE, STRUCTURES, schematic_grid, rotate_xz,  # noqa: E402
                          SCHEMATIC_SKIPPED, is_gravity)
from great_hall_set import state, bid, parse_state, write_template  # noqa: E402

OUT = os.path.join(STRUCTURES, 'first_task_grounds.nbt')
RADIUS = 200
TIME_OF_DAY = 6000
FOG_FAR = 220
MAX_TOTAL = 450000                      # template + schematic + fills
G = 16                                  # the plateau: stand y (its top block at y 15)
GT = G - 1
TEMPLATE_AT = (-200, -30, -200)
SIZE = (401, 170, 401)                  # y -30..139

ARENA_C = (-102, 82)                    # the arena's centre (x, z); its floor stands at y 0
FLOOR_A, FLOOR_B, SUPER_N = 32.0, 24.0, 3.2
RIM_S = 1.22                            # the rim, in floor-normalised superellipse units
NROWS = 13


def row_y(k):
    """The deck block y of row k: the rows climb 3 every 2 (a steep stand, about 37 degrees)."""
    return GT + (3 * k) // 2
CASTLE_ROT = 180
CASTLE_AT = (28, GT, -100)
SCHEMATIC = {'id': 'hogwartsmagic:mini_hogwarts', 'at': list(CASTLE_AT), 'rotation': CASTLE_ROT}
FILLS = []                              # the viaduct and the ground are in the template
BRIDGE_Z = (-21, -19)


# ---------------------------------------------------------------- noise
def _hash(ix, iz, seed):
    h = (ix * 374761393 + iz * 668265263 + seed * 982451653) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0x1000000)


def _smooth(t):
    return t * t * (3 - 2 * t)


def vnoise(x, z, scale, seed):
    fx, fz = x / scale, z / scale
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = _smooth(fx - ix), _smooth(fz - iz)
    a, b = _hash(ix, iz, seed), _hash(ix + 1, iz, seed)
    c, d = _hash(ix, iz + 1, seed), _hash(ix + 1, iz + 1, seed)
    return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz


def fbm(x, z, seed, scale=40.0):
    return (0.5 * vnoise(x, z, scale, seed) + 0.3 * vnoise(x, z, scale / 2.5, seed + 7) +
            0.2 * vnoise(x, z, scale / 6.0, seed + 13))


def rnd(x, y, z, seed):
    return _hash(x * 31 + y * 7919, z * 17 + y * 104729, seed)


def clamp(v, a, b):
    return a if v < a else b if v > b else v


class World:
    def __init__(self):
        self.g = {}

    def set(self, p, s):
        if s is None or s == 'minecraft:air':
            self.g.pop(p, None)
        else:
            self.g[p] = s if ':' in s else 'minecraft:' + s

    def get(self, p):
        return self.g.get(p)


# ---------------------------------------------------------------- the castle
def castle_cells():
    """{scene cell: block id} of the Mini Hogwarts as SetBuilder places it (ids only)."""
    w, h, l, cells = schematic_grid('mini_hogwarts')
    out = {}
    for y in range(h):
        for z in range(l):
            for x in range(w):
                b = cells[x + z * w + y * w * l]
                if b in SCHEMATIC_SKIPPED:
                    continue
                rx, rz = rotate_xz(x, z, w, l, CASTLE_ROT)
                out[(CASTLE_AT[0] + rx, CASTLE_AT[1] + y, CASTLE_AT[2] + rz)] = b
    return out, (w, h, l)


CASTLE, CASTLE_DIMS = castle_cells()
CASTLE_COLS = {}
for (_x, _y, _z) in CASTLE:
    if (_x, _z) not in CASTLE_COLS or _y < CASTLE_COLS[(_x, _z)]:
        CASTLE_COLS[(_x, _z)] = _y
_W, _H, _L = CASTLE_DIMS
CASTLE_FOOT = (CASTLE_AT[0], CASTLE_AT[0] + (_W if CASTLE_ROT in (0, 180) else _L) - 1,
               CASTLE_AT[2], CASTLE_AT[2] + (_L if CASTLE_ROT in (0, 180) else _W) - 1)
CASTLE_TOP_Y = max(p[1] for p in CASTLE)


# ---------------------------------------------------------------- the arena's shape
def arena_local(x, z):
    return x - ARENA_C[0], z - ARENA_C[1]


def arena_s(lx, lz):
    """The floor-normalised superellipse radius: 1 at the floor's edge, RIM_S at the rim."""
    return (abs(lx / FLOOR_A) ** SUPER_N + abs(lz / FLOOR_B) ** SUPER_N) ** (1.0 / SUPER_N)


ARENA_EXT = (68, 60)                    # |lx|, |lz| the arena's works reach (stands and crags)


def in_arena_rect(lx, lz, margin=0):
    return abs(lx) <= ARENA_EXT[0] + margin and abs(lz) <= ARENA_EXT[1] + margin


def arena_wall_top(lx, lz):
    """The ravine's ground top y at a column inside the rim (None outside it)."""
    s = arena_s(lx, lz) + (vnoise(lx, lz, 7.0, 3) - 0.5) * 0.07
    if s >= RIM_S:
        return None
    if s < 1.0:
        # the floor, with scree mounds rising toward the cliff foot
        base = -1
        if s > 0.9:
            base += int((s - 0.9) / 0.1 * 2.2 * vnoise(lx, lz, 4.0, 5) + 0.3)
        return base
    t = (s - 1.0) / (RIM_S - 1.0)
    # a steep cliff with two ragged ledges (terraces at about a third and two thirds)
    f = t ** 0.55
    terr = 0.25 * (math.floor(f * 3) / 3.0) + 0.75 * f
    h = -1 + 16 * terr + (vnoise(lx, lz, 3.0, 9) - 0.5) * 2.4
    return int(clamp(round(h), -1, GT))


# ---------------------------------------------------------------- the stands
STAND_DEFS = [
    # name, axis along which the rows run, outward sign, inner cell of row 0, the run's range, facing yaw, school
    ('north', 'x', -1, -31, (-36, 35), 0.0, 'hogwarts'),
    ('south', 'x', +1, 31, (-36, 35), 180.0, 'hogwarts'),
    ('east', 'z', +1, 40, (-28, 27), 90.0, 'beauxbatons'),
    ('west', 'z', -1, -40, (-28, 27), -90.0, 'durmstrang'),
]
AISLES = {'north': (-12, 11), 'south': (-12, 11), 'east': (0,), 'west': (0,)}
BOOTH = ('north', (-3, 2), 9)           # the commentator's booth: stand, run cells, first row it replaces
CANNONS = [('south', (27, 33), 9), ('south', (-34, -28), 9)]
FASCIA = {'north': ('red_wool', 'yellow_wool'), 'south': ('blue_wool', 'green_wool'),
          'east': ('light_blue_wool', 'white_wool'), 'west': ('red_wool', 'brown_wool')}
FLAGS = {'north': ('red', 'yellow', 'red', 'yellow'), 'south': ('blue', 'green', 'blue', 'green'),
         'east': ('light_blue', 'white', 'blue'), 'west': ('red', 'brown', 'black')}


def row_cells(sd, k):
    """(inner, outer) depth coordinates of row k."""
    sgn, f0 = sd[2], sd[3]
    inner = f0 + sgn * 2 * k
    return inner, inner + sgn


def stand_back(sd):
    return sd[3] + sd[2] * 2 * NROWS


def in_box(sd, a, k):
    name = sd[0]
    if name == BOOTH[0] and BOOTH[1][0] - 1 <= a <= BOOTH[1][1] + 1 and k >= BOOTH[2]:
        return 'booth'
    for nm, (a0, a1), k0 in CANNONS:
        if name == nm and a0 <= a <= a1 and k >= k0:
            return 'cannon'
    return None


def to_scene(lx, lz):
    return lx + ARENA_C[0], lz + ARENA_C[1]


def stand_footprint():
    """{(x, z) scene column: True} under every stand (incl. its back wall) - the ground stays at the plateau."""
    cols = set()
    for sd in STAND_DEFS:
        a0, a1 = sd[4]
        for a in range(a0 - 1, a1 + 2):
            d0, d1 = sorted((sd[3] - sd[2], stand_back(sd)))
            for d in range(d0, d1 + 1):
                lx, lz = (a, d) if sd[1] == 'x' else (d, a)
                cols.add(to_scene(lx, lz))
    return cols


STAND_COLS = stand_footprint()


# ---------------------------------------------------------------- the gorge
def gorge_cx(z):
    return -1.0 + 4.0 * math.sin(z / 37.0) + 2.0 * math.sin(z / 13.0 + 1.0)


GORGE_Z = (-168, 32)                    # its north end (in the mountains) and its head
GORGE_FLOOR = -23                       # top block of the floor (stand y -22)


def gorge_floor_y(z):
    """The gorge floor's top y along it: level, rising at the head (south) and into the mountains (north)."""
    if z > 6:
        return GORGE_FLOOR + (z - 6) * (GT - GORGE_FLOOR) / float(GORGE_Z[1] - 6)
    if z < -140:
        return GORGE_FLOOR + (-140 - z) * 1.6
    return GORGE_FLOOR


def gorge_top(x, z):
    """The ground top the gorge carves to at a column (None when the column is outside it)."""
    if not (GORGE_Z[0] - 30 <= z <= GORGE_Z[1] + 4):
        return None
    dx = abs(x - gorge_cx(z))
    hw_floor = 5.0 + 1.5 * vnoise(x, z, 11.0, 71)
    fy = gorge_floor_y(z)
    if dx <= hw_floor:
        return int(math.floor(fy))
    # the canyon walls: steep, with a ledge two thirds up, ragged
    w = dx - hw_floor
    rise = w * 2.6 + (vnoise(x, z, 4.0, 73) - 0.5) * 5.0
    if w > 7:
        rise += (w - 7) * 2.0
    if 8 < w < 10.5:
        rise = min(rise, 8 * 2.6 + 1)
    return int(math.floor(fy + max(0.0, rise)))


# ---------------------------------------------------------------- the ground's heightfield
MOUNT_D0 = (60, 20)                     # where the mountains start, in from the edge (+ more to the north-east)
MOUNT_H = (94, 24, 30)                  # their height over the plateau (+ north-east, noise)


def mountain_height(x, z):
    """The mountains' top y from the edge in (before the protected zones cap them)."""
    d = min(RADIUS - abs(x), RADIUS - abs(z))
    ne = clamp((x + 40) / 160.0, 0, 1) * 0.6 + clamp((-z + 40) / 160.0, 0, 1) * 0.6
    d0 = MOUNT_D0[0] + MOUNT_D0[1] * clamp(ne, 0, 1)
    hmax = MOUNT_H[0] + MOUNT_H[1] * clamp(ne, 0, 1) + (fbm(x, z, 31, 70.0) - 0.5) * MOUNT_H[2]
    if d >= d0:
        return GT + (fbm(x, z, 37, 45.0) - 0.5) * 3.0
    t = 1.0 - d / d0
    ridge = abs(vnoise(x, z, 23.0, 41) - 0.5) * 2         # ridge lines
    h = hmax * (t ** 1.25) * (0.88 + 0.22 * (1 - ridge)) + (vnoise(x, z, 6.0, 43) - 0.5) * 6 * t
    return GT + max(0.0, h)


def dist_field(protect, rng=60):
    """Chebyshev-ish (8-neighbour BFS) distance from the protected columns, capped at rng."""
    dist = {c: 0 for c in protect}
    frontier = list(protect)
    for step in range(1, rng + 1):
        nxt = []
        for (x, z) in frontier:
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    n = (x + dx, z + dz)
                    if n in dist or abs(n[0]) > RADIUS or abs(n[1]) > RADIUS:
                        continue
                    dist[n] = step
                    nxt.append(n)
        frontier = nxt
    return dist


def protected_columns():
    """Columns the mountains must leave at the plateau: (the arena works, the rest: the castle and the open land
    between them and the gorge where the chase flies low). Behind the arena the mountains may rise steeply (a wall
    of rock round the stands' backs); elsewhere they ramp up gently."""
    arena, other = set(), set()
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            lx, lz = arena_local(x, z)
            if in_arena_rect(lx, lz, 3):
                arena.add((x, z))
            elif CASTLE_FOOT[0] - 8 <= x <= CASTLE_FOOT[1] + 8 and CASTLE_FOOT[2] - 8 <= z <= CASTLE_FOOT[3] + 8:
                other.add((x, z))
            elif -60 <= x <= 30 and -60 <= z <= 60:
                other.add((x, z))                         # the land between arena, gorge and castle
    return arena, other


ARENA_WALL_SLOPE = 4.0                  # how steeply the mountains may rise behind the arena's stands
OPEN_SLOPE = 1.8                        # ... and from the castle and the open land
FOOT_WANDER = 20
MOUNT_TOP = TEMPLATE_AT[1] + SIZE[1] - 4   # the highest summit (inside the template)


def build_heights():
    prot_a, prot_o = protected_columns()
    dist_a = dist_field(prot_a, 80)
    dist_o = dist_field(prot_o, 80)
    H = {}
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            base = GT                                   # level: steps on open grass show as stripes
            m = mountain_height(x, z)
            da, do = dist_a.get((x, z), 81), dist_o.get((x, z), 81)
            dd = min(da, do)
            # the foot of the mountains wanders (0..FOOT_WANDER further out), so it never shows the zones' lines
            wa = FOOT_WANDER * vnoise(x, z, 31.0, 53)
            cap = min(GT + max(0, da - 2 - wa * 0.2) * ARENA_WALL_SLOPE, GT + max(0, do - 2 - wa) * OPEN_SLOPE) +                 (vnoise(x, z, 9.0, 51) - 0.5) * 4
            top = max(base, min(MOUNT_TOP, int(round(min(m, cap))))) if dd > 0 else base
            lx, lz = arena_local(x, z)
            if in_arena_rect(lx, lz, 3):
                top = GT
            if CASTLE_FOOT[0] - 8 <= x <= CASTLE_FOOT[1] + 8 and CASTLE_FOOT[2] - 8 <= z <= CASTLE_FOOT[3] + 8:
                top = GT
            if (x, z) in STAND_COLS:
                top = GT
            # the ravine
            wt = arena_wall_top(lx, lz)
            if wt is not None:
                top = wt
            # the gorge
            gt = gorge_top(x, z)
            if gt is not None and gt < top and not in_arena_rect(lx, lz, 6):
                top = gt
            H[(x, z)] = top
    corner_crags(H)
    return H


CRAG_COLS = set()


def corner_crags(H):
    """Rock crags in the four corners between the stands' ends: a few steep pinnacles hugging each corner, about as
    high as the stand tops (low in the north-east, so the castle shows from the arena and her climb out is clear)."""
    for sx in (-1, 1):
        for sz in (-1, 1):
            ne = sx > 0 and sz < 0
            peaks = []
            for i in range(6):
                u, v = _hash(i, sx * 3 + sz, 61), _hash(i, sx * 5 - sz, 62)
                px = sx * (36 + u * 14)
                pz = sz * (28 + v * 14)
                ph = (4 + 4 * _hash(i, 9, 63)) if ne else (12 + 10 * _hash(i, 9, 63))
                pr = 4.5 + 4.5 * _hash(i, 10, 64)
                peaks.append((px, pz, ph, pr))
            for lx in range(34 * sx, 68 * sx, sx):
                for lz in range(26 * sz, 60 * sz, sz):
                    x, z = to_scene(lx, lz)
                    if (x, z) in STAND_COLS or abs(x) > RADIUS or abs(z) > RADIUS:
                        continue
                    if arena_s(lx, lz) < RIM_S + 0.02:
                        continue
                    best = 0.0
                    for px, pz, ph, pr in peaks:
                        d = math.hypot(lx - px, lz - pz)
                        if d < pr:
                            best = max(best, ph * (1 - d / pr) ** 0.3)     # steep fins, not mounds
                    best += (vnoise(lx, lz, 2.5, 65) - 0.5) * 2.5
                    if best > 0.8:
                        H[(x, z)] = max(H[(x, z)], GT + int(round(best)))
                        CRAG_COLS.add((x, z))


# ---------------------------------------------------------------- terrain blocks
def rock(x, y, z, seed=0):
    # strata: thin darker bands of tuff that wander a little, so a cliff reads as layered rock
    if (y + int(vnoise(x, z, 21.0, 80) * 5)) % 6 == 0 and rnd(x, y, z, 79) < 0.75:
        return 'minecraft:tuff'
    r = vnoise(x + y * 0.7, z - y * 0.5, 5.0, 81 + seed) * 0.7 + rnd(x, y, z, 82 + seed) * 0.3
    if r < 0.32:
        return 'minecraft:stone'
    if r < 0.55:
        return 'minecraft:andesite'
    if r < 0.75:
        return 'minecraft:tuff'
    return 'minecraft:cobblestone'


def slope_at(H, x, z):
    h = H[(x, z)]
    m = 0
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        n = H.get((x + dx, z + dz))
        if n is not None:
            m = max(m, abs(n - h))
    return m


def top_block(H, x, z):
    h = H[(x, z)]
    s = slope_at(H, x, z)
    lx, lz = arena_local(x, z)
    in_ravine = arena_wall_top(lx, lz) is not None
    if in_ravine and h <= -1:
        # the arena floor
        r = vnoise(x, z, 4.0, 91) * 0.75 + rnd(x, 0, z, 92) * 0.25
        dn = math.hypot(lx - NEST_LC[0], lz - NEST_LC[1])
        if dn < 16 and vnoise(x, z, 3.5, 93) < 0.33 * (1 - dn / 16) + 0.08:
            return 'minecraft:blackstone' if rnd(x, 1, z, 94) < 0.6 else 'minecraft:basalt[axis=y]'
        if r < 0.42:
            return 'minecraft:gravel'
        if r < 0.62:
            return 'minecraft:tuff'
        if r < 0.78:
            return 'minecraft:andesite'
        return 'minecraft:coarse_dirt' if vnoise(x, z, 2.5, 90) < 0.5 else 'minecraft:gravel'
    if (x, z) in CRAG_COLS:
        return rock(x, h, z, 2)
    if in_ravine or (gorge_top(x, z) is not None and h < GT - 1):
        if h <= GORGE_FLOOR + 1 and not in_ravine:
            r = rnd(x, 0, z, 95)
            return 'minecraft:gravel' if r < 0.4 else 'minecraft:mossy_cobblestone' if r < 0.6 else rock(x, h, z, 3)
        if s <= 1 and rnd(x, h, z, 96) < 0.5:
            return 'minecraft:moss_block' if not in_ravine else 'minecraft:gravel'
        return rock(x, h, z)
    if h >= 86 + int(vnoise(x, z, 9.0, 97) * 10):
        return 'minecraft:snow_block'
    if s >= 2:
        return rock(x, h, z)
    n = vnoise(x, z, 9.0, 99)
    if h > GT + 12:
        # the mountains' gentler shoulders: rock with coherent patches of turf and moss
        g = vnoise(x, z, 13.0, 100)
        if g < 0.38:
            return 'minecraft:grass_block[snowy=false]' if n < 0.6 else 'minecraft:moss_block'
        return rock(x, h, z)
    if n < 0.2:
        return 'minecraft:moss_block'
    return 'minecraft:grass_block[snowy=false]'


def build_ground(w, H):
    for (x, z), h in H.items():
        lo = h
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            n = H.get((x + dx, z + dz))
            if n is not None and n < lo:
                lo = n
        w.set((x, h, z), top_block(H, x, z))
        for y in range(lo + 1, h):
            w.set((x, y, z), rock(x, y, z))
        if bid(w.get((x, h, z))) == 'minecraft:gravel' and h - 1 < lo + 1:
            w.set((x, h - 1, z), 'minecraft:stone')         # gravel never over air


def build_stream(w, H):
    """Still water along the gorge floor's middle, one deep, on stone, rock either side."""
    for z in range(-138, 17):
        cx = gorge_cx(z)
        for x in range(int(math.floor(cx - 1.4)), int(math.floor(cx + 1.4)) + 1):
            if H.get((x, z)) != GORGE_FLOOR:
                continue
            ok = all(H.get((x + dx, z + dz)) == GORGE_FLOOR for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if not ok:
                continue
            w.set((x, GORGE_FLOOR, z), 'minecraft:water[level=0]')
            w.set((x, GORGE_FLOOR - 1, z), 'minecraft:stone')


# ---------------------------------------------------------------- the arena's pieces
NEST_LC = (6.5, 0.5)                    # arena-local centre of the nest
SHELF = (8, 11, -2, 2)                  # lx0, lx1, lz0, lz1 of the perch shelf (top y 2.0)
BOWL_C = (4.5, 0.5)
EGG_CELLS = [(3, -1), (4, 1), (5, -1), (3, 1), (5, 2)]
GOLD_CELL = (4, -1)
HIDE = (-18, -15, 5, 9)                 # the hiding rock's cells lx0, lx1, lz0, lz1 (flat face at lx -15's east)
STAKE_LC = (18, 5)


def lset(w, lx, y, lz, s):
    x, z = to_scene(lx, lz)
    w.set((x, y, z), s)


def build_nest(w):
    for lx in range(-2, 16):  # noqa
        for lz in range(-9, 10):
            d = math.hypot(lx + 0.5 - NEST_LC[0], (lz + 0.5 - NEST_LC[1]) * 1.15)
            if d > 8.2:
                continue
            on_shelf = SHELF[0] <= lx <= SHELF[1] and SHELF[2] <= lz <= SHELF[3]
            db = math.hypot(lx + 0.5 - BOWL_C[0], lz + 0.5 - BOWL_C[1])
            if on_shelf:
                top = 1                                   # cells y 0..1: the shelf top at 2.0
            elif db < 2.9:
                top = 0                                   # the bowl's floor at 1.0
            elif d < 6.2:
                top = 1 + int(vnoise(lx, lz, 2.2, 101) * 2.6)
            else:
                top = int(vnoise(lx, lz, 2.0, 103) * 2.2) - 1
            for y in range(0, top + 1):
                r = rnd(lx, y, lz, 104)
                s = ('minecraft:stone' if r < 0.35 else 'minecraft:andesite' if r < 0.6 else
                     'minecraft:cobblestone' if r < 0.8 else 'minecraft:mossy_cobblestone')
                if on_shelf and y == top:
                    s = 'minecraft:smooth_stone' if r < 0.5 else 'minecraft:polished_andesite'
                if db < 2.9 and not on_shelf:
                    s = 'minecraft:coarse_dirt' if r < 0.6 else 'minecraft:stone'
                lset(w, lx, y, lz, s)
    # her clutch on the bowl's floor, with the golden egg's gap
    for (lx, lz) in EGG_CELLS:
        lset(w, lx, 1, lz, 'minecraft:dragon_egg')
    lset(w, GOLD_CELL[0], 1, GOLD_CELL[1], None)
    # straw-like dry grass and bones round the rim
    for lx, lz in ((1, 4), (9, -5), (11, 4), (0, -3)):
        x, z = to_scene(lx, lz)
        top = max([y for y in range(-1, 4) if w.get((x, y, z))], default=-1)
        w.set((x, top + 1, z), 'minecraft:dead_bush')
        if bid(w.get((x, top + 1, z))) == 'minecraft:dead_bush':
            w.set((x, top, z), 'minecraft:coarse_dirt')


HIDE_C = (-15.6, 0.4, 7.5)               # the boulder: an ellipsoid (centre, radii) cut flat on its nest side
HIDE_R = (2.8, 3.4, 3.2)


def build_hide_rock(w):
    """The hiding rock: a weathered boulder about 3.5 tall and 6 across, its east side split off flat (the face
    toward the nest), mossy on its back."""
    cx, cy, cz = HIDE_C
    rx, ry, rz = HIDE_R
    for lx in range(int(cx - rx) - 1, HIDE[1] + 1):
        for lz in range(int(cz - rz) - 1, int(cz + rz) + 2):
            for y in range(0, int(cy + ry) + 1):
                d = (((lx + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 + ((lz + 0.5 - cz) / rz) ** 2)
                d += (vnoise(lx * 1.7 + y, lz * 1.7 - y, 1.6, 113) - 0.5) * 0.35
                if d > 1.0:
                    continue
                r = rnd(lx, y, lz, 111)
                if lx == HIDE[1]:
                    s_ = 'minecraft:stone' if (lz + y) % 3 else 'minecraft:smooth_stone'
                elif lx <= HIDE[0] - 0 and y >= 1 and r < 0.35:
                    s_ = 'minecraft:mossy_cobblestone'
                else:
                    s_ = 'minecraft:stone' if r < 0.45 else 'minecraft:andesite' if r < 0.8 else 'minecraft:cobblestone'
                lset(w, lx, y, lz, s_)
    # the flat face toward the nest is whole: 5 wide, 3 tall
    for lz in range(HIDE[2], HIDE[3] + 1):
        for y in range(0, 3):
            lset(w, HIDE[1], y, lz, 'minecraft:stone' if (lz + y) % 3 else 'minecraft:smooth_stone')
            lset(w, HIDE[1] - 1, y, lz, 'minecraft:stone')


def build_stake(w):
    lx, lz = STAKE_LC
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                lset(w, lx + dx, 0, lz + dz, 'minecraft:cobblestone_slab[type=bottom,waterlogged=false]')
    lset(w, lx, 0, lz, 'minecraft:stripped_dark_oak_log[axis=y]')
    lset(w, lx, 1, lz, 'minecraft:stripped_dark_oak_log[axis=y]')
    lset(w, lx, 2, lz, 'minecraft:chain[axis=y,waterlogged=false]')


def build_boulders(w, H):
    """Loose boulders and scree on the floor, kept off the nest, the hiding rock, the stake and the run to it."""
    for i in range(46):
        lx = int(-29 + _hash(i, 1, 121) * 58)
        lz = int(-21 + _hash(i, 2, 121) * 42)
        if math.hypot(lx - NEST_LC[0], lz - NEST_LC[1]) < 10 or abs(lx - STAKE_LC[0]) + abs(lz - STAKE_LC[1]) < 4:
            continue
        if HIDE[0] - 4 <= lx <= HIDE[1] + 3 and HIDE[2] - 3 <= lz <= HIDE[3] + 3:
            continue
        if lx < -14 and -6 <= lz <= 12:
            continue                                     # the run from the entrance to the rock
        x, z = to_scene(lx, lz)
        if H[(x, z)] != -1:
            continue
        size = 1 + int(_hash(i, 3, 121) * 2.4)
        for dx in range(-size + 1, size):
            for dz in range(-size + 1, size):
                if abs(dx) + abs(dz) > size:
                    continue
                hh = size - abs(dx) - abs(dz) + (1 if _hash(i, dx * 7 + dz, 122) < 0.4 else 0) - 1
                for y in range(0, max(0, hh)):
                    r = rnd(lx + dx, y, lz + dz, 123)
                    lset(w, lx + dx, y, lz + dz, 'minecraft:andesite' if r < 0.5 else 'minecraft:stone' if r < 0.8
                         else 'minecraft:tuff')


MOUTH = -int(FLOOR_A) + 2                # the tunnel mouth's frame column (arena-local x)
TUNNEL_END = MOUTH - 23


def build_entrance(w):
    """The champions' tunnel through the west cliff: 5 wide, 5 high, timber-lined, closed in the dark at its end."""
    zs = range(-2, 3)
    for lx in range(TUNNEL_END + 1, MOUTH + 2):
        for lz in range(-3, 4):
            for y in range(0, 6):
                lset(w, lx, y, lz, None)
    for lx in range(TUNNEL_END + 1, MOUTH + 1):
        for lz in zs:
            lset(w, lx, -1, lz, 'minecraft:coarse_dirt' if (lx + lz) % 3 else 'minecraft:dirt_path')
        for y in range(0, 6):
            lset(w, lx, y, -3, 'minecraft:spruce_planks' if lx % 4 else 'minecraft:spruce_log[axis=y]')
            lset(w, lx, y, 3, 'minecraft:spruce_planks' if lx % 4 else 'minecraft:spruce_log[axis=y]')
        for lz in range(-3, 4):
            lset(w, lx, 5, lz, 'minecraft:spruce_log[axis=z]' if lx % 4 == 0 else 'minecraft:dark_oak_planks')
    for lz in range(-3, 4):
        for y in range(0, 6):
            lset(w, TUNNEL_END, y, lz, 'minecraft:dark_oak_planks')
    # the mouth: a heavy timber frame in the cliff, torches either side
    for y in range(0, 6):
        lset(w, MOUTH, y, -3, 'minecraft:spruce_log[axis=y]')
        lset(w, MOUTH, y, 3, 'minecraft:spruce_log[axis=y]')
    for lz in range(-3, 4):
        lset(w, MOUTH, 5, lz, 'minecraft:spruce_log[axis=z]')
        lset(w, MOUTH, 6, lz, rock(lz, 6, MOUTH))
    for lz in (-4, 4):
        for y in range(0, 7):
            lset(w, MOUTH, y, lz, rock(lz, y, MOUTH, 1))
    lset(w, MOUTH + 1, 3, -4, 'minecraft:wall_torch[facing=east]')
    lset(w, MOUTH + 1, 3, 4, 'minecraft:wall_torch[facing=east]')


# ---------------------------------------------------------------- stands
def stand_lc(sd, a, d):
    return (a, d) if sd[1] == 'x' else (d, a)


def sset(w, sd, a, d, y, s):
    lx, lz = stand_lc(sd, a, d)
    lset(w, lx, y, lz, s)


def out_facing(sd):
    """The blockstate facing that points outward (away from the arena) for a stand."""
    if sd[1] == 'x':
        return 'north' if sd[2] < 0 else 'south'
    return 'west' if sd[2] < 0 else 'east'


def in_facing(sd):
    return {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}[out_facing(sd)]


def build_stand(w, sd):
    name = sd[0]
    a0, a1 = sd[4]
    aisles = AISLES[name]
    out_f = out_facing(sd)
    sgn = sd[2]
    back = stand_back(sd)
    top_y = row_y(NROWS - 1) + 4
    for a in range(a0, a1 + 1):
        box = None
        for k in range(NROWS):
            inner, outer = row_cells(sd, k)
            fy = row_y(k)
            box = in_box(sd, a, k)
            prev = row_y(k - 1) if k else GT
            for d in (inner, outer):
                sset(w, sd, a, d, fy, 'minecraft:spruce_planks' if (a + k) % 7 else 'minecraft:dark_oak_planks')
                for y in range(prev + 1, fy):
                    sset(w, sd, a, d, y, 'minecraft:spruce_planks')      # the riser under a 2-high step
                for y in range(fy + 1, fy + 4):
                    sset(w, sd, a, d, y, None)
            if box:
                continue
            if a in aisles:
                sset(w, sd, a, outer, fy + 1, state('spruce_stairs', facing=out_f, half='bottom', shape='straight',
                                                    waterlogged='false'))
            else:
                sset(w, sd, a, outer, fy + 1, state('spruce_slab', type='bottom', waterlogged='false'))
        # the fascia of school colours along the front, on the rim
        c = FASCIA[name][((a - a0) // 3) % 2]
        sset(w, sd, a, sd[3] - sgn, G, 'minecraft:' + c)
        # the back wall: posts every 4, beams, boards
        for y in range(G, top_y + 1):
            post = (a - a0) % 4 == 0
            if post:
                sset(w, sd, a, back, y, 'minecraft:spruce_log[axis=y]')
            elif y in (G + 5, G + 11, top_y):
                sset(w, sd, a, back, y, 'minecraft:stripped_spruce_log[axis=%s]' % sd[1])
            else:
                sset(w, sd, a, back, y, 'minecraft:spruce_planks' if (a + y) % 5 else 'minecraft:dark_oak_planks')
        # flags along the crest
        if (a - a0) % 8 == 4:
            for y in range(top_y + 1, top_y + 5):
                sset(w, sd, a, back, y, 'minecraft:spruce_fence')
            col = FLAGS[name][((a - a0) // 8) % len(FLAGS[name])]
            sset(w, sd, a, back, top_y + 5, state(col + '_banner', rotation=int(round(sd[5] / 22.5)) % 16))
        # house banners hanging on the back wall's outside, under the crest
        if (a - a0) % 4 == 2:
            col = FLAGS[name][((a - a0) // 4) % len(FLAGS[name])]
            sset(w, sd, a, back + sgn, top_y - 1, state(col + '_wall_banner', facing=out_f))
    # the side walls at both ends: boarded under the deck, a rail above it
    for a in (a0 - 1, a1 + 1):
        for k in range(NROWS):
            for d in row_cells(sd, k):
                for y in range(G, row_y(k) + 3):
                    sset(w, sd, a, d, y, 'minecraft:spruce_planks' if y <= row_y(k) + 1 else 'minecraft:spruce_fence')
        for y in range(G, top_y + 1):
            sset(w, sd, a, back, y, 'minecraft:spruce_log[axis=y]')


def build_booth(w):
    sd = [s for s in STAND_DEFS if s[0] == BOOTH[0]][0]
    (a0, a1), k0 = BOOTH[1], BOOTH[2]
    fy = row_y(k0)
    d_front, _ = row_cells(sd, k0)
    back = stand_back(sd)
    ds = list(range(min(d_front, back - sd[2]), max(d_front, back - sd[2]) + 1))
    for a in range(a0, a1 + 1):
        for d in ds:
            sset(w, sd, a, d, fy, 'minecraft:dark_oak_planks')
            for y in range(fy + 1, row_y(NROWS - 1) + 4):
                sset(w, sd, a, d, y, None)
            sset(w, sd, a, d, fy + 5, 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
        sset(w, sd, a, d_front, fy + 1, 'minecraft:dark_oak_fence')
        sset(w, sd, a, d_front - sd[2], fy + 5, 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    for a in (a0 - 1, a1 + 1):
        for d in ds:
            for y in range(fy, fy + 6):
                sset(w, sd, a, d, y, 'minecraft:dark_oak_planks' if y < fy + 5 else
                     'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')
    # banners hanging from the booth's front and a lantern inside
    for a, col in ((a0, 'red'), (a1, 'yellow')):
        sset(w, sd, a, d_front - sd[2], fy + 4, state(col + '_wall_banner', facing=in_facing(sd)))
    sset(w, sd, a0 + 2, back - sd[2], fy + 4, 'minecraft:lantern[hanging=true,waterlogged=false]')


def build_cannons(w):
    for name, (a0, a1), k0 in CANNONS:
        sd = [s for s in STAND_DEFS if s[0] == name][0]
        fy = row_y(k0)
        d_front, _ = row_cells(sd, k0)
        back = stand_back(sd)
        ds = list(range(min(d_front, back - sd[2]), max(d_front, back - sd[2]) + 1))
        for a in range(a0, a1 + 1):
            for d in ds:
                sset(w, sd, a, d, fy, 'minecraft:stone_bricks' if (a + d) % 3 else 'minecraft:cracked_stone_bricks')
                for y in range(fy + 1, row_y(NROWS - 1) + 4):
                    sset(w, sd, a, d, y, None)
            sset(w, sd, a, d_front, fy + 1, 'minecraft:stone_brick_wall')
        ac = (a0 + a1) // 2
        # the cannon: a carriage and a barrel along the depth axis, pointing into the arena
        d1, d2 = d_front + sd[2], d_front + 2 * sd[2]
        axis = 'z' if sd[1] == 'x' else 'x'
        sset(w, sd, ac, d_front, fy + 1, 'minecraft:polished_basalt[axis=%s]' % axis)   # through the embrasure
        sset(w, sd, ac, d1, fy + 1, 'minecraft:polished_basalt[axis=%s]' % axis)
        sset(w, sd, ac, d2, fy + 1, 'minecraft:polished_blackstone_wall')
        for da in (-1, 1):
            sset(w, sd, ac + da, d1, fy + 1, state('dark_oak_trapdoor', facing='east' if da > 0 else 'west',
                                                   half='bottom', open='true', powered='false', waterlogged='false'))
        sset(w, sd, ac + 1, d2 + 2 * sd[2], fy + 1, 'minecraft:barrel[facing=up,open=false]')
        sset(w, sd, ac - 1, d2 + 2 * sd[2], fy + 1, 'minecraft:barrel[facing=up,open=false]')


def build_stands(w):
    for sd in STAND_DEFS:
        build_stand(w, sd)
    build_booth(w)
    build_cannons(w)


# ---------------------------------------------------------------- the viaduct
DECK_Y = GT                             # deck blocks at y 15, walked at 16
CENTRE_ARCH = 10                        # half span of the central arch (20 wide)
SPRING_C = -2
PIER_W = 3
SIDE_SPAN = 7


def bridge_layout():
    cx = int(round(gorge_cx(-20)))
    c0, c1 = cx - CENTRE_ARCH, cx + CENTRE_ARCH - 1         # central opening cells
    p_w = (c0 - PIER_W, c0 - 1)
    p_e = (c1 + 1, c1 + PIER_W)
    s_w = (p_w[0] - SIDE_SPAN, p_w[0] - 1)
    s_e = (p_e[1] + 1, p_e[1] + SIDE_SPAN)
    return {'cx': cx, 'centre': (c0, c1), 'piers': (p_w, p_e), 'sides': (s_w, s_e),
            'deck': (s_w[0] - 6, s_e[1] + 6)}


BR = bridge_layout()


def arch_underside(x, x0, x1, spring):
    """Underside y of a pointed (two-centred) arch over cells x0..x1 springing at `spring`: each side is an arc of
    radius 0.75 x the span, centred on the far springing line, so the crown is about 0.56 x the span up."""
    span = float(x1 - x0 + 1)
    r = 0.75 * span
    xm = x + 0.5
    if xm <= x0 or xm >= x1 + 1:
        return spring
    if xm < x0 + span / 2.0:
        c = x0 + r                                    # the left side's arc, centred right of the opening
    else:
        c = x1 + 1 - r
    dx = xm - c
    return spring + math.sqrt(max(0.0, r * r - dx * dx))


def arch_crown(x0, x1, spring):
    span = float(x1 - x0 + 1)
    r = 0.75 * span
    return spring + math.sqrt(r * r - (r - span / 2.0) ** 2)


def masonry(x, y, z):
    r = rnd(x, y, z, 131)
    if r < 0.6:
        return 'minecraft:stone_bricks'
    if r < 0.8:
        return 'minecraft:cracked_stone_bricks'
    if r < 0.93:
        return 'minecraft:mossy_stone_bricks'
    return 'minecraft:stone'


def build_viaduct(w, H):
    z0, z1 = BRIDGE_Z
    dx0, dx1 = BR['deck']
    c0, c1 = BR['centre']
    spans = [(c0, c1, SPRING_C)] + [(s[0], s[1], 7) for s in BR['sides']]
    for x in range(dx0, dx1 + 1):
        # where the masonry stops: under an arch, its underside; elsewhere, the ground
        under = None
        for (a0, a1, spring) in spans:
            if a0 <= x <= a1:
                under = int(math.floor(arch_underside(x, a0, a1, spring)))
        for z in range(z0, z1 + 1):
            gtop = min(H.get((x, z), GT), GT)
            if under is None:
                lo = gtop + 1
            else:
                lo = max(under, gtop + 1)
            for y in range(lo, DECK_Y + 1):
                w.set((x, y, z), masonry(x, y, z))
            # the arch ring and the keystone: stone bricks with a chiselled crown
            if under is not None and under >= gtop + 1:
                w.set((x, under, z), 'minecraft:stone_bricks')
            w.set((x, DECK_Y, z), 'minecraft:stone_bricks' if z == (z0 + z1) // 2 else 'minecraft:polished_andesite')
        for z in (z0, z1):
            w.set((x, DECK_Y + 1, z), 'minecraft:stone_brick_wall')
        for z in range(z0, z1 + 1):
            if under is not None:
                for y in range(min(H.get((x, z), GT), GT) + 1, under):
                    w.set((x, y, z), None)
    # cutwaters on the piers, and a string course
    for (p0, p1) in BR['piers']:
        for x in range(p0, p1 + 1):
            for z in (z0 - 1, z1 + 1):
                gtop = H.get((x, z), GT)
                for y in range(gtop + 1, SPRING_C + 2):
                    w.set((x, y, z), masonry(x, y, z))
        for x in range(p0 - 1, p1 + 2):
            for z in range(z0 - 1, z1 + 2):
                w.set((x, SPRING_C + 2, z), 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]'
                      if z in (z0 - 1, z1 + 1) or x in (p0 - 1, p1 + 1) else w.get((x, SPRING_C + 2, z)))
    # lamps on the parapet
    for x in range(dx0 + 2, dx1, 8):
        for z in (z0, z1):
            w.set((x, DECK_Y + 2, z), 'minecraft:lantern[hanging=false,waterlogged=false]')
    # the deck continues as a path to the castle and to the tower
    for x in range(dx1 + 1, CASTLE_FOOT[0] + 4):
        for z in range(z0, z1 + 1):
            h = H.get((x, z))
            if h is not None and h == GT and (x, z) not in CASTLE_COLS:
                w.set((x, h, z), 'minecraft:dirt_path' if (x + z) % 4 else 'minecraft:coarse_dirt')


def build_tower(w, H):
    """A round watch tower on the west rim at the viaduct's west end."""
    tx, tz, r = BR['deck'][0] - 3, (BRIDGE_Z[0] + BRIDGE_Z[1]) / 2.0 + 0.5, 4.3
    top = G + 18
    for x in range(int(tx - r - 2), int(tx + r + 3)):
        for z in range(int(tz - r - 2), int(tz + r + 3)):
            d = math.hypot(x + 0.5 - tx, z + 0.5 - tz)
            if d > r + 1.2:
                continue
            gtop = H.get((x, z), GT)
            if d <= r:
                shell = d > r - 1.2
                for y in range(min(gtop, GT) + 1 if shell else GT, top + 1):
                    if shell or y == GT:
                        w.set((x, y, z), masonry(x, y, z))
                    else:
                        w.set((x, y, z), None)
                # the parapet and the cone roof
                if shell:
                    if (x + z) % 2 == 0:
                        w.set((x, top + 1, z), masonry(x, top + 1, z))
            # the roof
    for y in range(top + 1, top + 12):
        rr = (r - 0.3) * (1 - (y - top - 1) / 11.0)
        for x in range(int(tx - r - 1), int(tx + r + 2)):
            for z in range(int(tz - r - 1), int(tz + r + 2)):
                d = math.hypot(x + 0.5 - tx, z + 0.5 - tz)
                if rr - 1.0 < d <= rr or (d <= 0.8 and y == top + 11):
                    w.set((x, y, z), 'minecraft:oak_planks')
    # the door onto the deck, a slit window
    for y in (G, G + 1, G + 2):
        for z in range(BRIDGE_Z[0], BRIDGE_Z[1] + 1):
            w.set((BR['deck'][0] - 1 + 0, y, z), None)
            w.set((int(tx + r) - 0, y, z), None)
    for y in (G + 8, G + 9, G + 13, G + 14):
        w.set((int(tx), y, int(tz - r)), None)


# ---------------------------------------------------------------- the castle's foot
def support_castle(w, H):
    for (x, y, z), b in CASTLE.items():
        if is_gravity(b) and (x, y - 1, z) not in CASTLE:
            w.set((x, y - 1, z), 'minecraft:stone')
    for (x, z), y0 in CASTLE_COLS.items():
        g = H.get((x, z), GT)
        if y0 - g > 10:
            continue
        for y in range(g + 1, y0):
            if (x, y, z) not in CASTLE:
                w.set((x, y, z), rock(x, y, z, 5))


# ---------------------------------------------------------------- dressing
def spruce(w, x, y0, z, height, seed):
    for y in range(y0, y0 + height):
        w.set((x, y, z), 'minecraft:spruce_log[axis=y]')
    top = y0 + height
    for y in range(y0 + 2, top + 2):
        r = max(0, int((top + 1 - y) * 0.42 + 0.5))
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) > r + (1 if r > 1 else 0):
                    continue
                if (dx, dz) == (0, 0) and y < top:
                    continue
                p = (x + dx, y, z + dz)
                if abs(p[0]) > RADIUS or abs(p[2]) > RADIUS:
                    continue
                if w.get(p) is None:
                    w.set(p, 'minecraft:spruce_leaves[distance=1,persistent=true,waterlogged=false]')


def keep_clear(x, z):
    """Columns no tree or boulder may stand on: the arena works, the castle, the bridge, the gorge."""
    lx, lz = arena_local(x, z)
    if in_arena_rect(lx, lz, 6):
        return True
    if CASTLE_FOOT[0] - 4 <= x <= CASTLE_FOOT[1] + 4 and CASTLE_FOOT[2] - 4 <= z <= CASTLE_FOOT[3] + 4:
        return True
    if gorge_top(x, z) is not None and abs(x - gorge_cx(z)) < 26:
        return True
    return False


def dress(w, H):
    # sparse spruces in clumps on the plateau and the mountains' lower slopes
    n = 0
    for i in range(900):
        x = int(-195 + _hash(i, 1, 141) * 390)
        z = int(-195 + _hash(i, 2, 141) * 390)
        h = H[(x, z)]
        if keep_clear(x, z) or h > GT + 30 or slope_at(H, x, z) > 1:
            continue
        if vnoise(x, z, 30.0, 143) < 0.58:
            continue
        if bid(w.get((x, h, z))) not in ('minecraft:grass_block', 'minecraft:moss_block', 'minecraft:podzol',
                                         'minecraft:coarse_dirt'):
            continue
        spruce(w, x, h + 1, z, 5 + int(_hash(i, 3, 141) * 7), i)
        n += 1
        if n >= 110:
            break
    # boulders, heather and grass on the plateau
    for (x, z), h in H.items():
        if keep_clear(x, z) or h > GT + 40:
            continue
        b = bid(w.get((x, h, z)))
        if w.get((x, h + 1, z)) is not None:
            continue
        r = rnd(x, h, z, 151)
        if b in ('minecraft:grass_block', 'minecraft:moss_block', 'minecraft:podzol'):
            if r < 0.004:
                w.set((x, h + 1, z), rock(x, h + 1, z))
                if rnd(x, h, z, 152) < 0.5:
                    w.set((x + 1, h + 1, z), rock(x + 1, h + 1, z))
            elif r < 0.03 and vnoise(x, z, 14.0, 153) > 0.6:
                w.set((x, h + 1, z), 'minecraft:allium')
            elif r < 0.07:
                w.set((x, h + 1, z), 'minecraft:short_grass')


# ---------------------------------------------------------------- build
def build(world=None):
    """Fills `world` (a dict scene cell -> block state; a new one when None) with the template and returns ANCHORS."""
    w = World()
    if world is not None:
        w.g = world
    _build(w)
    return ANCHORS


def _build(w):
    H = build_heights()
    build_ground(w, H)
    build_stream(w, H)
    build_nest(w)
    build_hide_rock(w)
    build_stake(w)
    build_boulders(w, H)
    build_entrance(w)
    build_stands(w)
    build_viaduct(w, H)
    build_tower(w, H)
    support_castle(w, H)
    dress(w, H)
    connect_fences(w)
    w.H = H
    ANCHORS['bridge']['cells'] = [list(p) for p in sorted(box_cells(ANCHORS['bridge']['break_box'])) if p in w.g]
    return w


FENCES = ('minecraft:spruce_fence', 'minecraft:dark_oak_fence', 'minecraft:oak_fence')
WALLS = ('minecraft:stone_brick_wall', 'minecraft:cobblestone_wall', 'minecraft:polished_blackstone_wall')


def connect_fences(w):
    for p, s in list(w.g.items()):
        name = bid(s)
        x, y, z = p
        if name in FENCES:
            props = {}
            for side, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('east', (1, 0)), ('west', (-1, 0))):
                n = bid(w.get((x + dx, y, z + dz)))
                props[side] = 'true' if (n in FENCES or (n is not None and n.endswith(('_planks', '_log')))) \
                    else 'false'
            w.g[p] = state(name, waterlogged='false', **props)
        elif name in WALLS:
            props = {}
            for side, (dx, dz) in (('north', (0, -1)), ('south', (0, 1)), ('east', (1, 0)), ('west', (-1, 0))):
                n = bid(w.get((x + dx, y, z + dz)))
                props[side] = 'low' if (n in WALLS or (n is not None and n.endswith(('_bricks', 'stone')))) \
                    else 'none'
            line = (props['north'] != 'none') == (props['south'] != 'none') and \
                (props['east'] != 'none') == (props['west'] != 'none') and \
                ((props['north'] != 'none') != (props['east'] != 'none'))
            w.g[p] = state(name, up='false' if line else 'true', waterlogged='false', **props)


def world_with_castle(g):
    out = {p: bid(s) for p, s in g.items()}
    out.update(CASTLE)
    return out


# ---------------------------------------------------------------- anchors
def _p(x, y, z):
    return [round(x, 4), round(y, 4), round(z, 4)]


def la(lx, y, lz):
    """Arena-local point to a scene point."""
    return _p(lx + ARENA_C[0], y, lz + ARENA_C[1])


def stands_anchor():
    out = []
    for sd in STAND_DEFS:
        a0, a1 = sd[4]
        rows = []
        for k in range(NROWS):
            _, outer = row_cells(sd, k)
            seat_top = row_y(k) + 1.5
            run = []
            for a in range(a0, a1 + 2):
                ok = a <= a1 and a not in AISLES[sd[0]] and not in_box(sd, a, k)
                if ok:
                    run.append(a)
                    continue
                if run:
                    p0 = stand_lc(sd, run[0], outer)
                    p1 = stand_lc(sd, run[-1], outer)
                    q0 = la(p0[0] + 0.5, seat_top, p0[1] + 0.5)
                    q1 = la(p1[0] + 0.5, seat_top, p1[1] + 0.5)
                    rows.append(q0 + q1)
                    run = []
        out.append({'name': sd[0], 'school': sd[6], 'facing_yaw': sd[5], 'rows': rows})
    return out


def cannons_anchor():
    out = []
    for name, (a0, a1), k0 in CANNONS:
        sd = [s for s in STAND_DEFS if s[0] == name][0]
        ac = (a0 + a1) // 2
        d_front, _ = row_cells(sd, k0)
        fy = row_y(k0)
        lx, lz = stand_lc(sd, ac, d_front)
        # the muzzle: the barrel's arena-side face
        if sd[1] == 'x':
            mz = lz + (0.0 if sd[2] > 0 else 1.0)
            muzzle = la(lx + 0.5, fy + 1.5, mz)
            dirv = [0.0, 0.0, -float(sd[2])]
        else:
            mx = lx + (0.0 if sd[2] > 0 else 1.0)
            muzzle = la(mx, fy + 1.5, lz + 0.5)
            dirv = [-float(sd[2]), 0.0, 0.0]
        smoke = [round(muzzle[i] + dirv[i] * 1.5, 4) for i in range(3)]
        out.append({'muzzle': muzzle, 'dir': dirv, 'smoke': smoke})
    return out


def booth_anchor():
    sd = [s for s in STAND_DEFS if s[0] == BOOTH[0]][0]
    (a0, a1), k0 = BOOTH[1], BOOTH[2]
    d_front, _ = row_cells(sd, k0)
    lx, lz = stand_lc(sd, (a0 + a1 + 1) / 2.0, d_front + sd[2] * 1)
    return {'stand': la(lx, row_y(k0) + 1, lz + 0.5), 'yaw': sd[5], 'look': la(NEST_LC[0], 2.0, NEST_LC[1])}


def bridge_anchor():
    z0, z1 = BRIDGE_Z
    c0, c1 = BR['centre']
    crown = int(math.floor(arch_crown(c0, c1, SPRING_C)))
    bx = (c0, c1 + 1)
    return {
        'span_box': [[BR['deck'][0], SPRING_C, z0], [BR['deck'][1] + 1, DECK_Y + 2, z1 + 1]],
        'break_box': [[bx[0], crown - 2, z0], [bx[1], DECK_Y + 3, z1 + 1]],
        'underside_y': float(crown),
        'deck_y': float(DECK_Y + 1),
        'arch': {'x1': c0, 'x2': c1 + 1, 'spring_y': SPRING_C, 'crown_y': crown},
        'fall_column': [[BR['cx'] - 4, GORGE_FLOOR + 1, z0 - 3], [BR['cx'] + 5, crown - 3, z1 + 4]],
    }


NEST_SC = la(NEST_LC[0], 0.0, NEST_LC[1])
ANCHORS = {
    'ground_y': 0.0,
    'plateau_y': float(G),
    'nest_centre': NEST_SC,
    'nest_perch': {'pos': la(10.0, 2.0, 0.5), 'yaw': 90.0,
                   'shelf': [la(SHELF[0], 2.0, SHELF[2]), la(SHELF[1] + 1, 2.0, SHELF[3] + 1)]},
    'egg_spot': la(GOLD_CELL[0] + 0.5, 1.4, GOLD_CELL[1] + 0.5),
    'eggs': [la(lx + 0.5, 1.5, lz + 0.5) for (lx, lz) in EGG_CELLS],
    'entrance': {'pos': la(MOUTH + 1.5, 0.0, 0.5), 'yaw': -90.0, 'inside': la(TUNNEL_END + 6.5, 0.0, 0.5)},
    'hide_rock': {'spot': la(HIDE[0] - 2.2, 0.0, 7.5), 'face_centre': la(HIDE[1] + 1.0, 1.5, 7.5),
                  'face_normal': [1.0, 0.0, 0.0],
                  'face_box': [la(HIDE[1] + 1.0, 0.0, HIDE[2]), la(HIDE[1] + 1.0, 3.0, HIDE[3] + 1)],
                  'top_y': 4.0},
    'stake': la(STAKE_LC[0] + 0.5, 2.6, STAKE_LC[1] + 0.5),
    'cannon_spots': cannons_anchor(),
    'stands': stands_anchor(),
    'commentator_box': booth_anchor(),
    'broom_lane': [la(120, 64, -150), la(70, 58, -100), la(20, 50, -64), la(2, 40, -42), la(-8, 22, -18),
                   la(-14, 8, 2), la(-20.0, 2.2, 6.8)],
    'arena_air_box': [la(-22, 6, -14), la(22, 33, 14)],
    'launch_lane': [la(9, 6, 0.5), la(16, 16, -6), la(30, 32, -22), la(52, 44, -46), la(84, 50, -78)],
    'castle_route': [],                 # filled in by plan_routes() below
    'gorge_lane': [],
    'slate_roofs': [],
    'bridge': bridge_anchor(),
    'castle_view': {'pos': la(48, 40, -44), 'look': _p(76, 60, -28)},
    'arena_view': {'pos': la(-48, 66, 70), 'look': la(10, 0, -10)},
    'gorge_view': {'pos': _p(BR['cx'] + 0.5, 2.0, -48.0), 'look': _p(BR['cx'] + 0.5, 4.0, -20.0)},
    'castle_box': [list(CASTLE_AT), [CASTLE_FOOT[1] + 1, CASTLE_TOP_Y + 1, CASTLE_FOOT[3] + 1]],
    'RADIUS': RADIUS,
}


# ---------------------------------------------------------------- routes and the roofs the tail smashes
CASTLE_ROUTE = [[14, 46, 8], [32, 58, 8], [46, 82, 10], [60, 99, 2], [78, 90, -10], [86, 72, -34], [102, 62, -40],
                [118, 58, -50], [116, 54, -68], [110, 52, -86], [94, 56, -104], [62, 50, -110], [30, 44, -100], [10, 36, -92]]
GORGE_LANE_Z = [-92, -78, -62, -46, -32, -20, -8, 4, 16, 26]
GORGE_LANE_Y = [30, 14, 2, -4, -5, -5, -4, 0, 8, 20]


def gorge_lane():
    pts = [[round(gorge_cx(z) + 0.5, 3), float(y), float(z)] for z, y in zip(GORGE_LANE_Z, GORGE_LANE_Y)]
    pts.append([-30.0, 34.0, 44.0])                         # out over the head, back toward the arena
    return pts


ROOF_IDS = ('minecraft:oak_planks', 'minecraft:oak_stairs', 'minecraft:oak_slab', 'minecraft:spruce_planks',
            'minecraft:spruce_stairs')
SLATE_PATCHES = [(55, 12), (76, -8), (98, -51)]  # the great hall gable at its south-west and north-east ends, and
                                                # the north range: the roof patches nearest the castle route


def find_slate_roofs(route):
    """The roof patches her tail smashes as she passes over: 3 x 3 open roof cells (castle roof blocks with air
    above) round each SLATE_PATCHES centre, with the roof normal (from the patch's slope), the patch centre and
    its distance from the castle route (her path dips to them inside the slate_hits windows)."""
    tops = {}
    for (x, y, z), b in CASTLE.items():
        if b in ROOF_IDS and (x, y + 1, z) not in CASTLE and y > tops.get((x, z), (-99, None))[0]:
            tops[(x, z)] = (y, b)
    pts = samples(route, 1.0)
    out = []
    for (bx, bz) in SLATE_PATCHES:
        cells = [[bx + i, tops[(bx + i, bz + j)][0], bz + j] for i in (-1, 0, 1) for j in (-1, 0, 1)
                 if (bx + i, bz + j) in tops]
        ys = {(c[0], c[2]): c[1] for c in cells}
        y0 = ys[(bx, bz)]
        gx = (ys.get((bx + 1, bz), y0) - ys.get((bx - 1, bz), y0)) / 2.0
        gz = (ys.get((bx, bz + 1), y0) - ys.get((bx, bz - 1), y0)) / 2.0
        n = [-gx, 1.0, -gz]
        nn = math.sqrt(sum(v * v for v in n))
        centre = _p(bx + 0.5, y0 + 1.0, bz + 0.5)
        gap = min(math.dist(centre, p) for p in pts)
        out.append({'cells': sorted(cells), 'normal': [round(v / nn, 4) for v in n], 'centre': centre,
                    'route_distance': round(gap, 2)})
    return out


# ---------------------------------------------------------------- checks
PASS = set(FREE) | {'minecraft:light', 'minecraft:short_grass', 'minecraft:allium', 'minecraft:dead_bush',
                    'minecraft:wall_torch', 'minecraft:torch'}


def solid_of(world_ids):
    return frozenset(p for p, b in world_ids.items() if b not in PASS)


def clearance(solid, p, R=8):
    """Distance from point p to the nearest solid cell (as a unit cube), capped at R."""
    cx, cy, cz = int(math.floor(p[0])), int(math.floor(p[1])), int(math.floor(p[2]))
    best = R
    r = int(math.ceil(R)) + 1
    for dx in range(-r, r + 1):
        ex = max(cx + dx - p[0], 0.0, p[0] - (cx + dx + 1))
        if ex >= best:
            continue
        for dy in range(-r, r + 1):
            ey = max(cy + dy - p[1], 0.0, p[1] - (cy + dy + 1))
            if ex * ex + ey * ey >= best * best:
                continue
            for dz in range(-r, r + 1):
                if (cx + dx, cy + dy, cz + dz) not in solid:
                    continue
                ez = max(cz + dz - p[2], 0.0, p[2] - (cz + dz + 1))
                d = math.sqrt(ex * ex + ey * ey + ez * ez)
                if d < best:
                    best = d
    return best


def samples(pts, step=0.5):
    out = []
    for a, b in zip(pts, pts[1:]):
        n = max(1, int(math.dist(a, b) / step))
        for i in range(n):
            t = i / float(n)
            out.append([a[k] + (b[k] - a[k]) * t for k in range(3)])
    out.append(list(pts[-1]))
    return out


def lane_min(solid, pts, R, step=0.5, skip_first=0.0, skip_last=0.0):
    """(min clearance, at point) along a polyline sampled every step, leaving out the first and last `skip` blocks
    of path."""
    total = sum(math.dist(a, b) for a, b in zip(pts, pts[1:]))
    best, at, run, prev = R, None, 0.0, None
    for p in samples(pts, step):
        if prev is not None:
            run += math.dist(prev, p)
        prev = p
        if run < skip_first or run > total - skip_last:
            continue
        c = clearance(solid, p, R)
        if c < best:
            best, at = c, [round(v, 2) for v in p]
    return best, at


def cell_of(p):
    return (int(math.floor(p[0])), int(math.floor(p[1] + 1e-6)), int(math.floor(p[2])))


def box_cells(box):
    (x0, y0, z0), (x1, y1, z1) = box
    for x in range(int(math.floor(x0)), int(math.ceil(x1))):
        for y in range(int(math.floor(y0)), int(math.ceil(y1))):
            for z in range(int(math.floor(z0)), int(math.ceil(z1))):
                yield (x, y, z)


def stands_ok(ids, problems):
    """Every seat row: bottom slabs under the seat top, 2 passable cells above each seat and the head (seat top +
    1.2) in air. Returns the number of seat cells."""
    seats = 0
    for st in ANCHORS['stands']:
        for r in st['rows']:
            x1, y1, z1, x2, y2, z2 = r
            n = int(round(max(abs(x2 - x1), abs(z2 - z1)))) + 1
            for i in range(n):
                t = i / float(max(1, n - 1))
                x, z = x1 + (x2 - x1) * t, z1 + (z2 - z1) * t
                c = (int(math.floor(x)), int(math.floor(y1 - 0.5)), int(math.floor(z)))
                s = ids.get(c)
                if s is None or not s.endswith('_slab'):
                    problems.append('seat %s of %s is %s, not a slab' % (c, st['name'], s))
                for dy in (1, 2):
                    if ids.get((c[0], c[1] + dy, c[2])) not in PASS:
                        problems.append('seat %s of %s has %s above' % (c, st['name'],
                                                                         ids.get((c[0], c[1] + dy, c[2]))))
                head = cell_of([x, y1 + 1.2, z])
                if ids.get(head) not in PASS:
                    problems.append('a head at %s of %s hits %s' % (head, st['name'], ids.get(head)))
            seats += n
    return seats


def stand_free(ids, pos, height=2):
    c = cell_of(pos)
    out = []
    if ids.get((c[0], c[1] - 1, c[2])) in PASS:
        out.append('nothing under %s' % (pos,))
    for dy in range(height):
        if ids.get((c[0], c[1] + dy, c[2])) not in PASS:
            out.append('%s at %s over %s' % (ids.get((c[0], c[1] + dy, c[2])), (c[0], c[1] + dy, c[2]), pos))
    return out


VOID_FOV = 70.0
VOID_ASPECT = 16.0 / 9.0
VOID_BOUND = 0.02


def _ray_hits(solid, o, d, max_dist):
    x, y, z = int(math.floor(o[0])), int(math.floor(o[1])), int(math.floor(o[2]))
    steps, tmax, tdelta = [], [], []
    for k, c in enumerate((x, y, z)):
        if d[k] > 1e-12:
            steps.append(1); tmax.append((c + 1 - o[k]) / d[k]); tdelta.append(1.0 / d[k])
        elif d[k] < -1e-12:
            steps.append(-1); tmax.append((o[k] - c) / -d[k]); tdelta.append(-1.0 / d[k])
        else:
            steps.append(0); tmax.append(1e30); tdelta.append(1e30)
    sx, sy, sz = steps
    lo_y = TEMPLATE_AT[1] - 1
    t = 0.0
    while t <= max_dist:
        if (x, y, z) in solid:
            return True
        if (y < lo_y and sy <= 0) or abs(x) > RADIUS + 2 or abs(z) > RADIUS + 2:
            return False
        if tmax[0] < tmax[1]:
            if tmax[0] < tmax[2]:
                t = tmax[0]; x += sx; tmax[0] += tdelta[0]
            else:
                t = tmax[2]; z += sz; tmax[2] += tdelta[2]
        elif tmax[1] < tmax[2]:
            t = tmax[1]; y += sy; tmax[1] += tdelta[1]
        else:
            t = tmax[2]; z += sz; tmax[2] += tdelta[2]
    return False


def void_fraction(cam, look, solid, fov=VOID_FOV, aspect=VOID_ASPECT, cols=48, rows=27, max_dist=520.0):
    """The share of a frame (camera at cam looking at look, no roll) that is empty space below the horizon: rays
    that point below level and hit no block of the set (template or castle). The same measure as flying_lawn's."""
    f = [look[k] - cam[k] for k in range(3)]
    n = math.sqrt(sum(v * v for v in f)) or 1.0
    f = [v / n for v in f]
    r = [-f[2], 0.0, f[0]]
    rn = math.hypot(r[0], r[2]) or 1.0
    r = [r[0] / rn, 0.0, r[2] / rn]
    u = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]]
    ty = math.tan(math.radians(fov) / 2)
    tx = ty * aspect
    void = 0
    for j in range(rows):
        sy = (1 - 2 * (j + 0.5) / rows) * ty
        for i in range(cols):
            sx = (2 * (i + 0.5) / cols - 1) * tx
            d = [f[k] + r[k] * sx + u[k] * sy for k in range(3)]
            dn = math.sqrt(sum(v * v for v in d))
            d = [v / dn for v in d]
            if d[1] >= 0:
                continue
            if not _ray_hits(solid, cam, d, max_dist):
                void += 1
    return void / float(cols * rows)


def _dir_look(p, q, pitch=0.0, dist=60.0):
    dx, dz = q[0] - p[0], q[2] - p[2]
    h = math.hypot(dx, dz) or 1.0
    c = math.cos(math.radians(pitch))
    return [p[0] + dx / h * c * dist, p[1] - math.sin(math.radians(pitch)) * dist, p[2] + dz / h * c * dist]


def void_probe_cameras():
    """(name, cam, look): the named camera spots, and along the launch lane, the castle route and the gorge lane
    the flight direction and back along it, level and 15 degrees down."""
    a = ANCHORS
    cams = [('castle_view', a['castle_view']['pos'], a['castle_view']['look']),
            ('arena_view', a['arena_view']['pos'], a['arena_view']['look']),
            ('gorge_view', a['gorge_view']['pos'], a['gorge_view']['look'])]
    for lane in ('launch_lane', 'castle_route', 'gorge_lane'):
        pts = a[lane]
        for i in range(len(pts) - 1):
            p, q = pts[i], pts[i + 1]
            for pitch in (0.0, 15.0):
                cams.append(('%s[%d] ahead p%d' % (lane, i, pitch), p, _dir_look(p, q, pitch)))
                cams.append(('%s[%d] back p%d' % (lane, i, pitch), q, _dir_look(q, p, pitch)))
    return cams


def check(w, with_void=True):
    """(problems, numbers): problem strings (empty when the set is right) and the worst numbers."""
    g = w.g
    problems = []
    numbers = {}
    ids = world_with_castle(g)
    problems += physics_problems(ids, RADIUS)
    solid = solid_of(ids)
    a = ANCHORS
    # radius, template bounds, budget
    beyond = [p for p in ids if abs(p[0]) > RADIUS or abs(p[2]) > RADIUS]
    if beyond:
        problems.append('%d cells beyond radius %d, e.g. %s' % (len(beyond), RADIUS, beyond[:3]))
    for (x, y, z) in g:
        lx, ly, lz = x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]
        if not (0 <= lx < SIZE[0] and 0 <= ly < SIZE[1] and 0 <= lz < SIZE[2]):
            problems.append('cell %s outside the template' % ((x, y, z),))
            break
    total = len(g) + len(CASTLE) + len(FILLS)
    numbers['total'] = total
    if total > MAX_TOTAL:
        problems.append('template %d + schematic %d = %d blocks, over %d' % (len(g), len(CASTLE), total, MAX_TOTAL))
    # seats
    numbers['seats'] = stands_ok(ids, problems)
    # standing anchors
    for name, pos in (('entrance', a['entrance']['pos']), ('entrance.inside', a['entrance']['inside']),
                      ('hide_rock.spot', a['hide_rock']['spot']), ('commentator', a['commentator_box']['stand'])):
        problems += ['%s: %s' % (name, p) for p in stand_free(ids, pos)]
    perch = a['nest_perch']['pos']
    problems += ['nest_perch: %s' % p for p in stand_free(ids, perch, height=4)]
    pc = cell_of(perch)
    if ids.get((pc[0], pc[1] - 1, pc[2])) not in ('minecraft:smooth_stone', 'minecraft:polished_andesite'):
        problems.append('nest_perch is not on the shelf (%s)' % ids.get((pc[0], pc[1] - 1, pc[2])))
    ec = cell_of(a['egg_spot'])
    if ids.get(ec) is not None or ids.get((ec[0], ec[1] - 1, ec[2])) in PASS:
        problems.append('egg_spot %s: cell %s, under %s' % (ec, ids.get(ec), ids.get((ec[0], ec[1] - 1, ec[2]))))
    for e in a['eggs']:
        c = cell_of(e)
        if ids.get(c) != 'minecraft:dragon_egg' or ids.get((c[0], c[1] - 1, c[2])) in PASS:
            problems.append('egg at %s is %s on %s' % (c, ids.get(c), ids.get((c[0], c[1] - 1, c[2]))))
    sc = cell_of(a['stake'])
    if ids.get(sc) != 'minecraft:chain' or ids.get((sc[0], sc[1] - 1, sc[2])) != 'minecraft:stripped_dark_oak_log':
        problems.append('stake %s: %s on %s' % (sc, ids.get(sc), ids.get((sc[0], sc[1] - 1, sc[2]))))
    # the hiding rock: a flat face (rock behind the face plane, air in front) that shields the crouch from her head
    (fx, fy0, fz0), (_, fy1, fz1) = a['hide_rock']['face_box']
    for y in range(int(fy0), int(fy1)):
        for z in range(int(fz0), int(fz1)):
            if ids.get((int(fx) - 1, y, z)) in PASS or ids.get((int(fx), y, z)) not in PASS:
                problems.append('hide_rock face cell (%d, %d, %d) is not a flat face' % (int(fx) - 1, y, z))
    sp = a['hide_rock']['spot']
    eye = [sp[0], 1.0, sp[2]]
    head = [a['nest_perch']['pos'][0] - 6.0, 4.5, a['nest_perch']['pos'][2]]
    dl = math.dist(eye, head)
    if not _ray_hits(solid, head, [(eye[k] - head[k]) / dl for k in range(3)], dl - 0.6):
        problems.append('the hiding rock does not shield the crouch from her head')
    for cs in a['cannon_spots']:
        for p in (cs['muzzle'], cs['smoke']):
            q = [p[0] + cs['dir'][0] * 0.2, p[1], p[2] + cs['dir'][2] * 0.2]
            if ids.get(cell_of(q)) not in PASS:
                problems.append('cannon point %s is blocked by %s' % (p, ids.get(cell_of(q))))
    # lanes
    c, at = lane_min(solid, a['broom_lane'], 3.0, skip_last=6.0)
    numbers['broom_lane'] = c
    if c < 2.0:
        problems.append('broom_lane clearance %.2f at %s (needs 2.0)' % (c, at))
    c, at = lane_min(solid, a['broom_lane'][-2:], 3.0)
    numbers['broom_lane_end'] = c
    if c < 0.8:
        problems.append('broom_lane end clearance %.2f at %s (needs 0.8)' % (c, at))
    c, at = lane_min(solid, a['launch_lane'], 8.0, skip_first=8.0)
    numbers['launch_lane'] = c
    if c < 6.0:
        problems.append('launch_lane clearance %.2f at %s (needs 6)' % (c, at))
    c, at = lane_min(solid, a['castle_route'], 8.0)
    numbers['castle_route'] = c
    if c < 6.0:
        problems.append('castle_route clearance %.2f at %s (needs 6)' % (c, at))
    c, at = lane_min(solid, a['gorge_lane'], 4.0)
    numbers['gorge_lane'] = c
    if c < 2.5:
        problems.append('gorge_lane clearance %.2f at %s (needs 2.5)' % (c, at))
    for name, box in (('arena_air_box', a['arena_air_box']), ('fall_column', a['bridge']['fall_column'])):
        bad = [p for p in box_cells(box) if p in ids]
        if bad:
            problems.append('%s has %d blocks, e.g. %s %s' % (name, len(bad), bad[0], ids[bad[0]]))
    # the bridge: the central arch open 14 wide and 8 tall under its crown, the break box holding the span, and the
    # gorge lane passing under it
    br = a['bridge']
    ar = br['arch']
    mid = (ar['x1'] + ar['x2']) // 2
    open_box = [[mid - 7, ar['spring_y'], BRIDGE_Z[0]], [mid + 7, ar['spring_y'] + 8, BRIDGE_Z[1] + 1]]
    bad = [p for p in box_cells(open_box) if p in ids]
    if bad:
        problems.append('the central arch is not open 14 x 8: %s %s' % (bad[0], ids[bad[0]]))
    cells = [p for p in box_cells(br['break_box']) if p in ids]
    numbers['bridge_cells'] = len(cells)
    if sorted(tuple(c) for c in br.get('cells', [])) != sorted(cells):
        problems.append('bridge.cells does not list every block in the break box')
    if len(cells) < 60:
        problems.append('the break box holds only %d blocks' % len(cells))
    under = [p for p in a['gorge_lane'] if BRIDGE_Z[0] - 1 <= p[2] <= BRIDGE_Z[1] + 1]
    if not under or not all(ar['x1'] < p[0] < ar['x2'] and p[1] < br['underside_y'] for p in under):
        problems.append('the gorge lane does not pass under the central arch')
    # the roofs the tail smashes are roof blocks open to the sky
    if len(a['slate_roofs']) < 2:
        problems.append('only %d slate roofs found' % len(a['slate_roofs']))
    for sr in a['slate_roofs']:
        for (x, y, z) in sr['cells']:
            if CASTLE.get((x, y, z)) not in ROOF_IDS or (x, y + 1, z) in ids:
                problems.append('slate cell %s is not an open roof block' % ((x, y, z),))
    # cameras in air
    for name in ('castle_view', 'arena_view', 'gorge_view'):
        p = a[name]['pos']
        if clearance(solid, p, 2.0) < 1.0:
            problems.append('%s camera at %s is within 1 of a block' % (name, p))
    # the edge of the world
    if with_void:
        worst = (-1.0, '')
        numbers['void'] = {}
        for name, cam, look in void_probe_cameras():
            v = void_fraction(cam, look, solid)
            worst = max(worst, (v, name))
            if v > VOID_BOUND:
                problems.append('camera %s %s -> %s: %.3f of the frame is void' % (name, cam, look, v))
            numbers['void'][name] = round(v, 4)
        numbers['void_worst'] = worst
    return problems, numbers


ANCHORS['castle_route'] = [list(map(float, p)) for p in CASTLE_ROUTE]
ANCHORS['gorge_lane'] = gorge_lane()
ANCHORS['slate_roofs'] = find_slate_roofs(CASTLE_ROUTE)


def local_grid(g):
    return {(x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2]): s for (x, y, z), s in g.items()}


def write_nbt():
    """Builds the set, runs every check and writes structure/first_task_grounds.nbt."""
    w = _build(World())
    problems, numbers = check(w)
    assert not problems, '\n'.join(problems[:40]) + ('\n... %d in all' % len(problems))
    n_pal, n_blocks = write_template(local_grid(w.g), SIZE, OUT)
    return n_pal, n_blocks, w, numbers


def main():
    n_pal, n_blocks, w, numbers = write_nbt()
    print('first_task_grounds.nbt: size %s, %d blocks, %d palette states, %d bytes; TEMPLATE_AT %s RADIUS %d' % (
        SIZE, n_blocks, n_pal, os.path.getsize(OUT), TEMPLATE_AT, RADIUS))
    print('  with the Mini Hogwarts (%d): %d blocks (cap %d), %d build ticks at 4,000 a tick' % (
        len(CASTLE), numbers['total'], MAX_TOTAL, -(-numbers['total'] // 4000)))
    print('  seats %d; clearances: broom_lane %.2f (end %.2f), launch_lane %.2f, castle_route %.2f, gorge_lane %.2f'
          % (numbers['seats'], numbers['broom_lane'], numbers['broom_lane_end'], numbers['launch_lane'],
             numbers['castle_route'], numbers['gorge_lane']))
    print('  bridge break cells %d; void worst %.3f (%s), castle_view %.3f, %d probe cameras' % (
        numbers['bridge_cells'], numbers['void_worst'][0], numbers['void_worst'][1], numbers['void']['castle_view'],
        len(numbers['void'])))
    counts = {}
    for s in w.g.values():
        counts[bid(s)] = counts.get(bid(s), 0) + 1
    print('  census: ' + ', '.join('%s %d' % (k.split(':')[1], v) for k, v in
                                    sorted(counts.items(), key=lambda kv: -kv[1])[:28]))
    for k, v in ANCHORS.items():
        if k == 'stands':
            for st in v:
                print('  stands.%s: %d rows, facing %s' % (st['name'], len(st['rows']), st['facing_yaw']))
            continue
        print('  %s: %s' % (k, v))


if __name__ == '__main__':
    main()
