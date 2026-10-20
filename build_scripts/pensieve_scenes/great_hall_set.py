"""Builds the Great Hall memory set: structure/great_hall.nbt from the user-supplied hall plus the edits the Sorting
memory needs, and the scene anchors great_hall_scene.py stages it with.

    python -I build_scripts/pensieve_scenes/great_hall_set.py

Input: source/great_hall.schem (Sponge v2, 89 x 57 x 45, already 1.21.1-safe). The template keeps the schem's own
local frame (x east, the hall's long axis; staff end at low x; floor block y 0), grown east to 114 for the entrance
hall. Its licence is unknown: keep it on the sorting-memory branch for the user's own use.

Edits:
  1. The doorway. The east wall becomes a layered Gothic portal on the hall axis (z 22, centre line z 22.5):
     an 11-wide stone order (x 86) and a 9-wide stone order (x 85) round a 7-wide sandstone stop (x 84) with a
     carved tympanum, and an 11-wide sandstone pocket behind it on the hall side (x 83, x 82) where the two
     memory_door leaves (scale 4: 4 wide, 12 tall, 0.8 thick) hang and swing into the hall. The opening is empty.
  2. The entrance hall, x 86..111 by z 7..37 (31 wide), 22 tall under a ribbed stone vault: stone-brick walls with
     pilasters, wall torches, hanging lanterns on chains, a polished andesite floor with a deepslate-tile runner, an
     imperial staircase against the far (east) wall, two tall dark lancet windows and the four house banners.
  3. The sorting stool (a spruce slab, seat top y 2.5) on the top step at the head of the centre aisle, in front of
     the lectern, lit by light blocks; the builder's own off-axis stool prop on the dais (x 15, z 19) is removed.
  4. Lighting: every candle lit, light blocks over the tables.
  5. The enchanted ceiling plane sits at local y 21 (wall tops); nothing below it is removed (the roof trusses all
     start at y 21 or higher, so no hanging post reaches below the plane).
  6. Physics: unsupported sand becomes sandstone; physics_problems must be empty.
"""
import gzip
import io
import math
import os
import random
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import read_nbt, physics_problems, FREE, STRUCTURES  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, 'source', 'great_hall.schem')
OUT = os.path.join(STRUCTURES, 'great_hall.nbt')
DATA_VERSION = 3953

# Scene placement: the template corner goes at TEMPLATE_AT, so local (x, y, z) is scene (x, y, z) + TEMPLATE_AT.
# The stool (local 19, 2, 22) is at scene x/z 0: the hall floor block layer is scene y -1, so actors on the hall
# floor stand at scene y 0 (the dais and the stool's step are one block higher).
TEMPLATE_AT = (-19, -1, -22)
SIZE = (114, 57, 45)
RADIUS = 100

AXIS_Z = 22                      # the hall's centre-aisle cell row; the centre line is z 22.5
STOOL = (19, 2, 22)              # spruce slab on the top step in front of the lectern, seat top at local y 2.5
DOOR_SCALE = 4.0                 # memory_door leaf: 4 wide, 12 tall, 0.8 thick
DOOR_PLANE_X = 84.0              # the stop's hall-side face; the closed leaves hang on its west (hall) side
DOOR_HINGES = ((84.0, 1.0, 18.5), (84.0, 1.0, 26.5))     # north leaf (z 18.5..22.5), south leaf (22.5..26.5)

# entrance hall box (local)
EH_X0, EH_X1 = 86, 111           # interior x
EH_Z0, EH_Z1 = 7, 37             # interior z (31 wide, centred on z 22.5)
EH_WALL_TOP = 14                 # side walls are plain up to here, the vault springs above
EH_VAULT_TOP = 25


def sc(x, y, z):
    """Local template coordinates to scene coordinates."""
    return [x + TEMPLATE_AT[0], y + TEMPLATE_AT[1], z + TEMPLATE_AT[2]]


# ---------------------------------------------------------------- anchors (scene coordinates)
# Standing spots are feet positions [x, y, z]. Seats are [x, seat_top_y, z, yaw]: a seated actor's root goes at
# seat_top - 12/16 * scale (sorting.py SEAT_Y) with the `seated` pose; yaw follows Minecraft (90 faces -X/west,
# -90 faces +X/east, 0 faces +Z/south, 180 faces -Z/north).

def _first_years():
    """12 spots in three loose rows of four in front of the doors, facing them (yaw 90)."""
    rnd = random.Random(1991)
    out = []
    for row, x in enumerate((89.6, 91.0, 92.4)):
        for col, z in enumerate((20.3, 21.7, 23.3, 24.7)):
            jx = round(rnd.uniform(-0.2, 0.2), 2)
            jz = round(rnd.uniform(-0.15, 0.15), 2)
            p = sc(x + jx, 1, z + jz)
            out.append([round(p[0], 2), p[1], round(p[2], 2)])
    return out


