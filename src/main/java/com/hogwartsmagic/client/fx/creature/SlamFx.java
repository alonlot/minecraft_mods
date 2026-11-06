package com.hogwartsmagic.client.fx.creature;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModParticles;
import com.hogwartsmagic.client.fx.FxClient;
import com.hogwartsmagic.fx.FxKinds;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A creature's blow lands on the ground: the earth throws up a ring of dust in the colour of the ground that rolls out
 * from the spot, thins and settles; chunks of that very ground are flung up and come down under gravity; and a faint
 * pale shockwave ring, drawn flat on the ground and hugging its slope, races out ahead of the dust and fades. The Troll's club,
 * the Giant's fist and kick, a dragon coming down, a Basilisk breaking out of the earth and a willow limb all use
 * it ({@code fx.CreatureFx.slam}); how big it is comes with the message (reach in blocks, strength 0.3 a footfall to
 * 1.5 a dragon landing).
 *
 * <p>Everything is capped: a few slams at a time, a bounded number of particles per tick, nothing drawn past
 * {@link #DRAW_DISTANCE}. The view shake that goes with a slam is a separate {@code Fx.shake} from the server.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SlamFx {

    /** Slams remembered at once; a newer one pushes the oldest out. */
    private static final int MAX_SLAMS = 6;
    /** Segments round a shockwave ring. */
    private static final int SEGMENTS = 40;
    /** No ring is drawn, and no dust made, for a slam further than this from the camera (blocks). */
    private static final double DRAW_DISTANCE = 140.0;
    /** The shockwave ring lives this long (ticks) at radius 0, plus {@link #RING_PER_BLOCK} per block of reach. */
    private static final float RING_TICKS = 14.0f, RING_PER_BLOCK = 0.9f;
    /** The dust ring keeps rolling for this many ticks (capped), per block of reach. */
    private static final int DUST_TICKS_MAX = 18;
    private static final float DUST_TICKS_BASE = 6.0f, DUST_PER_BLOCK = 0.9f;
    /** At most this many dust particles are made in one tick for one slam. */
    private static final int MAX_DUST_PER_TICK = 26;
    /** Chunks flung at the first instant: base count plus this much per unit of strength (capped). */
    private static final int CHUNKS_BASE = 8, CHUNKS_PER_STRENGTH = 26, CHUNKS_MAX = 48;
    /** The shockwave ring sits this high over the ground it hugs, to stay clear of z-fighting. */
    private static final float RING_LIFT = 0.07f;

    private static final List<Slam> SLAMS = new ArrayList<>();
    private static long clientTick;

    private SlamFx() {
    }

    /** One slam as the client keeps it. */
    private static final class Slam {
        final double x, y, z;
        final float radius, strength;
        final long start;
        /** The ground block at the spot (what the dust and the chunks are made of), or null on water or air. */
        final BlockState ground;
        /** Ground height at each ring segment, so the shockwave follows a slope. */
        final float[] heights = new float[SEGMENTS];
        final int dustTicks;
        final float ringTicks;

        Slam(double x, double y, double z, float radius, float strength, BlockState ground, long start) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.strength = strength;
            this.ground = ground;
            this.start = start;
            this.dustTicks = Math.min(DUST_TICKS_MAX, Math.round(DUST_TICKS_BASE + radius * DUST_PER_BLOCK));
            this.ringTicks = RING_TICKS + radius * RING_PER_BLOCK;
        }
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxClient.register(FxKinds.SLAM, e -> start(e.x(), e.y(), e.z(), e.param(0, 4.0f), e.param(1, 1.0f)));
        }
    }

    private static void start(double x, double y, double z, float radius, float strength) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z)
                > DRAW_DISTANCE * DRAW_DISTANCE) {
            return;
        }
        radius = Mth.clamp(radius, 1.0f, 24.0f);
        strength = Mth.clamp(strength, 0.1f, 2.0f);
        double groundY = y;
        BlockState ground = null;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy >= -4; dy--) {
            probe.set(Mth.floor(x), Mth.floor(y) + dy, Mth.floor(z));
            BlockState state = level.getBlockState(probe);
            if (!state.isAir() && state.getFluidState().isEmpty() && state.isSolidRender(level, probe)) {
                ground = state;
                groundY = probe.getY() + 1.0;
                break;
            }
        }
        Slam slam = new Slam(x, groundY, z, radius, strength, ground, clientTick);
        for (int i = 0; i < SEGMENTS; i++) {
            double a = Math.PI * 2.0 * i / SEGMENTS;
            slam.heights[i] = (float) surfaceAt(level, x + Math.cos(a) * radius * 0.6, groundY, z + Math.sin(a) * radius * 0.6);
        }
        while (SLAMS.size() >= MAX_SLAMS) {
            SLAMS.remove(0);
        }
        SLAMS.add(slam);
        flingChunks(level, slam);
    }

    /** The top of the ground near (x, z), within a few blocks of {@code around}; {@code around} itself when none. */
    private static double surfaceAt(ClientLevel level, double x, double around, double z) {
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy >= -3; dy--) {
            probe.set(Mth.floor(x), Mth.floor(around) + dy, Mth.floor(z));
            BlockState state = level.getBlockState(probe);
            if (!state.isAir() && state.getFluidState().isEmpty() && state.isSolidRender(level, probe)) {
                return probe.getY() + 1.0;
            }
        }
        return around;
    }

    /** The blow's first instant: chunks of the ground, thrown up and out, coming down under gravity. */
    private static void flingChunks(ClientLevel level, Slam slam) {
        if (slam.ground == null) {
            return;
        }
        RandomSource random = level.random;
        BlockParticleOption chunk = new BlockParticleOption(ParticleTypes.BLOCK, slam.ground);
        int count = Math.min(CHUNKS_MAX, CHUNKS_BASE + Math.round(CHUNKS_PER_STRENGTH * slam.strength));
        float reach = Math.min(slam.radius * 0.45f, 3.0f);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = random.nextDouble() * reach;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            // Faster and higher the nearer the middle: the blow throws what is under it hardest.
            double heat = 1.0 - dist / (reach + 0.001);
            double out = 0.08 + random.nextDouble() * 0.22 * (0.6 + slam.strength * 0.5);
            double up = 0.28 + (0.25 + 0.5 * heat) * (0.5 + 0.5 * slam.strength) + random.nextDouble() * 0.3;
            level.addParticle(chunk, slam.x + dx * dist, slam.y + 0.1, slam.z + dz * dist, dx * out, up, dz * out);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTick++;
        if (SLAMS.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            SLAMS.clear();
            return;
        }
        for (int i = SLAMS.size() - 1; i >= 0; i--) {
            Slam slam = SLAMS.get(i);
            long age = clientTick - slam.start;
            if (age >= slam.ringTicks + 2) {
                SLAMS.remove(i);
            } else if (age <= slam.dustTicks) {
                rollDust(level, slam, (int) age);
            }
        }
    }

    /**
     * The dust ring at tick {@code age} of the blow: a belt of dust, in the ground's own colour, at the radius the
     * wave has reached, thick and low, so the sum of the ticks is a ring that rolls outwards and thins as it goes.
     */
    private static void rollDust(ClientLevel level, Slam slam, int age) {
        if (slam.ground == null) {
            return;
        }
        float u = age / (float) slam.dustTicks;
        float ring = slam.radius * (1.0f - (1.0f - u) * (1.0f - u));
        // Thick at the start, thinning to a trickle: the wave runs out of dust as it widens.
        float density = (1.0f - 0.8f * u) * (0.6f + 0.5f * slam.strength);
        int count = Math.min(MAX_DUST_PER_TICK, Math.round(2.0f + ring * 1.5f * density));
        RandomSource random = level.random;
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.DUST_PILLAR, slam.ground);
        boolean earth = slam.ground.is(BlockTags.DIRT) || slam.ground.is(BlockTags.SAND)
                || slam.ground.is(Blocks.GRAVEL);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double r = Math.max(0.3, ring + (random.nextDouble() - 0.5) * 1.1);
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double px = slam.x + dx * r, pz = slam.z + dz * r;
            double py = surfaceSegment(slam, angle) + 0.1;
            // Rolling outwards: the dust is pushed the way the wave is going, and sinks as it slows.
            double push = 0.05 + 0.1 * (1.0 - u);
            if (earth && (i & 1) == 0) {
                level.addParticle(ModParticles.ROOT_DUST.get(), px, py, pz, dx * push, 0.02, dz * push);
            } else {
                level.addParticle(dust, px, py, pz, dx * push, 0.02 + random.nextDouble() * 0.04, dz * push);
            }
        }
    }

    private static double surfaceSegment(Slam slam, double angle) {
        double t = (angle / (Math.PI * 2.0)) * SEGMENTS;
        int i = Mth.floor(t) % SEGMENTS;
        if (i < 0) {
            i += SEGMENTS;
        }
        return slam.heights[i];
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SLAMS.isEmpty()) {
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
        VertexConsumer vc = null;
        for (Slam slam : SLAMS) {
            if (cam.distanceToSqr(slam.x, slam.y, slam.z) > DRAW_DISTANCE * DRAW_DISTANCE) {
                continue;
            }
            float a = (clientTick - slam.start + partial) / slam.ringTicks;
            if (a < 0.0f || a >= 1.0f) {
                continue;
            }
            if (vc == null) {
                vc = buffer.getBuffer(CreatureDraw.SOFT);
            }
            shockwave(vc, m, slam, a, cam);
        }
        if (vc != null) {
            buffer.endBatch(CreatureDraw.SOFT);
        }
    }

    /**
     * The faint shockwave at fraction {@code a} of its life: a soft pale band on the ground that runs out fast and
     * slows, with a second, narrower wake behind it; both fade as they widen. Quads across the streak texture, so
     * the band's two edges are soft.
     */
    private static void shockwave(VertexConsumer vc, Matrix4f m, Slam slam, float a, Vec3 cam) {
        float eased = 1.0f - (float) Math.pow(1.0f - a, 2.2);
        float fade = (float) Math.pow(1.0f - a, 1.6) * CreatureDraw.smoothstep(0.0f, 0.06f, a);
        float strength = Math.min(1.0f, 0.4f + slam.strength * 0.5f);
        float lead = slam.radius * 1.1f * eased;
        band(vc, m, slam, lead, 0.5f + slam.radius * 0.16f, 0.30f * fade * strength, cam);
        float wake = lead * 0.66f;
        band(vc, m, slam, wake, 0.35f + slam.radius * 0.08f, 0.16f * fade * strength, cam);
    }

    /** One band of the shockwave: centred on {@code radius}, {@code width} wide, at {@code alpha}. */
    private static void band(VertexConsumer vc, Matrix4f m, Slam slam, float radius, float width, float alpha, Vec3 cam) {
        if (alpha <= 0.004f || radius < 0.3f) {
            return;
        }
        float inner = Math.max(0.0f, radius - width);
        float outer = radius + width;
        // Pale dust, a little warmer than white.
        float r = 0.92f, g = 0.88f, b = 0.80f;
        for (int i = 0; i < SEGMENTS; i++) {
            double a0 = Math.PI * 2.0 * i / SEGMENTS, a1 = Math.PI * 2.0 * (i + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float y0 = slam.heights[i] + RING_LIFT, y1 = slam.heights[(i + 1) % SEGMENTS] + RING_LIFT;
            float cx = (float) (slam.x - cam.x), cz = (float) (slam.z - cam.z);
            float ry0 = (float) (y0 - cam.y), ry1 = (float) (y1 - cam.y);
            CreatureDraw.vertex(vc, m, cx + c0 * inner, ry0, cz + s0 * inner, r, g, b, alpha, 0.0f, 0.5f);
            CreatureDraw.vertex(vc, m, cx + c1 * inner, ry1, cz + s1 * inner, r, g, b, alpha, 0.0f, 0.5f);
            CreatureDraw.vertex(vc, m, cx + c1 * outer, ry1, cz + s1 * outer, r, g, b, alpha, 1.0f, 0.5f);
            CreatureDraw.vertex(vc, m, cx + c0 * outer, ry0, cz + s0 * outer, r, g, b, alpha, 1.0f, 0.5f);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        SLAMS.clear();
    }
}
