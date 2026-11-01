"""The Grand Staircase: the central round tower's hollow shell made the castle's vertical heart.

    from mini_hogwarts import rooms_staircase
    anchors = rooms_staircase.place_staircase(grid)     # grid in the castle frame, after the Great Hall wing

WHAT WAS THERE
--------------
The old tower is a round cobblestone shell (ring x 53..69, z 140..156, centre cell (61, 148)) rising from the hill
at y 28 to a plain top at y 58, a corbelled band at y 59..65 and an oak cone to y 91. Inside it is empty: a cross
of cobblestone beams at y 28, an oak-fence pole up the middle with glowstone every six blocks, and black glass
slits in the ring every three blocks at twelve points round it. The covered bridge to the small bridge tower leaves
its south face at y 44 (deck), where a log-and-plank sham door blocks the ring.

WHAT IT BECOMES
---------------
An open well the full height of the tower, floored at the castle's main level, with flights of stone-brick stairs
hugging the wall and switching back between landings on its north and south sides, dark-oak rails, portraits in
dark-oak frames, lanterns hanging under every landing and a chain of lanterns down the middle from the cone's tip,
and a column of floating candles in the void.

    feet y   landing            side   way out
    25       the well floor     all    (the bottom of the well: a compass star under the void)
    32       first landing      north  the door onto the entrance hall's imperial landing (the main way in)
    35       half landing       south
    39       second landing     north  (the house banners hang on the far side of this wall: no door)
    45       top landing        south  the Fat Lady's door onto the bridge to the Gryffindor tower

The flights: up the west wall from the floor to the first landing (7 steps), up the east wall to the half landing
(3), up the west wall to the second landing (4), up the east wall to the top landing (6). West flights climb north,
east flights climb south, so seen from the side they zigzag. Each flight is two treads wide plus a rail tread on
the void side, a floating flight two blocks thick (a stepped underside of upside-down stairs).

The ring has a one-block recess at each compass point (its plan is a rounded square). Those four niches are lined
with stone brick so the flights run past a flat wall, and the lining holds the portraits: each sits in a 3 x 2
recess framed in dark oak, its back wall the ring itself. Recesses avoid the glass slits (a painting cannot hang on
glass); the slits the lining would cover are kept as small embrasures with a light inside, so they still glow.

The Gryffindor common room (Task 6) takes the bridge tower: the top landing's door opens onto the bridge, and the
whole bridge and tower beyond are Gryffindor's. The Fat Lady hangs beside that door, facing into the well.

Only the tower's inside changes: the well (the ring's interior), the inner face of the ring at the frames and the
Fat Lady's wall, the door cut through the ring's north face (hidden against the entrance hall), and the cone's pole.
CHANGED_BOXES lists exactly those, and build_castle.verify holds every other old block of the tower, so its
outside silhouette is the old castle's.
"""

import math
from collections import deque

from .frame import MAIN_FLOOR_Y
from . import checks, hall
from .grid import air_at, is_air
from .hall import G

# ---------------------------------------------------------------- the tower (castle frame)
AXIS = (61, 148)                       # the tower's axis cell; the Great Hall's centre line runs through x 61
# The ring's interior, row by row, from y 29 to WELL_TOP (identical at every height; verify recomputes it from the
# old castle). The rows at z 141 and 155 and the cells x 54 / 68 at z 147..149 are the four compass niches.
WELL_ROWS = {141: (60, 62), 142: (57, 65), 143: (56, 66), 144: (55, 67), 145: (55, 67), 146: (55, 67),
             147: (54, 68), 148: (54, 68), 149: (54, 68), 150: (55, 67), 151: (55, 67), 152: (55, 67),
             153: (56, 66), 154: (57, 65), 155: (60, 62)}
WELL_FLOOR_Y = MAIN_FLOOR_Y            # the well's floor block layer; feet at 25
WELL_TOP = 58                          # the plain ring's top course: above it the corbelled band and the cone
CONE_TOP = 91                          # the last air cell under the cone's cobblestone cap, on the axis
SEAL_Y = (24, 27)                      # below y 28 the ring never existed (hill and foundation walls); built here

# landings: feet y
FLOOR_FEET = WELL_FLOOR_Y + 1          # 25
FIRST_FEET = hall.ORIGIN[1] + 8        # 32: the entrance hall's imperial landing (the set's landing top is local y 7)
HALF_FEET = 35
SECOND_FEET = 39
TOP_FEET = 45                          # the bridge deck is y 44

