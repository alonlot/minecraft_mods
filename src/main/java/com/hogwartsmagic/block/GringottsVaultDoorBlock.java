package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.GringottsVaultDoorBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The door on a Gringotts vault.
 *
 * <p>Every block of the leaf is one of these, and any of them will start the sequence - the door is
 * opened by putting a hand on it, the way a goblin does, rather than by finding a keyhole. Which
 * block you touch is where the ripple starts from, so it is worth touching the middle.
 *
 * <p>The leaf is unminable on purpose. A vault you can take out with a pickaxe is not a vault, and
 * the whole of the rest of the chamber is sealed rock, so the door is the only way in either way.
 *
 * <h2>Three phases, because a moving block is not a block</h2>
 *
 * <p>Minecraft can only draw a block where its block is, so a door whose plates ripple, turn and
 * fold back cannot be drawn by the world renderer at all - it has to be drawn by the block entity,
 * which is free to put its geometry anywhere. That means the real block has to get out of the way
 * without ceasing to exist, and one boolean cannot say both things:
 *
 * <ul>
 *   <li><b>0 - shut.</b> Ordinary block: drawn from its model, solid, opaque.</li>
 *   <li><b>1 - moving.</b> Invisible <em>and still solid</em>. The renderer is drawing this cell
 *       somewhere else; the doorway is not open yet and must not be walked through.</li>
 *   <li><b>2 - parted.</b> Invisible, no collision, no light. It reads as gone, but it is still a
 *       block, which is what lets the vault shut itself again a minute later.</li>
 * </ul>
 */
public class GringottsVaultDoorBlock extends BaseEntityBlock {

    public static final MapCodec<GringottsVaultDoorBlock> CODEC =
            simpleCodec(GringottsVaultDoorBlock::new);

    public static final int SHUT = 0;
    public static final int MOVING = 1;
    public static final int PARTED = 2;

    public static final IntegerProperty PHASE = IntegerProperty.create("phase", SHUT, PARTED);

    public GringottsVaultDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PHASE, SHUT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PHASE) == SHUT ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return state.getValue(PHASE) == PARTED ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return state.getValue(PHASE) == PARTED ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        // Never occludes, even shut: while the leaf is MOVING the renderer draws geometry outside
        // these cells, and a cell that claims to be a full occluder would cull it away.
        return Shapes.empty();
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(PHASE) == PARTED ? 0 : 15;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(PHASE) == PARTED;
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GringottsVaultDoorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        // Both sides tick. The server runs the sequence; the client counts along with it between
        // the handful of corrections it is sent, which is what lets the animation be smooth without
        // a packet a tick.
        return createTickerHelper(type, ExampleMod.GRINGOTTS_VAULT_DOOR_BLOCK_ENTITY.get(),
                level.isClientSide
                        ? GringottsVaultDoorBlockEntity::clientTick
                        : GringottsVaultDoorBlockEntity::tick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (state.getValue(PHASE) != SHUT) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!player.getMainHandItem().isEmpty()) {
            // A goblin draws a finger down it. Anything held is in the way of that.
            server.playSound(null, pos, SoundEvents.NETHERITE_BLOCK_HIT, SoundSource.BLOCKS,
                    0.8F, 0.5F);
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, 8, 0.3D, 0.3D, 0.3D, 0.01D);
            player.displayClientMessage(
                    Component.translatable("block.hogwartsmagic.gringotts_vault_door.hand"), true);
            return InteractionResult.CONSUME;
        }
        if (level.getBlockEntity(pos) instanceof GringottsVaultDoorBlockEntity door) {
            door.beginOpening(server, hit.getLocation(),
                    player instanceof net.minecraft.server.level.ServerPlayer p ? p : null);
        }
        return InteractionResult.CONSUME;
    }
}
