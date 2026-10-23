"""The memory Horntail dev scene (not shipped).

    python -I build_scripts/pensieve_scenes/dev_horntail.py

writes dev_horntail.json and dev_horntail_snout.json (the mirror's mouth, every tick, for each dragon) to
run/pensieve_dev/. Point the common config's pensieveDevSceneDir at that folder and run `/pensieve preview dev_horntail`;
the client log then gets a `Pensieve horntail:` line every 5 s with the drawn mouth against the mirror's (the gap must
stay under 0.5). DragonFlightTest.devHorntailSceneValidates validates it when it exists.

What it shows, on an open lawn by day, 43 s:
  - perched at rest (IDLE), the ROAR, a ground BREATH_WINDUP into a BREATH, all cross-faded;
  - the TAKEOFF, then a banked circuit flown from a spline path: a climb at hover rate, a right-hand cruise circle (the
    auto-bank leans her in), a steep climb (the beat quickens), then a glide down (the wings stop and hold);
  - the pull-out into a hover, an AIR_BREATH, a BITE in the air, the descent and the LAND, back to IDLE;
  - a second dragon hovering on the far side who STAGGERs and tumbles to the ground (look pitch and roll keyed).
Every wingbeat sound lands on a downstroke (dragon_engine.downstroke_ticks); every pose and capsule comes from the
mirror in dragon_engine.py.
"""
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import REPO, dump  # noqa: E402
import scene_engine as E  # noqa: E402
import dragon_engine as D  # noqa: E402

OUT = os.path.join(REPO, 'run', 'pensieve_dev')
LENGTH = 860
R = 140
TAKEOFF, LAND, STAGGER = 190, 740, 700
DOWN = LAND + 30           # her feet on the lawn
LOW = 4.5                  # the glide-in's end, the LAND's start (blocks)


def r3(v):
    return [round(c, 4) for c in v]


def smooth(x):
    return D.smooth(x)


def ramp(t, a, b, va, vb):
    """va before a, vb after b, smoothstepped between."""
    return va + (vb - va) * smooth((t - a) / (b - a))


def flight_path():
    """The main dragon: perched, the takeoff, the circuit, the hover, the landing. Integrated per tick from a speed,
    climb and turn profile (every change smoothstepped), keyed every 5 ticks as spline keys."""
    keys = [{'t': 0, 'pos': [0.0, 0.0, 0.0]}, {'t': TAKEOFF, 'pos': [0.0, 0.0, 0.0], 'ease': 'in_out'}]
    # The takeoff: straight up and a little forward (+Z), the speed building.
    lift = [[0.0, 1.2, 0.6], [0.0, 3.4, 2.0], [0.0, 6.2, 4.4]]
    for i, p in enumerate(lift):
        keys.append({'t': TAKEOFF + 10 * (i + 1), 'pos': p, 'ease': 'spline'})
    x, y, z = lift[-1]
    yaw = 0.0
    t0 = TAKEOFF + 30
    for t in range(t0, 660):
        # speed (blocks/tick), climb (blocks/tick, up +), turn (degrees/tick, right +)
        v = ramp(t, t0, 300, 0.35, 0.72)
        v = ramp(t, 420, 440, v, 0.5)
        v = ramp(t, 465, 485, v, 1.0)
        v = ramp(t, 535, 575, v, 0.04)
        vy = ramp(t, t0, 250, 0.22, 0.12)
        vy = ramp(t, 290, 310, vy, 0.0)
        vy = ramp(t, 415, 430, vy, 0.26)
        vy = ramp(t, 460, 480, vy, -0.22)
        vy = ramp(t, 530, 560, vy, 0.0)
        turn = ramp(t, 280, 310, 0.0, 1.9)
        turn = ramp(t, 400, 420, turn, 0.4)
        turn = ramp(t, 520, 545, turn, 1.2)
        turn = ramp(t, 560, 600, turn, 0.0)
        yaw += turn
        th = math.radians(yaw)
        x += -math.sin(th) * v
        z += math.cos(th) * v
        y += vy
        if (t + 1 - t0) % 5 == 0:
            keys.append({'t': t + 1, 'pos': r3([x, y, z]), 'ease': 'spline'})
    hover = [x, y, z]
    keys[-1]['ease'] = 'in_out'
    # The descent: forward and down onto the lawn, landing as the LAND ends.
    th = math.radians(yaw)
    # A glide-in down a 24 degree slope (she pitches with it), low enough for the flare, then the LAND sets her down:
    # with the wings beating, the swept wingtips reach about 2.6 below her root.
    low = [hover[0] - math.sin(th) * 22.0, LOW, hover[2] + math.cos(th) * 22.0]
    ground = [hover[0] - math.sin(th) * 26.0, 0.0, hover[2] + math.cos(th) * 26.0]
    keys.append({'t': LAND, 'pos': r3(low), 'ease': 'in_out'})
    keys.append({'t': DOWN, 'pos': r3(ground), 'ease': 'in_out'})
    return keys, ground


