package com.hogwartsmagic.brewing;

import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * How well a brew was made: fine rare ingredients and a steady stirring rhythm raise it. Pure rules, no world access.
 * The cauldron counts; this class turns the counts into a grade.
 */
public final class BrewQuality {
    public static final int STANDARD = 0;
    public static final int FINE = 1;
    public static final int EXCEPTIONAL = 2;

    /** A stir is precise when it comes at least this long after the last action... */
    public static final int STIR_MIN_GAP_TICKS = 10;
    /** ...and, right after another stir, no more than this long after it. */
    public static final int STIR_MAX_GAP_TICKS = 30;
    public static final double FINE_SCORE = 0.5;
    public static final double EXCEPTIONAL_SCORE = 0.9;

    private static final Set<String> GRADABLE = Set.of(BrewRules.DITTANY, BrewRules.MANDRAKE_ROOT, BrewRules.ASPHODEL,
            BrewRules.WORMWOOD, BrewRules.VALERIAN_SPRIGS, BrewRules.ASHWINDER_EGG);

    private BrewQuality() {
    }

    /** True for the rare ingredients whose fine variants lift a brew. */
    public static boolean gradable(@Nullable String item) {
        return item != null && GRADABLE.contains(item);
    }

    public static boolean preciseStir(boolean previousWasStir, long gapTicks) {
        return gapTicks >= STIR_MIN_GAP_TICKS && (!previousWasStir || gapTicks <= STIR_MAX_GAP_TICKS);
    }

    /** The mean of the ingredient part and the stir part, whichever are present; 0 when neither is. */
    public static double score(int gradableAdds, int fineAdds, int stirs, int preciseStirs) {
        double sum = 0.0;
        int parts = 0;
        if (gradableAdds > 0) {
            sum += clamp01((double) fineAdds / gradableAdds);
            parts++;
        }
        if (stirs > 0) {
            sum += clamp01((double) preciseStirs / stirs);
            parts++;
        }
        return parts == 0 ? 0.0 : sum / parts;
    }

    public static int grade(double score) {
        if (score >= EXCEPTIONAL_SCORE) {
            return EXCEPTIONAL;
        }
        if (score >= FINE_SCORE) {
            return FINE;
        }
        return STANDARD; // below the bar, and NaN
    }

    public static int clampGrade(int grade) {
        return Math.max(STANDARD, Math.min(EXCEPTIONAL, grade));
    }

    public static String langKey(int grade) {
        int g = clampGrade(grade);
        return "quality.hogwartsmagic." + (g == EXCEPTIONAL ? "exceptional" : g == FINE ? "fine" : "standard");
    }

    private static double clamp01(double value) {
        return Double.isNaN(value) ? 0.0 : Math.max(0.0, Math.min(1.0, value));
    }
}
