"""The First Task's props, as the Python mirror of scene_engine knows them: the Firebolt and the golden egg.

    python -I build_scripts/pensieve_scenes/first_task_props.py            # writes firebolt_cases.json, egg_cases.json
    import first_task_props  # registers the Firebolt and the egg into scene_engine (BROOM_TYPES, BROOM_BOX, egg_box)

Importing this module adds, at run time, what the plan's Task 11 asked for in scene_engine.py itself (a controller
ruling kept scene_engine.py untouched while the Flying scene was in review). To fold it in later, move these into
scene_engine.py: the `FIREBOLT` entry of BROOM_TYPES and BROOM_BOX, `egg_box` and `egg_glint`. Nothing else in the
engine changes: every broom maths function (frame, bank, climb, rider_root, rider_pose, check_rider_grip) is type
agnostic and reads only BROOM_BOX for the lowest point.

Mirrors (checked by the Java tests against the JSON written here):
  - BroomFlight.BOX_FIREBOLT          BroomFlightTest.broomBoxesMatchTheModelLayers (against the baked layer)
  - six Firebolt parity cases         BroomFlightTest.fireboltMirrorAgrees (firebolt_cases.json)
  - GoldenEgg.box / glint / radiusAt  GoldenEggTest.pythonMirrorAgrees (egg_cases.json)
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import scene_engine as E  # noqa: E402

FIREBOLT = 'hogwartsmagic:memory_broom_firebolt'
EGG = 'hogwartsmagic:memory_golden_egg'
# The Firebolt layer's bounds in the broom frame (BroomFlight.BOX_FIREBOLT), min xyz then max xyz, measured from
# BroomModelV2.createFireboltLayer: the brush and wisp hang behind (-z), the stirrups below.
FIREBOLT_BOX = ((-0.350, -0.521, -2.270), (0.350, 0.218, 1.595))
FIREBOLT_CASES = os.path.join(HERE, 'firebolt_cases.json')

# GoldenEgg: the size, the taper, and the glint's slots.
EGG_LENGTH, EGG_WIDTH, EGG_TAPER = 0.36, 0.26, 0.14
GLINT_SLOT, GLINT_JITTER, GLINT_FLASH = 40.0, 10.0, 8.0
EGG_CASES = os.path.join(HERE, 'egg_cases.json')
M64 = (1 << 64) - 1


def _hm_mix(z):
    """HumanMotion.mix: the splitmix finaliser WITHOUT the golden-gamma step."""
    z = ((z ^ (z >> 30)) * 0xbf58476d1ce4e5b9) & M64
    z = ((z ^ (z >> 27)) * 0x94d049bb133111eb) & M64
    return z ^ (z >> 31)


def motion_seed(actor_id):
    """HumanMotion.seed: FNV-1a over the id's UTF-16 units, then the finaliser above (an unsigned 64-bit int).

    scene_engine.motion_seed is meant to be this, but scene_engine.py defines _mix64 twice (line ~985 for the
    motion seed, line ~1585 for the crowd, which adds the golden-gamma step), and the later def replaces the earlier
    for every caller: since the crowd was added, scene_engine.motion_seed no longer matches Java (a regenerated
    broom_cases.json would carry the wrong seed_bob and hover bob). register() patches the engine's name with this
    correct one, so everything generated through first_task_props agrees with HumanMotion.seed.
    """
    h = 0xcbf29ce484222325
    data = actor_id.encode('utf-16-be')
    for i in range(0, len(data), 2):
        h ^= (data[i] << 8) | data[i + 1]
        h = (h * 0x100000001b3) & M64
    return _hm_mix(h)


def register():
    """Teach scene_engine the Firebolt (idempotent) and give it the correct motion seed (see motion_seed)."""
    E.motion_seed = motion_seed
    if FIREBOLT not in E.BROOM_TYPES:
        E.BROOM_TYPES = tuple(E.BROOM_TYPES) + (FIREBOLT,)
    E.BROOM_BOX[FIREBOLT] = FIREBOLT_BOX
    E.egg_box = egg_box
    E.egg_glint = egg_glint


# ================================================================ the egg (GoldenEgg)

def egg_box(scale):
    """The egg's bounds about its centre at `scale`: ((min x, y, z), (max x, y, z))."""
    r, h = EGG_WIDTH / 2 * scale, EGG_LENGTH / 2 * scale
    return (-r, -h, -r), (r, h, r)


def _profile(v):
    return math.sqrt(max(0.0, 1 - v * v)) * (1 - EGG_TAPER * v)


_PEAK = max(_profile(-1 + i / 1000.0) for i in range(2001))


def egg_radius(v):
    """GoldenEgg.radiusAt: the radius at height v (-1 bottom, +1 top), blocks at scale 1."""
    return EGG_WIDTH / 2 * _profile(max(-1.0, min(1.0, v))) / _PEAK


