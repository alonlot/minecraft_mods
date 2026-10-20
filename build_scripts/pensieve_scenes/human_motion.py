"""Seat geometry of the player model, mirrored from pensieve/scene/HumanMotion.java, for scene generators.

How the player model really stands in the world (PlayerRenderer / LivingEntityRenderer): the model is scaled by
15/16 and hung from 1.501 blocks, +y down. A leg is a 4 x 12 x 4 px box from its hip pivot (model y 12), and the
pants layer grows it by 0.25 px on every side. The SIT_DOWN / STAND_UP / CHEER_STAND timing curves are here too,
so a generator can sample a seated or transitioning actor's leg boxes and assert they stay out of the set.

Kept in step with the Java by human_motion_cases.json: `python human_motion.py` checks this file against it,
`python human_motion.py --write` rewrites it, and HumanMotionTest.pythonMirrorAgrees checks the Java against it.

Typical use:
    root_y = seated_root_y(seat_top, scale)                      # feet root of a seated actor
    corners = leg_box(root, yaw, scale, pitch=-90, splay=8, side='right')
    sit = sit_down_root(s, stand_root, seat_root)                # where SIT_DOWN puts the root s seconds in
"""
import json
import math
import os
import sys

MODEL_SCALE = 0.9375          # PlayerRenderer
MODEL_ORIGIN = 1.501          # LivingEntityRenderer: model y 0 is this many model blocks above the feet
HIP_PX = 12.0                 # leg pivot (model y)
LEG_LENGTH_PX = 12.0
LEG_HALF_WIDTH_PX = 2.0
LAYER_PX = 0.25               # the pants layer
SEAT_EDGE_AHEAD = 0.5         # the seat front edge, ahead of the seated hip (blocks)
STAND_CLEARANCE = 0.05        # gap kept in front of the edge when standing up
SEAT_HEIGHT = 0.5             # seat top above the floor, assumed by CHEER_STAND only

SIT_DOWN_LEN = 1.0            # seconds
STAND_UP_LEN = 1.0

CASES = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'human_motion_cases.json')


def px_blocks(scale):
    return scale * MODEL_SCALE / 16.0


def hip_height(scale):
    """Hip pivot above the feet root, blocks."""
    return (MODEL_ORIGIN * 16 - HIP_PX) * px_blocks(scale)


def leg_half_width(scale):
    return (LEG_HALF_WIDTH_PX + LAYER_PX) * px_blocks(scale)


def seated_hip_lift(scale):
    """The hip pivot sits this far above the seat top so level thighs rest on the seat, not in it."""
    return leg_half_width(scale)


def seated_root_y(seat_top, scale):
    """Feet root height that seats an actor (legs at -90) on a seat whose top is at seat_top."""
    return seat_top + seated_hip_lift(scale) - hip_height(scale)


def stand_ahead(scale):
    """How far ahead of the seated hip the hip stands when up: clear of the seat edge."""
    return SEAT_EDGE_AHEAD + leg_half_width(scale) + STAND_CLEARANCE


def stand_rise(scale):
    """Hips rise from seated to standing on a SEAT_HEIGHT seat (negative: they drop)."""
    return hip_height(scale) - SEAT_HEIGHT - seated_hip_lift(scale)


def leg_lowest_below_hip(pitch, scale):
    a = math.radians(pitch)
    along = max(-LAYER_PX * math.cos(a), (LEG_LENGTH_PX + LAYER_PX) * math.cos(a))
    return (along + (LEG_HALF_WIDTH_PX + LAYER_PX) * abs(math.sin(a))) * px_blocks(scale)


def _turned(x, y, z, a, b, c):
    """(x, y, z) turned as a model part turns it: X, then Y, then Z."""
    y1 = y * math.cos(a) - z * math.sin(a)
    z1 = y * math.sin(a) + z * math.cos(a)
    x2 = x * math.cos(b) + z1 * math.sin(b)
    z2 = -x * math.sin(b) + z1 * math.cos(b)
    x3 = x2 * math.cos(c) - y1 * math.sin(c)
    y3 = x2 * math.sin(c) + y1 * math.cos(c)
    return x3, y3, z2


