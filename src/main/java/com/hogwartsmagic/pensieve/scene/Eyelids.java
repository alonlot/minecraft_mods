package com.hogwartsmagic.pensieve.scene;

/**
 * The eyelid curves of the {@code eyes_open}, {@code eyes_close} and {@code eyes_peek} screen effects (and of the
 * {@code wake} end action, an opening drawn over the real world). Pure, so it is unit-tested;
 * {@code PensieveOverlay} draws the result as two black bands.
 */
public final class Eyelids {
    /** An opening stays shut until this fraction of the duration... */
    static final float SHUT_UNTIL = 0.30f;
    /** ...then half-opens for a blink (peak at this fraction)... */
    static final float BLINK_PEAK = 0.45f;
    /** ...and is shut again by this fraction, before opening for good. */
    static final float BLINK_SHUT = 0.55f;
    /** A peek has opened to its slit by this fraction of the duration... */
    static final float PEEK_OPEN_UNTIL = 0.30f;
    /** ...holds it, and starts to shut again from this fraction. */
    static final float PEEK_CLOSE_FROM = 0.70f;
    /** How far an {@code eyes_peek} opens with no {@code intensity}: a slit, about a third. */
    public static final float DEFAULT_PEEK_OPEN = 0.35f;
    /** The narrowest (and widest, 1) opening an {@code eyes_peek} {@code intensity} may ask for. */
    public static final float MIN_PEEK_OPEN = 0.05f;
    /** An {@code eyes_peek} with no {@code duration} takes this long (3 s). */
    public static final int DEFAULT_PEEK_TICKS = 60;

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
     * {@code eyes_peek}: how shut the eyes are, 1 (black) to {@code 1 - open}. Shut at the start, the lids part to a
     * slit {@code open} wide over the first {@link #PEEK_OPEN_UNTIL} of the duration (ease-out), hold there until
     * {@link #PEEK_CLOSE_FROM}, then shut again by the end (ease-in) and stay shut ever after, like a closing.
     */
    public static float peek(float age, int duration, float open) {
        float slit = 1f - Math.min(1f, Math.max(0f, open));
        float t = duration <= 0 ? 1f : Math.max(0f, age / duration);
        if (t >= 1f) return 1f;
        if (t < PEEK_OPEN_UNTIL) return 1f - (1f - slit) * smooth(t / PEEK_OPEN_UNTIL);
        if (t < PEEK_CLOSE_FROM) return slit;
        return slit + (1f - slit) * smooth((t - PEEK_CLOSE_FROM) / (1f - PEEK_CLOSE_FROM));
    }

    /**
     * An {@code eyes_peek} event's {@code intensity} read as how far the lids open: absent (NaN) is
     * {@link #DEFAULT_PEEK_OPEN}, and anything else is clamped to {@link #MIN_PEEK_OPEN}..1.
     */
    public static float peekOpen(float intensity) {
        if (Float.isNaN(intensity)) return DEFAULT_PEEK_OPEN;
        return Math.min(1f, Math.max(MIN_PEEK_OPEN, intensity));
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

    /**
     * True when a scene opens its eyes from the very start (an {@code eyes_open} effect event at {@code t <= 0}): the
     * client then keeps the lids shut while the set builds, so the memory begins in darkness and the open-blink
     * starts from black instead of the half-built set showing first.
     */
    public static boolean opensShut(java.util.List<SceneDef.EventDef> events) {
        if (events == null) return false;
        for (SceneDef.EventDef e : events) {
            if ("effect".equals(e.kind()) && "eyes_open".equals(e.id()) && e.t() <= 0f) return true;
        }
        return false;
    }

    private static float smooth(float k) {
        return k * k * (3f - 2f * k);
    }
}
