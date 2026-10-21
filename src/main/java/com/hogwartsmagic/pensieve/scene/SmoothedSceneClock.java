package com.hogwartsmagic.pensieve.scene;

/**
 * A scene clock that follows the server's time smoothly instead of jumping with it. Pure; no Minecraft types.
 *
 * <p>Vanilla overwrites the client's game time with the server's every 20 server ticks, so a raw
 * {@code gameTime + partial} scene clock jumps whenever the two disagree, and every camera and actor jerks with it.
 * This clock has three parts.
 *
 * <p><b>1. Samples.</b> Every time sync the client receives is a sample: the client's own tick count (one per
 * {@link #tick}; it only moves forward) and the scene time the client was set to. A sync that moves nothing, because
 * the client already agreed, is as good a sample as one that pulls the client back: it says the server kept pace.
 *
 * <p><b>2. An estimate of the server's time</b>: a least-squares line through the recent samples (at most
 * {@link #FIT_WINDOW_TICKS} old, at most {@link #FIT_SAMPLES} of them), which smooths packet jitter. The line uses only
 * the newest run of samples that fit one line to within {@link #FIT_TOLERANCE} ticks. When the server changes speed
 * (lag starts or ends, a stall, a catch-up race, a freeze) the older samples stop fitting and leave the fit at once,
 * instead of dragging the estimate for the whole window. Two samples always fit, so the newest two are the shortest
 * run; with a single sample (just after a reset) the server is taken to keep pace.
 *
 * <p>The estimate is also kept under the <b>schedule</b>: the highest line of slope 1 through any sample of the last
 * {@link #SCHEDULE_TICKS} ticks. A server keeps to a real-time schedule and only runs faster than it to catch up
 * with it after a hitch, never past it. So a server racing back is expected to stop racing where it meets the
 * schedule, and the clock is never sent past that point.
 *
 * <p><b>3. A follower.</b> The playback rate is steered toward the estimate by a critically damped second-order
 * loop: each tick the rate changes by {@code OMEGA^2 * error + 2 * OMEGA * (server rate - rate)}, by at most
 * {@link #MAX_RATE_STEP}. So the speed always changes gradually and settles on the server's speed without
 * overshooting. Near the schedule the rate is also kept low enough that it can still brake to 1 in time, so the clock
 * does not run past the schedule either. The rate is never negative (the clock never steps back: ahead of the
 * server it slows, as far as a hold), and an error far beyond any recovery snaps ({@link #SNAP_FORWARD_TICKS},
 * {@link #SNAP_BACK_TICKS}). Within {@link #EXACT_TICKS} the clock lands exactly on the estimate, so tick work
 * (actions, events) falls on whole ticks. On a steady 20 tps server every sample lies on one slope-1 line, the fit is
 * exact, and the clock runs at exactly 1.
 *
 * <p>{@link #tick} is called once per client tick with the client's scene time for that tick's end. It advances by
 * the rate chosen on the previous tick, so the clock is continuous from the last frame of one tick to the first
 * frame of the next, then picks the next rate. Frames read {@link #at}: the tick's base plus the partial tick times
 * that rate.
 */
