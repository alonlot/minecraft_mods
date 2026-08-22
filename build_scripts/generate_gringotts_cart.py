"""Builds the Gringotts cart: one part list, three outputs that cannot disagree.

    python build_scripts/generate_gringotts_cart.py

Writes

  * `blockbench_models/gringotts_cart.bbmodel` - open it in Blockbench and it is a normal
    `modded_entity` project with named groups and packed box UVs.
  * `src/main/java/com/hogwartsmagic/client/model/GringottsCartModel.java`
  * `src/main/resources/assets/hogwartsmagic/textures/entity/gringotts_cart.png`

The three used to be authored separately, which is how a model ends up with UVs that do not match
its texture. Here the boxes are declared once and everything else is derived, so re-sizing a part
re-packs the atlas, repaints the texture and rewrites the Java in one run.

DESIGN SPACE
------------
`+X` right, `+Y` up, `+Z` toward the back of the cart, and **y=0 is the bottom of the wheels**,
which is the rail. That last choice is the one worth knowing: the corridor lays its deck two blocks
below the cart line, so anchoring the model at the railhead is what makes the cart sit on the track
instead of floating over it. The renderer only has to know one number.

The two exports negate different axes and it is worth writing down rather than rediscovering:

    Java:        x, -y, z      (entity models are authored with +Y down)
    Blockbench:  -x, y, z      (the modded_entity codec negates X on export)

THE CART ITSELF
---------------
Modelled on the Gringotts cart as the films and Hogwarts Legacy draw it, which is a Victorian mine
tub rather than a minecart: dark iron plate, a scooped waist you climb over, flared ends, spoked
wheels outboard of the body, a brass headlamp on the prow, and a burgundy bench. The waist is the
silhouette people recognise - a straight-sided box reads as a crate no matter what you paint on it.
"""

import io
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, 'src/main/resources/assets/hogwartsmagic/textures/entity/gringotts_cart.png')
JAVA = os.path.join(ROOT, 'src/main/java/com/hogwartsmagic/client/model/GringottsCartModel.java')
BBMODEL = os.path.join(ROOT, 'blockbench_models/gringotts_cart.bbmodel')

ATLAS_W, ATLAS_H = 256, 128


class Box:
    """One cube, in design space. `share` names another box whose UV region it reuses."""

    def __init__(self, name, group, mat, x, y, z, share=None):
        self.name = name
        self.group = group
        self.mat = mat
        self.x0, self.x1 = x
        self.y0, self.y1 = y
        self.z0, self.z1 = z
        self.share = share
        self.uv = None

    @property
    def w(self):
        return self.x1 - self.x0

    @property
    def h(self):
        return self.y1 - self.y0

    @property
    def d(self):
        return self.z1 - self.z0

    def footprint(self):
        return 2 * (self.d + self.w), self.d + self.h


# --------------------------------------------------------------------------- the part list

BOXES = []


def box(name, group, mat, x, y, z, share=None):
    BOXES.append(Box(name, group, mat, x, y, z, share))


# Underframe. Two beams the wheels hang off, and the axle bar between them.
box('frame_left', 'body', 'iron_dark', (-11, -8), (4, 7), (-15, 15))
box('frame_right', 'body', 'iron_dark', (8, 11), (4, 7), (-15, 15), share='frame_left')
box('axle_front', 'body', 'iron_dark', (-11, 11), (7, 9), (-10, -6))
box('axle_back', 'body', 'iron_dark', (-11, 11), (7, 9), (6, 10), share='axle_front')

# The tub. Floor, then sides with a scooped waist: high at both ends, low where you climb in.
box('floor', 'body', 'iron', (-9, 9), (7, 9), (-14, 14))
box('side_left', 'body', 'iron', (-10, -8), (9, 15), (-14, 14))
box('side_right', 'body', 'iron', (8, 10), (9, 15), (-14, 14), share='side_left')
box('flare_lf', 'body', 'iron', (-10, -8), (15, 21), (-14, -6))
box('flare_rf', 'body', 'iron', (8, 10), (15, 21), (-14, -6), share='flare_lf')
box('flare_lb', 'body', 'iron', (-10, -8), (15, 20), (6, 14))
box('flare_rb', 'body', 'iron', (8, 10), (15, 20), (6, 14), share='flare_lb')

