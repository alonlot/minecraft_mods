"""Compile the Whomping Willow's geometry into a .bbmodel and into Java.

    python build_scripts/generate_whomping_willow_model.py            # both
    python build_scripts/generate_whomping_willow_model.py --bbmodel  # geometry only
    python build_scripts/generate_whomping_willow_model.py --java     # splice Java only

Every other mob in this mod was sculpted in Blockbench and exported through the
Modded Entity codec. A tree is the one creature where that is the wrong tool: a
face has no formula, but a trunk taper, a segment series, a branch ring and a
leaf scatter are all f(index). The thing you actually want to iterate on here is
"is it too skinny, do the branches reach far enough" -- which is a one-character
edit to a constant below and a one-second rerun, versus a full re-sculpt.

The .bbmodel is NOT an optional intermediate. It is the input to
generate_whomping_willow_texture.py, which reads each cube's baked per-face UV
rectangle rather than carrying a hand-copied table that would rot on the first
re-pack. It is also the recovery artifact: when Blockbench is available again,
open it, sculpt the crown and the maw by hand, and re-export.

Coordinate conversion
---------------------
Authored here in Blockbench space: Y up, ground at y=0, treetop at y=180, and
-Z is the front. Minecraft's baked model has -Y up (see any model in
client/model: root sits at PartPose.offset(0, 24, 0) and everything above it is
negative). That is a reflection through the XZ plane:

    x -> x        y -> -y        z -> z

A reflection conjugates rotations as R' = M R M, which negates rotation about
the axes lying *in* the mirror plane and leaves the perpendicular one alone:

    rotX -> -rotX    rotY -> rotY    rotZ -> -rotZ

Group origins are absolute in Blockbench but relative to the parent pivot in
Java; cube corners are relative to their own group's pivot in Java and absolute
in Blockbench. Branch cubes are authored UNROTATED along +Z and the compass yaw
lives in the group rotation, so all four branches share one local frame: outward
is always +Z, droop is always +xRot, lash is always yRot. The solver in
WhompingWillowModel is one loop with a phase offset because of that.
"""

import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_BB = os.path.join(ROOT, "blockbench_models", "whomping_willow.bbmodel")
OUT_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "hogwartsmagic",
                        "client", "model", "WhompingWillowModel.java")

TEX_W, TEX_H = 512, 1024

# --- shape parameters -----------------------------------------------------------------------------
# Model is 180px tall and rendered at MODEL_SCALE 0.80 -> 180/16*0.80 = 9.00 blocks exactly.

TRUNK_RINGS = 8
RING_H      = [18, 17, 16, 15, 14, 13, 12, 11]      # 116px of trunk
RING_HALF_W = [30, 27, 24, 21, 18, 16, 14, 12]      # taper 60 -> 24: a trunk, not a pole

BRANCHES     = 4
# NOT b*90. Minecraft renders a model rotated by (180 - yaw), and Vec3.directionFromRotation maps a
# yaw to a rotation of MINUS that yaw, so a bone's local yaw is the negated world bearing with a
# half-turn on top. Using b*90 left branches 0 and 2 pointing exactly opposite the direction the
# server swung them, which is why the branch you saw move was often not the branch that hit you.
BRANCH_YAW   = [-(b * 90) - 180 for b in range(BRANCHES)]
BRANCH_HOST  = [4, 6, 4, 6]                         # ring index (0-based): staggered, not a flat cross
BRANCH_LIFT  = [6, 4, 6, 4]                         # px above the host ring's base
SEGS         = 5
SEG_LEN      = [33, 29, 25, 22, 18]                 # 127px chain -> ~7.1 blocks reach vs a 6.8 arc
SEG_THICK    = [18, 15, 12, 10, 8]                  # heavy at the shoulder, whip-thin at the tip