# staff chairs along the high table (x 5, seat top 2.5), facing the hall; the throne (x 4) is Dumbledore's
_STAFF_Z = [21, 23, 20, 24, 19, 25, 18, 26, 17, 27, 16]
STAFF_CHAIR_Z = (16, 17, 18, 19, 20, 21, 23, 24, 25, 26, 27, 28)


def _staff_seats():
    seats = [sc(4.5, 2.5, 22.5) + [-90]]
    for z in _STAFF_Z:
        seats.append(sc(5.5, 2.5, z + 0.5) + [-90])
    return seats


# house tables: slab benches at the north row (z_lo, facing south) and south row (z_hi, facing north), x 25..73
TABLES = {'hufflepuff': (8, 12), 'gryffindor': (16, 20), 'ravenclaw': (24, 28), 'slytherin': (32, 36)}
BENCH_X = (25, 73)


def _table_seats():
    out = {}
    for house, (z_lo, z_hi) in TABLES.items():
        seats = []
        for x in range(BENCH_X[0], BENCH_X[1] + 1):
            seats.append(sc(x + 0.5, 1.5, z_lo + 0.5) + [0])
            seats.append(sc(x + 0.5, 1.5, z_hi + 0.5) + [180])
        out[house] = seats
    return out


# ceiling plane: the hall interior x 10..83, z 5..40 at local y 21
CEIL_X = (10.0, 83.0)
CEIL_Z = (5.0, 40.0)
CEIL_Y = 21

ANCHORS = {
    'doors_hinge_n': sc(*DOOR_HINGES[0]),
    'doors_hinge_s': sc(*DOOR_HINGES[1]),
    'door_leaf_scale': DOOR_SCALE,
    # Minecraft yaw for each leaf, closed and fully open (swung into the hall), with the plan's convention that
    # the free edge points along local +X: yaw 0 = +X, yaw -90 = +Z
    'door_yaw_n': {'closed': -90, 'open': -180},
    'door_yaw_s': {'closed': 90, 'open': 180},
    'entrance_floor_center': sc(92.5, 1, 22.5),
    'first_year_spots': _first_years(),
    'mcgonagall_entrance': sc(87.5, 1, 22.5),
    'aisle_start': sc(79.5, 1, 22.5),
    'aisle_end': sc(24.5, 1, 22.5),
    'stool': sc(STOOL[0] + 0.5, STOOL[1] + 0.5, STOOL[2] + 0.5),
    'stool_front': sc(21.5, 1, 22.5),
    'mcgonagall_stool': sc(19.5, 2, 20.5),
    'staff_seats': _staff_seats(),
    'table_seats': _table_seats(),
    'ceiling_center': sc((CEIL_X[0] + CEIL_X[1]) / 2, CEIL_Y, (CEIL_Z[0] + CEIL_Z[1]) / 2),
    'ceiling_spread': [(CEIL_X[1] - CEIL_X[0]) / 2, 0, (CEIL_Z[1] - CEIL_Z[0]) / 2],
    'RADIUS': RADIUS,
}


# ---------------------------------------------------------------- block state helpers
def state(name, **props):
    """'minecraft:name[k=v,...]' with sorted properties, the form the grid stores."""
    if ':' not in name:
        name = 'minecraft:' + name
    if not props:
        return name
    return name + '[' + ','.join('%s=%s' % (k, str(v).lower()) for k, v in sorted(props.items())) + ']'


def parse_state(s):
    if '[' not in s:
        return s, {}
    name, rest = s.split('[', 1)
    props = dict(kv.split('=', 1) for kv in rest.rstrip(']').split(',') if kv)
    return name, props


def bid(s):
    return None if s is None else s.split('[', 1)[0]