# Prow and stern. The prow is taller and carries the lamp.
box('prow', 'body', 'iron', (-8, 8), (9, 19), (-16, -14))
box('stern', 'body', 'iron', (-8, 8), (9, 20), (14, 16))

# Rivet bands around the waist, and the cap rail along the top of each side.
box('band_left', 'body', 'iron_dark', (-10.5, -7.5), (12, 14), (-14, 14))
box('band_right', 'body', 'iron_dark', (7.5, 10.5), (12, 14), (-14, 14), share='band_left')

# The bench, and the burgundy cushion on it.
box('bench', 'body', 'iron_dark', (-8, 8), (12, 13), (-4, 6))
box('cushion', 'body', 'cushion', (-8, 8), (13, 14), (-4, 6))

# Fenders: a stepped arch over each wheel, which is what reads as ironwork at a distance.
for side, sx in (('l', -1), ('r', 1)):
    for end, ez in (('f', -8), ('b', 8)):
        first = 'fender_lf_a' if not (side == 'l' and end == 'f') else None
        box('fender_%s%s_a' % (side, end), 'body', 'iron_dark',
            tuple(sorted((sx * 10.5, sx * 12.0))), (13, 15), (ez - 7, ez + 7),
            share=first)
        second = 'fender_lf_b' if not (side == 'l' and end == 'f') else None
        box('fender_%s%s_b' % (side, end), 'body', 'iron_dark',
            tuple(sorted((sx * 10.5, sx * 12.0))), (9, 13), (ez - 8, ez - 6),
            share=second)
        third = 'fender_lf_c' if not (side == 'l' and end == 'f') else None
        box('fender_%s%s_c' % (side, end), 'body', 'iron_dark',
            tuple(sorted((sx * 10.5, sx * 12.0))), (9, 13), (ez + 6, ez + 8),
            share=third)

# Wheels: flat discs with the spokes painted on. Four cubes, one UV region.
for i, (sx, ez) in enumerate(((-1, -8), (1, -8), (-1, 8), (1, 8))):
    box('wheel_%d' % i, 'wheels', 'wheel',
        tuple(sorted((sx * 11.0, sx * 12.0))), (0, 11), (ez - 5.5, ez + 5.5),
        share=None if i == 0 else 'wheel_0')

# The headlamp: the one thing on the cart that is not iron, and the thing you see first.
#
# Low and forward, not up on a post. The first pass mounted it above the prow and the preview came
# back looking like a chimney - a lamp reads as a lamp when it is out in front lighting the track,
# and as furniture the moment it is above the driver's head.
box('lamp_bracket', 'lamp', 'iron_dark', (-2, 2), (13, 16), (-18, -15))
box('lamp_body', 'lamp', 'brass', (-5, 5), (8, 18), (-24, -17))
box('lamp_lens', 'lamp', 'lens', (-4, 4), (9, 17), (-25, -24))
box('lamp_hood', 'lamp', 'iron_dark', (-6, 6), (18, 20), (-25, -17))

# The brake. Deliberately on one side only, because a perfectly symmetrical machine looks moulded.
box('brake_stand', 'body', 'iron_dark', (8, 10), (15, 17), (8, 11))
box('brake_lever', 'body', 'brass', (8.5, 9.5), (17, 22), (8, 12))


# --------------------------------------------------------------------------- UV packing

