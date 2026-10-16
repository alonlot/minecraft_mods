package com.hogwartsmagic.pensieve.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server to client: the viewer dived; start the swallow toward {@code basin}. */
public record PensieveEnterPacket(BlockPos basin) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(basin);
    }

    public static PensieveEnterPacket decode(FriendlyByteBuf buf) {
        return new PensieveEnterPacket(buf.readBlockPos());
    }

    public static void handle(PensieveEnterPacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveEnter", p);
    }
}
