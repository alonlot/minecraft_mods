"""Builds the new Mini Hogwarts schematic and its anchors sidecar from the old castle plus the rooms.

    python -I build_scripts/mini_hogwarts/build_castle.py

Input:  source/mini_hogwarts_v1.schem (the castle as it shipped; never the output file, so a rerun
        always starts from the same castle).
Output: src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem (Sponge v3, read by
        MiniHogwartsSchematic.java), mini_hogwarts.anchors.json beside it (every spot the Java side
        needs: effects, doors, chests...), and build/mh/mini_hogwarts.nbt, a structure-template copy
        for the renderers (schem_to_nbt.py).

THE CASTLE FRAME
----------------
The old castle's cells move by BASE_SHIFT into the new frame (x east, y up, z south, everything
>= 0). The shift makes room in the north: the Great Hall wing is 114 blocks long and stands on the
castle's axis with its staff end north, so it reaches well past the old castle's north edge, and the
rock skirt it stands on reaches further still. MAIN_FLOOR_Y is the floor block layer of the castle's
main level (the gatehouse and the viaduct deck); players walk at MAIN_FLOOR_Y + 1. Both live in
frame.py (so the room modules need not import this one) and are re-exported here.

The castle's arrival door is the gatehouse gate at the viaduct's south end: MAIN_ENTRANCE is the cell
in its arch, and every room must be walkable from it.

Rooms change the old castle only where they stand (up to their own roofline) or where
CHANGED_ON_PURPOSE says; verify() holds every other old block to its original state.

Each room is one function, called in order by ROOM_STEPS; each stamps its blocks into the grid and
adds its anchors. verify() asserts what the rooms promise (reachability, light, structure) and runs
before anything is written, so a broken castle never reaches the resources.

INTERIOR AIR
------------
The castle is placed as a worldgen structure, and the placer skips plain air ("leave the world alone"),
so terrain and trees standing where a room is would stay in it. After the rooms, seal_interiors() writes
minecraft:cave_air (which the placer does place) into every air cell of every room interior and of every
doorway between them (ROOMS, a name -> box dict collected from the room modules: their boxes reach out
through doors and windows) and into every air pocket the building closes in (grid.enclosed_air). A pocket
closed in by solid rock alone (the hill, the plinth) is filled with stone instead (ruling R35), so the
world gets no dark cavities. Outdoor air stays plain air.
"""

import json
import math
import os
import subprocess
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_BUILD_SCRIPTS = os.path.dirname(_HERE)
if _BUILD_SCRIPTS not in sys.path:
    sys.path.insert(0, _BUILD_SCRIPTS)

from mini_hogwarts.frame import BASE_SHIFT, MAIN_FLOOR_Y  # noqa: E402,F401  (re-exported for the room tasks)

REPO = os.path.dirname(_BUILD_SCRIPTS)
SOURCE = os.path.join(_HERE, 'source', 'mini_hogwarts_v1.schem')
SCHEM_DIR = os.path.join(REPO, 'src', 'main', 'resources', 'data', 'hogwartsmagic', 'schematic')
OUT_SCHEM = os.path.join(SCHEM_DIR, 'mini_hogwarts.schem')
OUT_ANCHORS = os.path.join(SCHEM_DIR, 'mini_hogwarts.anchors.json')
VIEWER_NBT = os.path.join(REPO, 'build', 'mh', 'mini_hogwarts.nbt')

from mini_hogwarts import (checks, hall, rooms_below, rooms_classrooms, rooms_library, rooms_staircase,  # noqa: E402
                           rooms_towers)
from mini_hogwarts import rooms_corridors  # noqa: E402
from mini_hogwarts.grid import (Grid, air_at, enclosed_air, is_air, load_schem, seal_interiors as _seal,  # noqa: E402
                                write_schem)
import schem_to_nbt  # noqa: E402  (decode_schem: check 62 reads the written stream back as the Java does)


# The gatehouse gate (old frame x 37..39, z 101, a closed oak-log pair of leaves under a pointed arch at
# z 100) is where the viaduct arrives and where a visitor walks in; this is the floor cell in the arch.
MAIN_ENTRANCE = (38 + BASE_SHIFT[0], MAIN_FLOOR_Y + 2, 100 + BASE_SHIFT[2])

# Old-castle blocks a room may change outside the old wing's outline and above the room's own roofline.
# Each entry is (name, inclusive castle box (x0, y0, z0, x1, y1, z1)). Room tasks append to it, with a
# reason in the name; anything not listed here must stay exactly as the old castle had it.
CHANGED_ON_PURPOSE = [
    # the Grand Staircase (rooms_staircase): the well inside the central tower's ring, one box per row of its round
    # plan, the inner course of the ring where frames replace it, the cone's pole and the entry door through the
    # ring's north face (against the entrance hall); never the ring's outside
    *rooms_staircase.changed_boxes(),
    # the Library (rooms_library), carved into the hollow hill under the gatehouse: the hall and the Restricted
    # Section with their shelving walls, floor and ceiling, and the tunnel from the grounds door through the hill's
    # north face (the only old blocks it changes: the hill's shell); the viaduct's last pier east of it is in no box
    *rooms_library.changed_boxes(),
    # the tower tops (rooms_towers): the Gryffindor tower's jettied storeys and new cone (ruling R26 lets that tower's
    # top change shape: these two boxes are its old top, the bridge's last two cells included); the Astronomy gallery's
    # floor over the central tower's band (the plain ring's top course, inside the band's wall) and the four arches
    # opened through the band's wall in place of its cardinal windows (holes in the wall; the outline is unchanged)
    *rooms_towers.changed_boxes(),
    # the classrooms (rooms_classrooms): the gatehouse gate's leaves opened (R18), the vestibule's bench and side windows
    # and the doorway through its south wall, the library stair's slot in the stair hall's floor (R31), Charms in the
    # ground floor's east room, Transfiguration in the upper floor's labelled room and the room east of it, and that
    # room's door from the upper landing; Defence and the stair below the floor are carved into the hill's hollow
    *rooms_classrooms.changed_boxes(),
    # below the castle (rooms_below): the stair from the entrance hall, the kitchens, the dungeon corridor and its cells,
    # the Slytherin common room, each box with its walls, all carved into the Great Hall's plinth (new stone: they change
    # no old-castle cell today; check 44 holds them inside the plinth)
    *rooms_below.changed_boxes(),
    # the above-ground finishing pass (rooms_corridors): the vestibule's two wall torches at the gate statues' heads
    *rooms_corridors.changed_boxes(),
]

# Every room interior, name -> inclusive box, filled by collect_rooms() once the rooms are built (some boxes are
# measured from the finished grid). seal_interiors() writes cave_air into their air; verify() checks it did.
ROOMS = {}


def load_base():
    """The old castle, moved into the castle frame by BASE_SHIFT."""
    old = load_schem(SOURCE)
    sx, sy, sz = BASE_SHIFT
    base = Grid()
    base.cells = {(x + sx, y + sy, z + sz): s for (x, y, z), s in old.cells.items()}
    return base


# ---------------------------------------------------------------- rooms, in order

def room_great_hall(grid):
    """The Sorting memory's Great Hall and entrance hall, swapped in for the old diagonal wing."""
    grid.anchors.update(hall.place_hall(grid))


def room_grand_staircase(grid):
    """The central tower's well: flights round the wall, landings at every floor, the Fat Lady's door."""
    found = rooms_staircase.place_staircase(grid)
    a = grid.anchors
    a.setdefault('candles', []).extend(found['candles'])
    a.setdefault('statues', []).extend(found['statues'])
    a.setdefault('paintings', []).extend(found['paintings'])
    a['fat_lady'] = found['fat_lady']


def room_library(grid):
    """The Library and its Restricted Section, carved into the hollow hill under the gatehouse."""
    found = rooms_library.place_library(grid)
    a = grid.anchors
    a.setdefault('chests', []).extend(found['chests'])
    a.setdefault('student_spots', []).extend(found['student_spots'])
    a['pensieve'] = found['pensieve']


def room_towers(grid):
    """The tower tops: the Gryffindor common room and dormitory in the jettied bridge tower, the Astronomy gallery and
    the Headmaster's office in the central tower's top."""
    found = rooms_towers.place_towers(grid)
    a = grid.anchors
    a.setdefault('chests', []).extend(found['chests'])
    a.setdefault('banners', []).extend(found['banners'])
    a.setdefault('student_spots', []).extend(found['student_spots'])
    a.setdefault('paintings', []).extend(found['paintings'])


