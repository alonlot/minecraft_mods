"""Generates the Battle of Hogwarts memory: data/hogwartsmagic/memories/battle_of_hogwarts.json (the night: the
shield, the siege, the charge, the battle and the last duel) and battle_of_hogwarts_dawn.json (the morning after),
chained with a white flash.

Usage (from the repo root): python -I build_scripts/pensieve_scenes/battle_of_hogwarts.py

The set is the Flying memory's castle lawn (flying_lawn.nbt, with the Mini Hogwarts on its crag to the north) seen
at night. Frame: the lawn's grass top is block y -1 (everyone on it stands at y 0); X east, Z south. The castle lies
north (z -85 and beyond), the curtain wall on the lawn's west side (x -26), and the hills close the glen to the
south: the Death Eaters gather on their slope (z ~80-88, y ~20-30) facing north over the lawn.

The shield is the shield_dome effect (an ellipsoid over the castle whose south wall stands at the hills' foot,
z ~68-72); every Death Eater spell that strikes it is a shield_hit at the point it lands, timed by the beam's flight
(1.55 blocks a tick). The dome's duration runs out just after Voldemort's last strike, so it burns away from there.
All spoken lines are subtitles, original paraphrase; only the incantations are canonical.
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from scene_common import write_scene, template_grid  # noqa: E402

R = 200
FOG_FAR = 260
TEMPLATE_AT = (-96, -40, -170)
SET = {'templates': [{'id': 'hogwartsmagic:flying_lawn', 'at': list(TEMPLATE_AT)}],
       'schematics': [{'id': 'mini_hogwarts', 'at': [-72, -6, -180], 'rotation': 90}]}

WANDS = ['hogwartsmagic:dragon_core_wand', 'hogwartsmagic:phoenix_core_wand', 'hogwartsmagic:unicorn_core_wand']
ELDER = 'hogwartsmagic:elder_wand'
HARRY_WAND = 'hogwartsmagic:harry_potter_wand'
BEAM_SPEED = 1.55 * 20          # blocks a second

# ---------------------------------------------------------------- the ground (from the template)
NOT_GROUND = ('air', 'leaves', '_log', 'short_grass', 'tall_grass', 'fern', 'allium', 'dandelion', 'daisy', 'bush',
              'minecraft:snow', 'vine', 'flower', 'poppy', 'tulip', 'bluet', 'cornflower', 'lily', 'sweet_berry',
              'carpet', 'button', 'torch', 'lantern', 'sapling', 'azalea', 'moss_carpet', 'pink_petals', 'wood')
# plants and the like: no collision, so neither a camera nor a walker is stopped by them
PASSABLE = ('short_grass', 'tall_grass', 'fern', 'allium', 'dandelion', 'daisy', 'bush', 'minecraft:light',
            'minecraft:snow', 'vine', 'flower', 'poppy', 'tulip', 'bluet', 'cornflower', 'lily', 'carpet',
            'sapling', 'pink_petals', 'sweet_berry', 'torch', 'button')
_GRID = template_grid('flying_lawn')
TOP, SOLID, LEAVES = {}, set(), set()
for (_x, _y, _z), _b in _GRID.items():
    if _b.startswith('minecraft:air'):
        continue
    p = (_x + TEMPLATE_AT[0], _y + TEMPLATE_AT[1], _z + TEMPLATE_AT[2])
    _id = _b.split('{')[0]
    if not any(k in _id for k in PASSABLE):
        SOLID.add(p)
    if 'leaves' in _id:
        LEAVES.add(p)
    if _id == 'minecraft:light' or any(k in _id for k in NOT_GROUND):
        continue
    if p[1] > TOP.get((p[0], p[2]), -999):
        TOP[(p[0], p[2])] = p[1]
del _GRID


def ground(x, z):
    """Feet y on the ground at (x, z): the top of the highest ground block of that column, plus one."""
    return TOP.get((math.floor(x), math.floor(z)), -999) + 1


def clear(x, y, z, wade=False):
    """Whether a person fits at feet (x, y, z): no block in the two cells above the feet (wade: knee-high scrub,
    a leaf block at the feet, is walked through)."""
    c = (math.floor(x), math.floor(y), math.floor(z))
    feet = (c[0], c[1], c[2])
    return (feet not in SOLID or (wade and feet in LEAVES)) and (c[0], c[1] + 1, c[2]) not in SOLID


def standing(x, z, reach=4):
    """The nearest standable spot to (x, z) (searching x and z within +-reach): (x, y, z) at the column's middle."""
    for dx, dz in sorted(((i, j) for i in range(-reach, reach + 1) for j in range(-reach, reach + 1)),
                         key=lambda d: d[0] * d[0] + d[1] * d[1]):
        if True:
            gx, gz = math.floor(x) + dx + 0.5, math.floor(z) + dz + 0.5
            y = ground(gx, gz)
            if y > -50 and clear(gx, y, gz) and all(abs(ground(gx + ox, gz + oz) - y) <= 2
                                                   for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                return (gx, float(y), gz)
    raise ValueError('nowhere to stand near %s' % ((x, z),))


def leg_clear(a, b):
    """Whether a walker can go straight from a to b over the terrain (wading scrub, no step over 2 high)."""
    dist = math.hypot(b[0] - a[0], b[2] - a[2])
    n = max(2, int(dist / 0.1))
    prev = None
    for i in range(n + 1):
        f = i / n
        for side in (-0.3, 0.3):
            dx, dz = (b[2] - a[2]) / max(dist, 1e-6) * side, -(b[0] - a[0]) / max(dist, 1e-6) * side
            x, z = a[0] + (b[0] - a[0]) * f + dx, a[2] + (b[2] - a[2]) * f + dz
            y = ground(x, z)
            if not clear(x, y + 0.05, z, wade=True):
                return False
        y = ground(a[0] + (b[0] - a[0]) * f, a[2] + (b[2] - a[2]) * f)
        if prev is not None and abs(y - prev) > 2:
            return False
        prev = y
    return True


def _cell_ok(cx, cz):
    x, z = cx + 0.5, cz + 0.5
    y = ground(x, z)
    return y > -50 and all(clear(x + dx, y + 0.05, z + dz, wade=True) for dx in (-0.3, 0.3) for dz in (-0.3, 0.3))


def route(a, b):
    """Waypoints from a to b (b last): A* over the terrain's columns (scrub waded, steps up to 2), then pulled
    straight wherever a straight leg is clear."""
    import heapq
    start, goal = (math.floor(a[0]), math.floor(a[2])), (math.floor(b[0]), math.floor(b[2]))
    if leg_clear(a, b):
        return [b]
    openq = [(0.0, start)]
    came, cost = {start: None}, {start: 0.0}
    while openq:
        _, c = heapq.heappop(openq)
        if c == goal:
            break
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (c[0] + dx, c[1] + dz)
                if n == c or abs(n[0] - start[0]) > 40 or abs(n[1] - start[1]) > 40 or not _cell_ok(*n):
                    continue
                if abs(ground(n[0] + 0.5, n[1] + 0.5) - ground(c[0] + 0.5, c[1] + 0.5)) > 2:
                    continue
                nc = cost[c] + math.hypot(dx, dz)
                if nc < cost.get(n, 1e9):
                    cost[n] = nc
                    came[n] = c
                    heapq.heappush(openq, (nc + math.hypot(goal[0] - n[0], goal[1] - n[1]), n))
    if goal not in came:
        raise ValueError('no route from %s to %s' % (a, b))
    cells = []
    c = goal
    while c is not None:
        cells.append((c[0] + 0.5, float(ground(c[0] + 0.5, c[1] + 0.5)), c[1] + 0.5))
        c = came[c]
    cells.reverse()
    cells[0], cells[-1] = a, b
    out, here = [], a
    i = 0
    while i < len(cells) - 1:
        j = len(cells) - 1
        while j > i + 1 and not leg_clear(here, cells[j]):
            j -= 1
        out.append(cells[j])
        here, i = cells[j], j
    return out


# ---------------------------------------------------------------- helpers
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


class Rng:
    def __init__(self, seed):
        self.s = seed

    def __call__(self):
        self.s = (self.s * 1103515245 + 12345) % 2147483648
        return self.s / 2147483648

    def uni(self, a, b):
        return a + (b - a) * self()


class Film:
    """One scene's events, words and camera."""

    def __init__(self, length):
        self.L = length
        self.events, self.words, self.cam, self.actors = [], [], [], []

    def ev(self, t, kind, id_, **kw):
        e = {'t': T(t), 'kind': kind, 'id': id_}
        e.update(kw)
        self.events.append(e)

    def snd(self, t, id_, at=None, volume=None, pitch=None):
        kw = {}
        if at is not None:
            kw['at'] = [r2(c) for c in at]
        if volume is not None:
            kw['volume'] = volume
        if pitch is not None:
            kw['pitch'] = r2(pitch)
        self.ev(t, 'sound', id_, **kw)

    def part(self, t, id_, at, count, spread):
        self.ev(t, 'particles', id_, at=[r2(c) for c in at], count=int(count), spread=[r2(s) for s in spread])

    def block(self, t, id_, at):
        self.ev(t, 'block', id_, at=[int(c) for c in at])

    def effect(self, t, id_, at, **kw):
        self.ev(t, 'effect', id_, at=[r2(c) for c in at], **kw)

    def music(self, t, name, volume=1.0):
        self.ev(t, 'music', 'hogwartsmagic:pensieve.music.' + name, volume=volume)

    def sfx(self, t, name, volume=1.0, **kw):
        self.ev(t, 'sfx', 'hogwartsmagic:pensieve.sfx.' + name, volume=volume, **kw)

    def say(self, t, speaker, text, dur=4.0):
        w = {'t': T(t), 'text': text, 'subtitle': True, 'duration': T(dur)}
        if speaker:
            w['speaker'] = speaker
        self.words.append(w)

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


class Actor:
    """Builds one actor's tracks in time order."""

    def __init__(self, film, aid, typ, pos, yaw, skin=None, items=None, scale=None, life=None):
        self.f = film
        self.d = {'id': aid, 'type': typ}
        if skin:
            self.d['skin'] = {'texture': skin}
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
        film.actors.append(self)

    @property
    def id(self):
        return self.d['id']

    @property
    def target(self):
        return 'actor:' + self.id

    def pos_at(self, t):
        ks = self.path
        if t <= ks[0][0]:
            return ks[0][1]
        for (t0, a, _), (t1, b, e) in zip(ks, ks[1:]):
            if t0 <= t <= t1:
                f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
                if e in (None, 'in_out'):
                    f = 4 * f ** 3 if f < 0.5 else 1 - (-2 * f + 2) ** 3 / 2
                return tuple(a[c] + (b[c] - a[c]) * f for c in range(3))
        return ks[-1][1]

    def chest(self, t):
        p = self.pos_at(t)
        return (p[0], p[1] + 1.3 * self.d.get('scale', 1), p[2])

    def wand(self, t):
        p = self.pos_at(t)
        return (p[0], p[1] + 1.5 * self.d.get('scale', 1), p[2])

    def key(self, t, pos, ease=None):
        t = round(t, 2)
        last = self.path[-1]
        if 0 < t <= last[0] and t > last[0] - 0.015:
            t = round(last[0] + 0.01, 2)           # a step key a hair after the one before (time rounding)
        assert t > last[0] or (t == last[0] == 0.0), (self.id, t, last[0])
        if t == 0.0:
            self.path[0] = (0.0, tuple(pos), None)
        else:
            self.path.append((t, tuple(pos), ease))

    def hold(self, t):
        """A path key at t where the actor already is (start of a move)."""
        if t > self.path[-1][0] + 1e-6:
            self.key(t, self.pos_at(t))

    def move(self, t0, t1, dest, ease='in_out'):
        self.hold(t0)
        self.key(t1, dest, ease)

    def walk(self, t0, dest, speed, step=None, on_ground=True):
        """Walk at `speed` blocks a second in a straight line from where the actor is at t0, its feet on the ground."""
        self.hold(t0)
        a = self.pos_at(t0)
        dist = math.hypot(dest[0] - a[0], dest[2] - a[2])
        dur = dist / speed
        if not on_ground:
            n = max(1, int(math.ceil(dist / (speed * (step or 0.4)))))
            for i in range(1, n + 1):
                f = i / n
                self.key(t0 + dur * f, tuple(a[c] + (dest[c] - a[c]) * f for c in range(3)),
                         'linear' if i < n else 'out')
            return t0 + dur
        # Over the terrain: the feet hold each column's height and step at its edge (down just past it, up just
        # before it), so they neither float over a drop nor sink into a rise. A key at every step and every block.
        fine = max(2, int(math.ceil(dist / 0.1)))
        pts = []
        for i in range(fine + 1):
            f = i / fine
            x, z = a[0] + (dest[0] - a[0]) * f, a[2] + (dest[2] - a[2]) * f
            ahead = min(1.0, f + 0.25 / max(dist, 1e-6))
            y = max(ground(x, z), ground(a[0] + (dest[0] - a[0]) * ahead, a[2] + (dest[2] - a[2]) * ahead))
            pts.append((f, (x, float(y), z)))
        keys = [pts[0]]
        last_f = 0.0
        for k in range(1, len(pts)):
            f, p = pts[k]
            if p[1] != pts[k - 1][1][1]:
                if pts[k - 1][0] > keys[-1][0]:
                    keys.append(pts[k - 1])
                keys.append(pts[k])
                last_f = f
            elif f - last_f >= 1.0 / max(dist, 1e-6) or k == len(pts) - 1:
                keys.append(pts[k])
                last_f = f
        for j, (f, p) in enumerate(keys[1:], 1):
            self.key(t0 + dur * f, p, 'linear' if j < len(keys) - 1 else 'out')
        return t0 + dur

    def face(self, t, yaw, pitch=0.0, ease=None):
        t = round(t, 2)
        prev = self.look[-1]
        assert t >= prev[0], (self.id, 'look', t, prev[0])
        yaw = unwrap(prev[1], yaw)
        if t == prev[0]:
            self.look[-1] = (t, yaw, pitch, ease)
        else:
            self.look.append((t, yaw, pitch, ease))

    def turn(self, t0, t1, yaw, pitch=0.0):
        """Hold the current facing until t0, then turn to yaw by t1."""
        prev = self.look[-1]
        if t0 > prev[0] + 1e-6:
            self.face(t0, prev[1], prev[2])
        self.face(t1, yaw, pitch)

    def face_point(self, t0, t1, point, pitch=None):
        p = self.pos_at(t1)
        self.turn(t0, t1, yaw_to(p, point), pitch_to((p[0], p[1] + 1.6, p[2]), point) if pitch is None else pitch)

    def act(self, t, do, **kw):
        a = {'t': T(t), 'do': do}
        a.update(kw)
        self.actions.append(a)

    def cast(self, t, spell, target):
        tgt = target if isinstance(target, str) else [r2(c) for c in target]
        self.act(t, 'cast', spell=spell, target=tgt)

    def pose(self, t, name, blend=None):
        k = {'t': T(t), 'name': name}
        if blend:
            k['blend'] = blend
        self.poses.append(k)

    def anim(self, t, name, blend=None):
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
            k = {'t': T(t), 'yaw': r2(yw), 'pitch': r2(pt)}
            if e:
                k['ease'] = e
            d['look'].append(k)
        for key, lst in (('poses', self.poses), ('anims', self.anims), ('actions', self.actions)):
            if lst:
                d[key] = sorted(lst, key=lambda k: tval(k['t']))
        if self.limbs:
            d['limbs'] = self.limbs
        return d


# ================================================================ scene 1: the night
L1 = 104.0
F = Film(L1)
rng = Rng(1998)

# ---- the shield: an ellipsoid over the castle, its south wall at the hills' foot
DOME_C = (0.0, -8.0, -70.0)
DOME_R = (160.0, 150.0, 142.0)
T_DOME = 9.6
T_BREAK = 43.4                        # the dome's duration runs out: it burns from the last hit before this


def dome_point(x, y):
    """The point of the dome's south face at (x, y)."""
    q = 1 - ((x - DOME_C[0]) / DOME_R[0]) ** 2 - ((y - DOME_C[1]) / DOME_R[1]) ** 2
    return (x, y, DOME_C[2] + DOME_R[2] * math.sqrt(max(0.0, q)))


HITS = []


def strike(caster, t, spell, x, y):
    """caster fires spell at the dome's face at (x, y): the cast, the hit when the beam lands, a boom."""
    p = dome_point(x, y)
    caster.cast(t, spell, p)
    land = t + math.dist(caster.wand(t), p) / BEAM_SPEED
    HITS.append(land)
    F.effect(land, 'shield_hit', p)
    return land, p


# ---- the defenders: a line across the lawn facing south, the trio behind it
LINE_Z = 47.0
defenders = []
SKINS_D = ['student_gryffindor', 'student_slytherin', 'student_gryffindor']
for i in range(12):
    x = -14.0 + 2.5 * i + rng.uni(-0.3, 0.3)
    z = LINE_Z + rng.uni(-0.6, 0.6)
    skin = SKINS_D[i % 3]
    a = Actor(F, 'defender_%d' % (i + 1), 'player', (round(x, 2), 0.0, round(z, 2)), rng.uni(-8, 8), skin=skin,
              items={'mainhand': WANDS[i % 3]})
    defenders.append(a)
neville = defenders[2]
harry = Actor(F, 'harry', 'player', (0.0, 0.0, 44.0), 0.0, skin='harry', items={'mainhand': HARRY_WAND})
ron = Actor(F, 'ron', 'player', (-1.6, 0.0, 43.6), 5.0, skin='student_gryffindor', items={'mainhand': WANDS[2]})
hermione = Actor(F, 'hermione', 'player', (1.6, 0.0, 43.7), -5.0, skin='hermione', items={'mainhand': WANDS[1]})
trio = [harry, ron, hermione]

# they raise their wands to the sky and the shield goes up
for i, a in enumerate(defenders + trio):
    t_up = 7.0 + 0.08 * i
    a.pose(t_up, 'wand_raised', 8)
    a.face(t_up, a.look[-1][1], -35)
    a.act(8.7 + 0.05 * (i % 5), 'cast', spell='protego')
    tip = a.wand(8.9)
    a.cast(9.0 + 0.04 * i, 'glacius', (tip[0] * 1.6, 70.0, 50.0 + (tip[2] - 50.0) * 0.4))
    a.pose(12.4 + 0.05 * i, 'wand_point', 12)
    a.face(12.4 + 0.05 * i, a.look[-1][1], -12)
F.effect(T_DOME, 'shield_dome', DOME_C, spread=list(DOME_R), duration=int(round((T_BREAK - T_DOME) * 20)))
F.say(6.4, 'McGonagall', 'Protego Maxima. Fianto Duri. Repello Inimicum!', 4.2)
F.snd(9.2, 'minecraft:block.beacon.activate', volume=1.0, pitch=0.6)
F.snd(9.6, 'minecraft:block.amethyst_block.resonate', volume=1.0, pitch=0.5)
F.snd(10.4, 'minecraft:block.beacon.ambient', volume=1.0, pitch=0.7)

# ---- the Death Eaters on the southern slope, Voldemort at the middle, a little above
V_HILL = standing(1.0, 88.0)
voldemort = Actor(F, 'voldemort', 'player', V_HILL, 180.0, skin='voldemort', items={'mainhand': ELDER})
eaters = []
for i in range(16):
    x = -22.0 + 2.9 * i + (2.6 if i >= 8 else 0.0)
    z = 83.0 + rng.uni(-1.2, 1.2) + abs(x) * 0.04
    p = standing(x, z)
    a = Actor(F, 'death_eater_%d' % (i + 1), 'player', p, 180.0 + rng.uni(-10, 10), skin='death_eater',
              items={'mainhand': WANDS[(i + 1) % 3]})
    eaters.append(a)
# they apparate along the ridge in black smoke, Voldemort first
voldemort.act(0.0, 'vanish')
voldemort.act(15.4, 'appear')
F.part(15.4, 'minecraft:large_smoke', voldemort.chest(15.4), 60, (0.6, 1.2, 0.6))
for i, a in enumerate(sorted(eaters, key=lambda e: abs(e.pos_at(0)[0]))):
    t = 15.9 + 0.17 * i + rng.uni(0, 0.1)
    a.act(0.0, 'vanish')
    a.act(t, 'appear')
    F.part(t, 'minecraft:large_smoke', a.chest(t), 30, (0.5, 1.0, 0.5))
    if i % 4 == 0:
        F.snd(t, 'minecraft:entity.illusioner.mirror_move', at=a.chest(t), volume=1.6, pitch=0.6)
for a in eaters + [voldemort]:
    p = a.pos_at(0)
    a.face(15.0, yaw_to(p, (0, 30, 0)), 6)

# ---- Voldemort's demand and his first strike
F.say(21.0, 'Voldemort', 'Give me Harry Potter... and no one else need die tonight.', 4.0)
voldemort.pose(25.0, 'wand_raised', 10)
voldemort.face(25.0, 180.0, -20)
voldemort.pose(26.6, 'wand_point', 3)
voldemort.face(26.6, 180.0, -6)
land1, hit1 = strike(voldemort, 27.0, 'avada_kedavra', 1.0, 34.0)
F.snd(land1, 'minecraft:entity.lightning_bolt.thunder', volume=0.9, pitch=0.6)
F.snd(land1, 'minecraft:entity.generic.explode', volume=0.8, pitch=0.5)
F.part(27.0, 'minecraft:flash', voldemort.wand(27.0), 2, (0.1, 0.1, 0.1))

# ---- the volley: every wand on the ridge, in waves
SPELLS_DE = ['bombarda', 'confringo', 'incendio', 'avada_kedavra', 'crucio', 'dolohov_curse', 'sectumsempra']
for a in eaters:
    a.pose(28.4 + rng.uni(0, 0.6), 'wand_point', 6)
volley_t = []
for wave in range(3):
    for i, a in enumerate(eaters):
        t = 29.3 + wave * 1.7 + rng.uni(0, 1.2)
        x = a.pos_at(t)[0] * 1.3 + rng.uni(-10, 10)
        y = rng.uni(14, 52)
        land, p = strike(a, round(t, 2), SPELLS_DE[(i + wave) % len(SPELLS_DE)], x, y)
        volley_t.append(land)
for k, t in enumerate(sorted(volley_t)[::4]):
    F.snd(t, 'minecraft:entity.generic.explode', volume=0.45, pitch=0.55 + 0.05 * (k % 6))
F.sfx(30.2, 'flying_thunder', 0.6)
# the inside of the shield: flickers of light raining down near the lawn camera
for k in range(14):
    t = 29.6 + 0.4 * k
    F.part(t, 'minecraft:electric_spark', (rng.uni(-8, 8), rng.uni(12, 20), rng.uni(58, 64)), 30, (3, 2, 2))

# ---- the trio look up; the last strike; the break
for a in trio:
    a.face(35.2, a.look[-1][1], -28)
ron.face(36.2, 25.0, -10)
F.say(36.2, 'Ron', "It's not going to hold.", 2.6)
F.say(38.4, 'Hermione', 'It has to.', 2.2)
hermione.face(38.0, -20.0, -25)
voldemort.pose(40.0, 'wand_raised', 10)
voldemort.face(40.0, 180.0, -22)
voldemort.pose(41.4, 'wand_point', 2)
voldemort.face(41.4, 180.0, -6)
for i, a in enumerate(eaters):
    a.pose(40.6, 'wand_raised', 8)
    a.pose(41.5 + 0.03 * i, 'wand_point', 2)
for i, a in enumerate(eaters):
    strike(a, 41.6 + 0.04 * i, 'avada_kedavra', 1.0 + rng.uni(-3, 3), 34.0 + rng.uni(-3, 3))
land_v, hit_v = strike(voldemort, 41.5, 'avada_kedavra', 1.0, 34.0)
assert land_v < T_BREAK - 0.3
# the last hit before the break is where it burns from: make it Voldemort's, a beat after the rest
F.effect(T_BREAK - 0.05, 'shield_hit', hit_v)
F.snd(land_v, 'minecraft:entity.lightning_bolt.thunder', volume=1.0, pitch=0.5)
F.effect(42.6, 'lightning', (30, 0, 90))
F.snd(T_BREAK, 'minecraft:block.glass.break', volume=1.0, pitch=0.5)
F.snd(T_BREAK + 0.1, 'minecraft:block.respawn_anchor.deplete', volume=1.0, pitch=0.6)
F.snd(T_BREAK + 0.3, 'minecraft:entity.warden.sonic_boom', volume=0.7, pitch=0.5)
F.snd(T_BREAK + 1.2, 'minecraft:block.glass.break', volume=0.8, pitch=0.7)
F.snd(T_BREAK + 2.2, 'minecraft:block.glass.break', volume=0.6, pitch=0.9)
F.sfx(T_BREAK + 0.4, 'flying_thunder', 0.8)
# embers drifting down over the lawn as it burns
for k in range(12):
    t = T_BREAK + 0.6 + 0.4 * k
    F.part(t, 'minecraft:lava', (rng.uni(-14, 10), rng.uni(14, 22), rng.uni(46, 62)), 14, (6, 3, 4))
    F.part(t + 0.2, 'minecraft:ash', (rng.uni(-14, 10), rng.uni(10, 18), rng.uni(46, 62)), 60, (8, 4, 6))
voldemort.pose(43.8, 'standing', 10)
voldemort.face(44.0, 180.0, 4)

# ---- the charge
T_CHARGE = 46.4
voldemort.act(47.6, 'vanish')
F.part(47.6, 'minecraft:large_smoke', voldemort.chest(47.6), 80, (0.6, 1.4, 0.6))
F.snd(47.4, 'minecraft:entity.ravager.roar', volume=1.0, pitch=0.5)
duel_spots = []
for i, a in enumerate(eaters):
    x0 = a.pos_at(0)[0]
    dest = (round(x0 * 0.62 + rng.uni(-0.5, 0.5), 2), 0.0, round(58.5 + rng.uni(-1.2, 1.4), 2))
    t0 = T_CHARGE + rng.uni(0, 1.6)
    a.pose(t0, 'standing', 8)
    p0 = a.pos_at(t0)
    sp = rng.uni(4.4, 5.6)
    t1 = t0
    for w in route(a.pos_at(t0), dest):
        a.face(t1, yaw_to(a.pos_at(t1), w), 8)
        t1 = a.walk(t1, w, sp)
    a.face(t1, 0.0 + 180.0, 0)
    duel_spots.append((a, t1))
for a in defenders:
    a.pose(46.2, 'standing', 8)
    a.face(46.2, a.look[-1][1], 0)
for a in trio:
    a.face(44.6, 0.0, 0)
F.say(48.6, 'Neville', "Here they come!", 2.4)
neville.limb('right_arm', 'rot', 48.4, (0, 0, 0))
neville.limb('right_arm', 'rot', 48.8, (-150, 0, 0))
neville.limb('right_arm', 'rot', 50.2, (0, 0, 0))

# the giants and a troll come down out of the dark
giants = []
for k, (gx, gz, tx, tz) in enumerate(((-22.0, 79.0, -17.0, 67.0), (26.0, 78.0, 21.0, 67.0))):
    p = standing(gx, gz)
    g = Actor(F, 'giant_%d' % (k + 1), 'hogwartsmagic:giant', p, 180.0)
    g.act(0.0, 'vanish')
    g.act(45.0 + k * 0.6, 'appear')
    g.face(45.0, yaw_to(p, (tx, 0, tz)))
    g.anim(0.0, 'IDLE')
    g.anim(49.4 + k * 0.7, 'ROAR', 6)
    F.snd(49.4 + k * 0.7, 'hogwartsmagic:giant.roar', at=g.chest(49.4), volume=3.0, pitch=0.8)
    g.anim(52.0 + k * 0.5, 'WALK', 6)
    t_end = g.walk(52.0 + k * 0.5, standing(tx, tz), 1.6, step=0.8)
    g.anim(t_end, 'IDLE', 8)
    for s in range(int((t_end - 52.0) / 0.9)):
        F.snd(52.4 + k * 0.5 + s * 0.9, 'hogwartsmagic:giant.step', at=g.pos_at(52.4 + s * 0.9), volume=2.0, pitch=0.8)
    g.anim(76.0 + k * 2.1, 'ROAR', 6)
    g.anim(78.8 + k * 2.1, 'IDLE', 6)
    giants.append(g)
troll = Actor(F, 'troll', 'hogwartsmagic:troll', standing(30.0, 64.0), 120.0)
troll.act(0.0, 'vanish')
troll.act(50.0, 'appear')
troll.anim(0.0, 'IDLE')
troll.anim(53.0, 'WALK', 6)
TROLL_AT = (14.5, 0.0, 58.5)
troll.face(53.0, yaw_to(troll.pos_at(53.0), TROLL_AT))
t_tr = troll.walk(53.0, TROLL_AT, 2.6)
troll.anim(t_tr, 'IDLE', 6)

# ---- the duels: defenders step up, every wand fires
for a in defenders:
    p = a.pos_at(49.0)
    a.walk(49.6 + rng.uni(0, 0.8), (p[0], 0.0, p[2] + rng.uni(2.0, 3.6)), 2.4)
for a, (dx, dz) in zip(trio, ((-4.0, 49.0), (-8.0, 49.4), (8.6, 52.2))):
    a.walk(49.8, (dx, 0.0, dz), 3.0)
SPELLS_D = ['stupefy', 'expelliarmus', 'petrificus_totalus', 'depulso', 'confringo', 'incarcerous', 'bombarda']
fighters_d = defenders + [harry, ron]
pairs = []
by_x = sorted(eaters, key=lambda e: e.pos_at(56.0)[0])
for k, d in enumerate(sorted(fighters_d, key=lambda e: e.pos_at(56.0)[0])):
    pairs.append((d, by_x[min(len(by_x) - 1, round(k * (len(by_x) - 1) / max(1, len(fighters_d) - 1)))]))
fallen = set()
T_DUEL0, T_DUEL1 = 54.0, 67.6
for k, (d, e) in enumerate(pairs):
    arrive = max(duel_spots[eaters.index(e)][1], d.path[-1][0])
    t = max(T_DUEL0 + rng.uni(0, 1.2), arrive + 0.7)
    d.pose(t - 0.6, 'wand_point', 5)
    e.pose(t - 0.6, 'wand_point', 5)
    turn = 0
    while t < T_DUEL1 - 0.6:
        caster, other = (d, e) if turn % 2 == 0 else (e, d)
        if caster.id in fallen or other.id in fallen:
            break
        if caster is d and caster in (harry, ron) and t > 62.4:
            break
        caster.face_point(t - 0.3, t - 0.05, other.chest(t))
        spells = SPELLS_D if caster in fighters_d else SPELLS_DE
        caster.cast(t, spells[(k + turn) % len(spells)], other.target)
        hit = t + math.dist(caster.wand(t), other.chest(t)) / BEAM_SPEED
        if rng() < 0.4:
            other.act(hit - 0.15, 'cast', spell='protego')
        else:
            other.act(hit + 0.05, 'hurt')
            if caster in fighters_d and rng() < 0.42 and t > 56.5:
                other.act(hit + 0.4, 'die')
                fallen.add(other.id)
                F.part(hit + 0.3, 'minecraft:large_smoke', other.chest(hit), 20, (0.4, 0.8, 0.4))
        if rng() < 0.35:
            F.part(hit, 'minecraft:explosion', other.chest(hit), 2, (0.6, 0.6, 0.6))
            F.snd(hit, 'minecraft:entity.generic.explode', at=other.chest(hit), volume=0.7, pitch=rng.uni(0.8, 1.2))
        t += rng.uni(0.9, 1.5)
        turn += 1
# the battlefield burns: fires and smoking craters across the lawn
for k in range(16):
    t = 52.0 + k * 0.9
    x, z = rng.uni(-20, 18), rng.uni(52, 66)
    gx, gz = math.floor(x), math.floor(z)
    if any(abs(gx + 0.5 - a.pos_at(t + 30)[0]) < 1.5 and abs(gz + 0.5 - a.pos_at(t + 30)[2]) < 1.5
           for a in F.actors):
        continue
    F.block(t, 'minecraft:fire' if k % 3 else 'minecraft:campfire', (gx, 0, gz))
    F.part(t, 'minecraft:explosion_emitter', (gx + 0.5, 0.5, gz + 0.5), 1, (0, 0, 0))
    F.snd(t, 'minecraft:entity.generic.explode', at=(gx, 0, gz), volume=1.4, pitch=rng.uni(0.6, 0.9))
F.sfx(52.0, 'first_task_crowd_bed', 0.25, loop=True, duration=360)

# ---- the troll: a slam among the students, Hermione brings it down
victims = [d for d in defenders if d.pos_at(58.0)[0] > 7]
troll.face(59.4, yaw_to(TROLL_AT, (10.0, 0, 51.0)))
troll.anim(59.6, 'SLAM_WINDUP', 3)
troll.anim(60.4, 'SLAM_STRIKE', 2)
F.snd(59.6, 'hogwartsmagic:troll.roar', at=TROLL_AT, volume=2.0, pitch=0.9)
F.snd(60.7, 'hogwartsmagic:troll.slam_impact', at=(12, 0, 55), volume=2.5)
F.part(60.7, 'minecraft:explosion_emitter', (12.0, 0.4, 55.5), 1, (0, 0, 0))
F.part(60.7, 'minecraft:large_smoke', (12.0, 0.5, 55.5), 80, (1.5, 0.4, 1.5))
for v in victims[:2]:
    v.act(60.75, 'hurt')
hermione.face_point(61.0, 61.4, troll.chest(61.6))
hermione.pose(61.0, 'wand_point', 4)
hermione.cast(61.6, 'confringo', troll.chest(61.6))
hermione.cast(62.4, 'bombarda', troll.chest(62.4))
troll.act(61.95, 'hurt')
troll.anim(62.0, 'STAGGER', 3)
troll.act(62.8, 'hurt')
troll.act(63.2, 'die')
F.snd(63.0, 'hogwartsmagic:troll.death', at=TROLL_AT, volume=2.0)
F.part(62.75, 'minecraft:explosion_emitter', troll.chest(62.7), 1, (0, 0, 0))

# ---- the Dementors swoop; Harry's stag drives them off
dementors = []
DEM_C = (-2.0, 0.0, 52.0)
for k in range(10):
    ph = 2 * math.pi * k / 10
    dm = Actor(F, 'dementor_%d' % (k + 1), 'hogwartsmagic:dementor', (0, 30, 50), 0.0)
    dm.act(0.0, 'vanish')
    dm.act(55.0 + 0.3 * k, 'appear')
    dm.anim(0.0, 'GLIDE')
    t = 55.0 + 0.3 * k
    r0 = 16 + 3 * (k % 3)
    first = True
    while t <= 66.2:
        f = max(0.0, min(1.0, (t - 61.0) / 4.0))
        r = r0 * (1 - 0.6 * f)
        y = 20 + 2 * (k % 4) - 13 * f
        a = ph + (t - 55.0) * 0.35
        p = (DEM_C[0] + r * math.cos(a), y, DEM_C[2] + r * math.sin(a))
        if first:
            dm.key(0.0, p)
            first = False
        dm.key(t, p, 'linear')
        t = round(t + 0.6, 2)
    # the light hits them: they shriek and flee up into the dark
    t_hit = 66.4 + 0.05 * k
    dm.anim(t_hit, 'RECOIL', 2)
    p = dm.pos_at(t_hit)
    dx, dz = p[0] - DEM_C[0], p[2] - DEM_C[2]
    dl = math.hypot(dx, dz) or 1.0
    dm.key(t_hit + 0.01, p, 'linear')
    dm.key(t_hit + 2.2, (p[0] + dx / dl * 20, p[1] + 30, p[2] + dz / dl * 20), 'in')
    dm.act(t_hit + 2.0, 'vanish')
    dementors.append(dm)
for t, k in ((56.0, 0), (59.0, 3), (62.0, 6), (64.4, 1)):
    F.snd(t, 'hogwartsmagic:dementor.ambient', at=dementors[k].pos_at(t), volume=2.5, pitch=0.85)
F.snd(66.5, 'hogwartsmagic:dementor.shriek', volume=1.0, pitch=0.9)
F.snd(66.9, 'hogwartsmagic:dementor.shriek', volume=0.8, pitch=0.7)
for t in (63.0, 63.8, 64.6):
    F.part(t, 'minecraft:snowflake', (-1.0, 3.0, 49.0), 60, (4, 2, 4))
# Harry pulls back, looks up, casts
harry.walk(62.6, (-1.0, 0.0, 46.5), 2.6)
harry.pose(63.6, 'wand_raised', 6)
harry.face(63.6, 10.0, -40)
harry.face(64.8, 0.0, -24)
harry.pose(64.8, 'wand_point', 3)
harry.cast(65.0, 'expecto_patronum', (-1.0, 6.0, 58.0))
F.say(64.0, 'Harry', 'Expecto Patronum!', 2.2)
stag = Actor(F, 'stag', 'hogwartsmagic:patronus', (-1.0, 0.0, 47.8), 0.0)
stag.act(0.0, 'vanish')
stag.act(65.3, 'appear')
stag.key(65.3, (-1.0, 0.0, 47.8))
stag_pts = [(66.2, (-1.0, 0.0, 56.0)), (66.9, (-6.0, 0.0, 60.0)), (67.6, (-12.0, 0.0, 58.0)),
            (68.3, (-14.0, 0.0, 52.0))]
prev = stag.pos_at(65.3)
for t, p in stag_pts:
    stag.face(t - 0.4, yaw_to(prev, p))
    stag.key(t, p, 'linear')
    prev = p
stag.act(68.4, 'vanish')
F.effect(66.3, 'patronus_light', (-2.0, 0.0, 54.0), duration=60)
for k in range(10):
    t = 65.4 + 0.25 * k
    F.part(t, 'minecraft:end_rod', stag.pos_at(t), 10, (0.5, 0.6, 0.5))

# ---- Voldemort comes: the fighting stops, a ring forms
T_V = 69.6
V_AT = (0.0, 0.0, 56.0)
V_DUEL = (0.0, 0.0, 54.0)
H_DUEL = (0.0, 0.0, 44.0)
MID = ((V_DUEL[0] + H_DUEL[0]) / 2, 1.45, (V_DUEL[2] + H_DUEL[2]) / 2)
F.effect(T_V - 0.4, 'lightning', (24, 0, 72))
F.part(T_V, 'minecraft:large_smoke', (0.0, 1.0, 56.0), 160, (0.8, 1.6, 0.8))
F.part(T_V, 'minecraft:smoke', (0.0, 0.3, 56.0), 120, (2.5, 0.2, 2.5))
F.snd(T_V, 'minecraft:entity.illusioner.mirror_move', volume=1.0, pitch=0.5)
voldemort.hold(47.7)
voldemort.key(T_V - 0.01, V_AT)
voldemort.face(T_V - 0.01, 0.0 + 180.0, 0)
voldemort.act(T_V, 'appear')
voldemort.pose(T_V, 'standing', 1)
voldemort.walk(T_V + 0.8, V_DUEL, 0.9)
voldemort.face(T_V + 0.8, 180.0, 4)
harry.walk(68.6, H_DUEL, 1.4)
harry.pose(68.6, 'standing', 10)
harry.face(68.6, 0.0, 0)
everyone = [a for a in defenders + eaters + [ron, hermione] if a.id not in fallen]
side = {}
for a in everyone:
    p = a.pos_at(T_V)
    ang = math.atan2(p[0] - MID[0], p[2] - MID[2])
    if abs(math.sin(ang)) < 0.55:                     # keep the line between them clear
        ang = math.copysign(math.asin(0.55), math.sin(ang) or 1.0) if math.cos(ang) > 0 else \
            math.copysign(math.pi - math.asin(0.55), math.sin(ang) or 1.0)
    rr = 9.5 + rng.uni(0, 2.5)
    dest = (MID[0] + rr * math.sin(ang), 0.0, MID[2] + rr * math.cos(ang))
    t0 = T_V + rng.uni(0.2, 0.9)
    a.pose(t0, 'standing', 8)
    a.face(t0, yaw_to(p, dest))
    t1 = a.walk(t0, dest, 2.2)
    a.face_point(t1, t1 + 0.6, (MID[0], 1.6, MID[2]))
    side[a.id] = dest
F.snd(T_V + 0.5, 'minecraft:ambient.cave', volume=0.6, pitch=0.6)

F.say(73.0, 'Voldemort', 'Harry Potter. The boy who lived... come to die.', 3.4)
F.say(75.2, 'Harry', "That wand was never yours, Tom. It answers to me.", 3.4)

# ---- the duel: the beams lock
T_CAST = 78.0
T_PUSH = 87.4
T_WIN = 88.5
harry.pose(77.0, 'wand_raised', 6)
voldemort.pose(77.0, 'wand_raised', 6)
harry.pose(T_CAST - 0.15, 'wand_point', 2)
voldemort.pose(T_CAST - 0.15, 'wand_point', 2)
harry.face(T_CAST - 0.15, 0.0, 0)
voldemort.face(T_CAST - 0.15, 180.0, 0)
F.say(T_CAST - 0.6, 'Harry', 'Expelliarmus!', 2.0)
F.say(T_CAST + 1.6, 'Voldemort', 'Avada Kedavra!', 2.0)


def lock_point(t):
    """Where the beams meet: the middle, wavering, then driven back into Voldemort."""
    base = 0.0 + 0.5 * math.sin(t * 2.3) * (1 if t < T_PUSH else 0)
    if t < 84.0:
        z = MID[2] + 0.6 * math.sin(t * 1.3)
    elif t < T_PUSH:
        z = MID[2] - 1.0 + (t - 84.0) * 0.15
    else:
        f = min(1.0, (t - T_PUSH) / (T_WIN - T_PUSH))
        z = MID[2] + f * f * (V_DUEL[2] - MID[2] - 0.4)
    return (base * 0.3, MID[1] + 0.15 * math.sin(t * 3.1), z)


t = T_CAST
while t < T_WIN - 0.05:
    lp = lock_point(t)
    harry.cast(round(t, 2), 'expelliarmus', lp)
    if t < T_PUSH:
        voldemort.cast(round(t + 0.2, 2), 'avada_kedavra', lock_point(t + 0.2))
    t = round(t + 0.45, 2)
voldemort.act(T_WIN - 0.05, 'hurt')
t = T_CAST + 0.25
while t < T_WIN:
    lp = lock_point(t)
    F.part(t, 'minecraft:electric_spark', lp, 26, (0.25, 0.25, 0.25))
    F.part(t, 'minecraft:end_rod', lp, 6, (0.2, 0.2, 0.2))
    if round(t * 20) % 4 == 0:
        F.part(t, 'minecraft:flash', lp, 1, (0, 0, 0))
        F.part(t, 'minecraft:lava', lp, 3, (0.2, 0.2, 0.2))
    t = round(t + 0.15, 2)
F.snd(T_CAST + 0.3, 'minecraft:entity.guardian.attack', at=MID, volume=1.6, pitch=0.6)
F.snd(T_CAST + 2.0, 'minecraft:block.beacon.ambient', at=MID, volume=2.0, pitch=0.5)
F.snd(T_CAST + 4.5, 'minecraft:entity.guardian.attack', at=MID, volume=1.6, pitch=0.7)
F.snd(T_CAST + 7.0, 'minecraft:block.beacon.ambient', at=MID, volume=2.0, pitch=0.6)
F.snd(T_PUSH, 'minecraft:item.totem.use', at=MID, volume=0.9, pitch=0.8)
# straining: Harry leans in, Voldemort's arm shakes and gives
harry.limb('body', 'rot', T_CAST, (0, 0, 0))
harry.limb('body', 'rot', T_CAST + 2.0, (10, 0, 0))
harry.limb('body', 'rot', T_PUSH, (14, 0, 0))
harry.limb('body', 'rot', T_WIN + 1.0, (0, 0, 0))
voldemort.limb('right_arm', 'rot', T_CAST, (0, 0, 0))
for k in range(int((T_PUSH - T_CAST) / 0.2)):
    voldemort.limb('right_arm', 'rot', T_CAST + 0.2 * (k + 1), (rng.uni(-4, 4), rng.uni(-3, 3), 0), 'linear')
voldemort.limb('body', 'rot', T_CAST, (0, 0, 0))
voldemort.limb('body', 'rot', T_PUSH, (4, 0, 0))
voldemort.limb('body', 'rot', T_WIN, (-14, 0, 0))

# Expelliarmus wins: the Elder Wand flies to Harry
voldemort.act(T_WIN + 0.05, 'hold', item='minecraft:air')
v_hand = (V_DUEL[0] - 0.35, 1.45, V_DUEL[2] - 0.5)
h_hand = (H_DUEL[0] - 0.35, 1.3, H_DUEL[2] + 0.45)
elder = Actor(F, 'elder_wand', 'hogwartsmagic:memory_block', v_hand, 0.0, scale=0.45)
elder.d['block'] = 'minecraft:end_rod'
elder.act(0.0, 'vanish')
elder.act(T_WIN + 0.05, 'appear')
T_CATCH = T_WIN + 1.0
n = 10
for k in range(n + 1):
    f = k / n
    p = (v_hand[0] + (h_hand[0] - v_hand[0]) * f, v_hand[1] + (h_hand[1] - v_hand[1]) * f + 2.2 * f * (1 - f),
         v_hand[2] + (h_hand[2] - v_hand[2]) * f)
    elder.key(T_WIN + 0.05 + (T_CATCH - T_WIN - 0.05) * f if k else T_WIN + 0.05, p,
              'spline' if 0 < k < n else ('in_out' if k else None))
elder.look = [(T_WIN + 0.05, 0.0, 0.0, None), (T_CATCH, 0.0, 720.0, 'out')]
elder.act(T_CATCH, 'vanish')
harry.limb('left_arm', 'rot', T_CATCH - 0.4, (0, 0, 0))
harry.limb('left_arm', 'rot', T_CATCH, (-70, 0, -10))
harry.limb('left_arm', 'rot', T_CATCH + 2.4, (-70, 0, -10))
harry.limb('left_arm', 'rot', T_CATCH + 3.4, (0, 0, 0))
harry.act(T_CATCH, 'equip', slot='offhand', item=ELDER)
F.snd(T_WIN, 'minecraft:entity.firework_rocket.large_blast', at=V_DUEL, volume=1.2, pitch=0.6)
F.part(T_WIN, 'minecraft:flash', (0, 1.4, V_DUEL[2] - 0.3), 2, (0, 0, 0))
F.part(T_WIN, 'minecraft:totem_of_undying', (0, 1.4, V_DUEL[2] - 0.3), 120, (0.4, 0.6, 0.4))
F.snd(T_CATCH, 'minecraft:block.amethyst_block.chime', at=H_DUEL, volume=1.4, pitch=0.8)

# Voldemort greys and flakes away to nothing
T_ASH = 91.3
T_GONE = 95.2
voldemort.pose(T_WIN + 0.3, 'arms_out', 12)
voldemort.face(T_WIN + 0.3, 180.0, -30)
voldemort.act(T_ASH, 'stone')
voldemort.face(T_GONE - 0.5, 180.0, -38)
F.snd(T_ASH, 'minecraft:block.sand.break', at=V_DUEL, volume=1.4, pitch=0.5)
F.snd(T_ASH + 1.5, 'minecraft:entity.phantom.death', at=V_DUEL, volume=1.2, pitch=0.5)
t = T_ASH - 0.6
while t < T_GONE + 3.0:
    f = min(1.0, max(0.0, (t - T_ASH + 0.6) / (T_GONE - T_ASH + 0.6)))
    if t < T_GONE:
        F.part(t, 'minecraft:ash', (0.0, 1.0, V_DUEL[2]), int(30 + 90 * f), (0.35, 0.9, 0.3))
        F.part(t, 'minecraft:white_ash', (0.0, 1.2, V_DUEL[2]), int(20 + 60 * f), (0.4, 0.9, 0.35))
    F.part(t, 'minecraft:ash', (0.0, 2.4 + (t - T_ASH), V_DUEL[2] + 0.4 * (t - T_ASH)), 40, (1.0, 0.8, 1.0))
    t = round(t + 0.2, 2)
voldemort.act(T_GONE, 'vanish')
F.part(T_GONE, 'minecraft:white_ash', (0.0, 1.0, V_DUEL[2]), 400, (0.6, 1.1, 0.6))
F.part(T_GONE, 'minecraft:ash', (0.0, 1.0, V_DUEL[2]), 400, (0.6, 1.1, 0.6))
F.snd(T_GONE, 'minecraft:block.sand.fall', at=V_DUEL, volume=1.4, pitch=0.6)
harry.pose(T_GONE + 1.0, 'standing', 30)
harry.face(T_GONE + 1.0, 0.0, 8)
harry.face(101.0, 0.0, 4)

# the white-out into dawn
for k in range(6):
    F.part(99.0 + 0.6 * k, 'minecraft:white_ash', (0.0, 2.0, 46.0), 50, (2.0, 1.5, 2.0))
F.snd(101.6, 'minecraft:block.beacon.activate', volume=0.8, pitch=1.4)

# ---- the world: wind, storm, thunder; light over the lawn; music
F.music(0.0, 'summoning_voldemort', 0.95)
F.music(42.6, 'chamber_duel', 1.0)
F.sfx(0.0, 'first_task_wind_high', 0.35, loop=True, duration=2060)
for t, at in ((2.8, (-60, 0, -40)), (18.0, (50, 0, 100)), (33.0, (-50, 0, 95)), (56.5, (60, 0, 20)),
              (84.0, (-40, 0, 110))):
    F.effect(t, 'lightning', at)
for k, (x, z) in enumerate(((-12, 44), (6, 44), (-16, 56), (10, 56), (0, 62), (-4, 34), (14, 34))):
    F.block(0.0, 'minecraft:light', (x, 9, z))
F.snd(0.4, 'minecraft:ambient.cave', volume=0.5, pitch=0.6)
F.narrate(1.0, 'THE BATTLE OF HOGWARTS', 4.2, size=3)

# ---- camera
H, V, HE, RO = 'actor:harry', 'actor:voldemort', 'actor:hermione', 'actor:ron'
vh = V_HILL
F.shot(0.0, (10.0, 6.0, 66.0), (0.0, 42.0, -120.0), 72, cut=False)
F.shot(6.0, (8.0, 9.5, 63.0), (0.0, 46.0, -120.0), 70, cut=False)
# 2: low on the line as they raise their wands
F.shot(6.0, (9.0, 0.7, 50.8), (-2.0, 1.9, 46.5), 60)
F.shot(10.0, (8.2, 0.75, 50.4), (-3.0, 2.6, 46.5), 58, cut=False)
# 3: behind them, looking up as the shield climbs
F.shot(10.0, (-1.5, 1.4, 38.0), (0.0, 12.0, 75.0), 88)
F.shot(15.5, (-1.0, 1.5, 39.2), (0.0, 26.0, 75.0), 90, cut=False)
# 4: the ridge, from behind the line: they apparate; the castle under its shield across the dark
F.shot(15.5, (4.0, vh[1] + 4.5, 96.0), (0.0, 38.0, -100.0), 70)
F.shot(20.5, (2.0, vh[1] + 4.0, 95.0), (0.0, 36.0, -100.0), 68, cut=False)
# 5: Voldemort, from below
F.shot(20.5, (1.6, vh[1] + 1.0, vh[2] - 3.2), V, 45, roll=-4)
F.shot(24.5, (1.4, vh[1] + 1.0, vh[2] - 2.8), V, 40, cut=False, roll=-5)
# 6: profile: the first strike
F.shot(24.5, (-11.0, vh[1] + 2.6, vh[2] - 4.0), (1.0, vh[1] + 6.0, 76.0), 68)
F.shot(29.0, (-11.5, vh[1] + 3.2, vh[2] - 4.6), (1.0, vh[1] + 8.0, 72.0), 70, cut=False)
# 7: inside, looking up: the volley
F.shot(29.0, (0.0, 1.0, 52.0), (0.0, 30.0, 72.0), 96, roll=3)
F.shot(35.0, (0.5, 1.2, 53.0), (0.0, 34.0, 72.0), 96, cut=False, roll=5)
# 8: the trio
F.shot(35.0, (0.6, 1.4, 47.6), (0.0, 1.9, 44.0), 52)
F.shot(39.5, (0.4, 1.35, 47.2), (0.0, 1.9, 44.0), 48, cut=False)
# 9: over his shoulder: the last strike
F.shot(39.5, (vh[0] + 1.4, vh[1] + 3.4, vh[2] + 2.2), (vh[0], vh[1] + 6.0, 70.0), 62)
F.shot(44.0, (vh[0] + 1.6, vh[1] + 3.8, vh[2] + 2.4), (vh[0], vh[1] + 9.0, 70.0), 66, cut=False)
# 10: the burn, wide from high over the lawn
F.shot(44.0, (-14.0, 12.0, 34.0), (0.0, 30.0, 72.0), 90)
F.shot(49.5, (-16.0, 13.5, 32.0), (0.0, 42.0, 72.0), 92, cut=False)
# 11: low at the hill's foot: the charge comes down past the lens
F.shot(49.5, (-5.0, 0.7, 63.5), (0.0, 16.0, 80.0), 78, roll=-3)
F.shot(54.5, (-5.5, 0.8, 62.8), (0.0, 6.0, 72.0), 80, cut=False, roll=-4)
# 12: tracking along the duel line
F.shot(54.5, (-20.0, 2.2, 52.0), (-10.0, 1.4, 56.0), 70)
F.shot(59.5, (-6.0, 2.6, 51.0), (4.0, 1.4, 56.0), 70, cut=False, ease='linear')
# 13: the troll
F.shot(59.5, (6.0, 0.6, 50.0), (14.0, 2.6, 58.0), 72, roll=4)
F.shot(63.5, (6.4, 0.7, 50.4), (13.0, 2.0, 57.0), 70, cut=False, roll=3)
# 14: the Dementors and the stag
F.shot(63.5, (-3.0, 0.8, 40.0), (-1.0, 10.0, 52.0), 84)
F.shot(68.5, (-3.4, 1.0, 39.0), (-4.0, 3.0, 56.0), 82, cut=False)
# 15: he comes; the ring opens
F.shot(68.5, (16.0, 4.0, 34.0), (0.0, 1.2, 54.0), 64)
F.shot(72.5, (15.0, 3.4, 35.0), (0.0, 1.4, 52.0), 60, cut=False)
# 16: the two-shot, profile
F.shot(72.5, (-10.0, 1.5, 49.0), (0.0, 1.5, 49.0), 58)
F.shot(77.5, (-8.6, 1.5, 49.0), (0.0, 1.5, 49.0), 56, cut=False)
# 17: the cast, low, between the wands
F.shot(77.5, (-3.6, 0.7, 49.0), (0.0, 1.5, 49.0), 74, roll=-2)
F.shot(81.5, (-3.2, 0.75, 49.4), (0.0, 1.5, 49.4), 72, cut=False, roll=-3)
# 18 / 19: close on each of them
F.shot(81.5, (1.3, 1.65, 46.4), H, 40, roll=-5)
F.shot(84.5, (1.2, 1.62, 46.1), H, 36, cut=False, roll=-6)
F.shot(84.5, (-1.3, 1.65, 51.6), V, 40, roll=5)
F.shot(87.0, (-1.2, 1.62, 51.9), V, 36, cut=False, roll=6)
# 20: the turn: the beam drives home, the wand flies
F.shot(87.0, (-7.6, 1.4, 49.0), (0.0, 1.5, 49.6), 62)
F.shot(91.0, (-7.0, 1.5, 48.6), (0.0, 1.5, 48.0), 60, cut=False)
# 21: he turns to ash
F.shot(91.0, (2.6, 1.5, 51.4), (0.0, 1.8, 54.0), 46)
F.shot(96.5, (2.2, 1.6, 51.0), (0.0, 2.6, 54.0), 44, cut=False)
# 22: Harry alone, from behind, the ash drifting past
F.shot(96.5, (-0.8, 1.9, 40.2), (0.0, 1.6, 50.0), 60)
F.shot(100.0, (-0.6, 1.9, 40.8), (0.0, 1.6, 50.0), 58, cut=False)
# 23: the push-in on his face, into the white
F.shot(100.0, (0.3, 1.66, 47.6), H, 48)
F.shot(L1, (0.15, 1.65, 46.3), H, 32, cut=False)


# ================================================================ scene 2: dawn
L2 = 34.0
G = Film(L2)
rng2 = Rng(2)
F2 = G
d_harry = Actor(G, 'harry', 'player', (0.0, 0.0, 6.0), 180.0, skin='harry',
                items={'mainhand': HARRY_WAND, 'offhand': ELDER})
d_ron = Actor(G, 'ron', 'player', (-3.6, 0.0, 12.6), 160.0, skin='student_gryffindor', items={'mainhand': WANDS[2]})
d_her = Actor(G, 'hermione', 'player', (3.6, 0.0, 12.6), 200.0, skin='hermione', items={'mainhand': WANDS[1]})
# survivors about the lawn
for k in range(10):
    x, z = rng2.uni(-18, 16), rng2.uni(28, 58)
    a = Actor(G, 'survivor_%d' % (k + 1), 'player', (round(x, 2), 0.0, round(z, 2)), rng2.uni(120, 240),
              skin=SKINS_D[k % 3])
    if k % 3 == 0:
        a.pose(0.0, 'kneel', 1)
    elif k % 3 == 1:
        a.face(0.0, rng2.uni(150, 210), 10)
for k in range(3):
    a = G.actors[3 + k * 2]
    other = G.actors[4 + k * 2]
    p = a.pos_at(0)
    other.key(0.0, (p[0] + 0.9, 0.0, p[2] + 0.2))
    a.face(0.0, -90.0, 6)
    other.face(0.0, 90.0, 6)
# Ron and Hermione come to him
t1 = d_ron.walk(6.8, (-1.2, 0.0, 6.9), 1.5)
t2 = d_her.walk(7.1, (1.2, 0.0, 6.9), 1.5)
d_ron.turn(t1, t1 + 0.6, 170.0)
d_her.turn(t2, t2 + 0.6, 190.0)
d_harry.turn(11.0, 11.8, 190.0, 10)
d_ron.turn(max(12.0, t1 + 0.7), max(12.4, t1 + 1.1), 120.0, 14)
G.say(12.0, 'Ron', "That's the Elder Wand. The most powerful wand ever made.", 3.6)
G.say(15.4, 'Harry', "It's done enough harm.", 2.6)
# he snaps it and throws the pieces over the brow
T_SNAP = 18.2
d_harry.limb('left_arm', 'rot', 16.4, (0, 0, 0))
d_harry.limb('left_arm', 'rot', 17.2, (-60, 0, -20))
d_harry.limb('right_arm', 'rot', 16.4, (0, 0, 0))
d_harry.limb('right_arm', 'rot', 17.2, (-60, 0, 20))
d_harry.act(16.4, 'hold', item='minecraft:air')
d_harry.act(T_SNAP, 'equip', slot='offhand', item='minecraft:air')
d_harry.limb('left_arm', 'rot', T_SNAP + 0.1, (-50, 0, -30))
d_harry.limb('right_arm', 'rot', T_SNAP + 0.1, (-50, 0, 30))
d_harry.limb('right_arm', 'rot', T_SNAP + 0.9, (-160, 0, 10))
d_harry.limb('right_arm', 'rot', T_SNAP + 1.4, (-40, 0, 0))
d_harry.limb('left_arm', 'rot', T_SNAP + 1.4, (0, 0, 0))
d_harry.limb('right_arm', 'rot', T_SNAP + 2.6, (0, 0, 0))
d_harry.act(T_SNAP + 2.6, 'hold', item=HARRY_WAND)
G.snd(T_SNAP, 'minecraft:entity.item.break', at=(0, 1.3, 5.6), volume=1.6, pitch=0.7)
G.snd(T_SNAP + 0.05, 'minecraft:block.wood.break', at=(0, 1.3, 5.6), volume=1.2, pitch=0.9)
G.part(T_SNAP, 'minecraft:crit', (0.0, 1.3, 5.5), 30, (0.15, 0.15, 0.15))
for k, side_x in enumerate((-1.0, 1.0)):
    piece = Actor(G, 'wand_piece_%d' % (k + 1), 'hogwartsmagic:memory_block', (0.0, 1.3, 5.6), 0.0, scale=0.25)
    piece.d['block'] = 'minecraft:end_rod'
    piece.act(0.0, 'vanish')
    piece.act(T_SNAP + 1.0, 'appear')
    s0 = (0.2, 1.9, 5.6)
    piece.key(T_SNAP + 1.0, s0)
    for j in range(1, 9):
        f = j / 8
        tt = T_SNAP + 1.0 + 1.6 * f
        piece.key(tt, (s0[0] + side_x * 1.5 * f, s0[1] + 6 * f - 11 * f * f, s0[2] - 14 * f),
                  'spline' if j < 8 else 'in_out')
    piece.look = [(T_SNAP + 1.0, 0.0, 0.0, None), (T_SNAP + 2.6, 300.0 * side_x, 900.0, 'linear')]
    piece.act(T_SNAP + 2.6, 'vanish')
d_harry.turn(T_SNAP + 2.8, T_SNAP + 3.4, 180.0, -4)
d_ron.turn(T_SNAP + 3.0, T_SNAP + 3.6, 180.0, -4)
d_her.turn(T_SNAP + 3.2, T_SNAP + 3.8, 180.0, -4)
# the world at dawn: smoke from the fires, the sky
G.effect(0.0, 'sky', (0, 0, 30), style='dawn')
for k, (x, z) in enumerate(((-14, 40), (9, 50), (-6, 58), (15, 36), (-20, 54), (4, 30))):
    G.block(0.0, 'minecraft:campfire', (x, 0, z))
G.music(0.6, 'lake_window', 1.0)
G.music(14.2, 'chamber_reunion', 1.0)
G.sfx(0.0, 'flying_lawn_air', 0.4, loop=True)
G.snd(2.0, 'minecraft:ambient.cave', volume=0.3, pitch=1.2)
G.narrate(2.4, 'Dawn.', 3.4, size=2)
G.narrate(28.4, 'All was well.', 5.0, size=2)
# camera
G.shot(0.0, (10.0, 5.0, 60.0), (0.0, 40.0, -120.0), 72, cut=False)
G.shot(6.0, (9.0, 6.4, 57.0), (0.0, 42.0, -120.0), 70, cut=False)
G.shot(6.0, (-14.0, 1.5, 49.0), (-6.0, 1.2, 44.0), 64)
G.shot(11.0, (-9.0, 1.6, 48.0), (0.0, 1.2, 40.0), 64, cut=False, ease='linear')
G.shot(11.0, (7.0, 1.55, 7.6), (0.0, 1.6, 6.4), 52)
G.shot(16.0, (6.4, 1.55, 7.4), (0.0, 1.6, 6.4), 50, cut=False)
G.shot(16.0, (1.4, 1.7, 3.4), (0.0, 1.35, 5.6), 50, roll=-3)
G.shot(21.0, (1.6, 1.8, 3.0), (-0.6, 3.0, -4.0), 56, cut=False, roll=-2)
G.shot(21.0, (0.2, 1.8, 13.0), (0.0, 30.0, -120.0), 62)
G.shot(26.0, (0.1, 1.8, 11.6), (0.0, 32.0, -120.0), 56, cut=False)
G.shot(26.0, (0.0, 2.2, 11.0), (0.0, 1.6, 6.5), 64)
G.shot(30.0, (0.0, 2.3, 10.2), (0.0, 1.6, 6.5), 60, cut=False)
G.shot(30.0, (1.2, 3.6, 16.0), (0.0, 6.0, -40.0), 64)
G.shot(L2, (6.0, 20.0, 34.0), (0.0, 26.0, -100.0), 70, cut=False)


# ================================================================ assemble and check
def scene(film, title, time_of_day, nxt=None, white=False):
    s = {'title': title, 'length': T(film.L), 'radius': R, 'fog_far': FOG_FAR}
    if nxt:
        s['next'] = nxt
        if white:
            s['handover_colour'] = 'white'
    st = dict(SET)
    st['time_of_day'] = time_of_day
    st['build_ticks'] = 100
    s['set'] = st
    s['poses'] = {'standing': {}}
    s['actors'] = [a.emit() for a in film.actors]
    s['events'] = sorted(film.events, key=lambda e: tval(e['t']))
    s['words'] = sorted(film.words, key=lambda w: tval(w['t']))
    s['camera'] = film.cam
    return s


def check(film, s, name):
    problems = []
    L = film.L
    for a in s['actors']:
        for field in ('path', 'look', 'poses', 'actions', 'anims'):
            ts = [tval(k['t']) for k in a.get(field, [])]
            if ts != sorted(ts):
                problems.append('%s %s not sorted' % (a['id'], field))
            if any(x < 0 or x > L for x in ts):
                problems.append('%s %s time outside the film' % (a['id'], field))
        ts = [tval(k['t']) for k in a['path']]
        if len(ts) != len(set(ts)):
            problems.append('%s path has repeated times' % a['id'])
        for pn, tr in a.get('limbs', {}).items():
            ts = [tval(k['t']) for k in tr.get('rot', [])]
            if ts != sorted(ts):
                problems.append('%s limb %s not sorted' % (a['id'], pn))
        ys = [k['yaw'] for k in a['look']]
        for y0, y1 in zip(ys, ys[1:]):
            if abs(y1 - y0) > 180 and a['type'] != 'hogwartsmagic:memory_block':
                problems.append('%s yaw jumps %s -> %s' % (a['id'], y0, y1))
    for lst, nm in ((s['events'], 'events'), (s['words'], 'words'), (s['camera'], 'camera')):
        for k in lst:
            if not 0 <= tval(k['t']) <= L:
                problems.append('%s time %s outside the film' % (nm, k['t']))
    for e in s['events']:
        if e['kind'] == 'particles' and e['count'] > 500:
            problems.append('too many particles %s' % e)
    # walkers stand on the ground and never inside a block (sampled every 0.25 s)
    for act in film.actors:
        if act.d['type'] != 'player':
            continue
        vis = []
        for k in sorted(act.actions, key=lambda k: tval(k['t'])):
            if k['do'] in ('vanish', 'die', 'appear'):
                vis.append((tval(k['t']), k['do'] == 'appear'))

        def shown(t):
            on = True
            for tt, v in vis:
                if tt <= t:
                    on = v
            return on
        for i in range(int(L * 4) + 1):
            t = i / 4
            if not shown(t):
                continue
            p = act.pos_at(t)
            g = ground(p[0], p[2])
            near = max(ground(p[0] + dx, p[2] + dz) for dx in (-0.3, 0, 0.3) for dz in (-0.3, 0, 0.3))
            low = min(ground(p[0] + dx, p[2] + dz) for dx in (-0.12, 0, 0.12) for dz in (-0.12, 0, 0.12))
            if p[1] < low - 0.05 or p[1] > near + 0.05 or not clear(p[0], p[1] + 0.05, p[2], wade=True):
                problems.append('%s off the ground at %.2f %s (ground %s)' % (act.id, t, [r2(c) for c in p], g))
                break
    # cameras above the terrain
    for k in s['camera']:
        p = k['pos']
        if p[1] < ground(p[0], p[2]) + 0.3 or (math.floor(p[0]), math.floor(p[1]), math.floor(p[2])) in SOLID:
            problems.append('camera at %s in the ground %s' % (k['t'], p))
    ct = [tval(k['t']) for k in s['camera']]
    if ct != sorted(ct):
        problems.append('camera not sorted')
    cuts = [0.0] + [tval(k['t']) for k in s['camera'] if k.get('cut')] + [L]
    lens = [round(b - a, 2) for a, b in zip(cuts, cuts[1:])]
    print(name, 'shots', len(lens), lens)
    for ln in lens:
        if ln < 2 or ln > 6:
            problems.append('shot length %s' % ln)
    wt = sorted(tval(w['t']) for w in s['words'])
    for a, b in zip(wt, wt[1:]):
        if b - a < 2:
            problems.append('lines closer than 2 s at %.1f' % a)
    ids = [a['id'] for a in s['actors']]
    if len(ids) != len(set(ids)):
        problems.append('duplicate actor ids')
    return problems


s1 = scene(F, 'The Battle of Hogwarts', 18000, nxt='battle_of_hogwarts_dawn', white=True)
s2 = scene(G, 'The Battle of Hogwarts: Dawn', 23800)
p1 = check(F, s1, 'battle_of_hogwarts')
p2 = check(G, s2, 'battle_of_hogwarts_dawn')
print('fallen', len(fallen), 'hits', len(HITS), 'actors', len(s1['actors']), 'events', len(s1['events']))
print('\n'.join(p1 + p2) if p1 + p2 else 'NO PROBLEMS')
for s, sid in ((s1, 'battle_of_hogwarts'), (s2, 'battle_of_hogwarts_dawn')):
    txt = write_scene(s, sid)
    print(sid, 'chars', len(txt))
