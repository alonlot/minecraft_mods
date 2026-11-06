package com.hogwartsmagic.fx;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * The player's own taste for the cinematic effects (hogwartsmagic-client.toml). Read only on the client; a dedicated
 * server registers the spec but never loads it. Every effect has an off switch here so a player who gets motion sick,
 * or runs a shader pack, can turn it down without losing the rest.
 */
public final class FxConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    static {
        BUILDER.push("cinematic");
    }

    public static final ForgeConfigSpec.DoubleValue CAMERA_SHAKE = BUILDER
            .comment("How hard big impacts (slams, blasts, roars, clashes) shake the view. 0 turns shaking off, 1 is normal, 2 is double.")
            .defineInRange("cameraShake", 1.0, 0.0, 2.0);

    public static final ForgeConfigSpec.BooleanValue CINEMATIC_MOMENTS = BUILDER
            .comment("Freeze-frames, impact flashes and the drained colour on killing blows and boss deaths.")
            .define("cinematicMoments", true);

    public static final ForgeConfigSpec.BooleanValue LETTERBOX = BUILDER
            .comment("Black bars top and bottom during the biggest moments (a Killing Curse landing, a boss falling, a boss first seen).")
            .define("letterbox", true);

    public static final ForgeConfigSpec.BooleanValue SLOW_MOTION = BUILDER
            .comment("In single player only: the world briefly slows to a crawl on the biggest moments. Never in multiplayer.")
            .define("slowMotion", true);

    public static final ForgeConfigSpec.BooleanValue LINGERING_MARKS = BUILDER
            .comment("Blasts and fire spells leave scorch marks, and Glacius frost, that fade over about a minute.")
            .define("lingeringMarks", true);

    public static final ForgeConfigSpec.BooleanValue BLOOM = BUILDER
            .comment("Spell light, Patroni, the Dark Mark and fire glow into the air round them. Switches itself off while a shader pack is in use.")
            .define("bloom", true);

    public static final ForgeConfigSpec.DoubleValue BLOOM_STRENGTH = BUILDER
            .comment("How strong the glow is. 1 is normal.")
            .defineInRange("bloomStrength", 1.0, 0.0, 3.0);

    public static final ForgeConfigSpec.BooleanValue HEAT_HAZE = BUILDER
            .comment("The air shimmers over Fiendfyre, dragon fire and Incendio.")
            .define("heatHaze", true);

    public static final ForgeConfigSpec.BooleanValue BROOM_SPEED_FX = BUILDER
            .comment("Flying fast on a broom widens the view and streaks wind past the edges of the screen.")
            .define("broomSpeedFx", true);

    public static final ForgeConfigSpec.BooleanValue BOSS_INTROS = BUILDER
            .comment("The first time you see a great beast (the Horntail, the Basilisk, a Giant...) the moment is marked.")
            .define("bossIntros", true);

    static {
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private FxConfig() {
    }

    /** A config value, or its default if the client config isn't loaded yet (early in start-up). */
    public static double get(ForgeConfigSpec.DoubleValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }

    public static boolean get(ForgeConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }
}
