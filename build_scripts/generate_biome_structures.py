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

BUILDING NOTES
--------------
The first version of these builds was flat: single-material boxes with a couple of decorations
dropped on top. The techniques below are what the rewrite applies, and they are why the helper
functions look the way they do.

  * Palette, not block. Each build picks 3-5 blocks that share a tone but differ in texture, and
    blends them with `tex()` so no wall is one flat material. Mossy and cracked variants cluster low,
    where damp and damage would actually be.
  * Depth beats detail. A flat wall catches no light, so walls get pilasters that stand one block
    proud of the surface and recessed bays between them. That single-block offset is what turns a
    90-degree edge into a shadow line.
  * Silhouette first. Ruins get uneven heights, collapsed corners and toppled columns; the outline
    should be readable before any of the detail is.
  * Stairs and slabs are texture. Arches, vaulted ceilings, roof trim and rubble are all half-blocks
    -- they break up the cube grid that makes builds read as programmer-art.
  * Light belongs to the structure. Lanterns sit on pilasters and hang from vault crowns rather than
    being scattered where they happen to fit.
"""

import gzip
import io
import math
import os
import re
import struct

# --- minimal NBT writer -------------------------------------------------------------------------
# Values are tagged tuples: ('int', v) ('string', s) ('list', elem_type, [values]) ('compound', {})

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT = 0, 1, 2, 3
TAG_FLOAT, TAG_DOUBLE, TAG_STRING, TAG_LIST, TAG_COMPOUND = 5, 6, 8, 9, 10

TYPE_IDS = {'byte': TAG_BYTE, 'short': TAG_SHORT, 'int': TAG_INT,
            'float': TAG_FLOAT, 'double': TAG_DOUBLE,
            'string': TAG_STRING, 'list': TAG_LIST, 'compound': TAG_COMPOUND}


def _write_payload(out, value):
    kind = value[0]
    if kind == 'byte':
        out.write(struct.pack('>b', value[1]))
    elif kind == 'short':
        out.write(struct.pack('>h', value[1]))
    elif kind == 'int':
        out.write(struct.pack('>i', value[1]))
    elif kind == 'float':
        out.write(struct.pack('>f', value[1]))
    elif kind == 'double':
        out.write(struct.pack('>d', value[1]))
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
    buf = io.BytesIO()
    buf.write(struct.pack('>b', TAG_COMPOUND))
    buf.write(struct.pack('>H', 0))  # empty root name
    _write_payload(buf, root)
    with gzip.open(path, 'wb') as fh:
        fh.write(buf.getvalue())


DATA_VERSION = 3953  # 1.21


def entity(x, y, z, entity_id, tags=None):
    """One baked-in entity for a structure template.

    Coordinates are structure-local, same frame as the blocks, and may be
    fractional. `tags` is merged into the entity's own NBT as typed tuples --
    that is how a grindylow arrives already asleep in the weed rather than
    having to be spawned by a spawn cycle that might never fire.
    """
    nbt = {'id': ('string', entity_id),
           'Pos': ('list', 'double', [('double', x), ('double', y), ('double', z)])}
    nbt.update(tags or {})
    return {
        'pos': ('list', 'double', [('double', x), ('double', y), ('double', z)]),
        'blockPos': ('list', 'int', [('int', int(x)), ('int', int(y)), ('int', int(z))]),
        'nbt': ('compound', nbt),
    }


def build_template(blocks, entities=None):
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
        'entities': ('list', 'compound', [('compound', e) for e in (entities or [])]),
    })


# --- geometry helpers ----------------------------------------------------------------------------

def noise(x, y, z, salt=0):
    """Deterministic 0..1 hash. Structures must be identical every run, so no RNG anywhere."""
    h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791) ^ (salt * 2654435761)
    h &= 0x7FFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0x7FFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def tex(palette, x, y, z, salt=0):
    """Pick from [(block, weight), ...] by position hash -- the texture-mixing technique."""
    total = sum(w for _, w in palette)
    roll = noise(x, y, z, salt) * total
    for block, weight in palette:
        roll -= weight
        if roll < 0:
            return block
    return palette[-1][0]


def put(b, pos, name, props=None):
    b[pos] = (name, props, None)


def fill(b, x0, y0, z0, x1, y1, z1, name, props=None):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                b[(x, y, z)] = (name, props, None)


def fill_tex(b, x0, y0, z0, x1, y1, z1, palette, salt=0, ground=None):
    """Fill with a blended palette. `ground` biases a second palette in near y=ground (damp/damage)."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                pal = palette
                if ground is not None and y - ground <= 1 and noise(x, y, z, salt + 7) < 0.55:
                    pal = ground_palette(palette)
                b[(x, y, z)] = (tex(pal, x, y, z, salt), None, None)


def ground_palette(palette):
    """Weight the mossy/cracked members of a palette up, for the damp bottom courses of a wall."""
    return [(bl, w * (4 if ('mossy' in bl or 'cracked' in bl) else 1)) for bl, w in palette]


def stair(b, pos, name, facing, half='bottom', shape='straight'):
    b[pos] = (name, {'facing': facing, 'half': half, 'shape': shape, 'waterlogged': 'false'}, None)


