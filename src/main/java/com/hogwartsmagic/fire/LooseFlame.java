package com.hogwartsmagic.fire;

import com.hogwartsmagic.pensieve.scene.DragonFire;

/**
 * A loose puff of the dragon's fire: burning gas shed by something on fire rather than breathed down a jet (the
 * Fiendfyre serpent's body, the fire it leaves on the ground). It is born at a point with a velocity, swells, cools and
 * fades over its life, then leaves smoke that rolls up off where it burned out, drawn with the jet's own sprites and
 * colours. Times are the client's world clock in ticks. Pure: no Minecraft classes.
 */
public final class LooseFlame {
    /** How long a loose puff's smoke lasts. */
    public static final double SMOKE_TICKS = 40;

    /** One puff: where and when it was born, its velocity (blocks a tick), start radius, life, and its look. */
    public record Puff(double x, double y, double z, double vx, double vy, double vz, double r0, double tb, double life,
                       int sprite, double spin, double spinRate, double drift, boolean smokes) {
        /** Whether nothing of it, flame or smoke, is left at {@code now}. */
        public boolean gone(double now) {
            return now > tb + life + (smokes ? SMOKE_TICKS : 0);
        }
    }

    private LooseFlame() {
    }

    /** The puff's flame {@code a} ticks after its birth, or null outside its life. */
    public static DragonFire.Flame flame(Puff p, double a) {
        if (a < 0 || a > p.life()) return null;
        double x = p.x() + p.vx() * a, y = p.y() + p.vy() * a + 0.004 * a * a, z = p.z() + p.vz() * a;
        double r = p.r0() * (1 + 0.06 * a);
        double f = a / p.life();
        double heat = 0.95 - 0.7 * f;
        double alpha = Math.min(1, a * 2 + 0.2) * (1 - smooth((f - 0.55) / 0.45));
        return new DragonFire.Flame(x, y, z, r, heat, alpha, DragonFire.Flame.FLYING);
    }

    /** The puff's smoke {@code a} ticks after its birth, or null before its fire is out, after it is gone, or none. */
    public static DragonFire.Smoke smoke(Puff p, double a) {
        if (!p.smokes()) return null;
        double b = a - p.life() + 3;
        if (b < 0 || b > SMOKE_TICKS) return null;
        DragonFire.Flame end = flame(p, p.life());
        double up = 0.05 * b + 0.0011 * b * b;
        double drift = 0.018 * b;
        double r = end.radius() * 0.85 + 0.05 * b;
        double alpha = smooth(b / 5.0) * (1 - smooth((b - SMOKE_TICKS * 0.3) / (SMOKE_TICKS * 0.7)));
        return new DragonFire.Smoke(end.x() + Math.cos(p.drift()) * drift, end.y() + up,
                end.z() + Math.sin(p.drift()) * drift, r, alpha, smooth((b - 2) / 12.0));
    }

    private static double smooth(double x) {
        double c = Math.max(0, Math.min(1, x));
        return c * c * (3 - 2 * c);
    }
}
