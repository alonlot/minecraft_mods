package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.assignment.AssignmentDef;
import com.hogwartsmagic.assignment.AssignmentEvents;
import com.hogwartsmagic.assignment.AssignmentProgress;
import com.hogwartsmagic.assignment.AssignmentRegistry;
import com.hogwartsmagic.assignment.AssignmentRules;
import com.hogwartsmagic.item.AssignmentItem;
import com.hogwartsmagic.item.MagicBookItem;
import com.hogwartsmagic.spell.SpellType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * End-to-end checks of Hogwarts Assignments on a real server with a mock player: accept, progress
 * from real events, live lines, item delivery, claiming the book, the owner lock and the active cap.
 * Run on a normal server ({@code ./gradlew runServer}) with {@code /test runall}.
 */
@GameTestHolder(ExampleMod.MODID)
public final class AssignmentGameTests {
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** Runs a test body and writes one PASS or FAIL line, so a console-started run can be read from the log. */
    private static void run(GameTestHelper helper, String name, java.util.function.Consumer<GameTestHelper> body) {
        try {
            body.accept(helper);
            LOGGER.info("ASSIGNMENT-GAMETEST PASS {}", name);
            helper.succeed();
        } catch (RuntimeException | AssertionError e) {
            LOGGER.error("ASSIGNMENT-GAMETEST FAIL {}: {}", name, e.getMessage());
            throw e;
        }
    }

    private AssignmentGameTests() {
    }

    private static ItemStack use(GameTestHelper helper, ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        ItemStack result = held.use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getObject();
        player.setItemInHand(InteractionHand.MAIN_HAND, result);
        return result;
    }

    private static void killZombie(GameTestHelper helper, ServerPlayer player) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        zombie.hurt(player.damageSources().playerAttack(player), 1000f);
    }

    @GameTest(template = EMPTY)
    public static void killsAndCastsCompleteAndClaimTheBook(GameTestHelper helper) {
        run(helper, "killsAndCastsCompleteAndClaimTheBook", h -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            AssignmentDef def = AssignmentRegistry.get("levicorpus");
            helper.assertTrue(def != null, "levicorpus assignment is loaded");
            player.setItemInHand(InteractionHand.MAIN_HAND, AssignmentItem.create(SpellType.LEVICORPUS));
            use(helper, player);
            helper.assertTrue(AssignmentProgress.isActive(player, "levicorpus"), "right-click accepts the assignment");

            for (int i = 0; i < 10; i++) {
                AssignmentEvents.onCast(player, SpellType.WINGARDIUM_LEVIOSA, false);
            }
            // A living hit is not a finished cast and must not count for a plain cast line.
            AssignmentEvents.onCast(player, SpellType.WINGARDIUM_LEVIOSA, true);
            for (int i = 0; i < 14; i++) {
                killZombie(helper, player);
            }
            int[] progress = AssignmentProgress.progress(player, def);
            helper.assertTrue(progress[0] == 10 && progress[1] == 14, "progress is 10/10 and 14/15");
            helper.assertFalse(AssignmentProgress.isComplete(player, def), "one kill short is not complete");
            killZombie(helper, player);
            helper.assertTrue(AssignmentProgress.isComplete(player, def), "15 kills complete it");

            ItemStack reward = use(helper, player);
            helper.assertTrue(reward.is(ExampleMod.MAGIC_BOOK.get()) && MagicBookItem.getSpell(reward) == SpellType.LEVICORPUS,
                    "claiming turns the scroll into the Levicorpus book");
            helper.assertFalse(AssignmentProgress.isActive(player, "levicorpus"), "claimed assignment is cleared");
        });
    }

    @GameTest(template = EMPTY)
    public static void knowSpellLineIsLive(GameTestHelper helper) {
        run(helper, "knowSpellLineIsLive", h -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            AssignmentDef def = AssignmentRegistry.get("stupefy");
            player.setItemInHand(InteractionHand.MAIN_HAND, AssignmentItem.create(SpellType.STUPEFY));
            use(helper, player);
            Zombie opponent = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
            AssignmentEvents.onDuelWin(player, opponent);
            AssignmentEvents.onDuelWin(player, opponent);
            helper.assertFalse(AssignmentProgress.isComplete(player, def), "duels alone are not enough without Expelliarmus");
            MagicBookItem.learnSpellIfAbsent(player, SpellType.EXPELLIARMUS);
            helper.assertTrue(AssignmentProgress.isComplete(player, def), "learning Expelliarmus completes it at once");
        });
    }

    @GameTest(template = EMPTY)
    public static void deliveriesAreTakenOnClaim(GameTestHelper helper) {
        run(helper, "deliveriesAreTakenOnClaim", h -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            AssignmentDef def = AssignmentRegistry.get("wingardium_leviosa");
            player.setItemInHand(InteractionHand.MAIN_HAND, AssignmentItem.create(SpellType.WINGARDIUM_LEVIOSA));
            use(helper, player);
            for (int i = 0; i < 20; i++) {
                AssignmentEvents.onCast(player, SpellType.LUMOS, false);
            }
            player.getInventory().add(new ItemStack(Items.FEATHER, 10));
            helper.assertFalse(AssignmentProgress.isComplete(player, def), "10 of 16 feathers is not enough");
            player.getInventory().add(new ItemStack(Items.FEATHER, 10));
            helper.assertTrue(AssignmentProgress.isComplete(player, def), "20 feathers is enough");
            use(helper, player);
            helper.assertTrue(player.getInventory().countItem(Items.FEATHER) == 4, "16 feathers are handed in, 4 stay");
        });
    }

    @GameTest(template = EMPTY)
    public static void scrollsBindAndActiveAssignmentsAreCapped(GameTestHelper helper) {
        run(helper, "scrollsBindAndActiveAssignmentsAreCapped", h -> {
            ServerPlayer owner = helper.makeMockServerPlayerInLevel();
            ServerPlayer other = helper.makeMockServerPlayerInLevel();
            owner.setItemInHand(InteractionHand.MAIN_HAND, AssignmentItem.create(SpellType.ACCIO));
            use(helper, owner);
            other.setItemInHand(InteractionHand.MAIN_HAND, owner.getMainHandItem().copy());
            use(helper, other);
            helper.assertFalse(AssignmentProgress.isActive(other, "accio"), "a bound scroll refuses another player");

            SpellType[] more = {SpellType.LUMOS, SpellType.DIFFINDO, SpellType.DEPULSO};
            for (SpellType spell : more) {
                owner.setItemInHand(InteractionHand.MAIN_HAND, AssignmentItem.create(spell));
                use(helper, owner);
            }
            helper.assertTrue(AssignmentProgress.activeCount(owner) == AssignmentRules.MAX_ACTIVE,
                    "only " + AssignmentRules.MAX_ACTIVE + " assignments can be in progress");
            helper.assertFalse(AssignmentProgress.isActive(owner, "depulso"), "the fourth one is refused");

            MagicBookItem.learnSpellIfAbsent(other, SpellType.LUMOS);
            other.setItemInHand(InteractionHand.MAIN_HAND, AssignmentItem.create(SpellType.LUMOS));
            use(helper, other);
            helper.assertFalse(AssignmentProgress.isActive(other, "lumos"), "a known spell's assignment is refused");
        });
    }
}
