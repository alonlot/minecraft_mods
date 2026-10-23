package com.hogwartsmagic.client.pensieve;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Debug evidence for actor smoothness (client only, DEBUG level, so silent unless debug logging is on; the dev
 * client's debug.log has it). Each frame, for each drawn actor, the camera-relative position's second difference
 * (blocks): a smooth follow keeps it near 0, a lerp fed the wrong partial tick makes it spike once a tick. Once a
 * second it logs the worst actor, with the frame's partial tick next to the one the render event offered, e.g.
 * {@code Pensieve jitter: worst broom_r_k 0.004 blocks/frame^2 over 60 frames; partial 0.41, event partial 0.33}.
 */
final class JitterProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long EVERY_MS = 1000;

    private static final Map<ActorRuntime, double[]> LAST = new IdentityHashMap<>();
    private static double worst;
    private static String worstId;
    private static int frames;
    private static long lastLog;
    private static float eventPartial = Float.NaN;

    private JitterProbe() {
    }

    static boolean on() {
        return LOGGER.isDebugEnabled();
    }

    /** The partial tick the render event offered this frame (Forge 51's frame length in ticks; see partial()). */
    static void eventPartial(float p) {
        eventPartial = p;
    }

    /** One actor drawn at (rx, ry, rz) relative to the camera this frame. */
    static void sample(ActorRuntime a, double rx, double ry, double rz) {
        double[] h = LAST.get(a);
        if (h == null) {
            LAST.put(a, new double[]{rx, ry, rz, rx, ry, rz, 0});
            return;
        }
        if (h[6] >= 1) {
            double dx = rx - 2 * h[0] + h[3], dy = ry - 2 * h[1] + h[4], dz = rz - 2 * h[2] + h[5];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            // A jump of more than a block is a cut or a new shot, not jitter.
            if (d > worst && d < 1.0) {
                worst = d;
                worstId = a.def.id();
            }
        }
        h[3] = h[0];
        h[4] = h[1];
        h[5] = h[2];
        h[0] = rx;
        h[1] = ry;
        h[2] = rz;
        h[6] = 1;
    }

    /** The frame is drawn: log once a second. */
    static void endFrame(float partial) {
        frames++;
        long now = System.currentTimeMillis();
        if (now - lastLog < EVERY_MS) return;
        if (worstId != null) {
            LOGGER.debug("Pensieve jitter: worst {} {} blocks/frame^2 over {} frames; partial {}, event partial {}",
                    worstId, String.format("%.4f", worst), frames, String.format("%.2f", partial),
                    String.format("%.2f", eventPartial));
        }
        lastLog = now;
        worst = 0;
        worstId = null;
        frames = 0;
    }

    static void clear() {
        LAST.clear();
        worst = 0;
        worstId = null;
        frames = 0;
    }
}
