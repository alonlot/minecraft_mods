package com.hogwartsmagic.spell.summon;

/**
 * Which animal answers a caster. Deterministic: the same UUID and house always give the same form, and
 * the caller stores the first answer so a later house change can't alter it. Each house draws from four
 * forms in keeping with it; an unsorted caster draws from all eight.
 */
public final class PatronusForms {
    /** Indexed by HogwartsHouse.ordinal(): GRYFFINDOR, RAVENCLAW, HUFFLEPUFF, SLYTHERIN. */
    private static final PatronusForm[][] HOUSE_POOLS = {
            {PatronusForm.STAG, PatronusForm.WOLF, PatronusForm.PHOENIX, PatronusForm.HARE},
            {PatronusForm.OWL, PatronusForm.HARE, PatronusForm.FOX, PatronusForm.CAT},
            {PatronusForm.DOE, PatronusForm.HARE, PatronusForm.CAT, PatronusForm.WOLF},
            {PatronusForm.FOX, PatronusForm.CAT, PatronusForm.WOLF, PatronusForm.OWL},
    };
    private static final PatronusForm[] ALL = {
            PatronusForm.STAG, PatronusForm.DOE, PatronusForm.HARE, PatronusForm.WOLF,
            PatronusForm.CAT, PatronusForm.OWL, PatronusForm.FOX, PatronusForm.PHOENIX,
    };

    private PatronusForms() {
    }

    public static PatronusForm[] pool(int houseIndex) {
        return poolFor(houseIndex).clone();
    }

    public static PatronusForm pick(long mostBits, long leastBits, int houseIndex) {
        PatronusForm[] pool = poolFor(houseIndex);
        long hash = mix(mostBits ^ Long.rotateLeft(leastBits, 32));
        return pool[(int) Math.floorMod(hash, (long) pool.length)];
    }

    private static PatronusForm[] poolFor(int houseIndex) {
        return houseIndex >= 0 && houseIndex < HOUSE_POOLS.length ? HOUSE_POOLS[houseIndex] : ALL;
    }

    /** The 64-bit MurmurHash3 finaliser: spreads nearby UUIDs across the whole range. */
    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
