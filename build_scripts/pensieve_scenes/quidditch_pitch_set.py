"""Builds the Quidditch pitch memory set: structure/quidditch_pitch.nbt cut out of the user-supplied world save, the
Mini Hogwarts beside it as a schematic entry, and the scene anchors the Flying memory's match is staged with.

    python -I build_scripts/pensieve_scenes/quidditch_pitch_set.py          # writes the .nbt, runs every assert

Input: source/quidditch_pitch_v2/ (the "Quidditch Pitch V2" Java world, DataVersion 3955; never committed, licence
unknown, the user's private use only). It is read as data by anvil.py. The built pitch is world x -395..-210,
y -43..33, z -989..-702; the field's top block layer is world y -43.

The scene frame: scene = world + OFFSET. The pitch is centred on the origin (the hoops' planes at z -108 and +108,
the centre hoops' poles on x 0); the field's top block layer is scene y -1, so actors on the grass stand at y 0.
The long axis is z. The castle side is +x.

What the set adds to the pitch as built:
  1. Its own ground: one grass layer over the whole radius with low hills rising toward the edge (so the horizon is
     a ridge, not the end of the world) and an earth skirt round the rim; dirt under the sand of the goal areas.
  2. The hoops recoloured gold (poles and rings), three a side, the rings left open.
  3. House drapes: wall banners on the field face of every stand tower, under its box, and hanging from the
     gallery rim round the inner wall, each in the house colour of the stand it hangs on.
  4. Two team gates: tunnels through the west stand at field level, opening onto the grass outside.
  5. Light: light blocks over the field, along the gallery and in the tower boxes, so actors read in the storm,
     and warm glowstone behind the castle's windows (template cells the schematic leaves air).
  6. Trees on the outskirts (spruce, persistent leaves), clear of the castle and the stadium.
The Mini Hogwarts (SCHEMATIC) stands behind and beside the east long stand on its own hill, turned 180 so its spire
and lit hall face the pitch, its foot layer flush with the ground.
"""
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import (read_nbt, physics_problems, FREE, STRUCTURES, SCHEMATICS, SCHEMATIC_SKIPPED,  # noqa: E402
                          rotate_xz)
from great_hall_set import write_template, state, parse_state, bid  # noqa: E402
from anvil import read_region_box  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
WORLD_DIR = os.path.join(HERE, 'source', 'quidditch_pitch_v2')
OUT = os.path.join(STRUCTURES, 'quidditch_pitch.nbt')

WORLD_BOX = (-395, -43, -989, -210, 33, -702)      # inclusive; the build as surveyed (y -43 is the field layer)
OFFSET = (302, 42, 845)                          # scene = world + OFFSET
RADIUS = 200
PLATEAU = 12                                     # the castle's rise above the field, its banks 1:1
TEMPLATE_AT = (-RADIUS, -4, -RADIUS)
SIZE = (2 * RADIUS + 1, 4 + PLATEAU + 112, 2 * RADIUS + 1)    # down to the rim skirt, up past the castle's windows
GRASS_Y = 0                                      # walking y on the field
BUDGET = 350000                                  # template + schematic blocks

TERRAIN = {'minecraft:grass_block', 'minecraft:dirt', 'minecraft:bedrock', 'minecraft:stone'}
GRASS = 'minecraft:grass_block[snowy=false]'

# the castle: the Mini Hogwarts schematic (96 x 109 x 145), turned 180 so its spire side faces the pitch (-x)
CASTLE_ROT = 180
CASTLE_AT = (104, PLATEAU - 1, -72)              # min corner of the turned footprint; its foot layer is the ground
SCHEMATIC = {'id': 'mini_hogwarts', 'at': list(CASTLE_AT), 'rotation': CASTLE_ROT}

HOUSE_COLOUR = {'gryffindor': 'red', 'hufflepuff': 'yellow', 'ravenclaw': 'blue', 'slytherin': 'green'}
# the one colour each house's checker owns outright (yellow is shared by Gryffindor and Hufflepuff, light grey by
# Ravenclaw and Slytherin)
HOUSE_KEY = {'minecraft:red_concrete': 'gryffindor', 'minecraft:black_concrete': 'hufflepuff',
             'minecraft:blue_concrete': 'ravenclaw', 'minecraft:green_concrete': 'slytherin'}

GATE_Z = (-40, 40)                               # team gate centre lines, through the west stand
GATE_HALF = 2                                    # 5 wide
GATE_H = 5                                       # clear height y 0..4
DIVE = (-30, 46)                                 # dive column x, z
DIVE_R = 3
DIVE_H = 70


def sc(p):
    return (p[0] + OFFSET[0], p[1] + OFFSET[1], p[2] + OFFSET[2])


def _p(x, y, z):
    return [round(x, 4), round(y, 4), round(z, 4)]


def _yaw_of(dx, dz):
    """Minecraft yaw facing direction (dx, dz): 0 = +Z, 90 = -X, -90 = +X, 180 = -Z."""
    return round(math.degrees(math.atan2(-dx, dz)), 2)


def free(s):
    return bid(s) in FREE


# ---------------------------------------------------------------- the castle schematic, with properties
_FACE_180 = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east'}


def turn_state(s, degrees):
    """Block state s turned like Rotation.CLOCKWISE_180 (the only turn this set uses; 0 is the identity)."""
    if degrees == 0 or '[' not in s:
        return s
    assert degrees == 180, degrees
    name, props = parse_state(s)
    out = {}
    for k, v in props.items():
        if k in ('facing', 'horizontal_facing') and v in _FACE_180:
            out[k] = _FACE_180[v]
        elif k in _FACE_180:
            out[_FACE_180[k]] = v
        elif k == 'rotation':
            out[k] = str((int(v) + 8) % 16)
        else:
            out[k] = v
    return state(name, **out)


