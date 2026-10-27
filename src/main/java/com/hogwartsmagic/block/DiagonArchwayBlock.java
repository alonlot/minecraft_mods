package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.DiagonArchwayBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The wall behind the Leaky Cauldron.
 *
 * <p>Every brick of the panel is one of these, and every brick can be tapped, but only one of them
 * does anything: the one three up and two across from the dustbin. Tap that one three times and the
 * wall opens. Tapping any other brick gets you a small puff of dust and nothing else, which is the
 * point -- a wall that opens wherever you hit it is a door.
 *
 * <h2>Three states</h2>
 * <ul>
 *   <li>{@code SHUT} -- a solid brick, drawn normally.</li>
 *   <li>{@code MOVING} -- invisible but still solid. The bricks of the archway sit like this for the
 *       whole eight seconds the wall is opening, because
 *       {@link com.hogwartsmagic.client.DiagonArchwayRenderer} is drawing them as free cubes and
 *       they must not be drawn twice; still solid, so nobody strolls through an arch that has not
 *       finished opening.</li>
 *   <li>{@code OPEN} -- invisible and not solid. The arch.</li>
 * </ul>
 *
 * <p>{@code LIT} is separate from all three: it swaps in the glowing-mortar texture, and the seam of
 * light that runs out across the wall before it opens is done by flipping that property on a
 * spreading ring of bricks. Doing it as a blockstate rather than a particle effect is what makes it
 * read from the far end of the courtyard.
 */
public class DiagonArchwayBlock extends BaseEntityBlock {

    public static final int SHUT = 0;
    public static final int MOVING = 1;
    public static final int OPEN = 2;

    public static final IntegerProperty PHASE = IntegerProperty.create("phase", SHUT, OPEN);
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    public static final MapCodec<DiagonArchwayBlock> CODEC = simpleCodec(DiagonArchwayBlock::new);

    public DiagonArchwayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, SHUT).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(PHASE, LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PHASE) == SHUT ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(PHASE) == OPEN ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(PHASE) == OPEN ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(PHASE) == OPEN ? 0 : 15;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(PHASE) == OPEN;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiagonArchwayBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (type != ExampleMod.DIAGON_ARCHWAY_BLOCK_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
                ? (lvl, pos, st, be) -> DiagonArchwayBlockEntity.clientTick(lvl, pos, st, (DiagonArchwayBlockEntity) be)
                : (lvl, pos, st, be) -> DiagonArchwayBlockEntity.serverTick(lvl, pos, st, (DiagonArchwayBlockEntity) be);
    }

    /**
     * If the brick running the opening sequence is broken, the sequence dies with it and the rest of
     * the panel -- sitting at {@code MOVING}, invisible but solid -- would stay that way for good.
     * The entity puts the wall back before it goes. Only when the block itself is replaced: a change
     * of {@code PHASE} or {@code LIT} keeps the same block and must not abort anything.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                            boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof DiagonArchwayBlockEntity brick) {
            brick.abortSequence(server);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** A bare hand or anything else: it is a tap either way. A wand is not required to get in. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return tap(level, pos, player);
    }

    /**
     * Tapping with something in hand counts too -- an umbrella, in the films -- so holding a wand
     * or a pickaxe does not stop the wall responding.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        tap(level, pos, player);
        return net.minecraft.world.ItemInteractionResult.CONSUME;
    }

    /**
     * A spell landed on this wall. Arms it, so the taps start counting.
     *
     * <p>Called from {@code SpellBeamEntity}'s Alohomora and Revelio arms — the same shape as
     * {@code ChamberDoorBlock.speakTo}. Either spell works: the books do not say which charm opens
     * the wall, and a player who reaches for the reasonable-but-other one should not be stuck.
     */
    public static boolean enchant(ServerLevel level, BlockPos pos, @Nullable ServerPlayer caster) {
        return level.getBlockEntity(pos) instanceof DiagonArchwayBlockEntity brick
                && brick.enchant(level, caster);
    }

    private InteractionResult tap(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof DiagonArchwayBlockEntity brick) {
            brick.tapped(server, player instanceof ServerPlayer sp ? sp : null);
        }
        return InteractionResult.CONSUME;
    }
}
