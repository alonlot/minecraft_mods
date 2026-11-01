"""Below the castle: the kitchens under the Great Hall, a torchlit dungeon corridor with cells, and the Slytherin common
room, all carved into the stone plinth the Great Hall stands on.

    from mini_hogwarts import rooms_below
    anchors = rooms_below.place_below(grid)      # castle frame, after the Great Hall wing (whose plinth it carves)

WHAT WAS THERE
--------------
The Great Hall wing stands on a plinth of solid stone (hall.build_plinth): every column of the hall proper, x 39..83 by
z 25..105, is stone from the floor (y 24) down to y 0, and nothing of the old castle stands in it. Under the entrance
hall the plinth only runs down to the old hill's crust, and east of x 52 the hill is hollow there (a dome of plain air
under the crust at z 107..139, open to the world), so nothing is carved under the entrance hall but the stair's own
slot along its west wall, where the plinth is solid to y 0.

WHAT IT BECOMES
---------------
    feet y   level      what is there
    24..18   the stair  a slot in the entrance hall's floor in its north-west corner (a dark-oak rail and a lantern on
                        its newel), then a stone-brick flight running north under the entrance hall's north wall and the
                        Great Hall's south wall
    17       kitchens   the kitchen passage at the stair's foot; the kitchens through a door in its north wall: a hall 37 x
                        61 and six tall right under the Great Hall, its four long tables on the very lines of the four
                        house tables above them (the food goes up through the ceiling), dark-oak beams across on stone
                        piers down the aisles, a spruce ceiling, a great brick hearth in the north wall (three fires and a
                        cauldron on a chain) with a work counter either side of its front, ranges, dressers and a
                        scullery down the side walls
    16..10   the stair  the passage runs on east and down a second flight
    9        dungeons   the dungeon corridor, north under the kitchens: eight cells behind iron bars (one stands open with
                        the DUNGEON chest in it), torches on the piers between them, four Living Statue alcoves; at its
                        north end, behind a bare deepslate wall with a green banner over the doorway, the Slytherin common
                        room: a dark vault 29 x 20 and five tall on four piers, ribbed and low, deepslate and dark
                        prismarine, green light from sea lanterns behind green glass (lake windows down both side walls
                        and coffers in every bay of the ceiling), a black fireplace with soul fires and a skull on the
                        mantel, sofas and armchairs on a green rug, study and chess tables, bookshelves, house banners

Every room is a box in ROOMS_BY_NAME (build_castle.ROOMS, so seal_interiors writes cave_air into its air). carve() cuts
them only out of the plinth: it asserts that every interior cell is a plinth cell (a hall column, solid, below the main
floor; the slot's floor cells are the only ones at y 24) and that every cell round them is solid, so no room ever opens
onto the world or the hill's hollow, and build_castle.verify_below holds the finished castle to the same.
"""

from .frame import MAIN_FLOOR_Y
from . import checks, hall
from .grid import air_at
from .hall import G

# ---------------------------------------------------------------- levels (castle frame)
KITCHEN_FLOOR_Y = 16
KITCHEN_FEET = KITCHEN_FLOOR_Y + 1     # 17
KITCHEN_TOP = 22                       # the last interior row: the ceiling (y 23) is right under the hall's floor
DUNGEON_FLOOR_Y = 8
DUNGEON_FEET = DUNGEON_FLOOR_Y + 1     # 9
CORRIDOR_TOP = 12
CELL_TOP = 11
SLYTHERIN_TOP = 13                     # five tall: low vaults

# ---------------------------------------------------------------- the stair from the entrance hall
STAIR_X = (46, 48)                     # beside the entrance hall's west wall (x 45), clear of the gallery door (z 115..117)
SLOT_Z = (111, 113)                    # the entrance hall's floor (y 24) is opened over these rows, under its north wall
# (z, feet): one tread a row, from the slot north under the walls to the passage's floor; a tread is a stone-brick stair
STAIR_TREADS = [(SLOT_Z[1] - k, MAIN_FLOOR_Y - k) for k in range(7)]      # (113, 24) .. (107, 18)
NEWEL = (49, MAIN_FLOOR_Y + 1, 113)    # the rail along the slot's open side ends in a post with a lantern on it
RAIL = [(49, MAIN_FLOOR_Y + 1, z) for z in (112, 113)]

# ---------------------------------------------------------------- the kitchens level
PASSAGE = (44, KITCHEN_FEET, 104, 52, KITCHEN_FEET + 3, 106)
KITCHEN_DOOR = (45, KITCHEN_FEET, 103, 47, KITCHEN_FEET + 2, 103)
KITCHEN = (43, KITCHEN_FEET, 42, 79, KITCHEN_TOP, 102)
HEARTH = (57, KITCHEN_FEET, 38, 65, KITCHEN_FEET + 3, 41)                # the fireplace through the north wall (z 41)
PIER_X = (53, 61, 69)                  # down the middle of the three aisles between the tables
PIER_Z = (54, 62, 70, 78, 86, 94)      # a dark-oak beam across the kitchens over every row of piers
TABLE_SLAB = G.state('spruce_slab', type='top', waterlogged=False)
# Wall groups (wall x, facing into the room, centre z, kind): ranges (ovens under a brick chimney breast), dressers
# (a counter and a shelf of pots) and the scullery (cauldrons of water).
WALL_GROUPS = (
    (43, 'east', 58, 'range'), (43, 'east', 66, 'dresser'), (43, 'east', 74, 'range'), (43, 'east', 82, 'dresser'),
    (43, 'east', 90, 'range'),
    (79, 'west', 58, 'dresser'), (79, 'west', 66, 'scullery'), (79, 'west', 74, 'dresser'), (79, 'west', 82, 'range'),
    (79, 'west', 90, 'dresser'),
)
ELF_SPOTS = ((59, KITCHEN_FEET, 43), (63, KITCHEN_FEET, 43), (45, KITCHEN_FEET, 58), (77, KITCHEN_FEET, 82),
             (53, KITCHEN_FEET, 66), (61, KITCHEN_FEET, 100))

