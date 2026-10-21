"""The Flying memory's scenes (Task 10) - WORK IN PROGRESS.

Done: the shared helpers, the engine-mirror checks (spline moves, 60 fps smoothness, clearance against player parts,
brooms and balls, camera air, hand contacts, items, ride edges, fliers, lying brooms, lines, keys, walkers,
framing, lawn void fraction) and the lesson scene builder lesson(h, world), which passes all of them.
Not done: the chase, match and snitch scenes, main() (writing memories/flying_*.json), the mutation bites, the
build-budget assert, the SceneDataTest tests. See .superpowers/sdd/2026-10-21-flying-memory/task-10-report.md.
Nothing here writes a scene yet.
"""
import copy
import hashlib
import json
import math
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from scene_common import MEMORIES, dump  # noqa: E402
import scene_engine as E  # noqa: E402
import human_motion as HM  # noqa: E402

MAX_JSON = 900000
HOUSES = ('gryffindor', 'hufflepuff', 'ravenclaw', 'slytherin')
KID, THIRD, ADULT = 0.75, 0.85, 1.0
SFX = 'hogwartsmagic:pensieve.sfx.flying_'
VOICE = 'hogwartsmagic:pensieve.voice.'
RES = os.path.normpath(os.path.join(MEMORIES, '..', '..', '..'))
REGISTERED = set(json.load(open(os.path.join(RES, 'assets', 'hogwartsmagic', 'sounds.json'), encoding='utf-8')))
CLIP = json.load(open(os.path.join(HERE, '..', 'sounds', 'flying_clip_lengths.json'), encoding='utf-8'))
SKINS = set(json.load(open(os.path.join(MEMORIES, '_skins.json'), encoding='utf-8')))
REMEMBRALL, QUAFFLE, BLUDGER, SNITCH = ('hogwartsmagic:memory_remembrall', 'hogwartsmagic:memory_quaffle',
                                        'hogwartsmagic:memory_bludger', 'hogwartsmagic:golden_snitch')
BROOM, RACING = 'hogwartsmagic:memory_broom', 'hogwartsmagic:memory_broom_racing'
WHISTLE, BAT = 'hogwartsmagic:whistle', 'minecraft:mace'
SNITCH_ITEM, REMEMBRALL_ITEM = 'hogwartsmagic:golden_snitch', 'hogwartsmagic:memory_remembrall'


def rival(h):
    """The viewer's opponent: Slytherin, or Gryffindor when the viewer is a Slytherin."""
    return 'gryffindor' if h == 'slytherin' else 'slytherin'


def robes(h):
    return 'hogwartsmagic:%s_robes' % h


# The spoken lines: (speaker shown in the subtitle, subtitle text). Wood's subtitle reads "got to" (his take says
# "gotta"; Task 9's ruling). No line names the boy: he is "you" or "the Seeker", his name only ever {viewer}.
LINES = {
    'flying_hooch_welcome': ('Madam Hooch', "Good afternoon, class. Don't stand there gawping, find a broom."),
    'flying_hooch_left': ('Madam Hooch', 'Stand on the left of it. Right hand out, flat, over the handle.'),
    'flying_hooch_up': ('Madam Hooch', "Now, firmly, and mean it: say 'Up!'"),
    'flying_class_up': ('The class', 'Up!'),
    'flying_viewer_up': ('{viewer}', 'Up!'),
    'flying_neville_whoa': ('Neville', "It's going on its own! I can't stop it, help!"),
    'flying_hooch_nobody': ('Madam Hooch', "Nobody moves! Feet on the grass until I'm back, or you'll answer for it."),
    'flying_malfoy_look': ('Malfoy', 'Look what the clumsy idiot dropped.'),
    'flying_malfoy_come': ('Malfoy', 'Want it? Come up and take it, then.'),
    'flying_malfoy_catch': ('Malfoy', 'Go on, then. Fetch!'),
    'flying_mcg_follow': ('Professor McGonagall', 'You. With me. Now.'),
    'flying_hooch_clean': ('Madam Hooch', 'I want a clean game, from all of you.'),
    'flying_captain': ('Wood', "Just catch the Snitch. That's all you've got to do."),
    'flying_comm_off': ('Commentator', "And they're off!"),
    'flying_comm_goal': ('Commentator', "And it's in!"),
    'flying_comm_seeker': ('Commentator', 'The Seeker has seen something!'),
}
for _l, (_who, _txt) in LINES.items():
    assert 'Harry' not in _txt and 'Potter' not in _txt and '{' not in _txt, _l
assert LINES['flying_captain'][1].endswith("you've got to do."), 'Wood: got to'

# Music: the user has not supplied the tracks yet (ruling: build without music, leave the slots). Each slot is the
# scene it starts in and its time there, with about 2 s of overlap across a hand-over (the outgoing cue fades over
# the chain's last 20 ticks while the next one starts at 0). flying_music.py cuts the user's tracks to these when
# they arrive; until then no music event is emitted (an unregistered id would fail validation).
MUSIC_SLOTS = {
    'flying_lesson': ('', 0.0, "The Norwegian Ridgeback and a Change of Season / Hedwig's Theme: the lesson"),
    'flying_chase': ('_chase', 0.0, 'the Remembrall chase and the catch'),
    'flying_match': ('_match', 0.0, 'Quidditch, Third Year: the walk-out to the hunt'),
    'flying_snitch': ('_snitch', 0.0, 'Quidditch, Third Year: the dive and the catch'),
}


def tk(s):
    return int(round(s * 20))


def r4(v):
    return [round(c, 4) for c in v]


def r3(v):
    return [round(c, 3) for c in v]


def add(a, b, f=1.0):
    return [a[i] + b[i] * f for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def lerp(a, b, f):
    return [a[i] + (b[i] - a[i]) * f for i in range(len(a))]


def smooth(f):
    f = E.clamp(f, 0.0, 1.0)
    return f * f * (3 - 2 * f)


def smoother(f):
    f = E.clamp(f, 0.0, 1.0)
    return f * f * f * (f * (f * 6 - 15) + 10)


def yaw_to(dx, dz):
    return math.degrees(math.atan2(-dx, dz))


def ahead(p, yaw, d, side=0.0, y=None):
    """p moved d along yaw and `side` to the actor's right."""
    fx, fz = E.fwd_vec(yaw)
    rx, rz = -fz, fx
    return [p[0] + fx * d + rx * side, p[1] if y is None else y, p[2] + fz * d + rz * side]


def unwrap(y, ref):
    while y - ref > 180:
        y -= 360
    while y - ref < -180:
        y += 360
    return y


def norm(v):
    n = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / n for c in v]


# ================================================================ key lists
class Keys:
    """An actor `path` (or a camera-like list): keys in ticks, kept in order. `ease` is the segment ending at the
    key. A key at the same tick as the last replaces it."""

    def __init__(self, field='pos'):
        self.k = []
        self.field = field

    def at(self, t, v, ease=None):
        k = {'t': t, self.field: r4(v)}
        if ease and ease != 'in_out':
            k['ease'] = ease
        if self.k and abs(self.k[-1]['t'] - t) < 1e-9:
            self.k[-1] = k
        else:
            assert not self.k or t > self.k[-1]['t'], (t, self.k[-1]['t'])
            self.k.append(k)
        return self

    def last(self):
        return self.k[-1][self.field]

    def hold(self, t):
        """Stay where the last key is until t (an eased hold: rest at both ends)."""
        if t > self.k[-1]['t'] + 1e-9:
            self.at(t, self.last())
        return self

    def run(self, t0, t1, fn, step, settle=True):
        """A spline run from the last key (at t0) to t1, sampled from fn(t) every `step` ticks; the last key is
        in_out when settle (the run comes to rest there) else spline."""
        assert abs(self.k[-1]['t'] - t0) < 1e-9, (self.k[-1]['t'], t0)
        n = max(1, int(round((t1 - t0) / step)))
        for i in range(1, n + 1):
            t = t0 + (t1 - t0) * i / n
            self.at(t, fn(t), 'in_out' if (i == n and settle) else 'spline')
        return self

    def through(self, pts, settle=True):
        """Spline keys through [(t, pos)], the last at rest when settle."""
        for i, (t, p) in enumerate(pts):
            self.at(t, p, 'in_out' if (i == len(pts) - 1 and settle) else 'spline')
        return self


def look_keys(pts):
    """[(t, yaw, pitch, roll or None, ease)] -> look keys."""
    out = []
    for t, y, p, r, e in pts:
        k = {'t': t, 'yaw': round(y, 3), 'pitch': round(p, 3)}
        if r is not None:
            k['roll'] = round(r, 3)
        if e and e != 'in_out':
            k['ease'] = e
        if out and abs(out[-1]['t'] - t) < 1e-9:
            out[-1] = k
        else:
            out.append(k)
    return out


def limb(rot=None, offset=None, mode='add'):
    d = {}
    if rot:
        d['rot'] = rot
    if offset:
        d['offset'] = offset
    if mode != 'add':
        d['mode'] = mode
    return d


class Channel:
    """A limb channel (rot or offset): keys of [x, y, z] in ticks; `to` eases from the held value."""

    def __init__(self, v0=(0.0, 0.0, 0.0)):
        self.k = [{'t': 0, 'v': [round(c, 2) for c in v0]}]

    def v(self):
        return self.k[-1]['v']

    def to(self, t0, t1, v, ease=None):
        """Hold the current value to t0, then move to v by t1."""
        if t0 > self.k[-1]['t'] + 1e-9:
            self.k.append({'t': t0, 'v': list(self.v())})
        k = {'t': t1, 'v': [round(c, 2) for c in v]}
        if ease and ease != 'in_out':
            k['ease'] = ease
        assert t1 > self.k[-1]['t'], (t1, self.k[-1]['t'])
        self.k.append(k)
        return self

    def set(self, t, v, ease='linear'):
        k = {'t': t, 'v': [round(c, 2) for c in v]}
        if ease and ease != 'in_out':
            k['ease'] = ease
        if abs(self.k[-1]['t'] - t) < 1e-9:
            self.k[-1] = k
        else:
            assert t > self.k[-1]['t'], (t, self.k[-1]['t'])
            self.k.append(k)
        return self

    def used(self):
        return len(self.k) > 1 or any(abs(c) > 1e-6 for c in self.k[0]['v'])


class Rig:
    """An actor's add-mode limbs: rot and offset channels per part."""

    def __init__(self):
        self.rot, self.off = {}, {}

    def r(self, part):
        return self.rot.setdefault(part, Channel())

    def o(self, part):
        return self.off.setdefault(part, Channel())

    def emit(self):
        out = {}
        for part in sorted(set(self.rot) | set(self.off)):
            d = {}
            if part in self.rot and self.rot[part].used():
                d['rot'] = self.rot[part].k
            if part in self.off and self.off[part].used():
                d['offset'] = self.off[part].k
            if d:
                out[part] = d
        return out


def bend_pose(a):
    """Add-mode (rot, offset px) for the torso pitched `a` degrees forward about the hips, the neck and shoulders
    carried round with it (HumanMotion.hipTurn done whole): {part: (rot add, offset add)}."""
    r = math.radians(a)
    nk = [0.0, 12 - 12 * math.cos(r), -12 * math.sin(r)]
    sh = [0.0, 10 - 10 * math.cos(r), -10 * math.sin(r)]
    return {'body': ([a, 0, 0], nk), 'head': ([0, 0, 0], nk), 'right_arm': ([a, 0, 0], sh),
            'left_arm': ([a, 0, 0], sh)}


