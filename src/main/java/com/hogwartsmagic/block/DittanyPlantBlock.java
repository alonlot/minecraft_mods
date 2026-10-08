package com.hogwartsmagic.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dittany, the woolly mountain herb of Essence of Dittany. It grows on earth and on bare rock (the Horntail Peaks
 * are blackstone, tuff and basalt). The dittany item plants it again (wild=false); a patch places wild=true. Breaking
 * it gives 1 dittany, plus 1 more only when wild (loot table), so a planted dittany never gives back more than it cost.
 */
public class DittanyPlantBlock extends BushBlock {
    public static final MapCodec<DittanyPlantBlock> CODEC = simpleCodec(DittanyPlantBlock::new);
    /** Grown by a worldgen patch (true) or planted from the dittany item (false, the default). */
    public static final BooleanProperty WILD = BooleanProperty.create("wild");
    private static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D);

    public DittanyPlantBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(WILD, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WILD);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    protected boolean mayPlaceOn(BlockState floor, BlockGetter level, BlockPos pos) {
        return floor.is(BlockTags.DIRT) || floor.is(BlockTags.BASE_STONE_OVERWORLD) || floor.is(BlockTags.BASE_STONE_NETHER);
    }
}
