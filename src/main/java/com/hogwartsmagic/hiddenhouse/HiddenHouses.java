package com.hogwartsmagic.hiddenhouse;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.block.entity.HiddenHouseBlockEntity;
import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.hiddenhouse.HiddenHouseRules.Cell;
import com.hogwartsmagic.spell.vfx.SpellVfxOptions;
import com.hogwartsmagic.tomb.CurseScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What Revelio does to a hidden wizard house. Cast within ten blocks of any of its hidden blocks and
 * the whole house gives up its glamour: a shimmer runs out from the caster, and as it reaches each
 * block the phantom ruin stones vanish and every glamoured block turns into the real one it was
 * hiding (the walls, the bed, the bookshelves, the chest with its loot). It is permanent.
 *
 * <p>Server only, loaded chunks only. A cast that finds nothing within ten blocks costs a walk
 * through the nine nearest chunks' block entities and no more.
 */
public final class HiddenHouses {
    /** Reveal blocks without shape updates, so a bed half or a lantern never pops while its partner is still hidden. */
    private static final int REVEAL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The pale silver-gold of the shimmer, as the particle bursts draw it. */
    private static final int SHIMMER_RGB = 0xE8E4C8;
    /** The red of a jinx lifting. */
    private static final int WARD_RGB = 0xD03030;
    /** One block in this many shows a burst of motes as it appears: every block would flood the network. */
    private static final int BURST_EVERY = 3;

    private HiddenHouses() {
    }

