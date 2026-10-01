package com.hogwartsmagic.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The core a Living Statue is animated around: ruin stone with the binding rune still lit in it.
 *
 * <p>It is a plain full cube, and that is the design rather than a shortcut. Everything else in
 * this mod that holds power is shaped -- the ward has a frame, the horcrux has a lid -- but the
 * heart is meant to look like a piece of the quarry the statues were cut from, something you could
 * walk past in a ruin without noticing until the rune catches the light. Carving a silhouette out
 * of it would make it read as an artefact on a pedestal, which is the opposite.
 *
 * <p>The only behaviour it owns is the ambient motes. The light level and the hardness are set at
 * registration in {@code ExampleMod} with the rest of the block properties, so this class stays
 * about the one thing the properties cannot express.
 */
public class StatueHeartBlock extends Block {
    public static final MapCodec<StatueHeartBlock> CODEC = simpleCodec(StatueHeartBlock::new);

    public StatueHeartBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) {
            return;
        }
        // The model is cube_all, so the rune is cut into every face, top and bottom included -- but
        // the motes only come off the four sides. Particles rising out of the middle of the block
        // read as a censer or a beacon; this is supposed to read as light leaking out of a groove
        // in the rock, and a groove is something you see edge-on.
        boolean alongX = random.nextBoolean();
        double face = random.nextBoolean() ? -0.08D : 1.08D;
        double along = 0.2D + random.nextDouble() * 0.6D;
        level.addParticle(ParticleTypes.WITCH,
                pos.getX() + (alongX ? along : face),
                pos.getY() + 0.3D + random.nextDouble() * 0.4D,
                pos.getZ() + (alongX ? face : along),
                0.0D, 0.012D, 0.0D);
    }
}
