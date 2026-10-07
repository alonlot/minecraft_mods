package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.WizardCauldronBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The Wizard Cauldron: a brewing station. LEVEL is the water doses (0..3) and picks the model. The default state is
 * full (3), so cauldrons from structures and from worlds saved before brewing still look full; a cauldron placed by a
 * player starts empty. Everything else lives in {@link WizardCauldronBlockEntity}.
 */
public class WizardCauldronBlock extends BaseEntityBlock {
    public static final MapCodec<WizardCauldronBlock> CODEC = simpleCodec(WizardCauldronBlock::new);
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 3);
    /** Blocks that heat a cauldron from below. Campfires count only when lit. */
    public static final TagKey<Block> CAULDRON_HEAT = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "cauldron_heat"));
    private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 13.0, 15.0);

    public WizardCauldronBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> 7).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 3));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(LEVEL, 0);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WizardCauldronBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Only with liquid; the ticker itself does work once a second and only with heat below.
        if (level.isClientSide || state.getValue(LEVEL) == 0) {
            return null;
        }
        return createTickerHelper(type, ExampleMod.WIZARD_CAULDRON_BLOCK_ENTITY.get(), WizardCauldronBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (!WizardCauldronBlockEntity.handles(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        WizardCauldronBlockEntity cauldron = cauldronAt(level, pos, state);
        if (cauldron == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        cauldron.useItem((ServerLevel) level, stack, player, hand);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!player.getOffhandItem().isEmpty()) {
            return InteractionResult.PASS; // vanilla then tries the off-hand item through useItemOn
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        WizardCauldronBlockEntity cauldron = cauldronAt(level, pos, state);
        if (cauldron == null) {
            return InteractionResult.PASS;
        }
        cauldron.showStatus(player);
        return InteractionResult.CONSUME;
    }

    /**
     * The cauldron's block entity. On the server thread Level.getBlockEntity creates a missing one for an
     * EntityBlock (LevelChunk.EntityCreationType.IMMEDIATE), which covers cauldrons saved before brewing existed.
     * If it still returns null, one is created here.
     */
    @Nullable
    private static WizardCauldronBlockEntity cauldronAt(Level level, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof WizardCauldronBlockEntity cauldron) {
            return cauldron;
        }
        if (blockEntity == null) {
            WizardCauldronBlockEntity created = new WizardCauldronBlockEntity(pos, state);
            level.setBlockEntity(created);
            return created;
        }
        return null;
    }

    /** Heat below: a block in #hogwartsmagic:cauldron_heat, and lit if it can be lit. Reads one loaded block. */
    public static boolean isHeated(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (!below.is(CAULDRON_HEAT)) {
            return false;
        }
        return !below.hasProperty(BlockStateProperties.LIT) || below.getValue(BlockStateProperties.LIT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LEVEL) > 0 && random.nextInt(5) == 0) {
            level.addParticle(ParticleTypes.WITCH,
                    pos.getX() + 0.3 + random.nextDouble() * 0.4,
                    pos.getY() + 0.8 + random.nextDouble() * 0.2,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4,
                    0.0, 0.02, 0.0);
        }
    }
}
