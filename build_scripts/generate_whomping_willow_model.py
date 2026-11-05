"""Compile the Whomping Willow: geometry, the shared rig table, a .bbmodel and Java.

    python build_scripts/generate_whomping_willow_model.py

Writes three things:

  * the mesh between the GENERATED GEOMETRY markers in client/model/WhompingWillowModel.java
  * the bone table between the GENERATED RIG markers in entity/WhompingWillowRig.java
  * blockbench_models/whomping_willow.bbmodel, which generate_whomping_willow_texture.py paints

Why a rig table, and why it lives in common code
------------------------------------------------
The willow's damage used to be computed from a handful of hand-kept constants (a bearing array, a
reach, a tip height) that were *supposed* to describe where the model's branches were. They did not:
the model was mirrored left-to-right relative to the server, so two of the four branches swung on
the opposite side of the trunk from the arc that dealt their damage, and the knot you were told to
press was drawn on the far side of the tree from where pressing it worked.

The fix is structural rather than a sign flip. Every bone's parent, offset and rest rotation is
written into WhompingWillowRig, a pure-Java class the server can load. The rig poses the bones and
runs forward kinematics with exactly the ModelPart convention (translate, then Rz*Ry*Rx), so the
server tests hits against the same branch polyline the client draws. There is no second description
of the tree left to drift.

Authoring space
---------------
Everything here is authored directly in Minecraft's baked model space: px units, -Y is up, the
root bone sits at y=24 (the entity's feet), -Z is the front. Bones are placed by their WORLD (rest)
pivot and WORLD rotation and the local PartPose is derived, which is what lets a curtain hang
plumb or a limb point at an exact bearing however twisted the trunk above it is.

A bearing `a` (Minecraft yaw convention, relative to the planted yaw) is the model-space direction
(-sin a, 0, -cos a), and a bone whose local +Z should point there has yRot = a + pi. This was
verified against LivingEntityRenderer + ModelPart with the scratch harness, not derived on paper:
the paper derivation is what was wrong last time.
"""

import json
import math
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_BB = os.path.join(ROOT, "blockbench_models", "whomping_willow.bbmodel")
OUT_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "hogwartsmagic",
                        "client", "model", "WhompingWillowModel.java")
OUT_RIG = os.path.join(ROOT, "src", "main", "java", "com", "hogwartsmagic",
                       "entity", "WhompingWillowRig.java")

TEX_W, TEX_H = 1024, 1024
MODEL_SCALE = 1.0          # 16 px per block: vanilla texel density, so the bark reads at block scale
PX = 16.0 / MODEL_SCALE    # model px per block
GEO_SEED = 0x77000D

# --- shape parameters -----------------------------------------------------------------------------

# Trunk: six nested rings. Heavy, crooked and twisted -- the old one was a tidy stack of squares.
RING_H = [20, 18, 17, 16, 15, 14]                  # 100 px = 6.25 blocks of bole
RING_HW = [27, 24, 22, 20, 18, 16]
RING_DRIFT = [(0, 0), (2, -1), (4, -2), (5, 0), (4, 2), (2, 4)]   # world px: a crooked bole
RING_TWIST = 0.11                                   # rad per ring: the bark ridges spiral

# The four striking limbs. Bearings in degrees relative to the planted yaw. Deliberately not on
# the compass points: four limbs on the exact quarters read as a manufactured cross.
LIMB_BEARING = [14.0, 97.0, 191.0, 274.0]
LIMB_HOST = [2, 1, 3, 2]                            # trunk ring each limb grows from
LIMB_LIFT = [10, 14, 6, 14]                          # px above that ring's base
LIMB_SCALE = [1.00, 0.94, 1.06, 0.97]
LIMB_LEN = [24, 23, 22, 20, 18, 16]
LIMB_THICK = [16, 13, 11, 9, 7, 5]
# Java xRot per segment, positive is UP (verified). Rest is the dormant, limp pose: a willow arch
# that lifts off the trunk and hangs at the tip.
LIMB_REST = [0.30, -0.05, -0.28, -0.34, -0.36, -0.36]

# Crown boughs: thick upward forks off the top ring that carry the canopy. Purely visual -- they
# never deal damage -- but they are full bones so the whole crown can move.
BOUGH_BEARING = [58.0, 147.0, 236.0, 322.0]
BOUGH_LEN = [34, 30, 24]
BOUGH_THICK = [14, 11, 8]
BOUGH_REST = [0.98, -0.22, -0.34]
LEADER_LEN = [36, 30, 22]
LEADER_THICK = [13, 10, 7]
LEADER_REST = [1.38, -0.10, -0.18]