def schematic_cells():
    """{scene cell: state} for the castle as SetBuilder places it (turned, skipped cells left out)."""
    import gzip
    root = read_nbt(gzip.open(os.path.join(SCHEMATICS, SCHEMATIC['id'] + '.schem')).read())
    s = root.get('Schematic', root)
    w, h, l = s['Width'] & 0xFFFF, s['Height'] & 0xFFFF, s['Length'] & 0xFFFF
    blk = s['Blocks'] if 'Blocks' in s else s
    pal = {v: k for k, v in blk['Palette'].items()}
    data = blk['Data'] if 'Data' in blk else blk['BlockData']
    cache, out, i, idx = {}, {}, 0, 0
    ax, ay, az = SCHEMATIC['at']
    while i < len(data) and idx < w * h * l:
        v = sh = 0
        while True:
            c = data[i] & 0xFF
            i += 1
            v |= (c & 0x7F) << sh
            if not c & 0x80:
                break
            sh += 7
        raw = pal[v]
        if raw.split('[')[0] not in SCHEMATIC_SKIPPED:
            st = cache.get(raw)
            if st is None:
                n, props = parse_state(raw)
                st = cache[raw] = turn_state(state(n, **props), SCHEMATIC['rotation'])
            x, z, y = idx % w, (idx // w) % l, idx // (w * l)
            rx, rz = rotate_xz(x, z, w, l, SCHEMATIC['rotation'])
            out[(ax + rx, ay + y, az + rz)] = st
        idx += 1
    return out, (w, h, l)


# ---------------------------------------------------------------- the pitch, cut out of the world
def load_pitch():
    """{scene cell: state} for every non-terrain block of the pitch."""
    raw = read_region_box(WORLD_DIR, *WORLD_BOX)
    out = {}
    for p, s in raw.items():
        if bid(s) in TERRAIN:
            continue
        out[sc(p)] = s
    return out


def is_hoop_cell(p):
    return abs(p[0]) <= 22 and 98 <= abs(p[2]) <= 118 and p[1] >= 0


def recolour_hoops(w):
    """Every hoop cell (poles and rings, light grey concrete as built) becomes gold. Returns the six hoops."""
    cells = [p for p, s in w.items() if is_hoop_cell(p)]
    assert cells and all(w[p] == 'minecraft:light_gray_concrete' for p in cells), \
        set(w[p] for p in cells)
    for p in cells:
        w[p] = 'minecraft:gold_block'
    hoops = []
    for side in (-1, 1):
        for xc in (-15.0, 0.0, 15.0):
            mine = [p for p in cells if (p[2] > 0) == (side > 0) and abs(p[0] + 0.5 - xc) <= 7]
            # the pole is the 2 x 2 column under the ring: its top is the highest y where only pole cells exist
            ys = sorted(set(p[1] for p in mine))
            pole = lambda y: all(abs(p[0] + 0.5 - xc) <= 1 for p in mine if p[1] == y)  # noqa: E731
            ring = [p for p in mine if not pole(p[1]) or p[1] > max(y for y in ys if pole(y) and
                                                                     any(not pole(yy) for yy in ys if yy > y))]
            # ring centre from its bounding box
            rx0, rx1 = min(p[0] for p in ring), max(p[0] for p in ring) + 1
            ry0, ry1 = min(p[1] for p in ring), max(p[1] for p in ring) + 1
            zs = sorted(set(p[2] for p in ring))
            cx, cy, cz = (rx0 + rx1) / 2.0, (ry0 + ry1) / 2.0, (zs[0] + zs[-1] + 1) / 2.0
            r_in = min(math.hypot(p[0] + 0.5 - cx, p[1] + 0.5 - cy) for p in ring) - 0.5
            r_out = max(math.hypot(p[0] + 0.5 - cx, p[1] + 0.5 - cy) for p in ring) + 0.5
            hoops.append({'centre': _p(cx, cy, cz), 'yaw': 0.0 if side < 0 else 180.0,
                          'radius': round(r_in, 3), 'outer_radius': round(r_out, 3),
                          'plane_z': [zs[0], zs[-1] + 1], 'pole_top': ry0})
    return hoops


# ---------------------------------------------------------------- towers and seats
def find_towers(w):
    """Stand towers: 2D components of cells above the gallery (y > 18), hoops left out."""
    cols = set((p[0], p[2]) for p in w if p[1] > 18 and not is_hoop_cell(p))
    towers, seen = [], set()
    for c in sorted(cols):
        if c in seen:
            continue
        comp, stack = [], [c]
        seen.add(c)
        while stack:
            a = stack.pop()
            comp.append(a)
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)):
                n = (a[0] + d[0], a[1] + d[1])
                if n in cols and n not in seen:
                    seen.add(n)
                    stack.append(n)
        if len(comp) < 20:
            continue                                   # a flag pole
        xs = [a[0] for a in comp]
        zs = [a[1] for a in comp]
        towers.append({'x': (min(xs), max(xs)), 'z': (min(zs), max(zs)),
                       'centre': ((min(xs) + max(xs) + 1) / 2.0, (min(zs) + max(zs) + 1) / 2.0)})
    for t in towers:
        cnt = {}
        for p, s in w.items():
            if t['x'][0] <= p[0] <= t['x'][1] and t['z'][0] <= p[2] <= t['z'][1] and s in HOUSE_KEY:
                cnt[HOUSE_KEY[s]] = cnt.get(HOUSE_KEY[s], 0) + 1
        t['house'] = max(cnt, key=cnt.get)
        t['top'] = max(p[1] for p in w if t['x'][0] <= p[0] <= t['x'][1] and t['z'][0] <= p[2] <= t['z'][1])
    return towers


def in_tower(t, x, z, pad=0):
    return t['x'][0] - pad <= x <= t['x'][1] + pad and t['z'][0] - pad <= z <= t['z'][1] + pad


