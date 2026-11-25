package com.hogwartsmagic.cosy;

import com.hogwartsmagic.block.decor.DecorShapes;
import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The butterbeer keg: a barrel on its side with a tap. Add a sugar, a honey bottle (the glass bottle comes back)
 * and wheat; the batch ages 6000 ticks into 8 servings; right-click with an empty tankard to fill it.
 * {@code fill} is 0 empty, 1 some ingredients, 2 aging (it bubbles), 3 ready.
 */
public class ButterbeerKegBlock extends BaseEntityBlock {
    public static final MapCodec<ButterbeerKegBlock> CODEC = simpleCodec(ButterbeerKegBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);

    private static final Map<Direction, VoxelShape> SHAPES = DecorShapes.allHorizontal(DecorShapes.boxes(
            new double[]{0, 0, 2, 16, 13, 14}, new double[]{7, 2.5, 0, 9, 8, 2}));

    public ButterbeerKegBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FILL, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILL);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ButterbeerKegBlockEntity(pos, state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------------------------------------------------------------- using it

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        int ingredient = KegRules.ingredientIndex(stack.getItem());
        boolean tankard = stack.is(CosyFood.TANKARD.get());
        if (ingredient < 0 && !tankard) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ButterbeerKegBlockEntity keg)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        long now = level.getGameTime();
        if (ingredient >= 0) {
            addIngredient(level, pos, state, keg, stack, player, hand, ingredient, now);
        } else {
            pour(level, pos, state, keg, stack, player, now);
        }
        return ItemInteractionResult.CONSUME;
    }

    private void addIngredient(Level level, BlockPos pos, BlockState state, ButterbeerKegBlockEntity keg, ItemStack stack,
                               Player player, InteractionHand hand, int ingredient, long now) {
        if (!keg.addIngredient(ingredient, now)) {
            player.displayClientMessage(Component.translatable(keg.servings() > 0
                    ? "message.hogwartsmagic.keg.busy" : "message.hogwartsmagic.keg.has_that"), true);
            return;
        }
        boolean honey = ingredient == KegRules.HONEY;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            if (honey) {
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, bottle);
                } else {
                    ChocolateFrogItem.giveOrDrop(player, bottle);
                }
            }
        }
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 1.3F);
        setFill(level, pos, state, keg.fill(now));
        if (keg.fill(now) == 2) {
            level.scheduleTick(pos, this, KegRules.AGE_TICKS + 1);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.keg.brewing"), true);
        } else {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.keg.added"), true);
        }
    }

    private void pour(Level level, BlockPos pos, BlockState state, ButterbeerKegBlockEntity keg, ItemStack stack,
                      Player player, long now) {
        // The block state may still say "aging" if the batch finished while nobody was looking.
        setFill(level, pos, state, keg.fill(now));
        if (!keg.pour(now)) {
            player.displayClientMessage(keg.isBrewing(now)
                    ? Component.translatable("message.hogwartsmagic.keg.not_ready", ticksToText(keg.ticksLeft(now)))
                    : Component.translatable("message.hogwartsmagic.keg.empty"), true);
            return;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        ChocolateFrogItem.giveOrDrop(player, new ItemStack(CosyFood.BUTTERBEER.get()));
        level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8F, 0.9F);
        setFill(level, pos, level.getBlockState(pos), keg.fill(now));
    }

    private static Component ticksToText(long ticks) {
        long seconds = (ticks + 19) / 20;
        return Component.literal((seconds / 60) + ":" + String.format("%02d", seconds % 60));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ButterbeerKegBlockEntity keg) {
            long now = level.getGameTime();
            setFill(level, pos, state, keg.fill(now));
            Component status;
            if (keg.isReady(now)) {
                status = Component.translatable("message.hogwartsmagic.keg.ready", keg.servings());
            } else if (keg.isBrewing(now)) {
                status = Component.translatable("message.hogwartsmagic.keg.not_ready", ticksToText(keg.ticksLeft(now)));
            } else {
                status = Component.translatable("message.hogwartsmagic.keg.needs",
                        missing(keg));
            }
            player.displayClientMessage(status, true);
        }
        return InteractionResult.CONSUME;
    }

    private static Component missing(ButterbeerKegBlockEntity keg) {
        net.minecraft.network.chat.MutableComponent list = Component.empty();
        boolean first = true;
        for (int ingredient = 0; ingredient < 3; ingredient++) {
            if (!keg.has(ingredient)) {
                net.minecraft.world.item.Item item = ingredient == KegRules.SUGAR ? Items.SUGAR
                        : ingredient == KegRules.HONEY ? Items.HONEY_BOTTLE : Items.WHEAT;
                list.append(first ? Component.empty() : Component.literal(", ")).append(item.getDescription());
                first = false;
            }
        }
        return list.withStyle(ChatFormatting.GOLD);
    }

    /** Breaking a keg that has not started a batch hands back what was put in; a started batch is lost. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
                && level.getBlockEntity(pos) instanceof ButterbeerKegBlockEntity keg && keg.servings() == 0) {
            if (keg.has(KegRules.SUGAR)) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.SUGAR));
            }
            if (keg.has(KegRules.HONEY)) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.HONEY_BOTTLE));
            }
            if (keg.has(KegRules.WHEAT)) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.WHEAT));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static void setFill(Level level, BlockPos pos, BlockState state, int fill) {
        if (state.is(CosyFood.BUTTERBEER_KEG.get()) && state.getValue(FILL) != fill) {
            level.setBlock(pos, state.setValue(FILL, fill), Block.UPDATE_ALL);
        }
    }

    // ---------------------------------------------------------------- aging

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(FILL) == 2;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        tick(state, level, pos, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ButterbeerKegBlockEntity keg) {
            setFill(level, pos, state, keg.fill(level.getGameTime()));
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(FILL) == 2 && random.nextInt(6) == 0) {
            double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
            double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
            level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 0.85, z, 0.0, 0.02, 0.0);
            if (random.nextInt(8) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.3F, 0.8F + random.nextFloat() * 0.4F, false);
            }
        } else if (state.getValue(FILL) == 3 && random.nextInt(24) == 0) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 0.0, 0.0, 0.0);
        }
    }
}
