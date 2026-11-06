"""Generates data/hogwartsmagic/memories/dark_lord.json: "The Dark Lord", a 45 s cinematic overview of Voldemort.

Usage (from the repo root): python build_scripts/pensieve_scenes/dark_lord.py

A show reel for his own model (VoldemortModel) and its clips (VoldemortAnimations): he is the real
`hogwartsmagic:voldemort` entity as an actor, not a player skin, so what plays here is what you meet in the world.
A graveyard at midnight in the rain. He streaks down out of the storm as black smoke and re-forms; the camera
circles him and holds on his face; three Death Eaters fly in as smoke and kneel; he raises the Dark Mark; an Auror
apparates in and stuns at him, he shields, then winds up the Killing Curse and kills him; he looses Fiendfyre into
the graves; he turns to the camera and leaves as smoke under the Mark.

Shot list (start, length, framing, move):
  S1   0.0  5.4  wide high over the graves, fov 80        drifts in, panning down with the smoke comet
  S2   5.4  3.6  low angle at his feet, fov 60            slow push; he re-forms at 6.0 under a strike (ENTRANCE)
  S3   9.0  5.0  chest-height orbit, r 3.2, fov 60        spline arc across his front; ARMS_SPREAD at 9.2
  S4  14.0  3.0  close on the face, fov 38, roll 4        creeping push; LAUGH at 14.8
  S5  17.0  4.0  over his shoulder, fov 75                drift; Death Eaters land 18.0 to 19.0 and kneel
  S6  21.0  4.0  low front-left, fov 70 -> 85             CONJURE, Morsmordre at 23, tilt up to the Mark
  S7  25.0  2.4  behind the Auror, fov 65                 he appears 25.2, Stupefy 26.4, Protego 26.3
  S8  27.4  2.7  low on Voldemort, fov 55                 push; AVADA wind-up from 27.6
  S9  30.1  2.5  side profile, wide, fov 70               the curse at 30.2, the Auror falls at 30.7
  S10 32.6  4.4  wide from behind-left, fov 80            he turns, CAST, Fiendfyre at 33.6, serpent, fires
  S11 37.0  8.0  front, fov 55 -> 42, then 60             push in to his face, he leaves as smoke at 41.5,
                                                          pan up after him; title at 42.4; fade at 45
"""
import math, os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import write_scene, physics_problems

R = 32
LENGTH = 45.0
rng = random.Random(1945)


def r2(v):
    return round(v + 0.0, 2)


def T(s):
    return '%gs' % round(s, 2)


def yaw_to(frm, to):
    return math.degrees(math.atan2(-(to[0] - frm[0]), to[2] - frm[2]))


# ------------------------------------------------------------------ the set
fills, world = [], {}


def fill(block, a, b):
    fills.append({'block': block, 'from': list(a), 'to': list(b)})
    for x in range(min(a[0], b[0]), max(a[0], b[0]) + 1):
        for y in range(min(a[1], b[1]), max(a[1], b[1]) + 1):
            for z in range(min(a[2], b[2]), max(a[2], b[2]) + 1):
                world[(x, y, z)] = block


def put(block, p):
    fill(block, p, p)


fill('minecraft:dirt', (-30, -5, -30), (30, -2, 30))
fill('minecraft:grass_block', (-30, -1, -30), (30, -1, 30))
disks = []
for cx, cz, r, b in [(-9, 6, 4, 'minecraft:podzol'), (10, -7, 5, 'minecraft:coarse_dirt'), (6, 12, 3, 'minecraft:podzol'),
                     (-14, -12, 4, 'minecraft:coarse_dirt'), (16, 9, 3, 'minecraft:podzol')]:
    disks.append({'block': b, 'center': [cx, -1, cz], 'radius': r})
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r:
                world[(x, -1, z)] = b
# the worn stone circle he lands on
disks.append({'block': 'minecraft:mossy_cobblestone', 'center': [0, -1, 0], 'radius': 3})
disks.append({'block': 'minecraft:cobblestone', 'center': [0, -1, 0], 'radius': 2})
for x in range(-3, 4):
    for z in range(-3, 4):
        if x * x + z * z <= 9:
            world[(x, -1, z)] = 'minecraft:cobblestone' if x * x + z * z <= 4 else 'minecraft:mossy_cobblestone'

# the obelisk behind him
fill('minecraft:stone_bricks', (-1, 0, -10), (1, 0, -8))
fill('minecraft:polished_andesite', (0, 1, -9), (0, 5, -9))
put('minecraft:chiseled_stone_bricks', (0, 6, -9))
put('minecraft:soul_lantern', (1, 1, -8))
put('minecraft:soul_lantern', (-1, 1, -8))

