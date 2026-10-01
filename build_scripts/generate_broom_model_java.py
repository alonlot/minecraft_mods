"""Compile the three v2 broom .bbmodel files into BroomModelV2.java.

Hand-transcribing ~90 cubes across three models is exactly the kind of thing
that produces one wrong sign and a broom rendered inside-out, so the Java is
generated straight from the Blockbench files instead.

Coordinate conversion
---------------------
Blockbench here is authored Y-up, -Z toward the nose. The existing broom
renderer flips the baked model, so in Java-local space -Y is up (you can see
this in the old BroomModel: nimbus_top_fin sits at y=-4.1, bottom_fin at +3.1).
That makes the mapping a reflection through the XZ plane:

    x -> x        y -> -y        z -> z

A reflection conjugates rotations as R' = M R M, which negates rotation about
the axes lying *in* the mirror plane and leaves the perpendicular one alone:

    rotX -> -rotX    rotY -> rotY    rotZ -> -rotZ

Group origins are absolute in Blockbench but relative to the parent pivot in
Java, and cube corners are relative to their own group's pivot.
"""

import json
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(ROOT, "blockbench_models")
OUT_JAVA = os.path.join(
    ROOT, "src", "main", "java", "com", "hogwartsmagic", "client", "BroomModelV2.java")

# Height of the shared "broom" pivot, in model units, and therefore where a
# Blockbench y of 0 ends up. BroomRenderer finishes with the usual
# scale(-1,-1,1) + translate(0,-1.501,0), which maps a model-space y to
#
#     world_y = 1.501 - y / 16
#
# The brooms are all modelled with the shaft centred on Blockbench y=0, and
# BroomEntity.getPassengerAttachmentPoint seats the rider at y=0.45, so we want
# Blockbench y=0 to land at 0.45:  (1.501 - 0.45) * 16 = 16.8. Anything else
# leaves the rider hovering off the handle.
ROOT_Y_OFFSET = 16.8

BROOMS = [
    ("Training", "broom_shooting_star_v2.bbmodel"),
    ("Nimbus", "broom_nimbus_2000_v2.bbmodel"),
    ("Firebolt", "broom_firebolt_v2.bbmodel"),
]


def f(v):
    """Format a float as a Java float literal, trimming pointless digits."""
    v = round(float(v) + 0.0, 4)
    if v == 0:
        v = 0.0
    return ("%gF" % v) if v != int(v) else ("%.1fF" % v)


def load(path):
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def collect(model):
    return {e["uuid"]: e for e in model["elements"] if e.get("type", "cube") == "cube"}


def collect_groups(model):
    """Group name/origin/rotation live in a separate `groups` array; the
    outliner tree only carries uuids and nesting."""
    return {g["uuid"]: g for g in model.get("groups", [])}


def emit_cube(el, pivot, lines, indent):
    """One .texOffs(...).addBox(...) chain, in coordinates local to `pivot`."""
    fr, to = el["from"], el["to"]
    uv = el.get("uv_offset", [0, 0])
    # size is authored on whole units, so rounding here is exact
    sx = abs(to[0] - fr[0])
    sy = abs(to[1] - fr[1])
    sz = abs(to[2] - fr[2])
    # All three axes are relative to the group pivot, so all three subtract it.
    # Y additionally mirrors, so the box's Java minimum comes from the
    # Blockbench maximum -- but the pivot term is still a subtraction. Adding it
    # here instead threw every cube in a group with a non-zero Y origin off by
    # twice that origin (the stirrup feet and the Firebolt's feather).
    x = min(fr[0], to[0]) - pivot[0]
    y = -(max(fr[1], to[1])) - pivot[1]
    z = min(fr[2], to[2]) - pivot[2]
    lines.append("%s.texOffs(%d, %d)" % (indent, int(uv[0]), int(uv[1])))
    lines.append("%s.addBox(%s, %s, %s, %s, %s, %s)" % (
        indent, f(x), f(y), f(z), f(sx), f(sy), f(sz)))


