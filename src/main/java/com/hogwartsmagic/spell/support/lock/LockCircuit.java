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

    private final int[] kinds;
    private final int[] initial;
    private final int[][] mainFrom;
    private final int[][] mainLoss;
    private final int[][] sideFrom;
    private final int[][] sideLoss;
    private final int[] inputNodes;
    private final int[] inputKinds;
    private final int[][] inputDomains;

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

        public int input(int kind, boolean on) {
            if (kind != LEVER) {
                throw new IllegalArgumentException("input kind must be LEVER: " + kind);
            }
            int node = add(INPUT, on ? 15 : 0);
            inputNodes.add(node);
            inputKinds.add(LEVER);
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
                    && kind != SUBTRACT && kind != OBSERVER) {
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
            if (k != REPEATER && k != COMPARE && k != SUBTRACT) {
                throw new IllegalArgumentException("node does not accept a side input: " + to);
            }
            sideFrom.get(to).add(from);
            sideLoss.get(to).add(loss);
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
