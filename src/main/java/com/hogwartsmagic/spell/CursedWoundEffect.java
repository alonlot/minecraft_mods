package com.hogwartsmagic.spell;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Lowers max health by one heart per level (vanilla scales the modifier by amplifier + 1). Health
 * above the new max is clamped by vanilla. Milk can't lift it: see CombatEffects.onEffectRemove.
 */
public class CursedWoundEffect extends MobEffect {
    public static final double HEALTH_PER_LEVEL = -2.0;

    public CursedWoundEffect() {
        super(MobEffectCategory.HARMFUL, 0x3B0A45);
        this.addAttributeModifier(
                Attributes.MAX_HEALTH,
                ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "cursed_wound/max_health"),
                HEALTH_PER_LEVEL,
                AttributeModifier.Operation.ADD_VALUE);
    }
}
