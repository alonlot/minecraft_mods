"""The above-ground finishing pass: the corridors dressed, the castle's guardians at their posts, light in every
window.

    from mini_hogwarts import rooms_corridors
    anchors = rooms_corridors.place_corridors(grid, interiors)   # castle frame, after every other room; interiors:
                                                                 # build_castle.collect_rooms(grid).values()

WHERE THE WAY WAS BARE OR DARK
------------------------------
Above ground the castle is one walk: the gatehouse gate, the viaduct north to its head (a terrace under the turret
and the central tower), the covered gallery up the Great Hall wing's west side to the entrance hall's west door, the
entrance hall (the Grand Staircase behind it, the Great Hall's portal in front). Every room on it is lit and furnished
by its own task; the two stretches between rooms were not:

    the gallery       a covered arcade 4 wide and 27 long (x 40..43, z 111..137), cobblestone piers every 4 with a
                      low wall between them on the west, the entrance hall's bare stone wall on the east, three wall
                      torches. It becomes the castle's guardian corridor: four statue bays cut 1 deep into the east
                      wall, each opposite an arch of the arcade (so every opening frames a knight), a chiseled plinth
                      under each statue and upside-down stair shoulders under the lintel; a house banner on every
                      strip of wall between the bays (red, yellow, blue, green, red: the four houses in order down the
                      corridor); a red runner two wide down its middle; lanterns hung on chains from the lean-to roof
                      every 5 blocks in place of the two torches the bays replace. The wall behind the bays is the
                      entrance hall's (2 thick there, the imperial staircase's solid flank behind it), so the bays
                      never show inside.
    the viaduct head  an open terrace where the viaduct arrives, dark at night between the bridge's torch posts. Two
                      lamp posts (stone-brick post, wall shaft, lantern) flank the way from the bridge to the gallery's
                      mouth; the bridge's last gaps between its torch posts get invisible light blocks (base light,
                      hall.light_dark_floors), the bridge's own torches staying its visible light.

THE GUARDIANS (the shared "statues" anchor list; dormant castle statues, Task 10's guardCastle)
-------------------------------------------------------------------------------------------
    4 in the gallery bays, facing west across the corridor into the arcade's openings
    2 flanking the Great Hall's portal on the entrance-hall side, between each door jamb and the polished-andesite
      column beside it, facing south into the entrance hall (the leaves' hinges are the door anchors)
    2 at the gatehouse gate, inside the vestibule just behind the folded-back leaves, facing north at whoever arrives
      over the viaduct; the two wall torches that hung at their heads become one lantern hung in the arch slot over
      the runner between them (the old castle's torches: CHANGED_ON_PURPOSE)
Each stands on a free floor cell with three cells of headroom, and none blocks a way: build_castle's check 44 walls
every statue up as a three-tall column and re-walks the castle; nothing walked before is lost but the statues' own
cells.

LIGHT IN EVERY WINDOW
---------------------
The castle glows at night: every outer face of glass (a glass block or pane with outdoor air on one side and the
castle's interior air on the other: an enclosed pocket or a room box) that has no light source within 2 of the cell
behind it gets an invisible light block (level 13) there, or in the cell above, below or one further in when that one
is taken. Never in a cell an anchor needs free (a chest, a student spot, a statue's column, the Pensieve, the Fat
Lady's canvas), never in a floating-candle box (check 7 holds the staircase's void empty), never in Potions (ruling
R42: untouched), never below MAIN_FLOOR_Y.

Also: "title_point", the castle's visual centre for the title-card distance check (the central tower's axis at half
its height), and four student spots (two in the gallery, two in the entrance hall).

Nothing here is written below MAIN_FLOOR_Y or into the castle's rock (R42); _put enforces both and records every cell
it writes, and place_corridors compares everything below MAIN_FLOOR_Y before and after (check 45).
"""

import math