def pack():
    """Shelf-packs every box that owns its own region. Deterministic: tallest first, then by name."""
    owners = [b for b in BOXES if b.share is None]
    owners.sort(key=lambda b: (-b.footprint()[1], b.name))
    x = y = shelf = 0
    for b in owners:
        fw, fh = b.footprint()
        fw, fh = int(fw), int(fh)
        if x + fw > ATLAS_W:
            x = 0
            y += shelf
            shelf = 0
        if y + fh > ATLAS_H:
            raise SystemExit('atlas too small: %s needs %dx%d at y=%d' % (b.name, fw, fh, y))
        b.uv = (x, y)
        x += fw
        shelf = max(shelf, fh)
    by_name = {b.name: b for b in BOXES}
    for b in BOXES:
        if b.share is not None:
            b.uv = by_name[b.share].uv


def face_rects(b):
    """The six face rectangles of a box UV, in texture pixels."""
    u, v = b.uv
    w, h, d = int(b.w), int(b.h), int(b.d)
    return {
        'up': (u + d, v, w, d),
        'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h),
        'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h),
        'south': (u + 2 * d + w, v + d, w, h),
    }


# --------------------------------------------------------------------------- painting

def rect(px, r, colour):
    x0, y0, w, h = r
    for y in range(int(y0), int(y0 + h)):
        for x in range(int(x0), int(x0 + w)):
            if 0 <= x < ATLAS_W and 0 <= y < ATLAS_H:
                px[x, y] = colour


def shade(px, x, y, f):
    if not (0 <= x < ATLAS_W and 0 <= y < ATLAS_H):
        return
    r, g, b, a = px[x, y]
    px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), a)


def paint_plate(px, r, base, rivets=True):
    """Iron plate: flat base, a darker seam all round, and rivets in from the corners."""
    x0, y0, w, h = (int(v) for v in r)
    rect(px, r, base)
    for x in range(x0, x0 + w):
        shade(px, x, y0, 0.72)
        shade(px, x, y0 + h - 1, 0.66)
    for y in range(y0, y0 + h):
        shade(px, x0, y, 0.72)
        shade(px, x0 + w - 1, y, 0.66)
    if w >= 2 and h >= 2:
        for x in range(x0 + 1, x0 + w - 1):
            shade(px, x, y0 + 1, 1.16)
    if not rivets or w < 5 or h < 5:
        return
    for cx in range(x0 + 2, x0 + w - 2, 5):
        for cy in range(y0 + 2, y0 + h - 2, 5):
            shade(px, cx, cy, 1.45)
            shade(px, cx + 1, cy + 1, 0.68)


def paint_wheel(px, r):
    """A spoked wheel: tyre, hub, and eight spokes struck through it."""
    x0, y0, w, h = (int(v) for v in r)
    if w < 4 or h < 4:
        paint_plate(px, r, (34, 36, 40, 255), rivets=False)
        return
    import math
    cx, cy = x0 + w / 2.0 - 0.5, y0 + h / 2.0 - 0.5
    rad = min(w, h) / 2.0
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)
            if dist > rad:
                px[x, y] = (26, 27, 31, 255)          # corner fill, reads as shadow
            elif dist > rad - 1.6:
                px[x, y] = (74, 78, 86, 255)          # tyre
            elif dist < rad * 0.30:
                px[x, y] = (150, 116, 52, 255)        # brass hub
            else:
                ang = math.atan2(dy, dx)
                spoke = abs(math.sin(ang * 4.0)) < 0.22
                px[x, y] = (96, 100, 108, 255) if spoke else (40, 42, 47, 255)


def paint_lens(px, r):
    """The headlamp: hot in the middle, falling off to the rim."""
    import math
    x0, y0, w, h = (int(v) for v in r)
    cx, cy = x0 + w / 2.0 - 0.5, y0 + h / 2.0 - 0.5
    rad = max(1.0, min(w, h) / 2.0)
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            t = min(1.0, math.hypot(x - cx, y - cy) / rad)
            if t > 0.86:
                px[x, y] = (122, 92, 40, 255)
            else:
                k = 1.0 - t * 0.55
                px[x, y] = (int(255 * k), int(238 * k), int(176 * k), 255)


