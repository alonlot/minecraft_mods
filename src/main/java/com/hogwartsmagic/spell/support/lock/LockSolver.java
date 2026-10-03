package com.hogwartsmagic.spell.support.lock;

import java.util.Arrays;

/**
 * Finds the setting of a lock's levers and item keys, and the order of its presses, that moves the most
 * door pistons: fewest actions first, real switches before forced sources, never a setting that powers a
 * trap. Pure; runs on the solver thread.
 */
public final class LockSolver {
    public enum Status { SOLVED, NO_DOOR, TOO_MANY_INPUTS, UNSTABLE, NO_SOLUTION, OUT_OF_TIME }

    /**
     * set[i] goes to level setTo[i]; presses are pressed (or forced) in order. Indexes are circuit inputs.
     * A FORCE input inside set (with setTo = 15) is not a level to set: every FORCE index in set is a source
     * to be held on together at one moment, the answer of the held-together subset pass (see
     * {@link LockSolver#solve}); playback forces them all in one step, before any presses.
     * The arrays are not defensively copied: callers must not modify them.
     */
    public record Solution(Status status, int[] set, int[] setTo, int[] presses, int moved, int movable, long work) {
        static Solution none(Status status, int movable, long work) {
            return new Solution(status, new int[0], new int[0], new int[0], 0, movable, work);
        }
    }

    private LockSolver() {
    }

    public static Solution solve(LockCircuit c, int maxInputs, int maxPresses, long budget) {
        if (c.goalCount() == 0) {
            return Solution.none(Status.NO_DOOR, 0, 0);
        }
        int movable = c.controllableGoals();
        if (c.nonForcedInputCount() > maxInputs) {
            return Solution.none(Status.TOO_MANY_INPUTS, movable, 0);
        }
        if (c.inputCount() == 0 || movable == 0) {
            return Solution.none(Status.NO_SOLUTION, movable, 0);
        }
        LockSimulator sim = new LockSimulator(c);
        if (!sim.resetBounded(budget)) {
            // out of budget or interrupted is not the same as a lock that never stops ticking
            return Solution.none(sim.stoppedForBudget() ? Status.OUT_OF_TIME : Status.UNSTABLE, movable, sim.work());
        }
        Search search = new Search(c, sim, budget, movable);
        int[] settable = concat(ofKind(c, LockCircuit.LEVER), ofKind(c, LockCircuit.ANALOG));
        int[] pulses = ofKind(c, LockCircuit.PULSE);
        int[] forced = ofKind(c, LockCircuit.FORCE);
        // With forcing available, pass 1 may spend at most 75% of the budget so pass 2 always gets a share;
        // with two or more forceable sources pass 2 stops at 7/8 so the held-together pass 3 gets the rest.
        long pass1 = forced.length > 0 ? budget / 4 * 3 : budget;
        long pass2 = forced.length >= 2 ? budget / 8 * 7 : budget;
        search.run(settable, pulses, maxPresses, pass1, -1);
        if (search.bestMoved < movable && forced.length > 0 && !Thread.currentThread().isInterrupted()) {
            search.run(settable, concat(pulses, forced), maxPresses, pass2, pulses.length);
        }
        if (search.bestMoved < movable && forced.length >= 2 && !Thread.currentThread().isInterrupted()) {
            search.runHeld(forced, budget);
        }
        return search.result();
    }

    private static final class Search {
        private final LockCircuit c;
        private final LockSimulator sim;
        private final long budget;
        private final int movable;
        private final int[] base;
        private final boolean[] goalBase;
        private final boolean[] dangerBase;
        int bestMoved;
        private int[] bestSet = new int[0];
        private int[] bestTo = new int[0];
        private int[] bestPresses = new int[0];
        boolean outOfTime;
        private boolean halted;
        private long limit;

        Search(LockCircuit c, LockSimulator sim, long budget, int movable) {
            this.c = c;
            this.sim = sim;
            this.budget = budget;
            this.movable = movable;
            base = sim.snapshot();
            goalBase = new boolean[c.goalCount()];
            for (int g = 0; g < goalBase.length; g++) {
                goalBase[g] = sim.goalPowered(g);
            }
            dangerBase = new boolean[c.dangerCount()];
            for (int d = 0; d < dangerBase.length; d++) {
                dangerBase[d] = sim.dangerPowered(d);
            }
        }

