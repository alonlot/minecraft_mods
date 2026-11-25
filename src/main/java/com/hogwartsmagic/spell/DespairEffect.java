package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;

import java.util.function.Consumer;

/**
 * Carries how deep the Drink of Despair has taken hold, as the amplifier (0 after the first cup, 5 after the
 * sixth). It does nothing itself: it exists so the drinking player's client can read the stage and darken the
 * screen ({@code DespairOverlay}), and it is applied hidden (no particles, no icon, not listed in the inventory).
 */
public class DespairEffect extends MobEffect {
    /** Shared by the despair effects: never shown in the inventory or on the HUD. */
    static IClientMobEffectExtensions hidden() {
        return new IClientMobEffectExtensions() {
            @Override public boolean isVisibleInInventory(MobEffectInstance instance) { return false; }
            @Override public boolean isVisibleInGui(MobEffectInstance instance) { return false; }
        };
    }

    public DespairEffect() {
        super(MobEffectCategory.HARMFUL, 0x1E3A2A);
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(hidden());
    }
}
