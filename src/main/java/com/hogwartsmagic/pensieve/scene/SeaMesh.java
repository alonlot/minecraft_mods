package com.hogwartsmagic.pensieve.scene;

/**
 * The {@code rough_sea} surface mesh for one frame, built round the camera with distance levels of detail, shaded on the
 * CPU. Pure (no Minecraft or client types) so its vertex budget, its seams and its allocations are unit tested
 * ({@code SeaMeshTest}); the client only copies the result into a vertex buffer ({@code client/pensieve/RoughSeaRenderer}).
 *
 * <p><b>Levels.</b> Five square rings centred on the camera: level L has spacing {@code 0.5 * 2^L} (0.5, 1, 2, 4, 8 blocks)
 * and half-size {@code 8 * 2^L} (8 to 128 blocks), 32 cells across; level L skips the cells level L - 1 covers. Each
 * level's centre is snapped to the next level's spacing, so a ring's inner edge lies exactly on the finer ring's outer
 * rim. On that rim every other fine vertex sits between two coarse vertices, and takes the straight-line height between
 * them (the coarse quad's own edge), so there are no cracks. Everything is clipped to the sea's area (positions clamped to
 * the edge). At most 1024 + 4 x 768 = 4096 water quads (16,384 vertices sent as quads), plus a foam pass capped at
 * {@link #FOAM_QUAD_CAP} quads on the three finest levels, nearest first: under 20,000 vertices for any sea.
 *
 * <p><b>Boat patches.</b> A rowing boat rides the exact surface, so wherever a boat sits on a ring coarser than 1 block
 * (level 2 and out, 8 blocks and more from the camera), the coarse cells within {@code radius} of it (the hull plus a
 * margin, given by the caller) are replaced by a 1-block grid of the exact surface. Where a patched cell meets a cell
 * that is not patched (or another ring), its edge vertices take that coarse edge's straight-line height, as the rings'
 * rims do, so no crack opens; between two patched cells the heights are exact on both sides. At most
 * {@link #PATCH_QUAD_CAP} patch quads a frame, for all boats together (the vertex bound grows by that much only).
 *
 * <p><b>Allocation.</b> Every buffer is allocated once, in the constructor, and reused by every {@link #build}: a build
 * allocates nothing.
 *
 * <p><b>Shading</b> per vertex: a dark grey-green body, lighter and greener where light comes through a crest, a Fresnel
 * reflection of the storm sky (toward a flash colour with {@code flash}), a moon glint and a broad sheen off the wave
 * backs, the detail ripples in the shading normal fading out by 36 blocks, the foam whitening the crests; alpha 0.9 to 1
 * with the Fresnel term, fading to 0 over the last 4 blocks of the area.
 */
public final class SeaMesh {
    /** Where the water meets rock, 0 to 1, at a sea-local point (sampled from the set by the client; 0 in tests). */
    public interface Shore {
        float at(double lx, double lz);
    }

    public static final int LEVELS = 5;
    /** Cells across one level, the finest spacing and the finest half-size. */
    public static final int CELLS = 32;
    public static final double BASE_SPACING = 0.5, BASE_REACH = 8.0;
    /** Foam quads drawn at most (4 vertices each), on levels 0 to FOAM_LEVELS - 1. */
    public static final int FOAM_QUAD_CAP = 900, FOAM_LEVELS = 3;
    /** Levels sampled for shore foam (the fine ones only). */
    private static final int SHORE_LEVELS = 2;
    private static final double EDGE_FADE = 4.0, DETAIL_REACH = 36.0;

    private static final float[] DEEP = {0.020f, 0.042f, 0.046f}, CREST = {0.055f, 0.120f, 0.105f},
            SKY = {0.115f, 0.140f, 0.155f}, FLASH = {0.62f, 0.68f, 0.78f}, GLINT = {0.42f, 0.47f, 0.52f};
    /** The foam colour (whitening the body, and the foam pass's tint before the flash). */
    public static final float[] FOAM_TINT = {0.60f, 0.64f, 0.65f};

    private static final int POINTS = (CELLS + 1) * (CELLS + 1);
    /** The boat patches' grid spacing, the first level that gets patched, and the patch budget (quads and vertices). */
    public static final double PATCH_SPACING = 1.0;
    public static final int PATCH_MIN_LEVEL = 2, PATCH_QUAD_CAP = 1536, MAX_BOATS = 4;
    private static final int PATCH_VERTS = PATCH_QUAD_CAP * 9 / 4;
    private static final int VERTS = LEVELS * POINTS + PATCH_VERTS;

