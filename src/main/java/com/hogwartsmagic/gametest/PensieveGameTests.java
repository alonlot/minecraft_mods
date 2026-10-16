package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.PensieveDimension;
import com.hogwartsmagic.pensieve.RegionAllocator;
import com.hogwartsmagic.pensieve.SetBuilder;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;

/** The memory dimension and the Pensieve set builder, exercised in the real memory level. */
@GameTestHolder(ExampleMod.MODID)
public final class PensieveGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private PensieveGameTests() {
    }

    /**
     * The memory level. The GameTestServer only has its single flat Test Level (it builds no other dimensions),
     * so there the builder is exercised in that level instead; the dimension itself is checked by runServer.
     */
    private static ServerLevel memoryOrTestLevel(GameTestHelper helper) {
        ServerLevel memory = PensieveDimension.level(helper.getLevel().getServer());
        return memory != null ? memory : helper.getLevel();
    }

    private static SceneDef.SetDef stoneFill() {
        return new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone", new int[]{0, 0, 0},
                new int[]{2, 0, 2})), List.of(), List.of(), 6000L, 20);
    }

    @GameTest(template = EMPTY)
    public static void setBuilderPlacesFillsInTheMemoryDimension(GameTestHelper helper) {
        ServerLevel memory = memoryOrTestLevel(helper);
        int[] o = RegionAllocator.originFor(7);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        SetBuilder builder = new SetBuilder(memory, origin, stoneFill());
        helper.assertTrue(builder.total() == 9, "expected 9 blocks, got " + builder.total());
        helper.assertTrue(!builder.advance(4), "a budget of 4 must not finish 9 blocks");
        helper.assertTrue(builder.placed().size() == 4, "budget must be respected");
        helper.assertTrue(builder.advance(100), "set should be done");
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.assertTrue(memory.getBlockState(origin.offset(x, 0, z)).is(Blocks.STONE),
                        "missing stone at " + x + "," + z);
            }
        }
        helper.assertTrue(builder.placed().size() == 9, "placed() should list 9 blocks");
        LOGGER.info("PENSIEVE-GAMETEST PASS {}", new Object() { }.getClass().getEnclosingMethod().getName());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void regionIsClearedOnRelease(GameTestHelper helper) {
        ServerLevel memory = memoryOrTestLevel(helper);
        int[] o = RegionAllocator.originFor(8);
        BlockPos origin = new BlockPos(o[0], o[1], o[2]);
        SetBuilder builder = new SetBuilder(memory, origin, stoneFill());
        builder.advance(1000);
        SetBuilder.ClearJob job = SetBuilder.clear(memory, builder.placed());
        helper.assertTrue(!job.advance(3), "clearing 9 with budget 3 is not done");
        helper.assertTrue(job.advance(100), "clear should finish");
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.assertTrue(memory.getBlockState(origin.offset(x, 0, z)).isAir(),
                        "not cleared at " + x + "," + z);
            }
        }
        LOGGER.info("PENSIEVE-GAMETEST PASS {}", new Object() { }.getClass().getEnclosingMethod().getName());
        helper.succeed();
    }
}
