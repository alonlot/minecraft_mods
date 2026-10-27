package com.hogwartsmagic.mixin;

import com.hogwartsmagic.pensieve.capture.CaptureHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds the Pensieve recorder every real block change. Hot path: the hook is a type check and a hash lookup.
 * Common mixin (registered under "mixins"); the hook ignores client levels.
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkCaptureMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void hogwartsmagic$captureBlockChange(BlockPos pos, BlockState state, boolean moved,
                                                  CallbackInfoReturnable<BlockState> cir) {
        BlockState old = cir.getReturnValue();
        if (old == null || old == state) {
            return;
        }
        CaptureHooks.onBlockChanged(((LevelChunk) (Object) this).getLevel(), pos, old, state);
    }
}
