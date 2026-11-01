"""The castle Library and its Restricted Section, carved into the hollow hill under the gatehouse.

    from mini_hogwarts import rooms_library
    anchors = rooms_library.place_library(grid)     # grid in the castle frame, after the Great Hall and staircase

WHY THE HILL
------------
The plan's default shell, the gatehouse's upper floors, is one storey of 25 x 5 x 4 cut into three rooms (and its
ground floor is kept for Task 7's classrooms), so tall shelving, a mezzanine and ladders cannot fit. The largest
interior left after the Great Hall wing, the Grand Staircase, the bridge tower (Gryffindor) and the Potions apse is
the hill the gatehouse stands on: a one-block shell of stone and grass, hollow from its foot to the gatehouse's
ground floor (y 25) and, under the apse tower, to the Potions classroom's floor (y 32). The library is carved into
it below the gatehouse, the way the film's castle sits on its rock, and never touches the gatehouse or the apse.

WHAT IT BECOMES
---------------
    floor y 11 (feet 12), mezzanine floor y 17 (feet 18), ceiling y 23 (the gatehouse's ground floor is y 25)

    z 174..185   the tunnel from the grounds door in the hill's north face, beside the viaduct's last pier
    z 185        the north wall: shelving to the ceiling round the tunnel's mouth (x 33..35)
    z 186..195   the hall (x 30..46): wall shelving to the ceiling, a U-shaped mezzanine three wide on dark-oak posts
                 and under-mezzanine stacks, reached by two ladders that climb the wall shelves through hatches;
                 two reading tables with lanterns either side of the public Pensieve on its dais under a lantern
                 chandelier; Madam Pince's desk by the door; study carrels with candles in the bays
    z 196        the Restricted Section's screen: iron bars floor to ceiling, one iron door in a dark-oak frame,
                 opened by a button on each side
    z 197..203   the Restricted Section in the Ollivanders shelving language (pensieve_scenes/diagon_set.py: banks of
                 mud bricks, tuff bricks and nearly empty chiseled bookshelves, banded by stripped dark oak, dark-oak
                 uprights), two shelf-lined closets and an alcove (x 34..42, z 200..203) under the apse with the
                 library chest, a ladder on a chain rail, one hanging lantern and a few cobwebs

Everything round the rooms is filled with stone up to the hill's shell (the hollow becomes rock the library is cut
into), so no dark pocket is left beside, above or below it; the corner towers' shafts, which open into the hollow
from above, are left as they were. The only old blocks that change are the hill's shell where the tunnel and its
portal cut through it; CHANGED_BOXES lists exactly those plus the rooms' boxes, and the viaduct's last pier beside
the portal is left whole.
"""

import random
from collections import deque

from . import hall
from .grid import air_at
from .hall import G

# ---------------------------------------------------------------- levels (castle frame)
FLOOR_Y = 11                           # the floor block layer; the hollow hill is closed in every layer from here up
FEET = FLOOR_Y + 1                     # 12
MEZZ_Y = 17                            # the mezzanine's floor block layer
MEZZ_FEET = MEZZ_Y + 1                 # 18
CEIL_Y = 23                            # the ceiling block layer; y 24 is the void under the gatehouse's oak floor
TOP = CEIL_Y - 1                       # 22, the top interior layer
CAVE_TOP = 31                          # the hollow under the apse reaches the Potions floor (y 32)

# ---------------------------------------------------------------- plan
HALL_X = (30, 46)                      # the hall's interior (both levels and the Restricted strip), x
HALL_Z = (186, 199)
AXIS_X = 38                            # the hall's centre line
RING_X = (29, 47)                      # the shelving walls round the hall
RING_Z = (185, 200)
SCREEN_Z = 196                         # the Restricted Section's bars
ALCOVE_X = (34, 42)                    # the Restricted Section's alcove, under the apse tower
ALCOVE_Z = (200, 203)
ALCOVE_RING_X = (33, 43)
ALCOVE_BACK_Z = 204
STACKS_Z = (198, 199)                  # the alcove's side walls run on as freestanding stacks to here

TUNNEL_X = (33, 35)                    # 3 wide, 4 tall
TUNNEL_TOP = 15
PORTAL_Z = 174                         # the portal's face on the hill's north slope (the slope outside is z 173)
DOOR_Z = 175                           # the grounds door, a single dark-oak door on the tunnel's axis
TUNNEL_END_Z = 185                     # the mouth in the hall's north wall

