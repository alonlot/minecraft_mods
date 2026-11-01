"""The First Task memory's scenes: memories/first_task.json, first_task_arena, first_task_chase and first_task_egg.

    python -I build_scripts/pensieve_scenes/first_task_scene.py           build, check and write the four scenes
    python -I build_scripts/pensieve_scenes/first_task_scene.py --bite    show that every assert bites (writes nothing)

Four scenes, 110 s: the champions' tent (16 s), the arena (31 s), the chase (47 s), the golden egg (16 s). The viewer
is the fourth champion (scale 0.85, Triwizard robes); the Hungarian Horntail is the memory Horntail at scale 1.

SHOT LIST (time in seconds; beats in ticks in TT / TA / TCH / TE)
first_task (tent, white hand-over)
  T1  0.0- 4.6  the dim tent, a slow dolly past the three champions to the viewer on his bench; a thud, the
                lanterns swing, ash sifts down
  T2  4.6- 8.8  his face from low by the flap; the second thud, he looks up at the lantern
  T3  8.8-12.0  over his shoulder at the flap: the cannon beyond the stand, the muffled call; he stands
  T4 12.0-16.0  behind him into the light of the flap; the white swallows him
first_task_arena
  A1  0.0- 3.6  out of the white, a crane over the crags into the packed ravine; the Horntail on her nest
  A2  3.6- 6.0  high over the nest: the golden egg glints on its prize rock among her eggs (seen, occlusion ray)
  A3  6.0-10.0  behind the champion out of the tunnel: she launches off the nest and lands short on her chain
  A4 10.0-12.8  low off her flank: the roar (music hit 9.7 s), the stands fall silent
  A5 12.8-14.9  close on her head: the indrawn breath, the jet bursts out
  A6 14.9-17.7  wide: the fire chases him across the floor (near miss, gasp) as he dives behind the rock
  A7 17.7-20.7  behind the rock beside him: the second breath pours over it
  A8 20.7-23.4  FIRST PERSON: the wand up at the sky, "Accio Firebolt!" in the music's hush
  A9 23.3-26.1  low in front: the Firebolt bursts over the stands' corner (cheer), streaks in over his shoulder
                into his raised hand; he swings it under him
  A10 26.1-27.7 FIRST PERSON: astride, he lifts off as her third breath slams into the rock
  A11 27.7-31.0 low from the north-west: he rockets up out of the fire (the stands erupt)
first_task_chase
  C1  0.0- 3.5  behind him banking round over the arena, the look tipped down onto her: she breathes the scorch
                back along the floor and onto the rock
  C2  3.5- 6.4  low off her flank: her head swings after him as he dives at her
  C3  6.4- 8.3  close on her head: the bite, she springs at him, her snout 1.7 from him, the jaws clap behind his
                broom (gasp)
  C4  8.3-11.3  wide and low: she rears against the chain; it snaps (music onset 156.0 s); the commentator
  C5 11.3-15.0  behind her on the floor as she beats up after him
  C6 15.0-18.7  wide alongside the two of them over the plateau, the castle ahead: she gains on him
  C7 18.7-19.8  the insert: beside her head, her jaws open, the fire bursts onto the great hall roof
  C8 19.8-21.7  over his shoulder looking back: her jaws pouring fire a few blocks off his broom
  C9 21.7-24.3  outside her swoop, 17 from the turret's spire: her tail smashes its cap, the shingles tumble
  C10 24.3-27.0 a static angle north of the gap: he dives through the 4-block slot between the hall wall and the
                turret, her flame licking his broom's tail (gasp), whips past; she is forced up over the turret
  C11 27.0-29.6 behind her as she stoops off the turret after him, her wings filling the frame
  C12 29.6-32.4 alongside, west along the castle's north side, she on his tail
  C13 32.4-35.6 from the gorge's west rim: they dive over the edge into it
  C14 35.6-39.2 low in the gorge: he shoots under the bridge's arch past us; she comes on high at the span
  C15 39.2-42.5 from the gorge's side, cut in just before: she slams into the span (music 185.5 s), it breaks all
                along, she tumbles down into the gorge
  C16 42.5-47.0 alone: he climbs out of the gorge into the open sky and turns home
first_task_egg (wake)
  E1  0.0- 5.0  behind the east stand: he sweeps back over it (cheer) and away over the ravine
  E2  5.0- 7.2  a low crane 27 from the egg: the golden egg glints on its rock (seen, occlusion ray, 1.2% of frame)
  E3  7.2- 9.1  low on the shelf behind the egg: he dives at us straight at the egg on its rock
  E4  9.1-10.6  FIRST PERSON: his hand out, the egg rushing up; it is in his hand (music hit 341 s), the roar
  E5 10.6-12.9  the cutaway: close on the east stand as it erupts, 23 from its front rows; the cannons
  E6 12.9-16.0  wide from the north stand: he flies the egg held high along the cheering south stand; the eyes close
"""
import copy
import hashlib
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import MEMORIES, write_scene, template_grid  # noqa: E402
import scene_engine as E  # noqa: E402
import dragon_engine as D  # noqa: E402
import human_motion as HM  # noqa: E402
import first_task_props as P  # noqa: E402
import flying_scene as F  # noqa: E402
import first_task_kit as K  # noqa: E402
import first_task_tent as FT  # noqa: E402
import first_task_grounds as FG  # noqa: E402
sys.path.insert(0, os.path.join(HERE, '..', 'sounds'))
import first_task_music as M  # noqa: E402  (its measured music hits)

tk, r3, r4, add, sub, lerp, smooth, smoother, yaw_to, unwrap, norm = (F.tk, F.r3, F.r4, F.add, F.sub, F.lerp, F.smooth,
                                                                     F.smoother, F.yaw_to, F.unwrap, F.norm)
Keys, Rig, move, const, ramp, smoothed = F.Keys, F.Rig, F.move, F.const, F.ramp, F.smoothed

CHAMP = 0.85                                     # the viewer: a 14-year-old champion
ROBES = 'hogwartsmagic:triwizard_robes'
WAND = '{viewer_wand}'
FIREBOLT = P.FIREBOLT
EGG = P.EGG
EGG_PROP = 'hogwartsmagic:golden_egg_prop'
DRAGON_SCALE = 1.0
LANTERN = 'minecraft:lantern[hanging=true,waterlogged=false]'
CHAIN = K.CHAIN_BLOCK
SKINS = F.SKINS
PARK = [-102.0, -80.0, 82.0]                     # far under the arena: an actor off stage

# ================================================================ the tent: first_task
LT = 16.0
TT = {'thud1': 2.6, 'thud2': 6.2, 'cannon': 8.8, 'call': 9.2, 'stand': 11.0, 'walk': (12.0, 15.0)}
TENT_CHAMPS = [('champ_a', 'student_hufflepuff_05', 1.0), ('champ_b', 'witch_03', 1.0), ('champ_c', 'wizard_07', 1.0)]


def seated_pose():
    return {'seated': {'right_leg': [-90, 8, 0], 'left_leg': [-90, -8, 0]}, 'standing': {}}


def lantern_swing(pivot, thuds, seed, hang=0.5):
    """A hanging lantern as a pendulum about its pivot, kicked by each thud: (path keys, look keys). The swing is a
    damped sine in two planes (pitch about x, roll about z), the lantern hanging `hang` under the pivot (its own
    chain stub reaches up to it) and tilted with the swing."""
    rnd = random.Random(seed)
    kicks = [(T, rnd.uniform(9.0, 14.0), rnd.uniform(0.0, 2 * math.pi), rnd.uniform(26.0, 30.0)) for T in thuds]

    def angles(t):
        p = r = 0.0
        for T, amp, ph, per in kicks:
            if t < T:
                continue
            u = t - T
            env = amp * math.exp(-u / 46.0) * smooth(u / 3.0)
            s = math.sin(2 * math.pi * u / per)
            p += env * s * math.cos(ph)
            r += env * s * math.sin(ph)
        return p, r

    def centre(t):
        p, r = angles(t)
        pr, rr = math.radians(p), math.radians(r)
        d = [math.sin(rr), -math.cos(rr) * math.cos(pr), -math.cos(rr) * math.sin(pr)]
        return [pivot[i] + hang * d[i] for i in range(3)], p, r
    path, look = Keys(), []
    path.at(0, centre(0)[0])
    look.append({'t': 0, 'yaw': 0.0, 'pitch': 0.0, 'roll': 0.0})
    first = min(T for T, _, _, _ in kicks)
    end = max(T for T, _, _, _ in kicks) + 150
    path.at(first - 1, centre(first - 1)[0])
    look.append({'t': first - 1, 'yaw': 0.0, 'pitch': 0.0, 'roll': 0.0})
    for t in range(first + 1, end + 1, 2):
        c, p, r = centre(t)
        e = 'spline' if t + 2 <= end else None
        path.at(t, c, e)
        k = {'t': t, 'yaw': 0.0, 'pitch': round(p, 2), 'roll': round(r, 2)}
        if e:
            k['ease'] = e
        look.append(k)
    return path, look


def tent_scene(world):
    sc = K.FSc('first_task', 'The Champions\' Tent', LT, world)
    A = FT.ANCHORS
    sc.poses = seated_pose()
    seat = A['viewer_start']['seat']
    seat_root = [seat[0], HM.seated_root_y(seat[1], CHAMP), seat[2]]
    stand_spot = [seat[0], 0.0, seat[2] + HM.stand_ahead(CHAMP)]
    T_STAND = tk(TT['stand'])
    w0, w1 = tk(TT['walk'][0]), tk(TT['walk'][1])
    # ---- the viewer: on the bench, nervous, up on the call and out through the flap into the light
    v = sc.actor({'id': 'viewer', 'type': 'player', 'scale': CHAMP, 'skin': {'viewer': True},
                  'items': {'chest': ROBES, 'mainhand': WAND}})
    path = Keys().at(0, seat_root).hold(T_STAND)
    path.at(T_STAND + 20, stand_spot).hold(w0)
    walk = [(w0 + 14, [0.5, 0.0, stand_spot[2] + 0.9]), (w0 + 28, [0.5, 0.0, 7.6]), (w0 + 42, [0.5, 0.0, 9.2]),
            (w0 + 56, [0.5, 0.0, 10.9]), (sc.L, [0.5, 0.0, 13.6])]
    path.through(walk, settle=False)
    v['path'] = path.k
    v['poses'] = [{'t': 0, 'name': 'seated', 'blend': 1}, {'t': T_STAND + 22, 'name': 'standing', 'blend': 6}]
    v['anims'] = [{'t': 6, 'name': 'NERVOUS', 'blend': 10}, {'t': tk(TT['call']) - 6, 'name': 'IDLE', 'blend': 10},
                  {'t': T_STAND, 'name': 'STAND_UP', 'blend': 4}, {'t': T_STAND + 21, 'name': 'IDLE', 'blend': 6}]
    t1, t2 = tk(TT['thud1']), tk(TT['thud2'])
    v['look'] = [{'t': 0, 'yaw': 0.0, 'pitch': 18.0}, {'t': t1, 'yaw': 0.0, 'pitch': 18.0},
                 {'t': t1 + 6, 'yaw': -14.0, 'pitch': -20.0}, {'t': t1 + 30, 'yaw': -6.0, 'pitch': -6.0},
                 {'t': t1 + 52, 'yaw': 4.0, 'pitch': 16.0}, {'t': t2, 'yaw': 4.0, 'pitch': 14.0},
                 {'t': t2 + 5, 'yaw': 18.0, 'pitch': -38.0}, {'t': t2 + 34, 'yaw': 12.0, 'pitch': -30.0},
                 {'t': tk(TT['cannon']) - 4, 'yaw': 2.0, 'pitch': 6.0}, {'t': tk(TT['cannon']) + 8, 'yaw': 0.0, 'pitch': -4.0},
                 {'t': T_STAND, 'yaw': 0.0, 'pitch': 2.0}, {'t': w1, 'yaw': 0.0, 'pitch': -6.0}]
    sc.seated['viewer'] = [(0, T_STAND + 20)]
    # ---- the other three champions, dim in the back of the tent, never the camera's subject
    for (cid, skin, s), spot in zip(TENT_CHAMPS, A['champions']):
        assert skin in SKINS, skin
        c = {'id': cid, 'type': 'player', 'scale': s, 'skin': {'texture': skin}, 'items': {'chest': ROBES}}
        if spot['pose'] == 'seat':
            st = spot['seat']
            c['path'] = [{'t': 0, 'pos': r4([st[0], HM.seated_root_y(st[1], s), st[2]])}]
            c['poses'] = [{'t': 0, 'name': 'seated', 'blend': 1}]
            sc.seated[cid] = [(0, sc.L)]
            c['look'] = [{'t': 0, 'yaw': st[3], 'pitch': 30.0}, {'t': t1, 'yaw': st[3], 'pitch': 30.0},
                         {'t': t1 + 8, 'yaw': st[3] + 10, 'pitch': -25.0}, {'t': t1 + 40, 'yaw': st[3], 'pitch': 24.0},
                         {'t': t2 + 2, 'yaw': st[3], 'pitch': 24.0}, {'t': t2 + 10, 'yaw': st[3] - 8, 'pitch': -18.0},
                         {'t': t2 + 50, 'yaw': st[3], 'pitch': 26.0}]
            c['anims'] = [{'t': 0, 'name': 'NERVOUS', 'blend': 4}]
        else:
            c['path'] = [{'t': 0, 'pos': r4(spot['pos'])}]
            y = spot['yaw']
            c['look'] = [{'t': 0, 'yaw': y, 'pitch': 6.0}, {'t': t1 + 2, 'yaw': y, 'pitch': 6.0},
                         {'t': t1 + 9, 'yaw': y - 12, 'pitch': -22.0}, {'t': t1 + 44, 'yaw': y, 'pitch': 4.0},
                         {'t': t2 + 1, 'yaw': y, 'pitch': 4.0}, {'t': t2 + 9, 'yaw': y + 10, 'pitch': -16.0},
                         {'t': t2 + 46, 'yaw': y, 'pitch': 8.0}]
            c['anims'] = [{'t': 0, 'name': 'HANDS_CLASPED' if cid == 'champ_c' else 'NERVOUS', 'blend': 4}]
        sc.actor(c)
    # ---- the lanterns, swinging on each thud
    for i, ln in enumerate(A['lanterns']):
        p, lk = lantern_swing(ln['pivot'], [t1, t2], 'lantern%d' % i)
        sc.actor({'id': 'lantern_%d' % i, 'type': 'hogwartsmagic:memory_block', 'block': LANTERN, 'path': p.k,
                  'look': lk})
    # ---- sounds, light and dust
    sc.ev(0, 'effect', 'eyes_open', at=[0, 0, 0], duration=60)
    sc.ev(0, 'effect', 'sky', at=[0, 0, 0], style='overcast')
    fl = A['flap']
    # the veil of daylight hanging in the flap: at 0.5 it vanished against the bright ground outside
    sc.ev(0, 'effect', 'light_rays', at=[0.5, 0.0, 9.0], yaw=180, spread=[2.5, 5.0, 13.0], intensity=1.4, duration=20)
    sc.ev(w0 + 30, 'effect', 'light_rays', at=[0.5, 0.0, 9.0], yaw=180, spread=[2.5, 5.0, 16.0], intensity=2.0,
          duration=40)
    sc.sfx(0, 'first_task_tent_crowd', 0.7, loop=True)
    sc.music(0, 'first_task_tent')
    for T in (t1, t2):
        sc.sfx(T, 'first_task_ground_thud', 1.0 if T == t1 else 1.15)
        for box in A['dust_boxes']:
            c = [(box[0][i] + box[1][i]) / 2 for i in range(3)]
            hs = [(box[1][i] - box[0][i]) / 2 for i in range(3)]
            sc.ev(T + 2, 'particles', 'minecraft:white_ash', at=r3(c), count=70, spread=r3(hs))
            sc.ev(T + 3, 'particles', 'minecraft:ash', at=r3(c), count=30, spread=r3(hs))
    tc = tk(TT['cannon'])
    sc.sfx(tc, 'first_task_cannon', 0.55)
    sc.cannons.append((tc, 'the cannon calls the champion (muffled, beyond the stand)', False))
    sc.sfx(tc + 6, 'flying_crowd_swell', 0.45)
    sc.say(tk(TT['call']), 'first_task_comm_call_muffled')
    tent_camera(sc, seat_root)
    scene = sc.scene({'templates': [{'id': 'hogwartsmagic:first_task_tent', 'at': list(FT.TEMPLATE_AT)}],
                      'time_of_day': FT.TIME_OF_DAY}, FT.RADIUS, nxt='first_task_arena')
    scene = dict(list(scene.items())[:4] + [('handover_colour', 'white')] + list(scene.items())[4:])
    return sc, scene


def tent_camera(sc, seat_root):
    V = K.view(sc)
    eye = lambda t: F.eye_fn(V, 'viewer')(t)
    seye = smoothed(eye, 6.0)
    # T1 0-4.6 the back of the dim tent: a slow dolly down past the champions toward the boy on his bench, the
    # bright flap beyond him; the first thud swings the lanterns and shakes dust out of the canvas
    pf = move([-2.2, 2.35, -7.6], [-1.6, 1.95, -2.6], 0, 92)
    sc.shot(0, 92, pf, lambda t: lerp([0.6, 1.2, 6.5], seye(t), 0.55), ramp(62.0, 56.0, 0, 92), 4)
    # T2 4.6-8.8 his face from low by the flap, a slow push; the second thud: he looks up at the lantern
    pf = move([1.55, 1.0, 7.0], [1.3, 1.02, 6.55], 92, 176)
    sc.shot(92, 176, pf, smoothed(lambda t: add(eye(t), [0.0, -0.08, 0.0]), 5.0), ramp(46.0, 42.0, 92, 176), 6)
    # T3 8.8-12.0 the cannon: over his right shoulder at the flap as the call comes through the canvas; he stands
    pf = move([1.25, 1.22, 3.35], [1.1, 1.32, 3.6], 176, 240)
    sc.shot(176, 240, pf, const([0.4, 1.35, 9.5]), ramp(54.0, 50.0, 176, 240), 6)
    # T4 12.0-16.0 behind him as he walks into the light of the flap; the white swallows him
    pf = move([0.85, 1.2, 2.7], [0.65, 1.3, 7.0], 240, sc.L)
    sc.shot(240, sc.L, pf, move([0.5, 1.4, 12.0], [0.5, 1.6, 16.0], 240, sc.L), ramp(60.0, 66.0, 240, sc.L), 4)
    E_ = lambda aid: (lambda sc_, t: F.eye_fn(sc_, aid)(t))
    sc.framing += [(6, 90, E_('viewer'), 'the boy on his bench (T1)'), (96, 172, E_('viewer'), 'his face (T2)'),
                   (180, 236, lambda sc_, t: [0.5, 1.6, 9.5], 'the flap (T3)'), (244, 296, E_('viewer'), 'the boy (T4)')]


# ================================================================ shared pieces of the grounds scenes
GA = FG.ANCHORS
GROUND_Y = GA['ground_y']
GROUNDS_SET = {'templates': [{'id': 'hogwartsmagic:first_task_grounds', 'at': list(FG.TEMPLATE_AT)}],
               'schematics': [dict(FG.SCHEMATIC, id='mini_hogwarts')], 'time_of_day': FG.TIME_OF_DAY,
               'build_ticks': 90}
ARENA_AT = [-102.0, -6.0, 82.0]                  # the point the crowd faces: over the middle of the floor
STAKE = GA['stake']
PERCH = GA['nest_perch']['pos']
PERCH_AT = [PERCH[0], PERCH[1], PERCH[2] - 0.2]        # her feet on the shelf, a little north of its middle
PERCH_YAW = 88
LAND_AT = [-111.0, GROUND_Y, 82.5]               # where her lunge out of the nest brings her up, on the chain's end
LAND_YAW = 82.0
HIDE = GA['hide_rock']
FACE_BOX = [list(HIDE['face_box'][0]), list(HIDE['face_box'][1])]
FACE_AIM = [HIDE['face_centre'][0] + 0.3, HIDE['face_centre'][1] + 0.2, HIDE['face_centre'][2]]
STOP = [-123.6, GROUND_Y, 84.0]                  # where the champion stops when she rises
CHAIN_N = 18                                     # links of a heavy chain, each CHAIN_SCALE blocks long
CHAIN_SCALE = 1.6
CHAIN_LEN = 30.0
NEAR_LINKS, FAR_EVERY = 3, 6                     # the links keyed every 2 ticks at her collar; the others' cadence
COMMENTATOR = 'wizard_02'
ARENA_LOOK = [-97.0, 6.0, 72.0]                  # the reveal crane's look: all four canopies and the booth
EGG_AT = [GA['egg_spot'][0], GA['egg_spot'][1] - 0.18 + 0.18 * CHAMP, GA['egg_spot'][2]]   # on its rock


def catmull(pts):
    """A centripetal Catmull-Rom curve through pts (Barry-Goldman, alpha 0.5: no hooks or loops where the spacing is
    uneven): fn(u) for u in [0, len(pts) - 1], u = i at pts[i]."""
    n = len(pts) - 1
    ext = [[2 * pts[0][k] - pts[1][k] for k in range(3)]] + [list(p) for p in pts] +           [[2 * pts[-1][k] - pts[-2][k] for k in range(3)]]

    def knot(ti, a, b):
        return ti + max(1e-6, math.dist(a, b)) ** 0.5

    def f(u):
        u = max(0.0, min(float(n), u))
        i = min(int(u), n - 1)
        s = u - i
        p0, p1, p2, p3 = ext[i], ext[i + 1], ext[i + 2], ext[i + 3]
        t0 = 0.0
        t1 = knot(t0, p0, p1)
        t2 = knot(t1, p1, p2)
        t3 = knot(t2, p2, p3)
        t = t1 + (t2 - t1) * s

        def mix(a, b, ta, tb):
            return [(tb - t) / (tb - ta) * a[k] + (t - ta) / (tb - ta) * b[k] for k in range(3)]
        a1, a2, a3 = mix(p0, p1, t0, t1), mix(p1, p2, t1, t2), mix(p2, p3, t2, t3)
        b1, b2 = mix(a1, a2, t0, t2), mix(a2, a3, t1, t3)
        return mix(b1, b2, t1, t2)
    return f, n


def by_length(pts, per=60, knots=False):
    """A curve through pts, sampled by arc length: (fn(d) -> point, total length[, the arc length at each point])."""
    f, n = catmull(pts)
    us = [i / float(per) for i in range(n * per + 1)]
    ps = [f(u) for u in us]
    s = [0.0]
    for a, b in zip(ps, ps[1:]):
        s.append(s[-1] + math.dist(a, b))

    def at(d):
        d = max(0.0, min(s[-1], d))
        lo, hi = 0, len(s) - 1
        while hi - lo > 1:
            m = (lo + hi) // 2
            if s[m] <= d:
                lo = m
            else:
                hi = m
        f_ = 0.0 if s[hi] == s[lo] else (d - s[lo]) / (s[hi] - s[lo])
        return lerp(ps[lo], ps[hi], f_)
    if knots:
        return at, s[-1], [s[i * per] for i in range(n + 1)]
    return at, s[-1]


def eased_run(length, t0, t1, a_in, a_out, v_in=0.0, v_out=0.0):
    """d(t): the distance along a run of `length` from t0 to t1 that starts at v_in and ends at v_out (blocks a tick),
    easing in over a_in ticks and out over a_out (smoothstep ramps, the cruise speed solved so it covers the length)."""
    T = float(t1 - t0)

    def shape(u, vc):
        v = vc
        if a_in > 0:
            v = v_in + (vc - v_in) * smooth(u / a_in)
        if a_out > 0:
            v = v + (v_out - v) * smooth((u - (T - a_out)) / a_out)
        return v

    def integral(vc, u1, n=400):
        h = u1 / n
        return sum(shape((k + 0.5) * h, vc) for k in range(n)) * h
    lo, hi = 0.0, 10.0
    for _ in range(60):
        m = (lo + hi) / 2
        if integral(m, T) < length:
            lo = m
        else:
            hi = m
    vc = (lo + hi) / 2
    cache = {}

    def d(t):
        u = max(0.0, min(T, t - t0))
        key = round(u, 4)
        if key not in cache:
            cache[key] = integral(vc, u, max(8, int(u * 4)))
        return cache[key]
    return d, vc


def stands_crowd(sc):
    """A triwizard crowd in every seat of the four stands, one event a stand, and their reaction boxes."""
    for st in GA['stands']:
        e = K.crowd_event(st, ARENA_AT)
        sc.ev(0, 'effect', 'crowd', at=e['at'], rows=e['rows'], style='triwizard')
        sc.crowd_boxes[st['name']] = K.stand_box(e)


def crowd_all(sc, t, style, cause, duration=None, order=('north', 'east', 'south', 'west'), stagger=0):
    for i, st in enumerate(order):
        K.react(sc, t + i * stagger, st, style, cause, duration)


