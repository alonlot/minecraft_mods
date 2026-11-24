package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.BloodSealBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/** The Locket cave's blood arch: bare rock, a toll, a one-second melt, and a reseal once nobody stands in it. */
@GameTestHolder(ExampleMod.MODID)
public final class SeaCaveGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final BlockPos SEAL = new BlockPos(1, 2, 1);

    private SeaCaveGameTests() {
    }

    private static BlockState seal(GameTestHelper helper) {
        return helper.getBlockState(SEAL);
    }

    /** A mock player cannot right-click through the client packet path, so the block's use logic is called directly. */
    private static void rightClickFromOutside(GameTestHelper helper, Player player) {
        BlockPos abs = helper.absolutePos(SEAL);
        Vec3 pos = Vec3.atBottomCenterOf(abs.west());
        player.setPos(pos.x, pos.y, pos.z);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(-0.5, 0, 0), Direction.WEST, abs, false);
        seal(helper).useWithoutItem(helper.getLevel(), player, hit);
    }

    private static void placeSeal(GameTestHelper helper) {
        helper.setBlock(SEAL, ExampleMod.BLOOD_SEAL.get().defaultBlockState().setValue(BloodSealBlock.FACING, Direction.WEST));
    }

    @GameTest(template = EMPTY, timeoutTicks = 1400)
    public static void bloodArchOpensOverOneSecondAndCostsToll(GameTestHelper helper) {
        placeSeal(helper);
        helper.assertTrue(seal(helper).getValue(BloodSealBlock.STAGE) == 0, "a fresh seal is bare rock, stage 0");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        float before = player.getHealth();
        rightClickFromOutside(helper, player);
        helper.assertTrue(Math.abs((before - player.getHealth()) - 6.0F) < 0.01F, "the toll should cost 6 health, cost " + (before - player.getHealth()));
        helper.assertTrue(seal(helper).getValue(BloodSealBlock.STAGE) == 1, "the melt starts at stage 1");

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(seal(helper).getValue(BloodSealBlock.STAGE) == 3, "stage 3 within 20 ticks, is " + seal(helper).getValue(BloodSealBlock.STAGE));
            helper.assertTrue(seal(helper).getCollisionShape(helper.getLevel(), helper.absolutePos(SEAL)).isEmpty(), "stage 3 has no collision");
        });
        helper.runAfterDelay(1260, () -> {
            helper.assertTrue(seal(helper).getValue(BloodSealBlock.STAGE) == 0, "the arch reseals to stage 0 after a minute");
            helper.assertFalse(seal(helper).getValue(BloodSealBlock.OPEN), "resealed means closed");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 1400)
    public static void bloodArchNeverResealsOnSomeoneInsideIt(GameTestHelper helper) {
        placeSeal(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        rightClickFromOutside(helper, player);
        helper.runAfterDelay(20, () -> helper.spawnWithNoFreeWill(EntityType.PIG, SEAL).setNoGravity(true)); // it must not fall out of the arch
        helper.runAfterDelay(1300, () -> {
            helper.assertTrue(seal(helper).getValue(BloodSealBlock.STAGE) == 3, "an open arch with someone in it stays open");
            helper.succeed();
        });
    }

    /** Two connected seals: clicking one carries the other to stage 3 as well. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void bloodArchMeltsEveryConnectedSeal(GameTestHelper helper) {
        placeSeal(helper);
        helper.setBlock(SEAL.above(), ExampleMod.BLOOD_SEAL.get().defaultBlockState().setValue(BloodSealBlock.FACING, Direction.WEST));
        rightClickFromOutside(helper, helper.makeMockPlayer(GameType.SURVIVAL));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(helper.getBlockState(SEAL.above()).getValue(BloodSealBlock.STAGE) == 3, "the neighbouring seal follows to stage 3");
            helper.assertTrue(seal(helper).getValue(BloodSealBlock.STAGE) == 3, "the clicked seal reaches stage 3");
            helper.succeed();
        });
    }

    /** A melt whose scheduled tick was lost must not strand the only way in: a click re-arms it, free of toll. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void stuckMeltIsRearmedByAClickWithoutToll(GameTestHelper helper) {
        helper.setBlock(SEAL, ExampleMod.BLOOD_SEAL.get().defaultBlockState()
                .setValue(BloodSealBlock.FACING, Direction.WEST).setValue(BloodSealBlock.STAGE, 1));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        float before = player.getHealth();
        rightClickFromOutside(helper, player);
        helper.assertTrue(player.getHealth() == before, "re-arming a stuck melt costs nothing");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(seal(helper).getValue(BloodSealBlock.STAGE) == 3, "the stuck arch reaches stage 3, is " + seal(helper).getValue(BloodSealBlock.STAGE));
            helper.succeed();
        });
    }

    // ---- the iron ring and its chain ------------------------------------------------------------

    private static final BlockPos RING = new BlockPos(0, 1, 1);
    private static final BlockPos WATER = new BlockPos(2, 1, 1);

    /**
     * A tiny arena by hand: a ring in the floor, one source of water two blocks away walled in with barriers,
     * and a basin block whose layout is set directly (no template, no marker scan).
     */
    private static com.hogwartsmagic.block.entity.CaveRingBlockEntity ringArena(GameTestHelper helper) {
        return ringArena(helper, true);
    }

    private static com.hogwartsmagic.locket.CaveLayout arenaLayout(GameTestHelper helper) {
        BlockPos ring = helper.absolutePos(RING.above());
        BlockPos dockShore = helper.absolutePos(WATER.above());
        BlockPos dockIsland = helper.absolutePos(WATER.above().east(2));
        return new com.hogwartsmagic.locket.CaveLayout(2, helper.absolutePos(new BlockPos(1, 1, 2)), java.util.List.of(), ring,
                dockShore, dockIsland, java.util.List.of(), helper.absolutePos(WATER).getY(), 12, 12, 12);
    }

    private static com.hogwartsmagic.block.entity.CaveRingBlockEntity ringArena(GameTestHelper helper, boolean scanned) {
        helper.setBlock(RING, ExampleMod.CAVE_RING.get());
        for (BlockPos wall : new BlockPos[]{WATER.east(), WATER.south(), WATER.north(), WATER.west(), WATER.below()}) {
            helper.setBlock(wall, net.minecraft.world.level.block.Blocks.BARRIER);
        }
        helper.setBlock(WATER, net.minecraft.world.level.block.Blocks.WATER);
        BlockPos basinPos = new BlockPos(1, 1, 2);
        helper.setBlock(basinPos, ExampleMod.DESPAIR_BASIN.get());
        if (scanned) {
            ((com.hogwartsmagic.block.entity.DespairBasinBlockEntity) helper.getBlockEntity(basinPos))
                    .setLayoutDirectly(arenaLayout(helper));
        }
        return (com.hogwartsmagic.block.entity.CaveRingBlockEntity) helper.getBlockEntity(RING);
    }

    private static java.util.List<com.hogwartsmagic.entity.CaveBoatEntity> skiffs(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(com.hogwartsmagic.entity.CaveBoatEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(WATER)).inflate(1.5D));
    }

    @GameTest(template = EMPTY, batch = "ring_pull", timeoutTicks = 200)
    public static void pullingRingRaisesSkiff(GameTestHelper helper) {
        com.hogwartsmagic.block.entity.CaveRingBlockEntity ring = ringArena(helper);
        net.minecraft.server.level.ServerPlayer player =
                (net.minecraft.server.level.ServerPlayer) helper.makeMockServerPlayerInLevel();
        ring.pull(player);
        helper.assertTrue(ring.haulTicks() == 0, "pulling a hidden chain does nothing");
        ring.reveal(200);
        helper.assertTrue(ring.revealed(), "reveal shows the chain");
        ring.pull(player);
        helper.assertTrue(ring.haulTicks() > 0, "pulling a revealed chain starts the haul");
        ring.pull(player);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(skiffs(helper).isEmpty(), "no skiff while the chain is still being hauled");
            helper.assertTrue(ring.haulTicks() > 0, "still hauling at tick 30");
            ring.pull(player); // a second pull mid-haul does nothing
        });
        helper.runAfterDelay(75, () -> {
            helper.assertTrue(skiffs(helper).size() == 1, "exactly one skiff after the haul, found " + skiffs(helper).size());
            net.minecraft.world.entity.Entity boat = skiffs(helper).get(0);
            BlockPos dock = helper.absolutePos(WATER);
            helper.assertTrue(Math.abs(boat.getX() - (dock.getX() + 0.5D)) < 0.01D
                    && Math.abs(boat.getZ() - (dock.getZ() + 0.5D)) < 0.01D, "the skiff is at the shore dock");
            helper.assertFalse(ring.revealed(), "the chain is spent and hidden again");
            ring.reveal(200);
            ring.pull(player); // a second pull with the skiff there does nothing
            helper.assertTrue(ring.haulTicks() == 0, "no haul while a skiff is at the dock");
        });
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(skiffs(helper).size() == 1, "still exactly one skiff after a second pull");
            skiffs(helper).forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    /** A ring touched before the basin has scanned its markers must not remember the legacy layout. */
    @GameTest(template = EMPTY, batch = "ring_legacy", timeoutTicks = 200)
    public static void ringTouchedBeforeScanDoesNotKeepLegacyLayout(GameTestHelper helper) {
        com.hogwartsmagic.block.entity.CaveRingBlockEntity ring = ringArena(helper, false);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ring.reveal(200);
        helper.assertTrue(ring.chainEnd() == null, "no chain end from a legacy layout");
        ring.pull(player);
        helper.assertTrue(ring.haulTicks() == 0, "the first pull is refused while the layout is unscanned");
        helper.runAfterDelay(10, () -> {
            ((com.hogwartsmagic.block.entity.DespairBasinBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 2)))
                    .setLayoutDirectly(arenaLayout(helper));
            ring.pull(player);
            helper.assertTrue(ring.haulTicks() > 0, "once the layout is v2 the pull starts the haul");
            helper.assertTrue(helper.absolutePos(WATER).below().equals(ring.chainEnd().below()), "chain ends at the shore dock water");
        });
        helper.runAfterDelay(90, () -> {
            java.util.List<com.hogwartsmagic.entity.CaveBoatEntity> boats = skiffs(helper);
            helper.assertTrue(boats.size() == 1, "one skiff at the shore dock, found " + boats.size());
            helper.assertTrue(Math.abs(boats.get(0).getX() - (helper.absolutePos(WATER).getX() + 0.5D)) < 0.01D, "skiff at dockShore");
            boats.forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    // ---- the skiff -----------------------------------------------------------------------------
    //
    // A hand-built water arena (not the 80x90x80 template): a 7x7 pool of source water inside a barrier ring, with a
    // CaveLayout built by CaveLayout.fromMarkers from markers placed round the middle block and turned by 0 or 90 degrees
    // about it, so the crossing is bent and the same code runs at both rotations.

    private static final String EMPTY9 = "hogwartsmagic:gametest/empty9";
    /** Marker offsets from the arena middle at rotation 0: shore dock, two path points, island dock. */
    private static final int[][] CROSSING = {{-3, -3}, {0, -3}, {2, 0}, {2, 3}};

    private static void waterArena(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.BARRIER);
                if (x == 0 || x == 8 || z == 0 || z == 8) {
                    helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.BARRIER);
                    helper.setBlock(new BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.BARRIER);
                } else {
                    helper.setBlock(new BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.WATER);
                }
            }
        }
    }

    private static com.hogwartsmagic.locket.CaveLayout crossingLayout(GameTestHelper helper, int quarterTurns) {
        java.util.List<com.hogwartsmagic.locket.CaveLayout.Marker> markers = new java.util.ArrayList<>();
        String[] roles = {"dock_shore", "path", "path", "dock_island"};
        for (int i = 0; i < CROSSING.length; i++) {
            int dx = CROSSING[i][0];
            int dz = CROSSING[i][1];
            for (int q = 0; q < quarterTurns; q++) {
                int t = dx;
                dx = -dz;
                dz = t;
            }
            markers.add(new com.hogwartsmagic.locket.CaveLayout.Marker(roles[i], i, helper.absolutePos(new BlockPos(4 + dx, 2, 4 + dz))));
        }
        return com.hogwartsmagic.locket.CaveLayout.fromMarkers(helper.absolutePos(new BlockPos(4, 2, 4)), markers);
    }

    private static java.util.List<com.hogwartsmagic.entity.CaveBoatEntity> arenaSkiffs(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(com.hogwartsmagic.entity.CaveBoatEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(4, 1, 4))).inflate(12.0D));
    }

    private static double flatDist(net.minecraft.world.entity.Entity e, BlockPos cell) {
        double dx = e.getX() - (cell.getX() + 0.5D);
        double dz = e.getZ() - (cell.getZ() + 0.5D);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Distance from the entity to the straight line through two cells, in the horizontal plane. */
    private static double offChord(net.minecraft.world.entity.Entity e, BlockPos a, BlockPos b) {
        double ax = a.getX() + 0.5D;
        double az = a.getZ() + 0.5D;
        double bx = b.getX() + 0.5D - ax;
        double bz = b.getZ() + 0.5D - az;
        return Math.abs((e.getX() - ax) * bz - (e.getZ() - az) * bx) / Math.sqrt(bx * bx + bz * bz);
    }

    private static void mount(com.hogwartsmagic.entity.CaveBoatEntity boat, net.minecraft.server.level.ServerPlayer player) {
        player.setPos(boat.getX(), boat.getY(), boat.getZ());
        boat.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    /** The rider reaches the island dock along the bent path, steps off, comes back along it, and gets to the shore. */
    private static void crossingTest(GameTestHelper helper, int quarterTurns) {
        waterArena(helper);
        com.hogwartsmagic.locket.CaveLayout layout = crossingLayout(helper, quarterTurns);
        BlockPos shore = layout.dockShore().below();
        BlockPos island = layout.dockIsland().below();
        com.hogwartsmagic.entity.CaveBoatEntity boat = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout);
        helper.assertTrue(boat != null, "a skiff rises");
        helper.assertTrue(boat.riseTicks() == com.hogwartsmagic.locket.SkiffRules.RISE_TICKS, "the rise starts at RISE_TICKS");
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int[] stage = {0};
        double[] bend = {0.0D};
        helper.runAfterDelay(650, () -> helper.fail("stalled at stage " + stage[0] + ": boat " + boat.position() + " rise " + boat.riseTicks()
                + " next/dir/end " + boat.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getInt("Next") + "/" + boat.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getInt("Dir") + "/" + boat.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getInt("DockEnd") + " riders " + boat.getPassengers().size() + " shore " + shore + " island " + island + " bend " + bend[0]));
        helper.onEachTick(() -> {
            if (stage[0] == 0 && boat.riseTicks() == 0) {
                helper.assertTrue(flatDist(boat, shore) < 0.01D, "it rises at the shore dock");
                mount(boat, player);
                helper.assertTrue(boat.getPassengers().contains(player), "the rider boards");
                stage[0] = 1;
            } else if (stage[0] == 1) {
                bend[0] = Math.max(bend[0], offChord(boat, shore, island));
                if (boat.isDockedAtIsland()) {
                    helper.assertTrue(bend[0] > 0.8D, "it followed the bent path, strayed " + bend[0] + " from the chord");
                    helper.assertTrue(boat.getPassengers().contains(player), "the rider is still aboard at the island");
                    boat.ejectPassengers();
                    helper.assertTrue(boat.getPassengers().isEmpty() && player.getVehicle() == null, "the rider steps off at the island");
                    mount(boat, player); // aboard again at the island: the trip home
                    helper.assertTrue(boat.getPassengers().contains(player), "the rider boards again at the island");
                    stage[0] = 2;
                }
            } else if (stage[0] == 2 && flatDist(boat, shore) < 0.4D) {
                helper.assertTrue(arenaSkiffs(helper).size() == 1, "still one skiff");
                boat.discard();
                helper.succeed();
            }
        });
    }

    @GameTest(template = EMPTY9, batch = "skiff_a", timeoutTicks = 700)
    public static void skiffCrossesAndReturnsRotation0(GameTestHelper helper) {
        crossingTest(helper, 0);
    }

    @GameTest(template = EMPTY9, batch = "skiff_b", timeoutTicks = 700)
    public static void skiffCrossesAndReturnsRotation90(GameTestHelper helper) {
        crossingTest(helper, 1);
    }

    /** An empty skiff at the island stays there; a second pull calls it back instead of raising another. */
    @GameTest(template = EMPTY9, batch = "skiff_c", timeoutTicks = 800)
    public static void skiffEmptyAtIslandStaysThenIsCalledBack(GameTestHelper helper) {
        waterArena(helper);
        com.hogwartsmagic.locket.CaveLayout layout = crossingLayout(helper, 0);
        BlockPos shore = layout.dockShore().below();
        BlockPos island = layout.dockIsland().below();
        com.hogwartsmagic.entity.CaveBoatEntity boat = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        long[] stamp = {0L};
        int[] stage = {0};
        helper.onEachTick(() -> {
            if (stage[0] == 0 && boat.riseTicks() == 0) {
                mount(boat, player);
                helper.assertTrue(com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout) == null,
                        "no second skiff while one carries a rider");
                stage[0] = 1;
            } else if (stage[0] == 1 && boat.isDockedAtIsland()) {
                boat.ejectPassengers();
                player.setPos(player.getX(), player.getY() + 300.0D, player.getZ()); // nobody on the island
                stamp[0] = helper.getTick();
                stage[0] = 2;
            } else if (stage[0] == 2 && helper.getTick() - stamp[0] >= 100L) {
                helper.assertTrue(flatDist(boat, island) < 0.4D && boat.isAlive(), "an empty skiff stays docked at the island");
                helper.assertTrue(com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout) == boat,
                        "the pull calls the same skiff back");
                helper.assertTrue(arenaSkiffs(helper).size() == 1, "still one skiff");
                stage[0] = 3;
            } else if (stage[0] == 3 && flatDist(boat, shore) < 0.4D) {
                helper.assertTrue(arenaSkiffs(helper).size() == 1, "one skiff after the call-back");
                boat.discard();
                helper.succeed();
            }
        });
    }

    /** A rider lost mid-lake leaves the skiff empty; it turns round and goes back to the shore dock. */
    @GameTest(template = EMPTY9, batch = "skiff_d", timeoutTicks = 800)
    public static void skiffEmptyMidLakeReturnsToShore(GameTestHelper helper) {
        waterArena(helper);
        com.hogwartsmagic.locket.CaveLayout layout = crossingLayout(helper, 0);
        BlockPos shore = layout.dockShore().below();
        com.hogwartsmagic.entity.CaveBoatEntity boat = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int[] stage = {0};
        helper.onEachTick(() -> {
            if (stage[0] == 0 && boat.riseTicks() == 0) {
                mount(boat, player);
                stage[0] = 1;
            } else if (stage[0] == 1 && flatDist(boat, shore) > 4.0D) {
                boat.ejectPassengers();
                player.setPos(player.getX(), player.getY() + 300.0D, player.getZ()); // nobody near the island
                helper.assertTrue(com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout) == null,
                        "a skiff out on the lake is not replaced");
                stage[0] = 2;
            } else if (stage[0] == 2 && flatDist(boat, shore) < 0.4D) {
                helper.assertTrue(arenaSkiffs(helper).size() == 1, "one skiff");
                boat.discard();
                helper.succeed();
            }
        });
    }

    /** Board at the island and step straight off: the skiff must stay for the person on the island, not sail away empty. */
    @GameTest(template = EMPTY9, batch = "skiff_g", timeoutTicks = 800)
    public static void skiffBoardedAtIslandThenDismountedStaysAtIsland(GameTestHelper helper) {
        waterArena(helper);
        com.hogwartsmagic.locket.CaveLayout layout = crossingLayout(helper, 0);
        BlockPos island = layout.dockIsland().below();
        com.hogwartsmagic.entity.CaveBoatEntity boat = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        long[] stamp = {0L};
        int[] stage = {0};
        helper.onEachTick(() -> {
            if (stage[0] == 0 && boat.riseTicks() == 0) {
                mount(boat, player);
                stage[0] = 1;
            } else if (stage[0] == 1 && boat.isDockedAtIsland()) {
                boat.ejectPassengers();
                mount(boat, player); // sets the trip home going ...
                boat.ejectPassengers(); // ... and the rider steps straight off
                helper.assertTrue(boat.getPassengers().isEmpty(), "the rider is off");
                stamp[0] = helper.getTick();
                stage[0] = 2;
            } else if (stage[0] == 2 && helper.getTick() - stamp[0] >= 120L) {
                helper.assertTrue(boat.isDockedAtIsland() && flatDist(boat, island) < 0.4D, "the skiff stays at the island dock");
                boat.discard();
                helper.succeed();
            }
        });
    }

    /** Step off two blocks out from the island while the skiff heads for the shore: it turns round to the island. */
    @GameTest(template = EMPTY9, batch = "skiff_h", timeoutTicks = 900)
    public static void skiffDismountedNearIslandReturnsToIsland(GameTestHelper helper) {
        waterArena(helper);
        com.hogwartsmagic.locket.CaveLayout layout = crossingLayout(helper, 0);
        BlockPos island = layout.dockIsland().below();
        com.hogwartsmagic.entity.CaveBoatEntity boat = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout);
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int[] stage = {0};
        helper.onEachTick(() -> {
            if (stage[0] == 0 && boat.riseTicks() == 0) {
                mount(boat, player);
                stage[0] = 1;
            } else if (stage[0] == 1 && boat.isDockedAtIsland()) {
                boat.ejectPassengers();
                mount(boat, player); // heading back to the shore
                stage[0] = 2;
            } else if (stage[0] == 2 && flatDist(boat, island) > 2.0D) {
                boat.ejectPassengers();
                player.setPos(boat.getX(), boat.getY(), boat.getZ()); // lands on the island side, near the dock
                stage[0] = 3;
            } else if (stage[0] == 3 && boat.isDockedAtIsland()) {
                helper.assertTrue(arenaSkiffs(helper).size() == 1, "one skiff");
                boat.discard();
                helper.succeed();
            }
        });
    }

    /** Duplicates left by a logout: empty ones away from the island are tidied before a new skiff is considered. */
    @GameTest(template = EMPTY9, batch = "skiff_i", timeoutTicks = 200)
    public static void skiffDuplicatesAreTidiedOnRise(GameTestHelper helper) {
        waterArena(helper);
        com.hogwartsmagic.locket.CaveLayout layout = crossingLayout(helper, 0);
        com.hogwartsmagic.entity.CaveBoatEntity first = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout);
        com.hogwartsmagic.entity.CaveBoatEntity twin = new com.hogwartsmagic.entity.CaveBoatEntity(
                com.hogwartsmagic.entity.ModEntities.CAVE_BOAT.get(), helper.getLevel());
        twin.load(first.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        twin.setUUID(java.util.UUID.randomUUID());
        twin.setPos(first.getX(), first.getY(), first.getZ());
        helper.getLevel().addFreshEntity(twin);
        helper.assertTrue(arenaSkiffs(helper).size() == 2, "set up two skiffs for one cave, found " + arenaSkiffs(helper).size() + " twin removed " + twin.isRemoved());
        com.hogwartsmagic.entity.CaveBoatEntity kept = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), layout);
        helper.assertTrue(kept == null, "an existing skiff at the shore means no new one");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(arenaSkiffs(helper).size() == 1, "the duplicate was discarded, found " + arenaSkiffs(helper).size());
            arenaSkiffs(helper).forEach(net.minecraft.world.entity.Entity::discard);
            helper.succeed();
        });
    }

    /** Two Inferi can latch on, a third is refused, and each slows the crossing. */
    @GameTest(template = EMPTY9, batch = "skiff_e", timeoutTicks = 100)
    public static void skiffLatchCapsAtTwoAndSlows(GameTestHelper helper) {
        waterArena(helper);
        com.hogwartsmagic.entity.CaveBoatEntity boat = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), crossingLayout(helper, 0));
        com.hogwartsmagic.entity.InferiusEntity[] inferi = new com.hogwartsmagic.entity.InferiusEntity[3];
        for (int i = 0; i < 3; i++) {
            inferi[i] = helper.spawnWithNoFreeWill(com.hogwartsmagic.entity.ModEntities.INFERIUS.get(), new BlockPos(4, 1, 4));
        }
        helper.assertTrue(boat.latch(inferi[0]) && boat.latch(inferi[1]), "two Inferi latch on");
        helper.assertFalse(boat.latch(inferi[2]), "a third is refused");
        helper.assertTrue(boat.latchedCount() == 2, "two latched");
        boat.unlatch(inferi[0]);
        helper.assertTrue(boat.latchedCount() == 1 && boat.latch(inferi[2]), "unlatching frees a place");
        for (com.hogwartsmagic.entity.InferiusEntity i : inferi) {
            i.discard();
        }
        boat.discard();
        helper.succeed();
    }

    /**
     * The template's waypoint markers must really load into CaveWaypointBlockEntity: the NBT stored in the structure is
     * fed through the block entity loader, and every role the layout needs is read back with an index.
     */
    @GameTest(template = EMPTY, batch = "skiff_f", timeoutTicks = 60)
    public static void templateWaypointNbtLoadsIntoBlockEntities(GameTestHelper helper) {
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = helper.getLevel().getStructureManager()
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "locket_cave")).orElse(null);
        helper.assertTrue(template != null, "the locket_cave template exists");
        java.util.List<net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo> infos =
                template.filterBlocks(BlockPos.ZERO, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                        ExampleMod.CAVE_WAYPOINT.get());
        helper.assertTrue(!infos.isEmpty(), "the template carries waypoint blocks");
        java.util.Set<String> roles = new java.util.HashSet<>();
        for (net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo info : infos) {
            helper.assertTrue(info.nbt() != null, "a waypoint block carries block entity NBT at " + info.pos());
            // The same call StructureTemplate.placeInWorld makes: a fresh block entity of the block, then its stored NBT.
            com.hogwartsmagic.block.entity.CaveWaypointBlockEntity be =
                    new com.hogwartsmagic.block.entity.CaveWaypointBlockEntity(info.pos(), info.state());
            be.loadWithComponents(info.nbt(), helper.getLevel().registryAccess());
            roles.add(be.role());
        }
        helper.assertFalse(roles.contains(""), "every waypoint has a role, found " + roles);
        helper.assertTrue(roles.contains("dock_shore") && roles.contains("dock_island") && roles.contains("path"),
                "the dock and path roles are present, found " + roles);
        helper.succeed();
    }
}