    // Per vertex, level-major (level L's vertices at L * POINTS).
    /** Sea-local x, the height above the still surface, sea-local z. */
    public final float[] x = new float[VERTS], y = new float[VERTS], z = new float[VERTS];
    public final float[] r = new float[VERTS], g = new float[VERTS], b = new float[VERTS];
    public final float[] alpha = new float[VERTS], foam = new float[VERTS];
    /** Water quads (the rings' and the boat patches'), four vertex indices each, and foam quads. */
    public final int[] quads = new int[(LEVELS * CELLS * CELLS + PATCH_QUAD_CAP) * 4];
    public final int[] foamQuads = new int[FOAM_QUAD_CAP * 4];
    public int quadCount, foamQuadCount, patchQuadCount;
    /** Per level, the cells a boat patch replaces (cell (i, j) of the level at j * CELLS + i). */
    private final boolean[][] patched = new boolean[LEVELS][CELLS * CELLS];
    private int patchVerts, patchBudget;

    // Per level: grid index of the first vertex, the number of cells each way, and the unclipped region (for holes).
    private final int[] i0 = new int[LEVELS], j0 = new int[LEVELS], nx = new int[LEVELS], nz = new int[LEVELS];
    private final int[] ri0 = new int[LEVELS], rj0 = new int[LEVELS];
    private final double[] grad = new double[2], slope = new double[2];

    // The frame being built.
    private RoughSea.Sea sea;
    private double in, clock, camX, camY, camZ, lightX, lightY, lightZ;
    private float flash;
    private long seed;

    public static double spacing(int level) {
        return BASE_SPACING * (1 << level);
    }

    /** The first vertex index of level L. */
    public static int offset(int level) {
        return level * POINTS;
    }

    public int i0(int level) {
        return i0[level];
    }

    public int j0(int level) {
        return j0[level];
    }

    public int nx(int level) {
        return nx[level];
    }

    public int nz(int level) {
        return nz[level];
    }

    /** The vertex index of grid point (i, j) of level L (i, j counted from that level's first vertex). */
    public int index(int level, int i, int j) {
        return level * POINTS + j * (nx[level] + 1) + i;
    }

    public int vertexCount() {
        return (quadCount + foamQuadCount) * 4;
    }

    /**
     * Build the mesh of {@code sea} at scene time {@code now} for a camera at the sea-local (camX, camY, camZ) (relative
     * to the still surface's centre); {@code flash} 0 to 1 brightens the sky reflection; {@code shore} may be null.
     */
    public void build(RoughSea.Sea sea, float now, double camX, double camY, double camZ, float flash, Shore shore) {
        build(sea, now, camX, camY, camZ, flash, shore, null, 0);
    }

    /**
     * As {@link #build(RoughSea.Sea, float, double, double, double, float, Shore)}, with {@code boatCount} boats on this
     * sea: {@code boats} holds sea-local {x, z, radius} for each, the radius being how far round it the water must be
     * exact (the hull's half-length plus a margin). At most {@link #MAX_BOATS} are patched.
     */
    public void build(RoughSea.Sea sea, float now, double camX, double camY, double camZ, float flash, Shore shore,
                      double[] boats, int boatCount) {
        this.sea = sea;
        this.in = sea.intensityAt(now);
        this.clock = sea.clock(now);
        this.seed = sea.seed();
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.flash = flash;
        double lx = -RoughSea.windX(seed) * 0.75, ly = 0.42, lz = -RoughSea.windZ(seed) * 0.75;
        double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
        lightX = lx / ll;
        lightY = ly / ll;
        lightZ = lz / ll;
        quadCount = 0;
        foamQuadCount = 0;
        patchQuadCount = 0;
        patchVerts = 0;
        patchBudget = PATCH_QUAD_CAP;
        for (boolean[] p : patched) java.util.Arrays.fill(p, false);
        if (in <= 0) return;
        double hx = sea.hx(), hz = sea.hz();
        for (int L = 0; L < LEVELS; L++) {
            double s = spacing(L), next = s * 2;
            // The centre snapped to the next level's spacing, so this level's rim lies on that level's grid lines.
            double cx = Math.round(camX / next) * next, cz = Math.round(camZ / next) * next;
            int half = CELLS / 2;
            ri0[L] = (int) Math.round(cx / s) - half;
            rj0[L] = (int) Math.round(cz / s) - half;
            int a0 = Math.max(ri0[L], (int) Math.floor(-hx / s)), a1 = Math.min(ri0[L] + CELLS, (int) Math.ceil(hx / s));
            int b0 = Math.max(rj0[L], (int) Math.floor(-hz / s)), b1 = Math.min(rj0[L] + CELLS, (int) Math.ceil(hz / s));
            i0[L] = a0;
            j0[L] = b0;
            nx[L] = Math.max(0, a1 - a0);
            nz[L] = Math.max(0, b1 - b0);
            vertices(L, shore);
        }
        for (int L = 0; L < LEVELS - 1; L++) stitch(L);
        int n = boats == null ? 0 : Math.min(boatCount, MAX_BOATS);
        for (int k = 0; k < n; k++) mark(boats[k * 3], boats[k * 3 + 1], boats[k * 3 + 2]);
        for (int L = 0; L < LEVELS; L++) cells(L);
        for (int L = PATCH_MIN_LEVEL; L < LEVELS; L++) patches(L);
    }

