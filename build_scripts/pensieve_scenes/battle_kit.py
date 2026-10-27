"""The toolkit the Battle of Hogwarts scenes are written with (battle_scenes.py).

  Film       one scene's events, words, camera and actors, emitted as scene JSON, with checks
  Actor      one actor's tracks, built in time order (key, walk, face, cast, act, pose, anim, limb)
  Melee      a battle simulator: two sides in an area, each fighter picking an enemy, closing, strafing, casting,
             blocking, getting hit, falling; reinforcements arriving; it writes ordinary actor tracks. Fights form
             in shifting clusters (pairs, threes, a rush, a fall back), never in lines.

The ground comes from battle_ground (the hogwarts_battle set). Times are seconds; the JSON gets '12.5s'.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import battle_ground as BG  # noqa: E402
from scene_common import write_scene  # noqa: E402

BEAM_SPEED = 1.55 * 20          # blocks a second
# blocks a view passes through (thin or clear): they stop a lens being put inside them, not a line of sight
SEE_THROUGH = ('fence', 'bars', 'pane', 'chain', 'glass', 'lantern', 'torch', 'rod', 'candle', 'wall_banner', 'sign',
               'button', 'ladder', 'trapdoor', 'cobweb', 'scaffolding')
# where each creature's eyes are, per unit of scale (the client aims at the entity's own eye)
EYE = {'player': 1.62, 'hogwartsmagic:giant': 3.9, 'hogwartsmagic:living_statue': 2.5,
       'hogwartsmagic:acromantula': 0.9, 'hogwartsmagic:troll': 2.9, 'hogwartsmagic:dementor': 2.6,
       'hogwartsmagic:patronus': 1.6, 'hogwartsmagic:memory_block': 0.0}


# (height, radius) of each body per unit of scale, for keeping lenses out of them: the spider's legs spread wide
BODY = {'hogwartsmagic:giant': (4.4, 1.3), 'hogwartsmagic:acromantula': (1.8, 3.3), 'hogwartsmagic:troll': (3.2, 1.1),
        'hogwartsmagic:living_statue': (2.9, 0.7)}


def sees_through(x, y, z):
    b = BG.block(x, y, z)
    return b is None or not BG.solid(x, y, z) or any(k in b for k in SEE_THROUGH)


# ---------------------------------------------------------------- small helpers
def r2(v):
    return round(v + 0.0, 2)


def T(s):
    return '%gs' % round(s, 2)


def tval(s):
    return float(s[:-1]) if isinstance(s, str) else s / 20.0


def yaw_to(a, b):
    """Minecraft yaw from a to b (0 = +Z, 90 = -X)."""
    return math.degrees(math.atan2(-(b[0] - a[0]), b[2] - a[2]))


def pitch_to(a, b):
    d = math.hypot(b[0] - a[0], b[2] - a[2])
    return -math.degrees(math.atan2(b[1] - a[1], d))


def unwrap(prev, y):
    while y - prev > 180:
        y -= 360
    while y - prev < -180:
        y += 360
    return y


def dist2(a, b):
    return math.hypot(a[0] - b[0], a[2] - b[2])


class Rng:
    def __init__(self, seed):
        self.s = seed

    def __call__(self):
        self.s = (self.s * 1103515245 + 12345) % 2147483648
        return self.s / 2147483648

    def uni(self, a, b):
        return a + (b - a) * self()

    def pick(self, seq):
        return seq[int(self() * len(seq)) % len(seq)]

    def chance(self, p):
        return self() < p


# ---------------------------------------------------------------- the ground
def walkable(x, z, y=0.0, tol=0.6):
    """A person can stand at (x, z) on a floor at about height y (feet), clear overhead."""
    g = BG.ground(x, z, below=y + 1.6)
    return g > -900 and abs(g - y) <= tol and BG.clear(x, g + 0.05, z)


def line_walkable(a, b, y=0.0, step=0.3, margin=0.3):
    d = dist2(a, b)
    n = max(1, int(d / step))
    for i in range(n + 1):
        f = i / n
        x, z = a[0] + (b[0] - a[0]) * f, a[2] + (b[2] - a[2]) * f
        for ox, oz in ((margin, 0), (-margin, 0), (0, margin), (0, -margin)):
            if not walkable(x + ox, z + oz, y):
                return False
    return True


def route(a, b, y=0.0, box=None):
    """Waypoints (b last) from a to b over a floor at height y: straight if clear, else A* over its cells."""
    import heapq
    if line_walkable(a, b, y):
        return [b]
    start, goal = (math.floor(a[0]), math.floor(a[2])), (math.floor(b[0]), math.floor(b[2]))
    x0, x1, z0, z1 = box or (min(start[0], goal[0]) - 30, max(start[0], goal[0]) + 30,
                             min(start[1], goal[1]) - 30, max(start[1], goal[1]) + 30)
    openq = [(0.0, start)]
    came, cost = {start: None}, {start: 0.0}
    while openq:
        _, c = heapq.heappop(openq)
        if c == goal:
            break
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (c[0] + dx, c[1] + dz)
                if n == c or not (x0 <= n[0] <= x1 and z0 <= n[1] <= z1) or n in cost and cost[n] <= cost[c] + 1:
                    continue
                if not walkable(n[0] + 0.5, n[1] + 0.5, y, tol=0.6):
                    continue
                if dx and dz and not (walkable(c[0] + dx + 0.5, c[1] + 0.5, y, tol=0.6) and
                                      walkable(c[0] + 0.5, c[1] + dz + 0.5, y, tol=0.6)):
                    continue                     # no cutting a corner past a blocked cell
                nc = cost[c] + math.hypot(dx, dz)
                if nc < cost.get(n, 1e9):
                    cost[n] = nc
                    came[n] = c
                    heapq.heappush(openq, (nc + math.hypot(goal[0] - n[0], goal[1] - n[1]), n))
    if goal not in came:
        return None
    cells = []
    c = goal
    while c is not None:
        cells.append((c[0] + 0.5, y, c[1] + 0.5))
        c = came[c]
    cells.reverse()
    cells[0], cells[-1] = a, b
    out, here, i = [], a, 0
    while i < len(cells) - 1:
        j = len(cells) - 1
        while j > i + 1 and not line_walkable(here, cells[j], y):
            j -= 1
        out.append(cells[j])
        here, i = cells[j], j
    return out


# ---------------------------------------------------------------- the film
class Film:
    def __init__(self, sid, title, length):
        self.id, self.title, self.L = sid, title, length
        self.events, self.words, self.cam, self.actors = [], [], [], []
        self.poses = {'standing': {}}
        self.ids = set()
        self.cam_issues = []

    def ev(self, t, kind, id_, **kw):
        if t > self.L:
            return
        e = {'t': T(max(0.0, t)), 'kind': kind, 'id': id_}
        e.update(kw)
        self.events.append(e)

    def snd(self, t, id_, at=None, volume=None, pitch=None):
        kw = {}
        if at is not None:
            kw['at'] = [r2(c) for c in at]
        if volume is not None:
            kw['volume'] = r2(volume)
        if pitch is not None:
            kw['pitch'] = r2(pitch)
        self.ev(t, 'sound', id_, **kw)

    def part(self, t, id_, at, count, spread):
        self.ev(t, 'particles', id_, at=[r2(c) for c in at], count=int(min(500, count)), spread=[r2(s) for s in spread])

    def block(self, t, id_, at):
        self.ev(t, 'block', id_, at=[int(math.floor(c)) for c in at])

    def effect(self, t, id_, at, **kw):
        self.ev(t, 'effect', id_, at=[r2(c) for c in at], **kw)

    def music(self, t, name, volume=1.0):
        self.ev(t, 'music', 'hogwartsmagic:pensieve.music.' + name, volume=volume)

    def sfx(self, t, name, volume=1.0, **kw):
        self.ev(t, 'sfx', 'hogwartsmagic:pensieve.sfx.' + name, volume=volume, **kw)

    def voice(self, t, name, volume=1.0):
        self.ev(t, 'voice', 'hogwartsmagic:pensieve.voice.' + name, volume=volume)

    def say(self, t, speaker, text, dur=4.0, voice=None):
        w = {'t': T(t), 'text': text, 'subtitle': True, 'duration': T(dur)}
        if speaker:
            w['speaker'] = speaker
        self.words.append(w)
        if voice:
            self.voice(t, voice)

    def narrate(self, t, text, dur=4.0, size=None):
        w = {'t': T(t), 'text': text, 'narration': True, 'duration': T(dur)}
        if size:
            w['size'] = size
        self.words.append(w)

    def shot(self, t, pos, look, fov, cut=True, ease=None, roll=None):
        k = {'t': T(t), 'pos': [r2(v) for v in pos],
             'look_at': look if isinstance(look, str) else [r2(v) for v in look], 'fov': fov}
        if cut:
            k['cut'] = True
        if ease:
            k['ease'] = ease
        if roll is not None:
            k['roll'] = roll
        self.cam.append(k)

    def actor(self, aid, typ, pos, yaw=0.0, **kw):
        assert aid not in self.ids, aid
        self.ids.add(aid)
        return Actor(self, aid, typ, pos, yaw, **kw)

    # ---- rubble and blasts
    def blast(self, t, at, big=False, rng=None, sound=True):
        self.part(t, 'minecraft:explosion_emitter' if big else 'minecraft:explosion', at, 1 if big else 3,
                  (0.6, 0.6, 0.6))
        self.part(t, 'minecraft:large_smoke', at, 60 if big else 25, (1.4, 1.0, 1.4))
        self.part(t + 0.05, 'minecraft:lava', at, 12 if big else 5, (0.8, 0.6, 0.8))
        if sound:
            self.snd(t, 'minecraft:entity.generic.explode', at=at, volume=2.5 if big else 1.4,
                     pitch=(rng.uni(0.6, 0.9) if rng else 0.75))

    def rubble(self, t, at, n, rng, spread=1.5, fall=None, block_ids=None, scale=(0.5, 1.0), out=None):
        """n stone blocks thrown from `at` and tumbling down to the floor (feet y `fall`, default the ground)."""
        ids = block_ids or ['minecraft:stone_bricks', 'minecraft:cracked_stone_bricks', 'minecraft:cobblestone',
                            'minecraft:mossy_stone_bricks', 'minecraft:andesite']
        made = []
        for k in range(n):
            sc = rng.uni(*scale)
            a = self.actor('rubble_%d' % len(self.actors), 'hogwartsmagic:memory_block', at, 0.0, scale=r2(sc))
            a.d['block'] = rng.pick(ids)
            a.act(0.0, 'vanish')
            a.act(t, 'appear')
            vx, vz = rng.uni(-1, 1) * spread, rng.uni(-1, 1) * spread
            if out:
                vx += out[0]
                vz += out[1]
            vy = rng.uni(1.0, 4.0)
            x, y, z = at
            gy = (fall if fall is not None else BG.ground(x + vx * 1.5, z + vz * 1.5, below=y)) + sc * 0.5
            if gy < -900:
                gy = y - 20
            # a parabola sampled every 0.15 s until it lands, then it lies still
            tt, keys = 0.0, [(t, (x, y, z))]
            while True:
                tt += 0.15
                py = y + vy * tt - 9.0 * tt * tt
                px, pz = x + vx * tt, z + vz * tt
                if py <= gy or tt > 3.0:
                    keys.append((t + tt, (px, max(py, gy), pz)))
                    break
                keys.append((t + tt, (px, py, pz)))
            for i, (kt, p) in enumerate(keys):
                a.key(kt, p, 'linear' if 0 < i < len(keys) - 1 else ('out' if i else None))
            spin = rng.uni(200, 700) * rng.pick((-1, 1))
            a.look = [(t, rng.uni(0, 90), 0.0, None), (keys[-1][0], rng.uni(0, 90) + spin * 0.3, spin, 'out')]
            made.append(a)
        return made

    # ---- emit
    def trim(self):
        """Cut everything at the scene's length: keys past it end in one key at L where the track was then."""
        L = self.L
        for a in self.actors:
            if a.path[-1][0] > L:
                p = a.pos_at(L)
                a.path = [k for k in a.path if k[0] < L] + [(L, p, 'linear')]
            if a.look[-1][0] > L:
                y = a.yaw_at(L)
                a.look = [k for k in a.look if k[0] < L] + [(L, y, a.look[-1][2], None)]
            for lst in (a.actions, a.poses, a.anims):
                lst[:] = [k for k in lst if tval(k['t']) <= L]
            for tr in a.limbs.values():
                for kk in ('rot', 'offset'):
                    if kk in tr:
                        tr[kk] = [k for k in tr[kk] if tval(k['t']) <= L] or tr[kk][:1]
        self.events = [e for e in self.events if tval(e['t']) <= L]
        self.words = [w for w in self.words if tval(w['t']) <= L]

    def scene(self, set_, radius, time_of_day, nxt=None, white=False, fog_far=None, build_ticks=160,
              end_action=None):
        self.trim()
        s = {'title': self.title, 'length': T(self.L), 'radius': radius}
        if fog_far:
            s['fog_far'] = fog_far
        if nxt:
            s['next'] = nxt
            if white:
                s['handover_colour'] = 'white'
        if end_action:
            s['end_action'] = end_action
        st = dict(set_)
        st['time_of_day'] = time_of_day
        st['build_ticks'] = build_ticks
        s['set'] = st
        s['poses'] = self.poses
        s['actors'] = [a.emit() for a in self.actors]
        s['events'] = sorted(self.events, key=lambda e: tval(e['t']))
        s['words'] = sorted(self.words, key=lambda w: tval(w['t']))
        s['camera'] = self.cam
        return s

    def check(self, s):
        problems = []
        L = self.L
        for a in s['actors']:
            for field in ('path', 'look', 'poses', 'actions', 'anims'):
                ts = [tval(k['t']) for k in a.get(field, [])]
                if ts != sorted(ts):
                    problems.append('%s %s not sorted' % (a['id'], field))
                if any(x < 0 or x > L + 1e-6 for x in ts):
                    problems.append('%s %s time outside the film' % (a['id'], field))
            ts = [tval(k['t']) for k in a['path']]
            if len(ts) != len(set(ts)):
                problems.append('%s path has repeated times' % a['id'])
            for pn, tr in a.get('limbs', {}).items():
                ts = [tval(k['t']) for k in tr.get('rot', [])]
                if ts != sorted(ts):
                    problems.append('%s limb %s not sorted' % (a['id'], pn))
            if a['type'] != 'hogwartsmagic:memory_block':
                ys = [k['yaw'] for k in a['look']]
                for y0, y1 in zip(ys, ys[1:]):
                    if abs(y1 - y0) > 180:
                        problems.append('%s yaw jumps %s -> %s' % (a['id'], y0, y1))
                        break
        for lst, nm in ((s['events'], 'events'), (s['words'], 'words'), (s['camera'], 'camera')):
            for k in lst:
                if not 0 <= tval(k['t']) <= L:
                    problems.append('%s time %s outside the film' % (nm, k['t']))
        ct = [tval(k['t']) for k in s['camera']]
        if ct != sorted(ct):
            problems.append('camera not sorted')
        for k in s['camera']:
            p = k['pos']
            if BG.solid(*p):
                problems.append('camera at %s inside %s' % (k['t'], BG.block(*p)))
        problems += self.camera_problems(s)
        cuts = [0.0] + [tval(k['t']) for k in s['camera'] if k.get('cut')] + [L]
        lens = [round(b - a, 2) for a, b in zip(cuts, cuts[1:])]
        for ln in lens:
            if ln < 2 or ln > 7:
                problems.append('shot length %s' % ln)
        wt = sorted((tval(w['t']), w) for w in s['words'] if not w.get('narration'))
        for (a, _), (b, _) in zip(wt, wt[1:]):
            if b - a < 1.2:
                problems.append('lines closer than 1.2 s at %.1f' % a)
        # standing actors on a floor, never inside a block
        for act in self.actors:
            if act.d['type'] != 'player' or act.d.get('ride') or act.airborne:
                continue
            for i in range(int(L * 4) + 1):
                t = i / 4
                if not act.shown(t) or act.in_smoke(t):
                    continue
                p = act.pos_at(t)
                if BG.solid(p[0], p[1] + 0.1, p[2]) or BG.solid(p[0], p[1] + 1.2, p[2]):
                    problems.append('%s inside %s at %.2f %s' % (act.id, BG.block(p[0], p[1] + 0.1, p[2]) or
                                                                 BG.block(p[0], p[1] + 1.2, p[2]), t,
                                                                 [r2(c) for c in p]))
                    break
                g = BG.ground(p[0], p[2], below=p[1] + 0.6)
                if p[1] - g > 1.05:
                    problems.append('%s floating at %.2f %s (ground %s)' % (act.id, t, [r2(c) for c in p], g))
                    break
        return problems, lens

    # ---- solved shots: say what the shot is of, the solver finds a lens position that works for all of it
    def _target(self, subject, t):
        if subject is None:
            return None
        if isinstance(subject, Actor):
            return subject.eye(t)
        if callable(subject):
            return tuple(subject(t))
        return tuple(subject)

    def lens_clear(self, pos, tgt, t, ignore=(), lens_r=0.35, actor_margin=0.3):
        """None if a lens at pos looking at tgt at time t sees cleanly, else why not."""
        for dx, dy, dz in ((0, 0, 0), (lens_r, 0, 0), (-lens_r, 0, 0), (0, lens_r, 0), (0, -lens_r, 0),
                           (0, 0, lens_r), (0, 0, -lens_r)):
            if BG.solid(pos[0] + dx, pos[1] + dy, pos[2] + dz):
                return 'block'
        d = math.dist(pos, tgt)
        m = max(2, int(d / 0.25))
        for j in range(1, m):
            q = [pos[c] + (tgt[c] - pos[c]) * j / m for c in range(3)]
            if math.dist(q, tgt) < 1.0:
                break
            if not sees_through(*q):
                return 'wall'
        for a in self.actors:
            if a.d['type'] == 'hogwartsmagic:memory_block' or a.id in ignore or not a.shown(t) or a.in_smoke(t):
                continue
            p = a.pos_at(t)
            h, r = BODY.get(a.d['type'], (1.9, 0.45))
            h, r = h * a.scale, r * a.scale
            cy = min(max(pos[1], p[1]), p[1] + h)
            if math.dist(pos, (p[0], cy, p[2])) < r + actor_margin:
                return 'actor ' + a.id
            # and nobody standing right across the near part of the view (a head filling the frame)
            near = min(d - 1.0, 3.0)
            if near > 0.6:
                for j in range(1, 6):
                    q = [pos[c] + (tgt[c] - pos[c]) * (near * j / 5) / d for c in range(3)]
                    cq = min(max(q[1], p[1]), p[1] + h)
                    if math.dist(q, (p[0], cq, p[2])) < r:
                        return 'blocker ' + a.id
        for a, tp in self.protegos:
            if tp - 0.1 <= t <= tp + 2.0 and math.dist(pos, a.chest(t)) < 2.4:
                return 'protego ' + a.id
        return None

    @property
    def protegos(self):
        out = []
        for a in self.actors:
            for k in a.actions:
                if k['do'] == 'cast' and k.get('spell') == 'protego':
                    out.append((a, tval(k['t'])))
        return out

    def frame(self, t0, t1, subject, dist=6.0, height=0.3, az=None, fov=60, move='push', amount=0.18,
              look_offset=(0, 0, 0), cut=True, track=False, ignore=(), tries=None, name='', lifts=None):
        """A solved shot from t0 to t1 of `subject` (an Actor, a point, or a function of time giving a point).
        The lens sits `dist` from the subject's eye (or the point) on the azimuth `az` (degrees, Minecraft yaw of
        the direction from the subject to the lens; default: in front of an actor, three-quarter), `height` above
        it, and moves: 'push' in / 'pull' out by `amount` of the distance, 'slide' round by amount*60 degrees,
        'rise' up by amount*dist, 'static'. With `track` the lens keeps its offset to a moving subject (keyed every
        second). Candidates round the preferred one are tried until the whole shot is clean: no block at the lens,
        no wall in the view, nobody in or crowding the lens, no Protego round it. Returns the problem if none was."""
        def tgt(t):
            p = self._target(subject, t)
            return (p[0] + look_offset[0], p[1] + look_offset[1], p[2] + look_offset[2])
        ig = set(ignore)
        if isinstance(subject, Actor):
            ig.add(subject.id)
            if az is None:
                az = subject.yaw_at(t0) + 28.0
        if az is None:
            az = 0.0
        offsets = tries or [0, 20, -20, 40, -40, 60, -60, 90, -90, 120, -120, 150, -150, 180]
        scales = [1.0, 0.8, 1.25, 0.65, 1.5, 1.9]
        lifts = lifts or [0.0, 1.2, 2.5, -0.5, 4.0]
        best = None
        for sc in scales:
            for lift in lifts:
                for off in offsets:
                    a = math.radians(az + off)
                    def lens(t, f, a=a, sc=sc, lift=lift):
                        base = tgt(t0 if not track else t)
                        dd = dist * sc
                        aa = a
                        hh = height + lift
                        if move == 'push':
                            dd *= 1 - amount * f
                        elif move == 'pull':
                            dd *= 1 + amount * f
                        elif move == 'slide':
                            aa = a + math.radians(amount * 60 * f)
                        elif move == 'rise':
                            hh += amount * dd * f
                        return (base[0] - math.sin(aa) * dd, base[1] + hh, base[2] + math.cos(aa) * dd)
                    bad = None
                    n = max(2, int((t1 - t0) / 0.25))
                    for i in range(n + 1):
                        f = i / n
                        e = 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2
                        t = t0 + (t1 - t0) * f
                        why = self.lens_clear(lens(t, e), tgt(t), t, ignore=ig)
                        if why:
                            bad = why
                            break
                    if bad is None:
                        self._emit_frame(t0, t1, lens, tgt, subject, fov, cut, track, look_offset)
                        return None
                    if best is None:
                        best = (bad, lens)
        # nothing clean: take the preferred lens anyway and report it
        self._emit_frame(t0, t1, best[1], tgt, subject, fov, cut, track, look_offset)
        issue = '%s %.1f-%.1f: no clean lens (%s)' % (name or 'shot', t0, t1, best[0])
        self.cam_issues.append(issue)
        return issue

    def _emit_frame(self, t0, t1, lens, tgt, subject, fov, cut, track, look_offset):
        look = subject.target if isinstance(subject, Actor) and look_offset == (0, 0, 0) else None
        def key(t, f, first):
            pos = lens(t, f)
            k = {'t': T(t), 'pos': [r2(v) for v in pos],
                 'look_at': look if look else [r2(v) for v in tgt(t)], 'fov': fov}
            if first and cut and t0 > 0:
                k['cut'] = True
            return k
        if not track:
            self.cam.append(key(t0, 0.0, True))
            self.cam.append(key(t1, 1.0, False))
            return
        n = max(2, int(math.ceil((t1 - t0) / 1.0)))
        for i in range(n + 1):
            f = i / n
            k = key(t0 + (t1 - t0) * f, f, i == 0)
            if 0 < i < n:
                k['ease'] = 'spline'
            self.cam.append(k)

    # ---- the camera, sampled the way the client eases it (close enough for clearance checks)
    def cam_at(self, t):
        """(pos, look point, fov) of the camera at time t."""
        ks = self.cam
        def look(k, t):
            la = k['look_at']
            if isinstance(la, str):
                a = next(x for x in self.actors if x.id == la.split(':', 1)[1])
                return a.eye(t)
            return tuple(la)
        times = [tval(k['t']) for k in ks]
        if t <= times[0]:
            return tuple(ks[0]['pos']), look(ks[0], t), ks[0]['fov']
        for i in range(1, len(ks)):
            t0, t1 = times[i - 1], times[i]
            if t <= t1:
                k0, k1 = ks[i - 1], ks[i]
                if k1.get('cut'):
                    return tuple(k0['pos']), look(k0, t), k0['fov']
                f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                e = k1.get('ease', 'in_out')
                if e == 'in_out':
                    f = 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2
                elif e == 'in':
                    f = f ** 3
                elif e == 'out':
                    f = 1 - (1 - f) ** 3
                pos = tuple(k0['pos'][c] + (k1['pos'][c] - k0['pos'][c]) * f for c in range(3))
                la, lb = look(k0, t), look(k1, t)
                return pos, tuple(la[c] + (lb[c] - la[c]) * f for c in range(3)), k0['fov'] + (k1['fov'] - k0['fov']) * f
        return tuple(ks[-1]['pos']), look(ks[-1], t), ks[-1]['fov']

    def camera_problems(self, s, step=0.25):
        """What a viewer would see go wrong: the lens in or against a block, a wall between the lens and what it
        looks at, someone walking through the lens, the lens inside a Protego bubble."""
        out = []
        protego = []
        for a in self.actors:
            for k in a.actions:
                if k['do'] == 'cast' and k.get('spell') == 'protego':
                    protego.append((a, tval(k['t'])))
        flagged = set()
        n = int(self.L / step)
        for i in range(n + 1):
            t = i * step
            pos, tgt, fov = self.cam_at(t)
            shot = max(k_t for k_t in [tval(k['t']) for k in s['camera'] if k.get('cut') or tval(k['t']) == 0.0] if k_t <= t + 1e-6)
            key = None
            for dx, dy, dz in ((0, 0, 0), (0.3, 0, 0), (-0.3, 0, 0), (0, 0.3, 0), (0, -0.3, 0), (0, 0, 0.3), (0, 0, -0.3)):
                if BG.solid(pos[0] + dx, pos[1] + dy, pos[2] + dz):
                    key = ('block', shot)
                    msg = 'lens against %s at %.2f %s' % (BG.block(pos[0] + dx, pos[1] + dy, pos[2] + dz), t,
                                                         [r2(c) for c in pos])
                    break
            if key is None:
                d = math.dist(pos, tgt)
                m = max(2, int(d / 0.25))
                for j in range(1, m):
                    f = j / m
                    q = [pos[c] + (tgt[c] - pos[c]) * f for c in range(3)]
                    if math.dist(q, tgt) < 1.2:
                        break
                    if not sees_through(*q):
                        key = ('wall', shot)
                        msg = 'view blocked by %s at %.2f (%.1f of %.1f to target)' % (
                            BG.block(*q), t, math.dist(pos, q), d)
                        break
            if key is None:
                for a in self.actors:
                    if a.d['type'] == 'hogwartsmagic:memory_block' or not a.shown(t) or a.in_smoke(t):
                        continue
                    p = a.pos_at(t)
                    h, r = BODY.get(a.d['type'], (1.8, 0.45))
                    h, r = h * a.scale, r * a.scale
                    cy = min(max(pos[1], p[1]), p[1] + h)
                    if math.dist(pos, (p[0], cy, p[2])) < r + 0.25:
                        key = ('actor', shot, a.id)
                        msg = '%s in the lens at %.2f' % (a.id, t)
                        break
                for a, tp in protego:
                    if tp - 0.1 <= t <= tp + 2.0 and math.dist(pos, a.chest(t)) < 2.4:
                        key = ('protego', shot, a.id)
                        msg = 'lens inside %s Protego at %.2f' % (a.id, t)
                        break
            if key and key not in flagged:
                flagged.add(key)
                out.append(msg)
        return out

    def write(self, s, width=400):
        txt = write_scene(s, self.id, width=width, tight=True)
        return txt


