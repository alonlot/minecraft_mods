package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A hit felt on the body for the spells with no reaction of their own: what a beam strikes reels away from it and
 * lights up in the spell's colour. The spells HitReactions knows (the tumble, the flinch, the disarm...) are left
 * to it, flash included. Client-side and cosmetic. The cast's sounds are CastSounds'; the camera never moves.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CastFeel {

    /** Spells that help or move rather than hurt: what they strike lights up but does not reel. */
    private static final Set<SpellType> GENTLE = EnumSet.of(SpellType.PROTEGO, SpellType.PROTEGO_MAXIMA,
            SpellType.HEAL, SpellType.LUMOS, SpellType.REVELIO, SpellType.ALOHOMORA, SpellType.COLLOPORTUS,
            SpellType.REPARO, SpellType.WINGARDIUM_LEVIOSA, SpellType.ACCIO, SpellType.EXPECTO_PATRONUM,
            SpellType.CAVE_INIMICUM, SpellType.ALATUS, SpellType.ASCENDIO, SpellType.PIERTOTUM_LOCOMOTOR,
            SpellType.APPARITION);

    private static final int HIT_TICKS = 10;

    private record Hit(long start, float dx, float dz) {
    }

    private static final Map<Integer, Hit> HITS = new HashMap<>();
    /** Entities whose pose was pushed in RenderLivingEvent.Pre and must be popped in Post. */
    private static final Set<Integer> PUSHED = new HashSet<>();

    private CastFeel() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            HITS.clear();
            return;
        }
        HITS.values().removeIf(hit -> minecraft.level.getGameTime() - hit.start > HIT_TICKS);
    }

    /** A beam hit at {@code at}: the creature it struck (if any) reels away from it and flashes in its colour. */
    static void onImpact(SpellBeamEntity beam, Vec3 at, SpellType spell) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || HitReactions.hasReaction(spell)) {
            return; // its own reaction (Stupefy's tumble, Expelliarmus's disarm...) and flash: HitReactions
        }
        Entity owner = beam.getOwner();
        LivingEntity struck = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity living : minecraft.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.3))) {
            if (living == owner || !living.isAlive()) {
                continue;
            }
            double d = living.getBoundingBox().getCenter().distanceToSqr(at);
            if (d < best) {
                best = d;
                struck = living;
            }
        }
        if (struck == null) {
            return;
        }
        Vec3 push = beam.getDeltaMovement().multiply(1.0, 0.0, 1.0);
        if (push.lengthSqr() < 1.0E-6) {
            push = struck.position().subtract(at).multiply(1.0, 0.0, 1.0);
        }
        push = push.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : push.normalize();
        if (!GENTLE.contains(spell)) {
            HITS.put(struck.getId(), new Hit(minecraft.level.getGameTime(), (float) push.x, (float) push.z));
        }

        // The flash: the struck body lit up in the spell's colour for a moment.
        float[] c = SpellFx.lightColor(spell);
        AABB box = struck.getBoundingBox();
        RandomSource random = minecraft.level.random;
        Vec3 centre = box.getCenter();
        float size = (float) Math.max(box.getXsize(), box.getYsize()) * 0.9F;
        SpellGlowParticle.spawn(centre.x, centre.y, centre.z, 0, 0, 0, c[0], c[1], c[2], 0.75F, size, size * 0.6F,
                6, 0.0F, 1.0F, false);
        for (int i = 0; i < 12; i++) {
            double x = box.minX + random.nextDouble() * box.getXsize();
            double y = box.minY + random.nextDouble() * box.getYsize();
            double z = box.minZ + random.nextDouble() * box.getZsize();
            SpellGlowParticle.spawn(x, y, z, push.x * 0.05, 0.02, push.z * 0.05, c[0], c[1], c[2], 0.9F,
                    0.22F, 0.02F, 6 + random.nextInt(5), 0.0F, 0.9F, false);
        }
    }

    /** The reel: the body tips away from the hit about its feet, fast, and rocks back up. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Hit hit = HITS.get(entity.getId());
        if (hit == null || entity.level() != Minecraft.getInstance().level) {
            return;
        }
        float t = entity.level().getGameTime() - hit.start + event.getPartialTick();
        if (t < 0.0F || t > HIT_TICKS) {
            return;
        }
        float env = t < 1.2F ? t / 1.2F : (float) Math.exp(-(t - 1.2F) / 2.2F);
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        PUSHED.add(entity.getId());
        pose.translate(hit.dx * 0.12F * env, 0.0F, hit.dz * 0.12F * env);
        // Tip about the horizontal axis across the push: up x push.
        org.joml.Vector3f axis = new org.joml.Vector3f(hit.dz, 0.0F, -hit.dx);
        if (axis.lengthSquared() > 1.0E-6F) {
            pose.mulPose(new org.joml.Quaternionf().rotationAxis(14.0F * env * Mth.DEG_TO_RAD, axis.normalize()));
        }
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (PUSHED.remove(event.getEntity().getId())) {
            event.getPoseStack().popPose();
        }
    }
}