# the mezzanine: a U along the west, north and east walls
MEZZ_W = (30, 32)
MEZZ_E = (44, 46)
MEZZ_N = (186, 187)
MEZZ_Z = (186, 195)
UPRIGHTS_X = (32, 36, 40, 44)          # north wall uprights (and the north mezzanine edge's posts), symmetric on x 38
UPRIGHTS_Z = (189, 193)                # side wall uprights (and the under-mezzanine stacks)
LADDERS = ((30, 191, 'east'), (46, 191, 'west'))     # wall ladders through the mezzanine's hatches

# the Restricted Section's door
DOOR_X = AXIS_X
FRAME_X = (37, 39)

# the cave fill
CAVE_SEED = (40, 195)                  # an air cell of the hollow hill in every layer from FLOOR_Y to CAVE_TOP
CAVE_BOX = (15, 66, 170, 212)          # x0, x1, z0, z1 the hill's hollow fits in (a layer that leaks past it is open)

# ---------------------------------------------------------------- blocks
SHELF = 'minecraft:bookshelf'
UPRIGHT = G.state('dark_oak_log', axis='y')
FLOOR_FIELD = 'minecraft:spruce_planks'
FLOOR_EDGE = 'minecraft:dark_oak_planks'
CEIL_PANEL = 'minecraft:spruce_planks'
MEZZ_FLOOR = 'minecraft:dark_oak_planks'
ROCK = 'minecraft:stone'
MASONRY = 'minecraft:stone_bricks'
OLL_BANDS = (14, 18, TOP)              # the Restricted shelving's stripped-dark-oak bands (Ollivanders: every 4)
CEIL_BEAMS_Z = (189, 193, 197, 201)


def _band(axis):
    return G.state('stripped_dark_oak_log', axis=axis)


def _lantern(hanging):
    return G.state('lantern', hanging=hanging, waterlogged=False)


CHAIN = G.state('chain', axis='y', waterlogged=False)


def _stairs(name, facing, half):
    return G.state(name, facing=facing, half=half, shape='straight', waterlogged=False)


def wand_shelf(rnd, facing):
    """One cell of a wand-box bank, the Ollivanders mix (diagon_set.wand_shelf, same odds; that module builds both
    Wand-memory sets when imported, so the six lines live here): mostly mud bricks, some tuff bricks and dark
    pigeonholes (a chiseled bookshelf with a box or two left in it)."""
    r = rnd.random()
    if r < 0.62:
        return 'minecraft:mud_bricks'
    if r < 0.78:
        return 'minecraft:tuff_bricks'
    return G.state('chiseled_bookshelf', facing=facing, **{'slot_%d_occupied' % k: rnd.random() < 0.2 for k in range(6)})


SHELF_IDS = frozenset(('bookshelf', 'chiseled_bookshelf', 'mud_bricks', 'tuff_bricks'))

# One walkable cell in each part, for the reachability check; the chest, the Pensieve and the reading spots.
LIBRARY_CELLS = {
    'grounds door': (34, FEET, 176),
    'hall': (AXIS_X, FEET, 188),
    'west mezzanine': (31, MEZZ_FEET, 190),
    'north mezzanine': (AXIS_X, MEZZ_FEET, 186),
    'east mezzanine': (45, MEZZ_FEET, 190),
    'restricted strip': (AXIS_X, FEET, 198),
    'restricted alcove': (AXIS_X, FEET, 202),
}
RESTRICTED_INSIDE = (AXIS_X, FEET, 197)         # the cell inside the door
CHEST = (AXIS_X, FEET, 203)                      # against the alcove's back shelves, facing the door
PENSIEVE = (AXIS_X, FEET + 1, 191)               # on the dais block at the hall's centre
STUDENT_SPOTS = ((36, FEET, 190), (40, FEET, 192), (31, FEET, 191), (AXIS_X, FEET, 188),
                 (31, MEZZ_FEET, 190), (45, MEZZ_FEET, 194))


def _box_cells(x0, y0, z0, x1, y1, z1):
    return {(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)}


def hall_box():
    return (HALL_X[0], FEET, HALL_Z[0], HALL_X[1], TOP, HALL_Z[1])


