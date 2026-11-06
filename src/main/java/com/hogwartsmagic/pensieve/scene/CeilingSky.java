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
     * The third style: a window onto the world's own vanilla sky (its colours, sun, moon, stars and clouds, read from
     * the client level when it draws, with a light magical touch). Unlike the other two it needs a clock and weather
     * the scene does not have.
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

    // ------------------------------------------------------------------ the real sky: a window onto vanilla's

    /**
     * Vanilla's sky objects sit this far from the camera ({@code LevelRenderer#renderSky}: the sun's quad at y 100 with
     * half-size 30, the moon's at y -100 with half-size 20, the stars normalised to 100).
     */
    public static final float SKY_DISTANCE = 100f, SUN_HALF = 30f, MOON_HALF = 20f;
    /** The visible body inside those quads: sun.png and each moon_phases cell draw their 8 by 8 middle of 32 by 32. */
    public static final float SUN_BODY = SUN_HALF / 4f, MOON_BODY = MOON_HALF / 4f;
    /** The magic glows round the sun and moon: a soft dot this big (sky units at 100), about 1.6 times the body. */
    public static final float SUN_GLOW_HALF = SUN_BODY * 1.6f, MOON_GLOW_HALF = MOON_BODY * 1.6f;
    /** Vanilla's star field: the seed of {@code LevelRenderer#drawStars} and how many points it tries. */
    public static final long VANILLA_STAR_SEED = 10842L;
    public static final int VANILLA_STAR_TRIES = 1500;
    /** Floats per vanilla star: its four corners (x, y, z each) in the sky's own frame, in vanilla's vertex order. */
    public static final int STAR_STRIDE = 12;
    /** Vanilla's cloud layer: blocks per texel, the texture's size in texels, the drift per tick, the alpha. */
    public static final float CLOUD_TEXEL = 12f, CLOUD_TEXTURE = 256f, CLOUD_DRIFT_PER_TICK = 0.03f,
            CLOUD_ALPHA = 0.8f;
    /** Fast clouds are drawn 0.33 above the dimension's cloud height and reach this far round the camera. */
    public static final float CLOUD_LIFT = 0.33f, CLOUD_REACH = 32f * CLOUD_TEXEL;
    /** Vanilla's sky disc lies this far above the camera; its fog distance is taken on it (FOG_SKY, a cylinder). */
    public static final float SKY_DISC_Y = 16f;

    /**
     * Vanilla's star field, exactly as {@code LevelRenderer#drawStars} builds it: {@link #VANILLA_STAR_TRIES} random
     * points from {@code RandomSource.create(10842)}, those inside the unit ball (and not at its centre) pushed out to
     * {@link #SKY_DISTANCE}, each a small quad turned at a random angle about its direction. {@link #STAR_STRIDE}
     * floats per star: the four corners as vanilla emits them (it accumulates each corner offset into the centre, so a
     * star is a diamond whose last corner is its direction point; that is reproduced too). Sky frame: the frame the
     * sun's quad is drawn in before the day's rotation ({@link #skyToWorld}).
     */
    public static float[] vanillaStars() {
        LegacyLcg random = new LegacyLcg(VANILLA_STAR_SEED);
        float[] buf = new float[VANILLA_STAR_TRIES * STAR_STRIDE];
        int n = 0;
        org.joml.Vector3f off = new org.joml.Vector3f();
        for (int j = 0; j < VANILLA_STAR_TRIES; j++) {
            float f1 = random.nextFloat() * 2.0F - 1.0F;
            float f2 = random.nextFloat() * 2.0F - 1.0F;
            float f3 = random.nextFloat() * 2.0F - 1.0F;
            float f4 = 0.15F + random.nextFloat() * 0.1F;
            float f5 = f1 * f1 + f2 * f2 + f3 * f3;                      // Mth.lengthSquared
            if (!(f5 <= 0.010000001F) && !(f5 >= 1.0F)) {
                org.joml.Vector3f v = new org.joml.Vector3f(f1, f2, f3).normalize(100.0F);
                float f6 = (float) (random.nextDouble() * (float) Math.PI * 2.0);
                org.joml.Quaternionf q = new org.joml.Quaternionf()
                        .rotateTo(new org.joml.Vector3f(0.0F, 0.0F, -1.0F), v).rotateZ(f6);
                int o = n * STAR_STRIDE;
                v.add(off.set(f4, -f4, 0.0F).rotate(q));
                o = putCorner(buf, o, v);
                v.add(off.set(f4, f4, 0.0F).rotate(q));
                o = putCorner(buf, o, v);
                v.add(off.set(-f4, f4, 0.0F).rotate(q));
                o = putCorner(buf, o, v);
                v.add(off.set(-f4, -f4, 0.0F).rotate(q));
                putCorner(buf, o, v);
                n++;
            }
        }
        return java.util.Arrays.copyOf(buf, n * STAR_STRIDE);
    }

    private static int putCorner(float[] buf, int o, org.joml.Vector3f v) {
        buf[o] = v.x;
        buf[o + 1] = v.y;
        buf[o + 2] = v.z;
        return o + 3;
    }

    /** Vanilla's {@code LegacyRandomSource}, bit for bit (a 48-bit LCG), for the star field only. */
    static final class LegacyLcg {
        private static final long MASK = (1L << 48) - 1L;
        private long seed;

        LegacyLcg(long seed) {
            this.seed = (seed ^ 25214903917L) & MASK;
        }

        int next(int bits) {
            seed = seed * 25214903917L + 11L & MASK;
            return (int) (seed >> 48 - bits);
        }

        float nextFloat() {
            return (float) next(24) * 5.9604645E-8F;
        }

        double nextDouble() {
            int i = next(26);
            int j = next(27);
            long k = ((long) i << 27) + (long) j;
            return (double) k * (double) 1.110223E-16F;
        }
    }

    /**
     * A point of the sky's own frame turned into the world for vanilla's sun angle {@code a}
     * ({@code Level#getSunAngle}, radians), into {@code out} ({x, y, z}) at {@code at}: vanilla's
     * {@code YP(-90) * XP(timeOfDay * 360)}. The sun's quad ({@code y = +100}) is overhead at noon, rising in the east
     * (+x) and setting in the west; the moon's ({@code y = -100}) opposite; the stars turn with them.
     */
    public static void skyToWorld(float cosA, float sinA, float x, float y, float z, float[] out, int at) {
        out[at] = -(y * sinA + z * cosA);
        out[at + 1] = y * cosA - z * sinA;
        out[at + 2] = x;
    }

    /**
     * Where a ray from the camera along {@code (dx, dy, dz)} (any length) crosses a horizontal plane {@code ay} above the
     * camera, into {@code out} ({x, z}, camera-relative): false (out untouched) when the plane is not above the camera
     * or the ray does not rise steeply enough to reach it within reason ({@code dy} at most a thousandth of its length).
     */
    public static boolean rayToPlane(float dx, float dy, float dz, float ay, float[] out) {
        if (!(ay > 0f)) return false;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(dy > 1e-3f * len)) return false;
        float t = ay / dy;
        out[0] = dx * t;
        out[1] = dz * t;
        return true;
    }

    /**
     * The fog vanilla puts on its sky disc along a unit ray of rise {@code v} and horizontal length {@code h}: the disc
     * is {@link #SKY_DISC_Y} up, fogged by distance as a cylinder ({@code max(horizontal, vertical)}) from 0 out to the
     * sky fog's end ({@code fogEnd}, the render distance), with the shader's smoothstep. 0 overhead-ish, 1 at the horizon.
     */
    public static float skyFog(float v, float h, float fogEnd) {
        if (!(fogEnd > 0f)) return 0f;
        if (!(v > 1e-4f)) return 1f;
        float dist = Math.max(SKY_DISC_Y * h / v, SKY_DISC_Y);
        if (dist >= fogEnd) return 1f;
        return smooth(0f, fogEnd, dist);
    }

    /** The sky fog's end the window uses when the render distance it is given is not a finite positive number. */
    public static final float SKY_FOG_END_FALLBACK = 192f;

    /**
     * The end of vanilla's sky fog ({@code FOG_SKY} ends at the render distance, in blocks) for a render distance of
     * {@code renderDistance} blocks: that, or {@link #SKY_FOG_END_FALLBACK} when it is NaN, infinite or not positive.
     * Never clamped to 0..1: it is a distance.
     */
    public static float skyFogEnd(float renderDistance) {
        return Float.isFinite(renderDistance) && renderDistance > 0f ? renderDistance : SKY_FOG_END_FALLBACK;
    }

    /** Fancy clouds' undersides, which is what the ceiling sees from below: vanilla shades them 0.7 of the colour. */
    public static final float CLOUD_UNDERSIDE_FANCY = 0.7f;

    /** How vanilla shades a cloud layer seen from below: {@link #CLOUD_UNDERSIDE_FANCY} with Fancy clouds, 1 with Fast. */
    public static float cloudUnderside(boolean fancy) {
        return fancy ? CLOUD_UNDERSIDE_FANCY : 1f;
    }

    /**
     * Vanilla's fog on a point {@code dist} (cylinder distance) away with the terrain fog from {@code start} to
     * {@code end}: 0 inside the start, then the shader's smoothstep, 1 past the end.
     */
    public static float terrainFog(float dist, float start, float end) {
        if (!(dist > start)) return 0f;
        if (!(end > start) || dist >= end) return 1f;
        return smooth(start, end, dist);
    }

    /** Floats in a sunrise fan: the centre and the 17 rim points, x, y, z each. */
    public static final int FAN_FLOATS = 18 * 3;

    /**
     * Vanilla's sunrise / sunset glow ({@code renderSky}'s triangle fan) in world directions, into {@code fan}
     * ({@link #FAN_FLOATS}): a centre on the horizon 100 out toward the sun (east at dawn, west at dusk;
     * {@code evening} is vanilla's {@code sin(sunAngle) >= 0}) and a rim 120 out that rises {@code 40 * alpha} on the
     * sun's side. The centre carries the colour at {@code alpha}, the rim at 0.
     */
    public static void sunriseFan(boolean evening, float alpha, float[] fan) {
        // XP(90) * ZP(f3) * ZP(90) with f3 = 0 in the evening, 180 in the morning: phi = 90 or 270 degrees.
        float sinPhi = evening ? 1f : -1f, cosPhi = 0f;
        fan[0] = -100f * sinPhi;
        fan[1] = 0f;
        fan[2] = 100f * cosPhi;
        for (int j = 0; j <= 16; j++) {
            float th = (float) j * (float) (Math.PI * 2) / 16.0F;
            float s = (float) Math.sin(th), c = (float) Math.cos(th);
            float lx = s * 120f, ly = c * 120f, lz = -c * 40f * alpha;
            float x1 = lx * cosPhi - ly * sinPhi, y1 = lx * sinPhi + ly * cosPhi;
            int o = 3 + j * 3;
            fan[o] = x1;
            fan[o + 1] = -lz;
            fan[o + 2] = y1;
        }
    }

    /**
     * How much of the sunrise colour vanilla blends in along the ray {@code (dx, dy, dz)} from the camera: the fan's
     * alpha where the ray meets it ({@link #sunriseFan}; {@code alpha} at the centre falling linearly to the rim), 0
     * where it misses.
     */
    public static float sunriseAlpha(float[] fan, float alpha, float dx, float dy, float dz) {
        float cx = fan[0], cy = fan[1], cz = fan[2];
        float best = 0f;
        for (int j = 0; j < 16; j++) {
            int a = 3 + j * 3, b = a + 3;
            float e1x = fan[a] - cx, e1y = fan[a + 1] - cy, e1z = fan[a + 2] - cz;
            float e2x = fan[b] - cx, e2y = fan[b + 1] - cy, e2z = fan[b + 2] - cz;
            float px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
            float det = e1x * px + e1y * py + e1z * pz;
            if (Math.abs(det) < 1e-6f) continue;
            float inv = 1f / det;
            float sx = -cx, sy = -cy, sz = -cz;                   // ray origin (the camera) minus the centre
            float u = (sx * px + sy * py + sz * pz) * inv;
            if (u < 0f || u > 1f) continue;
            float qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
            float w = (dx * qx + dy * qy + dz * qz) * inv;
            if (w < 0f || u + w > 1f) continue;
            float t = (e2x * qx + e2y * qy + e2z * qz) * inv;
            if (t <= 0f) continue;
            best = Math.max(best, alpha * (1f - u - w));
        }
        return best;
    }

    /**
     * Vanilla's cloud texture offset for a camera at {@code (camX, camZ)} and the cloud clock {@code cloudTicks}
     * ({@code LevelRenderer.ticks} plus the partial tick), into {@code out}: the texel (0..256, the texture repeats)
     * under the camera, x and z. {@code renderClouds} works it out as {@code (camX + ticks * 0.03) / 12} and
     * {@code camZ / 12 + 0.33}; the texel at a point {@code (rx, rz)} from the camera on the cloud layer is then this
     * plus {@code (rx, rz) / 12}.
     */
    public static void cloudBase(double camX, double camZ, float cloudTicks, float[] out) {
        double drift = (double) (cloudTicks * CLOUD_DRIFT_PER_TICK);
        double u = (camX + drift) / CLOUD_TEXEL;
        double v = camZ / CLOUD_TEXEL + (double) CLOUD_LIFT;
        out[0] = (float) (u - Math.floor(u / CLOUD_TEXTURE) * CLOUD_TEXTURE);
        out[1] = (float) (v - Math.floor(v / CLOUD_TEXTURE) * CLOUD_TEXTURE);
    }

    /**
     * The cloud texel (x, z) seen through plane point {@code (px, pz)} (camera-relative) of a plane {@code dy} above
     * the camera, the cloud layer {@code h} above the plane: the ray reaches the layer {@link #parallaxScale} times as
     * far out, and the texel there is {@link #cloudBase} plus that offset over 12. Not wrapped (the texture repeats
     * every 256), so neighbouring grid vertices never straddle a wrap.
     */
    public static void cloudTexel(float[] base, float px, float pz, float dy, float h, float[] out) {
        float k = parallaxScale(dy, h);
        out[0] = base[0] + px * k / CLOUD_TEXEL;
        out[1] = base[1] + pz * k / CLOUD_TEXEL;
    }

    /**
     * A plane {@code dy} above the camera, a layer {@code h} above that: the camera ray through a plane point reaches
     * the layer {@code (dy + h) / dy} times as far out, horizontally (1 for a layer on the plane). {@code dy} is held
     * to at least {@link #PARALLAX_MIN_DY}, so a camera brushing the plane does not divide by nothing.
     */
    public static float parallaxScale(float dy, float h) {
        float d = Math.max(PARALLAX_MIN_DY, dy);
        return (d + Math.max(0f, h)) / d;
    }

    /** The least camera depth under the plane {@link #parallaxScale} uses. */
    public static final float PARALLAX_MIN_DY = 0.05f;

    /**
     * Where the camera ray through plane point {@code (px, pz)} meets a layer {@code h} above the plane, horizontally,
     * into {@code out} ({x, z}): {@code C + (P - C) * (dy + h) / dy} for a camera at {@code (cx, cz)}, {@code dy}
     * below the plane.
     */
    public static void parallaxPoint(float cx, float cz, float px, float pz, float dy, float h, float[] out) {
        float k = parallaxScale(dy, h);
        out[0] = cx + (px - cx) * k;
        out[1] = cz + (pz - cz) * k;
    }

    /**
     * The sun's direction, a unit vector into {@code out} ({x, y, z}), for vanilla's time of day
     * ({@code Level#getTimeOfDay}: 0 at noon, 0.5 at midnight): straight up at noon, rising in the east (+x) and setting
     * in the west, as vanilla draws it ({@link #skyToWorld} of the sun's quad centre). The moon is the opposite.
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

    /**
     * The magic on vanilla's stars: star {@code i}'s gentle twinkle at time {@code t} ticks, 0.8 to 1.1 (vanilla's own
     * brightness times this).
     */
    public static float starTwinkle(int i, float t) {
        float tw = twinkle(VANILLA_STAR_SEED, t, i);                      // 0.35..1
        return 0.8f + 0.3f * (tw - 0.35f) / 0.65f;
    }

    /** Star {@code i}'s colour into {@code out}: white, but about one in nine faintly gold or faintly blue. */
    public static void starTint(int i, float[] out) {
        int k = (int) ((mix(VANILLA_STAR_SEED ^ (i * 0x9E3779B97F4A7C15L)) >>> 33) % 18);
        if (k == 0) {
            out[0] = 1f;
            out[1] = 0.90f;
            out[2] = 0.74f;
        } else if (k == 1) {
            out[0] = 0.80f;
            out[1] = 0.88f;
            out[2] = 1f;
        } else {
            out[0] = 1f;
            out[1] = 1f;
            out[2] = 1f;
        }
    }

    /** How many sparkle motes drift under a {@code hx} by {@code hz} ceiling: one per 60 square blocks, 4 to 20. */
    public static int moteCount(float hx, float hz) {
        return Math.max(4, Math.min(20, Math.round(4f * hx * hz / 60f)));
    }

    /**
     * Sparkle mote {@code i} at time {@code t} ticks under a {@code ±hx, ±hz} ceiling, into {@code out}: {x, z} from the
     * centre (always inside the rectangle), how far below the plane (0.3 to 1.5 blocks) and its glow (0..1). Each mote
     * lives 13 to 23 s, fading in and out while drifting a block or two, then reappears somewhere else.
     */
    public static void mote(long seed, int i, float t, float hx, float hz, float[] out) {
        long h = mix(seed ^ (i * 0xA0761D6478BD642FL + 0xE7037ED1A0B428DBL));
        float life = 260f + 200f * unit(h);
        h = mix(h);
        float tt = Math.max(0f, t) + life * unit(h);
        int cycle = (int) Math.floor(tt / life);
        float p = tt / life - cycle;
        long c = mix(h ^ (cycle * 0x8EBC6AF09C88C6E3L));
        float x0 = (unit(c) * 1.7f - 0.85f) * hx;
        c = mix(c);
        float z0 = (unit(c) * 1.7f - 0.85f) * hz;
        c = mix(c);
        double a = unit(c) * Math.PI * 2.0;
        c = mix(c);
        float travel = (0.006f + 0.006f * unit(c)) * life * p;
        c = mix(c);
        out[0] = clamp(x0 + (float) Math.cos(a) * travel, hx * 0.95f);
        out[1] = clamp(z0 + (float) Math.sin(a) * travel, hz * 0.95f);
        out[2] = 0.3f + 1.2f * unit(c);
        float s = (float) Math.sin(Math.PI * p);
        c = mix(c);
        out[3] = s * s * (0.5f + 0.5f * unit(c));
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
