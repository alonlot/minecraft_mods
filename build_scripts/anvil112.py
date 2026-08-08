"""Minimal reader for Minecraft 1.12-era Anvil region files.

1.12 predates the "flattening", so chunk sections store a byte array of numeric
block IDs plus a nibble array of metadata (and optionally an `Add` nibble array
for IDs above 255) rather than a per-section palette. Nothing in the modern
tooling reads that any more, hence this.

Used by extract_gringotts.py to lift a build out of a downloaded 1.12 world.
"""

import struct
import zlib
import gzip
import io
import os

import nbtlib

SECTOR = 4096


def _nibble(arr, index):
    """Read the index-th 4-bit value out of a packed nibble array."""
    byte = arr[index >> 1]
    return (byte >> 4) if (index & 1) else (byte & 0x0F)


def read_region(path):
    """Yields (chunk_x, chunk_z, level_tag) for every chunk present."""
    with open(path, "rb") as fh:
        header = fh.read(SECTOR)
        if len(header) < SECTOR:
            return
        for i in range(1024):
            offset, = struct.unpack_from(">I", header, i * 4)
            sector_offset = offset >> 8
            sector_count = offset & 0xFF
            if sector_offset == 0 or sector_count == 0:
                continue
            fh.seek(sector_offset * SECTOR)
            raw = fh.read(sector_count * SECTOR)
            if len(raw) < 5:
                continue
            length, = struct.unpack_from(">I", raw, 0)
            compression = raw[4]
            payload = raw[5:4 + length]
            try:
                if compression == 1:
                    data = gzip.decompress(payload)
                elif compression == 2:
                    data = zlib.decompress(payload)
                else:
                    continue
            except Exception:
                continue
            tag = nbtlib.File.parse(io.BytesIO(data))
            root = tag
            # nbtlib wraps the unnamed root; unwrap a single-key root if present.
            if "Level" not in root and len(root) == 1:
                root = next(iter(root.values()))
            level = root.get("Level")
            if level is None:
                continue
            yield int(level["xPos"]), int(level["zPos"]), level


def iter_blocks(level):
    """Yields (x, y, z, block_id, meta) in world coordinates for one chunk.

    Air (id 0) is skipped - callers only ever want the build.
    """
    cx = int(level["xPos"]) * 16
    cz = int(level["zPos"]) * 16
    sections = level.get("Sections")
    if sections is None:
        return
    for section in sections:
        base_y = int(section["Y"]) * 16
        blocks = bytes(bytearray((b & 0xFF) for b in section["Blocks"]))
        data = bytes(bytearray((b & 0xFF) for b in section["Data"]))
        add = section.get("Add")
        add = bytes(bytearray((b & 0xFF) for b in add)) if add is not None else None
        for index in range(4096):
            block_id = blocks[index]
            if add is not None:
                block_id |= _nibble(add, index) << 8
            if block_id == 0:
                continue
            y = index >> 8
            z = (index >> 4) & 15
            x = index & 15
            yield cx + x, base_y + y, cz + z, block_id, _nibble(data, index)


def scan_world(region_dir):
    """Returns (bounds, id_counts) across every region file."""
    min_x = min_y = min_z = 1 << 30
    max_x = max_y = max_z = -(1 << 30)
    counts = {}
    for name in sorted(os.listdir(region_dir)):
        if not name.endswith(".mca"):
            continue
        for _cx, _cz, level in read_region(os.path.join(region_dir, name)):
            for x, y, z, bid, meta in iter_blocks(level):
                min_x = min(min_x, x); max_x = max(max_x, x)
                min_y = min(min_y, y); max_y = max(max_y, y)
                min_z = min(min_z, z); max_z = max(max_z, z)
                counts[(bid, meta)] = counts.get((bid, meta), 0) + 1
    bounds = (min_x, min_y, min_z, max_x, max_y, max_z)
    return bounds, counts