def slab(b, pos, name, kind='bottom'):
    b[pos] = (name, {'type': kind, 'waterlogged': 'false'}, None)


def chest(b, pos, facing, loot):
    b[pos] = ('minecraft:chest', {'facing': facing},
              {'id': 'minecraft:chest', 'LootTable': loot})


def hall(b, w, h, d, shell, floor_pal, wall_pal, salt):
    """A sealed underground room with a vaulted ceiling and pilastered walls.

    Sealed on all six faces on purpose: these are placed at an absolute depth inside solid rock, so
    the shell is what stops the room opening into whatever cave happens to be next door.
    """
    fill(b, 0, 0, 0, w - 1, h - 1, d - 1, shell)          # solid block...
    fill(b, 1, 1, 1, w - 2, h - 2, d - 2, 'minecraft:air')  # ...hollowed out

    fill_tex(b, 0, 0, 0, w - 1, 0, d - 1, floor_pal, salt)
    for y in range(1, h - 1):
        fill_tex(b, 0, y, 0, w - 1, y, 0, wall_pal, salt, ground=1)
        fill_tex(b, 0, y, d - 1, w - 1, y, d - 1, wall_pal, salt, ground=1)
        fill_tex(b, 0, y, 1, 0, y, d - 2, wall_pal, salt, ground=1)
        fill_tex(b, w - 1, y, 1, w - 1, y, d - 2, wall_pal, salt, ground=1)
    fill_tex(b, 0, h - 1, 0, w - 1, h - 1, d - 1, wall_pal, salt + 3)


def vault(b, w, h, d, stairs):
    """Upside-down stairs around the ceiling edge, so the roof reads as an arch not a lid."""
    for x in range(1, w - 1):
        stair(b, (x, h - 2, 1), stairs, 'south', 'top')
        stair(b, (x, h - 2, d - 2), stairs, 'north', 'top')
    for z in range(2, d - 2):
        stair(b, (1, h - 2, z), stairs, 'east', 'top')
        stair(b, (w - 2, h - 2, z), stairs, 'west', 'top')


def pilasters(b, h, xs, zs, w, d, pal, cap, salt):
    """Columns standing one block proud of the wall, with a capital -- the depth technique.

    Returns the positions of the capitals so callers can hang lights off them.
    """
    caps = []
    for x in xs:
        for z in (1, d - 2):
            for y in range(1, h - 2):
                put(b, (x, y, z), tex(pal, x, y, z, salt))
            put(b, (x, h - 3, z), cap)
            caps.append((x, h - 4, z))
    for z in zs:
        for x in (1, w - 2):
            for y in range(1, h - 2):
                put(b, (x, y, z), tex(pal, x, y, z, salt))
            put(b, (x, h - 3, z), cap)
            caps.append((x, h - 4, z))
    return caps


def flood(b, w, h, d):
    """Submerge a build: water in every empty cell, and waterlogged=true on everything that can be.

    Doing this as one pass at the end rather than remembering it at each call site is deliberate.
    Waterlogging is invisible while you are placing blocks and only shows up in-game as a stair with
    a bubble of air trapped inside it, which is the single easiest thing to get wrong in an
    underwater build.
    """
    for pos, (block, props, be) in list(b.items()):
        if any(block.endswith(s) for s in WATERLOGGABLE):
            props = dict(props or {})
            props['waterlogged'] = 'true'
            b[pos] = (block, props, be)
    for x in range(w):
        for y in range(1, h):
            for z in range(d):
                b.setdefault((x, y, z), ('minecraft:water', {'level': '0'}, None))


def rubble(b, spots, palette, salt):
    """Small collapse heaps. Ruins without rubble read as unfinished rather than old."""
    for i, (x, y, z) in enumerate(spots):
        put(b, (x, y, z), tex(palette, x, y, z, salt + i))
        if noise(x, y, z, salt + i + 1) > 0.45:
            slab(b, (x, y + 1, z), 'minecraft:cobblestone_slab')


# --- the builds ---------------------------------------------------------------------------------

DARK_OAK = [('minecraft:dark_oak_planks', 6), ('minecraft:dark_oak_log', 2), ('minecraft:spruce_planks', 2)]
BOG_STONE = [('minecraft:mossy_cobblestone', 6), ('minecraft:cobblestone', 4), ('minecraft:mossy_stone_bricks', 3)]