public final class SmoothedSceneClock {
    /** Largest change of the playback rate in one tick. */
    public static final double MAX_RATE_STEP = 0.05;
    /**
     * The follower's natural frequency, per tick: it settles in a few times {@code 1/OMEGA} (about 17 ticks). Faster
     * follows a change of server speed more closely but passes more packet jitter on to the speed.
     */
    public static final double OMEGA = 0.06;
    /** Fastest the clock plays (a server racing back after a hitch runs at about double speed). */
    public static final double MAX_RATE = 3;
    /** The fitted server rate is kept within 0..this. */
    public static final double MAX_SERVER_RATE = 2.5;
    /**
     * Behind by more than this many ticks: snap forward. About three times the largest lag a recovery reaches (a
     * slow server recovering, or the end of a freeze), so it only fires on a real desync, which easing would turn
     * into seconds of fast-forward.
     */
    public static final double SNAP_FORWARD_TICKS = 30;
    /**
     * Ahead by more than this many ticks: snap back. A stalled server cannot be seen until it syncs again, so the
     * clock runs on by the whole stall; this holds a stall of up to 5 s, never rewinding it.
     */
    public static final double SNAP_BACK_TICKS = 100;
    /** Within this of the estimate, land on it exactly. */
    public static final double EXACT_TICKS = 0.02;
    /** Samples older than this many client ticks leave the fit (the newest two always stay). */
    public static final int FIT_WINDOW_TICKS = 100;
    /** The fit uses at most this many of the newest samples. */
    public static final int FIT_SAMPLES = 12;
    /** A sample further than this from the fitted line ends the run of samples the line is fitted to. */
    public static final double FIT_TOLERANCE = 4;
    /** The schedule is the highest slope-1 line through a sample of this many recent ticks. */
    public static final int SCHEDULE_TICKS = 200;
    /** Samples kept (enough for the schedule at a racing server's sync rate). */
    static final int CAPACITY = 32;

    private final long[] sampleTick = new long[CAPACITY];
    private final double[] sampleValue = new double[CAPACITY];
    /** Samples kept, the oldest at {@code first}. */
    private int first;
    private int count;

    private boolean running;
    /** Client ticks since the reset. */
    private long now;
    private double lastTarget;
    private double base;
    private double rate = 1;
    private double estimate;
    private double serverRate = 1;
    private double schedule;
    private double lastError;

    /** Forget everything: the next {@link #tick} snaps to the client's time. */
    public void reset() {
        running = false;
        now = 0;
        count = 0;
        first = 0;
        base = 0;
        rate = 1;
        estimate = 0;
        serverRate = 1;
        schedule = 0;
        lastError = 0;
    }

    public boolean running() {
        return running;
    }

    /**
     * One client tick has ended and {@code target} is the client's scene time at that moment (the level's game time
     * minus the scene's start). {@code synced} says a time sync from the server arrived since the last tick; a change
     * of the client's time other than +1 is taken as one too, since only the server moves it so. Returns true when
     * the clock snapped (the first tick, or past a snap limit).
     */
    public boolean tick(double target, boolean synced) {
        if (!running) {
            running = true;
            now = 0;
            count = 0;
            first = 0;
            addSample(0, target);
            lastTarget = target;
            base = target;
            rate = 1;
            estimate = target;
            serverRate = 1;
            schedule = target;
            lastError = 0;
            return true;
        }
        now++;
        if (synced || target - lastTarget != 1) addSample(now, target);
        lastTarget = target;
        fitEstimate();

        base += rate;
        double err = estimate - base;
        lastError = err;
        if (err > SNAP_FORWARD_TICKS || err < -SNAP_BACK_TICKS) {
            base = estimate;
            rate = Math.max(0, Math.min(MAX_RATE, serverRate));
            return true;
        }
        double old = rate;
        if (Math.abs(err) < EXACT_TICKS && Math.abs(old - serverRate) + Math.abs(err) <= MAX_RATE_STEP) {
            if (err >= 0) {
                base = estimate;                      // a forward nudge too small to see
                rate = serverRate;
            } else {
                rate = Math.max(0, serverRate + err); // lands on the estimate next tick, without stepping back
            }
            return false;
        }
        double accel = OMEGA * OMEGA * err + 2 * OMEGA * (serverRate - old);
        double want = old + Math.max(-MAX_RATE_STEP, Math.min(MAX_RATE_STEP, accel));
        // No faster than can still brake to the schedule's speed (1) by the time the clock reaches it.
        double ceiling = 1 + Math.sqrt(2 * MAX_RATE_STEP * Math.max(0, schedule - base));
        want = Math.min(want, Math.max(ceiling, old - MAX_RATE_STEP));
        rate = Math.max(0, Math.min(MAX_RATE, want));
        return false;
    }

