"""Build the Living Statue from one spec: mesh, solved key poses, texture, glow layer, .bbmodel.

    python build_scripts/generate_living_statue_model.py [--preview DIR]

The statue is a stone knight in plate with a halberd, in the manner of the Hogwarts suits of
armour that step off their plinths in the Battle of Hogwarts: dead still as scenery, then stiff,
heavy and mechanical once Piertotum Locomotor (or an intruder) sets it going.

What this script owns
---------------------
  * PARTS        the bone tree and every cube, in absolute model space
  * POSES        the key poses, written as INTENT (where the grip is, which way the blade points,
                 where the feet stand) and SOLVED here into joint angles -- nothing is eyeballed
  * the texture  painted per cube kind onto an auto-packed box-UV layout
  * the glow     the visor slit, drawn full-bright by LivingStatueModel's eyes layer
  * Java         the generated sections of LivingStatueModel.java (between the GENERATED markers):
                 createBodyLayer() and the key-pose tables. The animation code around them is
                 hand-written and reads those tables.

Coordinates are Minecraft model space throughout: +Y is DOWN, the floor is y=24, -Z is the way
the statue faces and its right side is -X. Rotations compose as Rz*Ry*Rx, which is what
ModelPart.translateAndRotate does.

Why poses are solved, not tuned: the previous statue was a flat rig (every limb a sibling of the
root) with a kneel swept by eye. Leaning the torso tore it away from the shoulders and head, and
four kneels in a row shipped with a floating figure, crossed legs or a buried boot. Here the
torso carries the head and arms, the hands are put where the halberd is by IK, the soles are put
on the floor by IK, and audit() re-checks contact and ground clearance on every run.
"""

import json
import math
import os
import random
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BB_OUT = os.path.join(ROOT, "blockbench_models", "living_statue.bbmodel")
JAVA_DIR = os.path.join(ROOT, "src", "main", "java", "com", "hogwartsmagic")
JAVA_OUT = os.path.join(JAVA_DIR, "client", "LivingStatueModel.java")
POSES_OUT = os.path.join(JAVA_DIR, "client", "LivingStatuePoses.java")
ENTITY_SRC = os.path.join(JAVA_DIR, "entity", "LivingStatueEntity.java")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "hogwartsmagic",
                       "textures", "entity")
TEX_OUT = os.path.join(TEX_DIR, "living_statue.png")
GLOW_OUT = os.path.join(TEX_DIR, "living_statue_glow.png")

TEX_W, TEX_H = 128, 128
FLOOR = 24.0
DEG = math.pi / 180.0

# --- the rig --------------------------------------------------------------------------------
# bone: (parent, pivot(abs), rest rotation in degrees or None)
BONES = {
    "root":           (None,            (0.0, 0.0, 0.0), None),
    "hips":           ("root",          (0.0, 4.0, 0.0), None),
    "torso":          ("hips",          (0.0, 4.0, 0.0), None),
    "head":           ("torso",         (0.0, -13.0, 0.0), None),
    "right_arm":      ("torso",         (-9.0, -11.0, 0.0), None),
    "right_forearm":  ("right_arm",     (-9.0, -1.5, 0.0), None),
    "halberd":        ("right_forearm", (-9.0, 6.5, 0.0), None),
    "left_arm":       ("torso",         (9.0, -11.0, 0.0), None),
    "left_forearm":   ("left_arm",      (9.0, -1.5, 0.0), None),
    # Hangs off the belt hilt-forward, tip back. A fixed pose, never animated.
    "sword":          ("hips",          (7.5, 1.0, -0.5), (20.0, 0.0, 0.0)),
    "right_leg":      ("root",          (-3.5, 5.0, 0.0), None),
    "right_shin":     ("right_leg",     (-3.5, 14.0, 0.0), None),
    "right_foot":     ("right_shin",    (-3.5, 21.5, 0.0), None),
    "left_leg":       ("root",          (3.5, 5.0, 0.0), None),
    "left_shin":      ("left_leg",      (3.5, 14.0, 0.0), None),
    "left_foot":      ("left_shin",     (3.5, 21.5, 0.0), None),
}
ORDER = list(BONES)

# Limb lengths the IK works with, read off the pivots above.
UPPER_ARM = 9.5          # shoulder (y -11) to elbow (y -1.5)
FOREARM = 8.0            # elbow to the centre of the fist (y 6.5), where a haft passes through
THIGH = 9.0              # hip (y 5) to knee (y 14)
SHIN = 7.5               # knee to ankle (y 21.5); the sole is 2.5 below the ankle
HAND = (0.0, 8.0, 0.0)   # fist centre in forearm space, relative to the elbow
HAFT_UP = 24             # haft above the grip, to the socket
HAFT_DOWN = 24           # haft below the grip, to the butt cap

# cube: (bone, name, origin(abs min corner), size(int w,h,d), kind)
CUBES = []


def cube(bone, name, origin, size, kind):
    CUBES.append((bone, name, origin, size, kind))


def mirror(bone, name, origin, size, kind):
    """The left-hand twin of a right-hand cube: mirrored across x=0."""
    cube(bone, name, (-origin[0] - size[0], origin[1], origin[2]), size, kind)


cube("head", "helm",       (-4.5, -21.0, -4.5), (9, 8, 9), "helm")
cube("head", "faceplate",  (-3.5, -19.5, -5.5), (7, 6, 1), "visor")
cube("head", "crest",      (-1.0, -23.0, -3.5), (2, 2, 8), "crest")
cube("head", "crest_tail", (-1.0, -21.0, 4.5),  (2, 5, 1), "crest")

cube("torso", "chest",   (-6.5, -13.0, -4.5), (13, 9, 9), "chest")
cube("torso", "ridge",   (-0.5, -12.0, -5.5), (1, 7, 1),  "ridge")
cube("torso", "abdomen", (-5.5, -4.0, -3.5),  (11, 5, 7), "fauld")

cube("hips", "belt",  (-6.0, 0.0, -4.0),  (12, 2, 8), "belt")
cube("hips", "skirt", (-6.5, 2.0, -4.5),  (13, 3, 9), "skirt")

cube("sword", "pommel",     (6.5, -4.0, -1.5), (2, 1, 2),  "hilt")
cube("sword", "grip",       (7.0, -3.0, -1.0), (1, 3, 1),  "grip")
cube("sword", "crossguard", (6.5, 0.0, -2.5),  (2, 1, 4),  "hilt")
cube("sword", "scabbard",   (7.0, 1.0, -1.5),  (1, 13, 2), "scabbard")

for side, sign in (("right", 1), ("left", -1)):
    put = cube if sign == 1 else mirror
    s = side[0]
    put(side + "_arm", s + "_pauldron", (-13.0, -13.5, -4.5), (7, 5, 9), "pauldron")
    put(side + "_arm", s + "_lame",     (-12.5, -9.0, -4.0),  (6, 2, 8), "lame")
    put(side + "_arm", s + "_upper",    (-11.5, -8.0, -2.5),  (5, 7, 5), "upper")
    put(side + "_forearm", s + "_vambrace", (-11.5, -1.5, -2.5), (5, 6, 5), "vambrace")
    put(side + "_forearm", s + "_gauntlet", (-12.0, 4.5, -3.0),  (6, 4, 6), "gauntlet")
    put(side + "_leg", s + "_thigh",   (-6.5, 5.0, -3.0),  (6, 9, 6), "thigh")
    put(side + "_leg", s + "_tasset",  (-7.0, 5.5, -4.0),  (7, 4, 1), "tasset")
    put(side + "_shin", s + "_knee",   (-5.5, 12.5, -4.0), (4, 3, 1), "knee")
    put(side + "_shin", s + "_greave", (-6.0, 14.0, -2.5), (5, 7, 5), "greave")
    put(side + "_foot", s + "_sabaton", (-6.5, 21.0, -5.5), (6, 3, 8), "sabaton")

# The halberd, haft along Y through the fist. The head is three masses -- a crescent blade
# facing -Z, a beak behind it and a spike above -- because a polearm is recognised almost
# entirely by its silhouette at the top.
_g = 6.5
cube("halberd", "haft",       (-10.0, _g - HAFT_UP, -1.0), (2, HAFT_UP + HAFT_DOWN, 2), "haft")
cube("halberd", "butt",       (-10.5, _g + HAFT_DOWN, -1.5), (3, 1, 3), "metal")
cube("halberd", "socket",     (-10.5, _g - HAFT_UP - 2, -1.5), (3, 2, 3), "metal")
cube("halberd", "blade",      (-9.5, _g - HAFT_UP - 8.5, -6.0), (1, 7, 5), "blade")
cube("halberd", "blade_edge", (-9.5, _g - HAFT_UP - 9.5, -8.0), (1, 9, 2), "edge")
cube("halberd", "beak",       (-9.5, _g - HAFT_UP - 6.5, 1.0), (1, 2, 4), "blade")
cube("halberd", "spike",      (-9.5, _g - HAFT_UP - 13.5, -0.5), (1, 12, 1), "edge")


