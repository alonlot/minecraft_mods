package com.hogwartsmagic.block;

import com.hogwartsmagic.ExampleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Gemino curse: treasure that copies itself the moment anything touches it.
 *
 * <p>Triggered by standing on it, which is the film's exact beat - a foot comes down on a goblet
 * and the vault begins to fill. Breaking it or picking the coins up does the rest.
 *
 * <h2>Why this cannot run away with a world</h2>
 *
 * <p>An uncapped copying block is a chunk-eater, so three separate things bound it and any one of
 * them alone would be enough:
 *
 * <ul>
 *   <li><b>Generation.</b> Every pile carries how far it is from an original. Children are one
 *       generation further out and the seventh cannot breed at all, so the spread is finite by
 *       construction rather than by a counter somebody has to keep.</li>
 *   <li><b>Three per touch</b>, into empty cells only.</li>
 *   <li><b>A cooldown on the ENTITY, not the block.</b> {@code stepOn} fires every tick for
 *       somebody standing still; per-block it would be three new piles a tick, forever.</li>
 * </ul>
 *
 * <p>Goblins and the dragon are exempt. It is their gold and their curse.
 */
public class GeminoPileBlock extends Block {

    /** How far from an original this pile is. The last generation is barren. */
    public static final IntegerProperty GENERATION = IntegerProperty.create("generation", 0, 7);

    private static final int MAX_GENERATION = 7;
    private static final int PER_TOUCH = 3;
    private static final int ENTITY_COOLDOWN = 10;
    private static final String TOUCHED_KEY = "hogwartsmagic:gemino_touched";

    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);

    public GeminoPileBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GENERATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GENERATION);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity living)) {
            return;
        }
        if (!(entity instanceof Player)) {
            return;      // it is the goblins' gold; it does not multiply under them
        }
        long now = server.getGameTime();
        if (now - living.getPersistentData().getLong(TOUCHED_KEY) < ENTITY_COOLDOWN) {
            return;
        }
        living.getPersistentData().putLong(TOUCHED_KEY, now);
        multiply(server, pos, state);
    }

    /** Copies itself into up to three empty neighbours, one generation further out. */
    public static void multiply(ServerLevel level, BlockPos pos, BlockState state) {
        int generation = state.hasProperty(GENERATION) ? state.getValue(GENERATION) : 0;
        if (generation >= MAX_GENERATION) {
            return;
        }
        BlockState child = ExampleMod.GEMINO_PILE.get().defaultBlockState()
                .setValue(GENERATION, generation + 1);

        int made = 0;
        for (BlockPos candidate : new BlockPos[]{
                pos.north(), pos.south(), pos.east(), pos.west(), pos.above(),
                pos.north().east(), pos.south().west(), pos.north().west(), pos.south().east()}) {
            if (made >= PER_TOUCH) {
                break;
            }
            if (!level.getBlockState(candidate).isAir()) {
                continue;
            }
            if (!level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(),
                    net.minecraft.core.Direction.UP)
                    && !(level.getBlockState(candidate.below()).getBlock() instanceof GeminoPileBlock)) {
                continue;
            }
            // Flag 2: told to clients, no neighbour updates. A cascade of block updates through a
            // vault filling with gold is exactly the runaway this is trying not to be.
            level.setBlock(candidate, child, Block.UPDATE_CLIENTS);
            level.sendParticles(ParticleTypes.FLAME, candidate.getX() + 0.5D,
                    candidate.getY() + 0.3D, candidate.getZ() + 0.5D, 3, 0.2D, 0.1D, 0.2D, 0.01D);
            made++;
        }
        if (made > 0) {
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.7F);
            level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5D, pos.getY() + 0.4D,
                    pos.getZ() + 0.5D, 2, 0.3D, 0.1D, 0.3D, 0.0D);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement,
                         boolean moving) {
        if (level instanceof ServerLevel server && !state.is(replacement.getBlock())) {
            // Breaking it counts as touching it.
            multiply(server, pos, state);
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
