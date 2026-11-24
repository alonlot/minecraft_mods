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
}
