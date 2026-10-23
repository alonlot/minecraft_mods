package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.ActorStep;
import com.hogwartsmagic.pensieve.scene.CameraPose;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import com.hogwartsmagic.pensieve.scene.SmoothedSceneClock;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Flying brooms' jitter at speed. Forge 51's {@code RenderLevelStageEvent.getPartialTick()} is not the partial tick
 * but the frame's length in ticks ({@code Stage.dispatch} passes {@code getRealtimeDeltaTicks()}). MemoryActors lerped
 * every actor with it (about 0.33 at 60 fps, every frame), so an actor stood nearly still through each tick and
 * jumped at the next, while the camera, given the real partial tick, moved smoothly. The broom and rider jittered
 * against the camera in proportion to their speed.
 */
class BroomFrameJitterTest {
    private static final Path PENSIEVE_CLIENT = Path.of("src/main/java/com/hogwartsmagic/client/pensieve");
    private static final Path MEMORIES = Path.of("src/main/resources/data/hogwartsmagic/memories");

    /** No memory render handler takes its time from the event's partial tick (only the debug probe reads it). */
    @Test
    void renderStageHandlersUseTheFramesPartialTick() throws Exception {
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.list(PENSIEVE_CLIENT)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
                if (lines.stream().noneMatch(l -> l.contains("RenderLevelStageEvent"))) continue;
                for (int i = 0; i < lines.size(); i++) {
                    String l = lines.get(i);
                    if (l.contains("getPartialTick()") && !l.contains("JitterProbe.eventPartial")
                            && !l.trim().startsWith("*") && !l.trim().startsWith("//")) {
                        bad.add(f.getFileName() + ":" + (i + 1) + ": " + l.trim());
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "use PensieveClientState.partial(), not the event's (the frame length):\n"
                + String.join("\n", bad));
    }

    /**
     * The match's Chaser o_c1 (up to 1.5 blocks a tick) seen from the shot's own camera at 60 fps (frame times
     * jittered by +-3 ms), on the real SmoothedSceneClock fed time syncs up to 2 ticks late: the angle it sits at on
     * screen changes smoothly (second difference per frame, degrees) when the actor is lerped with the partial tick,
     * and jerks every tick when lerped with the frame length, as the old code did.
     */
    @Test
    void fastBroomIsSmoothAgainstTheCameraOnlyWithThePartialTick() throws Exception {
        SceneDef s = SceneParser.parse("flying_gryffindor_match", JsonParser.parseString(
                Files.readString(MEMORIES.resolve("flying_gryffindor_match.json"), StandardCharsets.UTF_8)).getAsJsonObject());
        SceneDef.ActorDef rider = null;
        for (SceneDef.ActorDef a : s.actors()) if (a.id().equals("o_c1")) rider = a;
        assertTrue(rider != null);
        double good = rms(s, rider, false), old = rms(s, rider, true);
        assertTrue(good < 0.3, "lerped with the partial tick: rms " + good + " deg");
        assertTrue(old > 1.5, "lerped with the frame length: rms " + old + " deg (the metric must see the old jitter)");
    }

    /** RMS of the on-screen second difference (degrees per frame^2) while the actor is fast and near the camera. */
    private static double rms(SceneDef s, SceneDef.ActorDef a, boolean frameLength) {
        Random rnd = new Random(1);
        SmoothedSceneClock clock = new SmoothedSceneClock();
        double acc = 0;
        long raw = 0, server = 0;
        boolean first = true;
        int ticks = 0;
        float lastT = 0;
        double[] prev = null, cur = null, a1 = null, a2 = null;
        double sum = 0;
        int n = 0;
        while (ticks < s.length() - 2) {
            double frame = 1000.0 / 60 + (rnd.nextDouble() - 0.5) * 6;
            acc += frame / 50.0;
            while (acc >= 1) {
                acc -= 1;
                ticks++;
                raw++;
                server++;
                boolean synced = server % 20 == 0;
                if (synced) raw = server - Math.round(rnd.nextDouble() * 2);
                clock.tick(raw, synced || first);
                first = false;
                float t = (float) clock.at(0);
                double[] p = ActorStep.root(s, a, t);
                prev = cur == null || ActorStep.isCut(s, a, lastT, t) ? p : cur;
                cur = p;
                lastT = t;
            }
            if (cur == null) continue;
            float partial = frameLength ? (float) (frame / 50.0) : (float) acc;
            CameraPose cam = s.camera().sample((float) clock.at((float) acc), x -> null);
            double[] at = new double[3];
            for (int c = 0; c < 3; c++) at[c] = prev[c] + (cur[c] - prev[c]) * partial;
            double dx = at[0] - cam.x(), dy = at[1] - cam.y(), dz = at[2] - cam.z();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double yaw = wrap(Math.toDegrees(Math.atan2(-dx, dz)) - cam.yaw());
            double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))) - cam.pitch();
            double speed = Math.sqrt(sq(cur[0] - prev[0]) + sq(cur[1] - prev[1]) + sq(cur[2] - prev[2]));
            boolean on = speed > 0.6 && dist < 25 && Math.abs(yaw) < 45 && Math.abs(pitch) < 35;
            double[] ang = {yaw, pitch};
            if (on && a1 != null && a2 != null) {
                double ex = wrap(ang[0] - 2 * a1[0] + a2[0]), ey = ang[1] - 2 * a1[1] + a2[1];
                sum += ex * ex + ey * ey;
                n++;
            }
            a2 = on ? a1 : null;
            a1 = on ? ang : null;
        }
        assertTrue(n > 100, "the rider is on screen at speed for " + n + " frames");
        return Math.sqrt(sum / n);
    }

    private static double sq(double x) {
        return x * x;
    }

    private static double wrap(double d) {
        return ((d + 180) % 360 + 360) % 360 - 180;
    }
}