# ---------------------------------------------------------------- the dungeons level
# the second flight: (x, feet) one tread a column, east from the passage's end down to the corridor's landing
STAIR_B_Z = (104, 106)
STAIR_B = [(53 + i, KITCHEN_FEET - 1 - i) for i in range(7)]            # (53, 16) .. (59, 10)
LANDING = (60, DUNGEON_FEET, 104, 62, CORRIDOR_TOP, 106)
CORRIDOR = (60, DUNGEON_FEET, 65, 62, CORRIDOR_TOP, 103)
# the cells: (side, front x (the bars), interior x range, z range); four a side, a pier of wall between each
CELL_ROWS = ((98, 100), (94, 96), (90, 92), (86, 88))
CELLS = [('west', 59, (55, 58), zz) for zz in CELL_ROWS] + [('east', 63, (64, 67), zz) for zz in CELL_ROWS]
OPEN_CELL = ('west', 59, (55, 58), (90, 92))        # its bars hold an iron door standing open; the chest is inside
OPEN_DOOR_Z = 91
DUNGEON_CHEST = {'at': [55, DUNGEON_FEET, 91], 'facing': 'east', 'theme': 'DUNGEON'}
ALCOVE_Z = (72, 79)                    # a statue alcove each side of the corridor at these rows
WEST_TORCH_Z = (101, 93, 85, 76, 67)   # wall torches on the corridor's walls (never on an alcove)
EAST_TORCH_Z = (97, 89, 82, 70)

# ---------------------------------------------------------------- the Slytherin common room
SL_X = (47, 75)
SL_Z = (44, 63)
SLYTHERIN = (SL_X[0], DUNGEON_FEET, SL_Z[0], SL_X[1], SLYTHERIN_TOP, SL_Z[1])
SL_DOOR = (60, DUNGEON_FEET, 64, 62, DUNGEON_FEET + 2, 64)               # through its south wall, the corridor's end
SL_HEARTH = (60, DUNGEON_FEET, 41, 62, DUNGEON_FEET + 2, 43)             # the firebox through the north wall (z 43)
SL_PIERS = [(x, z) for x in (54, 68) for z in (50, 57)]
SL_RIB_X = (54, 68)
SL_RIB_Z = (50, 57)
SL_WINDOW_Z = (46, 47, 53, 54, 60, 61)                                   # the lake windows, both side walls, y 10..12
SL_COFFER_X = (50, 60, 61, 62, 72)                                       # coffers in the ceiling (y 14) of every bay
SL_COFFER_Z = (46, 47, 53, 54, 60, 61)
SL_SPOTS = ((52, DUNGEON_FEET, 46), (71, DUNGEON_FEET, 47), (52, DUNGEON_FEET, 57), (70, DUNGEON_FEET, 60))


def _stair_a_boxes():
    """One box a tread row of the stair from the entrance hall: three clear over each tread (four where the ceiling
    allows), up through the opened floor (y 24) in the slot's rows and under the walls' bottom course elsewhere."""
    out = {}
    for z, feet in STAIR_TREADS:
        if SLOT_Z[0] <= z <= SLOT_Z[1]:
            top = MAIN_FLOOR_Y
        else:
            top = min(feet + 3, MAIN_FLOOR_Y - 1)
        out['below: entrance hall stair, row z %d' % z] = (STAIR_X[0], feet, z, STAIR_X[1], top, z)
    return out


def _stair_b_boxes():
    """One box a tread column of the flight down to the dungeons: four clear over each tread, a stepped ceiling."""
    return {'below: dungeon stair, column x %d' % x: (x, feet, STAIR_B_Z[0], x, feet + 3, STAIR_B_Z[1])
            for x, feet in STAIR_B}


def _cell_box(cell):
    side, front, (x0, x1), (z0, z1) = cell
    return (x0, DUNGEON_FEET, z0, x1, CELL_TOP, z1)


def _cell_name(cell):
    return 'below: %s cell z %d..%d' % (cell[0], cell[3][0], cell[3][1])


ROOMS_BY_NAME = {
    **_stair_a_boxes(),
    'below: kitchen passage': PASSAGE,
    'below: kitchen door': KITCHEN_DOOR,
    'below: kitchens': KITCHEN,
    'below: kitchen hearth': HEARTH,
    **_stair_b_boxes(),
    'below: dungeon landing': LANDING,
    'below: dungeon corridor': CORRIDOR,
    **{_cell_name(c): _cell_box(c) for c in CELLS},
    **{'below: statue alcove %s z %d' % (side, z): (x, DUNGEON_FEET, z, x, CELL_TOP, z)
       for z in ALCOVE_Z for side, x in (('west', 59), ('east', 63))},
    'below: slytherin door': SL_DOOR,
    'below: slytherin common room': SLYTHERIN,
    'below: slytherin hearth': SL_HEARTH,
}

