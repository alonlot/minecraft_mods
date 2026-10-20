package com.hogwartsmagic.pensieve;

import com.hogwartsmagic.pensieve.scene.WandChoice;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandChoiceTest {
    @Test
    void durationAndIntensityDefaults() {
        assertEquals(100, WandChoice.duration(0));
        assertEquals(100, WandChoice.duration(-3));
        assertEquals(160, WandChoice.duration(160));
        assertEquals(1f, WandChoice.intensity(Float.NaN));
        assertEquals(WandChoice.MAX_INTENSITY, WandChoice.intensity(50f));
        assertEquals(WandChoice.MIN_INTENSITY, WandChoice.intensity(0f));
    }

    @Test
    void gatherThenPeakThenSettle() {
        int total = 100;
        assertEquals(0f, WandChoice.glow(-1, total));
        assertEquals(0f, WandChoice.glow(0, total), 1e-6f);
        // Gathers over 20 ticks, never falling back.
        float prev = 0f;
        for (float t = 0; t <= WandChoice.GATHER_TICKS; t += 0.5f) {
            float g = WandChoice.glow(t, total);
            assertTrue(g >= prev - 1e-6f, "gather dips at " + t);
            prev = g;
        }
        assertEquals(1f, WandChoice.glow(WandChoice.GATHER_TICKS, total), 1e-6f);
        assertTrue(WandChoice.glow(10, total) > 0.2f && WandChoice.glow(10, total) < 0.8f);
        // Holds at the peak, then settles to nothing by the end, never rising again.
        assertEquals(1f, WandChoice.glow(50, total), 1e-6f);
        prev = 1f;
        for (float t = 50; t <= total + 10; t += 0.5f) {
            float g = WandChoice.glow(t, total);
            assertTrue(g <= prev + 1e-6f, "settle rises at " + t);
            prev = g;
        }
        assertEquals(0f, WandChoice.glow(total, total), 1e-6f);
        // The wind rises after the glow starts, and is gone at the end too.
        assertTrue(WandChoice.wind(2, total) < WandChoice.glow(2, total));
        assertEquals(1f, WandChoice.wind(50, total), 1e-6f);
        assertEquals(0f, WandChoice.wind(total, total), 1e-6f);
        // The bloom peaks at the end of the gather and fades out by the end.
        float peak = WandChoice.bloom(WandChoice.GATHER_TICKS, total, 1f);
        assertEquals(WandChoice.BLOOM_ALPHA, peak, 1e-6f);
        assertTrue(WandChoice.bloom(5, total, 1f) < peak);
        assertTrue(WandChoice.bloom(60, total, 1f) < peak && WandChoice.bloom(60, total, 1f) > 0f);
        assertEquals(0f, WandChoice.bloom(total, total, 1f), 1e-6f);
        assertEquals(2 * WandChoice.bloom(60, total, 1f), WandChoice.bloom(60, total, 2f), 1e-6f);
    }

    @Test
    void aShortChoiceStillGathersAndSettles() {
        int total = 20;
        assertEquals(0f, WandChoice.glow(0, total), 1e-6f);
        float most = 0f;
        for (float t = 0; t <= total; t += 0.25f) most = Math.max(most, WandChoice.glow(t, total));
        assertEquals(1f, most, 1e-3f);
        assertEquals(0f, WandChoice.glow(total, total), 1e-6f);
    }

    @Test
    void motesStayInTheirColumn() {
        for (int total : new int[]{20, 100, 300}) {
            for (int i = 0; i < WandChoice.MOTES; i++) {
                for (float t = -5; t <= total + 5; t += 0.75f) {
                    float[] m = WandChoice.mote(1234L, i, t, total);
                    assertEquals(4, m.length);
                    for (float v : m) assertTrue(Float.isFinite(v));
                    float r = (float) Math.sqrt(m[0] * m[0] + m[2] * m[2]);
                    assertTrue(r <= WandChoice.MAX_RADIUS + 1e-4f, "radius " + r);
                    assertTrue(m[1] >= -1e-4f && m[1] <= WandChoice.MAX_HEIGHT + 1e-4f, "height " + m[1]);
                    assertTrue(m[3] >= 0f && m[3] <= 1f, "alpha " + m[3]);
                }
            }
        }
    }

    @Test
    void motesAreDeterministicAndDarkOutsideTheEffect() {
        assertArrayEquals(WandChoice.mote(7L, 3, 41.5f, 100), WandChoice.mote(7L, 3, 41.5f, 100));
        assertTrue(WandChoice.mote(7L, 3, 41.5f, 100)[0] != WandChoice.mote(8L, 3, 41.5f, 100)[0]);
        assertEquals(0f, WandChoice.mote(7L, 3, -1f, 100)[3]);
        assertEquals(0f, WandChoice.mote(7L, 3, 100f, 100)[3]);
        // While gathering the motes close in on the centre; afterwards they rise.
        float r0 = radius(WandChoice.mote(7L, 3, 1f, 100));
        float r1 = radius(WandChoice.mote(7L, 3, 19f, 100));
        assertTrue(r1 < r0, r0 + " -> " + r1);
        float lit = 0f;
        float high = 0f;
        for (int i = 0; i < WandChoice.MOTES; i++) {
            float[] m = WandChoice.mote(7L, i, 60f, 100);
            lit += m[3];
            high = Math.max(high, m[1]);
        }
        assertTrue(lit > WandChoice.MOTES * 0.2f, "too few motes lit: " + lit);
        assertTrue(high > WandChoice.MAX_HEIGHT * 0.6f, "motes do not rise: " + high);
    }

    @Test
    void validatorNeedsAtAndRangesIntensity() throws Exception {
        com.hogwartsmagic.pensieve.scene.SceneNames names = new com.hogwartsmagic.pensieve.scene.SceneNames(
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), java.util.Set.of(), java.util.Set.of());
        java.util.function.Function<String, java.util.List<String>> v = extra -> {
            try {
                return com.hogwartsmagic.pensieve.scene.SceneValidator.validate(com.hogwartsmagic.pensieve.scene.SceneParser.parse("t",
                        com.google.gson.JsonParser.parseString("{\"length\":200,\"events\":[{\"t\":1,\"kind\":\"effect\","
                                + "\"id\":\"wand_choice\"" + extra + "}]}").getAsJsonObject()), names);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        assertEquals(java.util.List.of(), v.apply(",\"at\":[0,1.2,0]"));
        assertEquals(java.util.List.of(), v.apply(",\"at\":[0,1.2,0],\"duration\":160,\"intensity\":1.5"));
        java.util.List<String> m = v.apply("");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).contains("wand_choice needs at"), m.get(0));
        m = v.apply(",\"at\":[0,1,0],\"intensity\":3");
        assertEquals(1, m.size(), m.toString());
        assertTrue(m.get(0).startsWith("events[0].intensity"), m.get(0));
    }

    private static float radius(float[] m) {
        return (float) Math.sqrt(m[0] * m[0] + m[2] * m[2]);
    }
}
