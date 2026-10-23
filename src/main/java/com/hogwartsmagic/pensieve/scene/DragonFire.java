package com.hogwartsmagic.pensieve.scene;

import com.hogwartsmagic.entity.HungarianHorntailEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The memory Horntail's fire ({@code breathe}), the pure part: every number the jet is drawn from, as a function of
 * the scene clock and a seed, so every viewer and every replay sees the same fire (mirror: {@code dragon_engine}'s
 * {@code fire_*} functions, checked by {@code DragonFireTest.pythonMirrorAgrees}). The look is drawn by
 * {@code client/pensieve/DragonFireRenderer}.
 *
 * <p><b>The gas.</b> A breathe emits {@link #PUFFS_PER_TICK} puffs of burning gas a tick from her mouth for its
 * {@code duration}. Each puff keeps the mouth and the aim it was born with and flies out along that ray at
 * {@link #SPEED} blocks a tick, so the jet's front grows at that speed ({@link #front}) and a head that sweeps bends the
 * stream like a hose. A puff widens as it flies ({@link #radius}) and cools from white-gold to dark red ({@link #heat});
 * where its ray meets a block it splashes and spreads along the surface ({@link #SPLASH_TICKS}); past
 * {@link #MAX_REACH} it thins out ({@link #FADE_TICKS}). Then it may leave a puff of black smoke ({@link #smokeRate})
 * that rolls up and spreads for {@link #SMOKE_TICKS}. When the breathe ends the last gas still flies on to the
 * target: the jet leaves her mouth and lands.
 *
 * <p><b>Strength</b> ({@link #strength}) swells over {@link #RISE_TICKS} and gutters over the last
 * {@link #GUTTER_TICKS}: the puffs born then are smaller, dimmer and some are skipped (a sputter).
 *
 * <p><b>Scorch</b> ({@link #scorchRadius}): each place the jet hits blackens out to 2.5 blocks over 30 ticks of hits,
 * and keeps glowing embers for {@link #EMBER_TICKS} after the last hit; the soot stays until the scene ends.
 */
public final class DragonFire {
    private DragonFire() {
    }

    /** The {@code breathe} action and its key. */
    public static final String ACTION = "breathe";
    /** How fast the gas (and so the jet's front) travels, blocks a tick. */
    public static final double SPEED = 1.2;
    /** The farthest the fire reaches, blocks. */
    public static final double MAX_REACH = 18;
    /** A breathe's {@code duration} range and default, ticks. */
    public static final int MIN_DURATION = 10, MAX_DURATION = 80, DEFAULT_DURATION = 40;
    /** The swell at the start and the guttering at the end, ticks. */
    public static final double RISE_TICKS = 3, GUTTER_TICKS = 8;
    /** Puffs of gas born a tick. */
    public static final int PUFFS_PER_TICK = 4;
    /** How long a puff splashes along a surface it hit, or thins out past the reach; how long its smoke lives. */
    public static final double SPLASH_TICKS = 10, FADE_TICKS = 4, SMOKE_TICKS = 56;
    /** The widest scorch (blocks), the hit time it takes to get there and the embers' glow after the last hit (ticks). */
    public static final double SCORCH_MAX = 2.5, SCORCH_TICKS = 30, EMBER_TICKS = 60;
    /** Hits within this distance of a scorch spot's centre belong to it (blocks). */
    public static final double SPOT_JOIN = 1.25;
    /** Vanilla particles alive per jet, at most. */
    public static final int MAX_PARTICLES = 300;
    /**
     * The tick in an {@code AIR_BREATH} at which her jaw opens to fire (HungarianHorntailModel: the jaw opens wide at
     * {@link HungarianHorntailEntity#AIR_BREATH_WINDUP}).
     */
    public static final int AIR_BREATH_OPEN = HungarianHorntailEntity.AIR_BREATH_WINDUP;

    // ---- The envelope ---------------------------------------------------------------------------------------------

    /** How far the jet has reached at {@code age} ticks into the breathe: {@link #SPEED} a tick, up to {@link #MAX_REACH}. */
    public static double front(double age) {
        if (age <= 0) return 0;
        return Math.min(MAX_REACH, age * SPEED);
    }

    /** The jet's strength 0..1 at {@code age} ticks: up over {@link #RISE_TICKS}, full, guttering over the last {@link #GUTTER_TICKS}. */
    public static double strength(double age, double duration) {
        if (age < 0 || age > duration) return 0;
        double up = smooth(age / RISE_TICKS);
        double down = smooth((duration - age) / GUTTER_TICKS);
        return Math.min(up, down);
    }

    /** The scorch's radius after {@code hitAge} ticks of fire on it: 0 to {@link #SCORCH_MAX} over {@link #SCORCH_TICKS}, easing out. */
    public static double scorchRadius(double hitAge) {
        double x = clamp01(hitAge / SCORCH_TICKS);
        return SCORCH_MAX * (1 - (1 - x) * (1 - x));
    }

    /**
     * The share of the puffs born at {@code age} that leave smoke: a clean fire at first, dirtier as it runs, and all
     * smoke as it gutters out.
     */
    public static double smokeRate(double age, double duration) {
        double gutter = 1 - smooth((duration - age) / GUTTER_TICKS);
        return Math.min(1, 0.35 + 0.4 * smooth(age / 30.0) + 0.6 * gutter);
    }

    /** The embers' glow on a spot last hit at {@code lastHit}: 1 while it is hit, fading over {@link #EMBER_TICKS}. */
    public static double emberGlow(double t, double lastHit) {
        if (t < lastHit) return 1;
        return 1 - smooth((t - lastHit - 2) / EMBER_TICKS);
    }

    /** A puff's radius (blocks, scale 1) after flying {@code s} blocks. */
    public static double radius(double s) {
        return 0.24 + 0.095 * Math.max(0, s);
    }

    /**
     * How far from the jet's centre line its flame shows, blocks at scale 1, {@code s} blocks out: the billow sprites
     * ({@link #radius} x 2 drawn, their texture fading out at about 0.85 of that) plus the puffs' spread across the jet
     * (up to 0.42 x the radius). For clearance checks: keep anything that must not burn this far plus a margin away.
     */
    public static double visibleRadius(double s) {
        return 2.1 * radius(s);
    }

    /** A puff's heat 0..1 after flying {@code s} blocks (1 white-gold at the mouth, about 0.25 deep red at the reach). */
    public static double heat(double s) {
        return 1 - 0.75 * clamp01(s / MAX_REACH);
    }

    /** Puff {@code k}'s birth tick, relative to the breathe's start. */
    public static double birth(int k) {
        return (double) k / PUFFS_PER_TICK;
    }

    /** How many puffs a breathe of {@code duration} emits. */
    public static int puffs(double duration) {
        return (int) Math.ceil(duration * PUFFS_PER_TICK);
    }

    /** How long a puff burns: its flight to the hit (or the reach), then its splash (or its fade). */
    public static double flameLife(double hit) {
        boolean hits = hit < MAX_REACH;
        return Math.min(hit, MAX_REACH) / SPEED + (hits ? SPLASH_TICKS : FADE_TICKS);
    }

    /** How long after a breathe begins its last puff's smoke is gone: the most any of its fire is drawn. */
    public static double lifetime(double duration) {
        return duration + MAX_REACH / SPEED + SPLASH_TICKS + SMOKE_TICKS;
    }

    // ---- The fire window (mirror: dragon_engine.fire_window) ------------------------------------------------------

    /**
     * The ticks {@code {t0, t1}} in which the {@code BREATH} or {@code AIR_BREATH} cue in force at t has her jaw open
     * at full fire, or null when neither is in force. {@code BREATH}: from the cue plus its fade-in ({@code blend},
     * default 6) to the end of the one-shot or the next cue. {@code AIR_BREATH}: the same, but from at least
     * {@link #AIR_BREATH_OPEN} ticks in (the jaw opens then).
     */
    public static double[] fireWindow(List<SceneDef.Cue> anims, double t) {
        HorntailCues.State s = HorntailCues.at(anims, t);
        boolean air = "AIR_BREATH".equals(s.name());
        if (!air && !"BREATH".equals(s.name())) return null;
        double cueT = t - s.time();
        List<SceneDef.Cue> cues = new ArrayList<>(anims);
        cues.sort(Comparator.comparingDouble(SceneDef.Cue::t));
        SceneDef.Cue cue = null;
        double next = Double.POSITIVE_INFINITY;
        for (SceneDef.Cue c : cues) {
            if (cue == null && Math.abs(c.t() - cueT) < 1e-4 && s.name().equals(c.name().toUpperCase(Locale.ROOT))) {
                cue = c;
            } else if (cue != null && c.t() > cue.t()) {
                next = c.t();
                break;
            }
        }
        if (cue == null) return null;
        double blend = cue.blend() > 0 ? cue.blend() : HorntailCues.DEFAULT_BLEND;
        double open = air ? Math.max(AIR_BREATH_OPEN, blend) : blend;
        return new double[]{cue.t() + open, Math.min(cue.t() + HorntailCues.duration(s.name()), next)};
    }

    // ---- The seed and each puff's look ----------------------------------------------------------------------------

    /**
     * The seed of one breathe: its time, the dragon's scene-relative path position then (EffectSeed, so the session
     * slot's origin never changes the fire) and the actor id, so two dragons breathing at once differ.
     */
    public static long seed(String actorId, float t, float[] rootAtCue) {
        long s = EffectSeed.of(rootAtCue[0], rootAtCue[1], rootAtCue[2], 0, 0, 0, t);
        return mix(s ^ (actorId.hashCode() * 0x9E3779B97F4A7C15L));
    }

    /**
     * A puff's random look, pure of the seed and its index: its offsets across the jet ({@code u}, {@code v}, about
     * -1..1), its sprite and spin, a size jitter, the way it splashes, its smoke's drift, and two numbers used for the
     * sputter and the smoke share.
     */
    public record Look(double u, double v, int sprite, double spin, double spinRate, double size, double splash,
                       double drift, double wobble, double sputter, double smoke) {
    }

    public static Look look(long seed, int k) {
        long h = mix(seed + k * 0xD1B54A32D192ED03L);
        double a = unit(h), b = unit(mix(h + 1)), c = unit(mix(h + 2)), d = unit(mix(h + 3)), e = unit(mix(h + 4));
        double f = unit(mix(h + 5)), g = unit(mix(h + 6)), i = unit(mix(h + 7)), j = unit(mix(h + 8));
        // A disc, not a square: the jet's edge stays round.
        double ang = a * Math.PI * 2, rad = Math.sqrt(b);
        return new Look(Math.cos(ang) * rad, Math.sin(ang) * rad, (int) (c * 4) & 3, d * Math.PI * 2,
                (e - 0.5) * 0.5, 0.8 + 0.4 * f, (g - 0.5) * Math.PI * 1.5, i * Math.PI * 2, j * Math.PI * 2,
                unit(mix(h + 9)), unit(mix(h + 10)));
    }

    /** Whether a puff born at {@code birthAge} is drawn at all: the guttering jet sputters. */
    public static boolean emitted(Look look, double birthAge, double duration) {
        double s = strength(birthAge, duration);
        return s > 0 && look.sputter() < 0.25 + 0.75 * Math.min(1, s * 1.6);
    }

    /** Whether a puff born at {@code birthAge} leaves smoke. */
    public static boolean smokes(Look look, double birthAge, double duration) {
        return look.smoke() < smokeRate(birthAge, duration);
    }

    // ---- Where a puff is born (the renderer's choice, pure so it is the same about any session origin) -------------

    /** Where a puff's mouth came from: the drawn snout between two frames, this frame's drawn snout, or her pose. */
    public static final int FROM_LERP = 0, FROM_NOW = 1, FROM_POSE = 2;

    /** A puff's birth: its mouth and her head's direction there (world), its aim (unit) and where the mouth came from. */
    public record Birth(double[] o, double[] head, double[] d, int from) {
    }

    /**
     * The puff born at scene tick {@code tb}, seen at {@code now}. Its mouth is her drawn snout: this frame's
     * ({@code curO}, {@code curD}; null when she was not drawn this frame) interpolated from the last frame's
     * ({@code lastO}, {@code lastD} at {@code lastT}; null or NaN when none) when tb lies between the two and within 2
     * ticks; else this frame's when within 0.75 ticks; else her pose's estimate ({@code pose}: world
     * {x, y, z, dx, dy, dz}, asked for only then). It aims at {@code target} (world; null for none), else, or when
     * the target is nearer than 0.5, along her head.
     */
    public static Birth birth(double tb, double now, double[] lastO, double[] lastD, double lastT, double[] curO,
                              double[] curD, java.util.function.Supplier<double[]> pose, double[] target) {
        double[] o, hd;
        int from;
        if (curO != null && lastO != null && tb >= lastT - 1e-6 && now > lastT && now - tb <= 2) {
            double f = (tb - lastT) / (now - lastT);
            o = lerp(lastO, curO, f);
            hd = unitOr(lerp(lastD, curD, f));
            from = FROM_LERP;
        } else if (curO != null && now - tb <= 0.75) {
            o = curO.clone();
            hd = curD.clone();
            from = FROM_NOW;
        } else {
            double[] m = pose.get();
            o = new double[]{m[0], m[1], m[2]};
            hd = unitOr(new double[]{m[3], m[4], m[5]});
            from = FROM_POSE;
        }
        double[] d = hd;
        if (target != null) {
            double[] v = {target[0] - o[0], target[1] - o[1], target[2] - o[2]};
            if (Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) >= 0.5) d = unitOr(v);
        }
        return new Birth(o, hd, d, from);
    }

    /** A breathe's point target (scene-relative) in the world of a session slot at {@code origin}. */
    public static double[] pointTarget(float[] point, double[] origin) {
        return new double[]{origin[0] + point[0], origin[1] + point[1], origin[2] + point[2]};
    }

    private static double[] lerp(double[] a, double[] b, double f) {
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    private static double[] unitOr(double[] a) {
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        return l < 1e-9 ? new double[]{0, 0, 1} : new double[]{a[0] / l, a[1] / l, a[2] / l};
    }

    // ---- Where a puff is ------------------------------------------------------------------------------------------

    /** A puff of burning gas now: where (world), its radius, heat and alpha share, and the stage it is in. */
    public record Flame(double x, double y, double z, double radius, double heat, double alpha, int stage) {
        public static final int FLYING = 0, SPLASHING = 1, FADING = 2;
    }

    /**
     * Puff {@code look} born at mouth {@code o} with aim {@code d} (unit), whose ray meets a block {@code hit} blocks
     * out (or {@code Double.POSITIVE_INFINITY}) on a face with normal {@code n}, {@code a} ticks after its birth; null
     * once it no longer burns. {@code scale} is the dragon's.
     */
    public static Flame flame(double[] o, double[] d, double hit, double[] n, Look look, double a, double scale) {
        return flame(o, d, hit, n, look, a, scale, Double.POSITIVE_INFINITY);
    }

    /**
     * {@link #flame} with the splash's spread along the surface held to {@code splashReach} blocks: the free run along
     * the puff's own {@link #splashDir} before a block (the renderer casts one ray a puff at its birth, from
     * {@link #SPLASH_RAY_LIFT} off the face, and passes {@link #splashReach} of it), so a splash never licks through
     * the rock beside or behind the face it hit.
     */
    public static Flame flame(double[] o, double[] d, double hit, double[] n, Look look, double a, double scale,
                              double splashReach) {
        if (a < 0) return null;
        double reach = Math.min(hit, MAX_REACH);
        double aEnd = reach / SPEED;
        double[] p1 = new double[3], p2 = new double[3];
        basis(d, p1, p2);
        if (a <= aEnd) {
            double s = SPEED * a;
            return along(o, d, p1, p2, look, a, s, scale, heat(s), Math.min(1, a * 2 + 0.2), Flame.FLYING);
        }
        double b = a - aEnd;
        if (hit < MAX_REACH) {
            if (b > SPLASH_TICKS) return null;
            // Spread along the surface: mostly forward along the jet's own run over it, eased out, never past a block.
            double[] e = splashDir(d, n, look);
            double spread = Math.min(SPLASH_SPREAD * scale * (1 - Math.exp(-b / 3.5)), Math.max(0, splashReach));
            double lift = (0.25 + 0.06 * b) * scale;
            double r = radius(hit) * scale * look.size() * (1 + 0.09 * b);
            double x = o[0] + d[0] * hit + n[0] * lift + e[0] * spread;
            double y = o[1] + d[1] * hit + n[1] * lift + e[1] * spread + 0.012 * b * b;
            double z = o[2] + d[2] * hit + n[2] * lift + e[2] * spread;
            double heat = heat(hit) * (1 - 0.5 * b / SPLASH_TICKS);
            double alpha = 1 - smooth((b - SPLASH_TICKS * 0.45) / (SPLASH_TICKS * 0.55));
            return new Flame(x, y, z, r, heat, alpha, Flame.SPLASHING);
        }
        if (b > FADE_TICKS) return null;
        double s = reach + SPEED * 0.6 * b;
        return along(o, d, p1, p2, look, a, s, scale, heat(s) * (1 - 0.3 * b / FADE_TICKS), 1 - b / FADE_TICKS,
                Flame.FADING);
    }

    /** How far a splash spreads along the surface at most, blocks at scale 1. */
    public static final double SPLASH_SPREAD = 3.2;
    /** The splash ray starts this far off the hit face (blocks). */
    public static final double SPLASH_RAY_LIFT = 0.3;
    /** A splash stops this far short of the block its ray meets (blocks), so its centre stays off the rock. */
    public static final double SPLASH_WALL_GAP = 0.25;

    /**
     * The unit direction a puff that hit a face of normal {@code n} spreads in along it: the jet's own run over the
     * surface (its aim {@code d} less the normal part), turned within the face by the puff's {@link Look#splash}
     * angle; across the jet's basis when it hits the face head on.
     */
    public static double[] splashDir(double[] d, double[] n, Look look) {
        double dn = d[0] * n[0] + d[1] * n[1] + d[2] * n[2];
        double[] e1 = {d[0] - n[0] * dn, d[1] - n[1] * dn, d[2] - n[2] * dn};
        double l = Math.sqrt(e1[0] * e1[0] + e1[1] * e1[1] + e1[2] * e1[2]);
        if (l < 0.2) {
            double[] p1 = new double[3], p2 = new double[3];
            basis(d, p1, p2);
            e1 = p1;
        } else {
            e1[0] /= l;
            e1[1] /= l;
            e1[2] /= l;
        }
        double[] e2 = cross(n, e1);
        double cs = Math.cos(look.splash()), sn = Math.sin(look.splash());
        return new double[]{e1[0] * cs + e2[0] * sn, e1[1] * cs + e2[1] * sn, e1[2] * cs + e2[2] * sn};
    }

    /**
     * The splash reach to pass {@link #flame}: the free run {@code free} its splash ray found before a block (or
     * +infinity), less {@link #SPLASH_WALL_GAP}, at least 0.
     */
    public static double splashReach(double free) {
        return Math.max(0, free - SPLASH_WALL_GAP);
    }

    private static Flame along(double[] o, double[] d, double[] p1, double[] p2, Look look, double a, double s,
                               double scale, double heat, double alpha, int stage) {
        double r = radius(s) * scale * look.size();
        // Turbulence: the puffs spread across the jet as it widens, and the stream wavers a little.
        double spread = r * 0.42 * (s / (s + 1.5));
        double wob = Math.sin(a * 0.55 + look.wobble()) * 0.05 * s * scale;
        double ou = look.u() * spread + wob, ov = look.v() * spread;
        double rise = 0.0035 * a * a * scale;
        return new Flame(o[0] + d[0] * s + p1[0] * ou + p2[0] * ov,
                o[1] + d[1] * s + p1[1] * ou + p2[1] * ov + rise,
                o[2] + d[2] * s + p1[2] * ou + p2[2] * ov, r, heat, alpha, stage);
    }

    /** A puff of smoke now: where (world), its radius, alpha and how far it has gone from fire-lit to black (0..1). */
    public record Smoke(double x, double y, double z, double radius, double alpha, double soot) {
    }

    /**
     * The smoke left by puff {@code look} (see {@link #flame}), {@code a} ticks after the puff's birth; null before its
     * fire is out or after the smoke is gone. It rises from where the fire ended, spreading and drifting.
     */
    public static Smoke smoke(double[] o, double[] d, double hit, double[] n, Look look, double a, double scale) {
        return smoke(o, d, hit, n, look, a, scale, Double.POSITIVE_INFINITY);
    }

    /** {@link #smoke} rising from where a splash held to {@code splashReach} ended (see {@link #flame}). */
    public static Smoke smoke(double[] o, double[] d, double hit, double[] n, Look look, double a, double scale,
                              double splashReach) {
        double life = flameLife(hit);
        double b = a - life + 3;       // the smoke starts 3 ticks before the fire is out, under it
        if (b < 0 || b > SMOKE_TICKS) return null;
        Flame end = flame(o, d, hit, n, look, life - 1e-6, scale, splashReach);
        if (end == null) return null;
        double up = (0.05 * b + 0.0011 * b * b) * scale;
        double drift = 0.018 * b * scale;
        double x = end.x() + Math.cos(look.drift()) * drift;
        double y = end.y() + up;
        double z = end.z() + Math.sin(look.drift()) * drift;
        double r = (end.radius() * 0.85 + 0.05 * b * scale);
        double alpha = smooth(b / 5.0) * (1 - smooth((b - SMOKE_TICKS * 0.3) / (SMOKE_TICKS * 0.7)));
        return new Smoke(x, y, z, r, alpha, smooth((b - 2) / 12.0));
    }

    // ---- The scorch -----------------------------------------------------------------------------------------------

    /** One scorched place: where the first hit landed, the face normal there and the times the jet hit it. */
    public static final class Spot {
        public final double x, y, z, nx, ny, nz;
        private final List<Double> hits = new ArrayList<>();

        public Spot(double x, double y, double z, double nx, double ny, double nz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.nx = nx;
            this.ny = ny;
            this.nz = nz;
        }

        public void hit(double t) {
            hits.add(t);
        }

        /** {@link #hit}, forgetting the oldest hit first when {@code max} are held (the open world scorch never ends). */
        public void hitCapped(double t, int max) {
            if (hits.size() >= max) hits.remove(0);
            hits.add(t);
        }

        /** How many hits it holds. */
        public int hitCount() {
            return hits.size();
        }

        /** Ticks of fire on it by t (each puff counts {@code 1 / PUFFS_PER_TICK}). */
        public double hitAge(double t) {
            int n = 0;
            for (double h : hits) if (h <= t) n++;
            return (double) n / PUFFS_PER_TICK;
        }

        /** The first hit, or +infinity. */
        public double firstHit() {
            double f = Double.POSITIVE_INFINITY;
            for (double h : hits) f = Math.min(f, h);
            return f;
        }

        /** The last hit at or before t, or -infinity when none. */
        public double lastHit(double t) {
            double l = Double.NEGATIVE_INFINITY;
            for (double h : hits) if (h <= t) l = Math.max(l, h);
            return l;
        }

        public double radius(double t) {
            return scorchRadius(hitAge(t));
        }

        public double distance(double px, double py, double pz) {
            double dx = px - x, dy = py - y, dz = pz - z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    /** Adds a hit at (x, y, z) to the spot it falls in, or a new one (at most {@code max} spots). */
    public static void addHit(List<Spot> spots, double x, double y, double z, double[] n, double t, int max) {
        Spot best = null;
        double bd = SPOT_JOIN;
        for (Spot s : spots) {
            double dd = s.distance(x, y, z);
            if (dd <= bd) {
                bd = dd;
                best = s;
            }
        }
        if (best == null) {
            if (spots.size() >= max) return;
            best = new Spot(x, y, z, n[0], n[1], n[2]);
            spots.add(best);
        }
        best.hit(t);
    }

    // ---- The particle budget --------------------------------------------------------------------------------------

    /**
     * Keeps a jet's vanilla particles under {@link #MAX_PARTICLES} alive: each spawn is counted alive for its type's
     * longest life, and a tick may only spawn what still fits.
     */
    public static final class Budget {
        private final int cap;
        private final List<long[]> spawned = new ArrayList<>();   // {tick, count, life}

        public Budget(int cap) {
            this.cap = cap;
        }

        /** The particles counted alive at {@code tick}. */
        public int alive(long tick) {
            int n = 0;
            for (long[] s : spawned) if (s[0] + s[2] > tick && s[0] <= tick) n += (int) s[1];
            return n;
        }

        /** How many of {@code wanted} particles of the given longest {@code life} may spawn at {@code tick}. */
        public int allow(long tick, int wanted, int life) {
            spawned.removeIf(s -> s[0] + s[2] <= tick);
            int ok = Math.max(0, Math.min(wanted, cap - alive(tick)));
            if (ok > 0) spawned.add(new long[]{tick, ok, life});
            return ok;
        }
    }

    // ---- Small maths ----------------------------------------------------------------------------------------------

    /** Two unit vectors across {@code d}: {@code p1} horizontal (unless d is vertical), {@code p2} = p1 x d. */
    public static void basis(double[] d, double[] p1, double[] p2) {
        double[] a = cross(d, new double[]{0, 1, 0});
        double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        if (l < 1e-4) {
            a = cross(d, new double[]{1, 0, 0});
            l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        }
        p1[0] = a[0] / l;
        p1[1] = a[1] / l;
        p1[2] = a[2] / l;
        double[] b = cross(p1, d);
        p2[0] = b[0];
        p2[1] = b[1];
        p2[2] = b[2];
    }

    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    static double smooth(double x) {
        double c = clamp01(x);
        return c * c * (3 - 2 * c);
    }

    static double clamp01(double x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    public static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }
}
