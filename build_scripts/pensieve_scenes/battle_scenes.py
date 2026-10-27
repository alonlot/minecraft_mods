"""Generates the Battle of Hogwarts memory, seven chained scenes (docs/superpowers/plans/2026-10-27-battle-of-hogwarts.md):

  battle_of_hogwarts           the defences: McGonagall wakes the stone knights; the shield goes up
  battle_of_hogwarts_siege     the army on the hill; Voldemort's demand; the volley; the shield burns away
  battle_of_hogwarts_bridge    Neville on the wooden bridge; the Snatchers charge; the bridge is blown under them
  battle_of_hogwarts_courtyard the Death Eaters land as smoke; the courtyard battle; giants and statues; spiders
  battle_of_hogwarts_hall      the Great Hall: the wounded; Molly and Bellatrix
  battle_of_hogwarts_duel      grey dawn in the ruined courtyard: Harry and Voldemort
  battle_of_hogwarts_dawn      the broken bridge: the Elder Wand snapped and thrown away

Usage (from the repo root): python -I build_scripts/pensieve_scenes/battle_scenes.py

The castle set is hogwarts_battle.nbt (hogwarts_battle_set.py, cut from the user's Hogwarts world). Scene frame: the
Clock Tower courtyard's paving is y -1 (everyone on it stands at y 0); its open floor is x -45..-2, z -18..17, the
fountain block x -28..-17, z -8..5; the east colonnade opens at z -2..0 onto the covered wooden bridge (walkway
z -2..0, deck feet y 0, x 11..121, the ravine 45 below), which ends in a gatehouse (x 121..130) on the grounds
(x 133.., y about -2..-5). The army's hill rises south-east of the bridge end (x 110..190, z 40..95, y 5..25).

All spoken lines are original paraphrase of the film's beats; only incantations are canonical. Each line has a voice
file (battle_voices.py generates them with Fish Audio from VOICE_LINES, written next to this file as
battle_voice_lines.json); a subtitle lasts as long as its clip when the clip exists.
"""
import json
import math
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import battle_ground as BG  # noqa: E402
import hogwarts_battle_set as HB  # noqa: E402
from battle_kit import Film, Melee, Rng, yaw_to, dist2, walkable, BEAM_SPEED, r2, T  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..'))
VOICE_DIR = os.path.join(REPO, 'src', 'main', 'resources', 'assets', 'hogwartsmagic', 'sounds', 'pensieve', 'voice')
R = 200
FOG = 300
CASTLE_SET = {'templates': [{'id': 'hogwartsmagic:hogwarts_battle', 'at': list(HB.TEMPLATE_AT)}]}

# ---------------------------------------------------------------- the cast
W = {'harry': 'hogwartsmagic:harry_potter_wand', 'elder': 'hogwartsmagic:elder_wand',
     'a': 'hogwartsmagic:dragon_core_wand', 'b': 'hogwartsmagic:phoenix_core_wand', 'c': 'hogwartsmagic:unicorn_core_wand'}
STUDENT_SKINS = [('battle_gryffindor_boy', False), ('battle_gryffindor_girl', True), ('battle_hufflepuff_boy', False),
                 ('battle_hufflepuff_girl', True), ('battle_ravenclaw_boy', False), ('battle_ravenclaw_girl', True),
                 ('battle_slytherin_boy', False), ('battle_slytherin_girl', True)]
DE_SKINS = ['death_eater_mask_a', 'death_eater_mask_b', 'death_eater_mask_c', 'death_eater_bare_a',
            'death_eater_bare_b', 'death_eater']
SNATCHER_SKINS = ['snatcher_a', 'snatcher_b']
CAST = {   # id: (skin, slim, scale, wand)
    'harry': ('harry', False, 0.95, W['harry']), 'ron': ('ron_dh', False, 0.97, W['c']),
    'hermione': ('hermione', False, 0.92, W['b']), 'neville': ('neville_dh', False, 0.95, W['a']),
    'seamus': ('seamus', False, 0.93, W['a']), 'ginny': ('ginny', True, 0.9, W['b']),
    'luna': ('luna', True, 0.9, W['c']), 'mcgonagall': ('mcgonagall_dh', True, 1.0, W['a']),
    'flitwick': ('flitwick', False, 0.6, W['c']), 'slughorn': ('slughorn', False, 1.0, W['b']),
    'molly': ('molly', True, 0.97, W['a']), 'kingsley': ('kingsley', False, 1.05, W['b']),
    'hagrid': ('hagrid_dh', False, 1.5, None), 'voldemort': ('voldemort', False, 1.0, W['elder']),
    'bellatrix': ('bellatrix', True, 0.97, W['c']),
}
SPEAKERS = {'mcgonagall': 'McGonagall', 'flitwick': 'Flitwick', 'neville': 'Neville', 'seamus': 'Seamus',
            'voldemort': 'Voldemort', 'ron': 'Ron', 'hermione': 'Hermione', 'harry': 'Harry', 'molly': 'Molly',
            'bellatrix': 'Bellatrix', 'hagrid': 'Hagrid', 'ginny': 'Ginny'}
VOICE_LINES = []                       # [{name, speaker, text}] for battle_voices.py


def principal(film, cid, pos, yaw=0.0, **kw):
    skin, slim, scale, wand = CAST[cid]
    items = {'mainhand': wand} if wand else None
    return film.actor(cid, 'player', pos, yaw, skin=skin, slim=slim, scale=scale, items=items, **kw)


def student(film, aid, pos, yaw, rng, scale=None):
    skin, slim = rng.pick(STUDENT_SKINS)
    return film.actor(aid, 'player', pos, yaw, skin=skin, slim=slim, scale=r2(scale or rng.uni(0.86, 0.97)),
                      items={'mainhand': rng.pick([W['a'], W['b'], W['c']])})


def eater(film, aid, pos, yaw, rng, snatcher=False):
    skin = rng.pick(SNATCHER_SKINS if snatcher else DE_SKINS)
    return film.actor(aid, 'player', pos, yaw, skin=skin, scale=r2(rng.uni(0.96, 1.04)),
                      items={'mainhand': rng.pick([W['a'], W['b'], W['c']])})


def clip_seconds(name):
    """Length of an OGG voice clip in seconds, or None (read from the last Ogg page's granule position)."""
    p = os.path.join(VOICE_DIR, name + '.ogg')
    if not os.path.exists(p):
        return None
    with open(p, 'rb') as f:
        data = f.read()
    i = data.rfind(b'OggS')
    if i < 0:
        return None
    granule = struct.unpack('<q', data[i + 6:i + 14])[0]
    return granule / 44100.0


def line(film, t, who, text, key):
    """A spoken line: subtitle + voice cue; the subtitle lasts as long as the clip (or a reading speed)."""
    name = 'bh_' + key
    VOICE_LINES.append({'name': name, 'speaker': who, 'text': text})
    dur = clip_seconds(name)
    if dur is None:
        dur = max(1.6, 0.6 + len(text.split()) * 0.36)
    film.say(t, SPEAKERS.get(who, who), text, dur=round(dur + 0.4, 2), voice=name if clip_seconds(name) else None)
    return t + dur


# ---------------------------------------------------------------- places (scene coordinates)
COURT = (-45.0, -2.0, -18.0, 17.0)             # open floor box (x0, x1, z0, z1), feet y 0
FOUNTAIN = (-22.5, 0.0, -1.5)
BRIDGE_Z = -0.5                                # walkway centre line (cells z -2..0; only z -1 at the colonnade)
BRIDGE_X = (11.0, 121.0)
GATE_EAST = (127.0, 0.0, -1.0)
GROUNDS = (140.0, -2.0, -1.0)
HILL_V = (150.0, None, 72.0)                   # Voldemort on the hill (y from the ground)

# the shield over the castle: its east wall stands across the bridge's first third
DOME_C = (-70.0, -50.0, 0.0)
DOME_R = (110.0, 130.0, 100.0)


def dome_point(y, z):
    """The point of the dome's east face at height y, across z."""
    q = 1 - ((y - DOME_C[1]) / DOME_R[1]) ** 2 - ((z - DOME_C[2]) / DOME_R[2]) ** 2
    return (DOME_C[0] + DOME_R[0] * math.sqrt(max(0.0, q)), y, z)


def stand(x, z, y_hint=50.0):
    g = BG.ground(x, z, below=y_hint)
    return (x, float(g), z)


def court_spot(rng, film_actors, t, margin=1.4, box=COURT, tries=60):
    for _ in range(tries):
        x, z = rng.uni(box[0] + 1, box[1] - 1), rng.uni(box[2] + 1, box[3] - 1)
        if not walkable(x, z, 0.0):
            continue
        if all(dist2((x, 0, z), a.pos_at(t)) > margin for a in film_actors if a.d['type'] == 'player'):
            return (round(x, 2), 0.0, round(z, 2))
    raise ValueError('no free courtyard spot')


STATUE_SPOTS = [(x, -16.5) for x in (-41, -35, -29, -17, -11, -5)] + \
               [(x, 15.5) for x in (-41, -35, -29, -17, -11, -5)] + \
               [(-43.5, z) for z in (-10, -4, 3, 9)]


