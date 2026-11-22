import com.hogwartsmagic.client.model.YetiModel;
import com.hogwartsmagic.entity.YetiEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Poses the real YetiModel and dumps its textured polygons in the frame format that
 * tools/werewolf_harness/render_werewolf.py draws (world blocks, y up, at the YetiRenderer scale).
 *
 *   java YetiHarness out.json label=action,time,sleep,limbSwing,amount,age,attack ...
 *   e.g. idle=NONE,0,0,0,0,0,0  walk=NONE,0,0,2,1,0,0  slam=SLAM,14,0,0,0,0,0  swipe=NONE,0,0,0,0,0,0.4
 */
public class YetiHarness {
    static final float SCALE = com.hogwartsmagic.client.renderer.YetiRenderer.SCALE;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        ModelPart top = YetiModel.createBodyLayer().bakeRoot();
        YetiModel model = new YetiModel(top);
        Field fPolys = ModelPart.Cube.class.getDeclaredField("polygons");
        fPolys.setAccessible(true);
        Class<?> poly = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon");
        Class<?> vert = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        Field fVerts = poly.getDeclaredField("vertices"); fVerts.setAccessible(true);
        Field fPos = vert.getDeclaredField("pos"); fPos.setAccessible(true);
        Field fU = vert.getDeclaredField("u"); fU.setAccessible(true);
        Field fV = vert.getDeclaredField("v"); fV.setAccessible(true);
        try (FileWriter w = new FileWriter(args[0])) {
            w.write("{\"frames\":[\n");
            for (int i = 1; i < args.length; i++) {
                String[] lv = args[i].split("=", 2);
                String[] p = lv[1].split(",");
                model.pose(YetiEntity.Action.valueOf(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]));
                model.attackTime = Float.parseFloat(p[6]);
                model.setupAnim(null, Float.parseFloat(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]), 0.0F, 0.0F);
                StringBuilder sb = new StringBuilder();
                sb.append("{\"name\":\"").append(lv[0]).append("\",\"quads\":[");
                final boolean[] first = {true};
                PoseStack ps = new PoseStack();
                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                ps.scale(-1.0F, -1.0F, 1.0F);
                ps.scale(SCALE, SCALE, SCALE);
                ps.translate(0.0F, -1.501F, 0.0F);
                final boolean showBoulder = lv[0].startsWith("throw");
                top.visit(ps, (pose, path, idx, cube) -> {
                    if (path.contains("boulder") && !showBoulder) {
                        return;     // hidden in game except while it throws
                    }
                    try {
                        for (Object pg : (Object[]) fPolys.get(cube)) {
                            Object[] vs = (Object[]) fVerts.get(pg);
                            if (!first[0]) sb.append(',');
                            first[0] = false;
                            sb.append('[');
                            for (int vi = 0; vi < vs.length; vi++) {
                                Vector3f q = (Vector3f) fPos.get(vs[vi]);
                                Vector3f v = pose.pose().transformPosition(q.x / 16f, q.y / 16f, q.z / 16f, new Vector3f());
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
}
