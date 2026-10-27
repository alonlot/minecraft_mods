"""Builds the Battle of Hogwarts set, structure/hogwarts_battle.nbt, cut from the user's Hogwarts world save ("Best
Hogwarts made in Minecraft", Minecraft 26.3, DataVersion 5023; licence unknown: keep it for the user's own use, like
great_hall.nbt).

    python -I build_scripts/pensieve_scenes/hogwarts_battle_set.py [world_dir]

The world defaults to C:/Users/alonl/Downloads/Best Hogwarts made in Minecraft, the extracted save (only its
dimensions/minecraft/overworld/region files are read; nothing from it is run); pass another folder if it lives
elsewhere. The strip cut is world x 250..620, y 0..140, z 250..440: the Paved Courtyard
(world ~265..335, 290..400), the Duelling Club range, the Clock Tower and its courtyard (380..440, 315..375, the
fountain at ~405, 342) and the covered wooden bridge east over the ravine (440..565, z ~340..348, floor of the ravine
near y 3) onto the grounds toward Hagrid's (to x 620).

Scene frame: world (430, 50, 345) is the scene origin, so the courtyards' paving (world block y 49) is scene y -1 and
everyone on it stands at y 0. TEMPLATE_AT is the template's min corner in scene coordinates.

Only what can be seen is kept: air is flood-filled from the top of the box (non-full blocks such as glass, slabs and
leaves let it through, the box's sides do not), and a block is kept if it is a non-full block in that air or a full
block touching it. That drops the buried rock and the caves (1.96M blocks read, ~443k kept).

Blocks the 26.x save has and 1.21 does not are mapped in MAP (to the nearest 1.21 block; properties are dropped where
the shapes differ). Block entities (sign text, banner patterns, head skins) are not carried: the template has none.
"""
import collections
import gzip
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from anvil import read_region_box  # noqa: E402
from scene_common import STRUCTURES  # noqa: E402
from great_hall_set import write_template  # noqa: E402

WORLD = r'C:/Users/alonl/Downloads/Best Hogwarts made in Minecraft'
BOX = (250, 0, 250, 620, 140, 440)         # world x0, y0, z0, x1, y1, z1 (inclusive)
ORIGIN = (430, 50, 345)                    # world cell that is scene (0, 0, 0)
TEMPLATE_AT = (BOX[0] - ORIGIN[0], BOX[1] - ORIGIN[1], BOX[2] - ORIGIN[2])
SIZE = (BOX[3] - BOX[0] + 1, BOX[4] - BOX[1] + 1, BOX[5] - BOX[2] + 1)
OUT = os.path.join(STRUCTURES, 'hogwarts_battle.nbt')

# 26.x blocks -> 1.21: (new id, keep properties?)
MAP = {
    'minecraft:bush': ('minecraft:fern', False),
    'minecraft:cactus_flower': (None, False),
    'minecraft:iron_chain': ('minecraft:chain', True),
    'minecraft:leaf_litter': (None, False),
    'minecraft:oak_shelf': ('minecraft:oak_slab', False),
    'minecraft:oxidized_lightning_rod': ('minecraft:lightning_rod', True),
    'minecraft:pale_moss_carpet': ('minecraft:moss_carpet', False),
    'minecraft:pale_oak_fence': ('minecraft:dark_oak_fence', True),
    'minecraft:potted_pale_oak_sapling': ('minecraft:potted_dark_oak_sapling', False),
    'minecraft:potted_poplar_sapling': ('minecraft:potted_birch_sapling', False),
    'minecraft:tall_dry_grass': ('minecraft:dead_bush', False),
    'minecraft:waxed_copper_chest': ('minecraft:chest', True),
}

