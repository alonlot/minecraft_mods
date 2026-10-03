package com.hogwartsmagic.mixin;

import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes a pressure plate's protected block set type, so Alohomora can play the plate's own click sound. */
@Mixin(BasePressurePlateBlock.class)
public interface BasePressurePlateTypeAccessor {
    @Accessor("type")
    BlockSetType hogwartsmagic$getType();
}
