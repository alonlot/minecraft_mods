package com.hogwartsmagic.util;

import com.hogwartsmagic.MagicParticleOptions;
import com.hogwartsmagic.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;

/**
 * Ready-made colours for the mod's magic particles, and a few shapes to throw them in. Replaces the
 * vanilla stand-ins (END_ROD, ENCHANT, WITCH, HAPPY_VILLAGER, CRIT, plain smoke) that used to read as
 * "Minecraft placeholder": a soft glowing mote, a stretched spark, a four point glint, a billowing puff.
 *
 * <p>Server safe: it only builds options and calls {@code ServerLevel.sendParticles}; how each one looks is
 * decided by the client's providers.
 */
public final class MagicParticles {

    private MagicParticles() {
    }

    // ---- palette: motes (soft glowing dots)
    /** Warm white light: what END_ROD used to stand in for. */
    public static final MagicParticleOptions MOTE_WHITE = MagicParticleOptions.mote(1.0F, 0.97F, 0.88F, 1.0F);
    /** Gold dust: the default "magic" glow. */
    public static final MagicParticleOptions MOTE_GOLD = MagicParticleOptions.mote(0.98F, 0.8F, 0.36F, 1.0F);
    /** Violet charm-work. */
    public static final MagicParticleOptions MOTE_VIOLET = MagicParticleOptions.mote(0.66F, 0.46F, 1.0F, 1.0F);
    /** Cold blue-white: ghostly or icy. */
    public static final MagicParticleOptions MOTE_ICE = MagicParticleOptions.mote(0.6F, 0.85F, 1.0F, 1.0F);
    /** Sickly green: the dark and the potion-ish. */
    public static final MagicParticleOptions MOTE_GREEN = MagicParticleOptions.mote(0.4F, 1.0F, 0.55F, 1.0F);
    /** Teal: water and the Floo. */
    public static final MagicParticleOptions MOTE_TEAL = MagicParticleOptions.mote(0.35F, 0.95F, 0.85F, 1.0F);
    /** Rose: healing, warmth. */
    public static final MagicParticleOptions MOTE_ROSE = MagicParticleOptions.mote(1.0F, 0.55F, 0.7F, 1.0F);
    /** A dull crimson: blood and curses. */
    public static final MagicParticleOptions MOTE_CRIMSON = MagicParticleOptions.mote(0.95F, 0.2F, 0.22F, 1.0F);

    // ---- palette: sparks (streaks)
    public static final MagicParticleOptions SPARK_WHITE = MagicParticleOptions.spark(1.0F, 0.96F, 0.85F, 1.0F);
    public static final MagicParticleOptions SPARK_GOLD = MagicParticleOptions.spark(1.0F, 0.78F, 0.3F, 1.0F);
    public static final MagicParticleOptions SPARK_VIOLET = MagicParticleOptions.spark(0.7F, 0.5F, 1.0F, 1.0F);
    public static final MagicParticleOptions SPARK_GREEN = MagicParticleOptions.spark(0.45F, 1.0F, 0.55F, 1.0F);
    /** The hard blue-white of metal struck on metal. */
    public static final MagicParticleOptions SPARK_METAL = MagicParticleOptions.spark(0.95F, 0.97F, 1.0F, 1.0F);

    // ---- palette: glints (stars)
    public static final MagicParticleOptions GLINT_WHITE = MagicParticleOptions.glint(1.0F, 0.98F, 0.92F, 1.0F);
    public static final MagicParticleOptions GLINT_GOLD = MagicParticleOptions.glint(1.0F, 0.84F, 0.4F, 1.0F);
    public static final MagicParticleOptions GLINT_VIOLET = MagicParticleOptions.glint(0.75F, 0.58F, 1.0F, 1.0F);

    // ---- palette: smoke (puffs)
    /** Ordinary pale smoke or steam. */
    public static final MagicParticleOptions SMOKE_WHITE = MagicParticleOptions.smoke(0.97F, 0.97F, 0.98F, 1.0F);
    /** Grey smoke. */
    public static final MagicParticleOptions SMOKE_GREY = MagicParticleOptions.smoke(0.62F, 0.62F, 0.64F, 1.0F);
    /** Near-black smoke, ink, dark magic. */
    public static final MagicParticleOptions SMOKE_DARK = MagicParticleOptions.smoke(0.22F, 0.2F, 0.24F, 1.0F);
    /** A violet haze. */
    public static final MagicParticleOptions SMOKE_VIOLET = MagicParticleOptions.smoke(0.55F, 0.36F, 0.85F, 1.0F);
    /** A sickly green haze. */
    public static final MagicParticleOptions SMOKE_GREEN = MagicParticleOptions.smoke(0.35F, 0.8F, 0.45F, 1.0F);
    /** A warm gold haze. */
    public static final MagicParticleOptions SMOKE_GOLD = MagicParticleOptions.smoke(0.95F, 0.78F, 0.4F, 1.0F);

    /** A spark of fire that cools from yellow to deep red. */
    public static ParticleOptions ember() {
        return ModParticles.EMBER.get();
    }

    // ---- shapes

    /** A flat ring of {@code count} motes thrown outwards from (x, y, z) at {@code speed} blocks a tick. */
    public static void ring(ServerLevel level, ParticleOptions what, double x, double y, double z,
                            int count, double speed) {
        for (int i = 0; i < count; i++) {
            double a = i * (Math.PI * 2.0D / count);
            // count 0: the (dx, dy, dz) is the velocity, speed multiplies it
            level.sendParticles(what, x, y, z, 0, Math.cos(a), 0.0D, Math.sin(a), speed);
        }
    }

    /** A burst in every direction: {@code count} particles flung from (x, y, z), evenly over a sphere. */
    public static void sphere(ServerLevel level, ParticleOptions what, double x, double y, double z,
                              int count, double speed, net.minecraft.util.RandomSource rnd) {
        for (int i = 0; i < count; i++) {
            double u = rnd.nextDouble() * 2.0D - 1.0D;
            double a = rnd.nextDouble() * Math.PI * 2.0D;
            double s = Math.sqrt(1.0D - u * u);
            level.sendParticles(what, x, y, z, 0, s * Math.cos(a), u, s * Math.sin(a), speed * (0.6D + rnd.nextDouble() * 0.6D));
        }
    }
}