# Flights: (wall x columns, rail x, first tread z, climb direction in z, feet from, feet to). The last tread is
# at the landing's level and belongs to the landing.
FLIGHTS = (
    ((55, 56), 57, 151, -1, FLOOR_FEET, FIRST_FEET),    # west wall, north, the floor -> the first landing
    ((66, 67), 65, 145, +1, FIRST_FEET, HALF_FEET),     # east wall, south, -> the half landing
    ((55, 56), 57, 151, -1, HALF_FEET, SECOND_FEET),    # west wall, north, -> the second landing
    ((66, 67), 65, 145, +1, SECOND_FEET, TOP_FEET),     # east wall, south, -> the top landing
)

# the doors
ENTRY_DOOR = ((60, 62), (138, 140))    # x and z ranges through the entrance hall's far wall and the ring
ENTRY_DOOR_Y = (FIRST_FEET, FIRST_FEET + 2)       # 3 tall, plus the pointed head cell at x 61
GRYFFINDOR_DOOR = (61, TOP_FEET, 155)  # the sham door's cell on the ring's inner course; z 156 is already open
BRIDGE_FAR_END = (61, TOP_FEET, 170)   # the bridge's last free cell, in front of the bridge tower's door
FAT_LADY_WALL = (60, TOP_FEET + 2, 155)
FAT_LADY_CELLS = [(x, y, 154) for x in (59, 60) for y in range(TOP_FEET + 1, TOP_FEET + 5)]

# One walkable cell on each landing, for the reachability check.
LANDING_CELLS = {
    'well floor': (61, FLOOR_FEET, 148),
    'first landing': (61, FIRST_FEET, 142),
    'half landing': (61, HALF_FEET, 153),
    'second landing': (61, SECOND_FEET, 142),
    'top landing': (61, TOP_FEET, 153),
}

# The void between the flights and landings: candles go here.
VOID_X = (59.0, 64.0)
VOID_Z = (146.0, 151.0)
VOID_Y = (30.0, 62.0)
CANDLE_COUNT = 48

# Portrait niches: the niche cells (3, along the wall), the frame's jamb cells (inner course of the ring), the wall
# cell behind the niche's middle, the way a portrait faces, the frames' bottom rows (each frame is r .. r + 3, the
# portrait recess r + 1 .. r + 2), slit embrasures, rows left open, and rows that are landing floors.
NICHES = {
    'west': dict(cells=[(54, z) for z in (147, 148, 149)], jambs=[(54, 146), (54, 150)], wall=(53, 148),
                 facing='east', frames=(32, 41, 47, 53), embrasures=(38,), open=(), floors=()),
    'east': dict(cells=[(68, z) for z in (147, 148, 149)], jambs=[(68, 146), (68, 150)], wall=(69, 148),
                 facing='west', frames=(26, 38, 47, 53), embrasures=(32, 35, 44), open=(), floors=()),
    'north': dict(cells=[(x, 141) for x in (60, 61, 62)], jambs=[(59, 141), (63, 141)], wall=(61, 140),
                  facing='south', frames=(26, 41, 47, 53), embrasures=(),
                  open=tuple(range(FIRST_FEET, FIRST_FEET + 4)), floors=(FIRST_FEET - 1, SECOND_FEET - 1)),
    'south': dict(cells=[(x, 155) for x in (60, 61, 62)], jambs=[(59, 155), (63, 155)], wall=(61, 156),
                  facing='north', frames=(26, 36), embrasures=(53, 56),
                  open=tuple(range(TOP_FEET, TOP_FEET + 6)), floors=(HALF_FEET - 1, TOP_FEET - 1)),
}
LINING = 'minecraft:stone_bricks'
RING = 'minecraft:cobblestone'
FRAME_BAR = 'minecraft:dark_oak_planks'
FRAME_JAMB = G.state('stripped_dark_oak_log', axis='y')
FLOOR = 'minecraft:polished_andesite'
EDGE = 'minecraft:stone_bricks'
EMBRASURE_LIGHT = G.state('light', level=12, waterlogged=False)

_FULL = frozenset(('stone_bricks', 'cobblestone', 'polished_andesite', 'dark_oak_planks', 'stripped_dark_oak_log',
                   'chiseled_stone_bricks', 'stone', 'cracked_stone_bricks', 'polished_deepslate',
                   'deepslate_tiles', 'andesite'))