def alcove_box():
    return (ALCOVE_X[0], FEET, ALCOVE_Z[0], ALCOVE_X[1], TOP, ALCOVE_Z[1])


def tunnel_box():
    return (TUNNEL_X[0], FEET, PORTAL_Z, TUNNEL_X[1], TUNNEL_TOP, TUNNEL_END_Z)


def restricted_boxes():
    """The Restricted Section's interior: the strip behind the bars and the alcove."""
    return [(HALL_X[0], FEET, SCREEN_Z + 1, HALL_X[1], TOP, HALL_Z[1]), alcove_box()]


def room_boxes():
    """The library's interior boxes (inclusive x0, y0, z0, x1, y1, z1): the no-dark-floor check covers these."""
    return [hall_box(), alcove_box(), tunnel_box()]


def interior_cells():
    """Every cell the rooms keep for air or furniture; the rock fill never goes here."""
    out = set()
    for b in room_boxes():
        out |= _box_cells(*b)
    return out


def rooms_with_walls():
    """The hall's and the alcove's boxes with their walls, floor and ceiling: every cell of them was air of the hollow
    hill before (build_castle.verify_library checks it), so none of them shows outside the hill."""
    return [(RING_X[0], FLOOR_Y, RING_Z[0], RING_X[1], CEIL_Y, RING_Z[1]),
            (ALCOVE_RING_X[0], FLOOR_Y, RING_Z[1], ALCOVE_RING_X[1], CEIL_Y, ALCOVE_BACK_Z)]


def changed_boxes():
    """(name, inclusive box) for build_castle.CHANGED_ON_PURPOSE: the rooms with their walls, floor and ceiling, and
    the tunnel with its portal through the hill's shell. Only the tunnel's box holds old blocks (the shell); the
    viaduct's last pier, east of it (x 37..39 at z 175), is outside every box, so the castle check holds it whole."""
    hall, alcove = rooms_with_walls()
    return [
        ('library: the hall, the Restricted strip and their shelving walls, floor and ceiling, in the gatehouse hill',
         hall),
        ("library: the Restricted Section's alcove under the apse tower", alcove),
        ("library: the tunnel and its portal through the hill's north face (west of the viaduct's last pier)",
         (TUNNEL_X[0] - 1, FLOOR_Y, PORTAL_Z - 1, TUNNEL_X[1] + 1, TUNNEL_TOP + 2, TUNNEL_END_Z - 1)),
    ]


# ---------------------------------------------------------------- the hollow hill

def cave_layer(cells, y):
    """The hollow hill at height y: the 2D flood from CAVE_SEED through air. None when it leaks out of CAVE_BOX
    (the layer is not closed) or the seed is not air."""
    x0, x1, z0, z1 = CAVE_BOX
    if not air_at(cells, (CAVE_SEED[0], y, CAVE_SEED[1])):
        return None
    seen = {CAVE_SEED}
    queue = deque([CAVE_SEED])
    while queue:
        x, z = queue.popleft()
        for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if n in seen or not air_at(cells, (n[0], y, n[1])):
                continue
            if not (x0 <= n[0] <= x1 and z0 <= n[1] <= z1):
                return None
            seen.add(n)
            queue.append(n)
    return seen


def cave_plan(cells):
    """{y: {(x, z)}} the hollow hill in every layer FLOOR_Y..CAVE_TOP, and the shaft columns: the cells of the top
    layer under the gatehouse (y 23) that open upwards into a space that is not the hollow (the corner towers'
    insides, two air cells tall at least; the one-cell void under the gatehouse's oak floor does not count). The fill
    leaves the shafts' columns alone."""
    layers = {}
    for y in range(FLOOR_Y, CAVE_TOP + 1):
        layer = cave_layer(cells, y)
        assert layer is not None, ('the hollow hill is not closed at y %d' % y)
        layers[y] = layer
    shafts = set()
    for y in range(FLOOR_Y, CAVE_TOP):
        for x, z in layers[y]:
            if (air_at(cells, (x, y + 1, z)) and (x, z) not in layers[y + 1]
                    and air_at(cells, (x, y + 2, z))):
                shafts.add((x, z))
    return layers, shafts


