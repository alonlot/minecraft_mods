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

import glob
import gzip
import io
import json
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
    """Write a gzipped, unnamed root compound -- the shape Minecraft expects for a template.

    mtime=0 because gzip otherwise stamps the current time into its header, which made every one of
    these templates show up as modified in git after any run of this script even when not a single
    block had changed. Minecraft ignores the field; six files of phantom diff per run is not worth
    the wall-clock it records.
    """
    buf = io.BytesIO()
    buf.write(struct.pack('>b', TAG_COMPOUND))
    buf.write(struct.pack('>H', 0))  # empty root name
    _write_payload(buf, root)
    with open(path, 'wb') as raw:
        with gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as fh:
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


# Direction.get2DDataValue(), which is what a painting's "facing" byte holds, and the horizontal
# counter-clockwise turn Painting.calculateBoundingBox uses to lay the canvas out sideways.
FACING_2D = {'south': 0, 'west': 1, 'north': 2, 'east': 3}
STEP = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
COUNTER_CLOCKWISE = {'north': 'west', 'west': 'south', 'south': 'east', 'east': 'north'}


RESOURCES = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..',
                         'src', 'main', 'resources')


def load_painting_sizes():
    """Read the registered variants instead of restating their sizes here.

    The size lives in the variant JSON, the anchor lives in the build, and the span the audit checks
    depends on both. Keeping a second copy of the size in this file is how a painting ends up half
    buried in a wall after somebody widens it.

    The texture is checked at the same time because the size is declared in three places, not two:
    a variant whose PNG is not exactly width*16 by height*16 fails to load, and the only symptom is
    a missing painting.
    """
    sizes = {}
    pattern = os.path.join(RESOURCES, 'data', 'hogwartsmagic', 'painting_variant', '*.json')
    for path in sorted(glob.glob(pattern)):
        with open(path, encoding='utf-8') as fh:
            data = json.load(fh)
        name = 'hogwartsmagic:' + os.path.splitext(os.path.basename(path))[0]
        sizes[name] = (data['width'], data['height'], data['asset_id'], path)
    return sizes


