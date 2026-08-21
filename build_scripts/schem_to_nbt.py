"""Convert a Sponge schematic (.schem) into a vanilla structure template (.nbt).

    python build_scripts/schem_to_nbt.py <in.schem> [-o out.nbt] [options]

WHY THIS EXISTS
---------------
Both of this repo's structure renderers -- .claude/skills/rendering-minecraft-structures/
render_structure.py and the deepslate viewer under textured/ -- read vanilla structure
templates and nothing else. The two biggest builds in the mod, Gringotts and Mini
Hogwarts, are Sponge schematics, so neither has ever been rendered. Nobody has actually
looked at the Gringotts bank.

That is fine right up until you want to cut a lift shaft into its back rotunda, at which
point reading the block array in a text cross-section stops being good enough.

The output is deliberately SPARSE: air cells are dropped unless --keep-air is passed. A
structure template lists explicit positions, so an omitted cell simply is not drawn, and
dropping air takes gringotts.schem from 149,600 entries down to about 21,000 -- the
difference between a file you can open and one you cannot. Vanilla's own structure blocks
save air explicitly; neither renderer cares either way.

This writes something to LOOK at, not something to place in the world. Block entity NBT
and entities are not carried across.
"""

import argparse
import gzip
import io
import os
import struct
import sys


# --- NBT reading ---------------------------------------------------------------------

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, \
    INT_ARRAY, LONG_ARRAY = range(13)

_FMT = {BYTE: '>b', SHORT: '>h', INT: '>i', LONG: '>q', FLOAT: '>f', DOUBLE: '>d'}


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
        return struct.unpack(fmt, self.raw(size))[0]

    def string(self):
        return self.raw(self.num('>H')).decode('utf-8')

    def payload(self, tag):
        if tag in _FMT:
            return self.num(_FMT[tag])
        if tag == BYTE_ARRAY:
            return self.raw(self.num('>i'))
        if tag == STRING:
            return self.string()
        if tag == LIST:
            elem = self.num('>b')
            return [self.payload(elem) for _ in range(self.num('>i'))]
        if tag == COMPOUND:
            out = {}
            while True:
                child = self.num('>b')
                if child == END:
                    return out
                # Name first, deliberately. `out[self.string()] = self.payload(child)`
                # reads the VALUE before the name -- Python evaluates the right-hand
                # side of an assignment first -- which desynchronises the whole stream.
                name = self.string()
                out[name] = self.payload(child)
        if tag == INT_ARRAY:
            return [self.num('>i') for _ in range(self.num('>i'))]
        if tag == LONG_ARRAY:
            return [self.num('>q') for _ in range(self.num('>i'))]
        raise ValueError('unknown tag id %d' % tag)


def read_nbt(path):
    with open(path, 'rb') as fh:
        head = fh.read(2)
        fh.seek(0)
        data = gzip.decompress(fh.read()) if head == b'\x1f\x8b' else fh.read()
    r = Reader(data)
    if r.num('>b') != COMPOUND:
        sys.exit('%s is not an NBT compound' % path)
    r.string()
    return r.payload(COMPOUND)


# --- NBT writing ---------------------------------------------------------------------
# Mirrors the tagged-tuple style in generate_biome_structures.py so the two read alike,
# but kept standalone on purpose: this must not break when that script changes.

_WRITE_IDS = {'int': INT, 'string': STRING, 'list': LIST, 'compound': COMPOUND}


def _tag(tag_id, name, payload):
    encoded = name.encode('utf-8')
    return struct.pack('>BH', tag_id, len(encoded)) + encoded + payload


def _payload(value):
    kind = value[0]
    if kind == 'int':
        return struct.pack('>i', value[1])
    if kind == 'string':
        encoded = value[1].encode('utf-8')
        return struct.pack('>H', len(encoded)) + encoded
    if kind == 'list':
        _, elem, items = value
        out = struct.pack('>bi', _WRITE_IDS[elem], len(items))
        return out + b''.join(_payload(i) for i in items)
    if kind == 'compound':
        out = b''
        for key, child in value[1].items():
            out += _tag(_WRITE_IDS[child[0]], key, _payload(child))
        return out + b'\x00'
    raise ValueError('unknown value kind %r' % (kind,))


def write_nbt(path, root):
    """mtime=0 so a re-run never shows the file as modified when nothing changed."""
    buf = io.BytesIO()
    buf.write(struct.pack('>b', COMPOUND))
    buf.write(struct.pack('>H', 0))
    buf.write(_payload(root))
    with open(path, 'wb') as raw:
        with gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as fh:
            fh.write(buf.getvalue())


# --- schematic decoding --------------------------------------------------------------

AIR = ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')


