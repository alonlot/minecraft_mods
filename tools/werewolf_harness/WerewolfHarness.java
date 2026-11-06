import com.hogwartsmagic.client.model.WhulvkWerewolfLycanModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Poses the real werewolf ModelPart tree with a keyframe clip at given times and dumps the posed,
 * textured polygons (world blocks, y up, through MobRenderer's transform) for render_werewolf.py.
 *
 *   java WerewolfHarness out.json label=Class.field@ms [label=Class.field@ms ...]
 *   label=rest            -> bind pose, no clip
 *   label=pose:Class.method@t  -> a static void method(WhulvkWerewolfLycanModel, float) poser
 */
public class WerewolfHarness {
    static Field fPolys, fVerts, fPos, fU, fV;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        ModelPart top = WhulvkWerewolfLycanModel.createBodyLayer().bakeRoot();
        WhulvkWerewolfLycanModel model = new WhulvkWerewolfLycanModel(top);
        fPolys = ModelPart.Cube.class.getDeclaredField("polygons");
        fPolys.setAccessible(true);
        Class<?> poly = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon");
        Class<?> vert = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        fVerts = poly.getDeclaredField("vertices"); fVerts.setAccessible(true);
        fPos = vert.getDeclaredField("pos"); fPos.setAccessible(true);
        fU = vert.getDeclaredField("u"); fU.setAccessible(true);
        fV = vert.getDeclaredField("v"); fV.setAccessible(true);

        if (args[0].equals("--bones")) {
            dumpBones(top.getChild("root"), "root", new PoseStack(), 0);
            return;
        }
        if (args[0].equals("--faces")) {
            // --faces out.json [Class.field@ms]  -> every polygon with its owning bone path, posed
            model.root().getAllParts().forEach(ModelPart::resetPose);
            if (args.length > 2) {
                String[] ct = args[2].split("@");
                int dot = ct[0].lastIndexOf('.');
                Class<?> c = Class.forName("com.hogwartsmagic.client.model." + ct[0].substring(0, dot));
                AnimationDefinition def = (AnimationDefinition) c.getField(ct[0].substring(dot + 1)).get(null);
                KeyframeAnimations.animate(model, def, (long) Float.parseFloat(ct[1]), 1.0F, new Vector3f());
            }
            StringBuilder sb = new StringBuilder("{\"polys\":[\n");
            PoseStack ps = new PoseStack();
            ps.mulPose(Axis.YP.rotationDegrees(180.0F));
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.translate(0.0F, -1.501F, 0.0F);
            final boolean[] first = {true};
            facesVisit(top, "", ps, sb, first);
            sb.append("\n]}\n");
            try (FileWriter w = new FileWriter(args[1])) { w.write(sb.toString()); }
            System.out.println("wrote " + args[1]);
            return;
        }
        try (FileWriter w = new FileWriter(args[0])) {
            w.write("{\"frames\":[\n");
            for (int i = 1; i < args.length; i++) {
                String[] lv = args[i].split("=", 2);
                model.root().getAllParts().forEach(ModelPart::resetPose);
                if (lv.length > 1 && !lv[1].equals("rest")) {
                    String spec = lv[1];
                    boolean poser = spec.startsWith("pose:");
                    if (poser) spec = spec.substring(5);
                    String[] ct = spec.split("@");
                    int dot = ct[0].lastIndexOf('.');
                    Class<?> c = Class.forName("com.hogwartsmagic.client.model." + ct[0].substring(0, dot));
                    String member = ct[0].substring(dot + 1);
                    float t = Float.parseFloat(ct[1]);
                    if (poser) {
                        c.getMethod(member, WhulvkWerewolfLycanModel.class, float.class).invoke(null, model, t);
                    } else {
                        AnimationDefinition def = (AnimationDefinition) c.getField(member).get(null);
                        KeyframeAnimations.animate(model, def, (long) t, 1.0F, new Vector3f());
                    }
                }
                StringBuilder sb = new StringBuilder();
                sb.append("{\"name\":\"").append(lv[0]).append("\",\"quads\":[");
                final boolean[] first = {true};
                PoseStack ps = new PoseStack();
                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                ps.scale(-1.0F, -1.0F, 1.0F);
                ps.translate(0.0F, -1.501F, 0.0F);
                top.visit(ps, (pose, path, idx, cube) -> {
                    try {
                        for (Object pg : (Object[]) fPolys.get(cube)) {
                            Object[] vs = (Object[]) fVerts.get(pg);
                            if (!first[0]) sb.append(',');
                            first[0] = false;
                            sb.append('[');
                            for (int vi = 0; vi < vs.length; vi++) {
                                Vector3f p = (Vector3f) fPos.get(vs[vi]);
                                Vector3f v = pose.pose().transformPosition(p.x / 16f, p.y / 16f, p.z / 16f, new Vector3f());
                                if (vi > 0) sb.append(',');
                                sb.append(String.format("%.4f,%.4f,%.4f,%.5f,%.5f", v.x, v.y, v.z, fU.getFloat(vs[vi]), fV.getFloat(vs[vi])));
                            }
                            sb.append(']');
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
                sb.append("]}").append(i + 1 < args.length ? ",\n" : "\n");
                w.write(sb.toString());
            }
            w.write("]}\n");
        }
        System.out.println("wrote " + args[0]);
    }

    static void facesVisit(ModelPart p, String path, PoseStack ps, StringBuilder sb, boolean[] first) throws Exception {
        ps.pushPose();
        p.translateAndRotate(ps);
        Field fc = ModelPart.class.getDeclaredField("cubes");
        fc.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<ModelPart.Cube> cubes = (java.util.List<ModelPart.Cube>) fc.get(p);
        int ci = 0;
        for (ModelPart.Cube cube : cubes) {
            int fi = 0;
            for (Object pg : (Object[]) fPolys.get(cube)) {
                Object[] vs = (Object[]) fVerts.get(pg);
                if (!first[0]) sb.append(",\n");
                first[0] = false;
                sb.append("{\"bone\":\"").append(path).append("\",\"cube\":").append(ci).append(",\"face\":").append(fi)
                        .append(String.format(",\"size\":[%.3f,%.3f,%.3f]", cube.maxX - cube.minX, cube.maxY - cube.minY, cube.maxZ - cube.minZ))
                        .append(",\"v\":[");
                for (int vi = 0; vi < vs.length; vi++) {
                    Vector3f q = (Vector3f) fPos.get(vs[vi]);
                    Vector3f v = ps.last().pose().transformPosition(q.x / 16f, q.y / 16f, q.z / 16f, new Vector3f());
                    if (vi > 0) sb.append(',');
                    sb.append(String.format("[%.4f,%.4f,%.4f,%.5f,%.5f]", v.x, v.y, v.z, fU.getFloat(vs[vi]), fV.getFloat(vs[vi])));
                }
                sb.append("]}");
                fi++;
            }
            ci++;
        }
        Field f = ModelPart.class.getDeclaredField("children");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ModelPart> ch = (java.util.Map<String, ModelPart>) f.get(p);
        for (var e : ch.entrySet()) facesVisit(e.getValue(), path.isEmpty() ? e.getKey() : path + "/" + e.getKey(), ps, sb, first);
        ps.popPose();
    }

    /** Bone tree: rest pose + world pivot (blocks, y up, entity facing -Z... printed as MC model px). */
    static void dumpBones(ModelPart p, String name, PoseStack ps, int depth) throws Exception {
        ps.pushPose();
        p.translateAndRotate(ps);
        Vector3f w = ps.last().pose().transformPosition(0, 0, 0, new Vector3f());
        Vector3f fx = ps.last().pose().transformDirection(1, 0, 0, new Vector3f());
        if (!name.startsWith("cube_r") && !name.matches(".*(toe|Toe|inger|nail|Nail).*")) {
            System.out.printf("%s%-16s rest(%.2f %.2f %.2f | %.3f %.3f %.3f) pivot(%.1f %.1f %.1f) +x->(%.2f %.2f %.2f)%n",
                    "  ".repeat(depth), name, p.x, p.y, p.z, p.xRot, p.yRot, p.zRot, w.x, w.y, w.z, fx.x, fx.y, fx.z);
        }
        Field f = ModelPart.class.getDeclaredField("children");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ModelPart> ch = (java.util.Map<String, ModelPart>) f.get(p);
        for (var e : ch.entrySet()) dumpBones(e.getValue(), e.getKey(), ps, depth + 1);
        ps.popPose();
    }
}
