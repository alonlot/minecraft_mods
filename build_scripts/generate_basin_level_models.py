"""Placeholder models for the basin's LEVEL look and the cave goblet's fill look. Task 13 finalises textures.

Basin: the frame is the existing despair_basin_looted model; despair_basin_liquid_1..6 are one liquid slab each,
its top dropping with the level; despair_basin_locket_piece is the Locket alone, shown at level 0 until taken.

Run from the repo root: python build_scripts/generate_basin_level_models.py
"""
import json
import os

ROOT = os.path.join('src', 'main', 'resources', 'assets', 'hogwartsmagic', 'models')
BLOCK = os.path.join(ROOT, 'block')


def load(name):
    with open(os.path.join(BLOCK, name + '.json'), encoding='utf-8') as f:
        return json.load(f)


def save(path, data):
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def slab(frm, to, tex):
    return {'from': frm, 'to': to, 'faces': {d: {'texture': tex} for d in ('up', 'north', 'south', 'east', 'west')}}


def main():
    frame = load('despair_basin_looted')
    textures = frame['textures']
    for level in range(1, 7):
        top = round(2 + 5 * level / 6.0, 2)
        save(os.path.join(BLOCK, 'despair_basin_liquid_%d.json' % level),
             {'textures': textures, 'elements': [slab([2, 2, 2], [14, top, 14], '#liquid')]})
    locket_full = load('despair_basin_locket')
    pieces = [e for e in locket_full['elements'] if e['faces']['north']['texture'] == '#locket']
    save(os.path.join(BLOCK, 'despair_basin_locket_piece.json'), {'textures': textures, 'elements': pieces})

    # the goblet item models (cave_goblet + fills) are hand-textured now; this script no longer writes them

    # multipart: the frame always; liquid by level while not looted; the Locket piece while it shows
    parts = [{'apply': {'model': 'hogwartsmagic:block/despair_basin_looted'}}]
    for level in range(1, 7):
        parts.append({'when': {'level': str(level), 'stage': 'full|drinking'},
                      'apply': {'model': 'hogwartsmagic:block/despair_basin_liquid_%d' % level}})
    parts.append({'when': {'locket': 'true'}, 'apply': {'model': 'hogwartsmagic:block/despair_basin_locket_piece'}})
    save(os.path.join('src', 'main', 'resources', 'assets', 'hogwartsmagic', 'blockstates', 'despair_basin.json'),
         {'multipart': parts})


if __name__ == '__main__':
    main()