def model_to_world(root, yaw, scale, mx, my, mz):
    k = px_blocks(scale)
    lx, ly, lz = -mx * k, (MODEL_ORIGIN * 16 - my) * k, mz * k
    r = math.radians(180 - yaw)
    wx = lx * math.cos(r) + lz * math.sin(r)
    wz = -lx * math.sin(r) + lz * math.cos(r)
    return [root[0] + wx, root[1] + ly, root[2] + wz]


def leg_box(root, yaw, scale, pitch, splay=0.0, roll=0.0, side='right', offset_px=(0.0, 0.0, 0.0)):
    """The 8 world corners of a leg box (pants layer included). pitch/splay/roll: the leg's x/y/z rotation in
    degrees as a pose writes them (seated: pitch -90, splay 8 on the right leg, -8 on the left); offset_px: any
    offset added to the leg pivot (CHEER_STAND's root steering, in model px)."""
    px = (-1.9 if side == 'right' else 1.9) + offset_px[0]
    py, pz = HIP_PX + offset_px[1], offset_px[2]
    a, b, c = math.radians(pitch), math.radians(splay), math.radians(roll)
    w = LEG_HALF_WIDTH_PX + LAYER_PX
    out = []
    for x in (-w, w):
        for y in (-LAYER_PX, LEG_LENGTH_PX + LAYER_PX):
            for z in (-w, w):
                v = _turned(x, y, z, a, b, c)
                out.append(model_to_world(root, yaw, scale, px + v[0], py + v[1], pz + v[2]))
    return out


# ---- the seat clips' timing (HumanMotion.sitDownCurve / standUpCurve / cheerStandCurve) -------------------------
def _smooth(x):
    if x <= 0:
        return 0.0
    if x >= 1:
        return 1.0
    return x * x * (3 - 2 * x)


def sit_down_curve(s, rising):
    """SIT_DOWN s seconds in: (leg pitch, horizontal root fraction, vertical root fraction)."""
    return (-90 * _smooth((s - 0.05) / 0.40), _smooth((s - 0.30) / 0.45),
            _smooth((s - 0.05) / 0.35) if rising else _smooth((s - 0.50) / 0.35))


def stand_up_curve(s, rising):
    """STAND_UP s seconds in: (leg pitch, horizontal root fraction, vertical root fraction)."""
    return (-90 * (1 - _smooth((s - 0.55) / 0.35)), _smooth((s - 0.15) / 0.40),
            _smooth((s - 0.10) / 0.40) if rising else _smooth((s - 0.55) / 0.30))


def cheer_stand_curve(q, scale):
    """CHEER_STAND at q (its time scaled to a 3 s clip; the real one lasts 2.7-3.3 s per actor):
    (leg pitch, forward fraction of stand_ahead, up fraction of stand_rise). The path stays at the seated root."""
    fwd = _smooth((q - 0.20) / 0.40) * (1 - _smooth((q - 2.30) / 0.40))
    down = _smooth((q - 0.55) / 0.25) * (1 - _smooth((q - 2.00) / 0.30))
    up = _smooth((q - 0.20) / 0.40) * (1 - _smooth((q - 2.35) / 0.35)) if stand_rise(scale) > 0 else down
    return (-90 + 84 * down, fwd, up)


def _transition_root(curve, s, start, end):
    rising = end[1] > start[1] + 1e-4
    pitch, h, v = curve(s, rising)
    root = [start[0] + (end[0] - start[0]) * h, start[1] + (end[1] - start[1]) * v,
            start[2] + (end[2] - start[2]) * h]
    return root, pitch


def sit_down_root(s, stand_root, seat_root):
    """Where SIT_DOWN puts the feet root s seconds after its cue (the path at the cue is stand_root, 1 s later
    seat_root), and the leg pitch then."""
    return _transition_root(sit_down_curve, s, stand_root, seat_root)


