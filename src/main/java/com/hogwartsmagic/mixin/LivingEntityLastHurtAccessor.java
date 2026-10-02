package com.hogwartsmagic.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the protected lastHurt field so a bleed tick can leave the victim i-frame bookkeeping untouched. */
@Mixin(LivingEntity.class)
public interface LivingEntityLastHurtAccessor {
    @Accessor("lastHurt")
    float hogwartsmagic$getLastHurt();

    @Accessor("lastHurt")
    void hogwartsmagic$setLastHurt(float value);
}
