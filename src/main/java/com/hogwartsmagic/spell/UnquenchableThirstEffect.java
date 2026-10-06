package com.hogwartsmagic.spell;

import com.hogwartsmagic.locket.DespairRules;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class UnquenchableThirstEffect extends MobEffect {
    public UnquenchableThirstEffect() { super(MobEffectCategory.HARMFUL, 0x3E6B48); }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % DespairRules.THIRST_DAMAGE_INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().dryOut(), 1.0F);
        return true;
    }
}
