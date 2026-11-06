package com.hogwartsmagic.client.fx;

import com.hogwartsmagic.client.fx.moments.DrainPost;
import com.hogwartsmagic.client.fx.moments.FreezeFrame;
import com.hogwartsmagic.client.fx.moments.SlowMotion;
import com.hogwartsmagic.client.pensieve.PensieveClientState;
import com.hogwartsmagic.fx.FxConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The big-moment effects any client feature can call: letterbox bars, an impact flash, the colour draining out of
 * the world, a freeze-frame, and (single player only) the world slowing to a crawl. Each respects its
 * {@code fx.FxConfig} switch, and all of them stay out of the way while a Pensieve memory is on screen (a memory
 * runs its own camera).
 *
 * <p>What the player sees: two black bars slide in from the top and bottom edges (about a tenth of the screen
 * each), hold, and slide away; a colour flash fills the screen and fades; the world loses its colour and gains a
 * touch of contrast and a dark vignette, then the colour seeps back; the picture stops dead for a few frames on
 * the instant of the blow; and the whole world drops to a crawl for a second before it eases back to speed.
 *
 * <p>All timing here runs on real time (the wall clock, in 50 ms "ticks"), not game ticks, because slow motion
 * stretches game ticks and an effect that slowed with the world would drag on. Everything is main thread only.
 */
public final class Cinematic {

    /** Each bar's height as a share of the screen height. */
    private static final float BAR_SHARE = 0.11f;
    /** Real ticks for the bars to slide in, and out. */
    private static final float BAR_SLIDE_TICKS = 8.0f;
    /** No more than this many flashes are remembered at once. */
    private static final int MAX_FLASHES = 8;
    /** No more than this many colour drains are remembered at once. */
    private static final int MAX_DRAINS = 6;
    private static final double TICK_NANOS = 50_000_000.0;

    private static long barStart;
    private static long barHoldEnd;
    private static boolean barsOn;

    private static final List<Flash> FLASHES = new ArrayList<>();
    private static final List<Drain> DRAINS = new ArrayList<>();

    private record Flash(float r, float g, float b, float strength, long start, long end) {
    }

    private record Drain(float amount, long start, long end) {
    }

    private Cinematic() {
    }

    private static boolean blocked() {
        return PensieveClientState.inMemory();
    }

    private static long now() {
        return System.nanoTime();
    }

    private static long ticks(int ticks) {
        return (long) (Math.max(0, ticks) * TICK_NANOS);
    }

    /** Black bars slide in, hold for {@code holdTicks}, slide out. Overlapping calls extend the hold. */
    public static void letterbox(int holdTicks) {
        if (blocked() || !FxConfig.get(FxConfig.LETTERBOX)) {
            return;
        }
        long t = now();
        float current = barAmount(t);
        long hold = ticks(holdTicks);
        if (barsOn && current > 0.0f) {
            // Already up (or on its way in or out): keep going from where the bars are now.
            long in = (long) (BAR_SLIDE_TICKS * TICK_NANOS);
            if (t > barHoldEnd) {
                barStart = t - (long) (current * in); // was sliding out: slide back in from here
            }
            barHoldEnd = Math.max(barHoldEnd, t + hold);
        } else {
            barsOn = true;
            barStart = t;
            barHoldEnd = t + (long) (BAR_SLIDE_TICKS * TICK_NANOS) + hold;
        }
    }

    private static float barAmount(long t) {
        if (!barsOn) {
            return 0.0f;
        }
        double slide = BAR_SLIDE_TICKS * TICK_NANOS;
        float in = (float) Mth.clamp((t - barStart) / slide, 0.0, 1.0);
        float out = (float) Mth.clamp((t - barHoldEnd) / slide, 0.0, 1.0);
        if (out >= 1.0f) {
            barsOn = false;
            return 0.0f;
        }
        return Math.min(ease(in), 1.0f - ease(out));
    }

