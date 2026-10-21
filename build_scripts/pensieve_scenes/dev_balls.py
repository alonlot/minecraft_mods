"""The balls dev scene: the Quaffle, the Bludger, the Remembrall and the Snitch as memory actors (not shipped).

    python -I build_scripts/pensieve_scenes/dev_balls.py

writes dev_balls.json to run/pensieve_dev/ (never to memories/). Point the common config's pensieveDevSceneDir at that
folder and run `/pensieve preview dev_balls`. SceneDataTest.devBallsSceneValidates validates it when it exists.

What it shows, on a flat lawn by day, between a boy (z 0, facing +z) and a catcher (z 16, facing him):
  - a throw: the boy holds a Quaffle in his raised right hand, lets it go at t 30 and it arcs (a spline of the
    projectile's own parabola) into the catcher's raised right hand at t 62, the seams turning as it flies;
  - a catch into a hand of a second ball: the Remembrall (glass, smoke swirling) leaves the boy's left hand at t 50 and
    lands in the catcher's left hand at t 80;
  - the catcher lets the Quaffle fall (t 90) and it rolls away across the grass, rolling true, slowing to a stop;
  - a Bludger pass (t 90 to 146): a fast straight line a metre and a half beside both of them, shaking as it goes;
  - a Golden Snitch darting about the lawn, its wing blur drawn.
Every hand is placed with scene_engine.arm_tip (the sway's middle, the corners asserted), every ball box comes from
scene_engine.ball_box.
"""
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import REPO, dump  # noqa: E402
import scene_engine as E  # noqa: E402

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
LENGTH = 220
R = 80
SCALE = 0.85
G = 0.04                       # a dropped ball's gravity, blocks per tick squared
THROW_G = 0.012                # a thrown ball's: a flat, quick throw (a lob would peak at a block a tick)
QUAFFLE, BLUDGER, REMEMBRALL = ('hogwartsmagic:memory_quaffle', 'hogwartsmagic:memory_bludger',
                                'hogwartsmagic:memory_remembrall')
ARM_UP = [-80.0, 0.0, 0.0]
CATCH_GAP = 0.15               # assert 5: ball centre to hand, every sway corner
TELEPORT = 2.0                 # MemoryActors.TELEPORT_BLOCKS: a ball moving farther than this in a tick is cut
BOY_AT, CATCHER_AT = [0.0, 0.0, 0.0], [0.0, 0.0, 16.0]


def r3(v):
    return [round(c, 4) for c in v]


def k(t, pos, ease='spline'):
    d = {'t': t, 'pos': r3(pos)}
    if ease:
        d['ease'] = ease
    return d


def hand(root, yaw, side):
    """The raised hand at the sway's middle."""
    z = ARM_UP[2] + (E.BOB_MID[1] if side == 'right_arm' else -E.BOB_MID[1])
    return E.arm_tip(root, yaw, SCALE, side, [ARM_UP[0] + E.BOB_MID[0], ARM_UP[1], z])


def hand_corners(root, yaw, side):
    """The hand at the sway's corners (zRot 0 and 0.1 rad, xRot +-0.05; left arm mirrored)."""
    sign = 1.0 if side == 'right_arm' else -1.0
    out = []
    for bz in E.BOB_Z:
        for bx in E.BOB_X:
            out.append(E.arm_tip(root, yaw, SCALE, side, [ARM_UP[0] + sign * bx, ARM_UP[1], ARM_UP[2] + sign * bz]))
    return out


def arc(p0, p1, t0, t1, step=4):
    """Spline keys along a projectile's parabola from p0 at t0 to p1 at t1 (the first key is not emitted)."""
    n = t1 - t0
    vy = (p1[1] - p0[1]) / n + 0.5 * THROW_G * n
    keys = []
    t = t0 + step
    while t < t1:
        tau = t - t0
        keys.append(k(t, [p0[0] + (p1[0] - p0[0]) * tau / n, p0[1] + vy * tau - 0.5 * THROW_G * tau * tau,
                          p0[2] + (p1[2] - p0[2]) * tau / n]))
        t += step
    keys.append(k(t1, p1, 'in_out'))
    return keys


def arm(rot_keys):
    return {'mode': 'add', 'rot': [dict(t=t, v=v, **({'ease': e} if e else {})) for t, v, e in rot_keys]}