def fill_rock(grid, layers, shafts, keep):
    """Stone in every empty cell of the hollow hill that the rooms do not keep, except the shafts' columns. A cell
    holding anything, an air marker (minecraft:cave_air: interior air the world must clear) included, is left."""
    n = 0
    for y, layer in layers.items():
        for x, z in layer:
            if (x, z) in shafts or (x, y, z) in keep or (x, y, z) in grid.cells:
                continue
            grid.set(x, y, z, ROCK)
            n += 1
    return n


# ---------------------------------------------------------------- floor, ceiling and walls

def _plan():
    """Every (x, z) of the rooms' plan with their walls: the hall's ring and the alcove's."""
    out = {(x, z) for x in range(RING_X[0], RING_X[1] + 1) for z in range(RING_Z[0], RING_Z[1] + 1)}
    out |= {(x, z) for x in range(ALCOVE_RING_X[0], ALCOVE_RING_X[1] + 1)
            for z in range(RING_Z[1], ALCOVE_BACK_Z + 1)}
    return out


def floor_and_ceiling(grid, rnd):
    """The floor: spruce in the open middle of the hall, dark oak under the mezzanine and the shelves, and the
    Ollivanders dark-oak/spruce mix in the Restricted Section. The ceiling: spruce panels between stripped dark-oak
    beams over the uprights."""
    for x, z in _plan():
        if z > SCREEN_Z:
            s = FLOOR_EDGE if rnd.random() < 0.55 else FLOOR_FIELD
        elif MEZZ_W[1] < x < MEZZ_E[0] and MEZZ_N[1] < z <= SCREEN_Z - 1:
            s = FLOOR_FIELD
        else:
            s = FLOOR_EDGE
        grid.set(x, FLOOR_Y, z, s)
        grid.set(x, CEIL_Y, z, _band('x') if z in CEIL_BEAMS_Z else CEIL_PANEL)


def _public_cell(y, upright, axis):
    if y in (MEZZ_Y, TOP):
        return _band(axis)
    return UPRIGHT if upright else SHELF


def build_public_walls(grid):
    """The hall's three shelving walls, floor to ceiling: bookshelves between dark-oak uprights, a stripped dark-oak
    band at the mezzanine's floor line and a cornice under the ceiling. The north wall's uprights frame the tunnel's
    mouth (x 33..35, y 12..15), with a lintel over it. The side walls end at the screen on a full-height post."""
    for y in range(FEET, TOP + 1):
        for z in range(RING_Z[0], SCREEN_Z):
            up = z == RING_Z[0] or z in UPRIGHTS_Z
            grid.set(RING_X[0], y, z, _public_cell(y, up, 'z'))
            grid.set(RING_X[1], y, z, _public_cell(y, up, 'z'))
        for x in (RING_X[0], RING_X[1]):
            grid.set(x, y, SCREEN_Z, UPRIGHT)
        for x in range(RING_X[0] + 1, RING_X[1]):
            if TUNNEL_X[0] <= x <= TUNNEL_X[1] and y <= TUNNEL_TOP:
                grid.clear(x, y, RING_Z[0])
            elif TUNNEL_X[0] <= x <= TUNNEL_X[1] and y == TUNNEL_TOP + 1:
                grid.set(x, y, RING_Z[0], _band('x'))
            else:
                grid.set(x, y, RING_Z[0], _public_cell(y, x in UPRIGHTS_X, 'x'))


def _restricted_walls():
    """[(x, z, facing, axis, upright)] every cell of the Restricted Section's shelving: the side walls behind the
    bars, the hall's south wall either side of the alcove, the alcove's sides (run on north as freestanding stacks)
    and its back wall."""
    out = []
    for z in range(SCREEN_Z + 1, RING_Z[1]):
        out.append((RING_X[0], z, 'east', 'z', False))
        out.append((RING_X[1], z, 'west', 'z', False))
    for x in range(RING_X[0], ALCOVE_RING_X[0]):
        out.append((x, RING_Z[1], 'north', 'x', x == RING_X[0]))
    for x in range(ALCOVE_RING_X[1] + 1, RING_X[1] + 1):
        out.append((x, RING_Z[1], 'north', 'x', x == RING_X[1]))
    for z in range(STACKS_Z[0], ALCOVE_BACK_Z + 1):
        end = z in (STACKS_Z[0], RING_Z[1], ALCOVE_BACK_Z)
        out.append((ALCOVE_RING_X[0], z, 'east', 'z', end))
        out.append((ALCOVE_RING_X[1], z, 'west', 'z', end))
    for x in range(ALCOVE_RING_X[0] + 1, ALCOVE_RING_X[1]):
        out.append((x, ALCOVE_BACK_Z, 'north', 'x', x == AXIS_X))
    return out


