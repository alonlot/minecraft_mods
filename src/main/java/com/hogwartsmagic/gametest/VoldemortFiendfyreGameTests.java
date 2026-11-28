package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.VoldemortEntity;
import com.hogwartsmagic.entity.VoldemortMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;

/** Voldemort's held Fiendfyre: the serpents come out of his wand, burn what he aims at, and go out when he lets go. */
@GameTestHolder(ExampleMod.MODID)
public final class VoldemortFiendfyreGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String ROOMY = "hogwartsmagic:gametest/empty9";
    private static final int START = 5;

    private VoldemortFiendfyreGameTests() {
    }

    @GameTest(template = ROOMY, timeoutTicks = 160)
    public static void heldFiendfyreBurnsItsTargetThenGoesOut(GameTestHelper helper) {
        VoldemortEntity voldemort = helper.spawn(ModEntities.VOLDEMORT.get(), new BlockPos(1, 1, 4));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(7, 1, 4));
        pig.setNoAi(true);
        float pigHealth = pig.getHealth();
        AABB around = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(24.0D);

        helper.runAtTickTime(START, () -> voldemort.startHeldFiendfyre(pig));
        helper.runAtTickTime(START + VoldemortMoves.HELD_SPAWN_TICK + 12, () -> {
            List<FiendfyreSerpentEntity> serpents = helper.getLevel().getEntitiesOfClass(FiendfyreSerpentEntity.class, around);
            helper.assertTrue(voldemort.isInMove(), "he should still be holding the fire");
            helper.assertTrue(voldemort.fiendfyreHeld(), "the channel should be held");
            helper.assertTrue(serpents.size() == 2, "two serpents should answer him before the turn, saw " + serpents.size());
            for (FiendfyreSerpentEntity s : serpents) {
                helper.assertFalse(s.isRogue(), "his serpents never turn");
                helper.assertTrue(s.distanceTo(voldemort) < 12.0D, "a serpent strayed " + s.distanceTo(voldemort) + " blocks from him");
            }
        });
        helper.runAtTickTime(START + 80, () ->
                helper.assertTrue(pig.isDeadOrDying() || pig.getHealth() < pigHealth, "the fire should have burnt the pig"));
        helper.runAtTickTime(START + VoldemortMoves.HELD_RELEASE_TICK + 4, () -> {
            int left = helper.getLevel().getEntitiesOfClass(FiendfyreSerpentEntity.class, around).size();
            helper.assertTrue(left == 0, "the serpents should go out when he lets go, " + left + " left");
            helper.assertFalse(voldemort.fiendfyreHeld(), "the channel should be over");
        });
        helper.runAtTickTime(START + VoldemortMoves.HELD_END_TICK + 4, () -> {
            helper.assertFalse(voldemort.isInMove(), "the move should have ended with its clip");
            voldemort.discard();
            helper.succeed();
            LOGGER.info("VOLDEMORT-FIENDFYRE-GAMETEST PASS heldFiendfyreBurnsItsTargetThenGoesOut");
        });
    }
}
