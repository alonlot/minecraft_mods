package com.hogwartsmagic.pensieve.net;

import com.hogwartsmagic.pensieve.ExitReason;
import com.hogwartsmagic.pensieve.PensieveSessions;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client to server: the viewer asks to leave the memory early. */
public record PensieveLeavePacket() {
    public void encode(FriendlyByteBuf buf) {
    }

    public static PensieveLeavePacket decode(FriendlyByteBuf buf) {
        return new PensieveLeavePacket();
    }

    public static void handle(PensieveLeavePacket p, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ctx.enqueueWork(() -> PensieveSessions.leave(player, ExitReason.LEFT));
        }
        ctx.setPacketHandled(true);
    }
}
