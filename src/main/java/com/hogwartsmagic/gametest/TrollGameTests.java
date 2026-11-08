package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.TrollEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

/** Wingardium Leviosa against a troll: the club goes up, comes down on its head, and it gets back up. */
@GameTestHolder(ExampleMod.MODID)
public final class TrollGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private TrollGameTests() {
    }

    /** Ticks the troll by hand: the test server stops ticking entities partway through a long batch. */
    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void wingardiumTakesTheClubAndKnocksTheTrollOut(GameTestHelper helper) {
        TrollEntity troll = helper.spawn(ModEntities.TROLL.get(), new BlockPos(1, 2, 1));
        troll.setNoAi(true);
        float before = troll.getHealth();

        helper.assertTrue(troll.pluckClub(null), "a troll holding its club should lose it");
        helper.assertFalse(troll.isHoldingClub(), "the club should have left its hand");
        helper.assertFalse(troll.pluckClub(null), "there is no second club to take");

        tick(troll, TrollEntity.DISARMED_DURATION + 2);
        helper.assertTrue(troll.getAttackState() == TrollEntity.AttackState.KNOCKED_OUT, "the falling club should knock it out");
        helper.assertTrue(troll.getHealth() < before, "the falling club should hurt");

        tick(troll, TrollEntity.KNOCKED_OUT_DURATION + TrollEntity.GET_UP_DURATION + 4);
        helper.assertFalse(troll.isIncapacitated(), "it should be back on its feet, but is " + troll.getAttackState());
        helper.assertTrue(troll.isHoldingClub(), "it should have picked the club back up");
        helper.assertFalse(troll.pluckClub(null), "the same trick should not work again straight away");
        helper.succeed();
    }

    private static void tick(TrollEntity troll, int ticks) {
        for (int i = 0; i < ticks; i++) {
            troll.tick();
        }
    }
}