def fen_gibbet():
    """Dementor Fen: a rotting gallows on a sunken stone plinth, one corner already gone.

    Silhouette does the work here -- the tall gibbet arm against a low deck, and the collapsed
    north-east corner, are what make it read from across the marsh.
    """
    b = {}
    # Sunken plinth. Grounding a build on a stone footing stops it looking like it was dropped in.
    for x in range(1, 10):
        for z in range(1, 10):
            if (x in (1, 9) and z in (1, 9)) or noise(x, 0, z, 5) < 0.12:
                continue
            put(b, (x, 0, z), tex(BOG_STONE, x, 0, z, 1))
            if noise(x, 0, z, 3) > 0.72:
                put(b, (x, 1, z), tex(BOG_STONE, x, 1, z, 2))

    posts = [(2, 2), (8, 2), (2, 8), (8, 8)]
    for px, pz in posts:
        if (px, pz) == (8, 8):           # the collapsed corner -- stump only
            put(b, (px, 2, pz), 'minecraft:dark_oak_log', {'axis': 'y'})
            continue
        fill(b, px, 2, pz, px, 4, pz, 'minecraft:dark_oak_log', {'axis': 'y'})
        # Diagonal braces at the post heads: structural framing, and it kills the bare 90-degree edge.
        stair(b, (px + (1 if px < 5 else -1), 4, pz), 'minecraft:dark_oak_stairs',
              'west' if px < 5 else 'east', 'top')
        stair(b, (px, 4, pz + (1 if pz < 5 else -1)), 'minecraft:dark_oak_stairs',
              'north' if pz < 5 else 'south', 'top')

    # Deck: mixed planks, slab patches worn thin, boards missing toward the fallen corner.
    for x in range(2, 9):
        for z in range(2, 9):
            rot = noise(x, 5, z, 11) + (x + z) / 26.0     # decay gradient toward (8,8)
            if rot > 1.05:
                continue
            if rot > 0.86:
                slab(b, (x, 5, z), 'minecraft:dark_oak_slab')
            else:
                put(b, (x, 5, z), tex(DARK_OAK, x, 5, z, 4))

    for x in range(2, 9):                                  # railings, broken in places
        for z in (2, 8):
            if noise(x, 6, z, 9) > 0.3 and (x, z) != (8, 8):
                put(b, (x, 6, z), 'minecraft:dark_oak_fence')
    for z in range(3, 8):
        for x in (2, 8):
            if noise(x, 6, z, 9) > 0.3:
                put(b, (x, 6, z), 'minecraft:dark_oak_fence')

    # The gibbet itself: post, braced arm, hanging cage.
    fill(b, 3, 6, 3, 3, 11, 3, 'minecraft:dark_oak_log', {'axis': 'y'})
    fill(b, 3, 11, 4, 3, 11, 6, 'minecraft:dark_oak_log', {'axis': 'z'})
    stair(b, (3, 10, 4), 'minecraft:dark_oak_stairs', 'north', 'top')
    slab(b, (3, 12, 3), 'minecraft:dark_oak_slab')
    fill(b, 3, 9, 6, 3, 10, 6, 'minecraft:chain', {'axis': 'y'})

    cage_y = 6
    for y in (cage_y, cage_y + 1, cage_y + 2):             # 3x3 barred cage
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                if dx == 0 and dz == 0 and y != cage_y:
                    continue
                if abs(dx) == 1 or abs(dz) == 1 or y == cage_y:
                    put(b, (3 + dx, y, 6 + dz), 'minecraft:iron_bars')
    put(b, (3, cage_y + 1, 6), 'minecraft:air')
    put(b, (3, cage_y + 2, 6), 'minecraft:air')

    for pos in [(4, 6, 4), (7, 6, 7), (2, 7, 5), (3, 9, 5)]:
        put(b, pos, 'minecraft:cobweb')
    put(b, (7, 6, 3), 'minecraft:soul_lantern', {'hanging': 'false'})
    put(b, (2, 7, 7), 'minecraft:soul_lantern', {'hanging': 'false'})
    rubble(b, [(9, 1, 8), (8, 1, 9), (9, 1, 6)], BOG_STONE, 21)
    chest(b, (6, 6, 3), 'south', 'minecraft:chests/simple_dungeon')

    # Baked in rather than left to spawn_overrides. Overrides only change what the ongoing spawn
    # cycle picks inside the bounds -- they do not populate the place when it generates, so they
    # still wait on darkness, the mob cap and where the player happens to be standing. A gibbet
    # with nothing hanging around it is not a gibbet.
    return b, [entity(6.5, 6.0, 5.5, 'hogwartsmagic:dementor',
                      {'PersistenceRequired': ('byte', 1)})]


CRAG = [('minecraft:blackstone', 6), ('minecraft:polished_blackstone', 3), ('minecraft:basalt', 3)]


