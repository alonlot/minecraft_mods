package com.hogwartsmagic.fx;

import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server side of the cinematic effects: tells the clients near a spot that something happened there (a slam, a
 * roar, a blast, a killing blow) and leaves how it looks to them ({@code client.fx.FxClient} and whatever registered
 * for the kind). Common code, so a dedicated server can call it; it never touches a client class.
 *
 * <p>A kind is a short string (constants in {@link FxKinds}); the numbers that ride with it mean what the kind says
 * they mean. A client that does not know a kind ignores it, so a kind can be added without a protocol bump.
 */
public final class Fx {

    /** At most this many numbers ride with one effect. */
    public static final int MAX_PARAMS = 8;
    /** Default reach of an effect: players further than this from it are not told. */
    public static final double DEFAULT_RANGE = 64.0;

    private Fx() {
    }

    /** Tell every player within {@code range} blocks of (x, y, z) in {@code level}. */
    public static void send(ServerLevel level, String kind, double x, double y, double z, double range, int entityId,
                            float... params) {
        SpellPackets.FxPacket packet = new SpellPackets.FxPacket(kind, x, y, z, entityId, clamp(params));
        double r2 = range * range;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= r2) {
                SpellPackets.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
            }
        }
    }

    public static void send(ServerLevel level, String kind, Vec3 at, double range, int entityId, float... params) {
        send(level, kind, at.x, at.y, at.z, range, entityId, params);
    }

    /** About an entity, at its position, to everyone within {@code range} of it. No-op off the server. */
    public static void sendAbout(Entity entity, String kind, double range, float... params) {
        if (entity.level() instanceof ServerLevel level) {
            send(level, kind, entity.getX(), entity.getY(), entity.getZ(), range, entity.getId(), params);
        }
    }

    /** Only to one player (their own moment: a boss first seen, their own killing blow). */
    public static void sendTo(ServerPlayer player, String kind, double x, double y, double z, int entityId,
                              float... params) {
        SpellPackets.CHANNEL.send(new SpellPackets.FxPacket(kind, x, y, z, entityId, clamp(params)),
                PacketDistributor.PLAYER.with(player));
    }

    /**
     * Shake the view of everyone near (x, y, z): {@code strength} about 0.3 (a thud) to 3 (the ground splitting),
     * felt out to {@code radius} blocks and fading over {@code durationTicks}. Sent to players within the radius.
     */
    public static void shake(ServerLevel level, double x, double y, double z, float strength, float radius,
                             int durationTicks) {
        send(level, FxKinds.SHAKE, x, y, z, radius + 4.0, -1, strength, radius, durationTicks, 1.0f);
    }

    public static void shake(ServerLevel level, Vec3 at, float strength, float radius, int durationTicks) {
        shake(level, at.x, at.y, at.z, strength, radius, durationTicks);
    }

    private static float[] clamp(float[] params) {
        if (params == null) {
            return new float[0];
        }
        if (params.length <= MAX_PARAMS) {
            return params;
        }
        float[] cut = new float[MAX_PARAMS];
        System.arraycopy(params, 0, cut, 0, MAX_PARAMS);
        return cut;
    }
}