def build_restricted_walls(grid, rnd):
    """The Restricted Section's shelving to the ceiling in the Ollivanders language."""
    for x, z, facing, axis, upright in _restricted_walls():
        for y in range(FEET, TOP + 1):
            if y in OLL_BANDS:
                grid.set(x, y, z, _band(axis))
            elif upright:
                grid.set(x, y, z, UPRIGHT)
            else:
                grid.set(x, y, z, wand_shelf(rnd, facing))


def build_screen(grid):
    """The bars across the hall at z 196, floor to ceiling, with one iron door in a dark-oak frame (full-height posts,
    a lintel over the door) and a dark-oak button on each post's outer face, beside the door: a button powers its
    post, the post powers the door's upper half."""
    z = SCREEN_Z
    for y in range(FEET, TOP + 1):
        for x in range(HALL_X[0], HALL_X[1] + 1):
            if x in FRAME_X:
                grid.set(x, y, z, UPRIGHT)
            elif x == DOOR_X and y < FEET + 2:
                half = 'lower' if y == FEET else 'upper'
                grid.set(x, y, z, G.state('iron_door', facing='south', half=half, hinge='left', open=False,
                                          powered=False))
            elif x == DOOR_X and y == FEET + 2:
                grid.set(x, y, z, 'minecraft:dark_oak_planks')
            else:
                grid.set(x, y, z, 'minecraft:iron_bars')
    grid.set(FRAME_X[0], FEET + 1, z - 1, G.state('dark_oak_button', face='wall', facing='north', powered=False))
    grid.set(FRAME_X[1], FEET + 1, z + 1, G.state('dark_oak_button', face='wall', facing='south', powered=False))


def connect_bars_and_fences(grid, cells):
    """Set the side properties of iron bars and dark-oak fences: each joins its own kind and the full blocks next to
    it (logs, planks, shelves, masonry)."""
    full = ('_log', '_planks', 'bookshelf', 'mud_bricks', 'tuff_bricks', 'stone_bricks', 'stone', '_wood')
    for x, y, z in cells:
        s = grid.get(x, y, z)
        kind = s.split('[', 1)[0]
        props = {}
        for side, (dx, dz) in (('east', (1, 0)), ('west', (-1, 0)), ('south', (0, 1)), ('north', (0, -1))):
            n = grid.get(x + dx, y, z + dz)
            nid = n.split('[', 1)[0]
            props[side] = nid == kind or (nid.endswith(full) and '_slab' not in nid and '_stairs' not in nid)
        grid.set(x, y, z, G.state(kind, waterlogged=False, **props))


# ---------------------------------------------------------------- the mezzanine

def _mezz_plan():
    west = {(x, z) for x in range(MEZZ_W[0], MEZZ_W[1] + 1) for z in range(MEZZ_Z[0], MEZZ_Z[1] + 1)}
    east = {(x, z) for x in range(MEZZ_E[0], MEZZ_E[1] + 1) for z in range(MEZZ_Z[0], MEZZ_Z[1] + 1)}
    north = {(x, z) for x in range(MEZZ_W[1] + 1, MEZZ_E[0]) for z in range(MEZZ_N[0], MEZZ_N[1] + 1)}
    return west | east | north


def _mezz_edge():
    """[(x, z, open side)] the mezzanine's inner edge: the rail stands on these cells."""
    out = [(MEZZ_W[1], z, 'east') for z in range(MEZZ_N[1], MEZZ_Z[1] + 1)]
    out += [(MEZZ_E[0], z, 'west') for z in range(MEZZ_N[1], MEZZ_Z[1] + 1)]
    out += [(x, MEZZ_N[1], 'south') for x in range(MEZZ_W[1] + 1, MEZZ_E[0])]
    return out


