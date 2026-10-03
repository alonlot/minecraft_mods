package com.hogwartsmagic.spell.motion;

/** Rank 1..4 to a table index 0..3; anything outside is clamped. */
final class RankIndex {
    private RankIndex() {
    }

    static int of(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
