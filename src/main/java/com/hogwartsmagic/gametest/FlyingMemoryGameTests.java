package com.hogwartsmagic.gametest;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.entity.BroomEntity;
import com.hogwartsmagic.entity.ModEntities;
import com.hogwartsmagic.entity.WizardEntity;
import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.FlyingMemory;
import com.hogwartsmagic.pensieve.MemoryRegistry;
import com.hogwartsmagic.pensieve.PensieveSessions;
import com.hogwartsmagic.pensieve.Session;
import com.hogwartsmagic.pensieve.scene.CameraTrack;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.sorting.HogwartsHouse;
import com.hogwartsmagic.util.PlayerMagicData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The Flying memory trigger: the first mount of a sorted player, the broom parked and held while they are away,
 * and the way home from every exit. The memory level is the PensieveSessions test seam and the scenes are stubs
 * under the shipped ids, so none of this depends on the real scenes. Every test has its own batch: the seam is
 * global.
 */
@GameTestHolder(ExampleMod.MODID)
public final class FlyingMemoryGameTests {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final String EMPTY = "hogwartsmagic:gametest/empty3";
    private static final HogwartsHouse HOUSE = HogwartsHouse.GRYFFINDOR;
    private static final String ROOT = FlyingMemory.sceneFor(HOUSE);
    private static final String SNITCH = ROOT + FlyingMemory.SNITCH_SUFFIX;

    private FlyingMemoryGameTests() {
    }

    private static SceneDef stubScene(String id, int length, String next) {
        SceneDef.SetDef set = new SceneDef.SetDef(List.of(), List.of(new SceneDef.Fill("minecraft:stone",
                new int[]{0, 0, 0}, new int[]{2, 0, 2})), List.of(), List.of(), 6000L, 20);
        return new SceneDef(id, "Test", length, 8, set, Map.of(), List.of(), List.of(), List.of(),
                CameraTrack.EMPTY, "wake", 0, next);
    }

    /** The seam with {@code ROOT -> SNITCH}; either scene can be left unloadable. */
    private static void seam(GameTestHelper helper, int rootLength, boolean withRoot, boolean withSnitch) {
        String json = "{\"actors\":[]}";
        PensieveSessions.setBuildBudgetForTests(-1);
        PensieveSessions.overrideForTests(helper.getLevel(), id -> {
            if (withRoot && ROOT.equals(id)) {
                return Optional.of(new MemoryRegistry.LoadedScene(stubScene(id, rootLength, SNITCH), json));
            }
            if (withSnitch && SNITCH.equals(id)) {
                return Optional.of(new MemoryRegistry.LoadedScene(stubScene(id, 40, null), json));
            }
            return Optional.empty();
        });
    }

    private static ServerPlayer player(GameTestHelper helper, boolean sorted) {
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        p.teleportTo(helper.getLevel(), at.x, at.y, at.z, java.util.Set.of(), 0f, 0f);
        // Mock players share one profile: start from a clean save.
        p.getPersistentData().remove("PlayerPersisted");
        if (sorted) sort(p);
        return p;
    }

    private static void sort(net.minecraft.world.entity.player.Player p) {
        net.minecraft.nbt.CompoundTag mod = PlayerMagicData.getModData(p);
        mod.putString("SortingHouse", HOUSE.name());
        PlayerMagicData.saveModData(p, mod);
    }

