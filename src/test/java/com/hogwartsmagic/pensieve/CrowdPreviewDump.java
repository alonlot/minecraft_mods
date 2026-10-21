package com.hogwartsmagic.pensieve;

import com.google.gson.JsonParser;
import com.hogwartsmagic.pensieve.scene.Crowd;
import com.hogwartsmagic.pensieve.scene.CrowdMesh;
import com.hogwartsmagic.pensieve.scene.SceneDef;
import com.hogwartsmagic.pensieve.scene.SceneParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Not a check: with CROWD_PREVIEW set to a folder, writes the crowd mesh of a small stand in four moments (idle, goal,
 * gasp, erupt) at every level of detail as text quads, for {@code build_scripts/pensieve_scenes/crowd_preview.py} to
 * draw, so the figures can be looked at without the game. Skipped otherwise.
 */
class CrowdPreviewDump {
    @Test
    void dump() throws Exception {
        String dir = System.getenv("CROWD_PREVIEW");
        Assumptions.assumeTrue(dir != null && !dir.isEmpty(), "CROWD_PREVIEW not set");
        float[][] rows = new float[4][];
        for (int k = 0; k < 4; k++) rows[k] = new float[]{-6, k + 0.5f, 10 + k, 6, k + 0.5f, 10 + k};
        String[] moments = {"idle", "goal", "gasp", "erupt"};
        float[] at = {0, 0, 0};
        Files.createDirectories(Path.of(dir));
        for (int lod = 0; lod < 3; lod++) {
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of(dir, "crowd_lod" + lod + ".txt")))) {
                for (int m = 0; m < moments.length; m++) {
                    String react = m == 0 ? "" : "{\"t\":100,\"kind\":\"effect\",\"id\":\"crowd_react\",\"at\":[0,0,0],\"style\":\""
                            + moments[m] + "\",\"duration\":200}";
                    SceneDef s = SceneParser.parse("t", JsonParser.parseString("{\"length\":2000,\"events\":[" + react + "]}")
                            .getAsJsonObject());
                    Crowd c = Crowd.layout(rows, at, "mixed", 0.9, 0, 77L + m).bind(Crowd.reactions(s.events()));
                    float t = m == 2 ? 118 : 135;
                    float shift = (m - 1.5f) * 15f;
                    CrowdMesh mesh = new CrowdMesh();
                    int[] all = new int[c.size()];
                    for (int i = 0; i < all.length; i++) all[i] = i;
                    final float dx = shift;
                    float full = lod == 0 ? 1e9f : 0f, mid = lod <= 1 ? 1e9f : 0f;
                    mesh.build(c, all, t, 0, 2, 0, 0, 1.5f, 0, full, mid, new CrowdMesh.Sink() {
                        @Override
                        public void figure(int index) {
                        }

                        @Override
                        public void vertex(float x, float y, float z, int argb) {
                            w.printf(Locale.ROOT, "%.4f %.4f %.4f %d%n", x + dx, y, z, argb & 0xFFFFFF);
                        }
                    });
                }
            }
        }
    }
}
