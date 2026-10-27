package com.hogwartsmagic.pensieve.capture;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Picks which actors make it into a recording when there are more candidates than the cap. */
public final class EntitySelect {
    public record Candidate(int actor, boolean player, boolean owner, double minDistance) {}

    private EntitySelect() {}

    /** Owner first, then players, then the rest; each group ordered by nearest distance. */
    public static List<Integer> pick(List<Candidate> candidates, int max) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt((Candidate c) -> c.owner() ? 0 : c.player() ? 1 : 2)
                .thenComparingDouble(Candidate::minDistance));
        List<Integer> out = new ArrayList<>();
        for (Candidate c : sorted) {
            if (out.size() >= max) {
                break;
            }
            out.add(c.actor());
        }
        return out;
    }
}