def read_schem(path):
    root = read_nbt(gzip.open(path).read())
    s = root.get('Schematic', root)
    w, h, l = s['Width'] & 0xFFFF, s['Height'] & 0xFFFF, s['Length'] & 0xFFFF
    pal = {v: k for k, v in s['Palette'].items()}
    data = s['BlockData']
    grid, i, idx = {}, 0, 0
    while i < len(data) and idx < w * h * l:
        v = sh = 0
        while True:
            c = data[i] & 0xFF
            i += 1
            v |= (c & 0x7F) << sh
            if not c & 0x80:
                break
            sh += 7
        b = pal[v]
        if b != 'minecraft:air':
            x, z, y = idx % w, (idx // w) % l, idx // (w * l)
            name, props = parse_state(b)
            grid[(x, y, z)] = state(name, **props)
        idx += 1
    return (w, h, l), grid


# ---------------------------------------------------------------- shapes
def arch_open(dz, dy, half, ratio=1.5):
    """Whether a cell is inside a two-centred pointed arch of half-span `half`. dz/dy are the cell centre's offsets
    from the axis and from the springing line; each arc has radius ratio*half, centred on the far side of the axis."""
    if dy < 0:
        return abs(dz) < half
    r = ratio * half
    return (abs(dz) + (r - half)) ** 2 + dy ** 2 <= r * r


def vault_underside(dz):
    """Underside height of the entrance hall vault at |dz| from its centre line (z 22.5)."""
    a = (EH_Z1 - EH_Z0 + 1) / 2.0
    t = min(1.0, abs(dz) / a)
    return EH_WALL_TOP + 1 + 8.6 * math.sqrt(max(0.0, 1 - t * t))


class Builder:
    def __init__(self, grid):
        self.g = grid
        self.rnd = random.Random(1991)

    def get(self, p):
        return self.g.get(p)

    def set(self, p, s):
        if s is None or s == 'minecraft:air':
            self.g.pop(p, None)
        else:
            self.g[p] = s

    def fill(self, x0, y0, z0, x1, y1, z1, s):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set((x, y, z), s() if callable(s) else s)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, None)


def stone_mix(rnd):
    return 'minecraft:cracked_stone_bricks' if rnd.random() < 0.10 else 'minecraft:stone_bricks'


# ---------------------------------------------------------------- edits
def edit_doorway(b):
    """The east wall: the hall-side pocket, the sandstone stop and the two stone orders of the portal."""
    g = b.g
    # drop the old porch and everything east of the hall wall; the entrance hall is rebuilt there
    for p in [p for p in g if p[0] >= 85]:
        del g[p]
    # hall side: open the inner sandstone frame (x 82) to 11 wide below its head so a swung leaf clears it
    for y in range(1, 15):
        for z in (17, 27):
            b.set((82, y, z), None)
    # the pocket the leaves hang in and swing out of (x 83), 11 wide, as tall as a leaf
    b.clear(83, 1, 17, 83, 12, 27)
    # lintel over the pocket, cut sandstone with a gold keystone line (seen from the hall through the frame)
    b.fill(83, 13, 17, 83, 14, 27, 'minecraft:smooth_sandstone')
    b.fill(83, 13, 18, 83, 13, 26, 'minecraft:cut_sandstone')
    b.set((83, 14, 22), 'minecraft:gold_block')
    # gold trim on the pocket jambs (hall face), as the old doorway had gold pillars
    for y in range(1, 13):
        b.set((83, y, 16), 'minecraft:gold_block' if y in (1, 12) else 'minecraft:cut_sandstone')
        b.set((83, y, 28), 'minecraft:gold_block' if y in (1, 12) else 'minecraft:cut_sandstone')
    # the stop (x 84): solid sandstone with a 7-wide, 12-tall rectangular opening; the leaves cover 18.5..26.5
    b.fill(84, 1, 18, 84, 14, 26, 'minecraft:smooth_sandstone')
    b.clear(84, 1, 19, 84, 12, 25)
    # a dark oak casing round the opening: it reads as the doors' own frame from both sides
    for y in range(1, 13):
        b.set((84, y, 18), state('stripped_dark_oak_wood', axis='y'))
        b.set((84, y, 26), state('stripped_dark_oak_wood', axis='y'))
    # floor threshold under the doors
    for x in (82, 83, 84):
        for z in range(17, 28):
            b.set((x, 0, z), 'minecraft:smooth_sandstone' if 19 <= z <= 25 else 'minecraft:cut_sandstone')
    b.fill(84, 0, 19, 84, 0, 25, 'minecraft:polished_deepslate')

    # x 84 tympanum above the doors, seen inside the 9-wide arch of x 85: dark oak panelling, a gold boss
    # (planks, with stripped-log mullions radiating like tracery) and a gold boss under the apex
    for y in range(13, 22):
        for z in range(18, 27):
            if arch_open(z - AXIS_Z, y - 13 + 0.5, 4.5):
                dz = z - AXIS_Z
                mull = dz == 0 or (abs(dz) == 2 and y <= 17)
                b.set((84, y, z), state('stripped_dark_oak_wood', axis='y') if mull else 'minecraft:dark_oak_planks')
    b.fill(84, 13, 18, 84, 13, 26, state('stripped_dark_oak_wood', axis='z'))   # the lintel over the leaves
    b.set((84, 16, 22), 'minecraft:gold_block')


