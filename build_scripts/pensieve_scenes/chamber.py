"""Generates data/hogwartsmagic/memories/chamber.json and checks it against the real chamber_hall template.

Usage (from the repo root): python build_scripts/pensieve_scenes/chamber.py

Scene frame: scene = template local + (-25, -7, -50). So the causeway (local x 21..29, top block y 6) is scene
x -4..4 with its walk surface at scene y 0, the antechamber floor (local top y 5) is walk y -1, the pools' water
(local y 2..5) is scene y -5..-2, the round door is local x 21..29 y 7..13 z 20..22 -> scene z -30..-28, and the
statue's mouth sill (local y 12, z 90..98) is walk y 6 at scene z 40..48.
"""
import json, math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import template_grid, write_scene

raw = template_grid('chamber_hall')
OX, OY, OZ = -25, -7, -50
R = 52                      # template spans x -25..25, z -50..50: everything is kept, nothing is cropped
LENGTH = 90.0

def L2S(x, y, z):
    return (x + OX, y + OY, z + OZ)

world = {}                  # scene cell -> block id (no properties); air is left out
for (x, y, z), n in raw.items():
    b = n.split('{')[0]
    if b != 'minecraft:air':
        world[L2S(x, y, z)] = b
TEMPLATE_CELLS = set(L2S(*p) for p in raw)

# ---------------------------------------------------------------- the set: invisible light, nothing else
fills = []
LIGHTS = [(0, 4, -40), (0, 6, -24), (0, 6, -14), (0, 6, -4), (0, 6, 6), (0, 6, 16), (0, 6, 26), (0, 6, 35)]
for p in LIGHTS:
    assert p in TEMPLATE_CELLS and p not in world, ('light not in open air', p, world.get(p))
    fills.append({'block': 'minecraft:light', 'from': list(p), 'to': list(p)})
    world[p] = 'minecraft:light'
OPEN = {'minecraft:water', 'minecraft:light', 'minecraft:air'}

# ---------------------------------------------------------------- helpers
def r2(v):
    return round(v + 0.0, 2)

def T(s):
    return '%gs' % round(s, 2)

events, words, cam = [], [], []
def ev(t, kind, id_, **kw):
    e = {'t': T(t), 'kind': kind, 'id': id_}
    e.update(kw)
    events.append(e)
def snd(t, id_, at=None, volume=None, pitch=None):
    kw = {}
    if at is not None: kw['at'] = [int(round(c)) for c in at]
    if volume is not None: kw['volume'] = volume
    if pitch is not None: kw['pitch'] = pitch
    ev(t, 'sound', id_, **kw)
def part(t, id_, at, count, spread):
    ev(t, 'particles', id_, at=[int(round(c)) for c in at], count=count, spread=[r2(s) for s in spread])
def block(t, id_, at):
    ev(t, 'block', id_, at=list(at))
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
def V(t, v, ease=None):
    k = {'t': T(t), 'v': [r2(c) for c in v]}
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
    """Minecraft yaw from a to b (0 = +Z, 90 = -X)."""
    return math.degrees(math.atan2(-(b[0] - a[0]), b[2] - a[2]))

WAND = 'hogwartsmagic:harry_potter_wand'
DRAUGHT = 'hogwartsmagic:mandrake_draught'

# ---------------------------------------------------------------- the Basilisk
# Body: the memory actor never ticks, so its position trail is empty and the spine runs straight back from the
# head along the body yaw (BasiliskEntity.samplePath runout), about 14 blocks. It keeps yaw 180 (facing north,
# down the causeway) the whole film so the body always lies along the causeway; the head turns with limbs.
BASI_LAIR = (0, 6, 52)       # behind the statue's back wall (wall scene z 49..50): hidden
B_IDLE = (0, 0, 27.0)
B_FRONT = (0.3, 0, 13.0)
B_LUNGE = (0.6, 0, 9.6)
B_BACK = (0.4, 0, 11.5)
B_DEEP = -3.6               # buried under the causeway (solid down to scene y -6)
B_UP = (2.0, 0, -6.4)       # surfaces 1.8 blocks short of the hero, as tickBurrowedTravel does
basi_path = [
    K(0, BASI_LAIR), K(17.0, BASI_LAIR),
    K(20.5, (0, 6, 47.5), 'in_out'),                 # slides out of the dark into the mouth: the eyes
    K(21.5, (0, 6, 47.5)),
    K(22.8, (0, 6, 41.0), 'in'),                     # out over the lip
    K(23.8, (0, 1.2, 35.6), 'linear'),               # down the steps
    K(25.5, B_IDLE, 'out'),
    K(35.8, B_IDLE),
    K(40.6, B_FRONT, 'in_out'),                      # closes to strike range (4..12)
    K(43.0, B_FRONT),
    K(43.3, B_LUNGE, 'out'),                         # the strike's lunge (forward impulse 1.1)
    K(44.6, B_BACK, 'in_out'),
    K(51.6, B_BACK),
    K(52.9, (B_BACK[0], B_DEEP, B_BACK[2]), 'in'),   # BURROW_DOWN, 26 ticks
    K(55.0, (B_UP[0], B_DEEP, B_UP[2]), 'linear'),   # travels under the floor at ~0.42 b/t
    K(55.4, B_UP, 'out'),                            # BURROW_UP: erupts at tick 8
]
basi_look = [Y(0, 180)]
basi_anims = [
    P(0, 'IDLE'), P(20.5, 'IDLE'), P(21.5, 'SLITHER', 6), P(25.5, 'IDLE', 8),
    P(35.8, 'SLITHER', 6), P(40.6, 'IDLE', 6),
    P(41.0, 'REAR', 4), P(42.95, 'STRIKE', 1), P(43.75, 'IDLE', 8),
    P(47.0, 'SPIT', 4), P(48.2, 'IDLE', 8),
    P(51.6, 'BURROW', 4), P(55.0, 'ERUPT', 1), P(56.2, 'IDLE', 8),
    P(61.3, 'BLINDED', 4),
]
basi_actions = []
# The REAR and SPIT shaping lives in BasiliskModel.solveSpine (rearProgress), driven by the action state a memory
# actor does not have: emulate it with limbs. Front segments lift up to 26 px, scaled 1, .8, .6, .4, .2 (front =
# 1 - i/5), and shift 7 px back; the head rides up on the neck.
def rear_track(scale):
    keys = [V(0, (0, 0, 0)),
            V(41.0, (0, 0, 0)), V(41.5, (0, -26 * scale, 7 * scale), 'out'), V(42.95, (0, -26 * scale, 7 * scale)),
            V(43.25, (0, 0, 0), 'in'),                                             # collapses as the head lances out
            V(47.0, (0, 0, 0)), V(47.4, (0, -26 * 0.45 * scale, 7 * 0.45 * scale), 'out'),
            V(48.1, (0, -26 * 0.45 * scale, 7 * 0.45 * scale)), V(48.4, (0, 0, 0))]
    return keys
