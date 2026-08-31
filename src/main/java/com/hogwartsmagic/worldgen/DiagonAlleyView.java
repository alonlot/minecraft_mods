package com.hogwartsmagic.worldgen;

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Showing one world to somebody standing in another.
 *
 * <p>This is what lets you look through the arch and see Diagon Alley before you have gone
 * anywhere. The street exists, in {@link DiagonAlleyDimension}, at the same coordinates as the wall.
 * All that is missing is for the client to have been told about it -- and a chunk packet is only
 * coordinates and blocks. It carries no dimension. So the alley chunks can simply be handed to a
 * player who is still standing in their own field, and their client will draw them.
 *
 * <p>The same trick run the other way is what leaves your own world visible behind you once you are
 * inside: nothing has to be sent at all, because the client still holds the chunks it had before
 * you crossed. {@link DiagonAlleyPortal} takes care not to overwrite or drop them.
 *
 * <h2>The wall is the seam</h2>
 *
 * <p>Chunks entirely on the far side of the wall are sent whole -- one packet each, light included.
 * Chunks on your own side are never touched, because replacing them would delete the ground you are
 * standing on.
 *
 * <p>The wall runs through one row of chunks, which are half yours and half the alley. Those get
 * the finer instrument: a {@link ClientboundSectionBlocksUpdatePacket} carrying only the cells
 * beyond the wall plane. It is more packets for that row, but it is the only way to split a chunk.
 */
public final class DiagonAlleyView {

    private DiagonAlleyView() {
    }

    /**
     * Hand the alley to a player who is still outside it.
     *
     * @param archCentre the wall, in the viewer's own world
     * @param facing     the way the arch opens, so "beyond" has a meaning
     */
    public static void showAlley(ServerPlayer viewer, ServerLevel alley,
                                 DiagonAlleyRegistry.Entry entry, BlockPos archCentre,
                                 Direction facing) {
        forEachChunk(viewer, entry, (cx, cz, straddles) -> {
            if (straddles) {
                sendFarHalf(viewer, alley, archCentre, facing, new ChunkPos(cx, cz), entry);
            } else {
                sendWholeChunk(viewer, alley, cx, cz);
            }
        });
    }

    /**
     * Put the viewer's own world back in front of them, undoing {@link #showAlley}.
     *
     * <p>Whole chunks either way: the straddling row was only ever patched cell by cell, and
     * resending the real chunk overwrites those patches along with everything else.
     */
    public static void showRealWorld(ServerPlayer viewer, ServerLevel source,
                                     DiagonAlleyRegistry.Entry entry) {
        forEachChunk(viewer, entry, (cx, cz, straddles) -> sendWholeChunk(viewer, source, cx, cz));
    }

    // ---------------------------------------------------------------------------------------

    private interface ChunkVisitor {
        void accept(int chunkX, int chunkZ, boolean straddlesWall);
    }

    /**
     * Every chunk of the street that is close enough for the client to accept.
     *
     * <p>The range check is not politeness. {@code ClientChunkCache.replaceWithPacketData} drops
     * any chunk outside the view radius and logs about it, so sending the far end of a long street
     * to somebody on a short render distance would be noise rather than scenery.
     */
    private static void forEachChunk(ServerPlayer viewer, DiagonAlleyRegistry.Entry entry,
                                     ChunkVisitor visitor) {
        int radius = viewer.server.getPlayerList().getViewDistance();
        ChunkPos at = viewer.chunkPosition();
        int cx0 = entry.anchor().getX() >> 4;
        int cx1 = (entry.anchor().getX() + entry.sizeX() - 1) >> 4;
        int cz0 = entry.anchor().getZ() >> 4;
        int cz1 = (entry.anchor().getZ() + entry.sizeZ() - 1) >> 4;
        int archChunkX = entry.arch().getX() >> 4;
        int archChunkZ = entry.arch().getZ() >> 4;
        boolean alongX = entry.facing().getAxis() == Direction.Axis.X;

        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (Math.abs(cx - at.x) > radius || Math.abs(cz - at.z) > radius) {
                    continue;
                }
                boolean straddles = alongX ? cx == archChunkX : cz == archChunkZ;
                visitor.accept(cx, cz, straddles);
            }
        }
    }

    private static void sendWholeChunk(ServerPlayer viewer, ServerLevel from, int cx, int cz) {
        LevelChunk chunk = from.getChunk(cx, cz);
        viewer.connection.send(new ClientboundLevelChunkWithLightPacket(
                chunk, from.getLightEngine(), null, null));
    }

    /**
     * The half of a straddling chunk that is through the arch.
     *
     * <p>Staged into a throwaway {@link LevelChunkSection} because
     * {@link ClientboundSectionBlocksUpdatePacket} reads its states out of a section rather than
     * taking them directly -- a section is the only way to say "pretend these cells are these
     * blocks" in one packet instead of one packet per block.
     */
    private static void sendFarHalf(ServerPlayer viewer, ServerLevel alley, BlockPos archCentre,
                                    Direction facing, ChunkPos chunk,
                                    DiagonAlleyRegistry.Entry entry) {
        int step = facing.getAxisDirection().getStep();
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        int wall = alongX ? archCentre.getX() : archCentre.getZ();

        int x0 = Math.max(chunk.getMinBlockX(), entry.anchor().getX());
        int x1 = Math.min(chunk.getMaxBlockX(), entry.anchor().getX() + entry.sizeX() - 1);
        int z0 = Math.max(chunk.getMinBlockZ(), entry.anchor().getZ());
        int z1 = Math.min(chunk.getMaxBlockZ(), entry.anchor().getZ() + entry.sizeZ() - 1);
        if (x0 > x1 || z0 > z1) {
            return;
        }
        int yLow = entry.anchor().getY();
        int yHigh = entry.anchor().getY() + entry.sizeY() - 1;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int sy = SectionPos.blockToSectionCoord(yLow);
                sy <= SectionPos.blockToSectionCoord(yHigh); sy++) {
            LevelChunkSection staged = new LevelChunkSection(
                    alley.registryAccess().registryOrThrow(Registries.BIOME));
            ShortSet positions = new ShortOpenHashSet();
            int baseY = SectionPos.sectionToBlockCoord(sy);

            for (int y = Math.max(yLow, baseY); y <= Math.min(yHigh, baseY + 15); y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        int along = alongX ? x : z;
                        if (step * (along - wall) <= 0) {
                            continue;           // the viewer's own side; leave it alone
                        }
                        cursor.set(x, y, z);
                        BlockState show = alley.getBlockState(cursor);
                        int lx = x & 15;
                        int ly = y & 15;
                        int lz = z & 15;
                        staged.setBlockState(lx, ly, lz, show, false);
                        positions.add((short) (lx << 8 | lz << 4 | ly));
                    }
                }
            }
            if (positions.isEmpty()) {
                continue;
            }
            viewer.connection.send(new ClientboundSectionBlocksUpdatePacket(
                    SectionPos.of(chunk, sy), positions, staged));
        }
    }
}