def edit_entrance_hall(b):
    g, rnd = b.g, b.rnd
    X0, X1, Z0, Z1 = EH_X0, EH_X1, EH_Z0, EH_Z1
    CZ = AXIS_Z + 0.5

    # shell: floor, walls (2 thick on the sides, 2 at the east end, x 85 on the west), vault
    b.fill(85, 0, Z0 - 2, X1 + 2, 0, Z1 + 2, 'minecraft:stone_bricks')
    for x in range(85, X1 + 3):
        for z in range(Z0 - 2, Z1 + 3):
            inside = X0 <= x <= X1 and Z0 <= z <= Z1
            for y in range(1, EH_VAULT_TOP + 1):
                if not inside:
                    b.set((x, y, z), stone_mix(rnd))
                elif y + 0.5 > vault_underside(z + 0.5 - CZ):
                    b.set((x, y, z), stone_mix(rnd))

    # floor: polished andesite flags with a slightly darker andesite joint grid and a deepslate-tile runner
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            s = 'minecraft:polished_andesite'
            if (x - X0) % 5 == 0 or (z - AXIS_Z) % 5 == 0:
                s = 'minecraft:andesite'
            if 20 <= z <= 24:
                s = 'minecraft:deepslate_tiles' if 21 <= z <= 23 else 'minecraft:polished_deepslate'
            b.set((x, 0, z), s)

    # base course and string course
    for x in range(X0, X1 + 1):
        for z in (Z0 - 1, Z1 + 1):
            b.set((x, 1, z), 'minecraft:polished_andesite')
            b.set((x, EH_WALL_TOP, z), 'minecraft:polished_andesite')
    for z in range(Z0, Z1 + 1):
        b.set((X1 + 1, 1, z), 'minecraft:polished_andesite')
        b.set((85, 1, z), 'minecraft:polished_andesite')

    # pilasters and transverse vault ribs every 5 blocks
    for x in (88, 93, 98, 103, 108):
        for z, face in ((Z0, 'south'), (Z1, 'north')):
            for y in range(1, EH_WALL_TOP + 1):
                b.set((x, y, z), 'minecraft:polished_andesite' if y in (1, EH_WALL_TOP) else 'minecraft:stone_bricks')
            b.set((x, EH_WALL_TOP + 1, z), state('stone_brick_stairs', facing='north' if face == 'south' else 'south',
                                                 half='top', shape='straight', waterlogged=False))
        for z in range(Z0, Z1 + 1):
            h = vault_underside(z + 0.5 - CZ)
            y = int(math.ceil(h - 0.5)) - 1
            b.set((x, y, z), 'minecraft:polished_andesite')

    # the portal frontispiece on the doors' wall (x 86): pilasters, the 11-wide order, a gable face
    for z in range(14, 31):
        for y in range(1, 22):
            dz = z - AXIS_Z
            if abs(dz) >= 7:
                continue
            if y <= 12 and abs(dz) <= 5:
                continue
            if y >= 13 and arch_open(dz, y - 13 + 0.5, 5.5):
                continue
            s = 'minecraft:stone_bricks'
            if abs(dz) == 6:
                s = 'minecraft:polished_andesite'
            b.set((86, y, z), s)
    # order trims: chiseled springers and keystone
    for z in (16, 28):
        b.set((86, 12, z), 'minecraft:chiseled_stone_bricks')
        b.set((86, 13, z), 'minecraft:chiseled_stone_bricks')
    # 9-wide order in the west wall (x 85)
    for z in range(Z0, Z1 + 1):
        for y in range(1, 22):
            dz = z - AXIS_Z
            if y <= 12 and abs(dz) <= 4:
                b.set((85, y, z), None)
            elif y >= 13 and arch_open(dz, y - 13 + 0.5, 4.5):
                b.set((85, y, z), None)
    # arch voussoirs: ring of polished andesite one cell outside each opening, on the faces
    for x, half in ((85, 4.5), (86, 5.5)):
        for z in range(14, 31):
            for y in range(13, 23):
                dz = z - AXIS_Z
                if arch_open(dz, y - 13 + 0.5, half):
                    continue
                if any(arch_open(dz + ddz, y + ddy - 13 + 0.5, half) for ddz, ddy in ((1, 0), (-1, 0), (0, -1))):
                    if (x, y, z) in g:
                        b.set((x, y, z), 'minecraft:polished_andesite')
    b.set((86, 21, AXIS_Z), 'minecraft:chiseled_stone_bricks')
    # cap the frontispiece with a stair cornice
    for z in range(16, 30):
        b.set((86, 22, z), state('stone_brick_stairs', facing='west', half='bottom', shape='straight', waterlogged=False))
    for z in (15, 16, 28, 29):
        b.set((86, 22, z), 'minecraft:polished_andesite')
        b.set((86, 23, z), state('stone_brick_wall', east='none', north='none', south='none', west='none', up=True,
                                 waterlogged=False))

    # imperial staircase: a central flight rising east to a landing, two flights rising back west along the walls
    STAIR_Z = (18, 27)
    for k in range(7):                                   # x 99..105, top cell y 1..7
        x = 99 + k
        for z in range(STAIR_Z[0], STAIR_Z[1] + 1):
            for y in range(1, k + 1):
                b.set((x, y, z), 'minecraft:stone_bricks')
            b.set((x, k + 1, z), state('polished_andesite_stairs', facing='east', half='bottom', shape='straight',
                                       waterlogged=False))
        for z in (STAIR_Z[0] - 1, STAIR_Z[1] + 1):       # stringers with balustrade posts
            for y in range(1, k + 2):
                b.set((x, y, z), 'minecraft:polished_andesite')
            b.set((x, k + 2, z), state('stone_brick_wall', east='low', west='low', north='none', south='none', up=k % 3 == 0,
                                       waterlogged=False))
    LAND_X = (106, X1)
    b.fill(LAND_X[0], 1, Z0, LAND_X[1], 7, Z1, 'minecraft:stone_bricks')
    for x in range(LAND_X[0], LAND_X[1] + 1):
        for z in range(Z0, Z1 + 1):
            b.set((x, 7, z), 'minecraft:polished_andesite')
    # landing front edge: a moulding row and a balustrade either side of the central flight
    for z in range(Z0, Z1 + 1):
        if STAIR_Z[0] - 1 <= z <= STAIR_Z[1] + 1:
            continue
        b.set((LAND_X[0], 8, z), state('stone_brick_wall', east='none', west='none', north='low', south='low',
                                       up=z % 3 == 0, waterlogged=False))
    # side flights: 5 wide along each wall, rising west from the landing (y 8) to upper landings at y 14
    for side, zs in (('n', range(Z0, Z0 + 5)), ('s', range(Z1 - 4, Z1 + 1))):
        for k in range(7):                               # x 105..99 -> top cell y 8..14
            x = 105 - k
            top = 8 + k
            for z in zs:
                for y in range(1, top):
                    b.set((x, y, z), 'minecraft:stone_bricks')
                b.set((x, top, z), state('polished_andesite_stairs', facing='west', half='bottom', shape='straight',
                                         waterlogged=False))
            inner = (Z0 + 5) if side == 'n' else (Z1 - 5)
            for y in range(1, top + 1):
                b.set((x, y, inner), 'minecraft:polished_andesite' if y >= top - 1 else stone_mix(rnd))
            b.set((x, top + 1, inner), state('stone_brick_wall', north='low', south='low', east='none', west='none',
                                             up=k % 3 == 0, waterlogged=False))
        # upper landing x 95..98 and a dark archway in the side wall
        for x in range(95, 99):
            for z in zs:
                for y in range(1, 15):
                    b.set((x, y, z), 'minecraft:stone_bricks')
                b.set((x, 14, z), 'minecraft:polished_andesite')
            inner = (Z0 + 5) if side == 'n' else (Z1 - 5)
            for y in range(1, 15):
                b.set((x, y, inner), 'minecraft:polished_andesite' if y >= 13 else stone_mix(rnd))
            b.set((x, 15, inner), state('stone_brick_wall', north='low', south='low', east='low' if x < 98 else 'none',
                                        west='low' if x > 95 else 'none', up=x in (95, 98), waterlogged=False))
        wall_z = Z0 - 1 if side == 'n' else Z1 + 1
        for x in range(95, 99):
            for y in range(15, 20):
                dz = x - 96.5
                if y <= 17 or arch_open(dz, y - 18 + 0.5, 2.0):
                    b.set((x, y, wall_z), 'minecraft:black_concrete')
        # the masses under the flights read as built, not solid: blind pointed niches on the inner face and a
        # dark arched passage in the front face (x 95)
        inner = (Z0 + 5) if side == 'n' else (Z1 - 5)
        back = 1 if side == 'n' else -1
        for x0, spring, top in ((100, 5, 9), (95, 8, 12)):
            for x in range(x0, x0 + 3):
                for y in range(1, top):
                    if y < spring or arch_open(x - (x0 + 1), y - spring + 0.5, 1.5):
                        b.set((x, y, inner), None)
                        b.set((x, y, inner - back), 'minecraft:polished_andesite' if y == 1 else 'minecraft:stone_bricks')
        zc = (Z0 + 2) if side == 'n' else (Z1 - 2)
        for z in range(zc - 1, zc + 2):
            for y in range(1, 9):
                if y < 6 or arch_open(z - zc, y - 6 + 0.5, 1.5):
                    b.set((95, y, z), None)
                    b.set((96, y, z), None)
                    b.set((97, y, z), 'minecraft:black_concrete')

    # far wall: two tall lancet windows above the landing, dark night glass with iron tracery
    for zc in (13, 32):
        for z in range(zc - 2, zc + 3):
            for y in range(9, 23):
                dz = z - zc
                if y <= 17 or arch_open(dz, y - 18 + 0.5, 2.5):
                    b.set((X1 + 1, y, z), None)
                    b.set((X1 + 2, y, z), 'minecraft:black_stained_glass')
        for y in range(9, 22):
            if (X1 + 1, y, zc) not in g:
                b.set((X1 + 1, y, zc), state('iron_bars', north=True, south=True, east=False, west=False,
                                             waterlogged=False) if y in (13, 17) else
                      state('iron_bars', north=False, south=False, east=False, west=False, waterlogged=False))
        for z in range(zc - 2, zc + 3):
            if z != zc:
                for y in (13, 17):
                    b.set((X1 + 1, y, z), state('iron_bars', north=True, south=True, east=False, west=False,
                                                waterlogged=False))
        # sill
        for z in range(zc - 3, zc + 4):
            b.set((X1, 8, z), state('stone_brick_slab', type='bottom', waterlogged=False))

    # house banners on the far wall between the windows, two-high (about 4 blocks of cloth)
    for z, colour in ((19, 'red'), (21, 'yellow'), (24, 'blue'), (26, 'green')):
        for y in (13, 15):
            b.set((X1, y, z), state(colour + '_wall_banner', facing='west'))
    # and one long pair each side of the doors, on the west wall
    for z, colour in ((10, 'red'), (12, 'yellow'), (33, 'blue'), (35, 'green')):
        for y in (8, 10):
            b.set((X0, y, z), state(colour + '_wall_banner', facing='east'))

    # torches in brackets between the pilasters, at two heights on the side walls; flanking the doors
    for x in (90, 96):
        for z, face in ((Z0, 'south'), (Z1, 'north')):
            b.set((x, 5, z), state('wall_torch', facing=face))
            b.set((x, 11, z), state('wall_torch', facing=face))
    for x in (101, 106, 110):
        for z, face in ((Z0, 'south'), (Z1, 'north')):
            y = 17 if x <= 105 else 11
            if (x, y, z) not in g:
                b.set((x, y, z), state('wall_torch', facing=face))
    for z in (16, 28):
        b.set((87, 6, z), state('wall_torch', facing='east'))
        b.set((87, 10, z), state('wall_torch', facing='east'))

    # lanterns hanging on chains from the vault ribs over the floor
    for x in (90, 96):
        for z in (14, 31):
            top = int(math.ceil(vault_underside(z + 0.5 - CZ) - 0.5)) - 1
            low = 11
            for y in range(low + 1, top + 1):
                b.set((x, y, z), state('chain', axis='y', waterlogged=False))
            b.set((x, low, z), state('lantern', hanging=True, waterlogged=False))

    # light so faces read: soft fill over the waiting floor, brighter by the doors
    for x in range(87, 99, 4):
        for z in range(9, 37, 5):
            if (x, 4, z) not in g:
                b.set((x, 4, z), state('light', level=11, waterlogged=False))
    for z in (19, 22, 25):
        b.set((88, 6, z), state('light', level=13, waterlogged=False))
    for x in (100, 104):
        for z in (12, 33):
            p = (x, 17, z)
            if p not in g:
                b.set(p, state('light', level=10, waterlogged=False))