def commentator(sc, lines):
    """The commentator in his booth at the top of the north stand, talking through each of his lines."""
    cb = GA['commentator_box']
    p = cb['stand']
    a = sc.actor({'id': 'commentator', 'type': 'player', 'skin': {'texture': COMMENTATOR},
                  'path': [{'t': 0, 'pos': r4([p[0], p[1], p[2]])}]})
    look = [{'t': 0, 'yaw': cb['yaw'], 'pitch': 22.0}]
    anims = []
    for t, line in lines:
        end = t + tk(K.clip_secs(line))
        anims += [{'t': t, 'name': 'TALK', 'blend': 6}, {'t': end, 'name': 'IDLE', 'blend': 8}]
    a['anims'] = anims
    a['look'] = look
    return a


def grounds_world():
    """The grounds as the checks read them: the template's cells (full states) and the Mini Hogwarts (ids)."""
    w = {}
    FG.build(w)
    w.update(FG.CASTLE)
    return w


def chain_curve(stake, neck, length, solid, n=CHAIN_N, side=(0.0, 0.0, 1.0)):
    """The chain's n links from the stake to her collar: a quadratic curve that bows out to `side` and sags with
    its slack, held up off the ground (its link centres at least 0.12 over the blocks under them). [(centre,
    unit along)]."""
    span = math.dist(stake, neck)
    slack = max(0.0, length - span)
    mid = lerp(stake, neck, 0.5)
    c = [mid[0] + side[0] * min(5.0, slack * 0.45), mid[1] - min(7.0, slack * 0.5 + span * 0.04),
         mid[2] + side[2] * min(5.0, slack * 0.45)]
    pts = []
    for k in range(n + 1):
        u = k / float(n)
        p = [(1 - u) ** 2 * stake[i] + 2 * u * (1 - u) * c[i] + u * u * neck[i] for i in range(3)]
        top = K.ground_top(solid, p[0], p[2], p[1] + 0.6)
        p[1] = max(p[1], top + 0.12)
        pts.append(p)
    out = []
    for k in range(n):
        a, b = pts[k], pts[k + 1]
        out.append((lerp(a, b, 0.5), norm(sub(b, a))))
    return out, pts


class Chain:
    """The memory_block links of her chain: their keys built tick by tick (from chain_curve while she is chained,
    then the snap: each link flung back toward the stake and up, falling to lie on the ground)."""

    def __init__(self, sc, prefix, n=CHAIN_N):
        self.sc, self.n = sc, n
        self.ids = ['%s_%02d' % (prefix, k) for k in range(n)]
        self.paths = [Keys() for _ in range(n)]
        self.looks = [[] for _ in range(n)]
        self.last = [None] * n

    def put(self, t, links, ease='spline', force=False):
        """Key the links at t: the NEAR links at her collar every call (they follow her neck), the rest every
        FAR_EVERY ticks, each only when it has moved (0.04 or 2 degrees) since its last key; a link that has held
        still gets a resting key FAR_EVERY ticks before it moves again, so its spline does not drift."""
        for k, (p, d) in enumerate(links):
            near = k >= self.n - NEAR_LINKS
            if not force and not near and t % FAR_EVERY:
                continue
            yw, pi = K.link_look(d)
            if not force and self.last[k] is not None and math.dist(self.last[k][0], p) < 0.04 and \
                    abs(self.last[k][1] - yw) < 2.0 and abs(self.last[k][2] - pi) < 2.0:
                continue
            gap = 2 if near else FAR_EVERY
            if self.paths[k].k and t - self.paths[k].k[-1]['t'] > gap + 0.5 and self.last[k] is not None:
                lt = t - gap
                if lt > self.paths[k].k[-1]['t'] + 1e-9:
                    self.paths[k].at(lt, self.last[k][0])
                    self.looks[k].append({'t': lt, 'yaw': round(self.last[k][1], 2), 'pitch': round(self.last[k][2], 2)})
            prev_yaw = self.looks[k][-1]['yaw'] if self.looks[k] else yw
            yw = unwrap(yw, prev_yaw)
            self.paths[k].at(t, p, ease if self.paths[k].k else None)
            lk = {'t': t, 'yaw': round(yw, 2), 'pitch': round(pi, 2)}
            if ease and ease != 'in_out' and len(self.looks[k]):
                lk['ease'] = ease
            if self.looks[k] and abs(self.looks[k][-1]['t'] - t) < 1e-9:
                self.looks[k][-1] = lk
            else:
                self.looks[k].append(lk)
            self.last[k] = (p, yw, pi)

    def emit(self):
        for k, aid in enumerate(self.ids):
            path = self.paths[k].k
            look = self.looks[k]
            if path and 'ease' in path[-1] and path[-1]['ease'] == 'spline':
                path[-1].pop('ease')
            if look and look[-1].get('ease') == 'spline':
                look[-1].pop('ease')
            self.sc.actor({'id': aid, 'type': 'hogwartsmagic:memory_block', 'block': CHAIN, 'scale': CHAIN_SCALE,
                           'path': path, 'look': look})


# ================================================================ the arena: first_task_arena
LA_ = 31.0
M_ARENA = 6                                      # the arena cue's first tick: its file's hits at 1.0 s (the reveal),
#                                                 9.7 (the roar), 21.5-22.9 a hush (the Accio), 23.1 (the Firebolt
#                                                 over the stands), 25.2 (the catch) and 27.4 (the burst)
TA = {'walk': (96, 152), 'takeoff': 136, 'land_cue': 176, 'landed': 198, 'roar': 200, 'windup1': 258,
      'run': (282, 302), 'windup2': 338, 'raise': 396, 'accio': 436, 'broom_go': 418, 'pass': 468, 'stand': 482,
      'windup3': 494, 'catch': 510, 'mount': 522, 'burst': 554}
SUMMON_LOOK = ([-100.0, 4.0, 72.0], [-78.0, 32.0, 56.0], 414)   # his eyes on the summon: from, to, and when
BREATH1 = 20                                     # the first breath: the chase across the floor, out as he ducks
NEAR_MISS = 4.8                                  # its centre line this far behind him (north) as he runs
HOP_TOP, HOP_UP, HOP_DROP = -2.6, 16, 38           # her lunge: its top, and the ticks to it and to the pounce
CATCH_YAW = 50.0                                 # he turns from her to meet it: it comes over his shoulder from behind
HOLD = 3                                         # ticks he holds it up in his hand before he swings it under him
A9_CAM = (3.2, 1.5, 0.9)                         # the catch shot: forward, left and up of him
CATCH_STEP = 3.0                                 # the steps he takes away from the rock to catch it
BURST_ROUTE = [[-131.5, 6.0, 92.5], [-131.0, 11.0, 82.0], [-123.0, 15.5, 74.5]]


def hop_path(d, t0, t1, a, b, top, t_top, drop_at, v_launch=0.4):
    """Her lunge out of the nest: a spring off the perch (v_launch blocks a tick up: the named launch), up to `top`
    by t_top, high over the eggs, the prize rock and the west boulders, and from drop_at the pounce down onto the
    floor at b, coming to rest: horizontally a smootherstep, vertically three Hermite pieces; keyed every 2 ticks."""
    up = F.hermite([0, a[1], 0], [0, v_launch, 0], [0, top, 0], [0, 0.0, 0], t0, t_top)
    glide = F.hermite([0, top, 0], [0, 0.0, 0], [0, top - 0.4, 0], [0, -0.08, 0], t_top, drop_at)
    pounce = F.hermite([0, top - 0.4, 0], [0, -0.08, 0], [0, b[1], 0], [0, 0, 0], drop_at, t1)

    def f(t):
        u = (t - t0) / float(t1 - t0)
        p = lerp(a, b, smoother(u))
        p[1] = (up if t <= t_top else glide if t <= drop_at else pounce)(t)[1]
        return p
    d.path.hold(t0)
    k = d.path
    k.at(t0 + 1, f(t0 + 1), 'linear')
    k.run(t0 + 1, t1, f, 2, settle=False)
    k.at(t1 + 2, f(t1))


def head_track(sc, d, keys, aim_fn, t0, t1, step, coarse=True):
    """Head look keys from t0 to t1 every `step` ticks, each solved on the mirror to point her head at aim_fn(t)
    (a coarse grid near the last key, then 1 degree); returns the worst aim error."""
    V = K.view(sc)
    worst = 0.0
    prev = tuple(d.head[-1]['v'][:2])
    for t in range(t0, t1 + 1, step):
        before = list(d.head)
        best = (1e9, prev)
        span = 24 if coarse else 10
        for pitch in range(int(prev[0]) - span, int(prev[0]) + span + 1, 3):
            for yaw in range(int(prev[1]) - span, int(prev[1]) + span + 1, 3):
                p_, y_ = max(-40, min(40, pitch)), max(-60, min(60, yaw))
                d.a['limbs'] = {D.HEAD_LOOK: {'rot': before + [{'t': t, 'v': [p_, y_, 0]}]}}
                e = K.aim_error(V, d.id, t, aim_fn(t))
                if e < best[0]:
                    best = (e, (p_, y_))
        c = best[1]
        for pitch in range(c[0] - 2, c[0] + 3):
            for yaw in range(c[1] - 2, c[1] + 3):
                p_, y_ = max(-40, min(40, pitch)), max(-60, min(60, yaw))
                d.a['limbs'] = {D.HEAD_LOOK: {'rot': before + [{'t': t, 'v': [p_, y_, 0]}]}}
                e = K.aim_error(V, d.id, t, aim_fn(t))
                if e < best[0]:
                    best = (e, (p_, y_))
        d.head = before
        d.look_head(t, best[1][0], best[1][1])
        prev = best[1]
        worst = max(worst, best[0])
    return worst


def arena_scene(world, solid, tent=None):
    sc = K.FSc('first_task_arena', 'The First Task', LA_, world)
    sc.poses = {'standing': {}}
    T = dict(TA)
    sc.notes['T'] = T
    stands_crowd(sc)
    sc.notes['solid'] = solid
    sc.notes['egg_at'] = EGG_AT
    sc.actor({'id': 'golden_egg', 'type': EGG, 'scale': CHAMP, 'path': [{'t': 0, 'pos': r4(EGG_AT)}]})
    V = K.view(sc)
    # ---- the champion: out of the tunnel, frozen by her lunge and roar, the run for the rock, the summon
    v = sc.actor({'id': 'viewer', 'type': 'player', 'scale': CHAMP, 'skin': {'viewer': True},
                  'items': {'chest': ROBES, 'mainhand': WAND}})
    hs = HIDE['spot']
    back = [STOP[0] - 0.55, GROUND_Y, STOP[2] - 0.1]
    w0, w1 = T['walk']
    path = Keys().at(0, [-133.5, GROUND_Y, 82.5]).hold(w0)
    path.through([(w0 + 18, [-130.0, GROUND_Y, 82.8]), (w0 + 36, [-126.4, GROUND_Y, 83.5]), (w1, STOP)])
    path.hold(T['landed'] - 4).at(T['landed'] + 8, back)
    r0, r1 = T['run']
    path.hold(r0)
    path.through([(r0 + 8, [back[0] + 0.1, GROUND_Y, back[2] + 1.9]), (r0 + 16, [hs[0] - 0.7, GROUND_Y, hs[2] - 1.9]),
                  (r1, hs)])
    v['path'] = path.k
    fwd = E.fwd_vec(CATCH_YAW)
    catch_spot = [hs[0] + fwd[0] * CATCH_STEP, GROUND_Y, hs[2] + fwd[1] * CATCH_STEP]
    path.hold(T['stand'] + 2).at(T['stand'] + 16, catch_spot)
    v['actions'] = [{'t': r1 - 2, 'do': 'sneak'}, {'t': T['stand'], 'do': 'stand'}]
    v['poses'] = [{'t': r1 - 2, 'name': 'cower', 'blend': 5}, {'t': T['raise'], 'name': 'wand_raised', 'blend': 10},
                  {'t': T['catch'] + 2, 'name': 'standing', 'blend': 8}]
    sc.notes['viewer_path'] = path
    # ---- the dragon: perched, her lunge out of the nest to her chain's end, the roar, three breaths
    d = K.Dragon(sc, 'horntail', PERCH_AT, PERCH_YAW, DRAGON_SCALE)
    d.cue(0, 'IDLE')
    d.cue(T['takeoff'], 'TAKEOFF')
    d.cue(T['takeoff'] + 1, 'DIVE', 1)
    d.cue(T['land_cue'], 'LAND', 6)
    d.cue(T['roar'], 'ROAR', 6)
    B = []
    for wk in ('windup1', 'windup2', 'windup3'):
        d.cue(T[wk], 'BREATH_WINDUP', 8)
        d.cue(T[wk] + 22, 'BREATH')
        B.append(T[wk] + 28)
    sc.notes['breaths'] = B
    for b in B:
        sc.dragon_ok.append((b - 28, b + 48, ('head',), 'a ground breath: her jaw low over the floor', 'floor'))
    hop_path(d, T['takeoff'], T['landed'], PERCH_AT, LAND_AT, HOP_TOP, T['takeoff'] + HOP_UP, T['takeoff'] + HOP_DROP)
    d.path.hold(sc.L)
    d.pins += [(0, T['takeoff'], PERCH_YAW), (T['landed'] - 14, sc.L, LAND_YAW)]
    # through the lunge her body stays near level (the climb pitch would stand her on her nose in the pounce)
    d.overrides.append((T['takeoff'] + HOP_UP, T['landed'] + 2,
                        lambda t, auto: (auto[0], auto[1] * 0.35 * (1 - smooth((t - T['landed'] + 8) / 10.0)), auto[2])))
    d.bake()
    sc.dragon_speed_ok.append((T['takeoff'] - 1, T['takeoff'] + 8, 'the launch off her perch'))
    sc.dragon_ok += [(0, T['takeoff'] + 4, ('leg', 'tail', 'body', 'wing'), 'perched: her feet and folded wings on the shelf',
                      'floor'),
                     (T['takeoff'], T['takeoff'] + 16, ('leg', 'tail', 'wing', 'body'), 'the launch off the rock',
                      'floor'),
                     (T['takeoff'], T['takeoff'] + 3, ('wing',), 'the takeoff pose flicks into the dive', 'drawn'),
                     (T['takeoff'] + HOP_DROP, sc.L, ('tail',), 'the pounce: her tail lies back over the west boulders'),
                     (T['landed'] - 10, sc.L, ('leg', 'tail', 'body', 'wing'), 'down on the floor at her chain\'s end',
                      'floor')]
    sc.notes['dragon_ground'] = {'horntail': lambda t, p: K.ground_top(solid, p[0], p[2], p[1] + 0.5)}
    # breathe actions (the first sweeps after the running champion: no target, along her head; the others at the
    # rock face he hides behind)
    d.breathe(B[0], BREATH1)
    d.breathe(B[1], 40, FACE_AIM)
    d.breathe(B[2], 40, FACE_AIM)
    sc.fire_hits += [(B[1], B[1] + 40, 'the rock face he hides behind', FACE_BOX),
                     (B[2], B[2] + 40, 'the rock face as he mounts', FACE_BOX)]
    sc.notes['dragon'] = d
    arena_viewer_look(sc, v, d, T, lambda t: GA['broom_lane'][4])     # provisional: the reach needs his body yaw
    arena_firebolt(sc, v, T)
    arena_viewer_look(sc, v, d, T, lambda t: broom_at(sc, t))
    arena_heads(sc, d, T, B)
    arena_chain(sc, d, solid)
    arena_sound(sc, d, T, B)
    arena_camera(sc, d, T, B)
    arena_reactions(sc, d, T, B, world)
    sc.notes['wingbeats'] = K.voice_wingbeats(sc, 'horntail')
    scene = sc.scene(GROUNDS_SET, FG.RADIUS, nxt='first_task_chase', fog_far=FG.FOG_FAR)
    sc.notes['extra_checks'] = dragon_checks('horntail')
    return sc, scene


def viewer_chest(sc, t):
    V = K.view(sc)
    a = E.actor_by_id(V, 'viewer')
    p = E.rider_root(V, a, t)[0]
    return [p[0], p[1] + 0.65 * 1.8 * CHAMP, p[2]]


def arena_heads(sc, d, T, B):
    """Her head: scanning on the perch, onto the champion as he comes out, forward through the lunge, the roar at
    him, the first breath swept after him as he runs (always a few blocks behind him), onto the rock face, the other
    two breaths at it, the Firebolt's arrival, then up after him."""
    V = K.view(sc)
    for t, p, y in ((30, 6, -12), (60, 2, 14), (90, 8, -4)):
        d.look_head(t, p, y, None)
    d.hold_head(104)
    head_track(sc, d, d.head, lambda t: viewer_chest(sc, t), 110, 134, 8)
    d.look_head(T['takeoff'] + 4, 0, 0, None)
    d.hold_head(T['landed'])
    head_track(sc, d, d.head, lambda t: add(viewer_chest(sc, t), [0.0, 1.2, 0.0]), T['roar'] + 4, T['roar'] + 44, 10)
    d.hold_head(T['windup1'])
    # the first breath: a few blocks behind him as he sprints for the rock; it gutters out as he dives behind it (her
    # ground breath cannot rise over him: the pose holds her head 40 down and the look lifts it only to level)
    lag = lambda t: add(viewer_chest(sc, t), [-0.3, 0.2, -NEAR_MISS])
    head_track(sc, d, d.head, lag, T['windup1'] + 8, B[0] + BREATH1, 3)
    d.hold_head(B[0] + BREATH1 + 10)
    for b in B[1:]:
        w = b - 28
        head_track(sc, d, d.head, lambda t: FACE_AIM, w + 6, b + 39, 6)
        d.hold_head(b + 41)
        if b == B[1]:
            # between the breaths: she watches the rock, swings her head up at the summoned broom
            d.look_head(b + 60, 6, -20, None)
            head_track(sc, d, d.head, lambda t: broom_at(sc, t), T['pass'] - 6, T['pass'] + 18, 8)
    head_track(sc, d, d.head, lambda t: viewer_chest(sc, t), B[2] + 44, sc.L - 4, 8)


def broom_at(sc, t, bid='firebolt'):
    V = sc if isinstance(sc, dict) else K.view(sc)
    return E.track_sample(E.actor_by_id(V, bid)['path'], t)


def shoulder(scene, a, side, t):
    """The arm's pivot (the shoulder) in the world at t, from the posed parts."""
    _, root, yaw, s, piv, rot = F.parts_of(scene, a, t)[side]
    return E.model_to_world(root, yaw, s, *piv)


ARM_LEN = math.sqrt(101.0) / 16.0 * 15.0 / 16.0           # shoulder to hand (the arm box's tip), at scale 1


def summon_start(sc, v, T0):
    """Where the summoned Firebolt is when it starts (T0): the point furthest along the broom lane (the corridor in
    over the north-east corner of the stands) that his own eyes cannot see then (out of his first-person frame, or
    behind the rim and the stands: an occlusion ray), so it never pops into the sky; and the lane points after it
    down to the floor."""
    V = K.view(sc)
    lane = [list(map(float, p)) for p in GA['broom_lane'][:5]]
    eye = F.head_eye(V, E.actor_by_id(V, 'viewer'), T0, 0.1)
    look = lerp(SUMMON_LOOK[0], SUMMON_LOOK[1], smooth((T0 - SUMMON_LOOK[2]) / 40.0))
    best = None
    for i in range(len(lane) - 1):
        for k in range(20):
            q = lerp(lane[i], lane[i + 1], k / 20.0)
            if not E.in_view(eye, look, 72.0, q) or not K.ray_clear(sc.world, eye, q):
                best = (i, q)
    i, q = best
    return [round(c, 3) for c in q], lane[i + 1:]


def arena_firebolt(sc, v, T):
    """The Firebolt: parked out of the world until the summon; then from beyond the north-east stands it streaks in
    over their corner, over the rock and over his left shoulder from behind into his raised left hand (he has turned
    away from her to meet it); he swings on (it already points his way), lifts off behind the rock in his own eyes,
    and bursts up out of the fire and away north over the floor."""
    hs = HIDE['spot']
    T0, TC, TM, tb = T['broom_go'], T['catch'], T['mount'], T['burst']
    fwd = E.fwd_vec(CATCH_YAW)
    fw = [fwd[0], 0.0, fwd[1]]
    right = [-fwd[1], 0.0, fwd[0]]
    left = [-c for c in right]
    behind = [-c for c in fw]
    # his left arm up and out to the left, a little forward: the hand where the broom will stop
    rg = Rig()
    F.refresh(v, rg)
    sh = shoulder(K.view(sc), v, 'left_arm', TC)
    dirn = norm(add(add([0.0, 0.55, 0.0], left, 0.85), fw, 0.3))
    tgt = add(sh, dirn, ARM_LEN * CHAMP)
    res = F.reach(sc, v, rg, 'left_arm', T['stand'] + 6, TC - 6, tgt)
    assert res < 0.06, res
    rg.r('left_arm').to(TC - 6, TC + HOLD, rg.r('left_arm').v())
    F.refresh(v, rg)
    H = F.hand(K.view(sc), v, 'left_arm', TC)
    in_hand = [H[0], H[1] - F.GRIP_BELOW, H[2]]
    start, lane = summon_start(sc, v, T0)
    # from the corner one sweep down over the rock and over his shoulder (no lane point between: each one bends it)
    via = [p for p in lane if p[1] > 20.0] + [add(add(in_hand, behind, 7.0), [0.0, 4.8, 0.0])]
    pts = [start] + via + [in_hand]
    at, length = by_length(pts)
    dist, vc = eased_run(length, T0, TC, 44, 40)
    sc.notes['broom_start'] = start
    sc.notes['broom_speed'] = vc
    b = F.Broom(sc, 'firebolt', PARK, CATCH_YAW, btype=FIREBOLT, roll_keys=False)
    b.path.hold(T0 - 0.25)
    b.path.at(T0, start)                        # in_out: the spline run after it starts from rest
    t = T0 + 2
    while t < TC - 0.5:
        b.path.at(t, at(dist(t)), 'spline')
        t += 2 if t < TC - 26 else 1
    b.path.at(TC, in_hand)
    sc.flier_ok['firebolt'] = [(0, T0 + 1), (TC - 8, TM + 22)]
    bend = min(range(T0, TC), key=lambda t: math.dist(at(dist(t)), GA['broom_lane'][3]))
    sc.speed_ok['firebolt'] = [(T0 - 1, T0 + 1, 'out of the park'), (TC - 1, TM + 1, 'the mount'),
                               (bend - 8, bend + 8, 'it banks hard in over the corner of the stands')]
    sc.notes.setdefault('yaw_ok', {})['firebolt'] = [(T0 - 1, T0 + 1, 'out of the park')]
    sc.notes.setdefault('crab_ok', {})['firebolt'] = [(T0 - 1, T0 + 1, 'out of the park'),
                                                        (TC - 6, TM + 2, 'slowing into his hand and dropping under him')]
    sc.hidden['firebolt'] = [(0, T0)]
    # its look: the steady heading and the climb, level as it comes into his hand
    b.look = [[0, CATCH_YAW, 0.0, None, None]]
    b.hold_look(T0 - 1)
    b.turn(T0, unwrap(yaw_to(via[0][0] - start[0], via[0][2] - start[2]), CATCH_YAW), 0.0)
    last = F.flight_look(b, T0, TC - 10, b.yaw(), step=4)
    b.turn(TC - 4, unwrap(CATCH_YAW, last), 0.0)
    b.turn(TC, unwrap(CATCH_YAW, last), 0.0)
    b.hold_look(TC + HOLD)
    sc.contacts.append(('viewer: the Firebolt into his raised left hand', 'viewer', 'left_arm', TC,
                        ('broom', 'firebolt', [0.0, F.GRIP_BELOW, 0.0]), 'accio'))
    # the mount: it drops under him, level, his way; he is on it from TM
    root = E.sample_path(v['path'], TM - 0.001)
    under = F.broom_under(root, b.yaw(), CHAMP)
    b.path.hold(TC + HOLD)
    b.path.at((TC + HOLD + TM) / 2.0, add(lerp(in_hand, under, 0.55), left, 0.55), 'spline')   # down at his side, clear of his face
    b.path.at(TM, under)
    b.turn(TM, b.yaw(), 0.0)
    rg.r('left_arm').to(TC + HOLD, TM + 2, [0, 0, 0])
    F.refresh(v, rg)
    v['ride'] = {'on': 'firebolt', 'from': TM}
    vp = Keys()
    vp.k = v['path']
    vp.hold(TM - 0.001).at(TM, E.rider_seat(CHAMP))
    v['path'] = vp.k
    # the lift (in his own eyes) and the burst up out of the fire, round to the north over the floor
    up0 = add(under, [0.0, 0.05, 0.0])
    b.path.at(TM + 4, up0)
    top = add(add(under, fw, 1.2), [0.0, 3.0, 0.0])
    route = [top, add(add(top, fw, 3.0), [0.0, 2.5, 0.0])] + BURST_ROUTE
    v_top = 0.18
    lift = F.hermite(up0, [0.0, 0.0, 0.0], top, [c * v_top for c in norm(sub(route[1], top))], TM + 4, tb)
    at, length = by_length(route)
    dist, _ = eased_run(length, tb, sc.L, 26, 0, v_in=v_top, v_out=v_top)
    sc.notes['burst_route'] = (at, dist)
    for t in sorted(set(list(range(TM + 8, sc.L, 4)) + [sc.L])):
        b.path.at(t, lift(t) if t <= tb else at(dist(t)), 'spline')      # the last too: it flies on into the chase
    sc.speed_ok['firebolt'].append((tb - 2, tb + 12, 'the burst up out of the fire'))
    # its look on the lift: the steady heading, the nose held near level while it rises almost straight up (the
    # climb pitch would bury its brush in the floor), easing into the climb pitch through the burst
    prev = b.yaw()
    b.hold_look(TM + 4)
    for t, hd in F.steady_yaws(b.path.k, sorted(set(list(range(TM + 8, sc.L, 4)) + [sc.L])), prev, t_start=TM + 4):
        cl = E.broom_climb(b.path.k, t)
        f = smooth((t - tb) / 24.0)
        g = smooth((t - TM - 10) / 12.0)
        b.turn(t, hd, max(-12.0, cl) * g * (1 - f) + cl * f, 0.0, 'spline')
    b.sync()
    sc.notes['broom_rig'] = rg
    sc.notes['catch'] = {'hand': in_hand, 'under': under, 'spot': [hs[0] + fw[0] * CATCH_STEP, GROUND_Y,
                                                                    hs[2] + fw[2] * CATCH_STEP]}


