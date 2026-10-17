"""Generates data/hogwartsmagic/memories/lake_patronus.json: the Memory of the Lake (Prisoner of Azkaban, the
time-turned night at the lake).

Usage (from the repo root): python build_scripts/pensieve_scenes/lake_patronus.py

Staging follows the film: from the far bank, future Harry and Hermione watch the past across a black lake; on the
near shore Sirius lies at the water's edge and past Harry kneels by him while the Dementors spiral down and the
lake freezes. Harry's own Patronus sputters out, the Kiss begins, and future Harry realises nobody is coming:
it was him. He steps to the water and casts; the stag gallops across the lake, the light flings the Dementors
away, past Harry sees the stag and a distant figure, and collapses. The stag returns, bows, and dissolves.
All spoken lines are original paraphrase; only the incantation is canonical.

Scene frame: ground top is block y -1 (actors stand at y 0). Lake = water disk r25 at the origin, surface block
y -1, stepping down to y -4 over a gravel bed, stone under everything. X east, Z south: the near shore is +Z.

Hogwarts: the Mini Hogwarts schematic (set.schematics) stands on its own rocky hill beyond the far bank, north and
a little east, its long west face (great hall, bridge, tallest spire) turned to the lake, on a grass shelf level
with the far-bank hills. A scattering of its lake-facing black glass windows is replaced with glowstone (one fill
per window, picked here from the schematic) so the castle shows lit windows at midnight. fog_far lets the
viewer see it through a haze instead of the default fog wall at the radius.

Lying flat: a custom pose cannot carry offsets, so `lying` is done with additive `limbs` (rot -90 about x on every
part plus pivot offsets in model pixels, y down) over a zeroed `flat` pose. The actor's position is where the
head lies; the body extends ~1.5 blocks along the actor's facing (feet forward, face up).
"""
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import (write_scene, physics_problems, unsupported_gravity, schematic_grid, rotate_xz,
                          SCHEMATIC_SKIPPED)

R = 48                                   # the lake's own ground runs to +-R
SCENE_R = 200                            # the set's radius: room for Mini Hogwarts far across the lake
FOG_FAR = 200                            # fog_far: see out to the castle through a haze (PensieveVoid)
LENGTH = 84.0
LAKE_R = 25

# ---------------------------------------------------------------- helpers (same conventions as summoning.py)
def r2(v):
    return round(v + 0.0, 2)

def T(s):
    return '%gs' % round(s, 2)

events, words, cam = [], [], []
def ev(t, kind, id_, **kw):
    e = {'t': T(t), 'kind': kind, 'id': id_}
    e.update(kw)
    events.append(e)
def ip(at):
    return [int(round(c)) for c in at]
def snd(t, id_, at=None, volume=None, pitch=None):
    kw = {}
    if at is not None: kw['at'] = ip(at)
    if volume is not None: kw['volume'] = volume
    if pitch is not None: kw['pitch'] = pitch
    ev(t, 'sound', id_, **kw)
def part(t, id_, at, count, spread):
    ev(t, 'particles', id_, at=ip(at), count=count, spread=[r2(s) for s in spread])
def block(t, id_, at):
    ev(t, 'block', id_, at=list(at))
def effect(t, id_, at, duration=None):
    kw = {'at': ip(at)}
    if duration is not None: kw['duration'] = duration
    ev(t, 'effect', id_, **kw)
def narrate(t, text, dur=None):
    w = {'t': T(t), 'text': text, 'narration': True}
    if dur: w['duration'] = T(dur)
    words.append(w)
def line(t, by, text):
    words.append({'t': T(t), 'by': by, 'text': text})
def shot(t, pos, look, fov, cut=True, ease=None, roll=None):
    k = {'t': T(t), 'pos': [r2(v) for v in pos],
         'look_at': look if isinstance(look, str) else [r2(v) for v in look], 'fov': fov}
    if cut: k['cut'] = True
    if ease: k['ease'] = ease
    if roll is not None: k['roll'] = roll
    cam.append(k)
def K(t, v, ease=None):
    k = {'t': T(t), 'pos': [r2(c) for c in v]}
    if ease: k['ease'] = ease
    return k
def Y(t, yaw, pitch=0, ease=None):
    k = {'t': T(t), 'yaw': r2(yaw), 'pitch': r2(pitch)}
    if ease: k['ease'] = ease
    return k
def P(t, name, blend=None):
    k = {'t': T(t), 'name': name}
    if blend: k['blend'] = blend
    return k
def A(t, do, **kw):
    k = {'t': T(t), 'do': do}
    k.update(kw)
    return k
def V3(t, v, ease=None):
    k = {'t': T(t), 'v': [r2(c) for c in v]}
    if ease: k['ease'] = ease
    return k
def yaw_to(a, b):
    """Minecraft yaw from a to b (0 = +Z, 90 = -X), using x and z of (x, y, z) tuples."""
    return math.degrees(math.atan2(-(b[0] - a[0]), b[2] - a[2]))
def unwrap(prev, y):
    while y - prev > 180: y -= 360
    while y - prev < -180: y += 360
    return y

seed = 11
def rnd():
    global seed
    seed = (seed * 1103515245 + 12345) % 2147483648
    return seed / 2147483648

WAND = 'hogwartsmagic:harry_potter_wand'
HARRY_SKIN = {'texture': 'harry'}

# ---------------------------------------------------------------- key places
C = (0.5, 0, 26.5)                       # the vortex centre over the near shore
PH = (0.0, 0, 28.0)                      # past Harry kneels here
SI = (1.8, 0, 29.2)                      # Sirius: where his head lies (feet north, to the water)
FH0 = (-5.5, 0, -30.5)                   # future Harry hiding
FH_EDGE = (-4.5, 0, -25.8)               # ...and at the water's edge
HE0 = (-2.3, 0, -30.6)                   # Hermione hiding
HE1 = (-3.2, 0, -26.9)                   # ...at his side at the end
LIGHT_AT = (0, 0, 27)                    # where the Patronus light bursts
CAST_TARGET = (0.5, 1.5, 26.0)

# beats
T_FROST0 = 20.4
T_WISP = 26.5
# past Harry's weak Patronus: the shield blooms, a second burst a beat later, it fails, they close in again
T_PUSH = round(T_WISP + 0.3, 2)          # the shield opens (patronus_wisp: 6-tick bloom)
T_PUSHED = round(T_PUSH + 1.0, 2)        # the nearest Dementors have been driven back
T_BURST2 = round(T_WISP + 1.3, 2)        # he half-manages it again
T_HANG_END = round(T_WISP + 2.7, 2)      # the shield breaks up (hold ends at +2.3 s, gone by +3.3 s)
T_REJOIN = round(T_WISP + 4.3, 2)        # back on their circling paths
N_PUSHED = 4
T_KISS = 33.8
T_STAND = 42.4
T_CAST = 46.9
T_STAG = 47.3                            # the stag erupts from the stream
T_SHORE = 52.4                           # it reaches the near shore
T_LIGHT = 52.5
T_CIRCLE_END = 58.4
T_STAG_STOP = 69.6
T_BOW = 70.2
T_STAG_GONE = 72.2
T_FALL = 66.0

# ---------------------------------------------------------------- the set
fills = [
    {'block': 'minecraft:stone', 'from': [-R, -8, -R], 'to': [R, -6, R]},
    {'block': 'minecraft:dirt', 'from': [-R, -5, -R], 'to': [R, -2, R]},
    {'block': 'minecraft:grass_block', 'from': [-R, -1, -R], 'to': [R, -1, R]},
    # low hills behind both banks, so the horizon is not a flat edge
    {'block': 'minecraft:grass_block', 'from': [-R, 0, -R], 'to': [R, 0, -38]},
    {'block': 'minecraft:grass_block', 'from': [-R, 1, -R], 'to': [R, 1, -42]},
    {'block': 'minecraft:grass_block', 'from': [-R, 2, -R], 'to': [R, 2, -45]},
    {'block': 'minecraft:grass_block', 'from': [-R, 0, 39], 'to': [R, 0, R]},
    {'block': 'minecraft:grass_block', 'from': [-R, 1, 43], 'to': [R, 1, R]},
    {'block': 'minecraft:grass_block', 'from': [-R, 0, -R], 'to': [-36, 0, R]},
    {'block': 'minecraft:grass_block', 'from': [36, 0, -R], 'to': [R, 0, R]},
    {'block': 'minecraft:grass_block', 'from': [-R, 1, -R], 'to': [-41, 1, R]},
    {'block': 'minecraft:grass_block', 'from': [41, 1, -R], 'to': [R, 1, R]},
]
disks = []
# dark shore: podzol and coarse dirt patches, then pebble strips at both waterlines
for c, r, b in (((0, -1, 0), 31, 'minecraft:podzol'), ((-14, -1, 22), 8, 'minecraft:coarse_dirt'),
                ((16, -1, -20), 7, 'minecraft:coarse_dirt'), ((22, -1, 12), 6, 'minecraft:podzol'),
                ((-22, -1, -10), 6, 'minecraft:coarse_dirt')):
    disks.append({'block': b, 'center': list(c), 'radius': r})
