package com.hogwartsmagic.portkey;

import com.hogwartsmagic.fx.Fx;
import com.hogwartsmagic.fx.FxKinds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of how a Portkey journey looks and sounds: each beat is one {@code fx.Fx} kind (no packets of its
 * own), and the clients turn it into the glow, the implosion, the tunnel and the burst ({@code client.fx.portkey}).
 * Common code: safe on a dedicated server.
 */
public final class PortkeyFx {

    /** Bystanders further than this do not see a charge or a bind. */
    private static final double NEAR = 48.0;
    /** The implosion and the arrival carry further: a flash of light is seen from afar. */
    private static final double FAR = 64.0;
    private static final double CRUMBLE_RANGE = 32.0;

    private PortkeyFx() {
    }

    public static void charge(ServerPlayer holder, boolean old) {
        Fx.sendAbout(holder, FxKinds.PORTKEY_CHARGE, NEAR, PortkeyRules.CHARGE_TICKS, old ? 1.0f : 0.0f);
    }

    public static void cancel(ServerPlayer holder) {
        Fx.sendAbout(holder, FxKinds.PORTKEY_CANCEL, NEAR);
    }

    /** The implosion at the holder's feet, and the ground trembling round it. */
    public static void hook(ServerLevel level, Vec3 feet, ServerPlayer holder) {
        Fx.send(level, FxKinds.PORTKEY_HOOK, feet, FAR, holder.getId(), (float) PortkeyRules.GROUP_RADIUS);
        Fx.shake(level, feet, PortkeyTimeline.DEPART_SHAKE, PortkeyTimeline.DEPART_SHAKE_RADIUS,
                PortkeyTimeline.DEPART_SHAKE_TICKS);
    }

    /** One traveller's own tunnel. */
    public static void transit(ServerPlayer traveller, boolean crossDim, boolean holder, boolean old) {
        Fx.sendTo(traveller, FxKinds.PORTKEY_TRANSIT, traveller.getX(), traveller.getY(), traveller.getZ(),
                traveller.getId(), PortkeyRules.TRANSIT_MAX_TICKS, crossDim ? 1.0f : 0.0f, holder ? 1.0f : 0.0f,
                old ? 1.0f : 0.0f);
    }

    /** The burst where the group lands. The travellers are already there, so they are told too. */
    public static void arrive(ServerLevel level, Vec3 feet, int count) {
        Fx.send(level, FxKinds.PORTKEY_ARRIVE, feet, FAR, -1, count);
        Fx.shake(level, feet, PortkeyTimeline.ARRIVE_SHAKE, PortkeyTimeline.ARRIVE_SHAKE_RADIUS,
                PortkeyTimeline.ARRIVE_SHAKE_TICKS);
    }

    public static void abort(ServerPlayer traveller) {
        Fx.sendTo(traveller, FxKinds.PORTKEY_ABORT, traveller.getX(), traveller.getY(), traveller.getZ(),
                traveller.getId());
    }

    public static void crumble(ServerPlayer holder) {
        Fx.sendAbout(holder, FxKinds.PORTKEY_CRUMBLE, CRUMBLE_RANGE);
    }

    public static void bind(ServerPlayer player) {
        Fx.sendAbout(player, FxKinds.PORTKEY_BIND, CRUMBLE_RANGE);
    }
}
