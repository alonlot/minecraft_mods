package com.hogwartsmagic.block;

import com.hogwartsmagic.block.entity.HorcruxBlockEntity;
import com.hogwartsmagic.hideout.CursedCup;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The cursed golden cup of a dark wizard hideout: a small two-handled golden cup on the altar,
 * bleeding green motes. Only breaking it with a pickaxe or burning it with Fiendfyre ends it, and
 * either way a shriek tears out of it, a dark soul flees up through the ceiling and a chest of the
 * hideout's treasure appears where the cup stood.
 *
 * <p>It is a Horcrux in every way the game checks (the same block entity, the same Fiendfyre and
 * break paths); it just has no owner and drops no item.
 */
public class CursedCupBlock extends HorcruxBlock {
    public static final MapCodec<CursedCupBlock> CODEC = simpleCodec(CursedCupBlock::new);
    /** Matches the model: foot, stem, bowl and the two handles all sit inside this box. */
    private static final VoxelShape SHAPE = Block.box(4.0, 0.0, 4.0, 12.0, 10.0, 12.0);

    public CursedCupBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CursedCupBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("message.hogwartsmagic.cursed_cup.status"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Release BEFORE super: the Horcrux code clears the bound flags, and an unbound cup frees nothing.
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof HorcruxBlockEntity horcrux
                && (horcrux.isActive() || horcrux.isPrimed())) {
            CursedCup.release(serverLevel, pos.immutable());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
