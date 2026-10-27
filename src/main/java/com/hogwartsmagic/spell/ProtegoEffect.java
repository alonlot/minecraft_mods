package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Retired. Protego is mod state now ({@link com.hogwartsmagic.spell.support.ProtegoShield}) drawn as a bubble,
 * not a potion effect. Still registered only so worlds saved while it was in use load without missing-registry
 * warnings; nothing applies it, and a copy left in an old save just runs out.
 */
public class ProtegoEffect extends MobEffect {

    public ProtegoEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x88CCFF);
    }
}