# Radians, baked into PartPose, and CUMULATIVE down the chain -- each segment inherits its parent's
# angle. Positive is downward. The branch lifts slightly, carries out flat, then droops at the tip:
# that is a willow, and it is also the only shape that keeps the horizontal reach. An earlier pass
# used a uniform positive droop totalling 126 degrees, which curled the branches under the tree and
# left barely 4 blocks of reach against a 6.8 damage arc -- the branches would have hit things they
# visibly could not touch.
REST_DROOP   = [-0.25, 0.15, 0.20, 0.30, 0.45]

CROWN_H = 64
GEO_SEED = 0x77000D


def rnd(n):
    """Deterministic 0..1 from an integer. No RNG object: the same tree every run, forever."""
    h = (n * 2654435761 ^ GEO_SEED) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def jit(n, amount):
    return (rnd(n) - 0.5) * 2.0 * amount


# --- scene graph ----------------------------------------------------------------------------------

class Cube:
    __slots__ = ("name", "fr", "to", "kind", "uv")

    def __init__(self, name, fr, to, kind):
        self.name, self.fr, self.to, self.kind = name, list(fr), list(to), kind
        self.uv = None

    def size(self):
        return [abs(self.to[i] - self.fr[i]) for i in range(3)]


class Bone:
    def __init__(self, name, origin, rot=(0.0, 0.0, 0.0)):
        self.name, self.origin, self.rot = name, list(origin), list(rot)
        self.cubes, self.children = [], []

    def child(self, name, origin, rot=(0.0, 0.0, 0.0)):
        b = Bone(name, origin, rot)
        self.children.append(b)
        return b

    def box(self, name, cx, cy, cz, hw, h, hd, kind):
        """A box by centre-x/z, base-y, half-width, height, half-depth."""
        self.cubes.append(Cube(name, [cx - hw, cy, cz - hd], [cx + hw, cy + h, cz + hd], kind))


