package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.item.HogwartsInviteItem;
import com.hogwartsmagic.network.SpellPackets;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Letter memory's trigger: the first time a new player opens their Hogwarts acceptance letter, the memory
 * {@code letter} (a chain ending in {@code letter_hut}, which wakes) plays in the Pensieve, and the real letter
 * screen opens when they are home. Common code: the screen is opened by a packet from the server.
 *
 * <p>The pending flag is set only for a player who is handed the first-year invite, so old saves never have it and
 * their letter opens directly. It is cleared only when the whole chain was watched to its end; leaving early or
 * being yanked out opens the letter but keeps the flag, so the memory plays again on the next use.
 */
public final class LetterMemory {
    public static final String SCENE = "letter";
    public static final String HUT_SCENE = "letter_hut";
    public static final String TAG_PENDING = "LetterMemoryPending";
    private static final String PERSISTED = "PlayerPersisted";
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Players home from the memory whose letter screen opens at the game time given (a wake plays out first). */
    private static final Map<UUID, Due> DUE = new ConcurrentHashMap<>();
    /** The recipient printed on the letter that started each player's memory. */
    private static final Map<UUID, String> RECIPIENT = new ConcurrentHashMap<>();
    /** Gametests: how many times each player's letter screen has been opened by this class. */
    private static final Map<UUID, Integer> OPENED = new ConcurrentHashMap<>();

    /** Players whose chain reached {@code letter_hut}: only an END there counts as the memory watched whole. */
    private static final java.util.Set<UUID> IN_HUT = ConcurrentHashMap.newKeySet();
    /** Players who died during the memory (or its wake): the letter opens when they have respawned. */
    private static final Map<UUID, String> AFTER_RESPAWN = new ConcurrentHashMap<>();

    private static boolean listening;
    private static boolean warnedMissing;

    private record Due(String recipient, long time) {
    }

    private LetterMemory() {
    }

    /** Hooks the home callback. Called once at common setup; repeat calls do nothing. */
    public static synchronized void register() {
        if (listening) return;
        listening = true;
        PensieveSessions.addHomeListener(LetterMemory::onHome);
        PensieveSessions.addSceneEnterListener(HUT_SCENE, (player, session) -> {
            if (RECIPIENT.containsKey(player.getUUID())) IN_HUT.add(player.getUUID());
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(LetterMemory::onDeath);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(LetterMemory::onRespawn);
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(PERSISTED)) root.put(PERSISTED, new CompoundTag());
        return root.getCompound(PERSISTED);
    }

    /** Marks a new player as owed the memory. The only caller is the first-year invite delivery. */
    public static void markPending(ServerPlayer player) {
        persisted(player).putBoolean(TAG_PENDING, true);
    }

    public static boolean isPending(Player player) {
        return persisted(player).getBoolean(TAG_PENDING);
    }

    private static void clearPending(Player player) {
        persisted(player).remove(TAG_PENDING);
    }

    /**
     * The letter was used. True when the memory started or is already running or building for this player (the
     * screen must not open underneath); false when the letter should open as usual: no flag, or the memory cannot
     * start (a missing scene is logged once).
     */
    public static boolean tryStart(ServerPlayer player, ItemStack letter) {
        if (PensieveSessions.isViewer(player) || DUE.containsKey(player.getUUID())) return true;
        if (!isPending(player)) return false;
        String recipient = HogwartsInviteItem.getRecipient(letter);
        if (!PensieveSessions.startSolo(player, SCENE)) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOGGER.warn("Letter memory: could not start '{}'; the letter opens directly", SCENE);
            }
            return false;
        }
        RECIPIENT.put(player.getUUID(), recipient);
        IN_HUT.remove(player.getUUID());
        return true;
    }

    private static void onHome(ServerPlayer player, String chainRoot, ExitReason reason, boolean sawStart) {
        if (!SCENE.equals(chainRoot)) return;
        // Only a memory this class started: a preview or a command of the same scene leaves the player alone.
        String recipient = RECIPIENT.remove(player.getUUID());
        if (recipient == null) return;
        // Only the last scene ends the chain with END; leaving or a yank mid-chain keeps the flag.
        // and only the last one counts: a chain that ended early (a missing next scene) never reached the hut.
        if (IN_HUT.remove(player.getUUID()) && reason == ExitReason.END) clearPending(player);
        long now = player.level().getGameTime();
        // A wake (sent just before the move home, same tick) plays out before the letter appears.
        boolean woke = PensieveSessions.lastWakeForTests(player.getUUID()) >= now - 1;
        if (woke) {
            DUE.put(player.getUUID(), new Due(recipient, now + PensieveSessions.WAKE_DONE_TICKS));
        } else {
            open(player, recipient);
        }
    }

    /**
     * Died during the memory or the wake after it: the tag stays, the recipient is dropped (so a later preview of
     * the scene is not mistaken for the real trigger) and the letter opens once the player has respawned.
     */
    private static void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        String recipient = RECIPIENT.remove(id);
        Due due = DUE.remove(id);
        IN_HUT.remove(id);
        if (recipient == null && due != null) recipient = due.recipient();
        if (recipient != null) AFTER_RESPAWN.put(id, recipient);
    }

    private static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String recipient = AFTER_RESPAWN.remove(player.getUUID());
        if (recipient != null) open(player, recipient);
    }

    /** Every server tick (END phase): opens the letters whose wake is over. */
    public static void tick(MinecraftServer server) {
        if (DUE.isEmpty()) return;
        long now = server.overworld().getGameTime();
        for (Iterator<Map.Entry<UUID, Due>> it = DUE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Due> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive()) {
                it.remove();
            } else if (now >= e.getValue().time()) {
                it.remove();
                open(p, e.getValue().recipient());
            }
        }
    }

    private static void open(ServerPlayer player, String recipient) {
        OPENED.merge(player.getUUID(), 1, Integer::sum);
        SpellPackets.CHANNEL.send(new SpellPackets.OpenHogwartsInviteScreenPacket(recipient),
                PacketDistributor.PLAYER.with(player));
    }

    /** A logout forgets this player's transient state; the flag is saved with the player. */
    public static void onLogout(ServerPlayer player) {
        DUE.remove(player.getUUID());
        RECIPIENT.remove(player.getUUID());
        AFTER_RESPAWN.remove(player.getUUID());
        IN_HUT.remove(player.getUUID());
    }

    /** Gametests: whether a memory this class started still holds a recipient for the player. */
    public static boolean hasRecipient(UUID id) {
        return RECIPIENT.containsKey(id);
    }

    /** Gametests: whether the letter screen is waiting on a wake. */
    public static boolean awaitingOpen(UUID id) {
        return DUE.containsKey(id);
    }

    /** Gametests: how many times the letter screen was opened for this player. */
    public static int openedCount(UUID id) {
        return OPENED.getOrDefault(id, 0);
    }
}
