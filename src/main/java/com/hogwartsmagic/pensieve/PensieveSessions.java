package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.Config;
import com.hogwartsmagic.pensieve.net.PensieveEnterPacket;
import com.hogwartsmagic.pensieve.net.PensieveExitPacket;
import com.hogwartsmagic.pensieve.net.PensieveNet;
import com.hogwartsmagic.pensieve.net.PensieveStartPacket;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
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
    public static final String RETURN_TAG = "PensieveReturn";
    private static final int MAX_RADIUS = 200;

    private static final List<Session> SESSIONS = new ArrayList<>();
    private static final Map<UUID, Session> BY_VIEWER = new ConcurrentHashMap<>();
    private static final List<Clearing> CLEARING = new ArrayList<>();
    private static RegionAllocator allocator = new RegionAllocator();
    @Nullable private static MinecraftServer initServer;
    @Nullable private static ServerLevel initLevel;

    @Nullable private static ServerLevel testLevel;
    @Nullable private static Function<String, Optional<MemoryRegistry.LoadedScene>> testScenes;

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

    /** True while anyone is inside the memory playing from this basin (for the take-out rule). */
    public static boolean anyoneInside(Level level, BlockPos basin) {
        Session s = sessionAt(level.dimension(), basin);
        return s != null && !s.viewers().isEmpty();
    }

    @Nullable
    private static Session sessionAt(ResourceKey<Level> dim, BlockPos pos) {
        for (Session s : SESSIONS) {
            if (s.at(dim, pos)) return s;
        }
        return null;
    }

    // ---- Starting -------------------------------------------------------------------------------------------

    /** A dive into {@code basin}: joins the memory already playing there, or starts it. */
    public static void startOrJoin(ServerPlayer player, PensieveBlockEntity basin) {
        if (isViewer(player) || basin.getLevel() == null) return;
        MinecraftServer server = player.server;
        ensureInit(server);
        if (PensieveDimension.isMemory(player.level())) {
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.inside"), true);
            return;
        }
        ServerLevel memory = memoryLevel(server);
        if (memory == null) {
            LOGGER.error("Pensieve: the hogwartsmagic:memory dimension is not loaded; refusing the dive");
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.unavailable"), true);
            return;
        }
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
            LOGGER.warn("Pensieve: no scene '{}' is loaded for the memory in the basin at {}", id, pos);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.no_scene"), true);
            return;
        }
        session = createSession(player, memory, id, scene.get(), dim, pos, false);
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
        ensureInit(server);
        if (PensieveDimension.isMemory(player.level())) {
            player.sendSystemMessage(Component.translatable("message.hogwartsmagic.pensieve.inside"));
            return false;
        }
        ServerLevel memory = memoryLevel(server);
        if (memory == null) {
            LOGGER.error("Pensieve: the hogwartsmagic:memory dimension is not loaded; refusing the preview");
            player.sendSystemMessage(Component.translatable("message.hogwartsmagic.pensieve.unavailable"));
            return false;
        }
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
        Session session = createSession(player, memory, sceneId, loaded.get(), null, null, true);
        if (session == null) return false;
        addViewer(session, player, player.blockPosition());
        return true;
    }

    private static Optional<MemoryRegistry.LoadedScene> lookup(String id) {
        if (testScenes != null) return testScenes.apply(id);
        Optional<SceneDef> def = MemoryRegistry.get(id);
        Optional<String> json = MemoryRegistry.rawJson(id);
        if (def.isEmpty() || json.isEmpty()) return Optional.empty();
        return Optional.of(new MemoryRegistry.LoadedScene(def.get(), json.get()));
    }

    @Nullable
    private static Session createSession(ServerPlayer player, ServerLevel memory, String id,
                                         MemoryRegistry.LoadedScene scene, @Nullable ResourceKey<Level> basinDim,
                                         @Nullable BlockPos basinPos, boolean preview) {
        if (scene.json().length() > PensieveStartPacket.MAX_JSON) {
            LOGGER.warn("Pensieve: scene '{}' is {} chars, over the {} the start packet carries", id,
                    scene.json().length(), PensieveStartPacket.MAX_JSON);
            player.displayClientMessage(Component.translatable("message.hogwartsmagic.pensieve.too_big"), !preview);
            return null;
        }
        int slot = allocator.acquire();
        int[] o = RegionAllocator.originFor(slot);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        Session s = new Session(id, scene.json(), scene.def(), slot, origin, basinDim, basinPos, preview);
        s.level = memory;
        int radius = Math.max(1, Math.min(MAX_RADIUS, scene.def().radius()));
        s.builder = new SetBuilder(memory, origin, scene.def().set(), radius);
        // A no-op on a derived level (the memory dimension shares the overworld clock); the client applies the
        // scene's time of day itself from the scene JSON.
        memory.setDayTime(scene.def().set().timeOfDay());
        SESSIONS.add(s);
        return s;
    }

    private static void addViewer(Session s, ServerPlayer p, BlockPos enterAt) {
        Session.Viewer v = new Session.Viewer(p.getUUID(), p.position(), p.getYRot(), p.getXRot(),
                p.level().dimension(), SWALLOW_TICKS, null, p.getAbilities().mayfly, p.getAbilities().flying,
                p.isInvisible(), p.isInvulnerable());
        s.viewers().put(v.id(), v);
        BY_VIEWER.put(v.id(), s);
        writeReturnTag(p, v);
        p.stopUsingItem();
        p.setDeltaMovement(Vec3.ZERO);
        PensieveNet.send(p, new PensieveEnterPacket(enterAt));
    }

    // ---- Ticking --------------------------------------------------------------------------------------------

    public static void tick(MinecraftServer server) {
        ensureInit(server);
        int budget = Math.max(1, Config.pensieveBlocksPerTick);
        Iterator<Clearing> it = CLEARING.iterator();
        while (it.hasNext()) {
            Clearing c = it.next();
            if (c.advance(budget)) {
                it.remove();
                RegionData.get(c.level).remove(c.slot);
                allocator.release(c.slot);
            }
        }
        for (Session s : new ArrayList<>(SESSIONS)) {
            tickSession(server, s, budget);
        }
    }

    private static void tickSession(MinecraftServer server, Session s, int budget) {
        ServerLevel memory = s.level;
        for (Session.Viewer v : new ArrayList<>(s.viewers().values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(v.id());
            if (p == null) {
                dropViewer(s, v);
            } else if (!p.isAlive()) {
                // Died (Task 7: a big hit on the body): normal death; no move back, just undo the ghost state.
                restoreState(p, v.wasMayfly, v.wasFlying, v.wasInvisible, v.wasInvulnerable);
                clearReturnTag(p);
                dropViewer(s, v);
            } else {
                tickViewer(s, v, p, memory);
            }
        }
        if (s.viewers().isEmpty()) {
            finish(s);
            return;
        }
        long now = memory.getGameTime();
        if (s.phase == Session.Phase.BUILD) {
            if (!s.built && s.builder != null) {
                s.built = s.builder.advance(budget);
                recordPlaced(s);
            }
            if (s.built && s.firstCrossTime >= 0 && now - s.firstCrossTime >= s.scene().set().buildTicks()) {
                s.phase = Session.Phase.PLAY;
                s.startGameTime = now;
                for (Session.Viewer v : s.viewers().values()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(v.id());
                    if (p != null && v.stage == Session.Viewer.Stage.INSIDE) sendStart(p, s);
                }
            }
        } else if (s.phase == Session.Phase.PLAY) {
            applyBlockEvents(s, now);
            if (SessionClock.finished(s.startGameTime, now, s.scene().length())) {
                s.phase = Session.Phase.CLOSING;
                for (Session.Viewer v : s.viewers().values()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(v.id());
                    if (p != null) beginExit(p, v, ExitReason.END);
                }
            }
        }
    }

    private static void tickViewer(Session s, Session.Viewer v, ServerPlayer p, ServerLevel memory) {
        if (v.stage == Session.Viewer.Stage.SWALLOW) {
            if (p.level().dimension().equals(v.returnDim())) hold(p, v.returnPos());
            if (--v.swallowTicksLeft <= 0) cross(s, v, p, memory);
        } else if (v.stage == Session.Viewer.Stage.INSIDE) {
            if (p.level() == memory) hold(p, s.anchor());
            applyGhost(p);
        } else {
            if (v.crossed) {
                if (p.level() == memory) hold(p, s.anchor());
            } else if (p.level().dimension().equals(v.returnDim())) {
                hold(p, v.returnPos());
            }
            if (--v.exitTicksLeft <= 0) {
                moveBack(p, v);
                dropViewer(s, v);
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
        // Task 7: unless s.preview(), spawn the body at v.returnPos() facing the basin and set v.bodyId.
        sendStart(p, s);
    }

    private static void sendStart(ServerPlayer p, Session s) {
        long start = s.phase == Session.Phase.PLAY ? s.startGameTime : -1;
        PensieveNet.send(p, new PensieveStartPacket(s.sceneId(), s.rawJson(), s.origin(), start,
                s.scene().set().buildTicks(), s.preview()));
    }

    /**
     * Invisible, flying and invulnerable. Re-applied every tick: an effect change resets invisibility and a game
     * mode change resets flight. Task 7's forwarded body damage must bypass this invulnerability (and the
     * LivingAttackEvent cancel in {@link PensieveEvents}).
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

    private static void applyBlockEvents(Session s, long now) {
        float elapsed = SessionClock.elapsed(s.startGameTime, now, 0f);
        while (s.nextEvent < s.blockEvents.size() && s.blockEvents.get(s.nextEvent).t() <= elapsed) {
            SceneDef.EventDef e = s.blockEvents.get(s.nextEvent++);
            if (e.id() == null || e.at() == null || e.at().length < 3) continue;
            ResourceLocation rl = ResourceLocation.tryParse(e.id());
            Optional<Block> block = rl == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(rl);
            if (block.isEmpty()) continue;
            BlockPos pos = s.origin().offset(e.at()[0], e.at()[1], e.at()[2]);
            s.level.setBlock(pos, block.get().defaultBlockState(), SetBuilder.FLAGS);
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

    private static int exitDelay(ExitReason reason) {
        if (reason == ExitReason.YANK) return 10;
        if (reason == ExitReason.ABORT) return 20;
        return 30;
    }

    private static void moveBack(ServerPlayer p, Session.Viewer v) {
        ServerLevel back = p.server.getLevel(v.returnDim());
        if (back == null) back = p.server.overworld();
        moveTo(p, back, v.returnPos(), v.yRot(), v.xRot());
        restoreState(p, v.wasMayfly, v.wasFlying, v.wasInvisible, v.wasInvulnerable);
        clearReturnTag(p);
        // Task 7: remove the body v.bodyId() left at the basin.
    }

    private static void moveTo(ServerPlayer p, ServerLevel level, Vec3 at, float yRot, float xRot) {
        if (p.serverLevel() == level) {
            p.connection.teleport(at.x, at.y, at.z, yRot, xRot);
        } else {
            SeamlessMove.move(p, level, at.x, at.y, at.z, yRot, xRot);
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    private static void restoreState(ServerPlayer p, boolean mayfly, boolean flying, boolean invisible,
                                     boolean invulnerable) {
        p.setInvisible(invisible);
        p.setInvulnerable(invulnerable);
        p.getAbilities().mayfly = mayfly;
        p.getAbilities().flying = flying;
        p.onUpdateAbilities();
    }

    private static void dropViewer(Session s, Session.Viewer v) {
        s.viewers().remove(v.id());
        BY_VIEWER.remove(v.id(), s);
    }

    /** Last viewer gone: release the builder's tickets first (shared ticket type), then clear and free the slot. */
    private static void finish(Session s) {
        SESSIONS.remove(s);
        s.phase = Session.Phase.CLOSING;
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
                moveTo(player, overworld, Vec3.atBottomCenterOf(overworld.getSharedSpawnPos()), player.getYRot(), 0f);
                player.gameMode.getGameModeForPlayer().updatePlayerAbilities(player.getAbilities());
                restoreState(player, player.getAbilities().mayfly, player.getAbilities().flying, false, false);
            }
            return;
        }
        CompoundTag t = mod.getCompound(RETURN_TAG);
        ResourceLocation dimId = ResourceLocation.tryParse(t.getString("Dim"));
        ServerLevel back = dimId == null ? null
                : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (back == null) back = player.server.overworld();
        moveTo(player, back, new Vec3(t.getDouble("X"), t.getDouble("Y"), t.getDouble("Z")),
                t.getFloat("YRot"), t.getFloat("XRot"));
        boolean mayfly = t.contains("Mayfly") ? t.getBoolean("Mayfly") : player.getAbilities().mayfly;
        boolean flying = t.contains("Flying") ? t.getBoolean("Flying") : player.getAbilities().flying;
        restoreState(player, mayfly, flying, t.getBoolean("Invisible"), t.getBoolean("Invulnerable"));
        clearReturnTag(player);
    }

    /** Server stopping: everyone back now, every job's tickets dropped. Unfinished clears resume next start. */
    public static void endAll() {
        MinecraftServer server = initServer;
        for (Session s : new ArrayList<>(SESSIONS)) {
            for (Session.Viewer v : new ArrayList<>(s.viewers().values())) {
                ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(v.id());
                if (p != null) moveBack(p, v);
                dropViewer(s, v);
            }
            if (s.builder != null) s.builder.release();
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
        CompoundTag mod = PlayerMagicData.getModData(p);
        mod.put(RETURN_TAG, t);
        PlayerMagicData.saveModData(p, mod);
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
        boolean advance(int budget) {
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
            int sets = 0;
            int visits = 0;
            int maxVisits = budget * 64;
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
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), SetBuilder.FLAGS);
                                sets++;
                            }
                        }
                    }
                    y++;
                    if (sets >= budget || visits >= maxVisits) return false;
                }
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
