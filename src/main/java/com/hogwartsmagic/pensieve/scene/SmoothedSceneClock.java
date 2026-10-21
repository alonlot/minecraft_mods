package com.hogwartsmagic.pensieve.scene;

/**
 * A scene clock that eases toward the server's instead of jumping with it. Pure; no Minecraft types.
 *
 * <p>Vanilla overwrites the client's game time with the server's every 20 server ticks, so a raw
 * {@code gameTime + partial} scene clock jumps whenever the two disagree, and every camera and actor jerks with it.
 * This clock keeps its own value instead. {@link #tick} is called once per client tick with the client's scene time
 * for that tick's end (the level's game time minus the start). It advances by the rate chosen on the previous tick, so
 * the clock is continuous from the last frame of one tick to the first frame of the next. Then it picks the next
 * tick's rate to close the error against its estimate of the server's time. Frames read {@link #at}: the tick's base
 * plus the partial tick times that rate.
 *
 * <p>How it works:
 * <ul>
 *   <li><b>The server's time</b> is estimated, not read. Each sync (a change of the client's time other than +1)
 *       measures how far the server got since the last one. The server rate is the ratio of a running average of those
 *       advances to a running average of the intervals. The estimate's anchor moves only part of the way to each sync's
 *       value, so packet jitter is smoothed out, not followed.</li>
 *   <li><b>Recovery:</b> a full-speed server's sync moves the client by nothing, so it cannot be seen. When a slow
 *       server's next pull-back is overdue by more than the intervals' own recent jitter, the server is taken to be
 *       back at full speed.</li>
 *   <li><b>The playback rate</b> is the estimated server rate plus a correction for the error. Behind, the correction
 *       is at most {@link #MAX_SPEED_UP}, ramping to {@link #MAX_CATCH_UP} when catching up after a recovery, and to
 *       double speed after a hitch while the server races back. Ahead, the clock slows as far as a hold, never
 *       backward. The correction is also limited so the clock can brake to the right speed without overshooting.</li>
 *   <li><b>Rate of change:</b> the rate moves by at most {@link #MAX_RATE_STEP} a tick, so nothing produces a cliff.</li>
 *   <li><b>Racing server</b> (measured faster than real time, catching up after a hitch): the clock aims at the
 *       client's own time, which a sync only ever moves forward to the server's. It never extrapolates past it, so it
 *       cannot overshoot when the race ends.</li>
 *   <li><b>Stall</b> ({@code /tick freeze}): the client's time stops moving, and the clock brakes to a hold.</li>
 *   <li><b>Snaps:</b> forward past {@link #SNAP_FORWARD_TICKS} (more after a hitch), back only past
 *       {@link #SNAP_BACK_TICKS}.</li>
 *   <li><b>Small errors:</b> within {@link #EXACT_TICKS} the clock lands exactly on the estimate. On a full-speed server
 *       tick work (actions, events) then falls on whole ticks.</li>
 * </ul>
 */