def edit_stool_and_staff(b):
    g = b.g
    sx, sy, sz = STOOL
    assert (sx, sy, sz) not in g, g.get((sx, sy, sz))
    b.set(STOOL, state('spruce_slab', type='bottom', waterlogged=False))
    b.set((15, 2, 19), None)                             # the builder's scaffolding-and-stair stool prop
    b.set((15, 3, 19), None)
    for p in ((sx + 1, 4, sz - 2), (sx + 1, 4, sz + 2), (sx + 3, 4, sz - 2), (sx + 3, 4, sz + 2), (sx + 2, 5, sz)):
        if p not in g:
            b.set(p, state('light', level=15, waterlogged=False))
    # complete the high table's chairs: a seat at every z 16..28 bar the throne's (22)
    for z in STAFF_CHAIR_Z:
        cur = g.get((5, 2, z))
        if cur is None:
            b.set((5, 2, z), state('dark_prismarine_stairs', facing='west', half='bottom', shape='straight',
                                   waterlogged=False))
        if z not in (21, 23) and (4, 2, z) not in g and (4, 3, z) not in g:
            hinge = 'right' if z < 22 else 'left'
            b.set((4, 2, z), state('dark_oak_door', facing='west', half='lower', hinge=hinge, open=False,
                                   powered=False))
            b.set((4, 3, z), state('dark_oak_door', facing='west', half='upper', hinge=hinge, open=False,
                                   powered=False))


