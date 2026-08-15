"""Render a Minecraft structure template (.nbt) as pictures you can actually look at.

    python render_structure.py <file.nbt> [options]

Reads the gzipped NBT itself, so it works on any 1.13+ structure template -- ones a generator
script produced, ones saved with a structure block in game, or vanilla's own. Nothing here imports
the project that built the file.

WHY THIS EXISTS
---------------
Structure templates are written blind. Validators check that blocks are legal, that lanterns have
something to hang from, that chests do not face a wall -- all necessary, none of it able to tell you
that a wall is twice as tall as it should be, or that the fire everything is arranged around has no
presence at all, or that a row of trunks reads as fence posts. Those are the faults that survive
every automated check and then greet you the moment you stand in the place.

Views:
  iso        isometric, painter's algorithm. The one that settles composition questions.
  layers     one top-down slice per Y. Best for verifying a pattern -- a dome, a ring, a spiral.
  elevation  flat orthographic from one side. Cheap, occasionally clarifying.
  section    a text cross-section printed to stdout. Settles geometry the pictures cannot.

Options that matter more than they look:
  --hide cobweb        Anything overhead hides everything under it. Nearly always needed.
  --crop / --cell      A whole build at small cell size cannot settle fine detail. Crop and enlarge.
"""

import argparse
import gzip
import hashlib
import io
import math
import os
import struct
import sys
import tempfile

try:
    from PIL import Image, ImageDraw
except ImportError:
    sys.exit('needs Pillow:  pip install Pillow')

# --- NBT reader ----------------------------------------------------------------------------------

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, \
    LONG_ARRAY = range(13)


class Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def raw(self, n):
        out = self.b[self.i:self.i + n]
        self.i += n
        return out

    def num(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack('>' + fmt, self.raw(size))[0]

    def string(self):
        return self.raw(self.num('H')).decode('utf-8', 'replace')

    def payload(self, kind):
        if kind == BYTE:
            return self.num('b')
        if kind == SHORT:
            return self.num('h')
        if kind == INT:
            return self.num('i')
        if kind == LONG:
            return self.num('q')
        if kind == FLOAT:
            return self.num('f')
        if kind == DOUBLE:
            return self.num('d')
        if kind == BYTE_ARRAY:
            return list(self.raw(self.num('i')))
        if kind == STRING:
            return self.string()
        if kind == LIST:
            elem, count = self.num('b'), self.num('i')
            return [self.payload(elem) for _ in range(count)]
        if kind == COMPOUND:
            out = {}
            while True:
                child = self.num('b')
                if child == END:
                    return out
                # Both of these advance the cursor, so the order they run in is the parse. Written
                # as `out[self.string()] = self.payload(child)` it silently reads them backwards:
                # Python evaluates the right-hand side of an assignment before the target, so the
                # payload gets consumed starting at the name. The parse then drifts and dies dozens
                # of tags later somewhere that looks unrelated.
                name = self.string()
                out[name] = self.payload(child)
        if kind == INT_ARRAY:
            return [self.num('i') for _ in range(self.num('i'))]
        if kind == LONG_ARRAY:
            return [self.num('q') for _ in range(self.num('i'))]
        raise ValueError('unknown tag %d at byte %d' % (kind, self.i))


def read_template(path):
    """-> (blocks {(x,y,z): (name, props)}, entities [(x,y,z,id)], size [x,y,z])"""
    with open(path, 'rb') as fh:
        head = fh.read(2)
        fh.seek(0)
        data = gzip.decompress(fh.read()) if head == b'\x1f\x8b' else fh.read()

    r = Reader(data)
    if r.num('b') != COMPOUND:
        sys.exit('%s is not an NBT compound' % path)
    r.string()                                    # root name, conventionally empty
    root = r.payload(COMPOUND)

    palette = []
    for entry in root.get('palette', []):
        palette.append((entry['Name'], entry.get('Properties', {})))

    blocks = {}
    for tag in root.get('blocks', []):
        x, y, z = tag['pos']
        blocks[(x, y, z)] = palette[tag['state']]

    entities = []
    for tag in root.get('entities', []):
        pos = tag.get('pos') or [0, 0, 0]
        entities.append((pos[0], pos[1], pos[2], tag.get('nbt', {}).get('id', '?')))

    return blocks, entities, root.get('size', [0, 0, 0])


# --- colour --------------------------------------------------------------------------------------
# Semantic colours for what turns up most. Anything unlisted gets a stable colour hashed from its
# name, which matters: an unlisted block rendered as one fixed "unknown" colour is invisible against
# every other unlisted block, and you end up staring at a shape you cannot read.

PALETTE = {
    'air': None,
    'water': (58, 92, 190), 'lava': (208, 110, 30), 'cobweb': (216, 220, 228),
    'grass_block': (104, 148, 66), 'dirt': (140, 100, 68), 'coarse_dirt': (122, 88, 58),
    'podzol': (92, 64, 34), 'dirt_path': (156, 130, 84), 'gravel': (140, 136, 132),
    'sand': (218, 210, 158), 'clay': (160, 166, 178), 'mud': (60, 52, 50),
    'stone': (128, 128, 128), 'cobblestone': (122, 122, 122),
    'mossy_cobblestone': (104, 118, 92), 'andesite': (136, 136, 136),
    'stone_bricks': (122, 122, 122), 'mossy_stone_bricks': (110, 124, 100),
    'cracked_stone_bricks': (112, 112, 112), 'chiseled_stone_bricks': (134, 134, 134),
    'deepslate': (72, 72, 78), 'cobbled_deepslate': (88, 88, 94),
    'deepslate_bricks': (80, 80, 86), 'cracked_deepslate_bricks': (68, 68, 74),
    'polished_deepslate': (74, 74, 80), 'chiseled_deepslate': (100, 100, 108),
    'deepslate_tiles': (62, 62, 68),
    'blackstone': (44, 38, 44), 'polished_blackstone': (56, 48, 56),
    'gilded_blackstone': (96, 70, 40), 'basalt': (78, 76, 84), 'magma_block': (146, 62, 30),
    'prismarine': (100, 160, 148), 'prismarine_bricks': (110, 172, 158),
    'dark_prismarine': (58, 96, 84), 'sea_lantern': (196, 228, 216),
    'oak_log': (124, 98, 58), 'dark_oak_log': (66, 45, 27),
    'stripped_dark_oak_log': (152, 112, 68), 'spruce_log': (96, 72, 44),
    'oak_planks': (162, 130, 78), 'dark_oak_planks': (66, 46, 28),
    'spruce_planks': (114, 84, 48), 'bone_block': (206, 202, 182),
    'iron_bars': (150, 154, 158), 'chain': (178, 178, 188), 'lantern': (208, 158, 78),
    'soul_lantern': (120, 225, 240), 'torch': (226, 190, 96),
    'campfire': (214, 132, 52), 'soul_campfire': (70, 200, 225),
    'chest': (196, 148, 62), 'barrel': (162, 122, 66), 'cauldron': (58, 58, 64),
    'furnace': (110, 110, 110), 'crafting_table': (150, 108, 62),
    'fern': (78, 136, 62), 'large_fern': (78, 136, 62), 'short_grass': (96, 150, 68),
    'dead_bush': (124, 96, 52), 'vine': (72, 116, 52), 'lily_pad': (60, 128, 60),
    'seagrass': (72, 140, 74), 'kelp': (66, 128, 60), 'glow_lichen': (124, 154, 118),
    'sea_pickle': (106, 132, 46),
    'white_wool': (232, 232, 232), 'black_wool': (34, 32, 38), 'gray_wool': (88, 86, 92),
    'brown_wool': (110, 76, 46), 'red_wool': (150, 52, 48),
    'black_carpet': (26, 24, 30), 'white_carpet': (226, 226, 226),
}

# Blocks with no meaningful cube volume. Drawn as a dot so they do not wall off what is behind them.
THIN_WORDS = ('carpet', 'fence', 'chain', 'torch', 'lantern', 'cobweb', 'rail', 'vine', 'grass',
              'fern', 'bush', 'flower', 'sapling', 'mushroom', 'seagrass', 'kelp', 'coral',
              'candle', 'campfire', 'cauldron', 'lily', 'pressure_plate', 'button', 'sign',
              'banner', 'pane', 'bars', 'lichen', 'snow', 'string')
HALF_WORDS = ('_slab',)

_fallbacks = set()


# A slab is the colour of the thing it is cut from. Deriving these matters more than it sounds:
# stairs, slabs, walls and fences are most of the decoration in any build, and giving each of them
# an unrelated hashed colour scatters confetti over exactly the surfaces whose shape you are trying
# to read.
SHAPE_SUFFIXES = ('_slab', '_stairs', '_wall', '_fence', '_fence_gate', '_button',
                  '_pressure_plate', '_trapdoor', '_door', '_sign')


def _derive(short):
    for suffix in SHAPE_SUFFIXES:
        if not short.endswith(suffix):
            continue
        base = short[:-len(suffix)]
        for guess in (base, base + 's', base + '_planks', base + '_log', base[:-1]):
            if guess in PALETTE:
                return PALETTE[guess]
    return None


def colour(name):
    short = name.split(':')[-1]
    if short in PALETTE:
        return PALETTE[short]
    derived = _derive(short)
    if derived is not None:
        return derived
    _fallbacks.add(name)
    h = hashlib.md5(short.encode()).digest()
    # Keep hashed colours mid-bright and lightly saturated so they read against the real palette
    # without ever being mistaken for one of it.
    return (90 + h[0] % 130, 90 + h[1] % 130, 90 + h[2] % 130)


def is_thin(name):
    short = name.split(':')[-1]
    return any(w in short for w in THIN_WORDS)


def is_half(name):
    return any(w in name for w in HALF_WORDS)


def shade(c, f):
    return tuple(min(255, max(0, int(v * f))) for v in c)


# --- views ---------------------------------------------------------------------------------------

def draw_iso(blocks, entities, path, title, cell=12, tilt=0.5):
    keys = [k for k, v in blocks.items() if v[0] != 'minecraft:air']
    if not keys:
        sys.exit('nothing to draw (everything hidden or filtered?)')
    hh = max(1, int(cell * tilt))

    def screen(x, y, z):
        return ((x - z) * cell, (x + z) * hh - y * cell)

    pts = [screen(*k) for k in keys]
    x0 = min(p[0] for p in pts) - cell * 2
    x1 = max(p[0] for p in pts) + cell * 2
    y0 = min(p[1] for p in pts) - cell * 2
    y1 = max(p[1] for p in pts) + cell * 3
    img = Image.new('RGB', (int(x1 - x0), int(y1 - y0)), (18, 18, 24))
    d = ImageDraw.Draw(img, 'RGBA')

    # Painter's algorithm: far corner first, so nearer blocks overwrite.
    for x, y, z in sorted(keys, key=lambda k: (k[0] + k[2], k[1])):
        name = blocks[(x, y, z)][0]
        col = colour(name)
        if col is None:
            continue
        sx, sy = screen(x, y, z)
        sx -= x0
        sy -= y0
        if is_thin(name):
            r = max(2, cell // 3)
            d.ellipse([sx - r, sy - r, sx + r, sy + r],
                      fill=col + (170 if 'cobweb' in name else 255,))
            continue
        top = cell // 2 if is_half(name) else cell
        d.polygon([(sx, sy - top), (sx + cell, sy + hh - top),
                   (sx, sy + 2 * hh - top), (sx - cell, sy + hh - top)], fill=shade(col, 1.25))
        d.polygon([(sx - cell, sy + hh - top), (sx, sy + 2 * hh - top),
                   (sx, sy + 2 * hh), (sx - cell, sy + hh)], fill=shade(col, 0.72))
        d.polygon([(sx + cell, sy + hh - top), (sx, sy + 2 * hh - top),
                   (sx, sy + 2 * hh), (sx + cell, sy + hh)], fill=shade(col, 0.95))

    for ex, ey, ez, eid in entities:
        sx, sy = screen(ex, ey, ez)
        sx -= x0
        sy -= y0
        r = max(3, cell // 3)
        d.ellipse([sx - r, sy - r, sx + r, sy + r], fill=(255, 70, 70), outline=(255, 220, 220))

    if title:
        d.text((8, 8), title, fill=(232, 232, 240))
    img.save(path)
    return img.size


def bounds(blocks):
    """Extent of what is actually left after --hide/--crop/--max-y, not the template's declared size.

    Drawing to the declared size instead means a crop still renders the full footprint with the
    cropped-away part blank, so zooming in changes nothing you can see.
    """
    ks = list(blocks)
    return [(min(k[i] for k in ks), max(k[i] for k in ks)) for i in range(3)]


def draw_layers(blocks, entities, path, title, cell=7, cols=4):
    (x0, x1), (y0, y1), (z0, z1) = bounds(blocks)
    sx, sz = x1 - x0 + 1, z1 - z0 + 1
    rows = (y1 - y0 + cols) // cols
    pad = 10
    img = Image.new('RGB', (cols * (sx * cell + pad) + pad,
                            rows * (sz * cell + pad + 12) + pad), (14, 14, 18))
    d = ImageDraw.Draw(img)
    for n, y in enumerate(range(y0, y1 + 1)):
        ox = pad + (n % cols) * (sx * cell + pad)
        oy = pad + 12 + (n // cols) * (sz * cell + pad + 12)
        d.text((ox, oy - 11), 'y=%d' % y, fill=(190, 190, 200))
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                v = blocks.get((x, y, z))
                col = (24, 24, 28) if not v else (colour(v[0]) or (24, 24, 28))
                px, pz = ox + (x - x0) * cell, oy + (z - z0) * cell
                d.rectangle([px, pz, px + cell - 1, pz + cell - 1], fill=col)
        for ex, ey, ez, _ in entities:
            if int(ey) == y:
                px, pz = ox + (ex - x0) * cell, oy + (ez - z0) * cell
                d.ellipse([px - 3, pz - 3, px + 3, pz + 3], fill=(255, 70, 70))
    if title:
        d.text((8, 2), title, fill=(232, 232, 240))
    img.save(path)
    return img.size


def draw_elevation(blocks, path, title, facing='south', cell=12):
    (x0, x1), (y0, y1), (z0, z1) = bounds(blocks)
    img = Image.new('RGB', ((x1 - x0 + 1) * cell, (y1 - y0 + 1) * cell), (18, 18, 24))
    d = ImageDraw.Draw(img)
    zs = range(z1, z0 - 1, -1) if facing == 'south' else range(z0, z1 + 1)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            hit = None
            for z in zs:
                v = blocks.get((x, y, z))
                if v and v[0] != 'minecraft:air':
                    hit = v[0]
                    break
            col = (18, 18, 24) if hit is None else (colour(hit) or (18, 18, 24))
            px, py = (x - x0) * cell, (y1 - y) * cell
            d.rectangle([px, py, px + cell - 1, py + cell - 1], fill=col)
    if title:
        d.text((6, 4), title, fill=(232, 232, 240))
    img.save(path)
    return img.size


GLYPHS = [('air', ' . '), ('cobweb', ' * '), ('water', ' ~ '), ('_log', '=L='),
          ('_slab', '[_]'), ('_stairs', '[/]'), ('_fence', ' | '), ('carpet', ' _ '),
          ('lantern', ' o '), ('chain', ' : ')]


def print_section(blocks, axis, index):
    """A text cross-section. Pictures show you a shape; this tells you exactly what is where."""
    (x0, x1), (y0, y1), (z0, z1) = bounds(blocks)
    across = range(z0, z1 + 1) if axis == 'x' else range(x0, x1 + 1)
    print('cross-section at %s=%d  (%s increasing to the right, y up)'
          % (axis, index, 'z' if axis == 'x' else 'x'))

    rows = []
    for y in range(y1, y0 - 1, -1):
        row = ''
        for a in across:
            pos = (index, y, a) if axis == 'x' else (a, y, index)
            v = blocks.get(pos)
            if not v or v[0] == 'minecraft:air':
                row += ' . '
                continue
            glyph = next((g for token, g in GLYPHS if token in v[0]), None)
            row += glyph or ('[%s]' % v[0].split(':')[-1][0].upper())
        rows.append((y, row))

    # Trim blank rows off the top and bottom, but keep every one in between. Dropping interior
    # blanks compresses the drawing and makes it lie about height -- a roof four blocks above a
    # floor prints as though it sat directly on it, which is the exact question this view is for.
    filled = [i for i, (_, r) in enumerate(rows) if r.strip(' .')]
    if not filled:
        print('  (nothing on this plane)')
        return
    for y, row in rows[filled[0]:filled[-1] + 1]:
        print('  y=%2d %s' % (y, row))


# --- driver --------------------------------------------------------------------------------------

def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument('nbt')
    # Not '.': the default must never scatter PNGs through a git repo just because someone
    # ran the script without reading the flags.
    p.add_argument('--out', default=os.path.join(tempfile.gettempdir(), 'structure_renders'),
                   help='directory for the images (default: a folder in the system temp dir)')
    p.add_argument('--views', default=None,
                   help='iso,layers,elevation  (default iso,layers; none if --section is used)')
    p.add_argument('--hide', default='',
                   help='comma-separated substrings of block ids to omit, e.g. cobweb,leaves')
    p.add_argument('--crop', default='', help='x0,z0,x1,z1 to zoom in on part of the build')
    p.add_argument('--max-y', type=int, default=None, help='drop everything above this Y')
    p.add_argument('--cell', type=int, default=12, help='pixels per block in the iso view')
    p.add_argument('--section', default='', help="axis=index, e.g. x=6 -- prints, does not draw")
    args = p.parse_args()

    blocks, entities, size = read_template(args.nbt)
    stem = os.path.splitext(os.path.basename(args.nbt))[0]
    print('%s  %dx%dx%d  %d blocks  %d entities'
          % (stem, size[0], size[1], size[2], len(blocks), len(entities)))

    if args.hide:
        words = [w.strip() for w in args.hide.split(',') if w.strip()]
        before = len(blocks)
        blocks = {k: v for k, v in blocks.items() if not any(w in v[0] for w in words)}
        print('  hid %d blocks matching %s' % (before - len(blocks), words))
    if args.max_y is not None:
        blocks = {k: v for k, v in blocks.items() if k[1] <= args.max_y}
    if args.crop:
        cx0, cz0, cx1, cz1 = (int(v) for v in args.crop.split(','))
        blocks = {k: v for k, v in blocks.items()
                  if cx0 <= k[0] <= cx1 and cz0 <= k[2] <= cz1}
        entities = [e for e in entities if cx0 <= e[0] <= cx1 and cz0 <= e[2] <= cz1]
        print('  cropped to x %d..%d, z %d..%d -> %d blocks' % (cx0, cx1, cz0, cz1, len(blocks)))

    if args.section:
        axis, _, idx = args.section.partition('=')
        print_section(blocks, axis.strip(), int(idx))

    # Asking for a section and getting a pile of images back is a surprise, so --section on its
    # own draws nothing. Pass --views explicitly to get both.
    views = args.views if args.views is not None else ('' if args.section else 'iso,layers')
    wanted = [v.strip() for v in views.split(',') if v.strip()]
    if not wanted:
        return
    os.makedirs(args.out, exist_ok=True)
    for view in wanted:
        out = os.path.join(args.out, '%s_%s.png' % (stem, view))
        if view == 'iso':
            dim = draw_iso(blocks, entities, out, stem, cell=args.cell)
        elif view == 'layers':
            dim = draw_layers(blocks, entities, out, stem)
        elif view == 'elevation':
            dim = draw_elevation(blocks, out, stem)
        else:
            sys.exit('unknown view %r' % view)
        print('  wrote %s  %dx%d' % (out, dim[0], dim[1]))

    if _fallbacks:
        # Worth saying out loud. A hashed colour is readable but arbitrary, so anything important
        # in this list is worth adding to PALETTE before you judge the build on colour.
        print('  hashed colours (not in PALETTE): %s' % ', '.join(sorted(_fallbacks)))


if __name__ == '__main__':
    main()