# One walkable cell in each part, for the reachability check.
BELOW_CELLS = {
    'stair top tread': (47, MAIN_FLOOR_Y, SLOT_Z[1]),
    'stair foot': (47, KITCHEN_FEET + 1, 107),
    'kitchen passage': (50, KITCHEN_FEET, 105),
    'kitchens, south end': (46, KITCHEN_FEET, 101),
    'kitchens, at the hearth': (61, KITCHEN_FEET, 43),
    'kitchens, east aisle': (77, KITCHEN_FEET, 74),
    'dungeon landing': (61, DUNGEON_FEET, 105),
    'dungeon corridor, north end': (61, DUNGEON_FEET, 66),
    'the open cell': (56, DUNGEON_FEET, 91),
    'slytherin door': (61, DUNGEON_FEET, 63),
    'slytherin hearth': (61, DUNGEON_FEET, 45),
    'slytherin, north-west corner': (48, DUNGEON_FEET, 45),
    'slytherin, south-east corner': (74, DUNGEON_FEET, 62),
}


def room_boxes():
    """{name: box} every interior this task carves (for build_castle.ROOMS)."""
    return dict(ROOMS_BY_NAME)


def light_boxes():
    """The boxes the no-dark-floor check covers: every room below."""
    return list(ROOMS_BY_NAME.values())


def changed_boxes():
    """(name, inclusive box) for build_castle.CHANGED_ON_PURPOSE: every room below with its walls, floor and ceiling (up
    to the main floor; the entrance hall's floor round the slot is the hall wing's, not the old castle's). They stand in
    the hall's plinth, which is new stone: no old-castle cell is in them today, so they name the region rather than
    excuse a change, should a later room move them onto the old hill's crust under the hall's doors."""
    return [('below the castle (rooms_below): %s, with its walls' % name[len('below: '):],
             (b[0] - 1, b[1] - 1, b[2] - 1, b[3] + 1, min(b[4] + 1, MAIN_FLOOR_Y - 1), b[5] + 1))
            for name, b in sorted(ROOMS_BY_NAME.items())]


def _box_cells(x0, y0, z0, x1, y1, z1):
    return {(x, y, z) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1) for x in range(x0, x1 + 1)}


def _in(p, box):
    return box[0] <= p[0] <= box[3] and box[1] <= p[1] <= box[4] and box[2] <= p[2] <= box[5]


def interior_cells():
    """Every cell of every room box."""
    out = set()
    for b in ROOMS_BY_NAME.values():
        out |= _box_cells(*b)
    return out


def in_rooms(p):
    return any(_in(p, b) for b in ROOMS_BY_NAME.values())


def _faces(p):
    x, y, z = p
    return ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1))


def shell_cells(inner):
    """The cells face-adjacent to `inner` and not in it: the rooms' walls, floors and ceilings."""
    return {n for p in inner for n in _faces(p) if n not in inner}


def _mouth(p):
    """The slot's mouth: the entrance hall's own air over the opened floor (the only air a room below may touch)."""
    return p[1] > MAIN_FLOOR_Y and _in(p, hall.ENTRANCE_BOX)


def slot_columns():
    """(x, z) of every floor cell opened in the entrance hall."""
    return [(x, z) for x in range(STAIR_X[0], STAIR_X[1] + 1) for z in range(SLOT_Z[0], SLOT_Z[1] + 1)]


# ---------------------------------------------------------------- audits (build_castle.verify_below)

def plinth_report(cells, footprint):
    """{'outside': room cells not in a hall column below the main floor (or at it outside the slot), 'leaks': cells
    round the rooms that are air (other than the slot's mouth)}. Both empty: the rooms are carved in the plinth."""
    inner = interior_cells()
    slot = set(slot_columns())
    outside = sorted(p for p in inner if (p[0], p[2]) not in footprint or p[1] > MAIN_FLOOR_Y
                     or (p[1] == MAIN_FLOOR_Y and (p[0], p[2]) not in slot))
    leaks = sorted(p for p in shell_cells(inner) if air_at(cells, p) and not _mouth(p))
    return {'outside': outside, 'leaks': leaks}


def hall_table_cells(cells):
    """{(x, z)} the four house tables of the Great Hall above: their tops at the hall's feet level (top slabs, and the
    upside-down stairs that shape their ends), in the columns of the hall that hold a long run of top slabs (the High
    Table on its dais is higher)."""
    x0, _, z0, x1, _, z1 = hall.HALL_PROPER_BOX
    cols, ends = {}, {}
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            s = cells.get((x, MAIN_FLOOR_Y + 1, z), '')
            name = checks.block_id(s)
            if name == 'dark_oak_slab' and 'type=top' in s:
                cols.setdefault(x, set()).add(z)
            elif name == 'dark_oak_stairs' and 'half=top' in s:
                ends.setdefault(x, set()).add(z)
    out = set()
    for x, zs in cols.items():
        if len(zs) >= 40:
            out |= {(x, z) for z in zs | {e for e in ends.get(x, ()) if min(zs) <= e <= max(zs)}}
    return out


def table_groups(columns):
    """The runs of adjacent x columns: one run a table."""
    runs = []
    for x in sorted(columns):
        if runs and runs[-1][-1] == x - 1:
            runs[-1].append(x)
        else:
            runs.append([x])
    return runs


