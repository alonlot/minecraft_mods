package com.hogwartsmagic.pensieve.scene;

/**
 * The crowd's geometry: each spectator of a {@link Crowd} as boxes and quads, posed for one moment, written vertex by
 * vertex to a {@link Sink}. Pure (no Minecraft classes) so the vertex budget and the winding are tested; the client's
 * {@code CrowdRenderer} sinks it into a vertex buffer. Three levels of detail by distance from the camera:
 * <ul>
 *   <li><b>full</b> (out to the full cut-off, at most {@link #MAX_FULL}): head, torso, two arms, thighs and shins
 *       folded on the seat or straightened to stand, a scarf, a flag or banner on a pole; the head turns to the play;</li>
 *   <li><b>two-box</b> (out to the mid cut-off, at most {@link #MAX_MID}): the body and the head, plus the arms while
 *       they are up and a flag's cloth;</li>
 *   <li><b>strip</b> (beyond): flat quads facing the pitch, body and head, with a band for arms thrown up.</li>
 * </ul>
 * Coordinates are scene-relative (the renderer offsets them by the scene origin less the camera). Faces are wound
 * counter-clockwise seen from outside and shaded like blocks (tops brightest). Nothing is allocated per figure: the
 * matrices and colours are this object's scratch arrays, so one mesh builder serves one thread.
 */
public final class CrowdMesh {
    /** Receives the mesh: {@code figure} before each spectator's vertices, then four vertices per quad. */
    public interface Sink {
        void figure(int index);

        void vertex(float x, float y, float z, int argb);
    }

    /** The level-of-detail ranges (blocks) and caps. */
    public static final float FULL_RANGE = 40f, MID_RANGE = 120f;
    public static final int MAX_FULL = 400, MAX_MID = 2000;
    /** The most vertices one spectator may take at each level. */
    public static final int FULL_MAX_VERTS = 9 * 24, MID_MAX_VERTS = 2 * 20 + 2 * 24 + 24, STRIP_MAX_VERTS = 16;
    /** The most vertices a frame's crowd may take: the caps filled at each level, the rest as strips. */
    public static final int VERTEX_BUDGET = MAX_FULL * FULL_MAX_VERTS + MAX_MID * MID_MAX_VERTS
            + (Crowd.MAX_SPECTATORS - MAX_FULL - MAX_MID) * STRIP_MAX_VERTS;

    /** Levels of detail. */
    public static final int FULL = 0, MID = 1, STRIP = 2;

    /** House colours, {first, second} per house (0 Gryffindor, 1 Hufflepuff, 2 Ravenclaw, 3 Slytherin): bright enough to read in a storm. */
    static final int[][] HOUSE = {{0x9E1B1B, 0xDDAA3A}, {0xE8BA30, 0x2E2824}, {0x2A4A9E, 0xB0824A}, {0x1F7340, 0xB8BCC2}};
    static final int[] SKIN = {0xF1C9A5, 0xE0AC88, 0xC68863, 0xA86B4A, 0x7D4B32, 0x5A3524};
    static final int[] HAIR = {0x1E1A17, 0x3B2618, 0x6A4426, 0x8A3B1F, 0xC9A25E, 0xB0541E};
    static final int ROBE_BLACK = 0x26262C, TROUSERS = 0x34343C, SHOES = 0x1A1716, POLE = 0x6B4A2B;

    private final float[] pose = new float[Crowd.CHANNELS];
    private final float[] yawM = new float[9], torsoM = new float[9], partM = new float[9], headM = new float[9];
    private final float[] p = new float[3], q = new float[3];
    private final int[] faces = new int[6];
    private int count;

    /** Vertices written by the last {@link #build}. */
    public int vertices() {
        return count;
    }

    /**
     * Write the spectators {@code chunk} of {@code crowd} at scene time {@code t}: {@code cam} is the camera and
     * {@code focus} the play the heads turn to (both scene-relative), {@code full2} and {@code mid2} the squared
     * cut-offs ({@link Crowd#lodCutoffs}). Returns the vertex count.
     */
    public int build(Crowd crowd, int[] chunk, float t, float camX, float camY, float camZ, float focusX, float focusY,
                     float focusZ, float full2, float mid2, Sink sink) {
        count = 0;
        for (int i : chunk) {
            float dx = crowd.x(i) - camX, dy = crowd.y(i) + 1f - camY, dz = crowd.z(i) - camZ;
            float d2 = dx * dx + dy * dy + dz * dz;
            int lod = d2 <= full2 ? FULL : d2 <= mid2 ? MID : STRIP;
            figure(crowd, i, t, lod, focusX, focusY, focusZ, sink);
        }
        return count;
    }

