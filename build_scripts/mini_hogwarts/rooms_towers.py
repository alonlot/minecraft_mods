"""The tower tops: the Gryffindor common room and its dormitory, the Astronomy gallery and the Headmaster's office.

    from mini_hogwarts import rooms_towers
    anchors = rooms_towers.place_towers(grid)   # castle frame, after the Great Hall, the staircase and the library

THE GRYFFINDOR TOWER (the old bridge tower)
-------------------------------------------
The little round tower at the far end of the covered bridge from the Grand Staircase's top landing was 5 wide inside
(7 at its corbelled top) under an oak cone. Ruling R26 lets its top change shape, and only its top: it becomes a
jettied upper storey, the castle's own way of getting more room high up. From y 44 (the bridge deck) the tower
widens on a stepped corbel of stone brick (three courses, y 41..43, upside-down stairs on every lip) to an octagon
11 wide inside (x 56..66, z 169..179), two storeys in cobblestone with black-glass windows and a smooth-stone string
course, under a new oak cone with a slab eave and a stone chimney stack. Its north face meets the bridge two cells
short of the old tower (the bridge's last two cells are now inside), so the bridge leads straight in through an open
doorway (61, 45..46, 168); the old log-and-plank sham door was inside what is now the room. The Fat Lady stays
where Task 4 hung her, beside the top landing's door, which is the only way to the bridge (R25: no password gate).
The rest of the old tower (its shaft from the hill to y 43) is untouched.

    feet y 45..50   the common room: a big stone fireplace on the south wall (three campfires in a deep firebox, a
                    stone-brick breast to the ceiling, a mantel with candles, the house banner above), a red settee
                    and two armchairs (mangrove stairs with trapdoor arms) on a red-and-gold rug, a red runner with
                    gold edges from the door, a study table with a lantern and candles, a bookcase, beams under the
                    ceiling with lanterns, and a dark-oak stair up the west wall to the dormitory
    feet y 52..56   the dormitory under the cone: five red four-posters (fence posts, red-wool canopies) along the
                    east and west walls and beside the chimney breast, barrel nightstands with candles, trunks, a lantern on a
                    chain from the cone's apex

THE CENTRAL TOWER'S TOP (the Astronomy gallery and the Headmaster's office)
--------------------------------------------------------------------------
The tallest tower is the Grand Staircase's. Its silhouette may not change (R26), so both rooms go inside its top,
above the well: the corbelled band (walls y 59..63, 17 wide inside) and the cone above it.

    feet y 46..58   a helical stair from the top landing, up the west wall and along the north wall (two treads wide,
                    a dark-oak rail on the void side, a stepped underside like Task 4's flights). It rises past the
                    west and north portrait niches on level runs, so no tread or underside ever stands in front of a
                    portrait's recess rows (48..49, 54..55).
    feet y 59       the Astronomy gallery: a floor over the band round a 7 x 7 oculus above the well (railed; the
                    stair candles float up through it), the band's four cardinal windows opened into arches three
                    wide and three tall with a fence rail on the sill (the "open" platform: nothing of the outline
                    changes), a telescope at each (a lightning-rod tube on a fence stand, pointing out), a star-chart
                    table and a lectern, lanterns on the rail corners. In the north-west a stone spiral stair climbs
                    into the office, with a three-block gargoyle carving beside its foot (the "stair door").
    feet y 64       the Headmaster's office under the cone: a round room 17 wide with a glass floor over the oculus
                    (the whole well is seen through it), the desk on the north side overlooking it with the
                    headmaster's chair, candles and an instrument, the phoenix perch (gold block, post, gold plate),
                    spindly instrument tables, bookcases round the wall between the cone's windows, four portraits
                    (paintings anchors), the TOWER chest, and a chandelier on the chain from the cone's cap (the
                    staircase's lantern chain now hangs from the glass floor).

Every walkable cell is lit (visible sources plus hall.light_dark_floors' light blocks). The anchors: the TOWER chest
(office), Gryffindor banners and student spots (common room), and the office's portraits.
"""

import math

from . import checks, hall, rooms_staircase
from .grid import Grid, air_at
from .hall import G

S = rooms_staircase

# ================================================================ the Gryffindor tower (castle frame)

OLD_FOOTPRINT = {170: (60, 62), 171: (59, 63), 172: (58, 64), 173: (58, 64), 174: (58, 64), 175: (59, 63),
                 176: (60, 62)}        # the old tower's ring (outer cells included) below the jetty, y 34..43
GT_ROWS = {169: (59, 63), 170: (57, 65), 171: (56, 66), 172: (56, 66), 173: (56, 66), 174: (56, 66), 175: (56, 66),
           176: (56, 66), 177: (56, 66), 178: (57, 65), 179: (59, 63)}       # the jetty's interior, both storeys
GT_CENTRE = (61, 174)                  # the octagon's centre cell (the roof's axis)
CORBEL_Y = 41                          # the corbel's lowest course
CR_FLOOR_Y = 44                        # the common room's floor: the bridge deck's level
CR_FEET = CR_FLOOR_Y + 1               # 45
DORM_FLOOR_Y = 51                      # the dormitory's floor, the common room's ceiling
DORM_FEET = DORM_FLOOR_Y + 1           # 52
WALL_TOP = 56                          # the walls' top course
EAVE_Y = 57                            # the roof's eave and first course
ROOF_R0 = 6.9                          # the cone's outer radius at the eave course (cells' centres from the axis)
ROOF_STEP = 0.3                        # the radius lost per course
ROOF_SHELL = 1.3                       # the shell's thickness
EAVE_R = 7.6                           # the slab eave's reach
GT_DOOR = (61, CR_FEET, 168)           # the doorway in the north wall; (61, 45, 167) is the bridge's last cell
BRIDGE = [(61, CR_FEET, z) for z in range(156, 168)]      # the covered bridge's walk, the top landing's door to ours
BRIDGE_LANTERN = (61, 47, 162)         # under the bridge roof's log at y 48 (logs on the even rows)
CHIMNEY_X = (60, 62)
CHIMNEY_TOP = 66

