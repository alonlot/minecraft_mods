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
  - The arena (centre ARENA_C, floor at stand y AF = -10): a deep ravine, a rounded-rectangle floor about 64 x 48
    of gravel, tuff and andesite, gently rolling (half steps are slabs, level round the anchors), scorched black
    round the nest and the hiding rock. Cliffs climb 26 to the rim (stand y 16) over ragged ledges, cut by gullies,
    with buttresses leaning out from their foot and lips overhanging the drop under the stand fronts. The rock is
    dark at the foot (cobbled deepslate, deepslate, tuff), andesite and stone with tuff strata up the wall, stone at
    the lip with moss on the ledges, streaked with basalt and blackstone near the nest and the hiding rock. Big
    rounded boulders lean on the cliff foot and a few pinnacles stand out on the floor. In it:
      * the nest east of centre: a crater of head-sized boulders (each one stone) round a bowl dished one below the
        floor and lined with blackstone and rooted dirt, her clutch (vanilla dragon eggs) on stone in it, the golden
        egg on the prize rock (a stone column four tall in the bowl's west half, its top 3 over the floor, so a
        rider skimming past at 0.6 over the nest's rocks can take it and low cameras see it), her perch at the back
        one big flat-topped rock; its west side, toward the entrance and the egg-grab dive, is kept low;
      * the hiding rock between the nest and the entrance, a boulder 4 tall with a flat face to the nest;
      * the stake behind the nest for her chain;
      * the champions' entrance: a timber-lined tunnel through the west cliff under the west stand.
  - The stands: four timber grandstands on stilts, one on each side, their front rows carried out over the drop on
    spruce legs footed down the cliff (a rock collar under each foot). Row 0 stands 5 over the rim and the rows climb
    2 every 2 (45 degrees), so the crowd stacks up into view from the floor. Each stand is a wing of 12 rows, a
    centre of 15 and a wing of 12, with a one-cell stair between them climbing to a timber stair tower (a pyramid
    cap and a flag) at the centre's back. Under the decks an open trestle: legs every 4 both ways, ledgers every 4
    up, X-bracing of stairs in the outer bays, boards on the bottom three courses round a gate. The decks are thin
    (spruce, dark oak risers), a seat slab on each row with the next row's deck as its back rest, aisles of
    stairs. Along each front a beam, a fascia of school colours, a rail and banners draped over the lip. Each
    centre carries a pavilion over its top four rows (gabled wool striped in the stand's colours, its underside 3.5
    over the seats, banners scalloped from its front beam, flags on its peaks); the wings stay open, so the
    skyline steps. The commentator's booth is at the top of the north centre (its towers the tallest), the two
    cannon platforms at the outer ends of the south wings. Rock crags fill the corners between the stands, lower
    in the north-east so the castle shows from the arena.
  - The ground: a Highland plateau at stand y 16 (grass, moss, coarse dirt, scattered boulders, heather), rising to
    snow-capped mountains round the set's edge so no chase-height camera sees the set end.
  - The castle: the Mini Hogwarts (SCHEMATIC, rotation 180 so its spire face looks west at the arena) on the
    plateau, its crag foot closed with rock, its loose gravel propped.
  - The gorge: a canyon about 36 wide and 38 deep with a stream, running north to south past the castle's west
    side, rising into the mountains in the north. South of the viaduct it runs level to z 40, then bends 17 east
    and climbs to its head at z 70, so the arch frames a long receding canyon under the mountains.
  - The viaduct: a stone bridge across the gorge at z -20 (a deck 3 wide between parapets), from a round watch
    tower on the west rim (a corbelled machicolation under its parapet, arrow slits up every side, an oak cone) to
    a small gate arch at the castle end: a pointed central arch 20 wide over the stream (springing y -2, crown
    underside y 12, so 34 of air under it) with a dark soffit of deepslate tiles and a voussoir ring a cell proud of
    both faces under a hood mould, a pointed side arch either way, tall piers on the canyon walls, a parapet on a
    corbel course with lamp piers every 4. The dragon hits its central span.

ANCHORS format
--------------
Every coordinate is a SCENE coordinate (template local + TEMPLATE_AT). The arena floor is the block layer at
y -11, so anyone on it stands at y -10 (ground_y); the plateau outside stands at y 16 (plateau_y). yaw is
Minecraft's: 0 faces +Z (south), 90 -X (west), 180 -Z (north), -90 +X (east).

  ground_y, plateau_y  -10.0 and 16.0
  nest_centre      [x, y, z] the middle of the nest at floor level
  nest_perch       {pos: [x, y, z] the point between her feet on the rock shelf (y on its top), yaw: 90 (facing
                   west, at the entrance and the hiding rock), shelf: [[x1, y1, z1], [x2, y2, z2]] its top's
                   bounds}
  egg_spot         [x, y, z] the golden egg's centre (scale 1) on the prize rock's top, 0.2 in from its north edge
  eggs             [[x, y, z], ...] the dragon eggs' cell centres
  entrance         {pos: [x, y, z] the tunnel mouth on the arena floor, yaw: -90 (facing in, +X), inside: a spot
                   back in the dark tunnel}
  hide_rock        {spot: [x, y, z] the viewer's crouch behind it, face_centre, face_normal, face_box:
                   [[x1, y1, z1], [x2, y2, z2]] the flat face toward the nest, top_y}
  stake            [x, y, z] the top of the chain's post (where the last link hooks)
  cannon_spots     2 x {muzzle: [x, y, z], dir: [dx, dy, dz], smoke: [x, y, z]} at the south wings' outer ends
  stands           [{name, school, facing_yaw, rows: [[x1, y1, z1, x2, y2, z2], ...]}] seat-top segments
                   (bottom slabs, 3.5 clear above), split at the aisles and sections; figures face facing_yaw
  commentator_box  {stand: [x, y, z], yaw, look: [x, y, z]} the booth's front at the top of the north stand
  broom_lane       [[x, y, z], ...] spline waypoints for the Firebolt from the north-east, in over the corner
                   between the north and east stands, down to the hiding rock (the last point is beside the rock,
                   2.2 above the floor)
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
import leaf_backing as LB  # noqa: E402
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

ARENA_C = (-102, 82)                    # the arena's centre (x, z)
AF = -10                                # the arena floor: its top blocks at y AF - 1, so feet stand at y AF
FLOOR_A, FLOOR_B, SUPER_N = 32.0, 24.0, 3.2
RIM_S = 1.45                            # the rim, in floor-normalised superellipse units
ROWS_C, ROWS_W = 15, 12                 # rows in a stand's centre section and in its wings
NROWS = ROWS_C
STILT = 5                               # row 0's deck stands this high over the rim, on legs down the cliff


def row_y(k):
    """The deck block y of row k: lifted STILT over the rim, the rows climbing 2 every 2 (45 degrees, so the
    crowd stacks up into view from the arena floor)."""
    return GT + STILT + 2 * k
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


# Gullies: narrow notches cut back into the cliff (angle round the arena in radians, half-width, depth in s units)
GULLIES = [(0.42, 0.045, 0.17), (1.18, 0.05, 0.15), (2.05, 0.04, 0.18), (2.4, 0.05, 0.14), (-0.55, 0.05, 0.16),
           (-1.32, 0.04, 0.17), (-2.2, 0.05, 0.15)]


def arena_angle(lx, lz):
    return math.atan2(lz / FLOOR_B, lx / FLOOR_A)


def gully(lx, lz):
    th = arena_angle(lx, lz)
    best = 0.0
    for (g, hw, depth) in GULLIES:
        d = abs((th - g + math.pi) % (2 * math.pi) - math.pi)
        hw2 = hw * (0.8 + 0.5 * vnoise(lx, lz, 3.0, 17))
        if d < hw2:
            best = max(best, depth * (1 - d / hw2) ** 0.35)
    return best


def floor_level_zone(lx, lz):
    """Floor columns kept dead level (the anchors stand there): the entrance run, the hiding rock, the stake and
    the nest."""
    if lx < -12 and -7 <= lz <= 13:
        return True
    if HIDE[0] - 5 <= lx <= HIDE[1] + 4 and HIDE[2] - 5 <= lz <= HIDE[3] + 5:
        return True
    if abs(lx - STAKE_LC[0]) <= 3 and abs(lz - STAKE_LC[1]) <= 3:
        return True
    return math.hypot(lx - NEST_LC[0], lz - NEST_LC[1]) < 14


def floor_undulation(lx, lz):
    """-1, 0 or +1: a gentle roll in the floor (half steps are slabs), level round the anchors."""
    if floor_level_zone(lx, lz):
        return 0
    n = vnoise(lx, lz, 8.0, 211) * 0.8 + vnoise(lx, lz, 3.0, 212) * 0.2
    return -1 if n < 0.3 else 1 if n > 0.7 else 0


def ravine_s(lx, lz):
    return arena_s(lx, lz) + (vnoise(lx, lz, 7.0, 3) - 0.5) * 0.07 - gully(lx, lz)


def arena_wall_top(lx, lz):
    """The ravine's ground top y at a column inside the rim (None outside it)."""
    s = ravine_s(lx, lz)
    if s >= RIM_S:
        return None
    if s < 1.0:
        # the floor, gently rolling, with scree mounds rising toward the cliff foot
        base = AF - 1 + floor_undulation(lx, lz)
        if s > 0.9:
            base += int((s - 0.9) / 0.1 * 2.2 * vnoise(lx, lz, 4.0, 5) + 0.3)
        return base
    t = (s - 1.0) / (RIM_S - 1.0)
    # a steep cliff with two ragged ledges (terraces at about a third and two thirds)
    f = t ** 0.55
    terr = 0.25 * (math.floor(f * 3) / 3.0) + 0.75 * f
    h = AF - 1 + (GT - AF + 1) * terr + (vnoise(lx, lz, 3.0, 9) - 0.5) * 2.4
    return int(clamp(round(h), AF - 1, GT))


def is_floor(lx, lz):
    return ravine_s(lx, lz) < 1.0


# ---------------------------------------------------------------- the stands
STAND_DEFS = [
    # name, axis along which the rows run, outward sign, inner cell of row 0, the run's range, facing yaw, school
    ('north', 'x', -1, -29, (-36, 35), 0.0, 'hogwarts'),
    ('south', 'x', +1, 29, (-36, 35), 180.0, 'hogwarts'),
    ('east', 'z', +1, 38, (-28, 27), 90.0, 'beauxbatons'),
    ('west', 'z', -1, -38, (-28, 27), -90.0, 'durmstrang'),
]
# Each stand is a wing, a taller centre and a wing: (first seat cell, last seat cell, rows). A side line of posts
# and rails closes each section one cell outside its seats; a one-cell stair climbs between two sections to a
# timber stair tower at the centre's back.
SECTIONS = {
    'north': [(-36, -15, ROWS_W), (-11, 10, ROWS_C), (14, 35, ROWS_W)],
    'south': [(-36, -15, ROWS_W), (-11, 10, ROWS_C), (14, 35, ROWS_W)],
    'east': [(-28, -11, ROWS_W), (-7, 6, ROWS_C), (10, 27, ROWS_W)],
    'west': [(-28, -11, ROWS_W), (-7, 6, ROWS_C), (10, 27, ROWS_W)],
}
GAPS = {'north': (-13, 12), 'south': (-13, 12), 'east': (-9, 8), 'west': (-9, 8)}
AISLES = {'north': (-26, 0, 25), 'south': (-26, 0, 25), 'east': (-20, 0, 19), 'west': (-20, 0, 19)}
CANOPY_K = ROWS_C - 4                   # the centre's canopy covers its top four rows
CANOPY_BAYS = {'north': 2, 'south': 2, 'east': 1, 'west': 1}
CANOPY_BASE = int(math.ceil(GT + STILT + 2 * (ROWS_C - 1) + 1.5 + 3.5))    # its underside: 3.5 over the seat tops
BOOTH = ('north', (-3, 3), ROWS_C - 4)          # the commentator's booth: stand, run cells, first row it replaces
CANNONS = [('south', (29, 35), ROWS_W - 3), ('south', (-36, -30), ROWS_W - 3)]
FASCIA = {'north': ('red_wool', 'yellow_wool'), 'south': ('blue_wool', 'green_wool'),
          'east': ('light_blue_wool', 'white_wool'), 'west': ('red_wool', 'brown_wool')}
FLAGS = {'north': ('red', 'yellow', 'red', 'yellow'), 'south': ('blue', 'green', 'blue', 'green'),
         'east': ('light_blue', 'white', 'blue'), 'west': ('red', 'brown', 'black')}


def row_cells(sd, k):
    """(inner, outer) depth coordinates of row k."""
    sgn, f0 = sd[2], sd[3]
    inner = f0 + sgn * 2 * k
    return inner, inner + sgn


def section_back(sd, rows):
    """The back line's depth (one cell behind the last row)."""
    return sd[3] + sd[2] * 2 * rows


def stand_back(sd):
    return section_back(sd, ROWS_C)


def section_at(sd, a):
    for sec in SECTIONS[sd[0]]:
        if sec[0] - 1 <= a <= sec[1] + 1:
            return sec
    return None


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
    """{(x, z) scene column} under every stand (its front line to the centre's back) - the ground stays at the
    plateau there."""
    cols = set()
    for sd in STAND_DEFS:
        a0, a1 = sd[4]
        for a in range(a0 - 1, a1 + 2):
            d0, d1 = sorted((sd[3] - sd[2], stand_back(sd) + sd[2]))
            for d in range(d0, d1 + 1):
                lx, lz = (a, d) if sd[1] == 'x' else (d, a)
                cols.add(to_scene(lx, lz))
    return cols


STAND_COLS = stand_footprint()


# ---------------------------------------------------------------- the gorge
GORGE_LEVEL_Z = 40                      # the floor is level from the mountains to here, south of the viaduct
GORGE_BEND = (38, 32, 17.0)             # past z 38 the canyon bends east, 17 over the next 32


def gorge_cx(z):
    b0, blen, bx = GORGE_BEND
    t = clamp((z - b0) / float(blen), 0.0, 1.0)
    return -1.0 + 4.0 * math.sin(z / 37.0) + 2.0 * math.sin(z / 13.0 + 1.0) + bx * _smooth(t)


GORGE_Z = (-168, 70)                    # its north end (in the mountains) and its head
GORGE_FLOOR = -23                       # top block of the floor (stand y -22)


def gorge_floor_y(z):
    """The gorge floor's top y along it: level, rising at the head (south) and into the mountains (north)."""
    if z > GORGE_LEVEL_Z:
        return GORGE_FLOOR + (z - GORGE_LEVEL_Z) * (GT - GORGE_FLOOR) / float(GORGE_Z[1] - GORGE_LEVEL_Z)
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


HIDE_SC = (-16.5 + ARENA_C[0], 7.5 + ARENA_C[1])    # scorch centres (scene x, z): the hiding rock, the nest
NEST_SCORCH = (6.5 + ARENA_C[0], 0.5 + ARENA_C[1])


def scorch_streak(x, y, z):
    """Fire-blackened streaks on the walls nearest the nest and the hiding rock: narrow, running up the rock."""
    dn = math.hypot(x - NEST_SCORCH[0], z - NEST_SCORCH[1])
    dh = math.hypot(x - HIDE_SC[0], z - HIDE_SC[1])
    near = max(0.0, 1 - dn / 36.0) + max(0.0, 1 - dh / 22.0)
    if near <= 0.25 or y > GT - 2:
        return None
    # tall narrow bands: the noise runs fast round the wall and slowly up it
    lx, lz = arena_local(x, z)
    u = arena_angle(lx, lz) * 34.0                       # about one unit per cell round the wall
    v = vnoise(u, y / 10.0, 1.3, 205) * 0.85 + vnoise(u, y / 3.0, 2.0, 206) * 0.15
    if v > 0.84 - 0.18 * min(1.0, near):
        return 'minecraft:basalt[axis=y]' if vnoise(x + z, y / 4.0, 1.5, 207) < 0.6 else 'minecraft:blackstone'
    return None


def ravine_rock(x, y, z):
    """The ravine's rock, dark at the cliff foot and lighter up the wall, in coherent zones (each inside about 40
    luma): cobbled deepslate, deepslate and tuff at the foot; andesite and stone with tuff strata in the middle;
    stone and andesite up to the lip. Scorch streaks near the nest and the hiding rock."""
    sc = scorch_streak(x, y, z)
    if sc:
        return sc
    yy = y - AF + (vnoise(x, z, 9.0, 201) - 0.5) * 6
    r = vnoise(x + y * 0.7, z - y * 0.5, 4.0, 202) * 0.7 + rnd(x, y, z, 203) * 0.3
    r = vnoise(x + y * 0.4, z - y * 0.3, 5.0, 202) * 0.85 + rnd(x, y, z, 203) * 0.15
    if yy < 7:
        if r < 0.4:
            return 'minecraft:cobbled_deepslate'
        if r < 0.75:
            return 'minecraft:deepslate[axis=y]'
        return 'minecraft:tuff'
    if yy < 9.5:
        return 'minecraft:tuff' if r < 0.6 else 'minecraft:deepslate[axis=y]'
    if yy < 18:
        if (y + int(vnoise(x, z, 21.0, 80) * 5)) % 7 == 0:
            return 'minecraft:tuff'
        if r < 0.5:
            return 'minecraft:andesite'
        return 'minecraft:stone'
    if r < 0.6:
        return 'minecraft:stone'
    return 'minecraft:andesite'


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
    if in_ravine and is_floor(lx, lz):
        # the arena floor, with a scorched ring of blackstone and basalt round the nest
        r = vnoise(x, z, 4.0, 91) * 0.75 + rnd(x, 0, z, 92) * 0.25
        dn = math.hypot(lx - NEST_LC[0], lz - NEST_LC[1])
        ring = 12.0 + 2.5 * vnoise(x, z, 5.0, 93)
        if dn < ring and (dn < ring - 3 or vnoise(x, z, 3.0, 95) < 0.55):
            q = vnoise(x, z, 3.5, 94)
            if q < 0.5:
                return 'minecraft:blackstone'
            if q < 0.75:
                return 'minecraft:basalt[axis=y]'
            return 'minecraft:coarse_dirt' if dn > 7 else 'minecraft:blackstone'
        dh = math.hypot(lx - HIDE_C[0], lz - HIDE_C[2])
        if dh < 6.5 and vnoise(x, z, 3.0, 96) < 0.5 * (1 - dh / 6.5) + 0.12:
            return 'minecraft:blackstone' if dh < 4.5 else 'minecraft:basalt[axis=y]'
        if r < 0.42:
            return 'minecraft:gravel'
        if r < 0.62:
            return 'minecraft:tuff'
        if r < 0.78:
            return 'minecraft:andesite'
        return 'minecraft:coarse_dirt' if vnoise(x, z, 2.5, 90) < 0.25 else 'minecraft:gravel'
    if in_ravine:
        # the cliff: ledges carry moss up the wall, gravel and dark scree at the foot
        if s <= 1 and h - AF > 4 and vnoise(x, z, 5.0, 97) < 0.5:
            return 'minecraft:moss_block' if rnd(x, h, z, 98) < 0.6 else 'minecraft:mossy_cobblestone'
        if s <= 1 and h - AF <= 4 and rnd(x, h, z, 96) < 0.5:
            return 'minecraft:gravel'
        return ravine_rock(x, h, z)
    if (x, z) in CRAG_COLS:
        return rock(x, h, z, 2)
    if gorge_top(x, z) is not None and h < GT - 1:
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
        lx, lz = arena_local(x, z)
        rk = ravine_rock if in_arena_rect(lx, lz) else rock
        for y in range(lo + 1, h):
            w.set((x, y, z), rk(x, y, z))
        if bid(w.get((x, h, z))) == 'minecraft:gravel' and h - 1 < lo + 1:
            w.set((x, h - 1, z), 'minecraft:stone')         # gravel never over air


def build_stream(w, H):
    """Still water along the gorge floor's middle, one deep, on stone, rock either side."""
    for z in range(-138, GORGE_LEVEL_Z - 3):
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
# Every y in this section is floor-relative (fset / fa add AF): 0 is where feet stand on the arena floor.
NEST_LC = (6.5, 0.5)                    # arena-local centre of the nest
SHELF = (8, 11, -2, 2)                  # lx0, lx1, lz0, lz1 of the perch shelf (top y 2.0)
BOWL_C = (4.5, 0.5)
BOWL_R = 3.1                            # the dished bowl: its lining one below the floor
EGG_CELLS = [(3, -1), (4, 1), (5, -1), (3, 1), (5, 2)]
GOLD_CELL = (4, -1)                     # the old gap in the clutch: a sixth dragon egg now
PRIZE = (2, 0)                          # the prize rock's column (floor-relative y -1..2: its top 3 over the floor)
PRIZE_TOP = 3
HIDE = (-18, -15, 5, 9)                 # the hiding rock's cells lx0, lx1, lz0, lz1 (flat face at lx -15's east)
STAKE_LC = (18, 5)
ROCK_IDS = ('minecraft:stone', 'minecraft:smooth_stone', 'minecraft:andesite', 'minecraft:polished_andesite',
            'minecraft:deepslate', 'minecraft:cobbled_deepslate', 'minecraft:tuff')


def lset(w, lx, y, lz, s):
    x, z = to_scene(lx, lz)
    w.set((x, y, z), s)


def fset(w, lx, y, lz, s):
    """lset with a floor-relative y."""
    lset(w, lx, y + AF, lz, s)


def fget(w, lx, y, lz):
    x, z = to_scene(lx, lz)
    return w.get((x, y + AF, z))


def ellipsoid(w, c, r, mat, noise=0.25, seed=0, keep=None, only_air=True, flat_top=None):
    """Fills a jittered ellipsoid (centre c, radii r, floor-relative y) with mat(lx, y, lz) where it is air.
    keep(lx, y, lz) -> False leaves a cell alone; flat_top cuts it level at that floor-relative y."""
    cx, cy, cz = c
    rx, ry, rz = r
    n = 0
    for lx in range(int(math.floor(cx - rx - 1)), int(math.ceil(cx + rx + 1)) + 1):
        for lz in range(int(math.floor(cz - rz - 1)), int(math.ceil(cz + rz + 1)) + 1):
            for y in range(int(math.floor(cy - ry - 1)), int(math.ceil(cy + ry + 1)) + 1):
                if flat_top is not None and y >= flat_top:
                    continue
                d = ((lx + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 + ((lz + 0.5 - cz) / rz) ** 2
                d += (vnoise(lx * 1.3 + y * 0.7, lz * 1.3 - y * 0.7, 2.0, 300 + seed) - 0.5) * noise
                if d > 1.0:
                    continue
                if keep is not None and not keep(lx, y, lz):
                    continue
                if only_air and fget(w, lx, y, lz) is not None:
                    continue
                fset(w, lx, y, lz, mat(lx, y, lz))
                n += 1
    return n


def boulder_mat(seed, mossy=0.0, dark=0.0):
    def mat(lx, y, lz):
        r = rnd(lx, y, lz, 310 + seed)
        m = vnoise(lx * 1.1, lz * 1.1 + y, 2.0, 311 + seed)
        if mossy and m < mossy:
            return 'minecraft:mossy_cobblestone' if r < 0.5 else 'minecraft:moss_block'
        if dark and r < dark:
            return 'minecraft:cobbled_deepslate' if r < dark * 0.5 else 'minecraft:deepslate[axis=y]'
        return ('minecraft:stone' if r < 0.45 else 'minecraft:andesite' if r < 0.78 else 'minecraft:tuff')
    return mat


# The nest's ring: (angle from the bowl's centre in degrees, ring radius, horizontal radius, height, palette). The
# west side (180, toward the entrance and the hiding rock) is kept low for the egg-grab dive; the east is her perch.
NEST_RING = [(42, 7.8, 2.8, 5.4, 0), (70, 7.4, 3.3, 6.2, 2), (98, 8.0, 2.9, 5.0, 1), (124, 7.5, 3.1, 5.6, 3),
             (150, 7.4, 2.5, 3.0, 0), (172, 7.2, 2.2, 2.3, 2), (194, 7.3, 2.3, 2.4, 4), (216, 7.5, 2.5, 2.9, 1),
             (240, 7.6, 3.0, 5.0, 3), (266, 7.9, 3.4, 6.0, 2), (292, 7.5, 3.0, 5.6, 0), (318, 7.8, 2.7, 4.6, 4)]
NEST_PALETTES = [('minecraft:stone', 'minecraft:andesite'), ('minecraft:tuff', 'minecraft:andesite'),
                 ('minecraft:cobbled_deepslate', 'minecraft:deepslate[axis=y]'), ('minecraft:andesite', 'minecraft:stone'),
                 ('minecraft:stone', 'minecraft:tuff')]


def palette_mat(seed, pal, mossy=0.0):
    """One boulder, one stone: a main block with coherent patches of a second, moss on its crown."""
    def mat(lx, y, lz):
        if mossy and y >= 2 and vnoise(lx * 1.2, lz * 1.2, 2.0, 312 + seed) < mossy:
            return 'minecraft:mossy_cobblestone' if rnd(lx, y, lz, 313 + seed) < 0.6 else 'minecraft:moss_block'
        return pal[1] if vnoise(lx + y * 0.6, lz - y * 0.6, 2.2, 311 + seed) < 0.3 else pal[0]
    return mat


def build_nest(w):
    """The nest: a crater of big rounded boulders round a bowl dished below the floor and lined with dark ash, the
    clutch on stone in it, and her perch at the back - one big flat-topped slab rock."""
    bx, bz = BOWL_C
    # the bowl: its floor one below the arena's, lined with blackstone and rooted dirt, stone under the eggs
    for lx in range(int(bx - BOWL_R) - 1, int(bx + BOWL_R) + 2):
        for lz in range(int(bz - BOWL_R) - 1, int(bz + BOWL_R) + 2):
            db = math.hypot(lx + 0.5 - bx, lz + 0.5 - bz)
            if db > BOWL_R:
                continue
            fset(w, lx, -1, lz, None)
            r = rnd(lx, 0, lz, 105)
            s = ('minecraft:blackstone' if r < 0.5 else 'minecraft:rooted_dirt' if r < 0.8 else
                 'minecraft:basalt[axis=y]')
            if (lx, lz) in EGG_CELLS or (lx, lz) == GOLD_CELL:
                s = 'minecraft:stone' if (lx + lz) % 2 else 'minecraft:smooth_stone'
            fset(w, lx, -2, lz, s)
    # the perch: one big rock with a flat top at 2.0 (smooth stone and polished andesite), rounded below
    ellipsoid(w, (10.6, 0.6, 0.5), (4.3, 2.3, 4.6), palette_mat(1, NEST_PALETTES[3]), noise=0.2, seed=1, flat_top=2,
              keep=lambda lx, y, lz: y >= 0)
    for lx in range(SHELF[0], SHELF[1] + 1):
        for lz in range(SHELF[2], SHELF[3] + 1):
            for y in range(0, 2):
                fset(w, lx, y, lz, 'minecraft:stone' if y == 0 else
                     ('minecraft:smooth_stone' if rnd(lx, y, lz, 107) < 0.55 else 'minecraft:polished_andesite'))
            for y in range(2, 7):
                fset(w, lx, y, lz, None)
    # the ring of boulders, each about the size of her head
    for i, (ang, rr, hr, ht, pal) in enumerate(NEST_RING):
        a = math.radians(ang + (_hash(i, 1, 108) - 0.5) * 8)
        cx = bx + math.cos(a) * rr
        cz = bz + math.sin(a) * rr
        ry = ht / 2.0 + 0.4
        mossy = 0.3 if i % 4 == 1 else 0.0
        ellipsoid(w, (cx, ry - 0.9, cz), (hr, ry, hr * (0.85 + 0.3 * _hash(i, 2, 108))),
                  palette_mat(10 + i, NEST_PALETTES[pal], mossy=mossy), noise=0.25, seed=10 + i,
                  keep=lambda lx, y, lz: y >= 0 and not (SHELF[0] - 1 <= lx <= SHELF[1] + 1 and
                                                          SHELF[2] - 1 <= lz <= SHELF[3] + 1))
    # the bowl's lip: a low ring of ash and scattered rock between the bowl and the boulders
    for lx in range(int(bx - 6), int(bx + 7)):
        for lz in range(int(bz - 6), int(bz + 7)):
            db = math.hypot(lx + 0.5 - bx, lz + 0.5 - bz)
            if BOWL_R < db < 4.6 and fget(w, lx, 0, lz) is None:
                if rnd(lx, 1, lz, 106) < 0.35 and not (SHELF[0] <= lx <= SHELF[1] + 1):
                    fset(w, lx, 0, lz, 'minecraft:cobblestone_slab[type=bottom,waterlogged=false]')
    # her clutch on the stone (the old gap filled with a sixth egg), and the prize rock: one column of stone rising
    # out of the bowl's west half, the golden egg on its top (Task 13: a skimming rider can reach it)
    for (lx, lz) in EGG_CELLS + [GOLD_CELL]:
        fset(w, lx, -1, lz, 'minecraft:dragon_egg')
    for y in range(-1, PRIZE_TOP):
        r = rnd(PRIZE[0], y, PRIZE[1], 109)
        fset(w, PRIZE[0], y, PRIZE[1], 'minecraft:polished_andesite' if y == PRIZE_TOP - 1 else
             'minecraft:tuff' if r < 0.35 else 'minecraft:andesite' if r < 0.7 else 'minecraft:stone')
    # dry grass and dead bushes caught in the ring
    for lx, lz in ((0, 4), (8, -5), (1, -4), (3, 5)):
        x, z = to_scene(lx, lz)
        top = max([y for y in range(AF - 2, AF + 6) if w.get((x, y, z))], default=None)
        if top is None:
            continue
        if bid(w.get((x, top, z))) in ROCK_IDS + ('minecraft:blackstone', 'minecraft:gravel', 'minecraft:tuff'):
            w.set((x, top, z), 'minecraft:coarse_dirt')
            w.set((x, top + 1, z), 'minecraft:dead_bush')


HIDE_C = (-15.6, 0.4, 7.5)               # the boulder: an ellipsoid (centre, radii) cut flat on its nest side
HIDE_R = (2.8, 3.4, 3.2)


def build_hide_rock(w):
    """The hiding rock: a weathered boulder 4 tall and about 6 across, its east side split off flat (the face
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
                fset(w, lx, y, lz, s_)
    # the flat face toward the nest is whole: 5 wide, 3 tall
    for lz in range(HIDE[2], HIDE[3] + 1):
        for y in range(0, 3):
            fset(w, HIDE[1], y, lz, 'minecraft:stone' if (lz + y) % 3 else 'minecraft:smooth_stone')
            fset(w, HIDE[1] - 1, y, lz, 'minecraft:stone')


def build_stake(w):
    lx, lz = STAKE_LC
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                fset(w, lx + dx, 0, lz + dz, 'minecraft:cobblestone_slab[type=bottom,waterlogged=false]')
    fset(w, lx, 0, lz, 'minecraft:stripped_dark_oak_log[axis=y]')
    fset(w, lx, 1, lz, 'minecraft:stripped_dark_oak_log[axis=y]')
    fset(w, lx, 2, lz, 'minecraft:chain[axis=y,waterlogged=false]')


def floor_point(theta, s=1.0):
    """The arena-local (lx, lz) where the ray from the centre at angle theta (arena_angle's sense) reaches s."""
    c, sn = math.cos(theta), math.sin(theta)
    lo, hi = 0.0, 3.0
    for _ in range(40):
        m = (lo + hi) / 2
        if arena_s(c * FLOOR_A * m, sn * FLOOR_B * m) < s:
            lo = m
        else:
            hi = m
    return c * FLOOR_A * lo, sn * FLOOR_B * lo


def clear_of_runs(lx, lz, y=0):
    """False where relief may not stand: the entrance run and its tunnel mouth, the hiding rock, the nest, the
    stake, and the open air over the floor (the arena air box)."""
    if lx < -14 and -8 <= lz <= 14:
        return False
    if HIDE[0] - 5 <= lx <= HIDE[1] + 4 and HIDE[2] - 5 <= lz <= HIDE[3] + 5:
        return False
    if math.hypot(lx - NEST_LC[0], lz - NEST_LC[1]) < 15:
        return False
    if abs(lx - STAKE_LC[0]) <= 4 and abs(lz - STAKE_LC[1]) <= 4:
        return False
    if abs(lx) <= 23 and abs(lz) <= 15 and y >= 5:
        return False
    return True


# Buttresses: (angle, how far they reach out over the floor, height up the wall, girth)
SPURS = [(0.12, 6.0, 24, 3.6), (0.7, 4.0, 17, 3.0), (0.98, 3.0, 26, 2.6), (1.55, 6.5, 22, 3.8),
         (1.95, 3.5, 13, 2.8), (2.45, 5.0, 20, 3.4), (-0.3, 5.0, 19, 3.2), (-0.85, 7.0, 25, 4.0),
         (-1.12, 3.0, 11, 2.4), (-1.62, 4.5, 15, 3.0), (-2.05, 6.0, 23, 3.6), (-2.6, 3.5, 12, 2.6)]


def build_relief(w):
    """Rock masses on the heightfield: buttresses and spurs leaning out from the cliff foot, lips overhanging the
    drop under the stand fronts, big rounded boulders at the cliff foot and a few pinnacles on the floor."""
    n = 0
    for i, (th, reach, hgt, girth) in enumerate(SPURS):
        fx, fz = floor_point(th, 1.0)
        nx, nz = -math.cos(th), -math.sin(th)           # inward, toward the arena centre
        steps = int(hgt / 2.4) + 1
        for j in range(steps):
            t = j / float(max(1, steps - 1))
            out = reach * (1 - t) ** 1.3 - 1.0
            cx, cz = fx + nx * out, fz + nz * out
            jit = (_hash(i, j, 320) - 0.5) * 1.6
            cx += -nz * jit
            cz += nx * jit
            cy = j * 2.4 - 0.5
            rr = girth * (1.05 - 0.45 * t) * (0.85 + 0.3 * _hash(i, j, 321))
            n += ellipsoid(w, (cx, cy, cz), (rr, 2.2, rr), lambda lx, y, lz: ravine_rock(
                to_scene(lx, lz)[0], y + AF, to_scene(lx, lz)[1]), noise=0.35, seed=20 + i,
                keep=lambda lx, y, lz: y >= -1 and clear_of_runs(lx, lz, y))
    # the overhanging lips at the rim, under the stand fronts: thin slabs of rock with air under them
    for i in range(22):
        th = -math.pi + (i + 0.5) * 2 * math.pi / 22 + (_hash(i, 1, 330) - 0.5) * 0.12
        if abs(abs(th) - math.pi) < 0.25:
            continue                                    # not over the tunnel mouth
        px, pz = floor_point(th, RIM_S - 0.05)
        nx, nz = -math.cos(th), -math.sin(th)
        cx, cz = px + nx * 1.2, pz + nz * 1.2
        ly = GT - AF - 2 - int(_hash(i, 2, 330) * 3)
        rr = 3.5 + 3.0 * _hash(i, 3, 330)
        n += ellipsoid(w, (cx, ly, cz), (rr, 1.3, rr), lambda lx, y, lz: ravine_rock(
            to_scene(lx, lz)[0], y + AF, to_scene(lx, lz)[1]), noise=0.3, seed=40 + i)
    # big rounded boulders leaning on the cliff foot
    for i in range(12):
        th = -math.pi + (i + 0.3 + 0.4 * _hash(i, 1, 340)) * 2 * math.pi / 12
        fx, fz = floor_point(th, 0.93)
        r = 2.5 + 2.0 * _hash(i, 2, 340)
        h = 3.0 + 3.0 * _hash(i, 3, 340)
        if not clear_of_runs(fx, fz, 0):
            continue
        n += ellipsoid(w, (fx, h / 2.0 - 0.6, fz), (r, h / 2.0 + 0.4, r * (0.8 + 0.4 * _hash(i, 4, 340))),
                       boulder_mat(50 + i, mossy=0.22 if i % 3 == 0 else 0.0, dark=0.3), noise=0.3, seed=50 + i,
                       keep=lambda lx, y, lz: y >= -1 and clear_of_runs(lx, lz, y))
    # a few rock pinnacles standing out on the floor
    for i, (px, pz, ph) in enumerate(((-26, -17, 9), (25, 18, 10), (27, -13, 7), (-9, 19, 8), (12, -19, 6))):
        steps = int(ph / 1.6) + 1
        for j in range(steps):
            t = j / float(steps - 1)
            rr = 2.6 * (1 - t) + 0.8 * t
            jx = (_hash(i, j, 350) - 0.5) * 0.8
            jz = (_hash(i, j, 351) - 0.5) * 0.8
            n += ellipsoid(w, (px + jx, j * 1.6 - 0.4, pz + jz), (rr, 1.4, rr * 0.9), lambda lx, y, lz:
                           ravine_rock(to_scene(lx, lz)[0], y + AF, to_scene(lx, lz)[1]), noise=0.3, seed=60 + i,
                           keep=lambda lx, y, lz: y >= -1)
    # small rocks scattered on the floor
    for i in range(30):
        lx = int(-29 + _hash(i, 1, 121) * 58)
        lz = int(-21 + _hash(i, 2, 121) * 42)
        if not clear_of_runs(lx, lz, 0) or not is_floor(lx, lz):
            continue
        r = 0.8 + 0.9 * _hash(i, 3, 121)
        n += ellipsoid(w, (lx + 0.5, 0.0, lz + 0.5), (r, 0.9 + 0.5 * _hash(i, 4, 121), r), boulder_mat(70 + i),
                       noise=0.4, seed=70 + i, keep=lambda lx, y, lz: y >= 0)
    return n


def floor_slabs(w, H):
    """Half steps where the rolling floor rises a block: a stone or andesite slab on the lower cell."""
    for lx in range(-34, 35):
        for lz in range(-26, 27):
            if not is_floor(lx, lz) or floor_level_zone(lx, lz):
                continue
            x, z = to_scene(lx, lz)
            h = H.get((x, z))
            if h is None or w.get((x, h + 1, z)) is not None:
                continue
            up = any(H.get((x + dx, z + dz)) == h + 1 and is_floor(lx + dx, lz + dz)
                     for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if up and rnd(x, h, z, 360) < 0.75:
                w.set((x, h + 1, z), 'minecraft:stone_slab[type=bottom,waterlogged=false]' if rnd(x, h, z, 361) < 0.5
                      else 'minecraft:andesite_slab[type=bottom,waterlogged=false]')


MOUTH = -int(FLOOR_A) + 2                # the tunnel mouth's frame column (arena-local x)
TUNNEL_END = MOUTH - 23


def build_entrance(w):
    """The champions' tunnel through the west cliff: 5 wide, 5 high, timber-lined, closed in the dark at its end."""
    zs = range(-2, 3)
    for lx in range(TUNNEL_END + 1, MOUTH + 2):
        for lz in range(-3, 4):
            for y in range(0, 6):
                fset(w, lx, y, lz, None)
    for lx in range(TUNNEL_END + 1, MOUTH + 1):
        for lz in zs:
            fset(w, lx, -1, lz, 'minecraft:coarse_dirt' if (lx + lz) % 3 else 'minecraft:dirt_path')
        for y in range(0, 6):
            fset(w, lx, y, -3, 'minecraft:spruce_planks' if lx % 4 else 'minecraft:spruce_log[axis=y]')
            fset(w, lx, y, 3, 'minecraft:spruce_planks' if lx % 4 else 'minecraft:spruce_log[axis=y]')
        for lz in range(-3, 4):
            fset(w, lx, 5, lz, 'minecraft:spruce_log[axis=z]' if lx % 4 == 0 else 'minecraft:dark_oak_planks')
    for lz in range(-3, 4):
        for y in range(0, 6):
            fset(w, TUNNEL_END, y, lz, 'minecraft:dark_oak_planks')
    # the mouth: a heavy timber frame in the cliff, torches either side, rock over it
    for y in range(0, 6):
        fset(w, MOUTH, y, -3, 'minecraft:spruce_log[axis=y]')
        fset(w, MOUTH, y, 3, 'minecraft:spruce_log[axis=y]')
    for lz in range(-3, 4):
        fset(w, MOUTH, 5, lz, 'minecraft:spruce_log[axis=z]')
        for y in (6, 7):
            x, z = to_scene(MOUTH, lz)
            fset(w, MOUTH, y, lz, ravine_rock(x, y + AF, z))
    for lz in (-4, 4):
        for y in range(0, 8):
            x, z = to_scene(MOUTH, lz)
            fset(w, MOUTH, y, lz, ravine_rock(x, y + AF, z))
    fset(w, MOUTH + 1, 3, -4, 'minecraft:wall_torch[facing=east]')
    fset(w, MOUTH + 1, 3, 4, 'minecraft:wall_torch[facing=east]')


# ---------------------------------------------------------------- stands
def stand_lc(sd, a, d):
    return (a, d) if sd[1] == 'x' else (d, a)


def sset(w, sd, a, d, y, s):
    lx, lz = stand_lc(sd, a, d)
    lset(w, lx, y, lz, s)


def sget(w, sd, a, d, y):
    lx, lz = stand_lc(sd, a, d)
    x, z = to_scene(lx, lz)
    return w.get((x, y, z))


def out_facing(sd):
    """The blockstate facing that points outward (away from the arena) for a stand."""
    if sd[1] == 'x':
        return 'north' if sd[2] < 0 else 'south'
    return 'west' if sd[2] < 0 else 'east'


def in_facing(sd):
    return OPPOSITE[out_facing(sd)]


OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def run_facing(sd, sign):
    """The facing along the run: +1 toward +a, -1 toward -a."""
    if sd[1] == 'x':
        return 'east' if sign > 0 else 'west'
    return 'south' if sign > 0 else 'north'


def depth_facing(sd, sign):
    """The facing along the depth axis: +1 toward +d, -1 toward -d."""
    if sd[1] == 'x':
        return 'south' if sign > 0 else 'north'
    return 'east' if sign > 0 else 'west'


def spruce_stair(f, half='bottom'):
    return state('spruce_stairs', facing=f, half=half, shape='straight', waterlogged='false')


SLAB = 'minecraft:spruce_slab[type=bottom,waterlogged=false]'
FENCE = 'minecraft:spruce_fence'
LEG = 'minecraft:spruce_log[axis=y]'
LEVELS = list(range(GT + STILT - 1 - 4 * 8, 110, 4))   # the ledgers' heights: under the front beam and every 4


def depth_axis(sd):
    return 'z' if sd[1] == 'x' else 'x'


def deck_bottom(w, sd, a, d):
    """The lowest block of the stand's structure over a column (scanning up from the plateau), or None."""
    for y in range(G, 100):
        if sget(w, sd, a, d, y) is not None:
            return y
    return None


def ground_below(w, sd, a, d, y):
    for yy in range(y - 1, AF - 4, -1):
        if sget(w, sd, a, d, yy) is not None:
            return yy
    return AF - 4


def build_decks(w, sd):
    """The seat rows: a thin stepped deck (spruce, with dark oak risers) on every section, bottom-slab seats on the
    outer cell of each row, aisles of stairs, rails on the side lines; the front beam, fascia, rail and banners."""
    name, sgn, f0 = sd[0], sd[2], sd[3]
    out_f, in_f = out_facing(sd), in_facing(sd)
    for (s0, s1, rows) in SECTIONS[name]:
        lo, hi = s0 - 1, s1 + 1
        for a in range(lo, hi + 1):
            for k in range(rows):
                if in_box(sd, a, k):
                    continue
                inner, outer = row_cells(sd, k)
                fy = row_y(k)
                for d in (inner, outer):
                    sset(w, sd, a, d, fy, 'minecraft:spruce_planks')
                    for y in range(fy + 1, fy + 5):
                        sset(w, sd, a, d, y, None)
                sset(w, sd, a, inner, fy - 1, 'minecraft:dark_oak_planks')     # the riser
                if a in (lo, hi):
                    sset(w, sd, a, inner, fy + 1, FENCE)
                    sset(w, sd, a, outer, fy + 1, FENCE)
                elif a in AISLES[name]:
                    sset(w, sd, a, outer, fy + 1, spruce_stair(out_f))
                else:
                    sset(w, sd, a, outer, fy + 1, SLAB)
    # the one-cell stairs between the sections, from the front to the centre's back
    for ag in GAPS[name]:
        for k in range(ROWS_C):
            inner, outer = row_cells(sd, k)
            fy = row_y(k)
            sset(w, sd, ag, inner, fy - 1, spruce_stair(out_f))
            sset(w, sd, ag, outer, fy, spruce_stair(out_f))
            for y in range(fy, fy + 4):
                sset(w, sd, ag, inner, y, None)
                sset(w, sd, ag, outer, y + 1, None)
    # the front: a heavy beam on the legs, the fascia of school colours on it, a rail, banners draped over the lip
    a0, a1 = sd[4]
    front = f0 - sgn
    y0 = row_y(0)
    for a in range(a0 - 1, a1 + 2):
        i = (a - a0 + 1) // 3
        sset(w, sd, a, front, y0, 'minecraft:' + FASCIA[name][i % 2])
        sset(w, sd, a, front, y0 - 1, 'minecraft:stripped_spruce_log[axis=%s]' % sd[1])
        sset(w, sd, a, front, y0 + 1, FENCE)
        if (a - a0 + 1) % 3 == 1:
            col = FLAGS[name][i % len(FLAGS[name])]
            sset(w, sd, a, front - sgn, y0, state(col + '_wall_banner', facing=in_f))


def build_back(w, sd, sec):
    """A section's back line: posts to the crest, a parapet behind the top row, the crest beam, house banners
    hanging outside, flags on the wings' crest."""
    name, sgn = sd[0], sd[2]
    s0, s1, rows = sec
    lo, hi = s0 - 1, s1 + 1
    back = section_back(sd, rows)
    top = row_y(rows - 1)
    crest = top + 4
    out_f = out_facing(sd)
    posts = post_runs(lo, hi)
    for a in range(lo, hi + 1):
        sset(w, sd, a, back, top, 'minecraft:stripped_spruce_log[axis=%s]' % sd[1])
        sset(w, sd, a, back, top + 1, FENCE)
        sset(w, sd, a, back, crest, 'minecraft:stripped_spruce_log[axis=%s]' % sd[1])
    for a in posts:
        for y in range(G, crest + 1):
            sset(w, sd, a, back, y, LEG)
        col = FLAGS[name][(a - lo) // 4 % len(FLAGS[name])]
        sset(w, sd, a, back + sgn, crest - 1, state(col + '_wall_banner', facing=out_f))
        if rows < ROWS_C and (a - lo) % 8 == 4:
            for y in range(crest + 1, crest + 5):
                sset(w, sd, a, back, y, FENCE)
            sset(w, sd, a, back, crest + 5, state(col + '_banner', rotation=int(round(sd[5] / 22.5)) % 16))
    return back


def post_runs(lo, hi, step=4):
    ps = list(range(lo, hi + 1, step))
    if hi - ps[-1] <= 1:
        ps[-1] = hi
    else:
        ps.append(hi)
    return ps


def post_depths(sd, rows):
    front, back = sd[3] - sd[2], section_back(sd, rows)
    out = []
    d = front
    while (back - d) * sd[2] > 2:
        out.append(d)
        d += 4 * sd[2]
    return out + [back]


def build_trestle(w, sd, sec):
    """The open timber trestle under a section: legs every 4 both ways (the front ones footed down the cliff,
    with a rock collar), ledgers every 4 up, X-bracing in the bays of the outer faces, boards on the bottom three
    courses of the back and sides round a gate."""
    name, sgn = sd[0], sd[2]
    s0, s1, rows = sec
    lo, hi = s0 - 1, s1 + 1
    back = section_back(sd, rows)
    front = sd[3] - sgn
    pa = post_runs(lo, hi)
    pd = post_depths(sd, rows)
    # legs (the back line's are built with it)
    foot, top = {}, {}
    for a in pa:
        for d in pd:
            if d == back:
                foot[(a, d)], top[(a, d)] = GT, row_y(rows - 1) + 4
                continue
            db = deck_bottom(w, sd, a, d)
            if db is None:
                continue
            g = ground_below(w, sd, a, d, db)
            for y in range(g + 1, db):
                sset(w, sd, a, d, y, LEG)
            foot[(a, d)], top[(a, d)] = g, db
            if g < GT:
                # a rock collar where the leg stands on the cliff
                for da, dd in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    if sget(w, sd, a + da, d + dd, g + 1) is None and sget(w, sd, a + da, d + dd, g) is not None:
                        lx, lz = stand_lc(sd, a + da, d + dd)
                        x, z = to_scene(lx, lz)
                        sset(w, sd, a + da, d + dd, g + 1, ravine_rock(x, g + 1, z) if rnd(x, g, z, 370) < 0.7
                             else 'minecraft:cobblestone')

    def free(a, d, y):
        if sget(w, sd, a, d, y) is not None:
            return False
        db = deck_bottom(w, sd, a, d)
        return db is not None and y < db and ground_below(w, sd, a, d, y + 1) < y

    run_axis = 'minecraft:stripped_spruce_log[axis=%s]' % sd[1]
    dep_axis = 'minecraft:stripped_spruce_log[axis=%s]' % depth_axis(sd)
    # ledgers along the run at every post depth, and along the depth at every post run
    for d in pd:
        for i in range(len(pa) - 1):
            p, q = (pa[i], d), (pa[i + 1], d)
            if p not in foot or q not in foot:
                continue
            for y in LEVELS:
                if not (max(foot[p], foot[q]) < y < min(top[p], top[q])):
                    continue
                for a in range(pa[i] + 1, pa[i + 1]):
                    if free(a, d, y):
                        sset(w, sd, a, d, y, run_axis)
    for a in pa:
        for i in range(len(pd) - 1):
            p, q = (a, pd[i]), (a, pd[i + 1])
            if p not in foot or q not in foot:
                continue
            for y in LEVELS:
                if not (max(foot[p], foot[q]) < y < min(top[p], top[q])):
                    continue
                for d in range(pd[i] + sgn, pd[i + 1], sgn):
                    if free(a, d, y):
                        sset(w, sd, a, d, y, dep_axis)
    # X-bracing in the bays of the front, the back and both sides
    def brace_face(cells_line, facing_up, facing_down, fixed):
        for i in range(len(cells_line) - 1):
            u0, u1 = cells_line[i], cells_line[i + 1]
            p, q = fixed(u0), fixed(u1)
            if p not in foot or q not in foot:
                continue
            span = abs(u1 - u0)
            if span < 3:
                continue
            step = 1 if u1 > u0 else -1
            for y in LEVELS:
                if not (max(foot[p], foot[q]) < y and y + 4 < min(top[p], top[q]) + 1):
                    continue
                for j in range(1, span):
                    yy = y + int(round(j * 4.0 / span))
                    if yy <= y or yy >= y + 4:
                        continue
                    for (u, f) in ((u0 + step * j, facing_up), (u1 - step * j, facing_down)):
                        a, d = fixed(u)
                        if free(a, d, yy):
                            sset(w, sd, a, d, yy, spruce_stair(f))

    for d in (front, back):
        brace_face(pa, run_facing(sd, 1), run_facing(sd, -1), lambda u, d=d: (u, d))
    for a in (lo, hi):
        brace_face(pd, depth_facing(sd, sgn), depth_facing(sd, -sgn), lambda u, a=a: (a, u))
    # boards on the bottom three courses of the back and the sides, with a gate in the back
    mid = (lo + hi) // 2
    for a in range(lo, hi + 1):
        if abs(a - mid) <= 1:
            continue
        for y in range(G, G + 3):
            if sget(w, sd, a, back, y) is None:
                sset(w, sd, a, back, y, 'minecraft:spruce_planks' if (a + y) % 4 else 'minecraft:dark_oak_planks')
    for a in (lo, hi):
        for d in range(front, back, sgn):
            for y in range(G, G + 3):
                if sget(w, sd, a, d, y) is None and ground_below(w, sd, a, d, y + 1) == y - 1:
                    sset(w, sd, a, d, y, 'minecraft:spruce_planks' if (d + y) % 4 else 'minecraft:dark_oak_planks')


def build_canopy(w, sd, sec):
    """The centre's pavilion roof over its top rows: wool striped in the stand's colours every 3, gabled in bays
    facing the arena, on the back posts and poles at the side lines, with banners scalloped from its front beam
    and flags on its peaks."""
    name, sgn = sd[0], sd[2]
    s0, s1, rows = sec
    lo, hi = s0 - 1, s1 + 1
    back = section_back(sd, rows)
    dcf = row_cells(sd, CANOPY_K)[0]
    base = CANOPY_BASE
    nb = CANOPY_BAYS[name]
    width = hi - lo + 1
    bays = []
    for b in range(nb):
        b0 = lo + (width * b) // nb
        b1 = lo + (width * (b + 1)) // nb - 1
        bays.append((b0, b1))

    def h_at(a):
        for b0, b1 in bays:
            if b0 <= a <= b1:
                return min(a - b0, b1 - a)
        return 0

    for a in range(lo, hi + 1):
        col = 'minecraft:' + FASCIA[name][((a - lo) // 3) % 2]
        h = h_at(a)
        for d in range(dcf, back + sgn, sgn):
            sset(w, sd, a, d, base + h, col)
        for d in (dcf, back):
            for y in range(base, base + h):
                sset(w, sd, a, d, y, col)
        sset(w, sd, a, dcf, base - 1, 'minecraft:stripped_spruce_log[axis=%s]' % sd[1])
        if (a - lo) % 2 == 0:
            fl = FLAGS[name][((a - lo) // 2) % len(FLAGS[name])]
            sset(w, sd, a, dcf - sgn, base - 1, state(fl + '_wall_banner', facing=in_facing(sd)))
    # poles at the side lines and between the bays, from the deck (or the booth's roof) to the beam
    for a in sorted(set([lo, hi] + [b0 for b0, _ in bays[1:]])):
        y = base - 2
        while y > G and sget(w, sd, a, dcf, y) is None:
            sset(w, sd, a, dcf, y, FENCE)
            y -= 1
    # the back posts carry it
    for a in post_runs(lo, hi):
        for y in range(row_y(rows - 1) + 5, base):
            sset(w, sd, a, back, y, LEG)
    # flags on the peaks
    for b0, b1 in bays:
        ac = (b0 + b1) // 2
        hp = h_at(ac)
        for d in (dcf, back):
            for y in range(base + hp + 1, base + hp + 4):
                sset(w, sd, ac, d, y, FENCE)
            fl = FLAGS[name][(b0 // 3) % len(FLAGS[name])]
            sset(w, sd, ac, d, base + hp + 4, state(fl + '_banner', rotation=int(round(sd[5] / 22.5)) % 16))
    return base + max(h_at(a) for a in range(lo, hi + 1))


def build_stair_tower(w, sd, ag, top_t):
    """A 3-wide timber stair tower at the centre's back, between two sections: log corners, plank walls with
    slits, a door where the stair arrives, a pyramid cap of spruce stairs and a flag."""
    name, sgn = sd[0], sd[2]
    back = stand_back(sd)
    d0 = back - 2 * sgn
    ds = (d0, back - sgn, back)
    for y in range(G, top_t + 1):
        for i, a in enumerate((ag - 1, ag, ag + 1)):
            for j, d in enumerate(ds):
                if i == 1 and j == 1:
                    if y > row_y(ROWS_C - 1):
                        sset(w, sd, a, d, y, None)
                    continue
                corner = i != 1 and j != 1
                if corner:
                    sset(w, sd, a, d, y, LEG)
                elif y % 6 == 3 and y > G + 4:
                    sset(w, sd, a, d, y, None)                       # a slit
                else:
                    sset(w, sd, a, d, y, 'minecraft:spruce_planks' if y % 5 else 'minecraft:dark_oak_planks')
    # the door at the stair's top
    ys = row_y(ROWS_C - 1)
    for y in range(ys, ys + 3):
        sset(w, sd, ag, d0, y, None)
    sset(w, sd, ag, back - sgn, ys, spruce_stair(out_facing(sd)))
    # the cap
    for lvl, r in ((1, 2), (2, 1)):
        y = top_t + lvl
        for da in range(-r, r + 1):
            for dd in range(-r, r + 1):
                a, d = ag + da, back - sgn + dd * sgn
                if max(abs(da), abs(dd)) < r:
                    sset(w, sd, a, d, y, 'minecraft:spruce_planks')
                    continue
                if abs(da) >= abs(dd):
                    f = run_facing(sd, -1 if da > 0 else 1)
                else:
                    f = depth_facing(sd, -sgn if dd > 0 else sgn)
                sset(w, sd, a, d, y, spruce_stair(f))
    sset(w, sd, ag, back - sgn, top_t + 3, 'minecraft:spruce_planks')
    for y in range(top_t + 4, top_t + 8):
        sset(w, sd, ag, back - sgn, y, FENCE)
    fl = FLAGS[name][0]
    sset(w, sd, ag, back - sgn, top_t + 8, state(fl + '_banner', rotation=int(round(sd[5] / 22.5)) % 16))


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
            for y in range(fy + 1, CANOPY_BASE - 1):
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
        sec = section_at(sd, a0)
        fy = row_y(k0)
        d_front, _ = row_cells(sd, k0)
        back = section_back(sd, sec[2])
        ds = list(range(min(d_front, back - sd[2]), max(d_front, back - sd[2]) + 1))
        for a in range(a0, a1 + 1):
            for d in ds:
                sset(w, sd, a, d, fy, 'minecraft:stone_bricks' if (a + d) % 3 else 'minecraft:cracked_stone_bricks')
                for y in range(fy + 1, fy + 6):
                    sset(w, sd, a, d, y, None)
            sset(w, sd, a, d_front, fy + 1, 'minecraft:stone_brick_wall')
        ac = (a0 + a1) // 2
        # the cannon: a carriage and a barrel along the depth axis, pointing into the arena
        d1, d2 = d_front + sd[2], d_front + 2 * sd[2]
        axis = depth_axis(sd)
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
        build_decks(w, sd)
    build_booth(w)
    build_cannons(w)
    for sd in STAND_DEFS:
        ridge = None
        for sec in SECTIONS[sd[0]]:
            build_back(w, sd, sec)
            if sec[2] == ROWS_C:
                ridge = build_canopy(w, sd, sec)
        top_t = ridge + 3 if sd[0] == BOOTH[0] else row_y(ROWS_C - 1) + 8
        for ag in GAPS[sd[0]]:
            build_stair_tower(w, sd, ag, top_t)
        for sec in SECTIONS[sd[0]]:
            build_trestle(w, sd, sec)


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
            # the soffit: dark, so the opening reads as an opening
            if under is not None and under >= gtop + 1:
                w.set((x, under, z), 'minecraft:deepslate_tiles' if rnd(x, under, z, 132) < 0.75 else
                      'minecraft:cobbled_deepslate')
            w.set((x, DECK_Y, z), 'minecraft:stone_bricks' if z == (z0 + z1) // 2 else 'minecraft:polished_andesite')
        for z in (z0 - 1, z1 + 1):
            # the parapet: a slab plinth on a corbel course, a wall on it (2 high over the deck)
            w.set((x, DECK_Y, z), 'minecraft:stone_brick_stairs[facing=%s,half=top,shape=straight,waterlogged=false]'
                  % ('south' if z < z0 else 'north'))
            w.set((x, DECK_Y + 1, z), 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]')
            w.set((x, DECK_Y + 2, z), 'minecraft:stone_brick_wall')
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
    # the central arch's voussoir ring, a cell proud of both faces (chiselled stone bricks and polished andesite in
    # turn, two deep), under a hood mould of upside-down stone brick stairs
    for x in range(c0, c1 + 1):
        under = int(math.floor(arch_underside(x, c0, c1, SPRING_C)))
        for z, f in ((z0 - 1, 'south'), (z1 + 1, 'north')):
            for k in (0, 1):
                w.set((x, under + k, z), 'minecraft:chiseled_stone_bricks' if (x + k) % 2 == 0 else
                      'minecraft:polished_andesite')
            if under + 2 < DECK_Y:
                w.set((x, under + 2, z), 'minecraft:stone_brick_stairs[facing=%s,half=top,shape=straight,'
                      'waterlogged=false]' % f)
    # piers on the parapet every 4, each carrying a lantern
    for x in range(dx0 + 1, dx1, 4):
        for z in (z0 - 1, z1 + 1):
            w.set((x, DECK_Y + 1, z), 'minecraft:stone_bricks')
            w.set((x, DECK_Y + 2, z), 'minecraft:stone_brick_wall')
            w.set((x, DECK_Y + 3, z), 'minecraft:stone_brick_wall')
            w.set((x, DECK_Y + 4, z), 'minecraft:lantern[hanging=false,waterlogged=false]')
    # a small gate arch at the castle end, to match the watch tower at the west end
    gx = dx1
    for z in (z0 - 2, z0 - 1, z1 + 1, z1 + 2):
        for x in (gx - 1, gx):
            for y in range(GT + 1, GT + 8):
                w.set((x, y, z), masonry(x, y, z))
    for z in range(z0 - 2, z1 + 3):
        for x in (gx - 1, gx):
            w.set((x, GT + 6, z), masonry(x, GT + 6, z))
            w.set((x, GT + 7, z), 'minecraft:stone_brick_slab[type=bottom,waterlogged=false]' if z in (z0 - 2, z1 + 2)
                  else masonry(x, GT + 7, z))
        for x in (gx - 1, gx):
            if z0 <= z <= z1:
                w.set((x, GT + 5, z), 'minecraft:stone_brick_stairs[facing=%s,half=top,shape=straight,'
                      'waterlogged=false]' % ('south' if z == z0 else 'north' if z == z1 else 'east'))
                if z == (z0 + z1) // 2:
                    w.set((x, GT + 5, z), None)
    for x in (gx - 1, gx):
        for z in range(z0, z1 + 1):
            for y in range(GT + 1, GT + 5):
                w.set((x, y, z), None)
    for z in (z0 - 2, z1 + 2):
        w.set((gx + 1, GT + 4, z), 'minecraft:lantern[hanging=false,waterlogged=false]')
        w.set((gx + 1, GT + 3, z), 'minecraft:stone_brick_wall')
        w.set((gx + 1, GT + 2, z), 'minecraft:stone_brick_wall')
        w.set((gx + 1, GT + 1, z), 'minecraft:stone_bricks')
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
            elif d <= r + 1.15:
                # the machicolation: upside-down stairs corbelled out under the parapet, the parapet on them
                dx_, dz_ = x + 0.5 - tx, z + 0.5 - tz
                if abs(dx_) >= abs(dz_):
                    f = 'west' if dx_ > 0 else 'east'
                else:
                    f = 'north' if dz_ > 0 else 'south'
                w.set((x, top, z), 'minecraft:stone_brick_stairs[facing=%s,half=top,shape=straight,waterlogged=false]'
                      % f)
                w.set((x, top + 1, z), masonry(x, top + 1, z))
                if (x + z) % 2 == 0:
                    w.set((x, top + 2, z), masonry(x, top + 2, z))
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
    # arrow slits: two up each side
    for y in (G + 6, G + 7, G + 11, G + 12):
        for (sx, sz) in ((int(tx), int(tz - r)), (int(tx), int(math.floor(tz + r))), (int(math.floor(tx - r)), int(tz)),
                         (int(math.floor(tx + r)), int(tz))):
            if y <= G + 2:
                continue
            w.set((sx, y, sz), None)


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
    """A conifer with a log spine to its tip. Leaves are see-through, so the cone is built backed (leaf_backing)."""
    top = y0 + height
    for y in range(y0, top + 2):
        w.set((x, y, z), 'minecraft:spruce_log[axis=y]')
    for p in LB.spruce_cone(x, z, y0 + 2, top):
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
    build_relief(w)
    floor_slabs(w, H)
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


def fa(lx, y, lz):
    """Arena-local point with a floor-relative y to a scene point."""
    return la(lx, y + AF, lz)


def stands_anchor():
    out = []
    for sd in STAND_DEFS:
        rows = []
        for (s0, s1, nrows) in SECTIONS[sd[0]]:
            for k in range(nrows):
                _, outer = row_cells(sd, k)
                seat_top = row_y(k) + 1.5
                run = []
                for a in range(s0, s1 + 2):
                    ok = a <= s1 and a not in AISLES[sd[0]] and not in_box(sd, a, k)
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
    return {'stand': la(lx, row_y(k0) + 1, lz + 0.5), 'yaw': sd[5], 'look': fa(NEST_LC[0], 2.0, NEST_LC[1])}


def bridge_anchor():
    z0, z1 = BRIDGE_Z
    c0, c1 = BR['centre']
    crown = int(math.floor(arch_crown(c0, c1, SPRING_C)))
    bx = (c0, c1 + 1)
    return {
        'span_box': [[BR['deck'][0], SPRING_C, z0 - 1], [BR['deck'][1] + 1, DECK_Y + 5, z1 + 2]],
        'break_box': [[bx[0], crown - 2, z0 - 1], [bx[1], DECK_Y + 5, z1 + 2]],
        'underside_y': float(crown),
        'deck_y': float(DECK_Y + 1),
        'arch': {'x1': c0, 'x2': c1 + 1, 'spring_y': SPRING_C, 'crown_y': crown},
        'fall_column': [[BR['cx'] - 4, GORGE_FLOOR + 1, z0 - 3], [BR['cx'] + 5, crown - 3, z1 + 4]],
    }


NEST_SC = fa(NEST_LC[0], 0.0, NEST_LC[1])
ANCHORS = {
    'ground_y': float(AF),
    'plateau_y': float(G),
    'nest_centre': NEST_SC,
    'nest_perch': {'pos': fa(10.0, 2.0, 0.5), 'yaw': 90.0,
                   'shelf': [fa(SHELF[0], 2.0, SHELF[2]), fa(SHELF[1] + 1, 2.0, SHELF[3] + 1)]},
    'egg_spot': fa(PRIZE[0] + 0.5, PRIZE_TOP + 0.18, PRIZE[1] + 0.2),
    'eggs': [fa(lx + 0.5, -0.5, lz + 0.5) for (lx, lz) in EGG_CELLS + [GOLD_CELL]],
    'entrance': {'pos': fa(MOUTH + 1.5, 0.0, 0.5), 'yaw': -90.0, 'inside': fa(TUNNEL_END + 6.5, 0.0, 0.5)},
    'hide_rock': {'spot': fa(HIDE[0] - 2.2, 0.0, 7.5), 'face_centre': fa(HIDE[1] + 1.0, 1.5, 7.5),
                  'face_normal': [1.0, 0.0, 0.0],
                  'face_box': [fa(HIDE[1] + 1.0, 0.0, HIDE[2]), fa(HIDE[1] + 1.0, 3.0, HIDE[3] + 1)],
                  'top_y': float(AF + 4)},
    'stake': fa(STAKE_LC[0] + 0.5, 2.6, STAKE_LC[1] + 0.5),
    'cannon_spots': cannons_anchor(),
    'stands': stands_anchor(),
    'commentator_box': booth_anchor(),
    'broom_lane': [la(120, 70, -150), la(70, 62, -100), la(46, 48, -52), la(26, 26, -22), fa(-2, 18, -8),
                   fa(-14, 8, 2), fa(-20.0, 2.2, 6.8)],
    'arena_air_box': [fa(-22, 6, -14), fa(22, 33, 14)],
    'launch_lane': [fa(9, 6, 0.5), fa(16, 18, -6), la(30, 34, -22), la(52, 46, -46), la(84, 52, -78)],
    'castle_route': [],                 # filled in by plan_routes() below
    'gorge_lane': [],
    'slate_roofs': [],
    'bridge': bridge_anchor(),
    'castle_view': {'pos': la(48, 40, -44), 'look': _p(76, 60, -28)},
    'arena_view': {'pos': la(-48, 74, 56), 'look': fa(5, 2, -2)},
    'gorge_view': {'pos': _p(BR['cx'] + 0.5, 2.0, -48.0), 'look': _p(BR['cx'] + 0.5, 4.0, -20.0)},
    'castle_box': [list(CASTLE_AT), [CASTLE_FOOT[1] + 1, CASTLE_TOP_Y + 1, CASTLE_FOOT[3] + 1]],
    'RADIUS': RADIUS,
}


# ---------------------------------------------------------------- routes and the roofs the tail smashes
CASTLE_ROUTE = [[14, 46, 8], [32, 58, 8], [46, 82, 10], [60, 99, 2], [78, 90, -10], [86, 72, -34], [102, 62, -40],
                [118, 58, -50], [116, 54, -68], [110, 52, -86], [94, 56, -104], [62, 50, -110], [30, 44, -100], [10, 36, -92]]
GORGE_LANE_Z = [-92, -78, -62, -46, -32, -20, -8, 4, 16, 28, 40, 52, 62]
GORGE_LANE_Y = [30, 14, 2, -4, -5, -5, -5, -5, -4, -3, 0, 6, 18]


def gorge_lane():
    pts = [[round(gorge_cx(z) + 0.5, 3), float(y), float(z)] for z, y in zip(GORGE_LANE_Z, GORGE_LANE_Y)]
    pts.append([-6.0, 30.0, 58.0])                          # out over the head's west rim, back toward the arena
    pts.append([-30.0, 36.0, 44.0])
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
                # nothing (a canopy, a banner, a beam) within 3.5 over the seat top
                for dy in (1, 2, 3):
                    if ids.get((c[0], c[1] + dy, c[2])) not in PASS:
                        problems.append('seat %s of %s: %s within 3.5 above' % (c, st['name'],
                                                                                ids.get((c[0], c[1] + dy, c[2]))))
                        break
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
    if ids.get(ec) is not None or ids.get((ec[0], ec[1] - 1, ec[2])) not in ROCK_IDS:
        problems.append('egg_spot %s: cell %s, under %s' % (ec, ids.get(ec), ids.get((ec[0], ec[1] - 1, ec[2]))))
    for e in a['eggs']:
        c = cell_of(e)
        if ids.get(c) != 'minecraft:dragon_egg' or ids.get((c[0], c[1] - 1, c[2])) not in ROCK_IDS:
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
    eye = [sp[0], AF + 1.0, sp[2]]
    head = [a['nest_perch']['pos'][0] - 6.0, AF + 4.5, a['nest_perch']['pos'][2]]
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
    problems += LB.problems(g, 'first_task_grounds')       # leaves are see-through: every one backed (leaf_backing)
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
    LB.assert_bites(w.g)                                   # the mutation bite: a spine log out and the check fails
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