    /** Spectator {@code i} at level {@code lod}. */
    public void figure(Crowd c, int i, float t, int lod, float fx, float fy, float fz, Sink sink) {
        sink.figure(i);
        // Beyond full detail the idle pose holds still (and so do flags), so an idle far stand is never rebuilt.
        c.pose(i, t, pose, 0, lod != FULL);
        float sc = c.scale(i);
        float s = pose[Crowd.STAND];
        float seatTop = c.standing(i) ? c.y(i) + 0.5f : c.y(i);
        float px = c.x(i), pz = c.z(i);
        float pelvisY = seatTop + s * (0.75f * sc - 0.5f) + pose[Crowd.JUMP];
        float yawDeg = c.yaw(i);
        setYaw(yawM, yawDeg);
        // The pelvis moves forward off the seat as they stand.
        float fwd = 0.15f * s * sc;
        float ox = px + yawM[6] * fwd, oz = pz + yawM[8] * fwd;
        int h = c.house(i);
        int first = HOUSE[h][0], second = HOUSE[h][1];
        int outfit = c.outfit(i);
        int body = outfit == Crowd.JUMPER ? first : outfit == Crowd.ROBE ? ROBE_BLACK : second;
        int skin = SKIN[c.skin(i)], hair = HAIR[c.hair(i)];
        int gear = c.gear(i);
        float raiseL = pose[Crowd.ARM_L], raiseR = pose[Crowd.ARM_R], hands = pose[Crowd.HANDS_HEAD];
        if (lod == STRIP) {
            strip(ox, pelvisY, oz, seatTop, s, sc, body, skin, hair, Math.max(raiseL, raiseR), sink);
            return;
        }
        if (lod == MID) {
            float bottom = s > 0.5f ? pelvisY - 0.75f * sc : seatTop - 0.45f;
            fill(faces, body);
            box(yawM, ox, 0, oz, -0.27f * sc, bottom, -0.14f * sc, 0.27f * sc, pelvisY + 0.75f * sc,
                    (0.14f + 0.3f * (1 - s)) * sc, faces, 0b110111, sink);
            headFaces(skin, hair);
            box(yawM, ox, pelvisY + 0.75f * sc, oz, -0.25f * sc, 0, -0.25f * sc, 0.25f * sc, 0.5f * sc, 0.25f * sc, faces,
                    0b110111, sink);
            copy(yawM, torsoM);
            if (Math.max(Math.max(raiseL, raiseR), hands) > 0.3f) {
                arm(c, i, ox, pelvisY, oz, sc, 1, raiseL, hands, body, skin, sink);
                arm(c, i, ox, pelvisY, oz, sc, -1, raiseR, hands, body, skin, sink);
            }
            if ((gear & (Crowd.FLAG | Crowd.BANNER)) != 0) {
                handPos(ox, pelvisY, oz, sc, -1, raiseR, hands);
                flag(c, i, t, sc, gear, first, second, false, false, sink);
            }
            return;
        }
        // Full figure. The torso leans and sways about the pelvis.
        copy(yawM, torsoM);
        rotX(torsoM, pose[Crowd.LEAN]);
        rotZ(torsoM, pose[Crowd.SWAY]);
        fill(faces, body);
        box(torsoM, ox, pelvisY, oz, -0.25f * sc, 0, -0.125f * sc, 0.25f * sc, 0.75f * sc, 0.125f * sc, faces, 0b111111, sink);
        // Legs: thighs folded forward on the seat, straight down standing; shins hang from the knee.
        int legs = outfit == Crowd.ROBE ? ROBE_BLACK : TROUSERS;
        copy(yawM, partM);
        rotX(partM, -(1 - s) * (float) (Math.PI / 2));
        fill(faces, legs);
        box(partM, ox, pelvisY, oz, -0.24f * sc, -0.375f * sc, -0.125f * sc, 0.24f * sc, 0, 0.125f * sc, faces, 0b111111, sink);
        float kx = ox + partM[3] * -0.375f * sc, ky = pelvisY + partM[4] * -0.375f * sc, kz = oz + partM[5] * -0.375f * sc;
        float shin = 0.48f + (0.375f * sc - 0.48f) * s;
        fill(faces, legs);
        faces[3] = SHOES;
        box(yawM, kx, ky, kz, -0.24f * sc, -shin, -0.125f * sc, 0.24f * sc, 0, 0.125f * sc, faces, 0b111111, sink);
        // The head turns toward the play, as far as a neck turns.
        float nx = ox + torsoM[3] * 0.75f * sc, ny = pelvisY + torsoM[4] * 0.75f * sc, nz = oz + torsoM[5] * 0.75f * sc;
        float ey = ny + 0.25f * sc;
        double want = Math.toDegrees(Math.atan2(-(fx - nx), fz - nz));
        float rel = (float) wrap(want - yawDeg) + 8f * (float) Math.sin(c.phase(i) * 3.0);
        rel = Math.max(-70f, Math.min(70f, rel));
        double hd = Math.sqrt((fx - nx) * (fx - nx) + (fz - nz) * (fz - nz));
        float pitch = (float) Math.atan2(ey - fy, Math.max(1e-3, hd));
        pitch = Math.max(-0.6f, Math.min(0.45f, pitch)) - 0.3f * hands;
        setYaw(headM, yawDeg + rel);
        rotX(headM, pitch);
        headFaces(skin, hair);
        box(headM, nx, ny, nz, -0.25f * sc, 0, -0.25f * sc, 0.25f * sc, 0.5f * sc, 0.25f * sc, faces, 0b111111, sink);
        arm(c, i, ox, pelvisY, oz, sc, 1, raiseL, hands, body, skin, sink);
        arm(c, i, ox, pelvisY, oz, sc, -1, raiseR, hands, body, skin, sink);
        if ((gear & Crowd.SCARF) != 0) {
            fill(faces, first);
            box(torsoM, ox, pelvisY, oz, -0.27f * sc, 0.6f * sc, -0.15f * sc, 0.27f * sc, 0.74f * sc, 0.15f * sc, faces,
                    0b111111, sink);
            fill(faces, second);
            box(torsoM, ox, pelvisY, oz, 0.04f * sc, 0.22f * sc, 0.125f * sc, 0.15f * sc, 0.6f * sc, 0.165f * sc, faces,
                    0b111111, sink);
            float wave = pose[Crowd.WAVE];
            if (wave > 0.05f) {
                // The scarf held up in the right hand, swinging.
                armMatrix(-1, raiseR, hands);
                rotZ(partM, 0.35f * (float) Math.sin(t * 0.9f + c.phase(i)));
                shoulder(ox, pelvisY, oz, sc, -1);
                fill(faces, first);
                box(partM, p[0], p[1], p[2], -0.06f * sc, -(0.6f + 0.7f * wave) * sc, -0.02f * sc, 0.06f * sc,
                        -0.6f * sc, 0.02f * sc, faces, 0b111111, sink);
            }
        }
        if ((gear & (Crowd.FLAG | Crowd.BANNER)) != 0) {
            handPos(ox, pelvisY, oz, sc, -1, raiseR, hands);
            flag(c, i, t, sc, gear, first, second, true, true, sink);
        }
    }

