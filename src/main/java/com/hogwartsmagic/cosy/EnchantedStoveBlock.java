package com.hogwartsmagic.cosy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The enchanted stove: a cast-iron range that runs on fuel. Right-click furnace fuel into it, up to eight coals'
 * worth; while it has fuel it is lit (glows, has flames, gives light 13) and is a heat source for a pot on top.
 */
public class EnchantedStoveBlock extends BaseEntityBlock {
    public static final MapCodec<EnchantedStoveBlock> CODEC = simpleCodec(EnchantedStoveBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public EnchantedStoveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnchantedStoveBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null
                : createTickerHelper(type, CosyKitchen.ENCHANTED_STOVE_BLOCK_ENTITY.get(), EnchantedStoveBlockEntity::serverTick);
    }

    // ---------------------------------------------------------------- fuel

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        int burn = net.minecraftforge.common.ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
        if (burn <= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof EnchantedStoveBlockEntity stove)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stove.addFuel(burn)) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.stove.full"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            ItemStack remainder = stack.hasCraftingRemainingItem() ? stack.getCraftingRemainingItem() : ItemStack.EMPTY;
            stack.shrink(1);
            if (!remainder.isEmpty()) {
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, remainder);
                } else {
                    ChocolateFrogItem.giveOrDrop(player, remainder);
                }
            }
        }
        level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.5F, 1.3F);
        stove.refreshLit(level);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof EnchantedStoveBlockEntity stove) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.stove.fuel",
                    String.format("%.1f", stove.burn() / 1600.0F)), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction front = state.getValue(FACING);
        if (random.nextInt(4) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.5F, 0.9F + random.nextFloat() * 0.2F, false);
        }
        double fx = pos.getX() + 0.5 + front.getStepX() * 0.52;
        double fz = pos.getZ() + 0.5 + front.getStepZ() * 0.52;
        double side = (random.nextDouble() - 0.5) * 0.5;
        double ox = front.getAxis() == Direction.Axis.X ? 0.0 : side;
        double oz = front.getAxis() == Direction.Axis.Z ? 0.0 : side;
        level.addParticle(ParticleTypes.FLAME, fx + ox, pos.getY() + 0.3 + random.nextDouble() * 0.2, fz + oz, 0.0, 0.0, 0.0);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5,
                    pos.getY() + 1.05, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.03, 0.0);
        }
    }
}
