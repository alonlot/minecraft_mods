package com.hogwartsmagic.client;

import com.hogwartsmagic.client.pensieve.ActorRuntime;
import com.hogwartsmagic.client.pensieve.MemoryActors;
import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.entity.MemoryRowboatEntity;
import com.hogwartsmagic.pensieve.scene.Rowboat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws the memory rowing boat ({@link Rowboat} holds the shape): a clinker-looking hull of tarred planks, lighter
 * weathered planks inside with ribs, a rubbing strake along the gunwale, a keel, a flat transom and a stem post, three
 * thwarts, bottom boards, iron oarlocks and two oars that stroke while the boat moves (catch, drive, a lifted and
 * feathered recovery; trailed aft when it stops). All geometry is built from {@code Rowboat}'s curves each frame, a few
 * hundred quads.
 *
 * <p>The PoseStack arrives translated to the boat's root (MemoryActors puts the heave into the position) and already
 * scaled by the actor scale; the renderer applies yaw, pitch and roll itself from {@link Rowboat#matrix}, so the boat,
 * its riders and the sea's mask all use the same rotation.
 *
 * <p>{@link #lid} draws a depth-only cover over the open hull at the gunwale, called by the sea renderer just before the
 * waves, so the water surface never shows inside the boat even though the bottom boards sit below the waterline.
 */
public class MemoryRowboatRenderer extends EntityRenderer<MemoryRowboatEntity> {
    private static final ResourceLocation HULL = mc("textures/block/spruce_planks.png");
    private static final ResourceLocation INNER = mc("textures/block/oak_planks.png");
    private static final ResourceLocation TRIM = mc("textures/block/dark_oak_planks.png");
    private static final ResourceLocation OAR = mc("textures/block/stripped_oak_log.png");
    private static final ResourceLocation IRON = mc("textures/block/anvil.png");

    /** Vertex tints: tarred outside, sun-and-salt weathered inside, darker trim, pale oars, dark iron. */
    private static final float[] HULL_TINT = {0.50f, 0.45f, 0.40f}, INNER_TINT = {0.78f, 0.70f, 0.60f},
            TRIM_TINT = {0.62f, 0.55f, 0.48f}, OAR_TINT = {0.86f, 0.80f, 0.70f}, IRON_TINT = {0.45f, 0.45f, 0.48f},
            FLOOR_TINT = {0.66f, 0.60f, 0.52f};

    /** Stations along the hull (z from stern to bow) and the strake and keel sizes. */
    private static final int STATIONS = 24;
    private static final double RAIL_DROP = 0.09, RAIL_OUT = 0.035, KEEL_HALF = 0.035, KEEL_DEPTH = 0.06;

    public MemoryRowboatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryRowboatEntity entity) {
        return HULL;
    }

    @Override
    public void render(MemoryRowboatEntity boat, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light) {
        float pitch = Mth.lerp(partialTick, boat.pitchO, boat.pitch);
        float roll = Mth.lerp(partialTick, boat.rollO, boat.roll);
        pose.pushPose();
        pose.mulPose(rotation(yaw, pitch, roll));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();

        double[][][] outer = new double[STATIONS + 1][][];
        double[][][] inner = new double[STATIONS + 1][][];
        double[] zs = new double[STATIONS + 1];
        for (int i = 0; i <= STATIONS; i++) {
            zs[i] = -Rowboat.HALF_LENGTH + 2 * Rowboat.HALF_LENGTH * i / STATIONS;
            outer[i] = section(zs[i] / Rowboat.HALF_LENGTH);
            inner[i] = insetSection(outer[i]);
        }

        Mesh hull = new Mesh(buffers.getBuffer(RenderType.entityCutoutNoCull(HULL)), m, p, light, HULL_TINT);
        Mesh in = new Mesh(buffers.getBuffer(RenderType.entityCutoutNoCull(INNER)), m, p, light, INNER_TINT);
        Mesh trim = new Mesh(buffers.getBuffer(RenderType.entityCutoutNoCull(TRIM)), m, p, light, TRIM_TINT);
        int n = Rowboat.SECTION.length;

        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < STATIONS; i++) {
                for (int j = 0; j < n - 1; j++) {
                    // Outer skin: planks run along the boat (u along z), one plank per section band.
                    hull.quadOut(pt(outer[i][j], side, zs[i]), pt(outer[i + 1][j], side, zs[i + 1]),
                            pt(outer[i + 1][j + 1], side, zs[i + 1]), pt(outer[i][j + 1], side, zs[i]),
                            zs[i], zs[i + 1], j * 0.5, (j + 1) * 0.5, side);
                    in.quadIn(pt(inner[i][j], side, zs[i]), pt(inner[i + 1][j], side, zs[i + 1]),
                            pt(inner[i + 1][j + 1], side, zs[i + 1]), pt(inner[i][j + 1], side, zs[i]),
                            zs[i], zs[i + 1], j * 0.5, (j + 1) * 0.5, side);
                }
                // The gunwale cap between the skins, and the rubbing strake outside it.
                double[] o0 = pt(outer[i][n - 1], side, zs[i]), o1 = pt(outer[i + 1][n - 1], side, zs[i + 1]);
                double[] i0 = pt(inner[i][n - 1], side, zs[i]), i1 = pt(inner[i + 1][n - 1], side, zs[i + 1]);
                trim.quad(o0, o1, i1, i0, 0, 1, zs[i], zs[i + 1], new double[]{0, 1, 0});
                double[] r0 = {o0[0] + side * RAIL_OUT, o0[1], o0[2]}, r1 = {o1[0] + side * RAIL_OUT, o1[1], o1[2]};
                double[] r0d = {r0[0], r0[1] - RAIL_DROP, r0[2]}, r1d = {r1[0], r1[1] - RAIL_DROP, r1[2]};
                double[] o0d = {o0[0], o0[1] - RAIL_DROP, o0[2]}, o1d = {o1[0], o1[1] - RAIL_DROP, o1[2]};
                trim.quad(r0d, r1d, r1, r0, zs[i], zs[i + 1], 0, RAIL_DROP, new double[]{side, 0, 0});
                trim.quad(o0, o1, r1, r0, zs[i], zs[i + 1], 0, RAIL_OUT, new double[]{0, 1, 0});
                trim.quad(o0d, o1d, r1d, r0d, zs[i], zs[i + 1], 0, RAIL_OUT, new double[]{0, -1, 0});
            }
        }

        // Keel: a narrow strip under the centreline, both sides and the bottom.
        for (int i = 0; i < STATIONS; i++) {
            double k0 = outer[i][0][1], k1 = outer[i + 1][0][1];
            double[] a0 = {KEEL_HALF, k0, zs[i]}, a1 = {KEEL_HALF, k1, zs[i + 1]};
            double[] b0 = {KEEL_HALF, k0 - KEEL_DEPTH, zs[i]}, b1 = {KEEL_HALF, k1 - KEEL_DEPTH, zs[i + 1]};
            double[] c0 = {-KEEL_HALF, k0 - KEEL_DEPTH, zs[i]}, c1 = {-KEEL_HALF, k1 - KEEL_DEPTH, zs[i + 1]};
            double[] d0 = {-KEEL_HALF, k0, zs[i]}, d1 = {-KEEL_HALF, k1, zs[i + 1]};
            trim.quad(a0, a1, b1, b0, zs[i], zs[i + 1], 0, KEEL_DEPTH, new double[]{1, 0, 0});
            trim.quad(d0, d1, c1, c0, zs[i], zs[i + 1], 0, KEEL_DEPTH, new double[]{-1, 0, 0});
            trim.quad(b0, b1, c1, c0, zs[i], zs[i + 1], 0, KEEL_HALF * 2, new double[]{0, -1, 0});
        }

        transom(hull, in, trim, outer[0], inner[0], zs[0]);
        stem(trim, outer[STATIONS], zs[STATIONS]);
        ribs(in, zs, inner);
        floorboards(in, zs, inner);
        thwarts(in);

        // Oars and oarlocks.
        ActorRuntime rt = MemoryActors.runtimeFor(boat);
        float now = PensieveClientState.sceneTime(partialTick);
        float amount = rt == null ? 0f : Rowboat.strokeAmount(rt.def, now);
        Rowboat.Oar oar = Rowboat.oar(Rowboat.phase(now), amount);
        Mesh iron = new Mesh(buffers.getBuffer(RenderType.entityCutoutNoCull(IRON)), m, p, light, IRON_TINT);
        Mesh wood = new Mesh(buffers.getBuffer(RenderType.entityCutoutNoCull(OAR)), m, p, light, OAR_TINT);
        for (int side = -1; side <= 1; side += 2) {
            double[] lock = Rowboat.alongOar(oar, 0);
            box(iron, side * lock[0], lock[1] - 0.04, lock[2], 0.035, 0.06, 0.035);
            oar(wood, oar, side);
        }
        pose.popPose();
        super.render(boat, yaw, partialTick, pose, buffers, light);
    }

    /** The rotation of {@link Rowboat#matrix} as a quaternion. */
    public static Quaternionf rotation(float yaw, float pitch, float roll) {
        double[] r = Rowboat.matrix(yaw, pitch, roll);
        // JOML's constructor takes columns: (m00, m01, m02) is the first column.
        Matrix3f m3 = new Matrix3f((float) r[0], (float) r[3], (float) r[6], (float) r[1], (float) r[4], (float) r[7],
                (float) r[2], (float) r[5], (float) r[8]);
        return new Quaternionf().setFromNormalized(m3);
    }

    // ---- The lid --------------------------------------------------------------------------------------------------

    /**
     * Depth-only cover over the hull's opening at the gunwale's inner edge, in world space relative to the camera at
     * {@code cam}, for a POSITION-format depth-mask consumer (see ModRenderTypes.DEPTH_MASK).
     */
    public static void lid(VertexConsumer vc, Matrix4f view, MemoryRowboatEntity boat, float scale, float partial, Vec3 cam) {
        float yaw = Mth.rotLerp(partial, boat.yRotO, boat.getYRot());
        float pitch = Mth.lerp(partial, boat.pitchO, boat.pitch);
        float roll = Mth.lerp(partial, boat.rollO, boat.roll);
        Matrix4f m = new Matrix4f(view)
                .translate((float) (Mth.lerp(partial, boat.xo, boat.getX()) - cam.x),
                        (float) (Mth.lerp(partial, boat.yo, boat.getY()) - cam.y),
                        (float) (Mth.lerp(partial, boat.zo, boat.getZ()) - cam.z))
                .rotate(rotation(yaw, pitch, roll))
                .scale(scale);
        int n = Rowboat.SECTION.length;
        double[][] prev = null;
        double prevZ = 0;
        for (int i = 0; i <= STATIONS; i++) {
            double z = -Rowboat.HALF_LENGTH + 2 * Rowboat.HALF_LENGTH * i / STATIONS;
            double[][] in = insetSection(section(z / Rowboat.HALF_LENGTH));
            if (prev != null) {
                double[] a = prev[n - 1], b = in[n - 1];
                pos(vc, m, a[0], a[1] - 0.01, prevZ);
                pos(vc, m, b[0], b[1] - 0.01, z);
                pos(vc, m, -b[0], b[1] - 0.01, z);
                pos(vc, m, -a[0], a[1] - 0.01, prevZ);
            }
            prev = in;
            prevZ = z;
        }
    }

    private static void pos(VertexConsumer vc, Matrix4f m, double x, double y, double z) {
        vc.addVertex(m, (float) x, (float) y, (float) z);
    }

    // ---- Geometry -------------------------------------------------------------------------------------------------

    /** The left half of the outer section at u, keel to gunwale, as {x, y}. */
    private static double[][] section(double u) {
        double[][] s = new double[Rowboat.SECTION.length][];
        for (int j = 0; j < s.length; j++) s[j] = Rowboat.sectionPoint(u, j);
        return s;
    }

    /** The inner skin: each outer point moved a plank's thickness inward along the section's normal. */
    private static double[][] insetSection(double[][] o) {
        double[][] in = new double[o.length][];
        for (int j = 0; j < o.length; j++) {
            double[] a = o[Math.max(0, j - 1)], b = o[Math.min(o.length - 1, j + 1)];
            double tx = b[0] - a[0], ty = b[1] - a[1];
            double len = Math.max(1e-6, Math.sqrt(tx * tx + ty * ty));
            double nx = ty / len, ny = -tx / len;                 // outward for the left side
            in[j] = new double[]{Math.max(0, o[j][0] - Rowboat.PLANK * nx), o[j][1] - Rowboat.PLANK * ny};
        }
        return in;
    }

    private static double[] pt(double[] xy, int side, double z) {
        return new double[]{xy[0] * side, xy[1], z};
    }

    /** The flat stern: outer and inner faces spanning the section, and a cap on top. */
    private static void transom(Mesh hull, Mesh in, Mesh trim, double[][] o, double[][] i, double z) {
        int n = o.length;
        for (int j = 0; j < n - 1; j++) {
            hull.quad(pt(o[j], 1, z), pt(o[j + 1], 1, z), pt(o[j + 1], -1, z), pt(o[j], -1, z),
                    -o[j][0], o[j][0], o[j][1], o[j + 1][1], new double[]{0, 0, -1});
            double zi = z + Rowboat.PLANK;
            in.quad(pt(i[j], 1, zi), pt(i[j + 1], 1, zi), pt(i[j + 1], -1, zi), pt(i[j], -1, zi),
                    -i[j][0], i[j][0], i[j][1], i[j + 1][1], new double[]{0, 0, 1});
        }
        double[] a = o[n - 1];
        trim.quad(pt(a, 1, z), pt(a, -1, z), pt(a, -1, z + Rowboat.PLANK + 0.02), pt(a, 1, z + Rowboat.PLANK + 0.02),
                -a[0], a[0], 0, 0.06, new double[]{0, 1, 0});
    }

    /** The stem post closing the bow: a front face from keel to gunwale and its two cheeks. */
    private static void stem(Mesh trim, double[][] o, double z) {
        int n = o.length;
        double w = 0.045;
        for (int j = 0; j < n - 1; j++) {
            double y0 = o[j][1], y1 = o[j + 1][1];
            double[] a = {w, y0, z + 0.02}, b = {w, y1 + (j == n - 2 ? 0.05 : 0), z + 0.02};
            double[] c = {-w, y1 + (j == n - 2 ? 0.05 : 0), z + 0.02}, d = {-w, y0, z + 0.02};
            trim.quad(a, b, c, d, 0, 2 * w, y0, y1, new double[]{0, 0, 1});
            trim.quad(a, b, new double[]{w, b[1], z - 0.1}, new double[]{w, y0, z - 0.1}, 0, 0.12, y0, y1, new double[]{1, 0, 0});
            trim.quad(d, c, new double[]{-w, c[1], z - 0.1}, new double[]{-w, y0, z - 0.1}, 0, 0.12, y0, y1, new double[]{-1, 0, 0});
        }
    }

    /** Frames: narrow strips standing proud of the inner skin every few stations. */
    private static void ribs(Mesh in, double[] zs, double[][][] inner) {
        double half = 0.03, proud = 0.025;
        for (int i = 2; i < zs.length - 2; i += 3) {
            double z = zs[i];
            double[][] s = inner[i];
            for (int side = -1; side <= 1; side += 2) {
                for (int j = 0; j < s.length - 1; j++) {
                    double[] a = s[j], b = s[j + 1];
                    double tx = b[0] - a[0], ty = b[1] - a[1];
                    double len = Math.max(1e-6, Math.sqrt(tx * tx + ty * ty));
                    double nx = -ty / len * proud, ny = tx / len * proud;     // inward
                    double[] p0 = {(a[0] + nx) * side, a[1] + ny, z - half}, p1 = {(a[0] + nx) * side, a[1] + ny, z + half};
                    double[] p2 = {(b[0] + nx) * side, b[1] + ny, z + half}, p3 = {(b[0] + nx) * side, b[1] + ny, z - half};
                    in.quad(p0, p1, p2, p3, 0, 0.12, j * 0.5, (j + 1) * 0.5, new double[]{-ty / len * side, tx / len, 0});
                }
            }
        }
    }

    /** Five bottom boards with small gaps, as far fore and aft as the hull is wide enough for them. */
    private static void floorboards(Mesh in, double[] zs, double[][][] inner) {
        double y = Rowboat.FLOOR, w = 0.17;
        for (int b = -2; b <= 2; b++) {
            double cx = b * 0.19;
            double z0 = Double.NaN, z1 = Double.NaN;
            for (int i = 0; i < zs.length; i++) {
                double room = Rowboat.halfWidthAt(zs[i] / Rowboat.HALF_LENGTH, y) - Rowboat.PLANK - 0.02;
                if (room >= Math.abs(cx) + w / 2 && zs[i] > -1.85 && zs[i] < 1.7) {
                    if (Double.isNaN(z0)) z0 = zs[i];
                    z1 = zs[i];
                }
            }
            if (Double.isNaN(z0) || z1 - z0 < 0.2) continue;
            in.tint = FLOOR_TINT;
            box(in, cx, y - 0.015, (z0 + z1) / 2, w / 2, 0.015, (z1 - z0) / 2);
            in.tint = INNER_TINT;
        }
    }

    /** The three thwarts, each spanning the hull at seat height. */
    private static void thwarts(Mesh in) {
        for (double z : Rowboat.THWART_Z) {
            double y = Rowboat.SEAT_TOP;
            double u0 = (z - Rowboat.THWART_DEPTH / 2) / Rowboat.HALF_LENGTH, u1 = (z + Rowboat.THWART_DEPTH / 2) / Rowboat.HALF_LENGTH;
            double half = Math.min(Rowboat.halfWidthAt(u0, y), Rowboat.halfWidthAt(u1, y)) - Rowboat.PLANK;
            in.tint = TRIM_TINT;
            box(in, 0, y - Rowboat.THWART_THICK / 2, z, half, Rowboat.THWART_THICK / 2, Rowboat.THWART_DEPTH / 2);
            in.tint = INNER_TINT;
        }
    }

    /** One oar on {@code side} (+1 left, -1 right): a square shaft through the oarlock and a blade at the end. */
    private static void oar(Mesh wood, Rowboat.Oar o, int side) {
        double[] from = Rowboat.alongOar(o, -Rowboat.OAR_INBOARD), root = Rowboat.alongOar(o, Rowboat.OAR_OUTBOARD - 0.5);
        double[] tip = Rowboat.alongOar(o, Rowboat.OAR_OUTBOARD);
        from[0] *= side;
        root[0] *= side;
        tip[0] *= side;
        double[] axis = norm(sub(tip, from));
        double[] up = norm(sub(new double[]{0, 1, 0}, scale(axis, axis[1])));
        double[] across = cross(axis, up);
        // The blade's broad face: vertical (square) at feather 0, turned flat by the feather angle.
        double f = Math.toRadians(o.feather()) * side;
        double[] broad = add(scale(up, Math.cos(f)), scale(across, Math.sin(f)));
        double[] thin = cross(axis, broad);
        prism(wood, from, root, up, across, 0.028, 0.028);
        prism(wood, root, tip, broad, thin, 0.085, 0.013);
        // A grip a little thicker at the handle end.
        prism(wood, from, add(from, scale(axis, 0.22)), up, across, 0.034, 0.034);
    }

    /** A box from a to b with half-sizes hu along u and hv along v (u, v unit and perpendicular to b - a). */
    private static void prism(Mesh mesh, double[] a, double[] b, double[] u, double[] v, double hu, double hv) {
        double len = Math.sqrt(dot(sub(b, a), sub(b, a)));
        double[][] c = new double[8][];
        int k = 0;
        for (double[] end : new double[][]{a, b}) {
            for (int s = 0; s < 4; s++) {
                double su = (s == 0 || s == 3) ? -hu : hu, sv = s < 2 ? -hv : hv;
                c[k++] = add(end, add(scale(u, su), scale(v, sv)));
            }
        }
        // Four long faces (corners 0-1 along -v, 1-2 along +u, 2-3 along +v, 3-0 along -u) and the two ends.
        int[][] faces = {{0, 1, 5, 4}, {1, 2, 6, 5}, {2, 3, 7, 6}, {3, 0, 4, 7}};
        double[][] normals = {scale(v, -1), u, v, scale(u, -1)};
        for (int i = 0; i < 4; i++) {
            int[] q = faces[i];
            mesh.quad(c[q[0]], c[q[1]], c[q[2]], c[q[3]], 0, 0.25, 0, len, normals[i]);
        }
        double[] ax = norm(sub(b, a));
        mesh.quad(c[0], c[1], c[2], c[3], 0, 0.1, 0, 0.1, scale(ax, -1));
        mesh.quad(c[4], c[5], c[6], c[7], 0, 0.1, 0, 0.1, ax);
    }

    /** An axis-aligned box centred at (x, y, z) with half-sizes (hx, hy, hz). */
    private static void box(Mesh mesh, double x, double y, double z, double hx, double hy, double hz) {
        double x0 = x - hx, x1 = x + hx, y0 = y - hy, y1 = y + hy, z0 = z - hz, z1 = z + hz;
        mesh.quad(new double[]{x0, y1, z0}, new double[]{x1, y1, z0}, new double[]{x1, y1, z1}, new double[]{x0, y1, z1},
                x0, x1, z0, z1, new double[]{0, 1, 0});
        mesh.quad(new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x0, y0, z1},
                x0, x1, z0, z1, new double[]{0, -1, 0});
        mesh.quad(new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x1, y1, z0}, new double[]{x0, y1, z0},
                x0, x1, y0, y1, new double[]{0, 0, -1});
        mesh.quad(new double[]{x0, y0, z1}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1}, new double[]{x0, y1, z1},
                x0, x1, y0, y1, new double[]{0, 0, 1});
        mesh.quad(new double[]{x0, y0, z0}, new double[]{x0, y0, z1}, new double[]{x0, y1, z1}, new double[]{x0, y1, z0},
                z0, z1, y0, y1, new double[]{-1, 0, 0});
        mesh.quad(new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1}, new double[]{x1, y1, z0},
                z0, z1, y0, y1, new double[]{1, 0, 0});
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] scale(double[] a, double k) {
        return new double[]{a[0] * k, a[1] * k, a[2] * k};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double[] norm(double[] a) {
        double l = Math.sqrt(dot(a, a));
        return l < 1e-9 ? new double[]{0, 1, 0} : scale(a, 1 / l);
    }

    /** One textured, tinted, lit consumer. UVs are in blocks (the plank textures repeat once per block). */
    private static final class Mesh {
        final VertexConsumer vc;
        final Matrix4f m;
        final PoseStack.Pose p;
        final int light;
        float[] tint;

        Mesh(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, int light, float[] tint) {
            this.vc = vc;
            this.m = m;
            this.p = p;
            this.light = light;
            this.tint = tint;
        }

        /** A quad with corners a b c d, UV rectangle u0..u1 by v0..v1 (a = (u0, v0), b = (u1, v0), c = (u1, v1)). */
        void quad(double[] a, double[] b, double[] c, double[] d, double u0, double u1, double v0, double v1, double[] n) {
            put(a, u0, v0, n);
            put(b, u1, v0, n);
            put(c, u1, v1, n);
            put(d, u0, v1, n);
        }

        /** A hull-skin quad whose normal faces away from the boat's inside (side +1 left, -1 right). */
        void quadOut(double[] a, double[] b, double[] c, double[] d, double u0, double u1, double v0, double v1, int side) {
            double[] n = norm(cross(sub(b, a), sub(d, a)));
            double[] mid = scale(add(add(a, b), add(c, d)), 0.25);
            if (n[0] * mid[0] + n[1] * (mid[1] - 0.25) < 0) n = scale(n, -1);
            quad(a, b, c, d, u0, u1, v0, v1, n);
        }

        /** As {@link #quadOut} with the normal facing the boat's inside. */
        void quadIn(double[] a, double[] b, double[] c, double[] d, double u0, double u1, double v0, double v1, int side) {
            double[] n = norm(cross(sub(b, a), sub(d, a)));
            double[] mid = scale(add(add(a, b), add(c, d)), 0.25);
            if (n[0] * mid[0] + n[1] * (mid[1] - 0.25) > 0) n = scale(n, -1);
            quad(a, b, c, d, u0, u1, v0, v1, n);
        }

        private void put(double[] q, double u, double v, double[] n) {
            vc.addVertex(m, (float) q[0], (float) q[1], (float) q[2]).setColor(tint[0], tint[1], tint[2], 1f)
                    .setUv((float) u, (float) v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(p, (float) n[0], (float) n[1], (float) n[2]);
        }
    }
}
