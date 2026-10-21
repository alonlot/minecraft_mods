"""The storm sky dev scene: an open field under a storm sky and rain, with lightning every 6 to 10 s (not shipped).

    python -I build_scripts/pensieve_scenes/dev_storm_sky.py

writes dev_storm_sky.json to run/pensieve_dev/ (never to memories/). Point the common config's pensieveDevSceneDir at that
folder and run `/pensieve preview dev_storm_sky`. SceneDataTest.devStormSkySceneValidates validates it when it exists.

What it shows, on a flat grass field with a boy standing in it:
  - a `sky` of style "storm" in daylight: a #4A525C grey horizon rising to a #262C35 zenith, a full billowing cloud deck
    to the horizon with lighter ragged scud sweeping under it (about 1 to 2 degrees a second overhead), along the rain's
    wind; fog and clear colour from the horizon, the fog drawn in to 30..240 blocks while the storm shows;
  - `rain` over a 200 x 200 block box (the largest the validator allows: one box covers a whole pitch); the drops are drawn
    only within 26 blocks of the camera, so it costs the same as a small box; while the storm sky shows, three distant
    rain curtains (35, 70, 130 blocks) carry the rain out to the far walls;
  - a `lightning` effect every 6 to 10 s (120 to 200 ticks, from a fixed seed): the dome and the clouds flash with each,
    two or three flickers over 1.3 s peaking at 0.85 (the rain brightens with the same flicker), lighting the cloud on
    the bolt's side most; the bolt and thunder are the effect's own.
"""
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import REPO, dump  # noqa: E402

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
LENGTH = 60 * 20
R = 150
HALF = 140


def strike_times():
    """A strike every 6 to 10 s from tick 60, from a fixed seed (the same file every run)."""
    rnd = random.Random(2110)
    t, out = 60, []
    while t < LENGTH - 40:
        out.append(t)
        t += rnd.randint(120, 200)
    return out


def build():
    rnd = random.Random(7)
    events = [
        {'t': 0, 'kind': 'effect', 'id': 'eyes_open'},
        {'t': 0, 'kind': 'effect', 'id': 'sky', 'at': [0, 0, 0], 'style': 'storm'},
        {'t': 0, 'kind': 'effect', 'id': 'rain', 'at': [0, 20, 0], 'spread': [200, 20, 200], 'intensity': 1.4},
    ]
    for t in strike_times():
        events.append({'t': t, 'kind': 'effect', 'id': 'lightning',
                       'at': [round(rnd.uniform(-45, 45), 1), 0, round(rnd.uniform(15, 60), 1)]})
    boy = {'id': 'boy', 'type': 'player', 'name': '{viewer}', 'skin': {'viewer': True}, 'scale': 0.85,
           'path': [{'t': 0, 'pos': [0.0, 0.0, 0.0]}], 'look': [{'t': 0, 'yaw': 0}]}
    camera = [
        # Low behind the boy, looking out over the field to the horizon.
        {'t': 0, 'pos': [0.0, 1.7, -5.0], 'look_at': [0.0, 6.0, 60.0], 'fov': 70},
        {'t': 400, 'pos': [0.0, 1.7, -5.0], 'look_at': [0.0, 22.0, 30.0], 'fov': 70, 'ease': 'in_out'},
        # Then up at the clouds.
        {'t': 800, 'pos': [0.0, 1.7, -5.0], 'look_at': [0.0, 60.0, 8.0], 'fov': 75, 'ease': 'in_out'},
        {'t': 1000, 'pos': [0.0, 14.0, -5.0], 'look_at': [0.0, 4.0, 110.0], 'fov': 80, 'ease': 'in_out'},
        {'t': LENGTH - 1, 'pos': [0.0, 14.0, -5.0], 'look_at': [0.0, 6.0, 100.0], 'fov': 70, 'ease': 'in_out'},
    ]
    return {
        'title': 'Dev storm sky',
        'length': LENGTH,
        'radius': R,
        'set': {'fills': [
            {'block': 'minecraft:grass_block', 'from': [-HALF, -1, -HALF], 'to': [HALF, -1, HALF]},
            {'block': 'minecraft:dirt', 'from': [-HALF, -3, -HALF], 'to': [HALF, -2, HALF]},
            # Stand-ins for the stands: stepped stone-brick walls 60, 100 and 135 blocks out, to judge the haze, the
            # distant rain curtains and the grass and stone against the sky in daylight.
            {'block': 'minecraft:stone_bricks', 'from': [-60, 0, 60], 'to': [60, 5, 61]},
            {'block': 'minecraft:stone_bricks', 'from': [-90, 0, 100], 'to': [90, 9, 102]},
            {'block': 'minecraft:stone_bricks', 'from': [-130, 0, 135], 'to': [130, 14, 138]},
        ], 'time_of_day': 6000, 'build_ticks': 60},
        'actors': [boy],
        'events': events,
        'camera': camera,
    }


def main():
    scene = build()
    os.makedirs(OUT, exist_ok=True)
    txt = dump(scene, 0, 150) + '\n'
    assert len(txt) < 900000
    gaps = [b - a for a, b in zip(strike_times(), strike_times()[1:])]
    assert all(120 <= g <= 200 for g in gaps), gaps
    with open(os.path.join(OUT, 'dev_storm_sky.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    print('wrote', os.path.join(OUT, 'dev_storm_sky.json'), len(txt), 'chars,', len(strike_times()), 'strikes')


if __name__ == '__main__':
    main()
