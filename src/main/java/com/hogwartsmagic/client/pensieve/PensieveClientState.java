package com.hogwartsmagic.client.pensieve;

import net.minecraft.core.BlockPos;

/** Client half of a Pensieve dive. Task 8 draws the swallow, the build and the exit; these are hooks until then. */
public final class PensieveClientState {
    private PensieveClientState() {
    }

    /** The local player dived into the basin at {@code basin}. */
    public static void onEnter(BlockPos basin) {
    }

    /** The local player is in the memory; {@code startGameTime} is -1 while the set is still building. */
    public static void onStart(String sceneId, String json, BlockPos origin, long startGameTime, int buildTicks,
                               boolean preview) {
    }

    /** The local player is leaving; {@code reason} is the server's {@code ExitReason} ordinal. */
    public static void onExit(int reason) {
    }

    /** The body left at the basin was hit; {@code strength} is 0..1. Task 8 plays the distant thud. */
    public static void onThud(float strength) {
    }
}
