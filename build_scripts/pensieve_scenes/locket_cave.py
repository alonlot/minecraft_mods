"""Generates data/hogwartsmagic/memories/locket_cave.json and checks it against the real template.

Usage (from the repo root): python build_scripts/pensieve_scenes/locket_cave.py

Scene frame: scene = template local + (-32, -23, -32). So the template's walk floor (local Y 22, walk 23)
is scene y -1 (actors stand at y 0), the lake/sea water top block is scene y -2, the basin is block (0,0,8).
"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import template_grid, write_scene, unsupported_gravity, physics_problems

grid = template_grid('locket_cave')
OX, OY, OZ = -32, -23, -32
R = 64

def L2S(x, y, z):
    return (x + OX, y + OY, z + OZ)

# ---------------------------------------------------------------- the set
tpl = {}   # scene cell -> block (template, non-air only: SetBuilder skips air)
tpl_air = set()
for (x, y, z), n in grid.items():
    s = L2S(x, y, z)
    if n == 'minecraft:air':
        tpl_air.add(s)
    else:
        tpl[s] = n.split('{')[0]
col_top = {}
for (x, y, z), n in tpl.items():
    if n != 'minecraft:water':
        col_top[(x, z)] = max(col_top.get((x, z), -999), y)
fp_cols = {(x + OX, z + OZ) for (x, y, z) in grid}   # template columns that hold anything

SEABED = -8          # local 15
WATER_LO, WATER_HI = -7, -2   # local 16..21

fills = []
def fill(block, a, b):
    fills.append({'block': block, 'from': list(a), 'to': list(b)})

# 1. the sea outside the template footprint (x -32..31, z -32..31): three big boxes, seabed + water
for (x0, z0, x1, z1) in ((-R, -R, R, -33), (-R, -32, -33, R), (32, -32, R, R)):
    fill('minecraft:gravel', (x0, SEABED, z0), (x1, SEABED, z1))
    fill('minecraft:water', (x0, WATER_LO, z0), (x1, WATER_HI, z1))

# 2. the sea inside the footprint, only where the template has no rock: a greedy box cover
need = {}   # (x, z) -> lowest water y (seabed one below when the column is empty)
for x in range(-32, 32):
    for z in range(-32, 32):
        t = col_top.get((x, z))
        if t is None:
            need[(x, z)] = WATER_LO
        elif t < WATER_HI:
            need[(x, z)] = max(t + 1, WATER_LO)
cells = set()
for (x, z), lo in need.items():
    for y in range(lo, WATER_HI + 1):
        if (x, y, z) not in tpl:
            cells.add((x, y, z))
bed = {(x, SEABED, z) for (x, z) in need if col_top.get((x, z)) is None}
for (x, z), t in col_top.items():   # low rock below the seabed: top it up to the seabed
    if -32 <= x < 32 and -32 <= z < 32 and t < SEABED:
        bed |= {(x, y, z) for y in range(t + 1, SEABED + 1) if (x, y, z) not in tpl}

def greedy(cells):
    cells = set(cells)
    boxes = []
    for c in sorted(cells, key=lambda c: (c[1], c[2], c[0])):
        if c not in cells:
            continue
        x0, y0, z0 = c
        x1 = x0
        while (x1 + 1, y0, z0) in cells:
            x1 += 1
        z1 = z0
        while all((x, y0, z1 + 1) in cells for x in range(x0, x1 + 1)):
            z1 += 1
        y1 = y0
        while all((x, y1 + 1, z) in cells for x in range(x0, x1 + 1) for z in range(z0, z1 + 1)):
            y1 += 1
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    cells.discard((x, y, z))
        boxes.append(((x0, y0, z0), (x1, y1, z1)))
    return boxes

for a, b in greedy(bed):
    fill('minecraft:gravel', a, b)
for a, b in greedy(cells):
    fill('minecraft:water', a, b)

# 3. the mainland: a stony shore south of the headland and flanks either side (all outside the footprint)
fill('minecraft:deepslate', (-R, SEABED, 32), (R, 1, R))
fill('minecraft:tuff', (-R, 2, 40), (R, 4, R))
fill('minecraft:deepslate', (-R, SEABED, -1), (-33, 0, 31))
fill('minecraft:deepslate', (32, SEABED, -1), (R, -1, 31))
# ragged shore: low rock shelves pushing into the sea off the flanks
for (x0, z0, x1, z1, top) in ((-58, -5, -50, -2, -1), (-46, -4, -40, -2, -1), (-38, -3, -34, -2, 0),
                              (36, -4, 41, -2, -1), (47, -6, 55, -2, -1), (58, -3, 63, -2, -2)):
    fill('minecraft:deepslate', (x0, SEABED, z0), (x1, top, z1))
# sea stacks off the point, for the establishing shot
fill('minecraft:deepslate', (-15, SEABED, -46), (-13, 4, -44))
fill('minecraft:tuff', (-14, 5, -45), (-14, 7, -45))
fill('minecraft:deepslate', (11, SEABED, -52), (12, 1, -51))
fill('minecraft:deepslate', (-30, SEABED, -57), (-28, -1, -55))

# 4. an invisible rim so the sea never flows out of the set
fill('minecraft:barrier', (-R, SEABED, -R), (R, WATER_HI, -R))
fill('minecraft:barrier', (-R, SEABED, -R), (-R, WATER_HI, 0))
fill('minecraft:barrier', (R, SEABED, -R), (R, WATER_HI, 0))

# 5. a faint glow down the top shaft, so the swim tunnel and the pool are not black
SHAFT_LIGHT = (0, -1, -28)
fill('minecraft:light', SHAFT_LIGHT, SHAFT_LIGHT)

# the world model, for the checks below
world = dict(tpl)
for f in fills:
    a, b = f['from'], f['to']
    for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
        if abs(x) > R:
            continue
        for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
            for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                if abs(z) <= R:
                    world[(x, y, z)] = f['block']
OPEN = {'minecraft:water', 'minecraft:light', 'minecraft:air', 'minecraft:chain', 'minecraft:barrier'}

# 6. bedrock under the seabed: gravel over nothing falls the moment anything updates it, and the sea drains
#    after it into the void. Every unsupported gravity cell gets a deepslate cell under it (greedy boxes).
support = {(x, y - 1, z) for (x, y, z) in unsupported_gravity(world)}
for a, b in greedy(support):
    fill('minecraft:deepslate', a, b)
for c in support:
    world[c] = 'minecraft:deepslate'

# ---------------------------------------------------------------- helpers
def T(s):
    return '%gs' % round(s, 2)

events, words, cam = [], [], []
def ev(t, kind, id_, **kw):
    e = {'t': T(t), 'kind': kind, 'id': id_}
    e.update(kw)
    events.append(e)
def snd(t, id_, at=None, volume=None, pitch=None):
    kw = {}
    if at is not None: kw['at'] = list(at)
    if volume is not None: kw['volume'] = volume
    if pitch is not None: kw['pitch'] = pitch
    ev(t, 'sound', id_, **kw)
def part(t, id_, at, count, spread):
    ev(t, 'particles', id_, at=list(at), count=count, spread=list(spread))
def block(t, id_, at):
    ev(t, 'block', id_, at=list(at))
def say(t, text, dur=None):
    w = {'t': T(t), 'text': text, 'narration': True}
    if dur: w['duration'] = T(dur)
    words.append(w)
def shot(t, pos, look, fov, cut=True, ease=None, roll=None):
    k = {'t': T(t), 'pos': [round(v, 2) for v in pos], 'look_at': look if isinstance(look, str) else [round(v, 2) for v in look], 'fov': fov}
    if cut: k['cut'] = True
    if ease: k['ease'] = ease
    if roll is not None: k['roll'] = roll
    cam.append(k)
def K(t, v, ease=None):
    k = {'t': T(t), 'pos': [round(c, 2) for c in v]}
    if ease: k['ease'] = ease
    return k
def Y(t, yaw, pitch=0, ease=None):
    k = {'t': T(t), 'yaw': yaw, 'pitch': pitch}
    if ease: k['ease'] = ease
    return k

# ---------------------------------------------------------------- the figure
fig_path = [
    K(0, (0.5, 11, -29.3)),
    K(12.8, (0.5, 11, -29.3)),
    K(14.4, (0.5, 11, -29.85), 'in_out'),          # to the lip
    K(14.9, (0.5, 11.9, -31.2), 'out'),            # the leap
    K(15.8, (0.5, -3.3, -34.6), 'in'),             # the plunge
    K(16.6, (0.5, -3.7, -34.8), 'out'),
    K(17.6, (0.5, -2.6, -34.3), 'in_out'),         # surfaces
    K(20.2, (0.5, -2.6, -31.1), 'linear'),         # swims into the open crack
    K(21.0, (0.5, -3.9, -29.6), 'in_out'),         # under the rock
    K(23.6, (0.5, -3.9, -25.6), 'linear'),         # the tunnel
    K(24.4, (0.5, -3.4, -23.5), 'in_out'),         # into the pool
    K(25.2, (0.5, -2.0, -22.6), 'out'),            # head up
    K(26.0, (0.5, 0, -21.0), 'in_out'),            # climbs out onto the floor
    K(27.6, (0.5, 0, -21.0)),
    K(30.0, (0.5, 0, -15.8), 'in_out'),            # walks to the seal
    K(31.4, (0.5, 0, -15.8)),
    K(32.0, (0.5, 0, -16.8), 'out'),               # staggers back
    K(35.8, (0.5, 0, -16.8)),
    K(38.2, (-0.6, 0, -13.5), 'in_out'),           # through the arch, onto the landing
    K(43.6, (-0.6, 0, -13.5)),
    K(44.6, (-0.5, 0, -12.5), 'in_out'),           # to the chain
    K(50.8, (-0.5, 0, -12.5)),
    K(52.0, (0.3, 0, -10.3), 'in_out'),            # to the water's edge
    K(52.2, (0.45, 0.1, -9.9), 'out'),             # a step out over the water
    K(52.6, (0.5, -1.9, -9.6), 'in'),              # down into the boat
    K(54.0, (0.5, -1.9, -9.6)),
    K(61.4, (0.5, -1.9, 1.3), 'in_out'),           # across the lake
    K(61.8, (0.5, -1.9, 1.3)),
    K(62.3, (0.5, 0.2, 2.6), 'out'),               # up out of the boat
    K(62.7, (0.5, 0, 3.6), 'in_out'),              # onto the island
    K(65.0, (0.5, 0, 6.3), 'in_out'),              # to the basin
    K(76.4, (0.5, 0, 6.3)),
    K(77.6, (0.5, 0, 7.0), 'in_out'),              # backs against the basin as they come
    K(81.4, (0.5, 0, 7.0)),
    K(81.7, (0.6, 0, 6.85), 'out'),                # jostled by the hands
    K(82.6, (0.35, 0, 7.1), 'in_out'),
    K(83.6, (0.6, 0, 6.9), 'in_out'),
    K(84.6, (0.5, 0, 7.0), 'in_out'),
]
fig_look = [
    Y(0, 180, 8), Y(3.5, 166, 6), Y(6.5, 192, 10), Y(7.6, 180, 42), Y(12.8, 180, 30),
    Y(14.4, 180, 10), Y(16.6, 180, 0), Y(17.6, 0, 0), Y(24.6, 0, 0), Y(25.6, 0, -10),
    Y(26.2, 0, 0), Y(30.0, 0, 0), Y(31.4, 0, 10), Y(32.0, 0, 30), Y(33.6, 0, 5),
    Y(38.2, 0, 0), Y(39.0, 35, 8), Y(39.9, -30, 8), Y(40.6, -50, 12), Y(43.6, -50, 12),
    Y(44.6, -90, 15), Y(50.8, -90, 10), Y(51.4, 0, 20), Y(54.0, 0, 5), Y(61.8, 0, 0),
    Y(65.0, 0, 30), Y(66.0, 0, 50), Y(69.4, 0, 45), Y(70.2, 0, 5), Y(71.6, -55, 8), Y(73.0, 55, 8),
    Y(74.6, 120, 4), Y(76.2, 180, 0), Y(79.0, 160, 4), Y(80.6, 200, 8), Y(81.6, 165, 18), Y(82.6, 205, 24),
    Y(83.6, 170, 20), Y(84.6, 180, 6), Y(85.6, 180, -28), Y(86.6, 180, -12), Y(88.0, 180, 0), Y(92.0, 170, 4),
    Y(95.0, 190, 4), Y(97.0, 180, 8), Y(99.6, 360, 35), Y(101.0, 360, 50),
]
fig_poses = [
    {'t': T(8.0), 'name': 'wand_raised', 'blend': 8},
    {'t': T(9.4), 'name': 'relax', 'blend': 10},
    {'t': T(14.2), 'name': 'dive', 'blend': 5},
    {'t': T(17.2), 'name': 'swim', 'blend': 8},
    {'t': T(25.6), 'name': 'relax', 'blend': 8},
    {'t': T(26.2), 'name': 'wand_point', 'blend': 6},
    {'t': T(27.4), 'name': 'relax', 'blend': 10},
    {'t': T(30.6), 'name': 'reach', 'blend': 10},
    {'t': T(31.4), 'name': 'stagger', 'blend': 2},
    {'t': T(34.0), 'name': 'relax', 'blend': 16},
    {'t': T(40.8), 'name': 'wand_raised', 'blend': 8},
    {'t': T(41.8), 'name': 'relax', 'blend': 10},
    {'t': T(45.0), 'name': 'reach', 'blend': 8},
    {'t': T(47.0), 'name': 'relax', 'blend': 12},
    {'t': T(52.4), 'name': 'seated', 'blend': 6},
    {'t': T(61.6), 'name': 'relax', 'blend': 6},
    {'t': T(65.4), 'name': 'look_down', 'blend': 20},
    {'t': T(70.0), 'name': 'relax', 'blend': 8},
    {'t': T(71.4), 'name': 'wary', 'blend': 10},
    {'t': T(81.4), 'name': 'grabbed', 'blend': 4},
    {'t': T(85.0), 'name': 'wand_raised', 'blend': 4},
    {'t': T(86.5), 'name': 'fire_cast', 'blend': 2},
    {'t': T(95.5), 'name': 'relax', 'blend': 20},
    {'t': T(100.6), 'name': 'look_down', 'blend': 20},
]
fig_actions = [
    {'t': T(9.0), 'do': 'cast', 'spell': 'revelio', 'target': [0.5, -1.0, -33.0]},
    {'t': T(26.6), 'do': 'cast', 'spell': 'lumos'},
    {'t': T(31.2), 'do': 'swing'},
    {'t': T(31.4), 'do': 'hurt'},
    {'t': T(41.4), 'do': 'cast', 'spell': 'revelio', 'target': [0.5, 0.5, -12.5]},
    {'t': T(45.6), 'do': 'swing'},
    {'t': T(81.5), 'do': 'hurt'},
    {'t': T(82.7), 'do': 'hurt'},
    {'t': T(83.8), 'do': 'hurt'},
    {'t': T(84.7), 'do': 'hurt'},
    {'t': T(86.4), 'do': 'cast', 'spell': 'fiendfyre', 'target': [0.5, 4.5, 0.0]},
]
# swimming strokes: arms sweep out and back, legs kick, from the surfacing to the pool
ra, la, rl, ll = [], [], [], []
t = 17.6
i = 0
while t <= 25.0:
    s = 1 if i % 2 else 0
    ra.append({'t': T(t), 'v': [0, 0, 38 * s]})
    la.append({'t': T(t), 'v': [0, 0, -38 * s]})
    rl.append({'t': T(t), 'v': [18 * (1 - 2 * s), 0, 0]})
    ll.append({'t': T(t), 'v': [-18 * (1 - 2 * s), 0, 0]})
    t += 0.5
    i += 1
for tr in (ra, la, rl, ll):
    tr.append({'t': T(25.4), 'v': [0, 0, 0]})
# the pull on the chain: the arm hauls down
ra_pull = [{'t': T(45.5), 'v': [0, 0, 0]}, {'t': T(45.9), 'v': [55, 0, 0], 'ease': 'in'},
           {'t': T(46.5), 'v': [0, 0, 0]}]
# stagger: body sags, head drops
fig_limbs = {
    'right_arm': {'rot': ra + ra_pull},
    'left_arm': {'rot': la},
    'right_leg': {'rot': rl},
    'left_leg': {'rot': ll},
}
figure = {
    'id': 'figure', 'type': 'player', 'skin': {'texture': 'hooded_figure'},
    'items': {'mainhand': 'hogwartsmagic:dragon_core_wand'},
    'path': fig_path, 'look': fig_look, 'limbs': fig_limbs, 'poses': fig_poses, 'actions': fig_actions,
}

# ---------------------------------------------------------------- the boat
BOAT_Y = -1.5   # the water block's y (-2) + the real boat's 0.5 float offset
boat = {
    'id': 'boat', 'type': 'hogwartsmagic:cave_boat',
    'path': [K(0, (0.5, -8.5, -9.5)), K(46.2, (0.5, -8.5, -9.5)), K(48.4, (0.5, BOAT_Y, -9.5), 'out'),
             K(54.0, (0.5, BOAT_Y, -9.5)), K(61.4, (0.5, BOAT_Y, 1.4), 'in_out')],
    'look': [Y(0, 0)],
}

poses = {
    'relax': {'head': [0, 0, 0]},
    'dive': {'right_arm': [-172, 0, 10], 'left_arm': [-172, 0, -10], 'head': [-10, 0, 0]},
    'swim': {'head': [-18, 0, 0], 'right_arm': [-150, 0, 22], 'left_arm': [-150, 0, -22],
             'right_leg': [12, 0, 0], 'left_leg': [-12, 0, 0]},
    'reach': {'right_arm': [-92, -6, 0], 'head': [12, 0, 0]},
    'stagger': {'head': [32, 0, 0], 'body': [12, 0, 0], 'left_arm': [-58, 34, 0], 'right_arm': [-18, 0, 18],
                'right_leg': [-8, 0, 0], 'left_leg': [6, 0, 0]},
    'wary': {'right_arm': [-62, -8, 0], 'left_arm': [-18, 0, -14], 'head': [4, 0, 0]},
    'grabbed': {'head': [-22, 0, 0], 'body': [-8, 0, 0], 'right_arm': [-28, 0, 46], 'left_arm': [-24, 0, -40],
                'right_leg': [-10, 0, 0], 'left_leg': [8, 0, 0]},
    'fire_cast': {'right_arm': [-168, -12, 0], 'left_arm': [-40, 0, -52], 'head': [-14, 0, 0], 'body': [-6, 0, 0]},
    'seated': {'right_leg': [-81, 18, 4.5], 'left_leg': [-81, -18, -4.5], 'right_arm': [-36, 0, 0],
               'left_arm': [-36, 0, 0]},
}

# ---------------------------------------------------------------- events
# soundtrack: files carry their own fades; cues overlap ~2 s to crossfade (MemoryAudio, non-positional)
def music(t, name):
    ev(t, 'music', 'hogwartsmagic:pensieve.music.' + name, volume=1.0)
music(0.0, 'cave_journey')
music(47.0, 'cave_despair')
music(77.0, 'lake_dementors')
# the storm out at sea
ev(3.0, 'effect', 'lightning', at=[-44, -2, -60])
ev(12.2, 'effect', 'lightning', at=[40, -2, -62])
snd(0.0, 'minecraft:item.elytra.flying', volume=0.3, pitch=0.6)
snd(7.0, 'minecraft:item.elytra.flying', volume=0.25, pitch=0.5)

# Revelio on the cliff: the crack in the cliff foot opens (concealed rock, local x31..33 y19..23 z1..2)
CRACK = [(x, y, z) for x in (-1, 0, 1) for y in range(-4, 1) for z in (-31, -30)]
for c in CRACK:
    assert world.get(c) == 'hogwartsmagic:concealed_rock', (c, world.get(c))
    block(9.4, 'minecraft:water' if c[1] <= WATER_HI else 'minecraft:air', c)
snd(9.4, 'minecraft:block.amethyst_block.chime', at=[0, 0, -31], volume=1.0, pitch=0.6)
for t in (9.4, 10.9, 12.4, 13.9, 15.4, 16.9, 18.4, 19.9):
    part(t, 'minecraft:enchant', (0, 0, -31), 30, (1.6, 1.2, 0.6))
    part(t + 0.4, 'minecraft:glow', (0, 0, -31), 8, (1.4, 1.0, 0.5))

# the dive
snd(14.9, 'minecraft:entity.player.attack.sweep', at=[0, 12, -31], volume=0.4, pitch=0.6)
snd(15.75, 'minecraft:entity.player.splash.high_speed', at=[0, -1, -35], volume=1.4)
part(15.75, 'minecraft:splash', (0, -1, -35), 140, (1.2, 0.3, 1.2))
part(15.85, 'minecraft:bubble', (0, -4, -35), 40, (0.8, 1.2, 0.8))
for t in (17.8, 18.8, 19.8):
    snd(t, 'minecraft:entity.player.swim', at=[0, -2, -33], volume=0.6)
snd(21.6, 'minecraft:ambient.underwater.enter', volume=0.7)
part(22.0, 'minecraft:bubble', (0, -3, -27), 20, (0.6, 0.6, 1.5))
part(23.4, 'minecraft:bubble', (0, -3, -25), 20, (0.6, 0.6, 1.2))
snd(24.6, 'minecraft:ambient.underwater.exit', volume=0.7)
snd(25.1, 'minecraft:entity.player.splash', at=[0, -1, -23], volume=0.8)
part(25.1, 'minecraft:splash', (0, -1, -23), 40, (0.7, 0.2, 0.7))
part(25.9, 'minecraft:falling_water', (0, 1, -21), 20, (0.3, 0.6, 0.3))

# Lumos in the antechamber: the light goes with the wand
lights = []
def light(t, at, prev=None):
    assert world.get(tuple(at), 'minecraft:air') in ('minecraft:air',) and tuple(at) not in tpl, ('light not in air', at, world.get(tuple(at)))
    block(t, 'minecraft:light', at)
    if prev is not None:
        block(t, 'minecraft:air', prev)
    lights.append((t, tuple(at)))
light(26.6, (0, 2, -20))
for t in (28.0, 28.6, 29.2, 29.8, 37.0, 37.6):
    snd(t, 'minecraft:block.deepslate.step', at=[0, 0, -19 if t < 30 else -15], volume=0.5)
light(28.8, (0, 2, -18), (0, 2, -20))

# the Blood Seal (local x30..34 y23..26 z17, less the two top corners)
SEAL = [(x, y, -15) for x in range(-2, 3) for y in range(0, 4) if not (y == 3 and x in (-2, 2))]
for c in SEAL:
    assert world.get(c) == 'hogwartsmagic:blood_seal', (c, world.get(c))
snd(31.3, 'minecraft:block.stone_button.click_on', at=[0, 1, -15], volume=1.0, pitch=0.5)
snd(31.4, 'minecraft:entity.player.hurt', at=[0, 1, -16], volume=1.0, pitch=0.8)
snd(31.5, 'hogwartsmagic:basilisk.gaze_heartbeat', volume=0.6)
part(31.4, 'minecraft:damage_indicator', (1, 2, -16), 3, (0.3, 0.25, 0.2))
part(31.45, 'minecraft:crimson_spore', (1, 1, -15), 25, (0.4, 0.5, 0.2))
for c in SEAL:
    block(32.0, 'minecraft:air', c)
part(32.0, 'minecraft:smoke', (0, 1, -15), 40, (1.6, 1.4, 0.3))
snd(32.0, 'minecraft:block.deepslate_bricks.break', at=[0, 1, -15], volume=0.8, pitch=0.5)
snd(33.0, 'minecraft:ambient.cave', volume=0.7)
light(36.4, (0, 2, -14), (0, 2, -18))

# Revelio at the shore: the hidden chain (local 32,23,19) shows itself
CHAIN = (0, 0, -13)
assert world.get(CHAIN) == 'hogwartsmagic:hidden_chain'
block(41.8, 'minecraft:chain', CHAIN)
snd(41.8, 'minecraft:block.amethyst_block.chime', at=list(CHAIN), volume=0.9, pitch=0.8)
part(41.8, 'minecraft:enchant', (1, 1, -12), 30, (0.4, 0.6, 0.4))
part(42.0, 'minecraft:glow', (1, 1, -12), 8, (0.3, 0.5, 0.3))
part(43.6, 'minecraft:glow', (1, 1, -12), 5, (0.3, 0.5, 0.3))

# the pull, and the boat rises (the real summon's sounds: chain place on use, chain break + paddle on rise)
snd(45.7, 'minecraft:block.chain.place', at=list(CHAIN), volume=1.0)
snd(46.3, 'minecraft:block.bubble_column.upwards_inside', at=[0, -2, -10], volume=0.9)
part(46.3, 'minecraft:bubble_column_up', (1, -6, -9), 40, (0.6, 2.0, 0.6))
part(47.4, 'minecraft:bubble_column_up', (1, -4, -9), 30, (0.6, 1.2, 0.6))
snd(48.2, 'minecraft:block.chain.break', at=[0, -1, -10], volume=1.0, pitch=0.8)
snd(48.3, 'minecraft:entity.boat.paddle_water', at=[0, -1, -10], volume=1.0)
part(48.2, 'minecraft:splash', (1, -1, -9), 60, (0.9, 0.2, 1.2))

# boarding and the crossing: the wand light rides above the boat
snd(52.6, 'minecraft:entity.generic.splash', at=[0, -1, -10], volume=0.4, pitch=1.2)
prev = (0, 2, -14)
for t, z in ((54.0, -9), (55.6, -6), (57.2, -3), (58.8, 0), (60.4, 2)):
    light(t, (0, 1, z), prev)
    prev = (0, 1, z)
snd(54.4, 'minecraft:ambient.soul_sand_valley.mood', volume=0.8)
snd(57.4, 'hogwartsmagic:inferius.ambient', at=[-9, -3, -2], volume=0.25, pitch=0.7)
for t in (55.0, 57.8, 60.6):
    part(t, 'minecraft:fishing', (1, -1, -9 + (t - 54.0) * 1.5), 4, (0.3, 0.0, 0.3))

# the island and the Basin of Despair (block 0,0,8): the wand light goes out, the basin glows
snd(62.6, 'minecraft:block.deepslate.step', at=[0, 0, 3], volume=0.5)
block(64.0, 'minecraft:air', prev)
snd(66.0, 'minecraft:block.beacon.ambient', at=[0, 0, 8], volume=0.6, pitch=0.6)
snd(102.0, 'minecraft:block.beacon.ambient', at=[0, 0, 8], volume=0.6, pitch=0.55)
for t in (63.0, 65.0, 67.0, 69.0, 99.0, 101.0, 103.0, 105.0, 107.0, 109.0):
    part(t, 'minecraft:glow', (1, 1, 9), 5, (0.25, 0.15, 0.25))

# ---------------------------------------------------------------- the Inferi and the Fiendfyre
# 80 Inferi rise from the lake around the island, wade in and grab the figure at the basin; the figure casts
# Fiendfyre and rings of fire (effect fiendfyre_rings, EffectVisuals: a wave born every 16 ticks from 1.5 blocks
# out, rolling 0.25 blocks a tick) burn them away. Each Inferius ignites the moment the first wave reaches it.
import random
rng = random.Random(1997)
CX, CZ = 0, 7                 # the fire's centre: the effect's block corner, by the figure at the basin
FIG = (0.5, 0, 7.0)           # where the figure stands for the fight
FIRE_T = 86.6                 # the cast and the first wave
WAVE_R0, WAVE_SPEED = 1.5, 5.0   # blocks, blocks per second (0.25 a tick)
WADE_Y = -2.3                 # feet in the lake: chest and head above the surface
DEEP_Y = -6.6                 # waiting under the water before they rise
SOLIDISH = lambda b: b is not None and b not in OPEN

def cell(x, y, z):
    return world.get((math.floor(x), y, math.floor(z)))
def on_island(x, z):
    return SOLIDISH(cell(x, -1, z)) and cell(x, 0, z) is None and cell(x, 1, z) is None
def in_lake(x, z):
    return cell(x, -2, z) == 'minecraft:water' and cell(x, -1, z) is None and cell(x, 0, z) is None
def ground_y(x, z):
    if on_island(x, z):
        return 0.0
    if in_lake(x, z):
        return WADE_Y
    return None
def hit_time(x, z):
    d = math.hypot(x - CX, z - CZ)
    return FIRE_T + max(0.0, d - WAVE_R0) / WAVE_SPEED

# the slots they end up standing in, ring by ring round the figure
slots = []
for ring, (r, n) in enumerate(((1.15, 6), (2.3, 12), (3.5, 16), (4.8, 20), (6.2, 24), (7.6, 30))):
    off = rng.random() * math.tau
    for k in range(n):
        a = off + k * math.tau / n + rng.uniform(-0.08, 0.08)
        x, z = FIG[0] + math.cos(a) * r, FIG[2] + math.sin(a) * r
        if ground_y(x, z) is None:
            continue
        slots.append((ring, a, r, x, z))
slots = slots[:80]
assert len(slots) == 80, len(slots)

def lake_reach(a):
    """The farthest open water along angle a from the figure, out to 18 blocks."""
    best = None
    r = 5.0
    while r < 18.0:
        x, z = FIG[0] + math.cos(a) * r, FIG[2] + math.sin(a) * r
        if in_lake(x, z):
            best = r
        elif best is not None and not on_island(x, z):
            break
        r += 0.25
    return best

def yaw_to(x, z, tx, tz):
    return math.degrees(math.atan2(-(tx - x), tz - z))

inferi = []
grabbers = []
for i, (ring, a, r, x, z) in enumerate(slots):
    far = lake_reach(a)
    assert far is not None, ('no lake along', a)
    rs = min(far - 0.8, max(r + 4.5, 8.0) + rng.uniform(0.0, 6.0))
    sx, sz = FIG[0] + math.cos(a) * rs, FIG[2] + math.sin(a) * rs
    assert in_lake(sx, sz), (i, sx, sz)
    # arrival: the inner rings first, so the grab lands before the rest crowd in. Ring 0 reaches him from 81.4,
    # with the figure's 'grabbed' pose and first hurt (81.4, 81.5): the hands land as he reacts, not a second after
    t_arr = {0: 81.4, 1: 83.0, 2: 83.6, 3: 84.2, 4: 84.8, 5: 85.4}[ring] + rng.uniform(0.0, 0.6)
    speed = rng.uniform(1.4, 2.1)
    dist = rs - r
    t_walk = t_arr - dist / speed
    t_rise = min(77.6, max(70.8, t_walk - 1.7))
    t_walk = t_rise + 1.7
    if dist / (t_arr - t_walk) > 3.0:
        t_arr = t_walk + dist / 3.0
    th = hit_time(x, z)
    assert t_arr < th - 0.3, (i, t_arr, th)
    yaw = yaw_to(x, z, FIG[0], FIG[2])
    # the path: deep, rise, wade in a straight line, climb out at the shore, stand
    path = [K(0, (sx, DEEP_Y, sz)), K(t_rise, (sx, DEEP_Y, sz)), K(t_rise + 1.5, (sx, WADE_Y, sz), 'out')]
    steps = max(2, int(dist / 0.5))
    prev_y = WADE_Y
    for s in range(1, steps + 1):
        f = s / steps
        px, pz = sx + (x - sx) * f, sz + (z - sz) * f
        gy = ground_y(px, pz)
        if gy is None:
            gy = prev_y
        if gy != prev_y:
            # the shoreline: the last wading step, then a climb onto the rock
            tf = max(t_walk + (t_arr - t_walk) * (s - 1) / steps, float(path[-1]['t'][:-1]) + 0.05)
            path.append(K(tf, (sx + (x - sx) * (s - 1) / steps, prev_y, sz + (z - sz) * (s - 1) / steps), 'linear'))
            path.append(K(tf + 0.45, (px, gy, pz), 'out'))
            prev_y = gy
    gy_end = prev_y
    path.append(K(max(t_arr, float(path[-1]['t'][:-1]) + 0.3), (x, gy_end, z), 'linear'))
    # the burn: thrown back a little along the same ground, then down
    push = 1.6 if ring < 2 else 1.0
    bx, bz = x + math.cos(a) * push, z + math.sin(a) * push
    if ground_y(bx, bz) != gy_end:
        bx, bz = x + math.cos(a) * 0.3, z + math.sin(a) * 0.3
    path.append(K(th, (x, gy_end, z)))
    path.append(K(th + 0.5, (bx, gy_end, bz), 'out'))
    die_t = th + rng.uniform(0.45, 1.0)
    anims = [{'t': T(t_rise), 'name': 'RISE', 'blend': 0},
             {'t': T(t_walk), 'name': 'WALK', 'blend': 6}]
    if ring <= 1:
        t = t_arr
        while t < th - 0.3:
            anims.append({'t': T(t), 'name': 'GRAB', 'blend': 2})
            t += 0.65
        grabbers.append(i)
    else:
        anims.append({'t': T(t_arr), 'name': 'IDLE_SWAY', 'blend': 8})
    anims.append({'t': T(th), 'name': 'RECOIL', 'blend': 0})
    # reaching for him: both arms out ahead, from the moment they walk until the fire
    reach = {'rot': [{'t': T(t_walk), 'v': [0, 0, 0]}, {'t': T(t_walk + 0.8), 'v': [-75, 0, 0]},
                     {'t': T(th), 'v': [-75, 0, 0]}, {'t': T(th + 0.3), 'v': [-150, 0, 0], 'ease': 'out'}]}
    inferi.append({
        'id': 'inferius_%02d' % i, 'type': 'hogwartsmagic:inferius',
        'path': path, 'look': [Y(0, round(yaw, 1))],
        'anims': anims,
        'limbs': {'right_arm': reach, 'left_arm': reach},
        'actions': [{'t': T(0), 'do': 'vanish'}, {'t': T(t_rise), 'do': 'appear'},
                    {'t': T(th), 'do': 'ignite'}, {'t': T(th + 0.05), 'do': 'hurt'}, {'t': T(die_t), 'do': 'die'}],
    })
    # the surface breaking as it rises; a groan from some of them
    part(t_rise + 1.0, 'minecraft:splash', (math.floor(sx), -1, math.floor(sz)), 18, (0.4, 0.1, 0.4))
    if i % 5 == 0:
        snd(t_rise + 1.0, 'hogwartsmagic:inferius.rise', at=[math.floor(sx), -1, math.floor(sz)], volume=0.6,
            pitch=round(rng.uniform(0.75, 1.0), 2))
    if i % 7 == 3:
        snd(t_arr - 1.0, 'hogwartsmagic:inferius.ambient', at=[math.floor(x), 0, math.floor(z)], volume=0.5,
            pitch=round(rng.uniform(0.7, 0.95), 2))
    # the burning: smoke and embers where it falls, a hiss and a death cry from some
    part(die_t + 0.9, 'minecraft:large_smoke', (math.floor(bx), 0, math.floor(bz)), 14, (0.4, 0.8, 0.4))
    if i % 3 == 0:
        snd(th + 0.1, 'minecraft:block.fire.extinguish', at=[math.floor(x), 0, math.floor(z)], volume=0.5,
            pitch=round(rng.uniform(0.6, 0.9), 2))
    if i % 6 == 1:
        snd(die_t, 'hogwartsmagic:inferius.death', at=[math.floor(bx), 0, math.floor(bz)], volume=0.6,
            pitch=round(rng.uniform(0.7, 0.9), 2))

for t in (81.6, 82.9, 84.0):
    snd(t, 'hogwartsmagic:inferius.grab', at=[0, 0, 7], volume=0.9)
snd(70.4, 'minecraft:ambient.underwater.loop.additions.rare', volume=0.9)
for t, x, z in ((70.2, -8, 0), (70.9, 9, 3), (71.5, -4, 16), (72.3, 12, 12)):
    part(t, 'minecraft:bubble', (x, -3, z), 30, (2.5, 1.0, 2.5))
    part(t + 0.3, 'minecraft:fishing', (x, -1, z), 12, (2.0, 0.0, 2.0))

# the Fiendfyre: the cast, the rings, and the fire's own light rolling outward with the first wave
ev(FIRE_T, 'effect', 'fiendfyre_rings', at=[CX, -1, CZ], duration=80)
snd(FIRE_T, 'minecraft:entity.generic.explode', at=[CX, 0, CZ], volume=0.7, pitch=0.6)
for t in (FIRE_T + 0.4, FIRE_T + 1.6, FIRE_T + 2.8, FIRE_T + 4.0):
    snd(t, 'minecraft:block.fire.ambient', at=[CX, 1, CZ], volume=2.0, pitch=0.6)
part(FIRE_T + 5.5, 'minecraft:large_smoke', (CX, 3, CZ), 120, (8.0, 2.0, 8.0))
part(FIRE_T + 7.5, 'minecraft:campfire_cosy_smoke', (CX, 1, CZ), 40, (6.0, 0.5, 6.0))
fire_lights = []
def fire_light(t, at):
    assert world.get(tuple(at)) is None, ('fire light not in air', at, world.get(tuple(at)))
    block(t, 'minecraft:light', at)
    fire_lights.append((t, tuple(at)))
centre_light = (0, 2, 6)
fire_light(FIRE_T, centre_light)
block(FIRE_T + 7.0, 'minecraft:air', centre_light)
prev_ring = []
for r in (3, 6, 9, 12, 15):
    t = FIRE_T + (r - WAVE_R0) / WAVE_SPEED
    ring = []
    for k in range(8):
        a = k * math.tau / 8 + 0.2
        p = (math.floor(CX + math.cos(a) * r), 1, math.floor(CZ + math.sin(a) * r))
        if world.get(p) is None and world.get((p[0], 2, p[2])) is None:
            fire_light(t, p)
            ring.append(p)
    for p in prev_ring:
        block(t + 0.4, 'minecraft:air', p)
    prev_ring = ring
for p in prev_ring:
    block(FIRE_T + 4.6, 'minecraft:air', p)

# the fire serpents: four of them spiral out of the wand and circle the island, then plunge into the lake
WAND = (0.5, 1.9, 6.4)
serpents = []
for j in range(4):
    keys = [K(0, WAND)]
    phase = j * math.pi / 2
    sign = 1 if j % 2 == 0 else -1
    tau = 0.0
    END = 9.0
    while tau <= END + 1e-6:
        grow = min(1.0, tau / 2.4)
        grow = grow * grow * (3 - 2 * grow)
        r = 0.6 + 10.5 * grow + 2.2 * math.sin(tau * 1.1 + j) * grow
        th_ = phase + sign * (1.15 * tau + 0.6 * tau * tau / END)
        y = 1.9 + (1.4 + 1.2 * math.sin(tau * 1.7 + j * 1.3)) * grow
        if tau > END - 0.9:
            # the plunge: rear up, then down into the black water
            f = (tau - (END - 0.9)) / 0.9
            y = y + 1.5 * math.sin(f * math.pi) - 6.0 * f * f
        px, pz = CX + 0.5 + math.cos(th_) * r, CZ + math.sin(th_) * r
        keys.append(K(FIRE_T + tau, (px, y, pz), 'linear'))
        tau += 0.2
    last = keys[-1]['pos']
    part(FIRE_T + END - 0.35, 'minecraft:splash', (math.floor(last[0]), -1, math.floor(last[2])), 60, (0.8, 0.2, 0.8))
    part(FIRE_T + END - 0.3, 'minecraft:large_smoke', (math.floor(last[0]), -1, math.floor(last[2])), 30, (0.6, 0.6, 0.6))
    snd(FIRE_T + END - 0.35, 'minecraft:block.lava.extinguish', at=[math.floor(last[0]), -1, math.floor(last[2])], volume=1.2, pitch=0.6)
    serpents.append({
        'id': 'serpent_%d' % j, 'type': 'hogwartsmagic:fiendfyre_serpent',
        'path': keys,
        'actions': [{'t': T(0), 'do': 'vanish'}, {'t': T(FIRE_T + 0.1 + 0.15 * j), 'do': 'appear'},
                    {'t': T(FIRE_T + END), 'do': 'vanish'}],
    })

# ---------------------------------------------------------------- words (the Pensieve's voice)
say(1.0, 'A cliff above a black, restless sea.')
say(10.6, 'Revelio shows what the rock hides.')
say(18.2, 'The way in lies beneath the water.')
say(33.0, 'The arch takes its toll in blood from all who enter.', 4.5)
say(42.4, 'Revelio again. The chain was always there.')
say(48.8, 'Pull it, and the boat rises to meet you.')
say(55.4, 'Stay in the boat. The lake does not welcome swimmers.', 4.5)
say(63.2, 'At its heart, the Basin of Despair.')
say(70.4, 'But the lake keeps its own dead.')
say(96.6, 'The dead fear fire and light. Little else.', 4.5)
say(104.4, 'Remember the way. A map will show you where.', 5)

# ---------------------------------------------------------------- camera
F = 'actor:figure'
# S1 establishing: over the black sea, the headland and the tiny figure
shot(0.0, (-30, 12, -60), (0.5, 11.5, -29.3), 82, cut=False)
shot(6.0, (-22, 9.5, -53), (0.5, 12.0, -29.3), 74, cut=False, ease='in_out')
# S2 over the shoulder on the clifftop, looking down at the waterline
shot(6.0, (-4.0, 9.0, -36.5), F, 55)
shot(9.3, (-3.4, 9.3, -35.4), F, 52, cut=False)
# S3 the reveal: low over the water, the cliff face, the crack lighting up, the figure above
shot(9.3, (4.0, 0.6, -47.0), (0.5, 4.5, -31), 70)
shot(14.2, (2.6, 0.4, -43.0), (0.5, 4.5, -31), 64, cut=False)
# S4 the dive, side on from the west at sea level
shot(14.2, (-15.0, 2.5, -35.5), F, 70)
shot(17.6, (-14.0, 1.2, -36.0), F, 66, cut=False)
# S5 behind him on the water as he swims into the crack
shot(17.6, (0.8, 2.4, -40.5), F, 60)
shot(21.4, (0.6, 0.9, -36.0), (0.5, -1.5, -30.5), 58, cut=False)
# S6 under water in the antechamber pool, looking into the tunnel mouth
shot(21.4, (-1.4, -1.6, -22.4), (0.5, -3.2, -27.0), 70)
shot(24.6, (-1.4, -1.4, -22.3), F, 72, cut=False)
# S7 the antechamber, from the seal end: he surfaces, lights his wand, walks toward us
shot(24.6, (-2.6, 3.6, -16.6), F, 60)
shot(30.2, (-3.0, 3.0, -16.3), F, 56, cut=False)
# S8 the toll, side close-up
shot(30.2, (-3.2, 1.7, -17.8), (0.5, 1.4, -15.6), 48, roll=0)
shot(31.4, (-3.0, 1.7, -17.6), (0.5, 1.3, -15.9), 47, cut=False, roll=4)
shot(35.6, (-2.7, 1.6, -17.3), (0.5, 1.1, -16.4), 45, cut=False, roll=2)
# S9 behind him through the open arch: the dark lake and the far glow of the island
shot(35.6, (1.9, 2.4, -20.0), (0.5, 1.0, -9.0), 64)
shot(40.4, (1.0, 2.0, -17.0), F, 60, cut=False)
# S10 Revelio at the shore, low from the water's edge
shot(40.4, (-3.4, 0.9, -10.4), F, 56)
shot(45.0, (-3.1, 0.9, -10.6), (0.5, 0.6, -12.6), 52, cut=False)
# S11 the pull: over the water looking back at the landing, the boat breaking the surface
shot(45.0, (3.4, 2.0, -4.8), (0.3, 0.3, -11.0), 62)
shot(50.6, (2.8, 1.6, -5.8), (0.4, -0.6, -10.0), 58, cut=False)
# S12 he steps down into the boat
shot(50.6, (-2.8, 2.8, -13.6), F, 56)
shot(54.2, (-2.2, 2.6, -12.6), F, 54, cut=False)
# S13 wide from the dome: a small light gliding over black water toward the island
shot(54.2, (-6.0, 6.5, -4.0), F, 76)
shot(58.4, (-5.0, 6.0, 1.0), F, 70, cut=False)
# S14 tracking beside the boat at water level
shot(58.4, (2.7, 0.2, -3.4), F, 50)
shot(62.2, (2.7, 0.3, 0.6), F, 52, cut=False)
# S15 from beyond the basin: he steps onto the island and comes to it
shot(62.2, (-2.4, 1.5, 11.2), F, 58)
shot(66.4, (-2.2, 1.6, 10.8), F, 56, cut=False)
# S16 profile: the figure over the basin
shot(66.4, (3.8, 1.5, 7.6), (0.5, 1.2, 7.4), 55)
shot(70.0, (3.5, 1.4, 7.4), (0.5, 1.1, 7.6), 53, cut=False)
# S17 low over the black water: the first heads break the surface between us and the island
shot(70.0, (9.0, -0.4, -3.0), (0.5, 0.6, 6.5), 72)
shot(73.4, (7.6, -0.3, -1.4), (0.5, 0.8, 6.5), 66, cut=False)
# S18 high from the dome: they rise in rings all round the island
shot(73.4, (-4.0, 8.5, 1.0), (0.5, 0.5, 7.0), 80)
shot(76.6, (3.0, 8.8, 1.5), (0.5, 0.5, 7.0), 76, cut=False)
# S19 close on the figure, turning to face them
shot(76.6, (1.6, 1.8, 4.6), F, 46, roll=4)
shot(79.6, (1.9, 1.7, 4.9), F, 44, cut=False, roll=5)
# S20 low behind the wading horde, tracking in
shot(79.6, (-8.0, -0.2, -1.0), (0.5, 1.0, 7.0), 64)
shot(82.0, (-6.2, 0.0, 1.2), (0.5, 1.0, 7.0), 60, cut=False)
# S21 high and tilted: the figure under the hands
shot(82.0, (3.8, 6.4, 3.4), F, 58, roll=8)
shot(84.2, (3.2, 5.8, 4.0), F, 54, cut=False, roll=10)
# S22 over the shoulder from above the basin: the wand goes up over the horde (from 84.2, so no shot is under 2 s)
shot(84.2, (0.7, 3.2, 8.7), (0.5, 2.2, 2.5), 55, roll=-3)
shot(86.3, (0.7, 3.4, 8.5), (0.5, 3.0, 2.5), 50, cut=False, roll=-2)
# S23 the cast, low from the water: the first ring of fire rolls through them and over us
shot(86.3, (-8.0, 0.2, 1.5), (0.5, 1.4, 7.0), 74)
shot(88.4, (-7.6, 0.3, 1.9), (0.5, 1.4, 7.0), 78, cut=False)
# S24 the crane from the dome: rings of fire rolling out in circles, the serpents spiralling round
shot(88.4, (-3.5, 9.5, 2.5), (0.5, 0.5, 7.0), 84)
shot(92.6, (3.5, 9.5, 2.5), (0.5, 0.5, 7.0), 80, cut=False)
# S25 a low orbit round the figure while the serpents circle, until they plunge into the lake
shot(92.6, (4.6, 1.8, 3.6), F, 62)
shot(96.0, (-4.2, 2.2, 3.9), F, 58, cut=False)
# S26 wide and still: smoke over an empty lake
shot(96.0, (-9.0, 4.5, -4.0), (0.5, 0.5, 7.0), 70)
shot(99.6, (-8.6, 4.4, -3.4), (0.5, 0.5, 7.0), 66, cut=False)
# S27 profile: back to the basin
shot(99.6, (3.8, 1.5, 7.9), (0.5, 1.2, 7.8), 55)
shot(104.0, (3.4, 1.4, 7.8), (0.5, 1.1, 8.0), 52, cut=False)
# S28 the slow push-in over his shoulder into the basin; the fade comes at the end
shot(104.0, (2.2, 2.8, 4.4), (0.5, 0.9, 8.5), 56)
shot(111.0, (1.3, 2.3, 5.8), (0.5, 0.9, 8.5), 40, cut=False, ease='in_out')

LENGTH = 111.0
scene = {
    'title': 'The Memory of the Cave',
    'length': T(LENGTH),
    'radius': R,
    'set': {
        'templates': [{'id': 'hogwartsmagic:locket_cave', 'at': [OX, OY, OZ]}],
        'fills': fills,
        'time_of_day': 18000,
        'build_ticks': 60,
    },
    'poses': poses,
    'actors': [figure, boat] + inferi + serpents,
    'events': sorted(events, key=lambda e: float(e['t'][:-1])),
    'words': words,
    'camera': cam,
    'end_action': 'cave_map',
}

# ---------------------------------------------------------------- checks
problems = []
def solid(p):
    c = (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))
    b = world.get(c)
    return b is not None and b not in OPEN and b not in ('hogwartsmagic:hidden_chain',), b

def tval(s):
    return float(s[:-1])

def sample(keys, t):
    ks = [(tval(k['t']), k['pos']) for k in keys]
    if t <= ks[0][0]: return ks[0][1]
    for (t0, a), (t1, b) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a[i] + (b[i] - a[i]) * f for i in range(3)]
    return ks[-1][1]

# block events land in the state they expect: lights in air, crack/seal/chain checked above
# actors: feet and head cells never in rock; the figure can stand in water while swimming
dynamic_open = set(CRACK) | set(SEAL) | {CHAIN}
CRACK_SET, SEAL_SET = set(CRACK), set(SEAL)
def rock(p, t):
    c = (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))
    if (c in CRACK_SET and t >= 9.4) or (c in SEAL_SET and t >= 32.0):
        return None
    b = world.get(c)
    if b is None or b in OPEN or b in ('hogwartsmagic:hidden_chain',):
        return None
    if c in dynamic_open:
        return b
    return b
for tt in [i / 10 for i in range(int(LENGTH * 10) + 1)]:
    p = sample(fig_path, tt)
    for dy in (0.05, 0.9, 1.7):
        b = rock((p[0], p[1] + dy, p[2]), tt)
        if b:
            # the seal stands until 32.0 and the figure must not walk into it
            problems.append('figure in %s at t=%.1f pos=%s+%.1f' % (b, tt, [round(v, 2) for v in p], dy))
            break

# camera: position free, and a clear line to what it looks at
def actor_eye(t):
    p = sample(fig_path, t)
    return [p[0], p[1] + 1.62, p[2]]
for i, k in enumerate(cam):
    t = tval(k['t'])
    pos = k['pos']
    b = rock(pos, t)
    if b:
        problems.append('camera %d at t=%s inside %s' % (i, k['t'], b))
    tgt = actor_eye(t) if k['look_at'] == F else k['look_at']
    n = int(math.dist(pos, tgt) * 10)
    for j in range(1, n):
        q = [pos[a] + (tgt[a] - pos[a]) * j / n for a in range(3)]
        if math.dist(q, tgt) < 0.6:
            break
        b = rock(q, t)
        if b:
            problems.append('camera %d at t=%s: view to %s blocked by %s at %s' % (i, k['t'], [round(v, 1) for v in tgt], b, [round(v, 1) for v in q]))
            break
# also sample the camera between keys inside each shot
for a, b2 in zip(cam, cam[1:]):
    if b2.get('cut'):
        continue
    t0, t1 = tval(a['t']), tval(b2['t'])
    for j in range(1, 10):
        f = j / 10
        t = t0 + (t1 - t0) * f
        pos = [a['pos'][c] + (b2['pos'][c] - a['pos'][c]) * f for c in range(3)]
        bb = rock(pos, t)
        if bb:
            problems.append('camera between %s and %s inside %s' % (a['t'], b2['t'], bb))
        tgt = actor_eye(t) if b2['look_at'] == F else b2['look_at']
        n = int(math.dist(pos, tgt) * 10)
        for m in range(1, n):
            q = [pos[c] + (tgt[c] - pos[c]) * m / n for c in range(3)]
            if math.dist(q, tgt) < 0.6:
                break
            bb = rock(q, t)
            if bb:
                problems.append('camera between %s and %s (t=%.1f): view blocked by %s at %s' % (a['t'], b2['t'], t, bb, [round(v, 1) for v in q]))
                break
# extents
for f in fills:
    for v in (f['from'], f['to']):
        if abs(v[0]) > R or abs(v[2]) > R:
            problems.append('fill beyond radius %s' % f)
if OX < -R or OX + 63 > R or OZ < -R or OZ + 63 > R:
    problems.append('template beyond radius')
# shot lengths
cuts = [0.0] + [tval(k['t']) for k in cam if k.get('cut')] + [LENGTH]
lens = [round(b - a, 2) for a, b in zip(cuts, cuts[1:])]
print('shots', len(lens), lens)
for l in lens:
    if l < 2:
        problems.append('shot length %s (under 2 s reads as a glitch)' % l)
# the grab: an Inferius has its hands on him (within 1.4 of his feet) at his first hurt of the fight
t_grab = min(tval(a['t']) for a in fig_actions if a['do'] == 'hurt' and tval(a['t']) > 70)
fp = sample(fig_path, t_grab)
reach = min(math.hypot(sample(a['path'], t_grab)[0] - fp[0], sample(a['path'], t_grab)[2] - fp[2]) for a in inferi)
print('nearest Inferius at the first grab (%.1f s): %.2f' % (t_grab, reach))
if reach > 1.4:
    problems.append('figure hurt at %.1f s with no Inferius in reach (nearest %.2f)' % (t_grab, reach))
words_t = [tval(w['t']) for w in words]
print('words gaps', [round(b - a, 1) for a, b in zip(words_t, words_t[1:])])
nblocks = 0
for f in fills:
    a, b = f['from'], f['to']
    nblocks += (abs(a[0] - b[0]) + 1) * (abs(a[1] - b[1]) + 1) * (abs(a[2] - b[2]) + 1)
print('fills', len(fills), 'fill blocks', nblocks, 'template non-air', len(tpl))
print('events', len(events))
problems += physics_problems(world, R)
print('support cells', len(support))
print('\n'.join(problems) if problems else 'NO PROBLEMS')

txt = write_scene(scene, 'locket_cave', width=400)   # 80 Inferi: wide lines keep it under the size cap
print('chars', len(txt))

# watertight: no water cell may sit beside (or above) a cell that is empty in the memory level
leaks = []
for c, b in world.items():
    if b != 'minecraft:water':
        continue
    x, y, z = c
    for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, -1, 0)):
        n = (x + d[0], y + d[1], z + d[2])
        nb = world.get(n)
        if nb is None or nb == 'minecraft:light':
            if abs(n[0]) <= R and abs(n[2]) <= R:
                leaks.append((c, n))
print('water leaks', len(leaks), leaks[:10])
