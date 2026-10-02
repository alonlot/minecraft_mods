package com.hogwartsmagic.spell.support;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.spell.SpellType;
import com.hogwartsmagic.spell.combat.CombatEffects;
import com.hogwartsmagic.spell.disable.DisableSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * Heal (Episkey, Vulnera Sanentur at Master). The only cleanse for Sectumsempra's bleeding (Adept and
 * up) and Dolohov's cursed wound (Master). Cleanses run before the heal so bleeding's heal halving
 * and the wound's lowered max health don't eat it. Common code; callers are server side.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HealSpell {
    /** Set only inside {@link #cureBleeding}, the one removal path the drink guard lets through. */
    private static boolean bleedingCureInProgress;

    private HealSpell() {
    }

    public static void apply(LivingEntity target, @Nullable Entity caster) {
        if (target.level().isClientSide() || !target.isAlive()) {
            return;
        }
        int rank = DisableSupport.casterRank(caster, SpellType.HEAL);
        if (HealRules.curesBleeding(rank)) {
            cureBleeding(target);
        }
        if (HealRules.curesCursedWound(rank)) {
            CombatEffects.cureCursedWound(target);
        }
        target.heal(HealRules.healAmount(rank));
        int regen = HealRules.regenTicks(rank);
        if (regen > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, regen, 0));
        }
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + target.getBbHeight() * 0.7,
                    target.getZ(), 4 + rank * 2, 0.4, 0.3, 0.4, 0.0);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f + rank * 0.1f);
        }
    }

    public static boolean cureBleeding(LivingEntity target) {
        Holder<MobEffect> holder = ExampleMod.BLEEDING.getHolder().orElse(null);
        if (holder == null || target.level().isClientSide()) {
            return false;
        }
        bleedingCureInProgress = true;
        try {
            return target.removeEffect(holder);
        } finally {
            bleedingCureInProgress = false;
        }
    }

    /**
     * Milk (any drink) calls removeAllEffects while the bucket is the use item: refuse bleeding then,
     * exactly as Phase 2 does for cursed_wound. HIGH priority so Phase 2's bleed-source cleanup (normal
     * priority) never sees the cancelled removal. /effect clear and expiry are not item use.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBleedingRemove(MobEffectEvent.Remove event) {
        if (bleedingCureInProgress || event.getEffect() != ExampleMod.BLEEDING.get()) {
            return;
        }
        if (event.getEntity().isUsingItem()) {
            event.setCanceled(true);
        }
    }
}
