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

    /** The daytime sky's colour at (u, v) as 0xRRGGBB, before the texture: #3F7FD6 overhead to #A9D2F2 at the walls. */
    public static int daySkyColour(long seed, float u, float v) {
        float d = Math.min(1f, (float) Math.sqrt(u * u + v * v));
        float k = 1f - d * d * (3f - 2f * d);                        // 1 overhead, 0 at the walls
        // The same seeded band the night sky has, as a faint brightening, so two skies are not twins.
        float haze = 1f + 0.03f * (bandGlow(seed, u, v) - 0.5f);
        return rgb(lerp(0xA9, 0x3F, k) * haze, lerp(0xD2, 0x7F, k) * haze, lerp(0xF2, 0xD6, k) * haze);
    }

    /**
     * The sunset (or sunrise) glow at (u, v) as 0xRRGGBB, before the texture: indigo #4B4F8C overhead, the horizon
     * #F2A65A on one side of the hall (u = -1) shading to #D96C6C on the other (u = 1).
     */
    public static int duskTint(float u, float v) {
        float d = Math.min(1f, (float) Math.sqrt(u * u + v * v));
        float k = 1f - d * d * (3f - 2f * d);
        float side = clamp01((u + 1f) * 0.5f);
        float hr = lerp(0xF2, 0xD9, side), hg = lerp(0xA6, 0x6C, side), hb = lerp(0x5A, 0x6C, side);
        return rgb(lerp(hr, 0x4B, k), lerp(hg, 0x4F, k), lerp(hb, 0x8C, k));
    }

    private static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    private static int rgb(float r, float g, float b) {
        return (Math.min(255, Math.round(r)) << 16) | (Math.min(255, Math.round(g)) << 8) | Math.min(255, Math.round(b));
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