        /**
         * forceFrom: -1 in pass 1; in pass 2 the first index of the FORCE inputs in pressable, and only
         * sequences that press at least one of them are tried (the rest were covered by pass 1).
         */
        void run(int[] settable, int[] pressable, int maxPresses, long limit, int forceFrom) {
            this.limit = limit;
            this.halted = false;
            int pressCap = pressable.length == 0 ? 0 : maxPresses;
            for (int cost = 1; cost <= settable.length + pressCap; cost++) {
                for (int k = Math.min(cost, settable.length); k >= Math.max(0, cost - pressCap); k--) {
                    int len = cost - k;
                    int[] pick = firstCombination(k);
                    do {
                        int[] set = select(settable, pick);
                        int[][] alts = new int[k][];
                        boolean possible = true;
                        for (int i = 0; i < k; i++) {
                            alts[i] = alternatives(set[i]);
                            possible &= alts[i].length > 0;
                        }
                        if (!possible) {
                            continue;
                        }
                        int[] choice = new int[k];
                        do {
                            int[] to = new int[k];
                            for (int i = 0; i < k; i++) {
                                to[i] = alts[i][choice[i]];
                            }
                            int[] seq = new int[len];
                            do {
                                if (sim.work() > limit || Thread.currentThread().isInterrupted()) {
                                    outOfTime = true;
                                    return;
                                }
                                if (forceFrom >= 0 && !pressesAny(seq, forceFrom)) {
                                    continue;
                                }
                                int[] presses = select(pressable, seq);
                                int moved = attempt(set, to, presses);
                                if (halted) {
                                    outOfTime = true;
                                    return;
                                }
                                if (moved > bestMoved) {
                                    bestMoved = moved;
                                    bestSet = set;
                                    bestTo = to;
                                    bestPresses = presses;
                                }
                            } while (nextSequence(seq, pressable.length));
                        } while (nextMixed(choice, alts));
                    } while (nextCombination(pick, settable.length));
                }
                if (bestMoved == movable) {
                    return;
                }
            }
        }

        /**
         * Pass 3: subsets of the forceable sources held on together, smallest first (size 2 upward; single
         * sources were covered by pass 2), at most 2^8 - 1 subsets. Each is one attempt from the baseline: all
         * on and settled, then all off and settled. Only a strictly better answer replaces an earlier one.
         */
        void runHeld(int[] forced, long limit) {
            this.limit = limit;
            this.halted = false;
            int n = Math.min(forced.length, 8);
            for (int size = 2; size <= n; size++) {
                for (int mask = 1; mask < (1 << n); mask++) {
                    if (Integer.bitCount(mask) != size) {
                        continue;
                    }
                    if (sim.work() > limit || Thread.currentThread().isInterrupted()) {
                        outOfTime = true;
                        return;
                    }
                    int[] subset = new int[size];
                    int[] to = new int[size];
                    int at = 0;
                    for (int i = 0; i < n; i++) {
                        if ((mask & (1 << i)) != 0) {
                            subset[at] = forced[i];
                            to[at++] = 15;
                        }
                    }
                    int moved = attemptHeld(subset);
                    if (halted) {
                        outOfTime = true;
                        return;
                    }
                    if (moved > bestMoved) {
                        bestMoved = moved;
                        bestSet = subset;
                        bestTo = to;
                        bestPresses = new int[0];
                        if (bestMoved == movable) {
                            return; // nothing later can do better: a later subset only wins if strictly better
                        }
                    }
                }
                if (bestMoved == movable) {
                    return;
                }
            }
        }

        /** Door pistons moved with all of these on together, or after letting go; -1 as in {@link #attempt}. */
        private int attemptHeld(int[] subset) {
            sim.restore(base);
            for (int input : subset) {
                sim.setInput(input, true);
            }
            int best = checkForcedOn();
            if (best < 0) {
                return -1;
            }
            for (int input : subset) {
                sim.setInput(input, false);
            }
            int released = check();
            if (released < 0) {
                return -1;
            }
            return Math.max(best, released);
        }

        Solution result() {
            if (bestMoved > 0) {
                return new Solution(Status.SOLVED, bestSet, bestTo, bestPresses, bestMoved, movable, sim.work());
            }
            return Solution.none(outOfTime ? Status.OUT_OF_TIME : Status.NO_SOLUTION, movable, sim.work());
        }

        /** Levels this input can move to from where it stands at baseline. */
        private int[] alternatives(int input) {
            int now = base[c.inputNode(input)];
            return Arrays.stream(c.inputDomain(input)).filter(v -> v != now).toArray();
        }