# keep-clear zones: the stone circle, the Auror's line and every camera
CLEAR = [((0, 0), 5.5)]


def clear_of(x, z):
    if any((x - cx) ** 2 + (z - cz) ** 2 < r * r for (cx, cz), r in CLEAR):
        return False
    if abs(x) <= 2 and -1 <= z <= 16:
        return False                            # the Auror stands at z 12 and the curse flies along x 0
    return True


graves = []
for gx in list(range(-17, -5, 3)) + list(range(7, 19, 3)):
    for gz in range(-15, 17, 4):
        x, z = gx + rng.choice((0, 0, 1)), gz + rng.choice((0, 1))
        if not clear_of(x, z) or (x, z) in [(0, -9)] or rng.random() < 0.18:
            continue
        graves.append((x, z))
for x, z in graves:
    put(rng.choice(['minecraft:stone_bricks', 'minecraft:mossy_stone_bricks', 'minecraft:cracked_stone_bricks']), (x, 0, z))
    put(rng.choice(['minecraft:stone_brick_slab', 'minecraft:mossy_stone_brick_slab', 'minecraft:andesite_slab']), (x, 1, z))
    if rng.random() < 0.16:
        put('minecraft:soul_lantern', (x, 2, z))

# dead trees
for (tx, tz, h) in [(-13, -6, 6), (12, -12, 7), (-15, 12, 5), (16, 3, 6), (-19, 2, 5)]:
    fill('minecraft:dark_oak_log', (tx, 0, tz), (tx, h, tz))
    put('minecraft:dark_oak_log', (tx + 1, h - 1, tz))
    put('minecraft:dark_oak_log', (tx + 2, h, tz))
    put('minecraft:dark_oak_log', (tx - 1, h - 2, tz + 1))
    put('minecraft:dark_oak_log', (tx - 1, h - 1, tz + 2))

# the railings round the yard
for a, b in [((-21, 0, -21), (21, 0, -21)), ((-21, 0, 21), (21, 0, 21)), ((-21, 0, -21), (-21, 0, 21)), ((21, 0, -21), (21, 0, 21))]:
    fill('minecraft:iron_bars', a, b)
fill('minecraft:air', (-1, 0, 21), (1, 0, 21))          # the gate stands open

# undergrowth (never on the stone circle or under a camera)
for _ in range(90):
    x, z = rng.randint(-20, 20), rng.randint(-20, 20)
    if (x, 0, z) in world or x * x + z * z < 30 or (abs(x) <= 2 and z > 0):
        continue
    put(rng.choice(['minecraft:short_grass', 'minecraft:short_grass', 'minecraft:fern', 'minecraft:dead_bush']), (x, 0, z))

# hidden light: soft fill over the circle so his face reads at midnight
for p in [(3, 5, 3), (-3, 5, 3), (0, 6, -4), (0, 4, 6)]:
    put('minecraft:light', p)

# ------------------------------------------------------------------ actors
V = [0.0, 0.0, 0.0]
WAND = 'hogwartsmagic:elder_wand'
actors, events, words, cam = [], [], [], []


def K(t, v, ease=None):
    k = {'t': T(t), 'pos': [r2(c) for c in v]}
    if ease:
        k['ease'] = ease
    return k


def Y(t, yaw, pitch=0, ease=None):
    k = {'t': T(t), 'yaw': r2(yaw), 'pitch': r2(pitch)}
    if ease:
        k['ease'] = ease
    return k


def A(t, name, blend=None):
    k = {'t': T(t), 'name': name}
    if blend is not None:
        k['blend'] = blend
    return k


def act(t, do, **kw):
    k = {'t': T(t), 'do': do}
    k.update(kw)
    return k


ARRIVE = 5.8
LEAVE = 41.5
AVADA_AT = 27.6
CURSE = AVADA_AT + 2.6          # VoldemortAnimations.AVADA slashes down at 2.6 s
FIRE_TARGET = [10.0, 1.0, 10.0]

