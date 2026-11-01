"""The Great Hall wing of Mini Hogwarts: the Sorting memory's hall, roofed, swapped in for the old diagonal wing.

    from mini_hogwarts import hall
    anchors = hall.place_hall(grid)      # grid is in the castle frame (build_castle.BASE_SHIFT applied)

WHAT IT DOES
------------
1. Removes the old wing: the hollow diagonal hall that ran from the central tower's north-west side towards
   the north-east, with its roof and ridge turrets. Its rock stays (stone below its old floor line, grass,
   dirt, gravel); the central tower and the little turret at its south-west end stay.
2. Builds the memory set with its roof (great_hall_set.build(open_roof=False)) and re-skins its OUTER faces
   in the castle's palette: stone walls, cobblestone quoins, base course, cornice and buttresses, oak-plank
   roofs, a cobblestone-wall parapet at the eaves. The inside is the memory's, untouched. The entrance hall,
   a flat-topped box in the memory, gets a cross-gabled oak roof at the castle's pitch (2 up for 1 across,
   the same as the hall's). Light blocks go just inside every tall window so the windows glow at night.
3. Pastes it on the castle's axis: one clockwise quarter turn, so the set's +x (staff end -> doors) runs
   south, the High Table is at the north end and the entrance hall's far wall touches the central tower's
   north face; the hall's centre line is the tower's centre line; the floor is MAIN_FLOOR_Y.
4. A solid stone plinth under every column of the wing, from the floor down to the old rock (or y 0), and
   north of the old castle a rock skirt sloping down from the plinth so the wing reads as built on the hill.
5. The entrance hall's west wall gets a door onto a covered gallery that runs down the wing's west side to
   the old viaduct head (the top of the west descent), so the viaduct, the hall and the stool are one walk.
6. Light blocks wherever the hall, the entrance hall or the gallery would otherwise have dark floor.
7. Returns the anchors (castle coordinates): the enchanted-ceiling rectangle, the floating-candle boxes,
   light rays through the tall windows, the two portal door leaves, the hall box, the main entrance, the
   stool and the Great Hall chest.

Coordinates: the set's own frame is "local" (great_hall_set's: x along the hall, staff end at x 0, floor
block y 0); the castle frame is the new schematic's. A local cell (x, y, z) lands on castle cell
(ox + 44 - z, oy + y, oz + x); a local point (px, py, pz) on (ox + 45 - pz, oy + py, oz + px); a yaw turns
by +90 (Minecraft yaw: 0 faces +z, 90 faces -x).
"""

import math
import os
import random
import sys
from collections import Counter, deque

from .build_castle import BASE_SHIFT, MAIN_FLOOR_Y
from . import checks
from .grid import paste

_PENSIEVE = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'pensieve_scenes')
if _PENSIEVE not in sys.path:
    sys.path.insert(0, _PENSIEVE)
import great_hall_set as G  # noqa: E402

SET_SIZE = G.SIZE                      # (114, 57, 45) local
TURNS = 1                              # local +x -> castle +z (rotate_state: clockwise from above)

# ---------------------------------------------------------------- the castle around the wing (old frame)
# Measured from the old schematic's slices: the central tower's ring spans x 53..69, z 56..72 at every
# height from y 28 up, so its centre is (61.5, 64.5) and its north face is the z 56 row.
OLD_TOWER = (61.5, 64.5)
OLD_TOWER_R = 8.5
OLD_TOWER_NORTH_Z = 56
OLD_TURRET = (35.0, 51.5)              # the small round turret beside the viaduct head (ring x 32..38, z 47..56)
OLD_TURRET_R = 4.5

# The old wing in the old frame: between its two long walls (x + z = 89 and 105, so 85..110 with the
# eaves) and from its south-west porch (x - z = -22) to its north-east gable (x - z = 46), south to the
# tower; the tower and the turret are carved out of that diagonal box.
OLD_WING_S = (85, 110)
OLD_WING_D = (-23, 48)
OLD_WING_MAX_Z = 63
OLD_WING_MIN_Y = 25                    # removal starts here: the viaduct head's floor (y 24) stays
OLD_FLOOR_Y = 29                       # the wing's walls start here; stone below it is the hill
OLD_WING_IDS = frozenset(('minecraft:stone', 'minecraft:cobblestone', 'minecraft:oak_planks',
                          'minecraft:black_stained_glass', 'minecraft:black_stained_glass_pane'))
TERRAIN_NAMES = frozenset(('stone', 'grass_block', 'coarse_dirt', 'dirt', 'gravel', 'mossy_cobblestone'))

# ---------------------------------------------------------------- where the set goes (castle frame)
TOWER_NORTH_Z = OLD_TOWER_NORTH_Z + BASE_SHIFT[2]
ORIGIN = (int(OLD_TOWER[0] - 22.5) + BASE_SHIFT[0],       # centre line local z 22.5 on the tower's x
          MAIN_FLOOR_Y,
          TOWER_NORTH_Z - SET_SIZE[0])                    # the entrance hall's far wall touches the tower


