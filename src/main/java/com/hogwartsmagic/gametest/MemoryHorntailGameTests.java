package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

/** The memory Horntail is a client puppet only: one made on a server leaves on its first tick, before any AI runs. */
@GameTestHolder(ExampleMod.MODID)
public final class MemoryHorntailGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private MemoryHorntailGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void discardsOnServer(GameTestHelper helper) {
        MemoryHorntailEntity dragon = helper.spawn(ModEntities.MEMORY_HORNTAIL.get(), new BlockPos(1, 2, 1));
        helper.assertTrue(dragon != null && !dragon.isRemoved(), "the puppet should exist until its first tick");
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(dragon.isRemoved(), "a memory_horntail made on the server must discard itself");
            helper.assertTrue(helper.getLevel().getEntity(dragon.getId()) == null, "it must be gone from the level");
            LOGGER.info("PENSIEVE-GAMETEST PASS memoryHorntailDiscardsOnServer");
            helper.succeed();
        });
    }
}