def build():
    root = Bone("root", (0, 0, 0))
    willow = root.child("willow", (0, 0, 0))          # master: lean, heave, death topple

    # Roots: a low plate plus radial buttresses, so the trunk sits in the ground
    # rather than on it. Nothing here moves independently.
    roots = willow.child("roots", (0, 0, 0))
    for i in range(8):
        a = i * math.pi / 4.0
        d = 20 + rnd(500 + i) * 10
        roots.box("root_k%d" % (i + 1),
                  math.cos(a) * d, 0, math.sin(a) * d,
                  6 + rnd(520 + i) * 3, 5 + rnd(540 + i) * 5, 6 + rnd(560 + i) * 3, "root")
    roots.box("root_plate", 0, 0, 0, 26, 4, 26, "root")

    # Trunk: a nested chain so a bend low down compounds all the way to the crown.
    parent, y = willow, 0
    for i in range(TRUNK_RINGS):
        hw = RING_HALF_W[i]
        bone = parent.child("trunk%d" % (i + 1), (0, y, 0),
                            (0.0, math.degrees(jit(i, 0.06)), 0.0))
        bone.box("trunk%d_c" % (i + 1), jit(100 + i, 1.5), y, jit(200 + i, 1.5),
                 hw, RING_H[i], hw, "bark")
        # a knuckle on alternating sides, so the silhouette is not a smooth cone
        if i % 2 == 0 and i < 6:
            side = 1 if (i // 2) % 2 == 0 else -1
            bone.box("trunk%d_knuckle" % (i + 1), side * (hw - 2), y + 4, jit(300 + i, 3),
                     4, 7, 5, "bark")
        parent, y = bone, y + RING_H[i]

    trunk_top = parent

    # The maw, low and facing front. Three boxes: a hollow, an upper lip and a jaw.
    maw = build_maw(find(root, "trunk3"))

    # Branches hang off two different rings so they do not read as a flat cross.
    for b in range(BRANCHES):
        host = find(root, "trunk%d" % (BRANCH_HOST[b] + 1))
        host_base = sum(RING_H[:BRANCH_HOST[b]])
        build_branch(host, b, host_base + BRANCH_LIFT[b], RING_HALF_W[BRANCH_HOST[b]])

    # Crown: gnarled masses plus leaf slabs, tip at y=180.
    crown = trunk_top.child("crown", (0, y, 0))
    for i in range(7):
        a = i * math.pi * 2.0 / 7.0 + 0.4
        d = 11 + rnd(700 + i) * 12
        crown.box("crown_m%d" % (i + 1), math.cos(a) * d, y + 2 + rnd(720 + i) * 18,
                  math.sin(a) * d, 9 + rnd(740 + i) * 5, 12 + rnd(760 + i) * 14,
                  9 + rnd(780 + i) * 5, "bark")
    for i in range(16):
        a = i * math.pi * 2.0 / 16.0 + 0.7
        d = 14 + rnd(800 + i) * 22
        crown.box("crown_leaf%d" % (i + 1), math.cos(a) * d, y + 14 + rnd(820 + i) * 40,
                  math.sin(a) * d, 15, 4, 15, "leaf")
    crown.box("crown_cap", 0, 168, 0, 16, 12, 16, "leaf")
    return root


def build_maw(trunk3):
    base = sum(RING_H[:2])
    maw = trunk3.child("maw", (0, base + 6, -RING_HALF_W[2]))
    maw.box("maw_upper", 0, base + 10, -RING_HALF_W[2] - 1, 8, 6, 3, "bark")
    maw.box("maw_gum", 0, base + 4, -RING_HALF_W[2] + 1, 7, 8, 2, "maw")
    jaw = maw.child("maw_jaw", (0, base + 4, -RING_HALF_W[2]))
    jaw.box("maw_lower", 0, base, -RING_HALF_W[2] - 1, 8, 5, 3, "bark")
    return maw


def build_branch(host, b, lift, host_hw):
    """One branch: five segments, authored straight along +Z with the compass yaw in the group
    rotation, so all four branches share one local frame.

    Blockbench cube corners and group origins are BOTH absolute, so z accumulates down the chain --
    authoring each segment at its own local offset instead collapses all five onto the trunk.
    """
    yaw = BRANCH_YAW[b]
    parent = host
    z = host_hw - 2
    for s in range(SEGS):
        name = "branch%d_s%d" % (b + 1, s + 1)
        rot = (math.degrees(REST_DROOP[s]), yaw if s == 0 else 0.0, 0.0)
        bone = parent.child(name, (0, lift, z), rot)
        t = SEG_THICK[s]
        bone.box(name + "_c", 0, lift - t / 2.0, z + SEG_LEN[s] / 2.0,
                 t / 2.0, t, SEG_LEN[s] / 2.0, "twig" if s >= 3 else "bark")
        if s >= 1:
            bone.box("branch%d_leaf%d" % (b + 1, s), 0, lift + 1, z + SEG_LEN[s] * 0.65,
                     13 - s, 4, 13 - s, "leaf")
        # Withes: thin strands hanging off the underside. A willow without these reads as an oak
        # with its branches nailed on, which is exactly what the first pass looked like.
        if s >= 1:
            for k, (wx, wz, wl) in enumerate((
                    (-7 + s, 0.30, 20 - s * 2), (6 - s, 0.55, 26 - s * 3), (0, 0.80, 22 - s * 2))):
                bone.box("branch%d_withe%d_%d" % (b + 1, s, k),
                         wx, lift - wl, z + SEG_LEN[s] * wz, 1.5, wl, 1.5, "leaf")
        parent, z = bone, z + SEG_LEN[s]


def find(bone, name):
    if bone.name == name:
        return bone
    for c in bone.children:
        r = find(c, name)
        if r:
            return r
    return None


def walk(bone):
    yield bone
    for c in bone.children:
        yield from walk(c)


# --- UV packing -----------------------------------------------------------------------------------
# A fully unique pack does not fit comfortably, so cubes that share (size, material) and are not
# part of the hero silhouette share one footprint. The painter dedupes by uv_offset, so a shared
# rect is painted once and the RNG stream stays stable.

SHAREABLE = ("root", "leaf", "twig", "bark")


def pack(cubes):
    shelf_x, shelf_y, shelf_h, classes = 0, 0, 0, {}
    for c in sorted(cubes, key=lambda c: c.name):
        w, h, d = [int(math.ceil(v)) for v in c.size()]
        fw, fh = 2 * d + 2 * w, d + h
        hero = c.name.startswith(("trunk", "crown_m", "maw")) and not c.name.startswith("crown_leaf")
        key = ("!" + c.name,) if (hero or c.kind not in SHAREABLE) else (c.kind, w, h, d)
        if key in classes:
            c.uv = classes[key]
            continue
        if shelf_x + fw > TEX_W:
            shelf_x, shelf_y, shelf_h = 0, shelf_y + shelf_h, 0
        if shelf_y + fh > TEX_H:
            raise SystemExit("UV overflow: %dx%d texture is too small" % (TEX_W, TEX_H))
        c.uv = [shelf_x, shelf_y]
        classes[key] = c.uv
        shelf_x += fw
        shelf_h = max(shelf_h, fh)
    used = sum((2 * int(math.ceil(c.size()[2])) + 2 * int(math.ceil(c.size()[0]))) *
               (int(math.ceil(c.size()[2])) + int(math.ceil(c.size()[1])))
               for c in {id(c.uv): c for c in cubes}.values())
    return used


def faces(c):
    """Baked per-face rectangles, so the texture generator never needs a UV table.

    NOTE: the layout below is verified against ModelPart.Cube in the 1.21 sources. The
    equivalent comment in generate_giant_texture.py has up/down and east/west SWAPPED --
    harmless there because it reads baked rects, fatal here because this emits them.
    """
    u, v = c.uv
    w, h, d = [int(math.ceil(x)) for x in c.size()]
    r = {
        "down":  [u + d, v, u + d + w, v + d],
        "up":    [u + d + w, v, u + d + w + w, v + d],
        "west":  [u, v + d, u + d, v + d + h],
        "north": [u + d, v + d, u + d + w, v + d + h],
        "east":  [u + d + w, v + d, u + 2 * d + w, v + d + h],
        "south": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h],
    }
    return {k: {"uv": val, "texture": 0} for k, val in r.items()}


