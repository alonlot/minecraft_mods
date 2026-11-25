package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

/** Hidden marker: while it lasts, the drinker client crouches and holds still (see DespairKneelClient). */
public class DespairKneelEffect extends MobEffect {
    public DespairKneelEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E3A2A);
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(DespairEffect.hidden());
    }
}