_SOFT = ('oak_fence', 'glowstone', 'light', 'chain', 'lantern')


def well_cells():
    """Every (x, z) of the well's plan (the ring's interior)."""
    return [(x, z) for z, (x0, x1) in WELL_ROWS.items() for x in range(x0, x1 + 1)]


def _flood(cells, y, limit=11.0):
    """The ring's interior at height y: the 2D flood from the axis through air and the old fittings (the pole, its
    glowstone, light, chain, lanterns). None when it leaks past `limit` (no closed ring at this height)."""
    seen = {AXIS}
    queue = deque([AXIS])
    while queue:
        x, z = queue.popleft()
        for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if n in seen:
                continue
            if math.hypot(n[0] - AXIS[0], n[1] - AXIS[1]) > limit:
                return None
            s = cells.get((n[0], y, n[1]))
            if s is None or is_air(s) or any(k in s for k in _SOFT):
                seen.add(n)
                queue.append(n)
    return seen


def measured_rows(cells, y=33):
    """The ring's interior rows at height y, as WELL_ROWS writes them ({z: (x0, x1)})."""
    rows = {}
    for x, z in _flood(cells, y):
        lo, hi = rows.get(z, (x, x))
        rows[z] = (min(lo, x), max(hi, x))
    return rows


def ring_cells(cells, y=29):
    """The ring's inner course at height y: the solid cells next to the well's plan."""
    plan = set(well_cells())
    out = set()
    for x, z in plan:
        for n in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)):
            if n not in plan and not air_at(cells, (n[0], y, n[1])):
                out.add(n)
    return out


def _row_boxes(plan, y0, y1):
    """Inclusive boxes covering `plan` ({(x, z)}) from y0 to y1, one per row run."""
    rows = {}
    for x, z in plan:
        rows.setdefault(z, []).append(x)
    out = []
    for z, xs in sorted(rows.items()):
        xs.sort()
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1:
                prev = x
                continue
            out.append((start, y0, z, prev, y1, z))
            if x is not None:
                start = prev = x
    return out


def light_boxes(cells):
    """The boxes the no-dark-floor check covers: the well to its top, the corbelled band and the cone (each level's
    own interior, so nothing outside the tower counts), and the entry door's passage."""
    boxes = _row_boxes(set(well_cells()), FLOOR_FEET, WELL_TOP)
    for y in range(WELL_TOP + 1, CONE_TOP + 1):
        plan = _flood(cells, y)
        if plan:
            boxes += _row_boxes(plan, y, y)
    (dx0, dx1), (dz0, dz1) = ENTRY_DOOR
    boxes.append((dx0, ENTRY_DOOR_Y[0], dz0, dx1, ENTRY_DOOR_Y[1] + 1, dz1))
    return boxes


def changed_boxes():
    """(name, inclusive box) for every old-castle block this room may change: build_castle.CHANGED_ON_PURPOSE.
    Only inside cells: the ring's interior, the ring's inner course where a frame or the Fat Lady's wall replaces it,
    the axis cell of the cone (its pole), and the entry door through the ring's north face (which stands against the
    entrance hall's far wall, so it is never seen from outside)."""
    out = [('grand staircase: the well, row z %d' % b[2], b)
           for b in _row_boxes(set(well_cells()), WELL_FLOOR_Y, WELL_TOP)]
    out.append(('grand staircase: the lantern chain in place of the cone\'s fence pole and glowstone',
                (AXIS[0], WELL_TOP + 1, AXIS[1], AXIS[0], CONE_TOP, AXIS[1])))
    (dx0, dx1), (dz0, dz1) = ENTRY_DOOR
    out.append(('grand staircase: the entry door through the ring\'s north face',
                (dx0, ENTRY_DOOR_Y[0], 140, dx1, ENTRY_DOOR_Y[1] + 1, 140)))
    for name, n in sorted(NICHES.items()):
        lo = min(n['frames'])
        hi = max(n['frames']) + 3
        for jx, jz in n['jambs']:
            out.append(('grand staircase: %s portrait frames, jamb on the ring\'s inner course' % name,
                        (jx, lo, jz, jx, hi, jz)))
    out.append(('grand staircase: the Fat Lady\'s frame on the ring\'s inner course',
                (59, TOP_FEET - 1, 155, 59, TOP_FEET + 5, 155)))
    return out