def room_classrooms(grid):
    """The gatehouse opened (its gate, the vestibule, the stair hall with the stair down into the Library), Charms and
    Transfiguration in its rooms, Defence Against the Dark Arts carved into the hill under the apse."""
    layers, _ = rooms_library.cave_plan(load_base().cells)
    found = rooms_classrooms.place_classrooms(grid, layers)
    a = grid.anchors
    a.setdefault('chests', []).extend(found['chests'])
    a.setdefault('student_spots', []).extend(found['student_spots'])


def room_below(grid):
    """Below the castle, carved into the Great Hall's plinth: the stair down from the entrance hall, the kitchens under
    the house tables, the dungeon corridor with its cells and statue alcoves, the Slytherin common room."""
    found = rooms_below.place_below(grid)
    a = grid.anchors
    a.setdefault('elf_spots', []).extend(found['elf_spots'])
    a.setdefault('chests', []).extend(found['chests'])
    a.setdefault('student_spots', []).extend(found['student_spots'])
    a.setdefault('statues', []).extend(found['statues'])


def room_corridors(grid):
    """The above-ground finishing pass, after every room: the gallery dressed as the guardians' corridor (statue bays,
    banners, a runner, lanterns), statues at the Great Hall's portal and the gatehouse gate, the viaduct head lit,
    light behind every outer window, and the title-card point."""
    found = rooms_corridors.place_corridors(grid, list(collect_rooms(grid).values()))
    a = grid.anchors
    a.setdefault('statues', []).extend(found['statues'])
    a.setdefault('student_spots', []).extend(found['student_spots'])
    a['title_point'] = found['title_point']        # inside the central tower on purpose (R44: see title_point)


def collect_rooms(grid):
    """Fill ROOMS from the room modules: the Great Hall, entrance hall and west gallery; the Grand Staircase's well,
    its entry door and every closed course of its top (its own light boxes); the Library's rooms and tunnel; the tower
    tops; the gatehouse's rooms and the classrooms. Each box is an interior (its air is the castle's, never the open
    sky)."""
    ROOMS.clear()
    ROOMS['great hall'] = hall.HALL_PROPER_BOX
    ROOMS['great hall apse'] = hall.APSE_BOX
    ROOMS['great hall doors'] = hall.MAIN_DOOR_BOX
    ROOMS['entrance hall'] = hall.ENTRANCE_BOX
    ROOMS['entrance hall west door'] = hall.WEST_DOOR_BOX
    ROOMS['hall west gallery'] = hall.GALLERY_BOX
    for i, b in enumerate(rooms_staircase.light_boxes(grid.cells)):
        ROOMS['grand staircase %d' % i] = b
    for i, b in enumerate(rooms_library.room_boxes()):
        ROOMS['library %d' % i] = b
    ROOMS.update(rooms_towers.room_boxes(grid.cells))
    ROOMS.update(rooms_classrooms.room_boxes())
    ROOMS.update(rooms_below.room_boxes())
    ROOMS.update(rooms_corridors.room_boxes())
    return ROOMS


def _rock(state):
    """The castle's solid rock and the hall's plinth and skirt, for R35's pocket fill."""
    return checks.block_id(state) in hall.TERRAIN_NAMES


def seal_interiors(grid):
    """Mark the castle's interior air as minecraft:cave_air and fill the air pockets in its rock with stone (see
    INTERIOR AIR above). Returns the number of cells sealed."""
    n, filled = _seal(grid, list(collect_rooms(grid).values()), rock=_rock)
    seal_interiors.count = n
    seal_interiors.filled = filled
    return n


def wing_tops():
    """{(x, z): y} every column a room has built in, with the top of what it built there. Room tasks merge theirs
    in here, so the unchanged-castle check knows where they stood."""
    return dict(hall.column_tops())


ROOM_STEPS = (room_great_hall, room_grand_staircase, room_library, room_towers, room_classrooms, room_below,
              room_corridors)


# ---------------------------------------------------------------- checks

def _cell_box(box):
    (x0, y0, z0), (x1, y1, z1) = box['min'], box['max']
    return (int(x0), int(y0), int(z0), int(x1), int(y1), int(z1))


def verify(grid):
    """Assert what the rooms promise. Raises AssertionError naming the first broken promise."""
    a = grid.anchors
    cells = grid.cells
    base = load_base()

    # 1. the old diagonal wing is gone: none of its building blocks are left above its old floor line,
    #    except where the new hall (and its plinth, skirt and gallery) now stands
    covered = hall.covered_columns()
    left = [(p, s) for p, s in cells.items()
            if p[1] >= hall.OLD_WING_MIN_Y and hall.in_old_wing(p[0], p[2]) and (p[0], p[2]) not in covered
            and s.split('[', 1)[0] in hall.OLD_WING_IDS
            and not (s.split('[', 1)[0] == 'minecraft:stone' and p[1] < hall.OLD_FLOOR_Y)]
    assert not left, 'old wing blocks left: %d, e.g. %s' % (len(left), left[:5])

    # 2. the rest of the castle is unchanged: every old block outside the old wing's outline is still there and
    #    the same, unless a room built in its column at or below that room's top, or CHANGED_ON_PURPOSE lists it
    tops = wing_tops()

    def excused(p):
        col = (p[0], p[2])
        if col in tops and MAIN_FLOOR_Y <= p[1] <= tops[col]:
            return True
        return any(b[0] <= p[0] <= b[3] and b[1] <= p[1] <= b[4] and b[2] <= p[2] <= b[5]
                   for _, b in CHANGED_ON_PURPOSE)
    changed = [(p, s, cells.get(p)) for p, s in base.cells.items()
               if cells.get(p) != s and not hall.in_old_wing(p[0], p[2]) and not excused(p)]
    assert not changed, 'old castle changed outside the rooms: %d, e.g. %s' % (len(changed), changed[:5])

    # 3. the hall and the viaduct can be walked to from the main entrance
    start = tuple(a['main_entrance'])
    walk = checks.reachable(grid, start)
    stool_front = hall.STOOL_FRONT_CELL
    assert stool_front in walk, 'stool front %s not reachable from %s' % (stool_front, start)
    deck = hall.VIADUCT_DECK_CELL
    assert deck in walk, 'viaduct deck %s not reachable from %s' % (deck, start)

    # 4. the roof is over the ceiling: every column of the ceiling rectangle has a solid cell above the plane
    ceil = a['hall_ceiling']
    (cx0, cz0), (cx1, cz1), cy = ceil['min'], ceil['max'], ceil['y']
    tops = {}
    for (x, y, z), s in cells.items():
        if y >= cy and not checks.passable(s):
            tops[(x, z)] = True
    open_cols = [(x, z) for x in range(int(cx0), int(cx1)) for z in range(int(cz0), int(cz1)) if (x, z) not in tops]
    assert not open_cols, 'ceiling columns with no roof: %d, e.g. %s' % (len(open_cols), open_cols[:5])
    assert ceil['clearance'] >= 0, ceil

    # 5. nothing of the hall hangs over air: solid from under the floor down to the base's rock (or y 0); a room carved
    #    into the plinth below (the kitchens, the stair) is no hole: verify_below holds its walls, floor and ceiling whole
    carved = list(rooms_below.room_boxes().values())
    hanging = []
    for (x, z) in sorted(hall.footprint_columns()):
        y = MAIN_FLOOR_Y - 1
        while y >= 0 and not air_at(cells, (x, y, z)):
            if (x, y, z) in base.cells and checks.block_id(base.cells[(x, y, z)]) in hall.TERRAIN_NAMES:
                break                                          # reached the old castle's rock
            y -= 1
        if y >= 0 and air_at(cells, (x, y, z)) and not any(rooms_below._in((x, y, z), b) for b in carved):
            hanging.append((x, y, z))
    assert not hanging, 'hall columns over air: %d, e.g. %s' % (len(hanging), hanging[:5])

    # 6. no dark floor in the hall
    hall_box = _cell_box(a['hall_box'])
    dark = checks.dark_floor_cells(grid, [hall_box])
    assert not dark, 'dark hall floor cells: %d, e.g. %s' % (len(dark), dark[:5])

    # 7. the floating candles hang inside the hall under the ceiling, or in the Grand Staircase's void with no block
    #    in the box but the lantern chain
    x0, y0, z0, x1, y1, z1 = hall_box
    for c in a['candles']:
        (ax, ay, az), (hx, hy, hz) = c['at'], c['spread']
        if x0 <= ax - hx and ax + hx <= x1 + 1 and z0 <= az - hz and az + hz <= z1 + 1:
            assert y0 <= ay - hy and ay + hy < cy, ('candle box outside the hall in y', c)
            continue
        void = rooms_staircase
        assert (void.VOID_X[0] <= ax - hx and ax + hx <= void.VOID_X[1]
                and void.VOID_Z[0] <= az - hz and az + hz <= void.VOID_Z[1]
                and void.VOID_Y[0] <= ay - hy and ay + hy <= void.VOID_Y[1]), \
            ('candle box in neither the hall nor the staircase void', c)
        inside = [(p, s) for p, s in cells.items()
                  if ax - hx <= p[0] + 0.5 <= ax + hx and ay - hy <= p[1] + 0.5 <= ay + hy
                  and az - hz <= p[2] + 0.5 <= az + hz and checks.block_id(s) not in ('chain', 'lantern')
                  and not air_at(cells, p)]
        assert not inside, ('blocks in the staircase candle box', inside[:5])

    # 8. the chest stands on a free floor tile
    for chest in a['chests']:
        cx, cy2, cz = chest['at']
        assert grid.is_air(cx, cy2, cz) and checks.supports(grid.get(cx, cy2 - 1, cz)), chest
    sx, sy, sz = hall.STOOL_CELL
    assert grid.get(sx, sy, sz).split('[', 1)[0].endswith('_slab'), ('no stool', grid.get(sx, sy, sz))

    verify_staircase(grid, base, walk)
    verify_library(grid, base, walk)
    verify_towers(grid, base, walk)
    verify_classrooms(grid, base, walk)
    verify_below(grid, base, walk)
    verify_corridors(grid, base, walk)
    verify_sealed(grid, walk)
    verify_castle(grid, walk)
    verify_schematic(grid)
    return walk


