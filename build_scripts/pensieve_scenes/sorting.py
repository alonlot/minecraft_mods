"""Generates data/hogwartsmagic/memories/sorting.json: the stub Sorting memory (stage 1).

Usage (from the repo root): python build_scripts/pensieve_scenes/sorting.py

About 12 s: a dark stone hall floor and the viewer himself (`{"viewer": true}` skin, scale 0.75, a first-year)
sitting on a stool. The eyelids open at the start, the narration says his name (`{viewer}...`), and the lids close
over the last 2 s. Putting the Sorting Hat on plays this (SortingHatManager.MEMORY_SCENE) before the quiz. It
exercises the viewer skin, actor scale, `{viewer}` in words and the eyelid effects in one place; stage 2 replaces
it with the full film, chained from this id through `next`.
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import write_scene, physics_problems

R = 12
LENGTH = 12.0
SCALE = 0.75
# A seated root: an oak slab stool (top at y 0.5); a scaled leg is 12 px * SCALE long, so with the legs swung
# forward the hips sit on the slab when the feet-root is that far below its top.
SEAT_Y = round(0.5 - 12 / 16 * SCALE, 3)


def T(s):
    return '%gs' % round(s, 2)


fills = [
    {'block': 'minecraft:polished_deepslate', 'from': [-8, -1, -8], 'to': [8, -1, 8]},
    {'block': 'minecraft:deepslate_tiles', 'from': [-3, -1, -3], 'to': [3, -1, 3]},
    {'block': 'minecraft:oak_slab', 'from': [0, 0, 0], 'to': [0, 0, 0]},
]

scene = {
    'title': 'The Sorting',
    'length': T(LENGTH),
    'radius': R,
    'set': {'fills': fills, 'time_of_day': 18000, 'build_ticks': 40},
    'poses': {
        'seated': {'right_leg': [-90, 8, 0], 'left_leg': [-90, -8, 0], 'right_arm': [-30, 0, 0],
                   'left_arm': [-30, 0, 0]},
    },
    'actors': [{
        'id': 'viewer', 'type': 'player', 'skin': {'viewer': True}, 'scale': SCALE,
        'path': [{'t': 0, 'pos': [0.5, SEAT_Y, 0.5]}],
        'look': [{'t': 0, 'yaw': 180, 'pitch': 10}],
        'poses': [{'t': 0, 'name': 'seated', 'blend': 1}],
    }],
    'events': [
        {'t': 0, 'kind': 'effect', 'id': 'eyes_open', 'at': [0, 0, 0], 'duration': T(3)},
        {'t': T(LENGTH - 2), 'kind': 'effect', 'id': 'eyes_close', 'at': [0, 0, 0], 'duration': T(2)},
    ],
    'words': [
        {'t': T(3.5), 'text': '{viewer}...', 'narration': True, 'duration': T(4)},
    ],
    'camera': [
        {'t': 0, 'pos': [0.5, 1.6, -3.5], 'look_at': 'actor:viewer', 'fov': 60},
        {'t': T(LENGTH), 'pos': [0.5, 1.3, -2.2], 'look_at': 'actor:viewer', 'fov': 50},
    ],
}

world = {}
for f in fills:
    for x in range(f['from'][0], f['to'][0] + 1):
        for y in range(f['from'][1], f['to'][1] + 1):
            for z in range(f['from'][2], f['to'][2] + 1):
                world[(x, y, z)] = f['block']
problems = physics_problems(world, R)
if problems:
    raise SystemExit('\n'.join(problems))

txt = write_scene(scene, 'sorting')
print('wrote sorting.json (%d chars)' % len(txt))