def arena_viewer_look(sc, v, d, T, broom_fn):
    """His head: on her as he comes out, frozen by the lunge and the roar, over his shoulder as he runs, at the rock
    and his wand behind it, up at the sky where the broom comes from, at it into his hand; then on the broom."""
    V = K.view(sc)
    her = lambda t: K.dragon_eye(V, 'horntail', t)
    look = []
    look = F.world_look(sc, 'viewer', [{'t': 0, 'yaw': -90.0, 'pitch': 0.0}], 104, T['walk'][1], her, step=12)
    look = F.world_look(sc, 'viewer', look, T['walk'][1] + 6, T['run'][0] - 2, her, step=16)
    hs = HIDE['spot']
    look.append({'t': T['run'][0] + 8, 'yaw': round(look[-1]['yaw'] + 30.0, 2), 'pitch': 8.0})
    look.append({'t': T['run'][1] + 2, 'yaw': -90.0, 'pitch': 20.0})
    look.append({'t': T['raise'] - 10, 'yaw': -92.0, 'pitch': 26.0})
    look.append({'t': T['raise'] + 8, 'yaw': -120.0, 'pitch': -20.0})
    ne = [-80.0, 30.0, 55.0]
    look.append({'t': T['accio'] - 10, 'yaw': round(unwrap(yaw_to(ne[0] - hs[0], ne[2] - hs[2]), -120.0), 2), 'pitch': -30.0})
    look = F.world_look(sc, 'viewer', look, T['pass'] - 6, T['stand'] - 2, broom_fn, step=8, max_pitch=50.0)
    cy = unwrap(CATCH_YAW, look[-1]['yaw'])
    look.append({'t': T['stand'] + 14, 'yaw': round(cy, 2), 'pitch': -12.0})
    look.append({'t': T['catch'] - 6, 'yaw': round(cy - 18.0, 2), 'pitch': -30.0})
    look.append({'t': T['catch'] + 2, 'yaw': round(cy - 10.0, 2), 'pitch': -14.0})
    look.append({'t': T['mount'] - 0.001, 'yaw': round(cy, 2), 'pitch': 0.0})
    look.append({'t': T['mount'], 'yaw': 0.0, 'pitch': -4.0})
    look.append({'t': T['burst'], 'yaw': 0.0, 'pitch': -14.0})
    look.append({'t': sc.L, 'yaw': 25.0, 'pitch': 10.0})
    v['look'] = look


def arena_chain(sc, d, solid):
    """Her chain from the stake behind the nest to her collar, every 4 ticks (every 2 through the lunge)."""
    V = K.view(sc)
    ch = Chain(sc, 'chain')
    ts = list(range(0, sc.L + 1, 2))
    for t in ts:
        neck = K.neck_point(V, 'horntail', t)
        links, pts = chain_curve(STAKE, neck, CHAIN_LEN, solid)
        ch.put(t, links)
    ch.emit()
    sc.chain = {'links': ch.ids, 'snap': 10 ** 9, 'end_of': chain_end, 'stake': STAKE}


def chain_end(scene, t):
    """The collar end of the chain from the emitted keys: the last link's centre plus half the spacing to the link
    before it, along its own axis (MemoryBlockRenderer: Ry(-yaw) Rx(pitch) turns the block's y axis)."""
    ids = sorted(a['id'] for a in scene['actors'] if a.get('block') == CHAIN and a['id'].startswith('chain_'))
    a1, a0 = E.actor_by_id(scene, ids[-1]), E.actor_by_id(scene, ids[-2])
    q1, q0 = E.track_sample(a1['path'], t), E.track_sample(a0['path'], t)
    y, pi = E.own_sample(a1, 'look', t)[:2]
    ry, rp = math.radians(y), math.radians(pi)
    dvec = [-math.sin(ry) * math.sin(rp), math.cos(rp), math.cos(ry) * math.sin(rp)]
    return add(q1, dvec, math.dist(q1, q0) / 2.0)


def arena_sound(sc, d, T, B):
    sc.ev(0, 'effect', 'sky', at=r3(GA['nest_centre']), style='overcast')
    sc.music(M_ARENA, 'first_task_arena')
    sc.sfx(0, 'first_task_crowd_bed', 0.55, loop=True, duration=T['roar'] - 4)
    sc.say(24, 'first_task_comm_horntail')
    commentator(sc, [(24, 'first_task_comm_horntail'), (T['pass'] + 8, 'first_task_comm_accio')])
    sc.sfx(T['landed'], 'first_task_ground_thud', 1.0, hit=T['landed'], label='she lands on the floor')
    sc.sfx(T['roar'], 'first_task_horntail_roar', 1.2, hit=T['roar'], label='the roar')
    sc.sfx(T['roar'] + 1, 'first_task_crowd_hush', 0.9)
    crowd_all(sc, T['roar'] + 3, 'sit', ('roar', T['roar']), stagger=1)
    for b in B:
        sc.sfx(b, 'first_task_fire_roar', 1.0, hit=b, label='a breath')
    sc.sfx(B[1] + 6, 'first_task_fire_scorch', 0.9)
    sc.sfx(B[2] + 6, 'first_task_fire_scorch', 0.9)
    sc.say(T['accio'], 'first_task_viewer_accio')
    sc.sfx(T['accio'], 'first_task_accio', 1.0)
    sc.say(T['pass'] + 8, 'first_task_comm_accio')
    sc.ev(T['windup1'] + 2, 'particles', 'minecraft:smoke', at=r3(D.horntail_snout(K.view(sc), 'horntail', T['windup1'] + 2)[0]),
          count=12, spread=[0.2, 0.15, 0.2])


def arena_reactions(sc, d, T, B, world):
    """The crowd's answers, each to its cause: a gasp at the near miss (the jet's closest pass behind him as he
    runs), the bed back under the noise, a cheer from the north and east stands as the Firebolt crosses their corner
    (the others a beat later), and a roar as he bursts up out of the fire; the whooshes where it passes the camera."""
    V = K.view(sc)
    v = E.actor_by_id(V, 'viewer')
    best = (9.0, None)
    for t4 in range(B[0] * 4, (B[0] + BREATH1) * 4 + 1):
        t = t4 / 4.0
        g = K.fire_geometry(V, 'horntail', t, world)
        if g is None:
            continue
        for part, rt, yaw, s_, piv, rot in E.posed_parts(V, v, t):
            for q in E.part_corners(rt, yaw, s_, piv, rot, E.PART_BOX[part]):
                gap = K.fire_gap(g, q)
                if gap < best[0]:
                    best = (gap, t)
    assert best[0] < 3.0, best
    tm = int(math.ceil(best[1]))
    sc.near_misses.append((tm, 'fire', 'the jet %.1f behind him as he runs' % best[0]))
    sc.notes['near_miss'] = best
    sc.sfx(tm + 3, 'flying_crowd_gasp', 0.95)
    crowd_all(sc, tm + 5, 'gasp', ('near miss', tm), stagger=3)
    sc.sfx(tm + 24, 'first_task_crowd_bed', 0.42, loop=True)
    corner = GA['broom_lane'][3]
    tp = min(range(T['broom_go'], T['catch']), key=lambda t: math.dist(broom_at(sc, t), corner))
    for st in ('north', 'east'):
        sc.passes.append((tp, st, 'the Firebolt over the corner of the %s stand' % st))
    sc.notes['pass_tick'] = tp
    sc.sfx(tp + 1, 'flying_crowd_swell', 0.9)
    crowd_all(sc, tp + 2, 'cheer', ('pass', tp), duration=60, order=('north', 'east'), stagger=1)
    crowd_all(sc, tp + 6, 'cheer', ('pass', tp), duration=50, order=('south', 'west'), stagger=2)
    tb = T['burst']
    sc.sfx(tb + 1, 'flying_crowd_erupt', 0.8)
    crowd_all(sc, tb + 2, 'cheer', ('escape', tb), duration=60, stagger=2)
    K.whoosh(sc, 'firebolt', 470, T['catch'] - 4)
    K.whoosh(sc, 'firebolt', tb, sc.L - 4, 0.8)


def arena_camera(sc, d, T, B):
    V = K.view(sc)
    her_eye = lambda t: K.dragon_eye(V, 'horntail', t)
    head = lambda t: K.dragon_head(V, 'horntail', t)
    boy = lambda t: F.eye_fn(V, 'viewer')(t)
    egg = sc.notes['egg_at']
    # A1 0-3.6 the reveal out of the white: a crane down over the south-west crags into the packed ravine, the
    # Horntail on her nest of boulders far below
    av = GA['arena_view']
    pf = move(av['pos'], [-143.0, 64.5, 131.5], 0, 72)
    sc.shot(0, 72, pf, F.turn_look(pf, ARENA_LOOK, [-97.0, -5.0, 80.0], 0, 72, 60.0), ramp(72.0, 64.0, 0, 72), 4)
    # A2 3.6-6.0 high over the nest: the golden egg glints on its rock among her black eggs, her head above it
    pf = move([-106.4, 4.2, 76.2], [-105.6, 3.6, 76.9], 72, 120)
    sc.shot(72, 120, pf, move(add(egg, [0.6, 0.8, 0.0]), add(egg, [1.1, 0.7, 0.1]), 72, 120), ramp(46.0, 42.0, 72, 120), 6)
    # A3 6.0-10.0 out of the tunnel behind the champion into the roar: she launches off her nest at him across the
    # floor and lands short on her chain
    sv = smoothed(boy, 8.0)
    cam = lambda t: add(sv(t), [-2.9, 0.25, 0.9])
    look = lambda t: lerp([-95.0, -5.5, 82.5], [-111.5, -7.0, 82.6], smoother((t - 136) / 64.0))
    sc.shot(120, 200, cam, look, ramp(56.0, 60.0, 120, 200), 4)
    # A4 10.0-12.8 low on the floor off her flank: she rears and roars; the stands fall silent
    pf = move([-121.5, -8.9, 76.8], [-121.0, -8.85, 77.3], 200, 256)
    sc.shot(200, 256, pf, smoothed(lambda t: add(head(t), [0.0, 0.4, 0.0]), 4.0), ramp(50.0, 46.0, 200, 256), 6)
    # A5 12.8-14.9 close on her head from beside the rock: the indrawn breath, smoke, and the jet bursting out
    pf = move([-113.0, -7.7, 88.6], [-113.2, -7.75, 88.9], 256, 298)
    sc.shot(256, 298, pf, smoothed(lambda t: add(head(t), [-0.6, 0.0, 0.0]), 3.0), ramp(42.0, 46.0, 256, 298), 6)
    # A6 14.9-17.7 wide from the north-west: the fire chasing him across the floor as he dives behind the rock
    pf = move([-131.5, -6.0, 74.2], [-131.0, -5.8, 74.8], 298, 354)
    sc.shot(298, 354, pf, move([-119.0, -8.4, 86.0], [-118.0, -8.2, 87.6], 298, 354), ramp(58.0, 56.0, 298, 354), 6)
    # A7 17.7-20.7 behind the rock beside him: the second breath pours over it and licks round its edges
    hs = HIDE['spot']
    pf = move([hs[0] - 2.3, -9.05, hs[2] + 3.1], [hs[0] - 2.1, -9.0, hs[2] + 2.9], 354, 414)
    sc.shot(354, 414, pf, smoothed(lambda t: lerp(boy(t), [-118.0, -7.0, 88.6], 0.35), 5.0), ramp(52.0, 48.0, 354, 414), 6)
    # A8 20.7-23.4 FIRST PERSON: the wand raised at the sky over the north-east stands: "Accio Firebolt!" in the hush
    eye_v = lambda t: F.head_eye(V, E.actor_by_id(V, 'viewer'), t, 0.1)
    aim = lambda t: add(eye_v(t), norm(sub(lerp(SUMMON_LOOK[0], SUMMON_LOOK[1], smooth((t - SUMMON_LOOK[2]) / 40.0)),
                                           eye_v(t))), 4.0)
    F.first_person(sc, V, 'viewer', 414, 466, fwd=0.1, fov=70.0, look_fn=smoothed(aim, 4.0), sigma=2.0)
    # A9 23.3-26.1 low in front of him: the Firebolt bursts over the far corner of the stands, streaks across the
    # floor at us, over the rock and over his shoulder into his raised hand; he swings it under him
    br = lambda t: broom_at(sc, t)
    sb = smoothed(lambda t: br(min(t, T['catch'])), 4.0)
    cs = sc.notes['catch']['spot']
    fwd = E.fwd_vec(CATCH_YAW)
    fw, lf = [fwd[0], 0.0, fwd[1]], [fwd[1], 0.0, -fwd[0]]
    c0 = add(add(add(cs, fw, A9_CAM[0]), lf, A9_CAM[1]), [0.0, A9_CAM[2], 0.0])
    pf = move(c0, add(c0, fw, -0.4), 466, T['mount'])
    sc.shot(466, T['mount'], pf, lambda t: lerp(sb(t), boy(t), smooth((t - 482) / 26.0) * 0.6),
            ramp(64.0, 54.0, 466, T['mount']), 4)
    # A10 26.1-27.7 FIRST PERSON: astride, he lifts off behind the rock as her fire slams into it
    sm_eye = smoothed(lambda t: F.head_eye(V, E.actor_by_id(V, 'viewer'), t, 0.06), 6.0)
    d0 = norm([fwd[0], 0.1, fwd[1]])
    d1 = norm([fwd[0] - fwd[1] * 0.5, 0.35, fwd[1] + fwd[0] * 0.5])
    aim2 = lambda t: add(sm_eye(t), F.slerp_dir(d0, d1, smoother((t - T['mount']) / 40.0)), 6.0)
    F.first_person(sc, V, 'viewer', T['mount'], T['burst'], fwd=0.06, fov=76.0, look_fn=aim2, sigma=6.0, step=2)
    # A11 27.7-31.0 the burst: low from the north-west, the rock in flames, he rockets up out of the fire
    pf = move([-128.5, -6.5, 77.0], [-128.0, -5.9, 76.6], 554, sc.L)
    sv2 = smoothed(lambda t: E.rider_eye(V, E.actor_by_id(V, 'viewer'), t), 6.0)
    sc.shot(554, sc.L, pf, lambda t: lerp([-117.0, -7.5, 88.5], sv2(t), smooth((t - 556) / 40.0) * 0.75),
            ramp(60.0, 66.0, 554, sc.L), 4)
    F_ = lambda fn: (lambda sc_, t: fn(t))
    E_ = lambda aid: (lambda sc_, t: F.eye_fn(sc_, aid)(t))
    D_ = lambda sc_, t: K.dragon_eye(sc_, 'horntail', t)
    sc.framing += [(4, 70, D_, 'the Horntail on her nest (A1)'), (124, 196, E_('viewer'), 'the champion (A3)'),
                   (140, 196, D_, 'her lunge (A3)'), (204, 252, D_, 'her roar (A4)'), (260, 294, lambda sc_, t: K.dragon_head(sc_, 'horntail', t), 'her head (A5)'),
                   (300, 352, E_('viewer'), 'the champion diving for cover (A6)'),
                   (358, 410, E_('viewer'), 'the champion behind the rock (A7)'),
                   (470, 508, lambda sc_, t: broom_at(sc_, t), 'the Firebolt streaking in (A9)'),
                   (500, 512, lambda sc_, t: F.hand(sc_, E.actor_by_id(sc_, 'viewer'), 'left_arm', t), 'his hand (A9)'),
                   (560, sc.L - 4, lambda sc_, t: E.rider_eye(sc_, E.actor_by_id(sc_, 'viewer'), t),
                    'the champion bursting up (A11)')]
    # the egg glint shot and the Firebolt streak, seen through the set (occlusion rays)
    sc.notes['seen'] = [(lambda sc_, t: list(egg), 74, 118, 16, 'the golden egg on its rock (A2)'),
                        (lambda sc_, t: broom_at(sc_, t), 468, 508, 20, 'the Firebolt in its streak (A9)')]


def dragon_checks(aid):
    """The First Task asserts on a scene with her in it, as (name, fn(sc, scene, problems)) for run_checks."""
    def solid_of(sc):
        return sc.notes['solid']
    return [('dragon blocks', lambda sc, s, P_: K.check_dragon_blocks(sc, s, P_, aid, solid_of(sc))),
            ('dragon motion', lambda sc, s, P_: K.check_dragon_motion(sc, s, P_, aid)),
            ('backslide', lambda sc, s, P_: K.check_backslide(sc, s, P_, [aid] + [a['id'] for a in s['actors']
                                                                                 if a['type'] == FIREBOLT])),
            ('wingbeats', lambda sc, s, P_: K.check_wingbeats(sc, s, P_, aid)),
            ('fire', lambda sc, s, P_: K.check_fire(sc, s, P_, aid, sc.world)),
            ('dragon camera', lambda sc, s, P_: K.check_dragon_camera(sc, s, P_, aid)),
            ('fliers', lambda sc, s, P_: K.check_fliers(sc, s, P_, sc.cam_world, [aid])),
            ('smashes', lambda sc, s, P_: K.check_smashes(sc, s, P_, aid)),
            ('chain', lambda sc, s, P_: K.check_chain(sc, s, P_, aid)),
            ('blend depth', lambda sc, s, P_: K.check_blend_depth(sc, s, P_, aid)),
            ('rolls', lambda sc, s, P_: K.check_rolls(sc, s, P_, aid)),
            ('seen', lambda sc, s, P_: [K.check_seen(sc, s, P_, sc.world, *x) for x in sc.notes.get('seen', [])])]


# ================================================================ the chase: first_task_chase
LC_ = 47.0
TCH = {'rebreathe': 0, 'bite': 128, 'roar': 166, 'snap': 200, 'turn': (170, 222), 'takeoff': 226, 'fly': 256,
       'pass': 140, 'arch': 782, 'crash': 793, 'out': 878, 'closed': 420}
PP = [-116.4, -7.0, 81.7]                        # where he flashes past her jaws (solved: her snout 1.7 off him)
RIDER_TO_PP = [[-112.0, 20.0, 66.0], [-97.0, 19.0, 70.5], [-92.5, 14.0, 86.0], [-104.0, 7.5, 97.5],
               [-115.5, 2.0, 96.0], [-117.8, -2.6, 90.3]]
RIDER_OUT = [[-116.6, -3.6, 72.0], [-108.0, 5.0, 63.0], [-95.0, 14.0, 61.0], [-80.0, 26.0, 60.5],
             [-68.0, 36.0, 54.0], [-50.0, 46.0, 36.0], [-18.0, 52.0, 4.0]]
ARCH_Y = 3.0                                     # he flies the arch this high (its crown 12): she hits the span above
ARCH_AT = [GA['bridge']['arch']['x1'] / 2.0 + GA['bridge']['arch']['x2'] / 2.0, ARCH_Y, -20.0]
GORGE_Y = {-62.0: 6.0, -46.0: 3.5, -32.0: 3.0, -8.0: 3.0, 4.0: 2.5, 16.0: 2.0, 28.0: 2.0}   # his height down the gorge
GORGE_IN = [[14.0, 39.0, -97.0], [2.0, 33.0, -93.0], [-2.6, 24.0, -86.0]]     # a wide turn down into the gorge
GORGE_OUT = [[8.0, 6.0, 52.0], [13.5, 15.5, 60.0], [12.5, 24.5, 68.5], [0.0, 31.0, 71.0], [-14.0, 40.0, 67.0],
             [-27.0, 49.0, 64.0]]                # up out of its head and round home toward the arena
# the slalom: off the castle's top he dives down past the round north-east tower and threads the 4-block gap between
# the hall's east wall (x 91, 79 high) and the turret (its cone at x 96-98, 68 high), out past the north-east tower
SLALOM = [[92.6, 64.5, -47.0], [92.8, 61.6, -56.0], [94.0, 60.8, -64.0], [96.6, 60.2, -71.5], [101.0, 57.5, -82.0]]
SLOT_AT = SLALOM[2]
NORTH_Y = 56.0                                   # his height along the castle's north side, level to the gorge's dive
V_ARCH = 1.0                                     # his speed under the arch, where his two runs join
OVER_TURRET = [5.2, 13.5]                        # she is forced up and over the turret: this far east and up off his line
JOIN_LAG = 44                                    # she joins his line where he was this many ticks before her climb ends
HIT_BEFORE = 3.0                                 # she meets the span this far along his line before the arch
HIGH_IN_GORGE = 9.0                              # she stays this high over his line down the gorge (into the span)
CHASE_GAP = 8.0
CLOSE_BLEND = 16                                 # ticks either side of TCH['closed'] her run blends onto his line
SINK_MAX = 0.12                                  # her sink (blocks a tick) outside a named dive: she beats, not glides
DIVE_SINK = 0.9                                  # inside one
BITE_HOP = 2.4
BITE_TICKS = 32
BITE_SNAP = 10                                   # the BITE pose claps its jaw shut (p 0.45-0.57) 9-11 ticks in: the snap
BITE_REACH = 2.0                                 # her snout passes this close to his nearest part at the snap, or closer
LEAP = 5.5                                       # she springs this much higher off the floor as she launches
LEAP_TICKS = 9
LAG_B = 13.5                                     # her lag behind him (ticks) once she has closed on him
FALL_AT = [-3.5, 0.0, -19.5]                     # where she comes to lie, at the foot of the fall column
FALL_TICKS = 32
CRASH_INTO = 3
AIR_BREATH_T = 34                                # an air breath's length: inside AIR_BREATH's fire window
BITE_LIFT = 30.0                                 # degrees her head comes up off the floor as she lands from the bite
DEBRIS_PER_SLICE = 8
C2_CAM = [-107.5, -8.6, 75.0]
C3_CAM = [-122.8, -6.9, 80.0]
C4_CAM = [-127.0, -5.0, 76.5]
C5_CAM = [-129.0, -6.8, 84.5]
C11_CAM = [6.5, -2.0, -2.0]
C12_CAM = [9.0, 2.0, -34.0]
M_CHASE = 0                                      # the chase cue from t 0: first_task_music.HITS lands it


def speed_run(length, t0, t1, v0, v1, ramp=0.3):
    """eased_run with ramps a fraction of the run at each end."""
    n = float(t1 - t0)
    return eased_run(length, t0, t1, ramp * n, ramp * n, v_in=v0, v_out=v1)


RUN_SCALE = 0.05                                 # a corner run may be time-scaled at most this much to fit its ticks


