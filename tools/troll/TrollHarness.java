import com.hogwartsmagic.client.model.TrollModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Poses the real troll ModelPart tree and dumps its textured polygons (world blocks, y up, entity facing +Z).
 *
 *   TrollHarness frames out.json label=FIELD@ms ...   frames for render_troll.py ("rest" for the bind pose)
 *   TrollHarness faces out.json [zero]                every polygon with bone + UV; zero = all rotations cleared
 *   TrollHarness club FIELD@ms ...                    where the held club's head is, in blocks at render scale
 */
public class TrollHarness {
    static final float SCALE = 0.9F;
    static Field fPolys, fVerts, fPos, fU, fV, fChildren, fCubes;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        ModelPart top = TrollModel.createBodyLayer().bakeRoot();
        TrollModel model = new TrollModel(top);
        fPolys = ModelPart.Cube.class.getDeclaredField("polygons"); fPolys.setAccessible(true);
        Class<?> poly = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon");
        Class<?> vert = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        fVerts = poly.getDeclaredField("vertices"); fVerts.setAccessible(true);
        fPos = vert.getDeclaredField("pos"); fPos.setAccessible(true);
        fU = vert.getDeclaredField("u"); fU.setAccessible(true);
        fV = vert.getDeclaredField("v"); fV.setAccessible(true);
        fChildren = ModelPart.class.getDeclaredField("children"); fChildren.setAccessible(true);
        fCubes = ModelPart.class.getDeclaredField("cubes"); fCubes.setAccessible(true);

