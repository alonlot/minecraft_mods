package com.hogwartsmagic.pensieve;

import net.minecraft.server.level.ServerPlayer;

/**
 * Told, on the server thread, each time a viewer is back in the real world after a memory, whatever the reason
 * (END, LEFT, YANK, ABORT). {@code chainRoot} is the scene that began the chain they were in, {@code sawStart}
 * whether they were inside from the start of PLAY. Death, logout and server stop do not call it.
 */
@FunctionalInterface
public interface ViewerHomeListener {
    void onHome(ServerPlayer player, String chainRoot, ExitReason reason, boolean sawStart);
}
