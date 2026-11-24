package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Carries how deep the Drink of Despair has taken hold, as the amplifier (0 after the first cup, 5 after the
 * sixth). It does nothing itself: it exists so the drinking player's client can read the stage and darken the
 * screen ({@code DespairOverlay}), and it is applied hidden (no particles, no icon).
 */
public class DespairEffect extends MobEffect {
    public DespairEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E3A2A);
    }
}