# the common room's stair: up the west wall from the north (feet 46 at z 171) to the dormitory (feet 52 at z 177)
GT_STAIR_X = (56, 57)
GT_STAIR = [(z, CR_FEET + 1 + i) for i, z in enumerate(range(171, 178))]
GT_STAIR_HOLE = [(x, z) for x in GT_STAIR_X for z in (174, 175, 176)]     # the dormitory floor's opening

# The fireplace on the south wall: the breast fills x 59..63 at z 178..179 to the ceiling; the firebox is x 60..62,
# two deep and two tall, with three campfires at its back; the mantel overhangs at y 47 on z 177.
BREAST_X = (59, 63)
BREAST_Z = (178, 179)
FIREBOX_X = (60, 62)

# beds: (head cell, facing from foot to head); each four-poster has its post one past the foot and a canopy over all
BEDS = (((66, 171), 'east'), ((66, 174), 'east'), ((66, 177), 'east'), ((63, 179), 'south'), ((56, 171), 'west'))

BANNERS = (((61, 49, 177), 'north'),   # over the mantel, on the chimney breast
           ((59, 48, 169), 'south'), ((63, 48, 169), 'south'),             # either side of the door
           ((66, 48, 174), 'west'))    # the east wall, above the study corner
STUDENT_SPOTS = ((58, CR_FEET, 172), (64, CR_FEET, 173), (61, CR_FEET, 177))

# ================================================================ the central tower's top

BAND_ROWS = {140: (59, 63), 141: (57, 65), 142: (56, 66), 143: (55, 67), 144: (54, 68), 145: (54, 68), 146: (53, 69),
             147: (53, 69), 148: (53, 69), 149: (53, 69), 150: (53, 69), 151: (54, 68), 152: (54, 68), 153: (55, 67),
             154: (56, 66), 155: (57, 65), 156: (59, 63)}    # the band's interior at y 59..62 (verify re-measures)
OFFICE_ROWS = {140: (59, 63), 141: (57, 65), 142: (57, 65), 143: (56, 66), 144: (54, 68), 145: (54, 68),
               146: (53, 69), 147: (53, 69), 148: (53, 69), 149: (53, 69), 150: (53, 69), 151: (54, 68),
               152: (54, 68), 153: (56, 66), 154: (57, 65), 155: (57, 65), 156: (59, 63)}   # inside the cone, y 64..65
GALLERY_FLOOR_Y = 58                   # the plain ring's top course
GALLERY_FEET = 59
OFFICE_FLOOR_Y = 63                    # the band's top course
OFFICE_FEET = 64
OCULUS_X = (58, 64)                    # the well's open void, kept open through the gallery floor
OCULUS_Z = (145, 151)
OCULUS_RAIL = ([(x, 144) for x in range(57, 66)] + [(x, 152) for x in range(57, 66)]
               + [(57, z) for z in range(145, 152)] + [(65, z) for z in range(145, 152)])

# The helical stair: (cells, feet) in climbing order. Each tread is a stone-brick stair facing the next step when the
# next is one higher, otherwise a landing block. The level runs at feet 48 (z 147..149) and 54 (x 60..62) pass the
# west and north portrait niches, whose recess rows are 48..49 and 54..55.
HELIX = (
    (((55, 151), (56, 151)), 46),
    (((55, 150), (56, 150)), 47),
    (((55, 149), (56, 149)), 48),
    (((55, 148), (56, 148)), 48),
    (((55, 147), (56, 147)), 48),
    (((55, 146), (56, 146)), 49),
    (((55, 145), (56, 145)), 50),
    (((55, 144), (56, 144), (56, 143), (57, 143), (57, 142)), 51),        # the north-west corner landing
    (((58, 142), (58, 143)), 52),
    (((59, 142), (59, 143)), 53),
    (((60, 142), (60, 143)), 54),
    (((61, 142), (61, 143)), 54),
    (((62, 142), (62, 143)), 54),
    (((63, 142), (63, 143)), 55),
    (((64, 142), (64, 143)), 56),
    (((65, 142), (65, 143)), 57),
    (((66, 143),), 58),                # turns south onto the gallery at (66, 144)
)
HELIX_ARRIVAL = (66, GALLERY_FEET, 144)
HELIX_RAILS = ([(57, z) for z in range(145, 152)] + [(57, 144)] + [(x, 144) for x in range(58, 66)])
STAIRWELL = [(64, 142), (64, 143), (65, 142), (65, 143), (66, 143)]       # the gallery floor's opening over it
STAIRWELL_RAILS = [(63, 142), (63, 143), (64, 141), (65, 141)]

# the arches: (band wall cells, the inside cell the telescope stands on, the way it points)
ARCHES = (
    ([(52, z) for z in (147, 148, 149)], (53, 148), 'west'),
    ([(70, z) for z in (147, 148, 149)], (69, 148), 'east'),
    ([(x, 139) for x in (60, 61, 62)], (61, 140), 'north'),
    ([(x, 157) for x in (60, 61, 62)], (61, 156), 'south'),
)

# The office stair: a spiral round a newel in the gallery's north-west, entered from the south ("the stair door",
# the gargoyle beside it), treads one higher each, arriving on the office floor north of the newel.
SPIRAL_NEWEL = (55, 145)
SPIRAL = (((55, 146), 60, 'west'), ((54, 146), 61, 'north'), ((54, 145), 62, 'north'), ((54, 144), 63, 'east'))
SPIRAL_DOOR = (55, GALLERY_FEET, 147)
SPIRAL_ARRIVAL = (55, OFFICE_FEET, 144)
OFFICE_HOLE = [(54, 146), (54, 145), (54, 144)]  # the office floor's opening over the treads with feet 61..63
GARGOYLE = (54, 147)

DESK = [(x, 143) for x in range(59, 64)]
CHAIR = (61, 142)
PERCH = (65, 144)
INSTRUMENT_TABLES = (((57, 151), 'brewing_stand'), ((65, 151), 'end_rod'))
OFFICE_CHEST = (67, OFFICE_FEET, 147)
CHANDELIER_Y = 68                      # the office's lantern; the chain runs from it to the cone's cap
PORTRAIT_ANGLES = (172, 8, 98, -50)    # where the office's portraits go (degrees round the axis, east 0, south 90)