voldemort = {
    'id': 'voldemort', 'type': 'hogwartsmagic:voldemort',
    'items': {'mainhand': WAND},
    'path': [K(0, [30, 22, -25]), K(2.0, [18, 12, -13], 'spline'), K(3.8, [7, 4, -4.5], 'spline'),
             K(5.2, [1.2, 0.8, -0.9], 'spline'), K(ARRIVE, V, 'in_out'),
             K(LEAVE, V), K(42.4, [0, 2.5, -2.5], 'spline'), K(43.6, [2, 12, -13], 'spline'),
             K(45.0, [6, 26, -30], 'spline')],
    'look': [Y(0, 0), Y(32.6, 0), Y(33.3, -45), Y(37.2, -45), Y(38.2, 0)],
    'anims': [A(0, 'IDLE'), A(6.0, 'ENTRANCE', 2), A(9.2, 'ARMS_SPREAD', 8), A(13.6, 'IDLE', 10), A(14.8, 'LAUGH', 4), A(17.2, 'IDLE', 6),
              A(21.0, 'CONJURE', 8), A(25.0, 'IDLE', 8), A(26.1, 'CAST', 2), A(26.9, 'POINT', 4),
              A(AVADA_AT, 'AVADA', 4), A(32.6, 'IDLE', 8), A(33.3, 'CAST', 2), A(34.2, 'POINT', 4),
              A(37.0, 'IDLE', 10), A(39.2, 'ARMS_SPREAD', 10), A(LEAVE - 0.6, 'IDLE', 8)],
    'actions': [act(0, 'smoke'), act(6.0, 'unsmoke'),
                act(23.0, 'cast', spell='morsmordre', target=[0, 7, -5]),
                act(26.3, 'cast', spell='protego'),
                act(CURSE, 'cast', spell='avada_kedavra', target='actor:auror'),
                act(33.6, 'cast', spell='fiendfyre', target=FIRE_TARGET),
                act(LEAVE, 'smoke')],
}
actors.append(voldemort)

# three Death Eaters fly in as smoke and kneel round him
KNEEL_AT = [([-4.5, 0, 2.0], [-28, 16, 14], 18.0), ([4.5, 0, 2.0], [28, 14, 16], 18.5), ([-3.2, 0, -3.6], [-24, 18, -26], 19.0)]
for i, (land, start, t_land) in enumerate(KNEEL_AT):
    t0 = t_land - 1.6
    mid = [(start[0] + land[0]) / 2, (start[1] + land[1]) / 2 + 2, (start[2] + land[2]) / 2]
    actors.append({
        'id': 'death_eater_%d' % i, 'type': 'player', 'skin': {'texture': 'death_eater'},
        'items': {'mainhand': 'hogwartsmagic:harry_potter_wand'},
        'path': [K(0, start), K(t0, start), K(t0 + 0.8, mid, 'spline'), K(t_land, land, 'in_out')],
        'look': [Y(0, yaw_to(land, V))],
        'poses': [{'t': T(t_land + 0.5), 'name': 'kneel', 'blend': 10}, {'t': T(29.2), 'name': 'look_down', 'blend': 8}],
        'actions': [act(0, 'vanish'), act(t0, 'appear'), act(t0 + 0.01, 'smoke'), act(t_land + 0.15, 'unsmoke')],
    })

# the Auror who stands against him
AUROR = [0.0, 0.0, 12.0]
actors.append({
    'id': 'auror', 'type': 'player', 'skin': {'texture': 'hooded_figure'},
    'items': {'mainhand': 'hogwartsmagic:harry_potter_wand'},
    'path': [K(0, [-28, 0, 28]), K(25.19, [-28, 0, 28]), K(25.2, AUROR)],
    'look': [Y(0, 180)],
    'poses': [{'t': T(25.6), 'name': 'wand_point', 'blend': 6}],
    'actions': [act(0, 'vanish'), act(25.2, 'appear'), act(26.4, 'cast', spell='stupefy', target='actor:voldemort'),
                act(30.6, 'hurt'), act(30.7, 'die')],
})

# the Fiendfyre serpent: hidden at the wand until the fire leaves it, then out over the graves
serp_path = [K(0, [0.4, 1.7, 0.6])]
t, p0 = 33.85, [0.4, 1.7, 0.6]
for i in range(16):
    s = i / 15.0
    x = p0[0] + (FIRE_TARGET[0] + 2 - p0[0]) * s + math.sin(s * math.pi * 2) * 1.6
    z = p0[2] + (FIRE_TARGET[2] + 2 - p0[2]) * s - math.sin(s * math.pi * 2) * 1.6
    y = p0[1] + math.sin(s * math.pi) * 2.2
    serp_path.append(K(t + i * 0.2, [x, y, z], 'linear'))
actors.append({
    'id': 'serpent', 'type': 'hogwartsmagic:fiendfyre_serpent', 'path': serp_path,
    'actions': [act(0, 'vanish'), act(33.85, 'appear'), act(37.0, 'vanish')],
})


# ------------------------------------------------------------------ events
def ev(t, kind, id_, **kw):
    e = {'t': T(t), 'kind': kind, 'id': id_}
    e.update(kw)
    events.append(e)


