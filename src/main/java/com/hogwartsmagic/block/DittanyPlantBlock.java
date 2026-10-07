package com.hogwartsmagic.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dittany, the woolly mountain herb of Essence of Dittany. It grows on earth and on bare rock (the Horntail Peaks
 * are blackstone, tuff and basalt). Breaking it gives 1 or 2 dittany (loot table); the dittany item plants it again.
 */
public class DittanyPlantBlock extends BushBlock {
    public static final MapCodec<DittanyPlantBlock> CODEC = simpleCodec(DittanyPlantBlock::new);
    private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D);

    public DittanyPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter level, BlockPos pos) {
        return floor.is(BlockTags.DIRT) || floor.is(BlockTags.BASE_STONE_OVERWORLD) || floor.is(BlockTags.BASE_STONE_NETHER);
    }
}
