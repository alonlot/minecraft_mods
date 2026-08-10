"""Generate the .nbt structure templates for the mod's biome structures.

Minecraft has no text format for structure templates -- they are gzipped NBT, which is why these
builds are described here as code and compiled to .nbt rather than hand-placed in a world and
exported. Re-run this script after editing a build; it overwrites the templates in place.

    python build_scripts/generate_biome_structures.py

Each build returns a dict of (x, y, z) -> (block_id, properties, block_entity_nbt). Only listed
positions are written, and anything not listed is left as whatever the terrain already had -- so
"air" has to be placed explicitly wherever a build needs a hollow interior. That is what carves the
two underground halls out of solid stone.

Coordinates are structure-local with y=0 at the bottom of the build. For the surface structures the
structure JSON projects y=0 onto the terrain heightmap; for the underground ones it is pinned to an
absolute depth instead. See the structure JSON files for which.
"""

import gzip
import os
import struct

# --- minimal NBT writer -------------------------------------------------------------------------
# Values are tagged tuples: ('int', v) ('string', s) ('list', elem_type, [values]) ('compound', {})

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT = 0, 1, 2, 3
TAG_STRING, TAG_LIST, TAG_COMPOUND = 8, 9, 10

TYPE_IDS = {'byte': TAG_BYTE, 'short': TAG_SHORT, 'int': TAG_INT,
            'string': TAG_STRING, 'list': TAG_LIST, 'compound': TAG_COMPOUND}


def _write_payload(out, value):
    kind = value[0]
    if kind == 'byte':
        out.write(struct.pack('>b', value[1]))
    elif kind == 'short':
        out.write(struct.pack('>h', value[1]))
    elif kind == 'int':
        out.write(struct.pack('>i', value[1]))
    elif kind == 'string':
        data = value[1].encode('utf-8')
        out.write(struct.pack('>H', len(data)))
        out.write(data)
    elif kind == 'list':
        elem_kind, items = value[1], value[2]
        out.write(struct.pack('>b', TYPE_IDS[elem_kind] if items else TAG_END))
        out.write(struct.pack('>i', len(items)))
        for item in items:
            _write_payload(out, item)
    elif kind == 'compound':
        for name, child in value[1].items():
            out.write(struct.pack('>b', TYPE_IDS[child[0]]))
            raw = name.encode('utf-8')
            out.write(struct.pack('>H', len(raw)))
            out.write(raw)
            _write_payload(out, child)
        out.write(struct.pack('>b', TAG_END))
    else:
        raise ValueError('unknown tag kind ' + kind)


def write_nbt(path, root):
    """Write a gzipped, unnamed root compound -- the shape Minecraft expects for a template."""
    import io
    buf = io.BytesIO()
    buf.write(struct.pack('>b', TAG_COMPOUND))
    buf.write(struct.pack('>H', 0))  # empty root name
    _write_payload(buf, root)
    with gzip.open(path, 'wb') as fh:
        fh.write(buf.getvalue())


DATA_VERSION = 3953  # 1.21


def build_template(blocks):
    """blocks: {(x,y,z): (name, props_dict_or_None, be_nbt_dict_or_None)} -> structure NBT root."""
    size = [max(p[i] for p in blocks) + 1 for i in range(3)]

    palette, palette_index = [], {}
    block_tags = []
    for pos in sorted(blocks):
        name, props, be = blocks[pos]
        key = (name, tuple(sorted((props or {}).items())))
        if key not in palette_index:
            palette_index[key] = len(palette)
            entry = {'Name': ('string', name)}
            if props:
                entry['Properties'] = ('compound', {k: ('string', v) for k, v in props.items()})
            palette.append(('compound', entry))
        tag = {
            'pos': ('list', 'int', [('int', c) for c in pos]),
            'state': ('int', palette_index[key]),
        }
        if be:
            tag['nbt'] = ('compound', {k: ('string', v) for k, v in be.items()})
        block_tags.append(('compound', tag))

    return ('compound', {
        'DataVersion': ('int', DATA_VERSION),
        'size': ('list', 'int', [('int', c) for c in size]),
        'palette': ('list', 'compound', palette),
        'blocks': ('list', 'compound', block_tags),
        'entities': ('list', 'compound', []),
    })


# --- helpers ------------------------------------------------------------------------------------

def box(blocks, x0, y0, z0, x1, y1, z1, name, props=None, hollow=False):
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for z in range(z0, z1 + 1):
                edge = x in (x0, x1) or y in (y0, y1) or z in (z0, z1)
                if hollow and not edge:
                    continue
                blocks[(x, y, z)] = (name, props, None)


def fill(blocks, x0, y0, z0, x1, y1, z1, name, props=None):
    box(blocks, x0, y0, z0, x1, y1, z1, name, props, hollow=False)


def chest(blocks, pos, facing, loot):
    blocks[pos] = ('minecraft:chest', {'facing': facing},
                   {'id': 'minecraft:chest', 'LootTable': loot})