# ---------------------------------------------------------------- building

def _stairs(facing, half):
    return G.state('stone_brick_stairs', facing=facing, half=half, shape='straight', waterlogged=False)


def _lantern(hanging):
    return G.state('lantern', hanging=hanging, waterlogged=False)


def clear_well(grid):
    """Empty the well (the beams, the pole, its glowstone, the bridge's sham door) and the cone's axis."""
    for x, z in well_cells():
        for y in range(WELL_FLOOR_Y + 1, WELL_TOP + 1):
            grid.clear(x, y, z)
    for y in range(WELL_TOP + 1, CONE_TOP + 1):
        grid.clear(AXIS[0], y, AXIS[1])


def floor_and_seal(grid):
    """The well's floor at the main level, a compass star under the void, and the ring closed below y 28 (where the
    old tower stood on the hill and its foundation walls ran wider)."""
    plan = set(well_cells())
    ring = ring_cells(grid.cells)
    for x, z in ring:
        for y in range(SEAL_Y[0], SEAL_Y[1] + 1):
            if grid.is_air(x, y, z):
                grid.set(x, y, z, RING)
    for x, z in plan:
        d = math.hypot(x - AXIS[0], z - AXIS[1])
        s = FLOOR
        if d < 1:
            s = 'minecraft:chiseled_stone_bricks'
        elif (x == AXIS[0] or z == AXIS[1]) and d <= 4.5:
            s = 'minecraft:deepslate_tiles'
        elif 4.5 < d <= 5.5:
            s = 'minecraft:polished_deepslate'
        grid.set(x, WELL_FLOOR_Y, z, s)
    return len(ring)


def build_niches(grid):
    """Line the four compass niches with stone brick from the floor to the ring's top, leaving dark-oak framed
    recesses for the portraits, embrasures in front of the slits, and the rows the doors and landings use.
    Returns the paintings anchors."""
    paintings = []
    for name in ('west', 'east', 'north', 'south'):
        n = NICHES[name]
        frame_rows = {}
        for r in n['frames']:
            frame_rows.update({r: 'bar', r + 1: 'recess', r + 2: 'recess', r + 3: 'bar'})
        for y in range(FLOOR_FEET, WELL_TOP + 1):
            if y in n['floors'] or y in n['open']:
                continue
            kind = frame_rows.get(y)
            for i, (x, z) in enumerate(n['cells']):
                if kind == 'bar':
                    grid.set(x, y, z, FRAME_BAR)
                elif kind == 'recess':
                    grid.clear(x, y, z)
                elif y in n['embrasures'] and i == 1:
                    grid.set(x, y, z, EMBRASURE_LIGHT)
                else:
                    grid.set(x, y, z, LINING)
            if kind:
                for jx, jz in n['jambs']:
                    grid.set(jx, y, jz, FRAME_BAR if kind == 'bar' else FRAME_JAMB)
        wx, wz = n['wall']
        for r in n['frames']:
            paintings.append({'at': [wx, r + 1, wz], 'facing': n['facing']})
    return paintings


def flight_treads(f):
    """[(z, feet, last)] of a flight: one tread per rise, the last at the landing's level."""
    cols, rail, z0, dz, a, b = f
    return [(z0 + dz * (k - 1), a + k, k == b - a) for k in range(1, b - a + 1)]


def build_flights(grid):
    """The four flights: stone-brick stairs facing the climb over an upside-down stair facing back (a floating flight
    with a stepped underside); the last tread, at the landing's level, is landing floor."""
    climb = {-1: 'north', +1: 'south'}
    back = {-1: 'south', +1: 'north'}
    for f in FLIGHTS:
        cols, rail, z0, dz, a, b = f
        for z, feet, last in flight_treads(f):
            for x in tuple(cols) + (rail,):
                if last:
                    grid.set(x, feet - 1, z, FLOOR if x != rail else EDGE)
                else:
                    grid.set(x, feet - 1, z, _stairs(climb[dz], 'bottom'))
                if feet - 2 > WELL_FLOOR_Y:
                    grid.set(x, feet - 2, z, _stairs(back[dz], 'top'))