# One cell of each room, for the reachability check.
TOWER_CELLS = {
    'bridge': (61, CR_FEET, 162),
    'common room': (61, CR_FEET, 172),
    'common room hearth': (61, CR_FEET, 177),
    'dormitory': (61, DORM_FEET, 174),
    'helical stair top': (66, 58, 143),
    'astronomy gallery': (61, GALLERY_FEET, 154),
    'astronomy gallery west': (55, GALLERY_FEET, 149),
    'office': (61, OFFICE_FEET, 147),
}

# ---------------------------------------------------------------- small helpers

_STEP = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
_BACK = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
FENCE = 'dark_oak_fence'
COBBLE = 'minecraft:cobblestone'
BRICK = 'minecraft:stone_bricks'
DARK_PLANKS = 'minecraft:dark_oak_planks'
LIGHT = G.state('light', level=12, waterlogged=False)


def _rows_cells(rows):
    return [(x, z) for z, (x0, x1) in sorted(rows.items()) for x in range(x0, x1 + 1)]


def _stairs(name, facing, half='bottom'):
    return G.state(name, facing=facing, half=half, shape='straight', waterlogged=False)


def _lantern(hanging):
    return G.state('lantern', hanging=hanging, waterlogged=False)


def _trapdoor(name, facing):
    """An open trapdoor flat against the side of its cell opposite `facing` (an armchair's arm faces away from the
    seat, so its panel lies against the seat)."""
    return G.state(name, facing=facing, half='bottom', open=True, powered=False, waterlogged=False)


def _candle(color=''):
    return G.state((color + '_candle') if color else 'candle', candles=1, lit=True, waterlogged=False)


def _connect(grid, cells, kind):
    """Set the side properties of fences (kind 'fence') or glass panes (kind 'pane') at cells: each joins the same
    kind beside it and the full blocks it touches."""
    full = frozenset(('cobblestone', 'stone_bricks', 'dark_oak_planks', 'polished_andesite', 'bookshelf',
                      'chiseled_stone_bricks', 'stone', 'oak_planks', 'stripped_dark_oak_log', 'red_wool'))
    for x, y, z in cells:
        s = grid.get(x, y, z)
        name = s.split('[', 1)[0]
        props = {}
        for side, (dx, dz) in _STEP.items():
            n = grid.get(x + dx, y, z + dz)
            nid = checks.block_id(n)
            same = nid.endswith('_fence') if kind == 'fence' else nid.endswith('glass_pane')
            props[side] = same or nid in full
        grid.set(x, y, z, G.state(name, waterlogged=False, **props))


def _open(grid, x, y, z):
    """Air, or one of the light blocks an earlier pass left (they have no collision box, so a floor must replace
    them)."""
    return grid.is_air(x, y, z) or checks.block_id(grid.get(x, y, z)) == 'light'


def _box_cells(x0, y0, z0, x1, y1, z1):
    return [(x, y, z) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1) for x in range(x0, x1 + 1)]


def _row_boxes(rows, y0, y1):
    return [(x0, y0, z, x1, y1, z) for z, (x0, x1) in sorted(rows.items())]


# ================================================================ the Gryffindor tower

def gt_interior():
    return set(_rows_cells(GT_ROWS))


def gt_walls():
    """The jetty's wall ring: every cell beside the interior (faces only) that is not interior."""
    inside = gt_interior()
    out = set()
    for x, z in inside:
        for dx, dz in _STEP.values():
            n = (x + dx, z + dz)
            if n not in inside:
                out.add(n)
    return out


def gt_footprint():
    return gt_interior() | gt_walls()


def _old_footprint():
    return set(_rows_cells(OLD_FOOTPRINT))


def roof_radius(y):
    return ROOF_R0 - ROOF_STEP * (y - EAVE_Y)


def _roof_d(x, z):
    return math.hypot(x - GT_CENTRE[0], z - GT_CENTRE[1])


def roof_top():
    """The highest course the cone has any cell in (its tip)."""
    y = EAVE_Y
    while roof_radius(y + 1) >= 0.5:
        y += 1
    return y


ROOF_BOX = (54, EAVE_Y, 166, 68, 90, 182)
WALLS_BOX = (55, CR_FLOOR_Y, 168, 67, WALL_TOP, 180)


def clear_tower_top(grid):
    """Take the old tower down to its ring at y 43: everything in the walls' box (the old ring, its corbelled top and
    the little pinnacle at its south-west, the bridge's last two cells) and the roof's box (the old cone)."""
    for box in (WALLS_BOX, ROOF_BOX):
        for x, y, z in _box_cells(*box):
            grid.clear(x, y, z)


def build_corbel(grid):
    """The stepped corbel under the jetty: every footprint column outside the old ring is solid from a bottom course
    that steps out and up (one cell out of the ring: from y 41; two: y 42; further: y 43) to y 43, stone brick with an
    upside-down stair on the lip facing back towards the tower. Cells already solid (the bridge's masonry) stay."""
    old = _old_footprint()
    dist = {c: 0 for c in old}
    frontier = list(old)
    foot = gt_footprint()
    while frontier:
        nxt = []
        for x, z in frontier:
            for dx, dz in _STEP.values():
                n = (x + dx, z + dz)
                if n in foot and n not in dist:
                    dist[n] = dist[(x, z)] + 1
                    nxt.append(n)
        frontier = nxt
    n_cells = 0
    for (x, z), d in dist.items():
        if d == 0:
            continue
        bottom = CORBEL_Y + min(d, 3) - 1
        toward = None
        for side, (dx, dz) in _STEP.items():
            if dist.get((x + dx, z + dz), 99) == d - 1:
                toward = side
                break
        for y in range(bottom, CR_FLOOR_Y):
            if not grid.is_air(x, y, z):
                continue
            grid.set(x, y, z, _stairs('stone_brick_stairs', toward, 'top') if y == bottom else BRICK)
            n_cells += 1
    return n_cells