disks += [
    {'block': 'minecraft:gravel', 'center': [0, -1, 27], 'radius': 7},
    {'block': 'minecraft:cobblestone', 'center': [-5, -1, 27], 'radius': 1},
    {'block': 'minecraft:andesite', 'center': [6, -1, 26], 'radius': 2},
    {'block': 'minecraft:gravel', 'center': [-3, -1, -27], 'radius': 5},
    {'block': 'minecraft:coarse_dirt', 'center': [-4, -1, -30], 'radius': 2},
]
# the lake: gravel beds first (each shelf one step deeper), then the water over them
SHELVES = [(-1, 25), (-2, 24), (-3, 22), (-4, 19)]
for y, r in SHELVES:
    disks.append({'block': 'minecraft:gravel', 'center': [0, y - 1, 0], 'radius': r})
for y, r in SHELVES:
    disks.append({'block': 'minecraft:water', 'center': [0, y, 0], 'radius': r})

def in_lake(x, z):
    return x * x + z * z <= LAKE_R * LAKE_R

# far bank cover: an azalea bush row with a gap (x -5..-4) to step through, and a big rock
cover = []
for x in list(range(-9, -5)) + list(range(-3, 1)):
    cover.append(((x, 0, -29), 'minecraft:flowering_azalea' if x in (-7, -2) else 'minecraft:azalea'))
for x in (-8, -1):
    cover.append(((x, 0, -30), 'minecraft:azalea'))
ROCK = [(1, 0, -30), (2, 0, -30), (3, 0, -30), (2, 0, -29), (3, 0, -29), (2, 0, -31), (3, 0, -31), (4, 0, -30),
        (2, 1, -30), (3, 1, -30), (2, 1, -31), (3, 2, -30)]
for i, p in enumerate(ROCK):
    cover.append((p, ('minecraft:mossy_cobblestone', 'minecraft:andesite', 'minecraft:stone', 'minecraft:tuff')[i % 4]))
# dead trees: bare log columns with a stub branch or two
DEAD = [((-11, 31), 6), ((9, 33), 7), ((-16, -27), 6), ((13, -30), 5), ((-26, 8), 5), ((26, -6), 6)]
for (x, z), h in DEAD:
    for y in range(0, h):
        cover.append(((x, y, z), 'minecraft:dark_oak_log'))
    cover.append(((x + 1, h - 2, z), 'minecraft:dark_oak_log'))
    cover.append(((x - 1, h - 3, z), 'minecraft:dark_oak_log'))
    cover.append(((x, h - 1, z + 1), 'minecraft:dark_oak_log'))
