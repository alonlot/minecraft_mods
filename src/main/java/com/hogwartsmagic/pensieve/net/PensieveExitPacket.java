package com.hogwartsmagic.pensieve.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server to client: the viewer is leaving; {@code reason} is {@code ExitReason.ordinal()}. */
public record PensieveExitPacket(int reason) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(reason);
    }

    public static PensieveExitPacket decode(FriendlyByteBuf buf) {
        return new PensieveExitPacket(buf.readVarInt());
    }

    public static void handle(PensieveExitPacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveExit", p);
    }
}