public final class SmoothedSceneClock {
    /** Speed-up for an ordinary lag: 5%, so a one-tick lag is absorbed over about a second. */
    public static final double MAX_SPEED_UP = 0.05;
    /** The speed-up ramps by this much per tick of lag beyond the first, after a recovery or a hitch. */
    public static final double CATCH_UP_RAMP = 0.08;
    /** The largest speed-up after a slow server recovers. */
    public static final double MAX_CATCH_UP = 0.25;
    /** Fastest the clock runs, following a server racing back after a hitch (double speed). */
    public static final double MAX_FOLLOW_RATE = 2.0;
    /** Largest change of the playback rate in one tick. */
    public static final double MAX_RATE_STEP = 0.05;
    /** Rate correction per tick of error behind, before the caps. */
    public static final double GAIN_BEHIND = 0.2;
    /** Rate correction per tick of error ahead. */
    public static final double GAIN_AHEAD = 0.05;
    /** Behind by more than this many ticks: snap forward. */
    public static final double SNAP_FORWARD_TICKS = 10;
    /** The forward snap limit while a server races back after a hitch, when a long lag is expected. */
    public static final double SNAP_FORWARD_RACING_TICKS = 40;
    /** Ahead by more than this many ticks: snap back. A slow server or a hitch of up to 5 s is held, not rewound. */
    public static final double SNAP_BACK_TICKS = 100;
    /** Within this of the estimate, land on it exactly. */
    public static final double EXACT_TICKS = 0.02;
    /** Server ticks between two of vanilla's time syncs. */
    static final int SYNC_TICKS = 20;
    /** Smallest margin by which a slow server's pull-back must be overdue to mean it is keeping pace again. */
    public static final int SYNC_MARGIN = 2;
    /** The overdue margin is this many times the recent jitter of the sync intervals, plus a tick. */
    static final double JITTER_MARGIN = 1.5;
    /** Per-sync decay of the recent-jitter estimate (a decaying maximum). */
    static final double JITTER_DECAY = 0.99;
    /** A change of the client's time other than +1 this soon after the last sync is not measured. */
    static final int MIN_SYNC_GAP = 10;
    /** Weight of the newest sync in the averaged advance and interval. */
    static final double RATE_MEMORY = 0.3;
    /** How far the estimate's anchor moves toward each sync's value. */
    static final double ANCHOR_GAIN = 0.35;
    /** A sync further than this from the estimate re-anchors it outright. */
    static final double ANCHOR_RESET_TICKS = 8;
    /** A server measured below this rate has really been slow: its recovery may be caught up at the ramped speed. */
    static final double SLOW_RATE = 0.85;

    private boolean running;
    private double base;
    private double rate = 1;
    private double lastError;

    private double lastTarget;
    /** The estimate's (smoothed) anchor, and the client's time at the last sync. */
    private double anchor;
    private double syncTarget;
    private int sinceSync;
    private double serverRate = 1;
    /** The last interval's rate as measured (above 1 while a server races back). */
    private double measuredRate = 1;
    private double advanceAvg = SYNC_TICKS;
    private double intervalAvg = SYNC_TICKS;
    private double jitter;
    private int stall;
    /** Catching up after a slow server's recovery, or after a race: the larger speed-ups are allowed. */
    private boolean recovering;
    private boolean wasRacing;

    /** Forget everything: the next {@link #tick} snaps to the authoritative time. */
    public void reset() {
        running = false;
        base = 0;
        rate = 1;
        lastError = 0;
        jitter = 0;
        stall = 0;
        recovering = false;
        wasRacing = false;
        fullSpeed(0);
    }

    public boolean running() {
        return running;
    }

