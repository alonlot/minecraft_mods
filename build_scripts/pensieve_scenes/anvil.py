"""A small stdlib reader for Minecraft Java Anvil region files (1.18+ chunk format), for cutting sets out of a world.

    from anvil import read_region_box
    cells = read_region_box(world_dir, x0, y0, z0, x1, y1, z1)    # {(x, y, z): 'minecraft:block[k=v,...]'}

Only blocks are read (no block entities, no entities, no biomes). Air of every kind is left out of the result.
Coordinates are world coordinates and the box is inclusive at both ends. Block states use the same sorted
'name[k=v,...]' form as the set scripts.

The chunk format is the post-1.18 one: Level-less root, 'sections' each with 'Y' and 'block_states' {palette, data}.
data is a long array packed 1.16+ style: bits = max(4, ceil(log2(len(palette)))), 64 // bits values per long, no
value spans two longs, index = (y * 16 + z) * 16 + x inside the 16^3 section. A one-entry palette has no data.

The world is untrusted data: this only parses it (zlib + NBT) and runs nothing from it.
"""
import os
import struct
import zlib

from scene_common import read_nbt

AIR = {'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'}


def _state(entry):
    name = entry['Name']
    props = entry.get('Properties')
    if not props:
        return name
    return name + '[' + ','.join('%s=%s' % (k, str(v).lower()) for k, v in sorted(props.items())) + ']'


def read_chunk(region_path, cx, cz):
    """The parsed NBT root of chunk (cx, cz) (world chunk coordinates) from its region file, or None if absent."""
    with open(region_path, 'rb') as f:
        header = f.read(4096)
        i = 4 * ((cx & 31) + (cz & 31) * 32)
        loc = struct.unpack('>I', header[i:i + 4])[0]
        offset, count = loc >> 8, loc & 0xFF
        if offset == 0 or count == 0:
            return None
        f.seek(offset * 4096)
        length, kind = struct.unpack('>IB', f.read(5))
        raw = f.read(length - 1)
    if kind & 0x80:
        raise ValueError('chunk %d,%d is stored in an external .mcc file' % (cx, cz))
    if kind == 2:
        data = zlib.decompress(raw)
    elif kind == 1:
        import gzip
        data = gzip.decompress(raw)
    elif kind == 3:
        data = raw
    else:
        raise ValueError('chunk %d,%d: unknown compression %d' % (cx, cz, kind))
    return read_nbt(data)


def section_states(section):
    """[16^3 state strings or None for air] for one section, in (y * 16 + z) * 16 + x order; None if all air."""
    bs = section.get('block_states')
    if not bs:
        return None
    palette = [_state(p) for p in bs['palette']]
    if len(palette) == 1:
        if palette[0] in AIR:
            return None
        return [palette[0]] * 4096
    bits = max(4, (len(palette) - 1).bit_length())
    per = 64 // bits
    mask = (1 << bits) - 1
    out = []
    pal = [None if s in AIR else s for s in palette]
    for word in bs['data']:
        word &= 0xFFFFFFFFFFFFFFFF
        for _ in range(per):
            out.append(pal[word & mask])
            word >>= bits
            if len(out) == 4096:
                break
        if len(out) == 4096:
            break
    return out


def read_region_box(world_dir, x0, y0, z0, x1, y1, z1):
    """{(x, y, z): state} for every non-air block in the inclusive world box, read from world_dir/region/*.mca."""
    x0, x1 = min(x0, x1), max(x0, x1)
    y0, y1 = min(y0, y1), max(y0, y1)
    z0, z1 = min(z0, z1), max(z0, z1)
    out = {}
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            path = os.path.join(world_dir, 'region', 'r.%d.%d.mca' % (cx >> 5, cz >> 5))
            if not os.path.exists(path):
                continue
            root = read_chunk(path, cx, cz)
            if root is None:
                continue
            for sec in root.get('sections', []):
                sy = sec['Y']
                if sy * 16 + 15 < y0 or sy * 16 > y1:
                    continue
                states = section_states(sec)
                if states is None:
                    continue
                bx, by, bz = cx * 16, sy * 16, cz * 16
                lx0, lx1 = max(x0 - bx, 0), min(x1 - bx, 15)
                lz0, lz1 = max(z0 - bz, 0), min(z1 - bz, 15)
                ly0, ly1 = max(y0 - by, 0), min(y1 - by, 15)
                for ly in range(ly0, ly1 + 1):
                    for lz in range(lz0, lz1 + 1):
                        base = (ly * 16 + lz) * 16
                        for lx in range(lx0, lx1 + 1):
                            s = states[base + lx]
                            if s is not None:
                                out[(bx + lx, by + ly, bz + lz)] = s
    return out
