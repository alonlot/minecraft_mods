package com.hogwartsmagic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hard stun. The lock itself lives elsewhere: LivingEntityStunMixin makes a stunned entity report
 * isImmobile() (no AI step and no movement input, on the server and on the stunned player's own
 * client), and CombatEffects cancels attacks, item use and casting. This class only draws the
 * circling stars.
 */
public class StunnedEffect extends MobEffect {
    public StunnedEffect() {
        super(MobEffectCategory.HARMFUL, 0xF2D64B);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 4 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            double base = (entity.tickCount % 40) / 40.0 * Math.PI * 2.0;
            double radius = Math.max(0.35, entity.getBbWidth() * 0.6);
            double y = entity.getY() + entity.getBbHeight() + 0.3;
            for (int i = 0; i < 3; i++) {
                double angle = base + i * (Math.PI * 2.0 / 3.0);
                level.sendParticles(ParticleTypes.CRIT, entity.getX() + Math.cos(angle) * radius, y,
                        entity.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        return true;
    }
}