def edit_lighting(b):
    g = b.g
    for p, s in list(g.items()):
        if bid(s) == 'minecraft:candle':
            n, props = parse_state(s)
            props['lit'] = 'true'
            g[p] = state(n, **props)
    for (z_lo, z_hi) in TABLES.values():
        zc = (z_lo + z_hi) // 2
        for x in range(26, 74, 6):
            p = (x, 6, zc)
            if p not in g:
                b.set(p, state('light', level=14, waterlogged=False))
    # the aisle and the dais
    for x in range(23, 80, 6):
        p = (x, 6, AXIS_Z)
        if p not in g:
            b.set(p, state('light', level=12, waterlogged=False))
    for z in range(14, 32, 4):
        p = (8, 6, z)
        if p not in g:
            b.set(p, state('light', level=13, waterlogged=False))


def fix_physics(b):
    """Unsupported sand (free cell under it) becomes sandstone until none is left."""
    g, n = b.g, 0
    while True:
        bad = [p for p, s in g.items() if bid(s) == 'minecraft:sand' and bid(g.get((p[0], p[1] - 1, p[2]))) in FREE]
        if not bad:
            return n
        for p in bad:
            g[p] = 'minecraft:sandstone'
        n += len(bad)


# ---------------------------------------------------------------- NBT writing
def _w_str(out, s):
    b = s.encode('utf-8')
    out.write(struct.pack('>H', len(b)))
    out.write(b)


