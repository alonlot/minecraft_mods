package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.portrait.PortraitEntity;
import com.hogwartsmagic.portrait.PortraitKind;
import com.hogwartsmagic.portrait.PortraitPasswords;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * The Fat Lady as a password door: a slip of paper and a chat line record the password, saying it in front of her
 * swings her open and takes out the two-block wall behind her canvas (down to the passage), she shuts and puts the
 * wall back once nobody stands in it, and from inside the passage she lets you out without a password.
 *
 * <p>Layout (test-local): floor y = 1, wall z = 4..5 for x 2..6 and y 2..6, the passage behind at z = 6..7. She hangs
 * facing north from cell (4, 3, 3): canvas x 3..4, y 2..5.
 */
@GameTestHolder(ExampleMod.MODID)
public final class PortraitDoorGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty9";

    private PortraitDoorGameTests() {
    }

    private static PortraitEntity build(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                level.setBlock(helper.absolutePos(new BlockPos(x, 1, z)), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 2; y <= 7; y++) {
                    boolean wall = (z == 4 || z == 5) && x >= 2 && x <= 6 && y <= 6;
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)),
                            wall ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        PortraitEntity lady = new PortraitEntity(level, PortraitKind.FAT_LADY, helper.absolutePos(new BlockPos(4, 3, 3)),
                Direction.NORTH);
        helper.assertTrue(lady.survives(), "the Fat Lady does not fit on the test wall");
        level.addFreshEntity(lady);
        return lady;
    }

    private static boolean wallOpen(GameTestHelper helper) {
        for (int z = 4; z <= 5; z++) {
            for (int x = 3; x <= 4; x++) {
                for (int y = 2; y <= 5; y++) {
                    if (!helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z))).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean wallShut(GameTestHelper helper) {
        for (int z = 4; z <= 5; z++) {
            for (int x = 3; x <= 4; x++) {
                for (int y = 2; y <= 5; y++) {
                    if (!helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, y, z)))
                            .is(Blocks.STONE_BRICKS)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void stand(GameTestHelper helper, ServerPlayer p, double x, double y, double z, float yaw) {
        BlockPos o = helper.absolutePos(BlockPos.ZERO);
        p.moveTo(o.getX() + x, o.getY() + y, o.getZ() + z, yaw, 0f);
    }

    @GameTest(template = EMPTY, batch = "portrait_door", timeoutTicks = 600)
    public static void passwordOpensAndShuts(GameTestHelper helper) {
        PortraitEntity lady = build(helper);
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL); // the mock is creative, where the paper is not used up
        stand(helper, p, 4.0, 2.0, 1.5, 180f); // in front of her, facing her (north)

        helper.assertFalse(PortraitPasswords.heardChat(p, "Caput Draconis"),
                "with no password set she took an ordinary chat line");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER, 2));
        lady.interact(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(p.getMainHandItem().getCount() == 1, "she did not take the slip of paper");
        helper.assertTrue(PortraitPasswords.heardChat(p, "Caput Draconis."), "she did not record the password");
        helper.assertFalse(lady.isOpen(), "recording the password opened her");
        helper.assertFalse(PortraitPasswords.heardChat(p, "hello there"), "she swallowed an ordinary chat line");
        helper.assertTrue(wallShut(helper), "the wall changed before the password was said");

        helper.assertTrue(PortraitPasswords.heardChat(p, "caput  draconis!"), "the password was not taken");
        helper.assertTrue(lady.isOpen(), "the password did not open her");
        helper.assertTrue(wallOpen(helper), "the wall behind her did not open down to the passage");
        helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(2, 3, 4)))
                .is(Blocks.STONE_BRICKS), "she took out wall beside her canvas");
        CompoundTag saved = new CompoundTag();
        lady.saveWithoutId(saved);
        helper.assertTrue(saved.getCompound("door").getList("carved", 10).size() == 16,
                "the 16 blocks she took out are not in her save data");

        // somebody standing in the doorway keeps her open past the 5 s
        stand(helper, p, 4.0, 2.0, 4.5, 0f);
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(lady.isOpen(), "she shut on somebody standing in the doorway");
            stand(helper, p, 4.0, 2.0, 1.5, 180f);
        });
        helper.runAfterDelay(170, () -> {
            helper.assertFalse(lady.isOpen(), "she did not shut once the doorway was clear");
            helper.assertTrue(wallShut(helper), "the wall did not go back");
            // asked "Password?", a wrong answer is turned away (and kept out of chat)
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            lady.interact(p, InteractionHand.MAIN_HAND);
            helper.assertTrue(PortraitPasswords.heardChat(p, "Pig snout"), "a wrong answer to Password? reached chat");
            helper.assertFalse(lady.isOpen(), "a wrong password opened her");
            // from inside the passage, facing out: she lets you out
            stand(helper, p, 4.0, 2.0, 6.5, 180f);
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(lady.isOpen(), "she did not open for somebody inside the passage");
            stand(helper, p, 4.0, 2.0, 1.0, 180f);
        });
        helper.runAfterDelay(340, () -> {
            helper.assertFalse(lady.isOpen(), "she did not shut after letting somebody out");
            helper.assertTrue(wallShut(helper), "the wall did not go back after letting somebody out");
            lady.discard(); // nothing left behind for the next batch
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "portrait_door_broken", timeoutTicks = 100)
    public static void brokenWhileOpenPutsTheWallBack(GameTestHelper helper) {
        PortraitEntity lady = build(helper);
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        stand(helper, p, 4.0, 2.0, 1.5, 180f);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
        lady.interact(p, InteractionHand.MAIN_HAND);
        PortraitPasswords.heardChat(p, "Fortuna Major");
        PortraitPasswords.heardChat(p, "fortuna major");
        helper.assertTrue(lady.isOpen() && wallOpen(helper), "she did not open");
        lady.kill();
        helper.assertTrue(wallShut(helper), "breaking her while open left a hole in the wall");
        helper.succeed();
    }
}