basi_limbs = {}
for i, name in enumerate(('seg01', 'seg02', 'seg03', 'seg04', 'seg05')):
    basi_limbs[name] = {'offset': rear_track(1 - i / 5)}
head_off = rear_track(1.0)
for k in head_off:
    k['v'][2] = 0                                   # the head rises; it does not slide back into the neck
# Head turns (rot add, degrees; + yaw turns toward +yaw, as netHeadYaw does): a blind basilisk tasting the air.
HEAD_SWAY = [V(0, (0, 0, 0)), V(63.9, (0, 0, 0)),
             V(64.3, (6, -27, 0), 'out'),            # snaps at the hero's footsteps
             V(66.4, (0, -8, 0)), V(68.8, (4, 18, 0)), V(71.4, (-2, -14, 0)), V(74.0, (3, 22, 0)),
             V(76.6, (0, -6, 0)), V(79.2, (5, -24, 0)), V(81.8, (0, 12, 0)), V(84.6, (2, -10, 0)),
             V(87.4, (0, 16, 0)), V(90.0, (0, 0, 0))]
basi_limbs['head'] = {'offset': head_off, 'rot': HEAD_SWAY}
# Blinded: the real renderer drops the glowing eye layer (isBlinded); a memory actor is never blinded, so sink the
# eye cubes into the skull (head box x -8.5..8.5, z -26..-14) where neither the glow nor the socket shows.
basi_limbs['eye_left'] = {'offset': [V(0, (0, 0, 0)), V(61.2, (0, 0, 0)), V(61.5, (4.5, 0, 3), 'in')]}
basi_limbs['eye_right'] = {'offset': [V(0, (0, 0, 0)), V(61.2, (0, 0, 0)), V(61.5, (-4.5, 0, 3), 'in')]}

# ---------------------------------------------------------------- the hero (Gryffindor)
H_GAZE = (1.2, 0, 3.5)
H_BACK = (1.2, 0, -4.5)
H_FAR = (1.2, 0, -8.0)
H_DODGE = (2.4, 0, -8.6)
H_STEP = (2.3, 0, -8.2)
H_LAND = (2.8, 0, -11.6)
H_HEARD = (-0.4, 0, -11.2)
H_END = (-2.9, 0, 8.8)
hero_path = [
    K(0, (0.5, -1, -41.0)), K(6.2, (0.5, -1, -41.0)),
    K(8.6, (0.5, -1, -34.0), 'in_out'),
    K(11.2, (0.5, -1, -34.0)),
    K(11.9, (0.5, -1, -31.1), 'in'),
    K(12.1, (0.5, 0, -31.1), 'out'),                 # a hop up onto the threshold
    K(12.4, (0.5, 0, -29.7), 'linear'),
    K(15.8, (0.6, 0, -20.0), 'linear'),
    K(20.6, (1.0, 0, -6.0), 'linear'),
    K(24.6, H_GAZE, 'out'),
    K(41.3, H_GAZE),
    K(42.9, H_BACK, 'out'),                          # backs off while it rears: out of the strike's reach
    K(44.2, H_BACK),
    K(46.4, H_FAR, 'in_out'),
    K(47.9, H_FAR),
    K(48.3, H_DODGE, 'out'),                         # the venom lands where he stood
    K(53.6, H_DODGE),
    K(54.4, H_STEP, 'in_out'),
    K(55.4, H_STEP),
    K(55.75, (2.6, 2.4, -10.0), 'out'),              # thrown by the eruption
    K(56.2, H_LAND, 'in'),
    K(63.4, H_LAND),
    K(64.0, H_HEARD, 'linear'),                      # two careless steps: it hears them
    K(64.6, H_HEARD),
    K(67.0, (-2.9, 0, -8.6), 'linear'),              # then creeps, crouched
    K(71.0, (-2.9, 0, -1.0), 'linear'),
    K(75.6, H_END, 'out'),
]
B_EYE = (0, 1.3, 25.6)       # the basilisk's eyes at B_IDLE (eye cubes ~1.45 blocks ahead, ~1.3 up)
hero_look = [
    Y(0, 0, 4), Y(6.2, 0, 2), Y(11.2, 0, -4), Y(14.0, -14, -12), Y(16.6, 18, -16), Y(19.0, 0, -4),
    Y(22.0, 2, -6), Y(24.6, yaw_to(H_GAZE, B_EYE), -2),
    Y(41.3, 0, 0), Y(55.4, 0, 0), Y(56.2, 0, 20), Y(57.6, 0, 0),
    Y(58.0, yaw_to(H_LAND, B_UP), -4), Y(63.4, yaw_to(H_LAND, B_UP), -4),
    Y(63.6, 40, 0, 'out'), Y(64.6, 40, 8),
    Y(65.4, 0, 10), Y(75.6, 0, 6), Y(76.4, -165, 12),
]
# Looking away: the head turns aside and down from 27.8 and stays averted until the eyes are gone.
hero_limbs = {'head': {'rot': [V(0, (0, 0, 0)), V(27.8, (0, 0, 0)), V(28.3, (30, 55, 0), 'out'),
                               V(61.8, (30, 55, 0)), V(62.6, (0, 0, 0), 'in_out')]}}
