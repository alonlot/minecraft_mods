package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.CaveRingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The iron ring set in the Locket cave's shore floor. Always visible. Right-clicking it shows the chain for ten
 * seconds; right-clicking while the chain shows hauls it in and raises the skiff. The chain itself is drawn by
 * the ring's block entity renderer.
 */
public class CaveRingBlock extends Block implements EntityBlock {
    public CaveRingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CaveRingBlockEntity ring && player instanceof ServerPlayer sp) {
            if (ring.revealed()) {
                ring.pull(sp);
            } else {
                ring.reveal(CaveRingBlockEntity.TOUCH_REVEAL_TICKS);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CaveRingBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ExampleMod.CAVE_RING_BLOCK_ENTITY.get()) {
            return null;
        }
        return (l, p, s, be) -> ((CaveRingBlockEntity) be).serverTick();
    }
}