    /**
     * Revelio's reveal of a hidden house.
     *
     * @return how many blocks were scheduled to reveal; 0 when no hidden house is within reach
     */
    public static int reveal(ServerLevel level, Vec3 center, @Nullable Entity caster) {
        if (!anyWithin(level, center, HiddenHouseRules.REVEAL_RADIUS)) {
            return 0;
        }
        Map<Cell, HiddenHouseBlockEntity> found = hiddenWithin(level, center, HiddenHouseRules.searchHalfWidth());
        List<Cell> house = HiddenHouseRules.house(found.keySet(), center.x, center.y, center.z);
        if (house.isEmpty()) {
            return 0;
        }

        double farthest = 0.0;
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        for (Cell cell : house) {
            BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
            double distance = Math.sqrt(pos.distToCenterSqr(center));
            farthest = Math.max(farthest, distance);
            sx += cell.x() + 0.5;
            sy += cell.y() + 0.5;
            sz += cell.z() + 0.5;
            int delay = HiddenHouseRules.revealDelay(distance);
            BlockPos partner = partnerOf(found.get(cell).real(), pos);
            if (partner != null && found.containsKey(new Cell(partner.getX(), partner.getY(), partner.getZ()))) {
                // The two halves of a bed or door appear together, on the earlier of their two beats.
                delay = Math.min(delay, HiddenHouseRules.revealDelay(Math.sqrt(partner.distToCenterSqr(center))));
            }
            if (delay <= 0) {
                revealOne(level, pos, 0);
            } else {
                CurseScheduler.after(delay, () -> revealOne(level, pos, 0));
            }
        }
        Fx.send(level, FxKinds.HIDDEN_HOUSE_SHIMMER, center, 64.0, -1, (float) farthest + 2.0F,
                HiddenHouseRules.WAVE_SPEED);
        level.playSound(null, center.x, center.y, center.z, ModSounds.HIDDEN_HOUSE_REVEAL.get(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        int n = house.size();
        Vec3 centroid = new Vec3(sx / n, sy / n, sz / n);
        if (centroid.distanceToSqr(center) > 9.0) {
            level.playSound(null, centroid.x, centroid.y, centroid.z, ModSounds.HIDDEN_HOUSE_REVEAL.get(),
                    SoundSource.BLOCKS, 0.8F, 1.1F);
        }
        return n;
    }

    /**
     * Turns one hidden block real. Skips quietly when the block is no longer hidden (a second cast, or a
     * reveal already done) or its chunk is unloaded; waits while someone stands where a solid block is about
     * to appear.
     */
    static void revealOne(ServerLevel level, BlockPos pos, int retries) {
        if (!level.isLoaded(pos)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof HiddenHouseBlockEntity be)) {
            return;
        }
        BlockState target = be.real() != null ? be.real() : Blocks.AIR.defaultBlockState();
        if (!target.getCollisionShape(level, pos).isEmpty()
                && !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), e -> !e.isSpectator()).isEmpty()) {
            if (retries < HiddenHouseRules.OCCUPIED_MAX_RETRIES) {
                CurseScheduler.after(HiddenHouseRules.OCCUPIED_RETRY_TICKS, () -> revealOne(level, pos, retries + 1));
            }
            // Past the retries it stays glamoured rather than entomb anyone; the next Revelio finishes it.
            return;
        }
        boolean ward = target.isAir() && be.getBlockState().is(com.hogwartsmagic.ExampleMod.JINX_WARD.get());
        CompoundTag nbt = be.realNbt() == null ? null : be.realNbt().copy();
        level.setBlock(pos, target, REVEAL_FLAGS);
        if (nbt != null) {
            if (nbt.contains("LootTable")) {
                nbt.putLong("LootTableSeed", level.random.nextLong());
            }
            BlockEntity fresh = level.getBlockEntity(pos);
            if (fresh != null) {
                fresh.loadWithComponents(nbt, level.registryAccess());
                fresh.setChanged();
            }
        }
        if (ward) {
            level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.SPARK, WARD_RGB, 0.25F, 20),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.05);
        } else if ((pos.getX() + pos.getY() * 3 + pos.getZ() * 5) % BURST_EVERY == 0) {
            level.sendParticles(new SpellVfxOptions(SpellVfxOptions.Kind.MOTE, SHIMMER_RGB, 0.3F, 22),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** The other half of a bed or a door, or null for a block with no partner. */
    @Nullable
    static BlockPos partnerOf(@Nullable BlockState real, BlockPos pos) {
        if (real == null) {
            return null;
        }
        if (real.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return real.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.above() : pos.below();
        }
        if (real.hasProperty(BlockStateProperties.BED_PART) && real.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            Direction facing = real.getValue(BlockStateProperties.HORIZONTAL_FACING);
            return real.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT
                    ? pos.relative(facing) : pos.relative(facing.getOpposite());
        }
        return null;
    }

    /** True when any hidden house block entity lies within {@code radius} of {@code center} (cheap first pass). */
    private static boolean anyWithin(ServerLevel level, Vec3 center, int radius) {
        double r2 = (double) radius * radius;
        for (LevelChunk chunk : loadedChunks(level, center, radius)) {
            for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                if (be instanceof HiddenHouseBlockEntity && be.getBlockPos().distToCenterSqr(center) <= r2) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Every hidden house block entity in the loaded chunks within {@code halfWidth} of {@code center}. */
    private static Map<Cell, HiddenHouseBlockEntity> hiddenWithin(ServerLevel level, Vec3 center, int halfWidth) {
        Map<Cell, HiddenHouseBlockEntity> found = new HashMap<>();
        for (LevelChunk chunk : loadedChunks(level, center, halfWidth)) {
            for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                if (be instanceof HiddenHouseBlockEntity hidden) {
                    BlockPos p = be.getBlockPos();
                    found.put(new Cell(p.getX(), p.getY(), p.getZ()), hidden);
                }
            }
        }
        return found;
    }

    private static List<LevelChunk> loadedChunks(ServerLevel level, Vec3 center, int halfWidth) {
        List<LevelChunk> chunks = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(center.x - halfWidth);
             cx <= SectionPos.blockToSectionCoord(center.x + halfWidth); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(center.z - halfWidth);
                 cz <= SectionPos.blockToSectionCoord(center.z + halfWidth); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) {
                    chunks.add(chunk);
                }
            }
        }
        return chunks;
    }
}
