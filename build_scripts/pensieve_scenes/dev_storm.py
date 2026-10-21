"""The storm dev scene: a rough sea under rain at night, a rowing boat crossing it with four riders (not shipped).

    python build_scripts/pensieve_scenes/dev_storm.py

writes dev_storm.json to run/pensieve_dev/ (never to memories/). Point the common config's pensieveDevSceneDir at that
folder and run `/pensieve preview dev_storm`. SceneDataTest.devStormSceneValidates validates it when it exists.

What it shows: a rough_sea (intensity 1) over a 92 x 92 block area of real water (its top one block under the sea's
still surface, a deepslate bed, a barrier rim), slanted rain over all of it, lightning now and then (the sea and the
rain brighten with each flash), a rock to break the waves on (shore foam), and a memory_rowboat rowed across the
sea by a rider at the oars, two riders in the stern and the viewer in the bow, all heaving, pitching and rolling with
the boat. The rower's arms are keyed to the oar stroke (Rowboat.oar: period 52 ticks, catch at phase 0).
"""
import math
import os

from scene_common import REPO, dump, physics_problems
from human_motion import seated_root_y

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
R = 60
LENGTH = 40 * 20
SEA_HALF = 46
WATER_TOP = -2          # the highest water block; its surface is at about -1.1, under the deepest trough (-0.6)

# Rowboat (pensieve/scene/Rowboat.java): seats at scale 1, the thwart top, the stroke period.
SEAT_TOP = 0.12
SEATS = {'oars': (0.0, 0.15, 180), 'stern_left': (0.42, -1.38, 0), 'stern_right': (-0.38, -1.38, 0), 'bow': (0.0, 1.3, 180)}
OAR_PERIOD = 52

BOAT_FROM = (-22.0, 0.0, -26.0)
BOAT_TO = (12.0, 0.0, 18.0)
ROW_FROM, ROW_TO = 20, 36 * 20


def boat_at(t):
    k = min(1.0, max(0.0, (t - ROW_FROM) / (ROW_TO - ROW_FROM)))
    return [BOAT_FROM[i] + (BOAT_TO[i] - BOAT_FROM[i]) * k for i in range(3)]


def heading():
    dx, dz = BOAT_TO[0] - BOAT_FROM[0], BOAT_TO[2] - BOAT_FROM[2]
    return math.degrees(math.atan2(-dx, dz))


def seat_offset(name, scale):
    x, z, _ = SEATS[name]
    return [x, round(seated_root_y(SEAT_TOP, scale), 4), z]


def rower_limbs():
    """The rower's arms and back through each stroke: reaching to the catch, pulling through, recovering."""
    arms, body = [], []
    t = 0
    while t <= LENGTH:
        s = (t % OAR_PERIOD) / OAR_PERIOD
        th = 2 * math.pi * s
        reach = 0.5 + 0.5 * math.cos(th)          # 1 at the catch, 0 at the finish
        arms.append({'t': t, 'v': [round(-38 - 42 * reach, 1), 0, 0], 'ease': 'linear'})
        body.append({'t': t, 'v': [round(-10 + 24 * reach, 1), 0, 0], 'ease': 'linear'})
        t += OAR_PERIOD // 8
    return {'right_arm': {'rot': arms, 'mode': 'set'},
            'left_arm': {'rot': [dict(k) for k in arms], 'mode': 'set'},
            'body': {'rot': body, 'mode': 'set'}}


def rider(aid, name, skin, seat, scale, extra=None):
    a = {'id': aid, 'type': 'player', 'name': name, 'skin': skin, 'ride': 'boat', 'scale': scale,
         'path': [{'t': 0, 'pos': seat_offset(seat, scale)}],
         'look': [{'t': 0, 'yaw': SEATS[seat][2]}],
         'poses': [{'t': 0, 'name': 'boat_seat', 'blend': 1}]}
    if extra:
        a.update(extra)
    return a


def world():
    """The set as a cell map, for the physics check."""
    w = {}
    for x in range(-SEA_HALF - 1, SEA_HALF + 2):
        for z in range(-SEA_HALF - 1, SEA_HALF + 2):
            rim = abs(x) == SEA_HALF + 1 or abs(z) == SEA_HALF + 1
            w[(x, -6, z)] = 'minecraft:deepslate'
            for y in range(-5, WATER_TOP + 1):
                w[(x, y, z)] = 'minecraft:barrier' if rim else 'minecraft:water'
    for (x, y, z) in rock_cells():
        w[(x, y, z)] = 'minecraft:stone'
    return w


def rock_cells():
    cells = []
    cx, cz = 24, 30
    for x in range(cx - 7, cx + 8):
        for z in range(cz - 6, cz + 7):
            d = math.hypot((x - cx) / 7.0, (z - cz) / 6.0)
            if d > 1:
                continue
            top = int(round(-1 + 5 * (1 - d) ** 0.7))
            for y in range(-5, top + 1):
                cells.append((x, y, z))
    return cells


