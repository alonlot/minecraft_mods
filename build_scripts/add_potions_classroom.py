"""Furnish the Potions classroom in mini_hogwarts.schem.

    python3 build_scripts/add_potions_classroom.py [--schem PATH] [--dry-run]

WHERE
-----
Behind the Transfiguration room of the gatehouse (the spruce door at schematic x=38, y=33-34,
z=106) stands the tall apse tower (x 32..45, z 106..120). It has walls from y=31 up to its roof at
y~63 but no floor: the door opened onto a five-block ledge (cobblestone at y=32, x 34..38,
z 107..109) and a drop of about thirty blocks to whatever ground the castle was dropped on. This
script lays a floor at y=32 over every air cell enclosed by the tower walls (96 cells: x 34..42,
z 107..119) and furnishes the room at y=33:

  * six student cauldrons (x 36, 38, 40 by z 111, 114) and a teacher's cauldron at (38, 116),
    each a hogwartsmagic:wizard_cauldron[level=3] (a full, classic green brew) over a magma
    block in the floor, so every cauldron is heated and works the moment it is used;
  * a lectern at (38, 118) facing the class;
  * twelve empty chiseled bookshelves (the shelves) along the west wall, x 34, z 110..115, y 33-34;
  * along the east wall, x 42: barrels at z 110, 112, 113, 115, two lanterns on the end barrels,
    and two chests (facing west) at z 111 and 114. MiniHogwartsManager fills every chest of the
    schematic with ClassroomSupplies when a castle is built (the castle had no chests before).

Nothing else changes: every other cell, the palette order, the block entities and the metadata are
written back as they were. The NBT is read and written with tag types preserved, and the script
proves it by re-encoding the untouched tree and comparing bytes before it edits anything.

Re-running is safe: a schematic that already holds a wizard cauldron is left alone.
"""

import argparse
import gzip
import io
import os
import struct
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SCHEM = os.path.join(REPO, 'src', 'main', 'resources', 'data', 'hogwartsmagic',
                             'schematic', 'mini_hogwarts.schem')

# --- type-preserving NBT -------------------------------------------------------------
# A node is (tag_id, value). Compounds are lists of (name, node) pairs so the key order
# survives; lists are (elem_id, [values]).

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, \
    INT_ARRAY, LONG_ARRAY = range(13)
_FMT = {BYTE: '>b', SHORT: '>h', INT: '>i', LONG: '>q', FLOAT: '>f', DOUBLE: '>d'}


class Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def take(self, n):
        out = self.b[self.i:self.i + n]
        self.i += n
        return out

    def num(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self):
        return self.take(self.num('>H')).decode('utf-8')

    def payload(self, tag):
        if tag in _FMT:
            return self.num(_FMT[tag])
        if tag == BYTE_ARRAY:
            return bytes(self.take(self.num('>i')))
        if tag == STRING:
            return self.string()
        if tag == LIST:
            elem = self.num('>b')
            count = self.num('>i')
            return (elem, [self.payload(elem) for _ in range(count)])
        if tag == COMPOUND:
            items = []
            while True:
                child = self.num('>b')
                if child == END:
                    return items
                name = self.string()
                items.append((name, (child, self.payload(child))))
        if tag == INT_ARRAY:
            return [self.num('>i') for _ in range(self.num('>i'))]
        if tag == LONG_ARRAY:
            return [self.num('>q') for _ in range(self.num('>i'))]
        raise ValueError('unknown tag id %d' % tag)


def encode_payload(tag, value):
    if tag in _FMT:
        return struct.pack(_FMT[tag], value)
    if tag == BYTE_ARRAY:
        return struct.pack('>i', len(value)) + bytes(value)
    if tag == STRING:
        raw = value.encode('utf-8')
        return struct.pack('>H', len(raw)) + raw
    if tag == LIST:
        elem, values = value
        return struct.pack('>bi', elem, len(values)) + b''.join(encode_payload(elem, v) for v in values)
    if tag == COMPOUND:
        out = b''
        for name, (child, child_value) in value:
            raw = name.encode('utf-8')
            out += struct.pack('>bH', child, len(raw)) + raw + encode_payload(child, child_value)
        return out + b'\x00'
    if tag == INT_ARRAY:
        return struct.pack('>i', len(value)) + b''.join(struct.pack('>i', v) for v in value)
    if tag == LONG_ARRAY:
        return struct.pack('>i', len(value)) + b''.join(struct.pack('>q', v) for v in value)
    raise ValueError('unknown tag id %d' % tag)


