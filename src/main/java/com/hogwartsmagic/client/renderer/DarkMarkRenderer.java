package com.hogwartsmagic.client.renderer;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.entity.DarkMarkEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Dark Mark as a volume, not a picture: the skull is five layers of looping smoke spread through
 * depth (each on its own phase, so moving around it shows parallax and the layers churn against each
 * other), the serpent is a 3D tube that pours out of the open jaw and coils toward the viewer, smoke
 * puffs drift through the space around it, and it hangs under a slow vortex of storm cloud lit green
 * from below. It only partly turns to face the camera, so looked at from beneath it foreshortens like
 * an object in the sky.
 *
 * <p>Timeline: a flash at the point of the cast, the smoke coalescing into the skull over ~2.5s, the
 * serpent sliding out of the mouth over the next ~3.5s, then the idle writhe; over its last 80 ticks
 * the layers drift apart and fade.
 *
 * <p>Textures come from {@code build_scripts/generate_dark_mark_textures.py}.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class DarkMarkRenderer extends EntityRenderer<DarkMarkEntity> {
    private static final ResourceLocation SKULL =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/dark_mark_skull.png");
    private static final ResourceLocation FX =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/dark_mark_fx.png");
    private static final ResourceLocation VORTEX =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/dark_mark_vortex.png");

    private static final RenderType SKULL_SMOKE = ModRenderTypes.markSmoke(SKULL);
    private static final RenderType FX_SMOKE = ModRenderTypes.markSmoke(FX);
    private static final RenderType FX_GLOW = ModRenderTypes.markGlow(FX);
    private static final RenderType VORTEX_SMOKE = ModRenderTypes.markSmoke(VORTEX);

    /** Skull frame size in blocks at visual scale 1. */
    private static final float SIZE = 26.0f;
    private static final int FRAMES = 16;
    private static final int SLICES = 5;
    private static final int SNAKE_SAMPLES = 72;
    private static final int PUFFS = 36;
    private static final int FADE_TICKS = 80;

    private static final int LIGHT = LightTexture.FULL_BRIGHT;

    public DarkMarkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * Nothing here: an entity pass is too early. Vanilla draws clouds and rain after entities, and
     * with no depth written by the smoke they paint straight over a mark hanging in front of them.
     * {@link #onRenderLevelStage} draws every mark after the weather instead.
     */
    @Override
    public void render(DarkMarkEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        float partialTick = event.getPartialTick();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        boolean drew = false;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof DarkMarkEntity mark && !mark.isRemoved()) {
                Vec3 origin = mark.getPosition(partialTick);
                if (origin.distanceToSqr(cam) > 512.0D * 512.0D) continue;
                // Identity plus a camera-relative offset: LevelRenderer already applied the camera
                // rotation, so the event's pose stack must not be multiplied in again.
                Matrix4f m = new Matrix4f().translate((float) (origin.x - cam.x), (float) (origin.y - cam.y),
                        (float) (origin.z - cam.z));
                draw(mark, partialTick, m, origin, cam, camera.rotation(), buffer);
                drew = true;
            }
        }
        if (drew) {
            buffer.endBatch(VORTEX_SMOKE);
            buffer.endBatch(FX_GLOW);
            buffer.endBatch(SKULL_SMOKE);
            buffer.endBatch(FX_SMOKE);
        }
    }

    private static void draw(DarkMarkEntity entity, float partialTick, Matrix4f m, Vec3 origin, Vec3 cam,
                             Quaternionf camRot, MultiBufferSource buffer) {
        float a = entity.tickCount + partialTick;
        float s = SIZE * entity.getVisualScale();

        float form = smooth(4f, 55f, a);
        float fade = Mth.clamp((entity.getRemainingTicks() - partialTick) / FADE_TICKS, 0f, 1f);
        float presence = form * fade;
        if (presence <= 0.001f && a > 30f) {
            return;
        }
        // the layers start scattered and pull together; at the end they drift apart again
        float spread = 1f + 3f * (1f - easeOut(form)) + 2.5f * (1f - fade);
        float grow = (0.55f + 0.45f * easeOut(form)) * (1f + 0.15f * (1f - fade));
        float flicker = 0.92f + 0.08f * Mth.sin(a * 0.11f) * Mth.sin(a * 0.037f + 1.7f);

        // --- frame: centre of the skull, facing partly toward the camera -------------------------
        Vector3f c = new Vector3f(0f, s * 0.55f + Mth.sin(a * 0.025f) * s * 0.012f, 0f);
        float dx = (float) (cam.x - origin.x);
        float dy = (float) (cam.y - origin.y) - c.y;
        float dz = (float) (cam.z - origin.z);
        float horiz = Mth.sqrt(dx * dx + dz * dz);
        float fx = horiz > 0.01f ? dx / horiz : 0f;
        float fz = horiz > 0.01f ? dz / horiz : 1f;
        float tilt = 0.55f * (float) Math.atan2(dy, Math.max(horiz, 0.01f));
        Vector3f fwd = new Vector3f(fx * Mth.cos(tilt), Mth.sin(tilt), fz * Mth.cos(tilt));
        Vector3f right = new Vector3f(fz, 0f, -fx);                       // cross(-fwd, up), flattened
        right.normalize();
        Vector3f up = new Vector3f(fwd).cross(right).normalize();
        float roll = 0.045f * Mth.sin(a * 0.021f);
        Vector3f r2 = new Vector3f(right).mul(Mth.cos(roll)).add(new Vector3f(up).mul(Mth.sin(roll)));
        Vector3f u2 = new Vector3f(up).mul(Mth.cos(roll)).sub(new Vector3f(right).mul(Mth.sin(roll)));
        Frame f = new Frame(c, r2, u2, fwd, s);
        Vector3f camLocal = f.toLocal(new Vector3f(dx, (float) (cam.y - origin.y), dz));

        Vector3f camRight = camRot.transform(new Vector3f(1f, 0f, 0f));
        Vector3f camUp = camRot.transform(new Vector3f(0f, 1f, 0f));

        // --- 1. storm vortex overhead ----------------------------------------------------------------
        float sky = smooth(0f, 120f, a) * fade;
        if (sky > 0.01f) {
            VertexConsumer vc = buffer.getBuffer(VORTEX_SMOKE);
            Vector3f vc0 = new Vector3f(c).add(0f, s * 0.70f, 0f);
            horizontal(vc, m, vc0, s * 2.9f, a * 0.0035f, 0f, 0f, 1f, 1f, 0.82f, 0.86f, 0.84f, 0.85f * sky, false);
            horizontal(vc, m, new Vector3f(vc0).add(0f, s * 0.08f, 0f), s * 2.1f, -a * 0.0055f + 1.3f,
                    1f, 0f, 0f, 1f, 0.9f, 0.95f, 0.92f, 0.65f * sky, false);
        }

        // --- 2. glow behind the skull and on the cloud base ------------------------------------------
        VertexConsumer glow = buffer.getBuffer(FX_GLOW);
        if (sky > 0.01f) {
            Vector3f under = new Vector3f(c).add(0f, s * 0.66f, 0f);
            horizontal(glow, m, under, s * 1.9f, 0f, 0.5f, 0f, 0.75f, 0.25f, 1f, 1f, 1f, 0.45f * sky * flicker, true);
        }
        float flash = a < 24f ? 1f - a / 24f : 0f;
        glowQuad(glow, m, f.world(0f, -0.02f, -0.12f * spread), f.right, f.up, s * 1.35f * grow,
                0.5f, 0f, 0.75f, 0.25f, 0.65f * presence * flicker + 0.8f * flash * flash);

        // --- 3. the skull volume ---------------------------------------------------------------------
        VertexConsumer skull = buffer.getBuffer(SKULL_SMOKE);
        float frameClock = a / 5f;
        for (int k = 0; k < SLICES; k++) {
            float zt = k / (float) (SLICES - 1) - 0.5f;                 // -0.5 back .. 0.5 front
            float z = zt * 0.16f * spread;
            float bulge = 1f - 0.4f * zt * zt;                           // rounder in the middle
            float half = 0.5f * s * grow * bulge;
            float ox = 0.006f * Mth.sin(a * 0.05f + k * 1.9f) * spread;
            float oy = 0.006f * Mth.cos(a * 0.043f + k * 2.3f) * spread + 0.02f * (spread - 1f) * zt;
            float alpha = (0.40f + 0.28f * (1f - 4f * zt * zt)) * presence;
            float clock = frameClock + k * 3.3f;
            int f0 = Mth.floor(clock);
            float blend = clock - f0;
            Vector3f at = f.world(ox, oy, z);
            skullFrame(skull, m, at, f.right, f.up, half, Math.floorMod(f0, FRAMES), alpha);
            skullFrame(skull, m, new Vector3f(at).add(new Vector3f(f.fwd).mul(0.002f * s)), f.right, f.up, half,
                    Math.floorMod(f0 + 1, FRAMES), alpha * blend);
        }

        // --- 4. serpent and drifting smoke -----------------------------------------------------------
        float snakeLen = easeInOut(Mth.clamp((a - 35f) / 70f, 0f, 1f));
        VertexConsumer smoke = buffer.getBuffer(FX_SMOKE);
        Snake snake = snakeLen > 0.02f ? buildSnake(f, camLocal, snakeLen, a, grow) : null;
        if (snake != null) {
            drawSnakeBody(smoke, m, snake, 1f, 0.5f, 0.625f, presence, false);
            drawSnakeHead(smoke, m, snake, 0.5f, 0.25f, 0.75f, 0.5f, presence, false);
        }
        drawPuffs(smoke, m, f, camRight, camUp, a, presence);

        // --- 5. additive bloom over everything ------------------------------------------------------
        glow = buffer.getBuffer(FX_GLOW);
        glowQuad(glow, m, f.world(0f, 0f, 0.1f * spread), f.right, f.up, s * 0.5f * grow * 1.04f,
                0f, 0f, 0.5f, 0.5f, 0.55f * presence * flicker);
        if (snake != null) {
            drawSnakeBody(glow, m, snake, 2.6f, 0.625f, 0.75f, 0.5f * presence * flicker, true);
            drawSnakeHead(glow, m, snake, 0f, 0.75f, 0.25f, 1f, 0.6f * presence * flicker, true);
        }
        if (flash > 0f) {
            float k = 1f - flash;
            glowQuad(glow, m, new Vector3f(c), camRight, camUp, s * (0.3f + 1.6f * easeOut(k)),
                    0.75f, 0f, 1f, 0.25f, 1.3f * flash * flash);
        }
    }

    // =============================================================================================
    // local frame: x right, y up, z toward the camera, in units of the skull size
    // =============================================================================================
    private record Frame(Vector3f c, Vector3f right, Vector3f up, Vector3f fwd, float s) {
        Vector3f world(float x, float y, float z) {
            return new Vector3f(c)
                    .add(new Vector3f(right).mul(x * s))
                    .add(new Vector3f(up).mul(y * s))
                    .add(new Vector3f(fwd).mul(z * s));
        }

        Vector3f toLocal(Vector3f worldRel) {
            Vector3f d = new Vector3f(worldRel).sub(c);
            return new Vector3f(d.dot(right), d.dot(up), d.dot(fwd)).div(s);
        }
    }

    private static void skullFrame(VertexConsumer vc, Matrix4f m, Vector3f at, Vector3f right, Vector3f up,
                                   float half, int frame, float alpha) {
        if (alpha <= 0.002f) return;
        float u0 = (frame % 4) * 0.25f, v0 = (frame / 4) * 0.25f;
        quad(vc, m, at, new Vector3f(right).mul(half), new Vector3f(up).mul(half),
                u0, v0, u0 + 0.25f, v0 + 0.25f, 1f, 1f, 1f, alpha);
    }

    // =============================================================================================
    // serpent
    // =============================================================================================
    private record Snake(Vector3f[] pos, Vector3f[] side, float[] width, float[] u, Vector3f headDir,
                         Vector3f headSide, float headSize, float scale) {
    }

    /** Point along the serpent's path, local units; s=0 at the mouth, the head is at s=len. */
    private static Vector3f path(float s, float a) {
        float phi = Mth.TWO_PI * 1.15f * s - a * 0.07f;
        float env = smooth(0f, 0.3f, s);
        float x = 0.21f * env * Mth.sin(phi) + 0.10f * smooth(0.75f, 1f, s);
        float y = -0.215f - 0.78f * s + 0.12f * smooth(0.82f, 1f, s) * smooth(0.82f, 1f, s);
        // always pushing out of the jaw toward the viewer; the coil only sways around that line
        float z = 0.04f + 0.24f * s + 0.05f * env * Mth.cos(phi);
        return new Vector3f(x, y, z);
    }

    private static Snake buildSnake(Frame f, Vector3f camLocal, float len, float a, float grow) {
        int n = SNAKE_SAMPLES;
        Vector3f[] local = new Vector3f[n + 1];
        for (int i = 0; i <= n; i++) {
            local[i] = path(len * i / n, a).mul(grow);
        }
        Vector3f[] pos = new Vector3f[n + 1];
        Vector3f[] side = new Vector3f[n + 1];
        float[] width = new float[n + 1];
        float[] u = new float[n + 1];
        Vector3f tangent = new Vector3f();
        for (int i = 0; i <= n; i++) {
            Vector3f prev = local[Math.max(0, i - 1)], next = local[Math.min(n, i + 1)];
            tangent.set(next).sub(prev).normalize();
            Vector3f view = new Vector3f(camLocal).sub(local[i]).normalize();
            Vector3f sd = new Vector3f(tangent).cross(view);
            if (sd.lengthSquared() < 1e-6f) sd.set(1f, 0f, 0f);
            sd.normalize();
            float q = i / (float) n;
            // thin where it leaves the mouth, thickest mid-body, narrowing to the neck
            width[i] = (0.016f + 0.024f * smooth(0f, 0.45f, q) - 0.008f * smooth(0.85f, 1f, q)) * grow;
            pos[i] = f.world(local[i].x, local[i].y, local[i].z);
            side[i] = new Vector3f(f.right).mul(sd.x).add(new Vector3f(f.up).mul(sd.y)).add(new Vector3f(f.fwd).mul(sd.z));
        }
        // texture runs from the head backward; sliding it this way carries the scales out of the
        // mouth toward the head, so the body reads as pouring out rather than being drawn back in
        u[n] = -a * 1.2f;
        for (int i = n - 1; i >= 0; i--) {
            float seg = local[i + 1].distance(local[i]);
            u[i] = u[i + 1] + seg / (2f * width[i]) * 128f;
        }
        Vector3f tipLocal = new Vector3f(local[n]).sub(local[n - 2]).normalize();
        Vector3f headDir = new Vector3f(f.right).mul(tipLocal.x).add(new Vector3f(f.up).mul(tipLocal.y))
                .add(new Vector3f(f.fwd).mul(tipLocal.z)).normalize();
        Vector3f headSide = new Vector3f(side[n]);
        // keep the head upright: its "up" must point the same way as the mark's up
        if (headSide.dot(f.up) < 0f) headSide.negate();
        float headSize = width[n] * f.s / 0.115f;
        return new Snake(pos, side, width, u, headDir, headSide, headSize, f.s);
    }

    private static void drawSnakeBody(VertexConsumer vc, Matrix4f m, Snake sn, float widthMul, float v0, float v1,
                                      float alpha, boolean additive) {
        int n = sn.pos.length - 1;
        for (int i = 0; i < n; i++) {
            float q0 = i / (float) n, q1 = (i + 1) / (float) n;
            float a0 = alpha * smooth(0f, 0.06f, q0), a1 = alpha * smooth(0f, 0.06f, q1);
            float uA = ((sn.u[i + 1] % 512f) + 512f) % 512f;
            float uB = uA + (sn.u[i] - sn.u[i + 1]);
            Vector3f p0 = sn.pos[i], p1 = sn.pos[i + 1];
            float w0 = sn.width[i] * widthMul, w1 = sn.width[i + 1] * widthMul;
            Vector3f s0 = new Vector3f(sn.side[i]).mul(w0 * sn.scale);
            Vector3f s1 = new Vector3f(sn.side[i + 1]).mul(w1 * sn.scale);
            float uu0 = uB / 1024f, uu1 = uA / 1024f;
            vertex(vc, m, new Vector3f(p0).sub(s0), uu0, v1, a0, additive);
            vertex(vc, m, new Vector3f(p1).sub(s1), uu1, v1, a1, additive);
            vertex(vc, m, new Vector3f(p1).add(s1), uu1, v0, a1, additive);
            vertex(vc, m, new Vector3f(p0).add(s0), uu0, v0, a0, additive);
        }
    }

    private static void drawSnakeHead(VertexConsumer vc, Matrix4f m, Snake sn, float u0, float v0, float u1, float v1,
                                      float alpha, boolean additive) {
        float h = sn.headSize;
        Vector3f tip = sn.pos[sn.pos.length - 1];
        Vector3f ax = new Vector3f(sn.headDir).mul(h * 0.5f);
        Vector3f ay = new Vector3f(sn.headSide).mul(h * 0.5f);
        // the neck occupies the image's left fifth: sink it back into the body
        Vector3f centre = new Vector3f(tip).add(new Vector3f(sn.headDir).mul(h * 0.40f));
        if (additive) {
            glowQuad(vc, m, centre, sn.headDir, sn.headSide, h * 0.5f, u0, v0, u1, v1, alpha);
        } else {
            quad(vc, m, centre, ax, ay, u0, v0, u1, v1, 1f, 1f, 1f, alpha);
        }
    }

    // =============================================================================================
    // smoke drifting through the volume
    // =============================================================================================
    private static void drawPuffs(VertexConsumer vc, Matrix4f m, Frame f, Vector3f camRight, Vector3f camUp,
                                  float a, float presence) {
        final float period = 160f;
        for (int i = 0; i < PUFFS; i++) {
            float t = a / period + hash(i, 0);
            int cycle = Mth.floor(t);
            float life = t - cycle;
            int seed = i * 7 + cycle * 131;
            float x = (hash(seed, 1) - 0.5f) * 0.75f;
            float y = (hash(seed, 2) - 0.42f) * 0.80f;
            float z = (hash(seed, 3) - 0.5f) * 0.40f;
            x *= 1f + 0.35f * life;
            y += 0.28f * life;
            float size = f.s * (0.09f + 0.16f * life) * (0.7f + 0.6f * hash(seed, 5));
            float alpha = (float) Math.pow(Mth.sin(life * Mth.PI), 1.5) * 0.30f * presence;
            if (alpha < 0.004f) continue;
            boolean lit = hash(seed, 4) > 0.5f;
            float r = lit ? 0.35f : 0.07f, g = lit ? 0.85f : 0.13f, b = lit ? 0.45f : 0.10f;
            float rot = hash(seed, 6) * Mth.TWO_PI + life * 0.6f;
            Vector3f ax = new Vector3f(camRight).mul(Mth.cos(rot)).add(new Vector3f(camUp).mul(Mth.sin(rot))).mul(size);
            Vector3f ay = new Vector3f(camUp).mul(Mth.cos(rot)).sub(new Vector3f(camRight).mul(Mth.sin(rot))).mul(size);
            quad(vc, m, f.world(x, y, z), ax, ay, 0.75f, 0.25f, 1f, 0.5f, r, g, b, alpha);
        }
    }

    // =============================================================================================
    // primitives
    // =============================================================================================
    private static void quad(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ax, Vector3f ay,
                             float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
        put(vc, m, c.x - ax.x - ay.x, c.y - ax.y - ay.y, c.z - ax.z - ay.z, u0, v1, r, g, b, a);
        put(vc, m, c.x + ax.x - ay.x, c.y + ax.y - ay.y, c.z + ax.z - ay.z, u1, v1, r, g, b, a);
        put(vc, m, c.x + ax.x + ay.x, c.y + ax.y + ay.y, c.z + ax.z + ay.z, u1, v0, r, g, b, a);
        put(vc, m, c.x - ax.x + ay.x, c.y - ax.y + ay.y, c.z - ax.z + ay.z, u0, v0, r, g, b, a);
    }

    /** Additive quad: the alpha is folded into the colour, since (ONE, ONE) blending ignores it. */
    private static void glowQuad(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f right, Vector3f up, float half,
                                 float u0, float v0, float u1, float v1, float strength) {
        if (strength <= 0.002f) return;
        float k = Math.min(strength, 1.5f);
        quad(vc, m, c, new Vector3f(right).mul(half), new Vector3f(up).mul(half), u0, v0, u1, v1, k, k, k, 1f);
    }

    /** A flat, horizontal, spinning square (the vortex and its underglow). */
    private static void horizontal(VertexConsumer vc, Matrix4f m, Vector3f c, float half, float angle,
                                   float u0, float v0, float u1, float v1, float r, float g, float b, float a,
                                   boolean additive) {
        float cs = Mth.cos(angle) * half, sn = Mth.sin(angle) * half;
        Vector3f ax = new Vector3f(cs, 0f, sn);
        Vector3f ay = new Vector3f(-sn, 0f, cs);
        if (additive) {
            float k = Math.min(a, 1.5f);
            quad(vc, m, c, ax, ay, u0, v0, u1, v1, r * k, g * k, b * k, 1f);
        } else {
            quad(vc, m, c, ax, ay, u0, v0, u1, v1, r, g, b, a);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f p, float u, float v, float a, boolean additive) {
        if (additive) {
            put(vc, m, p.x, p.y, p.z, u, v, a, a, a, 1f);
        } else {
            put(vc, m, p.x, p.y, p.z, u, v, 1f, 1f, 1f, a);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                            float r, float g, float b, float a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setLight(LIGHT).setNormal(0f, 1f, 0f);
    }

    // =============================================================================================
    // easing
    // =============================================================================================
    private static float smooth(float e0, float e1, float x) {
        float t = Mth.clamp((x - e0) / (e1 - e0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static float easeOut(float t) {
        float k = 1f - t;
        return 1f - k * k * k;
    }

    private static float easeInOut(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    private static float hash(int i, int k) {
        float h = Mth.sin(i * 12.9898f + k * 78.233f) * 43758.547f;
        return h - Mth.floor(h);
    }

    @Override
    public ResourceLocation getTextureLocation(DarkMarkEntity entity) {
        return SKULL;
    }
}