from . import checks, hall, rooms_classrooms, rooms_staircase
from .frame import MAIN_FLOOR_Y
from .grid import air_at, enclosed_air
from .hall import G

C = rooms_classrooms

FEET = MAIN_FLOOR_Y + 1                 # 25: the gallery's, the viaduct's and the entrance hall's feet

# ---------------------------------------------------------------- the gallery (hall.build_gallery's arcade)
GALLERY_X = hall.GALLERY_X              # (39, 43): the arcade's piers at x 39, the walk x 40..43
GALLERY_Z = hall.GALLERY_Z              # (111, 137)
WALL_X = GALLERY_X[1] + 1               # 44: the entrance hall's west wall, the gallery's east side
BACK_X = WALL_X + 1                     # 45: the wall's inner course (and the imperial staircase's flank behind it)
SILL_Y = FEET                           # the wall's cobblestone base course: each statue's plinth
BAY_FEET = SILL_Y + 1                   # 26
# one bay opposite every arch of the arcade (piers every GALLERY_PIER_STEP from GALLERY_Z[0], so an arch's middle is
# 2 past a pier) from the west door's jamb on (the door is WEST_DOOR_Z, its cobblestone jamb one past it)
BAY_ZS = tuple(z for z in range(GALLERY_Z[0] + 2, GALLERY_Z[1] - 2, hall.GALLERY_PIER_STEP)
               if z - 1 > hall.WEST_DOOR_Z[1] + 1)                          # (121, 125, 129, 133)
BANNER_Y = FEET + 4                     # 29: beside the keystones, its flag clear of every head (it hangs ~1.7 down)
BANNERS = tuple(zip([BAY_ZS[0] - 2] + [z + 2 for z in BAY_ZS],
                    ('red', 'yellow', 'blue', 'green', 'red')))             # the strips of wall between the bays
LANTERN_X = GALLERY_X[0] + 2            # 41: the walk's middle, under the lean-to roof
LANTERN_Y = FEET + 3                    # 28: over every head
LANTERN_ZS = tuple(range(GALLERY_Z[0] + 1, GALLERY_Z[1] + 1, 5))           # (112, 117, 122, 127, 132, 137)
RUNNER_X = (GALLERY_X[0] + 2, GALLERY_X[0] + 3)                            # x 41..42