hero_poses = [
    P(6.3, 'wand_raised', 6), P(7.2, 'relax', 10),
    P(36.8, 'wand_raised', 6), P(37.4, 'wand_point', 2), P(38.6, 'wand_raised', 4), P(39.2, 'wand_point', 2),
    P(40.2, 'relax', 10),
    P(55.45, 'cower', 2), P(56.3, 'kneel', 4), P(57.6, 'relax', 10),
    P(58.2, 'wand_raised', 5), P(58.8, 'wand_point', 2), P(59.4, 'wand_raised', 3), P(59.9, 'wand_point', 2),
    P(60.5, 'wand_raised', 3), P(61.0, 'wand_point', 2), P(61.8, 'relax', 10),
]
hero_actions = [
    A(6.6, 'cast', spell='lumos'),
    A(37.4, 'cast', spell='stupefy', target='actor:basilisk'),
    A(39.2, 'cast', spell='confringo', target='actor:basilisk'),
    A(55.45, 'hurt'),
    A(58.8, 'cast', spell='confringo', target='actor:basilisk'),
    A(59.9, 'cast', spell='bombarda', target='actor:basilisk'),
    A(61.0, 'cast', spell='stupefy', target='actor:basilisk'),
    A(64.5, 'sneak'),
]
hero = {'id': 'hero', 'type': 'player', 'skin': {'texture': 'student_gryffindor'}, 'items': {'mainhand': WAND},
        'path': hero_path, 'look': hero_look, 'limbs': hero_limbs, 'poses': hero_poses, 'actions': hero_actions}

# ---------------------------------------------------------------- the careless one (Slytherin)
C_STONE = (-2.0, 0, 6.0)
care_path = [
    K(0, (2.6, -1, -42.6)), K(6.4, (2.6, -1, -42.6)),
    K(8.9, (1.8, -1, -36.0), 'in_out'),
    K(12.0, (1.8, -1, -36.0)),
    K(12.9, (1.4, -1, -31.1), 'in'),
    K(13.1, (1.4, 0, -31.1), 'out'),
    K(13.4, (1.4, 0, -29.7), 'linear'),
    K(18.5, (-1.8, 0, -18.0), 'in_out'),             # stops under the serpent arches, gawking
    K(23.0, (-1.8, 0, -18.0)),
    K(27.0, (-2.4, 0, -6.0), 'in_out'),
    K(31.0, C_STONE, 'out'),                         # steps past the hero and looks up
]
care_look = [
    Y(0, 0, 2), Y(12.0, 0, 0), Y(18.5, -10, -10), Y(19.6, -30, -38), Y(21.2, 35, -32), Y(23.0, 0, -8),
    Y(24.0, 0, 26), Y(30.4, 0, 22), Y(31.0, yaw_to(C_STONE, B_EYE), 1, 'out'),
    Y(79.4, yaw_to(C_STONE, B_EYE), 1), Y(80.4, 22, 18), Y(84.0, 22, 6),
]
care_poses = [P(31.6, 'gape', 14), P(79.3, 'relax', 16), P(80.6, 'look_down', 12), P(83.4, 'relax', 16)]
care_actions = [A(33.5, 'stone'), A(79.2, 'unstone'), A(81.6, 'sneak')]
careless = {'id': 'careless', 'type': 'player', 'skin': {'texture': 'student_slytherin'},
            'items': {'mainhand': 'hogwartsmagic:dragon_core_wand'},
            'path': care_path, 'look': care_look, 'poses': care_poses, 'actions': care_actions}

# ---------------------------------------------------------------- the friend (Regulus skin), with the draught
F_DOOR = (-1.6, 0, -26.6)
F_END = (-2.5, 0, 7.6)
friend_path = [
    K(0, (-2.8, -1, -40.2)), K(6.6, (-2.8, -1, -40.2)),
    K(9.0, (-2.0, -1, -35.4), 'in_out'),
    K(13.0, (-2.0, -1, -35.4)),
    K(13.9, (-1.4, -1, -31.1), 'in'),
    K(14.1, (-1.4, 0, -31.1), 'out'),
    K(14.4, (-1.4, 0, -29.7), 'linear'),
    K(15.4, F_DOOR, 'out'),                          # stays at the door, watching
    K(61.8, F_DOOR),
    K(67.6, (-2.9, 0, -14.0), 'linear'),             # creeps in once it is blind
    K(72.4, (-2.9, 0, -4.0), 'linear'),
    K(75.8, (-2.9, 0, 3.0), 'linear'),
    K(77.2, F_END, 'out'),
]
friend_look = [
    Y(0, 0, 0), Y(15.4, 0, -4), Y(24.0, 2, -2), Y(28.0, 6, 0), Y(44.0, 6, 0), Y(56.0, 10, 2), Y(61.8, 0, 6),
    Y(76.6, 0, 6), Y(77.4, yaw_to(F_END, C_STONE), 18),
    Y(81.0, -150, 10), Y(84.0, -150, 4),
]
friend_poses = [P(55.5, 'cower', 6), P(57.8, 'relax', 12), P(77.6, 'offer', 6), P(79.6, 'relax', 12)]
friend_actions = [A(61.6, 'sneak'), A(77.3, 'stand'), A(77.7, 'use_item', item=DRAUGHT),
                  A(80.4, 'hold', item='minecraft:glass_bottle'), A(80.9, 'sneak')]
