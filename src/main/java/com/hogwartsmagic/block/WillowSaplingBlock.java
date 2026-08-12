package com.hogwartsmagic.block;

import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.WhompingWillowEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.UUID;

/**
 * A Whomping Willow sapling. Plant it and, in time, it grows into a willow that is <i>yours</i>.
 *
 * <p>The ownership is the whole point. A grown willow never targets whoever planted it and skips
 * them in its provocation check entirely, so you can walk under your own tree while it mauls
 * everything else — a living wall. The knot and the redstone freeze are the release valve for
 * letting guests through.
 *
 * <p>Growth refuses rather than forces: a nine-block tree needs room, and a sapling that punched its
 * way through a ceiling would be a grief report. If the space is occupied it simply waits and tries
 * again on the next random tick.
 */
public class WillowSaplingBlock extends BushBlock implements BonemealableBlock {

    public static final MapCodec<WillowSaplingBlock> CODEC = simpleCodec(WillowSaplingBlock::new);

    public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
    private static final VoxelShape SHAPE = box(3.0D, 0.0D, 3.0D, 13.0D, 14.0D, 13.0D);

    /** Clearance the grown tree needs, in blocks. The trunk box is 3 wide and 9 tall; the extra
     *  width is so it does not immediately shove itself out of a hole it exactly fits. */
    private static final int NEED_W = 5;
    private static final int NEED_H = 10;

    public WillowSaplingBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * A full-grown sapling waits to be raised, and whoever raises it owns it.
     *
     * <p>Ownership is decided here rather than at planting on purpose. A block state cannot carry a
     * UUID and this sapling has no block entity, so remembering the planter across the minutes
     * before it matures would mean stashing it somewhere that does not survive a restart and is
     * wrong the moment a second player plants one. Asking for a deliberate act instead makes the
     * answer exact, and it gives the player a moment where they choose to wake the thing.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (state.getValue(AGE) < 3) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!hasRoom(level, pos)) {
            // Silent failure would read as a broken block, so say what is wrong.
            player.displayClientMessage(
                    Component.translatable("block.hogwartsmagic.willow_sapling.no_room"), true);
            return InteractionResult.CONSUME;
        }
        grow((ServerLevel) level, pos, level.getRandom(), player.getUUID());
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) < 9 || random.nextInt(7) != 0) {
            return;
        }
        advance(state, level, pos, random);
    }

    private void advance(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int age = state.getValue(AGE);
        if (age < 3) {
            level.setBlock(pos, state.setValue(AGE, age + 1), 2);
            return;
        }
        // Deliberately does NOT grow on its own at full age: an unraised sapling is a sapling
        // waiting for someone to claim it. See useWithoutItem.
    }

    private boolean hasRoom(LevelReader level, BlockPos pos) {
        int half = NEED_W / 2;
        for (int dx = -half; dx <= half; dx++) {
            for (int dz = -half; dz <= half; dz++) {
                for (int dy = 0; dy < NEED_H; dy++) {
                    BlockPos check = pos.offset(dx, dy, dz);
                    if (check.equals(pos)) {
                        continue;   // the sapling itself is about to be removed
                    }
                    BlockState at = level.getBlockState(check);
                    if (!at.isAir() && !at.canBeReplaced()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void grow(ServerLevel level, BlockPos pos, RandomSource random, UUID owner) {
        WhompingWillowEntity willow = ModEntities.WHOMPING_WILLOW.get().create(level);
        if (willow == null) {
            return;
        }
        // Snapped to a right angle like every other willow, so its four branches sit on the compass
        // and the knot bearing is well defined.
        float yaw = random.nextInt(4) * 90.0F;
        willow.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, 0.0F);
        level.removeBlock(pos, false);
        willow.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.TRIGGERED, null);
        willow.setOwner(owner);
        level.addFreshEntity(willow);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(AGE) < 3;   // bone meal ripens it; only a person raises it
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return random.nextFloat() < 0.45F;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        advance(state, level, pos, random);
    }
}