def _w_payload(out, tag, v):
    if tag == 3:
        out.write(struct.pack('>i', v))
    elif tag == 8:
        _w_str(out, v)
    elif tag == 9:
        et, items = v
        out.write(struct.pack('>bi', et, len(items)))
        for it in items:
            _w_payload(out, et, it)
    elif tag == 10:
        for k, (t, vv) in v.items():
            out.write(struct.pack('>b', t))
            _w_str(out, k)
            _w_payload(out, t, vv)
        out.write(b'\x00')
    else:
        raise ValueError(tag)


def write_template(grid, size, path):
    keys = sorted(set(grid.values()))
    index = {s: i for i, s in enumerate(keys)}
    palette = []
    for s in keys:
        name, props = parse_state(s)
        c = {'Name': (8, name)}
        if props:
            c['Properties'] = (10, {k: (8, v) for k, v in sorted(props.items())})
        palette.append(c)
    blocks = []
    for (x, y, z) in sorted(grid, key=lambda p: (p[1], p[2], p[0])):
        blocks.append({'pos': (9, (3, [x, y, z])), 'state': (3, index[grid[(x, y, z)]])})
    root = {'DataVersion': (3, DATA_VERSION),
            'size': (9, (3, list(size))),
            'palette': (9, (10, palette)),
            'blocks': (9, (10, blocks)),
            'entities': (9, (10, []))}
    buf = io.BytesIO()
    buf.write(b'\x0a')
    _w_str(buf, '')
    _w_payload(buf, 10, root)
    with open(path, 'wb') as f:
        f.write(gzip.compress(buf.getvalue(), mtime=0))
    return len(keys), len(blocks)


# ---------------------------------------------------------------- checks
PASSABLE = set(FREE) | {'minecraft:spruce_wall_sign', 'minecraft:light'}
SEATS = ('_slab', '_stairs')


def to_cell(p):
    return (int(math.floor(p[0])) - TEMPLATE_AT[0], int(math.floor(p[1] + 1e-6)) - TEMPLATE_AT[1],
            int(math.floor(p[2])) - TEMPLATE_AT[2])