def _landing_plan():
    """{name: (floor y, cells)} for the four landings above the floor (each one block of floor)."""
    north = [(x, z) for x, z in well_cells() if z <= 144]
    south = [(x, z) for x, z in well_cells() if z >= 152]
    top = [(x, z) for x, z in well_cells() if z >= 151]
    return {
        'first': (FIRST_FEET - 1, north + [(x, 145) for x in (55, 56, 57)]),
        'half': (HALF_FEET - 1, south + [(x, z) for x in (65, 66, 67) for z in range(147, 152)]),
        'second': (SECOND_FEET - 1, north + [(x, z) for x in (55, 56, 57) for z in range(145, 149)]),
        'top': (TOP_FEET - 1, top + [(x, 150) for x in (65, 66, 67)]),
    }


# The rail runs (fences at feet level) along each landing's open edge, and the newel posts that carry lanterns.
RAILS = {
    'first': [(57, 145)] + [(x, 144) for x in range(57, 66)],
    'half': [(65, z) for z in range(147, 153)] + [(x, 152) for x in range(57, 65)],
    'second': [(57, z) for z in range(144, 149)] + [(x, 144) for x in range(58, 66)],
    'top': [(65, 150)] + [(x, 151) for x in range(55, 66)],
}
NEWELS = {'first': ((57, 144), (65, 144)), 'half': ((57, 152), (65, 152)),
          'second': ((57, 144), (65, 144)), 'top': ((55, 151), (65, 151))}
# Edge corbels under each landing's open side (facing: the side their lower half sits on, towards the landing).
CORBELS = {
    'first': ([(x, 144) for x in range(58, 65)], 'north'),
    'half': ([(x, 152) for x in range(58, 65)], 'south'),
    'second': ([(x, 144) for x in range(58, 65)], 'north'),
    'top': ([(x, 151) for x in range(58, 65)], 'south'),
}
HANG_X = (59, 63)                      # lanterns hang on a chain link under each landing's edge at these columns


def build_landings(grid):
    """The landing floors (polished andesite, stone brick along the open edge), their corbels and the lanterns hung
    on a chain link under the edge."""
    for name, (fy, cells) in _landing_plan().items():
        edge = set(RAILS[name])
        for x, z in cells:
            grid.set(x, fy, z, EDGE if (x, z) in edge else FLOOR)
        row, facing = CORBELS[name]
        for x, z in row:
            if x in HANG_X:
                grid.set(x, fy - 1, z, CHAIN)
                grid.set(x, fy - 2, z, _lantern(True))
            else:
                grid.set(x, fy - 1, z, _stairs(facing, 'top'))


def _fence(grid, x, y, z):
    grid.set(x, y, z, 'minecraft:dark_oak_fence')


def build_rails(grid):
    """Dark-oak rails: a post on every rail tread of the flights (balusters stepping up the flight), runs along the
    landings' open edges, and a lantern on each landing's two newel posts."""
    placed = []
    for f in FLIGHTS:
        cols, rail, z0, dz, a, b = f
        for z, feet, last in flight_treads(f):
            if not last:
                placed.append((rail, feet, z))
    feet_of = {'first': FIRST_FEET, 'half': HALF_FEET, 'second': SECOND_FEET, 'top': TOP_FEET}
    for name, cells in RAILS.items():
        placed += [(x, feet_of[name], z) for x, z in cells]
    for p in placed:
        _fence(grid, *p)
    for name, posts in NEWELS.items():
        for x, z in posts:
            grid.set(x, feet_of[name] + 1, z, _lantern(False))
    connect_fences(grid, placed)
    return len(placed)


def connect_fences(grid, cells):
    """Set each fence's side properties: it joins the fences beside it and the full blocks it touches."""
    for x, y, z in cells:
        props = {}
        for side, (dx, dz) in (('east', (1, 0)), ('west', (-1, 0)), ('south', (0, 1)), ('north', (0, -1))):
            s = grid.get(x + dx, y, z + dz)
            props[side] = s.startswith('minecraft:dark_oak_fence') or checks.block_id(s) in _FULL
        grid.set(x, y, z, G.state('dark_oak_fence', waterlogged=False, **props))


