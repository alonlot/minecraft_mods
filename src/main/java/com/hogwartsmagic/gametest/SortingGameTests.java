package com.hogwartsmagic.gametest;

import com.google.gson.JsonParser;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.MemoryRegistry;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.Session;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.sorting.HogwartsHouse;
import com.hogwartsmagic.sorting.SortingHatManager;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Sorting Hat flow: the hat plays the Sorting memory, then the fixed quiz sorts the player. The memory level is
 * PensieveSessions' test seam (the test level stands in for it) and the stub {@code sorting.json} is the scene.
 * Every test has its own batch: the seam is global, and a test that needs the quiz at once swaps in a seam with no
 * scene, so startSolo refuses and the manager falls straight through to the quiz.
 */
@GameTestHolder(ExampleMod.MODID)
public final class SortingGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final String HOUSE_KEY = "SortingHouse";

    private SortingGameTests() {
    }

    private static MemoryRegistry.LoadedScene stubScene() {
        String path = "/data/hogwartsmagic/memories/" + SortingHatManager.MEMORY_SCENE + ".json";
        try (InputStream in = SortingGameTests.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return new MemoryRegistry.LoadedScene(SceneParser.parse(SortingHatManager.MEMORY_SCENE,
                    JsonParser.parseString(json).getAsJsonObject()), json);
        } catch (Exception e) {
            throw new IllegalStateException("could not load the stub Sorting scene", e);
        }
    }

    /** The memory seam with the stub scene for the Sorting id (and nothing else). */
    private static void useStubSeam(GameTestHelper helper) {
        MemoryRegistry.LoadedScene stub = stubScene();
        PensieveSessions.setBuildBudgetForTests(-1);
        PensieveSessions.overrideForTests(helper.getLevel(),
                id -> SortingHatManager.MEMORY_SCENE.equals(id) ? Optional.of(stub) : Optional.empty());
    }

    /** A seam with no scenes: startSolo refuses, so the hat goes straight to the quiz. */
    private static void useNoSceneSeam(GameTestHelper helper) {
        PensieveSessions.setBuildBudgetForTests(-1);
        PensieveSessions.overrideForTests(helper.getLevel(), id -> Optional.empty());
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0f, 0f);
        return p;
    }

    private static void hat(ServerPlayer p, boolean on) {
        p.setItemSlot(EquipmentSlot.HEAD, on ? new ItemStack(ExampleMod.SORTING_HAT.get()) : ItemStack.EMPTY);
    }

    private static String house(ServerPlayer p) {
        return PlayerMagicData.getModData(p).getString(HOUSE_KEY);
    }

    private static void logout(GameTestHelper helper, ServerPlayer p) {
        helper.getLevel().getServer().getPlayerList().remove(p);
    }

    /** Leaves with {@code reason} on the first tick the player is inside the memory and watching PLAY. */
    private static void leaveOncePlaying(GameTestHelper helper, ServerPlayer p, ExitReason reason, AtomicBoolean left) {
        helper.onEachTick(() -> {
            var s = PensieveSessions.sessionOf(p);
            if (left.get() || s.isEmpty() || s.get().phase() != Session.Phase.PLAY) return;
            var v = s.get().viewers().get(p.getUUID());
            if (v == null || !v.sawStart()) return;
            left.set(true);
            PensieveSessions.leave(p, reason);
        });
    }

    /** Answers slot 0 until sorted (a tie, if any, also takes slot 0); at most 4 answers. */
    private static void answerToTheEnd(SortingHatManager m, ServerPlayer p) {
        for (int i = 0; i < 4 && m.hasQuiz(p.getUUID()); i++) {
            m.onChoice(p, m.quizStep(p.getUUID()), 0);
        }
    }

    @GameTest(template = EMPTY, batch = "sorting_memory_then_quiz", timeoutTicks = 400)
    public static void hatStartsMemoryThenQuiz(GameTestHelper helper) {
        useStubSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(PensieveSessions.isViewer(p), "the hat should start the Sorting memory");
        helper.assertTrue(!m.hasQuiz(p.getUUID()), "no quiz while the memory plays");
        AtomicBoolean left = new AtomicBoolean();
        leaveOncePlaying(helper, p, ExitReason.LEFT, left);
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get(), "not left yet");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(m.hasQuiz(p.getUUID()), "no quiz after coming home");
            helper.assertTrue(m.quizStep(p.getUUID()) == 0, "quiz should be at step 0, is " + m.quizStep(p.getUUID()));
            logout(helper, p);
            helper.assertTrue(!m.hasQuiz(p.getUUID()), "logout should drop the quiz");
            LOGGER.info("SORTING-GAMETEST PASS hatStartsMemoryThenQuiz");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_three_answers", timeoutTicks = 100)
    public static void threeAnswersSortPlayer(GameTestHelper helper) {
        useNoSceneSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(!PensieveSessions.isViewer(p), "no scene: no memory");
        helper.assertTrue(m.hasQuiz(p.getUUID()) && m.quizStep(p.getUUID()) == 0, "no scene: quiz at once");
        answerToTheEnd(m, p);
        helper.assertTrue(!m.hasQuiz(p.getUUID()), "quiz should be cleared once sorted");
        String h = house(p);
        boolean valid = false;
        for (HogwartsHouse hh : HogwartsHouse.values()) valid |= hh.name().equals(h);
        helper.assertTrue(valid, "no house saved, got '" + h + "'");
        m.tick(p);
        helper.assertTrue(!m.hasQuiz(p.getUUID()) && !PensieveSessions.isViewer(p), "a sorted player starts nothing");
        logout(helper, p);
        LOGGER.info("SORTING-GAMETEST PASS threeAnswersSortPlayer");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "sorting_hat_removed", timeoutTicks = 200)
    public static void hatRemovedMidQuizCancels(GameTestHelper helper) {
        useNoSceneSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        m.onChoice(p, 0, 0);
        helper.assertTrue(m.quizStep(p.getUUID()) == 1, "quiz should be at step 1");
        hat(p, false);
        m.tick(p);
        helper.assertTrue(!m.hasQuiz(p.getUUID()), "taking the hat off should drop the quiz");
        helper.assertTrue(house(p).isEmpty(), "no house may be saved");
        useStubSeam(helper);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(PensieveSessions.isViewer(p), "re-wearing the hat should replay the memory");
        PensieveSessions.leave(p, ExitReason.LEFT);
        helper.succeedWhen(() -> {
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS hatRemovedMidQuizCancels");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_already_sorted", timeoutTicks = 100)
    public static void sortedPlayerNothingHappens(GameTestHelper helper) {
        useStubSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        net.minecraft.nbt.CompoundTag mod = PlayerMagicData.getModData(p);
        mod.putString(HOUSE_KEY, HogwartsHouse.HUFFLEPUFF.name());
        PlayerMagicData.saveModData(p, mod);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(!PensieveSessions.isViewer(p), "a sorted player must not get the memory");
        helper.assertTrue(!m.hasQuiz(p.getUUID()), "a sorted player must not get the quiz");
        helper.assertTrue(HogwartsHouse.HUFFLEPUFF.name().equals(house(p)), "house changed");
        logout(helper, p);
        LOGGER.info("SORTING-GAMETEST PASS sortedPlayerNothingHappens");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "sorting_yank_replays", timeoutTicks = 400)
    public static void yankReplaysMemory(GameTestHelper helper) {
        useStubSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(PensieveSessions.isViewer(p), "the hat should start the Sorting memory");
        AtomicBoolean left = new AtomicBoolean();
        leaveOncePlaying(helper, p, ExitReason.YANK, left);
        AtomicInteger stage = new AtomicInteger();
        helper.onEachTick(() -> {
            if (stage.get() != 0 || !left.get() || PensieveSessions.isViewer(p)) return;
            // Home after the yank: no quiz, and the next tick starts the memory over.
            helper.assertTrue(!m.hasQuiz(p.getUUID()), "a yank must not open the quiz");
            m.tick(p);
            helper.assertTrue(PensieveSessions.isViewer(p), "the next tick should replay the memory");
            PensieveSessions.leave(p, ExitReason.LEFT);
            stage.set(1);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stage.get() == 1, "not replayed yet");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS yankReplaysMemory");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_forged_choice", timeoutTicks = 100)
    public static void forgedChoiceIgnored(GameTestHelper helper) {
        useNoSceneSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(m.hasQuiz(p.getUUID()) && m.quizStep(p.getUUID()) == 0, "quiz should be at step 0");
        m.onChoice(p, 2, 0);
        m.onChoice(p, 0, 7);
        m.onChoice(p, 0, -1);
        helper.assertTrue(m.quizStep(p.getUUID()) == 0, "a forged choice moved the quiz to " + m.quizStep(p.getUUID()));
        ServerPlayer other = player(helper);
        hat(other, true);
        m.onChoice(other, 0, 0);
        helper.assertTrue(!m.hasQuiz(other.getUUID()), "a choice with no quiz must not start one");
        logout(helper, p);
        logout(helper, other);
        LOGGER.info("SORTING-GAMETEST PASS forgedChoiceIgnored");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "sorting_stale_pending", timeoutTicks = 100)
    public static void stalePendingClears(GameTestHelper helper) {
        useNoSceneSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        // Marked as in the memory, but the Pensieve never had them and no home callback will come.
        m.markMemoryPendingForTests(p.getUUID());
        helper.assertTrue(m.isMemoryPending(p.getUUID()) && !PensieveSessions.isViewer(p), "setup: stale mark");
        m.tick(p);
        helper.assertTrue(!m.isMemoryPending(p.getUUID()), "a stale memory mark must be cleared");
        helper.assertTrue(m.hasQuiz(p.getUUID()), "the cleared player should not be stuck: the quiz should start");
        logout(helper, p);
        LOGGER.info("SORTING-GAMETEST PASS stalePendingClears");
        helper.succeed();
    }
}