def verify_staircase(grid, base, walk):
    """The Grand Staircase's promises: the well's plan is the old tower's, every landing is walked to from the main
    entrance, the top landing's door leads over the bridge to the Gryffindor tower with the Fat Lady's wall beside
    it, no dark floor anywhere in the tower, the statues stand clear of the walk and the portraits have a wall."""
    S = rooms_staircase
    a = grid.anchors
    cells = grid.cells

    # 9. the well's plan is the old tower's interior (so the changed boxes are inside cells only)
    assert S.measured_rows(base.cells) == S.WELL_ROWS, ('the tower interior moved', S.measured_rows(base.cells))

    # 10. every landing, and the far end of the bridge beyond the top landing's door, are walked to
    for name, c in sorted(S.LANDING_CELLS.items()):
        assert c in walk, '%s %s not reachable from the main entrance' % (name, c)
    door = S.GRYFFINDOR_DOOR
    assert door in walk and S.BRIDGE_FAR_END in walk, ('the Gryffindor door does not lead over the bridge', door)

    # 11. the Fat Lady: her wall stands beside that door and is solid behind her whole canvas, which is free
    fl = a['fat_lady']
    wx, wy, wz = fl['wall']
    assert fl['facing'] == 'north' and wz == door[2] and abs(wx - door[0]) <= 2, ('Fat Lady not at the door', fl)
    for x, y, z in S.FAT_LADY_CELLS:
        assert grid.is_air(x, y, z), ('something in front of the Fat Lady', (x, y, z), grid.get(x, y, z))
        assert checks.supports(grid.get(x, y, z + 1)), ('no wall behind the Fat Lady', (x, y, z + 1))

    # 12. no dark floor in the tower
    dark = checks.dark_floor_cells(grid, S.light_boxes(cells))
    assert not dark, 'dark staircase floor cells: %d, e.g. %s' % (len(dark), dark[:5])

    # 13. the statues: each on a free floor cell with three of headroom; standing there, they leave every landing,
    #     the bridge and the main entrance's walk to the first landing as they were
    statues = [tuple(s['at']) for s in a['statues']]
    for x, y, z in statues:
        assert all(grid.is_air(x, y + dy, z) for dy in range(3)), ('statue has no headroom', (x, y, z))
        assert checks.supports(grid.get(x, y - 1, z)), ('statue not on a floor', (x, y, z))
    blocked = Grid()
    blocked.cells = dict(cells)
    for x, y, z in statues:
        for dy in range(3):
            blocked.set(x, y + dy, z, 'minecraft:stone')
    start = S.LANDING_CELLS['first landing']
    walk2 = checks.reachable(blocked, start)
    for name, c in sorted(S.LANDING_CELLS.items()):
        assert c in walk2, 'the statues cut off the %s' % name
    assert S.BRIDGE_FAR_END in walk2 and tuple(a['main_entrance']) in walk2, 'the statues block the way'

    # 14. the portraits: a solid wall cell behind each, and nothing with a collision box in front (air, or the
    #     invisible light block that keeps a deep recess from being a dark spawning pocket; a painting survives it)
    step = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
    for p in a['paintings']:
        x, y, z = p['at']
        dx, dz = step[p['facing']]
        assert checks.supports(grid.get(x, y, z)), ('painting has no wall', p)
        for dy in (0, 1):
            s = grid.get(x + dx, y + dy, z + dz)
            assert grid.is_air(x + dx, y + dy, z + dz) or checks.block_id(s) == 'light', ('painting recess full', p, s)


def verify_library(grid, base, walk):
    """The Library's promises: every part of it is walked to from the main entrance, it holds 300 bookshelf-type
    blocks or more, the Restricted Section is barred with a single door, no floor is dark, the chest, the Pensieve and
    the reading spots stand on free floor, and the hollow hill round it is rock (no air pocket left beside it)."""
    L = rooms_library
    a = grid.anchors
    cells = grid.cells

    # 15. the tunnel, the hall, all three arms of the mezzanine (up the ladders), the Restricted strip and its alcove
    for name, c in sorted(L.LIBRARY_CELLS.items()):
        assert c in walk, 'library %s %s not reachable from the main entrance' % (name, c)

    # 16. shelving: bookshelves, chiseled bookshelves and the Ollivanders mix (mud and tuff bricks)
    shelves = L.shelf_count(cells)
    assert shelves >= 300, ('library shelf blocks', shelves)

    # 17. the Restricted Section is barred with one door: its screen is bars, the door's frame and exactly one door,
    #     the door is walked through, and with the door walled up nothing behind the screen can be walked to
    doors = []
    for x in range(L.HALL_X[0], L.HALL_X[1] + 1):
        for y in range(L.FEET, L.TOP + 1):
            s = checks.block_id(grid.get(x, y, L.SCREEN_Z))
            assert s in ('iron_bars', 'iron_door', 'dark_oak_log', 'dark_oak_planks'), ('gap in the bars', (x, y), s)
            if s == 'iron_door':
                doors.append((x, y, L.SCREEN_Z))
    assert len(doors) == 2 and doors[0][0] == doors[1][0], ('the bars need exactly one door', doors)
    assert L.RESTRICTED_INSIDE in walk, ('the Restricted Section\'s door does not open the way', L.RESTRICTED_INSIDE)
    walled = Grid()
    walled.cells = dict(cells)
    for p in doors:
        walled.set(*p, 'minecraft:stone')
    walk2 = checks.reachable(walled, tuple(a['main_entrance']))
    behind = [c for b in L.restricted_boxes() for c in L._box_cells(*b) if c in walk2]
    assert not behind, ('a way into the Restricted Section besides its door', behind[:5])

    # 18. no dark floor anywhere in the library
    dark = checks.dark_floor_cells(grid, L.room_boxes())
    assert not dark, 'dark library floor cells: %d, e.g. %s' % (len(dark), dark[:5])

    # 19. the anchors: the library chest in the Restricted Section with its front walked to (check 8 holds its floor);
    #     the Pensieve on a free cell over a solid block in the hall, with a walked cell beside it; the library's own
    #     reading spots (the shared list's spots inside its rooms; later rooms add theirs), 4 to 6 of them, each a
    #     walked, free floor cell with two of headroom
    step = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
    lib = [c for c in a['chests'] if c['theme'] == 'LIBRARY']
    assert len(lib) == 1, ('one library chest', lib)
    cx, cy, cz = lib[0]['at']
    dx, dz = step[lib[0]['facing']]
    in_restricted = any(b[0] <= cx <= b[3] and b[1] <= cy <= b[4] and b[2] <= cz <= b[5] for b in L.restricted_boxes())
    assert in_restricted and (cx + dx, cy, cz + dz) in walk, ('library chest not in the Restricted Section', lib)
    px, py, pz = a['pensieve']
    hx0, hy0, hz0, hx1, hy1, hz1 = L.hall_box()
    assert hx0 <= px <= hx1 and hz0 <= pz <= hz1, ('the Pensieve is not in the hall', a['pensieve'])
    assert grid.is_air(px, py, pz) and grid.is_air(px, py + 1, pz) and checks.supports(grid.get(px, py - 1, pz)), \
        ('the Pensieve\'s cell is not free floor', a['pensieve'])
    assert any((px + ex, py, pz + ez) in walk for ex, ez in step.values()), ('nobody can reach the Pensieve', (px, py, pz))
    spots = [tuple(s) for s in a['student_spots']
             if any(b[0] <= s[0] <= b[3] and b[1] <= s[1] <= b[4] and b[2] <= s[2] <= b[5] for b in L.room_boxes())]
    assert 4 <= len(spots) <= 6, ('library reading spots', spots)
    for x, y, z in spots:
        assert ((x, y, z) in walk and grid.is_air(x, y, z) and grid.is_air(x, y + 1, z)
                and checks.supports(grid.get(x, y - 1, z))), ('reading spot not a free, walked floor cell', (x, y, z))

    # 20. the hollow hill is rock round the rooms: every cell of its layers, but the corner towers' shafts, is solid
    #     or the rooms' own (the library's, and the classrooms' carved into the same hollow: the stair down from the
    #     gatehouse, ruling R31, and Defence Against the Dark Arts under the apse)
    layers, shafts = L.cave_plan(base.cells)
    keep = L.interior_cells() | rooms_classrooms.hill_cells()
    holes = [(x, y, z) for y, layer in layers.items() for x, z in layer
             if (x, z) not in shafts and air_at(cells, (x, y, z)) and (x, y, z) not in keep]
    assert not holes, 'air pockets left in the gatehouse hill: %d, e.g. %s' % (len(holes), holes[:5])

    # 21. the rooms, walls, floor and ceiling included, stand wholly in what was the hill's hollow, so the hill's shell
    #     is whole round them and nothing of the library shows outside but the tunnel's portal
    outside = [c for b in L.rooms_with_walls() for c in L._box_cells(*b) if (c[0], c[2]) not in layers[c[1]]]
    assert not outside, 'library cells outside the hill\'s hollow: %d, e.g. %s' % (len(outside), outside[:5])


