package com.hogwartsmagic.client.portrait;

/**
 * The paper-puppet grid: 25 x 45 vertices over the painting, moved every frame by {@link #deform}. A straight port
 * of the approved prototype's {@code deform()}, in the painting's own pixel space (688 x 1376; the texture is that
 * scaled to 512 x 1024). Output is in canvas blocks: x in [-1, 1] toward the image's right, y in [-2, 2] up.
 */
final class PuppetMesh {
    static final int GX = 25;
    static final int GY = 45;
    static final int COUNT = GX * GY;
    static final float W = 688f;
    static final float H = 1376f;

    /** Neck pivot, and the centre line the body breathes about. */
    private static final float NECK_X = 348f, NECK_Y = 600f, MID_X = 350f;

    private static final float[] PX = new float[COUNT];
    private static final float[] PY = new float[COUNT];
    private static final float[] HEAD = new float[COUNT];
    private static final float[] BODY = new float[COUNT];
    private static final float[] FIGURE = new float[COUNT];
    private static final float[] PIN = new float[COUNT];

    static {
        for (int j = 0; j < GY; j++) {
            for (int i = 0; i < GX; i++) {
                int k = j * GX + i;
                float x = i / (float) (GX - 1) * W;
                float y = j / (float) (GY - 1) * H;
                PX[k] = x;
                PY[k] = y;
                HEAD[k] = ellipse(x, y, 355f, 400f, 200f, 235f, 0.8f, 1.45f);
                BODY[k] = ellipse(x, y, 350f, 960f, 440f, 580f, 0.55f, 1.1f);
                FIGURE[k] = Math.max(HEAD[k], BODY[k]);
                PIN[k] = smooth(0f, 70f, Math.min(Math.min(x, W - x), Math.min(y, H - y)));
            }
        }
    }

    private PuppetMesh() {
    }

    static float smooth(float a, float b, float t) {
        t = Math.min(1f, Math.max(0f, (t - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    private static float ellipse(float x, float y, float cx, float cy, float rx, float ry, float a, float b) {
        float dx = (x - cx) / rx;
        float dy = (y - cy) / ry;
        return 1f - smooth(a, b, (float) Math.sqrt(dx * dx + dy * dy));
    }

    /** Where vertex k sits at rest (painting pixels), used for UVs and light tiles. */
    static float u(int k) {
        return PX[k] / W;
    }

    static float v(int k) {
        return PY[k] / H;
    }

    /** Fills outX/outY (length COUNT) with the deformed vertex positions in canvas blocks. */
    static void deform(PortraitAnim s, float[] outX, float[] outY) {
        for (int k = 0; k < COUNT; k++) {
            float ox = PX[k], oy = PY[k];
            float x = ox, y = oy;
            float wh = HEAD[k], wb = BODY[k], wf = FIGURE[k], pin = PIN[k];

            // breathing: the chest swells upward from the lap
            float br = s.breath * wb;
            y = H - (H - y) * (1f + 0.009f * br);
            x = MID_X + (x - MID_X) * (1f + 0.005f * br);

            // head: bend about the neck, plus a small slide
            if (wh > 0f) {
                float a = s.rot * wh;
                float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                float dx = x - NECK_X, dy = y - NECK_Y;
                x = NECK_X + dx * c - dy * sn + s.hx * wh;
                y = NECK_Y + dx * sn + dy * c + s.hy * wh;
            }

            // flinch: shrink back into the chair and jump up a little
            float f = s.flinch * wf;
            x = MID_X + (x - MID_X) * (1f - 0.04f * f);
            y = H - (H - y) * (1f - 0.03f * f) - 16f * f;

            // the canvas edges never move: no gaps at the frame
            x = ox + (x - ox) * pin;
            y = oy + (y - oy) * pin;

            outX[k] = (x / W - 0.5f) * 2f;
            outY[k] = (0.5f - y / H) * 4f;
        }
    }
}
