import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Vector3f;

import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Bakes entity LayerDefinitions with Minecraft's own ModelPart code and dumps every textured
 * polygon (world-space vertices in blocks, y up, entity facing +Z, plus normalised UVs) as JSON
 * for audit.py.  Args: out.json then name=fully.qualified.Class#method pairs.
 */
public class AtlasDump {
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        Field fPolys = ModelPart.Cube.class.getDeclaredField("polygons");
        fPolys.setAccessible(true);
        Class<?> poly = Class.forName("net.minecraft.client.model.geom.ModelPart$Polygon");
        Class<?> vert = Class.forName("net.minecraft.client.model.geom.ModelPart$Vertex");
        Field fVerts = poly.getDeclaredField("vertices"); fVerts.setAccessible(true);
        Field fPos = vert.getDeclaredField("pos"); fPos.setAccessible(true);
        Field fU = vert.getDeclaredField("u"); fU.setAccessible(true);
        Field fV = vert.getDeclaredField("v"); fV.setAccessible(true);
        Field fMat = LayerDefinition.class.getDeclaredField("material"); fMat.setAccessible(true);

        StringBuilder sb = new StringBuilder("{");
        boolean firstModel = true;
        for (int a = 1; a < args.length; a++) {
            String[] kv = args[a].split("=");
            String[] cm = kv[1].split("#");
            LayerDefinition def;
            try {
                Method m = Class.forName(cm[0]).getDeclaredMethod(cm[1]);
                m.setAccessible(true);
                def = (LayerDefinition) m.invoke(null);
            } catch (Throwable t) {
                System.err.println("SKIP " + kv[0] + ": " + t);
                continue;
            }
            Object mat = fMat.get(def);
            int tw = 0, th = 0;
            for (Field f : mat.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                if (f.getType() == int.class) {
                    if (tw == 0) tw = f.getInt(mat); else th = f.getInt(mat);
                }
            }
            ModelPart root = def.bakeRoot();
            PoseStack ps = new PoseStack();
            ps.mulPose(Axis.YP.rotationDegrees(180.0F));
            ps.scale(-1.0F, -1.0F, 1.0F);
            ps.translate(0.0F, -1.501F, 0.0F);
            if (!firstModel) sb.append(',');
            firstModel = false;
            sb.append('"').append(kv[0]).append("\":{\"tw\":").append(tw).append(",\"th\":").append(th).append(",\"quads\":[");
            final boolean[] first = {true};
            root.visit(ps, (pose, path, idx, cube) -> {
                try {
                    for (Object pg : (Object[]) fPolys.get(cube)) {
                        Object[] vs = (Object[]) fVerts.get(pg);
                        if (!first[0]) sb.append(',');
                        first[0] = false;
                        sb.append("{\"p\":\"").append(path).append("\",\"c\":").append(idx).append(",\"v\":[");
                        for (int vi = 0; vi < vs.length; vi++) {
                            Vector3f p = (Vector3f) fPos.get(vs[vi]);
                            Vector3f w = pose.pose().transformPosition(p.x / 16f, p.y / 16f, p.z / 16f, new Vector3f());
                            if (vi > 0) sb.append(',');
                            sb.append(String.format("%.4f,%.4f,%.4f,%.6f,%.6f", w.x, w.y, w.z, fU.getFloat(vs[vi]), fV.getFloat(vs[vi])));
                        }
                        sb.append("],\"l\":[");
                        for (int vi = 0; vi < vs.length; vi++) {
                            Vector3f p = (Vector3f) fPos.get(vs[vi]);
                            if (vi > 0) sb.append(',');
                            sb.append(String.format("%.4f,%.4f,%.4f", p.x, p.y, p.z));
                        }
                        sb.append("]}");
                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
            sb.append("]}");
            System.out.println("dumped " + kv[0] + " " + tw + "x" + th);
        }
        sb.append('}');
        try (FileWriter w = new FileWriter(args[0])) { w.write(sb.toString()); }
    }
}
