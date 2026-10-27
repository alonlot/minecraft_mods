"""The Battle of Hogwarts set's geometry for the scene generators: which cells are solid, where the ground is.

    import battle_ground as BG
    BG.ground(x, z)          feet y (scene) on the highest walkable surface at or below `below` (default: any)
    BG.clear(x, y, z)        a person fits at feet (x, y, z): the two cells above are free (plants are free)

Built once from structure/hogwarts_battle.nbt (hogwarts_battle_set.py) and cached in a pickle in __pycache__, keyed
on the template's mtime; use(name, at) switches to another set (the Great Hall) for a scene staged there.
"""
import math
import os
import pickle
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import template_grid, STRUCTURES  # noqa: E402
import hogwarts_battle_set as HB  # noqa: E402

NBT = os.path.join(STRUCTURES, 'hogwarts_battle.nbt')
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '__pycache__', 'battle_ground.pkl')
PASSABLE = ('air', 'short_grass', 'tall_grass', 'fern', 'flower', 'poppy', 'dandelion', 'tulip', 'allium', 'bluet',
            'daisy', 'cornflower', 'lily', 'dead_bush', 'sapling', 'vine', 'carpet', 'torch', 'button', 'minecraft:light',
            'minecraft:snow', 'sugar_cane', 'mushroom', 'pink_petals', 'sweet_berry', 'cobweb', 'rail', 'pressure_plate',
            'lever', 'sign', 'banner', 'water', 'seagrass', 'kelp', 'peony', 'rose_bush', 'lilac', 'glow_lichen')


def _load(name='hogwarts_battle', at=None):
    nbt = os.path.join(STRUCTURES, name + '.nbt')
    cache = CACHE.replace('battle_ground.pkl', 'ground_%s.pkl' % name)
    m = os.path.getmtime(nbt)
    if os.path.exists(cache):
        try:
            with open(cache, 'rb') as f:
                c = pickle.load(f)
            if c['mtime'] == m and c['at'] == tuple(at or HB.TEMPLATE_AT):
                return c
        except Exception:
            pass
    ax, ay, az = at or HB.TEMPLATE_AT
    solid, kinds = set(), {}
    for (x, y, z), b in template_grid(name).items():
        nm = b.split('{')[0]
        p = (x + ax, y + ay, z + az)
        kinds[p] = nm
        if not any(k in nm for k in PASSABLE):
            solid.add(p)
    cols = {}
    for (x, y, z) in solid:
        cols.setdefault((x, z), []).append(y)
    for k in cols:
        cols[k].sort()
    c = {'mtime': m, 'at': (ax, ay, az), 'solid': solid, 'cols': cols, 'kinds': kinds}
    os.makedirs(os.path.dirname(cache), exist_ok=True)
    with open(cache, 'wb') as f:
        pickle.dump(c, f, protocol=4)
    return c


def use(name, at):
    """Switch every lookup here to another set: template `name` placed with its min corner at `at` (scene coords)."""
    global SOLID, COLS, KINDS
    c = _load(name, at)
    SOLID, COLS, KINDS = c['solid'], c['cols'], c['kinds']


_C = _load()
SOLID = _C['solid']
COLS = _C['cols']
KINDS = _C['kinds']


def block(x, y, z):
    return KINDS.get((math.floor(x), math.floor(y), math.floor(z)))


def solid(x, y, z):
    return (math.floor(x), math.floor(y), math.floor(z)) in SOLID


def ground(x, z, below=1e9):
    """Feet y on the highest solid top in column (x, z) whose top is at or under `below` and has two free cells
    above it; -999 if none."""
    col = COLS.get((math.floor(x), math.floor(z)))
    if not col:
        return -999
    for y in reversed(col):
        if y + 1 > below:
            continue
        if (math.floor(x), y + 1, math.floor(z)) not in SOLID and (math.floor(x), y + 2, math.floor(z)) not in SOLID:
            return y + 1
    return -999


def clear(x, y, z):
    c = (math.floor(x), math.floor(y), math.floor(z))
    return c not in SOLID and (c[0], c[1] + 1, c[2]) not in SOLID
