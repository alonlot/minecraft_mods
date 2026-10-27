"""Background life for the Battle of Hogwarts scenes: nobody stands frozen while the story is somewhere else.

A scene's own beats are written first; then `liven(film, rng, mood)` finds every stretch where an actor does nothing
you could see from a few blocks away (no move, no turn, no action, no clip) and fills it with business that fits
the moment: a fighter trades spells, throws up a shield, shifts ground, ducks a blast; a watcher glances about,
looks to what matters, mutters to a neighbour, fidgets; a giant roars; a knight hacks; a spider skitters; the
wounded stir. Every filled stretch ends where it began (same spot, same facing, same pose and clip), so the scene's
own keys after it carry on exactly as written.

`mood(actor)` returns None (leave the actor alone) or a dict:
  acts   {name: weight}: glance, look, step, cast, volley, protego, duck, swing, talk, nervous, point, raise,
         roar, swipe, stomp, skitter, stir
  foes   f(t) -> actors to shoot at (cast/volley; they are aimed near, not into, so nobody has to react)
  look   f(t) -> the point that matters (look), or None
  span   (t0, t1): only fill inside this window
  pace   seconds of stillness between bits of business (min, max)
"""
import math

import battle_ground as BG
from battle_kit import tval, yaw_to, pitch_to, unwrap, dist2, line_walkable, GOOD_SPELLS, BAD_SPELLS, BEAM_SPEED

BUSY_ACTIONS = {'cast': (0.4, 0.6), 'hurt': (0.0, 0.6), 'swing': (0.5, 1.0), 'sneak': (0.0, 0.3),
                'stand': (0.0, 0.3), 'use_item': (0.0, 1.6), 'appear': (0.0, 0.3), 'vanish': (0.0, 0.3)}
CREATURE_IDLE = {'IDLE', 'WALK'}
LOW_POSES = ('kneel', 'flat', 'cower', 'bent_over_basin')
ARC = 85.0                     # the widest an actor turns away from the facing the scene gave it


def _ins_path(a, t, pos, ease=None):
    t = round(t, 2)
    if any(abs(k[0] - t) < 0.02 for k in a.path):
        return
    a.path.append((t, tuple(pos), ease))
    a.path.sort(key=lambda k: k[0])


def _ins_look(a, t, yaw, pitch, ease=None):
    t = round(t, 2)
    if any(abs(k[0] - t) < 0.02 for k in a.look):
        return
    a.look.append((t, yaw, pitch, ease))
    a.look.sort(key=lambda k: k[0])


def _look_at(a, t):
    ks = a.look
    for (t0, y0, p0, _), (t1, y1, p1, _) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return y0 + (y1 - y0) * f, p0 + (p1 - p0) * f
    k = ks[-1] if t >= ks[-1][0] else ks[0]
    return k[1], k[2]


class _Look:
    """An actor's look track as it was before any filling (for _look_at)."""
    def __init__(self, look):
        self.look = look


def _cue_at(lst, t):
    """The name of the last pose/anim cue at or before t (None if there is none)."""
    best = None
    for k in lst:
        if tval(k['t']) <= t + 1e-6 and (best is None or tval(k['t']) >= tval(best['t'])):
            best = k
    return best['name'] if best else None


