package com.hogwartsmagic.pensieve.scene;

import java.util.List;

/**
 * Pure maths for the {@code sky} effect: a daytime sky for a memory whose biome sky and fog are black
 * ({@code memory_void}). The client draws a large camera-centred dome whose colour runs from the horizon to the zenith
 * ({@link #colour}) under a few soft cloud layers, and, while it shows, lifts the fog colour from black to the horizon
 * colour ({@link #fogColour}) so distant blocks fade into haze instead of into night. Drawing is
 * {@code client/pensieve/SkyRenderer}.
 *
 * <p>Colours are linear 0..1 RGB, picked to read as a real sky rather than a saturated primary.
 */
public final class SkyDome {
    /** The looks a sky may take; the first is the default. */
    public static final List<String> STYLES = List.of("day", "overcast", "dawn", "storm", "storm_far");
    public static final String STORM = "storm";
    /**
     * A night storm seen across a distance: the storm's clouds, scud and lightning over a darker sky, but without its
     * haze, so the scene's own {@code fog_far} decides how far one sees (a castle 150 blocks off stays sharp).
     */
    public static final String STORM_FAR = "storm_far";
    /** A lightning flash lights the sky to at most this much (the ceiling's flicker peaks at 1; the open sky is dimmer). */
    public static final double FLASH_PEAK = 0.85;
    /**
     * Storm clouds slide this much of a texture tile a tick along the wind (unit direction), times the layer's speed.
     * Picked from the angular speed overhead ({@link #cloudAngularSpeed}): the deck drifts about 1.3 degrees a second, the
     * scud under it about 2.3.
     */
    public static final float STORM_DRIFT = 0.0003f;
    /** The storm's cloud layers, deck first: {texture scale, speed, height as a share of the dome radius}. */
    public static final float[][] STORM_LAYERS = {{1.0f, 1.0f, 0.16f}, {0.7f, 1.4f, 0.13f}, {0.45f, 2.2f, 0.09f}};
    /** The cloud tile is this share of the dome radius (times the layer's scale). */
    public static final float CLOUD_TILE_SHARE = 0.6f;
    /** The camera-centred rain sheets of a storm: radii in blocks and the alpha of each (the far ones are the thickest). */
    public static final float[] STORM_BANDS = {35f, 70f, 130f};
    public static final float[] STORM_BAND_ALPHA = {0.07f, 0.09f, 0.12f};
    /** While a storm sky shows, the fog closes in to these (blocks): a haze that thickens the far stands. */
    public static final float STORM_FOG_START = 30f, STORM_FOG_END = 240f;
    public static final String DAY = "day";
    /** The sky fades in from its {@code t}, and out over the end of a finite {@code duration}, over this many ticks. */
    public static final int FADE_TICKS = 20;
    /** The dome's smallest radius, and the share of the far plane it stays inside. */
    public static final float MIN_RADIUS = 48f, FAR_SHARE = 0.85f;

    /** {horizon, zenith} per style, in {@link #STYLES} order. */
    private static final float[][][] COLOURS = {
            {{0.80f, 0.88f, 0.96f}, {0.34f, 0.56f, 0.88f}},     // day: pale hazy horizon to a soft blue
            {{0.80f, 0.82f, 0.84f}, {0.62f, 0.65f, 0.69f}},     // overcast: English grey-white
            {{0.98f, 0.75f, 0.58f}, {0.40f, 0.52f, 0.76f}},     // dawn: warm horizon, cool blue overhead
            {{0x4A / 255f, 0x52 / 255f, 0x5C / 255f}, {0x26 / 255f, 0x2C / 255f, 0x35 / 255f}}, // storm: dark grey day storm
            {{0x34 / 255f, 0x3B / 255f, 0x46 / 255f}, {0x15 / 255f, 0x19 / 255f, 0x21 / 255f}}, // storm_far: a night storm
    };

    private SkyDome() {
    }

    /** Either storm: the clouds, the scud and the lightning flashes (only {@link #STORM} draws the haze in). */
    public static boolean isStorm(String style) {
        return STORM.equals(style) || STORM_FAR.equals(style);
    }

    /** {@code style} if it is one of {@link #STYLES}, else the default (an absent or unknown style is day). */
    public static String style(String style) {
        return style != null && STYLES.contains(style) ? style : DAY;
    }

    private static float[][] of(String style) {
        return COLOURS[STYLES.indexOf(style(style))];
    }

