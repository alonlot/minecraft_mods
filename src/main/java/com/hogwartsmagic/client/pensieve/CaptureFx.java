package com.hogwartsmagic.client.pensieve;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing a memory out of a head: a 30-tick silver thread from the caster's temple, along the wand tip, to the phial
 * hand. It grows in over {@link #GROW} ticks and fades over the rest, with end-rod motes along it; for the local
 * player the camera also tilts back a few degrees and eases forward again ({@link #pitchOffset}).
 *
 * <p>The ribbon is drawn at {@code AFTER_PARTICLES} from an identity matrix (stages after the camera push start from
 * identity), every point camera-relative in double precision against one anchor, and uses the additive ceiling glow
 * render type with a flat texel so the strand is soft only through its two layers (a wide dim one under a narrow
 * bright one).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CaptureFx {
    public static final int DURATION = 30;
    private static final int GROW = 18;
    private static final int SAMPLES = 28;
    private static final int MAX_ACTIVE = 12;
    /** How far the local camera tips back, in degrees, at its most (tick 10). */
    private static final float PITCH_BACK = 8f;
    private static final int PITCH_PEAK = 10;

    private static final RenderType GLOW = ModRenderTypes.ceilingGlow(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/ceiling_dot.png"));
    private static final int LIGHT = LightTexture.FULL_BRIGHT;
    private static final int OVERLAY = OverlayTexture.NO_OVERLAY;
    private static final float[] SILVER = {0.62f, 0.78f, 1.0f};
    private static final float[] WHITE = {1.0f, 1.0f, 1.0f};

    private static final class Effect {
        final int entityId;
        final boolean mainHandWand;
        int age;

        Effect(int entityId, boolean mainHandWand) {
            this.entityId = entityId;
            this.mainHandWand = mainHandWand;
        }
    }

    private static final List<Effect> ACTIVE = new ArrayList<>();

    private CaptureFx() {
    }

    /** Starts the effect on an entity; called on the client thread by the packet. */
    public static void start(int entityId, boolean mainHandWand) {
        if (ACTIVE.size() >= MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
        ACTIVE.add(new Effect(entityId, mainHandWand));
    }

    // ---- Camera ---------------------------------------------------------------------------------------------

    private static float smooth(float x) {
        float t = Mth.clamp(x, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** Degrees the local camera is tipped back at {@code age} ticks (up to 8, there by tick 10, gone by tick 30). */
    static float pitchAt(float age) {
        if (age <= 0f || age >= DURATION) {
            return 0f;
        }
        if (age < PITCH_PEAK) {
            return PITCH_BACK * smooth(age / PITCH_PEAK);
        }
        return PITCH_BACK * (1f - smooth((age - PITCH_PEAK) / (DURATION - PITCH_PEAK)));
    }

    /** Degrees to subtract from the camera pitch this frame (looking up is negative); 0 when nothing is running. */
    public static float pitchOffset(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0f;
        }
        float best = 0f;
        for (Effect e : ACTIVE) {
            if (e.entityId == mc.player.getId()) {
                best = Math.max(best, pitchAt(e.age + partial));
            }
        }
        return best;
    }

    // ---- The thread -----------------------------------------------------------------------------------------

    /** The strand's three anchors in world space: temple, wand tip, phial hand. */
    private static Vec3[] anchors(LivingEntity who, boolean mainHandWand, float partial) {
        Vec3 eye = who.getEyePosition(partial);
        float yaw = who.getViewYRot(partial) * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        HumanoidArm mainArm = who.getMainArm();
        // Which side the wand hand is on, +1 the player's right.
        float wandSide = (mainHandWand ? mainArm : mainArm.getOpposite()) == HumanoidArm.RIGHT ? 1f : -1f;
        Vec3 temple = eye.add(right.scale(0.25 * wandSide)).add(0, 0.1, 0);
        Vec3 wandTip = eye.add(right.scale(0.3 * wandSide)).add(fwd.scale(0.6)).add(0, -0.4, 0);
        Vec3 phial = eye.add(right.scale(-0.3 * wandSide)).add(fwd.scale(0.45)).add(0, -0.65, 0);
        return new Vec3[] {temple, wandTip, phial};
    }

    /** The point at {@code t} (0..1) of the quadratic curve that leaves the temple, passes the wand tip and ends at the phial. */
    private static Vec3 curve(Vec3[] a, float t, float sway) {
        Vec3 control = a[1].scale(2).subtract(a[0].add(a[2]).scale(0.5));
        float u = 1f - t;
        Vec3 p = a[0].scale(u * u).add(control.scale(2 * u * t)).add(a[2].scale(t * t));
        double s = Math.sin(t * Math.PI) * sway;
        return p.add(0, s, 0);
    }

    private static float swayAt(float age, float t) {
        return 0.04f * Mth.sin(age * 0.45f + t * 7f);
    }

    /** How far along the thread it has grown, and how bright it still is, at {@code age}. */
    private static float grown(float age) {
        return smooth(age / GROW);
    }

    private static float strength(float age) {
        return age < GROW ? 1f : Mth.clamp(1f - (age - GROW) / (DURATION - GROW), 0f, 1f);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            Effect e = ACTIVE.get(i);
            e.age++;
            if (level == null || e.age >= DURATION) {
                ACTIVE.remove(i);
                continue;
            }
            if (!(level.getEntity(e.entityId) instanceof LivingEntity who)) {
                continue;
            }
            float head = grown(e.age);
            if (head <= 0.02f || strength(e.age) <= 0.05f) {
                continue;
            }
            Vec3[] a = anchors(who, e.mainHandWand, 1f);
            for (int k = 0; k < 2; k++) {
                float t = level.random.nextFloat() * head;
                Vec3 p = curve(a, t, swayAt(e.age, t));
                level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, (level.random.nextDouble() - 0.5) * 0.01,
                        0.005 + level.random.nextDouble() * 0.01, (level.random.nextDouble() - 0.5) * 0.01);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = PensieveClientState.partial();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        for (Effect e : ACTIVE) {
            float age = e.age + partial;
            float head = grown(age);
            float fade = strength(age);
            if (head <= 0.01f || fade <= 0.01f || !(level.getEntity(e.entityId) instanceof LivingEntity who)) {
                continue;
            }
            if (vc == null) {
                vc = buffer.getBuffer(GLOW);
            }
            Vec3[] a = anchors(who, e.mainHandWand, partial);
            // One anchor in double precision; every strand point is a float offset from it.
            Vec3 anchor = a[0];
            Vector3f origin = new Vector3f((float) (anchor.x - cam.x), (float) (anchor.y - cam.y), (float) (anchor.z - cam.z));
            Vector3f[] pts = new Vector3f[SAMPLES + 1];
            for (int i = 0; i <= SAMPLES; i++) {
                float t = head * i / SAMPLES;
                Vec3 p = curve(a, t, swayAt(age, t));
                pts[i] = new Vector3f((float) (p.x - anchor.x), (float) (p.y - anchor.y), (float) (p.z - anchor.z)).add(origin);
            }
            ribbon(vc, m, pts, 0.07f, SILVER, 0.32f * fade);
            ribbon(vc, m, pts, 0.022f, WHITE, 0.9f * fade);
        }
        if (vc != null) {
            buffer.endBatch(GLOW);
        }
    }

    /** A camera-facing ribbon through {@code pts} (camera-relative), soft at both ends. */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vector3f[] pts, float halfWidth, float[] colour, float alpha) {
        int n = pts.length;
        Vector3f[] lo = new Vector3f[n];
        Vector3f[] hi = new Vector3f[n];
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            Vector3f tangent = new Vector3f(pts[Math.min(i + 1, n - 1)]).sub(pts[Math.max(i - 1, 0)]);
            Vector3f side = tangent.cross(new Vector3f(pts[i]));
            if (side.lengthSquared() < 1e-8f) {
                side.set(0f, 1f, 0f);
            }
            float t = i / (float) (n - 1);
            side.normalize().mul(halfWidth * (0.6f + 0.4f * Mth.sin(t * Mth.PI)));
            lo[i] = new Vector3f(pts[i]).sub(side);
            hi[i] = new Vector3f(pts[i]).add(side);
            // Dim at the temple end and at the growing tip, so the strand has no hard cut.
            a[i] = alpha * Mth.clamp(t * 6f, 0f, 1f) * Mth.clamp((1f - t) * 5f, 0.25f, 1f);
        }
        for (int i = 0; i + 1 < n; i++) {
            put(vc, m, lo[i], colour, a[i]);
            put(vc, m, hi[i], colour, a[i]);
            put(vc, m, hi[i + 1], colour, a[i + 1]);
            put(vc, m, lo[i + 1], colour, a[i + 1]);
        }
    }

    /** One additive vertex on the centre texel of the dot texture (flat white): colour premultiplied by alpha. */
    private static void put(VertexConsumer vc, Matrix4f m, Vector3f p, float[] c, float a) {
        float k = Mth.clamp(a, 0f, 1f);
        vc.addVertex(m, p.x, p.y, p.z).setColor(c[0] * k, c[1] * k, c[2] * k, k).setUv(0.5f, 0.5f)
                .setOverlay(OVERLAY).setLight(LIGHT).setNormal(0f, 1f, 0f);
    }
}
