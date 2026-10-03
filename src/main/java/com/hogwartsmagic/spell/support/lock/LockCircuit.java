package com.hogwartsmagic.spell.support.lock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** An immutable redstone-like signal graph. Pure data; {@link LockSimulator} runs it. */
public final class LockCircuit {
    public static final int LEVER = 1;
    public static final int ANALOG = 2;
    public static final int MAX = 3;
    public static final int NOT = 4;
    public static final int REPEATER = 5;
    public static final int COMPARE = 6;
    public static final int SUBTRACT = 7;
    public static final int OBSERVER = 8;
    public static final int INPUT = 9;
    public static final int CONSTANT = 10;
    /** A button, plate or sculk sensor: pressed, then springs back. */
    public static final int PULSE = 11;
    /** A comparator, observer or other source forced on for a few seconds (Alohomora Master). */
    public static final int FORCE = 12;
    /**
     * Instant, like dust: 15 when its side input is 15 or more, otherwise its main input. A comparator reads
     * past a conductor only while the conductor's own signal is below 15.
     */
    public static final int GATE15 = 13;

    private final int[] kinds;
    private final int[] initial;
    private final int[][] mainFrom;
    private final int[][] mainLoss;
    private final int[][] sideFrom;
    private final int[][] sideLoss;
    private final int[] inputNodes;
    private final int[] inputKinds;
    private final int[][] inputDomains;
    private final int[] goals;
    private final int[] dangers;
    private final boolean keyed;
    private final boolean truncated;
    private final boolean pistonLogic;

    private LockCircuit(Builder b) {
        int n = b.kinds.size();
        kinds = toArray(b.kinds);
        initial = toArray(b.initial);
        mainFrom = new int[n][];
        mainLoss = new int[n][];
        sideFrom = new int[n][];
        sideLoss = new int[n][];
        for (int i = 0; i < n; i++) {
            mainFrom[i] = toArray(b.mainFrom.get(i));
            mainLoss[i] = toArray(b.mainLoss.get(i));
            sideFrom[i] = toArray(b.sideFrom.get(i));
            sideLoss[i] = toArray(b.sideLoss.get(i));
        }
        inputNodes = toArray(b.inputNodes);
        inputKinds = toArray(b.inputKinds);
        inputDomains = b.inputDomains.toArray(new int[0][]);
        goals = b.goals.stream().distinct().mapToInt(Integer::intValue).toArray();
        dangers = b.dangers.stream().distinct().mapToInt(Integer::intValue).toArray();
        keyed = b.keyed;
        truncated = b.truncated;
        pistonLogic = b.pistonLogic;
    }

    private static int[] toArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    public int nodeCount() {
        return kinds.length;
    }

    public int inputCount() {
        return inputNodes.length;
    }

    /** Inputs the player must work: every one except the forceable sources (what the solver's input cap counts). */
    public int nonForcedInputCount() {
        int counted = 0;
        for (int kind : inputKinds) {
            if (kind != FORCE) {
                counted++;
            }
        }
        return counted;
    }

    public int inputKind(int input) {
        return inputKinds[input];
    }

    public int[] inputDomain(int input) {
        return inputDomains[input].clone();
    }

    public int inputNode(int input) {
        return inputNodes[input];
    }

    public int kind(int node) {
        return kinds[node];
    }

    public int goalCount() {
        return goals.length;
    }

    public int goalNode(int goal) {
        return goals[goal];
    }

    public int dangerCount() {
        return dangers.length;
    }

    public int dangerNode(int danger) {
        return dangers[danger];
    }

    /** Part of this lock is an item, not a switch, and the reader could not set it. */
    public boolean keyed() {
        return keyed;
    }

    /** The trace stopped at its block cap or an unloaded chunk: some wiring is missing. */
    public boolean truncated() {
        return truncated;
    }

    /** The lock moves blocks with pistons (a piston beside a redstone, slime or honey block or an observer). */
    public boolean pistonLogic() {
        return pistonLogic;
    }

