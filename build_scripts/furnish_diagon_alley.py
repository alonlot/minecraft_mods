"""Furnish the Diagon Alley shops with the mod's decorations.

    python build_scripts/furnish_diagon_alley.py [--dry-run]

Edits src/main/resources/data/hogwartsmagic/structure/diagon_alley.nbt in place, and refuses to run
twice on the same file.

Why it works on the written template
-----------------------------------
generate_diagon_alley.py calls furnish_file() as its last step. Working on the finished template keeps
this independent of how the street is laid out: every shop is found from the shopkeeper baked into it
(each keeper is a named villager standing right behind his counter), and the counter, the side walls
and the back of the shop are read off the blocks. Nothing is placed by fixed coordinate.

Run on its own, it furnishes the committed template, and refuses if it is already furnished.
"""
import argparse
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt_typed as N  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEMPLATE = os.path.join(ROOT, 'src/main/resources/data/hogwartsmagic/structure/diagon_alley.nbt')
M = 'hogwartsmagic:'
AIRS = ('minecraft:air', 'minecraft:cave_air')
FLOOR = 2   # a person stands at y = 2 inside the shops (floor blocks are y = 1)

# Keeper name -> what goes in that shop. 'wall:' pieces hang on a side wall one block up; 'front:'
# pieces stand on the customer side of the counter; the rest stand against a wall behind the counter.
KITS = {
    'Garrick Ollivander': ['front:wizard_chair', 'scroll_stack', 'candle_cluster', 'writing_desk'],
    'Mr Fenwick': ['wall:broom_rack', 'wall:broom_rack', 'quidditch_cup', 'hoop', 'school_trunk'],
    'Old Barnaby': ['wall:broom_rack', 'wall:broom_rack', 'wall:broom_rack', 'scroll_stack'],
    'Mr Jiggers': ['wall:potion_shelf', 'wall:potion_shelf', 'wall:potion_shelf', 'candle_cluster'],
    'Mr Mulpepper': ['wall:potion_shelf', 'wall:potion_shelf', 'wall:potion_shelf', 'candle_cluster'],
    'Mr Potage': ['wizard_cauldron', 'wizard_cauldron', 'wizard_cauldron', 'candle_cluster'],
    'Mr Blotts': ['writing_desk', 'scroll_stack', 'enchanted_book_pile', 'enchanted_book_pile',
                  'ceiling:floating_candle', 'ceiling:floating_candle'],
    'Mr Scribbulus': ['writing_desk', 'scroll_stack', 'scroll_stack', 'candle_cluster'],
    'Mr Wiseacre': ['brass_telescope', 'brass_telescope', 'school_trunk', 'wizard_chess_board'],
    'Eeylops': ['owl_cage', 'owl_cage', 'owl_cage'],
    'Miss Prewett': ['owl_cage', 'owl_cage'],
    'Madam Malkin': ['school_trunk', 'school_trunk', 'front:wizard_chair'],
    'Mr Twilfitt': ['common_room_armchair', 'front:wizard_chair'],
    'Florean Fortescue': ['cafe', 'cafe', 'candle_cluster'],
    'Mr Gambol': ['wizard_chess_board', 'candle_cluster'],
    'George Weasley': ['wizard_chess_board'],
    'Mr Borgin': ['candle_cluster', 'wall:potion_shelf'],
}
NO_FACING = ('candle_cluster', 'enchanted_book_pile', 'floating_candle', 'wizard_cauldron', 'wizard_table')
OPPOSITE = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}
DIRS = ((-1, 0, 'east'), (1, 0, 'west'), (0, -1, 'south'), (0, 1, 'north'))  # (offset to wall, facing)


