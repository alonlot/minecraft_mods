package com.hogwartsmagic.spell.voice;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client settings for voice casting (hogwartsmagic-client.toml). Only the client reads them: speech is turned
 * into a spell on the player's own machine, and the cast then goes through the same packets as the radial menu.
 */
public final class VoiceCastConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Hold the voice cast key and say a spell name to cast it. Speech is processed on this computer only;",
                    "no audio leaves it. Turn off to never open the microphone.")
            .define("voiceCasting", true);

    public static final ForgeConfigSpec.IntValue THREADS = BUILDER
            .comment("CPU threads for the speech recogniser. 0 picks half the cores, at most 4.")
            .defineInRange("voiceThreads", 0, 0, 16);

    public static final ForgeConfigSpec.IntValue MAX_SECONDS = BUILDER
            .comment("Longest take in seconds. Recording stops by itself after this.")
            .defineInRange("voiceMaxSeconds", 4, 1, 10);

    public static final ForgeConfigSpec.BooleanValue SHOW_HEARD = BUILDER
            .comment("Show what the recogniser heard when it matches no spell.")
            .define("voiceShowHeard", true);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private VoiceCastConfig() {
    }

    /** The configured thread count, or half the cores (1 to 4) for 0. */
    public static int threads(int configured, int cores) {
        if (configured > 0) {
            return configured;
        }
        return Math.max(1, Math.min(4, cores / 2));
    }
}