def cell(x, y, z):
    """A local cell to its castle cell."""
    ox, oy, oz = ORIGIN
    return (ox + SET_SIZE[2] - 1 - z, oy + y, oz + x)


def point(px, py, pz):
    """A local point (block-corner coordinates) to the castle frame."""
    ox, oy, oz = ORIGIN
    return [ox + SET_SIZE[2] - pz, oy + py, oz + px]


def yaw(local_yaw):
    """A local Minecraft yaw to the castle frame, in (-180, 180]."""
    y = (local_yaw + 90 * TURNS) % 360
    return y - 360 if y > 180 else y


def in_old_wing(x, z):
    """Whether castle column (x, z) is inside the old wing's outline."""
    x -= BASE_SHIFT[0]
    z -= BASE_SHIFT[2]
    s, d = x + z, x - z
    if not (OLD_WING_S[0] <= s <= OLD_WING_S[1] and OLD_WING_D[0] <= d <= OLD_WING_D[1] and z <= OLD_WING_MAX_Z):
        return False
    if math.hypot(x + 0.5 - OLD_TOWER[0], z + 0.5 - OLD_TOWER[1]) <= OLD_TOWER_R + 2:
        return False
    return math.hypot(x + 0.5 - OLD_TURRET[0], z + 0.5 - OLD_TURRET[1]) > OLD_TURRET_R + 0.5


# the walk the checks take: the hall floor in front of the stool, and a cell on the viaduct deck
STOOL_CELL = cell(*G.STOOL)
STOOL_FRONT_CELL = cell(21, 1, 22)
VIADUCT_DECK_CELL = (41 + BASE_SHIFT[0], MAIN_FLOOR_Y + 1, 70 + BASE_SHIFT[2])

# ---------------------------------------------------------------- the gallery to the viaduct head (castle)
# The entrance hall (local z 5..39 -> castle x 44..78) is narrower than the hall (castle x 39..83): the strip
# it leaves on the west, x 39..43, runs from the hall's corner straight to the viaduct head's floor.
GALLERY_X = (cell(0, 0, 44)[0], cell(0, 0, 40)[0])                 # 39..43
GALLERY_Z = (cell(85, 0, 0)[2], cell(85, 0, 0)[2] + 26)            # 111..137, onto the old porch floor
GALLERY_PIER_STEP = 4
DOOR_LOCAL_X = (89, 91)                                            # the west door, in front of the stair masses
MAIN_ENTRANCE = (GALLERY_X[1] - 1, MAIN_FLOOR_Y + 1, cell(90, 0, 0)[2])

# ---------------------------------------------------------------- the re-skin
PIERS = tuple(G.PIERS) + G.FIRE_BAY    # buttresses outside every pier and the fireplace's two
WALL_TOP_Y = 21                        # the side walls' top course (the roof springs from it)
WINDOW_LIGHT = 13
EH_ROOF_X = (85, 113)                  # the entrance hall's box, local
EH_ROOF_Z = (5, 39)
EH_TOP_Y = G.EH_VAULT_TOP              # 25: the box is solid up to here

_WOOD = frozenset(('dark_oak_planks', 'stripped_dark_oak_wood', 'dark_oak_log', 'dark_oak_wood', 'sculk',
                   'spruce_planks', 'mangrove_planks'))
_KEEP_NAMES = frozenset(('iron_bars', 'light', 'chain', 'lantern', 'soul_lantern'))
_KEEP_PARTS = ('glass', 'torch', 'banner', 'candle')


def _kept(short):
    return short in _KEEP_NAMES or short.endswith('_door') or any(k in short for k in _KEEP_PARTS)


def _split(state):
    return G.parse_state(state)


def _short(state):
    return state.split('[', 1)[0].replace('minecraft:', '')


def exterior_air(g):
    """Air cells of the set's box (grown by one, floor level and up) reachable from outside it."""
    x0, y0, z0 = -1, 0, -1
    x1, y1, z1 = SET_SIZE[0], SET_SIZE[1], SET_SIZE[2]
    start = (x0, y0, z0)
    seen = {start}
    queue = deque([start])
    while queue:
        x, y, z = queue.popleft()
        for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if n in seen or n in g:
                continue
            if not (x0 <= n[0] <= x1 and y0 <= n[1] <= y1 and z0 <= n[2] <= z1):
                continue
            seen.add(n)
            queue.append(n)
    return seen


