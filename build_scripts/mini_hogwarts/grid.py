"""The Mini Hogwarts generator's block grid, schematic reader/writer and rotation helpers.

WHY THIS EXISTS
---------------
The new Mini Hogwarts schematic is composed in Python: the old castle shell, a Great Hall
wing, and real rooms are stamped into one sparse grid and then written out as the single
Sponge schematic the mod loads. Everything that stamps blocks shares this module:

  * Grid          a sparse {(x, y, z): block-state string} with the generator's anchors
  * load_schem    read a Sponge v2/v3 .schem into a Grid (reusing schem_to_nbt's decoder)
  * write_schem   write a Sponge **v3** .schem that MiniHogwartsSchematic.java reads
  * rotate_state  turn one block-state string clockwise (seen from above) by quarter turns
  * paste         stamp a sparse piece into a Grid, rotated, at an origin

Coordinates are Minecraft's: +x east, +y up, +z south. Air is never stored, so a Grid of a
whole castle is a few hundred thousand entries rather than a million and a half.

Run any script that imports this with `python -I` (isolated mode puts nothing on sys.path,
which is why the path bootstrap below exists).
"""

import gzip
import io
import os
import struct
import sys

_BUILD_SCRIPTS = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _BUILD_SCRIPTS not in sys.path:
    sys.path.insert(0, _BUILD_SCRIPTS)

import schem_to_nbt as _nbt   # noqa: E402  (read_nbt, decode_schem, split_state live there)

AIR = 'minecraft:air'
_AIR_IDS = ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air')

DATA_VERSION = 3953           # Minecraft 1.21, the same default schem_to_nbt writes


def is_air(state):
    return state.split('[', 1)[0] in _AIR_IDS


class Grid:
    """A sparse block grid. `cells` holds only non-air block-state strings."""

    def __init__(self):
        self.cells = {}
        self.anchors = {}      # name -> anything the later stages want to remember (spawn points, doors...)

    def set(self, x, y, z, state):
        """Place a block; placing air clears the cell, so air is never stored."""
        if is_air(state):
            self.cells.pop((x, y, z), None)
        else:
            self.cells[(x, y, z)] = state

    def get(self, x, y, z):
        return self.cells.get((x, y, z), AIR)

    def clear(self, x, y, z):
        self.cells.pop((x, y, z), None)

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        """Fill the inclusive box between two corners (given in any order)."""
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for x in range(min(x0, x1), max(x0, x1) + 1):
                    self.set(x, y, z, state)

    def bounds(self):
        """(minx, miny, minz, maxx, maxy, maxz) of the stored blocks; all zeros when empty."""
        if not self.cells:
            return (0, 0, 0, 0, 0, 0)
        xs = [p[0] for p in self.cells]
        ys = [p[1] for p in self.cells]
        zs = [p[2] for p in self.cells]
        return (min(xs), min(ys), min(zs), max(xs), max(ys), max(zs))


# --- reading -------------------------------------------------------------------------

def load_schem(path):
    """Read a Sponge v2 or v3 schematic into a Grid (block entities are not carried)."""
    w, h, l, by_id, cells = _nbt.decode_schem(_nbt.read_nbt(path))
    grid = Grid()
    out = grid.cells
    solid = {i: s for i, s in by_id.items() if not is_air(s)}
    for index, value in enumerate(cells):
        state = solid.get(value)
        if state is None:
            continue
        y, rest = divmod(index, w * l)
        z, x = divmod(rest, w)
        out[(x, y, z)] = state
    return grid


# --- writing -------------------------------------------------------------------------

def _varints(values):
    out = bytearray()
    for v in values:
        while v > 0x7F:
            out.append((v & 0x7F) | 0x80)
            v >>= 7
        out.append(v)
    return bytes(out)


# Minimal typed NBT writer. schem_to_nbt's writer has no short / byte-array / int-array tags,
# and the Java loader reads Width/Height/Length with getShort, so the tag types matter.
def _name(name):
    raw = name.encode('utf-8')
    return struct.pack('>H', len(raw)) + raw


def _compound(children):
    """children: [(tag_id, name, payload bytes)] -> compound payload."""
    return b''.join(struct.pack('>b', t) + _name(n) + p for t, n, p in children) + b'\x00'


_T_BYTE_ARRAY, _T_INT, _T_SHORT, _T_COMPOUND, _T_INT_ARRAY = 7, 3, 2, 10, 11


