import com.hogwartsmagic.client.model.SphinxModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Poses the real SphinxModel and dumps its textured polygons in the frame format that
 * tools/werewolf_harness/render_werewolf.py draws (world blocks, y up, through MobRenderer's
 * transform at the SphinxRenderer scale).
 *
 *   java SphinxHarness out.json label=lie,angry,limbSwing,amount,attack ...
 *   e.g. lying=1,0,0,0,0  standing=0,0,0,0,0  walk=0,0,1.2,1,0  swipe=0,1,0,0,0.5
 */
public class SphinxHarness {
    static final float SCALE = com.hogwartsmagic.client.renderer.SphinxRenderer.SCALE;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        ModelPart top = SphinxModel.createBodyLayer().bakeRoot();
        SphinxModel model = new SphinxModel(top);
        Field fPolys = ModelPart.Cube.class.getDeclaredField("polygons");
        fPolys.setAccessible(true);
        Class<?> poly = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon");
        Class<?> vert = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        Field fVerts = poly.getDeclaredField("vertices"); fVerts.setAccessible(true);
        Field fPos = vert.getDeclaredField("pos"); fPos.setAccessible(true);
        Field fU = vert.getDeclaredField("u"); fU.setAccessible(true);
        Field fV = vert.getDeclaredField("v"); fV.setAccessible(true);
        Field fLie = SphinxModel.class.getDeclaredField("lie"); fLie.setAccessible(true);
        Field fAngry = SphinxModel.class.getDeclaredField("angry"); fAngry.setAccessible(true);
        try (FileWriter w = new FileWriter(args[0])) {
            w.write("{\"frames\":[\n");
            for (int i = 1; i < args.length; i++) {
                String[] lv = args[i].split("=", 2);
                String[] p = lv[1].split(",");
                top.getAllParts().forEach(ModelPart::resetPose);
                fLie.setFloat(model, Float.parseFloat(p[0]));
                fAngry.setBoolean(model, !p[1].equals("0"));
                model.attackTime = Float.parseFloat(p[4]);
                model.setupAnim(null, Float.parseFloat(p[2]), Float.parseFloat(p[3]), 0.0F, 0.0F, 0.0F);
                StringBuilder sb = new StringBuilder();
                sb.append("{\"name\":\"").append(lv[0]).append("\",\"quads\":[");
                final boolean[] first = {true};
                PoseStack ps = new PoseStack();
                ps.mulPose(Axis.YP.rotationDegrees(180.0F));
                ps.scale(-1.0F, -1.0F, 1.0F);
                ps.scale(SCALE, SCALE, SCALE);
                ps.translate(0.0F, -1.501F, 0.0F);
                top.visit(ps, (pose, path, idx, cube) -> {
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
