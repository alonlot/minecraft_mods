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
minecraft:cave_air (which the placer does place) into every air cell of every room interior (ROOMS, a
name -> box dict collected from the room modules: their boxes reach out through doors and windows) and
into every air pocket the blocks close in (grid.enclosed_air). Outdoor air stays plain air.
"""

import json
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

from mini_hogwarts import checks, hall, rooms_library, rooms_staircase, rooms_towers  # noqa: E402
from mini_hogwarts.grid import Grid, air_at, load_schem, seal_interiors as _seal, write_schem  # noqa: E402


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


def collect_rooms(grid):
    """Fill ROOMS from the room modules: the Great Hall, entrance hall and west gallery; the Grand Staircase's well,
    its entry door and every closed course of its top (its own light boxes); the Library's rooms and tunnel; the tower
    tops. Each box is an interior (its air is the castle's, never the open sky)."""
    ROOMS.clear()
    ROOMS['great hall'] = hall.HALL_PROPER_BOX
    ROOMS['great hall apse'] = hall.APSE_BOX
    ROOMS['entrance hall'] = hall.ENTRANCE_BOX
    ROOMS['hall west gallery'] = hall.GALLERY_BOX
    for i, b in enumerate(rooms_staircase.light_boxes(grid.cells)):
        ROOMS['grand staircase %d' % i] = b
    for i, b in enumerate(rooms_library.room_boxes()):
        ROOMS['library %d' % i] = b
    ROOMS.update(rooms_towers.room_boxes(grid.cells))
    return ROOMS


def seal_interiors(grid):
    """Mark the castle's interior air as minecraft:cave_air (see INTERIOR AIR above). Returns the count."""
    n = _seal(grid, list(collect_rooms(grid).values()))
    seal_interiors.count = n
    return n


def wing_tops():
    """{(x, z): y} every column a room has built in, with the top of what it built there. Room tasks merge theirs
    in here, so the unchanged-castle check knows where they stood."""
    return dict(hall.column_tops())


ROOM_STEPS = (room_great_hall, room_grand_staircase, room_library, room_towers)


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

    # 5. nothing of the hall hangs over air: solid from under the floor down to the base's rock (or y 0)
    hanging = []
    for (x, z) in sorted(hall.footprint_columns()):
        y = MAIN_FLOOR_Y - 1
        while y >= 0 and not air_at(cells, (x, y, z)):
            if (x, y, z) in base.cells and checks.block_id(base.cells[(x, y, z)]) in hall.TERRAIN_NAMES:
                break                                          # reached the old castle's rock
            y -= 1
        if y >= 0 and air_at(cells, (x, y, z)):
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
    verify_sealed(grid)
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
    #     or the rooms' own
    layers, shafts = L.cave_plan(base.cells)
    keep = L.interior_cells()
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


def verify_sealed(grid):
    """30. Interior air is marked: every air cell of every ROOMS box is minecraft:cave_air, never plain (absent) air,
    so the placer clears the world out of every room (rulings R29, R30)."""
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
    # and outdoor air stays plain: no cave_air cell stands under open sky (nothing at all above it in its column)
    top = {}
    for (x, y, z), s in cells.items():
        if not air_at(cells, (x, y, z)) and y > top.get((x, z), -1):
            top[(x, z)] = y
    sky = [p for p, s in cells.items() if s == 'minecraft:cave_air' and top.get((p[0], p[2]), -1) < p[1]]
    assert not sky, 'cave_air under open sky: %d, e.g. %s' % (len(sky), sorted(sky)[:5])


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
    print('sealed: %d cave_air cells (%d room boxes)' % (getattr(seal_interiors, 'count', 0), len(ROOMS)))
    print('census:')
    print('\n'.join(census_lines(grid)))


if __name__ == '__main__':
    main()
