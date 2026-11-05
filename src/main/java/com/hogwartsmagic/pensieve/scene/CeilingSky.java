package com.hogwartsmagic.pensieve.scene;

/**
 * Pure maths for the {@code enchanted_ceiling} effect: the Great Hall roof dissolving into a bewitched sky, either the
 * film's rolling storm ({@code "storm"}, the default: heavy cloud lit warm from below at the walls, lightning flickering
 * inside it) or a calm starry night ({@code "night"}). Everything is a function of the scene time and a seed, so every
 * viewer (late joiners included) sees the same stars twinkle and the same lightning at the same moment. The drawing is
 * {@code client/pensieve/EnchantedCeiling}.
 *
 * <p>Plane coordinates: {@code u, v} run -1..1 across the rectangle (x and z); block offsets are {@code u * hx}.
 * The storm is a volume: {@link #SLICES} stacked cloud slices from just above the plane to {@link #SKY_TOP} blocks up,
 * each wider than the last so the sky goes on past the walls, with a 3D {@link #density} field, under stars on a plane
 * {@link #STAR_HEIGHT} blocks up: the camera sees the cloud slide against the nearly still stars.
 */
public final class CeilingSky {
    /** A ceiling with no {@code duration} reveals over this many ticks (3 s). */
    public static final int DEFAULT_REVEAL_TICKS = 60;
    /** Cloud texture drift, in UV units a tick. */
    public static final float CLOUD_DRIFT = 0.0006f;
    /** The sky plane sits this far below the given height, so it never z-fights the roof's underside. */
    public static final float ROOF_GAP = 0.04f;
    /** Width of the soft edge of the dissolve front, as a fraction of the half-size. */
    private static final float DISSOLVE_SOFT = 0.35f;
    /** One shooting star in each window of this many ticks, at most... */
    private static final int SHOOT_WINDOW = 220;
    /** ...crossing the sky in this many ticks. */
    private static final int SHOOT_TICKS = 16;
    /** The share of stars on the Milky Way band, and the band's half-width in normalised units. */
    private static final float BAND_SHARE = 0.32f;
    private static final float BAND_WIDTH = 0.42f;
    /** Stars per square block of ceiling; the floor and ceiling on the count (the floor shrinks for a small ceiling). */
    public static final float STAR_DENSITY = 0.75f;
    public static final int MIN_STARS = 400, MAX_STARS = 1600;
    /** The sky texture's mean brightness (ceiling_sky.png): the gradient is divided by it so its average is exact. */
    public static final float SKY_TEXTURE_MEAN = 0.86f;
    /** Strikes: the first comes this long after the ceiling starts, then one every 6 to 14 s. */
    public static final int FIRST_STRIKE = 50, STRIKE_GAP_MIN = 120, STRIKE_GAP_MAX = 280;
    /** A strike's flicker lasts this many ticks. */
    public static final int FLASH_TICKS = 26;
    /** A flash lights the cloud this far round its centre, in blocks (gaussian radius). */
    public static final float FLASH_RADIUS = 7.5f;
    /** Thunder follows the flash after 10 to 30 ticks (it is far away). */
    public static final int THUNDER_DELAY_MIN = 10, THUNDER_DELAY_SPREAD = 20;
    /** The styles, by the scene key's value. */
    public static final String STORM = "storm", NIGHT = "night";
    /**
     * The third style: follow the world's actual sky (day, dawn and dusk, night, rain, thunder) as {@link #realWeights}
     * describes it. Unlike the other two it needs a clock and weather the scene does not have, so the drawing reads them
     * from the client level when it draws.
     */
    public static final String REAL = "real";

    private CeilingSky() {
    }

    /** How far the reveal is, 0 to 1 (ease-out), {@code elapsed} ticks after it began. */
    public static float revealAt(int elapsed, int duration) {
        return revealAt((float) elapsed, duration);
    }

    public static float revealAt(float elapsed, int duration) {
        if (elapsed <= 0) return 0f;
        if (duration <= 0 || elapsed >= duration) return 1f;
        float k = elapsed / duration;
        float inv = 1f - k;
        return 1f - inv * inv * inv;
    }

    /**
     * How revealed the sky is at the plane point {@code (u, v)} (each -1..1 across the rectangle), 0 to 1: the roof
     * dissolves from the middle outward, with a soft, slightly ragged front. 0 everywhere at reveal 0, 1 everywhere at 1.
     */
    public static float dissolve(float reveal, float u, float v) {
        if (reveal <= 0f) return 0f;
        if (reveal >= 1f) return 1f;
        float d = (float) Math.sqrt((u * u + v * v) * 0.5f);          // 0 centre .. 1 corner
        float ragged = 0.08f * (float) (Math.sin(u * 7.3f + v * 2.1f) * Math.sin(v * 6.1f - u * 3.7f));
        float front = reveal * (1f + DISSOLVE_SOFT + 0.08f) - DISSOLVE_SOFT;
        float k = (front - (d + ragged) + DISSOLVE_SOFT) / DISSOLVE_SOFT;
        k = Math.max(0f, Math.min(1f, k));
        return k * k * (3f - 2f * k);
    }

    /**
     * {@code count} stars, each {x, z, size}: x and z offsets from the centre inside {@code ±hx, ±hz}, and a size in
     * (0, 1] heavily weighted toward small (most stars are faint pinpricks, a few are bright). About a third crowd
     * along the seed's Milky Way band ({@link #bandAngle}), the rest are scattered evenly.
     */
    public static float[][] stars(long seed, float hx, float hz, int count) {
        float[][] out = new float[Math.max(0, count)][];
        double angle = bandAngle(seed);
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        long s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        for (int i = 0; i < out.length; i++) {
            s = mix(s + i);
            float u = unit(s) * 2f - 1f;
            s = mix(s);
            float v = unit(s) * 2f - 1f;
            s = mix(s);
            if (unit(s) < BAND_SHARE) {
                // Along the band: u runs the length of it, v is a narrow gaussian-ish spread across it.
                s = mix(s);
                float across = (unit(s) + unit(mix(s + 1)) + unit(mix(s + 2)) - 1.5f) * BAND_WIDTH;
                float bu = u * ca - across * sa;
                float bv = u * sa + across * ca;
                if (Math.abs(bu) <= 1f && Math.abs(bv) <= 1f) {   // off the ceiling: it stays a scattered star
                    u = bu;
                    v = bv;
                }
            }
            s = mix(s);
            float r = unit(s);
            float size = 0.12f + 0.88f * r * r * r * r;                 // a long tail of bright ones
            out[i] = new float[]{u * hx, v * hz, Math.min(1f, size)};
        }
        return out;
    }