# ================================================================ 1. the defences
def scene_defences():
    rng = Rng(1)
    F = Film('battle_of_hogwarts', 'The Battle of Hogwarts', 140.0)        # trimmed to the last shot below
    # --- the statues kneel round the courtyard
    statues = []
    for i, (x, z) in enumerate(STATUE_SPOTS):
        p = (x + 0.5, 0.0, z + 0.5)
        yaw = 0.0 if z < -10 else (180.0 if z > 10 else -90.0)
        s = F.actor('statue_%d' % (i + 1), 'hogwartsmagic:living_statue', p, yaw)
        statues.append(s)
    # --- McGonagall comes out of the north-west arcade to the fountain's west side
    mcg = principal(F, 'mcgonagall', (-42.5, 0.0, -15.5), 135.0)
    mcg.walk(11.0, (-33.5, 0.0, -1.5), 1.5)
    mcg.face(17.5, -90.0, 0)
    # --- students and teachers hurrying across, then gathering to face her
    people = []
    for i in range(44):
        a = student(F, 'student_%d' % (i + 1), (0, 0, 0), 0.0, rng)
        p = court_spot(rng, people, 0.0)
        a.key(0.0, p)
        a.face(0.0, rng.uni(-180, 180))
        people.append(a)
    for a in people:
        # bustle: a couple of quick crossings each, then turn to her once she speaks
        t = rng.uni(0.0, 2.0)
        for _ in range(2):
            dest = court_spot(rng, people, t + 3)
            sp = rng.uni(2.4, 4.6)
            if dist2(a.pos_at(t), dest) < 3:
                continue
            a.face(t, yaw_to(a.pos_at(t), dest))
            t = a.walk(t, dest, sp) + rng.uni(0.3, 2.5)
            if t > 15:
                break
        t = max(t, 15.0 + rng.uni(0, 1.5))
        a.face_point(t, t + 0.6, mcg.eye(18.0))
    teachers = [principal(F, 'flitwick', (-38.0, 0.0, -9.0), -60.0), principal(F, 'slughorn', (-37.0, 0.0, 6.5), -110.0),
                principal(F, 'molly', (-36.0, 0.0, 3.0), -100.0), principal(F, 'kingsley', (-38.5, 0.0, -6.0), -80.0)]
    trio = [principal(F, 'harry', (-12.0, 0.0, -9.0), 90.0), principal(F, 'ron', (-11.0, 0.0, -10.3), 90.0),
            principal(F, 'hermione', (-11.2, 0.0, -7.8), 90.0)]
    neville = principal(F, 'neville', (-15.5, 0.0, 9.0), 100.0)
    seamus = principal(F, 'seamus', (-14.6, 0.0, 10.4), 100.0)
    for a in trio + [neville, seamus]:
        a.face_point(17.0, 17.8, mcg.eye(18.0))

    # --- her speech
    t = 19.0
    t = line(F, t, 'mcgonagall', 'Hogwarts is under threat. To the walls, all of you. Guard this school with everything you have.', 'mcg_threat')
    mcg.pose(18.8, 'wand_point', 10)
    # Piertotum Locomotor
    T_SPELL = max(t + 1.0, 27.0)
    mcg.pose(T_SPELL - 0.8, 'wand_raised', 6)
    mcg.face(T_SPELL - 0.8, -90.0, -30)
    line(F, T_SPELL - 0.6, 'mcgonagall', 'Piertotum Locomotor!', 'mcg_piertotum')
    for k in range(8):
        F.part(T_SPELL + 0.1 + 0.12 * k, 'minecraft:enchant', mcg.wand(T_SPELL), 40, (1.2, 0.8, 1.2))
    F.snd(T_SPELL, 'minecraft:block.beacon.activate', volume=0.9, pitch=1.2)
    for i, s in enumerate(sorted(statues, key=lambda s: dist2(s.pos_at(0), mcg.pos_at(T_SPELL)))):
        tw = T_SPELL + 0.6 + 0.18 * i
        s.anim(tw, 'WAKE')
        F.snd(tw, 'hogwartsmagic:statue.wake', at=s.chest(tw), volume=1.6, pitch=rng.uni(0.8, 1.0))
        F.part(tw, 'minecraft:poof', s.chest(tw), 14, (0.6, 1.2, 0.6))
    mcg.pose(T_SPELL + 2.0, 'standing', 10)
    mcg.face(T_SPELL + 2.0, -90.0, 0)
    # the students step back from the knights as they rise, looking round at them
    for a in people[:20]:
        p = a.pos_at(T_SPELL + 2)
        near = min(statues, key=lambda s: dist2(s.pos_at(0), p))
        if dist2(near.pos_at(0), p) < 6:
            q = (p[0] + (p[0] - near.pos_at(0)[0]) * 0.4, 0.0, p[2] + (p[2] - near.pos_at(0)[2]) * 0.4)
            if walkable(q[0], q[2]):
                a.face_point(T_SPELL + 2.0, T_SPELL + 2.4, near.chest(T_SPELL + 2))
                a.walk(T_SPELL + 2.4, q, 1.8)
    # her quip, to Molly
    T_QUIP = T_SPELL + 6.0
    mcg.face_point(T_QUIP - 0.6, T_QUIP - 0.1, teachers[2].eye(T_QUIP))
    line(F, T_QUIP, 'mcgonagall', "I have been waiting years to cast that.", 'mcg_quip')
    teachers[2].face_point(T_QUIP - 0.4, T_QUIP + 0.2, mcg.eye(T_QUIP))

    # --- the knights march out in file over the bridge
    T_MARCH = T_SPELL + 4.5
    order = sorted(statues, key=lambda s: -s.pos_at(0)[0])
    for i, s in enumerate(order):
        t0 = T_MARCH + 0.9 * i
        s.anim(t0, 'IDLE', 4)
        mouth = (-6.0, 0.0, BRIDGE_Z)
        p0 = s.pos_at(t0)
        s.face(t0, yaw_to(p0, mouth))
        t1 = s.walk(t0, mouth, 2.2)
        s.face(t1, -90.0)
        t2 = s.walk(t1, (BRIDGE_X[0] + 60.0 - 2.6 * i, 0.0, BRIDGE_Z), 2.2, follow=True)
        for k in range(int((t2 - t0) / 0.6)):
            tt = t0 + 0.6 * k
            if k % 2 == 0 and i % 3 == 0:
                F.snd(tt, 'hogwartsmagic:statue.step', at=s.pos_at(tt), volume=1.4, pitch=rng.uni(0.8, 1.0))
    # --- the shield: the teachers and the oldest students raise it from the courtyard
    T_SHIELD = T_QUIP + 23.0
    casters = teachers + [mcg] + trio + [neville, seamus] + people[:16]
    for i, a in enumerate(casters):
        tu = T_SHIELD - 3.0 + 0.06 * i
        a.pose(tu, 'wand_raised', 8)
        a.face(tu, a.yaw_at(tu), -45)
        a.cast(T_SHIELD - 0.4 + 0.05 * (i % 8), 'protego', None)
        top = (FOUNTAIN[0] + (a.pos_at(tu)[0] - FOUNTAIN[0]) * 0.5, 60.0, a.pos_at(tu)[2] * 0.5)
        a.cast(T_SHIELD + 0.05 * i, 'glacius', top)
        a.pose(T_SHIELD + 4.0 + 0.05 * i, 'standing', 14)
        a.face(T_SHIELD + 4.0 + 0.05 * i, a.yaw_at(T_SHIELD), -25)
    line(F, T_SHIELD - 3.4, 'flitwick', 'Protego Maxima. Fianto Duri. Repello Inimicum!', 'flitwick_shield')
    F.effect(T_SHIELD + 0.4, 'shield_dome', DOME_C, spread=list(DOME_R))
    F.snd(T_SHIELD + 0.4, 'minecraft:block.beacon.activate', volume=1.0, pitch=0.6)
    F.snd(T_SHIELD + 0.9, 'minecraft:block.amethyst_block.resonate', volume=1.0, pitch=0.5)
    F.snd(T_SHIELD + 2.5, 'minecraft:block.beacon.ambient', volume=1.0, pitch=0.7)
    for k in range(10):
        F.part(T_SHIELD + 0.5 + 0.3 * k, 'minecraft:end_rod', (FOUNTAIN[0] + rng.uni(-10, 10), 14 + 2 * k, rng.uni(-10, 10)),
               30, (4, 1, 4))
    line(F, T_SHIELD + 14.0, 'neville', 'Brilliant.', 'neville_brilliant')
    neville.face(T_SHIELD + 13.4, 100.0, -35)

    # --- sound and the world
    F.music(0.0, 'summoning_voldemort', 0.8)
    F.music(50.0, 'chamber_secrets', 0.9)
    F.sfx(0.0, 'first_task_wind_high', 0.3, loop=True, duration=int((T_SHIELD + 18.4) * 20) - 40)
    F.sfx(4.0, 'hall_murmur_a', 0.6)
    F.sfx(11.0, 'hall_murmur_b', 0.5)
    F.sfx(17.6, 'hall_hush', 0.7)
    for t, at in ((3.0, (-120, 0, -150)), (35.0, (160, 0, 120)), (61.0, (-150, 0, 130))):
        F.effect(t, 'lightning', at)
    F.narrate(1.2, 'THE BATTLE OF HOGWARTS', 4.4, size=3)

    # --- camera
    F.shot(0.0, (150.0, 34.0, 70.0), (-30.0, 30.0, 0.0), 62, cut=False)
    F.shot(6.5, (140.0, 30.0, 62.0), (-30.0, 26.0, 0.0), 58, cut=False)
    F.shot(6.5, (-4.0, 1.2, 14.0), (-22.0, 2.0, -6.0), 72)                         # the bustle, low
    F.shot(11.5, (-7.0, 1.4, 13.0), (-26.0, 2.2, -4.0), 72, cut=False)
    F.shot(11.5, (-26.0, 2.4, -16.0), mcg.target, 55)                             # she comes out
    F.shot(17.0, (-29.0, 2.1, -12.0), mcg.target, 52, cut=False)
    F.shot(17.0, (-5.0, 3.6, -1.5), (-33.5, 1.8, -1.5), 50)                       # over the crowd to her
    F.shot(22.5, (-9.0, 3.2, -1.5), (-33.5, 1.8, -1.5), 46, cut=False)
    F.shot(22.5, (-30.5, 1.6, -3.0), mcg.target, 42, roll=-2)                     # close, the order
    F.shot(T_SPELL - 0.3, (-30.6, 1.5, -2.6), mcg.target, 40, cut=False, roll=-3)
    F.shot(T_SPELL - 0.3, (-36.0, 0.6, -12.0), statues[2].target, 60)             # a knight's head lifts
    F.shot(T_SPELL + 2.5, (-36.4, 0.8, -12.4), statues[2].target, 56, cut=False)
    F.shot(T_SPELL + 2.5, (-8.0, 7.0, 0.0), (-24.0, 1.0, -1.0), 82)               # they all rise
    F.shot(T_QUIP - 0.6, (-9.0, 8.0, 1.5), (-26.0, 1.0, -1.0), 82, cut=False)
    F.shot(T_QUIP - 0.6, (-31.0, 1.7, 0.6), mcg.target, 46)                       # the quip
    F.shot(T_QUIP + 3.2, (-31.4, 1.7, 0.9), mcg.target, 44, cut=False)
    F.shot(T_QUIP + 3.2, (-2.0, 0.7, -5.5), (-14.0, 2.0, -1.0), 70, roll=3)       # the file comes at the lens
    F.shot(T_QUIP + 8.2, (-1.0, 0.8, -5.0), (-4.0, 2.4, -1.0), 72, cut=False, roll=3)
    F.shot(T_QUIP + 8.2, (60.0, 2.0, 3.5), (30.0, 1.6, -1.0), 60)                 # on the bridge, toward them
    F.shot(T_QUIP + 14.0, (58.0, 2.2, 3.4), (20.0, 1.8, -1.0), 60, cut=False)
    F.shot(T_QUIP + 14.0, (40.0, -6.0, 26.0), (40.0, 2.0, -1.0), 70)               # the file under the roof, from the ravine air
    F.shot(T_QUIP + 19.5, (46.0, -5.0, 25.0), (44.0, 2.0, -1.0), 68, cut=False)
    F.shot(T_QUIP + 19.5, (-14.0, 1.0, 4.0), (-24.0, 6.0, -2.0), 80)              # wands up
    F.shot(T_SHIELD + 0.6, (-15.0, 1.1, 4.4), (-24.0, 12.0, -2.0), 84, cut=False)
    F.shot(T_SHIELD + 0.6, (150.0, 34.0, 70.0), (-40.0, 20.0, 0.0), 64)           # the dome climbs the castle
    F.shot(T_SHIELD + 6.6, (146.0, 32.0, 66.0), (-40.0, 40.0, 0.0), 66, cut=False)
    F.shot(T_SHIELD + 6.6, (-10.0, 1.2, 12.0), (-22.0, 30.0, -4.0), 90)           # overhead, closing
    F.shot(T_SHIELD + 12.6, (-10.4, 1.2, 12.6), (-22.0, 50.0, -4.0), 92, cut=False)
    F.shot(T_SHIELD + 12.6, (-12.6, 1.5, 7.2), neville.target, 48)                # Neville
    F.L = round(T_SHIELD + 18.4, 2)
    F.shot(F.L, (-13.4, 1.5, 7.9), neville.target, 40, cut=False)
    return F


