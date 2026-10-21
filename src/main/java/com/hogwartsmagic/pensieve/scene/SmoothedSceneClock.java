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
 * <p>The error is treated differently in each direction:
 * <ul>
 *   <li><b>Behind</b> (the server is ahead): catch up at most {@link #MAX_SPEED_UP} faster; snap past
 *       {@link #SNAP_FORWARD_TICKS}, for a join or a long hitch.</li>
 *   <li><b>Ahead</b>: slow down as much as it takes, down to a hold, but never run backward; snap back only past
 *       {@link #SNAP_BACK_TICKS}, for a real desync. A server below 20 tps is the common case. The client still adds 1
 *       a tick and each sync pulls it back, so the clock must be allowed to run slower than real time.</li>
 *   <li><b>Server rate:</b> estimated from successive syncs (how far the client's time moved between two pull-backs).
 *       Between syncs the clock aims for the time that rate predicts, not the client's own run-ahead, so on a slow
 *       server it plays at a steady slower speed instead of a speed that pulses at every sync.</li>
 *   <li><b>Stall</b> ({@code /tick freeze}): the client's time stops moving, and the clock holds within a tick.</li>
 *   <li><b>Small errors:</b> once within {@link #EXACT_TICKS}, the clock lands exactly on the server's time. Tick work
 *       (actions, events) then falls on whole ticks again instead of creeping up on them from below.</li>
 * </ul>
 */
public final class SmoothedSceneClock {
    /** Largest speed-up when behind: 5%, so a one-tick lag is absorbed over about a second. */
    public static final double MAX_SPEED_UP = 0.05;
    /** Rate change per tick of error behind, before the {@link #MAX_SPEED_UP} cap. */
    public static final double GAIN_BEHIND = 0.2;
    /** Rate change per tick of error ahead; the rate goes down to 0 (a hold), never below. */
    public static final double GAIN_AHEAD = 0.1;
    /** Behind by more than this many ticks: snap forward. */
    public static final double SNAP_FORWARD_TICKS = 10;
    /** Ahead by more than this many ticks: snap back. Only a real desync gets here, not a slow server. */
    public static final double SNAP_BACK_TICKS = 40;
    /** Within this of the server's time, land on it exactly. */
    public static final double EXACT_TICKS = 0.02;
    /** A change of the client's time other than +1 this soon after the last sync is not taken as a new one. */
    static final int MIN_SYNC_GAP = 10;
    /** Weight of the newest sync in the server-rate estimate. */
    static final double RATE_MEMORY = 0.3;
    /** With no sync for this many ticks the server is keeping pace, and the estimate drifts back to 1. */
    static final int RATE_DRIFT_TICKS = 60;

    private boolean running;
    private double base;
    private double rate = 1;
    private double lastError;

    private double lastTarget;
    private double syncTarget;
    private int sinceSync;
    private double serverRate = 1;
    private int stall;

    /** Forget everything: the next {@link #tick} snaps to the authoritative time. */
    public void reset() {
        running = false;
        base = 0;
        rate = 1;
        lastError = 0;
        serverRate = 1;
        stall = 0;
        sinceSync = 0;
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
            lastTarget = syncTarget = target;
            sinceSync = 0;
            serverRate = 1;
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
            serverRate = 1;
            syncTarget = target;
            sinceSync = 0;
        } else if (step != 1) {
            // A sync moved the client's time. Measure the server's rate over the last interval, if it was long
            // enough to mean anything, and aim from here.
            if (sinceSync >= MIN_SYNC_GAP) {
                double r = (target - syncTarget) / sinceSync;
                serverRate += (Math.max(0, Math.min(1 + MAX_SPEED_UP, r)) - serverRate) * RATE_MEMORY;
            }
            syncTarget = target;
            sinceSync = 0;
        } else if (sinceSync > RATE_DRIFT_TICKS) {
            serverRate += (1 - serverRate) * 0.05;
        }
        double aim = Math.min(target, syncTarget + serverRate * sinceSync);

        base += rate;
        double err = aim - base;
        lastError = err;
        if (err > SNAP_FORWARD_TICKS || err < -SNAP_BACK_TICKS) {
            snap(aim);
            return true;
        }
        if (err >= 0 && err < EXACT_TICKS) {
            base = aim;                       // a forward nudge too small to see
            rate = 1;
        } else if (err < 0 && err > -EXACT_TICKS) {
            rate = 1 + err;                   // lands on the server's time next tick, without stepping back
        } else if (err > 0) {
            rate = 1 + Math.min(MAX_SPEED_UP, err * GAIN_BEHIND);
        } else {
            rate = Math.max(0, 1 + err * GAIN_AHEAD);
        }
        if (stall >= 2 && err <= 0) rate = 0;
        return false;
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

    /** The speed the clock runs at until the next {@link #tick}: 0 (a hold) to 1 + {@link #MAX_SPEED_UP}. */
    public double rate() {
        return rate;
    }

    /** The estimated server tick rate, as a fraction of the client's (1 when it keeps pace). */
    public double serverRate() {
        return serverRate;
    }
}
