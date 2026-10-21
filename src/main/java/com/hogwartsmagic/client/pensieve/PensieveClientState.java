package com.hogwartsmagic.client.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.event.ClientModEvents;
import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.pensieve.net.PensieveLeavePacket;
import com.hogwartsmagic.pensieve.scene.Eyelids;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SessionClock;
import com.hogwartsmagic.pensieve.scene.SmoothedSceneClock;
import com.mojang.logging.LogUtils;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

/**
 * Client half of a Pensieve dive: the phase machine every other Pensieve client class reads.
 *
 * <p>Phases are plain ints (not an enum, so no synthetic switch map can ever be involved). Each phase is driven
 * only by {@link #phaseTime} (client ticks since it began) or {@link #sceneTime} (the shared game clock), so two
 * viewers of the same memory see the same film.
 *
 * <pre>
 *  onEnter ──► SWALLOW (30) ──► FALL ──onStart(-1)──► BUILD ──onStart(t)──► PLAY ──onExit──► EXIT (30 + return 15)
 *                                  └────────onStart(t), a late joiner────────┘          └─onExit(YANK)─► YANK (10)
 *  BUILD/PLAY ──onStart(t) of another scene id (a chain's hand-over)──► PLAY of the new scene, faded in from black
 *  NONE ──(a wake was sent before the move home)──► the eyes open on the real world, HUD still hidden, then nothing
 * </pre>
 *
 * <p>A hand-over the server asks for mid-scene ({@code PensieveSessions.handOver}) first sends {@link #onFade}: the
 * scene fades to black as at a chained scene's end and stays black until the new start. The new session may be the
 * same scene id (a hold scene looping); it is told apart by its origin, since every session has its own slot.
 *
 * <p>The wake ({@code end_action: "wake"}) is the one eyelid effect drawn outside a memory: {@link #onWake} arrives
 * just before the move home, the return then stays black, and once the dive is over ({@link #NONE}) the lids open
 * on the real world over the wake's ticks ({@link #wakeClosed}).
 *
 * <p>The time of day: the memory dimension shares the overworld clock, and the memory is reached with a seamless
 * move, so the client is still holding the overworld {@link ClientLevel}. While the memory is on screen this class
 * sets that level's day time to the scene's {@code set.timeOfDay} every client tick and again at the start of every
 * render frame (after the tick's packets, so the server's 1 Hz time sync can never show through), and restores the
 * real clock and the daylight rule as soon as the memory leaves the screen. The sky is not drawn inside the void,
 * so what the scene time changes is the light: a night scene renders with night sky light.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PensieveClientState {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int NONE = 0;
    public static final int SWALLOW = 1;
    public static final int FALL = 2;
    public static final int BUILD = 3;
    public static final int PLAY = 4;
    public static final int EXIT = 5;
    public static final int YANK = 6;

    /** Wire ordinals of the server's ExitReason (END, YANK, LEFT, ABORT). */
    private static final int REASON_YANK = 1;
    private static final int DEFAULT_RADIUS = 32;
    /** Back at the basin once the player is this close to it; memory regions are far away from any basin. */
    private static final double BACK_AT_BASIN = 24;

    private static int phase = NONE;
    /** Client ticks, advanced only while the game is not paused. */
    private static long ticks;
    private static long phaseStart;

    private static BlockPos basin;
    private static Vec3 enterEye;
    private static float enterYaw;
    private static float enterPitch;

    private static SceneDef scene;
    private static String sceneId;
    private static BlockPos origin;
    private static long startGameTime = -1;
    /**
     * The scene clock every reader sees ({@link #sceneTime}): eased toward the server's game time instead of jumping
     * with vanilla's 1 Hz time sync. Advanced once per client tick in {@link #onClockTick}, before any other tick work.
     */
    private static final SmoothedSceneClock CLOCK = new SmoothedSceneClock();
    /** The raw scene time at the last clock tick, to spot the server's corrections for the debug log. */
    private static long clockLastRaw = Long.MIN_VALUE;
    /** Server corrections seen since the last debug line, and the client tick of that line (rate limit). */
    private static int clockJumps;
    private static long clockJumpSum;
    private static long clockLogTick = Long.MIN_VALUE / 2;
    private static final int CLOCK_LOG_EVERY = 20;
    private static int buildTicks;
    private static boolean preview;
    /** PLAY was reached straight from FALL (a late joiner) or by a chain's hand-over: it fades in from black. */
    private static boolean lateJoin;
    /**
     * PLAY was reached by a chain's hand-over: the same fade from black, but the camera does not settle and the set
     * is not revealed out of the fog again (see {@link #setReveal}).
     */
    private static boolean handedOver;
    /**
     * A hand-over is holding black until the new set is drawn around the camera ({@link SetWarmup#ready}); the
     * fade in begins when it is, or when the hold gives up ({@link PensieveTimeline#handOverHeld}).
     */
    private static boolean holdForSet;
    private static long holdStart;
    /** {@link SetWarmup} failed once this run; logged once, not every tick. */
    private static boolean warmupWarned;
    /** The scene opens its eyes at its start ({@link Eyelids#opensShut}): the lids are held shut while it builds. */
    private static boolean opensShut;
    /** The lids were held shut when the exit began, so the collapse stays black. */
    private static boolean heldAtExit;
    /** In PLAY, the held lids give way to the scene's own eyes_open by this scene time at the latest. */
    private static final float HOLD_LIMIT = 5;
    /** A start that arrived during the swallow or the fall, held until the fall has run its minimum. */
    private static boolean startPending;
    /** The reveal (shadow to memory) began at this tick and lasts this many ticks; it may run on into PLAY. */
    private static long revealStart;
    private static int revealSpan = 1;

    /** A mid-scene hand-over's fade to black began at this tick (-1: none) and lasts this many ticks. */
    private static long fadeStart = -1;
    private static int fadeTicks = PensieveTimeline.CHAIN_FADE_TICKS;
    /** A wake arrived during the dive: the screen stays black until the dive is over, then the wake starts. */
    private static boolean wakePending;
    /** The running wake began at this tick (-1: none) and opens the eyes over this many ticks. */
    private static long wakeStart = -1;
    private static int wakeTicks;

    private static float thudStrength;
    private static long thudTick = Long.MIN_VALUE / 2;
    /** EXIT/YANK: the client tick the return began (the move back arrived), or -1 while still waiting. */
    private static long returnStart = -1;
    private static boolean leaveSent;

    private static ClientLevel timeLevel;
    private static long savedDayTime;
    private static long savedGameTime;
    private static boolean savedCycle;

    private PensieveClientState() {
    }

    // ---- Server hooks (through ClientRuntimeBridge) ----------------------------------------------------------

    /** The local player dived into the basin at {@code basin}. */
    public static void onEnter(BlockPos basinPos) {
        Minecraft mc = Minecraft.getInstance();
        PensieveSounds.stopAll();
        MemoryActors.clear();
        scene = null;
        sceneId = null;
        origin = null;
        startGameTime = -1;
        resetClock();
        lateJoin = false;
        handedOver = false;
        holdForSet = false;
        opensShut = false;
        heldAtExit = false;
        startPending = false;
        leaveSent = false;
        returnStart = -1;
        thudTick = Long.MIN_VALUE / 2;
        fadeStart = -1;
        wakePending = false;
        wakeStart = -1;
        basin = basinPos;
        // A memory started by putting a hat on from the inventory must not play behind the open screen.
        if (mc.screen != null) mc.setScreen(null);
        if (mc.player != null) {
            enterEye = mc.player.getEyePosition();
            enterYaw = mc.player.getYRot();
            enterPitch = mc.player.getXRot();
        } else {
            enterEye = Vec3.atCenterOf(basinPos).add(0, 1.2, 0);
            enterYaw = 0;
            enterPitch = 30;
        }
        if (!mc.options.getCameraType().isFirstPerson()) {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        }
        setPhase(SWALLOW);
        PensieveSounds.play("swallow", 1.0f, 1.0f);
    }

    /** The local player is in the memory; {@code startGameTime} is -1 while the set is still building. */
    public static void onStart(String id, String json, BlockPos at, long start, int build, boolean isPreview) {
        if (phase == EXIT || phase == YANK) return;
        // A different scene while one is already on screen: the server handed this viewer on to a chained scene. A
        // scene handed over to itself (a hold scene looping) is a new session in a new slot: its origin differs.
        boolean switched = sceneId != null && (!id.equals(sceneId) || (origin != null && !origin.equals(at)));
        if (scene == null || switched) {
            scene = parse(id, json);
            sceneId = id;
            opensShut = scene != null && Eyelids.opensShut(scene.events());
        }
        origin = at;
        buildTicks = build;
        preview = isPreview;
        if (start != startGameTime) resetClock();
        startGameTime = start;
        if (phase == SWALLOW || phase == FALL) {
            // The server is often ready the moment the viewer crosses; the fall through the dark still plays.
            startPending = true;
            return;
        }
        if (switched) {
            handOver();
            return;
        }
        applyStart();
    }

    private static void applyStart() {
        startPending = false;
        if (startGameTime < 0) {
            if (phase != BUILD) {
                setPhase(BUILD);
                beginReveal(buildTicks);
            }
        } else if (phase != PLAY) {
            lateJoin = phase != BUILD;
            handedOver = false;
            holdForSet = false;
            setPhase(PLAY);
            // A new memory is starting: nothing of an old one may linger, not even music still fading out.
            MemoryAudio.stopNow();
            MemoryActors.start(scene, origin, Minecraft.getInstance().level);
            if (lateJoin) beginReveal(PensieveTimeline.LATE_FADE_TICKS);
        }
        PensieveSounds.stopFall();
        PensieveSounds.hum(true);
    }

    /**
     * BUILD or PLAY, and the next scene of a chain arrived (the server already moved the viewer to its anchor). The
     * last scene's puppets, events, spell and effect visuals and memory sounds go (all of {@link MemoryActors#clear},
     * which also resets the event cursor), its voices stop and its music fades under the new opening
     * ({@link MemoryAudio#cut}); its words go by themselves, being drawn from the current scene and clock. The last
     * scene faded to black at its length (or, from BUILD, is cut to black), and the new one fades in from black,
     * only darkness: the set is not revealed out of the fog as on entering. The new set is a slot away, so its
     * chunks arrive and are drawn after the move; the screen holds black until they are ({@link #holdForSet}).
     * The time of day follows the new scene through {@link #syncTimeOfDay}.
     */
    private static void handOver() {
        fadeStart = -1;
        MemoryActors.clear();
        MemoryAudio.cut();
        if (startGameTime < 0) {
            // Not sent by the server today (a hand-over always starts the next scene), but never leave PLAY running
            // on a clock that has not started.
            if (phase != BUILD) setPhase(BUILD);
            holdForSet = false;
            beginReveal(buildTicks);
            return;
        }
        // PLAY keeps its phase start, so the leave hint does not come back for every scene of the chain.
        if (phase != PLAY) setPhase(PLAY);
        lateJoin = true;
        handedOver = true;
        MemoryActors.start(scene, origin, Minecraft.getInstance().level);
        holdForSet = true;
        holdStart = ticks;
    }

    private static void beginReveal(int span) {
        revealStart = ticks;
        revealSpan = Math.max(1, span);
    }

    /** The local player is leaving; {@code reason} is the server's {@code ExitReason} ordinal. */
    public static void onExit(int reason) {
        if (phase == NONE) return;
        returnStart = -1;
        if (reason == REASON_YANK) {
            if (phase == YANK) return;
            setPhase(YANK);
            PensieveSounds.stopFall();
            PensieveSounds.hum(false);
            PensieveSounds.play("gasp", 1.0f, 1.0f);
            return;
        }
        if (phase == EXIT || phase == YANK) return;
        startPending = false;
        heldAtExit = lidsHeldShut(0);
        holdForSet = false;
        boolean inside = phase == BUILD || phase == PLAY;
        setPhase(EXIT);
        if (!inside) {
            // Out before the memory was ever on screen: skip the collapse, straight into the dark rise.
            phaseStart = ticks - PensieveTimeline.EXIT_COLLAPSE_TICKS;
        }
        PensieveSounds.stopFall();
        PensieveSounds.play("rise", 1.0f, 1.0f);
    }

    /** The server asked for a hand-over mid-scene: fade the scene on screen to black over {@code span} ticks. */
    public static void onFade(int span) {
        if ((phase != BUILD && phase != PLAY) || fadeStart >= 0) return;
        fadeTicks = Math.max(1, span);
        fadeStart = ticks;
    }

    /**
     * The scene going home ends in a wake: keep the screen black through the rest of the dive, then open the eyes on
     * the real world over {@code span} ticks. Outside a dive (it arrived late) the wake starts at once.
     */
    public static void onWake(int span) {
        wakeTicks = Math.max(1, span);
        if (phase == NONE) {
            wakePending = false;
            wakeStart = ticks;
        } else {
            wakePending = true;
            wakeStart = -1;
        }
    }

    /** The body left at the basin was hit; {@code strength} is 0..1. */
    public static void onThud(float strength) {
        if (phase == NONE) return;
        thudStrength = Math.max(0, Math.min(1, strength));
        thudTick = ticks;
        PensieveSounds.play("distant_thud", Math.max(0.15f, thudStrength), 1.0f);
    }

    private static SceneDef parse(String id, String json) {
        try {
            return SceneParser.parse(id, JsonParser.parseString(json).getAsJsonObject());
        } catch (Exception e) {
            LOGGER.warn("Pensieve: could not parse scene '{}' on the client: {}", id, e.toString());
            return null;
        }
    }

    // ---- Reads ------------------------------------------------------------------------------------------------

    public static boolean active() {
        return phase != NONE;
    }

    public static int phase() {
        return phase;
    }

    /** Ticks since the current phase began, with the frame's partial tick. */
    public static float phaseTime(float partial) {
        return (ticks - phaseStart) + partial;
    }

    /** The client clock, for animation that only needs to move (wisps, shimmer). */
    public static float clock(float partial) {
        return ticks + partial;
    }

    /** The parsed scene, or null before onStart or when the JSON failed to parse. */
    public static SceneDef scene() {
        return scene;
    }

    public static BlockPos origin() {
        return origin;
    }

    public static BlockPos basin() {
        return basin;
    }

    public static Vec3 enterEye() {
        return enterEye;
    }

    public static float enterYaw() {
        return enterYaw;
    }

    public static float enterPitch() {
        return enterPitch;
    }

    public static boolean preview() {
        return preview;
    }

    public static boolean lateJoin() {
        return lateJoin;
    }

    /** True while PLAY came from a chain's hand-over (not a true late join): the camera skips its landing settle. */
    public static boolean handedOver() {
        return handedOver;
    }

    /**
     * True while the lids are held shut ahead of a scene that opens its eyes at its start: for the whole BUILD (the
     * memory opens in darkness, the set building unseen), into PLAY until the scene's own {@code eyes_open} is running
     * (a bounded wait, in case it never starts), and through an exit begun while they were held.
     */
    static boolean lidsHeldShut(float partial) {
        if (!opensShut) return false;
        if (phase == BUILD) return true;
        if (phase == PLAY) return !lateJoin && !EffectVisuals.eyesStarted() && sceneTime(partial) < HOLD_LIMIT;
        return phase == EXIT && heldAtExit;
    }

    /** How shut the viewer's eyes are, 0..1: held shut (above), else the scene's running eyelid effects. */
    static float lidsClosed(float partial) {
        return lidsHeldShut(partial) ? 1f : EffectVisuals.eyelids(partial);
    }

    public static int buildTicks() {
        return buildTicks;
    }

    public static int radius() {
        return scene != null ? Math.max(1, scene.radius()) : DEFAULT_RADIUS;
    }

    /** The scene's {@code fog_far} in blocks, or 0 when the fog follows the radius (the default). */
    public static int fogFar() {
        return scene != null ? Math.max(0, scene.fogFar()) : 0;
    }

    /** The reveal from shadow to memory, 0..1 (1 once it is complete); 0 while a hand-over holds black. */
    public static float reveal(float partial) {
        if (holdForSet) return 0f;
        return PensieveTimeline.reveal((ticks - revealStart) + partial, revealSpan);
    }

    /**
     * The reveal the set itself follows (fog radius, grade, shadow smoke): {@link #reveal}, except after a chain's
     * hand-over, where only the darkness fades in.
     */
    public static float setReveal(float partial) {
        return PensieveTimeline.setReveal(reveal(partial), handedOver);
    }

    /** Ticks since the reveal began (BUILD or a late joiner's PLAY). */
    public static float revealTime(float partial) {
        return (ticks - revealStart) + partial;
    }

    /**
     * Scene time from the shared game clock; 0 while the set is building. Eased ({@link #CLOCK}): it follows the
     * server's time but never jumps with the 1 Hz time sync, so cameras, limbs and effects sampled with a partial
     * tick move smoothly, and tick work (actors, events) reads the same clock at partial 0.
     */
    public static float sceneTime(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (startGameTime < 0 || mc.level == null) return 0;
        if (!CLOCK.running()) return SessionClock.elapsed(startGameTime, mc.level.getGameTime(), partial);
        return (float) Math.max(0, CLOCK.at(partial));
    }

    private static void resetClock() {
        CLOCK.reset();
        clockLastRaw = Long.MIN_VALUE;
    }

    /**
     * Advance the scene clock at the end of each client tick, ahead of every other END handler (the actors, events
     * and audio read it at partial 0 in theirs). The level's game time has already moved on for this tick.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onClockTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (startGameTime < 0 || mc.level == null || mc.isPaused()) return;
        long raw = Math.max(0, mc.level.getGameTime() - startGameTime);
        boolean snapped = CLOCK.tick(raw);
        long step = clockLastRaw == Long.MIN_VALUE ? 1 : raw - clockLastRaw;
        clockLastRaw = raw;
        if (!LOGGER.isDebugEnabled()) {
            clockJumps = 0;
            clockJumpSum = 0;
            return;
        }
        if (step != 1 || (snapped && step != 0)) {
            clockJumps++;
            clockJumpSum += step - 1;
        }
        if ((clockJumps > 0 || snapped) && ticks - clockLogTick >= CLOCK_LOG_EVERY) {
            LOGGER.debug("Pensieve clock: {} server correction(s), net {} tick(s), {}; error {} ticks, rate {}, "
                            + "server rate {}",
                    clockJumps, clockJumpSum, snapped ? "snapped" : "eased",
                    String.format("%.3f", CLOCK.lastError()), String.format("%.3f", CLOCK.rate()),
                    String.format("%.3f", CLOCK.serverRate()));
            clockJumps = 0;
            clockJumpSum = 0;
            clockLogTick = ticks;
        }
    }

    /**
     * A chained scene's fade to black at its length, 0..1 (0 for a scene with no {@code next}), or a mid-scene
     * hand-over's fade ({@link #onFade}), whichever is darker.
     */
    public static float chainEndDarkness(float partial) {
        float asked = fadeStart < 0 ? 0 : PensieveTimeline.smoothstep(((ticks - fadeStart) + partial) / fadeTicks);
        if (scene == null || scene.next() == null || startGameTime < 0) return asked;
        return Math.max(asked, PensieveTimeline.chainEndDarkness(sceneTime(partial), scene.length()));
    }

    /** Red-edge strength from the last hit on the body, decaying over 12 ticks. */
    public static float thud() {
        return thud(partial());
    }

    public static float thud(float partial) {
        return PensieveTimeline.thud(thudStrength, (ticks - thudTick) + partial);
    }

    public static float shudder(float partial) {
        return PensieveTimeline.shudder(thudStrength, (ticks - thudTick) + partial);
    }

    /** EXIT/YANK: ticks since the move back arrived, or -1 while still waiting for it. */
    public static float returnTime(float partial) {
        return returnStart < 0 ? -1 : (ticks - returnStart) + partial;
    }

    /** True while the memory itself (not the basin side of the dive) is what the camera shows. */
    public static boolean inMemory() {
        if (phase == BUILD || phase == PLAY) return true;
        if (phase == EXIT) return returnStart < 0 && (ticks - phaseStart) < PensieveTimeline.EXIT_TICKS;
        return phase == YANK && returnStart < 0;
    }

    /** The vanilla HUD (all but chat) is hidden for the whole dive, and while a wake opens the eyes afterwards. */
    public static boolean hidesHud() {
        return phase != NONE || waking();
    }

    /** True while a wake is pending or running (see {@link #wakeClosed}). */
    public static boolean waking() {
        return wakePending || wakeStart >= 0;
    }

    /** True while a wake has arrived but the dive is not over yet: the screen is held black. */
    static boolean wakePending() {
        return wakePending;
    }

    /** How shut the eyes of a running wake are, 0..1 (an {@code eyes_open} curve); 1 while pending, 0 with none. */
    static float wakeClosed(float partial) {
        if (wakePending) return 1f;
        if (wakeStart < 0) return 0f;
        return Eyelids.closed(true, (ticks - wakeStart) + partial, wakeTicks);
    }

    static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
    }

    // ---- Ticking ----------------------------------------------------------------------------------------------

    private static void setPhase(int p) {
        phase = p;
        phaseStart = ticks;
    }

    private static void reset() {
        phase = NONE;
        scene = null;
        sceneId = null;
        origin = null;
        basin = null;
        startGameTime = -1;
        resetClock();
        returnStart = -1;
        lateJoin = false;
        handedOver = false;
        holdForSet = false;
        opensShut = false;
        heldAtExit = false;
        startPending = false;
        leaveSent = false;
        fadeStart = -1;
        if (wakePending) {
            // The dive is over: the eyes open on the real world from here.
            wakePending = false;
            wakeStart = ticks;
        }
        MemoryActors.clear();
        PensieveSounds.stopAll();
        syncTimeOfDay();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (phase != NONE) reset();
            wakePending = false;
            wakeStart = -1;
            timeLevel = null;
            return;
        }
        if (phase == NONE) {
            drainLeaveKey();
            if (wakeStart >= 0) {
                if (!mc.isPaused()) ticks++;
                if (ticks - wakeStart >= wakeTicks) wakeStart = -1;
            }
            return;
        }
        if (!mc.isPaused()) ticks++;
        long t = ticks - phaseStart;
        if (phase == SWALLOW && t >= PensieveTimeline.SWALLOW_TICKS) {
            setPhase(FALL);
            PensieveSounds.startFall();
            PensieveSounds.hum(true);
        } else if (phase == FALL && startPending && t >= PensieveTimeline.MIN_FALL_TICKS) {
            applyStart();
        } else if (phase == EXIT) {
            if (returnStart < 0 && t >= PensieveTimeline.EXIT_TICKS
                    && (backAtBasin(mc) || t >= PensieveTimeline.EXIT_TICKS + PensieveTimeline.RETURN_WAIT_LIMIT)) {
                returnStart = ticks;
                PensieveSounds.hum(false);
            }
            if (returnStart >= 0 && ticks - returnStart >= PensieveTimeline.RETURN_TICKS) reset();
        } else if (phase == YANK) {
            if (t >= PensieveTimeline.YANK_TICKS
                    && (backAtBasin(mc) || t >= PensieveTimeline.YANK_TICKS + PensieveTimeline.RETURN_WAIT_LIMIT)) {
                reset(); // the return camera snaps: no ease after a yank
            }
        }
        if (phase == BUILD || phase == PLAY) {
            float st = sceneTime(0);
            boolean setReady = true;
            try {
                SetWarmup.tick(mc, st);
                if (holdForSet) setReady = SetWarmup.ready(mc, cameraAt(mc, st));
            } catch (RuntimeException e) {
                // Drawing ahead is an optimisation over vanilla internals: never let it take the memory down.
                if (!warmupWarned) LOGGER.warn("Pensieve: drawing the set ahead failed: {}", e.toString());
                warmupWarned = true;
            }
            if (holdForSet && !PensieveTimeline.handOverHeld(setReady, ticks - holdStart)) {
                holdForSet = false;
                beginReveal(PensieveTimeline.LATE_FADE_TICKS);
            }
            PensieveVoid.tickParticles(mc);
        }
        handleLeaveKey();
        syncTimeOfDay();
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) syncTimeOfDay();
    }

    /** No block outline for an invisible ghost hovering at the anchor. */
    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        if (phase != NONE) event.setCanceled(true);
    }

    /** Where the camera is at scene time {@code t}: the scene's shot, or the viewer's own eye with no camera track. */
    private static Vec3 cameraAt(Minecraft mc, float t) {
        Vec3 shot = PensieveCamera.shotPosition(t);
        return shot != null ? shot : mc.player.getEyePosition();
    }

    private static boolean backAtBasin(Minecraft mc) {
        if (basin == null || mc.player == null) return true;
        return mc.player.position().distanceToSqr(Vec3.atCenterOf(basin)) < BACK_AT_BASIN * BACK_AT_BASIN;
    }

    private static void handleLeaveKey() {
        boolean pressed = false;
        while (ClientModEvents.PENSIEVE_LEAVE_KEY.consumeClick()) {
            pressed = true;
        }
        if (pressed) requestLeave();
    }

    /**
     * Asks the server to leave the memory, once, while it is falling, building or playing; false otherwise. Also for
     * a screen that is open inside the memory (the Sorting quiz), which swallows the key before the key mapping sees it.
     */
    public static boolean requestLeave() {
        if (leaveSent || (phase != FALL && phase != BUILD && phase != PLAY)) return false;
        leaveSent = true;
        SpellPackets.CHANNEL.send(new PensieveLeavePacket(), PacketDistributor.SERVER.noArg());
        return true;
    }

    private static void drainLeaveKey() {
        while (ClientModEvents.PENSIEVE_LEAVE_KEY.consumeClick()) {
            // not in a memory: X does nothing
        }
    }

    // ---- Time of day ------------------------------------------------------------------------------------------

    private static boolean wantsSceneTime() {
        return scene != null && inMemory();
    }

    /** See the class comment: hold the scene's time of day on the client level while the memory is on screen. */
    private static void syncTimeOfDay() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        boolean want = level != null && wantsSceneTime();
        if (timeLevel != null && (timeLevel != level || !want)) {
            if (timeLevel == level) {
                long now = level.getGameTime();
                long real = savedCycle ? savedDayTime + Math.max(0, now - savedGameTime) : savedDayTime;
                level.setDayTime(real);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(savedCycle, null);
            }
            timeLevel = null;
        }
        if (!want) return;
        if (timeLevel == null) {
            timeLevel = level;
            savedDayTime = level.getDayTime();
            savedGameTime = level.getGameTime();
            savedCycle = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        }
        level.setDayTime(Math.floorMod(scene.set().timeOfDay(), 24000L));
    }
}
