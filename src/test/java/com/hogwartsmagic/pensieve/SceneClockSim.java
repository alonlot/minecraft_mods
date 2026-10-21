package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.SmoothedSceneClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntUnaryOperator;

/**
 * A simulated vanilla server and client feeding {@link SmoothedSceneClock}, as in game.
 *
 * <ul>
 *   <li>The client ticks once per step {@code k}. Its game time goes up by 1 each tick, unless the tick rate is
 *       frozen ({@code /tick freeze}).</li>
 *   <li>The server makes {@code rate(k)} ticks per client tick (1 = 20 tps, 0 = a stalled server thread, 2 = racing
 *       to catch up). Every 20 server ticks ({@code tickCount % 20}) it sends its game time, before that tick
 *       advances it. While frozen, its tick count still runs, so syncs keep coming with the same game time.</li>
 *   <li>A sync arrives 0..{@code jitter} client ticks late (uniform, plus any {@link #delay}), carrying the time it
 *       was sent with, and never before the sync sent ahead of it (TCP is in order). On arrival
 *       the client takes that time, then its tick adds 1 (vanilla's order: packets, then the tick), and the
 *       clock sees it at the tick's end with {@code synced} set.</li>
 * </ul>
 * The error is the clock minus the server's true time (its game time plus the fraction of the next tick).
 */
public final class SceneClockSim {
    public static final int FRAMES = 3; // 60 fps at 20 tps

    IntToDoubleFunction rate = k -> 1;
    IntPredicate frozen = k -> false;
    /** Extra delay of the sync with this index (counting from 0), on top of the jitter: a lag spike. */
    IntUnaryOperator delay = i -> 0;
    int ticks = 1200;
    int from = 100;
    int jitter;
    long seed = 1;
    /** When set, the error is also tracked from here on, for the peak of a recovery. */
    int recoverFrom = Integer.MAX_VALUE;

    public record Result(double worstBack, double maxRateStep, int snaps, double maxAhead, double maxBehind,
                         double minRate, double maxRate, double meanRate, double meanAbsError, int lastOff1,
                         double recoverPeak, double tail, int longestHold) {
    }

    public SceneClockSim rate(IntToDoubleFunction f) {
        rate = f;
        return this;
    }

    public SceneClockSim ticks(int n, int measureFrom) {
        ticks = n;
        from = measureFrom;
        return this;
    }

    public SceneClockSim jitter(int j, long s) {
        jitter = j;
        seed = s;
        return this;
    }

    public SceneClockSim frozen(IntPredicate f) {
        frozen = f;
        return this;
    }

    public SceneClockSim spike(int sync, int ticksLate) {
        return delay(i -> i == sync ? ticksLate : 0);
    }

    public SceneClockSim delay(IntUnaryOperator f) {
        delay = f;
        return this;
    }

    public SceneClockSim recoverFrom(int k) {
        recoverFrom = k;
        return this;
    }

    /** Stall {@code h} ticks from {@code start}, then race at 2x for {@code h} (or not), then 1x. */
    public static IntToDoubleFunction hitch(int start, int h, boolean catchUp) {
        return k -> k >= start && k < start + h ? 0 : catchUp && k >= start + h && k < start + 2 * h ? 2 : 1;
    }

    public Result run() {
        SmoothedSceneClock c = new SmoothedSceneClock();
        Random random = new Random(seed);
        List<long[]> pending = new ArrayList<>();
        long raw = 0;
        long gameTime = 0;
        long tickCount = 0;
        int syncs = 0;
        double acc = 0;
        double last = Double.NaN;
        double lastRate = 1;
        double worstBack = 0;
        double maxStep = 0;
        double ahead = -Double.MAX_VALUE;
        double behind = -Double.MAX_VALUE;
        double minRate = Double.MAX_VALUE;
        double maxRate = -Double.MAX_VALUE;
        double sumRate = 0;
        double sumAbs = 0;
        double recoverPeak = 0;
        double tail = 0;
        int n = 0;
        int snaps = 0;
        int lastOff = from;
        long lastArrive = 0;
        int hold = 0;
        int longestHold = 0;
        for (int k = 0; k <= ticks; k++) {
            boolean frz = frozen.test(k);
            boolean synced = false;
            if (k > 0) {
                if (!frz) raw++;
                acc += rate.applyAsDouble(k);
                while (acc >= 1) {
                    acc -= 1;
                    tickCount++;
                    if (tickCount % 20 == 0) {
                        int late = jitter > 0 ? random.nextInt(jitter + 1) : 0;
                        late += delay.applyAsInt(syncs);
                        syncs++;
                        // In order, as over TCP: a sync held up holds up the ones behind it, which then arrive together.
                        long arrive = Math.max(k + late, lastArrive);
                        lastArrive = arrive;
                        pending.add(new long[]{arrive, gameTime});
                    }
                    if (!frz) gameTime++;
                }
                for (int i = 0; i < pending.size(); i++) {
                    if (pending.get(i)[0] <= k) {
                        raw = pending.remove(i--)[1] + (frz ? 0 : 1);
                        synced = true;
                    }
                }
            }
            double server = gameTime + (frz ? 0 : acc);
            boolean snapped = c.tick(raw, synced);
            if (snapped && k > 0) snaps++;
            if (k > 0 && !snapped) maxStep = Math.max(maxStep, Math.abs(c.rate() - lastRate));
            lastRate = c.rate();
            for (int f = 0; f < FRAMES; f++) {
                double t = c.at((float) f / FRAMES);
                if (!Double.isNaN(last)) worstBack = Math.min(worstBack, t - last);
                last = t;
            }
            double err = c.at(0) - server;
            if (k >= from) {
                ahead = Math.max(ahead, err);
                behind = Math.max(behind, -err);
                minRate = Math.min(minRate, c.rate());
                maxRate = Math.max(maxRate, c.rate());
                sumRate += c.rate();
                sumAbs += Math.abs(err);
                if (Math.abs(err) >= 1.5 + jitter) lastOff = k; // a tick of server phase is invisible to any client
                hold = c.rate() < 0.1 ? hold + 1 : 0;
                longestHold = Math.max(longestHold, hold);
                n++;
            }
            if (k >= recoverFrom) recoverPeak = Math.max(recoverPeak, Math.abs(err));
            if (k >= ticks - 200) tail = Math.max(tail, Math.abs(err));
        }
        return new Result(worstBack, maxStep, snaps, ahead, behind, minRate, maxRate, sumRate / n, sumAbs / n,
                lastOff - from, recoverPeak, tail, longestHold);
    }
}
