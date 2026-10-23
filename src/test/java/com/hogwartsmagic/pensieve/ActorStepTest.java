package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.ActorStep;
import com.hogwartsmagic.pensieve.scene.BroomFlight;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.Vehicle;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cut rule MemoryActors draws actors by ({@link ActorStep}): a long step the path flies continuously is lerped, a
 * jump the path makes in an instant is not. The Flying memories' brooms fly up to 4.3 blocks a tick (the Keeper's dive,
 * Neville's runaway broom); under the old rule (any step over 2 blocks is a cut) each such tick drew the broom and its
 * rider still for a tick and then jumped: the stutter at speed the user saw.
 */
class ActorStepTest {
    private static final Path MEMORIES = Path.of("src/main/resources/data/hogwartsmagic/memories");
    /** Frames drawn per tick in the simulation. */
    private static final int FRAMES = 6;
    /** How far a drawn frame may sit from the path at its moment: a tick's lerp chord (0.125 at Neville's 4.3 b/t peak). */
    private static final double FRAME_TOLERANCE = 0.15;

    private static SceneDef parse(String json) throws Exception {
        return SceneParser.parse("step", JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void fastContinuousFlightIsNotACut() throws Exception {
        // 3 blocks a tick along +X for 100 ticks.
        SceneDef s = parse("""
                {"length":200,"actors":[{"id":"b","type":"hogwartsmagic:memory_broom",
                 "path":[{"t":0,"pos":[0,5,0]},{"t":100,"pos":[300,5,0]}],"look":[{"t":0,"yaw":-90}]}]}""");
        SceneDef.ActorDef b = s.actors().get(0);
        // From rest straight to 3 blocks a tick is a leap; from then on it is flight.
        for (int k = 2; k < 100; k++) assertFalse(ActorStep.isCut(s, b, k - 1, k), "tick " + k);
        // A clock running fast (rate 1.5) still lerps it.
        assertFalse(ActorStep.isCut(s, b, 10, 11.5f));
    }

    @Test
    void instantJumpIsStillACut() throws Exception {
        // Parked, then swapped 30 blocks away between two keys a thousandth of a tick apart.
        SceneDef s = parse("""
                {"length":200,"actors":[{"id":"p","type":"player",
                 "path":[{"t":0,"pos":[0,0,0]},{"t":50,"pos":[0,0,0]},{"t":50.001,"pos":[30,0,0]},
                         {"t":100,"pos":[30,0,0]}]}]}""");
        SceneDef.ActorDef p = s.actors().get(0);
        assertTrue(ActorStep.isCut(s, p, 49.5f, 50.5f));
        assertFalse(ActorStep.isCut(s, p, 48, 49));
        // A small jump while flying fast is a cut too: 2.5 blocks in an instant on top of 1 block a tick.
        SceneDef f = parse("""
                {"length":200,"actors":[{"id":"p","type":"player",
                 "path":[{"t":0,"pos":[0,0,0]},{"t":50,"pos":[50,0,0]},{"t":50.001,"pos":[52.5,0,0]},
                         {"t":100,"pos":[102.5,0,0]}]}]}""");
        assertTrue(ActorStep.isCut(f, f.actors().get(0), 49.5f, 50.5f));
        // A clock snap (a long step in time) is a cut whatever the path did.
        assertTrue(ActorStep.isCut(f, f.actors().get(0), 45, 55));
    }

    /** The Letter's cuts are keys a tick apart: still cuts (Harry's cot swap at 297, the letter's at 429). */
    @Test
    void letterCutsAreStillCuts() throws Exception {
        SceneDef s = SceneParser.parse("letter", JsonParser.parseString(Files.readString(MEMORIES.resolve("letter.json"),
                StandardCharsets.UTF_8)).getAsJsonObject());
        assertTrue(ActorStep.isCut(s, actor(s, "boy_cot"), 296, 297));
        assertTrue(ActorStep.isCut(s, actor(s, "first_letter"), 428, 429));
        assertTrue(ActorStep.isCut(s, actor(s, "owl_03"), 993, 994));
    }

    private static SceneDef.ActorDef actor(SceneDef s, String id) {
        for (SceneDef.ActorDef a : s.actors()) if (a.id().equals(id)) return a;
        throw new AssertionError("no actor " + id);
    }

    /**
     * Every broom and every broom rider of every shipped memory, drawn as MemoryActors draws it (ticks lerped, a cut
     * drawn at once) at {@link #FRAMES} frames a tick, stays within {@link #FRAME_TOLERANCE} of its own path at each
     * frame's moment, except across a jump the path itself makes. Under the old rule the Keeper's dive was off by up
     * to 2.6 blocks.
     */
    @Test
    void shippedBroomsAndRidersAreDrawnOnTheirPaths() throws Exception {
        List<String> bad = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(MEMORIES)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String id = f.getFileName().toString().replace(".json", "");
                com.google.gson.JsonElement json = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
                if (!json.isJsonObject()) continue;  // _skins.json
                SceneDef s = SceneParser.parse(id, json.getAsJsonObject());
                for (SceneDef.ActorDef a : s.actors()) {
                    SceneDef.ActorDef v = Vehicle.vehicleOf(s, a);
                    boolean broom = BroomFlight.isBroom(a.type());
                    boolean rider = v != null && BroomFlight.isBroom(v.type());
                    if (!broom && !rider) continue;
                    checked++;
                    String err = drawnOffPath(s, a);
                    if (err != null) bad.add(id + "/" + a.id() + ": " + err);
                }
            }
        }
        assertTrue(checked > 0, "no brooms found");
        assertTrue(bad.isEmpty(), "drawn off the path:\n" + String.join("\n", bad));
    }

