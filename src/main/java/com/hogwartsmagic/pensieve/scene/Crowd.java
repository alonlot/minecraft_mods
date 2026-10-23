package com.hogwartsmagic.pensieve.scene;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Pure maths for the stadium crowd: the {@code crowd} effect fills seat rows with spectators, and each
 * {@code crowd_react} event makes the ones in its box cheer, gasp, erupt, chant or sit. Everything here is a function of
 * the event, its {@link EffectSeed} and the scene time, so every viewer (late joiners included) sees the same people in
 * the same seats doing the same thing. The drawing is {@code client/pensieve/CrowdRenderer} through {@link CrowdMesh};
 * the generators' mirror is {@code scene_engine.crowd_seats} (kept in step by {@code crowd_cases.json}).
 *
 * <p><b>Seats.</b> A row is a seat-top segment {@code [x1, y1, z1, x2, y2, z2]}, scene-relative. A seventh number 1 makes
 * it a standing row: its y is the floor the spectators stand on (a rail or a deck), not a seat top. Seats lie along the
 * row 0.8 to 1.0 apart (each gap its own), the run centred on the row, each nudged up to {@link #SIDE_JITTER} sideways;
 * a row shorter than one gap still seats one spectator at its middle. Each spectator faces the crowd's {@code at} (the
 * pitch centre) give or take {@link #YAW_JITTER} degrees. A seat is filled when its own number is under
 * {@code intensity}; a stander whose body would hide the field from a seated spectator's eye is left out
 * ({@link #blocks}); the {@code count} cap keeps the filled seats with the lowest numbers.
 *
 * <p><b>Reactions.</b> A spectator reacts to a {@code crowd_react} whose box ({@code at ± spread}) holds them, after
 * their own delay of 0 to {@link #MAX_DELAY_TICKS} ticks, longer the further they are from {@code at}. The pose at a
 * time is the latest reaction they have started, blended in from where the one before had them; with none, the idle
 * pose: seated, swaying, now and then a scarf waved overhead. Standing rows stand throughout.
 */
public final class Crowd {
    /** A spectator's seat gap along a row, in blocks: each gap is drawn between these. */
    public static final double MIN_SPACING = 0.8, MAX_SPACING = 1.0;
    /** How far a seat is nudged off the row line, either way, and how far a spectator's facing strays, in degrees. */
    public static final double SIDE_JITTER = 0.06, YAW_JITTER = 6.0;
    /** The most spectators a scene's {@code crowd} events may seat between them. */
    public static final int MAX_SPECTATORS = 4000;
    /** The {@code style} names of a {@code crowd}: whose colours the robes, scarves and flags are. */
    public static final List<String> STYLES = List.of("gryffindor", "hufflepuff", "ravenclaw", "slytherin", "mixed",
            "triwizard");
    /**
     * The {@code triwizard} style (the First Task's stands): the four houses share {@link #TRIWIZARD_HOUSES} of the
     * crowd between them, Beauxbatons (powder blue) {@link #TRIWIZARD_BEAUXBATONS} and Durmstrang (dark oxblood with brown
     * fur) the rest. House ids 4 and 5.
     */
    public static final double TRIWIZARD_HOUSES = 0.70, TRIWIZARD_BEAUXBATONS = 0.15;
    public static final int BEAUXBATONS = 4, DURMSTRANG = 5;
    /** The {@code style} names of a {@code crowd_react}; the index is the reaction kind. */
    public static final List<String> REACTIONS = List.of("cheer", "goal", "gasp", "erupt", "chant", "sit");
    public static final int CHEER = 0, GOAL = 1, GASP = 2, ERUPT = 3, CHANT = 4, SIT = 5;
    /** Default durations (ticks) per reaction kind, when the event gives none; an erupt lasts at least its minimum. */
    static final int[] DEFAULT_TICKS = {50, 60, 20, 120, 80, 0};
    public static final int MIN_ERUPT_TICKS = 60;
    /** A gasp holds the hands at the head this long (1 s), whatever its duration. */
    public static final int GASP_TICKS = 20;
    /** The longest reaction delay (0.6 s), and the distance from {@code at} at which a spectator's delay is longest. */
    public static final float MAX_DELAY_TICKS = 12f, DELAY_RANGE = 80f;
    /** A reaction comes in over RISE ticks (a sit over SIT_RISE) and goes out over FALL ticks after its duration. */
    static final float RISE = 8f, SIT_RISE = 12f, FALL = 12f;
    /** The share of a {@code cheer} who stand (the rest cheer from their seats). */
    static final double CHEER_STANDERS = 0.65;
    /** The chant's arm pump and the erupt's bounce, both 2 Hz (period in ticks), and the bounce height in blocks. */
    static final float PUMP_PERIOD = 10f, BOUNCE_PERIOD = 10f, BOUNCE = 0.22f;
    /** A spectator's size range (students of every year), and the share wearing a scarf, waving a flag or a banner. */
    public static final double MIN_SCALE = 0.82, MAX_SCALE = 1.0, SCARF_SHARE = 0.5, FLAG_SHARE = 0.07, BANNER_SHARE = 0.02;
    /** Seated eye height (seat top to eye) and standing height at scale 1, in blocks; a stander's body radius. */
    public static final double SEATED_EYE = 1.0, STANDER_HEIGHT = 1.8, STANDER_RADIUS = 0.32;
    /** A stander only hides what lies within this many blocks along a seated spectator's sight line. */
    public static final double SIGHT_REACH = 6.0;

    /** Pose channels: written by {@link #pose} at {@code out[off + channel]}. */
    public static final int STAND = 0, JUMP = 1, ARM_L = 2, ARM_R = 3, HANDS_HEAD = 4, LEAN = 5, SWAY = 6, FLAG_UP = 7,
            WAVE = 8, CHANNELS = 9;
    /** Gear bits. */
    public static final int SCARF = 1, FLAG = 2, BANNER = 4;
    /** Outfit kinds: a jumper in the house's first colour, a black school robe, or the house's second colour. */
    public static final int JUMPER = 0, ROBE = 1, SECOND = 2;

    // The random salts, one per draw (the Python mirror uses the same numbers).
    static final int SALT_GAP = 1, SALT_SIDE = 2, SALT_YAW = 3, SALT_FILL = 4, SALT_SCALE = 5, SALT_HOUSE = 6,
            SALT_OUTFIT = 7, SALT_SKIN = 8, SALT_HAIR = 9, SALT_GEAR = 10, SALT_PHASE = 11, SALT_SWAY = 12,
            SALT_WAVE = 13, SALT_DELAY = 14, SALT_CHEER = 15;

    private final int n;
    private final float ax, ay, az;
    private final float[] x, y, z, yaw, scale, phase, swayTicks, waveTicks, delayDraw, cheerDraw;
    private final boolean[] standing;
    private final byte[] house, outfit, skin, hair, gear;
    private final int dropped;
    private SceneDef.EventDef event;
    /** The scene's reactions, and each one's onset per spectator (NaN: not in its box). */
    private Reaction[] reactions = new Reaction[0];
    private float[][] onset = new float[0][];

    private Crowd(int n, float[] at, int dropped) {
        this.n = n;
        this.ax = at[0];
        this.ay = at[1];
        this.az = at[2];
        this.dropped = dropped;
        x = new float[n];
        y = new float[n];
        z = new float[n];
        yaw = new float[n];
        scale = new float[n];
        phase = new float[n];
        swayTicks = new float[n];
        waveTicks = new float[n];
        delayDraw = new float[n];
        cheerDraw = new float[n];
        standing = new boolean[n];
        house = new byte[n];
        outfit = new byte[n];
        skin = new byte[n];
        hair = new byte[n];
        gear = new byte[n];
    }

    // ------------------------------------------------------------------ the random numbers

    /** The splitmix64 finaliser (with its golden-gamma step): one long to a well-mixed long. */
    static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** A number in [0, 1) for seat {@code k} of row {@code row}, one per {@code salt}. */
    static double unit(long seed, int row, int k, int salt) {
        long h = mix(mix(mix(seed + salt) + row) + k);
        return (h >>> 11) * 0x1.0p-53;
    }

    /** The seed of a {@code crowd} event: its scene-relative {@code at} and its time (see {@link EffectSeed}). */
    public static long seed(SceneDef.EventDef e) {
        float[] at = e.at() == null ? new float[3] : e.at();
        return EffectSeed.of(at[0], at[1], at[2], 0, 0, 0, e.t());
    }

    /** The crowd a {@code crowd} event seats (no reactions bound yet). */
    public static Crowd of(SceneDef.EventDef e) {
        float[] at = e.at() == null ? new float[3] : e.at();
        float intensity = Float.isNaN(e.intensity()) ? 1f : e.intensity();
        return layout(e.rows() == null ? new float[0][] : e.rows(), at, e.style(), intensity, e.count(), seed(e));
    }

    // ------------------------------------------------------------------ the seats

    /**
     * Seat a crowd. {@code rows}: seat-top segments (a seventh number 1: a standing row on its floor); {@code at}: what
     * they face; {@code style}: one of {@link #STYLES} (null or unknown: mixed); {@code intensity}: the share of seats
     * filled; {@code count}: a cap (0 or less: {@link #MAX_SPECTATORS}).
     */
    public static Crowd layout(float[][] rows, float[] at, String style, double intensity, int count, long seed) {
        Draft d = draft(rows, at, style, intensity, count, seed);
        return finish(d, List.of(d));
    }

    /**
     * Every {@code crowd} event of a scene seated together, in event order: as {@link #of} each, except that a stander is
     * tested against the seated spectators of <em>every</em> crowd event (each looking at its own {@code at}), so rails
     * kept in an event of their own still never blind a front row. The scene's real crowd: the client and the
     * validator use this.
     */
    public static List<Crowd> ofScene(List<SceneDef.EventDef> events) {
        List<Draft> drafts = new ArrayList<>();
        for (SceneDef.EventDef e : events) {
            if (!"effect".equals(e.kind()) || !"crowd".equals(e.id()) || e.rows() == null || e.at() == null) continue;
            float intensity = Float.isNaN(e.intensity()) ? 1f : e.intensity();
            Draft d = draft(e.rows(), e.at(), e.style(), intensity, e.count(), seed(e));
            d.event = e;
            drafts.add(d);
        }
        List<Crowd> out = new ArrayList<>(drafts.size());
        for (Draft d : drafts) out.add(finish(d, drafts));
        return out;
    }

    /** The {@code crowd} event each of {@link #ofScene}'s crowds came from. */
    public SceneDef.EventDef event() {
        return event;
    }

    /** One event's filled seats before standers are resolved against the scene and the cap applied. */
    private static final class Draft {
        float[] at;
        int houseStyle, count;
        long seed;
        List<double[]> seats;                               // x, y, z, yaw, scale, standing, row, k, fill
        SceneDef.EventDef event;
    }

    private static Draft draft(float[][] rows, float[] at, String style, double intensity, int count, long seed) {
        int houseStyle = style == null ? 4 : STYLES.indexOf(style);
        if (houseStyle < 0) houseStyle = 4;
        List<double[]> seats = new ArrayList<>();          // x, y, z, yaw, scale, standing, row, k, fill
        for (int r = 0; r < rows.length; r++) {
            float[] row = rows[r];
            if (row == null || row.length < 6) continue;
            boolean stand = row.length > 6 && row[6] >= 0.5f;
            double dx = row[3] - row[0], dy = row[4] - row[1], dz = row[5] - row[2];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double hl = Math.sqrt(dx * dx + dz * dz);
            double px = hl > 1e-9 ? -dz / hl : 1, pz = hl > 1e-9 ? dx / hl : 0;
            double ux = len > 1e-9 ? dx / len : 0, uy = len > 1e-9 ? dy / len : 0, uz = len > 1e-9 ? dz / len : 0;
            List<Double> along = new ArrayList<>();
            along.add(0.0);
            double run = 0;
            for (int k = 0; k < 100_000; k++) {
                double gap = MIN_SPACING + (MAX_SPACING - MIN_SPACING) * unit(seed, r, k, SALT_GAP);
                if (run + gap > len) break;
                run += gap;
                along.add(run);
            }
            double offset = (len - run) / 2;
            for (int k = 0; k < along.size(); k++) {
                double s = offset + along.get(k);
                double side = (unit(seed, r, k, SALT_SIDE) * 2 - 1) * SIDE_JITTER;
                double sx = row[0] + ux * s + px * side, sy = row[1] + uy * s, sz = row[2] + uz * s + pz * side;
                double face = Math.toDegrees(Math.atan2(-(at[0] - sx), at[2] - sz))
                        + (unit(seed, r, k, SALT_YAW) * 2 - 1) * YAW_JITTER;
                double sc = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * unit(seed, r, k, SALT_SCALE);
                double fill = unit(seed, r, k, SALT_FILL);
                if (fill < intensity) seats.add(new double[]{sx, sy, sz, face, sc, stand ? 1 : 0, r, k, fill});
            }
        }
        Draft d = new Draft();
        d.at = at;
        d.houseStyle = houseStyle;
        d.count = count;
        d.seed = seed;
        d.seats = seats;
        return d;
    }

    private static Crowd finish(Draft d, List<Draft> scene) {
        float[] at = d.at;
        int houseStyle = d.houseStyle, count = d.count;
        long seed = d.seed;
        // Standers who would hide the field from any seated spectator's eye (of any crowd in the scene) stay home.
        List<double[]> kept = new ArrayList<>(d.seats.size());
        int dropped = 0;
        for (double[] s : d.seats) {
            if (s[5] > 0 && blocksAny(s, scene)) dropped++;
            else kept.add(s);
        }
        int cap = count > 0 ? Math.min(count, MAX_SPECTATORS) : MAX_SPECTATORS;
        if (kept.size() > cap) {
            List<double[]> byFill = new ArrayList<>(kept);
            byFill.sort(Comparator.comparingDouble((double[] s) -> s[8]).thenComparingDouble(s -> s[6]).thenComparingDouble(s -> s[7]));
            List<double[]> keep = byFill.subList(0, cap);
            java.util.Set<double[]> chosen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            chosen.addAll(keep);
            kept.removeIf(s -> !chosen.contains(s));
        }
        Crowd c = new Crowd(kept.size(), at, dropped);
        c.event = d.event;
        for (int i = 0; i < c.n; i++) {
            double[] s = kept.get(i);
            int r = (int) s[6], k = (int) s[7];
            c.x[i] = (float) s[0];
            c.y[i] = (float) s[1];
            c.z[i] = (float) s[2];
            c.yaw[i] = (float) s[3];
            c.scale[i] = (float) s[4];
            c.standing[i] = s[5] > 0;
            c.house[i] = (byte) (houseStyle < 4 ? houseStyle : houseStyle == 5
                    ? triwizardHouse(unit(seed, r, k, SALT_HOUSE)) : Math.min(3, (int) (unit(seed, r, k, SALT_HOUSE) * 4)));
            double o = unit(seed, r, k, SALT_OUTFIT);
            c.outfit[i] = (byte) (o < 0.55 ? JUMPER : o < 0.8 ? ROBE : SECOND);
            c.skin[i] = (byte) Math.min(5, (int) (unit(seed, r, k, SALT_SKIN) * 6));
            c.hair[i] = (byte) Math.min(5, (int) (unit(seed, r, k, SALT_HAIR) * 6));
            double g = unit(seed, r, k, SALT_GEAR);
            int gear = g < SCARF_SHARE ? SCARF : 0;
            if (g >= 1 - FLAG_SHARE) gear |= FLAG;
            else if (g >= 1 - FLAG_SHARE - BANNER_SHARE) gear |= BANNER;
            c.gear[i] = (byte) gear;
            c.phase[i] = (float) (unit(seed, r, k, SALT_PHASE) * Math.PI * 2);
            c.swayTicks[i] = (float) (60 + 50 * unit(seed, r, k, SALT_SWAY));
            c.waveTicks[i] = (float) (160 + 160 * unit(seed, r, k, SALT_WAVE));
            c.delayDraw[i] = (float) unit(seed, r, k, SALT_DELAY);
            c.cheerDraw[i] = (float) unit(seed, r, k, SALT_CHEER);
        }
        return c;
    }

    /** The school a {@code triwizard} spectator at house draw u (in [0, 1)) belongs to: a house id 0 to 3, 4 or 5. */
    static int triwizardHouse(double u) {
        if (u < TRIWIZARD_HOUSES) return Math.min(3, (int) (u / TRIWIZARD_HOUSES * 4));
        return u < TRIWIZARD_HOUSES + TRIWIZARD_BEAUXBATONS ? BEAUXBATONS : DURMSTRANG;
    }

    private static boolean blocksAny(double[] stander, List<Draft> scene) {
        for (Draft d : scene) {
            for (double[] s : d.seats) {
                if (s[5] == 0 && blocks(s[0], s[1], s[2], s[4], stander[0], stander[1], stander[2], stander[4], d.at)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a stander (feet at {@code fx, fy, fz}, size {@code fs}) hides the field from the seated spectator at
     * {@code (sx, sy, sz)} (size {@code ss}): the line from the seated eye to {@code at} passes through the stander's
     * body (a {@link #STANDER_RADIUS} column from the feet to {@link #STANDER_HEIGHT}) within {@link #SIGHT_REACH}.
     */
    public static boolean blocks(double sx, double sy, double sz, double ss, double fx, double fy, double fz, double fs,
                                 float[] at) {
        double ex = sx, ey = sy + SEATED_EYE * ss, ez = sz;
        double wx = fx - ex, wz = fz - ez;
        if (wx * wx + wz * wz > (SIGHT_REACH + 1) * (SIGHT_REACH + 1)) return false;
        double dx = at[0] - ex, dy = at[1] - ey, dz = at[2] - ez;
        double dd = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dd < 1e-6) return false;
        dx /= dd;
        dy /= dd;
        dz /= dd;
        double hh = dx * dx + dz * dz;
        if (hh < 1e-9) return false;
        double s = (wx * dx + wz * dz) / hh;              // along the sight line, in blocks
        if (s <= 0 || s > SIGHT_REACH) return false;
        double cx = wx - dx * s, cz = wz - dz * s;
        if (cx * cx + cz * cz > STANDER_RADIUS * STANDER_RADIUS) return false;
        double cy = ey + dy * s;
        return cy >= fy && cy <= fy + STANDER_HEIGHT * fs;
    }

    /** How many spectators a {@code crowd} event seats (for the validator's scene-wide cap). */
    public static int seatCount(SceneDef.EventDef e) {
        return of(e).size();
    }

    // ------------------------------------------------------------------ the reactions

    /** One {@code crowd_react}: when, where attention goes, the box of who reacts (null: everyone), kind and length. */
    public record Reaction(float t, int kind, float ax, float ay, float az, float[] spread, int duration) {
        /** Ticks the reaction holds before it lets go (its own duration, or the kind's default). */
        public int hold() {
            int d = duration > 0 ? duration : DEFAULT_TICKS[kind];
            if (kind == GASP) return GASP_TICKS;
            if (kind == ERUPT) return Math.max(MIN_ERUPT_TICKS, d);
            return d;
        }

        boolean holds(float x, float y, float z) {
            if (spread == null) return true;
            return Math.abs(x - ax) <= spread[0] && Math.abs(y - ay) <= spread[1] && Math.abs(z - az) <= spread[2];
        }
    }

    /** A scene's {@code crowd_react} events in time order (unknown styles are left out; the validator names them). */
    public static List<Reaction> reactions(List<SceneDef.EventDef> events) {
        List<Reaction> out = new ArrayList<>();
        for (SceneDef.EventDef e : events) {
            if (!"effect".equals(e.kind()) || !"crowd_react".equals(e.id())) continue;
            int kind = e.style() == null ? -1 : REACTIONS.indexOf(e.style());
            if (kind < 0) continue;
            float[] at = e.at() == null ? new float[3] : e.at();
            out.add(new Reaction(e.t(), kind, at[0], at[1], at[2], e.spread(), Math.max(0, e.duration())));
        }
        out.sort(Comparator.comparingDouble(Reaction::t));
        return out;
    }

    /** Bind the scene's reactions: who reacts to each, and when (their own delay). Returns this crowd. */
    public Crowd bind(List<Reaction> list) {
        reactions = list.toArray(new Reaction[0]);
        onset = new float[reactions.length][];
        for (int r = 0; r < reactions.length; r++) {
            Reaction re = reactions[r];
            float[] o = new float[n];
            for (int i = 0; i < n; i++) {
                if (!re.holds(x[i], y[i], z[i])) {
                    o[i] = Float.NaN;
                    continue;
                }
                double dx = x[i] - re.ax(), dy = y[i] - re.ay(), dz = z[i] - re.az();
                double far = Math.min(1.0, Math.sqrt(dx * dx + dy * dy + dz * dz) / DELAY_RANGE);
                o[i] = re.t() + MAX_DELAY_TICKS * (float) (0.4 * delayDraw[i] + 0.6 * far);
            }
            onset[r] = o;
        }
        return this;
    }

    /** When spectator {@code i} starts reaction {@code r} (the index in time order), or NaN when it is not theirs. */
    public float onset(int r, int i) {
        return onset[r][i];
    }

    public int reactionCount() {
        return reactions.length;
    }

    // ------------------------------------------------------------------ the pose

    /**
     * Spectator {@code i}'s pose at scene time {@code t} into {@code out[off .. off + CHANNELS)}: how far up
     * ({@link #STAND} 0 seated .. 1 standing), the jump, each arm's raise (0 down .. 1 thrown up), the hands-to-head
     * share, the lean (radians, forward) and sway (radians), how high a flag is held, and a scarf wave. No allocation.
     */
    public void pose(int i, float t, float[] out, int off) {
        pose(i, t, out, off, false);
    }

    /**
     * As {@link #pose(int, float, float[], int)}; {@code still}: the idle pose holds still (no sway, lean or scarf wave),
     * for figures drawn too small to show it, so an idle far stand never needs rebuilding.
     */
    public void pose(int i, float t, float[] out, int off, boolean still) {
        this.still = still;
        evaluate(i, t, Float.POSITIVE_INFINITY, out, off, 0);
        if (standing[i]) out[off + STAND] = 1f;
    }

    /** Blends nest at most this deep (reactions landing within each other's rise); deeper starts from idle. */
    private static final int MAX_BLEND_DEPTH = 6;
    private final float[] blendScratch = new float[CHANNELS * MAX_BLEND_DEPTH];
    private boolean still;

    /**
     * The pose at {@code t} from the reactions whose onset is before {@code before}: the latest of them, blended in over
     * its rise from the pose actually shown at its onset (worked out the same way, so reactions landing close together
     * chain without a pop).
     */
    private void evaluate(int i, float t, float before, float[] out, int off, int depth) {
        int cur = -1;
        float curOn = Float.NEGATIVE_INFINITY;
        for (int r = 0; r < reactions.length; r++) {
            float o = onset[r][i];
            if (o != o || o > t || o >= before) continue;  // NaN: not theirs; later: not yet
            if (o >= curOn) {
                cur = r;
                curOn = o;
            }
        }
        if (cur < 0) {
            idle(i, t, out, off);
            return;
        }
        float tau = t - curOn;
        float rise = reactions[cur].kind() == SIT ? SIT_RISE : RISE;
        if (tau >= rise || depth >= MAX_BLEND_DEPTH) {
            react(i, cur, tau, t, out, off);
            return;
        }
        int so = depth * CHANNELS;
        evaluate(i, curOn, curOn, blendScratch, so, depth + 1);
        react(i, cur, tau, t, out, off);
        float w = smooth(tau / rise);
        for (int ch = 0; ch < CHANNELS; ch++) {
            float from = blendScratch[so + ch];
            out[off + ch] = from + (out[off + ch] - from) * w;
        }
    }

    /**
     * Whether spectator {@code i}'s pose is changing at {@code t} (a reaction has started and not yet let go): outside
     * these windows a figure drawn still is exactly its idle pose, unchanged since the last build.
     */
    public boolean moving(int i, float t) {
        for (int r = 0; r < reactions.length; r++) {
            float o = onset[r][i];
            if (o == o && o <= t && t <= o + span(r)) return true;
        }
        return false;
    }

    /** How long reaction {@code r} moves a spectator after their onset (its rise, hold and fall), in ticks. */
    public float span(int r) {
        Reaction re = reactions[r];
        if (re.kind() == SIT) return SIT_RISE;
        return Math.max(RISE, re.hold() + (re.kind() == GASP ? 10f : FALL));
    }

    /** Reaction {@code r} at {@code tau} ticks after spectator {@code i}'s onset ({@code t}: the scene time then). */
    private void react(int i, int r, float tau, float t, float[] out, int off) {
        Reaction re = reactions[r];
        int kind = re.kind();
        idle(i, t, out, off);
        if (kind == SIT) return;
        int hold = re.hold();
        float fall = kind == GASP ? 10f : FALL;
        float env = tau <= hold ? 1f : 1f - smooth((tau - hold) / fall);
        if (env <= 0f) return;
        float ph = phase[i];
        float stand = 0, arms = 0, hands = 0, jump = 0, lean = out[off + LEAN], flag = 0;
        float armL, armR;
        if (kind == GOAL || kind == CHEER) {
            stand = kind == GOAL || cheerDraw[i] < CHEER_STANDERS ? 1f : 0f;
            arms = 1f;
            flag = 1f;
            float shake = 0.12f * (float) Math.sin(2 * Math.PI * tau / 12f + ph);
            armL = arms - 0.12f + shake;
            armR = arms - 0.12f - shake;
            lean = 0.02f;
        } else if (kind == GASP) {
            hands = 1f;
            lean = -0.14f;
            armL = armR = 0f;
        } else if (kind == ERUPT) {
            stand = 1f;
            arms = 1f;
            flag = 1f;
            jump = BOUNCE * (float) Math.max(0, Math.sin(2 * Math.PI * tau / BOUNCE_PERIOD + ph));
            float shake = 0.1f * (float) Math.sin(2 * Math.PI * tau / 7f + ph);
            armL = 1f + shake;
            armR = 1f - shake;
            lean = -0.04f;
        } else {                                          // CHANT: in phase for the whole stand, on the scene clock
            float pump = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * t / PUMP_PERIOD);
            armR = 0.45f + 0.5f * pump;
            armL = 0.25f + 0.3f * pump;
            flag = 0.6f + 0.4f * pump;
        }
        out[off + STAND] += (stand - out[off + STAND]) * env;
        out[off + JUMP] += (jump - out[off + JUMP]) * env;
        out[off + ARM_L] += (armL - out[off + ARM_L]) * env;
        out[off + ARM_R] += (armR - out[off + ARM_R]) * env;
        out[off + HANDS_HEAD] += (hands - out[off + HANDS_HEAD]) * env;
        out[off + LEAN] += (lean - out[off + LEAN]) * env;
        out[off + FLAG_UP] += (flag - out[off + FLAG_UP]) * env;
        out[off + WAVE] *= 1f - env;
        out[off + SWAY] *= 1f - 0.7f * env;
    }

    /** The idle pose: seated, a slow sway, leaning in to watch, and a scarf waved overhead now and then. */
    private void idle(int i, float t, float[] out, int off) {
        float ph = phase[i];
        out[off + STAND] = 0f;
        out[off + JUMP] = 0f;
        out[off + HANDS_HEAD] = 0f;
        if (still) {
            out[off + LEAN] = 0.06f;
            out[off + SWAY] = 0f;
            out[off + WAVE] = 0f;
            out[off + ARM_L] = 0f;
            out[off + ARM_R] = 0f;
            out[off + FLAG_UP] = 0.15f;
            return;
        }
        out[off + LEAN] = 0.06f + 0.03f * (float) Math.sin(2 * Math.PI * t / (swayTicks[i] * 1.7f) + ph * 1.3f);
        out[off + SWAY] = 0.05f * (float) Math.sin(2 * Math.PI * t / swayTicks[i] + ph);
        float wave = 0f;
        if ((gear[i] & SCARF) != 0) {
            float w = (float) Math.sin(2 * Math.PI * t / waveTicks[i] + ph * 2.1f);
            wave = smooth((w - 0.8f) / 0.12f);
        }
        out[off + WAVE] = wave;
        out[off + ARM_L] = 0f;
        out[off + ARM_R] = 0.9f * wave;
        out[off + FLAG_UP] = 0.15f;
    }

    static float smooth(float k) {
        if (k <= 0f) return 0f;
        if (k >= 1f) return 1f;
        return k * k * (3f - 2f * k);
    }

    // ------------------------------------------------------------------ chunks and the level of detail

    /**
     * The spectators grouped into square cells {@code cell} blocks across (x and z), each cell split into runs of at most
     * {@code max}: the units the renderer rebuilds and draws. Every spectator is in exactly one chunk; order is fixed.
     */
    public int[][] chunks(float cell, int max) {
        Integer[] order = new Integer[n];
        long[] key = new long[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
            long cx = (long) Math.floor(x[i] / cell), cz = (long) Math.floor(z[i] / cell);
            key[i] = (cx << 32) ^ (cz & 0xFFFFFFFFL);
        }
        Arrays.sort(order, Comparator.comparingLong((Integer i) -> key[i]).thenComparingInt(i -> i));
        List<int[]> out = new ArrayList<>();
        int s = 0;
        while (s < n) {
            int e = s;
            while (e < n && key[order[e]] == key[order[s]] && e - s < max) e++;
            int[] c = new int[e - s];
            for (int j = s; j < e; j++) c[j - s] = order[j];
            out.add(c);
            s = e;
        }
        return out.toArray(new int[0][]);
    }

    /**
     * The level-of-detail cut-offs for this frame, as squared distances {@code {full, mid}}: full figures out to
     * {@code fullRange} and two-box figures out to {@code midRange}, each pulled in so at most {@code maxFull} full and
     * {@code maxMid} two-box figures are drawn ({@code d2[i]} is spectator i's squared distance to the camera;
     * {@code bins} is a scratch array, reused so nothing is allocated per frame). Beyond the mid cut-off: flat strips.
     */
    public static void lodCutoffs(float[] d2, int count, float fullRange, float midRange, int maxFull, int maxMid,
                                  int[] bins, float[] out) {
        int nb = bins.length;
        Arrays.fill(bins, 0);
        float step = midRange / nb;
        for (int i = 0; i < count; i++) {
            float d = (float) Math.sqrt(d2[i]);
            int b = (int) (d / step);
            if (b < nb) bins[b]++;
        }
        float full = 0, mid = 0;
        int sum = 0;
        boolean fullDone = false;
        for (int b = 0; b < nb; b++) {
            float edge = (b + 1) * step;
            sum += bins[b];
            if (!fullDone) {
                if (edge > fullRange + 1e-4f || sum > maxFull) fullDone = true;
                else full = edge;
            }
            if (sum > maxFull + maxMid) break;
            mid = edge;
        }
        out[0] = full * full;
        out[1] = mid * mid;
    }

    // ------------------------------------------------------------------ accessors

    public int size() {
        return n;
    }

    /** Standers left out because they would hide the field from a seated eye. */
    public int droppedStanders() {
        return dropped;
    }

    public float atX() {
        return ax;
    }

    public float atY() {
        return ay;
    }

    public float atZ() {
        return az;
    }

    public float x(int i) {
        return x[i];
    }

    /** The seat top, or the floor for a standing-row spectator. */
    public float y(int i) {
        return y[i];
    }

    public float z(int i) {
        return z[i];
    }

    /** Facing, in degrees (Minecraft yaw: 0 looks along +z). */
    public float yaw(int i) {
        return yaw[i];
    }

    public float scale(int i) {
        return scale[i];
    }

    public boolean standing(int i) {
        return standing[i];
    }

    /** 0 Gryffindor, 1 Hufflepuff, 2 Ravenclaw, 3 Slytherin, 4 Beauxbatons, 5 Durmstrang (the last two: triwizard only). */
    public int house(int i) {
        return house[i];
    }

    public int outfit(int i) {
        return outfit[i];
    }

    public int skin(int i) {
        return skin[i];
    }

    public int hair(int i) {
        return hair[i];
    }

    public int gear(int i) {
        return gear[i];
    }

    public float phase(int i) {
        return phase[i];
    }
}