def check_anchors(g):
    problems = []

    def free(c):
        return bid(g.get(c)) in PASSABLE

    def stand(name, p):
        c = to_cell(p)
        below = (c[0], c[1] - 1, c[2])
        if bid(g.get(below)) in PASSABLE:
            problems.append('%s %s: nothing solid under it (%s)' % (name, p, g.get(below)))
        for dy in (0, 1):
            cc = (c[0], c[1] + dy, c[2])
            if not free(cc):
                problems.append('%s %s: cell %s is %s' % (name, p, cc, g.get(cc)))

    def seat(name, p):
        c = to_cell(p)                                   # the seat block's own cell (top at .5)
        s = bid(g.get(c))
        if s is None or not (s.endswith(SEATS[0]) or s.endswith(SEATS[1])):
            problems.append('%s %s: no seat block at %s (%s)' % (name, p, c, g.get(c)))
        for dy in (1, 2):
            cc = (c[0], c[1] + dy, c[2])
            if not free(cc):
                problems.append('%s %s: cell %s is %s' % (name, p, cc, g.get(cc)))

    for k in ('entrance_floor_center', 'mcgonagall_entrance', 'aisle_start', 'aisle_end', 'stool_front',
              'mcgonagall_stool'):
        stand(k, ANCHORS[k])
    for i, p in enumerate(ANCHORS['first_year_spots']):
        stand('first_year_spots[%d]' % i, p)
    seat('stool', ANCHORS['stool'])
    for i, p in enumerate(ANCHORS['staff_seats']):
        seat('staff_seats[%d]' % i, p)
    for house, seats in ANCHORS['table_seats'].items():
        for i, p in enumerate(seats):
            seat('table_seats.%s[%d]' % (house, i), p)
    # the leaves' swing: the pocket must be empty where a leaf can be, closed or open, whichever side its
    # thickness lies (a 4-wide leaf from the hinge, up to 0.8 either side of the hinge line)
    for hx, hy, hz in DOOR_HINGES:
        for y in range(1, 13):
            for z in range(int(min(hz, 22.5)), int(math.ceil(max(hz, 22.5)))):
                for x in (83,):
                    if g.get((x, y, z)) is not None:
                        problems.append('closed leaf cell %s is %s' % ((x, y, z), g.get((x, y, z))))
            zz = int(math.floor(hz - 0.8)), int(math.floor(hz + 0.8))
            for x in range(80, 84):
                for z in range(zz[0], zz[1] + 1):
                    if g.get((x, y, z)) is not None:
                        problems.append('open leaf cell %s is %s' % ((x, y, z), g.get((x, y, z))))
    # the doorway the camera pushes through is open
    for y in range(1, 13):
        for z in range(19, 26):
            for x in range(80, 87):
                if bid(g.get((x, y, z))) not in PASSABLE:
                    problems.append('doorway cell %s is %s' % ((x, y, z), g.get((x, y, z))))
    return problems


def scene_world(g):
    w = {}
    for (x, y, z), s in g.items():
        w[(x + TEMPLATE_AT[0], y + TEMPLATE_AT[1], z + TEMPLATE_AT[2])] = bid(s)
    return w


def build():
    """The edited template grid {(x, y, z): state} (local coordinates)."""
    _, grid = read_schem(SOURCE)
    b = Builder(grid)
    edit_doorway(b)
    edit_entrance_hall(b)
    edit_stool_and_staff(b)
    edit_lighting(b)
    b.sand_fixed = fix_physics(b)
    return b


def main():
    b = build()
    g = b.g
    xs = [p[0] for p in g]
    ys = [p[1] for p in g]
    zs = [p[2] for p in g]
    assert min(xs) >= 0 and min(ys) >= 0 and min(zs) >= 0, (min(xs), min(ys), min(zs))
    assert max(xs) < SIZE[0] and max(ys) < SIZE[1] and max(zs) < SIZE[2], (max(xs), max(ys), max(zs))
    world = scene_world(g)
    problems = physics_problems(world, RADIUS)
    assert not problems, problems
    # the whole template fits inside the radius (SetBuilder crops silently beyond it)
    for axis in (0, 2):
        lo, hi = TEMPLATE_AT[axis], TEMPLATE_AT[axis] + SIZE[axis] - 1
        assert -RADIUS <= lo and hi <= RADIUS, ('template beyond radius', axis, lo, hi)
    assert RADIUS <= 120
    problems = check_anchors(g)
    assert not problems, '\n'.join(problems[:40]) + ('\n... %d in all' % len(problems))
    candles = [s for s in g.values() if bid(s) == 'minecraft:candle']
    assert all('lit=true' in s for s in candles)
    assert max(p[1] for p, s in g.items() if bid(s) == 'minecraft:candle') < CEIL_Y
    n_pal, n_blocks = write_template(g, SIZE, OUT)
    print('great_hall.nbt: size %s, %d blocks, %d palette states, %d bytes' % (SIZE, n_blocks, n_pal,
                                                                           os.path.getsize(OUT)))
    print('sand made sandstone: %d; candles: %d, all lit; physics clean' % (b.sand_fixed, len(candles)))
    print('TEMPLATE_AT %s  RADIUS %d  (template x %d..%d, z %d..%d in the scene)' % (
        TEMPLATE_AT, RADIUS, TEMPLATE_AT[0], TEMPLATE_AT[0] + SIZE[0] - 1, TEMPLATE_AT[2],
        TEMPLATE_AT[2] + SIZE[2] - 1))
    for k, v in ANCHORS.items():
        if k == 'table_seats':
            print('  table_seats: ' + ', '.join('%s %d seats' % (h, len(s)) for h, s in v.items()))
            for h, s in v.items():
                print('    %s first %s last %s' % (h, s[0], s[-1]))
        elif k in ('first_year_spots', 'staff_seats'):
            print('  %s (%d): %s' % (k, len(v), v))
        else:
            print('  %s: %s' % (k, v))


if __name__ == '__main__':
    main()