def mezzanine_posts():
    """The dark-oak posts under the mezzanine's edge: its corners, the north edge's uprights and the stack ends."""
    posts = {(x, MEZZ_N[1]) for x in UPRIGHTS_X}
    posts |= {(MEZZ_W[1], MEZZ_Z[1]), (MEZZ_E[0], MEZZ_Z[1])}
    posts |= {(x, z) for x in (MEZZ_W[1], MEZZ_E[0]) for z in UPRIGHTS_Z}
    return posts


def build_mezzanine(grid):
    """A U-shaped gallery three wide at y 17 along the west, north and east walls: dark-oak floor on posts and on the
    under-mezzanine stacks (bookshelves, three long, at the side walls' uprights), upside-down stair corbels under
    its edge between the posts, a dark-oak rail with a lantern on each corner post, and two ladders climbing the
    side walls' shelves from the floor through hatches in the gallery to near the ceiling. Returns the rail's
    cells."""
    hatches = {(x, z) for x, z, _ in LADDERS}
    for x, z in _mezz_plan():
        if (x, z) not in hatches:
            grid.set(x, MEZZ_Y, z, MEZZ_FLOOR)
    posts = mezzanine_posts()
    for x, z in posts:
        for y in range(FEET, MEZZ_Y):
            grid.set(x, y, z, UPRIGHT)
    for z in UPRIGHTS_Z:                                          # the stacks, from the wall to the end post
        for x in (MEZZ_W[0], MEZZ_W[0] + 1, MEZZ_E[1] - 1, MEZZ_E[1]):
            for y in range(FEET, MEZZ_Y):
                grid.set(x, y, z, SHELF)
    back = {'east': 'west', 'west': 'east', 'south': 'north'}    # a corbel's lower half sits on the gallery side
    for x, z, side in _mezz_edge():
        if (x, z) not in posts:
            grid.set(x, MEZZ_Y - 1, z, _stairs('dark_oak_stairs', back[side], 'top'))
    rail = []
    for x, z, side in _mezz_edge():
        grid.set(x, MEZZ_FEET, z, 'minecraft:dark_oak_fence')
        rail.append((x, MEZZ_FEET, z))
    for x, z in ((MEZZ_W[1], MEZZ_N[1]), (MEZZ_E[0], MEZZ_N[1]), (MEZZ_W[1], MEZZ_Z[1]), (MEZZ_E[0], MEZZ_Z[1])):
        grid.set(x, MEZZ_FEET + 1, z, _lantern(False))
    for x, z, facing in LADDERS:
        for y in range(FEET, TOP):
            grid.set(x, y, z, G.state('ladder', facing=facing, waterlogged=False))
    return rail


# ---------------------------------------------------------------- the hall's furniture

def _chair(x, z, facing):
    return (x, FEET, z, _stairs('dark_oak_stairs', facing, 'bottom'))


TABLE_X = (34, 42)
TABLE_Z = (190, 194)
DESK_X = (41, 42, 43)                  # Madam Pince's counter, in the bay between the north posts x 40 and 44


def build_reading_tables(grid):
    """Two long reading tables either side of the dais (dark-oak top slabs), chairs alternating down both sides with
    their backs to the aisles, and two lanterns on each table."""
    top = G.state('dark_oak_slab', type='top', waterlogged=False)
    for tx in TABLE_X:
        for z in range(TABLE_Z[0], TABLE_Z[1] + 1):
            grid.set(tx, FEET, z, top)
        for z in (TABLE_Z[0], TABLE_Z[0] + 3):
            grid.set(tx, FEET + 1, z, _lantern(False))
        for z in range(TABLE_Z[0], TABLE_Z[1] + 1):
            if (z - TABLE_Z[0]) % 2 == 0:
                x, y, zz, s = _chair(tx - 1, z, 'west')
            else:
                x, y, zz, s = _chair(tx + 1, z, 'east')
            grid.set(x, y, zz, s)


def build_pensieve_dais(grid):
    """The focal point: a chiseled stone-brick block for the public Pensieve to stand on, ringed by a step of stone
    brick slabs, under a chandelier of five lanterns on chains from the ceiling."""
    px, py, pz = PENSIEVE
    for x in range(px - 1, px + 2):
        for z in range(pz - 1, pz + 2):
            grid.set(x, FEET, z, G.state('stone_brick_slab', type='bottom', waterlogged=False))
    grid.set(px, FEET, pz, 'minecraft:chiseled_stone_bricks')
    for y in range(TOP - 2, TOP + 1):
        grid.set(px, y, pz, CHAIN)
    grid.set(px, TOP - 3, pz, _lantern(True))
    for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        for y in range(TOP - 1, TOP + 1):
            grid.set(px + dx, y, pz + dz, CHAIN)
        grid.set(px + dx, TOP - 2, pz + dz, _lantern(True))