def fx(t, id_, at, **kw):
    ev(t, 'effect', id_, at=[r2(c) for c in at], **kw)


ev(0.3, 'music', 'hogwartsmagic:pensieve.music.summoning_voldemort', volume=0.9)
fx(0, 'rain', [0, 8, 0], spread=[30, 10, 30], intensity=0.8)
for t_, at in [(0.8, [-16, 0, -22]), (6.0, [-3, 0, -6]), (12.4, [17, 0, -18]), (19.4, [-18, 0, 10]),
               (29.4, [8, 0, -16]), (35.6, [-20, 0, -14]), (42.6, [4, 0, -24])]:
    fx(t_, 'lightning', at)
# the re-forming
ev(6.0, 'sfx', 'hogwartsmagic:voldemort.entrance', volume=1.0)
ev(6.6, 'voice', 'hogwartsmagic:voldemort.voice.entrance', volume=1.0)
ev(6.05, 'sound', 'minecraft:entity.wither.spawn', at=[0, 1, 0], volume=0.5, pitch=0.6)
ev(6.0, 'particles', 'minecraft:squid_ink', at=[0, 1.1, 0], count=90, spread=[0.5, 0.9, 0.5])
ev(6.0, 'particles', 'minecraft:large_smoke', at=[0, 0.6, 0], count=70, spread=[1.2, 0.3, 1.2])
ev(6.1, 'particles', 'minecraft:soul', at=[0, 0.2, 0], count=24, spread=[1.4, 0.1, 1.4])
ev(14.8, 'voice', 'hogwartsmagic:voldemort.voice.laugh', volume=1.0)
ev(CURSE - 0.15, 'voice', 'hogwartsmagic:voldemort.voice.avada', volume=1.0)
ev(33.2, 'sfx', 'hogwartsmagic:voldemort.curse_gather', volume=0.9)
ev(LEAVE, 'sfx', 'hogwartsmagic:voldemort.smoke_whoosh', volume=0.9)
for i, (land, _, t_land) in enumerate(KNEEL_AT):
    ev(t_land + 0.15, 'particles', 'minecraft:squid_ink', at=[land[0], 1.0, land[2]], count=40, spread=[0.4, 0.8, 0.4])
    ev(t_land + 0.15, 'sound', 'minecraft:entity.phantom.flap', at=[land[0], 1, land[2]], volume=0.8, pitch=0.6)
ev(25.2, 'sound', 'minecraft:entity.enderman.teleport', at=AUROR, volume=0.9, pitch=0.8)
ev(30.75, 'sound', 'minecraft:entity.player.hurt', at=AUROR, volume=0.8, pitch=0.8)
# Fiendfyre takes the graves on the right
for i, (x, z) in enumerate([(8, 8), (10, 9), (9, 11), (11, 11), (12, 9), (10, 13), (13, 12), (7, 10)]):
    if (x, 0, z) not in world:
        ev(34.2 + i * 0.15, 'block', 'minecraft:fire', at=[x, 0, z])
ev(34.2, 'sound', 'minecraft:entity.blaze.shoot', at=FIRE_TARGET, volume=1.0, pitch=0.7)
ev(34.6, 'particles', 'minecraft:lava', at=FIRE_TARGET, count=30, spread=[2, 0.5, 2])
# the Mark over the yard as he leaves
fx(38.0, 'dark_mark', [0, 8, -10], duration=140)
ev(LEAVE, 'sound', 'minecraft:entity.phantom.flap', at=[0, 1, 0], volume=1.0, pitch=0.5)
ev(LEAVE, 'particles', 'minecraft:squid_ink', at=[0, 1.1, 0], count=80, spread=[0.5, 0.9, 0.5])


# ------------------------------------------------------------------ words
def say(t, text, dur=None, size=None):
    w = {'t': T(t), 'text': text, 'narration': True}
    if dur:
        w['duration'] = T(dur)
    if size:
        w['size'] = size
    words.append(w)


def line(t, text, dur=3.2):
    words.append({'t': T(t), 'text': text, 'subtitle': True, 'speaker': 'Lord Voldemort', 'duration': T(dur)})


say(1.0, 'There are names that are still only whispered.', 3.6)
line(6.6, 'At last... Let the world kneel.', 2.6)
line(19.8, 'Kneel.', 2.2)
line(CURSE - 0.15, 'Avada Kedavra!', 1.8)
line(31.9, 'Who else will stand against me?', 3.0)
say(42.4, 'Lord Voldemort', 2.6, 3)


