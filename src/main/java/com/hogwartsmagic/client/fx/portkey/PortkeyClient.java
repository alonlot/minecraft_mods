package com.hogwartsmagic.client.fx.portkey;

import com.hogwartsmagic.ExampleMod;
import com.hogwartsmagic.client.SpellLight;
import com.hogwartsmagic.client.fx.CameraShake;
import com.hogwartsmagic.client.fx.Cinematic;
import com.hogwartsmagic.client.fx.FxClient;
import com.hogwartsmagic.client.fx.FxOverlays;
import com.hogwartsmagic.fx.FxConfig;
import com.hogwartsmagic.fx.FxKinds;
import com.hogwartsmagic.portkey.PortkeyRules;
import com.hogwartsmagic.portkey.PortkeyTimeline;
import com.hogwartsmagic.portkey.PortkeyTimeline.Frame;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The client side of a Portkey journey: turns the server's {@code portkey_*} effect kinds into one timeline.
 *
 * <p>For the one travelling: the boot rises into the middle of the view and trembles as the charge builds, the
 * view narrows and the edges pulse blue-white; at the hook a white flash, the view flung wide and collapsing into a
 * spinning tunnel (PortkeyPost), the camera rolling and buffeted, the wind roaring; once the far end has arrived and
 * is drawn, the tunnel lets go and the new world unwinds out of the twist; the feet hit the ground with a thump, a
 * shake, a stagger and a ring of dust. For everyone near: a glow at the holder's hand, spirals and runes gathering
 * round them, an implosion of light where the group stood and a burst where it lands (PortkeyWorldFx).
 *
 * <p>The traveller's own timeline runs on the wall clock in 50 ms ticks (as {@code Cinematic} does), because the
 * client level is replaced on a dimension change; world effects run on level game time. Every number lives in
 * {@code portkey.PortkeyTimeline}. Logging out clears everything; a dimension change clears the world effects and
 * charges and keeps a trip in flight, which always ends on its own (hard timeout).
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class PortkeyClient {

    /** At most this many world events are remembered; the oldest go first. */
    private static final int MAX_EVENTS = 16;
    /** World event lifetimes (game ticks). */
    private static final float HOOK_EVENT_TICKS = 14.0f, ARRIVE_EVENT_TICKS = 18.0f, LAND_EVENT_TICKS = 14.0f;
    /** A charge the server never hooked or cancelled is let go after this long (game ticks). */
    private static final float CHARGE_EVENT_MAX = PortkeyRules.CHARGE_TICKS + 40.0f;
    /** An arrival within this many blocks of the player is our own. */
    private static final double OWN_ARRIVAL = 8.0;
    /** A jump of more than this many blocks while in the tunnel means we have been moved. */
    private static final double JUMP = 16.0;
    /** The trip is over this many ticks after the exit began (the landing roll has settled). */
    private static final float TRIP_END = 30.0f;
    private static final double TICK_NANOS = 50_000_000.0;

    /** Something that happened in the world, drawn by PortkeyWorldFx. {@code end} is when a charge was let go. */
    public record WorldEvent(String kind, int entityId, Vec3 pos, double start, int seed, boolean old, double end) {
        public float age(double now) {
            return (float) (now - start);
        }
    }

    private static final List<WorldEvent> EVENTS = new ArrayList<>();

    // ---- the wall clock (slowed for /portkeyfx screenshots) -----------------------------------------------------

    private static double rate = 1.0;
    private static long baseNanos = System.nanoTime();
    private static double baseTicks;

    /** Wall-clock ticks, scaled by the debug rate. */
    static double now() {
        return baseTicks + (System.nanoTime() - baseNanos) / TICK_NANOS * rate;
    }

    private static void setRate(double r) {
        baseTicks = now();
        baseNanos = System.nanoTime();
        rate = Mth.clamp(r, 0.1, 1.0);
    }

    // ---- the own trip -----------------------------------------------------------------------------------------

    private static double chargeStart = -1.0;
    private static double cancelStart = -1.0;
    private static double hookStart = -1.0;
    private static double exitStart = -1.0;
    private static double arrivedAt = -1.0;
    private static double crumbleStart = -1.0;
    private static boolean arrived;
    private static boolean old;
    private static boolean landed;
    private static boolean noLanding;
    private static boolean crossDim;
    /** Whether we hold the boot on this trip (a traveller taken along watches their own Portkey stay dark). */
    private static boolean holderTrip;
    private static float rollAtExit;
    private static int maxTicks = PortkeyRules.TRANSIT_MAX_TICKS;
    private static Vec3 transitFrom;
    private static ResourceKey<Level> transitDim;
    private static int lastShake = -1;
    /** A bystander pop waiting one tick to learn whether we were taken along after all. */
    private static Vec3 pendingPop;
    /** The debug sequence's scheduled beats (wall-clock tick, what). */
    private static final List<Scheduled> SCHEDULE = new ArrayList<>();

    private record Scheduled(double at, Runnable what) {
    }

    private PortkeyClient() {
    }

    // ---- what other client code reads -------------------------------------------------------------------------

    /** The traveller's frame for this render frame, or null when nothing of ours is running. */
    public static Frame current() {
        double n = now();
        if (hookStart >= 0.0) {
            float h = (float) (n - hookStart);
            float x = exitStart >= 0.0 ? (float) (n - exitStart) : -1.0f;
            return PortkeyTimeline.self(PortkeyRules.CHARGE_TICKS, h, x, rollAtExit);
        }
        if (chargeStart >= 0.0) {
            float t = (float) Math.min(n - chargeStart, PortkeyRules.CHARGE_TICKS);
            Frame f = PortkeyTimeline.self(t, -1.0f, -1.0f, 0.0f);
            float k = cancelFade(n);
            return k >= 1.0f ? f : scale(f, k);
        }
        return null;
    }

    /** 1 while a charge runs, falling to 0 over the cancel decay. */
    private static float cancelFade(double n) {
        if (cancelStart < 0.0) {
            return 1.0f;
        }
        return 1.0f - PortkeyTimeline.clamp01((float) (n - cancelStart) / PortkeyTimeline.CANCEL_TICKS);
    }

    private static Frame scale(Frame f, float k) {
        return new Frame(f.mix() * k, f.twist() * k, f.pull() * k, f.blur() * k, f.chroma() * k, f.glow() * k,
                1.0f - (1.0f - f.fov()) * k, f.roll() * k, f.pitch() * k, f.spin(), f.centreX(), f.centreY());
    }

    /** Whether the traveller's tunnel is up (from the hook until it has let go). */
    public static boolean transitActive() {
        if (hookStart < 0.0) {
            return false;
        }
        return exitStart < 0.0 || now() - exitStart < PortkeyTimeline.EXIT_MIX_TICKS;
    }

    /** Own charge ticks so far (wall clock), or -1. */
    static float ownChargeAge() {
        return chargeStart < 0.0 ? -1.0f : (float) (now() - chargeStart);
    }

    /** Ticks since our hook, or -1. */
    static float ownHookAge() {
        return hookStart < 0.0 ? -1.0f : (float) (now() - hookStart);
    }

    /** Ticks since our exit began, or -1. */
    static float ownExitAge() {
        return exitStart < 0.0 ? -1.0f : (float) (now() - exitStart);
    }

    /** The cancel decay of our own charge (1 = none). */
    static float ownCancelFade() {
        return cancelFade(now());
    }

    static boolean ownOld() {
        return old;
    }

    static boolean ownLanding() {
        return !noLanding;
    }

    /**
     * How far our crumbling Old Portkey has gone (0..1), or 0. It starts CRUMBLE_AT ticks into our own exit (so the
     * boot is gone before the server takes it from the hand), or when the server says so, whichever is first.
     */
    public static float crumbleOf(float partial) {
        if (crumbleStart < 0.0) {
            return 0.0f;
        }
        return PortkeyTimeline.clamp01((float) (now() - crumbleStart) / PortkeyTimeline.CRUMBLE_TICKS);
    }

    /** Whether the first-person boot is ours to draw (a charge, a trip, a crumble). */
    static boolean handActive() {
        if (crumbleStart >= 0.0 && crumbleOf(0.0f) < 1.0f) {
            return true;
        }
        if (hookStart >= 0.0) {
            return holderTrip && (exitStart < 0.0 || now() - exitStart < PortkeyTimeline.LANDING_FOV_TICKS + 10.0f);
        }
        return chargeStart >= 0.0;
    }

    /** The charge (0..1) glowing in {@code entityId}'s Portkey: the item's {@code charge} property, the hand glow. */
    public static float chargeOf(int entityId, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entityId == mc.player.getId()) {
            double n = now();
            if (hookStart >= 0.0) {
                if (!holderTrip) {
                    return 0.0f; // taken along: our own Portkey, if any, stays dark
                }
                if (exitStart < 0.0) {
                    return 1.0f;
                }
                return 1.0f - PortkeyTimeline.smoothstep(0.0f, 20.0f, (float) (n - exitStart));
            }
            if (chargeStart >= 0.0) {
                return PortkeyTimeline.chargeEase((float) (n - chargeStart)) * cancelFade(n);
            }
            return 0.0f;
        }
        if (mc.level == null) {
            return 0.0f;
        }
        double gameNow = mc.level.getGameTime() + partial;
        for (int i = EVENTS.size() - 1; i >= 0; i--) {
            WorldEvent e = EVENTS.get(i);
            if (e.entityId == entityId && "charge".equals(e.kind)) {
                return worldCharge(e, gameNow);
            }
        }
        return 0.0f;
    }

    /** A world charge's ease at {@code now} (game time), its cancel decay folded in. */
    static float worldCharge(WorldEvent e, double now) {
        float ease = PortkeyTimeline.chargeEase(e.age(now));
        if (e.end >= 0.0) {
            ease *= 1.0f - PortkeyTimeline.clamp01((float) (now - e.end) / PortkeyTimeline.CANCEL_TICKS);
        }
        return ease;
    }

    /** Whether {@code entityId} has a charge running or powering down (for its hum). */
    static boolean charging(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entityId == mc.player.getId() && hookStart < 0.0 && chargeStart >= 0.0) {
            return cancelFade(now()) > 0.0f;
        }
        for (WorldEvent e : EVENTS) {
            if (e.entityId == entityId && "charge".equals(e.kind)) {
                return true;
            }
        }
        return false;
    }

    /** 0..1 progress of {@code entityId}'s charge powering down, or -1 when it is not (for the hum's slide). */
    static float cancelProgress(int entityId, double gameNow) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && entityId == mc.player.getId() && chargeStart >= 0.0) {
            return cancelStart < 0.0 ? -1.0f : 1.0f - cancelFade(now());
        }
        for (WorldEvent e : EVENTS) {
            if (e.entityId == entityId && "charge".equals(e.kind) && e.end >= 0.0) {
                return PortkeyTimeline.clamp01((float) (gameNow - e.end) / PortkeyTimeline.CANCEL_TICKS);
            }
        }
        return -1.0f;
    }

    /** The world events (capped), for the world renderer. */
    public static List<WorldEvent> worldEvents() {
        return EVENTS;
    }

    /** The wind loop's volume right now (0 when there is no tunnel). */
    static float windVolume() {
        if (hookStart < 0.0) {
            return 0.0f;
        }
        double n = now();
        float in = PortkeyTimeline.smoothstep(0.0f, PortkeyTimeline.WIND_IN_TICKS, (float) (n - hookStart));
        if (exitStart >= 0.0) {
            in *= 1.0f - PortkeyTimeline.clamp01((float) (n - exitStart) / PortkeyTimeline.WIND_OUT_TICKS);
        }
        return in;
    }

    /** Whether the wind should be playing (a tunnel up, or still fading out). */
    static boolean windWanted() {
        if (hookStart < 0.0) {
            return false;
        }
        return exitStart < 0.0 || now() - exitStart < PortkeyTimeline.WIND_OUT_TICKS;
    }

    /** The light the Portkey throws on what is round it (SpellLight's sources). */
    public static void lights(float partial, double now, SpellLight.Sink sink) {
        Minecraft mc = Minecraft.getInstance();
        if (EVENTS.isEmpty() || mc.level == null) {
            return;
        }
        for (WorldEvent e : EVENTS) {
            float t = e.age(now);
            if ("charge".equals(e.kind)) {
                Entity holder = mc.level.getEntity(e.entityId);
                Vec3 at = holder != null ? PortkeyWorldFx.handOf(holder, partial) : e.pos.add(0.0, 1.0, 0.0);
                float ease = worldCharge(e, now);
                sink.add(at, PortkeyTimeline.GLOW_R, PortkeyTimeline.GLOW_G, PortkeyTimeline.GLOW_B,
                        PortkeyTimeline.LIGHT_RADIUS + PortkeyTimeline.LIGHT_RADIUS_GAIN * ease,
                        PortkeyTimeline.LIGHT_INTENSITY + PortkeyTimeline.LIGHT_INTENSITY_GAIN * ease);
            } else if ("hook".equals(e.kind) || "arrive".equals(e.kind)) {
                float f = 1.0f - PortkeyTimeline.clamp01(t / PortkeyTimeline.FLASH_LIGHT_TICKS);
                if (f > 0.0f) {
                    sink.add(e.pos.add(0.0, 1.0, 0.0), PortkeyTimeline.CORE_R, PortkeyTimeline.CORE_G,
                            PortkeyTimeline.CORE_B, PortkeyTimeline.FLASH_LIGHT_RADIUS, f);
                }
            }
        }
    }

    // ---- the handlers -----------------------------------------------------------------------------------------

    private static boolean self(FxClient.FxEvent e) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && e.entityId() == player.getId();
    }

    private static double gameNow() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0 : mc.level.getGameTime();
    }

    private static void addEvent(WorldEvent event) {
        if (EVENTS.size() >= MAX_EVENTS) {
            EVENTS.remove(0);
        }
        EVENTS.add(event);
    }

    private static int seed(FxClient.FxEvent e) {
        return e.entityId() * 0x45D9F3B + (int) gameNow() * 31 + (int) (e.x() * 7.0) + (int) (e.z() * 13.0);
    }

    private static void onCharge(FxClient.FxEvent e) {
        boolean isOld = e.param(1, 0.0f) > 0.5f;
        EVENTS.removeIf(w -> w.entityId == e.entityId() && "charge".equals(w.kind));
        addEvent(new WorldEvent("charge", e.entityId(), e.pos(), gameNow(), seed(e), isOld, -1.0));
        if (self(e) && hookStart < 0.0) {
            chargeStart = now();
            cancelStart = -1.0;
            crumbleStart = -1.0;
            old = isOld;
        }
        PortkeySounds.startCharge(e.entityId());
    }

    private static void onCancel(FxClient.FxEvent e) {
        for (int i = 0; i < EVENTS.size(); i++) {
            WorldEvent w = EVENTS.get(i);
            if (w.entityId == e.entityId() && "charge".equals(w.kind) && w.end < 0.0) {
                EVENTS.set(i, new WorldEvent(w.kind, w.entityId, w.pos, w.start, w.seed, w.old, gameNow()));
            }
        }
        if (self(e) && chargeStart >= 0.0 && hookStart < 0.0 && cancelStart < 0.0) {
            cancelStart = now();
        }
        PortkeyWorldFx.cancelPuff(e);
    }

    private static void onHook(FxClient.FxEvent e) {
        EVENTS.removeIf(w -> w.entityId == e.entityId() && "charge".equals(w.kind));
        if (self(e)) {
            hookMoment();
            holderTrip = true;
            return; // the holder is inside the implosion: their screen carries it
        }
        if (hookStart >= 0.0 && exitStart < 0.0) {
            return; // we are already in a tunnel of our own
        }
        addEvent(new WorldEvent("hook", e.entityId(), e.pos(), gameNow(), seed(e), false, -1.0));
        PortkeyWorldFx.hookBurst(e);
        pendingPop = e.pos(); // played next tick unless a transit tells us we were taken along
    }

    private static void onTransit(FxClient.FxEvent e) {
        pendingPop = null;
        boolean wasHooked = hookStart >= 0.0 && exitStart < 0.0;
        maxTicks = Math.max(PortkeyRules.TRANSIT_MIN_TICKS, (int) e.param(0, PortkeyRules.TRANSIT_MAX_TICKS));
        crossDim = e.param(1, 0.0f) > 0.5f;
        old = e.param(3, 0.0f) > 0.5f;
        if (!wasHooked) {
            hookMoment();
        }
        holderTrip = e.param(2, 0.0f) > 0.5f;
        LocalPlayer player = Minecraft.getInstance().player;
        transitFrom = player == null ? e.pos() : player.position();
        transitDim = player == null ? null : player.level().dimension();
        // The implosion round us is not ours to watch from inside the tunnel.
        EVENTS.removeIf(w -> "hook".equals(w.kind) && w.pos.distanceToSqr(transitFrom) < 16.0);
    }

    /** The yank: flash, shake, the view flung wide (the timeline's h = 0), glints snapping to the boot. */
    private static void hookMoment() {
        double n = now();
        chargeStart = chargeStart < 0.0 ? n - PortkeyRules.CHARGE_TICKS : chargeStart;
        cancelStart = -1.0;
        hookStart = n;
        exitStart = -1.0;
        arrived = false;
        arrivedAt = -1.0;
        landed = false;
        noLanding = false;
        rollAtExit = 0.0f;
        lastShake = -1;
        Cinematic.flash(0.88f, 0.94f, 1.0f, PortkeyTimeline.HOOK_FLASH, PortkeyTimeline.HOOK_FLASH_TICKS);
        CameraShake.addLocal(PortkeyTimeline.HOOK_SHAKE, PortkeyTimeline.HOOK_SHAKE_TICKS,
                PortkeyTimeline.HOOK_SHAKE_FREQ);
        PortkeySounds.hook();
        PortkeySounds.startWind();
        PortkeyWorldFx.ownHookGlints();
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            transitFrom = player.position();
            transitDim = player.level().dimension();
        }
    }

    private static void onArrive(FxClient.FxEvent e) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean ours = hookStart >= 0.0 && exitStart < 0.0 && player != null
                && player.position().distanceTo(e.pos()) <= OWN_ARRIVAL;
        addEvent(new WorldEvent("arrive", -1, e.pos(), gameNow(), seed(e), false, -1.0));
        PortkeyDecals.add(e.pos(), gameNow());
        PortkeyWorldFx.arrivalBurst(e.pos(), seed(e));
        if (ours) {
            markArrived();
        } else {
            PortkeySounds.popArrive(e.pos());
        }
    }

    private static void markArrived() {
        if (!arrived) {
            arrived = true;
            arrivedAt = now();
        }
    }

    private static void onAbort(FxClient.FxEvent e) {
        if (hookStart >= 0.0 && exitStart < 0.0) {
            noLanding = true;
            beginExit();
        }
    }

    private static void onCrumble(FxClient.FxEvent e) {
        PortkeyWorldFx.crumbs(e);
        PortkeySounds.crumble(e.pos());
        if (self(e) && crumbleStart < 0.0) {
            crumbleStart = now(); // our own exit had not got there yet
        }
    }

    private static void onBind(FxClient.FxEvent e) {
        addEvent(new WorldEvent("bind", e.entityId(), e.pos(), gameNow(), seed(e), false, -1.0));
        PortkeySounds.bind(e.pos());
        PortkeyWorldFx.bindGlints(e.pos(), seed(e));
    }

    private static void beginExit() {
        Frame f = current();
        rollAtExit = f == null ? 0.0f : f.roll();
        exitStart = now();
    }

    // ---- the tick ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double n = now();
        if (!SCHEDULE.isEmpty()) {
            List<Scheduled> due = new ArrayList<>();
            SCHEDULE.removeIf(s -> {
                if (s.at <= n) {
                    due.add(s);
                    return true;
                }
                return false;
            });
            for (Scheduled s : due) {
                s.what.run();
            }
        }
        if (pendingPop != null) {
            PortkeySounds.popDepart(pendingPop);
            pendingPop = null;
        }
        tickTrip(mc, n);
        if (mc.level != null && !EVENTS.isEmpty()) {
            double g = mc.level.getGameTime();
            EVENTS.removeIf(e -> expired(e, g));
        }
        PortkeySounds.tick();
        if (mc.level != null && !mc.isPaused()) {
            PortkeyWorldFx.tickParticles(mc);
        }
    }

    private static boolean expired(WorldEvent e, double now) {
        float t = e.age(now);
        if (t < -5.0f) {
            return true; // a level whose clock went backwards: drop it
        }
        if ("charge".equals(e.kind)) {
            return e.end >= 0.0 ? now - e.end > PortkeyTimeline.CANCEL_TICKS : t > CHARGE_EVENT_MAX;
        }
        if ("hook".equals(e.kind)) {
            return t > HOOK_EVENT_TICKS;
        }
        if ("arrive".equals(e.kind)) {
            return t > ARRIVE_EVENT_TICKS;
        }
        if ("land".equals(e.kind)) {
            return t > LAND_EVENT_TICKS;
        }
        return t > PortkeyTimeline.BIND_TICKS;
    }

    private static void tickTrip(Minecraft mc, double n) {
        if (chargeStart >= 0.0 && hookStart < 0.0) {
            if ((cancelStart >= 0.0 && n - cancelStart > PortkeyTimeline.CANCEL_TICKS)
                    || n - chargeStart > CHARGE_EVENT_MAX) {
                resetTrip(); // let go, or the hook never came
            }
            return;
        }
        if (hookStart < 0.0) {
            return;
        }
        float h = (float) (n - hookStart);
        LocalPlayer player = mc.player;
        if (exitStart < 0.0) {
            if (player != null && transitFrom != null && (player.position().distanceTo(transitFrom) > JUMP
                    || (transitDim != null && player.level().dimension() != transitDim))) {
                markArrived(); // moved: across the world or into another dimension
            }
            boolean sectionReady = player != null && mc.levelRenderer.isSectionCompiled(player.blockPosition())
                    && !(mc.screen instanceof ReceivingLevelScreen);
            float since = arrivedAt < 0.0 ? 0.0f : (float) (n - arrivedAt);
            if (PortkeyTimeline.exitReady(h, arrived, sectionReady, since) || h >= maxTicks) {
                if (!arrived) {
                    noLanding = true; // timed out where we stood: no thump at a spot we never left
                }
                beginExit();
            } else {
                int beat = (int) (h / PortkeyTimeline.TRANSIT_SHAKE_EVERY);
                if (beat != lastShake && h >= PortkeyTimeline.HOOK_SHAKE_TICKS * 0.5f) {
                    lastShake = beat;
                    CameraShake.addLocal(PortkeyTimeline.TRANSIT_SHAKE, PortkeyTimeline.TRANSIT_SHAKE_TICKS,
                            PortkeyTimeline.TRANSIT_SHAKE_FREQ);
                }
                if (h >= PortkeyTimeline.TRANSIT_LETTERBOX_AT) {
                    Cinematic.letterbox(4); // refreshed every tick: the bars slide out soon after the exit
                }
            }
            return;
        }
        float x = (float) (n - exitStart);
        if (old && holderTrip && !noLanding && crumbleStart < 0.0 && x >= PortkeyTimeline.CRUMBLE_AT) {
            crumbleStart = exitStart + PortkeyTimeline.CRUMBLE_AT; // the Old Portkey falls apart in the hand
        }
        if (!landed && x >= PortkeyTimeline.LANDING_AT) {
            landed = true;
            if (!noLanding) {
                landingBeat(mc);
            }
        }
        if (x > TRIP_END) {
            resetTrip();
        }
    }

    /** The feet hit the ground: thump, shake, flash, dust, and the ring running out from where we stand. */
    private static void landingBeat(Minecraft mc) {
        PortkeySounds.land();
        CameraShake.addLocal(PortkeyTimeline.LANDING_SHAKE, PortkeyTimeline.LANDING_SHAKE_TICKS,
                PortkeyTimeline.LANDING_SHAKE_FREQ);
        Cinematic.flash(1.0f, 1.0f, 1.0f, PortkeyTimeline.LANDING_FLASH, PortkeyTimeline.LANDING_FLASH_TICKS);
        LocalPlayer player = mc.player;
        if (player != null && mc.level != null) {
            Vec3 feet = player.position();
            addEvent(new WorldEvent("land", player.getId(), feet, mc.level.getGameTime(), player.getId() * 7919
                    + (int) mc.level.getGameTime(), false, -1.0));
            PortkeyWorldFx.landingDust(feet, player.getId() * 31 + (int) mc.level.getGameTime());
        }
    }

    private static void resetTrip() {
        chargeStart = -1.0;
        cancelStart = -1.0;
        hookStart = -1.0;
        exitStart = -1.0;
        arrivedAt = -1.0;
        arrived = false;
        landed = false;
        noLanding = false;
        crossDim = false;
        holderTrip = false;
        rollAtExit = 0.0f;
        transitFrom = null;
        transitDim = null;
        lastShake = -1;
        if (SCHEDULE.isEmpty() && rate != 1.0) {
            setRate(1.0);
        }
    }

    private static void clearAll() {
        resetTrip();
        crumbleStart = -1.0;
        pendingPop = null;
        SCHEDULE.clear();
        EVENTS.clear();
        setRate(1.0);
        PortkeySounds.stopAll();
        PortkeyDecals.clear();
        PortkeyPost.close();
    }

    // ---- the camera -------------------------------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Frame f = current();
        if (f == null) {
            return;
        }
        float scale = (float) FxConfig.get(FxConfig.CAMERA_SHAKE);
        if (scale <= 0.0f) {
            return;
        }
        float roll = f.roll();
        float pitch = f.pitch();
        float yaw = 0.0f;
        if (hookStart < 0.0 && chargeStart >= 0.0) {
            // The view's own tremble while the charge builds: smooth noise on the wall clock.
            double n = now();
            float e = PortkeyTimeline.chargeEase((float) (n - chargeStart)) * cancelFade(n);
            float amp = PortkeyTimeline.chargeNoiseDegrees(e) * (cancelStart < 0.0 ? 1.0f : cancelFade(n));
            float s = (float) ((n * 0.05) % 2000.0); // seconds
            float hz = PortkeyTimeline.bootTrembleHz(e);
            pitch += amp * (Mth.sin(s * hz * 6.2832f) * 0.6f + Mth.sin(s * hz * 11.3f + 1.7f) * 0.4f);
            yaw += amp * (Mth.sin(s * hz * 7.9f + 0.6f) * 0.6f + Mth.sin(s * hz * 13.7f + 2.9f) * 0.4f);
        }
        event.setRoll(event.getRoll() + roll * scale);
        event.setPitch(event.getPitch() + pitch * scale);
        event.setYaw(event.getYaw() + yaw * scale);
    }

    /** Per frame, not through the FOV modifier (vanilla eases that and would smear the punch into a ramp). */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!event.usedConfiguredFov()) {
            return;
        }
        Frame f = current();
        if (f == null || Math.abs(f.fov() - 1.0f) < 1.0e-4f) {
            return;
        }
        event.setFOV(Mth.clamp(event.getFOV() * f.fov(), 10.0, 160.0));
    }

    // ---- covers for screens -----------------------------------------------------------------------------------

    /** A dimension change shows its loading screen: the tunnel goes on over it, so the world never pops out. */
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof ReceivingLevelScreen && transitActive()) {
            PortkeyHud.drawFallbackTunnel(event.getGuiGraphics(), 1.0f);
        }
    }

    // ---- lifecycle --------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    /** A dimension change or a respawn: the world effects belong to the old level; a trip in flight carries on. */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        EVENTS.clear();
        pendingPop = null;
        PortkeyDecals.clear();
        PortkeySounds.stopCharges();
        if (hookStart < 0.0) {
            resetTrip(); // a charge does not survive a respawn
        }
    }

    // ---- the debug command ------------------------------------------------------------------------------------

    /**
     * {@code /portkeyfx <charge|hook|transit|land|all> [rate]}: plays our own sequence with no teleport ({@code all}
     * fakes an arrival 30 ticks after the hook). {@code rate} (0.1..1) slows the wall clock for screenshots.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("portkeyfx")
                .then(Commands.argument("part", StringArgumentType.word())
                        .suggests((c, b) -> {
                            for (String s : new String[]{"charge", "hook", "transit", "land", "all"}) {
                                b.suggest(s);
                            }
                            return b.buildFuture();
                        })
                        .executes(c -> debug(StringArgumentType.getString(c, "part"), 1.0f))
                        .then(Commands.argument("rate", FloatArgumentType.floatArg(0.1f, 1.0f))
                                .executes(c -> debug(StringArgumentType.getString(c, "part"),
                                        FloatArgumentType.getFloat(c, "rate"))))));
    }

    private static int debug(String part, float r) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 0;
        }
        clearAll();
        setRate(r);
        int id = player.getId();
        Vec3 p = player.position();
        double n = now();
        Runnable charge = () -> FxClient.play(FxKinds.PORTKEY_CHARGE, p.x, p.y, p.z, id, PortkeyRules.CHARGE_TICKS, 0);
        Runnable hook = () -> {
            FxClient.play(FxKinds.PORTKEY_HOOK, p.x, p.y, p.z, id, (float) PortkeyRules.GROUP_RADIUS);
            FxClient.play(FxKinds.PORTKEY_TRANSIT, p.x, p.y, p.z, id, PortkeyRules.TRANSIT_MAX_TICKS, 0, 1, 0);
        };
        Runnable transit = () -> FxClient.play(FxKinds.PORTKEY_TRANSIT, p.x, p.y, p.z, id,
                PortkeyRules.TRANSIT_MAX_TICKS, 0, 1, 0);
        Runnable arrive = () -> {
            LocalPlayer me = Minecraft.getInstance().player;
            if (me != null) {
                FxClient.play(FxKinds.PORTKEY_ARRIVE, me.getX(), me.getY(), me.getZ(), -1, 1);
            }
        };
        float fakeArrive = 30.0f;
        if ("charge".equals(part)) {
            charge.run();
            SCHEDULE.add(new Scheduled(n + PortkeyRules.CHARGE_TICKS,
                    () -> FxClient.play(FxKinds.PORTKEY_CANCEL, p.x, p.y, p.z, id)));
        } else if ("hook".equals(part)) {
            hook.run();
            SCHEDULE.add(new Scheduled(n + fakeArrive, arrive));
        } else if ("transit".equals(part)) {
            transit.run();
            SCHEDULE.add(new Scheduled(n + fakeArrive, arrive));
        } else if ("land".equals(part)) {
            hookStart = n - fakeArrive; // straight to the exit: the tunnel lets go and we land
            chargeStart = hookStart - PortkeyRules.CHARGE_TICKS;
            transitFrom = p;
            transitDim = player.level().dimension();
            holderTrip = true;
            markArrived();
            PortkeySounds.startWind();
            beginExit();
        } else if ("all".equals(part)) {
            charge.run();
            SCHEDULE.add(new Scheduled(n + PortkeyRules.CHARGE_TICKS, hook));
            SCHEDULE.add(new Scheduled(n + PortkeyRules.CHARGE_TICKS + fakeArrive, arrive));
        } else {
            player.displayClientMessage(Component.literal("portkeyfx: charge, hook, transit, land or all"), false);
            return 0;
        }
        return 1;
    }

    // ---- setup ------------------------------------------------------------------------------------------------

    @Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            FxClient.register(FxKinds.PORTKEY_CHARGE, PortkeyClient::onCharge);
            FxClient.register(FxKinds.PORTKEY_CANCEL, PortkeyClient::onCancel);
            FxClient.register(FxKinds.PORTKEY_HOOK, PortkeyClient::onHook);
            FxClient.register(FxKinds.PORTKEY_TRANSIT, PortkeyClient::onTransit);
            FxClient.register(FxKinds.PORTKEY_ARRIVE, PortkeyClient::onArrive);
            FxClient.register(FxKinds.PORTKEY_ABORT, PortkeyClient::onAbort);
            FxClient.register(FxKinds.PORTKEY_CRUMBLE, PortkeyClient::onCrumble);
            FxClient.register(FxKinds.PORTKEY_BIND, PortkeyClient::onBind);
            FxOverlays.register(FxOverlays.ORDER_WORLD_TINT + 20, "portkey", PortkeyHud::render);
            event.enqueueWork(() -> {
                ResourceLocation charge = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "charge");
                ItemProperties.register(ExampleMod.PORTKEY.get(), charge,
                        (stack, level, entity, seed) -> entity == null ? 0.0f : chargeOf(entity.getId(),
                                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
                ItemProperties.register(ExampleMod.OLD_PORTKEY.get(), charge,
                        (stack, level, entity, seed) -> entity == null ? 0.0f : chargeOf(entity.getId(),
                                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false)));
            });
        }
    }
}
