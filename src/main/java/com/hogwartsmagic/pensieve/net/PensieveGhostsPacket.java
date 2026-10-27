package com.hogwartsmagic.pensieve.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server to client: the other viewers inside the same recorded memory, whom this client draws as silver ghosts. */
public record PensieveGhostsPacket(List<UUID> ghosts) {
    /** More than this many other viewers in one memory is not a real session. */
    public static final int MAX_GHOSTS = 64;

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(ghosts.size());
        for (UUID id : ghosts) buf.writeUUID(id);
    }

    public static PensieveGhostsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_GHOSTS) throw new IllegalArgumentException("ghost count " + n);
        List<UUID> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) ids.add(buf.readUUID());
        return new PensieveGhostsPacket(List.copyOf(ids));
    }

    public static void handle(PensieveGhostsPacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveGhosts", p);
    }
}
