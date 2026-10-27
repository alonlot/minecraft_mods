package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.PensieveDimension;
import com.hogwartsmagic.pensieve.RecordedMemoryItem;
import com.hogwartsmagic.pensieve.capture.MemoryCapture;
import com.hogwartsmagic.pensieve.capture.CaptureLogs;
import com.hogwartsmagic.pensieve.capture.EntityHistory;
import com.hogwartsmagic.pensieve.capture.LevelLog;
import com.hogwartsmagic.pensieve.capture.Recording;
import com.hogwartsmagic.pensieve.capture.RecordingAssembly;
import com.hogwartsmagic.pensieve.capture.RecordingCodec;
import com.hogwartsmagic.pensieve.capture.RecordingStore;
import net.minecraft.core.BlockPos;
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
import java.util.UUID;
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

    private static void logout(GameTestHelper helper, ServerPlayer p) {
        helper.getLevel().getServer().getPlayerList().remove(p);
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
}