def png_size(path):
    """Width and height straight out of the IHDR chunk -- cheaper than depending on Pillow here."""
    with open(path, 'rb') as fh:
        head = fh.read(24)
    if head[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    return struct.unpack('>II', head[16:24])


def audit_painting_variants():
    problems = []
    for name, (w, h, asset_id, _) in PAINTING_SIZES.items():
        ns, _, rel = asset_id.partition(':')
        texture = os.path.join(RESOURCES, 'assets', ns, 'textures', 'painting', rel + '.png')
        if not os.path.exists(texture):
            problems.append('%s: asset_id %s has no texture at %s'
                            % (name, asset_id, os.path.normpath(texture)))
            continue
        actual = png_size(texture)
        if actual != (w * 16, h * 16):
            problems.append('%s: %dx%d variant needs a %dx%d texture, %s is %s'
                            % (name, w, h, w * 16, h * 16, os.path.basename(texture), actual))
    return problems


PAINTING_SIZES = load_painting_sizes()


def painting_span(ax, ay, az, facing, w, h):
    """Which blocks a w x h painting anchored at (ax, ay, az) actually covers.

    Worth doing in full rather than by eye. A painting's anchor is not its corner and not its middle:
    Painting.calculateBoundingBox pushes the canvas half a block counter-clockwise when the width is
    even and half a block up when the height is even, so a 4x3 hung at x=13 spans 12..15, which is
    not a span anyone guesses right. Reimplemented here from that method so the audit can check the
    canvas has air in front of it and solid wall behind, neither of which is visible from in here.
    """
    sx, sz = STEP[facing]
    cx, cy, cz = ax + 0.5 - sx * 0.46875, ay + 0.5, az + 0.5 - sz * 0.46875

    ccw_x, ccw_z = STEP[COUNTER_CLOCKWISE[facing]]
    if w % 2 == 0:
        cx, cz = cx + ccw_x * 0.5, cz + ccw_z * 0.5
    if h % 2 == 0:
        cy += 0.5

    thin = 0.0625
    size_x, size_y, size_z = (thin, h, w) if facing in ('east', 'west') else (w, h, thin)

    def cells(centre, size):
        lo, hi = centre - size / 2.0, centre + size / 2.0
        return range(int(math.floor(lo + 1e-6)), int(math.ceil(hi - 1e-6)))

    return [(x, y, z)
            for x in cells(cx, size_x)
            for y in cells(cy, size_y)
            for z in cells(cz, size_z)]


def painting(ax, ay, az, variant, facing, w, h):
    """A painting baked into the template, hung on the wall one step behind (ax, ay, az).

    Position is the anchor block's centre, not its corner, because the jigsaw rotates a pool element
    at random: transforming a corner lands on a block boundary and BlockPos.containing would then
    round it to the wrong block half the time. The facing byte needs no such care -- HangingEntity
    .rotate() turns the direction with the template.

    Vanilla logs one "Block-attached entity at invalid position" error per placement. That is
    unavoidable and harmless: BlockAttachedEntity.readAdditionalSaveData compares the TileX/Y/Z below
    against the world position StructureTemplate has already substituted into Pos, and template-local
    coordinates can never be within its 16-block tolerance of that. The position it keeps is the one
    Entity.load's reapplyPosition() derived from Pos, which is right, and moveTo overwrites it a
    moment later anyway.
    """
    return entity(ax + 0.5, ay + 0.5, az + 0.5, 'minecraft:painting', {
        'variant': ('string', variant),
        'facing': ('byte', FACING_2D[facing]),
        'TileX': ('int', ax),
        'TileY': ('int', ay),
        'TileZ': ('int', az),
    })


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
            # Plain strings are the common case (a chest's LootTable), but a banner's patterns are a
            # list of compounds, so a value that is already a typed tuple passes through untouched.
            tag['nbt'] = ('compound', {k: (v if isinstance(v, tuple) else ('string', v))
                                       for k, v in be.items()})
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


# BlockTags.DIRT, verbatim, because plants check it and it is narrower than it looks: gravel, sand,
# stone and mossy cobblestone are all absent. A fern rooted in any of those places without complaint,
# survives the audit that only looks at geometry, and then pops off as a dropped item the first time
# anything updates the block. Builds consult this before planting; the audit re-checks afterwards.
DIRT_TAG = {'minecraft:dirt', 'minecraft:grass_block', 'minecraft:podzol', 'minecraft:coarse_dirt',
            'minecraft:mycelium', 'minecraft:rooted_dirt', 'minecraft:moss_block', 'minecraft:mud',
            'minecraft:muddy_mangrove_roots'}
SAND_TAG = {'minecraft:sand', 'minecraft:red_sand', 'minecraft:suspicious_sand'}


# --- the builds ---------------------------------------------------------------------------------

DARK_OAK =[('minecraft:dark_oak_planks', 6), ('minecraft:dark_oak_log', 2), ('minecraft:spruce_planks', 2)]
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


# --- the Chamber of Secrets -----------------------------------------------------------------------
# Everything below builds one structure. It is by far the largest thing here and the only one whose
# subject is sculpture rather than architecture, so it gets its own toolkit: swept elliptical
# cross-sections instead of fill() boxes. A serpent head made of cuboids reads as a crate.

# Three tones, and the whole build depends on there being three. Everything was one grey to begin
# with -- pale statues on pale masonry -- and the result was that only the holes read: the eye
# sockets and the mouth showed, and the nose, cheeks and beard between them simply vanished. Relief
# is legible because of the shadow at its edge, and there is no shadow between two blocks of the
# same colour. So: near-black walls, mid-grey causeway underfoot, pale statuary standing off both.
CHAMBER_WALL = [('minecraft:deepslate_bricks', 8), ('minecraft:cracked_deepslate_bricks', 4),
                ('minecraft:deepslate_tiles', 3), ('minecraft:polished_deepslate', 2)]
CHAMBER_FLOOR = [('minecraft:deepslate_tiles', 6), ('minecraft:polished_deepslate', 4),
                 ('minecraft:cracked_deepslate_tiles', 3), ('minecraft:deepslate', 2)]
# The walk stays stone: in the film it is the one pale, wet surface in the room, and it is what the
# eye follows down the hall to the statue.
CAUSEWAY = [('minecraft:stone_bricks', 7), ('minecraft:mossy_stone_bricks', 4),
            ('minecraft:cracked_stone_bricks', 3), ('minecraft:stone', 2)]
# The statuary sits a little paler than the walls so twelve serpents do not sink into the masonry
# behind them. Kept inside one hue family and about 11 luma of spread -- wider than that and a
# blended palette stops reading as stone and starts reading as a chessboard. Cobblestone was in here
# and came out again: its texture is busy enough that at statue scale it read as damage, not stone.
SERPENT = [('minecraft:polished_andesite', 15), ('minecraft:andesite', 2)]
# The leaf and the shell are the mod's own unbreakable blocks. The leaf being its own block is what
# lets the door's block entity find itself unambiguously; both being unminable is what makes the
# Parseltongue lock the only way in rather than merely the polite way in.
SERPENT_DARK = [('minecraft:deepslate', 5), ('minecraft:polished_deepslate', 3),
                ('minecraft:blackstone', 2)]


def disc_yz(b, x, cy, cz, hh, hw, pal, salt):
    """Stamp one elliptical cross-section, in the YZ plane, of something sweeping along X.

    Sculpting by cross-section rather than by box is what lets a jaw taper. Half-extents are floats
    on purpose: the ellipse test is what rounds them, and letting a profile pass through 2.4 rather
    than snapping to 2 is the difference between a curve and a staircase.
    """
    hh, hw = max(hh, 0.5), max(hw, 0.5)
    for y in range(math.floor(cy - hh), math.ceil(cy + hh) + 1):
        for z in range(math.floor(cz - hw), math.ceil(cz + hw) + 1):
            dy, dz = (y - cy) / hh, (z - cz) / hw
            if dy * dy + dz * dz <= 1.0:
                b[(x, y, z)] = (tex(pal, x, y, z, salt), None, None)


def frame(dx, dz):
    """Unit forward and left-hand sideways vectors for a horizontal heading."""
    n = math.hypot(dx, dz) or 1.0
    fwd = (dx / n, dz / n)
    return fwd, (-fwd[1], fwd[0])


def station(fwd, side, t, across=0.0, cy=0.0):
    """Where a point t along the axis and `across` to the side lands, as float coordinates."""
    return (fwd[0] * t + side[0] * across, cy, fwd[1] * t + side[1] * across)


def disc_perp(b, fwd, side, t, cy, hh, hw, pal, salt):
    """One elliptical cross-section perpendicular to an arbitrary horizontal heading.

    disc_yz can only sweep along an axis, which forces every serpent to point straight across the
    hall. The film's heads angle inward and back down the room, and at 23 blocks long an
    axis-aligned head does not even fit the water channel beside the causeway. So the cross-section
    plane is spanned by Y and the sideways vector, and the y extent is solved rather than searched.
    """
    cx, cz = fwd[0] * t, fwd[1] * t
    for x in range(math.floor(cx - hw) - 1, math.ceil(cx + hw) + 2):
        for z in range(math.floor(cz - hw) - 1, math.ceil(cz + hw) + 2):
            rx, rz = x - cx, z - cz
            if abs(rx * fwd[0] + rz * fwd[1]) > 0.5:      # outside this slice of the sweep
                continue
            across = rx * side[0] + rz * side[1]
            room = 1.0 - (across / hw) ** 2
            if room <= 0:
                continue
            ext = hh * math.sqrt(room)
            for y in range(math.floor(cy - ext), math.ceil(cy + ext) + 1):
                if abs(y - cy) <= ext:
                    b[(x, y, z)] = (tex(pal, x, y, z, salt), None, None)


def sweep_dir(b, fwd, side, t0, t1, profile, pal, salt, step=0.34):
    """Sweep from t0 to t1 along a heading. profile(t) -> (centre_y, half_height, half_width).

    Stations overlap deliberately: each covers half a block either side of itself, so a step under
    one block cannot leave a diagonal sweep full of holes.
    """
    t = t0
    while t <= t1 + 1e-9:
        cy, hh, hw = profile(t)
        disc_perp(b, fwd, side, t, cy, max(hh, 0.5), max(hw, 0.5), pal, salt)
        t += step


FLIP_X = {'east': 'west', 'west': 'east', 'north': 'north', 'south': 'south'}
FLIP_SHAPE = {'inner_left': 'inner_right', 'inner_right': 'inner_left',
              'outer_left': 'outer_right', 'outer_right': 'outer_left', 'straight': 'straight'}


def blit(dst, src, ox, oy, oz, mirror_x=False, bounds=None):
    """Copy a locally-built part into the main dict, optionally mirrored across its own X axis.

    Mirroring is why the parts are built in their own frame at all: the hall has a serpent on each
    side facing inward, and a head sculpted twice by hand is a head that ends up subtly different
    twice. Blockstates have to be mirrored too -- a stair keeps its facing when you negate its
    coordinate, which silently turns a matched pair into a mismatched one.

    `bounds` clips to the room. The serpents are deliberately longer than the channel is wide, so
    that their necks and coils run back out through the side wall and the head reads as something
    emerging from the stone rather than as an ornament standing in a puddle.
    """
    for (x, y, z), (name, props, be) in src.items():
        if mirror_x:
            x = -x
            if props and ('facing' in props or 'shape' in props):
                props = dict(props)
                if 'facing' in props:
                    props['facing'] = FLIP_X.get(props['facing'], props['facing'])
                if 'shape' in props:
                    props['shape'] = FLIP_SHAPE.get(props['shape'], props['shape'])
        pos = (ox + x, oy + y, oz + z)
        if bounds and not all(bounds[i][0] <= pos[i] <= bounds[i][1] for i in range(3)):
            continue                                  # tail of the serpent, buried in the wall
        dst[pos] = (name, props, be)


def jaw_surface(b, x, z, cy, down):
    """First empty cell past the jaw's own surface, walking out from the axis at this station.

    Teeth were originally placed at an offset computed from the jaw's profile, which is off by one
    wherever the ellipse rounds the other way -- and a fang hanging one block clear of the jaw reads
    as a stalactite that happens to be nearby. Asking the blocks where the surface actually is
    cannot drift.
    """
    y = int(round(cy))
    if (x, y, z) not in b:
        return None
    step = -1 if down else 1
    while (x, y + step, z) in b:
        y += step
    return y + step


def fang(b, x, z, y, length, down, salt):
    """One dripstone tooth. Tapers to a tip, which is the whole reason it is not a stone column."""
    step = -1 if down else 1
    vertical = 'down' if down else 'up'
    for i in range(length):
        thickness = 'base' if i == 0 else ('tip' if i == length - 1 else 'middle')
        if length > 3 and i == 1:
            thickness = 'frustum'
        b[(x, y + step * i, z)] = ('minecraft:pointed_dripstone',
                                   {'vertical_direction': vertical, 'thickness': thickness,
                                    'waterlogged': 'false'}, None)


UPPER_RISE, LOWER_DROP = 0.62, 0.52       # per block of jaw; together a gape of about 65 degrees
UPPER_LEN, LOWER_LEN = 14, 12


def upper_jaw_axis(t):
    return 2.5 + UPPER_RISE * t


def lower_jaw_axis(t):
    return 0.5 - LOWER_DROP * t


def cranium_profile(t):
    """t runs backwards from the hinge, 0 to -7. Wider than tall throughout: a snake's skull is a
    flattened wedge, and the first version of this profile was as tall as it was broad -- which read
    as a fish. Its front section matches the base of the upper jaw so the two masses read as one
    skull with a kink in it, rather than as a lump with a plank stuck on the front."""
    u = -t
    return (2.8 + 0.2 * u - 0.05 * u * u,
            3.4 + 0.25 * u - 0.06 * u * u,
            5.2 + 0.3 * u - 0.08 * u * u)


def upper_profile(t):
    """Broad in section all the way out -- a snake's snout is a blade, not a spike, and half-width
    held well above half-height is what keeps it from reading as a beak. The last two blocks pull in
    hard to make a blunt nose; a tapered sweep left to itself trails off into a weightless nub."""
    if t <= UPPER_LEN:
        return (upper_jaw_axis(t), 2.9 - 0.10 * t, 5.2 - 0.16 * t)
    over = t - UPPER_LEN
    return (upper_jaw_axis(UPPER_LEN) - 0.5 * over, 2.1 - 0.2 * over, 3.4 - 0.4 * over)


def lower_profile(t):
    """Drops away from the hinge, shorter and slimmer than the upper jaw."""
    if t <= LOWER_LEN:
        return (lower_jaw_axis(t), 2.0 - 0.07 * t, 4.5 - 0.16 * t)
    return (lower_jaw_axis(LOWER_LEN) + 0.4, 1.6, 2.8)


def serpent_head(heading, salt=0):
    """One open-jawed serpent head, hinge at the local origin, snout pointing along `heading`.

    Built as three swept masses -- cranium, upper jaw, lower jaw -- because the gape is the whole
    silhouette. The jaws diverge at roughly 65 degrees, far wider than a real snake and exactly what
    the film's statues do: from the causeway you are meant to be looking into an open mouth.

    About 23 long by 19 tall by 13 wide, and that scale is not negotiable. Built at 13 long first,
    the same profiles produced a lumpy grey box: below roughly twenty blocks there is no room for a
    brow, an eye and a jawline to be separately legible, so they average into one shape.
    """
    h = {}
    fwd, side = frame(*heading)
    sweep_dir(h, fwd, side, -7.0, 0.0, cranium_profile, SERPENT, salt)
    sweep_dir(h, fwd, side, 0.0, UPPER_LEN + 2.0, upper_profile, SERPENT, salt + 1)
    sweep_dir(h, fwd, side, 0.0, LOWER_LEN + 1.0, lower_profile, SERPENT, salt + 2)

    def cell(t, across, cy):
        fx, fy, fz = station(fwd, side, t, across, cy)
        return (round(fx), round(fy), round(fz))

    # Throat, at the hinge only. An earlier version filled the whole cavity between the jaws and the
    # head lost its gape entirely -- from outside, a plugged mouth is just a solid head.
    for t in (0.4, 1.0, 1.6, 2.2):
        for across in range(-4, 5):
            for y in range(-2, 5):
                pos = cell(t, across, y)
                if pos not in h:
                    put(h, pos, tex(SERPENT_DARK, pos[0], pos[1], pos[2], salt + 4))

    # Brow ridge: an arc standing proud of the skull, from behind the eye forward onto the snout.
    # This one detail does most of the work of turning a smooth lump into a face, so it is a solid
    # bar rather than a line of single blocks -- one block of relief simply disappears.
    for t10 in range(-60, 45, 4):
        t = t10 / 10.0
        ridge = int(5.6 + 0.16 * t - 0.03 * t * t)
        for sa in (-1, 1):
            for across in (4, 5):
                for y in (ridge, ridge + 1):
                    pos = cell(t, sa * across, y)
                    put(h, pos, tex(SERPENT, pos[0], pos[1], pos[2], salt + 3))
            pos = cell(t, sa * 5, ridge - 1)
            put(h, pos, tex(SERPENT, pos[0], pos[1], pos[2], salt + 3))

    # Sunken eye under the ridge: a dark socket with a blacker pupil inside it.
    for t10 in range(-50, 4, 3):
        t = t10 / 10.0
        for sa in (-1, 1):
            for y in range(1, 5):
                pos = cell(t, sa * 5, y)
                put(h, pos, tex(SERPENT_DARK, pos[0], pos[1], pos[2], salt + 5))
            if -4.4 <= t <= -1.6:
                for y in (2, 3):
                    put(h, cell(t, sa * 5, y), 'minecraft:polished_blackstone')
                    put(h, cell(t, sa * 6, y), 'minecraft:polished_blackstone')

    # Fangs: upper pair hanging, lower pair standing, offset along the jaw so they bite past each
    # other rather than meeting point to point. Rooted by asking the jaw where its surface actually
    # is -- an offset computed from the profile is off by one wherever the ellipse rounds the other
    # way, and a fang hanging one block clear of the jaw reads as a nearby stalactite.
    for sa in (-1, 1):
        for t, across, length, down in ((10, 2, 4, True), (7, 2, 3, False)):
            axis = upper_jaw_axis(t) if down else lower_jaw_axis(t)
            x, _, z = cell(t, sa * across, axis)
            y = jaw_surface(h, x, z, axis, down)
            if y is not None:
                fang(h, x, z, y, length, down, salt)
    return h


def ball(b, cx, cy, cz, r, pal, salt):
    """A sphere at float coordinates. Necks and coils curve, and a curve stamped as boxes is a stair."""
    for x in range(math.floor(cx - r), math.ceil(cx + r) + 1):
        for y in range(math.floor(cy - r), math.ceil(cy + r) + 1):
            for z in range(math.floor(cz - r), math.ceil(cz + r) + 1):
                if (x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2 <= r * r:
                    b[(x, y, z)] = (tex(pal, x, y, z, salt), None, None)


def serpent(b, ox, oy, oz, heading, mirror, salt, bounds=None):
    """A whole serpent: head on a neck that dives back into the water, and a coil where it enters.

    Assembled in the head's own local frame and blitted once, so the pair either side of the causeway
    are the same sculpture rather than two hand-built approximations of each other.
    """
    h = serpent_head(heading, salt)
    fwd, side = frame(*heading)

    # Neck: continues back from the cranium, dropping faster as it goes, so it meets the water on a
    # curve instead of a ramp.
    for i in range(49):
        s = i / 48.0
        px, _, pz = station(fwd, side, -6.5 - 6.0 * s, 0, 0)
        ball(h, px, -0.5 - 13.5 * s * s, pz, 2.5 + 1.7 * s, SERPENT, salt + 6)

    # Coil: where the body breaks the surface, swinging out sideways. Without it the neck reads as a
    # pipe going into a hole rather than as an animal with more of itself underneath.
    bx, _, bz = station(fwd, side, -12.5, 0, 0)
    for i in range(33):
        a = math.pi * (-0.1 + 1.0 * i / 32.0)
        ball(h, bx + 4.6 * math.cos(a), -13.2 - 1.2 * math.sin(a) ** 2, bz + 4.6 * math.sin(a),
             3.4, SERPENT, salt + 7)
    blit(b, h, ox, oy, oz, mirror, bounds)


def serpent_column(b, cx, cz, y0, y1, salt):
    """A full-height pillar with a serpent wound round it, one of the pair framing each bay.

    The heads are twenty-odd blocks long and cannot be repeated often enough to give a sixty-block
    hall a beat. The columns carry that rhythm instead, and they are what reads as a colonnade.
    """
    for y in range(y0, y1 + 1):
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if abs(dx) + abs(dz) <= 3:
                    put(b, (cx + dx, y, cz + dz), tex(CHAMBER_WALL, cx + dx, y, cz + dz, salt))
    # The shaft stays dark. It carried a pale serpent wound round it to begin with, and against
    # eight pale serpent heads that was one pale thing too many -- the colonnade and the statuary
    # fused into a single mass. Architecture dark, statuary pale, and both become legible.
    for i in range((y1 - y0) * 2):
        y = y0 + i / 2.0
        a = i / 2.0 * (2 * math.pi / 7.0)
        ball(b, cx + 2.6 * math.cos(a), y, cz + 2.6 * math.sin(a), 1.2, CHAMBER_WALL, salt + 1)

    # Capital and base, so the shaft lands on something instead of just stopping.
    for y, spread in ((y0, 3), (y1 - 1, 4), (y1, 3)):
        for dx in range(-spread, spread + 1):
            for dz in range(-spread, spread + 1):
                if abs(dx) + abs(dz) <= spread + 1:
                    put(b, (cx + dx, y, cz + dz), 'minecraft:chiseled_deepslate')


def round_door(b, cx, cy, cz, r):
    """The threshold between antechamber and hall: the round door, built shut.

    It is shut because it is meant to be opened, and opening it is the point -- a Serpensortia cast
    at the lock in the middle retracts the seven serpent bolts one at a time and then unwinds the
    leaf. See ChamberDoorBlock and ChamberDoorBlockEntity.

    The leaf is tuff rather than the deepslate around it for two reasons. It reads as a separate
    object, which a door has to; and the block entity finds the leaf by scanning for tuff, so the
    leaf has to be made of something the rest of the Chamber is not.
    """
    for x in range(cx - r - 2, cx + r + 3):
        for y in range(cy - r - 2, cy + r + 3):
            d = math.hypot(x - cx, (y - cy) * 1.02)
            for z in range(cz, cz + 3):
                if d <= r:
                    put(b, (x, y, z), 'hogwartsmagic:chamber_door_leaf')
                elif d <= r + 1.9:
                    put(b, (x, y, z), 'hogwartsmagic:chamber_stone')

    # Seven serpent bolts around the rim -- the lock the film opens with a hiss. They stand one block
    # proud of the leaf on the antechamber side, so they are the first thing seen and the first thing
    # to move.
    for i in range(7):
        a = 2 * math.pi * i / 7.0 + 0.3
        bx, by = round(cx + (r - 0.6) * math.cos(a)), round(cy + (r - 0.6) * math.sin(a))
        put(b, (bx, by, cz), 'minecraft:polished_blackstone')
        put(b, (bx, by, cz - 1), 'minecraft:lightning_rod',
            {'facing': 'north', 'powered': 'false', 'waterlogged': 'false'})

    # The lock itself, dead centre, where a spell can reach it.
    put(b, (cx, cy, cz), 'hogwartsmagic:chamber_door', {'open': 'false'})


# --- Salazar Slytherin, at the far end -------------------------------------------------------------
# The face is a heightmap, not a stack of boxes: for every column of the end wall, `face_depth` says
# how far the stone stands forward. Modelling a face as nested cuboids is exactly what makes blocky
# faces look like waffles. A relief driven by one continuous function does not have that failure
# mode, and it retunes by changing a number rather than by rebuilding the geometry.

FACE_CX, MOUTH_Y0, MOUTH_Y1 = 24, 12, 17


def face_depth(x, y):
    """Blocks of relief at this point of the end wall. 0 is flat wall, negative cuts back in.

    Every feature has to fit inside the thirteen blocks the bay is deep, and that budget is the whole
    design. An earlier version spent twelve of them on the skull alone, so the nose -- which is only
    a nose because it stands proud of the cheeks -- hit the clamp and came out flat. The base masses
    stay shallow enough that the features on top of them still have somewhere to go.

    Proportions were tuned by rendering, and by overshooting first. A wider skull read as an egg with
    eyes; a hairline band drawn round the dome read as blotches rather than as hair, and came out
    again. What actually makes it Salazar rather than a skull is the beard: it is the largest element
    in the composition, it hangs from the chin all the way down to the dais, and the tone-drawn
    strands are what stop it reading as one slab.
    """
    dx = abs(x - FACE_CX)
    d = 0.0

    skull = (dx / 12.0) ** 2 + ((y - 23.0) / 7.5) ** 2      # cranium and temples
    if skull <= 1.0:
        d = max(d, 3.0 + 6.0 * math.sqrt(1.0 - skull))
    jaw = (dx / 10.0) ** 2 + ((y - 19.0) / 6.5) ** 2        # cheeks and jaw
    if jaw <= 1.0:
        d = max(d, 3.0 + 5.0 * math.sqrt(1.0 - jaw))

    if 3.0 <= y <= 16.5:                                    # beard, hung from the chin to the dais
        half = 4.5 + 10.0 * math.sin(math.pi * min(1.0, (y - 2.0) / 15.5))
        if dx <= half:
            lobe = 3.5 + 4.0 * math.sqrt(max(0.0, 1.0 - (dx / half) ** 2))
            d = max(d, lobe + 1.6 * math.sin(dx * 1.05))    # strands, cut along its length

    if dx <= 3.0 and 17.5 <= y <= 26.5:                     # nose, heaviest at the tip
        d += 2.6 + 1.8 * (26.5 - y) / 9.0
    if 3.5 <= dx <= 10.0 and 18.5 <= y <= 20.5:             # moustache
        d += 2.2
    if dx <= 12.0 and 26.5 <= y <= 28.0:                    # brow
        d += 2.2
    if math.hypot(dx - 6.5, y - 24.2) <= 3.2:               # eye sockets, cut back under the brow
        d -= 5.5
    return d


def beard_strand(x, y):
    """True in the valley between two strands of the beard.

    The strands are cut as depth, but depth alone is only visible if something shades it, and a hall
    lit by four lanterns shades very little. Darkening the valleys draws the same lines in tone,
    which reads from the far end of the causeway where the relief on its own does not.
    """
    return 3.0 <= y <= 16.5 and math.sin(abs(x - FACE_CX) * 1.75) < -0.80


def slytherin_face(b, w, z_wall, salt):
    """Carve Salazar into the end wall, with the mouth left open as the basilisk's tunnel."""
    for x in range(4, w - 4):
        for y in range(4, 29):
            d = int(round(face_depth(x, y)))
            if d <= 0:                                  # flat wall, or a socket cut back into it
                continue
            for z in range(max(z_wall - d + 1, z_wall - 13), z_wall + 1):
                put(b, (x, y, z), tex(SERPENT, x, y, z, salt))

    # Strand lines down the beard. A dark seam was drawn under the moustache as well, to separate
    # the face from the beard, and it had to come out: seventeen blocks of unbroken dark across the
    # widest part of the head read as a bar laid over the statue, not as a shadow under a lip. The
    # moustache already separates them in depth, which is the honest way to do it.
    for x in range(4, w - 4):
        for y in range(3, 18):
            d = int(round(face_depth(x, y)))
            if d <= 0:
                continue
            if beard_strand(x, y):
                for z in range(z_wall - d + 1, min(z_wall - d + 2, z_wall + 1)):
                    put(b, (x, y, z), tex(SERPENT_DARK, x, y, z, salt + 2))

    # Eyes: sunk sockets with a lit core -- the one place in the hall where the stone looks back.
    # Guarded to the skull mass, because the socket is a disc in its own right and without the guard
    # it paints dark blocks onto bare wall wherever it overhangs the head, which reads as two ragged
    # wings stuck to the sides of the face.
    for sx in (-1, 1):
        for x in range(4, w - 4):
            for y in range(20, 30):
                dx = abs(x - FACE_CX)
                on_skull = (dx / 12.0) ** 2 + ((y - 23.0) / 7.5) ** 2 <= 1.0
                if on_skull and math.hypot(dx - 6.5, y - 24.2) <= 3.2:
                    for z in range(z_wall - 6, z_wall + 1):
                        put(b, (x, y, z), tex(SERPENT_DARK, x, y, z, salt + 1))
        put(b, (FACE_CX + sx * 7, 24, z_wall - 5), 'minecraft:sea_lantern')
        put(b, (FACE_CX + sx * 7, 24, z_wall - 4), 'minecraft:green_stained_glass')

    # The mouth, and the shaft behind it. Left open all the way back to the shell so it reads as a
    # throat that goes somewhere rather than as a niche.
    for x in range(FACE_CX - 3, FACE_CX + 4):
        for y in range(MOUTH_Y0, MOUTH_Y1 + 1):
            for z in range(z_wall - 13, z_wall + 1):
                b.pop((x, y, z), None)
    for x in range(FACE_CX - 4, FACE_CX + 5):               # lip, so the opening has an edge
        for y in (MOUTH_Y0 - 1, MOUTH_Y1 + 1):
            for z in range(z_wall - 2, z_wall + 1):
                put(b, (x, y, z), 'minecraft:polished_blackstone')
    for y in range(MOUTH_Y0 - 1, MOUTH_Y1 + 2):
        for x in (FACE_CX - 4, FACE_CX + 4):
            for z in range(z_wall - 2, z_wall + 1):
                put(b, (x, y, z), 'minecraft:polished_blackstone')
    for z in range(z_wall - 12, z_wall + 1, 3):             # green light, from inside the throat
        for x in (FACE_CX - 2, FACE_CX + 2):
            put(b, (x, MOUTH_Y0 - 1, z), 'minecraft:sea_lantern')
            put(b, (x, MOUTH_Y1 + 1, z), 'minecraft:green_stained_glass')


def shed_skin(b, x0, z0, y0):
    """The sloughed basilisk skin from the film, lying in a slack curve across the antechamber."""
    for i in range(64):
        s = i / 63.0
        cx = x0 + 13.0 * s + 2.4 * math.sin(s * 5.0)
        cz = z0 + 9.0 * math.sin(s * 2.6)
        r = 1.9 - 1.1 * s                                   # tapers toward the tail
        axis = 'x' if abs(math.cos(s * 5.0)) > 0.4 else 'z'
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                for dy in range(0, 3):
                    if math.hypot(dx, dz) <= r and dy <= r:
                        put(b, (round(cx) + dx, y0 + dy, round(cz) + dz),
                            'minecraft:bone_block', {'axis': axis})


def chamber_hall():
    """The Chamber of Secrets: a flooded serpent hall under Slytherin's own face.

    Laid out along Z. The player arrives through a pipe at z=0 into an antechamber, passes the round
    door at z=20, and walks a causeway between two flooded channels the length of the room to the
    statue at z=97. Every proportion here is in service of one shot -- looking down the hall from the
    door, serpents crowding the walkway, the face lit green at the end.
    """
    W, H, D = 49, 30, 99
    CX0, CX1, WATER, WALK = 21, 27, 4, 5
    ANTE_Z, DOOR_Z, HALL_Z, BAY_Z = 18, 20, 22, 84
    b = {}

    # Shell: solid, then hollowed. Sealed on all six faces on purpose -- this is placed at an
    # absolute depth in solid rock, and the shell is what stops a flooded 99-block hall from finding
    # a lava cave next door and draining into it.
    fill(b, 0, 0, 0, W - 1, H - 1, D - 1, 'minecraft:deepslate_bricks')
    fill(b, 1, 1, 1, W - 2, H - 2, D - 2, 'minecraft:air')
    fill(b, 1, 14, 1, W - 2, H - 2, ANTE_Z, 'minecraft:deepslate_bricks')   # antechamber's low ceiling
    fill(b, 1, 1, DOOR_Z - 1, W - 2, H - 2, DOOR_Z + 1, 'hogwartsmagic:chamber_stone')  # bulkhead

    fill_tex(b, 0, 0, 0, W - 1, 0, D - 1, CHAMBER_FLOOR, 31)
    for y in range(1, H - 1):
        fill_tex(b, 0, y, 0, W - 1, y, 0, CHAMBER_WALL, 31, ground=1)
        fill_tex(b, 0, y, D - 1, W - 1, y, D - 1, CHAMBER_WALL, 31, ground=1)
        fill_tex(b, 0, y, 1, 0, y, D - 2, CHAMBER_WALL, 31, ground=1)
        fill_tex(b, W - 1, y, 1, W - 1, y, D - 2, CHAMBER_WALL, 31, ground=1)
    fill_tex(b, 0, H - 1, 0, W - 1, H - 1, D - 1, CHAMBER_WALL, 34)

    # Causeway and the two channels either side of it. The walk stands one block above the water --
    # any more and the hall stops feeling flooded, any less and it reads as a ford.
    for z in range(DOOR_Z + 2, D - 1):
        for x in range(1, W - 1):
            if CX0 <= x <= CX1:
                fill_tex(b, x, 1, z, x, WALK, z, CAUSEWAY, 32)
            else:
                fill(b, x, 1, z, x, WATER, z, 'minecraft:water', {'level': '0'})
    for z in range(HALL_Z, D - 1):                          # kerb, so the causeway has an edge
        for x in (CX0 - 1, CX1 + 1):
            slab(b, (x, WALK, z), 'minecraft:stone_brick_slab', 'top')

    # The serpents. Four pairs rather than six: at twenty-three blocks long, any closer spacing has
    # them intersecting, and four that read are worth more than six merged into one grey mass.
    inside = ((1, W - 2), (1, H - 2), (1, D - 2))
    heading = (0.92, -0.39)
    for i, z in enumerate((27, 44, 61, 78)):
        serpent(b, 7, 13, z, heading, False, 40 + i * 3, inside)
        serpent(b, W - 8, 13, z, heading, True, 40 + i * 3, inside)
    for i, z in enumerate(range(HALL_Z + 2, BAY_Z - 2, 10)):
        serpent_column(b, 4, z, 1, H - 3, 60 + i)
        serpent_column(b, W - 5, z, 1, H - 3, 60 + i)

    # Entrance: the antechamber floor, the pipe bored through the end wall, then the door.
    fill_tex(b, 1, 1, 1, W - 2, 4, ANTE_Z, CAUSEWAY, 33)
    for y in range(5, 13):
        for x in range(FACE_CX - 4, FACE_CX + 5):
            for z in range(0, 6):
                if math.hypot(x - FACE_CX, (y - 8.5) * 1.15) <= 3.2:
                    b.pop((x, y, z), None)
    shed_skin(b, 8, 9, 5)
    round_door(b, FACE_CX, 9, DOOR_Z - 1, 4)

    # The statue bay: a dais at the causeway's end, then Salazar.
    for x in range(6, W - 6):
        for z in range(BAY_Z, D - 1):
            fill_tex(b, x, 1, z, x, WALK, z, CAUSEWAY, 37)
    for i in range(3):                                      # steps up onto the dais
        for x in range(CX0 - 3 + i, CX1 + 4 - i):
            fill_tex(b, x, 1, BAY_Z + 2 + i, x, WALK + 1 + i, BAY_Z + 2 + i, CAUSEWAY, 38)
    slytherin_face(b, W, D - 2, 39)

    # Lighting. Green from the statue, cold blue-white from the colonnade, and nothing at all
    # overhead -- the ceiling being lost in the dark does as much work as anything that is lit.
    for z in range(HALL_Z + 2, BAY_Z - 2, 10):
        for x in (8, W - 9):
            put(b, (x, H - 5, z), 'minecraft:soul_lantern', {'hanging': 'true'})
    for z in range(HALL_Z + 6, BAY_Z, 12):                  # sconces just above the waterline
        put(b, (1, 8, z), 'minecraft:glowstone')
        put(b, (2, 8, z), 'minecraft:green_stained_glass')
        put(b, (W - 2, 8, z), 'minecraft:glowstone')
        put(b, (W - 3, 8, z), 'minecraft:green_stained_glass')

    # Bones in the shallows, and the two chests worth the walk.
    for i, (x, z) in enumerate(((13, 36), (35, 52), (15, 69), (33, 27), (11, 58))):
        for dx in (0, 1):
            put(b, (x + dx, 1, z), 'minecraft:bone_block', {'axis': 'x' if i % 2 else 'z'})
        if i % 2 == 0:
            put(b, (x, 2, z), 'minecraft:skeleton_skull', {'rotation': str(4 * i % 16)})
    # Both on the dais, both on the Chamber's own table. This is the deepest, best-defended room
    # in the mod and it was handing out vanilla stronghold loot.
    chest(b, (FACE_CX - 11, WALK + 1, BAY_Z + 2), 'north', 'hogwartsmagic:chests/chamber_of_secrets')
    chest(b, (FACE_CX + 11, WALK + 1, BAY_Z + 2), 'north', 'hogwartsmagic:chests/chamber_of_secrets')
    # One in the antechamber, beside the shed skin, as a consolation for anyone who gets this
    # far and cannot open the door.
    chest(b, (6, 5, 12), 'east', 'minecraft:chests/stronghold_corridor')

    # Waterlog everything standing in the channels. Doing this in one pass at the end rather than
    # remembering it at each call site is deliberate: waterlogging is invisible while you are placing
    # blocks, and only shows up in game as a slab with a bubble of air trapped inside it.
    for pos, (block, props, be) in list(b.items()):
        if pos[1] <= WATER and not (CX0 <= pos[0] <= CX1) and pos[2] > ANTE_Z:
            if any(block.endswith(s) for s in WATERLOGGABLE):
                props = dict(props or {})
                props['waterlogged'] = 'true'
                b[pos] = (block, props, be)

    # Seal it. The Chamber is meant to have exactly one way in, and a room you can tunnel into
    # through its own wall has none. So the whole build gets wrapped in a skin of unbreakable chamber
    # stone one block outside the masonry the player actually sees -- which is the point of wrapping
    # rather than just hardening the walls: the visible stonework stays a weathered four-way blend,
    # and the boundary that refuses a pickaxe is the buried layer nobody ever looks at.
    sealed = {}
    fill(sealed, 0, 0, 0, W + 1, H + 1, D + 1, 'hogwartsmagic:chamber_stone')
    # Every cell of the old box is copied explicitly, defaulting to air, and the default is the whole
    # point. Carving here is done with b.pop() -- the mouth shaft, the entrance pipe -- which leaves
    # no entry at all rather than an air one. Copying only the entries that exist let the wrap's
    # chamber stone stand in every hole that had been deliberately cut, so the basilisk's mouth and
    # the one way into the Chamber both came out plugged with unbreakable stone.
    for x in range(W):
        for y in range(H):
            for z in range(D):
                sealed[(x + 1, y + 1, z + 1)] = b.get((x, y, z), ('minecraft:air', None, None))
    # The entrance pipe has to come through the new skin too, or the one way in is sealed as well.
    for y in range(5, 13):
        for x in range(FACE_CX - 4, FACE_CX + 5):
            if math.hypot(x - FACE_CX, (y - 8.5) * 1.15) <= 3.2:
                sealed.pop((x + 1, y + 1, 0), None)

    # One basilisk, on the dais under the mouth it came out of. Baked rather than spawned: the hall
    # is a single boss encounter, and the structure's spawn overrides are empty so that nothing else
    # wanders in behind it.
    return sealed, [entity(FACE_CX + 1.5, WALK + 2.0, BAY_Z + 1.5, 'hogwartsmagic:basilisk',
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


CLEARING = [('minecraft:podzol', 6), ('minecraft:coarse_dirt', 5), ('minecraft:grass_block', 3),
            ('minecraft:mossy_cobblestone', 2)]


def willow_clearing():
    """A ring of trodden, root-broken ground with the willow itself standing in the middle.

    The tree is baked in rather than spawned: a landmark whose landmark is missing is just a bald
    patch. The clearing is deliberately wider than the canopy so the silhouette reads from outside
    the branches -- which is also the range at which the wind-up tells are still readable.
    """
    W, H, D = 23, 6, 23
    b = {}
    cx = cz = 11
    for x in range(W):
        for z in range(D):
            d = math.hypot(x - cx, z - cz)
            if d > 11.0:
                continue
            # Bare and beaten flat where the branches sweep; grass survives only at the fringe.
            pal = CLEARING if d > 7.5 else [('minecraft:coarse_dirt', 7), ('minecraft:podzol', 4),
                                            ('minecraft:dirt', 3)]
            put(b, (x, 0, z), tex(pal, x, 0, z, 91))
            # The mossy cobblestone in CLEARING is not soil, whatever it looks like -- a fern landing
            # on one of those tiles drops itself as an item the moment the block updates.
            if d > 8.5 and noise(x, 1, z, 92) > 0.86 and b[(x, 0, z)][0] in DIRT_TAG:
                put(b, (x, 1, z), 'minecraft:fern')

    # Roots breaking the surface, and the wreckage of whatever tried to stand here before.
    for i in range(10):
        a = i * math.pi / 5.0
        r = 6.0 + noise(i, 0, 0, 93) * 3.0
        rx, rz = int(cx + math.cos(a) * r), int(cz + math.sin(a) * r)
        if 0 <= rx < W and 0 <= rz < D:
            put(b, (rx, 1, rz), 'minecraft:oak_log', {'axis': 'x' if i % 2 else 'z'})
    rubble(b, [(3, 1, 6), (18, 1, 15), (7, 1, 19), (16, 1, 4)],
           [('minecraft:mossy_cobblestone', 5), ('minecraft:cobblestone', 4)], 94)
    chest(b, (4, 1, 4), 'south', 'minecraft:chests/simple_dungeon')

    return b, [entity(cx + 0.5, 1.0, cz + 0.5, 'hogwartsmagic:whomping_willow',
                      {'PersistenceRequired': ('byte', 1)})]


# Weighted for TONE, not just variety. The first mix put podzol (luma 66) next to gravel (128) and
# dirt_path (122) at even weights, and at one-block frequency that is not texture, it is a
# chessboard -- which only became obvious once the preview drew real textures instead of flat
# average colours. Everything here now sits in one hue family inside a ~40 luma spread, with the two
# bright outliers kept rare enough to read as incident rather than as pattern.
CAMP_GROUND = [('minecraft:coarse_dirt', 8), ('minecraft:podzol', 5), ('minecraft:dirt', 3),
               ('minecraft:gravel', 1)]
CAMP_TRODDEN = [('minecraft:coarse_dirt', 10), ('minecraft:dirt', 4), ('minecraft:podzol', 3),
                ('minecraft:dirt_path', 1)]
RITE_STONE = [('minecraft:deepslate_bricks', 6), ('minecraft:cracked_deepslate_bricks', 5),
              ('minecraft:cobbled_deepslate', 4), ('minecraft:blackstone', 3),
              ('minecraft:polished_blackstone', 2)]

CAMP_W = CAMP_D = 27
CAMP_CX = CAMP_CZ = 13
WALL_Z = 7            # south face of the rite wall; the painting hangs at WALL_Z + 1
# The wall was 15 wide and 2 thick to begin with, which in a forest clearing read as a rampart
# rather than as something raised in a hurry to hang a mark on. Eight wide, and two thick only
# across the middle where the canvas needs backing. It brackets the 4x3 painting exactly.
WALL_X0, WALL_X1 = 10, 17
CANOPY_R = 12.0
CANOPY_H = 11


def camp_clearing(b):
    """Trodden ground, beaten bare where the camp actually stands and fraying at the edges."""
    for x in range(CAMP_W):
        for z in range(CAMP_D):
            d = math.hypot(x - CAMP_CX, z - CAMP_CZ)
            if d > 12.6:
                continue
            pal = CAMP_TRODDEN if d < 8.0 else CAMP_GROUND
            put(b, (x, 0, z), tex(pal, x, 0, z, 61))
            # Undergrowth survives only past the ring of trunks, which is what makes the inside of
            # the ring read as occupied rather than as a gap in the trees. Skipped over the gravel
            # in the palette -- neither plant can root in it.
            if d > 9.5 and noise(x, 1, z, 62) > 0.82 and b[(x, 0, z)][0] in DIRT_TAG:
                put(b, (x, 1, z), 'minecraft:fern' if noise(x, 1, z, 63) > 0.4
                    else 'minecraft:dead_bush')


DAIS = [('minecraft:polished_deepslate', 6), ('minecraft:deepslate_tiles', 5),
        ('minecraft:polished_blackstone', 3), ('minecraft:cracked_deepslate_tiles', 2)]


def dark_mark_banner(b, pos, facing):
    """A black banner carrying vanilla's own skull pattern -- as close to the Mark as blocks get."""
    b[pos] = ('minecraft:black_wall_banner', {'facing': facing},
              {'id': 'minecraft:banner',
               'patterns': ('list', 'compound', [
                   ('compound', {'pattern': ('string', 'minecraft:skull'),
                                 'color': ('string', 'white')}),
               ])})


def ritual_circle(b):
    """The heart of the camp: a raised dais, a ring of standing stones, and runes cut into the floor.

    This replaces a flat blackstone hearth with a campfire on it. A camp arranged around a fire needs
    the fire to be the strongest thing in the clearing, and paving plus one campfire is not -- it
    reads as a courtyard with a spark in it. What carries here is the same trick a real ritual site
    uses: concentric structure. Dais, then stones, then an inscribed circle, each at its own radius,
    so the eye is walked inward.

    Green is done with light, not flame. Vanilla has no green fire; soul fire is blue-cyan and is as
    close as the game gets, so the flame stays soul-blue and the GLOW around it is froglight.
    """
    # Raised dais.
    for x in range(CAMP_CX - 2, CAMP_CX + 3):
        for z in range(CAMP_CZ - 2, CAMP_CZ + 3):
            if abs(x - CAMP_CX) + abs(z - CAMP_CZ) > 3:
                continue
            put(b, (x, 0, z), tex(DAIS, x, 0, z, 640))
            put(b, (x, 1, z), tex(DAIS, x, 1, z, 641))
    for dx, dz in ((0, 3), (0, -3), (3, 0), (-3, 0)):        # steps up to it
        slab(b, (CAMP_CX + dx, 1, CAMP_CZ + dz), 'minecraft:polished_deepslate_slab')

    # The brazier, and the green light pooling under it.
    put(b, (CAMP_CX, 2, CAMP_CZ), 'minecraft:chiseled_deepslate')
    put(b, (CAMP_CX, 3, CAMP_CZ), 'minecraft:soul_campfire',
        {'lit': 'true', 'signal_fire': 'false', 'waterlogged': 'false', 'facing': 'north'})
    # Two, not four. Froglight is a full-bright block; ringing the brazier with it made the middle
    # of the camp one white blob instead of a fire with light coming off it.
    for dx, dz in ((1, 1), (-1, -1)):
        put(b, (CAMP_CX + dx, 2, CAMP_CZ + dz), 'minecraft:verdant_froglight', {'axis': 'y'})
    for dx, dz in ((1, -1), (-1, 1)):
        put(b, (CAMP_CX + dx, 2, CAMP_CZ + dz), 'minecraft:sculk')

    # Standing stones, each with a lit slit. The arrangement echoes the plate on the wall.
    for i in range(8):
        a = i * math.pi / 4.0 + 0.13
        sx = CAMP_CX + int(round(math.cos(a) * 4.6))
        sz = CAMP_CZ + int(round(math.sin(a) * 4.6))
        height = 2 + (i % 3 == 0)
        for y in range(1, height + 1):
            put(b, (sx, y, sz), tex([('minecraft:deepslate', 5), ('minecraft:cobbled_deepslate', 4),
                                     ('minecraft:blackstone', 2)], sx, y, sz, 642))
        put(b, (sx, height, sz), 'minecraft:chiseled_deepslate')
        # The lit rune sits at the stone's foot, not on its head. Capping the stones turned each one
        # into a lamp on a post, and six full-bright blocks at eye level is a row of blobs rather
        # than a ring of standing stones with something burning between them.
        if i % 2 == 0:
            fx = CAMP_CX + int(round(math.cos(a) * 5.9))
            fz = CAMP_CZ + int(round(math.sin(a) * 5.9))
            if (fx, 1, fz) not in b:
                put(b, (fx, 0, fz), 'minecraft:verdant_froglight', {'axis': 'y'})

    # Runes cut into the floor. Sparse on purpose: a solid ring of froglight is a lamp, not writing.
    for radius, salt in ((6.4, 643), (7.6, 644)):
        steps = int(2 * math.pi * radius * 1.5)
        for i in range(steps):
            a = i * 2 * math.pi / steps
            x = int(round(CAMP_CX + math.cos(a) * radius))
            z = int(round(CAMP_CZ + math.sin(a) * radius))
            if (x, 1, z) in b or math.hypot(x - CAMP_CX, z - CAMP_CZ) < 5.5:
                continue
            roll = noise(x, 0, z, salt)
            if roll > 0.93:
                put(b, (x, 0, z), 'minecraft:verdant_froglight', {'axis': 'y'})
            elif roll > 0.22:
                # Mostly continuous. A ring that is half gaps reads as litter on the floor; the
                # sculk is the ink and the froglight is only where the writing catches light.
                put(b, (x, 0, z), 'minecraft:sculk')


def camp_trunks(b):
    """The ring of dead trunks the web was strung between. This is the silhouette.

    Three things stop these being fence posts, and the first draft had none of them: the angles and
    radii are jittered so the ring is not eight spokes of a wheel, the heights spread much wider than
    6-9, and each trunk gets a root flare -- two or three logs lying at its foot. The flare is what
    actually does the work. A bare column meeting flat ground at ninety degrees reads as a post no
    matter how tall it is; roots spreading into the dirt read as a tree.
    """
    tops = []
    for i in range(9):
        # Jitter both the angle and the radius. Even spacing is the tell.
        a = i * (2 * math.pi / 9.0) + noise(i, 0, 0, 651) * 0.55
        radius = 9.6 + noise(i, 1, 0, 652) * 2.2
        tx = CAMP_CX + int(round(math.cos(a) * radius))
        tz = CAMP_CZ + int(round(math.sin(a) * radius))
        if not (0 <= tx < CAMP_W and 0 <= tz < CAMP_D):
            continue
        if WALL_X0 - 2 <= tx <= WALL_X1 + 2 and 5 <= tz <= WALL_Z + 4:   # clear of wall and bench
            continue

        # 7..11, not 4..10. The canopy sits a block or two above the trunk, so a short trunk hangs
        # its leaves down into the clearing and roofs the camp instead of framing it.
        height = 7 + int(noise(tx, 0, tz, 65) * 5)
        if i == 5:
            height = 2                            # one snapped to a stump
        wood = 'minecraft:dark_oak_log' if noise(tx, 1, tz, 66) > 0.35 \
            else 'minecraft:stripped_dark_oak_log'
        fill(b, tx, 1, tz, tx, height, tz, wood, {'axis': 'y'})

        # Root flare at the foot.
        for j, (rx, rz, axis) in enumerate(((1, 0, 'x'), (-1, 0, 'x'), (0, 1, 'z'), (0, -1, 'z'))):
            if noise(tx + rx, 1, tz + rz, 653) > 0.45:
                continue
            put(b, (tx + rx, 1, tz + rz), 'minecraft:dark_oak_log', {'axis': axis})

        # Branches reaching in over the camp -- they are what the canopy hangs from.
        inward_x = -1 if tx > CAMP_CX else 1
        inward_z = -1 if tz > CAMP_CZ else 1
        if height >= 4:
            put(b, (tx + inward_x, height, tz), 'minecraft:dark_oak_log', {'axis': 'x'})
            put(b, (tx, height, tz + inward_z), 'minecraft:dark_oak_log', {'axis': 'z'})
            put(b, (tx + inward_x * 2, height - 1, tz), 'minecraft:dark_oak_log', {'axis': 'x'})
                # Canopy. The reference for this camp is a clearing under trees, not under a web, and
            # bare poles were reading as scaffolding no matter how well the bases were flared.
            # persistent=true matters: without it every one of these decays away after placement.
            for lx in range(-2, 3):
                for lz in range(-2, 3):
                    for ly in (0, 1):
                        if lx * lx + lz * lz + ly * 3 > 5:
                            continue
                        pos = (tx + lx, height + 1 + ly, tz + lz)
                        if pos[1] < 8:            # hard floor: leaves belong overhead, not in the camp
                            continue
                        if pos not in b and noise(pos[0], pos[1], pos[2], 654) > 0.25:
                            put(b, pos, 'minecraft:dark_oak_leaves',
                                {'persistent': 'true', 'distance': '7', 'waterlogged': 'false'})
            tops.append((tx, height, tz))
    return tops


CANOPY_INNER = 7.0    # nothing inside this: the hole they burned above the fire
CANOPY_FLOOR = 6      # nothing below this: cobweb at head height is a wall you walk into


def camp_canopy(b, tops):
    """Aragog's web, still overhead and torn open above the fire.

    THIS WAS THE WORST THING IN THE BUILD IN GAME, and the numbers say why. The first version put
    367 cobwebs onto a dome whose surface is only about 450 cells -- 81% coverage, a solid white
    ceiling, 29% of every block in the structure. Standing in it was a blizzard, not a clearing.

    Two further faults hid inside that one. The radial threads ran all the way in to r=3.4, so they
    roofed over the very hole they were meant to leave open. And at the fringe the dome falls to
    y=4, which is head height, so you walked face-first into web before you ever saw the camp.

    An annulus now, not a cap: nothing inside CANOPY_INNER, nothing below CANOPY_FLOOR, about a
    quarter the density. What is left hangs between the trunk tops where a web would really be
    anchored, and leaves open sky over the fire.

    The lesson generalises past cobweb. A block with a bright, busy texture needs a fraction of the
    coverage a plain one does -- and no top-down slice or dot-based preview will ever tell you that,
    because both draw it as a mark rather than as the full-size surface it is. Only standing under
    it does.
    """
    def dome_y(dist):
        if dist >= CANOPY_R:
            return None
        y = 1 + int(round(CANOPY_H * math.sqrt(max(0.0, 1.0 - (dist / CANOPY_R) ** 2))))
        return y if y >= CANOPY_FLOOR else None

    def web(x, y, z):
        if 0 <= x < CAMP_W and 0 <= z < CAMP_D and (x, y, z) not in b:
            put(b, (x, y, z), 'minecraft:cobweb')

    anchors = [(math.atan2(tz - CAMP_CZ, tx - CAMP_CX), math.hypot(tx - CAMP_CX, tz - CAMP_CZ))
               for tx, _, tz in tops]

    # A clump caught in each treetop. Cobweb's texture is a bright cross on transparency, so at real
    # size it carries far more visual weight than its block count suggests -- the annulus that
    # replaced the dome still read as one continuous white ring. Clumps with sky between them read
    # as remnants, which is what a web nobody has maintained since the spiders left should look like.
    for ang, radius in anchors:
        for dx in (-2, -1, 0, 1, 2):
            for dz in (-2, -1, 0, 1, 2):
                x = int(round(CAMP_CX + math.cos(ang) * radius)) + dx
                z = int(round(CAMP_CZ + math.sin(ang) * radius)) + dz
                d = math.hypot(x - CAMP_CX, z - CAMP_CZ)
                y = dome_y(d)
                if y is None or d < CANOPY_INNER:
                    continue
                if dx * dx + dz * dz > 5 or noise(x, y, z, 682) < 0.72:
                    continue
                web(x, y, z)

    # One thread slung between each neighbouring pair, so the clumps read as remains of a single web
    # rather than as separate nests.
    for i, (a0, r0) in enumerate(anchors):
        a1, r1 = anchors[(i + 1) % len(anchors)]
        da = (a1 - a0 + math.pi) % (2 * math.pi) - math.pi
        for s_ in range(1, 8):
            t = s_ / 8.0
            ang = a0 + da * t
            r = (r0 + (r1 - r0) * t) - 1.5
            x = int(round(CAMP_CX + math.cos(ang) * r))
            z = int(round(CAMP_CZ + math.sin(ang) * r))
            y = dome_y(math.hypot(x - CAMP_CX, z - CAMP_CZ))
            if y is not None and noise(x, y, z, 683) > 0.62:
                web(x, y, z)


def rite_wall(b):
    """A wall they raised out of the forest floor to hang the mark on.

    Deepslate low into blackstone high, with the courses between the pilasters recessed a block --
    the shadow gap is the only thing that keeps a flat slab of stone from reading as a backdrop.
    Broken down at both ends so it looks raised in a hurry and left.
    """
    canvas = painting_span(13, 4, WALL_Z + 1, 'south', 4, 3)
    backing = {(x, y) for x, y, _ in canvas}

    heights = {}
    for x in range(WALL_X0, WALL_X1 + 1):
        # Ruined profile: full height across the middle, falling away at both ends.
        reach = min(x - WALL_X0, WALL_X1 - x)
        heights[x] = min(7, 2 + reach * 2 + int(noise(x, 0, 0, 71) * 2))
        # Two thick only across the middle, where the canvas needs something behind it.
        planes = (WALL_Z - 1, WALL_Z) if WALL_X0 < x < WALL_X1 else (WALL_Z,)
        for y in range(1, heights[x] + 1):
            for z in planes:
                # Blown-out stones, except directly behind the canvas. The audit passes either way
                # on any one run, because whether a hole lands there is down to the hash -- and a
                # painting that survives today and falls off after the next edit is worse than one
                # that never hung at all.
                if noise(x, y, z, 72) < 0.06 and not (z == WALL_Z and (x, y) in backing):
                    continue
                pal = RITE_STONE if y > 3 else ground_palette(RITE_STONE)
                put(b, (x, y, z), tex(pal, x, y, z, 73))

    # Coping follows the broken top rather than a flat line, or it floats over the fallen ends.
    for x in range(WALL_X0, WALL_X1 + 1):
        for z in ((WALL_Z - 1, WALL_Z) if WALL_X0 < x < WALL_X1 else (WALL_Z,)):
            slab(b, (x, heights[x] + 1, z), 'minecraft:blackstone_slab')
    for x, y in ((11, 4), (16, 4)):
        put(b, (x, y, WALL_Z), 'hogwartsmagic:slytherin_banner_block')

    # Lanterns on short posts at the ends of the bench rather than on pilasters up the wall face.
    # The pilasters they replace stood six blocks tall against a wall whose ends only reach three,
    # which looked less like depth and more like scaffolding left behind.
    for x in (WALL_X0 - 1, WALL_X1 + 1):
        fill(b, x, 1, WALL_Z + 2, x, 2, WALL_Z + 2, 'minecraft:polished_blackstone')
        put(b, (x, 3, WALL_Z + 2), 'minecraft:soul_lantern', {'hanging': 'false'})

    # The canvas hangs at WALL_Z + 1, so that column has to be open however the terrain came out.
    for pos in canvas:
        put(b, pos, 'minecraft:air')

    # Altar bench along the foot of the wall: low on purpose, so nothing stands in front of the mark.
    for x in range(10, 18):
        for z in (WALL_Z + 2, WALL_Z + 3):
            put(b, (x, 0, z), tex([('minecraft:polished_blackstone', 5),
                                   ('minecraft:blackstone', 3),
                                   ('minecraft:polished_deepslate', 3)], x, 0, z, 75))
    for x in (10, 13, 17):
        put(b, (x, 1, WALL_Z + 2), 'hogwartsmagic:candle_cluster')
    put(b, (15, 1, WALL_Z + 2), 'hogwartsmagic:wizard_cauldron')
    put(b, (11, 1, WALL_Z + 3), 'hogwartsmagic:enchanted_book_pile')
    for pos in ((12, 1, WALL_Z + 3), (16, 1, WALL_Z + 3)):
        put(b, pos, 'minecraft:cobweb')


CANVAS = [('minecraft:black_wool', 6), ('minecraft:gray_wool', 4)]


def camp_tent(b, cx, cz, open_dir, salt):
    """One A-frame tent: wool slopes on a log ridge, closed at the back, open toward the fire.

    Death Eaters apparate and they held this clearing for a battle, not a season, so the camp gets
    canvas rather than walls.

    Five wide and four tall. The first cut was three tall, rising one block per block inward, and at
    that pitch the roof was so shallow it read as a long dark beam lying on the ground rather than as
    anything anyone could sleep under. A tent needs its ridge clear of the eaves by more than the
    eaves are off the floor: wall to y=2, roof to y=3, ridge to y=4.

    open_dir is -1 or +1 along x. The ridge runs along x either way; only which end is sewn shut
    changes, and it is always the end facing away from the fire.
    """
    back = cx - open_dir * 2                     # the closed gable
    for x in range(cx - 2, cx + 3):
        gable = (x == back)
        for dz in (-2, -1, 0, 1, 2):
            z = cz + dz
            if abs(dz) == 2:
                fill(b, x, 1, z, x, 2, z, tex(CANVAS, x, 1, z, salt))
            elif abs(dz) == 1:
                put(b, (x, 3, z), tex(CANVAS, x, 3, z, salt))
                if gable:
                    fill(b, x, 1, z, x, 2, z, tex(CANVAS, x, 1, z, salt))
            else:
                put(b, (x, 4, z), 'minecraft:dark_oak_log', {'axis': 'x'})
                if gable:
                    fill(b, x, 1, z, x, 3, z, tex(CANVAS, x, 1, z, salt))

    # One porch pole, at the open end only. Overhanging both ends put nearly half the ridge's length
    # outside the canvas, and a beam that long reads as a felled trunk lying across a dark heap
    # rather than as the spine of a tent. The back gable gets a flush ridge instead.
    porch = cx + open_dir * 3
    put(b, (porch, 4, cz), 'minecraft:dark_oak_log', {'axis': 'x'})
    fill(b, porch, 1, cz, porch, 3, cz, 'minecraft:dark_oak_fence')

    # Bedroll down the centre and a stash at the back.
    for x in range(cx - 1, cx + 2):
        put(b, (x, 1, cz), 'minecraft:black_carpet')
    put(b, (back + open_dir, 1, cz + 1), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (cx + open_dir * 2, 1, cz - 1), 'minecraft:cobweb')


def camp_kit(b):
    """The clutter an occupying force leaves lying about. Fills the scattered single cells that no
    tent could ever use -- the bare ground in this camp is spread thin, not pooled."""
    for x, z, axis in ((9, 15, 'x'), (10, 15, 'x'), (9, 16, 'z')):     # firewood, stacked
        put(b, (x, 1, z), 'minecraft:stripped_dark_oak_log', {'axis': axis})
    put(b, (9, 2, 15), 'minecraft:dark_oak_log', {'axis': 'x'})
    for pos in ((16, 1, 8), (17, 1, 20), (5, 1, 13)):                  # crates dropped where handy
        put(b, pos, 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    # A drying rack: two posts and a rail, with canvas slung over it.
    for x in (15, 18):
        fill(b, x, 1, 21, x, 2, 21, 'minecraft:dark_oak_fence')
    for x in range(15, 19):
        put(b, (x, 3, 21), 'minecraft:dark_oak_log', {'axis': 'x'})
    for x in (16, 17):
        put(b, (x, 2, 21), tex(CANVAS, x, 2, 21, 88))


def banner_frames(b):
    """Standing frames around the perimeter, each carrying the Mark and a warm lantern.

    These do most of the work of saying whose camp this is, and they set the colour story the whole
    build runs on: ordinary lanterns out here are warm orange, and every magical thing inward of them
    is cold green or soul-blue. Lighting the perimeter with soul lanterns too, which is what this
    build did before, makes the camp one temperature and throws the contrast away.

    A log, not a fence: a wall banner needs a solid face to hang on and will drop off a fence post.
    """
    for i in range(6):
        a = i * math.pi / 3.0 + 0.45
        px = CAMP_CX + int(round(math.cos(a) * 10.8))
        pz = CAMP_CZ + int(round(math.sin(a) * 10.8))
        if not (1 <= px < CAMP_W - 1 and 1 <= pz < CAMP_D - 1):
            continue
        if WALL_X0 - 2 <= px <= WALL_X1 + 2 and 5 <= pz <= WALL_Z + 4:
            continue
        fill(b, px, 1, pz, px, 4, pz, 'minecraft:dark_oak_log', {'axis': 'y'})

        # The banner faces the fire, so it reads on the way in.
        inward = 'east' if px < CAMP_CX else 'west'
        bx = px + (1 if px < CAMP_CX else -1)
        if (bx, 3, pz) not in b:
            dark_mark_banner(b, (bx, 3, pz), inward)

        arm_z = pz + (1 if pz < CAMP_CZ else -1)
        put(b, (px, 4, arm_z), 'minecraft:dark_oak_log', {'axis': 'z'})
        put(b, (px, 3, arm_z), 'minecraft:lantern', {'hanging': 'true', 'waterlogged': 'false'})


def alchemy_bench(b):
    """Where they work. Bottles and a brewing stand say occupied far better than another crate."""
    x0, z = 16, 20
    for x in range(x0, x0 + 4):
        slab(b, (x, 1, z), 'minecraft:dark_oak_slab', 'top')
        put(b, (x, 0, z), 'minecraft:dark_oak_fence')
    put(b, (x0, 2, z), 'minecraft:brewing_stand',
        {'has_bottle_0': 'true', 'has_bottle_1': 'false', 'has_bottle_2': 'true'})
    put(b, (x0 + 1, 2, z), 'minecraft:candle', {'candles': '3', 'lit': 'true', 'waterlogged': 'false'})
    put(b, (x0 + 2, 2, z), 'hogwartsmagic:wizard_cauldron')
    put(b, (x0 + 3, 2, z), 'minecraft:green_candle',
        {'candles': '2', 'lit': 'true', 'waterlogged': 'false'})
    put(b, (x0 + 3, 1, z + 1), 'hogwartsmagic:enchanted_book_pile')


def camp_throne(b):
    """A seat cut for one, across the fire from the wall, facing it."""
    z0 = 18
    fill(b, 12, 1, z0, 14, 1, z0 + 1, 'minecraft:polished_blackstone')
    fill(b, 12, 2, z0 + 1, 14, 4, z0 + 1, 'minecraft:blackstone')
    put(b, (13, 5, z0 + 1), 'minecraft:chiseled_deepslate')
    for x in (12, 14):
        put(b, (x, 2, z0), 'minecraft:polished_blackstone_wall',
            {'north': 'low', 'south': 'low', 'east': 'none', 'west': 'none',
             'up': 'true', 'waterlogged': 'false'})
    put(b, (11, 1, z0), 'minecraft:soul_lantern', {'hanging': 'false'})
    put(b, (15, 1, z0), 'minecraft:soul_lantern', {'hanging': 'false'})


def camp_prisoner_post(b):
    """Where they kept whoever they caught. Left standing, and empty."""
    px, pz = 20, 13
    fill(b, px, 1, pz, px, 5, pz, 'minecraft:stripped_dark_oak_log', {'axis': 'y'})
    fill(b, px - 1, 5, pz, px + 1, 5, pz, 'minecraft:dark_oak_log', {'axis': 'x'})
    for dx in (-1, 1):
        fill(b, px + dx, 3, pz, px + dx, 4, pz, 'minecraft:chain', {'axis': 'y'})
    put(b, (px, 1, pz + 1), 'minecraft:cobweb')
    put(b, (px + 1, 2, pz), 'minecraft:cobweb')
    rubble(b, [(px + 1, 1, pz + 2), (px - 1, 1, pz - 2)], BOG_STONE, 76)


def camp_supplies(b):
    """The corner they dumped their gear in."""
    chest(b, (7, 1, 16), 'south', 'minecraft:chests/pillager_outpost')
    put(b, (6, 1, 15), 'hogwartsmagic:enchanted_book_pile')
    put(b, (8, 1, 17), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (7, 1, 18), 'minecraft:cauldron')
    for pos in ((6, 1, 17), (8, 1, 15), (5, 1, 16)):
        put(b, pos, 'minecraft:cobweb')
    put(b, (9, 1, 18), 'minecraft:soul_lantern', {'hanging': 'false'})


def death_eater_camp():
    """The Death Eaters' camp in the Forbidden Forest -- the Acromantula lair they took off Aragog.

    Everything here is arranged around one idea: they did not build this place, they moved into it.
    The webs overhead and the dead trunks are the spiders'; the hearth, the wall, the throne and the
    post are theirs, dropped inside a ring that already existed. So the stonework is deliberately the
    only tidy thing in the clearing, and it is burned through the web above the fire to get here.
    """
    b = {}
    camp_clearing(b)
    tops = camp_trunks(b)
    ritual_circle(b)
    rite_wall(b)
    camp_throne(b)
    camp_prisoner_post(b)
    camp_supplies(b)
    # Only two 5x5 pads in the whole clearing are actually clear -- the bare ground reads as 75% of
    # the middle band but it is scattered cell by cell, not pooled, so two is the honest capacity.
    camp_tent(b, 6, 10, +1, 86)          # west-north-west, opening toward the fire
    camp_tent(b, 19, 18, -1, 87)         # south-east, opening toward the fire
    camp_kit(b)
    banner_frames(b)
    alchemy_bench(b)
    camp_canopy(b, tops)

    entities = [
        painting(13, 4, WALL_Z + 1, 'hogwartsmagic:summoning_rite', 'south', 4, 3),
        # Baked in for the same reason the gibbet's dementor is: spawn_overrides only bias the
        # ongoing spawn cycle, so a camp generated at noon would sit empty until nightfall.
        #
        # Spread roughly 120 degrees apart on bare earth outside the hearth. The audit only asks
        # whether an entity fits where it is put, and by that test the first draft passed while
        # standing two of them in the fire ring and the third on the blackstone of the altar bench.
        entity(8.5, 1.0, 10.5, 'hogwartsmagic:wizard', {'PersistenceRequired': ('byte', 1)}),
        entity(18.5, 1.0, 10.5, 'hogwartsmagic:wizard', {'PersistenceRequired': ('byte', 1)}),
        entity(15.5, 1.0, 19.5, 'hogwartsmagic:wizard', {'PersistenceRequired': ('byte', 1)}),
    ]
    return b, entities


# --- the ruined wing, and the students camping in it ---------------------------------------------
#
# The wing is TWO courts, not one, divided by a wall that is still standing to most of its height.
# East is the hall proper: the great window, the hearth, the tables, three of the four hangings.
# West is the outer court, where the tent and the stores are, and it is lit only by lantern posts.
# The Gryffindor hanging is on the west face of the dividing wall, which is what makes that wall
# read as a wall between two rooms rather than as the back of the hall.

RUIN_W, RUIN_D = 39, 29
RUIN_CX, RUIN_CZ = 19, 14

WEST_X0 = 4                  # outer face of the west court
DIV_X = 16                   # the dividing wall
EAST_X1 = 32                 # outer face of the hall
HALL_Z0, HALL_Z1 = 5, 21
GABLE_PEAK = 26              # the hall's gable peaks right of its own centre
WIN_X0, WIN_X1 = 23, 29
DOOR_X0, DOOR_X1 = 16, 19    # the gap in the front arcade, on the axis of the dividing wall
PASS_Z0, PASS_Z1 = 13, 15    # the way through the dividing wall

RUIN_STONE = [('minecraft:mossy_stone_bricks', 9), ('minecraft:stone_bricks', 6),
              ('minecraft:cracked_stone_bricks', 5), ('minecraft:mossy_cobblestone', 4),
              ('minecraft:cobblestone', 2)]
RUIN_FLAG = [('minecraft:mossy_cobblestone', 8), ('minecraft:cobblestone', 6),
             ('minecraft:mossy_stone_bricks', 5), ('minecraft:cracked_stone_bricks', 4),
             ('minecraft:stone_bricks', 3), ('minecraft:gravel', 2)]
RUIN_HEAP = [('minecraft:cobblestone', 6), ('minecraft:mossy_cobblestone', 6),
             ('minecraft:cracked_stone_bricks', 3)]
# Two canvases, because the reference has two: a pale weathered tent in the outer court and a dark
# tarpaulin slung against the hall's back wall.
# The tent has to sit clear of the stone in VALUE, not just in hue -- mid-grey canvas against
# mid-grey masonry has no figure/ground at all, and a light_gray/gray mix banded into vertical
# stripes that read as a deck chair. The fix is to go DARKER and warmer, not lighter: a
# white/light_gray tent separated fine but turned into a bright slab that owned the whole court,
# and the reference's tent is weathered tan. Tan tent, near-black tarp.
TENT_CANVAS = [('minecraft:brown_wool', 6), ('minecraft:light_gray_wool', 3),
               ('minecraft:gray_wool', 1)]
TARP_CANVAS = [('minecraft:gray_wool', 6), ('minecraft:black_wool', 4)]


def vine(b, pos, side):
    """Vine on one face. The named side is the block it is holding on to."""
    props = {s: 'false' for s in ('north', 'south', 'east', 'west', 'up')}
    props[side] = 'true'
    b[pos] = ('minecraft:vine', props, None)


def stone_post(b, x, z, h=3):
    """A square stone post with a lantern on it. The reference's courtyard lights stand on
    masonry stubs, not on fence posts -- they read as salvaged pier tops, which is why they suit
    a ruin where a wooden post would look brought-in."""
    for y in range(1, h + 1):
        put(b, (x, y, z), tex(RUIN_STONE, x, y, z, 440))
    put(b, (x, h + 1, z), 'minecraft:chiseled_stone_bricks')
    put(b, (x, h + 2, z), 'minecraft:lantern', {'hanging': 'false', 'waterlogged': 'false'})


def gable_height(x):
    """The hall's north wall is a pointed gable, peaking over the great window."""
    return max(5, 15 - int(abs(x - GABLE_PEAK) * 0.95))


def west_back_height(x):
    """The outer court's back wall: lower, and more broken than the hall's."""
    return max(4, 8 - int(abs(x - 10) * 0.25) + int(noise(x, 0, 5, 431) * 3) - 1)


def div_height(z):
    """The dividing wall, standing FULL height for its whole run, front to back.

    An earlier pass ramped it down to 2-5 at the south end on the grounds that internal walls go
    first in a ruin. That is true of ruins in general and wrong for this one: the reference has
    this wall standing to full height all the way to the front, and it is the thing that makes the
    two courts read as two rooms rather than as one yard with a stub in it. It stays full; only
    the top course is jagged, like every other wall here.
    """
    return max(9, 13 - int(abs(z - 12) * 0.15) + int(noise(0, 0, z, 432) * 3) - 1)


def wall_height(x, z):
    """How tall this perimeter column still stands.

    Every side at a different stage of collapse, plus shallow notches bitten out of the long runs.
    At -6 the notches cut clean through the courses either side and left free-standing columns,
    which read as chimneys rather than as a broken wall.
    """
    n = noise(x, 0, z, 401)
    # Real breaches. A ruin reads as a ruin because the wall STOPS somewhere and the inside spills
    # out; returning 1-2 here left a kerb that still drew the rectangle, and the outline had no gap
    # in 136 columns except the doorway. These two runs go to nothing.
    if x >= 29 and z >= 18:                            # the south-east corner is gone
        return 0
    if x == WEST_X0 and 13 <= z <= 18:                 # and a stretch of the west wall with it
        return 0 if z in (14, 15, 16, 17) else 1
    if z == HALL_Z0:
        return gable_height(x) if x >= DIV_X else west_back_height(x)
    if x == EAST_X1:
        h = 11 - int(abs(z - RUIN_CZ) * 0.22) + int(n * 3) - 1
        if noise(x, 1, z, 421) > 0.72:
            h -= 3
        return max(4, h)
    if x == WEST_X0:
        h = 8 - int(abs(z - RUIN_CZ) * 0.20) + int(n * 3) - 1
        if noise(x, 1, z, 422) > 0.70:
            h -= 3
        return max(3, h)
    if z == HALL_Z1:                                   # the low plinth under the front arcade
        if DOOR_X0 <= x <= DOOR_X1:
            return 0
        return 2 + (1 if n > 0.6 else 0)
    return 0


def in_court(x, z):
    """True inside either court's floor area."""
    return HALL_Z0 < z < HALL_Z1 and (WEST_X0 < x < DIV_X or DIV_X < x < EAST_X1)


def ruin_ground(b):
    """Forest floor, worn bare where the camp treads and fraying back to undergrowth at the edge."""
    for x in range(RUIN_W):
        for z in range(RUIN_D):
            dx = max(0, abs(x - RUIN_CX) - 10)
            dz = max(0, abs(z - RUIN_CZ) - 6)
            if math.hypot(dx, dz) > 9.5:
                continue
            near = abs(x - RUIN_CX) < 14 and abs(z - RUIN_CZ) < 9
            g = tex(CAMP_TRODDEN if near else CAMP_GROUND, x, 0, z, 402)
            # podzol's blockstate has no default variant key -- it is snowy=false / snowy=true only.
            # Written bare it matches nothing in a resource-pack model lookup, so every render of
            # this build was silently dropping ~165 ground blocks and showing holes that are not there.
            put(b, (x, 0, z), g, {'snowy': 'false'} if g == 'minecraft:podzol' else None)
            inside = WEST_X0 <= x <= EAST_X1 and HALL_Z0 <= z <= HALL_Z1
            if (not inside and math.hypot(dx, dz) > 5.0 and noise(x, 1, z, 403) > 0.72
                    and b[(x, 0, z)][0] in DIRT_TAG):
                put(b, (x, 1, z), 'minecraft:fern' if noise(x, 1, z, 404) > 0.45
                    else 'minecraft:dead_bush')
    # The path worn in through the gap in the arcade.
    for z in range(HALL_Z1, RUIN_D - 3):
        for x in range(17, 20):
            if (x, 0, z) in b:
                put(b, (x, 0, z), 'minecraft:dirt_path')
                if b.get((x, 1, z), (None,))[0] in ('minecraft:fern', 'minecraft:dead_bush'):
                    del b[(x, 1, z)]


def ruin_floor(b):
    """Flagstones in both courts, with the ground coming back up through the gaps.

    A ruin with an intact floor reads as a building with the roof off. The holes, and the moss in
    the joints, are what say nobody has maintained this in a century.
    """
    for x in range(WEST_X0, EAST_X1 + 1):
        for z in range(HALL_Z0, HALL_Z1 + 1):
            n = noise(x, 0, z, 405)
            if n > 0.84:
                continue
            put(b, (x, 0, z), tex(RUIN_FLAG, x, 0, z, 405))
    for x in range(WEST_X0 + 1, EAST_X1):
        for z in range(HALL_Z1 - 4, HALL_Z1 + 1):
            if (x, 0, z) in b and noise(x, 2, z, 406) > 0.55:
                put(b, (x, 0, z), 'minecraft:mossy_cobblestone')
    # Moss in PATCHES, not singles. Gated per texel it scattered ~90 lone moss blocks (luma ~78)
    # through a grey mix averaging ~115, which is a 37-point contrast at one-block frequency --
    # the chessboard failure. Sampling the noise at half resolution clumps them into 2x2 drifts,
    # which reads as moss taking hold in the damp joints instead of as pepper.
    for x in range(WEST_X0, EAST_X1 + 1):
        for z in range(HALL_Z0, HALL_Z1 + 1):
            if (x, 0, z) in b and noise(x // 2, 3, z // 2, 428) > 0.74:
                put(b, (x, 0, z), 'minecraft:moss_block')


def ruin_walls(b):
    """The standing stonework: jagged tops, buttresses proud of the face, vine on the outside."""
    perim = []
    for x in range(WEST_X0, EAST_X1 + 1):
        perim.append((x, HALL_Z0))
        perim.append((x, HALL_Z1))
    for z in range(HALL_Z0, HALL_Z1 + 1):
        perim.append((WEST_X0, z))
        perim.append((EAST_X1, z))
    for x, z in set(perim):
        h = wall_height(x, z)
        for y in range(1, h + 1):
            pal = ground_palette(RUIN_STONE) if y <= 2 else RUIN_STONE
            put(b, (x, y, z), tex(pal, x, y, z, 407))
        if h:
            slab(b, (x, h + 1, z), 'minecraft:mossy_cobblestone_slab'
                 if noise(x, h, z, 408) > 0.5 else 'minecraft:stone_brick_slab')

    # The dividing wall, with the passage left open through it.
    for z in range(HALL_Z0, HALL_Z1 + 1):
        h = div_height(z)
        for y in range(1, h + 1):
            if PASS_Z0 <= z <= PASS_Z1 and y <= 4:
                continue
            pal = ground_palette(RUIN_STONE) if y <= 2 else RUIN_STONE
            put(b, (DIV_X, y, z), tex(pal, DIV_X, y, z, 433))
        slab(b, (DIV_X, h + 1, z), 'minecraft:stone_brick_slab')
    # Arched head over the passage.
    stair(b, (DIV_X, 5, PASS_Z0), 'minecraft:stone_brick_stairs', 'south')
    stair(b, (DIV_X, 5, PASS_Z1), 'minecraft:stone_brick_stairs', 'north')
    put(b, (DIV_X, 6, PASS_Z0 + 1), 'minecraft:chiseled_stone_bricks')
    for z in range(PASS_Z0, PASS_Z1 + 1):
        put(b, (DIV_X, 0, z), 'minecraft:stone_bricks')

    # Buttresses, one block proud of the face, standing a course or two ABOVE the wall they brace.
    # Capped level they read as part of the wall; standing proud is what gives the stepped skyline.
    for x in (21, 31):
        top = wall_height(x, HALL_Z0) + 1
        fill(b, x, 1, HALL_Z0 - 1, x, top, HALL_Z0 - 1, 'minecraft:stone_bricks')
        put(b, (x, 1, HALL_Z0 - 2), 'minecraft:stone_brick_stairs',
            {'facing': 'north', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
        put(b, (x, top + 1, HALL_Z0 - 1), 'minecraft:chiseled_stone_bricks')
        put(b, (x, top + 2, HALL_Z0 - 1), 'minecraft:lantern',
            {'hanging': 'false', 'waterlogged': 'false'})
    for z in (9, 18):
        for x, out in ((WEST_X0, -1), (EAST_X1, 1)):
            top = wall_height(x, z) + 2
            fill(b, x + out, 1, z, x + out, top, z, 'minecraft:stone_bricks')
            put(b, (x + out * 2, 1, z), 'minecraft:stone_brick_stairs',
                {'facing': 'west' if out < 0 else 'east', 'half': 'bottom',
                 'shape': 'straight', 'waterlogged': 'false'})
            put(b, (x + out, top + 1, z), 'minecraft:chiseled_stone_bricks')
    # The dividing wall gets buttresses too, on its west face, which is what the tent court sees.
    for z in (8, 19):
        top = div_height(z) + 1
        fill(b, DIV_X - 1, 1, z, DIV_X - 1, top, z, 'minecraft:stone_bricks')
        put(b, (DIV_X - 1, top + 1, z), 'minecraft:chiseled_stone_bricks')

    # Vine, thickest on the shaded north walls. At a 0.40 gate it covered the gable wall to wall
    # and read as a hanging curtain rather than ivy that got a hold in places and spread.
    for x in range(WEST_X0, EAST_X1 + 1):
        for z, side, out in ((HALL_Z0, 'south', -1), (HALL_Z1, 'north', 1)):
            gate = 0.72 if z == HALL_Z0 else 0.78
            if z == HALL_Z0 and WIN_X0 - 1 <= x <= WIN_X1 + 1:
                gate = 0.90                    # keep the archivolt legible from outside too
            for y in range(2, wall_height(x, z)):
                if noise(x, y, z, 409) > gate and (x, y, z + out) not in b:
                    vine(b, (x, y, z + out), side)
    for z in range(HALL_Z0, HALL_Z1 + 1):
        for x, side, out in ((WEST_X0, 'east', -1), (EAST_X1, 'west', 1)):
            for y in range(2, wall_height(x, z)):
                if noise(x, y, z, 410) > 0.74 and (x + out, y, z) not in b:
                    vine(b, (x + out, y, z), side)
    # Ivy on the dividing wall's west face -- but never across the great window: the opening is
    # air, so a bare "is this cell free" test hangs vines straight down through it.
    for z in range(HALL_Z0 + 1, HALL_Z1):
        for y in range(2, div_height(z) - 1):
            if noise(DIV_X, y, z, 434) > 0.76 and (DIV_X - 1, y, z) not in b:
                vine(b, (DIV_X - 1, y, z), 'east')
    for x in range(WEST_X0 + 1, EAST_X1):
        if WIN_X0 - 1 <= x <= WIN_X1 + 1 or x == DIV_X:
            continue
        for y in range(3, wall_height(x, HALL_Z0) - 1):
            if noise(x, y, 0, 411) > 0.80 and (x, y, HALL_Z0 + 1) not in b:
                vine(b, (x, y, HALL_Z0 + 1), 'north')


WIN_SPAN = {4: (23, 29), 5: (23, 29), 6: (23, 29), 7: (23, 29), 8: (23, 29), 9: (23, 29),
            10: (24, 28), 11: (24, 28), 12: (25, 27), 13: (26, 26)}


def great_window(b):
    """The one thing still standing whole, and the only thing here built to be looked at.

    Seven wide, ten tall, and ringed by a course of clean stone brick that follows the arch. That
    ring is the feature: without it the opening is a hole in a mossy wall, and with it the arch
    reads as built -- it is what the eye actually picks out of the reference.
    """
    z = HALL_Z0
    for y, (x0, x1) in WIN_SPAN.items():
        for x in range(x0, x1 + 1):
            put(b, (x, y, z), 'minecraft:air')
    for y, (x0, x1) in WIN_SPAN.items():
        put(b, (x0 - 1, y, z), 'minecraft:stone_bricks')
        put(b, (x1 + 1, y, z), 'minecraft:stone_bricks')
    put(b, (GABLE_PEAK, 14, z), 'minecraft:chiseled_stone_bricks')
    put(b, (GABLE_PEAK - 1, 14, z), 'minecraft:stone_bricks')
    put(b, (GABLE_PEAK + 1, 14, z), 'minecraft:stone_bricks')
    for y, (x0, x1) in ((10, (24, 28)), (12, (25, 27)), (13, (26, 26))):
        stair(b, (x0 - 1, y, z), 'minecraft:stone_brick_stairs', 'east')
        stair(b, (x1 + 1, y, z), 'minecraft:stone_brick_stairs', 'west')
    # Tracery: three mullions and one transom, left otherwise open. The reference reads dark
    # through the glassless lights, so filling every cell with bars would close it up.
    for y, (x0, x1) in WIN_SPAN.items():
        for x in range(x0, x1 + 1):
            if x in (WIN_X0 + 1, GABLE_PEAK, WIN_X1 - 1) or y == 9:
                put(b, (x, y, z), 'minecraft:iron_bars',
                    {'north': 'false', 'south': 'false', 'east': 'true', 'west': 'true',
                     'waterlogged': 'false'})
    for x in range(WIN_X0, WIN_X1 + 1):
        slab(b, (x, 3, z), 'minecraft:stone_brick_slab', 'top')
    # Candles stand ON that sill, inside the opening, between the mullions -- which is both where
    # the reference puts them and the only place here with a solid face under them. Set a block
    # north at z+1 they had nothing at y=3 beneath and would have popped on the first block update.
    for x in (25, 27):
        put(b, (x, 4, z), 'minecraft:candle',
            {'candles': '3', 'lit': 'true', 'waterlogged': 'false'})
    # The clusters go on the floor either side of the window, on masonry placed for them.
    for x in (22, 30):
        put(b, (x, 0, z + 1), 'minecraft:stone_bricks')
        put(b, (x, 1, z + 1), 'hogwartsmagic:candle_cluster')


def north_recess(b):
    """The dark arched recess west of the great window, with a lantern set back in it."""
    z = HALL_Z0
    for x in (19, 20):
        for y in range(1, 5):
            put(b, (x, y, z), 'minecraft:air')
        put(b, (x, 0, z), 'minecraft:stone_bricks')
    stair(b, (19, 5, z), 'minecraft:stone_brick_stairs', 'east')
    stair(b, (20, 5, z), 'minecraft:stone_brick_stairs', 'west')
    put(b, (19, 6, z), 'minecraft:stone_bricks')
    put(b, (20, 6, z), 'minecraft:stone_bricks')
    # Backed, so it is a blind niche and not a hole. Left open it looked straight through to the
    # forest and read BRIGHT -- the exact opposite of the dark recess it is meant to be. Deepslate
    # because the point of it is that it is darker than everything around it.
    for x in (19, 20):
        for y in range(1, 5):
            put(b, (x, y, z - 1), 'minecraft:deepslate_bricks')
    put(b, (19, 2, z), 'minecraft:lantern', {'hanging': 'false', 'waterlogged': 'false'})
    put(b, (19, 1, z), 'minecraft:deepslate_bricks')


def side_arch(b):
    """A doorway through the east wall, arched like the window so the two read as one building."""
    x = EAST_X1
    for z in range(13, 16):
        for y in range(1, 5):
            put(b, (x, y, z), 'minecraft:air')
    stair(b, (x, 5, 13), 'minecraft:stone_brick_stairs', 'south')
    stair(b, (x, 5, 15), 'minecraft:stone_brick_stairs', 'north')
    put(b, (x, 5, 14), 'minecraft:air')
    put(b, (x, 6, 14), 'minecraft:chiseled_stone_bricks')
    for z in range(13, 16):
        put(b, (x, 0, z), 'minecraft:stone_bricks')
        for dx in range(1, 4):
            if (x + dx, 0, z) in b:
                put(b, (x + dx, 0, z), 'minecraft:dirt_path')
                if b.get((x + dx, 1, z), (None,))[0] in ('minecraft:fern', 'minecraft:dead_bush'):
                    del b[(x + dx, 1, z)]


def ruin_colonnade(b):
    """The front of both courts is a row of broken square columns on a low plinth, not a wall.

    This is the single feature that most says 'ruined building' rather than 'walled compound': the
    arcade is what is left when the infill between the piers has gone, and the gaps between the
    columns are what let the firelight out into the trees. Kept to 4-6 courses -- at 5-8 they stood
    in front of the camp from every angle that shows the camp.
    """
    # (x, height, width, capped, lantern). Written out rather than generated: seven piers all 2x2,
    # all capped in chiseled stone, all lanterned, at only two possible heights, produced a frieze
    # of identical glowing bullseyes at head height across the whole front elevation -- the single
    # loudest and most mechanical thing in the build. Two are stumps now, one stands tall, the
    # widths differ, and only three carry a light.
    # Heights raised back up across the west third: at h=2-3 those piers sat below the z=21 plinth
    # and the whole west front flattened into a bare 3-block wall with an 11-wide hole in the
    # arcade. A pier has to stand clear of its own plinth or it is not a pier.
    for x0, h, w, capped, lit in ((5, 7, 2, True, True), (8, 5, 2, False, False),
                                  (12, 6, 2, True, True), (14, 4, 1, False, False),
                                  (20, 6, 2, True, True), (24, 4, 3, False, False),
                                  (28, 8, 2, True, True)):
        for x in range(x0, min(x0 + w, EAST_X1 + 1)):
            for z in (HALL_Z1 - 1, HALL_Z1):
                for y in range(1, h + 1):
                    put(b, (x, y, z), tex(RUIN_STONE, x, y, z, 425))
                if capped:
                    put(b, (x, h + 1, z), 'minecraft:chiseled_stone_bricks')
                else:
                    slab(b, (x, h + 1, z), 'minecraft:mossy_cobblestone_slab')
        if capped:
            stair(b, (x0 + w, h + 1, HALL_Z1), 'minecraft:stone_brick_stairs', 'east')
            stair(b, (x0 - 1, h + 1, HALL_Z1), 'minecraft:stone_brick_stairs', 'west')
        for y in range(2, h):
            if noise(x0, y, 3, 427) > 0.62 and (x0, y, HALL_Z1 + 1) not in b:
                vine(b, (x0, y, HALL_Z1 + 1), 'north')
        # Nothing roots in mossy cobble, so anything already growing here has to go with it.
        put(b, (x0, 0, HALL_Z1 + 1), 'minecraft:mossy_cobblestone')
        if b.get((x0, 1, HALL_Z1 + 1), (None,))[0] in ('minecraft:fern', 'minecraft:dead_bush'):
            del b[(x0, 1, HALL_Z1 + 1)]
        if lit:
            put(b, (x0, h + 2, HALL_Z1), 'minecraft:lantern',
                {'hanging': 'false', 'waterlogged': 'false'})


def ruin_rubble(b):
    """Where the walls came down: heaps outside the collapse, and a course that fell inward."""
    rubble(b, [(2, 1, 18), (3, 1, 20), (6, 1, 23), (11, 1, 23), (21, 1, 23), (27, 1, 22),
               (33, 1, 19), (34, 1, 12), (33, 1, 7), (3, 1, 8), (2, 1, 13), (25, 1, 24),
               (35, 1, 16), (1, 1, 15)], RUIN_HEAP, 412)
    for x in range(28, 32):
        put(b, (x, 1, 19), 'minecraft:stone_brick_wall',
            {'north': 'none', 'south': 'none', 'east': 'low', 'west': 'low',
             'up': 'false', 'waterlogged': 'false'})
    for pos in ((6, 1, 7), (5, 1, 8), (14, 1, 20)):
        put(b, pos, tex(RUIN_HEAP, pos[0], pos[1], pos[2], 413))
    # Collapse spilling ACROSS the two breaches, inside and out. A gap with tidy edges reads as a
    # doorway; the wall has to be lying on the ground either side of it to read as fallen.
    for x, z in ((3, 14), (3, 16), (5, 15), (5, 17), (6, 14), (2, 15),
                 (30, 19), (31, 20), (29, 19), (33, 19), (28, 20), (32, 18)):
        if (x, 1, z) not in b:
            put(b, (x, 1, z), tex(RUIN_HEAP, x, 1, z, 435))
            if noise(x, 1, z, 436) > 0.5:
                slab(b, (x, 2, z), 'minecraft:mossy_cobblestone_slab')
    for x, z, facing in ((4, 13, 'north'), (4, 18, 'south'), (30, 18, 'north')):
        stair(b, (x, 1, z), 'minecraft:stone_brick_stairs', facing)
    for x, z in ((6, 16), (31, 19)):
        put(b, (x, 1, z), 'minecraft:stone_brick_wall',
            {'north': 'low', 'south': 'low', 'east': 'none', 'west': 'none',
             'up': 'false', 'waterlogged': 'false'})


def back_tarpaulin(b):
    """A dark tarp slung off the hall's back wall over the stores, west of the window.

    The reference has two canvases and they are different colours: this dark one against the
    stonework and the pale tent out in the open court.
    """
    for x in range(18, 21):
        put(b, (x, 6, 6), tex(TARP_CANVAS, x, 6, 6, 430))   # against the wall, not floating clear
        put(b, (x, 5, 7), tex(TARP_CANVAS, x, 5, 7, 430))
        put(b, (x, 4, 8), tex(TARP_CANVAS, x, 4, 8, 430))
    for x in (18, 20):
        fill(b, x, 1, 8, x, 3, 8, 'minecraft:dark_oak_fence')
    for pos in ((18, 1, 7), (19, 1, 7)):
        put(b, pos, 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (18, 2, 7), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})


def bookcase(b):
    """The bookcase against the hall's back wall, under the Slytherin hanging."""
    for y in (1, 2, 3):
        put(b, (21, y, 6), 'minecraft:bookshelf')
    put(b, (21, 4, 6), 'hogwartsmagic:enchanted_book_pile')
    put(b, (22, 1, 6), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})


def camp_hearth(b):
    """The fire the hall is arranged around, sunk in a kerb of stone lifted off the floor.

    Paving plus a campfire reads as a courtyard with a fire on it. The kerb is what turns it into
    a hearth, and it is the single change that gives the middle of this build a focal point.
    """
    cx, cz = 24, 14
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            put(b, (cx + dx, 0, cz + dz), tex([('minecraft:cobblestone', 6),
                                               ('minecraft:mossy_cobblestone', 4)],
                                              cx + dx, 0, cz + dz, 414))
    # The kerb is deepslate, NOT cobble: built from cobblestone and mossy cobblestone it was made
    # of the two commonest blocks in the floor palette, so the hearth was literally constructed out
    # of the paving around it and disappeared. Dark stone against grey flags is what makes it read.
    # Stairs face outward so the ring has a profile, and the fire is lifted a block clear of it.
    for dx, dz, facing in ((0, -1, 'north'), (0, 1, 'south'), (-1, 0, 'west'), (1, 0, 'east')):
        stair(b, (cx + dx, 1, cz + dz), 'minecraft:deepslate_brick_stairs', facing)
    for dx in (-1, 1):
        for dz in (-1, 1):
            put(b, (cx + dx, 1, cz + dz), 'minecraft:chiseled_deepslate')
    put(b, (cx, 1, cz), 'minecraft:deepslate_bricks')
    put(b, (cx, 2, cz), 'minecraft:campfire',
        {'facing': 'north', 'lit': 'true', 'signal_fire': 'false', 'waterlogged': 'false'})
    # The two chests immediately either side are the reference's -- they read as the things people
    # are sitting on and rummaging in, right where the light is.
    chest(b, (cx - 2, 1, cz), 'west', 'minecraft:chests/simple_dungeon')
    chest(b, (cx + 2, 1, cz), 'east', 'minecraft:chests/simple_dungeon')
    for x in range(cx - 2, cx + 3):
        stair(b, (x, 1, cz + 3), 'minecraft:dark_oak_stairs', 'north')
        if x != cx:
            stair(b, (x, 1, cz - 3), 'minecraft:dark_oak_stairs', 'south')
    for z in range(cz - 1, cz + 2):
        put(b, (cx + 4, 1, z), 'minecraft:dark_oak_log', {'axis': 'z'})
    put(b, (cx - 3, 1, cz - 2), 'hogwartsmagic:wizard_cauldron')
    for x in (cx + 1, cx + 2):
        put(b, (x, 1, cz - 4), 'minecraft:stripped_dark_oak_log', {'axis': 'x'})


def study_bench(b):
    """The long table down the hall's east side, and a shorter one under the window."""
    x, z0, z1 = 30, 9, 15
    for z in range(z0, z1 + 1):
        slab(b, (x, 1, z), 'minecraft:dark_oak_slab', 'top')
        put(b, (x, 0, z), 'minecraft:dark_oak_fence')
    put(b, (x, 2, z0), 'hogwartsmagic:candle_cluster')
    put(b, (x, 2, z0 + 2), 'hogwartsmagic:enchanted_book_pile')
    put(b, (x, 2, z0 + 3), 'minecraft:candle',
        {'candles': '2', 'lit': 'true', 'waterlogged': 'false'})
    put(b, (x, 2, z0 + 5), 'hogwartsmagic:enchanted_book_pile')
    put(b, (x, 2, z1), 'hogwartsmagic:candle_cluster')
    for z in (z0 + 1, z0 + 4, z0 + 6):
        stair(b, (x - 1, 1, z), 'minecraft:dark_oak_stairs', 'east')
    for x2 in range(24, 28):
        slab(b, (x2, 1, 8), 'minecraft:dark_oak_slab', 'top')
        put(b, (x2, 0, 8), 'minecraft:dark_oak_fence')
    put(b, (24, 2, 8), 'minecraft:candle',
        {'candles': '3', 'lit': 'true', 'waterlogged': 'false'})
    put(b, (26, 2, 8), 'hogwartsmagic:enchanted_book_pile')
    put(b, (27, 2, 8), 'hogwartsmagic:candle_cluster')
    stair(b, (25, 1, 9), 'minecraft:dark_oak_stairs', 'north')


def student_tent(b, cx, cz, salt, length=5):
    """The pale A-frame in the outer court, opening east toward the passage.

    Pitched broadside with a ridge log running one block past the opening onto a porch pole. Two
    five-wide tents in black wool filled a third of the hall and left nowhere for the camp to
    happen, which is what killed the first pass.
    """
    x0, x1 = cx - length // 2, cx + length // 2
    for x in range(x0, x1 + 1):
        for dz in (-2, -1, 1, 2):
            z = cz + dz
            if abs(dz) == 2:
                put(b, (x, 1, z), tex(TENT_CANVAS, x, 1, z, salt))
            else:
                fill(b, x, 1, z, x, 2, z, tex(TENT_CANVAS, x, 1, z, salt))
        put(b, (x, 3, cz), 'minecraft:dark_oak_log', {'axis': 'x'})
    for dz in (-2, -1, 0, 1, 2):
        put(b, (x0, 1, cz + dz), tex(TENT_CANVAS, x0, 1, cz + dz, salt))
        if abs(dz) < 2:
            put(b, (x0, 2, cz + dz), tex(TENT_CANVAS, x0, 2, cz + dz, salt))
    put(b, (x1 + 1, 3, cz), 'minecraft:dark_oak_log', {'axis': 'x'})
    fill(b, x1 + 1, 1, cz, x1 + 1, 2, cz, 'minecraft:dark_oak_fence')
    for dz in (-1, 0, 1):
        put(b, (x1, 1, cz + dz), 'minecraft:brown_carpet')


def west_court(b):
    """The outer court: the tent, the stores, and the lantern posts that are its only light.

    No fire here on purpose. The hearth is through the passage, and keeping this court lit only by
    standing lanterns is what makes it read as the yard you cross to get to the warm room.
    """
    student_tent(b, 9, 13, 415, length=5)
    stone_post(b, 6, 10, 3)
    stone_post(b, 13, 18, 3)
    stone_post(b, 6, 18, 2)
    # Crates and barrels stacked against the walls.
    for pos in ((6, 1, 16), (7, 1, 17), (13, 1, 8), (14, 1, 9)):
        put(b, pos, 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (6, 2, 16), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    chest(b, (13, 1, 16), 'west', 'minecraft:chests/simple_dungeon')
    # Firewood and a drying rack.
    for x in (11, 12):
        put(b, (x, 1, 19), 'minecraft:stripped_dark_oak_log', {'axis': 'x'})
    put(b, (11, 2, 19), 'minecraft:dark_oak_log', {'axis': 'x'})
    for x in (7, 9):
        fill(b, x, 1, 8, x, 2, 8, 'minecraft:dark_oak_fence')
    put(b, (8, 3, 8), 'minecraft:dark_oak_log', {'axis': 'x'})
    put(b, (8, 2, 8), tex(TENT_CANVAS, 8, 2, 8, 417))
    for z in (11, 12):
        put(b, (14, 1, z), 'minecraft:brown_carpet')
    # The north end of this court was thirty-five bare tiles holding two barrels. It becomes the
    # woodyard: the stack the hearth is fed from, which is a reason for the court to exist.
    for z in (6, 7):
        for x in (10, 11, 12):
            put(b, (x, 1, z), 'minecraft:stripped_dark_oak_log', {'axis': 'x'})
    for x in (10, 11):
        put(b, (x, 2, 6), 'minecraft:dark_oak_log', {'axis': 'x'})
    put(b, (13, 1, 6), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    stair(b, (9, 1, 7), 'minecraft:dark_oak_stairs', 'west')
    # Stacked at the tent's porch, where somebody living here would actually leave them.
    put(b, (13, 1, 11), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (13, 2, 11), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (13, 1, 12), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    # A lean-to over the woodpile so the court has something with height in it.
    for x in range(9, 13):
        put(b, (x, 4, 6), tex(TENT_CANVAS, x, 4, 6, 437))
    put(b, (12, 3, 7), 'minecraft:dark_oak_fence')
    put(b, (9, 3, 7), 'minecraft:dark_oak_fence')


def house_banners(b):
    """The four houses, set flush into the wall face rather than standing proud of it.

    The mod's own banner blocks rather than vanilla wall banners: vanilla's are a pattern on a flat
    sheet, and at three blocks tall these give the hall the same heavy hangings the rest of the
    castle has. Proud of the wall was the first attempt and it rendered as three stacked crates
    floating in front of the masonry: a full cube with no wall visibly behind it has no way to read
    as cloth. Flush, the stone frames it on all four sides and it reads as a hanging.

    Gryffindor is on the DIVIDING wall, facing the outer court -- that placement is what tells you
    the wall divides two rooms rather than closing off the back of one.
    """
    # The banner blocks are cube_all -- one texture on all six faces, no facing property. Set flush
    # into a wall only one block thick they therefore hang on BOTH sides of it: Gryffindor showed
    # into the hall as well as the outer court, and Hufflepuff read on the outside of the east wall
    # facing the forest. Each one gets masonry behind it so only the intended face is ever seen.
    for house, x, z, y0, bx, bz in (('gryffindor', DIV_X, 10, 6, DIV_X + 1, 10),
                                    ('slytherin', 21, HALL_Z0, 5, 21, HALL_Z0 - 1),
                                    ('ravenclaw', 31, HALL_Z0, 5, 31, HALL_Z0 - 1),
                                    ('hufflepuff', EAST_X1, 11, 4, EAST_X1 + 1, 11)):
        for y in range(y0, y0 + 3):
            put(b, (x, y, z), 'hogwartsmagic:%s_banner_block' % house)
            put(b, (bx, y, bz), tex(RUIN_STONE, bx, y, bz, 441))


def courtyard_posts(b):
    """Lantern posts standing loose in the hall floor, and one banner on a pole.

    The reference lights the middle of the courts, not just their walls -- without these every
    light source is stuck to the masonry, which reads as the ruin being lit rather than the camp.
    """
    stone_post(b, 20, 15, 2)
    stone_post(b, 22, 19, 3)
    stone_post(b, 28, 18, 2)
    stone_post(b, 22, 11, 2)
    px, pz = 26, 19
    fill(b, px, 1, pz, px, 5, pz, 'minecraft:dark_oak_log', {'axis': 'y'})
    put(b, (px, 6, pz), 'minecraft:dark_oak_log', {'axis': 'x'})
    put(b, (px - 1, 6, pz), 'minecraft:dark_oak_log', {'axis': 'x'})
    for y in (3, 4, 5):
        put(b, (px - 1, y, pz), 'hogwartsmagic:ravenclaw_banner_block')


def ruin_fittings(b):
    """The rest of what the students dragged in, and one thing that was always here."""
    for y in (3, 4):
        put(b, (EAST_X1, y, 17), 'hogwartsmagic:moving_portrait')
    put(b, (28, 1, HALL_Z0 + 1), 'hogwartsmagic:owl_perch')
    for z in range(16, 19):
        put(b, (DIV_X + 1, 5, z), 'minecraft:chain', {'axis': 'z', 'waterlogged': 'false'})
    for z in (16, 18):
        put(b, (DIV_X + 1, 4, z), tex(TARP_CANVAS, DIV_X + 1, 4, z, 418))
    put(b, (19, 1, 12), 'hogwartsmagic:enchanted_book_pile')
    # The strip inside the passage was fifty tiles of bare flagstone -- the first floor you stand
    # on entering the hall, and the emptiest thing in the build. It becomes the boot room.
    for z in (11, 12, 13):
        put(b, (18, 1, z), 'minecraft:brown_carpet')
    put(b, (18, 1, 10), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (17, 1, 17), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    put(b, (18, 2, 10), 'minecraft:barrel', {'facing': 'up', 'open': 'false'})
    for x in (18, 19):
        put(b, (x, 1, 19), 'minecraft:stripped_dark_oak_log', {'axis': 'x'})
    put(b, (18, 2, 19), 'minecraft:dark_oak_log', {'axis': 'x'})
    stair(b, (20, 1, 18), 'minecraft:dark_oak_stairs', 'east')
    stair(b, (20, 1, 17), 'minecraft:dark_oak_stairs', 'east')


def ruin_lighting(b):
    """Warm lanterns on the wall heads. Nothing cold anywhere in this camp -- it is the one build
    in the set meant to feel safe, and the temperature of the light is most of how that gets said."""
    for x, z in ((EAST_X1, 9), (EAST_X1, 18), (WEST_X0, 9), (WEST_X0, 18),
                 (8, HALL_Z0), (12, HALL_Z0)):
        h = wall_height(x, z)
        if h:
            put(b, (x, h + 2, z), 'minecraft:lantern', {'hanging': 'false', 'waterlogged': 'false'})
    for z in (8, 19):
        top = div_height(z) + 1
        put(b, (DIV_X - 1, top + 2, z), 'minecraft:lantern',
            {'hanging': 'false', 'waterlogged': 'false'})


def ruin_trees(b):
    """Trunks crowding the walls, so the ruin reads as something the forest took back.

    Built the way a dark oak actually is -- a 2x2 bole under a wide low crown -- because a 1x1
    trunk with a small cap is a lollipop on a stick. Some carry a lantern on a jutting branch,
    which is what the reference has glowing out among the trees.
    """
    # Position AND height written out. Three exact mirror pairs on one crown formula at h=7-9 gave
    # eight matching domes at even intervals round the footprint -- a planted hedge, not a forest.
    spots = [(1, 4, 11), (1, 19, 7), (16, 0, 9), (34, 8, 12), (35, 22, 6),
             (19, 26, 10), (6, 0, 7), (28, 25, 8), (36, 15, 9)]
    for i, (x, z, h) in enumerate(spots):
        if not (0 <= x < RUIN_W - 1 and 0 <= z < RUIN_D - 1) or (x, 0, z) not in b:
            continue
        for bx, bz in ((x, z), (x + 1, z), (x, z + 1), (x + 1, z + 1)):
            fill(b, bx, 1, bz, bx, h, bz, 'minecraft:dark_oak_log', {'axis': 'y'})
        for dx, dz in ((-1, 0), (2, 0), (0, -1), (0, 2)):
            p = (x + dx, 1, z + dz)
            if noise(p[0], 1, p[2], 423) > 0.5 and p not in b:
                put(b, p, 'minecraft:dark_oak_log', {'axis': 'y'})
        for dy, rad in ((-1, 2), (0, 2), (1, 1)):
            for dx in range(-rad, rad + 2):
                for dz in range(-rad, rad + 2):
                    if abs(dx - 0.5) + abs(dz - 0.5) > rad + 0.5:
                        continue
                    p = (x + dx, h + dy, z + dz)
                    if p not in b and 0 <= p[0] < RUIN_W and 0 <= p[2] < RUIN_D:
                        put(b, p, 'minecraft:dark_oak_leaves',
                            {'distance': '1', 'persistent': 'true', 'waterlogged': 'false'})
        if i % 2 == 0:
            ax = x - 1 if x > RUIN_CX else x + 2
            ay = h - 3
            if 0 <= ax < RUIN_W and (ax, ay, z) not in b:
                put(b, (ax, ay + 1, z), 'minecraft:dark_oak_log', {'axis': 'x'})
                put(b, (ax, ay, z), 'minecraft:lantern',
                    {'hanging': 'true', 'waterlogged': 'false'})


def floor_growth(b):
    """Grass coming up where the paving has gone.

    Runs LAST, not inside ruin_floor. Planted there it went down before the hearth pad was laid,
    and the pad then paved over the soil underneath one of the ferns and left it rooted in
    cobblestone -- which the audit caught, but only because the audit exists. Anything that depends
    on what is already on the ground has to be placed after everything that puts things on it.
    """
    for x in range(WEST_X0 + 1, EAST_X1):
        for z in range(HALL_Z0 + 1, HALL_Z1):
            if b.get((x, 0, z), ('',))[0] in DIRT_TAG and (x, 1, z) not in b                     and noise(x, 4, z, 429) > 0.55:
                put(b, (x, 1, z), 'minecraft:short_grass'
                    if noise(x, 5, z, 429) > 0.4 else 'minecraft:fern')


VINE_DIR = {'north': (0, 0, -1), 'south': (0, 0, 1),
            'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0)}


def prune_vines(b):
    """Drop every vine that is holding on to nothing, and clear the ones covering a hanging.

    The ivy passes run before the window, the passage and the side arch are carved, and they test
    "is this cell free" -- which air satisfies. So each carve quietly left vines gripping the hole
    it had just made: one hanging across the great window, three in the passage, two in the side
    arch. All of them place fine, survive the geometry audit, and pop the first time anything
    updates. Running the test again at the end, against what the build actually finished as, is the
    only way to catch that.
    """
    solid = {'minecraft:air', 'minecraft:vine', 'minecraft:lantern', 'minecraft:water'}
    for pos, (block, props, _) in list(b.items()):
        if block != 'minecraft:vine':
            continue
        side = next((k for k, v in (props or {}).items() if v == 'true'), None)
        if side is None:
            del b[pos]
            continue
        d = VINE_DIR[side]
        target = b.get((pos[0] + d[0], pos[1] + d[1], pos[2] + d[2]))
        if target is None or target[0] in solid:
            del b[pos]
    # And never in front of a banner: the Gryffindor hanging is the one placement that carries the
    # whole two-rooms read, and ivy was covering its bottom third from the side that matters.
    for pos, (block, _, _) in list(b.items()):
        if not block.endswith('_banner_block'):
            continue
        for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
            n = (pos[0] + d[0], pos[1] + d[1], pos[2] + d[2])
            if b.get(n, ('',))[0] == 'minecraft:vine':
                del b[n]


def hogwarts_ruin_camp():
    """A ruined wing of the castle, out in the Forbidden Forest and camped in by students.

    Two courts divided by a wall that still stands: the hall, with the great window and the
    hearth, and the outer court, with the tent and the stores. Everything is arranged around one
    read -- they did not build this and they are not staying. The stonework is centuries old and
    losing to the forest, so it is cold, mossy and still symmetrical wherever it survives, and it
    is drawn at the scale of a building. Everything warm is light enough to carry and could be
    gone by morning, so it is all dark oak, canvas and lantern-light.

    The gable peaks over the window rather than over the middle of the hall, which is what leaves
    the rest of that wall free for the recess, the bookcase and the tarp to happen against.
    """
    b = {}
    ruin_ground(b)
    ruin_floor(b)
    ruin_walls(b)
    ruin_colonnade(b)
    great_window(b)
    north_recess(b)
    side_arch(b)
    ruin_rubble(b)
    back_tarpaulin(b)
    bookcase(b)
    west_court(b)
    camp_hearth(b)
    study_bench(b)
    house_banners(b)
    ruin_fittings(b)
    ruin_lighting(b)
    courtyard_posts(b)
    ruin_trees(b)
    floor_growth(b)
    prune_vines(b)

    # One student per house, so the four hangings are accounted for: three round the hearth and
    # one over in the outer court. Baked rather than left to spawn_overrides for the same reason
    # the death eaters are -- a camp generated at noon would sit empty until the spawn cycle ran.
    entities = [entity(x, y, z, 'hogwartsmagic:student',
                       {'House': ('int', h), 'PersistenceRequired': ('byte', 1)})
                for x, y, z, h in ((22.5, 1.0, 16.5, 0), (26.5, 1.0, 16.5, 1),
                                   (22.5, 1.0, 12.5, 2), (12.5, 1.0, 17.5, 3))]

    # The pair that make the hearth worth guarding: one standing just outside each of the two
    # hearth chests, turned in toward the fire, dormant until something sets them off.
    #
    # Directly outboard on the chest axis, six blocks apart, mirrored about the fire. That does
    # stand each statue in the face its own chest is turned toward, and at 1.2 blocks wide the
    # hitbox screens that approach -- the interaction ray finds the statue before the chest.
    # Accepted rather than designed around: a chest is not a one-sided block, and both of these
    # stay open from the north, the south and from above. Sliding them off the axis to clear the
    # front would cost the symmetry, which is the whole reason they read as a pair set to guard
    # the fire rather than as two mobs that happen to be standing there.
    #
    # LivingStatueEvents wakes both off either chest. Worth being honest about what that is
    # worth HERE: an unbound statue already senses players within five blocks of its own box,
    # which from this spot reaches back as far as the passage through the dividing wall, so the
    # statue beside the chest you are opening has always risen on its own before you get there.
    # What the trigger actually buys in this build is the OTHER one, six blocks away across the
    # fire and outside its own sense box from where you are standing: rob one hearth chest and
    # both come up.
    #
    # No Bound tag, deliberately. Unbound is what makes these ruin guardians rather than
    # somebody's sentry: they hunt players, they die properly, and they drop the heart, where a
    # bound one crumbles and re-forms at its post instead. PersistenceRequired has to be restated
    # even though the constructor already sets it -- Mob.readAdditionalSaveData reads that flag
    # unconditionally, so an absent key deserialises as false and undoes the constructor.
    #
    # Yaw 270 is east and 90 is west (Direction.fromYRot), so each is facing the fire at x=24.
    # The tag alone is not enough to make that survive placement: StructureTemplate rotates the
    # jigsaw piece and writes the result to yRot only, leaving the body rotation the renderer
    # actually draws pointing wherever the tag said. LivingStatueEntity re-seats head and body onto
    # yRot on every dormant tick, which is what makes these two face the fire in all four rotations.
    entities += [entity(x, 1.0, 14.5, 'hogwartsmagic:living_statue',
                        {'Rotation': ('list', 'float', [('float', yaw), ('float', 0.0)]),
                         'PersistenceRequired': ('byte', 1)})
                 for x, yaw in ((21.5, 270.0), (27.5, 90.0))]
    return b, entities


BUILDS = {
    'fen_gibbet': fen_gibbet,
    'horntail_nest': horntail_nest,
    'chamber_hall': chamber_hall,
    'goblin_vault': goblin_vault,
    'black_lake_ruin': black_lake_ruin,
    'merfolk_village': merfolk_village,
    'willow_clearing': willow_clearing,
    'death_eater_camp': death_eater_camp,
    'hogwarts_ruin_camp': hogwarts_ruin_camp,
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
# Candles want a solid face under them, exactly like a standing lantern, and they are just as easy
# to leave hanging in mid-air. Added after three lit candles were found floating four blocks up in
# front of the ruin camp's great window -- placed fine, rendered fine, and would have dropped as
# items the first time anything updated the block. The geometry audit could not see it because
# nothing here was checking candles at all.
CANDLES = {'minecraft:candle', 'minecraft:white_candle', 'minecraft:green_candle',
           'minecraft:red_candle', 'minecraft:black_candle', 'minecraft:blue_candle',
           'hogwartsmagic:candle_cluster'}
# Only these two are the hanging/standing kind. sea_lantern and jack_o_lantern are ordinary full
# cubes that happen to end in "lantern" and need no support at all.
LANTERNS = {'minecraft:lantern', 'minecraft:soul_lantern'}
# What each plant will accept underneath it. BushBlock wants BlockTags.DIRT or farmland;
# DeadBushBlock wants #dead_bush_may_place_on, which is dirt plus sand plus terracotta.
NEEDS_SOIL = {'minecraft:fern': DIRT_TAG | {'minecraft:farmland'},
              'minecraft:short_grass': DIRT_TAG | {'minecraft:farmland'},
              'minecraft:large_fern': DIRT_TAG | {'minecraft:farmland'},
              'minecraft:tall_grass': DIRT_TAG | {'minecraft:farmland'},
              'minecraft:dead_bush': DIRT_TAG | SAND_TAG}
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


def audit_paintings(blocks, entities):
    """A hung painting fails in two ways that look identical from here and fatal in game.

    HangingEntity.survives() drops the painting if anything collides with its canvas or if any block
    behind it is not solid, and it is rechecked every 100 ticks -- so a wall one block too short does
    not produce a painting sticking out over the edge, it produces a painting on the floor some
    seconds after the chunk loads. Since the anchor is not the corner, being off by one is the
    default outcome rather than an unlucky one.
    """
    problems = []
    for e in entities:
        nbt = e['nbt'][1]
        if nbt['id'][1] != 'minecraft:painting':
            continue
        ax, ay, az = (nbt['TileX'][1], nbt['TileY'][1], nbt['TileZ'][1])
        facing = next(k for k, v in FACING_2D.items() if v == nbt['facing'][1])
        variant = nbt['variant'][1]

        if variant not in PAINTING_SIZES:
            problems.append('painting %s has no painting_variant JSON' % variant)
            continue
        w, h = PAINTING_SIZES[variant][:2]

        sx, sz = STEP[facing]
        for pos in painting_span(ax, ay, az, facing, w, h):
            front = blocks.get(pos, ('minecraft:air',))[0]
            if front != 'minecraft:air':
                problems.append('painting %s covers %s at %s' % (variant, front, pos))
            behind = (pos[0] - sx, pos[1], pos[2] - sz)
            wall = blocks.get(behind, (None,))[0]
            if wall is None or see_through(wall):
                problems.append('painting %s hangs on %s at %s'
                                % (variant, wall or 'nothing', behind))
    return problems


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

        if block in CANDLES:
            below = at((x, y - 1, z))
            if below is None or below in NOT_SUPPORT:
                problems.append(f'{block} at {pos} stands on {below or "nothing"}')

        soil = NEEDS_SOIL.get(block)
        if soil is not None:
            below = at((x, y - 1, z))
            if below is None or not (below in soil or below.endswith('terracotta')):
                problems.append(f'{block} at {pos} is rooted in {below or "nothing"}')

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

UNBREAKABLE = {'hogwartsmagic:chamber_stone', 'hogwartsmagic:chamber_door_leaf'}


def audit_chamber_seal(blocks, size):
    """The Chamber claims exactly one way in. This is what checks the claim is true.

    Two things have to hold, and neither is visible in a render. Every block on the outside of the
    template must be unbreakable, or a player simply mines through the wall next to the door; and
    air reachable from the pipe mouth must stop at the door, or the shut door is decorative because
    the hall is open round the side of it. Both were wrong at some point during this build -- the
    water channels used to start inside the bulkhead and flooded straight past a closed door.
    """
    problems = []
    W, H, D = size

    for x in range(W):
        for y in range(H):
            for z in range(D):
                if not (x in (0, W - 1) or y in (0, H - 1) or z in (0, D - 1)):
                    continue
                block = blocks.get((x, y, z))
                if block is None:
                    if z != 0:                      # z=0 is the pipe mouth, and is meant to be open
                        problems.append('hull is open to the world at %s' % ((x, y, z),))
                elif block[0] not in UNBREAKABLE:
                    problems.append('hull at %s is breakable %s' % ((x, y, z), block[0]))

    # Flood the air a player can actually walk into, starting from the pipe mouth.
    mouth = [(x, y, 0) for x in range(W) for y in range(H) if (x, y, 0) not in blocks]
    seen, queue = set(mouth), list(mouth)
    while queue:
        x, y, z = queue.pop()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in seen or not all(0 <= n[i] < size[i] for i in range(3)):
                continue
            here = blocks.get(n)
            if here is None or here[0] in ('minecraft:air', 'minecraft:water'):
                seen.add(n)
                queue.append(n)
    beyond = max((p[2] for p in seen), default=0)
    if beyond > 22:
        problems.append('the shut door is bypassable: reachable air runs to z=%d' % beyond)
    # And the opposite failure, which is the one that actually happened: if the way in is itself
    # blocked, nothing is reachable, `beyond` is 0, and the check above passes while reporting that
    # a sealed lump of rock is a working Chamber. A test that cannot fail is not a test.
    if beyond < 18:
        problems.append('the way in is blocked: reachable air stops at z=%d, short of the door' % beyond)
    return problems


OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources',
                   'data', 'hogwartsmagic', 'structure')

if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    failures = 0
    for problem in audit_painting_variants():
        print('painting_variant  ' + problem)
        failures += 1
    for name, builder in BUILDS.items():
        built = builder()
        blocks, entities = built if isinstance(built, tuple) else (built, [])
        size = [max(p[i] for p in blocks) + 1 for i in range(3)]
        problems = (audit(name, blocks) + audit_entities(blocks, entities, size)
                    + audit_paintings(blocks, entities))
        if name == 'chamber_hall':
            problems += audit_chamber_seal(blocks, size)
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