        switch (args[0]) {
            case "faces" -> {
                model.root().getAllParts().forEach(ModelPart::resetPose);
                if (args.length > 2 && args[2].equals("zero")) {
                    model.root().getAllParts().forEach(p -> { p.xRot = 0; p.yRot = 0; p.zRot = 0; });
                }
                StringBuilder sb = new StringBuilder("{\"polys\":[\n");
                boolean[] first = {true};
                faces(top, "", base(1.0F), sb, first);
                sb.append("\n]}\n");
                try (FileWriter w = new FileWriter(args[1])) { w.write(sb.toString()); }
                System.out.println("wrote " + args[1]);
            }
            case "club" -> {
                for (int i = 1; i < args.length; i++) {
                    pose(model, args[i]);
                    PoseStack ps = base(SCALE);
                    Vector3f[] out = new Vector3f[5];
                    findClub(top, ps, out);
                    System.out.printf("%-22s head (x%.2f y%.2f fwd%.2f)  tip (x%.2f y%.2f fwd%.2f)  butt y%.2f%n",
                            args[i], out[0].x, out[0].y, out[0].z, out[1].x, out[1].y, out[1].z, out[2].y);
                    if (out[3] != null) {
                        System.out.printf("%-22s loose: head (x%.3f y%.3f fwd%.3f)  butt (x%.3f y%.3f fwd%.3f)  held: head (x%.3f y%.3f fwd%.3f)  butt (x%.3f y%.3f fwd%.3f)%n",
                                "", out[3].x, out[3].y, out[3].z, out[4].x, out[4].y, out[4].z, out[0].x, out[0].y, out[0].z, out[2].x, out[2].y, out[2].z);
                    }
                }
            }
            default -> {
                try (FileWriter w = new FileWriter(args[1])) {
                    w.write("{\"frames\":[\n");
                    for (int i = 2; i < args.length; i++) {
                        String[] lv = args[i].split("=", 2);
                        pose(model, lv[1]);
                        boolean loose = looseShown(lv[1]);
                        StringBuilder sb = new StringBuilder("{\"name\":\"" + lv[0] + "\",\"quads\":[");
                        boolean[] first = {true};
                        quads(top, base(SCALE), sb, first, loose ? "club" : "club_loose");
                        sb.append("]}").append(i + 1 < args.length ? ",\n" : "\n");
                        w.write(sb.toString());
                    }
                    w.write("]}\n");
                }
                System.out.println("wrote " + args[1]);
            }
        }
    }

    /** The model hides one of the two clubs; these are the clips where the held one is gone. */
    static boolean looseShown(String spec) {
        if (spec.startsWith("DISARMED") || spec.startsWith("KNOCKED_OUT")) return true;
        return spec.startsWith("GET_UP") && Float.parseFloat(spec.split("@")[1]) < 700;
    }

    static PoseStack base(float scale) {
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.scale(scale, scale, scale);
        ps.translate(0.0F, -1.501F, 0.0F);
        return ps;
    }

    static void pose(TrollModel model, String spec) throws Exception {
        model.root().getAllParts().forEach(ModelPart::resetPose);
        if (spec.equals("rest")) return;
        String[] ct = spec.split("@");
        Class<?> c = Class.forName("com.hogwartsmagic.client.model.TrollAnimations");
        AnimationDefinition def = (AnimationDefinition) c.getField(ct[0]).get(null);
        KeyframeAnimations.animate(model, def, (long) Float.parseFloat(ct[1]), 1.0F, new Vector3f());
    }

    @SuppressWarnings("unchecked")
    static Map<String, ModelPart> children(ModelPart p) throws Exception {
        return (Map<String, ModelPart>) fChildren.get(p);
    }

    @SuppressWarnings("unchecked")
    static List<ModelPart.Cube> cubes(ModelPart p) throws Exception {
        return (List<ModelPart.Cube>) fCubes.get(p);
    }

    static void findClub(ModelPart p, PoseStack ps, Vector3f[] out) throws Exception {
        ps.pushPose();
        p.translateAndRotate(ps);
        for (var e : children(p).entrySet()) {
            if (e.getKey().equals("club_loose")) {
                ps.pushPose();
                e.getValue().translateAndRotate(ps);
                out[3] = ps.last().pose().transformPosition(0, 18.5F / 16F, 0, new Vector3f());
                out[4] = ps.last().pose().transformPosition(0, -6.5F / 16F, 0, new Vector3f());
                ps.popPose();
            } else if (e.getKey().equals("club")) {
                ps.pushPose();
                e.getValue().translateAndRotate(ps);
                out[0] = ps.last().pose().transformPosition(0, 18.5F / 16F, 0, new Vector3f());
                out[1] = ps.last().pose().transformPosition(0, 24.5F / 16F, 0, new Vector3f());
                out[2] = ps.last().pose().transformPosition(0, -6.5F / 16F, 0, new Vector3f());
                ps.popPose();
            } else {
                findClub(e.getValue(), ps, out);
            }
        }
        ps.popPose();
    }

    static void quads(ModelPart p, PoseStack ps, StringBuilder sb, boolean[] first, String skip) throws Exception {
        ps.pushPose();
        p.translateAndRotate(ps);
        for (ModelPart.Cube cube : cubes(p)) {
            for (Object pg : (Object[]) fPolys.get(cube)) {
                Object[] vs = (Object[]) fVerts.get(pg);
                if (!first[0]) sb.append(',');
                first[0] = false;
                sb.append('[');
                for (int vi = 0; vi < vs.length; vi++) {
                    Vector3f q = (Vector3f) fPos.get(vs[vi]);
                    Vector3f v = ps.last().pose().transformPosition(q.x / 16f, q.y / 16f, q.z / 16f, new Vector3f());
                    if (vi > 0) sb.append(',');
                    sb.append(String.format("%.4f,%.4f,%.4f,%.5f,%.5f", v.x, v.y, v.z, fU.getFloat(vs[vi]), fV.getFloat(vs[vi])));
                }
                sb.append(']');
            }
        }
        for (var e : children(p).entrySet()) {
            if (!e.getKey().equals(skip)) quads(e.getValue(), ps, sb, first, skip);
        }
        ps.popPose();
    }

    static void faces(ModelPart p, String path, PoseStack ps, StringBuilder sb, boolean[] first) throws Exception {
        ps.pushPose();
        p.translateAndRotate(ps);
        int ci = 0;
        for (ModelPart.Cube cube : cubes(p)) {
            int fi = 0;
            for (Object pg : (Object[]) fPolys.get(cube)) {
                Object[] vs = (Object[]) fVerts.get(pg);
                if (!first[0]) sb.append(",\n");
                first[0] = false;
                sb.append("{\"bone\":\"").append(path).append("\",\"cube\":").append(ci).append(",\"face\":").append(fi).append(",\"v\":[");
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
        for (var e : children(p).entrySet()) {
            faces(e.getValue(), path.isEmpty() ? e.getKey() : path + "/" + e.getKey(), ps, sb, first);
        }
        ps.popPose();
    }
}
