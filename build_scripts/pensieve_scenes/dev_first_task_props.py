"""The First Task props dev scene: the Firebolt, the golden egg, the champion robes and a triwizard crowd (not shipped).

    python -I build_scripts/pensieve_scenes/dev_first_task_props.py

writes dev_first_task_props.json to run/pensieve_dev/ (never to memories/). Point the common config's
pensieveDevSceneDir at that folder and run `/pensieve preview dev_first_task_props`.
SceneDataTest.devFirstTaskPropsSceneValidates validates it when it exists.

What it shows, on a flat lawn by day:
  - the golden egg standing at scale 1 on a low rock, glinting every 1.5 to 2.5 s (a close shot, 0-6 s);
  - the champion (14 years old, scale 0.85, the viewer's skin, the champion's robes in `chest`) flying a Firebolt in a
    banked circuit (6-13 s), the grip asserted against the real model's handle;
  - a ten-tier stand of 500 `triwizard` spectators, house robes and the Beauxbatons powder blue and Durmstrang oxblood
    among them (13-17 s);
  - the held egg (`golden_egg_prop`, in the hand of a second champion) beside an egg actor at the same scale, to compare
    their size (17-20 s).
Every position comes from the engine mirrors (first_task_props registers the Firebolt and the egg into scene_engine).
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import REPO, dump  # noqa: E402
import scene_engine as E  # noqa: E402
import first_task_props as P  # noqa: E402,F401  (registers the Firebolt and the egg)

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
LENGTH = 400
R = 80
CHAMP_SCALE = 0.85
FIREBOLT, EGG = P.FIREBOLT, P.EGG
ROCK = [-6.0, 0.0, 6.0]            # the rock's centre column (blocks (-7..-6, 0, 5..6): top face at y 1)
ROCK_TOP = 1.0
STAND_HX, STAND_Z0, TIERS = 24, 30, 10


def r3(v):
    return [round(c, 4) for c in v]


def k(t, pos, ease='spline'):
    d = {'t': t, 'pos': r3(pos)}
    if ease:
        d['ease'] = ease
    return d


def stand_rows():
    rows = []
    for tier in range(TIERS):
        z, y = STAND_Z0 + tier, tier + 0.5
        rows.append([-STAND_HX + 1, y, z, STAND_HX - 1, y, z])
    return rows


def fills():
    out = [{'block': 'minecraft:grass_block', 'from': [-R, -1, -R], 'to': [R, -1, R]},
           {'block': 'minecraft:dirt', 'from': [-R, -3, -R], 'to': [R, -2, R]},
           {'block': 'minecraft:cobblestone', 'from': [-7, 0, 5], 'to': [-6, 0, 6]}]
    for tier in range(TIERS):
        z = STAND_Z0 + tier
        if tier > 0:
            out.append({'block': 'minecraft:stone_bricks', 'from': [-STAND_HX, 0, z], 'to': [STAND_HX, tier - 1, z]})
        out.append({'block': 'minecraft:stone_brick_slab', 'from': [-STAND_HX, tier, z], 'to': [STAND_HX, tier, z]})
    return out


def build():
    # The egg: scale 1, standing on the rock (its bottom on the top face).
    lo, hi = P.egg_box(1.0)
    egg_y = ROCK_TOP - lo[1]
    egg = {'id': 'egg', 'type': EGG, 'scale': 1.0, 'path': [k(0, [ROCK[0], egg_y, ROCK[2]], None)]}

    # The Firebolt: a banked circuit of radius 14 about (0, 8, 14), 160 ticks a lap, a little up and down.
    cx, cz, rad, lap = 0.0, 14.0, 14.0, 160.0
    path = []
    for i in range(0, 41):
        t = i * 10
        th = 2 * math.pi * t / lap
        path.append(k(t, [cx - rad * math.sin(th), 8.0 + 0.8 * math.sin(2 * th), cz - rad * math.cos(th)],
                      None if i == 0 else 'spline'))
    fire = {'id': 'fb', 'type': FIREBOLT, 'path': path}
    champ = {'id': 'champ', 'type': 'player', 'name': '{viewer}', 'skin': {'viewer': True}, 'scale': CHAMP_SCALE,
             'items': {'chest': 'hogwartsmagic:triwizard_robes'}, 'ride': 'fb',
             'look': [{'t': 0, 'yaw': 0, 'pitch': 8}, {'t': 200, 'yaw': 0, 'pitch': 8},
                      {'t': 240, 'yaw': 45, 'pitch': 4, 'ease': 'in_out'}, {'t': 300, 'yaw': 0, 'pitch': 8, 'ease': 'in_out'}]}

    # A second champion holds the prop egg; an egg actor at the same scale floats beside it for the size to compare.
    holder_at = [4.0, 0.0, -6.0]
    arm_up = [-60.0, 0.0, 0.0]
    holder = {'id': 'holder', 'type': 'player', 'name': '{viewer}', 'skin': {'viewer': True}, 'scale': CHAMP_SCALE,
              'path': [k(0, holder_at, None)], 'look': [{'t': 0, 'yaw': 0}],
              'items': {'chest': 'hogwartsmagic:triwizard_robes', 'mainhand': 'hogwartsmagic:golden_egg_prop'},
              'limbs': {'right_arm': {'mode': 'add', 'rot': [{'t': 0, 'v': arm_up}]}}}
    hand = E.arm_tip(holder_at, 0.0, CHAMP_SCALE, 'right_arm', [arm_up[0] + E.BOB_MID[0], arm_up[1],
                                                                 arm_up[2] + E.BOB_MID[1]])
    lo85, hi85 = P.egg_box(CHAMP_SCALE)
    twin = {'id': 'twin', 'type': EGG, 'scale': CHAMP_SCALE,
            'path': [k(0, [hand[0] + 0.55, hand[1], hand[2]], None)]}

    camera = [
        # The egg close up, the glint at its top.
        {'t': 0, 'pos': [-4.9, 1.45, 4.3], 'look_at': [ROCK[0], egg_y + 0.05, ROCK[2]], 'fov': 38},
        {'t': 118, 'pos': [-5.1, 1.35, 4.0], 'look_at': [ROCK[0], egg_y + 0.05, ROCK[2]], 'fov': 34, 'ease': 'in_out'},
        # The Firebolt in its circuit.
        {'t': 119, 'pos': [18.0, 6.0, -4.0], 'look_at': 'actor:champ', 'fov': 60, 'cut': True},
        {'t': 190, 'pos': [-6.0, 9.0, 30.0], 'look_at': 'actor:champ', 'fov': 60, 'ease': 'spline'},
        {'t': 258, 'pos': [-16.0, 7.0, 4.0], 'look_at': 'actor:champ', 'fov': 60, 'ease': 'spline'},
        # The triwizard stand from the lawn.
        {'t': 259, 'pos': [-8.0, 5.0, 8.0], 'look_at': [0.0, 8.0, 36.0], 'fov': 70, 'cut': True},
        {'t': 338, 'pos': [8.0, 5.0, 8.0], 'look_at': [0.0, 8.0, 36.0], 'fov': 70, 'ease': 'in_out'},
        # The held egg beside the egg actor.
        {'t': 339, 'pos': [holder_at[0] + 0.9, 1.7, holder_at[2] + 1.6], 'look_at': [hand[0] + 0.25, hand[1], hand[2]],
         'fov': 40, 'cut': True},
        {'t': LENGTH - 1, 'pos': [holder_at[0] + 0.8, 1.6, holder_at[2] + 1.4],
         'look_at': [hand[0] + 0.25, hand[1], hand[2]], 'fov': 36, 'ease': 'in_out'},
    ]
    scene = {
        'title': 'Dev first task props', 'length': LENGTH, 'radius': R,
        'actors': [egg, fire, champ, holder, twin],
        'set': {'fills': fills(), 'time_of_day': 6000, 'build_ticks': 60},
        'events': [{'t': 0, 'kind': 'effect', 'id': 'eyes_open'},
                   {'t': 0, 'kind': 'effect', 'id': 'crowd', 'at': [0, 0, 0], 'style': 'triwizard', 'count': 500,
                    'rows': stand_rows()}],
        'camera': camera,
    }
    return scene, egg_y


def checks(scene, egg_y):
    fire = E.actor_by_id(scene, 'fb')
    probs = []
    E.check_spline_moves('dev_first_task_props', scene, probs)
    print('spline moves:', probs or 'ok')
    assert not probs, probs
    # The Firebolt stays well clear of the lawn (its lowest point is the stirrups).
    low = min(E.broom_lowest(scene, 'fb', t / 4) for t in range(0, LENGTH * 4))
    print('Firebolt: lowest point %.2f above the grass' % low)
    assert low >= 4.0, low
    # The egg stands on the rock exactly.
    lo, _ = P.egg_box(1.0)
    assert abs((egg_y + lo[1]) - ROCK_TOP) < 1e-9
    print('egg: bottom %.4f on the rock top %.1f' % (egg_y + lo[1], ROCK_TOP))
    # Glints: how many flashes in the close shot, and the longest and shortest gap over the scene.
    seed = P.egg_seed('egg')
    starts = [P.glint_start(seed, n) for n in range(0, LENGTH // 40 + 1)]
    inside = [s for s in starts if s < 118]
    gaps = [b - a for a, b in zip(starts, starts[1:])]
    print('glints in the close shot: %d at %s; gaps %.1f to %.1f ticks' % (
        len(inside), [round(s, 1) for s in inside], min(gaps), max(gaps)))
    assert len(inside) >= 2
    assert all(30 <= g <= 50 for g in gaps)
    print('grip: worst fist gap %.3f, deepest sink %.3f, fist-to-thigh clearance %.2f px' % E.check_rider_grip())
    # The crowd: 500 spectators, and the share of each school.
    ev = [e for e in scene['events'] if e.get('id') == 'crowd'][0]
    kept, dropped = E.crowd_layout(ev)
    print('crowd: %d seats (%d standers left out)' % (len(kept), dropped))
    assert len(kept) == 500, len(kept)


def main():
    scene, egg_y = build()
    checks(scene, egg_y)
    os.makedirs(OUT, exist_ok=True)
    txt = dump(scene, 0, 150) + '\n'
    assert len(txt) < 900000
    with open(os.path.join(OUT, 'dev_first_task_props.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    print('wrote', os.path.join(OUT, 'dev_first_task_props.json'), len(txt), 'chars')


if __name__ == '__main__':
    main()
