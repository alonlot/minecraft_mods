"""Generate the Auror tower template (data/hogwartsmagic/structure/auror_tower.nbt).

    python build_scripts/generate_auror_tower.py [--preview]

A round Ministry watchtower of dark deepslate with crimson glass, four floors around a central
spiral stair that carries on down into a dungeon of barred cells:

  dungeon   (template y 1)   eight cells behind iron bars, each with an iron door and a lever on the
                             corridor side; five hold a caged Death Eater (AurorRules.PRISONER_TAG).
                             A guard room to the west, an evidence room of confiscated dark objects
                             to the east.
  ground    (y 7, stand 8)   entrance hall: open archway to the south, crimson runner, reception
                             desks with Sneakoscopes, banners, bookcases, an owl perch.
  office    (y 13)           map room: cartography tables, lecterns, desks of candles and book piles,
                             the office chest.
  library   (y 19)           bookcases all round, an enchanting table, a potion bench with a wizard's
                             cauldron and brewing stands, arcane crystals, floating end-rod lights.
  barracks  (y 25)           the Aurors' bunks, drawers and a round table.
  roof      (y 31)           crenellated lookout with the Ministry banners on a mast.

Six Aurors are baked in. The structure JSON pins template y=8 to the surface (start_height -8), so
the ground floor's floor block (y=7) is the ground and the dungeon is buried under it.

Shares its helpers and audits with generate_biome_structures.py; that script does not build this one.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import generate_biome_structures as g  # noqa: E402
from generate_biome_structures import (put, fill, stair, slab, chest, entity, tex, noise,  # noqa: E402
                                       connect_bars, build_template, write_nbt)

W = 23                     # template is W x H x W
C = 11                     # tower axis runs through block (11, *, 11)
R_IN = 8.5                 # interior radius
R_OUT = 9.6                # outer face of the wall
R_PILLAR = 1.5             # central pillar
R_CORE = 3.6               # spiral stair ring
DUNGEON_FLOOR = 1
PLATES = [7, 13, 19, 25, 31]   # floor blocks: ground, office, library, barracks, roof deck
TOP = 37

WALL = [('minecraft:deepslate_bricks', 6), ('minecraft:cracked_deepslate_bricks', 1),
        ('minecraft:deepslate_tiles', 2), ('minecraft:polished_deepslate', 1)]
WALL_LOW = [('minecraft:deepslate_bricks', 4), ('minecraft:cracked_deepslate_bricks', 2),
            ('minecraft:deepslate_tiles', 2)]
BAND = 'minecraft:polished_blackstone_bricks'
TRIM = 'minecraft:polished_blackstone'
DUNGEON_WALL = [('minecraft:deepslate_bricks', 5), ('minecraft:cracked_deepslate_bricks', 3),
                ('minecraft:cobbled_deepslate', 2), ('minecraft:tuff_bricks', 1)]
DUNGEON_FLOOR_PAL = [('minecraft:deepslate_tiles', 4), ('minecraft:cracked_deepslate_tiles', 2),
                     ('minecraft:cobbled_deepslate', 2)]
PLAZA = [('minecraft:deepslate_tiles', 4), ('minecraft:polished_deepslate', 2),
         ('minecraft:cobbled_deepslate', 2), ('minecraft:gravel', 1)]

CARDINAL = {'east': (1, 0), 'south': (0, 1), 'west': (-1, 0), 'north': (0, -1)}
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def radius(x, z):
    return math.hypot(x - C, z - C)


def angle(x, z):
    """Degrees, 0 = east, 90 = south (+z), the way atan2 turns in Minecraft's x/z plane."""
    return math.degrees(math.atan2(z - C, x - C)) % 360.0