# ================================================================ 2. the siege
def scene_siege():
    rng = Rng(2)
    F = Film('battle_of_hogwarts_siege', 'The Battle of Hogwarts: The Siege', 72.0)
    vx, vz = HILL_V[0], HILL_V[2]
    vpos = stand(vx, vz)
    vold = principal(F, 'voldemort', vpos, 120.0)
    # the army: a crowd of masked figures over the hillside, and real Death Eaters in front of him
    rows = []
    for k in range(9):
        z = 58 + 4 * k
        x0, x1 = 112 + 2 * k, 188 - k
        pts = []
        for x in range(int(x0), int(x1), 6):
            g = BG.ground(x + 0.5, z + 0.5, below=60)
            if g > -900:
                pts.append((x, g, z))
        for a, b in zip(pts, pts[1:]):
            if abs(a[1] - b[1]) <= 2:
                rows.append([a[0], a[1], a[2], b[0], b[1], b[2], 1])
    F.effect(0.0, 'crowd', (-20, 30, 0), rows=rows, style='death_eaters', count=420)
    eaters = []
    for i in range(36):
        for _ in range(40):
            x, z = vx + rng.uni(-26, 22), vz + rng.uni(-14, -2)
            g = BG.ground(x, z, below=60)
            if g > -900 and BG.clear(x, g + 0.05, z) and all(dist2((x, g, z), e.pos_at(0)) > 1.6 for e in eaters) \
                    and dist2((x, g, z), vpos) > 3:
                break
        e = eater(F, 'death_eater_%d' % (i + 1), (round(x, 2), float(g), round(z, 2)), 120.0, rng)
        e.face(0.0, yaw_to(e.pos_at(0), (-30, 20, 0)))
        eaters.append(e)
    giants = []
    for k, (x, z) in enumerate(((128.0, 86.0), (176.0, 78.0), (160.0, 92.0))):
        p = stand(x, z)
        g = F.actor('giant_%d' % (k + 1), 'hogwartsmagic:giant', p, 120.0, scale=1.8)
        g.anim(0.0, 'IDLE')
        giants.append(g)
    # watchers on the castle side, at the bridge's courtyard end and on the bridge
    watchers = []
    for i in range(14):
        p = (rng.uni(-14.0, -3.0), 0.0, rng.uni(-6.0, 5.0)) if i < 6 else \
            stand(BRIDGE_X[0] + 9 + 2.0 * (i - 6), BRIDGE_Z + rng.uni(-0.6, 0.6), 3)
        a = student(F, 'watcher_%d' % (i + 1), p, -60.0, rng)
        watchers.append(a)
    trio = [principal(F, 'harry', (14.0, 0.0, -1.6), -50.0), principal(F, 'ron', (15.4, 0.0, -0.5), -50.0),
            principal(F, 'hermione', (12.6, 0.0, -0.4), -50.0)]
    F.effect(0.0, 'shield_dome', DOME_C, spread=list(DOME_R), duration=int(52.0 * 20))

    # Voldemort's voice over everything
    t = line(F, 9.0, 'voldemort', 'Many of you mean to fight me. You believe it brave. It is only foolish.', 'vold_fools')
    t = line(F, t + 0.8, 'voldemort', 'Bring me Harry Potter, and I will spare every one of you. Refuse, and none of you will see the dawn.', 'vold_hour')
    for a in trio:
        a.face(10.0, -55.0, -8)
    trio[0].face(16.0, -40.0, -4)
    # he raises the Elder Wand; the volley
    T_V = 29.0
    vold.pose(T_V - 1.2, 'wand_raised', 10)
    vold.face(T_V - 1.2, 100.0, -25)
    vold.pose(T_V - 0.1, 'wand_point', 2)
    hits = []

    def strike(caster, t, spell, y, z):
        p = dome_point(y, z)
        caster.cast(t, spell, p)
        land = t + math.dist(caster.wand(t), p) / BEAM_SPEED
        F.effect(land, 'shield_hit', p)
        hits.append(land)
        return land, p
    land, p = strike(vold, T_V, 'avada_kedavra', 30.0, 10.0)
    F.snd(land, 'minecraft:entity.lightning_bolt.thunder', volume=1.0, pitch=0.6)
    line(F, T_V - 2.6, 'voldemort', 'Begin.', 'vold_begin')
    for e in eaters:
        e.pose(T_V + rng.uni(0.4, 1.4), 'wand_point', 6)
    for wave in range(4):
        for e in eaters:
            tt = T_V + 1.6 + wave * 2.4 + rng.uni(0, 1.8)
            strike(e, round(tt, 2), rng.pick(['bombarda', 'confringo', 'incendio', 'avada_kedavra', 'crucio']),
                   rng.uni(4, 70), rng.uni(-40, 50))
    F.ev(T_V + 2.0, 'effect', 'crowd_react', at=[0, 20, 0], style='chant', duration=200)
    for k, tt in enumerate(sorted(hits)[::5]):
        F.snd(tt, 'minecraft:entity.generic.explode', volume=0.45, pitch=0.5 + 0.06 * (k % 6))
    F.sfx(T_V + 3.0, 'flying_thunder', 0.7)
    # the watchers flinch and back off
    for a in watchers + trio:
        a.face(T_V + 2.0, a.yaw_at(T_V + 2.0), -20)
        if a in watchers[:6]:
            a.act(T_V + 4.0 + rng.uni(0, 2), 'sneak')
            a.act(T_V + 9.0 + rng.uni(0, 2), 'stand')
    line(F, T_V + 7.0, 'ron', "It won't hold. It can't.", 'ron_hold')
    line(F, T_V + 10.0, 'hermione', 'It only has to hold long enough.', 'hermione_hold')
    trio[1].face(T_V + 6.6, -10.0, -14)
    trio[2].face(T_V + 9.6, 130.0, -6)
    # the last strike, all together; the shield burns from it
    T_BREAK = 52.0
    vold.pose(T_BREAK - 3.4, 'wand_raised', 8)
    vold.pose(T_BREAK - 2.3, 'wand_point', 2)
    for i, e in enumerate(eaters):
        strike(e, T_BREAK - 2.2 + 0.03 * i, 'avada_kedavra', 30.0 + rng.uni(-4, 4), 10.0 + rng.uni(-4, 4))
    land_v, hit_v = strike(vold, T_BREAK - 2.3, 'avada_kedavra', 30.0, 10.0)
    F.effect(T_BREAK - 0.05, 'shield_hit', hit_v)
    F.snd(T_BREAK, 'minecraft:block.glass.break', volume=1.0, pitch=0.5)
    F.snd(T_BREAK + 0.1, 'minecraft:block.respawn_anchor.deplete', volume=1.0, pitch=0.6)
    F.snd(T_BREAK + 0.3, 'minecraft:entity.warden.sonic_boom', volume=0.7, pitch=0.5)
    F.snd(T_BREAK + 1.4, 'minecraft:block.glass.break', volume=0.8, pitch=0.7)
    F.sfx(T_BREAK + 0.4, 'flying_thunder', 0.9)
    F.ev(T_BREAK + 0.6, 'effect', 'crowd_react', at=[0, 20, 0], style='erupt', duration=160)
    for k in range(14):
        tt = T_BREAK + 0.8 + 0.5 * k
        F.part(tt, 'minecraft:lava', (rng.uni(-8, 24), rng.uni(10, 20), rng.uni(-8, 8)), 14, (6, 3, 4))
        F.part(tt + 0.2, 'minecraft:ash', (rng.uni(-8, 24), rng.uni(8, 16), rng.uni(-8, 8)), 80, (8, 4, 6))
    for g in giants:
        g.anim(T_BREAK + 1.0 + rng.uni(0, 1), 'ROAR', 6)
        F.snd(T_BREAK + 1.2, 'hogwartsmagic:giant.roar', at=g.chest(T_BREAK), volume=4.0, pitch=0.8)
    vold.pose(T_BREAK + 1.0, 'standing', 12)
    # music and air
    F.music(0.0, 'cave_despair', 0.85)
    F.music(29.0, 'chamber_duel', 1.0)
    F.sfx(0.0, 'first_task_wind_high', 0.35, loop=True, duration=int(72 * 20) - 40)
    for t, at in ((4.0, (300, 0, 120)), (24.0, (100, 0, 190)), (45.0, (-150, 0, -180)), (T_BREAK + 0.5, (40, 0, 40))):
        F.effect(t, 'lightning', at)
    # camera
    V = vold.target
    F.shot(0.0, (100.0, 18.0, 98.0), (150.0, 20.0, 70.0), 70, cut=False)          # the hillside, the army
    F.shot(6.0, (104.0, 20.0, 100.0), (150.0, 22.0, 70.0), 66, cut=False)
    F.shot(6.0, (vx - 6.0, vpos[1] + 1.2, vz - 5.0), V, 44, roll=-3)              # him, from below
    F.shot(12.0, (vx - 5.4, vpos[1] + 1.3, vz - 4.4), V, 40, cut=False, roll=-4)
    F.shot(12.0, (17.0, 1.6, 0.6), (13.6, 1.6, -0.8), 50)                         # the trio listening
    F.shot(18.0, (17.6, 1.6, 0.5), (13.6, 1.6, -0.8), 46, cut=False)
    F.shot(18.0, (vx + 8.0, vpos[1] + 6.0, vz + 12.0), (0.0, 30.0, 0.0), 66)      # over the army at the castle
    F.shot(24.0, (vx + 7.0, vpos[1] + 6.5, vz + 11.0), (0.0, 34.0, 0.0), 62, cut=False)
    F.shot(24.0, (vx - 3.5, vpos[1] + 1.8, vz - 3.0), V, 50)                      # the wand comes up
    F.shot(T_V + 1.0, (vx - 3.0, vpos[1] + 1.9, vz - 2.6), V, 46, cut=False)
    F.shot(T_V + 1.0, (vx + 20.0, vpos[1] + 4.0, vz + 2.0), (40.0, 30.0, 0.0), 74)  # the volley flies
    F.shot(T_V + 6.5, (vx + 18.0, vpos[1] + 5.0, vz + 1.0), (30.0, 40.0, 0.0), 76, cut=False)
    F.shot(T_V + 6.5, (8.0, 1.0, 3.0), (30.0, 30.0, 6.0), 94, roll=4)             # under it, the dome rippling
    F.shot(T_V + 12.5, (8.4, 1.1, 3.4), (30.0, 40.0, 6.0), 96, cut=False, roll=5)
    F.shot(T_V + 12.5, (17.0, 1.5, 0.6), (14.0, 1.7, -0.8), 48)                   # Ron and Hermione
    F.shot(T_BREAK - 6.5, (17.4, 1.5, 0.5), (14.0, 1.7, -0.8), 46, cut=False)
    F.shot(T_BREAK - 6.5, (-8.0, 0.8, 2.0), (-2.0, 12.0, -1.0), 88, roll=-3)      # the courtyard, faces up at the flashes
    F.shot(T_BREAK - 3.0, (-8.4, 0.9, 2.4), (-2.0, 16.0, -1.0), 90, cut=False, roll=-4)
    F.shot(T_BREAK - 3.0, (vx + 2.2, vpos[1] + 2.3, vz + 1.6), (40.0, 30.0, 10.0), 58)  # over his shoulder
    F.shot(T_BREAK + 1.5, (vx + 2.6, vpos[1] + 2.5, vz + 2.0), (30.0, 34.0, 10.0), 62, cut=False)
    F.shot(T_BREAK + 1.5, (50.0, 45.0, 45.0), (-20.0, 40.0, 0.0), 90)             # the burn races over the castle
    F.shot(T_BREAK + 7.5, (46.0, 47.0, 41.0), (-30.0, 50.0, 0.0), 92, cut=False)
    F.shot(T_BREAK + 7.5, (90.0, 10.0, 70.0), (150.0, 20.0, 75.0), 72)            # the army roars
    F.shot(F.L - 6.5, (92.0, 11.0, 72.0), (150.0, 22.0, 75.0), 68, cut=False)
    F.shot(F.L - 6.5, (17.0, 1.5, 0.6), trio[0].target, 46)                       # Harry
    F.shot(F.L, (16.6, 1.5, 0.5), trio[0].target, 38, cut=False)
    return F


# ================================================================ 3. the bridge
def scene_bridge():
    rng = Rng(3)
    F = Film('battle_of_hogwarts_bridge', 'The Battle of Hogwarts: The Bridge', 120.0)    # trimmed to the last shot
    nev = principal(F, 'neville', stand(96.0, BRIDGE_Z, 3), -90.0)
    sea = principal(F, 'seamus', (6.5, 0.0, BRIDGE_Z - 1.0), -90.0)
    mcg = principal(F, 'mcgonagall', (-3.0, 0.0, -3.5), -90.0)
    # the Snatchers mass on the grounds beyond the gatehouse
    snatchers = []
    for i in range(40):
        for _ in range(80):
            x, z = rng.uni(134, 162), rng.uni(-12, 10)
            g = BG.ground(x, z, below=10)
            if g > -900 and BG.clear(x, g + 0.05, z) and all(dist2((x, g, z), s.pos_at(0)) > 1.3 for s in snatchers):
                break
        s = eater(F, 'snatcher_%d' % (i + 1), (round(x, 2), float(g), round(z, 2)), 90.0, rng, snatcher=i % 3 != 0)
        s.face(0.0, 90.0 + rng.uni(-20, 20))
        snatchers.append(s)
    # Neville walks out alone toward them, stops, taunts them
    nev.walk(2.0, (112.0, 0.0, BRIDGE_Z), 1.1, follow=True)
    nev.face(16.5, -90.0, 0)
    line(F, 18.0, 'neville', "Well? Come on, then! What are you waiting for?", 'nev_taunt')
    nev.limb('right_arm', 'rot', 17.6, (0, 0, 0))
    nev.limb('right_arm', 'rot', 18.4, (-110, 0, 10))
    nev.limb('right_arm', 'rot', 19.8, (-110, 0, 10))
    nev.limb('right_arm', 'rot', 20.6, (0, 0, 0))
    # they charge: through the gatehouse onto the deck in a stream
    T_CHARGE = 22.5
    for i, s in enumerate(sorted(snatchers, key=lambda s: dist2(s.pos_at(0), GATE_EAST))):
        t0 = T_CHARGE + 0.18 * i + rng.uni(0, 0.2)
        p0 = s.pos_at(t0)
        s.face(t0, yaw_to(p0, GATE_EAST))
        t1 = s.walk(t0, (131.5, 0.0, BRIDGE_Z + rng.uni(-0.4, 0.4)), rng.uni(4.6, 5.4), follow=True)
        s.face(t1, 90.0)
        s.walk(t1, (14.0 + 1.0 * i, 0.0, BRIDGE_Z + rng.uni(-0.5, 0.5)), 5.0, follow=True)
    F.sfx(T_CHARGE, 'first_task_crowd_bed', 0.4, loop=True, duration=400)
    # Neville runs for it
    T_RUN = T_CHARGE + 1.6
    nev.turn(T_RUN - 0.3, T_RUN, 90.0, 0)
    nev.walk(T_RUN, (3.0, 0.0, BRIDGE_Z), 5.6, follow=True)
    line(F, T_RUN + 0.2, 'seamus', 'Neville! Run!', 'seamus_run')
    # the charges go off from the far end and race after him: the front gains about a block a second
    T_BOOM = T_RUN + 3.4
    V_WAVE = 6.6
    X_FAR = 118.0

    def front(t):
        return X_FAR - V_WAVE * (t - T_BOOM)
    line(F, T_BOOM - 0.8, 'neville', 'Now, Seamus! Now!', 'nev_now')
    sea.pose(T_BOOM - 1.2, 'wand_point', 6)
    sea.cast(T_BOOM - 0.6, 'confringo', (40.0, 0.5, BRIDGE_Z))
    blasts = []
    x = X_FAR
    while x >= 14.0:
        tb = T_BOOM + (X_FAR - x) / V_WAVE
        np_ = nev.pos_at(tb)
        assert np_[0] < x - 5.0, ('the blast catches Neville', x, np_)
        blasts.append((tb, x))
        x -= 3.0
    for k, (tb, x) in enumerate(blasts):
        gy = BG.ground(x + 0.5, BRIDGE_Z, below=3)
        F.blast(tb, (x + 0.5, gy + 0.8, BRIDGE_Z), big=(k % 3 == 0), rng=rng, sound=(k % 2 == 0))
        F.rubble(tb + 0.05, (x + 0.5, gy + 0.5, BRIDGE_Z), 2, rng, spread=1.2, fall=-44.0,
                 block_ids=['minecraft:spruce_planks', 'minecraft:spruce_slab', 'minecraft:stripped_spruce_log'],
                 scale=(0.6, 1.0))
        F.rubble(tb + 0.2, (x + 0.5, gy + 9.5, BRIDGE_Z), 1, rng, spread=1.6, fall=-44.0,
                 block_ids=['minecraft:deepslate_tiles', 'minecraft:deepslate_tile_slab'], scale=(0.7, 1.0))
        # the real deck, posts and roof go with it
        F.ev(tb + 0.04, 'block', 'minecraft:air', at=[int(x) - 1, -3, -3], to=[int(x) + 1, 13, 1])
    # every Snatcher the front reaches on the deck falls with it; the rest stop at the gatehouse
    for s in snatchers:
        caught = None
        t = T_BOOM
        while t < T_BOOM + (X_FAR - 14) / V_WAVE + 0.5:
            p = s.pos_at(t)
            if 13 <= p[0] <= X_FAR + 1.5 and p[0] >= front(t) - 0.5:
                caught = (t, p)
                break
            t += 0.05
        if caught:
            tb, p = caught
            s.path = [k for k in s.path if k[0] < tb]
            s.key(tb, p, 'linear')
            for j in range(1, 9):
                f = j / 8
                s.key(tb + 1.8 * f, (p[0] + rng.uni(-1, 1) * f, p[1] - 44.0 * f * f, p[2] + rng.uni(-2, 2) * f),
                      'linear' if j < 8 else 'out')
            s.act(tb + 0.05, 'hurt')
            s.limb('body', 'rot', tb, (0, 0, 0))
            s.limb('body', 'rot', tb + 0.8, (rng.uni(-60, 60), 0, rng.uni(-40, 40)))
            s.limb('right_arm', 'rot', tb, (0, 0, 0))
            s.limb('right_arm', 'rot', tb + 0.4, (-170, 0, 20))
            s.limb('left_arm', 'rot', tb, (0, 0, 0))
            s.limb('left_arm', 'rot', tb + 0.4, (-170, 0, -20))
            s.airborne = True
            s.act(tb + 1.9, 'vanish')
        else:
            # still on the grounds or in the gatehouse: pull up short of the gap and stare
            t_stop = None
            for k in s.path:
                if k[0] >= T_BOOM and k[1][0] < X_FAR + 3:
                    t_stop = k[0]
                    break
            if t_stop is not None:
                p = s.pos_at(t_stop)
                s.path = [k for k in s.path if k[0] < t_stop] + [(t_stop, (max(p[0], X_FAR + 4.0), p[1], p[2]), 'out')]
    for k in range(10):
        F.snd(T_BOOM + 0.2 + 1.4 * k, 'minecraft:block.wood.break', at=(front(T_BOOM + 1.4 * k), 0, -1), volume=2.5,
              pitch=0.6)
    F.sfx(T_BOOM, 'first_task_bridge_crash', 1.0)
    T_SAFE = blasts[-1][0]
    nev.act(T_SAFE - 0.3, 'hurt')
    nev.face(T_SAFE + 0.8, 90.0, -10)
    line(F, T_SAFE + 5.0, 'neville', 'Ha! Did you see that?', 'nev_ha')
    sea.pose(T_SAFE + 0.5, 'standing', 10)
    sea.face_point(T_SAFE + 3.0, T_SAFE + 3.6, nev.eye(T_SAFE + 4))
    F.music(0.0, 'first_task_chase', 0.9)
    F.sfx(0.0, 'first_task_wind_high', 0.3, loop=True, duration=int((T_SAFE + 10.5) * 20) - 40)
    # camera
    N = nev.target
    F.shot(0.0, (60.0, 6.0, 12.0), (100.0, 2.0, -1.0), 62, cut=False)              # the bridge from outside, him on it
    F.shot(6.0, (66.0, 5.0, 11.0), (106.0, 2.0, -1.0), 58, cut=False)
    F.shot(6.0, (104.0, 1.0, BRIDGE_Z), N, 48)                                        # behind him walking out
    F.shot(11.5, (106.0, 1.0, BRIDGE_Z), N, 48, cut=False)
    F.shot(11.5, (124.0, 1.6, BRIDGE_Z), (150.0, 1.6, -1.0), 56)                      # what he sees: the Snatchers
    F.shot(16.5, (123.0, 1.6, BRIDGE_Z), (150.0, 1.6, -1.0), 52, cut=False)
    F.shot(16.5, (115.4, 1.4, BRIDGE_Z), N, 44)                                        # the taunt
    F.shot(T_CHARGE, (115.0, 1.4, BRIDGE_Z), N, 40, cut=False)
    F.shot(T_CHARGE, (128.0, 1.0, BRIDGE_Z), (150.0, 2.0, -1.0), 70, roll=-4)          # they come
    F.shot(T_RUN + 1.0, (128.5, 1.0, BRIDGE_Z), (140.0, 2.0, -1.0), 72, cut=False, roll=-4)
    nx = nev.pos_at(T_RUN + 1.0)[0]
    F.shot(T_RUN + 1.0, (nx - 9.0, 1.2, BRIDGE_Z), N, 66)                              # running at the lens, the fire behind
    F.shot(T_RUN + 6.0, (nev.pos_at(T_RUN + 6.0)[0] - 9.0, 1.2, BRIDGE_Z), N, 66, cut=False, ease='linear')
    F.shot(T_RUN + 6.0, (40.0, -10.0, 30.0), (nev.pos_at(T_RUN + 8.0)[0], -2.0, -1.0), 80)  # side on from the ravine air
    F.shot(T_RUN + 12.0, (34.0, -10.0, 30.0), (nev.pos_at(T_RUN + 12.0)[0] + 6, -4.0, -1.0), 82, cut=False)
    F.shot(T_RUN + 12.0, (7.5, 1.4, BRIDGE_Z + 0.2), N, 60)                            # from the courtyard end
    F.shot(T_SAFE, (7.5, 1.4, BRIDGE_Z + 0.2), N, 54, cut=False)
    F.shot(T_SAFE, (60.0, -30.0, 22.0), (60.0, -30.0, -1.0), 80)                       # the wreck falls into the ravine
    F.shot(T_SAFE + 4.5, (62.0, -32.0, 24.0), (64.0, -40.0, -1.0), 82, cut=False)
    F.shot(T_SAFE + 4.5, (2.4, 1.2, -1.6), N, 46)                                      # Neville on the stub
    F.L = round(T_SAFE + 10.5, 2)
    F.shot(F.L, (2.8, 1.3, -1.8), N, 40, cut=False)
    return F


