package com.hogwartsmagic.pensieve.scene;

import java.util.List;

/**
 * The {@code rough_sea} effect's surface: a heaving storm sea as a pure function of position, time and a seed, so the
 * client's wave mesh and the rowing boat that rides it (see {@link Rowboat}) agree exactly, and every replay and every
 * viewer sees the same sea. Pure: no Minecraft or client types.
 *
 * <p>The height is a sum of four directional swells (wavelengths 26, 17, 11 and 7 blocks, amplitudes 0.26, 0.17, 0.11
 * and 0.06 at intensity 1: at most {@link #MAX_AMPLITUDE} = 0.6 blocks from the still surface) that travel on deep-water
 * dispersion slowed to 0.8 (the 26-block swell rolls past in about 5 s). Each swell has sharpened crests and flat
 * troughs, {@code (1 + sin)^2 / 2 - 1}, the shape of a real storm wave. The longest swell is modulated in slow groups
 * (every few waves a bigger set). Intensity multiplies the whole height linearly. Directions fan out round a wind
 * direction picked from the seed.
 *
 * <p>Finer ripples ({@link #detailNormal}) only shade the surface (and place the foam): they never move it, so the
 * boat, which can follow only waves longer than itself, stays on the drawn water (within 0.15 blocks at intensity 1,
 * {@code RowboatTest.boatRidesTheSurface}).
 *
 * <p>Sine and cosine come from {@link #sin} / {@link #cos}, a 4096-step interpolated table (error under 1e-6): fast
 * enough for a 25,000-vertex mesh every frame, and the same numbers on every machine.
 */
public final class RoughSea {
    private RoughSea() {
    }

    /** The intensity range ({@code intensity} on the event; 1 when absent). */
    public static final float MIN_INTENSITY = 0.1f, MAX_INTENSITY = 2f;
    /** The roughest sea a memory_rowboat may ride (the validator rejects more): above it the hull cannot follow. */
    public static final float MAX_BOAT_INTENSITY = 1f;
    /** The area half-size range along x and z ({@code spread[0]} and {@code spread[2]}), blocks. */
    public static final float MIN_SPREAD = 2f, MAX_SPREAD = 100f;
    /** A sea starting after the scene's first tick, or ending at its {@code duration}, eases in or out over this long. */
    public static final int RAMP_TICKS = 40;

    /** Gravity in blocks per tick squared (9.8 m/s^2, a block being a metre), and the slowing of the waves. */
    private static final double G = 9.8 / 400.0, SPEED = 0.8;
    private static final double[] AMP = {0.26, 0.17, 0.11, 0.06};
    private static final double[] LENGTH = {26.0, 17.0, 11.0, 7.0};
    /** Each swell's direction relative to the wind, radians. */
    private static final double[] ANGLE = {0.0, 0.42, -0.6, 1.05};
    /** The largest |height| at intensity 1: the swell amplitudes summed. */
    public static final double MAX_AMPLITUDE = 0.6;
    /** The wave-group modulation of the longest swell: its amplitude swings between 1 - 2 GROUP_DEPTH and 1. */
    private static final double GROUP_DEPTH = 0.12, GROUP_K = 0.13, GROUP_W = 0.07;

    /** The detail ripples' wavelengths and slope strengths (shading only). */
    private static final double[] DETAIL_LENGTH = {4.3, 3.1, 2.2, 1.45};
    private static final double[] DETAIL_SLOPE = {0.10, 0.09, 0.075, 0.06};
    private static final double[] DETAIL_ANGLE = {0.3, -1.2, 2.1, -2.6};

    // ---- The fast sine --------------------------------------------------------------------------------------------

    private static final int TABLE = 4096;
    private static final double[] SIN = new double[TABLE + 1];

    static {
        for (int i = 0; i <= TABLE; i++) SIN[i] = Math.sin(i * (2 * Math.PI / TABLE));
    }

    /** sin(a), from the table with linear interpolation. */
    public static double sin(double a) {
        double f = a * (TABLE / (2 * Math.PI));
        double fl = Math.floor(f);
        int i = (int) (((long) fl) & (TABLE - 1));
        double r = f - fl;
        return SIN[i] + (SIN[i + 1] - SIN[i]) * r;
    }

