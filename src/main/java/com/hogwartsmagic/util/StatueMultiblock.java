package com.hogwartsmagic.util;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.LivingStatueEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * The Piertotum Locomotor rite: turn a stone build shaped like a man into a
 * {@link LivingStatueEntity} bound to whoever cast the charm at it.
 *
 * <h2>The shape</h2>
 * Six blocks, read downward from the head the beam struck:
 * <pre>
 *   y      [ heart ]        the Statue Heart -- the block you aim at
 *   y-1  [s][ stone ][s]    arms out, torso between them
 *   y-2  [s][       ][s]    legs, apart
 * </pre>
 * The body may be any of {@link #BODY_BLOCKS} and they need not match each other -- a statue
 * patched out of stone bricks and deepslate is still a statue. Only the head is fixed, because
 * the heart is the expensive part and it is what makes this a rite rather than a way to animate
 * any five stones you happen to have stacked.
 *
 * <h2>Why the gap between the legs is checked</h2>
 * Without it a solid 3x3x2 block of stone under a heart passes, and "statue" stops meaning
 * anything. Requiring air there is what forces the silhouette to actually read as a standing
 * figure. It is the one negative-space check in the pattern and it is deliberate.
 *
 * <h2>Defensiveness</h2>
 * This runs on the server on raw player input: any player can point a wand at any block in the
 * world, including one two blocks above the world floor or one whose neighbours sit in a chunk
 * that is not loaded. So every position is probed through {@link #isBody} / {@link #isGap},
 * which check {@link ServerLevel#isLoaded} first and treat anything unreadable as "does not
 * match" rather than reading a default state and guessing. Nothing is removed until the whole
 * pattern has already matched, so a half-recognised build is never half-eaten.
 */
public final class StatueMultiblock {

    /**
     * "Stone designing like a statue of a man." Deliberately a whitelist of worked and unworked
     * grey stone rather than a tag: {@code BlockTags.STONE_BRICKS} is too narrow to cover the
     * andesite and deepslate the brief asks for, and {@code BASE_STONE_OVERWORLD} is so wide it
     * would animate a lump of tuff or a granite boulder that nobody carved.
     */
    private static final Set<Block> BODY_BLOCKS = Set.of(
            Blocks.STONE,
            Blocks.SMOOTH_STONE,
            Blocks.STONE_BRICKS,
            Blocks.CRACKED_STONE_BRICKS,
            Blocks.MOSSY_STONE_BRICKS,
            Blocks.CHISELED_STONE_BRICKS,
            Blocks.ANDESITE,
            Blocks.POLISHED_ANDESITE,
            Blocks.DEEPSLATE,
            Blocks.COBBLED_DEEPSLATE,
            Blocks.POLISHED_DEEPSLATE,
            Blocks.CHISELED_DEEPSLATE,
            Blocks.DEEPSLATE_BRICKS,
            Blocks.CRACKED_DEEPSLATE_BRICKS,
            Blocks.DEEPSLATE_TILES,
            Blocks.CRACKED_DEEPSLATE_TILES);

    private StatueMultiblock() {
    }

    /**
     * Try to animate the build hanging under {@code headPos}.
     *
     * @param caster the wand hand. A statue with no owner is a ruin guardian, not a sentry, so a
     *               cast with no player behind it (a duelling Student, a Death Eater) does
     *               nothing at all rather than raising something hostile in the caster's face.
     * @return true if a statue was raised. False makes the beam fizzle, which is this codebase's
     *         signal for "wrong target" -- see {@code SpellBeamEntity.onHit}.
     */
    public static boolean tryRaise(ServerLevel level, BlockPos headPos, @Nullable Player caster) {
        if (caster == null || !level.isLoaded(headPos)) {
            return false;
        }
        if (!level.getBlockState(headPos).is(ExampleMod.STATUE_HEART.get())) {
            // Not even the right block. Silent: this is an ordinary miss, not a failed rite, and a
            // puff on every wall the player clips would be noise.
            return false;
        }

        // The body is mirror-symmetric, so NORTH and SOUTH match exactly the same six blocks and
        // only two of these four directions can ever be distinct. All four are tried anyway,
        // because "any horizontal orientation" is the rule and hard-coding the two axes is the
        // sort of shortcut that silently stops working the day the shape gains a shoulder.
        for (Direction arm : Direction.Plane.HORIZONTAL) {
            BlockPos[] body = matchBody(level, headPos, arm);
            if (body != null) {
                raise(level, caster, headPos, body);
                return true;
            }
        }

        // A heart with nothing worth animating under it. The build is left exactly as it was.
        // Returning false makes the beam fizzle on top of this, so the player hears both: that is
        // the point rather than an oversight. The plain fizzle means "wrong target" and says
        // nothing else; the stone knock underneath it is what separates "your heart is fine, the
        // body under it is not" from having clipped a wall. Kept quiet (0.5F) so it layers.
        level.sendParticles(ParticleTypes.SMOKE,
                headPos.getX() + 0.5D, headPos.getY() + 0.6D, headPos.getZ() + 0.5D,
                8, 0.22D, 0.14D, 0.22D, 0.01D);
        level.playSound(null, headPos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.5F, 0.6F);
        return false;
    }

    /**
     * The five body blocks in the orientation whose arms run along {@code arm}, or null if this
     * orientation does not match. Returned as torso, both arms, both legs.
     */
    @Nullable
    private static BlockPos[] matchBody(ServerLevel level, BlockPos headPos, Direction arm) {
        Direction other = arm.getOpposite();
        BlockPos torso = headPos.below();
        BlockPos hip = torso.below();

        BlockPos[] body = {
                torso,
                torso.relative(arm),
                torso.relative(other),
                hip.relative(arm),
                hip.relative(other),
        };
        for (BlockPos pos : body) {
            if (!isBody(level, pos)) {
                return null;
            }
        }
        return isGap(level, hip) ? body : null;
    }

    private static boolean isBody(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && BODY_BLOCKS.contains(level.getBlockState(pos).getBlock());
    }

    /**
     * Between the legs there must be nothing. Void air (below the world floor) reports itself as
     * air too, but a statue whose legs hang off the bottom of the world fails the body check long
     * before this is reached, so the loose test costs nothing.
     */
    private static boolean isGap(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).isAir();
    }

    /** Eat the build and stand the sentry up in its place. Only called on a confirmed match. */
    private static void raise(ServerLevel level, Player caster, BlockPos headPos, BlockPos[] body) {
        // The feet own the bottom layer, so that block's own Y is where the statue stands and the
        // post it comes home to when a bound one is put down (see LivingStatueEntity.getHomePos).
        BlockPos home = headPos.below(2);

        spitStone(level, headPos);
        level.removeBlock(headPos, false);
        for (BlockPos pos : body) {
            spitStone(level, pos);
            level.removeBlock(pos, false);
        }

        LivingStatueEntity statue = new LivingStatueEntity(ModEntities.LIVING_STATUE.get(), level);
        // Facing is taken from the caster rather than from the matched arm direction: the shape is
        // symmetric, so the pattern cannot tell front from back, and a statue that rises with its
        // back to the person who woke it looks broken.
        float yaw = (float) (Mth.atan2(caster.getZ() - (home.getZ() + 0.5D),
                caster.getX() - (home.getX() + 0.5D)) * Mth.RAD_TO_DEG) - 90.0F;
        statue.moveTo(home.getX() + 0.5D, home.getY(), home.getZ() + 0.5D, yaw, 0.0F);
        statue.setYBodyRot(yaw);
        statue.setYHeadRot(yaw);
        // Bound before it is added, so the spawn packet already carries the bound flag and the
        // client never draws a frame of it as an unbound ruin guardian. It stays dormant: the
        // entity starts with no wake ticks, and a bound statue only rouses for hostile mobs, so
        // the caster can walk round it.
        statue.bindTo(caster, home);
        level.addFreshEntity(statue);

        level.playSound(null, home, com.hogwartsmagic.ModSounds.STATUE_WAKE.get(), SoundSource.BLOCKS, 1.5F, 1.0F);
        level.playSound(null, home, com.hogwartsmagic.ModSounds.STATUE_BIND.get(), SoundSource.BLOCKS, 0.9F, 1.0F);
    }

    /**
     * Grit off a block that is about to be removed. The particle carries the block's own state, so
     * a deepslate statue throws deepslate -- read the state before the removal, not after, or the
     * particle is made of air.
     */
    private static void spitStone(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                22, 0.32D, 0.32D, 0.32D, 0.07D);
    }
}
