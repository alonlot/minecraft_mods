package com.hogwartsmagic.pensieve.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server to client: the viewer's body was hit; a distant thud, louder for a harder hit (0..1). */
public record PensieveThudPacket(float strength) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(strength);
    }

    public static PensieveThudPacket decode(FriendlyByteBuf buf) {
        return new PensieveThudPacket(buf.readFloat());
    }

    public static void handle(PensieveThudPacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveThud", p);
    }
}