def verify_towers(grid, base, walk):
    """The tower tops' promises: the band's plan is the old tower's, every room is walked to from the main entrance
    and the Gryffindor rooms only through the Fat Lady's door, at least four beds in the dormitory, the Astronomy
    gallery's edge railed, no dark floor, the telescopes point out through the arches, and the anchors (the TOWER
    chest, the Gryffindor banners and student spots) stand where they can be used."""
    T = rooms_towers
    a = grid.anchors
    cells = grid.cells

    # 22. the band's and the office's plans are the old tower's (so the gallery floor's boxes are inside cells only)
    assert T.measured_band(base.cells) == T.BAND_ROWS, ('the band\'s interior moved', T.measured_band(base.cells))
    assert T.measured_office(base.cells) == T.OFFICE_ROWS, ('the cone\'s interior moved', T.measured_office(base.cells))

    # 23. every room is walked to: the bridge, the common room, its hearth and the dormitory up its stair; the helical
    #     stair's top, the gallery round the oculus, and the office up the spiral
    for name, c in sorted(T.TOWER_CELLS.items()):
        assert c in walk, 'tower %s %s not reachable from the main entrance' % (name, c)

    # 24. the common room's door is the Fat Lady's: with the top landing's door walled up, nothing of the bridge or the
    #     Gryffindor tower is walked to (and her anchor is still Task 4's, beside that door)
    assert a['fat_lady'] == {'wall': list(rooms_staircase.FAT_LADY_WALL), 'facing': 'north'}, a['fat_lady']
    walled = Grid()
    walled.cells = dict(cells)
    dx, dy, dz = rooms_staircase.GRYFFINDOR_DOOR
    walled.set(dx, dy, dz, 'minecraft:stone')
    walled.set(dx, dy + 1, dz, 'minecraft:stone')
    walk2 = checks.reachable(walled, tuple(a['main_entrance']))
    behind = sorted(c for c in walk2 if T.in_gryffindor(c))
    assert not behind, ('a way into the Gryffindor tower besides the Fat Lady\'s door', behind[:5])

    # 25. at least four whole beds in the dormitory, each with a walked cell beside it
    beds = T.beds(cells)
    assert len(beds) >= 4, ('dormitory beds', beds)
    step = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
    for head, foot in beds:
        near = [(c[0] + ex, c[1], c[2] + ez) for c in (head, foot) for ex, ez in step.values()]
        assert any(n in walk for n in near), ('nobody can reach the bed', head)

    # 26. the Astronomy gallery's edge is railed: no walked cell beside a drop of more than 3 (the oculus, the arches,
    #     the helical stair's well)
    gaps = T.unrailed(grid, walk)
    assert not gaps, ('open edges on the Astronomy gallery', gaps[:5])
    assert T.platform_cells(walk), 'the Astronomy gallery is not walked to'

    # 27. no dark floor on the bridge, in the Gryffindor storeys, the gallery or the office
    dark = checks.dark_floor_cells(grid, T.tower_light_boxes())
    assert not dark, 'dark tower floor cells: %d, e.g. %s' % (len(dark), dark[:5])

    # 28. a telescope in every arch: a lightning-rod tube on its stand, pointing out through the opening
    for wall, (tx, tz), facing in T.ARCHES:
        rod = grid.get(tx, T.GALLERY_FEET + 1, tz)
        assert checks.block_id(rod) == 'lightning_rod' and 'facing=%s' % facing in rod, ('no telescope', (tx, tz), rod)
        ox, oz = step[facing]
        assert grid.is_air(tx + ox, T.GALLERY_FEET + 1, tz + oz), ('the telescope looks at a wall', (tx, tz))

    # 29. the anchors: one TOWER chest in the office with its front walked to (check 8 holds its floor tile); the
    #     Gryffindor banners in the common room, each in a free cell with a solid wall behind it; the common room's
    #     student spots (2 to 4, free walked floor cells with two of headroom)
    tower = [c for c in a['chests'] if c['theme'] == 'TOWER']
    assert len(tower) == 1, ('one tower chest', tower)
    cx, cy, cz = tower[0]['at']
    ox, oz = step[tower[0]['facing']]
    assert (cx, cz) in T.office_cells() and cy == T.OFFICE_FEET and (cx + ox, cy, cz + oz) in walk, tower
    assert a['banners'], 'no banners'
    x0, y0, z0, x1, y1, z1 = T.common_room_box()
    for b in a['banners']:
        bx, by, bz = b['at']
        ox, oz = step[b['facing']]
        free = grid.is_air(bx, by, bz) or checks.block_id(grid.get(bx, by, bz)) == 'light'
        assert b['house'] == 'gryffindor' and x0 <= bx <= x1 and y0 <= by <= y1 and z0 <= bz <= z1, b
        assert free and checks.supports(grid.get(bx - ox, by, bz - oz)), ('banner has no wall or no room', b)
    spots = [tuple(s) for s in a['student_spots'] if x0 <= s[0] <= x1 and y0 <= s[1] <= y1 and z0 <= s[2] <= z1]
    assert 2 <= len(spots) <= 4, ('common room student spots', spots)
    for x, y, z in spots:
        assert ((x, y, z) in walk and grid.is_air(x, y, z) and grid.is_air(x, y + 1, z)
                and checks.supports(grid.get(x, y - 1, z))), ('student spot not a free, walked floor cell', (x, y, z))


