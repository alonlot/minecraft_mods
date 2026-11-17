package com.hogwartsmagic.spell.voice;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client settings for voice casting (hogwartsmagic-voice-client.toml). Only the client reads them: speech is turned
 * into a spell on the player's own machine, and the cast then goes through the same packets as the radial menu.
 */
public final class VoiceCastConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("Say a spell name with a wand in your main hand to cast it (see voiceMode). Speech is processed on",
                    "this computer only; no audio leaves it. Turn off to never open the microphone.")
            .define("voiceCasting", true);

    /** How the microphone is used. */
    public enum Mode {
        /** The microphone is open while a wand is in the main hand; saying a spell on its own casts it. */
        WAND_OPEN_MIC,
        /** The microphone is open only while the voice cast key is held. */
        PUSH_TO_TALK
    }

    public static final ForgeConfigSpec.EnumValue<Mode> MODE = BUILDER
            .comment("WAND_OPEN_MIC: the microphone listens while you hold a wand in your main hand. Say a spell on its",
                    "own (\"Stupefy!\") to cast it; a spell name inside a sentence (\"did you see his Stupefy\") does not",
                    "cast. Use headphones: a friend's voice from your speakers can cast too.",
                    "PUSH_TO_TALK: the microphone listens only while the voice cast key (default V) is held.")
            .defineEnum("voiceMode", Mode.WAND_OPEN_MIC);

    public static final ForgeConfigSpec.IntValue THREADS = BUILDER
            .comment("CPU threads for the speech recogniser. 0 picks half the cores, at most 4.")
            .defineInRange("voiceThreads", 0, 0, 16);

    public static final ForgeConfigSpec.IntValue MAX_SECONDS = BUILDER
            .comment("Push-to-talk only: longest take in seconds. Recording stops by itself after this.")
            .defineInRange("voiceMaxSeconds", 4, 1, 10);

    public static final ForgeConfigSpec.BooleanValue SHOW_HEARD = BUILDER
            .comment("Push-to-talk only: show what the recogniser heard when it matches no spell.")
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