def cell_front(cell):
    side, front, _, (z0, z1) = cell
    return [(front, y, z) for y in range(DUNGEON_FEET, CELL_TOP + 1) for z in range(z0, z1 + 1)]


def barred_cells(cells):
    """The cells whose whole front onto the corridor is iron bars (the open cell, with its door, is not one)."""
    return [c for c in CELLS if all(checks.block_id(cells.get(p, '')) == 'iron_bars' for p in cell_front(c))]


def green_lights(cells):
    """The Slytherin common room's green lights: sea lanterns each right behind a green stained glass block that faces
    the room (a cell of it a player could stand in: air, or a light block carrying the coffer's light down)."""
    n = 0
    for p, s in cells.items():
        if checks.block_id(s) != 'sea_lantern':
            continue
        for g in _faces(p):
            if checks.block_id(cells.get(g, '')) != 'green_stained_glass':
                continue
            if any(_in(r, SLYTHERIN) and checks.passable(cells.get(r, 'minecraft:air')) for r in _faces(g)):
                n += 1
                break
    return n


# ---------------------------------------------------------------- blocks

def _stairs(name, facing, half='bottom'):
    return G.state(name, facing=facing, half=half, shape='straight', waterlogged=False)


def _slab(name, kind='top'):
    return G.state(name, type=kind, waterlogged=False)


def _lantern(hanging):
    return G.state('lantern', hanging=hanging, waterlogged=False)


def _h(x, y, z):
    """A fixed pseudo-random 0..1023 for a cell (so the stone mix is the same every build)."""
    return ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 1023


CHAIN = G.state('chain', axis='y', waterlogged=False)
BRICKS = 'minecraft:bricks'


def _zone(p):
    x, y, z = p
    if y <= SLYTHERIN_TOP + 1 and SL_X[0] - 1 <= x <= SL_X[1] + 1 and z <= SL_Z[1] + 1:
        return 'slytherin'
    if (KITCHEN_FLOOR_Y <= y <= KITCHEN_TOP + 1 and KITCHEN[0] - 1 <= x <= KITCHEN[3] + 1
            and HEARTH[2] - 1 <= z <= KITCHEN[5] + 1):
        return 'kitchen'
    return 'dungeon'


def _lining(p, inner):
    """The block for one cell of a room's walls, floor or ceiling, by the part of the castle it is in."""
    x, y, z = p
    floor = (x, y + 1, z) in inner
    ceiling = not floor and (x, y - 1, z) in inner
    zone = _zone(p)
    h = _h(x, y, z)
    if zone == 'kitchen':
        if floor:
            border = x in PIER_X or x in (KITCHEN[0], KITCHEN[3]) or z in (KITCHEN[2], KITCHEN[5])
            return 'minecraft:stone_bricks' if border else 'minecraft:polished_andesite'
        return 'minecraft:spruce_planks' if ceiling else 'minecraft:stone_bricks'
    if zone == 'slytherin':
        if floor:
            border = x in SL_X or z in SL_Z or x == 61
            return 'minecraft:dark_prismarine' if border else 'minecraft:polished_deepslate'
        if ceiling:
            return 'minecraft:deepslate_tiles'
        return 'minecraft:cracked_deepslate_bricks' if h < 100 else 'minecraft:deepslate_bricks'
    if floor:
        return 'minecraft:mossy_cobblestone' if h < 250 else 'minecraft:cobblestone'
    if ceiling:
        return 'minecraft:stone_bricks'
    if h < 200:
        return 'minecraft:cracked_stone_bricks'
    if h < 320:
        return 'minecraft:mossy_stone_bricks'
    return 'minecraft:stone_bricks'


# ---------------------------------------------------------------- carving

def carve(grid):
    """Cut every room out of the plinth and line it. Raises if a room cell is not a solid plinth cell (a hall column,
    below the main floor; the slot's floor cells at it) or if a cell round the rooms is air (a room would open onto the
    world or the hill's hollow), the slot's mouth into the entrance hall aside. The walls, floors and ceilings below the
    main floor are lined by the part of the castle they are in; the entrance hall's own floor round the slot is kept."""
    inner = interior_cells()
    report = plinth_report(grid.cells, hall.footprint_columns())
    assert not report['outside'], ('a room below outside the plinth', report['outside'][:5])
    cut_into_air = sorted(p for p in inner if grid.is_air(*p))
    assert not cut_into_air, ('a room below cut where the plinth is not solid', cut_into_air[:5])
    assert not report['leaks'], ('a room below would open onto air', report['leaks'][:5])
    for p in inner:
        grid.clear(*p)
    shell = shell_cells(inner)
    for p in shell:
        if p[1] < MAIN_FLOOR_Y:
            grid.set(*p, _lining(p, inner))
    return len(inner)


# ---------------------------------------------------------------- the stairs