# ---------------------------------------------------------------- the bridge stays broken after scene 3
BRIDGE_GAP = (14, 118)                         # deck x cells removed by the blasts


def bridge_gone(F, t=0.0):
    """The bridge as scene 3 left it: the deck, posts and roof cleared from x 14 to 118 (box block events)."""
    x = BRIDGE_GAP[0]
    while x <= BRIDGE_GAP[1]:
        x1 = min(BRIDGE_GAP[1], x + 15)
        F.ev(t, 'block', 'minecraft:air', at=[x, -3, -3], to=[x1, 13, 1])
        x = x1 + 1


# ================================================================ 4. the courtyard
def scene_courtyard():
    rng = Rng(4)
    F = Film('battle_of_hogwarts_courtyard', 'The Battle of Hogwarts: The Courtyard', 150.0)
    bridge_gone(F)
    melee = Melee(F, COURT, rng, y=0.0)
    taken = []

    def spot(box=COURT, margin=2.0):
        for _ in range(200):
            x, z = rng.uni(box[0] + 1, box[1] - 1), rng.uni(box[2] + 1, box[3] - 1)
            if walkable(x, z) and all(dist2((x, 0, z), q) > margin for q in taken):
                taken.append((x, 0.0, z))
                return (round(x, 2), 0.0, round(z, 2))
        raise ValueError('no free spot')
    # --- the defenders, spread over the courtyard in knots, wands up, waiting
    defenders = []
    named = [principal(F, 'mcgonagall', spot(), -90.0), principal(F, 'kingsley', spot(), -90.0),
             principal(F, 'flitwick', spot(), -90.0), principal(F, 'neville', spot(), -90.0),
             principal(F, 'seamus', spot(), -90.0), principal(F, 'ginny', spot(), -90.0),
             principal(F, 'luna', spot(), -90.0)]
    for a in named:
        melee.add(a, 0, 0.0, hp=4 if a.id in ('mcgonagall', 'kingsley', 'flitwick') else 3, speed=3.4,
                  aggression=0.8)
        defenders.append(a)
    for i in range(60):
        a = student(F, 'student_%d' % (i + 1), spot(margin=1.7), rng.uni(-150, -30), rng)
        melee.add(a, 0, 0.0, hp=rng.pick([1, 2, 2, 3]), speed=rng.uni(3.0, 4.2), aggression=rng.uni(0.3, 0.8))
        defenders.append(a)
    for a in defenders:
        a.face(0.0, rng.uni(-130, -50), -12)
        a.pose(rng.uni(2.0, 6.0), 'wand_point', 10)
    # Ron and Hermione are kept out of the melee for the spider beat
    ron = principal(F, 'ron', (-38.5, 0.0, -14.5), 0.0)
    her = principal(F, 'hermione', (-37.0, 0.0, -15.0), 0.0)
    # --- the Death Eaters come as smoke, over the walls, in waves
    eaters = []
    waves = [(4.0, 16), (11.0, 18), (19.0, 16), (30.0, 12), (44.0, 10), (58.0, 10)]
    n = 0
    for t_wave, count in waves:
        for k in range(count):
            n += 1
            land = court_spot(rng, defenders + eaters, t_wave + 4, margin=1.5, tries=400)
            start = (rng.uni(40, 120), rng.uni(24, 46), rng.uni(-50, 50))
            e = eater(F, 'death_eater_%d' % n, start, 90.0, rng)
            t0 = t_wave + rng.uni(0, 3.5)
            t_land = e.smoke_flight(t0, start, land, rng.uni(2.6, 3.8))
            F.part(t_land, 'minecraft:large_smoke', land, 30, (0.7, 1.0, 0.7))
            if k % 3 == 0:
                F.snd(t_land, 'minecraft:entity.illusioner.mirror_move', at=land, volume=1.4, pitch=rng.uni(0.5, 0.7))
            melee.add(e, 1, t_land + 0.3, hp=rng.pick([2, 2, 3]), speed=rng.uni(3.2, 4.4), aggression=rng.uni(0.5, 0.9))
            eaters.append(e)
    melee.t_end = 140.0
    melee.run()
    # --- the stone knights hold the colonnades, and hammer whatever comes near
    statues = []
    for i, (x, z) in enumerate([(-44.0, -12.0), (-44.0, 10.0), (-6.0, -16.0), (-6.0, 15.0), (-30.0, -16.5),
                                (-14.0, 15.5)]):
        s = F.actor('statue_%d' % (i + 1), 'hogwartsmagic:living_statue', (x + 0.5, 0.0, z + 0.5), 90.0)
        s.anim(0.0, 'IDLE')
        statues.append(s)
    for s in statues:
        t = rng.uni(10.0, 16.0)
        while t < 135.0:
            here = s.pos_at(t)
            foes = [e for e in eaters if e.shown(t) and not e.in_smoke(t) and dist2(e.pos_at(t), here) < 9]
            if not foes:
                t += 1.5
                continue
            e = min(foes, key=lambda e: dist2(e.pos_at(t), here))
            ep = e.pos_at(t)
            d = dist2(ep, here)
            to = (here[0] + (ep[0] - here[0]) * (1 - 1.6 / max(d, 0.1)), 0.0, here[2] + (ep[2] - here[2]) * (1 - 1.6 / max(d, 0.1)))
            s.face(t, yaw_to(here, ep))
            t1 = s.walk(t, to, 1.8) if walkable(to[0], to[2]) and d > 2.2 else t
            s.face(t1, yaw_to(s.pos_at(t1), e.pos_at(t1)))
            s.act(t1 + 0.1, 'swing')
            F.snd(t1 + 0.35, 'hogwartsmagic:statue.slam', at=e.chest(t1), volume=1.6, pitch=rng.uni(0.8, 1.0))
            e.act(t1 + 0.4, 'hurt')
            t = t1 + rng.uni(2.0, 3.2)
    # --- a giant on the south terrace, reaching over the arcade; three knights bring it down
    g = F.actor('giant_1', 'hogwartsmagic:giant', (-24.0, 0.0, 22.5), 180.0, scale=1.7)
    g.anim(0.0, 'IDLE')
    g.act(0.0, 'vanish')
    g.act(34.0, 'appear')
    g.key(34.0, (-60.0, 0.0, 22.5))
    g.anim(34.0, 'WALK', 4)
    g.face(34.0, -90.0)
    g.key(41.0, (-24.0, 0.0, 22.5), 'out')
    g.anim(41.0, 'ROAR', 6)
    g.face(41.5, 180.0)
    F.snd(41.2, 'hogwartsmagic:giant.roar', at=(-24, 8, 22), volume=4.0, pitch=0.85)
    for k in range(6):
        F.snd(34.4 + 1.1 * k, 'hogwartsmagic:giant.step', at=g.pos_at(34.4 + 1.1 * k), volume=3.0, pitch=0.8)
    g.anim(44.5, 'SWIPE', 4)
    F.snd(45.3, 'hogwartsmagic:giant.swipe', at=(-24, 6, 18), volume=3.0)
    F.blast(45.4, (-24.0, 7.0, 18.0), big=True, rng=rng)
    F.rubble(45.5, (-24.0, 7.5, 18.5), 10, rng, spread=3.0, fall=0.0, out=(0.0, -3.0))
    g.anim(48.0, 'IDLE', 6)
    g.anim(55.0, 'SWIPE', 4)
    F.blast(55.9, (-30.0, 7.0, 18.0), big=True, rng=rng)
    F.rubble(56.0, (-30.0, 7.5, 18.5), 8, rng, spread=3.0, fall=0.0, out=(0.0, -3.0))
    knights = statues[3:6]
    for i, s in enumerate(knights):
        t = 58.0 + 0.6 * i
        s.face(t, yaw_to(s.pos_at(t), g.pos_at(t)))
        s.act(t + 0.8, 'swing')
        s.act(t + 3.2, 'swing')
        F.snd(t + 1.1, 'hogwartsmagic:statue.impact', at=(-24, 3, 20), volume=2.0)
    g.anim(59.5, 'STAGGER', 3)
    g.act(60.0, 'hurt')
    g.anim(62.0, 'SWIPE', 4)
    knights[0].anim(62.8, 'CRUMBLE')
    F.snd(62.9, 'hogwartsmagic:statue.crumble', at=knights[0].chest(62.9), volume=2.0)
    F.rubble(63.0, knights[0].chest(63.0), 6, rng, spread=1.5, fall=0.0, block_ids=['minecraft:stone', 'minecraft:andesite', 'minecraft:cobblestone'])
    g.anim(65.0, 'STAGGER', 3)
    g.act(66.0, 'hurt')
    g.act(68.0, 'die')
    F.snd(68.0, 'hogwartsmagic:giant.death', at=g.chest(68.0), volume=4.0)
    F.blast(69.2, (-24.0, 1.0, 22.5), big=True, rng=rng)
    # --- acromantulas over the north wall; one drives Ron and Hermione along the arcade
    spiders = []
    for i in range(8):
        x = -40.0 + 4.5 * i
        top = (x, 16.0, -21.5)
        land = court_spot(rng, defenders + eaters, 76.0, margin=1.6, box=(-44, -4, -17, -9))
        sp = F.actor('spider_%d' % (i + 1), 'hogwartsmagic:acromantula', top, 0.0)
        sp.act(0.0, 'vanish')
        t0 = 72.0 + 0.5 * i
        sp.act(t0, 'appear')
        sp.key(t0, top)
        sp.key(t0 + 1.8, (land[0], 0.0, land[2]), 'in')
        sp.face(t0, 0.0, 40)
        sp.face(t0 + 1.8, rng.uni(-40, 40), 0)
        F.snd(t0, 'hogwartsmagic:acromantula.ambient', at=top, volume=2.0, pitch=rng.uni(0.8, 1.1))
        # scuttle after the nearest student, who flees
        t = t0 + 2.2
        while t < 128.0:
            here = sp.pos_at(t)
            prey = [a for a in defenders if a.shown(t) and dist2(a.pos_at(t), here) < 14]
            if not prey:
                t += 1.0
                continue
            v = min(prey, key=lambda a: dist2(a.pos_at(t), here))
            vp = v.pos_at(t)
            d = dist2(vp, here)
            to = (here[0] + (vp[0] - here[0]) * (1 - 1.8 / max(d, 0.1)), 0.0, here[2] + (vp[2] - here[2]) * (1 - 1.8 / max(d, 0.1)))
            sp.face(t, yaw_to(here, vp))
            t1 = sp.walk(t, to, 6.5) if walkable(to[0], to[2]) else t
            v.act(t1 + 0.2, 'hurt')
            F.snd(t1 + 0.1, 'hogwartsmagic:acromantula.hurt', at=sp.pos_at(t1), volume=1.6, pitch=1.3)
            t = t1 + rng.uni(1.6, 2.6)
        spiders.append(sp)
    # Ron and Hermione: the spider comes for them, they run the arcade, she blasts it
    chaser = F.actor('spider_ron', 'hogwartsmagic:acromantula', (-42.0, 16.0, -21.5), 0.0, scale=1.4)
    chaser.act(0.0, 'vanish')
    chaser.act(79.0, 'appear')
    chaser.key(79.0, (-42.0, 16.0, -21.5))
    chaser.key(80.6, (-41.5, 0.0, -16.5), 'in')
    chaser.face(79.0, 0.0, 40)
    chaser.face(80.6, 90.0, 0)
    F.snd(79.2, 'hogwartsmagic:acromantula.ambient', at=(-42, 10, -18), volume=3.0, pitch=0.7)
    for a in (ron, her):
        a.face_point(80.4, 80.8, chaser.chest(80.8))
    line(F, 81.4, 'ron', "Spiders. Why is it always spiders?", 'ron_spiders')
    line(F, 83.6, 'hermione', 'Ron, run!', 'hermione_run')
    for a, dz in ((ron, 0.0), (her, 1.2)):
        a.turn(83.6, 83.9, -90.0, 0)
        a.walk(83.9, (-12.0, 0.0, -15.0 + dz), 5.2)
    chaser.walk(84.2, (-16.0, 0.0, -15.5), 5.0)
    her.turn(88.9, 89.3, 90.0, 0)
    her.pose(89.0, 'wand_point', 3)
    her.cast(89.5, 'confringo', chaser.target)
    ron.turn(89.0, 89.4, 90.0, 0)
    ron.pose(89.2, 'wand_point', 3)
    ron.cast(89.9, 'stupefy', chaser.target)
    chaser.act(89.8, 'hurt')
    chaser.act(90.4, 'die')
    F.blast(89.8, chaser.chest(89.8), big=True, rng=rng)
    for a in (ron, her):
        a.pose(91.5, 'standing', 10)
    # --- a curse hits the Clock Tower: masonry falls into the west end
    F.blast(100.0, (-50.0, 34.0, -2.0), big=True, rng=rng)
    F.snd(100.0, 'minecraft:entity.generic.explode', volume=1.2, pitch=0.5)
    F.rubble(100.15, (-50.5, 34.0, -2.0), 26, rng, spread=2.6, fall=0.0, out=(3.0, 0.0), scale=(0.7, 1.3))
    F.snd(101.6, 'minecraft:block.stone.break', at=(-44, 0, -2), volume=3.0, pitch=0.5)
    # fires where the curses landed
    fires = 0
    for k in range(40):
        x, z = rng.uni(-44, -3), rng.uni(-17, 16)
        if fires >= 14 or not walkable(x, z):
            continue
        t = rng.uni(20, 110)
        if any(dist2(a.pos_at(tt), (x, 0, z)) < 1.4 for a in defenders + eaters for tt in (t, t + 10, t + 25)):
            continue
        F.block(t, 'minecraft:fire' if k % 3 else 'minecraft:campfire', (x, 0, z))
        fires += 1
    # --- the lull: Voldemort's voice; the fighting dies away
    T_TRUCE = 126.0
    line(F, T_TRUCE, 'voldemort', 'You have fought bravely, and I honour it. My forces will withdraw. See to your dead.', 'vold_withdraw')
    for e in eaters:
        if e.shown(T_TRUCE + 2):
            tt = T_TRUCE + 2.0 + rng.uni(0, 3)
            e.act(tt, 'smoke')
            here = e.pos_at(tt)
            e.hold(tt)
            e.key(tt + 2.0, (here[0] + 30, 40.0, here[2] + rng.uni(-20, 20)), 'in')
            e.act(tt + 2.0, 'vanish')
            e.airborne = True
    # --- sound
    F.music(0.0, 'chamber_duel', 1.0)
    F.music(58.0, 'first_task_chase', 0.95)
    F.music(T_TRUCE - 1.0, 'cave_despair', 0.8)
    F.sfx(0.0, 'first_task_wind_high', 0.25, loop=True, duration=int((T_TRUCE + 2) * 20))
    F.sfx(8.0, 'first_task_crowd_bed', 0.45, loop=True, duration=int((T_TRUCE - 8) * 20))
    for t, at in ((2.0, (60, 0, 90)), (37.0, (-120, 0, -120)), (78.0, (120, 0, -90)), (112.0, (-100, 0, 140))):
        F.effect(t, 'lightning', at)
    for k in range(30):
        tt = 12.0 + 3.8 * k
        if tt < T_TRUCE:
            F.snd(tt, 'minecraft:entity.generic.explode', at=(rng.uni(-44, -4), 2, rng.uni(-16, 16)), volume=1.0,
                  pitch=rng.uni(0.6, 1.0))
    # --- camera
    def wide_high(t0, t1, a, b):
        F.shot(t0, a[0], a[1], a[2], cut=t0 > 0)
        F.shot(t1, b[0], b[1], b[2], cut=False)
    wide_high(0.0, 6.0, ((90.0, 30.0, 40.0), (-24.0, 6.0, 0.0), 66), ((84.0, 28.0, 36.0), (-24.0, 4.0, 0.0), 62))      # smoke streams in
    wide_high(6.0, 11.0, ((-8.0, 0.6, 8.0), (-22.0, 3.0, -2.0), 84), ((-8.4, 0.7, 7.6), (-22.0, 6.0, -2.0), 86))      # they slam down among them
    wide_high(11.0, 17.0, ((-44.0, 24.0, -14.0), (-20.0, 0.0, 2.0), 76), ((-44.0, 24.0, -10.0), (-18.0, 0.0, 2.0), 74))  # high, the whole court
    wide_high(17.0, 22.5, ((-40.0, 1.6, -4.0), 'actor:mcgonagall', 52), ((-39.0, 1.6, -5.0), 'actor:mcgonagall', 50))
    wide_high(22.5, 28.0, ((-6.0, 1.7, -12.0), (-30.0, 1.6, 4.0), 62), ((-14.0, 1.7, -12.0), (-30.0, 1.6, 6.0), 62))  # tracking along
    wide_high(28.0, 33.5, ((-20.0, 1.5, 12.0), 'actor:kingsley', 48), ((-21.0, 1.5, 11.0), 'actor:kingsley', 46))
    wide_high(33.5, 39.0, ((-10.0, 10.0, 4.0), (-40.0, 3.0, 20.0), 78), ((-12.0, 10.0, 6.0), (-30.0, 6.0, 20.0), 78))  # the giant comes along the terrace
    wide_high(39.0, 44.5, ((-24.0, 0.5, 6.0), (-24.0, 12.0, 22.0), 86), ((-24.0, 0.6, 7.0), (-24.0, 14.0, 22.0), 88))  # it rears over the arcade
    wide_high(44.5, 50.0, ((-14.0, 2.0, 6.0), (-24.0, 4.0, 17.0), 74), ((-15.0, 2.2, 5.0), (-24.0, 3.0, 17.0), 74))  # the swipe, the rubble
    wide_high(50.0, 55.5, ((-30.0, 1.5, 0.0), 'actor:neville', 52), ((-31.0, 1.5, 1.0), 'actor:neville', 50))
    wide_high(55.5, 61.0, ((-34.0, 1.0, 10.0), (-24.0, 6.0, 20.0), 72), ((-33.0, 1.1, 11.0), (-24.0, 6.0, 20.0), 70))  # knights at the giant
    wide_high(61.0, 66.5, ((-20.0, 3.0, 8.0), (-24.0, 6.0, 22.0), 64), ((-21.0, 3.2, 9.0), (-24.0, 6.0, 22.0), 64))
    wide_high(66.5, 72.0, ((-12.0, 2.0, 12.0), (-24.0, 4.0, 22.0), 74), ((-12.6, 2.2, 12.6), (-24.0, 1.0, 22.0), 76))  # it falls
    wide_high(72.0, 77.5, ((-24.0, 1.0, -8.0), (-24.0, 14.0, -21.0), 80), ((-24.0, 1.0, -7.0), (-24.0, 8.0, -18.0), 82))  # spiders over the wall
    wide_high(77.5, 83.0, ((-34.0, 1.6, -11.0), 'actor:ron', 52), ((-34.6, 1.6, -11.6), 'actor:ron', 50))
    wide_high(83.0, 88.5, ((-6.0, 1.4, -15.5), 'actor:ron', 66), ((-8.0, 1.4, -15.5), 'actor:ron', 66))       # they run at the lens
    wide_high(88.5, 94.0, ((-10.0, 1.6, -12.5), 'actor:hermione', 50), ((-10.4, 1.6, -12.8), 'actor:hermione', 48))
    wide_high(94.0, 99.5, ((-26.0, 1.7, 14.0), (-30.0, 1.4, -2.0), 64), ((-34.0, 1.7, 14.0), (-34.0, 1.4, -2.0), 64))  # tracking the chaos
    wide_high(99.5, 105.0, ((-11.0, 2.0, 0.0), (-50.0, 30.0, -2.0), 74), ((-12.0, 1.8, 0.0), (-48.0, 4.0, -2.0), 78))  # the tower is hit
    wide_high(105.0, 110.5, ((-36.0, 1.6, 9.0), 'actor:luna', 50), ((-36.4, 1.6, 9.6), 'actor:luna', 48))
    wide_high(110.5, 116.0, ((-4.0, 6.0, 15.0), (-28.0, 1.0, -6.0), 70), ((-6.0, 6.5, 13.0), (-28.0, 1.0, -6.0), 70))
    wide_high(116.0, 121.5, ((-36.0, 1.5, -6.0), 'actor:mcgonagall', 48), ((-36.4, 1.5, -6.6), 'actor:mcgonagall', 46))
    wide_high(121.5, 127.0, ((-44.0, 24.0, 12.0), (-20.0, 0.0, -2.0), 76), ((-44.0, 25.0, 10.0), (-20.0, 0.0, -2.0), 74))
    wide_high(127.0, 132.5, ((-2.0, 1.0, 4.0), (-24.0, 20.0, -2.0), 84), ((-2.4, 1.0, 4.4), (-24.0, 30.0, -2.0), 86))  # they leave as smoke
    F.L = 138.0
    wide_high(132.5, F.L, ((-30.0, 1.6, 6.0), 'actor:neville', 46), ((-30.4, 1.6, 6.6), 'actor:neville', 38))
    return F