class Template:
    def __init__(self, path):
        self.root_name, self.root = N.read(path)
        r = self.root
        self.palette = []
        for e in r['palette'][1][1]:
            name = e['Name'][1]
            props = {k: v[1] for k, v in e.get('Properties', (N.COMPOUND, {}))[1].items()}
            self.palette.append((name, props))
        self.blocks = r['blocks'][1][1]
        self.at = {tuple(b['pos'][1][1]): i for i, b in enumerate(self.blocks)}
        self.changes = collections.Counter()

    def name(self, x, y, z):
        i = self.at.get((x, y, z))
        return 'minecraft:air' if i is None else self.palette[self.blocks[i]['state'][1]][0]

    def air(self, x, y, z):
        return self.name(x, y, z) in AIRS

    def solid(self, x, y, z):
        n = self.name(x, y, z)
        if n in AIRS:
            return False
        partial = ('slab', 'stairs', 'carpet', 'fence', 'pane', 'sign', 'door', 'glass', 'lantern', 'chain',
                   'wall', 'ladder', 'bars', 'cobweb', 'torch', 'trapdoor', 'button', 'campfire', M)
        return not any(p in n for p in partial)

    def state_id(self, name, props):
        key = (name, props)
        for i, p in enumerate(self.palette):
            if p == key:
                return i
        self.palette.append(key)
        return len(self.palette) - 1

    def put(self, x, y, z, block, **props):
        if not self.air(x, y, z):
            return False
        name = block if ':' in block else M + block
        sid = self.state_id(name, dict(sorted(props.items())))
        i = self.at.get((x, y, z))
        if i is None:
            self.blocks.append({'pos': (N.LIST, (N.INT, [x, y, z])), 'state': (N.INT, sid)})
            self.at[(x, y, z)] = len(self.blocks) - 1
        else:
            self.blocks[i]['state'] = (N.INT, sid)
        self.changes[name] += 1
        return True

    def set_props(self, x, y, z, **props):
        """Change the properties of the block already at (x, y, z)."""
        i = self.at.get((x, y, z))
        name, old = self.palette[self.blocks[i]['state'][1]]
        merged = dict(old)
        merged.update(props)
        self.blocks[i]['state'] = (N.INT, self.state_id(name, dict(sorted(merged.items()))))
        self.changes[name + ' (turned)'] += 1

    def save(self, path):
        pal = []
        for name, props in self.palette:
            e = {'Name': (N.STRING, name)}
            if props:
                e['Properties'] = (N.COMPOUND, {k: (N.STRING, v) for k, v in props.items()})
            pal.append(e)
        self.root['palette'] = (N.LIST, (N.COMPOUND, pal))
        self.root['blocks'] = (N.LIST, (N.COMPOUND, self.blocks))
        N.write(path, self.root_name, self.root)