def build_stairs(grid):
    """The two flights' treads, the slot's rail and newel lantern in the entrance hall, and the stairs' lights."""
    for z, feet in STAIR_TREADS:
        for x in range(STAIR_X[0], STAIR_X[1] + 1):
            grid.set(x, feet - 1, z, _stairs('stone_brick_stairs', 'south'))
    for x, feet in STAIR_B:
        for z in range(STAIR_B_Z[0], STAIR_B_Z[1] + 1):
            grid.set(x, feet - 1, z, _stairs('stone_brick_stairs', 'west'))
    for x, y, z in RAIL:
        grid.set(x, y, z, G.state('dark_oak_fence', north=True, south=z < NEWEL[2], east=False, west=False,
                                  waterlogged=False))
    grid.set(NEWEL[0], NEWEL[1] + 1, NEWEL[2], _lantern(False))
    grid.set(STAIR_X[0], MAIN_FLOOR_Y - 2, 109, G.state('wall_torch', facing='east'))       # on the flight's west wall
    grid.set(50, KITCHEN_FEET + 3, 105, _lantern(True))                                    # the passage
    grid.set(56, KITCHEN_FEET - 2, STAIR_B_Z[0], G.state('wall_torch', facing='south'))    # the second flight
    grid.set(62, DUNGEON_FEET + 2, 105, G.state('wall_torch', facing='west'))               # the landing
    # the kitchen door: a chiseled keystone over it, on both faces of its wall
    grid.set(46, KITCHEN_FEET + 3, 103, 'minecraft:chiseled_stone_bricks')


# ---------------------------------------------------------------- the kitchens

def build_kitchen_tables(grid, table_cells):
    """The four long tables, cell for cell under the four house tables above (a spruce top slab at feet level), laid
    with gold platters and jugs down both edges and cakes and candles down the middle."""
    for x, z in table_cells:
        grid.set(x, KITCHEN_FEET, z, TABLE_SLAB)
    for run in table_groups({x for x, _ in table_cells}):
        mid = run[len(run) // 2]
        zs = sorted(z for x, z in table_cells if x == mid)
        for i, z in enumerate(zs):
            if i % 4 == 1:
                grid.set(mid, KITCHEN_FEET + 1, z, G.state('cake', bites=0))
            elif i % 8 == 3:
                grid.set(mid, KITCHEN_FEET + 1, z, G.state('candle', candles=3, lit=True, waterlogged=False))
            for x in run:
                if x == mid or (x, z) not in table_cells:
                    continue
                if i % 2 == 0:
                    grid.set(x, KITCHEN_FEET + 1, z, G.state('light_weighted_pressure_plate', power=0))
                elif i % 4 == 3:
                    grid.set(x, KITCHEN_FEET + 1, z, 'minecraft:flower_pot')


def build_kitchen_vault(grid):
    """Stone piers down the three aisles, dark-oak beams across the whole room over every row of them (resting on the
    piers' chiseled capitals), and a lantern hung under the beams over a table, two to a beam."""
    for i, z in enumerate(PIER_Z):
        for x in range(KITCHEN[0], KITCHEN[3] + 1):
            grid.set(x, KITCHEN_TOP, z, G.state('stripped_dark_oak_log', axis='x'))
        for x in PIER_X:
            for y in range(KITCHEN_FEET, KITCHEN_TOP):
                grid.set(x, y, z, 'minecraft:stone_bricks')
            grid.set(x, KITCHEN_TOP, z, 'minecraft:chiseled_stone_bricks')
        for x in ((49, 65) if i % 2 == 0 else (57, 73)):
            grid.set(x, KITCHEN_TOP - 1, z, _lantern(True))


def build_hearth(grid):
    """The great hearth in the north wall: a brick firebox 9 wide, 4 tall and 3 deep with three fires and a cauldron
    hung on a chain over the middle one, brick jambs and lintel, a brick mantel along the kitchen face with pots on it,
    and the chimney breast up to the ceiling."""
    x0, y0, z0, x1, y1, z1 = HEARTH
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 1):
            grid.set(x, KITCHEN_FLOOR_Y, z, BRICKS)                          # the hearth stone
        for y in range(y0, KITCHEN_TOP + 1):
            if not (x0 <= x <= x1 and y <= y1):
                grid.set(x, y, z1, BRICKS)                                   # jambs, lintel, breast on the north wall
        grid.set(x, y1 + 1, z1 + 1, _stairs('brick_stairs', 'north', 'top'))   # the mantel along the kitchen face
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 2):
            grid.set(x, y, z0 - 1, BRICKS)                                   # the firebox's back
        for z in range(z0, z1):
            grid.set(x, y1 + 1, z, BRICKS)                                   # and its roof
    for z in range(z0, z1):
        for y in range(y0, y1 + 1):
            grid.set(x0 - 1, y, z, BRICKS)
            grid.set(x1 + 1, y, z, BRICKS)
    cx = (x0 + x1) // 2
    for x in (cx - 2, cx, cx + 2):
        grid.set(x, y0, z0 + 1, G.state('campfire', facing='south', lit=True, signal_fire=False, waterlogged=False))
    grid.set(cx, y0 + 1, z0 + 1, G.state('water_cauldron', level=3))
    for y in range(y0 + 2, y1 + 1):
        grid.set(cx, y, z0 + 1, CHAIN)
    for x in (x0, cx, x1):
        grid.set(x, y1 + 2, z1 + 1, G.state('decorated_pot', cracked=False, facing='south', waterlogged=False))
    for x in (x0 - 2, x1 + 2):                                               # barrels of fuel by the jambs
        grid.set(x, y0, z1 + 1, G.state('barrel', facing='up', open=False))
        grid.set(x, y0 + 1, z1 + 1, 'minecraft:flower_pot')


