package com.hogwartsmagic.pensieve.scene;

/** Which scene event kinds are soundtrack-like cues and what happens to them when a memory ends. Pure. */
public final class CueKinds {
    private CueKinds() {
    }

    /** True for cues that stop at once when the memory ends ({@code voice}, {@code sfx}); music fades out instead. */
    public static boolean stopsAtEnd(String kind) {
        return "voice".equals(kind) || "sfx".equals(kind);
    }
}
