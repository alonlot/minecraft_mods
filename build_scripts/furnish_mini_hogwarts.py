"""Furnish the Great Hall of Mini Hogwarts with the mod's decorations.

    python build_scripts/furnish_mini_hogwarts.py [--dry-run]

Edits src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem in place. Run it once on
a schematic that has not been furnished yet; it refuses to run twice (the palette tells it).

The hall is built at 45 degrees: its long axis runs toward +X -Z, and the four house tables are
diagonal lines of oak planks (x + z = 94, 97, 101, 104), in runs of five with a gap for an aisle.
The staff dais is the north-east end. Everything below is found from the castle's own blocks, not
typed in as coordinates, so a mistake shows up as "found 0" rather than as a bench inside a wall.

What goes in:
  - the house tables become Wizard Tables: each diagonal line is filled out into a stepped band, so
    the tables join into four long tables with legs only at the open corners;
  - no benches by default (--layout benches adds them in the wider gaps; rendered, they blurred the
    four tables into one dark mass, so they are left out);
  - Floating Candles in a loose grid high under the roof, ten blocks above the floor;
  - Hanging House Banners on the long walls, only where a full cobblestone block is behind both
    halves (cobblestone walls and glass are not sturdy enough to hang on);
  - the four House Points Hourglasses on the dais, across the far end.

Every block goes only into a cell that is air in the schematic.
"""
import argparse
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_typed as N  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCHEM = os.path.join(ROOT, 'src/main/resources/data/hogwartsmagic/schematic/mini_hogwarts.schem')
M = 'hogwartsmagic:'
HOUSES = ['gryffindor', 'ravenclaw', 'hufflepuff', 'slytherin']

FLOOR_Y = 37            # the cell a person stands in (the hall floor block is y = 36)
SEED = (56, 38, 44)     # an air cell in the middle of the hall, one above the floor
TABLE_LINES = (94, 97, 101, 104)
DAIS_FROM = 30          # x - z at or beyond this is the staff dais, short of it the hall floor
CANDLE_Y = 47          # the roof is at y = 50-51 over most of the hall
# Which side of each table line its band grows on (+1: toward x + z + 1). The gaps between the lines
# are 2, 3 and 2 cells; growing apart leaves 2, 1 and 2, so the middle gap is the aisle.
BAND_SIDE = {94: -1, 97: +1, 101: -1, 104: +1}
BENCH_ROWS = (92, 95, 96, 102, 103, 106)
AIRS = ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')


class Schematic:
    def __init__(self, path):
        self.root_name, self.root = N.read(path)
        s = self.root['Schematic'][1]
        self.s = s
        self.w, self.h, self.l = s['Width'][1] & 0xFFFF, s['Height'][1] & 0xFFFF, s['Length'][1] & 0xFFFF
        blocks = s['Blocks'][1]
        self.palette = {k: v[1] for k, v in blocks['Palette'][1].items()}
        self.by_id = {v: k for k, v in self.palette.items()}
        self.cells = N.varints(blocks['Data'][1], self.w * self.h * self.l)
        self.changes = collections.Counter()

    def idx(self, x, y, z):
        return x + z * self.w + y * self.w * self.l

    def get(self, x, y, z):
        if not (0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.l):
            return 'minecraft:air'
        return self.by_id.get(self.cells[self.idx(x, y, z)], 'minecraft:air')

    def name(self, x, y, z):
        return self.get(x, y, z).split('[')[0]

    def air(self, x, y, z):
        return self.name(x, y, z) in AIRS

    def put(self, x, y, z, block, replace=False, **props):
        if not replace and not self.air(x, y, z):
            return False
        state = block if ':' in block else M + block
        if props:
            state += '[' + ','.join('%s=%s' % (k, props[k]) for k in sorted(props)) + ']'
        if state not in self.palette:
            pid = max(self.palette.values()) + 1
            self.palette[state] = pid
            self.by_id[pid] = state
        self.cells[self.idx(x, y, z)] = self.palette[state]
        self.changes[state.split('[')[0]] += 1
        return True

    def save(self, path):
        blocks = self.s['Blocks'][1]
        blocks['Palette'] = (N.COMPOUND, {k: (N.INT, v) for k, v in self.palette.items()})
        blocks['Data'] = (N.BYTE_ARRAY, N.encode_varints(self.cells))
        if 'PaletteMax' in blocks:
            blocks['PaletteMax'] = (N.INT, len(self.palette))
        N.write(path, self.root_name, self.root)