def reskin_state(state, base_course, quoin):
    """The castle-palette block for one outer-face block of the set (properties carried over)."""
    short = _short(state)
    if _kept(short):
        return state
    _, props = _split(state)
    if short in _WOOD:
        return 'minecraft:oak_planks'
    wooden = 'oak' in short or 'spruce' in short or 'mangrove' in short
    if short.endswith('_stairs'):
        return G.state('oak_stairs' if wooden else 'cobblestone_stairs', **props)
    if short.endswith('_slab'):
        return G.state('oak_slab' if wooden else 'smooth_stone_slab', **props)
    if short.endswith('_wall'):
        return G.state('cobblestone_wall', **props)
    if short.endswith('_fence'):
        return G.state('oak_fence', **props)
    if short.endswith('_fence_gate'):
        return G.state('oak_fence_gate', **props)
    if short.endswith('_trapdoor'):
        return G.state('oak_trapdoor', **props)
    if set(props) - {'waterlogged'}:            # anything else with a shape stays what it is
        return state
    return 'minecraft:cobblestone' if (base_course or quoin) else 'minecraft:stone'


def reskin(g):
    """Re-skin the set's outer faces in place. Returns (Counter of the ids that were outer faces, the outside air
    cells), so callers can check the flood never got into the hall."""
    outside = exterior_air(g)
    seen = Counter()
    changes = {}
    for (x, y, z), s in g.items():
        ex = (x + 1, y, z) in outside or (x - 1, y, z) in outside
        ez = (x, y, z + 1) in outside or (x, y, z - 1) in outside
        ey = (x, y + 1, z) in outside
        if not (ex or ez or ey):
            continue
        seen[s.split('[', 1)[0]] += 1
        side_wall_top = y == WALL_TOP_Y and (z <= 4 or z >= SET_SIZE[2] - 5)
        changes[(x, y, z)] = reskin_state(s, y <= 1 or side_wall_top, ex and ez)
    g.update(changes)
    return seen, outside


def add_buttresses_and_parapet(g):
    """The side walls' outer dress (local frame). Their face is z 1 (and 43), with each tall window's glass set
    one block proud of it in z 0 (44), as bare panes in the memory. Here that outer course becomes masonry: a
    cobblestone base course (y 0..3) with a smooth-stone water table on it, a cobblestone frame round every
    pane, two-step stair-capped buttresses against the wall at every pier, a smooth-stone string course under
    the eaves and a cobblestone-wall parapet along the wall tops."""
    cob = 'minecraft:cobblestone'
    last = SET_SIZE[2] - 1
    xs = [x for x in range(0, SET_SIZE[0]) if (x, WALL_TOP_Y, 1) in g]
    lo, hi = min(xs), max(xs)                                  # the side walls run x 8..84
    slab_top = G.state('smooth_stone_slab', type='top', waterlogged=False)
    slab_bottom = G.state('smooth_stone_slab', type='bottom', waterlogged=False)
    for proud, deep, face, edge in ((0, -1, 'south', 1), (last, last + 1, 'north', last - 1)):
        # frames round the panes
        panes = sorted({x for (x, y, z), st in g.items() if z == proud and 'glass' in st})
        for x in panes:
            ys = [y for (xx, y, z) in g if xx == x and z == proud]
            g[(x, min(ys) - 1, proud)] = cob
            g[(x, max(ys) + 1, proud)] = cob
            for jamb in (x - 1, x + 1):
                if jamb not in panes:
                    for y in range(min(ys) - 1, max(ys) + 2):
                        g[(jamb, y, proud)] = cob
        # base course and water table
        for x in range(lo, hi + 1):
            for y in range(0, 4):
                g.setdefault((x, y, proud), cob)
            g.setdefault((x, 4, proud), slab_bottom)
        # buttresses
        for p in PIERS:
            for y in range(0, 17):
                g[(p, y, proud)] = cob
            g[(p, 17, proud)] = G.state('cobblestone_stairs', facing=face, half='bottom', shape='straight',
                                        waterlogged=False)
            for y in range(0, 9):
                g[(p, y, deep)] = cob
            g[(p, 9, deep)] = G.state('cobblestone_stairs', facing=face, half='bottom', shape='straight',
                                      waterlogged=False)
        # string course under the eaves, parapet on the wall top's outer edge
        for x in range(lo, hi + 1):
            g.setdefault((x, WALL_TOP_Y - 1, proud), slab_top)
            if (x, WALL_TOP_Y + 1, edge) in g or (x, WALL_TOP_Y, edge) not in g:
                continue
            post = x in PIERS or x in (lo, hi)
            g[(x, WALL_TOP_Y + 1, edge)] = G.state('cobblestone_wall', east='low' if x < hi else 'none',
                                                   west='low' if x > lo else 'none', north='none', south='none',
                                                   up=post, waterlogged=False)