ROOT_COUNT = 7
ROOT_LEN = [20, 17]
ROOT_THICK = [13, 8]
STOMP_RANGE_BLOCKS = 3.6       # must match the reach of the roots; checked below

KNOT_BEARING = 14.0            # low on the trunk, on limb 0's side
MAW_BEARING = 0.0


def rnd(n):
    """Deterministic 0..1 from an integer. No RNG object: the same tree every run, forever."""
    h = (n * 2654435761 ^ GEO_SEED) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def jit(n, amount):
    return (rnd(n) - 0.5) * 2.0 * amount


# --- linear algebra (ModelPart convention: R = Rz * Ry * Rx) --------------------------------------

def rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mm(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(a, v):
    return [sum(a[i][k] * v[k] for k in range(3)) for i in range(3)]


def tr(a):
    return [[a[j][i] for j in range(3)] for i in range(3)]


def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def scl(a, k):
    return [v * k for v in a]


I3 = [[1, 0, 0], [0, 1, 0], [0, 0, 1]]


def euler_zyx(R):
    """(x, y, z) with R = Rz(z) Ry(y) Rx(x)."""
    y = math.asin(max(-1.0, min(1.0, -R[2][0])))
    x = math.atan2(R[2][1], R[2][2])
    z = math.atan2(R[1][0], R[0][0])
    return [x, y, z]


def bearing_dir(a_deg):
    a = math.radians(a_deg)
    return [-math.sin(a), 0.0, -math.cos(a)]


def yaw_of(a_deg):
    return ry(math.radians(a_deg) + math.pi)


# --- scene graph ----------------------------------------------------------------------------------

class Cube:
    __slots__ = ("name", "box", "kind", "uv")

    def __init__(self, name, box, kind):
        self.name, self.box, self.kind = name, box, kind
        self.uv = None

    def size(self):
        return self.box[3:6]


class Bone:
    """A bone placed by its WORLD rest pivot P and WORLD rest rotation R (model space)."""

    def __init__(self, name, parent, P, R, euler=None):
        self.name, self.parent, self.P, self.R = name, parent, list(P), R
        self.cubes, self.children = [], []
        # An explicit local (x, y, z). Animated chain bones need it: the generic decomposition keeps
        # y inside +-90 degrees, so a limb pointing backwards comes out as x' = pi - x, z = pi, and
        # "add to xRot to raise" silently becomes "add to xRot to lower" on half the limbs.
        self.euler = euler
        if parent is not None:
            parent.children.append(self)

    def local(self):
        if self.parent is None:
            return list(self.P), euler_zyx(self.R)
        Rp = self.parent.R
        off = mv(tr(Rp), sub(self.P, self.parent.P))
        if self.euler is not None:
            return off, list(self.euler)
        return off, euler_zyx(mm(tr(Rp), self.R))

    def box(self, name, x, y, z, w, h, d, kind):
        """Local addBox(x, y, z, w, h, d). Sizes are rounded to whole px so the box UV lands on
        texel boundaries; fractional sizes smear the paint across faces."""
        w, h, d = int(round(w)), int(round(h)), int(round(d))
        self.cubes.append(Cube(name, [x, y, z, w, h, d], kind))

    def at(self, local):
        return add(self.P, mv(self.R, local))


def walk(bone):
    yield bone
    for c in bone.children:
        yield from walk(c)


class Tree:
    def __init__(self):
        self.root = Bone("root", None, [0, 24, 0], I3)
        self.willow = Bone("willow", self.root, [0, 24, 0], I3)
        self.trunk, self.limbs, self.boughs, self.roots = [], [], [], []
        self.curtains = []        # (bone, length px)
        self.maw_jaw = None


# --- dressing helpers -----------------------------------------------------------------------------

def leaf_clump(bone, name, centre, size, seed, flat=0.72):
    s = size
    bone.box(name, centre[0] - s / 2.0, centre[1] - s * flat / 2.0, centre[2] - s / 2.0,
             s, s * flat, s, "leaf")


def curtain(tree, host, name, local, length, width, seed):
    """Two crossed vertical planes hanging from `local` on `host`, as their own bone so the model
    can keep them plumb while the branch above them whips about. This is what makes a willow a
    willow: without the hanging streamers it is an oak."""
    P = host.at(local)
    phi = rnd(seed) * math.pi
    b = Bone(name, host, P, ry(phi))
    b.box(name + "_a", -width / 2.0, 0.0, 0.0, width, length, 0, "curtain")
    b.box(name + "_b", 0.0, 0.0, -width / 2.0, 0, length - 4, width, "curtain")
    tree.curtains.append((b, length))
    return b


def chain(tree, parent, prefix, P0, yaw_deg, rests, lens, thicks, kind_from=4, seed=0,
          knuckles=True):
    """A segmented limb authored along local +Z, segment k pitched by rests[k] relative to k-1."""
    bones = []
    # The parent frame is a pure yaw (the willow, a trunk ring): express the limb's world yaw in it.
    Rp = parent.R
    parent_yaw = math.atan2(Rp[0][2], Rp[2][2])
    local_yaw = math.radians(yaw_deg) + math.pi - parent_yaw
    R = mm(Rp, mm(ry(local_yaw), rx(rests[0])))
    P = list(P0)
    host = parent
    for k in range(len(lens)):
        if k:
            R = mm(R, rx(rests[k]))
        name = "%s_s%d" % (prefix, k + 1)
        b = Bone(name, host, P, R, euler=[rests[k], local_yaw if k == 0 else 0.0, 0.0])
        t, L = thicks[k], lens[k]
        kind = "twig" if k >= kind_from else "bark"
        b.box(name + "_c", -t / 2.0, -t / 2.0, -1.0, t, t, L + 2, kind)
        if knuckles and k and t >= 7:
            kt = t + 2
            b.box(name + "_k", -kt / 2.0, -kt / 2.0 + jit(seed + k, 1.0), -kt / 2.0 + 1.0,
                  kt, kt, kt - 2, kind)
        bones.append(b)
        P = b.at([0, 0, L])
        host = b
    return bones


def fork(tree, seg, name, z, side, length, thick, seed, leaves=True, curtain_len=0):
    """A short static fork off a segment: a twig, a leaf clump, maybe a streamer."""
    Rw = mm(seg.R, mm(ry(side * (0.55 + rnd(seed) * 0.35)), rx(-0.15 + jit(seed + 1, 0.25))))
    b = Bone(name, seg, seg.at([0, 0, z]), Rw)
    b.box(name + "_c", -thick / 2.0, -thick / 2.0, 0.0, thick, thick, length, "twig")
    if leaves:
        leaf_clump(b, name + "_leaf", [0, -2, length * 0.8], 12 + round(rnd(seed + 2) * 2) * 2,
                   seed + 3)
    if curtain_len:
        curtain(tree, b, name + "_cur", [0, thick / 2.0, length * 0.75], curtain_len, 12, seed + 4)
    return b


# --- build ----------------------------------------------------------------------------------------

def build():
    t = Tree()

    # Static flare where the bole meets the ground: low wide buttresses, so the trunk is grown into
    # the earth rather than standing on it.
    flare = Bone("flare", t.willow, [0, 24, 0], I3)
    flare.box("flare_plate", -31, -5, -31, 62, 6, 62, "root")
    for i in range(8):
        a = i * 45.0 + jit(400 + i, 12)
        d = bearing_dir(a)
        r = 24 + rnd(410 + i) * 6
        hw = 7 + rnd(420 + i) * 4
        h = 12 + rnd(430 + i) * 12
        cx, cz = d[0] * r, d[2] * r
        flare.box("flare_b%d" % (i + 1), cx - hw, -h, cz - hw, hw * 2, h + 1, hw * 2, "root")

    # Surface roots: real bones, because ROOT_STOMP heaves them up and slams them down, and the
    # stomp's damage ring is exactly as wide as they reach.
    for i in range(ROOT_COUNT):
        a = i * 360.0 / ROOT_COUNT + 20.0 + jit(440 + i, 14)
        d = bearing_dir(a)
        P = [d[0] * 22, 24 - 5, d[2] * 22]
        bones = chain(t, t.willow, "root%d" % (i + 1), P, a, [-0.16, -0.22],
                      ROOT_LEN, ROOT_THICK, kind_from=9, seed=450 + i * 3, knuckles=False)
        for b in bones:
            for c in b.cubes:
                c.kind = "root"
        t.roots.append(bones)

    # The bole.
    parent, base = t.willow, 0
    for i in range(len(RING_H)):
        dx, dz = RING_DRIFT[i]
        P = [dx, 24 - base, dz]
        b = Bone("trunk%d" % (i + 1), parent, P, ry(RING_TWIST * i))
        H, hw = RING_H[i], RING_HW[i]
        b.box("trunk%d_c" % (i + 1), -hw, -H - 1, -hw, hw * 2, H + 2, hw * 2, "bark")
        # Fluting: a ridge on every side, wandering ring to ring, so the bole's silhouette is
        # ribbed and lumpy instead of a smooth stepped cone.
        for k in range(4):
            rw = int(hw * (0.55 + rnd(500 + i * 4 + k) * 0.35))
            off = jit(520 + i * 4 + k, hw * 0.35)
            dep = 3 + int(rnd(540 + i * 4 + k) * 3)
            # Ridges run past both ends of their ring, so the seams between rings are bridged by
            # continuous vertical ribs instead of showing as rings stacked on rings.
            hh = H + 8
            y0 = -H - 3
            if k == 0:
                b.box("trunk%d_r%d" % (i + 1, k), hw - 1, y0, off - rw / 2.0, dep, hh, rw, "bark")
            elif k == 1:
                b.box("trunk%d_r%d" % (i + 1, k), -hw - dep + 1, y0, off - rw / 2.0, dep, hh, rw, "bark")
            elif k == 2:
                b.box("trunk%d_r%d" % (i + 1, k), off - rw / 2.0, y0, hw - 1, rw, hh, dep, "bark")
            else:
                b.box("trunk%d_r%d" % (i + 1, k), off - rw / 2.0, y0, -hw - dep + 1, rw, hh, dep, "bark")
        # A burl or two.
        if i in (1, 3, 4):
            a = rnd(560 + i) * 360.0
            d = mv(tr(b.R), bearing_dir(a))
            s = 8 + int(rnd(570 + i) * 5)
            b.box("trunk%d_burl" % (i + 1), d[0] * (hw + 1) - s / 2.0, -H * 0.6 - s / 2.0,
                  d[2] * (hw + 1) - s / 2.0, s, s, s, "bark")
        t.trunk.append(b)
        parent, base = b, base + H
    top_y = base

    # The knot: a pale raised gnarl low on the bole, on KNOT_BEARING. Pressing it is checked
    # against the same bearing, from WhompingWillowRig.KNOT_BEARING.
    ring = t.trunk[0]
    d = bearing_dir(KNOT_BEARING)
    hw = RING_HW[0]
    knot = Bone("knot", ring, add(ring.P, [d[0] * (hw + 3), -12, d[2] * (hw + 3)]),
                yaw_of(KNOT_BEARING))
    knot.box("knot_boss", -7, -7, -4, 14, 14, 6, "knot")
    knot.box("knot_eye", -4, -4, 1, 8, 8, 4, "knot")
    knot.box("knot_lip", -9, -9, -3, 18, 4, 4, "knot")

    # The maw on the front of ring 3.
    t.maw_jaw = build_maw(t, t.trunk[2])

    # Striking limbs.
    for li in range(4):
        host = t.trunk[LIMB_HOST[li]]
        hb = sum(RING_H[:LIMB_HOST[li]])
        d = bearing_dir(LIMB_BEARING[li])
        hw = RING_HW[LIMB_HOST[li]]
        P = [host.P[0] + d[0] * (hw - 5), 24 - hb - LIMB_LIFT[li], host.P[2] + d[2] * (hw - 5)]
        sc = LIMB_SCALE[li]
        lens = [round(v * sc) for v in LIMB_LEN]
        thick = [max(4, round(v * (0.9 + sc * 0.1))) for v in LIMB_THICK]
        rests = [v + jit(600 + li * 8 + k, 0.05) for k, v in enumerate(LIMB_REST)]
        segs = chain(t, host, "limb%d" % (li + 1), P, LIMB_BEARING[li], rests, lens, thick,
                     kind_from=4, seed=620 + li * 10)
        for k, seg in enumerate(segs):
            L, th = lens[k], thick[k]
            n = 700 + li * 40 + k * 5
            if 1 <= k <= 4:
                fork(t, seg, "limb%d_f%d" % (li + 1, k), L * 0.55, 1 if (k + li) % 2 else -1,
                     12 + k * 2, max(3, th - 5), n, leaves=k >= 2,
                     curtain_len=(16 + k * 4) if k >= 2 else 0)
            if k >= 2:
                leaf_clump(seg, "limb%d_leaf%d" % (li + 1, k), [0, -th / 2.0 - 2, L * 0.6],
                           14 + 2 * (k % 2), n + 1)
                curtain(t, seg, "limb%d_cur%d" % (li + 1, k), [jit(n + 2, 2), th / 2.0, L * 0.3],
                        14 + k * 4, 12, n + 3)
        t.limbs.append((segs, lens, thick))

    # Crown: four boughs and a leader off the top ring, each carrying arched secondaries that drop
    # long streamers. This is the canopy you see from a hundred blocks away.
    topring = t.trunk[-1]
    P_top = [topring.P[0], 24 - top_y + 2, topring.P[2]]
    specs = [("bough%d" % (i + 1), BOUGH_BEARING[i], BOUGH_REST, BOUGH_LEN, BOUGH_THICK, 7)
             for i in range(4)]
    specs.append(("leader", 20.0, LEADER_REST, LEADER_LEN, LEADER_THICK, 3))
    for bi, (name, bear, rests, lens, thick, off) in enumerate(specs):
        d = bearing_dir(bear)
        P = [P_top[0] + d[0] * off, P_top[1], P_top[2] + d[2] * off]
        rr = [v + jit(800 + bi * 8 + k, 0.06) for k, v in enumerate(rests)]
        segs = chain(t, topring, name, P, bear + jit(810 + bi, 8), rr, lens, thick,
                     kind_from=2, seed=820 + bi * 10)
        for k, seg in enumerate(segs):
            n = 900 + bi * 60 + k * 12
            L = lens[k]
            # Big leaf masses along the bough itself.
            leaf_clump(seg, "%s_leaf%d" % (name, k + 1), [jit(n, 3), -thick[k] / 2.0 - 4, L * 0.55],
                       22 + 2 * int(rnd(n + 1) * 3), n + 2)
            # Arched secondaries, alternating sides, each ending in a streamer curtain.
            for j, side in enumerate((-1, 1)):
                if k == 0 and j == 0 and name != "leader":
                    continue
                build_secondary(t, seg, "%s_x%d%d" % (name, k + 1, j + 1), L * (0.45 + j * 0.3),
                                side, n + 3 + j * 4, big=(k < 2))
        t.boughs.append(segs)
    return t


def build_secondary(t, seg, name, z, side, seed, big):
    """An arched secondary limb: up and out, then over and down, with leaf clumps along it and a
    long streamer hanging from where it turns over."""
    yaw = side * (0.7 + rnd(seed) * 0.6)
    R1 = mm(seg.R, mm(ry(yaw), rx(-0.55 + jit(seed + 1, 0.2))))
    # The parent bough points steeply up; the secondary is authored to come off it nearly level.
    la = 22 if big else 16
    lb = 18 if big else 13
    a = Bone(name + "a", seg, seg.at([0, 0, z]), R1)
    a.box(name + "a_c", -3, -3, 0, 6, 6, la, "twig")
    leaf_clump(a, name + "a_leaf", [0, -4, la * 0.7], 20 if big else 16, seed + 2)
    Rb = mm(R1, rx(-0.75 + jit(seed + 3, 0.2)))
    b = Bone(name + "b", a, a.at([0, 0, la]), Rb)
    b.box(name + "b_c", -2, -2, 0, 4, 4, lb, "twig")
    leaf_clump(b, name + "b_leaf", [0, -3, lb * 0.6], 16 if big else 14, seed + 4)
    curtain(t, a, name + "a_cur", [0, 3, la * 0.85], 34 if big else 26, 16, seed + 5)
    curtain(t, b, name + "b_cur", [0, 2, lb], 46 if big else 34, 14, seed + 6)


def build_maw(t, ring):
    """A split in the bole with splinter teeth: a heavy brow, cheeks, a recessed throat and a jaw
    that drops. Faces MAW_BEARING."""
    hw = RING_HW[2]
    d = bearing_dir(MAW_BEARING)
    R = yaw_of(MAW_BEARING)          # local +Z points out of the trunk face
    base = ring.at([0, 0, 0])
    P = [ring.P[0] + d[0] * (hw - 2), ring.P[1] - 9, ring.P[2] + d[2] * (hw - 2)]
    maw = Bone("maw", ring, P, R)
    maw.box("maw_gum", -9, -8, -6, 18, 12, 4, "maw")
    maw.box("maw_brow", -13, -14, -2, 26, 6, 7, "bark")
    maw.box("maw_lid", -10, -17, -1, 20, 4, 5, "bark")
    maw.box("maw_cheekl", -15, -10, -2, 5, 16, 6, "bark")
    maw.box("maw_cheekr", 10, -10, -2, 5, 16, 6, "bark")
    for i, (tx, tl) in enumerate(((-8, 5), (-4, 8), (0, 4), (4, 7), (8, 5))):
        maw.box("maw_tu%d" % (i + 1), tx - 1, -8, 1, 2 + (i % 2), tl, 2, "tooth")
    jaw = Bone("maw_jaw", maw, maw.at([0, 4, 0]), R)
    jaw.box("maw_lower", -12, 0, -2, 24, 6, 8, "bark")
    for i, (tx, tl) in enumerate(((-6, 6), (-2, 4), (2, 7), (6, 4))):
        jaw.box("maw_tl%d" % (i + 1), tx - 1, -tl, 2, 2 + (i % 2), tl, 2, "tooth")
    return jaw


# --- UV packing -----------------------------------------------------------------------------------
# Cubes that share (kind, size) and are not hero pieces share one footprint. The painter paints a
# shared rect once.

SHAREABLE = ("leaf", "curtain", "twig", "root", "tooth")


def pack(cubes):
    classes, items = {}, []
    for c in cubes:
        w, h, d = c.size()
        key = (c.kind, w, h, d) if c.kind in SHAREABLE else ("!", c.name)
        if key not in classes:
            classes[key] = []
            items.append((key, w, h, d))
        classes[key].append(c)
    # tallest first: a shelf packer wastes least that way
    items.sort(key=lambda it: (-(it[3] + it[2]), -(2 * it[3] + 2 * it[1])))
    x, y, shelf = 0, 0, 0
    used = 0
    for key, w, h, d in items:
        fw, fh = 2 * d + 2 * w, d + h
        if x + fw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        if y + fh > TEX_H:
            raise SystemExit("UV overflow: %dx%d texture is too small" % (TEX_W, TEX_H))
        for c in classes[key]:
            c.uv = [x, y]
        x += fw
        shelf = max(shelf, fh)
        used += fw * fh
    return used, len(items)


def faces(c):
    """Baked per-face rectangles (verified against ModelPart.Cube in the 1.21 sources)."""
    u, v = c.uv
    w, h, d = c.size()
    r = {
        "down": [u + d, v, u + d + w, v + d],
        "up": [u + d + w, v, u + d + w + w, v + d],
        "west": [u, v + d, u + d, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "east": [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
    }
    return {k: {"uv": val, "texture": 0} for k, val in r.items()}


# --- emit .bbmodel --------------------------------------------------------------------------------

def emit_bbmodel(tree):
    """Blockbench wants absolute UNROTATED coordinates, +Y up, ground at 0. Java offsets are
    relative to the parent's local frame, so the unrotated absolute position is the plain sum of
    local offsets down the chain."""
    uid = [0]

    def nid():
        uid[0] += 1
        return "%08x-0000-4000-8000-%012x" % (uid[0], uid[0])

    elements, outliner = [], []

    def emit(bone, into, abs_parent):
        off, rot = bone.local()
        A = add(abs_parent, off) if bone.parent is not None else list(off)
        origin = [A[0], 24.0 - A[1], A[2]]
        g = {"name": bone.name, "origin": [round(v, 4) for v in origin],
             "rotation": [round(-math.degrees(rot[0]), 4), round(math.degrees(rot[1]), 4),
                          round(-math.degrees(rot[2]), 4)],
             "uuid": nid(), "export": True, "isOpen": False, "visibility": True, "children": []}
        for c in bone.cubes:
            x, y, z, w, h, d = c.box
            fr = [A[0] + x, 24.0 - (A[1] + y + h), A[2] + z]
            to = [A[0] + x + w, 24.0 - (A[1] + y), A[2] + z + d]
            e = {"name": c.name, "kind": c.kind, "box_uv": True, "rescale": False,
                 "locked": False, "from": [round(v, 4) for v in fr],
                 "to": [round(v, 4) for v in to], "autouv": 0, "color": 0,
                 "origin": [round(v, 4) for v in origin], "uv_offset": c.uv,
                 "faces": faces(c), "type": "cube", "uuid": nid()}
            elements.append(e)
            g["children"].append(e["uuid"])
        for ch in bone.children:
            emit(ch, g["children"], A)
        into.append(g)

    emit(tree.root, outliner, [0, 0, 0])
    model = {
        "meta": {"format_version": "4.5", "model_format": "modded_entity", "box_uv": True},
        "name": "whomping_willow", "model_identifier": "",
        "modded_entity_version": "1.17", "modded_entity_flip_y": True,
        "visible_box": [4, 12, 0],
        "resolution": {"width": TEX_W, "height": TEX_H},
        "elements": elements, "outliner": outliner, "textures": [], "animations": [],
    }
    os.makedirs(os.path.dirname(OUT_BB), exist_ok=True)
    with open(OUT_BB, "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=1)
        fh.write("\n")


# --- emit Java ------------------------------------------------------------------------------------

def f(v):
    v = round(float(v) + 0.0, 4)
    if v == 0:
        v = 0.0
    return ("%gF" % v) if v != int(v) else ("%.1fF" % v)


def java_mesh(tree, bones):
    idx = {id(b): i for i, b in enumerate(bones)}
    out = ["    public static LayerDefinition createBodyLayer() {",
           "        MeshDefinition mesh = new MeshDefinition();",
           "        PartDefinition top = mesh.getRoot();", ""]
    for b in bones:
        off, rot = b.local()
        parent_var = "top" if b.parent is None else "b%d" % idx[id(b.parent)]
        cl = ["CubeListBuilder.create()"]
        for c in b.cubes:
            x, y, z, w, h, d = c.box
            cl.append("\n                .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)"
                      % (c.uv[0], c.uv[1], f(x), f(y), f(z), f(w), f(h), f(d)))
        pose = ("PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)"
                % (f(off[0]), f(off[1]), f(off[2]), f(rot[0]), f(rot[1]), f(rot[2])))
        decl = "PartDefinition b%d = " % idx[id(b)] if b.children else ""
        out.append("        %s%s.addOrReplaceChild(\"%s\", %s,\n                %s);"
                   % (decl, parent_var, b.name, "".join(cl), pose))
    out += ["", "        return LayerDefinition.create(mesh, %d, %d);" % (TEX_W, TEX_H), "    }"]
    return "\n".join(out)


def jarr_f(vals, per_line=6):
    s = [f(v) for v in vals]
    lines = [", ".join(s[i:i + per_line]) for i in range(0, len(s), per_line)]
    return "{\n            " + ",\n            ".join(lines) + "}"


def jarr_i(vals, per_line=16):
    s = [str(v) for v in vals]
    lines = [", ".join(s[i:i + per_line]) for i in range(0, len(s), per_line)]
    return "{" + ",\n            ".join(lines) + "}"


def java_rig(tree, bones):
    idx = {id(b): i for i, b in enumerate(bones)}
    names = ", ".join('"%s"' % b.name for b in bones)
    parents = [(-1 if b.parent is None else idx[id(b.parent)]) for b in bones]
    offs, rests = [], []
    for b in bones:
        off, rot = b.local()
        offs += off
        rests += rot
    limb = [[idx[id(s)] for s in segs] for segs, _, _ in tree.limbs]
    limb_len = [[float(v) for v in lens] for _, lens, _ in tree.limbs]
    limb_rad = [[th / 2.0 for th in thick] for _, _, thick in tree.limbs]
    roots = [[idx[id(s)] for s in r] for r in tree.roots]
    boughs = [[idx[id(s)] for s in r] for r in tree.boughs]
    curt = [idx[id(b)] for b, _ in tree.curtains]
    curt_len = [float(L) for _, L in tree.curtains]
    trunk = [idx[id(b)] for b in tree.trunk]

    def nested_i(rows):
        return "{\n            " + ",\n            ".join(jarr_i(r) for r in rows) + "}"

    def nested_f(rows):
        return "{\n            " + ",\n            ".join(
            "{" + ", ".join(f(v) for v in r) + "}" for r in rows) + "}"

    out = [
        "    public static final int BONES = %d;" % len(bones),
        "    public static final String[] NAME = {%s};" % names,
        "    public static final int[] PARENT = %s;" % jarr_i(parents),
        "    /** Local pivot offsets, model px, three per bone. */",
        "    static final float[] OFF = %s;" % jarr_f(offs),
        "    /** Rest rotations, radians (x, y, z), three per bone. */",
        "    public static final float[] REST = %s;" % jarr_f(rests),
        "    public static final int WILLOW = %d;" % idx[id(tree.willow)],
        "    public static final int MAW_JAW = %d;" % idx[id(tree.maw_jaw)],
        "    public static final int[] TRUNK = %s;" % jarr_i(trunk),
        "    public static final int[][] LIMB = %s;" % nested_i(limb),
        "    /** Segment lengths, px. The tip of segment k is (0, 0, LIMB_LEN[l][k]) in its frame. */",
        "    static final float[][] LIMB_LEN = %s;" % nested_f(limb_len),
        "    /** Segment half-thickness, px. */",
        "    static final float[][] LIMB_RAD = %s;" % nested_f(limb_rad),
        "    public static final float[] LIMB_BEARING = {%s};" % ", ".join(f(v) for v in LIMB_BEARING),
        "    public static final int[][] ROOT = %s;" % nested_i(roots),
        "    public static final int[][] BOUGH = %s;" % nested_i(boughs),
        "    public static final int[] CURTAIN = %s;" % jarr_i(curt),
        "    static final float[] CURTAIN_LEN = %s;" % jarr_f(curt_len, 10),
        "    public static final float KNOT_BEARING = %s;" % f(KNOT_BEARING),
        "    public static final float MODEL_SCALE = %s;" % f(MODEL_SCALE),
        "    /** Horizontal reach of the surface roots, blocks. ROOT_STOMP's damage ring. */",
        "    public static final float STOMP_RANGE = %s;" % f(STOMP_RANGE_BLOCKS),
    ]
    return "\n".join(out)


def splice(path, begin, end, body):
    src = open(path, encoding="utf-8").read()
    if begin not in src or end not in src:
        raise SystemExit("markers %r / %r not found in %s" % (begin, end, path))
    head = src[:src.index(begin) + len(begin)]
    tail = src[src.index(end):]
    open(path, "w", encoding="utf-8", newline="\n").write(head + "\n" + body + "\n" + tail)


# --- checks ---------------------------------------------------------------------------------------

def world_points(bone):
    """Rest-pose corners of every cube under `bone`, in model px."""
    pts = []
    for b in walk(bone):
        for c in b.cubes:
            x, y, z, w, h, d = c.box
            for i in range(8):
                pts.append(b.at([x + (w if i & 1 else 0), y + (h if i & 2 else 0),
                                 z + (d if i & 4 else 0)]))
    return pts


def report(tree, bones, cubes, used, footprints):
    pts = world_points(tree.root)
    top = max(24 - p[1] for p in pts) / PX
    spread = max(math.hypot(p[0], p[2]) for p in pts) / PX
    print("  %d cubes in %d bones, %d UV footprints, UV occupancy %.0f%% of %dx%d"
          % (len(cubes), len(bones), footprints, 100.0 * used / (TEX_W * TEX_H), TEX_W, TEX_H))
    print("  height %.2f blocks, widest %.2f blocks from the axis" % (top, spread))
    for li, (segs, lens, _) in enumerate(tree.limbs):
        tip = segs[-1].at([0, 0, lens[-1]])
        sh = segs[0].P
        reach = sum(lens) / PX
        print("  limb%d  bearing %5.1f  shoulder h %.2f r %.2f   rest tip h %.2f r %.2f   chain %.2f"
              % (li + 1, LIMB_BEARING[li], (24 - sh[1]) / PX, math.hypot(sh[0], sh[2]) / PX,
                 (24 - tip[1]) / PX, math.hypot(tip[0], tip[2]) / PX, reach))
    rr = []
    for r in tree.roots:
        b = r[-1]
        tip = b.at([0, 0, ROOT_LEN[-1]])
        rr.append(math.hypot(tip[0], tip[2]) / PX)
    print("  root reach %.2f..%.2f blocks (STOMP_RANGE %.2f)" % (min(rr), max(rr), STOMP_RANGE_BLOCKS))
    if abs(sum(rr) / len(rr) - STOMP_RANGE_BLOCKS) > 0.35:
        raise SystemExit("roots reach %.2f on average but STOMP_RANGE is %.2f -- the stomp would hit "
                         "where no root lands" % (sum(rr) / len(rr), STOMP_RANGE_BLOCKS))


# --- main -----------------------------------------------------------------------------------------

if __name__ == "__main__":
    tree = build()
    bones = list(walk(tree.root))
    cubes = [c for b in bones for c in b.cubes]
    names = [b.name for b in bones] + [c.name for c in cubes]
    dupes = sorted({n for n in names if names.count(n) > 1})
    if dupes:
        raise SystemExit("duplicate names: %s" % dupes)
    used, footprints = pack(cubes)
    emit_bbmodel(tree)
    splice(OUT_JAVA, "    // <<< GENERATED GEOMETRY", "    // >>> GENERATED GEOMETRY",
           java_mesh(tree, bones))
    splice(OUT_RIG, "    // <<< GENERATED RIG", "    // >>> GENERATED RIG", java_rig(tree, bones))
    print("wrote %s, %s, %s" % tuple(os.path.relpath(p, ROOT) for p in (OUT_BB, OUT_JAVA, OUT_RIG)))
    report(tree, bones, cubes, used, footprints)
