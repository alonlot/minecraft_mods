package com.hogwartsmagic.spell;

import com.hogwartsmagic.ModSounds;
import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server side of travel's look: Apparition (focusing, the squeeze away, the crack of arrival) and the Floo
 * Network (the green flames taking you, and giving you back). The clients draw it all ({@code TravelFxClient});
 * this sends each moment, once, to everyone close enough to see it, by distance rather than by tracking, so the
 * people at the far end of a jump see the arrival even before they track the traveller.
 */
public final class TravelFx {
    public static final byte APPARATE_FOCUS = 0;
    public static final byte APPARATE_CANCEL = 1;
    public static final byte APPARATE_DEPART = 2;
    public static final byte APPARATE_ARRIVE = 3;
    public static final byte FLOO_DEPART = 4;
    public static final byte FLOO_ARRIVE = 5;
    public static final byte FLOO_FIZZLE = 6;

    private static final double RANGE = 64.0;

    private TravelFx() {
    }

    public static void send(Entity traveller, byte kind, double x, double y, double z, int duration) {
        if (!(traveller.level() instanceof ServerLevel level)) {
            return;
        }
        SpellPackets.TravelFxPacket packet = new SpellPackets.TravelFxPacket(traveller.getId(), kind, x, y, z, duration);
        for (ServerPlayer player : level.players()) {
            if (player == traveller || player.distanceToSqr(x, y, z) < RANGE * RANGE) {
                SpellPackets.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
            }
        }
        if (kind == APPARATE_DEPART) {
            level.playSound(null, x, y, z, ModSounds.APPARATE_SQUEEZE.get(), SoundSource.PLAYERS, 1.1f,
                    0.95f + level.getRandom().nextFloat() * 0.1f);
        } else if (kind == APPARATE_ARRIVE) {
            level.playSound(null, x, y, z, ModSounds.APPARATE_CRACK.get(), SoundSource.PLAYERS, 1.3f,
                    0.95f + level.getRandom().nextFloat() * 0.1f);
        }
    }
}