def add_entrance_hall_dress(g):
    """The entrance hall's open (east) side wall: buttresses outside its pilasters and a smooth-stone string
    course at the vault's springing, so its 25-block face reads as castle wall, not a box. The west side is
    the gallery's and keeps its plain face."""
    face = G.EH_Z0 - 3                                       # the wall's outer face is local z 5; out from it
    for x in (88, 93, 98, 103, 108):
        for y in range(0, 21):
            g[(x, y, face)] = 'minecraft:cobblestone'
        g[(x, 21, face)] = G.state('cobblestone_stairs', facing='south', half='bottom', shape='straight',
                                   waterlogged=False)
        for y in range(0, 10):
            g[(x, y, face - 1)] = 'minecraft:cobblestone'
        g[(x, 10, face - 1)] = G.state('cobblestone_stairs', facing='south', half='bottom', shape='straight',
                                       waterlogged=False)
    for x in range(EH_ROOF_X[0], EH_ROOF_X[1] + 1):
        for y in (G.EH_WALL_TOP, 4):
            if (x, y, face) not in g:
                g[(x, y, face)] = G.state('smooth_stone_slab', type='top', waterlogged=False)


def add_apse_battlements(g):
    """Merlons round the apse's flat stone cap (every other block on its rim), as the castle's own flat tops have."""
    rim = [(x, z) for x in range(APSE_X[0], APSE_X[1] + 1) for z in range(APSE_Z[0], APSE_Z[1] + 1)
           if (x, APSE_CAP_Y, z) in g and (x in APSE_X or z in APSE_Z)]
    top = max(y for (x, y, z) in g if APSE_X[0] <= x <= APSE_X[1] and APSE_Z[0] <= z <= APSE_Z[1])
    for x, z in rim:
        if (x + z) % 2 == 0:
            g[(x, top + 1, z)] = 'minecraft:cobblestone'
        else:
            g[(x, top + 1, z)] = G.state('smooth_stone_slab', type='bottom', waterlogged=False)


def _disc(r):
    """Cell offsets (du, dv) of a disc of radius r about a cell's centre, and of the ring just outside it."""
    inner, ring = [], []
    n = int(math.ceil(r)) + 2
    for du in range(-n, n + 1):
        for dv in range(-n, n + 1):
            d = math.hypot(du, dv)
            if d <= r:
                inner.append((du, dv))
            elif d <= r + 1.0:
                ring.append((du, dv))
    return inner, ring


def add_gable_oculi(g):
    """A round window of dark castle glass in a cobblestone ring on each big plain gable: both of the entrance
    hall's (over its attic) and the hall's south gable above the entrance hall roof (over the roof space,
    behind the doors), each with a light behind it. Returns the count."""
    glass = 'minecraft:black_stained_glass'
    light = G.state('light', level=WINDOW_LIGHT, waterlogged=False)
    inner, ring = _disc(3.5)
    ex = (EH_ROOF_X[0] + EH_ROOF_X[1]) // 2               # 99: the entrance hall's ridge line
    jobs = [(('x', ex, z), 38, behind) for z, behind in ((EH_ROOF_Z[0], 1), (EH_ROOF_Z[1], -1))]
    jobs.append((('z', G.AXIS_Z, G.HALL_X1), 38, -1))  # the hall's south gable, outer face x 84
    for (axis, u0, plane), yc, behind in jobs:
        for du, dv in inner + ring:
            u, y = u0 + du, yc + dv
            p = (u, y, plane) if axis == 'x' else (plane, y, u)
            if (du, dv) in ring:
                if p in g:
                    g[p] = 'minecraft:cobblestone'
                continue
            g[p] = glass
            q = (u, y, plane + behind) if axis == 'x' else (plane + behind, y, u)
            if q in g and (du, dv) != (0, 0):
                del g[q]                                      # open the wall's inner course behind the glass
        c = (u0, yc, plane + 2 * behind) if axis == 'x' else (plane + 2 * behind, yc, u0)
        if c not in g:
            g[c] = light
    return len(jobs)


FLECHES = (26, 66)                     # local x of the two ridge spires, over the second bay from each end
RIDGE_Y = 56


def add_ridge_fleches(g):
    """Two small spires on the hall's ridge, in the castle's own language (a stone lantern with cobblestone
    corners and a smooth-stone band, a stepped oak spire, a wall finial), so the long roof has the broken,
    spiky skyline the old wing's ridge turrets gave it."""
    zc = G.AXIS_Z
    for xc in FLECHES:
        for x in range(xc - 1, xc + 2):
            for z in range(zc - 1, zc + 2):
                corner = x != xc and z != zc
                for y in range(RIDGE_Y - 2, RIDGE_Y + 4):
                    g[(x, y, z)] = 'minecraft:cobblestone' if corner else 'minecraft:stone'
                if not corner:
                    g[(x, RIDGE_Y + 2, z)] = 'minecraft:black_stained_glass' if (x, z) != (xc, zc) else 'minecraft:stone'
                g[(x, RIDGE_Y + 4, z)] = G.state('smooth_stone_slab', type='double', waterlogged=False)
                g[(x, RIDGE_Y + 5, z)] = 'minecraft:oak_planks'
        for y in range(RIDGE_Y + 6, RIDGE_Y + 10):
            g[(xc, y, zc)] = 'minecraft:oak_planks'
        g[(xc, RIDGE_Y + 10, zc)] = G.state('cobblestone_wall', east='none', west='none', north='none',
                                            south='none', up=True, waterlogged=False)