    public static double cos(double a) {
        return sin(a + Math.PI / 2);
    }

    // ---- The waves of one seed ------------------------------------------------------------------------------------

    /**
     * One seed's wave set: per swell k, omega, direction and phase, and the same for the detail ripples. A few recent
     * seeds are cached (several seas in one scene alternate without rebuilding).
     */
    private record Waves(long seed, double[] k, double[] w, double[] dx, double[] dz, double[] phase,
                         double[] dk, double[] dw, double[] ddx, double[] ddz, double[] dphase, double windX,
                         double windZ) {
    }

    private static final int CACHE = 4;
    private static final Waves[] CACHED = new Waves[CACHE];
    private static int cacheNext;

    private static Waves waves(long seed) {
        Waves[] cache = CACHED;
        for (int i = 0; i < CACHE; i++) {
            Waves c = cache[i];
            if (c != null && c.seed == seed) return c;
        }
        Waves c = build(seed);
        synchronized (CACHED) {
            cache[cacheNext] = c;
            cacheNext = (cacheNext + 1) % CACHE;
        }
        return c;
    }

    private static Waves build(long seed) {
        Waves c;
        long s = mix(seed);
        double wind = unit(s) * 2 * Math.PI;
        int n = AMP.length;
        double[] k = new double[n], w = new double[n], dx = new double[n], dz = new double[n], ph = new double[n];
        for (int i = 0; i < n; i++) {
            s = mix(s + i + 1);
            k[i] = 2 * Math.PI / LENGTH[i];
            w[i] = Math.sqrt(G * k[i]) * SPEED;
            dx[i] = Math.cos(wind + ANGLE[i]);
            dz[i] = Math.sin(wind + ANGLE[i]);
            ph[i] = unit(s) * 2 * Math.PI;
        }
        int m = DETAIL_LENGTH.length;
        double[] dk = new double[m], dw = new double[m], ddx = new double[m], ddz = new double[m], dph = new double[m];
        for (int i = 0; i < m; i++) {
            s = mix(s + 101 + i);
            dk[i] = 2 * Math.PI / DETAIL_LENGTH[i];
            dw[i] = Math.sqrt(G * dk[i]) * SPEED;
            ddx[i] = Math.cos(wind + DETAIL_ANGLE[i]);
            ddz[i] = Math.sin(wind + DETAIL_ANGLE[i]);
            dph[i] = unit(s) * 2 * Math.PI;
        }
        c = new Waves(seed, k, w, dx, dz, ph, dk, dw, ddx, ddz, dph, Math.cos(wind), Math.sin(wind));
        return c;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static double unit(long s) {
        return (s >>> 11) * 0x1.0p-53;
    }

    /** The wind direction of this seed's sea as a unit {x, z}: the main swell travels this way. */
    public static double[] wind(long seed) {
        Waves v = waves(seed);
        return new double[]{v.windX, v.windZ};
    }

    /** The wind's x component (see {@link #wind}), without allocating. */
    public static double windX(long seed) {
        return waves(seed).windX;
    }

    /** The wind's z component (see {@link #wind}), without allocating. */
    public static double windZ(long seed) {
        return waves(seed).windZ;
    }

    // ---- Height, normal, foam -------------------------------------------------------------------------------------

    /** The surface height offset at intensity 1, blocks, at sea-local (x, z) and time t (ticks). */
    public static double height(double x, double z, double t, long seed) {
        return eval(x, z, t, seed, null);
    }

    /** The surface height offset at {@code intensity} (a plain multiplier on the intensity-1 sea). */
    public static double height(double x, double z, double t, long seed, double intensity) {
        return intensity * eval(x, z, t, seed, null);
    }

    /** The unit surface normal {x, y, z} at intensity 1 (the swells only; see {@link #detailNormal} for shading). */
    public static double[] normal(double x, double z, double t, long seed) {
        return normal(x, z, t, seed, 1.0);
    }

    public static double[] normal(double x, double z, double t, long seed, double intensity) {
        double[] g = new double[2];
        eval(x, z, t, seed, g);
        return unitNormal(-g[0] * intensity, -g[1] * intensity);
    }

    /**
     * Height and gradient together, for the mesh: returns the height at intensity 1 and, when {@code grad} is given,
     * writes {dh/dx, dh/dz} into it.
     */
    public static double eval(double x, double z, double t, long seed, double[] grad) {
        Waves v = waves(seed);
        double h = 0, gx = 0, gz = 0;
        for (int i = 0; i < AMP.length; i++) {
            double along = v.dx[i] * x + v.dz[i] * z;
            double th = v.k[i] * along - v.w[i] * t + v.phase[i];
            double s = sin(th), c = cos(th);
            double q = 1 + s;
            double shape = q * q * 0.5 - 1;           // sharp crests, flat troughs, in [-1, 1]
            double dShape = q * c;                     // d shape / d theta
            double a = AMP[i];
            double mod = 1, dMod = 0;
            if (i == 0) {
                double gth = GROUP_K * v.k[0] * along - GROUP_W * v.w[0] * t + v.phase[1];
                mod = 1 - GROUP_DEPTH * (1 - sin(gth));
                dMod = GROUP_DEPTH * cos(gth) * GROUP_K * v.k[0];
            }
            h += a * mod * shape;
            if (grad != null) {
                double d = a * (mod * dShape * v.k[i] + dMod * shape);   // d h / d along
                gx += d * v.dx[i];
                gz += d * v.dz[i];
            }
        }
        if (grad != null) {
            grad[0] = gx;
            grad[1] = gz;
        }
        return h;
    }

    /**
     * The shading normal: the swells' gradient plus fine ripples (wavelengths 1.5 to 4.3 blocks) that never move the
     * surface. {@code detail} scales the ripples (1 near the camera, fading out with distance so they do not alias).
     * The ripples travel with the wind at their own dispersion speed. Unit {x, y, z}.
     */
    public static double[] detailNormal(double x, double z, double t, long seed, double intensity, double detail) {
        double[] g = new double[2];
        eval(x, z, t, seed, g);
        g[0] *= intensity;
        g[1] *= intensity;
        addDetail(x, z, t, seed, intensity, detail, g);
        return unitNormal(-g[0], -g[1]);
    }

    /** Adds the detail ripples' slope (see {@link #detailNormal}) to {@code grad} {dh/dx, dh/dz}. */
    public static void addDetail(double x, double z, double t, long seed, double intensity, double detail, double[] grad) {
        if (detail <= 0) return;
        Waves v = waves(seed);
        double strength = detail * (0.55 + 0.45 * Math.min(intensity, 2.0));
        for (int i = 0; i < v.dk.length; i++) {
            double th = v.dk[i] * (v.ddx[i] * x + v.ddz[i] * z) - v.dw[i] * t + v.dphase[i];
            double c = cos(th) * DETAIL_SLOPE[i] * strength;
            grad[0] += c * v.ddx[i];
            grad[1] += c * v.ddz[i];
        }
    }

    /**
     * How white the water is here, 0 to 1: foam gathers on the crests of the swells (the top fifth of the height) and
     * where the surface is steep, streaked by the detail ripples, more of it the rougher the sea.
     */
    public static double foam(double x, double z, double t, long seed, double intensity) {
        double[] g = new double[2];
        double h = eval(x, z, t, seed, g);
        return foamFrom(h, g[0], g[1], x, z, t, seed, intensity);
    }

    /** {@link #foam} from the intensity-1 height and gradient already evaluated at (x, z, t). */
    public static double foamFrom(double h, double gx, double gz, double x, double z, double t, long seed, double intensity) {
        double crest = smooth((h / MAX_AMPLITUDE - 0.32) / 0.5);
        double steep = smooth((Math.sqrt(gx * gx + gz * gz) - 0.16) / 0.14);
        Waves v = waves(seed);
        double streak = 0.5 + 0.5 * sin(v.dk[1] * 0.7 * (v.ddx[1] * x + v.ddz[1] * z) + v.dphase[2] - 0.05 * t)
                * cos(v.dk[0] * 0.9 * (v.ddx[0] * z - v.ddz[0] * x) + v.dphase[3]);
        double f = Math.max(crest, steep * 0.7) * (0.55 + 0.45 * streak);
        return Math.min(1.0, f * Math.min(1.0, 0.35 + 0.65 * intensity));
    }

    private static double smooth(double u) {
        if (u <= 0) return 0;
        if (u >= 1) return 1;
        return u * u * (3 - 2 * u);
    }

    private static double[] unitNormal(double nx, double nz) {
        double len = Math.sqrt(nx * nx + 1 + nz * nz);
        return new double[]{nx / len, 1 / len, nz / len};
    }

    // ---- A sea placed in a scene ----------------------------------------------------------------------------------

    /**
     * One {@code rough_sea} event: the still-water centre (scene-relative), the half-size along x and z, the intensity,
     * the start time and length (ticks; 0 = until the scene ends) and the seed ({@link EffectSeed} from the
     * scene-relative {@code at} and {@code t}, so every replay in every session slot is identical).
     */
    public record Sea(double cx, double cy, double cz, double hx, double hz, double intensity, float t0, int duration,
                      long seed) {
        /** From the event's raw values; {@code intensity} NaN means 1. */
        public static Sea of(float[] at, float[] spread, float intensity, float t, int duration) {
            double hx = clamp(spread != null && spread.length > 0 ? spread[0] : MIN_SPREAD, MIN_SPREAD, MAX_SPREAD);
            double hz = clamp(spread != null && spread.length > 2 ? spread[2] : MIN_SPREAD, MIN_SPREAD, MAX_SPREAD);
            double in = Float.isNaN(intensity) ? 1.0 : clamp(intensity, MIN_INTENSITY, MAX_INTENSITY);
            return new Sea(at[0], at[1], at[2], hx, hz, in, t, Math.max(0, duration),
                    EffectSeed.of(at[0], at[1], at[2], 0, 0, 0, t));
        }

        public static Sea of(SceneDef.EventDef e) {
            return of(e.at(), e.spread(), e.intensity(), e.t(), e.duration());
        }

        /** Running at scene time t: from t0 until t0 + duration (or for good). */
        public boolean activeAt(float t) {
            return t >= t0 && (duration <= 0 || t < t0 + duration);
        }

        /** Whether the scene-relative point (x, z) lies over this sea. */
        public boolean covers(double x, double z) {
            return Math.abs(x - cx) <= hx && Math.abs(z - cz) <= hz;
        }

        /**
         * The intensity in force at t: the event's, eased in over {@link #RAMP_TICKS} when the sea starts after the
         * scene's start, and out over the last {@link #RAMP_TICKS} of a finite duration; 0 when not running.
         */
        public double intensityAt(float t) {
            if (!activeAt(t)) return 0;
            double k = 1;
            if (t0 > 0) k = Math.min(k, smooth((t - t0) / RAMP_TICKS));
            if (duration > 0) k = Math.min(k, smooth((t0 + duration - t) / RAMP_TICKS));
            return intensity * k;
        }

        /** The surface height at the scene-relative point (x, z) at scene time t: absolute, the still surface plus waves. */
        public double surface(double x, double z, float t) {
            return cy + intensityAt(t) * eval(x - cx, z - cz, t - t0, seed, null);
        }

        /** The waves' own clock at scene time t (ticks since the sea began). */
        public double clock(float t) {
            return t - t0;
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /**
     * The sea a boat at the scene-relative point (x, z) rides at scene time t: of the {@code rough_sea} events running
     * then whose area contains the point, the one whose centre is nearest; null when there is none (still water).
     */
    public static Sea bind(List<SceneDef.EventDef> events, float t, double x, double z) {
        Sea best = null;
        double bestD = Double.MAX_VALUE;
        for (SceneDef.EventDef e : events) {
            if (!"effect".equals(e.kind()) || !"rough_sea".equals(e.id()) || e.at() == null) continue;
            Sea s = Sea.of(e);
            if (!s.activeAt(t) || !s.covers(x, z)) continue;
            double d = (x - s.cx) * (x - s.cx) + (z - s.cz) * (z - s.cz);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }
}
