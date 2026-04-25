package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class GillyweedEffect extends MobEffect {

    public GillyweedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x6E9A7B);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