# ================================================================ 5. the Great Hall
import great_hall_set as GH  # noqa: E402

HALL_SET = {'templates': [{'id': 'hogwartsmagic:great_hall', 'at': list(GH.TEMPLATE_AT)}]}
HALL_AREA = (5.0, 63.0, -16.0, 16.0)
# lying flat (lake_patronus): additive limbs over a zeroed pose; the actor position is the head
LIE_ROT = {'head': (-90, 0, 0), 'body': (-90, 0, 0), 'right_arm': (-90, 12, 0), 'left_arm': (-90, -12, 0),
           'right_leg': (-90, 5, 0), 'left_leg': (-90, -5, 0)}
LIE_OFF = {'head': (0, 20, 0), 'body': (0, 22, 0), 'right_arm': (0, 20, -2), 'left_arm': (0, 20, -2),
           'right_leg': (0, 10, -12), 'left_leg': (0, 10, -12)}
FLAT = {'head': [0, 0, 0], 'body': [0, 0, 0], 'right_arm': [0, 0, 0], 'left_arm': [0, 0, 0], 'right_leg': [0, 0, 0],
        'left_leg': [0, 0, 0]}


def lie_down(a, t=0.0, turn=0.0):
    a.pose(t, 'flat', 1)
    for p in LIE_ROT:
        r = LIE_ROT[p]
        a.limb(p, 'rot', t, (r[0], r[1], turn if p == 'head' else r[2]))
        a.limb(p, 'offset', t, LIE_OFF[p])
    a.d['life'] = False


