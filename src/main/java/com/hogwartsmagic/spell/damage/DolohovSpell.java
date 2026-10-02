package com.hogwartsmagic.spell.damage;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Dolohov's Curse: a lasting wound. Phase 2's cursed_wound lowers max health (2/3/4/5 hearts for
 * 30/45/60/90s by rank) and milk can't lift it. At Master the victim carries a mark; if it dies while
 * still cursed, the curse leaps to the caster's enemies within 3 blocks for half the hearts (rounded
 * up) and half the time. Spread curses carry no mark, so they never chain; the mark is also dropped
 * whenever the wound is removed or expires early, so a stale mark cannot outlive the curse.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DolohovSpell {
    public static final String MASTER_MARK_TAG = "hogwartsmagic:dolohov_master";

    private DolohovSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (!(target.level() instanceof ServerLevel level)) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.DOLOHOV_CURSE);
        target.hurt(DamageSupport.magicFrom(target, caster), DamageSupport.scaled(caster, DolohovRules.DAMAGE));
        if (!target.isAlive()) {
            return;
        }
        int ticks = DolohovRules.ticks(rank);
        boolean landed = CombatEffects.applyCursedWound(target, ticks, DolohovRules.hearts(rank), caster);
        CompoundTag data = target.getPersistentData();
        if (landed && DolohovRules.spreads(rank)) {
            CompoundTag mark = new CompoundTag();
            mark.putLong("Until", level.getGameTime() + ticks);
            Entity responsible = CombatEffects.responsible(caster);
            if (responsible != null) {
                mark.putUUID("By", responsible.getUUID());
            }
            data.put(MASTER_MARK_TAG, mark);
        } else if (landed) {
            data.remove(MASTER_MARK_TAG);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWoundRemoved(MobEffectEvent.Remove event) {
        if (event.isCanceled()) {
            return;
        }
        if (event.getEffect() == ExampleMod.CURSED_WOUND.get() && !event.getEntity().level().isClientSide()) {
            event.getEntity().getPersistentData().remove(MASTER_MARK_TAG);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWoundExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && instance.getEffect().value() == ExampleMod.CURSED_WOUND.get()
                && !event.getEntity().level().isClientSide()) {
            event.getEntity().getPersistentData().remove(MASTER_MARK_TAG);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        // Gate on the effect first so ordinary deaths never touch persistent data.
        MobEffectInstance wound = dead.getEffect(DisableSupport.holder(ExampleMod.CURSED_WOUND));
        if (wound == null) {
            return;
        }
        CompoundTag data = dead.getPersistentData();
        if (!data.contains(MASTER_MARK_TAG)) {
            return;
        }
        CompoundTag mark = data.getCompound(MASTER_MARK_TAG);
        data.remove(MASTER_MARK_TAG);
        if (mark.getLong("Until") < level.getGameTime()) {
            return;
        }
        Entity caster = mark.hasUUID("By") ? level.getEntity(mark.getUUID("By")) : null;
        int hearts = DolohovRules.spreadHearts(DolohovRules.hearts(4));
        int ticks = DolohovRules.spreadTicks(DolohovRules.ticks(4));
        double radius = DolohovRules.SPREAD_RADIUS;
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, dead.getBoundingBox().inflate(radius),
                e -> e.distanceToSqr(dead) <= radius * radius && DisableSupport.isEnemyOf(caster, e, dead))) {
            CombatEffects.applyCursedWound(nearby, ticks, hearts, caster);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, nearby.getX(), nearby.getY() + nearby.getBbHeight() * 0.5,
                    nearby.getZ(), 12, nearby.getBbWidth() * 0.4, nearby.getBbHeight() * 0.3, nearby.getBbWidth() * 0.4, 0.01);
        }
        level.sendParticles(ParticleTypes.WITCH, dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ(),
                40, radius * 0.4, 0.5, radius * 0.4, 0.02);
        level.playSound(null, dead.getX(), dead.getY(), dead.getZ(), ModSounds.SPELL_DOLOHOV_CURSE.get(),
                SoundSource.PLAYERS, 1.0f, 0.6f);
    }
}