GT_WINDOWS = {
    CR_FEET + 1: [(57, 169), (65, 169), (67, 172), (67, 176), (66, 178)],   # common room (the stair has the west)
    DORM_FEET + 1: [(57, 169), (65, 169), (55, 172), (67, 172), (67, 176), (57, 179), (65, 179)],
}


def build_jetty(grid):
    """The two storeys' walls (cobblestone), floors (dark oak; the common room's floor is the old tower's interior
    and the bridge's last two cells), the doorway, the windows (black glass panes, two tall) and a smooth-stone
    string course at the dormitory's floor line."""
    inside = gt_interior()
    walls = gt_walls()
    for x, z in walls:
        for y in range(CR_FLOOR_Y, WALL_TOP + 1):
            grid.set(x, y, z, COBBLE)
    for x, z in inside:
        grid.set(x, CR_FLOOR_Y, z, DARK_PLANKS)
        grid.set(x, DORM_FLOOR_Y, z, DARK_PLANKS)
    x, y, z = GT_DOOR
    grid.clear(x, y, z)
    grid.clear(x, y + 1, z)
    grid.set(x, CR_FLOOR_Y, z, 'minecraft:polished_andesite')             # the threshold
    grid.set(x, y + 2, z, 'minecraft:chiseled_stone_bricks')              # the keystone over it
    panes = []
    for y0, cells in GT_WINDOWS.items():
        for wx, wz in cells:
            assert (wx, wz) in walls, ('window not in the wall', (wx, wz))
            for yy in (y0, y0 + 1):
                grid.set(wx, yy, wz, 'minecraft:black_stained_glass_pane')
                panes.append((wx, yy, wz))
    _connect(grid, panes, 'pane')
    course = set()
    for x, z in walls:
        for dx, dz in _STEP.values():
            n = (x + dx, z + dz)
            if n not in walls and n not in inside:
                course.add(n)
    for x, z in course:
        if grid.is_air(x, DORM_FLOOR_Y, z):
            grid.set(x, DORM_FLOOR_Y, z, G.state('smooth_stone_slab', type='bottom', waterlogged=False))


def build_roof(grid):
    """The cone: an oak-plank shell whose radius shrinks ROOF_STEP a course from the eave, an oak-slab eave ring
    outside the walls, a finial (cobblestone wall and two oak fences) on the tip, and the chimney stack (stone brick,
    capped with a slab) through the south slope."""
    top = roof_top()
    for y in range(EAVE_Y, top + 1):
        r = roof_radius(y)
        for x in range(GT_CENTRE[0] - 8, GT_CENTRE[0] + 9):
            for z in range(GT_CENTRE[1] - 8, GT_CENTRE[1] + 9):
                d = _roof_d(x, z)
                if r - ROOF_SHELL < d <= r:
                    grid.set(x, y, z, 'minecraft:oak_planks')
                elif y == EAVE_Y and r < d <= EAVE_R:
                    grid.set(x, y, z, G.state('oak_slab', type='bottom', waterlogged=False))
    x, z = GT_CENTRE
    grid.set(x, top + 1, z, G.state('cobblestone_wall', east='none', north='none', south='none', west='none',
                                    up=True, waterlogged=False))
    tip = G.state('oak_fence', east=False, north=False, south=False, west=False, waterlogged=False)
    grid.set(x, top + 2, z, tip)
    grid.set(x, top + 3, z, tip)
    for cx in range(CHIMNEY_X[0], CHIMNEY_X[1] + 1):
        for y in range(DORM_FEET, CHIMNEY_TOP):         # one deep: it leaves the slope at y 64, three courses high
            grid.set(cx, y, BREAST_Z[1], BRICK)
        grid.set(cx, CHIMNEY_TOP, BREAST_Z[1], G.state('stone_brick_slab', type='bottom', waterlogged=False))
    return top


def _armchair(grid, seat, facing, arms):
    """A stair seat whose back is on its `facing` side, with an open trapdoor arm on each listed side."""
    x, y, z = seat
    grid.set(x, y, z, _stairs('mangrove_stairs', facing))
    for side in arms:
        dx, dz = _STEP[side]
        grid.set(x + dx, y, z + dz, _trapdoor('spruce_trapdoor', side))


