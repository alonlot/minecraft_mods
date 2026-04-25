package com.hogwartsmagic.spell;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class BasiliskPetrificationEffect extends MobEffect {

    public BasiliskPetrificationEffect() {
        super(MobEffectCategory.HARMFUL, 0x7F8B8C);
        this.addAttributeModifier(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "basilisk_petrification/movement_speed"),
                -0.95D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
        this.addAttributeModifier(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "basilisk_petrification/attack_speed"),
                -0.75D,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        return true;
    }
}
