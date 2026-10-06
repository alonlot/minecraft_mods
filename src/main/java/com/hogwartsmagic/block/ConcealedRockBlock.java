package com.hogwartsmagic.block;

import com.hogwartsmagic.block.entity.CaveMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cave wall that looks like stone until Revelio is cast nearby. While {@link #REVEALED} it has no
 * shape, no model and no occlusion, so the passage behind it can be walked through and seen; a
 * scheduled tick (see {@code LocketCaves.reveal}) closes it again once nothing stands inside it.
 */
public class ConcealedRockBlock extends Block implements EntityBlock, LiquidBlockContainer {
    /** Shared by every block {@code LocketCaves.reveal} can open. */
    public static final BooleanProperty REVEALED = BooleanProperty.create("revealed");

    private static final int RETRY_TICKS = 40;

    public ConcealedRockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(REVEALED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(REVEALED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(REVEALED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(REVEALED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(REVEALED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(REVEALED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(REVEALED) || random.nextInt(3) != 0) {
            return;
        }
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() + random.nextDouble();
        double z = pos.getZ() + random.nextDouble();
        level.addParticle(ParticleTypes.ENCHANT, x, y, z,
                (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
        level.addParticle(ParticleTypes.GLOW, x, y, z, 0.0, 0.0, 0.0);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        unrevealWhenClear(state, level, pos, this);
    }

    /** Closes a revealed block unless an entity is inside it, in which case it looks again in two seconds. */
    static void unrevealWhenClear(BlockState state, ServerLevel level, BlockPos pos, Block block) {
        if (!state.getValue(REVEALED)) {
            return;
        }
        if (level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> !e.isSpectator()).isEmpty()) {
            level.setBlock(pos, state.setValue(REVEALED, false), Block.UPDATE_ALL);
        } else {
            level.scheduleTick(pos, block, RETRY_TICKS);
        }
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
