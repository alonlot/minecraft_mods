"""Checks a carved undercroft dump against the line it was carved along.

/undercroftdump writes two files: the structure template (corridor_dNNN.nbt) and the line it was
carved along (corridor_dNNN.line.json). The template alone cannot say which side of the cart the
void is on, or whether a vault opens onto rock or onto thin air, because those are questions about
the grid RELATIVE to the line. This reads both and answers them.

    python build_scripts/audit_undercroft_dump.py run/undercroft_dumps/corridor_d122

Reports:
  * open side   - for every sample, is there open air beyond the corridor wall on the left, the
                  right, both or neither, and does that agree with the side the carver opened
  * floating    - solid cells with air on all six sides (a vault shell hanging in the chasm)
  * lights      - how many light sources, of which kind, at which depth
  * footprint   - how far the carve reaches from the station, against the resident footprint
"""
import gzip
import json
import math
import struct
import sys
from collections import Counter


# ---------------------------------------------------------------- a minimal NBT reader

def _read(buf, pos, tag):
    if tag == 1:
        return struct.unpack_from('>b', buf, pos)[0], pos + 1
    if tag == 2:
        return struct.unpack_from('>h', buf, pos)[0], pos + 2
    if tag == 3:
        return struct.unpack_from('>i', buf, pos)[0], pos + 4
    if tag == 4:
        return struct.unpack_from('>q', buf, pos)[0], pos + 8
    if tag == 5:
        return struct.unpack_from('>f', buf, pos)[0], pos + 4
    if tag == 6:
        return struct.unpack_from('>d', buf, pos)[0], pos + 8
    if tag == 7:
        n = struct.unpack_from('>i', buf, pos)[0]
        return buf[pos + 4:pos + 4 + n], pos + 4 + n
    if tag == 8:
        n = struct.unpack_from('>H', buf, pos)[0]
        return buf[pos + 2:pos + 2 + n].decode('utf-8'), pos + 2 + n
    if tag == 9:
        inner = buf[pos]
        n = struct.unpack_from('>i', buf, pos + 1)[0]
        pos += 5
        out = []
        for _ in range(n):
            v, pos = _read(buf, pos, inner)
            out.append(v)
        return out, pos
    if tag == 10:
        out = {}
        while True:
            t = buf[pos]
            pos += 1
            if t == 0:
                return out, pos
            n = struct.unpack_from('>H', buf, pos)[0]
            name = buf[pos + 2:pos + 2 + n].decode('utf-8')
            pos += 2 + n
            out[name], pos = _read(buf, pos, t)
    if tag == 11:
        n = struct.unpack_from('>i', buf, pos)[0]
        return list(struct.unpack_from('>%di' % n, buf, pos + 4)), pos + 4 + 4 * n
    if tag == 12:
        n = struct.unpack_from('>i', buf, pos)[0]
        return list(struct.unpack_from('>%dq' % n, buf, pos + 4)), pos + 4 + 8 * n
    raise ValueError('tag %d' % tag)


def read_nbt(path):
    buf = gzip.open(path, 'rb').read()
    assert buf[0] == 10
    n = struct.unpack_from('>H', buf, 1)[0]
    root, _ = _read(buf, 3 + n, 10)
    return root


# ---------------------------------------------------------------- the checks

LIGHTS = {'minecraft:lantern', 'minecraft:soul_lantern', 'minecraft:torch', 'minecraft:wall_torch',
          'minecraft:soul_torch', 'minecraft:soul_wall_torch', 'minecraft:shroomlight',
          'minecraft:glowstone', 'minecraft:lava', 'minecraft:magma_block', 'minecraft:sea_lantern',
          'minecraft:campfire', 'minecraft:soul_campfire', 'minecraft:redstone_torch',
          'minecraft:redstone_wall_torch', 'minecraft:ochre_froglight', 'minecraft:pearlescent_froglight',
          'minecraft:verdant_froglight', 'minecraft:jack_o_lantern', 'minecraft:end_rod',
          'minecraft:candle', 'minecraft:crying_obsidian', 'hogwartsmagic:thiefs_downfall'}
AIRS = {'minecraft:air', 'minecraft:cave_air', 'minecraft:water'}