def sector(x, z):
    return int(angle(x, z) // 30) % 12


def cell_at(deg, r):
    a = math.radians(deg)
    return (int(round(C + r * math.cos(a))), int(round(C + r * math.sin(a))))


def cardinal_of(dx, dz):
    if abs(dx) >= abs(dz):
        return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


def inward(x, z):
    return cardinal_of(C - x, C - z)


def tangent(x, z):
    """Direction of travel up the spiral (angle increasing)."""
    a = math.radians(angle(x, z))
    return cardinal_of(-math.sin(a), math.cos(a))


# ---- shell ----------------------------------------------------------------------------------------

def dungeon_shell(b):
    for x in range(1, W - 1):
        for z in range(1, W - 1):
            put(b, (x, 0, z), 'minecraft:deepslate')
            put(b, (x, DUNGEON_FLOOR, z), tex(DUNGEON_FLOOR_PAL, x, 1, z, 31))
            edge = x in (1, W - 2) or z in (1, W - 2)
            for y in range(2, 7):
                if edge:
                    put(b, (x, y, z), tex(DUNGEON_WALL, x, y, z, 32))
                else:
                    put(b, (x, y, z), 'minecraft:cave_air')


def tower_shell(b):
    for x in range(W):
        for z in range(W):
            r = radius(x, z)
            if r > R_OUT:
                continue
            for y in range(PLATES[0], TOP):
                wall = r > R_IN
                if wall:
                    if y > PLATES[-1] + 2:
                        continue
                    if y == PLATES[-1] + 2 and int(angle(x, z) // 15) % 2:
                        continue  # crenels between the merlons
                    if y in PLATES or y == PLATES[-1] + 2:
                        put(b, (x, y, z), BAND)
                    else:
                        put(b, (x, y, z), tex(WALL_LOW if y <= PLATES[0] + 2 else WALL, x, y, z, 41))
                elif y in PLATES:
                    put(b, (x, y, z), plate_block(x, y, z))
                else:
                    put(b, (x, y, z), 'minecraft:air')


def plate_block(x, y, z):
    if y == PLATES[0]:
        return 'minecraft:polished_deepslate' if (x + z) % 2 else 'minecraft:deepslate_tiles'
    if y == PLATES[1]:
        return 'minecraft:dark_oak_planks'
    if y == PLATES[2]:
        return 'minecraft:spruce_planks'
    if y == PLATES[3]:
        return 'minecraft:dark_oak_planks'
    return tex([('minecraft:deepslate_tiles', 3), ('minecraft:polished_deepslate', 1)], x, y, z, 43)


def plaza(b):
    """The ground round the tower, over the buried dungeon: a paved yard that frays into the grass."""
    for x in range(1, W - 1):
        for z in range(1, W - 1):
            r = radius(x, z)
            if r <= R_OUT:
                continue
            if r > 10.6 and noise(x, 7, z, 51) < (r - 10.6) / 3.5:
                put(b, (x, PLATES[0], z), 'minecraft:coarse_dirt' if noise(x, 7, z, 52) < 0.5 else 'minecraft:grass_block')
            else:
                put(b, (x, PLATES[0], z), tex(PLAZA, x, 7, z, 53))


def buttresses(b):
    for deg in (45, 135, 225, 315):
        for r, top in ((10.0, 21), (10.9, 13)):
            x, z = cell_at(deg, r)
            for y in range(PLATES[0], top + 1):
                put(b, (x, y, z), tex(WALL_LOW if y <= 9 else WALL, x, y, z, 61))
            stair(b, (x, top + 1, z), 'minecraft:deepslate_brick_stairs', inward(x, z))
        x, z = cell_at(deg, 10.0)
        put(b, (x, PLATES[0] - 1, z), 'minecraft:deepslate_bricks')


def windows(b):
    floors = [(PLATES[0] + 1, (0, 180, 270), 'minecraft:glass_pane'),
              (PLATES[1] + 1, (0, 90, 180, 270), 'minecraft:red_stained_glass_pane'),
              (PLATES[2] + 1, (0, 90, 180, 270), 'minecraft:red_stained_glass_pane'),
              (PLATES[3] + 1, (45, 135, 225, 315), 'minecraft:glass_pane')]
    for stand, degs, pane in floors:
        for deg in degs:
            x, z = cell_at(deg, 9.0)
            for y in (stand + 1, stand + 2):
                put(b, (x, y, z), pane)
            put(b, (x, stand + 3, z), TRIM)
        # arrow slits on the diagonals between, one block tall
        for deg in (22.5, 157.5, 202.5, 337.5):
            x, z = cell_at(deg, 9.0)
            put(b, (x, stand + 2, z), pane)


def connect_panes(b):
    for (x, y, z), (name, props, be) in list(b.items()):
        if not name.endswith('glass_pane'):
            continue
        p = {'waterlogged': 'false'}
        for d, (dx, dz) in CARDINAL.items():
            other = b.get((x + dx, y, z + dz))
            solid = other is not None and other[0] not in ('minecraft:air', 'minecraft:cave_air') \
                and not other[0].endswith(('_stairs', '_slab', '_carpet', 'lantern', '_fence', '_banner'))
            p[d] = 'true' if solid else 'false'
        b[(x, y, z)] = (name, p, None)


def entrance(b):
    """An open archway to the south, three wide, with steps down to the yard."""
    stand = PLATES[0] + 1
    for x in (C - 1, C, C + 1):
        for zz in range(W):
            if R_IN < radius(x, zz) <= R_OUT:
                for y in range(stand, stand + 3):
                    put(b, (x, y, zz), 'minecraft:air')
                if x == C:
                    put(b, (x, stand + 3, zz), 'minecraft:air')
                else:
                    stair(b, (x, stand + 3, zz), 'minecraft:deepslate_brick_stairs',
                          'east' if x < C else 'west', 'top')
                put(b, (x, PLATES[0], zz), TRIM)
    for x in (C - 2, C + 2):
        z = max(zz for zz in range(W) if radius(x, zz) <= R_OUT)
        for y in range(stand, stand + 4):
            put(b, (x, y, z), BAND)
        put(b, (x, stand + 4, z), 'minecraft:lantern', {'hanging': 'false', 'waterlogged': 'false'})


# ---- the spiral ----------------------------------------------------------------------------------

# (floor block it rises from, first sector). Each flight is half a turn; they alternate halves so a hole
# in a plate never sits over the next flight.
FLIGHTS = [(DUNGEON_FLOOR, 0), (PLATES[0], 6), (PLATES[1], 0), (PLATES[2], 6), (PLATES[3], 0)]
STAIR_BLOCK = 'minecraft:spruce_stairs'


def core_cells():
    for x in range(C - 4, C + 5):
        for z in range(C - 4, C + 5):
            r = radius(x, z)
            if R_PILLAR < r <= R_CORE:
                yield x, z, r


def spiral(b):
    for x in range(C - 1, C + 2):
        for z in range(C - 1, C + 2):
            for y in range(DUNGEON_FLOOR + 1, PLATES[-1] + 2):
                put(b, (x, y, z), 'minecraft:chiseled_polished_blackstone' if y in PLATES
                    else 'minecraft:polished_blackstone_bricks')
    for floor, s0 in FLIGHTS:
        for x, z, r in core_cells():
            rel = (sector(x, z) - s0) % 12
            if rel <= 5:
                stair(b, (x, floor + 1 + rel, z), STAIR_BLOCK, tangent(x, z))
            if 1 <= rel <= 4:
                put(b, (x, floor + 6, z), 'minecraft:air')  # headroom through the plate above
        # A railing round the hole on the floor it opens into, and across the dead end beside it.
        stand = floor + 7
        for x in range(C - 5, C + 6):
            for z in range(C - 5, C + 6):
                r = radius(x, z)
                rel = (sector(x, z) - s0) % 12
                if (R_CORE < r <= 4.7 and 1 <= rel <= 4) or (R_PILLAR < r <= R_CORE and rel == 0):
                    if b.get((x, floor + 6, z), ('',))[0] not in ('minecraft:air', 'minecraft:cave_air'):
                        put(b, (x, stand, z), 'minecraft:dark_oak_fence')


def connect_fences(b):
    for (x, y, z), (name, props, be) in list(b.items()):
        if not name.endswith('_fence'):
            continue
        p = {'waterlogged': 'false'}
        for d, (dx, dz) in CARDINAL.items():
            other = b.get((x + dx, y, z + dz))
            p[d] = 'true' if other is not None and (other[0].endswith('_fence') or other[0] in (
                'minecraft:polished_blackstone_bricks', 'minecraft:chiseled_polished_blackstone')) else 'false'
        b[(x, y, z)] = (name, p, None)


# ---- dressing helpers ----------------------------------------------------------------------------

def free(b, pos):
    return b.get(pos, ('minecraft:air',))[0] in ('minecraft:air', 'minecraft:cave_air')


def hanging_lanterns(b, floor, ceiling, soul=False):
    name = 'minecraft:soul_lantern' if soul else 'minecraft:lantern'
    for deg in (45, 135, 225, 315):
        x, z = cell_at(deg, 6.2)
        if free(b, (x, ceiling - 1, z)):
            put(b, (x, ceiling - 1, z), name, {'hanging': 'true', 'waterlogged': 'false'})
            if free(b, (x, ceiling - 2, z)):
                put(b, (x, ceiling - 2, z), 'minecraft:air')


def wall_arc(b, floor, deg0, deg1, block, height=3, props=None):
    """Furniture against the inside of the wall between two angles (one deep, `height` tall)."""
    placed = set()
    deg = deg0
    while deg <= deg1:
        x, z = cell_at(deg, 8.0)
        if (x, z) not in placed and radius(x, z) <= R_IN:
            placed.add((x, z))
            for y in range(floor + 1, floor + 1 + height):
                if free(b, (x, y, z)):
                    put(b, (x, y, z), block, props)
        deg += 4.0
    return placed


def wall_banner(b, deg, floor, color='red'):
    x, z = cell_at(deg, 8.0)
    if radius(x, z) > R_IN:
        return
    facing = inward(x, z)
    dx, dz = CARDINAL[facing]
    behind = (x - dx, floor + 3, z - dz)
    if b.get(behind, ('minecraft:air',))[0] in ('minecraft:air', 'minecraft:cave_air'):
        return
    for y in (floor + 2, floor + 3):
        if not free(b, (x, y, z)):
            return
    put(b, (x, floor + 3, z), 'minecraft:%s_wall_banner' % color, {'facing': facing})


def desk(b, cells, top='minecraft:dark_oak_slab', on=None):
    for i, pos in enumerate(cells):
        if not free(b, pos):
            continue
        slab(b, pos, top, 'top')
        if on and i < len(on) and on[i]:
            above = (pos[0], pos[1] + 1, pos[2])
            name, props = on[i] if isinstance(on[i], tuple) else (on[i], None)
            put(b, above, name, props)


def place(b, pos, name, props=None):
    if pos is not None and free(b, pos):
        put(b, pos, name, props)
        return True
    return False


def at(deg, r, floor):
    x, z = cell_at(deg, r)
    pos = (x, floor + 1, z)
    if radius(x, z) > R_IN or radius(x, z) <= 4.7:
        return None
    return pos


# ---- floors -------------------------------------------------------------------------------------

def ground_floor(b):
    f = PLATES[0]
    stand = f + 1
    for z in range(C + 5, C + 10):
        if radius(C, z) <= R_IN + 1:
            put(b, (C, stand, z), 'minecraft:red_carpet')
    # reception desks either side of the door, each with a Sneakoscope
    for deg in (55, 125):
        cells = [at(deg - 8, 6.6, f), at(deg, 6.6, f), at(deg + 8, 6.6, f)]
        desk(b, [c for c in cells if c], on=[None, ('hogwartsmagic:sneakoscope', {'power': '0'}),
                                              'hogwartsmagic:candle_cluster'])
    # bookcases on the north arc, banners between the windows
    wall_arc(b, f, 200, 250, 'minecraft:bookshelf')
    wall_arc(b, f, 290, 340, 'minecraft:bookshelf')
    for deg in (22, 158):
        wall_banner(b, deg, f)
    place(b, at(70, 7.6, f), 'hogwartsmagic:owl_perch')
    place(b, at(110, 7.6, f), 'minecraft:potted_fern')
    place(b, at(0, 6.0, f), 'minecraft:lectern', {'facing': 'west', 'has_book': 'false', 'powered': 'false'})
    place(b, at(180, 6.0, f), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    hanging_lanterns(b, f, PLATES[1])


def office_floor(b):
    f = PLATES[1]
    for deg in range(0, 360, 6):
        pos = at(deg, 5.2, f)
        if pos and free(b, pos) and int(deg // 30) % 3 != 2:
            put(b, pos, 'minecraft:red_carpet')
    place(b, at(20, 7.4, f), 'minecraft:cartography_table')
    place(b, at(30, 7.4, f), 'minecraft:cartography_table')
    place(b, at(160, 7.4, f), 'minecraft:lectern', {'facing': 'north', 'has_book': 'false', 'powered': 'false'})
    for deg in (60, 120):
        cells = [at(deg - 7, 7.3, f), at(deg, 7.3, f), at(deg + 7, 7.3, f)]
        desk(b, [c for c in cells if c], on=['hogwartsmagic:enchanted_book_pile', 'hogwartsmagic:candle_cluster',
                                              'hogwartsmagic:crystal_goblet'])
    wall_arc(b, f, 200, 250, 'minecraft:chiseled_bookshelf', height=2,
             props=None)
    wall_arc(b, f, 290, 340, 'minecraft:bookshelf')
    pos = at(195, 7.5, f)
    if pos and free(b, pos):
        chest(b, pos, 'east', 'hogwartsmagic:chests/auror_office')
    for deg in (22, 158, 205, 335):
        wall_banner(b, deg, f)
    place(b, at(300, 6.0, f), 'hogwartsmagic:sneakoscope', {'power': '0'})
    hanging_lanterns(b, f, PLATES[2])


def library_floor(b):
    f = PLATES[2]
    for lo, hi in ((8, 82), (98, 172), (188, 262), (278, 352)):
        for (x, z) in wall_arc(b, f, lo, hi, 'minecraft:bookshelf'):
            if noise(x, f, z, 71) < 0.35 and free(b, (x, f + 4, z)):
                put(b, (x, f + 4, z), 'hogwartsmagic:arcane_cluster', {'facing': 'up', 'waterlogged': 'false'})
    place(b, at(0, 6.2, f), 'minecraft:enchanting_table')
    place(b, at(180, 6.6, f), 'hogwartsmagic:wizard_cauldron', {'level': '3'})
    place(b, at(170, 6.6, f), 'minecraft:brewing_stand',
          {'has_bottle_0': 'false', 'has_bottle_1': 'false', 'has_bottle_2': 'false'})
    place(b, at(190, 6.6, f), 'minecraft:brewing_stand',
          {'has_bottle_0': 'false', 'has_bottle_1': 'false', 'has_bottle_2': 'false'})
    for deg in (90, 270):
        cells = [at(deg - 8, 6.4, f), at(deg, 6.4, f), at(deg + 8, 6.4, f)]
        desk(b, [c for c in cells if c], top='minecraft:spruce_slab',
             on=['hogwartsmagic:candle_cluster', 'hogwartsmagic:enchanted_book_pile', 'hogwartsmagic:candle_cluster'])
    # end rods hanging under the ceiling: the floating lights of a wizard's library
    for deg in range(15, 360, 45):
        x, z = cell_at(deg, 6.0)
        if free(b, (x, PLATES[3] - 1, z)):
            put(b, (x, PLATES[3] - 1, z), 'minecraft:end_rod', {'facing': 'down'})


def barracks(b):
    """The Aurors' bunk room: beds head to the wall, chests of drawers, a round table, a lookout's spyglass stand."""
    f = PLATES[3]
    for deg in (20, 70, 110, 160, 200, 340):
        hx, hz = cell_at(deg, 8.0)
        if radius(hx, hz) > R_IN:
            continue
        facing = cardinal_of(hx - C, hz - C)
        dx, dz = CARDINAL[facing]
        fx, fz = hx - dx, hz - dz
        if radius(fx, fz) <= 4.7 or not free(b, (hx, f + 1, hz)) or not free(b, (fx, f + 1, fz)):
            continue
        put(b, (hx, f + 1, hz), 'minecraft:red_bed', {'facing': facing, 'part': 'head', 'occupied': 'false'})
        put(b, (fx, f + 1, fz), 'minecraft:red_bed', {'facing': facing, 'part': 'foot', 'occupied': 'false'})
    for deg in (250, 290):
        place(b, at(deg, 7.6, f), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    place(b, at(270, 7.6, f), 'hogwartsmagic:candle_cluster')
    for deg in (250, 290):
        pos = at(deg, 7.6, f)
        if pos and b.get(pos, ('',))[0] == 'minecraft:barrel':
            put(b, (pos[0], pos[1] + 1, pos[2]), 'minecraft:lantern', {'hanging': 'false', 'waterlogged': 'false'})
    t = at(315, 6.0, f)
    if t and free(b, t):
        put(b, t, 'minecraft:dark_oak_fence')
        put(b, (t[0], t[1] + 1, t[2]), 'minecraft:dark_oak_pressure_plate', {'powered': 'false'})
    wall_banner(b, 90, f)
    wall_banner(b, 270, f)
    hanging_lanterns(b, f, PLATES[4])


def roof(b):
    f = PLATES[-1]
    stand = f + 1
    # The mast on the pillar, with the Ministry's crimson banners.
    for y in range(stand, stand + 4):
        put(b, (C, y, C), 'minecraft:polished_blackstone_wall',
            {'up': 'true', 'north': 'none', 'south': 'none', 'east': 'none', 'west': 'none', 'waterlogged': 'false'})
    for x in range(C - 1, C + 2):
        for z in range(C - 1, C + 2):
            if (x, z) != (C, C):
                slab(b, (x, stand, z), 'minecraft:polished_blackstone_brick_slab')
    for facing, (dx, dz) in CARDINAL.items():
        put(b, (C + dx, stand + 3, C + dz), 'minecraft:red_wall_banner', {'facing': facing})
    put(b, (C, stand + 4, C), 'minecraft:soul_lantern', {'hanging': 'false', 'waterlogged': 'false'})
    for deg in (0, 90, 180, 270):
        x, z = cell_at(deg, 9.0)
        if b.get((x, f + 2, z), ('',))[0] == BAND:
            put(b, (x, f + 3, z), 'minecraft:lantern', {'hanging': 'false', 'waterlogged': 'false'})
    for deg in (45, 225):
        pos = at(deg, 7.0, f)
        if pos and free(b, pos):
            stair(b, pos, 'minecraft:dark_oak_stairs', inward(pos[0], pos[2]))


# ---- dungeon ------------------------------------------------------------------------------------

CELL_XS = (3, 7, 13, 17)   # west column of each cell's 3-wide interior
NORTH = dict(interior=(2, 4), front=5, corridor=6, lever_facing='south')
SOUTH = dict(interior=(18, 20), front=17, corridor=16, lever_facing='north')
PRISONERS = [('north', 3), ('north', 7), ('north', 13), ('south', 7), ('south', 17)]


def cells(b):
    for row in (NORTH, SOUTH):
        z0, z1 = row['interior']
        zf = row['front']
        zlo, zhi = min(z0, zf), max(z1, zf)
        for x in range(2, W - 2):
            for z in range(zlo, zhi + 1):
                for y in range(2, 7):
                    put(b, (x, y, z), tex(DUNGEON_WALL, x, y, z, 81))
        for a in CELL_XS:
            for x in range(a, a + 3):
                for z in range(z0, z1 + 1):
                    for y in range(2, 6):
                        put(b, (x, y, z), 'minecraft:cave_air')
            for y in range(2, 7):
                put(b, (a, y, zf), 'minecraft:iron_bars')
                put(b, (a + 2, y, zf), 'minecraft:polished_deepslate')
            door_facing = row['lever_facing']
            put(b, (a + 1, 2, zf), 'minecraft:iron_door', {'facing': door_facing, 'half': 'lower', 'hinge': 'left',
                                                            'open': 'false', 'powered': 'false'})
            put(b, (a + 1, 3, zf), 'minecraft:iron_door', {'facing': door_facing, 'half': 'upper', 'hinge': 'left',
                                                            'open': 'false', 'powered': 'false'})
            put(b, (a + 1, 4, zf), TRIM)
            put(b, (a + 2, 3, row['corridor']), 'minecraft:lever',
                {'face': 'wall', 'facing': row['lever_facing'], 'powered': 'false'})
            # inside: a straw bed, a chain from the ceiling, a little candle-light
            back = z0 if row is NORTH else z1
            put(b, (a, 2, back), 'minecraft:hay_block', {'axis': 'x'})
            slab(b, (a + 2, 2, back), 'minecraft:spruce_slab')
            put(b, (a + 2, 5, back), 'minecraft:chain', {'axis': 'y', 'waterlogged': 'false'})
            put(b, (a + 2, 3, back), 'minecraft:candle', {'candles': '1', 'lit': 'true', 'waterlogged': 'false'})
    # The cell nobody left: bones and cobwebs.
    put(b, (18, 2, 3), 'minecraft:bone_block', {'axis': 'z'})
    put(b, (19, 2, 2), 'minecraft:skeleton_skull', {'rotation': '6'})
    put(b, (17, 4, 3), 'minecraft:cobweb')
    put(b, (19, 3, 4), 'minecraft:cobweb')


def dungeon_rooms(b):
    floor = DUNGEON_FLOOR
    # guard room, west
    slab(b, (3, 2, 9), 'minecraft:dark_oak_slab', 'top')
    slab(b, (3, 2, 10), 'minecraft:dark_oak_slab', 'top')
    put(b, (3, 3, 9), 'hogwartsmagic:sneakoscope', {'power': '0'})
    put(b, (3, 3, 10), 'hogwartsmagic:candle_cluster')
    stair(b, (4, 2, 9), 'minecraft:dark_oak_stairs', 'east')
    chest(b, (2, 2, 13), 'east', 'hogwartsmagic:chests/auror_evidence')
    put(b, (2, 2, 14), 'minecraft:barrel', {'facing': 'east', 'open': 'false'})
    put(b, (2, 3, 14), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (2, 2, 7), 'minecraft:grindstone', {'face': 'floor', 'facing': 'east'})
    put(b, (2, 3, 11), 'minecraft:red_wall_banner', {'facing': 'east'})
    # evidence room, east: confiscated dark objects
    chest(b, (20, 2, 9), 'west', 'hogwartsmagic:chests/auror_evidence')
    put(b, (20, 2, 10), 'minecraft:crying_obsidian')
    put(b, (20, 3, 10), 'minecraft:wither_skeleton_skull', {'rotation': '12'})
    put(b, (20, 2, 12), 'hogwartsmagic:wizard_cauldron', {'level': '3'})
    put(b, (20, 2, 13), 'minecraft:brewing_stand', {'has_bottle_0': 'false', 'has_bottle_1': 'false',
                                                    'has_bottle_2': 'false'})
    put(b, (20, 2, 14), 'minecraft:lectern', {'facing': 'west', 'has_book': 'false', 'powered': 'false'})
    put(b, (19, 2, 7), 'hogwartsmagic:candle_cluster')
    put(b, (20, 3, 11), 'minecraft:red_wall_banner', {'facing': 'west'})
    # soul lanterns along both corridors and over the rooms: no dark corner for anything to spawn in
    for x in (4, 9, 14, 19):
        for z in (6, 16):
            put(b, (x, 6, z), 'minecraft:soul_lantern', {'hanging': 'true', 'waterlogged': 'false'})
    for z in (9, 13):
        for x in (4, 18):
            put(b, (x, 6, z), 'minecraft:soul_lantern', {'hanging': 'true', 'waterlogged': 'false'})


# ---- entities -----------------------------------------------------------------------------------

def aurors():
    still = {'PersistenceRequired': ('byte', 1)}
    spots = [(15.5, PLATES[0] + 1, 14.5, 0), (15.5, PLATES[0] + 1, 6.5, 1),
             (5.5, PLATES[1] + 1, 11.5, 2), (16.5, PLATES[2] + 1, 11.5, 0),
             (11.5, PLATES[-1] + 1, 4.5, 1), (5.5, DUNGEON_FLOOR + 1, 11.5, 2)]
    return [entity(x, float(y), z, 'hogwartsmagic:auror', dict(still, Variant=('int', v))) for x, y, z, v in spots]


def prisoners():
    out = []
    for row, a in PRISONERS:
        r = NORTH if row == 'north' else SOUTH
        z = (r['interior'][0] + r['interior'][1]) / 2.0 + 0.5
        out.append(entity(a + 1.5, 2.0, z, 'hogwartsmagic:wizard', {
            'PersistenceRequired': ('byte', 1),
            'Disarmed': ('byte', 1),
            'Tags': ('list', 'string', [('string', 'hogwartsmagic:auror_prisoner')]),
        }))
    return out


def auror_tower():
    b = {}
    dungeon_shell(b)
    cells(b)
    dungeon_rooms(b)
    tower_shell(b)
    plaza(b)
    buttresses(b)
    windows(b)
    entrance(b)
    spiral(b)
    ground_floor(b)
    office_floor(b)
    library_floor(b)
    barracks(b)
    roof(b)
    connect_bars(b)
    connect_panes(b)
    connect_fences(b)
    return b, aurors() + prisoners()


def audit_cells(blocks, entities):
    """Every prisoner stands in a closed cell whose door is within AurorRules.CELL_DOOR_REACH, and its lever touches
    the pillar that powers that door."""
    problems = []
    for e in entities:
        nbt = e['nbt'][1]
        if nbt['id'][1] != 'hogwartsmagic:wizard':
            continue
        x, y, z = (int(math.floor(v[1])) for v in e['pos'][2])
        doors = [(dx, dz) for dx in range(-2, 3) for dz in range(-2, 3)
                 if blocks.get((x + dx, y, z + dz), ('',))[0] == 'minecraft:iron_door']
        if len(doors) != 1:
            problems.append('prisoner at %s sees %d cell doors' % ((x, y, z), len(doors)))
    return problems


def main():
    blocks, entities = auror_tower()
    size = [max(p[i] for p in blocks) + 1 for i in range(3)]
    problems = (g.audit('auror_tower', blocks) + g.audit_entities(blocks, entities, size)
                + g.audit_entity_room(blocks, entities) + g.audit_fire(blocks) + audit_cells(blocks, entities))
    path = os.path.join(g.OUT, 'auror_tower.nbt')
    write_nbt(path, build_template(blocks, entities))
    kinds = len({v[0] for v in blocks.values()})
    print('%-18s %2dx%2dx%2d  %5d blocks  %2d block types  %2d entities  %6d bytes  %s'
          % ('auror_tower', size[0], size[1], size[2], len(blocks), kinds, len(entities), os.path.getsize(path),
             'ok' if not problems else '%d PROBLEMS' % len(problems)))
    for p in problems:
        print('  ' + p)
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