def build_common_room(grid):
    """The fireplace, the seats, the rug and the runner, the study corner, the beams and the stair."""
    y = CR_FEET
    # the fireplace: breast to the ceiling, a two-deep firebox with three campfires at its back, a brick hearth, the
    # mantel with candles on its ends
    for x in range(BREAST_X[0], BREAST_X[1] + 1):
        for z in range(BREAST_Z[0], BREAST_Z[1] + 1):
            for yy in range(y, DORM_FLOOR_Y):
                grid.set(x, yy, z, BRICK)
    for x in range(FIREBOX_X[0], FIREBOX_X[1] + 1):
        for z in range(BREAST_Z[0], BREAST_Z[1] + 1):
            grid.clear(x, y, z)
            grid.clear(x, y + 1, z)
            grid.set(x, CR_FLOOR_Y, z, 'minecraft:bricks')
        grid.set(x, y, BREAST_Z[1], G.state('campfire', facing='north', lit=True, signal_fire=False,
                                             waterlogged=False))
        grid.set(x, y + 2, BREAST_Z[0], 'minecraft:chiseled_stone_bricks')      # the lintel
        grid.set(x, CR_FLOOR_Y, BREAST_Z[0] - 1, 'minecraft:bricks')            # the hearth in front
    for x in range(BREAST_X[0], BREAST_X[1] + 1):
        grid.set(x, y + 2, BREAST_Z[0] - 1, _stairs('stone_brick_stairs', 'south', 'top'))
    for x in (BREAST_X[0], BREAST_X[1]):
        grid.set(x, y + 3, BREAST_Z[0] - 1, _candle('red'))
    # the settee and the two armchairs round the fire, on a red rug with gold corners
    for x in (60, 61, 62):
        grid.set(x, y, 174, _stairs('mangrove_stairs', 'north'))
    grid.set(59, y, 174, _trapdoor('spruce_trapdoor', 'west'))
    grid.set(63, y, 174, _trapdoor('spruce_trapdoor', 'east'))
    _armchair(grid, (58, y, 176), 'west', ('north', 'south'))
    _armchair(grid, (64, y, 176), 'east', ('north', 'south'))
    for x in range(59, 64):
        for z in (175, 176):
            corner = x in (59, 63)
            grid.set(x, y, z, 'minecraft:yellow_carpet' if corner else 'minecraft:red_carpet')
    # the runner from the door: red, edged in gold
    for z in range(169, 174):
        grid.set(61, y, z, 'minecraft:red_carpet')
        if z >= 170:
            grid.set(60, y, z, 'minecraft:yellow_carpet')
            grid.set(62, y, z, 'minecraft:yellow_carpet')
    # the study corner: a two-seat table with candles and a lantern, and a bookcase in the north-east
    for z in (171, 172):
        grid.set(65, y, z, G.state('dark_oak_slab', type='top', waterlogged=False))
        grid.set(64, y, z, _stairs('dark_oak_stairs', 'west'))
    grid.set(65, y + 1, 171, _candle('yellow'))
    grid.set(65, y + 1, 172, _lantern(False))
    grid.set(65, y, 170, 'minecraft:bookshelf')
    grid.set(65, y + 1, 170, 'minecraft:bookshelf')
    grid.set(66, y, 173, 'minecraft:bookshelf')
    grid.set(66, y + 1, 173, 'minecraft:bookshelf')
    # beams under the ceiling, each with a lantern
    for z in (172, 176):
        for x in range(58, 67):
            if (x, z) in gt_interior() and grid.is_air(x, DORM_FLOOR_Y - 1, z):
                grid.set(x, DORM_FLOOR_Y - 1, z, G.state('stripped_dark_oak_log', axis='x'))
        grid.set(61, DORM_FLOOR_Y - 2, z, _lantern(True))
    # the stair up the west wall: dark-oak stairs climbing south on a solid mass, the last tread the dormitory floor
    for z, feet in GT_STAIR:
        for x in GT_STAIR_X:
            for yy in range(y, feet - 1):
                grid.set(x, yy, z, DARK_PLANKS)
            grid.set(x, feet - 1, z, DARK_PLANKS if feet == DORM_FEET else _stairs('dark_oak_stairs', 'south'))
    for x, z in GT_STAIR_HOLE:
        grid.clear(x, DORM_FLOOR_Y, z)


def build_dormitory(grid):
    """Five four-posters, nightstands and trunks, a rail round the stair's opening, a runner, and the chandelier."""
    y = DORM_FEET
    for (hx, hz), facing in BEDS:
        dx, dz = _STEP[facing]
        fx, fz = hx - dx, hz - dz
        grid.set(hx, y, hz, G.state('red_bed', facing=facing, occupied=False, part='head'))
        grid.set(fx, y, fz, G.state('red_bed', facing=facing, occupied=False, part='foot'))
        px, pz = fx - dx, fz - dz
        for yy in (y, y + 1):
            grid.set(px, yy, pz, G.state('dark_oak_fence', east=False, north=False, south=False, west=False,
                                         waterlogged=False))
        for cx, cz in ((hx, hz), (fx, fz), (px, pz)):
            grid.set(cx, y + 2, cz, 'minecraft:red_wool')
    for z in (172, 175):                                # nightstands between the east beds
        grid.set(66, y, z, G.state('barrel', facing='up', open=False))
        grid.set(66, y + 1, z, _candle('yellow'))
    grid.set(56, y, 172, G.state('barrel', facing='east', open=False))     # a trunk by the west bed
    rails = [(58, y, z) for z in (174, 175, 176)] + [(x, y, 173) for x in GT_STAIR_X]
    for p in rails:
        grid.set(*p, 'minecraft:dark_oak_fence')
    _connect(grid, rails, 'fence')
    for z in range(170, 177):
        grid.set(61, y, z, 'minecraft:red_carpet')
    for z in range(170, 177):
        for x in (60, 62):
            if grid.is_air(x, y, z):
                grid.set(x, y, z, 'minecraft:yellow_carpet')
    # the chandelier: a chain from the cone's apex to a lantern above the beds' heads
    x, z = GT_CENTRE
    yy = EAVE_Y
    while grid.is_air(x, yy + 1, z):
        yy += 1
    for cy in range(EAVE_Y + 1, yy + 1):
        grid.set(x, cy, z, G.state('chain', axis='y', waterlogged=False))
    grid.set(x, EAVE_Y, z, _lantern(True))
    return len(BEDS)


def light_bridge(grid):
    """A lantern hung mid-bridge under the roof's log, so the walk from the Fat Lady's door is lit."""
    grid.set(*BRIDGE_LANTERN, _lantern(True))


def common_room_box():
    return (56, CR_FEET, 169, 66, DORM_FLOOR_Y - 1, 179)


def dorm_box():
    return (56, DORM_FEET, 169, 66, WALL_TOP, 179)


def in_gryffindor(p):
    """True for a cell of the Gryffindor tower's rooms (both storeys) or the bridge."""
    x, y, z = p
    if (x, z) in gt_footprint() and CR_FEET <= y <= WALL_TOP:
        return True
    return x == 61 and 156 <= z <= 168 and CR_FEET <= y <= CR_FEET + 2


def gryffindor_room_boxes():
    """{name: box} the Gryffindor tower's interiors: the bridge, the doorway, both storeys row by row, and the cone's
    hollow over the dormitory course by course (the cells inside the shell)."""
    out = {'gryffindor: the covered bridge': (61, CR_FEET, 156, 61, CR_FEET + 2, 167),
           'gryffindor: the doorway': (61, CR_FEET, 168, 61, CR_FEET + 1, 168),
           'gryffindor: the stair opening': (GT_STAIR_X[0], DORM_FLOOR_Y, 174, GT_STAIR_X[1], DORM_FLOOR_Y, 176)}
    for z, (x0, x1) in sorted(GT_ROWS.items()):
        out['gryffindor: common room row z %d' % z] = (x0, CR_FEET, z, x1, DORM_FLOOR_Y - 1, z)
        out['gryffindor: dormitory row z %d' % z] = (x0, DORM_FEET, z, x1, WALL_TOP, z)
    for y in range(EAVE_Y, roof_top() + 1):
        inner = roof_radius(y) - ROOF_SHELL
        cells = [(x, z) for x, z in gt_interior() if _roof_d(x, z) <= inner]
        rows = {}
        for x, z in cells:
            lo, hi = rows.get(z, (x, x))
            rows[z] = (min(lo, x), max(hi, x))
        for z, (x0, x1) in sorted(rows.items()):
            out['gryffindor: roof hollow y %d z %d' % (y, z)] = (x0, y, z, x1, y, z)
    return out