def is_seat(s):
    if s is None:
        return False
    n, props = parse_state(s)
    if n.endswith('_stairs'):
        return props.get('half') == 'bottom'
    if n.endswith('_slab'):
        return props.get('type') == 'bottom'
    return False


def seat_cells(w):
    """Seat blocks (bottom stairs and slabs) with two passable cells above."""
    return set(p for p, s in w.items() if is_seat(s) and in_stadium(p) and free(w.get((p[0], p[1] + 1, p[2])))
               and free(w.get((p[0], p[1] + 2, p[2]))))


def in_stadium(p):
    """Inside the stadium's footprint (the pitch as built: x -93..92, z -144..143)."""
    return -93 <= p[0] <= 92 and -144 <= p[2] <= 143


def _components(cells):
    out, seen = [], set()
    for c in sorted(cells):
        if c in seen:
            continue
        comp, stack = [], [c]
        seen.add(c)
        while stack:
            a = stack.pop()
            comp.append(a)
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    n = (a[0] + dx, a[1], a[2] + dz)
                    if n in cells and n not in seen:
                        seen.add(n)
                        stack.append(n)
        out.append(comp)
    return out


def _segment_ok(a, b, cells):
    """Whether the straight seat-top line between cell centres a and b stays over seat cells all the way."""
    ax, az, bx, bz = a[0] + 0.5, a[2] + 0.5, b[0] + 0.5, b[2] + 0.5
    n = max(1, int(math.ceil(math.hypot(bx - ax, bz - az) / 0.2)))
    for i in range(n + 1):
        t = i / float(n)
        c = (int(math.floor(ax + (bx - ax) * t)), a[1], int(math.floor(az + (bz - az) * t)))
        if c not in cells:
            return False
    return True


def row_segments(comp, cells):
    """Straight seat-top segments [x1, y1, z1, x2, y2, z2] covering one connected row of seat cells, walking it in
    angle order round the pitch centre and extending each segment while it stays on the row."""
    order = sorted(comp, key=lambda p: math.atan2(p[2] + 0.5, p[0] + 0.5))
    # a row that wraps the -pi/pi cut (the gallery ring) starts at its largest angular gap instead
    angs = [math.atan2(p[2] + 0.5, p[0] + 0.5) for p in order]
    gaps = [(angs[(i + 1) % len(angs)] - angs[i]) % (2 * math.pi) for i in range(len(angs))]
    k = max(range(len(gaps)), key=lambda i: gaps[i])
    order = order[k + 1:] + order[:k + 1]
    segs, i = [], 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and _segment_ok(order[i], order[j + 1], cells):
            j += 1
        a, b = order[i], order[j]
        if j > i:
            y = a[1] + 0.5
            segs.append([a[0] + 0.5, y, a[2] + 0.5, b[0] + 0.5, y, b[2] + 0.5])
        i = j + 1
    return segs


def wall_house(w, x, z):
    """The house whose colour dominates the stand wall round (x, z)."""
    cnt = {}
    for dx in range(-4, 5):
        for dz in range(-4, 5):
            for y in range(0, 19):
                s = w.get((x + dx, y, z + dz))
                if s in HOUSE_KEY:
                    cnt[HOUSE_KEY[s]] = cnt.get(HOUSE_KEY[s], 0) + 1
    return max(cnt, key=cnt.get) if cnt else None


# ---------------------------------------------------------------- additions
def wall_banner(colour, facing):
    return state(colour + '_wall_banner', facing=facing)


FACING_OF = {(1, 0): 'east', (-1, 0): 'west', (0, 1): 'south', (0, -1): 'north'}


def _cardinal(dx, dz):
    if abs(dx) >= abs(dz):
        return (1 if dx > 0 else -1, 0)
    return (0, 1 if dz > 0 else -1)


def hang_tower_drapes(w, towers):
    """House banners on each tower's field face under its box: three columns, three banners stacked in each (a
    banner hangs two blocks, so they read as one long drape)."""
    n = 0
    for t in towers:
        cx, cz = t['centre']
        d = _cardinal(-cx, -cz)
        facing = FACING_OF[d]
        colour = HOUSE_COLOUR[t['house']]
        # the box floor: the lowest plank layer in the top half of the tower
        planks = [p[1] for p in w if in_tower(t, p[0], p[2]) and p[1] > 30 and bid(w[p]) == 'minecraft:spruce_planks']
        box = min(planks) if planks else t['top'] - 12
        if d[0]:
            span = range(t['z'][0], t['z'][1] + 1)
            mid = (t['z'][0] + t['z'][1]) // 2
        else:
            span = range(t['x'][0], t['x'][1] + 1)
            mid = (t['x'][0] + t['x'][1]) // 2
        for off in (-3, 0, 3):
            u = mid + off
            if u not in span:
                continue
            for y in (box - 3, box - 5, box - 7):
                face = None
                if d[0]:
                    xs = [p for p in range(t['x'][0], t['x'][1] + 1) if not free(w.get((p, y, u)))]
                    if xs:
                        fx = max(xs) if d[0] > 0 else min(xs)
                        face = (fx + d[0], y, u)
                else:
                    zs = [p for p in range(t['z'][0], t['z'][1] + 1) if not free(w.get((u, y, p)))]
                    if zs:
                        fz = max(zs) if d[1] > 0 else min(zs)
                        face = (u, y, fz + d[1])
                if face and w.get(face) is None:
                    w[face] = wall_banner(colour, facing)
                    n += 1
    return n


