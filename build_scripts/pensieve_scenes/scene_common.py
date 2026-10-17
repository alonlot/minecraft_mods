"""Shared helpers for the Pensieve scene generators in this folder.

Every generator resolves its paths from this file, so it runs from the repo root (or anywhere):
    python build_scripts/pensieve_scenes/<scene>.py
and rewrites src/main/resources/data/hogwartsmagic/memories/<scene>.json in place.
"""
import gzip
import json
import os
import struct

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
DATA = os.path.join(REPO, 'src', 'main', 'resources', 'data', 'hogwartsmagic')
MEMORIES = os.path.join(DATA, 'memories')
STRUCTURES = os.path.join(DATA, 'structure')


def read_nbt(data):
    """Parses an uncompressed NBT blob into plain Python values (compounds are dicts, lists are lists)."""
    pos = [0]

    def rd(fmt):
        n = struct.calcsize(fmt)
        v = struct.unpack_from('>' + fmt, data, pos[0]); pos[0] += n
        return v[0]

    def rstr():
        n = rd('H'); s = data[pos[0]:pos[0] + n].decode('utf-8'); pos[0] += n; return s

    def payload(t):
        if t == 1: return rd('b')
        if t == 2: return rd('h')
        if t == 3: return rd('i')
        if t == 4: return rd('q')
        if t == 5: return rd('f')
        if t == 6: return rd('d')
        if t == 7:
            n = rd('i'); v = data[pos[0]:pos[0] + n]; pos[0] += n; return v
        if t == 8: return rstr()
        if t == 9:
            et = rd('b'); n = rd('i'); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = rd('b')
                if tt == 0: return d
                k = rstr(); d[k] = payload(tt)
        if t == 11:
            n = rd('i'); return [rd('i') for _ in range(n)]
        if t == 12:
            n = rd('i'); return [rd('q') for _ in range(n)]
        raise ValueError(t)
    t = rd('b'); rstr(); return payload(t)


def template_grid(name):
    """{(x, y, z): 'namespace:block' + str(properties) when it has any} for structure/<name>.nbt, air included."""
    root = read_nbt(gzip.open(os.path.join(STRUCTURES, name + '.nbt')).read())
    pal = root['palette'] if 'palette' in root else root['palettes'][0]
    names = [p['Name'] + (str(p.get('Properties', '')) if 'Properties' in p else '') for p in pal]
    grid = {}
    for b in root['blocks']:
        x, y, z = b['pos']
        grid[(x, y, z)] = names[b['state']]
    return grid


SCHEMATICS = os.path.join(DATA, 'schematic')


def schematic_grid(name):
    """(width, height, length, cells) for schematic/<name>.schem (Sponge v2/v3): cells[x + z*W + y*W*L] is the
    block id without properties ('minecraft:air' included), the same flat order MiniHogwartsSchematic reads."""
    root = read_nbt(gzip.open(os.path.join(SCHEMATICS, name + '.schem')).read())
    s = root.get('Schematic', root)
    w, h, l = s['Width'] & 0xFFFF, s['Height'] & 0xFFFF, s['Length'] & 0xFFFF
    b = s['Blocks'] if 'Blocks' in s else s
    pal = {v: k.split('[')[0] for k, v in b['Palette'].items()}
    data = b['Data'] if 'Data' in b else b['BlockData']
    cells, i = [], 0
    while i < len(data) and len(cells) < w * h * l:
        v = sh = 0
        while True:
            c = data[i] & 0xFF
            i += 1
            v |= (c & 0x7F) << sh
            if not c & 0x80:
                break
            sh += 7
        cells.append(pal.get(v, 'minecraft:air'))
    cells += ['minecraft:air'] * (w * h * l - len(cells))
    return w, h, l, cells


def rotate_xz(x, z, w, l, degrees):
    """Where schematic cell (x, z) lands in its rotated footprint (offset from the min corner); the same as
    SetBuilder.rotateXZ: clockwise from above, 90 maps (x, z) to (l-1-z, x)."""
    if degrees == 90:
        return l - 1 - z, x
    if degrees == 180:
        return w - 1 - x, l - 1 - z
    if degrees == 270:
        return z, w - 1 - x
    return x, z


