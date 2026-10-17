package com.hogwartsmagic.pensieve.scene;

/**
 * The eyelid curve of the {@code eyes_open} and {@code eyes_close} screen effects. Pure, so it is unit-tested;
 * {@code PensieveOverlay} draws the result as two black bands.
 */
public final class Eyelids {
    /** An opening stays shut until this fraction of the duration... */
    static final float SHUT_UNTIL = 0.30f;
    /** ...then half-opens for a blink (peak at this fraction)... */
    static final float BLINK_PEAK = 0.45f;
    /** ...and is shut again by this fraction, before opening for good. */
    static final float BLINK_SHUT = 0.55f;

    private Eyelids() {
    }

    /**
     * How shut the eyes are, 1 (black) to 0 (open). {@code opening}: shut, a half-open blink that closes again,
     * then open by the end (ease-out); 0 after {@code duration}. Closing: open, shut by the end (ease-in), and
     * shut ever after: the effect ends with the scene.
     */
    public static float closed(boolean opening, float age, int duration) {
        float t = duration <= 0 ? 1f : Math.max(0f, age / duration);
        if (!opening) {
            float k = Math.min(1f, t);
            return k * k;
        }
        if (t >= 1f) return 0f;
        if (t < SHUT_UNTIL) return 1f;
        if (t < BLINK_PEAK) return 1f - 0.5f * smooth((t - SHUT_UNTIL) / (BLINK_PEAK - SHUT_UNTIL));
        if (t < BLINK_SHUT) return 0.5f + 0.5f * smooth((t - BLINK_PEAK) / (BLINK_SHUT - BLINK_PEAK));
        float k = (t - BLINK_SHUT) / (1f - BLINK_SHUT);
        return (1f - k) * (1f - k);
    }

    /**
     * Rows one lid covers from its screen edge, soft edge included, on a screen {@code h} tall. At 0 it is none; at 1
     * its solid part (all but the last {@code soft} rows) reaches the middle, so two shut lids leave no seam.
     */
    public static int band(float closed, int h, int soft) {
        float k = Math.min(1f, Math.max(0f, closed));
        if (k <= 0f) return 0;
        return (int) Math.ceil((h * 0.5f + soft) * k);
    }

    private static float smooth(float k) {
        return k * k * (3f - 2f * k);
    }
}
