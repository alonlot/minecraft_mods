package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.pensieve.net.PensieveEnterPacket;
import com.hogwartsmagic.pensieve.net.PensieveExitPacket;
import com.hogwartsmagic.pensieve.capture.IslandBounds;
import com.hogwartsmagic.pensieve.capture.Recording;
import com.hogwartsmagic.pensieve.capture.RecordingScene;
import com.hogwartsmagic.pensieve.capture.RecordingStore;
import com.hogwartsmagic.pensieve.net.PensieveFadePacket;
import com.hogwartsmagic.pensieve.net.PensieveGhostsPacket;
import com.hogwartsmagic.pensieve.net.PensieveNet;
import com.hogwartsmagic.pensieve.net.PensieveRecordingPacket;
import com.hogwartsmagic.pensieve.net.PensieveStartPacket;
import com.hogwartsmagic.pensieve.net.PensieveThudPacket;
import com.hogwartsmagic.pensieve.net.PensieveWakePacket;
import com.hogwartsmagic.pensieve.scene.ItemText;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneException;
import com.hogwartsmagic.pensieve.scene.SceneValidator;
import com.hogwartsmagic.pensieve.scene.SessionClock;
import com.hogwartsmagic.util.PlayerMagicData;
import com.hogwartsmagic.util.SeamlessMove;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The server side of the Pensieve: diving, crossing into the memory dimension, building the set over ticks,
 * playing the scene's block events, leaving for any reason, shared viewing, op previews and recovery after a
 * logout or a crash. Everything here runs on the server thread; the client only hears about it through the
 * enter/start/exit packets.
 *
 * <p><b>Leftovers after a restart.</b> Which blocks a set placed lives only in memory, so a crash or a stop
 * mid-memory would leave a set standing in its slot. Every slot's placed-block bounding box is therefore kept in
 * a {@link RegionData} saved on the memory level, grown as blocks go in and dropped once the slot is clear. The
 * first time this class sees a memory level in a server run, it reserves every slot still listed there and clears
 * its box over ticks (skipping air-only chunk sections, so only real leftovers cost anything), then frees it.
 *
 * <p><b>Test seam.</b> The GameTestServer builds no {@code hogwartsmagic:memory} level. {@link #overrideForTests}
 * makes a given level stand in for it and resolves scene ids through a given function; production never calls it.
 */
public final class PensieveSessions {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int SWALLOW_TICKS = 30;
    /** How long a dive waits for a recording to be encoded for the client before giving up. */
    private static final int BLOB_WAIT_TICKS = 400;
    public static final String RETURN_TAG = "PensieveReturn";
    private static final int MAX_RADIUS = 200;
    /**
     * Holds the basin's chunks entity-ticking for a whole session, so the body stays live (mobs can reach it, it
     * can be hurt) even with nobody else nearby. Distance 3 makes the basin chunk and the ring around it
     * entity-ticking (ticket levels 30 and 31), which covers the 16 blocks the body lures monsters from.
     */
    static final TicketType<ChunkPos> BASIN_TICKET = TicketType.create("hogwartsmagic_pensieve_basin",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int BASIN_TICKET_DISTANCE = 3;
    /**
     * A {@link #handOver} asked for mid-scene fades the scene to black over this many ticks before the viewers move
     * (the client's {@code PensieveTimeline.CHAIN_FADE_TICKS}, the fade a chained scene does at its length).
     */
    public static final int HANDOVER_FADE_TICKS = 20;
    /** The {@code wake} end action: how long the eyes take to open on the real world once the return is over. */
    public static final int WAKE_TICKS = 40;
    /** Ticks the client holds its return black after a move home (its {@code PensieveTimeline.RETURN_TICKS}). */
    public static final int RETURN_TICKS = 15;
    /** Ticks from the move home until that return is over and the world shows again, with a little slack. */
    public static final int RETURN_DONE_TICKS = RETURN_TICKS + 5;
    /**
     * Ticks from the move home until a {@code wake} has played out on the client: its return (the camera rising out
     * of the basin, {@code PensieveTimeline.RETURN_TICKS}, held black) plus {@link #WAKE_TICKS}, and a little slack.
     * Whatever should come after the wake (the Sorting's house reveal) waits this long after the home callback.
     */
    public static final int WAKE_DONE_TICKS = RETURN_TICKS + WAKE_TICKS + 5;

    private static final List<Session> SESSIONS = new ArrayList<>();
    private static final Map<UUID, Session> BY_VIEWER = new ConcurrentHashMap<>();
    private static final List<Clearing> CLEARING = new ArrayList<>();
    /** When a wake was last sent, per player, for the gametests; dropped at logout. */
    private static final Map<UUID, Long> WAKES = new ConcurrentHashMap<>();
    /** Sessions a hand-over moved viewers into during the current {@link #tick}; skipped for the rest of it. */
    private static final java.util.Set<Session> HANDED_THIS_TICK = new java.util.HashSet<>();
    private static RegionAllocator allocator = new RegionAllocator();
    @Nullable private static MinecraftServer initServer;
    @Nullable private static ServerLevel initLevel;

    /** True while this class is moving a viewer itself, so its own dimension changes are not cancelled. */
    private static boolean ownMove;
    /** True only inside {@link #forwardDamage}: the ghost protection lets the body damage through. */
    @Nullable private static UUID forwarding;
    @Nullable private static ServerLevel testLevel;
    @Nullable private static Function<String, Optional<MemoryRegistry.LoadedScene>> testScenes;
    /** Gametests only: the per-tick block budget in place of the config's (0 stalls every build); -1 for none. */
    private static int testBudget = -1;

    private PensieveSessions() {
    }

    // ---- Test seam ------------------------------------------------------------------------------------------

    /**
     * Gametests only: use {@code level} as the memory level and {@code scenes} to resolve scene ids. The
     * GameTestServer has no memory dimension; without this every dive there is refused.
     */
    public static void overrideForTests(ServerLevel level, Function<String, Optional<MemoryRegistry.LoadedScene>> scenes) {
        testLevel = level;
        testScenes = scenes;
    }

    /** Gametests only: a fixed per-tick block budget ({@code 0} stalls every build and clear); negative restores it. */
    public static void setBuildBudgetForTests(int blocks) {
        testBudget = blocks < 0 ? -1 : blocks;
    }

    /** Gametests: pretends a wake was sent to {@code player} at {@code time}. */
    public static void noteWakeForTests(UUID player, long time) {
        WAKES.put(player, time);
    }

    /** Whether a wake sent to {@code player} is still playing out on their client (any memory's). */
    public static boolean wakeInProgress(Player player) {
        Long at = WAKES.get(player.getUUID());
        return at != null && player.level().getGameTime() - at < WAKE_DONE_TICKS;
    }

    /** Whether a scene with this id is loaded (a start would not be refused for lack of it). */
    public static boolean hasScene(String sceneId) {
        return lookup(sceneId).isPresent();
    }

    /** Gametests: the game time a wake was last sent to {@code player} (just before their move home), or -1. */
    public static long lastWakeForTests(UUID player) {
        return WAKES.getOrDefault(player, -1L);
    }

    /** Gametests: whether the allocator still holds {@code slot} (a session, a standby or a clear owns it). */
    public static boolean slotInUse(int slot) {
        return allocator.isUsed(slot);
    }

    /** The level memories play in: the memory dimension, or the test stand-in; null if neither exists. */
    @Nullable
    public static ServerLevel memoryLevel(MinecraftServer server) {
        if (testLevel != null && testLevel.getServer() == server) return testLevel;
        return PensieveDimension.level(server);
    }

    // ---- Queries --------------------------------------------------------------------------------------------

    public static boolean isViewer(Player player) {
        return player != null && BY_VIEWER.containsKey(player.getUUID());
    }

    public static Optional<Session> sessionOf(Player player) {
        return player == null ? Optional.empty() : Optional.ofNullable(BY_VIEWER.get(player.getUUID()));
    }

    /** True for a viewer inside (not swallowing or leaving) a recorded memory: free to fly through its island. */
    public static boolean isFreeGhost(Player player) {
        if (player == null) return false;
        Session s = BY_VIEWER.get(player.getUUID());
        if (s == null || s.recording == null) return false;
        Session.Viewer v = s.viewers().get(player.getUUID());
        return v != null && v.stage == Session.Viewer.Stage.INSIDE;
    }

    /** True while anyone is inside the memory playing from this basin (for the take-out rule). */
    public static boolean anyoneInside(Level level, BlockPos basin) {
        Session s = sessionAt(level.dimension(), basin);
        return s != null && !s.viewers().isEmpty();
    }

    /** For {@link PensieveEvents}: true while the damage of a body is being applied to its viewer. */
    static boolean isForwarding(Player player) {
        return forwarding != null && forwarding.equals(player.getUUID());
    }

    /** For {@link PensieveEvents}: a viewer's dimension change is only allowed when this class makes it. */
    static boolean isOwnMove() {
        return ownMove;
    }

    @Nullable
    private static Session sessionAt(ResourceKey<Level> dim, BlockPos pos) {
        for (Session s : SESSIONS) {
            if (s.at(dim, pos)) return s;
        }
        return null;
    }

    /** Told when a viewer is back in the real world. Registered once at mod setup; never cleared by a restart. */
    private static final List<ViewerHomeListener> HOME_LISTENERS = new ArrayList<>();

    public static void addHomeListener(ViewerHomeListener l) {
        HOME_LISTENERS.add(l);
    }

    public static void removeHomeListener(ViewerHomeListener l) {
        HOME_LISTENERS.remove(l);
    }

    /** Per scene id, told when a viewer starts watching it. Registered at mod setup; never cleared by a restart. */
    private static final Map<String, List<SceneListener>> ENTER_LISTENERS = new HashMap<>();
    /** Per scene id, told when its clock runs out with no next scene. Same lifetime as {@link #ENTER_LISTENERS}. */
    private static final Map<String, List<SceneListener>> TIMEOUT_LISTENERS = new HashMap<>();

    /**
     * {@code l} is told each time a viewer starts watching scene {@code sceneId} with its clock running: the scene's
     * PLAY begins with them inside, they cross into it already playing, or a hand-over (a chain's {@code next} or
     * {@link #handOver}) moves them into it. Once per viewer per session: a scene handed over to itself counts again.
     * Previews and basin dives call it too; a listener that only wants its own players must check for them.
     */
    public static void addSceneEnterListener(String sceneId, SceneListener l) {
        ENTER_LISTENERS.computeIfAbsent(sceneId, k -> new ArrayList<>()).add(l);
    }

    public static void removeSceneEnterListener(String sceneId, SceneListener l) {
        List<SceneListener> list = ENTER_LISTENERS.get(sceneId);
        if (list != null) list.remove(l);
    }

    /**
     * {@code l} is told, for each viewer inside, when scene {@code sceneId}'s clock runs out and it has no next scene
     * standing by. If a listener calls {@link #handOver} (to the same scene, to loop a hold scene, or to another) the
     * viewers go there instead; otherwise the memory ends as usual.
     */
    public static void addSceneTimeoutListener(String sceneId, SceneListener l) {
        TIMEOUT_LISTENERS.computeIfAbsent(sceneId, k -> new ArrayList<>()).add(l);
    }

    public static void removeSceneTimeoutListener(String sceneId, SceneListener l) {
        List<SceneListener> list = TIMEOUT_LISTENERS.get(sceneId);
        if (list != null) list.remove(l);
    }

    private static void fireScene(Map<String, List<SceneListener>> registry, ServerPlayer p, Session s, String what) {
        List<SceneListener> list = registry.get(s.sceneId());
        if (list == null || list.isEmpty()) return;
        for (SceneListener l : new ArrayList<>(list)) {
            // A listener's bug must not break the server tick or the listeners after it.
            try {
                l.on(p, s);
            } catch (RuntimeException e) {
                LOGGER.error("Pensieve: a scene {} listener for '{}' failed for {}", what, s.sceneId(),
                        p.getScoreboardName(), e);
            }
        }
    }

    /** The scene-enter listeners, for each of {@code players} still watching {@code s}. */
    private static void entered(List<ServerPlayer> players, Session s) {
        for (ServerPlayer p : players) {
            if (BY_VIEWER.get(p.getUUID()) == s) fireScene(ENTER_LISTENERS, p, s, "enter");
        }
    }

    // ---- Starting -------------------------------------------------------------------------------------------

    /** A dive into {@code basin}: joins the memory already playing there, or starts it. */
    public static void startOrJoin(ServerPlayer player, PensieveBlockEntity basin) {
        if (isViewer(player) || basin.getLevel() == null) return;
        ServerLevel memory = startableMemory(player, false, "dive");
        if (memory == null) return;
        ResourceKey<Level> dim = basin.getLevel().dimension();
        BlockPos pos = basin.getBlockPos();
        Session session = sessionAt(dim, pos);
        if (session != null) {
            if (session.phase() == Session.Phase.CLOSING) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.fading"), true);
            } else {
                addViewer(session, player, pos);
            }
            return;
        }
        ItemStack stack = basin.memory();
        if (stack.isEmpty() || !(stack.getItem() instanceof PensieveMemory memoryItem)) return;
        String id = memoryItem.sceneId(stack);
        Optional<MemoryRegistry.LoadedScene> scene = lookup(id);
        if (scene.isEmpty()) {
            if (RecordingScene.idOf(id).isPresent()) {
                // A recording that is missing, corrupt, from a newer format or from another world: the memory fades.
                LOGGER.warn("Pensieve: the recording '{}' for the memory in the basin at {} could not be loaded", id, pos);
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.memory_faded"), true);
                return;
            }
            LOGGER.warn("Pensieve: no scene '{}' is loaded for the memory in the basin at {}", id, pos);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.no_scene"), true);
            return;
        }
        session = createSession(player, memory, id, scene.get(), dim, pos, false, startContext(player, Map.of()));
        if (session != null) addViewer(session, player, pos);
    }

    /**
     * {@code /pensieve preview <id>}: like a dive, from where the op stands, with no basin and no body. The scene
     * is read fresh from the dev folder when there is one; validation messages go to chat and stop the preview.
     */
    public static boolean startPreview(ServerPlayer player, String sceneId) {
        if (isViewer(player)) {
            player.sendSystemMessage(Component.literal("You are already inside a memory."));
            return false;
        }
        MinecraftServer server = player.server;
        ServerLevel memory = startableMemory(player, true, "preview");
        if (memory == null) return false;
        Optional<MemoryRegistry.LoadedScene> loaded;
        try {
            loaded = testScenes != null ? testScenes.apply(sceneId) : MemoryRegistry.loadForPreview(sceneId);
        } catch (SceneException e) {
            player.sendSystemMessage(Component.literal(sceneId + ": " + e.getMessage()));
            return false;
        }
        if (loaded.isEmpty()) {
            player.sendSystemMessage(Component.literal("No memory '" + sceneId + "' is loaded or in the dev folder."));
            return false;
        }
        List<String> messages = SceneValidator.validate(loaded.get().def(), PensieveCommand.liveNames(server));
        if (!messages.isEmpty()) {
            for (String m : messages) player.sendSystemMessage(Component.literal(sceneId + ": " + m));
            return false;
        }
        Session session = createSession(player, memory, sceneId, loaded.get(), null, null, true,
                startContext(player, Map.of()));
        if (session == null) return false;
        addViewer(session, player, player.blockPosition());
        return true;
    }

    /**
     * A memory with no basin: nobody else can join it and {@code Session.at} is never true. Unlike a preview it
     * leaves a body behind and forwards damage. Refuses quietly (false) when the player is dead, already a viewer or
     * inside the memory dimension, or the dimension or the scene is missing. Any open container is closed first, so
     * the memory never starts behind an inventory screen.
     */
    public static boolean startSolo(ServerPlayer player, String sceneId) {
        return startSolo(player, sceneId, Map.of());
    }

    /**
     * As {@link #startSolo(ServerPlayer, String)}, with a start context that fills the scene's placeholders for this
     * chain: {@code ItemText.CONTEXT_WAND} is the item id {@code {viewer_wand}} becomes (the wand just bought). A
     * placeholder the context leaves out falls back as {@link ItemText#viewerWand} says (the held wand, then Harry's).
     */
    public static boolean startSolo(ServerPlayer player, String sceneId, Map<String, String> context) {
        if (isViewer(player) || !player.isAlive()) return false;
        ServerLevel memory = startableMemory(player, false, "solo memory");
        if (memory == null) return false;
        Optional<MemoryRegistry.LoadedScene> loaded = lookup(sceneId);
        if (loaded.isEmpty()) {
            LOGGER.warn("Pensieve: no scene '{}' is loaded for a solo memory", sceneId);
            return false;
        }
        Session session = createSession(player, memory, sceneId, loaded.get(), null, null, false,
                startContext(player, context));
        if (session == null) return false;
        player.closeContainer();
        addViewer(session, player, player.blockPosition());
        return true;
    }

    /**
     * The memory level a start may begin in, or null with the player told why (in chat or on the action bar):
     * they are already inside the dimension, or it is not loaded.
     */
    @Nullable
    private static ServerLevel startableMemory(ServerPlayer player, boolean chat, String what) {
        MinecraftServer server = player.server;
        ensureInit(server);
        if (PensieveDimension.isMemory(player.level())) {
            tell(player, "message.hogwartsmagic.pensieve.inside", chat);
            return null;
        }
        ServerLevel memory = memoryLevel(server);
        if (memory == null) {
            LOGGER.error("Pensieve: the hogwartsmagic:memory dimension is not loaded; refusing the {}", what);
            tell(player, "message.hogwartsmagic.pensieve.unavailable", chat);
            return null;
        }
        return memory;
    }

    private static void tell(ServerPlayer player, String key, boolean chat) {
        if (chat) player.sendSystemMessage(Component.translatable(key));
        else player.displayClientMessage(Component.translatable(key), true);
    }

    /** The start context with its placeholders resolved against what {@code player} holds now (main hand first). */
    private static Map<String, String> startContext(ServerPlayer player, Map<String, String> context) {
        List<String> held = new ArrayList<>(2);
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            held.add(stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ItemText.resolve(context, held);
    }

    /** {@code scene} with its JSON's placeholders filled from {@code context}: what the clients are sent. */
    private static MemoryRegistry.LoadedScene filled(MemoryRegistry.LoadedScene scene, Map<String, String> context) {
        String json = ItemText.fill(scene.json(), context);
        return json.equals(scene.json()) ? scene : new MemoryRegistry.LoadedScene(scene.def(), json);
    }

    /** The recording behind a {@code recorded/<uuid>} scene id; empty for other ids and for a recording that will not load. */
    private static Optional<Recording> recordingOf(String id) {
        Optional<java.util.UUID> uuid = RecordingScene.idOf(id);
        MinecraftServer server = initServer;
        if (uuid.isEmpty() || server == null) return Optional.empty();
        return RecordingStore.load(server, uuid.get());
    }

    private static Optional<MemoryRegistry.LoadedScene> lookup(String id) {
        if (RecordingScene.idOf(id).isPresent()) {
            // A recorded memory carries no scene JSON: the clients get the recording itself, in parts.
            return recordingOf(id).map(r -> new MemoryRegistry.LoadedScene(RecordingScene.toScene(r), ""));
        }
        if (testScenes != null) return testScenes.apply(id);
        Optional<SceneDef> def = MemoryRegistry.get(id);
        Optional<String> json = MemoryRegistry.rawJson(id);
        if (def.isEmpty() || json.isEmpty()) return Optional.empty();
        return Optional.of(new MemoryRegistry.LoadedScene(def.get(), json.get()));
    }

    @Nullable
    private static Session createSession(ServerPlayer player, ServerLevel memory, String id,
                                         MemoryRegistry.LoadedScene scene, @Nullable ResourceKey<Level> basinDim,
                                         @Nullable BlockPos basinPos, boolean preview, Map<String, String> context) {
        scene = filled(scene, context);
        Recording recording = null;
        if (RecordingScene.idOf(id).isPresent()) {
            recording = recordingOf(id).orElse(null);
            if (recording == null) {
                player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.memory_faded"), !preview);
                return null;
            }
        }
        if (id.length() > PensieveStartPacket.MAX_ID) {
            LOGGER.warn("Pensieve: scene id '{}...' is over {} chars", id.substring(0, 32), PensieveStartPacket.MAX_ID);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.no_scene"), !preview);
            return null;
        }
        if (recording == null && !PensieveStartPacket.fits(scene.json())) {
            LOGGER.warn("Pensieve: scene '{}' is {} chars / {} UTF-8 bytes, over the {} chars / {} bytes the start packet"
                            + " carries (the custom payload limit is 1 MiB)", id, scene.json().length(),
                    PensieveStartPacket.utf8Length(scene.json()), PensieveStartPacket.MAX_JSON,
                    PensieveStartPacket.MAX_JSON_BYTES);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.too_big"), !preview);
            return null;
        }
        Session s = newSession(memory, id, scene, basinDim, basinPos, preview, recording);
        s.context = context;
        // A no-op on a derived level (the memory dimension shares the overworld clock); the client applies the
        // scene's time of day itself from the scene JSON.
        memory.setDayTime(scene.def().set().timeOfDay());
        SESSIONS.add(s);
        setPlaying(s, true);
        holdBasin(s, memory.getServer());
        return s;
    }

    /** A session in a fresh slot with its builder set up, not yet in {@link #SESSIONS}. */
    private static Session newSession(ServerLevel memory, String id, MemoryRegistry.LoadedScene scene,
                                      @Nullable ResourceKey<Level> basinDim, @Nullable BlockPos basinPos,
                                      boolean preview, @Nullable Recording recording) {
        int slot = allocator.acquire();
        int[] o = RegionAllocator.originFor(slot);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        Session s = new Session(id, scene.json(), scene.def(), slot, origin, basinDim, basinPos, preview);
        s.level = memory;
        int radius = Math.max(1, Math.min(MAX_RADIUS, scene.def().radius()));
        s.radius = radius;
        if (recording == null) {
            s.builder = new SetBuilder(memory, origin, scene.def().set(), radius);
        } else {
            s.recording = recording;
            RecordingStore.prefetch(memory.getServer(), recording.id());
            s.builder = new SetBuilder(memory, origin, recording.blocks(), radius);
            s.recordedChanges.addAll(recording.blocks().changes());
            s.recordedChanges.sort((a, b) -> Integer.compare(a.tick(), b.tick()));
            s.recordedStates = SetBuilder.parsePalette(memory, recording.blocks().palette(), new java.util.HashSet<>());
            s.anchorOverride = recordedAnchor(recording, origin, radius);
        }
        return s;
    }

    /**
     * {@code s} just began PLAY: when its scene names a {@code next} one, that is built ahead in its own slot as a
     * standby (same basin, same preview flag, same chain root), so the viewers can be handed over the moment this
     * one ends. A next scene that is missing, broken or too big, or one this chain already played (a cycle), is
     * logged and the chain simply ends here.
     */
    private static void buildNext(MinecraftServer server, Session s) {
        String next = s.scene().next();
        if (next == null || s.nextSession != null) return;
        if (s.chainIds.contains(next)) {
            LOGGER.warn("Pensieve: scene '{}' names next scene '{}', which chain '{}' already played; the chain ends there",
                    s.sceneId(), next, s.chainRoot);
            return;
        }
        s.nextSession = standbyFor(server, s, next);
    }

    /**
     * A standby session of scene {@code next} following {@code s} (same basin, preview flag and chain root), built
     * ahead in its own slot and already in {@link #SESSIONS}; null, logged, when the scene is missing, broken or too
     * big for the start packet. The caller links it as {@code s.nextSession}.
     */
    @Nullable
    private static Session standbyFor(MinecraftServer server, Session s, String next) {
        Optional<MemoryRegistry.LoadedScene> loaded;
        try {
            // Looked up the way the session itself was: a preview reads the dev folder fresh.
            loaded = s.preview() && testScenes == null ? MemoryRegistry.loadForPreview(next) : lookup(next);
        } catch (SceneException e) {
            LOGGER.warn("Pensieve: scene '{}' after '{}' does not load: {}", next, s.sceneId(), e.getMessage());
            return null;
        }
        if (loaded.isEmpty()) {
            LOGGER.warn("Pensieve: scene '{}' after '{}' is not loaded; the chain ends there", next, s.sceneId());
            return null;
        }
        // The chain's own context: a wand chosen at the start is the wand in every scene after it.
        loaded = Optional.of(filled(loaded.get(), s.context));
        Recording nextRecording = recordingOf(next).orElse(null);
        boolean recorded = RecordingScene.idOf(next).isPresent();
        if (recorded && nextRecording == null) {
            LOGGER.warn("Pensieve: the recording '{}' after '{}' could not be loaded; the chain ends there", next, s.sceneId());
            return null;
        }
        if (next.length() > PensieveStartPacket.MAX_ID || (!recorded && !PensieveStartPacket.fits(loaded.get().json()))) {
            LOGGER.warn("Pensieve: scene '{}' after '{}' is too big for the start packet ({} chars / {} UTF-8 bytes,"
                            + " limits {} / {}); the chain ends there", next, s.sceneId(), loaded.get().json().length(),
                    PensieveStartPacket.utf8Length(loaded.get().json()), PensieveStartPacket.MAX_JSON,
                    PensieveStartPacket.MAX_JSON_BYTES);
            return null;
        }
        if (s.preview()) {
            List<String> messages = SceneValidator.validate(loaded.get().def(), PensieveCommand.liveNames(server));
            if (!messages.isEmpty()) {
                for (Session.Viewer v : s.viewers().values()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(v.id());
                    if (p != null) for (String m : messages) p.sendSystemMessage(Component.literal(next + ": " + m));
                }
                LOGGER.warn("Pensieve: scene '{}' after preview '{}' has {} problem(s); the chain ends there",
                        next, s.sceneId(), messages.size());
                return null;
            }
        }
        Session n = newSession(s.level, next, loaded.get(), s.basinDim(), s.basinPos(), s.preview(), nextRecording);
        n.standby = true;
        n.chainRoot = s.chainRoot;
        n.chainIds.addAll(s.chainIds);
        n.context = s.context;
        SESSIONS.add(n);
        return n;
    }

    /**
     * Cuts the memory {@code player} is watching over to scene {@code sceneId} now, whatever the clock says: the scene
     * is built ahead as a standby (replacing any {@code next} one already standing by), the scene on screen fades to
     * black over {@link #HANDOVER_FADE_TICKS}, and once both the fade is over and the new set is built every viewer of
     * the session moves over exactly as at a chain's end (same return point, body and chain root). It may name the
     * scene playing now, to loop it, or one the chain already played. Safe while the scene is still building (the
     * viewers wait pinned, as in a stalled chain hand-over) and while a hand-over is already under way: asking again
     * for the scene already on its way changes nothing, and asking for another replaces it. False, with nothing
     * changed, when the player is not inside (or is already leaving) or the scene does not load.
     */
    public static boolean handOver(ServerPlayer player, String sceneId) {
        Session s = BY_VIEWER.get(player.getUUID());
        if (s == null || s.standby || s.phase == Session.Phase.CLOSING || sceneId == null) return false;
        Session.Viewer v = s.viewers().get(player.getUUID());
        if (v == null || v.stage == Session.Viewer.Stage.EXITING) return false;
        Session pending = s.nextSession;
        if (s.handOverAsked && pending != null && pending.sceneId().equals(sceneId)) return true;
        Session n = standbyFor(player.server, s, sceneId);
        if (n == null) return false;
        s.nextSession = n;
        if (pending != null && pending.standby) finish(pending);
        if (!s.handOverAsked) {
            s.handOverAsked = true;
            s.handOverNotBefore = s.level.getGameTime() + HANDOVER_FADE_TICKS;
            for (Session.Viewer w : s.viewers().values()) {
                if (w.stage != Session.Viewer.Stage.INSIDE) continue;
                ServerPlayer p = player.server.getPlayerList().getPlayer(w.id());
                if (p != null) PensieveNet.send(p, new PensieveFadePacket(HANDOVER_FADE_TICKS));
            }
        }
        s.phase = Session.Phase.HANDOVER;
        return true;
    }

    /** Takes the basin ticket (see {@link #BASIN_TICKET}) on the first dive; previews have no basin. */
    private static void holdBasin(Session s, @Nullable MinecraftServer server) {
        if (s.basinTicket != null || s.basinPos() == null || s.basinDim() == null || server == null) return;
        ServerLevel where = server.getLevel(s.basinDim());
        if (where == null) return;
        ChunkPos c = new ChunkPos(s.basinPos());
        where.getChunkSource().addRegionTicket(BASIN_TICKET, c, BASIN_TICKET_DISTANCE, c);
        s.basinTicket = c;
        s.basinTicketLevel = where;
    }

    /** Drops the basin ticket; idempotent, and safe for a session that never took one. */
    private static void releaseBasin(Session s) {
        ChunkPos c = s.basinTicket;
        ServerLevel where = s.basinTicketLevel;
        s.basinTicket = null;
        s.basinTicketLevel = null;
        if (c != null && where != null) {
            where.getChunkSource().removeRegionTicket(BASIN_TICKET, c, BASIN_TICKET_DISTANCE, c);
        }
    }

    /** Gametests: whether {@code s} holds its basin ticket right now. */
    public static boolean holdsBasinTicket(Session s) {
        return s.basinTicket != null;
    }

    private static void addViewer(Session s, ServerPlayer p, BlockPos enterAt) {
        // Off the horse (or boat, or minecart) first, and nothing riding them: a mount would be dragged into the
        // memory dimension with them, and the return point would be wherever the vehicle had wandered.
        p.stopRiding();
        p.ejectPassengers();
        Session.Viewer v = new Session.Viewer(p.getUUID(), p.position(), p.getYRot(), p.getXRot(),
                p.level().dimension(), SWALLOW_TICKS, null, p.getAbilities().mayfly, p.getAbilities().flying,
                p.isInvisible(), p.isInvulnerable(), p.gameMode.getGameModeForPlayer());
        s.viewers().put(v.id(), v);
        BY_VIEWER.put(v.id(), s);
        writeReturnTag(p, v);
        p.stopUsingItem();
        p.setDeltaMovement(Vec3.ZERO);
        PensieveNet.send(p, new PensieveEnterPacket(enterAt));
    }

    // ---- Ticking --------------------------------------------------------------------------------------------

    /**
     * One server tick. {@code pensieveBlocksPerTick} is one budget for the whole tick, shared by every build and
     * clear job: builds first (someone is waiting inside), then standby builds (the next scene of a chain, built
     * ahead), then clears with whatever is left.
     */
    public static void tick(MinecraftServer server) {
        ensureInit(server);
        int[] budget = {testBudget >= 0 ? testBudget : Math.max(1, Config.pensieveBlocksPerTick)};
        List<Session> order = new ArrayList<>(SESSIONS.size());
        for (Session s : SESSIONS) if (!s.standby) order.add(s);
        for (Session s : SESSIONS) if (s.standby) order.add(s);
        HANDED_THIS_TICK.clear();
        for (Session s : order) {
            // A standby finished earlier this tick (its owner ended) is gone; do not build into a freed slot. One
            // handed its viewers earlier this tick already counted their tick in the session they came from.
            if (SESSIONS.contains(s) && !HANDED_THIS_TICK.contains(s)) tickSession(server, s, budget);
        }
        HANDED_THIS_TICK.clear();
        Iterator<Clearing> it = CLEARING.iterator();
        while (it.hasNext() && budget[0] > 0) {
            Clearing c = it.next();
            if (c.advance(budget)) {
                it.remove();
                RegionData.get(c.level).remove(c.slot);
                allocator.release(c.slot);
            }
        }
    }

    private static void tickSession(MinecraftServer server, Session s, int[] budget) {
        if (s.standby) {
            // Built ahead for a chain: nobody to tick, and never finished for being empty (its owner finishes it).
            buildStep(s, budget);
            return;
        }
        ServerLevel memory = s.level;
        for (Session.Viewer v : new ArrayList<>(s.viewers().values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(v.id());
            if (p == null) {
                removeBody(server, v);
                dropViewer(s, v);
            } else if (!p.isAlive()) {
                // Normally onViewerDeath has already sent them home; this is the net for a death it never saw.
                removeBody(server, v);
                PensieveNet.send(p, new PensieveExitPacket(ExitReason.LEFT.ordinal()));
                restoreState(p, v);
                clearReturnTag(p);
                dropViewer(s, v);
            } else {
                if (v.crossed && !s.preview() && memory.getGameTime() % 20 == 0) ensureBody(s, v, p);
                tickViewer(s, v, p, memory);
            }
        }
        if (s.viewers().isEmpty()) {
            finish(s);
            return;
        }
        updateGhosts(server, s);
        long now = memory.getGameTime();
        if (s.phase == Session.Phase.BUILD) {
            buildStep(s, budget);
            if (s.built && s.firstCrossTime >= 0 && now - s.firstCrossTime >= s.scene().set().buildTicks()) {
                s.phase = Session.Phase.PLAY;
                s.startGameTime = now;
                List<ServerPlayer> watching = new ArrayList<>();
                for (Session.Viewer v : s.viewers().values()) {
                    if (v.stage != Session.Viewer.Stage.INSIDE) continue;
                    v.sawStart = true;
                    ServerPlayer p = server.getPlayerList().getPlayer(v.id());
                    if (p != null) {
                        sendStart(p, s);
                        watching.add(p);
                    }
                }
                buildNext(server, s);
                entered(watching, s);
            }
        } else if (s.phase == Session.Phase.PLAY) {
            applyBlockEvents(s, now, budget);
            if (SessionClock.finished(s.startGameTime, now, s.scene().length())) {
                // A hold scene may loop or move on instead of ending: its timeout listeners may hand over.
                if (s.nextSession == null) timedOut(server, s);
                if (s.phase != Session.Phase.PLAY) {
                    // A listener handed over (HANDOVER waits for its fade and build) or every viewer is gone.
                } else if (s.nextSession != null) {
                    s.phase = Session.Phase.HANDOVER;
                    tryHandover(server, s, now);
                } else {
                    endScene(server, s);
                }
            }
        } else if (s.phase == Session.Phase.HANDOVER) {
            tryHandover(server, s, now);
        }
    }

    private static void buildStep(Session s, int[] budget) {
        if (!s.built && s.builder != null && budget[0] > 0) {
            s.built = s.builder.advance(budget);
            recordPlaced(s);
        }
    }

    /** {@code s}'s clock ran out with no next scene: its timeout listeners hear about every viewer inside. */
    private static void timedOut(MinecraftServer server, Session s) {
        if (!TIMEOUT_LISTENERS.containsKey(s.sceneId())) return;
        for (Session.Viewer v : new ArrayList<>(s.viewers().values())) {
            if (v.stage != Session.Viewer.Stage.INSIDE) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(v.id());
            if (p != null && BY_VIEWER.get(p.getUUID()) == s) fireScene(TIMEOUT_LISTENERS, p, s, "timeout");
        }
    }

    /** The clock ran out with no next scene: everyone goes home with END. */
    private static void endScene(MinecraftServer server, Session s) {
        s.phase = Session.Phase.CLOSING;
        for (Session.Viewer v : s.viewers().values()) {
            ServerPlayer p = server.getPlayerList().getPlayer(v.id());
            if (p != null) beginExit(p, v, ExitReason.END);
        }
    }

    /**
     * {@code s}'s clock ran out and its next scene stands by. Until that is built, {@code s} holds in HANDOVER with
     * the viewers pinned at its anchor. Once built, every viewer moves over as the same {@link Session.Viewer} (return
     * point, body and {@code sawStart} go along): those inside are moved to the new anchor and sent its start, one
     * still swallowing crosses into it later, and one already exiting just finishes going home from it. The basin
     * ticket moves with them rather than being taken again: a region ticket is a set, so a second add followed by
     * the old session's remove would drop it.
     */
    private static void tryHandover(MinecraftServer server, Session s, long now) {
        Session next = s.nextSession;
        if (next == null) {
            endScene(server, s);
            return;
        }
        // A hand-over asked for mid-scene first lets the scene on screen fade to black.
        if (s.handOverAsked && now < s.handOverNotBefore) return;
        if (!next.built || (next.recording != null && !RecordingStore.clientBlobReady(next.recording.id()))) return;
        s.nextSession = null;
        next.standby = false;
        next.basinTicket = s.basinTicket;
        next.basinTicketLevel = s.basinTicketLevel;
        s.basinTicket = null;
        s.basinTicketLevel = null;
        next.phase = Session.Phase.PLAY;
        next.startGameTime = now;
        next.firstCrossTime = now;
        next.level.setDayTime(next.scene().set().timeOfDay());
        List<ServerPlayer> watching = new ArrayList<>();
        for (Session.Viewer v : new ArrayList<>(s.viewers().values())) {
            s.viewers().remove(v.id());
            // Each recorded memory has its own recording to send; the next scene may be another one.
            v.recordingSent = false;
            next.viewers().put(v.id(), v);
            BY_VIEWER.put(v.id(), next);
            if (v.stage != Session.Viewer.Stage.INSIDE) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(v.id());
            if (p == null) continue;
            moveTo(p, next.level, next.anchor(), p.getYRot(), p.getXRot());
            if (s.recording != null && next.recording == null) PensieveNet.send(p, new PensieveGhostsPacket(List.of()));
            sendStart(p, next);
            watching.add(p);
        }
        finish(s);
        HANDED_THIS_TICK.add(next);
        setPlaying(next, true);
        buildNext(server, next);
        entered(watching, next);
    }

    private static void tickViewer(Session s, Session.Viewer v, ServerPlayer p, ServerLevel memory) {
        if (v.stage == Session.Viewer.Stage.SWALLOW) {
            if (p.level().dimension().equals(v.returnDim())) hold(p, v.returnPos());
            if (--v.swallowTicksLeft <= 0) {
                if (s.recording != null && !RecordingStore.clientBlobReady(s.recording.id())) {
                    // The client form is still being encoded off-thread: wait here rather than encode on this tick.
                    RecordingStore.prefetch(p.server, s.recording.id());
                    if (v.swallowTicksLeft < -BLOB_WAIT_TICKS) {
                        LOGGER.error("Pensieve: recording {} never got a client form; sending {} home", s.recording.id(),
                                p.getScoreboardName());
                        beginExit(p, v, ExitReason.LEFT);
                    }
                    return;
                }
                cross(s, v, p, memory);
            }
        } else if (v.stage == Session.Viewer.Stage.INSIDE) {
            if (p.level() != memory) {
                // Moved out some other way: treat it as leaving, so they still go home with their state restored.
                beginExit(p, v, ExitReason.LEFT);
                return;
            }
            if (s.recording != null) {
                bound(s, p);
            } else {
                hold(p, s.anchor());
                applyGhost(p);
            }
        } else {
            if (v.crossed) {
                if (p.level() == memory) hold(p, s.anchor());
            } else if (p.level().dimension().equals(v.returnDim())) {
                hold(p, v.returnPos());
            }
            if (--v.exitTicksLeft <= 0) {
                // Before the move, so the client holds the screen black through the return instead of showing it.
                if (wakes(s, v)) {
                    PensieveNet.send(p, new PensieveWakePacket(WAKE_TICKS));
                    WAKES.put(p.getUUID(), p.level().getGameTime());
                }
                moveBack(p, v);
                dropViewer(s, v);
                // Only a memory watched whole pays out, and only here: once per viewer, back in the real world, so
                // the reward lands in their real inventory. A late joiner (not inside as PLAY began) saw only part
                // of it and gets nothing. A preview runs it too, so it can be tried.
                if (v.exitReason == ExitReason.END && v.sawStart) applyEndAction(p, s.scene().endAction());
                for (ViewerHomeListener l : new ArrayList<>(HOME_LISTENERS)) {
                    // A listener's bug must not break the server tick or the listeners after it.
                    try {
                        l.onHome(p, s.chainRoot(), v.exitReason, v.sawStart);
                    } catch (RuntimeException e) {
                        LOGGER.error("Pensieve: a home listener failed for {}", p.getScoreboardName(), e);
                    }
                }
            }
        }
    }

    /** Pins the player in place; the camera is the client's business, so their own rotation is kept. */
    private static void hold(ServerPlayer p, Vec3 at) {
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        if (p.position().distanceToSqr(at) > 1.0E-4) {
            p.connection.teleport(at.x, at.y, at.z, p.getYRot(), p.getXRot());
        }
    }

    private static void cross(Session s, Session.Viewer v, ServerPlayer p, ServerLevel memory) {
        Vec3 a = s.anchor();
        moveTo(p, memory, a, p.getYRot(), p.getXRot());
        applyGhost(p);
        v.stage = Session.Viewer.Stage.INSIDE;
        v.crossed = true;
        if (s.firstCrossTime < 0) s.firstCrossTime = memory.getGameTime();
        if (!s.preview()) ensureBody(s, v, p);
        sendStart(p, s);
        // Crossing into a scene already playing: they start watching it now (one still building tells them at PLAY).
        if (s.phase == Session.Phase.PLAY) entered(List.of(p), s);
    }

    /**
     * A recorded memory's client blob, in parts of at most {@link PensieveRecordingPacket#MAX_PART} bytes, in order,
     * ahead of the start packet. Once per viewer: a viewer already holding it is not sent it again.
     */
    private static void sendRecording(ServerPlayer p, Session s) {
        Recording rec = s.recording;
        Session.Viewer v = s.viewers().get(p.getUUID());
        if (rec == null || v == null || v.recordingSent) return;
        byte[] blob = RecordingStore.readyClientBlob(rec.id());
        if (blob == null) {
            LOGGER.error("Pensieve: no client form for recording {}; {} will see an empty memory", rec.id(),
                    p.getScoreboardName());
            return;
        }
        int parts = PensieveRecordingPacket.partsFor(blob.length);
        for (int i = 0; i < parts; i++) {
            int from = i * PensieveRecordingPacket.MAX_PART;
            int to = Math.min(blob.length, from + PensieveRecordingPacket.MAX_PART);
            PensieveNet.send(p, new PensieveRecordingPacket(s.sceneId(), i, parts,
                    java.util.Arrays.copyOfRange(blob, from, to)));
        }
        v.recordingSent = true;
    }

    /**
     * Tells every viewer inside a recorded memory who the other ghosts are, whenever the set of viewers inside
     * changes (someone crossed in, started out, logged off, died).
     */
    private static void updateGhosts(MinecraftServer server, Session s) {
        if (s.recording == null) return;
        java.util.Set<UUID> inside = new java.util.LinkedHashSet<>();
        for (Session.Viewer v : s.viewers().values()) {
            if (v.stage == Session.Viewer.Stage.INSIDE) inside.add(v.id());
        }
        if (inside.equals(s.ghostSet)) return;
        // Someone who was inside and no longer is (still online, on their way home) forgets the ghosts.
        for (UUID gone : s.ghostSet) {
            ServerPlayer p = inside.contains(gone) ? null : server.getPlayerList().getPlayer(gone);
            if (p != null) PensieveNet.send(p, new PensieveGhostsPacket(List.of()));
        }
        s.ghostSet = inside;
        for (UUID id : inside) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            List<UUID> others = new ArrayList<>();
            for (UUID other : inside) {
                if (!other.equals(id) && others.size() < PensieveGhostsPacket.MAX_GHOSTS) others.add(other);
            }
            PensieveNet.send(p, new PensieveGhostsPacket(others));
        }
    }

    private static void sendStart(ServerPlayer p, Session s) {
        sendRecording(p, s);
        long start = s.phase == Session.Phase.PLAY ? s.startGameTime : -1;
        PensieveNet.send(p, new PensieveStartPacket(s.sceneId(), s.rawJson(), s.origin(), start,
                s.scene().set().buildTicks(), s.preview()));
    }

    /** The middle of a recorded memory island: the sphere the capture culled to is centred here. */
    private static Vec3 islandCentre(BlockPos origin) {
        return new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
    }

    /**
     * Where the viewers of a recorded memory start: the owner at tick 0, moved 3 blocks back along their yaw and up
     * 1.6 (the camera trails them), kept 2 blocks inside the island. With no owner track: just above the origin.
     */
    private static Vec3 recordedAnchor(Recording rec, BlockPos origin, int radius) {
        Recording.Track owner = null;
        for (Recording.Track t : rec.tracks()) {
            if (t.uuid() != null && t.uuid().equals(rec.owner()) && t.x().length > 0) {
                owner = t;
                break;
            }
        }
        if (owner == null) return new Vec3(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5);
        double yaw = Math.toRadians(owner.yaw()[0]);
        double x = origin.getX() + owner.x()[0] + 3 * Math.sin(yaw);
        double y = origin.getY() + owner.y()[0] + 1.6;
        double z = origin.getZ() + owner.z()[0] - 3 * Math.cos(yaw);
        Vec3 c = islandCentre(origin);
        double[] at = IslandBounds.clamp(x, y, z, c.x, c.y, c.z, Math.max(1, radius - 2));
        return new Vec3(at[0], at[1], at[2]);
    }

    /**
     * A free ghost of a recorded memory flies where it likes: the client clamps it softly at the island radius, and
     * the server only snaps it back to the radius once it is more than 4 blocks past it.
     */
    static void bound(Session s, ServerPlayer p) {
        applyGhost(p);
        Vec3 c = islandCentre(s.origin());
        if (IslandBounds.outside(p.getX(), p.getY(), p.getZ(), c.x, c.y, c.z, s.radius + 4)) {
            double[] to = IslandBounds.clamp(p.getX(), p.getY(), p.getZ(), c.x, c.y, c.z, s.radius);
            p.connection.teleport(to[0], to[1], to[2], p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
        }
        p.fallDistance = 0;
    }

    /**
     * Invisible, flying and invulnerable. Re-applied every tick: an effect change resets invisibility and a game
     * mode change resets flight. The damage forwarded from the body bypasses this (see {@link #forwardDamage}).
     */
    private static void applyGhost(ServerPlayer p) {
        if (!p.isInvisible()) p.setInvisible(true);
        p.setInvulnerable(true);
        if (!p.getAbilities().mayfly || !p.getAbilities().flying) {
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        }
    }

    private static void applyBlockEvents(Session s, long now, int[] budget) {
        float elapsed = SessionClock.elapsed(s.startGameTime, now, 0f);
        // The block changes of a recorded memory, each at its own tick. They draw on the shared block budget, so a
        // burst (TNT) spreads over a few ticks instead of landing in one; a change never jumps the queue.
        while (s.nextChange < s.recordedChanges.size() && s.recordedChanges.get(s.nextChange).tick() <= elapsed
                && budget[0] > 0) {
            Recording.BlockChange c = s.recordedChanges.get(s.nextChange++);
            int size = s.recording == null ? s.radius : s.recording.blocks().size();
            if (c.state() < 0 || c.state() >= s.recordedStates.length || Math.abs(c.x()) > Math.min(s.radius, size)
                    || Math.abs(c.z()) > Math.min(s.radius, size) || Math.abs(c.y()) > size) {
                continue;
            }
            BlockPos pos = s.origin().offset(c.x(), c.y(), c.z());
            if (s.level.isOutsideBuildHeight(pos)) continue;
            if (s.level.getBlockState(pos).hasBlockEntity()) s.level.removeBlockEntity(pos);
            s.level.setBlock(pos, s.recordedStates[c.state()], SetBuilder.FLAGS);
            SetBuilder.clearTicks(s.level, new BoundingBox(pos));
            if (s.changedCells.add(pos.asLong())) s.eventPlaced.add(pos);
            RegionData.get(s.level).expand(s.slot(), pos);
            budget[0]--;
        }
        while (s.nextEvent < s.blockEvents.size() && s.blockEvents.get(s.nextEvent).t() <= elapsed) {
            SceneDef.EventDef e = s.blockEvents.get(s.nextEvent++);
            if (e.id() == null || e.at() == null || e.at().length < 3) continue;
            ResourceLocation rl = ResourceLocation.tryParse(e.id());
            Optional<Block> block = rl == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(rl);
            if (block.isEmpty()) continue;
            // Whole numbers (the validator rejects a fractional block position); floor is the safe reading anyway.
            BlockPos pos = s.origin().offset(Mth.floor(e.at()[0]), Mth.floor(e.at()[1]), Mth.floor(e.at()[2]));
            if (s.level.getBlockState(pos).hasBlockEntity()) s.level.removeBlockEntity(pos);
            s.level.setBlock(pos, block.get().defaultBlockState(), SetBuilder.FLAGS);
            // onPlace still schedules ticks under these flags: water placed by an event must not start flowing.
            SetBuilder.clearTicks(s.level, new BoundingBox(pos));
            s.eventPlaced.add(pos);
            RegionData.get(s.level).expand(s.slot(), pos);
        }
    }

    private static void recordPlaced(Session s) {
        if (s.builder == null) return;
        List<BlockPos> placed = s.builder.placed();
        if (s.recorded >= placed.size()) return;
        RegionData data = RegionData.get(s.level);
        for (int i = s.recorded; i < placed.size(); i++) data.expand(s.slot(), placed.get(i));
        s.recorded = placed.size();
    }

    // ---- Leaving --------------------------------------------------------------------------------------------

    /** Starts the viewer's exit: the exit packet now, the move back after the reason's delay. */
    public static void leave(ServerPlayer player, ExitReason reason) {
        Session s = BY_VIEWER.get(player.getUUID());
        if (s == null) return;
        Session.Viewer v = s.viewers().get(player.getUUID());
        if (v != null) beginExit(player, v, reason);
    }

    /** The basin at {@code basin} was broken or emptied: everyone inside is pulled out. */
    public static void abortAt(Level level, BlockPos basin) {
        if (level.isClientSide || level.getServer() == null) return;
        Session s = sessionAt(level.dimension(), basin);
        if (s == null) return;
        s.phase = Session.Phase.CLOSING;
        setPlaying(s, false);
        for (Session.Viewer v : s.viewers().values()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(v.id());
            if (p != null) beginExit(p, v, ExitReason.ABORT);
        }
    }

    private static void beginExit(ServerPlayer p, Session.Viewer v, ExitReason reason) {
        int delay = exitDelay(reason);
        if (v.stage == Session.Viewer.Stage.EXITING) {
            // Already on the way out; a harsher reason (a yank, a broken basin) may only make it sooner.
            v.exitTicksLeft = Math.min(v.exitTicksLeft, delay);
            return;
        }
        v.stage = Session.Viewer.Stage.EXITING;
        v.exitReason = reason;
        v.exitTicksLeft = delay;
        PensieveNet.send(p, new PensieveExitPacket(reason.ordinal()));
    }

    /**
     * The {@code wake} end action plays for a viewer going home from the scene by its end or by leaving (not by a
     * yank or an abort, which tear them out): the client keeps the screen black through the return and then opens
     * the eyes on the real world. Unlike the other end actions it needs no {@code sawStart}: it is only a picture.
     */
    private static boolean wakes(Session s, Session.Viewer v) {
        return "wake".equals(s.scene().endAction())
                && (v.exitReason == ExitReason.END || v.exitReason == ExitReason.LEFT);
    }

    /** A scene's {@code end_action}, for one viewer who saw it to the end and is home again. */
    private static void applyEndAction(ServerPlayer p, @Nullable String action) {
        if (action == null) return;
        if ("cave_map".equals(action)) {
            com.hogwartsmagic.item.CaveMemoryItem.grantCaveMap(p);
        } else if ("wake".equals(action)) {
            // Nothing to give: the wake itself was sent just before the move home (see wakes).
        } else {
            LOGGER.warn("Pensieve: unknown end action '{}' ignored", action);
        }
    }

    private static int exitDelay(ExitReason reason) {
        if (reason == ExitReason.YANK) return 10;
        if (reason == ExitReason.ABORT) return 20;
        return 30;
    }

    private static void moveBack(ServerPlayer p, Session.Viewer v) {
        ServerLevel back = p.server.getLevel(v.returnDim());
        Vec3 to = v.returnPos();
        if (back == null) {
            // The return dimension is gone: the same XYZ in the overworld could be anywhere, so world spawn.
            back = p.server.overworld();
            to = Vec3.atBottomCenterOf(back.getSharedSpawnPos());
        }
        moveTo(p, back, to, v.yRot(), v.xRot());
        restoreState(p, v);
        clearReturnTag(p);
        removeBody(p.server, v);
    }

    /**
     * SeamlessMove sends no respawn packet, so the client keeps its old ClientLevel. That is only safe when both
     * levels have the same height range; from a Nether or End basin the client would decode overworld-height
     * memory chunks into a Nether-shaped level. Those moves use a real dimension change (teleportTo) instead.
     */
    private static void moveTo(ServerPlayer p, ServerLevel level, Vec3 at, float yRot, float xRot) {
        moveTo(p, level, at, yRot, xRot, false);
    }

    private static void moveTo(ServerPlayer p, ServerLevel level, Vec3 at, float yRot, float xRot, boolean resync) {
        ownMove = true;
        try {
            if (p.serverLevel() == level) {
                p.connection.teleport(at.x, at.y, at.z, yRot, xRot);
            } else if (!resync && sameShape(p.serverLevel(), level)) {
                SeamlessMove.move(p, level, at.x, at.y, at.z, yRot, xRot);
            } else {
                p.teleportTo(level, at.x, at.y, at.z, yRot, xRot);
            }
        } finally {
            ownMove = false;
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    private static boolean sameShape(ServerLevel a, ServerLevel b) {
        return a.dimensionType() == b.dimensionType()
                || (a.getMinBuildHeight() == b.getMinBuildHeight() && a.getHeight() == b.getHeight());
    }

    private static void restoreState(ServerPlayer p, Session.Viewer v) {
        restoreState(p, v.wasMayfly, v.wasFlying, v.wasInvisible, v.wasInvulnerable, v.wasMode);
    }

    /**
     * Undoes the ghost. Flight comes back as it was at the dive, unless the game mode changed while they were
     * inside (an op's {@code /gamemode}): then the abilities are the new mode's own, not the stale snapshot, so a
     * player switched to survival does not keep creative flight.
     */
    private static void restoreState(ServerPlayer p, boolean mayfly, boolean flying, boolean invisible,
                                     boolean invulnerable, @Nullable GameType mode) {
        p.setInvisible(invisible);
        p.setInvulnerable(invulnerable);
        GameType now = p.gameMode.getGameModeForPlayer();
        if (mode != null && now != mode) {
            now.updatePlayerAbilities(p.getAbilities());
        } else {
            p.getAbilities().mayfly = mayfly;
            p.getAbilities().flying = flying;
        }
        p.onUpdateAbilities();
    }

    // ---- The body ---------------------------------------------------------------------------------------------

    /**
     * Makes sure the body of the viewer stands at the return point, spawning it (again) if it is missing and its
     * chunk is loaded. A body is never saved, so one whose chunk unloaded while the viewer was away is replaced.
     */
    private static void ensureBody(Session s, Session.Viewer v, ServerPlayer owner) {
        ServerLevel back = owner.server.getLevel(v.returnDim());
        if (back == null) return;
        if (v.bodyId != null) {
            net.minecraft.world.entity.Entity e = back.getEntity(v.bodyId);
            if (e != null && e.isAlive()) return;
        }
        BlockPos at = BlockPos.containing(v.returnPos());
        if (!back.isLoaded(at)) return;
        float yaw = v.yRot();
        BlockPos basin = s.basinPos();
        if (basin != null) {
            double dx = basin.getX() + 0.5 - v.returnPos().x;
            double dz = basin.getZ() + 0.5 - v.returnPos().z;
            if (dx * dx + dz * dz > 1.0E-4) yaw = (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
        }
        PensieveBodyEntity body = PensieveBodyEntity.spawn(back, owner, v.returnPos(), yaw);
        v.bodyId = body == null ? null : body.getUUID();
    }

    private static void removeBody(MinecraftServer server, Session.Viewer v) {
        if (v.bodyId == null) return;
        ServerLevel back = server.getLevel(v.returnDim());
        if (back != null) {
            net.minecraft.world.entity.Entity e = back.getEntity(v.bodyId);
            if (e != null) e.discard();
        }
        v.bodyId = null;
    }

    /**
     * The body was hit: the viewer takes the damage. The invulnerable flag of the ghost is lifted for exactly this
     * call and {@link #forwarding} makes the LivingAttackEvent cancel stand aside. Below a third of max health the
     * viewer is yanked out; a lethal hit is handled by {@link #onViewerDeath}.
     */
    public static boolean forwardDamage(ServerPlayer owner, DamageSource source, float amount) {
        Session s = BY_VIEWER.get(owner.getUUID());
        if (s == null) return false;
        boolean wasInvulnerable = owner.isInvulnerable();
        UUID previous = forwarding;
        boolean hurt;
        owner.setInvulnerable(false);
        forwarding = owner.getUUID();
        try {
            hurt = owner.hurt(source, amount);
        } finally {
            forwarding = previous;
            if (BY_VIEWER.containsKey(owner.getUUID())) owner.setInvulnerable(wasInvulnerable);
        }
        if (!hurt) return false;
        owner.setDeltaMovement(Vec3.ZERO);
        owner.hurtMarked = false;
        PensieveNet.send(owner, new PensieveThudPacket(Math.min(1f, amount / 6f)));
        if (owner.isAlive() && BY_VIEWER.containsKey(owner.getUUID())
                && YankRule.shouldYank(owner.getHealth(), owner.getMaxHealth())) {
            leave(owner, ExitReason.YANK);
        }
        return true;
    }

    /**
     * A viewer is dying (LivingDeathEvent, before vanilla drops anything). They are sent home right now, with a
     * real dimension change so the client resyncs, and taken out of the session, so the dropped inventory lands
     * at the body and the respawn can never put them back in the memory dimension.
     */
    static void onViewerDeath(ServerPlayer p) {
        Session s = BY_VIEWER.get(p.getUUID());
        if (s == null) return;
        Session.Viewer v = s.viewers().get(p.getUUID());
        if (v != null) {
            ServerLevel back = p.server.getLevel(v.returnDim());
            Vec3 to = v.returnPos();
            if (back == null) {
                back = p.server.overworld();
                to = Vec3.atBottomCenterOf(back.getSharedSpawnPos());
            }
            PensieveNet.send(p, new PensieveExitPacket(ExitReason.LEFT.ordinal()));
            // Not SeamlessMove: it revives the player. A dead player is moved with an ordinary teleport.
            moveTo(p, back, to, v.yRot(), v.xRot(), true);
            restoreState(p, v);
            removeBody(p.server, v);
            dropViewer(s, v);
        }
        clearReturnTag(p);
        BY_VIEWER.remove(p.getUUID());
        if (s.viewers().isEmpty()) finish(s);
    }

    /** Lights or darkens the basin this session plays at (previews have none). The BE syncs the flag to clients. */
    private static void setPlaying(Session s, boolean playing) {
        BlockPos pos = s.basinPos();
        ResourceKey<Level> dim = s.basinDim();
        if (pos == null || dim == null || s.level == null || s.level.getServer() == null) return;
        ServerLevel where = s.level.getServer().getLevel(dim);
        if (where != null && where.isLoaded(pos) && where.getBlockEntity(pos) instanceof PensieveBlockEntity basin) {
            basin.setPlaying(playing);
        }
    }

    private static void dropViewer(Session s, Session.Viewer v) {
        s.viewers().remove(v.id());
        BY_VIEWER.remove(v.id(), s);
    }

    /**
     * Last viewer gone (or all handed over): release the builder's tickets first (shared ticket type), then clear
     * and free the slot. A standby next scene goes with it.
     */
    private static void finish(Session s) {
        SESSIONS.remove(s);
        // Ending without a hand-over: the scene built ahead for it will never be watched.
        Session standby = s.nextSession;
        s.nextSession = null;
        if (standby != null && standby.standby) finish(standby);
        s.phase = Session.Phase.CLOSING;
        setPlaying(s, false);
        releaseBasin(s);
        List<BlockPos> placed = new ArrayList<>();
        if (s.builder != null) {
            s.builder.release();
            placed.addAll(s.builder.placed());
        }
        placed.addAll(s.eventPlaced);
        if (placed.isEmpty()) {
            RegionData.get(s.level).remove(s.slot());
            allocator.release(s.slot());
        } else {
            CLEARING.add(new Clearing(s.level, s.slot(), SetBuilder.clear(s.level, placed), null));
        }
    }

    // ---- Login, logout, shutdown ----------------------------------------------------------------------------

    /** Logging out mid-memory: out of the session at once and back to the return point before the save. */
    public static void onLogout(ServerPlayer player) {
        WAKES.remove(player.getUUID());
        Session s = BY_VIEWER.get(player.getUUID());
        if (s == null) return;
        Session.Viewer v = s.viewers().get(player.getUUID());
        if (v != null) {
            moveBack(player, v);
            dropViewer(s, v);
        }
        BY_VIEWER.remove(player.getUUID());
        if (s.viewers().isEmpty()) finish(s);
    }

    /**
     * Logging in with a return tag (the server crashed or stopped mid-memory without moving them) or inside the
     * memory dimension: back to the return point, ghost state undone, tag cleared.
     */
    public static void onLogin(ServerPlayer player) {
        if (isViewer(player)) return;
        CompoundTag mod = PlayerMagicData.getModData(player);
        if (!mod.contains(RETURN_TAG, Tag.TAG_COMPOUND)) {
            if (PensieveDimension.isMemory(player.level())) {
                // No record of where they came from: the world spawn is the only safe place.
                ServerLevel overworld = player.server.overworld();
                moveTo(player, overworld, Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), player.getYRot(), 0f,
                        true);
                player.gameMode.getGameModeForPlayer().updatePlayerAbilities(player.getAbilities());
                restoreState(player, player.getAbilities().mayfly, player.getAbilities().flying, false, false, null);
            }
            return;
        }
        CompoundTag t = mod.getCompound(RETURN_TAG);
        ResourceLocation dimId = ResourceLocation.tryParse(t.getString("Dim"));
        ServerLevel back = dimId == null ? null
                : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        Vec3 to = new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z"));
        if (back == null) {
            back = player.server.overworld();
            to = Vec3.atBottomCenterOf(back.getSharedSpawnPos());
        }
        // Always a real dimension change at login: it resyncs the client's level whatever it was saved in.
        moveTo(player, back, to, t.getFloat("YRot"), t.getFloat("XRot"), true);
        boolean mayfly = t.contains("Mayfly") ? t.getBoolean("Mayfly") : player.getAbilities().mayfly;
        boolean flying = t.contains("Flying") ? t.getBoolean("Flying") : player.getAbilities().flying;
        GameType mode = t.contains("Mode", Tag.TAG_INT) ? GameType.byId(t.getInt("Mode")) : null;
        restoreState(player, mayfly, flying, t.getBoolean("Invisible"), t.getBoolean("Invulnerable"), mode);
        clearReturnTag(player);
    }

    /** Server stopping: everyone back now, every job's tickets dropped. Unfinished clears resume next start. */
    public static void endAll() {
        MinecraftServer server = initServer;
        for (Session s : new ArrayList<>(SESSIONS)) {
            for (Session.Viewer v : new ArrayList<>(s.viewers().values())) {
                ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(v.id());
                if (p != null) moveBack(p, v);
                else if (server != null) removeBody(server, v);
                dropViewer(s, v);
            }
            if (s.builder != null) s.builder.release();
            releaseBasin(s);
        }
        for (Clearing c : CLEARING) c.release();
        SESSIONS.clear();
        CLEARING.clear();
        BY_VIEWER.clear();
        allocator = new RegionAllocator();
        initServer = null;
        initLevel = null;
        testLevel = null;
        testScenes = null;
        testBudget = -1;
    }

    private static void writeReturnTag(ServerPlayer p, Session.Viewer v) {
        CompoundTag t = new CompoundTag();
        t.putString("Dim", v.returnDim().location().toString());
        t.putDouble("X", v.returnPos().x);
        t.putDouble("Y", v.returnPos().y);
        t.putDouble("Z", v.returnPos().z);
        t.putFloat("YRot", v.yRot());
        t.putFloat("XRot", v.xRot());
        t.putBoolean("Mayfly", v.wasMayfly);
        t.putBoolean("Flying", v.wasFlying);
        t.putBoolean("Invisible", v.wasInvisible);
        t.putBoolean("Invulnerable", v.wasInvulnerable);
        if (v.wasMode != null) t.putInt("Mode", v.wasMode.getId());
        CompoundTag mod = PlayerMagicData.getModData(p);
        mod.put(RETURN_TAG, t);
        PlayerMagicData.saveModData(p, mod);
    }

    static void clearReturnTagAfterDeath(ServerPlayer p, ServerPlayer original) {
        clearReturnTag(original);
        clearReturnTag(p);
    }

    private static void clearReturnTag(ServerPlayer p) {
        CompoundTag mod = PlayerMagicData.getModData(p);
        if (mod.contains(RETURN_TAG)) {
            mod.remove(RETURN_TAG);
            PlayerMagicData.saveModData(p, mod);
        }
    }

    // ---- Restart leftovers ----------------------------------------------------------------------------------

    /** Once per server and memory level: reserve and clear every slot an earlier run left blocks in. */
    private static void ensureInit(MinecraftServer server) {
        if (initServer != server) {
            SESSIONS.clear();
            CLEARING.clear();
            BY_VIEWER.clear();
            allocator = new RegionAllocator();
            initServer = server;
            initLevel = null;
        }
        ServerLevel memory = memoryLevel(server);
        if (memory == null || memory == initLevel) return;
        initLevel = memory;
        RegionData data = RegionData.get(memory);
        for (Map.Entry<Integer, int[]> e : data.boxes.entrySet()) {
            int slot = e.getKey();
            boolean inUse = false;
            for (Session s : SESSIONS) inUse |= s.slot() == slot && s.level == memory;
            if (inUse) continue;
            allocator.reserve(slot);
            CLEARING.add(new Clearing(memory, slot, null, new BoxClear(memory, e.getValue())));
            LOGGER.info("Pensieve: clearing blocks an earlier run left in memory slot {}", slot);
        }
    }

    /** A slot being emptied: either the exact placed list, or (after a restart) its saved bounding box. */
    private record Clearing(ServerLevel level, int slot, @Nullable SetBuilder.ClearJob list, @Nullable BoxClear box) {
        boolean advance(int[] budget) {
            return list != null ? list.advance(budget) : box == null || box.advance(budget);
        }

        void release() {
            if (list != null) list.release();
            if (box != null) box.release();
        }
    }

    /**
     * Clears every non-air block in a box, a layer at a time, skipping chunk sections that hold only air. Holds a
     * ticket on the chunk it is working in only.
     */
    static final class BoxClear {
        private final ServerLevel level;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private final List<ChunkPos> chunks = new ArrayList<>();
        private int chunkIndex;
        private int y;
        @Nullable private ChunkPos held;

        BoxClear(ServerLevel level, int[] box) {
            this.level = level;
            this.minX = box[0];
            this.minY = Math.max(box[1], level.getMinBuildHeight());
            this.minZ = box[2];
            this.maxX = box[3];
            this.maxY = Math.min(box[4], level.getMaxBuildHeight() - 1);
            this.maxZ = box[5];
            for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
                for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) chunks.add(new ChunkPos(cx, cz));
            }
            this.y = minY;
        }

        boolean advance(int budget) {
            return advance(new int[]{budget});
        }

        /** Clears against a shared budget: {@code left[0]} goes down per block set to air. */
        boolean advance(int[] left) {
            if (left[0] <= 0) return false;
            int visits = 0;
            int maxVisits = left[0] * 64;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (chunkIndex < chunks.size()) {
                ChunkPos c = chunks.get(chunkIndex);
                if (held == null) {
                    held = c;
                    level.getChunkSource().addRegionTicket(SetBuilder.TICKET, c, 0, c);
                }
                LevelChunk chunk = level.getChunk(c.x, c.z);
                int x0 = Math.max(minX, c.getMinBlockX()), x1 = Math.min(maxX, c.getMaxBlockX());
                int z0 = Math.max(minZ, c.getMinBlockZ()), z1 = Math.min(maxZ, c.getMaxBlockZ());
                while (y <= maxY) {
                    int section = chunk.getSectionIndex(y);
                    if (section < 0 || section >= chunk.getSections().length || chunk.getSection(section).hasOnlyAir()) {
                        y = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(y)) + 16;
                        continue;
                    }
                    for (int x = x0; x <= x1; x++) {
                        for (int z = z0; z <= z1; z++) {
                            pos.set(x, y, z);
                            visits++;
                            if (!chunk.getBlockState(pos).isAir()) {
                                SetBuilder.setAir(level, pos);
                                left[0]--;
                            }
                        }
                    }
                    y++;
                    if (left[0] <= 0 || visits >= maxVisits) {
                        SetBuilder.clearTicks(level, new BoundingBox(x0, minY, z0, x1, maxY, z1));
                        return false;
                    }
                }
                // Ticks an earlier run saved with the chunk go with its blocks.
                if (minY <= maxY) SetBuilder.clearTicks(level, new BoundingBox(x0, minY, z0, x1, maxY, z1));
                release();
                chunkIndex++;
                y = minY;
            }
            return true;
        }

        void release() {
            if (held == null) return;
            level.getChunkSource().removeRegionTicket(SetBuilder.TICKET, held, 0, held);
            held = null;
        }
    }

    /** Per slot, the bounding box of every block a set put there that has not been cleared yet. */
    static final class RegionData extends SavedData {
        private static final String NAME = "hogwartsmagic_pensieve_regions";
        final Map<Integer, int[]> boxes = new HashMap<>();

        static RegionData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(RegionData::new, RegionData::load, null), NAME);
        }

        void expand(int slot, BlockPos p) {
            int[] b = boxes.get(slot);
            if (b == null) {
                boxes.put(slot, new int[]{p.getX(), p.getY(), p.getZ(), p.getX(), p.getY(), p.getZ()});
                setDirty();
                return;
            }
            if (p.getX() < b[0] || p.getY() < b[1] || p.getZ() < b[2] || p.getX() > b[3] || p.getY() > b[4]
                    || p.getZ() > b[5]) {
                b[0] = Math.min(b[0], p.getX());
                b[1] = Math.min(b[1], p.getY());
                b[2] = Math.min(b[2], p.getZ());
                b[3] = Math.max(b[3], p.getX());
                b[4] = Math.max(b[4], p.getY());
                b[5] = Math.max(b[5], p.getZ());
                setDirty();
            }
        }

        void remove(int slot) {
            if (boxes.remove(slot) != null) setDirty();
        }

        static RegionData load(CompoundTag tag, HolderLookup.Provider registries) {
            RegionData data = new RegionData();
            ListTag list = tag.getList("Slots", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag e = list.getCompound(i);
                int[] box = e.getIntArray("Box");
                if (box.length == 6) data.boxes.put(e.getInt("Slot"), box);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Map.Entry<Integer, int[]> e : boxes.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putInt("Slot", e.getKey());
                c.putIntArray("Box", e.getValue());
                list.add(c);
            }
            tag.put("Slots", list);
            return tag;
        }
    }
}
