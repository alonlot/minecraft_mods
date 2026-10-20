package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.PensieveDimension;
import com.hogwartsmagic.pensieve.RegionAllocator;
import com.hogwartsmagic.pensieve.SetBuilder;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;

/** The memory dimension and the Pensieve set builder, exercised in the real memory level. */
@GameTestHolder(ExampleMod.MODID)
public final class PensieveGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private PensieveGameTests() {
    }

    /**
     * The memory level. The GameTestServer only has its single flat Test Level (it builds no other dimensions),
     * so there the builder is exercised in that level instead; the dimension itself is checked by runServer.
     */
    private static ServerLevel memoryOrTestLevel(GameTestHelper helper) {
        ServerLevel memory = PensieveDimension.level(helper.getLevel().getServer());
        return memory != null ? memory : helper.getLevel();
    }

    private static SceneDef.SetDef stoneFill() {
        return new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone", new int[]{0, 0, 0},
                new int[]{2, 0, 2})), List.of(), List.of(), 6000L, 20);
    }

    // Runs in the memory dimension when present, else in the Test Level (see memoryOrTestLevel).
    @GameTest(template = EMPTY)
    public static void setBuilderPlacesFillsWithinBudget(GameTestHelper helper) {
        ServerLevel memory = memoryOrTestLevel(helper);
        int[] o = RegionAllocator.originFor(7);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        SetBuilder builder = new SetBuilder(memory, origin, stoneFill(), 20);
        helper.assertTrue(builder.total() == 9, "expected 9 blocks, got " + builder.total());
        helper.assertTrue(!builder.advance(4), "a budget of 4 must not finish 9 blocks");
        helper.assertTrue(builder.placed().size() == 4, "budget must be respected");
        helper.assertTrue(builder.advance(100), "set should be done");
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.assertTrue(memory.getBlockState(origin.offset(x, 0, z)).is(Blocks.STONE),
                        "missing stone at " + x + "," + z);
            }
        }
        helper.assertTrue(builder.placed().size() == 9, "placed() should list 9 blocks");
        LOGGER.info("PENSIEVE-GAMETEST PASS {}", new Object() { }.getClass().getEnclosingMethod().getName());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void regionIsClearedOnRelease(GameTestHelper helper) {
        ServerLevel memory = memoryOrTestLevel(helper);
        int[] o = RegionAllocator.originFor(8);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        SetBuilder builder = new SetBuilder(memory, origin, stoneFill(), 20);
        builder.advance(1000);
        SetBuilder.ClearJob job = SetBuilder.clear(memory, builder.placed());
        helper.assertTrue(!job.advance(3), "clearing 9 with budget 3 is not done");
        helper.assertTrue(job.advance(100), "clear should finish");
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.assertTrue(memory.getBlockState(origin.offset(x, 0, z)).isAir(),
                        "not cleared at " + x + "," + z);
            }
        }
        LOGGER.info("PENSIEVE-GAMETEST PASS {}", new Object() { }.getClass().getEnclosingMethod().getName());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void buildIsClippedToTheRadius(GameTestHelper helper) {
        ServerLevel level = memoryOrTestLevel(helper);
        int[] o = RegionAllocator.originFor(9);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        SetBuilder builder = new SetBuilder(level, origin, stoneFill(), 1);
        helper.assertTrue(builder.total() == 4, "radius 1 should keep x,z 0..1 only, got " + builder.total());
        builder.advance(100);
        helper.assertTrue(level.getBlockState(origin.offset(2, 0, 0)).isAir(), "block beyond the radius was placed");
        LOGGER.info("PENSIEVE-GAMETEST PASS buildIsClippedToTheRadius");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void featuresAreRecordedAndCleared(GameTestHelper helper) {
        ServerLevel level = memoryOrTestLevel(helper);
        int[] o = RegionAllocator.originFor(10);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:dirt",
                new int[]{-1, -1, -1}, new int[]{1, -1, 1})), List.of(),
                List.of(new SceneDef.FeatureRef("minecraft:oak", new int[]{0, 0, 0})), 6000L, 20);
        SetBuilder builder = new SetBuilder(level, origin, set, 20);
        builder.advance(9);
        helper.assertTrue(!builder.advance(0), "a feature must not start with no budget left");
        helper.assertTrue(builder.advance(10), "feature should have been placed");
        helper.assertTrue(builder.placed().size() > 9, "feature blocks must be recorded, got " + builder.placed().size());
        helper.assertTrue(level.getBlockState(origin).is(net.minecraft.tags.BlockTags.LOGS), "no trunk placed");
        SetBuilder.ClearJob job = SetBuilder.clear(level, builder.placed());
        helper.assertTrue(job.advance(100000), "clear should finish");
        helper.assertTrue(level.getBlockState(origin).isAir(), "trunk not cleared");
        LOGGER.info("PENSIEVE-GAMETEST PASS featuresAreRecordedAndCleared");
        helper.succeed();
    }

    /**
     * Mini Hogwarts as a set schematic: turned 90 degrees, every cell lands where the rotation puts it (checked
     * against the formula here, not SetBuilder's own), air is skipped, nothing beyond the radius is placed, and
     * the clear takes every block away again.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void schematicBuildsRotatedAndClears(GameTestHelper helper) {
        ServerLevel level = memoryOrTestLevel(helper);
        com.hogwartsmagic.worldgen.MiniHogwartsSchematic schem = com.hogwartsmagic.worldgen.MiniHogwartsSchematic.get();
        helper.assertTrue(schem != null, "mini_hogwarts schematic did not load");
        int w = schem.width(), l = schem.length();
        int[] o = RegionAllocator.originFor(13);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        // Turned 90 degrees the footprint is l by w; this anchor centres it, and radius 60 crops the far x side.
        int[] at = {-l / 2, 0, -w / 2};
        int radius = 60;
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(),
                List.of(new SceneDef.SchematicRef("mini_hogwarts", at, 90)), List.of(), List.of(), List.of(), 18000L, 20);
        SetBuilder builder = new SetBuilder(level, origin, set, radius);

        int expected = 0;
        int sampleIndex = -1;
        for (int i = 0; i < schem.volume(); i++) {
            net.minecraft.world.level.block.state.BlockState st = schem.blockAt(i);
            if (st.isAir() || st.is(Blocks.STRUCTURE_VOID) || st.is(Blocks.JIGSAW) || st.is(Blocks.STRUCTURE_BLOCK)
                    || st.is(Blocks.COMMAND_BLOCK) || st.is(Blocks.CHAIN_COMMAND_BLOCK)
                    || st.is(Blocks.REPEATING_COMMAND_BLOCK)) {
                continue;
            }
            int x = i % w, z = (i / w) % l;
            int rx = at[0] + (l - 1 - z), rz = at[2] + x; // clockwise 90: (x, z) -> (-z, x), shifted to >= 0
            if (Math.abs(rx) > radius || Math.abs(rz) > radius) continue;
            expected++;
            if (sampleIndex < 0 && st.is(Blocks.STONE)) sampleIndex = i;
        }
        helper.assertTrue(builder.total() == expected, "expected " + expected + " cells, got " + builder.total());
        helper.assertTrue(!builder.advance(expected / 2 + 1), "half the budget must not finish the castle");
        while (!builder.advance(20000)) {
            // the castle is a few tens of thousands of blocks: a handful of steps
        }
        helper.assertTrue(builder.placed().size() == expected, "placed() should list every cell");
        for (BlockPos p : builder.placed()) {
            if (level.getBlockState(p).isAir()) {
                helper.fail("placed cell is air at " + p);
                return;
            }
            if (Math.abs(p.getX() - origin.getX()) > radius || Math.abs(p.getZ() - origin.getZ()) > radius) {
                helper.fail("cell beyond the radius at " + p);
                return;
            }
        }
        helper.assertTrue(sampleIndex >= 0, "no stone cell inside the radius");
        int sx = sampleIndex % w, sz = (sampleIndex / w) % l, sy = sampleIndex / (w * l);
        BlockPos sample = origin.offset(at[0] + (l - 1 - sz), at[1] + sy, at[2] + sx);
        helper.assertTrue(level.getBlockState(sample).is(Blocks.STONE), "rotated stone cell missing at " + sample);

        SetBuilder.ClearJob job = SetBuilder.clear(level, builder.placed());
        while (!job.advance(20000)) {
            // as many steps as the build
        }
        for (BlockPos p : builder.placed()) {
            if (!level.getBlockState(p).isAir()) {
                helper.fail("not cleared at " + p);
                return;
            }
        }
        LOGGER.info("PENSIEVE-GAMETEST PASS schematicBuildsRotatedAndClears ({} cells)", expected);
        helper.succeed();
    }

    private static net.minecraft.world.item.ItemStack chamberMemory() {
        return new net.minecraft.world.item.ItemStack(ExampleMod.CHAMBER_MEMORY.get());
    }

    @GameTest(template = EMPTY, batch = "pensieve_basin")
    public static void pourAndTakeOut(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ExampleMod.PENSIEVE.get());
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, chamberMemory());
        helper.useBlock(pos, player);
        var be = (com.hogwartsmagic.pensieve.PensieveBlockEntity) helper.getBlockEntity(pos);
        helper.assertTrue(be.memory().is(ExampleMod.CHAMBER_MEMORY.get()), "basin should hold the memory");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "one memory should have left the hand");
        helper.assertTrue(player.getUUID().equals(be.pourer()), "pourer should be recorded");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        helper.useBlock(pos, player);
        helper.assertTrue(be.memory().isEmpty(), "basin should be empty after take out");
        helper.assertTrue(player.getInventory().contains(chamberMemory()), "player should get the memory back");
        LOGGER.info("PENSIEVE-GAMETEST PASS pourAndTakeOut");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "pensieve_basin")
    public static void strangerCannotTakeOutPrivateMemory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ExampleMod.PENSIEVE.get());
        var be = (com.hogwartsmagic.pensieve.PensieveBlockEntity) helper.getBlockEntity(pos);
        be.pour(chamberMemory(), java.util.UUID.randomUUID());
        net.minecraft.world.entity.player.Player stranger = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        stranger.setShiftKeyDown(true);
        helper.useBlock(pos, stranger);
        helper.assertTrue(!be.memory().isEmpty(), "a stranger must not take a private memory");
        helper.assertTrue(!stranger.getInventory().contains(chamberMemory()), "stranger got the memory");
        LOGGER.info("PENSIEVE-GAMETEST PASS strangerCannotTakeOutPrivateMemory");
        helper.succeed();
    }

    // ---- Sessions (Task 6) -------------------------------------------------------------------------------
    // The GameTestServer has no memory dimension, so these use PensieveSessions' test seam: the test level stands
    // in for the memory level and every scene id resolves to a tiny scene (a 1-block fill, 20 build ticks).

    private static final String SESSIONS = "pensieve_sessions";
    private static final BlockPos BASIN = new BlockPos(1, 2, 1);

    private static void useSessionSeam(GameTestHelper helper, int length) {
        useSessionSeam(helper, length, null);
    }

    private static void useSessionSeam(GameTestHelper helper, int length, String endAction) {
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone",
                new int[]{0, 0, 0}, new int[]{0, 0, 0})), List.of(), List.of(), 6000L, 20);
        SceneDef scene = new SceneDef("gametest_session", "Test", length, 8, set, java.util.Map.of(), List.of(),
                List.of(), List.of(), null, endAction);
        com.hogwartsmagic.pensieve.PensieveSessions.overrideForTests(helper.getLevel(),
                id -> java.util.Optional.of(new com.hogwartsmagic.pensieve.MemoryRegistry.LoadedScene(scene, "{}")));
    }

    private static com.hogwartsmagic.pensieve.PensieveBlockEntity basinWithMemory(GameTestHelper helper) {
        helper.setBlock(BASIN, ExampleMod.PENSIEVE.get());
        var be = (com.hogwartsmagic.pensieve.PensieveBlockEntity) helper.getBlockEntity(BASIN);
        be.pour(chamberMemory(), null);
        return be;
    }

    /** A server player standing beside the basin, facing a known way. */
    private static net.minecraft.server.level.ServerPlayer playerBeside(GameTestHelper helper, int dz) {
        net.minecraft.server.level.ServerPlayer p = helper.makeMockServerPlayerInLevel();
        net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 2.5 + dz));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 30f, 10f);
        return p;
    }

    private static void logout(GameTestHelper helper, net.minecraft.server.level.ServerPlayer... players) {
        for (net.minecraft.server.level.ServerPlayer p : players) {
            helper.getLevel().getServer().getPlayerList().remove(p);
        }
    }

    private static boolean nearPoint(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.phys.Vec3 v, double d) {
        return p.position().distanceTo(v) <= d;
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 300)
    public static void diveMovesPlayerIntoMemoryAndBack(GameTestHelper helper) {
        useSessionSeam(helper, 10);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.useBlock(BASIN, p);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "diving should make a viewer");
        java.util.concurrent.atomic.AtomicBoolean entered = new java.util.concurrent.atomic.AtomicBoolean();
        helper.runAtTickTime(35, () -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            helper.assertTrue(s.isPresent(), "should still be in the session at tick 35");
            net.minecraft.world.phys.Vec3 anchor = net.minecraft.world.phys.Vec3.atBottomCenterOf(s.get().origin()).add(0, 1, 0);
            helper.assertTrue(p.level() == com.hogwartsmagic.pensieve.PensieveSessions.memoryLevel(p.getServer()),
                    "viewer should be in the memory level");
            helper.assertTrue(nearPoint(p, anchor, 0.5), "viewer should be at the anchor, is at " + p.position());
            helper.assertTrue(p.isInvisible() && p.isInvulnerable() && p.getAbilities().flying, "viewer state not set");
            entered.set(true);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(entered.get(), "not entered yet");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(nearPoint(p, ret, 0.1), "not back at the return point: " + p.position() + " vs " + ret);
            helper.assertTrue(!p.isInvisible() && !p.isInvulnerable(), "still invisible or invulnerable");
            helper.assertTrue(!com.hogwartsmagic.util.PlayerMagicData.getModData(p).contains("PensieveReturn"),
                    "return tag not cleared");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS diveMovesPlayerIntoMemoryAndBack");
        });
    }

    // The second viewer joins while the memory is already playing (PLAY, not BUILD): same session, same clock.
    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 300)
    public static void secondViewerJoinsSameSession(GameTestHelper helper) {
        useSessionSeam(helper, 100);
        basinWithMemory(helper);
        var p1 = playerBeside(helper, 0);
        var p2 = playerBeside(helper, 1);
        helper.useBlock(BASIN, p1);
        java.util.concurrent.atomic.AtomicBoolean joined = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicLong start = new java.util.concurrent.atomic.AtomicLong(-1);
        helper.runAtTickTime(60, () -> {
            var s1 = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p1);
            helper.assertTrue(s1.isPresent() && s1.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.PLAY,
                    "the first viewer's memory should be playing by tick 60");
            start.set(s1.get().startGameTime());
            helper.useBlock(BASIN, p2);
            var s2 = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p2);
            helper.assertTrue(s2.isPresent(), "both should be viewers");
            helper.assertTrue(s1.get() == s2.get(), "second viewer should join the same session");
            joined.set(true);
        });
        helper.runAtTickTime(95, () -> {
            var s1 = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p1);
            var s2 = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p2);
            helper.assertTrue(s1.isPresent() && s2.isPresent() && s1.get() == s2.get(), "both should still watch");
            helper.assertTrue(p2.level() == com.hogwartsmagic.pensieve.PensieveSessions.memoryLevel(p2.getServer())
                    && nearPoint(p2, s2.get().anchor(), 0.5), "the late joiner should be inside by tick 95");
            helper.assertTrue(s2.get().startGameTime() == start.get(),
                    "the late joiner must play on the same clock: " + s2.get().startGameTime() + " vs " + start.get());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(joined.get(), "not joined yet");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p1)
                    && !com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p2), "still viewing");
            logout(helper, p1, p2);
            LOGGER.info("PENSIEVE-GAMETEST PASS secondViewerJoinsSameSession");
        });
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 300)
    public static void breakingBasinEndsSession(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "should be inside at tick 40");
            helper.destroyBlock(BASIN);
        });
        helper.runAtTickTime(62, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer after the break");
            helper.assertTrue(nearPoint(p, ret, 0.1), "not back at the return point: " + p.position());
            helper.assertTrue(bodiesOf(helper, p).isEmpty(), "no body may remain after the basin is broken");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS breakingBasinEndsSession");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 100)
    public static void loginInsideMemoryReturnsPlayer(GameTestHelper helper) {
        useSessionSeam(helper, 10);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 2, 0.5));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Dim", helper.getLevel().dimension().location().toString());
        tag.putDouble("X", ret.x);
        tag.putDouble("Y", ret.y);
        tag.putDouble("Z", ret.z);
        tag.putFloat("YRot", 90f);
        tag.putFloat("XRot", 0f);
        net.minecraft.nbt.CompoundTag mod = com.hogwartsmagic.util.PlayerMagicData.getModData(p);
        mod.put("PensieveReturn", tag);
        com.hogwartsmagic.util.PlayerMagicData.saveModData(p, mod);
        com.hogwartsmagic.pensieve.PensieveSessions.onLogin(p);
        helper.assertTrue(nearPoint(p, ret, 0.1), "not moved to the return point: " + p.position());
        helper.assertTrue(!com.hogwartsmagic.util.PlayerMagicData.getModData(p).contains("PensieveReturn"),
                "return tag not cleared");
        logout(helper, p);
        LOGGER.info("PENSIEVE-GAMETEST PASS loginInsideMemoryReturnsPlayer");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void viewerCannotBreakBlocks(GameTestHelper helper) {
        useSessionSeam(helper, 10);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        helper.useBlock(BASIN, p);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "diving should make a viewer");
        BlockPos target = helper.absolutePos(new BlockPos(0, 1, 0));
        var event = new net.minecraftforge.event.level.BlockEvent.BreakEvent(helper.getLevel(), target,
                helper.getLevel().getBlockState(target), p);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(event);
        helper.assertTrue(event.isCanceled(), "a viewer's block break must be cancelled");
        com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        helper.succeedWhen(() -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS viewerCannotBreakBlocks");
        });
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void viewerCannotCastSpells(GameTestHelper helper) {
        useSessionSeam(helper, 10);
        basinWithMemory(helper);
        var viewer = playerBeside(helper, 0);
        var control = playerBeside(helper, 1);
        String spent = "hogwartsmagic:patronus_cast_spent_at";
        for (var p : List.of(viewer, control)) {
            net.minecraft.world.item.ItemStack wand = new net.minecraft.world.item.ItemStack(ExampleMod.HARRY_POTTER_WAND.get());
            com.hogwartsmagic.item.MagicWandItem.setSelectedSpell(wand, com.hogwartsmagic.spell.SpellType.LUMOS);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, wand);
            com.hogwartsmagic.item.MagicBookItem.learnSpellIfAbsent(p, com.hogwartsmagic.spell.SpellType.LUMOS);
            p.getPersistentData().putLong(spent, 1L);
        }
        helper.useBlock(BASIN, viewer);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(viewer), "diving should make a viewer");
        // A release goes all the way through the cast path and clears the spent marker; a viewer's must not.
        var release = new com.hogwartsmagic.network.SpellPackets.CastSpellPacket(false, true, false, false);
        com.hogwartsmagic.network.SpellPackets.CastSpellPacket.castAs(control, release);
        helper.assertTrue(!control.getPersistentData().contains(spent), "control: the cast path should have run");
        com.hogwartsmagic.network.SpellPackets.CastSpellPacket.castAs(viewer, release);
        helper.assertTrue(viewer.getPersistentData().contains(spent), "a viewer's cast must do nothing");
        var use = new net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem(viewer,
                net.minecraft.world.InteractionHand.MAIN_HAND);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(use);
        helper.assertTrue(use.isCanceled(), "a viewer's wand right-click must be cancelled");
        com.hogwartsmagic.pensieve.PensieveSessions.leave(viewer, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        helper.succeedWhen(() -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(viewer), "still a viewer");
            logout(helper, viewer, control);
            LOGGER.info("PENSIEVE-GAMETEST PASS viewerCannotCastSpells");
        });
    }

    // ---- The body (Task 7) ---------------------------------------------------------------------------------

    /** A survival player past the three seconds of spawn invulnerability a fresh ServerPlayer has. */
    private static void survivalAndSeasoned(net.minecraft.server.level.ServerPlayer p) {
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for (int i = 0; i < 70; i++) p.tick();
    }

    private static com.hogwartsmagic.pensieve.PensieveBodyEntity bodyOf(GameTestHelper helper,
            net.minecraft.server.level.ServerPlayer p) {
        var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
        helper.assertTrue(s.isPresent(), "not in a session");
        java.util.UUID id = s.get().viewers().get(p.getUUID()).bodyId();
        helper.assertTrue(id != null, "no body was spawned");
        net.minecraft.world.entity.Entity e = helper.getLevel().getEntity(id);
        helper.assertTrue(e instanceof com.hogwartsmagic.pensieve.PensieveBodyEntity, "body entity not found");
        return (com.hogwartsmagic.pensieve.PensieveBodyEntity) e;
    }

    /** Every live body in the level whose owner is {@code p}. */
    private static List<com.hogwartsmagic.pensieve.PensieveBodyEntity> bodiesOf(GameTestHelper helper,
            net.minecraft.server.level.ServerPlayer p) {
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(helper.absolutePos(BASIN)).inflate(32);
        return helper.getLevel().getEntitiesOfClass(com.hogwartsmagic.pensieve.PensieveBodyEntity.class, area,
                b -> b.isAlive() && p.getUUID().equals(b.getOwnerId()));
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void hittingBodyHurtsViewer(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        survivalAndSeasoned(p);
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(p.getHealth() == 20f, "viewer should start at 20");
            var body = bodyOf(helper, p);
            helper.assertTrue(body.hurt(helper.getLevel().damageSources().generic(), 4f), "the body should take the hit");
            helper.assertTrue(p.getHealth() == 16f, "viewer should be at 16, is " + p.getHealth());
            helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "a light hit must not end the memory");
            helper.assertTrue(p.isInvulnerable(), "the ghost invulnerability must come back after the hit");
            helper.assertTrue(body.isAlive(), "the body must survive a hit");
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS hittingBodyHurtsViewer");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void heavyHitsYankViewerOut(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        survivalAndSeasoned(p);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            var body = bodyOf(helper, p);
            body.hurt(helper.getLevel().damageSources().generic(), 14f);
            helper.assertTrue(p.getHealth() == 6f, "viewer should be at 6, is " + p.getHealth());
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            helper.assertTrue(s.isPresent() && s.get().viewers().get(p.getUUID()).exitReason()
                    == com.hogwartsmagic.pensieve.ExitReason.YANK, "the viewer should be on a YANK exit");
        });
        helper.runAtTickTime(55, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer after the yank");
            helper.assertTrue(nearPoint(p, ret, 0.1), "not back at the return point: " + p.position());
            helper.assertTrue(p.getHealth() == 6f, "health must stay as it was");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS heavyHitsYankViewerOut");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void bodyIsRemovedWhenViewerLeaves(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.useBlock(BASIN, p);
        java.util.concurrent.atomic.AtomicReference<com.hogwartsmagic.pensieve.PensieveBodyEntity> held =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<com.hogwartsmagic.pensieve.Session> session =
                new java.util.concurrent.atomic.AtomicReference<>();
        helper.runAtTickTime(40, () -> {
            var body = bodyOf(helper, p);
            helper.assertTrue(body.position().distanceTo(ret) <= 0.01, "the body should stand at the return point");
            helper.assertTrue(p.getUUID().equals(body.getOwnerId()), "the body should know its owner");
            held.set(body);
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p).orElseThrow();
            helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.holdsBasinTicket(s),
                    "the session should hold the basin chunks entity-ticking");
            session.set(s);
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(held.get().isRemoved(), "the body should be gone");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.holdsBasinTicket(session.get()),
                    "the basin ticket must be released when the session ends");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS bodyIsRemovedWhenViewerLeaves");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void bodyOfNobodyIsDiscarded(GameTestHelper helper) {
        var body = com.hogwartsmagic.entity.ModEntities.PENSIEVE_BODY.get().create(helper.getLevel());
        body.setOwner(java.util.UUID.randomUUID(), "Nobody");
        net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 1.5));
        body.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(body);
        helper.assertTrue(!body.shouldBeSaved(), "a body must never be saved");
        body.hurt(helper.getLevel().damageSources().generic(), 4f);
        helper.assertTrue(body.isRemoved(), "a body whose owner is not a viewer must be discarded when hit");
        LOGGER.info("PENSIEVE-GAMETEST PASS bodyOfNobodyIsDiscarded");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void dyingInsideMemoryLeavesViewerAndLootAtTheBody(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        survivalAndSeasoned(p);
        net.minecraft.world.phys.Vec3 ret = p.position();
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            var body = bodyOf(helper, p);
            body.hurt(helper.getLevel().damageSources().generic(), 1000f);
            helper.assertTrue(p.isDeadOrDying(), "the forwarded hit should have killed the viewer");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "a dead viewer must leave the session");
            helper.assertTrue(!com.hogwartsmagic.util.PlayerMagicData.getModData(p).contains("PensieveReturn"),
                    "return tag must be cleared on death");
            helper.assertTrue(p.position().distanceTo(ret) <= 0.5, "the dying viewer should be at the body, is " + p.position());
            boolean dropped = !helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(ret, ret).inflate(3)).isEmpty();
            helper.assertTrue(dropped, "the inventory should have dropped at the body");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS dyingInsideMemoryLeavesViewerAndLootAtTheBody");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void killingTheBodyDoesNotKillTheViewer(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        survivalAndSeasoned(p);
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            var body = bodyOf(helper, p);
            body.hurt(helper.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(body.isRemoved(), "a killed body should go");
            helper.assertTrue(p.isAlive() && p.getHealth() == 20f, "the viewer must be untouched");
            helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
        });
        helper.runAtTickTime(75, () -> {
            helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer later");
            helper.assertTrue(bodyOf(helper, p).isAlive(), "the body should have been stood back up");
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        });
        helper.runAtTickTime(120, () -> {
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS killingTheBodyDoesNotKillTheViewer");
            helper.succeed();
        });
    }

    // ---- End action (Task 14) -------------------------------------------------------------------------------
    // The flat Test Level has no Locket caves, so the Cave Map's target comes from CaveMemoryItem's test seam.

    /** Cave Maps in the player's inventory (filled maps named as the Cave Map). */
    private static int caveMaps(net.minecraft.server.level.ServerPlayer p) {
        int n = 0;
        for (net.minecraft.world.item.ItemStack st : p.getInventory().items) {
            net.minecraft.network.chat.Component name = st.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
            if (st.is(net.minecraft.world.item.Items.FILLED_MAP) && name != null
                    && name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("item.hogwartsmagic.cave_map")) {
                n += st.getCount();
            }
        }
        return n;
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 300)
    public static void caveMapEndActionGivesTheMapAtEnd(GameTestHelper helper) {
        useSessionSeam(helper, 10, "cave_map");
        BlockPos cave = helper.absolutePos(BASIN).offset(512, 0, 512);
        com.hogwartsmagic.item.CaveMemoryItem.overrideCaveFinderForTests(pl -> java.util.Optional.of(cave));
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.useBlock(BASIN, p);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "diving should make a viewer");
        java.util.concurrent.atomic.AtomicBoolean sawEnd = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicLong homeAt = new java.util.concurrent.atomic.AtomicLong(-1);
        helper.onEachTick(() -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            if (s.isPresent()) {
                helper.assertTrue(caveMaps(p) == 0, "no Cave Map while still inside the memory");
                var v = s.get().viewers().get(p.getUUID());
                if (v != null && v.exitReason() == com.hogwartsmagic.pensieve.ExitReason.END) sawEnd.set(true);
            } else if (homeAt.get() < 0) {
                homeAt.set(helper.getTick());
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sawEnd.get(), "the memory should end with END");
            helper.assertTrue(homeAt.get() >= 0 && helper.getTick() >= homeAt.get() + 20, "not home long enough yet");
            helper.assertTrue(nearPoint(p, ret, 0.1), "the viewer should be home: " + p.position());
            int maps = caveMaps(p);
            helper.assertTrue(maps == 1, "exactly one Cave Map expected at END, found " + maps);
            com.hogwartsmagic.item.CaveMemoryItem.overrideCaveFinderForTests(null);
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS caveMapEndActionGivesTheMapAtEnd");
        });
    }

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void leavingEarlyGivesNoCaveMap(GameTestHelper helper) {
        useSessionSeam(helper, 400, "cave_map");
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(60, () -> com.hogwartsmagic.pensieve.PensieveSessions.leave(p,
                com.hogwartsmagic.pensieve.ExitReason.LEFT));
        helper.runAtTickTime(130, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "should have left");
            helper.assertTrue(caveMaps(p) == 0, "leaving before the end must not give the Cave Map");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS leavingEarlyGivesNoCaveMap");
            helper.succeed();
        });
    }

    // ---- Final review fixes ----------------------------------------------------------------------------------

    /** The session seam with a given scene (instead of the 1-block stone default). */
    private static void useSessionSeam(GameTestHelper helper, SceneDef scene) {
        com.hogwartsmagic.pensieve.PensieveSessions.overrideForTests(helper.getLevel(),
                id -> java.util.Optional.of(new com.hogwartsmagic.pensieve.MemoryRegistry.LoadedScene(scene, "{}")));
    }

    /**
     * Gravel over air and a water block event with open edges: set flags 2|16 do not stop onPlace from
     * scheduling a fall or a flow, so the builder and the event path must clear those ticks. A control gravel
     * block placed the ordinary way (flags 3) in the same area proves the area really ticks.
     */
    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 300)
    public static void memorySetGravelAndWaterStayPut(GameTestHelper helper) {
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:gravel",
                new int[]{3, 3, 3}, new int[]{5, 3, 5})), List.of(), List.of(), 6000L, 20);
        SceneDef.EventDef water = new SceneDef.EventDef(0f, "block", "minecraft:water", new int[]{8, 3, 0}, 1f, 1f,
                0, null, 0, Float.NaN);
        useSessionSeam(helper, new SceneDef("gametest_physics", "Test", 400, 12, set, java.util.Map.of(),
                List.of(), List.of(water), List.of(), null, null));
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        helper.useBlock(BASIN, p);
        // The memory slot is far from every test structure; force its chunks so block and fluid ticks run there.
        BlockPos slotOrigin = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p).orElseThrow().origin();
        List<net.minecraft.world.level.ChunkPos> forced = new java.util.ArrayList<>();
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                net.minecraft.world.level.ChunkPos c = new net.minecraft.world.level.ChunkPos(
                        (slotOrigin.getX() >> 4) + cx, (slotOrigin.getZ() >> 4) + cz);
                helper.getLevel().setChunkForced(c.x, c.z, true);
                forced.add(c);
            }
        }
        java.util.concurrent.atomic.AtomicReference<BlockPos> origin = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong waterAt = new java.util.concurrent.atomic.AtomicLong(-1);
        helper.onEachTick(() -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            if (s.isEmpty()) return;
            if (origin.get() == null) origin.set(s.get().origin());
            if (waterAt.get() < 0 && helper.getLevel().getBlockState(s.get().origin().offset(8, 3, 0)).is(Blocks.WATER)) {
                waterAt.set(helper.getTick());
                // The control: placed with neighbour updates, it must fall in this same area.
                helper.getLevel().setBlock(s.get().origin().offset(-6, 6, -6), Blocks.GRAVEL.defaultBlockState(), 3);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(waterAt.get() >= 0 && helper.getTick() >= waterAt.get() + 40,
                    "waiting for 40 ticks after the water event");
            BlockPos o = origin.get();
            ServerLevel level = helper.getLevel();
            helper.assertTrue(!level.getBlockState(o.offset(-6, 6, -6)).is(Blocks.GRAVEL),
                    "control: ordinary gravel over air should have fallen, so this area must tick");
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(o.offset(-2, -10, -2).getCenter(),
                    o.offset(11, 8, 8).getCenter());
            List<net.minecraft.world.entity.item.FallingBlockEntity> falling = level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.FallingBlockEntity.class, area);
            helper.assertTrue(falling.isEmpty(), "the set's gravel must not fall, found " + falling.size());
            int gravel = 0;
            for (int x = 3; x <= 5; x++) {
                for (int z = 3; z <= 5; z++) {
                    if (level.getBlockState(o.offset(x, 3, z)).is(Blocks.GRAVEL)) gravel++;
                }
            }
            helper.assertTrue(gravel == 9, "all 9 gravel blocks must stay, found " + gravel);
            helper.assertTrue(level.getBlockState(o.offset(8, 3, 0)).is(Blocks.WATER), "the event water must stay");
            int wet = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -3; dy <= 0; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (!level.getFluidState(o.offset(8 + dx, 3 + dy, dz)).isEmpty()) wet++;
                    }
                }
            }
            helper.assertTrue(wet == 1, "the water must not flow, wet cells " + wet);
            for (var f : level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                    new net.minecraft.world.phys.AABB(o).inflate(16))) {
                f.discard();
            }
            for (net.minecraft.world.level.ChunkPos c : forced) helper.getLevel().setChunkForced(c.x, c.z, false);
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
            LOGGER.info("PENSIEVE-GAMETEST PASS memorySetGravelAndWaterStayPut");
        });
    }

    /** A player riding a boat dives: they come off it first and the boat stays behind, empty. */
    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void mountedDiverIsDismounted(GameTestHelper helper) {
        useSessionSeam(helper, 10);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.entity.vehicle.Boat boat = net.minecraft.world.entity.EntityType.BOAT.create(helper.getLevel());
        helper.assertTrue(boat != null, "no boat");
        boat.moveTo(p.getX(), p.getY(), p.getZ());
        helper.getLevel().addFreshEntity(boat);
        helper.assertTrue(p.startRiding(boat, true) && p.isPassenger(), "the player should be riding the boat");
        helper.useBlock(BASIN, p);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "diving should make a viewer");
        helper.assertTrue(!p.isPassenger() && boat.getPassengers().isEmpty(), "the diver must be off the boat");
        helper.succeedWhen(() -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(!p.isPassenger(), "back home, still not riding");
            boat.discard();
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS mountedDiverIsDismounted");
        });
    }

    /** A zombie by the basin goes for the helpless body within a couple of seconds. */
    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 300)
    public static void zombieHuntsTheBody(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        helper.useBlock(BASIN, p);
        java.util.concurrent.atomic.AtomicReference<net.minecraft.world.entity.monster.Zombie> zombie =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<com.hogwartsmagic.pensieve.PensieveBodyEntity> body =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicLong targetedAt = new java.util.concurrent.atomic.AtomicLong(-1);
        helper.runAtTickTime(40, () -> {
            body.set(bodyOf(helper, p));
            net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
            helper.assertTrue(z != null, "no zombie");
            // In the far corner of the test structure: its barrier walls would hide a zombie outside them.
            net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(0.5, 2, 0.5));
            z.moveTo(at.x, at.y, at.z, 0f, 0f);
            z.setPersistenceRequired();
            helper.getLevel().addFreshEntity(z);
            zombie.set(z);
        });
        helper.onEachTick(() -> {
            if (zombie.get() != null && targetedAt.get() < 0 && zombie.get().getTarget() == body.get()) {
                targetedAt.set(helper.getTick());
            }
        });
        helper.runAtTickTime(80, () -> {
            var z = zombie.get();
            helper.assertTrue(targetedAt.get() >= 0, "the zombie should target the body within 40 ticks (alive "
                    + z.isAlive() + ", removed " + z.getRemovalReason() + ", target " + z.getTarget() + ", sight "
                    + z.hasLineOfSight(body.get()) + ", canAttack " + z.canAttack(body.get()) + ", difficulty "
                    + helper.getLevel().getDifficulty() + ", body alive " + body.get().isAlive() + ", body ticks "
                    + body.get().tickCount + ", distance " + z.distanceTo(body.get()) + ", zombie at " + z.position()
                    + ", body at " + body.get().position() + ")");
            zombie.get().discard();
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        });
        helper.runAtTickTime(130, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS zombieHuntsTheBody");
            helper.succeed();
        });
    }

    /** /kill on a viewer (GENERIC_KILL) goes through; the death path takes them home first. */
    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void killingAViewerSendsThemHome(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "should be inside at tick 40");
            p.kill();
            helper.assertTrue(p.isDeadOrDying(), "/kill must reach the viewer");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "a killed viewer must leave");
            helper.assertTrue(p.position().distanceTo(ret) <= 0.5, "the killed viewer should be home, is " + p.position());
            helper.assertTrue(!com.hogwartsmagic.util.PlayerMagicData.getModData(p).contains("PensieveReturn"),
                    "return tag must be cleared");
            helper.assertTrue(bodiesOf(helper, p).isEmpty(), "the body must be gone");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS killingAViewerSendsThemHome");
            helper.succeed();
        });
    }

    /** {@code /pensieve leave <player>} from an op pulls a viewer out. */
    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void opCanPullAViewerOut(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            var server = helper.getLevel().getServer();
            // Every mock player has the same name and a player argument refuses a UUID, so @p from the viewer's
            // own position (the memory anchor, where no one else floats).
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput()
                    .withLevel(p.serverLevel()).withPosition(p.position()), "pensieve leave @p");
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            helper.assertTrue(s.isPresent() && s.get().viewers().get(p.getUUID()).exitReason()
                    == com.hogwartsmagic.pensieve.ExitReason.LEFT, "the op command should start a LEFT exit");
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(nearPoint(p, ret, 0.1), "not back at the return point: " + p.position());
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS opCanPullAViewerOut");
            helper.succeed();
        });
    }

    /** Switched from survival to creative while inside: back home they fly as creative, not as the snapshot. */
    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void gameModeChangeInsideIsKept(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        helper.assertTrue(!p.getAbilities().mayfly, "a survival player should not be able to fly");
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(p.getAbilities().mayfly && p.getAbilities().instabuild,
                    "a player made creative inside must keep creative abilities");
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS gameModeChangeInsideIsKept");
            helper.succeed();
        });
    }

    /**
     * Logging out during PLAY: the real PlayerList.remove path (it fires PlayerLoggedOutEvent, which calls
     * PensieveSessions.onLogout) puts the player at the return point with the tag cleared before the save.
     */
    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 200)
    public static void logoutMidMemoryReturnsPlayer(GameTestHelper helper) {
        useSessionSeam(helper, 400);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(60, () -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            helper.assertTrue(s.isPresent() && s.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.PLAY,
                    "the memory should be playing at tick 60");
            helper.assertTrue(com.hogwartsmagic.util.PlayerMagicData.getModData(p).contains("PensieveReturn"),
                    "a viewer carries a return tag");
            logout(helper, p);
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer after logout");
            helper.assertTrue(nearPoint(p, ret, 0.1), "not back at the return point: " + p.position());
            helper.assertTrue(!com.hogwartsmagic.util.PlayerMagicData.getModData(p).contains("PensieveReturn"),
                    "return tag not cleared");
            helper.assertTrue(!p.isInvisible() && !p.isInvulnerable(), "ghost state not undone");
            helper.assertTrue(bodiesOf(helper, p).isEmpty(), "the body must be gone");
            LOGGER.info("PENSIEVE-GAMETEST PASS logoutMidMemoryReturnsPlayer");
            helper.succeed();
        });
    }

    /**
     * Only a viewer inside as PLAY began gets the end action; a late joiner saw part of it and gets nothing. Its
     * own batch: the Cave Map finder seam is static, and caveMapEndActionGivesTheMapAtEnd resets it when it ends.
     */
    @GameTest(template = EMPTY, batch = "pensieve_late_joiner", timeoutTicks = 300)
    public static void lateJoinerGetsNoCaveMap(GameTestHelper helper) {
        useSessionSeam(helper, 100, "cave_map");
        BlockPos cave = helper.absolutePos(BASIN).offset(512, 0, 512);
        com.hogwartsmagic.item.CaveMemoryItem.overrideCaveFinderForTests(pl -> java.util.Optional.of(cave));
        basinWithMemory(helper);
        var early = playerBeside(helper, 0);
        var late = playerBeside(helper, 1);
        helper.useBlock(BASIN, early);
        helper.runAtTickTime(60, () -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(early);
            helper.assertTrue(s.isPresent() && s.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.PLAY,
                    "should be playing at tick 60");
            helper.useBlock(BASIN, late);
            helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(late), "late joiner should join");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 100, "not yet");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(early)
                    && !com.hogwartsmagic.pensieve.PensieveSessions.isViewer(late), "still viewing");
            helper.assertTrue(caveMaps(early) == 1, "the viewer from the start should get the map");
            helper.assertTrue(caveMaps(late) == 0, "the late joiner must not get the map");
            com.hogwartsmagic.item.CaveMemoryItem.overrideCaveFinderForTests(null);
            logout(helper, early, late);
            LOGGER.info("PENSIEVE-GAMETEST PASS lateJoinerGetsNoCaveMap");
        });
    }

    private static int patronusMemories(net.minecraft.server.level.ServerPlayer p) {
        int n = 0;
        for (net.minecraft.world.item.ItemStack s : p.getInventory().items) {
            if (s.is(ExampleMod.PATRONUS_MEMORY.get())) n += s.getCount();
        }
        return n;
    }

    /** Learning Expecto Patronum for the first time hands over exactly one Memory of the Lake; again, none. */
    @GameTest(template = EMPTY)
    public static void learningPatronusGivesOneMemory(GameTestHelper helper) {
        var p = playerBeside(helper, 0);
        var other = playerBeside(helper, 1);
        var SPELL = com.hogwartsmagic.spell.SpellType.EXPECTO_PATRONUM;
        helper.assertTrue(patronusMemories(p) == 0, "nothing to start with");
        helper.assertTrue(com.hogwartsmagic.item.MagicBookItem.learnSpellIfAbsent(other, com.hogwartsmagic.spell.SpellType.LUMOS),
                "control learns lumos");
        helper.assertTrue(patronusMemories(other) == 0, "learning another spell gives no memory");
        helper.assertTrue(com.hogwartsmagic.item.MagicBookItem.learnSpellIfAbsent(p, SPELL), "first learn");
        helper.assertTrue(patronusMemories(p) == 1, "first learn should give one memory, got " + patronusMemories(p));
        helper.assertTrue(!com.hogwartsmagic.item.MagicBookItem.learnSpellIfAbsent(p, SPELL), "second learn is a no-op");
        helper.assertTrue(patronusMemories(p) == 1, "learning again gives none, got " + patronusMemories(p));
        logout(helper, p, other);
        LOGGER.info("PENSIEVE-GAMETEST PASS learningPatronusGivesOneMemory");
        helper.succeed();
    }

    /** One block budget per server tick: a second job sees what the first left. */
    @GameTest(template = EMPTY)
    public static void buildAndClearShareOneBudget(GameTestHelper helper) {
        ServerLevel level = memoryOrTestLevel(helper);
        int[] o1 = RegionAllocator.originFor(11);
        int[] o2 = RegionAllocator.originFor(12);
        SetBuilder a = new SetBuilder(level, new BlockPos(o1[0], o1[1], o1[2]), stoneFill(), 20);
        SetBuilder b = new SetBuilder(level, new BlockPos(o2[0], o2[1], o2[2]), stoneFill(), 20);
        int[] budget = {12};
        helper.assertTrue(a.advance(budget), "the first build (9 blocks) fits in 12");
        helper.assertTrue(budget[0] == 3, "9 of 12 should be spent, left " + budget[0]);
        helper.assertTrue(!b.advance(budget), "the second build has only 3 left");
        helper.assertTrue(budget[0] == 0 && b.placed().size() == 3, "3 placed by the second, got " + b.placed().size());
        SetBuilder.ClearJob clear = SetBuilder.clear(level, a.placed());
        helper.assertTrue(!clear.advance(budget), "a clear with no budget left does nothing");
        int[] next = {100};
        helper.assertTrue(clear.advance(next) && b.advance(next), "the next tick finishes both");
        SetBuilder.clear(level, b.placed()).advance(100);
        LOGGER.info("PENSIEVE-GAMETEST PASS buildAndClearShareOneBudget");
        helper.succeed();
    }

    // ---- Chained scenes (Sorting stage 1, Task 2) -------------------------------------------------------------
    // chain_a (stone floor) names chain_b (gold floor) as its next scene; "unchained" names none; any other id
    // (the basin's memory) resolves to chain_a. Each test has its own batch: the seam, the build budget hook and
    // the slot allocator are all static, and the next scene is looked up later than the dive.

    private static SceneDef floorScene(String id, String block, int length, String next) {
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill(block, new int[]{0, 0, 0},
                new int[]{2, 0, 2})), List.of(), List.of(), 6000L, 20);
        return new SceneDef(id, "Test", length, 8, set, java.util.Map.of(), List.of(), List.of(), List.of(),
                com.hogwartsmagic.pensieve.scene.CameraTrack.EMPTY, null, 0, next);
    }

    private static void useChainSeam(GameTestHelper helper) {
        SceneDef a = floorScene("chain_a", "minecraft:stone", 40, "chain_b");
        SceneDef b = floorScene("chain_b", "minecraft:gold_block", 40, null);
        SceneDef u = floorScene("unchained", "minecraft:stone", 40, null);
        // chain_a, but long enough that a viewer who leaves mid-PLAY is home before its clock could hand over.
        SceneDef l = floorScene("chain_long", "minecraft:stone", 400, "chain_b");
        com.hogwartsmagic.pensieve.PensieveSessions.setBuildBudgetForTests(-1);
        com.hogwartsmagic.pensieve.PensieveSessions.overrideForTests(helper.getLevel(), id -> java.util.Optional.of(
                new com.hogwartsmagic.pensieve.MemoryRegistry.LoadedScene(
                        "chain_b".equals(id) ? b : "unchained".equals(id) ? u : "chain_long".equals(id) ? l : a,
                        "{}")));
    }

    private static int goldFloor(GameTestHelper helper, BlockPos origin) {
        int n = 0;
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (helper.getLevel().getBlockState(origin.offset(x, 0, z)).is(Blocks.GOLD_BLOCK)) n++;
            }
        }
        return n;
    }

    @GameTest(template = EMPTY, batch = "pensieve_chain_play", timeoutTicks = 300)
    public static void chainHandsViewerToNextScene(GameTestHelper helper) {
        useChainSeam(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startPreview(p, "chain_a"), "preview refused");
        java.util.concurrent.atomic.AtomicBoolean handed = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            if (handed.get() || s.isEmpty() || !"chain_b".equals(s.get().sceneId())) return;
            helper.assertTrue("chain_a".equals(s.get().chainRoot()), "chainRoot should be chain_a, is " + s.get().chainRoot());
            helper.assertTrue(s.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.PLAY, "chain_b should play");
            helper.assertTrue(!s.get().standby(), "the session handed to must not be on standby");
            helper.assertTrue(nearPoint(p, s.get().anchor(), 0.5), "viewer should float at chain_b's anchor");
            helper.assertTrue(goldFloor(helper, s.get().origin()) == 9, "chain_b's gold floor should be built");
            helper.assertTrue(s.get().viewers().get(p.getUUID()).sawStart(), "sawStart should carry over");
            handed.set(true);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(handed.get(), "not handed to chain_b yet");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(nearPoint(p, ret, 0.1), "not back at the return point: " + p.position());
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS chainHandsViewerToNextScene");
        });
    }

    @GameTest(template = EMPTY, batch = "pensieve_chain_stall", timeoutTicks = 300)
    public static void handoverWaitsForBuild(GameTestHelper helper) {
        useChainSeam(helper);
        basinWithMemory(helper);
        var p = playerBeside(helper, 0);
        helper.useBlock(BASIN, p);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "diving should make a viewer");
        // chain_a's 9 blocks went in on the first tick; from here nothing builds, so the standby chain_b stalls.
        helper.runAtTickTime(5, () -> com.hogwartsmagic.pensieve.PensieveSessions.setBuildBudgetForTests(0));
        java.util.concurrent.atomic.AtomicLong handoverAt = new java.util.concurrent.atomic.AtomicLong(-1);
        java.util.concurrent.atomic.AtomicReference<com.hogwartsmagic.pensieve.Session> old =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            if (released.get()) return;
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            if (s.isEmpty()) return;
            if (handoverAt.get() < 0 && s.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.HANDOVER) {
                handoverAt.set(helper.getTick());
                old.set(s.get());
                helper.assertTrue(s.get().nextSession() != null && s.get().nextSession().standby(),
                        "a standby chain_b should be waiting");
            }
            if (handoverAt.get() >= 0 && helper.getTick() >= handoverAt.get() + 20) {
                helper.assertTrue(s.get() == old.get(), "the viewer must stay in chain_a while chain_b is unbuilt");
                helper.assertTrue(s.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.HANDOVER,
                        "chain_a should hold in HANDOVER, is " + s.get().phase());
                helper.assertTrue(nearPoint(p, s.get().anchor(), 0.5), "viewer should stay pinned at chain_a's anchor");
                com.hogwartsmagic.pensieve.PensieveSessions.setBuildBudgetForTests(-1);
                released.set(true);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(released.get(), "not unstalled yet");
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            helper.assertTrue(s.isPresent() && "chain_b".equals(s.get().sceneId())
                    && s.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.PLAY, "chain_b should play");
            helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.holdsBasinTicket(s.get()),
                    "the basin ticket should move to chain_b");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.holdsBasinTicket(old.get()),
                    "chain_a must not keep the basin ticket");
            helper.assertTrue(s.get().chainRoot().equals(old.get().sceneId()), "chainRoot should be the dived scene");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS handoverWaitsForBuild");
        });
    }

    @GameTest(template = EMPTY, batch = "pensieve_chain_leave", timeoutTicks = 300)
    public static void leaveMidChainClearsStandby(GameTestHelper helper) {
        useChainSeam(helper);
        var p = playerBeside(helper, 0);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startPreview(p, "chain_long"), "preview refused");
        java.util.concurrent.atomic.AtomicReference<com.hogwartsmagic.pensieve.Session> a =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<com.hogwartsmagic.pensieve.Session> b =
                new java.util.concurrent.atomic.AtomicReference<>();
        // The 400-tick clock never runs out here, so the standby can only go by being finished with its owner.
        helper.onEachTick(() -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            if (s.isEmpty()) return;
            helper.assertTrue(s.get().phase() != com.hogwartsmagic.pensieve.Session.Phase.HANDOVER,
                    "chain_long must never reach HANDOVER in this test");
            helper.assertTrue(!"chain_b".equals(s.get().sceneId()), "the viewer must never be handed to chain_b");
        });
        helper.runAtTickTime(70, () -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            helper.assertTrue(s.isPresent() && s.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.PLAY,
                    "chain_long should be playing at tick 70");
            var next = s.get().nextSession();
            helper.assertTrue(next != null && next.standby(), "a standby chain_b should exist during chain_a's PLAY");
            helper.assertTrue(goldFloor(helper, next.origin()) == 9, "the standby should be built ahead");
            helper.assertTrue(next.viewers().isEmpty(), "a standby has no viewers");
            a.set(s.get());
            b.set(next);
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(b.get() != null, "not left yet");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.slotInUse(a.get().slot()),
                    "chain_a's slot should be released");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.slotInUse(b.get().slot()),
                    "the standby's slot should be released");
            helper.assertTrue(goldFloor(helper, b.get().origin()) == 0, "the standby's gold floor should be cleared");
            helper.assertTrue(b.get().standby() && b.get().viewers().isEmpty(),
                    "chain_b must have been cleared while still a standby, never handed over to");
            helper.assertTrue(a.get().phase() == com.hogwartsmagic.pensieve.Session.Phase.CLOSING
                    && a.get().nextSession() == null, "chain_long should be finished with its standby unlinked");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS leaveMidChainClearsStandby");
        });
    }

    @GameTest(template = EMPTY, batch = "pensieve_chain_unchained", timeoutTicks = 300)
    public static void unchainedSceneUnchanged(GameTestHelper helper) {
        useChainSeam(helper);
        var p = playerBeside(helper, 0);
        net.minecraft.world.phys.Vec3 ret = p.position();
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startPreview(p, "unchained"), "preview refused");
        java.util.concurrent.atomic.AtomicBoolean sawEnd = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            if (s.isEmpty()) return;
            helper.assertTrue("unchained".equals(s.get().sceneId()), "the viewer must stay in the unchained scene");
            helper.assertTrue(s.get().nextSession() == null, "a scene with no next builds no standby");
            helper.assertTrue("unchained".equals(s.get().chainRoot()), "a fresh session is its own chain root");
            var v = s.get().viewers().get(p.getUUID());
            if (v != null && v.exitReason() == com.hogwartsmagic.pensieve.ExitReason.END) sawEnd.set(true);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sawEnd.get(), "the memory should end with END");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(nearPoint(p, ret, 0.1), "not back at the return point: " + p.position());
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS unchainedSceneUnchanged");
        });
    }

    // ---- Solo memories and the home listener (Sorting stage 1, Task 4) --------------------------------------

    private record Home(String root, com.hogwartsmagic.pensieve.ExitReason reason, boolean sawStart) {}

    @GameTest(template = EMPTY, batch = "pensieve_solo_body", timeoutTicks = 300)
    public static void soloStartLeavesBodyAndNoBasin(GameTestHelper helper) {
        useChainSeam(helper);
        var p = playerBeside(helper, 0);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startSolo(p, "chain_long"), "solo refused");
        var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
        helper.assertTrue(s.isPresent(), "no session");
        helper.assertTrue(s.get().basinPos() == null, "a solo memory has no basin");
        helper.assertTrue(!s.get().preview(), "a solo memory is not a preview");
        helper.succeedWhen(() -> {
            var v = s.get().viewers().get(p.getUUID());
            helper.assertTrue(v != null && v.bodyId() != null, "no body was left behind");
            helper.assertTrue(bodyOf(helper, p) != null, "body entity not found");
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS soloStartLeavesBodyAndNoBasin");
        });
    }

    @GameTest(template = EMPTY, batch = "pensieve_solo_twice", timeoutTicks = 100)
    public static void soloRefusesSecondStart(GameTestHelper helper) {
        useChainSeam(helper);
        var p = playerBeside(helper, 0);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startSolo(p, "chain_long"), "solo refused");
        helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.startSolo(p, "chain_long"),
                "a second solo start while inside must be refused");
        com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        logout(helper, p);
        LOGGER.info("PENSIEVE-GAMETEST PASS soloRefusesSecondStart");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "pensieve_home_left", timeoutTicks = 300)
    public static void homeListenerGetsChainRootAndReason(GameTestHelper helper) {
        useChainSeam(helper);
        var p = playerBeside(helper, 0);
        List<Home> homes = new java.util.ArrayList<>();
        com.hogwartsmagic.pensieve.ViewerHomeListener l = (pl, root, reason, saw) -> {
            if (pl == p) homes.add(new Home(root, reason, saw));
        };
        com.hogwartsmagic.pensieve.PensieveSessions.addHomeListener(l);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startSolo(p, "chain_a"), "solo refused");
        // chain_a plays for 40 ticks: leave on the first one the viewer is inside for.
        java.util.concurrent.atomic.AtomicBoolean left = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            if (left.get() || s.isEmpty() || s.get().phase() != com.hogwartsmagic.pensieve.Session.Phase.PLAY) return;
            var v = s.get().viewers().get(p.getUUID());
            if (v == null || !v.sawStart()) return;
            left.set(true);
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            com.hogwartsmagic.pensieve.PensieveSessions.removeHomeListener(l);
            logout(helper, p);
            helper.assertTrue(homes.size() == 1, "listener should fire once, fired " + homes.size());
            helper.assertTrue(homes.get(0).equals(new Home("chain_a", com.hogwartsmagic.pensieve.ExitReason.LEFT, true)),
                    "unexpected " + homes.get(0));
            LOGGER.info("PENSIEVE-GAMETEST PASS homeListenerGetsChainRootAndReason");
        });
    }

    @GameTest(template = EMPTY, batch = "pensieve_home_end", timeoutTicks = 400)
    public static void homeListenerSeesEndAfterChain(GameTestHelper helper) {
        useChainSeam(helper);
        var p = playerBeside(helper, 0);
        List<Home> homes = new java.util.ArrayList<>();
        com.hogwartsmagic.pensieve.ViewerHomeListener l = (pl, root, reason, saw) -> {
            if (pl == p) homes.add(new Home(root, reason, saw));
        };
        com.hogwartsmagic.pensieve.PensieveSessions.addHomeListener(l);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startSolo(p, "chain_a"), "solo refused");
        helper.succeedWhen(() -> {
            helper.assertTrue(!homes.isEmpty(), "not home yet");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            com.hogwartsmagic.pensieve.PensieveSessions.removeHomeListener(l);
            logout(helper, p);
            helper.assertTrue(homes.size() == 1, "listener should fire once, fired " + homes.size());
            helper.assertTrue(homes.get(0).equals(new Home("chain_a", com.hogwartsmagic.pensieve.ExitReason.END, true)),
                    "unexpected " + homes.get(0));
            LOGGER.info("PENSIEVE-GAMETEST PASS homeListenerSeesEndAfterChain");
        });
    }

    @GameTest(template = EMPTY, batch = "pensieve_solo_container", timeoutTicks = 100)
    public static void soloClosesOpenContainer(GameTestHelper helper) {
        useChainSeam(helper);
        var p = playerBeside(helper, 0);
        p.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inv, pl) -> net.minecraft.world.inventory.ChestMenu.threeRows(id, inv),
                net.minecraft.network.chat.Component.literal("chest")));
        helper.assertTrue(p.containerMenu != p.inventoryMenu, "setup: a container should be open");
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startSolo(p, "chain_long"), "solo refused");
        helper.assertTrue(p.containerMenu == p.inventoryMenu, "a solo start should close the open container");
        com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        helper.succeedWhen(() -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            logout(helper, p);
            LOGGER.info("PENSIEVE-GAMETEST PASS soloClosesOpenContainer");
        });
    }

    @GameTest(template = EMPTY, batch = "pensieve_chain_cycle", timeoutTicks = 400)
    public static void chainCycleEnds(GameTestHelper helper) {
        // cyc_a names cyc_b, which names cyc_a again: the chain must play each once and end, not loop forever.
        SceneDef a = floorScene("cyc_a", "minecraft:stone", 40, "cyc_b");
        SceneDef b = floorScene("cyc_b", "minecraft:gold_block", 40, "cyc_a");
        com.hogwartsmagic.pensieve.PensieveSessions.setBuildBudgetForTests(-1);
        com.hogwartsmagic.pensieve.PensieveSessions.overrideForTests(helper.getLevel(), id -> java.util.Optional.of(
                new com.hogwartsmagic.pensieve.MemoryRegistry.LoadedScene("cyc_b".equals(id) ? b : a, "{}")));
        var p = playerBeside(helper, 0);
        List<Home> homes = new java.util.ArrayList<>();
        com.hogwartsmagic.pensieve.ViewerHomeListener l = (pl, root, reason, saw) -> {
            if (pl == p) homes.add(new Home(root, reason, saw));
        };
        com.hogwartsmagic.pensieve.PensieveSessions.addHomeListener(l);
        helper.assertTrue(com.hogwartsmagic.pensieve.PensieveSessions.startSolo(p, "cyc_a"), "solo refused");
        java.util.concurrent.atomic.AtomicBoolean sawB = new java.util.concurrent.atomic.AtomicBoolean();
        helper.onEachTick(() -> {
            var s = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p);
            if (s.isEmpty() || !"cyc_b".equals(s.get().sceneId())) return;
            sawB.set(true);
            helper.assertTrue(s.get().nextSession() == null, "cyc_b must not build cyc_a again: the chain loops");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sawB.get(), "never handed to cyc_b");
            helper.assertTrue(!homes.isEmpty(), "not home yet");
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            com.hogwartsmagic.pensieve.PensieveSessions.removeHomeListener(l);
            logout(helper, p);
            helper.assertTrue(homes.get(0).equals(new Home("cyc_a", com.hogwartsmagic.pensieve.ExitReason.END, true)),
                    "unexpected " + homes.get(0));
            LOGGER.info("PENSIEVE-GAMETEST PASS chainCycleEnds");
        });
    }
}