# ================================================================ a scene under construction
class Sc:
    def __init__(self, sid, title, length_s, world, house):
        self.sid, self.title, self.world, self.h = sid, title, world, house
        self.L = tk(length_s)
        self.length = length_s
        self.actors, self.events, self.words, self.camera, self.poses = [], [], [], [], {}
        self.lines = []
        self.notes = {}           # facts the checks need: contacts, fliers, windows, ...
        self.contacts = []        # (label, actor id, side, t, target fn(scene) -> point, kind)
        self.lying = []           # (broom id, until tick)
        self.flier_ok = {}        # broom id -> [(t0, t1)] ticks where it may touch blocks (lying, mount, landing)
        self.speed_ok = {}        # broom id -> [(t0, t1, reason)] scripted speed-change exceptions
        self.first_person = []    # (t0, t1, actor id) ticks: first-person shots (the head is exempt)
        self.cause = []           # (tick, kind, label): goals, near misses, catches, entrances: what a reaction answers
        self.reactions = []       # (tick, style, label)
        self.sfx_hits = []        # (sfx name, tick, contact tick, label)
        self.hidden = {}          # actor id -> [(t0, t1)] ticks off stage (a teleport, out of every shot)
        self.framing = []         # (t0, t1, fn(scene, t) -> point, label): what the shot list says is in frame

    def unseen_at(self, t):
        return {aid for aid, wins in self.hidden.items() if in_windows(t, wins)}

    def ev(self, t, kind, eid, **kw):
        assert 0 <= t <= self.L, (self.sid, kind, eid, t)
        e = {'t': t, 'kind': kind, 'id': eid}
        e.update(kw)
        self.events.append(e)
        return e

    def sfx(self, t, name, volume=1.0, loop=False, duration=None, hit=None, label=None):
        assert 'pensieve.sfx.flying_' + name in REGISTERED, name
        kw = {'volume': volume}
        if loop:
            kw['loop'] = True
            if duration:
                kw['duration'] = duration
        self.ev(t, 'sfx', SFX + name, **kw)
        if hit is not None:
            self.sfx_hits.append((name, t, hit, label))

    def say(self, t, line, slot_end=None, volume=1.0):
        who, text = LINES[line]
        secs = float(CLIP[line])
        assert 'pensieve.voice.' + line in REGISTERED, line
        self.ev(t, 'voice', VOICE + line, volume=volume)
        self.words.append({'t': t, 'text': text, 'subtitle': True, 'speaker': who,
                           'duration': max(20, int(math.ceil(secs * 20)) + 6)})
        self.lines.append((line, t, secs, slot_end))
        return t + tk(secs)

    def key(self, t, pos, look, fov=70, cut=False, ease=None):
        k = {'t': t, 'pos': r4(pos)}
        if look is not None:
            k['look_at'] = look if isinstance(look, str) else r4(look)
        if abs(fov - 70) > 1e-6:
            k['fov'] = round(fov, 2)
        if ease and ease != 'in_out':
            k['ease'] = ease
        if cut:
            k['cut'] = True
        if self.camera and self.camera[-1]['t'] == t and not cut:
            self.camera[-1] = k
        else:
            assert not self.camera or t >= self.camera[-1]['t'], (self.sid, t, self.camera[-1]['t'])
            self.camera.append(k)

    def shot(self, t0, t1, cam_fn, look_fn, fov_fn, step=4, settle_end=False):
        """A shot from a cut at t0 to t1: keys every `step` ticks from the functions (positions, a look point, the
        fov), a spline run (the first key a cut), so position, look and fov keep a continuous velocity."""
        n = max(1, int(round((t1 - t0) / float(step))))
        for i in range(n + 1):
            t = t0 + (t1 - t0) * i / n
            tt = int(round(t))
            self.key(tt, cam_fn(tt), look_fn(tt), fov_fn(tt), cut=(i == 0),
                     ease=None if i == 0 else ('in_out' if (i == n and settle_end) else 'spline'))

    def actor(self, a):
        self.actors.append(a)
        return a

    def by_id(self, aid):
        return next(a for a in self.actors if a['id'] == aid)

    def scene(self, set_, radius, nxt=None, end_action=None, fog_far=None):
        s = {'title': self.title, 'length': self.L, 'radius': radius}
        if fog_far:
            s['fog_far'] = fog_far
        if nxt:
            s['next'] = nxt
        if end_action:
            s['end_action'] = end_action
        s['set'] = set_
        if self.poses:
            s['poses'] = self.poses
        s['actors'] = self.actors
        s['events'] = sorted(self.events, key=lambda e: e['t'])
        s['words'] = sorted(self.words, key=lambda w: w['t'])
        s['camera'] = self.camera
        return s


# ================================================================ the player model: hands, eyes, the arm solver
def parts_of(scene, a, t):
    return {p[0]: p for p in E.posed_parts(scene, a, t)}


def is_riding(scene, a, t):
    return E.broom_rider_at(scene, a, t) is not None


def hand(scene, a, side, t, bx=None, bz=None, life=None):
    """The hand (arm tip) at tick t from the emitted JSON (posed_parts: rest or crouch, held-item raise, pose,
    look, limbs add/set, the rider frame), with vanilla's bobModelPart sway (bx, bz; the left arm mirrored) and a
    life corner added on a standing actor (a rider's arms are the rider layer: no sway, no life)."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)[side]
    if not is_riding(scene, a, t):
        sign = 1.0 if side == 'right_arm' else -1.0
        bx = E.BOB_MID[0] if bx is None else bx
        bz = E.BOB_MID[1] if bz is None else bz
        rot = [rot[0] + sign * bx, rot[1], rot[2] + sign * bz]
        if life:
            dp, dr = life
            piv = [piv[0] + dp[0], piv[1] + dp[1], piv[2] + dp[2]]
            rot = [rot[0] + dr[0], rot[1] + dr[1], rot[2] + sign * dr[2]]
    return E.arm_tip(root, yaw, s, side, rot, pivot_px=piv)


def hand_corners(scene, a, side, t):
    """The hand at every sway corner (zRot 0 and 0.1 rad, xRot +-0.05; mirrored on the left) and, for a living
    actor whose arm is keyed, every corner of what life adds (life_arm_corners); a rider's one hand."""
    if is_riding(scene, a, t):
        return [hand(scene, a, side, t)]
    lifes = [None]
    if a.get('life', True) and a.get('type') == 'player':
        lifes = E.life_arm_corners()
    out = []
    for bz in E.BOB_Z:
        for bx in E.BOB_X:
            for lf in lifes:
                out.append(hand(scene, a, side, t, bx, bz, lf))
    return out


def solve_arm(scene, a, side, t, target, iters=60):
    """The [dx, dy, dz] degrees to add to an arm's rotation at tick t so its hand (at the sway's middle) lands on
    target (damped least squares over the three angles, small turns preferred); returns (delta, residual)."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)[side]
    rider = is_riding(scene, a, t)
    sign = 1.0 if side == 'right_arm' else -1.0

    def tip(d):
        r = [rot[0] + d[0], rot[1] + d[1], rot[2] + d[2]]
        if not rider:
            r = [r[0] + sign * E.BOB_MID[0], r[1], r[2] + sign * E.BOB_MID[1]]
        return E.arm_tip(root, yaw, s, side, r, pivot_px=piv)
    best = None
    for start in ([0.0, 0.0, 0.0], [-60.0, 0.0, 0.0], [-90.0, 0.0, sign * 20.0], [-40.0, 0.0, sign * 30.0]):
        d = list(start)
        lam = 1e-3
        for _ in range(iters):
            p = tip(d)
            e = sub(target, p)
            err = math.sqrt(sum(c * c for c in e))
            if err < 1e-5:
                break
            J = []
            for j in range(3):
                dd = list(d)
                dd[j] += 0.25
                q = tip(dd)
                J.append([(q[i] - p[i]) / 0.25 for i in range(3)])
            reg = (lam, lam * 6.0, lam)              # a turn about the arm's own axis is the last resort
            A = [[sum(J[r][i] * J[c][i] for i in range(3)) + (reg[r] if r == c else 0.0) for c in range(3)]
                 for r in range(3)]
            g = [sum(J[r][i] * e[i] for i in range(3)) for r in range(3)]
            step = _solve3(A, g)
            n = math.sqrt(sum(c * c for c in step))
            if n > 20.0:
                step = [c * 20.0 / n for c in step]
            nd = [d[i] + step[i] for i in range(3)]
            if math.dist(tip(nd), target) < err:
                d, lam = nd, max(lam * 0.3, 1e-6)
            else:
                lam *= 10.0
        r = math.dist(tip(d), target)
        if best is None or r < best[1] - 1e-6 or (abs(r - best[1]) < 1e-6 and sum(map(abs, d)) < sum(map(abs, best[0]))):
            best = (d, r)
    return best


def _solve3(A, b):
    import copy as _c
    M = [_c.copy(A[i]) + [b[i]] for i in range(3)]
    for c in range(3):
        piv = max(range(c, 3), key=lambda r: abs(M[r][c]))
        M[c], M[piv] = M[piv], M[c]
        if abs(M[c][c]) < 1e-12:
            return [0.0, 0.0, 0.0]
        for r in range(3):
            if r != c:
                f = M[r][c] / M[c][c]
                M[r] = [M[r][k] - f * M[c][k] for k in range(4)]
    return [M[i][3] / M[i][i] for i in range(3)]


def head_eye(scene, a, t, fwd=0.0):
    """The eye from the posed head (posed_parts: the look, the rider frame and lean), fwd blocks out of the face."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)['head']
    return E.eye_of(root, yaw, s, piv, rot, fwd)


def mouth(scene, a, t):
    """The mouth: 2.5 px under the eye on the face."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)['head']
    v = HM._turned(0.0, -1.5, -4.0, *[math.radians(c) for c in rot])
    return E.model_to_world(root, yaw, s, piv[0] + v[0], piv[1] + v[1], piv[2] + v[2])


def broom_point(scene, bid, t, local):
    """A point given in the broom's own frame (x left, y up, z to the tip) in the world at tick t."""
    b = E.broom_pose(scene, E.actor_by_id(scene, bid), t)
    v = E._mat_vec(b['m'], local)
    return [b['pos'][i] + v[i] for i in range(3)]


def broom_under(root, yaw, rider_scale):
    """The broom position (its seat point) that seats a rider of rider_scale whose root is `root`, broom level at
    `yaw` (pitch and roll 0): root - M x seat."""
    seat = E.rider_seat(rider_scale)
    v = E._mat_vec(E.broom_matrix(yaw, 0.0, 0.0), seat)
    return [root[i] - v[i] for i in range(3)]


LIE_Y = -E.BROOM_BOX[BROOM][0][1] + 0.004       # a school broom lying level on the grass: its brush rests on it
GRIP_BELOW = 0.035                              # the handle axis sits this far under the arm tip in a closed fist


def view(sc):
    """The scene so far, as the engine mirrors read it."""
    return {'actors': sc.actors, 'poses': sc.poses, 'length': sc.L, 'camera': sc.camera, 'events': sc.events}


def refresh(a, rig):
    lm = rig.emit()
    if lm:
        a['limbs'] = lm
    else:
        a.pop('limbs', None)


def reach(sc, a, rig, side, t0, t1, target, ease=None):
    """Move an add-mode arm from its held value at t0 to where its hand (at the sway's middle) is on `target` at t1.
    Returns the residual (blocks)."""
    ch = rig.r(side)
    held = list(ch.v())
    ch.to(t0, t1, held)
    refresh(a, rig)
    d, res = solve_arm(view(sc), a, side, t1, target)
    ch.k[-1]['v'] = [round(held[0] + d[0], 2), round(held[1] + d[1], 2), round(held[2] + d[2], 2)]
    if ease:
        ch.k[-1]['ease'] = ease
    refresh(a, rig)
    return math.dist(hand(view(sc), a, side, t1), target)


def park_keys(keys, t, park):
    """Jump an actor out of sight at tick t: a key at t - 0.25 where it is, the parked key at t (a cut: under half a
    tick, the ball speed rule's exemption)."""
    keys.at(t - 0.25, keys.last(), 'linear')
    keys.at(t, park, 'linear')