def build_desk_and_carrels(grid):
    """Madam Pince's desk by the door (a dark-oak counter with a lantern, her chair and a lectern behind it, under the
    north gallery), and four study carrels in the side bays under the mezzanine (a top-slab desk against the
    shelves with a cluster of lit candles, and a chair)."""
    for x in DESK_X:
        grid.set(x, FEET, 188, 'minecraft:dark_oak_planks')
    grid.set(DESK_X[2], FEET + 1, 188, _lantern(False))
    grid.set(DESK_X[1], FEET, 187, _stairs('dark_oak_stairs', 'north', 'bottom'))
    grid.set(DESK_X[0], FEET, 186, G.state('lectern', facing='south', has_book=False, powered=False))
    top = G.state('dark_oak_slab', type='top', waterlogged=False)
    candles = G.state('candle', candles=3, lit=True, waterlogged=False)
    for z in (187, 194):
        for wx, cx, facing in ((MEZZ_W[0], MEZZ_W[0] + 1, 'east'), (MEZZ_E[1], MEZZ_E[1] - 1, 'west')):
            grid.set(wx, FEET, z, top)
            grid.set(wx, FEET + 1, z, candles)
            grid.set(cx, FEET, z, _stairs('dark_oak_stairs', facing, 'bottom'))


# ---------------------------------------------------------------- the Restricted Section's furniture

def build_restricted_furniture(grid):
    """The alcove: a lectern beside the chest's place, a ladder up the back shelves hung from an Ollivanders chain
    rail, one lantern on a chain, and a few cobwebs high in the corners."""
    grid.set(AXIS_X + 2, FEET, ALCOVE_Z[1], G.state('lectern', facing='north', has_book=False, powered=False))
    for y in range(FEET, TOP - 1):
        grid.set(AXIS_X - 2, y, ALCOVE_Z[1], G.state('ladder', facing='north', waterlogged=False))
    for x in range(ALCOVE_X[0], ALCOVE_X[1] + 1):
        grid.set(x, TOP - 1, ALCOVE_Z[1], G.state('chain', axis='x', waterlogged=False))
    grid.set(AXIS_X, TOP, 201, CHAIN)
    grid.set(AXIS_X, TOP - 1, 201, _lantern(True))
    for p in ((HALL_X[0], TOP, SCREEN_Z + 1), (HALL_X[1], TOP, HALL_Z[1]), (ALCOVE_X[0], TOP, ALCOVE_Z[1]),
              (ALCOVE_X[1], TOP, ALCOVE_Z[0])):
        grid.set(*p, 'minecraft:cobweb')


# ---------------------------------------------------------------- the tunnel and the grounds door

def _soft(state):
    """Cells the tunnel's masonry may take: air, the fill, and the hill's own shell (never the viaduct's pier)."""
    return state.split('[', 1)[0] in ('minecraft:air', 'minecraft:stone', 'minecraft:grass_block',
                                      'minecraft:dirt', 'minecraft:coarse_dirt')