# ---------------------------------------------------------------- the gate (rooms_classrooms' vestibule)
GATE_STATUES = tuple((x, C.GROUND_FEET, C.VESTIBULE_Z[0]) for x in C.GATE_X)  # (37|39, 26, 186), behind the leaves
GATE_LANTERN = ((C.GATE_X[0] + C.GATE_X[1]) // 2, C.GROUND_FEET + 3, C.VESTIBULE_Z[0])   # (38, 29, 186)
GATE_TORCHES = tuple((x, C.GROUND_FEET + 1, z) for x, _, z in GATE_STATUES)              # the old castle's

# ---------------------------------------------------------------- the viaduct head
PLAZA_LAMPS = ((38, 142), (45, 142))    # either side of the way from the bridge (x 40..42) to the gallery's mouth
VIADUCT_BOX = (31, FEET, 138, 49, FEET + 2, 183)   # the terrace and the bridge, the gate's steps (for the light)

# ---------------------------------------------------------------- the anchors
GALLERY_SPOTS = ((40, FEET, 126), (43, FEET, 118))
ENTRANCE_SPOTS = ((50, FEET, 116), (72, FEET, 116))
WINDOW_LIGHT = 13
EXCLUDED = (C.POTIONS_BOX,)             # windows left as they are (R42: Potions is not touched)

YAW = {'south': 0, 'west': 90, 'north': 180, 'east': -90}


def room_boxes():
    """The interiors this pass opens (build_castle.ROOMS, so their air is sealed): the four gallery bays."""
    return {'gallery bay %d' % i: (WALL_X, BAY_FEET, z - 1, WALL_X, BAY_FEET + 2, z + 1)
            for i, z in enumerate(BAY_ZS)}


def changed_boxes():
    """The old castle's blocks this pass changes (build_castle.CHANGED_ON_PURPOSE): the vestibule's two wall torches at
    the gate statues' heads. Everything else it writes is in air or in the Great Hall wing's own columns."""
    return [('the gate statues\' heads: the vestibule\'s wall torch (%d, %d, %d), now a lantern in the arch' % t,
             t + t) for t in GATE_TORCHES]


def light_boxes():
    """Every box whose floor must not be dark after this pass: the gallery and its bays, the vestibule (its torches
    moved), the entrance hall (statues at its portal) and the viaduct head with the bridge."""
    return ([hall.GALLERY_BOX] + list(room_boxes().values())
            + [C.ROOMS_BY_NAME['gatehouse vestibule'], hall.ENTRANCE_BOX, VIADUCT_BOX])


def _in(p, box):
    return box[0] <= p[0] <= box[3] and box[1] <= p[1] <= box[4] and box[2] <= p[2] <= box[5]


def _put(grid, x, y, z, state, carve=False):
    """Write one cell, above the main floor only (R42), and remember it in WRITTEN. Writing over a block is a carve and
    must say so: a carve of a solid block (the bays) is only allowed in the Great Hall wing's own columns, never in the
    castle's rock or the old castle's masonry."""
    assert y > MAIN_FLOOR_Y, ('a corridor write at or below the main floor', (x, y, z), state)
    old = grid.get(x, y, z)
    if not grid.is_air(x, y, z):
        assert carve, ('an unannounced write over a block', (x, y, z), old, state)
        assert checks.passable(old) or (x, z) in hall.footprint_columns(), ('a carve outside the hall wing',
                                                                             (x, y, z), old)
    grid.set(x, y, z, state)
    WRITTEN[(x, y, z)] = state


WRITTEN = {}


# ---------------------------------------------------------------- the gallery

def build_bays(grid):
    """Cut the four statue bays into the gallery's east wall. Each is 3 wide and 1 deep: the sill (the wall's base
    course) becomes a stone-brick plinth with a chiseled middle, the two cells above it are opened, and under the
    lintel the shoulders are upside-down stairs round the open middle (an arch over the statue's head). The wall
    behind (BACK_X) must be solid from the sill to the lintel, so the entrance hall never sees a bay. Returns the
    statues' cells."""
    out = []
    for c in BAY_ZS:
        for z in (c - 1, c, c + 1):
            assert checks.supports(grid.get(WALL_X, SILL_Y, z)), ('no sill under the bay', (WALL_X, SILL_Y, z))
            for y in range(SILL_Y, BAY_FEET + 4):
                assert checks.supports(grid.get(BACK_X, y, z)), ('the bay would open into the entrance hall',
                                                                  (BACK_X, y, z), grid.get(BACK_X, y, z))
            assert checks.supports(grid.get(WALL_X, BAY_FEET + 3, z)), ('no lintel over the bay', (WALL_X, z))
            _put(grid, WALL_X, SILL_Y, z, 'minecraft:chiseled_stone_bricks' if z == c else 'minecraft:stone_bricks',
                 carve=True)
            for y in (BAY_FEET, BAY_FEET + 1):
                _put(grid, WALL_X, y, z, 'minecraft:air', carve=True)
        _put(grid, WALL_X, BAY_FEET + 2, c, 'minecraft:air', carve=True)
        _put(grid, WALL_X, BAY_FEET + 2, c - 1, G.state('stone_brick_stairs', facing='north', half='top',
                                                        shape='straight', waterlogged=False), carve=True)
        _put(grid, WALL_X, BAY_FEET + 2, c + 1, G.state('stone_brick_stairs', facing='south', half='top',
                                                        shape='straight', waterlogged=False), carve=True)
        _put(grid, WALL_X, BAY_FEET + 3, c, 'minecraft:chiseled_stone_bricks', carve=True)       # the keystone
        # the wall torch that hung where the bay now opens (hall.build_gallery's, every 8 from the second cell)
        t = (WALL_X - 1, FEET + 2, c)
        if checks.block_id(grid.get(*t)) == 'wall_torch':
            _put(grid, *t, 'minecraft:air', carve=True)
        out.append((WALL_X, BAY_FEET, c))
    return out


def dress_gallery(grid):
    """The runner, the banners and the lanterns. Returns (runner cells, banners, lanterns)."""
    runner = 0
    for z in range(GALLERY_Z[0], GALLERY_Z[1] + 1):
        for x in RUNNER_X:
            if grid.is_air(x, FEET, z) and checks.supports(grid.get(x, FEET - 1, z)):
                _put(grid, x, FEET, z, 'minecraft:red_carpet')
                runner += 1
    for z, colour in BANNERS:
        assert checks.supports(grid.get(WALL_X, BANNER_Y, z)) and grid.is_air(WALL_X - 1, BANNER_Y, z), \
            ('no wall for the banner', z)
        _put(grid, WALL_X - 1, BANNER_Y, z, G.state('%s_wall_banner' % colour, facing='west'))
    lanterns = 0
    for z in LANTERN_ZS:
        top = LANTERN_Y + 1
        while grid.is_air(LANTERN_X, top, z):
            top += 1
        assert top <= LANTERN_Y + 6 and checks.supports(grid.get(LANTERN_X, top, z)), ('no roof over the lantern', z)
        for y in range(LANTERN_Y + 1, top):
            _put(grid, LANTERN_X, y, z, G.state('chain', axis='y', waterlogged=False))
        assert grid.is_air(LANTERN_X, LANTERN_Y, z), ('the lantern\'s cell is taken', z)
        _put(grid, LANTERN_X, LANTERN_Y, z, G.state('lantern', hanging=True, waterlogged=False))
        lanterns += 1
    return runner, len(BANNERS), lanterns


# ---------------------------------------------------------------- the gate and the portal

def guard_gate(grid):
    """The two gate statues' cells cleared of the old castle's wall torches, and a lantern hung in the arch slot over
    the runner between them (from the gate's cobblestone transom). Returns the statues' cells."""
    for t in GATE_TORCHES:
        assert checks.block_id(grid.get(*t)) == 'wall_torch', ('the gate torch moved', t, grid.get(*t))
        _put(grid, *t, 'minecraft:air', carve=True)
    lx, ly, lz = GATE_LANTERN
    assert grid.is_air(lx, ly, lz) and checks.block_id(grid.get(lx, ly + 1, lz)) == 'cobblestone', \
        ('nothing to hang the gate lantern from', GATE_LANTERN)
    _put(grid, lx, ly, lz, G.state('lantern', hanging=True, waterlogged=False))
    return list(GATE_STATUES)


def portal_statues(grid):
    """The pair flanking the Great Hall's portal on the entrance-hall side: one cell outside each door leaf's hinge
    (the door anchors), two cells into the entrance hall (the portal wall is two thick)."""
    hinges = sorted(d['hinge'] for d in grid.anchors['doors'])
    z = int(math.floor(hinges[0][2])) + 2
    xs = (int(math.floor(hinges[0][0])) - 1, int(math.floor(hinges[-1][0])) + 1)
    return [(x, FEET, z) for x in xs]


# ---------------------------------------------------------------- the viaduct head

def light_viaduct(grid):
    """The two lamp posts on the terrace (stone brick, not cobblestone: the terrace is in the old wing's outline, where
    check 1 counts cobblestone as the old wing left standing), then base light (invisible) wherever the terrace or the
    bridge is still dark. Returns (lamps, light blocks)."""
    for x, z in PLAZA_LAMPS:
        assert checks.supports(grid.get(x, FEET - 1, z)), ('no terrace under the lamp', (x, z))
        for y in range(FEET, FEET + 3):
            assert grid.is_air(x, y, z), ('the lamp post\'s cell is taken', (x, y, z))
        _put(grid, x, FEET, z, 'minecraft:stone_bricks')
        _put(grid, x, FEET + 1, z, G.state('stone_brick_wall', east='none', north='none', south='none', west='none',
                                           up=True, waterlogged=False))
        _put(grid, x, FEET + 2, z, G.state('lantern', hanging=False, waterlogged=False))
    before = set(grid.cells)
    n = hall.light_dark_floors(grid, [VIADUCT_BOX])
    for p in set(grid.cells) - before:
        assert p[1] >= MAIN_FLOOR_Y, p
        WRITTEN[p] = grid.cells[p]
    return len(PLAZA_LAMPS), n


# ---------------------------------------------------------------- windows

def _is_glass(state):
    return 'glass' in checks.block_id(state)


def window_faces(cells, interior):
    """Every outer face of glass: (glass cell, the interior cell behind it, the step from inside out) for each glass
    block or pane with outdoor air (absent, and not interior) on one horizontal side and an interior cell (interior(p))
    on the opposite one. Windows in an EXCLUDED box are left out."""
    out = []
    for (x, y, z), s in cells.items():
        if not _is_glass(s) or any(_in((x, y, z), b) for b in EXCLUDED):
            continue
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            o, i = (x + dx, y, z + dz), (x - dx, y, z - dz)
            if o not in cells and not interior(o) and interior(i):
                out.append(((x, y, z), i, (dx, dz)))
                break
    return sorted(out)


def _near(p, lights, r=2):
    x, y, z = p
    return any((x + dx, y + dy, z + dz) in lights
               for dx in range(-r, r + 1) for dy in range(-r, r + 1) for dz in range(-r, r + 1)
               if abs(dx) + abs(dy) + abs(dz) <= r)


def unlit_windows(cells, interior):
    """The outer faces of glass (window_faces) with no light source within 2 of the cell behind them."""
    lights = {p for p, s in cells.items() if checks.is_light(s)}
    return [f for f in window_faces(cells, interior) if not _near(f[1], lights)]


def reserved_cells(anchors):
    """Cells the anchors need free (air): chests, student spots and their head cells, statues' columns, the Pensieve
    and the cell over it, the Fat Lady's canvas."""
    out = set()
    for c in anchors.get('chests', ()):
        out.add(tuple(c['at']))
    for s in anchors.get('student_spots', ()):
        out.update({tuple(s), (s[0], s[1] + 1, s[2])})
    for s in anchors.get('statues', ()):
        x, y, z = s['at']
        out.update((x, y + dy, z) for dy in range(3))
    if anchors.get('pensieve'):
        x, y, z = anchors['pensieve']
        out.update({(x, y, z), (x, y + 1, z)})
    if anchors.get('fat_lady'):
        out.update(rooms_staircase.FAT_LADY_CELLS)
    return out


def _in_candles(p, candles):
    return any(abs(p[0] + 0.5 - c['at'][0]) <= c['spread'][0] + 1 and abs(p[1] + 0.5 - c['at'][1]) <= c['spread'][1] + 1
               and abs(p[2] + 0.5 - c['at'][2]) <= c['spread'][2] + 1 for c in candles)


def light_windows(grid, interiors, extra_anchors):
    """Light every unlit outer window from inside (see LIGHT IN EVERY WINDOW). interiors: the room boxes;
    extra_anchors: this pass's own statues and student spots (not in grid.anchors yet), whose cells stay free too.
    Returns (outer faces, light blocks placed, faces that found no free cell)."""
    cells = grid.cells
    inside = enclosed_air(grid)
    for x0, y0, z0, x1, y1, z1 in interiors:
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    if air_at(cells, (x, y, z)):
                        inside.add((x, y, z))

    def interior(p):
        return p in inside

    reserved = reserved_cells(grid.anchors) | reserved_cells(extra_anchors)
    candles = grid.anchors.get('candles', ())
    lights = {p for p, s in cells.items() if checks.is_light(s)}
    faces = window_faces(cells, interior)
    placed = 0
    stuck = []
    for g, i, (dx, dz) in faces:
        if _near(i, lights):
            continue
        for p in (i, (i[0], i[1] + 1, i[2]), (i[0], i[1] - 1, i[2]), (i[0] - dx, i[1], i[2] - dz)):
            if (p in inside and air_at(cells, p) and p not in reserved and p[1] > MAIN_FLOOR_Y
                    and not _in_candles(p, candles) and not any(_in(p, b) for b in EXCLUDED) and _near(i, {p})):
                _put(grid, *p, G.state('light', level=WINDOW_LIGHT, waterlogged=False))
                lights.add(p)
                placed += 1
                break
        else:
            stuck.append(g)
    return len(faces), placed, stuck


def sealed_interior(cells):
    """For a finished (sealed) castle: a cell is interior air when it is cave_air, or a light block (the window and
    floor lights stand in what was interior air)."""
    def interior(p):
        s = cells.get(p)
        return s is not None and (s == 'minecraft:cave_air' or checks.block_id(s) == 'light')
    return interior


# ---------------------------------------------------------------- the title point

def title_point(cells):
    """The castle's visual centre for the title card: the central tower's axis at half its height above the main
    floor (its top is the highest block on the four columns round its axis)."""
    cx, cz = hall.OLD_TOWER[0], hall.OLD_TOWER[1] + hall.BASE_SHIFT[2]          # (61.5, 148.5)
    cols = {(int(cx - 0.5), int(cz - 0.5)), (int(cx + 0.5), int(cz - 0.5)), (int(cx - 0.5), int(cz + 0.5)),
            (int(cx + 0.5), int(cz + 0.5))}
    top = max(y for (x, y, z), s in cells.items() if (x, z) in cols and not air_at(cells, (x, y, z)))
    return [int(cx - 0.5), (MAIN_FLOOR_Y + top) // 2, int(cz - 0.5)], top


# ---------------------------------------------------------------- the pass

def _below(cells):
    return {p: s for p, s in cells.items() if p[1] < MAIN_FLOOR_Y}


def place_corridors(grid, interiors):
    """The finishing pass (see the module docstring). interiors: every room box (build_castle.collect_rooms), this
    module's own bays included. Returns the anchors: 'statues' (to append), 'student_spots' (to append) and
    'title_point'."""
    WRITTEN.clear()
    below = _below(grid.cells)
    bays = build_bays(grid)
    runner, banners, lanterns = dress_gallery(grid)
    gate = guard_gate(grid)
    portal = portal_statues(grid)
    lamps, viaduct_lights = light_viaduct(grid)
    statues = ([{'at': list(p), 'yaw': YAW['west']} for p in bays]
               + [{'at': list(p), 'yaw': YAW['south']} for p in portal]
               + [{'at': list(p), 'yaw': YAW['north']} for p in gate])
    spots = [list(p) for p in GALLERY_SPOTS + ENTRANCE_SPOTS]
    # the windows last, with this pass's statues and spots kept free as well
    faces, window_lights, stuck = light_windows(grid, interiors, {'statues': statues, 'student_spots': spots})
    point, top = title_point(grid.cells)
    place_corridors.below_changed = sorted(set(below.items()) ^ set(_below(grid.cells).items()))
    place_corridors.stats = {'bays': len(bays), 'runner': runner, 'banners': banners, 'lanterns': lanterns,
                             'lamps': lamps, 'viaduct_lights': viaduct_lights, 'window_faces': faces,
                             'window_lights': window_lights, 'windows_stuck': len(stuck), 'tower_top': top,
                             'written': len(WRITTEN)}
    return {'statues': statues, 'student_spots': spots, 'title_point': point}