def write_schem(grid, path):
    """Write the grid as a Sponge v3 schematic.

    Size is bounds max + 1 on each axis (the origin stays at 0,0,0, so any blocks below the
    grid's minimum are simply air). Palette index 0 is air; the rest are sorted so the same
    grid always produces the same bytes. Index order is x + z*W + y*W*L, matching
    MiniHogwartsSchematic.java. No block entities.
    """
    _, _, _, mx, my, mz = grid.bounds()
    w, h, l = mx + 1, my + 1, mz + 1
    if max(w, h, l) > 0xFFFF:
        raise ValueError('schematic too large for Sponge shorts: %dx%dx%d' % (w, h, l))
    states = sorted(set(grid.cells.values()))
    palette = {AIR: 0}
    for s in states:
        palette[s] = len(palette)
    flat = [0] * (w * h * l)
    for (x, y, z), s in grid.cells.items():
        flat[x + z * w + y * w * l] = palette[s]
    data = _varints(flat)

    pal_payload = _compound([(_T_INT, s, struct.pack('>i', i)) for s, i in palette.items()])
    blocks = _compound([
        (_T_COMPOUND, 'Palette', pal_payload),
        (_T_BYTE_ARRAY, 'Data', struct.pack('>i', len(data)) + data),
    ])
    schematic = _compound([
        (_T_INT, 'Version', struct.pack('>i', 3)),
        (_T_INT, 'DataVersion', struct.pack('>i', DATA_VERSION)),
        (_T_SHORT, 'Width', struct.pack('>h', w)),
        (_T_SHORT, 'Height', struct.pack('>h', h)),
        (_T_SHORT, 'Length', struct.pack('>h', l)),
        (_T_INT_ARRAY, 'Offset', struct.pack('>i', 3) + struct.pack('>3i', 0, 0, 0)),
        (_T_COMPOUND, 'Blocks', blocks),
    ])
    buf = io.BytesIO()
    buf.write(struct.pack('>b', _T_COMPOUND) + _name(''))
    buf.write(_compound([(_T_COMPOUND, 'Schematic', schematic)]))
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, 'wb') as raw:
        with gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as fh:   # mtime=0: stable bytes
            fh.write(buf.getvalue())


# --- rotation ------------------------------------------------------------------------

_CLOCKWISE = {'north': 'east', 'east': 'south', 'south': 'west', 'west': 'north'}
_SIDE_KEYS = ('north', 'east', 'south', 'west')


def _turn(direction, turns):
    for _ in range(turns):
        direction = _CLOCKWISE.get(direction, direction)
    return direction


def rotate_state(state, quarter_turns):
    """Turn a block-state string clockwise (seen from above) by quarter_turns * 90 degrees.

    Rotated: `facing` (horizontal values only), `axis` (x and z swap on odd turns), `rotation`
    (0..15, +4 per turn) and the north/east/south/west connection keys (the value at `east`
    moves to `south`, and so on). Left alone: stair `shape` (a rotation keeps handedness),
    `hinge`, `half`, `waterlogged` and everything else. Key order in the string is preserved.
    """
    turns = quarter_turns % 4
    if turns == 0 or '[' not in state:
        return state
    name, props = _nbt.split_state(state)
    out = dict(props)
    if 'facing' in props:
        out['facing'] = _turn(props['facing'], turns)
    if 'axis' in props and turns % 2 == 1:
        out['axis'] = {'x': 'z', 'z': 'x'}.get(props['axis'], props['axis'])
    if 'rotation' in props:
        out['rotation'] = str((int(props['rotation']) + 4 * turns) % 16)
    sides = {k: props[k] for k in _SIDE_KEYS if k in props}
    for key in sides:
        del out[key]
    for key, value in sides.items():
        out[_turn(key, turns)] = value
    order = [k for k in props if k in out] + [k for k in out if k not in props]
    return '%s[%s]' % (name, ','.join('%s=%s' % (k, out[k]) for k in order))


def rotated_size(size, quarter_turns):
    sx, sy, sz = size
    return (sz, sy, sx) if quarter_turns % 2 else (sx, sy, sz)


def paste(dst, src_cells, origin, quarter_turns, src_size):
    """Stamp a sparse piece into dst.

    src_cells: {(x, y, z): state} in the piece's own 0-based box of src_size = (sx, sy, sz).
    The piece is turned clockwise (seen from above) by quarter_turns about its own box, then
    its rotated min corner lands on `origin`. Only the piece's blocks are written: cells the
    piece does not list keep whatever dst had (carve the room out first if it must be empty).
    Returns the rotated size, so callers can lay out the next piece.
    """
    turns = quarter_turns % 4
    sx, sy, sz = src_size
    ox, oy, oz = origin
    for (x, y, z), state in src_cells.items():
        if turns == 0:
            nx, nz = x, z
        elif turns == 1:
            nx, nz = sz - 1 - z, x
        elif turns == 2:
            nx, nz = sx - 1 - x, sz - 1 - z
        else:
            nx, nz = z, sx - 1 - x
        dst.set(ox + nx, oy + y, oz + nz, rotate_state(state, turns))
    return rotated_size(src_size, turns)
