"""Generate the Mini Hogwarts decoration layer.

    python build_scripts/generate_mini_hogwarts_decor.py [--preview out.nbt]

WHAT THIS IS
------------
mini_hogwarts.schem is a third-party build. Its Great Hall is there, but bare: four lines
of single oak planks stand in for the house tables, the dais is an empty floor and there is
not one banner in the castle. This script writes a list of blocks that MiniHogwartsDecor
places on top of the finished castle:

  * the Great Hall: four house tables with benches, the staff table with the teachers'
    chairs and the headmaster's high-backed chair, the owl lectern, candles on the tables,
    the four house crests over the staff table, a house banner on every pillar, portraits
    over the door and two owl perches for the morning post;
  * a house banner on each side of the central round tower, seen from outside;
  * cauldrons in the south-wing classroom and book piles in the small library above it.

The output is DATA, not a schematic: src/main/resources/data/hogwartsmagic/schematic/
mini_hogwarts_decor.json. Each entry is a local position (same origin as the schematic), a
block state string, and for wall banners the house whose pattern the Java side applies.

THE GREAT HALL IS DIAGONAL
--------------------------
The hall runs at 45 degrees from the north-east (dais) to the south-west (door). Every rule
below works in two rotated coordinates:

    lane  = z + x   - which line parallel to the hall axis a cell is on (92..106 inside)
    along = z - x   - how far along the axis (-39 at the dais back, +7 by the door)

A cell exists only where lane and along have the same parity. Moving one step along a
lane is a diagonal step in the world, so one lane is a chain of cells that touch at the
corners. The original planks used lanes 94, 97, 101 and 104 for the tables, and this
keeps them: a wall aisle each side (92, 106), a centre aisle (99), and back-to-back
benches between the outer and inner tables.

VALIDATION
----------
Every entry is checked against the schematic before the file is written: floor items
must go into air with something under them, wall banners into air against a full block,
inlays only replace the wall blocks they are meant to replace, and no cell is used twice.
A failed check stops the script, so a schematic change cannot silently leave a banner
hanging in mid-air.
"""

import argparse
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)

from schem_to_nbt import read_nbt, decode_schem, split_state  # noqa: E402

SCHEM = os.path.join(ROOT, 'src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem')
OUT = os.path.join(ROOT, 'src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts_decor.json')

# Blocks a wall banner may hang from, and the castle wall blocks an inlay may replace.
FULL_BLOCKS = {
    'minecraft:cobblestone', 'minecraft:stone', 'minecraft:stone_bricks',
    'minecraft:oak_planks', 'minecraft:spruce_planks', 'minecraft:polished_andesite',
    'minecraft:mossy_cobblestone',
}
INLAY_REPLACES = {'minecraft:cobblestone'}

HOUSES = ('gryffindor', 'hufflepuff', 'ravenclaw', 'slytherin')
BANNER_BLOCK = {
    'gryffindor': 'minecraft:red_wall_banner',
    'hufflepuff': 'minecraft:yellow_wall_banner',
    'ravenclaw': 'minecraft:blue_wall_banner',
    'slytherin': 'minecraft:green_wall_banner',
}
CREST_BLOCK = {h: 'hogwartsmagic:%s_banner_block' % h for h in HOUSES}

HALL_FLOOR_Y = 37    # first air layer above the hall's cobblestone floor
DAIS_Y = 38          # first air layer above the dais planks
BANNER_Y = 45        # hangs over the pillar tops, clear of the torches at y=41
CREST_Y = 42
PORTRAIT_Y = 40
GREAT_HALL_CHEST = (70, 38, 31)   # must match MiniHogwartsDecor.GREAT_HALL_CHEST

TABLE = 'minecraft:dark_oak_slab[type=top,waterlogged=false]'
BENCH = 'minecraft:spruce_slab[type=bottom,waterlogged=false]'
CANDLES = 'hogwartsmagic:candle_cluster'

# Lanes of the four house tables, left to right as seen from the door.
TABLE_LANES = {'gryffindor': 94, 'hufflepuff': 97, 'ravenclaw': 101, 'slytherin': 104}
# The original planks leave two cross aisles; the tables are three runs each.
TABLE_RUNS = ((-28, -19), (-16, -7), (-4, 5))
PILLARS = (-33, -27, -21, -15, -9, -3, 3)   # along positions of the side-wall pillars


