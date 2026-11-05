import com.hogwartsmagic.entity.WhompingWillowEntity.AttackState;
import com.hogwartsmagic.entity.WhompingWillowRig;

import java.util.Locale;

/** Simulates the entity's hit tests (same constants, same sub-steps) against a standing player. */
public class HitCheck {
    static final double LIMB_SLACK = 0.45, SLAM_SPLASH = 1.1, GRAB_REACH = 1.3;
    static final double HALF = 0.3, TALL = 1.8;

    public static void main(String[] a) {
        Locale.setDefault(Locale.ROOT);
        float yaw = 0f;
        System.out.println("rows: player distance; cols: bearing offset from limb 0 (deg). H=hit .=miss");
        for (String kind : new String[]{"SWEEP", "SLAM", "GRAB"}) {
            for (boolean dodge : new boolean[]{false, true}) {
                System.out.println(kind + (dodge ? "  (player steps 2.5 blocks sideways after the wind-up starts)" : "  (player stands still)"));
                StringBuilder head = new StringBuilder("        ");
                for (int off = -40; off <= 40; off += 10) head.append(String.format("%5d", off));
                System.out.println(head);
                for (double d = 2.5; d <= 9.01; d += 0.5) {
                    StringBuilder row = new StringBuilder(String.format("  %4.1f  ", d));
                    for (int off = -40; off <= 40; off += 10) {
                        double bearing = WhompingWillowRig.LIMB_BEARING[0] + off;
                        row.append("    ").append(trial(kind, yaw, d, bearing, dodge) ? 'H' : '.');
                    }
                    System.out.println(row);
                }
            }
        }
    }

    static boolean trial(String kind, float yaw, double d, double bearingDeg, boolean dodge) {
        double br = Math.toRadians(yaw + bearingDeg);
        double px = -Math.sin(br) * d, pz = Math.cos(br) * d;   // directionFromRotation(0, yaw)
        double sx = Math.cos(br), sz = Math.sin(br);
        // choose limb by nearest bearing, as branchFor does
        int best = 0; double bo = 999;
        for (int b = 0; b < 4; b++) {
            double o = Math.abs(wrap(bearingDeg - WhompingWillowRig.LIMB_BEARING[b]));
            if (o < bo) { bo = o; best = b; }
        }
        double offDeg = wrap(bearingDeg - WhompingWillowRig.LIMB_BEARING[best]);
        WhompingWillowRig.Input in = new WhompingWillowRig.Input();
        in.state = AttackState.valueOf(kind);
        in.active = best;
        in.sign = offDeg >= 0 ? 1 : -1;
        in.aimYaw = (float) Math.max(-WhompingWillowRig.MAX_AIM_YAW, Math.min(WhompingWillowRig.MAX_AIM_YAW, Math.toRadians(offDeg)));
        in.aimDist = (float) d;
        in.aimHeight = 0.6f;
        if (dodge) { px += sx * 2.5; pz += sz * 2.5; }
        float[] rot = new float[WhompingWillowRig.BONES * 3];
        double[] pts = new double[21];
        if (kind.equals("SWEEP")) {
            for (int t = WhompingWillowRig.SWEEP_HIT_START; t <= WhompingWillowRig.SWEEP_HIT_END; t++) {
                for (int sub = 2; sub >= 0; sub--) {
                    in.tick = t - sub / 3.0f;
                    WhompingWillowRig.pose(in, rot);
                    WhompingWillowRig.limbPoints(in, rot, best, yaw, pts);
                    if (gap(pts, 1, px, pz, best, LIMB_SLACK) <= 0) return true;
                }
            }
            return false;
        } else if (kind.equals("SLAM")) {
            in.tick = WhompingWillowRig.SLAM_STRIKE_TICK;
            WhompingWillowRig.pose(in, rot);
            WhompingWillowRig.limbPoints(in, rot, best, yaw, pts);
            return gap(pts, 2, px, pz, best, SLAM_SPLASH) <= 0;
        } else {
            for (int sub = 1; sub >= 0; sub--) {
                in.tick = WhompingWillowRig.GRAB_CATCH_TICK - sub * 0.5f;
                WhompingWillowRig.pose(in, rot);
                WhompingWillowRig.limbPoints(in, rot, best, yaw, pts);
                double tx = pts[18], ty = pts[19], tz = pts[20];
                double cy = Math.max(0, Math.min(TALL, ty));
                double g = Math.sqrt(sq(tx - px) + sq(tz - pz) + sq(ty - cy)) - HALF - GRAB_REACH;
                if (g <= 0) return true;
            }
            return false;
        }
    }

    static double gap(double[] p, int from, double px, double pz, int limb, double slack) {
        double bestG = 1e9;
        for (int k = from; k < 6; k++) {
            double dd = segSeg(p[k * 3], p[k * 3 + 1], p[k * 3 + 2], p[k * 3 + 3], p[k * 3 + 4], p[k * 3 + 5],
                    px, 0, pz, px, TALL, pz);
            bestG = Math.min(bestG, dd - WhompingWillowRig.limbRadius(limb, k) - HALF - slack);
        }
        return bestG;
    }

    static double wrap(double d) { d = d % 360; if (d >= 180) d -= 360; if (d < -180) d += 360; return d; }
    static double sq(double v) { return v * v; }
    static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }

    static double segSeg(double px, double py, double pz, double p1x, double p1y, double p1z,
                         double qx, double qy, double qz, double q1x, double q1y, double q1z) {
        double d1x = p1x - px, d1y = p1y - py, d1z = p1z - pz;
        double d2x = q1x - qx, d2y = q1y - qy, d2z = q1z - qz;
        double rx = px - qx, ry = py - qy, rz = pz - qz;
        double a = d1x * d1x + d1y * d1y + d1z * d1z, e = d2x * d2x + d2y * d2y + d2z * d2z;
        double f = d2x * rx + d2y * ry + d2z * rz;
        double s, t;
        double c = d1x * rx + d1y * ry + d1z * rz;
        double b = d1x * d2x + d1y * d2y + d1z * d2z;
        double denom = a * e - b * b;
        s = denom > 1e-9 ? clamp((b * f - c * e) / denom, 0, 1) : 0;
        t = (b * s + f) / e;
        if (t < 0) { t = 0; s = clamp(-c / a, 0, 1); } else if (t > 1) { t = 1; s = clamp((b - c) / a, 0, 1); }
        double dx = (px + d1x * s) - (qx + d2x * t), dy = (py + d1y * s) - (qy + d2y * t), dz = (pz + d1z * s) - (qz + d2z * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