    /**
     * One client tick has ended and {@code target} is the client's scene time at that moment (the level's game time,
     * pulled to the server's at each sync). Returns true when the clock snapped (first tick, or past a snap limit).
     */
    public boolean tick(double target) {
        if (!running) {
            snap(target);
            lastTarget = target;
            fullSpeed(target);
            stall = 0;
            lastError = 0;
            return true;
        }
        double step = target - lastTarget;
        lastTarget = target;
        sinceSync++;
        stall = step == 0 ? stall + 1 : 0;
        if (stall >= 2) {
            // Stopped (a frozen tick rate): hold here, and pick up at full speed once it moves again.
            fullSpeed(target);
        } else if (step != 1) {
            sync(target);
        } else if ((serverRate != 1 || measuredRate != 1)
                && sinceSync > intervalAvg + Math.max(SYNC_MARGIN, JITTER_MARGIN * jitter + 1)) {
            // The pull-back a slow (or racing) server would have sent is overdue: a sync came and moved nothing, so
            // the client's time is the server's again.
            recovering = serverRate < SLOW_RATE || racing();
            wasRacing = racing();
            fullSpeed(target);
        }
        boolean racing = racing();
        double modelRate = racing ? 1 : serverRate;
        double aim = racing ? target : anchor + modelRate * sinceSync;

        base += rate;
        double err = aim - base;
        lastError = err;
        double snapForward = racing || wasRacing ? SNAP_FORWARD_RACING_TICKS : SNAP_FORWARD_TICKS;
        if (err > snapForward || err < -SNAP_BACK_TICKS) {
            snap(aim);
            return true;
        }
        if (err < 0.5) {
            recovering = false;
            wasRacing = false;
        }
        double old = rate;
        double v0 = Math.min(1, modelRate);
        double want;
        if (err >= 0 && err < EXACT_TICKS && Math.abs(old - v0) <= MAX_RATE_STEP) {
            base = aim;                       // a forward nudge too small to see
            want = v0;
        } else if (err < 0 && err > -EXACT_TICKS && Math.abs(old - v0) <= MAX_RATE_STEP) {
            want = v0 + err;                  // lands on the estimate next tick, without stepping back
        } else if (err > 0) {
            double ramp = MAX_SPEED_UP + CATCH_UP_RAMP * Math.max(0, err - 1);
            double cap = MAX_SPEED_UP;
            if (recovering) cap = Math.min(MAX_CATCH_UP, ramp);
            if (racing || wasRacing) cap = Math.max(cap, Math.min(MAX_FOLLOW_RATE - 1, ramp));
            want = v0 + Math.min(cap, Math.min(err * GAIN_BEHIND, brake(err)));
        } else {
            want = Math.max(0, v0 - Math.min(1, Math.min(-err * GAIN_AHEAD, brake(-err))));
        }
        if (stall >= 2 && err <= 0) want = 0;
        rate = Math.max(old - MAX_RATE_STEP, Math.min(old + MAX_RATE_STEP, want));
        return false;
    }

    /** The largest speed difference from which the rate limit can still brake within {@code err} ticks. */
    private static double brake(double err) {
        return Math.sqrt(2 * MAX_RATE_STEP * err);
    }

    private boolean racing() {
        return measuredRate > 1 + MAX_CATCH_UP;
    }

    /** A sync moved the client's time to the server's {@code target}: measure, and move the estimate toward it. */
    private void sync(double target) {
        double predicted = anchor + (racing() ? 1 : serverRate) * sinceSync;
        boolean measured = sinceSync >= MIN_SYNC_GAP;
        if (measured) {
            double advance = target - syncTarget;
            measuredRate = Math.max(0, advance / sinceSync);
            jitter = Math.max(Math.abs(sinceSync - intervalAvg), jitter * JITTER_DECAY);
            advanceAvg += (advance - advanceAvg) * RATE_MEMORY;
            intervalAvg += (sinceSync - intervalAvg) * RATE_MEMORY;
            serverRate = Math.max(0, Math.min(1 + MAX_SPEED_UP, advanceAvg / intervalAvg));
        }
        boolean outright = racing() || !measured || Math.abs(target - predicted) > ANCHOR_RESET_TICKS;
        anchor = outright ? target : predicted + ANCHOR_GAIN * (target - predicted);
        syncTarget = target;
        sinceSync = 0;
    }

    /** The server is taken to keep pace from {@code target} on. */
    private void fullSpeed(double target) {
        serverRate = 1;
        measuredRate = 1;
        anchor = target;
        syncTarget = target;
        sinceSync = 0;
        advanceAvg = SYNC_TICKS;
        intervalAvg = SYNC_TICKS;
    }

    private void snap(double target) {
        running = true;
        base = target;
        rate = 1;
    }

    /** The scene time of a frame {@code partial} (0..1) of a tick after the last {@link #tick}. */
    public double at(float partial) {
        return base + Math.max(0f, Math.min(1f, partial)) * rate;
    }

    /** The estimated server time minus this clock's, as measured at the last {@link #tick}. */
    public double lastError() {
        return lastError;
    }

    /** The speed the clock runs at until the next {@link #tick}: 0 (a hold) to {@link #MAX_FOLLOW_RATE}. */
    public double rate() {
        return rate;
    }

    /** The estimated server tick rate, as a fraction of the client's (1 when it keeps pace). */
    public double serverRate() {
        return serverRate;
    }
}
