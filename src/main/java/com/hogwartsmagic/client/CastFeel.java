package com.hogwartsmagic.client;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.entity.SpellBeamEntity;
import com.hogwartsmagic.item.MagicWandItem;
import com.hogwartsmagic.spell.SpellType;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
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
 * How a cast feels, on top of how it looks: the swing's whoosh, the crack as the spell leaves the wand, the
 * hit, a small kick of the view on strong spells, and the target reeling from the hit. All client-side and
 * cosmetic; the spell's own sounds (played by the server) are unchanged and layer on top.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class CastFeel {

    /** Curses get the darker crack. */
    private static final Set<SpellType> DARK = EnumSet.of(SpellType.AVADA_KEDAVRA, SpellType.CRUCIO,
            SpellType.IMPERIO, SpellType.SECTUMSEMPRA, SpellType.DOLOHOV_CURSE, SpellType.MORSMORDRE,
            SpellType.TENEBRIS_FLIGHT, SpellType.SERPENSORTIA);
    /** Spells whose release kicks the caster's view (first person only). */
    private static final Set<SpellType> KICK = EnumSet.of(SpellType.BOMBARDA, SpellType.CONFRINGO,
            SpellType.DEPULSO, SpellType.STUPEFY, SpellType.AVADA_KEDAVRA, SpellType.EXPELLIARMUS,
            SpellType.FIENDFYRE, SpellType.CRUCIO, SpellType.SECTUMSEMPRA, SpellType.DOLOHOV_CURSE,
            SpellType.INCENDIO, SpellType.ARRESTO_MOMENTUM);

    /** Spells that help or move rather than hurt: what they strike lights up but does not reel. */
    private static final Set<SpellType> GENTLE = EnumSet.of(SpellType.PROTEGO, SpellType.PROTEGO_MAXIMA,
            SpellType.HEAL, SpellType.LUMOS, SpellType.REVELIO, SpellType.ALOHOMORA, SpellType.COLLOPORTUS,
            SpellType.REPARO, SpellType.WINGARDIUM_LEVIOSA, SpellType.ACCIO, SpellType.EXPECTO_PATRONUM,
            SpellType.CAVE_INIMICUM, SpellType.ALATUS, SpellType.ASCENDIO, SpellType.PIERTOTUM_LOCOMOTOR,
            SpellType.APPARITION);

    private static final Set<CastGestures.Gesture> HEAVY_SWING = Set.of(CastGestures.SLASH,
            CastGestures.DOUBLE_SLASH, CastGestures.WHIP, CastGestures.CURSE, CastGestures.PUSH);
    private static final Set<CastGestures.Gesture> RISING_SWING = Set.of(CastGestures.RAISE,
            CastGestures.SWISH_FLICK);

    /** Per caster: the start tick of the last cast whose swing sounded, and of the last whose release did. */
    private static final Map<Integer, Integer> SWUNG = new HashMap<>();
    private static final Map<Integer, Integer> RELEASED = new HashMap<>();

    private CastFeel() {
    }

    // ---------------------------------------------------------------- swing and release

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.isPaused()) {
            if (minecraft.level == null) {
                SWUNG.clear();
                RELEASED.clear();
                HITS.clear();
            }
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Entity e : minecraft.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity caster) || !MagicWandItem.isAnyWand(caster.getMainHandItem())) {
                continue;
            }
            seen.add(caster.getId());
            CastGestures.Cast cast = CastGestures.castOf(caster);
            if (cast == null) {
                continue;
            }
            int since = caster.tickCount - cast.start();
            FirstPersonWandRig.Motion motion = FirstPersonWandRig.motionOf(cast.gesture());
            if (since < 0 || since > motion.length()) {
                continue;
            }
            Integer swung = SWUNG.get(caster.getId());
            if (swung == null || swung != cast.start()) {
                SWUNG.put(caster.getId(), cast.start());
                swing(caster, cast);
            }
            Integer released = RELEASED.get(caster.getId());
            if (since >= motion.release() && (released == null || released != cast.start())) {
                RELEASED.put(caster.getId(), cast.start());
                release(minecraft, caster, cast);
            }
        }
        SWUNG.keySet().retainAll(seen);
        RELEASED.keySet().retainAll(seen);
        kickO = kick;
        kick *= 0.55F;
        HITS.values().removeIf(hit -> minecraft.level.getGameTime() - hit.start > HIT_TICKS);
    }

    private static void swing(LivingEntity caster, CastGestures.Cast cast) {
        SoundEvent sound = HEAVY_SWING.contains(cast.gesture()) ? ModSounds.CAST_SWISH_HEAVY.get()
                : RISING_SWING.contains(cast.gesture()) ? ModSounds.CAST_SWISH_RISE.get()
                : ModSounds.CAST_SWISH_LIGHT.get();
        play(caster.position().add(0.0, caster.getEyeHeight() * 0.8, 0.0), sound, 0.5F, caster);
    }

    private static void release(Minecraft minecraft, LivingEntity caster, CastGestures.Cast cast) {
        SpellType spell = cast.spell();
        SoundEvent sound = spell != null && DARK.contains(spell) ? ModSounds.CAST_RELEASE_DARK.get()
                : ModSounds.CAST_RELEASE.get();
        play(caster.position().add(0.0, caster.getEyeHeight() * 0.8, 0.0), sound, 0.65F, caster);
        if (caster == minecraft.player && spell != null && KICK.contains(spell)
                && minecraft.options.getCameraType().isFirstPerson()) {
            boolean big = spell == SpellType.BOMBARDA || spell == SpellType.AVADA_KEDAVRA
                    || spell == SpellType.CONFRINGO || spell == SpellType.FIENDFYRE;
            kick = big ? 1.0F : 0.6F;
            kickSide = cast.variant().mirror() ? -1.0F : 1.0F;
        }
    }

    private static void play(Vec3 at, SoundEvent sound, float volume, Entity source) {
        Minecraft minecraft = Minecraft.getInstance();
        RandomSource random = minecraft.level.random;
        SoundSource category = source instanceof Player ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        minecraft.level.playLocalSound(at.x, at.y, at.z, sound, category, volume,
                0.92F + random.nextFloat() * 0.16F, false);
    }

    // ---------------------------------------------------------------- the view kick

    /** How hard the view is kicked now (1 at the moment of a big release), and last tick's value. */
    private static float kick;
    private static float kickO;
    private static float kickSide = 1.0F;

    /** The spell's force lifts the view a touch and rolls it, then it settles back in a few ticks. */
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float k = Mth.lerp((float) event.getPartialTick(), kickO, kick);
        if (k < 0.01F) {
            return;
        }
        event.setPitch(event.getPitch() - 2.2F * k);
        event.setRoll(event.getRoll() + 1.2F * k * kickSide);
    }

    // ---------------------------------------------------------------- hits

    private static final int HIT_TICKS = 10;

    private record Hit(long start, float dx, float dz) {
    }

    private static final Map<Integer, Hit> HITS = new HashMap<>();
    /** Entities whose pose was pushed in RenderLivingEvent.Pre and must be popped in Post. */
    private static final Set<Integer> PUSHED = new HashSet<>();

    /**
     * A beam hit at {@code at}: the hit sound, and the creature it struck (if any) reels away from it and
     * flashes in the spell's colour.
     */
    static void onImpact(SpellBeamEntity beam, Vec3 at, SpellType spell) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        boolean heavy = SpellFx.HEAVY.contains(spell);
        play(at, heavy ? ModSounds.CAST_IMPACT_HEAVY.get() : ModSounds.CAST_IMPACT.get(), heavy ? 0.9F : 0.7F, beam);

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
        if (spell == SpellType.PROTEGO) {
            ProtegoShieldRenderer.onShielded(struck);
        }
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
