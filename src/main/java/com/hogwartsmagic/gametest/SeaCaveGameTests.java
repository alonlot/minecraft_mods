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
}