# Blocks that are not a full opaque cube: air flows through them in the visibility flood.
NONFULL = ('slab', 'stairs', 'wall', 'fence', 'pane', 'glass', 'leaves', 'door', 'trapdoor', 'torch', 'lantern',
           'chain', 'carpet', 'button', 'lever', 'sign', 'banner', 'bars', 'rail', 'grass', 'fern', 'flower',
           'sapling', 'vine', 'ladder', 'candle', 'pot', 'head', 'skull', 'bed', 'chest', 'plate', 'scaffolding',
           'water', 'lava', 'snow', 'layer', 'rod', 'bush', 'litter', 'moss_carpet', 'shelf', 'petals', 'tulip',
           'poppy', 'dandelion', 'orchid', 'allium', 'bluet', 'daisy', 'cornflower', 'lily', 'rose', 'peony', 'lilac',
           'cobweb', 'mushroom', 'sugar_cane', 'kelp', 'seagrass', 'lectern', 'anvil', 'bell', 'cauldron', 'campfire',
           'hopper', 'brewing', 'enchanting', 'end_rod', 'piston', 'cake', 'composter', 'grindstone', 'stonecutter',
           'dripleaf', 'azalea', 'frogspawn', 'pickle', 'coral', 'amethyst_cluster', 'bud', 'light', 'barrier',
           'spawner', 'beacon', 'conduit', 'ice', 'honey', 'slime')


def full(state):
    n = state.split('[')[0]
    return not any(k in n for k in NONFULL)


def mapped(state):
    name = state.split('[')[0]
    if name not in MAP:
        return state
    new, keep = MAP[name]
    if new is None:
        return None
    return new + (state[len(name):] if keep else '')


def visible(cells):
    """The cells of `cells` (world coordinates) that an eye in the open air above could see."""
    x0, y0, z0, x1, y1, z1 = BOX
    nx, ny, nz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    solid = bytearray(nx * ny * nz)

    def idx(x, y, z):
        return ((y - y0) * nz + (z - z0)) * nx + (x - x0)
    for (x, y, z), s in cells.items():
        if full(s):
            solid[idx(x, y, z)] = 1
    seen = bytearray(nx * ny * nz)
    q = collections.deque()
    for z in range(z0, z1 + 1):
        for x in range(x0, x1 + 1):
            i = idx(x, y1, z)
            if not solid[i]:
                seen[i] = 1
                q.append(i)
    while q:
        i = q.popleft()
        a, r = i % nx, i // nx
        c, b = r % nz, r // nz
        for da, db, dc in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            aa, bb, cc = a + da, b + db, c + dc
            if 0 <= aa < nx and 0 <= bb < ny and 0 <= cc < nz:
                j = (bb * nz + cc) * nx + aa
                if not seen[j] and not solid[j]:
                    seen[j] = 1
                    q.append(j)
    keep = {}
    for (x, y, z), s in cells.items():
        if seen[idx(x, y, z)]:
            keep[(x, y, z)] = s
            continue
        a, b, c = x - x0, y - y0, z - z0
        for da, db, dc in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            aa, bb, cc = a + da, b + db, c + dc
            if 0 <= aa < nx and 0 <= bb < ny and 0 <= cc < nz and seen[(bb * nz + cc) * nx + aa]:
                keep[(x, y, z)] = s
                break
    return keep


def main():
    world = sys.argv[1] if len(sys.argv) > 1 else WORLD
    overworld = os.path.join(world, 'dimensions', 'minecraft', 'overworld')
    if not os.path.isdir(os.path.join(overworld, 'region')):
        overworld = world
    cells = read_region_box(overworld, *BOX)
    print('read', len(cells))
    if not cells:
        raise SystemExit('no blocks read from %s (pass the extracted world folder; the zip is not read)' % overworld)
    keep = visible(cells)
    grid = {}
    for (x, y, z), s in keep.items():
        m = mapped(s)
        if m is not None:
            grid[(x - BOX[0], y - BOX[1], z - BOX[2])] = m
    n_states, n_blocks = write_template(grid, SIZE, OUT)
    print('wrote', OUT, n_blocks, 'blocks,', n_states, 'states, size', SIZE, 'at', TEMPLATE_AT)


if __name__ == '__main__':
    main()
