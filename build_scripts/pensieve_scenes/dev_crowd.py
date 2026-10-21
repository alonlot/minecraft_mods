"""The crowd dev scene: a packed 3,000-seat bowl that cheers a goal, gasps and erupts (not shipped).

    python -I build_scripts/pensieve_scenes/dev_crowd.py

writes dev_crowd.json to run/pensieve_dev/ (never to memories/). Point the common config's pensieveDevSceneDir at that
folder and run `/pensieve preview dev_crowd`. SceneDataTest.devCrowdSceneValidates validates it when it exists. In a
dev_ scene CrowdRenderer logs a "Pensieve crowd:" line every 5 s: the crowd's CPU time per frame (average and worst)
with its level-of-detail counts.

What it shows, under a storm sky in daylight with lightning now and then:
  - a rectangular stone bowl of 14 seat tiers round a 60 x 40 field, one `crowd` event per side in that side's house
    colours (north Gryffindor, south Slytherin, east Ravenclaw, west Hufflepuff), 3,000 spectators in all, plus a rail
    of standers along the north touchline (those who would block a front-row seat are left out);
  - 0-8 s: a wide shot over the bowl (strips and two-box figures), then low by the north stand (full figures turning
    their heads to the play);
  - 8 s: a `goal` for the north stand (stand, arms and flags up) with the crowd swell;
  - 18 s: a `gasp` everywhere (hands to heads for 1 s, then sit) with the gasp;
  - 25 s: an `erupt` everywhere (up, jumping, arms thrown up, 6 s) with the eruption, then a `sit`;
  - 36 s: the south stand `chant`s (a 2 Hz arm pump in phase) with the chant.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import REPO, dump  # noqa: E402
import scene_engine as E  # noqa: E402

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
LENGTH = 46 * 20
R = 80
HX, HZ, TIERS = 30, 20, 14
SFX = 'hogwartsmagic:pensieve.sfx.flying_crowd_'


def side_rows(side):
    """The seat-top rows of one side of the bowl, tier by tier (tier k: one up and one out)."""
    rows = []
    for k in range(TIERS):
        x, z, y = HX + k, HZ + k, k + 0.5
        rows.append({'n': [-x + 1, y, z, x - 1, y, z], 's': [x - 1, y, -z, -x + 1, y, -z],
                     'e': [x, y, -z + 1, x, y, z - 1], 'w': [-x, y, z - 1, -x, y, -z + 1]}[side])
    return rows


def fills():
    out = [{'block': 'minecraft:grass_block', 'from': [-R, -1, -R], 'to': [R, -1, R]},
           {'block': 'minecraft:dirt', 'from': [-R, -3, -R], 'to': [R, -2, R]}]
    for k in range(TIERS):
        x, z = HX + k, HZ + k
        for lo, hi in (([-x, 0, z], [x, k, z]), ([-x, 0, -z], [x, k, -z]), ([x, 0, -z], [x, k, z]),
                       ([-x, 0, -z], [-x, k, z])):
            if k > 0:
                out.append({'block': 'minecraft:stone_bricks', 'from': lo, 'to': [hi[0], k - 1, hi[2]]})
            out.append({'block': 'minecraft:stone_brick_slab', 'from': [lo[0], k, lo[2]], 'to': [hi[0], k, hi[2]]})
    return out


def build():
    crowds = []
    for side, style, count in (('n', 'gryffindor', 880), ('s', 'slytherin', 880), ('e', 'ravenclaw', 620),
                               ('w', 'hufflepuff', 620)):
        rows = side_rows(side)
        if side == 'n':
            rows = rows + [[-28, 0, HZ - 1.3, 28, 0, HZ - 1.3, 1]]       # standers along the north touchline
        crowds.append({'t': 0, 'kind': 'effect', 'id': 'crowd', 'at': [0, 0, 0], 'style': style, 'count': count,
                       'rows': rows})
    north = {'at': [0, 4, HZ + 6], 'spread': [HX + TIERS + 2, 20, 9]}
    south = {'at': [0, 4, -HZ - 6], 'spread': [HX + TIERS + 2, 20, 9]}
    events = [
        {'t': 0, 'kind': 'effect', 'id': 'eyes_open'},
        {'t': 0, 'kind': 'effect', 'id': 'sky', 'at': [0, 0, 0], 'style': 'storm'},
        {'t': 0, 'kind': 'effect', 'id': 'rain', 'at': [0, 20, 0], 'spread': [80, 20, 80], 'intensity': 1.0},
        {'t': 0, 'kind': 'sfx', 'id': SFX + 'bed', 'volume': 0.45, 'loop': True},
    ] + crowds + [
        dict({'t': 160, 'kind': 'effect', 'id': 'crowd_react', 'style': 'goal', 'duration': 80}, **north),
        {'t': 160, 'kind': 'sfx', 'id': SFX + 'swell', 'volume': 1.0},
        {'t': 360, 'kind': 'effect', 'id': 'crowd_react', 'style': 'gasp', 'at': [0, 6, 0]},
        {'t': 360, 'kind': 'sfx', 'id': SFX + 'gasp', 'volume': 1.0},
        {'t': 500, 'kind': 'effect', 'id': 'crowd_react', 'style': 'erupt', 'at': [0, 6, 0], 'duration': 120},
        {'t': 500, 'kind': 'sfx', 'id': SFX + 'erupt', 'volume': 1.0},
        {'t': 660, 'kind': 'effect', 'id': 'crowd_react', 'style': 'sit', 'at': [0, 6, 0]},
        dict({'t': 720, 'kind': 'effect', 'id': 'crowd_react', 'style': 'chant', 'duration': 140}, **south),
        {'t': 720, 'kind': 'sfx', 'id': SFX + 'chant', 'volume': 0.9},
        {'t': 270, 'kind': 'effect', 'id': 'lightning', 'at': [-60, 0, 70]},
        {'t': 600, 'kind': 'effect', 'id': 'lightning', 'at': [70, 0, -60]},
    ]
    camera = [
        # A wide shot from high over the south-west corner, across the whole bowl.
        {'t': 0, 'pos': [-52.0, 30.0, -44.0], 'look_at': [0.0, 4.0, 6.0], 'fov': 75},
        {'t': 100, 'pos': [-40.0, 26.0, -46.0], 'look_at': [6.0, 4.0, 8.0], 'fov': 75, 'ease': 'in_out'},
        # Low on the field by the north stand: full figures, heads following the look target.
        {'t': 101, 'pos': [-12.0, 2.2, 8.0], 'look_at': [4.0, 5.0, 26.0], 'fov': 70, 'cut': True},
        {'t': 330, 'pos': [10.0, 2.2, 9.0], 'look_at': [-4.0, 5.0, 27.0], 'fov': 70, 'ease': 'in_out'},
        # Midfield toward the south stand for the gasp.
        {'t': 331, 'pos': [0.0, 6.0, 4.0], 'look_at': [0.0, 6.0, -30.0], 'fov': 72, 'cut': True},
        {'t': 450, 'pos': [6.0, 6.0, 0.0], 'look_at': [0.0, 6.0, -30.0], 'fov': 72, 'ease': 'in_out'},
        # High and wide for the eruption, then down to the south stand for the chant.
        {'t': 451, 'pos': [0.0, 34.0, 56.0], 'look_at': [0.0, 4.0, -8.0], 'fov': 78, 'cut': True},
        {'t': 690, 'pos': [-20.0, 30.0, 52.0], 'look_at': [0.0, 4.0, -10.0], 'fov': 78, 'ease': 'in_out'},
        {'t': 691, 'pos': [-6.0, 3.0, -8.0], 'look_at': [0.0, 6.0, -28.0], 'fov': 70, 'cut': True},
        {'t': LENGTH - 1, 'pos': [8.0, 3.0, -10.0], 'look_at': [0.0, 6.0, -28.0], 'fov': 70, 'ease': 'in_out'},
    ]
    return {
        'title': 'Dev crowd',
        'length': LENGTH,
        'radius': R,
        'set': {'fills': fills(), 'time_of_day': 6000, 'build_ticks': 60},
        'events': events,
        'camera': camera,
    }


def main():
    scene = build()
    seats = {}
    dropped = 0
    for e in scene['events']:
        if e.get('id') == 'crowd':
            kept, d = E.crowd_layout(e)
            seats[e['style']] = len(kept)
            dropped += d
    total = sum(seats.values())
    assert total == 3000, seats
    assert total <= E.CROWD_MAX
    os.makedirs(OUT, exist_ok=True)
    txt = dump(scene, 0, 150) + '\n'
    assert len(txt) < 900000
    with open(os.path.join(OUT, 'dev_crowd.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    print('wrote', os.path.join(OUT, 'dev_crowd.json'), len(txt), 'chars,', total, 'spectators', seats,
          dropped, 'standers left out')


if __name__ == '__main__':
    main()
