package com.hogwartsmagic.entity;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * What each of Voldemort's moves looks like, drawn on every client from his synced action and its start tick
 * ({@link VoldemortMoves}), in step with the clip the model plays. Only {@code Level#addParticle}: common code,
 * nothing client-only, and nothing is sent over the network for any of it.
 */
final class VoldemortFx {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75F, 0.04F, 0.06F), 1.1F);
    private static final DustParticleOptions CRUCIO_RED = new DustParticleOptions(new Vector3f(0.95F, 0.12F, 0.1F), 0.8F);
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.18F, 0.05F, 0.22F), 1.4F);

    private VoldemortFx() {
    }

    /** One client tick of the action {@code action}, {@code e} ticks after it began. */
    static void action(VoldemortEntity v, int action, int e) {
        Level level = v.level();
        RandomSource r = v.getRandom();
        Vec3 feet = v.position();
        float yaw = v.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0.0D, -Mth.sin(yaw));
        Vec3 wand = feet.add(fwd.scale(0.75D)).add(right.scale(0.3D)).add(0.0D, 1.45D, 0.0D);
        switch (action) {
            case VoldemortMoves.ENTRANCE -> {
                if (e < 18) ring(level, feet.add(0, 0.1D, 0), 0.5D + e * 0.42D, 28, ParticleTypes.SQUID_INK, 0.04D);
                if (e > 20 && e < 50 && e % 2 == 0) {
                    level.addParticle(ParticleTypes.SOUL, feet.x + (r.nextDouble() - 0.5D) * 1.6D, feet.y + 0.2D,
                            feet.z + (r.nextDouble() - 0.5D) * 1.6D, 0.0D, 0.06D, 0.0D);
                }
            }
            case VoldemortMoves.CAST -> {
                if (e >= 3 && e <= 5) burst(level, wand, 6, ParticleTypes.SMOKE, 0.06D);
            }
            case VoldemortMoves.CRUCIO -> {
                if (e >= 5 && e <= 24) {
                    for (int i = 0; i < 3; i++) {
                        level.addParticle(CRUCIO_RED, wand.x + r.nextGaussian() * 0.12D, wand.y + r.nextGaussian() * 0.12D,
                                wand.z + r.nextGaussian() * 0.12D, 0.0D, 0.0D, 0.0D);
                    }
                    if (e % 2 == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, wand.x, wand.y, wand.z,
                            r.nextGaussian() * 0.1D, r.nextGaussian() * 0.1D, r.nextGaussian() * 0.1D);
                }
            }
            case VoldemortMoves.SLASH -> {
                for (int i = 0; i < VoldemortMoves.SLASH_TICKS.length; i++) {
                    if (e == VoldemortMoves.SLASH_TICKS[i] || e == VoldemortMoves.SLASH_TICKS[i] + 1) {
                        slashArc(level, feet, fwd, right, i, e == VoldemortMoves.SLASH_TICKS[i] ? 0.0D : 0.5D);
                    }
                }
            }
            case VoldemortMoves.FIENDFYRE -> {
                if (e < VoldemortMoves.FIRE_TICK) {
                    double lift = 0.9D + e * 0.05D;
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 hand = feet.add(right.scale(0.45D * side)).add(fwd.scale(0.15D)).add(0.0D, lift, 0.0D);
                        double a = e * 0.7D + side;
                        level.addParticle(ParticleTypes.FLAME, hand.x + Math.cos(a) * 0.3D, hand.y + 0.1D, hand.z + Math.sin(a) * 0.3D,
                                0.0D, 0.03D, 0.0D);
                        level.addParticle(ParticleTypes.SMALL_FLAME, hand.x + Math.cos(a + Math.PI) * 0.25D, hand.y,
                                hand.z + Math.sin(a + Math.PI) * 0.25D, 0.0D, 0.02D, 0.0D);
                        if (e % 3 == 0) level.addParticle(ParticleTypes.LAVA, hand.x, hand.y, hand.z, 0.0D, 0.0D, 0.0D);
                    }
                } else if (e == VoldemortMoves.FIRE_TICK) {
                    for (int i = 0; i < 24; i++) {
                        Vec3 d = fwd.scale(0.25D + r.nextDouble() * 0.3D).add(r.nextGaussian() * 0.08D, r.nextGaussian() * 0.08D, r.nextGaussian() * 0.08D);
                        level.addParticle(ParticleTypes.FLAME, wand.x, wand.y, wand.z, d.x, d.y, d.z);
                    }
                }
            }
            case VoldemortMoves.SHOCKWAVE -> {
                if (e < VoldemortMoves.SLAM_TICK) {
                    // The dark drawn in: smoke from all round falls into him, soul fire climbs his robe.
                    for (int i = 0; i < 4; i++) {
                        double a = r.nextDouble() * Math.PI * 2.0D;
                        double rad = 3.0D + r.nextDouble();
                        Vec3 p = feet.add(Math.cos(a) * rad, 0.3D + r.nextDouble() * 1.8D, Math.sin(a) * rad);
                        Vec3 vel = feet.add(0, 1.2D, 0).subtract(p).scale(0.09D);
                        level.addParticle(ParticleTypes.SQUID_INK, p.x, p.y, p.z, vel.x, vel.y, vel.z);
                    }
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, feet.x + r.nextGaussian() * 0.3D, feet.y + 0.1D,
                            feet.z + r.nextGaussian() * 0.3D, 0.0D, 0.08D + e * 0.004D, 0.0D);
                } else if (e < VoldemortMoves.SLAM_TICK + 14) {
                    int k = e - VoldemortMoves.SLAM_TICK;
                    if (k == 0) {
                        level.addParticle(ParticleTypes.EXPLOSION_EMITTER, feet.x, feet.y + 0.2D, feet.z, 0.0D, 0.0D, 0.0D);
                    }
                    double rad = 0.8D + k * 0.6D;
                    ring(level, feet.add(0, 0.15D, 0), rad, 40, ParticleTypes.SQUID_INK, 0.08D);
                    ring(level, feet.add(0, 0.4D, 0), rad * 0.97D, 20, ParticleTypes.SOUL_FIRE_FLAME, 0.02D);
                    if (k % 2 == 0) ring(level, feet.add(0, 0.2D, 0), rad, 14, ParticleTypes.LARGE_SMOKE, 0.03D);
                }
            }
            case VoldemortMoves.STRIKE -> {
                if (e > 6 && e < 24) {
                    Vec3 tip = feet.add(right.scale(0.35D)).add(0.0D, 2.5D, 0.0D);
                    for (int i = 0; i < 3; i++) {
                        double h = r.nextDouble() * 4.0D;
                        level.addParticle(DARK, tip.x + r.nextGaussian() * 0.05D, tip.y + h, tip.z + r.nextGaussian() * 0.05D, 0.0D, 0.15D, 0.0D);
                    }
                    if (e % 3 == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y, tip.z, 0.0D, 0.2D, 0.0D);
                }
            }
            case VoldemortMoves.PHASE -> {
                if (e < VoldemortMoves.PHASE_BLAST_TICK) {
                    for (int i = 0; i < 3; i++) {
                        double a = e * 0.35D + i * Math.PI * 2.0D / 3.0D;
                        double y = (e * 0.07D + i * 0.6D) % 3.2D;
                        level.addParticle(ParticleTypes.SQUID_INK, feet.x + Math.cos(a) * 1.3D, feet.y + y, feet.z + Math.sin(a) * 1.3D,
                                0.0D, 0.02D, 0.0D);
                    }
                    level.addParticle(ParticleTypes.SOUL, feet.x + r.nextGaussian() * 0.4D, feet.y + 0.5D, feet.z + r.nextGaussian() * 0.4D,
                            0.0D, 0.1D, 0.0D);
                    if (e % 2 == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, feet.x + r.nextGaussian() * 0.5D,
                            feet.y + 1.0D + r.nextDouble() * 1.2D, feet.z + r.nextGaussian() * 0.5D, 0.0D, 0.0D, 0.0D);
                } else if (e < VoldemortMoves.PHASE_BLAST_TICK + 16) {
                    int k = e - VoldemortMoves.PHASE_BLAST_TICK;
                    if (k == 0) level.addParticle(ParticleTypes.EXPLOSION_EMITTER, feet.x, feet.y + 1.0D, feet.z, 0.0D, 0.0D, 0.0D);
                    double rad = 1.0D + k * 0.65D;
                    ring(level, feet.add(0, 1.0D, 0), rad, 44, ParticleTypes.SQUID_INK, 0.08D);
                    ring(level, feet.add(0, 1.2D, 0), rad, 22, ParticleTypes.SOUL_FIRE_FLAME, 0.02D);
                }
            }
            default -> {
            }
        }
    }

    /** Coming apart: ash and flakes of black lifting off him, thicker as it goes, and a last gust at the end. */
    static void death(VoldemortEntity v, int deathTime, int total) {
        Level level = v.level();
        RandomSource r = v.getRandom();
        int n = 2 + deathTime / 5;
        for (int i = 0; i < n; i++) {
            double x = v.getX() + (r.nextDouble() - 0.5D) * 0.7D;
            double y = v.getY() + r.nextDouble() * 2.0D;
            double z = v.getZ() + (r.nextDouble() - 0.5D) * 0.7D;
            ParticleOptions p = i % 3 == 0 ? ParticleTypes.SQUID_INK : (i % 3 == 1 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH);
            level.addParticle(p, x, y, z, r.nextGaussian() * 0.02D, 0.04D + r.nextDouble() * 0.05D, r.nextGaussian() * 0.02D);
        }
        if (deathTime == total - 4) {
            for (int i = 0; i < 70; i++) {
                level.addParticle(i % 2 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE,
                        v.getX() + r.nextGaussian() * 0.3D, v.getY() + 0.2D + r.nextDouble() * 1.8D, v.getZ() + r.nextGaussian() * 0.3D,
                        r.nextGaussian() * 0.15D, 0.05D + r.nextDouble() * 0.15D, r.nextGaussian() * 0.15D);
            }
        }
    }

    private static void ring(Level level, Vec3 c, double radius, int n, ParticleOptions p, double out) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2.0D / n;
            double cx = Math.cos(a), cz = Math.sin(a);
            level.addParticle(p, c.x + cx * radius, c.y, c.z + cz * radius, cx * out, 0.01D, cz * out);
        }
    }

    private static void burst(Level level, Vec3 c, int n, ParticleOptions p, double speed) {
        RandomSource r = RandomSource.create();
        for (int i = 0; i < n; i++) {
            level.addParticle(p, c.x, c.y, c.z, r.nextGaussian() * speed, r.nextGaussian() * speed, r.nextGaussian() * speed);
        }
    }

    /** One Sectumsempra stroke as a crimson arc in the air before him: down-left, down-right, then straight down. */
    private static void slashArc(Level level, Vec3 feet, Vec3 fwd, Vec3 right, int stroke, double phase) {
        for (int i = 0; i <= 12; i++) {
            double s = (i + phase) / 12.0D;
            double side;
            double h;
            if (stroke == 0) {
                side = Mth.lerp(s, -0.9D, 0.9D);
                h = Mth.lerp(s, 2.3D, 0.8D);
            } else if (stroke == 1) {
                side = Mth.lerp(s, 0.9D, -0.9D);
                h = Mth.lerp(s, 2.3D, 0.8D);
            } else {
                side = 0.1D * Math.sin(s * Math.PI);
                h = Mth.lerp(s, 2.6D, 0.6D);
            }
            double reach = 1.0D + 0.3D * Math.sin(s * Math.PI);
            Vec3 p = feet.add(fwd.scale(reach)).add(right.scale(side)).add(0.0D, h, 0.0D);
            level.addParticle(BLOOD, p.x, p.y, p.z, fwd.x * 0.05D, 0.0D, fwd.z * 0.05D);
        }
    }
}