def corner_run(at, s0, s1, t0, t1, v_in, v_out, a_lat=0.042, a_t=0.03, step=0.5):
    """s(t) along a curve (at: arc length -> point) from s0 at t0 to s1 at t1: the speed held under sqrt(a_lat /
    curvature) through every corner (so the turn's acceleration stays under a_lat), braking into and speeding out of
    each corner and ramped from v_in and to v_out under a_t, smoothed over a few blocks, the top speed on the straights solved (bisection) so the run takes exactly
    t1 - t0 ticks. Returns (fn(t) -> s, the top speed)."""
    n = max(2, int((s1 - s0) / step))
    ss = [s0 + (s1 - s0) * i / n for i in range(n + 1)]
    ps = [at(x) for x in ss]
    kap = [0.0] * (n + 1)
    for i in range(1, n):
        a, b, c = ps[i - 1], ps[i], ps[i + 1]
        ab, bc, ca = math.dist(a, b), math.dist(b, c), math.dist(c, a)
        cr = [(b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]),
              (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]),
              (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])]
        area2 = math.sqrt(sum(v * v for v in cr))
        kap[i] = 0.0 if ab * bc * ca < 1e-9 else 2.0 * area2 / (ab * bc * ca)
    w = int(3.0 / step)
    ksm = [max(kap[max(0, i - w):i + w + 1]) for i in range(n + 1)]       # look ahead and behind for each corner

    def profile(vmax):
        v = []
        for i, x in enumerate(ss):
            lim = min(vmax, math.sqrt(a_lat / ksm[i]) if ksm[i] > 1e-6 else vmax)
            lim = min(lim, math.sqrt(v_in * v_in + 2 * a_t * (x - s0)), math.sqrt(v_out * v_out + 2 * a_t * (s1 - x)))
            v.append(max(0.05, lim))
        for i in range(1, n + 1):                             # no harder than a_t into or out of a corner
            v[i] = min(v[i], math.sqrt(v[i - 1] ** 2 + 2 * a_t * (ss[i] - ss[i - 1])))
        for i in range(n - 1, -1, -1):
            v[i] = min(v[i], math.sqrt(v[i + 1] ** 2 + 2 * a_t * (ss[i + 1] - ss[i])))
        g = int(4.0 / step)                                   # smoothed: no step in the speed
        out = []
        for i in range(n + 1):
            lo_, hi_ = max(0, i - g), min(n, i + g)
            out.append(sum(v[lo_:hi_ + 1]) / (hi_ - lo_ + 1))
        out[0], out[-1] = v_in, v_out
        return out

    def times(v):
        ts = [float(t0)]
        for i in range(1, n + 1):
            ts.append(ts[-1] + (ss[i] - ss[i - 1]) * 2.0 / (v[i] + v[i - 1]))
        return ts
    lo, hi = 0.1, 6.0
    for _ in range(50):
        m = (lo + hi) / 2
        if times(profile(m))[-1] > t1:
            lo = m
        else:
            hi = m
    vmax = (lo + hi) / 2
    ts = times(profile(vmax))
    scale = (t1 - t0) / (ts[-1] - t0)              # then scaled to fit: within RUN_SCALE (Task 13 review M12)
    assert abs(scale - 1.0) <= RUN_SCALE, ('the run cannot be flown in its time', ts[-1], t1, scale)
    ts = [t0 + (x - t0) * scale for x in ts]

    def f(t):
        if t <= t0:
            return s0
        if t >= t1:
            return s1
        lo_, hi_ = 0, n
        while hi_ - lo_ > 1:
            m = (lo_ + hi_) // 2
            if ts[m] <= t:
                lo_ = m
            else:
                hi_ = m
        u = (t - ts[lo_]) / (ts[hi_] - ts[lo_])
        return ss[lo_] + (ss[hi_] - ss[lo_]) * u
    f.scale = scale
    return f, vmax


def rider_route(sc, start, v_start):
    """His flight as fn(t): the arena's climb carried on round over the arena and down past her jaws (PP at
    TCH['pass']), up and out over the north-east corner, the castle route over its top, the dive off it into the
    slalom (SLALOM: through the 4-block gap between the hall's east wall and the turret), level along the north side
    (NORTH_Y), down the gorge, under the arch (ARCH_AT at TCH['arch'], ARCH_Y high), out of the gorge's head and home
    toward the arena: one curve through every point (so its direction is continuous), three runs along it joined at
    PP and the arch, each slowing for its corners (corner_run)."""
    gl = [[p[0], GORGE_Y.get(float(p[2]), p[1]), p[2]] for p in (list(map(float, q)) for q in GA['gorge_lane'])]
    cr = [list(map(float, p)) for p in GA['castle_route']]
    cr[0][1] = 53.0                                 # level out over the plateau (no dip for her to glide down)
    north = [[p[0], NORTH_Y, p[2]] for p in cr[10:13]]   # level along the north side, then the dive into the gorge
    lane = cr[:6] + SLALOM + north + GORGE_IN + gl[1:5]
    tail = gl[6:11] + GORGE_OUT
    ahead = add(start, v_start, 6.0)
    pts = [start, ahead] + RIDER_TO_PP + [PP] + RIDER_OUT + lane + [ARCH_AT] + tail
    i_pp, i_arch = 2 + len(RIDER_TO_PP), 3 + len(RIDER_TO_PP) + len(RIDER_OUT) + len(lane)
    i_lane = i_pp + 1 + len(RIDER_OUT)
    assert pts[i_pp] == PP and pts[i_arch] == ARCH_AT
    at, length, ks = by_length(pts, knots=True)
    s_pp, s_arch = ks[i_pp], ks[i_arch]
    v0 = math.sqrt(sum(c * c for c in v_start))
    runs = []
    # (the first run is flown alone - she is on the floor - so it may corner harder than the ones she follows)
    for s_a, s_b, t0, t1, va, vb, al in ((0.0, s_pp, 0, TCH['pass'], v0, 0.9, 0.06),
                                         (s_pp, s_arch, TCH['pass'], TCH['arch'], 0.9, V_ARCH, 0.045),
                                         (s_arch, length, TCH['arch'], sc.L, V_ARCH, 0.8, 0.06)):
        fs, vmax = corner_run(at, s_a, s_b, t0, t1, va, vb, a_lat=al)
        runs.append((t0, t1, fs, vmax))
    sc.notes['rider_speeds'] = [(round(r[3], 3), round(r[2].scale, 3)) for r in runs]

    def s_of(t):
        for t0, t1, fs, vmax in runs:
            if t <= t1 or t1 == sc.L:
                return fs(t)

    def f(t):
        return at(s_of(t))
    i_slot = i_lane + 6 + 2
    assert pts[i_slot] == SLOT_AT
    sc.notes['curve'] = {'at': at, 'length': length, 's_of': s_of, 's_arch': s_arch, 's_pp': s_pp,
                         's_gorge': ks[i_arch - len(GA['gorge_lane'][1:5]) - len(GORGE_IN)],
                         's_top': ks[i_lane + 3], 's_dive_end': ks[i_lane + 5], 's_slot': ks[i_slot],
                         's_slalom': (ks[i_lane + 6], ks[i_lane + 10])}
    return f


def chase_scene(world, solid, arena):
    sc = K.FSc('first_task_chase', 'The Chase', LC_, world)
    sc.poses = {'standing': {}}
    T = dict(TCH)
    sc.notes['T'] = T
    sc.notes['solid'] = solid
    stands_crowd(sc)
    Va = arena
    # ---- where the arena left them
    fb = E.actor_by_id(Va, 'firebolt')
    L0 = Va['length']
    s0, s1 = E.track_sample(fb['path'], L0 - 1), E.track_sample(fb['path'], L0)
    start, v_start = s1, sub(s1, s0)
    ha = E.actor_by_id(Va, 'horntail')
    her0 = D.horntail_frame(Va, 'horntail', L0)
    route = rider_route(sc, start, v_start)
    sc.notes['route'] = route
    # ---- the champion on the Firebolt
    b = F.Broom(sc, 'firebolt', start, E.broom_pose(Va, fb, L0)['yaw'], btype=FIREBOLT, roll_keys=False)
    for t in range(2, sc.L + 1, 2):
        b.path.at(t, route(t), 'spline')
    F.flight_look(b, 0, sc.L, b.yaw(), step=4)
    b.sync()
    v = sc.actor({'id': 'viewer', 'type': 'player', 'scale': CHAMP, 'skin': {'viewer': True},
                  'items': {'chest': ROBES, 'mainhand': WAND}, 'ride': 'firebolt',
                  'path': [{'t': 0, 'pos': r4(E.rider_seat(CHAMP))}], 'look': [{'t': 0, 'yaw': 0.0, 'pitch': 0.0}]})
    # ---- the dragon: on the floor at her chain's end, the scorch breathed back onto the rock, the bite, the strain
    # and the snap, the launch after him, the chase over the castle, the bridge
    d = K.Dragon(sc, 'horntail', her0[0], her0[1], DRAGON_SCALE)
    # the scorch carried over (Task 13 review I8): the first breath's trail across the floor breathed again along
    # its own hits, then the rock face, both in the one BREATH cue's fire window
    sc.notes['arena_scorch'] = breath_hits(Va, 'horntail', world, TA['windup1'] + 28, BREATH1)
    d.cue(0, 'BREATH', 4)
    d.breathe(REBREATHE[0][0], REBREATHE[0][1])
    d.breathe(REBREATHE[1][0], REBREATHE[1][1], FACE_AIM)
    d.cue(48, 'IDLE', 10)
    d.cue(T['bite'], 'BITE', 4)
    d.cue(T['bite'] + BITE_SNAP + 4, 'IDLE', 6)     # the jaws snap shut at +11: her head comes up after him
    d.cue(T['roar'], 'ROAR', 6)
    d.cue(T['takeoff'], 'TAKEOFF')
    d.cue(T['fly'], 'FLY', 8)
    sc.notes['dragon'] = d
    sc.notes['chase_dragon'] = chase_dragon_path(sc, d, route, her0)
    d.bake()
    chase_slate(sc, d, world, solid)
    breaths = chase_breaths(sc, d, world)
    d.bake()
    chase_crash(sc, d, world)
    d.bake()
    t_f = first_contact(K.view(sc), 'horntail', GA['bridge']['break_box'], T['crash'] - 40, T['crash'] + 16)
    assert t_f is not None and abs(t_f - T['crash_tick']) <= 1, ('her baked flight meets the span off the crash tick',
                                                                 t_f, T['crash_tick'])
    chase_heads(sc, d, T, breaths)
    chase_chain(sc, d, solid, arena)
    chase_rider_look(sc, v, T)
    chase_sound(sc, d, T, breaths, world)
    chase_camera(sc, d, T, breaths)
    sc.notes['wingbeats'] = K.voice_wingbeats(sc, 'horntail')
    sc.broom_dragon_ok.append((T['bite'] - 6, T['bite'] + 18, 0.6, 'the bite: he flashes past her jaws'))
    sc.dragon_ok += [(0, T['takeoff'] + 2, ('leg', 'tail', 'body', 'wing'), 'on the floor at her chain\'s end', 'floor'),
                     (0, T['takeoff'] + 30, ('tail',), 'her tail lies back over the west boulders'),
                     (T['takeoff'], T['takeoff'] + 14, ('leg', 'tail', 'wing', 'body'), 'the launch off the floor', 'floor'),
                     (T['takeoff'], T['takeoff'] + 4, ('wing',), 'the takeoff pose flicks her wings into the first beat',
                      'drawn'),
                     (0, 62, ('head',), 'the scorch breathed back: her jaw low over the floor', 'floor'),
                     (100, 165, ('head', 'neck'), 'the bite: her jaws and neck low over the floor after him'),
                     (T['roar'], T['takeoff'], ('wing', 'head', 'neck'),
                      'she rears, roars and strains: a wingtip and her jaw low over the rim boulders')]
    sc.dragon_speed_ok.append((T['takeoff'] - 1, T['takeoff'] + LEAP_TICKS + 8, 'the launch: she springs off the floor'))
    t_hop = T['bite'] + BITE_SNAP - BITE_TICKS // 2
    sc.dragon_speed_ok.append((t_hop - 1, t_hop + BITE_TICKS + 2, 'the bite: she springs off the floor at him'))
    sc.notes['hops'] = [(t_hop, t_hop + BITE_TICKS, BITE_HOP + 0.3, 'the bite: she springs off the floor at him')]
    sc.notes['dragon_ground'] = {'horntail': lambda t, p: K.ground_top(solid, p[0], p[2], p[1] + 0.5)}
    sc.notes['extra_checks'] = dragon_checks('horntail') + chase_checks()
    return sc, d, b, v


def chase_dragon_path(sc, d, route, her0):
    """Her path: on the floor until the launch, turning to face him as she strains; the launch up over the nest onto
    his line behind him; then along his own curve on her own speed run (corner_run: she corners harder than he dares
    and so closes on him by TCH['closed']), then LAG_B ticks behind him on his own line (her head 6-12 off his eye,
    never closer than CHASE_GAP along it), forced up and east over the turret where he threads the gap (OVER_TURRET),
    lifted down the gorge so she meets the bridge's span where he flies under its arch. Outside the named dives
    (sc.notes['dive_ticks']) she sinks at most SINK_MAX, so she beats rather than glides (Task 13 review I3). Keyed
    every 2 ticks as one spline run from the launch."""
    T = sc.notes['T']
    T0 = T['takeoff']
    base = list(her0[0])
    climb_end = T0 + 80
    cv = sc.notes['curve']
    at_c, s_him = cv['at'], cv['s_of']
    s_join = s_him(climb_end - JOIN_LAG)
    join = at_c(s_join)
    v_join = (s_him(climb_end - JOIN_LAG + 1) - s_join)
    up = [[-106.0, -5.0, 79.0], [-97.0, 3.0, 73.0], [-87.0, 13.0, 67.0], join]
    at, length = by_length([base] + up)
    dist, _ = eased_run(length, T0, climb_end, 40, 0, v_in=0.0, v_out=v_join)
    # she closes on him along the straight lane out of the arena (her own run, cornering harder than he does), then
    # holds about LAG_B ticks behind him on his own line (so his corners are hers) to the bridge
    s_hit = cv['s_arch'] - HIT_BEFORE
    TA = T['closed']
    v_a = s_him(TA - LAG_B + 1) - s_him(TA - LAG_B)
    fa, vmax = corner_run(at_c, s_join, s_him(TA - LAG_B), climb_end, TA, v_join, v_a, a_lat=0.065, a_t=0.045)
    lo, hi = 0.0, 60.0
    for _ in range(50):
        m = (lo + hi) / 2
        if s_him(T['crash'] - m) > s_hit:
            lo = m
        else:
            hi = m
    lag_end = (lo + hi) / 2

    def lag(t):
        return LAG_B + (lag_end - LAG_B) * smooth((t - TA) / float(T['crash'] - TA)) if t > TA else LAG_B

    def fs(t):
        # her own run hands over to his line at TA, blended over CLOSE_BLEND ticks so her speed changes smoothly
        if t <= TA - 2 * CLOSE_BLEND:
            return fa(t)
        if t >= TA:
            return s_him(t - lag(t))
        return fa(t) + (s_him(t - lag(t)) - fa(t)) * smoother((t - TA + 2 * CLOSE_BLEND) / (2.0 * CLOSE_BLEND))
    sc.notes['dragon_vmax'] = round(vmax, 3)
    sc.notes['lag_end'] = round(lag_end, 2)
    s_g, s_slot = cv['s_gorge'], cv['s_slot']

    def lift(sv):
        # up and over the turret as he threads the gap (east of it and high), then down the gorge high over his line
        b = smoother((sv - (s_slot - 58.0)) / 50.0) * (1 - smoother((sv - (s_slot + 6.0)) / 44.0))
        return [OVER_TURRET[0] * b, OVER_TURRET[1] * b + HIGH_IN_GORGE * smoother((sv - s_g) / 60.0)]
    # the named dives, where she may sink as fast as she likes (a stoop, wings back): off the castle's top after him
    # (the spire swoop in it), down off the turret, and down the gorge into the span; elsewhere she holds her height
    # over his line (SINK_MAX), beating
    dives = [(cv['s_top'], s_slot - 30.0, 'the dive off the castle top after him'),
             (s_slot + 2.0, s_slot + 56.0, 'the stoop down off the turret'),
             (s_g - 26.0, cv['s_arch'] + 6.0, 'the dive down the gorge into the span')]
    sc.notes['dives_s'] = dives

    def raw(t):
        sv = fs(t)
        p = at_c(sv)
        q = lift(sv)
        return [p[0] + q[0], p[1] + q[1], p[2]]
    t_a, t_b = climb_end - 30, T['crash'] + 20
    ys = {}
    y = raw(t_a)[1]
    for t in range(t_a, t_b + 1):
        sv = fs(t)
        rate = DIVE_SINK if any(a <= sv <= b for a, b, _ in dives) else SINK_MAX
        y = max(raw(t)[1], y - rate)
        ys[t] = y - raw(t)[1]
    sig = 7.0
    ex = {}
    for t in range(t_a, t_b + 1):
        w = [(math.exp(-0.5 * ((u - t) / sig) ** 2), ys[u]) for u in range(max(t_a, t - 21), min(t_b, t + 21) + 1)]
        ex[t] = sum(a * b for a, b in w) / sum(a for a, _ in w)
    sc.notes['held_height'] = round(max(ex.values()), 2)

    def excess(t):
        if t <= t_a or t >= t_b:
            return 0.0
        i = int(math.floor(t))
        return ex[i] + (ex[min(t_b, i + 1)] - ex[i]) * (t - i)

    def chase(t):
        p = raw(t)
        return [p[0], p[1] + excess(t), p[2]]
    over = [t for t in range(t_a, t_b) if lift(fs(t))[0] > 0.25 * OVER_TURRET[0]]
    sc.notes.setdefault('gap_ok', []).append((min(over), max(over), 'she is forced up and over the turret'))
    sc.notes['dive_ticks'] = [(min(t for t in range(t_a, t_b) if a <= fs(t) <= b),
                               max(t for t in range(t_a, t_b) if a <= fs(t) <= b), why) for a, b, why in dives]
    p_end = at(dist(climb_end))
    v_end = sub(p_end, at(dist(climb_end - 1)))

    def climb(t):
        p = at(dist(t)) if t <= climb_end else add(p_end, v_end, t - climb_end)
        return [p[0], p[1] + LEAP * smoother((t - T0) / float(LEAP_TICKS)), p[2]]    # her wings clear the floor

    def f(t):
        if t <= climb_end - 24:
            return climb(t)
        if t >= climb_end + 24:
            return chase(t)
        c = chase(t) if t >= climb_end else at_c(s_join + v_join * (t - climb_end))
        return lerp(climb(t), c, smoother((t - climb_end + 24) / 48.0))
    # the bite: she springs up off the floor at him, BITE_HOP at the top just as her jaws snap (the BITE pose drops
    # her head into its lunge: the spring carries her snout up to him)
    tb = T['bite'] + BITE_SNAP - BITE_TICKS // 2
    d.path.hold(tb)
    d.path.run(tb, tb + BITE_TICKS, lambda t: [base[0], base[1] + BITE_HOP * math.sin(math.pi * (t - tb) / float(BITE_TICKS)) ** 2,
                                               base[2]], 2)
    # through the hop her body stays near level (the climb pitch would put her nose into the floor as she drops)
    d.overrides.append((tb, tb + BITE_TICKS + 4, lambda t, auto: (auto[0], auto[1] * 0.2, auto[2])))
    d.path.hold(T0)
    d.path.at(T0 + 1, f(T0 + 1), 'linear')
    d.path.run(T0 + 1, T['crash'] + 16, f, 2, settle=False)      # on through the span: the crash cuts it
    d.pins.append((0, T['turn'][0], her0[1]))
    face = unwrap(yaw_to(-80.0 - base[0], 60.0 - base[2]), her0[1])
    d.hover.append((T['turn'][0], T0 + 4, face))
    gaps = [s_him(t) - fs(t) for t in range(climb_end, T['crash'] + 1, 4)]
    sc.notes['chase_gap'] = round(min(gaps), 2)
    assert min(gaps) >= CHASE_GAP, ('she would catch him', min(gaps))
    return {'s': fs, 'climb_end': climb_end, 'chase': chase}


REBREATHE = ((4, 20), (26, 20))                  # (tick, ticks): the floor trail again, then the rock face
SCORCH_KEPT = 1.5                                # every arena hit point has a chase hit this close


def breath_hits(scene, aid, world, t0, dur):
    """Where each tick's puff ray meets a block within FIRE_MAX_REACH through the breathe at t0 (its mouth and
    aim from dragon_engine.fire_jet; DragonFireRenderer lays a scorch hit there, timed for the gas to arrive, however
    short the front is yet): the points its scorch is laid down on, in time order."""
    out = []
    for t in range(int(t0), int(t0 + dur) + 1):
        jet = D.fire_jet(scene, aid, t, world)
        if jet is None:
            continue
        o, dd, _ = jet
        ln = D.ray_hit(world, o, dd, D.FIRE_MAX_REACH)[0]
        if ln < D.FIRE_MAX_REACH - 1e-6:
            p = [o[i] + dd[i] * ln for i in range(3)]
            if not out or math.dist(out[-1], p) > 0.4:
                out.append(p)
    return out


def check_scorch_kept(sc, scene, problems):
    """Task 13 review I8: the arena's first breath left its scorch along the floor; the chase opens on the same
    floor, so a chase breathe (the first 60 ticks) must lay a hit within SCORCH_KEPT of every one of the arena's hit
    points. Returns the worst."""
    a = E.actor_by_id(scene, 'horntail')
    ours = []
    for t0, dur, _ in D.breathes(a):
        if t0 < 60:
            ours += breath_hits(scene, 'horntail', sc.world, t0, dur)
    worst = 0.0
    for p in sc.notes['arena_scorch']:
        g = min([math.dist(p, q) for q in ours] or [99.0])
        worst = max(worst, g)
    if worst > SCORCH_KEPT:
        problems.append('%s: an arena scorch point is %.2f from every chase hit (the floor trail is gone)' % (
            sc.sid, worst))
    return round(worst, 2), len(sc.notes['arena_scorch'])


def first_contact(scene, aid, box, t0, t1, parts=None):
    """The first tick from t0 to t1 at which any of her capsules (of the given groups) meets the box, or None."""
    lo, hi = box
    for t in range(t0, t1 + 1):
        for name, (a, b, r) in D.horntail_parts(scene, aid, t):
            if parts is not None and K.part_group(name) not in parts:
                continue
            if K.seg_box_distance(a, b, lo, hi) - r <= 0.0:
                return t
    return None


SLATE_W = 80                                     # the swoop's length (ticks): down onto the roof and up again
SLATE_DEBRIS = 12
SPIRE = (66, 2)                                  # the north-west turret's spire, on her line over the castle
SPIRE_FROM_Y = 82                                # its shingled cap from here up to the flagpole
SLATE_REAR = 22                                  # ticks either side of the swoop's bottom she rears through
SLATE_PITCH = 38.0


