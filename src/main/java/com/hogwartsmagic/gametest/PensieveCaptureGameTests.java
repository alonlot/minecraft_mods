package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.PensieveBlockEntity;
import com.hogwartsmagic.pensieve.PensieveDimension;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.RecordedMemoryItem;
import com.hogwartsmagic.pensieve.Session;
import com.hogwartsmagic.pensieve.capture.MemoryCapture;
import com.hogwartsmagic.pensieve.capture.CaptureLogs;
import com.hogwartsmagic.pensieve.capture.EntityHistory;
import com.hogwartsmagic.pensieve.capture.LevelLog;
import com.hogwartsmagic.pensieve.capture.Recording;
import com.hogwartsmagic.pensieve.capture.RecordingAssembly;
import com.hogwartsmagic.pensieve.capture.RecordingCodec;
import com.hogwartsmagic.pensieve.capture.RecordingStore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

/** The always-on recorder and the assembly of a recording, exercised in a real level with a mock player. */
@GameTestHolder(ExampleMod.MODID)
public final class PensieveCaptureGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final String BATCH = "capture";

    private PensieveCaptureGameTests() {
    }

    private static void pass(String name) {
        LOGGER.info("PENSIEVE-CAPTURE-GAMETEST PASS {}", name);
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0f, 0f);
        p.setOnGround(true);
        return p;
    }

    private static void logout(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer p : players) {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    /** Assembles and waits: the background half never needs the server thread, so blocking it here is safe. */
    private static Recording assembled(GameTestHelper helper, ServerPlayer owner) {
        try {
            return RecordingAssembly.assemble(owner, UUID.randomUUID(), "test").get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            helper.fail("assembly failed: " + e);
            throw new IllegalStateException(e);
        }
    }

    /** The changes logged at one position, oldest first, as {old, new} state ids. */
    private static List<int[]> changesAt(LevelLog log, BlockPos pos) {
        List<int[]> out = new ArrayList<>();
        log.forEachChange(0, (tick, p, o, n) -> {
            if (p == pos.asLong()) {
                out.add(new int[] {o, n});
            }
        });
        return out;
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void blockChangeIsLoggedWithOldAndNew(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 0));
        helper.runAfterDelay(5, () -> {
            int air = Block.getId(level.getBlockState(pos));
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            LevelLog log = CaptureLogs.of(level);
            helper.assertTrue(log != null, "the level has a log while a player is in it");
            List<int[]> changes = changesAt(log, pos);
            helper.assertTrue(changes.size() == 2, "two changes expected, got " + changes.size());
            helper.assertTrue(changes.get(0)[0] == air && changes.get(0)[1] == Block.getId(Blocks.STONE.defaultBlockState()),
                    "first change goes from the old state to stone");
            helper.assertTrue(changes.get(1)[0] == Block.getId(Blocks.STONE.defaultBlockState())
                    && changes.get(1)[1] == Block.getId(Blocks.DIRT.defaultBlockState()), "second change goes stone to dirt");
            logout(helper, p);
            pass("blockChangeIsLoggedWithOldAndNew");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void memoryDimensionIsNotRecorded(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        // The GameTestServer builds only its flat Test Level, so the memory level is usually absent here.
        ServerLevel memory = PensieveDimension.level(helper.getLevel().getServer());
        helper.assertTrue(CaptureLogs.of(helper.getLevel()) != null, "an ordinary level is recorded");
        if (memory != null) {
            BlockPos pos = new BlockPos(0, 100, 0);
            memory.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(CaptureLogs.of(memory) == null, "the memory dimension is never recorded");
            helper.assertTrue(CaptureLogs.existing(memory) == null, "and keeps no log");
            memory.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        logout(helper, p);
        pass("memoryDimensionIsNotRecorded");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void farChangeIsIgnored(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(5, () -> {
            double farthest = 0;
            for (ServerPlayer other : level.players()) {
                farthest = Math.max(farthest, Math.abs(other.getX()) + Math.abs(other.getZ()));
            }
            BlockPos far = new BlockPos((int) farthest + 200, 80, (int) farthest + 200);
            BlockPos near = helper.absolutePos(new BlockPos(1, 2, 0));
            level.setBlock(far, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(near, Blocks.STONE.defaultBlockState(), 3);
            LevelLog log = CaptureLogs.of(level);
            helper.assertTrue(changesAt(log, far).isEmpty(), "a change 200 blocks from every player is not logged");
            helper.assertTrue(!changesAt(log, near).isEmpty(), "a change next to the player is");
            level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
            logout(helper, p);
            pass("farChangeIsIgnored");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void entityIsSampledWithData(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ServerLevel level = helper.getLevel();
        Sheep sheep = helper.spawn(EntityType.SHEEP, 1, 2, 1);
        sheep.setNoAi(true);
        long spawned = level.getGameTime();
        helper.runAfterDelay(5, () -> sheep.setColor(DyeColor.RED));
        helper.runAfterDelay(15, () -> {
            LevelLog log = CaptureLogs.of(level);
            EntityHistory h = log.entities.get(sheep.getUUID());
            helper.assertTrue(h != null, "the sheep has a history");
            EntityHistory.Slice s = h.copy(0, Long.MAX_VALUE, level.getGameTime());
            helper.assertTrue(s.lanes[0].length >= 10, "it was sampled every tick, got " + s.lanes[0].length);
            boolean patched = s.data.stream().anyMatch(d -> d.tick >= spawned + 5);
            helper.assertTrue(patched, "dyeing it left a data patch at tick >= spawn + 5");
            logout(helper, p);
            pass("entityIsSampledWithData");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 300)
    public static void assembleRebuildsWindowStart(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 1));
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3); // before the window starts
        helper.runAfterDelay(30, () -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
        helper.runAfterDelay(65, () -> {
            Recording rec = assembled(helper, p);
            BlockPos c = p.blockPosition();
            int dx = pos.getX() - rec.centre()[0];
            int dy = pos.getY() - rec.centre()[1];
            int dz = pos.getZ() - rec.centre()[2];
            int size = rec.blocks().size();
            int cell = rec.blocks().cells()[Recording.Blocks.index(dx, dy, dz, size)];
            helper.assertTrue(cell > 0 && rec.blocks().palette().get(cell).equals("minecraft:stone"),
                    "the snapshot cell is stone, centre " + c + " cell " + cell);
            List<Recording.BlockChange> mine = rec.blocks().changes().stream()
                    .filter(b -> b.x() == dx && b.y() == dy && b.z() == dz).toList();
            helper.assertTrue(mine.size() == 1, "one change at the block, got " + mine.size());
            helper.assertTrue(mine.get(0).state() == 0, "it goes to air");
            helper.assertTrue(Math.abs(mine.get(0).tick() - 30) <= 8, "at about tick 30, got " + mine.get(0).tick());
            helper.assertTrue(rec.tracks().get(0).uuid().equals(p.getUUID()), "the owner is track 0");
            logout(helper, p);
            pass("assembleRebuildsWindowStart");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 300)
    public static void captureAfterTeleportStartsAfterIt(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ServerLevel level = helper.getLevel();
        long[] teleportedAt = new long[1];
        helper.runAfterDelay(10, () -> {
            Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 4.5));
            p.teleportTo(level, at.x, at.y, at.z, java.util.Set.of(), 0f, 0f);
            // A plain teleportTo posts no event; the commands, pearls and chorus fruit do, so post the command one.
            MinecraftForge.EVENT_BUS.post(new EntityTeleportEvent.TeleportCommand(p, at.x, at.y, at.z));
            teleportedAt[0] = level.getGameTime();
        });
        helper.runAfterDelay(65, () -> {
            long assembledAt = level.getGameTime();
            Recording rec = assembled(helper, p);
            long expectedMax = assembledAt - teleportedAt[0];
            helper.assertTrue(rec.length() <= expectedMax && rec.length() >= 40,
                    "the memory starts after the teleport: length " + rec.length() + ", max " + expectedMax);
            logout(helper, p);
            pass("captureAfterTeleportStartsAfterIt");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void respawnClearsTheKilledMark(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.monster.Zombie body = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        body.setNoAi(true);
        EntityHistory h = new EntityHistory(body.getUUID(), "minecraft:zombie", false, true, 200);
        h.sample(body, 1, level);
        body.setHealth(0f);
        h.killed = true; // what the death event sets
        h.sample(body, 2, level);
        helper.assertTrue(h.copy(0, 10, 2).killed, "a dying entity stays marked as killed");
        // The same uuid comes back alive, as a respawned player does.
        net.minecraft.world.entity.monster.Zombie again = helper.spawn(EntityType.ZOMBIE, 1, 2, 1);
        again.setNoAi(true);
        h.sample(again, 3, level);
        EntityHistory.Slice s = h.copy(0, 10, 3);
        helper.assertTrue(!s.killed && !s.gone, "after the respawn the track is no longer killed");
        pass("respawnClearsTheKilledMark");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void betweenTickDataChangeIsRecorded(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        ServerLevel level = helper.getLevel();
        Sheep sheep = helper.spawn(EntityType.SHEEP, 1, 2, 1);
        sheep.setNoAi(true);
        int[] before = new int[1];
        helper.runAfterDelay(10, () -> {
            before[0] = CaptureLogs.of(level).entities.get(sheep.getUUID()).copy(0, Long.MAX_VALUE, level.getGameTime()).data.size();
            // A task queued like a packet handler: it runs between two server ticks.
            level.getServer().execute(() -> sheep.setColor(DyeColor.PINK));
        });
        helper.runAfterDelay(20, () -> {
            int after = CaptureLogs.of(level).entities.get(sheep.getUUID()).copy(0, Long.MAX_VALUE, level.getGameTime()).data.size();
            helper.assertTrue(after > before[0], "the change made between ticks was recorded: " + before[0] + " -> " + after);
            logout(helper, p);
            pass("betweenTickDataChangeIsRecorded");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public static void storeRoundTrips(GameTestHelper helper) throws Exception {
        UUID id = UUID.randomUUID();
        Recording.Blocks blocks = new Recording.Blocks(List.of("minecraft:air", "minecraft:stone"), 1,
                new int[] {-1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1, 1, -1}, List.of(),
                List.of(new Recording.BlockChange(4, 0, 1, 0, 0)));
        float[] zeros = new float[3];
        Recording.Track track = new Recording.Track(0, "minecraft:player", UUID.randomUUID(), "Owner", true, null, null,
                0, 2, Recording.REMOVAL_NONE, new float[] {0f, 0.5f, 1f}, zeros, zeros, zeros, zeros, zeros, zeros,
                List.of(), List.of(), List.of());
        Recording rec = new Recording(id, track.uuid(), "Owner", "Round trip", 2, "minecraft:overworld", new int[] {1, 2, 3},
                1, 8, 6000, blocks, List.of(track), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var server = helper.getLevel().getServer();
        RecordingStore.save(server, rec).get(10, TimeUnit.SECONDS);
        RecordingStore.clearCache();
        Recording back = RecordingStore.load(server, id).orElse(null);
        helper.assertTrue(back != null, "the file was read back");
        helper.assertTrue(java.util.Arrays.equals(RecordingCodec.write(rec, false), RecordingCodec.write(back, false)),
                "the recording is unchanged by the trip through the world save");
        helper.assertTrue(RecordingStore.clientBlob(id) != null, "the client blob can be built");
        java.nio.file.Files.deleteIfExists(RecordingStore.dir(server).resolve(id + ".hpm"));
        RecordingStore.clearCache();
        helper.assertTrue(RecordingStore.load(server, id).isEmpty(), "a missing file loads as empty");
        pass("storeRoundTrips");
        helper.succeed();
    }

    // ---- Phials and the capture gesture ----

    private static void hold(ServerPlayer p, InteractionHand hand, ItemStack stack) {
        p.setItemInHand(hand, stack);
    }

    private static ItemStack wand() {
        return new ItemStack(ExampleMod.HARRY_POTTER_WAND.get());
    }

    private static ItemStack phial() {
        return new ItemStack(ExampleMod.EMPTY_MEMORY_PHIAL.get());
    }

    /**
     * How many recordings in the world save belong to this player. Other tests write recordings in parallel, so a
     * plain file count would race; the owner is what a test can call its own.
     */
    private static long recordingsOf(ServerLevel level, java.util.UUID owner) throws java.io.IOException {
        java.nio.file.Path dir = RecordingStore.dir(level.getServer());
        if (!java.nio.file.Files.isDirectory(dir)) {
            return 0;
        }
        try (var files = java.nio.file.Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".hpm")).filter(f -> {
                try {
                    return RecordingCodec.read(java.nio.file.Files.readAllBytes(f)).owner().equals(owner);
                } catch (java.io.IOException | RuntimeException e) {
                    return false;
                }
            }).count();
        }
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 300)
    public static void captureTurnsPhialIntoMemory(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setShiftKeyDown(true);
        hold(p, InteractionHand.MAIN_HAND, wand());
        hold(p, InteractionHand.OFF_HAND, phial());
        helper.runAfterDelay(65, () -> MemoryCapture.capture(p));
        // The future finishes off-thread and the hand-over runs on the server thread: poll for the result.
        helper.succeedWhen(() -> {
            ItemStack held = p.getOffhandItem();
            helper.assertTrue(held.is(ExampleMod.MEMORY.get()), "the offhand holds the Memory, not " + held);
            java.util.UUID id = RecordedMemoryItem.memoryId(held);
            helper.assertTrue(id != null, "the Memory carries its recording id");
            Recording rec = RecordingStore.load(helper.getLevel().getServer(), id).orElse(null);
            helper.assertTrue(rec != null, "the recording is in the store");
            helper.assertTrue(rec.ownerName().equals(p.getGameProfile().getName()), "its owner is the player");
            helper.assertTrue(rec.owner().equals(p.getUUID()), "owned by the player's uuid");
            net.minecraft.network.chat.contents.TranslatableContents name =
                    (net.minecraft.network.chat.contents.TranslatableContents) held.getHoverName().getContents();
            helper.assertTrue(name.getKey().equals("item.hogwartsmagic.memory.named")
                    && name.getArgs()[0].equals(p.getGameProfile().getName()), "named for its owner");
            helper.assertTrue(p.getMainHandItem().getItem() == ExampleMod.HARRY_POTTER_WAND.get(), "the wand stays in hand");
            logout(helper, p);
            pass("captureTurnsPhialIntoMemory");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void noWandMeansHintAndNoCapture(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setShiftKeyDown(true);
        hold(p, InteractionHand.OFF_HAND, phial());
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(65, () -> {
            MemoryCapture.capture(p);
            helper.runAfterDelay(20, () -> {
                try {
                    helper.assertTrue(p.getOffhandItem().is(ExampleMod.EMPTY_MEMORY_PHIAL.get()), "still a phial");
                    helper.assertTrue(p.getOffhandItem().getCount() == 1, "and not spent");
                    helper.assertTrue(recordingsOf(level, p.getUUID()) == 0, "no recording written");
                } catch (java.io.IOException e) {
                    helper.fail(e.toString());
                }
                logout(helper, p);
                pass("noWandMeansHintAndNoCapture");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void insideMemoryRefused(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setShiftKeyDown(true);
        hold(p, InteractionHand.MAIN_HAND, wand());
        hold(p, InteractionHand.OFF_HAND, phial());
        ServerLevel memory = PensieveDimension.level(helper.getLevel().getServer());
        if (memory == null) {
            // The GameTestServer builds only its flat Test Level; the refusal itself is PensieveDimension.isMemory.
            LOGGER.info("PENSIEVE-CAPTURE-GAMETEST insideMemoryRefused: no memory level here, nothing to place the player in");
            logout(helper, p);
            pass("insideMemoryRefused");
            helper.succeed();
            return;
        }
        p.teleportTo(memory, 0.5, 100, 0.5, java.util.Set.of(), 0f, 0f);
        helper.runAfterDelay(65, () -> {
            helper.assertTrue(PensieveDimension.isMemory(p.serverLevel()), "the player stands in the memory dimension");
            MemoryCapture.capture(p);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(p.getOffhandItem().is(ExampleMod.EMPTY_MEMORY_PHIAL.get()), "still a phial");
                p.teleportTo(helper.getLevel(), 0.5, 80, 0.5, java.util.Set.of(), 0f, 0f);
                logout(helper, p);
                pass("insideMemoryRefused");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public static void wandWithoutPhialChangesNothing(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.setShiftKeyDown(true);
        ItemStack held = wand();
        hold(p, InteractionHand.MAIN_HAND, held);
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(65, () -> {
            MemoryCapture.capture(p);
            helper.runAfterDelay(20, () -> {
                try {
                    helper.assertTrue(p.getMainHandItem() == held, "the wand is untouched");
                    helper.assertTrue(p.getOffhandItem().isEmpty(), "nothing appeared in the offhand");
                    helper.assertTrue(recordingsOf(level, p.getUUID()) == 0, "no recording written");
                } catch (java.io.IOException e) {
                    helper.fail(e.toString());
                }
                logout(helper, p);
                pass("wandWithoutPhialChangesNothing");
                helper.succeed();
            });
        });
    }

    // ---- Recorded sessions ----
    // The GameTestServer builds no memory dimension, so the test level stands in for it (PensieveSessions.overrideForTests)
    // and the recordings are hand-built and served by RecordingStore.overrideForTests. In production the same code
    // runs in hogwartsmagic:memory with recordings read from the world save; that path is checked by runServer only.

    private static final String RECORDED = "recorded_sessions";
    private static final BlockPos BASIN = new BlockPos(1, 2, 1);
    private static final java.util.Map<UUID, Recording> RECORDINGS = new ConcurrentHashMap<>();

    /** The override outlives a batch otherwise, and the capture batch reads real files through RecordingStore. */
    // Forge prefixes the batch of a test with its mod id; an after-batch hook must name the prefixed one.
    @AfterBatch(batch = ExampleMod.MODID + "." + RECORDED)
    public static void afterRecordedSessions(ServerLevel level) {
        RecordingStore.overrideForTests(null);
        RECORDINGS.clear();
        LOGGER.info("PENSIEVE-CAPTURE-GAMETEST recorded_sessions batch over: recording override cleared");
    }

    private static void recordedSeam(GameTestHelper helper) {
        PensieveSessions.overrideForTests(helper.getLevel(), id -> Optional.empty());
        RecordingStore.overrideForTests(id -> Optional.ofNullable(RECORDINGS.get(id)));
    }

    /**
     * A 5 cubed island (size 2) whose only blocks are a 3 by 1 by 3 stone floor at y = -1; the palette is
     * air, stone, dirt. The owner stands at the middle, facing south.
     */
    private static Recording floorRecording(UUID id, int length, int radius, List<Recording.BlockChange> changes) {
        int size = 2;
        int[] cells = new int[5 * 5 * 5];
        java.util.Arrays.fill(cells, -1);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                cells[Recording.Blocks.index(x, -1, z, size)] = 1;
            }
        }
        Recording.Blocks blocks = new Recording.Blocks(List.of("minecraft:air", "minecraft:stone", "minecraft:dirt"),
                size, cells, List.of(), changes);
        UUID owner = UUID.randomUUID();
        float[] zero = new float[1];
        Recording.Track track = new Recording.Track(0, "minecraft:player", owner, "Owner", true, null, null, 0, 0,
                Recording.REMOVAL_NONE, zero, zero, zero, zero, zero, zero, zero, List.of(), List.of(), List.of());
        Recording rec = new Recording(id, owner, "Owner", "Test", 1, "minecraft:overworld", new int[] {0, 64, 0}, radius,
                length, 6000, blocks, List.of(track), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        RECORDINGS.put(id, rec);
        return rec;
    }

    private static PensieveBlockEntity basinHolding(GameTestHelper helper, BlockPos at, UUID memoryId) {
        helper.setBlock(at, ExampleMod.PENSIEVE.get());
        PensieveBlockEntity be = (PensieveBlockEntity) helper.getBlockEntity(at);
        be.pour(RecordedMemoryItem.create(ExampleMod.MEMORY.get(), memoryId, UUID.randomUUID(), "Owner", 1, 10), null);
        return be;
    }

    /** A mock player standing at (x, 2, z) of the template. */
    private static ServerPlayer playerNear(GameTestHelper helper, double x, double z) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(x, 2, z));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 30f, 10f);
        return p;
    }

    /** Survival, and past the spawn grace, so a hit forwarded from the body counts. */
    private static void survival(ServerPlayer p) {
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for (int i = 0; i < 70; i++) {
            p.tick();
        }
    }

    /** Runs {@code then} once, on the first tick the player is a free ghost in a session that is playing. */
    private static void whenPlaying(GameTestHelper helper, ServerPlayer p, Consumer<Session> then) {
        AtomicBoolean fired = new AtomicBoolean();
        helper.onEachTick(() -> {
            Optional<Session> s = PensieveSessions.sessionOf(p);
            if (!fired.get() && s.isPresent() && s.get().phase() == Session.Phase.PLAY
                    && PensieveSessions.isFreeGhost(p)) {
                fired.set(true);
                then.accept(s.get());
            }
        });
    }

    private static Vec3 islandCentre(Session s) {
        return new Vec3(s.origin().getX() + 0.5, s.origin().getY(), s.origin().getZ() + 0.5);
    }

    @GameTest(template = EMPTY, batch = RECORDED, timeoutTicks = 300)
    public static void recordedMemoryBuildsSnapshot(GameTestHelper helper) {
        recordedSeam(helper);
        UUID id = UUID.randomUUID();
        floorRecording(id, 200, 8, List.of());
        basinHolding(helper, BASIN, id);
        ServerPlayer p = playerNear(helper, 1.5, 2.5);
        helper.useBlock(BASIN, p);
        helper.assertTrue(PensieveSessions.isViewer(p), "diving into a recorded memory makes a viewer");
        helper.runAfterDelay(20, () -> {
            Session s = PensieveSessions.sessionOf(p).orElseThrow();
            helper.assertTrue(s.recording() != null && s.recording().id().equals(id), "the session keeps its recording");
            BlockPos o = s.origin();
            ServerLevel level = helper.getLevel();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    helper.assertTrue(level.getBlockState(o.offset(x, -1, z)).is(Blocks.STONE),
                            "stone at the floor cell " + x + "," + z);
                }
            }
            // Counted from the builder, not the world: other tests leave blocks in neighbouring slots' cubes.
            helper.assertTrue(s.placedBlocks() == 9, "only the 9 floor cells were placed, builder wrote " + s.placedBlocks());
            logout(helper, p);
            pass("recordedMemoryBuildsSnapshot");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = RECORDED, timeoutTicks = 400)
    public static void recordedBlockChangesReplayInOrder(GameTestHelper helper) {
        recordedSeam(helper);
        UUID id = UUID.randomUUID();
        // The floor cell (0,-1,0) starts as stone; at tick 5 it becomes dirt, at tick 15 air. Listed out of order.
        floorRecording(id, 300, 8, List.of(new Recording.BlockChange(15, 0, -1, 0, 0),
                new Recording.BlockChange(5, 0, -1, 0, 2)));
        basinHolding(helper, BASIN, id);
        ServerPlayer p = playerNear(helper, 1.5, 2.5);
        helper.useBlock(BASIN, p);
        AtomicBoolean seenStone = new AtomicBoolean();
        AtomicBoolean seenDirt = new AtomicBoolean();
        helper.onEachTick(() -> {
            Optional<Session> found = PensieveSessions.sessionOf(p);
            if (found.isEmpty() || found.get().phase() != Session.Phase.PLAY) {
                return;
            }
            Session s = found.get();
            long elapsed = helper.getLevel().getGameTime() - s.startGameTime();
            net.minecraft.world.level.block.state.BlockState at =
                    helper.getLevel().getBlockState(s.origin().offset(0, -1, 0));
            if (elapsed <= 3) {
                helper.assertTrue(at.is(Blocks.STONE), "before its change the cell is stone at PLAY + " + elapsed);
                seenStone.set(true);
            } else if (elapsed == 6) {
                helper.assertTrue(at.is(Blocks.DIRT), "at PLAY + 6 the cell is dirt, is " + at);
                seenDirt.set(true);
            } else if (elapsed == 16) {
                helper.assertTrue(seenStone.get() && seenDirt.get(), "the earlier checks ran");
                helper.assertTrue(at.isAir(), "at PLAY + 16 the cell is air, is " + at);
                logout(helper, p);
                pass("recordedBlockChangesReplayInOrder");
                helper.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, batch = RECORDED, timeoutTicks = 400)
    public static void ghostIsClampedToIsland(GameTestHelper helper) {
        recordedSeam(helper);
        UUID id = UUID.randomUUID();
        floorRecording(id, 300, 8, List.of());
        basinHolding(helper, BASIN, id);
        ServerPlayer p = playerNear(helper, 1.5, 2.5);
        helper.useBlock(BASIN, p);
        whenPlaying(helper, p, s -> {
            Vec3 c = islandCentre(s);
            p.teleportTo(helper.getLevel(), c.x + 50, c.y, c.z, java.util.Set.of(), 0f, 0f);
            helper.runAfterDelay(3, () -> {
                double d = p.position().distanceTo(c);
                helper.assertTrue(d <= 8 + 0.01, "the ghost is back within the radius, " + d + " from the centre");
                helper.assertTrue(PensieveSessions.isViewer(p), "and still a viewer");
                helper.assertTrue(p.level() == helper.getLevel(), "and still in the memory level");
                logout(helper, p);
                pass("ghostIsClampedToIsland");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = RECORDED, timeoutTicks = 400)
    public static void ghostCanMoveInsideIsland(GameTestHelper helper) {
        recordedSeam(helper);
        UUID id = UUID.randomUUID();
        floorRecording(id, 300, 8, List.of());
        basinHolding(helper, BASIN, id);
        ServerPlayer p = playerNear(helper, 1.5, 2.5);
        helper.useBlock(BASIN, p);
        whenPlaying(helper, p, s -> {
            Vec3 to = islandCentre(s).add(3, 1, 2);
            p.teleportTo(helper.getLevel(), to.x, to.y, to.z, java.util.Set.of(), 0f, 0f);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(p.position().distanceTo(to) < 0.2,
                        "the ghost stays where it moved, " + p.position() + " vs " + to);
                helper.assertTrue(p.position().distanceTo(s.anchor()) > 1, "and is not pinned to the anchor");
                logout(helper, p);
                pass("ghostCanMoveInsideIsland");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = RECORDED, timeoutTicks = 100)
    public static void missingRecordingFadesQuietly(GameTestHelper helper) {
        recordedSeam(helper);
        UUID unknown = UUID.randomUUID();
        PensieveBlockEntity basin = basinHolding(helper, BASIN, unknown);
        ServerPlayer p = playerNear(helper, 1.5, 2.5);
        helper.useBlock(BASIN, p);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!PensieveSessions.isViewer(p), "no session starts for a recording that is not there");
            helper.assertTrue(unknown.equals(RecordedMemoryItem.memoryId(basin.memory())),
                    "the memory is still in the basin");
            helper.assertTrue(p.level() == helper.getLevel(), "the player never left");
            logout(helper, p);
            pass("missingRecordingFadesQuietly");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = RECORDED, timeoutTicks = 300)
    public static void recordedSessionLifecycle(GameTestHelper helper) {
        recordedSeam(helper);
        UUID id = UUID.randomUUID();
        floorRecording(id, 400, 8, List.of());
        BlockPos basinA = new BlockPos(0, 2, 0);
        BlockPos basinB = new BlockPos(1, 2, 1);
        BlockPos basinC = new BlockPos(2, 2, 2);
        basinHolding(helper, basinA, id);
        basinHolding(helper, basinB, id);
        basinHolding(helper, basinC, id);
        ServerPlayer a = playerNear(helper, 0.5, 1.5);
        ServerPlayer b = playerNear(helper, 1.5, 2.5);
        ServerPlayer c = playerNear(helper, 2.5, 1.5);
        survival(b);
        Vec3 retA = a.position();
        Vec3 retB = b.position();
        Vec3 retC = c.position();
        helper.useBlock(basinA, a);
        helper.useBlock(basinB, b);
        helper.useBlock(basinC, c);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(PensieveSessions.isViewer(a) && PensieveSessions.isViewer(b) && PensieveSessions.isViewer(c),
                    "all three are inside");
            // A: the basin is broken under them.
            helper.destroyBlock(basinA);
            // B: the body takes a heavy hit, which tears the viewer out.
            Session sb = PensieveSessions.sessionOf(b).orElseThrow();
            UUID bodyId = sb.viewers().get(b.getUUID()).bodyId();
            helper.assertTrue(bodyId != null && helper.getLevel().getEntity(bodyId) != null, "B has a body");
            helper.getLevel().getEntity(bodyId).hurt(helper.getLevel().damageSources().generic(), 14f);
            helper.assertTrue(sb.viewers().get(b.getUUID()).exitReason() == ExitReason.YANK, "B is on a YANK exit");
            // C: the server dies mid-memory. Only the return tag survives, and the next login uses it.
            net.minecraft.nbt.CompoundTag saved = com.hogwartsmagic.util.PlayerMagicData.getModData(c)
                    .getCompound(PensieveSessions.RETURN_TAG).copy();
            helper.assertTrue(saved.contains("Dim"), "C carries a return tag");
            PensieveSessions.onLogout(c);
            net.minecraft.nbt.CompoundTag mod = com.hogwartsmagic.util.PlayerMagicData.getModData(c);
            mod.put(PensieveSessions.RETURN_TAG, saved);
            com.hogwartsmagic.util.PlayerMagicData.saveModData(c, mod);
            Vec3 stray = helper.absoluteVec(new Vec3(0.5, 2, 0.5));
            c.teleportTo(helper.getLevel(), stray.x, stray.y, stray.z, java.util.Set.of(), 0f, 0f);
            PensieveSessions.onLogin(c);
            helper.assertTrue(c.position().distanceTo(retC) < 0.1, "C is back at the return point: " + c.position());
            helper.assertTrue(!com.hogwartsmagic.util.PlayerMagicData.getModData(c).contains(PensieveSessions.RETURN_TAG),
                    "and the return tag is gone");
        });
        helper.runAtTickTime(64, () -> {
            helper.assertTrue(!PensieveSessions.isViewer(a), "A left within 20 ticks of the break");
            helper.assertTrue(a.position().distanceTo(retA) < 0.1, "A is home: " + a.position());
            helper.assertTrue(!PensieveSessions.isViewer(b), "B was yanked out");
            helper.assertTrue(b.position().distanceTo(retB) < 0.1, "B is home: " + b.position());
            helper.assertTrue(!a.isInvisible() && !a.isInvulnerable() && !b.isInvisible(), "the ghost state is undone");
            logout(helper, a, b, c);
            pass("recordedSessionLifecycle");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = RECORDED, timeoutTicks = 400)
    public static void joinerGetsSameRecording(GameTestHelper helper) {
        recordedSeam(helper);
        UUID id = UUID.randomUUID();
        floorRecording(id, 400, 8, List.of());
        basinHolding(helper, BASIN, id);
        ServerPlayer first = playerNear(helper, 1.5, 2.5);
        ServerPlayer second = playerNear(helper, 0.5, 1.5);
        helper.useBlock(BASIN, first);
        helper.runAtTickTime(40, () -> helper.useBlock(BASIN, second));
        helper.runAtTickTime(130, () -> {
            Session s1 = PensieveSessions.sessionOf(first).orElse(null);
            Session s2 = PensieveSessions.sessionOf(second).orElse(null);
            helper.assertTrue(s1 != null && s1 == s2, "both watch the same session");
            helper.assertTrue(s1.phase() == Session.Phase.PLAY && s1.startGameTime() >= 0, "and it is playing");
            helper.assertTrue(s1.viewers().get(first.getUUID()).recordingSent(), "the first viewer was sent the recording");
            helper.assertTrue(s2.viewers().get(second.getUUID()).recordingSent(), "so was the joiner");
            helper.assertTrue(s2.recording().id().equals(id), "the same recording");
            logout(helper, first, second);
            pass("joinerGetsSameRecording");
            helper.succeed();
        });
    }
}
