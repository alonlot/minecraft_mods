package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * The wild Horntail breath is drawn on the client now (the flame particles left the server), so this pins what stayed
 * on the server: its jet still hurts and still sets alight what stands in the cone, along the synched aim.
 */
@GameTestHolder(ExampleMod.MODID)
public final class HorntailBreathGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private HorntailBreathGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 160)
    public static void wildBreathStillDamagesAndIgnites(GameTestHelper helper) {
        HungarianHorntailEntity d = new HungarianHorntailEntity(ModEntities.HUNGARIAN_HORNTAIL.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        d.setPos(at.x, at.y, at.z);
        d.setNoAi(true);
        d.setPersistenceRequired();
        helper.getLevel().addFreshEntity(d);

        Pig pig = EntityType.PIG.create(helper.getLevel());
        Vec3 pa = helper.absoluteVec(new Vec3(1.5, 2, 7.5));
        pig.setPos(pa.x, pa.y, pa.z);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0);
        pig.setHealth(200.0F);
        helper.getLevel().addFreshEntity(pig);

        d.setTarget(pig);
        d.beginBreath();
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < 200.0F, "the breath should have burnt the pig");
            helper.assertTrue(pig.getRemainingFireTicks() > 0 || pig.isOnFire(), "the breath should have set the pig alight");
            double[] aim = d.breathAimSynced();
            double l = Math.sqrt(aim[0] * aim[0] + aim[1] * aim[1] + aim[2] * aim[2]);
            helper.assertTrue(Math.abs(l - 1.0) < 1e-4, "the synched aim should be a unit vector");
            helper.assertTrue(aim[2] > 0.8, "the synched aim should point at the pig (+z), was " + aim[2]);
            LOGGER.info("PENSIEVE-GAMETEST PASS horntailBreathDamagesAndIgnites");
        });
    }
}