def main(stem):
    root = read_nbt(stem + '.nbt')
    line = json.load(open(stem + '.line.json'))
    mx, my, mz = line['min']
    palette = [p['Name'] for p in root['palette']]
    grid = {}
    for b in root['blocks']:
        x, y, z = b['pos']
        grid[(x + mx, y + my, z + mz)] = palette[b['state']]

    def name(x, y, z):
        return grid.get((x, y, z))

    def open_air(x, y, z):
        # Uncarved counts as rock: the world has terrain there.
        return name(x, y, z) in AIRS

    samples = line['samples']
    agree = disagree = neither = both = 0
    by_profile = Counter()
    for (s, px, py, pz, ux, uy, uz, rx, ry, rz, bank, profile) in samples[::2]:
        opened = 1 if bank >= 0 else -1          # what CorridorCarver treats as the void side
        seen = {}
        for side in (-1, 1):
            hits = 0
            for reach in range(6, 15):
                for v in (0, 2):
                    x = math.floor(px + rx * side * reach + ux * v)
                    y = math.floor(py + ry * side * reach + uy * v)
                    z = math.floor(pz + rz * side * reach + uz * v)
                    if open_air(x, y, z):
                        hits += 1
            seen[side] = hits >= 6
        if seen[-1] and seen[1]:
            both += 1
        elif not seen[-1] and not seen[1]:
            neither += 1
        elif seen[opened]:
            agree += 1
        else:
            disagree += 1
            by_profile[profile] += 1
    total = agree + disagree + both + neither
    print('open air beyond the corridor (every 2 blocks of %d):' % int(line['length']))
    print('  on the side the carver opened  %4d  (%.0f%%)' % (agree, 100.0 * agree / total))
    print('  on the OTHER side              %4d  (%.0f%%)' % (disagree, 100.0 * disagree / total))
    print('  both sides                     %4d' % both)
    print('  neither (enclosed in rock)     %4d  (%.0f%%)' % (neither, 100.0 * neither / total))

    floating = 0
    for (x, y, z), n in grid.items():
        if n in AIRS or n is None:
            continue
        if all(open_air(x + dx, y + dy, z + dz) for dx, dy, dz in
               ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            floating += 1
    print('solid cells with air on all six sides: %d' % floating)

    lights = Counter(n for n in grid.values() if n in LIGHTS)
    print('light sources: %s' % dict(lights))

    # Vaults: is the mouth open to the line, is the chamber sealed in rock rather than hung in air.
    for v in line.get('vaults', []):
        dx, dz = v['door'][0], v['door'][2]
        ox_, oz_ = v['out']
        floor_y = math.floor(v['door'][1]) - 1
        # The mouth: every cell whose centre is in the slab one block in front of the door and
        # within the middle three across it - the same bucketing the carver uses.
        mouth_air = mouth_cells = 0
        for x in range(math.floor(dx) - 6, math.floor(dx) + 7):
            for z in range(math.floor(dz) - 6, math.floor(dz) + 7):
                rx, rz = x + 0.5 - dx, z + 0.5 - dz
                d = rx * ox_ + rz * oz_
                u = rx * -oz_ + rz * ox_
                if -1.5 <= d < -0.5 and abs(u) <= 1.5:
                    for h in (1, 2, 3):
                        mouth_cells += 1
                        if open_air(x, floor_y + h, z) or (name(x, floor_y + h, z) or '').endswith('wall_sign'):
                            mouth_air += 1
        cx, cy, cz = v['chamber']
        wall_rock = 0
        probes = 0
        for ang in range(0, 360, 30):
            # Not toward the line: that way is the throat, which is meant to be open.
            if math.cos(math.radians(ang)) * -ox_ + math.sin(math.radians(ang)) * -oz_ > 0.6:
                continue
            for r in (6.5, 7.5):
                x = math.floor(cx + r * math.cos(math.radians(ang)))
                z = math.floor(cz + r * math.sin(math.radians(ang)))
                for h in (2, 5):
                    probes += 1
                    if not open_air(x, math.floor(cy) + h, z):
                        wall_rock += 1
        print('vault %3d: mouth %d/%d open, walls %d/%d rock%s' % (
            v['number'], mouth_air, mouth_cells, wall_rock, probes,
            '' if mouth_air == mouth_cells and wall_rock >= probes - 2 else '   <-- CHECK'))
    if 'anchor' in line:
        ax, ay, az = line['anchor']
        hall = sum(1 for (x, y, z), n in grid.items()
                   if n in AIRS and abs(x - ax) < 24 and abs(z - az) < 24 and ay - 3 <= y <= ay + 14)
        stubs = sum(1 for (x, y, z) in line['stubs'] if name(x, y, z) == 'minecraft:lightning_rod')
        under = name(math.floor(ax), math.floor(ay) - 1, math.floor(az))
        headroom = sum(1 for h in range(0, 6) if open_air(math.floor(ax), math.floor(ay) + h, math.floor(az)))
        print('dragon hall: %d open cells near the anchor, %d/%d stubs standing, floor under anchor %s, '
              'headroom %d/6' % (hall, stubs, len(line['stubs']), under, headroom))

    ox, oy, oz = line['origin']
    first = samples[0]
    st = (first[1], first[3])
    reach = max(max(abs(x - st[0]), abs(z - st[1])) for (x, y, z) in grid)
    print('furthest carved cell from the station: %d blocks (resident footprint is 48)' % reach)


if __name__ == '__main__':
    main(sys.argv[1].removesuffix('.nbt'))
