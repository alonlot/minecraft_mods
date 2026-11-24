package com.hogwartsmagic.block;

import com.hogwartsmagic.block.entity.HiddenHouseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A stone of a hidden wizard house ruin. It looks like mossy cobblestone, cobblestone or stone
 * bricks and is solid, but it was never there: when Revelio unmasks the house it simply vanishes,
 * and mined or blown up it leaves nothing. One block with a {@link #LOOK} property rather than
 * three blocks, so the generator only switches a string.
 */
public class PhantomRuinBlock extends Block implements EntityBlock {
    public static final EnumProperty<PhantomLook> LOOK = EnumProperty.create("look", PhantomLook.class);

    public PhantomRuinBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LOOK, PhantomLook.MOSSY_COBBLESTONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LOOK);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HiddenHouseBlockEntity(pos, state);
    }
}