    /** The horizon colour of {@code style}: also what the fog turns to. */
    public static float[] horizon(String style) {
        return of(style)[0].clone();
    }

    /**
     * The sky colour at {@code elevation}, 0 (the horizon) to 1 (the zenith); out of range is clamped. The blue deepens
     * quickly off the horizon and slowly overhead, as a real sky does.
     */
    public static float[] colour(String style, float elevation) {
        float[][] c = of(style);
        float e = Math.max(0f, Math.min(1f, elevation));
        float k = (float) Math.pow(e, 0.6);
        return new float[] {c[0][0] + (c[1][0] - c[0][0]) * k, c[0][1] + (c[1][1] - c[0][1]) * k,
                c[0][2] + (c[1][2] - c[0][2]) * k};
    }

    /**
     * How much of the sky shows {@code local} ticks after the effect's {@code t}: 0 before it, easing up to 1 over
     * {@link #FADE_TICKS}, then 1 until the scene ends ({@code duration} 0) or until the last {@link #FADE_TICKS} of a
     * finite {@code duration}, easing out to 0 at its end.
     */
    public static float fade(float local, int duration) {
        float in = smooth(local / FADE_TICKS);
        if (duration <= 0) return in;
        float out = smooth((duration - local) / FADE_TICKS);
        return Math.min(in, out);
    }

    /**
     * {@link #fade(float, int)} for a sky keyed at scene tick {@code startT}: one keyed at tick 0 is the scene's own
     * sky, so it shows in full from the first tick instead of fading in out of the reveal; a later one fades in as
     * usual. A finite {@code duration} still fades out at its end.
     */
    public static float fade(float local, int duration, float startT) {
        if (startT > 0) return fade(local, duration);
        if (local < 0f) return 0f;
        if (duration <= 0) return 1f;
        return smooth((duration - local) / FADE_TICKS);
    }

    private static float smooth(float x) {
        float k = Math.max(0f, Math.min(1f, x));
        return k * k * (3f - 2f * k);
    }

    /** The fog colour while the sky is {@code fade} shown: black (the memory's own) up to the horizon colour. */
    public static float[] fogColour(String style, float fade) {
        float[] h = of(style)[0];
        float f = Math.max(0f, Math.min(1f, fade));
        return new float[] {h[0] * f, h[1] * f, h[2] * f};
    }

    /**
     * The dome's radius for a scene of {@code sceneRadius} blocks: beyond the farthest set block, but never past
     * {@link #FAR_SHARE} of the camera's far plane (a clipped dome would show black), and not below {@link #MIN_RADIUS}.
     */
    public static float domeRadius(float sceneRadius, float farPlane) {
        float want = Math.max(MIN_RADIUS, sceneRadius * 1.25f + 8f);
        return Math.min(want, farPlane * FAR_SHARE);
    }

    /**
     * The lightning flash on the sky {@code t} ticks into the scene, 0 to {@link #FLASH_PEAK}: the strongest of the
     * flickers of every strike in {@code strikeTicks} (the scene ticks of the scene's {@code lightning} effects; null or
     * empty is none). Each strike flickers two or three times over {@link CeilingSky#FLASH_TICKS} (1.3 s), by the
     * shared {@link CeilingSky#flicker}, the second and third pulses picked from the strike's own tick so two strikes
     * differ. Pure: every viewer sees the same flash at the same moment.
     */
    public static double flash(double t, double[] strikeTicks) {
        if (strikeTicks == null) return 0.0;
        float best = 0f;
        for (double s : strikeTicks) {
            double dt = t - s;
            if (dt < 0.0 || dt >= CeilingSky.FLASH_TICKS) continue;
            long h = mix(Double.doubleToLongBits(s) ^ 0x2545F4914F6CDD1DL);
            float second = 0.45f + 0.35f * unit(h);
            h = mix(h);
            float third = unit(h) < 0.5f ? 0f : 0.5f + 0.4f * unit(mix(h));
            best = Math.max(best, CeilingSky.flicker((float) dt, second, third));
        }
        return Math.min(1f, best) * FLASH_PEAK;
    }

    private static final float[] FLASH_TINT = {0.78f, 0.82f, 0.95f};

    /** Channel {@code ch} (0 r, 1 g, 2 b) of a colour value {@code c} lit by {@code flash} toward a pale blue-white. */
    public static float lift(float c, int ch, double flash) {
        float f = (float) Math.max(0.0, Math.min(1.0, flash));
        return c + (FLASH_TINT[ch] - c) * f * 0.9f;
    }