friend = {'id': 'friend', 'type': 'player', 'skin': {'texture': 'regulus'}, 'items': {'mainhand': DRAUGHT},
          'path': friend_path, 'look': friend_look, 'poses': friend_poses, 'actions': friend_actions}

basilisk = {'id': 'basilisk', 'type': 'hogwartsmagic:basilisk', 'path': basi_path, 'look': basi_look,
            'limbs': basi_limbs, 'anims': basi_anims, 'actions': basi_actions}

poses = {
    'relax': {'head': [0, 0, 0]},
    'offer': {'right_arm': [-72, -14, 0], 'head': [16, 0, 0], 'body': [8, 0, 0]},
    'gape': {'right_arm': [-34, 0, 12], 'left_arm': [-22, 0, -14], 'head': [-8, 0, 0], 'body': [-4, 0, 0]},
}

# ---------------------------------------------------------------- events
LOCK = (25, 10, 20)
def door_plane(p):
    return (p[0] - LOCK[0], p[1] - LOCK[1])
leaf = sorted(p for p, n in raw.items() if n.startswith('hogwartsmagic:chamber_door_leaf'))
bolts = sorted(p for p, n in raw.items() if n.startswith('minecraft:lightning_rod'))
def ang(p):
    u, v = door_plane(p)
    return math.atan2(v, u)
def rad(p):
    u, v = door_plane(p)
    return math.hypot(u, v)
maxr = max(1.0, max(rad(p) for p in leaf))
leaf.sort(key=lambda p: ang(p) + 2 * math.pi * 1.6 * (1 - rad(p) / maxr))
bolts.sort(key=ang)
DOOR_C = L2S(25, 10, 21)
RIM_T0, RIM_T1 = 7.8, 9.6                     # the snake of light runs the rim (the real one takes 2.8 s)
snd(7.6, 'hogwartsmagic:basilisk.hiss_threat', at=DOOR_C, volume=1.6, pitch=0.7)
t = RIM_T0
bi = 0
while t <= RIM_T1 + 1e-6:
    a = -math.pi + (t - RIM_T0) / (RIM_T1 - RIM_T0) * 2 * math.pi
    hx = LOCK[0] + maxr * math.cos(a)
    hy = LOCK[1] + maxr * math.sin(a)
    part(t, 'minecraft:sculk_soul', L2S(hx, hy, 19), 3, (0.1, 0.1, 0.1))
    part(t, 'minecraft:enchant', L2S(hx, hy, 19), 5, (0.15, 0.15, 0.15))
    while bi < len(bolts) and ang(bolts[bi]) <= a:
        b = bolts[bi]; bi += 1
        block(t, 'minecraft:air', L2S(*b))
        stud = (b[0], b[1], b[2] + 1)                # the blackstone housing in the leaf behind each bolt
        assert raw.get(stud, '').startswith('minecraft:polished_blackstone'), (stud, raw.get(stud))
        block(t, 'minecraft:air', L2S(*stud))
        part(t, 'minecraft:crit', L2S(*b), 14, (0.25, 0.25, 0.25))
        snd(t, 'minecraft:block.iron_trapdoor.open', at=L2S(*b), volume=1.0, pitch=0.55)
        snd(t, 'hogwartsmagic:basilisk.hiss_threat', at=L2S(*b), volume=0.5, pitch=1.7)
    if round((t - RIM_T0) * 20) % 9 == 0:
        snd(t, 'minecraft:entity.sniffer.happy', at=DOOR_C, volume=0.35, pitch=2.0)
    t = round(t + 0.1, 2)