class Actor:
    def __init__(self, film, aid, typ, pos, yaw, skin=None, slim=False, items=None, scale=None, life=None,
                 name=None):
        self.f = film
        self.d = {'id': aid, 'type': typ}
        if skin:
            self.d['skin'] = {'texture': skin}
            if slim:
                self.d['skin']['slim'] = True
        if name:
            self.d['name'] = name
        if items:
            self.d['items'] = items
        if scale:
            self.d['scale'] = scale
        if life is not None:
            self.d['life'] = life
        self.path = [(0.0, tuple(pos), None)]
        self.look = [(0.0, yaw, 0.0, None)]
        self.actions, self.poses, self.anims = [], [], []
        self.limbs = {}
        self.airborne = typ != 'player'
        film.actors.append(self)

    @property
    def id(self):
        return self.d['id']

    @property
    def target(self):
        return 'actor:' + self.id

    @property
    def scale(self):
        return self.d.get('scale', 1.0)

    # ---- sampling (mirrors the game's interpolation closely enough for checks)
    def pos_at(self, t):
        ks = self.path
        if t <= ks[0][0]:
            return ks[0][1]
        for (t0, a, _), (t1, b, e) in zip(ks, ks[1:]):
            if t0 <= t <= t1:
                f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                if e in (None, 'in_out'):
                    f = 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2
                elif e == 'in':
                    f = f ** 3
                elif e == 'out':
                    f = 1 - (1 - f) ** 3
                return tuple(a[c] + (b[c] - a[c]) * f for c in range(3))
        return ks[-1][1]

    def yaw_at(self, t):
        ks = self.look
        if t <= ks[0][0]:
            return ks[0][1]
        for (t0, a, _, _), (t1, b, _, _) in zip(ks, ks[1:]):
            if t0 <= t <= t1:
                return a + (b - a) * (0 if t1 == t0 else (t - t0) / (t1 - t0))
        return ks[-1][1]

    def _timeline(self, on, off):
        vis = sorted((tval(k['t']), k['do']) for k in self.actions if k['do'] in on + off)
        return vis

    def shown(self, t):
        st = True
        for tt, do in self._timeline(('appear',), ('vanish', 'die')):
            if tt <= t:
                st = do == 'appear'
        return st

    def in_smoke(self, t):
        st = False
        for tt, do in self._timeline(('smoke',), ('unsmoke',)):
            if tt <= t:
                st = do == 'smoke'
        return st

    def chest(self, t):
        p = self.pos_at(t)
        return (p[0], p[1] + 1.3 * self.scale, p[2])

    def eye(self, t):
        p = self.pos_at(t)
        return (p[0], p[1] + EYE.get(self.d['type'], 1.62) * self.scale, p[2])

    def wand(self, t):
        p = self.pos_at(t)
        return (p[0], p[1] + 1.5 * self.scale, p[2])

    # ---- building
    def key(self, t, pos, ease=None):
        t = round(t, 2)
        last = self.path[-1]
        if 0 < t <= last[0] and t > last[0] - 0.015:
            t = round(last[0] + 0.01, 2)
        assert t > last[0] or t == last[0] == 0.0, (self.id, t, last[0])
        if t == 0.0:
            self.path[0] = (0.0, tuple(pos), None)
        else:
            self.path.append((t, tuple(pos), ease))

    def hold(self, t):
        if t > self.path[-1][0] + 1e-6:
            self.key(t, self.pos_at(t))

    def move(self, t0, t1, dest, ease='in_out'):
        self.hold(t0)
        self.key(t1, dest, ease)

    def walk(self, t0, dest, speed, y=None, ease_end='out', follow=False):
        """Walk at `speed` blocks a second from where the actor is at t0 to dest over the floor (routed round
        obstacles when the straight line is blocked; no move at all if there is no way). With `follow` the walk is
        a straight line whose feet ride the ground (a sagging bridge deck, a hillside), keyed about every block.
        Returns the arrival time."""
        self.hold(t0)
        a = self.pos_at(t0)
        if follow:
            # The feet hold each column's height and step at its edge (down just past it, up just before it), so
            # they neither sink into a rise nor float over a drop; a key at each step and about every 2 blocks.
            d = dist2(a, dest)
            dur = d / speed
            n = max(2, int(math.ceil(d / 0.1)))
            pts, fy = [], a[1]
            for i in range(n + 1):
                f = i / n
                x, z = a[0] + (dest[0] - a[0]) * f, a[2] + (dest[2] - a[2]) * f
                fa = min(1.0, f + 0.2 / max(d, 1e-6))
                xa, za = a[0] + (dest[0] - a[0]) * fa, a[2] + (dest[2] - a[2]) * fa
                g = BG.ground(x, z, below=fy + 1.3)
                ga = BG.ground(xa, za, below=fy + 1.3)
                g = max(g, ga)
                fy = float(g) if g > -900 else fy
                pts.append((f, (x, fy, z)))
            keys, last_f = [pts[0]], 0.0
            for k in range(1, len(pts)):
                f, p = pts[k]
                if p[1] != pts[k - 1][1][1]:
                    if pts[k - 1][0] > keys[-1][0]:
                        keys.append(pts[k - 1])
                    keys.append(pts[k])
                    last_f = f
                elif (f - last_f) * d >= 2.0 or k == len(pts) - 1:
                    keys.append(pts[k])
                    last_f = f
            for j, (f, p) in enumerate(keys[1:], 1):
                self.key(t0 + dur * f, p, 'linear' if j < len(keys) - 1 else ease_end)
            return t0 + dur
        fy = a[1] if y is None else y
        legs = route(a, (dest[0], fy, dest[2]), fy)
        if legs is None:
            return t0
        t = t0
        here = a
        for i, w in enumerate(legs):
            d = dist2(here, w)
            if d < 0.05:
                continue
            t += d / speed
            self.key(t, (w[0], fy, w[2]), 'linear' if i < len(legs) - 1 else ease_end)
            here = w
        return t

    def face(self, t, yaw, pitch=0.0, ease=None):
        t = round(t, 2)
        prev = self.look[-1]
        if t < prev[0]:
            return
        yaw = unwrap(prev[1], yaw)
        if t == prev[0]:
            self.look[-1] = (t, yaw, pitch, ease)
        else:
            self.look.append((t, yaw, pitch, ease))

    def turn(self, t0, t1, yaw, pitch=0.0):
        prev = self.look[-1]
        if t0 > prev[0] + 1e-6:
            self.face(t0, prev[1], prev[2])
        self.face(t1, yaw, pitch)

    def face_point(self, t0, t1, point, pitch=None):
        p = self.pos_at(t1)
        pt = pitch_to(self.eye(t1), point) if pitch is None else pitch
        self.turn(t0, t1, yaw_to(p, point), max(-60.0, min(60.0, pt)))

    def act(self, t, do, **kw):
        if t > self.f.L:
            return
        a = {'t': T(t), 'do': do}
        a.update(kw)
        self.actions.append(a)

    def cast(self, t, spell, target):
        tgt = target if isinstance(target, str) or target is None else [r2(c) for c in target]
        if tgt is None:
            self.act(t, 'cast', spell=spell)
        else:
            self.act(t, 'cast', spell=spell, target=tgt)

    def pose(self, t, name, blend=None):
        if t > self.f.L:
            return
        k = {'t': T(t), 'name': name}
        if blend:
            k['blend'] = blend
        self.poses.append(k)

    def anim(self, t, name, blend=None):
        if t > self.f.L:
            return
        k = {'t': T(t), 'name': name}
        if blend is not None:
            k['blend'] = blend
        self.anims.append(k)

    def limb(self, part, kind, t, v, ease=None, mode=None):
        tr = self.limbs.setdefault(part, {})
        if mode:
            tr['mode'] = mode
        k = {'t': T(t), 'v': [r2(c) for c in v]}
        if ease:
            k['ease'] = ease
        tr.setdefault(kind, []).append(k)

    def smoke_flight(self, t0, start, land, dur):
        """Arrive as black smoke: hidden until t0, a trail from `start` down to `land` over dur, then out of the
        smoke on its feet."""
        self.key(0.0, start)
        self.act(0.0, 'vanish')
        self.act(t0 - 0.06, 'smoke')                    # already smoke when it shows
        self.act(t0 - 0.05, 'appear')
        self.key(t0, start)
        over = (land[0] + (start[0] - land[0]) * 0.12, land[1] + 17.0, land[2] + (start[2] - land[2]) * 0.12)
        self.key(t0 + dur * 0.72, over, 'linear')        # over the walls, then a plunge onto the paving
        self.key(t0 + dur, land, 'in')
        self.act(t0 + dur, 'unsmoke')
        self.face(0.0, yaw_to(start, land))
        self.face(t0 + dur, yaw_to(start, land))
        self.airborne_until = t0 + dur
        return t0 + dur

    def emit(self):
        d = dict(self.d)
        d['path'] = []
        for t, p, e in self.path:
            k = {'t': T(t), 'pos': [r2(c) for c in p]}
            if e:
                k['ease'] = e
            d['path'].append(k)
        d['look'] = []
        for t, yw, pt, e in self.look:
            k = {'t': T(t), 'yaw': round(yw + 0.0, 1), 'pitch': round(pt + 0.0, 1)}
            if e:
                k['ease'] = e
            d['look'].append(k)
        for key, lst in (('poses', self.poses), ('anims', self.anims), ('actions', self.actions)):
            if lst:
                d[key] = sorted(lst, key=lambda k: tval(k['t']))
        if self.limbs:
            d['limbs'] = self.limbs
        return d


