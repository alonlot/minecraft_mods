package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.sorting.HogwartsHouse;
import com.hogwartsmagic.sorting.HousePerks;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Flying memory's trigger: the first time a sorted player mounts a broom, the memory plays (a chain
 * {@code flying_<house>} ... {@code flying_<house>_snitch}, which wakes), and they come home on the same broom.
 * Common code: the hook is {@code BroomEntity.interact} on the server, and the memory is started by the server.
 *
 * <p>Every player gets it once, old saves included; the seen flag is set only when the chain ended with END in the
 * snitch scene, so leaving early, a yank, a death or a disconnect plays it again on the next mount. An unsorted
 * player just mounts: the scene is chosen by the viewer's house.
 *
 * <p>While the player is away the broom is parked: held still (no gravity, no motion, nobody else can mount it) and
 * its chunk is kept loaded by a region ticket, so it cannot unload and lose the hold. At home the player is put
 * back on it if it is still within {@link #HOME_RANGE} blocks, otherwise they stand where they mounted.
 */
public final class FlyingMemory {
    public static final String TAG_SEEN = "FlyingMemorySeen";
    public static final String SNITCH_SUFFIX = "_snitch";
    /** The farthest the broom may be from the player at home for them to be put back on it. */
    public static final double HOME_RANGE = 64.0;
    private static final String PERSISTED = "PlayerPersisted";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Keeps the parked broom's chunk entity-ticking for the whole memory (distance 2 is ticket level 31), like the
     * basin ticket does for the body: a solo memory has no basin, so nothing else would hold the mount point.
     */
    static final TicketType<ChunkPos> BROOM_TICKET = TicketType.create("hogwartsmagic_flying_broom",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int TICKET_DISTANCE = 2;

    /** One memory this class started: the parked broom and when (if at all) the player is due back on it. */
    private static final class Hold {
        final ServerPlayer player;
        final UUID broom;
        final ServerLevel level;
        final ChunkPos chunk;
        final String root;
        /** Game time the player is put back on the broom (after a wake), or -1 while still inside. */
        long remountAt = -1;

        Hold(ServerPlayer player, BroomEntity broom, ServerLevel level, String root) {
            this.player = player;
            this.broom = broom.getUUID();
            this.level = level;
            this.chunk = broom.chunkPosition();
            this.root = root;
        }
    }

    private record Pending(ServerPlayer player, BroomEntity broom) {
    }

    private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();
    /** Players whose chain reached the snitch scene: only an END there counts as the memory watched whole. */
    private static final Set<UUID> IN_SNITCH = ConcurrentHashMap.newKeySet();
    /** Mounts to start on the next server tick (the click that mounted has finished by then). */
    private static final List<Pending> PENDING = new CopyOnWriteArrayList<>();

    private static boolean listening;
    private static boolean warnedMissing;

    private FlyingMemory() {
    }

    /** The first scene of the chain for a viewer of {@code h}; its last is this plus {@link #SNITCH_SUFFIX}. */
    public static String sceneFor(HogwartsHouse h) {
        return "flying_" + h.name().toLowerCase(Locale.ROOT);
    }

    /** Hooks the home callback, the snitch scene and the death and shutdown events. Repeat calls do nothing. */
    public static synchronized void register() {
        if (listening) return;
        listening = true;
        PensieveSessions.addHomeListener(FlyingMemory::onHome);
        for (HogwartsHouse h : HogwartsHouse.values()) {
            PensieveSessions.addSceneEnterListener(sceneFor(h) + SNITCH_SUFFIX, (player, session) -> {
                if (HOLDS.containsKey(player.getUUID())) IN_SNITCH.add(player.getUUID());
            });
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(FlyingMemory::onDeath);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(FlyingMemory::onServerStopped);
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(PERSISTED)) root.put(PERSISTED, new CompoundTag());
        return root.getCompound(PERSISTED);
    }

    public static boolean isSeen(Player player) {
        return persisted(player).getBoolean(TAG_SEEN);
    }

    public static void markSeen(Player player) {
        persisted(player).putBoolean(TAG_SEEN, true);
    }

    /**
     * Whether {@code player} is the server's own player for their id. Forge 1.21 has no FakePlayer class, so a machine
     * of another mod (not in the player list) counts as fake, as {@code BrewAdvancements.grant} does.
     */
    public static boolean isRealPlayer(ServerPlayer player) {
        return player.server.getPlayerList().getPlayer(player.getUUID()) == player;
    }

    /** The hook: a real player just mounted {@code broom}. The memory starts on the next server tick. */
    public static void scheduleStart(ServerPlayer player, BroomEntity broom) {
        PENDING.add(new Pending(player, broom));
    }

    /**
     * The player mounted {@code broom}. True when the memory started (they are dismounted and inside); false when
     * they just keep flying: already seen, not sorted, no longer on that broom, already inside, or no scene (logged
     * once).
     */
    public static boolean tryStart(ServerPlayer player, BroomEntity broom) {
        if (player.getVehicle() != broom || broom.isRemoved() || !(broom.level() instanceof ServerLevel level)) {
            return false;
        }
        if (isSeen(player)) return false;
        HogwartsHouse house = HousePerks.getHouse(player);
        if (house == null) return false;
        UUID id = player.getUUID();
        if (PensieveSessions.isViewer(player) || HOLDS.containsKey(id)) return false;
        String root = sceneFor(house);
        Hold hold = new Hold(player, broom, level, root);
        // Parked before the start: the start dismounts the player, and the broom must already refuse everyone.
        level.getChunkSource().addRegionTicket(BROOM_TICKET, hold.chunk, TICKET_DISTANCE, hold.chunk);
        broom.holdFor(id);
        HOLDS.put(id, hold);
        IN_SNITCH.remove(id);
        if (!PensieveSessions.startSolo(player, root)) {
            HOLDS.remove(id);
            broom.releaseHold();
            level.getChunkSource().removeRegionTicket(BROOM_TICKET, hold.chunk, TICKET_DISTANCE, hold.chunk);
            if (!warnedMissing) {
                warnedMissing = true;
                LOGGER.warn("Flying memory: could not start '{}'; the player just mounts", root);
            }
            return false;
        }
        return true;
    }

    private static void onHome(ServerPlayer player, String chainRoot, ExitReason reason, boolean sawStart) {
        Hold hold = HOLDS.get(player.getUUID());
        // Only a memory this class started: a preview or a command of the same scene leaves the player alone.
        if (hold == null || !hold.root.equals(chainRoot)) return;
        // Only the snitch scene ends the chain with END; leaving, a yank or an early end keeps the flag unset.
        if (IN_SNITCH.remove(player.getUUID()) && reason == ExitReason.END) markSeen(player);
        if (!player.isAlive()) {
            release(player.getUUID());
            return;
        }
        long now = player.level().getGameTime();
        // A wake (sent just before the move home, same tick) plays out before they are back on the broom.
        boolean woke = PensieveSessions.lastWakeForTests(player.getUUID()) >= now - 1;
        if (woke) {
            hold.remountAt = now + PensieveSessions.WAKE_DONE_TICKS;
        } else {
            remount(hold);
        }
    }

    /** Every server tick (END phase): starts the mounts of the last tick, then remounts those whose wake is over. */
    public static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty()) {
            List<Pending> due = new ArrayList<>(PENDING);
            PENDING.clear();
            for (Pending p : due) {
                if (p.player().isAlive() && !p.player().hasDisconnected()) tryStart(p.player(), p.broom());
            }
        }
        if (HOLDS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Hold h : new ArrayList<>(HOLDS.values())) {
            if (h.remountAt < 0 || now < h.remountAt) continue;
            if (h.player.isAlive()) remount(h);
            else release(h.player.getUUID());
        }
    }

    /** Back on the parked broom if it is still near; either way the hold is released. */
    private static void remount(Hold hold) {
        ServerPlayer player = hold.player;
        BroomEntity broom = release(player.getUUID());
        if (broom == null || !player.isAlive() || PensieveSessions.isViewer(player) || player.getVehicle() != null) return;
        if (player.level().dimension() != hold.level.dimension()) return;
        if (player.distanceToSqr(broom) > HOME_RANGE * HOME_RANGE) return;
        player.startRiding(broom);
    }

    /** Drops the player's hold, the broom's park and the chunk ticket; the broom, or null if it is gone. */
    private static BroomEntity release(UUID id) {
        IN_SNITCH.remove(id);
        Hold hold = HOLDS.remove(id);
        if (hold == null) return null;
        hold.level.getChunkSource().removeRegionTicket(BROOM_TICKET, hold.chunk, TICKET_DISTANCE, hold.chunk);
        Entity e = hold.level.getEntity(hold.broom);
        if (e instanceof BroomEntity broom && !broom.isRemoved()) {
            broom.releaseHold();
            return broom;
        }
        return null;
    }

    /** Died during the memory or the wake after it: the broom is released and the player is not remounted. */
    private static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) release(player.getUUID());
    }

    /** The server is gone: nothing here may outlive it (an integrated server restarts in the same process). */
    private static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        HOLDS.clear();
        IN_SNITCH.clear();
        PENDING.clear();
    }

    /** A logout releases the broom; the flag is saved with the player. */
    public static void onLogout(ServerPlayer player) {
        release(player.getUUID());
        PENDING.removeIf(p -> p.player() == player);
    }

    /** Gametests: whether a memory this class started holds a broom for the player. */
    public static boolean isHolding(UUID id) {
        return HOLDS.containsKey(id);
    }

    /**
     * Gametests: whether the player's parked broom has its chunk ticket. The ticket is taken with the hold and dropped
     * with it, in one place each ({@link #tryStart}, {@link #release}); the chunk's own load level cannot tell it
     * apart from the player's.
     */
    public static boolean holdsTicket(UUID id) {
        return HOLDS.containsKey(id);
    }
}