    /** Door pistons any input reaches at all: the most a solution can move. */
    public int controllableGoals() {
        int n = kinds.length;
        List<List<Integer>> readers = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            readers.add(new ArrayList<>());
        }
        for (int to = 0; to < n; to++) {
            for (int from : mainFrom[to]) {
                readers.get(from).add(to);
            }
            for (int from : sideFrom[to]) {
                readers.get(from).add(to);
            }
        }
        boolean[] seen = new boolean[n];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        for (int node : inputNodes) {
            if (!seen[node]) {
                seen[node] = true;
                queue.add(node);
            }
        }
        while (!queue.isEmpty()) {
            for (int next : readers.get(queue.poll())) {
                if (!seen[next]) {
                    seen[next] = true;
                    queue.add(next);
                }
            }
        }
        int count = 0;
        for (int goal : goals) {
            if (seen[goal]) {
                count++;
            }
        }
        return count;
    }

    int initialValue(int node) {
        return initial[node];
    }

    int[] mainFrom(int node) {
        return mainFrom[node];
    }

    int[] mainLoss(int node) {
        return mainLoss[node];
    }

    int[] sideFrom(int node) {
        return sideFrom[node];
    }

    int[] sideLoss(int node) {
        return sideLoss[node];
    }

    boolean inDomain(int input, int level) {
        for (int v : inputDomains[input]) {
            if (v == level) {
                return true;
            }
        }
        return false;
    }

    /** Collects nodes and edges, then freezes them into a circuit. */
    public static final class Builder {
        private final List<Integer> kinds = new ArrayList<>();
        private final List<Integer> initial = new ArrayList<>();
        private final List<List<Integer>> mainFrom = new ArrayList<>();
        private final List<List<Integer>> mainLoss = new ArrayList<>();
        private final List<List<Integer>> sideFrom = new ArrayList<>();
        private final List<List<Integer>> sideLoss = new ArrayList<>();
        private final List<Integer> inputNodes = new ArrayList<>();
        private final List<Integer> inputKinds = new ArrayList<>();
        private final List<int[]> inputDomains = new ArrayList<>();
        private final List<Integer> goals = new ArrayList<>();
        private final List<Integer> dangers = new ArrayList<>();
        private boolean keyed;
        private boolean truncated;
        private boolean pistonLogic;

        /** A LEVER, PULSE or FORCE input; each is off (0) or on (15). */
        public int input(int kind, boolean on) {
            if (kind != LEVER && kind != PULSE && kind != FORCE) {
                throw new IllegalArgumentException("input kind must be LEVER, PULSE or FORCE: " + kind);
            }
            int node = add(INPUT, on ? 15 : 0);
            inputNodes.add(node);
            inputKinds.add(kind);
            inputDomains.add(new int[] {0, 15});
            return node;
        }

        public int analog(int initialLevel, int[] domain) {
            if (domain == null || domain.length == 0) {
                throw new IllegalArgumentException("domain must not be empty");
            }
            int[] copy = domain.clone();
            boolean found = false;
            for (int i = 0; i < copy.length; i++) {
                checkLevel(copy[i]);
                for (int j = 0; j < i; j++) {
                    if (copy[j] == copy[i]) {
                        throw new IllegalArgumentException("duplicate domain value: " + copy[i]);
                    }
                }
                found |= copy[i] == initialLevel;
            }
            if (!found) {
                throw new IllegalArgumentException("initial level not in domain: " + initialLevel);
            }
            int node = add(INPUT, initialLevel);
            inputNodes.add(node);
            inputKinds.add(ANALOG);
            inputDomains.add(copy);
            return node;
        }

        public int constant(int level) {
            checkLevel(level);
            return add(CONSTANT, level);
        }

        public int node(int kind, int initialValue) {
            if (kind != MAX && kind != NOT && kind != REPEATER && kind != COMPARE
                    && kind != SUBTRACT && kind != OBSERVER && kind != GATE15) {
                throw new IllegalArgumentException("not a node kind: " + kind);
            }
            checkLevel(initialValue);
            return add(kind, initialValue);
        }

        public void link(int from, int to, int loss) {
            checkEdge(from, to, loss);
            int k = kinds.get(to);
            if (k == INPUT || k == CONSTANT) {
                throw new IllegalArgumentException("cannot link into an input or constant: " + to);
            }
            mainFrom.get(to).add(from);
            mainLoss.get(to).add(loss);
        }

        public void side(int from, int to, int loss) {
            checkEdge(from, to, loss);
            int k = kinds.get(to);
            if (k != REPEATER && k != COMPARE && k != SUBTRACT && k != GATE15) {
                throw new IllegalArgumentException("node does not accept a side input: " + to);
            }
            sideFrom.get(to).add(from);
            sideLoss.get(to).add(loss);
        }

        /** A door piston: the solver tries to change whether it is powered. */
        public void goal(int node) {
            checkNode(node);
            goals.add(node);
        }

        /** A dispenser, dropper or TNT: no solution may power it if it was unpowered. */
        public void danger(int node) {
            checkNode(node);
            dangers.add(node);
        }

        public void markKeyed() {
            keyed = true;
        }

        public void markTruncated() {
            truncated = true;
        }

        public void markPistonLogic() {
            pistonLogic = true;
        }

        private void checkNode(int node) {
            if (node < 0 || node >= kinds.size()) {
                throw new IllegalArgumentException("node index out of range: " + node);
            }
        }

        public LockCircuit build() {
            return new LockCircuit(this);
        }

        private int add(int kind, int value) {
            kinds.add(kind);
            initial.add(value);
            mainFrom.add(new ArrayList<>());
            mainLoss.add(new ArrayList<>());
            sideFrom.add(new ArrayList<>());
            sideLoss.add(new ArrayList<>());
            return kinds.size() - 1;
        }

        private void checkEdge(int from, int to, int loss) {
            if (from < 0 || from >= kinds.size() || to < 0 || to >= kinds.size()) {
                throw new IllegalArgumentException("node index out of range: " + from + " -> " + to);
            }
            if (loss < 0) {
                throw new IllegalArgumentException("loss must be >= 0: " + loss);
            }
        }

        private static void checkLevel(int level) {
            if (level < 0 || level > 15) {
                throw new IllegalArgumentException("level must be 0..15: " + level);
            }
        }
    }

    @Override
    public String toString() {
        return "LockCircuit" + Arrays.toString(kinds);
    }
}