def cell(lane, along):
    """(lane, along) -> local (x, z). Lane and along must share parity."""
    assert (lane + along) % 2 == 0, (lane, along)
    return (lane - along) // 2, (lane + along) // 2


class Schematic:
    def __init__(self, path):
        w, h, l, by_id, cells = decode_schem(read_nbt(path))
        self.w, self.h, self.l = w, h, l
        self.by_id = by_id
        self.cells = cells

    def name(self, x, y, z):
        if not (0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.l):
            return 'minecraft:air'
        return split_state(self.by_id[self.cells[x + z * self.w + y * self.w * self.l]])[0]

    def is_air(self, x, y, z):
        return self.name(x, y, z) in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')


OFFSET = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}


class Decor:
    def __init__(self, schem):
        self.s = schem
        self.entries = []
        self.used = {}
        self.errors = []

    def _claim(self, x, y, z, what):
        key = (x, y, z)
        if key in self.used:
            self.errors.append('%s at %s already holds %s' % (what, key, self.used[key]))
            return False
        self.used[key] = what
        return True

    def floor(self, x, y, z, state, replace=()):
        """A block standing on the floor: the cell is air (or a listed block) with support below."""
        here = self.s.name(x, y, z)
        if not (self.s.is_air(x, y, z) or here in replace):
            self.errors.append('floor %s at %s: cell holds %s' % (state, (x, y, z), here))
            return
        below_ok = not self.s.is_air(x, y - 1, z) or (x, y - 1, z) in self.used
        if not below_ok:
            self.errors.append('floor %s at %s: nothing underneath' % (state, (x, y, z)))
            return
        if self._claim(x, y, z, state):
            self.entries.append({'x': x, 'y': y, 'z': z, 'state': state})

    def wall_banner(self, x, y, z, facing, house):
        """A wall banner in an air cell, hanging from the full block behind it."""
        dx, dz = OFFSET[facing]
        support = self.s.name(x - dx, y, z - dz)
        if not self.s.is_air(x, y, z):
            self.errors.append('banner at %s: cell holds %s' % ((x, y, z), self.s.name(x, y, z)))
            return
        if support not in FULL_BLOCKS:
            self.errors.append('banner at %s facing %s: hangs from %s' % ((x, y, z), facing, support))
            return
        if not self.s.is_air(x, y - 1, z):
            self.errors.append('banner at %s: the cloth would run into %s'
                               % ((x, y, z), self.s.name(x, y - 1, z)))
            return
        state = '%s[facing=%s]' % (BANNER_BLOCK[house], facing)
        if self._claim(x, y, z, state):
            self.entries.append({'x': x, 'y': y, 'z': z, 'state': state, 'banner': house})

    def inlay(self, x, y, z, state, show_towards):
        """Swap a wall block for a decorative full block whose face shows on the given side."""
        here = self.s.name(x, y, z)
        if here not in INLAY_REPLACES:
            self.errors.append('inlay %s at %s: would replace %s' % (state, (x, y, z), here))
            return
        dx, dz = OFFSET[show_towards]
        if not self.s.is_air(x + dx, y, z + dz):
            self.errors.append('inlay %s at %s: the %s face is covered' % (state, (x, y, z), show_towards))
            return
        if self._claim(x, y, z, state):
            self.entries.append({'x': x, 'y': y, 'z': z, 'state': state})


# --- the Great Hall -----------------------------------------------------------------

