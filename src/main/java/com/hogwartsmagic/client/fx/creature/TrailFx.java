package com.hogwartsmagic.client.fx.creature;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.WizardEntity;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The two streaks that follow a creature through the air, both driven from state the client already has, so the
 * server sends nothing per tick:
 * <ul>
 *   <li>a Death Eater (a {@link WizardEntity}, Voldemort too) in smoke flight: its model is hidden and in its place a
 *       ribbon of churning black smoke streams behind it, thick and dark at the body, tearing into wisps further back,
 *       inside a faint dark sheath;</li>
 *   <li>a Hungarian Horntail (or Ironbelly) stooping on its target: a ribbon of fire off the body that cools from
 *       white-gold to red, and behind it a much longer, heavier ribbon of black smoke.</li>
 * </ul>
 * Each tick the client notes where the creature is; the ribbon is those points, camera-facing strips of the soft
 * streak texture with the width, fade and wobble of every point worked out from its age. Points age out on their own,
 * so a streak outlives the flight for a moment and dies out behind it. Capped in tracks and points, and the whole
 * thing does nothing for a creature that is not flying or diving.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class TrailFx {

    /** Creatures streaking at once. */
    private static final int MAX_TRACKS = 12;
    /** Points a track keeps (one per tick). */
    private static final int CAP = 48;
    /** Nothing is drawn further from the camera than this (blocks). */
    private static final double DRAW_DISTANCE = 190.0;

    // --- Death Eater smoke ---
    /** Ticks a smoke point lasts. */
    private static final float SMOKE_LIFE = 26.0f;
    /** Half-width of the core at the body (blocks). */
    private static final float SMOKE_HALF = 0.55f;
    /** The sheath is this much wider than the core, and this faint. */
    private static final float SHEATH_WIDEN = 2.4f, SHEATH_ALPHA = 0.16f;
    /** Wisps tearing off the core, and how far they drift out sideways at the tail (blocks). */
    private static final int WISPS = 4;
    private static final float WISP_DRIFT = 1.1f;

    // --- Dragon stoop ---
    private static final float DRAGON_FIRE_LIFE = 15.0f, DRAGON_SMOKE_LIFE = 44.0f;
    private static final float DRAGON_FIRE_HALF = 1.15f, DRAGON_SMOKE_HALF = 1.5f;

    private static final int KIND_SMOKE = 0, KIND_DRAGON = 1;

    private static final Map<Integer, Track> TRACKS = new HashMap<>();
    private static long clientTick;
    private static ClientLevel lastLevel;

    private static final double[] REL = new double[CAP * 3];
    private static final float[] SIDE = new float[CAP * 3];
    private static final float[] AGE = new float[CAP];

    private TrailFx() {
    }

    /** One creature's recent path, newest point first. */
    private static final class Track {
        final int kind;
        final Entity entity;
        final double[] x = new double[CAP], y = new double[CAP], z = new double[CAP];
        final long[] born = new long[CAP];
        int count;
        /** Whether the creature was streaking on the last tick (the head follows it only then). */
        boolean live;
        final float phase;

        Track(int kind, Entity entity) {
            this.kind = kind;
            this.entity = entity;
            this.phase = (entity.getId() * 1.618f) % Mth.TWO_PI;
        }

        void add(double px, double py, double pz) {
            int keep = Math.min(count, CAP - 1);
            for (int i = keep; i > 0; i--) {
                x[i] = x[i - 1];
                y[i] = y[i - 1];
                z[i] = z[i - 1];
                born[i] = born[i - 1];
            }
            x[0] = px;
            y[0] = py;
            z[0] = pz;
            born[0] = clientTick;
            count = keep + 1;
        }

        float life() {
            return kind == KIND_SMOKE ? SMOKE_LIFE : DRAGON_SMOKE_LIFE;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTick++;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != lastLevel) {
            TRACKS.clear();
            lastLevel = level;
        }
        if (level == null) {
            return;
        }
        for (Track t : TRACKS.values()) {
            t.live = false;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Entity e : level.entitiesForRendering()) {
            int kind;
            if (e instanceof WizardEntity wizard && wizard.isFlying()) {
                kind = KIND_SMOKE;
            } else if (e instanceof HungarianHorntailEntity dragon
                    && dragon.getAttackState() == HungarianHorntailEntity.AttackState.DIVE) {
                kind = KIND_DRAGON;
            } else {
                continue;
            }
            if (e.distanceToSqr(cam) > DRAW_DISTANCE * DRAW_DISTANCE) {
                continue;
            }
            Track track = TRACKS.get(e.getId());
            if (track == null) {
                if (TRACKS.size() >= MAX_TRACKS) {
                    continue;
                }
                track = new Track(kind, e);
                TRACKS.put(e.getId(), track);
                if (kind == KIND_SMOKE) {
                    burst(level, e);
                }
            }
            track.live = true;
            track.add(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
            if (kind == KIND_DRAGON) {
                dragonPuffs(level, track);
            }
        }
        for (Iterator<Track> it = TRACKS.values().iterator(); it.hasNext(); ) {
            Track t = it.next();
            if (!t.live && (t.count == 0 || clientTick - t.born[0] > t.life())) {
                it.remove();
            }
        }
    }

    /** The instant a Death Eater turns to smoke: one dark cloud blown outwards where it stood. */
    private static void burst(ClientLevel level, Entity e) {
        double cy = e.getY() + e.getBbHeight() * 0.5;
        for (int i = 0; i < 14; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0;
            double s = 0.04 + level.random.nextDouble() * 0.1;
            level.addParticle(ParticleTypes.LARGE_SMOKE, e.getX() + Math.cos(a) * 0.3, cy + (level.random.nextDouble() - 0.5),
                    e.getZ() + Math.sin(a) * 0.3, Math.cos(a) * s, 0.03, Math.sin(a) * s);
        }
    }

    /** A few lingering puffs and sparks off a stooping dragon, to give the ribbons a body to cling to. */
    private static void dragonPuffs(ClientLevel level, Track track) {
        Entity e = track.entity;
        Vec3 v = e.getDeltaMovement();
        double bx = -v.x * 0.5, by = -v.y * 0.5, bz = -v.z * 0.5;
        for (int i = 0; i < 2; i++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, e.getX() + (level.random.nextDouble() - 0.5) * 2.4,
                    e.getY() + e.getBbHeight() * 0.5 + (level.random.nextDouble() - 0.5) * 1.6,
                    e.getZ() + (level.random.nextDouble() - 0.5) * 2.4, bx * 0.2, by * 0.2 + 0.02, bz * 0.2);
        }
        level.addParticle(ParticleTypes.SMALL_FLAME, e.getX() + (level.random.nextDouble() - 0.5) * 1.6,
                e.getY() + e.getBbHeight() * 0.4 + (level.random.nextDouble() - 0.5), e.getZ() + (level.random.nextDouble() - 0.5) * 1.6,
                bx * 0.3, by * 0.3, bz * 0.3);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || TRACKS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        VertexConsumer soft = null, glow = null;
        float time = clientTick + partial;
        for (Track t : TRACKS.values()) {
            int n = gather(t, partial, cam);
            if (n < 2) {
                continue;
            }
            if (soft == null) {
                soft = buffer.getBuffer(CreatureDraw.SOFT);
            }
            if (t.kind == KIND_SMOKE) {
                smoke(soft, m, n, t.phase, time, SMOKE_LIFE, SMOKE_HALF, 1.0f, 0.02f, 0.02f, 0.03f);
            } else {
                smoke(soft, m, n, t.phase, time, DRAGON_SMOKE_LIFE, DRAGON_SMOKE_HALF, 0.8f, 0.05f, 0.045f, 0.045f);
            }
        }
        if (soft != null) {
            buffer.endBatch(CreatureDraw.SOFT);
        }
        // Fire goes over the smoke, additive, in its own batch.
        for (Track t : TRACKS.values()) {
            if (t.kind != KIND_DRAGON) {
                continue;
            }
            int n = gather(t, partial, cam);
            if (n < 2) {
                continue;
            }
            if (glow == null) {
                glow = buffer.getBuffer(CreatureDraw.GLOW);
            }
            fire(glow, m, n, t.phase, time);
        }
        if (glow != null) {
            buffer.endBatch(CreatureDraw.GLOW);
        }
    }

    /**
     * Fills {@link #REL}, {@link #SIDE} and {@link #AGE} with the track's points (camera-relative, the head first,
     * following the creature smoothly between ticks while it streaks) and returns how many.
     */
    private static int gather(Track t, float partial, Vec3 cam) {
        int n = 0;
        if (t.live && !t.entity.isRemoved()) {
            Vec3 head = t.entity.getPosition(partial);
            REL[0] = head.x - cam.x;
            REL[1] = head.y + t.entity.getBbHeight() * 0.5 - cam.y;
            REL[2] = head.z - cam.z;
            AGE[0] = 0.0f;
            n = 1;
        }
        float life = t.life();
        for (int i = 0; i < t.count && n < CAP; i++) {
            float age = clientTick - t.born[i] + partial;
            if (age > life) {
                break;
            }
            // The point the head already stands on is not repeated.
            if (n == 1 && i == 0 && t.live) {
                age = Math.max(age, 0.5f);
            }
            REL[n * 3] = t.x[i] - cam.x;
            REL[n * 3 + 1] = t.y[i] - cam.y;
            REL[n * 3 + 2] = t.z[i] - cam.z;
            AGE[n] = age;
            n++;
        }
        if (n < 2) {
            return n;
        }
        for (int k = 0; k < n; k++) {
            int a = Math.max(0, k - 1) * 3, b = Math.min(n - 1, k + 1) * 3;
            double tx = REL[b] - REL[a], ty = REL[b + 1] - REL[a + 1], tz = REL[b + 2] - REL[a + 2];
            double cx = -REL[k * 3], cy = -REL[k * 3 + 1], cz = -REL[k * 3 + 2];
            double sx = ty * cz - tz * cy, sy = tz * cx - tx * cz, sz = tx * cy - ty * cx;
            double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (len < 1e-6) {
                sx = 1.0;
                sy = 0.0;
                sz = 0.0;
                len = 1.0;
            }
            SIDE[k * 3] = (float) (sx / len);
            SIDE[k * 3 + 1] = (float) (sy / len);
            SIDE[k * 3 + 2] = (float) (sz / len);
        }
        return n;
    }

    /**
     * The smoke ribbon over the {@code n} gathered points: a faint dark sheath round it, the churning core (fat and
     * dark at the head, wobbling in width and narrowing with age) and {@link #WISPS} wisps that peel off the core as
     * it ages, drift out to the sides in lazy waves and break into pieces, so the tail frays rather than ends.
     * {@code r, g, b} tint it (near black); {@code density} scales how opaque the core is.
     */
    private static void smoke(VertexConsumer vc, Matrix4f m, int n, float phase, float time, float life, float half,
                              float density, float r, float g, float b) {
        strip(vc, m, n, phase, time, life, half * SHEATH_WIDEN, 0.0f, 0.0f, SHEATH_ALPHA * density, 0.0f, 0.35f, r, g, b);
        strip(vc, m, n, phase, time, life, half, 0.0f, 0.0f, 0.95f * density, 0.0f, 1.0f, r, g, b);
        for (int w = 0; w < WISPS; w++) {
            float wp = phase + w * (Mth.TWO_PI / WISPS) + w * 0.7f;
            strip(vc, m, n, wp, time, life, half * 0.38f, WISP_DRIFT * half / SMOKE_HALF, w + 1,
                    0.7f * density, 0.12f, 0.6f, r, g, b);
        }
    }

    /**
     * One strip. {@code drift} is how far (blocks, at the tail) the strip waves out to the side of the path;
     * {@code wisp} 0 for the core and the sheath, 1.. for a wisp (which fades in after {@code startAt} of the life
     * and breaks into pieces); {@code churn} is how much the width wobbles.
     */
    private static void strip(VertexConsumer vc, Matrix4f m, int n, float phase, float time, float life, float half,
                              float drift, float wisp, float alpha, float startAt, float churn,
                              float r, float g, float b) {
        boolean isWisp = wisp > 0.0f;
        float prevX = 0, prevY = 0, prevZ = 0, prevW = 0, prevA = 0, prevU = 0;
        boolean havePrev = false;
        float prevSx = 0, prevSy = 0, prevSz = 0;
        for (int k = 0; k < n; k++) {
            float a = CreatureDraw.clamp01(AGE[k] / life);
            // Width: full at the head, shrinking to a thread; the core churns, the wisps and sheath breathe less.
            float wobble = 1.0f + 0.22f * churn * Mth.sin(AGE[k] * 0.9f + phase * 2.0f - time * 0.35f);
            float width = half * (1.0f - 0.78f * a) * wobble;
            float fade = (float) Math.pow(1.0f - a, isWisp ? 0.9 : 0.7);
            float al = alpha * fade;
            float off = 0.0f;
            if (isWisp) {
                float gate = CreatureDraw.clamp01(0.5f + 0.9f * Mth.sin(a * 17.0f + phase * 3.0f - time * 0.2f));
                float ramp = CreatureDraw.smoothstep(startAt, startAt + 0.25f, a);
                al *= gate * ramp;
                // Waving out to the sides, more the older it is; a wisp is torn away from the core, not a copy of it.
                off = drift * a * (0.4f + 0.6f * a) * Mth.sin(a * 6.0f + phase + time * 0.12f);
                width = half * (1.0f - 0.55f * a);
            } else if (churn < 0.5f) {
                al *= 0.9f + 0.1f * Mth.sin(time * 0.3f);
            }
            float sx = SIDE[k * 3], sy = SIDE[k * 3 + 1], sz = SIDE[k * 3 + 2];
            float cx = (float) REL[k * 3] + sx * off, cy = (float) REL[k * 3 + 1] + sy * off, cz = (float) REL[k * 3 + 2] + sz * off;
            if (havePrev && (al > 0.003f || prevA > 0.003f)) {
                CreatureDraw.vertex(vc, m, prevX - prevSx * prevW, prevY - prevSy * prevW, prevZ - prevSz * prevW, r, g, b, prevA, 0.0f, prevU);
                CreatureDraw.vertex(vc, m, prevX + prevSx * prevW, prevY + prevSy * prevW, prevZ + prevSz * prevW, r, g, b, prevA, 1.0f, prevU);
                CreatureDraw.vertex(vc, m, cx + sx * width, cy + sy * width, cz + sz * width, r, g, b, al, 1.0f, a);
                CreatureDraw.vertex(vc, m, cx - sx * width, cy - sy * width, cz - sz * width, r, g, b, al, 0.0f, a);
            }
            prevX = cx;
            prevY = cy;
            prevZ = cz;
            prevW = width;
            prevA = al;
            prevU = a;
            prevSx = sx;
            prevSy = sy;
            prevSz = sz;
            havePrev = true;
        }
    }

    /**
     * The fire off a stooping dragon: three nested additive strips (a wide red flicker, an orange body, a thin
     * white-gold core), each cooling and thinning over {@link #DRAGON_FIRE_LIFE} ticks. Colour is faded by scaling
     * it down, since additive blending ignores alpha.
     */
    private static void fire(VertexConsumer vc, Matrix4f m, int n, float phase, float time) {
        flame(vc, m, n, phase, time, DRAGON_FIRE_HALF * 1.7f, 0.85f, 0.18f, 0.04f, 0.55f);
        flame(vc, m, n, phase + 1.3f, time, DRAGON_FIRE_HALF, 1.0f, 0.5f, 0.1f, 0.9f);
        flame(vc, m, n, phase + 2.6f, time, DRAGON_FIRE_HALF * 0.45f, 1.0f, 0.9f, 0.6f, 1.0f);
    }

    private static void flame(VertexConsumer vc, Matrix4f m, int n, float phase, float time, float half,
                              float r, float g, float b, float gain) {
        float prevX = 0, prevY = 0, prevZ = 0, prevW = 0, prevK = 0, prevU = 0;
        float prevSx = 0, prevSy = 0, prevSz = 0;
        boolean havePrev = false;
        for (int k = 0; k < n; k++) {
            float a = AGE[k] / DRAGON_FIRE_LIFE;
            if (a >= 1.0f) {
                break;
            }
            float flicker = 0.8f + 0.35f * Mth.sin(AGE[k] * 1.7f + phase * 3.0f - time * 0.9f);
            float width = half * (1.0f - 0.7f * a) * flicker;
            float level = gain * (float) Math.pow(1.0f - a, 1.3) * (0.85f + 0.15f * flicker);
            float sx = SIDE[k * 3], sy = SIDE[k * 3 + 1], sz = SIDE[k * 3 + 2];
            // The flame streams a little to the side as it ages, like a flag in the wind of the dive.
            float off = half * 0.6f * a * Mth.sin(a * 5.0f + phase);
            float cx = (float) REL[k * 3] + sx * off, cy = (float) REL[k * 3 + 1] + sy * off, cz = (float) REL[k * 3 + 2] + sz * off;
            if (havePrev) {
                CreatureDraw.vertex(vc, m, prevX - prevSx * prevW, prevY - prevSy * prevW, prevZ - prevSz * prevW,
                        r * prevK, g * prevK, b * prevK, 1.0f, 0.0f, prevU);
                CreatureDraw.vertex(vc, m, prevX + prevSx * prevW, prevY + prevSy * prevW, prevZ + prevSz * prevW,
                        r * prevK, g * prevK, b * prevK, 1.0f, 1.0f, prevU);
                CreatureDraw.vertex(vc, m, cx + sx * width, cy + sy * width, cz + sz * width,
                        r * level, g * level, b * level, 1.0f, 1.0f, a);
                CreatureDraw.vertex(vc, m, cx - sx * width, cy - sy * width, cz - sz * width,
                        r * level, g * level, b * level, 1.0f, 0.0f, a);
            }
            prevX = cx;
            prevY = cy;
            prevZ = cz;
            prevW = width;
            prevK = level;
            prevU = a;
            prevSx = sx;
            prevSy = sy;
            prevSz = sz;
            havePrev = true;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TRACKS.clear();
    }
}