    /** {@code colour} lit by a {@code flash} (0..FLASH_PEAK): toward a pale blue-white, 0 leaves it as it is. */
    public static float[] flashed(float[] colour, double flash) {
        return new float[] {lift(colour[0], 0, flash), lift(colour[1], 1, flash), lift(colour[2], 2, flash)};
    }

    /**
     * How much of a flash reaches the sky in direction {@code (vx, vz)} when the bolt lies in direction {@code (sx, sz)}
     * (both seen from the camera, any length): 1 toward the bolt, falling to 0.4 on the far side, so the cloud nearest the
     * bolt is lit most. 1 when either direction is degenerate.
     */
    public static double flashWeight(double vx, double vz, double sx, double sz) {
        double a = Math.sqrt(vx * vx + vz * vz), b = Math.sqrt(sx * sx + sz * sz);
        if (a < 1.0e-6 || b < 1.0e-6) return 1.0;
        double cos = (vx * sx + vz * sz) / (a * b);
        return 0.4 + 0.6 * (0.5 + 0.5 * cos);
    }

    /**
     * How fast a storm cloud layer sweeps the sky, radians a tick, at elevation {@code e} (radians; pi/2 overhead): the
     * layer's linear speed over its height times sin(e), the worst direction across the view. Independent of the radius.
     */
    public static double cloudAngularSpeed(float speed, float scale, float height, double e) {
        return STORM_DRIFT * speed * CLOUD_TILE_SHARE * scale / height * Math.sin(e);
    }

    /** The rain curtains' streaks fall this fast (blocks a tick) and a curtain's texture tile is this tall per block of radius. */
    public static final double SHEET_FALL = 0.65, SHEET_TILE_HEIGHT = 0.5;

    /**
     * The texture v of a curtain of {@code radius} at {@code up} blocks above its foot, {@code now} ticks in (band
     * {@code band} offsets its phase). The texture's image top is v 0 and its streaks are bright at the bottom (their
     * heads), so v grows downward: v falls with height, and, for the streaks to FALL, a given texture row must sit lower
     * as time passes, which is v dropping with time at a fixed height.
     */
    public static double sheetV(double up, double radius, double now, int band) {
        double tileH = radius * SHEET_TILE_HEIGHT;
        return -up / tileH - (now * SHEET_FALL / tileH + band * 0.31);
    }

    /** The height above a curtain's foot at which texture row {@code v} shows at {@code now}: the inverse of {@link #sheetV}. */
    public static double sheetHeightOf(double v, double radius, double now, int band) {
        double tileH = radius * SHEET_TILE_HEIGHT;
        return -(v + now * SHEET_FALL / tileH + band * 0.31) * tileH;
    }

    /** As {@link #stormFog(float, float, float)} into {@code out} {near, far}: no allocation, for the per-frame fog. */
    public static void stormFog(float near, float far, float share, float[] out) {
        float k = Math.max(0f, Math.min(1f, share));
        out[0] = near + (Math.min(near, STORM_FOG_START) - near) * k;
        out[1] = far + (Math.min(far, STORM_FOG_END) - far) * k;
    }

    /** The {near, far} fog of a storm {@code share} (0..1) shown: the scene's fog drawn in to the storm haze. */
    public static float[] stormFog(float near, float far, float share) {
        float k = Math.max(0f, Math.min(1f, share));
        float n = Math.min(near, STORM_FOG_START), f = Math.min(far, STORM_FOG_END);
        return new float[] {near + (n - near) * k, far + (f - far) * k};
    }

    /** A storm's wind direction from its {@code seed}: a unit {x, z}, the same for every viewer. */
    public static double[] stormWind(long seed) {
        double a = unit(mix(seed ^ 0x51ED270B3C6EF372L)) * Math.PI * 2;
        return new double[] {Math.cos(a), Math.sin(a)};
    }

    /** How far storm clouds of speed 1 have slid by {@code now} along the unit wind {@code (dx, dz)}, in texture tiles {u, v}. */
    public static double[] stormDrift(float now, double dx, double dz) {
        return new double[] {now * STORM_DRIFT * dx, now * STORM_DRIFT * dz};
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long h) {
        return (h >>> 40) / (float) (1 << 24);
    }

    /** How far the clouds have slid by scene time {@code now} (ticks), in texture tiles: a slow steady drift. */
    public static float cloudDrift(float now) {
        return now * 0.00035f;
    }
}
