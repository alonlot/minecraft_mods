package com.hogwartsmagic.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * A client-side entity that is never ticked or synched has to be told it is using an item by hand: the
 * living-entity flag is only set on the server, and the remaining-ticks counter only counts down in tick().
 */
@Mixin(LivingEntity.class)
public interface LivingEntityUseAccessor {
    @Invoker("setLivingEntityFlag")
    void hogwartsmagic$setFlag(int flag, boolean value);

    @Accessor("useItemRemaining")
    void hogwartsmagic$setUseItemRemaining(int ticks);
}
