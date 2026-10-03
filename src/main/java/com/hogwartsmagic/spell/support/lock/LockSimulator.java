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

    public LockSimulator(LockCircuit circuit) {
        this.circuit = circuit;
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

    public boolean settle() {
        return settle(MAX_TICKS);
    }

    /** Runs ticks until stable. False means not stable within maxTicks (oscillating or too slow). */
    public boolean settle(int maxTicks) {
        if (maxTicks < 1) {
            throw new IllegalArgumentException("maxTicks must be >= 1: " + maxTicks);
        }
        recomputeInstant();
        for (int t = 0; t < maxTicks; t++) {
            if (!tick()) {
                return true;
            }
        }
        return false;
    }

    public void setInput(int input, boolean on) {
        if (circuit.inputKind(input) != LockCircuit.LEVER) {
            throw new IllegalArgumentException("input is not a lever: " + input);
        }
        applyInput(input, on ? 15 : 0);
    }

    public void setLevel(int input, int level) {
        if (circuit.inputKind(input) != LockCircuit.ANALOG) {
            throw new IllegalArgumentException("input is not analog: " + input);
        }
        if (!circuit.inDomain(input, level)) {
            throw new IllegalArgumentException("level not in domain: " + level);
        }
        applyInput(input, level);
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
            if (circuit.kind(n) == LockCircuit.MAX) {
                value[n] = 0;
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int n = 0; n < nodes; n++) {
                if (circuit.kind(n) == LockCircuit.MAX) {
                    work++;
                    int v = main(n);
                    if (v != value[n]) {
                        value[n] = v;
                        changed = true;
                    }
                }
            }
        }
    }

    private boolean tick() {
        boolean changed = false;
        for (int n = 0; n < nodes; n++) {
            int kind = circuit.kind(n);
            if (kind == LockCircuit.MAX || kind == LockCircuit.INPUT || kind == LockCircuit.CONSTANT) {
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
            if (kind == LockCircuit.MAX || kind == LockCircuit.INPUT || kind == LockCircuit.CONSTANT) {
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