def decode_schem(root):
    """-> (width, height, length, {id: blockstate string}, cells)

    Handles Sponge v2 (flat root) and v3 (a Schematic compound wrapping a Blocks
    compound) -- the same two shapes GringottsSchematic.java copes with, for the same
    reason: mini_hogwarts.schem is v3 and gringotts.schem is v2.
    """
    schem = root.get('Schematic', root)
    width = schem['Width'] & 0xFFFF
    height = schem['Height'] & 0xFFFF
    length = schem['Length'] & 0xFFFF

    blocks = schem.get('Blocks', schem)
    palette = blocks['Palette']
    data = blocks.get('Data', blocks.get('BlockData'))
    if data is None:
        sys.exit('no block data found (looked for Blocks/Data and BlockData)')

    by_id = {v: k for k, v in palette.items()}

    expected = width * height * length
    cells = []
    cursor = 0
    while len(cells) < expected and cursor < len(data):
        value = 0
        shift = 0
        while True:
            byte = data[cursor]
            cursor += 1
            value |= (byte & 0x7F) << shift
            if not byte & 0x80:
                break
            shift += 7
        cells.append(value)
    if len(cells) != expected:
        print('warning: decoded %d cells, expected %d' % (len(cells), expected),
              file=sys.stderr)
    return width, height, length, by_id, cells


def split_state(state):
    """minecraft:oak_stairs[facing=east,half=top] -> (name, {facing: east, half: top})"""
    if '[' not in state:
        return state, {}
    name, _, rest = state.partition('[')
    props = {}
    for pair in rest.rstrip(']').split(','):
        if '=' in pair:
            key, _, val = pair.partition('=')
            props[key.strip()] = val.strip()
    return name, props


# --- main ----------------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('schem')
    ap.add_argument('-o', '--out', help='default: build/schem_nbt/<stem>.nbt')
    ap.add_argument('--keep-air', action='store_true',
                    help='write explicit air cells too (far larger, rarely wanted)')
    ap.add_argument('--data-version', type=int, default=3953, help='default 3953 (1.21)')
    ap.add_argument('--crop', nargs=6, type=int,
                    metavar=('X0', 'Y0', 'Z0', 'X1', 'Y1', 'Z1'),
                    help='keep only this inclusive box, re-origined to 0,0,0')
    args = ap.parse_args()

    width, height, length, by_id, cells = decode_schem(read_nbt(args.schem))
    print('%s  %dx%dx%d  %d cells  palette %d'
          % (os.path.basename(args.schem), width, height, length, len(cells), len(by_id)))

    x0, y0, z0 = 0, 0, 0
    x1, y1, z1 = width - 1, height - 1, length - 1
    if args.crop:
        cx0, cy0, cz0, cx1, cy1, cz1 = args.crop
        x0, x1 = max(0, min(cx0, cx1)), min(width - 1, max(cx0, cx1))
        y0, y1 = max(0, min(cy0, cy1)), min(height - 1, max(cy0, cy1))
        z0, z1 = max(0, min(cz0, cz1)), min(length - 1, max(cz0, cz1))
        print('  cropped to (%d,%d,%d)-(%d,%d,%d)' % (x0, y0, z0, x1, y1, z1))

    palette_index = {}
    palette_entries = []
    block_tags = []
    skipped = 0

    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                index = x + z * width + y * width * length
                if index >= len(cells):
                    continue
                state = by_id.get(cells[index], 'minecraft:air')
                if not args.keep_air and state.split('[')[0] in AIR:
                    skipped += 1
                    continue
                if state not in palette_index:
                    name, props = split_state(state)
                    entry = {'Name': ('string', name)}
                    if props:
                        entry['Properties'] = (
                            'compound', {k: ('string', v) for k, v in props.items()})
                    palette_index[state] = len(palette_entries)
                    palette_entries.append(('compound', entry))
                block_tags.append(('compound', {
                    'pos': ('list', 'int',
                            [('int', x - x0), ('int', y - y0), ('int', z - z0)]),
                    'state': ('int', palette_index[state]),
                }))

    size = [x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1]
    root = ('compound', {
        'DataVersion': ('int', args.data_version),
        'size': ('list', 'int', [('int', c) for c in size]),
        'palette': ('list', 'compound', palette_entries),
        'blocks': ('list', 'compound', block_tags),
        'entities': ('list', 'compound', []),
    })

    out = args.out
    if not out:
        stem = os.path.splitext(os.path.basename(args.schem))[0]
        repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        out = os.path.join(repo, 'build', 'schem_nbt', stem + '.nbt')
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    write_nbt(out, root)

    print('  wrote %s\n  %dx%dx%d  %d blocks  %d palette  %d air skipped  %d bytes'
          % (out, size[0], size[1], size[2], len(block_tags), len(palette_entries),
             skipped, os.path.getsize(out)))


if __name__ == '__main__':
    main()