def checker(x, z, a, b, mod=3):
    return a if (x * 7 + z * 5) % mod else b


# --- the builds ---------------------------------------------------------------------------------

def fen_gibbet():
    """Dementor Fen: a rotting gallows platform on stilts over the bog."""
    b = {}
    posts = [(1, 1), (7, 1), (1, 7), (7, 7)]
    for x, z in posts:
        fill(b, x, 0, z, x, 2, z, 'minecraft:dark_oak_log', {'axis': 'y'})

    # Deck, with a few boards rotted through.
    for x in range(1, 8):
        for z in range(1, 8):
            if (x * 3 + z * 5) % 11 == 0:
                continue
            b[(x, 3, z)] = ('minecraft:dark_oak_planks', None, None)

    # Railing, broken in places.
    for x in range(1, 8):
        for z in (1, 7):
            if (x + z) % 4:
                b[(x, 4, z)] = ('minecraft:dark_oak_fence', None, None)
    for z in range(2, 7):
        for x in (1, 7):
            if (x + z) % 4:
                b[(x, 4, z)] = ('minecraft:dark_oak_fence', None, None)

    # The gibbet arm and its hanging cage.
    fill(b, 4, 4, 1, 4, 8, 1, 'minecraft:dark_oak_log', {'axis': 'y'})
    fill(b, 4, 8, 2, 4, 8, 4, 'minecraft:dark_oak_log', {'axis': 'z'})
    fill(b, 4, 6, 4, 4, 7, 4, 'minecraft:chain', {'axis': 'y'})
    box(b, 3, 4, 3, 5, 6, 5, 'minecraft:iron_bars', None, hollow=True)
    fill(b, 4, 5, 4, 4, 5, 4, 'minecraft:air')

    for pos in [(2, 4, 2), (6, 4, 6), (3, 4, 6), (4, 7, 2)]:
        b[pos] = ('minecraft:cobweb', None, None)
    b[(2, 5, 6)] = ('minecraft:soul_lantern', {'hanging': 'false'}, None)
    chest(b, (6, 4, 2), 'south', 'minecraft:chests/simple_dungeon')
    return b


def horntail_nest():
    """Horntail Peaks: a scorched nest ringed with basalt, picked bones in the middle."""
    b = {}
    cx = cz = 5
    for x in range(11):
        for z in range(11):
            d2 = (x - cx) ** 2 + (z - cz) ** 2
            if d2 > 30:
                continue
            b[(x, 0, z)] = ('minecraft:blackstone', None, None)
            if 12 <= d2 <= 30:                      # raised rim
                b[(x, 1, z)] = (checker(x, z, 'minecraft:basalt', 'minecraft:blackstone'),
                                {'axis': 'y'} if checker(x, z, 1, 0) else None, None)
                if d2 >= 22 and (x + z) % 3 == 0:
                    b[(x, 2, z)] = ('minecraft:basalt', {'axis': 'y'}, None)
            elif d2 <= 4:                           # hot centre
                b[(x, 0, z)] = ('minecraft:magma_block', None, None)

    for pos in [(4, 1, 4), (6, 1, 5), (5, 1, 6)]:   # eggs
        b[pos] = ('minecraft:gilded_blackstone', None, None)
    for pos in [(3, 1, 6), (7, 1, 3), (6, 1, 7)]:   # prey
        b[pos] = ('minecraft:bone_block', {'axis': 'x'}, None)
    chest(b, (3, 1, 3), 'south', 'minecraft:chests/nether_bridge')
    return b


def chamber_hall():
    """Chamber Depths: a pillared serpent hall with a water channel down its spine.

    Fully enclosed on all six sides -- this is placed at a fixed depth inside solid rock, so the
    shell is what stops it opening into whatever cave happens to be next door."""
    b = {}
    box(b, 0, 0, 0, 12, 7, 12, 'minecraft:stone_bricks')          # solid shell
    fill(b, 1, 1, 1, 11, 6, 11, 'minecraft:air')                  # hollowed out

    for x in range(13):                                            # weathered floor + walls
        for z in range(13):
            b[(x, 0, z)] = (checker(x, z, 'minecraft:stone_bricks',
                                    'minecraft:mossy_stone_bricks', 4), None, None)
    for x in range(13):
        for y in range(1, 7):
            for z in (0, 12):
                b[(x, y, z)] = (checker(x + y, z, 'minecraft:stone_bricks',
                                        'minecraft:cracked_stone_bricks', 5), None, None)
    for z in range(1, 12):
        for y in range(1, 7):
            for x in (0, 12):
                b[(x, y, z)] = (checker(z + y, x, 'minecraft:stone_bricks',
                                        'minecraft:mossy_stone_bricks', 5), None, None)

    for px, pz in [(3, 3), (9, 3), (3, 9), (9, 9)]:               # pillars
        fill(b, px, 1, pz, px, 5, pz, 'minecraft:stone_bricks')
        b[(px, 6, pz)] = ('minecraft:chiseled_stone_bricks', None, None)

    fill(b, 6, 0, 1, 6, 0, 11, 'minecraft:water')                 # channel
    fill(b, 5, 0, 1, 5, 0, 11, 'minecraft:stone_brick_slab', {'type': 'bottom'})
    fill(b, 7, 0, 1, 7, 0, 11, 'minecraft:stone_brick_slab', {'type': 'bottom'})

    fill(b, 4, 1, 11, 8, 4, 11, 'minecraft:iron_bars')            # serpent gate
    for pos in [(1, 6, 1), (11, 6, 1), (1, 6, 11), (11, 6, 11), (6, 6, 6)]:
        b[pos] = ('minecraft:soul_lantern', {'hanging': 'true'}, None)
    for pos in [(2, 5, 2), (10, 5, 3), (2, 5, 10), (9, 5, 9)]:
        b[pos] = ('minecraft:cobweb', None, None)
    chest(b, (2, 1, 2), 'south', 'minecraft:chests/simple_dungeon')
    chest(b, (10, 1, 10), 'north', 'minecraft:chests/stronghold_corridor')
    return b


