package com.hogwartsmagic.spell;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class ProtegoEffect extends MobEffect {

    public ProtegoEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x88CCFF);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Tick every frame to spawn glowing shield particles
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        Level level = entity.level();
        if (level.isClientSide) {
            boolean maximaBarrier = amplifier >= 3;
            double radius = maximaBarrier ? 4.5D : entity.getBbWidth() * 0.8D;
            double height = maximaBarrier ? entity.getBbHeight() * 0.55D : entity.getBbHeight() * 0.5D;
            int particles = maximaBarrier ? 7 : 2;
            
            for (int i = 0; i < particles; i++) {
                double theta = level.random.nextDouble() * 2 * Math.PI;
                double phi = Math.acos(2 * level.random.nextDouble() - 1);
                
                double x = radius * Math.sin(phi) * Math.cos(theta);
                double y = radius * Math.sin(phi) * Math.sin(theta) + height;
                double z = radius * Math.cos(phi);
                
                Vec3 pos = entity.position().add(x, y, z);
                
                level.addParticle(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 0, 0, 0);
                if (maximaBarrier && i % 2 == 0) {
                    level.addParticle(ParticleTypes.GLOW, pos.x, pos.y, pos.z, 0, 0, 0);
                }
            }
        }
        return true;
    }
}