# ---------------------------------------------------------------- the battle simulator
# No bombarda in the melee: its beam trails explosion clouds and bursts into a huge white emitter, and a crowd
# casting it buries the frame in white (it stays for scripted blasts)
GOOD_SPELLS = ['stupefy', 'expelliarmus', 'petrificus_totalus', 'depulso', 'confringo', 'incarcerous', 'stupefy',
               'glacius', 'expelliarmus', 'depulso']
BAD_SPELLS = ['avada_kedavra', 'crucio', 'sectumsempra', 'dolohov_curse', 'incendio', 'confringo', 'avada_kedavra',
              'stupefy', 'incendio']


class Fighter:
    def __init__(self, actor, side, enter, hp, speed, aggression):
        self.a, self.side, self.enter, self.hp, self.speed, self.aggr = actor, side, enter, hp, speed, aggression
        self.alive = True
        self.busy = enter
        self.target = None
        self.dest = None
        self.hits = 0
        self.down_until = -1.0


class Melee:
    """A running fight in a walkable area. Fighters join at their `enter` time (already standing on the floor there)
    and fight until t_end or until they fall. Everything is decided by the seeded rng, so a rerun writes the same."""

    def __init__(self, film, area, rng, y=0.0, t_end=None):
        self.f, self.area, self.rng, self.y = film, area, rng, y
        self.t_end = t_end or film.L
        self.fighters = []
        self.zones = []                    # (x, z, r, weight): where the fight gathers
        self.miss, self.block = 0.28, 0.35   # how often a curse goes wide, and how often a hit is shielded

    def add(self, actor, side, enter, hp=2, speed=3.6, aggression=0.5):
        f = Fighter(actor, side, enter, hp, speed, aggression)
        self.fighters.append(f)
        return f

    def inside(self, x, z):
        x0, x1, z0, z1 = self.area
        return x0 <= x <= x1 and z0 <= z <= z1 and walkable(x, z, self.y)

    def _crowded(self, me, p, t, r=1.4):
        for o in self.fighters:
            if o is me or not o.alive or t < o.enter:
                continue
            q = o.a.pos_at(t)
            if dist2(p, q) < r:
                return True
            if o.dest and o.dest[1] > t and dist2(p, o.dest[0]) < r:
                return True
        return False

    def _point_near(self, me, around, rmin, rmax, t, tries=14):
        for _ in range(tries):
            ang = self.rng.uni(0, 2 * math.pi)
            rr = self.rng.uni(rmin, rmax)
            p = (around[0] + math.cos(ang) * rr, self.y, around[2] + math.sin(ang) * rr)
            if self.inside(p[0], p[2]) and not self._crowded(me, p, t) and \
                    line_walkable(me.a.pos_at(t), p, self.y):
                return p
        return None

    def run(self, step=0.25):
        rng = self.rng
        t = min(f.enter for f in self.fighters)
        while t < self.t_end - 0.5:
            for me in self.fighters:
                if not me.alive or t < me.enter or t < me.busy or t < me.down_until:
                    continue
                pos = me.a.pos_at(t)
                foes = [o for o in self.fighters if o.side != me.side and o.alive and o.enter <= t and
                        not o.a.in_smoke(t)]
                if not foes:
                    continue
                # keep a target a while, but switch to someone who is very close, or when it falls
                if me.target is None or not me.target.alive or rng.chance(0.08):
                    def score(o):
                        q = o.a.pos_at(t)
                        attackers = sum(1 for x in self.fighters if x.target is o and x.alive)
                        return dist2(pos, q) + 3.0 * attackers + rng.uni(0, 4)
                    me.target = min(foes, key=score)
                tq = me.target.a.pos_at(t)
                d = dist2(pos, tq)
                r = rng()
                # move: close in when far, give ground when pressed, strafe otherwise
                want_move = d > 11 or d < 2.5 or r < 0.22 + 0.15 * (1 - me.aggr)
                if want_move:
                    if d > 11:
                        p = self._point_near(me, tq, 5.0, 8.0, t)
                        spd = me.speed * rng.uni(1.1, 1.5)
                    elif d < 2.5:
                        away = (pos[0] + (pos[0] - tq[0]) * 1.5, self.y, pos[2] + (pos[2] - tq[2]) * 1.5)
                        p = self._point_near(me, away, 0.5, 2.5, t)
                        spd = me.speed * 1.3
                    else:
                        p = self._point_near(me, pos, 1.5, 4.0, t)
                        spd = me.speed * rng.uni(0.7, 1.1)
                    if p:
                        dur = dist2(pos, p) / spd
                        me.a.move(t, t + dur, p, 'in_out' if dur > 0.6 else 'linear')
                        me.a.face(t, yaw_to(pos, p) if d > 11 else yaw_to(p, tq), 0)
                        me.a.face(t + dur, yaw_to(p, tq), 0)
                        me.dest = (p, t + dur)
                        me.busy = t + dur + rng.uni(0.0, 0.4)
                        continue
                # cast
                if d > 22:
                    me.busy = t + 0.5
                    continue
                spells = GOOD_SPELLS if me.side == 0 else BAD_SPELLS
                spell = rng.pick(spells)
                tc = t + 0.3
                tgt = me.target
                aim = tgt.a.chest(tc)
                me.a.face_point(t, tc - 0.05, aim)
                miss = rng.chance(self.miss)
                if miss:
                    aim = (aim[0] + rng.uni(-2.5, 2.5), aim[1] + rng.uni(-0.5, 2.0), aim[2] + rng.uni(-2.5, 2.5))
                    me.a.cast(tc, spell, aim)
                else:
                    me.a.cast(tc, spell, tgt.a.target)
                land = tc + math.dist(me.a.wand(tc), aim) / BEAM_SPEED
                if not miss:
                    blocked = rng.chance(self.block) and tgt.busy <= land - 0.2
                    if blocked:
                        tgt.a.act(max(tgt.enter, land - 0.2), 'cast', spell='protego')
                        tgt.busy = max(tgt.busy, land + 0.3)
                    else:
                        tgt.a.act(land + 0.03, 'hurt')
                        tgt.hits += 1
                        tgt.busy = max(tgt.busy, land + 0.6)
                        if spell in ('bombarda', 'confringo') and rng.chance(0.3):
                            self.f.blast(land, tgt.a.chest(land), rng=rng)
                        if tgt.hits >= tgt.hp:
                            self.fall(tgt, land + 0.1)
                elif rng.chance(0.12):
                    self.f.part(land, 'minecraft:explosion', aim, 1, (0.3, 0.3, 0.3))
                me.busy = tc + rng.uni(0.6, 1.4) * (1.4 - me.aggr)
            t = round(t + step, 2)

    def fall(self, f, t):
        f.alive = False
        f.a.act(t, 'die')                  # the die action makes its own small poof; no second cloud over it
