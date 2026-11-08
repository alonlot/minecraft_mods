package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.entity.ChamberDoorBlockEntity;
import com.hogwartsmagic.ModSounds;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The lock at the centre of the Chamber of Secrets' round door.
 *
 * <p>The door does not open to a hand, a key or a pickaxe. It opens to Parseltongue, which in this
 * mod means a Serpensortia cast onto this block -- see {@link com.hogwartsmagic.entity.SpellBeamEntity}.
 * Anything else gets a hiss out of the carved serpents and nothing more, which is the hint: the
 * bolts are snakes, so talk to them.
 *
 * <p>The leaf around the lock is {@link ChamberDoorLeafBlock}, found by a scan from here rather than
 * by hard-coded offsets, because the jigsaw is free to place the Chamber at any of four rotations.
 * The lock has the leaf's three phases too: while the door moves it is invisible but solid, and once
 * the door has swung open it is not there to walk into.
 */
public class ChamberDoorBlock extends BaseEntityBlock {
    public static final MapCodec<ChamberDoorBlock> CODEC = simpleCodec(ChamberDoorBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final IntegerProperty PHASE = ChamberDoorLeafBlock.PHASE;

    public ChamberDoorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, Boolean.FALSE)
                .setValue(PHASE, ChamberDoorLeafBlock.SHUT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return ChamberDoorLeafBlock.renderShape(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ChamberDoorLeafBlock.shape(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return ChamberDoorLeafBlock.shape(state);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return ChamberDoorLeafBlock.occlusion(state);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return ChamberDoorLeafBlock.lightBlock(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(OPEN, PHASE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChamberDoorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Both sides tick. The server runs the sequence; the client counts along with it between the
        // few corrections it is sent, so the animation is smooth without a packet every tick.
        return createTickerHelper(type, ExampleMod.CHAMBER_DOOR_BLOCK_ENTITY.get(),
                level.isClientSide ? ChamberDoorBlockEntity::clientTick : ChamberDoorBlockEntity::tick);
    }

    /**
     * Serpensortia has been cast on this block. Returns whether that actually started anything, so
     * the spell can decide if it was a hit.
     */
    public static boolean speakTo(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChamberDoorBlockEntity door && door.beginOpening(level);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (state.getValue(OPEN) || state.getValue(PHASE) != ChamberDoorLeafBlock.SHUT) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            server.playSound(null, pos, ModSounds.BASILISK_HISS_THREAT.get(), SoundSource.BLOCKS, 0.7F, 1.4F);
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    12, 0.4, 0.4, 0.4, 0.01);
            player.displayClientMessage(Component.translatable("block.hogwartsmagic.chamber_door.hiss"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