    /** The direction the Milky Way band crosses the ceiling, radians, in normalised (u, v) space. */
    public static double bandAngle(long seed) {
        return 0.35 + unit(mix(seed ^ 0x5DEECE66DL)) * 0.9;            // always a diagonal, never along a wall
    }

    /** How much of the faint band glow lies at (u, v): 1 on the band's spine, falling to 0 across it. */
    public static float bandGlow(long seed, float u, float v) {
        double a = bandAngle(seed);
        float d = (float) (-Math.sin(a) * u + Math.cos(a) * v);
        return (float) Math.exp(-(d * d) / (BAND_WIDTH * BAND_WIDTH * 0.35f));
    }

    /**
     * Star {@code starIndex}'s brightness at scene time {@code t} ticks, 0.35 to 1: two slow sines on the star's own
     * phase and rate, from the sky's {@code seed} so two ceilings do not twinkle in step.
     */
    public static float twinkle(long seed, float t, int starIndex) {
        long h = mix(seed ^ (starIndex * 0x9E3779B97F4A7C15L + 0x2545F4914F6CDD1DL));
        float rate1 = 0.04f + 0.10f * unit(h);
        h = mix(h);
        float rate2 = 0.15f + 0.25f * unit(h);
        h = mix(h);
        float phase = 6.2831855f * unit(h);
        h = mix(h);
        float depth = 0.35f + 0.65f * unit(h);                       // some stars barely flicker, some pulse
        float w = 0.65f * (float) Math.sin(t * rate1 + phase) + 0.35f * (float) Math.sin(t * rate2 + phase * 1.7f);
        float k = 0.5f + 0.5f * w;                                   // 0..1
        float b = 1f - depth * (1f - k);
        return 0.35f + 0.65f * b;
    }

    /** How many stars a {@code hx} by {@code hz} ceiling gets: by area, the floor never above 1.5 per square block. */
    public static int starCount(float hx, float hz) {
        float area = 4f * hx * hz;
        int floor = Math.min(MIN_STARS, Math.round(area * 1.5f));
        return Math.max(floor, Math.min(MAX_STARS, Math.round(area * STAR_DENSITY)));
    }

    /** The edge of the ceiling, 0 over the middle to 1 at the walls: where the candle glow from below reaches. */
    public static float edge(float u, float v) {
        float m = Math.max(Math.abs(u), Math.abs(v));
        return smooth(0.45f, 1f, m);
    }

    /**
     * The sky colour at (u, v), before the texture: night, a deep gradient (#0B1030 at the walls to #1A2350 overhead)
     * plus a faint Milky Way band; storm, near-black blue-grey warmed by the candle glow at the walls. Divided by
     * {@link #SKY_TEXTURE_MEAN} so that, times the mottled texture, it averages to the stated colours.
     */
    public static float[] skyColour(String style, long seed, float u, float v) {
        float d = Math.min(1f, (float) Math.sqrt(u * u + v * v) / 1.2f);
        float k = 1f - d * d * (3f - 2f * d);                        // 1 overhead, 0 at the walls
        float r, g, b;
        if (NIGHT.equals(style)) {
            float band = bandGlow(seed, u, v) * (0.4f + 0.6f * k);
            r = 0x0B / 255f + (0x1A - 0x0B) / 255f * k + 0.055f * band;
            g = 0x10 / 255f + (0x23 - 0x10) / 255f * k + 0.060f * band;
            b = 0x30 / 255f + (0x50 - 0x30) / 255f * k + 0.095f * band;
        } else {
            float w = edge(u, v);
            r = 0.035f + 0.025f * k + 0.10f * w;
            g = 0.040f + 0.030f * k + 0.06f * w;
            b = 0.070f + 0.045f * k + 0.025f * w;
        }
        return new float[]{r / SKY_TEXTURE_MEAN, g / SKY_TEXTURE_MEAN, b / SKY_TEXTURE_MEAN};
    }

    // ------------------------------------------------------------------ the real sky

    /** Day time (0..24000) of each real-sky control point, and the day / dusk / night weights there; blends are eased. */
    private static final float[] REAL_AT = {1000f, 11000f, 12500f, 13800f, 22200f, 23250f, 25000f};
    private static final float[][] REAL_WEIGHTS = {
            {1f, 0f, 0f}, {1f, 0f, 0f}, {0f, 1f, 0f}, {0f, 0f, 1f}, {0f, 0f, 1f}, {0f, 1f, 0f}, {1f, 0f, 0f}};

