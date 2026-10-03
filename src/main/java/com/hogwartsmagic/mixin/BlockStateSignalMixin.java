package com.hogwartsmagic.mixin;

import com.hogwartsmagic.spell.support.lock.ForcedSignals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A source an Alohomora Master forced on reads as 15 for its few seconds (see ForcedSignals). */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateSignalMixin {
    @Inject(method = "getSignal", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$forcedSignal(BlockGetter getter, BlockPos pos, Direction direction,
                                            CallbackInfoReturnable<Integer> cir) {
        if (ForcedSignals.ACTIVE && getter instanceof ServerLevel level) {
            int forced = ForcedSignals.signal(level, pos, (BlockState) (Object) this, direction, false);
            if (forced > 0) {
                cir.setReturnValue(forced);
            }
        }
    }

    @Inject(method = "getDirectSignal", at = @At("HEAD"), cancellable = true)
    private void hogwartsmagic$forcedDirectSignal(BlockGetter getter, BlockPos pos, Direction direction,
                                                  CallbackInfoReturnable<Integer> cir) {
        if (ForcedSignals.ACTIVE && getter instanceof ServerLevel level) {
            int forced = ForcedSignals.signal(level, pos, (BlockState) (Object) this, direction, true);
            if (forced > 0) {
                cir.setReturnValue(forced);
            }
        }
    }
}