def build_wall_groups(grid):
    """The ranges, dressers and scullery down the side walls (see WALL_GROUPS)."""
    for wx, facing, c, kind in WALL_GROUPS:
        out = 1 if facing == 'east' else -1
        zs = range(c - 2, c + 3)
        if kind == 'range':
            ovens = ('smoker', 'furnace', 'blast_furnace', 'furnace', 'smoker')
            for z, oven in zip(zs, ovens):
                grid.set(wx, KITCHEN_FEET, z, G.state(oven, facing=facing, lit=False))
                for y in range(KITCHEN_FEET + 2, KITCHEN_TOP + 1):
                    grid.set(wx, y, z, BRICKS)                               # the chimney breast
                grid.set(wx + out, KITCHEN_FEET + 3, z, _slab('brick_slab'))    # the hood's lip, head-high plus one
            for z in (c - 1, c + 1):
                grid.set(wx, KITCHEN_FEET + 1, z, 'minecraft:cauldron')       # pots on the stove
            continue
        if kind == 'dresser':
            for z in zs:
                end = z in (c - 2, c + 2)
                grid.set(wx, KITCHEN_FEET, z, G.state('barrel', facing='up', open=False) if end
                         else 'minecraft:spruce_planks')
            grid.set(wx, KITCHEN_FEET + 1, c - 1, 'minecraft:flower_pot')
            grid.set(wx, KITCHEN_FEET + 1, c + 1, G.state('decorated_pot', cracked=False, facing=facing,
                                                          waterlogged=False))
        else:                                                                # the scullery
            for z in zs:
                end = z in (c - 2, c + 2)
                grid.set(wx, KITCHEN_FEET, z, G.state('barrel', facing='up', open=False) if end
                         else G.state('water_cauldron', level=3))
        for z in zs:                                                         # a shelf of pots over it
            grid.set(wx, KITCHEN_FEET + 3, z, TABLE_SLAB)
            pot = G.state('decorated_pot', cracked=False, facing=facing, waterlogged=False)
            grid.set(wx, KITCHEN_FEET + 4, z, pot if (z - c) % 2 == 0 else 'minecraft:flower_pot')


WORK_COUNTERS = ((45, 53), (69, 77))   # x runs of the two work counters before the hearth, the hearth's front left open
WORK_COUNTER_Z = 47


def build_work_counters(grid):
    """Two long work counters across the north end, either side of the hearth's front: spruce tops on barrels at their
    ends, laid with what is being made (cakes, jugs, pots, a cauldron, candles)."""
    things = ('minecraft:flower_pot', G.state('cake', bites=0), None, 'minecraft:cauldron', None,
              G.state('decorated_pot', cracked=False, facing='south', waterlogged=False), None,
              G.state('candle', candles=2, lit=True, waterlogged=False), None)
    for x0, x1 in WORK_COUNTERS:
        for i, x in enumerate(range(x0, x1 + 1)):
            end = x in (x0, x1)
            grid.set(x, KITCHEN_FEET, WORK_COUNTER_Z, G.state('barrel', facing='up', open=False) if end else TABLE_SLAB)
            thing = things[i % len(things)]
            if thing and not end:
                grid.set(x, KITCHEN_FEET + 1, WORK_COUNTER_Z, thing)


def build_kitchen_corners(grid):
    """Barrels stacked in the corners of the south end, either side of the door."""
    for x, z, high in ((43, 101, True), (44, 102, False), (43, 102, True), (79, 101, True), (78, 102, False),
                       (79, 102, True), (77, 102, False)):
        grid.set(x, KITCHEN_FEET, z, G.state('barrel', facing='up', open=False))
        if high:
            grid.set(x, KITCHEN_FEET + 1, z, G.state('barrel', facing='up', open=False))


# ---------------------------------------------------------------- the dungeons

def _bars(z_neighbours_north, z_neighbours_south):
    return G.state('iron_bars', east=False, west=False, north=z_neighbours_north, south=z_neighbours_south,
                   waterlogged=False)


def build_cells(grid):
    """The cells' fronts (iron bars, one cell's holding an iron door standing open), the corridor's torches and its
    arched edges, the statue alcoves' lintels, and each cell's few things (chains, straw, a skull, a bucket...)."""
    for cell in CELLS:
        side, front, (cx0, cx1), (z0, z1) = cell
        for x, y, z in cell_front(cell):
            grid.set(x, y, z, _bars(True, True))
        back = cx0 if side == 'west' else cx1                                # the cell's last cell against its back
        zc = (z0 + z1) // 2
        k = CELLS.index(cell)
        if k % 2 == 0 and cell != OPEN_CELL:                                 # manacles (the chest stands in the open one)
            grid.set(back, CELL_TOP, zc, CHAIN)
            grid.set(back, CELL_TOP - 1, zc, CHAIN)
        if k % 3 == 0:
            grid.set(back, DUNGEON_FEET, z0, G.state('hay_block', axis='z'))
        if k % 3 == 1:                                                       # a skull looking out at the corridor
            grid.set(back, DUNGEON_FEET, z1, G.state('skeleton_skull', rotation=12 if side == 'west' else 4,
                                                     powered=False))
        if k in (3, 6):
            grid.set(back, DUNGEON_FEET, zc, 'minecraft:cauldron')
        if k in (1, 5):
            grid.set(back, CELL_TOP, z0, 'minecraft:cobweb')
    # the open cell: an iron door standing open in the middle of its bars
    side, front, _, _ = OPEN_CELL
    for y, half in ((DUNGEON_FEET, 'lower'), (DUNGEON_FEET + 1, 'upper')):
        grid.set(front, y, OPEN_DOOR_Z, G.state('iron_door', facing='east', half=half, hinge='left', open=True,
                                                powered=False))
    # the corridor's edges arched in under its ceiling (the landing's west side is the flight's mouth), and its torches
    for z in range(CORRIDOR[2], LANDING[5] + 1):
        if z <= CORRIDOR[5]:
            grid.set(CORRIDOR[0], CORRIDOR_TOP, z, _stairs('stone_brick_stairs', 'west', 'top'))
        grid.set(CORRIDOR[3], CORRIDOR_TOP, z, _stairs('stone_brick_stairs', 'east', 'top'))
    for z in WEST_TORCH_Z:
        grid.set(CORRIDOR[0], DUNGEON_FEET + 2, z, G.state('wall_torch', facing='east'))
    for z in EAST_TORCH_Z:
        grid.set(CORRIDOR[3], DUNGEON_FEET + 2, z, G.state('wall_torch', facing='west'))
    for z in ALCOVE_Z:
        for x in (59, 63):
            grid.set(x, CELL_TOP + 1, z, 'minecraft:chiseled_stone_bricks')
    # the Slytherin door, on the corridor's side: a green banner over it
    grid.set(61, CORRIDOR_TOP, SL_DOOR[2] + 1, G.state('green_wall_banner', facing='south'))