def chase_slate(sc, d, world, solid):
    """Her tail through the shingled cap of the north-west turret's spire (SPIRE: the grounds' slate_roofs lie 12-21
    off her line, so she smashes the roof that is on it): a swoop of her own line down onto it (a cos^2 bump of SLATE_W ticks toward it, her wings folding into TAIL_WHIP at
    its bottom so her tail lashes through it), solved over the swoop's centre and depth for the first tail contact
    with the cap while nothing else of her comes within 0.85 of a block. The cap's cells go to air on that tick, its
    shingles tumble off the turret, first_task_slate_smash. Returns the smash tick."""
    T = sc.notes['T']
    cs = sorted(c for c in world if abs(c[0] - SPIRE[0]) <= 2 and abs(c[2] - SPIRE[1]) <= 2 and c[1] >= SPIRE_FROM_Y
                and c[1] < SPIRE_FROM_Y + 12 and not world[c].startswith('minecraft:air'))
    assert len(cs) >= 8, cs
    lo = [float(min(c[i] for c in cs)) for i in range(3)]
    hi = [float(max(c[i] for c in cs) + 1) for i in range(3)]
    q = [(lo[i] + hi[i]) / 2.0 for i in range(3)]
    keys0 = [dict(k) for k in d.path.k]
    ov0 = list(d.overrides)

    def rear(tc):
        # she rears nose-up at the swoop's bottom, so her tail swings down under her onto the roof
        return (tc - SLATE_REAR, tc + SLATE_REAR,
                lambda t, auto: (auto[0], auto[1] - SLATE_PITCH * math.sin(math.pi * (t - tc + SLATE_REAR) /
                                                                          (2.0 * SLATE_REAR)) ** 2, auto[2]))
    anims0 = list(d.anims)
    near = [c for c in solid if math.dist(c, q) < 60.0]       # the cap too: only her tail may touch it
    near = set(near)
    near_after = near - set(cs)                   # the cap is gone from the smash on
    W = SLATE_W
    best = None
    # the swoop's bottom near where her own line passes the cap
    t_near = min(range(T['fly'], T['arch']), key=lambda t: math.dist(E.track_sample(keys0, t), q))
    T['slate'] = (t_near - 8, t_near + 16)
    for tc in range(T['slate'][0], T['slate'][1] + 1, 4):
        d.path.k = [dict(k) for k in keys0]
        d.anims = list(anims0)
        d.sync()
        d.bake()
        V = K.view(sc)
        tail = min((K.seg_box_distance(a, b, lo, hi) - r, name, a, b) for name, (a, b, r) in
                   D.horntail_parts(V, 'horntail', tc, swept=False) if K.part_group(name) == 'tail')
        aim = [(tail[2][i] + tail[3][i]) / 2 for i in range(3)]
        dvec = sub(q, aim)
        for k_ in (0.7, 0.76, 0.82, 0.88, 0.94):
            d.path.k = [dict(k) for k in keys0]
            for k in d.path.k:
                u = (k['t'] - tc) / float(W)
                if abs(u) < 0.5:
                    w_ = math.cos(math.pi * u) ** 2
                    k['pos'] = r4(add(k['pos'], dvec, k_ * w_))
            d.anims = list(anims0)
            d.cue(tc - 15, 'TAIL_WHIP', 6)
            d.cue(tc + 15, 'FLY', 8)
            d.overrides = ov0 + [rear(tc)]
            d.bake()
            V = K.view(sc)
            t_s = first_contact(V, 'horntail', (lo, hi), tc - W // 2, tc + W // 2, parts=('tail',))
            if t_s is None:
                continue
            clear, why = 9.0, None
            for t in sorted(set(range(tc - W // 2, tc + W // 2 + 1, 2)) | set(range(tc - 24, tc + 25))):
                caps = D.horntail_parts(V, 'horntail', t)
                g = K.caps_to_cells(caps, near if t < t_s else near_after, groups=None if not (t_s - 2 <= t <= t_s + 12) else
                                    ('body', 'neck', 'head', 'wing', 'leg'))
                if g[0] < clear:
                    clear, why = g[0], (t, g[1], g[2])
            depth = min(K.seg_box_distance(a, b, lo, hi) - r for t in range(t_s, t_s + 8)
                        for name, (a, b, r) in D.horntail_parts(V, 'horntail', t) if K.part_group(name) == 'tail')
            score = (clear >= 0.85, clear if clear < 0.85 else -abs(depth + 0.6))
            if best is None or score > best[0]:
                best = (score, tc, k_, dvec, t_s, clear, depth, why)
    assert best is not None and best[0][0], ('no swoop lands her tail on the spire', best)
    _, tc, k_, dvec, t_s, clear, depth, why = best
    d.path.k = [dict(k) for k in keys0]
    for k in d.path.k:
        u = (k['t'] - tc) / float(W)
        if abs(u) < 0.5:
            k['pos'] = r4(add(k['pos'], dvec, k_ * math.cos(math.pi * u) ** 2))
    d.anims = list(anims0)
    d.cue(tc - 15, 'TAIL_WHIP', 6)
    d.cue(tc + 15, 'FLY', 8)
    d.overrides = ov0 + [rear(tc)]
    d.sync()
    d.bake()
    sc.notes['slate'] = {'centre': tc, 'depth_scale': k_, 'tick': t_s, 'clear': round(clear, 2), 'into': round(depth, 2),
                         'cap': [lo, hi], 'centre_point': q}
    sc.notes.setdefault('gap_ok', []).append((t_s - 16, t_s + 30, 'she swoops down onto the spire and climbs out'))
    sc.dragon_ok.append((t_s - 2, t_s + 14, ('tail',), 'her tail lashes through the turret spire\'s shingles'))
    # the shingles: the cap to air, each one tumbling off the turret down its fall
    for c in cs:
        sc.ev(t_s, 'block', 'minecraft:air', at=list(c))
    rnd = random.Random('slates')
    V = K.view(sc)
    hv = sub(E.track_sample(d.path.k, t_s + 1), E.track_sample(d.path.k, t_s))
    ids = []
    for i, c in enumerate(cs[:SLATE_DEBRIS]):
        did = 'slate_%02d' % i
        st = world.get(c)
        st = st if not E.bid_of(st).endswith(('_wall', '_fence')) else 'minecraft:oak_planks'
        p0 = [c[0] + 0.5, c[1] + 0.5, c[2] + 0.5]
        nrm = norm([p0[0] - q[0] + 0.01, 0.0, p0[2] - q[2] + 0.01])
        v0 = [hv[0] * 0.35 + nrm[0] * 0.15 + rnd.uniform(-0.08, 0.08), 0.12 + rnd.uniform(0.0, 0.15),
              hv[2] * 0.35 + nrm[2] * 0.15 + rnd.uniform(-0.08, 0.08)]
        g = 0.05
        k = Keys().at(0, PARK).hold(t_s - 0.25)
        k.at(t_s, p0, 'linear')
        look = [{'t': 0, 'yaw': 0.0, 'pitch': 0.0, 'roll': 0.0}, {'t': t_s, 'yaw': 0.0, 'pitch': 0.0, 'roll': 0.0}]
        spin = [rnd.uniform(-20, 20), rnd.uniform(-25, 25), rnd.uniform(-18, 18)]
        land = None
        for t in range(2, 80, 2):
            pt = [p0[0] + v0[0] * t, p0[1] + v0[1] * t - 0.5 * g * t * t, p0[2] + v0[2] * t]
            top = K.ground_top(solid, pt[0], pt[2], pt[1] + 0.5)
            if t > 4 and pt[1] - 0.5 <= top:
                land = (t, [pt[0], top + 0.5, pt[2]])
                break
            k.at(t_s + t, pt, 'spline')
            look.append({'t': t_s + t, 'yaw': round(spin[0] * t, 1), 'pitch': round(spin[1] * t, 1),
                         'roll': round(spin[2] * t, 1), 'ease': 'spline'})
        t_l, pl = land if land else (80, pt)
        k.at(t_s + t_l, pl)
        look.append({'t': t_s + t_l, 'yaw': round(spin[0] * t_l, 1), 'pitch': 0.0, 'roll': 0.0})
        sc.actor({'id': did, 'type': 'hogwartsmagic:memory_block', 'block': st, 'path': k.k, 'look': look})
        ids.append(did)
    sc.sfx(t_s, 'first_task_slate_smash', 1.1)
    sc.ev(t_s, 'particles', 'minecraft:large_smoke', at=r3(q), count=40, spread=[1.5, 0.8, 1.5])
    sc.smashes.append({'tick': t_s, 'label': 'the turret spire\'s shingles', 'cells': [list(c) for c in cs],
                       'box': [lo, hi], 'parts': ('tail',), 'sfx': 'first_task_slate_smash', 'debris': ids})
    return t_s


def chase_crash(sc, d, world):
    """The bridge: her first contact with the central span's break box is the crash tick; every block of the break
    box goes to air on it, chunks of masonry tumble down into the gorge, dust bursts, the crash sounds; she staggers
    (STAGGER re-cued so the pose holds), tumbles down through the fall column and lies at the foot of the gorge, out
    of his sight."""
    V = K.view(sc)
    T = sc.notes['T']
    br = GA['bridge']
    box = [list(map(float, br['break_box'][0])), list(map(float, br['break_box'][1]))]
    t_c = first_contact(V, 'horntail', box, T['crash'] - 40, T['crash'] + 8)
    assert t_c is not None, 'she never meets the bridge'
    # nothing else of the bridge before it: her line keeps clear of the side arches (check_dragon_blocks says so)
    T['crash_tick'] = t_c
    keys = d.path.k
    t_k = t_c + CRASH_INTO                          # she ploughs on into the span a few ticks, then drops
    p_k = E.track_sample(keys, t_k)
    v_c = sub(p_k, E.track_sample(keys, t_k - 1))
    while keys and keys[-1]['t'] > t_k:
        keys.pop()
    d.path.at(t_k, p_k, 'spline')
    p_c = p_k
    rest = [FALL_AT[0], GA['bridge']['fall_column'][0][1] + 0.0, FALL_AT[2]]
    fall = F.hermite(p_c, [v_c[0] * 0.35, v_c[1] * 0.35, v_c[2] * 0.35], rest, [0.0, -1.0, 0.0], t_k, t_c + FALL_TICKS)
    for t in range(t_k + 2, t_c + FALL_TICKS, 2):
        d.path.at(t, fall(t), 'spline')
    d.path.at(t_c + FALL_TICKS, rest, 'linear')
    d.path.hold(sc.L)
    for k in (0, 18, 36):
        d.cue(t_c + k, 'STAGGER', 3)
    d.cue(t_c + FALL_TICKS + 6, 'IDLE', 8)
    # the tumble: her look keys carry her over (nose down, rolling), the auto values blended out from the contact
    def tumble(t, auto):
        u = smooth((t - t_k) / 30.0)
        return (auto[0] + 40.0 * u, auto[1] * (1 - u) + 58.0 * u, auto[2] * (1 - u) + 132.0 * u)
    d.overrides.append((t_k, sc.L, tumble))
    sc.dragon_ok.append((t_c - 1, sc.L, None, 'the crash: she smashes through the span and falls into the gorge'))
    sc.dragon_speed_ok.append((t_c - 1, sc.L, 'the crash and the fall'))
    # the span goes: every cell of the break box to air on the crash tick, chunks of it tumbling down
    cells = [tuple(c) for c in br['cells']]
    for c in cells:
        sc.ev(t_c, 'block', 'minecraft:air', at=list(c))
    rnd = random.Random('bridge')
    # the debris taken all along the span (Task 13 review I7): DEBRIS_PER_SLICE from every DEBRIS_SLICE blocks of
    # it, spread through each slice's cells, flung harder the nearer her
    lo_x = int(box[0][0])
    pick = []
    for x0 in range(lo_x, int(box[1][0]), DEBRIS_SLICE):
        sl = sorted(c for c in cells if x0 <= c[0] < x0 + DEBRIS_SLICE)
        k = max(1, len(sl) // DEBRIS_PER_SLICE)
        pick += [sl[(i * k + rnd.randrange(k)) % len(sl)] for i in range(min(DEBRIS_PER_SLICE, len(sl)))]
    ids = []
    floor = GA['bridge']['fall_column'][0][1]
    for i, c in enumerate(pick):
        did = 'debris_%02d' % i
        st = world.get(c) or 'minecraft:stone_bricks'
        st = st if not E.bid_of(st).endswith(('_wall', 'lantern', '_fence')) else 'minecraft:stone_bricks'
        p0 = [c[0] + 0.5, c[1] + 0.5, c[2] + 0.5]
        out = norm(sub(p0, p_c))
        f = max(0.25, 1.0 - math.dist(p0, p_c) / 14.0)          # the nearer her, the harder
        v0 = [v_c[0] * 0.5 * f + out[0] * rnd.uniform(0.1, 0.4) * f, rnd.uniform(0.02, 0.3) * f,
              v_c[2] * 0.5 * f + out[2] * rnd.uniform(0.1, 0.4) * f]
        g = 0.05
        land_t = (v0[1] + math.sqrt(v0[1] ** 2 + 2 * g * (p0[1] - floor - 0.5))) / g
        k = Keys().at(0, PARK).hold(t_c - 0.25)
        k.at(t_c, p0, 'linear')
        look = [{'t': 0, 'yaw': 0.0, 'pitch': 0.0, 'roll': 0.0}, {'t': t_c, 'yaw': 0.0, 'pitch': 0.0, 'roll': 0.0}]
        spin = [rnd.uniform(-18, 18), rnd.uniform(-22, 22), rnd.uniform(-15, 15)]
        n = int(land_t)
        for t in range(3, n + 1, 3):
            q = [p0[0] + v0[0] * t, p0[1] + v0[1] * t - 0.5 * g * t * t, p0[2] + v0[2] * t]
            k.at(t_c + t, q, 'spline')
            look.append({'t': t_c + t, 'yaw': round(spin[0] * t, 1), 'pitch': round(spin[1] * t, 1),
                         'roll': round(spin[2] * t, 1), 'ease': 'spline'})
        endp = [p0[0] + v0[0] * land_t, floor + 0.5, p0[2] + v0[2] * land_t]
        k.at(t_c + land_t + 0.5, endp)
        look.append({'t': t_c + land_t + 0.5, 'yaw': round(spin[0] * n, 1), 'pitch': round(round(spin[1] * n / 90.0) * 90.0, 1),
                     'roll': round(round(spin[2] * n / 90.0) * 90.0, 1)})
        sc.actor({'id': did, 'type': 'hogwartsmagic:memory_block', 'block': st, 'path': k.k, 'look': look})
        ids.append(did)
    sc.sfx(t_c, 'first_task_bridge_crash', 1.25)
    for q, n_ in ((p_c, 120), (add(p_c, [0.0, -6.0, 0.0]), 80)):
        sc.ev(t_c, 'particles', 'minecraft:explosion', at=r3(q), count=6, spread=[3.0, 2.0, 2.0])
        sc.ev(t_c, 'particles', 'minecraft:large_smoke', at=r3(q), count=n_, spread=[5.0, 3.0, 2.5])
        sc.ev(t_c + 2, 'particles', 'minecraft:poof', at=r3(q), count=n_, spread=[6.0, 4.0, 3.0])
    # and dust all along the span as it goes, every DUST_EVERY blocks
    zc, yc = (box[0][2] + box[1][2]) / 2.0, (box[0][1] + box[1][1]) / 2.0
    nb = int(math.ceil((box[1][0] - box[0][0] - 2.0) / DUST_EVERY)) + 1
    for k in range(nb):
        x = box[0][0] + 1.0 + (box[1][0] - box[0][0] - 2.0) * k / (nb - 1.0)
        sc.ev(t_c, 'particles', 'minecraft:large_smoke', at=r3([x, yc, zc]), count=50, spread=[2.5, 3.0, 2.0])
        sc.ev(t_c + 2, 'particles', 'minecraft:poof', at=r3([x, yc - 1.0, zc]), count=40, spread=[3.0, 3.5, 2.5])
    sc.ev(t_c + FALL_TICKS, 'particles', 'minecraft:large_smoke', at=r3(rest), count=90, spread=[4.0, 1.5, 4.0])
    sc.sfx(t_c + FALL_TICKS, 'first_task_ground_thud', 1.0)
    sc.smashes.append({'tick': t_c, 'label': 'the bridge', 'cells': [list(c) for c in cells], 'box': box,
                       'parts': None, 'sfx': 'first_task_bridge_crash', 'debris': ids})
    return t_c


def chase_chain(sc, d, solid, arena):
    """Her chain, carried on from the arena's last keys, on her collar until the snap; then it breaks at her collar:
    each link flung back toward the stake and up (the nearer her neck, the harder), falling under gravity onto the
    ground, where it lies at the angle it fell."""
    V = K.view(sc)
    T = sc.notes['T']
    ts = T['snap']
    ch = Chain(sc, 'chain')
    for t in range(0, ts + 1, 2):
        neck = K.neck_point(V, 'horntail', t)
        links, pts = chain_curve(STAKE, neck, CHAIN_LEN, solid)
        ch.put(t, links, force=(t == ts))
    links, pts = chain_curve(STAKE, K.neck_point(V, 'horntail', ts), CHAIN_LEN, solid)
    rnd = random.Random('chain')
    g = 0.07
    for k, (p0, dvec) in enumerate(links):
        f = (k + 1) / float(len(links))
        back = norm(sub(STAKE, p0))
        v0 = [back[0] * 0.55 * f + rnd.uniform(-0.05, 0.05), 0.12 + 0.3 * f, back[2] * 0.55 * f + rnd.uniform(-0.05, 0.05)]
        spin = rnd.uniform(-14, 14) * f
        yw0, pi0 = K.link_look(dvec)
        landed = None
        for t in range(1, 60):
            q = [p0[0] + v0[0] * t, p0[1] + v0[1] * t - 0.5 * g * t * t, p0[2] + v0[2] * t]
            top = K.ground_top(solid, q[0], q[2], q[1] + 0.6)
            if q[1] <= top + 0.12 and t > 2:
                q[1] = top + 0.12
                landed = (t, q)
                break
            ch.paths[k].at(ts + t, q, 'spline')
            ch.looks[k].append({'t': ts + t, 'yaw': round(yw0 + spin * t, 2), 'pitch': round(pi0 + 9.0 * f * t, 2),
                                'ease': 'spline'})
        t_l, q = landed if landed else (60, q)
        ch.paths[k].at(ts + t_l, q)
        ch.looks[k].append({'t': ts + t_l, 'yaw': round(yw0 + spin * t_l, 2), 'pitch': 90.0})
    ch.emit()
    sc.chain = {'links': ch.ids, 'snap': ts, 'end_of': chain_end, 'stake': STAKE}
    sc.notes['chain_rest'] = {aid: E.track_sample(E.actor_by_id(K.view(sc), aid)['path'], sc.L) for aid in ch.ids}


def chase_breaths(sc, d, world):
    """Two breaths in the air (AIR_BREATH, the jaw open at 12): the great hall's south-west gable (the grounds' slate
    patch 1), breathed at the tick in her reach whose jet (dragon_engine.fire_jet, with the breathe in place) lands
    on it for the most ticks; then the lick (lick_breath), at the round north-east tower's roof just behind him. Her
    head is turned onto each afterwards (heads). Returns the breathe ticks."""
    T = sc.notes['T']
    out = []
    V = K.view(sc)
    for name, patch in (('the great hall roof', GA['slate_roofs'][0]),):
        q = patch['centre']
        tgt = [q[0], q[1] - 0.4, q[2]]
        cs = patch['cells']
        lo = [min(c[i] for c in cs) - 1.5 for i in range(3)]
        hi = [max(c[i] for c in cs) + 2.5 for i in range(3)]
        best = None
        anims, actions = list(d.anims), list(d.actions)
        near = [t for t in range(T['fly'], T['slate'][0]) if 6.0 <= math.dist(d.pos(t + 10), tgt) <= 17.0]
        assert near, 'she never comes within reach of %s' % name
        w0, w1 = min(near), max(near)
        T['air1'] = (w0, w1)
        for tb in range(w0, w1 + 1, 2):
            nxt = min([c['t'] for c in anims if c['t'] > tb - 12] or [10 ** 9])
            if tb + AIR_BREATH_T > nxt - 2:
                continue                            # the cue after it would close her jaw on the breath
            d.anims, d.actions = list(anims), list(actions)
            d.cue(tb - 12, 'AIR_BREATH', 8)
            d.breathe(tb, AIR_BREATH_T, tgt)
            V = K.view(sc)
            hits = 0
            for t in range(tb + 6, tb + AIR_BREATH_T + 1, 2):
                jet = D.fire_jet(V, 'horntail', t, world)
                if jet is None:
                    continue
                o, dd, ln = jet
                e = [o[i] + dd[i] * (ln + 0.06) for i in range(3)]
                if ln < D.FIRE_MAX_REACH - 1e-6 and all(lo[i] <= e[i] <= hi[i] for i in range(3)):
                    hits += 1
            if best is None or hits > best[0]:
                best = (hits, tb)
        d.anims, d.actions = anims, actions
        assert best is not None and best[0] >= 5, ('the breath never lands on %s' % name, best)
        tb = best[1]
        d.cue(tb - 12, 'AIR_BREATH', 8)
        d.cue(tb + 44, 'FLY', 8)
        d.breathe(tb, AIR_BREATH_T, tgt)
        sc.fire_hits.append((tb + 6, tb + AIR_BREATH_T, name, [lo, hi]))
        sc.notes.setdefault('roof_targets', []).append((tb, tgt))
        out.append(tb)
    out.append(lick_breath(sc, d, world))
    return out


LICK = (0.6, 2.2)                                # the second breath's flame licks his broom's tail this close
LICK_BODY = 1.7                                  # while it stays this far off him (assert 12 holds 1.5)


def broom_tail(scene, bid, t):
    """The tail end of the broom's box (its brush), scene space: the end of its long axis behind its travel."""
    b = E.actor_by_id(scene, bid)
    lo, hi = E.broom_box(b['type'], b.get('scale', 1.0))
    bp = E.broom_pose(scene, b, t)
    ends = [add(bp['pos'], E._mat_vec(bp['m'], [0.0, (lo[1] + hi[1]) / 2, z])) for z in (lo[2], hi[2])]
    v = sub(E.track_sample(b['path'], t + 0.5), E.track_sample(b['path'], t - 0.5))
    return min(ends, key=lambda p: sum((p[i] - bp['pos'][i]) * v[i] for i in range(3)))


def him_gap(scene, geom, t, viewer='viewer'):
    """The least gap from the drawn flame to his part boxes (fire_gap at their corners)."""
    v = E.actor_by_id(scene, viewer)
    return min(K.fire_gap(geom, q) for part, rt, yaw, s, piv, rot in E.posed_parts(scene, v, t)
               for q in E.part_corners(rt, yaw, s, piv, rot, E.PART_BOX[part]))


def lick_breath(sc, d, world):
    """The second air breath: as he dives past the round north-east tower into the gap, she breathes at its roof
    just behind him, so the flame licks his broom's tail (LICK) while it keeps LICK_BODY off him: the breathe tick
    and the roof point solved over the tower's open faces (the jet's ray lands on the tower for 5 ticks or more).
    A near miss (the gasp), its roar and the scorch. Returns the breathe tick."""
    T = sc.notes['T']
    tower = K.cells_in_box(sc.notes['solid'], [93, 54, -60], [104, 66, -48])
    sol = set(tower)
    faces = []
    for c in tower:
        for n in ((0, 1, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1)):
            if (c[0] + n[0], c[1] + n[1], c[2] + n[2]) not in sol:
                faces.append([c[0] + 0.5 + 0.55 * n[0], c[1] + 0.5 + 0.55 * n[1], c[2] + 0.5 + 0.55 * n[2]])
    faces.sort()
    V0 = K.view(sc)
    t_slot = min(range(T['fly'], T['arch']), key=lambda t: math.dist(E.track_sample(E.actor_by_id(V0, 'firebolt')['path'], t), SLOT_AT))
    anims, actions = list(d.anims), list(d.actions)
    best = None
    for tb in range(t_slot - 44, t_slot - 13, 3):
        nxt = min([c['t'] for c in anims if c['t'] > tb - 12] or [10 ** 9])
        if tb + AIR_BREATH_T > nxt - 2:
            continue
        for tgt in faces[::2]:
            d.anims, d.actions = list(anims), list(actions)
            d.cue(tb - 12, 'AIR_BREATH', 8)
            d.breathe(tb, AIR_BREATH_T, tgt)
            V = K.view(sc)
            hits, body, tail = 0, 9.0, (9.0, None)
            for t in range(tb + 2, tb + AIR_BREATH_T + 1):
                jet = D.fire_jet(V, 'horntail', t, world)
                if jet is None:
                    continue
                o, dd, ln = jet
                if ln < D.FIRE_MAX_REACH - 1e-6 and math.dist([o[i] + dd[i] * ln for i in range(3)], tgt) < 2.0:
                    hits += 1
                g = (o, dd, ln, 1.0, [])
                body = min(body, him_gap(V, g, t))
                gt = K.fire_gap(g, broom_tail(V, 'firebolt', t))
                if gt < tail[0]:
                    tail = (gt, t)
                if body < LICK_BODY:
                    break
            if hits < 5 or body < LICK_BODY:
                continue
            score = (LICK[0] <= tail[0] <= LICK[1], -abs(tail[0] - sum(LICK) / 2.0), hits)
            if best is None or score > best[0]:
                best = (score, tb, tgt, tail, body, hits)
    d.anims, d.actions = anims, actions
    assert best is not None and best[0][0], ('no breath licks his tail', best)
    _, tb, tgt, tail, body, hits = best
    d.cue(tb - 12, 'AIR_BREATH', 8)
    d.cue(tb + 44, 'FLY', 8)
    d.breathe(tb, AIR_BREATH_T, tgt)
    lo, hi = [tgt[i] - 2.0 for i in range(3)], [tgt[i] + 2.0 for i in range(3)]
    sc.fire_hits.append((tb + 2, tb + AIR_BREATH_T, 'the round tower roof behind him', [lo, hi]))
    sc.notes.setdefault('roof_targets', []).append((tb, tgt))
    sc.notes['lick'] = {'tick': tail[1], 'tail': round(tail[0], 2), 'body': round(body, 2), 'breathe': tb,
                        'target': r3(tgt), 'hits': hits, 'slot_tick': t_slot}
    T['air2'] = tb
    return tb


_ROOFS = []


def castle_roof_tops():
    """The castle's open top blocks (a block with air over it, above the plateau): what a breath can scorch."""
    if not _ROOFS:
        cells = FG.CASTLE
        for (x, y, z), b in cells.items():
            if y > FG.G + 20 and (x, y + 1, z) not in cells and (x, y + 2, z) not in cells:
                _ROOFS.append([float(x), float(y), float(z)])
        _ROOFS.sort()
    return _ROOFS


def chase_heads(sc, d, T, breaths):
    """Her head: the rebreathe at the rock, onto him as he dives at her, the bite at him, up at him as she strains and
    after him as she launches, then forward with a glance after him, and onto each roof she breathes at."""
    V = K.view(sc)
    him = lambda t: F.eye_fn(V, 'viewer')(t)
    trail = sc.notes['arena_scorch']
    (r0, rd), (f0, fd) = REBREATHE
    head_track(sc, d, d.head, lambda t: trail[min(len(trail) - 1, int(len(trail) * (t - r0) / float(rd)))], 2,
               r0 + rd, 2)
    head_track(sc, d, d.head, lambda t: FACE_AIM, f0, f0 + fd + 2, 4)
    head_track(sc, d, d.head, him, 52, T['takeoff'] - 2, 6)
    tb = T['bite']
    for k in d.head:                                # up off the floor as she comes down from the bite
        if tb + BITE_SNAP < k['t'] < tb + BITE_TICKS + 12:
            u = math.sin(math.pi * (k['t'] - tb - BITE_SNAP) / float(BITE_TICKS + 2)) ** 2
            k['v'][0] = round(min(40.0, k['v'][0] + BITE_LIFT * u), 2)
    d.sync()
    d.look_head(T['takeoff'] + 10, 0, 0, None)
    for tb in breaths:
        tgt = next(q for t_, q in sc.notes['roof_targets'] if t_ == tb)
        d.hold_head(tb - 14)
        head_track(sc, d, d.head, lambda t, q=tgt: q, tb - 4, tb + AIR_BREATH_T, 4)
        d.look_head(tb + 50, 0, 0, None)
    d.hold_head(T['crash_tick'])


def chase_rider_look(sc, v, T):
    """His head on the broom: down at her as he dives at her, back over his shoulder at her as she launches and in
    the castle, ahead through the gorge, back at the bridge as she hits it, then ahead, home."""
    V = K.view(sc)
    her = lambda t: K.dragon_eye(V, 'horntail', t)
    look = [{'t': 0, 'yaw': 0.0, 'pitch': 10.0}]
    F.aim_rider(sc, 'viewer', look, 60, T['pass'] + 8, her, step=10, max_pitch=60.0)
    look.append({'t': T['pass'] + 20, 'yaw': 0.0, 'pitch': 0.0})
    F.aim_rider(sc, 'viewer', look, T['snap'] - 10, T['takeoff'] + 30, her, step=12, max_pitch=50.0, gain=0.9)
    look.append({'t': T['takeoff'] + 48, 'yaw': 0.0, 'pitch': -4.0})
    for t0, t1 in ((420, 470), (560, 610)):
        look.append({'t': t0, 'yaw': 0.0, 'pitch': -4.0})
        F.aim_rider(sc, 'viewer', look, t0 + 10, t1, her, step=10, max_pitch=40.0, gain=0.85)
        look.append({'t': t1 + 14, 'yaw': 0.0, 'pitch': -4.0})
    look.append({'t': T['crash_tick'] - 6, 'yaw': 0.0, 'pitch': -2.0})
    F.aim_rider(sc, 'viewer', look, T['crash_tick'], T['crash_tick'] + 24, her, step=8, max_pitch=40.0, gain=0.7)
    look.append({'t': T['crash_tick'] + 40, 'yaw': 0.0, 'pitch': -4.0})
    look.append({'t': sc.L, 'yaw': -10.0, 'pitch': 4.0})
    v['look'] = look


def chase_sound(sc, d, T, breaths, world):
    sc.ev(0, 'effect', 'sky', at=r3(GA['nest_centre']), style='overcast')
    sc.music(M_CHASE, 'first_task_chase')
    sc.sfx(0, 'first_task_crowd_bed', 0.42, loop=True, duration=T['takeoff'] + 120)
    for t0, _ in REBREATHE:
        sc.sfx(t0, 'first_task_fire_roar', 0.35, hit=t0, label='the scorch breathed back')
    for tb in breaths:
        sc.sfx(tb, 'first_task_fire_roar', 1.0, hit=tb, label='an air breath')
        sc.sfx(tb + 8, 'first_task_fire_scorch', 0.7)
    tb_ = T['bite'] + BITE_SNAP
    sc.sfx(tb_, 'first_task_horntail_snap', 1.1, hit=tb_, label='the jaws snap shut')
    sc.sfx(T['roar'], 'first_task_horntail_roar', 1.2, hit=T['roar'], label='she rears against the chain')
    sc.sfx(T['snap'], 'first_task_chain_snap', 1.2, hit=T['snap'], label='the chain snaps')
    sc.say(T['snap'] + 6, 'first_task_comm_chain')
    commentator(sc, [(T['snap'] + 6, 'first_task_comm_chain')])
    sc.sfx(T['takeoff'] + 10, 'first_task_wind_high', 0.6, loop=True, duration=T['out'] - T['takeoff'] - 10)
    # the crowd: a gasp at the snap of her jaws by him, a scream at the chain
    V = K.view(sc)
    v = E.actor_by_id(V, 'viewer')
    dj, tj = bite_pass(V, T['bite'])                # her snout at its nearest to him, the jaws open, then the clap
    jaws = int(math.ceil(tj))
    sc.notes['bite_gap'] = (round(dj, 2), tj, round(jaws_gap(V, T['bite'] + BITE_SNAP), 2))
    sc.near_misses.append((jaws, 'jaws', 'her jaws %.1f from him' % dj))
    sc.sfx(jaws + 3, 'flying_crowd_gasp', 0.95)
    crowd_all(sc, jaws + 5, 'gasp', ('near miss', jaws), stagger=3)
    sc.sfx(T['snap'] + 3, 'flying_crowd_gasp', 1.0)
    crowd_all(sc, T['snap'] + 5, 'gasp', ('the chain snaps', T['snap']), stagger=3)
    # the second air breath licks his broom's tail: the far stands gasp
    lk = sc.notes['lick']
    sc.near_misses.append((lk['tick'], 'fire', 'her flame %.1f off his broom' % lk['tail']))
    sc.sfx(lk['tick'] + 4, 'flying_crowd_gasp', 0.45)
    crowd_all(sc, lk['tick'] + 6, 'gasp', ('near miss', lk['tick']), stagger=2)


def jaws_gap(scene, t):
    """Her snout (horntail_snout) to his nearest part box at t."""
    return K.body_gap(scene, 'viewer', t, D.horntail_snout(scene, 'horntail', t)[0])[0]


def bite_pass(scene, cue_t):
    """(least snout-to-him gap, its tick) over the BITE cue at cue_t, every quarter tick."""
    return min((jaws_gap(scene, q / 4.0), q / 4.0) for q in range((cue_t + 2) * 4, (cue_t + 22) * 4 + 1))


SNAP_AFTER = 2.5                                 # the jaws clap at most this many ticks after her snout's nearest pass
SNAP_GAP = 3.0                                   # and he is at most this far off them as they clap


def check_bite(sc, scene, problems):
    """The bite (Task 13 review I5): her snout passes within BITE_REACH of his nearest part, the jaws clap (the
    BITE cue + BITE_SNAP, the snap sfx on it) at most SNAP_AFTER ticks after that nearest pass and with him at most
    SNAP_GAP off them. Returns (nearest gap, its tick, the gap at the clap)."""
    a = E.actor_by_id(scene, 'horntail')
    cues = [c['t'] for c in a.get('anims', []) if c['name'] == 'BITE']
    if not cues:
        problems.append('%s: no BITE cue' % sc.sid)
        return None
    tb = cues[0]
    snap = tb + BITE_SNAP
    g, tg = bite_pass(scene, tb)
    gs = jaws_gap(scene, snap)
    if g > BITE_REACH:
        problems.append('%s: her snout comes only %.2f from him at the bite (needs %.1f)' % (sc.sid, g, BITE_REACH))
    if not 0.0 <= snap - tg <= SNAP_AFTER:
        problems.append('%s: the jaws clap at t %d, %.2f ticks after her nearest pass (t %.2f)' % (sc.sid, snap,
                                                                                               snap - tg, tg))
    if gs > SNAP_GAP:
        problems.append('%s: he is %.2f from her jaws as they clap (t %d)' % (sc.sid, gs, snap))
    if not any(e.get('id') == K.SFX + 'first_task_horntail_snap' and abs(e['t'] - snap) <= 1 for e in scene['events']):
        problems.append('%s: no snap sfx on the clap (t %d)' % (sc.sid, snap))
    return round(g, 2), tg, round(gs, 2)


def chase_camera(sc, d, T, breaths):
    V = K.view(sc)
    boy = lambda t: E.rider_eye(V, E.actor_by_id(V, 'viewer'), t)
    her = lambda t: D.horntail_frame(V, 'horntail', t)[0]
    her_eye = lambda t: K.dragon_eye(V, 'horntail', t)
    head = lambda t: K.dragon_head(V, 'horntail', t)
    L = sc.L
    tc = T['crash_tick']
    t_s = sc.notes['slate']['tick']
    a1, a2 = breaths
    lick = sc.notes['lick']
    cuts = sc.notes['cuts'] = chase_cuts(T, t_s, a1, a2, tc)
    sbl = smoothed(boy, 4.0)
    mid = lambda t: lerp(boy(t), her_eye(t), 0.5)
    sml = smoothed(mid, 6.0)
    she = smoothed(her_eye, 5.0)
    # C1 0-3.5 high behind him as he banks round over the arena, the look tipped down onto her on the floor below,
    # her fire pouring along the scorched floor and onto the rock (Task 13 review M10), then back up after him
    raw, sb, hb = F.follow_cam(boy, lambda t: [-8.0, 3.2, 2.4], 8.0, L)
    cam = K.spring_cam(raw, 0, 70)
    sc.shot(0, 70, cam, lambda t: lerp(add(sbl(t), hb(t), 5.0), she(t), 0.42 - 0.27 * smooth((t - 40) / 30.0)),
            ramp(68.0, 62.0, 0, 70), 4)
    # C2 3.5-6.4 low on the floor off her flank: her head swings after him as he comes down at her out of the south
    pf = move(C2_CAM, add(C2_CAM, [0.3, 0.1, 0.4]), 70, 128)
    sc.shot(70, 128, pf, smoothed(lambda t: lerp(head(t), boy(t), smooth((t - 90) / 30.0) * 0.45), 5.0),
            ramp(58.0, 54.0, 70, 128), 4)
    # C3 6.4-8.3 close on her head from the west: she springs at him, her open jaws a block and a half from him, and
    # snap shut behind his broom
    pf = move(C3_CAM, add(C3_CAM, [0.0, 0.05, 0.25]), 128, 166)
    sc.shot(128, 166, pf, smoothed(lambda t: lerp(head(t), PP, 0.4), 5.5), ramp(48.0, 46.0, 128, 166), 4)
    # C4 8.3-11.3 wide and low from the south-west: she rears against the chain, and it snaps
    pf = move(C4_CAM, add(C4_CAM, [0.4, 0.15, -0.3]), 166, 226)
    sc.shot(166, 226, pf, smoothed(lambda t: lerp(head(t), STAKE, 0.25), 6.0), ramp(64.0, 60.0, 166, 226), 4)
    # C5 11.3-15.0 behind her on the floor as she beats up into the sky after him
    pf = move(C5_CAM, add(C5_CAM, [0.6, 0.5, -0.4]), 226, 300)
    sc.shot(226, 300, pf, smoothed(lambda t: lerp(her_eye(t), boy(t), 0.3), 8.0), ramp(62.0, 70.0, 226, 300), 4)
    # C6 wide alongside the two of them out over the plateau, the castle ahead: she gains on him, wingbeat by
    # wingbeat
    c6, c7, c8, c9, c10, c11, c12, c13, c14, c15 = cuts
    raw, sm, hm = F.follow_cam(mid, lambda t: [2.0, 5.0, -30.0 + 8.0 * smooth((t - c6) / float(c7 - c6))], 8.0, L)
    cam = K.spring_cam(raw, c6, c7)
    sc.shot(c6, c7, cam, sml, ramp(60.0, 54.0, c6, c7), 4)
    # C7 the insert: close beside her head as her jaws open and the fire bursts out onto the great hall roof below
    tgt1 = next(q for t_, q in sc.notes['roof_targets'] if t_ == a1)
    side = insert_side(V, a1 + 6, tgt1)
    raw, sh, hh = F.follow_cam(head, lambda t: [1.2, 2.8, 4.6 * side], 5.0, L)
    cam = K.spring_cam(raw, c7, c8)
    sc.shot(c7, c8, cam, smoothed(lambda t: add(head(t), [0.0, -0.3, 0.0]), 3.0), ramp(46.0, 42.0, c7, c8), 4)
    # C8 over his shoulder looking back: her open jaws, still pouring fire on the roof, a few blocks off his broom
    raw, sb2, hb2 = F.follow_cam(boy, lambda t: [0.4, 0.75, -0.95], 4.0, L)
    cam = K.spring_cam(raw, c8, c9, omega=0.12, a_max=0.0016)
    sc.shot(c8, c9, cam, smoothed(lambda t: lerp(head(t), boy(t), 0.12), 3.0), ramp(64.0, 60.0, c8, c9), 4)
    # C9 the spire (Task 13 review I2): outside her swoop, 14-20 from the turret's cap, her tail lashing in and the
    # shingles tumbling
    cap = sc.notes['slate']['centre_point']
    c0 = SPIRE_CAM
    pf = move(c0, add(c0, [0.6, -0.4, 0.5]), c9, c10)
    sc.shot(c9, c10, pf, smoothed(lambda t: lerp(cap, her_eye(t), 0.35 * (1 - smooth((t - t_s + 14) / 14.0)) +
                                                 0.15 * smooth((t - t_s - 8) / 20.0)), 4.0),
            ramp(54.0, 50.0, c9, c10), 4)
    # C10 a static angle north of the gap: he dives through the slot between the hall wall and the turret at us, her
    # flame licking his broom's tail, and whips past; she is forced up and over the turret
    pf = move(GAP_CAM, add(GAP_CAM, [0.3, 0.2, -0.3]), c10, c11)
    slot = list(SLOT_AT)
    sc.shot(c10, c11, pf, smoothed(lambda t: lerp(lerp(slot, boy(min(t, lick['slot_tick'] - 2)), 0.6),
                                                  her_eye(t), 0.15 + 0.6 * smooth((t - lick['slot_tick'] + 6) / 16.0)), 5.0),
            ramp(60.0, 56.0, c10, c11), 4)
    # C11 behind her as she stoops down off the turret after him, her wings filling the frame, him ahead
    raw, sh, hh = F.follow_cam(her_eye, lambda t: [-10.0, 3.5, 3.0], 8.0, L)
    cam = K.spring_cam(raw, c11, c12)
    sc.shot(c11, c12, cam, lambda t: lerp(she(t), sbl(t), 0.55), ramp(62.0, 58.0, c11, c12), 4)
    # C12 alongside, west along the castle's north side toward the gorge, she right on his tail
    raw, sm2, hm2 = F.follow_cam(mid, lambda t: [1.0, 2.5, 13.0], 8.0, L)
    cam = K.spring_cam(raw, c12, c13)
    sc.shot(c12, c13, cam, sml, ramp(56.0, 54.0, c12, c13), 4)
    # C13 from the gorge's west rim: they dive over the edge and down into it past us
    pf = move(RIM_CAM, add(RIM_CAM, [0.4, -0.6, 0.6]), c13, c14)
    sc.shot(c13, c14, pf, smoothed(lambda t: lerp(boy(t), her_eye(t), 0.4 + 0.35 * smooth((t - c13 - 20) / 30.0)), 6.0),
            ramp(62.0, 60.0, c13, c14), 4)
    # C14 low in the gorge south of the bridge: he shoots down the gorge, under the arch and past us; she comes on
    # high behind him, at the span
    pf = move(C11_CAM, add(C11_CAM, [0.2, 0.3, 0.2]), c14, c15)
    sc.shot(c14, c15, pf, smoothed(lambda t: lerp(ARCH_AT, lerp(boy(t), her_eye(t), 0.5), 0.55), 6.0),
            ramp(66.0, 70.0, c14, c15), 4)
    # C15 from the gorge's side, cut in just before she hits (Task 13 review I7): she slams into the span, it breaks
    # all along, she tumbles down through it into the gorge
    pf = move(C12_CAM, add(C12_CAM, [-0.4, -0.3, 0.3]), c15, tc + 60)
    sc.shot(c15, tc + 60, pf, smoothed(lambda t: lerp([ARCH_AT[0], 12.0, -20.0], her(t), 0.75 * smooth((t - tc) / 22.0)),
                                       4.0), ramp(58.0, 56.0, c15, tc + 60), 4)
    # C16 alone: he climbs out of the gorge's head into the open sky and turns home, a slow push in
    raw, sb, hb = F.follow_cam(boy, lambda t: [3.6 - 1.0 * smooth((t - tc - 60) / 120.0), 0.6, 2.2], 8.0, L)
    cam = K.spring_cam(raw, tc + 60, L)
    sc.shot(tc + 60, L, cam, sbl, ramp(50.0, 44.0, tc + 60, L), 4)
    sc.notes['softened'] = K.soften_shots(sc)
    sc.notes['tamed'] = K.tame_shot_ends(sc)
    E_ = lambda sc_, t: E.rider_eye(sc_, E.actor_by_id(sc_, 'viewer'), t)
    D_ = lambda sc_, t: K.dragon_eye(sc_, 'horntail', t)
    H_ = lambda sc_, t: K.dragon_head(sc_, 'horntail', t)
    sm_ = next(x for x in sc.smashes if x['label'] != 'the bridge')
    deb = lambda sc_, t: [sum(E.track_sample(E.actor_by_id(sc_, i)['path'], t)[k] for i in sm_['debris']) /
                          len(sm_['debris']) for k in range(3)]
    sc.framing += [(4, 66, E_, 'the champion banking round (C1)'), (10, 40, D_, 'the Horntail breathing below (C1)'),
                   (74, 124, D_, 'the Horntail (C2)'), (132, 150, H_, 'the bite (C3)'),
                   (170, 222, D_, 'she rears (C4)'), (230, 296, D_, 'her launch (C5)'),
                   (c6 + 4, c7 - 4, E_, 'the champion (C6)'), (c6 + 4, c7 - 4, D_, 'the Horntail gaining (C6)'),
                   (c7 + 2, c8 - 2, H_, 'her head and jaws as she breathes (C7)'),
                   (c8 + 2, c9 - 2, H_, 'her jaws behind him (C8)'),
                   (c9 + 2, c10 - 2, lambda sc_, t: cap, 'the turret spire (C9)'),
                   (t_s, min(c10 - 2, t_s + 20), deb, 'the shingles tumbling (C9)'),
                   (c10 + 2, lick['slot_tick'] - 2, E_, 'the champion through the gap (C10)'),
                   (lick['slot_tick'] + 10, c11 - 2, D_, 'the Horntail over the turret (C10)'),
                   (c11 + 2, c12 - 2, D_, 'the Horntail stooping (C11)'), (c11 + 2, c12 - 2, E_, 'the champion ahead (C11)'),
                   (c12 + 2, c13 - 2, D_, 'the Horntail on his tail (C12)'), (c12 + 2, c13 - 2, E_, 'the champion (C12)'),
                   (c13 + 2, c14 - 2, D_, 'the Horntail diving into the gorge (C13)'),
                   (c14 + 4, c15 - 4, lambda sc_, t: ARCH_AT, 'the arch (C14)'),
                   (c15 + 2, tc + 30, D_, 'she hits the span and falls (C15)'), (tc + 64, L - 4, E_, 'the champion alone (C16)')]
    sc.notes['seen'] = sc.notes.get('seen', []) + [
        (lambda sc_, t: cap_face(cap, SPIRE_CAM), t_s - 6, t_s + 10, 12, 'the turret spire as her tail smashes it (C9)')]


SPIRE_CAM = [54.0, 83.0, 13.5]                   # south-west of the north-west turret's spire, outside her swoop
GAP_CAM = [104.5, 64.0, -70.0]                   # north-east of the gap, looking back south down the slot
RIM_CAM = [-13.0, 31.0, -86.0]                   # on the gorge's west rim where they dive in


def chase_cuts(T, t_s, a1, a2, tc):
    """The chase's cuts from C6 on, from its beats: C7 (the insert) on the great hall breath's jaws opening, C8
    (over his shoulder) from the breath's middle, C9 (the spire) t_s - 23, C10 (the gap) as the second breath's jaws
    open, C11-C14 through the stoop, the north side and the gorge, C15 seven ticks before the crash."""
    return (300, a1 - 8, a1 + 15, t_s - 23, a2 - 8, a2 + 46, a2 + 98, a2 + 154, a2 + 218, tc - 7)


def insert_side(scene, t, target):
    """+1 or -1: the side of her head (follow_cam's right) away from where she breathes, for the insert camera."""
    h = D.horntail_snout(scene, 'horntail', t)
    v = sub(target, h[0])
    hd = norm([h[1][0], 0.0, h[1][2]])
    r = [-hd[2], 0.0, hd[0]]
    return -1.0 if v[0] * r[0] + v[2] * r[2] > 0 else 1.0


def cap_face(cap, cam):
    """A point just outside the spire's cap toward the camera (so the occlusion ray does not end inside it)."""
    return add(cap, norm(sub(cam, cap)), 2.2)


GAP_MAX, GAP_MEAN = 17.0, 13.0                   # her head to his eye once she has closed on him: at most, on average
GLIDE_RUN = 24                                   # the longest still-winged glide she may show in frame, outside a dive


def check_chase_gap(sc, scene, problems):
    """Task 13 review I1: from TCH['closed'] to the crash her head is at most GAP_MAX from his eye (GAP_MEAN on
    average), but where she is named over the turret. Returns (nearest, farthest, mean)."""
    T = sc.notes['T']
    v = E.actor_by_id(scene, 'viewer')
    gs = []
    for t in range(T['closed'], T['crash_tick'] + 1, 2):
        if any(a <= t <= b for a, b, _ in sc.notes.get('gap_ok', [])):
            continue
        g = math.dist(K.dragon_head(scene, 'horntail', t), E.rider_eye(scene, v, t))
        gs.append(g)
        if g > GAP_MAX:
            problems.append('%s: at t %d she is %.1f behind him (at most %.0f)' % (sc.sid, t, g, GAP_MAX))
            break
    mean = sum(gs) / max(1, len(gs))
    if mean > GAP_MEAN:
        problems.append('%s: she runs %.1f behind him on average (at most %.0f)' % (sc.sid, mean, GAP_MEAN))
    return round(min(gs), 2), round(max(gs), 2), round(mean, 2)


def glide_ticks(scene, aid):
    """The ticks she flies a beating cue (FLY, AIR_BREATH) with her wings held in the glide (dragon_engine.glide at
    0.5 or more), and the ticks she flies a beating cue."""
    a = E.actor_by_id(scene, aid)
    keys = a['path']
    fly, gl = [], []
    for t in range(0, scene['length'] + 1):
        st = D.horntail_cue(a, t)
        if not st['flying'] or st['name'] not in ('FLY', 'AIR_BREATH'):
            continue
        fly.append(t)
        if D.glide(E.path_speed(keys, t), D.climb_rate(keys, t)) >= 0.5:
            gl.append(t)
    return gl, fly


def check_glide(sc, scene, problems):
    """Task 13 review I3: outside her named dives no run of still-winged glide longer than GLIDE_RUN ticks while she
    is in frame within 50 blocks. Returns (gliding ticks, flying ticks, the longest such run)."""
    gl, fly = glide_ticks(scene, 'horntail')
    dives = sc.notes.get('dive_ticks', [])
    worst, run, prev = 0, 0, None
    for t in gl:
        c = E.engine_cam(scene['camera'], t)
        lk, fov = E.engine_look(scene, scene['camera'], t)
        p = K.dragon_eye(scene, 'horntail', t)
        ok = (not any(a <= t <= b for a, b, _ in dives) and math.dist(c, p) < 50.0
              and E.in_view(c, lk, fov, p, aspect=16 / 9.0))
        run = (run + 1 if prev == t - 1 else 1) if ok else 0
        prev = t
        worst = max(worst, run)
    if worst > GLIDE_RUN:
        problems.append('%s: she glides with still wings %d ticks running in frame (at most %d)' % (sc.sid, worst,
                                                                                                GLIDE_RUN))
    return len(gl), len(fly), worst


DEBRIS_SLICE, DEBRIS_PER = 3, 8                  # the span's debris: at least this many in every slice this wide
DUST_EVERY = 5.0                                 # and a dust burst within this of every point along it


def check_debris_span(sc, scene, problems):
    """Task 13 review I7: the bridge's debris starts all along the break box (DEBRIS_PER in every DEBRIS_SLICE
    blocks of x) and its dust bursts cover the span (one within DUST_EVERY / 2 of every x), on the crash tick.
    Returns (the fewest in a slice, the farthest x from a burst)."""
    sm = next(x for x in sc.smashes if x['label'] == 'the bridge')
    T = sm['tick']
    lo, hi = sm['box']
    xs = [E.track_sample(E.actor_by_id(scene, did)['path'], T)[0] for did in sm['debris']]
    fewest = 99
    x = lo[0]
    while x < hi[0] - 1e-6:
        n = sum(1 for q in xs if x <= q < x + DEBRIS_SLICE)
        fewest = min(fewest, n)
        if n < DEBRIS_PER:
            problems.append('%s: only %d debris start in x %.0f..%.0f of the span' % (sc.sid, n, x, x + DEBRIS_SLICE))
            break
        x += DEBRIS_SLICE
    dust = [e['at'][0] for e in scene['events'] if e.get('kind') == 'particles' and T <= e['t'] <= T + 2
            and lo[2] - 3 <= e['at'][2] <= hi[2] + 3]
    far = max(min([abs(x_ - q) for q in dust] or [99.0])
              for x_ in [lo[0] + 0.5 * k for k in range(int((hi[0] - lo[0]) * 2) + 1)])
    if far > DUST_EVERY / 2.0 + 1e-6:
        problems.append('%s: a stretch of the span %.1f from any dust burst' % (sc.sid, far))
    return fewest, round(far, 2)


HIT_SLACK = 0.15                                 # seconds


def check_music_hits(sc, scene, problems):
    """Task 13 review I6: the music cue's measured hits (first_task_music.HITS) land on their beats: each hit's
    scene tick (the music event's t plus its file time) within HIT_SLACK of the sfx it is named for. Returns
    [(sfx, offset seconds)]."""
    out = []
    for e in scene['events']:
        if e.get('kind') != 'music':
            continue
        cue = e['id'].split('.')[-1]
        for sec, sfx in M.HITS.get(cue, []):
            t_hit = e['t'] + sec * 20.0
            ts = [x['t'] for x in scene['events'] if x.get('id') == K.SFX + sfx]
            off = min([(x - t_hit) / 20.0 for x in ts], key=abs) if ts else None
            out.append((sfx, None if off is None else round(off, 2)))
            if off is None or abs(off) > HIT_SLACK:
                problems.append('%s: the hit at %.2f s of %s is %s s off its %s' % (sc.sid, sec, cue, off, sfx))
    return out


def check_lick(sc, scene, problems):
    """Task 13 review I1: the second air breath's drawn flame licks his broom's tail within LICK[1] (assert 12
    keeps it 1.5 off him). Returns the least gap."""
    a = E.actor_by_id(scene, 'horntail')
    tb = sc.notes['T']['air2']
    best = 9.0
    for t0, dur, _ in D.breathes(a):
        if t0 != tb:
            continue
        for q in range(int(t0) * 2, int(t0 + dur) * 2 + 1):
            t = q / 2.0
            jet = D.fire_jet(scene, 'horntail', t, sc.world)
            if jet is None:
                continue
            g = K.fire_gap((jet[0], jet[1], jet[2], a.get('scale', 1.0), []), broom_tail(scene, 'firebolt', t))
            best = min(best, g)
    if best > LICK[1]:
        problems.append('%s: the second air breath comes only %.2f from his broom (needs %.1f)' % (sc.sid, best,
                                                                                               LICK[1]))
    return round(best, 2)


def chase_checks():
    return [('bite', check_bite), ('scorch kept', check_scorch_kept), ('chase gap', check_chase_gap),
            ('glide', check_glide), ('debris span', check_debris_span), ('lick', check_lick)]


# ================================================================ the egg: first_task_egg
LE_ = 16.0
TE = {'see': (100, 144), 'dive': 144, 'reach': (168, 196), 'grab': 200, 'raise': (208, 238), 'cannon': 206,
      'comm': 214, 'close': 280}
M_EGG = 0                                        # the egg cue: its file's onsets at 5.1 and 7.2 s (round the west end,
                                                 # the dive) and its biggest hit at 10.0 (the grab)
EGG_LOOP = [[-50.0, 54.0, 69.0], [-78.0, 46.0, 63.0], [-106.0, 38.0, 61.0], [-128.0, 31.0, 66.0],
            [-136.5, 26.0, 76.0]]                # over the east stand, west along the north side, round the west end
EGG_DIVE = [[-131.0, 12.0, 80.6], [-125.0, 1.0, 81.3], [-118.0, -4.3, 81.5], [-110.0, -6.0, 81.5]]
EGG_OUT = [[-92.0, -6.3, 81.45], [-85.0, -4.4, 81.6], [-78.0, 4.0, 84.5], [-74.0, 12.0, 93.0], [-82.0, 19.0, 101.0],
           [-98.0, 23.5, 101.5], [-114.0, 26.0, 97.0]]
GRAB_DIR = [-0.745, -0.65, 0.12]                 # his right arm down and out to the egg (broom frame: x left): the
                                                 # hand 0.62 out, his broom's side 0.07 off the prize rock's face
RAISE_DIR = [-0.25, 1.0, 0.2]                    # and up over his head with it
V_GRAB = 0.7
E1_CAM = [-46.0, 47.5, 52.5]
E2_CAM = [-113.0, 9.0, 65.0]                     # 27 from the egg, high over the north-west of the bowl
E3_CAM = [-86.5, -5.0, 79.0]
E5_CAM = [-84.0, 14.0, 70.0]                     # 21 from the east stand's front rows
E5_LOOK = [-56.0, 30.0, 82.0]
E5_CUT, E6_CUT = 212, 258                        # the first person to the cutaway (the erupt + 10), the cutaway to E6
E6_CAM = [-108.0, 34.0, 50.0]


def egg_route(sc, start, v_start, g):
    """His flight as fn(t) through the grab point g (the broom there at TE['grab']): one curve from where the chase
    leaves him, over the east stand, round the north side and the west end, down the dive into the bowl, the dip
    past the prize rock and the climb out round to the south stand; two corner runs joined at the grab."""
    ahead = add(start, v_start, 6.0)
    pts = [start, ahead] + EGG_LOOP + EGG_DIVE + [g] + EGG_OUT
    i_g = 2 + len(EGG_LOOP) + len(EGG_DIVE)
    at, length, ks = by_length(pts, knots=True)
    s_g = ks[i_g]
    v0 = math.sqrt(sum(c * c for c in v_start))
    fa, va = corner_run(at, 0.0, s_g, 0, TE['grab'], v0, V_GRAB, a_lat=0.06, a_t=0.015)
    fb, vb = corner_run(at, s_g, length, TE['grab'], sc.L, V_GRAB, 0.55, a_lat=0.05, a_t=0.012)
    sc.notes['egg_speeds'] = [(round(va, 3), round(fa.scale, 3)), (round(vb, 3), round(fb.scale, 3))]

    def f(t):
        return at(fa(t) if t <= TE['grab'] else fb(t))
    return f


def egg_scene(world, solid, chase):
    sc = K.FSc('first_task_egg', 'The Golden Egg', LE_, world)
    sc.poses = {'standing': {}}
    T = dict(TE)
    sc.notes['T'] = T
    sc.notes['solid'] = solid
    stands_crowd(sc)
    Vc = chase
    fb = E.actor_by_id(Vc, 'firebolt')
    L0 = Vc['length']
    s0, s1 = E.track_sample(fb['path'], L0 - 1), E.track_sample(fb['path'], L0)
    start, v_start = s1, sub(s1, s0)
    yaw0 = E.broom_pose(Vc, fb, L0)['yaw']
    TG = T['grab']
    sc.actor({'id': 'golden_egg', 'type': EGG, 'scale': CHAMP,
              'path': [{'t': 0, 'pos': r4(EGG_AT)}, {'t': TG - 0.25, 'pos': r4(EGG_AT), 'ease': 'linear'},
                       {'t': TG, 'pos': r4(PARK), 'ease': 'linear'}]})
    egg_chain(sc, Vc)
    # ---- the champion on the Firebolt: the grab point solved so his reaching hand closes on the egg
    rg = Rig()
    g = [EGG_AT[0] - 0.2, EGG_AT[1] - 0.18, EGG_AT[2] - 0.62]
    b = v = None
    for _ in range(4):
        sc.actors = [a for a in sc.actors if a['id'] not in ('firebolt', 'viewer')]
        route = egg_route(sc, start, v_start, g)
        b = F.Broom(sc, 'firebolt', start, yaw0, btype=FIREBOLT, roll_keys=False)
        for t in range(2, sc.L + 1, 2):
            b.path.at(t, route(t), 'spline')
        F.flight_look(b, 0, sc.L, b.yaw(), step=4)
        b.sync()
        v = sc.actor({'id': 'viewer', 'type': 'player', 'scale': CHAMP, 'skin': {'viewer': True},
                      'items': {'chest': ROBES, 'mainhand': WAND}, 'ride': 'firebolt',
                      'path': [{'t': 0, 'pos': r4(E.rider_seat(CHAMP))}], 'look': [{'t': 0, 'yaw': 0.0, 'pitch': 0.0}]})
        rg = Rig()
        F.refresh(v, rg)
        r0, r1 = T['reach']
        V = K.view(sc)
        res = F.reach(sc, v, rg, 'right_arm', r0, r1, F.arm_target(V, v, 'firebolt', r1, GRAB_DIR))
        assert res < 0.05, res
        rg.r('right_arm').to(r1, TG, rg.r('right_arm').v())
        F.refresh(v, rg)
        H = F.hand(K.view(sc), v, 'right_arm', TG)
        err = sub(EGG_AT, H)
        if math.sqrt(sum(c * c for c in err)) < 0.01:
            break
        g = add(g, err)
    sc.notes['grab_point'] = r3(g)
    sc.notes['grab_gap'] = round(math.dist(F.hand(K.view(sc), v, 'right_arm', TG), EGG_AT), 4)
    # with it: the egg up over his head to the stands, held there; the wand gone into his robes
    V = K.view(sc)
    a0, a1 = T['raise']
    res = F.reach(sc, v, rg, 'right_arm', a0, a1, F.arm_target(V, v, 'firebolt', a1, RAISE_DIR))
    assert res < 0.06, res
    rg.r('right_arm').to(a1, sc.L, rg.r('right_arm').v())
    F.refresh(v, rg)
    v['actions'] = [{'t': TG, 'do': 'hold', 'item': EGG_PROP}]
    sc.grab = {'tick': TG, 'egg': 'golden_egg', 'rider': 'viewer', 'broom': 'firebolt', 'side': 'right_arm'}
    sc.notes['nest_dive'] = [('firebolt', TG - 14, TG + 12)]
    sc.flier_ok['firebolt'] = [(TG - 4, TG + 7)]          # his broom skims the prize rock's north face as he takes it
    sc.notes['route'] = route
    egg_rider_look(sc, v, T)
    egg_camera(sc, T)
    egg_sound(sc, T)
    sc.ev(T['close'], 'effect', 'eyes_close', at=[0, 0, 0], duration=40)
    scene = sc.scene(GROUNDS_SET, FG.RADIUS, end_action='wake', fog_far=FG.FOG_FAR)
    sc.notes['extra_checks'] = egg_checks()
    return sc, scene


def egg_chain(sc, chase):
    """Her broken chain where it fell in the chase, each link lying still."""
    for a in chase['actors']:
        if a.get('block') == CHAIN and a['id'].startswith('chain_'):
            L = chase['length']
            yw, pi = E.own_sample(a, 'look', L)[:2]
            sc.actor({'id': a['id'], 'type': a['type'], 'block': CHAIN, 'scale': a.get('scale', 1.0),
                      'path': [{'t': 0, 'pos': r4(E.track_sample(a['path'], L))}],
                      'look': [{'t': 0, 'yaw': round(yw, 2), 'pitch': round(pi, 2)}]})


def egg_rider_look(sc, v, T):
    """His head: ahead over the stands, down at the egg as he rounds the west end and dives on it, on it into his
    hand, then up at it held high and round at the stands."""
    V = K.view(sc)
    egg = lambda t: EGG_AT
    look = [{'t': 0, 'yaw': 0.0, 'pitch': 0.0}]
    look.append({'t': 60, 'yaw': -10.0, 'pitch': 4.0})
    F.aim_rider(sc, 'viewer', look, T['see'][0] - 10, T['grab'] - 2, egg, step=8, max_pitch=70.0)
    look.append({'t': T['grab'] + 8, 'yaw': 10.0, 'pitch': -10.0})
    look.append({'t': T['raise'][1], 'yaw': 18.0, 'pitch': -32.0})
    look.append({'t': T['raise'][1] + 30, 'yaw': 40.0, 'pitch': -8.0})
    look.append({'t': sc.L, 'yaw': 50.0, 'pitch': -4.0})
    v['look'] = look


def egg_sound(sc, T):
    TG = T['grab']
    sc.ev(0, 'effect', 'sky', at=r3(GA['nest_centre']), style='overcast')
    sc.music(M_EGG, 'first_task_egg')
    sc.sfx(0, 'first_task_crowd_bed', 0.5, loop=True, duration=TG)
    V = K.view(sc)
    # the pass over the east stand: a cheer there, the rest a beat later
    box = sc.crowd_boxes['east']
    tp = min(range(0, 80), key=lambda t: math.dist(broom_at(sc, t), box[0]))
    sc.passes.append((tp, 'east', 'the Firebolt back over the east stand'))
    sc.notes['pass_tick'] = tp
    sc.sfx(tp + 1, 'flying_crowd_swell', 0.9)
    crowd_all(sc, tp + 2, 'cheer', ('pass', tp), duration=70, order=('east',))
    crowd_all(sc, tp + 6, 'cheer', ('pass', tp), duration=60, order=('north', 'south', 'west'), stagger=2)
    # the grab: the egg into his hand, the stands erupt, the cannons from the south stand, the commentator
    sc.sfx(TG, 'first_task_egg_grab', 1.0, hit=TG, label='the hand closes on the egg')
    sc.sfx(TG + 1, 'flying_crowd_erupt', 1.0)
    crowd_all(sc, TG + 2, 'erupt', ('grab', TG), duration=100, stagger=1)
    tc = T['cannon']
    sc.sfx(tc, 'first_task_cannon', 1.0)
    for cs in GA['cannon_spots']:
        sc.ev(tc, 'particles', 'minecraft:large_smoke', at=r3(cs['smoke']), count=40, spread=[0.6, 0.6, 1.2])
        sc.ev(tc, 'particles', 'minecraft:poof', at=r3(cs['smoke']), count=20, spread=[0.4, 0.4, 0.8])
    sc.cannons.append((tc, 'the cannons after the grab', True))
    sc.say(T['comm'], 'first_task_comm_egg')
    commentator(sc, [(T['comm'], 'first_task_comm_egg')])
    sc.sfx(TG + 30, 'first_task_crowd_bed', 0.65, loop=True)
    K.whoosh(sc, 'firebolt', 0, 70)
    K.whoosh(sc, 'firebolt', 150, 186)
    K.whoosh(sc, 'firebolt', 212, 262, 0.8)


def egg_camera(sc, T):
    V = K.view(sc)
    boy = lambda t: E.rider_eye(V, E.actor_by_id(V, 'viewer'), t)
    TG = T['grab']
    L = sc.L
    # E1 0-5.0 behind the east stand's back rows: he sweeps in over the stand past us and away over the ravine
    pf = move(E1_CAM, add(E1_CAM, [-1.0, 0.4, 0.6]), 0, 100)
    sc.shot(0, 100, pf, smoothed(boy, 6.0), ramp(66.0, 60.0, 0, 100), 4)
    # E2 5.0-7.2 a low crane over the north-west of the bowl, 27 from the egg (Task 13 review M11): the golden egg
    # glints on its rock among her black eggs as he rounds the west end high behind us
    pf = move(E2_CAM, add(E2_CAM, [0.5, -0.4, 0.4]), 100, T['dive'])
    sc.shot(100, T['dive'], pf, move(add(EGG_AT, [0.0, 0.6, 0.0]), add(EGG_AT, [-0.3, 0.3, 0.0]), 100, T['dive']),
            ramp(48.0, 42.0, 100, T['dive']), 4)
    # E3 7.2-9.1 low on the shelf at the egg's back: he dives at us out of the sky straight at the egg on its rock
    pf = move(E3_CAM, add(E3_CAM, [0.25, 0.05, 0.0]), T['dive'], 182)
    sc.shot(T['dive'], 182, pf, smoothed(lambda t: lerp(EGG_AT, boy(t), 0.35), 4.0), ramp(62.0, 54.0, T['dive'], 182), 4)
    # E4 9.1-10.6 FIRST PERSON: down the dive, his hand out, the egg rushing up; it is in his hand, the roar breaks
    eye_v = lambda t: F.head_eye(V, E.actor_by_id(V, 'viewer'), t, 0.1)
    def aim(t):
        e = eye_v(t)
        down = add(e, [6.0, -2.6, 0.4])                    # ahead and down along the dive, the egg in it
        fwd = add(e, [8.0, 0.6, 0.0])
        q = lerp(lerp(EGG_AT, down, smooth((t - 184) / 12.0)), fwd, smooth((t - TG + 2) / 26.0))
        return add(e, norm(sub(q, e)), 4.0)
    F.first_person(sc, V, 'viewer', 182, E5_CUT, fwd=0.1, fov=74.0, look_fn=smoothed(aim, 6.0), sigma=5.0)
    # E5 10.6-12.9 the cutaway (Task 13 review I9): close on the east stand as it erupts, the crowd on its feet, 20
    # from its front rows
    pf = move(E5_CAM, add(E5_CAM, [0.4, 0.3, 0.6]), E5_CUT, E6_CUT)
    sc.shot(E5_CUT, E6_CUT, pf, move(E5_LOOK, add(E5_LOOK, [0.0, 1.0, 2.0]), E5_CUT, E6_CUT),
            ramp(54.0, 52.0, E5_CUT, E6_CUT), 4)
    sc.notes['crowd_shots'] = [(E5_CUT + 2, E6_CUT - 2, 'the east stand erupting (E5)')]
    # E6 12.9-16.0 wide from the north stand: he flies the egg held high along the cheering south stand, the eyes
    # close
    pf = move(E6_CAM, add(E6_CAM, [-2.0, 0.5, 1.5]), E6_CUT, L)
    sc.shot(E6_CUT, L, pf, smoothed(boy, 8.0), ramp(52.0, 48.0, E6_CUT, L), 4)
    sc.notes['softened'] = K.soften_shots(sc)
    sc.notes['tamed'] = K.tame_shot_ends(sc)
    E_ = lambda sc_, t: E.rider_eye(sc_, E.actor_by_id(sc_, 'viewer'), t)
    egg = lambda sc_, t: list(EGG_AT)
    sc.framing += [(20, 96, E_, 'the champion over the east stand (E1)'), (104, 140, egg, 'the egg (E2)'),
                   (148, 180, E_, 'the champion diving (E3)'), (148, 180, egg, 'the egg on its rock (E3)'),
                   (186, 199, egg, 'the egg in his own eyes (E4)'), (E6_CUT + 6, L - 4, E_, 'the champion and the egg (E6)')]
    lo, hi = P.egg_box(CHAMP)
    sc.notes['seen'] = [(egg, 100, T['dive'], 16, 'the golden egg before the dive (E2)', 60.0, hi[1] - lo[1], EGG_SHARE)]


EGG_SHARE = 0.009                                # the egg fills at least this share of the frame's height in E2


def egg_checks():
    def solid_of(sc):
        return sc.notes['solid']
    return [('egg', lambda sc, s, P_: K.check_egg(sc, s, P_)),
            ('dive over', lambda sc, s, P_: K.check_dive_over(sc, s, P_, solid_of(sc))),
            ('backslide', lambda sc, s, P_: K.check_backslide(sc, s, P_, ['firebolt'])),
            ('fliers', lambda sc, s, P_: K.check_fliers(sc, s, P_, sc.cam_world, [])),
            ('seen', lambda sc, s, P_: [K.check_seen(sc, s, P_, sc.world, *x) for x in sc.notes.get('seen', [])]),
            ('crowd shots', K.check_crowd_shots)]


# ================================================================ the checks, per scene

def run_checks(sc, scene, world, solid=None, dragon=None):
    """Every assert on one scene: {name: worst number}, and 'problems'."""
    P_ = []
    out = {}
    if not hasattr(sc, 'cam_world'):
        sc.cam_world = F.cam_world(world)
    out['spline runs'] = E.check_spline_moves(sc.sid, scene, P_)
    m = E.check_camera_smoothness(sc.sid, scene, P_)
    out['smooth'] = 'a %.5f aw %.4f ja %.5f jw %.4f' % (max(s['a'] for s in m.values()), max(s['aw'] for s in m.values()),
                                                        max(s['ja'] for s in m.values()), max(s['jw'] for s in m.values()))
    out['shortest shot'] = round(min((s['t1'] - s['t0']) / 20.0 for s in m.values()), 2)
    out['air points'] = F.check_camera_air(sc, scene, P_)
    cs = F.check_contacts(sc, scene, P_)
    out['contacts'] = ', '.join('%s %.3f' % (l.split(':')[0], g) for l, g in cs) or '-'
    out['items'] = F.check_items(sc, scene, P_)
    out['rides'] = F.check_rides(sc, scene, P_)
    out['yaw rate'] = F.check_yaw_rates(sc, scene, P_)
    K.check_lines(sc, scene, P_)
    F.check_keys(sc, scene, P_)
    out['walkers'] = K.check_walkers(sc, scene, P_, world)
    out['framing'] = F.check_framing(sc, scene, P_)
    out['ball speed'] = E.check_ball_speeds(sc.sid, scene, P_)
    out['sfx'] = F.check_sfx_hits(sc, scene, P_)
    out['reactions'] = K.check_reactions(sc, scene, P_)
    for name, fn in sc.notes.get('extra_checks', []):
        out[name] = fn(sc, scene, P_)
    out['clearance'] = F.check_clearance(sc, scene, P_)
    out['egg camera'] = K.check_egg_camera(sc, scene, P_)
    out['cannons'] = K.check_cannons(sc, scene, P_, [c['smoke'] for c in GA['cannon_spots']])
    out['music hits'] = check_music_hits(sc, scene, P_)
    if any(e.get('id') == 'crowd' for e in scene['events']):
        out['crowd gap'] = F.check_crowd_gap(sc, scene, P_)
    out['problems'] = P_
    return out


def tent_world():
    w = {}
    FT.build(w)
    return w


SCENE_ORDER = ['first_task', 'first_task_arena', 'first_task_chase', 'first_task_egg']
MAX_JSON = F.MAX_JSON
TENT_CAP = 100000
BUILD_PER_TICK = 4000                            # SetBuilder's blocks a tick


def build_all(worlds):
    """All four scenes, in order (each starts where the one before leaves off): {id: (sc, scene, world)}."""
    out = {}
    tw = worlds['tent']
    sc, s = tent_scene(tw)
    sc.cam_world = F.cam_world(tw)
    out[sc.sid] = (sc, s, tw)
    gw, solid = worlds['grounds'], worlds['solid']
    cw = F.cam_world(gw)
    sc, s = arena_scene(gw, solid)
    sc.cam_world = cw
    out[sc.sid] = (sc, s, gw)
    sc, _, _, _ = chase_scene(gw, solid, s)
    s = sc.scene(GROUNDS_SET, FG.RADIUS, nxt='first_task_egg', fog_far=FG.FOG_FAR)
    sc.cam_world = cw
    out[sc.sid] = (sc, s, gw)
    sc, s = egg_scene(gw, solid, s)
    sc.cam_world = cw
    out[sc.sid] = (sc, s, gw)
    return out


def check_mirrors(problems, extra=''):
    """Assert 1: neither this generator nor its kit defines a function of scene_engine's or dragon_engine's own
    names (a private copy of a mirror). Returns the number of functions looked at."""
    import re
    names = ({n for n in dir(E) if callable(getattr(E, n)) and not n.startswith('_')} |
             {n for n in dir(D) if callable(getattr(D, n)) and not n.startswith('_')})
    n = 0
    for path in (os.path.abspath(__file__), os.path.join(HERE, 'first_task_kit.py')):
        src = open(path, encoding='utf-8').read() + (extra if path.endswith('first_task_scene.py') else '')
        own = set(re.findall(r'^def ([a-z_0-9]+)\(', src, re.M))
        n += len(own)
        clash = sorted(own & names)
        if clash:
            problems.append('%s defines private copies of engine mirrors: %s' % (os.path.basename(path), ', '.join(clash)))
    return n


def check_chain_wiring(scenes, problems):
    """Assert 10, wiring: the four scenes exist and hand over in order, the tent in white; the first opens on
    eyes_open at t 0 and the last ends in a wake on shut lids; no next anywhere else."""
    for i, sid in enumerate(SCENE_ORDER):
        s = scenes.get(sid)
        if s is None:
            problems.append('%s is missing' % sid)
            continue
        want = SCENE_ORDER[i + 1] if i < 3 else None
        if s.get('next') != want:
            problems.append('%s hands over to %s, not %s' % (sid, s.get('next'), want))
        if (s.get('end_action') == 'wake') != (i == 3):
            problems.append('%s end_action %s' % (sid, s.get('end_action')))
        if (s.get('handover_colour') == 'white') != (i == 0):
            problems.append('%s handover_colour %s' % (sid, s.get('handover_colour')))
    first, last = scenes.get(SCENE_ORDER[0]), scenes.get(SCENE_ORDER[3])
    if first and not any(e['id'] == 'eyes_open' and e['t'] == 0 for e in first['events']):
        problems.append('%s does not open on eyes_open at t 0' % SCENE_ORDER[0])
    if last and not any(e['id'] == 'eyes_close' and e['t'] + e.get('duration', 40) <= last['length']
                        for e in last['events']):
        problems.append('%s does not end on shut lids' % SCENE_ORDER[3])


def set_counts():
    tent = sum(1 for v in template_grid('first_task_tent').values() if not v.startswith('minecraft:air'))
    grounds = sum(1 for v in template_grid('first_task_grounds').values() if not v.startswith('minecraft:air'))
    castle = sum(1 for v in FG.CASTLE.values() if not v.startswith('minecraft:air'))
    return {'tent': tent, 'grounds': grounds, 'castle': castle}


def check_budget(scenes, problems, counts):
    """Assert 16: the tent's set at most 100,000 blocks; the grounds set (template and castle) is built as a standby
    during the scene before each grounds scene, which lasts at least twice its build ticks, and each grounds set
    declares those ticks. Returns (tent blocks, grounds blocks, build ticks)."""
    g = counts['grounds'] + counts['castle']
    ticks = int(math.ceil(g / float(BUILD_PER_TICK)))
    if counts['tent'] > TENT_CAP:
        problems.append('the tent set is %d blocks (at most %d)' % (counts['tent'], TENT_CAP))
    for i, sid in enumerate(SCENE_ORDER[1:]):
        s, before = scenes[sid], scenes[SCENE_ORDER[i]]
        if s['set'].get('build_ticks') != ticks:
            problems.append('%s declares %s build ticks, its set needs %d' % (sid, s['set'].get('build_ticks'), ticks))
        if before['length'] < 2 * ticks:
            problems.append('%s lasts %d ticks, under twice the grounds build (2 x %d) for %s' % (
                SCENE_ORDER[i], before['length'], ticks, sid))
    return counts['tent'], g, ticks


def check_size(sid, txt, problems):
    n, b = len(txt), len(txt.encode('utf-8'))
    if n > MAX_JSON or b >= 1000000:
        problems.append('%s is %d characters, %d bytes' % (sid, n, b))
    ids = [a['id'] for a in json.loads(txt)['actors']]
    if len(ids) != len(set(ids)):
        problems.append('%s has duplicate actor ids' % sid)
    return n


def bites(built, counts):
    """Each assert shown to bite: one mutation of the emitted JSON (or of the notes it is checked against) per
    assert, which must give it a problem. Returns [(assert, problems the mutation raised, the first)]."""
    out = []
    A_, C_, G_, T_ = 'first_task_arena', 'first_task_chase', 'first_task_egg', 'first_task'

    def bite(name, sid, mutate, run):
        sc, s, w = built[sid]
        m = copy.deepcopy(s)
        sc2 = copy.copy(sc)
        sc2.notes = dict(sc.notes)
        for f in ('reactions', 'sfx_hits', 'fire_hits', 'cannons', 'smashes', 'dragon_ok', 'near_misses', 'passes',
                  'framing', 'lines'):
            setattr(sc2, f, list(getattr(sc, f)))
        mutate(m, sc2)
        P_ = []
        run(sc2, m, P_)
        out.append((name, len(P_), P_[0] if P_ else None))
        assert P_, 'the %s assert did not bite' % name

    def keys_near(keys, t):
        return min(keys, key=lambda k: abs(k['t'] - t))

    # 1 mirrors
    P_ = []
    check_mirrors(P_, extra=chr(10) + 'def track_sample(keys, t):' + chr(10) + '    pass' + chr(10))
    out.append(('1 mirrors (a private track_sample)', len(P_), P_[0] if P_ else None))
    assert P_

    def run_keys(m, sc):
        for k in m['camera'][5:8]:
            k['ease'] = 'linear'
    bite('2 spline moves (three linear camera keys)', A_, run_keys, lambda sc, m, P: E.check_spline_moves(sc.sid, m, P))

    def kink(m, sc):
        k = m['camera'][6]
        k['pos'] = [k['pos'][0] + 0.4] + k['pos'][1:]
    bite('3 smoothness (a camera key 0.4 off its line)', A_, kink, lambda sc, m, P: E.check_camera_smoothness(sc.sid, m, P))

    def into_head(m, sc):
        k = next(k for k in m['camera'] if k['t'] >= 130 and not k.get('cut'))
        k['pos'] = r4(F.head_eye(m, E.actor_by_id(m, 'viewer'), k['t'], 0.0))
    bite('4 clearance (a camera key in the champion\'s head)', A_, into_head, lambda sc, m, P: F.check_clearance(sc, m, P))

    def into_ground(m, sc):
        k = next(k for k in m['camera'] if k['t'] >= 210 and not k.get('cut'))
        k['pos'] = [k['pos'][0], GROUND_Y - 1.5, k['pos'][2]]
    bite('4 camera air (a key under the arena floor)', A_, into_ground, lambda sc, m, P: F.check_camera_air(sc, m, P))

    def drift_broom(m, sc):
        b = E.actor_by_id(m, 'firebolt')
        k = next(k for k in b['path'] if abs(k['t'] - sc.notes['T']['catch']) < 1e-6)
        k['pos'] = [k['pos'][0] + 0.4] + k['pos'][1:]
    bite('5 hand contacts (the Firebolt 0.4 off his hand)', A_, drift_broom, lambda sc, m, P: F.check_contacts(sc, m, P))

    def jolt_ride(m, sc):
        a = E.actor_by_id(m, 'viewer')
        tm = a['ride']['from']
        k = next(k for k in a['path'] if abs(k['t'] - (tm - 0.001)) < 1e-6)
        k['pos'] = [k['pos'][0] + 0.1] + k['pos'][1:]
    bite('9 ride windows (he mounts 0.1 off the seat)', A_, jolt_ride, lambda sc, m, P: F.check_rides(sc, m, P))

    def late_erupt(m, sc):
        sc.reactions = [(t + 8 if c[0] == 'grab' else t, st, hh, c) for t, st, hh, c in sc.reactions]
    bite('15 crowd timing (the erupt 0.4 s late)', G_, late_erupt, lambda sc, m, P: K.check_reactions(sc, m, P))

    def no_gasp(m, sc):
        sc.reactions = [r for r in sc.reactions if r[1] != 'gasp']
        m['events'] = [e for e in m['events'] if not (e.get('id') == 'crowd_react' and e.get('style') == 'gasp')]
    bite('15 a gasp after each near miss (the gasps gone)', A_, no_gasp, lambda sc, m, P: K.check_reactions(sc, m, P))

    def late_roar(m, sc):
        sc.sfx_hits = [(n, t + (4 if n == 'first_task_horntail_roar' else 0), hit, lab) for n, t, hit, lab in sc.sfx_hits]
    bite('7 sound timing (the roar 4 ticks late)', A_, late_roar, lambda sc, m, P: F.check_sfx_hits(sc, m, P))

    def into_stand(m, sc):
        b = E.actor_by_id(m, 'firebolt')
        k = next(k for k in b['path'] if k['t'] >= 40)
        k['pos'] = [-50.0, 30.0, 80.0]
    bite('8 fliers (the Firebolt into the east stand)', G_, into_stand, lambda sc, m, P: K.check_fliers(sc, m, P, sc.cam_world, []))

    def low_dive(m, sc):
        b = E.actor_by_id(m, 'firebolt')
        tg = sc.notes['T']['grab']
        for k in b['path']:
            if tg - 14 <= k['t'] <= tg + 12:
                k['pos'] = [k['pos'][0], k['pos'][1] - 1.0, k['pos'][2]]
    bite('8 the nest dive (1.0 lower)', G_, low_dive, lambda sc, m, P: K.check_dive_over(sc, m, P, sc.notes['solid']))

    def miswire(m, sc):
        m['next'] = G_

    def wiring(sc, m, P):
        sc_all = {k: v[1] for k, v in built.items()}
        sc_all[A_] = m
        check_chain_wiring(sc_all, P)
    bite('10 wiring (the arena hands over to the egg)', A_, miswire, wiring)

    def no_white(m, sc):
        m.pop('handover_colour', None)

    def wiring_t(sc, m, P):
        sc_all = {k: v[1] for k, v in built.items()}
        sc_all[T_] = m
        check_chain_wiring(sc_all, P)
    bite('10 wiring (the tent hands over without white)', T_, no_white, wiring_t)

    def fat(m, sc):
        m['actors'].append(copy.deepcopy(m['actors'][0]))

    def size_run(sc, m, P):
        check_size(sc.sid, json.dumps(m), P)
    bite('10 sizes and ids (a duplicate actor)', G_, fat, size_run)

    def named(m, sc):
        m['words'].append({'t': 5, 'text': 'Well done, Harry!', 'subtitle': True, 'speaker': 'Commentator', 'duration': 20})
    bite('10 no line names the player (Harry in a subtitle)', G_, named, lambda sc, m, P: K.check_lines(sc, m, P))

    def into_rock(m, sc):
        a = E.actor_by_id(m, 'horntail')
        k = keys_near(a['path'], 60)
        k['pos'] = [k['pos'][0], k['pos'][1] - 2.0, k['pos'][2]]
    bite('11 dragon blocks (perched 2 into her shelf)', A_, into_rock, lambda sc, m, P: K.check_dragon_blocks(sc, m, P, 'horntail', sc.notes['solid']))

    def jolt_her(m, sc):
        a = E.actor_by_id(m, 'horntail')
        k = keys_near(a['path'], 620)
        k['pos'] = [k['pos'][0] + 2.0] + k['pos'][1:]
    bite('11 dragon motion (a flight key 2 off her line)', C_, jolt_her, lambda sc, m, P: K.check_dragon_motion(sc, m, P, 'horntail'))

    def fly_perched(m, sc):
        a = E.actor_by_id(m, 'horntail')
        a['anims'] = sorted(a['anims'] + [{'t': 40, 'name': 'FLY'}, {'t': 80, 'name': 'IDLE'}], key=lambda c: c['t'])
    bite('11 cues match her path (FLY while perched)', A_, fly_perched, lambda sc, m, P: K.check_dragon_motion(sc, m, P, 'horntail'))

    def spin_her(m, sc):
        a = E.actor_by_id(m, 'horntail')
        k = keys_near(a['look'], 600)
        k['yaw'] += 90.0
    bite('11 her yaw rate (a look key turned 90)', C_, spin_her, lambda sc, m, P: K.check_dragon_motion(sc, m, P, 'horntail'))

    def early_breath(m, sc):
        a = E.actor_by_id(m, 'horntail')
        a['actions'] = sorted(a['actions'] + [{'t': 60, 'do': 'breathe', 'duration': 10}], key=lambda x: x['t'])
    bite('12 every breathe in a fire window (one on the perch)', A_, early_breath, lambda sc, m, P: K.check_fire(sc, m, P, 'horntail', sc.world))

    def miss_rock(m, sc):
        sc.fire_hits = [(t0, t1, lab, [[b[0][0], b[0][1] + 30, b[0][2]], [b[1][0], b[1][1] + 30, b[1][2]]])
                        for t0, t1, lab, b in sc.fire_hits]
    bite('12 the scripted hits land (the rock face 30 up)', A_, miss_rock, lambda sc, m, P: K.check_fire(sc, m, P, 'horntail', sc.world))

    def burn_him(m, sc):
        a = E.actor_by_id(m, 'viewer')
        b = sc.notes['breaths'][1]
        q = [FACE_AIM[0] + 2.5, GROUND_Y, FACE_AIM[2]]                   # out in front of the rock, in the jet
        a['path'] = sorted([k for k in a['path'] if not b - 6 <= k['t'] <= b + 42] +
                           [{'t': b - 5, 'pos': q, 'ease': 'linear'}, {'t': b + 41, 'pos': q, 'ease': 'linear'}],
                           key=lambda k: k['t'])

    def burn_run(sc, m, P):
        Q = []
        K.check_fire(sc, m, Q, 'horntail', sc.world)
        P.extend(q for q in Q if 'from the viewer' in q)
    bite('12 the jet clear of him (the champion out in front of the rock)', A_, burn_him, burn_run)

    def no_roar(m, sc):
        m['events'] = [e for e in m['events'] if e.get('id') != K.SFX + 'first_task_fire_roar']
    bite('12 a fire roar on each breathe (the roars gone)', A_, no_roar, lambda sc, m, P: K.check_fire(sc, m, P, 'horntail', sc.world))

    def late_beat(m, sc):
        for e in m['events']:
            if e.get('id') == K.SFX + 'first_task_wingbeat':
                e['t'] += 3
                break
    bite('13 wingbeats (one 3 ticks late)', C_, late_beat, lambda sc, m, P: K.check_wingbeats(sc, m, P, 'horntail'))

    def late_bridge(m, sc):
        tc = sc.notes['T']['crash_tick']
        for e in m['events']:
            if e.get('kind') == 'block' and abs(e['t'] - tc) < 1e-6:
                e['t'] = tc + 4
    bite('14 the bridge breaks on her contact (its blocks 4 late)', C_, late_bridge, lambda sc, m, P: K.check_smashes(sc, m, P, 'horntail'))

    def early_smash(m, sc):
        sc.smashes = [dict(x, tick=x['tick'] - 6) for x in sc.smashes]
    bite('14 the smash tick is her first contact (6 early)', C_, early_smash, lambda sc, m, P: K.check_smashes(sc, m, P, 'horntail'))

    def loose_link(m, sc):
        a = max((a for a in m['actors'] if a['id'].startswith('chain_')), key=lambda a: a['id'])   # the collar link
        k = keys_near(a['path'], 100)
        k['pos'] = [k['pos'][0], k['pos'][1] + 0.6, k['pos'][2]]
    bite('14 the chain on her collar (the collar link 0.6 off)', A_, loose_link, lambda sc, m, P: K.check_chain(sc, m, P, 'horntail'))

    def no_smoke(m, sc):
        m['events'] = [e for e in m['events'] if not (e.get('kind') == 'particles'
                                                      and abs(e['t'] - sc.notes['T']['cannon']) < 1e-6)]
    bite('15 the cannons smoke (the muzzle smoke gone)', G_, no_smoke,
         lambda sc, m, P: K.check_cannons(sc, m, P, [c['smoke'] for c in GA['cannon_spots']]))

    def short_tent(m, sc):
        m['length'] = 100

    def budget_run(sc, m, P):
        sc_all = {k: v[1] for k, v in built.items()}
        sc_all[T_] = m
        check_budget(sc_all, P, counts)
    bite('16 build budget (a 5 s tent before the grounds)', T_, short_tent, budget_run)

    def unparked(m, sc):
        a = E.actor_by_id(m, 'golden_egg')
        a['path'] = a['path'][:1]
    bite('17 the egg parked on the grab (left on its rock)', G_, unparked, lambda sc, m, P: K.check_egg(sc, m, P))

    def off_hand(m, sc):
        a = E.actor_by_id(m, 'golden_egg')
        for k in a['path'][:2]:
            k['pos'] = [k['pos'][0], k['pos'][1] + 0.3, k['pos'][2]]
    bite('17 the hand on the egg (the egg 0.3 higher)', G_, off_hand, lambda sc, m, P: K.check_egg(sc, m, P))

    def look_away(m, sc):
        for k in m['camera']:
            if 100 <= k['t'] <= 144:
                k['look_at'] = [0.0, 80.0, 0.0]
    bite('the egg in frame before the dive (E2 looking away)', G_, look_away,
         lambda sc, m, P: [K.check_seen(sc, m, P, sc.world, *x) for x in sc.notes['seen']])

    def frame_away(m, sc):
        for k in m['camera']:
            if 200 <= k['t'] <= 256:
                k['look_at'] = [0.0, 80.0, 0.0]
    bite('framing (the roar shot looks away)', A_, frame_away, lambda sc, m, P: F.check_framing(sc, m, P))

    def onto_seat(m, sc):
        e = next(e for e in m['events'] if e.get('id') == 'crowd')
        p = E.crowd_seats(e)[0][0]
        a = E.actor_by_id(m, 'commentator')
        a['path'] = [{'t': 0, 'pos': [p[0], p[1], p[2]]}]
    bite('crowd seats (the commentator on a seat)', A_, onto_seat, lambda sc, m, P: F.check_crowd_gap(sc, m, P))

    def back_key(m, sc):
        b = E.actor_by_id(m, 'firebolt')
        k = keys_near(b['path'], 60)
        v = sub(keys_near(b['path'], 62)['pos'], k['pos'])
        k['pos'] = add(k['pos'], v, -2.5)
    bite('backslide (a broom key 2.5 steps back)', G_, back_key, lambda sc, m, P: K.check_backslide(sc, m, P, ['firebolt']))

    def big_roll(m, sc):
        a = E.actor_by_id(m, 'horntail')
        keys_near(a['look'], 820)['roll'] = 200.0
    bite('her rolls within 180 (one at 200)', C_, big_roll, lambda sc, m, P: K.check_rolls(sc, m, P, 'horntail'))

    def deep_blend(m, sc):
        a = E.actor_by_id(m, 'horntail')
        a['anims'] = sorted(a['anims'] + [{'t': 300 + i, 'name': n, 'blend': 10} for i, n in
                                          enumerate(('ROAR', 'BITE', 'ROAR', 'BITE', 'ROAR', 'BITE'))], key=lambda c: c['t'])
    bite('cue fades nest at most the engine\'s depth (six cues in one fade)', A_, deep_blend,
         lambda sc, m, P: K.check_blend_depth(sc, m, P, 'horntail'))

    def cam_in_her(m, sc):
        k = next(k for k in m['camera'] if k['t'] >= 210 and not k.get('cut'))
        k['pos'] = r4(K.dragon_head(m, 'horntail', k['t']))
    bite('the camera clear of her (a key in her head)', A_, cam_in_her, lambda sc, m, P: K.check_dragon_camera(sc, m, P, 'horntail'))

    def walker_under(m, sc):
        a = E.actor_by_id(m, 'viewer')
        k = keys_near(a['path'], 100)
        k['pos'] = [k['pos'][0], k['pos'][1] - 1.0, k['pos'][2]]
    bite('walkers (the champion 1.0 into the floor)', A_, walker_under, lambda sc, m, P: K.check_walkers(sc, m, P, sc.world))

    # ---- fix round 1 (Task 13 review): each new assert bites
    def lag_her(m, sc):
        a = E.actor_by_id(m, 'horntail')
        T = sc.notes['T']
        keys = copy.deepcopy(a['path'])
        for k in a['path']:
            if T['closed'] + 10 <= k['t'] <= T['crash_tick'] - 10:
                k['pos'] = r4(E.track_sample(keys, k['t'] - 20))
    bite('I1 the chase gap (her 20 ticks further behind)', C_, lag_her, check_chase_gap)

    def miss_tail(m, sc):
        a = E.actor_by_id(m, 'horntail')
        for x in a['actions']:
            if x['t'] == sc.notes['T']['air2']:
                x['target'] = [x['target'][0] + 9.0, x['target'][1] - 4.0, x['target'][2]]
    bite('I1 the flame licks his broom (the second breath 9 east)', C_, miss_tail, check_lick)

    def spire_away(m, sc):
        t_s = sc.notes['slate']['tick']
        for k in m['camera']:
            if t_s - 30 <= k['t'] <= t_s + 30:
                k['look_at'] = [k['pos'][0], k['pos'][1] + 40.0, k['pos'][2] + 1.0]
    bite('I2 the spire smash is seen (C9 looks at the sky)', C_, spire_away,
         lambda sc, m, P: [K.check_seen(sc, m, P, sc.world, *x) for x in sc.notes['seen']])

    def sink_her(m, sc):
        a = E.actor_by_id(m, 'horntail')
        for k in a['path']:
            if 596 <= k['t'] <= 650:
                k['pos'] = [k['pos'][0], k['pos'][1] + 0.2 * (623 - k['t']), k['pos'][2]]
    bite('I3 no long glide in frame (her line sinking 0.2 a tick on the north side)', C_, sink_her, check_glide)

    def drop_beat(m, sc):
        a = E.actor_by_id(m, 'horntail')
        near = [t for t in D.downstroke_ticks(a, 0, m['length'])
                if math.dist(E.engine_cam(m['camera'], t), D.horntail_frame(m, 'horntail', t)[0]) < K.WINGBEAT_COVER]
        m['events'] = [e for e in m['events'] if not (e.get('id') == K.SFX + 'first_task_wingbeat'
                                                     and abs(e['t'] - near[0]) <= 1)]
    bite('I4 wingbeat coverage (a near downstroke unvoiced)', C_, drop_beat,
         lambda sc, m, P: K.check_wingbeats(sc, m, P, 'horntail'))

    def late_bite(m, sc):
        a = E.actor_by_id(m, 'horntail')
        for c in a['anims']:
            if c['name'] == 'BITE':
                c['t'] += 4
    bite('I5 the jaws clap on her nearest pass (the BITE cue 4 late)', C_, late_bite, check_bite)

    def late_music(m, sc):
        for e in m['events']:
            if e.get('kind') == 'music':
                e['t'] += 10
    bite('I6 the crash on a music hit (the chase cue 0.5 s late)', C_, late_music, check_music_hits)

    def thin_debris(m, sc):
        sm = next(x for x in sc.smashes if x['label'] == 'the bridge')
        T, lo = sm['tick'], sm['box'][0][0]
        keep = [i for i in sm['debris'] if not lo <= E.track_sample(E.actor_by_id(m, i)['path'], T)[0] < lo + 3]
        sc.smashes = [dict(x, debris=keep) if x is sm else x for x in sc.smashes]
    bite('I7 debris along the whole span (the west slice without debris)', C_, thin_debris, check_debris_span)

    def no_rebreath(m, sc):
        a = E.actor_by_id(m, 'horntail')
        a['actions'] = [x for x in a['actions'] if x['t'] != REBREATHE[0][0]]
    bite('I8 the floor scorch kept (the floor re-breath gone)', C_, no_rebreath, check_scorch_kept)

    def crowd_away(m, sc):
        for k in m['camera']:
            if E5_CUT <= k['t'] <= E6_CUT:
                k['look_at'] = [k['pos'][0] - 30.0, k['pos'][1] - 10.0, k['pos'][2]]
    bite('I9 a close-crowd cutaway (E5 looks into the bowl)', G_, crowd_away, K.check_crowd_shots)

    def crab_her(m, sc):
        a = E.actor_by_id(m, 'horntail')
        for k in a['look']:
            if 596 <= k['t'] <= 680:
                k['yaw'] += 80.0 * min(1.0, (k['t'] - 596) / 24.0, (680 - k['t']) / 24.0)

    def crab_run(sc, m, P):
        Q = []
        K.check_dragon_motion(sc, m, Q, 'horntail')
        P.extend(q for q in Q if 'off her own heading' in q)
    bite('M13 her crab (her look yawed 80 off her heading at speed)', C_, crab_her, crab_run)

    P_ = []
    try:
        at = lambda d_: [d_, 0.0, 0.0]
        corner_run(at, 0.0, 150.0, 0, 60, 0.4, 0.4, a_t=0.03)
    except AssertionError as e:
        P_.append('corner_run: %s' % (e,))
    out.append(('M12 a corner run time-scaled past 5% (150 blocks in 3 s from 0.4)', len(P_), P_[0] if P_ else None))
    assert P_
    return out


def main():
    import time
    t0 = time.time()
    bite_only = '--bite' in sys.argv
    gw = grounds_world()
    worlds = {'tent': tent_world(), 'grounds': gw, 'solid': K.solid_cells(gw, FG.PASS)}
    built = build_all(worlds)
    print('built %d scenes in %.0f s' % (len(built), time.time() - t0))
    problems = []
    nf = check_mirrors(problems)
    scenes = {k: v[1] for k, v in built.items()}
    check_chain_wiring(scenes, problems)
    counts = set_counts()
    tn, gn, ticks = check_budget(scenes, problems, counts)
    print('mirrors: %d functions; sets: tent %d blocks (cap %d), grounds %d blocks = %d build ticks (the scene before '
          'each at least %d)' % (nf, tn, TENT_CAP, gn, ticks, 2 * ticks))
    if bite_only:
        for name, n, first in bites(built, counts):
            print('  bites: %-62s %d problem(s), e.g. %s' % (name, n, (first or '')[:110]))
        print('%.0f s' % (time.time() - t0))
        return
    for sid in SCENE_ORDER:
        sc, s, w = built[sid]
        nums = run_checks(sc, s, w)
        problems += nums.pop('problems')
        txt = write_scene(s, sid)
        n = check_size(sid, txt, problems)
        print('%-20s %5.1f s  %7d chars  md5 %s' % (sid, s['length'] / 20.0, n, hashlib.md5(txt.encode('utf-8')).hexdigest()))
        for k, v in nums.items():
            print('    %-16s %s' % (k, v if not isinstance(v, float) else round(v, 5)))
        for k in ('tamed', 'near_miss', 'bite_gap', 'chase_gap', 'grab_gap', 'pass_tick', 'egg_speeds', 'rider_speeds'):
            if k in sc.notes:
                print('    %-16s %s' % ('(' + k + ')', sc.notes[k]))
    print('%.0f s' % (time.time() - t0))
    assert not problems, '\n'.join(problems[:80]) + '\n%d problems' % len(problems)
    print('0 problems')


if __name__ == '__main__':
    main()
