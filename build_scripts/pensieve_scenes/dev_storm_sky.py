"""The storm sky dev scene: an open field under a storm sky and rain, with lightning every 6 to 10 s (not shipped).

    python -I build_scripts/pensieve_scenes/dev_storm_sky.py

writes dev_storm_sky.json to run/pensieve_dev/ (never to memories/). Point the common config's pensieveDevSceneDir at that
folder and run `/pensieve preview dev_storm_sky`. SceneDataTest.devStormSkySceneValidates validates it when it exists.

What it shows, on a flat grass field with a boy standing in it:
  - a `sky` of style "storm": a slate horizon rising to a near-black zenith, three fast low torn cloud layers sliding
    along the rain's wind, fog and clear colour taken from the horizon;
  - `rain` over a 200 x 200 block box (the largest the validator allows: one box covers a whole pitch); the drops are drawn
    only within 26 blocks of the camera, so it costs the same as a small box;
  - a `lightning` effect every 6 to 10 s (120 to 200 ticks, from a fixed seed): the dome and the clouds flash with each,
    two or three flickers over 1.3 s peaking at 0.85, the bolt and thunder are the effect's own.
"""
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import REPO, dump  # noqa: E402

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
LENGTH = 60 * 20
R = 80
HALF = 70


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
        {'t': LENGTH - 1, 'pos': [0.0, 1.7, -5.0], 'look_at': [0.0, 6.0, 60.0], 'fov': 70, 'ease': 'in_out'},
    ]
    return {
        'title': 'Dev storm sky',
        'length': LENGTH,
        'radius': R,
        'set': {'fills': [
            {'block': 'minecraft:grass_block', 'from': [-HALF, -1, -HALF], 'to': [HALF, -1, HALF]},
            {'block': 'minecraft:dirt', 'from': [-HALF, -3, -HALF], 'to': [HALF, -2, HALF]},
            # Dim light over the boy so he reads in the dark.
            {'block': 'minecraft:light', 'from': [0, 6, -3], 'to': [0, 6, -3]},
        ], 'time_of_day': 13000, 'build_ticks': 30},
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
