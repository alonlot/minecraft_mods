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
main level (the gatehouse and the viaduct deck); players walk at MAIN_FLOOR_Y + 1.

Each room is one function, called in order by ROOM_STEPS; each stamps its blocks into the grid and
adds its anchors. verify() asserts what the rooms promise (reachability, light, structure) and runs
before anything is written, so a broken castle never reaches the resources.
"""

import json
import os
import subprocess
import sys

_HERE = os.path.dirname(os.path.abspath(__file__))
_BUILD_SCRIPTS = os.path.dirname(_HERE)
if _BUILD_SCRIPTS not in sys.path:
    sys.path.insert(0, _BUILD_SCRIPTS)

# The 60 the plan started from left no room north of the hall for its rock skirt (the hall's north
# end stands 24 blocks above open ground); 84 = 60 + the skirt's reach.
BASE_SHIFT = (0, 0, 84)
MAIN_FLOOR_Y = 24

REPO = os.path.dirname(_BUILD_SCRIPTS)
SOURCE = os.path.join(_HERE, 'source', 'mini_hogwarts_v1.schem')
SCHEM_DIR = os.path.join(REPO, 'src', 'main', 'resources', 'data', 'hogwartsmagic', 'schematic')
OUT_SCHEM = os.path.join(SCHEM_DIR, 'mini_hogwarts.schem')
OUT_ANCHORS = os.path.join(SCHEM_DIR, 'mini_hogwarts.anchors.json')
VIEWER_NBT = os.path.join(REPO, 'build', 'mh', 'mini_hogwarts.nbt')

from mini_hogwarts import checks, hall  # noqa: E402  (hall reads BASE_SHIFT / MAIN_FLOOR_Y from here)
from mini_hogwarts.grid import Grid, load_schem, write_schem  # noqa: E402


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


ROOM_STEPS = (room_great_hall,)


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

    # 2. the hall and the viaduct can be walked to from the main entrance
    start = tuple(a['main_entrance'])
    walk = checks.reachable(grid, start)
    stool_front = hall.STOOL_FRONT_CELL
    assert stool_front in walk, 'stool front %s not reachable from %s' % (stool_front, start)
    deck = hall.VIADUCT_DECK_CELL
    assert deck in walk, 'viaduct deck %s not reachable from %s' % (deck, start)

    # 3. the roof is over the ceiling: every column of the ceiling rectangle has a solid cell above the plane
    ceil = a['hall_ceiling']
    (cx0, cz0), (cx1, cz1), cy = ceil['min'], ceil['max'], ceil['y']
    tops = {}
    for (x, y, z) in cells:
        if y >= cy:
            tops[(x, z)] = True
    open_cols = [(x, z) for x in range(int(cx0), int(cx1)) for z in range(int(cz0), int(cz1)) if (x, z) not in tops]
    assert not open_cols, 'ceiling columns with no roof: %d, e.g. %s' % (len(open_cols), open_cols[:5])
    assert ceil['clearance'] >= 0, ceil

    # 4. nothing of the hall hangs over air: solid from under the floor down to the base's rock (or y 0)
    hanging = []
    for (x, z) in sorted(hall.footprint_columns()):
        y = MAIN_FLOOR_Y - 1
        while y >= 0 and (x, y, z) in cells:
            if (x, y, z) in base.cells and checks._id(base.cells[(x, y, z)]) in hall.TERRAIN_NAMES:
                break                                          # reached the old castle's rock
            y -= 1
        if y >= 0 and (x, y, z) not in cells:
            hanging.append((x, y, z))
    assert not hanging, 'hall columns over air: %d, e.g. %s' % (len(hanging), hanging[:5])

    # 5. no dark floor in the hall
    hall_box = _cell_box(a['hall_box'])
    dark = checks.dark_floor_cells(grid, [hall_box])
    assert not dark, 'dark hall floor cells: %d, e.g. %s' % (len(dark), dark[:5])

    # 6. the floating candles hang inside the hall, under the ceiling
    x0, y0, z0, x1, y1, z1 = hall_box
    for c in a['candles']:
        (ax, ay, az), (hx, hy, hz) = c['at'], c['spread']
        assert x0 <= ax - hx and ax + hx <= x1 + 1, ('candle box outside the hall in x', c)
        assert z0 <= az - hz and az + hz <= z1 + 1, ('candle box outside the hall in z', c)
        assert y0 <= ay - hy and ay + hy < cy, ('candle box outside the hall in y', c)

    # 7. the chest stands on a free floor tile
    for chest in a['chests']:
        cx, cy2, cz = chest['at']
        assert (cx, cy2, cz) not in cells and checks.supports(grid.get(cx, cy2 - 1, cz)), chest
    sx, sy, sz = hall.STOOL_CELL
    assert grid.get(sx, sy, sz).split('[', 1)[0].endswith('_slab'), ('no stool', grid.get(sx, sy, sz))
    return walk


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
    for step in ROOM_STEPS:
        step(grid)
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
    print('census:')
    print('\n'.join(census_lines(grid)))


if __name__ == '__main__':
    # Run the package's copy of this module, so hall.py (which imports BASE_SHIFT and MAIN_FLOOR_Y
    # from mini_hogwarts.build_castle) and this script share one set of constants.
    from mini_hogwarts import build_castle as _castle
    _castle.main()