    /** Whether level L draws its cell (gi, gj) (global grid indices): inside its clipped grid and not the finer hole. */
    private boolean draws(int L, int gi, int gj) {
        if (gi < i0[L] || gj < j0[L] || gi >= i0[L] + nx[L] || gj >= j0[L] + nz[L]) return false;
        if (L == 0) return true;
        int hi0 = ri0[L - 1] / 2, hj0 = rj0[L - 1] / 2;
        return !(gi >= hi0 && gi < hi0 + CELLS / 2 && gj >= hj0 && gj < hj0 + CELLS / 2);
    }

    private boolean isPatched(int L, int gi, int gj) {
        return draws(L, gi, gj) && patched[L][(gj - j0[L]) * CELLS + (gi - i0[L])];
    }

    /** Mark the coarse cells within {@code radius} of the boat at sea-local (bx, bz) on every level that is coarse there. */
    private void mark(double bx, double bz, double radius) {
        for (int L = PATCH_MIN_LEVEL; L < LEVELS; L++) {
            double s = spacing(L);
            int per = (int) Math.round(s / PATCH_SPACING);
            int ga = (int) Math.floor((bx - radius) / s), gb = (int) Math.floor((bx + radius) / s);
            int ha = (int) Math.floor((bz - radius) / s), hb = (int) Math.floor((bz + radius) / s);
            for (int gj = ha; gj <= hb; gj++) {
                for (int gi = ga; gi <= gb; gi++) {
                    if (!draws(L, gi, gj)) continue;
                    int idx = (gj - j0[L]) * CELLS + (gi - i0[L]);
                    if (patched[L][idx] || patchBudget < per * per || patchVerts + (per + 1) * (per + 1) > PATCH_VERTS) continue;
                    patched[L][idx] = true;
                    patchBudget -= per * per;
                    patchVerts += (per + 1) * (per + 1);
                }
            }
        }
        // patchVerts is only the reservation here; patches() hands the vertices out again from 0.
    }

