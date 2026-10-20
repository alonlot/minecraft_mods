package com.hogwartsmagic.pensieve.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server to client: a hand-over was asked for mid-scene ({@code PensieveSessions.handOver}), so the scene on screen
 * fades to black now over {@code ticks}, as a chained scene does at its length, and stays black until the next
 * scene's start arrives.
 */
public record PensieveFadePacket(int ticks) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ticks);
    }

    public static PensieveFadePacket decode(FriendlyByteBuf buf) {
        return new PensieveFadePacket(Math.max(1, Math.min(200, buf.readVarInt())));
    }

    public static void handle(PensieveFadePacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveFade", p);
    }
}