def hang_gallery_drapes(w, step_deg=5.0, y=12):
    """Banners down the inner face of the stand wall, just under the gallery, every step_deg round the pitch,
    each in its sector's house colour. Skipped where the cardinal facing has no wall behind it."""
    n = 0
    a = 0.0
    while a < 360.0:
        th = math.radians(a)
        dx, dz = math.cos(th), math.sin(th)
        hit = None
        for r10 in range(600, 2200):
            r = r10 / 10.0
            c = (int(math.floor(dx * r)), y, int(math.floor(dz * r)))
            if not free(w.get(c)):
                hit = c
                break
        a += step_deg
        if hit is None or is_hoop_cell(hit):
            continue
        d = _cardinal(-dx, -dz)
        cell = (hit[0] + d[0], y, hit[2] + d[1])
        if w.get(cell) is not None or free(w.get((cell[0] - d[0], y, cell[2] - d[1]))):
            continue
        if w.get((cell[0], y - 1, cell[2])) is not None:
            continue
        house = wall_house(w, hit[0], hit[2])
        if house is None:
            continue
        w[cell] = wall_banner(HOUSE_COLOUR[house], FACING_OF[d])
        n += 1
    return n


def cut_team_gates(w):
    """Tunnels through the west stand at field level (5 wide, 5 tall), lined with spruce, floored with path."""
    gates = []
    for zg in GATE_Z:
        # the stand wall along this line: the first solid column at y 2 marching west from the field
        x_in = None
        for x in range(-60, -100, -1):
            if not free(w.get((x, 6, zg))):
                x_in = x
                break
        assert x_in is not None, zg
        # the wall's outer face: the last solid cell on this line at y 2 before open air outside
        xs = [x for x in range(x_in - 12, x_in + 1) if not free(w.get((x, 6, zg)))]
        x_out = min(xs)
        # clear the embankment in front, the wall, and line the tunnel
        for x in range(x_out, x_in + 8):
            for z in range(zg - GATE_HALF, zg + GATE_HALF + 1):
                for y in range(0, GATE_H):
                    if x <= x_in or (not is_hoop_cell((x, y, z)) and bid(w.get((x, y, z))) in (
                            'minecraft:lime_wool', 'minecraft:green_wool', 'minecraft:white_wool')):
                        w.pop((x, y, z), None)
        for x in range(x_out, x_in + 1):
            for y in range(0, GATE_H + 1):
                for z in (zg - GATE_HALF - 1, zg + GATE_HALF + 1):
                    w[(x, y, z)] = 'minecraft:spruce_planks' if y < GATE_H else 'minecraft:stripped_spruce_wood'
            for z in range(zg - GATE_HALF - 1, zg + GATE_HALF + 2):
                w[(x, GATE_H, z)] = state('stripped_spruce_wood', axis='z')
                w[(x, -1, z)] = 'minecraft:dirt_path'
        # posts and lintel on the field face, and a lantern either side
        for y in range(0, GATE_H + 1):
            for z in (zg - GATE_HALF - 1, zg + GATE_HALF + 1):
                w[(x_in + 1, y, z)] = state('stripped_spruce_log', axis='y')
        for z in range(zg - GATE_HALF - 1, zg + GATE_HALF + 2):
            w[(x_in + 1, GATE_H, z)] = state('stripped_spruce_log', axis='z')
        for z in (zg - GATE_HALF - 2, zg + GATE_HALF + 2):
            if w.get((x_in + 1, GATE_H - 2, z)) is None:
                w[(x_in + 1, GATE_H - 2, z)] = state('wall_torch', facing='east')
        for x in range(x_out - 12, x_out):                 # the path out to the grounds
            for z in range(zg - GATE_HALF, zg + GATE_HALF + 1):
                if w.get((x, -1, z)) in (None, GRASS):
                    w[(x, -1, z)] = 'minecraft:dirt_path'
        gates.append({'field': _p(x_in + 4.5, 0, zg + 0.5), 'inside': _p((x_in + x_out) / 2.0 + 0.5, 0, zg + 0.5),
                      'outside': _p(x_out - 3.5, 0, zg + 0.5), 'yaw': -90.0, 'width': 2 * GATE_HALF + 1,
                      'height': GATE_H, 'x_range': [x_out, x_in + 1]})
    return gates


# ---------------------------------------------------------------- ground
def hill_height(x, z, rnd_phase):
    """Extra height of the rolling ground at (x, z): flat round the stadium and the castle, rising to a low ridge
    near the edge of the set and easing down again at the rim."""
    # distance from the stadium's ellipse (semi-axes 100 x 150) as a 0..1 ramp
    e = math.hypot(x / 108.0, z / 158.0)
    a = max(0.0, min(1.0, (e - 1.08) / 0.32))
    edge = max(abs(x), abs(z))
    rim = max(0.0, min(1.0, (RADIUS - edge) / 18.0))
    # keep the land between the stadium and the castle flat, and the castle's own footprint
    cx0, cx1 = CASTLE_AT[0] - 14, CASTLE_AT[0] + 96 + 14
    cz0, cz1 = CASTLE_AT[2] - 14, CASTLE_AT[2] + 145 + 14
    near_castle = max(0.0, min(1.0, max(cx0 - x, x - cx1, cz0 - z, z - cz1) / 16.0)) if x > 60 else 1.0
    s = a * near_castle
    wave = (math.sin(x * 0.045 + rnd_phase[0]) * 0.5 + math.sin(z * 0.037 + rnd_phase[1]) * 0.35
            + math.sin((x + z) * 0.061 + rnd_phase[2]) * 0.25)
    h = s * (7.0 + 5.0 * wave) * (0.35 + 0.65 * rim)
    return max(0, int(round(h)), castle_rise(x, z, rnd_phase))


def castle_rise(x, z, rnd_phase):
    """The castle's knoll: PLATEAU over its footprint (corners rounded), easing down to the field over 12 blocks on
    the stadium side and up to 40 elsewhere, its edge wandering a few blocks so it reads as land, not a plinth."""
    x0, x1 = CASTLE_AT[0] + 6, CASTLE_AT[0] + 95 - 6
    z0, z1 = CASTLE_AT[2] + 10, CASTLE_AT[2] + 144 - 10
    dx = max(x0 - x, x - x1, 0)
    dz = max(z0 - z, z - z1, 0)
    d = math.hypot(dx, dz) - 10.0
    d += 3.0 * math.sin(z * 0.11 + rnd_phase[0]) + 2.0 * math.sin(x * 0.13 + rnd_phase[1])
    width = 12.0 + 28.0 * max(0.0, min(1.0, (x - CASTLE_AT[0]) / 30.0))
    if d <= 0:
        return PLATEAU
    t = max(0.0, 1.0 - d / width)
    return int(round(PLATEAU * t * t * (3 - 2 * t)))