def add_entrance_hall_roof(g):
    """A cross-gabled oak roof on the entrance hall's flat top: ridge across the hall's axis (local z), 2 up for
    every 1 across like the hall's own roof, stone gable walls over the side walls, and light in the closed
    attic so nothing spawns up there."""
    x0, x1 = EH_ROOF_X
    z0, z1 = EH_ROOF_Z
    planks = 'minecraft:oak_planks'
    for x in range(x0, x1 + 1):
        k = min(x - x0, x1 - x)
        base = EH_TOP_Y + 1 + 2 * k
        for z in range(z0 - 1, z1 + 2):                      # one block of eave over each gable
            g[(x, base, z)] = planks
            g[(x, base + 1, z)] = planks
        for z in (z0, z1):
            for y in range(EH_TOP_Y + 1, base):
                g[(x, y, z)] = 'minecraft:cobblestone' if x in (x0, x1) else 'minecraft:stone'
        if k >= 2 and (x - x0) % 6 == 3:
            for z in range(z0 + 4, z1 - 2, 8):
                g[(x, EH_TOP_Y + 1, z)] = G.state('light', level=15, waterlogged=False)


def add_window_lights(g):
    """A light block just inside every pane of the tall side windows and the entrance hall's far lancets, a few
    blocks apart, so the glass glows at night. Returns the windows as (side, x0, x1, y0, y1) runs."""
    last = SET_SIZE[2] - 1
    runs = []
    for side, glass_z, inner_z in (('n', 0, 1), ('s', last, last - 1)):
        cols = {}
        for (x, y, z), s in g.items():
            if z == glass_z and 'glass' in s and 5 <= x <= 84:
                cols.setdefault(x, []).append(y)
        xs = sorted(cols)
        start = None
        for i, x in enumerate(xs):
            if start is None:
                start = x
            if i + 1 == len(xs) or xs[i + 1] != x + 1:
                ys = [y for xx in range(start, x + 1) for y in cols[xx]]
                runs.append((side, start, x, min(ys), max(ys)))
                start = None
        for _, a, b, y0, y1 in [r for r in runs if r[0] == side]:
            for x in range(a, b + 1):
                for y in range(y0 + 2, y1 + 1, 4):
                    if (x, y, inner_z) not in g:
                        g[(x, y, inner_z)] = G.state('light', level=WINDOW_LIGHT, waterlogged=False)
    far = G.EH_X1 + 1
    for (x, y, z), s in list(g.items()):
        if x == G.EH_X1 + 2 and 'glass' in s and y % 4 == 1 and (far, y, z) not in g:
            g[(far, y, z)] = G.state('light', level=WINDOW_LIGHT, waterlogged=False)
    return runs


APSE_X = (0, 8)                         # the staff-end apse behind the High Table, local
APSE_Z = (11, 33)
APSE_CAP_Y = 42


def seal_apse(g):
    """Close the apse's stone cap. The memory opened the sky over the apse too, so restyle_staff_window cleared
    the window wall's inner course (x 1) right up through the cap, leaving holes at its edge that would let
    weather (and the re-skin's outside) into the hall. Returns the cells filled."""
    n = 0
    for x in range(APSE_X[0], APSE_X[1] + 1):
        for z in range(APSE_Z[0], APSE_Z[1] + 1):
            if (x, APSE_CAP_Y, z) not in g and (x, APSE_CAP_Y + 1, z) not in g:
                g[(x, APSE_CAP_Y, z)] = G.WALL
                n += 1
    return n


INSIDE_PROBE = (40, 5, G.AXIS_Z)        # a cell in the middle of the hall: must never be "outside"


def build_set():
    """The roofed, re-skinned set in its local frame, plus what the anchors need from it."""
    b = G.build(open_roof=False)
    g = b.g
    seal_apse(g)
    seen, outside = reskin(g)
    assert INSIDE_PROBE not in outside, 'the set is open to the outside: the re-skin would reach the hall'
    add_buttresses_and_parapet(g)
    add_entrance_hall_dress(g)
    add_apse_battlements(g)
    add_entrance_hall_roof(g)
    add_gable_oculi(g)
    add_ridge_fleches(g)
    windows = add_window_lights(g)
    return g, windows, seen


# ---------------------------------------------------------------- castle-frame steps

def remove_old_wing(grid):
    """Clear the old wing above its floor line, keeping the hill's own blocks. Returns the count removed."""
    gone = []
    for (x, y, z), s in grid.cells.items():
        if y < OLD_WING_MIN_Y or not in_old_wing(x, z):
            continue
        name = checks._id(s)
        if name in TERRAIN_NAMES and not (name == 'stone' and y >= OLD_FLOOR_Y):
            continue
        gone.append((x, y, z))
    for p in gone:
        del grid.cells[p]
    return len(gone)


