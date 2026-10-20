package com.hogwartsmagic.gametest;

import com.google.gson.JsonParser;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.MemoryRegistry;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.Session;
import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Sorting Hat flow: the hat plays the Sorting memory, then the fixed quiz sorts the player. The memory level is
 * PensieveSessions' test seam (the test level stands in for it) and the shipped {@code sorting.json} is the scene.
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
            // Home after the yank: no quiz, and the memory does not re-dive the hurt player while the hat stays on.
            helper.assertTrue(!m.hasQuiz(p.getUUID()), "a yank must not open the quiz");
            m.tick(p);
            m.tick(p);
            helper.assertTrue(!PensieveSessions.isViewer(p), "a yank must not replay the memory until the hat is re-worn");
            helper.assertTrue(!m.hasQuiz(p.getUUID()), "no quiz while waiting for the hat to be re-worn");
            // Off and on again: now it replays.
            hat(p, false);
            m.tick(p);
            hat(p, true);
            m.tick(p);
            helper.assertTrue(PensieveSessions.isViewer(p), "re-wearing the hat should replay the memory");
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

    @GameTest(template = EMPTY, batch = "sorting_yank_logout", timeoutTicks = 400)
    public static void yankRewearFlagClearsOnLogout(GameTestHelper helper) {
        useStubSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(PensieveSessions.isViewer(p), "the hat should start the Sorting memory");
        AtomicBoolean left = new AtomicBoolean();
        leaveOncePlaying(helper, p, ExitReason.YANK, left);
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get() && !PensieveSessions.isViewer(p), "not home from the yank yet");
            helper.assertTrue(m.needsRewear(p.getUUID()), "a yank should wait for the hat to be re-worn");
            m.onLogout(p);
            helper.assertTrue(!m.needsRewear(p.getUUID()), "logout should clear the re-wear wait");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS yankRewearFlagClearsOnLogout");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_dead_player", timeoutTicks = 100)
    public static void deadPlayerNoMemory(GameTestHelper helper) {
        useStubSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        // Dead on the death screen (keepInventory: the hat is still worn), with no death event in between.
        p.setHealth(0f);
        helper.assertTrue(!p.isAlive(), "setup: the player should be dead");
        m.tick(p);
        helper.assertTrue(!PensieveSessions.isViewer(p), "a dead player must not be sent into the memory");
        helper.assertTrue(!m.hasQuiz(p.getUUID()) && !m.isMemoryPending(p.getUUID()), "a dead player starts nothing");
        helper.assertTrue(!PensieveSessions.startSolo(p, SortingHatManager.MEMORY_SCENE),
                "startSolo must refuse a dead player");
        helper.assertTrue(!PensieveSessions.isViewer(p), "still not a viewer");
        logout(helper, p);
        LOGGER.info("SORTING-GAMETEST PASS deadPlayerNoMemory");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "sorting_hat_off_mid_memory", timeoutTicks = 400)
    public static void hatRemovedMidMemoryAborts(GameTestHelper helper) {
        useStubSeam(helper);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(PensieveSessions.isViewer(p) && m.isMemoryPending(p.getUUID()), "the memory should start");
        hat(p, false);
        m.tick(p);
        helper.assertTrue(!m.isMemoryPending(p.getUUID()), "taking the hat off should drop the memory mark");
        var v = PensieveSessions.sessionOf(p).map(s -> s.viewers().get(p.getUUID())).orElse(null);
        helper.assertTrue(v != null && v.exitReason() == ExitReason.ABORT, "the memory should be aborted");
        AtomicInteger stage = new AtomicInteger();
        helper.onEachTick(() -> {
            if (stage.get() != 0 || PensieveSessions.isViewer(p)) return;
            helper.assertTrue(!m.hasQuiz(p.getUUID()), "an aborted memory must not open the quiz");
            hat(p, true);
            m.tick(p);
            helper.assertTrue(PensieveSessions.isViewer(p), "putting the hat back on should replay the memory");
            PensieveSessions.leave(p, ExitReason.LEFT);
            stage.set(1);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(stage.get() == 1, "not replayed yet");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS hatRemovedMidMemoryAborts");
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

    // ---- Sorting inside the mind (Great Hall plan, Addendum 5 K) ---------------------------------------------
    // A tiny chain stands in for the shipped scenes under the shipped ids: "sorting" (next: sorting_choice), the
    // hold scene sorting_choice, and the four sorting_reveal_<house> scenes, which end in a wake.

    private static SceneDef tinyScene(String id, int length, String next, String endAction) {
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone",
                new int[]{0, 0, 0}, new int[]{2, 0, 2})), List.of(), List.of(), 6000L, 20);
        return new SceneDef(id, "Test", length, 8, set, Map.of(), List.of(), List.of(), List.of(), CameraTrack.EMPTY,
                endAction, 0, next);
    }

    /** The mind flow: the hall scene runs {@code hallLength}, the choice scene {@code choiceLength}. */
    private static void useMindSeam(GameTestHelper helper, int hallLength, int choiceLength) {
        Map<String, SceneDef> scenes = new HashMap<>();
        scenes.put(SortingHatManager.MEMORY_SCENE, tinyScene(SortingHatManager.MEMORY_SCENE, hallLength,
                SortingHatManager.CHOICE_SCENE, null));
        scenes.put(SortingHatManager.CHOICE_SCENE, tinyScene(SortingHatManager.CHOICE_SCENE, choiceLength, null, null));
        for (HogwartsHouse h : HogwartsHouse.values()) {
            String id = SortingHatManager.revealScene(h);
            scenes.put(id, tinyScene(id, 40, null, "wake"));
        }
        PensieveSessions.setBuildBudgetForTests(-1);
        PensieveSessions.overrideForTests(helper.getLevel(), id -> Optional.ofNullable(scenes.get(id))
                .map(d -> new MemoryRegistry.LoadedScene(d, "{}")));
    }

    /** The session the player watches, if it is playing {@code sceneId}. */
    private static Optional<Session> playing(ServerPlayer p, String sceneId) {
        return PensieveSessions.sessionOf(p)
                .filter(s -> sceneId.equals(s.sceneId()) && s.phase() == Session.Phase.PLAY);
    }

    @GameTest(template = EMPTY, batch = "sorting_mind_quiz", timeoutTicks = 400)
    public static void enteringChoiceOpensQuizInsideMemory(GameTestHelper helper) {
        useMindSeam(helper, 40, 2000);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        helper.assertTrue(PensieveSessions.isViewer(p), "the hat should start the Sorting memory");
        AtomicBoolean entered = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (entered.get()) return;
            if (playing(p, SortingHatManager.MEMORY_SCENE).isPresent()) {
                helper.assertTrue(!m.hasQuiz(p.getUUID()), "no quiz before the choice scene");
            }
            if (playing(p, SortingHatManager.CHOICE_SCENE).isEmpty()) return;
            helper.assertTrue(m.hasQuiz(p.getUUID()) && m.quizStep(p.getUUID()) == 0,
                    "entering the choice scene should open the quiz at step 0");
            helper.assertTrue(PensieveSessions.isViewer(p), "the quiz opens inside the memory");
            entered.set(true);
            // Done: the hat off drops the quiz and ends the memory.
            hat(p, false);
            m.tick(p);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(entered.get(), "never reached the choice scene");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(!m.hasQuiz(p.getUUID()), "the hat off should drop the quiz");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS enteringChoiceOpensQuizInsideMemory");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_mind_reveal", timeoutTicks = 700)
    public static void sortedInMindHandsOverToRevealThenWakesThenReveals(GameTestHelper helper) {
        useMindSeam(helper, 40, 2000);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        AtomicReference<HogwartsHouse> house = new AtomicReference<>();
        AtomicBoolean sawReveal = new AtomicBoolean();
        AtomicBoolean sawHeldAtHome = new AtomicBoolean();
        helper.onEachTick(() -> {
            m.tickReveal(p);
            if (house.get() == null) {
                if (playing(p, SortingHatManager.CHOICE_SCENE).isEmpty() || !m.hasQuiz(p.getUUID())) return;
                answerToTheEnd(m, p);
                helper.assertTrue(!m.hasQuiz(p.getUUID()), "the quiz should be over");
                HogwartsHouse h = HogwartsHouse.valueOf(house(p));
                house.set(h);
                helper.assertTrue(m.heldReveal(p.getUUID()) == h, "the reveal should be held back");
                helper.assertTrue(m.heldRevealDue(p.getUUID()) == -1, "no reveal time while still in the memory");
                helper.assertTrue(m.revealedAt(p.getUUID()) == -1, "no house reveal inside the memory");
                Session s = PensieveSessions.sessionOf(p).orElseThrow();
                helper.assertTrue(s.phase() == Session.Phase.HANDOVER, "SORTED should hand over at once, is "
                        + s.phase());
                helper.assertTrue(s.nextSession() != null
                                && SortingHatManager.revealScene(h).equals(s.nextSession().sceneId()),
                        "the hand-over should go to " + SortingHatManager.revealScene(h));
                return;
            }
            String reveal = SortingHatManager.revealScene(house.get());
            if (playing(p, reveal).isPresent()) {
                sawReveal.set(true);
                helper.assertTrue(PensieveSessions.sessionOf(p).get().chainRoot().equals(SortingHatManager.MEMORY_SCENE),
                        "the reveal belongs to the Sorting chain");
            }
            if (!PensieveSessions.isViewer(p) && PensieveSessions.lastWakeForTests(p.getUUID()) >= 0
                    && m.revealedAt(p.getUUID()) < 0) {
                helper.assertTrue(m.heldReveal(p.getUUID()) == house.get() && m.heldRevealDue(p.getUUID()) > 0,
                        "home again: the reveal waits for the wake");
                sawHeldAtHome.set(true);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sawReveal.get(), "never handed over to the reveal scene");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            long wake = PensieveSessions.lastWakeForTests(p.getUUID());
            helper.assertTrue(wake >= 0, "the reveal end should send the wake");
            long revealed = m.revealedAt(p.getUUID());
            helper.assertTrue(revealed >= 0, "no house reveal yet");
            helper.assertTrue(sawHeldAtHome.get(), "the reveal should be held after the move home");
            helper.assertTrue(revealed - wake >= PensieveSessions.WAKE_DONE_TICKS,
                    "the house reveal came " + (revealed - wake) + " ticks after the wake, before it was done");
            helper.assertTrue(m.heldReveal(p.getUUID()) == null, "the held reveal should be gone");
            helper.assertTrue(house.get().name().equals(house(p)), "the house should stay saved");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS sortedInMindHandsOverToRevealThenWakesThenReveals");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_mind_leave_choice", timeoutTicks = 400)
    public static void leavingDuringChoiceTakesQuizHome(GameTestHelper helper) {
        useMindSeam(helper, 40, 2000);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        AtomicBoolean left = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (left.get() || playing(p, SortingHatManager.CHOICE_SCENE).isEmpty() || !m.hasQuiz(p.getUUID())) return;
            m.onChoice(p, 0, 0);
            helper.assertTrue(m.quizStep(p.getUUID()) == 1, "one answer inside the memory");
            left.set(true);
            PensieveSessions.leave(p, ExitReason.LEFT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get(), "never left the choice scene");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(m.hasQuiz(p.getUUID()) && m.quizStep(p.getUUID()) == 1,
                    "the quiz should carry on at home at step 1, is " + m.quizStep(p.getUUID()));
            helper.assertTrue(!m.needsRewear(p.getUUID()) && house(p).isEmpty(), "leaving is not a yank");
            answerToTheEnd(m, p);
            helper.assertTrue(!house(p).isEmpty(), "answered at home: sorted");
            helper.assertTrue(m.revealedAt(p.getUUID()) >= 0, "sorted at home: revealed at once");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS leavingDuringChoiceTakesQuizHome");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_mind_leave_hall", timeoutTicks = 400)
    public static void leavingBeforeChoiceOpensQuizAtHome(GameTestHelper helper) {
        useMindSeam(helper, 2000, 2000);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        helper.onEachTick(() -> helper.assertTrue(playing(p, SortingHatManager.CHOICE_SCENE).isEmpty(),
                "the choice scene must never be reached"));
        AtomicBoolean left = new AtomicBoolean();
        leaveOncePlaying(helper, p, ExitReason.LEFT, left);
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get() && !PensieveSessions.isViewer(p), "not home yet");
            helper.assertTrue(m.hasQuiz(p.getUUID()) && m.quizStep(p.getUUID()) == 0,
                    "leaving before the choice should open the quiz at home");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS leavingBeforeChoiceOpensQuizAtHome");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_mind_timeout", timeoutTicks = 500)
    public static void choiceTimeoutLoopsWithQuizOpen(GameTestHelper helper) {
        useMindSeam(helper, 40, 40);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        AtomicReference<Session> first = new AtomicReference<>();
        AtomicBoolean looped = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (looped.get()) return;
            Optional<Session> s = playing(p, SortingHatManager.CHOICE_SCENE);
            if (s.isEmpty()) return;
            if (first.get() == null) {
                first.set(s.get());
                m.onChoice(p, 0, 0);
                return;
            }
            if (s.get() == first.get()) return;
            helper.assertTrue(PensieveSessions.isViewer(p), "the hold scene must not end the memory");
            helper.assertTrue(m.hasQuiz(p.getUUID()) && m.quizStep(p.getUUID()) == 1,
                    "the looped scene keeps the quiz where it was, step " + m.quizStep(p.getUUID()));
            helper.assertTrue(s.get().chainRoot().equals(SortingHatManager.MEMORY_SCENE), "same chain");
            looped.set(true);
            hat(p, false);
            m.tick(p);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(looped.get(), "the choice scene never looped");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS choiceTimeoutLoopsWithQuizOpen");
        });
    }

    @GameTest(template = EMPTY, batch = "sorting_mind_yank", timeoutTicks = 400)
    public static void yankDuringChoiceDropsQuiz(GameTestHelper helper) {
        useMindSeam(helper, 40, 2000);
        SortingHatManager m = SortingHatManager.get();
        ServerPlayer p = player(helper);
        hat(p, true);
        m.tick(p);
        AtomicBoolean yanked = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (yanked.get() || playing(p, SortingHatManager.CHOICE_SCENE).isEmpty() || !m.hasQuiz(p.getUUID())) return;
            yanked.set(true);
            PensieveSessions.leave(p, ExitReason.YANK);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(yanked.get() && !PensieveSessions.isViewer(p), "not home from the yank yet");
            helper.assertTrue(!m.hasQuiz(p.getUUID()), "a yank closes the quiz, as before");
            helper.assertTrue(m.needsRewear(p.getUUID()), "a yank waits for the hat to be re-worn");
            helper.assertTrue(house(p).isEmpty(), "nothing saved");
            logout(helper, p);
            LOGGER.info("SORTING-GAMETEST PASS yankDuringChoiceDropsQuiz");
        });
    }
}