    /** A strip figure: flat quads facing the pitch (the body's front plane, the face under a hair band), the arms a band above the head. */
    private void strip(float ox, float pelvisY, float oz, float seatTop, float s, float sc, int body, int skin, int hair,
                       float raise, Sink sink) {
        float bottom = s > 0.5f ? pelvisY - 0.75f * sc : seatTop - 0.45f;
        float top = pelvisY + 0.75f * sc;
        float zf = 0.14f * sc;
        quad(yawM, ox, oz, -0.27f * sc, bottom, 0.27f * sc, top, zf, shade(body, 0.85f), sink);
        quad(yawM, ox, oz, -0.25f * sc, top, 0.25f * sc, top + 0.34f * sc, zf, shade(skin, 0.85f), sink);
        quad(yawM, ox, oz, -0.25f * sc, top + 0.34f * sc, 0.25f * sc, top + 0.5f * sc, zf, shade(hair, 0.9f), sink);
        if (raise > 0.5f) {
            quad(yawM, ox, oz, -0.36f * sc, top + 0.45f * sc, 0.36f * sc, top + (0.45f + 0.45f * raise) * sc, zf - 0.01f,
                    shade(body, 0.8f), sink);
        }
    }

    /** One arm: side +1 the left (local +x), -1 the right. */
    private void arm(Crowd c, int i, float ox, float pelvisY, float oz, float sc, int side, float raise, float hands,
                     int sleeve, int skin, Sink sink) {
        armMatrix(side, raise, hands);
        shoulder(ox, pelvisY, oz, sc, side);
        fill(faces, sleeve);
        faces[3] = skin;
        box(partM, p[0], p[1], p[2], -0.125f * sc, -0.625f * sc, -0.125f * sc, 0.125f * sc, 0.125f * sc, 0.125f * sc, faces,
                0b111111, sink);
    }

