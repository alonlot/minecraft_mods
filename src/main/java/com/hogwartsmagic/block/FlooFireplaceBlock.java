package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.FlooFireplaceBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FlooFireplaceBlock extends CampfireBlock {
    public static final MapCodec<FlooFireplaceBlock> CODEC = simpleCodec(FlooFireplaceBlock::new);
    public static final BooleanProperty ACTIVE = BlockStateProperties.SIGNAL_FIRE;

    public FlooFireplaceBlock(BlockBehaviour.Properties properties) {
        super(true, 2, properties.noOcclusion().lightLevel(state -> 12));
        registerDefaultState(defaultBlockState()
                .setValue(LIT, true)
                .setValue(SIGNAL_FIRE, false)
                .setValue(WATERLOGGED, false)
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<CampfireBlock> codec() {
        return (MapCodec<CampfireBlock>) (MapCodec<?>) CODEC;
    }

    /**
     * Hearth, back wall and side pillars, matching the fireplace model. Written for FACING north (the
     * placing player looked north, so the back wall is on the north side and the opening faces them).
     */
    private static final java.util.Map<net.minecraft.core.Direction, VoxelShape> SHAPES =
            com.hogwartsmagic.block.decor.DecorShapes.allHorizontal(com.hogwartsmagic.block.decor.DecorShapes.boxes(
                    new double[]{0, 0, 0, 16, 2, 16},
                    new double[]{0, 2, 0, 16, 16, 3},
                    new double[]{0, 2, 3, 2.5, 16, 14},
                    new double[]{13.5, 2, 3, 16, 16, 14}));

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlooFireplaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ExampleMod.FLOO_FIREPLACE_BLOCK_ENTITY.get(), FlooFireplaceBlockEntity::tick);
    }

    /**
     * The campfire's own crackle and ember pops, drawn as the mod's sparks: vanilla's lava pops (and the vanilla
     * smoke each one trails) would otherwise come with the inherited campfire behaviour.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(10) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    net.minecraft.sounds.SoundEvents.CAMPFIRE_CRACKLE, net.minecraft.sounds.SoundSource.BLOCKS,
                    0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        if (random.nextInt(5) == 0) {
            level.addParticle(com.hogwartsmagic.spell.vfx.SpellVfx.swap(net.minecraft.core.particles.ParticleTypes.LAVA),
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    (random.nextFloat() - 0.5F) * 0.1F, 0.12F + random.nextFloat() * 0.08F, (random.nextFloat() - 0.5F) * 0.1F);
        }
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(ACTIVE)) {
            return;
        }
        super.entityInside(state, level, pos, entity);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof FlooFireplaceBlockEntity fireplace) {
            Component message = fireplace.getStatusMessage(player);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(message, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
