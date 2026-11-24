package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.GnomeEntity;
import com.hogwartsmagic.entity.GnomeRules;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Garden gnomes: they uproot ripe crops and dig up lawns, and can be grabbed, whirled and flung. Ticked by
 * hand, as the Niffler's tests are: the test server stops ticking entities partway through a long batch.
 */
@GameTestHolder(ExampleMod.MODID)
public final class GnomeGameTests {
    private static final String EMPTY9 = "hogwartsmagic:gametest/empty9";

    private GnomeGameTests() {
    }

    private static void dirtPad(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(x, 1, z, Blocks.DIRT);
            }
        }
    }

    private static void stonePad(GameTestHelper h) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                h.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static BlockState carrots(int age) {
        return Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, age);
    }

    /** One ripe and one green carrot, each in its own farmland. */
    private static void field(GameTestHelper h) {
        dirtPad(h);
        h.setBlock(5, 1, 4, Blocks.FARMLAND);
        h.setBlock(new BlockPos(5, 2, 4), carrots(7));
        h.setBlock(5, 1, 6, Blocks.FARMLAND);
        h.setBlock(new BlockPos(5, 2, 6), carrots(3));
    }

    private static GnomeEntity gnome(GameTestHelper h, double x, double y, double z) {
        return h.spawn(ModEntities.GNOME.get(), new Vec3(x, y, z));
    }

    private static ServerPlayer survivor(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    /** A gnome that is carried is a passenger and is ticked as one; every other gnome ticks normally. */
    private static void step(GnomeEntity g) {
        if (g.isPassenger()) {
            g.rideTick();
        } else {
            g.tick();
        }
    }

    private static boolean holdsPlaceholder(ServerPlayer p) {
        for (ItemStack s : p.getInventory().items) {
            if (s.is(ExampleMod.HELD_GNOME.get())) {
                return true;
            }
        }
        return p.getOffhandItem().is(ExampleMod.HELD_GNOME.get());
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void aGnomeUprootsAndEatsARipeCarrot(GameTestHelper h) {
        field(h);
        GnomeEntity g = gnome(h, 2.5, 2, 4.5);
        for (int i = 0; i < 400 && g.cropsEaten() < 1; i++) {
            g.tick();
        }
        h.assertTrue(g.cropsEaten() == 1, "it should have eaten the ripe carrot, ate " + g.cropsEaten());
        h.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, 4));
        h.assertBlockPresent(Blocks.FARMLAND, new BlockPos(5, 1, 4));
        h.assertBlockPresent(Blocks.CARROTS, new BlockPos(5, 2, 6));
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20, batch = "gnome_griefing")
    public static void noGriefingMeansNoCropsAndNoHoles(GameTestHelper h) {
        field(h);
        h.setBlock(2, 1, 2, Blocks.GRASS_BLOCK);
        GnomeEntity g = gnome(h, 2.5, 2, 4.5);
        GameRules.BooleanValue rule = h.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean saved = rule.get();
        try {
            rule.set(false, h.getLevel().getServer());
            h.assertTrue(g.findRipeCrop() == null, "with mob griefing off no crop is a target");
            h.assertTrue(g.digAt(h.absolutePos(new BlockPos(2, 1, 2)), 0.0F) == GnomeRules.DIG_NONE,
                    "with mob griefing off nothing is dug");
            h.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(2, 1, 2));
        } finally {
            rule.set(saved, h.getLevel().getServer());
        }
        h.assertTrue(h.absolutePos(new BlockPos(5, 2, 4)).equals(g.findRipeCrop()),
                "with mob griefing back on the ripe carrot is the target");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void digTurnsGrassToCoarseDirtAndOnlyPitsOverSolidGround(GameTestHelper h) {
        dirtPad(h);
        GnomeEntity g = gnome(h, 0.5, 2, 0.5);
        g.setNoAi(true);
        h.setBlock(2, 1, 2, Blocks.GRASS_BLOCK);
        h.assertTrue(g.digAt(h.absolutePos(new BlockPos(2, 1, 2)), 0.99F) == GnomeRules.DIG_COARSE, "grass scratches");
        h.assertBlockPresent(Blocks.COARSE_DIRT, new BlockPos(2, 1, 2));

        h.setBlock(4, 0, 4, Blocks.STONE);
        h.setBlock(4, 1, 4, Blocks.COARSE_DIRT);
        h.assertTrue(g.digAt(h.absolutePos(new BlockPos(4, 1, 4)), 0.0F) == GnomeRules.DIG_PIT, "a pit over stone");
        h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 1, 4));

        h.setBlock(6, 0, 6, Blocks.AIR);
        h.setBlock(6, 1, 6, Blocks.COARSE_DIRT);
        h.assertTrue(g.digAt(h.absolutePos(new BlockPos(6, 1, 6)), 0.0F) == GnomeRules.DIG_NONE,
                "never a pit over a hollow");
        h.assertBlockPresent(Blocks.COARSE_DIRT, new BlockPos(6, 1, 6));

        h.setBlock(6, 1, 2, Blocks.FARMLAND);
        h.setBlock(2, 1, 6, Blocks.DIRT_PATH);
        h.assertTrue(g.digAt(h.absolutePos(new BlockPos(6, 1, 2)), 0.0F) == GnomeRules.DIG_NONE, "farmland is left");
        h.assertTrue(g.digAt(h.absolutePos(new BlockPos(2, 1, 6)), 0.0F) == GnomeRules.DIG_NONE, "paths are left");
        h.assertBlockPresent(Blocks.FARMLAND, new BlockPos(6, 1, 2));
        h.assertBlockPresent(Blocks.DIRT_PATH, new BlockPos(2, 1, 6));
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void anUnspunGnomeBitesAndWrigglesFree(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        p.moveTo(h.absoluteVec(new Vec3(1.5, 2, 4.5)));
        GnomeEntity g = gnome(h, 3, 2, 4);
        h.assertTrue(g.mobInteract(p, InteractionHand.MAIN_HAND).consumesAction(), "a click should grab it");
        h.assertTrue(g.getVehicle() == p, "it should hang from the player");
        h.assertTrue(p.getMainHandItem().is(ExampleMod.HELD_GNOME.get()), "the placeholder is in hand");
        h.assertTrue(g.state() == GnomeEntity.State.CARRIED, "it should be carried");
        for (int i = 0; i < 30; i++) {
            step(g);
        }
        h.assertTrue(g.getVehicle() == p, "not bitten yet");
        h.assertTrue(p.getHealth() == 20.0F, "still unhurt");
        for (int i = 0; i < 40; i++) {
            step(g);
        }
        h.assertTrue(p.getHealth() == 18.0F, "one heart bitten, health " + p.getHealth());
        h.assertFalse(g.isPassenger(), "it wriggled free");
        h.assertFalse(holdsPlaceholder(p), "the placeholder is gone");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void aSecondGrabIsRefused(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p1 = survivor(h);
        ServerPlayer p2 = survivor(h);
        p1.moveTo(h.absoluteVec(new Vec3(1.5, 2, 4.5)));
        p2.moveTo(h.absoluteVec(new Vec3(7.5, 2, 4.5)));
        GnomeEntity g1 = gnome(h, 3, 2, 4);
        GnomeEntity g2 = gnome(h, 5, 2, 6);
        g1.mobInteract(p1, InteractionHand.MAIN_HAND);
        h.assertTrue(g1.getVehicle() == p1, "player one has the first gnome");
        g1.mobInteract(p2, InteractionHand.MAIN_HAND);
        h.assertTrue(g1.getVehicle() == p1, "a held gnome cannot be taken");
        h.assertTrue(p2.getMainHandItem().isEmpty(), "player two's hand stays empty");
        g2.mobInteract(p1, InteractionHand.MAIN_HAND);
        h.assertFalse(g2.isPassenger(), "a second gnome cannot be grabbed");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void switchingTheHotbarSlotLetsGo(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        p.moveTo(h.absoluteVec(new Vec3(1.5, 2, 4.5)));
        GnomeEntity g = gnome(h, 3, 2, 4);
        h.assertTrue(g.grab(p), "the grab works");
        p.getInventory().selected = 1;
        step(g);
        h.assertFalse(g.isPassenger(), "it is let go");
        h.assertFalse(holdsPlaceholder(p), "no placeholder survives");
        h.assertTrue(p.getHealth() == 20.0F, "no bite for the player");
        h.assertTrue(g.state() == GnomeEntity.State.NONE, "it is free");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void aLobLandsDazedNearbyAndStays(GameTestHelper h) {
        stonePad(h);
        ServerPlayer p = survivor(h);
        Vec3 at = h.absoluteVec(new Vec3(1.5, 2, 4.5));
        p.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
        GnomeEntity g = gnome(h, 3, 2, 4);
        h.assertTrue(g.grab(p), "the grab works");
        g.throwFrom(p, 0);
        for (int i = 0; i < 100 && g.state() == GnomeEntity.State.THROWN; i++) {
            g.tick();
        }
        h.assertTrue(g.state() == GnomeEntity.State.DAZED, "it lands dazed, state " + g.state());
        h.assertFalse(g.isLeaving(), "a lob does not send it away");
        h.assertTrue(g.lastThrowDistance() < GnomeRules.GONE_DISTANCE, "a lob is short");
        h.assertTrue(g.getHealth() == g.getMaxHealth(), "landing does not hurt it");
        for (int i = 0; i < GnomeEntity.DAZE_TICKS + 5; i++) {
            g.tick();
        }
        h.assertTrue(g.state() == GnomeEntity.State.NONE && !g.isRemoved(), "it comes round and stays");
        h.succeed();
    }

    @GameTest(template = EMPTY9, timeoutTicks = 20)
    public static void aGnomeThrownTwelveBlocksLeavesForGood(GameTestHelper h) {
        stonePad(h);
        GnomeEntity g1 = gnome(h, 2, 2, 2);
        GnomeEntity g2 = gnome(h, 6, 2, 6);
        g1.landAfterThrow(13.0D);
        h.assertTrue(g1.state() == GnomeEntity.State.DAZED && g1.isLeaving(), "far thrown: dazed and leaving");
        for (int i = 0; i < GnomeEntity.DAZE_TICKS + GnomeEntity.LEAVE_MAX_TICKS + 20 && !g1.isRemoved(); i++) {
            g1.tick();
        }
        h.assertTrue(g1.isRemoved(), "it is gone");
        g2.landAfterThrow(11.0D);
        h.assertFalse(g2.isLeaving(), "eleven blocks is not far enough");
        h.succeed();
    }
}