    private void addSample(long tick, double value) {
        if (count == CAPACITY) {
            first = (first + 1) % CAPACITY;
            count--;
        }
        int i = (first + count) % CAPACITY;
        sampleTick[i] = tick;
        sampleValue[i] = value;
        count++;
        while (count > 2 && tick - sampleTick[first] > SCHEDULE_TICKS) {
            first = (first + 1) % CAPACITY;
            count--;
        }
    }

    /** The ring index of the sample {@code back} places before the newest. */
    private int index(int back) {
        return (first + count - 1 - back) % CAPACITY;
    }

    /** The server's time and rate at {@link #now}, under the schedule. */
    private void fitEstimate() {
        int newest = index(0);
        long kn = sampleTick[newest];
        double vn = sampleValue[newest];
        // The fit's samples: the newest two, then on back while they are recent enough.
        int n = 2;
        while (n < Math.min(count, FIT_SAMPLES) && kn - sampleTick[index(n)] <= FIT_WINDOW_TICKS) n++;
        n = Math.min(n, count);
        double atNewest = vn;
        double slope = 1;
        for (int m = n; m >= 2; m--) {
            // Least squares on the newest m samples, as offsets from a slope-1 line through the newest one. A server
            // keeping pace gives all zeros, so the fit (and the clock) stays exact.
            double sx = 0;
            double sz = 0;
            for (int j = 0; j < m; j++) {
                int i = index(j);
                sx += sampleTick[i] - kn;
                sz += (sampleValue[i] - sampleTick[i]) - (vn - kn);
            }
            double mx = sx / m;
            double mz = sz / m;
            double sxx = 0;
            double sxz = 0;
            for (int j = 0; j < m; j++) {
                int i = index(j);
                double x = sampleTick[i] - kn - mx;
                sxx += x * x;
                sxz += x * ((sampleValue[i] - sampleTick[i]) - (vn - kn) - mz);
            }
            double c = sxx > 0 ? sxz / sxx : 0;
            double at = vn + (mz - c * mx);
            double b = 1 + c;
            boolean fits = true;
            for (int j = 0; j < m && fits && m > 2; j++) {
                int i = index(j);
                fits = Math.abs(sampleValue[i] - (at + b * (sampleTick[i] - kn))) <= FIT_TOLERANCE;
            }
            if (fits) {
                atNewest = at;
                slope = b;
                break;
            }
        }
        serverRate = Math.max(0, Math.min(MAX_SERVER_RATE, slope));
        estimate = atNewest + serverRate * (now - kn);
        double offset = -Double.MAX_VALUE;
        for (int j = 0; j < count; j++) {
            int i = index(j);
            if (j >= 1 && kn - sampleTick[i] > SCHEDULE_TICKS) break;
            offset = Math.max(offset, sampleValue[i] - sampleTick[i]);
        }
        schedule = now + offset;
        if (estimate > schedule) {
            estimate = schedule;
            serverRate = Math.min(serverRate, 1);
        }
    }

    /** The scene time of a frame {@code partial} (0..1) of a tick after the last {@link #tick}. */
    public double at(float partial) {
        return base + Math.max(0f, Math.min(1f, partial)) * rate;
    }

    /** The estimated server time minus this clock's, as measured at the last {@link #tick}. */
    public double lastError() {
        return lastError;
    }

    /** The speed the clock runs at until the next {@link #tick}: 0 (a hold) to {@link #MAX_RATE}. */
    public double rate() {
        return rate;
    }

    /** The estimated server tick rate, as a fraction of the client's (1 when it keeps pace). */
    public double serverRate() {
        return serverRate;
    }

    /** The estimated server scene time at the last {@link #tick}. */
    public double estimate() {
        return estimate;
    }
}
