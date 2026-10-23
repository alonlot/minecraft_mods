"""The memory Horntail's fire dev scene (not shipped).

    python -I build_scripts/pensieve_scenes/dev_dragon_fire.py

writes dev_dragon_fire.json and dev_dragon_fire_snout.json (the mirror's mouth, every tick, for the `Pensieve
horntail:` snout check) to run/pensieve_dev/. Point the common config's pensieveDevSceneDir at that folder and run
`/pensieve preview dev_dragon_fire`; the client log then gets a `Pensieve fire:` line every 5 s with the fire's CPU cost
per frame (budget: two jets and their smoke in 1.0 ms), its puffs, smoke, scorch quads and particles, the frames it had
no drawn snout for, and how far the jet's aim strays from her head. DragonFireTest.devDragonFireSceneValidates
validates it when it exists.

What it shows, on an open lawn by day, 36 s:
  - the dragon perched on a rock platform (her nest's height) roars, then a ground BREATH at a boulder 10 blocks out:
    the jet leaves her mouth, splashes over the boulder's face and scorches it, while a boy cowers behind it;
  - a second BREATH with no target, her head sweeping 70 degrees across the lawn: the stream bends like a hose and
    leaves a scorched trail;
  - the takeoff and a climbing turn to a hover over a cottage, an AIR_BREATH at its roof (scorched slates);
  - a last wide shot of the three scorches, embers dying.
Her head is aimed with `limbs.head_look`, solved against the mirror so the jet leaves along her head (the aim error is
printed and checked); every fire check runs on dragon_engine's fire mirror, the splash included (fire_clearance), and
a jet landing on the lawn just before the boulder must splash up to its face, not through it (the splash clamp).

The preview checklist (`/pensieve preview dev_dragon_fire`, no gradle in flight; the user's in-game pass):
  - the look: the boulder breath (t 112-160, side on, then from behind the boulder with the boy) and the air breath at
    the roof (t 512-560): brightness against the day sky, the shimmer (it may read as pale steam), the soot's rim, the
    smoke in front of the jet from the stands' side (drawn over the flame since fix round 1);
  - the `Pensieve fire:` lines: CPU ms a frame (budget 1.0 for two jets and their smoke), the scorch's own ms, the
    soot and ember cells (at most 6,000 a pass), the fps and frame time, and the sprite overdraw estimate (screens a
    frame: past about 3 the GPU fill is worth a look, near the lens especially);
  - one frame with Fabulous graphics on (the line says which): the additive layers must still glow, not turn dull or
    opaque; if they do, the additive passes move to AFTER_WEATHER;
  - the `Pensieve horntail:` lines: the worst snout gap at most 0.5.
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
SID = 'dev_dragon_fire'
LENGTH = 720
R = 90
NEST_TOP = 4                     # the platform's top surface (her feet)
ROAR = 20
WINDUP1, BREATH1 = 84, 106       # BREATH window 112..152
FIRE1 = 112
WINDUP2, BREATH2 = 196, 218      # window 224..264
FIRE2 = 224
TAKEOFF = 300
HOVER_AT = 470
AIR = 500                        # AIR_BREATH window 512..552
FIRE3 = 512
LEAVE = 600
BOULDER_FACE = [0.5, 1.5, 13.0]  # the boulder's front face, the first target


def r3(v):
    return [round(c, 4) for c in v]


def ramp(t, a, b, va, vb):
    return va + (vb - va) * D.smooth((t - a) / (b - a))


# ---------------------------------------------------------------- the set

def boxes():
    """The set's solid boxes: (block, from, to), inclusive cells."""
    out = [('minecraft:grass_block', [-R, -1, -R], [R, -1, R]),
           ('minecraft:dirt', [-R, -3, -R], [R, -2, R]),
           # The platform she perches on (her nest's height), her feet on its top at y 4.
           ('minecraft:stone', [-4, 0, -9], [4, NEST_TOP - 1, 2]),
           ('minecraft:andesite', [-5, 0, -8], [5, 1, 1]),
           ('minecraft:cobblestone', [-3, NEST_TOP - 1, -9], [3, NEST_TOP - 1, -7]),
           # The boulder the boy hides behind.
           ('minecraft:stone', [-2, 0, 13], [3, 2, 15]),
           ('minecraft:andesite', [-1, 3, 13], [2, 3, 14]),
           ('minecraft:tuff', [-3, 0, 14], [-3, 1, 15]),
           ('minecraft:cobblestone', [4, 0, 13], [4, 1, 14])]
    return out