# ---------------------------------------------------------------- the Slytherin common room

def build_slytherin_vault(grid):
    """The low vault: four polished-deepslate piers, ribs of deepslate tiles along their lines under the ceiling with a
    chiseled boss over each pier, upside-down stairs springing the ribs from the piers and the walls; a green coffer in
    the crown of every bay (green glass in the ceiling, a sea lantern over it) and a lake window in every bay of both
    side walls (green glass in the wall, a sea lantern behind it); faint light blocks under the coffers carry their light
    into the room."""
    top = SLYTHERIN_TOP
    for x in SL_RIB_X:
        for z in range(SL_Z[0], SL_Z[1] + 1):
            grid.set(x, top, z, 'minecraft:deepslate_tiles')
    for z in SL_RIB_Z:
        for x in range(SL_X[0], SL_X[1] + 1):
            grid.set(x, top, z, 'minecraft:deepslate_tiles')
    for px, pz in SL_PIERS:
        for y in range(DUNGEON_FEET, top):
            grid.set(px, y, pz, 'minecraft:polished_deepslate')
        grid.set(px, top, pz, 'minecraft:chiseled_deepslate')
        for dx, dz, facing in ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north')):
            grid.set(px + dx, top - 1, pz + dz, _stairs('polished_deepslate_stairs', facing, 'top'))
    for x in SL_RIB_X:                                                       # springers at the walls
        grid.set(x, top - 1, SL_Z[0], _stairs('polished_deepslate_stairs', 'north', 'top'))
        grid.set(x, top - 1, SL_Z[1], _stairs('polished_deepslate_stairs', 'south', 'top'))
    for z in SL_RIB_Z:
        grid.set(SL_X[0], top - 1, z, _stairs('polished_deepslate_stairs', 'west', 'top'))
        grid.set(SL_X[1], top - 1, z, _stairs('polished_deepslate_stairs', 'east', 'top'))
    for x in SL_COFFER_X:
        for z in SL_COFFER_Z:
            grid.set(x, top + 1, z, 'minecraft:green_stained_glass')
            grid.set(x, top + 2, z, 'minecraft:sea_lantern')
    for x in (50, 61, 72):
        for z in (46, 53, 60):
            grid.set(x, top, z, G.state('light', level=10, waterlogged=False))
    for z in SL_WINDOW_Z:
        for y in range(DUNGEON_FEET + 1, top):
            grid.set(SL_X[0] - 1, y, z, 'minecraft:green_stained_glass')
            grid.set(SL_X[0] - 2, y, z, 'minecraft:sea_lantern')
            grid.set(SL_X[1] + 1, y, z, 'minecraft:green_stained_glass')
            grid.set(SL_X[1] + 2, y, z, 'minecraft:sea_lantern')