# --- UV packing -----------------------------------------------------------------------------
def pack_uvs():
    """Shelf-pack every box net. Nets are 2(d+w) x (d+h); none may share texels."""
    order = sorted(range(len(CUBES)), key=lambda i: -(CUBES[i][3][2] + CUBES[i][3][1]))
    uv = [None] * len(CUBES)
    x = y = shelf = 0
    for i in order:
        w, h, d = CUBES[i][3]
        nw, nh = 2 * (d + w), d + h
        if x + nw > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        uv[i] = (x, y)
        x += nw
        shelf = max(shelf, nh)
    if y + shelf > TEX_H:
        raise SystemExit("UV layout overflows %dx%d (needs %d rows)" % (TEX_W, TEX_H, y + shelf))
    return uv


UVS = pack_uvs()


# --- maths ----------------------------------------------------------------------------------
def rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def mm(A, B):
    return [[sum(A[i][k] * B[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def mv(A, v):
    return [sum(A[i][k] * v[k] for k in range(3)) for i in range(3)]


def tr(A):
    return [[A[j][i] for j in range(3)] for i in range(3)]


def rot(x, y, z):
    return mm(rz(z), mm(ry(y), rx(x)))


def euler(R):
    """Inverse of rot(): R = Rz Ry Rx -> (x, y, z)."""
    y = math.asin(max(-1.0, min(1.0, -R[2][0])))
    return math.atan2(R[2][1], R[2][2]), y, math.atan2(R[1][0], R[0][0])


def add(a, b):
    return [a[i] + b[i] for i in range(3)]


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def scale(a, k):
    return [x * k for x in a]


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def norm(a):
    n = math.sqrt(dot(a, a)) or 1.0
    return [x / n for x in a]


def frame(a, n):
    """Orthonormal basis as COLUMNS: a, n made perpendicular to a, and a x n."""
    a = norm(a)
    n = norm(sub(n, scale(a, dot(n, a))))
    c = cross(a, n)
    return [[a[i], n[i], c[i]] for i in range(3)]


# --- pose parameters (mirrored exactly by LivingStatueModel) ---------------------------------
# One flat float vector per key pose. Angles are radians. LivingStatueModel blends these vectors
# and then runs the same two IK passes this file runs, so the slots below are the contract.
SLOTS = ["drop", "root_z",
         "hips_x", "hips_y", "hips_z",
         "torso_x", "torso_y", "torso_z",
         "head_x", "head_y", "head_z", "head_dx", "head_dy", "head_dz",
         "rarm_x", "rarm_y", "rarm_z", "rfore_x",
         "hal_x", "hal_y", "hal_z", "hal_dx", "hal_dy", "hal_dz",
         "larm_x", "larm_y", "larm_z", "lfore_x",
         "two_hand", "grip_k",
         "rfoot_z", "lfoot_z", "rlift", "llift", "flat", "rfoot_p", "lfoot_p"]
S = {n: i for i, n in enumerate(SLOTS)}

ANKLE_Y = 21.5           # ankle pivot height with the sole on the floor
# Where the elbows point, torso space. Mostly BACK: an elbow pointed outward swings the upper arm
# out with it, and the pauldron riding on that bone flares up like a wing.
LEFT_POLE = (0.4, 0.3, 1.0)
RIGHT_POLE = (-0.4, 0.3, 1.0)


def arm_ik(shoulder, target, pole):
    """Two-bone arm IK in torso space -> (arm x, y, z, forearm x). Mirrored in Java (solveArm).

    The elbow is a hinge about the forearm's own X and only ever folds the fist FORWARD (negative
    forearm pitch). The whole chain is then swung so the fist lands on the target with the elbow
    pointing as near the pole as the target allows."""
    t = sub(target, shoulder)
    d = math.sqrt(dot(t, t))
    d = max(abs(UPPER_ARM - FOREARM) + 0.05, min(UPPER_ARM + FOREARM - 0.05, d))
    c = (UPPER_ARM ** 2 + FOREARM ** 2 - d * d) / (2.0 * UPPER_ARM * FOREARM)
    bend = -(math.pi - math.acos(max(-1.0, min(1.0, c))))
    hand = [0.0, UPPER_ARM + FOREARM * math.cos(bend), FOREARM * math.sin(bend)]
    elbow = [0.0, UPPER_ARM, 0.0]
    a = norm(hand)
    e_perp = sub(elbow, scale(a, dot(elbow, a)))
    local = frame(a, e_perp)
    world = frame(t, list(pole))
    R = mm(world, tr(local))
    x, y, z = euler(R)
    return x, y, z, bend


def leg_ik(hip_y, hip_z, ankle_y, ankle_z):
    """Planar two-bone leg IK -> (thigh x, shin x, shin world pitch). Mirrored in Java.

    Knees bend forward only. Out-of-reach targets straighten the leg towards them."""
    dy, dz = ankle_y - hip_y, ankle_z - hip_z
    d = math.sqrt(dy * dy + dz * dz)
    d = max(abs(THIGH - SHIN) + 0.05, min(THIGH + SHIN - 0.001, d))
    base = math.atan2(dz, dy)
    c = (THIGH ** 2 + d * d - SHIN ** 2) / (2.0 * THIGH * d)
    thigh = base - math.acos(max(-1.0, min(1.0, c)))
    ky, kz = hip_y + THIGH * math.cos(thigh), hip_z + THIGH * math.sin(thigh)
    shin_world = math.atan2(ankle_z - kz, ankle_y - ky)
    return thigh, shin_world - thigh, shin_world


def haft_point(p):
    """The point grip_k along the haft, torso space -- the left fist's target. Mirrored in Java."""
    sh = sub(BONES["right_arm"][1], BONES["torso"][1])
    Ra = rot(p[S["rarm_x"]], p[S["rarm_y"]], p[S["rarm_z"]])
    elbow = add(sh, mv(Ra, [0.0, UPPER_ARM, 0.0]))
    Rf = mm(Ra, rx(p[S["rfore_x"]]))
    grip = add(elbow, mv(Rf, add(HAND, [p[S["hal_dx"]], p[S["hal_dy"]], p[S["hal_dz"]]])))
    Rh = mm(Rf, rot(p[S["hal_x"]], p[S["hal_y"]], p[S["hal_z"]]))
    return add(grip, mv(Rh, [0.0, p[S["grip_k"]], 0.0]))


def resolve(p):
    """Key-pose vector -> per-bone (offset delta, rotation). Exactly what setupAnim writes."""
    p = list(p)
    if p[S["two_hand"]] > 0.0:
        sh = sub(BONES["left_arm"][1], BONES["torso"][1])
        ik = arm_ik(sh, haft_point(p), LEFT_POLE)
        w = min(1.0, p[S["two_hand"]])
        tri = (S["larm_x"], S["larm_y"], S["larm_z"])
        for i, v in zip(tri, slerp_euler([p[i] for i in tri], ik[:3], w)):
            p[i] = v
        p[S["lfore_x"]] += (ik[3] - p[S["lfore_x"]]) * w
    out = {b: ([0.0, 0.0, 0.0], [0.0, 0.0, 0.0]) for b in ORDER}
    out["root"] = ([0.0, p[S["drop"]], p[S["root_z"]]], [0.0, 0.0, 0.0])
    out["hips"] = ([0.0, 0.0, 0.0], [p[S["hips_x"]], p[S["hips_y"]], p[S["hips_z"]]])
    out["torso"] = ([0.0, 0.0, 0.0], [p[S["torso_x"]], p[S["torso_y"]], p[S["torso_z"]]])
    out["head"] = ([p[S["head_dx"]], p[S["head_dy"]], p[S["head_dz"]]],
                   [p[S["head_x"]], p[S["head_y"]], p[S["head_z"]]])
    out["right_arm"] = ([0.0] * 3, [p[S["rarm_x"]], p[S["rarm_y"]], p[S["rarm_z"]]])
    out["right_forearm"] = ([0.0] * 3, [p[S["rfore_x"]], 0.0, 0.0])
    out["halberd"] = ([p[S["hal_dx"]], p[S["hal_dy"]], p[S["hal_dz"]]],
                      [p[S["hal_x"]], p[S["hal_y"]], p[S["hal_z"]]])
    out["left_arm"] = ([0.0] * 3, [p[S["larm_x"]], p[S["larm_y"]], p[S["larm_z"]]])
    out["left_forearm"] = ([0.0] * 3, [p[S["lfore_x"]], 0.0, 0.0])
    rest = BONES["sword"][2]
    out["sword"] = ([0.0] * 3, [rest[0] * DEG, rest[1] * DEG, rest[2] * DEG])
    for side, zs, lift, pitch in (("right", "rfoot_z", "rlift", "rfoot_p"),
                                  ("left", "lfoot_z", "llift", "lfoot_p")):
        hip = BONES[side + "_leg"][1]
        thigh, shin, shin_w = leg_ik(hip[1], 0.0, ANKLE_Y - p[S[lift]] - p[S["drop"]],
                                     p[S[zs]] - p[S["root_z"]])
        out[side + "_leg"] = ([0.0] * 3, [thigh, 0.0, 0.0])
        out[side + "_shin"] = ([0.0] * 3, [shin, 0.0, 0.0])
        out[side + "_foot"] = ([0.0] * 3, [-shin_w * p[S["flat"]] + p[S[pitch]], 0.0, 0.0])
    return out


ROT_TRIPLES = [tuple(S[n + a] for a in ("_x", "_y", "_z"))
               for n in ("hips", "torso", "head", "rarm", "hal", "larm")]


def quat(x, y, z):
    """rot(x, y, z) = Rz Ry Rx as a quaternion (w, x, y, z)."""
    cx, sx = math.cos(x / 2), math.sin(x / 2)
    cy, sy = math.cos(y / 2), math.sin(y / 2)
    cz, sz = math.cos(z / 2), math.sin(z / 2)
    return (cz * cy * cx + sz * sy * sx, cz * cy * sx - sz * sy * cx,
            cz * sy * cx + sz * cy * sx, sz * cy * cx - cz * sy * sx)


def quat_euler(q):
    w, x, y, z = q
    R = [[1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y)],
         [2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x)],
         [2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)]]
    return euler(R)


def slerp_euler(a, b, t):
    """Blend two joint rotations along the short arc. Mirrored in Java (slerpInto).

    Lerping Euler angles is what the old statue did, and between two far-apart orientations it
    takes a path no joint would: the halberd tumbled end over end through its own wielder."""
    qa, qb = quat(*a), quat(*b)
    d = sum(qa[i] * qb[i] for i in range(4))
    if d < 0.0:
        qb, d = tuple(-v for v in qb), -d
    if d > 0.9995:
        q = tuple(qa[i] + (qb[i] - qa[i]) * t for i in range(4))
    else:
        th = math.acos(d)
        sa, sb = math.sin((1 - t) * th) / math.sin(th), math.sin(t * th) / math.sin(th)
        q = tuple(qa[i] * sa + qb[i] * sb for i in range(4))
    n = math.sqrt(sum(v * v for v in q))
    return quat_euler(tuple(v / n for v in q))


def blend(a, b, t):
    """Pose a -> b at t. Scalars lerp; joint rotations slerp. Mirrored in Java (blendInto)."""
    out = [a[i] + (b[i] - a[i]) * t for i in range(len(a))]
    for tri in ROT_TRIPLES:
        r = slerp_euler([a[i] for i in tri], [b[i] for i in tri], t)
        for i, v in zip(tri, r):
            out[i] = v
    return out


def angle_delta(a, b):
    d = (b - a) % (2 * math.pi)
    return d - 2 * math.pi if d > math.pi else d


def world(pose):
    """{bone: (origin, R)} in entity space, the way ModelPart walks the tree."""
    out = {}
    for b in ORDER:
        parent, piv, _ = BONES[b]
        off, r = pose[b]
        R = rot(*r)
        ppiv = BONES[parent][1] if parent else (0.0, 0.0, 0.0)
        local = add(sub(piv, ppiv), off)
        if parent is None:
            out[b] = (local, R)
        else:
            po, pR = out[parent]
            out[b] = (add(po, mv(pR, local)), mm(pR, R))
    return out


def to_world(W, bone, p_abs):
    o, R = W[bone]
    return add(o, mv(R, sub(p_abs, BONES[bone][1])))


# --- key poses: intent -> solved vectors ---------------------------------------------------
def solve(spec):
    """Turn a pose written as intent into the flat vector.

    spec keys (all optional):
      drop, root_z           the whole figure's sink and forward shift
      hips, torso, head      degrees (x, y, z)
      feet                   dict of rfoot_z, lfoot_z, rlift, llift, flat, rfoot_p, lfoot_p
      grip                   entity-space point the right fist goes to (IK)
      right                  (arm x, y, z, forearm x) degrees, instead of grip
      halberd_up/face        entity-space directions: towards the blade head, and the way the
                             blade's edge faces. "vertical" is up with the edge forward
      halberd_world          (centre point, up, face): the weapon dropped, placed in the world
      left                   (arm x, y, z, forearm x) degrees, or
      left_target            entity-space point for the left fist, or
      two_hand               grip_k: the left fist rides the haft that far from the grip
      head_world             (centre point, (x, y, z) degrees): the head knocked off and placed
    """
    p = [0.0] * len(SLOTS)
    p[S["flat"]] = 1.0
    p[S["drop"]] = spec.get("drop", 0.0)
    p[S["root_z"]] = spec.get("root_z", 0.0)
    for name in ("hips", "torso", "head"):
        v = spec.get(name, (0, 0, 0))
        for i, ax in enumerate("xyz"):
            p[S["%s_%s" % (name, ax)]] = v[i] * DEG
    for k, v in spec.get("feet", {}).items():
        p[S[k]] = v

    def torso_space(pt):
        W = world(resolve(p))
        o, R = W["torso"]
        return add(mv(tr(R), sub(pt, o)), BONES["torso"][1])

    def ts_rel(pt):
        return sub(torso_space(pt), BONES["torso"][1])

    sh_r = sub(BONES["right_arm"][1], BONES["torso"][1])
    if "grip" in spec:
        ik = arm_ik(sh_r, ts_rel(spec["grip"]), spec.get("right_pole", RIGHT_POLE))
        for slot, v in zip(("rarm_x", "rarm_y", "rarm_z", "rfore_x"), ik):
            p[S[slot]] = v
    elif "right" in spec:
        for slot, v in zip(("rarm_x", "rarm_y", "rarm_z", "rfore_x"), spec["right"]):
            p[S[slot]] = v * DEG

    W = world(resolve(p))
    _, Rf = W["right_forearm"]
    if "halberd_world" in spec:
        centre, up, face = spec["halberd_world"]
        Rw = frame(scale(up, -1.0), scale(face, -1.0))   # columns: local +y, local +z
        Rw = [[Rw[i][0], Rw[i][1], Rw[i][2]] for i in range(3)]
        Rlocal = frame([0.0, 1.0, 0.0], [0.0, 0.0, 1.0])
        Rh = mm(Rw, tr(Rlocal))
        hx, hy, hz = euler(mm(tr(Rf), Rh))
        p[S["hal_x"]], p[S["hal_y"]], p[S["hal_z"]] = hx, hy, hz
        # put the haft's midpoint on the centre
        mid_local = [0.0, (HAFT_DOWN - HAFT_UP) / 2.0 - 4.0, 0.0]
        grip_world = sub(centre, mv(Rh, mid_local))
        fo, _ = W["right_forearm"]
        rel = mv(tr(Rf), sub(grip_world, fo))
        bind = sub(BONES["halberd"][1], BONES["right_forearm"][1])
        p[S["hal_dx"]], p[S["hal_dy"]], p[S["hal_dz"]] = sub(rel, bind)
    else:
        up = spec.get("halberd_up", "vertical")
        if up == "vertical":
            up, face = [0.0, -1.0, 0.0], [0.0, 0.0, -1.0]
        else:
            face = spec["halberd_face"]
        Rw = frame(scale(up, -1.0), scale(face, -1.0))
        Rh = mm(Rw, tr(frame([0.0, 1.0, 0.0], [0.0, 0.0, 1.0])))
        p[S["hal_x"]], p[S["hal_y"]], p[S["hal_z"]] = euler(mm(tr(Rf), Rh))

    if "two_hand" in spec:
        p[S["two_hand"]] = 1.0
        p[S["grip_k"]] = spec["two_hand"]
        sh_l = sub(BONES["left_arm"][1], BONES["torso"][1])
        ik = arm_ik(sh_l, haft_point(p), LEFT_POLE)
        for slot, v in zip(("larm_x", "larm_y", "larm_z", "lfore_x"), ik):
            p[S[slot]] = v
    elif "left_target" in spec:
        sh_l = sub(BONES["left_arm"][1], BONES["torso"][1])
        ik = arm_ik(sh_l, ts_rel(spec["left_target"]), spec.get("left_pole", LEFT_POLE))
        for slot, v in zip(("larm_x", "larm_y", "larm_z", "lfore_x"), ik):
            p[S[slot]] = v
    elif "left" in spec:
        for slot, v in zip(("larm_x", "larm_y", "larm_z", "lfore_x"), spec["left"]):
            p[S[slot]] = v * DEG

    if "head_world" in spec:
        centre, deg = spec["head_world"]
        W = world(resolve(p))
        to, tR = W["torso"]
        Rw = rot(*(d * DEG for d in deg))
        p[S["head_x"]], p[S["head_y"]], p[S["head_z"]] = euler(mm(tr(tR), Rw))
        # helm centre sits 4 above the neck pivot in head space
        piv_world = sub(centre, mv(Rw, [0.0, -4.0, 0.0]))
        rel = mv(tr(tR), sub(piv_world, to))
        bind = sub(BONES["head"][1], BONES["torso"][1])
        p[S["head_dx"]], p[S["head_dy"]], p[S["head_dz"]] = sub(rel, bind)
    return p


def pommel_point():
    """Where the sword's pommel sits at rest: the left fist's anchor in the dormant pose."""
    W = world(resolve([0.0] * len(SLOTS)))
    top = to_world(W, "sword", (7.5, -4.0, -0.5))
    # the fist rests ON it: gauntlet centre is 2 above its lower edge
    return [top[0], top[1] - 2.2, top[2]]


SPECS = {}

# DORMANT: at attention on its post. Halberd planted upright beside the right boot, edge forward;
# left fist resting on the sword's pommel; eyes front. Nothing about this pose moves.
SPECS["rest"] = dict(
    head=(3, 0, 0),
    grip=(-10.0, FLOOR - HAFT_DOWN - 1.0, -6.5),
    halberd_up="vertical",
    left_target=None,                   # filled below: the pommel
    left_pole=(0.25, 0.2, 1.0),         # elbow straight back, so the pauldron does not flare
    feet=dict(rfoot_z=0.0, lfoot_z=0.0),
)

# AWAKE: a guard stance it marches in. Feet staggered, knees given a little, the halberd held
# two-handed across the body with the blade high on its left -- port arms.
READY = dict(
    drop=0.8,
    torso=(6, 0, 0), head=(-5, 0, 0),
    # Shallow enough that the haft crosses the chest below the chin -- steeper and it runs
    # straight across the visor and the statue has no face.
    grip=(-7.0, 2.0, -7.0),
    halberd_up=norm((1.0, -0.72, -0.3)), halberd_face=norm((0.45, 0.6, -1.0)),
    two_hand=-10.0,
    feet=dict(rfoot_z=1.5, lfoot_z=-2.5),
)
SPECS["ready"] = READY

# The rise, beat by beat. Each stage moves ONE thing, which is what makes it read as a statue
# working itself loose rather than a man standing up.
SPECS["wake_look"] = dict(SPECS["rest"], head=(-9, -22, 0))
SPECS["wake_free"] = dict(SPECS["rest"], head=(-6, -10, 0), torso=(-3, 0, 0),
                          left_target=None, left=(-8, 0, -10, -18))
SPECS["wake_arms"] = dict(READY, drop=0.0, feet=dict(rfoot_z=0.0, lfoot_z=0.0))
SPECS["wake_step"] = dict(READY, drop=0.0,
                          feet=dict(rfoot_z=0.8, lfoot_z=-4.5, llift=5.0, lfoot_p=0.22))


def _strike_grip_for_floor(spec, lo, hi):
    """Push the heavy blow's halberd down until its edge just meets the floor."""
    def lowest(a):
        s = dict(spec, halberd_up=norm((0.0, a, -1.0)))
        W = world(resolve(solve(s)))
        return max(max(c[1] for c in cube_corners(W, i)) for i, cb in enumerate(CUBES)
                   if cb[0] == "halberd" and cb[4] in ("edge", "blade"))
    for _ in range(40):
        mid = (lo + hi) / 2.0
        if lowest(mid) > FLOOR:
            hi = mid
        else:
            lo = mid
    return dict(spec, halberd_up=norm((0.0, lo, -1.0)))


# THE COMBO. Blow 0: high right to low left. Blow 1: back from high left to low right. Blow 2:
# straight overhead into the floor. Hips take 40% of each twist so the torso is not wrung round
# on a fixed pelvis.
SPECS["wind0"] = dict(READY, drop=0.6, hips=(0, 14, 0), torso=(-6, 22, 0), head=(-2, -24, 0),
                      grip=(-12.0, -15.0, 3.0),
                      halberd_up=norm((-0.55, -1.0, 0.65)), halberd_face=norm((0.6, 0.3, -0.8)),
                      two_hand=9.0)
SPECS["strike0"] = dict(READY, drop=2.6, root_z=-1.0, hips=(0, -14, 0), torso=(16, -24, 0),
                        head=(-12, 26, 0), grip=(2.0, 3.5, -11.0),
                        halberd_up=norm((0.75, 0.45, -1.0)), halberd_face=norm((0.3, 1.0, 0.2)),
                        two_hand=9.0)
SPECS["wind1"] = dict(READY, drop=0.6, hips=(0, -14, 0), torso=(-6, -24, 0), head=(-2, 26, 0),
                      grip=(4.0, -15.0, 2.0),
                      halberd_up=norm((0.6, -1.0, 0.6)), halberd_face=norm((-0.6, 0.3, -0.8)),
                      two_hand=9.0)
SPECS["strike1"] = dict(READY, drop=2.6, root_z=-1.0, hips=(0, 14, 0), torso=(16, 22, 0),
                        head=(-12, -24, 0), grip=(-10.0, 3.5, -10.0),
                        halberd_up=norm((-0.75, 0.45, -1.0)), halberd_face=norm((-0.3, 1.0, 0.2)),
                        two_hand=9.0)
SPECS["wind2"] = dict(READY, drop=0.2, torso=(-12, 0, 0), head=(-6, 0, 0),
                      grip=(0.0, -22.0, -1.5),
                      halberd_up=norm((0.0, -0.45, 1.0)), halberd_face=norm((0.0, -1.0, -0.3)),
                      two_hand=6.0)
SPECS["strike2"] = dict(READY, drop=4.0, root_z=-1.5, torso=(30, 0, 0), head=(-24, 0, 0),
                        grip=(-2.5, 1.0, -13.0),
                        halberd_up=norm((0.0, 0.5, -1.0)), halberd_face=norm((0.0, 1.0, 0.4)),
                        two_hand=9.0, feet=dict(rfoot_z=2.5, lfoot_z=-4.5))

# DESTROYED: the knees go, it drops to the floor, the halberd falls and then the head topples
# off and rolls. Kneeling contact is solved: the knee cop sits 4 below the shin's axis and the
# greave 2.5, so the knee's axis rides 4 above the floor and the ankle's 2.5.
_KNEEL_DROP = 10.0
_kt = -math.acos((FLOOR - 4.0 - 5.0 - _KNEEL_DROP) / THIGH)
_knee_z = THIGH * math.sin(_kt)
_ankle_dz = math.sqrt(SHIN ** 2 - 1.5 ** 2)
_KNEEL_FEET = dict(rfoot_z=_knee_z + _ankle_dz, lfoot_z=_knee_z + _ankle_dz,
                   rlift=ANKLE_Y - (FLOOR - 2.5), llift=ANKLE_Y - (FLOOR - 2.5),
                   # Pointed, sole up. Reached through toe-UP (-90 deg on the way to -180): the
                   # other way round passes toe-down and drives the boot through the floor.
                   flat=1.0, rfoot_p=-math.pi, lfoot_p=-math.pi)
SPECS["crumble_a"] = dict(
    drop=_KNEEL_DROP, torso=(28, 0, 6), head=(26, 8, 0), hips=(8, 0, 0),
    right=(-28, 0, 14, -12), left=(-24, 0, -16, -14),
    # Tipping forward across the body, close to how READY holds it, so the blend from guard
    # never swings the butt down through the floor.
    halberd_up=norm((0.9, -0.15, -0.6)), halberd_face=norm((0.3, 0.6, -0.6)),
    feet=_KNEEL_FEET,
)
SPECS["crumble_b"] = dict(
    SPECS["crumble_a"], torso=(46, 0, 9), hips=(12, 0, 0),
    left=(-6, 0, -26, -10),
    # Still gripped, lying flat on the floor in front of it. A weapon flung loose would have to
    # travel in forearm space, and a straight lerp there drives it through the floor mid-fall.
    grip=(-6.0, FLOOR - 3.0, -13.0),
    halberd_up=norm((1.0, 0.0, -0.35)), halberd_face=norm((-0.33, 0.0, -0.94)),
    head_world=((9.0, FLOOR - 4.5, -17.0), (8, 35, 78)),
)

# Pose order in the Java table. Java indexes these by the constants it emits.
POSE_ORDER = ["rest", "wake_look", "wake_free", "wake_arms", "wake_step", "ready",
              "wind0", "strike0", "wind1", "strike1", "wind2", "strike2",
              "crumble_a", "crumble_b"]


def cube_corners(W, i):
    bone, _n, o, s, _k = CUBES[i]
    out = []
    for dx in (0, s[0]):
        for dy in (0, s[1]):
            for dz in (0, s[2]):
                out.append(to_world(W, bone, (o[0] + dx, o[1] + dy, o[2] + dz)))
    return out


def settle(name, key, bones, nested=True):
    """Lower (or raise) a placed piece until its lowest corner rests exactly on the floor."""
    for _ in range(4):
        spec = dict(SPECS[name])
        W = world(resolve(solve({k: v for k, v in spec.items() if v is not None})))
        low = max(c[1] for i, cb in enumerate(CUBES) if cb[0] in bones
                  for c in cube_corners(W, i))
        point = list(spec[key][0] if nested else spec[key])
        point[1] += FLOOR - low
        SPECS[name] = dict(spec, **{key: (tuple(point),) + tuple(spec[key][1:]) if nested
                                    else tuple(point)})


def build_poses():
    SPECS["rest"]["left_target"] = pommel_point()
    settle("crumble_b", "head_world", ("head",))
    settle("crumble_b", "grip", ("halberd", "right_forearm"), nested=False)
    SPECS["wake_look"]["left_target"] = SPECS["rest"]["left_target"]
    SPECS["strike2"] = _strike_grip_for_floor(SPECS["strike2"], -0.2, 1.2)
    poses = {}
    for name in POSE_ORDER:
        spec = {k: v for k, v in SPECS[name].items() if v is not None}
        poses[name] = solve(spec)
    return poses


# Every pair of poses setupAnim blends between. A straight lerp across an angle step bigger than
# pi spins the joint the long way round, so audit() rejects any such pair.
BLENDS = [("rest", "wake_look"), ("wake_look", "wake_free"), ("wake_free", "wake_arms"),
          ("wake_arms", "wake_step"), ("wake_step", "ready"),
          ("ready", "wind0"), ("wind0", "strike0"), ("strike0", "ready"),
          ("ready", "wind1"), ("wind1", "strike1"), ("strike1", "ready"),
          ("ready", "wind2"), ("wind2", "strike2"), ("strike2", "ready"),
          ("ready", "crumble_a"), ("crumble_a", "crumble_b"), ("crumble_b", "rest")]
ANGLE_SLOTS = [S[n] for n in ("rfore_x", "lfore_x")]


# --- audits ---------------------------------------------------------------------------------
STANDING = ["rest", "wake_look", "wake_free", "wake_arms", "ready",
            "wind0", "strike0", "wind1", "strike1", "wind2", "strike2"]


def audit(poses):
    out = []
    for a, b in BLENDS:
        for i in ANGLE_SLOTS:
            if abs(poses[a][i] - poses[b][i]) > math.pi:
                out.append("%s -> %s: %s jumps %.0f deg, the lerp would spin it"
                           % (a, b, SLOTS[i], (poses[b][i] - poses[a][i]) / DEG))
    for name, p in poses.items():
        W = world(resolve(p))
        low = max(max(c[1] for c in cube_corners(W, i)) for i in range(len(CUBES)))
        if low > FLOOR + 0.6:
            worst = max(range(len(CUBES)),
                        key=lambda i: max(c[1] for c in cube_corners(W, i)))
            out.append("%s: %s is %.2f below the floor" % (name, CUBES[worst][1], low - FLOOR))
        if name in STANDING:
            for side in ("r", "l"):
                sole = max(c[1] for i, cb in enumerate(CUBES) if cb[1] == side + "_sabaton"
                           for c in cube_corners(W, i))
                if abs(sole - FLOOR) > 0.35:
                    out.append("%s: %s sole at y=%.2f, not on the floor" % (name, side, sole))
        if p[S["two_hand"]] >= 1.0:
            r = resolve(p)
            Wr = world(r)
            fist = to_world(Wr, "left_forearm", (9.0, 6.5, 0.0))
            o, R = Wr["halberd"]
            axis = mv(R, [0.0, 1.0, 0.0])
            rel = sub(fist, o)
            miss = math.sqrt(max(0.0, dot(rel, rel) - dot(rel, axis) ** 2))
            if miss > 0.6:
                out.append("%s: left fist %.2f off the haft (out of reach?)" % (name, miss))
    rest = world(resolve(poses["rest"]))
    butt = max(c[1] for i, cb in enumerate(CUBES) if cb[1] == "butt"
               for c in cube_corners(rest, i))
    if abs(butt - FLOOR) > 0.35:
        out.append("rest: halberd butt at y=%.2f, should stand on the floor" % butt)
    return out


# --- texture --------------------------------------------------------------------------------
def faces(i):
    """{face: (u, v, w, h)} for cube i's box net, named by where they face on the model."""
    _b, _n, _o, (w, h, d), _k = CUBES[i]
    u, v = UVS[i]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


STONE_D = (112, 110, 104)
STONE_L = (184, 181, 171)
MOSS = (104, 118, 86)
LICHEN = (162, 158, 112)
SLIT = (34, 34, 38)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    return tuple(max(0, min(255, int(round(x * k)))) for x in c)


def vnoise(x, y, seed):
    def h(ix, iy):
        n = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
        n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
        return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * fx
    b = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * fx
    return a + (b - a) * fy


def stone(u, v, tone=0.0):
    """Limestone: broad mottling, fine grain. tone shifts lighter/darker for a part."""
    n = 0.55 * vnoise(u / 5.0, v / 5.0, 3) + 0.30 * vnoise(u / 2.0, v / 2.0, 7)
    n += 0.15 * vnoise(u * 1.0, v * 1.0, 11)
    return mix(STONE_D, STONE_L, max(0.0, min(1.0, 0.18 + 0.75 * n + tone)))


def paint_face(px, rect, kind, face, rng, tone):
    u0, v0, w, h = rect
    for y in range(h):
        for x in range(w):
            c = stone(u0 + x, v0 + y, tone)
            # Carved edges: the top lip catches light, the lower edge and sides fall into shade.
            # This is what turns a flat grey box into a dressed block.
            if y == 0 and face not in ("top", "bottom"):
                c = shade(c, 1.10)
            elif y == h - 1 and h > 2:
                c = shade(c, 0.84)
            if (x == 0 or x == w - 1) and w > 2:
                c = shade(c, 0.92)
            px[(u0 + x, v0 + y)] = c

    def put(x, y, k=None, col=None):
        if 0 <= x < w and 0 <= y < h:
            cur = px[(u0 + x, v0 + y)]
            px[(u0 + x, v0 + y)] = col if col is not None else shade(cur, k)

    def hline(y, k, x0=0, x1=None):
        for x in range(x0, w if x1 is None else x1):
            put(x, y, k)

    def vline(x, k, y0=0, y1=None):
        for y in range(y0, h if y1 is None else y1):
            put(x, y, k)

    side = face in ("front", "back", "left", "right")
    # --- the detail that makes each piece read as what it is ---
    if kind == "visor" and face == "front":
        for x in range(w):
            put(x, 1, col=SLIT if x != w // 2 else None, k=1.15)
        put(w // 2, 0, 1.15)
        put(w // 2, 2, 1.12)
        put(w // 2, 3, 1.08)
        # Breaths on the right cheek only, as on a real armet. Symmetric holes either side read
        # as a mouth under the eye slit and the helm turns into a smiley face.
        for (x, y) in ((w - 2, 3), (w - 3, 4), (w - 2, 5)):
            put(x, y, col=shade(SLIT, 1.4))
    elif kind == "helm":
        if side:
            hline(h - 2, 0.80)
            for x in (1, w // 2, w - 2):
                put(x, h - 3, 1.18)
            if face in ("left", "right"):
                vline(w - 2 if face == "right" else 1, 0.86, 2, h - 2)
        if face == "top":
            vline(w // 2, 0.9)
    elif kind == "crest":
        if face in ("left", "right"):
            for x in range(0, w, 2):
                vline(x, 0.82)
    elif kind == "chest":
        if face == "front":
            for y in range(h):
                for x in range(w):
                    dist = abs(x - (w - 1) / 2.0) / (w / 2.0)
                    put(x, y, 1.10 - 0.22 * dist)
            # the arm holes, cut in at the top corners
            for (x, y) in ((0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1)):
                put(x, y, 0.72)
            hline(h - 2, 0.78, 1, w - 1)
            hline(h - 1, 1.04, 1, w - 1)
        if face == "back":
            vline(w // 2, 0.86, 1, h - 1)
            hline(h - 2, 0.80)
        if face in ("left", "right"):
            hline(h - 2, 0.80)
    elif kind == "ridge" and side:
        vline(0, 1.15)
    elif kind == "fauld":
        if side:
            for y in (1, 3):
                hline(y, 0.74)
                hline(y + 1, 1.10)
            if face == "front":
                for x in (1, w - 2):
                    for y in (0, 2):
                        put(x, y, 1.25)
    elif kind == "belt" and side:
        hline(0, 0.85)
        if face == "front":
            for x in (w // 2 - 1, w // 2):
                put(x, 0, 1.35)
                put(x, 1, 1.25)
            put(w // 2 - 1, 0, 0.6)
    elif kind == "skirt" and side:
        for x in range(2, w - 1, 3):
            vline(x, 0.74)
        hline(h - 1, 0.80)
    elif kind == "tasset" and face == "front":
        hline(1, 0.76)
        hline(2, 1.10)
        hline(h - 1, 0.78)
    elif kind == "thigh" and face == "front":
        vline(w // 2, 1.10)
        vline(w // 2 - 1, 0.92)
    elif kind == "knee" and face == "front":
        for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
            put(x, y, 0.70)
        put(w // 2, 1, 1.25)
        put(w // 2 - 1, 1, 1.15)
    elif kind == "greave" and side:
        if face == "front":
            vline(w // 2, 1.18)
            vline(w // 2 - 1, 0.94)
        hline(0, 0.82)
    elif kind == "sabaton":
        if face == "top":
            for y in range(1, h - 2, 2):
                hline(y, 0.74)
        if face == "front":
            hline(0, 1.12)
    elif kind == "pauldron":
        if side or face == "top":
            for y in range(1, h, 2):
                hline(y, 0.76)
    elif kind == "lame" and side:
        hline(h - 1, 0.74)
    elif kind in ("upper", "vambrace") and side:
        hline(0, 0.82)
        if kind == "vambrace":
            hline(h - 1, 0.76)
    elif kind == "gauntlet":
        if side:
            hline(0, 1.15)
            hline(1, 0.80)
            for x in range(1, w - 1, 2):
                vline(x, 0.80, 2, h)
        if face == "bottom":
            for x in range(1, w - 1, 2):
                vline(x, 0.80)
    elif kind == "haft" and side:
        for y in range(h):
            for x in range(w):
                put(x, y, 0.86)
        for y in range(4, h - 4, 9):
            hline(y, 1.25)
            hline(y + 1, 0.80)
    elif kind in ("blade", "edge"):
        if kind == "edge":
            for y in range(h):
                for x in range(w):
                    put(x, y, 1.12)
            if face in ("left", "right"):
                vline(0 if face == "left" else w - 1, 1.12)
        if face in ("left", "right"):
            hline(0, 1.10)
    elif kind == "metal" and side:
        hline(0, 1.15)
        hline(h - 1, 0.78)
    elif kind == "scabbard" and side:
        for y in (0, h - 3):
            hline(y, 1.22)
            hline(y + 1, 1.12)
        for y in range(2, h - 3):
            for x in range(w):
                put(x, y, 0.88)
    elif kind in ("hilt", "grip") and side:
        hline(0, 1.15)

    # --- weathering: rain streaks, cracks, moss low down ---
    if side and w * h >= 24:
        for _ in range(rng.randint(0, 2)):
            x = rng.randrange(w)
            for y in range(rng.randrange(h // 2, h + 1)):
                put(x, y, 0.94)
    if side and w >= 3 and w * h >= 40 and rng.random() < 0.65:
        x, y = rng.randrange(1, w - 1), rng.randrange(0, h // 2 + 1)
        for _ in range(rng.randint(3, 6)):
            put(x, y, 0.66)
            x += rng.choice((-1, 0, 0, 1))
            y += 1
    if kind in ("sabaton", "greave", "skirt", "butt") and side:
        for y in range(max(0, h - 2), h):
            for x in range(w):
                if rng.random() < 0.30:
                    put(x, y, col=mix(MOSS, px[(u0 + x, v0 + y)], rng.random() * 0.4))
    for y in range(h):
        for x in range(w):
            if rng.random() < 0.006:
                put(x, y, col=mix(LICHEN, px[(u0 + x, v0 + y)], 0.35))


def build_textures():
    px = {}
    for i, (bone, name, _o, _s, kind) in enumerate(CUBES):
        rng = random.Random(name)
        tone = {"haft": -0.10, "edge": 0.08, "blade": 0.04, "scabbard": -0.12,
                "grip": -0.15, "crest": -0.04}.get(kind, 0.0)
        for face, rect in faces(i).items():
            paint_face(px, rect, kind, face, rng, tone)
    write_png(TEX_OUT, TEX_W, TEX_H, px)

    # The glow layer: only the visor slit, either side of the nasal bar. Drawn full-bright and
    # additively by the model's eyes layer, so everything else must be transparent black.
    glow = {}
    for i, cb in enumerate(CUBES):
        if cb[1] == "faceplate":
            u0, v0, w, h = faces(i)["front"]
            for x in range(w):
                if x != w // 2:
                    edge = x in (0, w - 1)
                    glow[(u0 + x, v0 + 1)] = (110, 190, 235) if edge else (175, 230, 255)
    write_png(GLOW_OUT, TEX_W, TEX_H, glow)
    return px


def write_png(path, w, h, px):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            c = px.get((x, y))
            raw += bytes(c) + b"\xff" if c else b"\x00\x00\x00\x00"

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
           + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(png)


# --- Java -----------------------------------------------------------------------------------
def jf(v):
    s = ("%.4f" % v).rstrip("0")
    if s.endswith("."):
        s += "0"
    if s in ("-0.0",):
        s = "0.0"
    return s + "F"


def java_mesh():
    L = ["    public static LayerDefinition createBodyLayer() {",
         "        MeshDefinition mesh = new MeshDefinition();",
         "        PartDefinition root = mesh.getRoot().addOrReplaceChild(\"root\","
         " CubeListBuilder.create(), PartPose.ZERO);"]
    for b in ORDER[1:]:
        parent, piv, rest = BONES[b]
        ppiv = BONES[parent][1]
        cubes = [(i, c) for i, c in enumerate(CUBES) if c[0] == b]
        L.append("        PartDefinition %s = %s.addOrReplaceChild(\"%s\", CubeListBuilder.create()"
                 % (camel(b), camel(parent), b))
        for i, (_b, _n, o, s, _k) in cubes:
            L.append("                .texOffs(%d, %d).addBox(%s, %s, %s, %d.0F, %d.0F, %d.0F)"
                     % (UVS[i][0], UVS[i][1], jf(o[0] - piv[0]), jf(o[1] - piv[1]),
                        jf(o[2] - piv[2]), s[0], s[1], s[2]))
        off = sub(piv, ppiv)
        if rest:
            L.append("                , PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));"
                     % (jf(off[0]), jf(off[1]), jf(off[2]),
                        jf(rest[0] * DEG), jf(rest[1] * DEG), jf(rest[2] * DEG)))
        else:
            L.append("                , PartPose.offset(%s, %s, %s));"
                     % (jf(off[0]), jf(off[1]), jf(off[2])))
    L.append("        return LayerDefinition.create(mesh, %d, %d);" % (TEX_W, TEX_H))
    L.append("    }")
    return "\n".join(L)


def camel(b):
    parts = b.split("_")
    return parts[0] + "".join(x.title() for x in parts[1:])


def java_poses(poses):
    L = ["    // Timing shared with LivingStatueEntity (read from its source on every run).",
         "    static final int WAKE_TICKS = %d;" % WAKE_TICKS,
         "    static final float STRIKE_AT = %s;" % jf(STRIKE_AT),
         "",
         "    // Pose vector slots. Angles in radians, offsets in model units."]
    for i, n in enumerate(SLOTS):
        L.append("    private static final int %s = %d;" % (n.upper(), i))
    L.append("    private static final int SLOTS = %d;" % len(SLOTS))
    L.append("")
    L.append("    // Rig dimensions the IK runs on, read off the generated mesh.")
    for n, v in (("UPPER_ARM", UPPER_ARM), ("FOREARM", FOREARM), ("THIGH", THIGH),
                 ("SHIN", SHIN), ("ANKLE_Y", ANKLE_Y), ("HIP_Y", BONES["right_leg"][1][1])):
        L.append("    private static final float %s = %s;" % (n, jf(v)))
    t = BONES["torso"][1]
    for n, b in (("R_SHOULDER", "right_arm"), ("L_SHOULDER", "left_arm")):
        s = sub(BONES[b][1], t)
        L.append("    private static final float[] %s = {%s, %s, %s};"
                 % (n, jf(s[0]), jf(s[1]), jf(s[2])))
    for n, v in (("L_POLE", LEFT_POLE),):
        L.append("    private static final float[] %s = {%s, %s, %s};"
                 % (n, jf(v[0]), jf(v[1]), jf(v[2])))
    L.append("")
    L.append("    // Key poses, SOLVED by the generator from intent (grip, blade direction, feet).")
    for name in POSE_ORDER:
        vals = ", ".join(jf(v) for v in poses[name])
        L.append("    static final float[] %s = {%s};" % (name.upper(), vals))
    return "\n".join(L)


def splice(src, tag, body):
    a = "    // <generated:%s>" % tag
    b = "    // </generated:%s>" % tag
    i, j = src.index(a), src.index(b)
    return src[:i + len(a)] + "\n" + body + "\n" + src[j:]


def build_java(poses):
    src = open(JAVA_OUT, encoding="utf-8").read()
    open(JAVA_OUT, "w", encoding="utf-8", newline="\n").write(splice(src, "mesh", java_mesh()))
    src = open(POSES_OUT, encoding="utf-8").read()
    open(POSES_OUT, "w", encoding="utf-8", newline="\n").write(
        splice(src, "poses", java_poses(poses)))


def entity_constants():
    """WAKE_TICKS and STRIKE_AT belong to the entity; the timeline here must use the same."""
    import re
    src = open(ENTITY_SRC, encoding="utf-8").read()
    wake = int(re.search(r"WAKE_TICKS = (\d+);", src).group(1))
    strike = float(re.search(r"STRIKE_AT = ([0-9.]+)F;", src).group(1))
    return wake, strike


# --- .bbmodel (geometry, openable in Blockbench) ---------------------------------------------
def build_bbmodel():
    elements, nodes, n = [], {}, [0]

    def uid():
        n[0] += 1
        return "5a7a0e00-0000-4000-8000-%012d" % n[0]
    for b in ORDER:
        parent, piv, rest = BONES[b]
        kids = []
        for i, (cb, name, o, s, _k) in enumerate(CUBES):
            if cb != b:
                continue
            e = uid()
            elements.append({
                "name": name, "box_uv": True, "rescale": False, "type": "cube", "uuid": e,
                "from": [o[0], FLOOR - (o[1] + s[1]), o[2]],
                "to": [o[0] + s[0], FLOOR - o[1], o[2] + s[2]],
                "origin": [piv[0], FLOOR - piv[1], piv[2]],
                "uv_offset": list(UVS[i]), "faces": {}})
            kids.append(e)
        r = rest or (0, 0, 0)
        nodes[b] = {"name": b, "uuid": uid(), "export": True, "isOpen": True,
                    "origin": [piv[0], FLOOR - piv[1], piv[2]],
                    "rotation": [-r[0], -r[1], r[2]], "children": kids}
    outliner = []
    for b in ORDER:
        parent = BONES[b][0]
        (nodes[parent]["children"] if parent else outliner).append(nodes[b])
    model = {
        "meta": {"format_version": "4.5", "model_format": "modded_entity", "box_uv": True},
        "name": "living_statue", "model_identifier": "living_statue",
        "resolution": {"width": TEX_W, "height": TEX_H},
        "elements": elements, "outliner": outliner,
        "textures": [{"path": TEX_OUT, "name": "living_statue.png", "folder": "entity",
                      "namespace": "hogwartsmagic", "id": "0", "particle": False,
                      "mode": "link", "uuid": uid()}],
    }
    os.makedirs(os.path.dirname(BB_OUT), exist_ok=True)
    json.dump(model, open(BB_OUT, "w"), indent=1)


# --- preview renderer -----------------------------------------------------------------------
FACE_CORNERS = {
    # face: (top-left, top-right, bottom-left) as (x?, y?, z?) picks of min(0)/max(1)
    "front": ((0, 0, 0), (1, 0, 0), (0, 1, 0)),
    "back": ((1, 0, 1), (0, 0, 1), (1, 1, 1)),
    "right": ((0, 0, 1), (0, 0, 0), (0, 1, 1)),
    "left": ((1, 0, 0), (1, 0, 1), (1, 1, 0)),
    "top": ((0, 0, 1), (1, 0, 1), (0, 0, 0)),
    "bottom": ((0, 1, 0), (1, 1, 0), (0, 1, 1)),
}


def render(pose_vec, tex, yaw_deg, pitch_deg=12.0, px_per=5, size=(360, 470)):
    import numpy as np
    W = world(pose_vec if isinstance(pose_vec, dict) else resolve(pose_vec))
    cy, sy = math.cos(yaw_deg * DEG), math.sin(yaw_deg * DEG)
    cp, sp = math.cos(pitch_deg * DEG), math.sin(pitch_deg * DEG)

    def view(p):
        x = p[0] * cy - p[2] * sy
        z = p[0] * sy + p[2] * cy
        y = p[1] - 6.0
        y2 = y * cp + z * sp
        z2 = -y * sp + z * cp
        return (size[0] / 2 + x * px_per, size[1] * 0.55 + y2 * px_per, z2)

    img = np.zeros((size[1], size[0], 3), dtype=np.float32)
    img[:] = (40, 44, 52)
    zb = np.full((size[1], size[0]), 1e9, dtype=np.float32)
    texa = np.zeros((TEX_H, TEX_W, 4), dtype=np.float32)
    for (u, v), c in tex.items():
        texa[v, u, :3] = c
        texa[v, u, 3] = 1
    L = norm([-0.35, -1.0, -0.7])
    quads = []
    # floor
    for gx in range(-3, 3):
        for gz in range(-3, 3):
            x0, z0 = gx * 8.0, gz * 8.0
            col = (92, 98, 88) if (gx + gz) % 2 else (80, 86, 78)
            quads.append(([x0, FLOOR, z0], [x0 + 8, FLOOR, z0], [x0, FLOOR, z0 + 8], None, col))
    for i, (bone, _n, o, s, _k) in enumerate(CUBES):
        f = faces(i)
        for face, (tl, trc, bl) in FACE_CORNERS.items():
            def corner(sel):
                return to_world(W, bone, tuple(o[k] + s[k] * sel[k] for k in range(3)))
            quads.append((corner(tl), corner(trc), corner(bl), f[face], None))
    for (p0, p1, p3, rect, flat) in quads:
        a, b, c = view(p0), view(p1), view(p3)
        e1 = (b[0] - a[0], b[1] - a[1])
        e2 = (c[0] - a[0], c[1] - a[1])
        det = e1[0] * e2[1] - e1[1] * e2[0]
        if abs(det) < 1e-6:
            continue
        nrm = norm(cross(sub(p1, p0), sub(p3, p0)))
        light = 0.45 + 0.6 * max(0.0, -dot(nrm, L)) if flat is None else 1.0
        xs = [a[0], b[0], c[0], b[0] + e2[0]]
        ys = [a[1], b[1], c[1], b[1] + e2[1]]
        x0, x1 = max(0, int(min(xs))), min(size[0] - 1, int(max(xs)) + 1)
        y0, y1 = max(0, int(min(ys))), min(size[1] - 1, int(max(ys)) + 1)
        if x0 >= x1 or y0 >= y1:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        dx, dy = gx - a[0], gy - a[1]
        s_ = (dx * e2[1] - dy * e2[0]) / det
        t_ = (e1[0] * dy - e1[1] * dx) / det
        m = (s_ >= 0) & (s_ < 1) & (t_ >= 0) & (t_ < 1)
        if not m.any():
            continue
        depth = a[2] + s_ * (b[2] - a[2]) + t_ * (c[2] - a[2])
        if rect is not None:
            u0, v0, w, h = rect
            tu = np.clip((u0 + s_ * w).astype(int), u0, u0 + w - 1)
            tv = np.clip((v0 + t_ * h).astype(int), v0, v0 + h - 1)
            texel = texa[tv, tu]
            m &= texel[..., 3] > 0
            col = texel[..., :3] * light
        else:
            col = np.empty(gx.shape + (3,), dtype=np.float32)
            col[:] = flat
        sub_zb = zb[y0:y1, x0:x1]
        m &= depth < sub_zb
        sub_zb[m] = depth[m]
        img[y0:y1, x0:x1][m] = col[m]
    return np.clip(img, 0, 255).astype("uint8")


# --- the runtime timeline, mirrored from LivingStatuePoses ----------------------------------
WAKE_TICKS, STRIKE_AT = 50, 0.45     # overwritten from the entity source in main()
WAKE_BEATS = [5.0, 13.0, 21.0, 33.0, 41.0, 46.0]


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def grind(t):
    t = max(0.0, min(1.0, t))
    if t < 0.45:
        return 0.55 * smooth(t / 0.45)
    if t < 0.6:
        return 0.55 + 0.05 * (t - 0.45) / 0.15
    return 0.6 + 0.4 * smooth((t - 0.6) / 0.4)


def wake_pose(P, w):
    b = WAKE_BEATS
    seq = [("rest", "wake_look", grind), ("wake_look", "wake_free", grind),
           ("wake_free", "wake_arms", grind), ("wake_arms", "wake_step", smooth),
           ("wake_step", "ready", lambda t: t * t)]
    if w < b[0]:
        return list(P["rest"])
    for i, (a, c, f) in enumerate(seq):
        if w < b[i + 1]:
            return blend(P[a], P[c], f((w - b[i]) / (b[i + 1] - b[i])))
    p = list(P["ready"])
    t = (w - b[5]) / (WAKE_TICKS - b[5])
    p[S["drop"]] += 0.9 * (1 - t) * (1 - t)
    return p


def swing_pose(P, combo, s):
    wind, strike = P["wind%d" % combo], P["strike%d" % combo]
    heavy = combo == 2
    wind_end = 0.33 if heavy else 0.27
    hang_end = wind_end + (0.05 if heavy else 0.04)
    hold_end = 0.68 if heavy else 0.60
    if s < wind_end:
        t = s / wind_end
        return blend(P["ready"], wind, 1 - (1 - t) * (1 - t))
    if s < hang_end:
        return list(wind)
    if s < STRIKE_AT:
        t = (s - hang_end) / (STRIKE_AT - hang_end)
        return blend(wind, strike, t * t)
    if s < hold_end:
        p = list(strike)
        t = (s - STRIKE_AT) / (hold_end - STRIKE_AT)
        p[S["drop"]] += (1.0 if heavy else 0.4) * (1 - t) * (1 - t)
        return p
    return blend(strike, P["ready"], smooth((s - hold_end) / (1 - hold_end)))


def crumble_pose(P, base, c):
    if c < 0.35:
        t = c / 0.35
        return blend(base, P["crumble_a"], t * t)
    t = (c - 0.35) / 0.65
    return blend(P["crumble_a"], P["crumble_b"], t * t)


def walk_pose(P, limb_swing, walk):
    return walk_resolve(list(P["ready"]), limb_swing, walk)


def walk_resolve(p, limb_swing, walk):
    """resolve() plus the march -- LivingStatuePoses.resolve, walk branch included."""
    p = list(p)
    if walk <= 0:
        return resolve(p)
    ph = limb_swing * 0.55
    sn, cs = math.sin(ph), math.cos(ph)
    shaped = math.copysign(abs(sn) ** 0.7, sn)
    swing_r = -0.40 * shaped * walk
    lift_r, lift_l = max(0.0, cs) * 0.8 * walk, max(0.0, -cs) * 0.8 * walk
    bob = (THIGH + SHIN) * (1 - math.cos(swing_r))
    p[S["torso_z"]] += 0.05 * cs * walk
    p[S["hips_z"]] -= 0.03 * cs * walk
    p[S["torso_y"]] += 0.07 * shaped * walk
    pose = resolve(p)
    pose["root"] = ([0.0, p[S["drop"]] + bob, p[S["root_z"]]], [0.0, 0.0, 0.0])
    for side, sw, lift in (("right", swing_r, lift_r), ("left", -swing_r, lift_l)):
        t = pose[side + "_leg"][1][0] + sw
        sh = pose[side + "_shin"][1][0] + lift
        pose[side + "_leg"] = ([0.0] * 3, [t, 0.0, 0.0])
        pose[side + "_shin"] = ([0.0] * 3, [sh, 0.0, 0.0])
        pose[side + "_foot"] = ([0.0] * 3, [-(t + sh), 0.0, 0.0])
    return pose


def py_evaluate(P, wake, combo, swing, crumble, reform, yaw, pitch, limb_swing, limb_amt):
    """LivingStatuePoses.evaluate, line for line, returning its output vector."""
    look = walk = 0.0
    if wake <= 0:
        p = list(P["rest"])
    elif wake < WAKE_TICKS:
        p = wake_pose(P, wake)
    else:
        p = list(P["ready"])
        look, walk = 1.0, min(1.0, limb_amt * 2.2)
        if combo >= 0:
            p = swing_pose(P, combo, swing)
            hold_end = 0.68 if combo == 2 else 0.60
            committed = (1.0 - smooth((swing - hold_end) / (1 - hold_end))
                         if swing >= hold_end else 1.0)
            look *= 1 - 0.8 * committed
            walk *= 1 - committed
    if reform > 0:
        if reform < 0.5:
            p = blend(P["crumble_b"], P["crumble_a"], smooth(reform / 0.5))
        else:
            p = blend(P["crumble_a"], P["rest"], smooth((reform - 0.5) / 0.5))
        look = walk = 0.0
    elif crumble > 0:
        p = crumble_pose(P, p, crumble)
        look = walk = 0.0
    if look > 0:
        y = max(-55.0, min(55.0, yaw)) * DEG * look
        x = max(-30.0, min(30.0, pitch)) * DEG * look
        p[S["head_y"]] += y * 0.65
        p[S["torso_y"]] += y * 0.35
        p[S["head_x"]] += x * 0.8
    pose = walk_resolve(p, limb_swing, walk)
    out = []
    for b in JAVA_BONES:
        off, r = pose[b]
        out += list(off) + list(r)
    return out


JAVA_BONES = ["root", "hips", "torso", "head", "right_arm", "right_forearm", "halberd",
              "left_arm", "left_forearm", "right_leg", "right_shin", "right_foot", "left_leg",
              "left_shin", "left_foot"]

HARNESS = r"""
import com.hogwartsmagic.client.LivingStatuePoses;
import java.io.*;
public class PoseHarness {
    public static void main(String[] a) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        float[] out = new float[LivingStatuePoses.OUT_SIZE];
        StringBuilder sb = new StringBuilder();
        for (String line; (line = r.readLine()) != null; ) {
            String[] f = line.trim().split(" ");
            LivingStatuePoses.evaluate(Float.parseFloat(f[0]), Integer.parseInt(f[1]),
                Float.parseFloat(f[2]), Float.parseFloat(f[3]), Float.parseFloat(f[4]),
                Float.parseFloat(f[5]), Float.parseFloat(f[6]), Float.parseFloat(f[7]),
                Float.parseFloat(f[8]), out);
            for (float v : out) sb.append(v).append(' ');
            sb.append('\n');
        }
        System.out.print(sb);
    }
}
"""


def check_java(P, work_dir):
    """Compile LivingStatuePoses on its own and compare it with py_evaluate over many states.

    The previews only prove what the game draws if the two agree, and this is the check that
    they do -- no Minecraft needed, so it can run on every build."""
    import subprocess
    import shutil
    os.makedirs(work_dir, exist_ok=True)
    harness = os.path.join(work_dir, "PoseHarness.java")
    open(harness, "w").write(HARNESS)
    classes = os.path.join(work_dir, "classes")
    shutil.rmtree(classes, ignore_errors=True)
    subprocess.run(["javac", "-d", classes, POSES_OUT, harness], check=True)
    rng = random.Random(7)
    cases = []
    for w in [0, 2, 6, 9, 14, 17, 25, 30, 35, 39, 43, 45.5, 47, 49.9, 50]:
        cases.append((w, -1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
    for combo in range(3):
        for sv in [0.05, 0.2, 0.3, 0.34, 0.4, 0.44, 0.46, 0.55, 0.65, 0.7, 0.85, 0.99]:
            cases.append((50, combo, sv, 0.0, 0.0, rng.uniform(-80, 80), rng.uniform(-40, 40),
                          rng.uniform(0, 20), rng.uniform(0, 0.6)))
    for c in [0.05, 0.2, 0.34, 0.36, 0.6, 0.9, 1.0]:
        cases.append((50, -1, 0.0, c, 0.0, 0.0, 0.0, 0.0, 0.0))
    for rf in [0.1, 0.4, 0.6, 0.95]:
        cases.append((0, -1, 0.0, 0.0, rf, 0.0, 0.0, 0.0, 0.0))
    for _ in range(12):
        cases.append((50, -1, 0.0, 0.0, 0.0, rng.uniform(-80, 80), rng.uniform(-40, 40),
                      rng.uniform(0, 40), rng.uniform(0, 1)))
    stdin = "\n".join(" ".join(str(v) for v in c) for c in cases) + "\n"
    res = subprocess.run(["java", "-cp", classes, "PoseHarness"], input=stdin,
                         capture_output=True, text=True, check=True)
    worst, where = 0.0, None
    for case, line in zip(cases, res.stdout.strip().splitlines()):
        jv = [float(x) for x in line.split()]
        pv = py_evaluate(P, *case)
        for i, (a, b) in enumerate(zip(jv, pv)):
            d = abs(a - b)
            if d > worst:
                worst, where = d, (case, JAVA_BONES[i // 6], "dx dy dz xr yr zr".split()[i % 6])
    return len(cases), worst, where


def timeline_frames(P):
    out = []
    for w in (3, 9, 17, 27, 37, 44, 48):
        out.append(("t_wake_%02d" % w, wake_pose(P, w)))
    for combo in range(3):
        for s in (0.15, 0.30, 0.40, 0.50, 0.80):
            out.append(("t_swing%d_%02d" % (combo, int(s * 100)), swing_pose(P, combo, s)))
    for c in (0.2, 0.5, 0.8):
        out.append(("t_crumble_%02d" % int(c * 100), crumble_pose(P, P["ready"], c)))
    return out


def preview(poses, tex, out_dir, frames=None):
    import numpy as np
    from PIL import Image, ImageDraw
    os.makedirs(out_dir, exist_ok=True)
    items = frames if frames is not None else [(n, poses[n]) for n in POSE_ORDER]
    for name, vec in items:
        tiles = [render(vec, tex, yaw) for yaw in (0, -40, 90, 200)]
        im = Image.fromarray(np.concatenate(tiles, axis=1))
        ImageDraw.Draw(im).text((8, 6), name, fill=(255, 255, 255))
        im.save(os.path.join(out_dir, name + ".png"))


def main():
    global WAKE_TICKS, STRIKE_AT
    WAKE_TICKS, STRIKE_AT = entity_constants()
    poses = build_poses()
    tex = build_textures()
    build_java(poses)
    build_bbmodel()
    probs = audit(poses)
    top = min(c[1] for i in range(len(CUBES)) for c in cube_corners(world(resolve(poses["rest"])), i))
    print("living_statue: %d bones, %d cubes, %.1f units tall at rest (%.2f blocks)"
          % (len(ORDER), len(CUBES), FLOOR - top, (FLOOR - top) / 16.0))
    if "--preview" in sys.argv:
        d = sys.argv[sys.argv.index("--preview") + 1]
        preview(poses, tex, d)
        preview(poses, tex, d, timeline_frames(poses))
        preview(poses, tex, d, [("t_walk_%02d" % i, walk_pose(poses, i * 0.95, 1.0))
                                for i in range(0, 12, 2)])
    # every in-between the game can draw must stay out of the floor too
    for name, vec in timeline_frames(poses):
        W = world(resolve(vec))
        low = max(max(c[1] for c in cube_corners(W, i)) for i in range(len(CUBES)))
        if low > FLOOR + 1.0:  # a heel may graze for a frame or two mid-fall
            worst = max(range(len(CUBES)), key=lambda i: max(c[1] for c in cube_corners(W, i)))
            probs.append("%s: %s %.2f below the floor" % (name, CUBES[worst][1], low - FLOOR))
    if "--check-java" in sys.argv:
        n, worst, where = check_java(poses, sys.argv[sys.argv.index("--check-java") + 1])
        print("  java parity: %d states, worst difference %.5f %s" % (n, worst, where or ""))
        if worst > 2e-3:
            probs.append("LivingStatuePoses disagrees with the mirror here by %.4f at %s"
                         % (worst, where))
    for p in probs:
        print("  PROBLEM:", p)
    return 1 if probs else 0


if __name__ == "__main__":
    raise SystemExit(main())