def cottage(at):
    """A small stone cottage with a stepped slate roof (ridge along z), its south-west ground corner at `at`: fills."""
    x0, z0 = at[0], at[2]
    w, d, h = 7, 9, 4
    fills = [('minecraft:stone_bricks', [x0, 0, z0], [x0 + w - 1, h - 1, z0 + d - 1]),
             ('minecraft:air', [x0 + 1, 0, z0 + 1], [x0 + w - 2, h - 1, z0 + d - 2])]
    for i in range((w + 1) // 2):
        y = h + i
        fills.append(('minecraft:deepslate_tiles', [x0 + i, y, z0 - 1], [x0 + i, y, z0 + d]))
        fills.append(('minecraft:deepslate_tiles', [x0 + w - 1 - i, y, z0 - 1], [x0 + w - 1 - i, y, z0 + d]))
    ridge = [x0 + w // 2, h + w // 2, z0 - 1]
    return fills, ridge


def world_of(fills, singles):
    w = {}
    for block, a, b in fills:
        for x in range(a[0], b[0] + 1):
            for y in range(a[1], b[1] + 1):
                for z in range(a[2], b[2] + 1):
                    if block == 'minecraft:air':
                        w.pop((x, y, z), None)
                    else:
                        w[(x, y, z)] = block
    for p, st in singles:
        w[tuple(p)] = st
    return w


# ---------------------------------------------------------------- the dragon

def flight_path():
    """Perched on the platform, the takeoff, a climbing left turn, a near-hover drifting slowly forward (the air breath;
    a dead hover would leave her heading to a spline's overshoot), then away. Integrated per tick from a speed, climb
    and turn profile, every change smoothstepped, keyed every 5 ticks as spline keys."""
    keys = [{'t': 0, 'pos': [0.0, float(NEST_TOP), 0.0]}, {'t': TAKEOFF, 'pos': [0.0, float(NEST_TOP), 0.0], 'ease': 'in_out'}]
    lift = [[0.0, NEST_TOP + 1.2, 0.6], [0.0, NEST_TOP + 3.4, 2.0], [0.0, NEST_TOP + 6.2, 4.4]]
    for i, p in enumerate(lift):
        keys.append({'t': TAKEOFF + 10 * (i + 1), 'pos': p, 'ease': 'spline'})
    x, y, z = lift[-1]
    yaw = 0.0
    t0 = TAKEOFF + 30
    hover = None
    for t in range(t0, LENGTH):
        v = ramp(t, t0, 360, 0.35, 0.62)
        v = ramp(t, 425, HOVER_AT, v, 0.02)
        v = ramp(t, LEAVE, LEAVE + 50, v, 0.6)
        vy = ramp(t, t0, 350, 0.2, 0.1)
        vy = ramp(t, 400, 440, vy, 0.0)
        vy = ramp(t, LEAVE, LEAVE + 30, vy, 0.15)
        turn = ramp(t, 345, 365, 0.0, -1.3)
        turn = ramp(t, 395, 420, turn, 0.0)
        yaw += turn
        th = math.radians(yaw)
        x += -math.sin(th) * v
        z += math.cos(th) * v
        y += vy
        if t + 1 == FIRE3 + 20:
            hover = [x, y, z]
        if (t + 1 - t0) % 5 == 0:
            keys.append({'t': t + 1, 'pos': r3([x, y, z]), 'ease': 'spline'})
    return keys, hover, E.broom_heading_held(keys, FIRE3 + 20)


def aim_error(scene, t, target):
    """Degrees between her head's direction and the line from her mouth to the target at t."""
    p, d = D.horntail_snout(scene, 'h', t)
    v = [target[i] - p[i] for i in range(3)]
    n = math.sqrt(sum(c * c for c in v))
    return math.degrees(math.acos(max(-1.0, min(1.0, sum(v[i] * d[i] for i in range(3)) / n))))


def solve_look(scene, t, target, keys_before):
    """The head look [pitch, yaw] that points her head at the target at t (grid search on the mirror)."""
    h = E.actor_by_id(scene, 'h')
    best = (1e9, None)
    for pitch in range(-40, 41, 2):
        for yaw in range(-60, 61, 3):
            h['limbs'] = {D.HEAD_LOOK: {'rot': keys_before + [{'t': t, 'v': [pitch, yaw, 0]}]}}
            e = aim_error(scene, t, target)
            if e < best[0]:
                best = (e, [pitch, yaw, 0])
    return best[1]


def build():
    path, hover, yaw = flight_path()
    th = math.radians(yaw)
    fwd = [-math.sin(th), 0.0, math.cos(th)]
    side = [fwd[2], 0.0, -fwd[0]]
    # The cottage ahead of her hover, 13 blocks out and well below: its roof's near slope faces her.
    c = [hover[0] + fwd[0] * 13.0, 0.0, hover[2] + fwd[2] * 13.0]
    at = [int(round(c[0] - 3.5)), 0, int(round(c[2] - 4.5))]
    cfills, ridge = cottage(at)
    roof_target = [at[0] + 1.5 if fwd[0] > 0 else at[0] + 5.5, 5.5, at[2] + 4.5]
    h = {'id': 'h', 'type': D.HORNTAIL_TYPE, 'path': path,
         'anims': [{'t': 0, 'name': 'IDLE'}, {'t': ROAR, 'name': 'ROAR', 'blend': 8},
                   {'t': WINDUP1, 'name': 'BREATH_WINDUP'}, {'t': BREATH1, 'name': 'BREATH'},
                   {'t': WINDUP2, 'name': 'BREATH_WINDUP'}, {'t': BREATH2, 'name': 'BREATH'},
                   {'t': TAKEOFF, 'name': 'TAKEOFF'}, {'t': TAKEOFF + 30, 'name': 'FLY'},
                   {'t': AIR, 'name': 'AIR_BREATH'}],
         'actions': [{'t': FIRE1, 'do': 'breathe', 'target': BOULDER_FACE},
                     {'t': FIRE2, 'do': 'breathe'},
                     {'t': FIRE3, 'do': 'breathe', 'target': roof_target}]}
    boy = {'id': 'boy', 'type': 'player', 'name': 'Champion', 'skin': {'texture': 'harry'},
           'items': {'mainhand': 'hogwartsmagic:harry_potter_wand'},
           'path': [{'t': 0, 'pos': [0.5, 0.0, 18.0]}],
           'look': [{'t': 0, 'yaw': 180, 'pitch': 0}, {'t': 100, 'yaw': 180, 'pitch': 0},
                    {'t': 112, 'yaw': 160, 'pitch': 20}, {'t': 180, 'yaw': 180, 'pitch': -10}],
           'poses': [{'t': 108, 'name': 'cower', 'blend': 4}, {'t': 175, 'name': 'standing', 'blend': 8}],
           'actions': [{'t': 106, 'do': 'sneak'}, {'t': 176, 'do': 'stand'}]}
    scene = {'title': 'Dev dragon fire', 'length': LENGTH, 'radius': R, 'poses': {'standing': {}},
             'actors': [h, boy]}

    # Her head aimed at each target through each breath (the mirror's head look), and swept for the second.
    k1 = solve_look(scene, FIRE1 + 20, BOULDER_FACE, [])
    sweep_l, sweep_r = [-6, -32, 0], [-6, 32, 0]
    keys = [{'t': 0, 'v': [0, 0, 0]}, {'t': WINDUP1, 'v': [0, 0, 0]}, {'t': BREATH1, 'v': k1}, {'t': BREATH1 + 46, 'v': k1},
            {'t': BREATH1 + 58, 'v': [0, 0, 0]},
            {'t': WINDUP2 + 8, 'v': [0, 0, 0]}, {'t': BREATH2, 'v': sweep_l}, {'t': FIRE2 + 4, 'v': sweep_l},
            {'t': FIRE2 + 36, 'v': sweep_r}, {'t': BREATH2 + 58, 'v': [0, 0, 0]}]
    k3 = solve_look(scene, FIRE3 + 20, roof_target, keys)
    keys += [{'t': AIR, 'v': [0, 0, 0]}, {'t': FIRE3, 'v': k3}, {'t': FIRE3 + 40, 'v': k3}, {'t': FIRE3 + 52, 'v': [0, 0, 0]}]
    h['limbs'] = {D.HEAD_LOOK: {'rot': keys}}

    fills = boxes() + cfills
    world = world_of(fills, [])

    def at_of(t):
        return [round(v, 2) for v in D.horntail_frame(scene, 'h', t)[0]]

    events = [{'t': 0, 'kind': 'effect', 'id': 'eyes_open'},
              {'t': 0, 'kind': 'effect', 'id': 'sky', 'at': [0, 0, 0], 'style': 'day'},
              {'t': ROAR, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.roar', 'at': at_of(ROAR), 'volume': 2.0}]
    for t in (FIRE1, FIRE2, FIRE3):
        events.append({'t': t, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.fire_breath', 'at': at_of(t), 'volume': 1.6})
        events.append({'t': t, 'kind': 'sound', 'id': 'minecraft:item.firecharge.use', 'at': at_of(t), 'volume': 1.2})
    flaps = D.downstroke_ticks(h, 0, LENGTH)
    for t in flaps:
        events.append({'t': t, 'kind': 'sound', 'id': 'hogwartsmagic:horntail.flap', 'at': at_of(t), 'volume': 1.1})
    events.sort(key=lambda e: e['t'])

    mouth3 = D.horntail_snout(scene, 'h', FIRE3 + 10)[0]
    mid3 = [(mouth3[i] + roof_target[i]) / 2 for i in range(3)]
    cam3 = [mid3[0] + side[0] * 15.0 - fwd[0] * 3.0, 5.0, mid3[2] + side[2] * 15.0 - fwd[2] * 3.0]

    def follow(t0, t1, off, step=10, fov=64):
        out = []
        for t in range(t0, t1 + 1, step):
            p = D.horntail_frame(scene, 'h', t)[0]
            out.append({'t': t, 'pos': r3([p[0] + off[0], max(2.5, p[1] + off[1]), p[2] + off[2]]),
                        'look_at': 'actor:h', 'fov': fov, 'ease': 'spline'})
        out[0].pop('ease')
        out[0]['cut'] = True
        out[-1]['ease'] = 'in_out'
        return out

    camera = [
        # The dragon on her rock, roaring.
        {'t': 0, 'pos': [13.0, 3.5, 15.0], 'look_at': 'actor:h', 'fov': 62},
        {'t': 78, 'pos': [11.0, 3.8, 13.5], 'look_at': 'actor:h', 'fov': 60, 'ease': 'in_out'},
        # Side on: the jet from her mouth to the boulder.
        {'t': 80, 'pos': [-14.0, 4.5, 7.0], 'look_at': [0.5, 2.6, 8.5], 'fov': 60, 'cut': True},
        {'t': 134, 'pos': [-12.0, 4.0, 8.5], 'look_at': [0.5, 2.4, 9.0], 'fov': 56, 'ease': 'in_out'},
        # Behind the boulder with the boy: the fire splashing over its face.
        {'t': 135, 'pos': [3.8, 1.5, 20.6], 'look_at': [0.0, 2.6, 10.0], 'fov': 66, 'cut': True},
        {'t': 178, 'pos': [3.2, 1.6, 20.2], 'look_at': [0.0, 2.4, 11.0], 'fov': 62, 'ease': 'in_out'},
        # High three-quarter: the sweep across the lawn.
        {'t': 180, 'pos': [17.0, 10.0, 13.0], 'look_at': [0.0, 1.0, 7.0], 'fov': 66, 'cut': True},
        {'t': 296, 'pos': [15.0, 9.0, 16.0], 'look_at': [0.0, 1.5, 7.0], 'fov': 64, 'ease': 'in_out'},
    ]
    camera += follow(298, 468, [-14.0, 3.0, -12.0])
    camera += [
        # Beside the cottage: the air breath on its roof.
        {'t': 470, 'pos': r3(cam3), 'look_at': r3(mid3), 'fov': 62, 'cut': True},
        {'t': 590, 'pos': r3([cam3[0] - fwd[0] * 2, cam3[1] + 0.8, cam3[2] - fwd[2] * 2]), 'look_at': r3(mid3), 'fov': 58,
         'ease': 'in_out'},
        # The three scorches, embers dying.
        {'t': 592, 'pos': [24.0, 16.0, -4.0], 'look_at': [4.0, 0.0, 14.0], 'fov': 72, 'cut': True},
        {'t': LENGTH, 'pos': [22.0, 15.0, -1.0], 'look_at': [5.0, 0.0, 15.0], 'fov': 70, 'ease': 'in_out'},
    ]
    set_fills = [{'block': b, 'from': a, 'to': c} for b, a, c in fills]
    scene.update({
        'set': {'fills': set_fills, 'time_of_day': 6000, 'build_ticks': 80},
        'events': events,
        'camera': camera,
    })
    return scene, world, flaps, {'boulder': BOULDER_FACE, 'roof': roof_target, 'look1': k1, 'look3': k3,
                                 'cottage': (at, ridge)}


def checks(scene, world, flaps, info):
    problems = []
    h = E.actor_by_id(scene, 'h')
    # Every breathe lies inside its fire window (assert 12; the validator checks it too).
    for t0, dur, _ in D.breathes(h):
        w = D.fire_window(h, t0)
        if w is None or t0 < w[0] - 1e-6 or t0 + dur > w[1] + 1e-6:
            problems.append('breathe at %s outside its fire window %s' % (t0, w))
    # The jets land: at full reach the ray meets the boulder's face and the roof.
    hits = {}
    for name, t0, target in (('boulder', FIRE1, info['boulder']), ('roof', FIRE3, info['roof'])):
        best = 1e9
        for t in range(t0 + 15, t0 + 40):
            jet = D.fire_jet(scene, 'h', t, world)
            o, d, length = jet
            end = [o[i] + d[i] * length for i in range(3)]
            best = min(best, math.dist(end, target))
        hits[name] = best
        if best > 1.0:
            problems.append('the %s jet ends %.2f from its target' % (name, best))
    # The sweep's jet meets the lawn all the way across.
    short = [t for t in range(FIRE2 + 16, FIRE2 + 40, 2) if D.fire_jet(scene, 'h', t, world)[2] >= D.FIRE_MAX_REACH - 1e-6]
    if short:
        problems.append('the sweep misses the ground at %s' % short)
    # The jets leave along her head: the aim strays at most 12 degrees from her head while she breathes at a target.
    worst_aim = 0.0
    for t0, dur, target in D.breathes(h):
        if target is None:
            continue
        for t in range(int(t0), int(t0 + dur)):
            worst_aim = max(worst_aim, aim_error(scene, t, target))
    if worst_aim > 12:
        problems.append('the jet strays %.1f degrees from her head' % worst_aim)
    # The fire never touches the boy, nor the camera.
    worst_boy, worst_cam = 1e9, 1e9
    boy = E.actor_by_id(scene, 'boy')
    for t4 in range(FIRE1 * 4, (FIRE3 + 41) * 4):
        t = t4 / 4.0
        for hy in (0.2, 0.7, 1.2):
            p = E.sample_path(boy['path'], t)
            c = D.fire_clearance(scene, 'h', t, world, [p[0], p[1] + hy, p[2]])
            if c is not None:
                worst_boy = min(worst_boy, c)
        c = D.fire_clearance(scene, 'h', t, world, E.engine_cam(scene['camera'], t))
        if c is not None:
            worst_cam = min(worst_cam, c)
    if worst_boy < 1.5:
        problems.append('the fire comes %.2f from the boy' % worst_boy)
    # A jet landing on the lawn just before the boulder splashes up to its face, never on through the rock.
    o, _ = D.horntail_snout(scene, 'h', FIRE1 + 20)
    short = [0.5, 0.0, BOULDER_FACE[2] - 1.5]
    d = D._norm([short[i] - o[i] for i in range(3)])
    hit, n = D.ray_hit(world, o, d, D.FIRE_MAX_REACH)
    held = D.splash_centres(world, o, d, hit, n, 1.0)
    loose = D.splash_centres(world, o, d, hit, n, 1.0, clamp=False)

    def into_rock(cs):
        return max(c[2] for c, _ in cs if -3.5 <= c[0] <= 5.5 and c[1] < 3.0) - BOULDER_FACE[2]
    splash_in, loose_in = into_rock(held), into_rock(loose)
    if n is None or splash_in > 0:
        problems.append('a jet landing before the boulder splashes %.2f into it' % splash_in)
    # Her mouth as the fire estimates it when she is not drawn (a late joiner) stays near the drawn snout.
    worst_mouth = 0.0
    for t0, dur, _ in D.breathes(h):
        for k in range(0, int(dur * 4), 2):          # every other puff's birth (DragonFire.birth: 4 a tick)
            t = t0 + k / 4.0
            worst_mouth = max(worst_mouth, math.dist(D.mouth_estimate(h, t)[0], D.horntail_snout(scene, 'h', t)[0]))
    if worst_mouth > 0.5:
        problems.append('the mouth estimate strays %.2f from her snout while she breathes' % worst_mouth)
    if worst_cam < 1.0:
        problems.append('the fire comes %.2f from the camera' % worst_cam)
    # The dragon clear of the set in flight: every capsule 0.8 from the lawn (top y 0) and from the cottage's box.
    at, ridge = info['cottage']
    box = ([at[0], 0, at[2] - 1], [at[0] + 7, ridge[1] + 1, at[2] + 10])
    worst = (1e9, None, None)
    for t in range(TAKEOFF + 40, LENGTH, 2):
        for name, (a, b, r) in D.horntail_parts(scene, 'h', t):
            for k in range(5):
                q = [a[i] + (b[i] - a[i]) * k / 4.0 for i in range(3)]
                gap = q[1] - r
                out = [max(box[0][i] - q[i], 0.0, q[i] - box[1][i]) for i in range(3)]
                gap = min(gap, math.sqrt(sum(c * c for c in out)) - r)
                if gap < worst[0]:
                    worst = (gap, t, name)
    print('flight: worst capsule gap to the lawn or the cottage %.2f (t %s, %s)' % worst)
    if worst[0] < 0.8:
        problems.append('the dragon comes within %.2f of the set at t %s (%s)' % worst)
    for t in range(0, LENGTH, 2):
        s = D.horntail_cue(h, t)
        y = D.horntail_frame(scene, 'h', t)[0][1]
        if s['flying'] and y < NEST_TOP + 0.01 and TAKEOFF + 2 < t:
            problems.append('flying on the rock at %d' % t)
        if not s['flying'] and y > NEST_TOP + 0.5:
            problems.append('grounded in the air at %d (%s)' % (t, s['name']))
    acc = 0.0
    keys = D.path_of(h)
    for t in range(TAKEOFF + 32, LENGTH - 2):
        acc = max(acc, abs(E.path_speed(keys, t + 1) - E.path_speed(keys, t)))
    if acc > 0.08:
        problems.append('speed change %.3f' % acc)
    for t in flaps:
        if math.cos(D.stroke_angle(D.wing_phase(h, t))) < 0.9:
            problems.append('flap at %d off the downstroke' % t)
    # The camera stays in air.
    for t, c, _ in E.camera_samples(scene['camera'], LENGTH):
        if (math.floor(c[0]), math.floor(c[1]), math.floor(c[2])) in world:
            problems.append('the camera is inside a block at t %.2f' % t)
            break
    probs = []
    E.check_spline_moves(SID, scene, probs)
    problems += probs
    print('fire: boulder jet ends %.2f from its target, roof jet %.2f; aim off her head %.1f degrees at most; '
          'head looks %s and %s' % (hits['boulder'], hits['roof'], worst_aim, info['look1'], info['look3']))
    print('fire: at least %.2f from the boy, %.2f from the camera; %d wingbeats; worst speed change %.4f; spline moves %s'
          % (worst_boy, worst_cam, len(flaps), acc, probs or 'ok'))
    print('fire: a jet landing 1.5 before the boulder splashes to %.2f of its face (%.2f into it unheld); the mouth '
          'estimate within %.2f of her snout while she breathes' % (-splash_in, loose_in, worst_mouth))
    return problems


def snout_table(scene):
    rows = []
    for t in range(0, LENGTH + 1):
        p, _ = D.horntail_snout(scene, 'h', t)
        rows.append([t] + [round(c, 4) for c in p])
    return {'h': rows}


def main():
    scene, world, flaps, info = build()
    problems = checks(scene, world, flaps, info)
    for p in problems:
        print('PROBLEM', p)
    os.makedirs(OUT, exist_ok=True)
    txt = dump(scene, 0, 150) + '\n'
    assert len(txt) < 900000
    with open(os.path.join(OUT, SID + '.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(txt)
    with open(os.path.join(OUT, SID + '_snout.json'), 'w', newline='\n', encoding='utf-8') as f:
        f.write(dump(snout_table(scene), 0, 150) + '\n')
    print('wrote', os.path.join(OUT, SID + '.json'), len(txt), 'chars, and the snout side file')
    sys.exit(1 if problems else 0)


if __name__ == '__main__':
    main()