def busy_spans(a, L):
    """Spans when the actor visibly does something already (or must not be touched: smoke, hidden, stone, dying)."""
    sp = []
    for (t0, p0, _), (t1, p1, _) in zip(a.path, a.path[1:]):
        if max(abs(p0[c] - p1[c]) for c in range(3)) > 0.02:
            sp.append((t0, t1))
    for (t0, y0, q0, _), (t1, y1, q1, _) in zip(a.look, a.look[1:]):
        # a slow drift of the gaze over many seconds is no business at a distance; a real turn is
        ch = max(abs(y0 - y1), abs(q0 - q1))
        if ch > 2 and (t1 - t0 < 2.0 or ch / (t1 - t0) > 12.0):
            sp.append((t0, t1))
    hidden_from = None
    for k in sorted(a.actions, key=lambda k: tval(k['t'])):
        t, do = tval(k['t']), k['do']
        if do in BUSY_ACTIONS:
            b, e = BUSY_ACTIONS[do]
            sp.append((t - b, t + e))
        if do in ('die',):
            sp.append((t - 0.2, L))
        if do in ('smoke', 'vanish', 'stone') and hidden_from is None:
            hidden_from = t
        if do in ('unsmoke', 'appear', 'unstone') and hidden_from is not None:
            sp.append((hidden_from, t + 0.4))
            hidden_from = None
    if hidden_from is not None:
        sp.append((hidden_from, L))
    for k in a.poses:
        t = tval(k['t'])
        sp.append((t - 0.2, t + 0.6))
    anims = sorted(a.anims, key=lambda k: tval(k['t']))
    for i, k in enumerate(anims):
        t = tval(k['t'])
        nxt = tval(anims[i + 1]['t']) if i + 1 < len(anims) else L
        name = k['name'].upper()
        if a.d['type'] == 'player':
            if name != 'IDLE':
                sp.append((t, nxt))          # a clip is life already
        elif name not in CREATURE_IDLE:
            sp.append((t, t + 2.2))
    if a.d['type'] == 'hogwartsmagic:living_statue':
        first = min((tval(k['t']) for k in a.anims), default=L)
        sp.append((0.0, first + 2.6))         # dormant until woken, then the 50-tick rise
        for k in a.anims:
            if k['name'].upper() == 'CRUMBLE':
                sp.append((tval(k['t']), L))
    for tr in a.limbs.values():
        for kk in ('rot', 'offset'):
            ts = [tval(k['t']) for k in tr.get(kk, [])]
            for t0, t1 in zip(ts, ts[1:]):
                sp.append((t0, t1))
    au = getattr(a, 'airborne_until', None)
    if au:
        sp.append((0.0, au + 0.4))
    return sp


def free_gaps(a, L, span, min_gap):
    sp = sorted(busy_spans(a, L))
    gaps, t = [], span[0]
    for b, e in sp:
        if b > t:
            gaps.append((t, min(b, span[1])))
        t = max(t, e)
    if t < span[1]:
        gaps.append((t, span[1]))
    return [(g0, g1) for g0, g1 in gaps if g1 - g0 >= min_gap and a.shown((g0 + g1) / 2)]


