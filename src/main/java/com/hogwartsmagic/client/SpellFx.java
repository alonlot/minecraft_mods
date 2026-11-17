package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.EnumSet;
import java.util.Set;

/**
 * Client look of a spell in flight: the colours, the sparkle trail, the cast flash and the impact burst.
 * Everything here is visual only and made on this client; the server still owns the spell.
 * No switch on an enum (project rule for client code): spell families are EnumSets.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class SpellFx {

    /** Spells that crackle with small lightning arcs: the violent curses and the stunner. */
    public static final Set<SpellType> CRACKLING = EnumSet.of(
            SpellType.AVADA_KEDAVRA, SpellType.CRUCIO, SpellType.DOLOHOV_CURSE, SpellType.STUPEFY,
            SpellType.SECTUMSEMPRA, SpellType.MORSMORDRE);
    /** Spells whose impact also throws a shockwave ring. */
    public static final Set<SpellType> HEAVY = EnumSet.of(
            SpellType.BOMBARDA, SpellType.CONFRINGO, SpellType.DEPULSO, SpellType.STUPEFY,
            SpellType.AVADA_KEDAVRA, SpellType.EXPELLIARMUS, SpellType.FIENDFYRE);
    /** Spells with no glowing orb (Aqua Eructo is an instant water lash). */
    public static final Set<SpellType> NO_ORB = EnumSet.of(SpellType.AQUA_ERUCTO);

    private SpellFx() {
    }

    /**
     * The spell colour made bright enough to read as light. Additive light cannot be dark, so a dark spell
     * (Sectumsempra, Morsmordre, Tenebris) keeps its hue but is lifted until its brightest channel is 0.7.
     */
    public static float[] lightColor(SpellType spell) {
        float r = spell.getR();
        float g = spell.getG();
        float b = spell.getB();
        float max = Math.max(r, Math.max(g, b));
        if (max < 0.05F) {
            return new float[]{0.6F, 0.6F, 0.65F};
        }
        if (max < 0.7F) {
            float lift = 0.7F / max;
            r *= lift;
            g *= lift;
            b *= lift;
        }
        return new float[]{r, g, b};
    }

    /** Mixes a colour towards white: 0 keeps it, 1 is white. */
    public static float[] whiten(float[] c, float amount) {
        return new float[]{
                Mth.lerp(amount, c[0], 1.0F),
                Mth.lerp(amount, c[1], 1.0F),
                Mth.lerp(amount, c[2], 1.0F)};
    }

    // ---------------------------------------------------------------- world effects (particles)

    /** The burst at the wand when a spell leaves it: a colour flash, a white-hot core and a spray of sparks. */
    public static void castFlash(Vec3 at, Vec3 forward, SpellType spell) {
        float[] c = lightColor(spell);
        float[] hot = whiten(c, 0.6F);
        SpellGlowParticle.spawn(at.x, at.y, at.z, 0, 0, 0, c[0], c[1], c[2], 0.9F, 0.55F, 1.0F, 5, 0, 1, false);
        SpellGlowParticle.spawn(at.x, at.y, at.z, 0, 0, 0, hot[0], hot[1], hot[2], 1.0F, 0.22F, 0.3F, 4, 0, 1, false);
        RandomSource random = RandomSource.create();
        for (int i = 0; i < 8; i++) {
            Vec3 v = forward.scale(0.08 + random.nextDouble() * 0.12)
                    .add(randomUnit(random).scale(0.06 + random.nextDouble() * 0.05));
            SpellGlowParticle.spawn(at.x, at.y, at.z, v.x, v.y, v.z, hot[0], hot[1], hot[2], 0.9F,
                    0.06F + random.nextFloat() * 0.05F, 0.02F, 6 + random.nextInt(5), 0.0F, 0.86F, false);
        }
    }

    /** Where a spell lands: a big flash, a core, sparks thrown outwards, and a shockwave ring for heavy spells. */
    public static void impactBurst(Vec3 at, SpellType spell) {
        float[] c = lightColor(spell);
        float[] hot = whiten(c, 0.55F);
        boolean heavy = HEAVY.contains(spell);
        SpellGlowParticle.spawn(at.x, at.y, at.z, 0, 0, 0, c[0], c[1], c[2], 0.95F,
                heavy ? 1.1F : 0.8F, heavy ? 2.2F : 1.4F, heavy ? 8 : 6, 0, 1, false);
        SpellGlowParticle.spawn(at.x, at.y, at.z, 0, 0, 0, hot[0], hot[1], hot[2], 1.0F, 0.4F, 0.55F, 4, 0, 1, false);
        if (heavy) {
            SpellGlowParticle.spawn(at.x, at.y, at.z, 0, 0, 0, hot[0], hot[1], hot[2], 0.9F, 0.4F, 3.2F, 9, 0, 1, true);
        }
        RandomSource random = RandomSource.create();
        int sparks = heavy ? 22 : 14;
        for (int i = 0; i < sparks; i++) {
            Vec3 v = randomUnit(random).scale(0.12 + random.nextDouble() * (heavy ? 0.28 : 0.18));
            float[] sc = random.nextBoolean() ? hot : c;
            SpellGlowParticle.spawn(at.x, at.y, at.z, v.x, v.y + 0.05, v.z, sc[0], sc[1], sc[2], 1.0F,
                    0.07F + random.nextFloat() * 0.07F, 0.02F, 10 + random.nextInt(10), 0.6F, 0.88F, false);
        }
    }

    /** One tick of sparkle trail for a beam in flight: small glows left along its path that drift and fade. */
    private static void trail(SpellBeamEntity beam) {
        SpellType spell = beam.getSpellType();
        if (NO_ORB.contains(spell) || beam.hasFreshClientImpact()) {
            return;
        }
        Vec3 vel = beam.getDeltaMovement();
        double speed = vel.length();
        if (speed < 1.0E-4) {
            return;
        }
        float[] c = lightColor(spell);
        float[] hot = whiten(c, 0.5F);
        RandomSource random = beam.getRandom();
        Vec3 pos = SpellPath.orb(beam, 0.0F); // the drawn orb, on its curve
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        if (pos.distanceToSqr(camera) < 9.0) {
            return; // a sparkle a block from the eye would fill the screen
        }
        for (int i = 0; i < 3; i++) {
            Vec3 at = pos.subtract(vel.scale(random.nextDouble()));
            Vec3 drift = randomUnit(random).scale(0.015 + random.nextDouble() * 0.02);
            float[] sc = random.nextInt(3) == 0 ? hot : c;
            SpellGlowParticle.spawn(at.x, at.y, at.z, drift.x, drift.y, drift.z, sc[0], sc[1], sc[2], 0.8F,
                    0.09F + random.nextFloat() * 0.08F, 0.01F, 8 + random.nextInt(8), 0.0F, 0.92F, false);
        }
    }

    private static Vec3 randomUnit(RandomSource random) {
        double y = random.nextDouble() * 2.0 - 1.0;
        double a = random.nextDouble() * Math.PI * 2.0;
        double h = Math.sqrt(1.0 - y * y);
        return new Vec3(Math.cos(a) * h, y, Math.sin(a) * h);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof SpellBeamEntity beam) {
                trail(beam);
            }
        }
    }

    /** The server discarding a beam is the client's cue for its impact burst. */
    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof SpellBeamEntity beam)) {
            return;
        }
        Entity.RemovalReason reason = beam.getRemovalReason();
        if (reason != Entity.RemovalReason.DISCARDED && reason != Entity.RemovalReason.KILLED) {
            return; // unloaded with its chunk, not a hit
        }
        SpellType spell = beam.getSpellType();
        if (NO_ORB.contains(spell)) {
            return;
        }
        Vec3 at = beam.getClientImpact();
        if (at == null) {
            at = beam.position().add(0.0, SpellBeamRenderer.ORB_HEIGHT, 0.0);
        }
        impactBurst(at, spell);
        CastFeel.onImpact(beam, at, spell);
    }

    // ---------------------------------------------------------------- quad drawing (renderers)

    /** One camera-facing glow quad in the current (already camera-rotated) pose, centred on (cx, cy, cz). */
    public static void glowQuad(VertexConsumer consumer, Matrix4f matrix, float cx, float cy, float cz, float half,
                                float r, float g, float b, float a) {
        vertex(consumer, matrix, cx - half, cy - half, cz, 0.0F, 1.0F, r, g, b, a);
        vertex(consumer, matrix, cx + half, cy - half, cz, 1.0F, 1.0F, r, g, b, a);
        vertex(consumer, matrix, cx + half, cy + half, cz, 1.0F, 0.0F, r, g, b, a);
        vertex(consumer, matrix, cx - half, cy + half, cz, 0.0F, 0.0F, r, g, b, a);
    }

    /** One vertex in the NEW_ENTITY format that the spell light render types use. */
    public static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v,
                              float r, float g, float b, float a) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    /**
     * A small jagged lightning arc in the XY plane of the current pose, from (0, 0) out to about {@code length},
     * drawn with the vanilla lightning type (position and colour only). The shape follows {@code seed}, so a
     * caller that changes the seed every tick gets a flickering crackle.
     */
    public static void crackle(VertexConsumer consumer, Matrix4f matrix, long seed, float length, float width,
                               float r, float g, float b, float a) {
        RandomSource random = RandomSource.create(seed);
        float angle = random.nextFloat() * Mth.TWO_PI;
        float x = 0.0F;
        float y = 0.0F;
        int segments = 5;
        float step = length / segments;
        for (int i = 0; i < segments; i++) {
            angle += (random.nextFloat() - 0.5F) * 1.6F;
            float nx = x + Mth.cos(angle) * step;
            float ny = y + Mth.sin(angle) * step;
            float w = width * (1.0F - i / (float) segments * 0.7F);
            float dx = nx - x;
            float dy = ny - y;
            float len = Mth.sqrt(dx * dx + dy * dy);
            float px = -dy / len * w;
            float py = dx / len * w;
            consumer.addVertex(matrix, x - px, y - py, 0.0F).setColor(r, g, b, a);
            consumer.addVertex(matrix, nx - px, ny - py, 0.0F).setColor(r, g, b, a);
            consumer.addVertex(matrix, nx + px, ny + py, 0.0F).setColor(r, g, b, a);
            consumer.addVertex(matrix, x + px, y + py, 0.0F).setColor(r, g, b, a);
            x = nx;
            y = ny;
        }
    }
}