def lay_ground(w, keep, castle):
    """Grass over the whole radius at y -1 (the field's layer), raised into rolling hills away from the stadium.
    Wherever a column stands above a neighbour it is filled down with dirt; the rim gets an earth skirt to y -3. `keep` is the set of
    columns the pitch already covers at y -1. Returns the height map."""
    rnd = random.Random(1993)
    phase = (rnd.uniform(0, 6.28), rnd.uniform(0, 6.28), rnd.uniform(0, 6.28))
    H = {}
    for x in range(-RADIUS, RADIUS + 1):
        for z in range(-RADIUS, RADIUS + 1):
            H[(x, z)] = -1 + hill_height(x, z, phase)
    # the castle's own foot, and two blocks round it, stand on the plateau whatever the knoll's edge does
    foot = set((p[0], p[2]) for p in castle if p[1] <= CASTLE_AT[1] + 2)
    for (x, z) in foot:
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                c = (x + dx, z + dz)
                if c in H:
                    H[c] = max(H[c], CASTLE_AT[1])
    # where the castle's hill shell starts a few blocks above the ground, close the gap with its own stone (the
    # foundation skirt the overworld build lays, kept short: a hollow higher up is the hill's inside)
    low = {}
    for p in castle:
        c = (p[0], p[2])
        if p[1] < low.get(c, 999):
            low[c] = p[1]
    for c, y_low in low.items():
        h = H.get(c)
        if h is not None and h < y_low <= h + 6:
            for y in range(h + 1, y_low):
                w[(c[0], y, c[1])] = 'minecraft:stone'
    for (x, z), h in H.items():
        if (x, z) in keep:
            continue
        w[(x, h, z)] = GRASS
        lo = min(H.get((x + d[0], z + d[1]), h) for d in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        for y in range(lo + 1, h):
            w[(x, y, z)] = 'minecraft:dirt'
        if max(abs(x), abs(z)) == RADIUS:
            for y in range(min(h - 3, -3), h):
                w[(x, y, z)] = 'minecraft:dirt'
    return H


def plant_trees(w, H, castle):
    """Spruces in loose clumps on the outskirts: behind both ends and the west stand, never between the stadium and
    the castle. Leaves are persistent."""
    rnd = random.Random(1991)
    spots = []
    tries = 0
    while len(spots) < 70 and tries < 4000:
        tries += 1
        x = rnd.randint(-RADIUS + 6, RADIUS - 6)
        z = rnd.randint(-RADIUS + 6, RADIUS - 6)
        if math.hypot(x / 110.0, z / 162.0) < 1.12:
            continue
        if x > CASTLE_AT[0] - 30 and CASTLE_AT[2] - 30 < z < CASTLE_AT[2] + 175:
            continue
        if any(abs(z - g) < 10 for g in GATE_Z) and x < -90:
            continue
        if any(math.hypot(x - a, z - b) < 7 for a, b in spots):
            continue
        spots.append((x, z))
    n = 0
    for x, z in spots:
        base = H[(x, z)] + 1
        hgt = rnd.randint(9, 15)
        for y in range(base, base + hgt):
            w[(x, y, z)] = state('spruce_log', axis='y')
        top = base + hgt
        for y in range(base + 3, top + 2):
            r = max(0.6, (top + 1 - y) * 0.42)
            if (y - base) % 2 == 1:
                r *= 0.7
            ri = int(math.ceil(r))
            for dx in range(-ri, ri + 1):
                for dz in range(-ri, ri + 1):
                    if dx * dx + dz * dz <= r * r + 0.3 and (dx, dz) != (0, 0) or y >= top:
                        if dx * dx + dz * dz > r * r + 0.3:
                            continue
                        c = (x + dx, y, z + dz)
                        if w.get(c) is None and c not in castle:
                            w[c] = state('spruce_leaves', distance=1, persistent=True, waterlogged=False)
        n += 1
    return n


# ---------------------------------------------------------------- light
def light(level):
    return state('light', level=level, waterlogged=False)


def place_lights(w, seats, towers, castle):
    """Light blocks: a sparse grid over the field (y 3), one over every fourth gallery seat, a few in each tower
    box; glowstone behind the castle's windows."""
    n = 0
    for x in range(-84, 85, 12):
        for z in range(-138, 139, 12):
            if math.hypot(x / 86.0, z / 140.0) > 0.97:
                continue
            if abs(x - DIVE[0]) <= DIVE_R + 2 and abs(z - DIVE[1]) <= DIVE_R + 2:
                continue
            c = (x, 3, z)
            if w.get(c) is None and w.get((x, 2, z)) is None:
                w[c] = light(11)
                n += 1
    k = 0
    for p in sorted(seats):
        k += 1
        if k % 4:
            continue
        c = (p[0], p[1] + 3, p[2])
        if w.get(c) is None:
            w[c] = light(12)
            n += 1
    for t in towers:
        cx, cz = t['centre']
        for y in range(t['top'] - 30, t['top']):
            c = (int(math.floor(cx)), y, int(math.floor(cz)))
            if w.get(c) is None and w.get((c[0], c[1] - 1, c[2])) is not None and y % 4 == 0:
                w[c] = light(13)
                n += 1
    # castle windows: glowstone in the air cell on the inner side of a glass block or pane, where the inward ray
    # meets the castle within 10 and the outward one finds open air for 16
    glow = 0
    for p, s in castle.items():
        nm = bid(s)
        if 'glass' not in nm:
            continue
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            inside = (p[0] + d[0], p[1], p[2] + d[1])
            if inside in castle or inside in w:
                continue
            hit_in = any((p[0] + d[0] * k, p[1], p[2] + d[1] * k) in castle for k in range(2, 11))
            hit_out = any((p[0] - d[0] * k, p[1], p[2] - d[1] * k) in castle for k in range(1, 17))
            if hit_in and not hit_out and (p[0] - d[0], p[1], p[2] - d[1]) not in castle:
                w[inside] = 'minecraft:glowstone'
                glow += 1
                break
    return n, glow


# ---------------------------------------------------------------- build
def _build(w, with_castle):
    pitch = load_pitch()
    w.update(pitch)
    hoops = recolour_hoops(w)
    towers = find_towers(w)
    castle, csize = schematic_cells()
    gates = cut_team_gates(w)
    keep = set((p[0], p[2]) for p in w if p[1] == -1)
    H = lay_ground(w, keep, castle)
    # dirt under the goal areas' sand and the castle's gravel, so nothing in the set can fall
    for p in [p for p, s in list(w.items()) + list(castle.items()) if bid(s) in ('minecraft:sand', 'minecraft:gravel')]:
        if w.get((p[0], p[1] - 1, p[2])) is None and (p[0], p[1] - 1, p[2]) not in castle:
            w[(p[0], p[1] - 1, p[2])] = 'minecraft:dirt'
    n_td = hang_tower_drapes(w, towers)
    n_gd = hang_gallery_drapes(w)
    n_trees = plant_trees(w, H, castle)
    seats = seat_cells(w)
    n_light, n_glow = place_lights(w, seats, towers, castle)
    info = {'hoops': hoops, 'towers': towers, 'gates': gates, 'H': H, 'castle': castle, 'castle_size': csize,
            'tower_drapes': n_td, 'gallery_drapes': n_gd, 'trees': n_trees, 'lights': n_light, 'glow': n_glow,
            'template': dict(w)}
    if with_castle:
        w.update(castle)
    return info


def make_anchors(info, w):
    """ANCHORS from the built set (w includes the castle)."""
    seats = seat_cells(w)
    towers = info['towers']
    hoops = info['hoops']
    # the teachers' and the commentator's boxes: the two towers flanking the centre line on the castle side
    east = sorted([t for t in towers if t['centre'][0] > 60], key=lambda t: abs(t['centre'][1]))
    centre_pair = sorted(east[:2], key=lambda t: t['centre'][1])
    commentator_t, teachers_t = centre_pair[0], centre_pair[1]
    reserved = (commentator_t, teachers_t)
    # close rows: the gallery between those two towers (the castle side's touchline, nearest the field)
    z_lo, z_hi = commentator_t['z'][1] + 1, teachers_t['z'][0] - 1
    stands, close_rows, box_rows = {}, [], {}
    for comp in _components(seats):
        cells = set(comp)
        owner = None
        for t in towers:
            if all(in_tower(t, p[0], p[2], 1) for p in comp):
                owner = t
        for seg in row_segments(comp, cells):
            mx, mz = (seg[0] + seg[3]) / 2.0, (seg[2] + seg[5]) / 2.0
            if owner is not None:
                if owner in reserved:
                    box_rows.setdefault(id(owner), []).append(seg)
                    continue
                house = owner['house']
            else:
                if mx > 60 and z_lo <= mz <= z_hi and seg[1] < 20:
                    close_rows.append(seg)
                    continue
                house = wall_house(w, int(math.floor(mx)), int(math.floor(mz)))
            stands.setdefault(house, []).append([round(v, 3) for v in seg])
    for h in stands:
        stands[h].sort()

    def box_anchor(t):
        rows = sorted(box_rows.get(id(t), []), key=lambda s: s[1])
        # a standing spot at the front of the box: a plank cell with two free cells above, nearest the field
        cand = [p for p, s in w.items() if in_tower(t, p[0], p[2]) and p[1] > 30 and bid(s) == 'minecraft:spruce_planks'
                and free(w.get((p[0], p[1] + 1, p[2]))) and free(w.get((p[0], p[1] + 2, p[2])))]
        front = min(cand, key=lambda p: (p[1], math.hypot(p[0] + 0.5, p[2] + 0.5)))
        stand = _p(front[0] + 0.5, front[1] + 1, front[2] + 0.5)
        return {'stand': stand, 'yaw': _yaw_of(-stand[0], -stand[2]), 'rows': [[round(v, 3) for v in r] for r in rows],
                'house': t['house'], 'tower': [t['x'][0], t['z'][0], t['x'][1], t['z'][1]]}

    # castle_view: over the field, 26 up on the centre line, looking through the gap between the teachers' and the
    # commentator's towers at the castle's middle (its spire stands in that gap)
    castle_view = {'pos': _p(-20.0, 26.0, 0.5), 'look_at': _p(CASTLE_AT[0] + 48.0, CASTLE_AT[1] + 51.0, 0.5)}
    anchors = {
        'pitch_centre': _p(0.0, GRASS_Y, 0.0),
        'grass_y': GRASS_Y,
        'hoops': [{'centre': h['centre'], 'yaw': h['yaw'], 'radius': h['radius']} for h in hoops],
        'stands': [{'house': h, 'rows': stands[h]} for h in sorted(stands)],
        'close_rows': [[round(v, 3) for v in s] for s in sorted(close_rows)],
        'team_gates': [{'field': g['field'], 'inside': g['inside'], 'outside': g['outside'], 'yaw': g['yaw']}
                       for g in info['gates']],
        'hooch_spot': _p(0.5, GRASS_Y, 0.5),
        'ball_crate': _p(3.5, GRASS_Y, 0.5),
        'commentator_box': box_anchor(commentator_t),
        'teachers_box': box_anchor(teachers_t),
        'castle_view': castle_view,
        'castle_box': [[CASTLE_AT[0], CASTLE_AT[1], CASTLE_AT[2]],
                       [CASTLE_AT[0] + info['castle_size'][0], CASTLE_AT[1] + info['castle_size'][1],
                        CASTLE_AT[2] + info['castle_size'][2]]],
        'rain_box': [[-100, 0, -150], [200, 90, 150]],
        'rain': {'at': [50.0, 45.0, 0.0], 'spread': [150.0, 45.0, 150.0]},
        'dive_column': {'at': _p(DIVE[0] + 0.5, GRASS_Y, DIVE[1] + 0.5), 'top': GRASS_Y + DIVE_H,
                        'radius': DIVE_R},
        'RADIUS': RADIUS,
    }
    return anchors


ANCHORS = None


def build(world=None, with_castle=True):
    """Fills `world` (a dict scene cell -> block state; a new one when None) with the set, the castle included unless
    with_castle is False, and returns ANCHORS."""
    global ANCHORS
    w = {} if world is None else world
    info = _build(w, with_castle)
    full = w if with_castle else dict(w, **info['castle'])
    ANCHORS = make_anchors(info, full)
    build.info = info
    return ANCHORS


def write_nbt(template=None):
    """Writes structure/quidditch_pitch.nbt from the template cells (scene coordinates); returns (palette, blocks)."""
    if template is None:
        build({})
        template = build.info['template']
    local = {}
    for (x, y, z), s in template.items():
        local[(x - TEMPLATE_AT[0], y - TEMPLATE_AT[1], z - TEMPLATE_AT[2])] = s
    return write_template(local, SIZE, OUT)


# ---------------------------------------------------------------- checks
def check(anchors, info, w):
    """Problem strings; empty when the set is right. Also returns the numbers worth printing."""
    problems, numbers = [], {}
    world = {p: bid(s) for p, s in w.items()}
    problems += physics_problems(world, RADIUS)
    tmpl = info['template']
    beyond = [p for p in list(tmpl) + list(info['castle']) if abs(p[0]) > RADIUS or abs(p[2]) > RADIUS]
    numbers['cells beyond radius'] = len(beyond)
    if beyond:
        problems.append('%d cells beyond the radius, e.g. %s' % (len(beyond), beyond[:5]))
    lx = [p[0] - TEMPLATE_AT[0] for p in tmpl]
    ly = [p[1] - TEMPLATE_AT[1] for p in tmpl]
    lz = [p[2] - TEMPLATE_AT[2] for p in tmpl]
    if min(lx) < 0 or min(ly) < 0 or min(lz) < 0 or max(lx) >= SIZE[0] or max(ly) >= SIZE[1] or max(lz) >= SIZE[2]:
        problems.append('template outside its size: %s %s' % ((min(lx), min(ly), min(lz)), (max(lx), max(ly), max(lz))))
    total = len(tmpl) + len(info['castle'])
    numbers['template blocks'] = len(tmpl)
    numbers['schematic blocks'] = len(info['castle'])
    numbers['total blocks'] = total
    if total > BUDGET:
        problems.append('template + schematic = %d > %d' % (total, BUDGET))

    def cell(p):
        return (int(math.floor(p[0])), int(math.floor(p[1] + 1e-6)), int(math.floor(p[2])))

    def stand(name, p):
        c = cell(p)
        if free(w.get((c[0], c[1] - 1, c[2]))):
            problems.append('%s %s: nothing solid under it (%s)' % (name, p, w.get((c[0], c[1] - 1, c[2]))))
        for dy in (0, 1):
            if not free(w.get((c[0], c[1] + dy, c[2]))):
                problems.append('%s %s: cell %s is %s' % (name, p, (c[0], c[1] + dy, c[2]),
                                                          w.get((c[0], c[1] + dy, c[2]))))

    def row(name, seg):
        n = max(1, int(math.ceil(math.hypot(seg[3] - seg[0], seg[5] - seg[2]) / 0.25)))
        for i in range(n + 1):
            t = i / float(n)
            c = (int(math.floor(seg[0] + (seg[3] - seg[0]) * t)), int(math.floor(seg[1])),
                 int(math.floor(seg[2] + (seg[5] - seg[2]) * t)))
            if not is_seat(w.get(c)) or not free(w.get((c[0], c[1] + 1, c[2]))) or \
                    not free(w.get((c[0], c[1] + 2, c[2]))):
                problems.append('%s %s: cell %s is %s' % (name, seg, c, w.get(c)))
                return
        if abs(seg[1] - (math.floor(seg[1]) + 0.5)) > 1e-6:
            problems.append('%s %s: seat top not at a half block' % (name, seg))

    for k in ('pitch_centre', 'hooch_spot', 'ball_crate'):
        stand(k, anchors[k])
    for i, g in enumerate(anchors['team_gates']):
        for k in ('field', 'inside', 'outside'):
            stand('team_gates[%d].%s' % (i, k), g[k])
    for k in ('commentator_box', 'teachers_box'):
        stand(k + '.stand', anchors[k]['stand'])
        for j, r in enumerate(anchors[k]['rows']):
            row('%s.rows[%d]' % (k, j), r)
        if not anchors[k]['rows']:
            problems.append(k + ' has no seat rows')
    seats = 0
    for st in anchors['stands']:
        for j, r in enumerate(st['rows']):
            row('stands.%s[%d]' % (st['house'], j), r)
            seats += math.hypot(r[3] - r[0], r[5] - r[2]) / 0.9 + 1
    numbers['stand rows'] = sum(len(s['rows']) for s in anchors['stands'])
    numbers['crowd seats (about, at 0.9)'] = int(seats)
    for j, r in enumerate(anchors['close_rows']):
        row('close_rows[%d]' % j, r)
    if not anchors['close_rows']:
        problems.append('no close rows')
    if sorted(s['house'] for s in anchors['stands']) != sorted(HOUSE_COLOUR):
        problems.append('stands do not cover the four houses: %s' % [s['house'] for s in anchors['stands']])
    # hoops: three a side, rings open at the centre +-1 in their plane
    if len(anchors['hoops']) != 6:
        problems.append('%d hoops' % len(anchors['hoops']))
    worst_open = 99.0
    for i, h in enumerate(anchors['hoops']):
        cx, cy, cz = h['centre']
        for zz in (cz - 0.5, cz + 0.5):
            for du in (-1, 0, 1):
                for dv in (-1, 0, 1):
                    c = cell((cx + du, cy + dv, zz))
                    if w.get(c) is not None:
                        problems.append('hoop %d not open at %s: %s' % (i, c, w.get(c)))
        worst_open = min(worst_open, h['radius'])
        ring = [p for p in info['template'] if is_hoop_cell(p) and abs(p[2] + 0.5 - cz) <= 1.0]
        if not ring or any(w.get(p) != 'minecraft:gold_block' for p in ring):
            problems.append('hoop %d is not gold' % i)
    numbers['smallest hoop radius'] = worst_open
    # dive column: all air, nothing at all (light included), 70 tall to the grass
    dc = anchors['dive_column']
    x0, z0 = int(math.floor(dc['at'][0])), int(math.floor(dc['at'][2]))
    bad = [(x, y, z) for x in range(x0 - dc['radius'], x0 + dc['radius'] + 1)
           for z in range(z0 - dc['radius'], z0 + dc['radius'] + 1) for y in range(GRASS_Y, dc['top'] + 1)
           if (x, y, z) in w]
    if bad:
        problems.append('dive column not empty: %s' % bad[:5])
    if free(w.get((x0, GRASS_Y - 1, z0))):
        problems.append('no grass under the dive column')
    near = min(math.hypot(dc['at'][0] - h['centre'][0], dc['at'][2] - h['centre'][2]) for h in anchors['hoops'])
    numbers['dive column to nearest hoop'] = round(near, 1)
    # castle_view: in air, and the line to the castle stays clear until it is past the stands
    cv = anchors['castle_view']
    if not free(w.get(cell(cv['pos']))):
        problems.append('castle_view pos is in %s' % w.get(cell(cv['pos'])))
    p0, p1 = cv['pos'], cv['look_at']
    L = math.dist(p0, p1)
    first_hit = None
    for i in range(int(L / 0.25)):
        t = i * 0.25 / L
        q = [p0[k] + (p1[k] - p0[k]) * t for k in range(3)]
        if not free(w.get(cell(q))):
            first_hit = q
            break
    numbers['castle_view first hit x'] = None if first_hit is None else round(first_hit[0], 1)
    if first_hit is not None and first_hit[0] < CASTLE_AT[0]:
        problems.append('castle_view line blocked at %s by %s' % (first_hit, w.get(cell(first_hit))))
    # the ground has no holes: every column has a solid block at its surface (and the field layer is whole)
    holes = [c for c, h in info['H'].items() if free(w.get((c[0], h, c[1]))) and free(w.get((c[0], -1, c[1])))]
    if holes:
        problems.append('%d ground holes, e.g. %s' % (len(holes), holes[:5]))
    # the castle stands on the ground: every castle cell at its foot layer has ground or castle under it... the foot
    # layer is the ground layer itself (y -1), so check the castle has nothing below -1 and nothing floating there
    # the castle's foot sits in the ground: every castle cell near the foot with nothing of its own under it is at
    # or below the ground's surface there (it takes the place of the surface block), or stands on a solid block
    col_low = {}
    for p in info['castle']:
        col_low[(p[0], p[2])] = min(p[1], col_low.get((p[0], p[2]), 999))
    floating = [p for p in info['castle'] if p[1] <= CASTLE_AT[1] + 6 and p[1] == col_low[(p[0], p[2])]
                and info['H'].get((p[0], p[2]), -99) < p[1] and free(w.get((p[0], p[1] - 1, p[2])))]
    numbers['castle foot cells over air'] = len(floating)
    if floating:
        problems.append('%d castle foot cells over air, e.g. %s' % (len(floating), floating[:5]))
    low = min(p[1] for p in info['castle'])
    if low != CASTLE_AT[1]:
        problems.append('castle foot at %d, not %d' % (low, CASTLE_AT[1]))
    return problems, numbers


def main():
    w = {}
    anchors = build(w)
    info = build.info
    problems, numbers = check(anchors, info, w)
    assert not problems, '\n'.join(problems[:40]) + ('\n... %d in all' % len(problems))
    n_pal, n_blocks = write_nbt(info['template'])
    print('quidditch_pitch.nbt: size %s, %d blocks, %d palette states, %d bytes' % (SIZE, n_blocks, n_pal,
                                                                                os.path.getsize(OUT)))
    print('towers %d, tower drapes %d, gallery drapes %d, trees %d, lights %d, castle window glow %d' % (
        len(info['towers']), info['tower_drapes'], info['gallery_drapes'], info['trees'], info['lights'],
        info['glow']))
    for k, v in numbers.items():
        print('  %s: %s' % (k, v))
    print('  build ticks at 4000/tick: %d' % math.ceil(numbers['total blocks'] / 4000.0))
    print('TEMPLATE_AT %s  RADIUS %d  SCHEMATIC %s' % (TEMPLATE_AT, RADIUS, SCHEMATIC))
    for k, v in anchors.items():
        if k == 'stands':
            for s in v:
                print('  stands.%s: %d rows' % (s['house'], len(s['rows'])))
        elif k == 'close_rows':
            print('  close_rows (%d): %s' % (len(v), v))
        elif k in ('commentator_box', 'teachers_box'):
            print('  %s: stand %s yaw %s house %s, %d rows' % (k, v['stand'], v['yaw'], v['house'], len(v['rows'])))
        else:
            print('  %s: %s' % (k, v))


if __name__ == '__main__':
    main()