def read_root(raw):
    r = Reader(raw)
    if r.num('>b') != COMPOUND:
        sys.exit('not an NBT compound')
    name = r.string()
    value = r.payload(COMPOUND)
    if r.i != len(raw):
        sys.exit('trailing bytes after the root compound')
    return name, value


def encode_root(name, value):
    raw = name.encode('utf-8')
    return struct.pack('>bH', COMPOUND, len(raw)) + raw + encode_payload(COMPOUND, value)


def child(compound, name):
    for key, node in compound:
        if key == name:
            return node
    sys.exit('missing tag %s' % name)


def set_child(compound, name, node):
    for i, (key, _) in enumerate(compound):
        if key == name:
            compound[i] = (name, node)
            return
    sys.exit('missing tag %s' % name)


# --- varints (the Sponge block data) ----------------------------------------------------

def decode_varints(data, expected):
    out = []
    cursor = 0
    while len(out) < expected and cursor < len(data):
        value = 0
        shift = 0
        while True:
            byte = data[cursor]
            cursor += 1
            value |= (byte & 0x7F) << shift
            if not byte & 0x80:
                break
            shift += 7
        out.append(value)
    if len(out) != expected or cursor != len(data):
        sys.exit('block data: decoded %d cells from %d of %d bytes, expected %d cells'
                 % (len(out), cursor, len(data), expected))
    return out


def encode_varints(values):
    out = bytearray()
    for value in values:
        while True:
            byte = value & 0x7F
            value >>= 7
            if value:
                out.append(byte | 0x80)
            else:
                out.append(byte)
                break
    return bytes(out)


# --- the room ---------------------------------------------------------------------------

FLOOR_Y = 32
ROOM_Y = 33
SEED = (39, 112)              # an air cell in the middle of the tower at FLOOR_Y
EXPECTED_FLOOR_CELLS = 96
EXPECTED_BOX = (34, 42, 107, 119)   # x0, x1, z0, z1 of the enclosed air at FLOOR_Y

FLOOR = 'minecraft:stone_bricks'
HEAT = 'minecraft:magma_block'
CAULDRON = 'hogwartsmagic:wizard_cauldron[level=3]'
LECTERN = 'minecraft:lectern[facing=north,has_book=false,powered=false]'
SHELF = ('minecraft:chiseled_bookshelf[facing=east,slot_0_occupied=false,slot_1_occupied=false,'
         'slot_2_occupied=false,slot_3_occupied=false,slot_4_occupied=false,slot_5_occupied=false]')
BARREL = 'minecraft:barrel[facing=up,open=false]'
CHEST = 'minecraft:chest[facing=west,type=single,waterlogged=false]'
LANTERN = 'minecraft:lantern[hanging=false,waterlogged=false]'

CAULDRONS = [(36, 111), (38, 111), (40, 111), (36, 114), (38, 114), (40, 114), (38, 116)]