def verify_classrooms(grid, base, walk):
    """The classrooms' promises: the gatehouse gate is a real front door (R18); Transfiguration is the room behind the
    labelled door, and Defence, Transfiguration and Charms are each walked to with at least eight student desks, a
    teacher's desk and their theme prop; the gatehouse's stair leads down into the Library on its own (R31); Potions
    is untouched; no floor is dark; Defence stays inside the hill's hollow; the anchors (the SPELL_STUDY chest, the
    classrooms' student spots) stand where they can be used; every new interior is a sealed room."""
    C = rooms_classrooms
    a = grid.anchors
    cells = grid.cells
    step = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}

    def inside(p, box):
        return box[0] <= p[0] <= box[3] and box[1] <= p[1] <= box[4] and box[2] <= p[2] <= box[5]

    # 31. the gate is open: the main entrance's own cell leads through the gate plane into the vestibule
    gx, gy, gz = C.GATE_CELL
    assert checks.passable(grid.get(gx, gy, gz)) and checks.passable(grid.get(gx, gy + 1, gz)), \
        ('the gatehouse gate is still shut', grid.get(gx, gy, gz))
    assert C.VESTIBULE_CELL in walk, ('the gate does not lead inside', C.VESTIBULE_CELL)

    # 32. every classroom, the stair hall, the upper landing and the library stair are walked to; Transfiguration is the
    #     room behind the labelled door (the old castle's spruce door to the apse, still there), and Potions, behind
    #     that door, is walked to through it
    for name, c in sorted(C.CLASSROOM_CELLS.items()):
        assert c in walk, 'classroom %s %s not reachable from the main entrance' % (name, c)
    dx, dy, dz = C.LABELLED_DOOR
    for yy, half in ((dy, 'lower'), (dy + 1, 'upper')):
        s = grid.get(dx, yy, dz)
        assert checks.block_id(s) == 'spruce_door' and 'half=%s' % half in s, ('the labelled door is gone', s)
    front = (dx, dy, dz - 1)
    assert inside(front, C.ROOMS_BY_NAME['transfiguration']) and front in walk, ('Transfiguration is not behind the '
                                                                                 'labelled door', front)
    assert C.POTIONS_CELL in walk, ('Potions is not walked to through the labelled door', C.POTIONS_CELL)

    # 33. eight student desks or more in each classroom (a desk with a chair beside it, the sitter facing the desk) and
    #     the teacher's desk
    for name, room in sorted(C.CLASSROOMS.items()):
        desks = C.student_desks(cells, name)
        assert len(desks) >= 8, ('%s student desks' % name, len(desks))
        for x, y, z in room['teacher']:
            assert checks.block_id(grid.get(x, y, z)) == room['teacher_block'], ('%s teacher\'s desk' % name, (x, y, z))

    # 34. the theme props: Defence's dragon skeleton, bone blocks above head height hung from the ceiling by chains;
    #     Transfiguration's blackboard on a wall and its cages, and the old castle's Mirror of Erised set up at its back
    #     (R41); Charms' raised tiered floor (desks on three levels or more) and its floating feathers
    dragon = C.dragon_report(cells)
    assert dragon['bones'] >= 25 and dragon['low'] == [] and dragon['loose'] == [] and dragon['chains'] >= 2, \
        ('the dragon skeleton', dragon)
    board, cages = C.transfiguration_props(cells)
    assert board >= 6 and cages >= 4, ('Transfiguration board and cages', board, cages)
    assert C.mirror_ok(cells), ('the Mirror of Erised is gone from Transfiguration (R41)',
                                [(p, grid.get(*p)) for p in C.MIRROR_CELLS])
    levels = sorted({d[1] for d, _ in C.student_desks(cells, 'charms')})
    assert len(levels) >= 3 and levels == list(range(levels[0], levels[0] + len(levels))), ('Charms tiers', levels)
    feathers = C.floating_feathers(cells)
    assert feathers >= 5, ('Charms floating feathers', feathers)

    # 35. no dark floor in any classroom, the vestibule, the stair hall, the upper landing or on the library stair
    dark = checks.dark_floor_cells(grid, C.light_boxes())
    assert not dark, 'dark classroom floor cells: %d, e.g. %s' % (len(dark), dark[:5])

    # 36. the gatehouse's stair leads into the Library on its own (R31): with the grounds door in the hill walled up,
    #     the hall, the north gallery and the stair's foot are still walked to from the main entrance, and the stair
    #     climbs back: the vestibule is walked to from its foot (a drop of three gets down a stair with a missing tread,
    #     but nobody gets up it)
    walled = Grid()
    walled.cells = dict(cells)
    for y in (rooms_library.FEET, rooms_library.FEET + 1):
        walled.set(rooms_library.TUNNEL_X[0] + 1, y, rooms_library.DOOR_Z, 'minecraft:stone')
    walk2 = checks.reachable(walled, tuple(a['main_entrance']))
    for c in (C.LIBRARY_STAIR_FOOT, rooms_library.LIBRARY_CELLS['north mezzanine'], rooms_library.LIBRARY_CELLS['hall']):
        assert c in walk2, ('the gatehouse stair does not lead into the Library', c)
    assert C.VESTIBULE_CELL in checks.reachable(walled, C.LIBRARY_STAIR_FOOT), \
        ('the library stair cannot be climbed back to the gatehouse', C.LIBRARY_STAIR_FOOT)

    # 37. Potions is untouched: every old block of the apse room is as it was, its 7 cauldrons and 2 chests included
    changed = [(p, s, cells.get(p)) for p, s in base.cells.items() if inside(p, C.POTIONS_BOX) and cells.get(p) != s]
    assert not changed, ('Potions changed', changed[:5])
    pot = [s for p, s in cells.items() if inside(p, C.POTIONS_BOX)]
    assert sum('wizard_cauldron' in s for s in pot) == 7 and sum(checks.block_id(s) == 'chest' for s in pot) == 2, \
        'Potions lost a cauldron or a chest'

    # 38. Defence is carved into the hill's hollow only: its interior and its doorway are cells that were the hollow's
    #     air, and every cell of its walls, floor and ceiling outside the hollow is the old castle's, unchanged
    layers, _ = rooms_library.cave_plan(base.cells)
    for b in (C.ROOMS_BY_NAME['defence'], C.ROOMS_BY_NAME['defence doorway (hill)']):
        out = [c for c in rooms_library._box_cells(*b) if (c[0], c[2]) not in layers.get(c[1], ())]
        assert not out, ('Defence outside the hill\'s hollow', out[:5])
    shell = [c for c in rooms_library._box_cells(*C.DEFENCE_SHELL_BOX)
             if (c[0], c[2]) not in layers.get(c[1], ()) and cells.get(c) != base.cells.get(c)]
    assert not shell, ('Defence changed the hill\'s shell or the apse', shell[:5])

    # 39. the anchors: one SPELL_STUDY chest in Charms with its front walked to (check 8 holds its floor tile); each
    #     classroom's own student spots (3 or more), free walked floor cells with two of headroom
    spell = [c for c in a['chests'] if c['theme'] == 'SPELL_STUDY']
    assert len(spell) == 1, ('one SPELL_STUDY chest', spell)
    cx, cy, cz = spell[0]['at']
    ox, oz = step[spell[0]['facing']]
    assert inside((cx, cy, cz), C.ROOMS_BY_NAME['charms']) and (cx + ox, cy, cz + oz) in walk, spell
    for name, room in sorted(C.CLASSROOMS.items()):
        spots = [tuple(s) for s in a['student_spots'] if any(inside(s, b) for b in room['boxes'])]
        assert len(spots) >= 3, ('%s student spots' % name, spots)
        for x, y, z in spots:
            assert ((x, y, z) in walk and grid.is_air(x, y, z) and grid.is_air(x, y + 1, z)
                    and checks.supports(grid.get(x, y - 1, z))), ('student spot not a free, walked floor cell', (x, y, z))

    # 40. every new interior is one of the castle's sealed rooms (check 30 then holds its air to cave_air)
    rooms = ROOMS or collect_rooms(grid)
    missing = [n for n, b in C.ROOMS_BY_NAME.items() if b not in rooms.values()]
    assert not missing, ('classroom interiors not in ROOMS', missing)

    # 41. nothing is un-sealed: every air pocket the old castle closed in round the gatehouse (which the seal used to
    #     mark as an enclosed pocket, before the gate opened it to the outside) is cave_air or a block now, never plain
    #     air the world's terrain would keep
    plain = sorted(p for p in enclosed_air(base) if inside(p, C.GATEHOUSE_BOX) and p not in cells)
    assert not plain, ('gatehouse air left unsealed: %d' % len(plain), plain[:5])


