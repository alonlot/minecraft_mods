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
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone",
                new int[]{0, 0, 0}, new int[]{0, 0, 0})), List.of(), List.of(), 6000L, 20);
        SceneDef scene = new SceneDef("gametest_session", "Test", length, 8, set, java.util.Map.of(), List.of(),
                List.of(), List.of(), null, null);
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

    @GameTest(template = EMPTY, batch = SESSIONS, timeoutTicks = 300)
    public static void secondViewerJoinsSameSession(GameTestHelper helper) {
        useSessionSeam(helper, 30);
        basinWithMemory(helper);
        var p1 = playerBeside(helper, 0);
        var p2 = playerBeside(helper, 1);
        helper.useBlock(BASIN, p1);
        java.util.concurrent.atomic.AtomicBoolean joined = new java.util.concurrent.atomic.AtomicBoolean();
        helper.runAtTickTime(40, () -> {
            helper.useBlock(BASIN, p2);
            var s1 = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p1);
            var s2 = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p2);
            helper.assertTrue(s1.isPresent() && s2.isPresent(), "both should be viewers");
            helper.assertTrue(s1.get() == s2.get(), "second viewer should join the same session");
            joined.set(true);
        });
        helper.runAtTickTime(80, () -> {
            var s1 = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p1);
            var s2 = com.hogwartsmagic.pensieve.PensieveSessions.sessionOf(p2);
            helper.assertTrue(s1.isPresent() && s2.isPresent() && s1.get() == s2.get(), "both should still watch");
            helper.assertTrue(s1.get().startGameTime() >= 0, "the session should be playing by tick 80");
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
        helper.runAtTickTime(40, () -> {
            var body = bodyOf(helper, p);
            helper.assertTrue(body.position().distanceTo(ret) <= 0.01, "the body should stand at the return point");
            helper.assertTrue(p.getUUID().equals(body.getOwnerId()), "the body should know its owner");
            held.set(body);
            com.hogwartsmagic.pensieve.PensieveSessions.leave(p, com.hogwartsmagic.pensieve.ExitReason.LEFT);
        });
        helper.runAtTickTime(80, () -> {
            helper.assertTrue(!com.hogwartsmagic.pensieve.PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(held.get().isRemoved(), "the body should be gone");
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
        net.minecraft.world.phys.Vec3 ret = p.position();
        p.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
        helper.useBlock(BASIN, p);
        helper.runAtTickTime(40, () -> {
            var body = bodyOf(helper, p);
            body.hurt(helper.getLevel().damageSources().genericKill(), 1000f);
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
}