def beds(cells):
    """[(head, foot)] every whole bed (a head with its foot behind it) in the dormitory."""
    out = []
    x0, y0, z0, x1, y1, z1 = dorm_box()
    for (x, y, z), s in cells.items():
        if not (x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1):
            continue
        if checks.block_id(s).endswith('_bed') and 'part=head' in s:
            facing = s.split('facing=', 1)[1].split(',', 1)[0].rstrip(']')
            dx, dz = _STEP[facing]
            foot = cells.get((x - dx, y, z - dz), '')
            if checks.block_id(foot).endswith('_bed') and 'part=foot' in foot:
                out.append(((x, y, z), (x - dx, y, z - dz)))
    return sorted(out)


# ================================================================ the central tower's top

def band_cells():
    return set(_rows_cells(BAND_ROWS))


def office_cells():
    return set(_rows_cells(OFFICE_ROWS))


def measured_band(cells):
    """The band's interior rows at the gallery's feet level, as BAND_ROWS writes them."""
    rows = {}
    for x, z in S._flood(cells, GALLERY_FEET):
        lo, hi = rows.get(z, (x, x))
        rows[z] = (min(lo, x), max(hi, x))
    return rows


def measured_office(cells):
    rows = {}
    for x, z in S._flood(cells, OFFICE_FEET):
        lo, hi = rows.get(z, (x, x))
        rows[z] = (min(lo, x), max(hi, x))
    return rows


def in_oculus(x, z):
    return OCULUS_X[0] <= x <= OCULUS_X[1] and OCULUS_Z[0] <= z <= OCULUS_Z[1]


def build_helix(grid):
    """The helical stair from the top landing to the gallery, its rails, and lanterns on two of its posts."""
    for i, (cells, feet) in enumerate(HELIX):
        nxt = HELIX[i + 1] if i + 1 < len(HELIX) else (((HELIX_ARRIVAL[0], HELIX_ARRIVAL[2]),), HELIX_ARRIVAL[1])
        climbing = nxt[1] == feet + 1 and len(cells) <= 2       # a corner landing stays level blocks
        for x, z in cells:
            if climbing:
                nx, nz = min(nxt[0], key=lambda c: abs(c[0] - x) + abs(c[1] - z))
                if (nx, nz) == (x, z):
                    facing = 'north'
                elif abs(nx - x) >= abs(nz - z):
                    facing = 'east' if nx > x else 'west'
                else:
                    facing = 'south' if nz > z else 'north'
                grid.set(x, feet - 1, z, _stairs('stone_brick_stairs', facing))
                below = (x, feet - 2, z)
                if grid.is_air(*below):
                    grid.set(*below, _stairs('stone_brick_stairs', _BACK[facing], 'top'))
            else:
                grid.set(x, feet - 1, z, S.FLOOR)
    feet_at = {}
    for cells, feet in HELIX:
        for c in cells:
            feet_at[c] = feet
    rails = []
    for x, z in HELIX_RAILS:
        f = max(feet_at.get((x + dx, z + dz), 0) for dx, dz in _STEP.values())
        if grid.is_air(x, f, z):
            grid.set(x, f, z, 'minecraft:dark_oak_fence')
            rails.append((x, f, z))
    # the top landing's rail gives way to the first tread (its west newel lantern would stand on the tread); the
    # next post re-joins
    grid.clear(55, S.TOP_FEET + 1, 151)
    rails.append((57, S.TOP_FEET, 151))
    S.connect_fences(grid, rails)
    for x, z in ((57, 151), (57, 144)):
        f = max(feet_at.get((x + dx, z + dz), 0) for dx, dz in _STEP.values())
        grid.set(x, f + 1, z, _lantern(False))


def build_gallery(grid):
    """The gallery floor round the oculus, the rails, the arches and their telescopes, the chart table and lectern."""
    y = GALLERY_FLOOR_Y
    rail = set(OCULUS_RAIL)
    for x, z in band_cells():
        if in_oculus(x, z) or (x, z) in STAIRWELL:
            continue
        grid.set(x, y, z, S.EDGE if (x, z) in rail else S.FLOOR)
    posts = [(x, GALLERY_FEET, z) for x, z in OCULUS_RAIL + STAIRWELL_RAILS]
    for p in posts:
        grid.set(*p, 'minecraft:dark_oak_fence')
    for x, z in ((57, 144), (65, 144), (57, 152), (65, 152)):
        grid.set(x, GALLERY_FEET + 1, z, _lantern(False))
    sills = []
    for wall, (tx, tz), facing in ARCHES:
        for wx, wz in wall:
            grid.set(wx, GALLERY_FEET, wz, 'minecraft:dark_oak_fence')
            sills.append((wx, GALLERY_FEET, wz))
            for yy in (GALLERY_FEET + 1, GALLERY_FEET + 2):
                grid.clear(wx, yy, wz)
        grid.set(tx, GALLERY_FEET, tz, 'minecraft:dark_oak_fence')
        posts.append((tx, GALLERY_FEET, tz))
        grid.set(tx, GALLERY_FEET + 1, tz, G.state('lightning_rod', facing=facing, powered=False, waterlogged=False))
    S.connect_fences(grid, posts + sills)
    grid.set(67, GALLERY_FEET, 146, 'minecraft:cartography_table')
    grid.set(55, GALLERY_FEET, 150, G.state('lectern', facing='east', has_book=False, powered=False))


