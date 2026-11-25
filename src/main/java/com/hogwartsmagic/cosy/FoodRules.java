package com.hogwartsmagic.cosy;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/** What chocolate does: it undoes everything a Dementor puts on you, then leaves a little courage behind. */
public final class FoodRules {
    /** How long Chocolate Courage lasts. It must outlast the Dementor's one-second aura pulse. */
    public static final int COURAGE_TICKS = 600;

    private FoodRules() {
    }

    /** Removes Slowness, Blindness and Wither (the whole of a Dementor's touch) and adds Chocolate Courage. */
    public static void applyChocolateCure(LivingEntity e) {
        e.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        e.removeEffect(MobEffects.BLINDNESS);
        e.removeEffect(MobEffects.WITHER);
        CosyFood.CHOCOLATE_COURAGE.getHolder().ifPresent(courage ->
                e.addEffect(new MobEffectInstance(courage, COURAGE_TICKS, 0, false, true, true)));
    }

    /** True while the entity is under Chocolate Courage: a Dementor's aura passes over it. */
    public static boolean hasCourage(LivingEntity e) {
        return CosyFood.CHOCOLATE_COURAGE.getHolder().map(e::hasEffect).orElse(false);
    }
}
