package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

public class FineWoodLogBlock extends RotatedPillarBlock {
    public FineWoodLogBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction toolAction, boolean simulate) {
        if (toolAction == ToolActions.AXE_STRIP && context.getItemInHand().canPerformAction(toolAction)) {
            return ExampleMod.STRIPPED_FINE_WOOD_LOG.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
        }
        return super.getToolModifiedState(state, context, toolAction, simulate);
    }
}
