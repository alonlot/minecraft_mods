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
        // Through the portal's non-blocking delivery: a chunk of the street that is not resident is
        // requested by ticket and sent when it arrives, not loaded here on the server thread.
        forEachChunk(viewer, entry, (cx, cz, straddles) -> {
            ChunkPos pos = new ChunkPos(cx, cz);
            if (straddles) {
                DiagonAlleyPortal.deliver(viewer, viewer.serverLevel(), pos, alley, false, entry, entry);
            } else {
                DiagonAlleyPortal.deliver(viewer, viewer.serverLevel(), pos, alley, true, null, entry);
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
        forEachChunk(viewer, entry, (cx, cz, straddles) ->
                DiagonAlleyPortal.deliver(viewer, viewer.serverLevel(), new ChunkPos(cx, cz), source,
                        true, null, null));
    }

    /** Is any part of this chunk through the arch? */
    public static boolean throughTheArch(int chunkX, int chunkZ, DiagonAlleyRegistry.Entry entry) {
        return sideOf(chunkX, chunkZ, entry) != NEAR;
    }

    // ---------------------------------------------------------------------------------------

    private interface ChunkVisitor {
        void accept(int chunkX, int chunkZ, boolean straddlesWall);
    }

    /** Where a chunk sits relative to the wall. */
    public static final int NEAR = 0;           // entirely on the viewer own side
    public static final int STRADDLING = 1;     // the wall runs through it
    public static final int BEYOND = 2;         // entirely through the arch

    /**
     * Which side of the wall a chunk is on, measured against the wall plane itself.
     *
     * <p>Getting this wrong is what put the Leaky Cauldron courtyard in somebody garden: the first
     * version treated every chunk of the footprint except the arch own chunk as through the arch,
     * and the footprint reaches back past the wall. Chunks on the near side were sent whole, which
     * replaced the ground the player was standing on with the alley -- and with the void underneath
     * it, which is why the world appeared to have a hole cut in it.
     */
    public static int sideOf(int chunkX, int chunkZ, DiagonAlleyRegistry.Entry entry) {
        boolean alongX = entry.facing().getAxis() == Direction.Axis.X;
        int step = entry.facing().getAxisDirection().getStep();
        int wall = alongX ? entry.arch().getX() : entry.arch().getZ();
        int lo = (alongX ? chunkX : chunkZ) << 4;
        int hi = lo + 15;
        boolean loBeyond = step * (lo - wall) > 0;
        boolean hiBeyond = step * (hi - wall) > 0;
        if (loBeyond && hiBeyond) {
            return BEYOND;
        }
        return loBeyond || hiBeyond ? STRADDLING : NEAR;
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
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (Math.abs(cx - at.x) > radius || Math.abs(cz - at.z) > radius) {
                    continue;
                }
                int side = sideOf(cx, cz, entry);
                if (side == NEAR) {
                    continue;               // the viewer own world; never touch it
                }
                visitor.accept(cx, cz, side == STRADDLING);
            }
        }
    }

    /**
     * Send a chunk that is already in hand, whole, straight down the player's connection.
     *
     * <p>Not through {@code PlayerChunkSender.sendChunk}, on purpose: that is the method
     * {@code PlayerChunkSenderMixin} intercepts, so a substitute sent through it would be
     * substituted again.
     */
    public static void sendChunk(ServerPlayer viewer, ServerLevel from, LevelChunk chunk) {
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
     *
     * <p>Public because the chunk-send mixin needs it too: a straddling chunk is never sent from
     * either world alone. The viewer's own world supplies the whole chunk and this lays the far
     * half of the other one over it.
     */
    public static void sendFarHalf(ServerPlayer viewer, ServerLevel alley, BlockPos archCentre,
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