def great_hall(d):
    planks = {'minecraft:oak_planks'}

    # House tables and benches. The table replaces the original plank line it sits on.
    for house, lane in TABLE_LANES.items():
        for start, end in TABLE_RUNS:
            run = [al for al in range(start, end + 1) if (al + lane) % 2 == 0]
            for al in run:
                x, z = cell(lane, al)
                d.floor(x, HALL_FLOOR_Y, z, TABLE, replace=planks)
            for bench_lane in (lane - 1, lane + 1):
                for al in range(start, end + 1):
                    if (al + bench_lane) % 2 == 0:
                        x, z = cell(bench_lane, al)
                        d.floor(x, HALL_FLOOR_Y, z, BENCH)
            # Candles on the middle of every run, a little light to eat by.
            x, z = cell(lane, run[len(run) // 2])
            d.floor(x, HALL_FLOOR_Y + 1, z, CANDLES)

    # Staff table: two rows across the dais make one connected stepped table.
    staff = set()
    for al, lanes in ((-36, range(94, 105, 2)), (-35, range(93, 106, 2))):
        for lane in lanes:
            x, z = cell(lane, al)
            d.floor(x, DAIS_Y, z, TABLE)
            staff.add((x, z))
    for lane in (93, 105):   # candles at both ends of the staff table
        x, z = cell(lane, -35)
        d.floor(x, DAIS_Y + 1, z, CANDLES)

    # Teachers' chairs behind it. A stair is a chair whose back is its 'facing' side; each
    # faces the table cell to its south, or to its west at the far end.
    for lane in range(93, 106, 2):
        x, z = cell(lane, -37)
        if lane == 99:
            continue
        facing = 'north' if (x, z + 1) in staff else 'east'
        d.floor(x, DAIS_Y, z, 'minecraft:spruce_stairs[facing=%s,half=bottom,shape=straight,'
                              'waterlogged=false]' % facing)

    # The headmaster's chair in the centre, darker, with a high back in the window bay.
    x, z = cell(99, -37)
    d.floor(x, DAIS_Y, z, 'minecraft:dark_oak_stairs[facing=north,half=bottom,shape=straight,'
                          'waterlogged=false]')
    bx, bz = cell(98, -38)
    assert (bx, bz) == (x, z - 1), 'the high back must sit right behind the chair'
    d.floor(bx, DAIS_Y, bz, 'minecraft:dark_oak_planks')
    d.floor(bx, DAIS_Y + 1, bz, 'minecraft:dark_oak_slab[type=bottom,waterlogged=false]')

    # The owl lectern at the front of the dais. A lectern faces whoever reads at it, and
    # the headmaster reads from the dais side.
    x, z = cell(99, -33)
    d.floor(x, DAIS_Y, z, 'minecraft:lectern[facing=north,has_book=false,powered=false]')

    # The four house crests on the back wall, above the staff table.
    for house, lane in zip(HOUSES, (94, 96, 102, 104)):
        x, z = cell(lane, -38)
        d.inlay(x, CREST_Y, z, CREST_BLOCK[house], show_towards='south')

    # A house banner on every side-wall pillar, on the face you see from the door. The
    # banners alternate between the two houses whose tables are on that side.
    for i, al in enumerate(PILLARS):
        x, z = cell(92, al + 1)
        d.wall_banner(x, BANNER_Y, z, 'south', ('gryffindor', 'hufflepuff')[i % 2])
        x, z = cell(106, al + 1)
        d.wall_banner(x, BANNER_Y, z, 'west', ('slytherin', 'ravenclaw')[i % 2])

    # Portraits over the door, set into the end wall like the paintings they are.
    for lane in (94, 96, 102, 104):
        x, z = cell(lane, 8)
        d.inlay(x, PORTRAIT_Y, z, 'hogwartsmagic:moving_portrait[facing=north]', show_towards='north')

    # Two owl perches by the door for the morning post.
    for lane in (93, 105):
        x, z = cell(lane, 7)
        d.floor(x, HALL_FLOOR_Y, z, 'hogwartsmagic:owl_perch')

    # MiniHogwartsManager puts the Great Hall's food chest in the window bay behind the
    # headmaster's chair (MiniHogwartsDecor.GREAT_HALL_CHEST). Keep that cell free.
    x, z = cell(101, -39)
    assert (x, DAIS_Y, z) == GREAT_HALL_CHEST, 'update MiniHogwartsDecor.GREAT_HALL_CHEST'
    if not (d.s.is_air(x, DAIS_Y, z) and not d.s.is_air(x, DAIS_Y - 1, z)) or (x, DAIS_Y, z) in d.used:
        d.errors.append('the Great Hall chest cell %s is not a free floor tile' % ((x, DAIS_Y, z),))
    wx, wz = x + OFFSET['west'][0], z + OFFSET['west'][1]
    if not d.s.is_air(wx, DAIS_Y, wz) or (wx, DAIS_Y, wz) in d.used:
        d.errors.append('the Great Hall chest at %s cannot open to the west' % ((x, DAIS_Y, z),))


# --- outside and the south wing -----------------------------------------------------

def tower_banners(d):
    """One house banner high on each side of the central round tower, facing outward."""
    cx, cz = 61, 64
    sides = (('north', 'gryffindor', (cx, 56)), ('east', 'ravenclaw', (69, cz)),
             ('south', 'hufflepuff', (cx, 72)), ('west', 'slytherin', (53, cz)))
    for facing, house, (wx, wz) in sides:
        dx, dz = OFFSET[facing]
        placed = False
        # Highest spot on the wall with a full block to hang from and open air below.
        for y in range(60, 44, -1):
            x, z = wx + dx, wz + dz
            if (d.s.name(wx, y, wz) in FULL_BLOCKS and d.s.is_air(x, y, z)
                    and d.s.is_air(x, y - 1, z)):
                d.wall_banner(x, y, z, facing, house)
                placed = True
                break
        if not placed:
            d.errors.append('no spot for the %s banner on the round tower' % facing)


def south_wing(d):
    # The south-wing classroom (desks and chairs at y=26): a potions class needs cauldrons.
    for x, z in ((47, 102), (47, 104)):
        d.floor(x, 26, z, 'hogwartsmagic:wizard_cauldron')
    # The small library above it, around the bookshelf stack.
    for x, z in ((36, 101), (40, 101)):
        d.floor(x, 32, z, 'hogwartsmagic:enchanted_book_pile')


def build(schem):
    d = Decor(schem)
    great_hall(d)
    tower_banners(d)
    south_wing(d)
    return d


# --- preview ------------------------------------------------------------------------

def write_preview(schem, entries, path):
    """The castle plus the decor as a sparse structure template, for the renderers."""
    from schem_to_nbt import write_nbt
    overrides = {(e['x'], e['y'], e['z']): e['state'] for e in entries}
    palette, index, blocks = [], {}, []

    def pid(state):
        if state not in index:
            name, props = split_state(state)
            entry = {'Name': ('string', name)}
            if props:
                entry['Properties'] = ('compound', {k: ('string', v) for k, v in props.items()})
            index[state] = len(palette)
            palette.append(('compound', entry))
        return index[state]

    for y in range(schem.h):
        for z in range(schem.l):
            for x in range(schem.w):
                state = overrides.get((x, y, z))
                if state is None:
                    state = schem.by_id[schem.cells[x + z * schem.w + y * schem.w * schem.l]]
                    if split_state(state)[0] in ('minecraft:air', 'minecraft:cave_air'):
                        continue
                blocks.append(('compound', {'pos': ('list', 'int', [('int', x), ('int', y), ('int', z)]),
                                            'state': ('int', pid(state))}))
    root = {
        'DataVersion': ('int', 3953),
        'size': ('list', 'int', [('int', schem.w), ('int', schem.h), ('int', schem.l)]),
        'palette': ('list', 'compound', palette),
        'blocks': ('list', 'compound', blocks),
        'entities': ('list', 'compound', []),
    }
    write_nbt(path, ('compound', root))


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--preview', help='also write the castle with the decor as a .nbt to render')
    args = ap.parse_args()

    schem = Schematic(SCHEM)
    d = build(schem)
    if d.errors:
        for e in d.errors:
            print('ERROR', e, file=sys.stderr)
        sys.exit('%d placement errors; nothing written' % len(d.errors))

    d.entries.sort(key=lambda e: (e['y'], e['z'], e['x']))
    with open(OUT, 'w') as fh:
        fh.write('{\n  "_comment": "Generated by build_scripts/generate_mini_hogwarts_decor.py. Do not edit.",\n')
        fh.write('  "blocks": [\n')
        fh.write(',\n'.join('    ' + json.dumps(e, separators=(', ', ': ')) for e in d.entries))
        fh.write('\n  ]\n}\n')
    counts = {}
    for e in d.entries:
        name = split_state(e['state'])[0]
        counts[name] = counts.get(name, 0) + 1
    print('wrote %s: %d blocks' % (os.path.relpath(OUT, ROOT), len(d.entries)))
    for name, n in sorted(counts.items(), key=lambda t: -t[1]):
        print('  %4d  %s' % (n, name))
    if args.preview:
        write_preview(schem, d.entries, args.preview)
        print('preview: %s' % args.preview)


if __name__ == '__main__':
    main()
