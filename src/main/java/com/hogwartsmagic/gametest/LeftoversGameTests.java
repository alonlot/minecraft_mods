package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.FiendfyreEntity;
import com.hogwartsmagic.entity.FiendfyreSerpentEntity;
import com.hogwartsmagic.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import com.hogwartsmagic.entity.SnakeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
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
    @GameTest(template = EMPTY, timeoutTicks = 200, batch = "leftovers_griefing")
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
                helper.assertTrue(fire.isAlive() && fire.getLifeTicks() > 0, "the fire entity should still be ticking");
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

    /** A serpent fated to turn at life tick 60 glows red for a second first, then goes rogue. */
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void serpentTelegraphsThenTurns(GameTestHelper helper) {
        FiendfyreSerpentEntity serpent = new FiendfyreSerpentEntity(ModEntities.FIENDFYRE_SERPENT.get(), helper.getLevel());
        serpent.configure(0, 1, 60, 120);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 3, 1.5));
        serpent.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(serpent);

        helper.runAtTickTime(45, () -> {
            helper.assertTrue(serpent.isAlive(), "the serpent should still be burning at tick 45");
            helper.assertTrue(serpent.isTelegraphing(), "the serpent should glow red before it turns");
            helper.assertFalse(serpent.isRogue(), "the serpent should not have turned yet");
        });
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(serpent.isAlive(), "the serpent should still be burning at tick 65");
            helper.assertTrue(serpent.isRogue(), "the serpent should have turned by tick 65");
            serpent.discard(); // a rogue serpent hunts for 16 blocks: keep it out of the neighbouring tests
            helper.succeed();
            LOGGER.info("LEFTOVERS-GAMETEST PASS serpentTelegraphsThenTurns");
        });
    }

    /**
     * A rogue serpent burns the mob beside it but not the protected player (the mock player is creative,
     * and a caster-less serpent could not harm another player anyway) nor an armor stand.
     */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void rogueSerpentSparesBystanders(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 2);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);
        zombie.setHealth(zombie.getMaxHealth());
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, 0, 1, 1);
        stand.setNoGravity(true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 playerAt = helper.absoluteVec(new Vec3(1.5, 1, 0.5));
        player.moveTo(playerAt.x, playerAt.y, playerAt.z);

        FiendfyreSerpentEntity serpent = new FiendfyreSerpentEntity(ModEntities.FIENDFYRE_SERPENT.get(), helper.getLevel());
        serpent.configure(0, 1, 1, 120); // turns on its first tick
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 1.5, 1.5));
        serpent.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(serpent);

        helper.runAtTickTime(8, () -> {
            try {
                helper.assertTrue(serpent.isRogue(), "the serpent should have turned rogue");
                helper.assertTrue(zombie.isOnFire() || zombie.getHealth() < zombie.getMaxHealth(), "the rogue serpent should burn the mob");
                helper.assertTrue(player.getHealth() >= player.getMaxHealth() && !player.isOnFire(), "the protected player must not be burnt");
                helper.assertTrue(stand.isAlive() && !stand.isOnFire(), "the armor stand must not be burnt");
            } finally {
                serpent.discard();
                zombie.discard();
                stand.discard();
                player.discard();
            }
            helper.succeed();
            LOGGER.info("LEFTOVERS-GAMETEST PASS rogueSerpentSparesBystanders");
        });
    }

    /** A conjured snake refuses its owner as a target, even when handed it directly through setTarget. */
    @GameTest(template = EMPTY, timeoutTicks = 160)
    public static void snakeNeverTurnsOnItsOwner(GameTestHelper helper) {
        Villager owner = helper.spawn(EntityType.VILLAGER, new Vec3(1.5, 2, 1.5));
        owner.setNoAi(true);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(5.5, 2, 1.5));
        zombie.setNoAi(true);
        SnakeEntity snake = new SnakeEntity(ModEntities.SNAKE.get(), helper.getLevel());
        snake.conjure(owner, 400, 0);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 1.5));
        snake.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(snake);
        snake.setTarget(owner);

        helper.runAtTickTime(100, () -> {
            try {
                helper.assertTrue(snake.getTarget() != owner, "the snake must not have targeted its owner");
                snake.setTarget(owner);
                helper.assertTrue(snake.getTarget() != owner, "setTarget must refuse the owner");
            } finally {
                snake.discard();
                owner.discard();
                zombie.discard();
            }
            helper.succeed();
            LOGGER.info("LEFTOVERS-GAMETEST PASS snakeNeverTurnsOnItsOwner");
        });
    }

    /** Remove the owner and the conjured snake must go within a couple of seconds, never turning wild. */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void snakeVanishesWhenItsOwnerIsGone(GameTestHelper helper) {
        Pig owner = helper.spawn(EntityType.PIG, new Vec3(1.5, 2, 1.5));
        owner.setNoAi(true);
        SnakeEntity snake = new SnakeEntity(ModEntities.SNAKE.get(), helper.getLevel());
        snake.conjure(owner, 1200, 0);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 1.5));
        snake.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(snake);

        helper.runAtTickTime(5, owner::discard);
        helper.runAtTickTime(100, () -> {
            if (!snake.isRemoved()) {
                snake.discard();
                helper.fail("the snake should have vanished after its owner was gone");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(snake.isRemoved(), "the snake should vanish when its owner is gone");
            LOGGER.info("LEFTOVERS-GAMETEST PASS snakeVanishesWhenItsOwnerIsGone");
        });
    }
}