def clear_columns(grid, columns, y0):
    """Remove everything at y >= y0 in the given columns."""
    gone = [p for p in grid.cells if p[1] >= y0 and (p[0], p[2]) in columns]
    for p in gone:
        del grid.cells[p]


def build_plinth(grid, columns):
    """Stone under every column from the floor down to whatever is already there (the old rock) or y 0."""
    n = 0
    for (x, z) in columns:
        y = MAIN_FLOOR_Y - 1
        while y >= 0 and (x, y, z) not in grid.cells:
            grid.cells[(x, y, z)] = 'minecraft:stone'
            n += 1
            y -= 1
    return n


def _value_noise(seed, step):
    rnd = random.Random(seed)
    knots = {}

    def knot(i, j):
        if (i, j) not in knots:
            knots[(i, j)] = rnd.uniform(-1.0, 1.0)
        return knots[(i, j)]

    def at(x, z):
        fx, fz = x / step, z / step
        i, j = math.floor(fx), math.floor(fz)
        tx, tz = fx - i, fz - j
        tx, tz = tx * tx * (3 - 2 * tx), tz * tz * (3 - 2 * tz)
        a = knot(i, j) * (1 - tx) + knot(i + 1, j) * tx
        c = knot(i, j + 1) * (1 - tx) + knot(i + 1, j + 1) * tx
        return a * (1 - tz) + c * tz
    return at


SKIRT_REACH = 24
SKIRT_FALL = 1.0                       # blocks down per block out: the hill's own flanks are about this steep


def build_skirt(grid, footprint, rect, east_only=False):
    """A rock skirt round the hall's north part, falling from the plinth to the ground, so the wing stands on
    the hill rather than on a stone box. Only raises ground, never touches a column the old castle built on,
    and is a shell (each column filled down to its lowest neighbour), as the old hill is: the castle's
    placement fills every column's underside with stone down to the real ground anyway."""
    x0, z0, x1, z1 = rect
    noise = _value_noise(1991, 7.0)
    tops, built = {}, set()
    for (x, y, z), s in grid.cells.items():
        if checks._id(s) in TERRAIN_NAMES:
            if y > tops.get((x, z), -1):
                tops[(x, z)] = y
        else:
            built.add((x, z))
    heights = {}
    for x in range(x0 - SKIRT_REACH, x1 + SKIRT_REACH + 1):
        for z in range(z0 - SKIRT_REACH, z1 + SKIRT_REACH + 1):
            if (x, z) in footprint or (x, z) in built or x < 0 or z < 0 or (east_only and x <= x1):
                continue
            dx = max(x0 - x, 0, x - x1)
            dz = max(z0 - z, 0, z - z1)
            d = math.hypot(dx, dz)
            if d <= 0 or d > SKIRT_REACH:
                continue
            h = int(round(MAIN_FLOOR_Y - SKIRT_FALL * d + 2.5 * noise(x, z)))
            h = min(h, MAIN_FLOOR_Y - 1)
            if h >= 1 and h > tops.get((x, z), -1):
                heights[(x, z)] = h

    def surface(c):
        if c in footprint:
            return MAIN_FLOOR_Y - 1
        return heights.get(c, tops.get(c, -1))

    rnd = random.Random(7)
    n = 0
    for (x, z), h in heights.items():
        nb = min(surface(c) for c in ((x + 1, z), (x - 1, z), (x, z + 1), (x, z - 1)))
        low = max(tops.get((x, z), -1) + 1, min(nb + 1, h - 1), 0)
        steep = h - nb >= 3
        r = rnd.random()
        if steep or r < 0.2:
            top = 'minecraft:stone'
        elif r < 0.75:
            top = 'minecraft:grass_block[snowy=false]'
        elif r < 0.92:
            top = 'minecraft:coarse_dirt'
        else:
            top = 'minecraft:gravel'
        for y in range(low, h + 1):
            if (x, y, z) in grid.cells:
                continue
            if y == h:
                s = top
            elif y == h - 1 and top.startswith('minecraft:grass'):
                s = 'minecraft:dirt'
            else:
                s = 'minecraft:stone'
            grid.cells[(x, y, z)] = s
            n += 1
    return n


