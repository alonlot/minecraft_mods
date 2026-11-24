package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.block.FlooFireplaceBlock;
import com.hogwartsmagic.block.FlooFireplacePartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * The walk-in Floo fireplace: a hearth with eight frame parts around it (three wide, three tall).
 * The frame builds all at once, breaking any part takes the whole fireplace and drops one item,
 * a player-sized box fits standing on the hearth, and a one-block fireplace from before grows its frame.
 *
 * <p>Layout (test-local): stone floor at y = 1, the hearth at (4, 2, 4) facing east, so the frame runs along z.
 */
@GameTestHolder(ExampleMod.MODID)
public final class FlooFireplaceGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty9";
    private static final BlockPos HEARTH = new BlockPos(4, 2, 4);
    private static final Direction FACING = Direction.EAST;

    private FlooFireplaceGameTests() {
    }

    private static BlockPos floorAndHearth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 2; y <= 6; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        BlockPos hearth = helper.absolutePos(HEARTH);
        level.setBlock(hearth, ExampleMod.FLOO_FIREPLACE.get().defaultBlockState().setValue(FlooFireplaceBlock.FACING, FACING), 3);
        return hearth;
    }

    private static int partCount(GameTestHelper helper, BlockPos hearth) {
        int parts = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                BlockState state = helper.getLevel().getBlockState(FlooFireplaceBlock.partPos(hearth, FACING, column, row));
                if (state.getBlock() instanceof FlooFireplacePartBlock
                        && FlooFireplacePartBlock.hearthOf(state, FlooFireplaceBlock.partPos(hearth, FACING, column, row)).equals(hearth)) {
                    parts++;
                }
            }
        }
        return parts;
    }

    @GameTest(template = EMPTY)
    public static void frameBuildsAndFitsAStandingPlayer(GameTestHelper helper) {
        BlockPos hearth = floorAndHearth(helper);
        helper.assertTrue(FlooFireplaceBlock.completeFrame(helper.getLevel(), hearth, FACING), "frame did not build in open air");
        helper.assertTrue(partCount(helper, hearth) == 8, "expected 8 parts pointing at the hearth, got " + partCount(helper, hearth));
        helper.assertTrue(FlooFireplaceBlock.isComplete(helper.getLevel(), hearth, FACING), "isComplete disagrees with the parts");
        // parts with this facing run along the facing's clockwise axis (south for east)
        helper.assertBlockPresent(ExampleMod.FLOO_FIREPLACE_PART.get(), HEARTH.south());
        helper.assertBlockPresent(ExampleMod.FLOO_FIREPLACE_PART.get(), HEARTH.north().above(2));

        Vec3 spot = FlooFireplaceBlock.standingSpot(hearth);
        AABB player = EntityType.PLAYER.getDimensions().makeBoundingBox(spot);
        helper.assertTrue(helper.getLevel().noCollision(player), "a standing player does not fit on the hearth");
        helper.assertFalse(helper.getLevel().noCollision(player.move(0, -0.01, 0)), "the hearth floor is not under the player");
        helper.assertFalse(helper.getLevel().noCollision(player.move(0, 0.3, 0)), "the lintel is not over the player's head");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void breakingAPartDropsOneFireplace(GameTestHelper helper) {
        BlockPos hearth = floorAndHearth(helper);
        FlooFireplaceBlock.completeFrame(helper.getLevel(), hearth, FACING);
        helper.getLevel().destroyBlock(FlooFireplaceBlock.partPos(hearth, FACING, 2, 2), true);
        helper.assertBlockNotPresent(ExampleMod.FLOO_FIREPLACE.get(), HEARTH);
        helper.assertTrue(partCount(helper, hearth) == 0, "parts left behind: " + partCount(helper, hearth));
        int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(hearth).inflate(4),
                        e -> e.getItem().is(ExampleMod.FLOO_FIREPLACE_ITEM.get()))
                .stream().mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(dropped == 1, "expected exactly one fireplace dropped, got " + dropped);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void blockedFrameIsNotHalfBuilt(GameTestHelper helper) {
        BlockPos hearth = floorAndHearth(helper);
        helper.getLevel().setBlock(FlooFireplaceBlock.partPos(hearth, FACING, 0, 2), Blocks.STONE.defaultBlockState(), 3);
        helper.assertFalse(FlooFireplaceBlock.completeFrame(helper.getLevel(), hearth, FACING), "built through stone");
        helper.assertTrue(partCount(helper, hearth) == 0, "half-built frame: " + partCount(helper, hearth));
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void oldOneBlockFireplaceGrowsItsFrame(GameTestHelper helper) {
        BlockPos hearth = floorAndHearth(helper);
        helper.succeedWhen(() -> helper.assertTrue(partCount(helper, hearth) == 8, "frame not grown yet"));
    }
}