def goblin_vault():
    """Goblin Undercity: a barred vault row cut into the deepslate. Also fully enclosed."""
    b = {}
    box(b, 0, 0, 0, 12, 6, 8, 'minecraft:deepslate_bricks')
    fill(b, 1, 1, 1, 11, 5, 7, 'minecraft:air')

    for x in range(13):
        for z in range(9):
            b[(x, 0, z)] = (checker(x, z, 'minecraft:polished_deepslate',
                                    'minecraft:deepslate_tiles', 3), None, None)

    # Vault alcoves down the long wall: barred front, treasure behind.
    for i, x in enumerate((2, 5, 8)):
        fill(b, x, 1, 1, x + 1, 3, 1, 'minecraft:iron_bars')
        fill(b, x, 1, 0, x + 1, 2, 0, 'minecraft:gold_block' if i != 1 else 'minecraft:emerald_block')
        b[(x, 4, 1)] = ('minecraft:chiseled_deepslate', None, None)
        b[(x + 1, 4, 1)] = ('minecraft:chiseled_deepslate', None, None)

    for x in range(1, 12):                                        # cart track
        b[(x, 1, 4)] = ('minecraft:rail', {'shape': 'east_west'}, None)
    for pos in [(1, 4, 2), (11, 4, 2), (1, 4, 6), (11, 4, 6), (6, 5, 4)]:
        b[pos] = ('minecraft:lantern', {'hanging': 'true'}, None)
    chest(b, (10, 1, 6), 'west', 'minecraft:chests/abandoned_mineshaft')
    chest(b, (2, 1, 6), 'east', 'minecraft:chests/stronghold_crossing')
    return b


def black_lake_ruin():
    """The Black Lake: a drowned colonnade on the lake floor. Flooded, not hollow."""
    b = {}
    for x in range(11):
        for z in range(11):
            if (x * 5 + z * 3) % 7 == 0:
                continue
            b[(x, 0, z)] = (checker(x, z, 'minecraft:stone_bricks',
                                    'minecraft:mossy_stone_bricks', 3), None, None)

    for px, pz, h in [(1, 1, 4), (9, 1, 3), (1, 9, 5), (9, 9, 2), (5, 5, 4)]:
        for y in range(1, h + 1):
            b[(px, y, pz)] = ('minecraft:stone_bricks', None, None)
        b[(px, h + 1, pz)] = ('minecraft:chiseled_stone_bricks', None, None)

    for pos in [(3, 1, 5), (7, 1, 5), (5, 1, 2)]:
        b[pos] = ('minecraft:prismarine_bricks', None, None)
    b[(5, 5, 5)] = ('minecraft:sea_lantern', None, None)
    b[(1, 5, 9)] = ('minecraft:sea_lantern', None, None)

    # Everything the ruin does not occupy stays water: an air pocket on a lake floor reads as a bug.
    for x in range(11):
        for y in range(1, 7):
            for z in range(11):
                b.setdefault((x, y, z), ('minecraft:water', {'level': '0'}, None))

    chest(b, (3, 1, 3), 'south', 'minecraft:chests/underwater_ruin_big')
    return b


BUILDS = {
    'fen_gibbet': fen_gibbet,
    'horntail_nest': horntail_nest,
    'chamber_hall': chamber_hall,
    'goblin_vault': goblin_vault,
    'black_lake_ruin': black_lake_ruin,
}

OUT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources',
                   'data', 'hogwartsmagic', 'structure')

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, builder in BUILDS.items():
        blocks = builder()
        path = os.path.join(OUT, name + '.nbt')
        write_nbt(path, build_template(blocks))
        size = [max(p[i] for p in blocks) + 1 for i in range(3)]
        print('%-18s %2dx%2dx%2d  %5d blocks  %6d bytes'
              % (name, size[0], size[1], size[2], len(blocks), os.path.getsize(path)))
