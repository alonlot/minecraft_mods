"""Check the mod's worldgen data files before the game does.

    python build_scripts/validate_worldgen.py

Worldgen is JSON, so javac never sees it, and Minecraft only reports a problem at world load --
usually as a wall of DFU "Failed to parse either" text that names the value but not the file. Every
check here exists because something actually broke:

  * value-provider bounds -- "minecraft:uniform" is not one thing. Inside worldgen it is a value
    provider, and even there the field name of the upper bound depends on which: UniformFloat wants
    max_exclusive, while UniformInt and UniformHeight want max_inclusive. Getting it wrong fails
    world creation. (Loot tables have a third, unrelated minecraft:uniform that takes plain min/max,
    which is why this check is scoped to worldgen and not run over every data file.)
  * dangling references -- a structure naming a template pool that names a .nbt that isn't there
    does not crash, it just silently never generates, which is much harder to notice.
  * underground placement -- project_start_to_heightmap is what drags a buried structure to the
    surface, so its presence on an underground_structures entry is always a mistake.
  * duplicate salts -- two structure sets sharing a salt stack on the same chunks.

Not covered: feature-order cycles between mod and vanilla biomes, which needs the decompiled vanilla
biome sources to check against. That one still has to be done by hand.
"""

import json
import os
import sys
import glob

# normpath matters: these paths contain a '..', and a lexical prefix test against an un-normalised
# path silently matches nothing -- which would make this script cheerfully pass a broken file.
ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)),
                                     '..', 'src', 'main', 'resources', 'data'))
NS = 'hogwartsmagic'
WORLDGEN = os.path.join(ROOT, NS, 'worldgen')

# Fields whose minecraft:uniform is a UniformFloat -- the only provider using an exclusive upper
# bound. Anything else (y, height, start_height, count, ...) is Int- or Height-based and inclusive.
FLOAT_FIELDS = {'yScale', 'horizontal_radius_multiplier', 'vertical_radius_multiplier',
                'floor_level', 'thickness', 'width', 'height_scale', 'spread', 'wind_speed'}

errors = []


def err(msg):
    errors.append(msg)


def load(path):
    with open(path, encoding='utf-8') as fh:
        return json.load(fh)


def local(rl):
    """'hogwartsmagic:foo' -> 'foo'; vanilla ids -> None (nothing for us to check)."""
    ns, _, path = rl.partition(':')
    return path if ns == NS else None


def exists(kind, name, ext='.json'):
    return os.path.exists(os.path.join(WORLDGEN, kind, name + ext))


def check_providers(node, where, parent=None):
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:uniform':
            is_float = parent in FLOAT_FIELDS
            want, wrong = ('max_exclusive', 'max_inclusive') if is_float else ('max_inclusive', 'max_exclusive')
            kind = 'UniformFloat' if is_float else 'UniformInt/UniformHeight'
            if wrong in node:
                err(f"{where}: '{parent}' is a {kind}, so it needs {want}, not {wrong}")
            elif want not in node:
                err(f"{where}: '{parent}' uniform is missing {want}")
        for key, value in node.items():
            check_providers(value, where, key)
    elif isinstance(node, list):
        for value in node:
            check_providers(value, where, parent)


def main():
    files = sorted(glob.glob(os.path.join(ROOT, '**', '*.json'), recursive=True))
    for path in files:
        rel = os.path.relpath(path, ROOT)
        try:
            data = load(path)
        except json.JSONDecodeError as exc:
            err(f'{rel}: invalid JSON -- {exc}')
            continue
        # Provider bounds are a worldgen concept; loot tables use a different minecraft:uniform.
        if os.path.normpath(path).startswith(WORLDGEN + os.sep):
            check_providers(data, rel)

    biomes = {os.path.basename(p)[:-5] for p in glob.glob(os.path.join(WORLDGEN, 'biome', '*.json'))}

    for path in sorted(glob.glob(os.path.join(WORLDGEN, 'biome', '*.json'))):
        name = os.path.basename(path)[:-5]
        biome = load(path)
        for carver in sum(biome.get('carvers', {}).values(), []):
            ref = local(carver)
            if ref and not exists('configured_carver', ref):
                err(f'biome/{name}: missing configured_carver {carver}')
        for step in biome.get('features', []):
            for feature in step:
                ref = local(feature)
                if not ref:
                    continue
                if not exists('placed_feature', ref):
                    err(f'biome/{name}: missing placed_feature {feature}')
                    continue
                inner = local(load(os.path.join(WORLDGEN, 'placed_feature', ref + '.json'))['feature'])
                if inner and not exists('configured_feature', inner):
                    err(f'biome/{name}: placed_feature {ref} -> missing configured_feature {inner}')

    for path in sorted(glob.glob(os.path.join(WORLDGEN, 'structure', '*.json'))):
        name = os.path.basename(path)[:-5]
        s = load(path)
        pool = local(s['start_pool'])
        if pool and not exists('template_pool', pool):
            err(f'structure/{name}: missing template_pool {pool}')
        elif pool:
            for element in load(os.path.join(WORLDGEN, 'template_pool', pool + '.json'))['elements']:
                tpl = local(element['element']['location'])
                if tpl and not os.path.exists(os.path.join(ROOT, NS, 'structure', tpl + '.nbt')):
                    err(f'structure/{name}: template pool points at missing {tpl}.nbt')

        tag = local(s['biomes'].lstrip('#'))
        if tag:
            tag_path = os.path.join(ROOT, NS, 'tags', 'worldgen', 'biome', tag + '.json')
            if not os.path.exists(tag_path):
                err(f'structure/{name}: missing biome tag {tag}')
            else:
                for value in load(tag_path)['values']:
                    ref = local(value if isinstance(value, str) else value['id'])
                    if ref and ref not in biomes:
                        err(f'structure/{name}: biome tag names unknown biome {ref}')

        if not exists('structure_set', name):
            err(f'structure/{name}: has no structure_set, so it will never be placed')

        underground = s['step'] == 'underground_structures'
        projected = 'project_start_to_heightmap' in s
        if underground and projected:
            err(f'structure/{name}: underground, but project_start_to_heightmap will pull it '
                f'to the surface')
        if not underground and not projected:
            err(f'structure/{name}: surface structure with no project_start_to_heightmap')

    salts = {}
    for path in glob.glob(os.path.join(WORLDGEN, 'structure_set', '*.json')):
        placement = load(path)['placement']
        salts.setdefault(placement['salt'], []).append(os.path.basename(path)[:-5])
        if placement['separation'] >= placement['spacing']:
            err(f'{os.path.basename(path)}: separation must be less than spacing')
    for salt, owners in salts.items():
        if len(owners) > 1:
            err(f'structure sets share salt {salt}: {", ".join(owners)} -- they will stack')

    print(f'checked {len(files)} data files')
    if errors:
        print()
        for e in errors:
            print('ERROR: ' + e)
        return 1
    print('worldgen data OK')
    return 0


if __name__ == '__main__':
    sys.exit(main())
