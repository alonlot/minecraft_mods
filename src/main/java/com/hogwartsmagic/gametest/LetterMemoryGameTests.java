package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.item.HogwartsInviteItem;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.LetterMemory;
import com.hogwartsmagic.pensieve.MemoryRegistry;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.Session;
import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Letter memory trigger: the once-only flag, the old-save path, what each way home does to the flag and when the
 * letter screen opens. The memory level is the PensieveSessions test seam (the test level stands in for it) and the
 * scene is a stub under the shipped id {@code letter}, so none of this depends on the real scenes. Every test has its
 * own batch: the seam is global.
 */
@GameTestHolder(ExampleMod.MODID)
public final class LetterMemoryGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";

    private LetterMemoryGameTests() {
    }

    private static SceneDef stubScene(String id, int length) {
        return stubScene(id, length, null);
    }

    private static SceneDef stubScene(String id, int length, String next) {
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone",
                new int[]{0, 0, 0}, new int[]{2, 0, 2})), List.of(), List.of(), 6000L, 20);
        return new SceneDef(id, "Test", length, 8, set, Map.of(), List.of(), List.of(), List.of(),
                CameraTrack.EMPTY, "wake", 0, next);
    }

    /** The seam with the chain {@code letter -> next}; {@code withHut} false leaves {@code next} unloadable. */
    private static void useChainSeam(GameTestHelper helper, int length, String next, boolean withNext) {
        String json = "{\"actors\":[]}";
        PensieveSessions.setBuildBudgetForTests(-1);
        PensieveSessions.overrideForTests(helper.getLevel(), id -> {
            if (LetterMemory.SCENE.equals(id)) {
                return Optional.of(new MemoryRegistry.LoadedScene(stubScene(id, length, next), json));
            }
            if (withNext && next.equals(id)) {
                return Optional.of(new MemoryRegistry.LoadedScene(stubScene(id, length), json));
            }
            return Optional.empty();
        });
    }

    /** The seam with a {@code letter} stub of {@code length} ticks ending on a wake, or with no scenes at all. */
    private static void useSeam(GameTestHelper helper, boolean withScene, int length) {
        String json = "{\"actors\":[]}";
        PensieveSessions.setBuildBudgetForTests(-1);
        PensieveSessions.overrideForTests(helper.getLevel(), id -> withScene && LetterMemory.SCENE.equals(id)
                ? Optional.of(new MemoryRegistry.LoadedScene(stubScene(id, length), json))
                : Optional.empty());
    }

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0f, 0f);
        return p;
    }

    private static void logout(GameTestHelper helper, ServerPlayer p) {
        helper.getLevel().getServer().getPlayerList().remove(p);
        LetterMemory.onLogout(p);
    }

    private static ItemStack letter(ServerPlayer p) {
        return HogwartsInviteItem.createFor(p);
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

    @GameTest(template = EMPTY, batch = "letter_pending_starts", timeoutTicks = 100)
    public static void pendingPlayerStartsMemory(GameTestHelper helper) {
        useSeam(helper, true, 400);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "a pending player should start the memory");
        helper.assertTrue(PensieveSessions.isViewer(p), "the memory should have started");
        helper.assertTrue(PensieveSessions.sessionOf(p).get().chainRoot().equals(LetterMemory.SCENE),
                "the chain should begin at the letter scene");
        helper.assertTrue(LetterMemory.isPending(p), "the flag stays until the memory is watched");
        helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 0, "the screen must not open yet");
        PensieveSessions.leave(p, ExitReason.ABORT);
        helper.runAfterDelay(60, () -> {
            logout(helper, p);
            LOGGER.info("LETTERMEMORY-GAMETEST PASS pendingPlayerStartsMemory");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "letter_old_save", timeoutTicks = 100)
    public static void oldSaveOpensLetterDirectly(GameTestHelper helper) {
        useSeam(helper, true, 400);
        ServerPlayer p = player(helper);
        // Mock players share one profile, so a flag set by a concurrent test can show up here: start from a clean save.
        p.getPersistentData().remove("PlayerPersisted");
        helper.assertTrue(!LetterMemory.isPending(p), "an old save has no flag");
        helper.assertTrue(!LetterMemory.tryStart(p, letter(p)), "no flag: the letter opens as usual");
        helper.assertTrue(!PensieveSessions.isViewer(p), "no memory for an old save");
        logout(helper, p);
        LOGGER.info("LETTERMEMORY-GAMETEST PASS oldSaveOpensLetterDirectly");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "letter_scene_missing", timeoutTicks = 100)
    public static void missingSceneOpensLetterAndKeepsPending(GameTestHelper helper) {
        useSeam(helper, false, 400);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(!LetterMemory.tryStart(p, letter(p)), "no scene: the letter opens as usual");
        helper.assertTrue(!PensieveSessions.isViewer(p), "no memory without the scene");
        helper.assertTrue(LetterMemory.isPending(p), "the flag waits for the scene to exist");
        logout(helper, p);
        LOGGER.info("LETTERMEMORY-GAMETEST PASS missingSceneOpensLetterAndKeepsPending");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "letter_end", timeoutTicks = 600)
    public static void memoryEndClearsPendingAndOpensLetter(GameTestHelper helper) {
        useChainSeam(helper, 40, LetterMemory.HUT_SCENE, true);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "the memory should start");
        AtomicBoolean sawHome = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (!sawHome.get() && !PensieveSessions.isViewer(p) && PensieveSessions.lastWakeForTests(p.getUUID()) >= 0) {
                sawHome.set(true);
                helper.assertTrue(!LetterMemory.isPending(p), "the whole memory watched clears the flag");
                helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 0, "the letter waits for the wake");
                helper.assertTrue(LetterMemory.awaitingOpen(p.getUUID()), "the letter should be queued");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sawHome.get(), "not home yet");
            helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 1, "the letter should open once, after the wake");
            helper.assertTrue(!LetterMemory.isPending(p), "the flag stays cleared");
            helper.assertTrue(!LetterMemory.tryStart(p, letter(p)), "afterwards the letter opens as usual");
            logout(helper, p);
            LOGGER.info("LETTERMEMORY-GAMETEST PASS memoryEndClearsPendingAndOpensLetter");
        });
    }

    @GameTest(template = EMPTY, batch = "letter_leave", timeoutTicks = 600)
    public static void leaveEarlyOpensLetterAndKeepsPending(GameTestHelper helper) {
        useSeam(helper, true, 400);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "the memory should start");
        AtomicBoolean left = new AtomicBoolean();
        leaveOncePlaying(helper, p, ExitReason.LEFT, left);
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get(), "not left yet");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(LetterMemory.isPending(p), "leaving early keeps the flag");
            helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 1, "the letter should open once");
            helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "the next use plays the memory again");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            LOGGER.info("LETTERMEMORY-GAMETEST PASS leaveEarlyOpensLetterAndKeepsPending");
        });
    }

    @GameTest(template = EMPTY, batch = "letter_yank", timeoutTicks = 400)
    public static void yankKeepsPending(GameTestHelper helper) {
        useSeam(helper, true, 400);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "the memory should start");
        AtomicBoolean yanked = new AtomicBoolean();
        leaveOncePlaying(helper, p, ExitReason.YANK, yanked);
        helper.succeedWhen(() -> {
            helper.assertTrue(yanked.get(), "not yanked yet");
            helper.assertTrue(!PensieveSessions.isViewer(p), "still a viewer");
            helper.assertTrue(LetterMemory.isPending(p), "a yank keeps the flag");
            helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 1, "the letter should open once");
            logout(helper, p);
            LOGGER.info("LETTERMEMORY-GAMETEST PASS yankKeepsPending");
        });
    }

    @GameTest(template = EMPTY, batch = "letter_reuse", timeoutTicks = 100)
    public static void secondUseWhileInMemoryIgnored(GameTestHelper helper) {
        useSeam(helper, true, 400);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "the memory should start");
        Session first = PensieveSessions.sessionOf(p).get();
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "a second use is swallowed");
        helper.assertTrue(PensieveSessions.sessionOf(p).get() == first, "no second memory");
        helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 0, "the screen must not open underneath");
        PensieveSessions.leave(p, ExitReason.ABORT);
        helper.runAfterDelay(60, () -> {
            logout(helper, p);
            LOGGER.info("LETTERMEMORY-GAMETEST PASS secondUseWhileInMemoryIgnored");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "letter_first_join", timeoutTicks = 100)
    public static void firstJoinLeavesTagSet(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        p.getPersistentData().remove("PlayerPersisted");
        com.hogwartsmagic.event.ModEvents.onPlayerJoin(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent(p));
        helper.assertTrue(LetterMemory.isPending(p), "the real first login must leave the flag set");
        logout(helper, p);
        LOGGER.info("LETTERMEMORY-GAMETEST PASS firstJoinLeavesTagSet");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "letter_early_end", timeoutTicks = 600)
    public static void earlyEndKeepsPending(GameTestHelper helper) {
        useChainSeam(helper, 40, LetterMemory.HUT_SCENE, false);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "the memory should start");
        helper.succeedWhen(() -> {
            helper.assertTrue(!PensieveSessions.isViewer(p) && LetterMemory.openedCount(p.getUUID()) == 1,
                    "the chain should have ended early and opened the letter");
            helper.assertTrue(LetterMemory.isPending(p), "an end that never reached letter_hut keeps the flag");
            logout(helper, p);
            LOGGER.info("LETTERMEMORY-GAMETEST PASS earlyEndKeepsPending");
        });
    }

    @GameTest(template = EMPTY, batch = "letter_death", timeoutTicks = 400)
    public static void deathOpensLetterAfterRespawn(GameTestHelper helper) {
        useSeam(helper, true, 400);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "the memory should start");
        AtomicBoolean died = new AtomicBoolean();
        helper.onEachTick(() -> {
            var s = PensieveSessions.sessionOf(p);
            if (died.get() || s.isEmpty() || s.get().phase() != Session.Phase.PLAY) return;
            died.set(true);
            p.die(p.damageSources().generic());
        });
        AtomicBoolean respawned = new AtomicBoolean();
        helper.succeedWhen(() -> {
            helper.assertTrue(died.get(), "not dead yet");
            helper.assertTrue(!LetterMemory.hasRecipient(p.getUUID()), "death must drop the recipient");
            helper.assertTrue(LetterMemory.isPending(p), "death keeps the flag");
            if (!respawned.get()) {
                helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 0, "no letter before the respawn");
                respawned.set(true);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
                        new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p, false));
            }
            helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 1, "the letter opens once after respawn");
            logout(helper, p);
            LOGGER.info("LETTERMEMORY-GAMETEST PASS deathOpensLetterAfterRespawn");
        });
    }

    @GameTest(template = EMPTY, batch = "letter_logout", timeoutTicks = 100)
    public static void logoutDropsRecipientAndKeepsPending(GameTestHelper helper) {
        useSeam(helper, true, 400);
        ServerPlayer p = player(helper);
        LetterMemory.markPending(p);
        helper.assertTrue(LetterMemory.tryStart(p, letter(p)), "the memory should start");
        helper.assertTrue(LetterMemory.hasRecipient(p.getUUID()), "the recipient is held while inside");
        com.hogwartsmagic.event.ModEvents.onPlayerLogout(
                new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
        helper.assertTrue(!LetterMemory.hasRecipient(p.getUUID()), "logout must drop the recipient");
        helper.assertTrue(LetterMemory.isPending(p), "logout keeps the flag");
        helper.assertTrue(LetterMemory.openedCount(p.getUUID()) == 0, "no letter is opened on a disconnect");
        helper.getLevel().getServer().getPlayerList().remove(p);
        LOGGER.info("LETTERMEMORY-GAMETEST PASS logoutDropsRecipientAndKeepsPending");
        helper.succeed();
    }
}