def emit_group(node, cubes, groups, parent_pivot, parent_var, lines, depth):
    """Recursively emit a PartDefinition for one Blockbench group."""
    meta = groups.get(node["uuid"], {})
    name = meta.get("name", "group_" + node["uuid"][:6])
    origin = meta.get("origin", [0, 0, 0])
    rot = meta.get("rotation", [0, 0, 0])
    pivot = [origin[0], -origin[1], origin[2]]

    ox = pivot[0] - parent_pivot[0]
    oy = pivot[1] - parent_pivot[1]
    oz = pivot[2] - parent_pivot[2]
    # mirrored frame: X and Z rotations flip, Y keeps its sign
    rx = math.radians(-rot[0])
    ry = math.radians(rot[1])
    rz = math.radians(-rot[2])

    var = "p%d_%s" % (depth, name.replace("-", "_"))
    ind = "        "
    child_cubes = [c for c in node.get("children", []) if isinstance(c, str)]
    child_groups = [c for c in node.get("children", []) if isinstance(c, dict)]

    lines.append("")
    lines.append('%sPartDefinition %s = %s.addOrReplaceChild("%s", CubeListBuilder.create()'
                 % (ind, var, parent_var, name))
    for uuid in child_cubes:
        el = cubes.get(uuid)
        if el is not None:
            emit_cube(el, pivot, lines, ind + "        ")
    if any(abs(r) > 1e-9 for r in (rx, ry, rz)):
        lines.append("%s        , PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s));"
                     % (ind, f(ox), f(oy), f(oz), f(rx), f(ry), f(rz)))
    else:
        lines.append("%s        , PartPose.offset(%s, %s, %s));" % (ind, f(ox), f(oy), f(oz)))

    for g in child_groups:
        emit_group(g, cubes, groups, pivot, var, lines, depth + 1)


def build_layer(label, filename):
    model = load(os.path.join(MODEL_DIR, filename))
    cubes = collect(model)
    groups = collect_groups(model)
    res = model.get("resolution", {"width": 128, "height": 128})
    lines = []
    lines.append("    public static LayerDefinition create%sLayer() {" % label)
    lines.append("        MeshDefinition mesh = new MeshDefinition();")
    lines.append("        PartDefinition root = mesh.getRoot();")
    lines.append("")
    lines.append('        PartDefinition p0_broom = root.addOrReplaceChild("broom", '
                 'CubeListBuilder.create(), PartPose.offset(0.0F, %s, 0.0F));' % f(ROOT_Y_OFFSET))

    # Anything sitting at the top level hangs off the shared broom pivot, so
    # that pivot is the origin every top-level group measures itself against.
    # It has to be [0,0,0] and not [0,-ROOT_Y_OFFSET,0]: emit_group subtracts
    # this from the child's own pivot, so a non-zero value here gets added on
    # top of the offset p0_broom already carries and sinks the whole broom by
    # that much again.
    pivot0 = [0.0, 0.0, 0.0]
    loose = [u for u in model["outliner"] if isinstance(u, str)]
    if loose:
        lines.append("")
        lines.append('        p0_broom.addOrReplaceChild("loose", CubeListBuilder.create()')
        for uuid in loose:
            el = cubes.get(uuid)
            if el is not None:
                emit_cube(el, pivot0, lines, "                ")
        lines.append("                , PartPose.ZERO);")

    for node in model["outliner"]:
        if isinstance(node, dict):
            emit_group(node, cubes, groups, pivot0, "p0_broom", lines, 1)

    lines.append("")
    lines.append("        return LayerDefinition.create(mesh, %d, %d);"
                 % (int(res["width"]), int(res["height"])))
    lines.append("    }")
    return "\n".join(lines)


HEADER = '''package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
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
 * Movie-accurate broom models, one layer per variant.
 *
 * GENERATED by build_scripts/generate_broom_model_java.py from the .bbmodel
 * files in blockbench_models/ -- edit those and re-run the script rather than
 * editing this file by hand.
 *
 * The older single-mesh {@link BroomModel} is left intact so the renderer can
 * be pointed back at it if these are not wanted.
 */
public class BroomModelV2 extends EntityModel<BroomEntity> {
    public static final ModelLayerLocation TRAINING_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "broom_v2_training"), "main");
    public static final ModelLayerLocation NIMBUS_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "broom_v2_nimbus"), "main");
    public static final ModelLayerLocation FIREBOLT_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "broom_v2_firebolt"), "main");

    private final ModelPart broom;

    public BroomModelV2(ModelPart root) {
        this.broom = root.getChild("broom");
    }

    @Override
    public void setupAnim(BroomEntity entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int loadedLight, int loadedOverlay, int color) {
        // ModelPart.render walks its children, so the whole hierarchy draws from here
        broom.render(poseStack, vertexConsumer, loadedLight, loadedOverlay, color);
    }

'''


def main():
    parts = [build_layer(label, fn) for label, fn in BROOMS]
    src = HEADER + "\n\n".join(parts) + "\n}\n"
    with open(OUT_JAVA, "w", encoding="utf-8") as fh:
        fh.write(src)
    print("wrote", OUT_JAVA, len(src.splitlines()), "lines")


if __name__ == "__main__":
    main()
