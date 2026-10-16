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


def dump(o, ind=0):
    """Compact JSON: anything that fits in 150 columns on one line, otherwise one entry per line."""
    sp = '  ' * ind
    if isinstance(o, dict):
        flat = json.dumps(o, separators=(', ', ': '))
        if len(flat) + len(sp) <= 150:
            return flat
        items = ['%s  %s: %s' % (sp, json.dumps(k), dump(v, ind + 1)) for k, v in o.items()]
        return '{\n' + ',\n'.join(items) + '\n' + sp + '}'
    if isinstance(o, list):
        flat = json.dumps(o, separators=(', ', ': '))
        if len(flat) + len(sp) <= 150:
            return flat
        items = ['%s  %s' % (sp, dump(v, ind + 1)) for v in o]
        return '[\n' + ',\n'.join(items) + '\n' + sp + ']'
    return json.dumps(o)


def write_scene(scene, scene_id):
    """Writes memories/<scene_id>.json (LF line endings) and returns the text."""
    txt = dump(scene) + '\n'
    with open(os.path.join(MEMORIES, scene_id + '.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    return txt
