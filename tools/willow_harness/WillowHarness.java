import com.hogwartsmagic.client.model.WhompingWillowModel;
import com.hogwartsmagic.entity.WhompingWillowEntity.AttackState;
import com.hogwartsmagic.entity.WhompingWillowRig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 1. Parity: the rig's FK (what the server hit-tests) against Minecraft's own ModelPart + the
 *    LivingEntityRenderer transform (what the client draws), across every attack state.
 * 2. Continuity: no pose pops between sub-ticks.
 * 3. Intent: the IK targets are actually reached on the damage ticks.
 * 4. Dump posed polygons as JSON for the preview renderer.
 */
public class WillowHarness {
    static WhompingWillowModel model;
    static ModelPart top;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        top = WhompingWillowModel.createBodyLayer().bakeRoot();
        model = new WhompingWillowModel(top);
        String out = args.length > 0 ? args[0] : "frames.json";

        parity();
        continuity();
        intent();
        if (args.length > 0) dump(out);
    }

    static WhompingWillowRig.Input in(AttackState s, float t, int active, float sign, float aimYaw, float aimDist, float aimH) {
        WhompingWillowRig.Input in = new WhompingWillowRig.Input();
        in.state = s; in.tick = t; in.active = active; in.sign = sign;
        in.aimYaw = aimYaw; in.aimDist = aimDist; in.aimHeight = aimH;
        in.rouse = 1.0F; in.idle = false; in.age = 0;
        return in;
    }

    static PoseStack rendererStack(float yaw) {
        PoseStack ps = new PoseStack();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.scale(WhompingWillowRig.MODEL_SCALE, WhompingWillowRig.MODEL_SCALE, WhompingWillowRig.MODEL_SCALE);
        ps.translate(0.0F, -1.501F, 0.0F);
        return ps;
    }

    /** part name -> pose matrix, as ModelPart.visit sees it */
    static Map<String, Matrix4f> partPoses(float yaw) {
        Map<String, Matrix4f> m = new HashMap<>();
        top.visit(rendererStack(yaw), (pose, path, idx, cube) -> {
            String name = path.substring(path.lastIndexOf('/') + 1);
            m.putIfAbsent(name, new Matrix4f(pose.pose()));
        });
        return m;
    }

    static final Object[][] CASES = {
            {AttackState.SWEEP, 0f}, {AttackState.SWEEP, 9f}, {AttackState.SWEEP, 12.5f}, {AttackState.SWEEP, 15f},
            {AttackState.SWEEP, 21f}, {AttackState.SWEEP, 27f},
            {AttackState.SLAM, 6f}, {AttackState.SLAM, 12f}, {AttackState.SLAM, 16f}, {AttackState.SLAM, 20f},
            {AttackState.SWAT, 6f}, {AttackState.SWAT, 11f},
            {AttackState.GRAB, 5f}, {AttackState.GRAB, 10f},
            {AttackState.SMASH, 6f}, {AttackState.SMASH, 18f}, {AttackState.SMASH, 25f}, {AttackState.SMASH, 32f},
            {AttackState.ROOT_STOMP, 14f}, {AttackState.ROOT_STOMP, 22f},
            {AttackState.NONE, 0f}, {AttackState.DORMANT, 0f}, {AttackState.STAGGER, 6f},
    };

    static void parity() {
        double worst = 0;
        String worstAt = "";
        float[] rot = new float[WhompingWillowRig.BONES * 3];
        double[] pts = new double[21];
        for (float yaw : new float[]{0f, 90f, 180f, 270f, 37f}) {
            for (Object[] c : CASES) {
                for (int active = 0; active < 4; active++) {
                    for (float sign : new float[]{1f, -1f}) {
                        WhompingWillowRig.Input in = in((AttackState) c[0], (Float) c[1], active, sign, 0.35f * sign, 4.8f, 6.5f);
                        model.poseFrom(in, 0, 0);
                        Map<String, Matrix4f> poses = partPoses(yaw);
                        WhompingWillowRig.pose(in, rot);
                        for (int l = 0; l < 4; l++) {
                            WhompingWillowRig.limbPoints(in, rot, l, yaw, pts);
                            for (int k = 0; k <= 6; k++) {
                                String name = WhompingWillowRig.NAME[WhompingWillowRig.LIMB[l][Math.min(k, 5)]];
                                Matrix4f m = poses.get(name);
                                if (k == 6) continue;   // the tip is a fixed local offset of joint 5
                                Vector3f v = m.transformPosition(0, 0, 0, new Vector3f());
                                double e = Math.sqrt(sq(v.x - pts[k * 3]) + sq(v.y - pts[k * 3 + 1]) + sq(v.z - pts[k * 3 + 2]));
                                if (e > worst) {
                                    worst = e;
                                    worstAt = String.format("yaw %.0f %s t=%.1f active=%d limb=%d joint=%d  model(%.3f,%.3f,%.3f) rig(%.3f,%.3f,%.3f)",
                                            yaw, c[0], c[1], active, l, k, v.x, v.y, v.z, pts[k * 3], pts[k * 3 + 1], pts[k * 3 + 2]);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.printf("PARITY  worst joint error %.6f blocks%n   at %s%n", worst, worstAt);
        // and the bearing sanity check that the old model failed: rest bearing of each limb tip
        WhompingWillowRig.Input in = in(AttackState.NONE, 0, 0, 1, 0, 5, 1);
        WhompingWillowRig.pose(in, rot);
        for (int l = 0; l < 4; l++) {
            WhompingWillowRig.limbPoints(in, rot, l, 0f, pts);
            double b = Math.toDegrees(Math.atan2(-pts[18], pts[20]));
            System.out.printf("  limb %d tip bearing %7.1f (design %6.1f)  dist %.2f  h %.2f%n", l, b,
                    WhompingWillowRig.LIMB_BEARING[l], Math.hypot(pts[18], pts[20]), pts[19]);
        }
    }

    static double sq(double v) { return v * v; }

    static void continuity() {
        float[] rot = new float[WhompingWillowRig.BONES * 3];
        double[] a = new double[21], b = new double[21];
        Object[][] states = {
                {AttackState.SWEEP, 30}, {AttackState.SLAM, 34}, {AttackState.SWAT, 26}, {AttackState.GRAB, 18},
                {AttackState.SMASH, 48}, {AttackState.ROOT_STOMP, 40}, {AttackState.WAKE, 30}, {AttackState.STAGGER, 20}};
        System.out.println("CONTINUITY  biggest tip move per 0.1 tick (blocks), and tip at state start/end vs NONE");
        WhompingWillowRig.Input none = in(AttackState.NONE, 0, 0, 1, 0.35f, 4.8f, 6.5f);
        WhompingWillowRig.pose(none, rot);
        double[] n = new double[21];
        WhompingWillowRig.limbPoints(none, rot, 0, 0, n);
        for (Object[] s : states) {
            int dur = (Integer) s[1];
            double worst = 0; float at = 0;
            for (float t = 0; t < dur; t += 0.1f) {
                WhompingWillowRig.Input i1 = in((AttackState) s[0], t, 0, 1, 0.35f, 4.8f, 6.5f);
                WhompingWillowRig.pose(i1, rot);
                WhompingWillowRig.limbPoints(i1, rot, 0, 0, a);
                WhompingWillowRig.Input i2 = in((AttackState) s[0], t + 0.1f, 0, 1, 0.35f, 4.8f, 6.5f);
                WhompingWillowRig.pose(i2, rot);
                WhompingWillowRig.limbPoints(i2, rot, 0, 0, b);
                double d = Math.sqrt(sq(a[18] - b[18]) + sq(a[19] - b[19]) + sq(a[20] - b[20]));
                if (d > worst) { worst = d; at = t; }
            }
            WhompingWillowRig.Input i0 = in((AttackState) s[0], 0, 0, 1, 0.35f, 4.8f, 6.5f);
            WhompingWillowRig.pose(i0, rot);
            WhompingWillowRig.limbPoints(i0, rot, 0, 0, a);
            WhompingWillowRig.Input ie = in((AttackState) s[0], dur, 0, 1, 0.35f, 4.8f, 6.5f);
            WhompingWillowRig.pose(ie, rot);
            WhompingWillowRig.limbPoints(ie, rot, 0, 0, b);
            System.out.printf("  %-10s max %.3f at t=%.1f   start-NONE %.3f  end-NONE %.3f%n", s[0], worst, at,
                    Math.sqrt(sq(a[18] - n[18]) + sq(a[19] - n[19]) + sq(a[20] - n[20])),
                    Math.sqrt(sq(b[18] - n[18]) + sq(b[19] - n[19]) + sq(b[20] - n[20])));
        }
    }

    static void intent() {
        float[] rot = new float[WhompingWillowRig.BONES * 3];
        double[] p = new double[21];
        System.out.println("INTENT  tip (dist, height) on the damage ticks, per limb");
        Object[][] checks = {
                {AttackState.SWEEP, 9f, "sweep start (cocked) "}, {AttackState.SWEEP, 13f, "sweep mid  -> 7.0,1.0"},
                {AttackState.SWEEP, 21f, "sweep end  -> 7.0,1.0"},
                {AttackState.SLAM, 12f, "slam raised -> 4.0,9.6"}, {AttackState.SLAM, 16f, "slam strike -> 6.6,0.25"},
                {AttackState.SWAT, 11f, "swat strike -> 4.8,6.5"}, {AttackState.GRAB, 10f, "grab catch -> 4.8,0.9"},
                {AttackState.SMASH, 12f, "smash high -> 3.9,7.4"}, {AttackState.SMASH, 18f, "smash blowA -> 4.8,0.15"},
                {AttackState.SMASH, 32f, "smash blowB -> 4.8,0.15"},
        };
        for (Object[] c : checks) {
            StringBuilder sb = new StringBuilder(String.format("  %-24s", c[2]));
            for (int l = 0; l < 4; l++) {
                WhompingWillowRig.Input i = in((AttackState) c[0], (Float) c[1], l, 1, 0.0f, 4.8f, 6.5f);
                WhompingWillowRig.pose(i, rot);
                WhompingWillowRig.limbPoints(i, rot, l, 0, p);
                double minH = 99;
                for (int k = 1; k <= 6; k++) minH = Math.min(minH, p[k * 3 + 1]);
                sb.append(String.format("  L%d %.2f,%.2f (low %.2f)", l, Math.hypot(p[18], p[20]), p[19], minH));
            }
            System.out.println(sb);
        }
    }

    // --- dump --------------------------------------------------------------------------------------

    static Field fPolys, fVerts, fPos, fU, fV;

    static void dump(String out) throws Exception {
        fPolys = ModelPart.Cube.class.getDeclaredField("polygons");
        fPolys.setAccessible(true);
        Class<?> poly = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon");
        Class<?> vert = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        fVerts = poly.getDeclaredField("vertices"); fVerts.setAccessible(true);
        fPos = vert.getDeclaredField("pos"); fPos.setAccessible(true);
        fU = vert.getDeclaredField("u"); fU.setAccessible(true);
        fV = vert.getDeclaredField("v"); fV.setAccessible(true);

        Object[][] frames = {
                {"rest", AttackState.DORMANT, 0f, 0, 1f, 0f},
                {"roused", AttackState.NONE, 0f, 0, 1f, 0f},
                {"sweep_05", AttackState.SWEEP, 5f, 0, 1f, 0f},
                {"sweep_09", AttackState.SWEEP, 9f, 0, 1f, 0f},
                {"sweep_13", AttackState.SWEEP, 13f, 0, 1f, 0f},
                {"sweep_17", AttackState.SWEEP, 17f, 0, 1f, 0f},
                {"sweep_21", AttackState.SWEEP, 21f, 0, 1f, 0f},
                {"slam_12", AttackState.SLAM, 12f, 0, 1f, 0f},
                {"slam_16", AttackState.SLAM, 16f, 0, 1f, 0f},
                {"swat_11", AttackState.SWAT, 11f, 0, 1f, 0f},
                {"grab_10", AttackState.GRAB, 10f, 0, 1f, 0.3f},
                {"smash_12", AttackState.SMASH, 12f, 0, 1f, 0.3f},
                {"smash_18", AttackState.SMASH, 18f, 0, 1f, 0.3f},
                {"stomp_14", AttackState.ROOT_STOMP, 14f, 0, 1f, 0f},
                {"stomp_22", AttackState.ROOT_STOMP, 22f, 0, 1f, 0f},
        };
        float[] rot = new float[WhompingWillowRig.BONES * 3];
        double[] pts = new double[21];
        try (FileWriter w = new FileWriter(out)) {
            w.write("{\"frames\":[\n");
            for (int f = 0; f < frames.length; f++) {
                Object[] fr = frames[f];
                WhompingWillowRig.Input in = in((AttackState) fr[1], (Float) fr[2], (Integer) fr[3], (Float) fr[4], (Float) fr[5], 4.8f, 6.5f);
                if (fr[1] == AttackState.DORMANT) in.rouse = 0;
                model.poseFrom(in, 0, 0);
                WhompingWillowRig.pose(in, rot);
                WhompingWillowRig.limbPoints(in, rot, (Integer) fr[3], 0f, pts);
                StringBuilder sb = new StringBuilder();
                sb.append("{\"name\":\"").append(fr[0]).append("\",\"limb\":[");
                for (int i = 0; i < 21; i++) sb.append(i > 0 ? "," : "").append(String.format("%.4f", pts[i]));
                sb.append("],\"quads\":[");
                final boolean[] first = {true};
                final StringBuilder q = sb;
                top.visit(rendererStack(0f), (pose, path, idx, cube) -> {
                    try {
                        Object[] polys = (Object[]) fPolys.get(cube);
                        for (Object pg : polys) {
                            Object[] vs = (Object[]) fVerts.get(pg);
                            if (!first[0]) q.append(',');
                            first[0] = false;
                            q.append('[');
                            for (int vi = 0; vi < vs.length; vi++) {
                                Vector3f p = (Vector3f) fPos.get(vs[vi]);
                                Vector3f v = pose.pose().transformPosition(p.x / 16f, p.y / 16f, p.z / 16f, new Vector3f());
                                if (vi > 0) q.append(',');
                                q.append(String.format("%.4f,%.4f,%.4f,%.5f,%.5f", v.x, v.y, v.z, fU.getFloat(vs[vi]), fV.getFloat(vs[vi])));
                            }
                            q.append(']');
                        }
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                });
                sb.append("]}").append(f + 1 < frames.length ? ",\n" : "\n");
                w.write(sb.toString());
            }
            w.write("]}\n");
        }
        System.out.println("wrote " + out);
    }
}
