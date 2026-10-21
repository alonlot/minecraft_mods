"""The broom dev scene: the memory brooms, their riders and spline actor paths (not shipped).

    python -I build_scripts/pensieve_scenes/dev_brooms.py

writes dev_brooms.json to run/pensieve_dev/ (never to memories/). Point the common config's pensieveDevSceneDir at that
folder and run `/pensieve preview dev_brooms`. SceneDataTest.devBroomsSceneValidates validates it when it exists.

What it shows, on a flat lawn by day:
  - a school broom lying on the grass that leaps up into a standing boy's raised hand ("Up!"), the hand placed with
    scene_engine.arm_tip;
  - the mount: the broom slides under him and he rides from t 100 (a ride window, the path bracketed at 99.999 in
    world space and at 100 in the broom frame, so his root does not jump), the rider layer easing in;
  - a banked circuit (auto-bank from the spline path), a dive with the tuck and a pull-out;
  - a hover with the ridden bob, then a landing and the dismount (bracketed the same way);
  - two riders racing on Nimbus brooms all the while, side by side.
Every position comes from the engine mirrors in scene_engine.py (broom_pose, rider_root, arm_tip, broom_lowest).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import REPO, dump  # noqa: E402
import scene_engine as E  # noqa: E402

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
LENGTH = 600
R = 80
BOY_SCALE = 0.75
MOUNT, LAND, DISMOUNT = 100, 520, 560
GRIP = 0.25           # the boy grips the handle this far toward the tip from the seat point


def r3(v):
    return [round(c, 4) for c in v]


def build():
    boy_root = [0.0, 0.0, 0.0]
    # The raised arm and its hand (right arm, set mode, straight forward and a little down).
    arm_up = [-80.0, 0.0, 0.0]
    hand = E.arm_tip(boy_root, 0.0, BOY_SCALE, 'right_arm', arm_up)
    # The broom across in front of him in the hand: yaw 90 (tip toward -X), level; grip point on the hand.
    m_hand = E.broom_matrix(90.0, 0.0, 0.0)
    g = E._mat_vec(m_hand, [0.0, 0.0, GRIP])
    in_hand = [hand[i] - g[i] for i in range(3)]
    # Lying on the grass (top at y 0) to his right, its lowest point just on the ground.
    lying_y = -E.BROOM_BOX['hogwartsmagic:memory_broom'][0][1] + 0.01
    lying = [-1.2, lying_y, 1.0]
    # Under him for the mount: facing his way (yaw 0), the seat where his hips will be.
    seat = E.rider_seat(BOY_SCALE)
    under = [boy_root[0] - seat[0], boy_root[1] - seat[1], boy_root[2] - seat[2]]

    def k(t, pos, ease='spline'):
        d = {'t': t, 'pos': r3(pos)}
        if ease:
            d['ease'] = ease
        return d

    path = [k(0, lying, None), k(40, lying, 'in_out'),
            k(52, [lying[0] * 0.5 + in_hand[0] * 0.5, in_hand[1] + 0.4, lying[2] * 0.5 + in_hand[2] * 0.5]),
            k(60, in_hand, 'in_out'), k(80, in_hand, 'in_out'), k(MOUNT, under, 'in_out'),
            k(120, [0, 2.5, 4])]
    # The banked circuit: a loop of radius 12 round (0, 6, 16), keys every 12 ticks.
    cx, cz, rad = 0.0, 16.0, 12.0
    for i in range(1, 13):
        th = i * math.pi / 6
        path.append(k(120 + i * 12, [cx - rad * math.sin(th), 6.0 + 0.8 * math.sin(th), cz - rad * math.cos(th)]))
    # Up, the dive, the pull-out.
    path += [k(290, [0, 16, 14]), k(310, [0, 18, 24]), k(330, [0, 10, 32]), k(345, [0, 3.0, 40]),
             k(360, [0, 2.6, 50]), k(375, [0, 4.0, 58]), k(395, [0, 4.0, 62], 'in_out'),
             k(460, [0, 4.0, 62], 'in_out')]
    landed = [0.0, under[1], 66.0]
    path += [k(LAND, landed, 'in_out')]
    # The look: across his hand (yaw 90) until it turns under him for the mount (yaw 0); then the heading and the
    # climb angle of its own path (the engine mirrors, unwrapped), keyed every 5 ticks as splines. No roll key: the
    # roll stays the auto-bank.
    look = [{'t': 0, 'yaw': 90, 'pitch': 0}, {'t': 80, 'yaw': 90, 'pitch': 0},
            {'t': MOUNT, 'yaw': 0, 'pitch': 0, 'ease': 'in_out'}]
    prev = 0.0
    for t in range(MOUNT + 10, LAND + 1, 5):
        h = E.broom_heading(path, t)
        h += 360.0 * round((prev - h) / 360.0)
        prev = h
        look.append({'t': t, 'yaw': round(h, 3), 'pitch': round(E.broom_climb(path, t), 3), 'ease': 'spline'})
    look[-1]['ease'] = 'in_out'
    look[-1]['pitch'] = 0
    school = {'id': 'school', 'type': 'hogwartsmagic:memory_broom', 'path': path, 'look': look}
    boy = {'id': 'boy', 'type': 'player', 'name': '{viewer}', 'skin': {'viewer': True}, 'scale': BOY_SCALE,
           'items': {'chest': 'hogwartsmagic:gryffindor_robes'},
           'ride': {'on': 'school', 'from': MOUNT, 'until': DISMOUNT},
           'limbs': {'right_arm': {'mode': 'set', 'rot': [
               {'t': 0, 'v': [0, 0, 0]}, {'t': 36, 'v': [0, 0, 0]}, {'t': 44, 'v': arm_up, 'ease': 'in_out'},
               {'t': 80, 'v': arm_up}, {'t': 96, 'v': [0, 0, 0], 'ease': 'in_out'}]}}}
    scene = {'title': 'Dev brooms', 'length': LENGTH, 'radius': R, 'actors': [school, boy]}
    # Before the window his path and look are world space; inside it they are broom-relative. The brackets: a
    # world key a thousandth of a tick before each edge where the seat puts him.
    root_at = E.rider_root(scene, boy, MOUNT)[0]
    boy['path'] = [k(0, [0, 0, -4], None), k(30, boy_root, 'in_out'), k(MOUNT - 0.001, r3(root_at), 'in_out'),
                   k(MOUNT, seat, None), k(DISMOUNT - 0.001, seat, None)]
    off_at = E.rider_root(scene, boy, DISMOUNT - 0.001)[0]
    boy['path'] += [k(DISMOUNT, off_at, None), k(DISMOUNT + 30, [off_at[0] + 1.5, off_at[1], off_at[2] + 2.0], 'in_out')]
    end_yaw = E.broom_pose(scene, school, DISMOUNT - 0.001)['yaw']
    boy['look'] = [{'t': 0, 'yaw': 0}, {'t': MOUNT - 0.001, 'yaw': 0}, {'t': MOUNT, 'yaw': 0},
                   {'t': DISMOUNT - 0.001, 'yaw': 0}, {'t': DISMOUNT, 'yaw': round(end_yaw, 3)}]

    # Two riders racing on Nimbus brooms, side by side, a long weaving line past the lawn.
    racers = []
    for j, (side, skin) in enumerate(((-2.0, 'first_year_02'), (2.0, 'wizard_03'))):
        rp = []
        for i in range(0, 13):
            t = i * 50
            x = -60 + i * 10.0
            rp.append(k(t, [x, 12 + 3 * math.sin(i * 0.9), -30 + side + 5 * math.sin(i * 0.6 + j * 0.3)],
                        None if i == 0 else 'spline'))
        bid = 'nimbus_%d' % j
        racers.append({'id': bid, 'type': 'hogwartsmagic:memory_broom_racing', 'path': rp})
        racers.append({'id': 'racer_%d' % j, 'type': 'player', 'skin': {'texture': skin}, 'scale': 0.85, 'ride': bid,
                       'look': [{'t': 0, 'yaw': 0, 'pitch': 10}]})
    scene['actors'] += racers

    camera = [
        {'t': 0, 'pos': [3.5, 1.6, 4.5], 'look_at': 'actor:boy', 'fov': 60},
        {'t': 60, 'pos': [3.0, 1.5, 4.0], 'look_at': 'actor:school', 'fov': 60, 'ease': 'spline'},
        {'t': 100, 'pos': [4.0, 2.0, 3.0], 'look_at': 'actor:boy', 'fov': 62, 'ease': 'spline'},
        {'t': 180, 'pos': [18.0, 9.0, 4.0], 'look_at': 'actor:boy', 'fov': 66, 'ease': 'spline'},
        {'t': 260, 'pos': [16.0, 12.0, 30.0], 'look_at': 'actor:boy', 'fov': 66, 'ease': 'spline'},
        {'t': 340, 'pos': [12.0, 8.0, 44.0], 'look_at': 'actor:boy', 'fov': 66, 'ease': 'spline'},
        {'t': 420, 'pos': [6.0, 5.0, 58.0], 'look_at': 'actor:boy', 'fov': 60, 'ease': 'spline'},
        {'t': 500, 'pos': [5.0, 3.0, 64.0], 'look_at': 'actor:boy', 'fov': 60, 'ease': 'spline'},
        {'t': 600, 'pos': [5.0, 2.0, 68.0], 'look_at': 'actor:boy', 'fov': 60, 'ease': 'in_out'},
    ]
    scene.update({
        'set': {'fills': [{'block': 'minecraft:grass_block', 'from': [-70, -1, -50], 'to': [70, -1, 75]},
                          {'block': 'minecraft:dirt', 'from': [-70, -3, -50], 'to': [70, -2, 75]}],
                'time_of_day': 6000, 'build_ticks': 60},
        'events': [{'t': 0, 'kind': 'effect', 'id': 'eyes_open'}],
        'words': [{'t': 36, 'by': 'boy', 'text': 'Up!', 'duration': 30}],
        'camera': camera,
    })
    return scene


def checks(scene):
    """The mount and dismount continuity, the broom clear of the grass in the dive, the racers apart."""
    boy = E.actor_by_id(scene, 'boy')
    for T in (MOUNT, DISMOUNT):
        a = E.rider_root(scene, boy, T - 0.0005)[0]
        b = E.rider_root(scene, boy, T)[0]
        d = math.dist(a, b)
        print('ride edge t %d: root jump %.4f' % (T, d))
        assert d < 0.02, (T, d)
    low = min(E.broom_lowest(scene, 'school', t / 4) for t in range(290 * 4, 400 * 4))
    print('dive: lowest broom point %.3f above the grass' % low)
    assert low >= 0.6, low
    gap = min(math.dist(E.broom_frame(scene, 'nimbus_0', t)[0], E.broom_frame(scene, 'nimbus_1', t)[0])
              for t in range(0, LENGTH, 2))
    print('racers: closest %.2f' % gap)
    assert gap >= 1.2, gap
    probs = []
    E.check_spline_moves('dev_brooms', scene, probs)
    print('spline moves:', probs or 'ok')
    assert not probs, probs


def main():
    scene = build()
    checks(scene)
    os.makedirs(OUT, exist_ok=True)
    txt = dump(scene, 0, 150) + '\n'
    assert len(txt) < 900000
    with open(os.path.join(OUT, 'dev_brooms.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    print('wrote', os.path.join(OUT, 'dev_brooms.json'), len(txt), 'chars')


if __name__ == '__main__':
    main()
