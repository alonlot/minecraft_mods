package com.hogwartsmagic.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Moves a player between dimensions by hand, so the client never shows a loading screen and no dimension
 * change is announced. Extracted unchanged from the Diagon Alley arch crossing.
 */
public final class SeamlessMove {
    private SeamlessMove() {
    }

    public static void move(ServerPlayer player, ServerLevel target, double x, double y, double z,
                            float yRot, float xRot) {
        ServerLevel from = player.serverLevel();
        // Off the horse, out of the boat, and nobody on their shoulders. Removing a player by the
        // CHANGED_DIMENSION route takes the player and only the player: the mount is left standing
        // in the old world and the rider arrives in the new one still believing they are on it,
        // so every tick after that fights over a vehicle in a level it is not in.
        player.stopRiding();
        player.ejectPassengers();
        from.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
        player.revive();
        player.setServerLevel(target);
        target.addDuringTeleport(player);
        player.connection.teleport(x, y, z, yRot, xRot);
        player.connection.resetPosition();
        com.hogwartsmagic.pensieve.capture.CaptureHooks.markDiscontinuity(player);
    }
}