    private static BroomEntity broom(GameTestHelper helper, Vec3 rel) {
        BroomEntity b = new BroomEntity(ModEntities.BROOM.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(rel);
        b.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(b);
        return b;
    }

    /** The real hook: right-click the broom, as the server handles it. */
    private static void mount(GameTestHelper helper, ServerPlayer p, BroomEntity b) {
        b.interact(p, InteractionHand.MAIN_HAND);
        helper.assertTrue(p.getVehicle() == b, "the player should be on the broom");
    }

    private static void logout(GameTestHelper helper, ServerPlayer p) {
        helper.getLevel().getServer().getPlayerList().remove(p);
        FlyingMemory.onLogout(p);
    }

    private static boolean playing(ServerPlayer p) {
        var s = PensieveSessions.sessionOf(p);
        if (s.isEmpty() || s.get().phase() != Session.Phase.PLAY) return false;
        var v = s.get().viewers().get(p.getUUID());
        return v != null && v.sawStart();
    }

    /** Runs {@code act} on the first tick the player is inside and watching PLAY. */
    private static AtomicBoolean oncePlaying(GameTestHelper helper, ServerPlayer p, Runnable act) {
        AtomicBoolean done = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (done.get() || !playing(p)) return;
            done.set(true);
            act.run();
        });
        return done;
    }

    /** Whether the parked broom's chunk holds the Flying memory's region ticket (read from the distance manager). */
    private static boolean ticket(GameTestHelper helper, BroomEntity b) {
        return FlyingMemory.hasTicket(helper.getLevel(), b.chunkPosition());
    }

    private static void pass(String name) {
        LOGGER.info("FLYINGMEMORY-GAMETEST PASS {}", name);
    }

    // ---- Starting ----------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "flying_first_mount", timeoutTicks = 200)
    public static void firstMountStartsMemory(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        helper.assertTrue(!PensieveSessions.isViewer(p), "the memory starts on the next tick, not inside the click");
        helper.succeedWhen(() -> {
            helper.assertTrue(PensieveSessions.isViewer(p), "the memory should have started");
            helper.assertTrue(PensieveSessions.sessionOf(p).get().chainRoot().equals(ROOT),
                    "the chain should begin at the viewer's house scene");
            helper.assertTrue(p.getVehicle() == null, "the memory dismounts the player");
            helper.assertTrue(b.isHeld(), "the broom should be held");
            helper.assertTrue(ticket(helper, b), "the broom's chunk should be held");
            helper.assertTrue(!FlyingMemory.isSeen(p), "the flag waits for the memory to be watched");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("firstMountStartsMemory");
        });
    }

    @GameTest(template = EMPTY, batch = "flying_unsorted", timeoutTicks = 100)
    public static void unsortedMountDoesNotStart(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, false);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!PensieveSessions.isViewer(p), "an unsorted player just mounts");
            helper.assertTrue(p.getVehicle() == b, "and stays mounted");
            helper.assertTrue(!b.isHeld() && !FlyingMemory.isHolding(p.getUUID()), "nothing is held");
            helper.assertTrue(!FlyingMemory.isSeen(p), "the flag stays unset until they are sorted");
            logout(helper, p);
            pass("unsortedMountDoesNotStart");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "flying_seen", timeoutTicks = 100)
    public static void seenPlayerJustMounts(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        FlyingMemory.markSeen(p);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!PensieveSessions.isViewer(p), "a player who has seen it just mounts");
            helper.assertTrue(p.getVehicle() == b && !b.isHeld(), "they stay on a free broom");
            logout(helper, p);
            pass("seenPlayerJustMounts");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "flying_missing_scene", timeoutTicks = 100)
    public static void missingSceneJustMounts(GameTestHelper helper) {
        seam(helper, 400, false, false);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!PensieveSessions.isViewer(p), "no scene, no memory");
            helper.assertTrue(p.getVehicle() == b && !b.isHeld(), "the player stays on a free broom");
            helper.assertTrue(!ticket(helper, b) && !FlyingMemory.isHolding(p.getUUID()),
                    "a refused start leaves nothing held");
            helper.assertTrue(!FlyingMemory.isSeen(p), "the flag waits for the scene to exist");
            logout(helper, p);
            pass("missingSceneJustMounts");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "flying_wizard", timeoutTicks = 100)
    public static void wizardMountNeverStarts(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        // A Death Eater (WizardEntity) mounts with startRiding(broom, true), never through interact.
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        WizardEntity w = new WizardEntity(ModEntities.WIZARD.get(), helper.getLevel());
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        w.setPos(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(w);
        helper.assertTrue(w.startRiding(b, true), "the wizard should mount");
        // A machine of another mod (a player the server's list does not hold) right-clicking a broom starts nothing.
        ServerPlayer fake = player(helper, true);
        helper.getLevel().getServer().getPlayerList().remove(fake);
        helper.assertTrue(!FlyingMemory.isRealPlayer(fake), "a player outside the list is fake");
        BroomEntity b2 = broom(helper, new Vec3(2.5, 2, 2.5));
        fake.setPos(at.x + 1, at.y, at.z + 1);
        b2.interact(fake, InteractionHand.MAIN_HAND);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!b.isHeld() && !b2.isHeld(), "no broom is held");
            helper.assertTrue(!PensieveSessions.isViewer(p) && !PensieveSessions.isViewer(fake), "nobody is inside");
            helper.assertTrue(!FlyingMemory.isHolding(p.getUUID()) && !FlyingMemory.isHolding(fake.getUUID()),
                    "no memory was begun for anyone");
            w.discard();
            logout(helper, p);
            pass("wizardMountNeverStarts");
            helper.succeed();
        });
    }

    // ---- The parked broom --------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "flying_parked", timeoutTicks = 200)
    public static void parkedBroomHoldsStill(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        // Mounted in the air: with nothing under it the empty broom would fall.
        BroomEntity b = broom(helper, new Vec3(1.5, 6, 1.5));
        Vec3 start = b.position();
        mount(helper, p, b);
        AtomicBoolean held = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (!b.isHeld()) return;
            held.set(true);
            helper.assertTrue(b.position().distanceToSqr(start) < 1.0E-6, "the parked broom must not drift or fall");
            helper.assertTrue(b.getDeltaMovement().lengthSqr() < 1.0E-9, "the parked broom has no motion");
            helper.assertTrue(b.isNoGravity(), "the parked broom has no gravity");
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(held.get(), "the broom was never held");
            helper.assertTrue(ticket(helper, b), "the chunk ticket should be held while away");
            PensieveSessions.leave(p, ExitReason.ABORT);
            helper.runAfterDelay(60, () -> {
                logout(helper, p);
                pass("parkedBroomHoldsStill");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "flying_other_player", timeoutTicks = 200)
    public static void otherPlayerCannotTakeParkedBroom(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        AtomicBoolean checked = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (checked.get() || !b.isHeld()) return;
            checked.set(true);
            ServerPlayer q = helper.makeMockServerPlayerInLevel();
            q.setUUID(UUID.randomUUID());
            Vec3 at = b.position();
            q.setPos(at.x + 1, at.y, at.z);
            b.interact(q, InteractionHand.MAIN_HAND);
            helper.assertTrue(q.getVehicle() == null, "another player must not take the parked broom");
            helper.assertTrue(!q.startRiding(b), "nor by forcing a mount");
            Pig pig = new Pig(net.minecraft.world.entity.EntityType.PIG, helper.getLevel());
            pig.setPos(at.x, at.y, at.z);
            helper.getLevel().addFreshEntity(pig);
            helper.assertTrue(!pig.startRiding(b), "nor can a mob");
            pig.discard();
            helper.getLevel().getServer().getPlayerList().remove(q);
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(checked.get(), "the broom was never held");
            helper.assertTrue(b.getPassengers().isEmpty(), "nobody is on the broom");
            PensieveSessions.leave(p, ExitReason.ABORT);
            helper.runAfterDelay(60, () -> {
                logout(helper, p);
                pass("otherPlayerCannotTakeParkedBroom");
                helper.succeed();
            });
        });
    }

    // ---- Coming home -------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "flying_wake", timeoutTicks = 800)
    public static void wakeRemountsBroom(GameTestHelper helper) {
        seam(helper, 40, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        Vec3 start = b.position();
        mount(helper, p, b);
        AtomicBoolean sawHome = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (sawHome.get() || PensieveSessions.isViewer(p) || PensieveSessions.lastWakeForTests(p.getUUID()) < 0) return;
            sawHome.set(true);
            helper.assertTrue(FlyingMemory.isSeen(p), "a chain ended with END in the snitch scene is the memory watched");
            helper.assertTrue(p.getVehicle() == null, "the broom waits out the wake");
            helper.assertTrue(b.isHeld() && ticket(helper, b), "and stays held until then");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sawHome.get(), "not home yet");
            helper.assertTrue(p.getVehicle() == b, "after the wake the player is back on the broom");
            helper.assertTrue(!b.isHeld() && !ticket(helper, b), "the hold is released");
            helper.assertTrue(b.position().distanceToSqr(start) < 1.0E-6, "the same broom, where it was");
            helper.assertTrue(FlyingMemory.isSeen(p), "the flag stays set");
            logout(helper, p);
            pass("wakeRemountsBroom");
        });
    }

    private static void leaveEarly(GameTestHelper helper, ExitReason reason, String name) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        AtomicBoolean left = oncePlaying(helper, p, () -> PensieveSessions.leave(p, reason));
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get() && !PensieveSessions.isViewer(p), "not home yet");
            helper.assertTrue(p.getVehicle() == b, "home at once, on the same broom");
            helper.assertTrue(!b.isHeld() && !ticket(helper, b), "the hold is released");
            helper.assertTrue(!FlyingMemory.isSeen(p), "an early exit keeps the flag unset");
            logout(helper, p);
            pass(name);
        });
    }

    @GameTest(template = EMPTY, batch = "flying_leave", timeoutTicks = 400)
    public static void leaveEarlyRemountsAndKeepsUnseen(GameTestHelper helper) {
        leaveEarly(helper, ExitReason.LEFT, "leaveEarlyRemountsAndKeepsUnseen");
    }

    @GameTest(template = EMPTY, batch = "flying_yank", timeoutTicks = 400)
    public static void yankKeepsUnseen(GameTestHelper helper) {
        leaveEarly(helper, ExitReason.YANK, "yankKeepsUnseen");
    }

    @GameTest(template = EMPTY, batch = "flying_no_broom", timeoutTicks = 400)
    public static void missingBroomStandsPlayer(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        AtomicBoolean left = oncePlaying(helper, p, () -> {
            b.discard();
            PensieveSessions.leave(p, ExitReason.LEFT);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(left.get() && !PensieveSessions.isViewer(p), "not home yet");
            helper.assertTrue(p.getVehicle() == null, "no broom: the player stands where they mounted");
            helper.assertTrue(!FlyingMemory.isHolding(p.getUUID()) && !ticket(helper, b),
                    "nothing is left held");
            helper.assertTrue(!FlyingMemory.isSeen(p), "the flag stays unset");
            logout(helper, p);
            pass("missingBroomStandsPlayer");
        });
    }

    // ---- Exits -------------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "flying_death", timeoutTicks = 400)
    public static void deathReleasesBroom(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        AtomicBoolean died = oncePlaying(helper, p, () -> p.die(p.damageSources().generic()));
        helper.succeedWhen(() -> {
            helper.assertTrue(died.get(), "not dead yet");
            helper.assertTrue(!b.isHeld(), "death releases the broom");
            helper.assertTrue(!ticket(helper, b) && !FlyingMemory.isHolding(p.getUUID()),
                    "and the chunk ticket");
            helper.assertTrue(p.getVehicle() == null, "no remount after death");
            helper.assertTrue(!FlyingMemory.isSeen(p), "death keeps the flag unset");
            logout(helper, p);
            pass("deathReleasesBroom");
        });
    }

    @GameTest(template = EMPTY, batch = "flying_logout", timeoutTicks = 400)
    public static void disconnectReleasesBroom(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        AtomicBoolean gone = oncePlaying(helper, p, () -> {
            helper.assertTrue(b.isHeld(), "held while inside");
            com.hogwartsmagic.event.ModEvents.onPlayerLogout(
                    new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
            helper.getLevel().getServer().getPlayerList().remove(p);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gone.get(), "not logged out yet");
            helper.assertTrue(!b.isHeld(), "a disconnect releases the broom");
            helper.assertTrue(!ticket(helper, b) && !FlyingMemory.isHolding(p.getUUID()),
                    "and the chunk ticket");
            helper.assertTrue(!FlyingMemory.isSeen(p), "a disconnect keeps the flag unset");
            pass("disconnectReleasesBroom");
        });
    }

    // ---- Re-entry ----------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "flying_reentry", timeoutTicks = 200)
    public static void secondMountWhileInMemoryIgnored(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        BroomEntity other = broom(helper, new Vec3(2.5, 2, 2.5));
        mount(helper, p, b);
        AtomicBoolean checked = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (checked.get() || !PensieveSessions.isViewer(p)) return;
            checked.set(true);
            Session first = PensieveSessions.sessionOf(p).get();
            helper.assertTrue(!FlyingMemory.tryStart(p, b), "a start while inside is refused");
            helper.assertTrue(!FlyingMemory.tryStart(p, other), "on any broom");
            FlyingMemory.scheduleStart(p, other);
            helper.assertTrue(PensieveSessions.sessionOf(p).get() == first, "no second memory");
            helper.assertTrue(!other.isHeld(), "the second broom is not held");
            helper.assertTrue(b.isHeld(), "the first broom still is");
        });
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(checked.get(), "never started");
            helper.assertTrue(!other.isHeld(), "still not held");
            PensieveSessions.leave(p, ExitReason.ABORT);
            helper.runAfterDelay(60, () -> {
                logout(helper, p);
                pass("secondMountWhileInMemoryIgnored");
                helper.succeed();
            });
        });
    }

    // ---- Fix round 1 -------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = "flying_stop", timeoutTicks = 200)
    public static void serverStopReleasesBroom(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        AtomicBoolean checked = oncePlaying(helper, p, () -> {
            helper.assertTrue(ticket(helper, b) && b.isHeld(), "held while inside");
            FlyingMemory.releaseAll();
            helper.assertTrue(!ticket(helper, b) && !b.isHeld() && !FlyingMemory.isHolding(p.getUUID()),
                    "a stopping server releases the ticket and the broom");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(checked.get(), "never started");
            PensieveSessions.leave(p, ExitReason.ABORT);
            logout(helper, p);
            pass("serverStopReleasesBroom");
        });
    }

    @GameTest(template = EMPTY, batch = "flying_other_wake", timeoutTicks = 100)
    public static void mountDuringAnotherWakeDoesNotStart(GameTestHelper helper) {
        seam(helper, 400, true, true);
        ServerPlayer p = player(helper, true);
        PensieveSessions.noteWakeForTests(p.getUUID(), helper.getLevel().getGameTime());
        BroomEntity b = broom(helper, new Vec3(1.5, 2, 1.5));
        mount(helper, p, b);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!PensieveSessions.isViewer(p), "another memory's wake is playing: just mount");
            helper.assertTrue(p.getVehicle() == b && !b.isHeld() && !ticket(helper, b), "nothing is held");
            helper.assertTrue(!FlyingMemory.isSeen(p), "the flag stays unset");
            logout(helper, p);
            pass("mountDuringAnotherWakeDoesNotStart");
            helper.succeed();
        });
    }

    /** Mounts 30 blocks up and runs the whole chain; {@code lose} removes the broom while the player is away. */
    private static void highWake(GameTestHelper helper, boolean lose, String name) {
        seam(helper, 40, true, true);
        ServerPlayer p = player(helper, true);
        BroomEntity b = broom(helper, new Vec3(1.5, 30, 1.5));
        mount(helper, p, b);
        double[] homeY = {Double.NaN};
        if (lose) oncePlaying(helper, p, b::discard);
        helper.onEachTick(() -> {
            if (PensieveSessions.isViewer(p) || PensieveSessions.lastWakeForTests(p.getUUID()) < 0) return;
            // Once seated, the broom's seat is the position: the pin is for the wake before that.
            if (p.getVehicle() != null) return;
            if (Double.isNaN(homeY[0])) homeY[0] = p.getY();
            helper.assertTrue(Math.abs(p.getY() - homeY[0]) < 0.05, "the player fell during the wake: " + (homeY[0] - p.getY()));
            helper.assertTrue(p.fallDistance == 0, "fall distance must stay zero, was " + p.fallDistance);
            helper.assertTrue(p.isAlive() && p.getHealth() == p.getMaxHealth(), "the player must be unhurt");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(!Double.isNaN(homeY[0]), "not home yet");
            helper.assertTrue(!FlyingMemory.isHolding(p.getUUID()), "the wake is not over");
            helper.assertTrue(p.isAlive() && p.getHealth() == p.getMaxHealth() && p.fallDistance == 0, "unhurt at the end");
            if (lose) {
                helper.assertTrue(p.getVehicle() == null, "no broom to board");
                helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING),
                        "set down safely: slow falling");
            } else {
                helper.assertTrue(p.getVehicle() == b, "seated on the broom again");
            }
            logout(helper, p);
            pass(name);
        });
    }

    @GameTest(template = EMPTY, batch = "flying_high_wake", timeoutTicks = 800)
    public static void highMountSurvivesWake(GameTestHelper helper) {
        highWake(helper, false, "highMountSurvivesWake");
    }

    @GameTest(template = EMPTY, batch = "flying_high_lost", timeoutTicks = 800)
    public static void highMountLostBroomSetsPlayerDownSafely(GameTestHelper helper) {
        highWake(helper, true, "highMountLostBroomSetsPlayerDownSafely");
    }
}
