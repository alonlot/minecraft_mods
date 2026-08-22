package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.ExampleMod;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Showing different people different walls.
 *
 * <p>Diagon Alley is opened <em>in place</em>, so its seventy-six thousand blocks are really there,
 * in the world, where anybody could walk up and see them. Two illusions fix that, and they are the
 * same mechanism pointed in opposite directions:
 *
 * <ul>
 *   <li><b>Outsiders</b> are sent the blocks that were there <i>before</i> — straight out of
 *       {@link DiagonAlleySnapshot}, which was recorded for exactly this purpose. Where the alley
 *       was built on thin air they are sent air. So the street is invisible from without, and the
 *       hillside they remember is still standing.</li>
 *   <li><b>Whoever is inside</b> is sent air for the shaft above the street, so there is sky
 *       overhead even when the arch was opened under a mountain. The mountain is not removed — only
 *       not shown. That is what keeps the snapshot bounded to the alley's own box instead of
 *       swallowing half a hill.</li>
 * </ul>
 *
 * <h2>Why this hangs off chunk-watch</h2>
 *
 * <p>The mod's existing per-player concealment (Cave Inimicum) pushes its illusion when a player
 * crosses a boundary and never again — so when a chunk unloads and reloads, vanilla resends the
 * authoritative blocks and the illusion is simply gone. Walk out of render distance and back and
 * the secret is out.
 *
 * <p>Hanging the illusion on {@link ChunkWatchEvent.Watch} instead means it is reapplied every time
 * the client is sent that chunk, because that is precisely when the event fires. The illusion
 * travels with the chunk rather than racing it.
 *
 * <h2>Honest limits</h2>
 *
 * <ul>
 *   <li>The blocks are real, so an outsider will walk into geometry they cannot see. No concealment
 *       scheme in a shared world avoids that.</li>
 *   <li>The sky shaft is {@value #SKY_SHAFT} blocks. Open a wall on the surface and you get real
 *       sky; open one deep inside a mountain and you get a very high ceiling instead.</li>
 *   <li>Lighting is computed from the real blocks. The client re-lights when blocks change, so the
 *       shaft should let daylight down, but under a thick mountain the street may still want its
 *       lamps.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DiagonAlleyIllusion {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-DiagonAlley");

    /** How far above the street to pretend there is nothing. */
    public static final int SKY_SHAFT = 48;
    /** Slack around the footprint for deciding who counts as inside. */
    private static final int INSIDE_MARGIN = 6;
    /** How often to notice somebody has walked in or out. */
    private static final int EDGE_CHECK = 20;

    /** Who was inside last time we looked, so a crossing can be spotted and re-synced. */
    private static final Map<UUID, Boolean> wasInside = new HashMap<>();

    private DiagonAlleyIllusion() {
    }

    // ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerLevel level = event.getLevel();
        DiagonAlleySnapshot snapshot = DiagonAlleySnapshot.get(level);
        if (!snapshot.isActive()) {
            return;
        }
        ChunkPos pos = event.getPos();
        if (!intersects(snapshot, pos)) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        sendFor(player, level, snapshot, pos, isInside(snapshot, player));
    }

    /**
     * Spot somebody crossing the threshold and re-send everything they can see.
     *
     * <p>Walking in has to swap the whole illusion round: the street has to appear, and the sky has
     * to open. Nothing else would notice, because no chunk was loaded or unloaded by walking.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null || server.getTickCount() % EDGE_CHECK != 0) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            DiagonAlleySnapshot snapshot = DiagonAlleySnapshot.get(level);
            if (!snapshot.isActive()) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                boolean inside = isInside(snapshot, player);
                Boolean before = wasInside.put(player.getUUID(), inside);
                if (before != null && before == inside) {
                    continue;
                }
                resendAround(player, level, snapshot);
            }
        }
    }

    /** Forget people who log out, so the map does not grow forever. */
    public static void forget(ServerPlayer player) {
        wasInside.remove(player.getUUID());
    }

    /** Called when an alley closes: everyone goes back to seeing the truth. */
    public static void clearAll(ServerLevel level, DiagonAlleySnapshot snapshot) {
        wasInside.clear();
        for (ServerPlayer player : level.players()) {
            resendTruth(player, level, snapshot);
        }
    }

    // ---------------------------------------------------------------------------------------

    private static boolean intersects(DiagonAlleySnapshot snapshot, ChunkPos pos) {
        BlockPos o = snapshot.origin();
        return pos.getMaxBlockX() >= o.getX() && pos.getMinBlockX() <= o.getX() + snapshot.sizeX()
                && pos.getMaxBlockZ() >= o.getZ() && pos.getMinBlockZ() <= o.getZ() + snapshot.sizeZ();
    }

    private static boolean isInside(DiagonAlleySnapshot snapshot, ServerPlayer player) {
        BlockPos o = snapshot.origin();
        BlockPos p = player.blockPosition();
        return p.getX() >= o.getX() - INSIDE_MARGIN
                && p.getX() <= o.getX() + snapshot.sizeX() + INSIDE_MARGIN
                && p.getZ() >= o.getZ() - INSIDE_MARGIN
                && p.getZ() <= o.getZ() + snapshot.sizeZ() + INSIDE_MARGIN
                && p.getY() >= o.getY() - INSIDE_MARGIN
                && p.getY() <= o.getY() + snapshot.sizeY() + SKY_SHAFT;
    }

    private static void resendAround(ServerPlayer player, ServerLevel level,
                                     DiagonAlleySnapshot snapshot) {
        boolean inside = isInside(snapshot, player);
        int view = level.getServer().getPlayerList().getViewDistance();
        ChunkPos centre = player.chunkPosition();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                ChunkPos pos = new ChunkPos(centre.x + dx, centre.z + dz);
                if (intersects(snapshot, pos)) {
                    sendFor(player, level, snapshot, pos, inside);
                }
            }
        }
    }

    /** Undo the illusion for one player: show them what is really there. */
    private static void resendTruth(ServerPlayer player, ServerLevel level,
                                    DiagonAlleySnapshot snapshot) {
        int view = level.getServer().getPlayerList().getViewDistance();
        ChunkPos centre = player.chunkPosition();
        for (int dx = -view; dx <= view; dx++) {
            for (int dz = -view; dz <= view; dz++) {
                ChunkPos pos = new ChunkPos(centre.x + dx, centre.z + dz);
                if (!intersects(snapshot, pos)) {
                    continue;
                }
                // Replay the real world over whatever they were shown.
                sendSectionsFromWorld(player, level, snapshot, pos);
            }
        }
    }

    // ---------------------------------------------------------------------------------------

    /**
     * Build and send the fake sections for one chunk.
     *
     * <p>The states are staged into a throwaway {@link LevelChunkSection} because
     * {@link ClientboundSectionBlocksUpdatePacket} reads them out of a section rather than taking
     * them directly — so a section is the only way to say "pretend these blocks are these states"
     * in a single packet instead of one packet per block.
     */
    private static void sendFor(ServerPlayer player, ServerLevel level,
                                DiagonAlleySnapshot snapshot, ChunkPos chunk, boolean inside) {
        BlockPos origin = snapshot.origin();
        int x0 = Math.max(chunk.getMinBlockX(), origin.getX());
        int x1 = Math.min(chunk.getMaxBlockX(), origin.getX() + snapshot.sizeX() - 1);
        int z0 = Math.max(chunk.getMinBlockZ(), origin.getZ());
        int z1 = Math.min(chunk.getMaxBlockZ(), origin.getZ() + snapshot.sizeZ() - 1);
        if (x0 > x1 || z0 > z1) {
            return;
        }

        int yLow = inside ? origin.getY() + snapshot.sizeY() : origin.getY();
        int yHigh = inside
                ? Math.min(level.getMaxBuildHeight() - 1, origin.getY() + snapshot.sizeY() + SKY_SHAFT)
                : origin.getY() + snapshot.sizeY() - 1;

        int sectionLow = SectionPos.blockToSectionCoord(yLow);
        int sectionHigh = SectionPos.blockToSectionCoord(yHigh);

        for (int sy = sectionLow; sy <= sectionHigh; sy++) {
            LevelChunkSection staged = new LevelChunkSection(
                    level.registryAccess().registryOrThrow(Registries.BIOME));
            ShortSet positions = new ShortOpenHashSet();
            int baseY = SectionPos.sectionToBlockCoord(sy);

            for (int y = Math.max(yLow, baseY); y <= Math.min(yHigh, baseY + 15); y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        BlockState show;
                        if (inside) {
                            // Only bother where something is actually in the way; open air above
                            // the street needs no help.
                            if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                                continue;
                            }
                            show = Blocks.AIR.defaultBlockState();
                        } else {
                            show = snapshot.originalAt(x, y, z);
                            if (show == null) {
                                show = Blocks.AIR.defaultBlockState();
                            }
                            if (show.equals(level.getBlockState(new BlockPos(x, y, z)))) {
                                continue;           // already correct; nothing to fake
                            }
                        }
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
            player.connection.send(new ClientboundSectionBlocksUpdatePacket(
                    SectionPos.of(chunk, sy), positions, staged));
        }
    }

    /** Send the world as it really is, for the given chunk — used when an alley closes. */
    private static void sendSectionsFromWorld(ServerPlayer player, ServerLevel level,
                                              DiagonAlleySnapshot snapshot, ChunkPos chunk) {
        BlockPos origin = snapshot.origin();
        int x0 = Math.max(chunk.getMinBlockX(), origin.getX());
        int x1 = Math.min(chunk.getMaxBlockX(), origin.getX() + snapshot.sizeX() - 1);
        int z0 = Math.max(chunk.getMinBlockZ(), origin.getZ());
        int z1 = Math.min(chunk.getMaxBlockZ(), origin.getZ() + snapshot.sizeZ() - 1);
        if (x0 > x1 || z0 > z1) {
            return;
        }
        int yLow = origin.getY();
        int yHigh = Math.min(level.getMaxBuildHeight() - 1,
                origin.getY() + snapshot.sizeY() + SKY_SHAFT);
        for (int sy = SectionPos.blockToSectionCoord(yLow);
             sy <= SectionPos.blockToSectionCoord(yHigh); sy++) {
            LevelChunkSection staged = new LevelChunkSection(
                    level.registryAccess().registryOrThrow(Registries.BIOME));
            ShortSet positions = new ShortOpenHashSet();
            int baseY = SectionPos.sectionToBlockCoord(sy);
            for (int y = Math.max(yLow, baseY); y <= Math.min(yHigh, baseY + 15); y++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        BlockState real = level.getBlockState(new BlockPos(x, y, z));
                        int lx = x & 15;
                        int ly = y & 15;
                        int lz = z & 15;
                        staged.setBlockState(lx, ly, lz, real, false);
                        positions.add((short) (lx << 8 | lz << 4 | ly));
                    }
                }
            }
            if (!positions.isEmpty()) {
                player.connection.send(new ClientboundSectionBlocksUpdatePacket(
                        SectionPos.of(chunk, sy), positions, staged));
            }
        }
    }

}
