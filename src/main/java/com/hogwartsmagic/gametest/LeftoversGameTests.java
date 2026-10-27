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
    /** 9x4x9 of air, for tests whose actors need more than 3 blocks between them. */
    private static final String ROOMY = "hogwartsmagic:gametest/empty9";

    /** The owner is removed at tick 5 and the snake gives up after OWNER_GONE_TICKS; a few ticks of slack on top. */
    private static final int SNAKE_VANISH_DEADLINE = 5 + SerpensortiaRules.OWNER_GONE_TICKS + 5;

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
        // No AI keeps it from strolling out of the force-loaded area; aiStep still runs the owner-gone logic.
        snake.setNoAi(true);
        helper.getLevel().addFreshEntity(snake);

        helper.runAtTickTime(5, owner::discard);
        // With noAi the target check alone is vacuous: the real assertion is that it is gone in time.
        helper.runAtTickTime(SNAKE_VANISH_DEADLINE, () -> {
            if (!snake.isRemoved()) {
                snake.discard();
                helper.fail("the snake should vanish within " + SNAKE_VANISH_DEADLINE + " ticks of its owner going");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(snake.getTarget() == null, "the snake must never gain a target");
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

    /** A Master Patronus pushes a zombie 3 blocks away out past 3.5 blocks. */
    @GameTest(template = ROOMY, timeoutTicks = 100, batch = "leftovers_patronus")
    public static void masterPatronusRepelsUndead(GameTestHelper helper) {
        com.hogwartsmagic.entity.PatronusEntity master = patronusAt(helper, new Vec3(1.5, 2, 1.5), true);
        Zombie near = noAiZombie(helper, new Vec3(4.5, 2, 1.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(master.isAlive() && near.isAlive(), "the Master Patronus and its zombie should both still be alive");
            double pushed = near.position().distanceTo(master.position());
            helper.assertTrue(pushed > 3.5, "the zombie should be driven past 3.5 blocks, was " + pushed);
            LOGGER.info("LEFTOVERS-GAMETEST PASS masterPatronusRepelsUndead");
        });
    }

    /** The control: a Patronus without the Master aura leaves its zombie where it stood. */
    @GameTest(template = ROOMY, timeoutTicks = 100, batch = "leftovers_patronus")
    public static void plainPatronusLeavesUndeadBe(GameTestHelper helper) {
        com.hogwartsmagic.entity.PatronusEntity plain = patronusAt(helper, new Vec3(1.5, 2, 1.5), false);
        Zombie calm = noAiZombie(helper, new Vec3(4.5, 2, 1.5));
        // Long enough for the aura to have pushed a Master's zombie (the Master test succeeds well inside this).
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(plain.isAlive() && calm.isAlive(), "the control zombie and Patronus should both still be alive");
            double kept = calm.position().distanceTo(plain.position());
            helper.assertTrue(kept <= 3.2, "without the aura the zombie should stay put, was " + kept);
            helper.succeed();
            LOGGER.info("LEFTOVERS-GAMETEST PASS plainPatronusLeavesUndeadBe");
        });
    }

    /** The caster of a Master Patronus standing 2 blocks away has Night Vision within a few seconds. */
    @GameTest(template = EMPTY, timeoutTicks = 100, batch = "leftovers_patronus")
    public static void masterPatronusGivesNightVision(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        player.moveTo(at.x, at.y, at.z);
        com.hogwartsmagic.entity.PatronusEntity master = patronusAt(helper, new Vec3(1.5, 2, 1.5), true);
        master.setOwner(player.getUUID());
        master.moveTo(at.x + 2.0, at.y, at.z);
        helper.runAfterDelay(45, () -> {
            boolean has = player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
            master.discard();
            player.discard();
            helper.assertTrue(has, "the caster should have Night Vision");
            helper.succeed();
            LOGGER.info("LEFTOVERS-GAMETEST PASS masterPatronusGivesNightVision");
        });
    }

    private static com.hogwartsmagic.entity.PatronusEntity patronusAt(GameTestHelper helper, Vec3 rel, boolean aura) {
        com.hogwartsmagic.entity.PatronusEntity p = new com.hogwartsmagic.entity.PatronusEntity(ModEntities.PATRONUS.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(rel);
        p.moveTo(at.x, at.y, at.z);
        p.setMasterAura(aura);
        p.setNoAi(true);
        helper.getLevel().addFreshEntity(p);
        return p;
    }

    private static Zombie noAiZombie(GameTestHelper helper, Vec3 rel) {
        Zombie z = new Zombie(helper.getLevel());
        Vec3 at = helper.absoluteVec(rel);
        z.moveTo(at.x, at.y, at.z);
        // A truly no-AI mob never travels, so it could not be pushed: keep the AI on but give it no goals.
        z.goalSelector.removeAllGoals(g -> true);
        z.targetSelector.removeAllGoals(g -> true);
        z.setNoGravity(true);
        helper.getLevel().addFreshEntity(z);
        return z;
    }

    /** Only a Master may send a Patronus message, and a second send straight away is refused by the cooldown. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void patronusMessengerGatesAndCoolsDown(GameTestHelper helper) {
        ServerPlayer sender = helper.makeMockServerPlayerInLevel();
        ServerPlayer recipient = helper.makeMockServerPlayerInLevel();
        com.hogwartsmagic.spell.SpellType spell = com.hogwartsmagic.spell.SpellType.EXPECTO_PATRONUM;
        com.hogwartsmagic.item.MagicBookItem.learnSpellIfAbsent(sender, spell);
        sender.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.item.ItemStack(ExampleMod.HARRY_POTTER_WAND.get()));
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 0.5));
        Vec3 away = helper.absoluteVec(new Vec3(1.5, 2, 2.5));
        sender.moveTo(at.x, at.y, at.z);
        recipient.moveTo(away.x, away.y, away.z);
        try {
            helper.assertFalse(com.hogwartsmagic.spell.summon.PatronusMessenger.send(sender, recipient, "hello"),
                    "a non-Master must be refused");
            com.hogwartsmagic.spell.mastery.SpellMasteryData.setXp(sender, spell, com.hogwartsmagic.Config.getMasteryThresholds()[3]);
            helper.assertTrue(com.hogwartsmagic.spell.summon.PatronusMessenger.send(sender, recipient, "hello"),
                    "a Master should send the first message");
            helper.assertFalse(com.hogwartsmagic.spell.summon.PatronusMessenger.send(sender, recipient, "hello again"),
                    "an immediate second message must hit the cooldown");
        } finally {
            sender.discard();
            recipient.discard();
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS patronusMessengerGatesAndCoolsDown");
    }

    /**
     * A real survival player: the helper mock overrides isCreative to true, which would make Remorse read as OFF.
     * Placed through the player list like a login, and removed again with {@link #leave}.
     */
    private static ServerPlayer survivalPlayerWithDarkness(GameTestHelper helper, String name, float darkness) {
        var server = helper.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, player, cookie);
        try {
            // Login puts the player at world spawn; bring it into the test structure's bounds.
            Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
            player.moveTo(at.x, at.y, at.z);
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            com.hogwartsmagic.spell.dark.DarkMagicData.set(player, darkness);
        } catch (RuntimeException | Error e) {
            leave(helper, player);
            throw e;
        }
        return player;
    }

    private static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
    }

    private static void drinkRemorse(ServerPlayer player) {
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(ExampleMod.POTION_OF_REMORSE.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        player.invulnerableTime = 0;
        stack.getItem().finishUsingItem(stack, player.level(), player);
    }

    /** One Remorse lightens the soul by 5; a second drink inside the window still hurts but lightens nothing. */
    @GameTest(template = EMPTY, timeoutTicks = 120)
    public static void remorseLightensOnceAWindow(GameTestHelper helper) {
        ServerPlayer player = survivalPlayerWithDarkness(helper, "remorse-window", 50.0f);
        // A freshly placed player is hurt-proof for 60 ticks, so the pain would read as nothing.
        helper.runAfterDelay(65, () -> {
            try {
                drinkRemorse(player);
                helper.assertTrue(com.hogwartsmagic.spell.dark.DarkMagicData.getStored(player) == 45.0f,
                        "the first drink should lighten 50 to 45, was " + com.hogwartsmagic.spell.dark.DarkMagicData.getStored(player));
                float healthAfterFirst = player.getHealth();
                helper.assertTrue(healthAfterFirst < player.getMaxHealth(), "the drink should hurt");
                drinkRemorse(player);
                helper.assertTrue(com.hogwartsmagic.spell.dark.DarkMagicData.getStored(player) == 45.0f,
                        "the second drink inside the window must lighten nothing");
                helper.assertTrue(player.getHealth() < healthAfterFirst, "the second drink should still hurt");
            } finally {
                leave(helper, player);
            }
            helper.succeed();
            LOGGER.info("LEFTOVERS-GAMETEST PASS remorseLightensOnceAWindow");
        });
    }

    /** A drink with nothing to lighten changes nothing and does not spend the window. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void remorseAtTheFloorKeepsTheWindow(GameTestHelper helper) {
        ServerPlayer player = survivalPlayerWithDarkness(helper, "remorse-floor", 0.0f);
        try {
            drinkRemorse(player);
            helper.assertTrue(com.hogwartsmagic.spell.dark.DarkMagicData.getStored(player) == 0.0f, "Clean stays Clean");
            com.hogwartsmagic.spell.dark.DarkMagicData.set(player, 50.0f);
            drinkRemorse(player);
            helper.assertTrue(com.hogwartsmagic.spell.dark.DarkMagicData.getStored(player) == 45.0f,
                    "the window must not have been spent by the drink at the floor");
        } finally {
            leave(helper, player);
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS remorseAtTheFloorKeepsTheWindow");
    }

    /** A grey charm adds its small Darkness but leaves the quiet window and fade progress alone; a dark spell resets them. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void greyCastLeavesTheFadeClockUntouched(GameTestHelper helper) {
        ServerPlayer player = survivalPlayerWithDarkness(helper, "grey-clock", 50.0f);
        try {
            net.minecraft.nbt.CompoundTag data = com.hogwartsmagic.util.PlayerMagicData.getModData(player);
            data.putLong(com.hogwartsmagic.spell.dark.DarkMagicData.KEY_LAST_DARK_CAST, -100000L);
            data.putInt(com.hogwartsmagic.spell.dark.DarkMagicData.KEY_DECAY_PROGRESS, 77);
            com.hogwartsmagic.util.PlayerMagicData.saveModData(player, data);
            com.hogwartsmagic.spell.dark.DarkMagicData.onDarkCastPaid(player, com.hogwartsmagic.spell.SpellType.OBLIVIATE);
            data = com.hogwartsmagic.util.PlayerMagicData.getModData(player);
            helper.assertTrue(com.hogwartsmagic.spell.dark.DarkMagicData.getStored(player) > 50.0f, "the grey cast should still darken");
            helper.assertTrue(data.getLong(com.hogwartsmagic.spell.dark.DarkMagicData.KEY_LAST_DARK_CAST) == -100000L,
                    "a grey cast must not restart the quiet window");
            helper.assertTrue(data.getInt(com.hogwartsmagic.spell.dark.DarkMagicData.KEY_DECAY_PROGRESS) == 77,
                    "a grey cast must not reset the fade progress");
            com.hogwartsmagic.spell.dark.DarkMagicData.onDarkCastPaid(player, com.hogwartsmagic.spell.SpellType.CRUCIO);
            data = com.hogwartsmagic.util.PlayerMagicData.getModData(player);
            helper.assertTrue(data.getInt(com.hogwartsmagic.spell.dark.DarkMagicData.KEY_DECAY_PROGRESS) == 0,
                    "a dark cast must reset the fade progress");
        } finally {
            leave(helper, player);
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS greyCastLeavesTheFadeClockUntouched");
    }

    // ---- brewing quality

    private static void brewAt(GameTestHelper helper, net.minecraft.world.entity.player.Player player, BlockPos pos,
                               long tick, net.minecraft.world.item.ItemStack stack) {
        helper.runAtTickTime(tick, () -> {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            helper.useBlock(pos, player);
        });
    }

    /** Wiggenweld on a campfire: water, honey, two glow berries, three stirs at the given ticks, 20 s simmer, then a bottle. */
    private static void brewWiggenweld(GameTestHelper helper, long[] stirTicks, int expectedGrade, String name) {
        BlockPos fire = new BlockPos(1, 2, 1);
        BlockPos pos = new BlockPos(1, 3, 1);
        helper.setBlock(fire, Blocks.CAMPFIRE);
        helper.setBlock(pos, ExampleMod.WIZARD_CAULDRON.get());
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        brewAt(helper, player, pos, 1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        brewAt(helper, player, pos, 5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.HONEY_BOTTLE));
        brewAt(helper, player, pos, 25, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOW_BERRIES));
        brewAt(helper, player, pos, 45, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLOW_BERRIES));
        for (long t : stirTicks) {
            brewAt(helper, player, pos, t, new net.minecraft.world.item.ItemStack(ExampleMod.FINE_WOOD_STICK.get()));
        }
        // The simmer needs 20 heated seconds after the stirs; the cauldron ticks once a second.
        helper.runAtTickTime(stirTicks[stirTicks.length - 1] + 20 * 20 + 80, () -> {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
            helper.useBlock(pos, player);
            net.minecraft.world.item.ItemStack result = player.getMainHandItem();
            helper.assertTrue(result.is(ExampleMod.WIGGENWELD_POTION.get()), "expected a Wiggenweld potion, got " + result);
            int grade = result.getOrDefault(ExampleMod.BREW_QUALITY.get(), 0);
            helper.assertTrue(grade == expectedGrade, "expected grade " + expectedGrade + ", got " + grade);
            helper.succeed();
            LOGGER.info("LEFTOVERS-GAMETEST PASS " + name);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 1000)
    public static void steadyStirsBrewExceptionalWiggenweld(GameTestHelper helper) {
        brewWiggenweld(helper, new long[]{65, 85, 105}, com.hogwartsmagic.brewing.BrewQuality.EXCEPTIONAL,
                "steadyStirsBrewExceptionalWiggenweld");
    }

    @GameTest(template = EMPTY, timeoutTicks = 1000)
    public static void spammedStirsBrewStandardWiggenweld(GameTestHelper helper) {
        brewWiggenweld(helper, new long[]{65, 66, 67}, com.hogwartsmagic.brewing.BrewQuality.STANDARD,
                "spammedStirsBrewStandardWiggenweld");
    }

    /** A cauldron saved before quality tracking (no Q keys) loads with zero counters and bottles Standard. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void oldCauldronSaveLoadsAsStandard(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ExampleMod.WIZARD_CAULDRON.get());
        var be = helper.getBlockEntity(pos);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putBoolean("Initialized", true);
        tag.putInt("Doses", 3);
        net.minecraft.nbt.ListTag history = new net.minecraft.nbt.ListTag();
        history.add(historyEntry("ADD", "minecraft:honey_bottle", 1));
        history.add(historyEntry("ADD", "minecraft:glow_berries", 2));
        history.add(historyEntry("STIR_CLOCKWISE", "", 3));
        history.add(historyEntry("SIMMER", "", 20));
        tag.put("History", history);
        be.loadCustomOnly(tag, helper.getLevel().registryAccess());
        net.minecraft.world.entity.player.Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE));
        helper.useBlock(pos, player);
        net.minecraft.world.item.ItemStack result = player.getMainHandItem();
        helper.assertTrue(result.is(ExampleMod.WIGGENWELD_POTION.get()), "expected a Wiggenweld potion, got " + result);
        helper.assertTrue(result.getOrDefault(ExampleMod.BREW_QUALITY.get(), 0) == 0, "an old save must grade Standard");
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS oldCauldronSaveLoadsAsStandard");
    }

    /** A wild dittany plant drops a fine leaf (beside the plain one); a planted one drops no fine leaf. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void wildDittanyDropsFineLeaves(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos abs = helper.absolutePos(pos);
        for (boolean wild : new boolean[] {true, false}) {
            net.minecraft.world.level.block.state.BlockState state = ExampleMod.DITTANY_PLANT.get().defaultBlockState()
                    .setValue(com.hogwartsmagic.block.DittanyPlantBlock.WILD, wild);
            helper.setBlock(pos, state);
            java.util.List<net.minecraft.world.item.ItemStack> drops =
                    net.minecraft.world.level.block.Block.getDrops(state, helper.getLevel(), abs, null);
            int fine = 0;
            int plain = 0;
            for (net.minecraft.world.item.ItemStack s : drops) {
                if (!s.is(ExampleMod.DITTANY.get())) {
                    continue;
                }
                if (s.getOrDefault(ExampleMod.FINE_INGREDIENT.get(), false)) {
                    fine += s.getCount();
                } else {
                    plain += s.getCount();
                }
            }
            if (wild) {
                helper.assertTrue(fine == 1 && plain == 1, "wild dittany should drop 1 fine + 1 plain leaf, got fine=" + fine + " plain=" + plain);
            } else {
                helper.assertTrue(fine == 0 && plain == 1, "planted dittany should drop 1 plain leaf, got fine=" + fine + " plain=" + plain);
            }
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS wildDittanyDropsFineLeaves");
    }

    /** A player who has not read Advanced Potion-Making is refused the first Asphodel without loss; once read, it is taken. */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void unreadPlayerIsRefusedWithoutLoss(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ExampleMod.WIZARD_CAULDRON.get());
        ServerPlayer player = survivalPlayerWithDarkness(helper, "unread-book", 0.0f);
        try {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
            helper.useBlock(pos, player);
            net.minecraft.world.item.ItemStack asphodel = new net.minecraft.world.item.ItemStack(ExampleMod.ASPHODEL.get(), 3);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, asphodel);
            helper.useBlock(pos, player);
            helper.assertTrue(player.getMainHandItem().getCount() == 3,
                    "an unread player must keep the asphodel, have " + player.getMainHandItem().getCount());
            var cauldron = (com.hogwartsmagic.block.entity.WizardCauldronBlockEntity) helper.getBlockEntity(pos);
            helper.assertTrue(cauldron.historySize() == 0, "a refused step must not enter the history");
            helper.assertTrue(!cauldron.isRuined(), "a refused step must not ruin the brew");
            helper.assertTrue(com.hogwartsmagic.brewing.AdvancedPotions.markRead(player), "first reading should report true");
            helper.assertTrue(!com.hogwartsmagic.brewing.AdvancedPotions.markRead(player), "second reading should report false");
            helper.useBlock(pos, player);
            helper.assertTrue(player.getMainHandItem().getCount() == 2,
                    "a reader's asphodel is taken, have " + player.getMainHandItem().getCount());
            helper.assertTrue(cauldron.historySize() == 1 && !cauldron.isRuined(), "the reader starts Living Death");
        } finally {
            leave(helper, player);
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS unreadPlayerIsRefusedWithoutLoss");
    }

    private static net.minecraft.nbt.CompoundTag historyEntry(String kind, String item, int amount) {
        net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
        entry.putString("Kind", kind);
        entry.putString("Item", item);
        entry.putInt("Amount", amount);
        return entry;
    }

    /** Veritaserum on a villager: gossip rises by 25 and the bottle is spent; a second try the same day is refused. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void veritaserumMakesAVillagerConfessOnce(GameTestHelper helper) {
        ServerPlayer player = survivalPlayerWithDarkness(helper, "veritaserum-giver", 0.0f);
        Villager villager = helper.spawn(EntityType.VILLAGER, new Vec3(1.5, 2, 1.5));
        villager.setNoAi(true);
        try {
            var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            java.util.UUID id = player.getUUID();
            int before = villager.getGossips().getReputation(id, t -> true);

            net.minecraft.world.item.ItemStack first = new net.minecraft.world.item.ItemStack(ExampleMod.VERITASERUM.get());
            player.setItemInHand(hand, first);
            first.getItem().interactLivingEntity(first, player, villager, hand);
            int after = villager.getGossips().getReputation(id, t -> true);
            helper.assertTrue(after - before == 25, "the villager should confess for 25, was " + (after - before));
            helper.assertTrue(player.getItemInHand(hand).is(net.minecraft.world.item.Items.GLASS_BOTTLE),
                    "the dose should be spent, hand holds " + player.getItemInHand(hand));

            net.minecraft.world.item.ItemStack second = new net.minecraft.world.item.ItemStack(ExampleMod.VERITASERUM.get());
            player.setItemInHand(hand, second);
            second.getItem().interactLivingEntity(second, player, villager, hand);
            helper.assertTrue(villager.getGossips().getReputation(id, t -> true) == after, "a second use must not add gossip");
            helper.assertTrue(player.getItemInHand(hand).is(ExampleMod.VERITASERUM.get())
                    && player.getItemInHand(hand).getCount() == 1, "a refused use must keep the dose");
        } finally {
            villager.discard();
            leave(helper, player);
        }
        helper.succeed();
        LOGGER.info("LEFTOVERS-GAMETEST PASS veritaserumMakesAVillagerConfessOnce");
    }
}
