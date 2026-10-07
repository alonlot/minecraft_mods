package com.hogwartsmagic.sneakoscope;

import java.util.List;

/**
 * The Sneakoscope's rules, free of Minecraft so they are unit-tested. What it detects, how far, how strongly, and
 * how often a carried one whistles. SneakoscopeSensor finds the sources in the world and hands their distances here.
 *
 * Part B (dark players) adds one Source constant with Reach.NEAR and one probe in SneakoscopeSensor; nothing here
 * changes for it.
 */
public final class SneakoscopeRules {
    /** Which configured range a source uses: the normal range, or the long range for something in the sky. */
    public enum Reach {
        NEAR,
        SKY
    }

    /** What a Sneakoscope detects. Voldemort is a Death Eater here (VoldemortEntity extends WizardEntity). */
    public enum Source {
        DEATH_EATER(Reach.NEAR),
        DEMENTOR(Reach.NEAR),
        DARK_MARK(Reach.SKY);

        private final Reach reach;

        Source(Reach reach) {
            this.reach = reach;
        }

        public Reach reach() {
            return reach;
        }
    }

    /** One detected source and its distance in blocks from the Sneakoscope. */
    public record Hit(Source source, double distance) {
    }

    public static final int MAX_STRENGTH = 15;
    public static final int DEFAULT_RANGE = 16;
    public static final int DEFAULT_DARK_MARK_RANGE = 48;
    /** Both the carried and the placed check run once in this many ticks. */
    public static final int CHECK_INTERVAL_TICKS = 20;
    /** Carried whistle interval at strength 1. */
    public static final int SLOWEST_WHISTLE_TICKS = 40;
    /** Carried whistle interval at strength 15. */
    public static final int FASTEST_WHISTLE_TICKS = 10;

    private SneakoscopeRules() {
    }

    /** The range in blocks for a source, given the two configured ranges. */
    public static int range(Source source, int range, int darkMarkRange) {
        return source.reach() == Reach.SKY ? darkMarkRange : range;
    }

    /**
     * 15 * (1 - distance / range), rounded up: 15 at the source, 1 just inside the edge, 0 at or past it.
     * The small epsilon keeps an exact whole number from being pushed up by floating-point noise.
     */
    public static int strength(double distance, int range) {
        if (range <= 0 || distance >= range) {
            return 0;
        }
        double d = Math.max(0.0, distance);
        int s = (int) Math.ceil(MAX_STRENGTH * (range - d) / range - 1.0e-9);
        return Math.max(1, Math.min(MAX_STRENGTH, s));
    }

    /** The strongest of all hits; each source is measured against its own range. 0 when nothing is in range. */
    public static int strength(List<Hit> hits, int range, int darkMarkRange) {
        int best = 0;
        for (Hit hit : hits) {
            best = Math.max(best, strength(hit.distance(), range(hit.source(), range, darkMarkRange)));
        }
        return best;
    }

    /** Ticks between carried whistles: 40 at strength 1 down to 10 at 15, linear, rounded. 0 means no whistle. */
    public static int whistleInterval(int strength) {
        if (strength <= 0) {
            return 0;
        }
        int s = Math.min(MAX_STRENGTH, strength);
        int span = SLOWEST_WHISTLE_TICKS - FASTEST_WHISTLE_TICKS;
        int steps = MAX_STRENGTH - 1;
        return SLOWEST_WHISTLE_TICKS - ((s - 1) * span + steps / 2) / steps;
    }

    /** True once in every CHECK_INTERVAL_TICKS ticks for a key (entity id or packed block position), spread by key. */
    public static boolean dueToCheck(long gameTime, long phaseKey) {
        return Math.floorMod(gameTime, (long) CHECK_INTERVAL_TICKS) == Math.floorMod(phaseKey, (long) CHECK_INTERVAL_TICKS);
    }

    /** A placed Sneakoscope whistles once, when it goes from detecting nothing to detecting something. */
    public static boolean whistlesOnRise(int oldPower, int newPower) {
        return oldPower <= 0 && newPower > 0;
    }

    /** The Sneakoscope spins (and glows) while it detects anything. */
    public static boolean spinning(int strength) {
        return strength > 0;
    }

    /**
     * When a carried Sneakoscope next whistles, after a check moved the strength from oldStrength to newStrength.
     * Nothing near: never. Detection starts: now. Otherwise the earlier of the scheduled whistle and one interval
     * at the new strength, so a closer source speeds it up at once and a farther one lets the scheduled one play.
     */
    public static long nextWhistle(long now, long scheduled, int oldStrength, int newStrength) {
        if (newStrength <= 0) {
            return Long.MAX_VALUE;
        }
        if (oldStrength <= 0) {
            return now;
        }
        return Math.min(scheduled, now + whistleInterval(newStrength));
    }
}
