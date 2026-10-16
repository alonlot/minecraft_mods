package com.hogwartsmagic.pensieve.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server to client: the viewer is in the memory dimension. {@code startGameTime} is -1 while the set builds
 * (the client shows build-from-shadow) and is resent with the real memory-level game time when play begins.
 */
public record PensieveStartPacket(String sceneId, String sceneJson, BlockPos origin, long startGameTime,
                                  int buildTicks, boolean preview) {
    /**
     * Scene text cap, in chars. At most 3 bytes per char that is 768 KiB, under the 1 MiB custom payload limit,
     * and far above any hand-written scene; the server refuses to start a scene over it.
     */
    public static final int MAX_JSON = 262144;
    /** Scene id cap, in chars; checked when a session is created. */
    public static final int MAX_ID = 256;

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(sceneId, MAX_ID);
        buf.writeUtf(sceneJson, MAX_JSON);
        buf.writeBlockPos(origin);
        buf.writeLong(startGameTime);
        buf.writeVarInt(buildTicks);
        buf.writeBoolean(preview);
    }

    public static PensieveStartPacket decode(FriendlyByteBuf buf) {
        return new PensieveStartPacket(buf.readUtf(MAX_ID), buf.readUtf(MAX_JSON), buf.readBlockPos(), buf.readLong(),
                buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PensieveStartPacket p, CustomPayloadEvent.Context ctx) {
        PensieveNet.toClient(ctx, "pensieveStart", p);
    }
}
