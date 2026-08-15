"""Pull exactly the assets one structure needs out of the game jar and the mod, for the viewer.

    python export_assets.py <file.nbt> --jar <client-extra.jar> --out <dir> [--mod <assets dir>]

deepslate can render real block models with real textures, but it needs feeding: the blockstate
JSON for every block, every model in every parent chain those reference, and a bitmap for every
texture. Shipping all of it would be 1062 blockstates, 2137 models and 1061 PNGs. A structure uses
a few dozen, so this walks out from the blocks actually present and takes only those.

The mod's own assets are searched first, so `hogwartsmagic:candle_cluster` renders as itself rather
than as a missing-texture cube -- which is the whole reason to do this locally instead of uploading
the file to a web viewer.
"""

import argparse
import json
import os
import shutil
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from render_structure import read_template                      # noqa: E402


class Assets:
    """Vanilla jar plus an optional mod assets tree, searched mod-first."""

    def __init__(self, jar, mod_dir=None):
        self.zip = zipfile.ZipFile(jar)
        self.names = set(self.zip.namelist())
        self.mod_dir = mod_dir

    def _paths(self, namespace, kind, path, ext):
        rel = 'assets/%s/%s/%s%s' % (namespace, kind, path, ext)
        if self.mod_dir and namespace != 'minecraft':
            local = os.path.join(self.mod_dir, kind, path.replace('/', os.sep) + ext)
            if os.path.exists(local):
                return ('file', local)
        return ('zip', rel) if rel in self.names else (None, rel)

    def json(self, namespace, kind, path):
        where, loc = self._paths(namespace, kind, path, '.json')
        if where == 'file':
            with open(loc, encoding='utf-8') as fh:
                return json.load(fh)
        if where == 'zip':
            return json.loads(self.zip.read(loc))
        return None

    def png(self, namespace, path):
        where, loc = self._paths(namespace, 'textures', path, '.png')
        if where == 'file':
            with open(loc, 'rb') as fh:
                return fh.read()
        if where == 'zip':
            return self.zip.read(loc)
        return None


# Blocks drawn by a block-entity renderer rather than by a block model. Their textures live under
# textures/entity/, so walking the models never finds them and they come out as magenta cubes with
# nothing in the blockstate or model JSON to explain why. deepslate's SpecialRenderers wants these.
SPECIAL_TEXTURES = {
    'minecraft:chest': ['minecraft:entity/chest/normal'],
    'minecraft:trapped_chest': ['minecraft:entity/chest/trapped'],
    'minecraft:ender_chest': ['minecraft:entity/chest/ender'],
    'minecraft:conduit': ['minecraft:entity/conduit/base'],
    'minecraft:bell': ['minecraft:entity/bell/bell_body'],
    'minecraft:decorated_pot': ['minecraft:entity/decorated_pot/decorated_pot_base',
                                'minecraft:entity/decorated_pot/decorated_pot_side'],
    'minecraft:shulker_box': ['minecraft:entity/shulker/shulker'],
    # Fluids have no block model at all -- the renderer synthesises them from these two textures,
    # which are animated strips; fromBlobs takes the first frame, which is the right one.
    'minecraft:water': ['minecraft:block/water_still', 'minecraft:block/water_flow'],
    'minecraft:lava': ['minecraft:block/lava_still', 'minecraft:block/lava_flow'],
}


def split(ref, default_prefix=''):
    """'minecraft:block/cross' or 'block/cross' -> ('minecraft', 'block/cross')."""
    ns, _, path = ref.partition(':')
    if not path:
        ns, path = 'minecraft', ns
    if default_prefix and '/' not in path:
        path = default_prefix + path
    return ns, path


