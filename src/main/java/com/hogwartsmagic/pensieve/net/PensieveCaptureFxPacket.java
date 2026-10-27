package com.hogwartsmagic.pensieve.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server to client: an entity is drawing a memory out; the silver thread runs from its temple, along the wand, to the phial. */
public record PensieveCaptureFxPacket(int entityId, boolean mainHandWand) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(mainHandWand);
    }

    public static PensieveCaptureFxPacket decode(FriendlyByteBuf buf) {
        return new PensieveCaptureFxPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PensieveCaptureFxPacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveCaptureFx", p);
    }
}