    /** The 1-block grids replacing level L's patched cells. */
    private void patches(int L) {
        if (L == PATCH_MIN_LEVEL) patchVerts = 0;
        double s = spacing(L), hx = sea.hx(), hz = sea.hz();
        int per = (int) Math.round(s / PATCH_SPACING);
        for (int cj = 0; cj < nz[L]; cj++) {
            for (int ci = 0; ci < nx[L]; ci++) {
                if (!patched[L][cj * CELLS + ci]) continue;
                int gi = i0[L] + ci, gj = j0[L] + cj;
                boolean west = isPatched(L, gi - 1, gj), east = isPatched(L, gi + 1, gj);
                boolean south = isPatched(L, gi, gj - 1), north = isPatched(L, gi, gj + 1);
                float c00 = y[index(L, ci, cj)], c10 = y[index(L, ci + 1, cj)];
                float c01 = y[index(L, ci, cj + 1)], c11 = y[index(L, ci + 1, cj + 1)];
                int base = LEVELS * POINTS + patchVerts;
                for (int b = 0; b <= per; b++) {
                    for (int a = 0; a <= per; a++) {
                        int k = base + b * (per + 1) + a;
                        double px = Math.max(-hx, Math.min(hx, (gi + a / (double) per) * s));
                        double pz = Math.max(-hz, Math.min(hz, (gj + b / (double) per) * s));
                        vertex(k, px, pz, null, false);
                        float fa = a / (float) per, fb = b / (float) per;
                        boolean edgeA = a == 0 || a == per, edgeB = b == 0 || b == per;
                        if (edgeA && edgeB) {
                            y[k] = a == 0 ? (b == 0 ? c00 : c01) : (b == 0 ? c10 : c11);
                        } else if (b == 0 && !south) {
                            y[k] = c00 + (c10 - c00) * fa;
                        } else if (b == per && !north) {
                            y[k] = c01 + (c11 - c01) * fa;
                        } else if (a == 0 && !west) {
                            y[k] = c00 + (c01 - c00) * fb;
                        } else if (a == per && !east) {
                            y[k] = c10 + (c11 - c10) * fb;
                        }
                    }
                }
                for (int b = 0; b < per; b++) {
                    for (int a = 0; a < per; a++) {
                        int v0 = base + b * (per + 1) + a;
                        addQuad(v0, v0 + 1, v0 + per + 2, v0 + per + 1, L < FOAM_LEVELS);
                        patchQuadCount++;
                    }
                }
                patchVerts += (per + 1) * (per + 1);
            }
        }
    }

    private void addQuad(int a, int bb, int c, int d, boolean foamOk) {
        if (alpha[a] + alpha[bb] + alpha[c] + alpha[d] <= 0f) return;
        int q = quadCount * 4;
        quads[q] = a;
        quads[q + 1] = bb;
        quads[q + 2] = c;
        quads[q + 3] = d;
        quadCount++;
        if (foamOk && foamQuadCount < FOAM_QUAD_CAP && foam[a] + foam[bb] + foam[c] + foam[d] >= 0.04f) {
            int f = foamQuadCount * 4;
            foamQuads[f] = a;
            foamQuads[f + 1] = bb;
            foamQuads[f + 2] = c;
            foamQuads[f + 3] = d;
            foamQuadCount++;
        }
    }

    private void vertices(int L, Shore shore) {
        if (nx[L] <= 0 || nz[L] <= 0) return;
        double s = spacing(L), hx = sea.hx(), hz = sea.hz();
        for (int j = 0; j <= nz[L]; j++) {
            for (int i = 0; i <= nx[L]; i++) {
                int k = index(L, i, j);
                double px = Math.max(-hx, Math.min(hx, (i0[L] + i) * s));
                double pz = Math.max(-hz, Math.min(hz, (j0[L] + j) * s));
                vertex(k, px, pz, shore, L < SHORE_LEVELS);
            }
        }
    }

    /** One vertex at sea-local (px, pz): the exact height, foam, shading and the edge fade. */
    private void vertex(int k, double px, double pz, Shore shore, boolean useShore) {
        double hx = sea.hx(), hz = sea.hz();
        double h1 = RoughSea.eval(px, pz, clock, seed, grad);
        double h = h1 * in;
        double fo = RoughSea.foamFrom(h1, grad[0], grad[1], px, pz, clock, seed, in);
        double dx = px - camX, dy = h - camY, dz = pz - camZ;
        double dist = Math.max(1e-4, Math.sqrt(dx * dx + dy * dy + dz * dz));
        slope[0] = grad[0] * in;
        slope[1] = grad[1] * in;
        RoughSea.addDetail(px, pz, clock, seed, in, Math.max(0, 1 - dist / DETAIL_REACH), slope);
        if (shore != null && useShore) {
            float sh = shore.at(px, pz);
            if (sh > 0) fo = Math.max(fo, sh * (0.55 + 0.45 * RoughSea.sin(px * 1.7 + pz * 1.3 - clock * 0.11)));
        }
        x[k] = (float) px;
        y[k] = (float) h;
        z[k] = (float) pz;
        foam[k] = (float) Math.min(1, fo);
        shade(k, slope[0], slope[1], h1, fo, dx, dy, dz, dist);
        double edge = Math.min(hx - Math.abs(px), hz - Math.abs(pz));
        double f = Math.max(0, Math.min(1, edge / EDGE_FADE));
        alpha[k] *= (float) (f * f * (3 - 2 * f));
    }

