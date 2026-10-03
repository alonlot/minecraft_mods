package com.hogwartsmagic.spell.support.lock;

/**
 * Runs a {@link LockCircuit}: instant dust, one-tick delayed torches, repeaters, comparators and observers.
 * Inputs set together before one {@link #settle()} are seen as one simultaneous change; a search that
 * depends on order should settle between input changes.
 */
public final class LockSimulator {
    public static final int MAX_TICKS = 256;
    public static final int PULSE_TICKS = 2;

    private final LockCircuit circuit;
    private final int nodes;
    private final int inputs;
    private final int[] value;
    private final int[] inputLevel;
    private final int[] last;
    private final int[] pulseLeft;
    private final int[] next;
    private long work;
    private final boolean[] dangerOn;
    private boolean dangerRose;
    private boolean stopped;

    public LockSimulator(LockCircuit circuit) {
        this.circuit = circuit;
        this.dangerOn = new boolean[circuit.dangerCount()];
        this.nodes = circuit.nodeCount();
        this.inputs = circuit.inputCount();
        this.value = new int[nodes];
        this.inputLevel = new int[inputs];
        this.last = new int[nodes];
        this.pulseLeft = new int[nodes];
        this.next = new int[nodes];
        load();
    }

    public boolean reset() {
        load();
        return settle();
    }

    /**
     * Like {@link #reset()}, but the settle respects workLimit and thread interrupts as in
     * {@link #settle(int, long)}; {@link #stoppedForBudget()} tells a stop from an unstable lock.
     */
    public boolean resetBounded(long workLimit) {
        load();
        return settle(MAX_TICKS, workLimit);
    }

    public boolean settle() {
        return settle(MAX_TICKS);
    }

    /** Runs ticks until stable. False means not stable within maxTicks (oscillating or too slow). */
    public boolean settle(int maxTicks) {
        return settleImpl(maxTicks, Long.MAX_VALUE, false);
    }

    /**
     * Like {@link #settle(int)}, but stops partway and returns false when {@link #work()} passes workLimit or
     * the thread is interrupted; {@link #stoppedForBudget()} then reads true.
     */
    public boolean settle(int maxTicks, long workLimit) {
        return settleImpl(maxTicks, workLimit, true);
    }

    private boolean settleImpl(int maxTicks, long workLimit, boolean bounded) {
        if (maxTicks < 1) {
            throw new IllegalArgumentException("maxTicks must be >= 1: " + maxTicks);
        }
        dangerRose = false;
        stopped = false;
        for (int d = 0; d < dangerOn.length; d++) {
            dangerOn[d] = value[circuit.dangerNode(d)] > 0;
        }
        recomputeInstant();
        sampleDangers();
        for (int t = 0; t < maxTicks; t++) {
            if (bounded && (work > workLimit || Thread.currentThread().isInterrupted())) {
                stopped = true;
                return false;
            }
            boolean changed = tick();
            sampleDangers();
            if (!changed) {
                return true;
            }
        }
        return false;
    }

    /** True if any danger node went from off to on at any moment of the last settle, even if it ended off. */
    public boolean dangerRoseInLastSettle() {
        return dangerRose;
    }

    /** True if the last bounded settle stopped for its work limit or a thread interrupt. */
    public boolean stoppedForBudget() {
        return stopped;
    }

    private void sampleDangers() {
        for (int d = 0; d < dangerOn.length; d++) {
            boolean on = value[circuit.dangerNode(d)] > 0;
            if (on && !dangerOn[d]) {
                dangerRose = true;
            }
            dangerOn[d] = on;
        }
    }

    /** A lever, button, plate, sensor or forced source: on (15) or off (0). */
    public void setInput(int input, boolean on) {
        if (circuit.inputKind(input) == LockCircuit.ANALOG) {
            throw new IllegalArgumentException("input is analog, use setLevel: " + input);
        }
        applyInput(input, on ? 15 : 0);
    }

    /** Any input, to any level in its domain (a lever's domain is 0 and 15). */
    public void setLevel(int input, int level) {
        if (!circuit.inDomain(input, level)) {
            throw new IllegalArgumentException("level not in domain: " + level);
        }
        applyInput(input, level);
    }

    public boolean goalPowered(int goal) {
        return value[circuit.goalNode(goal)] > 0;
    }

    public boolean dangerPowered(int danger) {
        return value[circuit.dangerNode(danger)] > 0;
    }

    public int value(int node) {
        return value[node];
    }

