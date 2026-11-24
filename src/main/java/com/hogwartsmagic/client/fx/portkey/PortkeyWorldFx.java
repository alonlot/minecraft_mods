package com.hogwartsmagic.client.fx.portkey;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.ModRenderTypes;
import com.hogwartsmagic.client.SpellFx;
import com.hogwartsmagic.client.fx.FxClient;
import com.hogwartsmagic.portkey.PortkeyTimeline;
import com.hogwartsmagic.util.MagicParticles;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import static com.hogwartsmagic.client.SpellFxGeometry.billboard;
import static com.hogwartsmagic.client.SpellFxGeometry.frame;
import static com.hogwartsmagic.client.SpellFxGeometry.hash;
import static com.hogwartsmagic.client.SpellFxGeometry.randomDirection;
import static com.hogwartsmagic.client.SpellFxGeometry.ribbon;
import static com.hogwartsmagic.client.SpellFxGeometry.ring;
import static com.hogwartsmagic.portkey.PortkeyTimeline.*;

/**
 * What everyone near a Portkey sees. While it charges: a cold blue-white glow at the holder's hand, flickering and
 * swelling; three ribbons of light spiralling in from the ground two blocks out to the boot, spinning faster as it
 * builds, pulses running along them inwards; a ripple of light running out along the ground every three-quarters of
 * a second, and twelve runes lying in a ring round the holder's feet glinting as each ripple passes; motes drawn in
 * out of the air. At the hook: a sphere of light collapsing to a point, five vortex ribbons winding up and in, a thin
 * white streak standing up through the middle, a shock ring along the ground, sparks and gold glints sucked in and
 * then spat out. At the far end: a point of light bursting into a sphere, ribbons flung outwards, a ring of displaced
 * air rolling out, dust and leaves thrown up, and the scuffed landing mark. A bind flashes a ring of runes at the
 * feet.
 *
 * <p>Camera-relative at {@code AFTER_TRANSLUCENT_BLOCKS} with an identity matrix (the event's pose is not used), in
 * the bloom-routed light types, so it glows into the air. The holder's own glow and spirals are left out in first
 * person: their screen carries them. Nothing past {@code WORLD_CULL} blocks is drawn, every list is capped, and the
 * path arrays are reused (render thread only).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PortkeyWorldFx {

    private static final ResourceLocation RUNES_TEX = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "textures/fx/portkey_runes.png");
    private static final ResourceLocation RING_TEX = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID,
            "textures/fx/portkey_ring.png");
    private static final RenderType RUNES = ModRenderTypes.magicGlow(RUNES_TEX);
    private static final RenderType RING = ModRenderTypes.magicGlow(RING_TEX);

    /** Third-person hand estimate: this far along the body's facing from the eyes, this far below them. */
    private static final double HAND_REACH = 0.65, HAND_DROP = 0.35;
    /** First-person boot, for particles aimed at it: along the view and below the eye. */
    private static final double VIEW_BOOT = 0.62, VIEW_BOOT_DROP = 0.25;
    /** A rune's half-size on the ground (blocks). */
    private static final float RUNE_HALF = 0.17f;
    /** Particle counts (each burst stays under 60). */
    private static final int HOOK_SPARKS = 24, HOOK_GOLD = 12, HOOK_OUT = 16, OWN_GLINTS = 28;
    private static final int ARRIVE_DUST = 30, ARRIVE_LEAVES = 8, ARRIVE_SMOKE = 10, ARRIVE_GLINTS = 10;
    private static final int CANCEL_PUFF = 8, CRUMBS = 16, CRUMB_SMOKE = 6, BIND_GLINTS = 10;
    /** Motes travel about this many times their starting speed before friction stops them. */
    private static final double DRIFT = 14.0;

    // Scratch for the ribbons (render thread only).
    private static final Vec3[] PATH24 = new Vec3[SPIRAL_SEGMENTS];
    private static final float[] ALPHA24 = new float[SPIRAL_SEGMENTS];
    private static final float[] WIDTH24 = new float[SPIRAL_SEGMENTS];
    private static final Vec3[] PATH16 = new Vec3[VORTEX_SEGMENTS];
    private static final float[] ALPHA16 = new float[VORTEX_SEGMENTS];
    private static final float[] WIDTH16 = new float[VORTEX_SEGMENTS];
    private static final Vec3 X = new Vec3(1.0, 0.0, 0.0);
    private static final Vec3 Z = new Vec3(0.0, 0.0, 1.0);

    private PortkeyWorldFx() {
    }

    // ---- where things are -------------------------------------------------------------------------------------

    /** Where {@code holder}'s boot is: the first-person boot for our own view, else a third-person hand estimate. */
    static Vec3 handOf(Entity holder, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (holder == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return holder.getEyePosition(partial).add(holder.getViewVector(partial).scale(VIEW_BOOT))
                    .add(0.0, -VIEW_BOOT_DROP, 0.0);
        }
        float yaw = (holder instanceof net.minecraft.world.entity.LivingEntity living
                ? Mth.rotLerp(partial, living.yBodyRotO, living.yBodyRot) : holder.getViewYRot(partial))
                * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        return holder.getEyePosition(partial).add(facing.scale(HAND_REACH)).add(0.0, -HAND_DROP, 0.0);
    }

    private static Vec3 colourGlow(boolean old) {
        return old ? new Vec3(Mth.lerp(0.35f, GLOW_R, GOLD_R), Mth.lerp(0.35f, GLOW_G, GOLD_G),
                Mth.lerp(0.35f, GLOW_B, GOLD_B)) : new Vec3(GLOW_R, GLOW_G, GLOW_B);
    }

    // ---- particles --------------------------------------------------------------------------------------------

    private static void add(ClientLevel level, ParticleOptions p, Vec3 at, Vec3 v) {
        level.addParticle(p, at.x, at.y, at.z, v.x, v.y, v.z);
    }

    /** Once a client tick: motes drawn in to each charging boot, the implosion's sparks spat back out. */
    static void tickParticles(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        RandomSource random = level.random;
        double now = level.getGameTime();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (PortkeyClient.WorldEvent e : PortkeyClient.worldEvents()) {
            if (e.pos().distanceToSqr(camera) > WORLD_CULL * WORLD_CULL) {
                continue;
            }
            float t = e.age(now);
            if ("charge".equals(e.kind()) && e.end() < 0.0) {
                Entity holder = level.getEntity(e.entityId());
                if (holder == null) {
                    continue;
                }
                Vec3 hand = handOf(holder, 1.0f);
                float ease = PortkeyClient.worldCharge(e, now);
                int count = Math.round(Mth.lerp(ease, MOTES_FROM, MOTES_TO) + random.nextFloat() - 0.5f);
                for (int i = 0; i < count; i++) {
                    double ang = random.nextDouble() * Math.PI * 2.0;
                    double up = (random.nextDouble() - 0.4) * 1.4;
                    Vec3 from = hand.add(Math.cos(ang) * MOTE_RADIUS, up, Math.sin(ang) * MOTE_RADIUS);
                    Vec3 v = hand.subtract(from).scale(1.0 / DRIFT);
                    add(level, random.nextFloat() < 0.7f ? MagicParticles.MOTE_ICE : MagicParticles.GLINT_WHITE,
                            from, v);
                }
            } else if ("hook".equals(e.kind()) && t >= IMPLOSION_TICKS && t < IMPLOSION_TICKS + 1.0f) {
                Vec3 c = e.pos().add(0.0, 1.0, 0.0);
                for (int i = 0; i < HOOK_OUT; i++) {
                    Vec3 dir = randomDirection(e.seed() + i * 101);
                    add(level, MagicParticles.SPARK_WHITE, c, dir.scale(0.22 + 0.12 * random.nextDouble()));
                }
            }
        }
    }

    /** A cancelled charge: a puff of white smoke where the boot was. */
    static void cancelPuff(FxClient.FxEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity holder = e.entity();
        Vec3 at = holder != null ? handOf(holder, 1.0f) : e.pos().add(0.0, 1.1, 0.0);
        RandomSource random = mc.level.random;
        for (int i = 0; i < CANCEL_PUFF; i++) {
            add(mc.level, MagicParticles.SMOKE_WHITE, at.add((random.nextDouble() - 0.5) * 0.2,
                    (random.nextDouble() - 0.5) * 0.2, (random.nextDouble() - 0.5) * 0.2),
                    new Vec3((random.nextDouble() - 0.5) * 0.06, 0.03 + random.nextDouble() * 0.04,
                            (random.nextDouble() - 0.5) * 0.06));
        }
    }

    /** The implosion's sparks and gold glints, sucked into the middle. */
    static void hookBurst(FxClient.FxEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 c = e.pos().add(0.0, 1.0, 0.0);
        RandomSource random = mc.level.random;
        for (int i = 0; i < HOOK_SPARKS + HOOK_GOLD; i++) {
            Vec3 dir = randomDirection(e.entityId() * 31 + i * 7 + (int) mc.level.getGameTime());
            Vec3 from = c.add(dir.scale(1.6 + random.nextDouble() * 0.6));
            Vec3 in = c.subtract(from);
            if (i < HOOK_SPARKS) {
                add(mc.level, MagicParticles.SPARK_WHITE, from, in.scale(0.06));
            } else {
                add(mc.level, MagicParticles.GLINT_GOLD, from, in.scale(1.0 / DRIFT * 1.1));
            }
        }
    }

    /** Our own hook: glints snapping out of the air into the boot in front of us. */
    static void ownHookGlints() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Vec3 boot = handOf(mc.player, 1.0f);
        RandomSource random = mc.level.random;
        for (int i = 0; i < OWN_GLINTS; i++) {
            Vec3 dir = randomDirection(random.nextInt());
            Vec3 from = boot.add(dir.scale(1.2 + random.nextDouble() * 1.0));
            add(mc.level, i % 4 == 0 ? MagicParticles.GLINT_GOLD : MagicParticles.GLINT_WHITE, from,
                    boot.subtract(from).scale(1.05 / DRIFT));
        }
    }

    /** The far end: dust of the ground, leaves or grass, a ring of grey smoke rolling out, glints flung up. */
    static void arrivalBurst(Vec3 feet, int seed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(feet)
                > WORLD_CULL * WORLD_CULL) {
            return;
        }
        dust(mc.level, feet, seed, ARRIVE_DUST);
        RandomSource random = mc.level.random;
        for (int i = 0; i < ARRIVE_GLINTS; i++) {
            Vec3 dir = randomDirection(seed + i * 17);
            add(mc.level, MagicParticles.GLINT_WHITE, feet.add(0.0, 1.0, 0.0),
                    new Vec3(dir.x * 0.25, Math.abs(dir.y) * 0.2 + 0.05, dir.z * 0.25));
        }
        for (int i = 0; i < ARRIVE_SMOKE; i++) {
            double ang = i * Math.PI * 2.0 / ARRIVE_SMOKE + random.nextDouble() * 0.3;
            add(mc.level, MagicParticles.SMOKE_GREY, feet.add(Math.cos(ang) * 0.4, 0.15, Math.sin(ang) * 0.4),
                    new Vec3(Math.cos(ang) * 0.5, 0.01, Math.sin(ang) * 0.5));
        }
    }

    /** Our own landing: the same dust and leaves at our feet, once the world is back. */
    static void landingDust(Vec3 feet, int seed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            dust(mc.level, feet, seed, ARRIVE_DUST);
        }
    }

    private static void dust(ClientLevel level, Vec3 feet, int seed, int count) {
        RandomSource random = level.random;
        BlockPos below = BlockPos.containing(feet.x, feet.y - 0.2, feet.z);
        BlockState ground = level.getBlockState(below);
        if (ground.isAir()) {
            ground = Blocks.DIRT.defaultBlockState();
        }
        BlockParticleOption groundDust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        for (int i = 0; i < count; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            double speed = 0.15 + random.nextDouble() * 0.25;
            add(level, groundDust, feet.add(Math.cos(ang) * 0.3, 0.1, Math.sin(ang) * 0.3),
                    new Vec3(Math.cos(ang) * speed, 0.12 + random.nextDouble() * 0.2, Math.sin(ang) * speed));
        }
        BlockParticleOption bits = new BlockParticleOption(ParticleTypes.BLOCK, leavesNear(level, below));
        for (int i = 0; i < ARRIVE_LEAVES; i++) {
            double ang = random.nextDouble() * Math.PI * 2.0;
            add(level, bits, feet.add(Math.cos(ang) * 0.5, 0.4 + random.nextDouble() * 0.6, Math.sin(ang) * 0.5),
                    new Vec3(Math.cos(ang) * 0.2, 0.15 + random.nextDouble() * 0.15, Math.sin(ang) * 0.2));
        }
    }

    /** The nearest leaves within a few blocks, for the leaf bits; grass when there are none. */
    private static BlockState leavesNear(ClientLevel level, BlockPos at) {
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -1; dy <= r + 1; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        BlockState s = level.getBlockState(at.offset(dx, dy, dz));
                        if (s.is(BlockTags.LEAVES)) {
                            return s;
                        }
                    }
                }
            }
        }
        return Blocks.SHORT_GRASS.defaultBlockState();
    }

    /** The Old Portkey falls to crumbs and dust in the hand. */
    static void crumbs(FxClient.FxEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity holder = e.entity();
        Vec3 at = holder != null ? handOf(holder, 1.0f) : e.pos().add(0.0, 1.1, 0.0);
        RandomSource random = mc.level.random;
        ItemParticleOption crumb = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ExampleMod.OLD_PORTKEY.get()));
        for (int i = 0; i < CRUMBS; i++) {
            add(mc.level, crumb, at, new Vec3((random.nextDouble() - 0.5) * 0.12, random.nextDouble() * 0.08,
                    (random.nextDouble() - 0.5) * 0.12));
        }
        for (int i = 0; i < CRUMB_SMOKE; i++) {
            add(mc.level, MagicParticles.SMOKE_GREY, at, new Vec3((random.nextDouble() - 0.5) * 0.05, 0.02,
                    (random.nextDouble() - 0.5) * 0.05));
        }
    }

    /** A bind: glints rising off the rune ring. */
    static void bindGlints(Vec3 feet, int seed) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (int i = 0; i < BIND_GLINTS; i++) {
            double ang = i * Math.PI * 2.0 / BIND_GLINTS + hash(seed, i, 3);
            add(mc.level, MagicParticles.GLINT_WHITE,
                    feet.add(Math.cos(ang) * BIND_RING, 0.08, Math.sin(ang) * BIND_RING),
                    new Vec3(0.0, 0.05 + 0.04 * hash(seed, i, 5), 0.0));
        }
    }

    // ---- drawing ----------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(false);
        double now = level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f m = new Matrix4f();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        PortkeyDecals.render(buffer, m, camera, now);
        if (PortkeyClient.worldEvents().isEmpty()) {
            return;
        }
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();

        VertexConsumer light = buffer.getBuffer(ModRenderTypes.AVADA_LIGHT);
        for (PortkeyClient.WorldEvent e : PortkeyClient.worldEvents()) {
            if (e.pos().distanceToSqr(camera) > WORLD_CULL * WORLD_CULL) {
                continue;
            }
            float t = e.age(now);
            if ("charge".equals(e.kind())) {
                drawCharge(light, m, level, e, camera, partial, now, firstPerson, mc);
            } else if ("hook".equals(e.kind())) {
                drawImplosion(light, m, e, e.pos().subtract(camera), t);
            } else if ("arrive".equals(e.kind())) {
                drawArrival(light, m, e, e.pos().subtract(camera), t, true);
            } else if ("land".equals(e.kind())) {
                drawArrival(light, m, e, e.pos().subtract(camera), t, false);
            } else if ("bind".equals(e.kind())) {
                float f = t / BIND_TICKS;
                float a = Mth.sin(Mth.PI * Mth.clamp(f, 0.0f, 1.0f));
                ring(light, m, e.pos().subtract(camera).add(0.0, 0.05, 0.0), X, Z, BIND_RING * (0.8f + 0.2f * f),
                        0.06f, 0.7f * a, GLOW_R, GLOW_G, GLOW_B, 40);
            }
        }
        buffer.endBatch(ModRenderTypes.AVADA_LIGHT);

        VertexConsumer runes = buffer.getBuffer(RUNES);
        for (PortkeyClient.WorldEvent e : PortkeyClient.worldEvents()) {
            if ("charge".equals(e.kind()) && e.pos().distanceToSqr(camera) <= WORLD_CULL * WORLD_CULL) {
                drawRunes(runes, m, level, e, camera, partial, now);
            }
        }
        buffer.endBatch(RUNES);

        VertexConsumer ringTex = buffer.getBuffer(RING);
        for (PortkeyClient.WorldEvent e : PortkeyClient.worldEvents()) {
            if ("bind".equals(e.kind()) && e.pos().distanceToSqr(camera) <= WORLD_CULL * WORLD_CULL) {
                float t = e.age(now);
                float a = Mth.sin(Mth.PI * Mth.clamp(t / BIND_TICKS, 0.0f, 1.0f));
                flat(ringTex, m, e.pos().subtract(camera).add(0.0, 0.04, 0.0), BIND_RING * 1.15f, t * 0.03f,
                        0, 0, 1, 1, GLOW_R, GLOW_G, GLOW_B, a);
            }
        }
        buffer.endBatch(RING);
    }

    /** The ground ripples' radius and strength for a charge at age {@code t}: up to two at once. */
    private static float rippleGlint(float t, float atRadius) {
        float glint = 0.0f;
        int newest = (int) Math.floor(t / RIPPLE_EVERY);
        for (int j = 0; j < 2; j++) {
            float f = (t - (newest - j) * RIPPLE_EVERY) / RIPPLE_TICKS;
            if (f < 0.0f || f >= 1.0f || newest - j < 0) {
                continue;
            }
            float r = RIPPLE_RADIUS * (1.0f - (1.0f - f) * (1.0f - f));
            float d = (r - atRadius) / 0.3f;
            glint += (float) Math.exp(-d * d) * (1.0f - f);
        }
        return glint;
    }

    private static void drawCharge(VertexConsumer out, Matrix4f m, ClientLevel level, PortkeyClient.WorldEvent e,
                                   Vec3 camera, float partial, double now, boolean firstPerson, Minecraft mc) {
        Entity holder = level.getEntity(e.entityId());
        Vec3 feetW = holder != null ? holder.getPosition(partial) : e.pos();
        Vec3 handW = holder != null ? handOf(holder, partial) : feetW.add(0.0, 1.1, 0.0);
        float t = e.age(now);
        float ease = PortkeyClient.worldCharge(e, now);
        float fade = e.end() < 0.0 ? 1.0f
                : 1.0f - Mth.clamp((float) (now - e.end()) / CANCEL_TICKS, 0.0f, 1.0f);
        if (fade <= 0.0f) {
            return;
        }
        Vec3 glow = colourGlow(e.old());
        Vec3 feet = feetW.subtract(camera);
        boolean ownFirst = holder == mc.player && firstPerson;
        if (!ownFirst) {
            Vec3 hand = handW.subtract(camera);
            float radius = WORLD_GLOW_RADIUS + WORLD_GLOW_RADIUS_GAIN * ease;
            float flicker = 0.85f + 0.15f * Mth.sin(t * 1.9f) * Mth.sin(t * 0.73f + 1.0f);
            billboard(out, m, hand, radius * 2.4f, 0.3f * (0.3f + 0.7f * ease) * fade, (float) glow.x,
                    (float) glow.y, (float) glow.z, false);
            billboard(out, m, hand, radius, (0.55f + 0.45f * ease) * flicker * fade, CORE_R, CORE_G, CORE_B, true);
            drawSpirals(out, m, e, hand, feet, t, ease, fade, glow);
        }
        // Ripples running out along the ground.
        int newest = (int) Math.floor(t / RIPPLE_EVERY);
        for (int j = 0; j < 2; j++) {
            if (newest - j < 0) {
                continue;
            }
            float f = (t - (newest - j) * RIPPLE_EVERY) / RIPPLE_TICKS;
            if (f < 0.0f || f >= 1.0f) {
                continue;
            }
            float r = RIPPLE_RADIUS * (1.0f - (1.0f - f) * (1.0f - f));
            float a = (float) Math.pow(1.0f - f, 1.5) * (0.3f + 0.6f * ease) * fade;
            ring(out, m, feet.add(0.0, 0.05, 0.0), X, Z, r, 0.08f + 0.12f * f, a, CYAN_R, CYAN_G, CYAN_B, 48);
        }
        // A faint standing ring under the runes.
        ring(out, m, feet.add(0.0, 0.04, 0.0), X, Z, RUNE_RING, 0.05f,
                0.25f * ease * fade * smoothstep(0.0f, 10.0f, t), (float) glow.x, (float) glow.y, (float) glow.z, 48);
    }

    /** Three ribbons spiralling in from the ground two blocks out to the boot, pulses running inwards along them. */
    private static void drawSpirals(VertexConsumer out, Matrix4f m, PortkeyClient.WorldEvent e, Vec3 hand, Vec3 feet,
                                    float t, float ease, float fade, Vec3 glow) {
        float u = Mth.clamp(t / com.hogwartsmagic.portkey.PortkeyRules.CHARGE_TICKS, 0.0f, 1.0f);
        // The spin speeds up with the charge: the integral of lerp(from, to, ease).
        double turn = SPIRAL_SPIN_FROM * t + (SPIRAL_SPIN_TO - SPIRAL_SPIN_FROM)
                * (com.hogwartsmagic.portkey.PortkeyRules.CHARGE_TICKS / (CHARGE_EASE_POWER + 1.0f))
                * Math.pow(u, CHARGE_EASE_POWER + 1.0f);
        float a = (SPIRAL_ALPHA + SPIRAL_ALPHA_GAIN * ease) * fade * smoothstep(0.0f, 6.0f, t);
        if (a <= 0.01f) {
            return;
        }
        int n = SPIRAL_SEGMENTS;
        for (int k = 0; k < SPIRAL_RIBBONS; k++) {
            double base = turn + k * Math.PI * 2.0 / SPIRAL_RIBBONS + hash(e.seed(), k, 1) * 0.5;
            for (int i = 0; i < n; i++) {
                float p = i / (float) (n - 1);
                double radius = SPIRAL_RADIUS * Math.pow(1.0 - p, 1.3);
                double angle = base + p * 4.2;
                double cx = Mth.lerp(p, feet.x, hand.x);
                double cz = Mth.lerp(p, feet.z, hand.z);
                double y = Mth.lerp(Math.pow(p, 0.8), feet.y + 0.1, hand.y);
                PATH24[i] = new Vec3(cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius);
                float pulse = 0.55f + 0.45f * Mth.sin(p * 14.0f - t * 0.9f + k * 2.1f);
                ALPHA24[i] = a * smoothstep(0.0f, 0.12f, p) * (1.0f - smoothstep(0.86f, 1.0f, p)) * pulse;
                WIDTH24[i] = 0.012f + 0.05f * (1.0f - p);
            }
            ribbon(out, m, PATH24, WIDTH24, ALPHA24, 2.6f, 0.32f, (float) glow.x, (float) glow.y, (float) glow.z);
            ribbon(out, m, PATH24, WIDTH24, ALPHA24, 0.7f, 1.0f, CORE_R, CORE_G, CORE_B);
        }
    }

    /** Twelve runes lying round the holder's feet, glinting as each ripple passes. */
    private static void drawRunes(VertexConsumer out, Matrix4f m, ClientLevel level, PortkeyClient.WorldEvent e,
                                  Vec3 camera, float partial, double now) {
        Entity holder = level.getEntity(e.entityId());
        Vec3 feet = (holder != null ? holder.getPosition(partial) : e.pos()).subtract(camera);
        float t = e.age(now);
        float ease = PortkeyClient.worldCharge(e, now);
        float fade = e.end() < 0.0 ? 1.0f
                : 1.0f - Mth.clamp((float) (now - e.end()) / CANCEL_TICKS, 0.0f, 1.0f);
        float show = smoothstep(0.0f, 10.0f, t) * fade;
        if (show <= 0.01f) {
            return;
        }
        float glint = rippleGlint(t, RUNE_RING);
        float spin = t * 0.012f;
        Vec3 glow = colourGlow(e.old());
        for (int i = 0; i < RUNE_COUNT; i++) {
            double ang = spin + i * Math.PI * 2.0 / RUNE_COUNT;
            Vec3 at = feet.add(Math.cos(ang) * RUNE_RING, 0.045, Math.sin(ang) * RUNE_RING);
            int glyph = (int) (hash(e.seed(), i, 9) * 16.0f) & 15;
            float u0 = (glyph % 4) * 0.25f;
            float v0 = (glyph / 4) * 0.25f;
            float flick = 0.8f + 0.2f * Mth.sin(t * 0.5f + i * 1.3f);
            float a = Math.min(1.0f, show * ((0.15f + 0.3f * ease) * flick + 0.9f * glint));
            flat(out, m, at, RUNE_HALF, (float) ang + Mth.HALF_PI, u0, v0, u0 + 0.25f, v0 + 0.25f,
                    (float) glow.x, (float) glow.y, (float) glow.z, a);
        }
    }

    /** The implosion where the group stood. */
    private static void drawImplosion(VertexConsumer out, Matrix4f m, PortkeyClient.WorldEvent e, Vec3 feet, float t) {
        Vec3 c = feet.add(0.0, 1.0, 0.0);
        float f = Mth.clamp(t / IMPLOSION_TICKS, 0.0f, 1.0f);
        if (t < IMPLOSION_TICKS) {
            float r = IMPLOSION_RADIUS * (1.0f - f * f);
            billboard(out, m, c, r * 1.6f, 0.35f * IMPLOSION_ALPHA * (1.0f - 0.4f * f), GLOW_R, GLOW_G, GLOW_B,
                    false);
            billboard(out, m, c, r + 0.05f, IMPLOSION_ALPHA * (1.0f - 0.3f * f), CORE_R, CORE_G, CORE_B, true);
        } else if (t < IMPLOSION_TICKS + 3.0f) {
            float g = (t - IMPLOSION_TICKS) / 3.0f;
            billboard(out, m, c, 0.5f + 0.4f * g, (1.0f - g) * (1.0f - g), 1.0f, 1.0f, 1.0f, true);
        }
        // Five ribbons winding up and in to a point above the feet.
        float g = Mth.clamp(t / VORTEX_TICKS, 0.0f, 1.0f);
        if (g < 1.0f) {
            float a = (1.0f - g) * smoothstep(0.0f, 1.0f, t);
            float shrink = (float) Math.pow(1.0f - g, 1.5);
            for (int k = 0; k < VORTEX_RIBBONS; k++) {
                double base = k * Math.PI * 2.0 / VORTEX_RIBBONS + t * 0.6 + t * t * 0.05 + hash(e.seed(), k, 2);
                for (int i = 0; i < VORTEX_SEGMENTS; i++) {
                    float p = i / (float) (VORTEX_SEGMENTS - 1);
                    double radius = SPIRAL_RADIUS * (1.0 - p) * shrink;
                    double angle = base + p * 3.5;
                    PATH16[i] = feet.add(Math.cos(angle) * radius, Mth.lerp(p, 0.05, VORTEX_TOP),
                            Math.sin(angle) * radius);
                    ALPHA16[i] = a * smoothstep(0.0f, 0.1f, p) * (0.4f + 0.6f * p);
                    WIDTH16[i] = 0.02f + 0.05f * (1.0f - p);
                }
                boolean gold = k == 2;
                ribbon(out, m, PATH16, WIDTH16, ALPHA16, 2.4f, 0.35f, gold ? GOLD_R : GLOW_R, gold ? GOLD_G : GLOW_G,
                        gold ? GOLD_B : GLOW_B);
                ribbon(out, m, PATH16, WIDTH16, ALPHA16, 0.7f, 1.0f, CORE_R, CORE_G, CORE_B);
            }
        }
        // The white streak standing up through the middle.
        if (t > STREAK_FROM && t < STREAK_TO) {
            float s = Mth.sin(Mth.PI * (t - STREAK_FROM) / (STREAK_TO - STREAK_FROM));
            Vec3[] line = {feet.add(0.0, -0.2, 0.0), feet.add(0.0, 1.0, 0.0), feet.add(0.0, STREAK_HEIGHT - 0.2, 0.0)};
            float[] w = {STREAK_WIDTH * 0.3f, STREAK_WIDTH, STREAK_WIDTH * 0.3f};
            float[] al = {0.0f, s, 0.0f};
            ribbon(out, m, line, w, al, 3.0f, 0.4f, GLOW_R, GLOW_G, GLOW_B);
            ribbon(out, m, line, w, al, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        // The shock ring along the ground.
        float sf = Mth.clamp(t / DEPART_RING_TICKS, 0.0f, 1.0f);
        if (sf < 1.0f) {
            ring(out, m, feet.add(0.0, 0.06, 0.0), X, Z, DEPART_RING * (1.0f - (1.0f - sf) * (1.0f - sf)),
                    0.15f + 0.25f * sf, 0.8f * (float) Math.pow(1.0f - sf, 1.5), GLOW_R, GLOW_G, GLOW_B, 56);
        }
    }

    /** The burst where the group lands ({@code full}); our own landing gets only the rings ({@code !full}). */
    private static void drawArrival(VertexConsumer out, Matrix4f m, PortkeyClient.WorldEvent e, Vec3 feet, float t,
                                    boolean full) {
        Vec3 c = feet.add(0.0, 1.0, 0.0);
        if (full) {
            float f = Mth.clamp(t / ARRIVE_SPHERE_TICKS, 0.0f, 1.0f);
            if (f < 1.0f) {
                float r = ARRIVE_SPHERE * (1.0f - (1.0f - f) * (1.0f - f)) + 0.15f;
                billboard(out, m, c, r, 0.95f * (float) Math.pow(1.0f - f, 1.5), CORE_R, CORE_G, CORE_B, true);
                billboard(out, m, c, r * 1.5f, 0.3f * (1.0f - f), GLOW_R, GLOW_G, GLOW_B, false);
            }
            float shell = Mth.clamp(t / (ARRIVE_SPHERE_TICKS + 1.0f), 0.0f, 1.0f);
            if (shell < 1.0f && c.lengthSqr() > 1.0e-6) {
                Vec3[] facing = frame(c.scale(-1.0).normalize());
                ring(out, m, c, facing[0], facing[1], ARRIVE_SPHERE * (1.0f - (1.0f - shell) * (1.0f - shell)),
                        0.14f, 0.6f * (1.0f - shell), CORE_R, CORE_G, CORE_B, 40);
            }
            // Five ribbons flung outwards from the point of light.
            float g = Mth.clamp(t / VORTEX_TICKS, 0.0f, 1.0f);
            if (g < 1.0f) {
                float a = (1.0f - g) * (1.0f - g);
                for (int k = 0; k < VORTEX_RIBBONS; k++) {
                    double base = k * Math.PI * 2.0 / VORTEX_RIBBONS - t * 0.5 + hash(e.seed(), k, 4);
                    for (int i = 0; i < VORTEX_SEGMENTS; i++) {
                        float p = i / (float) (VORTEX_SEGMENTS - 1);
                        double radius = ARRIVE_RING * 0.7 * g * p;
                        double angle = base + p * 2.8;
                        PATH16[i] = c.add(Math.cos(angle) * radius, -0.9 * p * g, Math.sin(angle) * radius);
                        ALPHA16[i] = a * (1.0f - p) * smoothstep(0.0f, 0.15f, p);
                        WIDTH16[i] = 0.02f + 0.05f * p;
                    }
                    ribbon(out, m, PATH16, WIDTH16, ALPHA16, 2.4f, 0.35f, GLOW_R, GLOW_G, GLOW_B);
                    ribbon(out, m, PATH16, WIDTH16, ALPHA16, 0.7f, 1.0f, CORE_R, CORE_G, CORE_B);
                }
            }
        }
        float rf = Mth.clamp(t / ARRIVE_RING_TICKS, 0.0f, 1.0f);
        if (rf < 1.0f) {
            float r = ARRIVE_RING * (1.0f - (1.0f - rf) * (1.0f - rf));
            ring(out, m, feet.add(0.0, 0.06, 0.0), X, Z, r, 0.2f + 0.3f * rf,
                    (full ? 0.75f : 0.55f) * (float) Math.pow(1.0f - rf, 1.5), GLOW_R, GLOW_G, GLOW_B, 56);
        }
        float lag = Mth.clamp((t - 2.0f) / ARRIVE_RING_TICKS, 0.0f, 1.0f);
        if (t > 2.0f && lag < 1.0f) {
            float r = ARRIVE_RING * 0.75f * (1.0f - (1.0f - lag) * (1.0f - lag));
            ring(out, m, feet.add(0.0, 0.05, 0.0), X, Z, r, 0.1f, 0.45f * (1.0f - lag), CYAN_R, CYAN_G, CYAN_B, 48);
        }
    }

    /** A textured quad lying flat at {@code at} (camera-relative), premultiplied for the additive glow type. */
    private static void flat(VertexConsumer vc, Matrix4f m, Vec3 at, float half, float rot, float u0, float v0,
                             float u1, float v1, float r, float g, float b, float a) {
        if (a <= 0.004f) {
            return;
        }
        float cx = (float) at.x, cy = (float) at.y, cz = (float) at.z;
        float c = Mth.cos(rot) * half, s = Mth.sin(rot) * half;
        float pr = r * a, pg = g * a, pb = b * a;
        SpellFx.vertex(vc, m, cx - c + s, cy, cz - s - c, u0, v0, pr, pg, pb, a);
        SpellFx.vertex(vc, m, cx + c + s, cy, cz + s - c, u1, v0, pr, pg, pb, a);
        SpellFx.vertex(vc, m, cx + c - s, cy, cz + s + c, u1, v1, pr, pg, pb, a);
        SpellFx.vertex(vc, m, cx - c - s, cy, cz - s + c, u0, v1, pr, pg, pb, a);
    }
}
