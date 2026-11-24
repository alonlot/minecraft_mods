package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.portkey.PortkeyData;
import com.hogwartsmagic.portkey.PortkeyJourneys;
import com.hogwartsmagic.portkey.PortkeyLanding;
import com.hogwartsmagic.portkey.PortkeyRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.Set;

/**
 * Portkeys: the group within two blocks goes and the rest stays, the boot rests after a journey, letting go cancels,
 * a built-over bound spot lands you beside it, a buried one fizzles, and a holder who vanishes mid-journey takes the
 * journey with them. Every charge here is started without holding use ({@code begin(..., false)}): a mock player
 * cannot hold a button.
 */
@GameTestHolder(ExampleMod.MODID)
public final class PortkeyGameTests {
    private static final String ROOM = "hogwartsmagic:gametest/empty9";
    /** Charge, the teleport delay, and two ticks of slack. */
    private static final int LANDED = PortkeyRules.CHARGE_TICKS + PortkeyRules.TELEPORT_DELAY_TICKS + 2;

    private PortkeyGameTests() {
    }

    /** A stone floor under the whole room at y = 1, so feet stand at y = 2. */
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(x, 1, z, Blocks.STONE);
            }
        }
    }

    private static ServerPlayer player(GameTestHelper helper, double x, double y, double z) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, Set.of(), 0f, 0f);
        return p;
    }

    /** A Portkey bound to the feet block (x, y, z) of the room, in the holder's hand. */
    private static ItemStack portkey(GameTestHelper helper, ServerPlayer holder, int x, int y, int z) {
        ItemStack stack = new ItemStack(ExampleMod.PORTKEY.get());
        PortkeyData.bind(stack, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(x, y, z)), 0f);
        holder.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static Wolf wolf(GameTestHelper helper, ServerPlayer owner, double x, double z, boolean sitting) {
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(x, 2, z));
        wolf.moveTo(at.x, at.y, at.z, 0f, 0f);
        wolf.setNoAi(true);
        wolf.tame(owner);
        wolf.setOrderedToSit(sitting);
        wolf.setInSittingPose(sitting);
        helper.getLevel().addFreshEntity(wolf);
        return wolf;
    }

    private static boolean near(net.minecraft.world.entity.Entity e, Vec3 to, double within) {
        return e.position().distanceTo(to) <= within;
    }

    /** A crafted Portkey on its third journey lands, is spent, and will not go again (it crumbles in the hand). */
    @GameTest(template = ROOM, timeoutTicks = LANDED + 20)
    public static void lastTripSpendsIt(GameTestHelper helper) {
        floor(helper);
        ServerPlayer holder = player(helper, 1.5, 2, 1.5);
        ItemStack stack = portkey(helper, holder, 6, 2, 6);
        PortkeyData.setTripsUsed(stack, PortkeyRules.TRIPS - 1);
        helper.assertFalse(PortkeyData.spent(stack), "two trips used still leaves one");
        helper.assertTrue(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false), "the last trip should start");
        helper.runAfterDelay(LANDED, () -> {
            helper.assertTrue(near(holder, helper.absoluteVec(new Vec3(6.5, 2, 6.5)), 1.5), "the last trip still lands");
            helper.assertTrue(PortkeyData.spent(stack), "after its third journey the Portkey is spent");
            helper.assertFalse(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false), "a spent Portkey never goes");
            helper.succeed();
        });
    }

    @GameTest(template = ROOM, timeoutTicks = LANDED + 20)
    public static void groupTravelsSittingDogStays(GameTestHelper helper) {
        floor(helper);
        ServerPlayer holder = player(helper, 1.5, 2, 1.5);
        portkey(helper, holder, 6, 2, 6);
        ServerPlayer friend = player(helper, 3.0, 2, 1.5);
        ServerPlayer farAway = player(helper, 1.5, 2, 4.5);
        Wolf standing = wolf(helper, holder, 1.5, 2.5, false);
        Wolf sitting = wolf(helper, holder, 2.5, 1.5, true);
        Cow cow = EntityType.COW.create(helper.getLevel());
        Vec3 cowAt = helper.absoluteVec(new Vec3(0.5, 2, 1.5));
        cow.moveTo(cowAt.x, cowAt.y, cowAt.z, 0f, 0f);
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        Vec3 sittingAt = sitting.position();
        Vec3 farAt = farAway.position();

        helper.assertTrue(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false), "the charge should start");
        helper.runAfterDelay(LANDED, () -> {
            Vec3 bound = helper.absoluteVec(new Vec3(6.5, 2, 6.5));
            helper.assertTrue(near(holder, bound, 1.5), "the holder should land on the bound spot: " + holder.position());
            helper.assertTrue(near(friend, bound, 1.5), "a player 1.5 away travels: " + friend.position());
            helper.assertTrue(near(standing, bound, 1.5), "a standing pet travels: " + standing.position());
            helper.assertTrue(near(sitting, sittingAt, 0.01), "a sitting pet stays");
            helper.assertTrue(near(cow, cowAt, 0.01), "an untamed cow stays");
            helper.assertTrue(near(farAway, farAt, 0.01), "a player 3 away stays");
            helper.succeed();
        });
    }

    @GameTest(template = ROOM, timeoutTicks = PortkeyRules.CHARGE_TICKS + 20)
    public static void cooldownSet(GameTestHelper helper) {
        floor(helper);
        ServerPlayer holder = player(helper, 1.5, 2, 1.5);
        ItemStack stack = portkey(helper, holder, 6, 2, 6);
        helper.assertTrue(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false), "the charge should start");
        helper.runAfterDelay(PortkeyRules.CHARGE_TICKS + 1, () -> {
            long now = helper.getLevel().getGameTime();
            long left = PortkeyRules.cooldownLeft(now, PortkeyData.readyAt(stack));
            helper.assertTrue(left > 11900, "the Portkey should rest about ten minutes, rests " + left);
            helper.assertTrue(PortkeyData.tripsUsed(stack) == 1, "one of its three trips is spent");
            helper.assertFalse(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false),
                    "it should not go again while resting");
            helper.succeed();
        });
    }

    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void chargeCancelsWhenUseStops(GameTestHelper helper) {
        floor(helper);
        ServerPlayer holder = player(helper, 1.5, 2, 1.5);
        ItemStack stack = portkey(helper, holder, 6, 2, 6);
        // requireUse, but the mock player is not holding use: the very next tick cancels it.
        helper.assertTrue(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, true), "the charge should start");
        helper.runAfterDelay(1, () -> {
            helper.assertFalse(PortkeyJourneys.hasJourney(holder.getUUID()), "letting go cancels the charge");
            helper.assertTrue(PortkeyData.readyAt(stack) == 0L, "a cancelled charge costs no cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = ROOM, timeoutTicks = 30)
    public static void hitCancelsCharge(GameTestHelper helper) {
        floor(helper);
        ServerPlayer holder = player(helper, 1.5, 2, 1.5);
        ItemStack stack = portkey(helper, holder, 6, 2, 6);
        helper.assertTrue(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false), "the charge should start");
        // fellOutOfWorld: a mock player is still spawn-invulnerable to ordinary blows.
        helper.runAfterDelay(10, () -> holder.hurt(helper.getLevel().damageSources().fellOutOfWorld(), 1.0f));
        helper.runAfterDelay(11, () -> {
            helper.assertFalse(PortkeyJourneys.hasJourney(holder.getUUID()), "a blow breaks the charge");
            helper.assertTrue(PortkeyData.readyAt(stack) == 0L, "a broken charge costs no cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = ROOM, timeoutTicks = LANDED + 20)
    public static void obstructedBoundSpotLandsBeside(GameTestHelper helper) {
        floor(helper);
        helper.setBlock(6, 2, 6, Blocks.STONE);
        helper.setBlock(6, 3, 6, Blocks.STONE);
        ServerPlayer holder = player(helper, 1.5, 2, 1.5);
        portkey(helper, holder, 6, 2, 6);
        helper.assertTrue(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false), "the charge should start");
        helper.runAfterDelay(LANDED, () -> {
            Vec3 bound = helper.absoluteVec(new Vec3(6.5, 2, 6.5));
            double off = Math.hypot(holder.getX() - bound.x, holder.getZ() - bound.z);
            helper.assertTrue(off > 0.5 && off <= 4.0 * Math.sqrt(2.0) + 0.01,
                    "the holder should land beside the walled-up spot, landed " + off + " away");
            ServerLevel level = helper.getLevel();
            helper.assertTrue(PortkeyLanding.isSafeFeet(level, holder.blockPosition()),
                    "and on a safe spot: " + holder.blockPosition());
            helper.succeed();
        });
    }

    @GameTest(template = ROOM, timeoutTicks = LANDED + 20)
    public static void buriedBoundSpotFizzles(GameTestHelper helper) {
        // A 9x9x9 block of stone round the bound spot (4, 5, 4): nowhere within four blocks to stand.
        for (int x = 0; x < 9; x++) {
            for (int y = 1; y < 10; y++) {
                for (int z = 0; z < 9; z++) {
                    helper.setBlock(x, y, z, Blocks.STONE);
                }
            }
        }
        ServerPlayer holder = player(helper, 4.5, 10, 4.5);
        ItemStack stack = portkey(helper, holder, 4, 5, 4);
        Vec3 start = holder.position();
        helper.assertTrue(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false), "the charge should start");
        helper.runAfterDelay(PortkeyRules.CHARGE_TICKS + 2, () -> {
            helper.assertTrue(near(holder, start, 0.01), "the holder should not have moved");
            helper.assertFalse(PortkeyJourneys.hasJourney(holder.getUUID()), "the journey should be over");
            helper.assertTrue(PortkeyData.readyAt(stack) == 0L, "a fizzle costs no cooldown");
            for (int x = 0; x < 9; x++) {
                for (int y = 4; y < 10; y++) {
                    for (int z = 0; z < 9; z++) {
                        helper.setBlock(x, y, z, Blocks.AIR); // leave the room as it was above its own height
                    }
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = ROOM, timeoutTicks = PortkeyRules.CHARGE_TICKS + 20)
    public static void holderRemovedMidTransitDropsJourney(GameTestHelper helper) {
        floor(helper);
        ServerPlayer holder = player(helper, 1.5, 2, 1.5);
        portkey(helper, holder, 6, 2, 6);
        // The holder is discarded two ticks after the hook, before the teleport delay is up: still in the tunnel.
        helper.assertTrue(PortkeyJourneys.begin(holder, InteractionHand.MAIN_HAND, false), "the charge should start");
        helper.runAfterDelay(PortkeyRules.CHARGE_TICKS + 2, () -> holder.discard());
        helper.runAfterDelay(PortkeyRules.CHARGE_TICKS + 3, () -> {
            helper.assertFalse(PortkeyJourneys.hasJourney(holder.getUUID()), "the journey should be dropped");
            helper.assertFalse(PortkeyJourneys.isTravelling(holder), "nobody is left in its tunnel");
            helper.succeed();
        });
    }
}