def furniture():
    """(x, y, z, state) for everything that stands on the floor."""
    out = [(x, ROOM_Y, z, CAULDRON) for x, z in CAULDRONS]
    out.append((38, ROOM_Y, 118, LECTERN))
    for z in range(110, 116):
        out.append((34, ROOM_Y, z, SHELF))
        out.append((34, ROOM_Y + 1, z, SHELF))
    for z in (110, 112, 113, 115):
        out.append((42, ROOM_Y, z, BARREL))
    for z in (111, 114):
        out.append((42, ROOM_Y, z, CHEST))
    out.append((42, ROOM_Y + 1, 110, LANTERN))
    out.append((42, ROOM_Y + 1, 115, LANTERN))
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--schem', default=DEFAULT_SCHEM)
    ap.add_argument('--dry-run', action='store_true', help='check and report, write nothing')
    args = ap.parse_args()

    with open(args.schem, 'rb') as fh:
        raw = gzip.decompress(fh.read())
    root_name, root = read_root(raw)
    if encode_root(root_name, root) != raw:
        sys.exit('NBT round trip is not byte-identical; refusing to edit')

    schem = child(root, 'Schematic')[1]
    width = child(schem, 'Width')[1] & 0xFFFF
    height = child(schem, 'Height')[1] & 0xFFFF
    length = child(schem, 'Length')[1] & 0xFFFF
    blocks = child(schem, 'Blocks')[1]
    palette_tag, palette = child(blocks, 'Palette')
    data_tag, data = child(blocks, 'Data')
    if palette_tag != COMPOUND or data_tag != BYTE_ARRAY:
        sys.exit('unexpected Palette/Data tag types')

    by_state = {name: node[1] for name, node in palette}
    if any(name.startswith('hogwartsmagic:wizard_cauldron') for name in by_state):
        print('mini_hogwarts.schem already has the Potions classroom; nothing to do')
        return

    cells = decode_varints(data, width * height * length)
    air = by_state['minecraft:air']

    def index(x, y, z):
        return x + z * width + y * width * length

    def state_id(state):
        if state not in by_state:
            new_id = max(by_state.values()) + 1
            by_state[state] = new_id
            palette.append((state, (INT, new_id)))
        return by_state[state]

    # The floor: every air cell at FLOOR_Y that the tower walls enclose.
    seen = {SEED}
    stack = [SEED]
    if cells[index(SEED[0], FLOOR_Y, SEED[1])] != air:
        sys.exit('the seed cell is not air: wrong schematic?')
    while stack:
        x, z = stack.pop()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if (nx, nz) in seen:
                continue
            if not (0 <= nx < width and 0 <= nz < length):
                sys.exit('the floor leaks out of the schematic: wrong schematic?')
            if cells[index(nx, FLOOR_Y, nz)] == air:
                seen.add((nx, nz))
                stack.append((nx, nz))
    box = (min(x for x, _ in seen), max(x for x, _ in seen), min(z for _, z in seen), max(z for _, z in seen))
    if len(seen) != EXPECTED_FLOOR_CELLS or box != EXPECTED_BOX:
        sys.exit('enclosed floor is %d cells in %s, expected %d in %s: wrong schematic?'
                 % (len(seen), box, EXPECTED_FLOOR_CELLS, EXPECTED_BOX))

    items = furniture()
    heated = set(CAULDRONS)
    for x, y, z, state in items:
        if cells[index(x, y, z)] != air:
            sys.exit('(%d, %d, %d) is not air; refusing to overwrite it' % (x, y, z))
        if y == ROOM_Y and (x, z) not in seen:
            sys.exit('(%d, %d, %d) does not stand on the new floor' % (x, y, z))
    for x, z in heated:
        if (x, z) not in seen:
            sys.exit('cauldron (%d, %d) is off the floor' % (x, z))

    floor_id = state_id(FLOOR)
    heat_id = state_id(HEAT)
    for x, z in seen:
        cells[index(x, FLOOR_Y, z)] = heat_id if (x, z) in heated else floor_id
    for x, y, z, state in items:
        cells[index(x, y, z)] = state_id(state)

    set_child(blocks, 'Data', (BYTE_ARRAY, encode_varints(cells)))

    print('floor: %d cells (%d magma), furniture: %d blocks, palette now %d entries'
          % (len(seen), len(heated), len(items), len(by_state)))
    if args.dry_run:
        print('dry run: nothing written')
        return
    buf = io.BytesIO()
    with gzip.GzipFile(filename='', mode='wb', fileobj=buf, mtime=0) as fh:
        fh.write(encode_root(root_name, root))
    with open(args.schem, 'wb') as out:
        out.write(buf.getvalue())
    print('wrote %s (%d bytes)' % (args.schem, os.path.getsize(args.schem)))


if __name__ == '__main__':
    main()
