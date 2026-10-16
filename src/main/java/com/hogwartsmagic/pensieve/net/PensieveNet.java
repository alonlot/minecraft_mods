package com.hogwartsmagic.pensieve.net;

import com.hogwartsmagic.network.SpellPackets;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

/** Registration and sending for the Pensieve packets, which ride the shared {@link SpellPackets#CHANNEL}. */
public final class PensieveNet {
    private PensieveNet() {
    }

    public static void register() {
        SpellPackets.CHANNEL.messageBuilder(PensieveEnterPacket.class)
                .encoder(PensieveEnterPacket::encode).decoder(PensieveEnterPacket::decode)
                .consumerMainThread(PensieveEnterPacket::handle).add();
        SpellPackets.CHANNEL.messageBuilder(PensieveStartPacket.class)
                .encoder(PensieveStartPacket::encode).decoder(PensieveStartPacket::decode)
                .consumerMainThread(PensieveStartPacket::handle).add();
        SpellPackets.CHANNEL.messageBuilder(PensieveExitPacket.class)
                .encoder(PensieveExitPacket::encode).decoder(PensieveExitPacket::decode)
                .consumerMainThread(PensieveExitPacket::handle).add();
        SpellPackets.CHANNEL.messageBuilder(PensieveLeavePacket.class)
                .encoder(PensieveLeavePacket::encode).decoder(PensieveLeavePacket::decode)
                .consumerMainThread(PensieveLeavePacket::handle).add();
    }

    public static void send(ServerPlayer player, Object packet) {
        SpellPackets.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }

    /** Server-to-client packets only run on a client; a client sending one to the server is ignored. */
    static void toClient(CustomPayloadEvent.Context ctx, String action, Object payload) {
        if (ctx.getSender() == null) {
            ctx.enqueueWork(() -> com.hogwartsmagic.util.ClientSideBridge.handlePacket(action, payload));
        }
        ctx.setPacketHandled(true);
    }
}