def build():
    h = heading()
    fills = [
        {'block': 'minecraft:deepslate', 'from': [-SEA_HALF - 1, -6, -SEA_HALF - 1], 'to': [SEA_HALF + 1, -6, SEA_HALF + 1]},
        {'block': 'minecraft:barrier', 'from': [-SEA_HALF - 1, -5, -SEA_HALF - 1], 'to': [SEA_HALF + 1, WATER_TOP, SEA_HALF + 1]},
        {'block': 'minecraft:water', 'from': [-SEA_HALF, -5, -SEA_HALF], 'to': [SEA_HALF, WATER_TOP, SEA_HALF]},
    ]
    # The rock, as columns (one fill per column keeps the JSON small).
    cols = {}
    for (x, y, z) in rock_cells():
        cols[(x, z)] = max(cols.get((x, z), -99), y)
    for (x, z), top in sorted(cols.items()):
        fills.append({'block': 'minecraft:stone', 'from': [x, -5, z], 'to': [x, top, z]})
    # Dim storm light along the crossing (light blocks in the air over the sea), so the boat and riders read.
    lights = []
    for k in range(0, 6):
        p = boat_at(ROW_FROM + (ROW_TO - ROW_FROM) * k / 5)
        lights.append({'block': 'minecraft:light', 'from': [int(p[0]), 4, int(p[2])], 'to': [int(p[0]), 4, int(p[2])]})
    fills += lights

    boat = {'id': 'boat', 'type': 'hogwartsmagic:memory_rowboat',
            'path': [{'t': ROW_FROM, 'pos': [round(c, 3) for c in BOAT_FROM]},
                     {'t': ROW_TO, 'pos': [round(c, 3) for c in BOAT_TO], 'ease': 'in_out'}],
            'look': [{'t': 0, 'yaw': round(h, 2)}]}
    actors = [
        boat,
        rider('vernon', 'Vernon', {'texture': 'wizard_03'}, 'oars', 1.0, {'limbs': rower_limbs(), 'life': True}),
        rider('petunia', 'Petunia', {'texture': 'witch_02'}, 'stern_left', 1.0),
        rider('dudley', 'Dudley', {'texture': 'first_year_02'}, 'stern_right', 0.75),
        rider('boy', '{viewer}', {'viewer': True}, 'bow', 0.75),
    ]
    events = [
        {'t': 0, 'kind': 'effect', 'id': 'rough_sea', 'at': [0, 0, 0], 'spread': [SEA_HALF, 0, SEA_HALF], 'intensity': 1.0},
        {'t': 0, 'kind': 'effect', 'id': 'rain', 'at': [0, 11, 0], 'spread': [SEA_HALF, 12, SEA_HALF], 'intensity': 1.0},
    ]
    for t, at in ((160, [34, 0, 44]), (420, [-30, 0, 38]), (640, [40, 0, -10])):
        events.append({'t': t, 'kind': 'effect', 'id': 'lightning', 'at': at})

    def cam_near(t, off, fov, look='actor:boat', cut=False):
        p = boat_at(t)
        k = {'t': t, 'pos': [round(p[0] + off[0], 2), off[1], round(p[2] + off[2], 2)], 'look_at': look, 'fov': fov}
        if cut:
            k['cut'] = True
        return k

    camera = [
        # A wide, slow establishing shot over the heaving sea.
        {'t': 0, 'pos': [-40, 7, -44], 'look_at': [-18, 0, -20], 'fov': 78},
        {'t': 120, 'pos': [-36, 6, -42], 'look_at': 'actor:boat', 'fov': 72},
        # Low, beside the boat at water level, tracking it.
        cam_near(140, (6, 2.0, -3), 60, cut=True),
        cam_near(260, (6, 1.8, -3), 58),
        # Over the stern on the rower, pulling.
        cam_near(280, (-1.8, 2.6, -3.6), 50, 'actor:vernon', cut=True),
        cam_near(380, (-1.6, 2.5, -3.4), 48, 'actor:vernon'),
        # From the bow, past the viewer, across the family.
        cam_near(400, (0.9, 2.2, 3.4), 55, 'actor:petunia', cut=True),
        cam_near(500, (0.8, 2.1, 3.2), 52, 'actor:dudley'),
        # Wide again: the small boat on the big sea, toward the rock.
        cam_near(520, (-14, 5, 4), 70, cut=True),
        cam_near(LENGTH, (-10, 4, 12), 60),
    ]
    scene = {
        'title': 'Dev storm',
        'length': LENGTH,
        'radius': R,
        'set': {'fills': fills, 'time_of_day': 18000, 'build_ticks': 80},
        'poses': {'boat_seat': {'right_leg': [-65, 8, 0], 'left_leg': [-65, -8, 0]}},
        'actors': actors,
        'events': events,
        'camera': camera,
    }
    return scene


def main():
    problems = physics_problems(world(), R)
    if problems:
        raise SystemExit('set problems: ' + '; '.join(problems))
    os.makedirs(OUT, exist_ok=True)
    txt = dump(build(), 0, 150) + '\n'
    assert len(txt) < 900000
    with open(os.path.join(OUT, 'dev_storm.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    print('wrote', os.path.join(OUT, 'dev_storm.json'), len(txt), 'chars')


if __name__ == '__main__':
    main()
