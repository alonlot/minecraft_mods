package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.pensieve.ActorRuntime;
import com.hogwartsmagic.client.pensieve.MemoryActors;
import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.entity.MemoryTapeEntity;
import com.hogwartsmagic.pensieve.scene.TapeTrail;
import com.hogwartsmagic.pensieve.scene.Track;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a {@code memory_tape} actor: a cream cloth measuring tape, numbered and ticked, that trails behind its brass
 * end (the actor) along the actor's own recent path ({@link TapeTrail}), and hangs where the path runs out. The flat
 * of the tape stands upright, so a tape circling the boy wraps him like a belt, with a slow ripple along it. Brass
 * caps finish both ends. Texture from {@code build_scripts/generate_memory_tape_texture.py}.
 *
 * <p>The actor {@code scale} is already on the PoseStack (MemoryActors scales it), so the tape's width and length
 * scale with it; its trail, read in world blocks, is divided back by the scale.
 */
public class MemoryTapeRenderer extends EntityRenderer<MemoryTapeEntity> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/memory_tape.png");
    /** The tape strip is u 0 to 0.5 across, the full v along; the brass swatch is u 0.5 to 1, v 0 to 0.125. */
    private static final float TAPE_U = 0.5f;
    private static final float BRASS_U0 = 0.5f, BRASS_U1 = 1f, BRASS_V1 = 0.125f;
    /** Half the tape's width, and the caps' half-length along it, half-width and half-thickness, at scale 1. */
    private static final float HALF_W = 0.035f;
    private static final float CAP_LEN = 0.03f, CAP_W = 0.042f, CAP_T = 0.012f;
    /** The ripple: degrees of twist about the tape, and its wavelength in points. */
    private static final float RIPPLE_DEG = 14f, RIPPLE_WAVE = 9f;

    public MemoryTapeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0f;
    }

    @Override
    public ResourceLocation getTextureLocation(MemoryTapeEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MemoryTapeEntity entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        ActorRuntime a = MemoryActors.runtimeFor(entity);
        Track path = a == null ? Track.EMPTY : a.def.path();
        float scale = a == null ? 1f : a.def.scale();
        float now = PensieveClientState.sceneTime(partialTick);
        float[][] pts = TapeTrail.trail(path, now, TapeTrail.LENGTH * scale, TapeTrail.POINTS);
        int n = pts.length;
        for (float[] p : pts) {
            p[0] /= scale;
            p[1] /= scale;
            p[2] /= scale;
        }

        // A frame per point: the tangent along the tape and the width direction, upright (up less its share along
        // the tangent), twisted a little by the ripple. A vertical stretch keeps the previous point's width.
        Vector3f[] tan = new Vector3f[n];
        Vector3f[] wid = new Vector3f[n];
        Vector3f lastW = new Vector3f(1, 0, 0);
        for (int i = 0; i < n; i++) {
            float[] p0 = pts[Math.max(0, i - 1)], p1 = pts[Math.min(n - 1, i + 1)];
            Vector3f t = new Vector3f(p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]);
            if (t.lengthSquared() < 1e-10f) t.set(0, -1, 0);
            t.normalize();
            Vector3f w = new Vector3f(0, 1, 0).sub(new Vector3f(t).mul(t.y));
            if (w.lengthSquared() < 1e-4f) {
                w.set(lastW);
            } else {
                w.normalize();
            }
            float twist = Mth.sin((i / RIPPLE_WAVE + now * 0.08f) * Mth.TWO_PI) * RIPPLE_DEG * Mth.DEG_TO_RAD;
            w.rotateAxis(twist, t.x, t.y, t.z);
            lastW.set(w);
            tan[i] = t;
            wid[i] = w;
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        // The cloth: one quad per step, v running the whole length (the numbers read from the brass end).
        for (int i = 0; i + 1 < n; i++) {
            float v0 = (float) i / (n - 1), v1 = (float) (i + 1) / (n - 1);
            float[] a0 = pts[i], a1 = pts[i + 1];
            Vector3f w0 = wid[i], w1 = wid[i + 1];
            Vector3f nrm = new Vector3f(tan[i]).cross(w0);
            vertex(vc, m, p, a0[0] - w0.x * HALF_W, a0[1] - w0.y * HALF_W, a0[2] - w0.z * HALF_W, 0f, v0, nrm, light);
            vertex(vc, m, p, a0[0] + w0.x * HALF_W, a0[1] + w0.y * HALF_W, a0[2] + w0.z * HALF_W, TAPE_U, v0, nrm, light);
            vertex(vc, m, p, a1[0] + w1.x * HALF_W, a1[1] + w1.y * HALF_W, a1[2] + w1.z * HALF_W, TAPE_U, v1, nrm, light);
            vertex(vc, m, p, a1[0] - w1.x * HALF_W, a1[1] - w1.y * HALF_W, a1[2] - w1.z * HALF_W, 0f, v1, nrm, light);
        }
        cap(vc, m, p, pts[0], tan[0], wid[0], light);
        cap(vc, m, p, pts[n - 1], tan[n - 1], wid[n - 1], light);
    }

    /** A brass cap: a small box centred on {@code at}, its length along {@code t} and its width along {@code w}. */
    private static void cap(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float[] at, Vector3f t, Vector3f w, int light) {
        Vector3f d = new Vector3f(t).cross(w).normalize();
        Vector3f[] axes = {t, w, d};
        float[] half = {CAP_LEN, CAP_W, CAP_T};
        // Each face: its outward axis and sign, then the two axes spanning it.
        for (int ax = 0; ax < 3; ax++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                Vector3f out = new Vector3f(axes[ax]).mul(sign);
                Vector3f a = axes[(ax + 1) % 3], b = axes[(ax + 2) % 3];
                float ha = half[(ax + 1) % 3], hb = half[(ax + 2) % 3], ho = half[ax];
                float[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
                for (int c = 0; c < 4; c++) {
                    int k = sign > 0 ? c : 3 - c;
                    float sa = corners[k][0] * ha, sb = corners[k][1] * hb;
                    float x = at[0] + out.x * ho + a.x * sa + b.x * sb;
                    float y = at[1] + out.y * ho + a.y * sa + b.y * sb;
                    float z = at[2] + out.z * ho + a.z * sa + b.z * sb;
                    float u = corners[k][0] < 0 ? BRASS_U0 : BRASS_U1;
                    float v = corners[k][1] < 0 ? 0f : BRASS_V1;
                    vertex(vc, m, p, x, y, z, u, v, out, light);
                }
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose p, float x, float y, float z, float u, float v,
                               Vector3f n, int light) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(p, n.x, n.y, n.z);
    }
}
