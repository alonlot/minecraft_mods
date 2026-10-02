package com.hogwartsmagic.spell;

import com.hogwartsmagic.spell.combat.BleedRules;
import com.hogwartsmagic.spell.combat.CombatDamage;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;

/**
 * One damage per second per stack (amplifier + 1, at most 3). The no-regeneration and half-healing
 * parts are in FoodDataBleedingMixin, PlayerBleedingMixin and CombatEffects.onLivingHeal.
 */
public class BleedingEffect extends MobEffect {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.0f);

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0303);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return BleedRules.isDamageTick(duration);
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            entity.hurt(CombatDamage.bleeding(level), BleedRules.damagePerSecond(amplifier));
            level.sendParticles(BLOOD, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    6 + 3 * BleedRules.stacks(amplifier), entity.getBbWidth() * 0.3, entity.getBbHeight() * 0.25,
                    entity.getBbWidth() * 0.3, 0.0);
        }
        return true;
    }
}