def paint_cushion(px, r):
    """Buttoned burgundy leather."""
    x0, y0, w, h = (int(v) for v in r)
    rect(px, r, (109, 31, 34, 255))
    for x in range(x0, x0 + w):
        shade(px, x, y0, 1.22)
        shade(px, x, y0 + h - 1, 0.7)
    for cx in range(x0 + 2, x0 + w - 1, 4):
        for cy in range(y0 + 2, y0 + h - 1, 4):
            shade(px, cx, cy, 0.55)


PLATES = {
    'iron': (58, 62, 68, 255),
    'iron_dark': (38, 40, 45, 255),
    'brass': (146, 112, 50, 255),
}


def paint():
    img = Image.new('RGBA', (ATLAS_W, ATLAS_H), (0, 0, 0, 0))
    px = img.load()
    done = set()
    for b in BOXES:
        if b.uv in done:
            continue
        done.add(b.uv)
        rects = face_rects(b)
        for side, r in rects.items():
            if b.mat == 'wheel':
                # Only the two flat faces are the wheel; the rim is plain iron.
                if side in ('east', 'west'):
                    paint_wheel(px, r)
                else:
                    paint_plate(px, r, (52, 55, 61, 255), rivets=False)
            elif b.mat == 'lens':
                paint_lens(px, r) if side == 'north' else paint_plate(px, r, (122, 92, 40, 255), False)
            elif b.mat == 'cushion':
                paint_cushion(px, r)
            else:
                paint_plate(px, r, PLATES[b.mat], rivets=(b.mat != 'brass'))
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    img.save(TEX)
    return img


# --------------------------------------------------------------------------- Java

JAVA_HEAD = '''package com.hogwartsmagic.client.model;

import com.hogwartsmagic.entity.GringottsCartEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The Gringotts cart. <b>Generated - edit {@code build_scripts/generate_gringotts_cart.py}.</b>
 *
 * <p>A Victorian mine tub rather than a minecart, which is what the films and Hogwarts Legacy both
 * draw: dark iron plate, a scooped waist you climb over, flared ends, spoked wheels outboard of the
 * body, a brass headlamp on the prow and a burgundy bench. The waist is the part that does the
 * work - a straight-sided box reads as a crate whatever you paint on it.
 *
 * <p><b>y=0 in the source model is the bottom of the wheels</b>, so the renderer positions the cart
 * by the railhead and nothing else has to know how tall it is. The corridor lays its deck two
 * blocks below the cart line, which is where {@link com.hogwartsmagic.client.GringottsCartRenderer}
 * gets its one offset from.
 *
 * <p>Authored un-mirrored in X on purpose. The broom renderer draws inside a
 * {@code scale(-1, -1, 1)}, which is why the code that rotates its rider has to negate every sign
 * and carries a comment explaining it. Skipping the mirror here means the rider uses the same signs
 * as the cart and that whole class of bug never arrives.
 *
 * <p>The wheels turn. It is one line in {@code setupAnim} and it is the difference between a cart
 * that is moving and a cart that is being dragged.
 */
public class GringottsCartModel extends EntityModel<GringottsCartEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "gringotts_cart"), "main");

    /** Rolling radius in model units, so the wheels turn at the speed the cart is going. */
    private static final float WHEEL_RADIUS = 5.5F;

    private final ModelPart body;
    private final ModelPart wheels;
    private final ModelPart lamp;

    public GringottsCartModel(ModelPart root) {
        this.body = root.getChild("body");
        this.wheels = root.getChild("wheels");
        this.lamp = root.getChild("lamp");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

'''

JAVA_TAIL = '''
        return LayerDefinition.create(mesh, %(w)d, %(h)d);
    }

    @Override
    public void setupAnim(GringottsCartEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Distance travelled over the rolling radius. Each wheel is its own part around a shared
        // pivot, so rotating the group turns all four together without any of them orbiting.
        this.wheels.xRot = (float) (entity.rolledDistance() / WHEEL_RADIUS);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                               int colour) {
        this.body.render(pose, buffer, light, overlay, colour);
        this.wheels.render(pose, buffer, light, overlay, colour);
        this.lamp.render(pose, buffer, light, overlay, colour);
    }
}
'''