def model_refs(definition):
    """Every model id a blockstate definition can produce, variants and multipart alike."""
    out = []
    for entry in definition.get('variants', {}).values():
        for case in (entry if isinstance(entry, list) else [entry]):
            out.append(case['model'])
    for part in definition.get('multipart', []):
        apply = part.get('apply', {})
        for case in (apply if isinstance(apply, list) else [apply]):
            out.append(case['model'])
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument('nbt', nargs='+')
    p.add_argument('--jar', required=True)
    p.add_argument('--out', default=os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                                  'build'),
                   help='served directory (default: <skill>/textured/build)')
    p.add_argument('--mod', default=None, help='e.g. src/main/resources/assets/hogwartsmagic')
    p.add_argument('--mod-namespace', default='hogwartsmagic')
    args = p.parse_args()

    assets = Assets(args.jar, args.mod)

    # Every structure named goes into ONE build: a single merged atlas and one set of models, with
    # the viewer choosing between them by name. Exporting each separately would re-fetch the same
    # few dozen shared textures every time and leave a directory per structure to keep in step.
    structures = {}
    block_ids = set()
    for path in args.nbt:
        blocks, entities, size = read_template(path)
        name = os.path.basename(path)
        structures[name] = {'size': size,
                            'entities': [{'pos': [e[0], e[1], e[2]], 'id': e[3]} for e in entities]}
        block_ids |= {v[0] for v in blocks.values()}
    block_ids = sorted(block_ids)

    definitions, models, textures = {}, {}, {}
    missing = []

    for block in block_ids:
        if block == 'minecraft:air':
            continue
        ns, name = split(block)
        definition = assets.json(ns, 'blockstates', name)
        if definition is None:
            missing.append('blockstate ' + block)
            continue
        definitions[block] = definition

        for extra in SPECIAL_TEXTURES.get(block, []):
            tns, tpath = split(extra, 'block/')
            textures.setdefault('%s:%s' % (tns, tpath), (tns, tpath))

        # Walk every model this block can use, and every parent above them.
        queue = list(model_refs(definition))
        while queue:
            ref = queue.pop()
            mns, mpath = split(ref, 'block/')
            key = '%s:%s' % (mns, mpath)
            if key in models:
                continue
            model = assets.json(mns, 'models', mpath)
            if model is None:
                missing.append('model ' + key)
                continue
            models[key] = model
            if 'parent' in model:
                queue.append(model['parent'])
            for value in model.get('textures', {}).values():
                if value.startswith('#'):
                    continue                       # a slot reference; deepslate resolves it
                tns, tpath = split(value, 'block/')
                textures.setdefault('%s:%s' % (tns, tpath), (tns, tpath))

    os.makedirs(args.out, exist_ok=True)
    tex_dir = os.path.join(args.out, 'textures')
    if os.path.isdir(tex_dir):
        shutil.rmtree(tex_dir)
    os.makedirs(tex_dir)

    written = []
    for key, (tns, tpath) in sorted(textures.items()):
        data = assets.png(tns, tpath)
        if data is None:
            missing.append('texture ' + key)
            continue
        fname = key.replace(':', '__').replace('/', '_') + '.png'
        with open(os.path.join(tex_dir, fname), 'wb') as fh:
            fh.write(data)
        written.append((key, fname))

    payload = {
        'structures': structures,
        'blockstates': definitions,
        'models': models,
        'textures': dict(written),
    }
    with open(os.path.join(args.out, 'assets.json'), 'w', encoding='utf-8') as fh:
        json.dump(payload, fh)

    # Make --out a complete, servable directory. The vendored deepslate build means this whole
    # thing runs with no npm and no network.
    for path in args.nbt:
        shutil.copy(path, os.path.join(args.out, os.path.basename(path)))
    here = os.path.dirname(os.path.abspath(__file__))
    shutil.copy(os.path.join(here, 'viewer.html'), os.path.join(args.out, 'index.html'))
    for lib in ('deepslate.umd.cjs', 'gl-matrix.js'):
        shutil.copy(os.path.join(here, 'vendor', lib), os.path.join(args.out, lib))
    print('%d structures, %d distinct blocks -> %d blockstates, %d models, %d textures'
          % (len(structures), len(block_ids), len(definitions), len(models), len(written)))
    if missing:
        print('MISSING (%d):' % len(missing))
        for m in sorted(set(missing)):
            print('   ' + m)


if __name__ == '__main__':
    main()