def _mix(z):
    z = (z + 0x9E3779B97F4A7C15) & M64
    z = ((z ^ (z >> 30)) * 0xBF58476D1CE4E5B9) & M64
    z = ((z ^ (z >> 27)) * 0x94D049BB133111EB) & M64
    return z ^ (z >> 31)


def egg_seed(actor_id):
    """MemoryBall.seedFor: HumanMotion.seed(id) cut to a signed 32-bit int."""
    low = motion_seed(actor_id) & 0xFFFFFFFF
    return low - (1 << 32) if low >= (1 << 31) else low


def _unit(seed, slot):
    """GoldenEgg.unit: splitmix of (splitmix of the signed seed) + slot, top 53 bits as a fraction."""
    h = _mix((_mix(seed & M64) + slot) & M64)
    return (h >> 11) * 2.0 ** -53


def glint_start(seed, k):
    return GLINT_SLOT * k + GLINT_JITTER * _unit(seed, k)


def egg_glint(t, seed):
    """GoldenEgg.glint: 0 outside a flash, rising to 1 mid-flash (seed: egg_seed of the actor id)."""
    k = math.floor(t / GLINT_SLOT)
    local = t - glint_start(seed, k)
    if local <= 0 or local >= GLINT_FLASH:
        return 0.0
    return math.sin(math.pi * local / GLINT_FLASH)


def egg_cases():
    ids = ['egg', 'golden_egg', 'prize', 'a']
    glints = []
    for actor_id in ids:
        seed = egg_seed(actor_id)
        for k in (0, 1, 2, 7, 123, 1999):
            start = glint_start(seed, k)
            for off in (-1.0, 0.0, 1.5, 4.0, 6.25, 8.0, 12.0):
                t = round(start + off, 4)
                glints.append({'id': actor_id, 't': t, 'seed': seed, 'glint': egg_glint(t, seed)})
    boxes = [{'scale': sc, 'lo': list(egg_box(sc)[0]), 'hi': list(egg_box(sc)[1])} for sc in (0.75, 0.85, 1.0, 2.0)]
    radii = [{'v': v, 'r': egg_radius(v)} for v in (-1.0, -0.8, -0.4, 0.0, 0.3, 0.6, 0.9, 1.0)]
    return {'constants': {'LENGTH': EGG_LENGTH, 'WIDTH': EGG_WIDTH, 'TAPER': EGG_TAPER, 'SLOT': GLINT_SLOT,
                          'JITTER': GLINT_JITTER, 'FLASH': GLINT_FLASH},
            'boxes': boxes, 'radii': radii, 'glints': glints}


# ================================================================ the Firebolt cases (BroomFlightTest)

# Six cases from the school/Nimbus scenes, flown on a Firebolt: scene -> times.
FIREBOLT_TIMES = {'straight': [30.0], 'turning': [47.25], 'climbing': [50.0], 'diving': [46.0], 'hovering': [40.25],
                  'mount': [75.5]}


def firebolt_cases():
    register()
    base = E._case_scenes()
    scenes = {}
    for name in FIREBOLT_TIMES:
        sc = json.loads(json.dumps(base[name]))
        b = E.actor_by_id(sc, 'b')
        b['type'] = FIREBOLT
        scenes[name] = sc
    cases = []
    for name, times in FIREBOLT_TIMES.items():
        sc = scenes[name]
        b = E.actor_by_id(sc, 'b')
        r = E.actor_by_id(sc, 'r')
        for t in times:
            bp = E.broom_pose(sc, b, t)
            root, m = E.rider_root(sc, r, t)
            cases.append({'scene': name, 't': t, 'pos': bp['pos'], 'yaw': bp['yaw'], 'pitch': bp['pitch'],
                          'roll': bp['roll'], 'speed': bp['speed'], 'bank': E.broom_bank(b['path'], t),
                          'climb': E.broom_climb(b['path'], t), 'heading': E.broom_heading(b['path'], t),
                          'rider_root': root, 'rider_m': m, 'rider_pose': E.rider_pose(bp['speed'], bp['pitch']),
                          'rider_weight': E.rider_weight(*E.ride_of(r)[1:], t) if E.rides_at(r, t) else None,
                          'seed_bob': E.hover_bob(0.0, t, motion_seed('b'))})
    return {'constants': {'BOX_FIREBOLT': [list(FIREBOLT_BOX[0]), list(FIREBOLT_BOX[1])]}, 'scenes': scenes,
            'cases': cases}


def write_cases():
    data = firebolt_cases()
    with open(FIREBOLT_CASES, 'w', newline='\n', encoding='utf-8') as f:
        f.write(json.dumps(data, indent=1, sort_keys=True) + '\n')
    print('wrote', FIREBOLT_CASES, len(data['cases']), 'cases')
    eg = egg_cases()
    with open(EGG_CASES, 'w', newline='\n', encoding='utf-8') as f:
        f.write(json.dumps(eg, indent=1, sort_keys=True) + '\n')
    print('wrote', EGG_CASES, len(eg['glints']), 'glints')


register()

if __name__ == '__main__':
    write_cases()
