package com.hogwartsmagic.block.decor;

import com.hogwartsmagic.cosy.CandleBob;
import com.hogwartsmagic.cosy.FloatingCandleBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A candle that floats in mid-air, as in the Great Hall. It needs no support, has no collision and
 * gives full light. Three candles of different heights hover in one cell, and bob there: the client draws
 * them from the block entity (see FloatingCandleRenderer), and the wick particles follow. An empty-handed
 * click snuffs them or lights them again. Candles placed before the block entity existed are lit, and get
 * their entity when a player first sees the chunk (see CosyHomeEvents).
 */
public class FloatingCandleBlock extends Block implements EntityBlock {
    public static final MapCodec<FloatingCandleBlock> CODEC = simpleCodec(FloatingCandleBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(3.0, 1.0, 3.0, 13.0, 14.0, 13.0);

    /** Wick tips in pixels, matching the model: {x, y, z}. */
    private static final double[][] WICKS = {{5.0, 14.0, 6.0}, {10.0, 12.0, 5.0}, {8.0, 11.0, 11.0}};
    /** Where each candle turns when it sways, in pixels: the middle of its body. */
    public static final double[][] PIVOTS = {{5.0, 8.5, 6.0}, {10.0, 6.5, 5.0}, {8.0, 5.5, 11.0}};

    public FloatingCandleBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion().noCollission().lightLevel(state -> state.getValue(LIT) ? 14 : 0));
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    /** The bobbing candles are drawn by the block entity renderer, not baked into the chunk. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloatingCandleBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // A held block still places against the candles; only an empty hand snuffs or lights them.
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel serverLevel) {
            boolean nowLit = !state.getValue(LIT);
            level.setBlock(pos, state.setValue(LIT, nowLit), Block.UPDATE_ALL);
            level.playSound(null, pos, nowLit ? SoundEvents.FIRECHARGE_USE : SoundEvents.CANDLE_EXTINGUISH,
                    SoundSource.BLOCKS, nowLit ? 0.4f : 1.0f, nowLit ? 1.4f : 1.0f);
            if (!nowLit) {
                for (double[] w : WICKS) {
                    serverLevel.sendParticles(ParticleTypes.SMOKE, pos.getX() + w[0] / 16.0, pos.getY() + w[1] / 16.0 + 0.05,
                            pos.getZ() + w[2] / 16.0, 2, 0.02, 0.03, 0.02, 0.01);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        long time = level.getGameTime();
        long seed = pos.asLong();
        for (int i = 0; i < WICKS.length; i++) {
            double[] w = WICKS[i];
            double lever = (w[1] - PIVOTS[i][1]) / 16.0;
            double x = pos.getX() + w[0] / 16.0 + CandleBob.swayShift(CandleBob.swayDeg(time, 0f, i, seed), lever);
            double y = pos.getY() + w[1] / 16.0 + 0.03 + CandleBob.offsetY(time, 0f, i, seed);
            double z = pos.getZ() + w[2] / 16.0;
            // animateTick reaches a given block only now and then, so every call lights every wick,
            // the same as vanilla candles.
            level.addParticle(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.SMALL_FLAME), x, y, z, 0.0, 0.0, 0.0);
            if (random.nextFloat() < 0.15f) {
                level.addParticle(com.hogwartsmagic.spell.vfx.SpellVfx.swap(ParticleTypes.SMOKE), x, y + 0.05, z, 0.0, 0.0, 0.0);
            }
        }
    }
}
