package com.hogwartsmagic.mixin;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the data items of an entity so the Pensieve recorder can read the ones flagged dirty, including a value
 * that went back to its default (which {@code getNonDefaultValues} leaves out). Common mixin: the recorder is server code.
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {
    @Accessor("itemsById")
    SynchedEntityData.DataItem<?>[] hogwartsmagic$items();
}
