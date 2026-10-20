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
     * Scene text cap, in chars (writeUtf/readUtf count chars). The wire carries UTF-8 bytes and a clientbound custom
     * payload is capped at 1 MiB (1,048,576 bytes: ClientboundCustomPayloadPacket.MAX_PAYLOAD_SIZE), so the chars alone
     * do not say it fits: {@link #MAX_JSON_BYTES} is the real budget, checked by {@link #fits}.
     */
    public static final int MAX_JSON = 900000;
    /**
     * Scene text cap, in UTF-8 bytes: the 1 MiB payload minus a generous 48,576 for the channel and packet ids, the
     * scene id (up to 256 chars), the origin, the time, the build ticks and the length prefix.
     */
    public static final int MAX_JSON_BYTES = 1000000;
    /** Scene id cap, in chars; checked when a session is created. */
    public static final int MAX_ID = 256;

    /** Whether a scene's JSON fits the packet: within {@link #MAX_JSON} chars and {@link #MAX_JSON_BYTES} UTF-8 bytes. */
    public static boolean fits(String json) {
        return json.length() <= MAX_JSON && utf8Length(json) <= MAX_JSON_BYTES;
    }

    /** The UTF-8 byte length of {@code s} (what it costs on the wire). */
    public static int utf8Length(String s) {
        return s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

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
