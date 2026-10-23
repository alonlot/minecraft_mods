package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The First Task memory's trigger: every player who hurt a (wild) Hungarian Horntail within the last minute, and was
 * near her when she died, gets the memory once, when her death animation has finished. The killer does not matter, so
 * a wolf, fire or another player finishing her counts for everyone who damaged her. Server-side only: it reads two
 * Forge events and reaches no client class.
 *
 * <p>The player is pending ({@link #TAG_PENDING}, saved with them) from the kill until the memory starts, so a player
 * who is busy (in another memory, in its wake, dead) or gone (logged out) gets it as soon as they are free. The seen
 * flag is set only when the chain ended with END in its last scene; leaving early or a yank leaves it unseen, and the
 * next kill plays it again.
 */
public final class FirstTaskMemory {
    public static final String TAG_SEEN = "FirstTaskMemorySeen";
    public static final String TAG_PENDING = "FirstTaskMemoryPending";
    public static final String SCENE = "first_task";
    public static final String LAST = "first_task_egg";
    /** How long a hit counts: records older than this are not eligible and are pruned. */
    public static final long WINDOW_TICKS = 1200;
    /** The farthest a damager may be from the dying dragon. */
    public static final double RANGE = 64.0;
    /** Vanilla's 20-tick death animation, then the removal. */
    public static final int DEATH_DELAY = 21;
    /** Ticks between tries while a pending player is busy. */
    public static final int RETRY_TICKS = 20;
    /** Ticks after a login before a pending player is tried. */
    public static final int LOGIN_DELAY = 100;
    private static final String PERSISTED = "PlayerPersisted";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Per dragon, the game time each player last damaged her. */
    private static final Map<UUID, Map<UUID, Long>> RECORDS = new ConcurrentHashMap<>();
    /** Pending players and the game time they are next tried. */
    private static final Map<UUID, Long> NEXT_TRY = new ConcurrentHashMap<>();
    /** Players whose memory this class started and that are not yet home. */
    private static final Set<UUID> STARTED = ConcurrentHashMap.newKeySet();
    /** Those of them that have entered the last scene: only an END there counts as the memory watched. */
    private static final Set<UUID> IN_LAST = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_MISSING = ConcurrentHashMap.newKeySet();
    private static boolean listening;

    private FirstTaskMemory() {
    }

    /** Hooks the damage, death, tick, login, logout and stop events and the chain. Repeat calls do nothing. */
    public static synchronized void register() {
        if (listening) return;
        listening = true;
        PensieveSessions.addHomeListener(FirstTaskMemory::onHome);
        PensieveSessions.addSceneEnterListener(LAST, (player, session) -> {
            if (STARTED.contains(player.getUUID())) IN_LAST.add(player.getUUID());
        });
        var bus = MinecraftForge.EVENT_BUS;
        bus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, false, LivingDamageEvent.class,
                FirstTaskMemory::onDamage);
        bus.addListener(FirstTaskMemory::onDeathEvent);
        bus.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) tick(e.getServer());
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) onLogin(p);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) onLogout(p);
        });
        bus.addListener((ServerStoppedEvent e) -> reset());
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

    public static boolean isPending(Player player) {
        return persisted(player).getBoolean(TAG_PENDING);
    }

    // ---- Damage and death ---------------------------------------------------------------------------------------

    private static void onDamage(LivingDamageEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0f) return;
        if (!(event.getEntity() instanceof HungarianHorntailEntity dragon) || dragon instanceof MemoryHorntailEntity
                || dragon.level().isClientSide()) return;
        // getEntity() is the one that caused the damage: the shooter of an arrow, the caster of a spell. A pet
        // resolves to the pet, so a wolf's bites do not count for its owner.
        Entity cause = event.getSource().getEntity();
        if (cause instanceof ServerPlayer p) recordDamage(dragon, p, dragon.level().getGameTime());
    }

    private static void onDeathEvent(LivingDeathEvent event) {
        if (event.getEntity() instanceof HungarianHorntailEntity dragon && !(dragon instanceof MemoryHorntailEntity)
                && !dragon.level().isClientSide()) {
            onDeath(dragon);
        }
    }

    /** {@code p} damaged {@code dragon} at {@code gameTime}. A memory puppet is never recorded. */
    public static void recordDamage(HungarianHorntailEntity dragon, ServerPlayer p, long gameTime) {
        if (dragon instanceof MemoryHorntailEntity) return;
        prune(gameTime);
        RECORDS.computeIfAbsent(dragon.getUUID(), k -> new ConcurrentHashMap<>()).put(p.getUUID(), gameTime);
    }

    /**
     * The dragon died: every player who damaged her within {@link #WINDOW_TICKS}, in her level and within
     * {@link #RANGE}, and who has not seen the memory, becomes pending and is tried in {@link #DEATH_DELAY} ticks.
     */
    public static void onDeath(HungarianHorntailEntity dragon) {
        if (dragon instanceof MemoryHorntailEntity || !(dragon.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        prune(now);
        Map<UUID, Long> hits = RECORDS.remove(dragon.getUUID());
        if (hits == null) return;
        for (Map.Entry<UUID, Long> hit : hits.entrySet()) {
            if (now - hit.getValue() > WINDOW_TICKS) continue;
            ServerPlayer p = online(level.getServer(), hit.getKey());
            if (p == null || p.level() != level || p.distanceToSqr(dragon) > RANGE * RANGE || isSeen(p)) continue;
            persisted(p).putBoolean(TAG_PENDING, true);
            NEXT_TRY.merge(p.getUUID(), now + DEATH_DELAY, Math::max);
        }
    }

    private static void prune(long now) {
        RECORDS.entrySet().removeIf(e -> {
            e.getValue().values().removeIf(t -> now - t > WINDOW_TICKS);
            return e.getValue().isEmpty();
        });
    }

    private static ServerPlayer online(MinecraftServer server, UUID id) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(id)) return p;
        }
        return null;
    }

    // ---- Starting -----------------------------------------------------------------------------------------------

    /**
     * Starts the memory for {@code p} if they are pending and free. False keeps them pending when they are busy
     * (dead, inside a memory, in a wake, no scene yet) and drops it for good when they have seen it or are not
     * pending.
     */
    public static boolean tryStart(ServerPlayer p) {
        if (isSeen(p)) {
            persisted(p).remove(TAG_PENDING);
            return false;
        }
        if (!isPending(p)) return false;
        if (!p.isAlive() || p.hasDisconnected() || PensieveSessions.isViewer(p) || PensieveSessions.wakeInProgress(p)) {
            return false;
        }
        if (!PensieveSessions.hasScene(SCENE)) {
            if (WARNED_MISSING.add(SCENE)) LOGGER.warn("First Task memory: scene '{}' is not loaded; waiting", SCENE);
            return false;
        }
        STARTED.add(p.getUUID());
        IN_LAST.remove(p.getUUID());
        if (!PensieveSessions.startSolo(p, SCENE)) {
            STARTED.remove(p.getUUID());
            return false;
        }
        persisted(p).remove(TAG_PENDING);
        return true;
    }

    /** Every server tick (END phase): tries the pending players whose time has come. */
    public static void tick(MinecraftServer server) {
        if (NEXT_TRY.isEmpty() && RECORDS.isEmpty()) return;
        long now = server.overworld().getGameTime();
        if (now % 200 == 0) prune(now);
        for (Map.Entry<UUID, Long> e : new ArrayList<>(NEXT_TRY.entrySet())) {
            if (now < e.getValue()) continue;
            ServerPlayer p = online(server, e.getKey());
            if (p == null) {
                NEXT_TRY.remove(e.getKey());
            } else if (tryStart(p) || !isPending(p)) {
                NEXT_TRY.remove(e.getKey());
            } else {
                NEXT_TRY.put(e.getKey(), now + RETRY_TICKS);
            }
        }
    }

    private static void onHome(ServerPlayer player, String chainRoot, ExitReason reason, boolean sawStart) {
        // Only a memory this class started: a preview or a command of the same scene leaves the flags alone.
        if (!STARTED.remove(player.getUUID()) || !SCENE.equals(chainRoot)) {
            IN_LAST.remove(player.getUUID());
            return;
        }
        if (IN_LAST.remove(player.getUUID()) && reason == ExitReason.END) markSeen(player);
    }

    /** A login: a pending player is tried again after {@link #LOGIN_DELAY} ticks. */
    public static void onLogin(ServerPlayer p) {
        if (isPending(p) && !isSeen(p)) NEXT_TRY.put(p.getUUID(), p.level().getGameTime() + LOGIN_DELAY);
    }

    /** A logout keeps the pending flag (it is saved with the player) and forgets the rest. */
    public static void onLogout(ServerPlayer p) {
        NEXT_TRY.remove(p.getUUID());
        STARTED.remove(p.getUUID());
        IN_LAST.remove(p.getUUID());
    }

    /** The server is gone, or a test starts clean: nothing here may outlive it. */
    public static void reset() {
        RECORDS.clear();
        NEXT_TRY.clear();
        STARTED.clear();
        IN_LAST.clear();
    }

    /** Gametests: whether the player has a record against any dragon. */
    public static boolean hasRecord(UUID player) {
        return RECORDS.values().stream().anyMatch(m -> m.containsKey(player));
    }
}
