package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small decorative goblet that sits on the Basin of Despair's island. */
public class CrystalGobletBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5, 0, 5, 11, 1, 11),
            Block.box(7.5, 1, 7.5, 8.5, 5, 8.5),
            Block.box(5, 5, 5, 11, 10, 11));

    public CrystalGobletBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Lifting the goblet: the block becomes the item. A left-click drops it too (loot table), so it is never lost. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            ItemStack goblet = new ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get());
            level.removeBlock(pos, false);
            player.getInventory().placeItemBackInInventory(goblet);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
