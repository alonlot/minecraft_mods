"""Lifts the Gringotts build out of the downloaded 1.12 world and writes it as a
Sponge v2 schematic the mod can load.

The source world is a 1.12 superflat containing one large build. We take the
bounding box of everything that is not the flat terrain layer, translate the old
numeric block IDs to modern blockstate strings, and emit
data/hogwartsmagic/schematic/gringotts.schem in the same format the Mini
Hogwarts loader already understands.

Run from the repo root:  python build_scripts/extract_gringotts.py
"""

import gzip
import os
import struct
import sys

import anvil112
from mc112_blocks import to_modern

WORLD_REGION = r"C:\Users\alonl\Downloads\Gringotts\Gringotts\region"

# Bounding box of the build, measured by scanning for non-terrain blocks.
X0, X1 = 1436, 1535
Y0, Y1 = 4, 47
Z0, Z1 = 89, 122

# Superflat layers: grass, dirt, bedrock. Dropped so the build sits on whatever
# terrain it is later placed into.
TERRAIN_IDS = {2, 3, 7}

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "data", "hogwartsmagic", "schematic", "gringotts.schem",
)

# 1.21's data version, so the file is not treated as needing an upgrade.
DATA_VERSION = 3953


# --- minimal big-endian NBT writer -------------------------------------------

def _b(tag_id, name, payload):
    encoded = name.encode("utf-8")
    return struct.pack(">BH", tag_id, len(encoded)) + encoded + payload


def tag_int(name, value):
    return _b(3, name, struct.pack(">i", value))


def tag_short(name, value):
    return _b(2, name, struct.pack(">h", value))


def tag_byte_array(name, data):
    return _b(7, name, struct.pack(">i", len(data)) + bytes(data))


def tag_string(name, value):
    encoded = value.encode("utf-8")
    return _b(8, name, struct.pack(">H", len(encoded)) + encoded)


def tag_compound(name, body):
    return _b(10, name, body + b"\x00")


def varint(value):
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


def main():
    width = X1 - X0 + 1
    height = Y1 - Y0 + 1
    length = Z1 - Z0 + 1
    print("extracting %d x %d x %d" % (width, height, length))

    # Palette id 0 must be air: every cell we never write stays air.
    palette = {"minecraft:air": 0}
    cells = [0] * (width * height * length)
    unmapped = {}
    kept = 0

    for name in sorted(os.listdir(WORLD_REGION)):
        if not name.endswith(".mca"):
            continue
        for cx, cz, level in anvil112.read_region(os.path.join(WORLD_REGION, name)):
            if not (X0 - 16 <= cx * 16 <= X1 + 16 and Z0 - 16 <= cz * 16 <= Z1 + 16):
                continue
            for x, y, z, bid, meta in anvil112.iter_blocks(level):
                if bid in TERRAIN_IDS:
                    continue
                if not (X0 <= x <= X1 and Y0 <= y <= Y1 and Z0 <= z <= Z1):
                    continue
                try:
                    state = to_modern(bid, meta)
                except KeyError:
                    unmapped[(bid, meta)] = unmapped.get((bid, meta), 0) + 1
                    continue
                pid = palette.get(state)
                if pid is None:
                    pid = len(palette)
                    palette[state] = pid
                index = (x - X0) + (z - Z0) * width + (y - Y0) * width * length
                cells[index] = pid
                kept += 1

    if unmapped:
        print("ERROR: unmapped blocks encountered:", file=sys.stderr)
        for key in sorted(unmapped):
            print("   id=%d meta=%d  x%d" % (key[0], key[1], unmapped[key]), file=sys.stderr)
        return 1

    print("kept %d blocks, palette size %d" % (kept, len(palette)))

    block_data = bytearray()
    for pid in cells:
        block_data += varint(pid)

    palette_body = b"".join(tag_int(state, pid) for state, pid in palette.items())
    body = (
        tag_int("Version", 2)
        + tag_int("DataVersion", DATA_VERSION)
        + tag_short("Width", width)
        + tag_short("Height", height)
        + tag_short("Length", length)
        + tag_int("PaletteMax", len(palette))
        + tag_compound("Palette", palette_body)
        + tag_byte_array("BlockData", block_data)
    )
    root = tag_compound("Schematic", body)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as fh:
        fh.write(root)
    print("wrote", OUT, os.path.getsize(OUT), "bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