def build_office_stair(grid):
    """The spiral from the gallery to the office round a stone newel, solid under every tread, and the gargoyle at its
    foot: a chiseled plinth, an andesite-wall body and a hunched stone-brick head looking south at the visitor."""
    nx, nz = SPIRAL_NEWEL
    for yy in range(GALLERY_FEET, OFFICE_FLOOR_Y + 1):
        grid.set(nx, yy, nz, BRICK)
    for (x, z), feet, facing in SPIRAL:
        for yy in range(GALLERY_FEET, feet - 1):
            grid.set(x, yy, z, BRICK)
        grid.set(x, feet - 1, z, _stairs('stone_brick_stairs', facing))
    gx, gz = GARGOYLE
    grid.set(gx, GALLERY_FEET, gz, 'minecraft:chiseled_stone_bricks')
    grid.set(gx, GALLERY_FEET + 1, gz, G.state('andesite_wall', east='none', north='none', south='none',
                                               west='none', up=True, waterlogged=False))
    grid.set(gx, GALLERY_FEET + 2, gz, _stairs('stone_brick_stairs', 'north', 'top'))


def build_office(grid, rnd_angles=PORTRAIT_ANGLES):
    """The office floor (dark oak, glass over the oculus), the chandelier, the desk and chair, the perch, the
    instrument tables, the chest's place, the portraits' walls and the bookcases. Returns (portraits, chest)."""
    y = OFFICE_FLOOR_Y
    hole = set(OFFICE_HOLE)
    for x, z in band_cells() | office_cells():
        if (x, z) in hole or not _open(grid, x, y, z):
            continue
        grid.set(x, y, z, 'minecraft:glass' if in_oculus(x, z) else DARK_PLANKS)
    ax, az = S.AXIS
    grid.set(ax, y, az, 'minecraft:glass')               # the staircase's chain now hangs from the glass floor
    for yy in range(OFFICE_FEET, S.CONE_TOP + 1):
        grid.clear(ax, yy, az)
    for yy in range(CHANDELIER_Y + 1, S.CONE_TOP + 1):
        grid.set(ax, yy, az, S.CHAIN)
    grid.set(ax, CHANDELIER_Y, az, _lantern(True))
    # the rail round the spiral's opening, on the newel a post with a lantern
    rails = [(53, OFFICE_FEET, 146), (54, OFFICE_FEET, 147), (55, OFFICE_FEET, 146), (55, OFFICE_FEET, 145)]
    for p in rails:
        grid.set(*p, 'minecraft:dark_oak_fence')
    S.connect_fences(grid, rails)
    grid.set(55, OFFICE_FEET + 1, 145, _lantern(False))
    # the desk overlooking the glass floor, the chair behind it
    for x, z in DESK:
        grid.set(x, OFFICE_FEET, z, _stairs('dark_oak_stairs', 'south', 'top'))
    grid.set(DESK[0][0], OFFICE_FEET + 1, DESK[0][1], _candle())
    grid.set(DESK[-1][0], OFFICE_FEET + 1, DESK[-1][1], _candle())
    grid.set(DESK[3][0], OFFICE_FEET + 1, DESK[3][1], G.state('brewing_stand', has_bottle_0=False,
                                                            has_bottle_1=False, has_bottle_2=False))
    grid.set(CHAIR[0], OFFICE_FEET, CHAIR[1], _stairs('dark_oak_stairs', 'north'))
    # the phoenix perch
    px, pz = PERCH
    grid.set(px, OFFICE_FEET, pz, 'minecraft:gold_block')
    grid.set(px, OFFICE_FEET + 1, pz, G.state('dark_oak_fence', east=False, north=False, south=False, west=False,
                                              waterlogged=False))
    grid.set(px, OFFICE_FEET + 2, pz, G.state('light_weighted_pressure_plate', power=0))
    # the instrument tables
    for (x, z), what in INSTRUMENT_TABLES:
        grid.set(x, OFFICE_FEET, z, G.state('dark_oak_fence', east=False, north=False, south=False, west=False,
                                            waterlogged=False))
        if what == 'end_rod':
            grid.set(x, OFFICE_FEET + 1, z, G.state('end_rod', facing='up'))
        else:
            grid.set(x, OFFICE_FEET + 1, z, G.state('brewing_stand', has_bottle_0=True, has_bottle_1=False,
                                                    has_bottle_2=True))
    # the portraits first (each on a plank course of the cone with its two front cells free), then bookcases along
    # the rest of the wall wherever the wall is planks (never in front of a window) and nothing else stands
    keep = {(CHAIR[0], CHAIR[1]), PERCH, (OFFICE_CHEST[0], OFFICE_CHEST[2]), SPIRAL_NEWEL,
            (SPIRAL_ARRIVAL[0], SPIRAL_ARRIVAL[2]), (56, 144), (56, 145)} | set(DESK) | hole
    keep |= {c for c, _ in INSTRUMENT_TABLES}
    inside = office_cells()
    edge = []                                            # (inside cell, wall cell, facing into the room)
    for x, z in sorted(inside):
        for side, (dx, dz) in _STEP.items():
            w = (x + dx, z + dz)
            if w not in inside:
                edge.append(((x, z), w, _BACK[side]))

    def plank(c, yy):
        return checks.block_id(grid.get(c[0], yy, c[1])).endswith('_planks')

    def angle(c):
        return math.degrees(math.atan2(c[1] - az, c[0] - ax))

    portraits = []
    used = set(keep)
    for target in rnd_angles:
        best = None
        for c, w, facing in edge:
            if c in used or not (plank(w, OFFICE_FEET) and plank(w, OFFICE_FEET + 1)):
                continue
            if not (grid.is_air(c[0], OFFICE_FEET, c[1]) and grid.is_air(c[0], OFFICE_FEET + 1, c[1])):
                continue
            diff = abs((angle(c) - target + 180) % 360 - 180)
            if best is None or diff < best[0]:
                best = (diff, c, w, facing)
        _, c, w, facing = best
        portraits.append({'at': [w[0], OFFICE_FEET, w[1]], 'facing': facing})
        for dx in (-1, 0, 1):                            # leave the portrait room either side (2-wide variants)
            for dz in (-1, 0, 1):
                used.add((c[0] + dx, c[1] + dz))
    shelves = 0
    for c, w, facing in edge:
        if c in used or in_oculus(*c):
            continue
        if not (plank(w, OFFICE_FEET) and plank(w, OFFICE_FEET + 1)):
            continue
        if not grid.is_air(c[0], OFFICE_FEET, c[1]):
            continue
        grid.set(c[0], OFFICE_FEET, c[1], 'minecraft:bookshelf')
        grid.set(c[0], OFFICE_FEET + 1, c[1], 'minecraft:bookshelf')
        used.add(c)
        shelves += 1
    chest = {'at': list(OFFICE_CHEST), 'facing': 'west', 'theme': 'TOWER'}
    return portraits, chest, shelves


