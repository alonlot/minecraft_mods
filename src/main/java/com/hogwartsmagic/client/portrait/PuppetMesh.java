package com.hogwartsmagic.client.portrait;

import com.hogwartsmagic.portrait.PortraitKind;

/**
 * The paper-puppet grid of one portrait kind: 25 x 45 vertices over a tall canvas (45 x 25 over a wide one),
 * moved every frame by {@link #deform}. A straight port of the approved prototype's {@code deform()}, in the
 * painting's own pixel space (the kind's {@code paintW x paintH}). Output is in canvas blocks: x in
 * [-width/2, width/2] toward the image's right, y in [-height/2, height/2] up.
 */
final class PuppetMesh {
    /** The tall canvas uses 25 x 45 vertices and the wide one 45 x 25: about 11-12 cells per block either way. */
    private static final int LONG_SIDE = 45;
    private static final int SHORT_SIDE = 25;

    final int gx;
    final int gy;
    final int count;
    /** How many grid rows from the top the face-part overlays have to cover (the head region, with a margin). */
    final int overlayRows;

    private final float w, h;
    private final int blocksX, blocksY;
    private final float neckX, neckY, midX;
    private final float[] px, py, head, body, figure, pin;

    PuppetMesh(PortraitKind kind) {
        this.w = kind.paintW();
        this.h = kind.paintH();
        this.blocksX = kind.widthBlocks();
        this.blocksY = kind.heightBlocks();
        this.gx = blocksX > blocksY ? LONG_SIDE : SHORT_SIDE;
        this.gy = blocksY > blocksX ? LONG_SIDE : SHORT_SIDE;
        this.count = gx * gy;
        this.neckX = kind.neckX();
        this.neckY = kind.neckY();
        this.midX = kind.torso().cx(); // the centre line the body breathes about
        this.px = new float[count];
        this.py = new float[count];
        this.head = new float[count];
        this.body = new float[count];
        this.figure = new float[count];
        this.pin = new float[count];
        PortraitKind.Ellipse he = kind.head();
        PortraitKind.Ellipse to = kind.torso();
        for (int j = 0; j < gy; j++) {
            for (int i = 0; i < gx; i++) {
                int k = j * gx + i;
                float x = i / (float) (gx - 1) * w;
                float y = j / (float) (gy - 1) * h;
                px[k] = x;
                py[k] = y;
                head[k] = ellipse(x, y, he);
                body[k] = ellipse(x, y, to);
                figure[k] = Math.max(head[k], body[k]);
                pin[k] = smooth(0f, kind.edgePin(), Math.min(Math.min(x, w - x), Math.min(y, h - y)));
            }
        }
        // the face parts all sit inside the head's reach: rows below it need no overlay (2 rows of margin)
        float reach = Math.min(h, he.maxY());
        this.overlayRows = Math.min(gy - 1, (int) Math.ceil(reach / h * (gy - 1)) + 2);
    }

    static float smooth(float a, float b, float t) {
        t = Math.min(1f, Math.max(0f, (t - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    private static float ellipse(float x, float y, PortraitKind.Ellipse e) {
        float dx = (x - e.cx()) / e.rx();
        float dy = (y - e.cy()) / e.ry();
        return 1f - smooth(e.from(), e.to(), (float) Math.sqrt(dx * dx + dy * dy));
    }

    /** Where vertex k sits at rest (painting pixels), used for UVs and light tiles. */
    float u(int k) {
        return px[k] / w;
    }

    float v(int k) {
        return py[k] / h;
    }

    /** Fills outX/outY (length {@link #count}) with the deformed vertex positions in canvas blocks. */
    void deform(PortraitAnim s, float[] outX, float[] outY) {
        for (int k = 0; k < count; k++) {
            float ox = px[k], oy = py[k];
            float x = ox, y = oy;
            float wh = head[k], wb = body[k], wf = figure[k], pn = pin[k];

            // breathing: the chest swells upward from the lap
            float br = s.breath * wb;
            y = h - (h - y) * (1f + 0.009f * br);
            x = midX + (x - midX) * (1f + 0.005f * br);

            // head: bend about the neck, plus a small slide
            if (wh > 0f) {
                float a = s.rot * wh;
                float c = (float) Math.cos(a), sn = (float) Math.sin(a);
                float dx = x - neckX, dy = y - neckY;
                x = neckX + dx * c - dy * sn + s.hx * wh;
                y = neckY + dx * sn + dy * c + s.hy * wh;
            }

            // flinch: shrink back into the chair and jump up a little
            float f = s.flinch * wf;
            x = midX + (x - midX) * (1f - 0.04f * f);
            y = h - (h - y) * (1f - 0.03f * f) - 16f * f;

            // the canvas edges never move: no gaps at the frame
            x = ox + (x - ox) * pn;
            y = oy + (y - oy) * pn;

            outX[k] = (x / w - 0.5f) * blocksX;
            outY[k] = (0.5f - y / h) * blocksY;
        }
    }
}
