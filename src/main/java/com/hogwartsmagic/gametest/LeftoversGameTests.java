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
import com.hogwartsmagic.spell.summon.SerpensortiaRules;
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

        // The fire sits outside the 3x3x3 template, so a neighbouring test may discard it before tick 120:
        // drive it synchronously instead of waiting on the clock.
        try {
            for (int i = 0; i < 120; i++) {
                fire.tick();
            }
            helper.assertTrue(fire.getLifeTicks() >= 120, "the fire should have lived 120 ticks, lived " + fire.getLifeTicks());
            for (int x = 0; x < 9; x++) {
                for (int y = 2; y < 5; y++) {
                    for (int z = 0; z < 9; z++) {
                        helper.assertBlockNotPresent(Blocks.FIRE, new BlockPos(x, y, z));
                    }
                }
            }
        } finally {
            fire.discard();
            rule.set(saved, helper.getLevel().getServer());
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS groundFireRespectsMobGriefing");
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

    /** Serpensortia conjures 1 snake at Novice and 3 at Expert, owned and allied, and a new cast dismisses the old ones. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void serpensortiaConjuresByRank(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        com.hogwartsmagic.item.MagicBookItem.learnSpellIfAbsent(player, com.hogwartsmagic.spell.SpellType.SERPENSORTIA);
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        player.moveTo(at.x, at.y, at.z);
        java.util.List<SnakeEntity> all = new java.util.ArrayList<>();
        try {
            helper.assertTrue(com.hogwartsmagic.spell.summon.SerpensortiaSpell.conjureAt(helper.getLevel(), at, null, player) == 1,
                    "a Novice should conjure one snake");
            java.util.List<SnakeEntity> first = com.hogwartsmagic.spell.summon.SerpensortiaSpell.snakesOf(helper.getLevel(), player);
            all.addAll(first);
            helper.assertTrue(first.size() == 1, "exactly one snake should belong to the caster");
            SnakeEntity snake = first.get(0);
            helper.assertTrue(player.getUUID().equals(snake.getOwnerUUID()), "the snake should belong to the caster");
            helper.assertTrue(snake.getTags().contains(com.hogwartsmagic.spell.combat.CombatTags.ALLIED), "the snake should be tagged allied");

            com.hogwartsmagic.spell.mastery.SpellMasteryData.setXp(player, com.hogwartsmagic.spell.SpellType.SERPENSORTIA,
                    com.hogwartsmagic.Config.getMasteryThresholds()[2]);
            helper.assertTrue(com.hogwartsmagic.spell.summon.SerpensortiaSpell.conjureAt(helper.getLevel(), at, null, player) == 3,
                    "an Expert should conjure three snakes");
            all.addAll(com.hogwartsmagic.spell.summon.SerpensortiaSpell.snakesOf(helper.getLevel(), player));
            helper.assertTrue(snake.isRemoved(), "a new cast should dismiss the earlier snake");
        } finally {
            for (SnakeEntity s : all) {
                s.discard();
            }
            player.discard();
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS serpensortiaConjuresByRank");
    }

    /**
     * Aimed at another player, a Master's command is refused while PvP is off: the snakes neither attack
     * nor switch mode. With PvP on the command is not refused (the helper's mock player is creative, so it
     * is no fair target and the command falls through to the follow/guard toggle).
     */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "leftovers_pvp")
    public static void parseltongueRespectsPvp(GameTestHelper helper) {
        net.minecraft.server.MinecraftServer server = helper.getLevel().getServer();
        boolean savedPvp = server.isPvpAllowed();
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        ServerPlayer victim = helper.makeMockServerPlayerInLevel();
        com.hogwartsmagic.item.MagicBookItem.learnSpellIfAbsent(caster, com.hogwartsmagic.spell.SpellType.SERPENSORTIA);
        com.hogwartsmagic.spell.mastery.SpellMasteryData.setXp(caster, com.hogwartsmagic.spell.SpellType.SERPENSORTIA,
                com.hogwartsmagic.Config.getMasteryThresholds()[3]);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 0.5));
        Vec3 away = helper.absoluteVec(new Vec3(1.5, 2, 2.5));
        caster.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        victim.moveTo(away.x, away.y, away.z);
        try {
            helper.assertTrue(com.hogwartsmagic.spell.summon.SerpensortiaSpell.conjureAt(helper.getLevel(), at, null, caster) > 0,
                    "the Master should conjure snakes");
            SnakeEntity snake = com.hogwartsmagic.spell.summon.SerpensortiaSpell.snakesOf(helper.getLevel(), caster).get(0);

            helper.assertTrue(snake.getMode() == SerpensortiaRules.MODE_FOLLOW, "new snakes should be following");

            server.setPvpAllowed(false);
            com.hogwartsmagic.spell.summon.SerpensortiaSpell.command(caster);
            helper.assertTrue(snake.getTarget() != victim, "snakes must not be sent at a player while PvP is off");
            helper.assertTrue(snake.getMode() == SerpensortiaRules.MODE_FOLLOW, "a refused command must change nothing");

            caster.getPersistentData().remove("hogwartsmagic:parseltongue_at");
            server.setPvpAllowed(true);
            com.hogwartsmagic.spell.summon.SerpensortiaSpell.command(caster);
            helper.assertTrue(snake.getMode() == SerpensortiaRules.MODE_GUARD, "with PvP on the command should not be refused");
        } finally {
            server.setPvpAllowed(savedPvp);
            for (SnakeEntity s : com.hogwartsmagic.spell.summon.SerpensortiaSpell.snakesOf(helper.getLevel(), caster)) {
                s.discard();
            }
            caster.discard();
            victim.discard();
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS parseltongueRespectsPvp");
    }
}