def hall_footprint(sc, y=SEED[1]):
    """(x, z) cells of the hall interior at height y: flood fill through air from the seed column,
    staying under a roof (open sky means we walked out of a door)."""
    def roofed(x, z):
        return any(not sc.air(x, yy, z) for yy in range(y + 2, y + 18))

    start = (SEED[0], SEED[2])
    seen = {start}
    queue = collections.deque([start])
    while queue:
        x, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if (nx, nz) in seen or not (30 <= nx <= 80 and 18 <= nz <= 66):
                continue
            if sc.air(nx, y, nz) and roofed(nx, nz):
                seen.add((nx, nz))
                queue.append((nx, nz))
    return seen


def furnish(sc, layout='bare'):
    hall = hall_footprint(sc)
    report = {'hall cells': len(hall)}
    y = FLOOR_Y

    # --- house tables -----------------------------------------------------------------------------
    # The builder's tables are single planks on a 45-degree line, touching only at their corners, so
    # as Wizard Tables each one stood alone on four legs and the hall read as a field of stools.
    # Filling the inside corner of every step turns each line into one stepped band of joined table:
    # one long top, legs only at the open corners. The band grows toward the wider gap beside it.
    line = {(x, z) for (x, z) in hall
            if sc.name(x, y, z) == 'minecraft:oak_planks' and (x + z) in TABLE_LINES and x - z < DAIS_FROM}
    tables = set(line)
    for (x, z) in line:
        k = x + z
        if (x + 1, z - 1) in line:
            fill = (x + 1, z) if BAND_SIDE[k] > 0 else (x, z - 1)
            if fill in hall and sc.air(fill[0], y, fill[1]) and not sc.air(fill[0], y - 1, fill[1]):
                tables.add(fill)
    for (x, z) in tables:
        sc.put(x, y, z, 'wizard_table', replace=True,
               north=str((x, z - 1) in tables).lower(), south=str((x, z + 1) in tables).lower(),
               west=str((x - 1, z) in tables).lower(), east=str((x + 1, z) in tables).lower())
    report['table cells'] = len(tables)

    # --- benches: only where a gap is two cells wide, one row facing each table ------------------------
    benches = 0
    if layout == 'benches':
        for (x, z) in sorted(hall):
            if (x + z) not in BENCH_ROWS or (x, z) in tables or x - z >= DAIS_FROM:
                continue
            if not sc.air(x, y, z) or sc.air(x, y - 1, z):
                continue
            for (dx, dz, facing) in ((0, -1, 'north'), (0, 1, 'south'), (-1, 0, 'west'), (1, 0, 'east')):
                if (x + dx, z + dz) in tables:
                    benches += sc.put(x, y, z, 'great_hall_bench', facing=facing)
                    break
    report['benches'] = benches

    # --- floating candles, high under the roof --------------------------------------------------------
    candles = 0
    for (x, z) in sorted(hall):
        if (x + 2 * z) % 7 == 0:
            if all(sc.air(x, CANDLE_Y + d, z) for d in (-1, 0, 1, 2)):
                candles += sc.put(x, CANDLE_Y, z, 'floating_candle')
    report['floating candles'] = candles

    # --- hanging banners on the long walls ----------------------------------------------------------
    # Searched at banner height: the long walls step inward with stair blocks lower down, so the cells
    # right against them are only open from y = 39 up.
    spots = []
    for (x, z) in hall_footprint(sc, 39):
        if not (sc.air(x, 39, z) and sc.air(x, 40, z)) or x - z >= DAIS_FROM - 2:
            continue
        for (dx, dz, facing) in ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north')):
            if all(sc.name(x + dx, yy, z + dz) == 'minecraft:cobblestone' for yy in (39, 40)):
                spots.append((x, z, facing))
                break
    # One wall at a time, every third spot along it, houses in turn.
    banners = 0
    for wall in ('east', 'west', 'south', 'north'):
        along = sorted((s for s in spots if s[2] == wall), key=lambda s: s[0] - s[1])
        for (x, z, facing) in along[1::3]:
            house = HOUSES[banners % 4]
            if sc.put(x, 39, z, house + '_hanging_banner', facing=facing, half='lower'):
                sc.put(x, 40, z, house + '_hanging_banner', facing=facing, half='upper')
                banners += 1
    report['banner spots found'] = len(spots)
    report['banners'] = banners

    # --- house points hourglasses on the dais ---------------------------------------------------------
    dais = [(x, z) for (x, z) in hall
            if x - z >= DAIS_FROM and sc.air(x, 38, z) and sc.air(x, 39, z) and not sc.air(x, 37, z)]
    dais.sort(key=lambda c: (-(c[0] - c[1]), c[0]))
    chosen = []
    for c in dais:
        if all(max(abs(c[0] - o[0]), abs(c[1] - o[1])) >= 2 for o in chosen):
            chosen.append(c)
        if len(chosen) == 4:
            break
    chosen.sort(key=lambda c: c[0] + c[1])
    for house, (x, z) in zip(HOUSES, chosen):
        sc.put(x, 38, z, house + '_points_hourglass', facing='south')
    report['dais cells'] = len(dais)
    report['hourglasses'] = len(chosen)

    # --- the library building and the astronomy room ------------------------------------------------
    report['library pieces'] = furnish_rooms(sc, LIBRARY_FLOORS, LIBRARY_BOX, LIBRARY_KIT)
    report['astronomy pieces'] = furnish_rooms(sc, ASTRONOMY_FLOORS, ASTRONOMY_BOX, ASTRONOMY_KIT)
    return report


