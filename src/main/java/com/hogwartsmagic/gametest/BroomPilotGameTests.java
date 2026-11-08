package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.WizardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Death Eater flying a broom, in the real engine with real collisions. The rider's AI is switched off where a test
 * only measures the flight, so no spell can change the scene; the broom pilot reads the target and flies regardless.
 */
@GameTestHolder(ExampleMod.MODID)
public final class BroomPilotGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final double SPELL_SPEED = 1.5D;

    private BroomPilotGameTests() {
    }

    /**
     * Entities only tick in forced chunks here, and a flight leaves the test's own chunk within seconds. The test
     * framework also rings every test with invisible barrier blocks, which a broom would fly straight into.
     */
    private static void loadAround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos centre = helper.absolutePos(BlockPos.ZERO);
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                level.setChunkForced((centre.getX() >> 4) + dx, (centre.getZ() >> 4) + dz, true);
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-48, -2, -48), centre.offset(48, 40, 48))) {
            if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static BroomEntity broom(GameTestHelper helper, Vec3 rel) {
        BroomEntity broom = new BroomEntity(ModEntities.BROOM.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(rel);
        broom.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(broom);
        return broom;
    }

    private static WizardEntity rider(GameTestHelper helper, BroomEntity broom, boolean ai) {
        WizardEntity wizard = new WizardEntity(ModEntities.WIZARD.get(), helper.getLevel());
        wizard.setPos(broom.getX(), broom.getY(), broom.getZ());
        wizard.setNoAi(!ai);
        helper.getLevel().addFreshEntity(wizard);
        helper.assertTrue(wizard.startRiding(broom, true), "the wizard should mount");
        return wizard;
    }

    private static Villager mark(GameTestHelper helper, Vec3 rel) {
        Villager villager = new Villager(EntityType.VILLAGER, helper.getLevel());
        Vec3 at = helper.absoluteVec(rel);
        villager.setPos(at.x, at.y, at.z);
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static double flatDistance(Vec3 a, Vec3 b) {
        return Math.sqrt(Mth.square(a.x - b.x) + Mth.square(a.z - b.z));
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * (180.0D / Math.PI)) - 90.0F;
    }

    /** Batches share nearly the same ground, and flying riders outlive their test's own bounding box. */
    private static void removeFliers(GameTestHelper helper, net.minecraft.world.entity.Entity... fliers) {
        for (net.minecraft.world.entity.Entity flier : fliers) {
            flier.discard();
        }
    }

    private static void finish(GameTestHelper helper, String name, AtomicReference<String> violation, String stats,
                               net.minecraft.world.entity.Entity... fliers) {
        LOGGER.info("BROOM-PILOT {} {} violation={}", name, stats, violation.get());
        removeFliers(helper, fliers);
        helper.assertTrue(violation.get() == null, String.valueOf(violation.get()));
        helper.succeed();
    }

    /** It closes in from far away, then holds a readable fight: in range, slower than its spells, nose on the mark. */
    @GameTest(template = EMPTY, batch = "broom_pilot_orbit", timeoutTicks = 520)
    public static void holdsTheFightAtSpellRange(GameTestHelper helper) {
        loadAround(helper);
        Villager target = mark(helper, new Vec3(1.5, 2, 1.5));
        BroomEntity broom = broom(helper, new Vec3(31.5, 8, 1.5));
        WizardEntity wizard = rider(helper, broom, false);
        wizard.setTarget(target);

        AtomicReference<String> violation = new AtomicReference<>();
        AtomicInteger tick = new AtomicInteger();
        AtomicReference<Vec3> last = new AtomicReference<>(broom.position());
        double[] seen = {Double.MAX_VALUE, 0.0D, 0.0D, 0.0D};
        helper.onEachTick(() -> {
            int now = tick.incrementAndGet();
            Vec3 at = broom.position();
            double moved = at.distanceTo(last.getAndSet(at));
            if (now <= 240) {
                return; // the approach
            }
            double range = flatDistance(at, target.position());
            double height = at.y - target.getY();
            float off = Math.abs(Mth.wrapDegrees(broom.getYRot() - yawTo(at, target.position())));
            float seat = Math.abs(Mth.wrapDegrees(wizard.yBodyRot - broom.getYRot()));
            seen[0] = Math.min(seen[0], range);
            seen[1] = Math.max(seen[1], range);
            seen[2] = Math.max(seen[2], moved);
            seen[3] = Math.max(seen[3], off);
            if (violation.get() != null) {
                return;
            }
            if (moved >= SPELL_SPEED * 0.6D) {
                violation.set("tick " + now + ": moved " + moved + " a tick, too near its own spell speed");
            } else if (range < 7.0D || range > 16.0D) {
                violation.set("tick " + now + ": range " + range + " is outside the fight");
            } else if (height < 3.0D || height > 9.0D) {
                violation.set("tick " + now + ": hovering " + height + " above the mark");
            } else if (off > 35.0F) {
                violation.set("tick " + now + ": the broom points " + off + " degrees off the mark");
            } else if (seat > 5.0F) {
                violation.set("tick " + now + ": the rider sits " + seat + " degrees across the broom");
            }
        });
        helper.runAfterDelay(480, () -> finish(helper, "orbit", violation,
                "range=" + seen[0] + ".." + seen[1] + " maxMove=" + seen[2] + " maxOff=" + seen[3],
                wizard, broom, target));
    }

    /** With no target it wanders near where it started, at a fixed height over the ground, at a broom's cruise. */
    @GameTest(template = EMPTY, batch = "broom_pilot_patrol", timeoutTicks = 760)
    public static void patrolsNearHomeOverTheGround(GameTestHelper helper) {
        loadAround(helper);
        BroomEntity broom = broom(helper, new Vec3(1.5, 12, 1.5));
        WizardEntity wizard = rider(helper, broom, false);
        Vec3 home = broom.position();
        double ground = helper.absoluteVec(new Vec3(0, 1, 0)).y;

        AtomicReference<String> violation = new AtomicReference<>();
        AtomicInteger tick = new AtomicInteger();
        AtomicReference<Vec3> last = new AtomicReference<>(broom.position());
        double[] seen = {0.0D, 0.0D, Double.MAX_VALUE, 0.0D};
        helper.onEachTick(() -> {
            int now = tick.incrementAndGet();
            Vec3 at = broom.position();
            double moved = at.distanceTo(last.getAndSet(at));
            if (now <= 120) {
                return;
            }
            double away = flatDistance(at, home);
            double height = at.y - ground;
            seen[0] = Math.max(seen[0], away);
            seen[1] = Math.max(seen[1], moved);
            seen[2] = Math.min(seen[2], height);
            seen[3] = Math.max(seen[3], height);
            if (violation.get() != null) {
                return;
            }
            if (moved > 0.75D) {
                violation.set("tick " + now + ": cruising at " + moved + " a tick");
            } else if (away > 50.0D) {
                violation.set("tick " + now + ": wandered " + away + " blocks from home");
            } else if (height < 6.0D || height > 16.0D) {
                violation.set("tick " + now + ": patrolling " + height + " above the ground");
            }
        });
        helper.runAfterDelay(720, () -> finish(helper, "patrol", violation,
                "away=" + seen[0] + " maxMove=" + seen[1] + " height=" + seen[2] + ".." + seen[3],
                wizard, broom));
    }

    /** A wall too wide to go round, between it and the mark: it climbs over and carries on. */
    @GameTest(template = EMPTY, batch = "broom_pilot_ridge", timeoutTicks = 420)
    public static void climbsOverARidge(GameTestHelper helper) {
        loadAround(helper);
        for (int z = -24; z <= 26; z++) {
            for (int y = 1; y <= 14; y++) {
                helper.getLevel().setBlock(helper.absolutePos(new BlockPos(14, y, z)), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        Villager target = mark(helper, new Vec3(32.5, 2, 1.5));
        BroomEntity broom = broom(helper, new Vec3(1.5, 4, 1.5));
        WizardEntity wizard = rider(helper, broom, false);
        wizard.setTarget(target);

        double wallX = helper.absoluteVec(new Vec3(15, 0, 0)).x;
        AtomicInteger crossedAt = new AtomicInteger(-1);
        AtomicInteger tick = new AtomicInteger();
        helper.onEachTick(() -> {
            int now = tick.incrementAndGet();
            if (crossedAt.get() < 0 && broom.getX() > wallX) {
                crossedAt.set(now);
            }
        });
        helper.runAfterDelay(380, () -> {
            LOGGER.info("BROOM-PILOT ridge crossedAt={} x={} y={}", crossedAt.get(), broom.getX(), broom.getY());
            for (int z = -24; z <= 26; z++) {
                for (int y = 1; y <= 14; y++) {
                    helper.getLevel().setBlock(helper.absolutePos(new BlockPos(14, y, z)), Blocks.AIR.defaultBlockState(), 2);
                }
            }
            removeFliers(helper, wizard, broom, target);
            helper.assertTrue(crossedAt.get() > 0, "the broom never crossed the ridge");
            helper.succeed();
        });
    }

    /** Disarmed, it flies down to its wand and takes it back: the one job a disarmed Death Eater has. */
    @GameTest(template = EMPTY, batch = "broom_pilot_wand", timeoutTicks = 500)
    public static void disarmedRiderFetchesItsWand(GameTestHelper helper) {
        loadAround(helper);
        BroomEntity broom = broom(helper, new Vec3(1.5, 9, 1.5));
        WizardEntity wizard = rider(helper, broom, true);
        wizard.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        wizard.setDisarmed(true);
        Vec3 spot = helper.absoluteVec(new Vec3(8.5, 2.2, 6.5));
        ItemEntity wand = new ItemEntity(helper.getLevel(), spot.x, spot.y, spot.z,
                new ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
        wand.setNoPickUpDelay();
        wand.setUnlimitedLifetime();
        helper.getLevel().addFreshEntity(wand);
        helper.succeedWhen(() -> {
            helper.assertTrue(!wizard.isDisarmed(), "the rider should have taken its wand back");
            LOGGER.info("BROOM-PILOT wand recovered at y={}", broom.getY());
            removeFliers(helper, wizard, broom);
        });
    }
}