    /** {@link #partM} = the torso frame turned for an arm: raised forward and up, splayed out (or in to the head). */
    private void armMatrix(int side, float raise, float hands) {
        float angle = 0.35f + 2.6f * raise;
        angle += (2.6f - angle) * hands;
        float splay = 0.08f + 0.24f * raise;
        splay += (-0.45f - splay) * hands;
        copy(torsoM, partM);
        rotZ(partM, side * splay * (float) Math.cos(angle));
        rotX(partM, -angle);
    }

    /** {@link #p} = the shoulder pivot of {@code side}, in the torso frame. */
    private void shoulder(float ox, float pelvisY, float oz, float sc, int side) {
        float lx = side * 0.375f * sc, ly = 0.625f * sc;
        p[0] = ox + torsoM[0] * lx + torsoM[3] * ly;
        p[1] = pelvisY + torsoM[1] * lx + torsoM[4] * ly;
        p[2] = oz + torsoM[2] * lx + torsoM[5] * ly;
    }

    /** {@link #q} = the hand of {@code side} (the arm frame left in {@link #partM}). */
    private void handPos(float ox, float pelvisY, float oz, float sc, int side, float raise, float hands) {
        armMatrix(side, raise, hands);
        shoulder(ox, pelvisY, oz, sc, side);
        float d = -0.6f * sc;
        q[0] = p[0] + partM[3] * d;
        q[1] = p[1] + partM[4] * d;
        q[2] = p[2] + partM[5] * d;
    }

    /** A flag or banner on a pole standing up from the hand at {@link #q}; {@code pole} false draws the cloth only. */
    private void flag(Crowd c, int i, float t, float sc, int gear, int first, int second, boolean pole, boolean flutter,
                      Sink sink) {
        boolean banner = (gear & Crowd.BANNER) != 0;
        float hp = (banner ? 1.3f : 1.0f) * sc, w = (banner ? 1.1f : 0.55f) * sc, hc = (banner ? 0.6f : 0.36f) * sc;
        copy(yawM, partM);
        rotZ(partM, 0.12f);
        if (pole) {
            fill(faces, POLE);
            box(partM, q[0], q[1], q[2], -0.03f * sc, 0, -0.03f * sc, 0.03f * sc, hp, 0.03f * sc, faces, 0b111111, sink);
        }
        rotY(partM, flutter ? 0.35f * (float) Math.sin(t * 0.45f + c.phase(i) * 2f) : 0.35f * (float) Math.sin(c.phase(i) * 2f));
        fill(faces, banner ? second : first);
        box(partM, q[0], q[1], q[2], 0.03f * sc, hp - hc, -0.015f * sc, 0.03f * sc + w, hp, 0.015f * sc, faces, 0b111111, sink);
    }

    private void headFaces(int skin, int hair) {
        fill(faces, skin);
        faces[2] = hair;            // top
        faces[5] = hair;            // back
    }

    // ------------------------------------------------------------------ boxes and quads