def scene_hall():
    BG.use('great_hall', GH.TEMPLATE_AT)      # main() switches back after this scene's checks
    rng = Rng(5)
    F = Film('battle_of_hogwarts_hall', 'The Battle of Hogwarts: The Great Hall', 150.0)
    F.set_, F.radius, F.ground = HALL_SET, GH.RADIUS, ('great_hall', GH.TEMPLATE_AT)
    F.poses['flat'] = FLAT
    taken = []

    def spot(box, margin=1.6):
        for _ in range(300):
            x, z = rng.uni(box[0], box[1]), rng.uni(box[2], box[3])
            if walkable(x, z) and all(dist2((x, 0, z), q) > margin for q in taken):
                taken.append((x, 0.0, z))
                return (round(x, 2), 0.0, round(z, 2))
        raise ValueError('no hall spot in %s' % (box,))
    # the wounded along the aisles, people kneeling by them
    aisles = [(6.0, 54.0, -8.6, -7.4), (6.0, 54.0, 7.4, 8.6), (6.0, 54.0, -15.8, -15.2), (6.0, 54.0, 15.2, 15.8)]
    for i in range(16):
        box = aisles[i % 4]
        p = spot(box, margin=2.4)
        a = student(F, 'wounded_%d' % (i + 1), p, rng.pick([90.0, -90.0]), rng)
        lie_down(a, 0.0, rng.uni(-25, 25))
        if i % 2 == 0:
            q = (p[0] + 1.1, 0.0, p[2])
            if walkable(q[0], q[2]):
                k = student(F, 'tending_%d' % (i + 1), q, 90.0, rng)
                k.pose(0.0, 'kneel', 1)
                k.face(0.0, 90.0, 30)
                taken.append(q)
    # the Weasleys round Fred in the centre aisle
    fred = F.actor('fred', 'player', (30.0, 0.0, 0.5), -90.0, skin='ron_dh', scale=0.98)
    lie_down(fred, 0.0, 15)
    molly = principal(F, 'molly', (31.2, 0.0, -0.6), 90.0)
    molly.pose(0.0, 'kneel', 1)
    molly.face(0.0, 60.0, 35)
    ginny = principal(F, 'ginny', (31.0, 0.0, 1.6), 120.0)
    ginny.pose(0.0, 'kneel', 1)
    ginny.face(0.0, 140.0, 30)
    ron = principal(F, 'ron', (29.0, 0.0, 1.6), -120.0)
    ron.face(0.0, -150.0, 30)
    harry = principal(F, 'harry', (61.0, 0.0, 0.5), 90.0)
    hermione = principal(F, 'hermione', (60.6, 0.0, -0.8), 90.0)
    luna = principal(F, 'luna', (12.0, 0.0, -0.4), -90.0)
    taken += [(30, 0, 0.5), (31.2, 0, -0.6), (31, 0, 1.6), (29, 0, 1.6), (61, 0, 0.5), (60.6, 0, -0.8), (12, 0, -0.4)]
    F.sfx(1.0, 'hall_whispers', 0.5)
    harry.walk(8.0, (40.0, 0.0, 0.0), 1.0)
    harry.face(29.0, 90.0, 12)
    hermione.walk(8.6, (42.0, 0.0, -1.2), 1.0)
    # the second assault: Death Eaters pour in through the doors as smoke and the hall fights
    melee = Melee(F, HALL_AREA, rng)
    defenders = []
    for i in range(22):
        a = student(F, 'student_%d' % (i + 1), spot((8.0, 58.0, -16.0, 16.0), 2.0), rng.uni(-120, -60), rng)
        melee.add(a, 0, 33.0, hp=rng.pick([2, 2, 3]), speed=3.4, aggression=rng.uni(0.4, 0.8))
        defenders.append(a)
    for a in (harry, hermione, ron):
        melee.add(a, 0, 34.0, hp=6, speed=3.6, aggression=0.8)
    eaters = []
    for i in range(26):
        land = spot((34.0, 62.0, -16.0, 16.0), 2.0)
        start = (90.0 + rng.uni(0, 20), 6.0 + rng.uni(0, 4), rng.uni(-3, 3))
        e = eater(F, 'death_eater_%d' % (i + 1), start, 90.0, rng)
        t0 = 30.0 + 0.35 * i + rng.uni(0, 0.4)
        t_land = e.smoke_flight(t0, start, land, rng.uni(1.8, 2.6))
        melee.add(e, 1, t_land + 0.3, hp=rng.pick([2, 3]), speed=3.8, aggression=0.75)
        eaters.append(e)
    F.sfx(30.0, 'hall_door_boom', 1.0)
    melee.t_end = 100.0
    melee.run()
    # Bellatrix: through the doors laughing, at the girls by the dais
    bella = principal(F, 'bellatrix', (66.0, 0.0, 0.5), 90.0)
    bella.act(0.0, 'vanish')
    bella.smoke_flight(40.0, (95.0, 8.0, 0.5), (24.0, 0.0, 0.5), 3.0)
    line(F, 44.0, 'bellatrix', 'Oh, look at the little ones. Shall we play?', 'bella_play')
    ginny.pose(36.0, 'standing', 8)
    ginny.walk(36.5, (15.5, 0.0, -0.4), 3.0)
    luna.walk(36.5, (13.8, 0.0, 1.2), 2.0)
    T_DUEL = 46.0
    for a in (ginny, luna):
        a.face_point(T_DUEL - 1.0, T_DUEL - 0.6, bella.chest(T_DUEL))
        a.pose(T_DUEL - 0.8, 'wand_point', 4)
    bella.face(T_DUEL - 0.5, 90.0, 0)
    bella.pose(T_DUEL - 0.6, 'wand_point', 4)
    t = T_DUEL
    for k in range(6):
        who = (ginny, luna)[k % 2]
        who.cast(t, rng.pick(['stupefy', 'expelliarmus', 'petrificus_totalus']), bella.target)
        bella.act(t + 0.25, 'cast', spell='protego')
        bella.cast(t + 0.6, rng.pick(['crucio', 'sectumsempra', 'stupefy']), who.target)
        who.act(t + 0.85, 'cast', spell='protego')
        t += 1.4
    # the curse that nearly kills Ginny, missing by a hand's width
    T_NEAR = t + 0.3
    bella.cast(T_NEAR, 'avada_kedavra', (ginny.chest(T_NEAR)[0] + 0.3, 1.6, ginny.chest(T_NEAR)[2] - 0.7))
    ginny.act(T_NEAR + 0.1, 'sneak')
    ginny.act(T_NEAR + 1.0, 'stand')
    bella.limb('head', 'rot', T_NEAR + 0.4, (0, 0, 0))
    bella.limb('head', 'rot', T_NEAR + 0.8, (-20, 0, 10))
    bella.limb('head', 'rot', T_NEAR + 1.6, (0, 0, 0))
    F.snd(T_NEAR + 0.5, 'minecraft:entity.witch.celebrate', at=bella.chest(T_NEAR), volume=1.4, pitch=1.2)
    # Molly
    T_MOLLY = T_NEAR + 1.6
    molly.pose(T_MOLLY - 1.0, 'standing', 6)
    molly.walk(T_MOLLY - 0.6, (17.5, 0.0, 0.6), 5.0)
    molly.face(T_MOLLY + 1.0, -90.0, 0)
    molly.pose(T_MOLLY + 1.0, 'wand_point', 4)
    line(F, T_MOLLY + 0.4, 'molly', 'NOT MY DAUGHTER, YOU WITCH!', 'molly_daughter')
    for a in (ginny, luna):
        a.walk(T_MOLLY + 1.0, (a.pos_at(T_MOLLY)[0], 0.0, a.pos_at(T_MOLLY)[2] + (2.0 if a is luna else -2.0)), 2.5)
    t = T_MOLLY + 3.2
    for k in range(9):
        molly.cast(t, rng.pick(['stupefy', 'confringo', 'expelliarmus', 'depulso']), bella.target)
        bella.act(t + 0.3, 'cast', spell='protego')
        bella.cast(t + 0.55, rng.pick(['crucio', 'avada_kedavra', 'sectumsempra']), molly.target)
        molly.act(t + 0.8, 'cast', spell='protego')
        if k in (3, 6):
            bella.walk(t + 0.9, (bella.pos_at(t)[0] + 1.2, 0.0, 0.5 + rng.uni(-0.6, 0.6)), 2.5)
        t += 1.05
    line(F, T_MOLLY + 7.0, 'bellatrix', 'Ha! And who will mind your brats when you are dead, hmm?', 'bella_children')
    T_STONE = t + 0.3
    molly.cast(T_STONE, 'petrificus_totalus', bella.target)
    bella.act(T_STONE + 0.35, 'stone')
    bella.d['life'] = True
    bella.pose(T_STONE + 0.3, 'arms_out', 3)
    F.snd(T_STONE + 0.4, 'hogwartsmagic:statue.bind', at=bella.chest(T_STONE), volume=1.6)
    T_SHATTER = T_STONE + 2.2
    molly.cast(T_SHATTER, 'bombarda', bella.target)
    bella.act(T_SHATTER + 0.4, 'vanish')
    F.blast(T_SHATTER + 0.4, bella.chest(T_SHATTER), big=False, rng=rng)
    F.rubble(T_SHATTER + 0.42, bella.chest(T_SHATTER), 14, rng, spread=2.0, fall=0.0,
             block_ids=['minecraft:stone', 'minecraft:andesite', 'minecraft:smooth_stone', 'minecraft:gravel'],
             scale=(0.25, 0.5))
    F.snd(T_SHATTER + 0.45, 'hogwartsmagic:statue.crumble', at=bella.chest(T_SHATTER), volume=2.0)
    molly.pose(T_SHATTER + 1.6, 'standing', 20)
    molly.face(T_SHATTER + 1.6, -90.0, 18)
    # music
    F.music(0.0, 'lake_window', 0.9)
    F.music(28.0, 'chamber_duel', 1.0)
    F.effect(0.0, 'enchanted_ceiling', GH.ANCHORS['ceiling_center'], spread=GH.ANCHORS['ceiling_spread'], duration=1,
             style='storm')
    F.effect(0.0, 'floating_candles', (30.0, 13.0, 0.5), spread=[24.0, 3.0, 13.0], count=180)
    for t0 in (12.0, 60.0):
        F.sfx(t0, 'hall_murmur_b', 0.4)
    # camera
    def shot2(t0, t1, a, b):
        F.shot(t0, a[0], a[1], a[2], cut=t0 > 0)
        F.shot(t1, b[0], b[1], b[2], cut=False)
    shot2(0.0, 6.0, ((62.0, 6.0, 0.5), (20.0, 2.0, 0.5), 74), ((60.0, 5.0, 0.5), (20.0, 1.0, 0.5), 70))          # the hall of the wounded
    shot2(6.0, 12.0, ((52.0, 1.4, 8.0), (30.0, 0.6, 8.0), 60), ((40.0, 1.4, 8.0), (20.0, 0.6, 8.0), 60))        # along the aisle
    shot2(12.0, 18.0, ((33.0, 1.3, -1.8), molly.target, 46), ((33.4, 1.3, -2.2), molly.target, 42))            # Molly over Fred
    shot2(18.0, 24.0, ((44.0, 1.6, 1.5), harry.target, 50), ((43.0, 1.6, 1.6), harry.target, 46))              # Harry sees
    shot2(24.0, 30.0, ((36.0, 1.0, 2.6), (30.0, 0.4, 0.5), 50), ((35.0, 1.0, 2.4), (30.0, 0.4, 0.5), 46))
    shot2(30.0, 35.5, ((50.0, 1.2, 8.0), (64.0, 3.0, 0.5), 80), ((49.0, 1.2, 8.0), (60.0, 2.0, 0.5), 82))      # they burst in
    shot2(35.5, 41.0, ((24.0, 8.0, -8.0), (44.0, 1.0, 0.5), 76), ((26.0, 8.0, -8.0), (44.0, 1.0, 0.5), 74))
    shot2(41.0, 46.0, ((20.0, 1.2, -1.5), bella.target, 54), ((19.0, 1.3, -1.4), bella.target, 50))            # Bellatrix lands
    shot2(46.0, 51.5, ((19.0, 1.4, -8.0), (19.0, 1.4, 0.5), 60), ((18.4, 1.4, -8.0), (18.4, 1.4, 0.5), 60))     # the girls duel her
    shot2(51.5, T_MOLLY + 1.0, ((14.0, 1.4, 1.8), ginny.target, 48), ((14.4, 1.4, 1.6), ginny.target, 44))
    shot2(T_MOLLY + 1.0, T_MOLLY + 6.5, ((19.0, 1.5, -1.4), molly.target, 44), ((19.4, 1.5, -1.0), molly.target, 40))
    shot2(T_MOLLY + 6.5, T_MOLLY + 12.0, ((20.6, 1.4, 5.0), (20.6, 1.4, 0.5), 62), ((21.0, 1.4, 5.0), (21.0, 1.4, 0.5), 62))
    shot2(T_MOLLY + 12.0, T_STONE + 2.0, ((24.0, 1.4, -1.6), bella.target, 46), ((23.6, 1.4, -1.4), bella.target, 42))
    shot2(T_STONE + 2.0, T_SHATTER + 4.0, ((15.0, 1.5, 2.4), (22.0, 1.4, 0.5), 56), ((15.4, 1.5, 2.2), (22.0, 1.0, 0.5), 54))
    F.L = round(T_SHATTER + 9.5, 2)
    shot2(T_SHATTER + 4.0, F.L, ((20.0, 1.5, -1.6), molly.target, 46), ((19.6, 1.5, -1.2), molly.target, 38))
    return F


# ================================================================ 6. the duel
DUEL_Z = 10.5                                  # the duel runs along the open south half, clear of the fountain


