package com.hogwartsmagic.client.fx;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.client.SpellFx;
import com.hogwartsmagic.client.SpellLight;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import com.hogwartsmagic.util.MagicParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How the Horcrux rite looks. The vessel's own heart, halo and rite circle are drawn by its block entity renderer;
 * this draws the moments the server announces ({@code fx.FxKinds.HORCRUX_*}) and the light the vessels throw.
 *
 * <ul>
 *   <li><b>Rite</b>: a crimson shock rolls out over the floor, the heart flares, sparks spit up.</li>
 *   <li><b>Feed</b> (each pulse): a writhing crimson tether is torn out of the owner's chest into the heart, beads of
 *       soul running down it; the owner's view throbs red at the edges and jolts.</li>
 *   <li><b>Bound</b>: two seconds of implosion (rings and light drawn inward, the world greying for the owner) timed to
 *       the bind sound's swell, then the slam at {@link #BOUND_SLAM}: an impact freeze and flash for the owner, a
 *       pillar of green light, double shockwave, shake.</li>
 *   <li><b>Abort</b>: the tether snaps back into the stone and smokes.</li>
 *   <li><b>Shatter</b>: the heart sucks in, bursts into green light and black stone, and the trapped soul streaks up
 *       out of it as a shrieking wisp.</li>
 *   <li><b>Rebirth</b>: black smoke spirals into the reborn owner; their view comes back out of black.</li>
 * </ul>
 * All light is drawn with {@link ModRenderTypes#BLOOM_GLOW} / {@link ModRenderTypes#BLOOM_RING}, which the bloom pass makes glow. Client only; cosmetic.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class HorcruxFx {

    /** Ticks into the bind at which its sound slams home (horcrux/bind.ogg peaks at ~2.1 s). */
    public static final int BOUND_SLAM = 42;

    private static final RenderType GLOW = ModRenderTypes.BLOOM_GLOW;
    private static final RenderType RING = ModRenderTypes.BLOOM_RING;
    private static final int MAX_EFFECTS = 32;
    private static final double DRAW_DISTANCE = 96.0;
    private static final int GREEN = HorcruxBlockEntity.SOUL_GREEN;
    private static final int CRIMSON = HorcruxBlockEntity.RITE_CRIMSON;
    private static final int PALE = 0xD8FFE0;

    // Effect types as ints: switch-on-enum has failed to load in this mod's render code before.
    private static final int RITE = 0, FEED = 1, BOUND = 2, ABORT = 3, SHATTER = 4, REBIRTH = 5;

    private static final class Effect {
        final int type;
        final Vec3 pos;
        final int entityId;
        final float a;
        final float b;
        final long start;
        final int life;
        /** Where the owner was when it began, if their entity is not (or no longer) on this client. */
        final Vec3 fallback;
        boolean climaxed;

        Effect(int type, Vec3 pos, int entityId, float a, float b, long start, int life, Vec3 fallback) {
            this.type = type;
            this.pos = pos;
            this.entityId = entityId;
            this.a = a;
            this.b = b;
            this.start = start;
            this.life = life;
            this.fallback = fallback;
        }
    }

    /** A vessel the renderer drew lately: where its heart is, its colour and its beat. */
    private record Seen(Vec3 heart, int rgb, float beat, boolean rite, long tick) {
    }

    private static final List<Effect> EFFECTS = new ArrayList<>();
    private static final Map<Long, Seen> SEEN = new HashMap<>();
    private static long clientTick;
    /** The owner's own view: the red throb at the edges while the rite feeds on them. */
    private static int edgeLeft;

    private HorcruxFx() {
    }

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxClient.register(FxKinds.HORCRUX_RITE, e -> start(RITE, e, 34));
            FxClient.register(FxKinds.HORCRUX_FEED, e -> start(FEED, e, 22));
            FxClient.register(FxKinds.HORCRUX_BOUND, e -> start(BOUND, e, BOUND_SLAM + 60));
            FxClient.register(FxKinds.HORCRUX_ABORT, e -> start(ABORT, e, 24));
            FxClient.register(FxKinds.HORCRUX_SHATTER, e -> start(SHATTER, e, 56));
            FxClient.register(FxKinds.HORCRUX_REBIRTH, e -> start(REBIRTH, e, 60));
            FxOverlays.register(FxOverlays.ORDER_WORLD_TINT + 6, "horcrux_edges", HorcruxFx::renderOverlay);
        }
    }

    /** The renderer reports each vessel it draws, so the light can follow the beat. */
    public static void seen(BlockPos pos, Vec3 heart, int rgb, float beat, boolean rite) {
        SEEN.put(pos.asLong(), new Seen(heart, rgb, beat, rite, clientTick));
    }

    private static void start(int type, FxClient.FxEvent e, int life) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 pos = e.pos();
        if (mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(pos) > DRAW_DISTANCE * DRAW_DISTANCE) {
            return;
        }
        Entity who = e.entity();
        Vec3 fallback = who != null ? chest(who, 1.0f) : pos.add(0.0, 0.5, 2.0);
        while (EFFECTS.size() >= MAX_EFFECTS) {
            EFFECTS.remove(0);
        }
        EFFECTS.add(new Effect(type, pos, e.entityId(), e.param(0, 0), e.param(1, 1), clientTick, life, fallback));
        onStart(type, e, pos, isLocal(e.entityId()));
    }

    /** What happens the instant an effect arrives: bursts, flashes, the owner's own view. */
    private static void onStart(int type, FxClient.FxEvent e, Vec3 pos, boolean local) {
        ClientLevel level = Minecraft.getInstance().level;
        RandomSource rnd = level.random;
        double floorY = pos.y - 1.05;
        switch (type) {
            case RITE -> {
                for (int i = 0; i < 34; i++) {
                    double yaw = rnd.nextDouble() * Math.PI * 2.0;
                    double s = 0.05 + rnd.nextDouble() * 0.12;
                    level.addParticle(new SpellVfxOptions(i % 3 == 0 ? SpellVfxOptions.Kind.GLOW : SpellVfxOptions.Kind.SPARK,
                                    i % 4 == 0 ? 0xFFB0B8 : CRIMSON, 0.08f, 16 + rnd.nextInt(12)).alwaysShown(),
                            pos.x, pos.y, pos.z, Math.cos(yaw) * s * 0.6, 0.12 + rnd.nextDouble() * 0.14, Math.sin(yaw) * s * 0.6);
                }
                for (int i = 0; i < 22; i++) {
                    double yaw = i * Math.PI * 2.0 / 22.0;
                    level.addParticle(MagicParticles.SMOKE_DARK, pos.x + Math.cos(yaw) * 0.6, floorY + 0.1,
                            pos.z + Math.sin(yaw) * 0.6, Math.cos(yaw) * 0.14, 0.01, Math.sin(yaw) * 0.14);
                }
                if (local) {
                    Cinematic.flash(1.0f, 0.1f, 0.16f, 0.4f, 12);
                    Cinematic.drain(0.35f, 34);
                }
            }
            case FEED -> {
                if (local) {
                    edgeLeft = 16;
                    CameraShake.addLocal(0.35f + 0.05f * e.param(0, 1), 8, 1.3f);
                    Cinematic.flash(0.55f, 0.0f, 0.05f, 0.14f, 8);
                }
            }
            case BOUND -> {
                if (local) {
                    Cinematic.letterbox(BOUND_SLAM + 34);
                    Cinematic.drain(0.55f, BOUND_SLAM + 10);
                }
            }
            case ABORT -> {
                for (int i = 0; i < 18; i++) {
                    level.addParticle(MagicParticles.SMOKE_DARK, pos.x + (rnd.nextDouble() - 0.5) * 0.4, pos.y,
                            pos.z + (rnd.nextDouble() - 0.5) * 0.4, (rnd.nextDouble() - 0.5) * 0.1, 0.04, (rnd.nextDouble() - 0.5) * 0.1);
                }
                for (int i = 0; i < 12; i++) {
                    level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, 0x9A8A90, 0.06f, 12), pos.x, pos.y, pos.z,
                            (rnd.nextDouble() - 0.5) * 0.25, rnd.nextDouble() * 0.15, (rnd.nextDouble() - 0.5) * 0.25);
                }
                if (local) {
                    CameraShake.addLocal(0.5f, 10, 1.0f);
                    Cinematic.flash(0.45f, 0.45f, 0.5f, 0.25f, 10);
                }
            }
            case SHATTER -> {
                // The bang itself waits for the suck-in (tick()): only the first crack is instant.
                for (int i = 0; i < 10; i++) {
                    level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, PALE, 0.05f, 8), pos.x, pos.y, pos.z,
                            (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3, (rnd.nextDouble() - 0.5) * 0.3);
                }
            }
            case REBIRTH -> {
                if (local) {
                    Cinematic.flash(0.0f, 0.0f, 0.0f, 1.0f, 36);
                    Cinematic.drain(0.9f, 90);
                    Cinematic.letterbox(50);
                    CameraShake.addLocal(0.7f, 26, 0.8f);
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clientTick++;
        if (edgeLeft > 0) {
            edgeLeft--;
        }
        SEEN.values().removeIf(s -> clientTick - s.tick > 3);
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        EFFECTS.removeIf(e -> clientTick - e.start > e.life);
        RandomSource rnd = level.random;
        for (Effect e : EFFECTS) {
            long age = clientTick - e.start;
            switch (e.type) {
                case FEED -> {
                    if (age < 12) {
                        // Smoke torn off the owner and dragged towards the vessel.
                        Vec3 from = owner(e, 1.0f);
                        Vec3 dir = e.pos.subtract(from);
                        Vec3 v = dir.scale(0.07);
                        level.addParticle(MagicParticles.SMOKE_DARK, from.x + (rnd.nextDouble() - 0.5) * 0.3,
                                from.y + (rnd.nextDouble() - 0.5) * 0.4, from.z + (rnd.nextDouble() - 0.5) * 0.3, v.x, v.y, v.z);
                        level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, CRIMSON, 0.05f, 14), from.x, from.y,
                                from.z, v.x * 1.2, v.y * 1.2, v.z * 1.2);
                    }
                    if (age == 11) {
                        for (int i = 0; i < 10; i++) {
                            level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, CRIMSON, 0.1f, 10), e.pos.x, e.pos.y,
                                    e.pos.z, (rnd.nextDouble() - 0.5) * 0.12, (rnd.nextDouble() - 0.5) * 0.12, (rnd.nextDouble() - 0.5) * 0.12);
                        }
                    }
                }
                case BOUND -> tickBound(level, rnd, e, age);
                case SHATTER -> tickShatter(level, rnd, e, age);
                case REBIRTH -> {
                    if (age < 34) {
                        // Black smoke and soul motes spiralling in onto the body as it reforms.
                        Vec3 at = owner(e, 1.0f).subtract(0.0, 0.6, 0.0);
                        for (int i = 0; i < 6; i++) {
                            double ang = rnd.nextDouble() * Math.PI * 2.0;
                            double r = 2.2 + rnd.nextDouble() * 0.8;
                            double y = rnd.nextDouble() * 2.0;
                            double tx = -Math.sin(ang), tz = Math.cos(ang);
                            double ix = -Math.cos(ang), iz = -Math.sin(ang);
                            level.addParticle(i < 4 ? MagicParticles.SMOKE_DARK
                                            : new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, GREEN, 0.05f, 14),
                                    at.x + Math.cos(ang) * r, at.y + y, at.z + Math.sin(ang) * r,
                                    (tx * 0.12 + ix * 0.14), (1.0 - y) * 0.02, (tz * 0.12 + iz * 0.14));
                        }
                    }
                    if (age == 30) {
                        Vec3 at = owner(e, 1.0f);
                        burst(level, rnd, at, GREEN, 26, 0.18);
                        for (int i = 0; i < 18; i++) {
                            double yaw = i * Math.PI * 2.0 / 18.0;
                            level.addParticle(MagicParticles.SMOKE_DARK, at.x, at.y - 0.6, at.z, Math.cos(yaw) * 0.2, 0.02,
                                    Math.sin(yaw) * 0.2);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void tickBound(ClientLevel level, RandomSource rnd, Effect e, long age) {
        if (age < BOUND_SLAM) {
            // Light and soul drawn in from all round, faster as the slam nears.
            int n = 2 + (int) (age / 10);
            for (int i = 0; i < n; i++) {
                double yaw = rnd.nextDouble() * Math.PI * 2.0;
                double pitch = (rnd.nextDouble() - 0.35) * Math.PI * 0.8;
                double r = 3.0 + rnd.nextDouble() * 1.5;
                Vec3 from = e.pos.add(Math.cos(yaw) * Math.cos(pitch) * r, Math.sin(pitch) * r, Math.sin(yaw) * Math.cos(pitch) * r);
                Vec3 v = e.pos.subtract(from).scale(1.0 / 14.0);
                level.addParticle(new SpellVfxOptions(i % 3 == 0 ? SpellVfxOptions.Kind.GLOW : SpellVfxOptions.Kind.MOTE,
                        i % 4 == 0 ? PALE : GREEN, 0.07f, 14), from.x, from.y, from.z, v.x, v.y, v.z);
            }
            if (age % 3 == 0) {
                double yaw = rnd.nextDouble() * Math.PI * 2.0;
                Vec3 from = e.pos.add(Math.cos(yaw) * 2.5, -1.0, Math.sin(yaw) * 2.5);
                Vec3 v = e.pos.subtract(from).scale(1.0 / 16.0);
                level.addParticle(MagicParticles.SMOKE_DARK, from.x, from.y, from.z, v.x, v.y, v.z);
            }
            return;
        }
        if (e.climaxed) {
            if (age < BOUND_SLAM + 30 && age % 2 == 0) {
                // Embers falling back down out of the pillar.
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, GREEN, 0.06f, 30),
                        e.pos.x + (rnd.nextDouble() - 0.5) * 1.2, e.pos.y + 1.0 + rnd.nextDouble() * 5.0,
                        e.pos.z + (rnd.nextDouble() - 0.5) * 1.2, 0.0, -0.03, 0.0);
            }
            return;
        }
        e.climaxed = true;
        Minecraft mc = Minecraft.getInstance();
        boolean local = isLocal(e.entityId);
        double dist = mc.gameRenderer.getMainCamera().getPosition().distanceTo(e.pos);
        if (local) {
            Cinematic.freeze(3);
            Cinematic.flash(0.75f, 1.0f, 0.8f, 0.85f, 16);
            Cinematic.drain(0.4f, 30);
        } else if (dist < 20.0) {
            Cinematic.flash(0.75f, 1.0f, 0.8f, 0.35f * (float) (1.0 - dist / 20.0), 12);
        }
        CameraShake.add(e.pos, 1.6f, 16.0f, 26.0f, 1.0f);
        burst(level, rnd, e.pos, GREEN, 60, 0.32);
        burst(level, rnd, e.pos, PALE, 30, 0.45);
        double floorY = e.pos.y - 1.05;
        for (int i = 0; i < 30; i++) {
            double yaw = i * Math.PI * 2.0 / 30.0;
            level.addParticle(MagicParticles.SMOKE_DARK, e.pos.x + Math.cos(yaw) * 0.8, floorY + 0.15, e.pos.z + Math.sin(yaw) * 0.8,
                    Math.cos(yaw) * 0.3, 0.01, Math.sin(yaw) * 0.3);
        }
    }

    private static void tickShatter(ClientLevel level, RandomSource rnd, Effect e, long age) {
        if (age == 6 && !e.climaxed) {
            e.climaxed = true;
            boolean violent = e.a > 0.5f;
            burst(level, rnd, e.pos, GREEN, violent ? 50 : 40, 0.3);
            burst(level, rnd, e.pos, PALE, 24, 0.4);
            int stone = Block.getId(Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            for (int i = 0; i < 16; i++) {
                level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.CHUNK, stone, 0.1f, 30 + rnd.nextInt(20)).alwaysShown(),
                        e.pos.x, e.pos.y - 0.3, e.pos.z, (rnd.nextDouble() - 0.5) * 0.45, 0.15 + rnd.nextDouble() * 0.25,
                        (rnd.nextDouble() - 0.5) * 0.45);
            }
            for (int i = 0; i < 24; i++) {
                level.addParticle(MagicParticles.SMOKE_DARK, e.pos.x, e.pos.y - 0.2, e.pos.z,
                        (rnd.nextDouble() - 0.5) * 0.3, 0.02 + rnd.nextDouble() * 0.08, (rnd.nextDouble() - 0.5) * 0.3);
            }
            Minecraft mc = Minecraft.getInstance();
            double dist = mc.gameRenderer.getMainCamera().getPosition().distanceTo(e.pos);
            if (dist < 16.0) {
                Cinematic.flash(0.5f, 1.0f, 0.6f, 0.35f * (float) (1.0 - dist / 16.0), 10);
            }
        }
        if (age > 6 && age < 40) {
            // The trapped soul's trail as it streaks up and away.
            Vec3 w = wraith(e, age);
            level.addParticle(new SpellVfxOptions(SpellVfxOptions.Kind.GLOW, GREEN, 0.09f, 14), w.x, w.y, w.z, 0.0, -0.01, 0.0);
            level.addParticle(MagicParticles.SMOKE_DARK, w.x, w.y - 0.2, w.z, 0.0, 0.0, 0.0);
        }
    }

    /** Where the shrieking soul is {@code age} ticks into a shatter: it spirals up out of the vessel. */
    private static Vec3 wraith(Effect e, float age) {
        float t = Mth.clamp((age - 6.0f) / 34.0f, 0.0f, 1.0f);
        float rise = 5.0f * (1.0f - (1.0f - t) * (1.0f - t));
        double ang = t * Math.PI * 3.0;
        double r = 0.5 * Math.sin(t * Math.PI);
        return e.pos.add(Math.cos(ang) * r, rise, Math.sin(ang) * r);
    }

    private static void burst(ClientLevel level, RandomSource rnd, Vec3 at, int rgb, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double yaw = rnd.nextDouble() * Math.PI * 2.0;
            double pitch = (rnd.nextDouble() - 0.3) * Math.PI;
            double s = speed * (0.4 + rnd.nextDouble() * 0.6);
            level.addParticle(new SpellVfxOptions(i % 3 == 0 ? SpellVfxOptions.Kind.GLOW : SpellVfxOptions.Kind.SPARK, rgb,
                            0.09f, 16 + rnd.nextInt(14)).alwaysShown(),
                    at.x, at.y, at.z, Math.cos(yaw) * Math.cos(pitch) * s, Math.sin(pitch) * s, Math.sin(yaw) * Math.cos(pitch) * s);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EFFECTS.clear();
        SEEN.clear();
        edgeLeft = 0;
    }

    // ------------------------------------------------------------------------------------------------ light

    /** Coloured light on the room ({@link SpellLight} calls this every frame). */
    public static void lights(float partial, double now, SpellLight.Sink sink) {
        for (Seen s : SEEN.values()) {
            sink.add(s.heart, red(s.rgb), green(s.rgb), blue(s.rgb), s.rite ? 5.5f : 4.0f,
                    (s.rite ? 0.35f : 0.18f) + (s.rite ? 0.45f : 0.3f) * s.beat);
        }
        for (Effect e : EFFECTS) {
            float age = (float) (clientTick - e.start) + partial;
            switch (e.type) {
                case RITE -> sink.add(e.pos, 1.0f, 0.15f, 0.2f, 7.5f, 1.0f - age / e.life);
                case FEED -> sink.add(owner(e, partial), 1.0f, 0.15f, 0.2f, 3.0f, 0.5f * (1.0f - age / e.life));
                case BOUND -> {
                    if (age < BOUND_SLAM) {
                        sink.add(e.pos, 0.3f, 1.0f, 0.48f, 4.0f + 3.0f * age / BOUND_SLAM, 0.2f + 0.6f * age / BOUND_SLAM);
                    } else {
                        sink.add(e.pos, 0.6f, 1.0f, 0.7f, 8.0f, 1.0f - (age - BOUND_SLAM) / (e.life - BOUND_SLAM));
                    }
                }
                case SHATTER -> {
                    if (age >= 6.0f) {
                        sink.add(e.pos, 0.3f, 1.0f, 0.48f, 7.0f, 1.0f - (age - 6.0f) / 30.0f);
                        sink.add(wraith(e, age), 0.5f, 1.0f, 0.6f, 3.0f, 0.6f * (1.0f - age / e.life));
                    }
                }
                case REBIRTH -> sink.add(owner(e, partial), 0.3f, 1.0f, 0.48f, 4.5f,
                        age < 30 ? 0.15f + age / 60.0f : 0.9f * (1.0f - (age - 30.0f) / 30.0f));
                default -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || EFFECTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        // After the camera push: identity, everything relative to the camera (see SpellLight).
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        VertexConsumer glow = buffer.getBuffer(GLOW);
        for (Effect e : EFFECTS) {
            drawGlow(glow, m, e, (float) (clientTick - e.start) + partial, partial, cam, left, up);
        }
        buffer.endBatch(GLOW);

        VertexConsumer ring = buffer.getBuffer(RING);
        for (Effect e : EFFECTS) {
            drawRings(ring, m, e, (float) (clientTick - e.start) + partial, cam);
        }
        buffer.endBatch(RING);
    }

    private static void drawGlow(VertexConsumer vc, Matrix4f m, Effect e, float age, float partial, Vec3 cam,
                                 Vector3f left, Vector3f up) {
        switch (e.type) {
            case RITE -> {
                float t = age / 12.0f;
                if (t < 1.0f) {
                    billboard(vc, m, e.pos, cam, left, up, 0.6f + 3.2f * ease(t), 1.0f, 0.15f, 0.22f, 1.0f - t);
                }
            }
            case FEED -> {
                Vec3 from = owner(e, partial);
                float alpha = fadeInOut(age, 3.0f, e.life, 8.0f);
                float strength = 0.6f + 0.4f * e.a / Math.max(1.0f, e.b);
                tether(vc, m, from, e.pos, 1.0f, cam, 0.55f, 0.8f, 0.05f, 0.1f, alpha * strength * 0.6f, age, 0.35f);
                tether(vc, m, from, e.pos, 1.0f, cam, 0.15f, 1.0f, 0.55f, 0.6f, alpha * strength, age, 0.35f);
                // Beads of soul running down the tether into the stone.
                for (int k = 0; k < 4; k++) {
                    float t = (age * 0.075f + k * 0.25f) % 1.0f;
                    Vec3 at = along(from, e.pos, t, age, 0.35f);
                    billboard(vc, m, at, cam, left, up, 0.32f, 1.0f, 0.35f, 0.4f, alpha * Mth.sin(t * Mth.PI));
                }
            }
            case BOUND -> {
                if (age < BOUND_SLAM) {
                    float t = age / BOUND_SLAM;
                    billboard(vc, m, e.pos, cam, left, up, 0.6f + 1.4f * t * t, 0.3f, 1.0f, 0.5f, 0.3f + 0.7f * t);
                } else {
                    float t = (age - BOUND_SLAM) / 14.0f;
                    if (t < 1.0f) {
                        billboard(vc, m, e.pos, cam, left, up, 1.2f + 5.0f * ease(t), 0.7f, 1.0f, 0.78f, 1.0f - t);
                    }
                    float p = (age - BOUND_SLAM) / 28.0f;
                    if (p < 1.0f) {
                        // The pillar: a column of light from the floor to the sky, thinning away.
                        float width = 1.3f * (1.0f - p) * (1.0f - p);
                        column(vc, m, e.pos.add(0.0, -1.0, 0.0), 10.0f * Math.min(1.0f, p * 6.0f + 0.3f), width, cam,
                                0.4f, 1.0f, 0.55f, 1.0f - p);
                        column(vc, m, e.pos.add(0.0, -1.0, 0.0), 10.0f * Math.min(1.0f, p * 6.0f + 0.3f), width * 0.35f, cam,
                                0.85f, 1.0f, 0.9f, 1.0f - p);
                    }
                }
            }
            case ABORT -> {
                // The tether snapping back into the stone, flickering out.
                float t = age / 9.0f;
                if (t < 1.0f) {
                    Vec3 from = owner(e, partial);
                    float flick = 0.6f + 0.4f * Mth.sin(age * 2.7f);
                    tether(vc, m, from, e.pos, 1.0f - t, cam, 0.2f, 0.55f, 0.45f, 0.5f, (1.0f - t) * flick, age, 0.6f);
                }
                float f = age / 8.0f;
                if (f < 1.0f) {
                    billboard(vc, m, e.pos, cam, left, up, 0.8f + 1.4f * f, 0.6f, 0.5f, 0.55f, 1.0f - f);
                }
            }
            case SHATTER -> {
                if (age < 6.0f) {
                    // Sucked in before it bursts.
                    float t = age / 6.0f;
                    billboard(vc, m, e.pos, cam, left, up, 1.6f - 1.3f * t, 0.4f, 1.0f, 0.55f, 0.5f + 0.5f * t);
                } else {
                    float t = (age - 6.0f) / 12.0f;
                    if (t < 1.0f) {
                        billboard(vc, m, e.pos, cam, left, up, 0.8f + 4.5f * ease(t), 0.55f, 1.0f, 0.65f, 1.0f - t);
                    }
                    if (age < 40.0f) {
                        Vec3 w = wraith(e, age);
                        float fade = 1.0f - Mth.clamp((age - 28.0f) / 12.0f, 0.0f, 1.0f);
                        float wob = 1.0f + 0.15f * Mth.sin(age * 1.9f);
                        billboard(vc, m, w, cam, left, up, 1.1f * wob, 0.35f, 1.0f, 0.5f, 0.8f * fade);
                        billboard(vc, m, w, cam, left, up, 0.4f, 0.85f, 1.0f, 0.9f, fade);
                    }
                }
            }
            case REBIRTH -> {
                Vec3 at = owner(e, partial);
                if (age < 30.0f) {
                    float t = age / 30.0f;
                    billboard(vc, m, at, cam, left, up, 2.6f - 1.8f * t, 0.25f, 0.8f, 0.4f, 0.15f + 0.4f * t);
                } else {
                    float t = (age - 30.0f) / 12.0f;
                    if (t < 1.0f) {
                        billboard(vc, m, at, cam, left, up, 1.0f + 3.0f * ease(t), 0.5f, 1.0f, 0.6f, 1.0f - t);
                    }
                }
            }
            default -> {
            }
        }
    }

    private static void drawRings(VertexConsumer vc, Matrix4f m, Effect e, float age, Vec3 cam) {
        Vec3 floor = e.pos.subtract(0.0, 1.05, 0.0);
        switch (e.type) {
            case RITE -> {
                float t = age / 16.0f;
                if (t < 1.0f) {
                    flat(vc, m, floor, cam, 0.5f + 5.0f * ease(t), 1.0f, 0.12f, 0.18f, 1.0f - t, 0.04f);
                }
            }
            case BOUND -> {
                if (age < BOUND_SLAM) {
                    // Three rings closing in on the pedestal, one after another.
                    for (int k = 0; k < 3; k++) {
                        float t = ((age + k * 14.0f) % BOUND_SLAM) / BOUND_SLAM;
                        float a = Mth.sin(t * Mth.PI) * (0.4f + 0.6f * age / BOUND_SLAM);
                        flat(vc, m, floor, cam, 4.5f * (1.0f - t) + 0.3f, 0.3f, 1.0f, 0.5f, a, 0.04f + k * 0.002f);
                    }
                } else {
                    for (int k = 0; k < 2; k++) {
                        float t = (age - BOUND_SLAM - k * 5.0f) / 20.0f;
                        if (t > 0.0f && t < 1.0f) {
                            flat(vc, m, floor, cam, 0.6f + 9.0f * ease(t), 0.55f, 1.0f, 0.7f, 1.0f - t, 0.045f + k * 0.002f);
                        }
                    }
                }
            }
            case ABORT -> {
                float t = age / 10.0f;
                if (t < 1.0f) {
                    flat(vc, m, floor, cam, 0.4f + 2.2f * ease(t), 0.6f, 0.5f, 0.55f, 0.8f * (1.0f - t), 0.04f);
                }
            }
            case SHATTER -> {
                float t = (age - 6.0f) / 16.0f;
                if (t > 0.0f && t < 1.0f) {
                    flat(vc, m, floor, cam, 0.5f + 6.5f * ease(t), 0.4f, 1.0f, 0.55f, 0.65f * (1.0f - t), 0.04f);
                }
            }
            case REBIRTH -> {
                Vec3 feet = owner(e, 1.0f).subtract(0.0, 1.0, 0.0);
                if (age < 30.0f) {
                    float t = age / 30.0f;
                    flat(vc, m, feet, cam, 3.0f * (1.0f - t) + 0.3f, 0.3f, 0.9f, 0.45f, 0.3f + 0.5f * t, 0.04f);
                } else {
                    float t = (age - 30.0f) / 14.0f;
                    if (t < 1.0f) {
                        flat(vc, m, feet, cam, 0.5f + 4.0f * ease(t), 0.5f, 1.0f, 0.6f, 1.0f - t, 0.04f);
                    }
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ the view

    /** The owner's own view throbs red at the edges as each pulse of the rite tears at them. */
    private static void renderOverlay(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (edgeLeft <= 0 || mc.options.hideGui || mc.player == null) {
            return;
        }
        float s = Math.max(0.0f, (edgeLeft - delta.getGameTimeDeltaPartialTick(false)) / 16.0f);
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int depth = Math.max(8, (int) (Math.min(w, h) * 0.3f));
        for (int i = 0; i < depth; i++) {
            float t = 1.0f - i / (float) depth;
            int alpha = (int) (190 * s * t * t * t);
            if (alpha < 2) {
                break;
            }
            int colour = (alpha << 24) | (t > 0.7f ? 0x3A0008 : 0x8A0A1A);
            graphics.fill(i, i, w - i, i + 1, colour);
            graphics.fill(i, h - i - 1, w - i, h - i, colour);
            graphics.fill(i, i + 1, i + 1, h - i - 1, colour);
            graphics.fill(w - i - 1, i + 1, w - i, h - i - 1, colour);
        }
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static boolean isLocal(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entityId >= 0 && mc.player.getId() == entityId;
    }

    private static Vec3 chest(Entity who, float partial) {
        return who.getPosition(partial).add(0.0, who.getBbHeight() * 0.62, 0.0);
    }

    /** The effect's owner's chest now, or where they were when it began. */
    private static Vec3 owner(Effect e, float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity who = level == null || e.entityId < 0 ? null : level.getEntity(e.entityId);
        return who != null ? chest(who, partial) : e.fallback;
    }

    /** A point {@code t} of the way along a writhing tether from {@code a} to {@code b}. */
    private static Vec3 along(Vec3 a, Vec3 b, float t, float age, float writhe) {
        Vec3 base = a.lerp(b, t);
        Vec3 dir = b.subtract(a);
        Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        float env = Mth.sin(t * Mth.PI) * writhe;
        double s1 = Math.sin(age * 0.7 + t * 9.0) * env;
        double s2 = Math.cos(age * 0.55 + t * 7.0) * env;
        return base.add(side.scale(s1)).add(0.0, s2 + Mth.sin(t * Mth.PI) * 0.4, 0.0);
    }

    /** A camera-facing ribbon along the tether, {@code reach} (0..1) of the way from {@code a}. */
    private static void tether(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float reach, Vec3 cam, float width,
                               float r, float g, float bl, float alpha, float age, float writhe) {
        if (alpha <= 0.004f || reach <= 0.0f) {
            return;
        }
        int segs = 20;
        // The ribbon is drawn from the stone end, so a snapping tether retracts into the vessel.
        Vec3 prev = along(a, b, 1.0f, age, writhe);
        for (int i = 1; i <= segs; i++) {
            float t = 1.0f - reach * i / (float) segs;
            Vec3 next = along(a, b, t, age, writhe);
            Vec3 seg = next.subtract(prev);
            Vec3 toCam = cam.subtract(prev);
            Vec3 w = seg.cross(toCam);
            if (w.lengthSqr() > 1.0E-8) {
                w = w.normalize().scale(width * 0.5);
                float cr = r * alpha, cg = g * alpha, cb = bl * alpha;
                SpellFx.vertex(vc, m, rel(prev.add(w), cam, 0), rel(prev.add(w), cam, 1), rel(prev.add(w), cam, 2), 0.5f, 0.0f, cr, cg, cb, alpha);
                SpellFx.vertex(vc, m, rel(prev.subtract(w), cam, 0), rel(prev.subtract(w), cam, 1), rel(prev.subtract(w), cam, 2), 0.5f, 1.0f, cr, cg, cb, alpha);
                SpellFx.vertex(vc, m, rel(next.subtract(w), cam, 0), rel(next.subtract(w), cam, 1), rel(next.subtract(w), cam, 2), 0.5f, 1.0f, cr, cg, cb, alpha);
                SpellFx.vertex(vc, m, rel(next.add(w), cam, 0), rel(next.add(w), cam, 1), rel(next.add(w), cam, 2), 0.5f, 0.0f, cr, cg, cb, alpha);
            }
            prev = next;
        }
    }

    private static float rel(Vec3 p, Vec3 cam, int axis) {
        return (float) (axis == 0 ? p.x - cam.x : axis == 1 ? p.y - cam.y : p.z - cam.z);
    }

    /** A vertical column of light standing on {@code base}, turned to face the camera about its own axis. */
    private static void column(VertexConsumer vc, Matrix4f m, Vec3 base, float height, float width, Vec3 cam,
                               float r, float g, float b, float a) {
        if (a <= 0.004f || width <= 0.01f) {
            return;
        }
        double dx = cam.x - base.x, dz = cam.z - base.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        float sx = len < 1.0E-4 ? 1.0f : (float) (-dz / len) * width * 0.5f;
        float sz = len < 1.0E-4 ? 0.0f : (float) (dx / len) * width * 0.5f;
        float cx = (float) (base.x - cam.x), cy = (float) (base.y - cam.y), cz = (float) (base.z - cam.z);
        float cr = r * a, cg = g * a, cb = b * a;
        // u across the width uses the round glow sprite's middle row, so the edges stay soft.
        SpellFx.vertex(vc, m, cx - sx, cy, cz - sz, 0.0f, 0.5f, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx + sx, cy, cz + sz, 1.0f, 0.5f, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx + sx, cy + height, cz + sz, 1.0f, 0.5f, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx - sx, cy + height, cz - sz, 0.0f, 0.5f, cr, cg, cb, a);
    }

    /** A camera-facing quad at {@code at}, {@code size} across; additive, so the fade darkens the colour. */
    private static void billboard(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 cam, Vector3f left, Vector3f up,
                                  float size, float r, float g, float b, float a) {
        if (a <= 0.004f) {
            return;
        }
        float h = size * 0.5f;
        float cx = (float) (at.x - cam.x), cy = (float) (at.y - cam.y), cz = (float) (at.z - cam.z);
        float lx = left.x() * h, ly = left.y() * h, lz = left.z() * h;
        float ux = up.x() * h, uy = up.y() * h, uz = up.z() * h;
        float cr = r * a, cg = g * a, cb = b * a;
        SpellFx.vertex(vc, m, cx + lx + ux, cy + ly + uy, cz + lz + uz, 0, 0, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx - lx + ux, cy - ly + uy, cz - lz + uz, 1, 0, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx - lx - ux, cy - ly - uy, cz - lz - uz, 1, 1, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx + lx - ux, cy + ly - uy, cz + lz - uz, 0, 1, cr, cg, cb, a);
    }

    /** A ring sprite lying flat over {@code at}, {@code half} blocks to each side. */
    private static void flat(VertexConsumer vc, Matrix4f m, Vec3 at, Vec3 cam, float half, float r, float g, float b,
                             float a, float lift) {
        if (a <= 0.004f || half <= 0.001f) {
            return;
        }
        float cx = (float) (at.x - cam.x), cy = (float) (at.y + lift - cam.y), cz = (float) (at.z - cam.z);
        float cr = r * a, cg = g * a, cb = b * a;
        SpellFx.vertex(vc, m, cx - half, cy, cz - half, 0, 0, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx + half, cy, cz - half, 1, 0, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx + half, cy, cz + half, 1, 1, cr, cg, cb, a);
        SpellFx.vertex(vc, m, cx - half, cy, cz + half, 0, 1, cr, cg, cb, a);
    }

    private static float fadeInOut(float age, float in, float life, float out) {
        return Mth.clamp(Math.min(age / in, (life - age) / out), 0.0f, 1.0f);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0f;
    }

    static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0f;
    }

    static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0f;
    }
}