# cells SetBuilder never places from a schematic
SCHEMATIC_SKIPPED = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:structure_void',
                     'minecraft:jigsaw', 'minecraft:structure_block', 'minecraft:command_block',
                     'minecraft:chain_command_block', 'minecraft:repeating_command_block'}


def dump(o, ind=0, width=150):
    """Compact JSON: anything that fits in width columns on one line, otherwise one entry per line."""
    sp = '  ' * ind
    if isinstance(o, dict):
        flat = json.dumps(o, separators=(', ', ': '))
        if len(flat) + len(sp) <= width:
            return flat
        items = ['%s  %s: %s' % (sp, json.dumps(k), dump(v, ind + 1, width)) for k, v in o.items()]
        return '{\n' + ',\n'.join(items) + '\n' + sp + '}'
    if isinstance(o, list):
        flat = json.dumps(o, separators=(', ', ': '))
        if len(flat) + len(sp) <= width:
            return flat
        items = ['%s  %s' % (sp, dump(v, ind + 1, width)) for v in o]
        return '[\n' + ',\n'.join(items) + '\n' + sp + ']'
    return json.dumps(o)


def write_scene(scene, scene_id, width=150):
    """Writes memories/<scene_id>.json (LF line endings) and returns the text; width as for dump."""
    txt = dump(scene, 0, width) + '\n'
    with open(os.path.join(MEMORIES, scene_id + '.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    return txt


# ---------------------------------------------------------------- physics self-check
# The set is placed without neighbour updates and the server clears the ticks it schedules, but a set must not
# rely on that alone: a gravity block over nothing, or water beside an empty cell, is a set that collapses or
# floods the moment anything updates it. world maps scene cell -> block id (no properties); a missing cell is
# empty in the memory level (SetBuilder skips template air, and nothing exists outside the set).
FREE = {None, 'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air', 'minecraft:light', 'minecraft:water',
        'minecraft:lava', 'minecraft:fire', 'minecraft:soul_fire', 'minecraft:bubble_column',
        'minecraft:short_grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern',
        'minecraft:dead_bush', 'minecraft:vine', 'minecraft:snow', 'minecraft:seagrass', 'minecraft:tall_seagrass'}
WET = {'minecraft:water', 'minecraft:lava', 'minecraft:bubble_column', 'minecraft:seagrass',
       'minecraft:tall_seagrass'}
GRAVITY = {'minecraft:gravel', 'minecraft:sand', 'minecraft:red_sand', 'minecraft:suspicious_sand',
           'minecraft:suspicious_gravel', 'minecraft:anvil', 'minecraft:chipped_anvil', 'minecraft:damaged_anvil',
           'minecraft:dragon_egg'}


def is_gravity(block):
    return block in GRAVITY or (block is not None and block.endswith('_concrete_powder'))


def unsupported_gravity(world):
    """Gravity blocks (gravel, sand, concrete powder, anvils) with a free cell under them."""
    return sorted(c for c, b in world.items() if is_gravity(b) and world.get((c[0], c[1] - 1, c[2])) in FREE)


def water_leaks(world, radius):
    """(water cell, open neighbour) pairs: water beside or above an empty cell, or at the edge of the set."""
    leaks = []
    for c, b in world.items():
        if b != 'minecraft:water':
            continue
        x, y, z = c
        for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
            n = (x + d[0], y + d[1], z + d[2])
            if abs(n[0]) > radius or abs(n[2]) > radius:
                leaks.append((c, n))
                continue
            nb = world.get(n)
            if nb in FREE and nb not in WET:
                leaks.append((c, n))
    return leaks


def physics_problems(world, radius):
    """Problem strings for the generator's problem list (empty when the set is stable)."""
    out = []
    g = unsupported_gravity(world)
    if g:
        out.append('%d unsupported gravity blocks, e.g. %s' % (len(g), g[:5]))
    w = water_leaks(world, radius)
    if w:
        out.append('%d open water edges, e.g. %s' % (len(w), w[:5]))
    return out
