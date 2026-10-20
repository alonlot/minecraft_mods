package com.hogwartsmagic.pensieve.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server to client, sent just before the move home from a scene whose {@code end_action} is {@code wake}: the screen
 * stays black through the return, then the viewer's eyes open on the real world over {@code ticks}.
 */
public record PensieveWakePacket(int ticks) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ticks);
    }

    public static PensieveWakePacket decode(FriendlyByteBuf buf) {
        return new PensieveWakePacket(Math.max(1, Math.min(400, buf.readVarInt())));
    }

    public static void handle(PensieveWakePacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveWake", p);
    }
}