def build_gallery(grid):
    """The covered gallery from the entrance hall's west door to the viaduct head, in the viaduct's own dress:
    a cobblestone deck with a stone-brick spine, an arcade of cobblestone piers with a wall between them, an
    oak lean-to roof against the entrance hall and wall torches on the hall's wall."""
    gx0, gx1 = GALLERY_X
    gz0, gz1 = GALLERY_Z
    y = MAIN_FLOOR_Y
    for x in range(gx0, gx1 + 1):
        for z in range(gz0, gz1 + 1):
            for yy in range(y + 1, y + 13):
                grid.clear(x, yy, z)
            grid.set(x, y, z, 'minecraft:stone_bricks' if x == (gx0 + gx1) // 2 else 'minecraft:cobblestone')
    for z in range(gz0, gz1 + 1):
        pier = (z - gz0) % GALLERY_PIER_STEP == 0 or z == gz1
        if pier:
            for yy in range(y + 1, y + 5):
                grid.set(gx0, yy, z, 'minecraft:cobblestone')
        else:
            grid.set(gx0, y + 1, z, G.state('cobblestone_wall', north='low', south='low', east='none', west='none',
                                            up=False, waterlogged=False))
        grid.set(gx0, y + 5, z, 'minecraft:cobblestone')                     # the arcade's lintel
        for i, x in enumerate(range(gx0, gx1 + 1)):                           # lean-to roof, 1:1 to the wall
            grid.set(x, y + 6 + i, z, 'minecraft:oak_planks')
            if i:
                grid.set(x, y + 5 + i, z, 'minecraft:oak_planks')
    for z in range(gz0 + 2, gz1, 8):
        grid.set(gx1, y + 3, z, G.state('wall_torch', facing='west'))
    # the door through the entrance hall's west wall (local z 38..39), 3 wide and 4 tall, with a cobblestone
    # surround on its outer face
    zs = [cell(x, 0, 0)[2] for x in DOOR_LOCAL_X]
    wall_x = (cell(0, 0, 39)[0], cell(0, 0, 38)[0])
    for wx in range(wall_x[0], wall_x[1] + 1):
        for z in range(zs[0], zs[1] + 1):
            for yy in range(y + 1, y + 5):
                grid.clear(wx, yy, z)
    for z in range(zs[0] - 1, zs[1] + 2):
        grid.set(wall_x[0], y + 5, z, 'minecraft:cobblestone')
    for z in (zs[0] - 1, zs[1] + 1):
        for yy in range(y + 1, y + 5):
            grid.set(wall_x[0], yy, z, 'minecraft:cobblestone')


def light_dark_floors(grid, boxes, rounds=8):
    """Add light blocks (level 12, two above the floor) until no walkable cell in the boxes is dark."""
    added = 0
    for _ in range(rounds):
        dark = checks.dark_floor_cells(grid, boxes)
        if not dark:
            break
        chosen = []
        for c in dark:
            if all(abs(c[0] - k[0]) + abs(c[1] - k[1]) + abs(c[2] - k[2]) > 6 for k in chosen):
                chosen.append(c)
        for x, y, z in chosen:
            for yy in (y + 2, y + 1, y):
                if (x, yy, z) not in grid.cells:
                    grid.set(x, yy, z, G.state('light', level=12, waterlogged=False))
                    added += 1
                    break
    return added


def _box(local_a, local_b):
    a, b = cell(*local_a), cell(*local_b)
    return tuple(min(a[i], b[i]) for i in range(3)) + tuple(max(a[i], b[i]) for i in range(3))


HALL_BOX = _box((1, 1, 4), (83, G.CEIL_Y - 1, 40))                     # the hall's floor to the ceiling plane
ENTRANCE_BOX = _box((G.EH_X0, 1, G.EH_Z0), (G.EH_X1, 20, G.EH_Z1))
GALLERY_BOX = (GALLERY_X[0], MAIN_FLOOR_Y + 1, GALLERY_Z[0], GALLERY_X[1], MAIN_FLOOR_Y + 4, GALLERY_Z[1])

_COLUMNS = None


def _set_columns(g):
    """(base, covered): the castle columns the set stands on (everything but the entrance hall roof's eaves,
    which overhang its walls) plus the gallery, and every column it covers at any height."""
    lowest = {}
    for (x, y, z) in g:
        c = cell(x, 0, z)[0::2]
        lowest[c] = min(y, lowest.get(c, y))
    covered = set(lowest)
    base = {c for c, y in lowest.items() if y < EH_TOP_Y}      # not the roof eaves that overhang the walls
    gallery = {(x, z) for x in range(GALLERY_X[0], GALLERY_X[1] + 1) for z in range(GALLERY_Z[0], GALLERY_Z[1] + 1)}
    return base | gallery, covered | gallery


def _columns():
    global _COLUMNS
    if _COLUMNS is None:
        _COLUMNS = _set_columns(build_set()[0])
    return _COLUMNS


def footprint_columns():
    """Every castle column the wing stands on (and so has a plinth under): the set's floor and the gallery."""
    return _columns()[0]


def covered_columns():
    """Every castle column the wing occupies at any height."""
    return _columns()[1]


def _candles():
    """The memory's floating_candles events, scene -> local -> castle (spread axes swap with the turn)."""
    import json
    path = os.path.join(os.path.dirname(_PENSIEVE), '..', 'src', 'main', 'resources', 'data', 'hogwartsmagic',
                        'memories', 'sorting_hall.json')
    with open(os.path.normpath(path), encoding='utf-8') as fh:
        scene = json.load(fh)
    out = []
    t = G.TEMPLATE_AT
    for e in scene['events']:
        if e.get('kind') == 'effect' and e.get('id') == 'floating_candles':
            (ax, ay, az), (hx, hy, hz) = e['at'], e['spread']
            at = point(ax - t[0], ay - t[1], az - t[2])
            out.append({'at': [round(v, 3) for v in at], 'spread': [hz, hy, hx], 'count': e['count']})
    return out


def _rays(windows):
    """Daylight shafts through every tall side window, each from its inner face into the hall."""
    out = []
    last = SET_SIZE[2] - 1
    for side, a, b, y0, y1 in windows:
        cx = (a + b + 1) / 2.0
        if side == 'n':
            at, local_yaw = point(cx, y0, 1.0), 0
        else:
            at, local_yaw = point(cx, y0, float(last)), 180
        out.append({'at': at, 'yaw': yaw(local_yaw), 'spread': [(b - a + 1) / 2.0, y1 - y0 + 1, 14]})
    return out


def _chest(grid):
    """The Great Hall chest: on the floor of a window bay by the east wall near the doors, facing the hall."""
    for lx in (79, 71, 63, 55, 31, 23, 15):
        for lz in (5, 39):
            c = cell(lx, 1, lz)
            if (c in grid.cells or (c[0], c[1] + 1, c[2]) in grid.cells
                    or not checks.supports(grid.get(c[0], c[1] - 1, c[2]))):
                continue
            facing = 'west' if lz < 22 else 'east'          # local south / north, turned: into the hall
            return {'at': list(c), 'facing': facing, 'theme': 'GREAT_HALL'}
    raise AssertionError('no free floor tile for the Great Hall chest')


# The memory's ceiling plane starts at x 1, over the staff-end apse and the two open corners beside it, which
# is harmless under an open sky. In the castle the plane is drawn in the world, and beside the apse it would hang
# outside the walls with no roof over it, so here it starts at the hall's end wall face (x 10, where the house
# banners hang) and the apse keeps its own stone vault.
CEIL_X = (10.0, G.CEIL_X[1])


def _ceiling(grid):
    c0 = point(CEIL_X[0], G.CEIL_Y, G.CEIL_Z[1])
    c1 = point(CEIL_X[1], G.CEIL_Y, G.CEIL_Z[0])
    y = c0[1]
    lowest, highest = None, None
    for x in range(int(c0[0]), int(c1[0])):
        for z in range(int(c0[2]), int(c1[2])):
            above = [yy for yy in range(y, y + SET_SIZE[1]) if (x, yy, z) in grid.cells]
            if above:
                lowest = above[0] if lowest is None else min(lowest, above[0])
                highest = above[0] if highest is None else max(highest, above[0])
    return {'min': [c0[0], c0[2]], 'max': [c1[0], c1[2]], 'y': y,
            'clearance': (lowest - y) if lowest is not None else 0,
            'ridge_clearance': (highest - y) if highest is not None else 0}


def place_hall(grid):
    """Swap the Great Hall wing into the castle grid. Returns its anchors (castle coordinates)."""
    global _COLUMNS
    removed = remove_old_wing(grid)
    g, windows, seen = build_set()
    _COLUMNS = base, covered = _set_columns(g)

    clear_columns(grid, covered, MAIN_FLOOR_Y)
    paste(grid, g, ORIGIN, TURNS, SET_SIZE)
    build_gallery(grid)
    plinth = build_plinth(grid, base)
    hall_end_z = cell(84, 0, 0)[2]                     # the skirt hugs the hall proper, north of the entrance hall
    xs = [c[0] for c in base if c[1] <= hall_end_z]
    zs = [c[1] for c in base if c[1] <= hall_end_z]
    skirt = build_skirt(grid, covered, (min(xs), min(zs), max(xs), max(zs)))
    # and down the entrance hall's east side (the west side is the gallery and the viaduct's valley)
    east = [c for c in base if c[1] > hall_end_z]
    ex0 = max(c[0] for c in east)
    skirt += build_skirt(grid, covered, (ex0, min(c[1] for c in east), ex0, max(c[1] for c in east)), east_only=True)
    lights = light_dark_floors(grid, [HALL_BOX, ENTRANCE_BOX, GALLERY_BOX])
    place_hall.stats = {'removed': removed, 'plinth': plinth, 'skirt': skirt, 'floor_lights': lights,
                        'outer_faces': dict(seen), 'windows': len(windows)}

    doors = [{'hinge': point(*h), 'yaw_closed': yaw(-90), 'mirrored': i == 1}
             for i, h in enumerate(G.DOOR_HINGES)]
    return {
        'hall_ceiling': _ceiling(grid),
        'candles': _candles(),
        'rays': _rays(windows),
        'doors': doors,
        'hall_box': {'min': list(HALL_BOX[:3]), 'max': list(HALL_BOX[3:])},
        'main_entrance': list(MAIN_ENTRANCE),
        'stool': point(G.STOOL[0] + 0.5, G.STOOL[1] + 0.5, G.STOOL[2] + 0.5),
        'chests': [_chest(grid)],
    }