# The two-storey building with the bookshelves, west of the hall (floors stand at y = 26 and 32), and
# the small room high in the north tower (y = 72). Boxes are inclusive (x0, z0, x1, z1).
LIBRARY_FLOORS = (26, 32)
LIBRARY_BOX = (27, 101, 49, 105)   # inside the walls at z = 100 and z = 106
LIBRARY_KIT = ['writing_desk', 'enchanted_book_pile', 'scroll_stack', 'candle_cluster', 'school_trunk']
ASTRONOMY_FLOORS = (72,)
ASTRONOMY_BOX = (28, 114, 38, 124)
ASTRONOMY_KIT = ['brass_telescope', 'brass_telescope', 'scroll_stack']

DIRS = ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north'))


def furnish_rooms(sc, floors, box, kit):
    """Stand each piece of the kit against a wall, facing into the room, one per room per floor in
    turn. A spot needs a solid block on one side, solid floor, and two blocks of air. Doorways are
    kept clear: no piece goes next to a door or in front of one."""
    x0, z0, x1, z1 = box
    placed = 0
    for y in floors:
        spots = []
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if not (sc.air(x, y, z) and sc.air(x, y + 1, z)) or sc.air(x, y - 1, z):
                    continue
                if all(sc.air(x, yy, z) for yy in range(y + 2, y + 8)):
                    continue  # no roof: outside, or on a balcony
                if any('door' in sc.name(x + dx, y, z + dz) for dx, dz, _ in DIRS):
                    continue
                for dx, dz, facing in DIRS:
                    behind = sc.name(x + dx, y, z + dz)
                    if behind not in AIRS and ('cobblestone' in behind or 'stone' in behind
                                               or 'planks' in behind or 'bookshelf' in behind):
                        spots.append((x, z, facing))
                        break
        used = []
        k = 0
        for (x, z, facing) in spots:
            if k >= len(kit) * 2:
                break
            if any(max(abs(x - ux), abs(z - uz)) < 3 for ux, uz in used):
                continue
            if sc.put(x, y, z, kit[k % len(kit)], facing=facing) if kit[k % len(kit)] not in (
                    'enchanted_book_pile', 'candle_cluster') else sc.put(x, y, z, kit[k % len(kit)]):
                used.append((x, z))
                placed += 1
                k += 1
    return placed


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--dry-run', action='store_true', help='report what would be placed, write nothing')
    ap.add_argument('--out', default=SCHEM)
    ap.add_argument('--src', default=SCHEM)
    ap.add_argument('--layout', choices=('bare', 'benches'), default='bare',
                    help='bare: joined tables only (chosen: benches on the diagonal rows blur the tables into one mass)')
    args = ap.parse_args()

    sc = Schematic(args.src)
    if any(k.startswith(M + 'floating_candle') for k in sc.palette):
        sys.exit('mini_hogwarts.schem is already furnished (floating candles in its palette)')
    report = furnish(sc, args.layout)
    for k, v in report.items():
        print('  %-20s %d' % (k, v))
    for k, v in sorted(sc.changes.items()):
        print('  placed %-44s %d' % (k, v))
    if not args.dry_run:
        sc.save(args.out)
        print('wrote', os.path.relpath(args.out, ROOT))


if __name__ == '__main__':
    main()