def emit_java():
    groups = {}
    for b in BOXES:
        groups.setdefault(b.group, []).append(b)

    out = [JAVA_HEAD]
    # Wheels pivot about their own axle height so the group can simply be rotated.
    pivots = {'body': (0.0, 0.0, 0.0), 'wheels': (0.0, -5.5, 0.0), 'lamp': (0.0, 0.0, 0.0)}
    for group in ('body', 'wheels', 'lamp'):
        px, py, pz = pivots[group]
        pose = 'PartPose.ZERO' if (px, py, pz) == (0.0, 0.0, 0.0) \
            else 'PartPose.offset(%.1fF, %.1fF, %.1fF)' % (px, py, pz)
        out.append('        PartDefinition %s = root.addOrReplaceChild("%s",\n'
                   '                CubeListBuilder.create(), %s);\n' % (group, group, pose))
        for b in groups[group]:
            u, v = b.uv
            x0 = b.x0
            y0 = -b.y1 - py
            z0 = b.z0 - pz
            out.append('        %s.addOrReplaceChild("%s", CubeListBuilder.create()\n'
                       '                .texOffs(%d, %d).addBox(%.1fF, %.1fF, %.1fF, '
                       '%.1fF, %.1fF, %.1fF), PartPose.ZERO);\n'
                       % (group, b.name, u, v, x0, y0, z0, b.w, b.h, b.d))
        out.append('\n')
    out.append(JAVA_TAIL % {'w': ATLAS_W, 'h': ATLAS_H})
    io.open(JAVA, 'w', encoding='utf-8', newline='\n').write(''.join(out))


# --------------------------------------------------------------------------- Blockbench

def emit_bbmodel():
    elements = []
    outliner = []
    by_group = {}
    for i, b in enumerate(BOXES):
        uuid = '0000-0000-0000-%04d' % (i + 1)
        elements.append({
            'name': b.name,
            'box_uv': True,
            'rescale': False,
            'locked': False,
            'type': 'cube',
            'uuid': uuid,
            'inflate': 0.0,
            # X is negated on export by the modded_entity codec, so the file stores it mirrored.
            'from': [-b.x1, b.y0, b.z0],
            'to': [-b.x0, b.y1, b.z1],
            'origin': [0.0, 0.0, 0.0],
            'uv_offset': list(b.uv),
            'faces': {},
        })
        by_group.setdefault(b.group, []).append(uuid)

    for gi, group in enumerate(('body', 'wheels', 'lamp')):
        outliner.append({
            'name': group,
            'uuid': '0000-0000-0001-%04d' % gi,
            'export': True,
            'isOpen': True,
            'origin': [0.0, 5.5 if group == 'wheels' else 0.0, 0.0],
            'rotation': [0, 0, 0],
            'children': by_group.get(group, []),
        })

    doc = {
        'meta': {'format_version': '4.5', 'model_format': 'modded_entity', 'box_uv': True},
        'name': 'gringotts_cart',
        'model_identifier': 'gringotts_cart',
        'resolution': {'width': ATLAS_W, 'height': ATLAS_H},
        'elements': elements,
        'outliner': outliner,
        'textures': [{
            'path': TEX,
            'name': 'gringotts_cart.png',
            'folder': 'entity',
            'namespace': 'hogwartsmagic',
            'id': '0',
            'particle': False,
            'mode': 'link',
            'uuid': '0000-0000-0002-0000',
        }],
        'animations': [],
    }
    os.makedirs(os.path.dirname(BBMODEL), exist_ok=True)
    io.open(BBMODEL, 'w', encoding='utf-8', newline='\n').write(json.dumps(doc, indent=2) + '\n')


