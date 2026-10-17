package com.hogwartsmagic.sorting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Pure quiz state machine: three questions, then an optional tie pick. No Minecraft types. */
public final class SortingSession {
    public enum Outcome {
        NEXT_QUESTION, TIE, SORTED, REJECTED
    }

    private static final int QUESTIONS = 3;
    private static final int TIE_STEP = QUESTIONS;
    private static final int DONE_STEP = QUESTIONS + 1;

    private final List<SortingQuestion> questions = new ArrayList<>();
    private final List<List<HogwartsHouse>> orders = new ArrayList<>();
    private final List<HogwartsHouse> picks = new ArrayList<>();
    private List<HogwartsHouse> tied = List.of();
    private HogwartsHouse result;
    private int step;

    public SortingSession(List<SortingQuestion> bank, Random rng) {
        if (bank.size() < QUESTIONS) {
            throw new IllegalArgumentException("Need at least " + QUESTIONS + " questions");
        }
        List<SortingQuestion> pool = new ArrayList<>(bank);
        Collections.shuffle(pool, rng);
        for (int i = 0; i < QUESTIONS; i++) {
            questions.add(pool.get(i));
            List<HogwartsHouse> order = new ArrayList<>(List.of(HogwartsHouse.values()));
            Collections.shuffle(order, rng);
            orders.add(List.copyOf(order));
        }
    }

    public int step() {
        return step;
    }

    /** The current question; only valid while step() is 0..2. */
    public SortingQuestion question() {
        return questions.get(step);
    }

    /** Houses in display order for the current question; only valid while step() is 0..2. */
    public List<HogwartsHouse> order() {
        return orders.get(step);
    }

    public List<HogwartsHouse> tied() {
        return tied;
    }

    public HogwartsHouse result() {
        return result;
    }

    public Outcome answer(int atStep, int choice) {
        if (atStep != step || step >= DONE_STEP) {
            return Outcome.REJECTED;
        }
        if (step == TIE_STEP) {
            if (choice < 0 || choice >= tied.size()) {
                return Outcome.REJECTED;
            }
            result = tied.get(choice);
            step = DONE_STEP;
            return Outcome.SORTED;
        }
        List<HogwartsHouse> order = orders.get(step);
        if (choice < 0 || choice >= order.size()) {
            return Outcome.REJECTED;
        }
        picks.add(order.get(choice));
        step++;
        if (step < QUESTIONS) {
            return Outcome.NEXT_QUESTION;
        }
        return resolve();
    }

    private Outcome resolve() {
        Map<HogwartsHouse, Integer> counts = new EnumMap<>(HogwartsHouse.class);
        for (HogwartsHouse h : picks) {
            counts.merge(h, 1, Integer::sum);
        }
        int best = Collections.max(counts.values());
        List<HogwartsHouse> leaders = new ArrayList<>();
        for (HogwartsHouse h : HogwartsHouse.values()) {
            if (counts.getOrDefault(h, 0) == best) {
                leaders.add(h);
            }
        }
        if (leaders.size() == 1) {
            result = leaders.get(0);
            step = DONE_STEP;
            return Outcome.SORTED;
        }
        tied = List.copyOf(leaders);
        step = TIE_STEP;
        return Outcome.TIE;
    }
}
