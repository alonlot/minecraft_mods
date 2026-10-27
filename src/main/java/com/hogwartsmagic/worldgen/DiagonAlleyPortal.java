package com.hogwartsmagic.worldgen;

import com.hogwartsmagic.block.DiagonArchwayBlock;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
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
 *
 * <h2>Chunks the client is owed</h2>
 *
 * <p>Declining to send the void (see above) is not enough on its own, because vanilla keeps its own
 * account of what the client holds, and that account is wrong here. The alley's chunk map believes
 * it sent every near-side chunk we cancelled, so when one leaves range it sends a forget, and when it
 * comes back it is cancelled again -- a hole where the player's own world should be. So
 * {@link #interceptChunkSend} <em>substitutes</em> instead of cancelling: a chunk of the alley
 * dimension that is not part of the street is answered with the same chunk of the world the wall is
 * in, and the mirror image applies to a player in that world looking at the street through an open
 * arch. The one exception is the burst of chunks that arrive the moment somebody crosses -- the
 * client already holds all of those, so {@link #held} lets exactly those through unsent.
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

    /**
     * Chunks a player's client already holds from the world they walked in from, recorded at the
     * moment they cross into the alley and consumed as the alley's chunk map announces them.
     *
     * <p>Without it the alley's first act on receiving a player would be to re-send their entire
     * surroundings -- hundreds of chunks they are already looking at, in one tick, at the one moment
     * the whole design exists to keep invisible.
     */
    private static final Map<UUID, Held> held = new HashMap<>();
    /**
     * How long a {@link Held} record is believed, in ticks. The alley's chunk map announces what it
     * has to within a few ticks of a crossing, so a record still unconsumed after this long is
     * describing chunks the client has since been told to forget, and cancelling a send on its
     * account would leave a hole.
     */
    private static final int HELD_LIFETIME = 200;

    /** The chunks one player's client was holding when they crossed in, and when that was. */
    private static final class Held {
        final LongSet chunks;
        final long since;

        Held(LongSet chunks, long since) {
            this.chunks = chunks;
            this.since = since;
        }
    }

    /** Where the street is, as far as the void rescue is concerned. */
    private static final int VOID_DROP = 6;
    private static final int VOID_MARGIN = 4;
    /** How often the rescue looks, in ticks. Falling takes long enough that every fifth is plenty. */
    private static final int VOID_CHECK_INTERVAL = 5;
    private static final int RESCUE_COOLDOWN = 60;
    private static final Map<UUID, Long> lastRescue = new HashMap<>();

    /** How far across a wall a tapped brick may be from a registered doorway and still belong to it. */
    private static final int DOORWAY_REACH = 24;
    /** The panel's lowest brick is the arch's, so little below it; the wall can be tall above it. */
    private static final int DOORWAY_DROP = 2;
    private static final int DOORWAY_RISE = 40;

    /**
     * Keeps a chunk loaded long enough for us to read it. Long, because the ticket is the only thing
     * between a chunk that is being loaded or generated and one that is dropped again before it
     * finishes.
     */
    private static final TicketType<ChunkPos> VIEW_TICKET = TicketType.create(
            "hogwartsmagic_diagon_view", Comparator.comparingLong(ChunkPos::toLong), 100);
    private static final int MAX_DELIVERIES_PER_TICK = 12;
    private static final int GIVE_UP_AFTER = 600;
    /** How often a standing request renews its ticket; comfortably inside the ticket lifespan of 100. */
    private static final int TICKET_RENEWAL = 40;

    /** Substitute chunks whose source was not resident when the chunk map asked for them. */
    private static final ArrayDeque<Pending> waiting = new ArrayDeque<>();
    /**
     * What each player is already waiting on. Keyed by the world it is fetched from as well as by
     * the chunk: a request for the street's chunk and a later request for the field's chunk at the
     * same position are different requests, and treating the second as a duplicate of the first
     * leaves the player with whichever one was cancelled.
     */
    private static final Map<UUID, Set<Want>> requested = new HashMap<>();

    private record Want(long chunk, ServerLevel from) {
    }

    private DiagonAlleyPortal() {
    }

    /**
     * A chunk we owe somebody but cannot read yet.
     *
     * <p>Loaded by ticket and polled, never by waiting: {@code ServerChunkCache.getChunkFuture}
     * called from the server thread blocks it until the chunk is done, which for a chunk that has to
     * be generated is exactly the stall this is meant to avoid.
     */
    private static final class Pending {
        final ServerPlayer player;
        final ServerLevel playerLevel;
        final ChunkPos pos;
        final ServerLevel fetchFrom;
        final boolean sendWhole;
        @Nullable
        final DiagonAlleyRegistry.Entry patch;
        /** Set for a player outside the alley: the doorway whose open arch justifies the substitute. */
        @Nullable
        final DiagonAlleyRegistry.Entry mirror;
        final long since;

        Pending(ServerPlayer player, ServerLevel playerLevel, ChunkPos pos, ServerLevel fetchFrom,
                boolean sendWhole, @Nullable DiagonAlleyRegistry.Entry patch,
                @Nullable DiagonAlleyRegistry.Entry mirror, long since) {
            this.player = player;
            this.playerLevel = playerLevel;
            this.pos = pos;
            this.fetchFrom = fetchFrom;
            this.sendWhole = sendWhole;
            this.patch = patch;
            this.mirror = mirror;
            this.since = since;
        }
    }

    /** True while this player is being moved between worlds and must keep every chunk it has. */
    public static boolean isMoving(ServerPlayer player) {
        return !moving.isEmpty() && moving.contains(player.getUUID());
    }

    /**
     * Decide what a player's chunk map is allowed to send them, and send the right thing instead
     * where it is not. Returns true when the vanilla send must be cancelled.
     *
     * <p>Called from {@code PlayerChunkSenderMixin} for every chunk any player is sent, so the first
     * thing it does for the overwhelmingly common player -- in an ordinary world, with no doorway
     * open in front of them -- is return false on a map lookup.
     *
     * <p>Two situations, mirror images of each other:
     * <ul>
     *   <li><b>In the alley.</b> A chunk through the arch is the street and goes out as it is. Any
     *       other chunk is void here and is the player's own world there, so they are sent that
     *       world's chunk instead. A chunk the wall runs through is both: their world's chunk, then
     *       the far half of the alley's laid over it.</li>
     *   <li><b>Outside, looking in.</b> If they have been shown the street through an open arch, the
     *       chunks of the street are the alley's, not the field's, and a straddling chunk is the
     *       field's with the alley's far half laid over it.</li>
     * </ul>
     */
    public static boolean interceptChunkSend(ServerPlayer player, ServerLevel level, LevelChunk chunk) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return false;
        }
        ChunkPos pos = chunk.getPos();
        if (DiagonAlleyDimension.isAlley(level)) {
            DiagonAlleyRegistry registry = DiagonAlleyRegistry.get(level);
            if (registry.all().isEmpty()) {
                return false;                   // nothing built, nothing to be careful about
            }
            DiagonAlleyRegistry.Entry covering = registry.coveringChunk(pos.x, pos.z);
            int side = covering != null ? DiagonAlleyView.sideOf(pos.x, pos.z, covering) : -1;
            if (covering != null && side == DiagonAlleyView.BEYOND) {
                return false;                   // the street itself
            }
            if (consumeHeld(player, pos)) {
                return true;                    // they walked in with it; it is already on their screen
            }
            // Which world to show is decided by the street the player is in, not by whichever street
            // happens to cover this chunk: with alleys from two different worlds in range, a chunk
            // of the far one's footprint would otherwise be answered with the wrong world's terrain.
            // The covering entry is only used for what it is right about, which side of its wall
            // this chunk is on.
            DiagonAlleyRegistry.Entry view = registry.at(player.blockPosition());
            if (view == null) {
                view = covering != null ? covering : nearestEntry(registry, player.blockPosition());
            }
            ServerLevel source = view != null ? sourceLevel(server, view) : null;
            if (source == null) {
                return true;                    // nothing true to show; do not paint the void
            }
            boolean straddles = covering != null && side == DiagonAlleyView.STRADDLING;
            deliver(player, level, pos, source, true, straddles ? covering : null, null);
            return true;
        }

        if (shown.isEmpty()) {
            return false;
        }
        Set<BlockPos> seen = shown.get(player.getUUID());
        if (seen == null || seen.isEmpty()) {
            return false;
        }
        ServerLevel alley = DiagonAlleyDimension.level(server);
        if (alley == null) {
            return false;
        }
        String dimension = level.dimension().location().toString();
        for (DiagonAlleyRegistry.Entry entry : DiagonAlleyRegistry.get(alley).all()) {
            if (!seen.contains(entry.arch()) || !entry.sourceDimension().equals(dimension)
                    || !entry.coversChunk(pos.x, pos.z)) {
                continue;
            }
            int side = DiagonAlleyView.sideOf(pos.x, pos.z, entry);
            if (side == DiagonAlleyView.NEAR || !archIsOpen(level, entry.arch())) {
                continue;
            }
            if (side == DiagonAlleyView.BEYOND) {
                deliver(player, level, pos, alley, true, null, entry);
            } else {
                // Their own chunk goes first, by hand, because the patch has to land after it and
                // the vanilla send would only arrive once we return.
                DiagonAlleyView.sendChunk(player, level, chunk);
                deliver(player, level, pos, alley, false, entry, entry);
            }
            return true;
        }
        return false;
    }

    private static boolean consumeHeld(ServerPlayer player, ChunkPos pos) {
        if (held.isEmpty()) {
            return false;
        }
        Held record = held.get(player.getUUID());
        if (record == null) {
            return false;
        }
        if (player.server.getTickCount() - record.since > HELD_LIFETIME) {
            held.remove(player.getUUID());
            return false;
        }
        return record.chunks.remove(pos.toLong());
    }

    /**
     * Record the chunks the client of a player about to cross into the alley actually holds.
     *
     * <p>Taken from the player's chunk tracking view, not from a square of the server's view
     * distance. The tracking view is a rounded shape clamped to the distance the <em>client</em>
     * asked for, so a square over-claims: its corners, and a whole outer ring when the client runs a
     * shorter distance than the server, were never sent. Cancelling the alley's send for one of those
     * later leaves a hole where the world should be. Chunks still queued in the sender are skipped
     * for the same reason -- the client does not have them yet, and the alley's chunk map is about to
     * ask for them again.
     */
    private static void holdWhatTheClientHas(ServerPlayer player) {
        LongSet chunks = new LongOpenHashSet();
        player.getChunkTrackingView().forEach(chunk -> {
            if (!player.connection.chunkSender.isPending(chunk.toLong())) {
                chunks.add(chunk.toLong());
            }
        });
        held.put(player.getUUID(), new Held(chunks, player.server.getTickCount()));
    }

    @Nullable
    private static DiagonAlleyRegistry.Entry nearestEntry(DiagonAlleyRegistry registry, BlockPos at) {
        DiagonAlleyRegistry.Entry inside = registry.at(at);
        if (inside != null) {
            return inside;
        }
        DiagonAlleyRegistry.Entry best = null;
        double bestDist = Double.MAX_VALUE;
        for (DiagonAlleyRegistry.Entry e : registry.all()) {
            double d = e.arch().distSqr(at);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /**
     * Send a chunk of {@code fetchFrom} to the player now if it is resident, or when it is.
     *
     * <p>Package-private because {@link DiagonAlleyView} sends its chunks through here too: reading
     * a chunk with {@code getChunk} loads or generates it on the spot, on the server thread, which
     * is a stall nobody should be able to cause by walking through a door.
     *
     * @param sendWhole send the fetched chunk as it is
     * @param patch     if non-null, lay the far half of the street over what they have afterwards
     * @param mirror    the doorway that justifies this, for a player outside the alley
     */
    static void deliver(ServerPlayer player, ServerLevel playerLevel, ChunkPos pos,
                                ServerLevel fetchFrom, boolean sendWhole,
                                @Nullable DiagonAlleyRegistry.Entry patch,
                                @Nullable DiagonAlleyRegistry.Entry mirror) {
        LevelChunk got = fetchFrom.getChunkSource().getChunkNow(pos.x, pos.z);
        if (got != null) {
            finish(player, fetchFrom, got, sendWhole, patch);
            return;
        }
        if (!requested.computeIfAbsent(player.getUUID(), id -> new HashSet<>())
                .add(new Want(pos.toLong(), fetchFrom))) {
            return;                             // already waiting on this one
        }
        fetchFrom.getChunkSource().addRegionTicket(VIEW_TICKET, pos, 0, pos);
        waiting.add(new Pending(player, playerLevel, pos, fetchFrom, sendWhole, patch, mirror,
                fetchFrom.getServer().getTickCount()));
    }

    private static void finish(ServerPlayer player, ServerLevel fetchFrom, LevelChunk chunk,
                               boolean sendWhole, @Nullable DiagonAlleyRegistry.Entry patch) {
        if (sendWhole) {
            DiagonAlleyView.sendChunk(player, fetchFrom, chunk);
        }
        if (patch != null) {
            MinecraftServer server = fetchFrom.getServer();
            ServerLevel alley = server != null ? DiagonAlleyDimension.level(server) : null;
            if (alley != null) {
                DiagonAlleyView.sendFarHalf(player, alley, patch.arch(), patch.facing(),
                        chunk.getPos(), patch);
            }
        }
    }

    /** Hand over whatever chunks have finished loading since they were asked for. */
    private static void deliverWaiting(MinecraftServer server) {
        long now = server.getTickCount();
        int budget = MAX_DELIVERIES_PER_TICK;
        Iterator<Pending> it = waiting.iterator();
        // The whole queue is walked every tick even once the delivery budget is spent: the walk is
        // also what keeps the tickets of the chunks further back alive (see below).
        while (it.hasNext()) {
            Pending p = it.next();
            if (now - p.since > GIVE_UP_AFTER || !stillWanted(server, p)) {
                it.remove();
                stopWaiting(p);
                continue;
            }
            LevelChunk got = p.fetchFrom.getChunkSource().getChunkNow(p.pos.x, p.pos.z);
            if (got == null) {
                // The ticket outlives neither the delivery budget nor a slow generation, and a chunk
                // whose ticket lapses is unloaded again before anyone has read it. Renewed well
                // inside its lifespan for as long as the request stands.
                if ((now - p.since) % TICKET_RENEWAL == 0) {
                    p.fetchFrom.getChunkSource().addRegionTicket(VIEW_TICKET, p.pos, 0, p.pos);
                }
                continue;
            }
            if (budget <= 0) {
                // Resident but over budget: keep it resident until next tick's turn.
                if ((now - p.since) % TICKET_RENEWAL == 0) {
                    p.fetchFrom.getChunkSource().addRegionTicket(VIEW_TICKET, p.pos, 0, p.pos);
                }
                continue;
            }
            it.remove();
            stopWaiting(p);
            budget--;
            finish(p.player, p.fetchFrom, got, p.sendWhole, p.patch);
        }
    }

    private static void stopWaiting(Pending p) {
        Set<Want> asked = requested.get(p.player.getUUID());
        if (asked != null) {
            asked.remove(new Want(p.pos.toLong(), p.fetchFrom));
        }
    }

    /** Is the player still where the chunk was meant for, and still in need of it? */
    private static boolean stillWanted(MinecraftServer server, Pending p) {
        ServerPlayer player = p.player;
        if (server.getPlayerList().getPlayer(player.getUUID()) != player
                || player.serverLevel() != p.playerLevel) {
            return false;
        }
        int radius = server.getPlayerList().getViewDistance();
        ChunkPos at = player.chunkPosition();
        if (Math.abs(p.pos.x - at.x) > radius || Math.abs(p.pos.z - at.z) > radius) {
            return false;                       // the client has long since stopped caring
        }
        if (p.mirror != null) {
            Set<BlockPos> seen = shown.get(player.getUUID());
            return seen != null && seen.contains(p.mirror.arch())
                    && archIsOpen(p.playerLevel, p.mirror.arch());
        }
        return true;
    }

    /**
     * The registered doorway this brick belongs to, or null.
     *
     * <p>A wall has one doorway, but any brick of it can be the one somebody taps. Judging by the
     * brick -- "the arch is centred on whatever I hit" -- gives a different doorway for every brick
     * and shuts only one side of the real one. So a brick is matched to the entry whose wall it is
     * in: same plane, close enough across, near enough in height. From inside the alley the match is
     * the street the brick stands in; from anywhere else it is a street that opens out of this world.
     */
    @Nullable
    public static DiagonAlleyRegistry.Entry doorwayFor(ServerLevel level, BlockPos brick) {
        return doorwayFor(level, brick, null);
    }

    /**
     * As {@link #doorwayFor(ServerLevel, BlockPos)}, but when the whole wall the brick belongs to is
     * known the doorway must actually be <em>in</em> it.
     *
     * <p>Distance alone is not enough: a second wall built in line with an open one, within reach,
     * would be taken for the same wall and tapping it would try to close a doorway it has no part of.
     * With the wall in hand the test is exact -- the registered arch has to lie inside the extent of
     * its bricks.
     */
    @Nullable
    public static DiagonAlleyRegistry.Entry doorwayFor(ServerLevel level, BlockPos brick,
                                                       @Nullable java.util.Collection<BlockPos> wall) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        ServerLevel alley = DiagonAlleyDimension.level(server);
        if (alley == null) {
            return null;
        }
        DiagonAlleyRegistry registry = DiagonAlleyRegistry.get(alley);
        if (registry.all().isEmpty()) {
            return null;
        }
        boolean inAlley = DiagonAlleyDimension.isAlley(level);
        String dimension = inAlley ? null : level.dimension().location().toString();
        DiagonAlleyRegistry.Entry best = null;
        double bestDist = Double.MAX_VALUE;
        for (DiagonAlleyRegistry.Entry e : registry.all()) {
            if (inAlley ? !e.contains(brick) : !e.sourceDimension().equals(dimension)) {
                continue;
            }
            boolean alongX = e.facing().getAxis() == Direction.Axis.X;
            if (alongX ? brick.getX() != e.arch().getX() : brick.getZ() != e.arch().getZ()) {
                continue;                       // another wall, however close
            }
            int across = Math.abs(alongX ? brick.getZ() - e.arch().getZ()
                    : brick.getX() - e.arch().getX());
            int dy = brick.getY() - e.arch().getY();
            if (across > DOORWAY_REACH || dy < -DOORWAY_DROP || dy > DOORWAY_RISE) {
                continue;
            }
            if (wall != null && !wallHolds(wall, e, alongX)) {
                continue;
            }
            double d = (double) across * across + (double) dy * dy;
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** Does the extent of these wall bricks include the arch of this doorway? */
    private static boolean wallHolds(java.util.Collection<BlockPos> wall,
                                     DiagonAlleyRegistry.Entry entry, boolean alongX) {
        int lo = Integer.MAX_VALUE;
        int hi = Integer.MIN_VALUE;
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (BlockPos p : wall) {
            int a = alongX ? p.getZ() : p.getX();
            lo = Math.min(lo, a);
            hi = Math.max(hi, a);
            low = Math.min(low, p.getY());
            high = Math.max(high, p.getY());
        }
        int arch = alongX ? entry.arch().getZ() : entry.arch().getX();
        return arch >= lo && arch <= hi
                && entry.arch().getY() >= low - DOORWAY_DROP && entry.arch().getY() <= high;
    }

    /**
     * The arch of a doorway standing open in this brick's wall, or null.
     *
     * <p>A second way of answering "is this wall open", independent of flooding the panel. The
     * flood decides which bricks are coplanar with the one that was hit, and if it ever disagrees
     * with the wall that was actually opened -- a brick added since, a panel that measures its
     * facing differently from a corner -- the tap silently does nothing and the player is left with
     * an arch they cannot shut. Asking the registry instead cannot disagree with itself.
     *
     * <p>Only this wall's doorway counts. It used to be any doorway within twenty-four blocks, which
     * meant a neighbouring wall standing open made every brick beside it "close" instead of "arm".
     */
    @Nullable
    public static BlockPos openDoorwayNear(ServerLevel level, BlockPos brick,
                                           java.util.Collection<BlockPos> wall) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        ServerLevel alley = DiagonAlleyDimension.level(server);
        DiagonAlleyRegistry.Entry entry = doorwayFor(level, brick, wall);
        if (alley == null || entry == null) {
            return null;
        }
        ServerLevel source = sourceLevel(server, entry);
        if (archIsOpen(alley, entry.arch()) || (source != null && archIsOpen(source, entry.arch()))) {
            return entry.arch();
        }
        return null;
    }

    public static void forget(ServerPlayer player) {
        shown.remove(player.getUUID());
        moving.remove(player.getUUID());
        held.remove(player.getUUID());
        lastRescue.remove(player.getUUID());
        requested.remove(player.getUUID());
        waiting.removeIf(p -> p.player.getUUID().equals(player.getUUID()));
    }

    /** Forget every player. Called when the server stops. */
    public static void reset() {
        shown.clear();
        moving.clear();
        held.clear();
        lastRescue.clear();
        requested.clear();
        waiting.clear();
    }

    // ---------------------------------------------------------------------------------------

    /** Every tick: hand out the view through open arches, and move whoever walks into one. */
    public static void tick(MinecraftServer server) {
        if (!waiting.isEmpty()) {
            deliverWaiting(server);
        }
        ServerLevel alley = DiagonAlleyDimension.level(server);
        if (alley == null) {
            return;
        }
        DiagonAlleyRegistry registry = DiagonAlleyRegistry.get(alley);
        if (registry.all().isEmpty()) {
            return;
        }
        if (server.getTickCount() % VOID_CHECK_INTERVAL == 0) {
            rescueFromTheVoid(alley, registry);
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
        // Matched to the wall, not to the exact cell: the cell that was tapped may not be the one
        // the doorway was recorded at, and a mirror that finds nothing closes one side only.
        DiagonAlleyRegistry.Entry entry = doorwayFor(from, archCentre);
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

    /**
     * Is the doorway standing open at this arch?
     *
     * <p>Looks at the cell above as well. The arch cell is the bottom of the opening, and a street
     * built one row off from the wall -- which it once was -- puts paving there and the opening one
     * up. Answering "shut" for a doorway that is plainly open leaves nobody able to cross and,
     * worse, nobody inside able to leave, so a one-row mismatch must never be able to do that again.
     */
    private static boolean archIsOpen(ServerLevel level, BlockPos arch) {
        return cellIsOpen(level, arch) || cellIsOpen(level, arch.above());
    }

    private static boolean cellIsOpen(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DiagonArchwayBlock
                && state.getValue(DiagonArchwayBlock.PHASE) == DiagonArchwayBlock.OPEN;
    }

    /**
     * Put somebody back on the street if they have gone over the edge of it.
     *
     * <p>The alley dimension is void everywhere the street is not, and the street has a roof you can
     * reach on a broom and a perimeter you can walk off. Nothing below it ever stops a fall, so a
     * player who leaves the street at the wrong height falls forever -- visibly, since the client is
     * showing them the real world they are no longer in. Players above the street are left alone,
     * and so are spectators: flying about up there is allowed.
     */
    private static void rescueFromTheVoid(ServerLevel alley, DiagonAlleyRegistry registry) {
        for (ServerPlayer player : new ArrayList<>(alley.players())) {
            // Anyone who can fly is deliberately somewhere with no ground under them, and is not
            // falling; pulling a creative player back to the cobbles every time they go exploring
            // would make the dimension unusable to them.
            if (player.isSpectator() || player.getAbilities().flying) {
                continue;
            }
            DiagonAlleyRegistry.Entry entry = nearestEntry(registry, player.blockPosition());
            if (entry == null) {
                continue;
            }
            BlockPos arch = entry.arch();
            boolean fellThrough = player.getY() < entry.anchor().getY() - VOID_DROP;
            boolean offTheEdge = player.getY() < arch.getY() && outsideTheBox(entry, player);
            if (!fellThrough && !offTheEdge) {
                continue;
            }
            // A second rescue straight after the first means the first put them somewhere with no
            // floor (an unfinished street, say); repeating it every check would be a loop.
            long now = alley.getServer().getTickCount();
            Long last = lastRescue.get(player.getUUID());
            if (last != null && now - last < RESCUE_COOLDOWN) {
                continue;
            }
            lastRescue.put(player.getUUID(), now);
            BlockPos back = standingSpotInside(alley, entry);
            player.stopRiding();
            player.connection.teleport(back.getX() + 0.5D, back.getY(), back.getZ() + 0.5D,
                    entry.facing().toYRot(), 0.0F);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.fallDistance = 0.0F;
            player.hurtMarked = true;
            player.displayClientMessage(Component.literal(
                    "The alley's magic catches you and sets you back on the cobbles."), true);
            LOGGER.info("{} fell out of the alley at {}; set back at {}",
                    player.getGameProfile().getName(), player.blockPosition(), back);
        }
    }

    /**
     * Somewhere just inside the arch where a player can actually stand: two clear cells over a
     * floor that holds weight.
     *
     * <p>Searched for rather than computed, because the arithmetic answer (three along from the arch,
     * at arch height) is only right for a finished street built at the current offset. A street that
     * was reused from an older build can sit a row off, and one that was never finished may have no
     * floor at all there. Searches outward along the street and up and down a little at each step, and
     * if there is truly nothing falls back to the arithmetic answer rather than to nothing.
     */
    private static BlockPos standingSpotInside(ServerLevel alley, DiagonAlleyRegistry.Entry entry) {
        BlockPos arch = entry.arch();
        int[] alongs = {3, 4, 2, 5, 6, 7};
        int[] rises = {0, 1, -1, 2, -2, 3, -3, 4};
        for (int along : alongs) {
            BlockPos column = arch.relative(entry.facing(), along);
            if (!alley.isLoaded(column)) {
                continue;
            }
            for (int rise : rises) {
                BlockPos feet = new BlockPos(column.getX(), arch.getY() + rise, column.getZ());
                if (canStandAt(alley, feet)) {
                    return feet;
                }
            }
        }
        LOGGER.warn("No standable cell found inside the arch at {}; using the nominal spot", arch);
        return new BlockPos(arch.relative(entry.facing(), 3).getX(), arch.getY(),
                arch.relative(entry.facing(), 3).getZ());
    }

    private static boolean canStandAt(ServerLevel level, BlockPos feet) {
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)
                && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet).getFluidState().isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static boolean outsideTheBox(DiagonAlleyRegistry.Entry entry, ServerPlayer player) {
        BlockPos a = entry.anchor();
        return player.getX() < a.getX() - VOID_MARGIN
                || player.getX() >= a.getX() + entry.sizeX() + VOID_MARGIN
                || player.getZ() < a.getZ() - VOID_MARGIN
                || player.getZ() >= a.getZ() + entry.sizeZ() + VOID_MARGIN;
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

        // Going in, the client is holding the whole of the world it is leaving; coming out, the
        // alley's chunk map has nothing it needs to be told to withhold. Either way the old record
        // is wrong the moment they move.
        if (target.dimension().equals(DiagonAlleyDimension.KEY)) {
            holdWhatTheClientHas(player);
        } else {
            held.remove(player.getUUID());
        }
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