        /** Door pistons moved at the best moment of this attempt; -1 if it never settles or springs a trap. */
        private int attempt(int[] set, int[] to, int[] presses) {
            sim.restore(base);
            int best = 0;
            if (set.length > 0) {
                for (int i = 0; i < set.length; i++) {
                    sim.setLevel(set[i], to[i]);
                }
                best = check();
                if (best < 0) {
                    return -1;
                }
            }
            for (int input : presses) {
                for (int phase = 0; phase < 2; phase++) {
                    sim.setInput(input, phase == 0);
                    int moved = phase == 0 && c.inputKind(input) == LockCircuit.FORCE ? checkForcedOn() : check();
                    if (moved < 0) {
                        return -1;
                    }
                    best = Math.max(best, moved);
                }
            }
            return best;
        }

        private static boolean pressesAny(int[] seq, int forceFrom) {
            for (int s : seq) {
                if (s >= forceFrom) {
                    return true;
                }
            }
            return false;
        }

        private int check() {
            if (!sim.settle(LockSimulator.MAX_TICKS, limit)) {
                if (sim.stoppedForBudget()) {
                    halted = true; // out of budget or interrupted: not the same as an oscillating lock
                }
                return -1;
            }
            return score();
        }

        /**
         * The on-phase of a forced source. The world holds it for LockRules.FORCE_TICKS game ticks, which is
         * FORCE_TICKS / 2 simulator (redstone) ticks. A circuit still ticking when the hold ends is not unstable:
         * the world lets go then, and the release is checked next. Traps are checked as in check(). A door
         * piston counts as moved only if it kept its value over the last simulated tick of the hold.
         */
        private int checkForcedOn() {
            int hold = LockRules.FORCE_TICKS / 2;
            if (sim.settle(hold - 1, limit)) {
                return score();
            }
            if (sim.stoppedForBudget()) {
                halted = true;
                return -1;
            }
            if (trapped()) {
                return -1;
            }
            boolean[] before = new boolean[goalBase.length];
            for (int g = 0; g < before.length; g++) {
                before[g] = sim.goalPowered(g);
            }
            if (sim.settle(1, limit)) {
                return score();
            }
            if (sim.stoppedForBudget()) {
                halted = true;
                return -1;
            }
            if (trapped()) {
                return -1;
            }
            int moved = 0;
            for (int g = 0; g < goalBase.length; g++) {
                boolean now = sim.goalPowered(g);
                if (now == before[g] && now != goalBase[g]) {
                    moved++;
                }
            }
            return moved;
        }

        /** After a settle: -1 if a trap fired, otherwise the door pistons moved from baseline. */
        private int score() {
            if (trapped()) {
                return -1;
            }
            int moved = 0;
            for (int g = 0; g < goalBase.length; g++) {
                if (sim.goalPowered(g) != goalBase[g]) {
                    moved++;
                }
            }
            return moved;
        }

        /** A trap rose during the last settle, or is on now when it was off at baseline. */
        private boolean trapped() {
            if (sim.dangerRoseInLastSettle()) {
                return true;
            }
            for (int d = 0; d < dangerBase.length; d++) {
                if (!dangerBase[d] && sim.dangerPowered(d)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static int[] ofKind(LockCircuit c, int kind) {
        int[] out = new int[c.inputCount()];
        int count = 0;
        for (int i = 0; i < c.inputCount(); i++) {
            if (c.inputKind(i) == kind) {
                out[count++] = i;
            }
        }
        return Arrays.copyOf(out, count);
    }

    private static int[] concat(int[] a, int[] b) {
        int[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static int[] select(int[] from, int[] indexes) {
        int[] out = new int[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
            out[i] = from[indexes[i]];
        }
        return out;
    }

    private static int[] firstCombination(int k) {
        int[] pick = new int[k];
        for (int i = 0; i < k; i++) {
            pick[i] = i;
        }
        return pick;
    }

    /** Next k-subset of 0..n-1 in lexicographic order; false after the last. */
    private static boolean nextCombination(int[] pick, int n) {
        int k = pick.length;
        for (int i = k - 1; i >= 0; i--) {
            if (pick[i] < n - k + i) {
                pick[i]++;
                for (int j = i + 1; j < k; j++) {
                    pick[j] = pick[j - 1] + 1;
                }
                return true;
            }
        }
        return false;
    }

    /** Next sequence over 0..base-1 (an odometer); false after the last. */
    private static boolean nextSequence(int[] seq, int base) {
        for (int i = seq.length - 1; i >= 0; i--) {
            if (++seq[i] < base) {
                return true;
            }
            seq[i] = 0;
        }
        return false;
    }

    /** Odometer where position i runs over alts[i]; false after the last. */
    private static boolean nextMixed(int[] choice, int[][] alts) {
        for (int i = choice.length - 1; i >= 0; i--) {
            if (++choice[i] < alts[i].length) {
                return true;
            }
            choice[i] = 0;
        }
        return false;
    }
}
