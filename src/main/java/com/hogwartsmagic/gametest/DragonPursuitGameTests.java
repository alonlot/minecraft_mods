package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.HungarianHorntailEntity.AttackState;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Horntail goes after a broom rider in the air. A villager on a broom stands in for the player: a ridden broom
 * is driven by its rider's client, so here the test flies it by hand and the dragon only sees where it goes.
 */
@GameTestHolder(ExampleMod.MODID)
public final class DragonPursuitGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private DragonPursuitGameTests() {
    }

    private static void loadAround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false, level.getServer());
        BlockPos centre = helper.absolutePos(BlockPos.ZERO);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                level.setChunkForced((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz, true);
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-72, -2, -72), centre.offset(72, 40, 72))) {
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static HungarianHorntailEntity dragon(GameTestHelper helper, Vec3 rel) {
        HungarianHorntailEntity d = new HungarianHorntailEntity(ModEntities.HUNGARIAN_HORNTAIL.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(rel);
        d.setPos(at.x, at.y, at.z);
        d.setPersistenceRequired();
        helper.getLevel().addFreshEntity(d);
        return d;
    }

    private static Villager riderOn(GameTestHelper helper, BroomEntity broom) {
        Villager v = new Villager(EntityType.VILLAGER, helper.getLevel());
        v.setPos(broom.getX(), broom.getY(), broom.getZ());
        v.setNoAi(true);
        v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
        v.setHealth(1000.0F);
        helper.getLevel().addFreshEntity(v);
        helper.assertTrue(v.startRiding(broom, true), "the villager should mount");
        return v;
    }

    private static BroomEntity broom(GameTestHelper helper, Vec3 rel) {
        BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(rel);
        broom.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(broom);
        return broom;
    }

    private static boolean chasing(AttackState s) {
        return s == AttackState.PURSUIT || s == AttackState.AIR_SNAP || s == AttackState.AIR_BREATH;
    }

    private static void cleanUp(Entity... es) {
        for (Entity e : es) {
            e.discard();
        }
    }

    /** A rider hovering out of reach: the dragon takes off and goes after them instead of standing underneath. */
    @GameTest(template = EMPTY, batch = "dragon_pursuit_takeoff", timeoutTicks = 240)
    public static void takesOffAfterABroomRider(GameTestHelper helper) {
        loadAround(helper);
        BroomEntity broom = broom(helper, new Vec3(1.5, 16, 1.5));
        Villager rider = riderOn(helper, broom);
        HungarianHorntailEntity d = dragon(helper, new Vec3(16.5, 2, 1.5));
        d.setTarget(rider);
        double startY = d.getY();
        helper.assertTrue(HungarianHorntailEntity.isAirborneQuarry(rider), "a rider with open air under them is airborne");

        helper.onEachTick(() -> {
            broom.setDeltaMovement(Vec3.ZERO);
            broom.setPos(helper.absoluteVec(new Vec3(1.5, 16, 1.5)));
            if (chasing(d.getAttackState()) && d.getY() > startY + 5.0D) {
                LOGGER.info("DRAGON-PURSUIT PASS takeoff state={} rose={}", d.getAttackState(), d.getY() - startY);
                cleanUp(d, rider, broom);
                helper.succeed();
            }
        });
    }

    /** A rider flying a wide circle: the dragon keeps after them for the whole loop and gets its teeth or fire in. */
    @GameTest(template = EMPTY, batch = "dragon_pursuit_circle", timeoutTicks = 520)
    public static void keepsAfterAFleeingRider(GameTestHelper helper) {
        loadAround(helper);
        Vec3 centre = helper.absoluteVec(new Vec3(1.5, 18, 1.5));
        double radius = 30.0D;
        double speed = 1.2D;
        BroomEntity broom = broom(helper, new Vec3(1.5 + radius, 18, 1.5));
        Villager rider = riderOn(helper, broom);
        HungarianHorntailEntity d = dragon(helper, new Vec3(1.5, 2, 1.5));
        d.setTarget(rider);

        AtomicInteger tick = new AtomicInteger();
        double[] closestLate = {Double.MAX_VALUE};
        int[] chasingTicks = {0};
        helper.onEachTick(() -> {
            int now = tick.incrementAndGet();
            double a = now * speed / radius;
            broom.setDeltaMovement(Vec3.ZERO);
            broom.setPos(centre.x + Math.cos(a) * radius, centre.y, centre.z + Math.sin(a) * radius);
            if (chasing(d.getAttackState())) chasingTicks[0]++;
            if (now > 200) closestLate[0] = Math.min(closestLate[0], d.distanceTo(rider));
            if (now >= 480) {
                float lost = 1000.0F - rider.getHealth();
                LOGGER.info("DRAGON-PURSUIT circle chasingTicks={} closestLate={} riderLost={} state={}",
                        chasingTicks[0], closestLate[0], lost, d.getAttackState());
                cleanUp(d, rider, broom);
                helper.assertTrue(chasingTicks[0] > 300, "it should chase most of the loop, chased " + chasingTicks[0]);
                helper.assertTrue(closestLate[0] < 12.0D, "it should close on the rider, closest " + closestLate[0]);
                helper.assertTrue(lost > 0.0F, "it should have bitten or burnt the rider");
                helper.succeed();
            }
        });
    }

    /** A Firebolt at full boost, flying a wide loop: the dragon still runs it down. */
    @GameTest(template = EMPTY, batch = "dragon_pursuit_firebolt", timeoutTicks = 420)
    public static void runsDownAFireboltAtFullBoost(GameTestHelper helper) {
        loadAround(helper);
        Vec3 centre = helper.absoluteVec(new Vec3(1.5, 18, 1.5));
        double radius = 60.0D;
        double speed = 10.0D;
        BroomEntity broom = broom(helper, new Vec3(1.5 + radius, 18, 1.5));
        broom.setBroomType(2);
        Villager rider = riderOn(helper, broom);
        HungarianHorntailEntity d = dragon(helper, new Vec3(1.5, 2, 1.5));
        d.setTarget(rider);

        AtomicInteger tick = new AtomicInteger();
        double[] closestLate = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            int now = tick.incrementAndGet();
            double a = now * speed / radius;
            broom.setDeltaMovement(Vec3.ZERO);
            broom.setPos(centre.x + Math.cos(a) * radius, centre.y, centre.z + Math.sin(a) * radius);
            if (now > 120) closestLate[0] = Math.min(closestLate[0], d.distanceTo(rider));
            if (now >= 400) {
                LOGGER.info("DRAGON-PURSUIT firebolt closestLate={} speed={} riderLost={} state={}",
                        closestLate[0], d.getDeltaMovement().length(), 1000.0F - rider.getHealth(), d.getAttackState());
                cleanUp(d, rider, broom);
                helper.assertTrue(closestLate[0] < 12.0D, "it should catch the Firebolt, closest " + closestLate[0]);
                helper.succeed();
            }
        });
    }
}