    /** Face order: +x, -x, +y, -y, +z, -z (bit i of {@code mask} draws face i). */
    private static final int[][] FACE = {
            {1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1},
            {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
            {0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
            {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
            {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1},
            {0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0}};

    /**
     * A box {@code [x0..x1] x [y0..y1] x [z0..z1]} in the frame {@code m} (an orthonormal basis, columns x, y, z) at
     * {@code (ox, oy, oz)}, coloured per face from {@code colours}; {@code mask} picks the faces (the low six bits).
     */
    void box(float[] m, float ox, float oy, float oz, float x0, float y0, float z0, float x1, float y1, float z1,
             int[] colours, int mask, Sink sink) {
        for (int f = 0; f < 6; f++) {
            if ((mask & (1 << f)) == 0) continue;
            int[] c = FACE[f];
            // The face's world normal: plus or minus one basis column.
            int axis = f >> 1;
            float sign = (f & 1) == 0 ? 1f : -1f;
            float nxw = m[axis * 3] * sign, nyw = m[axis * 3 + 1] * sign;
            int argb = shade(colours[f], 0.75f + 0.25f * nyw - 0.06f * Math.abs(nxw));
            for (int v = 0; v < 4; v++) {
                float lx = c[v * 3] == 0 ? x0 : x1, ly = c[v * 3 + 1] == 0 ? y0 : y1, lz = c[v * 3 + 2] == 0 ? z0 : z1;
                sink.vertex(ox + m[0] * lx + m[3] * ly + m[6] * lz, oy + m[1] * lx + m[4] * ly + m[7] * lz,
                        oz + m[2] * lx + m[5] * ly + m[8] * lz, argb);
            }
            count += 4;
        }
    }

    /** A quad in the plane {@code z = zf} of the yaw frame, facing +z, from {@code (x0, y0)} to {@code (x1, y1)} (y absolute). */
    private void quad(float[] m, float ox, float oz, float x0, float y0, float x1, float y1, float zf, int argb, Sink sink) {
        float ax = ox + m[0] * x0 + m[6] * zf, az = oz + m[2] * x0 + m[8] * zf;
        float bx = ox + m[0] * x1 + m[6] * zf, bz = oz + m[2] * x1 + m[8] * zf;
        sink.vertex(ax, y0, az, argb);
        sink.vertex(bx, y0, bz, argb);
        sink.vertex(bx, y1, bz, argb);
        sink.vertex(ax, y1, az, argb);
        count += 4;
    }

    static int shade(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * k));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * k));
        int b = Math.min(255, Math.round((rgb & 255) * k));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void fill(int[] a, int v) {
        for (int i = 0; i < a.length; i++) a[i] = v;
    }

    private static double wrap(double deg) {
        double d = deg % 360.0;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    // ------------------------------------------------------------------ 3x3 frames (column-major: x, y, z columns)

    /** {@code m} = the yaw frame: x the spectator's left, y up, z the way they face (Minecraft yaw, degrees). */
    static void setYaw(float[] m, float yawDeg) {
        double a = Math.toRadians(yawDeg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        m[0] = c;
        m[1] = 0;
        m[2] = s;
        m[3] = 0;
        m[4] = 1;
        m[5] = 0;
        m[6] = -s;
        m[7] = 0;
        m[8] = c;
    }

    /** {@code m = m * Rx(a)}: a positive angle tips the frame's y toward its z (a lean forward, a look down). */
    static void rotX(float[] m, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        for (int r = 0; r < 3; r++) {
            float y = m[3 + r], z = m[6 + r];
            m[3 + r] = y * c + z * s;
            m[6 + r] = -y * s + z * c;
        }
    }

    /** {@code m = m * Rz(a)}: a positive angle tips the frame's x toward its y. */
    static void rotZ(float[] m, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        for (int r = 0; r < 3; r++) {
            float x = m[r], y = m[3 + r];
            m[r] = x * c + y * s;
            m[3 + r] = -x * s + y * c;
        }
    }

    /** {@code m = m * Ry(a)}: a positive angle turns the frame's z toward its x. */
    static void rotY(float[] m, float a) {
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        for (int r = 0; r < 3; r++) {
            float x = m[r], z = m[6 + r];
            m[r] = x * c - z * s;
            m[6 + r] = x * s + z * c;
        }
    }

    private static void copy(float[] from, float[] to) {
        System.arraycopy(from, 0, to, 0, 9);
    }
}