def stand_up_root(s, seat_root, stand_root):
    """Where STAND_UP puts the feet root s seconds after its cue, and the leg pitch then."""
    return _transition_root(stand_up_curve, s, seat_root, stand_root)


def cheer_stand_root(q, seat_root, yaw, scale):
    """Where CHEER_STAND puts the feet root at q (3 s scale) for an actor seated at seat_root facing yaw."""
    pitch, fwd, up = cheer_stand_curve(q, scale)
    r = math.radians(yaw)
    ahead = fwd * stand_ahead(scale)
    return [seat_root[0] - math.sin(r) * ahead, seat_root[1] + up * stand_rise(scale),
            seat_root[2] + math.cos(r) * ahead], pitch


# ---- cross-check with the Java ------------------------------------------------------------------------------
def cases():
    out = {'constants': {'MODEL_SCALE': MODEL_SCALE, 'MODEL_ORIGIN': MODEL_ORIGIN, 'HIP_PX': HIP_PX,
                         'LEG_LENGTH_PX': LEG_LENGTH_PX, 'LEG_HALF_WIDTH_PX': LEG_HALF_WIDTH_PX, 'LAYER_PX': LAYER_PX,
                         'SEAT_EDGE_AHEAD': SEAT_EDGE_AHEAD, 'STAND_CLEARANCE': STAND_CLEARANCE,
                         'SEAT_HEIGHT': SEAT_HEIGHT},
           'scales': [], 'curves': [], 'boxes': []}
    for sc in (0.75, 0.9, 1.0, 1.5):
        out['scales'].append({'scale': sc, 'hip_height': hip_height(sc), 'seated_root_y_0_5': seated_root_y(0.5, sc),
                              'stand_ahead': stand_ahead(sc), 'stand_rise': stand_rise(sc),
                              'lowest_m90': leg_lowest_below_hip(-90, sc), 'lowest_m65': leg_lowest_below_hip(-65, sc),
                              'lowest_0': leg_lowest_below_hip(0, sc)})
    for i in range(23):
        s = i * 0.05
        out['curves'].append({'s': s, 'sit_rise': list(sit_down_curve(s, True)), 'sit_fall': list(sit_down_curve(s, False)),
                              'stand_rise': list(stand_up_curve(s, True)), 'stand_fall': list(stand_up_curve(s, False)),
                              'cheer_075': list(cheer_stand_curve(s * 3 / 1.1, 0.75)),
                              'cheer_1': list(cheer_stand_curve(s * 3 / 1.1, 1.0))})
    for root, yaw, sc, pitch, splay, side in (([1.5, 0.2, -2.0], 180, 0.75, -90, 8, 'right'),
                                              ([0.0, 0.0, 0.0], 37, 1.0, -65, -8, 'left'),
                                              ([3.0, 1.0, 4.0], -90, 0.9, 0, 0, 'right')):
        out['boxes'].append({'root': root, 'yaw': yaw, 'scale': sc, 'pitch': pitch, 'splay': splay, 'side': side,
                             'corners': leg_box(root, yaw, sc, pitch, splay, 0, side)})
    return out


def _close(a, b, path=''):
    if isinstance(a, dict):
        assert a.keys() == b.keys(), path
        for k in a:
            _close(a[k], b[k], path + '.' + k)
    elif isinstance(a, list):
        assert len(a) == len(b), path
        for i, (x, y) in enumerate(zip(a, b)):
            _close(x, y, '%s[%d]' % (path, i))
    elif isinstance(a, str):
        assert a == b, path
    else:
        assert abs(a - b) < 1e-9, '%s: %r != %r' % (path, a, b)


if __name__ == '__main__':
    if '--write' in sys.argv:
        with open(CASES, 'w', encoding='utf-8') as f:
            json.dump(cases(), f, indent=1)
        print('wrote', CASES)
    else:
        with open(CASES, encoding='utf-8') as f:
            _close(cases(), json.load(f))
        print('human_motion.py agrees with', os.path.basename(CASES))