# ================================================================ the lesson: flying_<h>
import flying_lawn as FL  # noqa: E402
import quidditch_pitch_set as QP  # noqa: E402

LA = FL.ANCHORS
ROWS = LA['broom_line']
LAWN_Y = LA['lawn_y']
STEP_IN = 0.25                   # students stand this much nearer their broom than the anchor (the hand reaches it)
HAND_H = 0.72                    # a first-year's flat hand out over the handle
PARK_LAWN = [-12.0, -36.0, 36.0]  # under the lawn's shell, out of every shot
LAWN_SET = {'templates': [{'id': 'hogwartsmagic:flying_lawn', 'at': list(FL.TEMPLATE_AT)}],
            'schematics': [{'id': 'mini_hogwarts', 'at': list(FL.SCHEMATIC['at']), 'rotation': FL.SCHEMATIC['rotation']}],
            'time_of_day': FL.TIME_OF_DAY}
# (flying_lawn.SCHEMATIC names it 'hogwartsmagic:mini_hogwarts'; the validator knows schematics by bare id)

L1 = 43.0
T1 = {'hooch_in': (0.8, 5.6), 'welcome': 4.6, 'left': 10.0, 'hands': (12.0, 13.4), 'up': 16.4, 'chorus': 21.5,
      'viewer_up': 24.4, 'leap': 25.05, 'contact': 25.25, 'v_mount': 27.4, 'n_mount': 27.9, 'whistle': (27.9, 28.6),
      'n_lift': 29.2, 'whoa': 29.8, 'bolt': 30.2, 'buck': 31.0, 'fall': 33.2, 'land': 34.0, 'hooch_run': (29.6, 35.0),
      'kneel': 35.1, 'nobody': 35.8, 'stand': 38.4, 'leave': 39.6}

# who does what when the class says "Up!"
SUCCESS = {'e1': 22.0, 'e7': 22.3, 'w3': 22.55, 'w6': 22.8}          # brooms up into the hand at these times
MOUNT_AT = {'viewer': T1['v_mount'], 'neville': T1['n_mount'], 'e1': 27.6, 'e7': 28.0, 'w3': 27.7, 'w6': 28.1}
SMACK = 'w5'
ROLLERS = {'e3': 21.9, 'e8': 22.4, 'w4': 22.1}
TWITCHERS = {'e0': 21.7, 'e2': 22.2, 'e5': 21.8, 'e9': 22.6, 'w0': 22.0, 'w2': 21.75, 'malfoy': 22.3, 'w9': 22.5}
NERVOUS = {'e2': (1.0, 9.5), 'w0': (2.2, 10.4), 'e8': (3.0, 8.0), 'neville': (0.5, 11.2), 'w6': (4.0, 10.9)}


def lesson_people(h):
    """The two rows: east (the viewer's house, the viewer 5th from the north), west (the rival house, Neville and
    Malfoy among them). Each entry: id, house, skin, spot, yaw, broom spot, broom yaw."""
    r = rival(h)
    own = [i for i in range(2, 31) if not (h == 'gryffindor' and i == 1) and not (h == 'slytherin' and i == 27)]
    oth = [i for i in range(2, 31) if not (r == 'gryffindor' and i == 1) and not (r == 'slytherin' and i == 27)]
    out = []
    for side, house, pool in (('east', h, own), ('west', r, oth)):
        for i in range(10):
            e = ROWS[side][i]
            aid = ('e' if side == 'east' else 'w') + str(i)
            skin, hs = 'student_%s_%02d' % (house, pool[(i * 3 + 1) % len(pool)]), house
            if aid == 'e4':
                aid, skin = 'viewer', None
            elif aid == 'w1':
                aid, skin, hs = 'neville', 'student_gryffindor_01', 'gryffindor'
            elif aid == 'w8':
                aid, skin, hs = 'malfoy', 'student_slytherin_27', 'slytherin'
            yaw = e['student_yaw']
            spot = ahead(e['student'], yaw, 0.0, side=STEP_IN)
            out.append({'id': aid, 'house': hs, 'skin': skin, 'spot': spot, 'yaw': yaw,
                        'broom': [e['broom'][0], LIE_Y, e['broom'][2]], 'broom_yaw': e['broom_yaw']})
    for p in out:
        assert p['skin'] is None or p['skin'] in SKINS, p['skin']
    return out


def lying_y(yaw, pitch, roll):
    """The seat-point height that rests a school broom's box on the grass (lawn_y) at this orientation."""
    m = E.broom_matrix(yaw, pitch, roll)
    lo, hi = E.BROOM_BOX[BROOM]
    low = min(E._mat_vec(m, [x, y, z])[1] for x in (lo[0], hi[0]) for y in (lo[1], hi[1]) for z in (lo[2], hi[2]))
    return LAWN_Y - low + 0.004


class Broom:
    """A memory broom's path and look (yaw, pitch, roll) under construction, ticks."""

    def __init__(self, sc, bid, pos, yaw, btype=BROOM, scale=1.0, roll_keys=True):
        self.sc, self.id, self.type = sc, bid, btype
        self.path = Keys().at(0, pos)
        self.look = [[0, yaw, 0.0, 0.0 if roll_keys else None, None]]
        self.roll_keys = roll_keys
        self.a = sc.actor({'id': bid, 'type': btype})
        if scale != 1.0:
            self.a['scale'] = scale
        self.sync()

    def yaw(self):
        return self.look[-1][1]

    def turn(self, t, yaw, pitch=0.0, roll=0.0, ease=None):
        """A look key at t (yaw unwrapped to the last)."""
        y = unwrap(yaw, self.look[-1][1])
        k = [t, y, pitch, roll if self.roll_keys else None, ease]
        if abs(self.look[-1][0] - t) < 1e-9:
            self.look[-1] = k
        else:
            assert t > self.look[-1][0], (self.id, t, self.look[-1][0])
            self.look.append(k)
        return self

    def hold_look(self, t):
        l = self.look[-1]
        if t > l[0] + 1e-9:
            self.turn(t, l[1], l[2], l[3] or 0.0)
        return self

    def sync(self):
        self.a['path'] = self.path.k
        self.a['look'] = look_keys([tuple(k) for k in self.look])
        return self.a


def student_actor(sc, p, life=True):
    a = {'id': p['id'], 'type': 'player', 'scale': KID,
         'skin': {'viewer': True} if p['id'] == 'viewer' else {'texture': p['skin']},
         'items': {'chest': robes(p['house'])}}
    if not life:
        a['life'] = False
    return sc.actor(a)


