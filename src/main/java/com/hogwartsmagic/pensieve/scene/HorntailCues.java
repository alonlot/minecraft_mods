package com.hogwartsmagic.pensieve.scene;

import com.hogwartsmagic.entity.HungarianHorntailEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The memory Horntail's state from her {@code anims} cues, a pure function of the cues and the scene clock (mirror:
 * {@code dragon_engine.horntail_cue} and {@code horntail_blend}). The cue names are the wild dragon's attack states
 * ({@link SceneNames#HORNTAIL_ANIMS}, case-insensitive), so the memory dragon plays exactly the wild dragon's model
 * animations:
 * <ul>
 *   <li>{@code IDLE} and {@code FLY} hold until the next cue: {@code IDLE} lands her (the state is {@code NONE}, the
 *       model's knuckle-walking rest), {@code FLY} lifts her (flight pose, beating wings);</li>
 *   <li>every other name is a one-shot of the wild dragon's own length ({@code *_DURATION} in
 *       {@link HungarianHorntailEntity}); once it is over the state is {@code FLY} if she is flying, else {@code NONE};</li>
 *   <li>{@code TAKEOFF} and {@code FLY} set flying, {@code IDLE} clears it, {@code LAND} clears it at the end of its
 *       {@code LAND_DURATION} (or at the next cue, if that comes first: she is down); every other cue keeps it.</li>
 * </ul>
 * Before the first cue she is grounded in {@code NONE}. {@code stateTick} is the whole ticks since the state began.
 *
 * <p><b>Blends.</b> The wild dragon snaps from one state's last frame to the next state's first (a breath's open jaw
 * shuts in one frame). The memory dragon cross-fades: for {@link #blendTicks} after each change (a cue, or a one-shot
 * running out) the previous state keeps playing and is faded out with a smoothstep ({@link #blendAt}).
 */
public final class HorntailCues {
    private HorntailCues() {
    }

    /** The default cross-fade between two states, in ticks (a cue's own {@code blend} overrides it for that cue). */
    public static final float DEFAULT_BLEND = 6;
    /**
     * The states in which a flying dragon shows the beating wings (the others pose the wings themselves: a dive's
     * swept delta, the landing flare, a roar's or a breath's spread wings, the stagger).
     */
    public static final Set<String> BEATING = Set.of("FLY", "TAKEOFF", "AIR_BREATH", "BITE");

    /** The one-shots and their lengths: the wild dragon's own. */
    private static final Map<String, Integer> DURATIONS = Map.ofEntries(
            Map.entry("TAKEOFF", HungarianHorntailEntity.TAKEOFF_DURATION),
            Map.entry("LAND", HungarianHorntailEntity.LAND_DURATION),
            Map.entry("ROAR", HungarianHorntailEntity.ROAR_DURATION),
            Map.entry("BITE", HungarianHorntailEntity.BITE_DURATION),
            Map.entry("TAIL_WHIP", HungarianHorntailEntity.TAIL_WHIP_DURATION),
            Map.entry("BREATH_WINDUP", HungarianHorntailEntity.BREATH_WINDUP_DURATION),
            Map.entry("BREATH", HungarianHorntailEntity.BREATH_DURATION),
            Map.entry("WING_GUST", HungarianHorntailEntity.WING_GUST_DURATION),
            Map.entry("DIVE", HungarianHorntailEntity.DIVE_DURATION),
            Map.entry("STAGGER", HungarianHorntailEntity.STAGGER_DURATION),
            Map.entry("AIR_BREATH", HungarianHorntailEntity.AIR_BREATH_DURATION));

    /**
     * A state: {@code name} is an attack-state name ({@code NONE} for the grounded rest), {@code stateTick} the whole
     * ticks since it began, {@code flying} whether she flies, {@code time} the exact (fractional) ticks since it began.
     */
    public record State(String name, int stateTick, boolean flying, double time) {
    }

    /**
     * The state now, and the one fading out ({@code before}, null when none) with the weight of {@code now}. When the
     * change came while {@code before} was itself still fading in, {@code older} is that fade (its {@code now} is
     * {@code before}), so the pose fades out of what was really drawn rather than snapping to {@code before} alone.
     */
    public record Blend(State now, State before, double weight, Blend older) {
        public Blend(State now, State before, double weight) {
            this(now, before, weight, null);
        }
    }

    /** How many fades deep a quick run of cues keeps (beyond it the oldest fade is dropped). */
    public static final int MAX_NESTED_BLENDS = 3;

    /** A one-shot's length in ticks, or -1 for a held state ({@code IDLE}, {@code FLY}). */
    public static int duration(String name) {
        Integer d = DURATIONS.get(name);
        return d == null ? -1 : d;
    }

    /** The cue name as a state name: upper case, {@code IDLE} as {@code NONE}. */
    public static String stateName(String cueName) {
        String n = cueName.toUpperCase(Locale.ROOT);
        return "IDLE".equals(n) ? "NONE" : n;
    }

    /** One segment of the state timeline: a state from {@code start}, with its flying flag. */
    private record Segment(String name, double start, boolean flying, float blend) {
    }

    /** The cues as a timeline of segments, in time order (each one-shot followed by its fallback). */
    private static List<Segment> timeline(List<SceneDef.Cue> anims) {
        List<SceneDef.Cue> cues = new ArrayList<>(anims);
        cues.sort(Comparator.comparingDouble(SceneDef.Cue::t));
        List<Segment> out = new ArrayList<>();
        out.add(new Segment("NONE", Double.NEGATIVE_INFINITY, false, 0));
        boolean flying = false;
        for (int i = 0; i < cues.size(); i++) {
            SceneDef.Cue c = cues.get(i);
            String name = stateName(c.name());
            double next = i + 1 < cues.size() ? cues.get(i + 1).t() : Double.POSITIVE_INFINITY;
            if ("TAKEOFF".equals(name) || "FLY".equals(name)) flying = true;
            if ("NONE".equals(name)) flying = false;
            boolean landing = "LAND".equals(name);
            out.add(new Segment(name, c.t(), landing || flying, c.blend()));
            int d = duration(name);
            if (landing) flying = false;
            if (d >= 0 && c.t() + d < next) {
                out.add(new Segment(flying ? "FLY" : "NONE", c.t() + d, flying, 0));
            }
        }
        return out;
    }

    private static int segmentAt(List<Segment> segs, double t) {
        int i = 0;
        while (i + 1 < segs.size() && segs.get(i + 1).start() <= t) i++;
        return i;
    }

    private static State stateOf(Segment s, double t) {
        double time = Double.isInfinite(s.start()) ? 0 : t - s.start();
        return new State(s.name(), (int) Math.floor(time), s.flying(), time);
    }

    /** The state at scene time t. */
    public static State at(List<SceneDef.Cue> anims, double t) {
        List<Segment> segs = timeline(anims);
        return stateOf(segs.get(segmentAt(segs, t)), t);
    }

    /** The cross-fade at the start of segment i, in ticks: its cue's blend, else {@link #DEFAULT_BLEND}. */
    private static double blendTicks(Segment s) {
        return s.blend() > 0 ? s.blend() : DEFAULT_BLEND;
    }

    /**
     * The state at t and, within the cross-fade after it began, the state before it (still playing on its own
     * clock: an ended one-shot holds its last frame) and the weight of the new one, a smoothstep from 0 to 1. A cue
     * that lands inside the previous change's fade nests it ({@link Blend#older}, up to {@link #MAX_NESTED_BLENDS}
     * deep), every fade running on in its own time.
     */
    public static Blend blendAt(List<SceneDef.Cue> anims, double t) {
        List<Segment> segs = timeline(anims);
        return blendOf(segs, segmentAt(segs, t), t, MAX_NESTED_BLENDS);
    }

    private static Blend blendOf(List<Segment> segs, int i, double t, int depth) {
        Segment s = segs.get(i);
        State now = stateOf(s, t);
        if (i == 0) return new Blend(now, null, 1);
        double into = t - s.start(), ticks = blendTicks(s);
        if (into >= ticks) return new Blend(now, null, 1);
        Blend prev = depth > 0 ? blendOf(segs, i - 1, t, depth - 1) : null;
        return new Blend(now, stateOf(segs.get(i - 1), t), DragonFlight.smooth(into / ticks),
                prev != null && prev.before() != null ? prev : null);
    }
}
