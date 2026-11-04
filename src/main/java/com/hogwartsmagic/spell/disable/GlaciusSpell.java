package com.hogwartsmagic.spell.disable;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.TerrainRules;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.combat.CrowdControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Glacius sets up combos. The first hit slows (Slowness II) and chills: a hidden glacius_chill marker
 * that opens a short window. A hit inside that window freezes the target solid: a stun through
 * CrowdControl, brittle (the payoff for Confringo and melee) and the glacius_frozen marker. Surface
 * water under the target turns to frosted ice. Fire thaws a frozen target at once. At Master a frozen
 * target that dies shatters and slows everything nearby except the caster.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GlaciusSpell {
    public static final String FROZEN_BY_TAG = "hogwartsmagic:glacius_frozen_by";

    private GlaciusSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.GLACIUS);
        Entity responsible = CombatEffects.responsible(caster);
        frostWaterUnderfoot(level, target, caster);

        Holder<MobEffect> chill = DisableSupport.holder(ExampleMod.GLACIUS_CHILL);
        Holder<MobEffect> frozen = DisableSupport.holder(ExampleMod.GLACIUS_FROZEN);
        boolean primed = target.hasEffect(chill) || target.hasEffect(frozen);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                GlaciusRules.slowTicks(rank, isCold(target)), GlaciusRules.SLOW_AMPLIFIER), responsible);
        if (!primed) {
            DisableSupport.addHiddenMarker(target, ExampleMod.GLACIUS_CHILL, GlaciusRules.chillWindowTicks(rank), rank - 1, caster);
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), GlaciusRules.CHILL_FROST_TICKS));
            return;
        }
        if (freezeSolid(level, target, caster, rank)) {
            target.removeEffect(chill); // keep the chill window if nothing at all landed
        }
    }

    /**
     * @return true if any freeze effect landed (the stun and/or brittle). A longer stun already running
     * makes CrowdControl.apply return 0, but brittle is not crowd control, so it and the frozen marker
     * still apply; the marker is then flagged ambient so a fire thaw leaves the other stun alone.
     */
    private static boolean freezeSolid(ServerLevel level, LivingEntity target, @Nullable Entity caster, int rank) {
        int freezeTicks = GlaciusRules.freezeTicks(rank);
        int stunTicks = CrowdControl.apply(target, DisableSupport.holder(ExampleMod.STUNNED), freezeTicks, caster);
        boolean stunned = stunTicks > 0;
        int ticks = stunned ? stunTicks : freezeTicks;
        boolean brittle = CombatEffects.applyBrittle(target, GlaciusRules.brittleTicks(ticks), GlaciusRules.brittleAmplifier(rank), caster);
        if (!stunned && !brittle) {
            return false;
        }
        if (stunned) {
            DisableSupport.interrupt(target);
        }
        // MobEffectInstance.update copies the incoming ambient flag, so a recast that lands no stun must
        // not refresh (and so disown) a marker that already owns its stun; it may only add an ambient one.
        Holder<MobEffect> frozenHolder = DisableSupport.holder(ExampleMod.GLACIUS_FROZEN);
        MobEffectInstance existing = target.getEffect(frozenHolder);
        if (stunned || existing == null || existing.isAmbient()) {
            boolean ambient = !stunned && (existing == null || existing.isAmbient());
            target.addEffect(new MobEffectInstance(frozenHolder, ticks, Math.max(0, rank - 1),
                    ambient, false, false), CombatEffects.responsible(caster));
            // The frozen-by tag moves only with the marker. Remaining edge: if addEffect runs but vanilla
            // update keeps the old instance (shorter, weaker recast), the tag can still move.
            Entity responsible = CombatEffects.responsible(caster);
            if (responsible != null) {
                target.getPersistentData().putUUID(FROZEN_BY_TAG, responsible.getUUID());
            } else {
                target.getPersistentData().remove(FROZEN_BY_TAG);
            }
        }
        // Fully frozen for the whole freeze (ticksFrozen decays by 2 a tick): vanilla shakes the body
        // and draws the frost overlay for a player.
        if (stunned) {
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 2 * ticks));
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                30, target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.02);
        level.sendParticles(ParticleTypes.ITEM_SNOWBALL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                16, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.05);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_PLACE, SoundSource.NEUTRAL, 1.0f, 1.6f);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.NEUTRAL, 0.8f, 1.0f);
        return true;
    }

    /**
     * Surface water under (or, if it is swimming, around) the target turns to frosted ice, which melts on
     * its own. Blocks the target's own body is in are skipped, so nobody is sealed inside the ice.
     */
    private static void frostWaterUnderfoot(ServerLevel level, LivingEntity target, @Nullable Entity caster) {
        BlockPos feet = target.blockPosition();
        BlockPos center;
        if (level.getFluidState(feet).isEmpty()) {
            center = feet.below();
        } else {
            center = feet;
            for (int i = 0; i < 2 && !level.getFluidState(center.above()).isEmpty(); i++) {
                center = center.above();
            }
        }
        AABB body = target.getBoundingBox();
        BlockState ice = Blocks.FROSTED_ICE.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                BlockState state = level.getBlockState(pos);
                if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()
                        || !level.getBlockState(pos.above()).isAir() || new AABB(pos).intersects(body)) {
                    continue;
                }
                if (!TerrainRules.canPlace(level, pos, caster)) {
                    continue;
                }
                level.setBlockAndUpdate(pos, ice);
                level.scheduleTick(pos, Blocks.FROSTED_ICE, Mth.nextInt(level.getRandom(), 60, 120));
            }
        }
    }

    /** Snowy and frozen biomes (base temperature under 0.15) make the slow last longer. */
    private static boolean isCold(LivingEntity target) {
        return target.level().getBiome(target.blockPosition()).value().getBaseTemperature() < 0.15f;
    }

    /** Fire thaws a frozen target at once; the frozen-by tag is cleared once the freeze is over. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        Holder<MobEffect> frozen = DisableSupport.holder(ExampleMod.GLACIUS_FROZEN);
        MobEffectInstance marker = target.getEffect(frozen);
        if (marker == null) {
            // Never touch getPersistentData() here: it lazily creates an empty tag on every mob. The
            // frozen-by tag is cleared when the marker expires or is removed (handlers below).
            return;
        }
        if (!target.isOnFire()) {
            return;
        }
        int remaining = marker.getDuration();
        target.removeEffect(frozen);
        target.getPersistentData().remove(FROZEN_BY_TAG);
        // Only take what Glacius applied: a longer stun or brittle from another source stays.
        Holder<MobEffect> stunned = DisableSupport.holder(ExampleMod.STUNNED);
        MobEffectInstance stun = target.getEffect(stunned);
        // An ambient marker means no Glacius stun landed (someone else's was running): never take that one.
        if (!marker.isAmbient() && stun != null && stun.getDuration() <= remaining + 1) {
            target.removeEffect(stunned);
        }
        Holder<MobEffect> brittle = DisableSupport.holder(ExampleMod.BRITTLE);
        MobEffectInstance brit = target.getEffect(brittle);
        if (brit != null && brit.getDuration() <= remaining + GlaciusRules.BRITTLE_LINGER_TICKS + 1
                && brit.getDuration() > GlaciusRules.BRITTLE_LINGER_TICKS) {
            // Glacius owns it: keep the brittle linger past the thaw, no more.
            target.removeEffect(brittle);
            target.addEffect(new MobEffectInstance(brittle, GlaciusRules.BRITTLE_LINGER_TICKS, brit.getAmplifier(),
                    brit.isAmbient(), brit.isVisible(), brit.showIcon()));
        }
        target.setTicksFrozen(0);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 1.0, target.getZ(), 16, 0.35, 0.3, 0.35, 0.01);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    /** The frozen marker ran out: the frozen-by tag has done its job. */
    @SubscribeEvent
    public static void onFrozenExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.getEffect().value() == ExampleMod.GLACIUS_FROZEN.get()
                && !event.getEntity().level().isClientSide()) {
            event.getEntity().getPersistentData().remove(FROZEN_BY_TAG);
        }
    }

    /** The frozen marker was removed (thaw, milk, /effect clear): same cleanup. */
    @SubscribeEvent
    public static void onFrozenRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect() == ExampleMod.GLACIUS_FROZEN.get() && !event.getEntity().level().isClientSide()) {
            event.getEntity().getPersistentData().remove(FROZEN_BY_TAG);
        }
    }

    /** Master: a target that dies while frozen solid shatters into shards that slow everything nearby. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        MobEffectInstance frozen = dead.getEffect(DisableSupport.holder(ExampleMod.GLACIUS_FROZEN));
        if (frozen == null || !GlaciusRules.shatters(frozen.getAmplifier() + 1)) {
            return;
        }
        CompoundTag data = dead.getPersistentData();
        UUID frozenBy = data.hasUUID(FROZEN_BY_TAG) ? data.getUUID(FROZEN_BY_TAG) : null;
        double radius = GlaciusRules.SHATTER_RADIUS;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(radius),
                e -> e != dead && e.isAlive() && e.distanceToSqr(dead) <= radius * radius
                        && (frozenBy == null || !frozenBy.equals(e.getUUID())))) {
            nearby.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,
                    GlaciusRules.SHATTER_SLOW_TICKS, GlaciusRules.SHATTER_SLOW_AMPLIFIER));
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState()),
                dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(), 60,
                dead.getBbWidth() * 0.6, dead.getBbHeight() * 0.4, dead.getBbWidth() * 0.6, 0.25);
        level.sendParticles(ParticleTypes.SNOWFLAKE, dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(),
                40, radius * 0.4, 0.4, radius * 0.4, 0.05);
        level.playSound(null, dead.getX(), dead.getY(), dead.getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2f, 0.7f);
        data.remove(FROZEN_BY_TAG);
    }
}