def lesson(h, world):
    sc = Sc('flying_' + h, 'The Flying Lesson', L1, world, h)
    people = lesson_people(h)
    P = {p['id']: p for p in people}
    V = view(sc)
    hooch_spot = LA['hooch_spot']['pos']

    # ---- the brooms (vehicles first: riders name them)
    B = {}
    for p in people:
        B[p['id']] = Broom(sc, 'broom_' + p['id'], p['broom'], p['broom_yaw'])
        sc.lying.append(('broom_' + p['id'], None))

    # ---- the people: path, look, limbs (add), actions
    S, R, LOOK, ACT, CLIPS = {}, {}, {}, {}, {}
    for p in people:
        S[p['id']] = student_actor(sc, p)
        R[p['id']] = Rig()
        LOOK[p['id']] = [(0, p['yaw'], 0.0)]
        ACT[p['id']] = []
        CLIPS[p['id']] = []
        S[p['id']]['path'] = Keys().at(0, p['spot']).k
    hooch = sc.actor({'id': 'hooch', 'type': 'player', 'skin': {'texture': 'witch_05'},
                      'items': {'mainhand': WHISTLE}})
    hrig = Rig()

    def set_look(aid, pts):
        a = S[aid]
        a['look'] = [{'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)} for t, y, pi in pts]

    # while Hooch talks the class turn a little toward her, then back to their brooms, eyes down, for the hands
    for p in people:
        aid = p['id']
        to_h = yaw_to(hooch_spot[0] - p['spot'][0], hooch_spot[2] - p['spot'][2])
        part = unwrap(to_h, p['yaw'])
        rnd = random.Random('look' + aid)
        tilt = p['yaw'] + (part - p['yaw']) * rnd.uniform(0.25, 0.45)
        t_a = tk(T1['welcome'] + rnd.uniform(-0.3, 0.6))
        t_b = tk(T1['hands'][0] - rnd.uniform(0.2, 0.6))
        LOOK[aid] = [(0, p['yaw'], 0.0), (t_a, p['yaw'], 0.0), (t_a + 12, tilt, 4.0), (t_b - 8, tilt, 4.0),
                     (t_b, p['yaw'], 22.0)]
    for aid, (c0, c1) in NERVOUS.items():
        CLIPS[aid] += [{'t': tk(c0), 'name': 'NERVOUS', 'blend': 8}, {'t': tk(c1), 'name': 'IDLE', 'blend': 8}]
    for aid in S:
        if CLIPS[aid]:
            S[aid]['anims'] = CLIPS[aid]
        set_look(aid, LOOK[aid])
    vy = P['viewer']['yaw']
    LOOK['viewer'] = LOOK['viewer'] + [(470, vy, 22.0), (486, vy, 48.0), (505, vy, 46.0), (540, vy, 40.0)]
    set_look('viewer', LOOK['viewer'])
    sc.notes['look_world'] = LOOK

    # ---- right hands out flat over the handles (12.0-13.4, each on its own beat)
    out_t = {}
    for p in people:
        aid = p['id']
        rnd = random.Random('hand' + aid)
        t0 = tk(T1['hands'][0] + rnd.uniform(0.0, 1.0))
        t1 = t0 + 10
        out_t[aid] = (t0, t1)
        tgt = [p['broom'][0], HAND_H, p['broom'][2]]
        res = reach(sc, S[aid], R[aid], 'right_arm', t0, t1, tgt)
        assert res < 0.06, (aid, res)

    # in his own eyes (S8) he looks down and right at the broom by his hand
    R['viewer'].r('head').to(474, 486, [0, 20, 0])
    R['viewer'].r('head').to(548, 562, [0, 0, 0])
    refresh(S['viewer'], R['viewer'])
    lesson_brooms(sc, people, P, B, S, R, ACT, out_t)
    lesson_neville(sc, P, B, S, R, ACT)
    lesson_hooch(sc, hooch, hrig, S)
    lesson_remembrall(sc, S)
    lesson_finish_people(sc, people, P, B, S, R, ACT)
    lesson_camera(sc, P, B, S, hooch)
    sc.ev(0, 'effect', 'eyes_open', at=[0, 0, 0], duration=60)
    sc.ev(0, 'effect', 'sky', at=r3(LA['lawn_centre']), style='day')
    sc.sfx(0, 'lawn_air', 0.55, loop=True)
    scene = sc.scene(LAWN_SET, FL.RADIUS, nxt='flying_%s_chase' % h, fog_far=FL.FOG_FAR)
    return sc, scene


def twitch(b, T, pos, yaw, rnd, big=1.0):
    """A broom that stirs on the grass: two little hops with its nose and roll wobbling, resting again by T + 14."""
    b.path.hold(T)
    b.hold_look(T)
    hop = [(3, 0.07 * big, -4.0, 3.0), (6, 0.0, 2.0, -2.0), (9, 0.04 * big, -2.5, 2.0), (12, 0.0, 1.0, -1.0),
           (14, 0.0, 0.0, 0.0)]
    for dt, up, pi, ro in hop:
        pi *= big * rnd.uniform(0.7, 1.2)
        ro *= big * rnd.uniform(0.7, 1.2)
        if dt == 14:
            pi = ro = 0.0
        y = lying_y(yaw, pi, ro) + up
        b.path.at(T + dt, [pos[0], y, pos[2]], 'spline' if dt < 14 else None)
        b.turn(T + dt, yaw, pi, ro, 'spline' if dt < 14 else None)
    return T + 14


def roll_over(b, T, pos, yaw, sign=1.0, n=26):
    """A broom that rolls once right over about its handle, its brush swinging over the top, back where it lay."""
    b.path.hold(T)
    b.hold_look(T)
    for i in range(1, n + 1):
        f = smooth(i / float(n))
        ro = sign * 360.0 * f
        e = 'spline' if i < n else None
        b.path.at(T + i, [pos[0], lying_y(yaw, 0.0, ro), pos[2]], e)
        b.turn(T + i, yaw, 0.0, ro, e)
    return T + n


def lesson_brooms(sc, people, P, B, S, R, ACT, out_t):
    V = view(sc)
    mounts = sc.notes.setdefault('mounts', {})
    sc.notes['first_up'] = {}
    rest = {}                                         # broom id -> tick it is lying still again (or moves for good)
    for aid, t in sorted(TWITCHERS.items()):
        p = P[aid]
        rnd = random.Random('tw' + aid)
        end = twitch(B[aid], tk(t), p['broom'], p['broom_yaw'], rnd)
        if rnd.random() < 0.5:
            end = twitch(B[aid], end + 4, p['broom'], p['broom_yaw'], rnd, 0.6)
        rest['broom_' + aid] = end
        sc.notes['first_up'][aid] = tk(T1['chorus'])
    for aid, t in sorted(ROLLERS.items()):
        p = P[aid]
        end = roll_over(B[aid], tk(t), p['broom'], p['broom_yaw'], 1.0 if aid != 'e8' else -1.0)
        rest['broom_' + aid] = end
        sc.notes['first_up'][aid] = tk(T1['chorus'])

    # brooms that come up into the hand: the four quick ones at the chorus, then the viewer's, first try
    holds = dict((aid, t) for aid, t in SUCCESS.items())
    holds['viewer'] = T1['contact']
    for aid, t in sorted(holds.items(), key=lambda x: x[1]):
        p, b = P[aid], B[aid]
        tc = tk(t)
        h = hand(V, S[aid], 'right_arm', tc)
        in_hand = [h[0], h[1] - GRIP_BELOW, h[2]]
        lead = 6 if aid != 'viewer' else 9
        b.path.hold(tc - lead)
        b.hold_look(tc - lead)
        if aid == 'viewer':                           # it stirs, then shoots up into his palm
            b.path.at(tc - 6, [p['broom'][0], lying_y(p['broom_yaw'], -3.0, 2.0) + 0.02, p['broom'][2]], 'spline')
            b.turn(tc - 6, p['broom_yaw'], -3.0, 2.0, 'spline')
            b.path.at(tc - 4, [p['broom'][0], lying_y(p['broom_yaw'], 2.0, -1.5), p['broom'][2]], 'spline')
            b.turn(tc - 4, p['broom_yaw'], 2.0, -1.5, 'spline')
        mid = lerp(p['broom'], in_hand, 0.55)
        b.path.at(tc - 2, [mid[0], mid[1] + 0.03, mid[2]], 'spline')
        b.turn(tc - 2, p['broom_yaw'], -2.0, 0.0, 'spline')
        b.path.at(tc, in_hand)
        b.turn(tc, p['broom_yaw'], 0.0, 0.0)
        sc.contacts.append(('%s: the broom into the hand' % aid, aid, 'right_arm', tc,
                            ('broom', b.id, [0.0, GRIP_BELOW, 0.0]), 'broom_up'))
        sc.sfx(tc, 'broom_up', 1.0 if aid == 'viewer' else 0.35, hit=tc, label='broom into %s hand' % aid)
        rest['broom_' + aid] = tc - lead
        sc.notes['first_up'][aid] = tk(T1['chorus']) if aid != 'viewer' else tk(T1['viewer_up'])
        # the mount: the broom swings under him (out of shot) and he rides from MOUNT_AT
        tm = tk(MOUNT_AT[aid])
        under = broom_under(p['spot'], p['broom_yaw'], KID)
        b.path.hold(tm - 10)
        b.hold_look(tm - 10)
        b.path.at(tm, under)
        b.turn(tm, p['broom_yaw'], 0.0, 0.0)
        b.path.at(tm + 12, [under[0], under[1] + 0.05, under[2]])
        b.turn(tm + 12, p['broom_yaw'], 0.0, 0.0)
        R[aid].r('right_arm').to(tm - 10, tm, [0, 0, 0])
        refresh(S[aid], R[aid])
        mounts[aid] = tm
        sc.flier_ok.setdefault(b.id, []).append((tm - 10, tm + 14))

    # the smack: w5's broom leaps, spins round and cracks him across the face, then drops back to the grass
    p, b = P[SMACK], B[SMACK]
    tc = tk(22.15)
    F = head_eye(V, S[SMACK], tc, fwd=0.03)
    y1 = unwrap(p['broom_yaw'] + 180.0, p['broom_yaw'])
    d = E._mat_vec(E.broom_matrix(y1, -10.0, 0.0), [0.0, 0.0, 1.0])
    at_face = [F[i] - 1.6 * d[i] for i in range(3)]
    b.path.hold(tc - 6)
    b.hold_look(tc - 6)
    b.path.at(tc - 3, lerp(p['broom'], at_face, 0.45), 'spline')
    b.turn(tc - 3, p['broom_yaw'] + (y1 - p['broom_yaw']) * 0.5, -25.0, 30.0, 'spline')
    b.path.at(tc, at_face, 'spline')
    b.turn(tc, y1, -10.0, 0.0, 'spline')
    away = add(at_face, [1.0, 0.3, 0.25])
    b.path.at(tc + 6, away, 'spline')
    b.turn(tc + 6, y1 + 25.0, 18.0, 35.0, 'spline')
    land = [p['broom'][0] + 1.4, 0.0, p['broom'][2] + 0.2]
    b.path.at(tc + 14, [land[0], lying_y(y1 + 10.0, 0.0, 0.0), land[2]])
    b.turn(tc + 14, y1 + 10.0, 0.0, 0.0)
    rest[b.id] = tc - 6
    sc.speed_ok[b.id] = [(tc - 6, tc + 14, 'the broom that leaps up and smacks a boy')]
    sc.notes['smack'] = (tc, SMACK)
    sc.notes['first_up'][SMACK] = tk(T1['chorus'])
    sc.contacts.append(('%s: the broom tip into his face' % SMACK, SMACK, 'face', tc,
                        ('broom', b.id, [0.0, 0.0, 1.6]), 'smack'))
    sc.sfx(tc, 'broom_up', 0.7, hit=tc, label='the smack')
    a, rg = S[SMACK], R[SMACK]
    ACT[SMACK].append({'t': tc, 'do': 'hurt'})
    rg.r('head').to(tc, tc + 3, [-30, 8, 0])
    rg.r('head').to(tc + 14, tc + 30, [-6, 0, 0])
    rg.r('right_arm').to(tc, tc + 6, [-20, 0, 25])
    rg.r('left_arm').to(tc + 1, tc + 7, [-125, -15, -8])
    rg.r('left_arm').to(tc + 34, tc + 46, [0, 0, 0])
    rg.r('right_arm').to(tc + 30, tc + 40, [0, 0, 0])
    refresh(a, rg)
    back = ahead(p['spot'], p['yaw'], -0.38)
    a['path'] = Keys().at(0, p['spot']).hold(tc).at(tc + 6, back).k
    sc.notes['rest'] = rest

    # the rest of the class give up: hands down once the chorus has failed them
    for p_ in people:
        aid = p_['id']
        if aid in holds or aid in ('neville', SMACK):
            continue
        rnd = random.Random('down' + aid)
        t0 = tk(25.4 + rnd.uniform(0.0, 1.6))
        R[aid].r('right_arm').to(t0, t0 + 12, [0, 0, 0])
        refresh(S[aid], R[aid])


NEVILLE_FLIGHT = [   # (tick, broom seat point): the lift, the bolt, the bucking loop, the throw (664) and away
    (584, None), (590, [-3.92, 0.62, 22.6]), (596, [-4.05, 0.95, 22.5]), (602, [-3.9, 1.35, 22.75]),
    (608, [-4.3, 2.1, 23.3]), (614, [-5.2, 3.3, 24.4]), (620, [-6.5, 4.6, 26.0]), (630, [-8.9, 6.0, 29.0]),
    (640, [-12.3, 6.2, 30.9]), (650, [-14.9, 6.4, 33.8]), (658, [-14.6, 5.7, 36.0]), (664, [-13.4, 5.0, 36.9]),
    (670, [-11.9, 6.6, 37.6]), (678, [-14.0, 10.5, 39.6]), (688, [-19.5, 17.5, 42.0]), (698, [-26.5, 27.0, 44.5]),
    (710, [-38.0, 42.0, 48.0]), (724, [-55.0, 58.0, 53.0]), (742, [-80.0, 70.0, 60.0])]


def lesson_neville(sc, P, B, S, R, ACT):
    V = view(sc)
    p, b, a, rg = P['neville'], B['neville'], S['neville'], R['neville']
    tm = tk(T1['n_mount'])
    T_FALL, T_LAND = tk(T1['fall']), tk(T1['land'])
    sc.notes['neville'] = {'mount': tm, 'fall': T_FALL, 'land': T_LAND, 'lift': tk(T1['n_lift'])}
    under = broom_under(p['spot'], p['broom_yaw'], KID)
    # offstage (Hooch's close shot) his broom finally stirs and he gets on it
    b.path.hold(tm - 10)
    b.hold_look(tm - 10)
    b.path.at(tm, under)
    b.turn(tm, p['broom_yaw'], 0.0, 0.0)
    rest = [under[0], under[1] + 0.05, under[2]]
    b.path.at(tm + 12, rest)
    b.turn(tm + 12, p['broom_yaw'], 0.0, 0.0)
    sc.notes['rest']['broom_neville'] = tm - 10
    sc.flier_ok.setdefault(b.id, []).append((tm - 10, tm + 14))
    rg.r('right_arm').to(tm - 10, tm, [0, 0, 0])
    sc.notes['mounts']['neville'] = tm
    sc.notes['first_up']['neville'] = tk(T1['chorus'])
    # the flight: spline keys through NEVILLE_FLIGHT, resting at the end
    b.path.hold(NEVILLE_FLIGHT[0][0])
    for i, (t, q) in enumerate(NEVILLE_FLIGHT[1:]):
        b.path.at(t, q, 'in_out' if i == len(NEVILLE_FLIGHT) - 2 else 'spline')
    # its look: the heading and climb of its own path, with the bucking on top (pitch and roll kicks)
    b.hold_look(NEVILLE_FLIGHT[0][0])
    prev = p['broom_yaw']
    rnd = random.Random('buck')
    last = NEVILLE_FLIGHT[-1][0]
    for t in range(NEVILLE_FLIGHT[0][0] + 3, last + 1, 3):
        hd = unwrap(E.broom_heading(b.path.k, t), prev)
        prev = hd
        cl = E.broom_climb(b.path.k, t)
        amp = (0.25 if t < 604 else 1.0 if t < 664 else max(0.0, 1.0 - (t - 664) / 70.0))
        ph = (t - 584) / 20.0
        pitch = cl + amp * (18.0 * math.sin(2 * math.pi * 2.6 * ph) + rnd.uniform(-4, 4))
        roll = amp * (32.0 * math.sin(2 * math.pi * 1.9 * ph + 0.7) + rnd.uniform(-6, 6))
        yj = amp * 14.0 * math.sin(2 * math.pi * 1.3 * ph + 1.9)
        e = 'spline' if t + 3 <= last else None
        b.turn(t, hd + yj, max(-70.0, min(70.0, pitch)), roll, e)
    b.turn(last + 6, prev, 0.0, 0.0)
    sc.speed_ok[b.id] = [(NEVILLE_FLIGHT[0][0], last + 6, "Neville's broom bolts and bucks")]
    b.sync()

    # the rider: on from tm until he is thrown at T_FALL; flailing from the bolt, still by 660; arms up as he falls
    a['ride'] = {'on': b.id, 'from': tm, 'until': T_FALL}
    seat = E.rider_seat(KID)
    path = Keys().at(0, p['spot']).hold(tm - 0.001)
    path.at(tm, seat).at(T_FALL - 0.001, seat)
    a['path'] = path.k
    a['look'] = [{'t': 0, 'yaw': p['yaw'], 'pitch': 0.0}]      # placeholder: the real look is set below
    off = E.rider_root(V, a, T_FALL - 0.001)[0]
    # the fall: a ballistic drop onto the grass at neville_fall, landing on T_LAND
    land = LA['neville_fall']['pos']
    n = T_LAND - T_FALL
    vy = 0.04
    g = 2.0 * (off[1] + vy * n - land[1]) / (n * n)
    path.at(T_FALL, off, 'linear')
    for k in range(2, n, 2):
        f = k / float(n)
        path.at(T_FALL + k, [off[0] + (land[0] - off[0]) * f, off[1] + vy * k - 0.5 * g * k * k,
                             off[2] + (land[2] - off[2]) * f], 'spline')
    path.at(T_LAND, land, 'linear')
    sc.notes['neville']['g'] = g
    # flailing (add mode on top of the grip), decaying to nothing by 660
    for part, amp, base in (('right_arm', 38, [-35, 0, 12]), ('left_arm', 38, [-35, 0, -12]),
                            ('right_leg', 24, [-10, 0, 6]), ('left_leg', 24, [-10, 0, -6])):
        ch = rg.r(part)
        ch.to(604, 610, [base[0] * 0.5, base[1], base[2]])
        sgn = 1 if part in ('right_arm', 'left_leg') else -1
        t = 614
        while t <= 650:
            fall = max(0.0, 1.0 - max(0, t - 636) / 16.0)
            ch.set(t, [base[0] + sgn * amp * fall, base[1], base[2] + sgn * 8 * fall], None)
            sgn = -sgn
            t += 4
        ch.to(652, 660, [0, 0, 0])
    sc.notes['neville']['flail_end'] = 660
    rg.r('right_arm').to(T_FALL, T_FALL + 8, [-150, 0, 25])
    rg.r('left_arm').to(T_FALL, T_FALL + 8, [-150, 0, -25])
    rg.r('right_arm').to(T_LAND, T_LAND + 10, [-38, 0, 14])
    rg.r('left_arm').to(T_LAND, T_LAND + 10, [-52, -18, -24])
    rg.r('head').to(T_LAND, T_LAND + 8, [28, 0, 0])
    rg.r('head').to(tk(T1['stand']), tk(T1['stand']) + 10, [10, 0, 0])
    refresh(a, rg)
    ACT['neville'] += [{'t': T_LAND, 'do': 'sneak'}, {'t': T_LAND, 'do': 'hurt'}, {'t': tk(T1['stand']), 'do': 'stand'}]
    # his look: world, then broom-relative (eyes down, darting), world again from the throw
    look = [{'t': 0, 'yaw': p['yaw'], 'pitch': 0.0}]
    for t, y, pi in sc.notes['look_world']['neville']:
        if 0 < t < tm - 1:
            look.append({'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)})
    look.append({'t': tm - 0.001, 'yaw': look[-1]['yaw'], 'pitch': look[-1]['pitch']})
    look.append({'t': tm, 'yaw': 0.0, 'pitch': 12.0})
    for t, y, pi in ((584, 0.0, 12.0), (592, -10.0, 30.0), (604, 15.0, 35.0), (618, -25.0, 10.0), (630, 20.0, 25.0),
                     (642, -30.0, 5.0), (654, 10.0, 30.0)):
        look.append({'t': t, 'yaw': y, 'pitch': pi})
    look.append({'t': T_FALL - 0.001, 'yaw': 10.0, 'pitch': 30.0})
    by = E.broom_pose(V, b.a, T_FALL - 0.001)['yaw'] + 10.0
    look.append({'t': T_FALL, 'yaw': round(by, 2), 'pitch': 30.0})
    ly = unwrap(LA['neville_fall']['yaw'], by)
    look.append({'t': T_LAND, 'yaw': round(ly, 2), 'pitch': 20.0})
    to_h = unwrap(yaw_to(-12.3 - land[0], 35.2 - land[2]), ly)
    look.append({'t': 704, 'yaw': round(ly, 2), 'pitch': 20.0})
    look.append({'t': 716, 'yaw': round(to_h, 2), 'pitch': -10.0})
    a['look'] = look
    sc.notes['neville_look_done'] = True


def path_yaws(keys, t0, t1, step, start_yaw):
    """Look keys facing along a walker's path (its heading through the mirror), unwrapped."""
    out, prev = [], start_yaw
    for t in range(t0, t1 + 1, step):
        y = unwrap(E.broom_heading(keys, t), prev)
        prev = y
        out.append((t, y))
    return out


def lesson_hooch(sc, hooch, hrig, S):
    V = view(sc)
    spot = LA['hooch_spot']['pos']
    a, rg = hooch, hrig
    path = Keys().at(0, [-1.5, 0.0, 6.0]).hold(16)
    path.through([(64, [-0.6, 0.0, 11.4]), (112, spot)])
    T_RUN0, T_RUN1 = tk(T1['hooch_run'][0]), tk(T1['hooch_run'][1])
    path.hold(T_RUN0)
    run = [(608, [-4.2, 0.0, 17.4]), (628, [-8.5, 0.0, 19.8]), (650, [-10.8, 0.0, 26.0]), (672, [-11.6, 0.0, 31.0]),
           (T_RUN1, [-12.3, 0.0, 35.2])]
    path.through(run)
    T_STAND, T_LEAVE = tk(T1['stand']), tk(T1['leave'])
    path.hold(T_LEAVE)
    path.through([(826, [-13.3, 0.0, 38.4]), (sc.L, [-14.2, 0.0, 41.3])], settle=False)
    a['path'] = path.k
    look = [(0, 10.0, 0.0), (16, 10.0, 0.0)]
    look += [(t, y, 0.0) for t, y in path_yaws(path.k, 22, 104, 6, 10.0)]
    look += [(118, 0.0, 0.0), (150, -16.0, 3.0), (196, 14.0, 3.0), (236, 0.0, 6.0), (276, -12.0, 6.0),
             (330, 8.0, 2.0), (380, -6.0, 2.0), (420, 0.0, 4.0), (470, 18.0, 6.0), (520, -10.0, 4.0), (556, 0.0, 0.0),
             (580, 0.0, 0.0), (590, 33.0, -18.0)]
    for t, y in path_yaws(path.k, T_RUN0 + 6, T_RUN1 - 8, 6, 33.0):
        look.append((t, y, -12.0 if t < 664 else 6.0))
    nf = LA['neville_fall']['pos']
    face_n = unwrap(yaw_to(nf[0] - run[-1][1][0], nf[2] - run[-1][1][2]), look[-1][1])
    look += [(T_RUN1, face_n, 30.0), (T_STAND + 4, face_n, 10.0), (T_STAND + 14, unwrap(-80.0, face_n), 0.0),
             (T_LEAVE - 6, unwrap(-80.0, face_n), 0.0)]
    for t, y in path_yaws(path.k, T_LEAVE + 4, sc.L, 8, look[-1][1]):
        look.append((t, y, 4.0))
    a['look'] = [{'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)} for t, y, pi in look]
    a['anims'] = [{'t': tk(T1['welcome']), 'name': 'TALK', 'blend': 6},
                  {'t': tk(T1['welcome'] + 4.6), 'name': 'IDLE', 'blend': 8},
                  {'t': tk(T1['left']), 'name': 'TALK', 'blend': 6},
                  {'t': tk(T1['left'] + 5.6), 'name': 'IDLE', 'blend': 8},
                  {'t': tk(T1['up']), 'name': 'TALK', 'blend': 6},
                  {'t': tk(T1['up'] + 4.4), 'name': 'IDLE', 'blend': 8}]
    a['actions'] = [{'t': tk(T1['kneel']), 'do': 'sneak'}, {'t': T_STAND, 'do': 'stand'}]
    # the whistle to her lips as the class waits mounted; down again when Neville's broom moves
    w0, w1 = tk(T1['whistle'][0]), tk(T1['whistle'][1])
    # a rigid arm's hand lies 10 px from the shoulder: the head turns a little from the hand and the shoulder
    # drops a pixel, so the lips come out to where the hand can reach them
    rg.r('head').to(w0, w1, [0, -25, 0])
    rg.o('right_arm').to(w0, w1, [0, 1.0, 1.5])
    refresh(a, rg)
    res = reach(sc, a, rg, 'right_arm', w0, w1, mouth(V, a, w1))
    assert res < 0.03, res
    rg.r('right_arm').to(586, 596, [0, 0, 0])
    rg.r('head').to(586, 596, [0, 0, 0])
    rg.o('right_arm').to(586, 596, [0, 0, 0])
    rg.r('left_arm').to(T_STAND + 16, T_STAND + 26, [-48, 0, -10])          # pointing the class to stay put
    rg.r('left_arm').to(T_LEAVE - 8, T_LEAVE + 4, [0, 0, 0])
    refresh(a, rg)
    sc.contacts.append(('hooch: the whistle to her lips', 'hooch', 'right_arm', w1, ('mouth', 'hooch'), 'whistle'))
    # the lines
    sc.say(tk(T1['welcome']), 'flying_hooch_welcome')
    sc.say(tk(T1['left']), 'flying_hooch_left')
    sc.say(tk(T1['up']), 'flying_hooch_up')
    sc.say(tk(T1['chorus']), 'flying_class_up')
    sc.say(tk(T1['viewer_up']), 'flying_viewer_up')
    sc.say(tk(T1['whoa']), 'flying_neville_whoa')
    sc.say(tk(T1['nobody']), 'flying_hooch_nobody')
    # Neville goes with her: up, then off toward the hospital wing
    nev = S['neville']
    np_ = Keys()
    np_.k = nev['path']
    np_.hold(T_LEAVE)
    np_.through([(826, [-14.6, 0.0, 39.2]), (sc.L, [-15.6, 0.0, 42.0])], settle=False)
    lk = nev['look']
    for t, y in path_yaws(np_.k, T_LEAVE + 4, sc.L, 8, lk[-1]['yaw']):
        lk.append({'t': t, 'yaw': round(y, 2), 'pitch': 12.0})


def lesson_remembrall(sc, S):
    nev = S['neville']
    T_LAND = tk(T1['land'])
    r = E.ball_radius(REMEMBRALL, KID) + 0.002
    land = LA['neville_fall']['pos']
    ly = E._yaw_pitch(nev, T_LAND)[0]
    pocket = ahead(land, ly, 0.05, side=0.2, y=0.33)
    drop = [LA['remembrall_drop'][0], r, LA['remembrall_drop'][2]]
    rest = [-6.4, r, 37.3]
    k = Keys().at(0, PARK_LAWN).hold(T_LAND - 0.25)
    k.at(T_LAND, pocket, 'linear')
    for dt in (2, 4, 6, 8):
        f = dt / 10.0
        q = lerp(pocket, drop, f)
        k.at(T_LAND + dt, [q[0], q[1] + 0.3 * 4 * f * (1 - f), q[2]], 'spline')
    k.at(T_LAND + 10, drop, 'linear')
    hop = lerp(drop, rest, 0.06)
    k.at(T_LAND + 14, [hop[0], r + 0.06, hop[2]], 'spline')
    start = lerp(drop, rest, 0.12)
    k.at(T_LAND + 18, start, 'spline')
    t0, t1 = T_LAND + 18, T_LAND + 66
    for t in range(t0 + 4, t1, 4):
        u = (t - t0) / float(t1 - t0)
        k.at(t, lerp(start, rest, 1 - (1 - u) ** 2), 'spline')
    k.at(t1, rest)
    sc.actor({'id': 'remembrall', 'type': REMEMBRALL, 'scale': KID, 'path': k.k})
    sc.notes['remembrall'] = {'pocket': (T_LAND, pocket), 'rest': (t1, rest), 'drop': drop}


def lesson_finish_people(sc, people, P, B, S, R, ACT):
    """Paths, looks and ride windows for the class once every broom is keyed."""
    V = view(sc)
    mounts = sc.notes['mounts']
    for p in people:
        aid = p['id']
        a = S[aid]
        if ACT[aid]:
            a['actions'] = sorted(ACT[aid], key=lambda x: x['t'])
        refresh(a, R[aid])
        if aid == 'neville':
            continue
        world = [(t, y, pi) for t, y, pi in sc.notes['look_world'][aid]]
        if aid in mounts:
            tm = mounts[aid]
            a['ride'] = {'on': 'broom_' + aid, 'from': tm}
            path = Keys().at(0, p['spot'])
            path.hold(tm - 0.001).at(tm, E.rider_seat(KID))
            a['path'] = path.k
            look = [{'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)} for t, y, pi in world if t < tm - 1]
            look.append({'t': tm - 0.001, 'yaw': look[-1]['yaw'], 'pitch': look[-1]['pitch']})
            look.append({'t': tm, 'yaw': 0.0, 'pitch': look[-1]['pitch']})
            # eyes on Neville from the lift to the fall, then on Hooch; the head only (the body faces the broom)
            look.append({'t': 580, 'yaw': 0.0, 'pitch': look[-1]['pitch']})
            for t in range(590, 781, 10):
                tgt = E.rider_root(V, S['neville'], t)[0] if t < 680 else E.sample_path(S['neville']['path'], t)
                d = sub(tgt, p['spot'])
                yw = unwrap(yaw_to(d[0], d[2]) - p['broom_yaw'], 0.0)
                yw = max(-70.0, min(70.0, yw)) * (0.9 if aid == 'viewer' else 0.75)
                pi = -math.degrees(math.atan2(d[1] + 0.6 - 1.2, math.hypot(d[0], d[2])))
                look.append({'t': t, 'yaw': round(yw, 2), 'pitch': round(max(-40.0, min(30.0, pi)), 2),
                             'ease': 'spline'})
            look[-1].pop('ease')
            a['look'] = look
        else:
            look = [{'t': t, 'yaw': round(y, 2), 'pitch': round(pi, 2)} for t, y, pi in world]
            body = p['yaw']
            rnd = random.Random('watch' + aid)
            t_w = 600 + rnd.randint(0, 8)
            if look[-1]['t'] < t_w - 12:
                look.append({'t': t_w - 12, 'yaw': look[-1]['yaw'], 'pitch': look[-1]['pitch']})
            for t in range(t_w, 781, 12):
                tgt = E.rider_root(V, S['neville'], t)[0] if t < 680 else E.sample_path(S['neville']['path'], t)
                d = sub(tgt, p['spot'])
                yw = unwrap(yaw_to(d[0], d[2]), body)
                yw = body + max(-60.0, min(60.0, yw - body)) * 0.55
                pi = -math.degrees(math.atan2(d[1] + 0.4 - 1.2, math.hypot(d[0], d[2])))
                if t > look[-1]['t']:
                    look.append({'t': t, 'yaw': round(yw, 2), 'pitch': round(max(-40.0, min(30.0, pi)), 2),
                                 'ease': 'spline'})
            look[-1].pop('ease', None)
            a['look'] = look
    for b in B.values():
        b.sync()


# ================================================================ checks (the Global Constraints' asserts and the Flying ones)
PLANTS = {'minecraft:dandelion', 'minecraft:oxeye_daisy', 'minecraft:allium', 'minecraft:short_grass',
          'minecraft:tall_grass', 'minecraft:fern', 'minecraft:poppy', 'minecraft:cornflower', 'minecraft:azure_bluet'}


def cam_world(world):
    """The set as the camera's air check reads it: see-through plants (a flower, a tuft) are no wall."""
    return {c: s for c, s in world.items() if E.bid_of(s) not in PLANTS}


def in_windows(t, wins):
    return any(a <= t <= b for a, b in wins)


def broom_box_distance(scene, broom, t, p):
    """Blocks from point p to a broom's model box at tick t (0 inside), and the box's world corners."""
    b = E.broom_pose(scene, broom, t)
    lo, hi = E.broom_box(broom['type'], broom.get('scale', 1.0))
    loc = E._mat_vec(E._transpose(b['m']), sub(p, b['pos']))
    q = [E.clamp(loc[i], lo[i], hi[i]) for i in range(3)]
    corners = []
    for x in (lo[0], hi[0]):
        for y in (lo[1], hi[1]):
            for z in (lo[2], hi[2]):
                corners.append(add(b['pos'], E._mat_vec(b['m'], [x, y, z])))
    return math.dist(loc, q), corners


def check_clearance(sc, scene, problems):
    """Assert 4: the camera, four times a tick, at least CAM_CLEAR from every player part box (posed from the JSON:
    crouch, item raise, pose, look, limbs, the rider frame) and every broom and ball box, unless no point of it is
    in the frustum (aspect 2.0); the first-person head is exempt. Returns the worst (d, t, what)."""
    cam = scene['camera']
    best = [9.0, None, None]
    players = [a for a in scene['actors'] if a.get('type') == 'player']
    brooms = [a for a in scene['actors'] if E.is_broom(a)]
    balls = [a for a in scene['actors'] if a['type'] in E.FAST_MOVERS]
    fp = sc.first_person
    for t, c, shot in E.camera_samples(cam, scene['length']):
        view_ = None
        for a in players:
            root = E.rider_root(scene, a, t)[0]
            if math.dist(root, c) > 3.6 * a.get('scale', 1.0):
                continue
            for part, rt, yaw, s, piv, rot in E.posed_parts(scene, a, t):
                if part == 'head' and any(t0 <= t <= t1 and aid == a['id'] for t0, t1, aid in fp):
                    continue
                d = E.box_distance(rt, yaw, s, piv, rot, E.PART_BOX[part], c)
                if d >= best[0] and d >= E.CAM_CLEAR:
                    continue
                view_ = view_ or E.engine_look(scene, cam, t)
                if not any(E.in_view(c, view_[0], view_[1], q)
                           for q in E.box_surface(rt, yaw, s, piv, rot, E.PART_BOX[part])):
                    continue
                if d < E.CAM_CLEAR:
                    problems.append('%s: the camera at t %.2f is %.3f from %s %s' % (sc.sid, t, d, a['id'], part))
                    return best
                best = [d, t, a['id'] + ' ' + part]
        for b in brooms:
            bp = E.track_sample(b['path'], t)
            if math.dist(bp, c) > 2.6 * b.get('scale', 1.0):
                continue
            d, corners = broom_box_distance(scene, b, t, c)
            if d >= best[0] and d >= E.CAM_CLEAR:
                continue
            view_ = view_ or E.engine_look(scene, cam, t)
            if not any(E.in_view(c, view_[0], view_[1], q) for q in corners):
                continue
            if d < E.CAM_CLEAR:
                problems.append('%s: the camera at t %.2f is %.3f from broom %s' % (sc.sid, t, d, b['id']))
                return best
            best = [d, t, b['id']]
        for b in balls:
            bp = E.track_sample(b['path'], t)
            rad = E.ball_radius(b['type'], b.get('scale', 1.0)) if E.is_ball(b['type']) else 0.15 * b.get('scale', 1.0)
            d = math.dist(bp, c) - rad
            if d >= best[0] and d >= E.CAM_CLEAR:
                continue
            view_ = view_ or E.engine_look(scene, cam, t)
            if not E.in_view(c, view_[0], view_[1], bp):
                continue
            if d < E.CAM_CLEAR:
                problems.append('%s: the camera at t %.2f is %.3f from ball %s' % (sc.sid, t, d, b['id']))
                return best
            best = [d, t, b['id']]
    return best


def check_camera_air(sc, scene, problems, margin=0.06):
    """Assert 4, blocks: every camera key, and the real path between keys in steps of 0.08 (the spline's own path),
    clear of block shapes by `margin`. Returns the number of points tested."""
    w = sc.cam_world
    cam = scene['camera']
    n = 0
    for i, k in enumerate(cam):
        n += 1
        if not E.in_air(w, k['pos'], margin):
            problems.append('%s camera key %d t %s pos %s is in %s' % (sc.sid, i, k['t'], k['pos'],
                                                                      w.get(E.cell(k['pos']))))
        if i and not k.get('cut') and k['t'] > cam[i - 1]['t']:
            a0, a1 = cam[i - 1]['pos'], k['pos']
            steps = int(max(abs(a1[j] - a0[j]) for j in range(3)) / 0.08) + 1
            spl = E._cam_spline(cam, i)
            if spl:
                steps *= 2
            for s_ in range(1, steps):
                tt = cam[i - 1]['t'] + (k['t'] - cam[i - 1]['t']) * s_ / steps
                p = E.engine_cam(cam, tt)
                n += 1
                if not E.in_air(w, p, margin):
                    problems.append('%s camera move into key %d (t %s) passes %s (%s)' % (
                        sc.sid, i, k['t'], r3(p), w.get(E.cell(p))))
                    break
    return n


def contact_target(scene, spec, t):
    kind = spec[0]
    if kind == 'broom':
        return broom_point(scene, spec[1], t, spec[2])
    if kind == 'ball':
        return E.track_sample(E.actor_by_id(scene, spec[1])['path'], t)[:3]
    if kind == 'mouth':
        return mouth(scene, E.actor_by_id(scene, spec[1]), t)
    if kind == 'point':
        return spec[1]
    raise ValueError(kind)


CONTACT_GAP = 0.15


def check_contacts(sc, scene, problems):
    """Assert 5: every hand contact recomputed from the emitted JSON (posed_parts: add/set, the item raise, the
    crouch, the rider frame; the sway corners and life's corners on a standing actor): the gap at most 0.15 at every
    corner. A face contact (the smack) is the broom tip against the face point. Returns [(label, worst gap)]."""
    out = []
    for label, aid, side, t, spec, kind in sc.contacts:
        a = E.actor_by_id(scene, aid)
        tgt = contact_target(scene, spec, t)
        if side == 'face':
            pts = [head_eye(scene, a, t, fwd=0.03)]
        elif side == 'bat':
            pts = [bat_point(scene, a, t, h) for h in hand_corners(scene, a, 'right_arm', t)]
        else:
            pts = hand_corners(scene, a, side, t)
        gap = max(math.dist(p, tgt) for p in pts)
        out.append((label, gap))
        if gap > CONTACT_GAP:
            problems.append('%s: %s at t %s: the hand is %.3f from it (limit %.2f)' % (sc.sid, label, t, gap,
                                                                                     CONTACT_GAP))
    return out


def bat_point(scene, a, t, tip):
    """The Beater's bat: its striking face 0.5 x scale out along the forearm from the fist."""
    _, root, yaw, s, piv, rot = parts_of(scene, a, t)['right_arm']
    sh = E.model_to_world(root, yaw, s, *piv)
    d = norm(sub(tip, sh))
    return add(tip, d, 0.5 * s)


def check_items(sc, scene, problems):
    """Assert 6: every main-hand change between empty and held on a standing actor's add-mode arm is balanced by
    item_raise_steps (the hand jumps under 0.01 across the item tick, at the sway's middle); a rider's arms are the
    rider layer (no raise), checked the same way. Hand motion in a first-person close-up at most 0.03 a tick.
    Returns (worst jump, worst first-person hand step)."""
    worst = [0.0, 0.0]
    for a in scene['actors']:
        if a.get('type') != 'player':
            continue
        ticks = sorted({x['t'] for x in a.get('actions', []) if x['do'] in ('hold', 'equip', 'use_item')
                        and (x['do'] != 'equip' or x.get('slot') == 'mainhand')})
        for T in ticks:
            before = E.EquipTimeline_item(a, 'mainhand', T - 1)
            after = E.EquipTimeline_item(a, 'mainhand', T)
            if (before is None) == (after is None):
                continue
            j = math.dist(hand(scene, a, 'right_arm', T), hand(scene, a, 'right_arm', T - 2 * E.ITEM_STEP_EPS))
            worst[0] = max(worst[0], j)
            if j > 0.01:
                problems.append('%s: %s hand jumps %.3f at the item change at t %s' % (sc.sid, a['id'], j, T))
    for t0, t1, aid in sc.first_person:
        a = E.actor_by_id(scene, aid)
        for side in ('right_arm', 'left_arm'):
            prev = None
            for t in range(t0, t1 + 1):
                h = hand(scene, a, side, t)
                if prev is not None:
                    st = math.dist(h, prev)
                    cam = E.engine_cam(scene['camera'], t)
                    lk = E.engine_look(scene, scene['camera'], t)
                    if E.in_view(cam, lk[0], lk[1], h) and math.dist(cam, h) < 1.5:
                        worst[1] = max(worst[1], st)
                        if st > sc.notes.get('fp_hand_limit', {}).get((aid, side, t), 0.03):
                            problems.append('%s: %s %s hand moves %.3f in one tick at t %d in a first-person close-up' % (
                                sc.sid, aid, side, st, t))
                            break
                prev = h
    return worst


def check_rides(sc, scene, problems):
    """Assert 9: at every mount and dismount the rider's root is continuous within 0.02, and the path is bracketed
    (a key at T - 0.001 and one at T). Returns the worst jump."""
    worst = 0.0
    for a in scene['actors']:
        r = E.ride_of(a)
        if r is None or isinstance(a.get('ride'), str):
            continue
        for T in (r[1], r[2]):
            if T in (E.INF, -E.INF):
                continue
            j = math.dist(E.rider_root(scene, a, T - 0.0005)[0], E.rider_root(scene, a, T)[0])
            worst = max(worst, j)
            if j > 0.02:
                problems.append('%s: %s root jumps %.3f at the ride edge t %s' % (sc.sid, a['id'], j, T))
            ts = [k['t'] for k in a.get('path', [])]
            if not (any(abs(x - (T - 0.001)) < 1e-6 for x in ts) and any(abs(x - T) < 1e-6 for x in ts)):
                problems.append('%s: %s path is not bracketed at the ride edge t %s' % (sc.sid, a['id'], T))
    return worst


def check_fliers(sc, scene, problems):
    """Assert 8: every broom path at least 0.5 from every block, except where it rests by the ground (lying on the
    lawn, standing under a mounted rider) and its mount and landing windows; at least 1.2 from every other broom
    while either flies; speed changes at most 0.12 blocks/tick^2 (the scripted exceptions by name). Returns (least
    flier gap, largest speed change)."""
    w = sc.world
    brooms = [a for a in scene['actors'] if E.is_broom(a)]
    least_pair, worst_acc = 9.0, 0.0
    flying = {}
    n = scene['length']
    for b in brooms:
        ok = sc.flier_ok.get(b['id'], [])
        pos = [E.track_sample(b['path'], t) for t in range(n + 1)]
        fl = []
        for t, p in enumerate(pos):
            spd = math.dist(pos[min(n, t + 1)], pos[max(0, t - 1)]) / 2.0
            ridden = E.broom_ridden(scene, b['id'], t) > 0
            grounded = p[1] < 1.0 and (spd < 0.06 or not ridden)     # lying, stirring, in a hand, stood astride
            if grounded or in_windows(t, ok):
                continue
            if not E.in_air(w, p, 0.5) or not E.in_air(w, p, 0.0):
                problems.append('%s: broom %s at t %d (%s) is within 0.5 of a block' % (sc.sid, b['id'], t, r3(p)))
                break
        fl = []
        for t, p in enumerate(pos):
            spd = math.dist(pos[min(n, t + 1)], pos[max(0, t - 1)]) / 2.0
            ridden = E.broom_ridden(scene, b['id'], t) > 0
            fl.append(not (p[1] < 1.0 and (spd < 0.06 or not ridden)))
        exc = sc.speed_ok.get(b['id'], [])
        for t in range(2, n + 1):
            if any(t0 <= t <= t1 for t0, t1, _ in exc):
                continue
            acc = math.dist(sub(pos[t], pos[t - 1]), sub(pos[t - 1], pos[t - 2]))
            worst_acc = max(worst_acc, acc)
            if acc > 0.12 + 1e-9:
                problems.append('%s: broom %s speed changes %.3f blocks/tick^2 at t %d' % (sc.sid, b['id'], acc, t))
                break
        flying[b['id']] = (pos, fl)
    ids = sorted(flying)
    for i in range(len(ids)):
        for j in range(i + 1, len(ids)):
            pa, fa = flying[ids[i]]
            pb, fb = flying[ids[j]]
            for t in range(n + 1):
                if not (fa[t] or fb[t]):
                    continue
                d = math.dist(pa[t], pb[t])
                least_pair = min(least_pair, d)
                if d < 1.2:
                    problems.append('%s: brooms %s and %s are %.2f apart at t %d' % (sc.sid, ids[i], ids[j], d, t))
                    break
    return least_pair, worst_acc


def check_lying(sc, scene, problems):
    """Every lawn broom lies on the grass (its box bottom within 0.05 of lawn_y) until it first moves."""
    worst = 0.0
    for bid, _ in sc.lying:
        b = E.actor_by_id(scene, bid)
        keys = b['path']
        first_move = None
        p0 = keys[0]['pos']
        l0 = (b.get('look') or [{}])[0]
        for k in keys[1:]:
            if math.dist(k['pos'], p0) > 1e-6:
                first_move = keys[keys.index(k) - 1]['t']
                break
        for k in (b.get('look') or [])[1:]:
            if any(abs(k.get(f, 0.0) - l0.get(f, 0.0)) > 1e-6 for f in ('yaw', 'pitch', 'roll')):
                pt = b['look'][b['look'].index(k) - 1]['t']
                first_move = pt if first_move is None else min(first_move, pt)
                break
        end = first_move if first_move is not None else scene['length']
        for t in range(0, int(end) + 1, 5):
            low = E.broom_lowest(scene, bid, t) - LAWN_Y
            worst = max(worst, abs(low))
            if not (-1e-6 <= low <= 0.05):
                problems.append('%s: broom %s lies %.3f off the grass at t %d' % (sc.sid, bid, low, t))
                break
    return worst


def check_lines(sc, scene, problems):
    """Every voice line has its subtitle with the clip's measured length, no two lines overlap, and every line ends
    before the scene does. No spoken text names the boy."""
    for line, t, secs, slot in sc.lines:
        w = [x for x in scene['words'] if x['t'] == t and x.get('subtitle') and x['text'] == LINES[line][1]]
        if not w or w[0]['duration'] < int(math.ceil(secs * 20)):
            problems.append('%s: %s has no subtitle covering its %.2f s' % (sc.sid, line, secs))
        if t + secs * 20 > scene['length'] - 6:
            problems.append('%s: %s runs into the scene end' % (sc.sid, line))
        if slot is not None and t + secs * 20 > slot + 1e-6:
            problems.append('%s: %s runs past its slot' % (sc.sid, line))
    sl = sorted(sc.lines, key=lambda l: l[1])
    for a, b in zip(sl, sl[1:]):
        if b[1] < a[1] + a[2] * 20 + 2:
            problems.append('%s: %s starts before %s ends' % (sc.sid, b[0], a[0]))
    for w in scene['words']:
        txt = w['text'] + ' ' + w.get('speaker', '')
        if any(x in txt for x in ('Harry', 'Potter')) or ('{' in txt and txt.replace('{viewer}', '').count('{')):
            problems.append('%s: word text %r names the boy or holds a placeholder other than {viewer}' % (sc.sid, txt))


def check_keys(sc, scene, problems):
    """Ids unique, key times sorted and inside the scene."""
    ids = [a['id'] for a in scene['actors']]
    if len(ids) != len(set(ids)):
        problems.append('%s: duplicate actor ids' % sc.sid)
    for a in scene['actors']:
        for kk in ('path', 'look', 'anims', 'poses', 'actions'):
            ts = [k['t'] for k in a.get(kk, [])]
            if ts != sorted(ts) or any(not (0 <= t <= scene['length']) for t in ts):
                problems.append('%s: %s %s keys out of order or outside the scene' % (sc.sid, a['id'], kk))
            if kk in ('path', 'look', 'anims') and len(ts) != len(set(ts)):
                problems.append('%s: %s %s has two keys at one time' % (sc.sid, a['id'], kk))
        for part, d in a.get('limbs', {}).items():
            for kind in ('rot', 'offset'):
                ts = [k['t'] for k in d.get(kind, [])]
                if ts != sorted(ts) or len(ts) != len(set(ts)):
                    problems.append('%s: %s %s %s keys out of order' % (sc.sid, a['id'], part, kind))
        if a.get('type') == 'player':
            s = a.get('scale', 1.0)
            if s not in (KID, THIRD, ADULT):
                problems.append('%s: %s has scale %s' % (sc.sid, a['id'], s))
    cam = scene['camera']
    if [k['t'] for k in cam] != sorted(k['t'] for k in cam) or cam[0]['t'] != 0:
        problems.append('%s: camera keys out of order or not from 0' % sc.sid)
    ev = [e['t'] for e in scene['events']]
    if ev != sorted(ev):
        problems.append('%s: events out of order' % sc.sid)


def check_void(sc, scene, problems, solid):
    """Lawn cameras (ruling for Task 7): every camera key's frame, and the camera every 10 ticks, shows at most
    VOID_BOUND of empty space below the horizon, and stays inside CAM_REGION. Returns the worst."""
    cam = scene['camera']
    worst = (0.0, None)
    x0, x1, z0, z1 = FL.CAM_REGION
    cache = sc.notes.setdefault('void_cache', {})
    ts = sorted(set([k['t'] for k in cam] + list(range(0, scene['length'] + 1, 10))))
    for t in ts:
        c = E.engine_cam(cam, t)
        lk, fov = E.engine_look(scene, cam, t)
        if not (x0 - 1e-6 <= c[0] <= x1 + 1e-6 and z0 - 1e-6 <= c[2] <= z1 + 1e-6):
            problems.append('%s: camera at t %s (%s) is outside CAM_REGION' % (sc.sid, t, r3(c)))
        key = (tuple(round(v, 2) for v in c), tuple(round(v, 1) for v in lk), round(fov))
        if key not in cache:
            cache[key] = FL.void_fraction(c, lk, solid, fov=max(fov, 40.0))
        v = cache[key]
        if v > worst[0]:
            worst = (v, t)
        if v > FL.VOID_BOUND:
            problems.append('%s: camera at t %s shows %.3f void below the horizon' % (sc.sid, t, v))
    return worst


# ================================================================ camera helpers
def smoothed(fn, sigma, lo=None, hi=None):
    """fn(t) averaged with Gaussian weights over +-2 sigma ticks (whole-tick samples, clamped to [lo, hi]): a look
    target that follows an actor without his bob, his bank or his keys' corners."""
    if sigma <= 0:
        return fn
    n = int(math.ceil(2 * sigma))
    ws = [math.exp(-0.5 * (j / sigma) ** 2) for j in range(-n, n + 1)]
    tot = sum(ws)
    cache = {}

    def g(t):
        if t in cache:
            return cache[t]
        acc = [0.0, 0.0, 0.0]
        for j, wt in zip(range(-n, n + 1), ws):
            tt = t + j
            if lo is not None:
                tt = max(lo, tt)
            if hi is not None:
                tt = min(hi, tt)
            v = fn(tt)
            acc = [acc[i] + v[i] * wt for i in range(3)]
        cache[t] = [c / tot for c in acc]
        return cache[t]
    return g


def slerp_dir(d0, d1, e):
    d0, d1 = norm(d0), norm(d1)
    c = E.clamp(sum(d0[i] * d1[i] for i in range(3)), -1.0, 1.0)
    om = math.acos(c)
    if om < 1e-6:
        return d0
    s0, s1 = math.sin((1 - e) * om) / math.sin(om), math.sin(e * om) / math.sin(om)
    return [d0[i] * s0 + d1[i] * s1 for i in range(3)]


def move(p0, p1, t0, t1, ease=smoother):
    return lambda t: lerp(p0, p1, ease((t - t0) / float(t1 - t0)))


def const(v):
    return lambda t: v


def ramp(a, b, t0, t1, ease=smoother):
    return lambda t: a + (b - a) * ease((t - t0) / float(t1 - t0))


def turn_look(pos_fn, l0, l1, t0, t1, dist=30.0, ease=smoother):
    """A look point that turns on the great circle from looking at l0 (from pos_fn(t0)) to l1 (from pos_fn(t1))."""
    d0 = sub(l0, pos_fn(t0))
    d1 = sub(l1, pos_fn(t1))
    return lambda t: add(pos_fn(t), slerp_dir(d0, d1, ease((t - t0) / float(t1 - t0))), dist)


def eye_fn(scene, aid, up=0.0):
    a = E.actor_by_id(scene, aid)

    def f(t):
        if E.broom_rider_at(scene, a, t) is not None:
            p = E.rider_eye(scene, a, t)
        else:
            r = E.own_sample(a, 'path', t) if isinstance(a.get('ride'), dict) else E.sample_path(a['path'], t)
            p = [r[0], r[1] + E.actor_eye_height(a, t), r[2]]
        return [p[0], p[1] + up, p[2]]
    return f


def first_person(sc, scene, aid, t0, t1, fwd=0.2, dist=2.5, fov=72.0, step=2, look_fn=None):
    """A first-person shot at the actor's posed eye (fwd blocks out of his face), looking where his head looks
    (or at look_fn(t)); the head is exempt from the clearance check inside the shot."""
    a = E.actor_by_id(scene, aid)

    def cam(t):
        return head_eye(scene, a, t, fwd)

    def look(t):
        if look_fn:
            return look_fn(t)
        e0, e1 = head_eye(scene, a, t, 0.0), head_eye(scene, a, t, 1.0)
        return add(cam(t), norm(sub(e1, e0)), dist)
    sc.shot(t0, t1, cam, look, const(fov), step)
    sc.first_person.append((t0, t1, aid))


def lesson_camera(sc, P, B, S, hooch):
    V = view(sc)
    hs = LA['hooch_spot']['pos']
    # S1 0.0-6.4 the establishing crane: high over the lawn, the castle on its crag, down to the class
    P0, P1 = [16.0, 24.0, 64.0], [7.6, 3.6, 47.5]
    pf = move(P0, P1, 0, 128)
    sc.shot(0, 128, pf, turn_look(pf, [-6.0, 34.0, -120.0], [0.0, 1.0, 24.5], 0, 128), ramp(70.0, 62.0, 0, 128), 4)
    # S2 6.4-10.0 Madam Hooch, medium, a slow push
    pf = move([1.35, 1.55, 20.7], [1.15, 1.55, 20.15], 128, 200)
    sc.shot(128, 200, pf, const([hs[0], 1.58, hs[2]]), ramp(46.0, 43.0, 128, 200), 8)
    # S3 10.0-13.8 down the east row at hand height as the hands go out over the handles
    pf = move([1.25, 1.05, 38.6], [1.25, 1.05, 31.4], 200, 276)
    sc.shot(200, 276, pf, lambda t: [4.0, 0.72, pf(t)[2] - 3.2], const(55.0), 6)
    # S4 13.8-16.6 the boy, front left, his hand out over his broom
    v = P['viewer']['spot']
    pf = move([v[0] - 1.65, 0.95, v[2] + 0.95], [v[0] - 1.55, 0.97, v[2] + 0.8], 276, 332)
    sc.shot(276, 332, pf, const([v[0] - 0.35, 0.8, v[2] - 0.1]), const(50.0), 8)
    # S5 16.6-19.4 Hooch close: "Now, firmly, and mean it"
    pf = move([0.6, 1.62, 18.75], [0.5, 1.62, 18.45], 332, 388)
    sc.shot(332, 388, pf, const([hs[0], 1.58, hs[2]]), ramp(38.0, 36.0, 332, 388), 8)
    # S6 19.4-21.4 over his right shoulder, along the arm, the west row beyond
    pf = move([v[0] + 0.85, 1.42, v[2] - 0.65], [v[0] + 0.8, 1.4, v[2] - 0.6], 388, 428)
    sc.shot(388, 428, pf, const([v[0] - 1.8, 0.35, v[2] - 0.45]), const(55.0), 8)
    # S7 21.4-24.2 the chorus, across the aisle at the west row: brooms twitch and roll, one smacks a boy
    pf = move([1.35, 0.92, 26.0], [1.4, 0.95, 26.9], 428, 484)
    sc.shot(428, 484, pf, move([-4.0, 0.62, 30.9], [-4.0, 0.65, 31.3], 428, 484), const(56.0), 8)
    # S8 24.2-27.0 first person: his broom leaps into his hand on the first try
    bv = P['viewer']['broom']
    aim = smoothed(lambda t: lerp([bv[0] - 0.3, 0.42, bv[2] + 0.05], [bv[0] - 0.3, 0.62, bv[2] + 0.05],
                                   smooth((t - 498) / 14.0)), 3.0)
    first_person(sc, V, 'viewer', 484, 540, fwd=0.05, fov=70.0, look_fn=aim)
    # S9 27.0-29.6 Hooch, the whistle to her lips (the class mounts behind the camera)
    pf = move([0.95, 1.58, 19.6], [0.85, 1.58, 19.3], 540, 592)
    sc.shot(540, 592, pf, const([hs[0], 1.62, hs[2]]), const(42.0), 8)
    # S10 29.6-33.0 Neville's broom lifts him, bolts and bucks; Hooch runs through the foreground
    nev = smoothed(eye_fn(V, 'neville'), 6.0)
    pf = move([-0.2, 0.75, 18.6], [-0.6, 0.9, 19.2], 592, 660)
    sc.shot(592, 660, pf, lambda t: lerp(nev(t), [-6.0, 2.5, 28.0], 0.25), ramp(60.0, 66.0, 592, 660), 4)
    # S11 33.0-35.6 wide: he is thrown, falls; the broom shoots off over the wall
    pf = move([8.5, 6.4, 29.5], [8.3, 6.2, 30.6], 660, 712)
    sc.shot(660, 712, pf, move([-11.5, 3.5, 35.0], [-12.5, 1.0, 36.0], 660, 712), const(62.0), 8)
    # S12 35.6-37.6 the Remembrall rolls out of the grass and comes to rest
    pf = move([-5.75, 0.3, 36.75], [-5.8, 0.3, 36.8], 712, 752)
    sc.shot(712, 752, pf, const([-8.0, 0.12, 37.45]), const(48.0), 8)
    # S13 37.6-43.0 Hooch gets Neville up, "Nobody moves!", and leads him away
    mid = smoothed(lambda t: lerp(eye_fn(V, 'hooch')(t), eye_fn(V, 'neville')(t), 0.5), 10.0)
    pf = move([-8.4, 1.45, 33.4], [-9.2, 1.5, 34.6], 752, sc.L)
    sc.shot(752, sc.L, pf, mid, ramp(50.0, 47.0, 752, sc.L), 6)
    eye = lambda aid: (lambda sc_, t: eye_fn(sc_, aid)(t))
    sc.framing += [(130, 198, eye('hooch'), 'Hooch (S2)'), (280, 330, eye('viewer'), 'the boy (S4)'),
                   (334, 386, eye('hooch'), 'Hooch close (S5)'), (430, 470, eye(SMACK), 'the smacked boy (S7)'),
                   (494, 512, lambda sc_, t: broom_point(sc_, 'broom_viewer', t, [0.0, 0.0, 0.0]), 'his broom (S8)'),
                   (505, 530, lambda sc_, t: hand(sc_, E.actor_by_id(sc_, 'viewer'), 'right_arm', t), 'his hand (S8)'),
                   (560, 586, lambda sc_, t: hand(sc_, E.actor_by_id(sc_, 'hooch'), 'right_arm', t), 'the whistle (S9)'),
                   (596, 656, eye('neville'), 'Neville aloft (S10)'), (662, 690, eye('neville'), 'Neville falls (S11)'),
                   (716, 750, lambda sc_, t: E.ball_pos(sc_, 'remembrall', t), 'the Remembrall (S12)'),
                   (756, 850, eye('neville'), 'Neville led away (S13)')]


def check_walkers(sc, scene, problems, step=2):
    """People on their feet: no body point (feet, middle, head, the corners of the torso) inside a block, and no two
    standing people closer than 0.6 while one of them walks. Riders are the fliers' check; parked actors (under
    the set) and unseen ones are skipped. Returns the least gap seen."""
    w = sc.world
    people = [a for a in scene['actors'] if a.get('type') == 'player']
    least = 9.0
    for t in range(0, scene['length'] + 1, step):
        here = {}
        for a in people:
            if E.ride_of(a) is not None and E.rides_at(a, t):
                continue
            p = E.own_sample(a, 'path', t) if isinstance(a.get('ride'), dict) else E.sample_path(a['path'], t)
            if p is None or p[1] < -5 or a['id'] in sc.unseen_at(t):
                continue
            here[a['id']] = (p, a.get('scale', 1.0), a)
        for aid, (p, s, a) in here.items():
            for dy in (0.06, 0.9 * s, 1.7 * s):
                for dx, dz in ((0, 0), (0.17 * s, 0.17 * s), (-0.17 * s, -0.17 * s), (0.17 * s, -0.17 * s),
                               (-0.17 * s, 0.17 * s)):
                    q = [p[0] + dx, p[1] + dy, p[2] + dz]
                    hit = E.shape_hit(w, q)
                    if hit and E.bid_of(hit) not in PLANTS:
                        problems.append('%s t %d: %s body point %s in %s' % (sc.sid, t, aid, r3(q), E.bid_of(hit)))
                        return least
        ids = sorted(here)
        for i in range(len(ids)):
            for j in range(i + 1, len(ids)):
                pa, pb = here[ids[i]][0], here[ids[j]][0]
                if abs(pa[1] - pb[1]) > 1.6:
                    continue
                d = math.hypot(pa[0] - pb[0], pa[2] - pb[2])
                least = min(least, d)
                if d < 0.6:
                    problems.append('%s t %d: %s and %s stand %.2f apart' % (sc.sid, t, ids[i], ids[j], d))
                    return least
    return least


def check_framing(sc, scene, problems):
    """The film's own asserts: each moment the shot list promises is in the 16:9 frame (the subject's point inside
    the frustum, past the near plane). Returns the number of points checked."""
    n = 0
    for t0, t1, fn, label in sc.framing:
        for t in range(int(t0), int(t1) + 1, 2):
            p = fn(scene, t)
            c = E.engine_cam(scene['camera'], t)
            lk, fov = E.engine_look(scene, scene['camera'], t)
            n += 1
            if not E.in_view(c, lk, fov, p, aspect=16 / 9.0):
                problems.append('%s: %s is out of frame at t %d' % (sc.sid, label, t))
                break
    return n