# --- emit .bbmodel --------------------------------------------------------------------------------

def emit_bbmodel(root, cubes):
    uid = [0]

    def nid():
        uid[0] += 1
        return "%08x-0000-4000-8000-%012x" % (uid[0], uid[0])

    elements, groups, outliner = [], [], []

    def emit(bone, into):
        g = {"name": bone.name, "origin": [round(v, 4) for v in bone.origin],
             "rotation": [round(v, 4) for v in bone.rot], "uuid": nid(),
             "export": True, "isOpen": True, "visibility": True, "children": []}
        groups.append(g)
        for c in bone.cubes:
            e = {"name": c.name, "box_uv": True, "rescale": False, "locked": False,
                 "from": [round(v, 4) for v in c.fr], "to": [round(v, 4) for v in c.to],
                 "autouv": 0, "color": 0, "origin": [round(v, 4) for v in bone.origin],
                 "uv_offset": c.uv, "faces": faces(c), "type": "cube", "uuid": nid()}
            elements.append(e)
            g["children"].append(e["uuid"])
        for ch in bone.children:
            emit(ch, g["children"])
        into.append(g)

    emit(root, outliner)
    model = {
        "meta": {"format_version": "5.0", "model_format": "modded_entity", "box_uv": True},
        "name": "whomping_willow", "model_identifier": "",
        "modded_entity_version": "1.17", "modded_entity_flip_y": True,
        "visible_box": [4, 12, 0],
        "resolution": {"width": TEX_W, "height": TEX_H},
        "elements": elements, "outliner": outliner, "groups": groups,
        "textures": [], "animations": [],
    }
    with open(OUT_BB, "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2)
        fh.write("\n")


# --- emit Java ------------------------------------------------------------------------------------

BEGIN = "    // <<< GENERATED GEOMETRY"
END = "    // >>> GENERATED GEOMETRY"


def f(v):
    v = round(float(v) + 0.0, 4)
    if v == 0:
        v = 0.0
    return ("%gF" % v) if v != int(v) else ("%.1fF" % v)


def java_body(root):
    out = ["    public static LayerDefinition createBodyLayer() {",
           "        MeshDefinition mesh = new MeshDefinition();",
           "        PartDefinition partdefinition = mesh.getRoot();",
           ""]

    def var(name):
        return name.replace("-", "_")

    def emit(bone, parent_var, parent_origin):
        ox = bone.origin[0] - parent_origin[0]
        oy = -(bone.origin[1] - parent_origin[1])          # y mirrors
        oz = bone.origin[2] - parent_origin[2]
        if bone.name == "root":
            ox, oy, oz = 0.0, 24.0, 0.0                    # house convention: feet at Java y=24
        rx, ry, rz = -math.radians(bone.rot[0]), math.radians(bone.rot[1]), -math.radians(bone.rot[2])
        pose = ("PartPose.offset(%s, %s, %s)" % (f(ox), f(oy), f(oz))
                if (rx == 0 and ry == 0 and rz == 0) else
                "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)"
                % (f(ox), f(oy), f(oz), f(rx), f(ry), f(rz)))
        cl = ["CubeListBuilder.create()"]
        for c in bone.cubes:
            w, h, d = c.size()
            x = min(c.fr[0], c.to[0]) - bone.origin[0]
            y = -(max(c.fr[1], c.to[1])) + bone.origin[1]   # Java min-y from Blockbench max-y
            z = min(c.fr[2], c.to[2]) - bone.origin[2]
            cl.append("            .texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s, new CubeDeformation(0.0F))"
                      % (c.uv[0], c.uv[1], f(x), f(y), f(z), f(w), f(h), f(d)))
        body = "\n".join(cl) if len(cl) > 1 else "CubeListBuilder.create()"
        out.append("        PartDefinition %s = %s.addOrReplaceChild(\"%s\", %s,\n                %s);"
                   % (var(bone.name), parent_var, bone.name, body, pose))
        for ch in bone.children:
            emit(ch, var(bone.name), bone.origin)

    emit(root, "partdefinition", [0, 0, 0])
    out += ["", "        return LayerDefinition.create(mesh, %d, %d);" % (TEX_W, TEX_H), "    }"]
    return "\n".join(out)


def splice(body):
    if not os.path.exists(OUT_JAVA):
        print("  ! %s does not exist yet -- writing the generated body to .generated.txt instead"
              % os.path.relpath(OUT_JAVA, ROOT))
        with open(OUT_JAVA + ".generated.txt", "w", encoding="utf-8") as fh:
            fh.write(body + "\n")
        return
    src = open(OUT_JAVA, encoding="utf-8").read()
    if BEGIN not in src or END not in src:
        raise SystemExit("markers %r / %r not found in %s" % (BEGIN, END, OUT_JAVA))
    head = src[:src.index(BEGIN) + len(BEGIN)]
    tail = src[src.index(END):]
    open(OUT_JAVA, "w", encoding="utf-8").write(head + "\n" + body + "\n" + tail)


# --- elevation ------------------------------------------------------------------------------------

def posed_boxes(root):
    """World-space axis-aligned boxes with every bone rotation applied.

    The authored coordinates are the UNROTATED chain -- branches lie flat along +Z until their
    group rotations bend and swing them. An elevation drawn from the raw numbers would therefore
    show a shape that never appears in game, which is worse than no elevation at all.
    """
    out = []

    def rot(p, r, o):
        x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
        rx, ry, rz = [math.radians(v) for v in r]
        y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
        x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
        x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
        return [x + o[0], y + o[1], z + o[2]]

    def visit(bone, stack):
        chain = stack + [(bone.rot, bone.origin)]
        for c in bone.cubes:
            pts = []
            for i in range(8):
                p = [c.fr[0] if i & 1 else c.to[0],
                     c.fr[1] if i & 2 else c.to[1],
                     c.fr[2] if i & 4 else c.to[2]]
                for r, o in reversed(chain):
                    p = rot(p, r, o)
                pts.append(p)
            out.append((c.kind,
                        [min(p[a] for p in pts) for a in range(3)],
                        [max(p[a] for p in pts) for a in range(3)]))
        for ch in bone.children:
            visit(ch, chain)

    visit(root, [])
    return out


def elevation(boxes, axis):
    """ASCII front/side view at 4px per character. With no Blockbench viewport this is the only
    way to catch a chimney-shaped trunk or a lopsided crown before launching the game."""
    step = 4
    lo = min(b[1][axis] for b in boxes)
    hi = max(b[2][axis] for b in boxes)
    top = max(b[2][1] for b in boxes)
    bot = min(b[1][1] for b in boxes)
    cols = int((hi - lo) / step) + 1
    rows = int((top - bot) / step) + 1
    grid = [[" "] * cols for _ in range(rows)]
    for kind, mn, mx in sorted(boxes, key=lambda b: 0 if b[0] == "leaf" else 1):
        ch = {"leaf": ".", "bark": "#", "twig": "+", "root": "=", "maw": "O"}.get(kind, "#")
        for xi in range(int((mn[axis] - lo) / step), int((mx[axis] - lo) / step) + 1):
            for yi in range(int((mn[1] - bot) / step), int((mx[1] - bot) / step) + 1):
                if 0 <= xi < cols and 0 <= yi < rows:
                    grid[yi][xi] = ch
    return "\n".join("    |" + "".join(r) for r in reversed(grid))


# --- main -----------------------------------------------------------------------------------------

if __name__ == "__main__":
    args = sys.argv[1:]
    do_bb = not args or "--bbmodel" in args
    do_java = not args or "--java" in args

    root = build()
    cubes = [c for b in walk(root) for c in b.cubes]

    names = [c.name for c in cubes]
    dupes = {n for n in names if names.count(n) > 1}
    if dupes:
        raise SystemExit("duplicate cube names (breaks the texture painter's RNG order): %s"
                         % sorted(dupes))

    used = pack(cubes)
    bones = list(walk(root))
    boxes = posed_boxes(root)
    top = max(b[2][1] for b in boxes)
    span = max(max(abs(b[1][a]), abs(b[2][a])) for b in boxes for a in (0, 2))
    reach = max(max(abs(b[1][a]), abs(b[2][a])) for b in boxes if b[0] in ("bark", "twig", "leaf")
                for a in (0, 2))

    if do_bb:
        emit_bbmodel(root, cubes)
        print("wrote %s" % os.path.relpath(OUT_BB, ROOT))
    if do_java:
        splice(java_body(root))
        print("wrote %s" % os.path.relpath(OUT_JAVA, ROOT))

    print("\n  %d cubes in %d bones   height %.0fpx (%.2f blocks at MODEL_SCALE 0.80)"
          % (len(cubes), len(bones), top, top / 16.0 * 0.80))
    print("  branch reach %.0fpx (%.2f blocks from the trunk axis)   -- BRANCH_RANGE is 6.8"
          % (reach, reach / 16.0 * 0.80))
    print("  widest extent %.0fpx   UV occupancy %.0f%% of %dx%d"
          % (span, 100.0 * used / (TEX_W * TEX_H), TEX_W, TEX_H))
    print("\n  front (looking at -Z), rotations applied:\n%s" % elevation(boxes, 0))
    print("\n  side (looking at +X), rotations applied:\n%s" % elevation(boxes, 2))
