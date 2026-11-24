"""Writes the hidden wizard house as two plain templates, the ruin and the cottage, so each can be rendered
with the rendering-minecraft-structures skill (which knows vanilla blocks only).

    python build_scripts/hidden_house_views.py <out_dir>

Mod blocks are swapped for the nearest vanilla look; the real template is untouched.
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('gen', os.path.join(HERE, 'generate_biome_structures.py'))
gen = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gen)

STAND_IN = {
    'hogwartsmagic:wizard_table': ('minecraft:dark_oak_slab', {'type': 'top', 'waterlogged': 'false'}),
    'hogwartsmagic:wizard_chair': ('minecraft:dark_oak_stairs', None),
    'hogwartsmagic:wizard_cauldron': ('minecraft:cauldron', None),
    'hogwartsmagic:common_room_armchair': ('minecraft:red_wool', None),
    'hogwartsmagic:hieroglyph_sandstone': ('minecraft:chiseled_sandstone', None),
    'hogwartsmagic:cursed_glyph': ('minecraft:chiseled_sandstone', None),
}


def vanilla(blocks):
    out = {}
    for pos, (name, props, nbt) in blocks.items():
        if name in STAND_IN:
            name, props = STAND_IN[name]
            if name == 'minecraft:dark_oak_stairs':
                props = {'facing': 'north', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'}
        out[pos] = (name, props, nbt if name == 'minecraft:chest' else None)
    return out


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '.'
    os.makedirs(out, exist_ok=True)
    blocks, _ = gen.hidden_house()
    for label, view in (('ruin', gen.ruin_view(blocks)), ('cottage', gen.cottage_view(blocks))):
        gen.write_nbt(os.path.join(out, 'hidden_house_%s.nbt' % label), gen.build_template(vanilla(view)))
        print(label, len(view), 'blocks')