# ------------------------------------------------------------------ camera
def C(t, pos, look, fov, cut=False, ease=None, roll=None):
    k = {'t': T(t), 'pos': [r2(v) for v in pos],
         'look_at': look if isinstance(look, str) else [r2(v) for v in look], 'fov': fov}
    if cut:
        k['cut'] = True
    if ease:
        k['ease'] = ease
    if roll is not None:
        k['roll'] = roll
    cam.append(k)


FACE = [0, 1.78, 0]
# S1 the storm and the comet
C(0, [-14, 9, 18], 'actor:voldemort', 80)
C(5.3, [-8, 5.5, 14], 'actor:voldemort', 76)
# S2 at his feet as he re-forms
C(5.4, [1.2, 0.4, 2.6], [0, 1.4, 0], 60, cut=True)
C(9.0, [0.9, 0.5, 2.0], [0, 1.5, 0], 58)
# S3 the orbit
for i in range(11):
    t_ = 9.0 + i * 0.5
    a = math.radians(-65 + i * 13)
    C(t_, [3.2 * math.sin(a), 1.55, 3.2 * math.cos(a)], [0, 1.45, 0], 60, cut=(i == 0),
      ease=None if i in (0, 10) else 'spline')
# S4 the face
C(14.0, [0.18, 1.8, 1.3], FACE, 38, cut=True, roll=4)
C(16.9, [0.1, 1.8, 1.05], FACE, 36, roll=5)
# S5 over his shoulder as they land
C(17.0, [1.8, 2.6, -4.5], [0, 0.9, 3], 75, cut=True)
C(20.9, [2.4, 2.9, -5.3], [0, 0.9, 3], 72)
# S6 the Mark
C(21.0, [-3.5, 0.6, 4.5], [0, 2.2, 0], 70, cut=True)
C(23.0, [-3.3, 0.6, 4.3], [0, 2.6, 0], 72)
C(24.9, [-3.0, 0.7, 4.0], [0, 10, -5], 85)
# S7 the Auror apparates
C(25.0, [2.6, 1.7, 15.5], [0, 1.4, 5], 65, cut=True)
C(27.3, [2.2, 1.7, 15.0], [0, 1.5, 3], 62)
# S8 the wind-up
C(27.4, [0.8, 0.9, 3.2], [0, 2.0, 0], 55, cut=True)
C(30.0, [0.55, 1.0, 2.35], [0, 2.2, 0], 50)
# S9 the curse
C(30.1, [9, 2.2, 6], [0, 1.5, 6], 70, cut=True)
C(32.5, [8.4, 2.1, 6.4], [0, 1.2, 8], 68)
# S10 Fiendfyre
C(32.6, [-6, 3, -3], [4, 1.5, 4], 80, cut=True)
C(36.9, [-5, 3.4, -1.5], [6, 1.5, 6], 78)
# S11 the push in, and after him into the sky
C(37.0, [0.3, 1.75, 5.5], FACE, 55, cut=True)
C(41.4, [0.15, 1.8, 3.0], FACE, 42)
C(43.4, [0.1, 1.6, 3.2], [2, 11, -12], 60)
C(45.0, [0.1, 1.5, 3.4], [5, 22, -26], 64)

scene = {
    'title': 'The Dark Lord',
    'length': T(LENGTH),
    'radius': R,
    'set': {'fills': fills, 'disks': disks, 'time_of_day': 18000},
    'actors': actors,
    'events': events,
    'words': words,
    'camera': cam,
}


# ------------------------------------------------------------------ checks
def check():
    problems = list(physics_problems(world, R))
    solid = {k for k, b in world.items() if b not in ('minecraft:short_grass', 'minecraft:fern', 'minecraft:dead_bush',
                                                          'minecraft:light', 'minecraft:air')}
    for k in cam:
        x, y, z = k['pos']
        for dx in (-0.3, 0.3):
            for dy in (-0.3, 0.3):
                for dz in (-0.3, 0.3):
                    c = (math.floor(x + dx), math.floor(y + dy), math.floor(z + dz))
                    if c in solid:
                        problems.append('camera at %s %s is inside %s' % (k['t'], k['pos'], world[c]))
    for a in actors:
        for key in a.get('path', []):
            x, y, z = key['pos']
            if y < 0.5 and (math.floor(x), 0, math.floor(z)) in solid:
                problems.append('%s stands in a block at %s' % (a['id'], key['t']))
    return problems


if __name__ == '__main__':
    probs = check()
    for p in probs:
        print('PROBLEM', p)
    txt = write_scene(scene, 'dark_lord')
    print('wrote dark_lord.json (%d chars, %d fills, %d graves)' % (len(txt), len(fills), len(graves)))
    sys.exit(1 if probs else 0)