def build():
    boy_r, boy_l = hand(BOY_AT, 0.0, 'right_arm'), hand(BOY_AT, 0.0, 'left_arm')
    cat_r, cat_l = hand(CATCHER_AT, 180.0, 'right_arm'), hand(CATCHER_AT, 180.0, 'left_arm')
    zero = [0.0, 0.0, 0.0]

    # The Quaffle: held, thrown at 30, caught at 62, held, let go at 92, dropped to the grass, rolled away.
    q_r = E.ball_radius(QUAFFLE)
    drop_t = int(math.ceil(math.sqrt(2 * (cat_r[1] - q_r) / G)))
    rest = [cat_r[0], q_r, cat_r[2]]
    quaffle = [k(0, boy_r, None), k(30, boy_r, 'in_out')]
    quaffle += arc(boy_r, cat_r, 30, 62)
    quaffle += [k(92, cat_r, 'in_out')]
    fall = []
    for t in range(94, 92 + drop_t, 2):
        tau = t - 92
        fall.append(k(t, [cat_r[0], cat_r[1] - 0.5 * G * tau * tau, cat_r[2]]))
    quaffle += fall
    land_t = 92 + drop_t
    quaffle += [k(land_t, rest, 'in_out')]
    # The roll: v0 slowing linearly to rest over 70 ticks, keys every 5 as splines, ending on in_out.
    v0, span = 0.25, 70
    roll_start = land_t + 1
    quaffle += [k(roll_start, rest, 'in_out')]
    for t in range(roll_start + 5, roll_start + span, 5):
        tau = t - roll_start
        quaffle.append(k(t, [rest[0] + v0 * tau - v0 * tau * tau / (2 * span), q_r, rest[2]]))
    quaffle.append(k(roll_start + span, [rest[0] + v0 * span / 2, q_r, rest[2]], 'in_out'))

    # The Remembrall: boy's left hand at 50, catcher's left hand at 80.
    rem = [k(0, boy_l, None), k(50, boy_l, 'in_out')] + arc(boy_l, cat_l, 50, 80, 3) + [k(LENGTH, cat_l, 'in_out')]

    # The Bludger: a fast pass beside them, z from 38 to -30 over 56 ticks, weaving a little.
    blud = [k(0, [-1.4, 1.5, 38.0], None), k(90, [-1.4, 1.5, 38.0], 'in_out')]
    for i in range(1, 8):
        f = i / 7.0
        blud.append(k(90 + 8 * i, [-1.4 + 0.5 * math.sin(f * 6), 1.5 + 0.3 * math.sin(f * 4), 38.0 - 68.0 * f]))
    blud.append(k(150, [-1.4, 1.5, -32.0], 'in_out'))

    # The Snitch: darting about the lawn, a new target every 7 ticks.
    rnd = random.Random(8)
    snitch = [k(0, [5.0, 2.5, 6.0], None)]
    pos = [5.0, 2.5, 6.0]
    for t in range(7, LENGTH, 7):
        pos = [min(9.0, max(2.0, pos[0] + rnd.uniform(-3.0, 3.0))), min(4.0, max(1.0, pos[1] + rnd.uniform(-1.4, 1.4))),
               min(13.0, max(3.0, pos[2] + rnd.uniform(-3.5, 3.5)))]
        snitch.append(k(t, pos))
    snitch[-1]['ease'] = 'in_out'

    boy = {'id': 'boy', 'type': 'player', 'name': '{viewer}', 'skin': {'viewer': True}, 'scale': SCALE,
           'items': {'chest': 'hogwartsmagic:gryffindor_robes'},
           'path': [k(0, BOY_AT, None)], 'look': [{'t': 0, 'yaw': 0}],
           'limbs': {'right_arm': arm([(0, ARM_UP, None), (30, ARM_UP, None), (50, zero, 'in_out')]),
                     'left_arm': arm([(0, ARM_UP, None), (50, ARM_UP, None), (76, zero, 'in_out')])}}
    catcher = {'id': 'catcher', 'type': 'player', 'skin': {'texture': 'wizard_03'}, 'scale': SCALE,
               'path': [k(0, CATCHER_AT, None)], 'look': [{'t': 0, 'yaw': 180}],
               'limbs': {'right_arm': arm([(0, zero, None), (20, zero, None), (40, ARM_UP, 'in_out'), (92, ARM_UP, None),
                                           (112, zero, 'in_out')]),
                         'left_arm': arm([(0, zero, None), (50, zero, None), (68, ARM_UP, 'in_out'), (140, ARM_UP, None),
                                          (160, zero, 'in_out')])}}
    scene = {'title': 'Dev balls', 'length': LENGTH, 'radius': R, 'actors': [
        boy, catcher,
        {'id': 'quaffle', 'type': QUAFFLE, 'path': quaffle},
        {'id': 'remembrall', 'type': REMEMBRALL, 'path': rem},
        {'id': 'bludger', 'type': BLUDGER, 'path': blud},
        {'id': 'snitch', 'type': 'hogwartsmagic:golden_snitch', 'path': snitch}]}
    scene['catch'] = {'quaffle': (62, catcher, cat_r), 'remembrall': (80, catcher, cat_l)}
    camera = [
        {'t': 0, 'pos': [8.0, 2.2, -3.0], 'look_at': 'actor:boy', 'fov': 60},
        {'t': 40, 'pos': [8.0, 2.6, 5.0], 'look_at': 'actor:quaffle', 'fov': 60, 'ease': 'spline'},
        {'t': 80, 'pos': [7.0, 2.4, 12.0], 'look_at': 'actor:remembrall', 'fov': 60, 'ease': 'spline'},
        {'t': 110, 'pos': [-8.0, 2.2, 14.0], 'look_at': 'actor:bludger', 'fov': 62, 'ease': 'spline'},
        {'t': 150, 'pos': [-8.0, 2.4, 4.0], 'look_at': 'actor:quaffle', 'fov': 62, 'ease': 'spline'},
        {'t': 190, 'pos': [10.0, 3.2, 9.0], 'look_at': 'actor:snitch', 'fov': 60, 'ease': 'spline'},
        {'t': 219, 'pos': [11.0, 3.4, 9.0], 'look_at': 'actor:snitch', 'fov': 60, 'ease': 'in_out'},
    ]
    real = {k_: v for k_, v in scene.items() if k_ != 'catch'}
    real.update({
        'set': {'fills': [{'block': 'minecraft:grass_block', 'from': [-50, -1, -50], 'to': [50, -1, 60]},
                          {'block': 'minecraft:dirt', 'from': [-50, -3, -50], 'to': [50, -2, 60]}],
                'time_of_day': 6000, 'build_ticks': 30},
        'events': [{'t': 0, 'kind': 'effect', 'id': 'eyes_open'}],
        'camera': camera,
    })
    return real, scene['catch']