def ruins(F, rng, n=46):
    """Rubble heaps, scorched paving and smouldering fires over the courtyard after the night's fighting."""
    placed = []
    for k in range(n * 4):
        if len(placed) >= n:
            break
        x, z = rng.uni(-44, -3), rng.uni(-17, 16)
        if not walkable(x, z) or abs(z - DUEL_Z) < 3.2 and -40 < x < -4:   # keep the duel line clear
            continue
        if any(dist2((x, 0, z), q) < 2.5 for q in placed):
            continue
        placed.append((x, 0, z))
        b = rng.pick(['minecraft:cobblestone', 'minecraft:mossy_cobblestone', 'minecraft:cracked_stone_bricks',
                      'minecraft:stone_bricks', 'minecraft:andesite', 'minecraft:gravel'])
        F.block(0.0, b, (x, 0, z))
        if rng.chance(0.5):
            F.block(0.0, rng.pick(['minecraft:cobblestone_slab', 'minecraft:stone_brick_slab', 'minecraft:cobblestone']),
                    (x + rng.pick((-1, 1)), 0, z))
        if rng.chance(0.18):
            F.block(0.0, 'minecraft:campfire', (x, 0, z + 1.2))
    return placed


def scene_duel():
    rng = Rng(6)
    F = Film('battle_of_hogwarts_duel', 'The Battle of Hogwarts: The Last Duel', 150.0)
    F.tod, F.white = 23300, True
    bridge_gone(F)
    heaps = ruins(F, rng)
    H_DUEL = (-11.0, 0.0, DUEL_Z)
    V_DUEL = (-35.0, 0.0, DUEL_Z)
    MID = ((H_DUEL[0] + V_DUEL[0]) / 2, 1.45, DUEL_Z)
    vold = principal(F, 'voldemort', (-37.0, 0.0, DUEL_Z), -90.0)
    harry = principal(F, 'harry', (8.0, 0.0, BRIDGE_Z), 90.0)
    # the survivors pressed back to the arcades on both sides, a few Death Eaters beside him
    watchers = []
    for i in range(48):
        side = 1 if i % 2 else -1
        for _ in range(80):
            x, z = (rng.uni(-44, -4), rng.uni(14.2, 16.5)) if side > 0 else (rng.uni(-44, -4), rng.uni(-17.0, -9.0))
            if walkable(x, z) and all(dist2((x, 0, z), w.pos_at(0)) > 1.3 for w in watchers) and \
                    all(dist2((x, 0, z), q) > 1.4 for q in heaps):
                break
        if i < 8:
            a = eater(F, 'death_eater_%d' % (i + 1), (round(x, 2), 0.0, round(z, 2)), 0.0, rng)
        elif i == 8:
            a = principal(F, 'ron', (round(x, 2), 0.0, round(z, 2)))
        elif i == 9:
            a = principal(F, 'hermione', (round(x, 2), 0.0, round(z, 2)))
        elif i == 10:
            a = principal(F, 'neville', (round(x, 2), 0.0, round(z, 2)))
        elif i == 11:
            a = principal(F, 'mcgonagall', (round(x, 2), 0.0, round(z, 2)))
        else:
            a = student(F, 'student_%d' % (i + 1), (round(x, 2), 0.0, round(z, 2)), 0.0, rng)
        a.face(0.0, yaw_to(a.pos_at(0), MID), 4)
        watchers.append(a)
    for a in watchers:
        for k in range(4):
            t = 14.0 + 14.0 * k + rng.uni(0, 6)
            a.face(t, yaw_to(a.pos_at(t), (MID[0] + rng.uni(-6, 6), 1.5, MID[2])), rng.uni(-6, 8))
    # Harry walks in from the broken bridge
    harry.walk(4.0, (0.0, 0.0, BRIDGE_Z), 1.3)
    harry.walk(10.5, (-3.0, 0.0, 6.0), 1.3)
    harry.walk(17.0, H_DUEL, 1.3)
    harry.face(23.0, 90.0, 0)
    vold.walk(9.0, V_DUEL, 0.9)
    vold.face(12.0, -90.0, 0)
    t = line(F, 24.0, 'voldemort', 'Harry Potter. The boy who lived. And now you come to me to die.', 'vold_come_to_die')
    t = line(F, t + 1.0, 'harry', "Then let's finish it, Tom. Just you and me.", 'harry_finish')
    # they circle, and exchange: Voldemort's curses, Harry's shield
    T_X = t + 1.5
    for a, p in ((harry, (-12.5, 0.0, DUEL_Z - 2.5)), (vold, (-33.5, 0.0, DUEL_Z + 2.5))):
        a.walk(T_X, p, 1.0)
    tt = T_X + 4.0
    for k in range(5):
        harry.face_point(tt - 0.3, tt, vold.chest(tt))
        vold.face_point(tt - 0.3, tt, harry.chest(tt))
        vold.pose(tt - 0.3, 'wand_point', 3)
        vold.cast(tt, rng.pick(['avada_kedavra', 'crucio', 'sectumsempra']), harry.target)
        harry.act(tt + 0.4, 'cast', spell='protego')
        harry.pose(tt + 0.8, 'wand_point', 3)
        harry.cast(tt + 1.0, rng.pick(['stupefy', 'expelliarmus']), vold.target)
        vold.act(tt + 1.4, 'cast', spell='protego')
        F.blast(tt + 0.8, (harry.chest(tt)[0] - 1.5, 1.0, harry.chest(tt)[2] + rng.uni(-2, 2)), rng=rng)
        tt += 2.4
    for a, p in ((harry, H_DUEL), (vold, V_DUEL)):
        a.walk(tt, p, 1.2)
    # the lock
    T_CAST = tt + 3.0
    T_PUSH = T_CAST + 8.0
    T_WIN = T_PUSH + 1.2
    for a, y in ((harry, 90.0), (vold, -90.0)):
        a.pose(T_CAST - 1.0, 'wand_raised', 6)
        a.pose(T_CAST - 0.15, 'wand_point', 2)
        a.face(T_CAST - 0.15, y, 0)
    line(F, T_CAST - 0.8, 'harry', 'Expelliarmus!', 'harry_expelliarmus')
    line(F, T_CAST + 0.9, 'voldemort', 'Avada Kedavra!', 'vold_avada')

    def lock_point(t):
        if t < T_CAST + 5.0:
            x = MID[0] + 1.2 * math.sin(t * 1.3)
        elif t < T_PUSH:
            x = MID[0] + 2.0 - (t - T_CAST - 5.0) * 0.6
        else:
            f = min(1.0, (t - T_PUSH) / (T_WIN - T_PUSH))
            x = MID[0] + f * f * (V_DUEL[0] - MID[0] + 0.5)
        return (x, MID[1] + 0.2 * math.sin(t * 3.1), MID[2] + 0.3 * math.sin(t * 2.3))
    t = T_CAST
    while t < T_WIN - 0.05:
        harry.cast(round(t, 2), 'expelliarmus', lock_point(t))
        if t < T_PUSH:
            vold.cast(round(t + 0.2, 2), 'avada_kedavra', lock_point(t + 0.2))
        t = round(t + 0.45, 2)
    t = T_CAST + 0.25
    while t < T_WIN:
        lp = lock_point(t)
        F.part(t, 'minecraft:electric_spark', lp, 26, (0.25, 0.25, 0.25))
        F.part(t, 'minecraft:end_rod', lp, 6, (0.2, 0.2, 0.2))
        if round(t * 20) % 4 == 0:
            F.part(t, 'minecraft:flash', lp, 1, (0, 0, 0))
            F.part(t, 'minecraft:lava', lp, 3, (0.2, 0.2, 0.2))
        t = round(t + 0.15, 2)
    for k in range(4):
        F.snd(T_CAST + 0.3 + 2.2 * k, 'minecraft:entity.guardian.attack', at=MID, volume=1.6, pitch=0.6 + 0.05 * k)
        F.snd(T_CAST + 1.4 + 2.2 * k, 'minecraft:block.beacon.ambient', at=MID, volume=2.0, pitch=0.5)
    F.snd(T_PUSH, 'minecraft:item.totem.use', at=MID, volume=0.9, pitch=0.8)
    harry.limb('body', 'rot', T_CAST, (0, 0, 0))
    harry.limb('body', 'rot', T_CAST + 2.0, (10, 0, 0))
    harry.limb('body', 'rot', T_PUSH, (14, 0, 0))
    harry.limb('body', 'rot', T_WIN + 1.0, (0, 0, 0))
    vold.limb('right_arm', 'rot', T_CAST, (0, 0, 0))
    for k in range(int((T_PUSH - T_CAST) / 0.2)):
        vold.limb('right_arm', 'rot', T_CAST + 0.2 * (k + 1), (rng.uni(-4, 4), rng.uni(-3, 3), 0), 'linear')
    vold.limb('body', 'rot', T_CAST, (0, 0, 0))
    vold.limb('body', 'rot', T_PUSH, (4, 0, 0))
    vold.limb('body', 'rot', T_WIN, (-14, 0, 0))
    for w in watchers:
        w.face(T_CAST + rng.uni(0.2, 1.0), yaw_to(w.pos_at(T_CAST), (lock_point(T_CAST)[0], 1.5, DUEL_Z)), 0)
    # the wand flies to Harry; Voldemort greys and flakes away
    vold.act(T_WIN + 0.05, 'hold', item='minecraft:air')
    v_hand = (V_DUEL[0] + 0.5, 1.45, V_DUEL[2] - 0.35)
    h_hand = (H_DUEL[0] - 0.45, 1.3, H_DUEL[2] + 0.35)
    wand = F.actor('elder_wand', 'hogwartsmagic:memory_block', v_hand, 0.0, scale=0.45)
    wand.d['block'] = 'minecraft:end_rod'
    wand.act(0.0, 'vanish')
    wand.act(T_WIN + 0.05, 'appear')
    T_CATCH = T_WIN + 1.0
    for k in range(11):
        f = k / 10
        p = (v_hand[0] + (h_hand[0] - v_hand[0]) * f, v_hand[1] + (h_hand[1] - v_hand[1]) * f + 2.2 * f * (1 - f),
             v_hand[2] + (h_hand[2] - v_hand[2]) * f)
        wand.key(T_WIN + 0.05 + (T_CATCH - T_WIN - 0.05) * f, p, 'spline' if 0 < k < 10 else ('in_out' if k else None))
    wand.look = [(T_WIN + 0.05, 90.0, 0.0, None), (T_CATCH, 90.0, 720.0, 'out')]
    wand.act(T_CATCH, 'vanish')
    harry.limb('left_arm', 'rot', T_CATCH - 0.4, (0, 0, 0))
    harry.limb('left_arm', 'rot', T_CATCH, (-70, 0, -10))
    harry.limb('left_arm', 'rot', T_CATCH + 2.4, (-70, 0, -10))
    harry.limb('left_arm', 'rot', T_CATCH + 3.4, (0, 0, 0))
    harry.act(T_CATCH, 'equip', slot='offhand', item=W['elder'])
    F.snd(T_WIN, 'minecraft:entity.firework_rocket.large_blast', at=V_DUEL, volume=1.4, pitch=0.6)
    F.part(T_WIN, 'minecraft:flash', (V_DUEL[0] + 0.3, 1.4, DUEL_Z), 2, (0, 0, 0))
    F.part(T_WIN, 'minecraft:totem_of_undying', (V_DUEL[0] + 0.3, 1.4, DUEL_Z), 120, (0.4, 0.6, 0.4))
    T_ASH = T_WIN + 2.6
    T_GONE = T_ASH + 4.0
    vold.pose(T_WIN + 0.3, 'arms_out', 12)
    vold.face(T_WIN + 0.3, -90.0, -30)
    vold.act(T_ASH, 'stone')
    vold.face(T_GONE - 0.5, -90.0, -38)
    F.snd(T_ASH, 'minecraft:block.sand.break', at=V_DUEL, volume=1.4, pitch=0.5)
    F.snd(T_ASH + 1.5, 'minecraft:entity.phantom.death', at=V_DUEL, volume=1.2, pitch=0.5)
    t = T_ASH - 0.6
    while t < T_GONE + 3.0:
        f = min(1.0, max(0.0, (t - T_ASH + 0.6) / (T_GONE - T_ASH + 0.6)))
        if t < T_GONE:
            F.part(t, 'minecraft:ash', (V_DUEL[0], 1.0, DUEL_Z), int(30 + 90 * f), (0.35, 0.9, 0.3))
            F.part(t, 'minecraft:white_ash', (V_DUEL[0], 1.2, DUEL_Z), int(20 + 60 * f), (0.4, 0.9, 0.35))
        F.part(t, 'minecraft:ash', (V_DUEL[0] - 0.4 * (t - T_ASH), 2.4 + (t - T_ASH), DUEL_Z), 40, (1.0, 0.8, 1.0))
        t = round(t + 0.2, 2)
    vold.act(T_GONE, 'vanish')
    F.part(T_GONE, 'minecraft:white_ash', (V_DUEL[0], 1.0, DUEL_Z), 400, (0.6, 1.1, 0.6))
    F.part(T_GONE, 'minecraft:ash', (V_DUEL[0], 1.0, DUEL_Z), 400, (0.6, 1.1, 0.6))
    F.snd(T_GONE, 'minecraft:block.sand.fall', at=V_DUEL, volume=1.4, pitch=0.6)
    harry.pose(T_GONE + 1.0, 'standing', 30)
    for w in watchers:
        if w.d['skin']['texture'].startswith('death_eater'):
            tt = T_GONE + rng.uni(0.6, 2.0)
            w.act(tt, 'smoke')
            here = w.pos_at(tt)
            w.hold(tt)
            w.key(tt + 1.8, (here[0] + 40, 40.0, here[2] * 2), 'in')
            w.act(tt + 1.8, 'vanish')
            w.airborne = True
        else:
            tt = T_GONE + rng.uni(1.5, 4.0)
            w.face(tt, yaw_to(w.pos_at(tt), H_DUEL), 0)
            if rng.chance(0.4):
                w.act(tt + 0.3, 'cast', spell='lumos')
    F.sfx(T_GONE + 2.0, 'hall_cheer_small', 0.6)
    for k in range(6):
        F.part(T_GONE + 3.0 + 0.6 * k, 'minecraft:white_ash', (H_DUEL[0], 2.0, DUEL_Z), 50, (2.0, 1.5, 2.0))
    # world
    F.effect(0.0, 'sky', (0, 0, 0), style='overcast')
    F.music(0.0, 'cave_despair', 0.8)
    F.music(T_X - 2.0, 'chamber_duel', 1.0)
    F.sfx(0.0, 'first_task_wind_high', 0.3, loop=True, duration=int((T_GONE + 8.0) * 20))
    # camera
    def s2(t0, t1, a, b):
        F.shot(t0, a[0], a[1], a[2], cut=t0 > 0)
        F.shot(t1, b[0], b[1], b[2], cut=False)
    H, V = harry.target, vold.target
    Z = DUEL_Z
    s2(0.0, 6.0, ((-44.0, 24.0, -14.0), (-20.0, 0.0, 6.0), 72), ((-44.0, 22.0, -12.0), (-18.0, 0.0, 6.0), 68))   # the ruins at grey dawn
    s2(6.0, 11.0, ((-4.0, 1.5, 4.0), H, 52), ((-4.4, 1.5, 4.4), H, 48))                                          # Harry comes off the stub
    s2(11.0, 17.0, ((-31.0, 1.0, Z - 4.0), V, 50), ((-32.0, 1.0, Z - 3.6), V, 46))                               # him, waiting
    s2(17.0, 23.0, ((-23.0, 7.5, Z - 9.5), (-23.0, 1.5, Z), 60), ((-23.0, 7.0, Z - 8.5), (-23.0, 1.5, Z), 58))   # the two of them, the ring
    T_MID = round((23.0 + T_X + 0.5) / 2, 2)
    s2(23.0, T_MID, ((-31.5, 1.6, Z - 2.0), V, 44), ((-31.7, 1.6, Z - 2.3), V, 40))                              # come to die
    s2(T_MID, T_X + 0.5, ((-14.0, 1.6, Z - 1.5), H, 44), ((-14.2, 1.6, Z - 1.8), H, 40))
    s2(T_X + 0.5, T_X + 6.0, ((-23.0, 8.0, Z - 9.0), (-23.0, 1.0, Z), 66), ((-21.0, 8.0, Z - 9.0), (-23.0, 1.0, Z), 66))  # circling
    s2(T_X + 6.0, T_X + 11.5, ((-28.0, 1.2, Z + 5.0), (-20.0, 1.4, Z), 70), ((-26.0, 1.2, Z + 5.0), (-20.0, 1.4, Z), 70))
    s2(T_X + 11.5, T_X + 15.5, ((-23.0, 0.7, Z - 5.0), (-23.0, 1.5, Z), 74), ((-23.0, 0.75, Z - 4.6), (-23.0, 1.5, Z), 72))
    s2(T_X + 15.5, T_CAST + 0.5, ((-17.0, 1.0, Z + 4.0), (-25.0, 1.5, Z), 64), ((-17.6, 1.0, Z + 3.6), (-25.0, 1.5, Z), 62))
    s2(T_CAST + 0.5, T_CAST + 4.0, ((-12.6, 1.65, Z - 1.6), H, 40), ((-12.8, 1.62, Z - 1.9), H, 36))
    s2(T_CAST + 4.0, T_PUSH - 1.0, ((-33.4, 1.65, Z + 1.7), V, 40), ((-33.2, 1.62, Z + 2.0), V, 36))
    s2(T_PUSH - 1.0, T_WIN + 2.0, ((-23.0, 1.4, Z + 4.4), (-23.0, 1.5, Z), 62), ((-23.6, 1.5, Z + 4.8), (-26.0, 1.5, Z), 60))
    s2(T_WIN + 2.0, T_GONE + 1.5, ((-32.0, 1.5, Z + 2.2), (V_DUEL[0], 1.8, Z), 46), ((-32.4, 1.6, Z + 2.6), (V_DUEL[0], 2.6, Z), 44))
    s2(T_GONE + 1.5, T_GONE + 6.0, ((-19.0, 1.9, Z), H, 56), ((-18.0, 1.9, Z), H, 52))
    F.L = round(T_GONE + 10.5, 2)
    s2(T_GONE + 6.0, F.L, ((-12.8, 1.66, Z - 1.8), H, 46), ((-12.2, 1.65, Z - 1.4), H, 30))
    return F


