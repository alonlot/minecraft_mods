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
        // A skiff left by a neighbouring test sits inside rise()'s one-per-cave search; clear strays first.
        for (com.hogwartsmagic.entity.CaveBoatEntity stray : helper.getLevel().getEntitiesOfClass(com.hogwartsmagic.entity.CaveBoatEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(4, 1, 4))).inflate(24.0D))) {
            stray.discard();
        }
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
                .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hogwartsmagic", "locket_sea_cave")).orElse(null);
        helper.assertTrue(template != null, "the locket_sea_cave template exists");
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

    // ---- the goblet and the basin --------------------------------------------------------------
    //
    // A mock player cannot run the item-use animation, so these call dip and CaveGobletItem.drinkFrom (what
    // finishUsingItem does on the server) directly. Everything happens inside one test tick so the basin never
    // ticks with its mock drinker absent from the player list.

    private static final BlockPos GOBLET_BASIN = new BlockPos(1, 1, 1);

    private static com.hogwartsmagic.block.entity.DespairBasinBlockEntity gobletBasin(GameTestHelper helper, boolean v2) {
        helper.setBlock(GOBLET_BASIN, ExampleMod.DESPAIR_BASIN.get());
        com.hogwartsmagic.block.entity.DespairBasinBlockEntity be =
                (com.hogwartsmagic.block.entity.DespairBasinBlockEntity) helper.getBlockEntity(GOBLET_BASIN);
        if (v2) {
            BlockPos at = helper.absolutePos(GOBLET_BASIN);
            be.setLayoutDirectly(new com.hogwartsmagic.locket.CaveLayout(2, at, java.util.List.of(), at, at, at,
                    java.util.List.of(), at.getY() - 1, 12, 12, 12));
        }
        return be;
    }

    private static net.minecraft.server.level.ServerPlayer onIsland(GameTestHelper helper) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(GOBLET_BASIN.above()));
        player.setPos(at.x, at.y, at.z);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static int basinLevel(GameTestHelper helper) {
        return helper.getBlockState(GOBLET_BASIN).getValue(com.hogwartsmagic.block.DespairBasinBlock.LEVEL);
    }

    @GameTest(template = EMPTY, batch = "goblet_a", timeoutTicks = 60)
    public static void legacyBasinAcceptsGoblet(GameTestHelper helper) {
        com.hogwartsmagic.block.entity.DespairBasinBlockEntity basin = gobletBasin(helper, false);
        helper.assertTrue(basin.layout().version() == 1, "no markers around the basin means the legacy layout");
        net.minecraft.server.level.ServerPlayer player = onIsland(helper);
        net.minecraft.world.item.ItemStack goblet = new net.minecraft.world.item.ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get());
        helper.assertTrue(com.hogwartsmagic.item.CaveGobletItem.fillOf(goblet) == com.hogwartsmagic.locket.GobletFill.EMPTY, "a new goblet is empty");
        helper.assertTrue(basin.dip(player, goblet), "the legacy basin accepts a dip");
        helper.assertTrue(com.hogwartsmagic.item.CaveGobletItem.fillOf(goblet) == com.hogwartsmagic.locket.GobletFill.POTION, "the dipped goblet holds the potion");
        helper.assertTrue(basin.drinks() == 0 && basinLevel(helper) == 6, "dipping alone does not lower the basin");
        com.hogwartsmagic.item.CaveGobletItem.drinkFrom(goblet, helper.getLevel(), player);
        helper.assertTrue(basin.drinks() == 1, "one cup drunk, drinks " + basin.drinks());
        helper.assertTrue(basinLevel(helper) == 5, "the basin drops one level, is " + basinLevel(helper));
        helper.assertTrue(com.hogwartsmagic.item.CaveGobletItem.fillOf(goblet) == com.hogwartsmagic.locket.GobletFill.EMPTY, "drinking empties the goblet");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "goblet_b", timeoutTicks = 60)
    public static void sixCupsShowLocket(GameTestHelper helper) {
        com.hogwartsmagic.block.entity.DespairBasinBlockEntity basin = gobletBasin(helper, true);
        net.minecraft.server.level.ServerPlayer player = onIsland(helper);
        net.minecraft.server.level.ServerPlayer other = onIsland(helper);
        helper.assertFalse(player.getUUID().equals(other.getUUID()), "two different players");
        net.minecraft.world.item.ItemStack goblet = new net.minecraft.world.item.ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get());
        net.minecraft.world.item.ItemStack otherGoblet = new net.minecraft.world.item.ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get());
        for (int cup = 1; cup <= 6; cup++) {
            basin.forgetLastDrinkForTest(); // the spacing rule would otherwise need 20 ticks between cups
            helper.assertTrue(basin.dip(player, goblet), "dip " + cup + " is accepted");
            if (cup == 3) {
                helper.assertFalse(basin.dip(other, otherGoblet), "a second player is refused mid-run");
                helper.assertTrue(com.hogwartsmagic.item.CaveGobletItem.fillOf(otherGoblet) == com.hogwartsmagic.locket.GobletFill.EMPTY, "the refused goblet stays empty");
            }
            player.setHealth(player.getMaxHealth());
            com.hogwartsmagic.item.CaveGobletItem.drinkFrom(goblet, helper.getLevel(), player);
            helper.assertTrue(basinLevel(helper) == 6 - cup, "after cup " + cup + " the level is " + basinLevel(helper));
        }
        BlockState state = helper.getBlockState(GOBLET_BASIN);
        helper.assertTrue(basinLevel(helper) == 0, "six cups leave the basin dry");
        helper.assertTrue(state.getValue(com.hogwartsmagic.block.DespairBasinBlock.LOCKET), "the Locket shows on the dry floor");
        basin.forgetLastDrinkForTest();
        helper.assertFalse(basin.dip(player, goblet), "a dry basin takes no seventh cup");
        basin.takeLocket(other);
        helper.assertTrue(helper.getBlockState(GOBLET_BASIN).getValue(com.hogwartsmagic.block.DespairBasinBlock.LOCKET), "another player cannot take the Locket");
        basin.takeLocket(player);
        helper.assertTrue(helper.getBlockState(GOBLET_BASIN).getValue(com.hogwartsmagic.block.DespairBasinBlock.STAGE) == com.hogwartsmagic.locket.BasinStage.LOOTED, "the drinker takes it");
        helper.assertTrue(player.getInventory().contains(new net.minecraft.world.item.ItemStack(ExampleMod.SLYTHERINS_LOCKET.get())), "the Locket is in the drinker's pack");
        helper.succeed();
    }

    /** After the sixth cup the drinker holds an empty goblet; a right-click with it on the dry basin takes the Locket. */
    @GameTest(template = EMPTY, batch = "goblet_g", timeoutTicks = 60)
    public static void emptyGobletClickTakesLocket(GameTestHelper helper) {
        com.hogwartsmagic.block.entity.DespairBasinBlockEntity basin = gobletBasin(helper, true);
        net.minecraft.server.level.ServerPlayer player = onIsland(helper);
        net.minecraft.world.item.ItemStack goblet = new net.minecraft.world.item.ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get());
        for (int cup = 1; cup <= 6; cup++) {
            basin.forgetLastDrinkForTest();
            helper.assertTrue(basin.dip(player, goblet), "dip " + cup);
            player.setHealth(player.getMaxHealth());
            com.hogwartsmagic.item.CaveGobletItem.drinkFrom(goblet, helper.getLevel(), player);
        }
        helper.assertTrue(basinLevel(helper) == 0, "six cups leave the basin dry");
        helper.assertTrue(com.hogwartsmagic.item.CaveGobletItem.fillOf(goblet) == com.hogwartsmagic.locket.GobletFill.EMPTY, "the drinker holds an empty goblet");
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, goblet);
        BlockPos abs = helper.absolutePos(GOBLET_BASIN);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false);
        helper.getBlockState(GOBLET_BASIN).useItemOn(goblet, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(helper.getBlockState(GOBLET_BASIN).getValue(com.hogwartsmagic.block.DespairBasinBlock.STAGE) == com.hogwartsmagic.locket.BasinStage.LOOTED,
                "a click with the empty goblet takes the Locket");
        helper.assertTrue(player.getInventory().contains(new net.minecraft.world.item.ItemStack(ExampleMod.SLYTHERINS_LOCKET.get())), "the Locket is in the pack");
        helper.succeed();
    }

    /** The decorative goblet (Diagon Alley) is a plain block item, and outside a cave a click passes and breaking drops it. */
    @GameTest(template = EMPTY, batch = "goblet_h", timeoutTicks = 60)
    public static void decorativeGobletIsPlainOutsideCaves(GameTestHelper helper) {
        helper.assertTrue(ExampleMod.CRYSTAL_GOBLET.get().asItem() != net.minecraft.world.item.Items.AIR, "the goblet block has an item");
        gobletFloor(helper);
        // basins left behind by other tests would make this look like a cave
        net.minecraft.server.level.ServerLevel lvl = helper.getLevel();
        BlockPos centre = helper.absolutePos(BlockPos.ZERO);
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-60, -10, -60), centre.offset(60, 30, 60))) {
            if (lvl.getBlockState(p).is(ExampleMod.DESPAIR_BASIN.get())) {
                lvl.removeBlock(p.immutable(), false);
            }
        }
        helper.setBlock(new BlockPos(1, 1, 1), ExampleMod.CRYSTAL_GOBLET.get());
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 1, 1));
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false);
        net.minecraft.world.InteractionResult r = helper.getBlockState(new BlockPos(1, 1, 1)).useWithoutItem(helper.getLevel(), player, hit);
        helper.assertTrue(r == net.minecraft.world.InteractionResult.PASS, "no basin around: the click passes, got " + r);
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).is(ExampleMod.CRYSTAL_GOBLET.get()), "the goblet stays");
        java.util.List<net.minecraft.world.item.ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                helper.getBlockState(new BlockPos(1, 1, 1)), helper.getLevel(), abs, null);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ExampleMod.CRYSTAL_GOBLET.get().asItem()), "breaking drops the plain goblet: " + drops);
        helper.succeed();
    }

    // ---- the basin restores its goblet ---------------------------------------------------------

    /**
     * Leftovers of earlier tests (a mock player still carrying a goblet, a dropped goblet) are inside a legacy basin's
     * cave-wide search box and would stop it restoring one; batch order is not fixed, so each test clears them first.
     */
    private static void purgeGoblets(GameTestHelper helper) {
        for (net.minecraft.server.level.ServerPlayer p : helper.getLevel().players()) {
            p.getInventory().clearOrCountMatchingItems(st -> st.is(ExampleMod.CRYSTAL_GOBLET_ITEM.get()), -1, p.inventoryMenu.getCraftSlots());
        }
        for (net.minecraft.world.entity.item.ItemEntity e : helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(200.0D),
                i -> i.getItem().is(ExampleMod.CRYSTAL_GOBLET_ITEM.get()))) {
            e.discard();
        }
    }

    private static void gobletFloor(GameTestHelper helper) {
        purgeGoblets(helper);
        for (int x = 0; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
    }

    private static int gobletBlocks(GameTestHelper helper) {
        int n = 0;
        for (int x = 0; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                for (int z = 0; z <= 2; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(ExampleMod.CRYSTAL_GOBLET.get())) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    @GameTest(template = EMPTY, batch = "goblet_c", timeoutTicks = 200)
    public static void basinRestoresALostGoblet(GameTestHelper helper) {
        gobletFloor(helper);
        gobletBasin(helper, false);
        helper.assertTrue(gobletBlocks(helper) == 0, "no goblet block yet, the basin had its goblet destroyed");
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(gobletBlocks(helper) == 1, "the basin set exactly one goblet back, found " + gobletBlocks(helper));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "goblet_d", timeoutTicks = 200)
    public static void basinAddsNoGobletWhileOneIsCarried(GameTestHelper helper) {
        gobletFloor(helper);
        gobletBasin(helper, false);
        net.minecraft.server.level.ServerPlayer player = onIsland(helper);
        player.getInventory().add(new net.minecraft.world.item.ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get()));
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(gobletBlocks(helper) == 0, "a carried goblet means no new block, found " + gobletBlocks(helper));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "goblet_e", timeoutTicks = 200)
    public static void basinAddsNoGobletWhileOneLiesOnTheFloor(GameTestHelper helper) {
        gobletFloor(helper);
        gobletBasin(helper, false);
        helper.spawnItem(ExampleMod.CRYSTAL_GOBLET_ITEM.get(), 0.5F, 1.0F, 0.5F);
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(gobletBlocks(helper) == 0, "a dropped goblet means no new block, found " + gobletBlocks(helper));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "goblet_f", timeoutTicks = 200)
    public static void basinKeepsAnExistingGobletAlone(GameTestHelper helper) {
        gobletFloor(helper);
        gobletBasin(helper, false);
        helper.setBlock(new BlockPos(2, 1, 1), ExampleMod.CRYSTAL_GOBLET.get());
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(gobletBlocks(helper) == 1, "still exactly one goblet block, found " + gobletBlocks(helper));
            helper.succeed();
        });
    }

    // ---- thirst through the goblet -------------------------------------------------------------

    private static void thirst(net.minecraft.server.level.ServerPlayer player) {
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(ExampleMod.UNQUENCHABLE_THIRST.getHolder().get(), 6000, 0));
    }

    /** Conjured water never reaches a thirsty player. Calls AquaStream.spoils directly: a stream needs a real caster. */
    @GameTest(template = EMPTY, batch = "thirst_a", timeoutTicks = 60)
    public static void aquaIntoGobletVanishes(GameTestHelper helper) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.0, 1.5));
        player.setPos(at.x, at.y, at.z);
        thirst(player);
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        com.hogwartsmagic.spell.AquaStream.Drop near = new com.hogwartsmagic.spell.AquaStream.Drop(1, at.add(2.0, 0.5, 0.0), new Vec3(-1, 0, 0));
        helper.assertTrue(com.hogwartsmagic.spell.AquaStream.spoils(level, null, near, null), "a drop 2 blocks from a thirsty player vanishes");
        helper.assertTrue(near.gone, "the spoiled drop is removed");
        helper.assertTrue(com.hogwartsmagic.locket.LocketCaves.isThirsty(player), "the water did not cure the thirst");
        com.hogwartsmagic.spell.AquaStream.Drop far = new com.hogwartsmagic.spell.AquaStream.Drop(2, at.add(8.0, 0.5, 0.0), new Vec3(-1, 0, 0));
        helper.assertFalse(com.hogwartsmagic.spell.AquaStream.spoils(level, null, far, null) || far.gone, "a distant drop flies on");
        // landing on a player holding a goblet, not thirsty
        player.removeAllEffects();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get()));
        com.hogwartsmagic.spell.AquaStream.Drop onGoblet = new com.hogwartsmagic.spell.AquaStream.Drop(3, at.add(8.0, 0.5, 0.0), new Vec3(-1, 0, 0));
        var hit = new com.hogwartsmagic.spell.AquaStream.Drop.Hit(at, new Vec3(-1, 0, 0), null, player, 8.0);
        helper.assertTrue(com.hogwartsmagic.spell.AquaStream.spoils(level, null, onGoblet, hit) && onGoblet.gone, "water landing on a goblet holder vanishes");
        helper.succeed();
    }

    /** A thirsty player dips the goblet in covered cave-lake water, drinks it: cured, and the lake wakes. */
    @GameTest(template = EMPTY, batch = "thirst_b", timeoutTicks = 60)
    public static void gobletInLakeStartsAmbush(GameTestHelper helper) {
        gobletFloor(helper);
        com.hogwartsmagic.block.entity.DespairBasinBlockEntity basin = gobletBasin(helper, false);
        BlockPos at = helper.absolutePos(GOBLET_BASIN);
        basin.setLayoutDirectly(new com.hogwartsmagic.locket.CaveLayout(2, at, java.util.List.of(), at, at, at,
                java.util.List.of(), at.getY(), 12, 12, 12));
        helper.setBlock(new BlockPos(2, 1, 2), net.minecraft.world.level.block.Blocks.WATER);
        helper.setBlock(new BlockPos(2, 2, 2), net.minecraft.world.level.block.Blocks.STONE);
        net.minecraft.server.level.ServerPlayer player = onIsland(helper);
        thirst(player);
        net.minecraft.world.item.ItemStack goblet = new net.minecraft.world.item.ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get());
        BlockPos lake = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.assertTrue(com.hogwartsmagic.locket.LocketCaves.isCoveredWater(helper.getLevel(), lake), "the cell is covered water");
        helper.assertTrue(com.hogwartsmagic.locket.LocketCaves.dipGoblet(helper.getLevel(), player, goblet, lake), "the dip fills");
        helper.assertTrue(com.hogwartsmagic.item.CaveGobletItem.fillOf(goblet) == com.hogwartsmagic.locket.GobletFill.WATER, "goblet holds water");
        helper.assertTrue(basin.isAmbushActive(), "the lake wakes the moment the goblet touches it");
        helper.assertTrue(com.hogwartsmagic.locket.LocketCaves.isThirsty(player), "still thirsty until the drink");
        com.hogwartsmagic.item.CaveGobletItem.drinkFrom(goblet, helper.getLevel(), player);
        helper.assertFalse(com.hogwartsmagic.locket.LocketCaves.isThirsty(player), "thirst cured");
        helper.assertTrue(basin.isAmbushActive(), "the lake wakes");
        helper.assertTrue(com.hogwartsmagic.item.CaveGobletItem.fillOf(goblet) == com.hogwartsmagic.locket.GobletFill.EMPTY, "goblet emptied");
        // water above the lake surface (a pool, a placed bucket) is not the lake and is refused
        helper.setBlock(new BlockPos(0, 2, 2), net.minecraft.world.level.block.Blocks.WATER);
        net.minecraft.world.item.ItemStack second = new net.minecraft.world.item.ItemStack(ExampleMod.CRYSTAL_GOBLET_ITEM.get());
        helper.assertFalse(com.hogwartsmagic.locket.LocketCaves.dipGoblet(helper.getLevel(), player, second, helper.absolutePos(new BlockPos(0, 2, 2))),
                "water above the lake surface is not the lake");
        helper.succeed();
    }

    // ---- the Inferi on the island -------------------------------------------------------------
    //
    // A compact 9x9 arena (not the 80x90x80 template): floor y=0, a 2-deep pool (water y=1..2) round an island whose
    // rings rise one block at a time (slope: top y=1, 2, 3) or stand sheer (wall top y=4, two above the waterline).
    // Real AI, real pathing; the target is set directly because a mock player is not picked by target goals.

    private static final int ARENA_MID = 4;

    private static int ring(int x, int z) {
        return Math.max(Math.abs(x - ARENA_MID), Math.abs(z - ARENA_MID));
    }

    private static void inferiusArena(GameTestHelper helper, boolean sheer) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                int d = ring(x, z);
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.DEEPSLATE);
                int top = sheer ? (d <= 2 ? 4 : 0) : (d <= 1 ? 3 : d == 2 ? 2 : d == 3 ? 1 : 0);
                for (int y = 1; y <= 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), y <= top ? net.minecraft.world.level.block.Blocks.DEEPSLATE
                            : y <= 2 ? net.minecraft.world.level.block.Blocks.WATER : net.minecraft.world.level.block.Blocks.AIR);
                }
            }
        }
    }

    private static net.minecraft.server.level.ServerPlayer islandPlayer(GameTestHelper helper, int topY) {
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(ARENA_MID, topY + 1, ARENA_MID)));
        player.setPos(at.x, at.y, at.z);
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static com.hogwartsmagic.entity.InferiusEntity lakeInferius(GameTestHelper helper, net.minecraft.world.entity.player.Player target) {
        com.hogwartsmagic.entity.InferiusEntity e = helper.spawn(com.hogwartsmagic.entity.ModEntities.INFERIUS.get(), new BlockPos(ARENA_MID, 1, 0));
        e.setTarget(target);
        return e;
    }

    private static double flatDistTo(net.minecraft.world.entity.Entity a, net.minecraft.world.entity.Entity b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    /** Spawned in the lake with a player on the island top, the Inferius must walk out of the water and reach melee range. */
    @GameTest(template = EMPTY9, batch = "inferi_reach_a", timeoutTicks = 420)
    public static void inferiusReachesIslandFromLake(GameTestHelper helper) {
        inferiusArena(helper, false);
        net.minecraft.server.level.ServerPlayer player = islandPlayer(helper, 3);
        com.hogwartsmagic.entity.InferiusEntity inferius = lakeInferius(helper, player);
        helper.onEachTick(() -> {
            if (!inferius.isAlive()) {
                helper.fail("the Inferius died");
                return;
            }
            inferius.setTarget(player);
            if (flatDistTo(inferius, player) <= 2.0D && Math.abs(inferius.getY() - player.getY()) < 1.5D && !inferius.isInWater()) {
                System.out.println("[inferi-reach] slope reached in " + helper.getTick() + " ticks");
                helper.succeed();
            }
        });
        helper.runAfterDelay(400, () -> helper.fail("the Inferius never reached the island top; at "
                + inferius.position() + " inWater " + inferius.isInWater() + " dist " + flatDistTo(inferius, player)));
    }

    /** The control: the same arena with a sheer wall must keep the Inferius out, which is what made the old cave fail. */
    @GameTest(template = EMPTY9, batch = "inferi_reach_b", timeoutTicks = 420)
    public static void inferiusCannotReachSheerIsland(GameTestHelper helper) {
        inferiusArena(helper, true);
        net.minecraft.server.level.ServerPlayer player = islandPlayer(helper, 4);
        com.hogwartsmagic.entity.InferiusEntity inferius = lakeInferius(helper, player);
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            inferius.setTarget(player);
            if (inferius.isAlive()) {
                closest[0] = Math.min(closest[0], flatDistTo(inferius, player));
                helper.assertFalse(!inferius.isInWater() && inferius.getY() > helper.absolutePos(new BlockPos(0, 4, 0)).getY(),
                        "the Inferius climbed the sheer wall");
            }
        });
        helper.runAfterDelay(400, () -> {
            System.out.println("[inferi-reach] sheer closest approach " + closest[0]);
            helper.assertTrue(closest[0] > 2.0D, "closest approach " + closest[0] + " should stay outside melee range");
            helper.succeed();
        });
    }

    /** Fire on the shore holds the Inferi off; the dock cell stays clear and a flammable control block beyond the fire stays unburnt. */
    @GameTest(template = EMPTY9, batch = "inferi_fire", timeoutTicks = 320)
    public static void fireHoldsShoreWithoutBlockingDock(GameTestHelper helper) {
        inferiusArena(helper, false);
        net.minecraft.server.level.ServerPlayer player = islandPlayer(helper, 3);
        BlockPos control = new BlockPos(ARENA_MID, 3, ARENA_MID); // the island centre, two blocks inside the fire ring
        helper.setBlock(control, net.minecraft.world.level.block.Blocks.OAK_PLANKS);
        com.hogwartsmagic.locket.CaveLayout layout = com.hogwartsmagic.locket.CaveLayout.fromMarkers(helper.absolutePos(control),
                java.util.List.of(new com.hogwartsmagic.locket.CaveLayout.Marker("dock_island", 0, helper.absolutePos(control.above()))));
        com.hogwartsmagic.entity.InferiusEntity inferius = lakeInferius(helper, player);
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            // Fire only on the ring-2 shore, one block above its top, topped up so it does not age out.
            for (int x = 0; x <= 8; x++) {
                for (int z = 0; z <= 8; z++) {
                    if (ring(x, z) == 2) {
                        helper.setBlock(new BlockPos(x, 3, z), net.minecraft.world.level.block.Blocks.FIRE);
                    }
                }
            }
            inferius.setTarget(player);
            if (inferius.isAlive()) {
                closest[0] = Math.min(closest[0], flatDistTo(inferius, player));
            }
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(closest[0] > 2.0D, "closest approach " + closest[0] + " should stay outside melee range");
            helper.assertTrue(helper.getLevel().getBlockState(layout.dockIsland()).isAir(),
                    "the layout dockIsland cell stays clear, is " + helper.getLevel().getBlockState(layout.dockIsland()));
            helper.assertTrue(helper.getBlockState(control).is(net.minecraft.world.level.block.Blocks.OAK_PLANKS),
                    "the flammable control block beyond the fire is unburnt, is " + helper.getBlockState(control));
            helper.succeed();
        });
    }

    /** On land the grab drags the victim toward the nearest lake water, slowly. */
    @GameTest(template = EMPTY9, batch = "inferi_land", timeoutTicks = 100)
    public static void landGrabDragsTowardTheLake(GameTestHelper helper) {
        inferiusArena(helper, false);
        net.minecraft.world.entity.animal.Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(ARENA_MID, 3, 2));
        com.hogwartsmagic.entity.InferiusEntity inferius = helper.spawnWithNoFreeWill(com.hogwartsmagic.entity.ModEntities.INFERIUS.get(), new BlockPos(ARENA_MID, 4, 3));
        double startZ = pig.getZ();
        helper.runAfterDelay(2, () -> inferius.doHurtTarget(pig));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(pig.isAlive(), "the pig survived");
            System.out.println("[inferi-land] pig moved " + (pig.getZ() - startZ) + " in z toward the lake");
            helper.assertTrue(pig.getZ() < startZ - 0.2D, "the land grab drags toward the water, z moved " + (pig.getZ() - startZ));
            helper.succeed();
        });
    }

    /** A cave Inferius clings to a moving skiff, hurts the rider, and a hit knocks it off. */
    @GameTest(template = EMPTY9, batch = "inferi_latch", timeoutTicks = 400)
    public static void inferiusLatchesToSkiffAndHitKnocksItOff(GameTestHelper helper) {
        waterArena(helper);
        com.hogwartsmagic.entity.CaveBoatEntity boat = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), crossingLayout(helper, 0));
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth());
        com.hogwartsmagic.entity.InferiusEntity[] inferius = {null};
        float[] health = {0f};
        int[] stage = {0};
        helper.onEachTick(() -> {
            if (stage[0] == 0 && boat.riseTicks() == 0) {
                mount(boat, player);
                health[0] = player.getHealth();
                inferius[0] = helper.spawn(com.hogwartsmagic.entity.ModEntities.INFERIUS.get(), new BlockPos(3, 1, 3));
                inferius[0].bindToCave(helper.absolutePos(new BlockPos(4, 2, 4)));
                stage[0] = 1;
            } else if (stage[0] == 1) {
                if (inferius[0].isLatched()) {
                    helper.assertTrue(boat.latchedCount() == 1, "the skiff counts one latched Inferius");
                    System.out.println("[inferi-latch] latched at tick " + helper.getTick());
                    stage[0] = 2;
                }
            } else if (stage[0] == 2 && player.getHealth() < health[0]) {
                System.out.println("[inferi-latch] rider hurt at tick " + helper.getTick());
                inferius[0].hurt(helper.getLevel().damageSources().generic(), 1.0F);
                stage[0] = 3;
            } else if (stage[0] == 3 && !inferius[0].isLatched()) {
                helper.assertTrue(boat.latchedCount() == 0, "the skiff is free again");
                inferius[0].discard();
                boat.discard();
                helper.succeed();
            }
        });
    }

    /** A latched Inferius never punches the rider off: after 100 ticks aboard the rider is still mounted and took only latch damage. */
    @GameTest(template = EMPTY9, batch = "inferi_latch_hold", timeoutTicks = 400)
    public static void latchedInferiusKeepsTheRiderMounted(GameTestHelper helper) {
        waterArena(helper);
        com.hogwartsmagic.entity.CaveBoatEntity boat = com.hogwartsmagic.entity.CaveBoatEntity.rise(helper.getLevel(), crossingLayout(helper, 0));
        net.minecraft.world.entity.animal.Pig rider = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 3, 4));
        com.hogwartsmagic.entity.InferiusEntity[] inferius = {null};
        int[] stage = {0};
        long[] since = {0L};
        long[] lastHit = {-100L};
        int[] mobHits = {0};
        helper.onEachTick(() -> {
            if (stage[0] == 0 && boat.riseTicks() == 0) {
                // Only a player's click starts the trip; the pig takes the seat the same tick.
                net.minecraft.server.level.ServerPlayer starter = helper.makeMockServerPlayerInLevel();
                mount(boat, starter);
                starter.stopRiding();
                rider.startRiding(boat, true);
                inferius[0] = helper.spawn(com.hogwartsmagic.entity.ModEntities.INFERIUS.get(), new BlockPos(3, 1, 3));
                inferius[0].bindToCave(helper.absolutePos(new BlockPos(4, 2, 4)));
                stage[0] = 1;
            } else if (stage[0] >= 1) {
                if (rider.hurtTime > 8 && helper.getTick() - lastHit[0] > 5L && rider.getLastDamageSource() != null
                        && rider.getLastDamageSource().getEntity() instanceof com.hogwartsmagic.entity.InferiusEntity) {
                    mobHits[0]++; // the arena's barrier wall may also pinch the pig (in-wall damage); only Inferius hits count
                    lastHit[0] = helper.getTick();
                }
                if (stage[0] == 2) {
                    inferius[0].setTarget(rider); // refused while latched, like a target goal re-acquiring the rider
                }
                if (stage[0] == 1 && inferius[0].isLatched()) {
                    stage[0] = 2;
                    since[0] = helper.getTick();
                } else if (stage[0] == 2) {
                    inferius[0].doHurtTarget(rider); // a melee swing is refused while latched
                    helper.assertTrue(inferius[0].isLatched() && boat.getPassengers().contains(rider),
                            "still latched and mounted at tick " + helper.getTick());
                    if (helper.getTick() - since[0] >= 100L) {
                        helper.assertTrue(mobHits[0] >= 1 && mobHits[0] <= 3,
                                "only latch damage from the Inferius (one per 40 ticks), hits " + mobHits[0]);
                        inferius[0].discard();
                        rider.discard();
                        boat.discard();
                        helper.succeed();
                    }
                }
            }
        });
    }

    /** Past the arch (outside the seal plane) a cave Inferius drops its target and will not take another; inside it keeps hunting. */
    @GameTest(template = EMPTY9, batch = "inferi_arch", timeoutTicks = 120)
    public static void inferiusTurnsBackPastTheArch(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 0, z), net.minecraft.world.level.block.Blocks.DEEPSLATE);
            }
        }
        BlockPos basinRel = new BlockPos(4, 1, 1);
        BlockPos archRel = new BlockPos(4, 2, 7);
        helper.setBlock(basinRel, ExampleMod.DESPAIR_BASIN.get());
        helper.setBlock(archRel.east(), ExampleMod.BLOOD_SEAL.get().defaultBlockState().setValue(BloodSealBlock.FACING, Direction.SOUTH));
        BlockPos basinAbs = helper.absolutePos(basinRel);
        ((com.hogwartsmagic.block.entity.DespairBasinBlockEntity) helper.getBlockEntity(basinRel)).setLayoutDirectly(
                new com.hogwartsmagic.locket.CaveLayout(2, basinAbs, java.util.List.of(helper.absolutePos(archRel)), basinAbs, basinAbs, basinAbs,
                        java.util.List.of(), basinAbs.getY() - 1, 20, 12, 12));
        net.minecraft.world.entity.animal.Pig bait = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(4, 1, 4));
        com.hogwartsmagic.entity.InferiusEntity outside = helper.spawnWithNoFreeWill(com.hogwartsmagic.entity.ModEntities.INFERIUS.get(), new BlockPos(1, 1, 8));
        com.hogwartsmagic.entity.InferiusEntity inside = helper.spawnWithNoFreeWill(com.hogwartsmagic.entity.ModEntities.INFERIUS.get(), new BlockPos(7, 1, 6));
        for (com.hogwartsmagic.entity.InferiusEntity e : new com.hogwartsmagic.entity.InferiusEntity[] {outside, inside}) {
            e.bindToCave(basinAbs);
            e.setTarget(bait);
        }
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(outside.getTarget() == null, "past the arch the Inferius dropped its target");
            outside.setTarget(bait);
            helper.assertTrue(outside.getTarget() == null, "and refuses a new one");
            helper.assertTrue(inside.getTarget() == bait, "inside the arch the Inferius keeps hunting");
            outside.discard();
            inside.discard();
            bait.discard();
            helper.succeed();
        });
    }
}
