package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.FiendfyreEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Gametests for the roadmap-leftovers plan. Each task adds its own tests here and logs a
 * LEFTOVERS-GAMETEST PASS line the verification step greps for.
 */
@GameTestHolder(ExampleMod.MODID)
public final class LeftoversGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private LeftoversGameTests() {
    }

    /** With mobGriefing off, a caster-less Fiendfyre must not light a single block. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void groundFireRespectsMobGriefing(GameTestHelper helper) {
        GameRules.BooleanValue rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean saved = rule.get();
        rule.set(false, helper.getLevel().getServer());

        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        FiendfyreEntity fire = new FiendfyreEntity(ModEntities.FIENDFYRE.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
        fire.moveTo(at.x, at.y, at.z);
        fire.setCaster(null);
        helper.getLevel().addFreshEntity(fire);

        helper.runAtTickTime(120, () -> {
            try {
                for (int x = 0; x < 9; x++) {
                    for (int y = 2; y < 5; y++) {
                        for (int z = 0; z < 9; z++) {
                            helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, y, z));
                        }
                    }
                }
            } finally {
                rule.set(saved, helper.getLevel().getServer());
            }
            helper.succeed();
            LOGGER.info("LEFTOVERS-GAMETEST PASS groundFireRespectsMobGriefing");
        });
    }
}
