package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.block.DiagonArchwayBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Walking into another world without being told you have.
 *
 * <p>Vanilla cannot do this. {@code ServerPlayer.changeDimension} sends exactly one packet to
 * announce the swap -- {@code ClientboundRespawnPacket} -- and {@code ClientPacketListener}
 * responds to it by throwing away the level, building a new one, and calling
 * {@code startWaitingForNewLevel}, which puts {@code ReceivingLevelScreen} on the screen
 * unconditionally. There is no flag that suppresses it and no dimension setting that avoids it.
 * Every route through the vanilla transfer ends at a loading screen.
 *
 * <p>So this does not use it. The player is moved between {@link ServerLevel}s by hand and the
 * client is never told. It keeps the world it already has, the chunk cache stays centred where it
 * was, and the alley -- which is standing at the same coordinates in
 * {@link DiagonAlleyDimension} -- simply arrives in it as ordinary chunk packets. A chunk packet
 * carries no dimension. Nothing about the crossing is visible.
 *
 * <h2>What that costs</h2>
 *
 * <p>The client believes it is still in the world it started in, and it is wrong. F3 will say so,
 * and so will anything client-side that reads the dimension. Server-side everything is correct --
 * the player really is in the alley, ticking, colliding and saving there -- so this is a lie told
 * to one component rather than an inconsistency in the game state. It is contained, and it is the
 * price of a seam nobody can see.
 *
 * <h2>Why chunks must not be dropped</h2>
 *
 * <p>{@code ChunkMap.updatePlayerStatus(player, false)} ends with
 * {@code applyChunkTrackingView(player, EMPTY)}, which tells the client to forget every chunk it
 * holds. Left alone, the world would blink out at the threshold and stream back in. So
 * {@code PlayerChunkSenderMixin} drops nothing while {@link #isMoving} is true.
 *
 * <p>That has a second effect, and it is the one that makes looking back through the arch work: the
 * chunks of your own world are still in the client when you arrive. The alley dimension is empty
 * out there and would overwrite them with void, so the same mixin declines to send alley chunks
 * outside the street. Nothing is faked. Your world is simply never painted over.
 *
 * <h2>No bookkeeping</h2>
 *
 * <p>A doorway is not remembered anywhere. It is an entry in {@link DiagonAlleyRegistry} whose arch
 * block happens to be standing open, checked afresh each tick. Both arches -- the one the player
 * built and the template one on the alley side -- sit at the same coordinates, so one entry
 * describes the door from both sides, and a server restart or a player logging in inside the alley
 * needs no special case at all.
 */
public final class DiagonAlleyPortal {

    private static final Logger LOGGER = LoggerFactory.getLogger("HogwartsMagic-DiagonAlley");

    /** How far from an arch a player has to be for it to be worth testing them against it. */
    private static final int NEAR = 48;
    /** Half-width and half-height of the opening, generously, so walking through always registers. */
    private static final int MOUTH_WIDE = 4;
    private static final int MOUTH_TALL = 9;

    /** Players in a transfer right now. Read by {@code PlayerChunkSenderMixin}. */
    private static final Set<UUID> moving = new HashSet<>();
    /**
     * Which arches each player has already been handed the view through.
     *
     * <p>A set, not one arch each, and that is not tidiness. With a single slot, two doorways in
     * range of the same player took it in turns: each tick, entry A found the slot holding B and
     * resent the whole street, then entry B found it holding A and did the same. Sixty chunk
     * packets became two thousand a second, the outbound queue grew without limit, and the server
     * died of an OutOfMemoryError a couple of minutes later.
     */
    private static final Map<UUID, Set<BlockPos>> shown = new HashMap<>();

    private DiagonAlleyPortal() {
    }

    /** True while this player is being moved between worlds and must keep every chunk it has. */
    public static boolean isMoving(ServerPlayer player) {
        return !moving.isEmpty() && moving.contains(player.getUUID());
    }

    /**
     * Should this chunk of the alley dimension be sent to this player?
     *
     * <p>Only if it holds some of a street. Everywhere else the alley is void, and sending void
     * would paint over the world the client is still holding behind the arch.
     */
    public static boolean shouldSendAlleyChunk(ServerPlayer player, ChunkPos pos) {
        ServerLevel alley = player.serverLevel();
        if (!DiagonAlleyDimension.isAlley(alley)) {
            return true;
        }
        DiagonAlleyRegistry.Entry entry = DiagonAlleyRegistry.get(alley).coveringChunk(pos.x, pos.z);
        // Beyond the wall only. The near side is where the player came from, and their client is
        // still holding it -- that is what they see when they turn round and look back out.
        return entry != null && DiagonAlleyView.throughTheArch(pos.x, pos.z, entry);
    }

    /**
     * The arch of a doorway standing open near this brick, or null.
     *
     * <p>A second way of answering "is this wall open", independent of flooding the panel. The
     * flood decides which bricks are coplanar with the one that was hit, and if it ever disagrees
     * with the wall that was actually opened -- a brick added since, a panel that measures its
     * facing differently from a corner -- the tap silently does nothing and the player is left with
     * an arch they cannot shut. Asking the registry instead cannot disagree with itself.
     */
    @javax.annotation.Nullable
    public static BlockPos openDoorwayNear(ServerLevel level, BlockPos brick) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        ServerLevel alley = DiagonAlleyDimension.level(server);
        if (alley == null) {
            return null;
        }
        for (DiagonAlleyRegistry.Entry entry : DiagonAlleyRegistry.get(alley).all()) {
            if (entry.arch().distSqr(brick) > 24 * 24) {
                continue;
            }
            ServerLevel source = sourceLevel(server, entry);
            if (archIsOpen(alley, entry.arch())
                    || (source != null && archIsOpen(source, entry.arch()))) {
                return entry.arch();
            }
        }
        return null;
    }

    public static void forget(ServerPlayer player) {
        shown.remove(player.getUUID());
        moving.remove(player.getUUID());
    }

    /** Forget every player. Called when the server stops. */
    public static void reset() {
        shown.clear();
        moving.clear();
    }

    // ---------------------------------------------------------------------------------------

    /** Every tick: hand out the view through open arches, and move whoever walks into one. */
    public static void tick(MinecraftServer server) {
        ServerLevel alley = DiagonAlleyDimension.level(server);
        if (alley == null) {
            return;
        }
        DiagonAlleyRegistry registry = DiagonAlleyRegistry.get(alley);
        if (registry.all().isEmpty()) {
            return;
        }

        for (DiagonAlleyRegistry.Entry entry : registry.all()) {
            ServerLevel source = sourceLevel(server, entry);
            if (source == null) {
                continue;
            }
            boolean openOutside = archIsOpen(source, entry.arch());
            boolean openInside = archIsOpen(alley, entry.arch());

            // Copied, not iterated live. cross() takes the player out of the level it is standing
            // in, which is the very list being walked -- ConcurrentModificationException, and the
            // server tick loop dies on the first step through an arch.
            for (ServerPlayer player : new ArrayList<>(source.players())) {
                if (player.serverLevel() != source || !near(player, entry.arch())) {
                    continue;
                }
                if (openOutside) {
                    show(player, alley, entry);
                    if (beyond(player, entry, true)) {
                        cross(player, alley, entry, "in");
                    }
                } else {
                    unshow(player, source, entry);
                }
            }

            for (ServerPlayer player : new ArrayList<>(alley.players())) {
                if (player.serverLevel() != alley || !near(player, entry.arch())
                        || !entry.contains(player.blockPosition())) {
                    continue;
                }
                if (openInside && beyond(player, entry, false)) {
                    cross(player, source, entry, "out");
                    // Their own world is what they are walking back into, and their client has been
                    // holding it all along; but the straddling row was patched with alley blocks
                    // when they came in, so it has to be painted back.
                    DiagonAlleyView.showRealWorld(player, source, entry);
                    Set<BlockPos> seen = shown.get(player.getUUID());
                    if (seen != null) {
                        seen.remove(entry.arch());
                    }
                }
            }
        }
    }

    /**
     * Open or shut the matching arch in the other world.
     *
     * <p>The two walls stand at the same coordinates -- the template carries its own panel of
     * archway brick at the courtyard end, and the anchor is chosen so it lands exactly where the
     * player built theirs. So a doorway is one hole in two worlds, and it has to be cut in both or
     * a player who steps through the open side arrives inside a solid wall on the other.
     *
     * <p>Shutting is mirrored at the <em>start</em> of the closing animation rather than the end,
     * so the way through stops being a way through the moment the bricks begin to fly back, on both
     * sides at once.
     */
    public static void mirrorArch(ServerLevel from, BlockPos archCentre,
                                  java.util.List<BlockPos> cells, boolean open) {
        MinecraftServer server = from.getServer();
        if (server == null) {
            return;
        }
        ServerLevel alley = DiagonAlleyDimension.level(server);
        if (alley == null) {
            return;
        }
        DiagonAlleyRegistry.Entry entry = DiagonAlleyRegistry.get(alley).byArch(archCentre);
        if (entry == null) {
            return;
        }
        ServerLevel other = DiagonAlleyDimension.isAlley(from) ? sourceLevel(server, entry) : alley;
        if (other == null || other == from) {
            return;
        }
        int phase = open ? DiagonArchwayBlock.OPEN : DiagonArchwayBlock.SHUT;
        int changed = 0;
        for (BlockPos cell : cells) {
            BlockState state = other.getBlockState(cell);
            if (!(state.getBlock() instanceof DiagonArchwayBlock)) {
                continue;
            }
            if (state.getValue(DiagonArchwayBlock.PHASE) == phase) {
                continue;
            }
            other.setBlock(cell, state
                    .setValue(DiagonArchwayBlock.PHASE, phase)
                    .setValue(DiagonArchwayBlock.LIT, false),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            changed++;
        }
        if (changed > 0) {
            LOGGER.info("Mirrored the arch at {} {} in {} ({} cells)", archCentre,
                    open ? "open" : "shut", other.dimension().location(), changed);
        }
    }

    // ---------------------------------------------------------------------------------------

    private static ServerLevel sourceLevel(MinecraftServer server, DiagonAlleyRegistry.Entry entry) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(entry.sourceDimension())) {
                return level;
            }
        }
        return null;
    }

    private static boolean archIsOpen(ServerLevel level, BlockPos arch) {
        if (!level.isLoaded(arch)) {
            return false;
        }
        BlockState state = level.getBlockState(arch);
        return state.getBlock() instanceof DiagonArchwayBlock
                && state.getValue(DiagonArchwayBlock.PHASE) == DiagonArchwayBlock.OPEN;
    }

    private static boolean near(ServerPlayer player, BlockPos arch) {
        return player.blockPosition().distSqr(arch) < NEAR * NEAR;
    }

    /**
     * Has this player stepped through?
     *
     * <p>{@code inward} true asks whether they are on the alley side of the wall, false whether
     * they are back on their own side. Both also require them to be in the mouth of the arch rather
     * than merely somewhere past the plane, or walking round the end of a wall would count.
     */
    private static boolean beyond(ServerPlayer player, DiagonAlleyRegistry.Entry entry,
                                  boolean inward) {
        Direction facing = entry.facing();
        boolean alongX = facing.getAxis() == Direction.Axis.X;
        int step = facing.getAxisDirection().getStep();
        BlockPos arch = entry.arch();

        double along = alongX ? player.getX() : player.getZ();
        double across = alongX ? player.getZ() : player.getX();
        int archAlong = alongX ? arch.getX() : arch.getZ();
        int archAcross = alongX ? arch.getZ() : arch.getX();

        if (Math.abs(across - (archAcross + 0.5D)) > MOUTH_WIDE) {
            return false;
        }
        if (player.getY() < arch.getY() - 2 || player.getY() > arch.getY() + MOUTH_TALL) {
            return false;
        }
        double past = step * (along - (archAlong + 0.5D));
        // A band, not a half-space: they have to be at the wall, not merely on its far side, so
        // somebody who is already deep in the street is not endlessly re-transferred.
        return inward ? (past > 0.0D && past < 6.0D) : (past < 0.0D && past > -6.0D);
    }

    private static void show(ServerPlayer player, ServerLevel alley,
                             DiagonAlleyRegistry.Entry entry) {
        Set<BlockPos> seen = shown.computeIfAbsent(player.getUUID(), id -> new HashSet<>());
        if (!seen.add(entry.arch().immutable())) {
            return;
        }
        DiagonAlleyView.showAlley(player, alley, entry, entry.arch(), entry.facing());
    }

    private static void unshow(ServerPlayer player, ServerLevel source,
                               DiagonAlleyRegistry.Entry entry) {
        Set<BlockPos> seen = shown.get(player.getUUID());
        if (seen == null || !seen.remove(entry.arch())) {
            return;
        }
        DiagonAlleyView.showRealWorld(player, source, entry);
    }

    /**
     * The crossing itself.
     *
     * <p>Position is carried over exactly. They are mid-stride in the mouth of the arch, which is
     * air in both worlds, so there is nothing to be put down on and nothing to be clipped into --
     * and moving them even half a block would be the one visible thing about the whole transfer.
     */
    private static void cross(ServerPlayer player, ServerLevel target,
                              DiagonAlleyRegistry.Entry entry, String way) {
        ServerLevel from = player.serverLevel();
        if (from == target) {
            return;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        moving.add(player.getUUID());
        try {
            com.hogwartsmagic.util.SeamlessMove.move(player, target, x, y, z, player.getYRot(), player.getXRot());
        } finally {
            moving.remove(player.getUUID());
        }
        // The vanilla changed_dimension trigger cannot fire for this: the crossing is made by hand
        // precisely so that no dimension change is ever announced, so the advancement is awarded here.
        if (target.dimension().equals(DiagonAlleyDimension.KEY)) {
            com.hogwartsmagic.util.ModAdvancements.award(player, "diagon_alley");
        }
        LOGGER.info("{} stepped {} through the arch at {} ({} -> {})",
                player.getGameProfile().getName(), way, entry.arch(),
                from.dimension().location(), target.dimension().location());
    }
}