assert bi == len(bolts), 'a bolt was left in the door'
LEAF_T0 = 9.7
snd(LEAF_T0, 'minecraft:block.ancient_debris.break', at=DOOR_C, volume=1.6, pitch=0.4)
PER_TICK = 6
for i, p in enumerate(leaf):
    tk = LEAF_T0 + (i // PER_TICK) * 0.05
    block(tk, 'minecraft:air', L2S(*p))
    if i % PER_TICK == 0 and (i // PER_TICK) % 3 == 0:
        q = L2S(*p)
        part(tk, 'minecraft:sculk_soul', (q[0] + 0.5, q[1] + 0.5, q[2] - 1), 3, (0.2, 0.2, 0.2))
    if i % 12 == 0:
        snd(tk, 'minecraft:block.stone.break', at=DOOR_C, volume=1.1, pitch=0.5)
LOCK_T = round(LEAF_T0 + (len(leaf) // PER_TICK + 1) * 0.05 + 0.1, 2)
block(LOCK_T, 'minecraft:air', L2S(*LOCK))
part(LOCK_T, 'minecraft:sculk_soul', L2S(25.5, 10.5, 20.5), 40, (0.6, 0.6, 0.6))
snd(LOCK_T, 'minecraft:block.piston.extend', at=DOOR_C, volume=1.5, pitch=0.45)
snd(LOCK_T, 'hogwartsmagic:basilisk.hiss_threat', at=DOOR_C, volume=1.4, pitch=0.85)

# ambience
snd(0.0, 'minecraft:ambient.cave', volume=0.6)
for tt in (2.0, 13.0):
    part(tt, 'minecraft:falling_dripstone_water', (0, 14, 0 if tt < 5 else -12), 6, (8, 1, 14))
snd(14.4, 'minecraft:block.pointed_dripstone.drip_water', at=(0, 1, -16), volume=0.8)

# the Basilisk wakes
snd(17.0, 'hogwartsmagic:basilisk.ambient', at=(0, 7, 46), volume=1.4, pitch=0.8)
snd(19.2, 'hogwartsmagic:basilisk.ambient2', at=(0, 7, 45), volume=1.4)
snd(21.6, 'hogwartsmagic:basilisk.hiss_threat', at=(0, 7, 42), volume=1.2, pitch=0.9)
part(22.6, 'minecraft:smoke', (0, 6, 41), 20, (1.2, 0.6, 1.0))
snd(24.2, 'minecraft:block.stone.step', at=(0, 1, 34), volume=1.0, pitch=0.5)

# the gaze: grey edges from inside the hero's eyes, then the careless one's stare runs its full 2.5 s
ev(25.8, 'effect', 'grey_edges', duration=64)       # closes in for 2 s, drains from 27.8 as he looks away
for tt, v in ((26.0, 0.5), (26.8, 0.7), (27.5, 0.9)):
    snd(tt, 'hogwartsmagic:basilisk.gaze_heartbeat', volume=v)
for tt, v, pt in ((31.4, 0.6, 1.0), (32.2, 0.8, 1.05), (32.9, 1.0, 1.1)):
    snd(tt, 'hogwartsmagic:basilisk.gaze_heartbeat', volume=v, pitch=pt)
part(33.5, 'minecraft:ash', (C_STONE[0], 1.6, C_STONE[2]), 40, (0.4, 0.8, 0.4))   # BasiliskEntity.petrify
part(33.5, 'minecraft:cloud', (C_STONE[0], 0.9, C_STONE[2]), 24, (0.3, 0.6, 0.3))
snd(33.5, 'minecraft:block.stone.place', at=(C_STONE[0], 1, C_STONE[2]), volume=1.0, pitch=0.6)
snd(33.6, 'minecraft:block.deepslate.place', at=(C_STONE[0], 1, C_STONE[2]), volume=0.8, pitch=0.5)

# every cast lands on the Basilisk after the beam's flight time (1.55 blocks a tick): a hurt flash and sparks
def lerp_path(keys, t):
    ks = [(float(k['t'][:-1]), k['pos'], k.get('ease', 'in_out')) for k in keys]
    if t <= ks[0][0]: return ks[0][1]
    for (t0, a, _), (t1, b, e) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            f = f if e == 'linear' else (f * f * f if e == 'in' else (1 - (1 - f) ** 3 if e == 'out' else
                 (4 * f * f * f if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2)))
            return [a[i] + (b[i] - a[i]) * f for i in range(3)]
    return ks[-1][1]
BASI_HITS = []
for c in hero_actions:
    if c['do'] != 'cast' or c.get('target') != 'actor:basilisk':
        continue
    tc = float(c['t'][:-1])
    hp = lerp_path(hero_path, tc)
    bp = lerp_path(basi_path, tc)
    d = math.dist((hp[0], hp[1] + 1.4, hp[2]), (bp[0], bp[1] + 1.5, bp[2]))
    th = round(tc + math.ceil(d / 1.55) / 20 + 0.05, 2)
    bh = lerp_path(basi_path, th)
    BASI_HITS.append(th)
    basi_actions.append(A(th, 'hurt'))
    part(th, 'minecraft:crit', (bh[0], 1.6, bh[2] - 1.5), 14, (0.5, 0.4, 0.5))
    snd(th, 'hogwartsmagic:basilisk.hurt', at=bh, volume=0.9, pitch=1.1)

# rear and strike
snd(41.0, 'hogwartsmagic:basilisk.hiss_threat', at=(0, 2, 13), volume=1.5)
snd(43.25, 'hogwartsmagic:basilisk.strike', at=(0, 2, 9), volume=1.6)
part(43.25, 'minecraft:sweep_attack', (0.6, 1.2, 6.6), 1, (0, 0, 0))

# the spit: an arcing glob of venom from the head to where the hero stood, a pool where it lands
snd(47.5, 'hogwartsmagic:basilisk.spit', at=(0, 2, 10), volume=1.3)
src = (0.4, 1.6, 9.9)
dst = (H_FAR[0], 0.2, H_FAR[2])
N = 7
for i in range(N + 1):
    f = i / N
    x = src[0] + (dst[0] - src[0]) * f
    z = src[2] + (dst[2] - src[2]) * f
    y = src[1] + (dst[1] - src[1]) * f + 2.4 * 4 * f * (1 - f)
    part(47.5 + f * 0.85, 'minecraft:item_slime', (x, y, z), 10, (0.15, 0.15, 0.15))
LAND = 48.35
part(LAND, 'minecraft:item_slime', (dst[0], 0.1, dst[2]), 40, (0.6, 0.15, 0.6))
part(LAND, 'minecraft:squid_ink', (dst[0], 0.2, dst[2]), 8, (0.4, 0.1, 0.4))
snd(LAND, 'minecraft:entity.slime.squish', at=(dst[0], 0, dst[2]), volume=1.0, pitch=0.6)
for tt in (48.9, 49.5, 50.1, 50.7, 51.3):
    part(tt, 'minecraft:item_slime', (dst[0], 0.1, dst[2]), 6, (0.6, 0.05, 0.6))

# burrow: down through the floor, a line of dust racing under the causeway, then the eruption
snd(51.65, 'hogwartsmagic:basilisk.burrow', at=(0, 0, 11), volume=1.2, pitch=0.9)
part(51.7, 'minecraft:poof', (B_BACK[0], 0.2, B_BACK[2] - 1), 40, (1.0, 0.2, 1.4))
part(52.2, 'minecraft:large_smoke', (B_BACK[0], 0.3, B_BACK[2]), 12, (0.8, 0.2, 1.0))
snd(52.3, 'minecraft:block.gravel.break', at=(0, 0, 11), volume=1.0, pitch=0.6)
for i in range(6):
    f = (i + 1) / 6
    tt = 52.9 + f * 2.1
    x = B_BACK[0] + (B_UP[0] - B_BACK[0]) * f
    z = B_BACK[2] + (B_UP[2] - B_BACK[2]) * f
    part(tt, 'minecraft:poof', (x, 0.1, z), 6, (0.6, 0.05, 0.6))
    if i % 2 == 0:
        snd(tt, 'minecraft:block.gravel.break', at=(x, 0, z), volume=0.6, pitch=0.5)
snd(55.4, 'hogwartsmagic:basilisk.erupt', at=B_UP, volume=1.7)
part(55.4, 'minecraft:explosion', (B_UP[0], 0.6, B_UP[2]), 3, (0.8, 0.4, 0.8))
part(55.4, 'minecraft:poof', (B_UP[0], 0.4, B_UP[2]), 60, (1.6, 0.6, 1.6))
part(55.5, 'minecraft:large_smoke', (B_UP[0], 0.6, B_UP[2]), 20, (1.2, 0.8, 1.2))
snd(55.5, 'minecraft:entity.player.hurt', at=H_STEP, volume=1.0)

# blows to the head, then blind (BasiliskEntity.setBlinded: hurt sound at 1.8 / 0.75, 30 damage indicators)
assert BASI_HITS[-1] < 61.3, BASI_HITS
snd(61.3, 'hogwartsmagic:basilisk.hurt', at=B_UP, volume=1.8, pitch=0.75)
part(61.3, 'minecraft:damage_indicator', (B_UP[0], 1.8, B_UP[2] - 1.3), 30, (0.6, 0.4, 0.6))

# heard: two ordinary steps, a hiss and a snap of the head; then nothing but creeping
snd(63.6, 'minecraft:block.stone.step', at=(1.8, 0, -11.5), volume=1.0)
snd(63.9, 'minecraft:block.stone.step', at=(0.6, 0, -11.3), volume=1.0)
snd(64.15, 'hogwartsmagic:basilisk.hiss_threat', at=B_UP, volume=1.3, pitch=1.1)
snd(70.5, 'hogwartsmagic:basilisk.ambient', at=B_UP, volume=0.9, pitch=0.9)
snd(80.0, 'hogwartsmagic:basilisk.ambient2', at=B_UP, volume=0.8, pitch=0.85)

# the Mandrake Restorative Draught (Petrification.cure: stoneBurst, stone break at 1.0 / 0.8)
part(78.4, 'minecraft:glow', (C_STONE[0], 1.2, C_STONE[2]), 10, (0.3, 0.5, 0.3))
snd(78.9, 'minecraft:item.bottle.empty', at=(F_END[0], 1, F_END[2]), volume=0.8)
snd(79.2, 'minecraft:block.stone.break', at=(C_STONE[0], 1, C_STONE[2]), volume=1.0, pitch=0.8)
part(79.2, 'minecraft:poof', (C_STONE[0], 1.0, C_STONE[2]), 30, (0.3, 0.5, 0.3))
part(79.25, 'minecraft:cloud', (C_STONE[0], 1.0, C_STONE[2]), 10, (0.3, 0.6, 0.3))
part(79.4, 'minecraft:happy_villager', (C_STONE[0], 1.4, C_STONE[2]), 8, (0.4, 0.5, 0.4))

# ---------------------------------------------------------------- words (the Pensieve's voice)
say(1.0, 'Beneath the school lies the Chamber of Secrets.', 4.5)
say(7.0, 'Its door opens only to Parseltongue.', 4.0)
say(12.4, 'Three came looking for the monster.', 3.6)
say(17.4, 'The Basilisk wakes.', 3.2)
say(21.6, 'Its stare turns the living to stone.', 3.6)
say(25.6, 'But the stare is slow. The grey closes in.', 2.8)
say(28.6, 'Look away, and it drains from your sight.', 3.2)
say(33.8, 'Hold its eyes too long, and you are stone.', 3.2)
say(37.2, 'Fight it from the edge of your sight.', 3.6)
say(41.2, 'It rears before it strikes. Get back.', 3.4)
say(47.0, 'At range, it spits venom. Keep moving.', 3.6)
say(51.6, 'Too far, and it goes under...', 3.4)
say(55.6, '...and comes up beneath you.', 2.8)
say(58.6, 'Strike the head, from the front.', 2.8)
say(61.6, 'Enough blows put its eyes out for good.', 2.8)
say(64.6, 'Blind, it hunts by sound.', 3.0)
say(68.0, 'Creep, and it cannot hear you.', 3.4)
say(77.6, 'A Mandrake Restorative Draught...', 2.6)
say(80.4, '...brings the stone back to life.', 3.2)
say(84.4, 'Look away. Strike the head. Move softly.', 5.0)

# ---------------------------------------------------------------- camera
H, C, F, B = 'actor:hero', 'actor:careless', 'actor:friend', 'actor:basilisk'
# S1 establishing: high over the causeway by the door, drifting toward the statue at the far end
shot(0.0, (0.5, 13.0, -26.0), (0, 9.0, 44), 86, cut=False)
shot(6.0, (0.5, 11.5, -19.0), (0, 7.5, 44), 80, cut=False, ease='in_out')
# S2 the antechamber: behind the three, the round door unwinds in a spiral
shot(6.0, (-1.6, 1.4, -47.0), (0.5, 2.6, -29.5), 62)
shot(11.5, (-1.2, 1.3, -44.6), (0.5, 2.6, -29.5), 56, cut=False)
# S3 inside the hall looking back: they step through the opened door
shot(11.5, (3.0, 1.6, -20.5), H, 60)
shot(16.0, (2.4, 1.7, -17.0), H, 58, cut=False)
# S4 the statue: a slow push toward the black mouth as two eyes light in it
shot(16.0, (0.6, 2.4, 14.0), (0, 7.6, 44.5), 56)
shot(21.5, (0.4, 3.2, 20.0), (0, 7.4, 45.0), 42, cut=False)
# S5 from over the right-hand pool: it pours out of the mouth and down the steps
shot(21.5, (11.5, 2.4, 31.0), (0, 4.0, 39.0), 66)
shot(25.5, (10.5, 1.8, 28.5), (0, 1.4, 29.0), 60, cut=False)
# S6 THE GAZE: behind the hero's shoulder, looking where he looks; the grey closes in, then he looks away
shot(25.5, (0.0, 2.15, 0.6), (0, 1.5, 25.0), 58)
shot(30.0, (0.2, 2.05, 1.4), (0, 1.4, 25.0), 50, cut=False)
# S7 the careless one: face on as he raises his eyes to it, and stays there
shot(30.0, (-2.5, 1.75, 11.4), C, 46)
shot(35.5, (-2.3, 1.7, 10.8), C, 44, cut=False)
# S8 side on from the left pool: he fights with his eyes turned aside, and it comes on
shot(35.5, (-6.2, 1.5, -0.8), (0.6, 1.4, 6.0), 72)
shot(40.8, (-6.0, 1.4, 0.4), (0.6, 1.4, 7.0), 68, cut=False)
# S9a it rears (right pool, low)
shot(40.8, (9.6, 1.4, 13.0), (0.3, 2.0, 10.0), 60)
shot(42.95, (9.3, 1.3, 12.6), (0.3, 2.2, 10.0), 58, cut=False)
# S9b the strike lands on air: behind him as he backs off, the head lancing in
shot(42.95, (2.6, 2.4, -8.0), (0.6, 1.4, 8.0), 60)
shot(46.5, (2.4, 2.2, -9.4), (0.8, 1.2, 6.0), 58, cut=False)
# S10 over the serpent's head: the venom arcs away down the causeway
shot(46.5, (1.8, 3.9, 15.4), H, 52)
shot(51.3, (1.4, 3.5, 14.4), H, 54, cut=False)
# S11a high over the left pool: it goes under, a line of dust races at him
shot(51.3, (-9.0, 7.5, 3.5), (0.6, 0, 1.0), 74)
shot(55.25, (-8.6, 7.0, 1.0), (1.4, 0, -4.0), 70, cut=False)
# S11b low beside him: it erupts beneath his feet
shot(55.25, (-1.6, 0.9, -14.8), (2.0, 1.6, -7.0), 72, roll=6)
shot(58.4, (-1.3, 1.0, -14.2), (2.4, 1.2, -9.0), 68, cut=False, roll=0)
# S12a over his shoulder: blows to the head, from the front
shot(58.4, (4.0, 2.3, -14.0), (2.0, 1.6, -7.6), 54)
shot(61.2, (3.8, 2.2, -13.6), (2.0, 1.6, -7.6), 52, cut=False)
# S12b the head, close: the eyes go out, it thrashes; a footstep, and it snaps toward the sound
shot(61.2, (-1.2, 2.3, -10.6), (2.0, 1.4, -7.4), 50)
shot(65.0, (-1.0, 2.1, -10.2), (1.8, 1.3, -7.6), 48, cut=False)
# S13a low from the left pool: he creeps past its blind head
shot(65.0, (-9.0, 1.6, -1.8), (-1.6, 0.8, -8.4), 62)
shot(70.0, (-8.6, 1.4, -1.6), (-2.2, 0.8, -3.0), 60, cut=False)
# S13b high behind them both along the causeway, the friend creeping up behind
shot(70.0, (-0.8, 4.4, -9.0), H, 64)
shot(75.0, (-1.0, 4.0, -1.6), H, 60, cut=False)
# S14a the friend reaches the statue that was a boy
shot(75.0, (0.4, 2.4, 1.4), (-2.3, 1.1, 6.6), 52)
shot(79.0, (0.3, 2.3, 2.0), (-2.2, 1.2, 6.4), 50, cut=False)
# S14b face on: the stone falls away
shot(79.0, (0.2, 1.75, 8.6), (-2.0, 1.55, 6.0), 46)
shot(84.0, (0.1, 1.7, 8.3), (-2.0, 1.4, 6.2), 44, cut=False)
# S15 the push-in: the three together, the blind serpent searching beyond them
shot(84.0, (-0.6, 2.6, 12.8), (-2.3, 1.0, 5.6), 62)
shot(90.0, (-1.3, 2.1, 10.8), (-2.3, 1.0, 6.2), 44, cut=False, ease='in_out')

scene = {
    'title': 'The Memory of the Chamber',
    'length': T(LENGTH),
    'radius': R,
    'set': {
        'templates': [{'id': 'hogwartsmagic:chamber_hall', 'at': [OX, OY, OZ]}],
        'fills': fills,
        'time_of_day': 18000,
        'build_ticks': 60,
    },
    'poses': poses,
    'actors': [hero, careless, friend, basilisk],
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

def sample(keys, t, field='pos'):
    ks = [(tval(k['t']), k[field], k.get('ease', 'in_out')) for k in keys]
    if t <= ks[0][0]: return ks[0][1]
    for (t0, a, _), (t1, b, e) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else ease_f((t - t0) / (t1 - t0), e)
            return [a[i] + (b[i] - a[i]) * f for i in range(3)]
    return ks[-1][1]

dynamic_open = set()
for e in events:
    if e['kind'] == 'block':
        dynamic_open.add((tuple(e['at']), tval(e['t'])))
opened_at = {c: tt for c, tt in dynamic_open}
def rock(p, t):
    c = (math.floor(p[0]), math.floor(p[1]), math.floor(p[2]))
    if c in opened_at and t >= opened_at[c]:
        return None
    b = world.get(c)
    if b is None or b in OPEN:
        return None
    return b
def solid_under(p, t):
    c = (math.floor(p[0]), math.floor(p[1] - 0.05), math.floor(p[2]))
    b = world.get(c)
    if c in opened_at and t >= opened_at[c]:
        return False
    return b is not None and b not in OPEN

actors = {'hero': hero_path, 'careless': care_path, 'friend': friend_path, 'basilisk': basi_path}
# the hop onto the threshold (the centre crosses the step edge a moment before the feet do), and the eruption
AIRBORNE = {'hero': [(11.9, 12.35), (55.4, 56.2)], 'careless': [(12.9, 13.35)], 'friend': [(13.9, 14.35)]}
for aid, path in actors.items():
    for i in range(int(LENGTH * 20) + 1):
        tt = i / 20
        p = sample(path, tt)
        if aid == 'basilisk':
            # the head (2.6 blocks ahead, -z) must not sit in rock while above ground
            if p[1] > -0.5 and tt > 21.0:
                for d in (0.5, 1.5, 2.4):
                    b = rock((p[0], p[1] + 0.6, p[2] - d), tt)
                    if b:
                        problems.append('basilisk head in %s at t=%.2f pos=%s' % (b, tt, [round(v, 2) for v in p]))
                        break
            continue
        for dy in (0.05, 0.9, 1.7):
            b = rock((p[0], p[1] + dy, p[2]), tt)
            if b:
                problems.append('%s in %s at t=%.2f pos=%s+%.1f' % (aid, b, tt, [round(v, 2) for v in p], dy))
                break
        if not any(a <= tt <= b2 for a, b2 in AIRBORNE.get(aid, [])):
            if abs(p[1] - round(p[1])) < 1e-6 and not solid_under(p, tt):
                problems.append('%s floating at t=%.2f pos=%s' % (aid, tt, [round(v, 2) for v in p]))
# the door must be open before anyone walks through it
for aid, path in actors.items():
    if aid == 'basilisk': continue
    for i in range(int(LENGTH * 20) + 1):
        tt = i / 20
        p = sample(path, tt)
        if -30.6 < p[2] < -27.4 and tt < LOCK_T:
            problems.append('%s in the doorway at %.2f before it opens (%.2f)' % (aid, tt, LOCK_T))
            break

EYE = {'hero': 1.62, 'careless': 1.62, 'friend': 1.62, 'basilisk': 2.55}
def target(look, t):
    if isinstance(look, str):
        aid = look.split(':')[1]
        p = sample(actors[aid], t)
        return [p[0], p[1] + EYE[aid], p[2]]
    return look
def los(pos, tgt, t, label):
    n = int(math.dist(pos, tgt) * 10)
    for m in range(1, n):
        q = [pos[c] + (tgt[c] - pos[c]) * m / n for c in range(3)]
        if math.dist(q, tgt) < 0.6:
            break
        bb = rock(q, t)
        if bb:
            problems.append('%s: view blocked by %s at %s' % (label, bb, [round(v, 1) for v in q]))
            return
for i, k in enumerate(cam):
    t = tval(k['t'])
    if rock(k['pos'], t):
        problems.append('camera %d at %s inside %s' % (i, k['t'], rock(k['pos'], t)))
    los(k['pos'], target(k['look_at'], t), t, 'camera %d at %s' % (i, k['t']))
for a, b2 in zip(cam, cam[1:]):
    if b2.get('cut'):
        continue
    t0, t1 = tval(a['t']), tval(b2['t'])
    for j in range(1, 10):
        f = j / 10
        t = t0 + (t1 - t0) * f
        pos = [a['pos'][c] + (b2['pos'][c] - a['pos'][c]) * f for c in range(3)]
        if rock(pos, t):
            problems.append('camera between %s and %s inside %s' % (a['t'], b2['t'], rock(pos, t)))
        ta, tb = target(a['look_at'], t), target(b2['look_at'], t)
        los(pos, [ta[c] + (tb[c] - ta[c]) * f for c in range(3)], t, 'camera %s..%s t=%.1f' % (a['t'], b2['t'], t))
# camera not inside the basilisk's body (straight back 14 blocks from the head, ~1.5 tall, ~1.4 wide)
for a, b2 in zip(cam, cam[1:] + [None]):
    t0 = tval(a['t'])
    t1 = tval(b2['t']) if b2 else LENGTH
    for j in range(0, 10):
        t = t0 + (t1 - t0) * j / 10
        if b2 and not b2.get('cut'):
            f = j / 10
            pos = [a['pos'][c] + (b2['pos'][c] - a['pos'][c]) * f for c in range(3)]
        else:
            pos = a['pos']
        bp = sample(basi_path, t)
        if abs(pos[0] - bp[0]) < 1.4 and bp[2] - 3.0 < pos[2] < bp[2] + 14.5 and bp[1] - 0.2 < pos[1] < bp[1] + 2.0:
            problems.append('camera inside the basilisk at t=%.1f pos=%s' % (t, [round(v, 1) for v in pos]))
# extents
for f in fills:
    for v in (f['from'], f['to']):
        if abs(v[0]) > R or abs(v[2]) > R:
            problems.append('fill beyond radius %s' % f)
if OX < -R or OX + 50 > R or OZ < -R or OZ + 100 > R:
    problems.append('template beyond radius')
# shots, words
cuts = [0.0] + [tval(k['t']) for k in cam if k.get('cut')] + [LENGTH]
lens = [round(b2 - a, 2) for a, b2 in zip(cuts, cuts[1:])]
print('shots', len(lens), lens)
for l in lens:
    if l < 2 or l > 6.5:
        problems.append('shot length %s' % l)
wt = [(tval(w['t']), tval(w.get('duration', 80))) for w in words]
for (a, d), (b2, _) in zip(wt, wt[1:]):
    if a + d > b2 + 1e-6:
        problems.append('narration at %.1f overlaps the next at %.1f' % (a, b2))
    if b2 - a < 2:
        problems.append('narration closer than 2 s at %.1f' % a)
# numbers are clean
def walk(o):
    if isinstance(o, dict):
        for v in o.values(): walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)
    elif isinstance(o, float):
        if abs(o * 100 - round(o * 100)) > 1e-6:
            problems.append('float noise %r' % o)
walk(scene)
print('basilisk hits', BASI_HITS)
print('events', len(events), 'door leaf', len(leaf), 'bolts', len(bolts), 'lock at', LOCK_T)
print('\n'.join(problems) if problems else 'NO PROBLEMS')

txt = write_scene(scene, 'chamber')
print('chars', len(txt))
