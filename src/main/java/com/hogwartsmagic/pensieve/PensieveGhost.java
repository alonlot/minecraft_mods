package com.hogwartsmagic.pensieve;

import net.minecraft.world.entity.player.Player;

/**
 * Whether a player flies through blocks as a free ghost in a recorded memory. Common code: the client sets
 * {@link #localNoclip} (a plain flag, so no client class is touched here) and the server asks the session.
 */
public final class PensieveGhost {
    /** Set by client code while the local player is a free ghost inside a recorded memory. */
    public static volatile boolean localNoclip;

    private PensieveGhost() {
    }

    /** True when {@code p} passes through blocks: the local player by the client flag, anyone else by the server sessions. */
    public static boolean noclip(Player p) {
        if (p.level().isClientSide) return localNoclip && p.isLocalPlayer();
        return PensieveSessions.isFreeGhost(p);
    }
}
