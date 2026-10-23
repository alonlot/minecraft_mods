package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.HungarianHorntailEntity;
import com.hogwartsmagic.entity.MemoryHorntailEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.FirstTaskMemory;
import com.hogwartsmagic.pensieve.MemoryRegistry;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.Session;
import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The First Task memory trigger: who gets it when a Horntail dies, when it starts, how a busy, dead or absent
 * player keeps it pending, and how it ends. The memory level is the PensieveSessions test seam and the scenes are
 * stubs under the shipped ids, so none of this depends on the real scenes. Every test has its own batch: the seam
 * and the trigger's maps are global.
 */
@GameTestHolder(ExampleMod.MODID)
public final class FirstTaskMemoryGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final String OTHER = "first_task_test_other";

    private FirstTaskMemoryGameTests() {
    }

    private static SceneDef stubScene(String id, int length, String next) {
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone",
                new int[]{0, 0, 0}, new int[]{2, 0, 2})), List.of(), List.of(), 6000L, 20);
        return new SceneDef(id, "Test", length, 8, set, Map.of(), List.of(), List.of(), List.of(),
                CameraTrack.EMPTY, "wake", 0, next);
    }

    /** The seam with {@code first_task -> first_task_egg}, a free-standing {@link #OTHER}, and a clean trigger. */
    private static void seam(GameTestHelper helper, int length, boolean withScene) {
        FirstTaskMemory.reset();
        String json = "{\"actors\":[]}";
        PensieveSessions.setBuildBudgetForTests(-1);
        PensieveSessions.overrideForTests(helper.getLevel(), id -> {
            if (withScene && FirstTaskMemory.SCENE.equals(id)) {
                return Optional.of(new MemoryRegistry.LoadedScene(
                        stubScene(id, length, FirstTaskMemory.LAST), json));
            }
            if (withScene && FirstTaskMemory.LAST.equals(id)) {
                return Optional.of(new MemoryRegistry.LoadedScene(stubScene(id, length, null), json));
            }
            if (OTHER.equals(id)) {
                return Optional.of(new MemoryRegistry.LoadedScene(stubScene(id, 400, null), json));
            }
            return Optional.empty();
        });
    }

    /** A mock player in the test area (each has its own random id), with a clean save. */
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0f, 0f);
        p.getPersistentData().remove("PlayerPersisted");
        return p;
    }

    private static HungarianHorntailEntity dragon(GameTestHelper helper) {
        HungarianHorntailEntity d = new HungarianHorntailEntity(ModEntities.HUNGARIAN_HORNTAIL.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        d.setPos(at.x, at.y, at.z);
        d.setNoAi(true);
        d.setPersistenceRequired();
        helper.getLevel().addFreshEntity(d);
        return d;
    }

    /** A real hit through the damage event: {@code source} against the dragon, past her invulnerability frames. */
    private static void hit(GameTestHelper helper, HungarianHorntailEntity d, DamageSource source) {
        d.invulnerableTime = 0;
        helper.assertTrue(d.hurt(source, 1.0F), "the dragon should take the hit");
    }

    /** The kill: health to zero, then the vanilla death, which posts the death event. */
    private static long kill(GameTestHelper helper, HungarianHorntailEntity d, DamageSource source) {
        long at = helper.getLevel().getGameTime();
        d.setHealth(0.0F);
        d.die(source);
        return at;
    }

    private static void logout(GameTestHelper helper, ServerPlayer p) {
        helper.getLevel().getServer().getPlayerList().remove(p);
        FirstTaskMemory.onLogout(p);
    }

    /** The player list's own list (getPlayers() is a read-only view), so a test can put a player back on login. */
    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> backingList(GameTestHelper helper, ServerPlayer p) {
        var list = helper.getLevel().getServer().getPlayerList();
        for (java.lang.reflect.Field f : net.minecraft.server.players.PlayerList.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                Object v = f.get(list);
                if (v instanceof java.util.ArrayList<?> a && a.contains(p)) return (List<ServerPlayer>) a;
            } catch (ReflectiveOperationException | RuntimeException e) {
                // try the next field
            }
        }
        throw new IllegalStateException("PlayerList has no backing player list");
    }

    private static boolean inChain(ServerPlayer p) {
        Optional<Session> s = PensieveSessions.sessionOf(p);
        return s.isPresent() && FirstTaskMemory.SCENE.equals(s.get().chainRoot());
    }

    private static boolean playing(ServerPlayer p) {
        var s = PensieveSessions.sessionOf(p);
        if (s.isEmpty() || s.get().phase() != Session.Phase.PLAY || !inChain(p)) return false;
        var v = s.get().viewers().get(p.getUUID());
        return v != null && v.sawStart();
    }

    private static AtomicBoolean oncePlaying(GameTestHelper helper, ServerPlayer p, Runnable act) {
        AtomicBoolean done = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (done.get() || !playing(p)) return;
            done.set(true);
            act.run();
        });
        return done;
    }

    private static void pass(String name) {
        LOGGER.info("FIRSTTASKMEMORY-GAMETEST PASS {}", name);
    }

    /** After {@code ticks}, asserts the players were not started and the pending flag is as given, then ends. */
    private static void nobodyAfter(GameTestHelper helper, int ticks, String name, Object... playersAndPending) {
        helper.runAfterDelay(ticks, () -> {
            for (int i = 0; i < playersAndPending.length; i += 2) {
                ServerPlayer p = (ServerPlayer) playersAndPending[i];
                boolean pending = (Boolean) playersAndPending[i + 1];
                helper.assertTrue(!PensieveSessions.isViewer(p), "should not have been started");
                helper.assertTrue(FirstTaskMemory.isPending(p) == pending, "pending should be " + pending);
                logout(helper, p);
            }
            pass(name);
            helper.succeed();
        });
    }

    // ---- Who gets it -------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "first_task_kill", timeoutTicks = 200)
    public static void killStartsAfterDeathAnimation(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        AtomicLong killed = new AtomicLong(-1);
        killed.set(kill(helper, d, d.damageSources().playerAttack(p)));
        helper.assertTrue(FirstTaskMemory.isPending(p), "the kill makes the damager pending");
        helper.onEachTick(() -> {
            long age = helper.getLevel().getGameTime() - killed.get();
            if (age < 20) helper.assertTrue(!PensieveSessions.isViewer(p), "not before the death animation is done: " + age);
            if (age > 24) helper.assertTrue(PensieveSessions.isViewer(p), "should have started by now");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getGameTime() - killed.get() >= 21, "too early to judge");
            helper.assertTrue(inChain(p), "the chain begins at first_task");
            helper.assertTrue(!FirstTaskMemory.isPending(p) && !FirstTaskMemory.isSeen(p),
                    "no longer pending, and not seen until it is watched");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("killStartsAfterDeathAnimation");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_both", timeoutTicks = 200)
    public static void bothDamagersGetIt(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer a = player(helper);
        ServerPlayer b = player(helper);
        ServerPlayer bystander = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(a));
        hit(helper, d, d.damageSources().playerAttack(b));
        kill(helper, d, d.damageSources().playerAttack(b));
        helper.assertTrue(!FirstTaskMemory.isPending(bystander), "a player who never hit her is not pending");
        helper.succeedWhen(() -> {
            helper.assertTrue(inChain(a) && inChain(b), "both damagers are inside");
            helper.assertTrue(!PensieveSessions.isViewer(bystander), "the bystander is not");
            PensieveSessions.leave(a, ExitReason.ABORT);
            PensieveSessions.leave(b, ExitReason.ABORT);
            logout(helper, a);
            logout(helper, b);
            logout(helper, bystander);
            pass("bothDamagersGetIt");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_far", timeoutTicks = 200)
    public static void damagerOver64BlocksDoesNot(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer near = player(helper);
        ServerPlayer far = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(near));
        hit(helper, d, d.damageSources().playerAttack(far));
        far.setPos(d.getX() + 70.0, d.getY(), d.getZ());
        kill(helper, d, d.damageSources().playerAttack(near));
        helper.assertTrue(!FirstTaskMemory.isPending(far), "70 blocks away is out of range");
        helper.succeedWhen(() -> {
            helper.assertTrue(inChain(near), "the near damager is inside");
            helper.assertTrue(!PensieveSessions.isViewer(far) && !FirstTaskMemory.isPending(far), "the far one is not");
            PensieveSessions.leave(near, ExitReason.ABORT);
            logout(helper, near);
            logout(helper, far);
            pass("damagerOver64BlocksDoesNot");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_old", timeoutTicks = 200)
    public static void damageOlderThan60sDoesNot(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer old = player(helper);
        ServerPlayer fresh = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        long now = helper.getLevel().getGameTime();
        FirstTaskMemory.recordDamage(d, old, now - FirstTaskMemory.WINDOW_TICKS - 1);
        FirstTaskMemory.recordDamage(d, fresh, now - FirstTaskMemory.WINDOW_TICKS + 5);
        kill(helper, d, d.damageSources().fellOutOfWorld());
        helper.assertTrue(!FirstTaskMemory.isPending(old), "a hit 61 s before the kill does not count");
        helper.assertTrue(FirstTaskMemory.isPending(fresh), "a hit just inside the minute does");
        helper.succeedWhen(() -> {
            helper.assertTrue(inChain(fresh) && !PensieveSessions.isViewer(old), "only the fresh damager is inside");
            PensieveSessions.leave(fresh, ExitReason.ABORT);
            logout(helper, old);
            logout(helper, fresh);
            pass("damageOlderThan60sDoesNot");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_wolf", timeoutTicks = 200)
    public static void wolfKillCountsForDamagers(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        Wolf wolf = new Wolf(EntityType.WOLF, helper.getLevel());
        wolf.setPos(d.getX() + 2, d.getY(), d.getZ());
        helper.getLevel().addFreshEntity(wolf);
        kill(helper, d, d.damageSources().mobAttack(wolf));
        helper.assertTrue(FirstTaskMemory.isPending(p), "a wolf got the kill, the damager still counts");
        helper.succeedWhen(() -> {
            helper.assertTrue(inChain(p), "started");
            wolf.discard();
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("wolfKillCountsForDamagers");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_fire", timeoutTicks = 200)
    public static void fireKillCountsForDamagers(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().onFire());
        helper.assertTrue(FirstTaskMemory.isPending(p), "fire got the kill, the damager still counts");
        helper.succeedWhen(() -> {
            helper.assertTrue(inChain(p), "started");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("fireKillCountsForDamagers");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_spell", timeoutTicks = 200)
    public static void spellDamageCountsCaster(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        // An arrow (or a spell bolt) is the direct entity; the shooter or caster is the cause the hook reads.
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        hit(helper, d, d.damageSources().arrow(arrow, p));
        helper.assertTrue(FirstTaskMemory.hasRecord(p.getUUID()), "the shooter is recorded");
        kill(helper, d, d.damageSources().generic());
        helper.succeedWhen(() -> {
            helper.assertTrue(inChain(p), "the caster gets it");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("spellDamageCountsCaster");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_pet", timeoutTicks = 100)
    public static void petDamageDoesNotCountForOwner(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        Wolf wolf = new Wolf(EntityType.WOLF, helper.getLevel());
        wolf.setPos(d.getX() + 2, d.getY(), d.getZ());
        wolf.tame(p);
        helper.getLevel().addFreshEntity(wolf);
        hit(helper, d, d.damageSources().mobAttack(wolf));
        helper.assertTrue(!FirstTaskMemory.hasRecord(p.getUUID()), "the owner is not recorded for the pet's bite");
        kill(helper, d, d.damageSources().mobAttack(wolf));
        wolf.discard();
        nobodyAfter(helper, 40, "petDamageDoesNotCountForOwner", p, false);
    }

    @GameTest(template = EMPTY, batch = "first_task_puppet", timeoutTicks = 100)
    public static void memoryPuppetIsNeverRecorded(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        MemoryHorntailEntity puppet = new MemoryHorntailEntity(ModEntities.MEMORY_HORNTAIL.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        puppet.setPos(at.x, at.y, at.z);
        FirstTaskMemory.recordDamage(puppet, p, helper.getLevel().getGameTime());
        helper.assertTrue(!FirstTaskMemory.hasRecord(p.getUUID()), "no record against a puppet");
        FirstTaskMemory.onDeath(puppet);
        // And through the real events, which match every HungarianHorntailEntity subclass.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDamageEvent(
                puppet, puppet.damageSources().playerAttack(p), 5.0F));
        helper.assertTrue(!FirstTaskMemory.hasRecord(p.getUUID()), "the damage event records nothing for a puppet");
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(
                puppet, puppet.damageSources().playerAttack(p)));
        nobodyAfter(helper, 40, "memoryPuppetIsNeverRecorded", p, false);
    }

    @GameTest(template = EMPTY, batch = "first_task_unsorted", timeoutTicks = 200)
    public static void unsortedPlayerGetsIt(GameTestHelper helper) {
        // The mock player has no house at all: there is no gate on the house.
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().playerAttack(p));
        helper.succeedWhen(() -> {
            helper.assertTrue(inChain(p), "an unsorted player gets it too");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("unsortedPlayerGetsIt");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_seen", timeoutTicks = 100)
    public static void seenPlayerDoesNotReplay(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        FirstTaskMemory.markSeen(p);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().playerAttack(p));
        nobodyAfter(helper, 50, "seenPlayerDoesNotReplay", p, false);
    }

    // ---- Waiting -----------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "first_task_busy", timeoutTicks = 400)
    public static void inMemoryWaitsThenStarts(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        helper.assertTrue(PensieveSessions.startSolo(p, OTHER), "another memory starts");
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().playerAttack(p));
        AtomicBoolean left = new AtomicBoolean();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(PensieveSessions.isViewer(p) && !inChain(p), "still inside the other memory");
            helper.assertTrue(FirstTaskMemory.isPending(p), "and still pending");
            left.set(true);
            PensieveSessions.leave(p, ExitReason.ABORT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get(), "still waiting");
            helper.assertTrue(inChain(p), "free now: the First Task begins");
            helper.assertTrue(!FirstTaskMemory.isPending(p), "no longer pending");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("inMemoryWaitsThenStarts");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_wake", timeoutTicks = 400)
    public static void wakeOfAnotherMemoryWaits(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        long wakeAt = helper.getLevel().getGameTime();
        PensieveSessions.noteWakeForTests(p.getUUID(), wakeAt);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().playerAttack(p));
        helper.onEachTick(() -> {
            long age = helper.getLevel().getGameTime() - wakeAt;
            if (age < PensieveSessions.WAKE_DONE_TICKS) {
                helper.assertTrue(!PensieveSessions.isViewer(p), "never inside another memory's wake: " + age);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().getGameTime() - wakeAt >= PensieveSessions.WAKE_DONE_TICKS, "wake not over");
            helper.assertTrue(inChain(p), "the wake is over: it begins");
            helper.assertTrue(!FirstTaskMemory.isPending(p), "no longer pending");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("wakeOfAnotherMemoryWaits");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_dead", timeoutTicks = 400)
    public static void deadAtKillStartsAfterRespawn(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        // Dead at the kill (the same player object; a real respawn copies PlayerPersisted across).
        p.setHealth(0.0F);
        helper.assertTrue(!p.isAlive(), "the player is dead");
        kill(helper, d, d.damageSources().fellOutOfWorld());
        helper.assertTrue(FirstTaskMemory.isPending(p), "a dead damager is still pending");
        AtomicBoolean back = new AtomicBoolean();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!PensieveSessions.isViewer(p) && FirstTaskMemory.isPending(p), "nothing starts for the dead");
            back.set(true);
            p.setHealth(p.getMaxHealth());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(back.get(), "still dead");
            helper.assertTrue(inChain(p), "alive again: it begins");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("deadAtKillStartsAfterRespawn");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_logout", timeoutTicks = 500)
    public static void logoutKeepsPendingStartsAfterLogin(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        List<ServerPlayer> backing = backingList(helper, p);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().playerAttack(p));
        helper.assertTrue(FirstTaskMemory.isPending(p), "pending");
        // Gone before the 21 ticks are up.
        logout(helper, p);
        AtomicLong loggedIn = new AtomicLong(-1);
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(!PensieveSessions.isViewer(p), "an absent player is not started");
            helper.assertTrue(FirstTaskMemory.isPending(p), "the pending flag is saved with them");
            backing.add(p);
            // The removal also took the entity out of its level: a real login builds a new one.
            try {
                var m = net.minecraft.world.entity.Entity.class.getDeclaredMethod("unsetRemoved");
                m.setAccessible(true);
                m.invoke(p);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            helper.getLevel().addNewPlayer(p);
            FirstTaskMemory.onLogin(p);
            loggedIn.set(helper.getLevel().getGameTime());
        });
        helper.onEachTick(() -> {
            if (loggedIn.get() < 0) return;
            long age = helper.getLevel().getGameTime() - loggedIn.get();
            if (age < FirstTaskMemory.LOGIN_DELAY) helper.assertTrue(!PensieveSessions.isViewer(p), "after the login delay: " + age);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(loggedIn.get() >= 0 && helper.getLevel().getGameTime() - loggedIn.get() >= FirstTaskMemory.LOGIN_DELAY,
                    "not logged in long enough");
            helper.assertTrue(inChain(p), "back online: the memory begins");
            helper.assertTrue(!FirstTaskMemory.isPending(p), "no longer pending");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("logoutKeepsPendingStartsAfterLogin");
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_missing", timeoutTicks = 200)
    public static void missingSceneKeepsPending(GameTestHelper helper) {
        seam(helper, 400, false);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().playerAttack(p));
        nobodyAfter(helper, 60, "missingSceneKeepsPending", p, true);
    }

    // ---- Endings -----------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "first_task_end", timeoutTicks = 800)
    public static void endSetsSeen(GameTestHelper helper) {
        seam(helper, 40, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().playerAttack(p));
        AtomicBoolean went = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (PensieveSessions.isViewer(p)) {
                went.set(true);
                helper.assertTrue(!FirstTaskMemory.isSeen(p), "not seen while it plays");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(went.get() && !PensieveSessions.isViewer(p), "not home yet");
            helper.assertTrue(PensieveSessions.lastWakeForTests(p.getUUID()) >= 0, "the chain ends in a wake");
            helper.assertTrue(FirstTaskMemory.isSeen(p), "END in the last scene is the memory watched");
            helper.assertTrue(!FirstTaskMemory.isPending(p), "and not pending");
            logout(helper, p);
            pass("endSetsSeen");
        });
    }

    private static void leaveEarly(GameTestHelper helper, ExitReason reason, boolean inLastScene, String name) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d = dragon(helper);
        hit(helper, d, d.damageSources().playerAttack(p));
        kill(helper, d, d.damageSources().playerAttack(p));
        AtomicBoolean left = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (left.get() || !playing(p)) return;
            if (inLastScene) {
                // Hand the viewer to the last scene, as the chain does, then leave from inside it.
                if (!PensieveSessions.sessionOf(p).get().sceneId().equals(FirstTaskMemory.LAST)) {
                    PensieveSessions.handOver(p, FirstTaskMemory.LAST);
                    return;
                }
            }
            left.set(true);
            PensieveSessions.leave(p, reason);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get() && !PensieveSessions.isViewer(p), "not home yet");
            helper.assertTrue(!FirstTaskMemory.isSeen(p), "an early exit keeps it unseen");
            helper.assertTrue(!FirstTaskMemory.isPending(p), "and it is not pending: the next kill plays it again");
            logout(helper, p);
            pass(name);
        });
    }

    @GameTest(template = EMPTY, batch = "first_task_leave", timeoutTicks = 400)
    public static void leaveEarlyKeepsUnseenAndNotPending(GameTestHelper helper) {
        leaveEarly(helper, ExitReason.LEFT, false, "leaveEarlyKeepsUnseenAndNotPending");
    }

    @GameTest(template = EMPTY, batch = "first_task_leave_last", timeoutTicks = 400)
    public static void leaveInLastSceneKeepsUnseen(GameTestHelper helper) {
        leaveEarly(helper, ExitReason.LEFT, true, "leaveInLastSceneKeepsUnseen");
    }

    @GameTest(template = EMPTY, batch = "first_task_yank", timeoutTicks = 400)
    public static void yankKeepsUnseen(GameTestHelper helper) {
        leaveEarly(helper, ExitReason.YANK, false, "yankKeepsUnseen");
    }

    @GameTest(template = EMPTY, batch = "first_task_replay", timeoutTicks = 600)
    public static void nextKillAfterEarlyLeavePlaysAgain(GameTestHelper helper) {
        seam(helper, 400, true);
        ServerPlayer p = player(helper);
        HungarianHorntailEntity d1 = dragon(helper);
        hit(helper, d1, d1.damageSources().playerAttack(p));
        kill(helper, d1, d1.damageSources().playerAttack(p));
        AtomicBoolean second = new AtomicBoolean();
        oncePlaying(helper, p, () -> PensieveSessions.leave(p, ExitReason.LEFT));
        helper.onEachTick(() -> {
            if (second.get() || PensieveSessions.isViewer(p) || FirstTaskMemory.isPending(p)) return;
            second.set(true);
            HungarianHorntailEntity d2 = dragon(helper);
            hit(helper, d2, d2.damageSources().playerAttack(p));
            kill(helper, d2, d2.damageSources().playerAttack(p));
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second.get() && inChain(p), "the next kill plays it again");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("nextKillAfterEarlyLeavePlaysAgain");
        });
    }
}