def build_slytherin_fireplace(grid):
    """The dark fireplace in the north wall: a polished-blackstone surround with chiseled corners, three soul fires on a
    blackstone hearth in the firebox, a blackstone mantel shelf and a wither skeleton's skull on it."""
    x0, y0, z0, x1, y1, z1 = SL_HEARTH
    for x in range(x0 - 2, x1 + 3):
        for y in range(y0, SLYTHERIN_TOP + 1):
            if not (x0 <= x <= x1 and y <= y1):
                corner = x in (x0 - 2, x1 + 2) and y == y1 + 1
                grid.set(x, y, z1, 'minecraft:chiseled_polished_blackstone' if corner
                         else 'minecraft:polished_blackstone_bricks')
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            if z <= z1 or x0 <= x <= x1:
                grid.set(x, DUNGEON_FLOOR_Y, z, 'minecraft:blackstone')
        grid.set(x, y1 + 1, z1 + 1, _slab('polished_blackstone_brick_slab'))     # the mantel shelf
    for x in range(x0, x1 + 1):
        for z in range(z0, z1):
            grid.set(x, y1 + 1, z, 'minecraft:polished_blackstone_bricks')
        for y in range(y0, y1 + 2):
            grid.set(x, y, z0 - 1, 'minecraft:polished_blackstone_bricks')
        grid.set(x, y0, z0, G.state('soul_campfire', facing='south', lit=True, signal_fire=False, waterlogged=False))
    for z in range(z0, z1):
        for y in range(y0, y1 + 1):
            grid.set(x0 - 1, y, z, 'minecraft:polished_blackstone_bricks')
            grid.set(x1 + 1, y, z, 'minecraft:polished_blackstone_bricks')
    grid.set((x0 + x1) // 2, y1 + 2, z1 + 1, G.state('wither_skeleton_skull', rotation=0, powered=False))


def build_slytherin_furniture(grid):
    """Sofas and armchairs round the fire on a green rug with a low table and green candles; study tables with chairs in
    the side bays and a chess table either side of the runner; bookshelves down the east wall between the lake windows;
    a green carpet runner from the door; house banners on the piers and either side of the door."""
    sofa = _stairs('dark_oak_stairs', 'south')
    for x in (57, 58, 59, 63, 64, 65):
        grid.set(x, DUNGEON_FEET, 49, sofa)
    for z in (46, 47):
        grid.set(56, DUNGEON_FEET, z, _stairs('dark_oak_stairs', 'west'))
        grid.set(66, DUNGEON_FEET, z, _stairs('dark_oak_stairs', 'east'))
    for x in (60, 61, 62):
        grid.set(x, DUNGEON_FEET, 47, _slab('dark_oak_slab'))
    grid.set(61, DUNGEON_FEET + 1, 47, G.state('green_candle', candles=3, lit=True, waterlogged=False))
    for x in range(56, 67):
        for z in range(45, 49):
            if grid.is_air(x, DUNGEON_FEET, z):
                grid.set(x, DUNGEON_FEET, z, 'minecraft:green_carpet')
    for x in (60, 61, 62):
        for z in range(51, SL_Z[1] + 1):
            if grid.is_air(x, DUNGEON_FEET, z):
                grid.set(x, DUNGEON_FEET, z, 'minecraft:green_carpet')
    for z in (53, 60):                                                       # study tables in the side bays
        for xs in ((49, 50, 51), (71, 72, 73)):
            for x in xs:
                grid.set(x, DUNGEON_FEET, z, _slab('dark_oak_slab'))
                grid.set(x, DUNGEON_FEET, z - 1, _stairs('dark_oak_stairs', 'north'))
                grid.set(x, DUNGEON_FEET, z + 1, _stairs('dark_oak_stairs', 'south'))
            grid.set(xs[1], DUNGEON_FEET + 1, z, G.state('green_candle', candles=2, lit=True, waterlogged=False))
    for x in (57, 65):                                                       # chess tables either side of the runner
        grid.set(x, DUNGEON_FEET, 60, _slab('dark_oak_slab'))
        grid.set(x, DUNGEON_FEET, 59, _stairs('dark_oak_stairs', 'north'))
        grid.set(x, DUNGEON_FEET, 61, _stairs('dark_oak_stairs', 'south'))
        grid.set(x, DUNGEON_FEET + 1, 60, G.state('green_candle', candles=1, lit=True, waterlogged=False))
    for z in range(SL_Z[0], SL_Z[1] + 1):                                    # bookshelves on the east wall
        if z in SL_WINDOW_Z:
            continue
        for y in range(DUNGEON_FEET, DUNGEON_FEET + 3):
            grid.set(SL_X[1], y, z, 'minecraft:bookshelf')
    for x in SL_RIB_X:
        grid.set(x, DUNGEON_FEET + 2, SL_RIB_Z[0] - 1, G.state('green_wall_banner', facing='north'))
    for x in (58, 64):
        grid.set(x, DUNGEON_FEET + 2, SL_Z[1], G.state('green_wall_banner', facing='north'))


# ---------------------------------------------------------------- placing it

def place_below(grid):
    """Carve and furnish the stairs, the kitchens, the dungeons and the Slytherin common room into the castle grid.
    Returns {'elf_spots', 'chests', 'student_spots', 'statues'} to merge into the castle's anchors."""
    tables = hall_table_cells(grid.cells)
    carved = carve(grid)
    build_stairs(grid)
    build_kitchen_vault(grid)
    build_kitchen_tables(grid, tables)
    build_hearth(grid)
    build_work_counters(grid)
    build_wall_groups(grid)
    build_kitchen_corners(grid)
    build_cells(grid)
    build_slytherin_vault(grid)
    build_slytherin_fireplace(grid)
    build_slytherin_furniture(grid)
    lights = hall.light_dark_floors(grid, light_boxes())
    place_below.stats = {
        'carved': carved, 'tables': len(table_groups({x for x, _ in tables})), 'table_cells': len(tables),
        'barred_cells': len(barred_cells(grid.cells)), 'green_lights': green_lights(grid.cells), 'lights': lights,
    }
    statues = []
    for z in ALCOVE_Z:
        statues.append({'at': [59, DUNGEON_FEET, z], 'yaw': -90})           # the west alcoves face east
        statues.append({'at': [63, DUNGEON_FEET, z], 'yaw': 90})            # the east ones west
    return {
        'elf_spots': [list(p) for p in ELF_SPOTS],
        'chests': [dict(DUNGEON_CHEST)],
        'student_spots': [list(p) for p in SL_SPOTS],
        'statues': statues,
    }
