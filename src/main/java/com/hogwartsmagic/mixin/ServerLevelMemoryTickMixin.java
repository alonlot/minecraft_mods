package com.hogwartsmagic.mixin;

import com.hogwartsmagic.pensieve.PensieveDimension;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A memory shows one moment: crops must not grow, leaves decay, ice melt or fire spread while it replays. The memory
 * level shares the overworld game rules, so {@code randomTickSpeed} cannot be set for it alone; skipping the chunk tick
 * (random block ticks, ice and snow, lightning) there does the same. Scheduled ticks are dropped by the set builder.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMemoryTickMixin {

    @Inject(method = "tickChunk", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$noRandomTicksInMemories(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        if (PensieveDimension.isMemory((ServerLevel) (Object) this)) {
            ci.cancel();
        }
    }
}