def main():
    for b in BOXES:
        for v in (b.x0, b.x1, b.y0, b.y1, b.z0, b.z1):
            if abs(v * 2 - round(v * 2)) > 1e-6:
                raise SystemExit('%s: %s is not on a half-unit' % (b.name, v))
    pack()
    paint()
    emit_java()
    emit_bbmodel()
    used = max((b.uv[1] + int(b.footprint()[1])) for b in BOXES)
    print('%d cubes, %d UV regions, atlas %dx%d (%d rows used)'
          % (len(BOXES), len({b.uv for b in BOXES}), ATLAS_W, ATLAS_H, used))
    print('->', os.path.relpath(TEX, ROOT))
    print('->', os.path.relpath(JAVA, ROOT))
    print('->', os.path.relpath(BBMODEL, ROOT))
    if '--preview' in __import__('sys').argv:
        out = os.path.join(ROOT, 'build', 'cart_preview.png')
        os.makedirs(os.path.dirname(out), exist_ok=True)
        print('->', preview(out))
        print('->', preview(os.path.join(ROOT, 'build', 'cart_preview_front.png'),
                            yaw=0.0, pitch=0.18, scale=11.0))
        print('->', preview(os.path.join(ROOT, 'build', 'cart_preview_side.png'),
                            yaw=1.5708, pitch=0.12, scale=11.0))




# --------------------------------------------------------------------------- looking at it

def preview(path, yaw=0.7, pitch=0.55, scale=9.0):
    """A flat-shaded orthographic render of the box list.

    Not a substitute for the game, but it catches the mistakes that matter most at this stage --
    proportion, silhouette, whether the lamp reads -- and it costs one run rather than a client
    launch. Faces are painted by material and shaded by normal, back to front.
    """
    import math
    from PIL import ImageDraw

    cy, sy = math.cos(yaw), math.sin(yaw)
    cp, sp = math.cos(pitch), math.sin(pitch)

    def project(p):
        x, y, z = p
        rx = x * cy + z * sy
        rz = -x * sy + z * cy
        ry = y * cp - rz * sp
        depth = y * sp + rz * cp
        return rx, -ry, depth

    tone = {'iron': (86, 92, 100), 'iron_dark': (52, 55, 62), 'brass': (168, 130, 58),
            'lens': (255, 238, 176), 'cushion': (122, 36, 40), 'wheel': (64, 68, 76)}
    faces = [
        ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0), 0.72),
        ((0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1), 0.82),
        ((0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0), 0.64),
        ((1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0), 0.92),
        ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1), 1.0),
        ((0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1), 0.5),
    ]

    quads = []
    for b in BOXES:
        for a, bb, c, d, lit in faces:
            corners = []
            for cx_, cy_, cz_ in (a, bb, c, d):
                corners.append(project((b.x0 + cx_ * b.w, b.y0 + cy_ * b.h, b.z0 + cz_ * b.d)))
            depth = sum(p[2] for p in corners) / 4.0
            base = tone[b.mat]
            colour = tuple(min(255, int(v * lit)) for v in base)
            quads.append((depth, [(p[0], p[1]) for p in corners], colour))
    quads.sort(key=lambda q: q[0])

    xs = [p[0] for _, pts, _ in quads for p in pts]
    ys = [p[1] for _, pts, _ in quads for p in pts]
    pad = 20
    w = int((max(xs) - min(xs)) * scale) + pad * 2
    h = int((max(ys) - min(ys)) * scale) + pad * 2
    img = Image.new('RGBA', (w, h), (14, 14, 18, 255))
    draw = ImageDraw.Draw(img)
    for _, pts, colour in quads:
        poly = [((p[0] - min(xs)) * scale + pad, (p[1] - min(ys)) * scale + pad) for p in pts]
        draw.polygon(poly, fill=colour, outline=(18, 18, 22, 255))
    img.save(path)
    return path

if __name__ == '__main__':
    main()