def horntail_nest():
    """Horntail Peaks: a scorched eyrie. An uneven, broken crest is what sells it as a nest."""
    b = {}
    cx = cz = 7
    for x in range(15):
        for z in range(15):
            d2 = (x - cx) ** 2 + (z - cz) ** 2
            if d2 > 46:
                continue
            put(b, (x, 0, z), tex(CRAG, x, 0, z, 1))
            if d2 <= 6:                                    # hot centre
                put(b, (x, 0, z), 'minecraft:magma_block' if noise(x, 0, z, 2) > 0.35
                    else 'minecraft:blackstone')
            elif 20 <= d2 <= 46:
                # Rim height varies with angle so the crest is jagged, not a smooth donut.
                crest = 1 + int(noise(x // 2, 0, z // 2, 8) * 3)
                for y in range(1, crest + 1):
                    put(b, (x, y, z), tex(CRAG, x, y, z, 3))
                if noise(x, 0, z, 12) > 0.62:              # broken teeth along the crest
                    put(b, (x, crest + 1, z), 'minecraft:basalt', {'axis': 'y'})
                elif noise(x, 0, z, 13) > 0.72:
                    stair(b, (x, crest + 1, z),
                          'minecraft:polished_blackstone_stairs',
                          'east' if x < cx else 'west', 'bottom')

    for ex, ez in [(6, 6), (8, 7), (7, 9)]:                # eggs, on a scraped-up mound
        put(b, (ex, 0, ez), 'minecraft:polished_blackstone')
        put(b, (ex, 1, ez), 'minecraft:gilded_blackstone')
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if noise(ex + dx, 1, ez + dz, 17) > 0.55:
                slab(b, (ex + dx, 1, ez + dz), 'minecraft:polished_blackstone_slab')

    for bx, by, bz, axis in [(5, 1, 9, 'x'), (9, 1, 5, 'z'), (10, 1, 9, 'x'), (4, 1, 6, 'z')]:
        put(b, (bx, by, bz), 'minecraft:bone_block', {'axis': axis})
    put(b, (10, 2, 8), 'minecraft:magma_block')
    chest(b, (4, 1, 8), 'east', 'minecraft:chests/nether_bridge')

    # The dragon is fire-immune, so sitting it on the magma at the centre of its own nest is safe.
    return b, [entity(7.5, 1.0, 4.5, 'hogwartsmagic:hungarian_horntail',
                      {'PersistenceRequired': ('byte', 1)})]


CHAMBER_WALL = [('minecraft:stone_bricks', 8), ('minecraft:mossy_stone_bricks', 4),
                ('minecraft:cracked_stone_bricks', 3), ('minecraft:stone', 2)]
CHAMBER_FLOOR = [('minecraft:stone_bricks', 6), ('minecraft:mossy_stone_bricks', 5),
                 ('minecraft:cobblestone', 2), ('minecraft:mossy_cobblestone', 2)]


def chamber_hall():
    """Chamber Depths: a pillared serpent hall, vaulted, with a flooded channel down its spine."""
    W, H, D = 15, 9, 17
    b = {}
    hall(b, W, H, D, 'minecraft:stone_bricks', CHAMBER_FLOOR, CHAMBER_WALL, salt=31)
    vault(b, W, H, D, 'minecraft:stone_brick_stairs')

    caps = pilasters(b, H, [3, 7, 11], [4, 8, 12], W, D, CHAMBER_WALL,
                     'minecraft:chiseled_stone_bricks', salt=33)
    for i, (x, y, z) in enumerate(caps):
        if i % 2 == 0:
            put(b, (x, y, z), 'minecraft:soul_lantern', {'hanging': 'true'})

    # Recessed bays between the pilasters: pushing the wall back one block is what gives the
    # colonnade its shadow line.
    for z in (2, 3, 5, 6, 9, 10, 13, 14):
        for y in range(2, H - 3):
            for x in (1, W - 2):
                put(b, (x, y, z), 'minecraft:air')

    channel = D // 2
    fill(b, 1, 0, channel, W - 2, 0, channel, 'minecraft:water', {'level': '0'})
    for z in (channel - 1, channel + 1):
        for x in range(1, W - 1):
            slab(b, (x, 0, z), 'minecraft:stone_brick_slab', 'top')

    # Serpent gate at the far end: a barred arch rather than a flat grid of bars.
    for x in range(4, 11):
        put(b, (x, 1, D - 2), 'minecraft:iron_bars')
        put(b, (x, 2, D - 2), 'minecraft:iron_bars')
        if 5 <= x <= 9:
            put(b, (x, 3, D - 2), 'minecraft:iron_bars')
        if 6 <= x <= 8:
            put(b, (x, 4, D - 2), 'minecraft:iron_bars')
    put(b, (7, 5, D - 2), 'minecraft:chiseled_stone_bricks')
    stair(b, (6, 5, D - 2), 'minecraft:stone_brick_stairs', 'west')
    stair(b, (8, 5, D - 2), 'minecraft:stone_brick_stairs', 'east')

    # Kept clear of the gate at z=15 -- a heap piled against the bars buries them.
    rubble(b, [(2, 1, 7), (12, 1, 11), (4, 1, 12), (11, 1, 3), (5, 1, 2)], CHAMBER_FLOOR, 41)
    for pos in [(2, 6, 2), (12, 6, 4), (3, 6, 13), (11, 6, 15)]:
        put(b, pos, 'minecraft:cobweb')
    chest(b, (2, 1, 2), 'south', 'minecraft:chests/simple_dungeon')
    chest(b, (12, 1, 15), 'north', 'minecraft:chests/stronghold_corridor')

    # The whole reason this structure exists: a basilisk is 3.5 blocks wide and natural cave
    # worldgen almost never offers it a box that size, so the hall is the one place underground
    # it reliably fits.
    return b, [entity(7.5, 1.0, 6.5, 'hogwartsmagic:basilisk',
                      {'PersistenceRequired': ('byte', 1)})]


VAULT_WALL = [('minecraft:deepslate_bricks', 8), ('minecraft:cracked_deepslate_bricks', 3),
              ('minecraft:deepslate_tiles', 3), ('minecraft:polished_deepslate', 2)]
VAULT_FLOOR = [('minecraft:deepslate_tiles', 6), ('minecraft:polished_deepslate', 4),
               ('minecraft:cracked_deepslate_tiles', 2), ('minecraft:gravel', 1)]


def goblin_vault():
    """Goblin Undercity: a barrel-vaulted vault row, arched strongboxes cut into one wall."""
    W, H, D = 17, 8, 11
    b = {}
    hall(b, W, H, D, 'minecraft:deepslate_bricks', VAULT_FLOOR, VAULT_WALL, salt=51)
    vault(b, W, H, D, 'minecraft:deepslate_brick_stairs')

    caps = pilasters(b, H, [3, 8, 13], [], W, D, VAULT_WALL,
                     'minecraft:chiseled_deepslate', salt=53)
    for x, y, z in caps:
        put(b, (x, y, z), 'minecraft:lantern', {'hanging': 'true'})

    # Arched strongboxes recessed into the north wall: bars in front, treasure behind, arch on top.
    for i, x in enumerate((5, 10)):
        fill(b, x, 1, 0, x + 2, 3, 0, 'minecraft:air')
        metal = 'minecraft:gold_block' if i == 0 else 'minecraft:emerald_block'
        for dx in range(3):
            put(b, (x + dx, 1, 0), metal if noise(x + dx, 1, 0, 61) > 0.35
                else 'minecraft:raw_gold_block')
            put(b, (x + dx, 2, 0), 'minecraft:chiseled_deepslate')
            put(b, (x + dx, 1, 1), 'minecraft:iron_bars')
            put(b, (x + dx, 2, 1), 'minecraft:iron_bars')
        stair(b, (x, 3, 1), 'minecraft:deepslate_brick_stairs', 'east', 'top')
        stair(b, (x + 2, 3, 1), 'minecraft:deepslate_brick_stairs', 'west', 'top')
        put(b, (x + 1, 3, 1), 'minecraft:chiseled_deepslate')

    for x in range(1, W - 1):                              # cart track in a cut trough
        put(b, (x, 0, D // 2), 'minecraft:polished_deepslate')
        put(b, (x, 1, D // 2), 'minecraft:rail', {'shape': 'east_west'})
    for z in (D // 2 - 1, D // 2 + 1):
        for x in range(1, W - 1):
            slab(b, (x, 1, z), 'minecraft:deepslate_tile_slab')

    fill(b, 8, H - 3, D // 2, 8, H - 3, D // 2, 'minecraft:chain', {'axis': 'y'})
    put(b, (8, H - 4, D // 2), 'minecraft:lantern', {'hanging': 'true'})
    rubble(b, [(2, 1, 2), (15, 1, 8), (6, 1, 9), (13, 1, 2)], VAULT_FLOOR, 57)
    chest(b, (15, 1, 2), 'west', 'minecraft:chests/abandoned_mineshaft')
    chest(b, (2, 1, 8), 'east', 'minecraft:chests/stronghold_crossing')

    # Goblins keep the vaults; the troll is what they keep to guard them. The troll needs 3.25 of
    # headroom and the hall gives it six.
    crew = [entity(x + 0.5, 1.0, z + 0.5, 'hogwartsmagic:goblin',
                   {'PersistenceRequired': ('byte', 1)})
            for x, z in ((3, 3), (11, 7), (7, 2))]
    crew.append(entity(13.5, 1.0, 8.5, 'hogwartsmagic:troll',
                       {'PersistenceRequired': ('byte', 1)}))
    return b, crew


DROWNED = [('minecraft:stone_bricks', 6), ('minecraft:mossy_stone_bricks', 6),
           ('minecraft:cracked_stone_bricks', 4), ('minecraft:prismarine', 2)]


def black_lake_ruin():
    """The Black Lake: a broken colonnade on the lake floor, half of it already toppled.

    Column heights are deliberately uneven and two are lying flat -- a colonnade of equal columns
    reads as a fence. The gaps in the floor matter as much as the floor.
    """
    W, H, D = 15, 9, 15
    b = {}
    for x in range(W):
        for z in range(D):
            if noise(x, 0, z, 71) < 0.22:                  # floor broken through
                continue
            put(b, (x, 0, z), tex(DROWNED, x, 0, z, 72))

    columns = [(2, 2, 6), (7, 2, 4), (12, 2, 7), (2, 12, 3), (12, 12, 5), (7, 12, 6)]
    for cx, cz, height in columns:
        for y in range(1, height + 1):
            put(b, (cx, y, cz), tex(DROWNED, cx, y, cz, 73))
        put(b, (cx, height + 1, cz), 'minecraft:chiseled_stone_bricks')
        for dx, dz, facing in ((1, 0, 'west'), (-1, 0, 'east'), (0, 1, 'north'), (0, -1, 'south')):
            stair(b, (cx + dx, 1, cz + dz), 'minecraft:stone_brick_stairs', facing)

    # An arch still standing between two of them: the one intact silhouette in the ruin.
    for z in range(3, 12):
        put(b, (2, 7, z), tex(DROWNED, 2, 7, z, 74))
    stair(b, (2, 6, 2), 'minecraft:stone_brick_stairs', 'north')
    stair(b, (2, 6, 12), 'minecraft:stone_brick_stairs', 'south')

    for tx, tz, axis in [(5, 9, 'x'), (6, 9, 'x'), (7, 9, 'x'), (9, 3, 'z'), (9, 4, 'z')]:
        put(b, (tx, 1, tz), 'minecraft:stone_brick_wall')   # toppled shafts

    put(b, (7, 3, 7), 'minecraft:sea_lantern')
    put(b, (2, 4, 6), 'minecraft:sea_lantern')
    put(b, (12, 1, 12), 'minecraft:sea_lantern')
    for px, pz in [(5, 5), (10, 6), (4, 11), (11, 9)]:
        put(b, (px, 1, pz), 'minecraft:dark_prismarine' if noise(px, 1, pz, 77) > 0.5
            else 'minecraft:prismarine_bricks')
    rubble(b, [(4, 1, 3), (11, 1, 11), (8, 1, 12), (3, 1, 8)], DROWNED, 78)
    chest(b, (5, 1, 3), 'south', 'minecraft:chests/underwater_ruin_big')

    flood(b, W, H, D)

    return b, [entity(x + 0.5, 2.0, z + 0.5, 'hogwartsmagic:grindylow',
                      {'Lurking': ('byte', 1), 'PersistenceRequired': ('byte', 1)})
               for x, z in ((5, 5), (10, 9), (7, 11))]


MERSTONE = [('minecraft:mossy_cobblestone', 7), ('minecraft:cobblestone', 5),
            ('minecraft:prismarine', 4), ('minecraft:mossy_stone_bricks', 3)]
MERSTATUE = [('minecraft:prismarine_bricks', 7), ('minecraft:dark_prismarine', 4),
             ('minecraft:prismarine', 3)]


def merhut(b, cx, cz, radius, height, door, salt):
    """One crude merfolk dwelling: a weed-grown stone beehive with a low doorway.

    Round rather than square on purpose. Every other build in this pack is
    rectilinear because it was built by people with set squares; merpeople stack
    lakebed rock, and a ring of it reads as a different civilisation from across
    the water.
    """
    for y in range(1, height + 1):
        # Shrink the ring toward the top so the hut closes into a dome.
        r = radius - (0 if y <= height - 2 else 1)
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                dist = (dx * dx + dz * dz) ** 0.5
                if dist < r - 0.6 or dist > r + 0.45:
                    continue
                put(b, (cx + dx, y, cz + dz), tex(MERSTONE, cx + dx, y, cz + dz, salt))
    # Cap the dome.
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if abs(dx) + abs(dz) <= 1:
                slab(b, (cx + dx, height + 1, cz + dz), 'minecraft:mossy_cobblestone_slab')

    # Hollow it out, then punch the doorway through the wall.
    for y in range(1, height + 1):
        for dx in range(-radius, radius + 1):
            for dz in range(-radius, radius + 1):
                if (dx * dx + dz * dz) ** 0.5 < radius - 0.6:
                    put(b, (cx + dx, y, cz + dz), 'minecraft:air')
    ddx, ddz = door
    for step in range(radius, radius + 2):
        for y in (1, 2):
            put(b, (cx + ddx * step, y, cz + ddz * step), 'minecraft:air')

    put(b, (cx, 1, cz), 'minecraft:sea_lantern')


def merfolk_village():
    """The Black Lake: the merfolk village, with its merchieftainess statue.

    This is the place Harry swims into in Goblet of Fire -- "crude stone
    dwellings daubed with algae" around a square, and a great statue of a
    merperson looming over it. The statue is what has to carry the build: it is
    the only thing down there that says the ruin has owners.

    Merpeople spawn here through the structure's spawn_overrides, so the village
    arrives already inhabited rather than waiting for a natural spawn cycle.
    """
    W, H, D = 25, 14, 25
    b = {}

    # Lakebed: patchy, weedy, broken through in places so it does not read as a
    # laid floor. These are people who live on the bottom, not on a plaza.
    for x in range(W):
        for z in range(D):
            n = noise(x, 0, z, 91)
            if n < 0.18:
                continue
            if n < 0.40:
                put(b, (x, 0, z), 'minecraft:gravel')
            elif n < 0.55:
                put(b, (x, 0, z), 'minecraft:sand')
            else:
                put(b, (x, 0, z), tex(MERSTONE, x, 0, z, 92))

    # Four dwellings round the square, each facing inward.
    merhut(b, 5, 5, 3, 4, (0, -1), 101)
    merhut(b, 19, 5, 3, 5, (0, -1), 102)
    merhut(b, 5, 19, 3, 5, (0, 1), 103)
    merhut(b, 19, 19, 4, 5, (0, 1), 104)

    # --- the merchieftainess ------------------------------------------------
    # Read from the ground up: plinth, coiled tail, torso, spread arms, head. It
    # stands nine blocks over a four-block hut, which is the whole point.
    cx, cz = 12, 12
    fill_tex(b, cx - 2, 1, cz - 2, cx + 2, 2, cz + 2, MERSTATUE, 110)
    for dx, dz, facing in ((3, 0, 'west'), (-3, 0, 'east'), (0, 3, 'north'), (0, -3, 'south')):
        stair(b, (cx + dx, 1, cz + dz), 'minecraft:prismarine_brick_stairs', facing)

    fill_tex(b, cx - 1, 3, cz - 1, cx + 1, 5, cz + 1, MERSTATUE, 111)   # coiled tail
    put(b, (cx + 2, 3, cz), tex(MERSTATUE, cx + 2, 3, cz, 111))         # a coil breaking out
    put(b, (cx + 2, 4, cz + 1), tex(MERSTATUE, cx + 2, 4, cz + 1, 111))

    fill_tex(b, cx - 1, 6, cz, cx + 1, 8, cz, MERSTATUE, 112)           # torso
    put(b, (cx, 6, cz - 1), tex(MERSTATUE, cx, 6, cz - 1, 112))
    put(b, (cx, 7, cz - 1), 'minecraft:dark_prismarine')

    for side in (-1, 1):                                                # arms, spread wide
        put(b, (cx + side * 2, 8, cz), tex(MERSTATUE, cx + side * 2, 8, cz, 113))
        put(b, (cx + side * 3, 7, cz), tex(MERSTATUE, cx + side * 3, 7, cz, 113))
        put(b, (cx + side * 3, 6, cz), 'minecraft:prismarine_wall')

    put(b, (cx, 9, cz), 'minecraft:chiseled_stone_bricks')              # head
    put(b, (cx, 10, cz), 'minecraft:dark_prismarine')                   # and her crown
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        put(b, (cx + dx, 10, cz + dz), 'minecraft:prismarine_wall')

    # Her spear, planted: the silhouette that reads first from a distance.
    for y in range(1, 12):
        put(b, (cx + 4, y, cz - 1), 'minecraft:prismarine_wall')
    put(b, (cx + 4, 12, cz - 1), 'minecraft:sea_lantern')

    # Lamp posts round the square.
    for px, pz in ((12, 4), (12, 20), (4, 12), (20, 12)):
        for y in range(1, 4):
            put(b, (px, y, pz), tex(MERSTATUE, px, y, pz, 120))
        put(b, (px, 4, pz), 'minecraft:sea_lantern')

    # Weed and clutter, thickest where nobody walks.
    for x in range(W):
        for z in range(D):
            if (x, z) == (cx, cz) or b.get((x, 1, z), (None,))[0] not in (None, 'minecraft:air'):
                continue
            if b.get((x, 0, z), (None,))[0] is None:
                continue
            n = noise(x, 1, z, 130)
            if n > 0.90:
                put(b, (x, 1, z), 'minecraft:sea_pickle')
            elif n > 0.80:
                put(b, (x, 1, z), 'minecraft:seagrass')

    rubble(b, [(9, 1, 17), (16, 1, 8), (8, 1, 8), (17, 1, 17)], MERSTONE, 140)

    # The hoard, laid at the chieftainess's feet where anyone can see it and
    # nobody sensible touches it. Placed after the weed pass so nothing has
    # grown over it, and the block it faces is cleared so the lid can open.
    put(b, (cx, 1, cz - 4), 'minecraft:air')
    put(b, (cx, 1, cz - 5), 'minecraft:air')
    chest(b, (cx, 1, cz - 4), 'north', 'hogwartsmagic:chests/merfolk_hoard')
    put(b, (cx - 1, 1, cz - 4), 'minecraft:dark_prismarine')
    put(b, (cx + 1, 1, cz - 4), 'minecraft:dark_prismarine')

    # A lesser stash tucked behind a hut. Taking from it is just as much theft.
    chest(b, (19, 1, 21), 'south', 'minecraft:chests/underwater_ruin_big')

    flood(b, W, H, D)

    # A nest of grindylows asleep in the weed around the hoard. Baked into the
    # template rather than left to a spawn cycle, so the ambush is a property of
    # the place and always happens. They wake when the chest is opened -- see
    # MerfolkEvents -- or if you blunder right into one.
    nest = []
    for dx, dz in ((-2.4, -1.6), (2.4, -1.6), (-3.4, 1.4), (3.4, 1.4), (0.0, -3.4)):
        nest.append(entity(cx + dx + 0.5, 1.1, cz - 4 + dz + 0.5, 'hogwartsmagic:grindylow', {
            'Lurking': ('byte', 1),
            'PersistenceRequired': ('byte', 1),
        }))
    return b, nest


BUILDS = {
    'fen_gibbet': fen_gibbet,
    'horntail_nest': horntail_nest,
    'chamber_hall': chamber_hall,
    'goblin_vault': goblin_vault,
    'black_lake_ruin': black_lake_ruin,
    'merfolk_village': merfolk_village,
}

# Builds whose empty space is water rather than air. Anything waterloggable inside one has to say so,
# or it keeps a pocket of air inside its own model and the ruin looks like it is full of bubbles.
FLOODED = {'black_lake_ruin', 'merfolk_village'}


# --- audit ---------------------------------------------------------------------------------------
# These builds are written blind -- there is no way to look at them from in here -- and the things
# that go wrong when you build blind are not geometry, they are BLOCK STATES. A lantern with nothing
# above it, a chest facing into a wall, two chests that silently merge into one double chest, a stair
# underwater that forgot to be waterlogged: every one of those places fine and looks broken. So the
# rules that a player would notice by eye are checked here instead.

NOT_SUPPORT = {'minecraft:air', 'minecraft:water', 'minecraft:cobweb', 'minecraft:rail'}
# Only these two are the hanging/standing kind. sea_lantern and jack_o_lantern are ordinary full
# cubes that happen to end in "lantern" and need no support at all.
LANTERNS = {'minecraft:lantern', 'minecraft:soul_lantern'}
DIRS = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}
WATERLOGGABLE = ('_stairs', '_slab', '_wall', '_fence', 'iron_bars', 'chain')


def see_through(block):
    """True if you can see past this block. Bars next to bars is a cage, not a burial."""
    if block is None or block in ('minecraft:air', 'minecraft:water'):
        return True
    return (block in LANTERNS
            or block in ('minecraft:cobweb', 'minecraft:iron_bars', 'minecraft:chain',
                         'minecraft:rail')
            or any(block.endswith(s) for s in ('_stairs', '_slab', '_wall', '_fence')))


# A mob can stand in any of these -- they have no collision box. Everything else underfoot means the
# entity was baked inside a solid block and will be shoved somewhere unpredictable on placement.
PASSABLE = re.compile(r'air|water|seagrass|kelp|grass|fern|_bush|vine|sapling|rail|torch|lantern'
                      r'|cobweb|sea_pickle|coral|light|flower|lily')


def audit_entities(blocks, entities, size):
    """Baked entities have to stand somewhere they fit, and that is easy to get wrong by a block.

    Both faults this caught on its first run were a single slab: a troll standing in the rail
    trough and a dragon standing on the stone scraped up around its own eggs.
    """
    problems = []
    for e in entities:
        pos = [v[1] for v in e['pos'][2]]
        bp = tuple(int(math.floor(v)) for v in pos)
        eid = e['nbt'][1]['id'][1]
        if not all(0 <= bp[i] < size[i] for i in range(3)):
            problems.append('%s baked at %s is outside the %s template' % (eid, bp, size))
            continue
        feet = blocks.get(bp, ('minecraft:air',))[0]
        head = blocks.get((bp[0], bp[1] + 1, bp[2]), ('minecraft:air',))[0]
        if not PASSABLE.search(feet):
            problems.append('%s baked at %s is standing inside %s' % (eid, bp, feet))
        elif not PASSABLE.search(head):
            problems.append('%s baked at %s has %s at head height' % (eid, bp, head))
    return problems


def audit(name, blocks):
    problems = []

    def at(pos):
        return blocks.get(pos, (None, None, None))[0]

    flooded = name in FLOODED
    chests = [p for p, v in blocks.items() if v[0] == 'minecraft:chest']

    for pos, (block, props, _) in sorted(blocks.items()):
        x, y, z = pos
        props = props or {}

        if block in LANTERNS:
            if props.get('hanging') == 'true':
                above = at((x, y + 1, z))
                if above is None or above in NOT_SUPPORT:
                    problems.append(f'{block} at {pos} hangs from {above or "nothing"}')
            else:
                below = at((x, y - 1, z))
                if below is None or below in NOT_SUPPORT:
                    problems.append(f'{block} at {pos} stands on {below or "nothing"}')

        if block == 'minecraft:rail':
            below = at((x, y - 1, z))
            if below is None or below in NOT_SUPPORT:
                problems.append(f'rail at {pos} sits on {below or "nothing"}')

        if block == 'minecraft:chest':
            dx, _, dz = DIRS[props['facing']]
            front = at((x + dx, y, z + dz))
            if front is not None and front not in ('minecraft:air', 'minecraft:water'):
                problems.append(f'chest at {pos} faces {props["facing"]} into {front}')
            for other in chests:
                if other != pos and abs(other[0] - x) + abs(other[1] - y) + abs(other[2] - z) == 1:
                    problems.append(f'chest at {pos} touches chest at {other} -- they merge')

        if flooded and any(block.endswith(s) for s in WATERLOGGABLE):
            if props.get('waterlogged') != 'true':
                problems.append(f'{block} at {pos} is underwater but not waterlogged')

        # Decoration walled in on all six sides is decoration nobody will ever see. Easy to do by
        # accident, because the build dict lets a later write quietly bury an earlier one.
        if block in LANTERNS | {'minecraft:cobweb', 'minecraft:chest', 'minecraft:iron_bars'}:
            if not any(see_through(at((x + dx, y + dy, z + dz)))
                       for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0),
                                          (0, -1, 0), (0, 0, 1), (0, 0, -1))):
                problems.append(f'{block} at {pos} is sealed inside solid blocks')

    return problems

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                   'data', 'hogwartsmagic', 'structure')

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    failures = 0
    for name, builder in BUILDS.items():
        built = builder()
        blocks, entities = built if isinstance(built, tuple) else (built, [])
        size = [max(p[i] for p in blocks) + 1 for i in range(3)]
        problems = audit(name, blocks) + audit_entities(blocks, entities, size)
        path = os.path.join(OUT, name + '.nbt')
        write_nbt(path, build_template(blocks, entities))
        kinds = len({v[0] for v in blocks.values()})
        print('%-18s %2dx%2dx%2d  %5d blocks  %2d block types  %2d entities  %6d bytes  %s'
              % (name, size[0], size[1], size[2], len(blocks), kinds, len(entities),
                 os.path.getsize(path),
                 'ok' if not problems else '%d PROBLEMS' % len(problems)))
        for p in problems:
            print('    ' + p)
        failures += len(problems)
    raise SystemExit(1 if failures else 0)