def checks(scene, catches):
    """Speeds under the cut limit, the grass under the rolling ball, the hands on the catches, the Bludger's gap."""
    worst = 0.0
    for a in scene['actors']:
        if not (E.is_ball(a['type']) or a['type'] == 'hogwartsmagic:golden_snitch'):
            continue
        prev = E.track_sample(a['path'], 0)[:3]
        for i in range(1, LENGTH * 4 + 1):
            cur = E.track_sample(a['path'], i / 4.0)[:3]
            worst = max(worst, math.dist(prev, cur) * 4)
            prev = cur
    print('fastest mover: %.2f blocks/tick (the cut limit is %.1f)' % (worst, TELEPORT))
    assert worst < TELEPORT - 0.1, worst
    # The Quaffle's last rolling stretch stays on the grass: its lowest point within a hair of y 0, never under.
    q = E.actor_by_id(scene, 'quaffle')
    land = [key for key in q['path'] if key['pos'][1] <= E.ball_radius(QUAFFLE) + 1e-9][0]['t']
    low = min(E.ball_lowest(scene, 'quaffle', t / 4.0) for t in range(int(land * 4), (LENGTH - 20) * 4))
    print('rolling Quaffle: lowest point %.4f above the grass' % low)
    assert -0.002 <= low < 0.01, low
    # Every catch: the ball centre within 0.15 of the hand at every sway corner.
    for bid, (t, who, tip) in catches.items():
        side = 'right_arm' if bid == 'quaffle' else 'left_arm'
        corners = hand_corners(CATCHER_AT, 180.0, side)
        centre = E.ball_pos(scene, bid, t)
        gap = max(math.dist(c, centre) for c in corners)
        print('%s catch at t %d: worst corner gap %.3f (limit %.2f)' % (bid, t, gap, CATCH_GAP))
        assert gap <= CATCH_GAP, (bid, gap)
    # The throws start in the boy's hands.
    for bid, side, t in (('quaffle', 'right_arm', 30), ('remembrall', 'left_arm', 50)):
        gap = max(math.dist(c, E.ball_pos(scene, bid, t)) for c in hand_corners(BOY_AT, 0.0, side))
        print('%s release at t %d: worst corner gap %.3f' % (bid, t, gap))
        assert gap <= CATCH_GAP, (bid, gap)
    # The Bludger passes within 1.5 of both of them (a near miss), never through one.
    near = min(math.dist(E.ball_pos(scene, 'bludger', t / 4.0), [c[0], 1.2, c[2]])
               for t in range(90 * 4, 150 * 4) for c in (BOY_AT, CATCHER_AT))
    print('Bludger pass: closest %.2f to a player' % near)
    assert 0.6 <= near <= 1.5, near
    probs = []
    E.check_spline_moves('dev_balls', scene, probs)
    print('spline moves:', probs or 'ok')
    assert not probs, probs


def main():
    scene, catches = build()
    checks(scene, catches)
    os.makedirs(OUT, exist_ok=True)
    txt = dump(scene, 0, 150) + '\n'
    assert len(txt) < 900000
    with open(os.path.join(OUT, 'dev_balls.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    print('wrote', os.path.join(OUT, 'dev_balls.json'), len(txt), 'chars')


if __name__ == '__main__':
    main()
