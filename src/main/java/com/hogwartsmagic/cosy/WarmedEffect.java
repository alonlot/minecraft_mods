package com.hogwartsmagic.cosy;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Warmed: the cold cannot take hold (Glacius chill and powder snow both let go, see CosyHomeEvents), and a body that has not
 * been hurt lately slowly mends. Given by the hearth, the Floo fireplace and Butterbeer.
 */
public class WarmedEffect extends MobEffect {
    /** Persistent-data key: the game time of the last damage this entity took. */
    public static final String HURT_KEY = "hogwartsmagic_cosy_last_hurt";

    public WarmedEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xFF9A3C);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            return true;
        }
        long now = entity.level().getGameTime();
        long lastHurt = entity.getPersistentData().contains(HURT_KEY)
                ? entity.getPersistentData().getLong(HURT_KEY) : Long.MIN_VALUE / 2;
        int sinceHurt = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, now - lastHurt));
        if (entity.getHealth() < entity.getMaxHealth()
                && WarmthRules.shouldHeal((int) (now % 80_000L), sinceHurt)) {
            entity.heal(1.0F);
        }
        return true;
    }
}
