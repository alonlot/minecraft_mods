"""Generates data/hogwartsmagic/memories/summoning.json: the Dark Summoning of Voldemort, as a tutorial film.

Usage (from the repo root): python build_scripts/pensieve_scenes/summoning.py

What it teaches is the real rite (VoldemortSummoningEntity, SpellBeamEntity, the guide's "A dark rite" page):
- three Wards (hogwartsmagic:ward) on one level, none more than 4 blocks (x and z) from the one struck;
- the shape formsDiagonalTriangle() accepts: two legs of horizontal distance^2 8 and a base of 16, i.e. two Wards
  four apart on a straight line and the third two to the side of their midpoint ("a roof");
- a Morsmordre beam striking any one of them starts the ritual (SpellBeamEntity, MORSMORDRE branch -> tryStart);
- the ritual storms for SUMMON_DELAY_TICKS (160 = 8 s) with a strike every 10 ticks, the Dark Mark at tick 110;
- Voldemort then appears at the triangle's centre with a bolt, flash, ink and smoke, Resistance V for 100 ticks
  (nothing hurts him for 5 s), and 4..6 Death Eater guards with a bolt each, within 9 blocks.

Scene frame: the clearing's grass top is block y -1, so actors stand at y 0. The Wards are A (-2,0,0), B (0,0,2),
C (0,0,-2): the right angle (A) on the -x side, matching VoldemortSummoningEntity.emitRitualEffects. The real
centre of that trio is (-0.17, 0.5, 0.5) in block terms; the summoning_ritual effect's `at` is an integer corner,
so it sits at (0,0,0) and its columns rise 0.17 east and 0.5 north of each Ward's middle (see the report).
"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import write_scene, physics_problems

R = 30
LENGTH = 53.0

# ---------------------------------------------------------------- helpers (same conventions as chamber.py)
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
def say(t, text, dur=None):
    w = {'t': T(t), 'text': text, 'narration': True}
    if dur: w['duration'] = T(dur)
    words.append(w)
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
def yaw_to(a, b):
    """Minecraft yaw from a to b (0 = +Z, 90 = -X), using x and z of (x, y, z) or (x, z) tuples."""
    az, bz = a[-1], b[-1]
    return math.degrees(math.atan2(-(b[0] - a[0]), bz - az))

WARD = 'hogwartsmagic:ward'
DE_SKIN = {'texture': 'death_eater'}

# ---------------------------------------------------------------- the rite's geometry
WARD_A, WARD_B, WARD_C = (-2, 0, 0), (0, 0, 2), (0, 0, -2)
def d2(p, q):
    return (p[0] - q[0]) ** 2 + (p[2] - q[2]) ** 2
# VoldemortSummoningEntity.formsDiagonalTriangle, and the 9x9 search box around the struck Ward (A)
assert sorted([d2(WARD_A, WARD_B), d2(WARD_A, WARD_C), d2(WARD_B, WARD_C)]) == [8, 8, 16]
assert all(abs(w[0] - WARD_A[0]) <= 4 and abs(w[2] - WARD_A[2]) <= 4 and w[1] == WARD_A[1] for w in (WARD_B, WARD_C))
# getTriangleCenter: mean of the block corners + 0.5 (x, z); where Voldemort stands
V = (r2((WARD_A[0] + WARD_B[0] + WARD_C[0]) / 3 + 0.5), 0, r2((WARD_A[2] + WARD_B[2] + WARD_C[2]) / 3 + 0.5))
RITUAL_AT = (0, 0, 0)       # integer corner nearest V: emitRitualEffects puts the columns at -4/3,0 / 2/3,+-2 from it
A_TOP = (WARD_A[0] + 0.5, 1.0, WARD_A[2] + 0.5)   # where the curse strikes Ward A

T_C, T_B, T_A = 9.4, 12.6, 15.9                    # the Wards go down: C, then B (the base), then A (the apex)
T_CAST = 24.0
T_HIT = 24.1                                      # 2.9 blocks at 1.55 a tick: two ticks
RITUAL_TICKS = 160                                # SUMMON_DELAY_TICKS
T_MARK = round(T_HIT + 110 / 20, 2)               # markSpawned at tickCount 110
T_SUMMON = round(T_HIT + RITUAL_TICKS / 20, 2)    # 32.1

# ---------------------------------------------------------------- the set: a dark clearing ringed by dark oaks
fills = [
    {'block': 'minecraft:dirt', 'from': [-R, -5, -R], 'to': [R, -2, R]},
    {'block': 'minecraft:grass_block', 'from': [-R, -1, -R], 'to': [R, -1, R]},
]
disks = [
    {'block': 'minecraft:podzol', 'center': [0, -1, 0], 'radius': 9},
    {'block': 'minecraft:podzol', 'center': [-8, -1, -9], 'radius': 3},
    {'block': 'minecraft:podzol', 'center': [9, -1, 6], 'radius': 2},
    {'block': 'minecraft:podzol', 'center': [-10, -1, 7], 'radius': 2},
    {'block': 'minecraft:coarse_dirt', 'center': [0, -1, 0], 'radius': 3},
]
# Trees: (x, z) of the trunk's corner; a dark oak trunk is 2x2 (x..x+1, z..z+1), its crown reaches ~4 further.
# The north gap is the path in; the south-east is kept open so the last shot sees the sky.
TREES = [(-1, -25), (-9, -17), (6, -17), (-15, -12), (13, -13), (-19, -5), (-18, 4), (-14, 12), (18, -6),
         (19, 3), (-8, 17), (-2, 22), (11, 18), (16, 11), (-12, -21), (12, -21), (-23, -4), (22, -15),
         (5, -23), (-20, 16), (-23, 9), (23, 6)]
features = [{'id': 'minecraft:dark_oak', 'at': [x, 0, z]} for x, z in TREES]
# Undergrowth: deterministic scatter outside the ritual ground and off the walking lanes.
undergrowth = []
seed = 7
def rnd():
    global seed
    seed = (seed * 1103515245 + 12345) % 2147483648
    return seed / 2147483648
while len(undergrowth) < 70:
    x, z = int(rnd() * 2 * (R - 2)) - (R - 2), int(rnd() * 2 * (R - 2)) - (R - 2)
    r = math.hypot(x, z)
    if r < 8 or r > 24:
        continue
    if -6 <= x <= 6 and z < -6:                    # the path in
        continue
    if any(tx - 1 <= x <= tx + 2 and tz - 1 <= z <= tz + 2 for tx, tz in TREES):
        continue
    plant = ('minecraft:fern', 'minecraft:short_grass', 'minecraft:short_grass', 'minecraft:dead_bush')[int(rnd() * 4)]
    if plant == 'minecraft:dead_bush' and r < 12:
        plant = 'minecraft:fern'
    if (x, z) not in [(u[0], u[2]) for u, _ in undergrowth]:
        undergrowth.append(((x, 0, z), plant))
for (x, y, z), plant in sorted(undergrowth):
    fills.append({'block': plant, 'from': [x, y, z], 'to': [x, y, z]})
# Moonlight: three invisible light blocks well above the ground, so black robes still read at midnight.
LIGHTS = [(0, 5, 0), (0, 5, -11), (-1, 6, 9)]
for p in LIGHTS:
    fills.append({'block': 'minecraft:light', 'from': list(p), 'to': list(p)})

# ---------------------------------------------------------------- the three Death Eaters
WANDS = {'de1': 'hogwartsmagic:dragon_core_wand', 'de2': 'hogwartsmagic:unicorn_core_wand',
         'de3': 'hogwartsmagic:dragon_core_wand'}
RING = {'de1': (-4.4, 0, 0.5), 'de2': (2.4, 0, 3.9), 'de3': (2.6, 0, -2.8)}   # ~4.2 from V, facing him
RING_YAW = {k: yaw_to(p, V) for k, p in RING.items()}
KNEEL_T = {'de1': 32.5, 'de2': 32.7, 'de3': 32.9}

def placing(t_place, yaw):
    """The look, actions for laying a Ward: crouch, look down, swing; then stand and draw the wand."""
    look = [Y(t_place - 0.8, yaw, 0), Y(t_place - 0.4, yaw, 50)]
    acts = [A(t_place - 0.4, 'sneak'), A(t_place, 'swing'), A(t_place + 0.5, 'stand')]
    return look, acts

def ritual_look(aid):
    """During the rite: heads lift to the columns, then to the Mark; then they kneel to him, heads bowed."""
    y = RING_YAW[aid]
    return [Y(T_HIT, y, 0), Y(T_HIT + 1.6, y, -22), Y(T_MARK, y, -24), Y(T_MARK + 1.6, y, -42),
            Y(KNEEL_T[aid] - 0.2, y, -10), Y(KNEEL_T[aid] + 0.6, y, 18)]

# de3 lays C (the north end of the base)
D3_AT = (WARD_C[0] + 0.5, 0, WARD_C[2] - 1.4)
de3_path = [K(0, (0.6, 0, -21.0)), K(1.4, (0.6, 0, -21.0)), K(8.4, D3_AT, 'out'), K(10.6, D3_AT),
            K(11.8, RING['de3'], 'in_out')]
look, acts = placing(T_C, 0)
de3_look = [Y(0, 0, 4), Y(8.0, 0, 6)] + look + [Y(10.6, 0, 10), Y(11.8, RING_YAW['de3'], 0)] + ritual_look('de3')
de3_actions = acts + [A(10.4, 'hold', item=WANDS['de3'])]
block(T_C, WARD, WARD_C)

# de2 lays B (the south end), walking round the east side
D2_AT = (WARD_B[0] + 0.5, 0, WARD_B[2] + 2.4)
de2_path = [K(0, (2.8, 0, -20.5)), K(1.0, (2.8, 0, -20.5)), K(7.0, (3.0, 0, -4.5), 'in_out'),
            K(10.4, (2.6, 0, 4.4), 'in_out'), K(11.4, D2_AT, 'out'), K(13.8, D2_AT), K(15.0, RING['de2'], 'in_out')]
look, acts = placing(T_B, 180)
de2_look = ([Y(0, 0, 4), Y(7.0, 0, 4), Y(10.0, 20, 4), Y(11.4, 180, 10)] + look +
            [Y(13.8, 180, 10), Y(15.0, RING_YAW['de2'], 0)] + ritual_look('de2'))
de2_actions = acts + [A(13.6, 'hold', item=WANDS['de2'])]
block(T_B, WARD, WARD_B)

# de1 lays A (the apex, two to the side of the base's middle) and strikes it with Morsmordre
D1_AT = (WARD_A[0] - 1.4, 0, WARD_A[2] + 0.5)
de1_path = [K(0, (-2.2, 0, -20.5)), K(2.0, (-2.2, 0, -20.5)), K(8.6, (-4.6, 0, -6.0), 'in_out'),
            K(12.2, (-4.6, 0, -6.0)), K(14.4, D1_AT, 'in_out'), K(17.0, D1_AT), K(17.8, RING['de1'], 'in_out')]
look, acts = placing(T_A, -90)
de1_look = ([Y(0, 0, 4), Y(8.6, -20, 6), Y(9.6, -45, 12), Y(12.4, -30, 10), Y(14.4, -90, 10)] + look +
            [Y(16.6, -90, 10), Y(17.8, -90, 0), Y(T_CAST - 1.0, -90, 12)])
de1_look += ritual_look('de1')   # after the strike the head lifts with the rest
de1_poses = [P(T_CAST - 0.8, 'wand_raised', 6), P(T_CAST - 0.05, 'wand_point', 2), P(T_CAST + 1.0, 'relax', 10)]
de1_actions = acts + [A(16.8, 'hold', item=WANDS['de1']),
                      A(T_CAST, 'cast', spell='morsmordre', target=[r2(c) for c in A_TOP])]
block(T_A, WARD, WARD_A)

def kneel(aid):
    return [P(KNEEL_T[aid], 'kneel', 8)]

de1 = {'id': 'de1', 'type': 'player', 'skin': DE_SKIN, 'items': {'mainhand': WARD}, 'path': de1_path,
       'look': de1_look, 'poses': de1_poses + kneel('de1'), 'actions': de1_actions}
de2 = {'id': 'de2', 'type': 'player', 'skin': DE_SKIN, 'items': {'mainhand': WARD}, 'path': de2_path,
       'look': de2_look, 'poses': kneel('de2'), 'actions': de2_actions}
de3 = {'id': 'de3', 'type': 'player', 'skin': DE_SKIN, 'items': {'mainhand': WARD}, 'path': de3_path,
       'look': de3_look, 'poses': kneel('de3'), 'actions': de3_actions}

# ---------------------------------------------------------------- Voldemort
UNDER = -4.0
T_TURN0, T_TURN1 = 46.6, 47.8
CAM_TURN = (-2.2, 1.75, 3.6)                 # S13's camera: he turns to face it
vold_path = [K(0, (V[0], UNDER, V[2])), K(T_SUMMON - 0.05, (V[0], UNDER, V[2])),
             K(T_SUMMON, (V[0], -1.9, V[2]), 'linear'),          # hidden in the ground, then up through the smoke
             K(T_SUMMON + 1.4, (V[0], 0.5, V[2]), 'out'),          # Tenebris Flight holds him up a moment...
             K(T_SUMMON + 2.5, V, 'in_out')]                        # ...and sets him down
vold_look = [Y(0, 90, 0), Y(T_SUMMON + 1.4, 90, -20), Y(T_SUMMON + 2.9, 90, 0),
             Y(36.4, 125, -4), Y(38.4, 70, 0), Y(40.0, 90, 30),     # surveys his guards, then looks down at de1
             Y(T_TURN0, 90, 30), Y(T_TURN1, yaw_to(V, CAM_TURN), 2, 'in_out')]
vold_poses = [P(T_SUMMON, 'rising', 1), P(T_SUMMON + 2.7, 'relax', 14)]
vold_actions = [A(0, 'vanish'), A(T_SUMMON, 'appear')]
voldemort = {'id': 'voldemort', 'type': 'player', 'skin': {'texture': 'voldemort'},
             'items': {'mainhand': 'hogwartsmagic:harry_potter_wand'},
             'path': vold_path, 'look': vold_look, 'poses': vold_poses, 'actions': vold_actions}

# ---------------------------------------------------------------- his guards (summonDeathEaters: 4..6, a bolt each)
GUARDS = [(-6.0, 0, -5.0), (6.2, 0, -4.2), (5.6, 0, 6.4), (-5.4, 0, 6.6)]
guards = []
for i, g in enumerate(GUARDS):
    tg = round(T_SUMMON + 0.05 * (i + 1), 2)
    out = yaw_to(V, g)                                  # facing outward, wands ready
    guards.append({
        'id': 'guard%d' % (i + 1), 'type': 'player', 'skin': DE_SKIN,
        'items': {'mainhand': ('hogwartsmagic:dragon_core_wand', 'hogwartsmagic:unicorn_core_wand')[i % 2]},
        'path': [K(0, (g[0], UNDER, g[2])), K(tg - 0.05, (g[0], UNDER, g[2])), K(tg, g, 'linear')],
        'look': [Y(0, out, 0), Y(tg + 1.0, out, 0), Y(tg + 2.0, out + (14 if i % 2 else -14), 4),
                 Y(tg + 6.0, out - (10 if i % 2 else -10), 0)],
        'poses': [P(tg + 0.8, 'wand_point', 8)],
        'actions': [A(0, 'vanish'), A(tg, 'appear')],
    })
    effect(tg, 'lightning', g)
    part(tg, 'minecraft:large_smoke', (g[0], 1, g[2]), 35, (0.45, 0.9, 0.45))
    part(tg, 'minecraft:squid_ink', (g[0], 1, g[2]), 20, (0.3, 0.8, 0.3))

poses = {
    'relax': {'head': [0, 0, 0]},
    'rising': {'right_arm': [-12, 0, 38], 'left_arm': [-12, 0, -38], 'head': [-14, 0, 0]},
}

# ---------------------------------------------------------------- events
# night forest, low fog all film long
snd(0.0, 'minecraft:ambient.cave', volume=0.5)
snd(2.2, 'minecraft:entity.wolf.howl', at=(-24, 2, -20), volume=0.35, pitch=0.8)
t = 0.0
i = 0
while t < LENGTH - 0.4:
    cx, cz = ((0, -2), (-4, 4), (4, -6), (2, 6))[i % 4]
    part(t, 'minecraft:cloud', (cx, 0, cz), 16, (13, 0.15, 13))
    if i % 3 == 0:
        part(t, 'minecraft:cloud', (0, 0, -16), 8, (4, 0.15, 4))   # mist on the path in
    t = round(t + 0.5, 2)
    i += 1
# the Wards go down (WardBlock copies amethyst: its place sound)
for tp, w in ((T_C, WARD_C), (T_B, WARD_B), (T_A, WARD_A)):
    snd(tp, 'minecraft:block.amethyst_block.place', at=(w[0], 0, w[2]), volume=1.0, pitch=0.8)
    part(tp + 0.05, 'minecraft:enchant', (w[0], 1, w[2]), 12, (0.3, 0.3, 0.3))
# the curse: a short black streak (the beam's squid-ink trail) and the ritual's opening, as tryStart sends it
src = (RING['de1'][0] + 0.45, 1.35, RING['de1'][2] + 0.3)
for k in range(1, 4):
    f = k / 4
    q = [src[c] + (A_TOP[c] - src[c]) * f for c in range(3)]
    part(T_CAST + 0.05 * (k - 1), 'minecraft:squid_ink', q, 3, (0.05, 0.05, 0.05))
part(T_HIT, 'minecraft:squid_ink', (A_TOP[0], 1, A_TOP[2]), 40, (0.35, 0.6, 0.35))
part(T_HIT, 'minecraft:witch', (A_TOP[0], 1, A_TOP[2]), 18, (0.35, 0.5, 0.35))
snd(T_HIT, 'minecraft:block.end_portal.spawn', at=WARD_A, volume=0.6, pitch=0.55)
snd(T_HIT, 'hogwartsmagic:spell.morsmordre', at=WARD_A, volume=1.5, pitch=0.55)
# the ritual itself: the three columns, swirling smoke and its own strikes, for exactly 160 ticks
effect(T_HIT, 'summoning_ritual', RITUAL_AT, RITUAL_TICKS)
# the storm it raises (setWeatherParameters: rain and thunder); extra bolts with NO duration (no late-join replay)
for tl, at in ((25.2, (7, 0, -5)), (27.8, (-6, 0, 6)), (30.4, (5, 0, 7))):
    effect(tl, 'lightning', at)
t = T_HIT + 1.0
while t < LENGTH - 0.4:
    part(t, 'minecraft:rain', (0, 0, 0), 60, (14, 0.1, 14))
    t = round(t + 0.5, 2)
for tr in (25.4, 29.0, 32.6, 36.2, 39.8, 43.4, 47.0, 50.6):
    snd(tr, 'minecraft:weather.rain', volume=0.45, pitch=0.9)
# the Mark answers, at tick 110 (the real one hangs 26 blocks up for three minutes; the memory's rises 18)
effect(T_MARK, 'dark_mark', (0, 4, 0), int(round((LENGTH - T_MARK) * 20)))
snd(T_MARK, 'minecraft:entity.ender_dragon.growl', at=(0, 8, 0), volume=1.2, pitch=0.55)
# he comes: a bolt where he stands, flash, ink and smoke (summonVoldemort), the wither's cry
effect(T_SUMMON, 'lightning', V)
part(T_SUMMON, 'minecraft:flash', (V[0], 1, V[2]), 6, (0.3, 0.4, 0.3))
part(T_SUMMON, 'minecraft:squid_ink', (V[0], 1, V[2]), 70, (0.45, 0.9, 0.45))
part(T_SUMMON, 'minecraft:large_smoke', (V[0], 1, V[2]), 90, (0.6, 1.0, 0.6))
part(T_SUMMON + 0.5, 'minecraft:large_smoke', (V[0], 1, V[2]), 50, (0.8, 1.2, 0.8))
part(T_SUMMON + 1.0, 'minecraft:large_smoke', (V[0], 0, V[2]), 30, (1.0, 0.4, 1.0))
snd(T_SUMMON, 'minecraft:entity.wither.spawn', at=V, volume=1.2, pitch=0.7)
snd(T_SUMMON + 0.3, 'hogwartsmagic:spell.morsmordre', at=V, volume=1.25, pitch=0.65)
snd(T_TURN0 + 0.2, 'minecraft:entity.lightning_bolt.thunder', volume=0.5, pitch=0.6)

# ---------------------------------------------------------------- words (the Pensieve's voice)
say(1.0, 'Deep in the forest, they came to call him back.', 4.0)
say(5.6, 'The rite wants three Wards, laid on one level.', 3.6)
say(12.4, 'Two, four paces apart, on a straight line.', 3.2)
say(15.8, 'The third, two paces aside from their middle.', 3.0)
say(19.0, 'Seen from above, a roof: two slopes and a flat base.', 3.4)
say(22.8, 'Then cast Morsmordre at any one of the three.', 3.4)
say(26.4, 'If the shape is true, the Mark answers.', 3.0)
say(29.6, 'Eight seconds after the strike...', 2.4)
say(32.2, '...he rises where the three lines meet.', 3.2)
say(35.8, 'He never comes alone.', 2.8)
say(39.0, 'For his first five seconds, no spell can harm him.', 3.4)
say(47.8, 'What the rite calls is not to be met unprepared.', 4.6)

# ---------------------------------------------------------------- camera
VO = 'actor:voldemort'
# S1 establishing: high over the south-east, the three come out of the trees on the north path
shot(0.0, (9.0, 12.0, 12.0), (0, 1.0, -6.0), 84, cut=False)
shot(5.5, (7.4, 10.6, 9.8), (0, 0.5, -4.0), 80, cut=False)
# S2 side on, low: they walk in carrying the Wards
shot(5.5, (6.8, 1.4, -9.0), 'actor:de3', 60)
shot(9.0, (6.4, 1.4, -6.6), 'actor:de3', 58, cut=False)
# S3 the first Ward: from the west, de3 crouches and sets C down
shot(9.0, (-3.4, 1.2, -3.2), (0.5, 0.9, -2.7), 52)
shot(12.2, (-3.2, 1.2, -3.0), (0.5, 1.0, -2.6), 50, cut=False)
# S4 straight down the base line from behind C: B goes down four paces beyond it
shot(12.2, (0.5, 2.4, -7.5), (0.5, 0.3, 2.5), 50)
shot(15.5, (0.5, 2.3, -7.0), (0.5, 0.3, 2.5), 48, cut=False)
# S5 from the east between B and C: de1 lays the apex, facing us
shot(15.5, (3.5, 1.3, 0.5), (-2.8, 1.2, 0.5), 56)
shot(18.0, (3.0, 1.25, 0.5), (-2.8, 1.2, 0.5), 54, cut=False)
# S6 overhead, facing west so the apex points up the screen: the roof
shot(18.0, (1.4, 13.0, 0.5), (-0.6, 0, 0.5), 52)
shot(22.6, (1.2, 11.2, 0.5), (-0.6, 0, 0.5), 50, cut=False)
# S7 over de1's shoulder: the curse strikes Ward A
shot(22.6, (-6.6, 2.0, 2.0), (-1.5, 0.7, 0.3), 55)
shot(26.0, (-6.3, 1.9, 1.7), (-1.5, 0.9, 0.3), 52, cut=False)
# S8 low from the north: the columns climb (they rise on the north faces of the Wards, toward us)
shot(26.0, (0.4, 1.4, -9.0), (-0.2, 3.5, 0.5), 80)
shot(29.4, (0.3, 1.2, -7.6), (-0.2, 7.0, 0.5), 84, cut=False)
# S9 low from the south-west, looking up: the Mark rises over the trees
shot(29.4, (-5.5, 0.5, 7.5), (0, 9.0, 0), 88, roll=5)
shot(32.0, (-5.0, 0.5, 6.8), (0, 14.0, 0), 90, cut=False, roll=7)
# S10 THE RISING: from the north between C and de3, he comes up through the smoke in profile; hold
shot(32.0, (-0.6, 1.0, -4.6), (V[0], 1.3, V[2]), 60)
shot(34.8, (-0.5, 1.1, -3.9), (V[0], 1.7, V[2]), 54, cut=False, ease='out')
# S11 high from the south-west: his guards stand in a ring, wands out
shot(36.0, (-6.5, 7.0, 9.5), (0, 0.5, -0.5), 80)
shot(40.0, (-6.0, 6.2, 8.4), (0, 0.5, -0.5), 76, cut=False)
# S12 low from the north: master and servant in profile, the struck Ward between them
shot(40.0, (-2.6, 0.7, -4.2), (-2.3, 1.0, 0.5), 55)
shot(44.2, (-2.5, 0.7, -3.8), (-2.3, 1.0, 0.5), 52, cut=False)
# S13 close on him; he turns toward us, and we cut away mid-turn
shot(44.2, CAM_TURN, VO, 46)
shot(47.4, (-2.0, 1.75, 3.3), VO, 42, cut=False)
# S14 the clearing under the Mark: a slow push in to the fade
shot(47.4, (3.6, 1.2, 14.6), (0, 8.2, 0.5), 74)
shot(LENGTH, (2.9, 1.3, 12.6), (0, 8.0, 0.5), 70, cut=False, ease='in_out')

actors = [de1, de2, de3, voldemort] + guards
scene = {
    'title': 'The Memory of the Summoning',
    'length': T(LENGTH),
    'radius': R,
    'set': {
        'fills': fills,
        'disks': disks,
        'features': features,
        'time_of_day': 18000,
        'build_ticks': 60,
    },
    'poses': poses,
    'actors': actors,
    'events': sorted(events, key=lambda e: float(e['t'][:-1])),
    'words': words,
    'camera': cam,
}

# ---------------------------------------------------------------- checks
problems = []
def tval(s):
    return float(s[:-1]) if isinstance(s, str) else s / 20.0

def ease_f(f, e):
    if e == 'linear': return f
    if e == 'in': return f * f * f
    if e == 'out': return 1 - (1 - f) ** 3
    return 4 * f * f * f if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2

def sample(keys, t, field='pos', n=3):
    ks = [(tval(k['t']), k[field] if field != 'yawpitch' else [k['yaw'], k['pitch']], k.get('ease', 'in_out'))
          for k in keys]
    if t <= ks[0][0]: return ks[0][1]
    for (t0, a, _), (t1, b, e) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else ease_f((t - t0) / (t1 - t0), e)
            return [a[i] + (b[i] - a[i]) * f for i in range(len(a))]
    return ks[-1][1]

# times sorted per track and inside 0..length
for a in actors:
    for field in ('path', 'look', 'poses', 'actions'):
        ts = [tval(k['t']) for k in a.get(field, [])]
        if ts != sorted(ts):
            problems.append('%s %s not sorted: %s' % (a['id'], field, ts))
        if any(x < 0 or x > LENGTH for x in ts):
            problems.append('%s %s time outside the film' % (a['id'], field))
    ys = [k['yaw'] for k in a['look']]
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

# obstacles: Wards (once placed), tree trunks and (roughly) crowns, the ground
ward_t = {WARD_A: T_A, WARD_B: T_B, WARD_C: T_C}
def solid(p, t, crowns=True):
    x, y, z = p
    if y < 0:
        return 'ground'
    c = (math.floor(x), math.floor(y), math.floor(z))
    for w, tw in ward_t.items():
        if t >= tw and c == w:
            return 'ward'
    for tx, tz in TREES:
        if tx <= x < tx + 2 and tz <= z < tz + 2 and y < 9:
            return 'trunk %s' % ((tx, tz),)
        if crowns and tx - 3 <= x < tx + 5 and tz - 3 <= z < tz + 5 and 5 <= y < 11:
            return 'crown %s' % ((tx, tz),)
    return None

# actors: never inside a Ward or trunk while above ground; everyone inside the radius
for a in actors:
    for i in range(int(LENGTH * 20) + 1):
        t = i / 20
        p = sample(a['path'], t)
        if abs(p[0]) > R - 1 or abs(p[2]) > R - 1:
            problems.append('%s outside the radius at %.2f' % (a['id'], t))
            break
        if p[1] < -0.5 or (a['id'] == 'voldemort' and T_SUMMON <= t <= T_SUMMON + 1.4):
            continue   # hidden below, or rising up through the turf on purpose
        for dx, dz in ((-0.3, -0.3), (0.3, -0.3), (-0.3, 0.3), (0.3, 0.3)):
            b = solid((p[0] + dx, p[1] + 0.1, p[2] + dz), t, crowns=False)
            if b:
                problems.append('%s in %s at t=%.2f pos=%s' % (a['id'], b, t, [round(v, 2) for v in p]))
                break
# the placer stands next to his Ward and faces it
for aid, w, tp in (('de1', WARD_A, T_A), ('de2', WARD_B, T_B), ('de3', WARD_C, T_C)):
    a = {'de1': de1, 'de2': de2, 'de3': de3}[aid]
    p = sample(a['path'], tp)
    d = math.hypot(p[0] - (w[0] + 0.5), p[2] - (w[2] + 0.5))
    yw, pt = sample(a['look'], tp, 'yawpitch')
    want = yaw_to(p, (w[0] + 0.5, 0, w[2] + 0.5))
    if not 1.0 < d < 2.2 or abs(((yw - want) + 180) % 360 - 180) > 12:
        problems.append('%s placing %s from %.2f away, yaw %.0f vs %.0f' % (aid, w, d, yw, want))

EYE = 1.62
def target(look, t):
    if isinstance(look, str):
        aid = look.split(':')[1]
        a = next(x for x in actors if x['id'] == aid)
        p = sample(a['path'], t)
        return [p[0], p[1] + EYE, p[2]]
    return look
def los(pos, tgt, t, label):
    n = int(math.dist(pos, tgt) * 10)
    for m in range(1, n):
        q = [pos[c] + (tgt[c] - pos[c]) * m / n for c in range(3)]
        if math.dist(q, tgt) < 0.6:
            break
        bb = solid(q, t)
        if bb:
            problems.append('%s: view blocked by %s at %s' % (label, bb, [round(v, 1) for v in q]))
            return
for i, k in enumerate(cam):
    t = tval(k['t'])
    if solid(k['pos'], t):
        problems.append('camera %d at %s inside %s' % (i, k['t'], solid(k['pos'], t)))
    los(k['pos'], target(k['look_at'], t), t, 'camera %d at %s' % (i, k['t']))
for a, b2 in zip(cam, cam[1:]):
    if b2.get('cut'):
        continue
    t0, t1 = tval(a['t']), tval(b2['t'])
    for j in range(1, 10):
        f = j / 10
        t = t0 + (t1 - t0) * f
        pos = [a['pos'][c] + (b2['pos'][c] - a['pos'][c]) * f for c in range(3)]
        if solid(pos, t):
            problems.append('camera between %s and %s inside %s' % (a['t'], b2['t'], solid(pos, t)))
        ta, tb = target(a['look_at'], t), target(b2['look_at'], t)
        los(pos, [ta[c] + (tb[c] - ta[c]) * f for c in range(3)], t, 'camera %s..%s t=%.1f' % (a['t'], b2['t'], t))
# the cut away: he must be mid-turn toward the S13 camera when the last shot cuts in
T_CUT = tval(cam[-2]['t'])
yw = sample(vold_look, T_CUT, 'yawpitch')[0]
y0, y1 = vold_look[-2]['yaw'], vold_look[-1]['yaw']
frac = (yw - y0) / (y1 - y0)
print('turn at the cut: yaw %.1f, %.0f%% of the way to the camera' % (yw, frac * 100))
if not 0.5 < frac < 0.95:
    problems.append('the cut does not land mid-turn (%.2f)' % frac)
# lightning: never a duration (a late joiner would see an old strike replayed)
for e in events:
    if e['kind'] == 'effect' and e['id'] == 'lightning' and 'duration' in e:
        problems.append('lightning with a duration at %s' % e['t'])
# extents: fills, disks, feature crowns (trunk + ~5)
for f in fills:
    for v in (f['from'], f['to']):
        if abs(v[0]) > R or abs(v[2]) > R:
            problems.append('fill beyond radius %s' % f)
for d in disks:
    c = d['center']
    if abs(c[0]) + d['radius'] > R or abs(c[2]) + d['radius'] > R:
        problems.append('disk beyond radius %s' % d)
for x, z in TREES:
    if max(abs(x - 4), abs(x + 6)) > R or max(abs(z - 4), abs(z + 6)) > R:
        problems.append('tree crown may be cropped at %s' % ((x, z),))
    for u, v in TREES:
        if (u, v) != (x, z) and abs(u - x) < 4 and abs(v - z) < 4:
            problems.append('trees crowd %s %s' % ((x, z), (u, v)))
# shots, words, events
cuts = [0.0] + [tval(k['t']) for k in cam if k.get('cut')] + [LENGTH]
lens = [round(b2 - a, 2) for a, b2 in zip(cuts, cuts[1:])]
print('shots', len(lens), lens, 'total', sum(lens))
for l in lens:
    if l < 2 or l > 6:
        problems.append('shot length %s' % l)
wt = [(tval(w['t']), tval(w.get('duration', 80))) for w in words]
for (a, d), (b2, _) in zip(wt, wt[1:]):
    if a + d > b2 + 1e-6:
        problems.append('narration at %.1f overlaps the next at %.1f' % (a, b2))
    if b2 - a < 2:
        problems.append('narration closer than 2 s at %.1f' % a)
if wt[-1][0] + wt[-1][1] > LENGTH:
    problems.append('last line runs past the end')
def walk(o):
    if isinstance(o, dict):
        for v in o.values(): walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)
    elif isinstance(o, float):
        if abs(o * 100 - round(o * 100)) > 1e-6:
            problems.append('float noise %r' % o)
walk(scene)
print('V', V, 'hit', T_HIT, 'mark', T_MARK, 'summon', T_SUMMON)
print('events', len(events), 'fills', len(fills), 'trees', len(TREES), 'actors', len(actors))
# physics: the fills and disks (the dark oak features place only logs and leaves)
set_world = {}
for f in fills:
    a, b = f['from'], f['to']
    for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
        for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
            for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                set_world[(x, y, z)] = f['block']
for d in disks:
    cx, cy, cz = d['center']
    for dx in range(-d['radius'], d['radius'] + 1):
        for dz in range(-d['radius'], d['radius'] + 1):
            if dx * dx + dz * dz <= d['radius'] ** 2:
                set_world[(cx + dx, cy, cz + dz)] = d['block']
problems += physics_problems(set_world, R)
print('\n'.join(problems) if problems else 'NO PROBLEMS')

txt = write_scene(scene, 'summoning')
print('chars', len(txt))