    private static String drawnOffPath(SceneDef s, SceneDef.ActorDef a) {
        int len = (int) Math.ceil(s.length());
        double worst = 0;
        int worstTick = -1;
        for (int k = 1; k <= len; k++) {
            double[] prev = ActorStep.root(s, a, k - 1), cur = ActorStep.root(s, a, k);
            if (jerky(s, a, k)) continue;
            boolean cut = ActorStep.isCut(s, a, k - 1, k);
            for (int j = 0; j < FRAMES; j++) {
                double u = j / (double) FRAMES;
                double[] exact = ActorStep.root(s, a, (float) (k - 1 + u));
                double e = 0;
                for (int c = 0; c < 3; c++) {
                    // MemoryActors draws lerp(partial, old, new) between the last two ticks: the frame u of the way
                    // from tick k-1 to tick k shows prev..cur, unless the step is a cut (then cur at once).
                    double drawn = cut ? cur[c] : prev[c] + (cur[c] - prev[c]) * u;
                    e += (drawn - exact[c]) * (drawn - exact[c]);
                }
                e = Math.sqrt(e);
                if (e > worst) {
                    worst = e;
                    worstTick = k;
                }
            }
        }
        return worst > FRAME_TOLERANCE ? String.format("%.3f blocks off at tick %d", worst, worstTick) : null;
    }

    /**
     * The path's step into tick k differs by over a block from the one before or after it: a cut or nearly one, where
     * drawing it at once is right. Smooth flight changes its step by far less (under 0.1 a tick in the Keeper's dive).
     */
    private static boolean jerky(SceneDef s, SceneDef.ActorDef a, int k) {
        double sp = dist(ActorStep.root(s, a, k - 2), ActorStep.root(s, a, k - 1));
        double sk = dist(ActorStep.root(s, a, k - 1), ActorStep.root(s, a, k));
        double sn = dist(ActorStep.root(s, a, k), ActorStep.root(s, a, k + 1));
        return Math.abs(sk - sp) > 1.0 || Math.abs(sk - sn) > 1.0;
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }
}