def verify_below(grid, base, walk):
    """The promises below the castle: every room is walked to from the main entrance, and only down the entrance hall's
    stair; every room is carved into the Great Hall's plinth and closed in by it; the kitchens' four long tables lie
    under the four house tables, cell for cell; three cells or more stand behind iron bars; the Slytherin common room is
    lit green (sea lanterns behind green glass), low and has its dark fireplace; no floor below is dark; the anchors
    (the house elves' spots, the DUNGEON chest, the common room's student spots, the alcoves' statues) stand where they
    can be used; every room is a sealed interior."""
    B = rooms_below
    a = grid.anchors
    cells = grid.cells
    step = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}

    def free_floor(p):
        x, y, z = p
        return grid.is_air(x, y, z) and grid.is_air(x, y + 1, z) and checks.supports(grid.get(x, y - 1, z))

    # 42. every part below is walked to: both ends of the stair, the passage, the kitchens (south end, hearth, east
    #     aisle), the dungeon landing and the corridor's far end, the open cell, the Slytherin common room's door, hearth
    #     and far corners
    for name, c in sorted(B.BELOW_CELLS.items()):
        assert c in walk, 'below: %s %s not reachable from the main entrance' % (name, c)

    # 43. the way down is the entrance hall's stair: with the slot floored over, nothing below is walked to
    floored = Grid()
    floored.cells = dict(cells)
    for x, z in B.slot_columns():
        floored.set(x, MAIN_FLOOR_Y, z, 'minecraft:stone')
    walk2 = checks.reachable(floored, tuple(a['main_entrance']))
    other = sorted(c for c in walk2 if B.in_rooms(c))
    assert not other, ('a way below besides the entrance hall\'s stair', other[:5])

    # 44. carved into the plinth: every room cell is in a column of the hall, below its floor (the slot's own floor cells
    #     at it), and every cell round the rooms is solid but the slot's mouth into the entrance hall, so no room opens
    #     onto the world or the hill's hollow under the entrance hall
    report = B.plinth_report(cells, hall.footprint_columns())
    assert not report['outside'], ('below: room cells outside the plinth', report['outside'][:5])
    assert not report['leaks'], ('below: a room opens onto air', report['leaks'][:5])

    # 45. the kitchens' four long tables lie under the four house tables: every top cell of the hall's tables has a
    #     kitchen table cell under it, on the same x and z; and they are laid, not blizzarded (V3): on each table at
    #     most TABLE_DRESSING_MAX items per cell and TABLE_GOLD_MAX gold plates, with something on it
    tables = B.hall_table_cells(cells)
    groups = B.table_groups({x for x, _ in tables})
    assert len(groups) == 4, ('the Great Hall\'s house tables', groups)
    missing = sorted((x, z) for x, z in tables if grid.get(x, B.KITCHEN_FEET, z) != B.TABLE_SLAB)
    assert not missing, ('kitchen table cells missing under the house tables', len(missing), missing[:5])
    for i, t in enumerate(B.table_dressing_report(cells, tables)):
        assert 0 < t['items'] <= B.TABLE_DRESSING_MAX * t['cells'], ('kitchen table %d is over-laid' % i, t)
        assert t['gold'] <= B.TABLE_GOLD_MAX, ('kitchen table %d: too many gold plates' % i, t)

    # 46. three cells or more behind iron bars (their whole front onto the corridor is bars), and the open one with its
    #     iron door
    barred = B.barred_cells(cells)
    assert len(barred) >= 3, ('dungeon cells behind iron bars', barred)
    door = grid.get(B.OPEN_CELL[1], B.DUNGEON_FEET, B.OPEN_DOOR_Z)
    assert checks.block_id(door) == 'iron_door', ('the open cell\'s door', door)

    # 47. the Slytherin common room: lit green (sea lanterns right behind green glass facing the room, a dozen or more),
    #     low (five tall or less), and its dark fireplace (a lit soul fire in the firebox)
    green = B.green_lights(cells)
    assert green >= 12, ('Slytherin green lights', green)
    assert B.SLYTHERIN[4] - B.SLYTHERIN[1] + 1 <= 5, ('the Slytherin vault is not low', B.SLYTHERIN)
    fires = [p for p, s in cells.items() if B._in(p, B.SL_HEARTH) and checks.block_id(s) == 'soul_campfire'
             and 'lit=true' in s]
    assert fires, 'no soul fire in the Slytherin fireplace'

    # 48. no dark floor anywhere below
    dark = checks.dark_floor_cells(grid, B.light_boxes())
    assert not dark, 'dark floor cells below the castle: %d, e.g. %s' % (len(dark), dark[:5])

    # 49. the anchors: three house-elf spots or more in the kitchens; one DUNGEON chest in a cell with its front walked to
    #     (check 8 holds its floor tile); the common room's student spots (3 or 4); the alcoves' statues (check 13 holds
    #     their headroom) in the corridor's alcoves. Every spot is a free, walked floor cell with two of headroom
    elves = [tuple(p) for p in a.get('elf_spots', ())]
    assert len(elves) >= 3 and all(B._in(p, B.KITCHEN) for p in elves), ('house-elf spots', elves)
    dungeon = [c for c in a['chests'] if c['theme'] == 'DUNGEON']
    assert len(dungeon) == 1, ('one DUNGEON chest', dungeon)
    cx, cy, cz = dungeon[0]['at']
    ox, oz = step[dungeon[0]['facing']]
    assert any(B._in((cx, cy, cz), B._cell_box(c)) for c in B.CELLS) and (cx + ox, cy, cz + oz) in walk, dungeon
    spots = [tuple(s) for s in a['student_spots'] if B._in(s, B.SLYTHERIN)]
    assert 3 <= len(spots) <= 4, ('Slytherin student spots', spots)
    for p in elves + spots:
        assert p in walk and free_floor(p), ('spot below not a free, walked floor cell', p)
    alcoves = [tuple(s['at']) for s in a['statues'] if s['at'][1] == B.DUNGEON_FEET and B.in_rooms(tuple(s['at']))]
    assert len(alcoves) == 2 * len(B.ALCOVE_Z), ('the dungeon alcoves\' statues', alcoves)

    # 50. every room below is one of the castle's sealed rooms (check 30 then holds its air to cave_air; with 44, no air
    #     touches them that is not theirs)
    rooms = ROOMS or collect_rooms(grid)
    missing = [n for n, b in B.ROOMS_BY_NAME.items() if b not in rooms.values()]
    assert not missing, ('rooms below not in ROOMS', missing)


def verify_corridors(grid, base, walk):
    """The above-ground finishing pass's promises: its bays are sealed rooms walked to from the main entrance, no
    corridor floor is dark, the castle's guardians stand clear of every way, nothing at or below the main floor
    changed, every outer window has light behind it, and the title point and the new student spots are usable."""
    K = rooms_corridors
    S = rooms_staircase
    a = grid.anchors
    cells = grid.cells
    rooms = ROOMS or collect_rooms(grid)

    def inside(p, box):
        return box[0] <= p[0] <= box[3] and box[1] <= p[1] <= box[4] and box[2] <= p[2] <= box[5]

    # 51. the bays are sealed rooms (in ROOMS) with their statue's cell walked to; every box the pass lights has a
    #     walked floor cell in it
    for name, b in sorted(K.room_boxes().items()):
        assert rooms.get(name) == b, ('a gallery bay is not in ROOMS', name)
        assert any(c in walk for c in rooms_library._box_cells(*b)), ('nobody can reach the %s' % name, b)
    for b in K.light_boxes():
        assert any(inside(c, b) for c in walk), ('a corridor box nobody walks into', b)

    # 52. no dark floor in the gallery and its bays, the vestibule, the entrance hall or on the viaduct and its head
    dark = checks.dark_floor_cells(grid, K.light_boxes())
    assert not dark, 'dark corridor floor cells: %d, e.g. %s' % (len(dark), dark[:5])

    # 53. the guardians: eight or more beyond the staircase's ten (the gallery's bays, the portal's pair, the gate's
    #     pair), each on a free floor cell with three of headroom; walled up as three-tall columns, the statues leave
    #     every room's landmark walked to from the main entrance, and nothing walked with only the staircase's statues
    #     standing is lost to this pass's but their own cells
    statues = [tuple(s['at']) for s in a['statues']]
    expected = set(K.portal_statues(grid)) | set(K.GATE_STATUES) | {(K.WALL_X, K.BAY_FEET, z) for z in K.BAY_ZS}
    mine = [p for p in statues if p in expected]
    stair = [p for p in statues if p not in expected]
    assert set(mine) == expected and len(mine) >= 8 and len(stair) >= 10 and len(set(statues)) == len(statues), \
        ('statues', statues)
    for x, y, z in mine:
        assert all(grid.is_air(x, y + dy, z) for dy in range(3)), ('statue has no headroom', (x, y, z))
        assert checks.supports(grid.get(x, y - 1, z)), ('statue not on a floor', (x, y, z))

    def walled(points):
        g = Grid()
        g.cells = dict(cells)
        for x, y, z in points:
            for dy in range(3):
                g.set(x, y + dy, z, 'minecraft:stone')
        return checks.reachable(g, tuple(a['main_entrance']))
    walk_stair = walled(stair)
    walk_all = walled(statues)
    lost = walk_stair - walk_all - set(mine)
    assert not lost, ('the guardians block the way to %d cells' % len(lost), sorted(lost)[:5])
    marks = dict(S.LANDING_CELLS)
    marks.update(rooms_towers.TOWER_CELLS)
    marks.update(rooms_library.LIBRARY_CELLS)
    marks.update(rooms_classrooms.CLASSROOM_CELLS)
    marks.update({'stool front': hall.STOOL_FRONT_CELL, 'viaduct deck': hall.VIADUCT_DECK_CELL,
                  'hall west door': tuple(a['hall_west_door']), 'bridge far end': S.BRIDGE_FAR_END,
                  'potions': rooms_classrooms.POTIONS_CELL})
    cut = sorted(n for n, c in marks.items() if c not in walk_all)
    assert not cut, ('the statues cut off', cut)
    #     the castle's walk is well connected (a blocked gate is walked round through the Library's grounds door), so
    #     each guarded place is also walked on its own, inside its box: with every statue in it walled up, no cell of
    #     the box is farther to walk to than without them
    for name, start, box in K.LOCAL_BOXES:
        start = start or tuple(a['main_entrance'])
        guards = [p for p in statues if inside(p, box)]
        assert guards, ('no statue guards %s' % name, box)
        longer = K.detours(cells, start, box, guards)
        assert not longer, ('the statues lengthen the way at %s: %d cells' % (name, len(longer)), longer[:5])
    #     and every statue's 1.2-wide box clears the Great Hall's scale-4 door leaves whichever way they swing (R45)
    for d in a['doors']:
        near = [(p, round(K.door_clearance(p, d['hinge']), 2)) for p in statues
                if abs(p[1] - d['hinge'][1]) < 5 and K.door_clearance(p, d['hinge']) < K.DOOR_SWEEP]
        assert not near, ('a statue in a hall door leaf\'s sweep', d['hinge'], near)

    # 54. nothing at or below the main floor changed (R42): every cell the pass wrote is above it, and the castle below
    #     it is the same after the pass as before (compared inside the pass, before the seal)
    low = sorted(p for p in K.WRITTEN if p[1] <= MAIN_FLOOR_Y)
    assert not low, ('corridor writes at or below the main floor', low[:5])
    assert K.place_corridors.below_changed == [], ('the castle below the main floor changed',
                                                   K.place_corridors.below_changed[:5])

    # 55. every outer window has light behind it (Potions excepted, R42)
    unlit = K.unlit_windows(cells, K.sealed_interior(cells))
    assert not unlit, ('unlit outer windows: %d' % len(unlit), unlit[:5])

    # 56. the title point is on the central tower's axis between the main floor and the tower's top; the pass's
    #     student spots are free, walked floor cells with two of headroom
    tx, ty, tz = a['title_point']
    _, top = K.title_point(cells)
    assert abs(tx + 0.5 - hall.OLD_TOWER[0]) <= 1 and abs(tz + 0.5 - hall.OLD_TOWER[1] - BASE_SHIFT[2]) <= 1 \
        and MAIN_FLOOR_Y < ty < top, ('title point', a['title_point'])
    for x, y, z in K.GALLERY_SPOTS + K.ENTRANCE_SPOTS + K.FILL_SPOTS:
        assert [x, y, z] in a['student_spots'], (x, y, z)
        assert ((x, y, z) in walk and grid.is_air(x, y, z) and grid.is_air(x, y + 1, z)
                and checks.supports(grid.get(x, y - 1, z))), ('student spot not a free, walked floor cell', (x, y, z))


