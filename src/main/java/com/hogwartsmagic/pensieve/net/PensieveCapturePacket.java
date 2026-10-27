package com.hogwartsmagic.pensieve.net;

import com.hogwartsmagic.pensieve.capture.MemoryCapture;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client to server: the player sneaked and used a wand with an empty phial in the other hand. */
public record PensieveCapturePacket() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static PensieveCapturePacket decode(FriendlyByteBuf buf) {
        return new PensieveCapturePacket();
    }

    public static void handle(PensieveCapturePacket p, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ctx.enqueueWork(() -> MemoryCapture.capture(player));
        }
        ctx.setPacketHandled(true);
    }
}
