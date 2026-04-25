package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/** Intermediate mana regeneration potion effect: boosts mana regeneration while active (server player tick). */
public class WizardElixirEffect extends MobEffect {

    public WizardElixirEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x7B68EE);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
