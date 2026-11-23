package com.hogwartsmagic.spell.voice;

import com.hogwartsmagic.network.SpellPackets;
import com.hogwartsmagic.portrait.PortraitPasswords;
import com.hogwartsmagic.worldgen.mechanism.CastleMechanisms;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The one "something is listening for a password" flag each player's voice client has: on while a closed gargoyle
 * ({@link CastleMechanisms}) or a password portrait ({@link PortraitPasswords}) listens to them. Each listener keeps
 * its own state and calls {@link #sync} when it changes; this sends the flag only when the combined answer changes,
 * so the two never switch each other off. Server only.
 */
public final class VoiceEars {
    private static final Set<UUID> SENT = new HashSet<>();

    private VoiceEars() {
    }

    public static void sync(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean want = CastleMechanisms.gargoyleListensTo(id) || PortraitPasswords.listensTo(id);
        if (want == SENT.contains(id)) {
            return;
        }
        if (want) {
            SENT.add(id);
        } else {
            SENT.remove(id);
        }
        SpellPackets.CHANNEL.send(new SpellPackets.GargoyleListeningPacket(want), PacketDistributor.PLAYER.with(player));
    }

    public static void forget(UUID id) {
        SENT.remove(id);
    }

    public static void clear() {
        SENT.clear();
    }
}
