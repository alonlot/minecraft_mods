package com.hogwartsmagic.spell.mind;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * Moves a player from the server. Player movement is client-authoritative, so the server sets the velocity
 * and sends it straight to that player's client (the motion packet), whose own physics then carries it out
 * with its input zeroed. The server never simulates player motion, so its stored vertical velocity is stale:
 * the caller passes the Y from the previous tick and the push keeps whatever the player really did.
 */
public final class ForcedPlayerMovement {
    private static final double JUMP_VELOCITY = 0.42;
    private static final double MAX_VERTICAL = 3.0;

    private ForcedPlayerMovement() {
    }

    /** On the ground: a jump if wanted, else none. In the air: what the player actually moved last tick. */
    public static double verticalVelocity(ServerPlayer player, double previousY, boolean wantJump) {
        if (player.onGround()) {
            return wantJump ? JUMP_VELOCITY : 0.0;
        }
        return Mth.clamp(player.getY() - previousY, -MAX_VERTICAL, MAX_VERTICAL);
    }

    public static void push(ServerPlayer player, double vx, double vy, double vz) {
        player.setDeltaMovement(vx, vy, vz);
        player.hasImpulse = true;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }
}
