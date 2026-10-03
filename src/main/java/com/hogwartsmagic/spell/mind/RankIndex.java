package com.hogwartsmagic.spell.mind;

/** Maps a mastery rank (1..4) to a table index (0..3). Anything outside is clamped; NPC casters pass 1. */
final class RankIndex {
    private RankIndex() {
    }

    static int of(int rank) {
        return Math.max(1, Math.min(4, rank)) - 1;
    }
}