# The whole-castle caps (plan Task 9 Step 1). The candle cap is the Great Hall set's own block candles (its house
# tables', which are not ours to cut) plus CANDLE_ALLOWANCE for every room since (ruling R6).
COBWEB_CAP = 40
CANDLE_ALLOWANCE = 150
TORCH_LANTERN_CAP = 900
MIN_CHESTS = 5
MIN_STUDENT_SPOTS = 32
MIN_STATUES = 10 + 8                    # the Grand Staircase's ten and eight or more beyond them
SCHEM_DIM_MAX = 0x7FFF                  # write_schem packs Width/Height/Length as signed shorts; Java reads them unsigned
PALETTE_MAX = 32767                     # MiniHogwartsSchematic keeps palette ids in a short[]
VERIFY_SCHEM = os.path.join(REPO, 'build', 'mh', 'verify_mini_hogwarts.schem')


def set_candles():
    """The block candles the Great Hall set already has (the memory's own table candles), before any room adds its own."""
    return sum(1 for s in hall.G.build(open_roof=False).g.values() if checks.block_id(s).endswith('candle'))


def verify_castle(grid, walk):
    """The whole castle's promises, over every room at once: every room box is reached from the main entrance, no room
    floor is dark, every anchor stands where the Java side can use it, the castle keeps under its block caps, and the
    anchors hold the counts the furnishing needs."""
    a = grid.anchors
    cells = grid.cells
    rooms = ROOMS or collect_rooms(grid)

    def box_cells(b):
        x0, y0, z0, x1, y1, z1 = b
        return [(x, y, z) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1) for x in range(x0, x1 + 1)]

    # 57. every room box is reached from the main entrance: through passable cells (flying, so a void's or a vault's air
    #     counts) that are room air or walked cells, from the cells a player walks in (feet and head). Exempt: a box with
    #     no air left in it (a one-row slice a block has filled since it was measured: a chain, a pole, a pane) and the
    #     dungeon cells behind iron bars, which are seen through their bars, never entered (check 46; each front's bars
    #     must stand against the walked corridor)
    #     This proves AIR connection, not walking reach: the flood flies, so a room it reaches may still need a jump, a
    #     ladder or a missing step to enter on foot. Reach on foot is what the per-room walk checks prove (each room's
    #     own walk tests in test_castle.py, and the cells this file requires to be walked to, such as check 51's).
    in_rooms = set()
    for b in rooms.values():
        in_rooms.update(box_cells(b))
    flood = set()
    for x, y, z in walk:
        flood.update({(x, y, z), (x, y + 1, z)})
    queue = list(flood)
    while queue:
        x, y, z = queue.pop()
        for n in ((x + 1, y, z), (x - 1, y, z), (x, y + 1, z), (x, y - 1, z), (x, y, z + 1), (x, y, z - 1)):
            if n not in flood and n in in_rooms and checks.passable(cells.get(n, 'minecraft:air')):
                flood.add(n)
                queue.append(n)
    barred = {rooms_below._cell_box(c): c for c in rooms_below.barred_cells(cells)}
    unreached = []
    for name, b in sorted(rooms.items()):
        own = box_cells(b)
        if not any(air_at(cells, p) for p in own):
            continue
        if b in barred:
            front = rooms_below.cell_front(barred[b])
            dx = 1 if barred[b][0] == 'west' else -1             # the corridor is east of the west cells
            assert any((x + dx, y, z) in walk for x, y, z in front), ('a barred cell with no corridor before it', name)
            continue
        if not any(p in flood for p in own):
            unreached.append((name, b))
    assert not unreached, ('room boxes not reached from the main entrance: %d' % len(unreached), unreached[:5])

    # 58. no dark floor in any room
    dark = checks.dark_floor_cells(grid, list(rooms.values()))
    assert not dark, 'dark room floor cells: %d, e.g. %s' % (len(dark), dark[:5])

    # 59. every anchor point is usable as MiniHogwartsManager reads it (BlockState.isAir: an invisible light block is
    #     not air there): chests, the Pensieve, student and house-elf spots and statues stand in air (one, one, two, two
    #     and three cells tall) on a floor; the Fat Lady's wall and every painting's are solid; a banner hangs in an air
    #     cell against a solid wall; the stool is a slab; every point is inside the schematic; no two standing anchors
    #     share a cell
    _, _, _, mx, my, mz = grid.bounds()

    def in_schem(p):
        return 0 <= p[0] <= mx and 0 <= p[1] <= my and 0 <= p[2] <= mz

    def stands(p, tall, what):
        x, y, z = p
        assert in_schem(p) and in_schem((x, y + tall - 1, z)), ('%s outside the schematic' % what, p)
        assert all(grid.is_air(x, y + dy, z) for dy in range(tall)), \
            ('%s not in free air' % what, p, [grid.get(x, y + dy, z) for dy in range(tall)])
        assert checks.supports(grid.get(x, y - 1, z)), ('%s not on a floor' % what, p, grid.get(x, y - 1, z))
        return [(x, y + dy, z) for dy in range(tall)]

    standing = []
    standing += [c for ch in a['chests'] for c in stands(tuple(ch['at']), 1, 'chest')]
    standing += stands(tuple(a['pensieve']), 2, 'the Pensieve')
    standing += [c for s in a['student_spots'] for c in stands(tuple(s), 2, 'student spot')]
    standing += [c for s in a['elf_spots'] for c in stands(tuple(s), 2, 'house-elf spot')]
    standing += [c for s in a['statues'] for c in stands(tuple(s['at']), 3, 'statue')]
    shared = sorted(p for p in set(standing) if standing.count(p) > 1) if len(set(standing)) < len(standing) else []
    assert not shared, ('anchors share a cell', shared[:5])
    walls = [('the Fat Lady', tuple(a['fat_lady']['wall']))] + [('a painting', tuple(p['at'])) for p in a['paintings']]
    for what, p in walls:
        assert in_schem(p) and checks.supports(grid.get(*p)), ('%s has no solid wall' % what, p, grid.get(*p))
    step = {'north': (0, -1), 'south': (0, 1), 'east': (1, 0), 'west': (-1, 0)}
    for b in a['banners']:
        x, y, z = b['at']
        dx, dz = step[b['facing']]
        assert in_schem((x, y, z)) and grid.is_air(x, y, z), ('banner cell not air', b, grid.get(x, y, z))
        assert checks.supports(grid.get(x - dx, y, z - dz)), ('banner has no wall', b)
    sx, sy, sz = (int(math.floor(v)) for v in a['stool'])
    assert grid.get(sx, sy, sz).split('[', 1)[0].endswith('_slab'), ('the stool anchor is not a slab', a['stool'])
    for key in ('main_entrance', 'hall_west_door', 'title_point'):
        assert in_schem(tuple(a[key])), ('%s outside the schematic' % key, a[key])
    assert tuple(a['main_entrance']) in walk

    # 60. the census caps: cobwebs, block candles (the set's own plus the allowance, R6), torches and lanterns
    census = checks.census(grid)

    def count(match):
        return sum(n for k, n in census.items() if match(checks.block_id(k)))
    cobwebs = count(lambda i: i == 'cobweb')
    candles = count(lambda i: i.endswith('candle'))
    lights = count(lambda i: 'torch' in i or 'lantern' in i)
    cap = set_candles() + CANDLE_ALLOWANCE
    assert cobwebs <= COBWEB_CAP, ('cobwebs', cobwebs)
    assert candles <= cap, ('block candles over the cap', candles, cap)
    assert lights <= TORCH_LANTERN_CAP, ('torches and lanterns', lights)
    verify_castle.census = {'cobwebs': cobwebs, 'candles': candles, 'candle_cap': cap, 'torches_lanterns': lights}

    # 61. the counts the furnishing needs (MiniHogwartsAnchorsTest.shippedFileFinalCounts holds the shipped file to
    #     them too): five themed chests or more, 32 student spots or more, the staircase's ten statues and eight more
    assert len(a['chests']) >= MIN_CHESTS, ('chests', len(a['chests']))
    assert len(a['student_spots']) >= MIN_STUDENT_SPOTS, ('student spots', len(a['student_spots']))
    assert len(a['statues']) >= MIN_STATUES, ('statues', len(a['statues']))