    public int[] snapshot() {
        int[] out = new int[stateSize()];
        System.arraycopy(value, 0, out, 0, nodes);
        System.arraycopy(inputLevel, 0, out, nodes, inputs);
        System.arraycopy(last, 0, out, nodes + inputs, nodes);
        System.arraycopy(pulseLeft, 0, out, 2 * nodes + inputs, nodes);
        return out;
    }

    public void restore(int[] snapshot) {
        if (snapshot == null || snapshot.length != stateSize()) {
            throw new IllegalArgumentException("snapshot does not fit this circuit");
        }
        System.arraycopy(snapshot, 0, value, 0, nodes);
        System.arraycopy(snapshot, nodes, inputLevel, 0, inputs);
        System.arraycopy(snapshot, nodes + inputs, last, 0, nodes);
        System.arraycopy(snapshot, 2 * nodes + inputs, pulseLeft, 0, nodes);
    }

    public long work() {
        return work;
    }

    private int stateSize() {
        return 3 * nodes + inputs;
    }

    /** Initial values, dust settled, observers primed so no pulse fires at start. Does not tick. */
    private void load() {
        loadInitial();
        recomputeInstant();
        for (int n = 0; n < nodes; n++) {
            if (circuit.kind(n) == LockCircuit.OBSERVER) {
                last[n] = main(n);
                pulseLeft[n] = 0;
            }
        }
    }

    private void loadInitial() {
        for (int n = 0; n < nodes; n++) {
            value[n] = circuit.initialValue(n);
            last[n] = 0;
            pulseLeft[n] = 0;
        }
        for (int i = 0; i < inputs; i++) {
            inputLevel[i] = value[circuit.inputNode(i)];
        }
    }

    private void applyInput(int input, int level) {
        inputLevel[input] = level;
        value[circuit.inputNode(input)] = level;
    }

    private int main(int n) {
        return strongest(circuit.mainFrom(n), circuit.mainLoss(n));
    }

    private int side(int n) {
        return strongest(circuit.sideFrom(n), circuit.sideLoss(n));
    }

    private int strongest(int[] from, int[] loss) {
        int best = 0;
        for (int i = 0; i < from.length; i++) {
            best = Math.max(best, value[from[i]] - loss[i]);
        }
        return best;
    }

    /** Dust takes the least fixpoint, so a source-free dust loop holds no stale power. */
    private void recomputeInstant() {
        for (int n = 0; n < nodes; n++) {
            if (instant(circuit.kind(n))) {
                value[n] = 0;
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int n = 0; n < nodes; n++) {
                int kind = circuit.kind(n);
                if (instant(kind)) {
                    work++;
                    int v = kind == LockCircuit.GATE15 && side(n) >= 15 ? 15 : main(n);
                    if (v != value[n]) {
                        value[n] = v;
                        changed = true;
                    }
                }
            }
        }
    }

    /** Dust and the 15-gate settle within the tick; everything else is delayed. */
    private static boolean instant(int kind) {
        return kind == LockCircuit.MAX || kind == LockCircuit.GATE15;
    }

    private boolean tick() {
        boolean changed = false;
        for (int n = 0; n < nodes; n++) {
            int kind = circuit.kind(n);
            if (instant(kind) || kind == LockCircuit.INPUT || kind == LockCircuit.CONSTANT) {
                continue;
            }
            work++;
            int out;
            switch (kind) {
                case LockCircuit.NOT -> out = main(n) == 0 ? 15 : 0;
                case LockCircuit.REPEATER -> out = side(n) > 0 ? value[n] : (main(n) > 0 ? 15 : 0);
                case LockCircuit.COMPARE -> {
                    int rear = main(n);
                    out = rear >= side(n) ? rear : 0;
                }
                case LockCircuit.SUBTRACT -> out = Math.max(0, main(n) - side(n));
                default -> {
                    int s = main(n);
                    if (s != last[n]) {
                        last[n] = s;
                        pulseLeft[n] = PULSE_TICKS;
                        changed = true;
                    }
                    out = 0;
                    if (pulseLeft[n] > 0) {
                        out = 15;
                        pulseLeft[n]--;
                        changed = true;
                    }
                }
            }
            next[n] = out;
        }
        for (int n = 0; n < nodes; n++) {
            int kind = circuit.kind(n);
            if (instant(kind) || kind == LockCircuit.INPUT || kind == LockCircuit.CONSTANT) {
                continue;
            }
            if (value[n] != next[n]) {
                value[n] = next[n];
                changed = true;
            }
        }
        recomputeInstant();
        return changed;
    }
}
