package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class PolyjuiceEffect extends MobEffect {

    public PolyjuiceEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x6BB06B);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        return true;
    }
}