class _Filler:
    def __init__(self, film, a, m, rng, g0, g1):
        self.f, self.a, self.m, self.rng = film, a, m, rng
        self.g0, self.g1 = g0, g1
        self.home = a.pos_at(g0)
        self.yaw0, self.pitch0 = _look_at(a, g0)
        self.yaw1, self.pitch1 = _look_at(a, g1)       # where the scene's own (slow) look is by the end
        self.orig = _Look(list(a.look))
        self.yaw, self.pitch = self.yaw0, self.pitch0
        self.pose0 = _cue_at(a.poses, g0)
        self.clip0 = _cue_at(a.anims, g0)
        self.player = a.d['type'] == 'player'
        self.down = self.pose0 in LOW_POSES           # kneeling, lying, cowering: no stepping about

    # ---- primitives
    def turn(self, t0, t1, yaw, pitch):
        # never more than ARC either side of the scene's own facing: business, not a spin (and no key-to-key
        # change past half a turn)
        base = _look_at(self.orig, t1)[0]
        yaw = max(base - ARC, min(base + ARC, unwrap(base, yaw)))
        yaw = max(self.yaw - 170.0, min(self.yaw + 170.0, yaw))
        pitch = max(-55.0, min(55.0, pitch))
        _ins_look(self.a, t0, self.yaw, self.pitch)
        _ins_look(self.a, t1, yaw, pitch)
        self.yaw, self.pitch = yaw, pitch

    def face(self, t0, t1, point):
        eye = self.a.eye(t1)
        self.turn(t0, t1, yaw_to(self.home, point), pitch_to(eye, point))

    def near_lens(self, p, t0, t1, r=4.5):
        if not self.f.cam:
            return False
        n = max(2, int((t1 - t0) / 0.25))
        for i in range(n + 1):
            c = self.f.cam_at(t0 + (t1 - t0) * i / n)[0]
            if math.dist(c, (p[0], p[1] + 1.0, p[2])) < r * max(1.0, self.a.scale):
                return True
        return False

    def others_near(self, p, t, r):
        for o in self.f.actors:
            if o is self.a or o.d['type'] == 'hogwartsmagic:memory_block' or not o.shown(t):
                continue
            if dist2(o.pos_at(t), p) < r and abs(o.pos_at(t)[1] - p[1]) < 3:
                return True
        return False

    # ---- business; each returns the time it is done, or None when it does not fit
    def b_glance(self, t, room):
        if room < 1.6:
            return None
        d = self.rng.uni(25, 70) * self.rng.pick((-1, 1))
        hold = self.rng.uni(0.6, min(2.2, room - 1.0))
        self.turn(t, t + self.rng.uni(0.25, 0.5), self.yaw0 + d, self.pitch0 + self.rng.uni(-14, 8))
        t2 = t + 0.5 + hold
        self.turn(t2, t2 + 0.45, self.yaw0 + self.rng.uni(-10, 10), self.pitch0)
        return t2 + 0.45

    def b_look(self, t, room):
        f = self.m.get('look')
        p = f(t) if f else None
        if p is None or room < 2.0:
            return self.b_glance(t, room)
        self.face(t, t + self.rng.uni(0.3, 0.6), (p[0] + self.rng.uni(-3, 3), p[1] + self.rng.uni(-1, 2),
                                                   p[2] + self.rng.uni(-3, 3)))
        return t + self.rng.uni(1.2, min(3.5, room))

    def b_step(self, t, room):
        if room < 2.4 or self.down:
            return None
        h = self.home
        sc = self.a.scale
        for _ in range(8):
            ang = self.rng.uni(0, 2 * math.pi)
            r = self.rng.uni(0.5, 1.5) * max(1.0, sc)
            q = (h[0] + math.cos(ang) * r, h[1], h[2] + math.sin(ang) * r)
            if not line_walkable(h, q, h[1], margin=0.25 * max(1.0, sc)):
                continue
            if self.others_near(q, t + 0.6, 0.9 * max(1.0, sc)) or self.near_lens(q, t, t + 4.5):
                continue
            out = r / self.rng.uni(1.2, 2.2)
            dwell = self.rng.uni(0.4, max(0.5, min(2.0, room - 2 * out - 0.3)))
            if 2 * out + dwell > room:
                continue
            _ins_path(self.a, t, h)
            _ins_path(self.a, t + out, q)
            _ins_path(self.a, t + out + dwell, q)
            _ins_path(self.a, t + 2 * out + dwell, h)
            if self.rng.chance(0.5):
                self.turn(t, t + 0.4, yaw_to(h, q), self.pitch0)
                self.turn(t + out, t + out + 0.35, self.yaw0 + self.rng.uni(-30, 30), self.pitch0)
            return t + 2 * out + dwell
        return None

    def _foe_point(self, t):
        foes = [o for o in (self.m['foes'](t) if self.m.get('foes') else []) if o.shown(t)]
        here = self.home
        near = [o for o in foes if 3.0 < dist2(o.pos_at(t), here) < 34]
        if near:
            o = self.rng.pick(sorted(near, key=lambda o: dist2(o.pos_at(t), here))[:6])
            c = o.chest(t) if not o.in_smoke(t) else o.pos_at(t)
            miss = 0.0 if o.in_smoke(t) else self.rng.uni(0.8, 2.6)
            ang = self.rng.uni(0, 2 * math.pi)
            return (c[0] + math.cos(ang) * miss, c[1] + self.rng.uni(-0.6, 1.8), c[2] + math.sin(ang) * miss)
        return None

    def b_cast(self, t, room, n=1):
        if room < 1.2 or not self.player:
            return None
        spells = self.m.get('spells') or (BAD_SPELLS if self.m.get('side') == 1 else GOOD_SPELLS)
        tt = t
        p = self._foe_point(t)
        base = _look_at(self.orig, t)[0]
        if p is None or abs(unwrap(base, yaw_to(self.home, p)) - base) > ARC:
            return None
        self.face(t, t + 0.25, p)                     # one aim for the volley, the curses sprayed round it
        for k in range(n):
            if tt + 0.9 > t + room:
                break
            if k:
                p = (p[0] + self.rng.uni(-1.5, 1.5), p[1] + self.rng.uni(-0.4, 0.8), p[2] + self.rng.uni(-1.5, 1.5))
            # confringo bursts into a white cloud where it lands: not in the camera's face
            near = self.near_lens(p, tt, tt + 1.0, r=9.0)
            pool = [x for x in spells if x != 'confringo'] if near else spells
            self.a.act(tt + 0.32, 'cast', spell=self.rng.pick(pool), target=[round(c, 1) for c in p])
            land = tt + 0.32 + math.dist(self.a.wand(tt), p) / BEAM_SPEED
            if self.rng.chance(0.06) and not self.near_lens(p, land, land + 0.6, r=8.0):
                self.f.part(land, 'minecraft:explosion', p, 1, (0.2, 0.2, 0.2))
            tt += self.rng.uni(0.7, 1.15)
        return tt if tt > t else None

    def b_volley(self, t, room):
        return self.b_cast(t, room, n=self.rng.pick((2, 3, 3, 4)))

    def b_protego(self, t, room):
        if room < 1.0 or not self.player or self.near_lens(self.home, t, t + 2.4):
            return None
        self.a.act(t + 0.1, 'cast', spell='protego')
        return t + 1.4

    def b_duck(self, t, room):
        if room < 1.4 or not self.player or self.down:
            return None
        self.a.act(t, 'sneak')
        self.turn(t, t + 0.2, self.yaw + self.rng.uni(-25, 25), self.pitch0 + 20)
        up = t + self.rng.uni(0.5, 1.1)
        self.a.act(up, 'stand')
        return up + 0.3

    def _clip(self, t, room, name, lo, hi):
        if not self.player or self.clip0 not in (None, 'IDLE') or room < lo + 0.4:
            return None
        e = t + self.rng.uni(lo, min(hi, room - 0.3))
        self.a.anim(t, name, 8)
        self.a.anim(e, 'IDLE', 10)
        return e + 0.3

    def b_talk(self, t, room):
        mates = [o for o in self.f.actors if o is not self.a and o.d['type'] == 'player' and o.shown(t)
                 and 0.8 < dist2(o.pos_at(t), self.home) < 3.2]
        if mates:
            o = self.rng.pick(mates)
            self.face(t, t + 0.4, o.eye(t))
        return self._clip(t + 0.2, room - 0.2, 'TALK', 1.6, 4.0)

    def b_nervous(self, t, room):
        return self._clip(t, room, 'NERVOUS', 2.0, 4.5)

    def _pose(self, t, room, name, lo, hi):
        if not self.player or self.down or room < lo + 0.6:
            return None
        e = t + self.rng.uni(lo, min(hi, room - 0.4))
        self.a.pose(t, name, 6)
        self.a.pose(e, self.pose0 or 'standing', 8)
        return e + 0.4

    def b_point(self, t, room):
        f = self.m.get('look')
        p = f(t) if f else None
        if p is not None:
            self.face(t, t + 0.4, p)
        return self._pose(t + 0.2, room - 0.2, 'wand_point', 1.0, 2.6)

    def b_raise(self, t, room):
        return self._pose(t, room, 'wand_raised', 0.8, 2.0)

    def b_swing(self, t, room):
        if room < 1.6:
            return None
        p = self._foe_point(t)
        if p is not None and self.a.d['type'] == 'hogwartsmagic:living_statue':
            self.turn(t, t + 0.4, yaw_to(self.home, p), 0)
        self.a.act(t + 0.4, 'swing')
        return t + (2.4 if self.a.d['type'] == 'hogwartsmagic:living_statue' else 1.0)

    def _creature(self, t, room, name, dur):
        if room < dur + 0.3:
            return None
        self.a.anim(t, name)
        self.a.anim(t + dur, 'IDLE')
        return t + dur + 0.2

    def b_roar(self, t, room):
        if room > 3.0 and self.f.L > t:
            self.f.snd(t + 0.3, 'hogwartsmagic:giant.roar', at=self.home, volume=2.0, pitch=self.rng.uni(0.8, 1.0))
        return self._creature(t, room, 'ROAR', 2.6)

    def b_swipe(self, t, room):
        return self._creature(t, room, self.rng.pick(('SWIPE', 'KICK')), 2.0)

    def b_stomp(self, t, room):
        return self.b_step(t, room)

    def b_skitter(self, t, room):
        # a spider darts a little way, freezes, darts back: quick and twitchy
        if room < 1.4:
            return None
        h = self.home
        for _ in range(8):
            ang = self.rng.uni(0, 2 * math.pi)
            r = self.rng.uni(0.8, 2.4)
            q = (h[0] + math.cos(ang) * r, h[1], h[2] + math.sin(ang) * r)
            if not line_walkable(h, q, h[1], margin=0.4) or self.near_lens(q, t, t + 3.0, r=6.0):
                continue
            out = r / self.rng.uni(4.0, 7.0)
            dwell = self.rng.uni(0.2, max(0.25, min(1.2, room - 2 * out - 0.2)))
            if 2 * out + dwell > room:
                continue
            self.turn(t, t + 0.12, yaw_to(h, q), 0)
            _ins_path(self.a, t, h)
            _ins_path(self.a, t + out, q, 'linear')
            _ins_path(self.a, t + out + dwell, q)
            _ins_path(self.a, t + 2 * out + dwell, h, 'linear')
            self.turn(t + out + dwell - 0.1, t + out + dwell, yaw_to(q, h) + 180, 0)
            return t + 2 * out + dwell
        return None

    def b_stir(self, t, room):
        # the wounded: the head rolls, a hand lifts and falls (keys added into the lying limb tracks)
        if room < 2.5:
            return None
        part = self.rng.pick(('head', 'right_arm', 'left_arm', 'head'))
        tr = self.a.limbs.get(part)
        if not tr or 'rot' not in tr:
            return None
        base = None
        for k in tr['rot']:
            if tval(k['t']) <= t:
                base = k['v']
        if base is None:
            return None
        amt = (0, self.rng.uni(-28, 28), self.rng.uni(-10, 10)) if part == 'head' else \
            (self.rng.uni(-35, -15), 0, self.rng.uni(-10, 10))
        up = t + self.rng.uni(0.8, 1.4)
        hold = up + self.rng.uni(0.5, min(1.8, room - 2.0))
        down = hold + self.rng.uni(0.8, 1.2)
        from battle_kit import T, r2
        for tt, v in ((t, base), (up, [base[i] + amt[i] for i in range(3)]), (hold, [base[i] + amt[i] for i in range(3)]),
                      (down, base)):
            tr['rot'].append({'t': T(tt), 'v': [r2(c) for c in v], 'ease': 'in_out'})
        tr['rot'].sort(key=lambda k: tval(k['t']))
        return down

    def run(self):
        acts = self.m['acts']
        names = list(acts)
        weights = [acts[n] for n in names]
        lo, hi = self.m.get('pace', (0.4, 1.6))
        t = self.g0 + self.rng.uni(0.1, hi)
        end = self.g1 - 0.6
        last = self.g0
        while t < end - 0.8:
            r, acc, pick = self.rng() * sum(weights), 0.0, names[-1]
            for n, w in zip(names, weights):
                acc += w
                if r <= acc:
                    pick = n
                    break
            done = getattr(self, 'b_' + pick)(t, end - t)
            if done is not None:
                last = done
            t = (done if done is not None else t + 0.3) + self.rng.uni(lo, hi)
        # back to the facing the scene left it in, by the end of the stretch
        if abs(self.yaw - self.yaw1) > 1 or abs(self.pitch - self.pitch1) > 1:
            _ins_look(self.a, min(last + 0.05, self.g1 - 0.5), self.yaw, self.pitch)
            _ins_look(self.a, self.g1 - 0.05, self.yaw1, self.pitch1)


def liven(film, rng, mood, min_gap=1.8):
    """Fill every idle stretch of every actor the mood covers. Returns how many stretches were filled."""
    n = 0
    for a in list(film.actors):
        m = mood(a)
        if not m:
            continue
        span = m.get('span', (0.0, film.L))
        for g0, g1 in free_gaps(a, film.L, span, min_gap):
            _Filler(film, a, m, rng, g0, g1).run()
            n += 1
    return n