def verify_schematic(grid):
    """62. The castle writes as a schematic MiniHogwartsSchematic loads: written to a scratch file (never the resource,
    so a broken castle still never reaches it) and read back the way the Java reads it, its size fits the shorts, its
    palette fits a short, the varint stream holds exactly one id per cell, and every cell reads back as the grid has it
    (cave_air included, which load_schem drops, so the stream is decoded here directly)."""
    _, _, _, mx, my, mz = grid.bounds()
    assert max(mx, my, mz) + 1 <= SCHEM_DIM_MAX, ('schematic size does not fit a short', (mx + 1, my + 1, mz + 1))
    write_schem(grid, VERIFY_SCHEM)
    w, h, l, by_id, flat = schem_to_nbt.decode_schem(schem_to_nbt.read_nbt(VERIFY_SCHEM))
    assert (w, h, l) == (mx + 1, my + 1, mz + 1), ('schematic size read back', (w, h, l))
    assert len(by_id) < PALETTE_MAX and max(by_id) < PALETTE_MAX, ('palette', len(by_id))
    assert len(flat) == w * h * l, ('cells in the stream', len(flat), w * h * l)
    wrong = [(p, s) for p, s in grid.cells.items() if by_id[flat[p[0] + p[2] * w + p[1] * w * l]] != s]
    assert not wrong, ('cells that read back wrong', len(wrong), wrong[:5])
    blocks = sum(1 for v in flat if not is_air(by_id[v]))
    assert blocks == sum(1 for s in grid.cells.values() if not is_air(s)), ('blocks read back', blocks)
    verify_schematic.stats = {'size': (w, h, l), 'palette': len(by_id), 'blocks': blocks,
                              'cave_air': sum(1 for v in flat if by_id[v] == 'minecraft:cave_air')}
    os.remove(VERIFY_SCHEM)


def verify_sealed(grid, walk):
    """30. Interior air is marked: every air cell of every ROOMS box is minecraft:cave_air, never plain (absent) air,
    so the placer clears the world out of every room (rulings R29, R30); no walked cell is a plain-air gap between
    sealed air (a doorway or passage no box covers, which the world's terrain would plug); and no cave_air stands
    under open sky."""
    rooms = ROOMS or collect_rooms(grid)
    cells = grid.cells
    plain = []
    for name, (x0, y0, z0, x1, y1, z1) in sorted(rooms.items()):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                for x in range(x0, x1 + 1):
                    if (x, y, z) not in cells:
                        plain.append((name, (x, y, z)))
    assert not plain, 'unsealed room air: %d, e.g. %s' % (len(plain), plain[:5])
    gaps = sealed_gaps(grid, walk)
    assert not gaps, 'plain-air gaps between sealed rooms: %d, e.g. %s' % (len(gaps), gaps[:5])
    # and outdoor air stays plain: no cave_air cell stands under open sky (nothing at all above it in its column)
    top = {}
    for (x, y, z), s in cells.items():
        if not air_at(cells, (x, y, z)) and y > top.get((x, z), -1):
            top[(x, z)] = y
    sky = [p for p, s in cells.items() if s == 'minecraft:cave_air' and top.get((p[0], p[2]), -1) < p[1]]
    assert not sky, 'cave_air under open sky: %d, e.g. %s' % (len(sky), sorted(sky)[:5])


def sealed_gaps(grid, walk, reach=4):
    """Every walked feet or head cell of plain (absent) air that has cave_air on both opposite horizontal sides
    along x or along z, looking up to `reach` cells through plain air each way: a doorway or passage between two
    sealed interiors that was left unsealed."""
    cells = grid.cells

    def side(x, y, z, dx, dz):
        for k in range(1, reach + 1):
            s = cells.get((x + dx * k, y, z + dz * k))
            if s is not None:
                return s
        return None

    out = set()
    for x, y, z in walk:
        for yy in (y, y + 1):
            if (x, yy, z) in cells:
                continue
            for dx, dz in ((1, 0), (0, 1)):
                if side(x, yy, z, dx, dz) == 'minecraft:cave_air' and side(x, yy, z, -dx, -dz) == 'minecraft:cave_air':
                    out.add((x, yy, z))
                    break
    return sorted(out)


def census_lines(grid, top=40):
    c = checks.census(grid)
    return ['%7d  %s' % (n, k) for k, n in c.most_common(top)]


# ---------------------------------------------------------------- output

def write_outputs(grid):
    write_schem(grid, OUT_SCHEM)
    with open(OUT_ANCHORS, 'w', newline='\n') as fh:
        json.dump(grid.anchors, fh, indent=1, sort_keys=True)
        fh.write('\n')
    os.makedirs(os.path.dirname(VIEWER_NBT), exist_ok=True)
    subprocess.run([sys.executable, '-I', os.path.join(_BUILD_SCRIPTS, 'schem_to_nbt.py'), OUT_SCHEM,
                    '-o', VIEWER_NBT], check=True, stdout=subprocess.DEVNULL)


def build():
    grid = load_base()
    grid.anchors['main_entrance'] = list(MAIN_ENTRANCE)
    for step in ROOM_STEPS:
        step(grid)
    seal_interiors(grid)
    return grid


def main():
    grid = build()
    walk = verify(grid)
    write_outputs(grid)
    b = grid.bounds()
    print('mini_hogwarts.schem: %d x %d x %d, %d blocks, %d bytes' % (
        b[3] + 1, b[4] + 1, b[5] + 1, len(grid.cells), os.path.getsize(OUT_SCHEM)))
    print('reachable from main_entrance %s: %d cells' % (grid.anchors['main_entrance'], len(walk)))
    print('anchors: ' + ', '.join(sorted(grid.anchors)))
    print('hall: %s' % getattr(hall.place_hall, 'stats', {}))
    print('staircase: %s' % getattr(rooms_staircase.place_staircase, 'stats', {}))
    print('library: %s' % getattr(rooms_library.place_library, 'stats', {}))
    print('towers: %s' % getattr(rooms_towers.place_towers, 'stats', {}))
    print('classrooms: %s' % getattr(rooms_classrooms.place_classrooms, 'stats', {}))
    print('below: %s' % getattr(rooms_below.place_below, 'stats', {}))
    print('corridors: %s' % getattr(rooms_corridors.place_corridors, 'stats', {}))
    print('castle: %s' % getattr(verify_castle, 'census', {}))
    print('schematic read back: %s' % getattr(verify_schematic, 'stats', {}))
    print('sealed: %d cave_air cells (%d room boxes); %d cells of rock pockets filled with stone' % (
        getattr(seal_interiors, 'count', 0), len(ROOMS), getattr(seal_interiors, 'filled', 0)))
    print('census:')
    print('\n'.join(census_lines(grid)))


if __name__ == '__main__':
    main()