def build_tunnel(grid):
    """The tunnel from the grounds door to the hall: 3 wide and 4 tall, stone-brick walls, floor and vault (upside-down
    stair haunches), a lantern; at its north end the door plane (a single dark-oak door between stone-brick piers) and
    the portal on the hill's face (stone-brick jambs and lintel, a chiseled keystone, a lantern under the lintel)."""
    x0, x1 = TUNNEL_X
    for z in range(PORTAL_Z, TUNNEL_END_Z + 1):
        for x in range(x0, x1 + 1):
            for y in range(FEET, TUNNEL_TOP + 1):
                grid.clear(x, y, z)
            grid.set(x, FLOOR_Y, z, MASONRY if x != (x0 + x1) // 2 else 'minecraft:polished_andesite')
    for z in range(DOOR_Z, TUNNEL_END_Z):
        for y in range(FLOOR_Y, TUNNEL_TOP + 2):
            for x in (x0 - 1, x1 + 1):
                if _soft(grid.get(x, y, z)):
                    grid.set(x, y, z, MASONRY)
        for x in range(x0, x1 + 1):
            grid.set(x, TUNNEL_TOP + 1, z, MASONRY)
        if z > DOOR_Z:
            grid.set(x0, TUNNEL_TOP, z, _stairs('stone_brick_stairs', 'west', 'top'))
            grid.set(x1, TUNNEL_TOP, z, _stairs('stone_brick_stairs', 'east', 'top'))
    mid = (x0 + x1) // 2
    for y in range(FEET, TUNNEL_TOP + 1):                         # the door plane
        grid.set(x0, y, DOOR_Z, MASONRY)
        grid.set(x1, y, DOOR_Z, MASONRY)
        if y >= FEET + 2:
            grid.set(mid, y, DOOR_Z, MASONRY)
    for y, half in ((FEET, 'lower'), (FEET + 1, 'upper')):
        grid.set(mid, y, DOOR_Z, G.state('dark_oak_door', facing='south', half=half, hinge='left', open=False,
                                         powered=False))
    for y in range(FEET, TUNNEL_TOP + 2):                         # the portal
        grid.set(x0 - 1, y, PORTAL_Z, MASONRY)
        grid.set(x1 + 1, y, PORTAL_Z, MASONRY)
    for x in range(x0, x1 + 1):
        grid.set(x, TUNNEL_TOP + 1, PORTAL_Z, MASONRY)
    grid.set(mid, TUNNEL_TOP + 2, PORTAL_Z, 'minecraft:chiseled_stone_bricks')
    grid.set(mid, TUNNEL_TOP, PORTAL_Z, _lantern(True))
    grid.set(mid, TUNNEL_TOP, 180, _lantern(True))


# ---------------------------------------------------------------- placing it

def place_library(grid):
    """Carve the library into the gatehouse hill. Returns {'chests', 'pensieve', 'student_spots'} (castle
    coordinates) to merge into the castle's anchors."""
    rnd = random.Random(1205)
    layers, shafts = cave_plan(grid.cells)
    keep = interior_cells()
    for x, y, z in keep:                                          # only the tunnel's cells held anything (the shell)
        grid.clear(x, y, z)
    floor_and_ceiling(grid, rnd)
    build_public_walls(grid)
    build_restricted_walls(grid, rnd)
    build_screen(grid)
    rail = build_mezzanine(grid)
    build_reading_tables(grid)
    build_pensieve_dais(grid)
    build_desk_and_carrels(grid)
    build_restricted_furniture(grid)
    build_tunnel(grid)
    bars = [(x, y, SCREEN_Z) for x in range(HALL_X[0], HALL_X[1] + 1) for y in range(FEET, TOP + 1)
            if grid.get(x, y, SCREEN_Z) == 'minecraft:iron_bars']
    connect_bars_and_fences(grid, bars + rail)
    rock = fill_rock(grid, layers, shafts, keep)
    lights = hall.light_dark_floors(grid, room_boxes())
    shelves = shelf_count(grid.cells)
    place_library.stats = {'rock_fill': rock, 'shafts_left': len(shafts), 'lights': lights, 'shelf_blocks': shelves}
    return {
        'chests': [{'at': list(CHEST), 'facing': 'north', 'theme': 'LIBRARY'}],
        'pensieve': list(PENSIEVE),
        'student_spots': [list(p) for p in STUDENT_SPOTS],
    }


def _in_rooms(p):
    """True for a cell of the rooms or their walls, floor and ceiling (the changed boxes of the hall and alcove)."""
    x, y, z = p
    if not FLOOR_Y <= y <= CEIL_Y:
        return False
    return ((RING_X[0] <= x <= RING_X[1] and RING_Z[0] <= z <= RING_Z[1])
            or (ALCOVE_RING_X[0] <= x <= ALCOVE_RING_X[1] and RING_Z[1] <= z <= ALCOVE_BACK_Z))


def shelf_count(cells):
    """Bookshelf-type blocks in the rooms: bookshelves, chiseled bookshelves and the Ollivanders mix."""
    return sum(1 for p, s in cells.items() if _in_rooms(p) and s.split('[', 1)[0][10:] in SHELF_IDS)