    /**
     * How the world's sky looks, for the {@code real} style: {day, dusk, night, overcast, storm}, each 0 to 1, with
     * {@code day + dusk + night == 1}. {@code dayTime} is {@code level.getDayTime() % 24000} (any value is accepted and
     * wrapped): day is full from 1000 to 11000, dusk peaks at 12500 and, as dawn, at 23250, night is full from 13800 to
     * 22200, and each blend is eased. {@code overcast} is the rain level and {@code storm} the thunder level.
     */
    public static float[] realWeights(float dayTime, float rain, float thunder) {
        float t = dayTime % 24000f;
        if (t < 0f) t += 24000f;
        if (t < REAL_AT[0]) t += 24000f;                     // before 1000 is the tail of the dawn-to-day blend
        int i = 0;
        while (i < REAL_AT.length - 2 && t >= REAL_AT[i + 1]) i++;
        float k = smooth(0f, 1f, (t - REAL_AT[i]) / (REAL_AT[i + 1] - REAL_AT[i]));
        float day = REAL_WEIGHTS[i][0] + (REAL_WEIGHTS[i + 1][0] - REAL_WEIGHTS[i][0]) * k;
        float night = REAL_WEIGHTS[i][2] + (REAL_WEIGHTS[i + 1][2] - REAL_WEIGHTS[i][2]) * k;
        float dusk = REAL_WEIGHTS[i][1] + (REAL_WEIGHTS[i + 1][1] - REAL_WEIGHTS[i][1]) * k;
        return new float[]{day, dusk, night, clamp01(rain), clamp01(thunder)};
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static float clamp01(float f) {
        return f < 0f ? 0f : f > 1f ? 1f : f;
    }

    /**
     * A storm cloud's colour at (u, v), before the texture: charcoal blue-grey, warmed toward amber near the walls (the
     * candles below light the cloud's underside), and lit pale blue-white from within round a flash. {@code flash} is
     * {@link #flash}'s result or null; {@code hx, hz} turn the flash's reach into plane units. May exceed 1 in a flash.
     */
    public static float[] stormCloudColour(float u, float v, float[] flash, float hx, float hz) {
        float w = 0.75f * edge(u, v);
        float r = 0.30f + (0.88f - 0.30f) * w;
        float g = 0.33f + (0.60f - 0.33f) * w;
        float b = 0.42f + (0.36f - 0.42f) * w;
        if (flash != null) {
            float dx = (u - flash[0]) * hx, dz = (v - flash[1]) * hz;
            float f = flash[2] * (float) Math.exp(-(dx * dx + dz * dz) / (FLASH_RADIUS * FLASH_RADIUS)) * 1.6f;
            r += 0.75f * f;
            g += 0.80f * f;
            b += 1.00f * f;
        }
        return new float[]{r, g, b};
    }

    /** The pale shimmer on the dissolve front where it is revealed {@code a} (0..1): strongest half-way, 0 at 0 and 1. */
    public static float shimmer(float a) {
        float e = 4f * a * (1f - a);
        return e * e * 0.35f;
    }

    /** When strike {@code k} (0, 1, 2...) of this sky begins, in ticks after the ceiling started. */
    public static float strikeStart(long seed, int k) {
        float t = FIRST_STRIKE;
        for (int i = 0; i < k; i++) t += strikeGap(seed, i);
        return t;
    }

    /** The latest strike begun by {@code t} ticks after the ceiling started, or -1 before the first. */
    public static int strikeIndex(long seed, float t) {
        if (t < FIRST_STRIKE) return -1;
        int k = 0;
        float start = FIRST_STRIKE;
        while (start + strikeGap(seed, k) <= t) {
            start += strikeGap(seed, k);
            k++;
        }
        return k;
    }

    private static float strikeGap(long seed, int i) {
        return STRIKE_GAP_MIN + (STRIKE_GAP_MAX - STRIKE_GAP_MIN) * unit(mix(seed ^ (0xA24BAED4963EE407L * (i + 1))));
    }

    /** When strike {@code k}'s thunder is heard, in ticks after the ceiling started. */
    public static float thunderAt(long seed, int k) {
        return strikeStart(seed, k) + THUNDER_DELAY_MIN
                + THUNDER_DELAY_SPREAD * unit(mix(seed ^ (k * 0x3C6EF372FE94F82BL + 5)));
    }

    /**
     * The lightning {@code t} ticks after the ceiling started: {u, v, intensity, forked, k, height}, or null while
     * the sky is dark. Intensity (0..1) flickers in two or three quick pulses over {@link #FLASH_TICKS}; forked is 1
     * when this strike also shows a bolt; k is the strike number (for {@link #bolt}); height is how far above the
     * plane, inside the cloud volume, it strikes (6 to 18 blocks).
     */
    public static float[] flash(long seed, float t) {
        int k = strikeIndex(seed, t);
        if (k < 0) return null;
        float dt = t - strikeStart(seed, k);
        if (dt >= FLASH_TICKS) return null;
        long h = strikeHash(seed, k);
        float u = unit(h) * 1.5f - 0.75f;
        h = mix(h);
        float v = unit(h) * 1.5f - 0.75f;
        h = mix(h);
        float forked = unit(h) < 0.4f ? 1f : 0f;
        h = mix(h);
        float second = 0.45f + 0.35f * unit(h);
        h = mix(h);
        float third = unit(h) < 0.5f ? 0f : 0.5f + 0.4f * unit(mix(h));
        float height = 6f + 12f * unit(mix(mix(h)));
        float i = flicker(dt, second, third);
        if (i <= 0.001f) return null;
        return new float[]{u, v, Math.min(1f, i), forked, k, height};
    }

    /**
     * The shared lightning flicker, {@code dt} ticks after a strike: 0 before it and from {@link #FLASH_TICKS} on, else
     * the strongest of a first pulse (peak 1, at tick 1), a second at tick 5 of peak {@code second} and a third at tick
     * 12 of peak {@code third} (0 for none): two or three quick flickers. {@code CeilingSky#flash} and
     * {@code SkyDome#flash} both use it, so the roof and the open sky flicker alike.
     */
    public static float flicker(float dt, float second, float third) {
        if (dt < 0f || dt >= FLASH_TICKS) return 0f;
        return Math.max(pulse(dt, 0f, 1f), Math.max(pulse(dt, 5f, second), pulse(dt, 12f, third)));
    }

    private static long strikeHash(long seed, int k) {
        return mix(seed ^ (k * 0x9FB21C651E98DF25L + 77));
    }

    /** One flicker: a 1-tick rise to {@code peak} at {@code at}, then a fast decay. */
    private static float pulse(float dt, float at, float peak) {
        float x = dt - at;
        if (x < 0f || peak <= 0f) return 0f;
        if (x < 1f) return peak * x;
        return peak * (float) Math.exp(-(x - 1f) / 2.5f);
    }

    /**
     * Strike {@code k}'s forked bolt as segments {u0, v0, u1, v1, width} in plane units (every end inside -1..1): a
     * jagged main stroke from the flash outward, width 1 tapering, and one or two thinner branches (width 0.5).
     */
    public static float[][] bolt(long seed, int k) {
        long h = strikeHash(seed, k);
        float u = unit(h) * 1.5f - 0.75f;
        h = mix(h);
        float v = unit(h) * 1.5f - 0.75f;
        h = mix(mix(mix(mix(h))));
        double a = unit(h) * Math.PI * 2;
        h = mix(h);
        float len = 0.5f + 0.4f * unit(h);
        java.util.List<float[]> out = new java.util.ArrayList<>();
        stroke(out, u, v, a, len, 9, 1f, h);
        h = mix(h ^ 0x51ED27L);
        int branches = 1 + (unit(h) < 0.5f ? 1 : 0);
        for (int b = 0; b < branches; b++) {
            float[] from = out.get(2 + b * 3);
            h = mix(h + b);
            double ba = a + (unit(h) < 0.5f ? -1 : 1) * (0.45 + 0.4 * unit(mix(h)));
            stroke(out, from[2], from[3], ba, len * 0.4f, 4, 0.5f, mix(h + 7));
        }
        return out.toArray(new float[0][]);
    }

    private static void stroke(java.util.List<float[]> out, float u, float v, double a, float len, int n, float w,
                               long h) {
        float step = len / n;
        float cu = u, cv = v;
        double px = -Math.sin(a), pz = Math.cos(a);
        for (int i = 0; i < n; i++) {
            h = mix(h + i);
            float j = (unit(h) - 0.5f) * 0.12f;
            float nu = clamp(cu + (float) (Math.cos(a) * step + px * j), 0.98f);
            float nv = clamp(cv + (float) (Math.sin(a) * step + pz * j), 0.98f);
            out.add(new float[]{cu, cv, nu, nv, w * (1f - 0.5f * i / n)});
            cu = nu;
            cv = nv;
        }
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    // ------------------------------------------------------------------ the storm's cloud volume

    /** Storm volume: this many stacked cloud slices, from {@link #SLICE_LOW} to {@link #SKY_TOP} blocks above the plane. */
    public static final int SLICES = 16;
    public static final float SLICE_LOW = 0.6f, SKY_TOP = 26f;
    /** Each slice reaches this much further out than the hall per block of height, so the sky goes on over the walls. */
    public static final float SLICE_SPREAD = 1.6f;
    /** The stars lie on a plane this high above the ceiling, under a dark backdrop a little higher. */
    public static final float STAR_HEIGHT = 62f, BACKDROP_HEIGHT = 66f;
    /** The star plane and backdrop reach this far past the hall on every side. */
    public static final float FAR_REACH = 150f;
    /** The far sky is fogged by the scene's own fog stretched this much (it is distant sky, not the hall). */
    public static final float FAR_FOG_STRETCH = 1.8f;
    /** Lightning lights the slices within this many blocks of its height (exponential falloff). */
    public static final float FLASH_DEPTH = 5f;
    /** The density field's horizontal and vertical scales, in blocks, and its drift in blocks a tick. */
    private static final float NOISE_XZ = 13f, NOISE_Y = 7f, NOISE_DRIFT = 0.025f;

    /** Height of slice {@code i} of {@code n} above the plane: packed near the bottom, where the cloud is thick. */
    public static float sliceHeight(int i, int n) {
        float k = n <= 1 ? 0f : i / (float) (n - 1);
        return SLICE_LOW + (SKY_TOP - SLICE_LOW) * (float) Math.pow(k, 1.6);
    }

    /** How much further than the hall's half-size a slice at height {@code h} reaches on every side. */
    public static float sliceReach(float h) {
        return SLICE_SPREAD * h;
    }

    /**
     * The cloud's 3D density at (x, h, z) blocks from the ceiling centre ({@code h} above the plane) at scene time
     * {@code t}: two octaves of smooth lattice noise, 0..1, drifting slowly so the masses roll. Continuous in all three
     * axes, so neighbouring slices agree and the stack reads as one volume rather than separate sheets.
     */
    public static float density(long seed, float x, float h, float z, float t) {
        float dx = x + t * NOISE_DRIFT, dz = z + t * NOISE_DRIFT * 0.4f;
        float a = lattice(seed, dx / NOISE_XZ, h / NOISE_Y, dz / NOISE_XZ);
        float b = lattice(seed ^ 0x5DEECE66DL, dx / (NOISE_XZ * 0.45f) + 3.1f, h / (NOISE_Y * 0.5f), dz / (NOISE_XZ * 0.45f));
        return Math.max(0f, Math.min(1f, 0.68f * a + 0.32f * b));
    }

    /**
     * A slice's opacity at (x, h, z): thick near the plane and thinning with height, with real gaps where the density is
     * low (the stars show through them). 0..{@link #sliceBase}(h).
     */
    public static float sliceAlpha(long seed, float x, float h, float z, float t) {
        return sliceBase(h) * smooth(0.30f, 0.72f, density(seed, x, h, z, t));
    }

    /** The most opaque a slice at height {@code h} can be: 0.58 at the bottom down to 0.08 at the top. */
    public static float sliceBase(float h) {
        float k = Math.max(0f, Math.min(1f, h / SKY_TOP));
        return 0.08f + 0.5f * (float) Math.pow(1f - k, 0.8);
    }

    /**
     * A slice's colour at (x, h, z) blocks (before the texture), for a hall of half-size {@code hx, hz}: charcoal
     * blue-grey, lit amber from below near the walls (fading with height), cooler and hazier higher up (it fades toward
     * the dark of the far sky), and lit from inside round a flash ({@link #flash}, or null) with depth falloff.
     */
    public static float[] sliceColour(float x, float h, float z, float hx, float hz, float[] flash) {
        float k = Math.max(0f, Math.min(1f, h / SKY_TOP));
        float warm = 0.8f * edge(x / hx, z / hz) * (float) Math.exp(-h / 6f);
        float r = 0.30f - 0.10f * k, g = 0.33f - 0.09f * k, b = 0.42f - 0.08f * k;
        r += (0.88f - r) * warm;
        g += (0.60f - g) * warm;
        b += (0.36f - b) * warm;
        float haze = k * k;                                  // into the far dark: #121622
        r += (0.07f - r) * haze * 0.6f;
        g += (0.086f - g) * haze * 0.6f;
        b += (0.133f - b) * haze * 0.6f;
        if (flash != null) {
            float dx = x - flash[0] * hx, dz = z - flash[1] * hz;
            float f = flash[2] * 1.6f * (float) Math.exp(-(dx * dx + dz * dz) / (FLASH_RADIUS * FLASH_RADIUS))
                    * (float) Math.exp(-Math.abs(h - flash[5]) / FLASH_DEPTH);
            r += 0.75f * f;
            g += 0.80f * f;
            b += 1.00f * f;
        }
        return new float[]{r, g, b};
    }

    /**
     * How far (radians) a point {@code height} blocks above the camera swings across the view when the camera moves
     * {@code move} blocks sideways beneath it: parallax. The far stars swing a fraction of what the low cloud does.
     */
    public static double parallax(float move, float height) {
        return Math.atan2(move, height);
    }

    /** Smooth lattice noise in 0..1 at (x, y, z) lattice units: hashed corners, smoothstep-weighted trilinear blend. */
    private static float lattice(long seed, float x, float y, float z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        float fx = x - x0, fy = y - y0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        float c000 = corner(seed, x0, y0, z0), c100 = corner(seed, x0 + 1, y0, z0);
        float c010 = corner(seed, x0, y0 + 1, z0), c110 = corner(seed, x0 + 1, y0 + 1, z0);
        float c001 = corner(seed, x0, y0, z0 + 1), c101 = corner(seed, x0 + 1, y0, z0 + 1);
        float c011 = corner(seed, x0, y0 + 1, z0 + 1), c111 = corner(seed, x0 + 1, y0 + 1, z0 + 1);
        float a = c000 + (c100 - c000) * fx, b = c010 + (c110 - c010) * fx;
        float c = c001 + (c101 - c001) * fx, d = c011 + (c111 - c011) * fx;
        float e = a + (b - a) * fy, f = c + (d - c) * fy;
        return e + (f - e) * fz;
    }

    private static float corner(long seed, int x, int y, int z) {
        return unit(mix(seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L)));
    }

    /** Cloud texture UV drift at scene time {@code t} ticks. */
    public static float cloudOffset(float t) {
        return t * CLOUD_DRIFT;
    }

    /** The height the sky plane is drawn at, for a ceiling event at block height {@code y}. */
    public static float planeY(float y) {
        return y - ROOF_GAP;
    }

    /**
     * The shooting star in flight at scene time {@code t}, or null: {x0, z0, x1, z1, progress} with both ends inside
     * {@code ±hx, ±hz} and progress 0..1 along it. Rare: at most one every {@value #SHOOT_WINDOW} ticks, and some
     * windows have none.
     */
    public static float[] shootingStar(long seed, float t, float hx, float hz) {
        if (t < 0) return null;
        int window = (int) (t / SHOOT_WINDOW);
        long h = mix(seed ^ (window * 0xD1B54A32D192ED03L));
        if (unit(h) < 0.35f) return null;                            // a quiet stretch
        h = mix(h);
        float start = unit(h) * (SHOOT_WINDOW - SHOOT_TICKS);
        float p = (t - window * SHOOT_WINDOW - start) / SHOOT_TICKS;
        if (p < 0f || p > 1f) return null;
        h = mix(h);
        float x0 = (unit(h) * 1.6f - 0.8f) * hx;
        h = mix(h);
        float z0 = (unit(h) * 1.6f - 0.8f) * hz;
        h = mix(h);
        double a = unit(h) * Math.PI * 2;
        float len = Math.min(12f, 0.6f * Math.max(hx, hz));
        float x1 = clamp(x0 + (float) Math.cos(a) * len, hx);
        float z1 = clamp(z0 + (float) Math.sin(a) * len, hz);
        return new float[]{x0, z0, x1, z1, p};
    }

    private static float clamp(float v, float h) {
        return Math.max(-h, Math.min(h, v));
    }

    // ------------------------------------------------------------------ the real sky's depth

    /**
     * The real sky's far layers are drawn on the plane but sampled as if they lay this many blocks above it, so moving
     * the camera slides them past one another (parallax): the stars and the Milky Way, the high cirrus, the cloud deck.
     * Real cloud slices under a roof sit nearer still, between the plane and the roof.
     */
    public static final float STAR_LAYER_H = 300f, CIRRUS_H = 120f, DECK_H = 50f;
    /**
     * A camera nearer the plane than this (or above it) is treated as this far below it: the true scale grows without
     * bound as the camera reaches the plane, and the layers would race past. The mapping stays a uniform scale about the
     * camera, so everything still lines up.
     */
    public static final float PARALLAX_MIN_DY = 4f;
    /** The camera depth under the plane the layers' sizes are tuned for (about the castle hall's floor). */
    public static final float PARALLAX_REF_DY = 16f;

    /**
     * How much bigger a layer {@code h} above the plane is than the plane, seen from a camera {@code dy} below the
     * plane: {@code (dy + h) / dy} (1 for a layer on the plane). The camera ray through a plane point meets the layer
     * this many times as far from the camera, horizontally.
     */
    public static float parallaxScale(float dy, float h) {
        float d = Math.max(PARALLAX_MIN_DY, dy);
        return (d + Math.max(0f, h)) / d;
    }

    /**
     * Where the camera ray through plane point {@code (px, pz)} meets a layer {@code h} above the plane, horizontally,
     * into {@code out} ({x, z}): {@code C + (P - C) * (dy + h) / dy} for a camera at {@code (cx, cz)}, {@code dy}
     * below the plane. Its texture is sampled there, so it slides as the camera moves the way a far layer would.
     */
    public static void parallaxPoint(float cx, float cz, float px, float pz, float dy, float h, float[] out) {
        float k = parallaxScale(dy, h);
        out[0] = cx + (px - cx) * k;
        out[1] = cz + (pz - cz) * k;
    }

    /** The inverse of {@link #parallaxPoint}: where on the plane a point {@code (sx, sz)} of layer {@code h} shows. */
    public static void planePoint(float cx, float cz, float sx, float sz, float dy, float h, float[] out) {
        float k = parallaxScale(dy, h);
        out[0] = cx + (sx - cx) / k;
        out[1] = cz + (sz - cz) / k;
    }

    /**
     * A texture tile size for layer {@code h}, in the layer's own blocks, that looks {@code tile} blocks wide on the
     * plane from {@link #PARALLAX_REF_DY} below: deep layers are drawn with big tiles so they are not finely repeated.
     */
    public static float layerTile(float tile, float h) {
        return tile * parallaxScale(PARALLAX_REF_DY, h);
    }

    /**
     * The sun's direction, a unit vector into {@code out} ({x, y, z}), for vanilla's time of day
     * ({@code Level#getTimeOfDay}: 0 at noon, 0.5 at midnight): straight up at noon, rising in the east (+x) and setting
     * in the west, as vanilla draws it. The moon is the opposite direction.
     */
    public static void sunDirection(float timeOfDay, float[] out) {
        double a = timeOfDay * Math.PI * 2.0;
        out[0] = (float) -Math.sin(a);
        out[1] = (float) Math.cos(a);
        out[2] = 0f;
    }

    /**
     * Where the ray from a camera at {@code (cx, cz)}, {@code dy} below the plane, along the unit direction
     * {@code (dx, dy2, dz)} crosses the plane, into {@code out} ({x, z, distance along the ray}), when that point lies
     * inside the {@code ±hx, ±hz} rectangle (centre 0, 0). False, with {@code out} untouched, when the camera is not
     * under the plane, the ray does not rise (or barely: it would meet the plane absurdly far off), or it misses.
     */
    public static boolean planeHit(float cx, float cz, float dy, float dx, float dy2, float dz, float hx, float hz,
                                   float[] out) {
        if (dy <= 0f || dy2 <= 0.01f) return false;
        float t = dy / dy2;
        float x = cx + dx * t, z = cz + dz * t;
        if (Math.abs(x) > hx || Math.abs(z) > hz) return false;
        out[0] = x;
        out[1] = z;
        out[2] = t;
        return true;
    }

    /** How many stars the end of the star budget fades over, so a shrinking budget dims stars out instead of popping them. */
    public static final float STAR_BUDGET_FADE = 96f;

    /**
     * Brightness factor (0..1) of star {@code index} when the budget keeps {@code budget} stars (fractional): 1 well
     * inside it, falling linearly to 0 over the last {@link #STAR_BUDGET_FADE} stars before the cutoff. As the budget
     * changes smoothly with the camera's distance below the plane, each star fades rather than flickering in or out.
     */
    public static float starBudgetFade(int index, float budget) {
        return clamp01((budget - index) / STAR_BUDGET_FADE);
    }

    /** 1 deep inside the {@code±hx, ±hz} rectangle, falling to 0 at its edge over the last {@code soft} blocks. */
    public static float insideFade(float x, float z, float hx, float hz, float soft) {
        float d = Math.min(hx - Math.abs(x), hz - Math.abs(z));
        return smooth(0f, Math.max(1e-3f, soft), d);
    }

    /** Daytime sky colours: overhead and at the horizon. */
    private static final float[] DAY_ZENITH = {0.16f, 0.36f, 0.78f}, DAY_HORIZON = {0.68f, 0.82f, 0.95f};
    /** Dusk: overhead, the horizon toward the sun and the horizon away from it. */
    private static final float[] DUSK_ZENITH = {0.17f, 0.19f, 0.40f}, DUSK_TOWARD = {0.98f, 0.58f, 0.28f},
            DUSK_AWAY = {0.55f, 0.40f, 0.55f};
    /** Night: overhead and the faint glow low down. */
    private static final float[] NIGHT_ZENITH = {0.035f, 0.05f, 0.14f}, NIGHT_HORIZON = {0.09f, 0.11f, 0.23f};

    /**
     * The sky's colour along the unit view ray {@code (rx, ry, rz)} for the unit sun direction {@code (sx, sy, sz)} and
     * {@link #realWeights} {@code w}, into {@code out} (rgb): by the ray's elevation, hazy and pale low down (rays that
     * graze the plane, near the walls) and deep overhead; by day blue with a bright halo round the sun; at dusk an
     * indigo zenith over a horizon glowing orange toward the sun's azimuth and dusky violet away from it; at night deep
     * navy with a faint glow low down and a cool halo round the moon. Rain washes the colour toward grey (the caller
     * also dims it). Never above 1 in any channel.
     */
    public static void atmosphere(float rx, float ry, float rz, float sx, float sy, float sz, float[] w, float[] out) {
        float e = clamp01(ry);
        float cosSun = rx * sx + ry * sy + rz * sz;
        float day = w[0], dusk = w[1], night = w[2], overcast = w[3];
        float r = 0f, g = 0f, b = 0f;
        if (day > 0f) {
            float h = (float) Math.pow(1f - e, 1.6);
            float halo = (0.30f * (float) Math.exp((cosSun - 1f) * 40f) + 0.16f * (float) Math.exp((cosSun - 1f) * 6f))
                    * clamp01(sy * 4f + 0.5f);
            r += day * (lerp(DAY_ZENITH[0], DAY_HORIZON[0], h) + halo);
            g += day * (lerp(DAY_ZENITH[1], DAY_HORIZON[1], h) + halo * 0.95f);
            b += day * (lerp(DAY_ZENITH[2], DAY_HORIZON[2], h) + halo * 0.85f);
        }
        if (dusk > 0f) {
            float toward = (float) Math.pow(towardSun(rx, rz, sx, sz), 1.5);
            float h = (float) Math.pow(1f - e, 1.1);
            float glow = 0.25f * (float) Math.exp((cosSun - 1f) * 8f);
            r += dusk * (lerp(DUSK_ZENITH[0], lerp(DUSK_AWAY[0], DUSK_TOWARD[0], toward), h) + glow);
            g += dusk * (lerp(DUSK_ZENITH[1], lerp(DUSK_AWAY[1], DUSK_TOWARD[1], toward), h) + glow * 0.7f);
            b += dusk * (lerp(DUSK_ZENITH[2], lerp(DUSK_AWAY[2], DUSK_TOWARD[2], toward), h) + glow * 0.4f);
        }
        if (night > 0f) {
            float h = (float) Math.pow(1f - e, 1.4);
            float moon = sy < 0f ? 0.07f * (float) Math.exp((-cosSun - 1f) * 30f) : 0f;
            r += night * (lerp(NIGHT_ZENITH[0], NIGHT_HORIZON[0], h) + moon * 0.7f);
            g += night * (lerp(NIGHT_ZENITH[1], NIGHT_HORIZON[1], h) + moon * 0.8f);
            b += night * (lerp(NIGHT_ZENITH[2], NIGHT_HORIZON[2], h) + moon);
        }
        float grey = 0.30f * r + 0.59f * g + 0.11f * b;
        float wash = 0.75f * clamp01(overcast);
        out[0] = clamp01(lerp(r, grey, wash));
        out[1] = clamp01(lerp(g, grey, wash));
        out[2] = clamp01(lerp(b, grey * 1.04f, wash));
    }

    /** 0 (the ray looks away from the sun's azimuth) to 1 (toward it); 0.5 when either is near vertical. */
    private static float towardSun(float rx, float rz, float sx, float sz) {
        float rl = (float) Math.sqrt(rx * rx + rz * rz), sl = (float) Math.sqrt(sx * sx + sz * sz);
        if (rl < 1e-4f || sl < 1e-4f) return 0.5f;
        return clamp01(0.5f + 0.5f * (rx * sx + rz * sz) / (rl * sl));
    }

    /**
     * A far cloud layer's colour along the unit view ray for the sun direction and weights, into {@code out} (rgb, the
     * texture is near white): white by day (the high cirrus a touch brighter than the deck), at dusk lit orange on the
     * sun's side and dusky violet away from it (the cirrus pinker, it still catches the sun), at night a dim moonlit
     * blue-grey; rain greys and darkens it. {@code high} is 0 for the deck, 1 for the cirrus.
     */
    public static void cloudTint(float rx, float ry, float rz, float sx, float sy, float sz, float[] w, float high,
                                 float[] out) {
        float day = w[0], dusk = w[1], night = w[2], overcast = clamp01(w[3]);
        float toward = (float) Math.pow(towardSun(rx, rz, sx, sz), 1.2);
        float dr = lerp(0.93f, 1f, high), dg = lerp(0.94f, 1f, high), db = lerp(0.97f, 1f, high);
        float kr = lerp(lerp(0.50f, 1.00f, toward), 1.00f, 0.35f * high);
        float kg = lerp(lerp(0.42f, 0.70f, toward), 0.62f, 0.35f * high);
        float kb = lerp(lerp(0.55f, 0.45f, toward), 0.62f, 0.35f * high);
        float nr = lerp(0.26f, 0.30f, high), ng = lerp(0.29f, 0.33f, high), nb = lerp(0.38f, 0.42f, high);
        float r = day * dr + dusk * kr + night * nr;
        float g = day * dg + dusk * kg + night * ng;
        float b = day * db + dusk * kb + night * nb;
        float grey = (0.30f * r + 0.59f * g + 0.11f * b) * (1f - 0.42f * overcast);
        float wash = 0.6f * overcast;
        out[0] = clamp01(lerp(r * (1f - 0.42f * overcast), grey, wash));
        out[1] = clamp01(lerp(g * (1f - 0.42f * overcast), grey, wash));
        out[2] = clamp01(lerp(b * (1f - 0.42f * overcast), grey * 1.04f, wash));
    }

    /**
     * How lit a point of a real cloud slice is, 0.15 to 1: brighter higher up in the stack (light comes from above, the
     * mass shades what is under it) and brighter on the side facing the light, where the density a few blocks toward
     * the sun (or moon), {@code towardLight}, is lower than {@code here}; darker where more cloud lies between it and
     * the light. {@code heightFrac} is the slice's height over the room above the plane, 0..1.
     */
    public static float cloudLight(float here, float towardLight, float heightFrac) {
        float side = Math.max(-1f, Math.min(1f, (here - towardLight) * 2.5f));
        return Math.max(0.15f, Math.min(1f, 0.45f + 0.30f * clamp01(heightFrac) + 0.35f * side));
    }

    /**
     * A real cloud slice's colour for its {@link #cloudLight} {@code light} and the weights, into {@code out} (rgb):
     * by day white tops over blue-grey shadowed bases, at dusk sunlit amber over violet shade, at night moonlit
     * grey-blue over near-black; rain flattens and darkens it.
     */
    public static void volumeCloudColour(float light, float[] w, float[] out) {
        float day = w[0], dusk = w[1], night = w[2], overcast = clamp01(w[3]);
        float l = clamp01(light);
        float r = day * lerp(0.60f, 1.00f, l) + dusk * lerp(0.36f, 1.00f, l) + night * lerp(0.08f, 0.34f, l);
        float g = day * lerp(0.64f, 1.00f, l) + dusk * lerp(0.30f, 0.74f, l) + night * lerp(0.10f, 0.38f, l);
        float b = day * lerp(0.72f, 1.00f, l) + dusk * lerp(0.42f, 0.52f, l) + night * lerp(0.16f, 0.48f, l);
        float grey = 0.30f * r + 0.59f * g + 0.11f * b;
        float dark = 1f - 0.45f * overcast;
        out[0] = clamp01(lerp(r, grey, 0.6f * overcast) * dark);
        out[1] = clamp01(lerp(g, grey, 0.6f * overcast) * dark);
        out[2] = clamp01(lerp(b, grey * 1.04f, 0.6f * overcast) * dark);
    }

    /** The sun's colour at elevation {@code sinElev} (the sine), into {@code out}: white-gold high, deep orange low. */
    public static void sunColour(float sinElev, float[] out) {
        float k = smooth(0f, 0.4f, sinElev);
        out[0] = 1f;
        out[1] = lerp(0.55f, 0.96f, k);
        out[2] = lerp(0.25f, 0.86f, k);
    }

    /**
     * Star {@code i}'s colour, a temperature from bluish white through white and gold to orange, into {@code out}:
     * most stars near white, a tail of strongly coloured ones either way, from the sky's seed. Peak channel 1.
     */
    public static void starColour(long seed, int i, float[] out) {
        float r = unit(mix(seed ^ (i * 0xD6E8FEB86659FD93L + 0x1234567L)));
        float s = 2f * r - 1f;
        float t = 0.5f + 0.5f * Math.signum(s) * (float) Math.pow(Math.abs(s), 2.2);   // 0 blue .. 0.5 white .. 1 orange
        if (t < 0.5f) {
            float k = t / 0.5f;
            out[0] = lerp(0.66f, 1f, k);
            out[1] = lerp(0.78f, 1f, k);
            out[2] = 1f;
        } else {
            float k = (t - 0.5f) / 0.5f;
            out[0] = 1f;
            out[1] = lerp(1f, 0.70f, k);
            out[2] = lerp(1f, 0.46f, k);
        }
    }

    /**
     * How much of a star of {@code size} (0..1, its magnitude) shows when the sky lets {@code strength} (0..1) of the
     * stars through: at dusk only the brightest come out, the faint ones join as it darkens; all of them at 1.
     */
    public static float starVisibility(float strength, float size) {
        return smooth(0f, 1f, strength * 1.6f - (1f - size) * 0.6f);
    }

    /**
     * A gentle twinkle for the real sky, 0.55 to 1: {@link #twinkle} squeezed, flickering a little more for stars low
     * over the walls (light through more air scintillates), {@code sinElev} the sine of the star's elevation.
     */
    public static float realTwinkle(long seed, float t, int starIndex, float sinElev) {
        float tw = twinkle(seed, t, starIndex);                       // 0.35..1
        float depth = 0.25f + 0.20f * (1f - clamp01(sinElev));
        return 1f - depth * (1f - tw) / 0.65f;
    }

    /**
     * The real sky's star layer repeats every {@code FIELD_ALONG} blocks along the Milky Way and every
     * {@code FIELD_ACROSS} across it (blocks of the star layer, {@link #STAR_LAYER_H} up): one band in view at a time.
     */
    public static final float FIELD_ALONG = 1280f, FIELD_ACROSS = 2048f;
    /** The band's half-width across, in star-layer blocks (about 8 blocks on the plane from the hall floor). */
    public static final float FIELD_BAND_WIDTH = 150f;
    /**
     * Stars per square block of the star layer: {@link #STAR_DENSITY} per square block of plane as seen from
     * {@link #PARALLAX_REF_DY} below, as the flat sky had. Nearer the plane more of the layer is in view, so more stars
     * show on it, as they should (the same stars per patch of sky).
     */
    public static final float FIELD_DENSITY = STAR_DENSITY
            / (parallaxScale(PARALLAX_REF_DY, STAR_LAYER_H) * parallaxScale(PARALLAX_REF_DY, STAR_LAYER_H));

    /** How many stars the real sky's repeating star tile holds. */
    public static int fieldCount() {
        return Math.round(FIELD_DENSITY * FIELD_ALONG * FIELD_ACROSS);
    }

    /**
     * The real sky's star tile, flat {a, b, size} per star ({@link #fieldCount} of them): {@code a} in
     * [0, FIELD_ALONG) along the Milky Way, {@code b} in [-FIELD_ACROSS / 2, FIELD_ACROSS / 2) across it with the band's
     * spine on {@code b = 0}, sizes as {@link #stars} makes them. About a third crowd along the band.
     */
    public static float[] starField(long seed) {
        int n = fieldCount();
        float[] out = new float[n * 3];
        long s = seed * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L;
        for (int i = 0; i < n; i++) {
            s = mix(s + i);
            float a = unit(s) * FIELD_ALONG;
            s = mix(s);
            float b = (unit(s) - 0.5f) * FIELD_ACROSS;
            s = mix(s);
            if (unit(s) < BAND_SHARE) {
                s = mix(s);
                b = (unit(s) + unit(mix(s + 1)) + unit(mix(s + 2)) - 1.5f) * FIELD_BAND_WIDTH * 1.2f;
            }
            s = mix(s);
            float r = unit(s);
            out[i * 3] = a;
            out[i * 3 + 1] = b;
            out[i * 3 + 2] = Math.min(1f, 0.12f + 0.88f * r * r * r * r);
        }
        return out;
    }

    /** The Milky Way's glow at {@code b} across it (any value; the tile repeats): 1 on the spine, 0 well off it. */
    public static float fieldBandGlow(float b) {
        float d = b - FIELD_ACROSS * (float) Math.floor(b / FIELD_ACROSS + 0.5f);
        return (float) Math.exp(-(d * d) / (FIELD_BAND_WIDTH * FIELD_BAND_WIDTH));
    }

    /** How far the band's spine sits off the sky's centre, across it, in star-layer blocks: a seeded few dozen. */
    public static float fieldBandOffset(long seed) {
        return (unit(mix(seed ^ 0x2F7A3C91B5D4E86BL)) - 0.5f) * 300f;
    }

    /**
     * The real sky's cloud slices: the heights above the plane of the slices that fit under a roof
     * {@code hy} above it (none under {@link #VOLUME_MIN_HY}), evenly from {@link #VOLUME_LOW} up to 70% of the room or
     * a block under the roof, whichever is lower; the roof slopes down to the walls, so high slices would hit it.
     */
    public static float[] volumeHeights(float hy) {
        if (hy < VOLUME_MIN_HY) return new float[0];
        float top = Math.min(0.7f * hy, hy - 1f);
        int n = Math.max(1, Math.min(VOLUME_SLICES, (int) (hy / 3f)));
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = n == 1 ? VOLUME_LOW : VOLUME_LOW + (top - VOLUME_LOW) * i / (n - 1);
        return out;
    }

    /**
     * A real cloud slice's opacity where the {@link #density} is {@code density}, {@code heightFrac} (0..1) of the way
     * up the room: fair-weather masses with clear sky between them (a gap wherever the density is below about a half),
     * a little thinner higher up; rain ({@code overcast}) closes the gaps and thickens them. 0..0.85.
     */
    public static float volumeAlpha(float density, float heightFrac, float overcast) {
        float o = clamp01(overcast);
        float base = Math.min(0.85f, 0.5f * (1f - 0.35f * clamp01(heightFrac)) * (1f + 0.4f * o));
        float lo = 0.52f - 0.14f * o;
        return base * smooth(lo, lo + 0.22f, density);
    }

    /** Real cloud slices: at most this many, from this height, and only under a roof at least this far up. */
    public static final int VOLUME_SLICES = 5;
    public static final float VOLUME_LOW = 1.5f, VOLUME_MIN_HY = 3f;

    /**
     * How much of a real cloud slice {@code h} above the plane shows at (u, v) (each -1..1) under a roof {@code hy}
     * above the plane that slopes down to the walls across the shorter side (a hall's ridge runs along its length):
     * the room at a point across is taken as {@code 1.5 hy (1 - |across|)}, capped at {@code hy}, and the slice thins
     * out before it gets there; along the length it only softens near the gable ends. 0..1.
     */
    public static float volumeFade(float h, float hy, float u, float v, float hx, float hz) {
        if (hy <= 0f) return 0f;
        float across = hx <= hz ? Math.abs(u) : Math.abs(v);
        float along = hx <= hz ? Math.abs(v) : Math.abs(u);
        float reach = 1f - h / (1.5f * hy);                      // |across| where the roof comes down to h
        if (reach <= 0.05f) return 0f;
        float f = 1f - smooth(reach - Math.min(0.3f, reach), reach, across);
        return f * (1f - smooth(0.8f, 1f, along));
    }

    /** SplitMix64 finaliser. */
    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, 1) from the top 24 bits. */
    private static float unit(long h) {
        return (h >>> 40) / (float) (1 << 24);
    }
}