def build():
    path, ground = flight_path()
    h = {'id': 'h', 'type': D.HORNTAIL_TYPE, 'path': path,
         'anims': [{'t': 0, 'name': 'IDLE'}, {'t': 40, 'name': 'ROAR', 'blend': 8},
                   {'t': 100, 'name': 'BREATH_WINDUP'}, {'t': 122, 'name': 'BREATH', 'blend': 4},
                   {'t': TAKEOFF, 'name': 'TAKEOFF'}, {'t': TAKEOFF + 30, 'name': 'FLY'},
                   {'t': 570, 'name': 'AIR_BREATH'}, {'t': 640, 'name': 'BITE'},
                   {'t': LAND, 'name': 'LAND', 'blend': 8}, {'t': DOWN, 'name': 'IDLE'}]}
    tx, ty, tz = 52.0, 22.0, 34.0
    fall = [{'t': 0, 'pos': [tx, ty, tz]}, {'t': STAGGER, 'pos': [tx - 14.0, ty, tz], 'ease': 'in_out'}]
    for t, dx, yy in ((710, 1.0, 21.2), (720, 2.2, 18.0), (730, 3.4, 12.0), (740, 4.6, 4.0)):
        fall.append({'t': t, 'pos': [tx - 14.0 - dx, yy, tz], 'ease': 'spline'})
    fall.append({'t': 746, 'pos': [tx - 19.4, 0.0, tz], 'ease': 'in_out'})
    tumble = [{'t': 0, 'yaw': -90, 'pitch': 0, 'roll': 0}, {'t': STAGGER, 'yaw': -90, 'pitch': 0, 'roll': 0},
              {'t': 708, 'yaw': -80, 'pitch': -25, 'roll': 40, 'ease': 'spline'},
              {'t': 716, 'yaw': -62, 'pitch': 20, 'roll': 110, 'ease': 'spline'},
              {'t': 726, 'yaw': -44, 'pitch': 35, 'roll': 175, 'ease': 'spline'},
              {'t': 736, 'yaw': -32, 'pitch': 12, 'roll': 260, 'ease': 'spline'},
              {'t': 748, 'yaw': -26, 'pitch': 0, 'roll': 360, 'ease': 'in_out'}]
    tumbler = {'id': 'tumbler', 'type': D.HORNTAIL_TYPE, 'path': fall, 'look': tumble,
               'anims': [{'t': 0, 'name': 'FLY'}, {'t': STAGGER, 'name': 'STAGGER', 'blend': 3},
                         {'t': 748, 'name': 'IDLE', 'blend': 8}]}
    scene = {'title': 'Dev horntail', 'length': LENGTH, 'radius': R, 'actors': [h, tumbler]}

    events = [{'t': 0, 'kind': 'effect', 'id': 'eyes_open'}]

    def at(aid, t):
        p = D.horntail_frame(scene, aid, t)[0]
        return [round(c, 2) for c in p]

    events.append({'t': 40, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.roar', 'at': at('h', 40), 'volume': 2.0})
    events.append({'t': 122, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.fire_breath', 'at': at('h', 122)})
    events.append({'t': 582, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.fire_breath', 'at': at('h', 582)})
    events.append({'t': 649, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.bite', 'at': at('h', 649)})
    events.append({'t': STAGGER, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.hurt', 'at': at('tumbler', STAGGER)})
    flaps = {}
    for aid in ('h', 'tumbler'):
        ticks = D.downstroke_ticks(E.actor_by_id(scene, aid), 0, LENGTH)
        flaps[aid] = ticks
        for t in ticks:
            events.append({'t': t, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.flap', 'at': at(aid, t),
                           'volume': 1.2})
    events.sort(key=lambda e: e['t'])

    def follow(t0, t1, off, step=10, aid='h', fov=66):
        keys = []
        for t in range(t0, t1 + 1, step):
            p = D.horntail_frame(scene, aid, t)[0]
            keys.append({'t': t, 'pos': r3([p[0] + off[0], max(2.0, p[1] + off[1]), p[2] + off[2]]),
                         'look_at': 'actor:' + aid, 'fov': fov, 'ease': 'spline'})
        keys[0].pop('ease')
        keys[0]['cut'] = True
        keys[-1]['ease'] = 'in_out'
        return keys

    camera = [{'t': 0, 'pos': [11.0, 2.4, 15.0], 'look_at': 'actor:h', 'fov': 62},
              {'t': 185, 'pos': [8.0, 2.8, 13.0], 'look_at': 'actor:h', 'fov': 62, 'ease': 'in_out'}]
    camera += follow(188, 540, [-16.0, 5.0, -14.0])
    camera += follow(545, 690, [12.0, 2.0, 14.0], fov=60)
    camera += [{'t': 692, 'pos': [22.0, 9.0, 14.0], 'look_at': 'actor:tumbler', 'fov': 58, 'cut': True},
               {'t': 735, 'pos': [24.0, 6.0, 16.0], 'look_at': 'actor:tumbler', 'fov': 58, 'ease': 'in_out'}]
    camera += follow(738, LENGTH, [12.0, 2.5, 13.0], fov=62)
    scene.update({
        'set': {'fills': [{'block': 'minecraft:grass_block', 'from': [-R, -1, -R], 'to': [R, -1, R]},
                          {'block': 'minecraft:dirt', 'from': [-R, -3, -R], 'to': [R, -2, R]},
                          {'block': 'minecraft:stone', 'from': [-4, 0, -6], 'to': [-3, 1, -5]},
                          {'block': 'minecraft:stone', 'from': [4, 0, -4], 'to': [5, 0, -3]}],
                'time_of_day': 6000, 'build_ticks': 80},
        'events': events,
        'camera': camera,
    })
    return scene, flaps


def checks(scene, flaps):
    problems = []
    # Wingbeats: each flap on a downstroke, the wings at their lowest.
    for aid, ticks in flaps.items():
        a = E.actor_by_id(scene, aid)
        for t in ticks:
            if math.sin(D.wing_phase(a, t)) < 0.85:
                problems.append('%s flap at %d off the downstroke' % (aid, t))
    print('wingbeats: %d for the main dragon, %d for the tumbler' % (len(flaps['h']), len(flaps['tumbler'])))
    # The main dragon clear of the lawn (top y 0) in flight: every capsule at least 0.8 above it.
    worst = (1e9, None, None)
    for t4 in range((TAKEOFF + 40) * 4, LAND * 4):
        t = t4 / 4.0
        for name, (a, b, r) in D.horntail_parts(scene, 'h', t) if t4 % 8 == 0 else ():
            low = min(a[1], b[1]) - r
            if low < worst[0]:
                worst = (low, t, name)
    print('flight: lowest capsule %.2f above the lawn (t %s, %s)' % worst)
    if worst[0] < 0.8:
        problems.append('the dragon comes within %.2f of the lawn at t %s (%s)' % worst)
    # Her speed changes smoothly: at most 0.08 blocks/tick^2 outside the takeoff.
    keys = D.path_of(E.actor_by_id(scene, 'h'))
    acc = 0.0
    for t in range(TAKEOFF + 32, LAND + 20):
        acc = max(acc, abs(E.path_speed(keys, t + 1) - E.path_speed(keys, t)))
    print('flight: worst speed change %.4f blocks/tick^2' % acc)
    if acc > 0.08:
        problems.append('speed change %.3f' % acc)
    # The cue states match the path: no FLY while perched, nothing flying on the lawn.
    for t in range(0, LENGTH, 2):
        s = D.horntail_cue(E.actor_by_id(scene, 'h'), t)
        y = D.horntail_frame(scene, 'h', t)[0][1]
        if s['flying'] and y < 0.01 and TAKEOFF + 2 < t < LAND:
            problems.append('flying on the ground at %d' % t)
        if not s['flying'] and y > 0.5:
            problems.append('grounded in the air at %d (%s)' % (t, s['name']))
    probs = []
    E.check_spline_moves('dev_horntail', scene, probs)
    print('spline moves:', probs or 'ok')
    problems += probs
    return problems


def snout_table(scene):
    out = {}
    for aid in ('h', 'tumbler'):
        rows = []
        for t in range(0, LENGTH + 1):
            p, _ = D.horntail_snout(scene, aid, t)
            rows.append([t] + [round(c, 4) for c in p])
        out[aid] = rows
    return out


def main():
    scene, flaps = build()
    problems = checks(scene, flaps)
    for p in problems:
        print('PROBLEM', p)
    os.makedirs(OUT, exist_ok=True)
    txt = dump(scene, 0, 150) + '\n'
    assert len(txt) < 900000
    with open(os.path.join(OUT, 'dev_horntail.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    with open(os.path.join(OUT, 'dev_horntail_snout.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(dump(snout_table(scene), 0, 150) + '\n')
    print('wrote', os.path.join(OUT, 'dev_horntail.json'), len(txt), 'chars, and the snout side file')
    sys.exit(1 if problems else 0)


if __name__ == '__main__':
    main()
