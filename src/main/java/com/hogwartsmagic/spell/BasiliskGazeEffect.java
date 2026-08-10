package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Carries how far a basilisk's stare has got, as the effect's amplifier: 0 is a
 * first glance, 4 is the moment before the victim turns to stone.
 *
 * <p>It does nothing on its own. Its whole purpose is to get the charge to the
 * client without a custom packet, where {@code BasiliskGazeOverlay} reads the
 * amplifier and closes the screen down accordingly. The slowdown at high tiers
 * is applied by the basilisk itself, not here, so that the tier stays a pure
 * signal.
 */
public class BasiliskGazeEffect extends MobEffect {

    public BasiliskGazeEffect() {
        super(MobEffectCategory.HARMFUL, 0x6F6A50);
    }
}