# ================================================================ 7. dawn
def scene_dawn():
    rng = Rng(7)
    F = Film('battle_of_hogwarts_dawn', 'The Battle of Hogwarts: Dawn', 120.0)
    F.tod = 200
    bridge_gone(F)
    ruins(F, rng, n=30)
    harry = principal(F, 'harry', (12.6, 0.0, BRIDGE_Z), -90.0)
    harry.d['items'] = {'mainhand': W['harry'], 'offhand': W['elder']}
    ron = principal(F, 'ron', (-2.0, 0.0, BRIDGE_Z), -90.0)
    her = principal(F, 'hermione', (-3.5, 0.0, BRIDGE_Z), -90.0)
    for k in range(18):
        p = court_spot(rng, F.actors, 0.0, margin=2.0, box=(-44, -6, -17, 16))
        a = student(F, 'survivor_%d' % (k + 1), p, rng.uni(-180, 180), rng)
        if k % 4 == 0:
            a.pose(0.0, 'kneel', 1)
    ron.walk(6.0, (10.8, 0.0, -1.5), 1.4)
    her.walk(6.4, (11.0, 0.0, 0.4), 1.4)
    ron.face(15.4, -60.0, 8)
    her.face(15.4, -120.0, 8)
    t = line(F, 16.0, 'ron', "That's the Elder Wand, mate. Nothing could stand against it. We'd never lose again.", 'ron_elder')
    harry.face(t + 0.2, -90.0, 10)
    t = line(F, t + 1.2, 'harry', "I don't want it. It's done enough.", 'harry_dont_want')
    T_SNAP = t + 1.2
    harry.limb('left_arm', 'rot', T_SNAP - 1.4, (0, 0, 0))
    harry.limb('left_arm', 'rot', T_SNAP - 0.6, (-60, 0, -20))
    harry.limb('right_arm', 'rot', T_SNAP - 1.4, (0, 0, 0))
    harry.limb('right_arm', 'rot', T_SNAP - 0.6, (-60, 0, 20))
    harry.act(T_SNAP - 1.4, 'hold', item='minecraft:air')
    harry.act(T_SNAP, 'equip', slot='offhand', item='minecraft:air')
    harry.limb('left_arm', 'rot', T_SNAP + 0.1, (-50, 0, -30))
    harry.limb('right_arm', 'rot', T_SNAP + 0.1, (-50, 0, 30))
    harry.limb('right_arm', 'rot', T_SNAP + 0.9, (-160, 0, 10))
    harry.limb('right_arm', 'rot', T_SNAP + 1.4, (-40, 0, 0))
    harry.limb('left_arm', 'rot', T_SNAP + 1.4, (0, 0, 0))
    harry.limb('right_arm', 'rot', T_SNAP + 2.6, (0, 0, 0))
    harry.act(T_SNAP + 2.6, 'hold', item=W['harry'])
    F.snd(T_SNAP, 'minecraft:entity.item.break', at=(12.6, 1.3, -0.5), volume=1.6, pitch=0.7)
    F.snd(T_SNAP + 0.05, 'minecraft:block.wood.break', at=(12.6, 1.3, -0.5), volume=1.2, pitch=0.9)
    F.part(T_SNAP, 'minecraft:crit', (12.2, 1.3, -0.5), 30, (0.15, 0.15, 0.15))
    for k, side_z in enumerate((-1.0, 1.0)):
        piece = F.actor('wand_piece_%d' % (k + 1), 'hogwartsmagic:memory_block', (12.4, 1.3, -0.5), 0.0, scale=0.25)
        piece.d['block'] = 'minecraft:end_rod'
        piece.act(0.0, 'vanish')
        piece.act(T_SNAP + 1.0, 'appear')
        s0 = (12.8, 1.9, -0.5)
        piece.key(T_SNAP + 1.0, s0)
        for j in range(1, 13):
            f = j / 12
            piece.key(T_SNAP + 1.0 + 3.0 * f, (s0[0] + 9 * f, s0[1] + 5 * f - 40 * f * f, s0[2] + side_z * 1.5 * f),
                      'spline' if j < 12 else 'in_out')
        piece.look = [(T_SNAP + 1.0, 0.0, 0.0, None), (T_SNAP + 4.0, 300.0 * side_z, 1200.0, 'linear')]
        piece.act(T_SNAP + 4.0, 'vanish')
    her.face(T_SNAP + 3.4, -90.0, 2)
    ron.face(T_SNAP + 3.6, -90.0, 2)
    harry.face(T_SNAP + 3.0, -90.0, -4)
    F.effect(0.0, 'sky', (0, 0, 0), style='dawn')
    F.music(0.6, 'lake_window', 1.0)
    F.music(14.2, 'chamber_reunion', 1.0)
    F.sfx(0.0, 'flying_lawn_air', 0.35, loop=True)
    T_END = T_SNAP + 17.0
    F.narrate(T_END - 6.0, 'All was well.', 5.0, size=2)
    VOICE_LINES.append({'name': 'bh_narr_allwell', 'speaker': 'narrator', 'text': 'All was well.'})
    if clip_seconds('bh_narr_allwell'):
        F.voice(T_END - 6.0, 'bh_narr_allwell')
    # camera
    def s2(t0, t1, a, b):
        F.shot(t0, a[0], a[1], a[2], cut=t0 > 0)
        F.shot(t1, b[0], b[1], b[2], cut=False)
    H = harry.target
    s2(0.0, 6.0, ((60.0, -6.0, 30.0), (0.0, 20.0, -10.0), 66), ((56.0, -5.0, 28.0), (0.0, 22.0, -10.0), 64))   # dawn over the castle, the broken bridge
    s2(6.0, 11.5, ((-24.0, 2.0, 8.0), (-10.0, 1.0, -2.0), 64), ((-22.0, 2.0, 8.0), (-8.0, 1.0, -2.0), 62))      # the courtyard after
    s2(11.5, 17.0, ((24.0, 1.8, 6.0), (12.0, 1.6, -0.5), 52), ((23.0, 1.8, 5.6), (12.0, 1.6, -0.5), 50))        # the three at the edge, from the air
    T_M = round((17.0 + T_SNAP - 1.5) / 2, 2)
    s2(17.0, T_M, ((9.0, 1.6, 0.6), 'actor:ron', 44), ((9.4, 1.6, 0.4), 'actor:ron', 42))
    s2(T_M, T_SNAP - 1.5, ((10.0, 1.6, -1.6), H, 44), ((10.4, 1.6, -1.4), H, 40))
    s2(T_SNAP - 1.5, T_SNAP + 3.5, ((14.6, 1.4, 1.4), (12.6, 1.3, -0.5), 50), ((14.8, 1.4, 1.6), (13.0, 1.3, -0.5), 48))
    s2(T_SNAP + 3.5, T_SNAP + 8.5, ((30.0, -2.0, 14.0), (12.0, 1.0, -0.5), 54), ((30.0, -3.0, 14.0), (12.0, 1.0, -0.5), 52))
    s2(T_SNAP + 8.5, T_SNAP + 13.0, ((7.0, 1.7, -0.5), (60.0, 6.0, -0.5), 60), ((8.0, 1.7, -0.5), (60.0, 6.0, -0.5), 58))
    F.L = round(T_END, 2)
    s2(T_SNAP + 13.0, F.L, ((20.0, 1.4, -0.5), (0.0, 4.0, -0.5), 66), ((60.0, 30.0, 40.0), (0.0, 10.0, -0.5), 72))
    return F


SCENES = [scene_defences, scene_siege, scene_bridge, scene_courtyard, scene_hall, scene_duel, scene_dawn]
CHAIN = ['battle_of_hogwarts', 'battle_of_hogwarts_siege', 'battle_of_hogwarts_bridge',
         'battle_of_hogwarts_courtyard', 'battle_of_hogwarts_hall', 'battle_of_hogwarts_duel',
         'battle_of_hogwarts_dawn']


def main():
    all_problems = []
    for fn in SCENES:
        F = fn()
        i = CHAIN.index(F.id)
        nxt = CHAIN[i + 1] if i + 1 < len(CHAIN) and fn is not SCENES[-1] else None
        s = F.scene(getattr(F, 'set_', CASTLE_SET), getattr(F, 'radius', R), getattr(F, 'tod', 18000), nxt=nxt,
                    white=getattr(F, 'white', False), fog_far=FOG)
        problems, lens = F.check(s)
        BG.use('hogwarts_battle', HB.TEMPLATE_AT)
        txt = F.write(s)
        print('%-30s %5.1fs actors %3d events %4d chars %7d shots %s' % (F.id, F.L, len(s['actors']),
                                                                      len(s['events']), len(txt), lens))
        all_problems += ['%s: %s' % (F.id, p) for p in problems]
    with open(os.path.join(HERE, 'battle_voice_lines.json'), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(VOICE_LINES, f, indent=1)
    print('\n'.join(all_problems[:60]) if all_problems else 'NO PROBLEMS')
    print(len(all_problems), 'problems')


if __name__ == '__main__':
    main()