def keepers(t):
    out = []
    for e in t.root['entities'][1][1]:
        nbt = e['nbt'][1]
        if nbt['id'][1] != 'minecraft:villager' or 'CustomName' not in nbt:
            continue
        name = nbt['CustomName'][1].split('"text":"')[1].split('"')[0]
        x, y, z = (int(v // 1) for v in e['pos'][1][1])
        out.append((name, x, y, z))
    return out


def shop_room(t, kx, kz):
    """-> (x0, x1, counter_z, back_step, front_z) for the shop whose keeper stands at (kx, FLOOR, kz).
    back_step is +1 or -1: the way from the counter toward the back of the shop."""
    if 'dark_oak' in t.name(kx + 1, FLOOR, kz + 1) or 'dark_oak' in t.name(kx - 1, FLOOR, kz + 1):
        counter, step = kz + 1, -1
    else:
        counter, step = kz - 1, 1
    x0 = kx
    while t.air(x0 - 1, FLOOR + 1, kz) and kx - x0 < 12:
        x0 -= 1
    x1 = kx
    while t.air(x1 + 1, FLOOR + 1, kz) and x1 - kx < 12:
        x1 += 1
    # The customer side runs from the counter to the shop front.
    front = counter - step
    while t.air(kx, FLOOR + 1, front - step) and abs(front - counter) < 4:
        front -= step
    return x0, x1, counter, step, front


def furnish_shop(t, name, kx, kz, kit):
    x0, x1, counter, step, front = shop_room(t, kx, kz)
    keep_clear = {(kx + dx, kz + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    used = []
    placed = 0

    def free(x, z, y=FLOOR):
        return (x, z) not in keep_clear and x != kx and all(
            max(abs(x - ux), abs(z - uz)) >= 2 for ux, uz in used) and t.air(x, y, z) and t.air(x, y + 1, z)

    cells = [(x, z) for z in range(kz, kz + step * 9, step) for x in range(x0, x1 + 1)]

    def spread(candidates):
        """Candidates ordered so the first one is farthest from the keeper and from every piece
        already placed: the kit spreads round the room instead of piling up by the counter."""
        anchors = used + [(kx, kz)]
        return sorted(candidates, key=lambda c: (-min(max(abs(c[0] - a[0]), abs(c[1] - a[1]))
                                                       for a in anchors), c))

    # The wand and broom benches gained a facing; the template has none, so they all face north.
    # Turn each one to face the counter, the way the keeper works at it.
    toward_counter = 'south' if step < 0 else 'north'
    for x, z in cells:
        for y in (FLOOR, FLOOR + 1):
            if t.name(x, y, z) in (M + 'wand_workbench', M + 'broom_workbench'):
                t.set_props(x, y, z, facing=toward_counter)

    for piece in kit:
        behind = spread(cells)
        kind, _, block = piece.rpartition(':')
        done = False
        if kind == 'wall':
            for x, z in behind:
                for wx, facing in ((x0 - 1, 'east'), (x1 + 1, 'west')):
                    if x == wx + (1 if facing == 'east' else -1) and t.solid(wx, FLOOR + 1, z) \
                            and free(x, z, FLOOR + 1):
                        done = t.put(x, FLOOR + 1, z, block, facing=facing)
                        break
                if done:
                    used.append((x, z))
                    break
        elif kind == 'ceiling':
            for x, z in behind:
                if free(x, z, FLOOR + 2) and t.air(x, FLOOR + 3, z) and not t.air(x, FLOOR + 4, z):
                    done = t.put(x, FLOOR + 2, z, block)
                    if done:
                        used.append((x, z))
                        break
        elif kind == 'front':
            for x in (x0, x1):
                z = front
                if free(x, z) and not t.air(x, FLOOR - 1, z):
                    done = t.put(x, FLOOR, z, block, facing='north' if step < 0 else 'south')  # toward the counter
                    if done:
                        used.append((x, z))
                        break
        elif block == 'cafe':
            # A table with a chair either side of it, both facing it.
            for x, z in behind:
                if all(free(x + dx, z) and not t.air(x + dx, FLOOR - 1, z) for dx in (-1, 0, 1)) \
                        and x - 1 > x0 - 1 and x + 1 < x1 + 1:
                    t.put(x, FLOOR, z, 'wizard_table', north='false', south='false', east='false', west='false')
                    t.put(x - 1, FLOOR, z, 'wizard_chair', facing='east')
                    done = t.put(x + 1, FLOOR, z, 'wizard_chair', facing='west')
                    used.extend([(x - 1, z), (x, z), (x + 1, z)])
                    break
        elif block == 'hoop':
            for x, z in behind:
                if all(free(x, z, y) for y in (FLOOR, FLOOR + 1)) and t.air(x, FLOOR + 2, z) \
                        and not t.air(x, FLOOR - 1, z) and x in (x0, x1):
                    t.put(x, FLOOR, z, 'quidditch_hoop_pole', facing='north')
                    t.put(x, FLOOR + 1, z, 'quidditch_hoop_pole', facing='north')
                    done = t.put(x, FLOOR + 2, z, 'quidditch_hoop', facing='east')
                    used.append((x, z))
                    break
        else:
            for x, z in behind:
                if not free(x, z) or t.air(x, FLOOR - 1, z):
                    continue
                for dx, dz, facing in DIRS:
                    if t.solid(x + dx, FLOOR, z + dz) or t.solid(x + dx, FLOOR + 1, z + dz):
                        if block in NO_FACING:
                            done = t.put(x, FLOOR, z, block)
                        elif block == 'wizard_chair':
                            # A chair beside the table just placed, facing it.
                            done = t.put(x, FLOOR, z, block, facing=facing)
                        else:
                            done = t.put(x, FLOOR, z, block, facing=facing)
                        break
                if done:
                    used.append((x, z))
                    break
        placed += bool(done)
    return placed, (x0, x1, counter, step)


def furnish_file(path, out=None, quiet=False, dry_run=False):
    """Furnish the template at `path` (written to `out`, default the same file). Returns the count."""
    t = Template(path)
    if any(name == M + 'broom_rack' for name, _ in t.palette):
        raise SystemExit('%s is already furnished (broom racks in its palette)' % os.path.basename(path))
    total = 0
    for name, kx, ky, kz in keepers(t):
        kit = KITS.get(name)
        if not kit:
            continue
        placed, room = furnish_shop(t, name, kx, kz, kit)
        total += placed
        if not quiet:
            print('  %-20s %d of %d   room x %d..%d, counter z %d, back %+d' % (name, placed, len(kit), *room))
    if not quiet:
        for k, v in sorted(t.changes.items()):
            print('  placed %-40s %d' % (k, v))
    if not dry_run:
        t.save(out or path)
    print('  furnished %d pieces in %s' % (sum(t.changes.values()), os.path.basename(out or path)))
    return total


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--dry-run', action='store_true')
    ap.add_argument('--out', default=TEMPLATE)
    args = ap.parse_args()

    furnish_file(TEMPLATE, args.out, dry_run=args.dry_run)


if __name__ == '__main__':
    main()