def gallery_box():
    x0 = min(r[0] for r in BAND_ROWS.values())
    x1 = max(r[1] for r in BAND_ROWS.values())
    return (x0, GALLERY_FEET, min(BAND_ROWS), x1, OFFICE_FLOOR_Y - 1, max(BAND_ROWS))


def platform_cells(walk):
    """The walked cells of the Astronomy gallery: inside the band's plan or in its arches' openings (cells of the
    band's wall ring), from its floor to the office floor."""
    band = band_cells() | {c for wall, _, _ in ARCHES for c in wall}
    return sorted(c for c in walk if GALLERY_FEET <= c[1] < OFFICE_FLOOR_Y and (c[0], c[2]) in band)


def unrailed(grid, walk):
    """[(cell, neighbour, drop)] for every walked gallery cell beside a column a player would fall more than 3 down:
    the open edges a rail (a fence, a wall, any block) must close."""
    cells = grid.cells
    air = 'minecraft:air'
    out = []
    for x, y, z in platform_cells(walk):
        for dx, dz in _STEP.values():
            nx, nz = x + dx, z + dz
            if not (checks.passable(cells.get((nx, y, nz), air)) and checks.passable(cells.get((nx, y + 1, nz), air))):
                continue
            drop = 0
            while drop <= 3 and not checks.supports(cells.get((nx, y - 1 - drop, nz), air)) \
                    and not checks.climbable(cells.get((nx, y - drop, nz), air)):
                drop += 1
            if drop > 3:
                out.append(((x, y, z), (nx, y, nz), drop))
    return out


def tower_light_boxes():
    """The boxes the no-dark-floor check covers: the bridge, both Gryffindor storeys, the gallery (its feet level and
    the spiral's treads) and the office's floor."""
    boxes = [(61, CR_FEET, 156, 61, CR_FEET, 168), common_room_box(), dorm_box()]
    boxes += _row_boxes(BAND_ROWS, GALLERY_FEET, OFFICE_FLOOR_Y)
    boxes += _row_boxes(OFFICE_ROWS, OFFICE_FEET, OFFICE_FEET + 1)
    return boxes


def office_room_boxes(cells):
    """{name: box} the gallery and the office: the band's rows from the gallery floor up, and the cone's hollow over
    the office course by course (the staircase's own flood of each course)."""
    out = {}
    for b in _row_boxes(BAND_ROWS, GALLERY_FEET, OFFICE_FLOOR_Y - 1):
        out['astronomy gallery row z %d' % b[2]] = b
    for y in range(OFFICE_FEET, S.CONE_TOP + 1):
        plan = S._flood(cells, y)
        if not plan:
            continue
        for b in S._row_boxes(plan, y, y):
            out['headmaster\'s office y %d z %d x %d' % (y, b[2], b[0])] = b
    for (wall, _, _) in ARCHES:
        for wx, wz in wall:
            out['astronomy arch %d %d' % (wx, wz)] = (wx, GALLERY_FEET + 1, wz, wx, GALLERY_FEET + 2, wz)
    return out


def changed_boxes():
    """(name, inclusive box) for every old-castle block this task may change: build_castle.CHANGED_ON_PURPOSE.
    The Gryffindor tower's top (walls and roof boxes, ruling R26), the gallery floor over the band's rows (the plain
    ring's top course and its corbels, all inside the band's wall), and the four arches through the band's wall."""
    out = [('gryffindor tower: the jettied storeys (R26: this tower\'s top may change shape)', WALLS_BOX),
           ('gryffindor tower: the new cone in place of the old', ROOF_BOX)]
    for b in _row_boxes(BAND_ROWS, GALLERY_FLOOR_Y, GALLERY_FLOOR_Y):
        out.append(('astronomy gallery: the floor over the band, row z %d' % b[2], b))
    for wall, _, facing in ARCHES:
        xs = [c[0] for c in wall]
        zs = [c[1] for c in wall]
        out.append(('astronomy gallery: the %s arch through the band\'s wall' % facing,
                    (min(xs), GALLERY_FEET, min(zs), max(xs), GALLERY_FEET + 2, max(zs))))
    return out


def room_boxes(cells):
    """{name: box} every interior this task builds (for build_castle.ROOMS)."""
    out = dict(gryffindor_room_boxes())
    out.update(office_room_boxes(cells))
    return out


# ================================================================ placing

def place_towers(grid):
    """Build the three tower-top rooms into the castle grid. Returns their anchors: 'chests' (the TOWER chest),
    'banners' (Gryffindor, in the common room), 'student_spots' (the common room) and 'paintings' (the office)."""
    clear_tower_top(grid)
    corbel = build_corbel(grid)
    build_jetty(grid)
    roof = build_roof(grid)
    build_common_room(grid)
    n_beds = build_dormitory(grid)
    light_bridge(grid)
    build_helix(grid)
    build_gallery(grid)
    build_office_stair(grid)
    portraits, chest, shelves = build_office(grid)
    lights = hall.light_dark_floors(grid, tower_light_boxes() + S.light_boxes(grid.cells))
    place_towers.stats = {'corbel_cells': corbel, 'roof_tip_y': roof, 'beds': n_beds, 'office_shelves': shelves,
                          'lights': lights}
    return {
        'chests': [chest],
        'banners': [{'at': list(at), 'facing': facing, 'house': 'gryffindor'} for at, facing in BANNERS],
        'student_spots': [list(s) for s in STUDENT_SPOTS],
        'paintings': portraits,
    }
