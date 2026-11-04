package com.hogwartsmagic.spell.disable;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

/**
 * A marker effect that is always applied hidden (no particles, no icon) and has no texture. Vanilla's
 * inventory screen lists every active effect whatever its icon flag, so without this a hanging, petrified,
 * chained or frozen player would see a missing-texture square in their inventory. Forge only calls
 * {@link #initializeClient} on the client, so this is safe on a dedicated server.
 */
public class HiddenMarkerEffect extends MobEffect {
    public HiddenMarkerEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            @Override
            public boolean isVisibleInInventory(MobEffectInstance instance) {
                return false;
            }

            @Override
            public boolean isVisibleInGui(MobEffectInstance instance) {
                return false;
            }
        });
    }
}