    private void shade(int k, double gx, double gz, double h1, double fo, double dx, double dy, double dz, double dist) {
        double nl = Math.sqrt(gx * gx + 1 + gz * gz);
        double nX = -gx / nl, nY = 1 / nl, nZ = -gz / nl;
        double vx = -dx / dist, vy = -dy / dist, vz = -dz / dist;
        double nv = nX * vx + nY * vy + nZ * vz;
        double ndv = Math.max(0, nv);
        double f1 = 1 - ndv, f2 = f1 * f1;
        double fresnel = 0.02 + 0.98 * f2 * f2 * f1;
        double rx = 2 * nv * nX - vx, ry = 2 * nv * nY - vy, rz = 2 * nv * nZ - vz;
        double rl = Math.max(0, rx * lightX + ry * lightY + rz * lightZ);
        double r2 = rl * rl, r4 = r2 * r2, r8 = r4 * r4, r16 = r8 * r8, r32 = r16 * r16;
        double glint = r32 * r16 * 0.9 + r4 * r2 * 0.10;
        double crest = Math.max(0, Math.min(1, (h1 / RoughSea.MAX_AMPLITUDE + 0.1) / 0.9));
        double white = Math.min(1, fo) * 0.35;
        double gl = glint * (1 - Math.min(1, fo)) * (1 + flash);
        r[k] = channel(0, crest, fresnel, white, gl);
        g[k] = channel(1, crest, fresnel, white, gl);
        b[k] = channel(2, crest, fresnel, white, gl);
        alpha[k] = (float) (0.90 + 0.10 * fresnel);
    }

    private float channel(int c, double crest, double fresnel, double white, double gl) {
        double sky = SKY[c] + (FLASH[c] - SKY[c]) * flash;
        double body = DEEP[c] + CREST[c] * crest;
        double v = body + (sky - body) * fresnel;
        v += (FOAM_TINT[c] - v) * white;
        return (float) (v + GLINT[c] * gl);
    }

    /**
     * Level L's outer rim takes level L + 1's straight-line heights where it lies between two of its vertices (every
     * other rim vertex, when the rim sits on the coarser grid), so the two levels meet with no crack.
     */
    private void stitch(int L) {
        if (nx[L] <= 0 || nz[L] <= 0) return;
        int n = nx[L], m = nz[L];
        for (int i = 0; i <= n; i++) {
            if (j0[L] == rj0[L]) rimMid(L, i, 0, true);
            if (j0[L] + m == rj0[L] + CELLS) rimMid(L, i, m, true);
        }
        for (int j = 0; j <= m; j++) {
            if (i0[L] == ri0[L]) rimMid(L, 0, j, false);
            if (i0[L] + n == ri0[L] + CELLS) rimMid(L, n, j, false);
        }
    }

    private void rimMid(int L, int i, int j, boolean alongX) {
        int along = alongX ? i0[L] + i : j0[L] + j;
        if ((along & 1) == 0) return;                       // on a coarse vertex: already the same point and height
        int last = alongX ? nx[L] : nz[L];
        int pos = alongX ? i : j;
        if (pos == 0 || pos == last) return;                 // a clipped corner at the area edge
        int a = alongX ? index(L, i - 1, j) : index(L, i, j - 1);
        int c = alongX ? index(L, i + 1, j) : index(L, i, j + 1);
        y[index(L, i, j)] = (y[a] + y[c]) * 0.5f;
    }

    private void cells(int L) {
        int n = nx[L], m = nz[L];
        if (n <= 0 || m <= 0) return;
        // The finer level's region in this level's grid indices (it is aligned to this grid).
        int hi0 = 0, hi1 = 0, hj0 = 0, hj1 = 0;
        boolean hole = L > 0;
        if (hole) {
            hi0 = ri0[L - 1] / 2;
            hj0 = rj0[L - 1] / 2;
            hi1 = hi0 + CELLS / 2;
            hj1 = hj0 + CELLS / 2;
        }
        for (int j = 0; j < m; j++) {
            for (int i = 0; i < n; i++) {
                int gi = i0[L] + i, gj = j0[L] + j;
                if (hole && gi >= hi0 && gi < hi1 && gj >= hj0 && gj < hj1) continue;
                if (patched[L][j * CELLS + i]) continue;          // replaced by a boat patch
                int a = index(L, i, j), bb = index(L, i + 1, j), c = index(L, i + 1, j + 1), d = index(L, i, j + 1);
                addQuad(a, bb, c, d, L < FOAM_LEVELS);
            }
        }
    }
}