    private static float ease(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    /** A full-screen flash of colour (0..1 rgb) at {@code strength} (0..1), fading over {@code ticks}. */
    public static void flash(float r, float g, float b, float strength, int ticks) {
        if (blocked() || strength <= 0.0f || ticks <= 0) {
            return;
        }
        long t = now();
        if (FLASHES.size() >= MAX_FLASHES) {
            FLASHES.remove(0);
        }
        FLASHES.add(new Flash(Mth.clamp(r, 0, 1), Mth.clamp(g, 0, 1), Mth.clamp(b, 0, 1),
                Mth.clamp(strength, 0, 1), t, t + ticks(ticks)));
    }

    /** The world's colour drains by {@code amount} (0..1, 1 = grey) and comes back over {@code ticks}. */
    public static void drain(float amount, int ticks) {
        if (blocked() || amount <= 0.0f || ticks <= 0) {
            return;
        }
        long t = now();
        if (DRAINS.size() >= MAX_DRAINS) {
            DRAINS.remove(0);
        }
        DRAINS.add(new Drain(Mth.clamp(amount, 0, 1), t, t + ticks(ticks)));
    }

    /** The strongest colour drain right now, 0 when none: what {@link DrainPost} should apply this frame. */
    public static float drainAmount() {
        if (DRAINS.isEmpty()) {
            return 0.0f;
        }
        if (blocked()) {
            DRAINS.clear();
            return 0.0f;
        }
        long t = now();
        float best = 0.0f;
        for (int i = DRAINS.size() - 1; i >= 0; i--) {
            Drain d = DRAINS.get(i);
            if (t >= d.end) {
                DRAINS.remove(i);
                continue;
            }
            float x = (float) ((t - d.start) / (double) (d.end - d.start));
            // Quick fall to grey, a long hold, then the colour seeps back.
            float env = x < 0.12f ? ease(x / 0.12f) : x < 0.45f ? 1.0f : 1.0f - ease((x - 0.45f) / 0.55f);
            best = Math.max(best, d.amount * env);
        }
        return best;
    }

    /** The picture holds still for {@code frames} rendered frames (an impact frame), then carries on. */
    public static void freeze(int frames) {
        if (blocked() || frames <= 0 || !FxConfig.get(FxConfig.CINEMATIC_MOMENTS)) {
            return;
        }
        FreezeFrame.request(frames);
    }

    /**
     * Single player only (no-op otherwise or when switched off): the world runs at {@code rate} (0.1..1) of normal
     * speed for {@code realTicks} real ticks, then eases back.
     */
    public static void slowMotion(float rate, int realTicks) {
        if (blocked() || !FxConfig.get(FxConfig.SLOW_MOTION)) {
            return;
        }
        SlowMotion.start(rate, realTicks);
    }

    /** Drops every running effect (logout, a memory starting). */
    public static void clear() {
        barsOn = false;
        FLASHES.clear();
        DRAINS.clear();
        FreezeFrame.clear();
        SlowMotion.cancel();
    }

    /** The overlay layer for the flashes, at {@code FxOverlays.ORDER_FLASH}. */
    public static void renderFlashes(GuiGraphics graphics, DeltaTracker delta) {
        if (FLASHES.isEmpty()) {
            return;
        }
        if (blocked()) {
            FLASHES.clear();
            return;
        }
        long t = now();
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        for (int i = FLASHES.size() - 1; i >= 0; i--) {
            Flash f = FLASHES.get(i);
            if (t >= f.end) {
                FLASHES.remove(i);
                continue;
            }
            float x = (float) ((t - f.start) / (double) (f.end - f.start));
            float fade = (1.0f - x) * (1.0f - x);
            int alpha = Mth.clamp((int) (f.strength * fade * 255.0f), 0, 255);
            if (alpha <= 0) {
                continue;
            }
            int argb = alpha << 24 | (int) (f.r * 255) << 16 | (int) (f.g * 255) << 8 | (int) (f.b * 255);
            graphics.fill(0, 0, w, h, argb);
        }
    }

    /** The overlay layer for the letterbox bars, at {@code FxOverlays.ORDER_LETTERBOX}. */
    public static void renderBars(GuiGraphics graphics, DeltaTracker delta) {
        if (!barsOn) {
            return;
        }
        if (blocked()) {
            barsOn = false;
            return;
        }
        float amount = barAmount(now());
        if (amount <= 0.0f) {
            return;
        }
        int w = graphics.guiWidth();
        int h = graphics.guiHeight();
        int bar = Math.round(h * BAR_SHARE * amount);
        if (bar <= 0) {
            return;
        }
        graphics.fill(0, 0, w, bar, 0xFF000000);
        graphics.fill(0, h - bar, w, h, 0xFF000000);
    }
}