# reeds at the waterline (sugar cane beside water), rushes and dead grass further up
reeds = []
for k in range(220):
    a = rnd() * 2 * math.pi
    x, z = int(round((LAKE_R + 1) * math.sin(a))), int(round((LAKE_R + 1) * math.cos(a)))
    if in_lake(x, z):
        continue
    if abs(x - PH[0]) < 9 and z > 20:           # keep the near-shore stage clear
        continue
    if -10 <= x <= 5 and z < -22:               # and the far-bank hiding place
        continue
    if any(in_lake(x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        reeds.append((x, z))
reeds = sorted(set(reeds))
for i, (x, z) in enumerate(reeds):
    if i % 3 == 2:
        continue
    cover.append(((x, 0, z), 'minecraft:sugar_cane'))
    if i % 4 == 0:
        cover.append(((x, 1, z), 'minecraft:sugar_cane'))
grass_cells = set()
while len(grass_cells) < 140:
    x, z = int(rnd() * 2 * (R - 6)) - (R - 6), int(rnd() * 2 * (R - 6)) - (R - 6)
    d = math.hypot(x, z)
    if d < LAKE_R + 2 or d > 36:
        continue
    if abs(x - 1) < 8 and 22 < z < 34:
        continue
    if -10 <= x <= 5 and -34 < z < -22:
        continue
    grass_cells.add((x, z))
for x, z in sorted(grass_cells):
    cover.append(((x, 0, z), ('minecraft:short_grass', 'minecraft:fern', 'minecraft:dead_bush')[(x * 7 + z * 3) % 3]))
taken = set()
for (p, b) in cover:
    if p in taken:
        continue
    taken.add(p)
    fills.append({'block': b, 'from': list(p), 'to': list(p)})
# moonlight: invisible light blocks well above the two stages (8 blocks up: ice below never sees light > 11)
LIGHTS = [(0, 7, 31), (-3, 7, -32), (0, 9, 0)]
for p in LIGHTS:
    fills.append({'block': 'minecraft:light', 'from': list(p), 'to': list(p)})
# dark spruces on the hills, silhouettes against the sky
TREES = [(-20, -40), (-9, -42), (6, -41), (19, -39), (31, -30), (-32, -28), (-38, 4), (38, 10), (-28, 34),
         (-12, 41), (14, 41), (30, 33), (36, -16), (-37, -14)]
features = [{'id': 'minecraft:spruce', 'at': [x, (2 if z <= -45 else 1 if z <= -42 or z >= 43 or abs(x) >= 41
                                                   else 0 if z <= -38 or z >= 39 or abs(x) >= 36 else -1) + 1, z]}
            for x, z in TREES]

N_LAKE_FILLS = len(fills)

# ---------------------------------------------------------------- Mini Hogwarts beyond the lake
CASTLE_ROT = 270                         # the schematic's west face looks south, across the lake
CASTLE_AT = (-52, -10, -178)             # min corner of the rotated 145 x 109 x 96 footprint
SHELF_Y = 2                              # the grass shelf: level with the far-bank hills, cutting the hill
CW, CH, CL, ccells = schematic_grid('mini_hogwarts')
CFX, CFZ = (CL, CW) if CASTLE_ROT in (90, 270) else (CW, CL)    # rotated footprint, x by z
def cscene(x, y, z):
    rx, rz = rotate_xz(x, z, CW, CL, CASTLE_ROT)
    return (CASTLE_AT[0] + rx, CASTLE_AT[1] + y, CASTLE_AT[2] + rz)
def cblock(x, y, z):
    return ccells[x + z * CW + y * CW * CL]
castle = {}
for i, b in enumerate(ccells):
    if b in SCHEMATIC_SKIPPED:
        continue
    x, z, y = i % CW, (i // CW) % CL, i // (CW * CL)
    castle[cscene(x, y, z)] = b
# the shelf: one layer of grass from the far-bank hills to behind the castle; its own hill is the bluff
fills.append({'block': 'minecraft:grass_block', 'from': [-110, SHELF_Y, -195], 'to': [150, SHELF_Y, -R - 1]})
# lit windows: black glass on the lake-facing (+Z) facade with a clear line out of the footprint toward the lake
OUT = next((dx, dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
           if [a - b for a, b in zip(cscene(5 + dx, 0, 5 + dz), cscene(5, 0, 5))] == [0, 0, 1])
GLASS = {'minecraft:black_stained_glass', 'minecraft:black_stained_glass_pane'}
def facade(x, y, z):
    if cblock(x, y, z) not in GLASS:
        return False
    x, z = x + OUT[0], z + OUT[1]
    while 0 <= x < CW and 0 <= z < CL:
        if cblock(x, y, z) not in SCHEMATIC_SKIPPED:
            return False
        x, z = x + OUT[0], z + OUT[1]
    return True
cand = {(x, y, z) for y in range(CH) for z in range(CL) for x in range(CW) if facade(x, y, z)}
windows, seen = [], set()
for c in sorted(cand):
    if c in seen:
        continue
    comp, todo = [], [c]
    seen.add(c)
    while todo:
        q = todo.pop()
        comp.append(q)
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (q[0] + d[0], q[1] + d[1], q[2] + d[2])
            if n in cand and n not in seen:
                seen.add(n)
                todo.append(n)
    windows.append(sorted(cscene(*q) for q in comp))
# a scattering: in each 12-block-wide slice of the facade (roughly a tower each), every third window from the
# bottom up (a lone window is lit), at most seven, so the lights spread over the towers and floors the same
# way every run
WINDOW_SHARE, WINDOW_MAX = 3, 7
lit = []
by_slice = {}
for w in windows:
    by_slice.setdefault(w[0][0] // 12, []).append(w)
for k in sorted(by_slice):
    ws = sorted(by_slice[k], key=lambda w: (w[0][1], w[0][0]))
    lit += ws[min(1, len(ws) - 1)::WINDOW_SHARE][:WINDOW_MAX]
for w in lit:
    xs, ys, zs = zip(*w)
    box = (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))
    vol = (box[1][0] - box[0][0] + 1) * (box[1][1] - box[0][1] + 1) * (box[1][2] - box[0][2] + 1)
    if vol == len(w):
        fills.append({'block': 'minecraft:glowstone', 'from': list(box[0]), 'to': list(box[1])})
    else:
        for c in w:
            fills.append({'block': 'minecraft:glowstone', 'from': list(c), 'to': list(c)})
schematics = [{'id': 'mini_hogwarts', 'at': list(CASTLE_AT), 'rotation': CASTLE_ROT}]

# ---------------------------------------------------------------- the world dict (physics + obstacle checks)
world = dict(castle)                     # the schematic first: fills and disks overwrite it, as in SetBuilder
for f in fills:
    a, b = f['from'], f['to']
    for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
        for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
            for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                world[(x, y, z)] = f['block']
for d in disks:
    cx, cy, cz = d['center']
    for dx in range(-d['radius'], d['radius'] + 1):
        for dz in range(-d['radius'], d['radius'] + 1):
            if dx * dx + dz * dz <= d['radius'] ** 2:
                world[(cx + dx, cy, cz + dz)] = d['block']
for x in range(-R, R + 1):
    for z in range(-R, R + 1):
        if world.get((x, -1, z)) == 'minecraft:water':
            assert in_lake(x, z)
# the castle's gravel over air gets stone under it (SetBuilder would hold it anyway): one slab per layer when the
# slab's cells are all empty, else a block per cell
by_y = {}
for c in unsupported_gravity(world):
    by_y.setdefault(c[1], []).append(c)
for y, cs in sorted(by_y.items()):
    x0, x1 = min(c[0] for c in cs), max(c[0] for c in cs)
    z0, z1 = min(c[2] for c in cs), max(c[2] for c in cs)
    slab = [(x, y - 1, z) for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)]
    if all(world.get(c) is None for c in slab):
        fills.append({'block': 'minecraft:stone', 'from': [x0, y - 1, z0], 'to': [x1, y - 1, z1]})
        under = slab
    else:
        under = [(x, y - 1, z) for x, _, z in cs]
        fills += [{'block': 'minecraft:stone', 'from': list(c), 'to': list(c)} for c in under]
    for c in under:
        world[c] = 'minecraft:stone'

PLANTS = {'minecraft:short_grass', 'minecraft:fern', 'minecraft:dead_bush', 'minecraft:light', 'minecraft:water',
          'minecraft:sugar_cane', 'minecraft:air'}
def solid_at(x, y, z):
    b = world.get((math.floor(x), math.floor(y), math.floor(z)))
    return b if b is not None and b not in PLANTS else None

# ---------------------------------------------------------------- frost: the lake freezing out from the near shore
FROST_C = (0, 27)
ice_cells = []
for x in range(-LAKE_R, LAKE_R + 1):
    for z in range(-LAKE_R, LAKE_R + 1):
        if world.get((x, -1, z)) != 'minecraft:water':
            continue
        d = math.hypot(x + 0.5 - FROST_C[0], z + 0.5 - FROST_C[1])
        if d <= 16:
            ice_cells.append((d, x, z))
ice_cells.sort()
for d, x, z in ice_cells:
    k = max(0, math.ceil(d) - 3)
    t = T_FROST0 + k * 0.32
    # frosted_ice melts on random ticks; packed_ice gives the same patchy look and stays
    b = 'minecraft:packed_ice' if (x * 31 + z * 17) % 5 == 0 or d > 14 else 'minecraft:ice'
    block(round(t, 2), b, (x, -1, z))
for k in range(0, 14, 2):
    t = T_FROST0 + k * 0.32
    rr = k + 3
    part(t, 'minecraft:snowflake', (FROST_C[0], 0, FROST_C[1] - rr * 0.7), 30, (rr * 0.7, 0.2, rr * 0.4))
    snd(t, 'minecraft:block.glass.place', at=(FROST_C[0], 0, FROST_C[1] - rr), volume=0.5, pitch=0.5 + k * 0.03)
snd(T_FROST0, 'minecraft:block.powder_snow.place', at=(0, 0, 25), volume=1.0, pitch=0.6)

# ---------------------------------------------------------------- lying limbs
LIE_ROT = {'head': (-90, 0, 0), 'body': (-90, 0, 0), 'right_arm': (-90, 12, 0), 'left_arm': (-90, -12, 0),
           'right_leg': (-90, 5, 0), 'left_leg': (-90, -5, 0)}
LIE_OFF = {'head': (0, 20, 0), 'body': (0, 22, 0), 'right_arm': (0, 20, -2), 'left_arm': (0, 20, -2),
           'right_leg': (0, 10, -12), 'left_leg': (0, 10, -12)}
poses = {
    'flat': {'head': [0, 0, 0], 'body': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0],
             'right_leg': [0, 0, 0], 'left_leg': [0, 0, 0]},
    'relax': {'head': [0, 0, 0]},
}

# Sirius: collapsed at the water's edge from the start, face turned aside
sirius = {'id': 'sirius', 'type': 'player', 'name': 'Sirius', 'skin': {'texture': 'sirius'},
          'path': [K(0, SI)], 'look': [Y(0, 180, 0)], 'poses': [P(0, 'flat', 1)],
          'limbs': {p: {'rot': [V3(0, (LIE_ROT[p][0], LIE_ROT[p][1], 25 if p == 'head' else LIE_ROT[p][2]))],
                        'offset': [V3(0, LIE_OFF[p])]} for p in LIE_ROT}}

# ---------------------------------------------------------------- past Harry (kneeling by Sirius throughout)
# rotations ADDED on top of the kneel pose (head 10, arms -25, legs 85); then over `flat` when he falls.
ph_rot = {
    'head': [V3(0, (25, 0, 0)), V3(14.6, (25, 0, 0)), V3(15.6, (-55, 0, 0)), V3(20.0, (-30, 0, 0)),
             V3(24.6, (-30, 0, 0)), V3(25.2, (-40, 0, 0)), V3(29.4, (-40, 0, 0)), V3(30.2, (22, 0, 0)),
             V3(33.4, (22, 0, 0)), V3(34.2, (-38, 0, 0)), V3(52.5, (-38, 0, 0)), V3(53.4, (28, 0, 0)),
             V3(57.6, (28, 0, 0)), V3(58.6, (-14, 0, 0)), V3(64.6, (-14, 0, 0)), V3(65.6, (18, 0, 0)),
             V3(T_FALL, (18, 0, 0)), V3(T_FALL + 0.7, (-90, 0, -20), 'in')],
    'body': [V3(0, (8, 0, 0)), V3(29.4, (8, 0, 0)), V3(30.2, (20, 0, 0)), V3(33.4, (20, 0, 0)),
             V3(34.2, (-12, 0, 0)), V3(52.5, (-12, 0, 0)), V3(53.4, (16, 0, 0)), V3(57.6, (16, 0, 0)),
             V3(58.6, (4, 0, 0)), V3(T_FALL, (6, 0, 0)), V3(T_FALL + 0.7, LIE_ROT['body'], 'in')],
    'right_arm': [V3(0, (0, 0, 0)), V3(24.9, (0, 0, 0)), V3(25.7, (-125, 0, 10)), V3(28.8, (-122, 0, 10)),
                  V3(29.6, (-40, 0, 0)), V3(30.2, (-115, 20, -25)), V3(33.4, (-115, 20, -25)),
                  V3(34.4, (10, 0, 8)), V3(52.5, (10, 0, 8)), V3(53.4, (20, 0, 4)), V3(T_FALL, (20, 0, 4)),
                  V3(T_FALL + 0.7, LIE_ROT['right_arm'], 'in')],
    'left_arm': [V3(0, (0, 0, 0)), V3(29.6, (0, 0, 0)), V3(30.2, (-115, -20, 25)), V3(33.4, (-115, -20, 25)),
                 V3(34.4, (10, 0, -8)), V3(52.5, (10, 0, -8)), V3(53.4, (20, 0, -4)), V3(T_FALL, (20, 0, -4)),
                 V3(T_FALL + 0.7, LIE_ROT['left_arm'], 'in')],
    'right_leg': [V3(0, (0, 0, 0)), V3(T_FALL, (0, 0, 0)), V3(T_FALL + 0.7, LIE_ROT['right_leg'], 'in')],
    'left_leg': [V3(0, (0, 0, 0)), V3(T_FALL, (0, 0, 0)), V3(T_FALL + 0.7, LIE_ROT['left_leg'], 'in')],
}
ph_limbs = {}
for p, keys in ph_rot.items():
    ph_limbs[p] = {'rot': keys, 'offset': [V3(0, (0, 0, 0)), V3(T_FALL, (0, 0, 0)),
                                           V3(T_FALL + 0.7, LIE_OFF[p], 'in')]}
# he falls back from his knees: the head (actor position) ends ~0.7 behind where his knees were
PH_DOWN = (PH[0], 0, PH[2] + 0.7)
past_harry = {'id': 'past_harry', 'type': 'player', 'name': 'Harry', 'skin': HARRY_SKIN, 'items': {'mainhand': WAND},
              'path': [K(0, PH), K(T_FALL, PH), K(T_FALL + 0.7, PH_DOWN, 'in')],
              'look': [Y(0, -90, 0), Y(14.6, -90, 0), Y(15.8, -180, 0)],
              'poses': [P(0, 'kneel', 1), P(T_FALL, 'flat', 12)],
              'limbs': ph_limbs,
              'actions': [A(T_WISP, 'cast', spell='patronus_wisp', target=[0, 3.5, 24.0]), A(30.0, 'hurt'),
                          A(T_KISS + 0.6, 'hurt')]}

# ---------------------------------------------------------------- future Harry and Hermione on the far bank
cast_yaw = yaw_to(FH_EDGE, CAST_TARGET)
fh_path = [K(0, FH0), K(T_STAND + 0.2, FH0), K(43.6, (-4.5, 0, -29.6), 'in_out'), K(45.4, FH_EDGE, 'out')]
fh_look = [Y(0, 0, 4), Y(36.0, 0, 4), Y(38.9, 0, 6), Y(39.6, 40, 6), Y(40.6, 40, 6), Y(41.6, 4, 8),
           Y(T_STAND, 4, 2), Y(45.4, cast_yaw, -4), Y(T_CAST, cast_yaw, -4),
           Y(58.8, cast_yaw, 0), Y(T_STAG_STOP, 6, 12), Y(T_BOW + 0.4, 6, 18), Y(T_STAG_GONE + 0.6, 2, 2),
           Y(74.8, 30, 4), Y(76.2, 4, 0)]
future_harry = {'id': 'future_harry', 'type': 'player', 'name': 'Harry', 'skin': HARRY_SKIN,
                'items': {'mainhand': WAND}, 'path': fh_path, 'look': fh_look,
                'poses': [P(45.8, 'wand_raised', 6), P(T_CAST - 0.05, 'wand_point', 2), P(T_STAG_STOP - 0.4, 'relax', 16)],
                'actions': [A(0, 'sneak'), A(T_STAND, 'stand'),
                            A(T_CAST, 'cast', spell='expecto_patronum', target=list(CAST_TARGET))]}
he_yaw_harry = yaw_to(HE0, FH0)
hermione = {'id': 'hermione', 'type': 'player', 'name': 'Hermione', 'skin': {'texture': 'hermione'},
            'path': [K(0, HE0), K(72.6, HE0), K(73.4, (-4.0, 0, -30.0), 'in'), K(73.9, (-4.0, 0, -28.4), 'linear'),
                     K(74.6, HE1, 'out')],
            'look': [Y(0, 6, 2), Y(37.2, 6, 2), Y(38.4, he_yaw_harry, 6), Y(41.8, he_yaw_harry, 6),
                     Y(43.0, 60, 0), Y(45.6, 30, -2), Y(T_CAST, 4, -2), Y(T_STAG_STOP, 10, 6),
                     Y(72.6, 30, 4), Y(73.4, 60, 0), Y(73.9, 20, 0), Y(74.6, 40, 2), Y(76.6, 6, 0)],
            'actions': [A(0, 'sneak'), A(43.0, 'stand')]}

# ---------------------------------------------------------------- the stag
def circle_pt(a, cx=0.5, cz=26.5, r=5.5):
    return (cx + r * math.sin(a), 0, cz - r * math.cos(a))
stag_pos = [(0.0, (-4.5, 0, -24.6), None), (T_STAG, (-4.5, 0, -24.6), None), (T_SHORE, (0.0, 0, 21.0), 'linear')]
n = 20
for k in range(1, n + 1):
    a = 2 * math.pi * k / n
    stag_pos.append((round(T_SHORE + (T_CIRCLE_END - T_SHORE) * k / n, 2), circle_pt(a), 'linear'))
stag_pos += [(59.2, (1.6, 0, 19.0), 'linear'), (T_STAG_STOP, (-4.3, 0, -24.0), 'out')]
stag_path = [K(t, p, e) for t, p, e in stag_pos]
# look: heading along the path, unwrapped, then the bow
stag_look = []
prev = None
for i, (t, p, e) in enumerate(stag_pos):
    if i < 2:
        yw = yaw_to(p, stag_pos[2][1])
    elif i + 1 < len(stag_pos):
        yw = yaw_to(stag_pos[i - 1][1], stag_pos[i + 1][1])
    else:
        yw = yaw_to(stag_pos[i - 1][1], p)
    yw = yw if prev is None else unwrap(prev, yw)
    prev = yw
    stag_look.append(Y(t, yw, 0, 'linear' if i > 1 else None))
stag_look += [Y(T_BOW, prev, 0), Y(T_BOW + 0.6, prev, 42), Y(T_BOW + 1.4, prev, 42), Y(T_STAG_GONE - 0.3, prev, 10)]
stag = {'id': 'stag', 'type': 'hogwartsmagic:patronus', 'path': stag_path, 'look': stag_look,
        'actions': [A(0, 'vanish'), A(T_STAG, 'appear'), A(T_STAG_GONE, 'vanish')]}

# ---------------------------------------------------------------- the Dementors
N_DEM = 24
KISS_HARRY = (PH[0], -0.4, PH[2] - 1.2)      # hovering in front of him (north), facing him
KISS_SIRIUS = (SI[0], -0.8, SI[2] + 1.0)    # over his head, from the south, facing north
dem_par = []
for i in range(N_DEM):
    f = rnd()
    dem_par.append({'phi': 2 * math.pi * i / N_DEM + rnd() * 0.2, 'f': f, 'g': rnd(),
                    'r0': 14 + 6 * f, 'y0': 22 + 9 * rnd(), 'r1': 8 + 3 * f, 'y1': 9 + 5 * rnd(),
                    'r2': 3.8 + 2.4 * f, 'y2': 3.4 + 4.2 * rnd(), 'v': 4.5 + 1.5 * rnd()})

def lerp(a, b, f):
    return a + (b - a) * f

def ring_state(dp, t):
    if t <= 6.0:
        return dp['r0'], dp['y0']
    if t <= 30.0:
        f = (t - 6.0) / 24.0
        f = f * f * (3 - 2 * f)
        return lerp(dp['r0'], dp['r1'], f), lerp(dp['y0'], dp['y1'], f)
    if t <= 35.0:
        f = (t - 30.0) / 5.0
        f = f * f * (3 - 2 * f)
        return lerp(dp['r1'], dp['r2'], f), lerp(dp['y1'], dp['y2'], f)
    return dp['r2'], dp['y2']

def ring_track(dp, t_end):
    """[(t, (x,y,z))] circling C counter-clockwise from above, angular speed v / r (at most 0.8 rad/s), keys 1 s apart then 0.75 s."""
    out = []
    th = dp['phi']
    t = 0.0
    step = 0.05
    nxt = 0.0
    while t <= t_end + 1e-6:
        r, y = ring_state(dp, t)
        if t >= nxt - 1e-6:
            bob = 0.35 * math.sin(t * 0.9 + dp['phi'] * 3)
            out.append((round(t, 2), (C[0] + r * math.cos(th), y + bob, C[2] + r * math.sin(th))))
            nxt = round(nxt + (1.0 if t < 29.5 else 0.75), 2)
        th -= min(dp['v'] / max(r, 2.0), 0.8) * step
        t = round(t + step, 2)
    return out

def track_at(trk, t):
    """Linear sample of a [(t, (x,y,z))] track (the paths are linear between ring keys)."""
    if t <= trk[0][0]:
        return trk[0][1]
    for (t0, a), (t1, b) in zip(trk, trk[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return tuple(a[c] + (b[c] - a[c]) * f for c in range(3))
    return trk[-1][1]

# the Dementors nearest past Harry and Sirius when the weak shield opens (the two kissers excepted)
MID = ((PH[0] + SI[0]) / 2, 1.0, (PH[2] + SI[2]) / 2)
near = sorted(range(2, N_DEM), key=lambda i: math.dist(track_at(ring_track(dem_par[i], T_REJOIN + 1), T_PUSH), MID))
PUSHED = set(near[:N_PUSHED])

PUSH_INFO = []
def push_track(trk, dp):
    """Replace T_PUSH..T_REJOIN of a ring track: driven back 4-8 blocks from Harry and up 1-2, held, then home.

    While the shield holds their circling slows to 0.3x (they hang there), so they rejoin the very same ring track
    a little behind where they were (LAG seconds), and keep that lag to the end.
    """
    back = 4.5 + 3.0 * dp['g']
    rise = 1.3 + 0.6 * dp['f']
    PUSH_INFO.append((back, rise))
    slow = 0.3
    lag = (1 - slow) * (T_HANG_END - T_PUSH)
    def tau(t):
        if t <= T_HANG_END:
            return T_PUSH + slow * (t - T_PUSH)
        return t - lag
    def amount(t):
        # 0 -> 1 over the first second (ease out), the second burst adds a little, eases home over the return
        if t <= T_PUSHED:
            f = (t - T_PUSH) / (T_PUSHED - T_PUSH)
            return 1 - (1 - f) ** 3
        if t <= T_HANG_END:
            f = (t - T_PUSHED) / (T_HANG_END - T_PUSHED)
            return 1 + 0.08 * math.sin(math.pi * f)
        f = (t - T_HANG_END) / (T_REJOIN - T_HANG_END)
        return 1 - f * f * (3 - 2 * f)
    keys = []
    n = 10
    for k in range(n + 1):
        t = round(T_PUSH + (T_REJOIN - T_PUSH) * k / n, 2)
        q = track_at(trk, tau(t))
        dx, dz = q[0] - PH[0], q[2] - PH[2]
        dl = math.hypot(dx, dz) or 1.0
        a = amount(t)
        keys.append((t, (q[0] + dx / dl * back * a, q[1] + rise * a, q[2] + dz / dl * back * a), None))
    after = [(t, track_at(trk, t - lag), None) for t, p in trk if t > T_REJOIN]
    return [(t, p, None) for t, p in trk if t < T_PUSH] + keys + after

dementors = []
for i, dp in enumerate(dem_par):
    did = 'dementor_%d' % (i + 1)
    kisser = {0: ('sirius', KISS_SIRIUS), 1: ('past_harry', KISS_HARRY)}.get(i)
    # the light reaches them at once (they are 4-6 from it); a small stagger by distance
    t_rec = round(T_LIGHT + 0.08 + 0.05 * (i % 6) + (0 if kisser else 0.1), 2)
    if kisser:
        trk = [k for k in ring_track(dp, 31.0)]
        trk.append((T_KISS - 0.2, KISS_SIRIUS if i == 0 else KISS_HARRY))
        kp = trk[-1][1]
        for tt in (37.0, 41.0, 45.0, 49.0):
            trk.append((tt, (kp[0], kp[1] + (0.12 if tt in (37.0, 45.0) else -0.05), kp[2])))
        trk.append((t_rec, kp))
    else:
        trk = ring_track(dp, t_rec)
        if trk[-1][0] < t_rec:
            r, y = ring_state(dp, t_rec)
            # continue the circle to t_rec
            th = math.atan2(trk[-1][1][2] - C[2], trk[-1][1][0] - C[0]) - min(dp['v'] / r, 0.8) * (t_rec - trk[-1][0])
            trk.append((t_rec, (C[0] + r * math.cos(th), trk[-1][1][1], C[2] + r * math.sin(th))))
    pushed = i in PUSHED
    eases = {}
    if pushed:
        pt = push_track(trk, dp)
        trk = [(t, p) for t, p, _ in pt]
        eases = {t: e for t, p, e in pt if e}
    p0 = trk[-1][1]
    dx, dz = p0[0] - C[0], p0[2] - C[2]
    dl = math.hypot(dx, dz) or 1.0
    dist = 20 + 6 * dp['g']
    tx, tz = p0[0] + dx / dl * dist, p0[2] + dz / dl * dist
    s = 1.0
    for v0, v1 in ((p0[0], tx), (p0[2], tz)):
        if abs(v1) > R - 2:
            s = min(s, (R - 2 - abs(v0)) / abs(v1 - v0))
    tx, tz = p0[0] + (tx - p0[0]) * s, p0[2] + (tz - p0[2]) * s
    t_gone = round(t_rec + 1.2 + 0.9 * dp['f'], 2)
    trk.append((round(t_rec + 2.4, 2), (tx, p0[1] + 14 + 8 * dp['g'], tz)))
    path = []
    for j, (t, p) in enumerate(trk):
        e = 'linear'
        if kisser and t == T_KISS - 0.2:
            e = 'in_out'
        if t in eases:
            e = eases[t]
        if j == len(trk) - 1:
            e = 'out'
        path.append(K(t, [round(c, 1) for c in p], e if j else None))
    d = {'id': did, 'type': 'hogwartsmagic:dementor', 'path': path,
         'anims': [{'t': T(0), 'name': 'GLIDE' if i % 3 else 'FLOAT'}],
         'actions': [A(t_gone, 'vanish')]}
    if kisser:
        victim = SI if i == 0 else PH
        look = []
        prev = None
        for j, (t, p) in enumerate(trk[:-1]):
            if t < T_KISS - 0.2:
                q = trk[j + 1][1]
                yw = yaw_to(p, q)
            else:
                yw = yaw_to(p, victim) if i == 1 else 180.0
            yw = yw if prev is None else unwrap(prev, yw)
            prev = yw
            look.append(Y(t, yw, 0 if t < T_KISS - 0.2 else 20, 'linear' if j else None))
        look.append(Y(round(t_rec + 2.4, 2), prev, -30))
        d['look'] = look
        d['anims'] = [{'t': T(0), 'name': 'GLIDE'}, {'t': T(T_KISS - 0.6), 'name': 'FLOAT', 'blend': 6}]
        d['anims'] += [{'t': T(tk), 'name': 'KISS', 'blend': 4 if tk == T_KISS else 0}
                       for tk in (T_KISS, T_KISS + 4, T_KISS + 8, T_KISS + 12, T_KISS + 16)]
    if pushed:
        # flinch from the weak shield, facing Harry while driven back, then circle on
        base = d['anims'][0]['name']
        d['anims'] += [{'t': T(T_PUSH), 'name': 'RECOIL', 'blend': 2},
                       {'t': T(round(T_PUSH + 1.4, 2)), 'name': base, 'blend': 4}]
        look = []
        prev = None
        for j, (t, p) in enumerate(trk[:-1]):
            if T_PUSH <= t <= T_HANG_END:
                yw = yaw_to(p, PH)
            else:
                yw = yaw_to(p, trk[j + 1][1])
            yw = yw if prev is None else unwrap(prev, yw)
            prev = yw
            # the circling turns at a steady rate, so away from the push every fifth key is enough
            if j and j % 5 and not (T_PUSH - 1.5 <= t <= T_REJOIN + 1.5) and j < len(trk) - 3:
                continue
            look.append(Y(t, round(yw, 1), 0, 'linear' if j else None))
        look.append(Y(trk[-1][0], prev, -30))
        d['look'] = look
    d['anims'].append({'t': T(t_rec), 'name': 'RECOIL', 'blend': 2})
    d['anims'].append({'t': T(round(t_rec + 1.4, 2)), 'name': 'GLIDE', 'blend': 4})
    dementors.append(d)

# ---------------------------------------------------------------- events
# soundtrack: files carry their own fades; cues overlap ~2 s to crossfade (MemoryAudio, non-positional)
def music(t, name):
    ev(t, 'music', 'hogwartsmagic:pensieve.music.' + name, volume=1.0)
music(0.0, 'lake_dementors')
music(40.5, 'lake_patronus_light')
music(69.5, 'lake_window')
snd(0.0, 'minecraft:ambient.cave', volume=0.45, pitch=0.7)
snd(0.5, 'minecraft:item.elytra.flying', volume=0.18, pitch=0.5)
for tt, at in ((3.0, (4, 18, 26)), (9.5, (-6, 16, 22)), (14.0, (6, 12, 27)), (18.5, (-3, 9, 25)),
               (23.0, (5, 7, 27)), (31.0, (0, 5, 26))):
    snd(tt, 'hogwartsmagic:dementor.ambient', at=at, volume=1.4, pitch=0.85)
snd(16.0, 'minecraft:item.elytra.flying', at=(0, 8, 26), volume=0.35, pitch=0.45)
snd(30.0, 'minecraft:item.elytra.flying', at=(0, 5, 26), volume=0.45, pitch=0.5)
snd(T_KISS, 'hogwartsmagic:dementor.suck', at=PH, volume=1.3, pitch=0.9)
snd(T_KISS + 0.4, 'hogwartsmagic:dementor.kiss', at=SI, volume=1.2, pitch=0.85)
snd(T_KISS + 4.2, 'hogwartsmagic:dementor.suck', at=PH, volume=1.0, pitch=0.8)
snd(50.0, 'hogwartsmagic:dementor.suck', at=PH, volume=0.9, pitch=0.8)
# mist low on the water, all film long
t, i = 0.0, 0
while t < LENGTH - 0.4:
    cx, cz = ((0, 0), (-9, 8), (9, -8), (6, 10), (-8, -9))[i % 5]
    part(t, 'minecraft:cloud', (cx, 0, cz), 12, (12, 0.1, 12))
    t = round(t + 0.8, 2)
    i += 1
# cold over the near shore while they gather
t = 16.0
while t < T_LIGHT:
    part(t, 'minecraft:snowflake', (C[0], 3, C[2]), 25, (6, 3, 4))
    t = round(t + 0.8, 2)
# the weak Patronus: the Dementors shriek back from the shield, a second burst, then it breaks up into falling motes
snd(T_PUSH + 0.1, 'hogwartsmagic:dementor.shriek', at=(0, 8, 24), volume=0.9, pitch=1.1)
part(T_BURST2, 'minecraft:end_rod', (0, 3, 24), 24, (1.0, 1.0, 0.3))
part(T_BURST2, 'minecraft:glow', (0, 3, 24), 12, (0.8, 0.8, 0.3))
snd(T_BURST2, 'hogwartsmagic:patronus.shield_repel', at=(0, 2, 26), volume=0.6, pitch=1.3)
part(T_WISP + 2.6, 'minecraft:white_ash', (0, 3, 24), 30, (1.2, 1.0, 0.4))
part(T_WISP + 3.2, 'minecraft:white_ash', (0, 1, 26), 20, (0.6, 0.6, 0.6))
# the Kiss: pale light drawn up out of their mouths into the hoods
t = T_KISS + 0.2
k = 0
while t < T_LIGHT - 0.1:
    part(t, 'minecraft:end_rod', (0, 1, 27), 2, (0.15, 0.5, 0.35))
    part(t, 'minecraft:white_ash', (0, 1, 27), 6, (0.2, 0.55, 0.4))
    part(t + 0.15, 'minecraft:end_rod', (2, 1, 29), 2, (0.25, 0.6, 0.25))
    part(t + 0.15, 'minecraft:white_ash', (2, 1, 29), 6, (0.3, 0.6, 0.3))
    t = round(t + 0.5, 2)
    k += 1
# the cast: glow at the far bank as it leaves the wand, and the stag bursting out of the light
part(T_STAG, 'minecraft:end_rod', (-4, 1, -25), 60, (0.6, 0.8, 0.6))
part(T_STAG, 'minecraft:flash', (-4, 1, -25), 2, (0.2, 0.2, 0.2))
part(T_STAG + 0.1, 'minecraft:glow', (-4, 1, -25), 30, (0.8, 0.8, 0.8))
snd(T_STAG + 0.1, 'hogwartsmagic:patronus.ambient', at=(-4, 0, -25), volume=1.2, pitch=1.0)
# the stag leaves a trail of silver on the water as it runs
t = T_STAG + 0.3
while t < T_SHORE:
    f = (t - T_STAG) / (T_SHORE - T_STAG)
    x, z = -4.5 + 4.5 * f, -24.6 + 45.6 * f
    part(t, 'minecraft:end_rod', (x, 0, z), 6, (0.6, 0.3, 0.6))
    part(t, 'minecraft:splash', (x, 0, z), 10, (0.6, 0.1, 0.6))
    t = round(t + 0.25, 2)
# the light: the burst, the Dementors flung away, then pulses while the stag circles
effect(T_LIGHT, 'patronus_light', LIGHT_AT, 80)
effect(54.6, 'patronus_light', LIGHT_AT, 60)
effect(56.6, 'patronus_light', LIGHT_AT, 60)
snd(T_LIGHT + 0.1, 'hogwartsmagic:patronus.shield_repel', at=LIGHT_AT, volume=1.4, pitch=1.0)
snd(T_LIGHT + 0.25, 'hogwartsmagic:dementor.shriek', at=(0, 5, 26), volume=1.5, pitch=0.9)
snd(T_LIGHT + 0.7, 'hogwartsmagic:dementor.shriek', at=(4, 9, 24), volume=1.2, pitch=0.75)
snd(T_LIGHT + 1.3, 'hogwartsmagic:dementor.hurt', at=(-5, 12, 28), volume=1.0, pitch=0.7)
t = T_SHORE
while t < T_CIRCLE_END:
    a = 2 * math.pi * (t - T_SHORE) / (T_CIRCLE_END - T_SHORE)
    p = circle_pt(a)
    part(t, 'minecraft:end_rod', p, 5, (0.5, 0.4, 0.5))
    t = round(t + 0.3, 2)
# the bow and the dissolve
effect(T_STAG_GONE, 'patronus_light', (-4, 0, -24), 30)
part(T_STAG_GONE, 'minecraft:end_rod', (-4, 1, -24), 90, (0.7, 0.7, 0.9))
part(T_STAG_GONE, 'minecraft:glow', (-4, 1, -24), 40, (0.7, 0.6, 0.9))
part(T_STAG_GONE + 0.4, 'minecraft:end_rod', (-4, 2, -24), 40, (1.0, 1.0, 1.0))
snd(T_STAG_GONE, 'minecraft:block.amethyst_block.chime', at=(-4, 0, -24), volume=1.0, pitch=1.4)
snd(T_FALL + 0.6, 'minecraft:block.gravel.fall', at=PH, volume=0.8, pitch=0.8)
snd(78.5, 'minecraft:ambient.cave', volume=0.25, pitch=1.2)

# ---------------------------------------------------------------- words (paraphrase; only the incantation is canon)
# Each spoken line plays its Fish Audio take (assets/.../sounds/pensieve/voice, non-positional) with the text.
def voiced(t, sound):
    ev(t, 'voice', 'hogwartsmagic:pensieve.voice.' + sound, volume=1.0)
narrate(1.0, 'The lake, that night. The same hour, lived a second time.', 4.5); voiced(1.0, 'lake_narration')
line(25.0, 'past_harry', 'Expecto... Expecto Patronum...'); voiced(25.0, 'lake_harry_wisp')
line(38.8, 'hermione', "Harry, they're taking them both!"); voiced(38.8, 'lake_hermione')
line(40.8, 'future_harry', "No one's coming. That figure across the lake... it was me, all along."); voiced(40.8, 'lake_harry_me')
line(45.5, 'future_harry', 'Expecto Patronum!'); voiced(45.5, 'lake_harry_cast')  # ends as the cast fires at 46.9s
line(74.8, 'future_harry', 'It was never my dad out there. It was me. And now I know I can.'); voiced(74.8, 'lake_harry_knew')

actors = [past_harry, sirius, future_harry, hermione, stag] + dementors

# ---------------------------------------------------------------- camera
EYE = 1.62
def actor(aid):
    return next(a for a in actors if a['id'] == aid)
def tval(s):
    return float(s[:-1]) if isinstance(s, str) else s / 20.0
def ease_f(f, e):
    if e == 'linear': return f
    if e == 'in': return f * f * f
    if e == 'out': return 1 - (1 - f) ** 3
    return 4 * f * f * f if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2
def sample(keys, t, field='pos'):
    ks = [(tval(k['t']), k[field] if field != 'yawpitch' else [k['yaw'], k['pitch']], k.get('ease', 'in_out'))
          for k in keys]
    if t <= ks[0][0]: return ks[0][1]
    for (t0, a, _), (t1, b, e) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else ease_f((t - t0) / (t1 - t0), e)
            return [a[i] + (b[i] - a[i]) * f for i in range(len(a))]
    return ks[-1][1]

FH, HE, PHA, ST = 'actor:future_harry', 'actor:hermione', 'actor:past_harry', 'actor:stag'
FAR_PAIR = (-3.9, 1.05, -30.6)
# S1 establishing: high from the south-east over the black lake, specks circling, Hogwarts lit beyond it
shot(0.0, (30, 17, 32), (-8, 9, -60), 82, cut=False)
shot(6.0, (25, 14, 26), (-8, 8, -62), 78, cut=False)
# S2 far bank two-shot, low over the water, crouched behind the bushes
shot(6.0, (-3.6, 1.35, -25.4), FAR_PAIR, 55)
shot(11.0, (-3.7, 1.3, -26.4), FAR_PAIR, 50, cut=False)
# S3 reverse over their shoulders: across the water, the vortex over the near shore
shot(11.0, (-3.4, 1.9, -33.6), (0.5, 7, 26), 40)
shot(16.0, (-3.3, 1.9, -33.2), (0.5, 5, 26), 36, cut=False)
# S4 near shore, low from the water's edge: Harry by Sirius, the Dementors spiralling down
shot(16.0, (5.6, 0.7, 22.6), (0.6, 2.5, 28.0), 84, roll=4)
shot(21.0, (5.0, 0.8, 23.2), (0.5, 6.0, 27.0), 86, cut=False, roll=5)
# S5 the lake freezes under the swarm: high over the water from behind the shore, the castle across the lake
shot(21.0, (2.0, 8.5, 35.0), (0, 1.5, -6), 76)
shot(24.4, (2.0, 9.6, 34.2), (0, 1.0, -8), 74, cut=False)
# S6a side on: he turns north and raises the wand
shot(24.4, (-3.6, 1.2, 28.6), (0.0, 1.2, 27.6), 52)
shot(26.4, (-3.5, 1.2, 28.3), (0.0, 1.4, 27.6), 50, cut=False)
# S6b cut on the cast: low in front, looking up past him at the swarm
shot(26.4, (-1.2, 0.45, 25.4), (0.0, 1.3, 28.0), 50, roll=-3)
shot(29.6, (-1.1, 0.5, 25.6), (0.0, 1.5, 28.0), 48, cut=False, roll=-4)
# S7 worm's-eye up the vortex
shot(29.6, (4.8, 0.4, 30.4), (0.5, 9, 25.5), 92, roll=6)
shot(33.6, (4.6, 0.45, 30.0), (0.5, 5, 26), 90, cut=False, roll=7)
# S8 the Kiss: close side profile, sliding from Harry to Sirius
shot(33.6, (-3.2, 1.0, 27.4), (0.0, 1.2, 27.4), 50, roll=-5)
shot(36.2, (-3.0, 1.1, 27.9), (0.0, 1.3, 27.3), 48, cut=False, roll=-5)
shot(38.4, (-2.4, 1.3, 30.4), (1.8, 0.8, 29.6), 46, cut=False, roll=-4)
# S9 far bank: close on future Harry
shot(38.4, (-4.6, 1.25, -28.0), (-5.4, 1.15, -30.5), 46)
shot(42.6, (-4.7, 1.25, -28.3), (-5.4, 1.2, -30.5), 44, cut=False)
# S10 tracking from the east as he steps out to the water
shot(42.6, (1.4, 1.5, -29.6), (-5.0, 1.4, -30.0), 55)
shot(46.6, (0.6, 1.5, -26.4), (-4.5, 1.6, -25.8), 55, cut=False)
# S11 cut on the cast: low in front, on the water; the stag charges past the lens
shot(46.6, (-2.2, 0.5, -22.4), (-4.5, 1.9, -25.8), 60)
shot(49.0, (-2.1, 0.55, -22.6), (-4.4, 1.6, -25.4), 62, cut=False)
# S12 tracking beside the galloping stag (its path is linear, so the camera is too)
def stag_at(t):
    return sample(stag_path, t)
s0, s1 = stag_at(49.0), stag_at(52.2)
shot(49.0, (s0[0] + 3.4, 0.9, s0[2] - 1.0), ST, 66)
shot(52.2, (s1[0] + 3.4, 0.9, s1[2] - 1.0), ST, 66, cut=False, ease='linear')
# S13 the light: wide and low from the lake, looking up at the shore and the sky
shot(52.2, (-10.0, 2.0, 14.0), (0.5, 6, 27), 90)
shot(57.2, (-11.0, 2.6, 12.6), (0.5, 9, 27), 92, cut=False)
# S14 close on past Harry, fading, lifting his head
shot(57.2, (-1.4, 1.0, 25.6), (0.0, 1.05, 27.8), 44)
shot(61.0, (-1.3, 1.0, 25.9), (0.0, 1.05, 27.8), 42, cut=False)
# S15 his eyes: across the lake, the stag coming back over the water, a figure on the far bank
shot(61.0, (-0.1, 1.05, 27.0), (-3.0, 1.0, -25.0), 30, roll=3)
shot(65.6, (-0.1, 1.0, 26.9), (-4.0, 1.2, -25.6), 28, cut=False, roll=4)
# S16 he collapses beside Sirius
shot(65.6, (3.4, 2.8, 30.6), (0.6, 0.3, 28.4), 50)
shot(69.0, (3.2, 3.0, 30.4), (0.8, 0.2, 28.6), 48, cut=False)
# S17 far bank profile: the stag stops before him, bows, dissolves
shot(69.0, (1.4, 1.2, -25.2), (-4.4, 1.2, -25.0), 52)
shot(73.4, (1.2, 1.2, -25.4), (-4.4, 1.3, -25.4), 50, cut=False)
# S18 profile two-shot from the east: Hermione at his side, his quiet line
shot(73.4, (0.6, 1.5, -26.6), (-3.9, 1.5, -26.4), 52)
shot(78.0, (0.3, 1.5, -26.5), (-3.9, 1.5, -26.4), 50, cut=False)
# S19 the last push-in on their faces, fov narrowing, to the fade; low, so the castle rises behind them
shot(78.0, (-3.9, 1.3, -23.4), (-3.9, 1.75, -26.4), 50)
shot(LENGTH, (-3.9, 1.35, -24.6), (-3.9, 1.7, -26.4), 40, cut=False)

scene = {
    'title': 'The Memory of the Lake',
    'length': T(LENGTH),
    'radius': SCENE_R,
    'fog_far': FOG_FAR,
    'set': {
        'schematics': schematics,
        'fills': fills,
        'disks': disks,
        'features': features,
        'time_of_day': 18000,
        'build_ticks': 80,
    },
    'poses': poses,
    'actors': actors,
    'events': sorted(events, key=lambda e: float(e['t'][:-1])),
    'words': words,
    'camera': cam,
}

# ---------------------------------------------------------------- checks
problems = []
for a in actors:
    for field in ('path', 'look', 'poses', 'actions', 'anims'):
        ts = [tval(k['t']) for k in a.get(field, [])]
        if ts != sorted(ts):
            problems.append('%s %s not sorted: %s' % (a['id'], field, ts))
        if any(x < 0 or x > LENGTH for x in ts):
            problems.append('%s %s time outside the film' % (a['id'], field))
    for pn, tr in a.get('limbs', {}).items():
        for kk in ('rot', 'offset'):
            ts = [tval(k['t']) for k in tr.get(kk, [])]
            if ts != sorted(ts) or any(x < 0 or x > LENGTH for x in ts):
                problems.append('%s limbs %s %s times bad' % (a['id'], pn, kk))
    ys = [k['yaw'] for k in a.get('look', [])]
    for y0, y1 in zip(ys, ys[1:]):
        if abs(y1 - y0) > 180:
            problems.append('%s yaw jumps %s -> %s' % (a['id'], y0, y1))
for lst, name in ((events, 'events'), (words, 'words'), (cam, 'camera')):
    for k in lst:
        if not 0 <= tval(k['t']) <= LENGTH:
            problems.append('%s time %s outside the film' % (name, k['t']))
ct = [tval(k['t']) for k in cam]
if ct != sorted(ct):
    problems.append('camera not sorted')
ids = [a['id'] for a in actors]
if len(ids) != len(set(ids)):
    problems.append('duplicate actor ids')

# walkers: feet never inside a solid block, never in the water (the stag runs on it, so it is exempt)
for aid in ('past_harry', 'future_harry', 'hermione', 'stag'):
    a = actor(aid)
    for i in range(int(LENGTH * 20) + 1):
        t = i / 20
        p = sample(a['path'], t)
        if abs(p[0]) > R - 1 or abs(p[2]) > R - 1:
            problems.append('%s outside the radius at %.2f' % (aid, t)); break
        hit = None
        for dx, dz in ((-0.3, -0.3), (0.3, -0.3), (-0.3, 0.3), (0.3, 0.3)):
            for hy in (0.1, 1.0):
                b = solid_at(p[0] + dx, p[1] + hy, p[2] + dz)
                if b and not (aid == 'stag' and t < T_STAG):
                    hit = b
            if aid != 'stag' and world.get((math.floor(p[0] + dx), -1, math.floor(p[2] + dz))) == 'minecraft:water':
                hit = 'water'
        if hit:
            problems.append('%s in %s at t=%.2f pos=%s' % (aid, hit, t, [round(v, 2) for v in p])); break
# dementors stay inside the loaded region and above the ground except the two kissers' hems
for d in dementors:
    for i in range(int(LENGTH * 20) + 1):
        t = i / 20
        p = sample(d['path'], t)
        if abs(p[0]) > R - 1 or abs(p[2]) > R - 1:
            problems.append('%s outside the radius at %.2f %s' % (d['id'], t, p)); break
        if p[1] < -1.0:
            problems.append('%s too low at %.2f' % (d['id'], t)); break
# the stag's circle stays clear of Harry and Sirius
for k in range(40):
    p = circle_pt(2 * math.pi * k / 40)
    for who in (PH, SI):
        if math.hypot(p[0] - who[0], p[2] - who[2]) < 1.5:
            problems.append('stag circle runs over %s' % (who,))
# the cast reaches out over the lake toward the shore and the stag meets future Harry
if abs(stag_path[-1]['pos'][2] - FH_EDGE[2]) > 2.2:
    problems.append('stag stops too far from future Harry')

def target(look, t):
    if isinstance(look, str):
        p = sample(actor(look.split(':')[1])['path'], t)
        return [p[0], p[1] + EYE, p[2]]
    return look
def los(pos, tgt, t, label):
    n = int(math.dist(pos, tgt) * 6)
    for m in range(1, n):
        q = [pos[c] + (tgt[c] - pos[c]) * m / n for c in range(3)]
        if math.dist(q, tgt) < 0.8:
            break
        b = solid_at(*q)
        if b:
            problems.append('%s: view blocked by %s at %s' % (label, b, [round(v, 1) for v in q]))
            return
def cam_ok(pos, label):
    if solid_at(*pos):
        problems.append('%s inside %s' % (label, solid_at(*pos)))
    if world.get((math.floor(pos[0]), -1, math.floor(pos[2]))) == 'minecraft:water' and pos[1] < 0.3:
        problems.append('%s at the waterline %s' % (label, pos))
for i, k in enumerate(cam):
    t = tval(k['t'])
    cam_ok(k['pos'], 'camera %d at %s' % (i, k['t']))
    los(k['pos'], target(k['look_at'], t), t, 'camera %d at %s' % (i, k['t']))
for a, b2 in zip(cam, cam[1:]):
    if b2.get('cut'):
        continue
    t0, t1 = tval(a['t']), tval(b2['t'])
    for j in range(1, 10):
        f = j / 10
        t = t0 + (t1 - t0) * f
        pos = [a['pos'][c] + (b2['pos'][c] - a['pos'][c]) * f for c in range(3)]
        cam_ok(pos, 'camera %s..%s' % (a['t'], b2['t']))
        ta, tb = target(a['look_at'], t), target(b2['look_at'], t)
        los(pos, [ta[c] + (tb[c] - ta[c]) * f for c in range(3)], t, 'camera %s..%s t=%.1f' % (a['t'], b2['t'], t))
# no Dementor drifts through the lens (body: a 1.2-wide column from its feet to 3 blocks up)
def cam_at(t):
    prev_k = None
    for a, b2 in zip(cam, cam[1:]):
        t0, t1 = tval(a['t']), tval(b2['t'])
        if t0 <= t < t1:
            if b2.get('cut'):
                return a['pos']
            f = ease_f((t - t0) / (t1 - t0), b2.get('ease', 'in_out'))
            return [a['pos'][c] + (b2['pos'][c] - a['pos'][c]) * f for c in range(3)]
    return cam[-1]['pos']
lens_hits = set()
for i in range(int(LENGTH * 10)):
    t = i / 10
    pc = cam_at(t)
    for d in dementors:
        vt = next(tval(a['t']) for a in d['actions'] if a['do'] == 'vanish')
        if t >= vt:
            continue
        p = sample(d['path'], t)
        if math.hypot(pc[0] - p[0], pc[2] - p[2]) < 1.3 and p[1] - 0.3 < pc[1] < p[1] + 3.3:
            lens_hits.add((d['id'], round(t)))
if lens_hits:
    problems.append('dementors through the lens: %s' % sorted(lens_hits)[:8])
# S12 keeps the stag beside the lens
for t in (49.0, 50.6, 52.2):
    pc = sample([k for k in cam if 49.0 <= tval(k['t']) <= 52.2 and k['look_at'] == ST], t)
    ps = stag_at(t)
    dd = math.hypot(pc[0] - ps[0], pc[2] - ps[2])
    if not 2.5 < dd < 5:
        problems.append('tracking shot drifts off the stag at %.1f (%.1f)' % (t, dd))
# lightning-like effects: patronus_light and grey_edges only, with durations
for e in events:
    if e['kind'] == 'particles' and e['count'] > 500:
        problems.append('too many particles %s' % e)
# extents: the lake within R, the castle's fills and its whole rotated footprint within the scene radius
for i, f in enumerate(fills):
    lim = R if i < N_LAKE_FILLS else SCENE_R
    for v in (f['from'], f['to']):
        if abs(v[0]) > lim or abs(v[2]) > lim:
            problems.append('fill beyond radius %s' % f)
for x, z in ((CASTLE_AT[0], CASTLE_AT[2]), (CASTLE_AT[0] + CFX - 1, CASTLE_AT[2] + CFZ - 1)):
    if abs(x) > SCENE_R or abs(z) > SCENE_R:
        problems.append('castle footprint cropped at %s' % ((x, z),))
if not 100 <= math.hypot(CASTLE_AT[0] + CFX / 2, CASTLE_AT[2] + CFZ / 2) <= 150:
    problems.append('castle not 100-150 from the origin')
if not 25 <= len(lit) <= 60:
    problems.append('%d lit windows (want a scattering, 25-60)' % len(lit))
if CASTLE_AT[1] > SHELF_Y + 1:
    problems.append('castle base above the shelf: it would float')
for d in disks:
    c = d['center']
    if abs(c[0]) + d['radius'] > R or abs(c[2]) + d['radius'] > R:
        problems.append('disk beyond radius %s' % d)
for x, z in TREES:
    if abs(x) + 4 > R or abs(z) + 4 > R:
        problems.append('tree crown may be cropped at %s' % ((x, z),))
for ft in features:
    x, y, z = ft['at']
    if world.get((x, y - 1, z)) != 'minecraft:grass_block' or world.get((x, y, z)) is not None:
        problems.append('tree not planted on grass at %s (below %s, at %s)' % (ft['at'], world.get((x, y - 1, z)),
                                                                                world.get((x, y, z))))
for (x, y, z), b in world.items():
    if b == 'minecraft:sugar_cane' and y == 0:
        if not any(world.get((x + dx, -1, z + dz)) == 'minecraft:water' for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            problems.append('reed not beside water at %s' % ((x, z),))
# ice only ever replaces lake surface water
for e in events:
    if e['kind'] == 'block' and world.get(tuple(e['at'])) != 'minecraft:water':
        problems.append('block event not on water: %s' % e)
# shots, words
cuts = [0.0] + [tval(k['t']) for k in cam if k.get('cut')] + [LENGTH]
lens = [round(b2 - a, 2) for a, b2 in zip(cuts, cuts[1:])]
print('shots', len(lens), lens, 'total', round(sum(lens), 2))
for l in lens:
    if l < 2 or l > 6:
        problems.append('shot length %s' % l)
wt = sorted(tval(w['t']) for w in words)
for a, b2 in zip(wt, wt[1:]):
    if b2 - a < 2:
        problems.append('lines closer than 2 s at %.1f' % a)
for w in words:
    end = tval(w['t']) + (tval(w['duration']) if 'duration' in w else (4.0 if w.get('narration') else 8.0))
    if end > LENGTH:
        problems.append('line at %s runs past the end' % w['t'])
def walk(o):
    if isinstance(o, dict):
        for v in o.values(): walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)
    elif isinstance(o, float):
        if abs(o * 100 - round(o * 100)) > 1e-6:
            problems.append('float noise %r' % o)
walk(scene)
problems += physics_problems(world, R)
n_ice = sum(1 for e in events if e['kind'] == 'block')
print('actors', len(actors), '(dementors %d)' % len(dementors), 'events', len(events), 'ice', n_ice,
      'fills', len(fills), 'disks', len(disks), 'trees', len(TREES), 'reeds', len(reeds))
# the set build: every block SetBuilder places (features are extra), at the default pensieveBlocksPerTick
set_blocks = sum(1 for (x, y, z) in world if abs(x) <= SCENE_R and abs(z) <= SCENE_R)
print('castle: %d blocks, %d facade windows, %d lit (%d cells); set %d blocks = %d ticks at 4000/tick'
      % (len(castle), len(windows), len(lit), sum(len(w) for w in lit), set_blocks, -(-set_blocks // 4000)))
# the weak shield's push: 4-8 back off their circle, 1-2 up, and a believable glide speed throughout
for d, (back, rise) in zip([d for d in dementors if int(d['id'].split('_')[1]) - 1 in PUSHED], PUSH_INFO):
    a, b2 = sample(d['path'], T_PUSH), sample(d['path'], T_PUSHED)
    def top_speed(t0, t1):
        return max(math.dist(sample(d['path'], t0 + k / 20), sample(d['path'], t0 + (k + 1) / 20)) * 20
                   for k in range(int((t1 - t0) * 20)))
    top = top_speed(T_HANG_END, T_REJOIN + 0.5)
    print('pushed %s: %.1f back, %.1f up (%.1f up overall), from %.1f away; flung at %.1f b/s, glides back at %.1f b/s'
          % (d['id'], back, rise, b2[1] - a[1], math.dist(a, (PH[0], 1.0, PH[2])), top_speed(T_PUSH, T_PUSHED), top))
    if not (4 <= back <= 8 and 1 <= rise <= 2 and b2[1] - a[1] >= 0.8):
        problems.append('%s push out of range' % d['id'])
    if top > 12:
        problems.append('%s glides back too fast (%.1f b/s)' % (d['id'], top))
print('\n'.join(problems) if problems else 'NO PROBLEMS')

txt = write_scene(scene, 'lake_patronus')
print('chars', len(txt))
if len(txt) > 252000:
    print('PROBLEM: scene JSON too big (%d chars, cap 262144)' % len(txt))
