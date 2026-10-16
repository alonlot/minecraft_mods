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
]
fig_look = [
    Y(0, 180, 8), Y(3.5, 166, 6), Y(6.5, 192, 10), Y(7.6, 180, 42), Y(12.8, 180, 30),
    Y(14.4, 180, 10), Y(16.6, 180, 0), Y(17.6, 0, 0), Y(24.6, 0, 0), Y(25.6, 0, -10),
    Y(26.2, 0, 0), Y(30.0, 0, 0), Y(31.4, 0, 10), Y(32.0, 0, 30), Y(33.6, 0, 5),
    Y(38.2, 0, 0), Y(39.0, 35, 8), Y(39.9, -30, 8), Y(40.6, -50, 12), Y(43.6, -50, 12),
    Y(44.6, -90, 15), Y(50.8, -90, 10), Y(51.4, 0, 20), Y(54.0, 0, 5), Y(61.8, 0, 0),
    Y(65.0, 0, 30), Y(66.0, 0, 50),
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
]
fig_actions = [
    {'t': T(9.0), 'do': 'cast', 'spell': 'revelio', 'target': [0.5, -1.0, -33.0]},
    {'t': T(26.6), 'do': 'cast', 'spell': 'lumos'},
    {'t': T(31.2), 'do': 'swing'},
    {'t': T(31.4), 'do': 'hurt'},
    {'t': T(41.4), 'do': 'cast', 'spell': 'revelio', 'target': [0.5, 0.5, -12.5]},
    {'t': T(45.6), 'do': 'swing'},
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
    'seated': {'right_leg': [-81, 18, 4.5], 'left_leg': [-81, -18, -4.5], 'right_arm': [-36, 0, 0],
               'left_arm': [-36, 0, 0]},
}

# ---------------------------------------------------------------- events
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
snd(72.0, 'minecraft:block.beacon.ambient', at=[0, 0, 8], volume=0.6, pitch=0.55)
for t in (63.0, 65.0, 67.0, 69.0, 71.0, 73.0, 75.0):
    part(t, 'minecraft:glow', (1, 1, 9), 5, (0.25, 0.15, 0.25))

# ---------------------------------------------------------------- words (the Pensieve's voice)
say(1.0, 'A cliff above a black, restless sea.')
say(10.6, 'Revelio shows what the rock hides.')
say(18.2, 'The way in lies beneath the water.')
say(33.0, 'The arch takes its toll in blood from all who enter.', 4.5)
say(42.4, 'Revelio again. The chain was always there.')
say(48.8, 'Pull it, and the boat rises to meet you.')
say(55.4, 'Stay in the boat. The lake does not welcome swimmers.', 4.5)
say(63.2, 'At its heart, the Basin of Despair.')
say(70.0, 'Remember the way. A map will show you where.', 5)

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
shot(71.0, (3.4, 1.4, 7.4), (0.5, 1.1, 7.6), 52, cut=False)
# S17 the slow push-in over his shoulder into the basin; the fade comes at the end
shot(71.0, (2.2, 2.8, 4.0), (0.5, 0.9, 8.5), 56)
shot(78.0, (1.3, 2.3, 5.5), (0.5, 0.9, 8.5), 40, cut=False, ease='in_out')

LENGTH = 78.0
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
    'actors': [figure, boat],
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

txt = write_scene(scene, 'locket_cave')
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
