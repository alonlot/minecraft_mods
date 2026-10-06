package com.hogwartsmagic.block;

import com.hogwartsmagic.block.entity.CaveMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The chain at the cave shore that raises the boat. Invisible and intangible until Revelio shows it;
 * walking into the hidden one only puffs a little smoke so the player can feel something is there.
 * Never collides, so revealing and re-hiding it cannot trap anyone.
 */
public class HiddenChainBlock extends Block implements EntityBlock, LiquidBlockContainer {
    public static final BooleanProperty REVEALED = ConcealedRockBlock.REVEALED;

    private static final int TOUCH_REVEAL_TICKS = 200;

    /** Thin post so a revealed chain can be targeted and right-clicked. */
    private static final VoxelShape SHAPE = Block.box(6.5, 0.0, 6.5, 9.5, 16.0, 9.5);

    public HiddenChainBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(REVEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(REVEALED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(REVEALED) ? SHAPE : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(REVEALED) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(REVEALED)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            // Task 8 raises the cave boat here.
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(REVEALED) || !(entity instanceof Player) || !(level instanceof ServerLevel server)) {
            return;
        }
        server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                3, 0.2, 0.3, 0.2, 0.01);
        // Walking into the chain shows it briefly.
        server.setBlock(pos, state.setValue(REVEALED, true), Block.UPDATE_ALL);
        server.scheduleTick(pos, this, TOUCH_REVEAL_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ConcealedRockBlock.unrevealWhenClear(state, level, pos, this);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CaveMarkerBlockEntity(pos, state);
    }

    /** Water must never spread into (and break) this block while its shape is empty. */
    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos, BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        return false;
    }
}
