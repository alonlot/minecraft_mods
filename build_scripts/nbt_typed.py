"""Typed NBT read/write: every value keeps its tag type, so a file read and written back without
changes comes out byte-for-byte equal (after gzip). The readers in schem_to_nbt.py and
generate_biome_structures.py drop the tag types, which is fine for looking at a file but not for
editing one: a Short written back as an Int breaks a Sponge schematic's Width/Height/Length.

A value is a (tag_id, payload) pair. Compounds are dicts of name -> (tag_id, payload); lists are
(LIST, (element_tag_id, [payload, ...])).
"""
import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)
_FMT = {BYTE: '>b', SHORT: '>h', INT: '>i', LONG: '>q', FLOAT: '>f', DOUBLE: '>d'}


class _Reader:
    def __init__(self, data):
        self.b = data
        self.i = 0

    def raw(self, n):
        out = self.b[self.i:self.i + n]
        self.i += n
        return out

    def num(self, fmt):
        return struct.unpack(fmt, self.raw(struct.calcsize(fmt)))[0]

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
            return (elem, [self.payload(elem) for _ in range(self.num('>i'))])
        if tag == COMPOUND:
            out = {}
            while True:
                child = self.num('>b')
                if child == END:
                    return out
                name = self.string()
                out[name] = (child, self.payload(child))
        if tag == INT_ARRAY:
            return [self.num('>i') for _ in range(self.num('>i'))]
        if tag == LONG_ARRAY:
            return [self.num('>q') for _ in range(self.num('>i'))]
        raise ValueError('unknown tag id %d' % tag)


def _write(out, tag, value):
    if tag in _FMT:
        out.write(struct.pack(_FMT[tag], value))
    elif tag == BYTE_ARRAY:
        out.write(struct.pack('>i', len(value)))
        out.write(bytes(value))
    elif tag == STRING:
        data = value.encode('utf-8')
        out.write(struct.pack('>H', len(data)))
        out.write(data)
    elif tag == LIST:
        elem, items = value
        out.write(struct.pack('>bi', elem if items else (elem or END), len(items)))
        for item in items:
            _write(out, elem, item)
    elif tag == COMPOUND:
        for name, (child, payload) in value.items():
            data = name.encode('utf-8')
            out.write(struct.pack('>bH', child, len(data)))
            out.write(data)
            _write(out, child, payload)
        out.write(b'\x00')
    elif tag == INT_ARRAY:
        out.write(struct.pack('>i', len(value)))
        for v in value:
            out.write(struct.pack('>i', v))
    elif tag == LONG_ARRAY:
        out.write(struct.pack('>i', len(value)))
        for v in value:
            out.write(struct.pack('>q', v))
    else:
        raise ValueError('unknown tag id %d' % tag)


def read(path):
    """-> (root_name, root_compound_dict)"""
    with open(path, 'rb') as fh:
        data = fh.read()
    if data[:2] == b'\x1f\x8b':
        data = gzip.decompress(data)
    r = _Reader(data)
    if r.num('>b') != COMPOUND:
        raise ValueError('%s is not an NBT compound' % path)
    name = r.string()
    return name, r.payload(COMPOUND)


def write(path, name, root):
    """mtime=0 so writing the same content twice gives the same bytes."""
    buf = io.BytesIO()
    buf.write(struct.pack('>b', COMPOUND))
    data = name.encode('utf-8')
    buf.write(struct.pack('>H', len(data)))
    buf.write(data)
    _write(buf, COMPOUND, root)
    with open(path, 'wb') as raw:
        with gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0) as fh:
            fh.write(buf.getvalue())


def varints(data, count):
    """Decode `count` unsigned LEB128 varints (Sponge schematic block data)."""
    out, cursor = [], 0
    while len(out) < count and cursor < len(data):
        value, shift = 0, 0
        while True:
            byte = data[cursor]
            cursor += 1
            value |= (byte & 0x7F) << shift
            if not byte & 0x80:
                break
            shift += 7
        out.append(value)
    return out


def encode_varints(values):
    out = bytearray()
    for v in values:
        while True:
            byte = v & 0x7F
            v >>= 7
            if v:
                out.append(byte | 0x80)
            else:
                out.append(byte)
                break
    return bytes(out)