def build_entry_door(grid):
    """The door from the imperial landing into the first landing: 3 wide and 3 tall with a pointed head, through the
    entrance hall's far wall and the ring's north face, framed on the hall side in polished andesite with a chiseled
    keystone. Its head stays below the two-tier house banners on that wall."""
    (dx0, dx1), (dz0, dz1) = ENTRY_DOOR
    y0, y1 = ENTRY_DOOR_Y
    head = (dx0 + dx1) // 2
    for z in range(dz0, dz1 + 1):
        for x in range(dx0, dx1 + 1):
            for y in range(y0, y1 + 1):
                grid.clear(x, y, z)
        grid.clear(head, y1 + 1, z)
    face = dz0
    for y in range(y0, y1 + 1):
        grid.set(dx0 - 1, y, face, FLOOR)
        grid.set(dx1 + 1, y, face, FLOOR)
    grid.set(dx0, y1 + 1, face, FLOOR)
    grid.set(dx1, y1 + 1, face, FLOOR)
    grid.set(head, y1 + 2, face, 'minecraft:chiseled_stone_bricks')


def build_gryffindor_door(grid):
    """The top landing's door onto the bridge (the sham door's cell opened, 1 x 2 like the bridge itself) and the
    wall it stands in: the south niche filled flat from the landing up, the Fat Lady's place (x 59..60, y 46..49)
    with dark-oak bars above and below her and a dark-oak post between her and the door."""
    x, y, z = GRYFFINDOR_DOOR
    for xx in (60, 61, 62):
        for yy in range(TOP_FEET, TOP_FEET + 6):
            grid.set(xx, yy, z, LINING)
    grid.clear(x, y, z)
    grid.clear(x, y + 1, z)
    for yy in range(y + 2, y + 6):
        grid.set(x, yy, z, FRAME_JAMB)
    for xx in (59, 60):
        grid.set(xx, TOP_FEET, z, FRAME_BAR)
        grid.set(xx, TOP_FEET + 5, z, FRAME_BAR)
    grid.set(x + 1, TOP_FEET, z - 1, _lantern(False))       # a lantern on the floor beside the door lights her


CHAIN = G.state('chain', axis='y', waterlogged=False)
CHAIN_LANTERNS = (82, 74, 66, 58, 50)


def build_lantern_chain(grid):
    """A chain down the axis from the cone's cap to above the top landing, a lantern every eight blocks."""
    x, z = AXIS
    for y in range(min(CHAIN_LANTERNS), CONE_TOP + 1):
        grid.set(x, y, z, _lantern(True) if y in CHAIN_LANTERNS else CHAIN)


def _yaw_to_axis(x, z):
    dx = AXIS[0] + 0.5 - (x + 0.5)
    dz = AXIS[1] + 0.5 - (z + 0.5)
    return int(round(math.degrees(math.atan2(-dx, dz))))


# Living Statue posts: two per landing, in the corners off the walk (feet cells).
STATUE_CELLS = (
    (58, FLOOR_FEET, 153), (64, FLOOR_FEET, 153),
    (57, FIRST_FEET, 142), (65, FIRST_FEET, 142),
    (57, HALF_FEET, 154), (65, HALF_FEET, 154),
    (57, SECOND_FEET, 142), (65, SECOND_FEET, 142),
    (57, TOP_FEET, 154), (65, TOP_FEET, 154),
)


def place_staircase(grid):
    """Build the Grand Staircase into the castle grid. Returns {'paintings', 'statues', 'fat_lady'} and the candle
    box under 'candles' (each a list or record to merge into the castle's anchors)."""
    clear_well(grid)
    sealed = floor_and_seal(grid)
    paintings = build_niches(grid)
    build_flights(grid)
    build_landings(grid)
    rails = build_rails(grid)
    build_entry_door(grid)
    build_gryffindor_door(grid)
    build_lantern_chain(grid)
    lights = hall.light_dark_floors(grid, light_boxes(grid.cells))
    place_staircase.stats = {'sealed_ring_cells': sealed, 'rail_posts': rails, 'lights': lights}
    candle = {'at': [(VOID_X[0] + VOID_X[1]) / 2, (VOID_Y[0] + VOID_Y[1]) / 2, (VOID_Z[0] + VOID_Z[1]) / 2],
              'spread': [(VOID_X[1] - VOID_X[0]) / 2, (VOID_Y[1] - VOID_Y[0]) / 2, (VOID_Z[1] - VOID_Z[0]) / 2],
              'count': CANDLE_COUNT}
    return {
        'candles': [candle],
        'statues': [{'at': list(c), 'yaw': _yaw_to_axis(c[0], c[2])} for c in STATUE_CELLS],
        'paintings': paintings,
        'fat_lady': {'wall': list(FAT_LADY_WALL), 'facing': 'north'},
    }
