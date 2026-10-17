"""Two chained dev scenes for checking the client hand-over between scenes (not shipped).

    python build_scripts/pensieve_scenes/dev_chain.py

writes dev_chain_a.json (night, red wool floor, one actor; next: dev_chain_b) and dev_chain_b.json (noon, blue wool
floor, a different actor) to run/pensieve_dev/, never to memories/. Point the common config's pensieveDevSceneDir
at that folder and run `/pensieve preview dev_chain_a`: the red scene, a fade to black, then the blue scene fading
in at noon, then home.
"""
import os

from scene_common import REPO, dump

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
R = 12


def scene(title, floor, time_of_day, actor, nxt=None):
    s = {
        'title': title,
        'length': '10s',
        'radius': R,
        'set': {
            'fills': [{'block': floor, 'from': [-8, -1, -8], 'to': [8, -1, 8]}],
            'time_of_day': time_of_day,
        },
        'actors': [actor],
        'camera': [
            {'t': 0, 'pos': [0, 3, -9], 'look_at': [0, 1, 0], 'fov': 70},
            {'t': '10s', 'pos': [0, 2.5, -6], 'look_at': [0, 1.2, 0], 'fov': 60},
        ],
    }
    if nxt:
        s['next'] = nxt
    return s


A = scene('Dev chain A', 'minecraft:red_wool', 18000, {
    'id': 'walker_a', 'type': 'player', 'name': 'Red', 'skin': {'texture': 'death_eater'},
    'path': [{'t': 0, 'pos': [-5, 0, 0]}, {'t': '10s', 'pos': [5, 0, 0]}],
    'look': [{'t': 0, 'yaw': -90, 'pitch': 0}],
}, nxt='dev_chain_b')

B = scene('Dev chain B', 'minecraft:blue_wool', 6000, {
    'id': 'walker_b', 'type': 'player', 'name': 'Blue', 'skin': {'texture': 'voldemort'},
    'path': [{'t': 0, 'pos': [5, 0, 3]}, {'t': '10s', 'pos': [-5, 0, 3]}],
    'look': [{'t': 0, 'yaw': 90, 'pitch': 0}],
})

os.makedirs(OUT, exist_ok=True)
for sid, s in (('dev_chain_a', A), ('dev_chain_b', B)):
    path = os.path.join(OUT, sid + '.json')
    with open(path, 'w', newline='\n', encoding='utf-8') as f:
        f.write(dump(s) + '\n')
    print('wrote', path)
